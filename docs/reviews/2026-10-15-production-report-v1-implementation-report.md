# ProductionReport V1 实施与验收报告

日期：2026-10-15。范围：不可变数量事实、管理员历史补录/订正/冲销、实际路径数量聚合与工单内管理入口。未提交、推送、部署或重启用户服务。

## A. 当前契约

- `WorkOrder → MesProductionExecution → MesExecutionPath/Node → MesProductionReport`。Execution 是唯一执行身份，报工不引用旧 ProductionBatch/BatchRouteNode。WorkOrder 默认工艺与 Execution 实际固定版本保持原契约。
- `ProductionQuantityService` 读取 `ExecutionPathService.view`，通过既有 `ExecutionPathValidator` 验证确切版本、节点和实际线性路径；不重选路、不用最新版本或 display_order 推断路径。UNSELECTED 可读取边界，SELECTED 可读数量；写入要求 LOCKED，防止已有事实后路径被重新选择。
- 人员为中心库 `sys_user`。既有正式报工资格签名是 `ReportOperatorEligibilityService.requireEligible(Long executionId, Long routingNodeId, Long userId)`。本轮历史补录按要求仅验证历史用户引用存在，允许当前已停用/不再具有工序能力的历史人员，不调用当前派工资格校验。
- `ReportUserReferenceService` 普通用户查询构造器注入 SysUserMapper，master 只读事务；已有业务事务内引用校验仅在实际跨库事务存在时使用既有受控独立只读窗口。
- 业务库不保存 factory_id；HTTP 当前工厂授权、动态业务库路由和事务租约沿用基础设施。业务写入可信提交人来自 SecurityUtils，不信任请求 userId/factoryId/recordedBy。
- 只读取 Operation、Routing、RoutingNode、RoutingEdge 稳定模型，未修改其核心模型。没有修改 Source Product。

## B. Schema

正式迁移：[V25__production_report_ledger.sql](../../mes-admin/src/main/resources/db/business/V25__production_report_ledger.sql)。已由 Flyway 从 V24 迁至 V25，25 项迁移校验通过。目标经中心配置核实：SIMPLE_B → 数据源8 → 192.168.18.25:3306/mes_base_dev_business_simple_b；没有换库或另建验收库。

| 字段 | 含义 |
|---|---|
| id | 自增流水主键 |
| execution_id / routing_node_id | 真实执行身份与锁定路径节点 |
| record_type | REPORT / ADJUSTMENT |
| quantity_delta | DECIMAL(20,6)，本次数量变化 |
| reference_report_id | 调整指向原始 REPORT |
| operator_user_id / recorded_by | 实际生产用户 / 可信提交用户 |
| report_time / create_time | 实际生产时间 / 服务端创建时间 |
| workstation_id | 可选当前库工作站引用 |
| reason | 必填操作原因，最多500字符 |

所有字段及表均有非空中文 COMMENT。普通索引：execution+node、reference、operator+time、create_time；没有新增 UNIQUE 或外键。CHECK 约束流水类型、数量正负、引用及非空原因；UPDATE/DELETE trigger 从数据库层阻止改写事实。生成器基于实际 metadata 生成 Entity/Example/Mapper/XML；遵循用户要求仅复用基础持久化，不发布通用 CRUD 写入口。

生成配置：`docs/development/production-report-codegen.yaml`。通用批量生成审查配置排除该表，防止后续意外生成写入口。迁移、生成及 Schema 证据：`.local/production-report-20261015/{history-before.tsv,schema-verification.tsv,codegen-report.json}`。

## C. 不可变流水

- `ProductionReportService` 只追加。REPORT 必须正数且无原始引用；ADJUSTMENT 可正可负、不可为0，且只能引用 REPORT。
- 调整继承原执行、节点、实际操作人、生产时间和工作站；提交人、原因、创建时间记录本次操作。
- 原始有效余额 = 原始数量 + 所有引用调整的 SUM，不得小于0。整笔冲销追加当前有效余额的相反数；已归零再次冲销返回409，不重复追加。
- 每次补录/订正/冲销均执行完整路径数量校验。数据库与服务双层禁止 UPDATE/DELETE；HTTP 没有这些动作，返回405。
- 任何报工事实（包括净值已归零）阻止删除 Execution；降低执行目标也受已有数量事实约束。不自动改变执行完工状态。

## D. ProductionQuantityService

- 节点 reportedQty = SUM(quantity_delta)，通过数据库按 execution_id+routing_node_id 聚合。
- 首节点 inputQty = Execution.targetQty；后续节点 inputQty = 实际前驱 effectiveOutputQty。
- availableQty = inputQty - reportedQty；当前 effectiveOutputQty = reportedQty。
- tentative delta 先作用于目标节点，再验证整个实际路径：所有节点累计非负、不超过输入，禁止上游负调整造成下游倒挂。未选分支不计入必经节点。
- 所有写命令采用 businessTransactionManager + READ_COMMITTED，复用 `ProductionChainLocks.execution` 的 WorkOrder→Execution 锁顺序；取得锁后重新查询并校验聚合，因此同一执行并发写不会超量。不是 JVM 锁。
- `ProductionQuantityMapper` 仅承载跨表投影；单表追加/读取复用生成 Mapper。工单执行数量摘要分页查询，末节点数量独立表达，不把不同节点产量相加当作工单产出。没有逐行人员/节点查询造成 N+1。

## E. 质检预留

HTTP 白名单模型 `ProductionReportViews.Node`：executionId、routingNodeId、operationId、code、name、sequenceNo、inputQty、reportedQty、defectQty、effectiveOutputQty、availableQty、qualityIntegrated。ID/decimal 均为字符串。

当前 defectQty=0、qualityIntegrated=false；页面显示“未接入”，并明确提示“暂未接入质量扣减，当前可报数量按报工数据计算。”不宣称真实零不良或质检合格。未来按 Execution+RoutingNode 接入质量事实后，在数量聚合处提供 defectQty 和 effectiveOutputQty；未来质量命令也必须沿用同一执行锁及下游约束。本轮没有质量模块运行依赖。

## F. 前端管理

入口：生产工单 → 详情/执行 → 数量/报工。复用既有 ProductionExecutionList，不增加独立报工菜单。

`ProductionReportManager.vue` 提供实际路径节点数量、分页流水、原始有效余额及调整历史、历史人员昵称检索、管理员补录、正负订正和整笔冲销。原因必填，用户ID/decimal保持字符串；无普通编辑/删除。切厂/撤权/关闭失效请求序列，拒绝迟到响应；未提交表单关闭先确认。

五项权限：business:production:report:{quantity,detail,supplement,adjust,void}，中心菜单2600–2604挂在工单2491下。用户授权“你来配置权限”后核实 admin100/平台角色100已全部具备，真实 API 和页面按钮生效，因此没有重复授予或扩展其他账号权限。

真实截图发现并修正：命令窗口节点名为空时显示“工序名称未记录”；路径表当前节点高亮并限制高度；输入内部去除重复焦点轮廓，保留组件外框焦点提示。已检查1440、1280、1920及390宽度，窄屏使用表内滚动。

## G. 验收证据

### 自动化与构建

| 命令 / 层级 | 结果 |
|---|---|
| `mvn -B -ntp verify`（含列表收敛） | 总518项，499执行通过、19按环境跳过，0失败/错误；2026-10-15 14:06完成 |
| 报工 HTTP 路由/H2 | ProductionReportHttpTest 5项通过，真实生成SQL、事务、权限、两厂同ID隔离、并发与路径分叉 |
| 中心引用单测 | ReportUserReferenceServiceTest 2项通过，普通注入与实际跨库窗口分支 |
| 列表异常隔离 | WorkOrderListResilienceHttpTest 2项通过，正常/缺扩展同页、分页、详情仍拒绝、另一厂不受影响、SQL故障继续抛出 |
| `MES_REPORT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionReportMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | 真实指定MySQL 2项通过，回滚/trigger/CHECK/并发 |
| 同命令指定 `ProductionReportMySqlTest#realMysqlRollbackConstraintsAndImmutableLedger` | 夹具扩展记录修复后1项再次通过，事务回滚无新增永久数据 |
| `npm run test`（Node22.23.1） | 47文件250项全通过，含报工7项、列表异常标记1项 |
| `npm run build` | 通过；保留既有大包警告 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 最新JAR 10通过/0失败；隔离临时进程，不重启用户服务 |
| `scripts/format-java` / `git diff --check` | 定向格式化完成；空白检查通过 |

日志分别位于 `/tmp/mes-report-resilience-{verify,smoke}.log`、`/tmp/mes-report-ui-resilience-{tests,build}.log`、`/tmp/mes-report-mysql.log`、`/tmp/mes-report-mysql-fixture-fix.log`。

MySQL 测试通过真实中心绑定选择并固定指定数据源，验证真实 SQL/事务；该测试上下文不冒充生产动态路由。两厂路由隔离由 routed HTTP fixture 提供，真实运行服务则验证 SIMPLE_B。

### 真实登录 API 与浏览器

真实服务8081、前端5173，用户自行重启后继续验收：

- API 16项通过：数量查询、工单摘要、拒绝伪造提交人字段400、两节点补录、上游倒挂409、正调整、禁止引用调整422、PUT/DELETE405、上下游依序冲销、重复冲销409、详情余额和历史、最终数量归零。
- 结束时另6项通过：匿名401、无效厂422、数量全0、UI原始流水未变、调整历史2条、工单列表200。证据：`.local/production-report-20261015/live-api-evidence.json`、`final-api-evidence.json`，均不含令牌或密码。
- 浏览器真实完成：REPORT11=1.000001、ADJUSTMENT12=-0.250000、ADJUSTMENT13=-0.750001，原始流水11不变，有效余额0，实际人员6087、可信提交人100。历史窗口显示两条调整。所有节点最终报工数量0，执行仍待执行。
- 复验入口：工厂 SIMPLE_B，工单 `TEST_REPORT_1574c94b-0a5`（77），Execution90，固定路径A→B→C→E。保留带TEST标记的审计事实，未删除不可变流水。
- 图片：`.local/production-report-20261015/03-ui-adjustment-history.jpg`、`04-final-1440.jpg`、`05-final-1280.jpg`、`06-final-1920.jpg`、`07-final-390.jpg`。早期服务未重启截图不作为最终阻塞；重启后已完成报工验收。

### 验收夹具问题及本轮追加收敛

并发测试最初创建的自有TEST工单77漏建必需的空 workorder_erp_extra，导致原列表hydrate抛409。已备份并补齐仅该工单的空扩展记录，同时修复 MySQL 测试夹具。未伪造任何外部ID、供应商或ERP成功状态。

用户进一步要求隔离单行异常：`WorkorderErpFacts.hydrateAvailable` 返回缺失ID，列表不删行、不改总数，SummaryView显式输出 erpExtraMissing，前端对应行显示“扩展记录缺失”。详情/写操作继续严格读取，不自动修复缺失数据，不捕获SQL/鉴权/连接故障伪造正常列表。既有hydrate保持严格语义，没有遗留一套被废弃的替代入口。

用户再次重启后，已在真实服务/API/UI完成追加验收：SIMPLE_B绑定复核仍为数据源8/192.168.18.25:3306/mes_base_dev_business_simple_b。冲突预检为空并保存备份后，创建自有测试工单80（TEST_LIST_RESILIENCE_20261015_NORMAL，正常空扩展）及81（TEST_LIST_RESILIENCE_20261015_MISSING，临时缺扩展）。列表200并同时返回两条，标志分别false/true，总数2；分页offset=1/limit=1仍total=2；异常单详情409、正常单详情200。浏览器同页显示两条，异常行明确提示“扩展记录缺失”，证据09-list-resilience-mixed-rows.jpg。随后恢复81的空扩展，API两条标志均false、81详情200；浏览器刷新警告消失，证据10-list-resilience-restored.jpg。保留两条TEST ONLY草稿供复验，未生成执行或数量事实。没有修改其他业务工单。

追加证据：`.local/production-report-20261015/list-resilience-{before.tsv,fixture.sql,live-api.json,restore.sql}`。未捕获任何请求凭证。该修复现已完成真实验收。

## H. 风险和待完成事项

- 当前无验收阻塞。两次需要加载新后端代码均由用户自行重启；Agent未启动、停止或重启用户服务。
- 质量、车间扫码正式报工、返工、生产入库、ERP、自动完工和DAG汇合算法均属未来范围。历史补录不是正式车间报工资格流程。
- 网络中断导致提交结果未知时需查询流水核对；本轮没有新增通用幂等引擎。并发数量上限已验证不等于业务重复提交自动去重。
- 历史路线名称未记录时明确显示缺失，不使用最新工序名称假冒历史快照。
- 本次正式迁移只验证指定 SIMPLE_B；其他工厂仍需通过正式迁移机制升级。不宣称全环境部署完成。
- backend初始HEAD edad2c74fe1bd31eec692760d656d8e27ea98b92，任务间外部变为8f78b55d62144798706cce518db05eca3a343e42；frontend初始732d669e41c11e13c5bfcdb1a4ba3e67076e942e。保留已有dirty工作，Agent未提交。
- 回滚代码可逐项撤回本任务文件/改动；已执行V25及不可变事实不得改写历史迁移或清表，后续Schema变更使用新迁移。列表收敛不改数据库结构。

## I. 最终状态

以下状态针对用户原 ProductionReport V1 范围、指定 SIMPLE_B 本地验收环境，均有独立证据：

| 状态 | 结论 |
|---|---|
| SCHEMA_READY | PASS：V25已正式执行、Schema/trigger/CHECK核实 |
| API_VERIFIED | PASS：真实登录与16+6项正负例 |
| UI_VERIFIED | PASS：真实补录/负订正/冲销/历史及四档宽度 |
| BUSINESS_VERIFIED | PASS：MySQL并发/回滚、路径与数量边界、不可变审计事实保留 |

追加“单条工单扩展异常不阻断列表”修复：CODE/TEST/BUILD/LIVE_VERIFIED。异常与正常行同页、分页及恢复后的真实API/UI均已核实。

## J. 2026-10-15 页面收敛、路线恢复与统一引用目录追加验收

本节是后续任务的独立回执，不将上方原 V1 的 GO 自动沿用到新范围。范围为页面结构、只读路线展示、引用名称/缓存；数量算法、流水写入规则及 V25 Schema 未改变。

### A. 修改文件与范围

- 工单倒序：`WorkOrderService.list` 原有 `id desc` 保留，由 `WorkOrderListResilienceHttpTest.defaultDescendingIdsApplyBeforePaginationAndAfterFilters` 补充数据库分页回归。没有新增客户端排序。
- 数量页面：前端 `views/production/productionReport/index.vue`、`components/business/production/ProductionReportManager.vue`、`ExecutionSelect.vue`、`api/production/productionReport.ts`、`menus/production/productionReport.json`；工单页改用 `WorkOrderExecutions.vue` 摘要和跳转，移除完整数量/派工/路径命令嵌套。
- 路线展示：`WorkOrderRoutePreview.vue`、`ExecutionRoutePreview.vue` 复用 `RoutingGraphPreview.vue` / `routingGraph.ts`；后端 `ExecutionPathService`、`ExecutionActionController` 补充执行编号、确切版本和工序显示字段。`WorkOrderService.graph` 仅补充名称投影。
- 引用目录：后端 `BusinessDirectoryService` / `BusinessDirectoryController`、`ReferenceLabelCache`、`RedisCache`、`BusinessCacheProcessor`、`UserReferenceCacheInvalidation`、`SysUserMapper` / XML；生产执行 `ProductionExecutionQueryService` / Controller / VO，报工读模型/VO、数量读模型、工艺草稿详情统一复用目录。`RoutingServiceImpl` 仅替换草稿工序展示信息补齐，不修改工艺模型、发布快照、图规则或引用校验。
- 交互与导航：`ProductionExecutionList.vue`、`mesProductionExecution/fields.ts`、`CrudPage.vue` 的操作列宽配置；`api/business/directory.ts` / `BusinessDirectorySelect.vue`；`router.ts` 修复动态菜单首次匹配丢 query/hash。订正历史复用既有时间格式化。
- 约束：后端及前端 `AGENTS.md` 均追加“统一业务目录与引用缓存”，明确统一入口、宽投影、批量未命中查询、提交后失效及与生成 CRUD 淘汰关联的要求。
- 关联测试：BusinessDirectoryTest、ReferenceLabelCacheTest、UserReferenceCacheInvalidationTest、BusinessCacheIntegrationTest、ReportUserReferenceServiceTest、ExecutionRouteViewHttpTest、WorkOrderListResilienceHttpTest，前端 businessDirectory、dynamicRouteBootstrap、productionPages、productionExecution、productionReport 等；相应 Spring/MySQL fixture 装配同步适配。

### B. 工单 SQL 与分页

`WorkOrderService.list` 在 `PageHelper.offsetPage` 前设置 `WorkorderExample.orderByClause = "id desc"`。生成 Mapper/XML 的执行形态为 `SELECT ... FROM workorder WHERE ... ORDER BY id DESC LIMIT ...`，筛选参数仍绑定，不取一页后再排序。当前查询契约没有合法显式排序参数，因此不存在覆盖显式用户排序；未来引入时须采用允许列表并补唯一 ID 次序。

H2 真实 Mapper/XML/分页测试按 10、12、11 的插入顺序造数，验证首两条为 12、11，下一页为 10，DRAFT / RELEASED / 编码查询后仍保持倒序。真实 SIMPLE_B API 已校验默认及筛选三页无重复 ID，证据见 `live-api-read-evidence.json`。该验证针对静态数据集，不承诺并发插入时 offset 分页的跨请求快照一致性。

### C. 独立菜单、路由、权限与行为

正式路由 `/production/productionReport`；复用原菜单 ID 2600，将既有 quantity 按钮提升为正式菜单，父目录 2512，按钮 2601–2604 跟随其下。仍复用 `business:production:report:{quantity,detail,supplement,adjust,void}`，没有增加第二套权限。中心 SQL 按现有正式菜单脚本执行；变更前菜单备份及 `role-menu-before.tsv` / `role-menu-after.tsv` 一致，未扩大角色授权。生产执行 2579 恢复独立入口，派工兼容菜单隐藏。

可直接按工单查 Execution 摘要，也可按 Execution 查询实际节点、目标数量、上游输入、累计报工、剩余可报、质量未接入提示及流水。补录/正负订正/整笔冲销/历史沿用原组件/API。成功后刷新数量和摘要；切工单/Execution、切厂、撤权、关闭窗口、卸载均清理或隔离请求序号。浏览器发现并修复动态路由重匹配时把 fullPath 塞入 path 而丢 query/hash 的问题，现显式保留 path/query/hash；真实刷新直达参数及新增回归测试通过。

### D. 路线图消失原因与恢复

Git 历史确认前端 `ec3181bb76dbf30a151f2b73d6325a15efa3b8a2` 在清理旧批次/路径交互并嵌入 Execution 时，同时移除了工单默认路线图的组件及加载链路。更早的 `33ffe78`、`205f40b` 中已有图形展示。不是当前 Routing DAG 图形组件不可用。

恢复既有只读 DAG 组件：工单默认图取该工单默认 routing；Execution 图取 `ExecutionPathService.view` 返回的确切 routing 版本、真实边与 selectedNodeIds，不能用显示排序串节点。UNSELECTED 提示“尚未选择实际执行路径”，完整定义图明确标识仅作参考；SELECTED/LOCKED 显示所选节点及边。历史快照优先，缺快照时使用目录当前名称且标注“当前名称”。没有重建 BatchRouteNode / BatchPathPolicy，也没有修改 Operation/Routing 核心表。

真实 Execution 15=UNSELECTED/routing64，16=SELECTED/routing65，90=LOCKED/routing64；已验证不同 routing 的节点集合不混用。真实页面观察分叉/汇合与所选 A→B→C→E。V2 与线性路径、错版本响应拒绝由 HTTP/H2 和前端 fixture 覆盖；未声称创建了真实 V2 Execution。

### E. 最终页面职责

WorkOrder：列表、创建编辑、详情下达、默认工艺版本与图、Execution 摘要和跳转。ProductionExecution：执行单元维护、派工、路径选择和开工；列表操作收纳为编辑/删除/更多操作，更多中保留只读路线、数量跳转、派工、身份码、路径与开工。ProductionReport：独立的节点数量、流水和管理员订正操作。

### F. 验证与证据

- `mvn -B -ntp verify`：526 项，0 failure / 0 error，19 项环境条件测试跳过；其中 507 项实际执行。日志 `backend-verify.log`。
- 前端 `npm run test -- --run`：49 文件、261 项通过；`npm run build` 通过，保留既有大 chunk 提示。日志 `frontend-tests.log` / `frontend-build.log`。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10 通过、0 失败，独立测试进程不等于重启用户服务。
- 真实登录只读 API：11 个端点结果及 11 项字段/状态断言，`live-directory-read-evidence.json` / `live-directory-assertions.json`。包括去重用户/工序目录、白名单、工艺名称版本、三种路径状态、节点不跨 routing、原始流水与调整历史姓名、数量归零及质量占位。
- 用户重启后的真实服务已返回 zhanglixin / 平台管理员、工艺名称和版本；执行列表按名称/编码检索工艺并过滤至指定 routing64（9 行），更多操作与未选/已选路径已实际打开。期间服务 PID 变化导致一次超时，后续真实重试成功；没有把失败截图算通过。
- 独立页面真实操作：原始 17=1.000001；正订正18=+0.500000，负订正19=-0.250000，整笔冲销20=-1.250001。数据库只读复核原始记录保留、有效数量归零，API详情 originalEffectiveQty=0，历史3条，页面姓名正确。没有删除验收流水。
- 数据源再次核对：SIMPLE_B / datasource8 / 192.168.18.25:3306/mes_base_dev_business_simple_b，工厂与数据源均启用。全部报工写验收在此库；切厂仅测试既有工厂选择和页面清理，未在其他工厂操作生产数据。
- 真实 UI：1280、1440、1920，数量页/历史弹窗/执行操作列；视口 pageWidth 与 width 一致，宽表内部滚动。截图 `02`–`11`（`01` 为验收早期异常，非通过证据）。真实 SIMPLE_B→老厂→SIMPLE_B 验证旧执行行清空并返回工作台，再恢复指定工厂。
- 迟到响应、切 Execution 和撤权 UI 清理/只读权限负例由自动化 fixture 覆盖；本次未修改真实账号角色来制造撤权，也未用真实只读账号重跑整套订正拒绝场景，此项不能宣称真实验收通过。

证据均位于忽略目录 `.local/production-pages-20261015/`；不包含登录密码/Token。

### G. 并行改动及未验证边界

开始记录两仓 status/HEAD，后端 8f78b55d62144798706cce518db05eca3a343e42，前端 732d669e41c11e13c5bfcdb1a4ba3e67076e942e。期间外部将后端 HEAD 更新为 758145c7e8a10805bb09e49cdc392f65b8e8eb3f；Agent 没有提交/推送，未回退并行代码。已有前端认证、CRUD、工序编辑、工艺编辑等 dirty 内容保留，只追加本任务改动。

数量/报工 Service 文件因名称投影和读模型追加字段发生变化，不能声称整文件 hash 不变；数量 SQL、计算及写命令规则未改。V25 hash 与入场一致。未改 Source Product、未部署、未自行启动/停止/重启用户服务。真实撤权/只读账号组合仍未完成；独立测试 Redis/H2 不冒充用户运行 Redis/数据库的写入更新验收。

### H. GO / NO-GO

代码、测试、构建、真实菜单/数量页面/名称接口/路径展示/三档宽度：GO。若按原任务 7.4 要求把真实撤权和真实只读账号操作负例全部作为整体验收门槛：仍为 NO-GO，保留上述明确未完成项；不把自动化 fixture 转写为真实账号验收。

## K. 2026-10-15 统一 BusinessDirectory 缓存与通用淘汰审查

### 缓存归属及散落点收敛

| 审查位置 | 处理结果 |
|---|---|
| 原 personnel / departments / posts 通用组件 | 沿用 BusinessDirectoryController → BusinessDirectoryService；人员单项回显复用安全投影缓存，启用/删除状态仍先实时校验 |
| 生产执行列表/详情的工艺和创建/订正人 | ProductionExecutionQueryService 收集整页 IDs，统一调用目录批量方法，无逐行数据库/Redis 请求 |
| 报工流水/详情/订正历史的操作人、提交人 | 共用 userLabels，读模型批量回填；历史人员检索迁入目录 Controller；旧 report-operators 兼容 API 复用同一 Service 查询 |
| 历史报工人员存在性校验 | BusinessDirectoryService.historicalUserExists 实时读取；ReportUserReferenceService 只保留报工错误语义与用例适配，不再持有中心 Mapper/缓存 |
| 工单默认图、Execution 实际图、数量读模型 | 共用 operationLabels/routingLabels；快照不被当前名称覆盖 |
| Routing 草稿详情补工序展示信息 | 改用 operationLabels；发布时生成快照、引用校验、同步编码映射仍实时执行，属于业务事实而非通用回显缓存 |
| 工艺筛选前端 | 共用 BusinessDirectorySelect → 目录 routings API；不再以裸 ID 作为唯一显示 |
| 操作人员资格、适用工艺、业务引用规则 | 保留各自领域查询与实时授权；不以通用缓存结果判断可派工/可开工/可报工 |
| 其他生成 CRUD 的完整实体/分页缓存 | 继续使用通用 BusinessCacheAspect/Processor，并非散落名称缓存；本轮不机械替换。物料/仓库组件的 rows 查找是当前组件候选状态，未建立跨页面共享名称缓存 |

没有保留第二个 ReferenceDataCache/业务模块用户名称共享缓存。ReferenceLabelCache 是目录内部的 Redis 批量存取设施，业务读取入口统一 BusinessDirectoryService；失效由通用基础设施处理。

### 宽投影、查询和事务

每个对象一份可复用投影：id、code、name、version、description、status、departmentId、deletionFlag。实际对象不适用字段为 null，不缓存用户密码、联系方式、角色、权限或资格。HTTP Controller 使用独立白名单响应及字符串 ID，不直接返回 SysUser Entity。

userLabels / operationLabels / routingLabels 去空、去重，最多200个引用；一次 MGET，未命中部分一次参数化 IN，再批量 pipeline SET，每条固定 TTL（默认300秒，读取不续期），缺失对象不做负缓存。空批次不查库。Redis 读失败退回一次批量数据库查询；数据库异常继续传播，不伪装空名称。业务对象按 Factory+资源隔离，缓存前后保留事务租约校验，写事务不发布未提交投影；普通中心查询使用注入 Mapper，仅真实其他库活动事务进入既有受保护只读窗口。

### Key 是否受生成 CRUD 淘汰规则管理

**是，通过同一个通用淘汰处理器联动；不依赖原来的 list/detail 通配删除直接匹配目录键。**

- 业务目录命名空间统一由 `BusinessCacheProcessor.directoryNamespace(resource)` 构造：`reference:business:factory:<base64Factory>:resource:<资源长度>:<资源>:directory`。目录读取和提交后淘汰复用同一命名方法，外部还会套现有 SystemCacheNamespace 环境前缀。
- 目录键为 `<namespace>:version` 与 `<namespace>:value:<generation>:<id>`。`@BusinessCacheEvict`（生成新增 LIST、修改/删除 LIST_AND_DETAIL）和手工同步 `caches.evict` 都进入 `evictCaptured`；捕获原 Factory 前缀，提交后同时推进目录代际和删除原 list/detail。回滚不推进。旧未完成 loader 只能写旧代际，不能重新成为有效命中。
- Operation 的新增/修改/删除注解及同步入口使用 OperationResource.PREFIX；Routing 所有 dirty/insertRow/update/delete/同步入口使用 RoutingResource.PREFIX。目录读取复用同一资源常量，无逐业务额外补淘汰调用，也不需要修改生成模板。
- 中心用户使用共享 `reference:users`；SysUserMapper 的统一新增/更新/删除写入口由 UserReferenceCacheInvalidation 在提交后推进代际，回滚不推进，不缓存账号授权。
- 失效 Redis 故障明确记录日志；旧值最多存活固定 TTL，未承诺跨节点即时强一致。版本键不是权限缓存。

### 验证证据

ReferenceLabelCacheTest：冷/部分未命中只加载一次去重 IDs、暖命中0次数据库、空集合、不同工厂隔离、旧 loader 失效后迟到回填、Redis故障与SQL故障边界。BusinessDirectoryTest：真实中心 Mapper/XML 的安全宽字段、历史禁用/删除人员、两次调用只执行一次 IN、白名单及权限拒绝。UserReferenceCacheInvalidationTest：实际 AOP 代理写入的提交后失效/回滚保留。

BusinessCacheIntegrationTest.directoryProjectionUsesRealRedisBatchAndCommitInvalidation：独立真实 Redis 测试进程及 Spring 事务下，验证宽投影真实序列化/MGET/pipeline、暖命中不查库、通用 CRUD 提交后重载、回滚保留。该类14项全部通过。此证据是本地独立 Redis 集成，不冒充对用户运行 Redis 手动改名后的验收。

两仓 AGENTS 已明确补充：新增目录缓存必须与生成 CRUD 通用淘汰同源，禁止出现“读取走新 key、写入只淘汰旧 key”，并要求新对象同时覆盖导入/同步入口。
