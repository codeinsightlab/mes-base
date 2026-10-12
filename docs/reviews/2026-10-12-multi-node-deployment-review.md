# MES Base 多节点部署与横向扩容审查及后续整改清单

- 审查日期：2026-10-12。
- 计划状态：**当前没有扩容计划；多节点整改后置，尚未启动。**
- 用途：未来明确启动多节点部署或横向扩容时，作为复核、整改和上线验收的依据。
- 来源：从 [2026-10-09 架构审查报告 §七](2026-10-09-architecture-review.md#七2026-10-12-多节点部署与横向扩容复核) 移入，原审查证据和风险编号 M1–M8 保留。

## 当前计划与启动条件（2026-10-12 用户确认）

1. 目前没有多节点部署或横向扩容计划。本次只保存审查结论，不启动代码整改、基础设施接入或部署。
2. 未来用户明确启动扩容计划后，必须先读取本文，结合届时源码、版本、配置和部署拓扑逐项复核，再按本次审查修复仍存在且适用的问题。不得直接复制完整实例上线。
3. M1–M8 是后续追踪编号。实施时在本文追加每项的最新结论、变更位置、验证命令和结果、剩余风险；标为已解决或不适用必须提供证据，不能仅以已有测试通过关闭。
4. P1/P2 表示审查发现的风险优先级，不代表当前已排入开发。M5 属于已确认的路由一致性合同，未来扩容时须验证并遵守，不自动改回强一致架构。其他已接受的边界保留依据，不能用“暂不扩容”宣称问题已修复。
5. 扩容上线前完成适用问题的整改、容量评估与真实双节点验收。构建、H2/Mock、单节点 Redis 测试和局部多进程探针不能替代完整部署验收。
6. 本文是后续工作的依据，不是实施、数据库变更或部署授权。当前继续既定开发安排，不因保存此清单引入集群框架或拆分服务。

| 编号 | 未来启动时的处理目标 | 当前追踪状态 |
| --- | --- | --- |
| M1 | 调度定义同步、同次触发去重、任务幂等与崩溃恢复 | 待扩容计划启动后复核与整改 |
| M2 | 共享私有文件及既有文件迁移，验证跨节点访问 | 待扩容计划启动后复核与整改 |
| M3 | 跨节点物理库身份与工厂绑定隔离，覆盖地址别名 | 待扩容计划启动后复核与整改 |
| M4 | 参数/字典缓存发布、回滚和滚动启动竞争 | 待扩容计划启动后复核与整改 |
| M5 | 保持并验收 TTL、旧值回填、固定事务与切库排空合同 | 已接受边界；部署前复核与验收 |
| M6 | 明确消息时效要求，整改跨节点事件传播或记录轮询取舍 | 待扩容计划启动后复核与处理 |
| M7 | 制定每库连接预算并进行真实负载测试 | 待扩容计划启动后评估与验收 |
| M8 | 统一配置/密钥，补就绪与摘流；按实际拓扑验证 Redis、故障接管和写请求重试 | 待扩容计划启动后复核与整改 |

以下第 1–5 节保留首次审查的源码快照和已运行测试事实，不代表后续代码自动具备相同状态；本次文档整理没有重新执行架构审查或测试。

## 1. 结论、范围与证据

**当前具备扩展为多节点的主架构，但不能直接将完整实例复制多份并宣称集群就绪。**
普通 HTTP 会话、当前权限与 Factory 请求上下文具备跨节点工作的基础；调度一致性、本地文件、物理库重复绑定防护、共享缓存发布与部署运维仍有边界。
不需要因为横向扩容重建一套 Core 或拆为微服务。

- 对象：`mes-base` 的 `main` / HEAD `c49bd0c` 加当时已有未提交改动；前端仅追踪消息页的推送与轮询消费者。
- 源码事实：生产装配 → HTTP/Service → Redis/Mapper/事务/文件适配器；下文列出审查时可定位的文件与行号；未来实施须按最新源码重新定位。
- 测试事实：本轮运行 6 个测试类、25 项，0 failure / 0 error / 0 skipped，BUILD SUCCESS。Redis 为测试自行启动的临时实例；SQL 为 H2；部分池探测为 Mock。
- 未验证：两套完整应用进程、负载均衡、真实 MySQL 并发/故障、MinIO 外部实例、Redis Cluster、滚动发布、生产容量。
- 本轮未修改业务代码、配置或数据库，未启动/停止用户服务，未提交、推送或部署；只追加本报告。既有工作区改动保留。

## 2. 已有的多节点基础

| 能力 | 当前源码事实 | 边界 |
| --- | --- | --- |
| 身份会话 | `TokenService:56-117` 从共享 Redis 恢复随机 Bearer；每请求由中心库读取账号状态、认证版本、角色；Security 为 STATELESS | 同一集群必须共享中心库、Redis DB 和完整缓存命名空间；不要求用粘性会话保存身份 |
| 撤销会话 | `SessionRevocationService:41-103` 在账号事务中入 outbox；Redis Lua 按版本撤销，失败重试；账号版本在 Redis 交付失败时仍拒绝旧身份 | 多节点重试可重复交付，Lua/条件确认负责幂等；重复轮询会增加中心库/Redis 负载 |
| Factory 与事务 | `ManagedDatasourceService:426-481`、`RuntimeRegistry:164-180` 固定请求绑定、事务租约；每节点独立建池 | 本地池和 ThreadLocal 属于请求执行资源，本身不意味着不能部署多节点；已获租约事务按约定继续完成 |
| 通用同步协调 | `SyncCoordinator:42-91`、`MasterSyncMapper:8-13`、`V12__master_sync_coordination.sql` 使用 factory + sync key 唯一键、条件抢占、run token、数据库时间恢复 | 当前没有接入真实同步执行器；不能将协调器等同于所有 Quartz 任务已具有集群语义，也不能隔离已发出的外部副作用 |
| 自动编码 | `SysAutoCodeGenerationServiceImpl:43-103` 在中心事务内调用 `AutoCodeExtraMapper.lockRule` 的 FOR UPDATE | 避免依赖 JVM 计数器；热点规则仍串行，编码业务约束和跨节点时钟需另验 |
| 共享文件适配入口 | `SystemConfiguration:125-130` 已有 local / minio 两个 FileStorage 实现 | 默认仍为 local；存在适配器不等于已完成共享存储部署 |

## 3. 扩容发现与触发场景

### M1 / P1：每节点独立调度，数据库运行锁没有计划触发去重

证据：`SystemConfiguration:96-120` 手工创建 StdSchedulerFactory，仅消费线程数并在每节点启动恢复任务；
`SysJobServiceImpl:65-71,103-145,203-245` 只修改当前节点 Scheduler；
`QuartzJobExecution:18` / `QuartzDisallowConcurrentExecution:20` 只把 job ID 交给 runner；
`JobClaimMapper:7-10` 抢占条件为 running_token IS NULL，释放后清空 token，没有计划触发时刻或持久化 fire ID。

由此推导：A、B 对同一次 Cron 先后触发，A 完成并释放后 B 仍能抢占，可能执行两次；慢任务重叠时只有一个能抢占，但不构成同次触发去重。
在 A 修改 Cron，B 可继续按旧 Cron 触发；在 A 新增的任务，B 未装载，A 故障后无法直接由 B 接手。
数据库状态检查会阻止已暂停/删除任务实际执行，不能误报为“暂停后必然继续执行业务”，但 B 的旧 trigger 仍存在。
进程崩溃留下 running_token 后，当前 sys_job 恢复需要暂停、匹配 token、超过 5 分钟及显式确认死亡；不是自动故障转移。
当前可执行目标仅两种 heartbeat，因此已确认的是调度基础缺口，不能声称真实 MES 同步已重复写入。

建议：先明确单一调度角色或持久化集群调度方案，补同次触发/业务幂等与恢复验收。
当前没有独立 scheduler enabled 开关；关闭 mes.system.enabled 会一起关闭系统能力，不可当作现成隔离方案。
也不能仅增加 spring.quartz 配置：自建 factory 不自动消费其余配置，且 init 中有 scheduler.clear()；接入共享 JobStore 前必须审查启动清理和任务同步语义。

### M2 / P1（条件触发）：独立本地磁盘导致跨节点文件不可见

证据：`application.yml:48-50` 默认 local；`SystemConfiguration:125-130`；
`LocalPrivateFileStorage:27-49` 按本地 root 写文件，并在缺少文件或大小不符时返回 NOT_FOUND。
附件元数据共享并不会共享文件字节：A 上传、B 下载即可失败；A 故障后粘性会话也无法提供其磁盘内容。
共用可靠挂载可以消除这一条件，但当前配置本身未证明存在共享挂载。
建议使用已实现的私有共享 FileStorage 适配并迁移既有文件，补跨节点上传/下载/删除及节点失效验证。

### M3 / P1（错误配置触发）：物理业务库防重复检查仅在节点内

证据：`V11__managed_factory_datasource.sql:16,27` 只约束 host/port/db name 文本和 datasource ID；
`ManagedDatasourceService:105,464-478,524-528` 用本地 physical map 对实际 server UUID/database 做冲突检查；
`RuntimeRegistry:88-91,113-117` 的归属状态同样是节点内。

两个不同主机别名可以指向同一个物理 schema，从而形成两个不同 datasource ID；A 节点只加载工厂 F1，B 只加载 F2 时，双方都看不到对方的 physical map。
业务隔离依赖独立数据库，此时可能让两厂访问同一批业务数据；同节点加载两份定义时才可能暴露冲突。
这不是已发现真实配置重复，而是静态确认的全局防护缺口。
上线前应核对所有绑定的物理身份，并使中心侧绑定校验能够执行全局隔离约束；不能把节点内 map 当作集群保护。

### M4 / P2：参数和字典共享缓存可能被滚动启动或并发旧读取覆盖

证据：`SystemConfiguration:104-109` 每节点启动预热；`SysConfigServiceImpl:81-91,128-183` 在事务内写 Redis、无版本发布；
`SysDictTypeServiceImpl:132-139` 批量回填字典；`DictUtils:31-33` 与 `RedisCache:32-35` 写入不设置 TTL。

例：B 启动读到旧参数后暂停，A 更新参数并提交新值，B 随后预热将旧值覆盖，缓存不会自动过期。
事务提交前 Redis 发布也可能暴露未提交值；数据库回滚不撤回已经写入的缓存。
这是既有并发一致性风险，多节点和滚动发布增加触发机会。
建议另立小范围任务处理提交后失效、过期恢复和启动预热竞争；不与下述已确认的路由缓存取舍混为一谈。

### M5 / 已接受边界：工厂路由不是即时全节点切换

证据：`FactoryMetadataCache:57-88`、`RoutingMetadataStore:26-35`、`application.yml:65-67`；
`ManagedDatasourceService:503-521` 仅定期核对本节点已初始化池。
路由定义固定 TTL 为 300 秒，允许失效前开始的读取在失效后回填；淘汰失败记录日志并等过期。
TTL 从 SET 起算，不能承诺管理提交后最多 300 秒全部生效；慢读和已准入长请求可扩大墙钟窗口。
5 秒池核对不是全节点准入屏障，旧缓存仍可能重新建池；已获租约事务继续使用原连接。

这是 2026-10-11 用户确认的取舍，本轮不建议恢复已删除的代际/outbox 协议。
部署与运维必须接受停用/改绑收敛窗口，真正切库安排停止新写入和排空；不能宣称在线迁库无损或即时撤销全部事务。

### M6 / P2：跨节点消息实时通知缺失，轮询可恢复

证据：`MessageEventHub:13-20` 仅保存本进程 SSE 连接；`SysMessageServiceImpl:127-129` 提交后调用本进程 events.changed；
`mes-base-ui/src/views/Inbox.vue:8-9` 只处理 inbox-changed，页面可见时每 30 秒重新读取。
A 写消息、用户 SSE 在 B 时收不到即时事件；数据库消息仍存在，可由轮询/手动刷新读出，不能写成消息丢失。
同一用户粘性路由不能确保其他发送者或后台任务与接收者落在同节点。
建议按产品时效要求选择跨节点事件传播或明确接受轮询；同时验证代理 SSE 超时/缓冲与重连。

### M7 / 容量风险：连接池和中心准入负载随实例数增加

证据：`DruidPools:36-59` 每个物理池独立创建；`application.yml:21-25` 的 initial/minIdle/maxActive 为 5/10/20；
`RuntimeRegistry` 正常启用池没有按工厂闲置时间卸载机制；每节点 reconcile 与 session outbox 重试独立运行。
若 N 个节点都访问 F 个工厂，稳定版本、没有退休池和临时探测池时，总连接配置上界约为 N × (1 + F) × 20。
例如 4 节点、10 工厂，上界约 880，分布在中心库及各业务库；每个库约 80，不是单库 880。排空期间新旧池并存会进一步增加连接。
这是配置预算，不是实际连接数、吞吐或容量测量。中心库仍承担每请求账号/权限读取；同一个工厂数据库不会因为应用实例增多而自动分片。
扩容前按每库 max_connections、实际热工厂数、CPU/慢 SQL 和 Redis 延迟确定池配额，做阶梯压测。

### M8 / 部署前提与故障接管缺口

- `SystemCacheNamespace:18-26` 始终将完整中心 JDBC URL 的 SHA-256 加入 Redis 前缀，即使显式配置 namespace 也如此。同一库使用不同别名、参数顺序或 URL 参数会分裂会话/验证码/缓存。集群须统一完整 URL、namespace、Redis 实例及逻辑 DB；当前本机 profile 不能直接当多机模板。
- `FactoryRuntimeConfiguration:61-64` / `DatasourcePasswordCipher:37-44` 要求节点共享正确的数据源密钥；密钥不一致会出现只有部分节点能连接业务库。时区、时钟、版本和缓存模型也需统一。
- `FoundationController:25-26` 的 /health 固定返回 UP；它不检查本节点到中心库/Redis 的可用性。若负载均衡只用它，依赖失联节点仍可能接流量。未找到已交付的负载均衡/readiness/滚动发布拓扑；没有据此断言框架完全不支持优雅退出。
- `DruidMonitorService` 和 DDL 文件快照属于节点内观测，负载均衡后要保留节点标识，不能当作集群总量。
- 应用多节点不等于 Redis 分片集群就绪。`UserSessionStore:18-48,58-86` 的 Lua 同时访问 session/epoch/index 键，键未设计同一 hash tag，撤销/注销脚本还有动态键访问。Redis Cluster 要求一次 Lua 的相关键在同槽，不能直接切换分片模式；参见 [Redis 官方集群说明](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)。本轮未运行 Cluster，不把共享单实例 Redis 测试称为分片兼容验证。
- 没有发现覆盖所有业务写请求的通用幂等机制；负载均衡故障转移不能无条件重放非幂等写请求。中心库/Redis 自身高可用、恢复后的会话/任务状态还需独立验收。

## 4. 建议的最小落地顺序（尚未实施）

1. 保持单代码与中心库 + 多业务库，先统一集群配置、共享私有文件、物理库绑定核对及连接预算。
2. 隔离调度职责或完成持久化集群调度，解决定义同步、同次触发重复、崩溃恢复；接真实执行器时补业务幂等。
3. 修补参数/字典缓存发布竞争；消息按实时性要求补传播或明确轮询边界；路由缓存沿用已确认的一致性合同。
4. 补依赖就绪检查和摘流/排空；用同一中心库、同一共享 Redis 的两个完整应用进程、真实业务数据库与负载均衡完成验收。

双节点验收至少覆盖：A 登录 B 访问；A 改密/禁用 B 拒绝后续旧会话；同 Token 并发不同 Factory 无串厂；别名指向同物理库拒绝；A 上传 B 下载；同次 Cron 去重、跨节点修改 Cron 和 kill 后恢复；配置变化按约定收敛且旧租约正确排空；滚动启动不回填旧参数；某节点依赖断连被摘流；负载逐级增加时测每库连接数/中心 SQL/Redis 延迟。

## 5. 本轮实际验证回执

```sh
mvn -B -ntp -pl mes-admin -am -Dtest=SyncCoordinatorTest,SessionAdmissionTest,IndexedRedisSessionTest,RoutingMetadataRedisTest,RequestSnapshotTransactionTest,ManagedColdPoolTest -Dsurefire.failIfNoSpecifiedTests=false -Dmes.test.redis=true test
```

| 测试 | 数量 | 本轮结果与证据等级 |
| --- | --- | --- |
| SyncCoordinatorTest | 8 | 通过；Mock 控制流，非真实 DB 竞争 |
| SessionAdmissionTest | 7 | 通过；H2 账号版本/outbox 与 Mock Redis |
| IndexedRedisSessionTest | 1 | 通过；自有临时 Redis 的真实 Lua，并发发行/撤销/注销 |
| RoutingMetadataRedisTest | 1 | 通过；两个独立 Redis 客户端 + H2，固定 TTL、允许旧回填、故障拒绝 |
| RequestSnapshotTransactionTest | 3 | 通过；H2 固定租约、退休、并发上下文 |
| ManagedColdPoolTest | 5 | 通过；H2/Mock 物理探测的冷池准备与清理 |

合计 25 通过，0 失败/错误/跳过；Maven 用时 12.676 秒。日志 `/tmp/mes-base-multinode-audit-20261012.log`。
日志里的退休池关闭失败和缓存淘汰失败属于测试注入，不能误报生产故障。
现有 `ManagedRuntimeMySqlTest` 包含双独立 JVM 抢占探针，但本轮未运行；没有新增测试，也未执行全量 verify、打包 smoke 或前端 test/build。
本轮测试只复核已有基础能力；M1/M2/M3/M4/M6 的具体跨节点故障场景是源码推导，未冒充部署复现。
首轮审查文档追加后 `git diff --check` 通过；既有源码、配置和前端工作区修改未作处理。

## 文档变更记录

- 2026-10-12：完成首次只读架构审查，记录 M1–M8 与 25 项定向测试结果。
- 2026-10-12：用户确认当前没有扩容计划，未来计划启动时按本次审查修复；将原报告 §七迁为本独立文档，新增计划状态、启动条件和追踪表；原报告及下一阶段规划保留链接。仅整理文档，未修改业务代码或配置。
