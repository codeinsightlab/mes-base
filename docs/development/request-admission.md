# 认证、请求授权与业务事务准入

2026-10-11 用户决定与实现。SOURCE_PRODUCT_PORT：复用 ktg-mes 的 Redis 身份恢复、逐请求授权及执行上下文复用方向，适配 Base 的随机 Bearer、角色模型、Spring Security 7 与双 SessionFactory；没有整块复制 JWT、默认库或 Redis 元数据缓存。

## 请求到数据库

1. `RequestBoundaryFilter` 建立 requestId、清理线程 Scope/PageHelper。`SystemTokenFilter` 恢复 Redis 安全 Identity，`SessionAccountMapper.account` 一次读取无密码的账号状态/authRevision、当前部门及有效角色/dataScope，映射到既有 LoginUser 消费者。完整用户查询只在登录、个人改密等明确需要的入口执行。Redis/DB 不可用拒绝准入，基础设施故障为 503。
2. `SecurityFilterChain` 只放行从 PUBLIC MVC 声明生成的路径/HTTP 方法以及内部 ERROR dispatch，其余请求必须认证，非 MVC/静态/未知路径不会匿名放行。生成 PUBLIC 匹配器时同时执行 EndpointCatalog 漏配启动检查，没有重复白名单。system=false 的隔离 fixture 保留 Basic。
3. `FoundationAccessInterceptor` 按完整 URL 判断平台/共享/业务入口。平台管理必须有 PLATFORM 角色；业务解析显式/默认有效厂。`SystemPermissionService` 将当前授权、角色证明、全菜单 URL 分类放在 request 属性；重复调用复用，下一请求重新加载。平台业务权限合并当前厂 FACTORY 权限，保留当前 deptId/dataScope 和空范围拒绝，无 userId=1 特例。ASYNC redispatch 复用同一已准入身份；跨线程仍须显式建立业务 Scope，不传 Session。
4. Service 的 `businessTransactionManager` begin 重新 resolve 当前工厂/连接配置并租借固定版本池。生产仅 master/business 两组 DataSource/SessionFactory/TM，Mapper 固定包自动扫描。
5. SQL、缓存返回前与提交前 guard 只检查已固定工厂/binding/lease 和会话归属，不重查最新管理配置。同厂 REQUIRED 共享事务，REQUIRES_NEW 新准入，挂起恢复原工厂/租约；切厂、切库及双向中心业务混用拒绝。Spring 异常、超时与回滚保持。
6. completion 归还租约，退休池最后使用者归还后关闭；请求边界清理线程状态。A/B 并发请求独立；前端在 fetch 返回及错误/JSON/text/blob body 读取后检查 token/factoryId/revision，旧 A 响应及 401 不得改变 B 页面或新登录态。

## 会话撤销与恢复

会话仍为 32 字节随机 opaque Bearer，Redis 键使用 SHA-256 摘要，不存原 Token。Identity 白名单为用户编号/账号、部门、昵称/头像、登录时间、客户端信息及 authRevision，不含密码、完整 Entity、角色、菜单或权限。`token.expire-time` 绝对有效期保持，请求不续期。

`SystemCacheNamespace` 保持中心 DB 隔离。逻辑键为 `mes:system:session:v2:<digest>` hash、`mes:system:user-sessions:<userId>` sorted set、`mes:system:session-epoch:<userId>` 撤销下界。索引跟随最后会话到期。ISSUE/LOGOUT/REVOKE Lua 顺序化登录、注销与撤销，只访问该用户索引，不扫描全部在线会话。在线监控既有 bounded SCAN 仅用于展示。

禁用/状态改变、删除、改密在同一中心事务增加认证版本并写 `sys_session_revocation`，提交后删除旧版本会话。表内记录表示“尚未持久确认完成”，不一定表示 Redis 撤销失败：提交后成功也不立即开启确认事务，后台再次幂等交付并按版本确认删除。角色/菜单/数据权限修改不改变认证版本，无需重新登录。新版本登录不会被延迟旧交付误删，回滚不交付 Redis。注销/强退删除对应会话和索引项。

DB/Redis 不天然原子：提交后 Redis 失败返回 `SESSION_REVOCATION_PENDING`，明确“账号变更已保存，撤销待重试”，不能误称 DB 回滚。精简 DB fence 在交付失败窗口仍拒绝旧会话；已准入请求不重新验账号版本。后台默认每 5 秒重试，按 userId+revision 条件确认，不覆盖新事件，不依赖 TTL；配置 `mes.system.session-revocation-retry-ms` 可调整。V20 增加 next_attempt_at 与排序索引，每轮最多 100 条到期记录，按 next_attempt_at/user_id 稳定排序；失败按 5/10/20/40/80/160/300 秒退避，最高 300 秒，使用数据库时钟。新 revision 入队重置失败信息与处理时间，同版本或较旧入队不重置；旧失败更新也必须匹配版本。批量入队去重、每 500 ID 参数化读取版本，空集合不操作、空 ID/缺失账号明确失败并由调用方账号事务回滚。既有批量删除未发现集合总上限，此处限制 SQL 批大小，不增加产品限额。Redis 故障明确失败，不伪造注销成功。

新增 `V19__indexed_session_revocation.sql` 及后续 `V20__session_revocation_retry_schedule.sql` 随正常部署按序应用，本轮未执行真实库迁移。v2 不读取旧 Redis 会话格式，部署后已有会话需重新登录；废弃的空 setLoginUser/revokeUser 兼容入口已删除。Lua 验证使用自有单节点 Redis，未验证 Redis Cluster 多键脚本部署。

epoch 无 TTL：它是用户认证版本的发行下界，不能因最后一个会话到期或账号删除就直接消失，否则延迟到达的旧版本发行可能重新写入。数据库准入 fence 是另一重保护，不替代当前 Redis 发行保证。当前每个曾发行/撤销的用户保留一个小键，没有自动删除策略；后续清理必须证明旧发行已终止、账号 ID 不复用且版本不回退，不能随意增加 TTL。本轮不改变此策略或登录会话数量语义。Lua 在大单用户索引下会阻塞其他请求，隔离容量测量与适用边界见实施验收报告新增章节。

## 配置与池

没有 Redis metadata 缓存。新独立事务直接读取中心工厂与数据源，不依赖缓存删除/generation/TTL；中心故障准入失败，不回退过期元数据或默认库。暖池查询位于同步锁之外，只有冷准备/发布串行并重查配置。

本节点管理提交后退休旧池；其他节点新事务同样直接查中心。每 5 秒批量核对已初始化池，停厂/解绑/停源/版本改变则退休，普通工厂名称编辑不退休；`mes.datasource.reconcile-ms` 只调整资源回收间隔，不是准入有效期。中心故障时核对失败，旧池可能暂存，但正常新事务仍须中心准入。退休池物理库证据保留到关闭，同一身份旧租约未排空时不得移交另一厂。

已有连接及已有租约但尚未取连接者都可沿原绑定完成；仅持有 FactoryScope 而无事务租约者必须重新准入。旧池关闭失败保留跟踪、日志与物理证据并重试，不中止已提交业务、不误关已发布新池。真正切库允许旧事务写旧库、新事务写新库的重叠，本轮不承诺在线迁库一致性。

长任务应以独立事务/批次重新准入，不能把整个任务当短请求。本轮没有迁移 Quartz 真实执行器或 OpenAPI 消费者。

## 验证及性能证据

- `SessionAdmissionTest`：安全字段、当前角色/部门、旧版本拒绝、实际禁用/改密/删除 Service 入口、outbox/回滚、提交后 Redis 失败恢复及条件确认。隔离 H2 仅在 test interceptor 将 MySQL SYSDATE() 替换为 CURRENT_TIMESTAMP，不宣称真实 MySQL 方言验收。
- `IndexedRedisSessionTest`：`-Dmes.test.redis=true` 启动并停止自有 loopback Redis，真实 Lua 验证登录/撤销竞争、新版保留、注销和绝对有效期；无生产数据。
- `SystemTokenFilterAdmissionTest`、`RoleScopePermissionTest`：ASYNC 身份复用、Redis 503、授权合并/请求复用/次请求撤权。`FoundationHttpTest` 使用真实嵌入 Tomcat 验证 PUBLIC、默认认证、非 MVC/静态与权限负例。
- `ManagedAdmissionTest`：H2 配置读写，名称编辑不退休、模拟其他节点解绑后新准入拒绝、旧租约排空。`DedicatedDatabasePersistenceTest`/`PersistenceDeepAuditTest`：固定身份、缓存 guard、并发 A/B、跨库、挂起恢复、独立新事务、回滚/清理、退休后首次取连接、owner 限制及关闭失败恢复。
- `ProWorkorderRequestBudgetTest`：真实 filter/interceptor、工单 Controller/Service/Mapper 与分页 SQL；中心/业务为独立 H2，Redis payload 为 synthetic stub，真实 Spring TransactionTemplate 包围事务。25 次循环，5 次预热，20 次计时。每次暖请求 **6 次中心 SELECT + 2 次业务 SELECT**，没有用户密码读取。中心构成为账号/角色 1、入口工厂 1、全菜单 1、权限 1、事务绑定 2。结果写 `target/validation/pro-workorder-request-budget.json`。

该工单测试无 HTTP socket、无真实 Redis/MySQL，不是整体 SecurityFilterChain 的性能实测；完整安全链另由 HTTP 测试覆盖。原延迟报告为开发环境 HTTP 16+2、约一秒；新 fixture 为 6+2，只能证明重复查询减少，不能将不同环境耗时直接计算为性能提升。

未验证：真实多实例 MySQL、新旧版本同环境 HTTP 延迟对测、Redis Cluster、真实浏览器视觉回归。没有重启用户服务、访问生产、执行真实迁移或大范围压测。
