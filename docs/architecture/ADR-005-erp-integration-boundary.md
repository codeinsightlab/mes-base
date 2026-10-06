# ADR-005：ERP 连接独立解析，未配置明确失败

- 日期：2026-10-05；状态：Accepted；2026-10-06下述修订取代原占位解析实现。
- 来源问题：Factory上下文混入ERP组织/连接、全局外部库旁路以及空结果假装成功，会让“选择Factory”被误当成“知道该连哪个ERP”。
- 旧系统行为：旧业务直接依赖具体外部单据、组织和连接配置，有局部Port方向但不能作为通用Core模型。仅提炼边界，不迁入供应商业务、地址或凭证。

## 决定与候选方案

`FactoryScope → FactoryErpConnectionResolver → ErpConnectionBinding → ErpAdapter`。Binding 只含FactoryId、provider、opaque连接身份与revision，未来真实适配器独立加载凭证与外部组织；没有Connection内容进入Factory/Scope。

当前唯一 provider 为 NOT_CONFIGURED。默认 resolver 抛 `IntegrationNotConfiguredException`，HTTP 422，不返回null/true/空集合；AUTHORIZED门仍先检查当前厂权限。最小Adapter Port只声明 provider 与连接验证，没有业务运输对象，没有任何空供应商类。

拒绝默认厂ERP、统一全局连接、沿用公司数据源/配置或伪造空成功结果。无第二真实供应商需求时，不建适配器框架或空模块。

## 测试与尚未覆盖

单元测试验证默认解析明确失败；实际 HTTP 验证有Foundation查看权限仍收到未配置422，空角色不能调用AUTHORIZED入口。主JAR无外部地址、真实账号或连接配置。

尚无实际ERP Adapter、HTTP/SQL调用、组织映射、幂等、超时未知结果、补偿/回查或真实联调。未来具体用例需要独立中性Port及业务响应验收；本地JDBC回滚不会回滚外部操作。本轮不承接任何现有ERP单据接口。


## 2026-10-06 修订：独立 ERP SQL Server 会话工厂

用户明确提供外部ERP数据库连接并要求三个会话工厂分别对应中心/业务/ERP库。ERP数据源来自独立配置，不由FactoryScope推导；PersistenceOwner.ERP自动将Mapper绑定到erpSqlSessionFactory，与平台和业务事务管理器分开。取消只有NOT_CONFIGURED的ErpProvider、FactoryErpConnectionResolver/Binding、空ErpAdapter及始终返回未配置的Foundation连接检查API，避免占位框架与真实SQL装配双轨维护。原测试记录仅表示当时占位行为，不表示当前接口继续存在。

当前只接入连接装配，通用配置默认关闭；启用但缺配置时启动失败。真实连接参数在Git忽略的本地配置中，不进ADR。无外部ERP业务Mapper/流程、组织映射、HTTP调用或写入；今后外部业务仍须使用明确Adapter边界，不让Core依赖外部持久化Entity。各本地事务不能承诺跨库原子性。本轮TCP连接可达，JDBC只读探针受TLS协议协商阻断，未建立连接/执行SQL；不宣称ERP联调成功。

## 2026-10-06 修订：一个外部工厂，多个显式连接标识

按用户要求，外部 ERP/HR/WMS 等连接使用统一 ERP ownership 和单个 erpSqlSessionFactory。连接配置改为 mes.erp.datasources 的标识映射；ErpRoutingDataSource 在 Spring 事务开始时固定选中的池，ErpTransactions 仅用 TransactionTemplate 提供选择入口。不存在默认连接；缺标识或未知标识明确失败；活动事务内不同标识切换拒绝。连接选择与 FactoryScope 无绑定关系。新增外部 Mapper 自动扫描，不增加中央绑定、类型枚举或新会话工厂。

接入模板在 config/erp-datasources.example.yml 与 docs/development/templates/erp/，不实现外部业务 SQL。DruidController 显式绑定平台数据源，解决启用第三数据源后的单 Bean 注入歧义。三数据源生产装配测试验证监控仍指向平台池；多标识真实 H2 测试验证路由、回滚、嵌套和线程隔离。真实外部数据库验证边界保持上述记录。
