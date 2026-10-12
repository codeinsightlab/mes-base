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

Redis 读取/写入或缓存 payload 恢复失败记录 WARN，继续原数据库查询/结果返回。数据库查询和业务异常直接传播。提交后淘汰失败记录明确的 ERROR，说明数据库写入已成功，不向调用方抛出假回滚失败；旧数据依靠后续淘汰和 TTL 收敛。本轮不引入版本号、锁、延迟双删或一致性协议。

## 物料真实调用链与权限

`MaterialController` → `IMaterialService` 的 Spring 代理 → `MaterialServiceImpl` → 生成的 `MaterialMapper/XML/Example`。接口路径不变，详情复用 `selectById`，保留缺失/跨厂 NOT_FOUND 语义。列表分页默认 offset=0、limit=20，上限 100；排序固定 `id`。完整筛选包括 code、name、specification、categoryId、unitId、materialType、status、externalId、externalCode。

本轮静态检查未发现物料查询的用户或部门数据权限过滤；查询范围为当前工厂与上述筛选条件。HTTP 的 FoundationAccessInterceptor 在 Controller/Service 前完成当前权限检查；缓存命中不能跳过该入口。工作区同期的独立权限调整将列表和详情权限拆为 list/query，本轮保留该调整，详情 UI 按 query 控制。

物料同步入口 → `MaterialSyncService` → 注入的 `MaterialSyncWriter` Spring 代理；写入与动态淘汰在同一事务内。通用缓存组件不依赖物料同步结果类型。

前端 `material/index.vue` 的编辑复用 CrudPage，列表行只取 ID 后调用 `api.detail`；新增只读详情按钮也只按 ID 查询，带 loading/error、权限检查和上下文变化清理。不以列表行作为详情结果。未修改代码生成器或模板，本轮接入不会自动写回生成模板。

## 验证与边界

`BusinessCacheIntegrationTest` 使用独立临时 Redis（随机端口，无持久化，测试结束关闭）及实际 Spring 代理/H2 事务。物料 Mapper 和业务校验为明确标记的 fixture；H2 marker 写入用于证明提交与回滚。未修改用户 Redis 或真实物料数据。

覆盖：offset 0/99 命中、100 绕过；limit/排序/筛选与 Map 顺序；工厂/业务隔离；不存在/null 不缓存；增改删范围；外层提交/回滚；无事务时捕获原工厂；同步新增/更新/跳过/回滚；详情和分页记录类型；写事务不缓存；错误注解；多批 SCAN；payload 损坏、数据库异常和 Redis 故障。Cluster 分支使用连接 fixture 验证主节点扫描和单 key DEL，不代表真实 Redis Cluster 验收。

`GeneratedMaterial.test.ts` 挂载真实物料页、CrudPage、API/request，使用合成 HTTP 响应验证编辑与只读详情调用 ID 接口及上下文变化清理；部分 Element Plus 浮层选择组件为测试 stub，不等于浏览器验收。

浏览器 CUA 初始化超时，本轮真实浏览器视觉与交互验收未完成。未验证生产 Redis、真实 Redis Cluster 或真实物料数据库业务验收；未部署、提交或推送。

### 实际执行结果

- `./scripts/format-java <本轮12个Java文件>`：通过；新增 Cluster fixture 后再次格式化其测试文件，通过。
- `mvn -B -ntp test`：全 reactor 通过。
- `mvn -B -ntp verify`：全 reactor 通过，含 13 个 BusinessCacheIntegrationTest；既有环境条件跳过的测试仍按原条件跳过。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10 passed / 0 failed。
- UI `.nvmrc` 已安装 Node 22.23.1：`npm run test`，27 文件 / 143 测试通过；`npm run build` 通过，保留已有大 bundle 提示。
- 两个工作区 `git diff --check`：通过。

### 本轮文件清单

- `mes-framework/src/main/java/dev/mesbase/infrastructure/cache/annotation/BusinessCache.java`：读取意图与显式参数位置。
- `mes-framework/src/main/java/dev/mesbase/infrastructure/cache/annotation/BusinessCacheEvict.java`：写入淘汰范围。
- `mes-framework/src/main/java/dev/mesbase/infrastructure/cache/BusinessCacheQuery.java`：有效分页/排序提取约定。
- `mes-framework/src/main/java/dev/mesbase/infrastructure/cache/BusinessCacheAspect.java`：代理读取、固定租约检查及淘汰调用。
- `mes-framework/src/main/java/dev/mesbase/infrastructure/cache/BusinessCacheProcessor.java`：稳定 key、类型恢复、TTL、故障降级和提交后淘汰。
- `mes-common/src/main/java/dev/mesbase/common/core/redis/RedisCache.java`：扩展 SCAN 删除，沿用连接、序列化与前缀。
- `mes-admin/src/main/java/dev/mesbase/server/PersistenceConfiguration.java`：明确事务 advisor 顺序。
- `mes-admin/src/main/resources/application.yml`：统一 TTL 配置。
- `mes-business/src/main/java/dev/mesbase/modules/material/domain/MaterialQueryDto.java`：查询参数约定。
- `mes-business/src/main/java/dev/mesbase/modules/material/service/impl/MaterialServiceImpl.java`：列表/详情/增改删注解。
- `mes-business/src/main/java/dev/mesbase/modules/material/sync/MaterialSyncWriter.java`：同步动态淘汰。
- `mes-business/src/test/java/dev/mesbase/modules/material/BusinessCacheIntegrationTest.java`：13 个缓存测试。
- `mes-admin/src/test/java/dev/materialfixture/MaterialSyncHttpTest.java`：原 HTTP/SQL fixture 显式提供缓存依赖，保留既有测试边界。
- `mes-base-ui/src/views/material/material/index.vue`：只读详情与上下文清理。
- `mes-base-ui/tests/GeneratedMaterial.test.ts`：编辑和详情接口调用测试。
- 本文：接入规范、事务/权限事实、测试证据与未验证边界。

工作区中生成器、模板和权限迁移文件的其他已有/并行修改未由本任务改写或清理。


### 2026-10-12 权限资源前缀统一

缓存 business 改为权限资源前缀，不含操作后缀。新增 `mes-business/src/main/java/dev/mesbase/modules/material/domain/MaterialResource.java`，以 `PREFIX = "business:material"` 统一缓存注解、同步淘汰及权限常量。MaterialPermissions 的最终权限字符串保持不变，未修改生成器或模板。

验证继续覆盖不同承载模块下的同名资源（business:material / erp:material），新增相似资源及嵌套资源边界检查（business:materials / business:material:list）。旧命名空间缓存不会被读取，自然按原 300 秒 TTL 过期，不扩大扫描范围清理历史 key。

本次前缀调整验证：8 个涉及 Java 文件经 `scripts/format-java` 格式化；`mvn -B -ntp test` 全 reactor 通过，13 个缓存测试均通过；`git diff --check` 通过。本次未修改前端，未重复执行前端或生产环境验收。

### 2026-10-12 生成模板接入

用户随后授权修改生成模板。Factory 业务 CRUD 现在生成 Resource.PREFIX、缓存查询参数约定、列表/详情注解及增改删淘汰；同步 Writer 复用提交后动态淘汰。命名来自配置中的完整权限资源前缀：material_category → business:material:category，不以 module 或方法名推断。平台 CRUD 不接入业务缓存。

此前“未修改生成器/模板”的记录是前一轮实现边界；本轮按新授权更新模板，未重新生成覆盖已有物料业务代码。模板文件、生成运行测试与隔离输出说明见 [codegen.md](codegen.md) 的同日“通用业务缓存模板接入”章节。
