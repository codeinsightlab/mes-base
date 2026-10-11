# 过度设计专项复审：对比 2026-10-09 评审（2026-10-11）

> 评审性质：**只读复审**。未修改任何源码、配置、数据库或服务，未提交、未推送、未部署。
> 本文为新增文档，不覆盖既有报告；冲突处以上轮评审与本轮实施报告为准。

- 评审对象：`/Users/user/work/lixin/mes-base`，工作区状态 `main` / HEAD `7fb7501`（含大量未提交改动）
- 本轮迭代边界：`git diff 560600b..工作区`，另含未跟踪新增文件
- 评审问题：本轮（认证、授权与路由缓存迭代）相对上轮评审**是进步还是退后**，是否存在**过度设计**
- 基线文档：`2026-10-09-architecture-review.md`、`-actions.md §5.7`、`2026-10-11-request-admission-implementation.md`、`2026-10-11-pro-workorder-latency-review.md`

---

## 一、基线：上轮评审的立场

| 项 | 上轮结论 |
| --- | --- |
| 后端架构本体 | 7.7 / 10，判「有条件放行」 |
| 技术缺陷 ① | 中心库热路径 `PersistenceStatementGuard → FactoryBusinessDataSource.verify → ManagedDatasourceService.resolve`，**每个业务 SQL 都重读中心配置**，开销随 SQL 数量增长 |
| 技术缺陷 ② | `resolve` 是**整个 Service 的 `synchronized`**，锁内含建池与真实连接探测，某厂首次加载会阻塞其他厂 resolver 与同锁列表/详情 |
| 阶段建议（§5.7） | **停止无边界的基础扩建，冻结中心库 + Factory 独立业务库架构，开始一个业务试点**；真实双厂验收完成前不宣称基础层已生产验收 |
| 动作裁定（U12） | 拒绝「拆上帝服务 / 批量重构 / 拆大页面 / 改注入风格」 |

**关键**：上轮已经把"继续扩建 Base"本身列为需要停止的行为，而不是列为待办。

## 二、本轮规模与实测

- 变更规模：`93 文件 / +5531 / -808`（`560600b..工作区`），另有未跟踪新增：
  `V21__routing_metadata_generation.sql`、`FactoryMetadataCache`、`RoutingMetadataStore`、
  `BusinessRequestSnapshot`、`AdmissionAccount`、`RoutingMetadataMapper`、`FactoryExecutionMetadata`、
  `RoutingCacheFixture`、`ManagedColdPoolTest`、`RequestSnapshotTransactionTest`、`RoutingMetadataRedisTest`。
- 本轮独立复跑（本复审执行）：
  `mvn -B -ntp verify` → `Tests run: 223, Failures: 0, Errors: 0, Skipped: 7`，**BUILD SUCCESS**，34.9s。
  断言：工作区可编译、可打包、默认回归通过。带 `-Dmes.test.redis=true` 时按实施报告为 243 项 / 4 跳过，本次未复跑。
- 未复跑：真实隔离 Redis 用例、多节点、真实 MySQL opt-in、前端 test/build。

## 三、过度设计判定

### 1. 路由元数据强一致缓存（严重度：高）

**构成**：`RoutingMetadataStore` 三段 Lua（`READ` / `PUBLISH` / `INVALIDATE`，见该文件 13/22/27 行）
+ 共享 hash `factory-routing:v1`（generation + 随机 epoch + `binding:<factoryId>`）
+ `FactoryMetadataCache.read()` 的 CAS 发布与三次重试、`changed()` 同事务推进代际（103 行）、
`retryPending()` 5 秒轮询（119 行）
+ `V21 sys_routing_metadata` 单行代际/失效确认单 + `RoutingMetadataMapper` 的 `advance/pending/delivered/failed`
+ 准入 SQL 增加「最低路由代际」标量子查询（`AdmissionAccount.routingGeneration`，`FactoryMetadataCache.generation()` 消费）。

**过度设计的根因**：缓存里放了**安全相关字段**——`factory_status`、`datasource_status`、`revision`
（`RoutingMetadataMapper.COLUMNS`），并且 `ManagedDatasourceService.binding(definition)` 直接据缓存内容
抛 `FACTORY_DISABLED` / `RUNTIME_NOT_CONFIGURED`。**一旦缓存里有"能不能用"，失效就必须是强一致的**，
于是不得不引入 generation 栅栏、epoch 防回填、CAS 发布、V21 持久失效单、后台恢复、以及把代际投影塞进入口 SQL。
整套协议的可追溯来源是"缓存了启用状态"这一个决定。

**收益与代价不对称**：

| | 中心 SELECT / 请求 | 说明 |
| --- | --- | --- |
| 本轮之前 | 6（账号角色 1、入口工厂 1、全菜单 1、权限 1、事务绑定 2） | 实施报告口径 |
| 本轮之后 | 2（账号/角色/最低代际 1、当前权限 1） | 实施报告 + 预算用例断言 |

其中被去掉的 4 次里，**只有「入口工厂」这一次是缓存换来的**（换成 1 次 Redis Lua），
「全菜单 1」来自授权路径去全菜单减法（与本缓存无关，且是真正的简化），
「事务绑定 2」来自 `BusinessRequestSnapshot` 消除逐事务/逐 SQL 重读。
**收益主体是请求快照，不是 Redis 缓存**；缓存换来的是 ~1–2 次中心 SELECT，
代价是一整套分布式一致性协议 + 一个新的 503 语义（`ROUTING_INVALIDATION_PENDING`）。

**更简的替代**：把工厂绑定与启用状态折进入口**本来就存在的那次**中心查询
（`SessionAccountMapper.account` 已读账号/角色），额外一次 JOIN 即可，然后保留请求快照。
这样可去掉 epoch、CAS 发布重试、V21 失效单与轮询、以及准入 SQL 的代际投影，而热路径收益基本不变。
当前方案并非错误，但在**没有多节点部署**的前提下，它的复杂度是为不存在的规模买的。

### 2. `RuntimeRegistry` 的 Reservation / Lease 双生命周期（严重度：中，复杂度收益比最高）

新增第二套所有权概念：`Reservation`（91 行）、`reserve`（124）、`reservedVersion`（143）、
`hasVersion`（151）、`registerReserved`（155）、`tryDrain` 增加 reservation 判定（293）。

问题不在于"该不该保请求一致性"，而在于为它引入的语义分支过多：
- `registerReserved` 用 `r.retired == reservation.retired` 做分组匹配、"只 attach 已准入组"；
- `reserve()` 的跨厂 identity 冲突检查与 `registerDedicated` 重复；
- 退休池"仍可对已有 reservation 懒建"导致同一 identity 可同时存在 current 与 retired 两个 runtime。

它覆盖的是「准入之后、首个事务之前」的窗口——在热路径上该窗口极短，而这块已成为全仓最难推理的代码。
这是**收益最薄、代价最高**的一项。

### 3. 会话撤销 outbox（V19 + V20）（严重度：中）

`SessionRevocationService`（105 行）：attempts/next_attempt_at/last_error、5→300 秒指数退避、
`ORDER BY next_attempt_at,user_id LIMIT 100` 公平排序、500/批版本查询、`afterCommit` 逐条交付。
`UserSessionStore.REVOKE` Lua 遍历该用户**全部**会话。

而**安全实际由 DB fence 保证**：`TokenService.resolve` 每请求读账号状态/authRevision，旧版本会话本来就进不来。
Redis 撤销在这个结构下主要是**清理**（防御纵深），实施报告自己写"表中存在记录不代表 Redis 撤销一定失败"。
为清理路径配备完整可靠投递协议（含公平调度与退避），代价偏高。

### 4. 启动期数据依赖硬失败（严重度：中，属新增风险而非纯过度设计）

`SystemConfiguration:70` 在 `SmartInitializingSingleton` 中调用 `installEndpointScopes`
→ `SystemPermissionService.validateMenuAuthorization()`（97 行）→ 查 `selectMenuList` 与
`selectFactoryAssignedMenuIds`，发现「权限码跨平台/业务复用」或「工厂角色持有平台菜单」即抛异常。

把**数据内容缺陷**升级为**启动失败**，实施报告也承认"之前可能被静默过滤的错误配置现在会阻止启动，
这是明确的迁移行为变化"。这与上轮 U14 的取向（"有具体入口再设计登记与负例，不现在预建一套框架"）相反。

### 5. `AdmissionAccount extends SysUser` 仅承载一个投影字段（严重度：低）

`AdmissionAccount` 继承持久化实体只为多带 `routingGeneration`。小，但是坏味道；投影字段放在独立 record 更干净。

## 四、真正的进步（不可忽略）

1. **§5.7 点名的两个缺陷都真修了**：
   - 每业务 SQL 重读中心配置 → 请求快照 + guard 只验固定身份/租约；
   - `resolve` 全程 `synchronized` → `preparationLocks[64]` 分段锁（`ManagedDatasourceService:88/453`），
     网络探测移出全局锁，"只有发布串行"。
2. **删掉了逐请求全菜单分类做权限减法**。这是授权路径上真正的**减法**，与"扩建"方向相反，明确加分。
3. **验证增强**：190 → 243 项；新增真实隔离 Redis 用例验证 Lua；`ManagedColdPoolTest` 验证 16 并发只建 1 池；
   `RequestSnapshotTransactionTest` 用两个独立 H2 库验证事务生命周期。
4. **证据诚实度罕见地高**：反复声明"H2 毫秒数不能与真实 MySQL/HTTP 直接比较""不能声称真实接口已降到 3.61ms"。
5. 登录 `NULL` perms 触发的 `List.copyOf` NPE，修复范围最小且落在真实根因。

## 五、判定

**净进步，但"过度设计"这条轴上出现一次明确的方向偏离。**

- 代码质量、缺陷修复、验证覆盖：**进步**。
- 架构复杂度、新增运行风险、以及是否服从上轮"停止扩建、开业务试点"的阶段建议：**退后**。

用一句话概括：**这轮做的每一件事都有道理，但最优的做法被排在了后面**——
收益/代价比最高的（授权去全菜单、请求快照）和最低的（强一致缓存协议、双生命周期、清理用可靠投递）
被放在同一个迭代里交付，而后者服务的目标是**多节点生产 + 高频管理变更**，这两者当前都不存在。

**风险提示**：下一轮若仍无业务试点、而是继续新增 Base 子系统，本次的"局部退后"会转为实质退后。

## 六、建议

1. **设复杂度预算**：下一个迭代不再新增基础子系统。按 §5.7 选一个依赖最少的现有基础资料业务，
   跑通「Schema → Service/Mapper → 权限/Factory → API → 双厂真实验收」闭环，让缓存与快照被真实流量消费一次。
2. **评估简化路由缓存**：保留 `BusinessRequestSnapshot`（收益主体），把绑定与启用状态折进入口已有的
   中心查询；可去掉 epoch、CAS 重试、V21 失效单/轮询与准入代际投影。改动范围小、语义更直白。
3. **回收启动期硬失败**：把 `validateMenuAuthorization` 降级为启动告警/管理审查入口，
   或至少提供显式的开关，避免菜单数据缺陷导致服务无法启动。
4. **会话撤销**：确认 DB fence 已覆盖安全目标后，评估把 outbox 的公平调度与退避降级为简单周期重试。

## 七、评分（沿用上轮同一套权重，保证可比）

> 方法：使用 `2026-10-09-architecture-review.md` 的 8 维度与权重，逐项按本轮实际状态重打分；
> "架构本体"沿用上轮口径 = 剔除 B5 后按剩余 86% 权重归一。评分为本轮独立判断，非引用既有报告。

| # | 维度 | 权重 | 上轮 | 本轮 | Δ | 主要依据 |
| --- | --- | --- | --- | --- | --- | --- |
| B1 | 分层与模块边界 | 12% | 7.5 | 7.4 | -0.1 | 新增 `dev.mesbase.common.core.redis.RoutingMetadataStore`（core 邻近包引入 Redis 依赖）；`FoundationAccessInterceptor` 以 `Consumer<FactoryScope>` 承接准入回调；其余分层保持 |
| B2 | Factory 隔离与持久化拓扑 | 16% | 9.0 | 8.8 | -0.2 | §5.7 两个缺陷真修（分段锁、探测移出全局锁）；但 Reservation/Lease 双生命周期复杂度高、收益薄 |
| B3 | 认证 / 授权 / HTTP 分类 | 12% | 8.5 | 8.6 | +0.1 | 取消逐请求全菜单分类减法、改 URL 范围 + 动作权限（实质简化）；`validateMenuAuthorization` 启动期硬失败为新增风险 |
| B4 | 事务一致性与同步 | 10% | 8.0 | 8.3 | +0.3 | 新增双 H2 库事务生命周期用例、REQUIRES_NEW 复用原快照 |
| B5 | 安全与密钥管理 | 14% | 3.0 | 6.5 | **+3.5** | 历史凭据清理（用户授权）、本地取消跟踪 + 守卫脚本、PostgreSQL 死依赖移除；仍缺 key-id 轮换、TLS/SSO，守卫有已知漏检类 |
| B6 | 测试与可验证性 | 12% | 8.0 | 8.3 | +0.3 | 190 → 243 项；真实隔离 Redis、冷池并发、事务生命周期；仍未复现多实例，CI 已被用户决定移除 |
| B7 | 文档与治理一致性 | 10% | 7.0 | 6.8 | -0.2 | 文档诚实度仍高；但"追加小节取代前文"的叠加模式加剧，单份准入文档已 6+ 段互相取代 |
| B8 | 工程卫生 / 可维护性 | 14% | 6.0 | 6.4 | +0.4 | FQN、PostgreSQL 依赖、README 等上轮项已修；但本轮又留下一个大型未提交工作区与 11 个新类 |

- **加权合计（含 B5）：** 上轮 7.08 → 本轮 **7.64**
- **架构本体（剔除 B5，剩余 86% 归一）：** 上轮 7.74 → 本轮 **7.83**
- **判定**：**总分上升约 +0.5，但上涨几乎全部来自 B5 的凭据整改（+3.5×14% ≈ +0.49），
  与"本轮扩建"无关；架构本体 7.7 → 7.8 实质持平。**
- **新增维度（原权重表未含，单独计）：设计克制 / 过度设计 — 约 6.0/10，且方向为负**
  （上轮隐含水平约 7.0–7.5）。本节将其单独列出，不并入上表，以免篡改原评分口径。

## 八、未验证边界

- 未复跑真实隔离 Redis 用例、真实 MySQL opt-in、多节点、前端 test/build、浏览器验收。
- 本次 `mvn -B -ntp verify` 为默认（不带 `-Dmes.test.redis=true`），223 项 / 7 跳过；
  与实施报告的 243 项 / 4 跳过口径不同，未复现其 redis 开关下的完整数字。
- 未验证缓存失效在真实多实例下的并发行为；"收益 ~1–2 次中心 SELECT"基于实施报告的分项口径推导，
  非本次实测。
- 未评估工作区未提交改动被其他并行任务覆盖的风险。

## 八、2026-10-11 简化后的复核意见

本节保留上文历史判断，针对用户后来明确接受低频路由配置短暂不一致、完成缓存简化后的
当前源码补充。静态核对 RuntimeRegistry、FactoryMetadataCache、RoutingMetadataStore、
SessionAccountMapper.xml、SystemConfiguration 与 SystemPermissionService，未重跑测试。

- 第 1、2、5 项所列路由代际/epoch/Lua/持久恢复、Reservation 与 AdmissionAccount 已移除，
  不再属于当前整改清单。V21 仍作为待核实应用情况的历史迁移保留，运行链路不依赖它。
- “缓存启用状态就必须强一致”过于绝对。是否立即生效取决于业务契约；用户已接受短暂
  路由旧值与固定 TTL，应按该契约评价。账号禁用/认证版本保持不同的既有安全要求。
- 请求快照与缓存共同降低路由 I/O，具体每一条被消除的 SELECT 不宜唯一归因于某机制；
  绑定折入账号查询是候选方案，不证明必然优于已确认的缓存方案，不再为此切换实现。
- 会话撤销 outbox 的持久恢复是用户明确保留的要求。DB 版本准入已保护新请求，不能由
  此自动推出 Redis 撤销恢复无价值；公平排序/退避避免失败积压和饥饿，也不专属于高频场景。
  若改为最终过期清理，需要新的明确生命周期决定，本轮不建议混入路由简化。
- 启动硬失败仍存在，需拆分评价：HTTP 注解缺失、平台/业务接口同码等代码契约冲突
  与存量角色菜单授权错误不是同类。后者导致服务不能启动也就无法从管理 UI 修正，
  可评估启动告警加管理入口严格校验，但必须先证明请求授权不会接受错误配置，不能
  一概降级或以关闭检查开关放行越权。
- 上文“冲突以报告为准”的表述不应作为事实裁决规则；当前源码、用户最新决定与对应
  版本的执行证据优先。大量未提交修改下，HEAD 和行号不足以冻结审查对象。

结论：报告对旧版复杂度的诊断和停止无边界扩建的阶段建议有价值，但不能整体当作
当前失败清单；已做减法保留，不继续重构基础设施。聚焦初始化修复的独立复审、菜单
数据启动边界及已发现的响应体超时，之后推进一个业务试点。未修改业务代码或配置。
