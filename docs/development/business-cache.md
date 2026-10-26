# 通用业务缓存处理器

状态：Active。实现及验证日期：2026-10-12。

## 接入约定

通用组件在 `mes-framework` 的 `dev.mesbase.infrastructure.cache`；注解在其 `annotation` 子包，避免与既有结构化 `BusinessCache` key 辅助类重名。复用 `RedisCache`、现有 `redisTemplate`、`SystemCacheNamespace` 与 `SpringCommitActions`，不新增 CacheManager 或另一套 Redis 连接。

```java
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
@BusinessCache(business = MaterialResource.PREFIX, type = BusinessCache.Type.LIST)
public MaterialPage selectPage(MaterialQueryDto query)

@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
@BusinessCache(business = MaterialResource.PREFIX, type = BusinessCache.Type.DETAIL)
public MaterialEntity selectById(Long id)

@Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
@BusinessCacheEvict(business = MaterialResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST)
public MaterialEntity insert(MaterialCreateCommand input)

@Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
@BusinessCacheEvict(business = MaterialResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
public MaterialEntity updateById(Long id, MaterialUpdateCommand input)
```

方法必须通过 Spring 代理调用。缓存读取沿用业务事务准入；只在当前事务只读时读写缓存。嵌套在写事务中的查询直接执行原方法，避免缓存未提交数据。增删改内部的 `requireRecord` 不经过缓存，也不增加同类自调用。

- `business` 使用权限资源前缀（承载模块 + 完整资源名，不含操作），例如 `business:material`、`business:pro:workorder`、`erp:material`。允许安全的冒号分段标识，禁止 glob 元字符和空段。物料注解、同步淘汰与 MaterialPermissions 复用领域侧 `MaterialResource.PREFIX`，不让 Service 依赖 Controller。
- `argument` 默认 0，显式指定查询参数或 ID 所在的位置，不猜参数名，不提供表达式框架。
- 列表参数实现 `BusinessCacheQuery`，提供有效的 `cacheOffset()`、`cacheLimit()`、`cacheSort()`。DTO 保留全部结果相关筛选条件，不能只提供分页。
- 摘要序列化全部方法参数及有效分页/排序；对象属性和 Map key 排序后计算 SHA-256。筛选参数不使用 `toString()` 或身份 hash。集合若表示无序条件，调用方应先规范化其顺序。
- `offset` 是从 0 开始的查询起始位置；只缓存 `0 <= offset < 100`。不是 key 数量上限。无效分页参数交还原方法处理，不改业务异常语义。
- 详情 ID 明确为 String、Long 或 Integer；不缓存 null，不捕获业务的 NOT_FOUND 等异常。
- 返回值按被代理目标方法声明的完整返回类型恢复。分页内部记录和泛型类型保留，不开启多态默认类型。Redis value 沿用 JDK 序列化，其中存放按声明类型恢复的 JSON 字节，不要求所有业务 DTO 实现 Serializable。
- 该通用组件不决定权限范围。后续查询如有用户、部门、资源权限差异，必须将有效范围作为查询参数纳入摘要，或暂不接入；详情若按用户裁剪结果，也不能直接使用当前只按业务/工厂/ID 的共享详情缓存。

## Key 与 TTL

物理 key 使用 `SystemCacheNamespace` 的前缀，保留可选命名空间与可信中心库 URL 的 SHA-256 标识，隔离共用 Redis 的不同部署。工厂 ID 与详情 ID 使用无补位 Base64 URL 编码，避免冒号、Redis glob 元字符及相似业务名误删；列表 key 使用查询摘要。资源前缀带长度边界，避免 `business:material` 的列表淘汰误删 `business:material:list` 的缓存。

示例：工厂 `A`、业务 `business:material`、详情 ID `1`，未配置 named 前缀时：

```text
mes:db:<中心库URL摘要>:business:factory:QQ:resource:17:business:material:detail:MQ
mes:db:<中心库URL摘要>:business:factory:QQ:resource:17:business:material:list:<查询SHA-256>
```

工厂 `B` 编码为 `Qg`；`business:material`、`business:materials` 与 `erp:material` 有明确的冒号边界。工厂上下文缺失调用 `FactoryContext.required()`，沿用现有异常，不回退默认厂。

TTL 配置在 `mes-admin/src/main/resources/application.yml`：

```yaml
mes:
  business:
    cache:
      ttl-seconds: 300
```

未设置时也是 300 秒，非正值启动失败。每个缓存写入都附带 TTL，命中不续期。

## 事务与淘汰

`PersistenceConfiguration` 显式设置事务 advisor order 为 `LOWEST_PRECEDENCE - 20`；缓存 advisor 为 `LOWEST_PRECEDENCE - 10`。事务在外层，缓存查询前已取得固定业务租约。命中返回前再次调用 `OwnedTransactionManager.verifyAccess()`，不绕过固定身份/线程/租约检查，不重新读取管理配置。

淘汰切面在进入方法时捕获工厂前缀和注解范围。方法抛异常不登记淘汰；成功返回后，有实际事务时复用 `SpringCommitActions.afterCommit`，外层事务回滚也不淘汰；无事务时立即执行。回调只使用捕获值，不再次读取 FactoryContext。

- 新增：当前工厂、当前业务的全部 `list:*`。
- 修改、删除：全部 `list:*` 和全部 `detail:*`，不是只删当前 ID。
- 同步：`MaterialSyncWriter` 在本轮写入结束、事务仍有效时调用 `BusinessCacheProcessor.evict`；有更新用 LIST_AND_DETAIL，只有新增用 LIST，全部跳过不登记。

`RedisCache.deleteByPattern` 将逻辑模式转换为物理模式，并通过实际 key serializer 构造 SCAN 匹配值。单机 SCAN count=200，积累最多 200 个 key 执行 DEL；Cluster 扫描每个主节点，按单 key 删除避免 CROSSSLOT。没有 KEYS、FLUSHDB 或全库清理。

Redis 读取/写入或缓存 payload 恢复失败记录 WARN，继续原数据库查询/结果返回。数据库查询和业务异常直接传播。提交后淘汰失败记录明确的 ERROR，说明数据库写入已成功，不向调用方抛出假回滚失败；旧数据依靠后续淘汰和 TTL 收敛。

## 物料真实调用链与权限

`MaterialController` → `IMaterialService` 的 Spring 代理 → `MaterialServiceImpl` → 生成的 `MaterialMapper/XML/Example`。接口路径不变，详情复用 `selectById`，保留缺失/跨厂 NOT_FOUND 语义。列表分页默认 offset=0、limit=20，上限 100；排序固定 `id`。完整筛选包括 code、name、specification、categoryId、unitId、materialType、status、externalId、externalCode。

查询范围为当前工厂与上述筛选条件。HTTP 的 FoundationAccessInterceptor 在 Controller/Service 前完成当前权限检查；缓存命中不能跳过该入口。列表和详情分别使用 list/query 权限，详情 UI 按 query 控制。

物料同步入口 → `MaterialSyncService` → 注入的 `MaterialSyncWriter` Spring 代理；写入与动态淘汰在同一事务内。通用缓存组件不依赖物料同步结果类型。

前端 `material/index.vue` 的编辑复用 CrudPage，列表行只取 ID 后调用 `api.detail`；新增只读详情按钮也只按 ID 查询，带 loading/error、权限检查和上下文变化清理。不以列表行作为详情结果。组件接入与生成器模板维护分别处理。

## 集成测试依赖

`BusinessCacheIntegrationTest` 使用独立随机端口的真实 Redis 兼容进程和 H2 事务，不复用应用缓存实例。默认可执行文件为 PATH 中的 `redis-server`；其他兼容运行时可通过测试 JVM 属性 `mes.test.redis.executable` 指定。测试使用临时配置文件关闭持久化，并在结束时关闭自己的进程；此参数不改变应用 Redis 配置。
