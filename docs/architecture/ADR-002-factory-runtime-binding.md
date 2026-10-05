# ADR-002：版本化 Business Runtime 与事务固定

- 日期：2026-10-05；状态：Accepted。
- 来源问题：缺范围默认库、连接替换、缓存前未校验和漏登记 Mapper 都可能绕开正确连接；连接池创建不等于数据库健康。
- 旧系统行为：分支有严格无默认业务库、身份+版本注册、事务开头选择和分离会话的有价值思路；旧池替换不等于应用级完整排空。独立重新实现这些技术约束。

## 决定与候选方案

`FactoryId → BusinessPersistenceResolver → BusinessRuntimeBinding(factoryId,datasourceIdentity,revision)`。配置和真实 DataSource 保留在基础设施。resolver 重读存在/启用状态；未知厂 404、无绑定/无注册 422、版本或身份冲突 409，不落平台库。

registry 显式注册 Dedicated owner 和新 pool，revision 必须递增。每个 runtime 独立 MyBatis SqlSessionFactory 与 Spring JdbcTransactionManager，事务开始固定物理连接。旧 runtime 标记 retired，新请求只拿当前版；旧 pool 等所有 lease 释放后关闭。已持有旧事务不会突然改到新库；替换后继续执行 SQL、或提交前发现版本变化则拒绝并回滚。在途操作不是全局即时撤销：已进入物理提交窗口的事务仍可能完成，配置操作需要运维协调。

`BusinessPersistence.transaction(scope, work)` 是显式 REQUIRED 单位；嵌套同厂共享 Spring 本地事务，跨厂、跨 resolver/registry、已有外部/平台事务均拒绝。pin 保护整个 begin/commit/rollback；代理每次 Mapper 调用、MyBatis Executor 查询/更新/cursor、回调末尾及 beforeCommit 校验选定 binding。关闭二级缓存，一级缓存限定 statement；保留调用时检查，避免将来启用缓存绕过范围验证。

Mapper 必须在所属会话显式注册，`@PersistenceOwner(BUSINESS/PLATFORM)` 匹配，未知归属不默认业务。服务拿 `OwnedSqlSession.mapper`，不能取得裸 SQL/连接/SqlSession；已逃逸 Mapper 在工作单元之外、或跨线程借用不能使用（平台会话同样受线程归属保护）。平台持久化有单独会话和事务管理器，不参与业务本地原子性。

拒绝全局 AbstractRoutingDataSource 默认库、调用者任意 datasourceKey、一个事务管理器假装多库原子提交。暂不建数据源管理后台、凭证中心、远程 registry 或配置热加载 API。

## 测试与尚未覆盖

`DedicatedDatabasePersistenceTest` 使用独立创作的 TEST ONLY probe 与真实 H2 JDBC：单厂/双厂同号、写入隔离、无 Scope/未知厂/缺池、换厂、换连接/版本、同厂嵌套回滚、JDBC 失败回滚、Mapper 归属、逃逸代理、平台跨事务拒绝、并发隔离、池替换/关闭排空、旧注册拒绝和已持有池保护。

节点内 register 与 resolver 是两个显式配置步骤，短暂不一致会失败，不会回退；正式配置管理应补校验/一致发布及回滚。尚未验证目标 MySQL/PostgreSQL 方言、XA、多节点配置失效、关闭异常恢复、真实凭证与实际连接故障。不同 identity 的独立 pool 若被运维错误配置到同一物理 schema，无法仅凭本阶段 opaque identity 自动识别；投产前必须验证物理库唯一性及 DB 权限。一个池对象不可重复注册。
