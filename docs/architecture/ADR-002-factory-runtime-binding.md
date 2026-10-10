# ADR-002：版本化 Business Runtime 与事务固定

- 日期：2026-10-05；2026-10-11 修订；状态：Accepted。事务内最新版本撤销规则已被用户新决定取代。
- 来源问题：缺范围默认库、连接替换、缓存前未校验和漏登记 Mapper 都可能绕开正确连接；连接池创建不等于数据库健康。
- 旧系统行为：分支有严格无默认业务库、身份+版本注册、事务开头选择和分离会话的有价值思路；旧池替换不等于应用级完整排空。独立重新实现这些技术约束。

## 决定与候选方案

`FactoryId → BusinessPersistenceResolver → BusinessRuntimeBinding(factoryId,datasourceIdentity,revision)`。配置和真实 DataSource 保留在基础设施。resolver 重读存在/启用状态；未知厂 404、无绑定/无注册 422、版本或身份冲突 409，不落平台库。

registry 显式注册 Dedicated owner 和新 pool，revision 必须递增。生产仅一个业务 SqlSessionFactory 和一个业务 Spring 事务管理器，runtime 只拥有物理池。事务 begin 解析当前有效配置、获取租约并固定工厂与连接身份。退休池停止新租约，已有租约继续使用，归还后排空关闭，包括已租借但尚未取连接的情况。普通停用、解绑、账号/权限变化、绑定或连接配置变化不主动中止已开启事务；数据库/业务异常及 Spring 超时仍正常回滚。新独立事务重新准入。

`BusinessPersistence.transaction(scope, work)` 是基础设施/fixture 的 REQUIRED 单位；生产 Service 使用具名 Spring 事务。嵌套同厂共享事务，跨厂及中心/业务混用拒绝。pin 保护 begin/commit/rollback；代理、MyBatis 缓存返回前和提交前仅校验固定 binding/lease 和会话归属，不调用 resolver，不重查最新管理配置。REQUIRES_NEW 新准入，挂起恢复原厂、原绑定和原租约。关闭二级缓存，一级缓存限定 statement。

生产 Mapper 按 ADR-004 与 AGENTS §9.1 固定包自动扫描，不恢复中央清单或 Mapper 归属注解；程序式 fixture 的 OwnedSqlSession 校验保留。已逃逸 Mapper 在工作单元之外、或跨线程借用不能使用，中心会话同样受线程归属保护。中心有单独会话工厂和事务管理器，不参与业务本地原子性。

拒绝默认库回退、调用者任意 datasourceKey、一个事务管理器假装多库原子提交。已有管理后台继续使用；本轮不建设完整在线迁库系统。新旧库写入可能重叠，切库需另行有边界的运维安排。

## 测试与尚未覆盖

`DedicatedDatabasePersistenceTest` 使用独立创作的 TEST ONLY probe 与真实 H2 JDBC：单厂/双厂同号、写入隔离、无 Scope/未知厂/缺池、换厂、换连接/版本、同厂嵌套回滚、JDBC 失败回滚、Mapper 归属、逃逸代理、平台跨事务拒绝、并发隔离、池替换/关闭排空、旧注册拒绝和已持有池保护。

Managed resolver 的热路径直接读取中心，冷池准备/发布才串行并重查配置，未引入 Redis 元数据缓存。管理提交后本节点退休池，其他节点新准入同样查中心；已初始化池每 5 秒批量核对退休，中心失败时不假装成功失效。server UUID + database 检查物理库唯一性，退休池的物理记录保留到实际关闭；同 identity 仍有旧租约时不能移交另一工厂。池对象不可重复注册，关闭失败不伪装关闭，由定期 retryRetired 及后续 registry.close 重试。真实 MySQL 多实例、生产配置竞争和关闭故障未实测；H2/模拟证据见专项验收文档。
