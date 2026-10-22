# 旧 MES 历史生产数据 V2.2：试迁与自动批量验收

审查日期：2026-10-20。本文记录真实 DEV 数据追加，承接 [V2](2026-10-20-legacy-report-driven-migration-v2-acceptance.md) 和 [V2.1](2026-10-20-legacy-production-migration-v21-acceptance.md)。本轮已完成最终对账及全量重放；执行过程和性能优化记录保留，最终结论见 A～K 表。

## 执行范围与冻结依据

- 用户授权：先完成 10～20 张代表性普通工艺工单试迁；整体 GO 后自动执行剩余冻结 APPROVABLE 范围，无须再次人工确认。
- 仅访问指定旧 MES 来源库（只读）和 DEV 性能测试目标库，Factory 为 `TEST_DEV_PERF_20261019`。真实连接、生产编号和账号仅保存在私有证据中。
- HEAD：`8c4b0b6ad1e68dd0af241d7ef0e79c93b4b0609c`；执行前工作区干净。
- 已核对实例 UUID、Factory revision 1、DataSource 9 / revision 2、启用 LOCAL 绑定。正式 Flyway V40/V41/V42 均成功，校验和分别为 2045972475、1137734570、1207799484；三个源文件与 V2.1 已验收构建字节一致。44 个当前触发器与 V2.1 最终对象逐项一致。
- 新采集的 13 张来源业务表与 V2 冻结内容逐行一致，包括 170,484 条报工及 1,185 条 broken-step。重新使用当前来源和目标人员、产品、工序及 Mapping 审核，原 564 张全部仍符合准入：8,375 个 Execution、122,155 条报工；未将其他分类升级。
- 两次目标逐主键、全字段摘要一致，无追加、修改或删除；现场未见其他目标写入事务。用户现有服务没有被重启、停止或换包。

## 受控执行与审批

新增入口只存在于 `src/test` 的 `LegacyAuthorizedBatchLauncher`，不进入生产 JAR，不注册生产 HTTP 导入接口、定时任务或启动导入钩子。由显式 CLI 启动，冻结完整 manifest 哈希、用户授权文本哈希、每条命令文件哈希、来源组件哈希、快照和 Factory。

执行身份来自正式 `/api/session` 登录，随后由既有 `TokenService.resolve` 验证当前会话和账号，检查启用的平台角色；不接受任意操作人 ID。每个组件仍经正式 Service 的 prepare、approve、execute/append，typed command hash 与技术审批范围由原 Service 冻结。阶段门禁和逐组件许可用于在单工作进程中完成有界检查点。命令和依赖使用独立冻结构建，运行过程中不读取可被 Maven 覆盖的 target/classes。

历史数量规则仍为 `report-driven-local-v2`。不修改已发布迁移，不更新旧事实，不创建库存、当前投入、正式质量放行、交接、接收或完工事实。

## 第一部分：代表性试迁

冻结 16 张未完整迁移工单，共 202 个 Execution、2,881 条报工。覆盖单卡、多卡、少/多工序、无不良、5704、5705、混合损耗、SELF、UNI、多次报工、晚申报不良、共享来源 Routing/Operation/Material 和大报工量。目标 Routing 继续按工单冻结实际路径；跨工单共享的目标 Operation/Material 按稳定 Mapping 复用，不冒称目标 Routing 已合并复用。

正式 HTTP 验收读取工单列表/详情、Execution、锁定路径、原始报工账本、历史详情及权威数量投影，并执行历史只读负例。报工账本补齐分页，并按原人员、时间、净数量的多重集合核对重复记录数量。

首个样本 26 个 Execution / 919 条报工已提交并重放同一身份，事务耗时 68,018 ms。首次协调器因证据父目录未创建而停止，尚未发出 HTTP 验收请求；修正目录准备后从原冻结命令和稳定 Mapping 恢复，未修改或删除已提交事实。此项是验收工具准备错误，不记为数据库事务回滚或基础设施重试。

第 10 个组件检查点再次暂停：协调器最初遗漏 `workorder_erp_extra`，把 10 行标准空扩展误列为未知追加。核对实际 Service 的 `facts.save(order, true)` 和数据库后，确认这些行与本轮新建 LOCAL/HISTORICAL 工单一一对应，`external_id`、`external_code`、`provider`、`binding_key`、`configuration_revision` 全为空。检查点随后增加严格的一一对应及全空校验，未修改事实、跳过来源门禁或允许真实 ERP 副作用。两次暂停均保留原始失败文件和恢复日志。

16 个样本已完成正式 HTTP 核对；独立来源到目标对账覆盖 202 个 Execution、2,881 条报工、2,283 个节点、227 条历史质量证据，全部匹配。原 901 闭环的 17 项审计全部通过，100,011 条核心事实保持一致。最终阶段门禁仍以协调器记录为准。

试迁最终结论：**GO**。`trial-GO.json`、`trial-mapping-GO.json` 和 `trial-901/audit-results.json` 已形成完整门禁证据。样本代号与真实来源的对应关系仅保存于私有 selection / command 文件。

| 样本 | Execution | 报工 | 历史质量证据 | 首次事务 ms | HTTP / 重放 |
|---|---:|---:|---:|---:|---|
| T01 | 26 | 919 | 28 | 68018 | PASS / 同身份 |
| T02 | 1 | 2 | 1 | 1046 | PASS / 同身份 |
| T03 | 1 | 4 | 0 | 1074 | PASS / 同身份 |
| T04 | 23 | 150 | 0 | 16155 | PASS / 同身份 |
| T05 | 10 | 149 | 13 | 10687 | PASS / 同身份 |
| T06 | 9 | 153 | 6 | 10200 | PASS / 同身份 |
| T07 | 9 | 153 | 14 | 11482 | PASS / 同身份 |
| T08 | 21 | 147 | 15 | 11383 | PASS / 同身份 |
| T09 | 21 | 147 | 16 | 11565 | PASS / 同身份 |
| T10 | 9 | 147 | 17 | 8981 | PASS / 同身份 |
| T11 | 11 | 154 | 22 | 9762 | PASS / 同身份 |
| T12 | 10 | 146 | 28 | 10163 | PASS / 同身份 |
| T13 | 9 | 154 | 10 | 9482 | PASS / 同身份 |
| T14 | 11 | 156 | 27 | 10202 | PASS / 同身份 |
| T15 | 11 | 156 | 16 | 9951 | PASS / 同身份 |
| T16 | 20 | 144 | 14 | 11871 | PASS / 同身份 |

## 第二部分：自动批量导入

第一阶段整体 GO 后，协调器已经自动开放剩余 548 个组件，未再次等待人工确认。最终全部 564 张均成功落地或确认复用，完整结果见 A～K 表。

实际复用 3 张已完整映射的 APPROVABLE 工单（12 个 Execution、161 条报工）；新增 561 张 WorkOrder、8,363 个 Execution、121,994 条 Report。既有其他分类的部分历史卡仅受保护，不继续扩围。

检查点除保留旧数据保护，还按冻结命令推导每个表、Mapping 和来源锁的精确追加数量，逐检查点核对。累计数据增长后，摘要采集去掉了原保护函数从未比较的“新增主键对应哈希”计算：仍对本轮开始前全部 **143,183 个主键的全部字段**计算哈希，所有新主键仍完整计数；最终整库重放前后继续对全部当前行、全部字段计算哈希。与原全量算法逐主键对比，143,183 个旧字段哈希全部一致，旧数据保护及追加计数判定完全相同。该优化仅位于私有只读验收脚本，不修改 Service、审批范围、数据库对象或业务门禁。证据为 `checkpoint-optimization-equivalence.json`、原完整采集及优化只读采集。

### 执行中性能诊断（2026-10-20，用户要求继续导入）

前 186 个成功组件覆盖 54,607 条来源报工，首次导入事务累计 4,214.3 秒，组件处理累计 4,952.2 秒；首次事务约占组件处理时间 85.1%，其余 737.8 秒包括文件读取、解析、prepare/approve 和即时重放。事务中位数 17.55 秒、P95 54.84 秒，最大 156.331 秒。上述累计不包含组件之间的数据保护检查点及 901 审计，覆盖条数含复用事实，不能作为纯新增 INSERT TPS。

源码确认导入未采用集合化访问：每条报工调用一次 `users.requireExists`，在业务事务内进入中心库独立只读连接/Session 查询人员；随后分别插入 Report 和来源 Mapping。每张流程卡又调用正式数量投影，读取路径及相关聚合。即时重放对每条报工逐条查询 Mapping，且该查询包含原始 JSON 证据字段。`prepare`、`approve`、首次执行和即时重放均重复执行命令校验；其中报工证据成员校验逐条扫描卡内原始报工集合，存在卡内平方级比较。运维入口串行执行，每 10 个组件保护检查、每 100 个批量组件审计，最终另有全量重放和完整摘要。

真实 JVM 两次只读线程采样均观察到主线程等待 MySQL Socket 响应，分别位于来源锁写入和既有工单校验读取；这支持串行数据库往返是实际耗时来源，但不足以给人员校验、INSERT、触发器、网络和提交分别分摊耗时。一次 InnoDB 事务快照未见等待起点，不能据此排除间歇锁等待；当前 Performance Schema 按目标 Schema 未提供可用语句摘要，尚无逐 SQL P95 或完整锁等待链。不能把全部耗时归因于数据库硬件、网络或 V41 触发器。

诊断结论：迁移实现存在明确的逐条 I/O 和重复校验问题，验收工具也有额外开销。诊断时未修改运行产物；随后用户明确要求优化代码再继续迁移，实施记录见下节。私有证据：`performance-diagnosis.json`、`performance-thread-live-1.txt`；首次即时线程输出仅在工具记录中，早先沙箱线程采样失败单独保留，不冒充成功证据。

### 用户追加授权后的代码优化与恢复

在第 200 个组件完成后写入 STOP，运维 JVM 在事务边界正常关闭；停止的只有本轮自建协调器及等待审计进程，未停止或重启用户已有服务。完整保存暂停时 48 张目标表的全部主键和字段摘要，冻结命令、审批哈希、来源快照、规则版本及 V1–V42 均不变。

实际修改：人员按组件去重后每批最多 200 个 ID，通过既有中心独立只读窗口核验，保留失效账号的历史存在语义且不缓存跨命令结果；Report、路径节点及来源 Mapping 使用参数化批量插入，每批最多 128 行，Mapping 另按原始证据 UTF-8 大小约束分块；批量报工使用 JDBC 返回的真实主键回填，拒绝主键缺失或重复，不按连续主键自行推算。重放按 128 个稳定来源键分块读取身份、哈希、父级及版本，批量读取对应 Execution，逐项校验原条件；冻结证据的成员查找由嵌套扫描改为 JSON 节点集合。来源锁顺序、审批、每张工单原子事务、正式数量投影及数据库门禁继续保留。仅手工修改的 `MesExecutionPathNodeMapper` 移除生成归属标记。

运维入口按已保存且校验哈希的成功结果恢复，已完成的 200 张不重新导入，最终全量重放仍覆盖全部 564 张；最终重放每张只执行一次正式完成态校验并和首次返回的身份比较，避免额外重复执行 prepare/approve 和第二次全量执行。新增记录本轮运行产物哈希、首次事务与即时重放的 SQL prepare 次数；该计数不包含连接级 SET/COMMIT，不能当作全部网络往返数。

标准 `mvn -B -ntp verify` 成功，新增测试覆盖批量主键关联、参数绑定、来源 Mapping 冲突后的事务回滚、分批人员核验与中心读窗口，以及冻结证据被替换或缺失时的拒绝。31 项 Python 迁移测试和 10 项打包探针通过。真实 MySQL 对两张未迁冻结组件执行正式 prepare/approve/execute，并在外层 Spring 事务中核对每条报工的返回主键、来源请求身份、数量、人员及时间后回滚；再对已完成 200 张逐张重放，48 张表的全部主键和全部字段与暂停快照完全相同。另有多行非法历史报工 INSERT 被实际 V41 拒绝的负例，探针事务回滚。上述回滚探针不计入实际导入成功数、失败数或新增事实数。

曾试验将数量投影集中到整单写入后执行，但实际配置为 `LocalCacheScope.STATEMENT`，SQL 计数没有减少，因此撤回该改动和缓存收益判断；不同试验耗时不能直接归因于该调整。恢复编译后的 Service 及 ReplayState class 字节与已完整验收的批量优化 JAR 完全一致。最终采用 JAR SHA-256 `7e901b3132662d3776925f77e2e6b69af63185321f1234cd9f9ac1a03581cff4`，独立冻结 classpath 不引用可变 target/classes。验证首次启动时误保留 STOP 导致运维 JVM 在接单前正常退出，重启后完成验证；无数据库写入失败，也未清理已提交事实。

第 201 张起恢复导入。前 3 张补做正式 HTTP 验收，前 10 张增加来源到目标逐项对账、保护检查及原 901 审计，全部通过后沿用原批量方式。用户随后明确要求按当前方式继续导入剩余数据，保持每张工单独立事务、每 10 张检查点，不增加并发或合并事务。最终效果、增量及完成状态仍以最终审计为准，不以测试通过冒充批量完成。

优化后前 10 张新增 102 个 Execution、1,729 条报工，首次事务累计 32.1 秒，组件处理（准备、解析、审批、事务和即时重放）累计 41.8 秒；该统计不含正式 HTTP、检查点和审计，样本与早期工单不同，不能当作严格控制变量的提速倍数。独立对账工具首次将历史路线产品关联中可空的编码/名称快照当成必填，导致协调器在 210 张检查点暂停；实际导入 Service 从 V2 起按 material_id 建立关联，产品编码、名称、规格、单位还保存在目标 Material 和冻结来源证据。修正只读验收契约后仍要求唯一关联及正确产品身份，并核验任何非空快照；未填充或改写已有历史字段。复用暂停时一致性读快照，210 张、3,793 个 Execution、60,761 条报工、49,700 个节点、5,624 项质量证据全部对账通过；另重新完成保护检查和原 901 审计后开放后续组件。此项为工具断言错误，未发生事实事务失败；`optimization-first10-GO.json`、`audit-contract-correction.json` 与原失败现场均保留。

### 10 单入口批量读取与内存分组（用户追加要求）

用户明确要求每 10 张工单在入口取得关联数据，在内存按工单、流程卡、路线分组，并在最外层复用物料、单位、工序及已发布路线等引用。新增显式、最多 10 单的 BatchReferences，通过冻结命令哈希、线程及已固定的 Factory/业务库身份和版本限制用途；不建立全局缓存或新的配置权威。每张工单仍独立事务，新增映射和引用仅在 Spring 实际提交后发布到批次缓存，回滚不发布，批次结束清理。锁内仍刷新未解决的来源身份，避免跨事务信任“之前不存在”的结果；审批、当前 LOCAL 模式、顺序来源锁及工单锁保持原校验。

流程卡新增事实尚不存在，无法在写入入口读取；写后数量守恒验证因此按单内全部新流程卡统一取得 Execution、实际路径、路线节点/边、历史投入与节点证据、净报工聚合及正式质量结果，再按 Execution 分组。单卡和批量入口共同调用原有数量组装及 project 计算，没有另写数量算法。新批量接口限定历史只读使用，质量读取有明确集合上限；普通生产原入口保留。

第 296 张后原数据保护检测到 1 条原有库存行变化并自动停止。用户确认是自己修改备注测试缓存，授权标记跳过。利用原备份的类型化字段重构，已验证变化仅涉及 remark 和 update_time，其他字段重构后的整行哈希与最初摘要一致。只接受这一主键已观察到的精确旧哈希到新哈希变化，不忽略整个库存表，也不放行该行后续变化；例外保存在 authorized-row-adjustment.json，初始摘要及失败现场不改写。迁移本身未执行库存写入或恢复用户备注。

为隔离并行的私有文件存储修改，以已提交 85ddc30044fa13dee3d94821ee4dce37c07fab81 为基线建立私有源码快照，只复制本轮明确文件清单，标准 Maven 不使用私有依赖仓库。源码清单及哈希单独保存。Git 归档不包含本机忽略的 dev Profile，第一次完整测试暴露既有配置测试依赖该文件；仅在隔离快照补入不含凭证的 mes.business.enabled 默认值，真实运维仍使用明确指定的外部配置。一次既有库存竞争 Fixture 断言失败，定向重跑通过；保留原失败及完整重验结果，不修改该测试或其业务代码。

真实同源回滚对照选取两张尚未迁入的冻结工单，分别 57 卡/395 条报工和 49 卡/897 条报工。原优化运行产物的首次执行分别为 13,081ms/17,502ms；SQL prepare 分别 1,374/1,204 次，其中 SELECT 933/811 次。新增批量实现的首次执行分别为 5,943ms/7,101ms，SQL prepare 降至 467/422 次，其中 SELECT 仅 26/29 次。入口两单共享预加载另耗时 1,646ms、5 次 SELECT，不计入上述单单事务；对照为相同冻结输入的单次真实回滚测量，存在 JVM/数据库预热差异，不承诺所有工单固定提速倍数。新增实现对两单各自回滚后复用同一 BatchReferences 再试一次，两次均成功，验证回滚没有污染缓存中的持久化身份；每条报工的真实 JDBC 主键与来源身份、数量、人员、时间逐项核对后再回滚。

完整隔离构建 `mvn -B -ntp verify` 最终通过，801 项测试中 48 项显式跳过、零失败/错误；另 10 项独立打包探针通过。随后仅为测试启动器补入同缓存回滚重试探针，经标准 Maven 编译打包。冻结新 JAR SHA-256 为 d173b27c4bce0b7052a9c4cddec38f044821536c4947cbb82bb460b8e3164c13，测试启动器 class 单独冻结，不进入生产 JAR。已导入 296 张全量重放通过，真实回滚试迁与重放前后 48 张表的全部主键、全部字段摘要完全相同，batch-prefetch-validation-GO.json 已产生。随后恢复未完成工单，首 10 单重新验证正式 HTTP、来源对账、数据保护和原 901 审计，最终批量效果仍以完成后的实际检查为准。

批量读取后仍有较大耗时差异：恢复阶段已有约 60～68 秒的大工单，不能把两单回滚样本的耗时推广到全部输入。一次真实主线程采样位于 JDBC INSERT 前的 `ConnectionImpl.isReadOnly → NativeSession.queryServerVariable` Socket 等待；实际打包的 Connector/J 9.7.0 字节码确认，在对应状态配置下会读取 `@@session.transaction_read_only`。这类驱动内部状态查询没有经过 MyBatis prepare，因而不在上述 SELECT 计数内；SQL 次数明显减少不等于全部数据库往返只剩同样数量。剩余数百次事实写入、触发器及连接状态往返仍是后续测量对象。本轮保留已验收的连接及事务状态行为，未为速度修改连接状态缓存参数或 MySQL 参数。另对 100 个真实历史 Execution 的新批量查询查看执行计划：历史证据使用 idx_legacy_target/idx_legacy_parent，报工聚合使用 idx_execution_path_order/idx_report_execution_node；聚合仍有有界临时分组。证据为 batch-prefetch-live-main-thread.txt、connectorj-ConnectionImpl.txt、batch-prefetch-explain.txt。

检查点元数据查询另做了等价优化：原来每列通过相关子查询取得表 ENGINE，改为 COLUMNS 与 TABLES 的 LEFT JOIN，投影字段、来源/目标 Schema 范围及排序不变。真实实例两种 SQL 返回的 4,344 行逐行、顺序及输出字节 SHA-256 完全相同；单次测量从 3.842 秒降至 0.254 秒。31 项 Python 迁移测试全部通过。后续新启动的检查点采用该只读查询；已运行的 Java 产物与业务事务不变，全部旧行哈希、追加主键计数及最终全量保护不减少。证据为 metadata-join-equivalence.json、metadata-join-before.sql、metadata-join-after.sql 和 metadata-join-python-tests.log。

30 秒自建 JVM JFR profile 随后给出了更强的阶段证据：主线程阈值以上 SocketRead 中，64 次属于 MyBatis 写语句响应等待，合计 28.362 秒；1 次驱动 queryServerVariable 状态检查慢读仅 0.012 秒。该样本不能将驱动额外查询判为主要卡点；这段实际时间主要在等待事实写入响应。JFR 只记录配置阈值以上的 SocketRead，不覆盖全部查询次数，不能区分数据库执行、触发器、磁盘和网络各自的耗时。每卡仍有多组 INSERT/Mapping 写入，整工单进一步合并写入仍有后续优化空间，本轮不为继续提速改动已冻结写入契约。证据为 batch-prefetch-io.jfr、batch-prefetch-io-sockets.json、batch-prefetch-io-category-summary.json。

### 最终 A～K 验收结论

| 项 | 实际结论 |
|---|---|
| A | 16 张代表性试迁全部 GO，正式 HTTP、来源对账、数量守恒、历史只读、同身份重放及保护检查通过。 |
| B | 试迁 GO 后自动执行其余 548 张；恢复门禁通过后继续冻结剩余范围，未要求用户逐批批准。 |
| C | 564/564 张 APPROVABLE 均成功落地或确认复用；新增 561 张工单，完整复用既有 3 张；隔离 0、业务失败 0。 |
| D | 8,375/8,375 个 Execution 成功覆盖，其中新增 8,363 个、复用 12 个。 |
| E | 122,155/122,155 条来源报工成功覆盖，其中新增 121,994 条、复用 161 条。 |
| F | 独立来源到目标对账逐 Execution、逐节点验证投入、工序总加工、有效损耗、净产出及上下游传递全部守恒；历史 availableQty=0，不可正常报工。 |
| G | 全部 564 张完成态重放通过；重放前后 48 表全部当前主键和全部字段完全相同，零新增、零修改、零删除、零重复。 |
| H | 原 901 闭环 17 项审计及初始 S1/S2/P1/P2/P3、Mapping、性能事实、库存余额与动作均通过保护；唯一例外为用户明确授权的 1 条库存备注测试及更新时间变化，精确哈希记录，不放宽其他数据。 |
| I | APP 范围覆盖 12,077 项历史质量证据，其中新增 12,057 项、既有 20 项；只新增来源证据 Mapping，正式质量、库存动作、投入、领退料、放行、交接、接收、入库及完工事实零新增。 |
| J | 来源仍有 4,045 个 Execution、48,313 条报工未迁移，保持原审核/拒绝/锻接分类，见下表。 |
| K | 首次成功事务累计 8519.2 秒，组件累计 9669.4 秒；从冻结到最终审计墙钟 5.15 小时，最大事务 156.331 秒。墙钟包含优化、构建、回滚探针、人工说明和审计等待；无失败业务事务重试或业务冲突。 |

重放前后各有 649,414 个当前目标主键的真实 64 位 SHA-256 字段摘要；两个快照均为全部当前行、全部字段，没有追加行计数占位摘要。

累计首次事务吞吐为 14.32 条新增报工/事务秒；该值混合多个实现阶段，不代表新版本单独吞吐。组件耗时包含准备、解析、审批、首次执行、入口预加载及即时重放，不含组件之间的 HTTP 验收、保护检查、901 审计或停机准备；首次事务时间也不含入口批次预加载。

| 实现阶段 | 成功组件 | 新增报工 | 首次事务累计秒 | 新增报工/事务秒 | 最大事务秒 |
|---|---:|---:|---:|---:|---:|
| 初始实现 | 200 | 59032 | 4588.2 | 12.87 | 156.331 |
| 第一轮批量写入优化 | 96 | 18933 | 1161.3 | 16.30 | 108.251 |
| 10 单预加载及批量数量读取 | 268 | 44029 | 2769.6 | 15.90 | 100.366 |

不同阶段工单不同，阶段聚合不能当作控制变量的提速倍数；同源两单的回滚对照和入口预加载成本见前文。SQL prepare 计数不包含连接 SET、COMMIT 或触发器内部语句。

成功回执去重前存在 11 条原试迁恢复产生的同身份重复回执；统计按阶段和冻结命令哈希去重。共 10 次显式回滚探针事务，包含撤回方案及同缓存回滚后重试验证，均不计入导入失败或新增事实。另保留期望的 V41 非法批量写入拒绝负例。工具目录准备、空 ERP 扩展白名单、可空路线产品快照断言和用户备注测试造成的暂停均保留现场及恢复证据，没有通过清空、覆盖或删除业务事实恢复。

| 未完整迁移分类 | 工单 | 未迁 Execution | 未迁报工 |
|---|---:|---:|---:|
| NEEDS_REVIEW，路径/人员/数量等需核实 | 83 | 741 | 11,756 |
| REJECTED，维持拒绝 | 70 | 927 | 16,301 |
| FORGE_EXCLUDED，锻接不扩围 | 146 | 2,377 | 20,256 |
| 合计 | 299 | 4,045 | 48,313 |

299 张未完整迁移工单中 2 张原本已有受保护的部分历史，297 张没有目标工单；不以部分既有 Mapping 冒充整单完成。全部 1,185 条 broken-step 来源记录保持不变。

最终确认来源业务表及人员身份快照未改，实例、Factory/Owner 路由、Flyway 校验和及 44 个触发器保持原状态。暂停时已完成的 296 张数据同样逐主键、全字段保护到最终。正常 LOCAL 链路由原 901 真实事实审计及本轮完整 Fixture/HTTP 测试支持；本轮未重新创建一套普通 LOCAL 领料到入库实物闭环，也未进行新的浏览器 UI 或真实 ERP 验收。

代码变更包括导入批次引用、历史数量批量读取/内存分组、对应 Mapper 和同一数量投影复用、固定事务身份的只读暴露，以及定向单元/SQL/运维探针；没有 Schema 变更。并行私有文件存储改动不包含在本轮冻结运行产物中。运维独立 JVM 在最终 STOP 后正常关闭；用户服务未由 Agent 停止或重启，Agent 未执行提交、推送或部署。代码回退不撤销已提交历史事实；保留稳定 Mapping 后按原身份恢复，禁止删除事实作为回滚。


## 私有证据索引

根目录：`.local/legacy-v22-20261020/`，不提交原始账号、生产编号、来源数据或会话令牌。

- `before/`、`window-check/`：新冻结来源、目标 metadata 和全字段摘要；`source-comparison.json`：来源对比。
- `binding-flyway.txt`、`migration-files.json`、`triggers-before.jsonl`：绑定、版本、源码和实际对象。
- `admission.json`、`candidate-entries.json`、`selection.json`、`commands/`、`manifest.json`：准入、覆盖、命令和范围冻结。
- `authorization.txt`：用户授权文本；`artifact.json`、`artifact/`、`classpath`：稳定构建及独立运维入口。
- `verify.log`、`python-tests.log`、`format.log`：构建和工具验证。
- `results.jsonl`、`run*.log`、`app.log`、`http/`：真实组件执行、HTTP 和恢复过程。
- `trial-*-protection/`、各 `*-checkpoint-*/`、`*-901*/`：数据保护和原有闭环审计，实际文件按执行进度产生。


- `batch-prefetch-source-manifest.json`、`batch-prefetch-build-profile.json`、`artifact-batch-prefetch.json`：隔离基线、明确源码、无凭证构建条件及冻结产物。
- `batch-prefetch-verify*.log`、`batch-prefetch-test-summary.json`、`batch-prefetch-smoke.log`：完整测试、原失败/重验、最终编译与打包证据。
- `compare-baseline.log`、`batch-prefetch-real-comparison.json`、`batch-prefetch.jsonl`、`batch-prefetch-explain.txt`：同源真实回滚 SQL/时间对比、批次预加载成本和实际索引计划。
- `batch-prefetch-before/`、`batch-prefetch-after-validation/`、`batch-prefetch-validation-GO.json`：同缓存回滚重试、296 张重放及 48 表全字段零变化。
- `authorized-row-adjustment.json`、`concurrent-inventory-change.json`、`bulk-checkpoint-280-user-note-confirmed/`：用户授权的精确单行变化及恢复保护检查；不覆盖最初摘要。
- `batch-prefetch-first10-GO.json`、`http-batch-prefetch/`、`batch-prefetch-first10-901/`：新版本首 10 单增强验收。
- `before-replay/`、`after-replay/`、`replay-zero.json`：564 张全局重放前后全部当前事实保护。
- `reconciliation.json`、`final-source-and-target/`、`final-protection.json`、`optimization-paused-facts-final-protection.json`、`final-source-protection.json`：最终独立对账、初始和暂停事实保护、来源不变。
- `bulk-final-901/`、`approved-batches-final.jsonl`、`schema-final.txt`、`triggers-final.jsonl`、`metrics-stage-final.json`、`final-summary.json`：原闭环、完整技术批次、对象、性能及最终 GO。

- `metadata-join-before.sql`、`metadata-join-after.sql`、`metadata-join-equivalence.json`、`metadata-join-python-tests.log`：4,344 行元数据结果逐字节等价及 31 项 Python 测试。
- `batch-prefetch-live-main-thread.txt`、`connectorj-ConnectionImpl.txt`、`batch-prefetch-io.jfr`、`batch-prefetch-io-sockets.json`、`batch-prefetch-io-category-summary.json`：驱动状态查询与实际写入等待的分层证据；JFR 仅使用本轮自建 JVM 的主线程阈值以上 SocketRead。

## 变更与证据边界

初始试迁与前 200 张导入未修改生产代码；随后按用户追加授权修改导入 Service、既有人员目录核验与 Mapper 的批量访问，并增加定向测试。未新增 Schema 或改写迁移脚本，未修改 MySQL 持久化参数、停用触发器、调用 ERP、重启用户服务或提交推送代码。独立运维验收启动器、HTTP 验收工具和本报告同时保留本轮变化。数据库事实和最终验收结论以本轮实际结果为准，旧报告、构建成功和工具源码不替代最终运行证据。


## 2026-10-21 — `.local` 清理及工单转换链路复审

### 本次范围与证据

用户要求清理 `.local` 无用文件、特别复核 backup，并提供转换入口用于人工审查。本次仅进行了本地文件整理、当前源码静态审查及历史运行证据复核，没有修改业务代码、访问或写入数据库、执行迁移、重启服务。开始时分支 main，HEAD 为 `85ddc30044fa13dee3d94821ee4dce37c07fab81`；保留所有已有未提交修改。

当前 19 个相关源文件中，18 个字节哈希与验收产物源码清单一致；ProductionQuantityMapper.xml 存在既有排版差异，XML 解析后去空白序列化与冻结源码一致。本次未修改该文件。此前真实回滚对照与 JFR 属于 10 月 20 日冻结产物证据，本次未运行新的性能基准，不把优化建议写成已经取得的收益。

### 清理结果与备份保留

.local 从约 **17.63 GiB** 降至 **10.23 GiB**，按实际目录占用回收约 **7.40 GiB**；df 可用空间由约 17 GiB 增至约 25 GiB。统计只针对本仓库 .local，没有扩大到其他项目或系统目录。

- 删除 1,873 份 SHA-256 完全相同的 JAR 实际副本，原路径改为指向保留副本的相对符号链接；没有使用硬链接。后续删除构建目录时其中 7 个链接同时删除，剩余 1,866 个全部验证目标存在。独有历史 JAR 版本保留，最终验收 JAR 哈希仍为 `d173b27c4bce0b7052a9c4cddec38f044821536c4947cbb82bb460b8e3164c13`。保留副本不得单独覆盖或删除，后续整体清理需同时检查符号链接引用。
- 103 个历史大型 JSONL/旧 JFR JSON 导出压缩为原路径加 .gz。逐个校验解压后 SHA-256，并再次确认原件未发生变化，才删除未压缩原件；唯一证据内容保留。某些旧 Python 工具仍要求原文本路径，复跑前按清单恢复对应输入。
- 删除 `legacy-v22-20261020/batch-prefetch-source/` 下 7 个可重新生成的 target 构建目录；删除前将 406 份测试报告归档并逐个比对内容哈希。19 个冻结源码文件及最新验收运行 JAR 完整保留，外部没有指向这些构建目录的符号链接。
- `core-optimization-20261019/before.sql.gz` 约 4.9 MiB，与 `dev-perf-copy-20261019/factory-b-v39-consistent.sql.gz` 约 4.6 MiB，是不同 SHA-256 的恢复点，体积小且有恢复价值，均保留并通过 gzip 完整性和原 SHA 校验。其他人员转换备份、私有配置、SQLite journal、命令和授权 manifest 保留，不因名称带 backup 就删除。
- V2.2 的 before、final-source-and-target、before-replay、after-replay 四份完整 snapshot.jsonl 仍保留原路径和未压缩格式。其他中间快照为可恢复压缩归档，未抹去失败/暂停和用户库存备注例外证据。

操作清单、原始内容哈希、恢复工具及最终空间统计在 `.local/storage-review-20261021/`：`plan.json`、`actions.jsonl`、`summary.json`、`build-cleanup.json`、`outcome.json`、`README.md`。恢复示例（参数相对于 .local）：

```sh
python3 .local/storage-review-20261021/cleanup.py --restore legacy-v2-20261020/before/snapshot.jsonl
```

该工具只恢复文件，不连接数据库，拒绝覆盖已有原件。历史 final-report-complete.json 的报告 SHA 继续代表 10 月 20 日时间点，本次追加报告的 SHA 单独保存在 storage-review 目录的 document-receipt.json。

### 人工审查入口及调用顺序

| 顺序 | 文件与函数 | 建议重点 |
| --- | --- | --- |
| 1 来源读取/候选 | `scripts/legacy/report_driven.py:335 capture`、`:183 convert`、`:441 main`；`scripts/legacy/conversion_v2.py:47 convert_v2` | 集合读取、按工单/卡/路线分组，投入/净报工/损耗证据及隔离清单 |
| 2 命令构建/准入 | `scripts/legacy/prepare_component.py:12 build_command`；`scripts/legacy/review_v21.py:13 audit_component`；`.local/legacy-v22-20261020/prepare.py:13` | 冻结数据、用户映射、目标冲突、重复主数据索引构造 |
| 3 真实批量入口 | `mes-admin/src/test/java/dev/issuefixture/LegacyAuthorizedBatchLauncher.java:110 main`、`:220` 批次组织、`:234 preloadBatch`、`:245` 准备/审批/执行/即时重放 | 每 10 单预取、每单独立事务、提交后才发布缓存；这是测试源码中的显式 CLI，没有生产 HTTP 导入接口 |
| 4 批次预取/执行 | `mes-business/src/main/java/dev/mesbase/business/legacy/service/LegacyHistoryImportService.java:257 preloadBatch`、`:399 executeReviewed` | Factory/库身份及命令哈希约束、来源锁、映射缓存命中与锁内补查 |
| 5 主要写入热点 | 同 Service `:575 appendExecutions`、`:846 insertMappings`；`legacy/dao/LegacyMigrationMapper.java:17` | 每卡分开的 Execution/Path/Node/Report/Mapping 写入、128 行和 512 KiB 分块、真实主键关联 |
| 6 写后集合校验 | `production/service/ProductionQuantityService.java:90 getHistoricalExecutionQuantities`；`production/service/ExecutionPathService.java:103 viewBatch`；`quality/service/QualityTreatmentService.java:201 nodeTotalsBatch` | 取得单内全部新卡相关数据，再按 Execution/节点在内存组合；复用既有 project 公式 |
| 7 运行与保护 | `.local/legacy-v22-20261020/run.py:12 checkpoint`、`:28 audit`；`checkpoint.py`、`reconcile.py` | 每单 HTTP 校验、每 10 单保护、每 100 单旧闭环审计及最终全量重放；这些耗时不在单单首次事务指标内 |

业务 Java 的表内完整路径前缀为 `mes-business/src/main/java/dev/mesbase/business/`。`LegacyHistoryController` 仅提供历史读取接口，不是迁移写入入口。

### 剩余优化空间（建议，尚未实施）

1. **最高优先：在单工单内跨卡合并写入。** appendExecutions 仍对每卡分别插 Execution、Path、Mapping，再分别批量插节点、报工及其 Mapping。即使每卡仅几条报工，也会产生各自的 SQL；可按父对象→子对象的依赖顺序收集整单数据，再有界批量提交，保留单工单事务。必须验证 MySQL/JDBC 返回主键与每条源记录的一一对应、FK/触发器顺序、来源幂等与回滚。不能假定自增 ID 连续，不能只靠循环顺序猜关联，也不能把 10 单合成一个大事务。此前 JFR 阈值以上主线程慢读中，64 次 MyBatis 写响应等待共 28.362 秒，驱动状态查询慢读仅 0.012 秒，因此继续减少写往返更值得优先验证；该证据不能区分网络、数据库执行、触发器和磁盘各自的贡献。
2. **批量来源锁初始化，降低固定开销。** executeReviewed 对去重的每个来源键分别 ensureLock 再 FOR UPDATE，至少两个语句/键，重放也经过此路径。可研究集合初始化和集合锁定，但必须验证缺失键的跨 JVM 竞争、稳定锁序、死锁与回滚，不能直接删锁或仅凭 ORDER BY 宣称锁顺序已得到保证。收益未测量，优先级低于事实写入。
3. **去掉无消费者的预加载。** BatchReferences.units/operations 只在 preloadBatch 填充、提交回调写入及 close 清空；ReferenceLookup 仅复制 identities/materials/routings，后续单位/工序身份复用来自 identities，未读取上述两个完整 Entity Map。应先明确这两个 Map 是否要承担真实校验，否则移除无效预取和提交回调，减少最多两组实际查询及内存占用；不是恢复逐条查询。executions Map 有消费者，不应一起删掉。
4. **把内存索引进一步提到 Python 最外层。** build_command 每单重建全部物料/工序/用户字典及登录计数；audit_component 每单重建整个来源 Mapping 字典，resolve 还按编码扫描目标表。prepare.py 的工单循环中也重建已有工单/执行来源集合。可为同一冻结输入一次构建只读索引，按编码/来源身份分组复用。它影响候选准备时间和内存分配，不是已观测 Java 写等待的解释。
5. **一次按工序聚合，避免重复 JSON 校验成本。** Service.check 每个节点 stream 扫描卡内全部 reports 求和，可以每卡一次 groupingBy/reduce；同一命令在预取、prepare、approve、execute 和即时重放重复解析 JSON、哈希及验证。可分离冻结内容纯校验结果和每次都必须执行的当前身份/Factory/LOCAL/审批/锁检查；缓存必须绑定完整内容哈希、库版本及生命周期。不能因为内容校验复用就跳过当前授权或持久化状态校验。先测 CPU 分配/火焰图，未证明它是主要卡点。
6. **区分运维总时长与业务事务时长。** 每单 HTTP、每 10 单全量原有行保护和文件落盘、最终独立对账会增加墙钟时间和 .local 体积。可以流式 hash/对账并压缩中间证据，而不减少保护字段、原有行范围或最终全量重放。不能用降低验收强度制造导入提速。

**增大入口批次的判断：** 当前 preloadBatch 明确最多 10 单；直接改为 20/50 单不会消除每卡写入语句和每单事务。它主要摊薄预取成本，并增加冻结命令和缓存内存；本次没有证据支持固定提速比例。更适合先优化单工单内写批次填充率，再按 SQL 往返、事务/预取/验收耗时、最大卡数/报工数、内存峰值分别基准比较。写后校验读取的是刚写入、未提交的新事实，无法在最外层写入前用旧缓存替代。

本次未做新真实写入或 Maven 构建测试。已验证压缩往返内容、JAR 哈希与链接、SQL gzip 备份、冻结源码、归档测试报告及 git diff --check；上述性能建议仍需独立实现和同输入基准验证。当前 APPROVABLE 的 564 单已完成，不删除事实或扩大未批准范围来制造新增写入样本。


### 2026-10-21 补充：日志分布及按最小保留集继续清理

用户进一步询问剩余约 10 GiB 的组成，并明确质疑旧验收 JAR、历史账本和冻结中间数据为什么不能删除。结论修正：它们不是当前服务运行依赖；之前保留它们是为了跨阶段精确复测，属于偏保守的可追溯性策略，不是“不能删除”的系统限制。本节替代上文对所有独有历史 JAR 和所有 SQLite journal 一律保留的清理策略，原时间点的操作回执继续保留。

**10.23 GiB 时的实际分布：** JSON 约 3.15 GiB、JAR 约 2.17 GiB、SQLite 约 2.01 GiB、压缩归档约 1.22 GiB、未压缩 JSONL 约 0.84 GiB、日志约 0.56 GiB，其余约 0.28 GiB。日志并非主要占用；主要目录为 V2.2 约 3.57 GiB、核心性能优化约 2.06 GiB、V2.1 约 2.00 GiB。

日志配置 `mes-admin/src/main/resources/logback-spring.xml:5` 按主机/PID 分目录，包含 app.log（应用）、access.log（HTTP 访问）和 error.log（异常），配置的按天/大小轮转和容量限制作用于各实例自身；没有在该配置中统一清理已经退出实例的整个目录。`.local/logs` 当时有 322 个实例目录和两个其他条目，共约 304 MiB；其中 320 个实例已退出，压缩归档并逐文件核验后删除，约 302 MiB 原始日志压为 14.7 MiB。两个仍在用或无法确认退出的目录，以及 redis.log、.DS_Store 保留，`.local/logs` 降为约 180 KiB。其他验收/构建目录还有约 269 MiB 分散日志，和 `.local/logs` 是不同统计范围。

先对 V2.1 admission/admission-final/admission-release 的命令文件做哈希去重，删除 1,226 个重复实体且保留路径，连同日志归档回收约 1.44 GiB；此后按用户“为什么不能删除”的进一步要求，不再保留已被 V2.2 冻结输入替代的旧准入命令，删除三个旧 commands 目录的全部 1,840 个逻辑文件入口。准入结论、隔离清单、冻结原始来源证据及 V2.2 当前 commands/manifest 保留。

进一步删除 18 个旧验收 JAR 版本及 2 个别名，只保留最新已验收 V2.2 批量预取版本和上一已验收批量版本。旧版本的独立类/依赖、源码/patch、摘要和历史记录不自动一起删除；最新运行 classpath 的全部入口已验证仍存在，最新 JAR SHA 与 ZIP CRC 完整性验证通过。

删除 26 份中间 901 闭环 SQLite 验收账本，保留 V2.1 original901-audit 原基线账本与 V2.2 bulk-final-901 最终完整账本，以及其他小型操作 journal。删除前确认账本只有 steps/timings/cycles 三张验收记录表，通过只读 quick_check，记录各表行数和文件 SHA；WAL 为零且无非空事务日志，不删除有未落盘变更的数据库。这些是本地测试/审计文件，不是 MySQL 业务库，也未对业务数据库执行任何删除。

上述最小保留清理另回收约 4.27 GiB。本轮两次追加共回收约 **5.72 GiB**，`.local` 从 **10.23 GiB 降为约 4.52 GiB**；从最初 17.63 GiB 起累计回收约 **13.11 GiB**，磁盘可用约 **30 GiB**。

当前剩余分类：

| 类型 | 约占用 | 内容 |
| --- | --- | --- |
| JSON | 1.40 GiB | 当前冻结转换命令、HTTP/SQL Trace 与结果元数据 |
| gzip 归档 | 1.23 GiB | 历史来源/保护快照、SQL 恢复备份、日志及测试报告归档 |
| JSONL | 0.85 GiB | V2.2 初始/最终和重放前后四份完整快照，以及小型过程记录 |
| JAR | 0.33 GiB | 两个已验收运行版本及共享依赖文件 |
| 分散 log | 0.26 GiB | 其他验收/构建目录日志，当前 .local/logs 本身仅约 180 KiB |
| SQLite | 0.15 GiB | 原基线和最终完整验收账本及小型操作 journal |
| 其他 | 约 0.30 GiB | SQL/TSV、配置、源码、补丁和诊断文件 |

保留了用户要求的原始来源、转换证据、最终对账和不可变历史事实查询能力；SQL 恢复备份 SHA 未变化，没有运行迁移、改变当前用户服务、修改业务代码或操作远端数据库。**旧验收阶段的精确可执行现场已退役，不能再承诺按旧路径原样复跑；旧报告中的相关私有产物路径以清理回执为准。** 不将这种回溯能力的减少描述为没有代价。

新增私有回执为 `.local/storage-review-20261021/followup-summary.json`、`logs-cleanup.json`、`minimum-retention-actions.jsonl`、`minimum-retention-summary.json`、`minimum-distribution-after.json`。原报告的阶段 SHA/动作清单是历史时间点，不能作为现存文件清单；当前存量以 minimum 回执为准。已退出实例日志的内容保存在 `exited-process-logs.tar.gz`，可列出目录后按需恢复，不覆盖当前服务日志。


### 2026-10-21 工艺编码/名称未按来源还原的复审

用户指出一条 LEGACY 路线与 ktg-mes 的中文业务字段不一致。已核对本机保留的最终目标与初始来源快照，来源身份可精确对应。当前 Service 第 465–466 行将工艺 code 硬编码为工单来源哈希的 LEGACY 前缀值，将 name 硬编码为“历史转换工艺 + 工单号”；LegacyImportCommand 未携带旧路线 route_code/route_name。工序节点的 Operation 编码/名称来自旧数据，不能据此声称路线本身的编码/名称也已还原。

这是转换映射不完整，属于此前验收未覆盖的展示/主数据语义偏差：已验证的数量、路径关联及不可变事实校验不能覆盖该缺口。按工单创建独立历史路线是为了隔离各工单实际加工路径，但不能以此推导业务 code/name 应丢弃来源字段。内部映射/冻结身份与来源业务编码/名称应分别处理。

该来源路线在当前批准命令集中被多个工单引用。修正不能直接将所有派生路线改为相同 code 和 version=1，也不能按旧编码合并已被生产事实引用的路线；应先核对编码/版本唯一约束、原路线身份与实际路径差异，再制定保留现有 ID/事实引用和不可变来源映射的修正方案。原路线来自当前来源表，不能伪称已有真实历史发布版本号。

本次仅定位，没有修改业务代码、数据库、冻结命令或既有审批哈希；快照事实尚未重新对当前在线库进行验证。具体来源/目标字段与引用范围保存在私有 `routing-code-name-review-20261021.json`，不把原始业务编号写入公开报告。此前已完成的迁移验收结论需增加“路线业务编码/名称还原未覆盖”这一明确限制，不能继续表述为工艺全部字段完全还原。


### 2026-10-21 工艺编码/名称恢复：原工艺 Map → 目标库 UPDATE

用户明确要求与 ktg-mes 保持一致，并指定简单方式：重新批量读取原工艺、建立 Map、更新数据库。本轮按该范围修复已迁入主数据，没有重建迁移架构、合并历史路线、重跑生产数据或改变既有冻结命令。原导入器生成 LEGACY 路线的逻辑本轮未改造；后续新增转换仍须另行处理来源字段传递，不能宣称新转换入口已自动修复。

**实际执行目标：** 核对中心库 mf_factory/mf_datasource 后，确认 Factory `TEST_DEV_PERF_20261019`、LOCAL、DS 9/revision 2、目标 `192.168.18.25:3306/mes_base_dev_perf_test`，MySQL server UUID 仍为既有授权实例。来源只读 `prod_j2eedb.pro_route`，一次读取 route_id/route_code/route_name 建 Map。566 条已有 ROUTING 映射均能由原冻结 source_payload 中的路线 ID 唯一对应到 47 条原工艺；缺失、多来源、空值、长度越界及编码/版本冲突均先拒绝，不猜名称或截断编码。

**备份与更新：** 写入前保存 routing 全表一致性 SQL gzip 备份和 649,414 条原有目标行全字段哈希基线，保存每条原路线完整字段、来源 Map 和更新计划。已将 566 条路线 code/name 恢复为原工艺 route_code/route_name；同编码的多个历史快照依照 Base(code,version)唯一约束分配独立本地版本，本轮最大版本 27。该版本是 Base 本地快照序号，不冒充 ktg-mes 原始历史发布版本。保留路线 ID、工单/执行/报工引用、节点/边、状态、description、external_id、source_version、revision、published_at。逐条字段比对通过。

**历史 UPDATE 门禁：** V41 会拒绝任何已映射历史路线 UPDATE，未直接停用触发器或使用会话跳过开关。新增正式 `V43__legacy_routing_identity_restore.sql`，经既有 Factory/实例校验的 Flyway 入口执行并 validate。迁移通过临时严格保护触发器完成原 guard 替换，不存在无保护的 DDL 间隙。仍只允许在 RUNNING 的 `legacy-route-identity-restore-v1` 修正批次中，凭不可变 `ROUTING_IDENTITY_RESTORE` 审计记录，绑定原 ROUTING 映射、原路线 ID、旧 code/name/version 和精确新值，更新 code/name/本地 version；其他字段依旧拒绝。普通编辑、无审批修正、错误值、已完成修正再次改写、报工和路径等历史事实均不放开。SQL 不含客户数据，没有新增表、生产 HTTP 修正入口或外部数据源装配。

Map 在私有 Python 工具中构建；在一个目标事务中锁定 566 条旧路线并核对原值，新增 1 个修正批次及 566 条不可变审计映射，以一次集合 UPDATE 写入，逐条比较结果后完成批次并提交。未修改原有来源映射或审批证据。再次运行只校验既有完成批次和全部结果，SQL 写入为零。当前真实启用且具平台角色的迁移操作员已核对并记录，凭据未写入报告。

**真实验证：** 无授权 UPDATE 拒绝、授权值不匹配拒绝、试图同时改 status 拒绝；正确授权可更新并完整回滚，回滚后授权记录和目标值均恢复。正式提交后再验证已完成批次不能复用、历史 Report UPDATE 仍拒绝。最终前后全字段比对确认原有 649,414 条行中，除 566 条路线允许的三个元数据字段外，其余原有行哈希不变；原 Mapping、生产数量与报工事实不变，没有原有行删除。新增本轮审计 Mapping 566、修正批次 1、Flyway V43 1，来源工艺 Map 读取前后逐字节一致。

验收窗口同时出现一条并发新增的 DRAFT 路线 607 及其 4 个节点、3 条边、1 个产品关联，均无原迁移 ROUTING 映射，不属于本轮冻结 566 条修正范围。该草稿仍带来源复制出的 LEGACY 标签，不能仅凭同编码推断它等于已迁历史对象。本轮未创建、合并、更新或删除该草稿及关联；全表新增统计明确单独记录，不把它冒认为“全库只新增本轮审计”或“全部 LEGACY 编码已清零”。未追问或覆盖其他任务的新数据。

使用用户现有后端 `127.0.0.1:8081`，真实登录并在指定 Factory 下读取 `/api/production/routing/593`，返回的 code/name 为来源中文业务值，ID 593 保持不变；另以 6 次分页请求核对全部 566 条列表值。仅按该厂 routing 资源的既有缓存协议轮换详情缓存 generation 和清理列表/详情，不清理其他工厂、权限或认证缓存；Redis 目标与当前 dev 配置一致，任务临时 Redis 凭据副本已删除。没有启动、停止或重启用户服务。

**构建边界：** 私有 Flyway 验收 helper 使用现有源码并仅将扫描位置明确为当前仓库 filesystem 迁移目录，已执行 IDEA 原生格式化、javac 编译和真实 Flyway migrate/validate。`mvn -B -ntp verify` 首次在既有 StockDocumentHttpTest 并发用例失败，该用例单独重验通过；第二次完整 verify 在 CodegenAcceptanceTest.globalCrudUsesPlatformOwnerAndPermissionWithoutFactoryIdentity 和 CodegenPersonAcceptanceTest.generatesPersonFromJdbcMetadataWithACompiledMenu 两项失败。验收期间工作区新增了多个其他任务的未提交改动，包括代码生成器及上述测试文件，已保护且未修改；本轮未声称完整构建通过，也不为取得通过而改写这些其他链路。数据库修正、真实门禁、回滚、重放、字段和 API 验证均已单独通过。git diff --check 通过。

私有证据根为 `.local/route-identity-restore-20261021/`：`plan.json`、`source-routes-before/after.jsonl`、`routes-before.jsonl`、`routing-before.sql.gz`、`backup-manifest.json`、`flyway-43-before/after.txt`、`flyway.log`、`result.json`、`routes-after-all-fields.json`、`field-and-final-guard-verification.json`、`replay-idempotence.json`、`verification.json`、`concurrent-draft-additions.json`、`http-route-593.json`、`cache-eviction.json`、各负例及构建日志。前后全字段快照压缩并校验内容 SHA 后保存为 before/after/snapshot.jsonl.gz；verify.py 支持直接读取压缩快照，不再保留重复的大型未压缩文件。旧 .local 清理报告的空间统计仍代表当时状态。

## 2026-10-21 — 导入业务状态与正式流转缺口复审

用户进一步明确目标：外部导入应进入同一正式业务模型，可信的已完成生产应在Base呈现完成，而非永久停留在历史只读。本节是诊断及设计建议，未实施状态升级，未修改业务代码、触发器或库存。前述V2.2验收仍只证明当时约定的历史只读迁移完成，不证明业务接续已实现。

### 当前真实数据与源码证据

- 重新核对中心绑定：TEST_DEV_PERF_20261019，ENABLED/LOCAL，数据源9/revision2/ENABLED，192.168.18.25:3306/mes_base_dev_perf_test。只读统计当前HISTORICAL工单566、执行8380、关联报工122171；这些执行关联的mes_material_issue_input及mes_execution_completion均为0。此为当前总存量，不能替代前述冻结564单的批次统计。
- LegacyHistoryImportService创建工单及执行时硬编码HISTORICAL；报工写入同一mes_production_report，类型REPORT，数量、人员、时间及原始来源证据落地，但没有将旧报工状态转换成Base生产完成状态。导入命令本身也没有可信完成状态/完成时间字段。
- 当前报工表没有草稿/审核/完成的生命周期状态字段，只有REPORT/ADJUSTMENT事实类型；作废通过追加负调整表达。productionReporting页面的AVAILABLE、NO_QUANTITY、NOT_ASSIGNED、NO_CAPABILITY、QUALITY_PENDING、EXECUTION_NOT_READY是当前节点可操作性，不是原报工流水是否有效。
- context()只接受工单RELEASED、执行READY/RUNNING及路径LOCKED；HISTORICAL首先返回EXECUTION_NOT_READY，通用原因没有清楚指出历史隔离。数量投影对历史对象固定writable=false、availableQty=0，以冻结映射读取推导历史投入；正式confirmedInput不是该历史投入。
- 自动完工候选只取RELEASED/LOCAL父单下的RUNNING执行；ProductionCompletionBalance还拒绝historical投影。已实际查询到mes_history_execution_update、mes_history_order_update及history_mes_execution_completion_insert三个BEFORE触发器，V41定义明确禁止历史对象更新及追加正式完工事实。该阻断不是巡检延迟，不能通过直接UPDATE状态修复。
- 当前来源REPORT映射保留的report.status分布：0共104，1共53，4共23，5共121991。只读审查ktg-mes的DicEnums确认0=未完结、1=已完结、4=部分入库、5=全部入库。旧状态混合生产完结与仓储进度，不能直接作为Base工单或执行状态。单条报工完成也不证明所有工序、所有流程卡或整张工单完成。
- 旧质量在本迁移中作为QUALITY_EVIDENCE映射保存，不能自动等同已建立正式质量处置。旧投入也是有来源的推导证据，不能宣称已创建当前LOCAL领料或库存动作。

### 设计判断与接续建议（未实施）

当前HISTORICAL同时表达来源、写保护和生产生命周期，适合第一阶段隔离归档，却不满足用户现在要求的正式接续。来源/迁移证据、生产状态、质量处置和仓储进度应分别表达；导入与手工创建复用同一状态规则及数量投影，不另建一套生产核心。历史导入标签不应永久代替正常业务状态，也不能把历史导入误归为当前ERP管理的EXTERNAL对象。

建议先逐执行核实完整路径、来源完成依据、归一单位的投入/净产出/最终损失及质量处置；再按父单核实全部流程卡范围，包括尚未迁入、拒绝及排除的范围。可信完成的执行受控登记带来源/原完成时间/本次登记时间的完成依据；可信整单完成再进入COMPLETED。未完成对象只有具备切换时点、在制数量、后续库存权属及剩余可加工额度时才接续RUNNING；证据缺失项列出具体原因，不能伪造领料、放行、入库或当前操作人点击事实。

生产完成不依赖成品先入库或人工数量放行；仍须质量处置结束。入库单确认过账负责库存生效，旧系统已入库记录仅凭状态值不得再次新增当前库存。数量闭合应使用相同计量口径、末道有效产出及最终损失，不能累计所有工序报工或把公斤原料直接等同件数产品；阶段性闭合还需明确执行范围及无未决投入/返工，防止提前终结。跨保管责任、工序或外协转移可按真实需求保留独立追溯事实，不应因为移除原成品交接审批链而删除所有流转记录。

实施需要新增正式迁移演进V41门禁及历史数量依据入口，保留已发布迁移和来源Mapping，不直接禁用全部触发器。验收应先列逐执行状态对照和阻断清单，再验证代表性已完成、未完成、有不良、已入库及不完整工单：状态正确、后续操作正确、重复导入无新增、当前库存不重复变化。当前尚未取得这些接续验收证据，也未验证用户运行服务已加载此前自动完工代码。

参考边界：OPC Foundation ISA-95 Part4 JobResponse同时表达JobState、实际资源/材料及StartTime/EndTime；这里借鉴事实与状态的区分，不宣称标准强制本项目使用上述具体完成公式。参考：https://reference.opcfoundation.org/specs/OPC-10031-4/6.3.5 。

### 同日用户纠正与正式接入实现

用户明确选择不使用外部完成状态：现有HISTORICAL执行先受控进入RUNNING，由内部数量及质量门禁自行判定完成。用户同时纠正上节“阶段性闭合还需等待执行范围固定”的建议：单执行数量闭合且质量处置结束即可自动完工；工单未覆盖的需求通过追加执行单元处理，累计不能突破工单量。本条取代上节有关未来可能追加生产而暂缓当前执行完工的建议，不把未来追加需求作为当前执行完工的额外门禁。

新增LegacyProductionActivationService作为受控运维用例，按工单持锁并在同一业务事务内验证历史投影、保存不可变ORDER_ACTIVATION/PRODUCTION_ACTIVATION来源关联、将父单设为RELEASED及执行设为RUNNING。启动时间记录本次正式接入时间，原始报工时间及来源不改。提交后发送现有ProductionCompletionRequested事件，沿用同一完工服务；不复制来源status，不直接把对象批量设为COMPLETED，不创建领料、库存或质量放行单据。原来源Mapping保持不变，激活后原已完成导入命令可按原身份重放，但旧历史追加入口不得继续给已激活工单补入只读执行。

ProductionQuantityService将“历史只读状态”和“导入期初数量依据”解耦：激活后读取有授权关联的冻结投入，作为已审核期初投入与当前正常投入相加；保留节点grossCorrection及期初最终损耗，再叠加正式报工调整及后续质量事实。切换RUNNING/COMPLETED不会让原投入归零或损耗消失，availableQty按同一投影重新计算，不再因来源是导入而固定为0。confirmedInputQty在此表达已认可投入总额，包含明确来源的期初量，不表示补造了当前领料事实。尚未激活的对象仍按旧只读契约保护。历史证据查询改为依据来源Mapping而非状态判断可读性。

补齐此前实际缺失的总量约束：执行创建/编辑在工单锁内限制有效执行目标合计不超过计划量；有已开始生产、领料或报工事实时不得减少执行目标以腾挪已占用额度。重复投产将当前确认投入、未决投入意图与本次投入一起占用执行目标，必要时在工单总量内扩展目标，未知/未决意图不能重复占用同一份额度。工单不能编辑到低于有效执行分配量。单执行仍按自身投入=末道有效产出+最终损耗完工；父单还须所有执行正式结束、有效执行目标合计及确认投入合计均等于工单计划量，避免首批执行结束就提前封单。

正式新增V45仅演进V41的工单/执行更新门禁，精确允许具有冻结来源关联及RUNNING审核批次的HISTORICAL→RELEASED/RUNNING转换，原报工、Mapping及其他事实的不可变门禁保留。已在重新核实的同一DEV实例及绑定上通过正式Flyway执行，45项校验通过；V40—V44校验和未改。已有566张历史工单与8380个历史执行的范围备份及执行日志在`.local/production-activation-20261021/`，没有保存额外验收JAR副本。

真实MySQL回滚试验使用当前登录的平台用户100、正式Factory路由/快照及业务事务：两张工单共11个执行先激活，再调用真实ProductionCompletionService.reconcile，11个均按内部规则完工；第一张父单因范围/数量未覆盖保持RELEASED，第二张满足条件转COMPLETED。全部事务显式回滚，随后历史工单仍566、历史执行仍8380、激活关联仍0；原报工、正式投入、库存余额及动作CHECKSUM与试验前完全一致。另实际验证未带激活关联的两种直接UPDATE均被V45拒绝。CHECKSUM是本次范围的附加变化检测，不替代前述迁移阶段的逐主键全字段审计。

定向24项测试已通过，其中新增HTTP/H2场景验证期初投入100、末道产出90及损耗10的执行自动完工，计划1000的父单保留追加900的空间、拒绝追加901；重复投产不能突破总量且失败不新增领料单。H2中的期初依据读取器是显式Mock，正式SQL、V45门禁及事务证据由上述真实MySQL探针补充。首次全量验证发现两条旧用例仍允许超量分配，已改为断言拒绝；另一个Fixture的12.12执行改为匹配10的工单计划。先前实现全量verify已通过，补齐重复投产上限后的最终构建记录另见后续回执。

部署边界：批量激活尚未执行。必须先由用户重新加载新后端，再执行566单的受控激活并给出实际完成/未完成及具体阻断原因；旧后端只按状态读取历史投入，不能在其仍运行时把8380个执行直接改为RUNNING。Agent没有重启用户服务，也没有将回滚结果记为已持久化完成。进入正式流程后不得回退到忽略期初依据的旧JAR；不能删除新增完工事实或把COMPLETED强改回HISTORICAL作为回滚。

最终验证补充：`verify-delivery.log`标准全量verify通过（合计810项、47项按既有环境门禁跳过，零失败/错误）；`verify-final-flow.log`补充12项verify通过，其中新场景在期初投入100、期初损耗10、末道已有80时，确认不能提前完工，再经正式报工HTTP追加10，真实提交后事件自动完成执行及父工单，最终产出90且库存动作仍0。打包smoke10项通过。原有有投入/报工执行目标从10降到6的旧测试已更新为拒绝，不为维持旧断言放松额度保护。新的有效分配及未决投入SQL已在实际目标只读执行，未决投入使用idx_input_execution及领料主键关联。

回滚后另将当前全部566张历史工单与8380个历史执行重新导出，与原备份的INSERT行按完整字段进行SHA-256比较，结果完全相同（`rollback-full-row-verification.json`），没有额外保存第二份全量导出。正式批次的566个工单ID及来源哈希已冻结到`scope.json`及`scope.sha256`；TEST ONLY运维启动器校验冻结摘要和来源身份，禁止静默扩大范围。激活用例复用现有Factory范围执行缓存失效协议，提交后清理执行列表及详情，回滚不发布失效，不清理其他工厂或权限缓存。尚未批量转态的部署边界保持不变。


### 同日正式执行完成：批量接入执行中，完工交回系统

本节更新前述“尚未批量转态”的时间点结论。用户已确认重新加载最终后端。首次重启与最后打包时间交叠，Agent过早提示重启，随后明确说明并由用户再次重启；第二次确认后8081真实登录及数量读取成功。最终JAR固定于2026-10-21 11:00:50，SHA-256为`103031f52c5cee39d20b39c1a4ac356d07d38187345dce5235a51bb08a2fcd11`，执行结束再次校验相同；Agent没有启动、停止或重启用户服务。

最初运维启动器在激活后串行逐执行检查完工，已激活60张工单、919个执行，其中917个完工、2个因数量未闭合保留RUNNING。用户随后明确要求只把历史对象接入执行中，让系统自行处理。原做法把批量接入和逐执行完工核算串在同一长任务中，是本轮等待过长的直接路径；本次没有完成SQL级耗时剖析，不能据此宣称已定位所有慢查询。按用户最新要求，仅对本轮拥有的workorder-completion运行令牌条件终止，旧执行器失去执行权后退出；未停止用户服务。随后正常调度已取得新令牌并成功检查181个候选，未被原任务永久占锁。

剩余506张工单、7461个执行采用集合式读取及按父单事务批量接入：预检冻结身份、路径、期初投入和工单额度，保存与正式服务同契约的不可变激活关联，经V45门禁将父单HISTORICAL→RELEASED、执行HISTORICAL→RUNNING，完成审核批次。先对13个执行实际回滚探针，再提交全部剩余对象。该运维批次不逐执行强制核算完工、不伪造完成事实；已COMPLETED的对象未降回RUNNING。后续正常报工事件及10分钟补偿调度继续使用系统正式完工链路，不再由Agent追加全量串行扫描。

最终真实DEV审计快照：566张工单全部接入，其中RELEASED 509、COMPLETED 57；8380个执行全部接入，其中RUNNING 7463、COMPLETED 917，范围内HISTORICAL为0。ORDER_ACTIVATION关联566、PRODUCTION_ACTIVATION关联8380。917个已完成执行各有正式完成事实，已完成缺事实及未完成有事实均为0。这是接入后的审计时点状态，后续系统调度可以继续改变完成数量；RUNNING不表示本轮已判定其数量不闭合。

原报工总数227783、原来源映射247915不变；本范围新增当前领料投入、库存动作及质量放行均为0。wm_inventory、wm_stock_action、mes_material_issue_input、mes_production_report的CHECKSUM分别仍为1808800821、2866375946、3328657690、2455882409，与写入前一致。受影响对象原始全字段备份保留在status-before.sql；新增状态、审计关联及917条完成事实是本轮明确授权的变化，不能把这些变化误称为“全库完全不变”。

批量后仅清理当前Factory的执行列表/详情缓存，generation更新已验证。通过用户现有8081读取末批执行9945：status=RUNNING、historical=false、writable=true，期初投入仍为2400，各节点reportedQty/effectiveOutputQty/defectQty与转态前逐值相同，历史只读阻断原因已清除。两个真实未闭合样本1138和1170仍为RUNNING，之前核算余量分别8和3，未强制标记完成。原样板1107已COMPLETED，父单1109因7200/9600覆盖继续RELEASED。

证据位于`.local/production-activation-20261021/`：bulk-state-probe.json、bulk-state-result.json、final-audit.sql/log、cache-after-bulk.json、http-last-sample-before/after.json及final-http-verification.json。最终接入、真实数据库审计和现有HTTP数量读取已完成；不把本次批量接入描述为全部8380个执行已经完成业务完工，也不宣称已逐一验收所有剩余对象的后续质量处置。临时会话令牌在读取验证结束后删除。


### 2026-10-21 补充：实际批号重复清单与不良业务迁移规划

用户要求核对executionNo是否适合展示实际批次，并把LEGACY摘要换为真实批号；随后补充“不合并，输出执行单元重复清单”。本次先交付只读核对清单，尚未修改execution_no、业务代码、状态或质量事实。旧实际批号为pro_reporting_work.xtransfer_no，冻结EXECUTION来源映射的source_pk为[工单编码, xtransfer_no, 来源route_id]。execution_no可用于展示该执行的批号，但不能当作全厂唯一身份或据其合并执行；内部主键及所有事实引用仍为execution_id。

在重新核实的TEST_DEV_PERF_20261019 / 数据源9 revision2 / 192.168.18.25:3306/mes_base_dev_perf_test只读提取全部8380个迁入执行：实际批号3164个，跨执行重复批号2302组，涉及7518个执行；同工单＋批号重复为0，同工单＋批号＋来源工艺重复为0。最大一批号对应14个执行且分属14张工单。因此本清单证明的是跨工单复用批号，不是重复导入。完整汇总及逐执行明细在`.local/execution-batch-review-20261021/执行单元重复批号清单.md`，附执行ID、当前编号、工单、实际批号、工艺版本、状态及目标量；extract.sql、rows.json和summary.json保留只读证据。

真实目标库的mes_legacy_source_mapping已有12078条entity_kind=QUALITY_EVIDENCE记录，source_table=qc_defective，source_pk为原record_id，source_payload保留原始数量、类型、原因、时间、批号、发现/责任工序及退废引用，parent_id指向对应EXECUTION映射，entity_id为空，conversion_payload目前为空对象。这些属于不可变来源证据，不是mes_production_defect正式申报。导入写入位置为LegacyHistoryImportService.appendExecutions；正常不良页面查询正式账本，不会自动展示这些来源JSON。不能把本批12078与更早来源全库17297直接比较为丢失，范围尚须按来源主键集合核对。

下一步不良迁移计划（尚未实施）：

1. 以这12078条来源主键和hash冻结范围，批量关联执行/发现节点/责任节点/人员/材料单位，输出可迁、缺字段、冲突及来源范围外清单；保留旧单号、发生时间及原始状态，不凭旧status=1认定Base质量处置或ERP现状。
2. 逐执行节点核对旧物料不良到产品数量的换算。现有ProductionQuantityService已把冻结grossCorrection加到旧净报工，并把effectiveDefect计入损耗；若正式申报再次纳入聚合，会重复扣不良。因此必须采用带原证据关联的替代投影，确保同一原不良始终只计一次，不能删除或改写原NODE/QUALITY_EVIDENCE。
3. 对缺去向、原申报账号或确切处置事件的数据明确列出缺口；现有正式申报要求这些字段，禁止填默认仓库、当前操作者冒充原申报人或虚构返工/放行。只能在有证据支持的迁移契约下接入同一质量业务，并保留未知边界。
4. 样板覆盖工废、料废、发现/责任工序不同、缺字段、已完工执行和后续修订。验证迁前迁后每节点投入/有效产出/最终损耗不变、页面可查询、幂等无重复、错误整单回滚、无库存及ERP副作用；任何影响现有已完工事实的矛盾先形成冲突清单，不自动重开或删除完工事实。
5. 样板通过后按有界工单批次集合预取、内存分组及短事务导入，保存新质量事实与来源关联，完成后核对源主键覆盖和数量守恒。所需Schema/门禁通过新增正式Flyway演进，不能重写已发布迁移。

本次已验证真实目标绑定、批号分布和来源证据落点，并审查正式不良模型及数量叠加入口；尚未实施批号修改或正式不良导入，没有为本次只读清单运行构建或重启服务。


### 2026-10-21 补充：旧净报工到当前加工总量的转换公式

用户追问“不良是否直接扣减、旧报工＋不良＝上道与Base是否一致”。源码复核：conversion_v2.convert_v2以旧报工聚合为legacyNet，以经物料/单位/有效状态核实的不良为effectiveDefect，计算gross=legacyNet+effectiveDefect，校验gross+legacyRemaining=upstream；prepare_component将effectiveDefect同时保存为NODE.conversion_payload的grossCorrection和effectiveDefect。原mes_production_report保留员工旧报工原数量，未把补足量记到某个员工工资/报工事实中。

ProductionQuantityService.compose读取正式报工合计后，先加冻结grossCorrection得到当前加工总量，再把冻结effectiveDefect加入不良，使用同一project计算有效产出。因此不是直接在旧净报工上再减一次不良。例：旧上道400、净报工396、不良4、未报0，转换为当前加工总量400、不良4、有效产出396；当前剩余按投入减加工总量计算为0。对原数据存在未报量的节点保留未报量，不强补到投入数。

真实DEV只读核对执行8354：来源工单/批号/工艺的冻结映射关联原不良13691数量7和13692数量2（同来源工序103）。节点5637冻结legacyNet=1271、grossCorrection=9、effectiveDefect=9，当前投影口径为加工1280、不良9、净产出1271。该执行其他有不良节点也保存同样的两项补正。此证据为数据库冻结转换依据和当前源码核对，本轮未重新登录HTTP、未声称新增正式不良记录或已完成新迁移。

当前缺口是转换仅落实为“原净报工＋导入节点补正”的数量投影以及QUALITY_EVIDENCE来源，而非完整正式不良账本。后续应按Base口径把“转换后加工总量＋正式不良事实”作为一次性迁移目标，并为每条来源建立唯一转换关联；同一事务/可验证切换中以正式质量贡献替代对应冻结effectiveDefect，gross补足也必须具有可追溯且只计一次的转换依据。禁止把冻结损耗保留参与聚合又新增同额正式不良；禁止直接原位修改不可变旧员工报工、给某员工虚增产量或删除原证据。全节点迁前迁后有效产出、未报量、损耗总量及完工判定须等价。具体正式落账契约仍属于前节规划，尚未实施；用户本次质询未被冒认为已有一次性转换任务完成。


### 2026-10-21 正式不良迁移：转换依据、隔离范围与完工门禁复审

用户授权按Base加工总量/不良口径正式迁移，进一步明确冲突数据保持原样，由用户人工审核，只提供工单及执行单元清单。本轮冻结QUALITY_EVIDENCE 12078条、4028个执行。逐执行核对来源身份、发现节点、单位转换后的不良和已审核期初损耗完全一致；仅接受5704/5705且旧status=1并有退废引用的记录。可迁4016个执行、12052条不良、6801个正损耗节点。12个冲突执行整组排除，关联26条来源不良，涉及10张工单：其中4个执行包含6条未取得报废处置依据的来源，另8个执行存在8条原申报人不明确的来源；同组其他记录也不部分迁入。

完整人工核对清单在`.local/legacy-quality-migration-20261021/不良迁移冲突工单及执行单元清单.md`，包含工单编码/ID、执行ID、原实际批号及隔离原因。用户指定不合并，且本轮未改execution_no。原申报人仅在来源昵称唯一、来源及中心的user_id和登录名完全一致时确认，共12052条核验一致；“周静”的同名账号和旧“管理员”不推断映射到当前操作人。原责任工序能唯一映射则保存节点，否则保留原责任字段说明及完整来源；不把多人的责任强行压为单人。未取得确定物理去向的历史记录使用明确UNSPECIFIED及“历史去向未记录”显示，不填默认仓库。普通申报入口仍拒绝该历史未知值（HTTP 400），不能藉此绕过正常去向规则。

正式数据使用原mes_production_defect账本追加CREATE版本，保留旧发生时间、原因、数量、工废/料废分类及可信原申报人；本次录入人是实际登录的平台用户100。每条追加独立归属核实及已转报废的期初损耗登记共两个mes_quality_treatment事实，登记时间/操作者表达本次迁移确认，原退废引用/时间保留于不可变来源；不宣称当前ERP单据已被重新查证，也不调用ERP或库存命令。旧status=0不补造处置事件。

加工总量补足通过独立不可变NODE_QUALITY_CONVERSION记录保存为经审核节点期初加工贡献grossDelta；QUALITY_DECLARATION将原不良证据关联到正式版本主键。原员工mes_production_report不改写、不给某个员工增加计件报工。LegacyMigrationMapper的单执行及批量入口仅在精确规则/确定性ID、原节点source_hash、COMPLETED审核批次及approved_scope全部匹配时采用转换贡献，并停止原effectiveDefect贡献；正式QualityTreatmentService接管不良及净损耗。这是“期初加工贡献＋正式不良”的同一数量投影，不是增加第二套生产核心，也不是把正式不良与原损耗同时扣减。未转换对象继续读取原有期初数量，未删除原映射或重写已发布Flyway。本轮复用现有表和生成Mapper，没有新增业务表或Schema迁移。

运维工具scripts/legacy/plan_quality.py一次冻结计划并在内存按执行/节点分组；scripts/legacy/migrate_quality.py使用数据库mes_master_sync的Factory+sync_key条件抢占、唯一run_token、续期、提交前执行权校验及超时令牌恢复。先锁父工单再锁执行，同事务批量追加正式申报、质量事实和两类来源关联，全部匹配后完成审核批次。重放入口集合读取已完成转换叶子，在内存校对来源哈希、实际正式数量和节点转换，整执行跳过已完成组；部分转换明确拒绝。原运行令牌丢失时回滚当前未提交批次，已提交来源保持不变，不停止用户后端。

最初十执行批次的校验重复读取全部节点JSON及报工合计，实际PROCESSLIST观察到一条50执行整节点校验SELECT执行20秒。该查询会再次读取没有变化的零损耗节点和大型冻结JSON。已在自己运行的令牌边界停止、验证已提交断点后，改为每批最多50执行：入口复用冻结节点数据，仅对实际变化节点在INSERT SELECT时核对原gross/loss，再集合核对正式不良贡献；原报工、输入及库存通过不可变规则和任务前后CHECKSUM另行核对。不再为证明无变化重复扫描整条历史路径。不能把这项定位冒认为全部数据库负载或并发UI计数查询都已完成性能分析。

**用户要求的完工问题复审：** 现有8081真实读取四个冲突执行如下，差额均为0，不能将其描述成数量不闭合却完工：

| 执行ID | 工单 | 投入 | 末道产出 | 累计损耗 | 差额 | 原自动完工登记时间（Asia/Shanghai） |
|---|---|---:|---:|---:|---:|---|
| 9452 | 5100-260801639 | 1000 | 992 | 8 | 0 | 2026-10-21 12:19:17 |
| 9635 | 5100-260901515 | 500 | 499 | 1 | 0 | 2026-10-21 12:20:41 |
| 9944 | 5100-261001769 | 2400 | 2390 | 10 | 0 | 2026-10-21 12:23:05 |
| 1126 | 5100-261001754 | 2400 | 2390 | 10 | 0 | 2026-10-21 11:04:47 |

实际缺口是旧不良先以期初损耗参与数量闭合，但没有正式申报/处置，QualityReleaseService.releaseBasis看不到这些未决处置，空声明列表被视为无质量阻断。新增unverifiedLossQty读取把未转报废或无退废依据的旧来源明确带入现有unverifiedDefectQty门禁；即便数量闭合，仍阻止后续正常完工。该规则不修改来源、数量或既有完工事实，不自动重开这4个执行；按用户最新要求保留人工审核。H2覆盖数量闭合但存在来源待处置时canComplete=false，真实Mapper的单/批量SQL结果一致，用户重启后的实际HTTP确认执行9452的待核实量8已经可见。原已完工记录作为审计事实保留，其处置由用户人工审核。

最终交付构建mvn -B -ntp verify通过：814项、47项既有环境跳过、零失败/错误；包括5项ActivatedProductionHttpTest。Python计划及断点重放7项通过；前端404项通过、生产build通过；IDEA原生格式化检查通过；新JAR的打包smoke10项通过。真实MySQL先执行8条不良样板回滚，并验证真实单/批量Mapper SQL替换损耗一致，后续50执行的171条及135条样板均回滚验证通过。历史报工/输入/库存CHECKSUM及已有质量账本34,550字节完整备份、原始来源和计划均保留在私有证据目录。

最终JAR SHA-256为f5d89d08470581adefd65a4f134cd249245da8babb02d3922e40c7fbba9480c5，已由用户自行重启8081。实际HTTP验证新门禁生效、待迁样本数量保持后才开始正式写入；Agent没有重启用户服务，之后未再次改写该JAR。首批已提交8条正式申报的实际HTTP查询确认处置状态SCRAPPED、原申报人可回显、全部节点投入/加工/不良/有效产出/剩余数量均与迁前一致。

本段记录实施及验证依据。正式整批提交数量、受保护冲突逐字段比较、最终全表校验和重放结果在收尾后追加，不把当前样板或阶段性提交记为整批完成。


#### 2026-10-21 正式批次收尾与实际验收结果

实际写入目标仍为192.168.18.25:3306/mes_base_dev_perf_test，Factory TEST_DEV_PERF_20261019、数据源9/revision2、LOCAL；服务端UUID及中心绑定已核验。正式迁移完成4016个执行、12052条不良、24104条质量处置事实、6801个节点转换依据。原12078条QUALITY_EVIDENCE保留；当前不良表12071条、处置表24146条，分别含迁前19条和42条。

最终全量只读核对逐条匹配来源集合、确定性转换ID、source_hash、执行/节点、原申报人、数量、分类、审核批次及处置分配；6801个节点转换贡献逐一匹配冻结计划。12个冲突执行、10张关联工单的全部字段，以及这些执行的正式质量记录，与迁前快照完全相同，26条来源整组排除。wm_inventory、wm_stock_action、mes_material_issue_input、mes_production_report四表CHECKSUM与迁前一致。证据：私有目录final-verification.json、formal-declarations-verified.jsonl.gz、conflicts-before.json/conflicts-after.json。最终全量关联审计读取约百秒；这是一次收尾审计，未放回逐批转换热路径。

8081真实HTTP复核5895/8354两个执行，共13条迁入不良均显示SCRAPPED、待处置量0、报废分配等于不良数量；各节点inputQty/reportedQty/defectQty/effectiveOutputQty/availableQty/netDefectQty/directDefectQty/unverifiedDefectQty与迁前逐项Decimal等值。实际5173不良品页面已回显迁入数量、原因、发现/责任工序及“历史去向未记录”；页面观察发生在批量进行期间，不以当时分页总数代替最终数据库计数。最终JAR SHA-256保持前述值，未重建或重启服务。

全操作再次执行通过：remainingExecutions=0、alreadyConvertedDeclarations=12052，无新增业务记录；再次核对不良/处置总数仍为12071/24146，mes_master_sync最终SUCCEEDED、processed_count=12052。Python计划/断点测试7项及前后端git diff --check再次通过。正式完成后仍保留冲突供用户人工审核，不自动改状态或补造处置结果。

用户质询迁移为何“跑全流程”后明确：本次以冻结来源集合映射并批量INSERT正式不良、已有历史处置事实和唯一转换关联，不逐条调用申报/审核/报废业务接口，不重跑领料、报工和库存。额外关联用于防止原期初损耗与正式不良重复扣减。早期重复扫描全节点属于本次工具实现过重，已移出逐批热路径；必要的收尾全量核对仅执行一次。


#### 2026-10-21 用户确认申报人映射与未处理不良补迁

用户随后明确授权旧admin相关不良映射到user_id=100，并允许选择当前系统一个同名“周静”账号。实际中心库账号6148/L20260614/周静启用且未删除，另一个5862已停用，因此选择6148。迁移计划通过显式reporter_overrides覆盖这两项身份，保留原create_by/source_hash；不是全体申报人回填当前操作者。旧admin共5条、周静共3条，解除8个执行的身份冲突，整组补迁20条，其他12条保留原已确认申报人。真实回滚样板通过后提交；逐条来源、数量、原申报人/授权映射、40项处置事实及15个节点转换核验通过，原12执行/10工单全部字段及报工/输入/库存校验值不变。证据在私有identity-supplement目录。

用户进一步明确旧status=0应映射本系统新增未处理状态。此前DISPOSITION_NOT_PROVEN是本轮工具的保守排除原因，并非系统没有待处置状态；原限制过严，现由用户授权调整。planner仅在本次显式allow_pending_disposition=True时接受status=0且没有退废引用的记录，未知或矛盾状态仍拒绝。其独立不良及节点损耗已经通过冻结来源、身份、全部节点数量守恒核实，因此登记ASSESS/INDEPENDENT，保留未处理事实，不生成ALLOCATE、报废、返工或库存动作。系统已有Summary自然投影为PENDING（待处置），不是伪造SCRAPPED，也不增加第二套状态机。

剩余4执行/6条来源、数量合计29已正式迁入：9452的3条，9635/9944/1126各1条。真实回滚后提交6条正式申报、6项归属核实、5个节点转换。真实8081逐条处置查询确认六条全部PENDING、pendingDispositionQty=defectQty、allocatedScrapQty=0、仅有ASSESS事实。全部4执行投入、报工、不良、有效产出、剩余量及净损耗与迁前Decimal逐项等值；原来源待核实提示转换为正式待处置表示（unverifiedDefectQty由合计29变0），质量放行权威接口safe=false，blockedReason为质量处置或返工尚未完成。待处置门禁保持，原4执行/4工单头字段完全不变；未重开或改写原完工事实，既有完工状态仍按用户先前要求留待人工审查。原员工报工、投入、库存四表CHECKSUM不变。

截至此次补迁，12078条来源全部映射为正式不良：12072条有历史退废依据，6条待处置；覆盖4028执行、6821节点转换。全库不良账本12097条（含迁前19），质量事件24192条（含迁前42）。最终重放remainingExecutions=0/alreadyConvertedDeclarations=12078，没有重复业务记录。两次补迁均核对原DEV目标绑定并使用原数据库令牌、确定性来源身份和事务批量插入；原计划/备份/前次验收不覆盖，新证据分别在identity-supplement和pending-supplement。仅修改Python迁移工具及其9项通过的测试，未修改Java/Vue业务代码、Schema或JAR，未重启服务。git diff --check通过。最新冲突清单已追加补迁结果，原隔离表保留为历史快照。


#### 2026-10-21 报工人／实际生产时间及工单创建时间复审

用户要求报工人及生产时间恢复旧数据，账号采用刚确认的admin→100、周静→6148规则，并要求工单创建时间恢复原始数据。本次先对真实DEV完整历史范围只读复核，未直接覆盖账本。真实绑定仍为TEST_DEV_PERF_20261019、数据源9/revision2、192.168.18.25:3306/mes_base_dev_perf_test、LOCAL，server_uuid与前述一致。

已关联REPORT来源的122171条报工，operator_user_id与冻结source_payload.report.user_id不符数0，report_time与冻结report.create_time不符数0；206个操作人分组的ID、来源账号及昵称与当前中心用户一致。该历史范围不存在admin或周静报工，不需应用本次两个特殊身份映射。真实8081详情接口复核105603/105604：105603操作人5008/贺仁翠、生产时间2026-10-04T13:22:46，与旧报工一致；recorded_by=100和2026-10-20的create_time分别是迁移提交人及本地登记时间。前端ProductionReportManager已分别展示实际操作人/提交人、生产时间(reportTime)/记录时间(createTime)。这些是不同字段，不应把当前登记审计字段改写为旧操作人和旧生产时间。

全表227783条报工还包含未关联本次历史REPORT来源的105612条记录；例如流水1所属工单TEST_PERF_20261018_000001_WO，备注明确规则驱动仿真、非现场真实记录，操作人100和2026-10-18时间不能被当成本次历史报工映射失败的证据。本次不据此将无来源的测试报工改派给历史员工。

566个已迁历史工单的create_time确为本次导入时间。通过冻结raw_source_identities中的原mo_id唯一关联prod_j2eedb.pro_workorder，仅12个仍存在，create_time全部NULL；其余554个原记录当前不在原表。该12个有transaction_date，但旧界面称其交易日期，并非已证明的原创建时间，未静默替代。既有迁移冻结未捕获原pro_workorder.create_time，只有报工驱动的计划边界及原身份，因此无法宣称已有566张真实创建时间可直接恢复。

已向用户发出缺失时间处理选择：使用每工单最早旧报工时间并明确标记推导，或等待取得原工单真实时间；此决定待回复，未回填创建时间，也未改报工事实、数量、状态或库存。只读证据在.local/legacy-report-identity-time-20261021/audit-summary.json、report-operator-groups.json、workorder-date-evidence.json、http-report-details.json。未修改业务代码、未构建或重启服务。


#### 2026-10-21 用户授权按最早旧报工时间回填工单创建时间（推导值）

用户明确选择“用各工单最早的旧报工时间回填，并明确记录为推导时间”，本节取代上一节的待回复状态。按122171条冻结REPORT来源report.create_time，经原报工→执行→工单关联，在入口集合聚合MIN时间，566张历史工单全部覆盖；同时逐组验证来源工单编码一致、实际report_time等于冻结时间且无缺失。不是按工单编码推算日期，不使用当前导入时间，也不把12张原表的交易日期冒充创建时间。

执行前再次核验目标Factory TEST_DEV_PERF_20261019、数据源9/revision2、LOCAL、192.168.18.25:3306/mes_base_dev_perf_test及server_uuid。冻结全1674张工单完整字段备份、566张变更计划、计划SHA256以及带旧值条件的执行/回退SQL。全部566张原备注可原样保留，追加“【创建时间推导】取本工单最早旧报工时间；非原系统创建时间。”，追加后最长54字符，不截断已有备注。

先进行真实事务回滚样板，566张更新及逐字段比较通过；正式执行再次锁定目标工单并核对全表与备份一致，通过单条集合UPDATE仅改create_time/remark并验证受影响566行，再提交并独立读回。新时间范围2024-05-06 07:52:20至2026-10-16 20:46:04。其余1108张工单完整字段不变；所有工单的状态、数量、路线、来源、revision和update_time均未变。mes_production_report、mes_production_execution、mes_production_defect、mes_quality_treatment、mes_material_issue_input、wm_inventory、wm_stock_action七表CHECKSUM与执行前完全一致。

实际8081详情复核1149/1407/1695三个工单及列表编码查询，创建时间和推导备注均已回显：5100-240701408=2024-07-01T15:03:30，5100-251001479=2025-10-05T15:11:40，5100-261001769=2026-10-05T15:24:37。现有前端创建时间字段消费该列表响应；本轮未额外执行浏览器视觉验收，未修改Java/Vue业务代码或Schema，未构建、重启服务。证据目录.local/legacy-report-identity-time-20261021/workorder-create-time包含all-workorders-before/after.json.gz、plan.json/sha256、apply.sql、rollback.sql、probe/apply-verification.json、http-verification.json。SQL已保存、正式执行并验证；rollback.sql仅保存未执行，回退仍须以当前值条件核对后使用。此前不良迁移阶段“工单头字段未变”的快照结论仍是当时事实，本次创建时间与备注变化由此条独立用户授权产生。


### 2026-10-21 - User-selected requestDate mapping

The user explicitly selects source workorder requestDate as Base workorder create_time, superseding both original create_time lookup and earliest-report inference for this synchronization. The actual source column is prod_j2eedb.pro_workorder.request_date; all1822 retrieved source workorders have a value. Reporting time continues to use the original reporting timestamp.

Execution reuses the earlier metadata repair SQL with exact old-value conditions and affected-row backups. It is running; full completion is not claimed. Receipts are stored under .local/legacy-broader-migration-20261021/request-date-mapping/. A following operations queue calls the existing production activation function after the original imports and then repeats this date mapping for newly eligible headers. Existing function failures and missing source orders remain explicit exceptions. No trigger bypass, new route synchronization, additional probe, user-service restart or production migration function changes are introduced.


### 2026-10-21 15:38 - In-flight import performance

Committed168/202 workorders,1842 executions,23242 original reports; duplicate replay receipt excluded. Sum of per-order transaction measurements3074.79s; average18.30s/order, median11.44s, P90=40.43s, P95=58.40s, maximum138.24s (33 executions/603 reports). Measured transaction throughput7.56 source report rows/s. Latest20 orders consumed187.09s for1265 reports/233 executions. These measurements exclude reference preloading, startup, later activation, formal quality conversion and date metadata repair. This is a live progress snapshot rather than final migration acceptance. Machine-readable snapshot: .local/legacy-broader-migration-20261021/performance-current.json.


### 2026-10-21 15:59 - Completion error during user service shutdown

User reported stopping the backend. Actual user-service PID64314 app.log shows graceful shutdown beginning15:58:22.148, HTTP shutdown complete15:58:31.679, workOrderCompletionExecutor still running after its30000ms wait at15:59:01.695, and WorkOrderCompletionJob logging failure15:59:02.149. Root cause is SQLException: interrupt while PageHelper obtains a Druid connection during QualityTreatmentService.nodeTotals / ProductionQuantityService loading. This is the interrupted automatic completion worker, not a source import transaction failure. The importer had already committed205 orders; the separate activation runner ended15:57:55 with192 successful orders/2326 executions and13 original-function rejections. Formal quality conversion continues in a separate owned Python process and does not depend on the user8081 server. No user-service restart or new code fix was performed for this diagnosis. Evidence: .local/logs/MacBook-Pro-3.local-64314/app.log:1516-1526 and error.log:1441 onward.


### 2026-10-21 - Broader import execution receipts

{
  "status": "FINISHED_WITH_EXCEPTIONS",
  "import": {
    "newHistoricalWorkorders": 205,
    "newExecutions": 2384,
    "newReports": 27348,
    "newDraftWorkorders": 10,
    "sourceReportRowsInFrozenInputs": 170484,
    "migratedSourceReports": 149519,
    "remainingSourceReports": 20965,
    "ordersWithRemainingReports": 171,
    "fullyUnimportedReportOrders": 92,
    "partialReportOrders": 79,
    "unstartedOrdersDeferred": 1759,
    "creationTimeSourceOrderAbsent": 722,
    "activationFailedOrders": 13
  },
  "activatedOrders": 192,
  "activatedExecutions": 2326,
  "formalDefectsWritten": 3501,
  "creationDatesUpdated": 47,
  "pendingHistoricalDateRows": 12,
  "sourceFieldForCreationTime": "prod_j2eedb.pro_workorder.request_date",
  "stageErrors": {},
  "outstandingList": "remaining-detail.json",
  "performance": "performance-import-final.json"
}

These are committed operation receipts, not additional UI or business-flow acceptance. All exception data is preserved. No source facts, original input/stock facts or completed business facts were overwritten to force admission. Detailed frozen inputs and receipts are under .local/legacy-broader-migration-20261021/.

### 2026-10-22 用户确认：迁移工艺继续用于正式生产

一次性历史生产导入与激活已退役，迁入工艺属于正式生产工艺。V41 中阻止新工单引用已映射路线的 `mes_history_order_new_reference`，以及同组按 `HISTORICAL` 状态拦截工单、执行、报工、质量、入库、领料和库存动作的触发器，不再符合当前业务边界。新增正式 Flyway V49 删除 V41/V42/V43/V45 当前遗留的历史生产写保护触发器；不改写已执行迁移。V40 对 `mes_legacy_source_mapping` 的更新/删除保护继续保留，以保护已迁入来源证据。该迁移尚未对真实数据库执行；应用效果需在目标库通过 Flyway 正式升级并核对触发器清单后确认。

### 2026-10-22 迁移门禁复查与自主 Flyway 升级说明

本节为静态源码审查，未连接或升级数据库，未修改业务代码。V49 是本轮开始前已有的未跟踪文件，保留原内容。不能据此宣称迁移门禁已全部移除。

- 新工单链路为 `WorkOrderController.create → WorkOrderService.create → validate/binding → saveOrder`。Java 的工艺绑定校验要求产品启用、工艺已发布及产品关联成立，没有按迁移来源拒绝新工单。V41 的 `mes_history_order_new_reference` 则明确禁止普通新工单引用 `mes_legacy_source_mapping` 中的 ROUTING；这是当前描述的直接候选原因，仍需实际异常和目标库触发器确认。
- V49 删除 V41 创建、经 V42/V43/V45 替换后仍存续的全部 21 个生产写保护触发器。后续迁移的临时 transition 触发器均在各自脚本末尾删除，不属于成功迁移后的存续门禁。V40 来源映射不可变保护、V36 来源不可变保护及其他正常业务事实保护不在 V49 删除范围。
- 应用仍有迁移专用限制：`WorkOrderService.order` 在锁定 HISTORICAL 工单时拒绝；`ProductionChainLocks.requireActive/order` 拒绝 HISTORICAL；`ProductionQuantityService.validateChange` 拒绝 historical 数量视图。前端执行单元及质量处理也以 HISTORICAL 隐藏或禁止操作。因此 V49 仅完成数据库层解除，不能代替状态、数量、前端和测试契约治理；直接删判断也不能将未知数量或缺失的正式业务状态伪装为可处理。
- `hasLegacyBatches` 对改产品/路线和删除工单的限制涉及已有批次引用保护；`WorkOrderSaveRequest` 的 batches 只读限制防止恢复废弃批次写入口。它们不能仅因名称含 Legacy 就与迁移来源门禁一起删除。

项目没有配置 Flyway Maven 插件或业务库自动迁移入口。`PersistenceConfiguration.masterDataSource` 的 `mes.platform.migrate` 只调用 `FactoryFlyway`，扫描 `db/platform` 和 `db/system`，不扫描 `db/business`。中心迁移还有 V34 Java Migration，不应仅拼 SQL 目录冒充完整中心升级。

自主升级业务库可使用独立 Flyway CLI（按官方 macOS 安装说明安装、加入 PATH，优先对齐项目实际解析的 Flyway 版本）。先核实中心库 `mf_factory → mf_datasource` 的实际启用绑定、目标 host/port/db_name，备份业务库（含触发器和 `flyway_business_schema_history`），在维护窗口操作。以下占位符由操作者替换，不代表已确认目标：

```zsh
export FLYWAY_URL='jdbc:mysql://目标主机:3306/目标业务库?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export FLYWAY_USER='升级账号'
read -s 'FLYWAY_PASSWORD?数据库密码: '
echo
export FLYWAY_PASSWORD
export FLYWAY_LOCATIONS='filesystem:/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business'
export FLYWAY_TABLE='flyway_business_schema_history'
export FLYWAY_TARGET='49'
export FLYWAY_CLEAN_DISABLED='true'
export FLYWAY_BASELINE_ON_MIGRATE='false'
flyway info
flyway validate
# 核实 info 的数据库身份、已应用版本及全部 Pending 项后执行：
flyway migrate
flyway validate
flyway info
unset FLYWAY_PASSWORD
```

`target=49` 表示升级至 V49，会按序执行此前所有尚未执行的版本；不是只执行 V49。预期库已到 V48 时，应只有 V49 Pending。历史缺失、checksum 不一致或 failed 项应先定位，不能用 baseline、repair 或手工改历史掩盖，也不应在数据库工具直接运行 V49 后补写历史。不同工厂的不同物理业务库分别核实、分别升级，共用物理库不要重复当作不同目标处理。

升级后核对 V49 的 success 记录、与 V49 DROP 清单逐项比较真实 `information_schema.TRIGGERS`，并确认来源映射不可变等正常保护仍在；最后用正式新工单入口引用相同工艺复验。MySQL DDL 中断可能部分生效，应按真实触发器及 Flyway 记录定位后恢复，不盲目 repair/重跑。数据库升级与应用重启分开；需要重启时由用户执行。

参考：[Flyway CLI 官方说明](https://documentation.red-gate.com/flyway/reference/usage/command-line)、[Target 配置](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-target-setting)。本机当前 PATH 未发现 flyway，命令为操作说明，未实际运行。

### 2026-10-22 项目升级入口、V49/V48.1 实际执行与残留清理

本节追加真实执行结果，取代上一节“尚未执行”的状态。新增维护入口 `scripts/mes-business-flyway` 和根 POM 的 `business-flyway` Profile，复用项目 Flyway 12.4.0 与受管 MySQL 驱动；文档位于 `docs/development/database-upgrades.md`，README 已链接。入口仅开放 info / validate / migrate，固定业务目录与历史表，要求 migrate 显式目标版本，禁用 clean、自动 baseline 和乱序；不编译业务模块、不启停服务。中心升级仍经包含 Java V34 的 `FactoryFlyway`，不使用此业务入口。

**目标与升级事实：** 重新核实中心 `mes_base_dev` 的 `TEST_DEV_PERF_20261019 → datasource 9 / revision 2 → 192.168.18.25:3306/mes_base_dev_perf_test`，工厂与数据源均 ENABLED、模式 LOCAL，server_uuid 为 `03dbc952-7403-11ef-802c-0242ac130002`。升级前 V48 成功，唯一待执行为 V49。通过项目新入口正式执行 V49，Flyway success 已记录；21 个迁移写保护触发器全部不存在，来源及来源证据不可变触发器保留。

V49 执行期间另一工作新增 V48.1 质量删列迁移，用户明确授权合并升级，随后要求“不需要备份，直接按顺序执行”。V49 已先写入历史，故仅本次维护显式设置 outOfOrder=true 补执行 V48.1，不倒改版本、不忽略校验；恢复常规禁止乱序设置后，严格 validate 通过全部 50 份迁移。V48.1 的 15,598 条不良记录及 31,194 条处理记录均未删除，逐行核对所有保留字段不变，returned_qty 与原 rework_qty 一致；删除字段是该质量清理 SQL 的显式 Schema 变更，不能把旧 ALLOCATE/START/RESULT 原始动作误报为当前处理已成功。

**备份边界：** V49 前保存全部触发器定义、Flyway 历史、绑定及九表 checksum；其 SQL 不改业务行。V48.1 的两张完整质量表（含旧列值、结构、索引和触发器）及 Flyway 历史备份 26,135,567 字节已在用户后续取消追加备份前完成，保留于私有证据目录，不再追加备份或执行恢复。工单、工艺、执行、报工、来源映射、库存及库存动作七表升级前后 checksum 一致；两张质量表因 Schema 变化不比较整表 checksum，而比较保留事实字段及数量。没有重建迁移、激活或数据转换入口。

**真实工单验证：** 升级前，在必定回滚的事务中用迁移已发布工艺 15 / 产品 17 插入普通 DRAFT，被 `mes_history_order_new_reference` 以 SQLSTATE 45000 / Historical production is read only 拒绝；V49 后相同探针成功并回滚。随后使用用户现有 8081 正式 HTTP 接口、同一工厂与工艺，POST `/api/production/workorder` 创建数量 1 的明确 TEST ONLY 草稿 1915（200），GET 详情（200），DELETE revision=0（204），再次 GET 为 404。没有下达、领料、报工或库存动作，测试草稿无残留。HTTP 验证使用现有运行代码，证明数据库层引用阻塞已解除，不代替新代码部署验收。

**代码残留处理：** 移除 WorkOrderService / ProductionChainLocks 锁定读取中的 HISTORICAL 专用拒绝；活动写入统一要求当前 DRAFT / RELEASED 状态。移除数量校验、正向报工阻断原因、可用量强置零及完工计算对 historical 标记的额外拒绝；冻结期初与节点证据仍只读参与计算。已无正式调用方的批量迁移数量校验、PathBatch / viewBatch、nodesBatch DTO/Mapper 和其专用测试一并删除，保留并扩展正式数量/订正测试。前端执行与质量操作改按当前工单/执行状态准入，不按迁移身份黑名单判断；未知原状态按原值可读，不标成“历史只读”。

仍保留的 HISTORICAL 枚举/查询筛选和只读冻结证据用于解释原记录，不构成迁移写入口。原 13 张工单、58 个执行的 HISTORICAL 状态未被自动改成 RELEASED/RUNNING；它们仍须满足当前业务状态和完整性规则，不能因取消来源门禁而伪造已下达、已投产或数量闭合。旧批次的引用/删除保护、废弃 batches 请求禁止写入、LOCAL/EXTERNAL、权限、路径、质量及事实保护继续生效。

**验证进度与证据：** 实际数据库升级、50 份迁移严格校验、触发器清单、七表 checksum、质量逐字段核对与真实工单创建/删除均通过；前端 74 文件 / 433 测试及生产构建通过（只有既有体积提示）。后端全量构建遇到并行代码/输出变更，后续在隔离源码快照完成验证后追加结果。现有用户服务未重启；V48.1 删列后旧质量接口不能作为新代码验收，须由用户重启新版后端后复验。

私有证据目录 `.local/legacy-guards-20261022/`：before.yml、triggers-before.sql、route-probe-before/after.txt、flyway-migrate.log、flyway-quality-migrate.log、flyway-after-validate-final.log、verified.yml、quality-audit.yml、quality-before.sql、quality-backup.yml、quality-verified.txt、http-route-probe.json、ui-tests.log、ui-build.log。凭证不写文档或命令参数；未提交、推送或启停用户服务。

#### 后端验证补充

使用 `/private/tmp/mes-base-legacy-guards-20261022` 隔离源码快照，避免并行构建改写同一输出目录。本轮九个相关测试类共 52 项全部通过，覆盖冻结证据数量、统一状态准入、正式订正、新旧事实读取、工单引用与来源隔离；执行带定向测试的 verify，编译及打包通过，随后 packaged smoke 10/10 通过。升级入口另做九项无数据库参数检查，通过显式目标、缺凭证拒绝、危险/无效命令拒绝及继承乱序设置不能覆盖固定保护。V48.1 的 13 个旧字段名均不存在，returned_qty DECIMAL(20,6) NOT NULL 已经真实核对。最终 info 返回 Schema version 49，V49 Success、V48.1 Out of Order，严格 validate 通过。

未宣称全量后端绿灯：较早快照的全量 verify 在 mes-admin 跑 582 项，其中 48 跳过、3 项失败。两项是并行质量清理后仍断言旧去向字段的过时测试，已由该工作修正并在上述定向测试通过。余下一项 `StockDocumentHttpTest.documentAndFormalProductionIssueCompeteOnTheSameOwner` 存在间歇返回 BAD_REQUEST 而非预期 409 的并发断言失败。只在私有快照将本轮 WorkOrderService / ProductionChainLocks 恢复 HEAD 基线后，单次通过，但十次重复有一次同样失败；证明在移除此轮锁定代码变更后仍可复现，未修改用户正式库存测试或扩展修复库存业务。证据为 maven-verify-snapshot-final.log、maven-targeted-snapshot.log、packaged-smoke.log、launcher-checks.json、stock-concurrency-baseline.log、stock-concurrency-baseline-repeat.log。

代码层清理回滚可还原本轮差异，需保护同时进行的质量/仓储改动；不能以回退代码倒改 Flyway 历史。数据库层已发生 V48.1 显式删列，必要恢复需使用已有私有备份制定保留后续事实的恢复方案。用户仍需自行重启新版后端；真实 HTTP 证明的是原先新工单引用阻塞解除，不是全部新质量界面或生产闭环已在部署版本验收。

### 2026-10-22 工艺版本关系与后续追加工艺标识恢复

**版本模型源码事实：** `RoutingServiceImpl.newVersion` 保留当前 code/name，查询同 code 最大 version 并加一，复制所选版本的节点、连接和产品到新的 DRAFT / routing ID。`copy` 使用用户指定的新 code/name，从 version=1 开始。普通更新不开放 code；name 可以在草稿中编辑，不作为版本关联键。真实目标库唯一键 `uk_routing_version(code,version)`，code 使用 utf8mb4_0900_ai_ci；没有独立 family_id、previous_version_id 或 cloned_from_id。故同 code 的记录属于同版本系列，不靠 name 肉眼推断，但仅靠当前表不能证明某版本究竟从哪一个旧版本复制。工单与执行保存确切 routing_id，创建新版本不会自动把已有工单切到新版本。version 是本地版本序号，revision 是并发修订号，source_version 是来源标签，三者不可混用。

**本次真实核对：** 仍为工厂 TEST_DEV_PERF_20261019、DS9/revision2、LOCAL、192.168.18.25:3306/mes_base_dev_perf_test，server_uuid 与前节一致。771 条不可变 ROUTING 映射全部能以冻结 executions.key 中的原路线 ID 对应只读来源 prod_j2eedb.pro_route；566 条已恢复，后续追加的 205 条仍为 LEGACY_哈希编码和“历史转换工艺 + 工单号”。来源编码/名称均存在、非空且满足目标长度，未截断、翻译或猜测。

另有 DRAFT 607 没有迁移 ROUTING 映射，但仍保留旧 hash code / name、version=2、revision=0。之前恢复计划明确记载该旧编码属于原路线 602（原 version=1，已修正为源工艺53 / 3737A291 / 通用工艺 / 本地 version=8）；结合未变的 description、external_id、source_version、草稿节点工序/节点属性、连接及产品关联，证明 607 是同一旧编码系列的派生草稿。发布工序快照字段不作为草稿结构等价条件。前次只修正已映射对象，遗漏此派生草稿，使系列被旧、新 code 拆开。

**实际修复：** 对 205 个来源明确的迁移工艺及派生草稿607，共206条，按当前明确任务恢复源 route_code / route_name。先使用目标 MySQL 相同 collation 与唯一键的临时表校验全部 code/version 分配，优先保留无冲突原序号；200 条为避免现有 (code,version) 冲突重新分配本地序号，本轮分配最大30。草稿607最后分配为 3737A291 v11，使它回到原系列并保持为该系列最后草稿；其他记录不合并、不重建、不迁移工单引用。迁移时按工单实际路径隔离出来的各快照不是已证明的原系统历史发布版本，修复后也不能将 v1/v2/v30 宣称为旧系统实际版本演进。

使用带旧 code/name/version/revision/status 条件的参数化 UPDATE，在单一目标事务中锁定并核对工艺集合后批量执行。SQL 模板和完整旧值/新值计划已保存，正式提交206行，逐字段核对 routing 全表仅计划内 code/name/version 改变；id、状态、revision、description、external_id、source_version、published_at 和其他工艺均不变。源数据只读，来源映射、迁移批次不写入；没有恢复已经退役的导入/激活服务、修改已发布 Flyway 或新增业务接口。用户此前要求不追加备份，本次保存核对及修复计划，不另做整库备份。

**真实验证：** 修复后本业务库 LEGACY_ 工艺编码为0；workorder、mes_production_execution、routing_node、routing_edge、routing_product、mes_production_report、wm_inventory、wm_stock_action 八表 checksum 前后一致。复用既有 BusinessCacheProcessor，仅失效当前工厂 business:routing 的列表/详情及通用详情 generation。使用现有8081服务读取607/610/612详情，并用9个分页请求核对全部206条 code/name/version 全部回显。例如610为 ISX15L-5484094（4059294） / 通用工艺 / 本地v25；607仍为DRAFT，602及其原有工单/执行引用未改变。没有新增工单、下达、报工或库存动作，未启停服务；未执行浏览器视觉验收。刷新工艺页面可重新读取已验证接口。

本轮未修改业务源码或Schema，不新增无关测试或重跑构建；真实数据及缓存/HTTP核对是本轮证据。私有目录 `.local/routing-labels-20261022/` 保存 audit.json、draft-lineage.json、plan.json、applied-statement.sql、references-before.json、expected-after.json、applied.json、verified.json、cache-invalidated.json、http-verification.json。未来若需回退，只能按该计划和当前精确值设计受控元数据修正，不能合并路线或覆盖后续用户编辑。


### 2026-10-22 同 code 工艺多版本只读审查

**结论：本次迁移把工单路径快照建成了工艺主数据，再在恢复原 code 时排列为本地版本。这是迁移建模错误，不是已证明的原工艺发布版本演进。** 上节 code/name 修正只解决了标识和唯一键冲突，没有解决快照被当成版本的问题。本次严格按用户要求审查，没有合并工艺、调整工单或执行引用，也没有修正节点和连接。

#### 核实范围与比较方法

目标仍为 Factory TEST_DEV_PERF_20261019、数据源9/revision2、ENABLED/LOCAL，物理库 `192.168.18.25:3306/mes_base_dev_perf_test`；server_uuid=`03dbc952-7403-11ef-802c-0242ac130002`。原系统来源为同实例 `prod_j2eedb`。提取使用 JDBC 只读、REPEATABLE_READ 事务，真实会话 `@@transaction_read_only=1`，仅执行 SELECT，最后 rollback。此次没有写数据库、失效缓存或启停服务。

全量读取当前801条工艺、9055个节点、8254条连接、801条产品关联，以及1889张工单、11870条执行引用；以771条不可变 ROUTING 来源映射识别本次迁移对象，对应53个原工艺ID/code。当前正常新建、派生草稿等未映射记录单独区分，不作为迁移重复记录计数。

比较忽略新生成的 routing/node/edge ID，按实际边连接恢复节点顺序；保留重复出现的同一工序，不将其折叠。801条现有图均为单一连通线性路径，因此没有无法匹配的分叉图。分层比较：①工序ID序列及连接拓扑；②is_key、description等节点业务属性；③node_code、发布工序编码/名称/说明、display_order等完整节点字段；④产品关联。另对每条映射冻结的完整来源工序记录ID、reported_by_node及当前源 pro_route_process 顺序交叉核实。不能用节点ID不同、显示顺序相同或产品相同代替结构比较。

#### 审查结果

- 771条迁移记录来自53个原工艺；52个 code 有多条迁移记录。其中24组全部版本的工序、节点属性、连接和产品关联完全一致；另外28组确有结构差异，但全部能由迁移时按已报工工序筛选解释。
- 687条迁移路径包含完整来源工序；84条仅包含来源工序子集。其中28条是前缀截短，56条还产生了跳过缺失中间工序的直接连接。84条裁剪工艺当前被84张工单、500条执行引用；这些引用本轮全部保留。
- 按同 code 统计，仅有86种工序/连接形态；再计完整节点属性和产品关联，共91种内容。相对771条记录分别有685条结构重复副本、680条完整内容重复副本。这是比较统计，不是可直接删除的清单。
- 所有53个 code 的迁移冻结来源均只有一套完整工序记录ID模板，且均与此次只读取得的原工艺工序一致。原 pro_route 表也没有 version/revision 或发布历史字段。本次冻结迁移范围没有支持几十次真实升版的证据；这不等于证明原系统在所有历史时间从未修改过工艺。

| 工艺 code | 现有表现 | 核实差异 |
| --- | --- | --- |
| 三角下摇臂热前 | 30个版本 | 全部为6节点、5连接，节点属性及产品关联也完全一致 |
| 三角下摇臂热后 | 30个版本 | 全部为9节点、8连接，完整内容一致 |
| ISX15L-5484094（4059294） | 29个版本 | 28条为完整18节点；610/v25只有17节点，缺末尾“清洗” |
| 3737A291 | 含派生草稿共11个版本 | 完整模板13节点；602/v8及未映射草稿607/v11只有前4节点，草稿发布快照属性另行区分 |
| ISX11.9L-4330228（4975244）-新工艺 | 完整来源18节点，部分目标17节点 | 缺“车端面2”，目标直接连接“车端面1→粗铣螺纹面”，属于迁移筛选后重连 |
| 钛15L排气摇臂轴（氮化前） | 4条迁移记录，14/14/11/8节点 | 来源完整18节点，当前没有完整迁入版本；最大版本v4反而只有8节点 |

因此不能直接选最大 version，也不能单凭节点最多选择所谓原版。后续若另行授权治理，应先按完整来源恢复准确主数据方案，并区分现有工单冻结路径与今后新建工单使用的正式工艺；本次不执行该治理。

#### 迁移原因的静态证据

仅查阅已经退役的私有归档，不恢复或执行旧迁移入口：

1. `.local/legacy-v22-20261020/batch-prefetch-source/scripts/legacy/conversion_v2.py` 按完整来源 current_node_ids 遍历，但遇到工序不在 reported_by_node 中就跳过；未报工不等于该工序不属于工艺，这一步把完整模板裁成了工单已发生事实的子集。
2. 同归档 `mes-business/src/main/java/dev/mesbase/business/legacy/service/LegacyHistoryImportService.java` 464–499行，为每张工单创建一个新的 routing，编码取 WORKORDER 哈希，初始 version=1，节点来自第一条 execution.nodes，并按相邻节点直接创建连接。description 自身就注明“不代表原始历史发布版本”。因此同一原工艺被重复创建，子集还会被串成新路径。
3. 后续把哈希恢复为相同原 code 时，为满足 `uk_routing_version(code,version)` 而分配不同本地序号，使上述工单快照显示成 v1/v2/v30。序号由恢复过程分配，不能据此推导真实变更顺序。

#### 全部原工艺分组结果

下表仅统计771条有来源映射的迁移记录；“一致”指完整来源工序序列与连接，不代表跨 code 适合合并。产品和严格节点属性另见逐对比较文件。

| 工艺 code | 原工艺ID | 迁移记录数 | 工序/连接形态数 | 节点数 | 与完整来源一致 | 裁剪记录数 |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| 1.9L摇臂总成1006021FB热前 | 32 | 20 | 1 | 7 | 20 | 0 |
| 1.9L摇臂总成1006021FB热后 | 37 | 22 | 2 | 3,5 | 21 | 1 |
| 1.9L摇臂总成1006031FB热前 | 33 | 20 | 1 | 7 | 20 | 0 |
| 1.9L摇臂总成1006031FB热后 | 36 | 22 | 2 | 3,5 | 21 | 1 |
| 15L5484091新厂加工 | 69 | 6 | 1 | 16 | 6 | 0 |
| 15L5484094新厂加工 | 70 | 6 | 1 | 16 | 6 | 0 |
| 2.7L摇臂总成1006021FE010-磷化后. | 38 | 23 | 2 | 2,6 | 22 | 1 |
| 2.7L摇臂总成1006031FE010-磷化后 | 39 | 23 | 1 | 6 | 23 | 0 |
| 2.7L支座1006015FE010 | 35 | 21 | 1 | 11 | 21 | 0 |
| 3142U051挺柱热前 | 61 | 6 | 1 | 3 | 6 | 0 |
| 3142U051挺柱热后 | 64 | 6 | 1 | 10 | 6 | 0 |
| 3142U061挺柱热前 | 62 | 6 | 1 | 3 | 6 | 0 |
| 3142U061挺柱热后 | 65 | 6 | 1 | 10 | 6 | 0 |
| 3737A281 | 52 | 10 | 2 | 4,13 | 9 | 1 |
| 3737A291 | 53 | 10 | 2 | 4,13 | 9 | 1 |
| 3737A341 | 66 | 9 | 1 | 13 | 9 | 0 |
| 3737A351 | 67 | 9 | 1 | 13 | 9 | 0 |
| 4646253挺柱热前 | 60 | 6 | 1 | 2 | 6 | 0 |
| 4646253挺柱热后 | 63 | 6 | 1 | 10 | 6 | 0 |
| DCE6.7L排气摇臂 | 59 | 6 | 3 | 12,13,17 | 4 | 2 |
| DCE6.7L进气摇臂 | 58 | 6 | 3 | 12,13,17 | 4 | 2 |
| ISX11.9L-4330228（4975244）-新工艺 | 46 | 11 | 2 | 17,18 | 1 | 10 |
| ISX11.9L-4330232（4975240）-新工艺 | 47 | 11 | 2 | 15,16 | 1 | 10 |
| ISX11.9L-4330237（4975235）-新工艺 | 48 | 9 | 2 | 17,18 | 2 | 7 |
| ISX11.9L-4330241（4975231）-新工艺 | 49 | 9 | 2 | 15,16 | 2 | 7 |
| ISX15L-5484091（4059353） | 1 | 29 | 2 | 17,18 | 28 | 1 |
| ISX15L-5484092（4059351） | 6 | 26 | 2 | 17,18 | 25 | 1 |
| ISX15L-5484093（4059297） | 7 | 27 | 3 | 1,17,18 | 25 | 2 |
| ISX15L-5484094（4059294） | 9 | 29 | 2 | 17,18 | 28 | 1 |
| M11-4003903 -新工艺 | 42 | 8 | 2 | 13,14 | 4 | 4 |
| M11-4003904-新工艺 | 43 | 8 | 2 | 13,14 | 1 | 7 |
| M11-4003905-新工艺 | 44 | 8 | 2 | 13,14 | 4 | 4 |
| M11-4003906-新工艺 | 45 | 8 | 2 | 13,14 | 1 | 7 |
| 三角下摇臂热前 | 14 | 30 | 1 | 6 | 30 | 0 |
| 三角下摇臂热后 | 17 | 30 | 1 | 9 | 30 | 0 |
| 云内27摇臂总成-490PLUS-070035 | 31 | 19 | 2 | 4,18 | 18 | 1 |
| 云内27摇臂总成-490PLUS-070036 | 30 | 19 | 2 | 4,17 | 18 | 1 |
| 喷油器摇臂-3896642 | 15 | 30 | 2 | 13,14 | 28 | 2 |
| 火狐狸总成-5267686 | 10 | 26 | 2 | 12,20 | 25 | 1 |
| 火狐狸总成-5267687 | 11 | 27 | 3 | 9,17,20 | 25 | 2 |
| 火狐狸支座5295581 | 34 | 22 | 1 | 10 | 22 | 0 |
| 迪尔摇臂-R133629 | 16 | 26 | 2 | 4,14 | 25 | 1 |
| 钛15L6536547摇臂 | 77 | 2 | 2 | 2,13 | 1 | 1 |
| 钛15L排气摇臂轴（氮化前） | 75 | 4 | 3 | 8,11,14 | 0 | 4 |
| 钛15L排气摇臂轴（氮化后） | 76 | 1 | 1 | 2 | 1 | 0 |
| 钛15L排气摇臂轴（调质前） | 74 | 2 | 1 | 5 | 2 | 0 |
| 钛15L进气摇臂轴（氮化前） | 72 | 3 | 2 | 10,17 | 2 | 1 |
| 钛15L进气摇臂轴（氮化后） | 73 | 2 | 1 | 2 | 2 | 0 |
| 钛15L进气摇臂轴（调质前） | 71 | 2 | 1 | 4 | 2 | 0 |
| 阿瓦隆总成-5259945 | 25 | 25 | 1 | 5 | 25 | 0 |
| 阿瓦隆总成-5259946 | 23 | 25 | 1 | 4 | 25 | 0 |
| 阿瓦隆总成磷化-5259945 | 24 | 22 | 1 | 5 | 22 | 0 |
| 阿瓦隆总成磷化-5259946 | 22 | 22 | 1 | 5 | 22 | 0 |


#### 证据与验证边界

私有审查目录 `.local/routing-version-audit-20261022/`：`snapshot.json` 为只读提取；`analysis.json` 为分层归一化结果；`frozen-verification.json` 为来源模板及报工筛选核对；`migrated-families.csv` 为53组迁移工艺清单；`version-pairs.csv` 对全部同 code 多版本组逐对比较7717对（含目标中同系列的正常/派生记录）；`summary.json` 为迁移对象统计。snapshot.json SHA-256 为 `786bde61a9fd950723f001bb3a998b57ba76dc88980f9b57a18eaeae2c5a8e0c`。

验证采用真实数据库只读提取、全量图结构比较与来源冻结证据交叉核对。未新增/修改业务源码、Schema、Flyway 或业务记录，不运行无关构建测试，不将本次审查宣称为工艺治理完成。项目只追加本审查记录，私有分析产物不作为产品运行入口。


### 2026-10-22 合并停止、阻塞原因与触发器职责/必要性审查

用户先要求停止合并脚本，随后明确将合并滞后，审查触发器数量、职责和存在必要性。本节为审查结论，不是执行删除触发器的授权记录。本轮未提交工艺合并，未继续执行 Flyway 升级；新建但未应用的 V51 合并SQL已移出正式迁移目录，保存为 `.local/routing-consolidation-20261022/V51__consolidate_identical_routing_versions.sql.pending`，避免服务启动或后续升级误执行。Flyway最大成功版本仍为50，版本51记录为0。

#### 停止操作与阻塞原因（真实数据库证据）

仍核实 Factory TEST_DEV_PERF_20261019、数据源9/revision2、ENABLED/LOCAL、192.168.18.25:3306/mes_base_dev_perf_test，server_uuid=03dbc952-7403-11ef-802c-0242ac130002；只读审查会话真实 transaction_read_only=1。

此次执行方案过重：回滚演练不仅处理685张工单，还一次更新执行、路径、报工、质量与来源映射中的大量节点引用，并对 workorder、execution、routing及其图/产品表全表加锁。前七类引用更新即涉及320703行；维护期四个触发器又逐行比较不变属性，其中来源JSON造成重复载荷检查。第一次演练连接中断；第二次为优化此成本主动停止。二者均未形成正式合并提交，不能把未完成的演练宣称为全量通过。

停止写入后数据库仍需撤销已产生的undo；观察到事务4117749/线程42983处于 ROLLING BACK，一度有约86万行锁、32万余条undo。恢复触发器的 `DROP TRIGGER IF EXISTS mes_defect_no_update`（线程43001）等待该事务释放元数据锁。**当时的直接阻塞链是大事务回滚持锁→维护DDL排队，不是每次业务请求都执行23个触发器。** 该执行方案和演练方式是本次维护的问题，不归咎于触发器总数。

用户要求停止后，已终止仅属于本任务的写入/排队维护连接，不再留下自动恢复DDL队列。14:52:34只读快照确认回滚结束，无活动维护写入、无待决元数据锁，工艺801条、节点9055条、连接8254条，保持合并前规模。随后仅完成停止操作必要收尾：恢复本轮临时替换的4个UPDATE触发器，清理本轮6个维护过程。实际比对全部23个触发器定义与演练前一致，`routing_merge_%`过程为0；没有删除正式触发器。全部17类引用列计数、按实体类型的来源映射计数及证据样本与演练前核对一致。此为事务回滚/计数/定义证据，未声称停止后另跑了全表逐行数据摘要或业务生产闭环。

#### 数量、职责与业务影响

实际业务库23个触发器，中心库 mes_base_dev 为0；未将此数量外推为每个工厂物理库均已核对。V49列出的21个旧迁移写门禁与实际触发器名称交集为0，已删除，没有恢复。现有23个全部为BEFORE触发器（12个UPDATE、11个DELETE），没有INSERT或AFTER触发器。

它们仅承担“检查并拒绝”职责，不写其他表、不计算库存或生产数量、不自动创建单据、不执行ERP、不推进状态。20个原定义是无条件SIGNAL，禁止指定表UPDATE/DELETE；另外2个比较OLD/NEW来源；最后1个只读查询交接引用。正常INSERT不会运行这些UPDATE/DELETE保护，SELECT也不会执行它们。来源/幂等/数量/权限/状态和事务仍由正式Service实施。

- 18个：9类业务/历史事实表各一对“禁止UPDATE、禁止DELETE”，保护报工、不良版本、质量处置、交接、质量放行、仓储接收、完工、工单作废、库存动作。
- 2个：禁止原位修改/删除冻结来源映射及数量证据。它们来自V40，但职责是证据保护，不是已移除的迁移准入/激活门禁。
- 2个：工单与仓库已有非空source不得改值或清空；并不替代来源合法值、运行模式、provider/binding和权限校验。
- 1个：存在生产交接记录时拒绝删除对应执行，保护跨表引用。

#### 逐项职责与处置判定

以下“候选”表示正式业务运行未发现必须依赖该触发器完成的用例，不表示删除后数据库直写防护已经等价实现；需要同步明确写账号权限并迁移相关测试。普通生成Mapper仍包含UPDATE/DELETE方法，不将“没有HTTP入口”误写成“底层无法写入”。

| 触发器 | 操作 | 实际职责 | 必要性审查 |
| --- | --- | --- | --- |
| `mes_defect_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_defect_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_execution_completion_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_execution_completion_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_legacy_mapping_no_delete` | DELETE | 禁止原位修改/删除冻结来源映射和数量依据 | 可移除候选，规则由服务/写权限承接 |
| `mes_legacy_mapping_no_update` | UPDATE | 禁止原位修改/删除冻结来源映射和数量依据 | 可移除候选，规则由服务/写权限承接 |
| `mes_quality_release_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_quality_release_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_quality_treatment_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_quality_treatment_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_receipt_execution_guard` | DELETE | 存在交接事实时拒绝删除对应生产执行 | 先补Service显式交接引用检查，再移除 |
| `mes_receipt_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_receipt_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_report_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_report_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_warehouse_acceptance_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_warehouse_acceptance_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_workorder_cancellation_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `mes_workorder_cancellation_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `wm_stock_action_no_delete` | DELETE | 禁止原位物理删除事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `wm_stock_action_no_update` | UPDATE | 禁止原位修改事实记录；不执行业务计算 | 可移除候选，规则由服务/写权限承接 |
| `wm_warehouse_source_immutable` | UPDATE | 禁止将已有非空业务来源改为其他值或清空 | 可移除候选，规则由服务/写权限承接 |
| `workorder_source_immutable` | UPDATE | 禁止将已有非空业务来源改为其他值或清空 | 可移除候选，规则由服务/写权限承接 |


#### 现有代码承接证据及缺口

1. **事实追加20个保护的承接：** ProductionReportSubmitService / ProductionReportService 通过 insert、insertBatch 追加报工及订正；ProductionDefectService 通过新revision行订正/作废；QualityTreatmentService、QualityReleaseService、ProductionCompletionService、WorkOrderCancellationService追加事件/事实；LocalStockService在同一事务更新当前余额并insert库存动作，未发现正式入口原位改写/删除动作账本。StockAction、质量放行/完工内部CRUD展示只提供查询；ProductionReceiptService和WarehouseAcceptanceService当前为历史只读入口；LegacyHistoryMapper / QueryService仅查询冻结证据。故这些保护是数据库的额外防改手段，业务规则本身不要求以触发器实现。生成Mapper的完整CRUD仍存在，未来移除时应限制调用方和运行账号权限，不能靠保留触发器掩盖错误写入口，也不能宣称Service可拦住数据库管理员SQL。
2. **来源2个保护的承接：** WorkOrderModels.Save、WmWarehouseUpdateCommand不含source。LOCAL创建由服务赋值，EXTERNAL同步检查旧对象必须为EXTERNAL及可信绑定，普通编辑保留旧source；WorkOrderService.update、WmWarehouseServiceImpl.updateById执行来源/模式校验。未发现正常API需要将已持久化source改派为另一体系，故触发器不是当前正式链路的唯一权威实现。移除它们仍保留来源隔离规则。
3. **交接引用1个保护需先补：** ProductionExecutionRules.deleting（133行起）锁定执行，检查开工、领料、报工、派工、不良历史及LOCKED路径，但未显式检查 mes_production_receipt。原触发器V28第31行恰好补这一条查询。现有正常事实往往会被其他状态检查间接拦住，但不能把间接条件当成显式覆盖。建议在既有删除规则与相同执行锁下补交接引用检查后移除此触发器；本轮不新增外键，也未证明存在可通过正式入口删除真实交接执行的线上复现。
4. **数据库层替代保护：** 如需继续阻止运行账号直接改账本，可采用账本SELECT/INSERT、冻结证据SELECT的最小表权限，维护账号与运行账号分离；来源字段也可采用列写权限配合服务白名单。此为可选实施方式，本轮未修改账号/授权。没有发现必须保留触发器才能满足的独有业务职责，建议整体向无业务触发器方向收敛，先补齐第3项，配套写入口与权限策略，再以新的正式迁移移除，不倒改V25–V49历史SQL。
5. **测试不能原样保留：** SourceIsolationMySqlTest直接断言改source返回45000；ProductionReportMySqlTest、ProductionDefectMySqlTest、QualityWorkflowMySqlTest和WorkOrderCancellationMySqlTest含直接SQL修改/删除被拒绝断言。将来去触发器时，应改为真实业务入口的不可编辑/追加订正/引用保护测试；如选用数据库账号权限，还应使用受限运行账号验证权限拒绝。不能继续用root写SQL失败来冒充无触发器后的产品保证。本轮仅审查测试源码，未运行会写库的测试。

关键源码：`ProductionExecutionRules.java:133`、`WorkOrderService.java:300`、`WmWarehouseServiceImpl.java:155,332`、`ProductionReportService.java:221`、`ProductionDefectService.java:193–202`、`QualityTreatmentService.java:192,423`、`QualityReleaseService.java:172`、`ProductionCompletionService.java:174–197`、`WorkOrderCancellationService.java:90`、`LocalStockService.java:349`、`LegacyHistoryMapper.java`。原始触发器来自业务迁移V25、V26、V28、V32、V34、V35、V36、V37、V40；完整23项名称、表、职责、维护状态、处置建议见私有 `trigger-responsibilities.csv`。

#### 本轮维护临时状态的单独结论

本轮只替换过 `mes_report_no_update`、`mes_defect_no_update`、`mes_quality_treatment_no_update`、`mes_legacy_mapping_no_update` 四个既有名称，没有增加触发器总数。临时定义通过会话变量允许维护连接改变关联列；这种变量可由SQL会话设置，不适合作为长期授权或不可变事实边界。它们现已全部恢复原定义，6个本轮维护过程已清理，停止收尾完成。尚未执行上表提出的正式触发器移除，没有修改业务源码、接口、账号权限、正式已执行迁移或应用服务。

证据目录 `.local/routing-consolidation-20261022/`：`inspection.json`（维护前定义）、`stopped-audit.json`（停止初始快照）、`trigger-audit-state.json`（回滚结束快照）、`center-triggers.json`、`trigger-responsibilities.csv`、`trigger-summary.json`、`stop-cleanup.json`、`references-after-stop.json`。根因与职责判断基于真实MySQL只读状态和当前源码；“可以移除”的部分是替代实现审查结论，不是已实施的发布结果。合并继续搁置。

#### 2026-10-22 用户确定代码层方向：逐项业务合理性评审清单

用户要求逐项列出并共同评审合理性，最终全部迁往代码层。方向已明确，但不表示用户已经认可现有整表禁止修改/删除规则。本节列出待评审建议，不实施触发器移除、不改变已有记录，不恢复工艺合并。上节数据库表/列写权限仅为当时备选，不作为本轮迁移业务规则的承接方案；不新增另一套数据库业务门禁。代码约束作用于正式服务入口，不承诺拦截管理员直接SQL。

下表前10项每项对应既有 `_no_update`、`_no_delete` 两个触发器，后3项各对应1个，共23个。修改和删除应分别评审，不能以“事实表”名义直接认定整表永久只读。

| 项 | 对象/触发器前缀 | 当前限制 | 待评审建议及理由 | 代码承接点/待补项 |
| --- | --- | --- | --- | --- |
| 1 | 报工 `mes_report` | 所有字段不能修改，不能删除 | 保留数量订正可追踪；评审备注等非数量字段能否编辑，以及未产生下游业务的错误报工如何撤销；不直接认定所有字段不可改 | ProductionReportService/SubmitService已有追加订正；字段编辑与撤销范围待定 |
| 2 | 不良申报 `mes_defect` | 所有字段不能修改，不能删除 | 已有新增版本订正/作废；评审是否保留版本方式，及未处置记录能否直接编辑；已处置后的修改必须核对处置数量 | ProductionDefectService已有revision订正/作废；须验证当前质量链路联动 |
| 3 | 质量处置 `mes_quality_treatment` | 所有字段不能修改，不能删除 | 已导致返工、报废或库存影响时不能只改一行；评审未执行处置能否修改/撤销，以及执行后纠错用例；不预设新增通用流程 | QualityTreatmentService/QualityDispositionService校验数量、状态、关联与事务；撤销能力不能视为已具备 |
| 4 | 生产交接 `mes_receipt` | 所有字段不能修改，不能删除 | 当前历史只读入口；保留读取，不恢复旧写流程；必要引用修复与业务数量更改分开评审 | ProductionReceiptService当前只读；受控维护不得改变数量与原始依据 |
| 5 | 质量放行 `mes_quality_release` | 所有字段不能修改，不能删除 | 放行可能已被后续业务消费；评审未消费时是否可撤销，消费后是否须先处理依赖；不默认整表禁改 | QualityReleaseService已有正式放行；撤销及依赖检查范围待定 |
| 6 | 仓储接收 `mes_warehouse_acceptance` | 所有字段不能修改，不能删除 | 当前历史只读；新入库遵循当前单据草稿/审核链路，不恢复旧接收写流程 | WarehouseAcceptanceService历史读取；StockDocumentService承担新业务 |
| 7 | 执行完工 `mes_execution_completion` | 所有字段不能修改，不能删除 | 完工撤回涉及执行、工单及入库依赖，应评审具体撤回条件；不能仅删除完工记录制造未完工状态 | ProductionCompletionService承接完工；撤回用例尚未在本轮证明 |
| 8 | 工单作废 `mes_workorder_cancellation` | 所有字段不能修改，不能删除 | 作废原因等字段与恢复工单是不同操作；评审原因订正权限及是否需要恢复能力，不能用删记录代替状态转换 | WorkOrderCancellationService已有作废事实与工单状态同事务写入 |
| 9 | 库存动作 `wm_stock_action` | 所有字段不能修改，不能删除 | 数量/方向/仓库直接改动会与余额脱节；库存纠错须同步处理余额和动作；评审备注等字段可编辑范围 | LocalStockService已有余额更新+动作新增同事务；不推断完整冲销入口已经存在 |
| 10 | 冻结来源映射 `mes_legacy_mapping` | 原始载荷、数量依据、目标引用等全部不能修改/删除 | 原始载荷与数量依据保留；目标工艺/节点引用不应仅因同表而永久冻结，可按明确维护范围核实调整；不恢复一次性导入入口 | LegacyHistoryMapper当前只读；未来引用维护方式另定，当前不执行合并 |
| 11 | `workorder_source_immutable` | 已有非空来源不能修改/清空 | 保留普通业务不能把LOCAL工单改派EXTERNAL的规则，与可信同步身份一致 | WorkOrderService创建/编辑/同步已有校验；检查所有正式入口与来源测试 |
| 12 | `wm_warehouse_source_immutable` | 已有非空来源不能修改/清空 | 保留普通业务不能改派仓库来源的规则，防止库存权威归属改变 | WmWarehouseServiceImpl创建/编辑/同步已有校验；检查所有正式入口与来源测试 |
| 13 | `mes_receipt_execution_guard` | 有交接记录时禁止删除执行 | 保留被引用执行不能删除的规则，避免交接成为孤立记录 | ProductionExecutionRules.deleting须在既有执行父锁/事务内补显式交接引用检查 |

实施依据：先确定每项允许修改的字段、允许撤销/删除的状态及关联影响，再复用现有Service实施，覆盖正式写入口、同事务引用校验与并发；不新增全局拦截器照搬整表禁改。随后将测试由SQL触发器拒绝改为真实业务入口行为测试，新增正式Flyway迁移删除23个触发器，不修改已发布迁移。此步骤顺序为实施建议，当前仅完成清单与评审材料；未修改业务代码、测试、账号权限或数据库，未执行新增测试。

#### 2026-10-22 用户修正评审标准：修改留痕与下游删除保护

此标准更新上一节的评审方向，不将既有整表禁改/禁删作为默认保留规则。用户明确：已有订正流水或变更记录承担修改留痕时，不应再由触发器禁止修改；删除按下游依赖决定，例如工单有执行则不能删，执行有报工则不能删，报工有不良则不能删。最终均由业务代码承接。

- 修改审查：逐项核实实际记录结构。独立变更日志、追加订正行与业务本身的数量流水不是同一种结构；应证明原值/新值或订正关系、操作者、时间和原因确实可追踪，再判断是否已有足够留痕。不因表名含“流水/记录”就宣称修改历史已覆盖，也不强制把现有追加订正改造成原位UPDATE。
- 删除审查：列出真实下游引用及已经产生的关联影响，核对Service是否有明确检查和准确拒绝原因；不能只凭“已开工/已完成”状态代替实际依赖检查。有下游则拒绝当前对象删除，无下游进入正常删除处理，不继续沿用无条件禁删。
- 已生效库存动作、完工等若涉及余额或父对象状态，删除用例仍须在同一业务事务内保持这些关联结果一致；无下游不等于仅DELETE一行即可完成。具体回退方式作为对象级评审项，不另设整表禁删触发器。
- 并发审查：删除检查与删除操作必须处于同一事务，复用现有父对象锁，并核对下游创建也遵守相同锁协议，防止检查后新建下游。权限、可信来源和幂等继续由现有正式业务入口执行。

后续清单统一采用“对象→现有订正/变更留痕→真实下游依赖→当前代码覆盖→缺口”的列结构；上述示例为用户确认的评审标准，不宣称其已全部实现。本轮仅更新评审文档，未修改业务代码、测试或数据库；工艺合并仍搁置。
