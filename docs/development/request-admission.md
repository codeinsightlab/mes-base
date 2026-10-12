# 认证、请求授权与业务事务准入

2026-10-11 用户决定与实现。SOURCE_PRODUCT_PORT：复用 ktg-mes 的 Redis 身份恢复、逐请求授权及执行上下文复用方向，适配 Base 的随机 Bearer、角色模型、Spring Security 7 与双 SessionFactory；没有复制 JWT、默认库、ERP 字段或外部数据库能力。

## 请求到数据库

1. `RequestBoundaryFilter` 建立 requestId、清理线程 Scope/PageHelper。`SystemTokenFilter` 恢复 Redis 安全 Identity，`SessionAccountMapper.account` 一次读取无密码的账号状态/authRevision、当前部门、有效角色/dataScope，映射到既有 LoginUser 消费者。完整用户查询只在登录、个人改密等明确需要的入口执行。Redis/DB 不可用拒绝准入，基础设施故障为 503。
2. `SecurityFilterChain` 只放行从 PUBLIC MVC 声明生成的路径/HTTP 方法以及内部 ERROR dispatch，其余请求必须认证，非 MVC/静态/未知路径不会匿名放行。生成 PUBLIC 匹配器时同时执行 EndpointCatalog 漏配启动检查，没有重复白名单。system=false 的隔离 fixture 保留 Basic。
3. `FoundationAccessInterceptor` 按完整 URL 判断平台/共享/业务入口。平台管理必须有 PLATFORM 角色；业务解析显式/默认有效厂。`SystemPermissionService` 将当前授权和角色证明放在 request 属性；重复调用复用，下一请求重新加载。平台业务权限合并当前厂 FACTORY 权限，保留当前 deptId/dataScope 和空范围拒绝，无 userId=1 特例。菜单全量分类仅服务导航、角色分配与管理/启动审查，不在业务授权热路径执行。ASYNC redispatch 复用同一已准入身份和绑定；跨线程仍须显式建立业务 Scope，不传 Session。
4. 业务入口从共享 Redis 读取有效绑定（未命中才 JOIN 工厂/数据源），授权通过后建立不可变 `BusinessRequestSnapshot` ，不保留请求级池使用资格，不建池、不取连接。Service 的 `businessTransactionManager` begin 复用快照并懒取固定版本池租约。生产仅 master/business 两组 DataSource/SessionFactory/TM，Mapper 固定包自动扫描。
5. SQL、缓存返回前与提交前 guard 只检查已固定工厂/binding/lease 和会话归属，不重查最新管理配置。同厂 REQUIRED 共享事务，同一 HTTP 请求内 REQUIRES_NEW 复用原快照；非 HTTP 新独立事务重新准入，挂起恢复原工厂/租约；切厂、切库及双向中心业务混用拒绝。Spring 异常、超时与回滚保持。
6. 事务 completion 归还租约，退休池已有租约排空后关闭；请求快照不持有池资源，ASYNC redispatch 使用请求自身保存的绑定；请求边界清理线程状态。A/B 并发请求独立；前端在 fetch 返回及错误/JSON/text/blob body 读取后检查 token/factoryId/revision，旧 A 响应及 401 不得改变 B 页面或新登录态。

## 会话撤销与恢复

会话仍为 32 字节随机 opaque Bearer，Redis 键使用 SHA-256 摘要，不存原 Token。Identity 白名单为用户编号/账号、部门、昵称/头像、登录时间、客户端信息及 authRevision，不含密码、完整 Entity、角色、菜单或权限。`token.expire-time` 绝对有效期保持，请求不续期。

`SystemCacheNamespace` 保持中心 DB 隔离。逻辑键为 `mes:system:session:v2:<digest>` hash、`mes:system:user-sessions:<userId>` sorted set、`mes:system:session-epoch:<userId>` 撤销下界。索引跟随最后会话到期。ISSUE/LOGOUT/REVOKE Lua 顺序化登录、注销与撤销，只访问该用户索引，不扫描全部在线会话。在线监控既有 bounded SCAN 仅用于展示。

禁用/状态改变、删除、改密在同一中心事务增加认证版本并写 `sys_session_revocation`，提交后删除旧版本会话。表内记录表示“尚未持久确认完成”，不一定表示 Redis 撤销失败：提交后成功也不立即开启确认事务，后台再次幂等交付并按版本确认删除。角色/菜单/数据权限修改不改变认证版本，无需重新登录。新版本登录不会被延迟旧交付误删，回滚不交付 Redis。注销/强退删除对应会话和索引项。

DB/Redis 不天然原子：提交后 Redis 失败返回 `SESSION_REVOCATION_PENDING`，明确“账号变更已保存，撤销待重试”，不能误称 DB 回滚。精简 DB fence 在交付失败窗口仍拒绝旧会话；已准入请求不重新验账号版本。后台默认每 5 秒重试，按 userId+revision 条件确认，不覆盖新事件，不依赖 TTL；配置 `mes.system.session-revocation-retry-ms` 可调整。V20 增加 next_attempt_at 与排序索引，每轮最多 100 条到期记录，按 next_attempt_at/user_id 稳定排序；失败按 5/10/20/40/80/160/300 秒退避，最高 300 秒，使用数据库时钟。新 revision 入队重置失败信息与处理时间，同版本或较旧入队不重置；旧失败更新也必须匹配版本。批量入队去重、每 500 ID 参数化读取版本，空集合不操作、空 ID/缺失账号明确失败并由调用方账号事务回滚。既有批量删除未发现集合总上限，此处限制 SQL 批大小，不增加产品限额。Redis 故障明确失败，不伪造注销成功。

新增 `V19__indexed_session_revocation.sql` 及后续 `V20__session_revocation_retry_schedule.sql` 随正常部署按序应用，本轮未执行真实库迁移。v2 不读取旧 Redis 会话格式，部署后已有会话需重新登录；废弃的空 setLoginUser/revokeUser 兼容入口已删除。Lua 验证使用自有单节点 Redis，未验证 Redis Cluster 多键脚本部署。

epoch 无 TTL：它是用户认证版本的发行下界，不能因最后一个会话到期或账号删除就直接消失，否则延迟到达的旧版本发行可能重新写入。数据库准入 fence 是另一重保护，不替代当前 Redis 发行保证。当前每个曾发行/撤销的用户保留一个小键，没有自动删除策略；后续清理必须证明旧发行已终止、账号 ID 不复用且版本不回退，不能随意增加 TTL。本轮不改变此策略或登录会话数量语义。Lua 在大单用户索引下会阻塞其他请求，隔离容量测量与适用边界见实施验收报告新增章节。

## 配置与池

2026-10-11 最新用户决定：配置低频修改，接受短暂 DB/缓存不一致。先前路由代际、epoch、持久失效交付及请求 reservation 已被此决定取代；历史实现与验证保留在实施报告，不是现行规则。账号 authRevision 与会话撤销 outbox 不受影响。

`FactoryMetadataCache` / `RoutingMetadataStore` 使用共享 Redis cache-aside。独立 string 键为 `factory-routing:entry:binding:<factoryId>`、`factory-routing:entry:default`，沿用 SystemCacheNamespace 项目/中心 JDBC URL SHA-256 前缀；不同环境须显式设置不同 `mes.system.cache-namespace`，同一中心库集群保持一致。只存工厂/绑定/数据源状态、连接定义、现有配置 revision 与加密 cipher。未知工厂不写缓存；无默认候选不缓存空值。不存明文密码、池、Connection、Session 或用户权限。

每条 SET 带固定 `mes.datasource.metadata-ttl-seconds`（application.yml 显式 300 秒），GET 不续期，其他键写入不延长旧键有效期。未命中一次 JOIN 获取定义；默认候选筛选和定义同一 JOIN，沿用数字 ID 排序规则。HTTP 请求内复用不可变定义；非 HTTP 独立事务查当前 Redis。账号准入不读取路由代际，也不依赖路由状态表。

所有 Managed 工厂/数据源创建、修改、绑定、解绑、启停和删除在原事务提交后 DELETE 受影响工厂及 default；回滚不 DELETE。淘汰失败记录 ERROR，管理接口返回原操作成功结果，不伪装回滚或鼓励客户端重复提交。不保证其他节点立即生效；允许失效前开始的读取在失效后 SET 旧值，最多再存该条 TTL（从回填发布时起算，不是从管理提交时起算）。没有防回填 CAS、代际、轮询恢复或路由 outbox。Redis 读取/发布失败明确 503，不回退默认厂/本地旧值；过期且 DB 失败同样失败。慢读或长请求可能超出 300 秒墙钟变更窗口，TTL 约束缓存条目而不是请求寿命。

事务 begin 复用请求 binding 并取得 RuntimeRegistry 租约。暖池直接租借；冷池按数据源身份分段锁创建，同一身份并发避免重复创建。物理检查/发布采用短临界区，失败候选关闭一次。仅已有租约保护退休池，包括尚未取 JDBC 连接的租约；请求快照不阻止关闭、不保证退休后还能开启事务或创建历史池。活跃新版本拒绝较旧绑定替换；同版本重新启用创建新池，旧租约仍指向旧实例，池关闭不删除新实例物理证据。新独立事务仍须能取得有效池，否则明确失败。

普通工厂名称等编辑不退休池；绑定/启停/删除与数据源修改提交后退休本节点，其他节点既有 `mes.datasource.reconcile-ms` 定期核对已初始化池（仅池资源清理）。旧池拒绝新租约、已有租约完成并排空关闭。缓存失败或回填期间可能按仍缓存的启用定义重新创建一个活动池，跨节点池核对及 TTL 后读新配置再收敛，不声称即时停用。真正切库的旧事务写旧库/新事务写新库重叠仍需运维安排，不提供在线迁库一致性。

迁移：V21 来自先前未提交的实现，Git 全历史无该文件提交，本轮记录仅 H2 fixture、未真实应用；无法在禁止访问用户 DB 的边界内确认是否有人手工应用。因此按用户要求保留 V21 原内容，不删除/重写可能已应用迁移、不新增 DROP。运行代码与测试不再引用 sys_routing_metadata；若常规迁移应用保留 V21，会创建无运行用途的历史状态表。确认所有目标环境均未应用后才能另行移除文件；已应用表的清理另行授权。本轮无真实迁移。

长任务应以非 HTTP 独立事务/批次建立新准入；一个 HTTP 请求内快照不自动在批次间刷新。跨线程不可隐式继承 Scope 或持久化 Session；本轮没有迁移 Quartz 真实执行器或 OpenAPI 消费者。

## 验证及性能证据

- `SessionAdmissionTest`：安全字段、当前角色/部门、旧版本拒绝、实际禁用/改密/删除 Service 入口、outbox/回滚、提交后 Redis 失败恢复及条件确认。隔离 H2 仅在 test interceptor 将 MySQL SYSDATE() 替换为 CURRENT_TIMESTAMP，不宣称真实 MySQL 方言验收。
- `IndexedRedisSessionTest`：`-Dmes.test.redis=true` 启动并停止自有 loopback Redis，真实 Lua 验证登录/撤销竞争、新版保留、注销和绝对有效期；无生产数据。
- `SystemTokenFilterAdmissionTest`、`RoleScopePermissionTest`：ASYNC 身份复用、Redis 503、授权合并/请求复用/次请求撤权。`FoundationHttpTest` 使用真实嵌入 Tomcat 验证 PUBLIC、默认认证、非 MVC/静态与权限负例。
- `ManagedAdmissionTest`：H2 配置读写，名称编辑不退休、模拟其他节点解绑后新准入拒绝、旧租约排空。`DedicatedDatabasePersistenceTest`/`PersistenceDeepAuditTest`：固定身份、缓存 guard、并发 A/B、跨库、挂起恢复、独立新事务、回滚/清理、退休后首次取连接、owner 限制及关闭失败恢复。
- `ProWorkorderRequestBudgetTest`：真实 filter/interceptor、工单 Controller/Service/Mapper 与分页 SQL；中心/业务为独立 H2，Redis payload 为 synthetic stub，真实 Spring TransactionTemplate 包围事务。25 次循环，5 次预热，20 次计时。每次暖请求 **2 次中心 SELECT + 2 次业务 SELECT**，没有用户密码读取。中心构成为账号/角色 1、当前权限 1；session read 1 + routing GET 1，事务无 metadata I/O。首次元数据 miss 另加 JOIN 1 与 SET with TTL 1；暖池分页 2 次业务 SQL。无显式选厂时偏好读取可能增加 1 次中心 SQL，default 缓存未命中额外一次候选绑定 JOIN。冷池建池/物理证据探测另计，不把网络连接成本算进暖池预算。结果写 `target/validation/pro-workorder-request-budget.json`。

该工单测试无 HTTP socket、无真实 Redis/MySQL，不是整体 SecurityFilterChain 的性能实测；完整安全链另由 HTTP 测试覆盖。原延迟报告为开发环境 HTTP 16+2、约一秒；新 fixture 为 2+2，只能证明重复查询减少，不能将不同环境耗时直接计算为性能提升。

未验证：真实多实例 MySQL、新旧版本同环境 HTTP 延迟对测、Redis Cluster、真实浏览器视觉回归。没有重启用户服务、访问生产、执行真实迁移或大范围压测。

## 2026-10-11 路由缓存补充验收入口

`RoutingMetadataRedisTest` 使用两个独立客户端和自有临时 Redis，覆盖独立 TTL、命中不续期、其他写入不延长旧值、提交淘汰/回滚不淘汰、淘汰失败后过期、允许旧回填、未知 ID 不入缓存、Redis 读/发布故障、过期后 DB 故障。中心事务为 H2，不能替代真实 MySQL。

`ManagedColdPoolTest` 执行实际 Managed resolver 与准备锁，Druid/JDBC probe mock：16 并发冷请求建 1 池，旧冷快照不能替换活跃新池，同版本新旧池按租约独立排空，探测失败关闭。`RequestSnapshotTransactionTest` 通过 H2 验证已开始事务退休后继续、旧快照不保证新事务、并发 A/B、回滚/挂起恢复/切厂拒绝/异常清理。既有双向跨库及缓存 guard 专项保留。隔离证据不是生产延迟结论。

业务授权按 URL，权限集合可含平台角色持有的平台代码，但业务接口需其声明的动作码；平台管理仍要求 PLATFORM 角色。为避免权限码被两个范围重复解释，启动检查 AUTHORIZED URL、全菜单（按钮继承父节点）；跨范围同码或 FACTORY 已持有平台菜单时拒绝启动并指出编号/权限码。菜单编辑在中心事务内同样审查，失败回滚；角色分配保留原工厂菜单约束。未自动删旧授权，未访问真实数据审查现存错误项。部署前需人工核对/修正错误授权，不能把过去静默剔除当现有合法权限。

## 2026-10-12 工厂上下文失效后的交互恢复

业务范围请求出现缺少工厂、工厂不存在/停用、业务运行时未配置时，HTTP 边界统一返回
`422 / FACTORY_SELECTION_REQUIRED`，安全消息为“当前工厂不可用，请重新选择工厂”。
内部 `FoundationException` 仍保留原码，日志保留原异常原因与 requestId；平台管理错误、
普通业务拒绝、权限拒绝及路由基础设施 503 不转为选厂错误。

前端在响应体读取后确认身份/工厂/revision 仍匹配，再触发恢复。保留有效身份、有效期、
平台菜单与权限，清空失效工厂 ID 和业务菜单/权限，递增上下文版本使旧页面/响应失效，
跳转 `/select-factory`。登录和刷新登录态过程中同样恢复，不因这类错误删除 Token。
重新选厂成功返回工作台，失败的业务请求不自动重放；无候选时可进入平台/个人工作台。
选厂和首页复用 `/api/foundation/factories`，通过同一 Store 方法读取候选；候选 SQL 在分页前
过滤为工厂 ENABLED 且存在已绑定的 ENABLED 数据源。平台管理列表不做此过滤。

不改变路由缓存固定 TTL/提交淘汰策略，不引入默认工厂或数据库兜底；被停用信息尚在允许的
缓存窗口内时仍遵守既有一致性约定，这项改动负责服务端已拒绝后的恢复交互。
