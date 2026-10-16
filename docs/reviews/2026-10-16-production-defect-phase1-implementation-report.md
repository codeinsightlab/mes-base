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
