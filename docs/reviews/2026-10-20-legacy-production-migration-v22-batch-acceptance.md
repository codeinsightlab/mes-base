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
