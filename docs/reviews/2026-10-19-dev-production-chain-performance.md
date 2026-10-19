# Factory B 十万级数据复制到DEV与性能对照验收

日期：2026-10-19。当前状态：**GO，本轮隔离复制及性能测量完成**，第13节为复制基线，第14节为后续FOR UPDATE实施与复测。第1—12节保留解阻前 NO-GO 历史记录，不能作为当前状态。源45表完整复制至独立DEV Schema，原901闭环及121,830条历史记录保持；正式Trace、1/4/8/16并发测试及收尾已完成，不代表生产SLA认证。

## 1. 源库、目标库和隔离预检

|项目|实际核验结果|
|---|---|
|源码|HEAD `9b709348fc4fbae58743f16e9fb9bf4536732e75`，main；开始时仅已有未跟踪SQL审查报告。固定构建后有一份application.yml并行改动，未触碰，见收尾记录|
|源绑定|Factory `V23_FACTORY_B_TEST` → DataSource2 → `110.42.227.83:3306/mes_base_v23_factory_b_test`，LOCAL、ENABLED、无ERP|
|源实例|MySQL8.0.40，UUID `21328a5f-b5fa-11ef-88d3-5254005efa1b`|
|DEV实例|MySQL8.2.0，UUID `03dbc952-7403-11ef-802c-0242ac130002`；Master=`mes_base_dev`|
|DEV TCP/原生客户端|真实连接和认证成功；不是仅依据端口开放|
|DEV JDBC|Java21 + 当前Connector/J9.7.0，实际getConnection及SELECT成功，握手366.724ms；一次探针未出现EOF，不代表持续稳定性已证明|
|目标账号SQL权限|只读SHOW GRANTS确认具有CREATE/ALTER/INDEX/INSERT/SELECT/TRIGGER/SHOW VIEW等权限；未通过创建探针对象验证写入|
|目标Schema冲突|`mes_base_dev_perf_test`不存在；没有创建、覆盖或复用已有业务库|
|现有DEV绑定|只读保存DEV/1/2/SIMPLE_B及数据源元数据，没有改变任何现有Factory/数据源|
|DEV Master管理权限|只读关联sys_user/role/menu确认现有测试管理员100具备Factory/DataSource创建、编辑及用户/角色新增权限；尚未登录独立DEV HTTP实例验权|
|DEV密钥|用当前DEV配置中的密钥调用正式DatasourcePasswordCipher做AES/GCM随机IV往返，内存测试通过；未公开密钥、未持久化新DataSource，实际装配解密待测|
|字符集|源utf8mb4_0900_ai_ci及utf8mb4_unicode_ci，目标均支持；8.0→8.2的实际DDL恢复兼容性未验证|
|磁盘容量|**未确认MySQL数据卷可用字节与挂载关系，阻止目标创建/导入**|

必须注意：旧`.local/mysql-dev.cnf`文件实际指向110.42.227.83，不能按文件名认定DEV。本轮从当前DEV应用配置核对192.168.18.25，并生成权限0600的私有client option文件；没有向旧文件指向的远程环境写入。原生备份/查询连接检查的TLS cipher非空；JDBC探针沿用应用当前useSSL=false等URL选项，探针只执行只读验证。

DEV的MySQL hostname是容器标识、datadir为`/var/lib/mysql/`。现有SSH密钥以本机用户和root访问均被拒绝，没有尝试密码或改变SSH设置。MySQL INFORMATION_SCHEMA.FILES只能显示表空间extents，不能替代宿主机/数据卷df可用空间。已通过文本问题请求数据卷df证据或可用只读SSH方式，尚未收到答复；没有将“权限足够/库很小”当作容量充足的证明。

[脱敏预检及阶段状态](evidence/2026-10-19-dev-production-chain-performance/preflight-summary.json)。私有连接参数不进入Git或报告。

## 2. 一致性备份及导入结果

源45张表全部InnoDB。真实执行mysqldump的一致性快照、quick、skip-lock-tables、no-tablespaces、set-gtid-purged=OFF、hex-blob，并包含triggers/routines/events对象；没有复制PROD Master、账号、会话或Redis，没有API重建901工单，没有向源库写业务事实。凭据通过私有option文件读取，不放在命令参数或脚本字面量。

备份完成：45个CREATE TABLE、21个TRIGGER，完整结束标记存在。压缩文件 **4,829,360字节**，SHA256 `a931a1c14140cd6ffae2b8d370f8013aa6e3ecbbc5cdf90e5cea4e7495c23f44`。全部业务表、索引和Flyway历史均在dump，不只是七类事实。文件保存在Git忽略且权限0700的`.local/dev-perf-copy-20261019/`，备份文件0600，不提交原始内容；[公开备份清单](evidence/2026-10-19-dev-production-chain-performance/backup-manifest.json)只含摘要。

源实际InnoDB表空间FILE_SIZE合计101,531,648字节（约96.83MiB），明显大于information_schema.TABLES的约5.98MB统计估计。两者都不是目标磁盘空闲，也不包含全部导入临时空间/binlog/新负载预算。**导入NOT_RUN，传输到目标、解压恢复耗时和目标增量磁盘占用均未知。**

对象审查：0视图、0存储过程/函数、0事件、21触发器，源DEFINER为root@%，与目标当前认证主体一致；触发器仅SIGNAL保护和本库历史存在性检查，没有跨Schema写入、外部调用或自动任务。这些是正式不可变事实保护，后续复制应保留，不能省略触发器后宣称等价。目标尚未创建，因此没有激活任何对象。[对象清单](evidence/2026-10-19-dev-production-chain-performance/backup-object-review.json)。

敏感性检查目前覆盖列metadata：未发现密码/Token/电话/邮箱等显式认证或联系字段；业务用户只存ID，名称主要是生产主数据/缺陷类型快照，不能因此保证所有自由文本都无敏感内容。原始dump仍按敏感私有备份保管；目标副本内的文本检查/必要脱敏尚未进行，未公开原始行，也未为脱敏改写源库。主键、业务UUID和不可变事实值不擅自改动。

## 3. Schema / Flyway / 全表一致性

源使用当前代码的正式Flyway业务位置`db/business`和`flyway_business_schema_history`调用validate及info：**39项校验通过，current39，V39 checksum1265136020、success1**。未调用migrate/repair/baseline，没有重写已发布迁移。

另外在一个独立的只读REPEATABLE_READ事务中，对45张表生成精确行数、按主键排序的主键集合摘要及全部字段摘要；NULL/非NULL加标记，字段使用二进制HEX规范化，每行SHA256再按主键顺序流式汇总。总行数121,830；全库报工表90,104，不是原性能清单中的90,100。包含后续SQL Trace/P1诊断资源，不混算原闭环规模。

[45表摘要](evidence/2026-10-19-dev-production-chain-performance/source-all-tables-digest.tsv)。Flyway历史在该清单中单独一行，39条迁移记录另有只读查询和正式validate证据。摘要事务与mysqldump是两个独立一致性快照，**尚未证明它们或目标逐行相等**；恢复后仍须对所有表、主键、规范化字段、DDL/索引/触发器及Flyway记录作完整对照。17项业务审计不能替代这一对照。

目标Schema尚不存在：表结构、索引和数据逐项比较全部NOT_RUN，不能宣称整库复制正确。

## 4. 原901个闭环与17项关联审计

复用原性能步骤日志journal中901个COMPLETED Execution/WorkOrder及库存映射，不按整库COUNT推算规模。将原清单只读复制到本任务私有目录，再执行项目原审计脚本；源库17项差异全部0。**这是源库现状验证，不是DEV副本验收。**

|原清单事实|真实数量|
|---|---:|
|完成闭环|901|
|正式报工|90,100|
|确认投产输入|901|
|质量放行|901|
|交接|1,802|
|仓储接收|1,802|
|完工冻结事实|901|
|库存动作|3,604|
|七类核心事实合计|100,011|
|原料实际消耗|180,160|
|成品库存总量|90,080|

17项覆盖生命周期/来源、锁定实际路径、报工总量/节点量/身份、确认投产、质量放行、交接/接收总量和引用、60%/40%拆分、不超交、完工冻结量、库存余额、流水计算与连续性、原料消耗闭环。另用原901个库存ID清单直接SUM负动作和成品余额核对180,160/90,080，未把SQL Trace的新库存混入。[审计](evidence/2026-10-19-dev-production-chain-performance/source-original901-audit.json)、[实际消耗](evidence/2026-10-19-dev-production-chain-performance/source-original901-quantities.json)。目标17项审计、UUID唯一性和逐字段对照尚未运行。

## 5. DEV Factory / DataSource装配状态

未创建测试Factory、DataSource、角色或身份，未修改已有DEV工厂绑定或复制PROD Master。管理权限已在真实DEV中心库只读核验，正式HTTP创建/启用、密文持久化后解密、Business SqlSessionFactory路由、具名TransactionManager及登录后Factory授权均待目标容量条件通过后验证。

另有明确的装配缺口：DEV Master自身system历史当前仅V39（独立于业务V39），`business:wm:stock:document:*`正式权限菜单0行；当前源码V40迁移负责登记这六项权限。沿用已确认负载的“独立库存→正式OTHER_RECEIPT开账→领料投产”需要它们。没有擅自升级现有DEV Master、手工补菜单或用直接SQL余额绕过正式开账。此缺口须在现有执行限制下明确解决，不能只凭管理员身份认定新负载可运行。V41质量权限也不宣称已经装配。

未启动独立服务，Quartz/ERP同步/通知无本轮新实例副作用；尚未完成实例运行时禁用验证，不以“未启动”冒充运行时隔离验证。用户8081/5173现有服务未启动、停止、重启或改配置。

## 6. DEV完整SQL Trace

**NOT_RUN**：READY首次报工、RUNNING正常、UUID重放、数量查询、报工流水分页、Execution详情及闭环查询均无DEV HTTP样本。DEV Mapper/driver SELECT/事务控制/Master-Business/完整协议数量、JDBC/服务器耗时和锁等待全部未知。

前轮84/69/8及Execution9/10次、WorkOrder6/8次、路径节点3次、全菜单、资格COUNT、transaction_read_only等，只是历史远程证据，未确认DEV是否重现。本轮没有为减少SQL移除锁、改变隔离/权限或绕过数量/路径/资格规则。

## 7. DEV各并发档位

|并发|正式样本数|P50/P95/P99|吞吐|错误率/超时|池等待/锁等待|
|---:|---|---|---|---|---|
|1|0，未运行|—|—|—|—|
|4|0，未运行|—|—|—|—|
|8|0，未运行|—|—|—|—|
|16|0，未运行|—|—|—|—|

0表示没有执行，不是零错误或零延迟。没有在导入阶段混跑压测，没有重新提交原901个完工工单，没有直接SQL灌数冒充正式报工，也没有因DEV阻塞改用PROD表压测。

## 8. 数据库 / JVM资源指标

源MySQL8.0.40、DEV8.2.0、源表空间101.53MB、目标字符集和授权已取得；DEV宿主机CPU/总内存、MySQL数据卷可用空间未取得，无法出资源余量结论。没有运行负载JVM，因此没有本轮GC、CPU、堆、连接池等待、网络吞吐或数据库锁等待测量；本地机器df和MySQL FILES.DATA_FREE未被冒充DEV数据卷余量。

当前源码固定JAR构建实际成功：`mvn -B -ntp -DskipTests package`，Corretto21.0.4；测试未运行，不将package当业务验收。JAR SHA256 `0a54a3080bc1f0e8ba2efbb0d0477ee56b42f78b86d327af0bce6fbab3b3f5d9`；源码树文件哈希集合摘要 `258d10fbbd2c02495a58ab14ffe993ea79cd0a352afc2c417702fa067dbbbf46`。完整运行classes/lib已从冻结JAR提取；探针和凭据均在忽略目录，不进入业务构建。[产物清单](evidence/2026-10-19-dev-production-chain-performance/artifact.json)。

## 9. 与原远程环境对照

|指标|原远程Factory B历史结果|本轮DEV|差异|
|---|---|---|---|
|READY完整SQL|84|未运行|不可比较|
|RUNNING完整SQL|69|未运行|不可比较|
|UUID重放完整SQL|8|未运行|不可比较|
|READY HTTP|约2082ms单样本|未运行|不可比较|
|RUNNING HTTP|约1729ms单样本|未运行|不可比较|
|1并发成功写接口/秒|0.720|未运行|不可比较|
|8并发成功写接口/秒|5.164|未运行|不可比较|
|16并发成功写接口/秒|原配置5.862（1次500）；借用校验对照6.266|未运行|不可比较|

历史吞吐是包含多类生产链写接口的阶段吞吐，不是纯报工TPS；原test-on-borrow对照不作为本轮配置。来源：[原十万级报告](2026-10-18-local-production-chain-performance.md)、[远程SQL审查第13节](2026-10-18-production-report-sql-audit.md)。本轮HEAD/JAR与历史不同，MySQL版本、硬件/拓扑、客户端距离、中心菜单/授权规模、诊断开销也未固定；没有DEV测量不能归因网络、计算提升百分比或推断P95。

## 10. 瓶颈及优化建议边界

当前阻碍是验收前置条件，不是已测得的DEV性能瓶颈：

1. MySQL数据卷容量和空闲未确认，禁止目标创建/导入。
2. DEV Master缺正式库存单据权限登记，新负载正式开账链路尚未具备已验证准入。

仅确认此次TCP、原生和JDBC握手成功，未证明旧EOF根因已修复。重复SQL的既有候选保留为历史假设，不在本轮实施；无任何DEV索引、SQL、事务、权限规则或Druid优化。

## 11. 测试资源及清理边界

本轮新增资源只有本地私有一致性备份、诊断Java、只读摘要、原清单副本、冻结JAR和本报告/脱敏证据。目标未建Schema、Factory、DataSource、账号或测试业务事实，不需要也没有删除服务器资源。没有清理源库或修改原始UUID。

原始备份、私有日志和运行配置保留在`.local/dev-perf-copy-20261019/`，已经git check-ignore确认忽略；不提交敏感内容。固定JAR和备份供解除阻塞后续用，不能把备份文件存在当成复制完成。没有自启长期服务，无本轮服务需停止。

## 12. GO / NO-GO与剩余事项

**结论：NO-GO（目标导入和性能验收阻塞，整体未完成）。** 已完成源只读核验/一致性备份、源17项审计、45表独立摘要、DEV客户端与真实JDBC握手、权限/字符集/配置密钥预检和固定构建。

解除阻塞至少需要DEV MySQL实际数据卷的容量、可用空间和挂载映射证据或可用只读SSH连接，并解决现有DEV Master正式库存权限登记缺口。目标安全条件成立后，仍需实际恢复全部表/索引/触发器/Flyway、逐表/主键/字段摘要对照、在目标重跑17项审计、正式Factory/数据源/身份装配及全部HTTP基线和1/4/8/16档压测。本报告中的未运行项不会用源结果、静态检查或历史P50补齐。

未修改业务代码、Flyway历史、Druid配置或权限规则；未向源Factory B或PROD写入。没有宣称整体完成。

收尾核对：除`mes-admin/src/main/resources/application.yml`出现并行变更外，所有开始时已有路径哈希保持不变，本轮未写入该配置。冻结JAR中application.yml与开始时哈希一致。本轮只新增本报告、脱敏证据及Git忽略的临时诊断文件，没有覆盖已有工作。

凭据处理偏差：早期直接读取DEV原始配置时，有一次含凭据的工具输出；后续已改用白名单元数据输出。原始配置/凭据未复制到新报告、公开证据或Git，私有连接文件保持0600。这不改变目标容量与正式权限未通过前禁止写入的边界。

最终检查：`scripts/check-private-config.py`通过（仅所选工作树，不含Git历史）；文档证据JSON及相对链接通过。原8081/PID40599与5173/PID12465仍监听；本轮没有启动新的服务。


## 13. 解阻后完成记录（本节为当前结论）

### 13.1 授权、隔离和正式 V40 升级

用户确认已释放足够空间并允许直接写入，随后单独授权 DEV Master 经正式 Flyway 仅升级至 V40。磁盘容量采用这项人工确认；没有取得宿主机 df 可用字节、挂载点或 OS IOPS，不能写成机器实测容量。初始第1—12节是解阻前历史记录，其中“未导入/未启动/NO-GO”等状态由本节取代。

写入前备份受影响中心表和历史，并核对目标 Schema 不存在；使用项目正式 FactoryFlyway 配置加载 db/platform、db/system 和 Java V34，实际只执行 V40 一项迁移。V40 checksum=-1393220798、success=1；旧39项历史保持，V41未应用。首个仅加载 db/system 的诊断调用因缺V1/JavaV34而在 validate 阶段失败，没有迁移；修正诊断工具调用正式配置后成功。未 repair、baseline 或重写历史。[正式迁移日志](evidence/2026-10-19-dev-production-chain-performance/dev-master-v40-official.log)、[最终核验](evidence/2026-10-19-dev-production-chain-performance/final-data-verification.json)。

新 Factory `TEST_DEV_PERF_20261019`（development，LOCAL、ENABLED、无ERP）通过正式管理 API 绑定新 DataSource9，revision2，`192.168.18.25:3306/mes_base_dev_perf_test`；最终 runtimeStatus=MANAGED、initialized=true。正式创建工厂角色152（17种业务权限，18个菜单ID，现有issue:update有两个菜单登记）和独立测试账号6246；该账号只有新厂角色、没有平台角色。管理员仅装配资源，报工操作者和 recordedBy 均为6246。数据源密钥通过正式加解密及实际业务池读取验证；没有复制PROD Master账号/密码/会话。新账号访问现有DEV厂返回403，随后访问新厂200。原DEV Factory/DataSource绑定及旧角色链接不变。[装配与正式HTTP证据](evidence/2026-10-19-dev-production-chain-performance/final-http-verification.json)。

### 13.2 完整恢复、敏感字段检查和逐表核验

在单事务一致性快照中逻辑备份源45张InnoDB业务表，完整恢复表、列、索引、主键、AUTO_INCREMENT、业务Flyway39行和21个触发器。没有重建901个工单，没有重生成UUID。0视图、0存储过程/函数、0事件；触发器经检查只包含本库事实不可变/引用保护，不带跨环境写入或外部任务，目标既有 DEFINER 可解析。备份4,829,360字节，SHA256=`a931a1c14140cd6ffae2b8d370f8013aa6e3ecbbc5cdf90e5cea4e7495c23f44`，原始文件留在忽略的私有目录，未进入Git。

源8.0.40与DEV8.2.0原始DDL文本不同：目标冗余显式列字符集、客户端关于libraries metadata的版本警告。只规范化这两种文本差异后DDL摘要相同；真实完整列metadata摘要亦相同，不借规范化隐藏字段差异。复制前后45表121,830行的行数、主键集合和全部规范化字段摘要一致，Flyway历史单独一致。最终测试后再将目标全部历史主键逐行hash与源快照对比：121,830条无缺失、无字段改变；源最终全表摘要与初始完全一致。[Schema](evidence/2026-10-19-dev-production-chain-performance/schema-comparison.json)、[全表比较](evidence/2026-10-19-dev-production-chain-performance/data-comparison.json)、[最终源摘要](evidence/2026-10-19-dev-production-chain-performance/source-final-digest.tsv)。二进制字段NULL标记/HEX规范化后逐行SHA，再按主键排序计算表摘要；不是只比较COUNT。

按原始901个工单/Execution清单在目标重跑17项关联审计，通过。原90,100条报工、100,011条核心事实、原料消耗180,160、成品90,080及原库存/入库/历史动作保持；整库90,104条报工中的4条旧诊断事实不混入原清单。[目标17项审计](evidence/2026-10-19-dev-production-chain-performance/target-original901-audit-after-load.json)。

业务文本扫描覆盖121,830行、45表、115,538个选定文本字段，email/手机号/凭据赋值规则0命中；这是有限模式检查，不能保证任意PII识别完整。未改写既有业务人员ID、时间、数量、UUID或快照。历史用户ID没有连同PROD账号迁移，在DEV展示的目录姓名不能作为跨环境同一自然人证明；新测试事实统一独立身份。[敏感字段检查](evidence/2026-10-19-dev-production-chain-performance/sensitive-text-review.json)。

### 13.3 固定产物、独立运行和采样口径

HEAD=`9b709348fc4fbae58743f16e9fb9bf4536732e75`，main，开始时已有未跟踪前轮SQL报告。执行 `mvn -B -ntp -DskipTests package` 成功，未跑单元测试；本轮没有业务代码修改。冻结JAR SHA256=`0a54a3080bc1f0e8ba2efbb0d0477ee56b42f78b86d327af0bce6fbab3b3f5d9`，与前轮远程V39成功SQL Trace的JAR完全相同。早期十万级吞吐验收的JAR为`756c321ffd0365ed5a5d791639f3b6935184e1edee86e0b036f834c402247021`，不同。源码树摘要、构建命令见[产物记录](evidence/2026-10-19-dev-production-chain-performance/artifact.json)。构建后application.yml出现用户并行修改，保留且未纳入已冻结产物；未覆盖此前SQL审查报告。

独立localhost:18082实例和空Redis:17479，使用冻结JAR解出的原类/依赖。临时启动器移除@Scheduled处理器和项目自定义startSystemJobs启动Bean；初次诊断启动发现仅spring.quartz.auto-startup=false不足以阻止自定义Bean启动Quartz，立即停止并修正临时启动器，所有下述正式基线/压测均在修正后进行。sys_job为空，sys_job_log前后100条不变，未观察到任务执行。LOCAL无ERP绑定。未改生产启动代码、Druid配置或事务隔离；加载实例没有javaagent，SQL诊断、准备/导入/权限修改、并发测试分阶段执行。

诊断基线复用调用级javaagent，同时覆盖MyBatis StatementHandler、实际JDBC execute、Connector/J NativeSession和NativeProtocol COM_QUERY；Mapper和实际execute一一核对，不靠日志SQL行数。服务器按请求实际物理连接ID对 Performance Schema statement/sql/* 前后差分，7个成功样本均与协议数量完全一致。digest表已满且新库无digest条目，没有清空服务器监控；使用已启用的线程事件汇总。参数/返回结果仅保留哈希、行数及调用栈，不公开凭据/原始参数。

### 13.4 完整成功 Trace 和耗时分解

以下每项都是独立成功单请求，HTTP200。HTTP列为服务端完整请求Root计时；客户端计时另存原证据。JDBC列为互斥区间并集，包含驱动探针及事务控制，不将Mapper、Native、Service嵌套相加。服务器elapsed含数据库等待，不是CPU；它已包含于JDBC，不再加到HTTP上。

|请求|Mapper=实际execute|Driver内部SELECT|事务控制|完整协议SQL|Mapper Master/Business|HTTP ms|JDBC并集 ms|服务器elapsed ms|
|---|---:|---:|---:|---:|---|---:|---:|---:|
|[READY](evidence/2026-10-19-dev-production-chain-performance/ready-sql.md)|67|10|7|84|5/62|549.315|423.923|105.587|
|[RUNNING](evidence/2026-10-19-dev-production-chain-performance/running-sql.md)|55|7|7|69|5/50|474.097|359.636|100.721|
|[REPLAY](evidence/2026-10-19-dev-production-chain-performance/replay-sql.md)|3|0|5|8|2/1|43.996|31.914|2.608|
|[QUANTITY](evidence/2026-10-19-dev-production-chain-performance/quantity-sql.md)|14|0|5|19|2/12|122.834|77.227|7.552|
|[LEDGER](evidence/2026-10-19-dev-production-chain-performance/ledger-sql.md)|16|0|7|23|2/14|154.989|92.414|8.834|
|[EXECUTION](evidence/2026-10-19-dev-production-chain-performance/execution-sql.md)|3|0|7|10|2/1|58.687|36.768|2.682|
|[CLOSURE](evidence/2026-10-19-dev-production-chain-performance/closure-sql.md)|21|0|7|28|2/19|191.381|130.900|10.421|

READY完整Master/Business=7/77，RUNNING=7/62，重放=2/6；业务事务期间操作者资格经独立中心只读窗口，不能把thread事务标志误认为中心SQL参与业务物理事务。全部每条SQL指纹、MapperID、执行顺序、库、事务状态、调用栈、锁分类、参数/结果hash和耗时见各请求链接及对应timeline/mapper JSON；完整[汇总](evidence/2026-10-19-dev-production-chain-performance/sql-summary.json)。COMMIT与提交后缓存失效均已到达；READY额外路径启动及状态写入到达。

READY互斥分解：JDBC423.923、借连接0.826、Redis4.949、Java/框架/映射/诊断开销及其他119.618ms，总549.315ms。RUNNING分别359.636、0.847、3.764、109.850ms，总474.097ms。认证过滤器、授权、参数处理、事务begin/commit、返回值序列化等原始span是嵌套计时，见sql-summary，不可相互累加成墙钟。未将Java剩余项全部当纯业务CPU。

服务器READY/RUNNING COMMIT各76.890/76.964ms，是单条最显著服务器成本；客户端COMMIT还含网络及驱动开销。`innodb_flush_log_at_trx_commit=1`、`sync_binlog=1`，没有调整；没有OS IO证据，不能确定是fsync/存储主因。相较每条SELECT大多约数毫秒JDBC往返，数据库自身读取较短；READY聚合Execution主键读取44.857ms、WorkOrder31.912ms、WorkOrder条件UPDATE30.415ms，RUNNING依次Execution37.596、WorkOrder24.120、Execution条件UPDATE23.884ms，属于多次往返聚合，不能当成一条慢SQL。服务器SUM_LOCK_TIME仅表/metadata计时，不能冒充InnoDB行锁等待；本次单请求没有可用逐语句row-wait history，因此无法精确拆开其行锁毫秒。

初次DEV READY/RUNNING样本分别930.37/973.18ms，后续完整服务器采样为549.315/474.097ms，均保留，不挑较快样本推导P50或提升率。后者仅用于对齐SQL和服务器时间口径。

### 13.5 正式写入负载与结果

31个独立新工单/Execution：2个基线、各并发档1/4/8/16个worker各一份。均按正式HTTP完成库存开账+20、工单/用料、投产10扣原料20、路径锁定、操作资格/指派；没有直接SQL灌报工。稳定确定性UUID、SQLite发送日志先落盘、不自动重试；READY+RUNNING各一次预热后开始计时。新负载只测节点A的RUNNING正常报工，每次0.010000，符合现有decimal数量契约，非随机数，不代表整数件物理产量。每worker独占Execution，不是同一Execution竞争压测。

|并发|正式计时样本|吞吐 req/s|P50 ms|P95 ms|P99 ms|错误/超时|
|---:|---:|---:|---:|---:|---:|---|
|1|256|2.372|411.61|471.51|546.28|0/0|
|4|256|8.445|462.95|539.91|583.47|0/0|
|8|256|14.568|518.29|642.72|785.47|0/0|
|16|512|22.377|607.39|1312.57|1534.60|0/0|

共1,280条计时请求全部200；分位数按(n-1)线性插值，单档256/512样本、P99仅少数尾部，不是长期容量或SLA。吞吐按发送/日志/收尾完整阶段墙钟，排除准备/登录/预热。结果JSON及逐请求时延位于同一证据目录的WRITE_C*-results/samples.json。预热58条、基线4条，共新增1,342条正式报工，UUID重放新增0，逐条数量/身份/UUID与发送日志匹配。31次投产合计310，原料消耗620，62条库存动作，全部新RAW/FG余额0；新Execution为RUNNING部分报工，未伪造质量放行、完工或成品入库。原901完整闭环独立保持，不把这些31条算作新闭环。

**READY预热不能被稳态结果掩盖。** 并发1/4/8/16预热各2/8/16/32请求，最大HTTP约481.3/1503.1/2715.7/5766.9ms，文件时间推算阶段跨度约0.859/1.933/3.150/6.184秒（不是精确单调时钟压测）。相邻计时档之间、含下一档READY预热及准备间隔，全局InnoDB行锁等待分别新增3/7/15次、累计921/6409/26249ms，与共享Routing1首次启动同值UPDATE序列化相关，但没有逐等待链证据，不能断言全部等待只来自该行。多线程等待总和可大于预热墙钟。业务池计时开始前累计等待在C8/C16已达3797/30295ms，主要落在非计时窗口，不能宣称整轮无排队。

各RUNNING计时窗口全局InnoDB row_lock_waits/time差分均0；全局计数可能含其他实例，0差分不证明同一Execution竞争性能。Business服务器语句差分C1/C4/C8/C16为15,878/15,882/15,877/31,749，分别比单请求Business62×样本多6/10/5/5条，含池维护/连接状态等未归因事件，**不是精确请求SQL数**，不强行扣除。服务器累计elapsed约24.901/34.936/42.573/97.348秒，为多连接累计，不能当阶段墙钟。精确69条口径仅来自上节单请求逐协议采样。

### 13.6 查询负载

每组128条正式GET、组前2条预热不计时，4接口×4并发，共2,048条全部200。使用原已完成Execution901：数量查询、限定executionId/routingNodeId的offset0/limit20流水页、Execution详情、闭环查询。新负载不写旧工单。不是9万条全局深分页或大量质量事实的压力证明。

|接口|并发|吞吐 req/s|P50 ms|P95 ms|P99 ms|错误|
|---|---:|---:|---:|---:|---:|---:|
|QUANTITY|1|10.72|90.71|107.49|150.40|0|
|LEDGER|1|8.73|112.36|131.94|139.72|0|
|EXECUTION|1|20.82|47.16|52.03|62.47|0|
|CLOSURE|1|7.29|133.76|154.27|182.32|0|
|QUANTITY|4|36.51|99.17|162.76|231.73|0|
|LEDGER|4|32.86|117.80|145.66|152.61|0|
|EXECUTION|4|82.74|47.65|57.59|58.82|0|
|CLOSURE|4|27.05|145.31|167.19|184.68|0|
|QUANTITY|8|77.76|100.91|116.63|122.06|0|
|LEDGER|8|63.31|124.25|136.42|139.14|0|
|EXECUTION|8|144.00|52.23|74.00|81.19|0|
|CLOSURE|8|51.07|153.13|181.37|189.27|0|
|QUANTITY|16|135.69|110.26|127.71|157.42|0|
|LEDGER|16|113.04|136.87|168.24|173.48|0|
|EXECUTION|16|262.38|56.63|78.40|81.67|0|
|CLOSURE|16|95.87|163.69|175.15|185.89|0|

### 13.7 数据库与JVM资源

1Hz Druid白名单/MXBean采样，不在压测时抓SQL文本。下表等待是累计计数的采样差分，边界存在约1秒误差；采样瞬时等待线程峰值均0不排除短暂排队。未取得的Connect/Create字段保持null，不能按0解释。

|并发|采样数|heap峰值 MiB|进程CPU load最大值(0—1)|GC累计差分 ms|Business active峰值|Business等待 ms|Master active峰值|Master等待 ms|
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|1|107|208.10|0.0360|106|1|0|1|0|
|4|31|211.31|0.0598|59|4|1|2|34|
|8|18|210.23|0.0608|44|8|0|5|0|
|16|23|199.18|0.0738|61|16|0|5|401|

两池maxActive20未变，采样ErrorCount0。CPU load为JVM进程归一化比例，不是MySQL CPU或单核利用率。DEV MySQL buffer pool128MiB、max_connections151；最终目标FILE_SIZE100,220,928、ALLOCATED_SIZE100,311,040字节，不能当文件系统可用空间。未取得DB宿主CPU、IOPS、磁盘free、逐事务锁等待链；JDBC包含网络，服务器elapsed包含等待，均不等于DB CPU。[资源原始白名单采样](evidence/2026-10-19-dev-production-chain-performance/resources.jsonl)。

### 13.8 与原远程环境对照

|指标|原远程Factory B|DEV副本|解释|
|---|---:|---:|---|
|READY完整SQL|84|84|同JAR、相同请求类别|
|RUNNING完整SQL|69|69|同JAR，重复访问仍存在|
|UUID重放完整SQL|8|8|重放没有第二事实|
|READY HTTP单样本 ms|2082.362|549.315（另一样本930.37）|非P50，不推导提升百分比|
|RUNNING HTTP单样本 ms|1728.787|474.097（另一样本973.18）|非吞吐结论|
|1并发历史链路吞吐/本轮报工 req/s|0.720|2.372|原为混合生产链HTTP，新为纯RUNNING报工，不同口径|
|4并发同上|3.026|8.445|不同口径|
|8并发同上|5.164|14.568|不同口径|
|16并发同上|5.862|22.377|旧有1次EOF；旧另轮改借用校验6.266不能混作本轮配置|

历史报工P50（1/4/8/16）约1380.7/1301.2/1385.7/2531.1ms，也不同于历史混合写吞吐的统计对象。原远程SQL Trace与本轮JAR相同；原十万级负载JAR不同。硬件/数据库版本、应用部署拓扑与客户端距离、Master菜单规模/权限角色、JIT缓存、导入后的物理布局不同，不能把差异全部归因网络。原远程服务器COMMIT约8.529/5.398ms，本轮约76.890/76.964ms，DEV也存在更大的数据库提交成本；旧服务器语句统计未完整覆盖84/69条，不能比较其不完整全量服务器耗时。本轮所有测量请求无EOF，不外推未来不会出现。

### 13.9 重复访问与最小下一轮候选（未实施）

DEV复现Execution主键READY10/RUNNING9次、WorkOrder8/6次、路径节点3次、Operation主键2次；OperationUserDirectory仍先取全菜单391行，筛出163个业务菜单ID，资格COUNT和SELECT各一次。数量load在报工内1次，质量事实/最新缺陷各加载1次，本成功零缺陷样本不支持宣称报工质量投影重复。闭环GET则可见同类质量投影多次，属于另一查询入口。相同参数/结果hash仅证明相同返回字段，不证明MVCC版本相同。

|类别/候选|当前READY/RUNNING|预计减少|风险/前提|成本/优先级|
|---|---|---|---|---|
|A/B 操作者资格存在性：内部requireValid专用有界查询，避免分页COUNT|COUNT1+SELECT1|COUNT最多1次/请求，收益待测|仍校验启用账号、角色、当前厂业务权限；普通目录分页保留原契约，需撤权/禁用/跨厂负例|低至中/P1|
|B 已持有锁后的Execution/WorkOrder显式事务内视图复用|10/9、8/6次读取|保守候选合计约4次，未证明前不可直接删除|必须先锁再取可信视图，状态写入后刷新；并发报工/停工/质量/完工/幂等竞争均验证，不缓存数量事实、不改单一数量权威|中/P1|
|B 锁定路径节点的同事务不可变快照复用|节点3次|保守1次|证明路径锁定后节点不变，READY启动后需使用更新状态；跨事务不复用|中/P2|
|C OperationUserDirectory有界权限/菜单查询|全菜单391行1次|次数未必减少；减少行数/参数量，收益待测|保留完整URL菜单分类与角色授权，不能按权限前缀猜范围；需平台/工厂角色矩阵负例|中/P2|
|D WO/Execution/Routing同值UPDATE|WO4/3，Execution3/3，READY Routing1|0（当前保留）|承担数量判断/状态互斥序列化；READY并发等待是验证线索，不能据同值直接删除|不实施|
|D 幂等与数量/质量权威读取|事务外/锁后UUID双查、数量load1|0（当前保留）|首次查避免无谓流程、锁后查堵并发UUID竞争；数量必须读取当前合法事实|保留|
|E Connector/J transaction_read_only探针|10/7|待测|涉及驱动只读状态和条件更新，未取得安全等价证明；不改Druid或事务配置|后续独立诊断|

前三项以减少数据库往返为目标；以本单样本每次JDBC约数毫秒作测量依据，不把4条读取×单条时间当成已验证墙钟收益。未加索引、未执行业务优化，也未在本轮额外做EXPLAIN诊断；已有主键读取频率和短服务端读耗时不足以论证新索引。当前最显著单条服务器成本是COMMIT，另需独立只读存储/提交等待调查，不能用删除一致性锁替代。

### 13.10 清理、证据范围和最终结论

完成后已停止自建18082后端及17479 Redis；原用户8081/PID40599、5173/PID12465仍在。测试库、新Factory/DataSource、角色152/账号6246、31个RUNNING部分报工资源和1,342条新事实保留供复验；不删除不可变事实或源十万级数据。原始敏感备份/私有配置/凭据/发送日志仅在忽略目录，权限受控；公开证据只有脱敏调用/摘要/统计。Redis停止到后端退出间有重连警告，发生在全部测量结束后，不作为测量错误。启动器及诊断agent仅为本地可移除工具。[清理核验](evidence/2026-10-19-dev-production-chain-performance/cleanup.json)、[工作树保护](evidence/2026-10-19-dev-production-chain-performance/workspace-preservation-final.json)。

**GO：完整业务库复制、原901闭环/17审计/全表字段与主键比较、正式Factory装配、7类成功Trace、1/4/8/16档报工与查询测量均完成。** 此GO仅指本轮隔离复制及采样验收，不是生产容量/SLA或所有生产场景通过。仍未证明：同Execution并发竞争吞吐、READY长时压力、长时间稳态、质量大数据/深OFFSET、DB宿主CPU/IOPS及逐语句行锁等待。没有修改源库或旧901事实，没有修改生产业务代码、权限规则、Flyway历史、Druid或事务隔离；DEV Master V40为本轮单独授权的正式升级。

当前答案：正常RUNNING完整协议69条（55 Mapper+7驱动SELECT+7控制），READY84条，重放8条；重复主键及锁相关往返仍在。单请求服务器最突出COMMIT约77ms，累计往返最高的是Execution/WorkOrder读取；16并发稳态报工22.377 req/s、P95 1312.57ms，READY预热另有明显等待。下一轮优先评估资格COUNT、锁后身份视图复用、锁定节点快照复用，先补并发证明再实施。

最终交付复核：`scripts/check-private-config.py`通过（仅选定工作树，不含Git历史）；全部公开证据JSON/JSONL解析、文档相对链接及SHA256清单核验通过。初始已有路径仅application.yml的用户并行修改不同；前轮报告保持原哈希。证据目录与私有诊断目录均命中现有Git忽略规则，未提交/推送。


## 14. 2026-10-19 同值 UPDATE 锁改为 FOR UPDATE：实施与 DEV 对照

### 14.1 实施范围、版本与保护边界

本节是后续用户明确授权的代码实施；第1—13节保留原复制与旧版性能历史。本轮全仓审查生产Java/XML中的同值UPDATE、主键补丁、锁方法和条件抢占，确认并替换 **24个纯加锁调用点，分布13个Service/Rules、11张业务表的原Mapper/XML**。范围包括生产链WorkOrder/Execution/Issue、工艺与工序/物料引用、计量单位/工位、库存及库存单据。逐文件清单见[锁审查清单](evidence/2026-10-19-production-lock-for-update/lock-audit.json)。

保留的真实UPDATE：工单作废的status+revision条件变更、工艺revision+1 CAS、库存余额和单据状态写入、同步run_token/lease与Quartz running_token条件抢占。这些是持久化状态转换，不能用SELECT或Redis锁替代。中心角色、启动版本、OpenAPI客户端、编码规则等已使用FOR UPDATE的入口未发现本轮这种“纯同值UPDATE+重读”模式，未重写；部分既有Mapper归属/Extra历史不属于本轮治理。数据库事务行锁本身可跨JVM互斥，并随commit/rollback结束；本轮不新增Redis分布式锁，不引入TTL与数据库提交脱节的第二套互斥协议。

HEAD仍为 `9b709348fc4fbae58743f16e9fb9bf4536732e75`，工作树含本轮源码和既有未跟踪报告。用户原有application.yml改动在本轮开始/结束字节一致，未覆盖。固定A旧JAR SHA256=`0a54a3080bc1f0e8ba2efbb0d0477ee56b42f78b86d327af0bce6fbab3b3f5d9`；固定B新JAR SHA256=`f7025c0e99013aa0ccbc8167258299f5beb764c7df483d150c6f61639b3a62b8`。[构建/源码摘要](evidence/2026-10-19-production-lock-for-update/artifact.json)。两版运行均显式使用同一份冻结的外部配置，排除用户并行application.yml变化对比较的影响；Java21、Connector/J、Druid、事务隔离、权限角色与数据源绑定保持相同。源码新改动没有部署到源Factory B。

DEV只用既有隔离Factory `TEST_DEV_PERF_20261019` → DataSource9/r2 → `192.168.18.25:3306/mes_base_dev_perf_test`，LOCAL；Business V39、Master V40，本轮没有执行迁移或权限授予。报工账号6246/工厂角色152未扩权；作废及完工负例使用已有DEV管理员100的既有正式权限，仍只针对本轮新工单，不使用PROD身份/会话。[前置核验](evidence/2026-10-19-production-lock-for-update/target-preflight.xml)。

### 14.2 锁语义与 Mapper 缓存

`ProductionChainLocks.order` 从 UPDATE(id=id)+SELECT 改为一次返回完整行的主键FOR UPDATE。`execution` 仍先普通读取父工单ID，再锁WorkOrder，最后锁Execution并复核父身份，**单次完整方法5条Mapper SQL变为3条**；发现父身份的一次读取不能在未知父ID时盲目删除。Issue仍按既有WorkOrder→Execution→Issue顺序及ID排序加锁，并保留锁后关联比较；WorkOrder/Execution重复重入的Service契约继续保留。本轮没有引入“已经锁过”的请求缓存或复用过期数量视图。

11个原Mapper/XML增加 `selectByPrimaryKeyForUpdate`，复用完整列清单/resultMap、主键参数和固定SessionFactory归属，无新ExtraMapper。批量引用保护仍按已发现ID排序逐主键锁定，用锁定读返回的当前行校验数量/状态，不再UPDATE后另做重读/COUNT；编码同步仍先发现候选ID、再按主键顺序逐条锁，保留有界N+1，尚未实现单条批量锁查询。旧后置查询可能看见未锁定的并发新插入，当前实现只使用本轮实际锁住的行，缺项失败关闭/由既有唯一约束拒绝，不转而更新未锁定对象。

MySQL的当前读只在SQL到达数据库后发生，不会自动绕过MyBatis缓存。新XML明确 `flushCache=true`（清理一级缓存并触发所属二级缓存失效）、`useCache=false`（不使用二级缓存），确保每次锁定读真正执行。没有全局关闭普通查询缓存。手工修改带MES-CODEGEN标记的文件已按规则移除该文件标记；通用MBG插件同时补齐同名主键锁定读生成能力，未来生成不会再因缺能力而借用UPDATE。调用方仍须处于原有业务写事务内。

### 14.3 同环境、同口径完整 SQL Trace

A/B串行启动独立诊断实例，使用相同调用级采样方法，参数及结果仅公开哈希。每个主样本的实际JDBC execute=Mapper次数，COM_QUERY协议数与实际物理连接的Performance Schema statement/sql前后计数一致；每条SQL顺序、MapperID、调用栈、事务/数据源、耗时及FOR UPDATE标识见链接。

|版本/请求|Mapper|Driver SELECT|事务控制|完整SQL|Master/Business完整SQL|HTTP Root ms|JDBC互斥并集 ms|服务器elapsed ms|
|---|---:|---:|---:|---:|---|---:|---:|---:|
|[A READY](evidence/2026-10-19-production-lock-for-update/a-ready-sql.md)|67|10|7|84|7/77|635.620|471.834|123.697|
|[A RUNNING](evidence/2026-10-19-production-lock-for-update/a-running-sql.md)|55|7|7|69|7/62|625.764|457.026|144.984|
|[A REPLAY](evidence/2026-10-19-production-lock-for-update/a-replay-sql.md)|3|0|5|8|2/6|40.947|29.164|2.422|
|[B READY](evidence/2026-10-19-production-lock-for-update/b-ready-sql.md)|59|2|7|68|7/61|474.618|361.762|85.458|
|[B RUNNING](evidence/2026-10-19-production-lock-for-update/b-running-sql.md)|49|1|7|57|7/50|403.382|301.421|93.680|
|[B REPLAY](evidence/2026-10-19-production-lock-for-update/b-replay-sql.md)|3|0|5|8|2/6|43.526|31.567|2.523|

结论：READY **84→68（少16条）**，RUNNING **69→57（少12条）**，UUID重放8→8。READY少8个纯UPDATE及其8个驱动只读探测；RUNNING少6个纯UPDATE及其6个探测。剩余真实INSERT/状态UPDATE仍有驱动transaction_read_only读取，未改驱动或Druid配置。Master SQL没有因本轮改动减少，授权/资格查询保持原链路。

**不能误读为Execution读取次数已下降。** READY Execution从10次普通SELECT+3次纯UPDATE，变为7次普通SELECT+3次FOR UPDATE；RUNNING从9次普通SELECT+3次纯UPDATE，变为6次普通SELECT+3次FOR UPDATE，SELECT合计仍10/9。WorkOrder普通SELECT从8/6变为4/3，另有4/3次FOR UPDATE，SELECT合计仍8/6。减少的是锁定与返回行合并后的独立DML及其驱动往返；跨Service重复加载问题仍待下一轮显式持锁视图证明，未借机删锁或缓存数量事实。

READY/RUNNING中的正式INSERT、COMMIT、提交后SpringCommitActions及缓存失效均到达；READY只进行一次生命周期启动，路径保持LOCKED。所有嵌套Mapper/Service/事务span、JDBC和服务器elapsed不能相加：JDBC含网络/服务器等待，服务器elapsed也不是DB CPU。线程级服务器计数只用于单请求串行且无后台重用连接干扰的窗口。

采样偏差单独保留：B第一条成功READY触发冷池初始化，100个协议命令中95个COM_QUERY、5个COM_PING，不能把100当SQL数；第二条成功READY为68条SQL，但服务器计数核对受同连接其他活动干扰。前两条不作服务器完整匹配主样本；各1条事实均保留，未重放为“首次报工”。增加登录审计静默期、预热连接，另建独立READY资源后取得上表匹配样本。干扰与异步登录审计存在时间相关性，未把未归因计数强行塞入报工调用链。[原始偏差摘要及私有Trace摘要](evidence/2026-10-19-production-lock-for-update/excluded-startup-samples.json)。HTTP单样本不推导P50或提升率。

### 14.4 DEV 两个独立 JVM 的并发正确性

正式HTTP请求交错发往18082/18083，使用同一个隔离Factory及独立测试账号；两个进程没有共享JVM锁。除显式持锁诊断只执行SELECT/ROLLBACK外，业务事实均由正式API创建，未直接SQL修改状态/库存或伪造报工。

|场景|实际结果与数据库事实|
|---|---|
|同Execution、同UUID、同payload，16请求|全部HTTP200，返回同一个ID，仅1条报工事实|
|同Execution、16个不同UUID各报1，投产上限10|10个200、6个409/REPORT_QUANTITY_CONFLICT；事实10条，总量10，可报量0|
|READY首次报工竞争16请求各0.01|全部200，16条事实/总量0.16；Execution RUNNING，有start_time，路径仍LOCKED|
|同UUID不同payload（数量1/2）竞争|1个200、1个409，仅1条事实|
|超额失败与持锁回滚|非法报11返回409、0事实；独立SQL会话按WO→Execution持锁，另一JVM请求实际等待；ROLLBACK后3358.682ms完成合法报工，仅1事实|
|作废与报工竞争|本样本报工先成功1条、随后作废成功；后续新UUID报工409/WORKORDER_CANCELLED；原成功UUID在另一JVM仍可重放且不新增事实|
|不具备完工条件|正式完工409/PRODUCTION_CLOSURE_BLOCKED，0完工事实；随后另一JVM正常报工200，证明失败路径释放锁|
|同一库存单据16次同UUID过账|全部200，仅1条+5库存动作|
|两个不同出库单争用余额5，各扣4|仅1份过账成功、另一份明确拒绝；余额/可用量均1，库存动作合计2条（+5、-4）；单据状态2 POSTED、1 DRAFT|

[生产并发结果](evidence/2026-10-19-production-lock-for-update/concurrency-results.json)、[库存并发结果](evidence/2026-10-19-production-lock-for-update/concurrency-stock-results.json)、同目录测试脚本。实际捕获锁等待为 `workorder / PRIMARY / RECORD / X,REC_NOT_GAP / WAITING / LOCK_DATA=1003`，明确是本轮新工单的记录锁；不是根据UPDATE耗时猜测行锁。完整列主键锁定读的只读EXPLAIN在WorkOrder、Execution、Inventory均为PRIMARY/const/估1行，无新增索引。[执行计划](evidence/2026-10-19-production-lock-for-update/locking-read-plans.json)。这不是缺失行/范围查询锁行为的全面证明。

单元/生成契约补验覆盖：父锁先于子锁、锁后父身份变化拒绝、等待期间Execution消失拒绝、被引用工单/工位删除保护、非id的String主键生成，以及普通读取缓存旧值而FOR UPDATE必须读到当前值。缓存用H2证明MyBatis机制，真实并发与锁证据来自上述DEV MySQL，二者不混用。

### 14.5 无 agent 的 A/B 性能对照

两版各29个独立工单/Execution，每worker一份；每档2次正式预热/worker，不计入时延统计。1/4/8并发各256次，16并发512次，两版共2,560个计时报工全部200，无错误、无超时。确定性UUID，发送前落SQLite，不自动重试。每次节点A报0.010000，投产10，保持相同业务规则；不是对原901个已完工工单重报。诊断、建数据、EXPLAIN、构建和管理操作均不与计时阶段混跑。

|并发|每版N|A吞吐 req/s|B吞吐 req/s|A P50 ms|B P50 ms|A P95 ms|B P95 ms|A P99 ms|B P99 ms|
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|1|256|2.403|2.716|408.98|359.06|462.63|438.11|485.31|475.57|
|4|256|8.311|9.191|468.99|417.26|549.12|529.91|599.72|590.78|
|8|256|14.296|16.579|525.36|471.27|643.83|583.19|759.90|657.67|
|16|512|24.988|26.762|616.91|554.70|751.91|772.84|857.14|847.64|

本次各档吞吐均提高、P50均降低；**16并发P95从751.91升到772.84ms，不能声称尾延迟全面改善。** 分位数按(n-1)线性插值，只有一组A后B的顺序实验，未随机交替/长时运行，不给统计显著性或生产SLA承诺。数据规模、每worker报工量与客户端一致，数据库持续增长少量新事实、缓存/JIT/存储背景活动仍可能影响结果。[完整逐请求与阶段结果](evidence/2026-10-19-production-lock-for-update/benchmark-comparison.json)，同目录a/b-write-c*-samples.json为逐请求时延。

各档计时窗口全局InnoDB row_lock_waits/time增量均0；这是独立Execution的RUNNING稳态，不代表同Execution/READY不等待。READY预热最大HTTP另列，A/B：C1 533.3/490.3ms、C4 1214.3/1017.6ms、C8 2468.5/2193.2ms、C16 4928.1/6021.4ms。共享Routing等必要锁、池扩容及提交等待仍可能形成排队，FOR UPDATE不消除热点；预热不混入稳态分位数，也不隐去C16预热变慢。

### 14.6 资源、数据与收尾

资源为1Hz白名单采样，等待计数只是在阶段内样本首尾差分，可能漏短暂峰值；CPU为JVM进程0—1归一化比例。没有MySQL宿主OS CPU/IOPS或逐语句行锁历史，不把服务器elapsed说成CPU。

|版本/并发|采样数|heap峰值 MiB|CPU load峰值|GC差分 ms|Business active峰值/等待 ms|Master active峰值/等待 ms|
|---|---:|---:|---:|---:|---|---|
|A/1|106|224.4|0.0349|106|1/0|1/0|
|A/4|30|219.2|0.0500|45|4/0|3/0|
|A/8|18|219.8|0.0538|30|8/0|3/0|
|A/16|21|219.7|0.1017|74|16/3|8/409|
|B/1|94|199.8|0.0338|73|1/0|1/0|
|B/4|28|195.1|0.0593|46|4/0|1/0|
|B/8|15|192.3|0.0566|37|8/0|1/0|
|B/16|19|193.8|0.0734|60|16/0|7/386|

池maxActive仍20，采样ErrorCount均0；未取得的Connect/Create计数不按0处理。[资源摘要](evidence/2026-10-19-production-lock-for-update/resource-summary.json)及a/b-resources.jsonl。压力阶段schema线程累计SQL/服务器时间含池维护等非请求活动，不能除以样本数作为精确单次SQL，更不能把多线程累计时间当墙钟；精确SQL结论只用上节逐协议Trace。

最终源Factory B绑定仍DataSource2/110.42.227.83/mes_base_v23_factory_b_test/LOCAL；源45表完整摘要前后完全一致，原901闭环/90,100条报工/100,011核心事实不变。DEV中本轮开始前 **123,886条既有记录逐主键/全部规范化字段均无缺失或改变**，包括前一轮验收新增事实；全部新事实只关联本轮69个独立工单/Execution。新增2,713条报工（2,560计时+116预热+6基线/诊断+31并发事实），逐UUID、数量、ID和操作者6246匹配正式响应/发送日志；18次成功报工重放未新增事实。69次投产合计690，生产原料消耗1380，140条新库存动作另含其他入库5/其他出库4，最终净库存1留在TEST库存2002，其余新库存余额0；这些其他出入库不是正式生产完工。未伪造质量放行/完工或把新部分生产算成完整闭环。[最终事实/绑定/全量保护核验](evidence/2026-10-19-production-lock-for-update/final-verification.json)、[正式数量及两实例状态读取](evidence/2026-10-19-production-lock-for-update/final-http.json)。

`mvn -B -ntp verify`最终SUCCESS，757项中46项按环境条件跳过，711执行，0失败/错误；46个未执行的旧Live验收不冒充通过。本轮DEV验收是单独真实HTTP/MySQL证据。`scripts/smoke-packaged.py`通过；30个改动Java文件native formatter检查通过，git diff --check通过。初期新生成契约测试切片忽略了XML属性排序、旧Mock仍按UPDATE/COUNT设置返回值，已修正为完整锁SELECT片段与锁定返回行，最终完整测试通过，未保留失真的旧桩。[测试摘要](evidence/2026-10-19-production-lock-for-update/test-summary.json)。

自建18082/18083 JVM及17479 Redis已停止；用户8081/PID40599、5173/PID12465保留，原application.yml保持本轮开始时哈希。测试库、新69组资源、1条工单作废事实、库存净余额1及全部不可变事实保留供复验，不做破坏性清理。私有配置/凭据/备份/逐行原始hash/冷启动完整诊断不提交Git。[收尾](evidence/2026-10-19-production-lock-for-update/cleanup.json)、[工作树保护](evidence/2026-10-19-production-lock-for-update/workspace-preservation.json)。

**本轮 GO：24处纯锁UPDATE已替换，锁顺序/校验保留，DEV跨JVM数量/幂等/状态/回滚/库存竞争通过，SQL与性能对照完成。** 不宣称全部未来死锁场景、质量大事实并发、缺行/范围锁、长时间SLA或所有24个入口的逐一真实竞争均已覆盖；本轮真实竞争集中于生产链和仓储，其他引用入口有静态链路与定向/完整测试证据。回滚可切回冻结A产物并审查还原本轮Mapper/Service/生成器改动，不清理不可变TEST事实、不覆盖用户application.yml或其他工作。未提交/推送，未修改源Factory B数据、绑定、Druid、隔离配置或Flyway。
