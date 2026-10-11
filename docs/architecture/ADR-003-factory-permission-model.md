# ADR-003：账号认证、FactoryScope 与能力授权分离

- 日期：2026-10-05；状态：Accepted。
- 来源问题：最初审查把缺 UserFactory 误判为独立准入缺陷；专项复核确认产品允许空角色用户进入启用厂，真正缺陷是部分受控能力没有后端权限门。
- 旧系统行为：有效账号可认证，逐厂加载角色/权限方向合理；首厂/固定编号回退、部分菜单保护代替动作授权和隐式管理员账号特例不适合作为 Base 默认。

## 决定与候选方案

不创建 UserFactory 关系。`AuthenticatedActor` 只有稳定身份；`Role` 归属于厂，包含 `Permission`；`AuthorizationService` 检查 actor+scope+permission。空角色/权限是合法状态，只能执行已明确批准的 AUTHENTICATED 能力。没有账号编号、通配权限或默认 admin 绕过。

所有 HTTP 方法显式声明 PUBLIC/AUTHENTICATED/AUTHORIZED，启动逐项校验；AUTHORIZED 必须声明合法 Permission。Spring Security 仅放行启动时从显式 PUBLIC MVC 方法生成的路径/HTTP 方法匹配器及内部 ERROR dispatch，其余请求必须认证。MVC 拦截器继续验证完整 URL 分类、选择 Factory 及操作授权；非 MVC/静态/未知路径不会因不经过 MVC 而匿名放行，新增能力仍须单独审查授权。没有自动表单登录/注销接口。

偏好仅表示本人上次选择。显式请求选厂优先；无显式值时恢复仍存在/启用的偏好，否则保留空 Scope。未知/停用显式厂明确失败。不存在编号1或首启用厂回退。本人身份来自认证上下文，不接受 userId 代传。

生产使用随机 opaque Bearer；system=false 的隔离 fixture 保留 stateless Basic、空默认账号与拒绝授权。Redis 身份快照不含密码/角色/权限；每个请求一次读取无密码的账号 fence 和当前角色，当前部门编号及角色 dataScope 保留。权限与角色证明在请求属性内复用；全菜单 URL 分类只在导航/角色分配/启动和菜单编辑审查中执行，下一请求重新查中心；授权改变无需重新登录。authRevision 仅用于账号状态/删除/密码变更，不用于角色或菜单权限版本。PLATFORM 业务权限与当前厂 FACTORY 权限并集、空范围拒绝、无 userId=1 特例保持。

禁用、删除、改密在同一中心事务写 sys_session_revocation；提交后按用户会话索引原子撤销旧认证版本，失败记录可恢复并返回 SESSION_REVOCATION_PENDING（账号变更已提交）。后台重试条件确认对应版本后删除记录；DB fence 在 Redis 交付失败窗口仍拒绝旧会话。登录发布与撤销通过 Redis Lua epoch 顺序化，更新版本的新会话不被延迟旧事件删除。退出仅删除本会话。不是跨 DB/Redis 原子提交；已通过入口的请求不重新撤销。会话绝对有效期不续期，v2 格式启用后原旧格式会话需要重新登录。

统一安全错误为 errorCode/message/requestId，状态 400/401/403/404/409/422/500（并保留405/415协议错误）。原始异常链记录服务端，前端仅稳定消息；内部 IllegalArgumentException 不伪装成客户端错误，只有输入边界转换为400。

候选方案是恢复成员准入表或根据菜单/按钮决定权限，均会偏离确认后的产品语义；绑定厂即授予能力同样拒绝。

## 测试与尚未覆盖

`AuthorizationPreferenceCacheTest` 验证空角色选厂、A/B 分离、撤权、无默认、偏好失效和缓存范围。`FoundationHttpTest` 在真实嵌入 Tomcat 上发 HTTP，验证匿名/错误凭证/停用账号401、空角色200、按厂403、偏好不授予权限、并发/撤权、输入400、未配置422、409/500安全消息和关联ID。不是只检查注解，也不是 MockMvc。

未实现生产用户库/密码策略、HTTPS部署、限流、持久审计、统一第三方身份或复杂对象 ownership。本阶段没有实际业务对象；新增业务接口仍必须做对象所属范围和相应 Service 授权，不能靠本次 Foundation 测试替代。

## 2026-10-10 修订：持久偏好与业务厂默认选择

用户明确修改前述“无偏好保留空 Scope”策略。显式 X-Factory-Id 仍优先，非法值明确失败；其次恢复有效启用偏好；否则从启用且绑定启用业务数据源的工厂中选默认。一个候选直接选，多候选按工厂 ID 最小值选择，纯数字使用 DECIMAL(65,0) 比较（2 小于 10），同数值按原字符串排序。兼容旧字符串 ID：数字优先，其余按中心库字符串排序。零候选为空；不制造编号 0/1，不回退中心库，不绕过版本、连接或业务路由校验。默认选择不覆盖历史偏好，只有本人手动选择才保存。

SOURCE_PRODUCT_PORT：迁入 Source 中心库 mf_user_preference 参数化 upsert 与认证主体归属；Factory ID 从 BIGINT 适配 Base VARCHAR(64)。生产偏好按受信任 LoginUser.userId 保存，重命名不丢失，不接受客户端 userId。system 禁用 fixture 保留内存实现。持久读写使用 masterTransactionManager，Mapper 按 system 固定包自动归中心库。

登录、刷新与候选变更查询服务端 context；浏览器 factoryId 不替代服务端决策。切换先清旧权限，PUT 保存成功后加载新厂权限，revision 拒绝注销/切换后的旧响应。选厂不创建角色或权限；无角色仍只能访问 AUTHENTICATED。

## 2026-10-11 修订：取消业务授权全菜单读取

接口 URL 是请求范围边界，去 /api 后完整段 /system、/tool、/monitor 为平台管理；共享个人入口与资源证明保持。平台管理需有效 PLATFORM 角色及声明权限，业务使用平台业务授权与当前工厂授权并集。授权结果仅请求内缓存，不缓存到长寿命 Redis。

取消业务权限集合的菜单范围减法：历史错误 FACTORY→平台菜单授权改为启动/管理校验显式拒绝，权限码跨平台/业务 URL 或菜单范围复用同样拒绝。按钮忽略自身路径，继承父菜单归属，/api 前缀按完整段剥离。未自动修改真实授权，部署需核查；不能把新校验引起的启动失败解释为路由故障。RoleScopePermissionTest 验证真实 SQL、错误授权、菜单分配和 URL 平台负例。

## 2026-10-11 路由缓存简化边界

路由代际和请求 reservation 被固定 TTL 缓存决定取代；本 ADR 的账号状态/认证版本准入、会话撤销 outbox、每请求角色/权限、平台 URL 与资源边界保持。菜单分类不回到业务请求热路径。
