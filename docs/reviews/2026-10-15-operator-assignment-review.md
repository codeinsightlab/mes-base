# 工序人员能力与生产节点派工 V1 实施报告

审查及实施日期：2026-10-15。范围：OperationUser、ExecutionAssignment、报工操作资格校验；不含正式报工。源码实现完成，真实全链路业务验收尚未完成。

## A. 当前契约审查

- 中心库 `192.168.18.25:3306/mes_base_dev` 中的 `sys_user.user_id` 为 BIGINT，Java Long；没有新增 Personnel、用户工厂关系表或第二套身份。
- 有效用户须未删除且启用。现有授权为 User → 有效 Role → Menu/Permission：FACTORY 角色仅当前 Factory，PLATFORM 角色的业务授权在选定工厂有效。新目录按 `MenuScopePolicy` 解析完整菜单路径及按钮父级，从启用、非空业务权限菜单中取得授权候选；普通平台管理角色不自动成为业务人员，也不绕过能力规则。不按权限前缀猜业务范围。
- `operation.id`、`routing_node.id/operation_id/routing_id`、`mes_production_execution.id/routing_id` 均为业务库 BIGINT / Java Long。Routing 的每个版本是一条独立记录，Execution.routing_id 引用确切版本，不按路线编码找“最新版”。已发布内容冻结；节点可能重复引用同一 Operation。
- V23 已具备执行路径/领料主链，当前代码以 ProductionExecution 为本轮生产上下文。本轮不使用 BatchRouteNode，不重构 WorkOrder、ProductionBatch 或路径选择；实施期间另一个 Agent 正在收敛其历史查询链，本轮保留那些差异。
- `FactoryContext`、固定业务事务租约与 `businessSqlSessionFactory` 负责业务分库；本轮 Mapper 位于固定 `dev.mesbase.business` 扫描包。中心候选投影 Mapper 位于 `dev.mesbase.system.production`，使用已有 `PlatformPersistence.readOnly` 独立只读窗口，避免在业务事务内开启中心写事务。没有增加 DataSource/SessionFactory/中央 Mapper 清单。
- 规则依据：根 AGENTS、个人工程入口及 Java/前端规范、项目 `mes-crud-codegen` Skill。基础 CRUD 显式 AUTHORIZED，PageHelper 默认20/最大100，HTTP 中 bigint 使用字符串。未调用互联网或依靠旧项目猜测契约。

## B. Schema

迁移：`mes-admin/src/main/resources/db/business/V24__operation_user_execution_assignment.sql`。

| 表 | 字段 |
|---|---|
| mes_operation_user | id BIGINT 自增主键；operation_id BIGINT 非空；user_id BIGINT 非空；remark VARCHAR(500) 可空；create_by VARCHAR(64) 可空；create_time DATETIME 可空 |
| mes_execution_assignment | id BIGINT 自增主键；execution_id / routing_node_id / user_id / assigned_by BIGINT 非空；assigned_at DATETIME 非空；remark VARCHAR(500) 可空 |

索引：两张表均有主键；能力表唯一 `(operation_id,user_id)`、普通 `(user_id)`；派工表唯一 `(execution_id,routing_node_id,user_id)`、普通 `(user_id)`。组合唯一约束依据本次用户对关系去重的明确要求，防止并发重复；没有跨库外键、逻辑删除或额外业务约束。每个字段及表有中文 COMMENT。

真实目标核对：SIMPLE_B / ENABLED → datasource 8 / ENABLED → **192.168.18.25:3306/mes_base_dev_business_simple_b**。迁移前最新 V23，两张目标表均不存在；备份 Flyway 历史后，用项目同版本 Flyway filesystem business migrations、指定 target24 执行 migrate + validate，成功且仅新增 V24。没有修改或执行未知工厂数据库，没有改写既有迁移或其他领域表。新表无 factory_id，Factory 只由基础设施路由。

菜单为 Codegen 正常产出的独立菜单导入 SQL，不是伪造 Flyway 记录。先备份中心 `sys_menu/sys_role_menu` 并检查冲突，执行 `mes-business/docs/generated/production/operator-assignment-menu.sql` 后验证每种权限/菜单类型各一条，保留既有目录/角色关联；没有给任何角色新增授权。本轮两类菜单关联角色数为0。

备份和Schema/菜单核对保存在忽略目录 `.local/operator-assignment-20261015/`；凭证、令牌不进入报告或 Git；本轮临时验收会话已注销并删除本地令牌文件。SQL 已保存、已执行、已核对，非“留给用户执行”。

## C. Codegen 与额外代码

执行配置：`docs/development/operator-assignment-codegen.yaml`，只选择两张新表，generateSync=false，write/fail，不覆盖其他表。

初次正式生成命令：

```sh
./scripts/mes-business-codegen docs/development/operator-assignment-codegen.yaml target/operator-assignment-codegen/report.json
```

真实 metadata 生成结果为 GENERATED / 2 supported / 0 rejected / **40个产物**：30 Java、2 Mapper XML、2 API TS、2 Vue、2菜单JSON、1合并SQL、1权限Markdown。包括两套 Entity/Example/Mapper/XML、Service接口/实现、Controller/权限、Command/Query/Page/VO、测试。随后在忽略目录生成隔离审查副本，保留可审查的报告 `docs/reviews/2026-10-15-operator-assignment-codegen.json`；它记录复现快照，不将快照误认为最终业务适配文件。

业务适配直接修改生成 Service，无额外转发 Facade。语义修改文件均解除 MES-CODEGEN 归属：两个 Service、能力批量接口及控制器、派工请求命令、查询 DTO、测试、前端 API/页面。标准持久化、未改语义的 Controller/VO 保留生成器归属；统一格式化不恢复手工文件标记。没有 OUTPUT_CONFLICT，也没有覆盖其他 Agent 产物；再次整套生成会对手工适配文件明确冲突。

额外代码：`OperationUserDirectory` + 中心跨表只读投影、`OperatorQualificationRules`、`OperatorCandidateService`、`OperatorDirectoryController`、`ReportOperatorEligibilityService`，以及独立前端 `OperatorConfiguration.vue`。所有单表 CRUD/count/批量删除仍复用生成 Mapper/Example，没有 ExtraMapper 或第二套持久化实现。

## D. 业务校验

- 能力：工序必须存在且启用；用户必须启用、未删除并有当前工厂有效业务授权。创建人与时间取可信登录身份和服务端时钟；唯一冲突返回409。编辑只允许原关系的备注，不转移工序/用户。删除只取消能力，不删除用户/工序，不自动删除已有派工。
- 批量：`POST /api/production/mes-operation-user/bind-users`，最多100个不同用户，批量校验中心授权、每条复用生成 insert；同一个业务事务内有任何重复/失败，整批回滚。它是“批量增加”，不是隐含覆盖完整名单。
- 派工：锁顺序复用 `ProductionChainLocks`（WorkOrder → Execution）；校验当前执行、节点及确切 routing_id、工序和用户当前能力。只有 READY/RUNNING 可新增或编辑；未知状态拒绝。assigned_by/assigned_at 不接受客户端输入。编辑不能转移执行/节点/用户。取消派工不要求用户仍有能力，也允许终态取消已有名单，不制造完成记录。
- 统一报工资格：`requireEligible(executionId,routingNodeId,userId)`，先验证上下文/确切节点/当前有效用户/工序能力，再以生成 count 查询节点名单；无名单允许有能力人员，有名单仅允许其中人员。无管理员旁路，查询异常原样失败。关系读取未使用业务缓存，取消能力后下次校验立即拒绝；取消最后一条派工后恢复无名单规则。
- 列表支持按工序/用户、执行/节点/用户分页。派工候选按能力关系分批扫描（每批100），中心授权一次批量筛选；返回 `nextOffset/hasMore` 而非伪造过滤后的总数。当前工艺图最多100节点，超过边界明确拒绝，不静默截断。

## E. 前端

- 工序页面每行“可操作人员”：只读列表、当前工厂授权人员查询、多选批量增加、现有 CrudPage 删除确认。
- 生产执行列表每行、执行身份详情内“工序派工”：读取确切版本节点，以 **routing_node_id** 固定明细上下文，候选来自该 Operation 能力关系；逐次可增加多人。同工序不同节点分别维护名单。
- 复用 request/Auth/CrudPage/Element Plus 和已有页面视觉协议。新增关联列表只作查询入口，写入引导回工序/Execution 上下文，避免裸填关联 ID。
- 两类权限独立：`business:mes:operation:user:{list,query,create,update,delete}` / `business:mes:execution:assignment:{list,query,create,update,delete}`；list 不隐含 create/delete。后端每个 HTTP 方法单独授权。
- ID 全程字符串；能力/派工候选、选中用户、节点、弹窗及请求序列在切厂、撤权、关闭和卸载时清理，迟到响应不回填。关闭配置有未保存人员选择时确认，遮罩、Esc和关闭按钮沿用默认行为；选择框操作不会关闭弹窗。关闭时立即卸载关联列表。
- 真实浏览器新交互未验收：IAB 当前无登录会话；在线测试账号导航中没有任何新增权限，两个新接口实测403。没有为了验收给现有角色扩权或注入模拟浏览器权限。

## F. 验收

详见同目录 `2026-10-15-operator-assignment-verification.json`，命令与证据等级分开记录。09:13常规verify为493项（476通过、17条件跳过，0失败）；指定MySQL另有1项通过记录。后续追加检查期间出现共享target依赖类及smoke日志文件消失，导致补跑失败，未修改业务代码来掩盖；重新串行执行含MySQL的完整verify并复核打包，09:16串行复验成功：493项，477通过、16条件跳过，0失败；指定MySQL专项实际通过，随后smoke 10项通过，格式检查39文件通过。最终结果以验证JSON为准。

- Java21 / 标准 Maven；`mvn -B -ntp verify` 全量成功，未用私有 Maven 镜像或项目本地仓库。
- `MES_OPERATOR_MYSQL_ACCEPTANCE=true mvn -B -ntp verify` 曾在本轮通过；后续仅清理了新查询 DTO 的无效缓存接口及测试旧导入，再执行最终 verify。
- 本轮新增后端32项：规则矩阵21、中心授权真实H2查询4、生成授权/事务契约4、双业务库HTTP集成2、指定MySQL回滚1。
- 双库HTTP/H2测试：真实生成 SQL、Spring 事务和请求授权；中心身份目录为显式 Mock。覆盖多工序/多人、批量事务回滚、重复关系、同工序不同节点、跨厂分库、不合法用户/工序、错误版本、终态、只读权限拒绝写入与字符串ID。
- 中心目录H2：真实 SQL/菜单范围规则，覆盖当前厂角色、平台业务授权、纯平台管理员、外厂、停用/删除/空角色/无权限及即时撤权、分页清理。
- 指定MySQL：复核中心绑定；真实中心用户授权和生成业务SQL，合成执行单元/能力/派工均在明确 rollback-only 事务内，无存量覆盖；复核合成标签计数归零。本环境只有一名符合候选的用户，未把真库单用户验证说成真库多人全矩阵；多人全矩阵由Mock/H2覆盖。
- 前端 Node22.23.1（项目.nvmrc要求22，未改全局默认）：`npm test` 41文件/218项通过，其中本轮新增8项；`npm run build` 通过，仅有构建体积提示。
- IntelliJ项目格式化、两仓 `git diff --check` 通过；打包 smoke 在自有临时回环端口运行，不接开发业务库、不使用测试账号，不操作用户运行服务。
- 在线：8081 `/health`=200；已有本地账号 `/api/session`=200，SIMPLE_B `/api/factory/navigation`=200，新权限数量0；两类新关联接口均403。没有在无授权时尝试写入。真实已授权业务API成功链、浏览器三档宽度、真实新增/取消操作仍未验证。

## G. 并行开发保护

初始后端 main / 5089000，无dirty；前端 main / 732d669，原dirty为：`AGENTS.md`、`src/components/business/RoutingGraphPreview.vue`、`src/views/production/routing/index.vue`、未跟踪 `tests/routingPage.test.ts`。它们全部保留，本轮未修改。

执行期间观察到另一个 Agent 修改 WorkOrder、BatchRouteNode、历史查询、codegen主配置和关联测试，前端修改 workorder API/页面/productionChain测试；本轮均未编辑、格式化、恢复或清理。完整状态快照在忽略证据目录 `backend-status.txt/frontend-status.txt`。

本轮对既有文件仅增加5个 ErrorCode、以及前端 operation/index、ProductionExecutionList、ExecutionIdentityLookup 的低侵入组件入口；其余为本任务新文件。未修改 Operation/Routing/Node/Edge 实体、表、业务逻辑或既有迁移；无 Source Product 写入；本 Agent 未执行提交、推送或部署。最终检查发现共享后端 HEAD 从 5089000 变为 bc71f12，该外部提交纳入了本轮部分新文件；没有撤销或重置该提交，剩余6个适配文件及报告仍在工作区。前端 HEAD 保持732d669。全量测试覆盖当前共享工作区，但别人的功能差异不属于本轮交付。

## H. 后续 Reporting 接入契约

未来 Reporting Service 在 `businessTransactionManager` 的保存事务内，取得可信当前登录用户（如 `SecurityUtils.getUserId()`），先保留原HTTP权限及Factory准入，再调用：

```java
eligibility.requireEligible(executionId, routingNodeId, trustedOperatorUserId);
```

调用通过后才能保存报工；不能用客户端任意 userId 或管理员身份绕过。未来代报另立明确规则。本服务不检查尚不存在的报工HTTP权限，也不以通过资格校验代表有接口权限。

能力/授权与派工读取在当前事务内校验；未来 Reporting 还需定义实际提交、幂等、数量和生产状态规则，以及其事务隔离/并发语义。本轮不承诺管理变更与未来报工写入跨库原子一致。不要求正式报工依赖当前前端候选列表。

Execution 在待执行阶段由既有命令修改 routing_id 后，旧版本派工不会自动迁移到新节点；资格校验按最新确切节点拒绝旧引用。既有Operation/Execution删除或工艺主链的反向引用清理不在本轮改写范围，遗留关联不能形成资格放行；后续如需要删除联动应在相应领域任务内补齐，而不是本轮暗改并行工艺模块。

## I. 最终状态与回退

| 状态 | 结果与边界 |
|---|---|
| SCHEMA_READY | YES，指定SIMPLE_B真实MySQL V24，索引/COMMENT/历史成功记录已核对 |
| CODE_GENERATED | YES，真实metadata 2表40产物，业务适配及生成所有权已审查 |
| API_VERIFIED | 隔离HTTP/H2 YES；在线未授权403 YES；在线有权限成功链 NOT_RUN |
| UI_VERIFIED | 组件测试和构建 YES；真实浏览器新交互/响应式 NOT_RUN |
| BUSINESS_VERIFIED | 统一规则测试及指定MySQL回滚 PARTIAL；真实授权API→UI端到端 NOT_RUN |

未宣称完整业务验收完成。后续应由管理员为指定测试角色明确配置两类最小权限，再使用当前服务/前端完成真实新增、取消与界面验收；若用户服务需加载新代码，由用户重启。保留一名或多名合成验收用户的授权策略由用户决定，本轮没有扩大其访问权限。

回退：先审查并只撤回本轮源码差异；不得删除其他Agent文件。V24和已导入菜单是独立数据库状态，源码回退不等于数据库回退；不自动drop新表、不篡改Flyway历史。需要数据库回退时依据备份和实际新增数据制定正式前向迁移/菜单恢复方案。


## 2026-10-15 跟进：真实前端视觉验收与修复

本节为当日后续证据，不替换上文09:16之前的403、未进行浏览器验收等时间点事实。

### 入口和现场

使用现有5173前端、8081后端，通过应用内浏览器正常登录已有本地验收账号，Factory为SIMPLE_B。未启动、停止或重启服务，未修改角色权限。首次登录显示“服务暂时不可用”，重试后登录和页面读取恢复；没有证明其根因，不把它归因于本轮前端代码或权限。本次实际已可见“工序能力人员”“生产节点派工”和管理按钮，说明授权状态与前述初查时不同；本轮没有扩权。

### 本次发现及修改

1. 工序能力弹窗的输入和选择器使用全宽默认尺寸，造成搜索/选择/保存堆成多行；空态高度叠加后，1280×720下分页被弹窗身体区域裁切。仅在OperatorConfiguration中增加两行flex布局、允许窄屏换行，并缩短该弹窗空态高度；全局CrudPage样式不变。
2. 弹窗没有当前工序/执行身份上下文。Operation、Execution列表及身份查询入口显式传入当前编码/名称，弹窗显示当前工序或执行编号。
3. 指定Execution的节点工序名称快照为空，原页面展示`A · null`。API TypeScript契约改为`name: string | null`，显示“工序名称未记录”，保留真实节点编码及节点ID，不编造名称或修改工艺数据。
4. 无候选人员时原页面只有空选择器。现在区分当前批次无候选和全部无候选，给出加载更多或配置工序能力/核对工厂授权的说明，保存仍受原后端规则约束。
5. filterable选择器的原生input被全局`:focus-visible`叠加出第二个大边框。局部去掉内部input重复outline，实际测得Element Plus外层焦点环仍在，不删除键盘焦点反馈。
6. 两个只读关联查询页把说明放在标题之前。说明移入CrudPage工具栏，回到标题→查询→工具栏→列表的原有页面结构，避免多根页面。

修改文件仅在mes-base-ui：

- `src/components/business/production/OperatorConfiguration.vue`：弹窗布局、上下文、缺失名称、候选空态和局部焦点反馈。
- `src/api/production/operatorConfiguration.ts`：节点名称可空契约。
- `src/views/production/operation/index.vue`、`src/components/business/production/ProductionExecutionList.vue`、`src/components/business/production/ExecutionIdentityLookup.vue`：传入真实上下文标签。
- `src/views/production/mesOperationUser/index.vue`、`src/views/production/mesExecutionAssignment/index.vue`：说明归入工具栏。
- `tests/operatorConfiguration.test.ts`：新增空候选指引/真实上下文、缺失名称两项回归。

上述文件均无需恢复生成归属；没有修改其他Agent的Routing/WorkOrder差异、后端业务代码或数据库。

### 本轮真实浏览器步骤与结果

| 步骤 | 实际操作 | 结果与边界 |
|---|---|---|
| 1 登录与选厂 | 正常登录已有账号，读取当前Factory SIMPLE_B | 重试后正常；首次服务不可用现场保留，根因未确定 |
| 2 工序能力 | 工序1000-01-30/喷砂打开可操作人员，展开真实候选，选中平台管理员100 | 控件及当前工序显示正常；未点击保存，未新增能力关系 |
| 3 未保存关闭 | 点击关闭→取消确认→再次关闭并确认放弃 | 取消保留选择，确认只清理前端草稿，未请求业务删除 |
| 4 执行派工 | EXE-15打开派工，选择节点A/1024，读取候选与关联列表 | 名称不再显示null，真实候选为空时提供能力配置指引；未新增派工 |
| 5 关联查询页 | 打开生产节点派工查询页 | 标题、筛选、说明和列表顺序正常，真实空表状态；能力查询页采用相同已编译结构，未将其单独交互计作验收 |
| 6 响应式与焦点 | 1920×1080、1440×900、1280×720及390×844 | 桌面完整访问分页；390窄屏自然换行，派工长内容可在弹窗内PageDown滚动到分页；选择器保留外层焦点环 |

控制台本轮验收最终error/warn为空。没有用Mock API、修改Pinia/浏览器授权状态、注入业务数据或绕过权限来展示页面。实际能力/派工列表为空，本次未覆盖非空业务列表、业务新增/取消成功链或整套无障碍合规，原BUSINESS_VERIFIED仍为PARTIAL。

### 截图证据

精确截图保存在本机`.local/operator-assignment-20261015/visual-audit/`。已经保存并重新打开检查关键结果；以下为本轮实际浏览器画面，不是生成图或测试DOM。

- `01-login-error.jpg`：首次登录服务不可用现场。
- `03-capability-before.jpg`：修复前能力弹窗多行堆叠及分页裁切。
- `05-assignment-before.jpg`：修复前节点null及无候选说明。
- `07-assignment-list-after-1440.jpg`：查询页层级修复。
- `10-capability-final-1280.jpg`：1280能力弹窗完整分页。
- `13-capability-final-390.jpg`、`14-capability-final-1920.jpg`：选中人员后窄屏/大屏。
- `15-assignment-final-1920.jpg`、`20-assignment-final-1280.jpg`、`21-assignment-final-1440.jpg`：派工桌面复验。
- `18-assignment-narrow-verified.jpg`、`19-assignment-narrow-pagination.jpg`：390派工空态及弹窗内滚动到分页。

16/17号图在视口切换时捕获到未稳定的缩放画面，已放入rejected目录，不用作验收通过证据。没有把加载中、半渲染或错误尺寸截图当作通过。

![修复后的工序能力配置](../../.local/operator-assignment-20261015/visual-audit/10-capability-final-1280.jpg)

![修复后的执行节点派工](../../.local/operator-assignment-20261015/visual-audit/20-assignment-final-1280.jpg)

### 最终检查与状态

Node22.23.1下，`npm test`：41文件/220项通过，0失败（日志`/tmp/mes-visual-final-tests.log`）；`npm run build`通过（日志`/tmp/mes-visual-final-build.log`），仅保留既有chunk体积提示；两仓`git diff --check`通过。仅前端变化，不运行无关Maven构建。未提交、推送、部署、重启服务或写入业务库。

UI现在增加了**真实、已登录、指定工厂、只读交互的视觉证据**；它不代表新增/删除业务成功链完成。浏览器临时视口已恢复，修复后页面保留可供用户检查。回退仅撤销本节列出的前端差异，不动其他Agent修改、先前Schema或菜单。


## 2026-10-15 跟进：工单内统一入口与人员配置闭环

### Change Summary

- 用户要求：执行单元、节点派工进入工单模块；处理可操作人员无法配置导致的闭环问题。
- 当前真实复现：在现有 5173 前端、8081 后端、SIMPLE_B 中，工序“喷砂”(990004)绑定用户100成功，并回读能力关系10。本次未复现持续的绑定接口失败，不能声称定位了之前失败的后端根因。
- 已确认的使用断点：EXE-15 的节点1024实际引用工序1（TEST-OR-OP1），与喷砂990004不是同一工序；原页面无候选时只能提示用户离开去工序列表，且保存缺少明确反馈。派工页现在可直接打开当前节点对应工序的能力配置，保存后刷新候选，保留独立权限；新增明确保存成功提示。
- `OperatorConfiguration.vue`复用同一能力组件嵌套配置；当前节点/当前工序ID显式传入。异步目录姓名到达后重建 Element Plus 列，修正重新打开配置偶发仅显示用户编号；使用真实CrudPage组件补充回归。
- `workorder/index.vue`详情宽度从1200提升到1680px（最多96vw），手机主信息改为一列。关闭工单即失效详情/统计请求并卸载嵌套执行上下文，保留已有历史只读改动。
- 两个旧页面改为工单跳转。菜单JSON与中心菜单均隐藏独立入口，权限码、菜单ID2579/2588及原role100绑定保持；菜单不是停用或删除。工单内沿用已有ProductionExecutionList与节点派工。旧地址跳到工单在真实浏览器确认；workorderId参数精度与自动详情打开由回归测试确认，浏览器地址栏参数保留未作为已验证项目。
- `productionExecution.test.ts`迁到实际被工单使用的执行组件；增加旧地址跳转、工单书签精确ID、关闭时拒绝迟到详情、嵌套能力保存刷新及真实表格姓名渲染测试，未保留旧独立页CRUD测试。

### SQL与真实数据证据

- 执行前重新核对：SIMPLE_B ENABLED → datasource8 ENABLED → 192.168.18.25:3306/mes_base_dev_business_simple_b。
- `.local/operator-assignment-20261015/pre-followup-business.tsv`记录写入前两张关系表为空；查询命令后续尝试读取猜测表名失败，未执行任何写入，随后通过真实metadata确认表名。备份范围为空，无既有关系覆盖。
- `.local/operator-assignment-20261015/menu-merge-before.tsv`保存两条菜单完整记录及角色绑定。`mes-business/docs/generated/production/workorder-execution-entry.sql`已在同实例中心库mes_base_dev执行，visible改1；执行后核对ID/状态/权限/角色绑定不变。同步调整已有两份菜单安装脚本的最终隐藏行为并移除被手工接管文件的生成归属标记。此为菜单维护SQL，无Schema变更，无绕过Flyway迁移。
- 实际UI写入及DB回读：能力10(operation990004,user100)；能力11(operation1,user100)；派工7(execution15,node1024,user100,assignedBy100,2026-10-15 09:54:29)。后两项属于已有QA_CHAIN_20261014_b6251f91验收工单上下文，未修改工单、执行、工序、路线核心事实。保留记录供复验，喷砂能力也保留且如实记录。
- 闭环：工单18 → EXE-15 → 节点1024 → 配置工序1能力 → 关闭子层后立即出现用户100候选 → 保存派工 → 列表回读 → 关闭重开仍存在 → MySQL回读一致。当前有效候选仅用户100，未扩张任何用户角色/权限；多人/拒绝矩阵沿用前轮自动化证据，不将单人真实UI验收冒充所有用例真实验证。

### 验证与视觉证据

- Node22.23.1 `npm test -- --run`：43文件226测试全部通过，日志`/tmp/mes-workorder-followup-tests.log`。
- `npm run build`通过，保留既有大chunk提示，日志`/tmp/mes-workorder-followup-build.log`。两仓`git diff --check`通过。
- 真实浏览器检查1920×1080、1440×900、1280×720、390×844；发现窄屏主信息拥挤后调整一列并重新截图，派工表可横向滚动且操作列固定。浏览器错误/警告读取为空。
- 截图目录`.local/operator-assignment-20261015/visual-audit/`：23-workorder-1440.jpg、24-workorder-1920.jpg、25-workorder-assignment-1280.jpg、26-workorder-assignment-390.jpg、28-workorder-390-final.jpg、30-workorder-assignment-final.jpg。27是微调前窄屏，29是姓名回显修正前画面；最终以28/30为准。
- 本跟进未改Java/Schema，无需重新启动服务；未执行提交、推送、部署。前轮后端493测试结果是此前证据，不冒充本次新执行。正式报工仍在原范围之外。
- 回滚：按任务diff恢复前端/菜单脚本，中心菜单仅将2579/2588 visible恢复到备份值0；保留权限和角色绑定。任何关系清理应只针对上述已记录ID与引用，不批量删除其他数据。本次未进行删除。


## 2026-10-15 链路审查：用户候选、工序关系与中心 Mapper

本节按用户要求进行源码只读审查；只追加报告，没有修改业务代码、配置、SQL或运行服务。本轮未执行HTTP/数据库查询、测试或性能测量；历史测试结果不作为本轮重新通过。

### 两个接口的实际职责

1. `GET /api/production/operator-users?keyword=&offset=0&limit=20`：前端OperatorConfiguration.open→loadOptions→operatorConfiguration.users→request→OperatorDirectoryController.users→OperationUserDirectory.page/read→PlatformPersistence.readOnly→SysMenuMapper.selectMenuList + MenuScopePolicy + OperationUserDirectoryMapper.select→中心库。请求本身需要当前工厂上下文和`business:mes:operation:user:list`。中心查询只返回id/name与分页字段，不返回SysUser完整Entity、密码、角色或联系方式。用户必须status=0、del_flag=0；至少有启用未删除的FACTORY当前厂角色或PLATFORM无厂角色，并持有启用业务菜单/按钮权限。业务菜单按解析后的路径分类，不按权限前缀猜测。关键词匹配账号/昵称。offset>=0、limit1..100、关键词最多100字符。
2. 它没有operationId，不读取mes_operation_user，也不判断某工序能力或排除已经绑定的用户；不能将结果理解为“该工序已具备能力人员”或“尚未绑定人员”。
3. `GET /api/production/mes-operation-user?offset=0&limit=20&operationId=990003`：OperatorConfiguration的CrudPage挂载→api.list，固定注入operationId→MesOperationUserController.list→MesOperationUserServiceImpl.selectPage→生成MesOperationUserExample(operationId=990003)、PageHelper→MesOperationUserMapper XML→当前工厂业务库mes_operation_user。业务Service使用businessTransactionManager只读事务、按id排序，返回关系id/operationId/userId/remark/createBy/createTime及分页信息；没有人员姓名、没有中心库join、没有重新排除已撤权用户。后者保留关系以便管理/取消，写入和报工资格另行校验。
4. 两接口均为只读GET，同为LIST权限；实际新增由`POST /mes-operation-user/bind-users`执行，CREATE权限、业务事务、Operation启用和当前有效用户再次校验、重复关系拒绝。取消用DELETE关系接口。HTTP公共身份/授权基础设施可能额外访问中心库，不能把上述领域查询描述为整条HTTP只执行这些SQL。

### 为什么有两次请求

页面同时需要独立分页的两个数据集：上方用户选择器的有效用户候选，和下方当前工序已配置关系。前者来自中心身份/授权，后者来自工厂业务事实。职责拆分合理，但“跨两个库”并不强制前端必须发两个HTTP；如需优化可由页面查询用例聚合，仍保留两数据集的独立分页。不能通过直接返回所有SysUser或删除后端校验来合并。

### 中心 Mapper 是否可直接注入

`OperationUserDirectoryMapper`本来就是`dev.mesbase.system.production`下的中心Mapper。OwnerMapperRegistration按固定包绑定masterSqlSessionFactory；PersistenceConfiguration把现有masterSqlSessionFactory和masterTransactionManager注入PlatformPersistence，没有新增第三套数据源/会话工厂。区别在访问会话：当前目录通过窗口的session.mapper取得Mapper，而非构造器注入MyBatis-Spring代理。

原因在复用调用链：bindUsers/insert、节点派工校验、ReportOperatorEligibilityService都可能已在businessTransactionManager中，再调用users.validUsers/requireValid。OwnedTransactionManager.checkOwner拒绝在同一活动事务中普通访问另一数据库；`@Transactional(masterTransactionManager,readOnly=true)`本身不会让跨库事务合法，简单改为直接注入会破坏这些调用。现有readOnly使用独立短中心连接、专用SqlSession及SELECT守卫，保留原业务事务和PageHelper上下文，调用后关闭。只读窗口不是中心/业务原子事务。

但是单独的operator-users GET没有业务事务，其中心查询采用直接注入中心Mapper+具名master只读事务也是合理写法，项目BusinessDirectoryService已有实例；不能声称中心Mapper一律不能注入。若改造，应区别纯目录查询与业务事务内校验的边界，不能仅替换构造器或删掉跨库守卫。PersistenceDeepAuditTest中independentPlatformReadAllowedButOrdinaryReadAndWritesRejectedInsideBusiness已有该保护用例源码，本轮未重新执行。

### 审查发现

- P2：已配置人员姓名错误依赖当前候选页。OperatorConfiguration的userId列options只来自当前20条候选及其搜索/更多结果；关系接口只返回userId。配置人员不在该候选页、搜索切换后或用户撤权被过滤时，姓名退化成编号。此前异步到达后重建列只解决刷新时序，没有解决这个契约问题。应由已配置关系的查询结果显式提供姓名/账号展示字段，按当前关系页userIds批量解析，且不混淆显示身份与当前可选资格；未知身份明确显示编号。不能逐行查中心库制造N+1。
- P2：当前存在额外重复加载。open设置visible后CrudPage挂载，onMounted(load)请求关系；loadOptions完成又refresh++，CrudPage的key随之变化并重挂载，再次请求同一关系列表。查询/加载更多候选也会触发无关关系列表重读。为重建人员名称列而重新加载整个表的实现需要收敛；应修复列更新/明确查询模型，候选更新不应触发关系重复请求。此为源码可达路径，不是本轮网络抓包计数。
- 性能观察：每次目录page/requireValid/validUsers都会读取全菜单并重新分类，再分页查询用户。存在明确重复工作，但没有本轮数据量/执行计划/耗时证据，不认定已发生慢查询；可评估复用现有请求内菜单分类，不能引入长期角色权限缓存。

建议优先处理姓名契约和重复关系请求，保留候选与已配置关系的清晰职责。直接注入方式属于可读性/边界调整，不能代替上述真实问题。本轮仅记录审查结果，未实施这些建议。


## 2026-10-15 实施：普通目录查询直接注入中心 Mapper

### Change Summary

用户认可现行用户授权判断，要求普通查询改用注入Mapper并写入AGENTS。

- `OperationUserDirectory.page`改为构造器注入的SysMenuMapper/OperationUserDirectoryMapper，通过`@Transactional(masterTransactionManager,readOnly=true)`读取中心库，不再经过PlatformPersistence.readOnly。
- `requireValid`/`validUsers`仍用于业务事务内部的身份资格校验，保留独立中心读取窗口。两条入口复用同一私有查询逻辑；未新增转发Service、Mapper、SQL、权限缓存或数据源。用户有效性、角色/工厂业务授权、分页和响应契约保持。
- 根AGENTS新增“2026-10-15 用户确认：普通查询使用注入 Mapper”，明确默认构造器注入及具名Spring事务；PlatformPersistence窗口只用于明确的业务事务内跨库metadata读取例外。
- `OperationUserDirectoryTest`由直接new对象升级为真实Spring事务代理+注入Mapper的H2测试，保留原四项资格/撤权/分页/缺Scope覆盖，新增普通查询不调用独立窗口、业务事务内校验保留原连接且普通中心事务被拒绝两项测试。原有MySQL回滚探针适配新构造器，不保留旧构造器兼容分支。
- 保护已有HTTP日志及其他并行工作。本轮没有前端改动、SQL或数据库写入，也没有重启用户服务；当前运行服务在用户重启后才会采用新Java实现。
- 本轮不处理前次审查记录的姓名查询契约、候选导致关系列表重复读取问题；它们不影响本次注入方式调整，仍是已记录待处理项。

### 验证

- 定向命令`mvn -B -ntp -pl mes-business -am test -Dtest=OperationUserDirectoryTest -Dsurefire.failIfNoSpecifiedTests=false`通过，6测试0失败/错误；日志`/tmp/mes-injected-directory-focused.log`。新增Spring测试装配中出现的泛型注册编译问题、缺少mes.business.enabled条件属性问题已修正，不属于生产逻辑失败。
- 运行中的HTTP/UI未宣称新实现已生效；未自行重启服务，未提交、推送或部署。回滚本节关联文件diff即可恢复原普通查询入口，数据库无变化。

- 最终`mvn -B -ntp verify`全模块BUILD SUCCESS，日志`/tmp/mes-injected-directory-verify.log`；按本次日志逐测试类统计505项，其中488通过、17条件跳过，0失败/错误。未设置真实MySQL验收环境开关，不将条件跳过描述为真实库复验。3个修改Java文件执行项目IntelliJ formatter成功，git diff --check通过。


## 2026-10-15 扩展审查：全项目普通查询 Mapper 注入治理

### Change Summary

用户指出上一轮仅改page不足，要求包含read函数并审查项目内全部违反约定的普通查询。本轮覆盖仓库所有模块`src/main`及对应调用测试，扫描PlatformPersistence、readOnly、mapper/getMapper、openSession、SqlSessionFactory；排除target生成隔离产物。未修改外部源产品、数据库或用户服务进程。

| 调用位置 | 审查结论与修改 |
|---|---|
| OperationUserDirectory.read | 普通调用/同中心事务直接使用注入Mapper；只有foreignTransactionActive为真才走独立中心读取。新增已有测试断言requireValid/validUsers普通路径不触发readOnly。 |
| ManagedDatasourceService | 构造器注入ManagedDatasourceMapper/ManagedFactoryMapper；数据源列表/计数/详情、工厂列表/详情/候选、DDL配置读取和节点池对账全部去除普通readOnly回调。分页去除PlatformPersistence.run查询包装，两个分页入口改为具名master只读事务（类去final以允许Spring代理）。写事务和afterCommit退休池仍沿用原机制。 |
| FactoryMetadataCache | 构造器注入RoutingMetadataMapper；正常缓存回源直接查询，只有实际其他数据库事务中回源时保留独立窗口。请求内/Redis缓存和提交后失效语义保持。 |
| SyncCoordinator | 构造器注入MasterSyncMapper；普通state直接查询。assertOwner按运行时事务归属选择普通查询或独立窗口，保留业务事务内处理/提交边界的run-token校验。写入抢占/恢复/续期/完成逻辑未改。 |
| ControlledJobRunner | 构造器注入JobClaimMapper/SysJobMapper；移除运行时服务定位器获取Mapper，state单条查询不再包装PlatformPersistence.run。抢占/释放/日志原事务保留。 |
| SystemConfiguration/FactoryRuntimeConfiguration | Bean参数注入对应中心Mapper，生产装配不手动调用persistence.mapper，不保留旧构造器兜底。 |
| SysRegisterService/SysJobServiceImpl | 已通过注入Service/Mapper访问；PlatformPersistence仅用于注册整单写事务或afterCommit调度协调，不属于普通查询违规，保留。 |
| PlatformAdminCli | 启动前初始化CLI、无Spring业务容器，已有受控事务/Mapper用法保留；不是HTTP普通查询。 |
| PersistenceConfiguration/OwnerMapperRegistration及infrastructure.persistence | 原基础设施Bean装配、固定包扫描、SqlSession代理、独立读取窗口本体保留。没有增加数据源/SqlSessionFactory或放松跨库拒绝。 |
| BusinessCodegenRunner的p.mapper() | 包配置属性getter，非Mapper查询，不改。 |

### 事务边界与规则

- OwnedTransactionManager新增foreignTransactionActive，仅检查当前活动事务与既有OWNER身份是否属于其他管理器，不执行SQL、不改事务绑定，不弱化checkOwner/verifyAccess。
- 普通read不再因为“可能在业务事务内调用”而统一开窗口：无活动事务或当前中心事务使用注入Mapper，实际存在其他数据库活动事务时才开独立metadata窗口。
- 剩余生产readOnly调用共4处，分布在OperationUserDirectory(1)、FactoryMetadataCache(2)、SyncCoordinator.assertOwner(1)，均有运行时foreign判断。独立窗口内仍使用专用session的Mapper，避免误加入外层会话；本轮不宣称跨中心/业务原子性。
- AGENTS第9节补充共享read函数的分支约束，保留用户已认可的账号/角色/当前工厂业务授权判断。

### 测试、保护与验收边界

- 19个任务Java文件通过项目IntelliJ formatter，适配现有构造器测试；无生成文件归属标记被错误保留，未改生成器模板或独立基础CRUD。
- OperationUserDirectoryTest保留真实Spring代理、分页/撤权/跨厂/缺Scope、事务内连接不变与普通查询拒绝跨库事务；普通入口断言扩展到requireValid/validUsers。SyncCoordinatorTest新增普通state不走窗口、外库事务旧run-token拒绝两项。ManagedAdmissionTest通过真实H2查询覆盖数据源/工厂各入口并断言无readOnly调用，继续覆盖配置变更/租约退休。
- `mvn -B -ntp -DskipTests test`编译全部生产及测试源码通过（仅编译，不冒充测试）。最终`mvn -B -ntp verify`全模块成功，当前日志507项=490通过/17条件跳过，0失败/错误；日志`/tmp/mes-query-injection-verify.log`。未开启真实MySQL验收开关。
- 二次扫描生产源码：普通服务及生产Bean装配无persistence.mapper服务定位器；除4处受条件保护的独立读取外，CLI和持久化基础设施为明确保留项。git diff --check通过。
- 执行期间仓库HEAD由bc71f12变为5208ba6（外部提交，包含本轮部分进行中的变更）；本Agent未执行提交/推送，不回退或覆盖该提交。批量适配曾触及两个.local备份的构造调用，已反向恢复，后续修改限定正式src目录。
- 本轮无SQL/配置环境/前端修改，不删除跨库保护。运行中的用户后端未重启，新Java实现需用户重启后生效；不能将本轮H2/构建证据宣称为运行服务已切换。

- 隔离打包冒烟：`/usr/bin/python3 scripts/smoke-packaged.py`通过10项、失败0项，日志`/tmp/mes-query-injection-smoke.log`；使用随机本机端口和脚本自有进程，无开发数据库连接，不改变用户运行服务。


## 2026-10-15 工序能力配置：单框昵称检索多选

### Change Summary

- 根据用户反馈，移除原“查询输入 + 查询按钮 + 多选输入”组合，改成一个 Element Plus 远程检索多选框；输入昵称300ms防抖自动调用既有人员候选接口，选中显示标签，仍由“批量增加能力”明确保存。
- 修改 mes-base-ui 的 `OperatorConfiguration.vue`：换关键词保留已选用户；下拉区分已选择与匹配人员，保留分页加载、失败重试、加载及无匹配反馈；迟到响应及切厂清理继续有效。未改变后端授权或接口。
- `CrudPage.vue` 增加可选 cell 字段插槽，既有默认单元格渲染保持；能力列表通过响应式姓名字典显示标签，不再因每次候选检索重建列表并重复请求。
- 同步适配 `operatorConfiguration.test.ts`、`operatorConfigurationRendering.test.ts`，覆盖跨关键词选择、旧响应拒绝、分页、多选精确字符串ID以及姓名迟到时列表只查询一次。修正模拟响应跨测试残留，不删除原权限/切厂/派工节点测试。

### 验证与边界

- Node22.23.1，最终 `npm test -- --run`：44文件、236测试通过，0失败；`npm run build`（vue-tsc + vite）通过，保留既有大chunk告警；`git diff --check`通过。日志 `/tmp/mes-operator-search-final-test.log`、`/tmp/mes-operator-search-final-build.log`。
- 使用现有5173页面和真实登录会话/SIMPLE_B，在喷砂工序弹窗验证昵称“管理”“陈”“zhang”匹配、切换关键词保留标签、真实两人多选与清除、无匹配反馈和保存按钮禁用；候选为当前有效用户，未模拟页面响应。浏览器控制台无warn/error。
- 截图对比原两输入与新单输入；检查1440×900、1280×800、1920×1080及390×844：桌面单行排列，窄屏保存按钮自然换行，标签可显示。截图 `.local/operator-assignment-20261015/search-select/01-before.jpg` 至 `08-empty.jpg`。1440多选最终效果为 `04-multiselect-1440.jpg`。
- 本轮只试选后清除，未提交能力变更、未新增SQL或修改后端、未启停服务。API保存语义由既有测试覆盖，不将本轮浏览器试选宣称为新增能力保存验收。恢复原视口并保留弹窗供用户复验。


## 2026-10-15 工序编辑合并人员配置与菜单收敛

### Change Summary

- 用户要求移除独立工序能力人员菜单、将工序编辑与人员配置合并。本轮取代前文“编辑/可操作人员两入口”形式：工序列表只保留编辑入口（删除动作仍保留），编辑弹窗宽度按1040px/94vw，包含“工序信息”“可操作人员”两个页签，人员页签按需加载。
- mes-base-ui `operation/index.vue` 组合现有 DocumentFields 与 OperatorConfigurationPanel；抽出人员面板供工序编辑与既有派工弹窗共用，OperatorConfiguration只负责弹窗入口。单框昵称检索、多选、分页、名单维护、权限、字符串ID和迟到响应保护沿用，不新增人员模型或后端API。
- CrudPage增加可选编辑器slot、宽度、只读查看入口、外部未保存/忙状态及保存后保留编辑器配置。默认编辑器继续复用DocumentFields；关闭/遮罩/Esc有未保存内容时确认，取消保留；切厂及卸载清理。工序信息保存后保持弹窗和人员选择，并从更新结果刷新乐观修订号，支持再次保存。
- 工序信息与能力名单分别保存，页面明确提示，不宣称整单原子提交。拥有能力权限但无operation:update的用户可在已有operation:list/query授权下进入同一编辑器，工序字段及保存受只读限制，人员仍按自身权限维护；不扩大后端授权。
- mesOperationUser菜单JSON隐藏并移除手工文件的生成归属标记；旧页面替换为跳转operation，不保留独立CRUD。原派工/能力接口及权限保留。

### 菜单SQL执行与数据核对

- 中心目标核对为192.168.18.25:3306/mes_base_dev；SIMPLE_B绑定启用datasource8，指向同主机mes_base_dev_business_simple_b。本轮不写业务库。
- 保存并执行 `mes-business/docs/generated/production/operation-editor-entry.sql`，仅将菜单2594（production/mesOperationUser/index且对应list权限）的visible从0改为1；菜单编号、parent2512、status0和权限不变，现有3条角色绑定保留。同步追加operator-assignment-menu.sql收尾隐藏规则，防止后续执行该脚本重新显示菜单。该项为已有菜单数据配置，不新增Schema/Flyway迁移。
- 初次mysqldump因配置中的database参数不被支持而失败，连续命令曾先执行隐藏；发现后按刚核实的visible=0恢复原值，使用过滤该参数的临时私有配置成功备份，再以check=True方式执行正式脚本。完整菜单备份 `.local/operator-assignment-20261015/operation-editor/menu-before.sql`，不含用户凭证；没有删除菜单或角色授权。回滚只需按此菜单ID/组件/权限将visible恢复0，并回退本轮前端整合，不回滚其他工作。

### 验证与边界

- `npm test -- --run`：45文件242测试全部通过；新增operationEditor5项覆盖同入口、连续保存修订号与保留人员选择、关闭取消、权限独立、切厂迟到清理；旧入口跳转新增1项。原人员/派工15项继续通过。`npm run build`（vue-tsc与vite）通过，既有大chunk告警保留。两仓库git diff --check通过。最终日志 `/tmp/mes-operation-edit-final-test.log`、`/tmp/mes-operation-edit-final-build.log`。
- 真实5173/现有后端/SIMPLE_B：刷新后独立菜单已消失；喷砂行只有编辑/删除，编辑包含两个页签；读取真实已配置管理员及候选人员，试选陈爱和后关闭出现确认，取消后保留选择。旧地址/production/mesOperationUser实际跳回/production/operation，控制台warn/error为空。
- 首版上下排列截图发现人员列表需向下滚动，已微调为页签。截图 `.local/operator-assignment-20261015/operation-editor/01-editor-1440.jpg` 为改前证据，02-information-1440、03-people-1440、04-people-1280、05-people-1920、06-people-390为改后复核。桌面列表及保存按钮在弹窗内，390窄屏选择器与增加按钮自然换行，表内允许滚动。临时视口已恢复。
- 浏览器试选后清理，未保存工序或新增/删除能力关系；保存行为由Mock组件测试覆盖，不冒充本轮真实写入验收。未修改Java/业务Schema，未重启用户服务，未提交/推送。
