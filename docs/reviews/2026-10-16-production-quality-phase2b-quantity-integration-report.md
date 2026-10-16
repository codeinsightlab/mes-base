# Base MES Phase 2B — Production × Quality Quantity Integration Report

日期：2026-10-16。仅实施正常实际路径的生产报工与有效不良数量联动。本文源码路径相对于 `/Users/user/work/lixin/mes-base`；前端路径相对于 `/Users/user/work/lixin/mes-base-ui`。行号以本轮最终工作区为准。

## A. 仓库基线及并行工作保护

后端 main，HEAD `4679154eccb61444f1c0f795c9620a54ec6e207d`；前端 main，HEAD `02a6554baff657f6c8dfe42cebdcbc9e5f863473`。完整 dirty/untracked 清单与已有文件 SHA256 存于 `.local/production-quality-phase2b-20261016/baseline.json`。

后端基线包含 Phase 2A 尚未提交实现、ID倒序修改、多个评审文档，以及并行报工 requestId、操作员资格、提交入口、V27/V30 等工作。本轮保留所有这些改动，未编辑其迁移或资格规则。共享 `ProductionReportSubmitService.submit` 仅替换数量校验调用；共享 HTTP fixture 只补充新依赖/错误适配。前端原已有 `src/styles.css`、`src/views/production/productionReporting/index.vue` 未提交修改：styles完全不动，报工页只增加质量数量列、选中节点数量展示和口径说明，保留原有布局改动。不对其他 Agent 文件整文件格式化；未改 Source Product。

## B. 原始生产数量契约与修改入口

原 `ProductionQuantityService`：reportedQty=报工 SUM，defectQty=0，effectiveOutputQty=reportedQty，下一节点 inputQty=上节点 reportedQty，qualityIntegrated=false。

当前 `mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java`：

| 方法/位置 | 行为 |
|---|---|
| `getExecutionQuantities:52` | 加载实际有序路径、独立报工/质量批量汇总，调用统一 calculate；冲突不返回伪造正常数量 |
| `load:56` / 质量读取`:74` | 仅组装内部数量快照，不先校验旧状态；候选 VOID/订正可修复旧状态，但必须使候选整条路径满足约束 |
| `getNodeQuantities:96` / `getNodeQuantity:100` | 继续复用执行数量结果 |
| `validateChange:118` | 保留既有静态入口，改用相同 calculate，仍要求报工路径可写 |
| `validateReportChange:126` | 写入持锁后加载最新快照并检验候选报工变动 |
| `validateDefectChange:133` | 写入持锁后检验候选不良贡献变化；不新增质量状态或强制质量申报先开工 |
| `calculate:137` | 读取、报工变更、质量变更的唯一数量公式与全路径校验 |

`getWorkorderQuantities:105` 的 `lastNodeReportedQty` 仍是末节点加工总报工量，不改名伪装为良品/成品数量，也不把不同节点加总成成品总量。没有新增表、DTO字段或第二套累计数据。

## C. Quality 聚合接入

新增类型化只读接口 `mes-business/src/main/java/dev/mesbase/business/quality/service/ProductionDefectQuantityQuery.java:10 nodeTotals`。已有 `ExecutionQualityReferenceQueryImpl.java:30` 同时实现该契约，复用原 Quality Mapper `nodeTotals`。没有再建一套聚合 Service/SQL；生产数量服务依赖该只读接口，绝不依赖 Quality 写入 Service，因此不存在 `Quantity → QualityCommands → Quantity` 循环。

原 SQL 位于 `mes-business/src/main/resources/dev/mesbase/business/quality/dao/MesProductionDefectMapper.xml:113 Latest / :126 nodeTotals`：先按 declaration_id 排除存在更大 revision_no 的版本，然后排除最新 VOID，再按 execution_id / affected_node_id 聚合。原 `sumEffective` 与 Quality 展示接口继续保留。未 JOIN 原始报工明细、未累计历史版本、未按发现/责任节点扣减。

一次加载执行数量仅调用一次 `defects.nodeTotals(executionId)`，映射到实际选中节点；未选路径节点不参与正常流转计算。空结果是零，不将 Mapper/表/数据源错误当作零。qualityIntegrated=true 只在质量批量查询成功、完整候选计算通过后随实际节点返回；未选路返回空节点，不谎报已选可写路径。

## D. 读取及写入统一公式

以实际路径顺序 N1…Nn 计算：`R=SUM(quantity_delta)`，`D=SUM(申报最新非VOID defect_qty)`，`E=R-D`，首节点 `I=targetQty`，后续节点 `I=前节点E`，`A=I-R`。逐节点要求 `0≤D≤R≤I`。calculate 只用 BigDecimal，不用浮点或前端公式。

报工仍是加工完成总量（包含不良）；不良事实独立存在。退工序、退仓库两种处置均扣减影响节点的正常流转量；处置目标不贡献普通报工量、不释放不良、不创建返工路径。

冲突采用既有 `409 / REPORT_QUANTITY_CONFLICT`。新增 `production/domain/ProductionQuantityConflictException.java` 与 `production/controller/ProductionQuantityErrorAdvice.java:18 conflict`，使用原 ErrorResponse 协议，明确返回冲突节点、累计报工、不良、可用输入及上游影响节点/调整后有效产出；只公开类型化数量事实，不将 SQL异常或用户输入直接拼作错误。

## E. 质量命令与报工订正的一致性

`mes-business/src/main/java/dev/mesbase/business/quality/service/ProductionDefectService.java:98 execute / :127–130`：保留 requestKey 指纹、锁后幂等复查、expectedRevision、创建后 execution_id/affected_node_id 不可变、完整版本 INSERT、审计及 LOCAL_ONLY acknowledgement。候选 D 为 `当前汇总 - oldContribution + newContribution`；CREATE旧贡献0，REVISE替换旧贡献，VOID新贡献0。全路径通过后才 append，拒绝不追加版本、不改下游、不写报工 ADJUSTMENT。

报工入口均保持既有 WorkOrder→Execution 锁及具名 READ_COMMITTED 业务事务：

| 入口 | 统一数量校验 |
|---|---|
| 正式提交 `production/service/ProductionReportSubmitService.java:92 submit` | `:102 validateReportChange`；requestId重放仍只返回旧成功事实，不产生新数量 |
| 管理员补录 `ProductionReportService.java:55 supplement` | `:63 validateReportChange` |
| 正/负订正 `ProductionReportService.java:93 adjustLocked` | `:97 validateReportChange`，保留原始报工余额不能为负 |
| 原始报工冲销 `ProductionReportService.java:85 voidOriginalReport` | 继续调用 adjustLocked，按负余额候选校验 |

`A报100、B报98、再为A补不良5` 返回409（A调整后有效产出95，B已报98），报工历史与质量历史均不变。报工减至小于已有不良同样409。仅普通非身份备注更新、开工等操作不增加数量查询门禁；报工本身现已必须依赖真实质量数量，查询故障不再允许报工。

## F. 历史数据扫描与启用条件

扫描通过 `mes-admin/src/test/java/dev/mesbase/acceptance/AcceptanceBusinessDatabase.java:18 source` 读取中心真实 SIMPLE_B 绑定，核对 `192.168.18.25:3306/mes_base_dev_business_simple_b` 与工厂/源启用状态；没有按同名库自行替换目标。测试自身 SQL 不进行迁移或业务历史修复。

首次扫描在修改生产计算前执行，证据 `.local/production-quality-phase2b-20261016/historical-audit.yml`：报工20条，质量历史20版；D>R、下游报工>上游E、质量节点/选路异常、疑似重复有效申报、报工不在实际路径内，均0项。扫描源码 `ProductionQualityQuantityAuditMySqlTest.scanExistingFacts`。疑似重复采用相同执行/影响/发现节点、数量、发生时间的多 declaration 启发式，仅是候选，不能证明同一实物。

存量报工究竟按总加工量还是合格量录入，SQL无法证明。当前任务已明确新口径为含不良总量，但不能仅用“数字无冲突”声称过去的业务录入口径已核验；正式启用前仍须核对存量来源及操作说明。若发现冲突，读取返回明确冲突，写入只允许满足全路径约束的候选状态，不自动改历史、改target或吞掉不良。

真实测试均使用新建 `TEST_QTY_*` 工艺、节点、工单、Execution，复用既有启用产品/工序/仓库只读。每次写入前备份既有报工和质量历史范围，结束后逐字段比较一致；目录内有 `mysql-existing-reports-*.yml`、`mysql-existing-quality-*.yml`、`TEST_QTY_*.txt`。测试结束用正式 VOID 追加测试申报终止版本；保留测试报工及ADJUSTMENT用于追溯，没有更新/删除历史版本。失败的测试准备保留自己的空执行清单，不清理用户数据。

测试后第二次只读扫描通过（`audit-after.log` / `historical-audit-after.yml`）：中心实际绑定 datasource_id=8，Factory/源均ENABLED；报工38条、质量44版、有效申报0。上述全部异常仍0，补充的负报工/超目标检查亦0。相比初始增加18条报工流水、24版质量历史，均属于本轮标记测试及追加收尾事实；各Harness的既有范围对比通过，不代表用新增记录改写了原20/20历史。没有把“有效申报0”的当前库扫描替代真实测试中正数D的过程验证。

## G. 事务与并发证据

沿用 `production/service/ProductionChainLocks.java:37 order / :47 execution`，统一业务事务内 WorkOrder→Execution 锁。Quality `ProductionDefectService.java:83–94` 与正式/补录/订正报工均使用 `businessTransactionManager / READ_COMMITTED`。取得同一 Execution 锁后读取最新 R、D、校验候选、追加事实。只读查询的 REQUIRED 事务加入当前业务事务，不开第二个写库，不跨线程传 Session。中心历史用户校验仍使用既有受控只读窗口。

真实 MySQL 测试 `ProductionQualityQuantityMySqlTest`：

| 方法/行 | 结果 |
|---|---|
| `qualityCreateVersusNegativeReportAdjustmentBothOrders:112` | Q先提交则负报工拒绝；负报工先提交则Q拒绝；两种顺序均验证 |
| `upstreamDefectVersusDownstreamReportBothOrders:131` | Q先提交则超量下游报工拒绝；下游先提交则Q拒绝 |
| `twoQualityCreatesCannotOverdrawSameProcessedQuantity:147` | R10时两个D6只有一个成功 |
| `concurrentRevisionsReplaceContributionAndKeepExpectedRevision:160` | 相同expectedRevision只有一个订正成功，汇总为替代值，非历史相加 |
| `voidVersusReportAdjustmentSerializesWithoutLosingHistory:176` | VOID释放容量后，等待中的负订正按新状态完成，历史完整 |
| `differentWorkordersDoNotShareAnExecutionQuantityLock:195` | 一个执行持锁时，另一工单执行在3秒界限内提交，无新增全局锁 |

ordered helper 在首事务持有真实数据库锁时启动第二调用，确认200ms仍未完成，释放提交后核对第二调用的200/409；非Mock。测试以已核实单一业务源/真实中心身份读取运行，不能冒充用户HTTP准入下的真实双工厂动态路由。跨Factory同ID使用双路由H2 HTTP证明；其他跨库/租约安全由全量既有基础设施测试回归。既有父工单锁会串行同一工单内不同Execution，本轮没有承诺消除此等待。

## H. 测试与失败记录

证据目录 `.local/production-quality-phase2b-20261016/`，前端日志在 `/private/tmp/mes-quality-phase2b-ui-*.log`。

| 实际命令 | 结果 |
|---|---|
| `MES_QUANTITY_AUDIT_MYSQL=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionQualityQuantityAuditMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | 只读历史扫描1通过，`audit.log` |
| `mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionQualityQuantityTest,ProductionQualityQuantityHttpTest,ProductionDefectHttpTest,ProductionQualityLifecycleHttpTest,ProductionReportHttpTest,ProductionReportingHttpTest -Dsurefire.failIfNoSpecifiedTests=false` | 当时42项通过（单元10、HTTP32）；`targeted.log`；随后新增1项HTTP及更多断言纳入完整verify |
| `MES_QUALITY_QUANTITY_MYSQL=true MES_QUALITY_REFERENCE_MYSQL=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionQualityQuantityMySqlTest,ProductionQualityLifecycleMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | 8通过、0失败/错误/跳过；`mysql-final.log` |
| `MES_DEFECT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionDefectMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | 原质量闭环真实MySQL回归1通过，使用自有新执行与真实总报工前置；`quality-mysql-regression.log` |
| 同首行扫描命令（测试后再次执行） | 1通过，`audit-after.log`，绑定与收尾数据见F |
| `mvn -B -ntp verify` | 最终BUILD SUCCESS：583项，552通过、31环境跳过、0失败/错误；`verify-final.log`，1分02秒。首次完整551通过，最终增加1项选路/批量读取单元测试 |
| Node22 `npm run test -- tests/productionReport.test.ts tests/productionDefect.test.ts tests/productionReporting.test.ts` | 30通过 |
| Node22 `npm run test` | 52文件、288测试通过；`mes-quality-phase2b-ui-all.log` |
| Node22 `npm run build` | vue-tsc + Vite通过；仅已有大chunk提示，未因此重构打包 |
| `scripts/format-java`（仅本轮自有/原本干净的目标） | 通过；`format.log`、`format-final.log`；共享并行文件未全量格式化 |

HTTP为 MockMvc + 真 Service/MapperXML/Spring事务 + 双H2业务库，中心目录/部分身份为fixture；真实 MySQL 为服务/事务/SQL/锁证据。没有启动/重启用户服务器，用户已运行服务HTTP/UI实测标记 NOT RUN，未将前端jsdom或build当作浏览器业务验收。

旧测试治理：Phase1无报工即可正数不良的fixture改为先有明确加工总报工；Phase2A“质量故障仍可报工”改为数量写入必须失败。零报工的正数Quality CREATE现在必拒绝，原以其成功作为前提的并发删除/重选测试被替换为合法前置条件下的引用/锁测试；保留VOID历史、删除拒绝、路径冻结、批删回滚、权限、身份和不同Factory隔离断言，不保留虚假的旧数量口径。

原 `ProductionDefectMySqlTest.committedLifecycleConcurrentIdempotencyAndImmutability` 已改为使用本轮自有新执行及总报工前置，不再从用户库任取首条执行申报测试不良。真实回归继续覆盖并发幂等/订正、回滚、latest聚合、VOID及数据库不可变触发器拒绝测试行UPDATE/DELETE。无历史质量行被实际修改。

中间失败：新增测试漏JdbcTemplate导入、JSONPath列表断言写法、单元候选边界和旧fixture总报工不足，已修正；最初真实MySQL准备使用不存在的操作员ID1，被原历史人员校验拒绝，改用既有验收ID100后8项通过，未Mock或放宽该校验。没有通过改生产历史使测试通过。

测试源码索引：

- `mes-business/src/test/java/dev/mesbase/business/production/ProductionQualityQuantityTest.java:52` 报工候选边界8组；`:60` 零不良/decimal；`:67` 批量查询故障传播；`:79` 未选节点不参与数量且每类流水仅一次批量查询。共11项单元。
- `mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityHttpTest.java:52` 完整公式/版本替代/VOID恢复/两类去向，`:88` 历史补录与订正倒挂/反向报工保护，`:122` 原因文本/责任节点不改变扣减归属，`:141` 正式报工/跨厂同ID，`:157` 查询故障不允许退化为零。共5项接口集成。
- `mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleHttpTest.java:120` 任意历史保护，`:151` 批删回滚/切厂隔离，`:165` 工单/已发布工艺保护，`:182` 查询故障边界，`:204` 正常领料/开工/合法报工与状态机。共9项（含参数化）。
- `mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityAuditMySqlTest.java:21 scanExistingFacts` 只读异常扫描；`ProductionDefectMySqlTest.java:131 committedLifecycleConcurrentIdempotencyAndImmutability` 真实质量回归；6项真实数量并发方法见G。

默认verify的31项跳过是MySQL、Redis及运行环境开关测试；本轮的6项数量并发、2项引用、1项质量闭环和只读扫描已通过上方独立启用命令执行，其余跳过项不宣称为真实通过。本轮范围与前端 `git diff --check` 通过；全工作区仍有并行报告Mapper XML的既有行尾空格，未借本任务清理它。

## I. 边界审计与前端

- 不改 ProductionReport不可变 REPORT/ADJUSTMENT 模型；Quality命令不写报工ADJUSTMENT。候选验证只接受或拒绝，不修补下游/target。
- 不新增Schema、索引、累计表或放行表；不修改V26/V27、Flyway记录，不执行repair/迁移。数量/质量命令是既有手工业务入口，检索mes-generator没有对应数量公式模板，无需另生成CRUD。
- 不修改已有Execution实际路径或Routing DAG；MySQL测试只准备自己的新TEST元数据。生产锁/状态定义均沿用。
- 不实现返工任务/返工报工、库存移动、ERP报废、工资；ERP仍LocalProductionDefectErpGateway，测试acknowledgement为LOCAL_ONLY/externalExecuted=false。
- 前端 `src/components/business/production/ProductionReportManager.vue:97` 更新含不良总量说明，`:103` 原defectQty/E列读取后端；`src/views/production/productionReporting/index.vue:90` 增加真实D/E显示，`:103` 更新总量说明；`src/views/quality/productionDefect/index.vue:82–83` 去除Phase1未扣减旧说明。质量提交失败时原`:66 catch`只设置formError，保留表单/幂等键；新增测试证明数量冲突不关窗、不报成功。
- V1 declaration 表示操作者声明的不良产品数量，同一件产品的多个原因不能自动变成多件；无实物序列身份，系统不自动跨declaration去重。一个原因文本列出多个缺陷仍只扣本条defectQty；不同申报按数量求和，必须人工避免同一实物重复申报。

## J. 最终结论与停止边界

**实现与隔离验收：GO。真实运行环境正式启用：NO-GO（待存量报工口径核验及用户按既有流程加载新后端、完成运行中HTTP/UI复验）。** 此NO-GO是明确的启用边界，并非扫描发现数量倒挂；当前指定库的数量/路径扫描为0异常。

已完成统一读取/候选校验、质量贡献替代/VOID、报工反向保护、明确冲突消息、批量查询无N+1、既有引用保护回归，以及9项真实MySQL业务测试和测试前后扫描。存量报工含不良语义不能由SQL证明，实物级去重也未提供；不将这些限制隐藏为“已安全上线”。10项选定并行基线文件（含前端styles）SHA256保持不变，证据 `parallel-preserved.json`。

最终验证：后端552通过/31环境跳过、前端288通过且build通过；另行启用的真实MySQL业务9通过、只读扫描两次通过。没有尚未处理的本轮代码/自动化失败。没有执行用户运行中服务或浏览器实测，也未把真实单业务源并发扩大宣称为真实双厂MySQL验收。

实施目标为ProductionQuantityService唯一计算并校验正常路径R/D/E/I/A；不进入Phase2C。存量口径核验、用户部署/重启及真实界面复验是独立边界。本轮不提交、推送、部署、重启，也不修改既有不可变事实。
