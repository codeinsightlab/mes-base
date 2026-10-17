> 状态：Superseded（2026-10-09）。以下为历史决定，不再是当前接入规范。当前中心 + 多业务库决定见 ADR-004；本 ADR 保留追溯，不保留生产外部实现。

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


## 2026-10-06 修订：固定包归属和声明式外部连接

用户认为逐 Mapper 归属与每次回调选库繁琐，明确提出固定包扫描。采用三个互斥包集合：system/quartz/platform 归平台、mes/modules 归业务、external 归 ERP。标准 MapperScannerConfigurer 各自绑定对应 SqlSessionFactory，不按每个 Mapper 创建注册代码。生产 Mapper 的重复 PersistenceOwner 注解移除，生成器按 Scope 强制正确包并不生成归属注解。

外部访问类/方法用 ErpSource 声明连接标识，注解组合 Spring 的 erpTransactionManager 事务，选库切面在事务拦截器之前执行。类级注解可供整类复用；方法可以覆盖，但事务中的嵌套不同标识拒绝。该设计避免 Mapper 调用发生时连接早已取得的问题，连接归属与会话工厂归属分开。ERP之外仍不按外部标识路由。旧 ErpTransactions 回调入口退役；缺配置、固定身份、异常清理及跨库事务拒绝规则不变。

## 2026-10-09：外部数据库框架退出 Base

按用户要求移除 ERP/HR/WMS 及其他外部数据库装配，不保留第三个 PersistenceOwner、SqlSessionFactory、TransactionManager、选源注解、连接配置或模板。历史规则和验证只描述当时快照；新集成需求须另行明确授权和设计，不恢复占位框架。中心库与 Factory 业务库规范见 ADR-004。

## 2026-10-17 修订：中心工厂运行配置与只读快照

本节只落实工厂业务模式与连接引用，不恢复第三数据源，不修改生产仓储边界审查。现有仓储/生产 Adapter 只有 `supports(scope, bindingKey)`，没有通用 ERP 类型枚举或连接管理表；供应商沿用服务端连接定义中的原注册标识，不另建 DINGJIE/SAP 枚举。

- `mf_factory` 保存 `runtime_mode`（LOCAL/ERP）、`erp_provider`、`erp_config_ref`、`integration_revision`、`integration_locked`。新厂默认 LOCAL；本次正式 Flyway Java V34 从每厂旧 `mes.integration.warehouse.factories.<id>` 导入明确 CONNECTED/provider/binding-key。非法旧配置在 DDL 前拒绝，不把解析错误解释为 LOCAL。既有工厂全部锁定业务权威，避免未绑定但曾有业务历史的工厂被误判为空厂；新建未绑定工厂可配置，一旦绑定即永久锁定，解绑不解锁。实际 LOCAL ↔ ERP 和 ERP 供应商/绑定迁移留给专门流程。
- 没有既有 ERP 连接管理模型可复用，因此连接定义继续采用现有服务端配置机制：`mes.integration.connections.<bindingKey>`。定义包含所属 factory-id、provider、service-url、credential-reference、organization、enabled、revision。认证引用指向服务端 Environment 可解析的凭证属性；不创建凭证存储，不在工厂表或响应中存入凭证。厂商 Adapter 将来负责使用其准确配置执行真实业务。
- `FactoryIntegrationRegistry` 最终校验所属厂、provider、引用、启用状态、正版本、HTTP(S)地址、组织和可解析的认证引用；地址不允许 user-info/query/fragment。工厂管理只返回 reference/provider/revision 选择项；LOCAL 必须无 ERP 字段。配置缺失、停用、错误明确拒绝。
- HTTP 准入沿用一次工厂 metadata 解析，`BusinessRequestSnapshot` 同时保存只读 `FactoryIntegrationSnapshot`，业务调用 `FactoryIntegrationContext.required()`。快照只含 factoryId、mode、erpType、configurationRef、revision、configurationRevision。FactoryScope 继续只含 FactoryId。路由共享缓存采用 v2 key，避免旧 JSON 缺字段。业务事务、权限和数据源租约行为保持原边界。
- 非 HTTP 执行器注入 `FactoryIntegrationExecutor`，在执行线程调用 `run(scope, task)`；它显式解析配置、校验当前可用工厂/绑定并建立有界上下文。Quartz 工厂入口使用该执行器。上下文不继承到新线程，任务期间 pin FactoryScope，异常退出恢复/清理；同一 HTTP 请求不能被另一次配置解析覆盖。
- `WarehouseIntegration` 的新操作选择使用准入快照；已冻结的提交、查询和作废使用原 provider/binding/source mode。查询同时要求 Adapter 明确支持冻结 provider，缺实现拒绝，不把历史 ERP 结果转换成本地成功。生产、领料和仓储 Service 及 ProductionQuantityService 算法未改。

连接绑定键代表不可变集成身份；调整供应商、组织、账套等权威身份时使用新的绑定键并走迁移流程。已有旧配置无服务端连接定义时仍保留 ERP/provider/reference，但新请求明确失败，不能静默降级。没有供应商实现时允许维护有效连接定义，执行实际能力仍按现有 Adapter 门拒绝。

本轮证据和未验证边界见 `docs/development/factory-business-integration-acceptance.md`；不以新增配置和 Fixture 证明厂商联调。

## 2026-10-17 补充：来源隔离与停旧启新（用户最终确认）

现行规则见 [Base MES — LOCAL / ERP 业务边界设计方案 V1.0](local-erp-business-boundary.md) 及根 AGENTS 的同日独立章节。本补充约束现有 HTTP 集成和工厂配置，不恢复上文已退出 Base 的外部数据库框架。

每厂最多一个有效 ERP；LOCAL/EXTERNAL 工单、原料仓、成品仓必须同来源，并由当前模式允许新业务。ERP 同步仅管理 EXTERNAL 权威字段，不得改写 LOCAL 或 MES 不可变事实；本地镜像不授予正式库存权威。LOCAL→ERP 采用停旧启新，禁止自动无缝接续或清除可信历史，历史查询/撤销仍按原冻结身份。普通工厂编辑不得绕过现有模式锁定；配置锁定不代表迁移流程已实现。

本补充是业务约束与文档固化。WorkOrder/Warehouse 尚无明确来源字段，三方来源准入、来源过滤同步及正式迁移准备流程仍待实施；已有模式快照与历史领料冻结不能替代这些能力。静态差异与未验证边界见 [本轮审查记录](../reviews/2026-10-17-local-erp-source-isolation-rules-review.md)。
