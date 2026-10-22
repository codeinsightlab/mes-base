# ProductionDefect Phase 1 实施与验收报告

2026-10-16；范围：不良申报自身闭环。**首次交付验收 NO-GO**：代码及定向自动化、指定 MySQL 业务验收已完成；当时中心菜单迁移被已有失败迁移阻断，真实服务与 Web 流程未完成验收。**同日后续已修复中心迁移并执行 V29，见文末恢复记录；其他验收边界未据此改为通过。** 没有进入 Phase 2。

`ProductionDefect Phase 1 = 自身闭环`

`Production Quantity Integration = NOT INTEGRATED`

## A. 仓库基线与并行工作保护

- 后端 `/Users/user/work/lixin/mes-base`：main，HEAD `1c38c306c18f39b2d4780225ac3886d2e8acc2aa`。
- 前端 `/Users/user/work/lixin/mes-base-ui`：main，HEAD `732d669e41c11e13c5bfcdb1a4ba3e67076e942e`。
- 后端原有 dirty：`docs/reviews/2026-10-14-production-chain-audit-review.md`、`docs/reviews/2026-10-15-production-report-v1-implementation-report.md`，以及未跟踪的 `mes-admin/src/test/java/dev/issuefixture/WorkOrderApplicableRoutingHttpTest.java`。未编辑这些文件。
- 前端原有 dirty 涉及 AGENTS、directory API、executionActions / mesProductionExecution / workorder API、CrudPage、RoutingGraphPreview、BusinessDirectorySelect、ExecutionIdentityLookup、ProductionExecutionList、request/sessionStorage/auth、router、Inbox、生产菜单和工单/工艺/工序/执行页面及关联测试；另有 operatorConfiguration、ProductionReport、ExecutionSelect 等未跟踪文件。它们作为当前真实依赖读取，没有编辑或覆盖。本轮前端只新增 Quality API、页面、菜单 JSON 和测试。
- 对照 Production Core / ProductionReport 现有报告，实际源码仍为 Execution 独立实际路径、不可变报工流水、`qualityIntegrated=false`、报工含不良的生产数量口径。未修改已有生产模块使其识别质量数量。
- Git dirty 能识别共享工作，不能证明所有并行进程的归属；本会话未分派子 Agent，未提交、推送、部署或操作 Source Product。

## B. Schema、SQL 与执行状态

业务迁移：`mes-admin/src/main/resources/db/business/V26__production_defect.sql`。

- 单表 `mes_production_defect`，24 个字段；表、全部字段均有中文数据库 COMMENT，无工厂字段、外键或其他新增业务唯一约束。
- `id` 为版本主键；`declaration_id` 为 UUID 稳定申报身份；`revision_no` 连续递增。
- 完整版本包括执行、影响/发现/责任节点、不良数量/原因/分类、责任用户/说明、唯一去向、原始申报人/发生时间、本版本录入人/时间、请求 key / SHA-256 摘要。
- 数量沿用 ProductionReport 的 `DECIMAL(20,6)`，最多 14 位整数、6 位小数，必须大于零。
- 主键；`UNIQUE(declaration_id,revision_no)`；二进制区分大小写的 `UNIQUE(request_key)`；普通 `(execution_id,affected_node_id)`、`responsible_user_id` 索引。版本唯一索引同时支持申报历史查询，没有重复建设相同前缀索引。
- 依照已有 ProductionReport 账本的简单策略，两个 BEFORE 触发器拒绝 UPDATE/DELETE；没有引入触发器业务编排。

**已保存、已执行、已验证：V26。** `AcceptanceBusinessDatabase.source()` 从实际中心配置读取并核对启用的 SIMPLE_B → 数据源 8 → `192.168.18.25:3306/mes_base_dev_business_simple_b`。迁移前确认新表不存在，无存量行需要搬迁或覆盖，保存 Schema/Flyway 前置快照；经正式 Flyway `flyway_business_schema_history` 迁移至 V26，26 项迁移校验通过。没有切换到临时验收库。

中心菜单迁移：`mes-admin/src/main/resources/db/system/V29__production_defect_menu.sql`。新增质量目录、不良管理菜单及 read/create/revise/void 四项权限，不自动授予角色。

**已保存、执行受阻、未执行成功：V29。** 使用上述绑定对应的已配置中心库、迁移前备份相关菜单范围（为空）；正式 Flyway 校验发现中心库**既有 V17 factory user preference 失败记录**，拒绝继续。没有 repair、删除失败记录、改写 V17 或绕过迁移历史手工写菜单。早期尝试的菜单文件号与已有 V16 重复，在执行前被 Flyway 拒绝，现已改为当前顺序 V29，并移除本次自有构建目录中的旧资源。

本地证据：`.local/production-defect-20261016/{binding.txt,schema-preflight.txt,center-menu-preflight.txt,migration.log,acceptance-tests.log,mysql-final.log,binding-final.log}`。私密配置和凭证不进入报告。

## C. 后端实现与权限

代码位于 `mes-business/src/main/java/dev/mesbase/business/quality/`；XML 位于同归属 `resources/dev/mesbase/business/quality/dao/`。

- 使用 **mes-crud-codegen Skill**，从已迁移的真实 JDBC metadata 生成 `persistence + backend + test`，一张表生成成功、零拒绝。配置 `docs/development/production-defect-codegen.yaml`；原始生成证据 `docs/reviews/2026-10-16-production-defect-codegen.json`。
- 保留生成 Entity/Example；在同一生成 Mapper/XML 上适配只读与 INSERT 能力，没有第二套单表 DAO。Entity/Example 与原始生成 hash 一致；手改 Mapper/XML 移除生成归属标记。
- 生成的通用写 Service、Controller、CRUD 命令和脚手架测试已删除，由 `ProductionDefectService` / `ProductionDefectController` / `ProductionDefectModels` / 实际行为测试替换；没有遗留 UPDATE/DELETE 正式入口或失真的 CRUD 测试。全量生成配置排除该账本，防止再次开放通用写入口。
- 业务 Service 使用 `businessTransactionManager`，命令为 READ_COMMITTED；构造器注入业务 Mapper。复用 `ProductionChainLocks` 和 `ExecutionPathService`，不创建用户、工厂、事务或数据源体系。
- 责任用户通过 `BusinessDirectoryService.historicalUserExists` 校验 sys_user；用户为可空引用，不会自动填报工操作人。中心 metadata 由既有受控只读窗口处理跨库事务中的读取。
- 列表/历史按页批量取得路由节点内的工序快照、统一用户目录中的当前姓名、仓库当前名称；最多 100 行、最多两次统一用户目录批量调用，不逐行 I/O，不另建名称缓存。姓名/仓库名称为当前引用，不冒称历史姓名快照。
- 分类、去向和动作有显式领域枚举；ERP 状态/模式及仓库启停复用已有枚举。公共 ErrorCode 仅追加 `DEFECT_IDEMPOTENCY_CONFLICT` / `DEFECT_REVISION_CONFLICT`。
- Controller 白名单响应隐藏请求指纹/key；ID 和 decimal 输出字符串，分页保留 PageHelper 总数。

正式 HTTP：

| 方法/路径（前缀 `/api/quality/defects`） | 权限 | 行为 |
| --- | --- | --- |
| GET `/` | `business:quality:defect:read` | 最新版本分页；executionId/workorderId/affectedNodeId 可筛选，含作废留痕 |
| GET `/{declarationId}` | read | 最新版本详情 |
| GET `/{declarationId}/history` | read | 全部完整历史分页 |
| GET `/executions/{id}/totals` | read | 按影响节点聚合最新有效不良数量 |
| GET `/executions/{id}/nodes` | read | 已选完整实际路径，按实际顺序提供节点 |
| GET `/warehouses` | read | 启用仓库搜索，最多 50 条 |
| POST `/` | `business:quality:defect:create` | CREATE |
| POST `/{id}/revisions` | `business:quality:defect:revise` | 管理员权限点 REVISE |
| POST `/{id}/void` | `business:quality:defect:void` | 管理员权限点 VOID |

管理员能力由明确的业务权限点授予，前端按钮与后端独立校验；不是只靠隐藏按钮。没有通用 PUT/DELETE。

ERP：`ProductionDefectErpGateway` + `LocalProductionDefectErpGateway`，复用 `ErpActionResult.localSuccess()`；默认响应 `success=true / externalExecuted=false / executionMode=LOCAL_ONLY / externalReference=null`。没有 HTTP 厂商实现、外部单号、库存写入或报废事实。重试只重复无外部副作用的本地确认。

## D. Web 实现

前端新增：

- `src/api/quality/productionDefect.ts`：命令/版本契约、精确字符串 ID/数量、API 适配、实际路径上游辅助函数。
- `src/views/quality/productionDefect/index.vue`：独立“不良品管理”，工单/Execution 筛选、最新列表、新增、管理员订正/作废、只读完整历史及展开详情、分页。
- `src/menus/quality/productionDefect.json`：供现有菜单/动态路由体系使用。现有 router 使用 glob 发现页面，无需修改用户正在编辑的 router。
- `tests/productionDefect.test.ts`：真实 Vue/Element Plus 组件行为测试，API 为 Mock。

复用 PageHeader、ScopeTag、Pagination、ExecutionSelect、WorkOrderSelect、PersonnelSelect 和现有设计样式。保留工废/料废、未认定责任、发生时间、去向备注；去向字段互斥，仓库远程搜索只返回启用对象；创建后影响节点/Execution 禁止订正。退工序默认实际上一道，并可选更早上游。

**基准节点说明（2026-10-16 用户已确认）：** 退工序以“发现节点”为基准：默认其上一道实际工序，允许指定更早的实际上游工序，禁止退回发现节点本身、下游或未选路径节点；首节点无上游时不能使用退工序去向。数量影响节点独立用于不良数量归属。用户回复与现有实现一致，本次仅更新确认记录，未修改业务代码、原工艺或数量服务；不改变其他验收阻断及 NO-GO 结论。

切厂、权限变化、关闭、卸载清空状态并失效请求序列；迟到响应不能恢复旧详情。未保存内容关闭前确认；遮罩、Esc、关闭按钮沿用默认关闭行为。未确定结果的重复提交保留同一个 requestKey；相同内容可重试。页面持续明确“仅本地登记、不执行返工/库存移动/ERP报废、不扣减下道可报工量”。

真实浏览器只检查到现有 `5173` 登录入口，未进行新业务流程或三档宽度验收；不以组件测试/构建冒充真实 UI 验收。

## E. 版本与幂等实现

1. CREATE 插入 revision 1；REVISE/VOID 必须 expectedRevision + 留痕原因；三个动作共享同一命令用例，不调用通用 UPDATE。
2. 首次检查幂等 key；按既有 WorkOrder → Execution 顺序锁定；在锁内重查 key 和最新版本；预期版本不匹配或最新 VOID 则拒绝。
3. REVISE 不能改变 executionId/affectedNodeId；引用、正数数量、最多六位小数、类别、责任、去向均由业务 Service 校验。VOID 继承完整事实和原始申报人，追加终止版本，不再校验可能已停用的历史仓库，仍可作废旧事实。
4. reportedBy/recordedBy 从可信 SecurityUtils 登录上下文取值，不绑定客户端审计身份；reportedBy 在订正/作废时保留原值。
5. 指纹采用命令类型、申报身份、操作者、预期版本、规范化留痕原因及全部业务参数的长度前缀编码，再 SHA-256；规范化 decimal 尾零、文本边界空白及数据库时间微秒精度，不依赖 JSON 键顺序。默认目标省略与显式提供目标属于不同请求内容。
6. 同 key/同摘要返回**原操作版本**，不会因后来订正而返回别的结果；同 key/不同摘要返回专用幂等冲突。数据库唯一约束为跨 Execution 同 key 并发提供最终保护；冲突后读取原操作核对摘要。数据库失败使事务回滚，重试不能重复插入。
7. 版本唯一键加共享数据库行锁串行化同一 Execution 版本追加，不依赖 JVM 锁。工厂隔离由现有业务库/固定事务租约提供，同 key 可在不同工厂独立使用，无跨厂全局身份缓存。
8. 最新有效查询采用相关 NOT EXISTS 先排除旧版本，再筛选并聚合；最新 VOID 排除，没有 JOIN ProductionReport。每条申报只贡献其当前有效版本，不累计历史。

## F. 测试与证据

所有日志位于 `.local/production-defect-20261016/`，下列“通过”按证据等级区分。

| 检查 | 命令/证据 | 结果 |
| --- | --- | --- |
| 真实 Schema/Flyway | `MES_DEFECT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am test '-Dtest=ProductionDefectMySqlTest,ProductionDefectMigrationMySqlTest#migrateDesignatedBusinessDatabase' -Dsurefire.failIfNoSpecifiedTests=false`，`mysql-final.log` | 2 项通过，真实指定 MySQL；不含受阻的中心菜单 |
| Quality HTTP/契约 | `mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionDefectHttpTest,ProductionDefectContractTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 + 3 项通过；HTTP 为双 H2 路由、真实 XML/Spring 事务，中心身份 Mock |
| 后端全量 | `mvn -B -ntp verify`，`verify-final.log` | 539 项：517 通过、22 跳过，失败 0、错误 0；显式环境门控的真实库测试默认跳过，另以真实 MySQL 专项补充 |
| 前端全量 | Node `v22.23.1`，`npm run test`，`ui-all-tests.log` | 51 个文件、275 项通过；其中 Quality 8 项 |
| 前端构建 | 同 Node，`npm run build`，`ui-build.log` | vue-tsc + Vite 通过；有大 chunk 提示，无构建失败 |
| Java 格式 | `scripts/format-java` 指定本轮文件 | 格式化完成；IDEA 旧版退出阶段有插件/索引警告，未格式化用户其他业务文件 |
| 修改检查 | `git diff --check`、生产禁止目录 diff | 通过；禁止业务目录无任务改动 |
| 中心菜单 | 正式 Flyway，`acceptance-tests.log` | BLOCKED：已有 V17 failed；V29 未执行 |
| 用户现有 HTTP | `127.0.0.1:8081`，健康端点 UP；授权质量读取探针 | 请求超时，未完成新接口验收，不宣称用户服务已加载新代码 |
| 真实 Web | 现有 `127.0.0.1:5173` | NOT RUN：仅见登录入口，未完成菜单/业务/宽度/切厂实际流程 |
| 打包启动 smoke | 未运行 | NOT RUN：本轮不启动、停止或重启用户服务；没有部署或发布证明 |

覆盖：CREATE/多次 REVISE/VOID/终态、原始历史保留、当前版本筛选、聚合归零、精确数量、非法数量、工废料废、空责任/非法责任、责任更新留痕、可信审计身份、无效 Execution/未选节点/责任节点、当前/下游/首节点拒绝、默认上一道、更早上游、启用/不存在/停用仓库及目标互斥、幂等同内容/异内容/不同动作/不同 Execution/不同工厂/并发同 key、并发订正、回滚重试、权限正负例、HTTP 无 UPDATE/DELETE、真实数据库触发器拒绝改写。

H2 的历史筛选回归测试故意改写旧行来模拟导入历史不一致，仅用于证明“先最新后过滤”；这不是正式写入口，MySQL 的相同改写已被触发器拒绝。前端测试覆盖精确值、权限按钮、只读历史、关闭取消、稳定重试 key、切厂/撤权迟到响应及阶段边界文案。

真实 MySQL 最新一次使用既有 Execution 61 / 节点 1024、启用仓库，在新的 UUID 请求范围内追加两个 TEST ONLY 申报：

- `bc60bcbf-538a-4da0-b112-2dddbe791a25`：3 个版本，最终 VOID。
- `e4d0e877-e470-468c-aaad-15a8fb68b247`：回滚/并发重试后 2 个已提交版本，最终 VOID。

当前有效贡献归零，历史留存供复验；此前一次相同验收也使用独立 UUID、最终作废，没有物理删除审计事实。变更前冲突数为零、记录受影响 Execution 报工快照，事后逐行比较 ProductionReport 相同。没有创建新工厂/验收库、覆盖存量业务行或写库存。MySQL 业务测试使用独立测试 Spring 上下文/可信身份夹具，不冒充现有用户服务 HTTP 认证验收，也不冒充真实双厂 MySQL 路由验收；双厂行为由 H2 路由集成测试证明。

## G. 边界审计与未解决依赖

确认本轮：

- 未修改 ProductionReport 的业务或 Schema，真实 MySQL 验收前后相关报工行相同。
- 未修改 ProductionQuantityService、ProductionExecution 状态机、ExecutionPath 选路/锁定、RoutingNode/Edge 结构、MaterialIssue 或 ProductionReceipt 业务。
- 未计算/改写 reportedQty、inputQty、availableQty、effectiveOutputQty、qualityIntegrated；未注入质量聚合到生产数量服务。
- 未执行返工、仓库库存移动、ERP 报废或工资核算；没有创建 ProductionReport。
- 未修改 Source Product；未提交、推送、部署或重启用户服务。

**不能跨出本轮边界修复的引用保护依赖：** `ProductionExecutionRules.deleting()` 当前只检查 started / issue / report；`ExecutionPathService` 的保护也没有依赖质量事实。在仍允许选择/修改路径或删除的执行上登记不良后，现有生产入口可能改变引用或删除 Execution，历史账本仍保留但后续非 VOID 订正可能因引用失效被拒绝。本轮没有擅自修改这些生产模块、增加跨表触发器或物理外键。正式业务投用前需单独确认该引用保护契约，或者明确限定允许申报的生产生命周期；不能把此风险描述为已解决。

其他限制：只在 SIMPLE_B 执行业务迁移；其他业务库需正式迁移。没有真实多厂 MySQL/部署/浏览器流程证明。仓库/用户当前名称可能随主数据更新，历史只保存原始 ID 与事实内容。requestKey 和申报身份只在当前业务库内唯一。未进行大数据量性能压测。

## H. 最终结论与交接

**Phase 1 NO-GO（完整业务验收口径）。** 已实现不良自身的 Schema、CREATE/REVISE/VOID、完整历史、责任及工废/料废、工序/仓库去向登记、有效数量聚合、LOCAL_ONLY ERP 适配与独立 Web 页面，且有定向/全量自动化及真实业务库证据。未完成：中心 V17 失败迁移的任务外治理及 V29 菜单执行、现有用户服务加载新代码后的真实 HTTP/Web/响应式流程、上述跨模块引用保护决定。

后续恢复验收顺序：有边界地处理中心既有迁移状态 → 通过正式 Flyway 执行 V29（不得手工绕过）→ 管理员按需授予质量权限 → 用户自行重启后端 → 在 SIMPLE_B 验证真实 HTTP、Web 及三档宽度/错误/切厂/撤权。这里没有授权或执行任何重启、角色赋权、迁移 repair 或 Phase 2 工作。

Phase 2 预留的是现有可调用契约，不是本轮新增集成：

```java
BigDecimal sumEffectiveDefectQty(Long executionId, Long affectedNodeId);
List<NodeTotal> nodeTotals(Long executionId);
```

返回最新非 VOID 版本合计；空集合/无贡献为零；没有报工 JOIN。Phase 2 必须另行设计统一锁顺序、报工与不良数量一致性、下游约束、effectiveOutputQty/availableQty 推导及真实跨入口验收，不能直接接入就宣称数量闭环。

回滚：保持当前未部署状态即可撤回应用代码；不可删除/更新已提交版本、不可改写已执行 V26/Flyway 历史。需要撤销数据库对象或审计数据时，另立经授权的正式迁移/数据保留任务。


## 2026-10-16 中心迁移恢复与菜单落库（用户追加授权）

用户要求“定位并且修复”菜单迁移失败。本次只修复中心库迁移状态、纳管已存在的结构并执行待办菜单迁移；没有修改业务代码、历史迁移 SQL 或现有角色授权，也没有重启用户服务。

### 根因与当前实证

历史事故记录见 [仓库与库存审查的中心库历史事故章节](2026-10-13-warehouse-inventory-review.md)。此前中心库 history 停在 V14，执行完整迁移成功到 V16，随后 V17 的 `CREATE TABLE mf_user_preference` 因表已存在而失败。实时复查确认：

- 实际中心目标为 `192.168.18.25:3306/mes_base_dev`；同一配置的 SIMPLE_B 仍绑定启用数据源 8、`192.168.18.25:3306/mes_base_dev_business_simple_b`。
- history V1–V16 成功，V17 `success=false`；V17 表已存在，有 4 条真实用户工厂偏好。
- V19 撤销表、V20 续期字段/索引、V21 路由 metadata、V23 个人列偏好也已存在。实际 Schema 与 Flyway history 脱节，单独清理 V17 失败记录再全量执行仍会遇到重复建表/加列。
- V18 已存在人员目录权限，但缺部门/岗位两项；V22 OpenAPI 菜单存在；V24/V25/V28 退役范围的菜单、个人偏好与同步键均为空。V26/V27 对应当前菜单已存在，可以按原 SQL 正常执行且保留 ID；不需要猜测旧角色授权。

### 修复方法与数据保护

1. 用正式配置解析连接，在变更前核对 Factory/数据源绑定、成功历史的 checksum/description/type、失败范围、所需字段类型/长度/null、主键/索引及路由单例有效性。发现结构、菜单路径或失败范围超出预期即停止。
2. 保存诊断快照，以及受影响范围的 SQL 备份：`flyway_schema_history`、`mf_user_preference`、`sys_session_revocation`、`sys_routing_metadata`、`sys_user_table_preference`、`sys_menu`、`sys_role_menu`、`mes_master_sync`。备份为 `.local/center-migration-recovery-20261016/center-before.sql`（78,405 字节）；临时备份凭证文件已删除，不纳入版本库或报告。
3. 先在隔离 H2 验证当前 Flyway 12.4 的 `skipExecutingMigrations` 会通过正式迁移机制登记历史且保留已有行。这是工具能力验证，不代替真实中心库验收。
4. 调用正式 `Flyway.repair()`，只移除 V17 的失败记录；断言没有 align/delete 其他历史，V1–V16 成功历史及 checksum 完全保持原样。没有手工 UPDATE/INSERT Flyway history。
5. 逐版本设置 `target`，对 **V17、V19、V20、V21、V23** 使用 `skipExecutingMigrations(true)` 纳管已核实的既有结构；对 **V18、V22、V24、V25、V26、V27、V28、V29** 正常执行原 SQL。已存在的偏好、撤销数据及路由代次不通过删表/重建或重置 seed 的方式处理。
6. 恢复默认配置，执行正常 Flyway migrate/validate 复验。skip 参数仅用于这次有前置核对的恢复调用，没有写入应用配置、没有留下全局跳过迁移的开关。既有 collation/COMMENT 的非业务结构差异予以保留，不宣称历史 DDL 被重新执行或物理定义逐字符相同。

官方依据：[Flyway Skip Executing Migrations Setting](https://documentation.red-gate.com/fd/flyway-skip-executing-migrations-setting-277579035.html)，用于将已在 Flyway 之外存在的变更纳入其迁移管理。本次没有购买/变更工具授权。

### 修复结果与验证

- **真实中心库 V1–V29 全部有效，29 项 validate 通过；failed=0，pending=0。**
- V29 已通过正式迁移执行：质量目录 **2607**，不良品管理页面 **2608**，申报/订正/作废按钮 **2609/2610/2611**；页面组件为 `quality/productionDefect/index`，read/create/revise/void 四项权限全部存在。
- 变更前 354 个菜单 ID 全部保留；885 条角色菜单关联逐项一致，没有新增角色授权或恢复无法证明的旧授权。4 条工厂偏好、个人列偏好和路由 metadata 代次逐项一致。
- 通过原有正常入口再次运行：
  `MES_DEFECT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am test '-Dtest=ProductionDefectMigrationMySqlTest#migrateVerifiedCenterMenu' -Dsurefire.failIfNoSpecifiedTests=false`
  **1 项通过，BUILD SUCCESS**；Flyway 返回数据库 V29 已是最新版本、无需迁移，29 项校验再次通过，四项质量权限数量断言通过。
- 本次没有业务代码/配置修改，因此没有重复运行前端全量测试或宣称质量业务页面已完成真实交互验收。现有 `8081` 服务登录读取尝试超时，未取得菜单 HTTP 响应；证据为 `live-menu-read.json`。当前工作区新出现的 ProductionExecutionRules / WorkOrderService / WorkOrderExtraMapper / WorkOrderEnumSqlTest 改动属于其他工作，未编辑或覆盖。

证据目录 `.local/center-migration-recovery-20261016/`：`diagnose.log`、`diagnostic-snapshot.yml`、`menu-audit.log`、`center-before.sql`、`backup.log`、`SkipProbe.java`/`skip-probe.log`、有严格前置检查的一次性 `Recover.java`/`recovery.log`、`after.yml`、`normal-migration-test.log`。该目录只保存本地诊断/备份，不包含提交或部署动作。

**本次菜单问题已修复。** 首次报告中的“中心 V17 阻断/V29 未执行”已被本节真实证据取代，保留旧段落用于追溯。本次未重新评估 Phase 1 的其他引用保护、用户服务加载及完整 Web 流程，不能因菜单落库就宣称完整业务投用验收 GO。

## 2026-10-22 — 不良列表查询收窄与生产批次展示

本节记录当前代码的新证据，不将前述 Phase 1 界面或数据模型描述当作当前交互。用户确认批次号从投入材料一直延续到成品；本次列表中的 `batchNo` 读取永久生产扫码身份的 `material_lot_code`，不使用执行编号、旧生产批次表或某一张入库单推导。没有可信绑定时明确显示“批次未记录”，没有为导入验证数据新增兼容分支。

### 源码审查与修改

- 原列表在完成分页后，通过 `QualityTreatmentService.summaries` 加载本页申报所有版本的完整流水，最多取5001行，再分组构造含事件、依据、净不良、待开始、在制等详情字段的 Summary。列表实际只消费状态、待处理、退回及转仓数量；旧版本流水不应参与本页状态计算。
- 现在先分页取最新申报版本，再由 `ProductionDefectListExtraMapper` 按当前页版本主键批量查询：关联信息走主键；处置按 `declaration_id + declaration_revision` 聚合，一页至多返回100个紧凑结果，不加载完整流水、人员、原因和依据，不再受跨版本5000条流水上限影响。仍保留决定现有业务状态的必要数量，并与详情共用同一个状态函数，避免列表和详情各定义一套状态。ASSESS的MAX只用于投影核实结论，依据是现有持锁命令路径每个版本至多允许一次核实，不代表按大小选择业务结论。
- 分页总数继续保留，一次COUNT只统计最新版本；不把统计分析页的分组汇总接到列表。列表有效排序仍为 `id DESC`，移除Service对Mapper排序的重复覆盖；HTTP转换继续只在Controller通过公共函数完成。关联Context使用类型化record和通用属性映射，不逐字段搬运新增字段。
- 列表新增批次号、产出品号、规格；批次与工单合列、产品名称省略并可查看完整值，减少长名称造成的行高。详情同步显示批次及规格。品号、名称和规格来自执行产出产品对应的Material；规格是当前主数据值，本次没有将其声明为申报时冻结快照。
- 修改范围：质量Controller/查询模型、列表ExtraMapper及XML、申报Service、共享状态函数、相关Fixture及HTTP测试；前端不良列表、详情组件、API类型及页面测试。原完整处置详情与生产数量业务继续读取其需要的事实。

### 表结构与冗余决定

已重新核对中心绑定：Factory `TEST_DEV_PERF_20261019` / datasource `9`，目标 `192.168.18.25:3306/mes_base_dev_perf_test`，服务端UUID `03dbc952-7403-11ef-802c-0242ac130002`；业务Flyway为V48。真实库有15598条不良版本、31194条处置流水、1106个扫码身份；同一执行多扫码身份数量为0，重复版本核实数量为0。

本次不向不可变不良版本账本增加批次、品号、规格冗余列。分页后只关联本页对象，Execution/Workorder/Material均使用主键，扫码身份使用 `idx_scan_execution`，处置使用 `idx_quality_treatment_declaration(declaration_id,declaration_revision)`；没有全表产品关联或一对多连接导致分页行膨胀。即使冗余产品字段，判定当前工单/执行状态及来源仍需读取这些关联，不能消除主要关联。若将来需要“申报时规格”这一历史业务事实，应明确建模快照，不能以本次性能猜测代替语义决定。

真实只读 `EXPLAIN ANALYZE` 对20条版本采样：最新页为PRIMARY反向扫描加 `uk_defect_revision` 反连接；关联信息5次热读中位服务端0.290ms，紧凑处置聚合0.844ms。两条查询各返回20行，处置索引实际读取40条本页事件。小规模样本不证明整体接口已加速，也不包含COUNT、目录、网络与序列化耗时；收益首先是查询版本范围和返回数据量收窄。证据位于 `.local/defect-query-20261022/query-plans.json`。

### 验证与边界

- `PATH=/opt/homebrew/bin:$PATH /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp verify`：BUILD SUCCESS，848项、零失败/错误，49项按环境跳过。新HTTP测试覆盖批次前导零、规格、状态与详情一致、旧版本核实不污染当前版本、数量精度、空页仍保留total、另一工厂读取隔离，以及只对当前页版本读取紧凑汇总。日志 `/tmp/mes-defect-batch-final-verify.log`。
- 前端使用已有Node22，`npm test`：74个文件、430项通过；`npm run build`通过。日志 `/tmp/mes-defect-ui-full-tests.log`、`/tmp/mes-defect-ui-build.log`。既有5173页面实际观察到新增列和1280/1440/1920宽度布局；其后端仍为旧进程，不能据此宣称新字段及查询链路已通过运行服务验收。
- 本次没有DDL/DML、迁移、真实ERP调用、提交或用户服务启停。查询变更已在核实的真实目标执行只读计划检查；业务读写及权限的自动化证据来自隔离H2 Fixture，不冒充真实库写入验收。撤销本轮相关源码变更即可回滚，没有数据库回滚需求；用户重启后端后才能验证其运行接口。
- 同轮用户确认修正不良转仓批次。`ProductionIdentityService.requiredBatch` 使用执行索引最多读取2条绑定、不做COUNT；核对唯一绑定、规范扫码编码摘要及冻结品号/材料/批次的一致性，保留批次原文，缺失或冲突明确拒绝。`QualityDispositionService` 对库存定位、新建库存及正式入库明细统一使用此批次，沿用原来源、权限、物料引用保护及本地事务，不改写旧库存或旧单据，不增加ERP写入能力。
- 质量处置10项HTTP回归通过，包含入库单/库存/列表批次一致、原请求重放不重复入库（即使新准入所需身份随后不可用，仍先读取原不可变结果）、缺失/歧义/冻结内容不一致拒绝且不产生仓储或处置事实，以及过账失败回滚。相关SQL写入仅发生在隔离H2 Fixture，未执行真实验收库入库。
- 后端最终构建结束后已请用户重启现有服务，再进行真实只读接口复核；不在验证中自动创建真实申报或库存动作。

## 2026-10-22 — 当前质量流程收敛与一次性迁移工具退役

本节取代上节中关于旧质量数量和状态兼容的描述。用户明确要求开发阶段不保留过期流程，并确认旧生产数据迁移已完成，不再需要执行迁移工具。当前只支持核实归属、退回申报节点、转仓；退回后走正常生产报工，转仓使用正式入库业务。历史报告中的旧命令、启动器和脚本仅为当时证据，不能再作为当前操作说明。

### 当前业务实现

- 删除 ALLOCATE、START、RESULT、取消分配/开始、结果订正等旧处置命令及数量字段投影。核实入口为 `POST /api/quality/defects/{id}/assessments`，旧 `POST .../treatment` 不再提供命令；详情读取仍保留 GET。前端不再发送旧命令结构中的零值占位字段。
- 状态为类型化枚举，枚举自身提供值索引；列表和详情通过 `Processing.of` 共用当前余额规则。待处理＝申报数量－退回数量－转仓数量，净不良＝独立申报数量－退回数量；转仓仍是已产出不良，不解释为合格品或“明确报废”。处理流水只解释当前三类动作；旧动作返回明确数据错误，不按兼容状态机计算，也不伪装为待处理。
- 详情按申报身份读取流水，列表只按当前页版本主键聚合退回/转仓数量和核实结论；移除报废、待开始、在制、结果、恢复数量的旧汇总。统计与工单进度同步使用当前语义；XML枚举编码由枚举getValue绑定为参数。
- 修复退回节点的中间态校验：退回同时减少该节点报工总量与净不良，原有效产出保持不变。生产数量校验以这一成对变化检查下游约束，同一事务写负向报工和质量流水，失败回滚；之后由正常报工恢复产出。自动化测试覆盖已有下游报工的退回和随后正常报工，不再依赖开始返工/确认结果命令。
- 上节的批次延续修正保留：正式转仓库存及入库单使用扫码身份冻结批次原文，缺失/歧义/内容冲突拒绝。没有调用真实ERP或新增ERP写入能力。

### 迁移退役范围与保留边界

- 删除 `LegacyHistoryImportService`、`LegacyProductionActivationService`、`LegacyImportCommand`，以及4个专用导入/激活/验收启动器、对应导入证据与批量写入测试。
- 删除 `scripts/legacy/` 的9个一次性准备、转换、执行、复核脚本及4个专用Python测试；移除不再使用的迁移命令模型。现存历史报告和备份保留，不再把其中命令视为可执行入口。
- 将 `LegacyMigrationMapper` 收窄并重命名为 `LegacyHistoryMapper`，删除INSERT、UPDATE、锁、批次准备/审批/状态转换、导入映射写入和仅供导入使用的查询。保留冻结期初投入和节点事实的只读查询；部分已迁入执行的现有数量计算仍依赖这些不可变证据，不能通过删读取来改变数量。归档只读查询及其数量、权限/状态边界测试继续保留，没有另建迁移服务或写入口。
- 正式Flyway版本升级、已发布V34/V40等迁移、数据库表/既有事实、备份与历史报告均未删除或重写；LOCAL/ERP来源、权限和事务边界保持原规则。旧工单用料YAML配置属于另一个尚受保护的运行契约，未冒认为一次性旧生产导入而删除。
- `AGENTS.md` 追加“一次性旧生产数据迁移退役”规则；打包烟测增加退役导入类不应进入安装包的断言。最终使用clean构建，避免已删除源码对应的class残留。

### 真实数据读取及验证边界

重新核对 `TEST_DEV_PERF_20261019` / datasource9 / `192.168.18.25:3306/mes_base_dev_perf_test` 后，对当前列表SQL执行只读 `EXPLAIN ANALYZE`。20条版本样本的关联信息热读中位0.307ms，当前处置聚合0.773ms，各返回20行。证据为 `.local/defect-query-20261022/current-workflow-query-plans.json`；此数据不证明完整HTTP耗时，也不代表真实业务写入验收。

现有导入验证数据仍含15598条旧处置动作（ALLOCATE15582、START8、RESULT8）。本轮没有删除、转换或改写这些不可变事实；当前读取遇到不符合当前契约的记录会明确失败。该边界与“迁移工具已退役”是两件事：不能因为工具移除就将旧动作解释成新流程。当前正向业务、并发、回滚、批次、权限和跨厂负例使用隔离H2 Fixture，真实验收库只读；没有真实入库、真实ERP调用或用户服务启停。

### 本轮最终验证结果

- `PATH=/opt/homebrew/bin:$PATH /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp clean verify`：BUILD SUCCESS，共843项、失败0、错误0、48项按环境跳过。日志 `/tmp/mes-current-quality-clean-verify.log`。清理关联测试时修正了旧申报目标仓库参数和共用测试工厂签名，保留数量公式、版本/作废、后续正常报工、并发、回滚、权限/隔离等行为验证；未启用会写入真实MySQL的环境开关。
- `python3 -m unittest discover -s scripts/tests`：剩余4项配置检查测试通过，日志 `/tmp/mes-migration-retirement-python-tests.log`。
- 前端 `npm test`：74个文件、430项通过，日志 `/tmp/mes-current-quality-ui-full-tests.log`；`npm run build`通过，日志 `/tmp/mes-current-quality-ui-build.log`。构建保留既有分块体积提示，没有为本任务调整打包配置。
- 以本次clean verify得到的JAR快照运行现有 `scripts/smoke-packaged.py`（仅将脚本内JAR路径指向 `/tmp/mes-current-quality-validated.jar`）：10项通过、0失败。独立随机端口进程使用classpath默认配置，不连接用户开发库、不探测ERP、不启停用户服务；原脚本其余逻辑未变。安装包内再次核对：旧导入/激活服务、命令及可写Mapper（含内部类）均不存在，只读 `LegacyHistoryMapper` 保留。
- `git diff --check`（后端/UI）通过。未提交、推送或部署；代码回滚可恢复本次源码变更，无数据库回滚。已在最终构建结束后请用户重启开发后端，运行服务复核另记，不能用上述Fixture/打包验证代替。

### 2026-10-22 用户重启后的真实只读复核

用户确认后端已重启后，重新核对中心绑定仍为 `TEST_DEV_PERF_20261019` / datasource9 / `192.168.18.25:3306/mes_base_dev_perf_test`。通过现有8081服务读取，不调用ERP，不创建申报、处置或库存事实。此前私有验证会话已失效，使用用户已提供的本地开发账号重新认证；令牌只在验证进程内使用，未写入报告。

- 默认不良列表、最新处理流水、默认工单进度均真实返回HTTP409 / `QUALITY_DATA_INVALID`，与当前库仍含旧动作的事实一致，不能宣称全库列表已正常可用。
- 执行1126、9944、9635的限定不良列表、单条申报详情与处理详情全部HTTP200。三个样本品号、规格存在；列表与详情的状态、待处理、退回和转仓数量逐项一致，流水均只有ASSESS。样本待处理分别10、10、1；没有虚构扫码批次，三个样本的batchNo均为null。
- 按工单号5100-261001754、5100-261001769筛选的进度HTTP200，返回创建时间、待处理10、退回0、转仓0，已不含旧报废/待开始/在制字段；工单5100-260901515的其他关联记录仍有旧动作，返回同一明确数据错误，不能凭该工单的一条有效申报推断整张工单有效。
- 5173浏览器实际加载新版列；筛选执行1126展示一条记录，品号24155262001、规格3142U061、批次未记录、待处理10。查看详情展示申报/待处理/退回/转仓的数量去向与核实记录；本轮没有提交处理。
- 发现并修复前端请求层提示遗漏：原专用错误码被HTTP409通用文案覆盖，错误提示误导用户“刷新重试”。在 `src/lib/request.ts` 的既有受控业务错误表补充 `QUALITY_DATA_INVALID`：“质量记录不符合当前流程，请核对验证数据；刷新无法解决此问题”。不透传任意后端错误文本，不改变身份/选厂或重试策略。浏览器热更新已实际显示新提示。
- 对应 `tests/request.test.ts` 增加该专用码的身份/选厂保持断言，`npm test -- tests/request.test.ts` 共30项通过；`npm run build`通过。日志 `/tmp/mes-quality-live-ui-request-tests.log`、`/tmp/mes-quality-live-ui-build.log`。后端本轮未再修改，不重复编译或要求重启。

真实HTTP响应和限定SQL候选记录保存在 `.local/defect-query-20261022/live-after-restart.json`；不包含认证令牌。此次完成的是新版本运行读取与错误边界验证，不是旧验证数据转换或真实处置写入验收。现有旧流水的数据问题仍需另行按事实保护规则处理；没有为了让页面可读而静默忽略旧数量，也没有恢复已退役的迁移工具。

## 2026-10-22 — 查询不因单条处置数据异常而阻断（用户纠正）

用户明确要求：数据状况不应阻断查询。本节取代前述“默认列表因旧动作整页返回409”的处理决定；不通过简单删除前端提示、隐藏异常记录或返回空列表来修复。

- 不良列表继续返回本页所有申报、原排序和total。未知动作、未知归属、非法余额或缺少汇总时，受影响记录的查询状态为 `UNRESOLVED`（处置待确认）；无法确认的待处理/退回/转仓数量返回null，前端显示“—”。正常记录仍按原规则计算，不能因同页其他记录异常而失败。
- 详情和最近10条处理流水保留原始动作码、登记时间、原因和操作者。未知动作展示“未识别的操作（原动作码）”，不恢复旧流程命令或替它编造新动作。详情数量不可确认时展示轻量说明，基本信息与处理记录仍可查看。
- 工单进度保留计划、正式完工、良品缺口及创建时间等可信字段；质量处置无法计算时，该工单的质量合计与依赖转仓数量的待完工差额返回null，并给出行内说明。统计保留申报和核实分类等已记录信息，有未知处置动作的组不返回未经确认的退回/净不良数量，标记处置数量待确认。
- 查询投影与命令校验使用同一当前余额规则，但查询允许结果待确认；写入、处置、放行和库存事实仍执行严格校验。未知动作不会被忽略后当作零处置放行；前端也不提供基于未知余额的核实/处理按钮。Factory隔离、权限、基础设施故障仍保留真实错误，不捕获或吞掉数据库/网络异常。
- 既有前端请求层 `QUALITY_DATA_INVALID` 仅供严格处理失败提示，改为“当前记录暂不能处理，请先核对处置记录”，不再使用阻塞查询的文案。`AGENTS.md` 追加查询可读性与处理校验规则。
- 回归场景使用同页一条有效申报和一条含未知流水的申报：列表/详情/最近流水/工单进度/统计均应200；分页和有效记录不受影响，未知余额为null，尝试写处置仍失败且无新增事实。前端回归验证原始流水可见、“—”区别于0、无整页错误且无误开放处理入口。本轮没有SQL/Schema变更、真实业务写入、迁移工具恢复或ERP调用。

本轮验证：`PATH=/opt/homebrew/bin:$PATH /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp verify` BUILD SUCCESS，844项、0失败/错误、48项环境跳过（`/tmp/mes-query-tolerance-verify.log`）；Node22下`npm test`为74个文件432项通过（`/tmp/mes-query-tolerance-ui-full-tests.log`），`npm run build`通过（`/tmp/mes-query-tolerance-ui-build.log`）。两端`git diff --check`通过。自动化业务证据来自隔离H2，未执行真实处置；后端最终构建结束后已请用户重启，再验证默认查询的真实服务结果。

用户确认本轮重启后，在重新核实的TEST_DEV_PERF_20261019/datasource9/perf_test目标通过真实8081复核：默认不良列表HTTP200，20条/总计15597；第二页20条、total相同且与第一页ID不重叠。最近流水HTTP200返回10条；默认工单进度HTTP200返回5条/总计1879；统计HTTP200返回20组/总计426。默认页含旧动作的20条记录均返回UNRESOLVED及null余额，记录本身可读；三个有效样本的列表和详情仍一致。原先会阻断整个查询的申报现可通过详情读取，基本信息、申报数量26、原始ALLOCATE动作/原因/时间均保留，处置余额显示“—”。浏览器5173已实际验证默认列表、详情和待确认展示，无原阻塞提示；未提交任何真实处置或修改事实。

最终打包烟测 `python3 scripts/smoke-packaged.py`：10项通过、0失败（`/tmp/mes-query-tolerance-smoke.log`）；隔离进程未使用开发库配置，未启停用户服务。真实只读HTTP证据为 `.local/defect-query-20261022/live-query-tolerance-after-restart.json`，不含认证令牌。本节将上一节的默认查询409实证标记为已修复，保留旧描述仅用于审查追溯；当前仍不支持以未知流水推导可写余额，也未恢复旧处置或迁移能力。


## 2026-10-22 不良品闭环与关联表使用审查

### 范围、结论与证据等级

按用户要求审查当前工作树：不良申报/订正/作废 → 归属核实 → 返回申报节点正常报工，或创建指定仓库入库单并审核 → 库存动作 → 质量放行与生产完工。只读审查，不修改业务代码、配置、迁移或业务数据；仅追加本章节及保存本地只读证据。工作树有用户及前轮修改，包括未提交V49，不执行或改写这些内容。

结论：**当前LOCAL两种处置的实现链路已接通，有隔离HTTP集成测试证明；当前指定验收库尚未完成新流程真实闭环，不能宣布不良品整体已验收。四张现行质量相关表均有实际代码入口，没有发现可以按“整表无人使用”直接删除的现行质量表；旧流程遗留主要是处置表的字段及存量动作。** 导入样本不满足当前规则属于验证数据准备问题，不要求产品兼容旧流程。

### 本轮发现

1. **验收阻碍：新申报入口缺少不良类型配置。** 当前perf库operation_defect_type共0条，而ProductionDefectService.declare要求defectTypeId、expectedTypeRevision并调用lockForDeclaration核实所属工序、启用状态和版本。正式GET declaration-context返回200、types=[]。因此不能仅凭旧申报列表有数据认定“新建申报→处理”已经真实验证。建议经现有工序不良类型维护接口准备正确配置，再使用当前正常生产链测试；不通过旧创建接口绕过V2来冒充验收。
2. **验收阻碍：没有当前退回/转仓的真实样本。** 本轮只读查处置表：ASSESS15596、ALLOCATE15582、START8、RESULT8，RETURN_NODE/TRANSFER_WAREHOUSE为0；DEFECT_RECEIPT/SCRAP_RECEIPT单据为0。申报15598条，19条SAME_NODE、15579条UNSPECIFIED，command_version均为空。前轮固定10条HTTP命令全部409，不能以日志/老ALLOCATE当作实际入库。批次未落正式身份链、样本已完工及旧动作问题沿用warehouse-inventory.md的INBOUND-001/002，不在本轮修复、重新迁移或恢复旧命令。
3. **中优先级产品风险：不良/报废仓库存可再次被普通业务使用。** LocalStockService.postDocument按入库方向增加quantity和available_quantity；QualityDispositionService没有单独冻结不良库存。WorkorderMaterialService.save只检查所选仓库启用、来源等，不排除DEFECTIVE/SCRAP；ProductionInputSupplyService.calculate只要求仓库在allowedWarehouses、物料/单位匹配且可用量足够。因此在不良/报废仓被纳入某工单允许领料清单且物料匹配时，没有类型门禁阻止其作为正常领料来源。这是源码确认的条件路径，本轮未向真实仓库写入来复现。用户选择“MES按指定仓入库、ERP决定最终处理”不要求新增复杂质量库存维度，但若LOCAL仍可领料，应明确禁止普通领料使用这些仓或制定受控回用入口；不能把仓库标签当作已完成隔离。与导入数据兼容无关。
4. **低优先级结构遗留：旧处置字段已退出当前模型，但物理列仍存在。** parent_id、scrap_qty、processed_qty、recovered_qty、failed_qty在当前处置Entity及正式Mapper读写字段中已移除，业务服务和聚合不再使用；真实库15590条记录在这些列仍保存旧事实。rework_qty则仍在使用，Mapper明确映射成returnedQty，表示当前RETURN_NODE退回数量，不能误删。表/列注释仍含旧“分配、开始、结果”等语义，容易误导后续维护。若治理列名/废弃列，应另做向前迁移及事实保留决策；本次审查不删除表列、不重写既有迁移，也不建议为了旧数据恢复旧代码。

### 表使用清单

| 表 | 当前代码读写与用途 | 本轮perf库数量 | 结论 |
| --- | --- | --- | --- |
| mes_production_defect | ProductionDefectService追加CREATE/REVISE/VOID完整版本；详情、列表、统计、数量投影及引用保护读取 | 15598 | 核心申报账本，在用；申报身份和版本行共用此表，没有另建版本历史表 |
| mes_quality_treatment | QualityTreatmentService追加ASSESS/RETURN_NODE/TRANSFER_WAREHOUSE；QualityDispositionService关联入库；列表、状态、放行、完工和统计读取 | 31194 | 核心处理流水，在用；当前真实库存量主要为旧动作，不等于表废弃 |
| operation_defect_type | OperationDefectTypeService提供工序内配置维护、启停/版本/引用保护；V2申报读取并冻结类型快照 | 0 | 已接入新建申报，当前库未配置，不是无人引用的空表 |
| mes_quality_release | QualityReleaseService追加RELEASE/REVOKE；StockDocumentService.productionBasis及ProductionCompletionService据此校验放行/完工 | 910 | 在用，属于良品后续放行门禁，并非重复的处置流水 |
| mes_production_report（复用） | RETURN_NODE同事务追加负向ADJUSTMENT和source_quality_event_id；后续走正常REPORT恢复产出 | 非本轮新增表 | 在用，不建立第二套开始返工/确认结果表 |
| wm_stock_document、wm_stock_document_line（复用） | 质量转仓创建DEFECT_RECEIPT/SCRAP_RECEIPT及明细，审核后POSTED；处置事件stock_document_id与审核UUID关联 | 两类质量单据0 | 代码已使用，当前库无成功新流程样本 |
| wm_stock_action、wm_inventory（复用） | 审核同事务追加动作并改变余额；失败回滚全部质量、单据和库存写入 | 前轮已确认库存动作4030 | 统一库存执行入口，不另设不良库存流水表 |

早期qc_index、qc_defect已由V7正式迁移删除，本轮真实schema中也不存在。没有发现独立的当前rework任务/结果表。mes_legacy_*属于冻结来源审计与期初数量证据，不是新不良处理表；现有只读引用不在本次整表删除范围。mes_quality_release的可选生成只读Controller默认关闭，不代表正式QualityReleaseService未使用该表。

### 已接通的当前流程

- 申报：V2选择工序不良类型并冻结名称/编码/版本，归属INDEPENDENT/OVERLAP/UNVERIFIED；未核实不能直接消耗处置数量。订正/作废追加新版本，已产生处置事实后禁止随意改写原申报。
- 返回节点：在同一业务事务中追加负向报工修正及RETURN_NODE事件，减少该节点已报量和净不良，恢复正常报工额度；不删除原记录，不再使用旧START/RESULT流程。后续正常报工是当前用户确认的返工记录方式，不要求单独“返工完成”状态。
- 指定仓入库：QualityDispositionService确认可信执行、数量、批次及仓库类型，创建不良/报废入库单及明细，追加关联处置事实，再调用StockDocumentService.approve；LocalStockService唯一执行余额变化。审核失败全事务回滚，原UUID重放不重复入库。审核不是第二人审批，当前质量业务按用户要求自动调用创建+审核。
- 后续放行/完工：pendingDispositionQty及未核实质量参与放行门禁，当前质量/报工依据变化会使旧放行快照失效；不能仅因已写处置流水就认定合格成品已获放行。
- MES边界：已转不良仓后由ERP决定最终处置是用户已确认边界；当前MES没有再花一次原申报额度把同批不良仓库存“转报废”的正式关联命令，不把它列为本轮必须新增的流程。实际ERP5705/5704/1601/1F05/1602创建、主材料/配件退料分单、ERP审核与库存对账均未实现/未实测，不能宣称已覆盖用户参考的整套ERP单据链。

### 本轮验证与未验证

- 重新核对中心绑定TEST_DEV_PERF_20261019 → datasource9 → 192.168.18.25:3306/mes_base_dev_perf_test，模式LOCAL；真实库只执行SELECT/metadata读取，证据保存 `.local/quality-closure-audit-20261022/readonly-evidence.json`。
- 使用现有8081服务只读GET：样本declaration-context为200、types空；treatment为200、UNRESOLVED、保留2条原流水。未新增申报、处理、审核或库存命令，未请求真实ERP，也未重启服务。
- 本轮实际执行 `mvn -B -ntp -pl mes-admin -am test -Dtest=QualityDispositionHttpTest,QualityWorkflowHttpTest,QualityReleaseHttpTest -Dsurefire.failIfNoSpecifiedTests=false`，16项通过，0失败/错误/跳过。日志 `/tmp/mes-quality-closure-audit-tests.log`。覆盖返工后正常报工、转仓/报废入库审核、幂等、并发数量竞争、回滚、权限/来源/批次负例及放行依据失效，环境为隔离H2 HTTP集成，不等同真实perf全链验收。
- 没有为审查新增测试或业务代码，没有运行全量构建、UI交互或真实ERP验证；前轮真实10条全拒绝证据继续有效。本轮没有证明库存隔离风险已被修复，也没有因为旧样本不可处理而要求兼容旧产品。


## 2026-10-22 质量表逐字段清理与结构审查

用户要求清理无效字段，尤其不能因“字段被赋值/搬运到接口”误判仍在使用。本轮从当前页面、V2申报、正式业务命令、Mapper、数量/放行/库存消费者、审计含义及数据库结构逐项核对。四张表职责不同，均保留，不因某表数据为空删除功能；不修复上游批次，不适配旧产品或重开旧返工流程。

### 删除14列、正名1列

| 表 | 字段 | 判定与清理 |
| --- | --- | --- |
| mes_production_defect | discovered_node_id | 当前单节点V2申报恒等affected_node_id，无独立录入/页面展示。旧Facts校验和响应搬运不是当前产品消费者；移除重复节点 |
| mes_production_defect | responsible_node_id | 无当前页面的责任工序维护/展示入口，只剩旧契约搬运；删除。责任人及责任说明仍有实际编辑入口，保留 |
| mes_production_defect | destination_type | 当前恒写SAME_NODE，不代表实际处理；移除字段、固定赋值及依赖此值的准入分支。真实去向由处置命令和流水确定 |
| mes_production_defect | target_node_id | 恒等affected_node_id，无独立目标选择；删除重复节点及名称查询 |
| mes_production_defect | target_warehouse_id | 当前申报强制为空；真正接收仓位于处理流水，删除申报空壳字段 |
| mes_production_defect | destination_remark | 旧计划去向说明，只复制而不影响当前处置；实际处理说明保存在流水reason，删除 |
| mes_production_defect | command_version | 仅被写入V2/空，没有实际读取、分派或业务消费者，删除 |
| mes_quality_treatment | parent_id | 旧ALLOCATE→START→RESULT链关系已退役；删除列及idx_quality_treatment_parent索引 |
| mes_quality_treatment | scrap_qty、processed_qty、recovered_qty、failed_qty | 旧分配报废和独立返工结果确认数量不再读写；删除。报废品实际入库保留transfer_qty、单据类型及库存动作 |
| mes_quality_treatment | confirmed_by | 当前所有正式动作与operator_user_id取同一登录用户，没有独立确认步骤；删除重复身份、姓名查询和响应 |
| mes_quality_treatment | occurred_at | 当前正式动作后台即时处理，与recorded_at取同一服务端时间；删除重复时间，详情/首页统一使用recordedAt |
| mes_quality_treatment | rework_qty → returned_qty | 仍被当前节点退回/数量/进度/统计消费，不能删除；正名为实际语义并保留全部原值，同步Mapper、Example和跨表查询 |

删除申报7列、处理7列；保留处理表target_warehouse_id、stock_document_id，它们是真实转仓来源，与申报表被删除的空壳字段不同。同步删除请求/响应、前端类型、赋值、无效名称查询、旧单值DefectDestination枚举及失效测试/条件；不保留假值、空壳字段或兼容适配。V2申报指纹字段不变；普通申报/订正按精简后的Facts计算指纹，已落库摘要不改写，旧契约的未决请求不得改UUID自动重放。

前轮审查曾按旧Facts接受/校验及响应搬运，将发现/责任节点视为可保留的信息能力；本轮追到实际页面和V2写入后纠正该判断。不能把仅在旧接口和测试中有字段视为当前产品仍需要它。

### 四张表及清理后全部63列

| 表 | 职责与基数 | 结构结论 |
| --- | --- | --- |
| mes_production_defect（22列） | 每项申报的完整版本：CREATE/REVISE/VOID | 申报身份不等于版本行ID；保留不可变账本，不另设版本历史表 |
| mes_quality_treatment（17列） | 每次归属核实、返工返回节点或正式质量入库 | 一项申报可有多次/部分处置，不能合并进申报行覆盖历史 |
| mes_quality_release（11列） | 每个执行的累计良品放行授权与撤回 | 控制良品入库和完工，不是另一份不良处理记录；全部字段有实际业务/追溯用途 |
| operation_defect_type（13列） | 工序直接拥有的可维护不良类型 | 第四张质量相关表；虽然当前验收库0条，申报依赖它，不能当闲置表删除 |

**mes_production_defect逐字段组：**

| 字段 | 实际消费者/保留理由 |
| --- | --- |
| id | 版本行身份、分页和当前页关联汇总 |
| declaration_id、revision_no | 稳定申报身份、最新完整版本、处置版本绑定和并发冲突 |
| record_action、revision_reason | 申报/订正/作废有效性、版本留痕；作废不再贡献当前不良 |
| execution_id、affected_node_id | 所属执行及唯一申报节点；路径/产品/数量校验、返工返回、统计 |
| defect_qty、waste_category、defect_reason | 不良数量、工废/料废类别和真实原因；数量/上限校验、统计及页面展示。原因可与类型名称不同 |
| responsible_user_id、responsibility_remark | 责任认定信息；当前订正表单可编辑，服务核对用户存在并保留历史 |
| request_key、request_fingerprint | 请求幂等和内容冲突检测；摘要不因页面不展示而删除 |
| reported_by、recorded_by | 原申报人和本版本录入人，订正时可能不同；不能因CREATE时相同判为重复 |
| occurred_at、recorded_at | 申报实际发生时间与后台录入时间，允许补报此前发生的不良，时间筛选/审计各有用途。不同于即时处置的同值时间 |
| defect_type_id、defect_type_code_snapshot、defect_type_name_snapshot、defect_type_revision | 类型引用、历史名称/编码/修订快照；删除保护、统计和历史说明，不以当前可改名类型还原旧事实 |

**mes_quality_treatment逐字段组：**

| 字段 | 实际消费者/保留理由 |
| --- | --- |
| id、request_hash | 处置/核实UUID和内容指纹；幂等、库存动作关联及重放校验 |
| declaration_id、declaration_revision | 作用于哪项申报的哪个版本；防止旧处理汇入新版本 |
| execution_id、routing_node_id | 实际执行/处理节点快照；执行批量投影、节点汇总、操作追溯 |
| action_type | ASSESS/RETURN_NODE/TRANSFER_WAREHOUSE；解释数量和行为，未知存量动作只读不恢复执行 |
| attribution_type、related_declaration_id、basis | 独立/重复归属、指向原申报和核实依据，决定扣量/处置准入；仅ASSESS使用，其他动作为空属合法可选 |
| returned_qty、transfer_qty | 两个正式分支实际消耗的处置数量；待处理、净不良、正常报工恢复和进度统计 |
| operator_user_id、recorded_at、reason | 唯一可信操作者、后台执行时间、实际处理原因；详情/首页流水和审计 |
| target_warehouse_id、stock_document_id | 实际接收仓及正式入库单；审核核对仓库/数量/请求身份，单据反查质量来源。返回节点动作为空不是无效字段 |

**mes_quality_release逐字段组：**

| 字段 | 实际消费者/保留理由 |
| --- | --- |
| id、request_hash | 放行/撤回请求幂等，避免重复或改换参数 |
| execution_id、sequence_no | 执行级事实、串行顺序；最新授权读取及expectedReleaseId并发保护 |
| action_type、quantity | RELEASE累计放行额度/REVOKE撤回；成品入库和正式完工门禁，不是单项不良数量 |
| basis_hash | 冻结路径、投入、报工、不良及处理依据；依据变化使旧放行失效，不能只查“有放行记录” |
| reference_id | 被替代/撤回的上一授权身份；不可变授权历史的因果链接，不是重复的状态字段 |
| operator_user_id、recorded_at、reason | 放行/撤回真实操作者、时间、依据；与申报/处置可能不同，独立保留 |

**operation_defect_type逐字段组：**

| 字段 | 实际消费者/保留理由 |
| --- | --- |
| id、operation_id、code、name | 工序所属类型、可维护编码/名称；工序归属和同工序编码唯一性、申报选择 |
| default_waste_category、status | 默认类别/启停，申报覆盖权限、候选过滤及禁用校验 |
| sort | 发现原实现允许编辑但查询只按id DESC，属于“写值却不生效”。本轮改为sort ASC,id ASC，管理列表与申报选择共用，并补HTTP顺序断言，不删除已有排序功能 |
| remark | 类型维护表单可写、编辑可回显的业务说明，有实际用户信息用途 |
| revision | 类型编辑/启停/删除及申报选择的并发修订保护 |
| create_by、create_time、update_by、update_time | 可维护主数据的创建/修改审计，编辑保留创建事实。未在简版列表展示不代表审计无效 |

### 约束、迁移和证据边界

- 保留申报身份/版本唯一约束、请求唯一约束、执行/节点/责任用户/类型索引；处置保留命令主键、声明版本、执行节点、最新流水、入库单索引；放行继续按执行/sequence_no读取并在公共生产锁内序列化。未新增业务factory_id、并行状态表、假默认值或未经审查的物理外键。
- 三张事实表原UPDATE/DELETE拒绝触发器、身份及已保存摘要保留；DDL不删除/更新事实行。旧处理42条发生时间与记录时间不同，原列完整保留在私有备份，当前产品不为旧流程恢复独立时间录入。
- 原计划以target=48.1限定本任务。运行核对发现另一迁移入口已于13:30:29执行V49、13:33:40执行本轮中间版V48.1；本Agent首次正式migrate在13:41:39因V48.1校验和不符被拒绝，未继续写DDL。以Flyway逐行CRC32核对后，恢复已执行V48.1原文（校验和-1377960441），保留其12列清理及数量列正名；最后两列另增V50向前迁移。不执行repair、不改历史记录，不重写V26/V32/V35/V39/V47等已发布迁移。
- V50首次尝试在获取当前schema前遇到连接中断。只读核对迁移记录尚无V50、两列仍存在后才重新发起；13:43:49正式Flyway确认只新应用1个V50，最终validate成功校验51项。V49为先前其他入口执行，本轮不冒充由本任务执行或验收其业务效果。
- 执行目标核实为TEST_DEV_PERF_20261019、datasource9、192.168.18.25:3306/mes_base_dev_perf_test。其他业务库不自动升级。四张表完整mysqldump含结构/数据/触发器，保存在忽略目录 `.local/quality-schema-cleanup-20261022/quality-ledgers-before.sql`，26396291字节、0600权限。保留字段、放行、类型及库存的规范行摘要在before.json，迁移后以相同序列化对比，不仅比较行数。
- schema变化需配套新后端。旧进程仍含被删列的SQL，迁移后应由用户重启；不以恢复旧字段/兼容查询掩盖版本不一致。需要回退时先停止相关业务，通过新向前迁移恢复所需结构并按备份精准恢复原列，不能直接用备份覆盖后来新增事实或伪造Flyway记录。
- 此次结构清理不代表真实新流程已验收：缺不良类型、上游正式批次缺失、旧动作/已完成样本等仍属于先前记录的独立问题；不自动改写数据或制造入库成功。

### 实际验证结果

- 实际执行 `mvn -B -ntp clean verify` 全量成功，845项、0失败/错误、48项环境门控跳过；过程中的旧字段断言已替换/删除，并保留版本、幂等、数量、权限和路径负例。一次无关慢请求日志时序失败在最终全量复跑通过，未修改日志业务代码。最终迁移拆分后执行 `mvn -B -ntp -pl mes-admin -am verify -Dtest=QualityDispositionHttpTest,QualityWorkflowHttpTest,QualityReleaseHttpTest,ProductionDefectHttpTest,WorkOrderProgressHttpTest,QualityV2HttpTest,ProductionQualityQuantityHttpTest,ActivatedProductionHttpTest,ProductionDefectContractTest,QualityTreatmentProjectionTest -Dsurefire.failIfNoSpecifiedTests=false`，58项全部通过并重新打包；安装包中的V48.1/V50原文与正式源码逐字一致；最终安装包执行 `python3 scripts/smoke-packaged.py`，10项隔离烟测全部通过，不连接用户业务库或ERP。
- 前端最终全量74文件/433项通过，`npm run build`成功；保留原有大chunk提示。单独31项定向验证包含处理流水使用recordedAt显示时间、原UUID恢复及申报页面行为。前后端diff空白检查通过，修改的Java经项目IDEA格式化。
- 真实数据库验证已通过：22/17/11/13列均与现行Entity属性完全一致，每列保留COMMENT；6个事实UPDATE/DELETE拒绝触发器存在，旧parent索引已移除。按规范行序列化的SHA256对比，申报15598行、处理31194行、放行910行、类型0行及库存2216行/库存动作4030行的全部保留字段与备份前基线一致，非仅数量汇总一致。
- 使用mes-crud-codegen读取同一真实目标metadata，在隔离忽略目录生成四表完整基础后端审查副本（64个文件，0拒绝），逐项比对生产Entity属性集合为22/17/11/13。没有把生成的普通写CRUD覆盖到不可变账本，也没有修改用户正式生成配置；手改生成归属文件已移除对应MES-CODEGEN标记。
- 证据位于 `.local/quality-schema-cleanup-20261022/`：before-original-retained.json、before.json、after.json、schema-verification.log、codegen-report.json、codegen-properties.json、migrate.log、validate.log及完整SQL备份。完整构建/定向/前端/打包日志在 `/tmp/mes-quality-cleanup-*`。备份和凭证不纳入Git。
- 尚待用户重启后通过运行服务核对精简后的只读接口；没有自行启停8081/5173，没有新增真实申报/处置/入库、没有真实ERP调用。打包隔离烟测不代表真实库业务闭环。本次修改业务代码、前端契约、测试及正式向前迁移；未提交或推送。


### 2026-10-22 用户重启后质量字段清理续验

用户确认“已经重启”后，复用现有8081服务与正式身份，重新核对中心绑定仍为TEST_DEV_PERF_20261019 / datasource9 / 192.168.18.25:3306/mes_base_dev_perf_test / LOCAL；V48.1与V50迁移记录均成功。没有自行启停服务、修改业务数据或调用ERP。

本轮12个只读HTTP请求全部200：工厂上下文、不良列表两页、申报详情、版本历史、处理详情、最新处理流水、放行历史、放行状态、库存单据类型目录、不良统计及工单进度。

- 不良列表offset=0/10、limit=10均返回10行、total=15597，两页版本行ID没有重叠；分页元数据正常。详情/版本历史及列表不再返回discoveredNodeId、responsibleNodeId、destinationType、targetNodeId、targetWarehouseId、destinationRemark、commandVersion及对应废弃名称字段。
- 样本处理详情保留2条旧原始流水，状态UNRESOLVED，不阻塞读取、不伪装已处置；响应已无申报destinationType，事件已无confirmedBy/confirmedByName/occurredAt，保留operatorUserId与非空recordedAt。首页最新处理流水返回固定10条，均使用recordedAt。
- 执行1135的正式放行历史返回1条；放行状态safe=true、basisValid=true，证明运行服务中的放行依据链仍可读取，并非只检查类型定义。没有执行放行/撤回命令。
- 不良统计返回10行、total=426；工单进度返回10行、total=1879，验证引用returned_qty的关联查询在真实服务中正常，未发生旧列不存在等SQL错误。

证据在忽略目录 `.local/quality-schema-cleanup-20261022/runtime/`：binding.json、context.json、requests.json、summary.json及各只读响应。此处关闭上节“待用户重启后的只读接口复核”事项。本次仅追加本文结果，未修改业务代码或业务数据；仍不代表旧样本已成功处置/入库，既有批次准备与不良仓领料隔离问题不因本轮查询通过而关闭。
