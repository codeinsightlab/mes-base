# 领料作废与 WorkOrder 产品一致性修复

2026-10-14。当前仅中心库与 Factory 业务库，未恢复旧 ERP Foundation 数据库装配。用户授权本轮实现、指定库迁移和验收；不提交、推送、部署或操作用户服务。

## 模型与实现

- 原 DRAFT/RECORDED 是后台本地资料状态，生产源码未调用 ERP 出库，不证明外部成功。V18 为领料单追加11个来源/作废字段：source_execution_mode、external_effect、external_binding_key、external_reference、void_operation_id、void_from_status、void_result_status、void_execution_mode、void_requested_at、void_finished_at、void_message。中文 COMMENT 完整，无新唯一约束/外键/工厂字段。历史默认为 UNVERIFIED/来源空；没有猜测或批量改为本地单。
- 当前新增 VOID_PENDING、VOID_UNKNOWN、VOIDED。普通 Command 仍只可 DRAFT/RECORDED，动作状态和来源字段仅 Service 写入。VOIDED 单头、明细不能恢复、转移、编辑或删除；已发起作废的拒绝结果也保留操作身份。未知/真实外部影响单据不能通过删除或转移引用绕过撤销。原有确认无外部影响的 LOCAL_ONLY 后台资料仍保留既有行政 CRUD 删除边界（有关联/明细时拒绝）。
- WorkOrder 更新先锁父单，检查独立 Execution/旧 Batch 保护，再按 generated Example 统计关联领料；只有来源与结果一致的确认作废不作为领料阻碍：LOCAL_ONLY 对应 NONE，CONNECTED 对应 REVERSED，均要求 VOIDED＋SUCCEEDED。VOID_PENDING/VOID_UNKNOWN、RECORDED、DRAFT、历史未知均阻止产品变化。报工/入库表不存在，未伪造检查。
- 创建/关联领料与作废阶段复用同一工单同值 UPDATE 行锁，事务隔离 READ_COMMITTED。不存在无约束“先查再插”或 JVM 锁。
- MaterialIssueVoidService：准备事务在已锁单据上认领 UUID 操作并保存原状态；事务外执行 WarehouseErpAdapter；落地事务验证相同操作和 pending/unknown 状态。重复 cancel 返回已有结果，不重复外部写。reconcile 仅查询同一身份用于 UNKNOWN 或进程崩溃后的 PENDING；未查到/仍处理中必须 UNKNOWN，不能当 REJECTED。
- SUCCEEDED→VOIDED，本地模式 external_effect=NONE，真实外部撤销确认才为 REVERSED；REJECTED→原有效状态，记录拒绝；UNKNOWN→VOID_UNKNOWN，继续阻止产品变更。异常保留链并按 UNKNOWN 落地。外部成功后本地落地失败不宣称失败或回滚外部，仍留下持久化 PENDING 供核实。

## Integration 与规范

WarehouseErpAdapter 仅提供 cancelMaterialIssue、queryMaterialIssueCancellation、按 scope/binding 的匹配能力；query 已有真实核实消费者。ErpActionResult 区分 SUCCEEDED/REJECTED/UNKNOWN、LOCAL_ONLY/CONNECTED、externalReference、诊断 message。无供应商实现、统一巨大 ErpAdapter、逐动作 Adapter、注册中心或任务引擎。

`mes.integration.warehouse.factories.<FactoryId>.mode` 必须显式配置；仅 git 忽略的 application-dev.yml 中 SIMPLE_B 明确 LOCAL_ONLY，默认/生产没有回退。本地 CRUD 只有在明确 LOCAL_ONLY 时记录 NONE；CONNECTED 或未配置的行政资料不伪造出库来源。未来实际出库消费者必须保存原连接/外部单据/实际结果。

单据原 CONNECTED 来源必须使用原 binding/reference 匹配当前 Factory 的实际 Adapter；即使当前配置变 LOCAL_ONLY，也不能本地跳过撤销。缺模式/缺唯一适配器明确失败。Adapter 无 MES Mapper/数据库/主事务依赖，DTO 不进入 Core。ProductionErpAdapter 本轮无消费者，仅在 ADR 明确未来边界，不生成空实现。

AGENTS.md 新增领域 Adapter、厂商逻辑隔离、显式本地、CONNECTED 关闭失败、三结果、事务边界、禁止 Adapter 写业务库、来源和幂等、禁止预建框架、Factory/领域隔离十项规则。正式决定见 ADR-007-warehouse-cancellation.md。

## Codegen 与文件

使用 mes-crud-codegen 从真实 SIMPLE_B V18 metadata 在 target/issue-void-generated 审查副本生成 persistence/backend/test（1表0拒绝），采用 Entity/Example/Mapper/XML四份持久化产物；保留当前手工 Service/Controller/命令并最小扩展，未开放来源字段的基础生成命令。XML 清理生成器尾随空白时移除生成标记，遵守手工保护规则，未改通用生成器。

后端改动：WorkOrderService、MaterialIssueRules、两份领料 CRUD Service、MaterialIssue Controller/VO/ErrorCode；新增仓储契约/执行选择、作废 Service/Controller、V18、测试。前端复用 MasterDetailDocument，增加显式 headerReadonly/业务动作槽/刷新能力；业务作废按钮按独立权限显示，已作废及处理中资料和明细只读，迟到响应/切厂/撤权清理。没有重写领料主子页面或 ERP 流程。

独立权限 `business:mes:material:issue:void`；菜单 SQL docs/generated/material-issue-void-menu.sql 只生成，保留菜单/角色，不自动授予角色。接口：POST `/api/issue/mes-material-issue/{id}/actions/void`、`/{id}/actions/reconcile-void`。查询现有 CRUD 返回来源/操作结果字段，外部身份仅不含秘密的 opaque key。

## 已执行证据

1. 入场检查两仓库 HEAD/status 并备份目标；没有覆盖前端此前 WorkOrder/Execution dirty 工作。实际绑定核对 SIMPLE_B→datasource8→192.168.18.25:3306/mes_base_dev_business_simple_b；备份领料、明细、工单到 `.local/material-issue-void-20261014/*-before.tsv`。
2. Flyway V18 真实执行并验证18个迁移通过。历史领料来源保持未知；没有改 V11/V12/V13～V17。
3. `MES_VOID_MYSQL_ACCEPTANCE=true MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true`：404 tests、0 failure/error、10 skipped，13:14:17 BUILD SUCCESS。包括新的真库多事务作废、产品订正、并发创建检查；真实 MySQL 并非 ERP 联调。
4. `npm test`：37 files/194 tests passed；Node22.23.1 `npm run build` 通过。前期 Test mock 的 ElementPlus 返回类型已修正；没有用测试存在代替执行结果。
5. 两库 HTTP Fixture 验证 void 独立权限、普通状态绕过拒绝、合法作废解除产品限制、保留历史原产品、同操作重复请求、跨厂、三种外部结果与查询核实、Adapter 不在事务内、并发单一认领/工单修改竞争。合成 Adapter 仅 src/test/TestConfig，不进入生产组件扫描。
6. MySQL retained TEST ONLY 数据：TEST_VOID_92b47097（工单20、issue22、target product69/routing104），TEST_VOID_b669c1de（工单22、issue24、target product70/routing105），以及各自 RACE 工单/可能领料。测试目标产品/版本是带明确标签的数据库规则 Fixture，克隆已存在版本用于产品订正校验，不宣称真实 Routing 发布验收；没有改变现有工艺定义。保留复验，不擅自清理。

## 当前待验收及边界

真实用户服务/API/UI 尚需用户加载最新构建及本地显式模式，按独立权限配置按钮后继续；不自动重启或授予角色。CONNECTED 真实供应商实现/连接/ERP 返回尚未接入，现有测试不能代表真实 ERP。历史 UNVERIFIED 单据必须人工核实，当前没有可绕过事实的行政“标记无影响”接口。

被拒绝后的新操作重试授权、厂商真实撤销/核实与连接加载、后续 Report/Receipt 不可逆引用，需要真实消费者时扩展；当前同操作重复请求稳定返回原结果。未建设 Reporting/WIP/入库或泛化集成平台。


## 2026-10-14 本阶段回执（用户服务复验前）

| 要求 | 当前结果 |
| --- | --- |
| P0_WORKORDER_ISSUE_FIX | Service 有效领料统计＋父单锁，HTTP Fixture 与指定 MySQL 并发通过 |
| MATERIAL_ISSUE_VOID_FLOW | 三阶段编排、独立权限、作废保留、普通恢复/明细绕过拒绝 |
| ADAPTER_DOMAIN_DESIGN | 仓储领域内聚 Port，生产领域留待真实消费者，无逐动作类/巨大Adapter |
| WAREHOUSE_ERP_ADAPTER | cancelMaterialIssue 与 queryMaterialIssueCancellation 已有真实编排消费者；无供应商实现 |
| LOCAL_ONLY_BEHAVIOR | 明确配置/单据来源/结果标记；未配置拒绝；不能替代原CONNECTED撤销 |
| CONNECTED_BEHAVIOR | 无唯一匹配 Adapter 失败；TEST ONLY 验证三种结果与来源切换，无真实ERP联调 |
| EXTERNAL_RESULT_SEMANTICS | SUCCEEDED/REJECTED/UNKNOWN，诊断不作成功依据，同操作不重复外部写 |
| AGENTS_RULES_UPDATED | 新增领域、厂商隔离、模式、结果、事务、数据库、身份、幂等、Factory及不过早建框架规则 |
| FILES_CHANGED | 正式V18＋再生成持久化4文件；业务规则/Service/HTTP响应；仓储契约/编排；前端主子组件显式动作与只读扩展；测试/规范/菜单SQL/烟测精确类名检查 |
| MYSQL_RESULT | 真库通过；产品订正与void原产品历史、并发竞争一致，记录明确TEST ONLY标签保留 |
| API_RESULT | 用户服务新接口/独立权限待复验，HTTP Fixture 已通过，不冒充真实API |
| UI_RESULT | 194个前端测试与构建通过，真实浏览器作废待复验 |
| TEST_RESULT | Maven404/0fail/0error/10skip；UI194；packaged10/0fail；追加来源切换定向5测试通过 |
| DEFERRED | 真实ERP连接/厂商实现、历史来源人工核实、拒绝后新操作授权重试、Reporting/Receipt事实保护 |

最新前端包含 VOID_PENDING/VOID_UNKNOWN/VOIDED 的查询展示，但普通表单只提供 DRAFT/RECORDED；来源未知按钮禁用并说明。作废成功刷新单据，并发送带当前Factory/工单ID的业务通知，已打开工单页面按权限及Scope重新读取，防止迟到跨厂刷新。

最新实查历史来源：UNVERIFIED/NULL 共5单（DRAFT3/RECORDED2），当前不会被自动认定为无ERP影响。本轮两次真库测试新增2张VOIDED/LOCAL_ONLY/REVERSED单，历史保留。

烟测第一次发现旧检查使用类名后缀，把新 WarehouseErpAdapter 误判为退役 ErpAdapter；改为精确 basename 匹配，仍禁止真正旧类，重新烟测10 passed/0 failed。git diff --check 两仓库通过。自有 smoke 随机端口实例不属于用户服务，不操作已有服务。

当前最新构建 target/mes-base-0.1.0-SNAPSHOT.jar；请求用户按项目第10节自行重启，并审查菜单SQL、配置作废独立权限。真实Service验收脚本已准备于 `.local/material-issue-void-20261014/live-acceptance.py`，先核对当前厂/绑定/新响应/权限，再保留带唯一标签的资料执行用例，未对旧单据猜测外部状态。


## 2026-10-14 最终来源修正与真实验收时间线

此前阶段的“待验收”和 LOCAL_ONLY/REVERSED 描述由本节更新：本地作废没有外部副作用，必须保存 NONE；CONNECTED 确认真实撤销才保存 REVERSED。V19 精确修正具有本地创建来源、本地作废来源、成功结果和操作身份的已作废记录，执行前备份 issues-before-v19.tsv；历史未知/外部来源不动。原 V18 不改写。

- 最新本轮构建 13:34:22 完成 `MES_VOID_MYSQL_ACCEPTANCE=true MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true`：404 项、0 失败、0 错误、10 跳过。最新 UI 37 文件/194 项及 build 通过；最终 packaged smoke 10/0。
- 用户明确授权除重启服务外的必要操作后，应用独立权限菜单2585，仅给现有平台管理员角色100新增该权限，复核原授权全部保留。
- V20 前真实服务20次 HTTP 请求通过：工单25有效领料26阻止产品变更，合法本地作废后允许订正为产品70/工艺105，作废单保留原产品1；伪造普通 VOIDED、恢复作废单、向作废单新增明细及匿名动作均拒绝。重复动作与持久化后查询比较，避免把 MySQL DATETIME 精度截断误判为新操作。
- V20 前真实浏览器创建领料27和明细25（0.123456），确认作废，显示 VOIDED / LOCAL_ONLY，主单输入及材料选择器禁用，明细保留且无写操作按钮。真实 API 另验证修改/删除该明细及删除主单均409。截图 `.local/material-issue-void-20261014/ui-voided.png`。未验证低权限浏览器、响应式矩阵及真实 ERP。
- 最后一次指定 MySQL 业务测试保留 TEST_VOID_601bb854：工单26、领料29、目标产品71/工艺106；另保留并发用例工单27。全部标记 TEST ONLY，工艺克隆仅验证规则，不能充当工艺发布验收。

### 用户再次重启后的复验受阻

最终脚本在 context/navigation 成功后，GET `/api/issue/mes-material-issue/19` 返回500，尚未创建任何新测试资料。核对指定库发现 Flyway V20 已成功执行，来源/结果字段迁入 `mes_material_issue_erp_extra` 并从主表移除；当前 target JAR 的 Mapper 仍查询旧主表字段，且不含 MaterialIssueErpFacts。当前工作区同时出现另一轮 ERP 扩展表适配代码。本轮不覆盖、不回滚、不补写该轮代码或迁移，等待兼容 V20 的构建完成并由用户重启。

数据库只读复核：领料22/24/26/27/29均为 VOIDED / LOCAL_ONLY / NONE / SUCCEEDED，历史来源空且 UNVERIFIED 仍5单。最终状态为 API_REVERIFY_BLOCKED_SCHEMA_RUNTIME_MISMATCH；此前 API/UI 证据保留为 V20 前的通过记录，不宣称当前服务已验证。真实 ERP 始终 NOT_VERIFIED。


## 2026-10-14 V20 兼容构建交付（13:45）

更新旧 MaterialIssueMySqlTest：主单20列、明细15列、ERP扩展16列共51列，检查三表字段注释；删除已迁出主表的测试字段赋值。未修改业务代码或迁移。前两轮分别遇到旧断言和并行 Maven 覆盖共享 target；并行进程结束后完整复验通过。

`MES_VOID_MYSQL_ACCEPTANCE=true MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true`：13:45:17 BUILD SUCCESS，411项、0失败、0错误、10跳过。`/usr/bin/python3 scripts/smoke-packaged.py`：10通过/0失败。git diff --check通过。末轮真库保留 TEST ONLY 工单38、领料40、产品75/工艺110（TEST_VOID_53dd7001）。

JAR 包内确认含 V20、MaterialIssueErpFacts 与扩展表Mapper，主表Mapper不再引用迁出字段。SHA256：1741f4f6ddafbb3af73418259ffea2cfa8f934bf515ae8f8e86fad71f1633d56。尚未重启用户服务，API/UI待加载此构建后复核，真实ERP未验证。
