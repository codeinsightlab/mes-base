# ADR-005：ERP 连接独立解析，未配置明确失败

- 日期：2026-10-05；状态：Accepted。
- 来源问题：Factory上下文混入ERP组织/连接、全局外部库旁路以及空结果假装成功，会让“选择Factory”被误当成“知道该连哪个ERP”。
- 旧系统行为：旧业务直接依赖具体外部单据、组织和连接配置，有局部Port方向但不能作为通用Core模型。仅提炼边界，不迁入供应商业务、地址或凭证。

## 决定与候选方案

`FactoryScope → FactoryErpConnectionResolver → ErpConnectionBinding → ErpAdapter`。Binding 只含FactoryId、provider、opaque连接身份与revision，未来真实适配器独立加载凭证与外部组织；没有Connection内容进入Factory/Scope。

当前唯一 provider 为 NOT_CONFIGURED。默认 resolver 抛 `IntegrationNotConfiguredException`，HTTP 422，不返回null/true/空集合；AUTHORIZED门仍先检查当前厂权限。最小Adapter Port只声明 provider 与连接验证，没有业务运输对象，没有任何空供应商类。

拒绝默认厂ERP、统一全局连接、沿用公司数据源/配置或伪造空成功结果。无第二真实供应商需求时，不建适配器框架或空模块。

## 测试与尚未覆盖

单元测试验证默认解析明确失败；实际 HTTP 验证有Foundation查看权限仍收到未配置422，空角色不能调用AUTHORIZED入口。主JAR无外部地址、真实账号或连接配置。

尚无实际ERP Adapter、HTTP/SQL调用、组织映射、幂等、超时未知结果、补偿/回查或真实联调。未来具体用例需要独立中性Port及业务响应验收；本地JDBC回滚不会回滚外部操作。本轮不承接任何现有ERP单据接口。
