# ADR-003：账号认证、FactoryScope 与能力授权分离

- 日期：2026-10-05；状态：Accepted。
- 来源问题：最初审查把缺 UserFactory 误判为独立准入缺陷；专项复核确认产品允许空角色用户进入启用厂，真正缺陷是部分受控能力没有后端权限门。
- 旧系统行为：有效账号可认证，逐厂加载角色/权限方向合理；首厂/固定编号回退、部分菜单保护代替动作授权和隐式管理员账号特例不适合作为 Base 默认。

## 决定与候选方案

不创建 UserFactory 关系。`AuthenticatedActor` 只有稳定身份；`Role` 归属于厂，包含 `Permission`；`AuthorizationService` 检查 actor+scope+permission。空角色/权限是合法状态，只能执行已明确批准的 AUTHENTICATED 能力。没有账号编号、通配权限或默认 admin 绕过。

所有 HTTP 方法显式声明 PUBLIC/AUTHENTICATED/AUTHORIZED，启动逐项校验；AUTHORIZED 必须声明合法 Permission。Spring Security 验证凭证，MVC 拦截器验证分类、选择 Factory 及按厂授权，真实方法执行前拒绝；全局 permitAll 仅把具体能力授权交给此必选拦截器，不代表端点开放。静态映射关闭；没有自动表单登录/注销接口。新增非 MVC Servlet/WebSocket 需要独立审核，不能声称当前分类自动保护它们。

偏好仅表示本人上次选择。显式请求选厂优先；无显式值时恢复仍存在/启用的偏好，否则保留空 Scope。未知/停用显式厂明确失败。不存在编号1或首启用厂回退。本人身份来自认证上下文，不接受 userId 代传。

最小本地安全方案为 stateless Basic；默认账号/目录为空、权限拒绝；demo 是显式 synthetic profile，凭证来自本地新环境值。测试账号仅在测试源，密码策略、身份管理、SSO/JWT、平台管理员能力及持久权限后台未迁入。

统一安全错误为 errorCode/message/requestId，状态 400/401/403/404/409/422/500（并保留405/415协议错误）。原始异常链记录服务端，前端仅稳定消息；内部 IllegalArgumentException 不伪装成客户端错误，只有输入边界转换为400。

候选方案是恢复成员准入表或根据菜单/按钮决定权限，均会偏离确认后的产品语义；绑定厂即授予能力同样拒绝。

## 测试与尚未覆盖

`AuthorizationPreferenceCacheTest` 验证空角色选厂、A/B 分离、撤权、无默认、偏好失效和缓存范围。`FoundationHttpTest` 在真实嵌入 Tomcat 上发 HTTP，验证匿名/错误凭证/停用账号401、空角色200、按厂403、偏好不授予权限、并发/撤权、输入400、未配置422、409/500安全消息和关联ID。不是只检查注解，也不是 MockMvc。

未实现生产用户库/密码策略、HTTPS部署、限流、持久审计、统一第三方身份或复杂对象 ownership。本阶段没有实际业务对象；新增业务接口仍必须做对象所属范围和相应 Service 授权，不能靠本次 Foundation 测试替代。
