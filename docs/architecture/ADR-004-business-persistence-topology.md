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
