# MES Base 工程约束

非简单工程任务先读 `/Users/user/Documents/agents/AGENTS.md`，按任务路由读取必要规范。本目录是独立 clean-room 工程；旧工程仅是需求/问题证据，不允许复制源码、配置、Schema、凭证或生产数据。

当前已完成第一阶段 Factory Foundation。进入 Routing/WorkOrder/Reporting 或任何新业务阶段必须由用户明确决定，不自动扩展。

- 一套 Core；单厂是 Factory count=1，禁止 Single/Multi 分叉或服务复制。
- FactoryScope 只含 FactoryId；连接、ERP、用户身份、权限分别建模。
- 不创建 UserFactory 准入关系。有效账号可认证；空角色可选择启用 Factory；AUTHORIZED 必须按当前厂 Permission 拒绝无权请求。
- HTTP 方法必须显式 `@EndpointAccess`，遗漏时启动失败。请求/响应 DTO 与持久化实体分离；真实 HTTP 错误和安全消息。
- Core 不依赖 ThreadLocal/Spring/具体基础设施。显式 Scope 进入用例，执行桥只在平台/基础设施边界使用。
- Business 缺 Scope/连接/版本不得回退平台库、首库或全局 key。Mapper 归属显式登记；跨平台/业务事务拒绝，不能声称分布式事务。
- Shared DB 尚未实现；任何启用必须先通过 ADR-004 的行级范围验收。
- TEST ONLY probe 只能在 `src/test`；demo 是显式 profile、合成账号/工厂，无业务实体、无默认密码、无外部连接。
- 构建使用 Java21、`.mvn/settings.xml`；依赖与输出保留在本目录。执行 `mvn -s .mvn/settings.xml verify`，有涉及打包/启动变更时运行 `scripts/smoke-packaged.py`。
- 保留已有工作；提交、推送、部署和进入后续阶段需要对应授权。
