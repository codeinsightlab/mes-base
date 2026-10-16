# 正式生产报工 V2 实施与验收（2026-10-16）

## 结论

| 标记 | 结果 | 证据范围 |
| --- | --- | --- |
| SCHEMA_READY | PASS | 指定 SIMPLE_B 业务库 V27、中心库 V30，Flyway migrate/validate 成功 |
| API_VERIFIED | PASS | 正式用户服务 26 项 API 检查、追加真实并发同请求重放及角色撤权403 |
| UI_VERIFIED | PASS | 独立菜单、仅报工权限工人登录、扫码文本+回车、真实报工、路径、数量刷新、切厂/撤权清理及4档宽度 |
| BUSINESS_VERIFIED | PASS | TEST 工单91 / Execution101 / 领料76；正式 REPORT、幂等、两人并发超报拒绝、订正后新报工、首次开工和报满不自动完工 |

本轮实施范围 GO；不是正式生产环境部署、实体扫码枪硬件、真实断网恢复或完整生产入库闭环的认证。所有真实数量写入只发生于新建 TEST ONLY 样本，保留不可变流水，不清表、不绕过触发器。用户服务由用户重启，Agent 未重启、提交、推送或部署。

## A. 当前真实契约

- `ProductionIdentityService.resolve/parse` 接受严格 `EXE:{正整数ID}`，按可信 FactoryContext 读取独立业务库中的 Execution、WorkOrder、Material 与确切已发布过的 Routing。二维码只定位，不触发写操作。
- `ProductionChainLocks.execution` 按 WorkOrder → Execution 锁序执行；正式报工、V1补录/订正及正式开工共用这条锁链。WRITE 事务为具名 businessTransactionManager / READ_COMMITTED，不加 JVM 锁或另一个并发计数器。
- `ProductionQuantityService.getExecutionQuantities/validateChange` 是唯一数量计算/校验入口。实际路径为 mes_execution_path + mes_execution_path_node；按所选路径的真实前后继数量约束，不根据 display_order、最新工艺或其他版本猜路线，不引入 DAG 并行汇合算法。
- `ReportOperatorEligibilityService.requireEligible(executionId,nodeId,currentUserId)` 是正式提交的人员准入：有效用户及当前厂业务授权、启用工序与能力；有派工时必须在当前节点名单。管理员不绕过。原有 requireEligible 命令校验体保留；同一服务新增批量查询提示，避免节点循环查询中心身份和业务表。
- V1 管理员补录/订正/冲销的 API、事务、数量公式与不可变事实仍保留；只适配原 Mapper 新增的可空 request_id 字段。

## B. 新 API、Service 与模型

| 接口 | 权限 | 行为 |
| --- | --- | --- |
| GET `/api/production/reporting/context?code=EXE:101` | business:production:report:submit | 身份、确切路径图、V1数量模型及当前用户各实际节点状态/原因；只读 |
| POST `/api/production/reporting` | 同上 | 新增正式 REPORT 或安全重放原结果 |

请求白名单：`executionId`、`routingNodeId`、`reportQty`、可空 `workstationId`、小写UUID `requestId`。数量为正，最多14位整数/6位小数。接口不接收可写的 recordType/operatorUserId/recordedBy/factoryId/reportTime；多余 JSON 字段不能改变服务端事实。

`ProductionReportSubmitService` 编排身份/数量查询、既有资格服务、执行锁、正式开工和原 `MesProductionReportMapper.insert`。没有新 Facade、规则引擎、第二套事实表、SQL SUM 或数量模型。业务模型 `ProductionReportSubmitModels` 只含命令/候选；Service 内部 Context 是查询模型；Controller 白名单转换为 HTTP Result，ID和数量均以字符串返回。`ExecutionActionController.toView` 开放为静态纯转换，复用原路径 VO；不增加路径写入口。

候选状态为 AVAILABLE / NO_QUANTITY / NOT_ASSIGNED / NO_CAPABILITY / EXECUTION_NOT_READY。只返回实际路径节点，非法或其他版本节点在提交时拒绝。工序名称优先原快照，缺少时沿用现有目录“当前名称”标记。查询是提示，实际领料/工艺/工单/资格/数量均在提交时按正式契约重查；READY候选页面明确提示首次报工还需正式领料确认。

## C. 幂等与 Schema

正式 Flyway：

- 业务 `V27__production_report_request.sql`：原 mes_production_report 增加 `request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL`（中文 COMMENT）及 `uk_report_request_id` 唯一索引。用户本轮已明确授权幂等所需唯一约束。未修改 V25、原不可变触发器或历史流水。
- 中心 `V30__production_reporting_menu.sql`：新增正式菜单2612；生产基础目录2512下的 `/production/productionReporting`；无角色批量授权 SQL。

同次业务用同一UUID；重放比较原行中的 execution/node/quantity/workstation/operator/recordedBy，数量按 BigDecimal 业务数值比较。同请求不同参数或不同用户拒绝。无需第二个请求摘要字段或单独幂等事实表。事务内在执行锁之后重查，防同执行并发重复；数据库唯一索引作为跨执行/跨节点请求键碰撞的最终保护。碰撞回滚并返回409。

成功重放返回第一次的不可变 Ledger（即使后来资格或状态变化也不再新增事实）；HTTP 当前权限/Scope 仍每次校验。前端成功后另查最新数量，既保证 Ledger 重放稳定，也不把“刷新失败”误报成“写入失败”。管理员补录/ADJUSTMENT 的 request_id 为空，不共享正式命令身份。

人工改动的原生成 Entity、Mapper 和 XML 已按 AGENTS 移除各自 MES-CODEGEN 标记；未改 Example 的生成归属，不恢复标记绕过未来生成冲突。

## D. Execution 状态

新命令在数量和资格校验后调用既有 `ExecutionPathService.start`，与追加REPORT处于同一事务：

- READY：要求工单 RELEASED、确切适用工艺、路径 LOCKED、正式领料已确认且无待核实提交/作废，首次成功后 RUNNING/开始时间。
- RUNNING：复用同一正式开工准入继续报工；不重复设置开始时间。
- COMPLETED / CANCELLED / 其他状态：新报工拒绝。
- 任意写入失败：开工状态与REPORT/请求键全部回滚。
- 报满不自动COMPLETED、零余额不自动关闭，也不入库。

真实 Execution101 的首节点最终报满10，仍为 RUNNING、end_time=NULL；不把单工序报满当整单完工。

## E. 独立页面与交互

新增 `mes-base-ui/src/views/production/productionReporting/index.vue`、`src/api/production/productionReporting.ts`、`src/menus/production/productionReporting.json` 和 `tests/productionReporting.test.ts`。

页面：扫码枪键盘输入或手输EXE身份码并回车 → 身份/工单/产出物料/目标/确切工艺 → 只读实际路径 → 可报工序及剩余量 → 输入数量 → 确认 → Ledger结果与数量刷新。扫码不自动报工。复用 PageHeader、Element Plus、RoutingGraphPreview、统一 request/auth；工人无需 V1 管理权限或平台目录权限。

默认显示 AVAILABLE 节点，开关可只读查看其余节点及原因；下拉中的无资格节点禁用。图使用原节点、真实边及 selectedNodeIds，本次实际路径高亮，其余同版本定义节点明确标为参考。UNSELECTED不展示伪造实际路径。

提交中禁止重复点击；未知网络/5xx结果保留原命令和UUID，锁定字段并提供“安全重试原报工”；明确4xx拒绝后允许修正输入。新业务动作生成新UUID。切Execution/Factory、撤权、卸载清理数据并失效请求序号，旧响应不能填回。请求不持久化到浏览器存储，刷新或切换上下文会按约定清理；不自动重放旧业务，未知结果应在离开页面前用安全重试核实。

真实窄屏发现公共页头因长账号名撑至547px；仅在 `src/styles.css` 增补≤600px的控件收缩/账号省略规则，修正为390px整页宽度。局部报工输入/选择/按钮采用44px触屏高度。表格/图允许内部横向滚动，页面没有整体横向溢出。

## F. 边界与并发

- 后端从 SecurityContext 获取 currentUserId，服务端生成时间、REPORT类型、正数数量和正式报工原因。工作站有值时校验真实引用。
- QuantityService校验实际路径/LOCKED/节点归属/正余额/上下游不倒挂；正式开工复用原工单、工艺与领料前提；人员资格重查现有服务。
- 查询资格按本批操作/节点IDs一次IN读取能力、启用工序和派工，只查询一次当前用户的工厂业务资格，不新建任何引用缓存，也不散落人员资格长期缓存。
- 同请求、不同请求、正式报工和管理员订正均受同执行事务锁；不按缓存或前端 AVAILABLE 直接允许写入。数据库异常不吞掉，不以缓存伪造成功。

## G. 自动化命令与结果

- `mvn -B -ntp verify`：最终日志 `/tmp/report-v2-verify-final.log`，562项，失败0、错误0、跳过26；跳过的是显式门控项目，不能称为562项均执行。新增 Reporting H2 HTTP8项，OperatorAssignment HTTP2项；中心身份目录在这组Fixture中Mock，业务Mapper/XML/事务/资格规则真实。
- 最后类型整理后的定向重跑：`mvn -B -ntp -pl mes-admin -am -Dtest=ProductionReportingHttpTest,OperatorAssignmentHttpTest -Dsurefire.failIfNoSpecifiedTests=false test`，10项通过，`/tmp/report-v2-http-final.log`。
- `npm run test -- --run`：52文件287项通过，其中报工页面12项，`/tmp/report-v2-ui-final-tests.log`。
- `npm run build`：成功，`/tmp/report-v2-ui-final-build.log`；保留既有大chunk提示，不升级构建依赖。
- `mvn -B -ntp -DskipTests package` 成功；`/usr/bin/python3 scripts/smoke-packaged.py` 10项通过，`/tmp/report-v2-{package,smoke-final}.log`。临时测试进程不代表重启或部署用户服务。

HTTP Fixture覆盖：扫码格式/无身份/无权限/缺Factory、同ID两库隔离、能力与派工及撤销、锁路/选中分叉/未选与其他版本节点、0/负数/精度/超量、上下游/连续报工/订正后余额、同请求并发重放、不同payload拒绝、正式报工与管理员订正并发、写失败回滚开工和幂等键、READY/RUNNING/COMPLETED/CANCELLED/DRAFT工单、报满不自动完工、其他Execution无事实。前端覆盖扫码只读、重复点击、同ID网络重试、新动作新ID、成功后刷新失败仍保留成功、拒绝可修正、迟到响应和切厂/撤权/切执行。

## H. 真实 MySQL / API / UI

目标经过中心绑定复核：SIMPLE_B ENABLED → datasource8 ENABLED → 192.168.18.25:3306/mes_base_dev_business_simple_b；中心库 mes_base_dev。先保存原流水/Schema及菜单预检，再通过 Flyway迁移；`migration.log`、`schema-after.tsv` 可核对版本和唯一索引，未手工执行后绕过迁移记录。

仅通过正式业务API创建新 TEST 样本：工单91 `TEST_REPORT_V2_7c00d681`、Execution101、实际节点1024→1025→1026→1028（A-B-C-E）、LOCAL_ONLY领料76/明细一行；未修改Routing定义及已有业务单。新 TEST工人6241、6242具备Operation1能力，6243故意不具备。TEST角色145只授予菜单2512及正式报工2612，不含补录/订正/冲销；既有管理员POST新入口403，证明没有超级管理员准入绕过。

真实验收：

1. 26项HTTP检查（含多次查询）：匿名401、管理员未授此权限403、普通工人无法管理员订正403、无能力422、错误分支/超量409、派工不含当前人422、能力撤销后422、READY→RUNNING、稳定Ledger重放及不同用户/不同数量拒绝。
2. 两名真实工人各并发报5，余额7时只有一笔成功、另一笔409；原3+成功5=8。管理员追加-1订正后，正式报工1基于最新有效量，累计回到8。
3. 另行并发两次相同已成功requestId，均返回流水24；数据库该requestId只有一行。
4. 工人浏览器扫码文本+Enter定位，真实点击两次各报1，生成流水28/29；首节点最终10、剩余0，下游输入10，Execution仍RUNNING。没有第二次自动提交，也没有生成入库。
5. 实际切到无权限工厂，旧Execution/输入消失；实际仅撤销TEST角色145的submit权限，接口403，刷新权限后菜单/旧数量数据清理；恢复为原最小菜单集合。未撤改现有用户角色。
6. 1280/1440/1920/390截图；1440图5个定义节点/5条边，仅4节点/3边高亮；390整页scrollWidth=innerWidth=390。使用扫码枪键盘协议等价的文本+回车，未插入实体扫码枪或调用摄像头。

最终本执行数量事实（全部保留）：REPORT24 +3、REPORT25 +5、ADJUSTMENT26 -1（引用24）、REPORT27/28/29各+1，合计10。正式REPORT的 operator_user_id=recorded_by=6241，请求UUID各唯一；ADJUSTMENT26的提交人为管理员100且request_id=NULL。原事实24仍是+3。两条禁止UPDATE/DELETE的数据库触发器仍存在。

证据根 `.local/reporting-v2-20261016/`：

- `center-before.tsv`、`business-before.tsv`、`migration.log`、`schema-after.tsv`；
- `fixture-before.json`、`fixture.json`、`routing-reference.json`；
- `test-permissions.tsv`、`role-before-revoke.json`；私有测试凭证仅保存在已忽略的.local，本文不输出内容；
- `live-api-evidence.json`、`concurrent-replay.json`、`mysql-after-api.tsv`、`mysql-final.tsv`、`verification.json`；
- `ui-success-1280.png`、`ui-success-final-1440.png`、`ui-1440-path.png`、`ui-1920-path.png`、`ui-390.png`、`ui-factory-cleared.png`、`ui-permission-revoked.png`。

未知网络/5xx及迟到响应是前端自动化的受控Promise/错误模拟，服务器幂等重放和MySQL并发为真实；没有人为断网、真实丢包、节点故障、负载压测或实体扫码枪证据，不将它们写作已通过。

## I. 文件、并行保护与 Git

本轮后端文件：

- 新增 `ProductionReportingController.java`、`ProductionReportSubmitService.java`、`ProductionReportSubmitModels.java`、`ProductionReportingHttpTest.java`、业务V27和中心V30。
- 原 `MesProductionReportEntity.java` / `MesProductionReportMapper.java` / XML：请求字段、参数化请求查找及原持久化写入适配；原 `ReportOperatorEligibilityService.java` / `OperatorQualificationRules.java`：批量查询提示，保留正式requireEligible；`ExecutionActionController.java`：纯VO转换复用。
- 共用、已脏的 `MaterialIssueHttpTest.java` 仅增补V27及H2方言适配，不覆盖其中其他Agent的V26/质量变更。
- 前端新增 API/页面/菜单/12项测试；`src/styles.css`只补窄屏页头响应式规则。

后端起止HEAD `4679154eccb61444f1c0f795c9620a54ec6e207d`，工作区在任务开始即存在质量/领料/仓储相关未提交代码和文档。`ExecutionPathService`、`ProductionExecutionRules`、Quality目录、公共ErrorCode及其MySQL/HTTP测试的并行变更未由本轮改写；本轮调用现有正式start，不修改其规则。Source Product未写入。

前端起始HEAD `732d669e41c11e13c5bfcdb1a4ba3e67076e942e`；期间外部操作提交为 `02a6554baff657f6c8dfe42cebdcbc9e5f863473`，包含本轮部分已落盘文件。Agent没有git add/commit/push；未回退外部提交，最终仍有本轮页面/样式未提交。完整状态在 `backend-status-final.txt` / `frontend-status-final.txt`，不是干净工作区交付，不把其他Agent diff 归入本轮。无已知文件覆盖冲突，后续合并需保留并行质量生命周期防护。

## J. 后续边界

正式入口现已接通“身份→实际路径→人员资格→数量→REPORT→必要开工状态”，不是V1管理员界面的复制。Quality/ProductionDefect由并行任务维护，本轮不申报不良、不扣减质量、不改qualityIntegrated=false预留规则。MaterialIssue只复用既有成功确认与正式开工契约，不实现ERP出库；Receipt、自动完工/入库、绩效工时、APS、第二套人员或数量模型均未开发。

当前交付为授权隔离开发环境的最小正式报工V2 GO。生产启用前由使用方为实际工人配置独立submit权限、对应工序能力和派工；本轮不扩大既有生产角色授权，不宣称已部署到真实生产环境。
