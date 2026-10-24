# 认证、请求授权与业务事务准入

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

epoch 无 TTL：它是用户认证版本的发行下界，不能因最后一个会话到期或账号删除就直接消失，否则延迟到达的旧版本发行可能重新写入。数据库准入 fence 是另一重保护，不替代当前 Redis 发行保证。当前每个曾发行/撤销的用户保留一个小键，没有自动删除策略；后续清理必须证明旧发行已终止、账号 ID 不复用且版本不回退，不能随意增加 TTL。Lua 在大单用户索引下会阻塞其他请求，部署时需要按会话规模评估容量。

## 配置与池

路由配置低频修改，允许下述有界缓存不一致。账号 authRevision 与会话撤销 outbox 独立运行。

`FactoryMetadataCache` / `RoutingMetadataStore` 使用共享 Redis cache-aside。独立 string 键为 `factory-routing:entry:binding:<factoryId>`、`factory-routing:entry:default`，沿用 SystemCacheNamespace 项目/中心 JDBC URL SHA-256 前缀；不同环境须显式设置不同 `mes.system.cache-namespace`，同一中心库集群保持一致。只存工厂/绑定/数据源状态、连接定义、现有配置 revision 与加密 cipher。未知工厂不写缓存；无默认候选不缓存空值。不存明文密码、池、Connection、Session 或用户权限。

每条 SET 带固定 `mes.datasource.metadata-ttl-seconds`（application.yml 显式 300 秒），GET 不续期，其他键写入不延长旧键有效期。未命中一次 JOIN 获取定义；默认候选筛选和定义同一 JOIN，沿用数字 ID 排序规则。HTTP 请求内复用不可变定义；非 HTTP 独立事务查当前 Redis。账号准入不读取路由代际，也不依赖路由状态表。

所有 Managed 工厂/数据源创建、修改、绑定、解绑、启停和删除在原事务提交后 DELETE 受影响工厂及 default；回滚不 DELETE。淘汰失败记录 ERROR，管理接口返回原操作成功结果，不伪装回滚或鼓励客户端重复提交。不保证其他节点立即生效；允许失效前开始的读取在失效后 SET 旧值，最多再存该条 TTL（从回填发布时起算，不是从管理提交时起算）。没有防回填 CAS、代际、轮询恢复或路由 outbox。Redis 读取/发布失败明确 503，不回退默认厂/本地旧值；过期且 DB 失败同样失败。慢读或长请求可能超出 300 秒墙钟变更窗口，TTL 约束缓存条目而不是请求寿命。

事务 begin 复用请求 binding 并取得 RuntimeRegistry 租约。暖池直接租借；冷池按数据源身份分段锁创建，同一身份并发避免重复创建。物理检查/发布采用短临界区，失败候选关闭一次。仅已有租约保护退休池，包括尚未取 JDBC 连接的租约；请求快照不阻止关闭、不保证退休后还能开启事务或创建历史池。活跃新版本拒绝较旧绑定替换；同版本重新启用创建新池，旧租约仍指向旧实例，池关闭不删除新实例物理证据。新独立事务仍须能取得有效池，否则明确失败。

普通工厂名称等编辑不退休池；绑定/启停/删除与数据源修改提交后退休本节点，其他节点既有 `mes.datasource.reconcile-ms` 定期核对已初始化池（仅池资源清理）。旧池拒绝新租约、已有租约完成并排空关闭。缓存失败或回填期间可能按仍缓存的启用定义重新创建一个活动池，跨节点池核对及 TTL 后读新配置再收敛，不声称即时停用。真正切库的旧事务写旧库/新事务写新库重叠仍需运维安排，不提供在线迁库一致性。

长任务应以非 HTTP 独立事务/批次建立新准入；一个 HTTP 请求内快照不自动在批次间刷新。跨线程不可隐式继承 Scope 或持久化 Session。
