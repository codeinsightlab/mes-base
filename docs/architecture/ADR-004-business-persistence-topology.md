# ADR-004：同一 Core，Dedicated 实现与 Shared 契约

- 日期：2026-10-05；状态：Accepted（Shared implementation pending）。
- 来源问题：单厂逻辑被机械分叉为多厂代码，或只改数据源映射就宣称共享库，会掩盖跨厂同号数据和缺行级过滤。
- 旧系统行为：独立库路由可以表达，多数业务 SQL 未普遍带厂条件；解除连接唯一性不足以获得共享库隔离。这里不复制 SQL 或 Schema。

## 决定与状态

| 拓扑 | 本阶段状态 | 证据 |
| --- | --- | --- |
| 单厂独立库 | IMPLEMENTED | 同一个 Dedicated adapter，TEST ONLY 单厂A → H2 DBA |
| 多厂独立库 | IMPLEMENTED | 同一个 adapter，TEST ONLY A/B → 两个H2 DB，同号读写/并发隔离 |
| 多厂共享库 | DESIGNED | `SharedDatabasePersistence`、`FactoryOwned` 与下面测试计划；未提供实现/行级验证 |

`Factory count=1/N` 是目录配置数量，Core 没有单多厂分支。`BusinessPersistence` 是持久化适配器内部的受控工作单元契约，接收 Scope 与 Mapper 操作；部署方式由基础设施装配决定。未来业务 Service 依赖所属业务的中性读写 Port，具体适配器再调用这个工作单元，不让 Domain/Service 引用 SqlSession 或判断 topology。第一阶段没有业务模型，因此不提前制造业务 Port。

Dedicated registry 预登记 Factory owner，防止无行过滤的适配器误当 Shared 使用。Shared 将来可多个厂映射同一物理 identity，但必须交付独立受控持久化实现；当前 registry 的 dedicated 注册接口不能用于这个场景。resolver 和 scope 模型不要求 datasourceIdentity 全局按厂唯一，因而没有堵死后续 Shared 实现。

未来业务实体/跨实体引用/事件/审计带 FactoryId，即使独立库也保留范围身份。共享表主键/唯一性和引用按厂定义；域模型不能依赖 SQL 方言或厂库部署判断。测试 probe 无生产意义，不变成工单模型。

拒绝创建 SingleFactoryService/MultiFactoryService，拒绝现在建大量共享业务表或凭通用 SQL 字符串替换实现隔离。

## Shared 实现前必须通过的测试计划

1. 在同一真实测试 DB 放 A/B 同 localId/业务号；两厂列表、ID查询、分页、排序、聚合各自只返回本厂。
2. 按ID更新/删除、批量、子查询、Join、原生 SQL、缓存和 cursor 强制 factory_id；无 Scope/无厂参数拒绝，不等于查询全量。
3. 所有客户端传入引用按厂核对；复合唯一键及外键防止跨厂关联，同厂并发唯一性/条件更新有 DB 证明。
4. 同一物理连接连续及并发处理 A/B，prepared statement 参数和 MyBatis 一级/二级缓存不能复用错误范围；SQL、结果及连接都记录 Fixture证据。
5. 同样用例在 Dedicated 与 Shared 两种配置下通过，Service 无 topology if；事务内厂变化依旧拒绝。
6. 权限A有/B无、Factory停用、关联对象越界、导出/文件/外部组织都逐领域验收；不能拿物理库隔离替代授权。
7. 投产目标 DB 方言、执行计划、索引、分页上限和负例真实执行；没有这些证据前共享库保持 DESIGNED。

以上是尚未执行的计划，不列入55项通过结果。SQLite/MySQL/PostgreSQL等具体生产部署及多企业 SaaS 本轮未交付。


## 2026-10-06 修订：标准 Spring/MyBatis-Spring 装配

用户明确要求优先参考源分支并减少手工绑定。Dedicated的安全边界保留，生产装配改为一个businessSqlSessionFactory和一个按Factory路由的数据源；版本化RuntimeRegistry只管理连接池。事务管理器扩展Spring生命周期锁定Factory/连接身份，MyBatis-Spring负责session，Service显式@Transactional。既有回调契约留给基础设施/CLI/生成骨架兼容，不要求业务Service再增加Port才能使用Mapper。Shared仍未实现，不因这次装配变化宣称共享库行范围隔离成立。

## 2026-10-06 架构瘦身：设计计划不保留空接口

删除没有实现、注册或调用者的 `SharedDatabasePersistence` 占位接口及嵌套 ScopedIdentity。Shared 仍是 DESIGNED，以上行范围验收计划和 Dedicated 防跨厂共池限制继续有效。
`FactoryOwned` 仍被生成器使用，保留。删除空接口不会启用 Shared、修改当前业务路由或事务；今后实现必须先满足本 ADR 的验收条件。证据见 [Architecture Slimming Audit](architecture-slimming-audit.md)。

## 2026-10-09 决定：只保留中心库与多业务库

用户明确收敛 Base：只有 masterSqlSessionFactory/businessSqlSessionFactory，配套 masterDataSource/businessDataSource 和 masterTransactionManager/businessTransactionManager。中心库固定连接，业务入口按 Factory 解析独立的版本化池；只支持分库模式，不启用共享库。

不保留 ERP/HR/WMS 或其他外部数据库概念、第三 owner、配置、选源注解、工厂与接入模板。ADR-005 外部连接决定被本决定取代。PlatformPersistence/PLATFORM 仍表达中心库上的平台权限和持久化语义，不增加第三库；配置沿用 mes.platform.*。

保留 Dedicated 的 Factory/版本/租约/缓存前 guard、双向跨中心业务事务拒绝、Spring 挂起恢复、回滚和池生命周期保护。删除外部专属测试，中心/业务混合测试迁移为双入口验收；不为满足数量断言删除隔离或错误边界用例。OpenAPI 消费者身份、Redis与文件存储继续独立于 JDBC 拓扑。

实现证据：PersistenceConfiguration、MapperPackages、OwnerMapperRegistration；PersistenceAssemblyTest 对生产装配的精确 Bean 名称/数量以及中心 + 两个 Factory fixture 读写边界验收。此次不改变业务 Schema、字段、权限、业务流程或 ktg-mes。真实多厂部署和生产验收未执行。

### 2026-10-09 验收收据

- `mvn -B -ntp test` 与 `mvn -B -ntp verify` 均通过；全 reactor 共 182 项（framework 7、generator 3、admin 172），178 通过，4 个未 opt-in 真实 MySQL 测试跳过，0 失败/错误。
- IDEA 原生格式化与 `scripts/format-java --check`：37 个本次修改 Java 文件通过；仓库原缺该 launcher，本轮补齐，复用既有 Project.xml，使用临时独立 IDE state，不影响用户正在运行的 IDEA。
- `/usr/bin/python3 scripts/smoke-packaged.py`：6 个检查通过。产物不含已删除外部类/SQL Server driver（含内嵌模块 JAR 检查），隔离 packaged loopback 启动通过；不使用用户开发配置。
- UI：Node22.23.1，`npm test` 82 项通过，`npm run build` 通过；已存在的 deep 语法和大 bundle 提示保留。两个仓库 `git diff --check` 通过。
- 中途测试失败为旧三 TM 数量断言与监控 Mock 未提供缺失项错误，已更新为新双入口事实并完成复跑；业务/中心隔离、回滚、Scope/版本/租约和异常清理用例继续运行。
- 结构化收据：`target/validation/two-factory-convergence.json`。未做真实多厂数据库、生产部署或浏览器验收；未提交/推送，未重启用户服务。修改基础设施装配、关联事务 Bean 引用及监控类型，没有修改业务 SQL、Schema 或业务流程。
