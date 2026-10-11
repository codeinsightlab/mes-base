# ADR-002：版本化 Business Runtime 与事务固定

- 日期：2026-10-05；2026-10-11 修订；状态：Accepted。事务内最新版本撤销规则已被用户新决定取代。
- 来源问题：缺范围默认库、连接替换、缓存前未校验和漏登记 Mapper 都可能绕开正确连接；连接池创建不等于数据库健康。
- 旧系统行为：分支有严格无默认业务库、身份+版本注册、事务开头选择和分离会话的有价值思路；旧池替换不等于应用级完整排空。独立重新实现这些技术约束。

## 决定与候选方案

`FactoryId → BusinessPersistenceResolver → BusinessRuntimeBinding(factoryId,datasourceIdentity,revision)`。配置和真实 DataSource 保留在基础设施。HTTP 入口从固定 TTL 的共享元数据缓存取得有效绑定，resolver 复用请求快照；非 HTTP 独立事务从当前缓存准入；未知厂 404、无绑定/无注册 422、版本或身份冲突 409，不落平台库。

registry 显式注册 Dedicated owner 和新 pool，revision 必须递增。生产仅一个业务 SqlSessionFactory 和一个业务 Spring 事务管理器，runtime 只拥有物理池。入口保留不可变绑定快照，事务 begin 复用该快照、获取租约并固定工厂与连接身份。退休池停止新租约，已有租约继续使用，归还后排空关闭，包括已租借但尚未取连接的情况。普通停用、解绑、账号/权限变化、绑定或连接配置变化不主动中止已开启事务；数据库/业务异常及 Spring 超时仍正常回滚。新请求与非 HTTP 独立事务重新准入；同请求多个事务沿原快照尝试取得活动池租约，不保证旧请求仍能开启新事务。

`BusinessPersistence.transaction(scope, work)` 是基础设施/fixture 的 REQUIRED 单位；生产 Service 使用具名 Spring 事务。嵌套同厂共享事务，跨厂及中心/业务混用拒绝。pin 保护 begin/commit/rollback；代理、MyBatis 缓存返回前和提交前仅校验固定 binding/lease 和会话归属，不调用 resolver，不重查最新管理配置。HTTP 内 REQUIRES_NEW 使用请求快照，非 HTTP REQUIRES_NEW 独立准入，挂起恢复原厂、原绑定和原租约。关闭二级缓存，一级缓存限定 statement。

生产 Mapper 按 ADR-004 与 AGENTS §9.1 固定包自动扫描，不恢复中央清单或 Mapper 归属注解；程序式 fixture 的 OwnedSqlSession 校验保留。已逃逸 Mapper 在工作单元之外、或跨线程借用不能使用，中心会话同样受线程归属保护。中心有单独会话工厂和事务管理器，不参与业务本地原子性。

拒绝默认库回退、调用者任意 datasourceKey、一个事务管理器假装多库原子提交。已有管理后台继续使用；本轮不建设完整在线迁库系统。新旧库写入可能重叠，切库需另行有边界的运维安排。

## 测试与尚未覆盖

`DedicatedDatabasePersistenceTest` 使用独立创作的 TEST ONLY probe 与真实 H2 JDBC：单厂/双厂同号、写入隔离、无 Scope/未知厂/缺池、换厂、换连接/版本、同厂嵌套回滚、JDBC 失败回滚、Mapper 归属、逃逸代理、平台跨事务拒绝、并发隔离、池替换/关闭排空、旧注册拒绝和已持有池保护。

Managed resolver 热路径复用请求定义及暖池；冷池按数据源身份分段锁准备，物理验证不持全局 resolver 锁。共享 Redis 独立条目固定 TTL 300 秒，提交淘汰相关条目/default、回滚不淘汰。淘汰失败记录明确日志，配置提交仍返回成功；允许并发旧读取回填，条目 TTL 到期后重新加载，不以旧本地数据续用。Redis 读/发布及过期后 DB 故障明确失败。

仅事务租约保护池生命周期；无请求 reservation、旧冷版本专用注册/保留。退休后已有租约完成，归还关闭，不能从退休池新取租约。新活动版本不能被旧快照替换；同版本重启的新池不干扰旧租约排空。旧 owner 活跃池/租约未关闭不得将身份移交另一厂。普通名称编辑不退休池，其他节点批量核对池仍是资源清理，不承诺全节点立即生效。

## 2026-10-11 决定变更记录

先前曾选择中心 sys_routing_metadata 代际/持久确认、账号路由代际投影、Redis epoch/CAS、防旧回填与请求 reservation。本次用户确认低频配置接受短暂不一致，这些保证超出所需，已拆除；保留认证安全 outbox 与事务租约。历史证据见实施报告。V21 是否由他人手工应用无法核实，原迁移保留但程序不依赖它。详见请求准入文档。

验证使用 RequestSnapshotTransactionTest、ManagedColdPoolTest、RoutingMetadataRedisTest 与实际分页预算；真实 MySQL、多节点部署及同环境 HTTP 延迟未验证。隔离 Redis 的 GET/SET/DELETE/TTL 是真实执行，中心/业务 SQL 是 Fixture。
