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

## 2026-10-16 V1.3：不良传播与单节点返工实施

本章取代上文“晚申报必须因下游超额而拒绝”的业务规则；历史报告及其当时验证结果保留。用户要求真实不良可以晚登记，正常REPORT保持不变，新增报工仍受有效输入约束。本章只描述本轮已实现及分级验证的行为。

### A. 原有契约与改动范围

基线后端HEAD `4679154eccb61444f1c0f795c9620a54ec6e207d`，前端HEAD `02a6554baff657f6c8dfe42cebdcbc9e5f863473`，两仓库已有大量未提交的领料/报工/质量/交接工作，起始逐文件摘要保存在`.local/quality-v13-20261016/baseline.json`。原Quantity在任何节点R大于I时直接抛错，因而上游晚申报会被拒绝；原Quality只有CREATE/REVISE/VOID完整版本和退工序/退仓库登记，没有真实返工完成及恢复事实。

本轮业务文件：

- `production/service/ProductionQuantityService.java`：唯一传播投影及新增正常报工门禁。
- `production/domain/ProductionReportModels.java`、`production/controller/vo/ProductionReportViews.java`：新增传播/恢复/原始差额字段，保留旧defectQty契约。
- `quality/service/ProductionDefectService.java`、`ExecutionQualityReferenceQueryImpl.java`：归属核实、受控版本保护、统一质量贡献；删除Mapper/XML中已废弃的另一套数量聚合入口。
- `quality/service/QualityTreatmentService.java`、`quality/domain/QualityTreatmentModels.java`、`quality/controller/QualityTreatmentController.java`：质量处置及返工唯一业务写入口。
- `quality/domain/ProductionDefectModels.java`、`DefectDestination.java`：可选明确归属依据、新SAME_NODE登记类型；原字段和历史去向类型不删。
- 新增MesQualityTreatment的标准Entity/Example/Mapper/XML；`issue/service/MaterialIssueVoidService.java`改为调用显式投产额度减少校验，避免读取放宽后误放开不安全领料撤销。
- `mes-common/.../ErrorCode.java`新增质量处置冲突与归属待核实安全错误码。
- 前端新增`src/api/quality/qualityTreatment.ts`、`src/components/business/quality/QualityTreatmentPanel.vue`，复用原不良页及ProductionReportManager，补齐生产数量API类型和相关测试。

未重写LOCAL/ERP或单材料投产服务，没有修改Operation/Routing核心代码、创建新Execution或返工DAG，没有正常报工、库存或交接的自动补写。

### B. 历史模型与兼容

目标库基线有67条不良版本，其中30条VOID，登记去向均为WAREHOUSE；这是版本行统计，不是去重后的当前不良人数或数量。V26原有target_node_id、target_warehouse_id、旧上游选择API、版本及触发器全部保留，不重新解释为已经实际退仓或返工。

新页面默认SAME_NODE。原“退工序/退仓库”登记可继续只读及受控版本维护，但不能直接当返工任务；管理员可在没有后续处置事实时留痕订正为所属工序处置。真实处置一旦分配/开始/确认，不允许再REVISE/VOID原申报以抹去来源，也不能修改被有效OVERLAP申报引用的原申报。历史核实事件在详情中跨版本保留，当前贡献只取最新非VOID版本。

缺少实物归属依据的旧记录不自动标成独立不良，也不改写旧行。默认UNVERIFIED，原始申报量仍可见；禁止据不确定投影新增报工或正向交接。需要人工核验加工事实和是否重叠，不能伪造产品唯一身份或批量补“独立”标记使测试通过。

### C. 传播算法、兼容字段和数学不变量

以锁定的Execution确切路径及节点连接为准，首节点I继续来自可信领料投入（含既有撤销预留），不恢复targetQty作为额度。令D为“已核实独立直接不良减可信恢复”：

```text
H = max(0, R - I)
rawAvailable = I - R
rawEffectiveOutput = R - D - H
E = max(0, rawEffectiveOutput)
A = max(0, rawAvailable)
下一实际节点 I = 上一实际节点 E
```

`E = max(0, min(R,I)-D)`，所以有效产出不会超过本节点真实报工或当前有效输入；每恢复1件，任一后续节点的E最多增加1件；尚未发生的末道报工不会因上游恢复而自动增加。H是投影，不写ADJUSTMENT，不作为新直接不良，不在末道再次合计扣减。

保存并返回未钳制的有符号差额。H大于0或rawEffectiveOutput小于0均显式返回historicalConflict。真实不良/恢复会重新计算投影，但不因下游既有H而拒绝；本节点独立不良总量仍不能超过可归属正常加工量。

字段兼容：旧`defectQty`继续表示最新非VOID申报原始总量，包括尚待核实和已关联重复的观察记录，不能直接用它再扣减一次。新增`directDefectQty`表示已核实独立不良；`recoveredQty`为可信恢复；`netDefectQty=directDefectQty-recoveredQty`；`unverifiedDefectQty`为待核实申报量。重叠记录保留原始数量但不再次进入D。新增`propagatedLossQty`、`historicalOverrunQty`、`rawEffectiveOutputQty`、`rawAvailableQty`说明传播及矛盾。

正常REPORT/ADJUSTMENT由统一校验比较修改前后投影：新增正数不能超出当前输入，任何正常订正不能扩大现有历史超报；负数仍不能低于已归属直接不良事实。原领料减少入口继续冻结存在倒挂或待核实的执行，不利用读取放宽绕过原有撤销保护。

候选公式先通过A～F及520251组整数边界检查，再落地。生产单测和五节点HTTP测试调用实际Quantity算法，覆盖部分报工、连续独立不良、恢复及未钳制负差额。公式不提供实物序列去重能力：独立/重叠依据必须明确，未知不能自动当独立。

### D. 最小处置与返工事实

新增一张不可变质量事实表，不创建完整返工平台：

- ASSESS：核实INDEPENDENT（独立）、OVERLAP（完整关联原申报）或保持UNVERIFIED（不写假核实事实）。独立和重叠都需要明确依据；重叠必须引用同Execution已核实原申报，不得跨厂或任意超出原申报数量。
- ALLOCATE：分配直接报废与待返工数量，累计不超过原不良数量。报废不能恢复。
- START：引用本申报的返工分配，只能在原不良所属节点开始；累计开始量不能超过分配返工量。
- RESULT：引用本申报的START，记录实际处理、成功恢复及失败转报废；处理=成功+失败，累计处理不超过该次开始量。处理时间不能早于关联开始。实际操作人来自可信START用户，结果确认人为当前可信登录用户，继续使用sys_user。

状态由事实推导：待核实、关联重复、待处置、待返工、处理中、成功恢复、失败转报废、申报已作废。混合或部分处理数量分别展示，失败不再次扣一遍不良。返工处理4、成功3、失败1只恢复3；原不良10最终未恢复7。

每条事件UUID主键承担可靠请求身份，SHA256指纹包含规范命令及可信操作者，重放返回已有状态；不同内容/操作者复用标识拒绝。锁顺序始终WorkOrder→Execution，重读原版本/实际路径/现有额度后追加不可变事实，同事务校验投影与提交。没有JVM锁、外部HTTP或Saga。

### E. Schema、生成与数据库证据

正式业务Flyway `V32__quality_treatment_facts.sql` 已执行并校验：SIMPLE_B → datasource8 → 192.168.18.25:3306/mes_base_dev_business_simple_b，工厂和数据源均启用。执行前检查目标表不存在并备份原不良/报工/交接/执行范围；没有修改已执行迁移。

`mes_quality_treatment`保存原申报/版本、Execution/原节点、动作/父事件、归属/关联/依据、报废/返工/处理/恢复/失败数量、系统用户、时间和原因。只有UUID主键及普通索引，没有新增非主键UNIQUE或外键。表及每列有中文COMMENT；UPDATE/DELETE由数据库触发器拒绝。

按`docs/development/quality-treatment-codegen.yaml`读取正式库metadata生成完整基础后端及测试，再移除会绕过不可变业务规则的普通CRUD入口/骨架，保留标准Entity/Example/Mapper/XML。生成及移除清单在本轮证据目录，不手写第二套基础Mapper。没有新增公开单表插入、更新或删除入口。

### F. API与权限

- 既有不良CREATE/REVISE命令可带明确assessment，未提供则待核实；VOID及完整版本历史继续保留。
- `GET /api/quality/defects/{id}/treatment`：复用`business:quality:defect:read`，返回当前状态及跨版本处置历史。
- `POST /api/quality/defects/{id}/treatment`：复用`business:quality:defect:revise`，执行核实/分配/开始/结果确认，无新权限体系。
- 既有Report V1/V2和Receipt继续消费同一Quantity结果；Receipt已有98而有效产出90时保留98、显示超额8，不自动REVERSAL、不动库存，正向质量放行仍关闭。

HTTP View对ID、decimal显式转字符串，返回字段白名单；不返回请求摘要、私有用户实体等内部字段。操作人与确认人名称按本批去重ID，通过BusinessDirectoryService.userLabels批量加载，复用通用缓存，不在循环中查用户。

### G. 页面行为

原不良页“处置/返工”打开同一详情内的QualityTreatmentPanel；原申报版本历史继续只读。新申报默认所属工序，不提供选择更早节点的返工操作。面板展示原不良、报废分配、待返工、实际加工、成功恢复、失败转报废及当前净损失，处理与确认分别留痕。

Quantity页面区分历史加工量、当前有效输入、当前正常有效产出、恢复量、历史超报/传播差额；展开项保留原始申报量、待核实量及未钳制差额。H不是新报工，也不自动改写历史。

切厂、撤权、换申报及卸载失效请求代次；迟到结果不能回填。未知提交结果保留同UUID重查，明确4xx拒绝后才允许调整为新命令；结果未确定时不允许普通关闭处置详情丢失请求身份。历史去向记录提示先受控订正，VOID记录不可再操作。

### H. 五道工序A～H

| 场景 | 结果及证据等级 |
| --- | --- |
| A 全部报100，B晚报10 | HTTP/H2及真实MySQL五节点：R均100、C/D/E的H=10、末道E=90 |
| B 报废6，返工成功4 | HTTP/H2及真实MySQL：净损失6、下游H=6、末道E=94，无新正常REPORT |
| C 下游80/60 | HTTP/H2：D可报10→恢复后14，E仍60；真实MySQL部分成功3后D可报13、E仍60 |
| D B10与C独立3 | HTTP/H2：明确INDEPENDENT后末道87；OVERLAP观察3不重复扣减，原申报总量保留；未知归属冻结新写 |
| E 处理4、成功3、失败1 | HTTP/H2及真实MySQL：未恢复7；失败不能再恢复，不可抹去原申报 |
| F 已交接98后产出90 | HTTP/H2及真实MySQL：超额8，原Receipt保留，不写REVERSAL；恢复4后差额为4 |
| G 当前输入90、已报90后+1 | 统一Quantity单测/HTTP正向超额拒绝，既有Report V1/V2回归通过 |
| H 同结果并发恢复4 | HTTP/H2及真实MySQL：同UUID只一条RESULT，最多恢复4；新UUID也不能超消费开始量 |

另有双H2业务库相同命令身份独立记账、跨厂申报读取拒绝和只读账号不能确认测试。不把双H2替代真实两个工厂业务库写入证据。

### I. 验收进度与证据

本轮证据目录`.local/quality-v13-20261016/`，凭证/令牌不公开输出。

- `mvn -B -ntp verify`通过：611项总计，577通过、34项需环境显式启用而跳过；日志`verify-final.log`。
- 单独开启MySQL环境的14项真实相关测试全部通过，日志`mysql-regressions2.log`：Quality数量6、Receipt1、Input1、Report2、不良版本1、V1.3五节点1、Quality生命周期2。首次运行的两项旧预期已按新晚申报/明确归属规则替换；一次连接中断单独记录，重跑通过。
- 前端55个文件313项测试及build通过，日志`ui-test-final.log`、`ui-build-final.log`。新测试覆盖部分结果、同请求重放、明确拒绝重建请求、切厂/撤权及换申报迟到响应。
- 27个手工Java文件经原生格式化；生成文件保持生成归属。打包冒烟及最终V1.3定向证据见smoke/final-v13-tests日志。
- 初次运行实例：管理员数量API200，质量API403；中心库没有任何角色关联质量权限，菜单本身已正式登记。因此浏览器显示未登记页面是当前账号无入口授权，不将此冒充UI通过。已向用户请求仅给既有TEST角色146/147临时配置本轮权限，并在验收后恢复；未取得明确答复前不修改授权。

真实API/UI最终结果将在权限授权及服务清理后追加到本节，当前不能声明UI_VERIFIED。

### J. 并行开发与Git

本轮未提交、推送、部署或重置。过程中外部操作将后端HEAD推进至`a6363a4e1d338b211645b78bd3b94fb2725d0dd8`、前端至`94167e0c25fd9cb4dfcac5ecef369f6e8b715546`；本轮质量增量仍在工作区，未覆盖这些提交。所有参考项目保持只读，本Agent没有写入Source。

没有实施上一轮讨论的本地工单用料维护收敛，也未修改LOCAL/ERP配置及对应开发。新迁移占用business V32，后续并行迁移应避开版本冲突。

### K. 业务限制

- 没有产品序列/实物唯一标识；人工依据必须明确，不自动推断不同工序、不同发现时间代表不同实物。完整重叠可关联；部分重叠需拆成明确记录或保持待核实，不自动分摊。
- 只支持原节点返工；不回更早节点、不重走下游、不自动完工/入库、不绕过Receipt质量放行。
- 已有处置事实后不开放原申报擦除或随意改数量；需要新的受控处置订正能力时另行建模，不能改不可变行。
- 单Execution当前最多1000项当前申报、5000项处置事实；数据库分页读取至上限+1后明确拒绝，不无界加载、不静默截断。大规模归档/历史分页需另行扩展。
- 存量没有加工依据或去重证据时需要人工核验，不为“可运行”补造投入、加工或独立不良事实。

### L. 当前结论

SCHEMA_READY=YES；BUSINESS_VERIFIED=YES（分级证据如上）；API_VERIFIED=待真实授权正向复验；UI_VERIFIED=待真实授权复验。实现及自动化通过，当前运行环境结论为CONDITIONAL GO，不能把权限受阻的页面或隔离测试声明为完整真实UI验收。

### 本轮交付时的权限与服务状态

最终V1.3定向测试（包含严格核对MySQL不可变触发器错误消息）10项通过；打包冒烟10通过/0失败。新API已纳入运行实例，管理员生产数量查询200，但质量读取403。再次查询中心库，持有质量权限的角色数仍为0；本轮没有自动授予权限，TEST角色146/147原始授权快照及精确临时方案已保存，等待用户明确答复。

真实浏览器保持SIMPLE_B/管理员身份，质量URL显示“页面尚未登记”，因为授权导航没有质量入口。证据`ui-permission-blocked.jpg`。这不是质量页面正向验收，四档业务页面视觉验收尚未完成；不将前端自动化和build等同于真实UI。

已按用户“验收完必须关闭”要求关闭本轮临时启动的后端，正常执行Quartz/连接池关闭，8081端口核对不再监听；前端5173未停止。`service-cleanup.json`记录临时服务停止且未修改角色。后续取得授权后可临时启动完成正向API/UI并恢复TEST角色，结束仍关闭服务。

### 2026-10-17 用户授权后的真实验收（替代上述待授权结论）

用户明确要求直接给admin授权。检查发现其平台管理员角色100同时被两个账号持有，因此没有修改共享角色。通过正式`POST /api/system/role`创建FACTORY角色148（admin-quality-simple-b），仅含既有质量目录2607和read/create/revise菜单2608～2610，通过`PUT /api/system/role/authUser/selectAll`只绑定admin用户100。原角色及菜单保持不变，TEST角色146/147未修改，没有新增void、Receipt或其他授权。该授权按用户要求长期保留，不再执行旧临时TEST授权方案。变更前快照、角色详情与成员/菜单核对保存在本轮证据目录。

实际服务继续使用SIMPLE_B绑定datasource8的指定验收库。临时后端使用本轮已verify的jar启动，既有5173前端保持运行；没有重启用户服务、提交或部署。质量API返回200，普通无质量权限账号POST返回403；同一admin在DEV访问质量记录返回403。浏览器真实登录后已出现质量管理/不良品管理菜单和本轮操作。

#### 真实浏览器写入与API复核

只使用本轮已创建的五节点TEST样本：工单168 / Execution181 / TEST_QUALITY13_32d77b61。新增申报`75b159d0-6709-49dd-b558-f2ba15ee3dfe`，C节点1581的独立不良2，原因含`TEST ONLY UI QUALITY13 20261017`。没有修改客户事实。

在浏览器依次完成：保存申报（UNVERIFIED）、填明确依据核实INDEPENDENT、报废0/返工2分配、关联该分配开始2、关联该START确认处理2/成功2/失败0。页面最终显示“返工成功已恢复”、原始不良2、加工完成2、确认恢复2、未恢复0。四条处置事件均显示实际操作人与确认人“平台管理员”。

随后通过真实HTTP读取并断言：本申报恰有4条事件；五个节点正常报工仍均为100；末道有效产出94。使用同一RESULT UUID、内容及操作者重放200，事件数仍4；使用新UUID再次恢复同一START返回409。已有B节点不良10、报废6/成功4的事实仍保留。Receipt用既有有权验收账号查询（没有给admin增加Receipt权限）：净交接98、末道产出94、超出4；无自动冲销或库存变化。

#### 视觉与状态验证

- 实际浏览器质量弹窗1280、1440、1920、390宽度验收；数量页面1920和390宽度验收。桌面摘要两列，手机摘要单列标签/数值，小数保持完整；大表在自身容器横向滚动，弹窗纵向滚动可达操作和历史。
- 发现并修复SAME_NODE新申报误显示目标仓库字段的问题；只在历史WAREHOUSE去向编辑时保留目标仓库，新增行为回归测试通过。
- 手机处置摘要曾把小数折断，已调整窄屏描述表布局并复验。
- 真实切SIMPLE_B→DEV：旧数量页跳回工作台、旧Execution数据和业务页面标签被清除；质量菜单不再出现。恢复SIMPLE_B后原授权正常。真实DEV API为403。
- 撤权、查询迟到、换申报与不确定提交重试由前端自动化覆盖；本轮没有在真实服务再次撤销admin授权或制造网络迟到，不将自动化提升为真实故障注入。
- 临时运行实例采用JVM UTC默认时间，填写晚于服务当前时间会触发既有`PastOrPresent`校验；合成事实使用过去的2026-10-16时间完成验证。本轮未修改系统时间或全局时区配置。

证据：`.local/quality-v13-20261016/live-api.json`、`live-ui-business.json`、`live-factory-access.json`、`admin-authorization-result.json`、`ui-quality-{1280,1440,1920,390}.png`、`ui-quantity-{1920,390}.png`。截图为真实本地页面，样本事实明确TEST，不代表真实车间实物已加工。

#### 最终验证与交付状态

后端本轮生产代码沿用已通过的全量verify（611总计、577通过/34显式环境跳过）、14项真实MySQL相关回归、10项最终V1.3定向验证和10项打包冒烟。本次授权后只修前端条件和窄屏布局，没有修改后端代码。前端最新55文件314项测试通过，`ui-test-acceptance.log`；最新build通过，`ui-build-acceptance-final.log`，仅保留既有大chunk提示。两仓库`git diff --check`通过。

SCHEMA_READY=YES；API_VERIFIED=YES；UI_VERIFIED=YES；BUSINESS_VERIFIED=YES。结论为本轮单节点V1.3范围GO；前述K节业务边界仍然适用，不承诺实物唯一追踪、多节点返工、自动质量放行或自动入库。真实第二业务库写入隔离和真实撤权/网络迟到故障注入未重复执行，相应隔离/竞态以已列自动化证据为准。旧CONDITIONAL GO只记录当时授权受阻状态，以本节为最新结论。

验收收尾：本轮临时后端已正常关闭，8081端口连接核对不再监听；5173前端保持原状，浏览器视口已恢复。admin专用角色148按用户授权保留。清理证据`service-cleanup-acceptance.json`，没有遗留Agent启动的后端进程。
