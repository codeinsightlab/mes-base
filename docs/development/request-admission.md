# 认证、请求授权与业务事务准入

## 请求到数据库

1. `RequestBoundaryFilter` 建立 requestId、清理线程 Scope/PageHelper。`SystemTokenFilter` 恢复 Redis 安全 Identity，`SessionAccountMapper.account` 一次读取无密码的账号状态/authRevision、当前部门、有效角色/dataScope，映射到既有 LoginUser 消费者。完整用户查询只在登录、个人改密等明确需要的入口执行。Redis/DB 不可用拒绝准入，基础设施故障为 503。
2. `SecurityFilterChain` 只放行从 PUBLIC MVC 声明生成的路径/HTTP 方法以及内部 ERROR dispatch，其余请求必须认证，非 MVC/静态/未知路径不会匿名放行。生成 PUBLIC 匹配器时同时执行 EndpointCatalog 漏配启动检查，没有重复白名单。system=false 的隔离 fixture 保留 Basic。
3. `FoundationAccessInterceptor` 按完整 URL 判断平台/共享/业务入口。平台管理必须有 PLATFORM 角色；业务解析显式/默认有效厂。`SystemPermissionService` 将当前授权和角色证明放在 request 属性；重复调用复用，下一请求重新加载。平台业务权限合并当前厂 FACTORY 权限，保留当前 deptId/dataScope 和空范围拒绝，无 userId=1 特例。菜单全量分类仅服务导航、角色分配与管理/启动审查，不在业务授权热路径执行。ASYNC redispatch 复用同一已准入身份和绑定；跨线程仍须显式建立业务 Scope，不传 Session。
4. 业务入口从共享 Redis 读取有效绑定（未命中才 JOIN 工厂/数据源），授权通过后建立不可变 `BusinessRequestSnapshot` ，不保留请求级池使用资格，不建池、不取连接。Service 的 `businessTransactionManager` begin 复用快照并懒取固定版本池租约。生产仅 master/business 两组 DataSource/SessionFactory/TM，Mapper 固定包自动扫描。
5. SQL、缓存返回前与提交前 guard 只检查已固定工厂/binding/lease 和会话归属，不重查最新管理配置。同厂 REQUIRED 共享事务，同一 HTTP 请求内 REQUIRES_NEW 复用原快照；非 HTTP 新独立事务重新准入，挂起恢复原工厂/租约；切厂、切库及双向中心业务混用拒绝。Spring 异常、超时与回滚保持。
6. 事务 completion 归还租约，退休池已有租约排空后关闭；请求快照不持有池资源，ASYNC redispatch 使用请求自身保存的绑定；请求边界清理线程状态。A/B 并发请求独立；前端在 fetch 返回及错误/JSON/text/blob body 读取后检查 token/factoryId/revision，旧 A 响应及 401 不得改变 B 页面或新登录态。

## 会话撤销与恢复

内部Bearer采用`<sessionId>.<secret>`：sessionId是UUID公开定位符，secret仍为32字节随机值的Base64URL形式。Redis会话Key按sessionId定位；认证时对secret计算SHA-256摘要，并与会话Hash的`secretDigest`字段以常量时间比较。仅知道sessionId不能完成认证。原始Bearer、secret、密码及摘要不放入Key、日志或本指南。Identity白名单为用户编号/账号、部门、昵称/头像、登录时间、客户端信息及authRevision，不含密码、完整Entity、角色、菜单或权限。`token.expire-time`绝对有效期保持，请求不续期。

物理Key均以公共前缀`mes:<namespace>:center:<host>:<port>:<database>:`开头。`namespace`来自现有`mes.system.cache-namespace`，默认`default`；非空标签符合`[A-Za-z][A-Za-z0-9_-]{0,63}`。中心身份从JDBC URL提取host/port/database，不包含连接凭据和参数。Host大小写及末尾点规范化，localhost/127.0.0.1/::1归一为localhost，并补数据库驱动默认端口。DNS别名不会自动解析；同部署所有实例必须使用相同规范化host/port/database与namespace，不同中心库即使标签相同仍隔离。

Key段统一采用UTF-8字节百分号转义：仅`A-Z a-z 0-9 . _ -`原样保留，其余使用大写`%HH`；转义可逆，并覆盖分隔符、转义符、Redis glob与花括号。业务缓存的Factory/ID保持原文可读；资源权限前缀中段间冒号改为点号，资源身份位于`{factory:<factoryId>:resource:<resource>}` Cluster同槽标记内，以支持详情代际CAS并按Factory/资源分槽。资源末尾边界标记保证相邻/嵌套资源失效不重叠。新增Redis Key应复用`SystemCacheNamespace.key(...)`和现有业务/详情构造器，不在调用点自行拼接、清洗或截断定位值。

当前认证Key结构（下列均在中心前缀内）：

| 用途 | 逻辑Key | 值/生命周期 |
| --- | --- | --- |
| 内部会话 | `mes:system:{sessions}:session:v3:<sessionId>` | Hash，存Identity、authRevision与secret摘要；绝对会话TTL不变 |
| 用户会话索引 | `mes:system:{sessions}:user-sessions:<userId>` | ZSET，成员为完整物理会话Key，随最后一条会话过期 |
| 用户认证版本下界 | `mes:system:{sessions}:session-epoch:<userId>` | 无TTL，保留发行/撤销下界 |
| 登录失败计数 | `mes:system:retry:<canonicalUserName>` | 首次失败时设置锁定TTL，默认10分钟；账号名按持久化规范名并安全转义 |
| 验证码 | `mes:system:captcha:<uuid>` | 一次性值，默认2分钟 |
| OpenAPI客户端Token索引 | `open-api:<namespace>:v1:{tokens}:client:<clientId>` | JSON，存tokenId及Bearer用于并发复用；按Token TTL过期 |
| OpenAPI Token | `open-api:<namespace>:v1:{tokens}:token:<tokenId>` | JSON记录Token摘要、clientId及credentialVersion；默认7200秒 |
| OpenAPI令牌来源限流 | `open-api:<namespace>:v1:rate:token-ip:<peer>` | 原子计数，默认窗口60秒 |
| OpenAPI取Token限流 | `open-api:<namespace>:v1:rate:token-client:<clientId>` | 原子计数，默认窗口60秒 |
| OpenAPI业务接口限流 | `open-api:<namespace>:v1:rate:client:<clientId>:api:<apiCode>` | 原子计数，默认窗口60秒 |

`open-api:<namespace>:v1:`中的namespace来自现有OpenAPI配置，默认`base`；peer、clientId和apiCode均按通用段规则转义。限流Key不包含Token或摘要，窗口与计数上限仍由现有OpenAPI配置控制。

其他中心缓存沿用相同物理前缀，逻辑Key包括：

- `platform:resource:{sys_user}:detail:<userId>`及`platform:resource:{sys_user}:detail-version`：用户安全详情与回填代际，共用`{sys_user}`同槽标记；与业务目录和平台用户CRUD共享同一详情缓存及失效入口。
- `committed-v1:sys_dict:<type>`：字典类型；`committed-v1:sys_config:<code>`：系统参数编码。系统设置TTL为30秒，账号安全开关仍直接读数据库。
- `factory-routing:entry:binding:<factoryId>`与`factory-routing:entry:default`：Factory路由定义，默认TTL为300秒，按变更事务提交后删除。
- `autocode:{rules:v1}:snapshot`与`autocode:{rules:v1}:version`：完整编码规则快照及回填代际，共用`{rules:v1}`同槽标记，快照TTL为3600秒；流水计数仍在数据库，不在Redis。

新增系统Key应在公共`SystemCacheNamespace`下集中构造，对每个动态定位段使用`RedisKeySegment.encode`；需模式失效时先验证只覆盖目标资源。不得自行拼第二套根前缀、哈希业务ID或把完整JDBC URL放入Key。版本代际、session-epoch、撤销数据与普通可重建缓存的生命周期不同，不能统一加TTL或清空。

OpenAPI Bearer形如`oa_<tokenId>.<secret>`，tokenId是UUID，随机secret单独验证；Redis Key只含tokenId，token摘要留在Value。`{factory:...}`、`{sys_user}`、`{rules:v1}`、`{sessions}`与`{tokens}`是Redis Cluster同槽标记，不是Hash摘要；同一Lua脚本涉及的业务/用户详情代际与详情Key、编码快照/代际、会话/epoch/索引及OpenAPI令牌动态Key都保持在对应同槽标记下。不能删除标签或把这些Lua多Key访问拆成不同slot。

`SystemOnlineController`仅扫描新版会话前缀并通过sessionId读取展示Identity；sessionId只用于监控定位和强退，不构成认证凭据。旧版摘要Key不会被新代码双读，升级后原内部Bearer和OpenAPI Token均失效，需要重新登录/取Token。新namespace中的session-epoch由登录时数据库已校验的authRevision初始化；待交付的撤销outbox继续更新新namespace，数据库准入fence保护Redis交付前的旧会话。旧namespace的epoch、会话索引及撤销信息不迁移、不清除，保留在旧前缀下。

### Key版本切换

namespace改变会令普通缓存冷启动；由原有cache-aside路径回源，不需复制旧缓存。每次整体Key格式切换都必须将现有`mes.system.cache-namespace`设置为一个全新、非空、永久固定的标签，且不同于旧值（旧值为空时也必须不同于`default`）；所有节点同时使用该标签。认证及限流不得新旧版本并行：由运维确认共享窗口后，排空入口并让所有节点一次切到同一版本和配置，再恢复流量。旧内部会话和OpenAPI Token不转换，要求重新登录/取Token；数据库authRevision和撤销outbox继续生效，旧namespace下epoch不清理，新namespace的epoch根据数据库当前版本重新建立。旧登录失败计数和限流窗口在新前缀下不可见，会从新窗口计数；登录失败锁定默认10分钟，OpenAPI限流默认60秒。窗口期间必须确保旧节点不再承接请求。

上线后永久保留新的`mes.system.cache-namespace`标签，绝不恢复旧标签或空值；回滚若读取旧namespace，残留旧会话可能让旧Token重新有效，撤销和限流也会分裂。回滚只允许使用继续读取新namespace并遵守新凭据格式的版本。旧Key仅在所有旧实例退出、最长有效期已过、E等并行任务无依赖且确认不回滚旧namespace后，按精确前缀另行评估清理；epoch、撤销队列和会话索引不作普通旧缓存清理。禁止`FLUSHDB`或无边界清理。此切换尚未在真实环境执行，隔离测试不构成运行切换证据。

禁用/状态改变、删除、改密在同一中心事务增加认证版本并写 `sys_session_revocation`，提交后删除旧版本会话。表内记录表示“尚未持久确认完成”，不一定表示 Redis 撤销失败：提交后成功也不立即开启确认事务，后台再次幂等交付并按版本确认删除。角色/菜单/数据权限修改不改变认证版本，无需重新登录。新版本登录不会被延迟旧交付误删，回滚不交付 Redis。注销/强退删除对应会话和索引项。

DB/Redis 不天然原子：提交后 Redis 失败返回 `SESSION_REVOCATION_PENDING`，明确“账号变更已保存，撤销待重试”，不能误称 DB 回滚。精简 DB fence 在交付失败窗口仍拒绝旧会话；已准入请求不重新验账号版本。后台默认每 5 秒重试，按 userId+revision 条件确认，不覆盖新事件，不依赖 TTL；配置 `mes.system.session-revocation-retry-ms` 可调整。V20 增加 next_attempt_at 与排序索引，每轮最多 100 条到期记录，按 next_attempt_at/user_id 稳定排序；失败按 5/10/20/40/80/160/300 秒退避，最高 300 秒，使用数据库时钟。新 revision 入队重置失败信息与处理时间，同版本或较旧入队不重置；旧失败更新也必须匹配版本。批量入队去重、每 500 ID 参数化读取版本，空集合不操作、空 ID/缺失账号明确失败并由调用方账号事务回滚。既有批量删除未发现集合总上限，此处限制 SQL 批大小，不增加产品限额。Redis 故障明确失败，不伪造注销成功。

epoch 无 TTL：它是用户认证版本的发行下界，不能因最后一个会话到期或账号删除就直接消失，否则延迟到达的旧版本发行可能重新写入。数据库准入 fence 是另一重保护，不替代当前 Redis 发行保证。当前每个曾发行/撤销的用户保留一个小键，没有自动删除策略；后续清理必须证明旧发行已终止、账号 ID 不复用且版本不回退，不能随意增加 TTL。Lua 在大单用户索引下会阻塞其他请求，部署时需要按会话规模评估容量。

## 配置与池

路由配置低频修改，允许下述有界缓存不一致。账号 authRevision 与会话撤销 outbox 独立运行。

`FactoryMetadataCache` / `RoutingMetadataStore` 使用共享 Redis cache-aside。独立string Key为`factory-routing:entry:binding:<factoryId>`、`factory-routing:entry:default`，沿用本指南的可读中心前缀与段转义。只存工厂/绑定/数据源状态、连接定义、现有配置revision与加密cipher。未知工厂不写缓存；无默认候选不缓存空值。不存明文密码、池、Connection、Session或用户权限。

每条 SET 带固定 `mes.datasource.metadata-ttl-seconds`（application.yml 显式 300 秒），GET 不续期，其他键写入不延长旧键有效期。未命中一次 JOIN 获取定义；默认候选筛选和定义同一 JOIN，沿用数字 ID 排序规则。HTTP 请求内复用不可变定义；非 HTTP 独立事务查当前 Redis。账号准入不读取路由代际，也不依赖路由状态表。

所有 Managed 工厂/数据源创建、修改、绑定、解绑、启停和删除在原事务提交后 DELETE 受影响工厂及 default；回滚不 DELETE。淘汰失败记录 ERROR，管理接口返回原操作成功结果，不伪装回滚或鼓励客户端重复提交。不保证其他节点立即生效；允许失效前开始的读取在失效后 SET 旧值，最多再存该条 TTL（从回填发布时起算，不是从管理提交时起算）。没有防回填 CAS、代际、轮询恢复或路由 outbox。Redis 读取/发布失败明确 503，不回退默认厂/本地旧值；过期且 DB 失败同样失败。慢读或长请求可能超出 300 秒墙钟变更窗口，TTL 约束缓存条目而不是请求寿命。

事务 begin 复用请求 binding 并取得 RuntimeRegistry 租约。暖池直接租借；冷池按数据源身份分段锁创建，同一身份并发避免重复创建。物理检查/发布采用短临界区，失败候选关闭一次。仅已有租约保护退休池，包括尚未取 JDBC 连接的租约；请求快照不阻止关闭、不保证退休后还能开启事务或创建历史池。活跃新版本拒绝较旧绑定替换；同版本重新启用创建新池，旧租约仍指向旧实例，池关闭不删除新实例物理证据。新独立事务仍须能取得有效池，否则明确失败。

普通工厂名称等编辑不退休池；绑定/启停/删除与数据源修改提交后退休本节点，其他节点既有 `mes.datasource.reconcile-ms` 定期核对已初始化池（仅池资源清理）。旧池拒绝新租约、已有租约完成并排空关闭。缓存失败或回填期间可能按仍缓存的启用定义重新创建一个活动池，跨节点池核对及 TTL 后读新配置再收敛，不声称即时停用。真正切库的旧事务写旧库/新事务写新库重叠仍需运维安排，不提供在线迁库一致性。

长任务应以非 HTTP 独立事务/批次建立新准入；一个 HTTP 请求内快照不自动在批次间刷新。跨线程不可隐式继承 Scope 或持久化 Session。
