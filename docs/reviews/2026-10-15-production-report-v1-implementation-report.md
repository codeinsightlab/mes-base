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
