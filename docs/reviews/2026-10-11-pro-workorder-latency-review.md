# 生产工单分页接口耗时审查

> 现行路由规则见文末“后续实施：低频路由配置简单缓存”。先前强一致设计仅保留历史证据，已由最新用户决定取代。

本轮简化验收：独立条目固定 TTL 300 秒，命中不续期；提交淘汰、失败日志/正常成功结果、允许旧回填，过期或 Redis 读取故障明确失败。删除路由代际/epoch/CAS/恢复/账号投影与请求 reservation；保留账号安全撤销与事务租约。暖工单 Redis 2/中心 2/业务 2，miss Redis 3/中心 3/业务 2，绑定与默认筛选均一次 JOIN。最终 verify 250 项（246 通过、4 MySQL 跳过），IDEA 15 Java、smoke 10/10 通过。H2 p50 4.644ms/p95 16.662ms 不是实际 HTTP 提升证明。详细变更、首次失败修复与 V21 保留依据见请求准入实施报告文末。

日期：2026-10-11；范围：`GET /api/pro/pro-workorder?offset=0&limit=20`。
本轮仅审查与运行采样，未修改业务代码、配置、数据库结构或业务数据，未启动/重启服务。
保留任务开始前的所有未提交修改。此前同类基础设施风险见
`docs/architecture/persistence-architecture-deep-audit.md`，本报告补充具体接口的真实测量。

## 运行环境与方法

- 本机已有后端 `localhost:8081`，开发账号与 `X-Factory-Id: DEV`；使用已有开发认证文件，未输出凭证。
- 工厂 DEV 绑定数据源 1；真实业务库 `mes_base_dev_business` 的 `pro_workorder` 共 28 行。
- Python urllib 顺序请求，计时包括接收完整响应；直接访问后端，未经过前端代理。
- 使用现有 `/api/monitor/druid/snapshot` 读取中心池与 `business:1` SQL 累计统计。
  无统计重置、无监控配置修改。先采集两组空操作快照，确认监控请求自身造成的查询数，
  再用目标请求前后快照做差。临时认证会话在正常采样后注销；异常时有一次注销失败，依既有会话有效期清理。
- 数据库仅执行 SELECT、EXPLAIN 和 metadata 查询。performance_schema 读取被账号权限拒绝，未提升数据库权限。
- Druid SQL 时间是 JDBC 执行统计，包含网络等待，不能冒充 MySQL 服务端执行时间，
  也不等于接口的全部时间。

## 实测结果

| 样本 | 完整 HTTP 耗时 |
| --- | --- |
| 初次成功请求 | 2033.8 ms |
| 后续 7 次 limit=20 | 1064.8、1212.9、1114.3、1091.0、1086.1、1084.4、1078.7 ms |
| limit=1 的 3 次 | 1104.7、1098.0、1133.2 ms |
| 再次预热后 3 次 limit=20 | 1437.7、1414.2、1183.3 ms |

limit=20 响应约 15.7 KB；limit=1 约 827 字节。减少返回行数未明显降低耗时。
最终 3 次正常采样期间两个池均无新增等待、无新增物理连接。

### SQL 数量与观察者扣除

一次完整的双池快照对比会额外发生 8 次中心查询：用户身份 2、平台权限 2、平台角色范围 2、
数据源详情 1、按数据源找工厂 1；业务池不执行查询。

最终 3 次目标请求加上快照读取，共记录中心查询 56 次，扣除监控的 8 次后为 48 次，
即每个正常目标请求 **16 次中心 SELECT + 2 次业务 SELECT**。

| 单个正常请求的查询 | 次数 | 说明 |
| --- | --- | --- |
| 按 ID 查询工厂 | 7 | 入口选厂 1 次，数据源解析 6 次 |
| 按 ID 查询数据源 | 6 | 每轮解析再读取一次数据源 |
| 用户身份及关联部门/角色 | 1 | TokenService.resolve 每次校验当前账号 |
| 全菜单查询 | 1 | 业务权限按完整菜单 URL 分类 |
| 当前工厂权限查询 | 1 | 合并平台业务权限与当前厂权限 |
| 工单 COUNT | 1 | PageHelper 总数 |
| 工单分页 SELECT | 1 | ORDER BY workorder_id LIMIT 20 |

最后三次业务 SQL：COUNT 累计 69 ms、分页 SELECT 累计 101 ms，合计平均 **56.7 ms/请求**。
中心工厂查询累计 516 ms；数据源查询累计 492 ms（含一次监控详情查询）。
其余中心查询也有耗时，但不能简单把 Druid SQL 时间与 HTTP 时间相减后宣称其余全部属于某个原因。

EXPLAIN：分页走 PRIMARY 索引，估算 20 行；COUNT 走 `pro_workorder_pk` 索引，估算 28 行。
未观察到大表扫描或分页排序导致的一秒执行；并不代表未来大数据、有筛选条件的查询已验收。

## 源码调用链与原因判断

1. `ProWorkorderController.list` → `ProWorkorderServiceImpl.selectPage`，业务只调用一次
   `mapper.selectByExample`，PageHelper 生成 COUNT 与分页 SQL。没有逐行 I/O、Join 或外部 HTTP。
2. `FoundationAccessInterceptor` 解析显式选厂，`ManagedDatasourceService.find` 查询中心工厂。
   `TokenService.resolve` 查询用户身份；`SystemPermissionService.scopedPermissions` 查询全菜单和当前权限。
3. `ManagedDatasourceService.resolve`（约第 399 行）是全局 synchronized 方法。
   **即使 registry 已有业务池，仍先各查一次 mf_factory 与 mf_datasource**，随后才检查池是否已加载。
4. `FactoryBusinessDataSource.begin/verify/getConnection` 和
   `OwnedTransactionManager.verifyAccess/doCommit` 都可触发解析；
   `PersistenceStatementGuard` 在 SQL 前执行 verify。
   运行计数证明本请求解析了 6 轮，不是仅在首次加载池时查一次。
5. `PlatformPersistence.readOnly` 每次读取单独借还中心连接，设置 readOnly 并创建独立 session。
   元数据 SELECT 时间之外，还有连接状态切换、事务控制等可能的 JDBC/网络开销；
   本轮未抓包或逐方法计时，不能给这些开销分配精确毫秒数。
6. 正常分页每请求业务池借连接 2 次、提交 1 次；当前 PageHelper 配置
   `autoRuntimeDialect=true`。额外借连接与运行期方言探测相符，但未进行调用栈采样，
   此项属于源码与计数支持的推理。

结论：一秒延迟已在直连后端复现。首要优化对象是公共持久化链路反复解析带来的中心库往返，
工单业务 SQL 在本数据规模下不是主要耗时。全局锁可能放大并发等待，但本轮无并发压测，
不能声称本次一秒就是锁争用。具体六轮触发点的逐方法耗时和协议往返仍需进一步 profiling。

## 连接池异常：单独的观测

采样中出现 HTTP 500；一轮失败请求未执行工单 SQL，业务池累计等待计数增长。
该窗口包含前序失败请求，不能把累计等待 26 秒直接当作单次 HTTP 时间。
之后一次成功请求耗时 3177.1 ms，业务池从无空闲连接变为 2 个空闲连接，新增物理连接 2，
新增累计等待约 1191 ms；说明冷连接重建会进一步放大延迟。

池参数当时为 initialSize=0、minIdle=0、maxActive=5、maxWait=5000 ms。
未获取与失败 requestId 对应的异常链，故 **500 的唯一根因未确认**；
不能据累计等待或 maxActive 直接断言连接泄漏、池耗尽或网络故障。
最终预热采样没有池等待而仍慢，证明正常一秒问题不依赖这次异常。

## 建议与必须保留的边界

按独立任务优先级处理，尚未实施：

1. 审查版本/状态校验的读库预算：优先把工厂与数据源元数据合并成一次只读查询，
   再依据真实失效契约设计 begin、statement、commit 的验证路径。
   不直接删除 guard，不用长寿命缓存绕过工厂禁用、解绑、revision 变化及旧租约失效。
2. 在只支持 MySQL 的已确认部署契约下评估明确分页方言，避免运行期重复借连接探测。
   修改前审查两套 SessionFactory、生成模板及现有分页测试。
3. 分别测量只读连接状态切换和事务控制的网络成本；不能仅把 minIdle/maxActive 调大当根治。
4. 业务权限的全菜单分类每次请求重建，可另评估安全的菜单分类复用；
   当前用户授权仍必须按每次请求当前工厂读取，不写入长寿命 Token 或跨厂缓存。
5. 对 HTTP 500 收集 requestId 与异常链、连接创建/销毁时间线后再定因；
   多厂并发任务再量化 resolver 的全局锁等待。

任何持久化优化仍须验证缺 Scope 拒绝、事务内切厂拒绝、跨中心/业务事务双向拒绝、
版本失效、回滚/挂起恢复、池退休排空、异常清理及真实多厂数据库行为。

## 验证边界

已验证：源码、真实开发 HTTP、真实业务库行数和 EXPLAIN、现有 Druid 统计差分、
`git diff --check`。未修改业务代码，因此未运行 Maven 编译/测试、UI test/build。
未验证：生产环境、并发吞吐、逐方法 trace、数据库服务端精确执行时间、HTTP 500 完整异常链、
大数据与筛选组合、性能修复后的回归。未提交、推送或部署。

## 2026-10-11：与当前 ktg-mes 的源码对比

对比源为 `/Users/user/work/heri/ktg-mes` 当前 `master`、HEAD `42a81cc5` 工作区，
仅静态读取，未修改或启动旧项目。该版本已经包含多厂改造，不能当作只有默认数据源的早期 RuoYi。
没有测量 ktg-mes 的真实接口耗时或 SQL 数量，不给出其精确请求预算。

| 链路 | ktg-mes 当前实现 | Base 当前实现 |
| --- | --- | --- |
| 身份 | TokenService.getLoginUser 从 Redis 读取 LoginUser | TokenService.resolve 从 Redis 取身份后再查当前用户、authRevision |
| 工厂配置 | FactoryAccessService.executionContext 通过 FactoryMetadataCache 生成请求快照 | 入口查工厂，业务 resolver 又独立查工厂与数据源 |
| 路由解析 | ManagedDatasourceService.resolve 优先复用 FactoryContext.Snapshot 的 datasourceId/revision | FactoryBusinessDataSource.verify 每次调用 resolver，读取最新中心记录 |
| 元数据复用 | Redis FactoryMetadataCache，缓存未命中才查库；管理更新提交后失效 | 没有同等的 resolver 元数据复用，每轮两个独立中心只读窗口 |
| 已加载池 | ManagedDatasourceService.datasource 先 registry.peek，命中直接返回 | resolve 先查两次中心库，再 registry.contains |
| 工厂权限 | FactoryAuthorizationService 每请求加载工厂角色与权限，@ss.hasPermi 读取请求内权限；另有管理员特殊处理 | SystemPermissionService 读取全菜单做 URL 分类，再查询当前厂有效权限；平台角色显式化 |
| 工单分页 | Controller.startPage → Service → Mapper；该列表 Service 无事务注解 | PageHelper.offsetPage → Mapper；Service 显式 businessTransactionManager 只读事务 |
| 事务保护 | FactoryDataSource.check/MapperDatasourceInterceptor/BusinessTransactionManager 校验数据库身份 | OwnedTransactionManager、statement guard、版本化 runtime lease，并在提交前再次校验 |

关键源码：

- ktg-mes `ktg-framework/.../factory/ManagedDatasourceService.java:72`：优先快照，
  `:96`：优先复用已有池。
- ktg-mes `ktg-system/.../service/FactoryMetadataCache.java`：Redis generation、缓存发布和提交后失效。
- ktg-mes `ktg-system/.../service/FactoryAccessService.java:32`：创建请求执行快照。
- ktg-mes `ktg-framework/.../factory/FactoryAuthorizationService.java`：每请求读取角色权限。
- ktg-mes `ktg-mes/.../pro/controller/ProWorkorderController.java:70` 与
  `.../service/impl/ProWorkorderServiceImpl.java:49`：列表链路。

判断：多厂隔离、权限和事务身份保护都不是 Base 独有，也不必然要求 16 次中心查询。
Base 的问题是把重复 guard 与昂贵的重新解析绑定在一起，同时缺少同等元数据复用，
让一次简单分页放大为多轮中心 I/O；这是公共基础设施的性能设计问题，不能归因于工单 CRUD。
全菜单分类每请求重建、resolver 全局同步锁也是额外成本或并发风险，但不能用未测量的锁等待解释本次全部延迟。

两套实现不能直接等价替换：ktg-mes 的请求快照不会在请求中途读取最新配置，Redis 失效也不更新
已经绑定的 Snapshot；Base 当前 guard 可观察请求/事务中途的配置变化。
直接把 Base 改成请求开始只读一次或加入 TTL 缓存，会改变既有版本失效契约。
应优先评估合并工厂/数据源查询，再设计有明确一致性与失效证明的轻量验证路径。
快照应属于事务绑定/基础设施，不把 Scope 扩充成数据源或权限对象。
本次仅追加对比证据，未修改业务代码或用户并行改动。


## 2026-10-11：请求入口到数据库的整体链路审查

本节按用户要求仅审查；未修改生产代码、配置或数据库，未启动/重启服务。本轮重新核对当前源码，沿用上文已有运行采样作为历史证据，没有重新测量 HTTP 耗时或 SQL 数量。

实际业务列表路径：RequestBoundaryFilter（requestId、线程上下文/PageHelper 清理）→ Spring Security/SystemTokenFilter → TokenService（Redis 身份＋当前账号校验）→ FoundationAccessInterceptor/EndpointScopePolicy（URL 分类、选厂、权限）→ Controller → 具名 Spring 业务事务 → FactoryBusinessDataSource/RuntimeRegistry → MyBatis-Spring Mapper/PageHelper → PersistenceStatementGuard → JDBC。SQL 前及提交时再经过事务/路由验证。

判断：Controller→Service→Mapper 的业务层数正常，额外成本主要来自重复中心库 I/O，而非本身的 URL 分支或必要隔离边界。

| 优先级 | 源码问题 | 影响与建议 |
| --- | --- | --- |
| P1 | FactoryBusinessDataSource.verify 每次 resolver.resolve；ManagedDatasourceService.resolve 每次先查工厂和数据源，随后才判断池命中 | 取连接、SQL、提交多个时点均触发昂贵的最新元数据解析。先把两个 metadata SELECT 合并为一次受控查询；再区分绑定获取与状态/版本验证。请求/事务内复用已验证数据须保持原有失效契约，不能用简单长寿命缓存绕过跨节点禁用、解绑及版本变化。 |
| P1 | ManagedDatasourceService.resolve 整个方法 synchronized，锁覆盖中心查询及冷池创建/物理身份核对 | 不同工厂的解析可能串行等待。已有池的快路径应缩小锁，创建/退休仍保证原子注册、物理身份去重、租约排空。尚无并发压测，不能给出锁导致的耗时比例。 |
| P1 | SystemPermissionService.scopedPermissions 每次业务授权加载全菜单并逐项追溯父路径，只检查一个 permission 却先构造完整授权集合 | 菜单分类与用户授权职责混合，CPU 和返回数据随菜单增长。复用有正确失效机制的菜单分类配置；用户角色/当前工厂权限仍每请求读取，专用操作检查可避免加载无关权限。不能用权限字符串前缀替代 URL 分类。 |
| P2 | TokenService.resolve 每请求 selectUserByUserName，现有 XML 关联部门/角色并取账号全部内部字段 | 当前账号状态、authRevision 检查有必要；身份投影可收窄，但先审查其他消费者需要的用户字段，不机械删除账号实时校验。 |
| P2 | 平台 AUTHORIZED 先 hasPlatformRole 再 grantedPlatform；权限 SQL 已限定启用 PLATFORM 角色 | 可用一次授权查询同时证明平台角色及具体权限。平台 AUTHENTICATED 仍须独立证明平台角色，共享 AUTHENTICATED 不要求平台角色。 |
| P2 | PageHelper autoRuntimeDialect=true | 已有报告中额外借连接与动态方言探测相符，但未逐栈证明。本项目只装配 MySQL，应验证固定 MySQL 方言能否减少借连接，保留分页/排序/总数契约。 |
| P3 | EndpointScopePolicy 将共享入口与平台管理都映射到 PLATFORM，EndpointAccess 又重复声明 scope | 可读性与配置重复，主要是维护负担，不能当作一秒延迟的主因。分类建议独立表达平台管理、共享、工厂业务，URL 保持唯一来源。 |

必须保留：身份有效性、每请求当前授权、有效选厂、具名 Spring 事务、两库归属/双向跨库拒绝、事务内切厂拒绝、缓存前 statement guard、最新配置失效与租约、提交验证、池退休排空、异常/线程上下文清理。不要直接删 Service 只读事务或 guard 来减少检查。

建议实施顺序：先明确元数据每次检查的语义并合并查询，缩小 resolver 锁；再优化菜单分类和单操作授权查询；最后收窄身份投影、验证分页方言和清理重复 scope 声明。每步分别核对真实查询数、延迟与多厂负例，不以构建通过代替性能改善。本轮仅静态审查和文档追加，`git diff --check` 为文档检查，未运行新的性能、并发、Maven 或 UI 测试。

## 2026-10-11：外部多厂、多数据源对比审查的复核

输入为用户提供的《ktg-mes 与 BaseMES 多厂、多数据源只读对比审查》。本轮核对 Base main / 560600b、ktg-mes / 42a81cc5 的关键源码；未修改业务代码、配置或数据库，未运行新的测试、性能对测或生产验证。

总体接受其“复用路由快照，保留 Base 授权和池生命周期边界”的方向，不将整篇报告直接当作已批准实施方案。

- **事实修正 D01：** ktg-mes TokenService.refreshToken（159–170 行）实际复制身份后清空 roles、roleIds、permissions 再写 Redis。缓存模型包含这些字段不等于缓存了有效权限集合。Base 当前账号/authRevision 校验与源项目 JWT 会话仍有差异，但不能以源项目长期缓存权限为替换会话的理由。
- **合理问题 D16：** Base ManagedDatasourceService.saveFactory 更新后无条件退休旧 datasourceId，即使只改工厂名称也会触发。RuntimeRegistry.verify 要求租约对象仍是 current 对象，因此已租借操作下次验证可能失败。是否改变停厂/解绑策略需另作决定，但无连接绑定变化的普通编辑应避免不必要的池退休。
- **合理问题 D15：** ktg-mes FactoryMetadataCache.read 忽略 PUBLISH 的返回值，generation 变化只阻止写回，仍返回本轮加载值；整个 hash 的 TTL 会被其他字段发布续期。因此不能将 300 秒宣称为配置提交后的严格失效上界。采用缓存前须明确失效失败、重试及准入新鲜度。
- **合理区分 D11/D17/D18：** 固定事务数据库身份、停止新租约、排空旧租约，与逐 SQL 检查最新管理配置是不同职责。快照不能自动保证严格切库无写入重叠，也不能替代已准入操作所需的资源租约。
- **前提待确认：** 报告将“允许普通在途操作使用原绑定完成”写成已确认要求；当前对话本轮仅要求评价报告，现行规则仍要求版本失效保护。这应作为建议的语义调整，不能据附件认定已授权删除检查。若接受，应同步修订 ADR、规则和相关负例测试。
- **建议收敛：** 普通数据库操作优先以 Spring 事务为绑定与租约边界；同请求的新独立事务重新准入，长任务下一批次重新准入；事务内 guard 继续检查 Scope、固定绑定、会话/库归属和跨库边界。先评估这一调整，再决定是否需要 Redis metadata 缓存，不把 HTTP、整个长任务及异步线程统称为一个快照生命周期。
- **暂不采用 D14：** 不为减少查询直接去掉只读事务。重复 resolve 是可以独立解决的问题；增加无事务业务连接路径会扩大基础设施契约，收益应单独测量。
- **范围澄清 D22：** Base mes-openapi 自动配置明确不安装安全链或消费者 HTTP 入口，只提供目录及文档 metadata；不能把模块存在当成消费者执行链已迁入。此差距应独立记录，不捆绑进路由优化。

实施后的验收应分开证明：权限/隔离负例、事务绑定与挂起恢复、旧池排空、配置并发变化和多节点失效、真实查询数量与延迟。上述本轮结论属于源码复核和建议，不是改造后的运行证明。


## 2026-10-11 实施后的独立证据

本报告上文保留旧开发环境测量。改造后 ProWorkorderRequestBudgetTest 在独立 H2 中执行真实 Token filter、请求授权、工单 Controller/Service/Mapper 和 Spring 事务：每个暖分页固定 **6 次中心 SELECT + 2 次业务 SELECT**，没有完整用户/密码读取。中心预算为精简账号及当前角色 1、入口工厂 1、全菜单 1、当前权限 1、事务准入绑定 2；事务内 SQL/缓存/提交 guard 不再触发 resolver。25 次循环中 5 次预热、20 次计时，结果见 target/validation/pro-workorder-request-budget.json。

这是 synthetic Redis payload + H2、无 HTTP socket 的 fixture；不能与上文开发库 HTTP 延迟直接比较或宣称真实接口降低到 fixture 毫秒数。本轮没有重启用户服务、执行真实迁移或生产查询；同环境 HTTP 改造前后延迟仍未验证。规则、恢复与部署边界见 [请求准入](../development/request-admission.md)。

## 2026-10-11 开发工单字段审查与 10 万条 fake 数据

用户明确授权读取 dev 业务库并新增约 10 万条 fake 工单。通过已有本地 MySQL
凭据文件访问真实数据库，核对中心库 DEV 工厂启用且绑定启用数据源 1，数据库为
`mes_base_dev_business`。本轮未修改业务代码、配置、Schema、中心数据或其他业务表，
未重启服务，保留已有请求准入报告的未提交修改。

### 字段与样本事实

`SHOW CREATE TABLE` 和 information_schema 确认共 34 个字段、InnoDB；没有该表的
触发器或关联外键声明。缺少外键声明不能证明应用层没有关联规则。

| 字段组 | 实际约束与生成方式 |
| --- | --- |
| workorder_id | BIGINT 自增主键；插入时不赋值 |
| workorder_code / workorder_name | 必填 VARCHAR(64/255)；使用批次前缀和六位序号，名称显式 FAKE |
| order_source / source_code | 来源类型必填；28 个样本全部 PLAN、来源单据全 NULL，保持样本值 |
| product_id / product_code / product_name / product_spc / unit_of_measure | 前三项与单位必填，规格可空；完整复制对应样本组合，不生成虚构产品 ID；样本含 15 种产品组合、单位 01/02/03 |
| quantity / quantity_produced / quantity_changed / quantity_scheduled | DOUBLE(14,2)，quantity 必填；四数量同乘 0.50–2.00 倍并保留两位小数，不创造新状态规则；样本排产数量全 0 |
| client_id / client_code / client_name | 样本全 NULL，保持空，不生成客户引用 |
| batch_code / remark | 写入精确批次标记；remark 记录来源工单 ID，便于追踪和删除 |
| request_date / finish_date / transaction_date | 需求日期必填，其余可空；普通样本平移需求与完成/审核日期，保留原相对间隔；特殊日期另见下文 |
| parent_id / ancestors | 样本全部根工单 0 / '0'，保持根节点，不伪造父子树 |
| status | 可空、默认 PREPARE，但样本实际为 '1'、'2'、'3'、'y'；按样本频率复制，不推断每个值的业务含义 |
| attr1 / attr2 / attr3 / attr4 | 保留对应样本值，不解释预留字段的业务意义 |
| create_by / create_time / update_by / update_time | 创建/更新人 fake_seed；构造明确的创建/更新日期，原样本 create_time 全 NULL |
| mo_id | 必填 VARCHAR(39)，唯一索引 pro_workorder_pk；生成新的 UUID，仅为 fake 标识，不代表真实 ERP 工单 |

原 28 行状态分布：1=10、2=13、3=3、y=2；数量 10–500000。
两条 y 样本的需求/完成日期为 `1900-01-01 / 9998-12-31`。
第一次普通日期平移触发 MySQL 1441 日期溢出，事务断开回滚后实查仍为 28 行；
随后修正 SQL 保留这组特殊日期，其审核日期单独生成在目标日期窗口内。
这些值是样本事实，未证明特殊日期的业务含义；finish_date 有值也不能据此宣称已完工。

### 插入与独立核验

- 精确批次：`FAKE-WO-20261011-5F9C2A`。
- 执行方式：临时数字表与原 28 条模板轮转组合，单次 INSERT SELECT；一个事务中检查
  受影响行数、唯一值与数量/日期基础一致性后 COMMIT。不新增永久辅助表、不关闭唯一约束。
- 实际新增 **100000** 行；提交后独立连接复查总行数 **100028**。
- 新行 ID：**1073865–1173864**；工单编码和 mo_id 的 distinct 数均为 100000。
- 新行状态：1=35712、2=46431、3=10715、y=7142。
- 普通需求日期：2025-10-12 至 2026-10-11，覆盖 365 天；另有 7142 条保留特殊日期。
- 新数量范围 5–1000000；数量非正、已生产/调整/排产大于数量、完成日期早于需求日期、
  父工单与祖先异常的检查结果均为 0。产品字段组合与来源模板不匹配为 0。
- 原有 28 条记录按原始 ID 集合重新 SELECT，逐字比较插入前 TSV，全字段完全一致。

### 本地证据与回滚边界

产物在未纳入版本管理的 `.local/backups/20261011-pro-workorder-fake-100k/`：
`before.sql`、`original-rows.tsv`、`insert.sql`、`insert-result.tsv`、
`verification.tsv`、`manifest.json`、`rollback.sql`。
mysqldump 初次因客户端默认导出 masking policies 而遭权限拒绝；使用
`--skip-masking-policies --single-transaction --no-tablespaces --set-gtid-purged=OFF`
重新完成原表备份，未提升数据库权限。

rollback.sql 仅按精确 batch_code、fake_seed 创建人和本批工单号前缀删除，尚未执行。
未来若为 fake 工单新增关联数据，删除前还需审查关联记录。不要直接恢复整表覆盖后来变更。
insert.sql 包含原行数和批次未存在断言，不能直接重跑累加。

已验证的是 dev 数据库直接写入和 SQL 数据核验，不是 UI/API 创建流程或生产验收，
未调用 ERP，也未执行 HTTP 压测。本次没有 Java/配置改动，未运行 Maven；后续性能测量
须标明表已从 28 行增至 100028 行，不能继续沿用上文小数据量测量作为当前性能结论。

## 2026-10-11：ktg-mes 普通分页请求的静态查询预算

只读核对旧项目 HEAD `42a81cc5` 当前源码，以 GET `/mes/pro/workorder/list`、
有效登录 Token、显式 `X-Factory-Id`、工厂/数据源 Redis 缓存命中及业务池已加载为前提。
以下为调用链推导，不是实际 HTTP/SQL 计数；排除登录、首次建池、连接校验和监控请求。

| 分支 | 中心 SELECT | 业务 SELECT | 正常 Redis 调用 |
| --- | --- | --- | --- |
| 普通用户 | 3：当前厂角色、当前厂菜单权限、部门数据范围 | 2：分页 count 与列表 | 5：会话 GET 1，工厂与数据源各 generation Lua + HGET 2 |
| 超级管理员（SysUser.isAdmin） | 2：角色与部门范围；菜单权限直接使用通配授权 | 2：分页 count 与列表 | 同上 5 |

没有显式选厂头时，FactoryAccessService.context 会额外查一次用户选厂偏好；
正常非空元数据的工厂和数据源缓存都未命中时，额外增加 2 次中心 SELECT 和
2 次发布 Lua。缓存发布设置整体 Redis hash TTL 300 秒，命中读取不续期。
Token 接近续期阈值（源码常量 20 分钟）时，额外写一次 Redis 会话；
普通权限注解从请求内 LoginUser 检查，不再次查询数据库。

关键证据：旧项目 TokenService.getLoginUser/refreshToken；
FactoryRequestInterceptor.preHandle；FactoryAccessService.executionContext/context；
FactoryAuthorizationService.authorize；FactoryRoleService.userRoles/userMenus/departments；
FactoryRoleExtraMapper.xml 三条查询；SysPermissionService.getMenuPermission；
FactoryMetadataCache.read；ManagedDatasourceService.resolve/datasource；
ProWorkorderController.list → ProWorkorderServiceImpl.selectProWorkorderList → Mapper。

当前 ktg-mes 的 Redis 会话明确移除角色和权限，工厂角色/权限仍每请求读取，
不能描述为“Token 有效期内全部授权只从 Redis 获取”。它减少中心读取的重要机制是
工厂/数据源 Redis 元数据缓存及请求内路由快照。Base 的 6 次预算另包含实时账号/版本
读取和全菜单分类，工厂/数据源则未使用相同元数据缓存。两者语义有差异，不能仅按数字
直接替换。此前口头将完整 Redis 授权快照视为当前 ktg-mes 方案的说法需要更正。

未启动或修改旧项目，未执行真实接口、数据库或 Redis 操作；未修改业务代码和配置。

### 同日补充：Base 平台列表与业务列表的路径差异

以 SysRoleController.list 的 AUTHORIZED 平台分页作对照：Redis 身份及精简账号/角色
准入仍共用，但 FoundationAccessInterceptor 的平台分支不解析 FactoryScope，平台角色
从本请求准入加载的角色复用；SystemPermissionService.permissions(user, null) 不进入
businessOnly 分支，不加载全菜单分类；中心列表直接使用 master 入口，无业务绑定解析。
常规非空分页可静态推导为 1 次账号/角色 + 1 次权限 + 2 次分页 SELECT，合计约 4 次
中心 SELECT；没有实际请求计数验证，不将该数推广到所有系统接口。

工单分页已验证的隔离预算为 6 次中心 + 2 次业务 SELECT，其中多出的入口工厂、
全菜单分类、事务工厂及数据源共 4 次中心读取解释了结构差异。相同授权规则并不要求
相同数据库路径或耗时；两种列表的数据量、SQL、网络距离和连接池冷热也须分别测量。
中心库后台任务持续借连接使其更可能处于暖状态，业务池可能空闲；这是源码支持的
候选解释，尚无截图对应的计时/池指标证明。保留当前配置修改，未调整或重启服务。

### 同日补充：请求快照、缓存与内存池的职责

用户质疑逐请求全菜单分类、重复工厂校验，以及已加载池仍读取数据库配置。
复核 ktg-mes FactoryAccessService.executionContext：工厂与数据源从
FactoryMetadataCache 读取，启用绑定生成不可变 Snapshot(factoryId, datasourceId,
datasourceRevision)，另含该旧项目 ERP 编码（不建议迁入 Base）。其
ManagedDatasourceService.resolve 优先使用 Snapshot；datasource(binding) 按数据源
ID/版本查 registry.peek，命中直接返回池。Base 已加载池也复用，但 begin 调用 resolver
仍先读取工厂与数据源，故复用池不等于复用绑定元数据。

对用户建议的评价（方案建议，未实施）：

- HTTP 平台/业务归属由 URL 确定，两边均可按对应角色范围和显式操作权限授权。
  每请求全菜单分类不是 URL 规则的必然要求；菜单归属与业务角色授权限制仍需在配置入口
  落实，但不必由每个业务请求读取完整菜单实现。若取消请求分类，需同时审查已有错误
  授权与同权限码跨 URL 复用行为，不能把当前“菜单分类也参与业务许可”的行为变化隐藏。
- 工厂/数据源元数据可以缓存，修改提交后统一失效，并在请求内复用绑定快照。
  缓存必须覆盖工厂绑定、启停、数据源连接与版本的变更入口；多实例共享失效结果。
  数据库提交与 Redis 失效之间并非原子操作，失败应明确可恢复；TTL 只能兜底，不能称为
  立即失效保证。ktg-mes 使用 generation 防旧读取回填和 300 秒 TTL，失效失败上抛，
  此类事务后异常并不撤销已提交的数据库配置。本轮不宣称其具有持久失效补偿。
- 连接池是节点内运行资源，不放入 Redis；其配置元数据可以放 Redis。
  加入管理不等于立即建池；首次实际访问懒加载，后续相同绑定/版本复用。
  暖池最终借连接与中心池相似，但业务入口仍需选择正确池，并处理配置版本变化。
- 已准入请求/事务使用固定租约完成；新请求从有效缓存生成新绑定，配置变化时旧池
  按 Base 已有退休排空机制处理，禁止缓存失效立即关闭仍有事务使用的池。
  保留 Core FactoryScope 仅含 FactoryId，绑定快照放执行/基础设施上下文，不机械复制
  旧项目 Scope 携带 ERP 字段的实现。

结论：用户建议有源码依据；动态路由必需，但每请求重复中心读取不是动态路由的必然
成本。仅审查并追加结论，未修改运行实现，未验证真实性能或缓存失效并发行为。

## 2026-10-12 远程数据库与 JDBC 公共开销取样

用户反馈所有接口仍偏慢。本轮只读使用已有 dev MySQL 凭据文件，未输出凭据、用户数据、
连接地址或 Token；未修改服务、数据库配置、Schema、业务数据或重启用户服务。
浏览器状态读取超时，未取得已认证业务 HTTP 分段证据，不将下述探针冒充完整接口测量。

### 实际观测

- 用户 backend 8081 正在运行；本地 /health 连续 6 次为 34.8、3.1、1.5、2.0、1.5、1.6ms。
  后五次暖请求证明本地 HTTP 基础开销小，不证明带身份/权限接口同样快。
- dev 数据库为远程连接。mysql 客户端同一连接 SELECT 1：首个含连接建立 214.3ms；
  后续 10 次 17.3–31.1ms，中位 18.6ms。这是客户端往返，不能拆成纯网络 RTT/服务端执行时间。
- dev pro_workorder 仍有 100028 行。独立 COUNT(*) 三次客户端耗时
  143.5、177.9、166.4ms。只解释工单分页的一部分，不能解释所有接口。

### JDBC 对照探针（不是运行服务配置变更）

通过运行 JVM classpath 核对使用 mysql-connector-j 9.7.0、Druid 1.2.28。
临时 /tmp/MesJdbcLatencyProbe.java 使用同版本驱动、同 dev 连接、独立暖池，
只 SELECT 1 及切换本连接 readOnly/autoCommit/commit 状态，无业务写入。
对照 useLocalSessionState=false/true，各操作 5 次平均，日志
/tmp/mes-jdbc-latency-probe.log：

| 操作 | false 平均 ms | true 平均 ms |
| --- | --- | --- |
| 直接 JDBC isReadOnly | 48.6 | 约 0 |
| Druid 借连接、SELECT 1、归还 | 29.2 | 18.3 |
| Druid 只读事务、SELECT 1、提交与状态恢复 | 294.7 | 116.9 |

直接 JDBC SELECT 1 对照自身也波动（49.8/67.0ms），直接 JDBC 事务为
266.7/276.6ms，所以不能把上述池差异当作严格可归因的线上加速比例。
但通过本地实际 Connector/J ConnectionImpl 字节码确认：isReadOnly 默认会在
useLocalSessionState=false 且 readOnlyPropagatesToServer=true 时查询
@@session.transaction_read_only；true 分支复用连接本地状态。
多条业务 SELECT 的预算没有统计这种驱动状态读取以及 SET/COMMIT 的额外通信。
工单 Service 是 Spring 只读事务，OwnedTransactionManager 仍调用标准 begin/commit/cleanup；
元数据独立窗口还会 setReadOnly(true)。这些是需要进一步实测的公共开销候选，
不由 H2 查询预算或本文探针证明其在每个真实接口中的精确数量。

### 下一步与边界

优先取得真实已认证 HTTP 的身份恢复、账号查询、权限查询、池借用、事务准备、SQL、
提交及归还分段计时，确认运行 JVM 使用的配置与当前源码一致。再对 JDBC 状态复用
做受控对照，审查 SQL 是否绕过 JDBC 修改会话状态，不关闭服务器只读防护、事务、权限
或跨厂检查来换速度。工单 COUNT 另审查索引/执行计划。未改运行配置、未执行 Maven，
本轮仅追加观测，未确认所有接口慢的唯一根因，也未承诺几十毫秒可达。

### 同日进一步收敛：中心库实际 Mapper 与执行计划

用户明确工单与其他接口延迟一致，下一步聚焦中心公共链路，不用 10 万行工单解释全局。
/tmp/MesCenterQueryProbe.java 使用运行 JVM classpath 的实际 PlatformPersistence、
SessionAccountMapper/SysUserMapper/SysRoleMapper/SysMenuMapper 和 XML，独立 Druid 单连接
暖池、生产 MyBatis 配置；读取 dev 中有效样本账号及平台权限，不发行会话、不打印
身份/权限数据，未修改用户服务。测试不是完整身份过滤器/HTTP 请求。

重要修正：dev 公共中心 JDBC URL 显式 useSSL=false，初版探针未沿用此参数；
后续 /tmp/mes-center-query-probe-actual-url.log 已按实际 dev YAML URL 重测。
确认 probe 与 dev YAML 的中心主机/数据库一致，不代表运行 JVM 配置已完整核实。
服务 PID 从 9505 变为 17295，非本轮操作，不混用为同一进程 HTTP 测量。

在实际 dev URL、useLocalSessionState=false 的独立生产 Mapper 链中：
- 账号 Mapper 10 次 p50 22.1ms，权限 Mapper p50 44.8ms。
- 账号＋权限 10 次配对 p50 55.5ms，范围 44.2–114.3ms。
- MySQL 同连接 Questions 差值扣除统计自身后恰好 20 条语句/10 对调用。
  未发现普通中心 SELECT 每次隐含 isReadOnly 状态查询；上一节驱动只读事务探针不能
  推广为所有账号/权限查询的共同根因。ClientPreparedStatement 对只读安全查询会跳过
  部分 isReadOnly 检查，源码字节码亦支持该差异。
- EXPLAIN ANALYZE 样本：账号 root actual end 0.0238ms（客户端24.6ms），
  权限 root actual end 0.656ms（客户端27.2ms）。服务端 SQL 执行并不重；
  客户端时间包含往返/驱动/收取等，不能直接将全部差值命名为纯网络延迟。

useLocalSessionState=true 对照：账号/权限单项 p50 19.7/21.9ms，但后面的配对
p50 升至 215.5ms、最大831.0ms，语句数仍20；独立 metadata readOnly 操作也有明显
波动。因此现有串行小样本不能证明该选项对真实中心请求有收益，不建议据上一节
294.7→116.9 的结果直接修改项目 JDBC 参数。需要排查波动并取得真实请求阶段计时。

浏览器读取恢复可用，但实际5173页处于登录状态，已请求用户登录以继续角色/菜单
中心接口测量。尚未解释真实约300ms中剩余阶段，也没有以探针结果宣布问题解决。
仅文档追加，未修改运行代码/配置，未重启服务或执行 Maven。

同 PID 阶段另取公共入口：8081 /api/login-options 连续 6 次 HTTP 200，
58.0、52.8、35.1、24.8、24.1、31.5ms；/health 为 2.1–5.4ms。
经 5173 代理的 login-options 三次也是200，35.0–48.0ms，直连同期39.4–43.8ms。
所以本地代理和所有公共接口不呈现固定300ms，但不能推广到已认证中心管理接口。
实际角色/菜单等 HTTP 请求仍需已登录浏览器后继续定位，当前不宣称300ms问题已解决。

### 同日已登录角色列表：运行 JVM 等待取样

用户完成内置浏览器登录后，真实打开5173/system/role，角色列表显示29条。
通过页面刷新按钮触发读取，采集用户8081进程17295的20秒JFR profile（自动结束），
SocketRead阈值1ms，无请求体/凭证记录；原始文件 /tmp/mes-center-live.jfr，
筛选事件 /tmp/mes-center-live-events.json。仅短时诊断记录，未改业务代码、配置，未重启。
浏览器只读evaluate不能访问performance API，故本轮没有浏览器waterfall/精确HTTP总耗时。

六个角色查询线程各观察到4次MySQL读取（账号、权限、PageHelper count、列表），
累计SocketRead等待366.2、378.5、413.7、442.9、537.5、770.9ms；另一个线程含5次读取，
不能统一视作严格一请求四SQL样本。页面其他自动请求及用户活动亦可能在记录窗口内。
代表线程exec-10依次126.7、81.0、94.2、111.8ms，合计413.7ms。
实际SocketRead端点与此前dev探针数据库地址一致。没有把SocketRead直接命名为纯网络RTT，
它包含等待服务端结果/传输及可能的调度暂停；不能仅凭该事件区分网络与数据库负载。

记录窗口仅一次GC暂停21.8ms，JVM user/system CPU总体低；HTTP线程仅观察到一次22.4ms
ThreadPark，没有发现连接池/Java锁等待足以解释上述几百毫秒的证据。Redis主要在Netty线程，
不能由HTTP线程未出现Redis SocketRead推导Redis零成本。前面EXPLAIN ANALYZE是独立样本，
未与每一条live SQL逐条关联，不宣称全部live SQL服务端执行都已测量。

只读核对进程启动类型：JDWP=true、IDEA runtime agent=true、debugger instrumentation
agent=true。独立Mapper探针没有相同调试代理，因此它和live JVM并非完全受控对照。
调试代理是待排除因素，不能据此断言Debug就是根因；JFR也引入少量观测开销。
建议同配置Run/Debug对照及关联服务端statement计时，不先修改授权或JDBC选项。
用户选择“暂时保留Debug，先看当前结论”，本轮不切换/重启用户服务。

当前结论：中心真实请求的明显时间消耗发生在数据库结果读取等待，已定位到阶段，
尚未证明唯一成因；不能用工单行数解释，也不支持继续增加路由/权限缓存以试错。

### 同日 ktg-mes 中心角色链路及导出对照

静态对比当前 ktg-mes：超级管理员平台角色页会在 FactoryRequestInterceptor 跳过
工厂授权装载，PermissionService.hasPermi 也直接许可，TokenService 从Redis取身份，
没有Base同等的逐请求账号版本fence。标准非空分页通常只需count/list两条中心SQL；
Base为账号/角色、权限、count/list四条。两套列表SQL均为sys_role关联user_role/user/dept、
distinct与排序，PageHelper分页；未发现Base列表SQL结构足以解释五倍差距的证据。
两套配置有池数量/驱动版本差异，不能用配置大小证明暖请求原因。

旧项目已读取profile的master JDBC地址均与Base dev地址不同，属于配置事实，不证明
用户实际比较部署采用这些profile。用户补充ktg非管理员同样快，因此管理员捷径不能
作为主要原因，需要同网络/实例/连接配置的实际对照，当前不判定MySQL引擎故障。

按用户授权导出当前Base开发中心库（完整结构＋数据，不含业务工单库）：
.local/backups/20261012-center-performance/mes-base-center.sql，651263字节，33张InnoDB表；
源清单与DDL表清单一致，无视图/trigger/routine/event。使用single-transaction、
skip-add-drop-table、no-tablespaces等只读导出；无CREATE DATABASE/USE/DROP TABLE，权限600。
SHA-256：33cf61699de5203b8649633491d05cb8d52162ab000e7b32923137f1165bd102。
manifest.json与README记录对照方式；未导入目标、未修改配置、未重启用户服务。
为避免同名sys_*冲突，建议导入ktg所在MySQL的新空测试库，继续用Base代码对比，
不要将Base schema覆盖现有ktg数据库或用ktg代码查询Base结构。

## 2026-10-11 实施更新：授权去全菜单与缓存绑定快照

> 历史记录：下述路由代际、V21 运行依赖、持久失效交付、防回填及请求 reservation 保证已由同日后续“路由缓存简化”用户决定取代；不再作为现行约束。账号安全与授权规则仍有效。

本节取代上述方案建议/6+2 的当前实现状态，历史真实 16+2 与当时数据量/网络环境证据保留。本次已实施，不是只读方案：业务动作按 URL 范围授权，取消逐请求全菜单减法；菜单分类留给导航/角色分配/启动及菜单编辑审查。发现工厂角色错误平台菜单或跨范围权限码复用时明确拒绝启动/编辑，不自动改真实授权。

新增 Redis 路由元数据及 V21 中心持久代际/失效确认，工厂/数据源变更同事务推进代际，提交后失效/恢复。代际通过既有账号/角色查询带入请求，Redis READ 原子拒绝较旧 metadata，失效失败不能只等待 TTL；旧回填验证代际+随机 epoch。读/发布失败 503，失效失败为“已保存，待恢复”，不声称 DB 回滚。缓存只含路由定义和加密 cipher；请求 snapshot/版本 reservation 在入口固定，事务复用，暖池无 metadata 查询。

最新实际生产类链路预算（H2/模拟会话及路由 Redis、非真实服务）：显式选厂+缓存命中+暖池的工单分页 **2 次中心 SELECT + 2 次业务 SELECT**，Redis session read 1 + metadata READ Lua 1。中心是账号/角色/路由最低代际 1、权限 1；首次 metadata miss 另加 JOIN 1 与发布 Lua 1。默认选厂、冷池、非 HTTP 的额外成本不能隐藏在此预算内。旧同链路 6+2 减少 4 次中心 SELECT；不能用 fixture 耗时计算相对旧真实 HTTP 的速度倍数。

旧请求及事务保持原 binding/租约；旧快照尚未取连接也保留使用资格。同请求多个事务复用原快照，新请求采用新配置。同版本重新启用的新请求不加入退休池；旧池及物理证据按 pool instance 排空。名称编辑不退休池，冷准备按版本分段锁，实际连接探测不持全局 resolver 锁，16 并发冷请求 fixture 只建一池。

详细实现、真实隔离 Redis/Fixture/打包验证及未验证部署边界见 [请求准入实施追加报告](2026-10-11-request-admission-implementation.md) 的“业务授权与路由元数据缓存”。规则同步到 AGENTS、ADR-002/003/004、request-admission；未执行真实迁移、服务重启、提交/推送，也未重新访问真实开发/生产 HTTP/数据库。

最终验收：IDEA 定向格式/检查 30 Java 通过；最终 verify 243 项（239 通过、4 MySQL opt-in 跳过、无失败/错误）；真实隔离 Redis Lua/双客户端故障验证通过；packaged smoke 10/10；两仓 diff --check 通过。最新 H2 fixture p50 4.560ms / p95 6.943ms，仅为回归样本，不是实际 HTTP 优化幅度。V21、真实现存错误授权审查及同环境性能仍需部署前单独验收。

最终回执再次完整重跑 verify 通过（/tmp/mes-route-verify-final-retry.log），覆盖后来追加的未知工厂 ID 缓存断言；一次长时间运行中的既有 Redis 容量用例跨越 600 秒会话有效期并失败，详情已记入实施报告，不隐藏这次运行失败，也不把其耗时作为路由性能证据。

## 2026-10-11 后续实施：低频路由配置简单缓存

最新用户决定取代上节强一致协议。删除路由代际/epoch/CAS/持久恢复/账号投影/请求 reservation，保留每请求身份授权、安全会话撤销与事务租约。元数据改为独立 string 键，固定显式 TTL 300 秒，GET 不续期、其他键不延长旧键；默认候选和绑定定义也合为一次 JOIN，旧独立 selector 移除。

提交后淘汰相关厂及 default；失败记录 ERROR 并返回配置已保存的正常结果，接受旧值到期及并发回填。TTL 从发布时起算，不能宣称提交后固定 300 秒内必然全节点一致。Redis 读/发布及过期后 DB 故障明确拒绝，没有持久失效协议。旧快照不保留池使用资格，已取得事务租约完成并排空；不同节点缓存短暂不一致不放松账号安全。

暖工单 Redis 2/中心 2/业务 2，miss Redis 3/中心 3/业务 2。最终 verify 250 项（246 通过、4 MySQL 跳过），IDEA 15 Java、smoke 10/10 通过。H2/synthetic Redis p50 4.644ms、p95 16.662ms 不是实际 HTTP 提升证明。V21 外部应用状态无法在本轮真实 DB 禁止访问的边界内确认，保留原历史迁移、运行不依赖该表、不执行真实删表。具体源码、首次失败修复及验证边界见请求准入实施报告“后续实施：路由缓存简化”。
