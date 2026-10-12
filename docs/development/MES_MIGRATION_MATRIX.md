# MES Migration Matrix

## 2026-10-06 当前范围：基础架构先行，业务分析后置

用户决定撤出生产监控和人员分析。两项能力的后端 `mes-mes`、前端页面/API 及专属测试已归档到 `migration-backups/2026-10-06-deferred-production-analytics/`，不再参与构建或 Mapper 扫描。V15 撤销菜单和角色菜单关联；V13 保留原样。此前关于继续迁入或默认关闭这两项能力的内容属于历史记录，由本节覆盖。

当前保留工厂/数据源、权限/资源隔离、系统监控、同步协调和三工厂持久化基础。隔离与事务验证继续使用独立 TEST ONLY 探针，不能依赖尚未接入的业务。后续恢复需重新审查业务 Schema、SQL、权限、Factory 范围及页面契约；归档不是验收通过。


## 2026-10-06 范围细化：数据源、生产监控、人员分析与同步协调

用户最新确认覆盖前一节的全分支范围表述：当前主要迁移数据源管理、生产监控和人员分析。外部系统功能不在范围内，Base 暂时只预留外部接口，不实现 ERP/WMS/外部 MES 等连接适配器、远程调用或具体外部同步流程。通用同步协调能力明确纳入：迁入分支 `mes_master_sync` 状态表及其数据库抢占、执行令牌和恢复机制，作为后续系统同步的统一分布式互斥基础；不因其源代码位于 erp 包就整块排除，也不带入 ERP 依赖。

已核对来源 `feat/multi-factory-isolation` / `bdc299dade855177a3e275199ffde6e72310e393`：

- 分支 `AGENTS.md:1045–1065`「定时同步任务并发控制（强制）」。
- `MasterSync`、`MasterSyncExtraMapper.xml` 及 `MasterDataSyncService.claim/recoverStale`。
- 源 SQL 的抢占和状态更新实际包含 `factory_id`，因此业务同步互斥键为 `factory_id + sync_key`，不能误迁成全局只按 sync_key 的锁。表的唯一约束、归属与实际 DDL仍需在实现前复核；目前只核对源码契约，没有执行数据库验收。

迁入必须保留的约束：

1. 使用数据库条件更新抢占，仅非 RUNNING 状态可进入运行；受影响行数为 1 才允许执行，失败明确返回正在进行，不等待同流任务。
2. 每次抢占生成唯一 run_token；进度、完成、失败均校验 factory_id + sync_key + run_token，并验证 RUNNING 状态，防止旧运行覆盖新状态。
3. 手工、Quartz 和其他同步入口复用同一套协调逻辑；sys_job 只调度，不替代同步互斥。禁止以 JVM 锁、synchronized 或 Quartz concurrent 作为跨节点同步锁。
4. 保存开始、结束、上次成功、处理数量、错误摘要及必要同步水位；迁移有效字段，不固化源 ITEM/ITEM_LOT 等外部业务类型。
5. 提供崩溃遗留 RUNNING 的恢复策略。源实现根据 updated_at/started_at 检查租约，通过旧 run_token 和超时条件将旧运行标为 FAILED，然后重新抢占；适配时需验证活动更新时间、恢复竞争与旧执行者令牌失效后的停止行为，不能把状态更新保护当作外部副作用已被隔离。
6. 通用协调机制不依赖 ERP 厂代码、外部凭据、外部客户端或已实现的同步器；未接入执行器的预留接口明确不可用，不返回假成功。

更新后的顺序：数据源与工厂绑定/持久化 → 通用同步协调基础（可与前者按依赖安排）→ 生产监控 → 人员分析。监控与分析所需本地业务表、查询口径、权限和页面随链路迁移；外部数据只通过预留契约定义来源，不以实现外部同步作为完成本轮的前提。

本轮仅更新迁移范围与源码约束记录，未新增表或同步实现、未修改运行配置、未连接外部系统，未运行迁移功能测试。前一节 ERP 外部实现及其余分支功能的扩展安排由本节收窄，不再作为当前执行范围。


## 2026-10-06 用户确认：多工厂分支增量为后续主要迁移范围

用户明确：接下来主要迁移 `feat/multi-factory-isolation`，该分支增量能力均需要。此前只将分支作为隔离设计参考、暂不迁数据源管理后台，以及“正式 MES 能力需另行授权”的阶段限制，不再作为排除本分支功能的依据。迁移采用 SOURCE_PRODUCT_PORT，保留源产品字段、流程和有效行为，按现有工程规则适配；不能用 Base 已有替代实现直接宣告等价完成。

### 固定来源与差分方法

- 来源仓库 `/Users/user/work/heri/ktg-mes`，只读；读取 Git ref，不切换或修改源工作区。
- 分支 `feat/multi-factory-isolation`，本轮 HEAD `bdc299dade855177a3e275199ffde6e72310e393`。
- 与当前 `master` 的共同祖先 `7f5226cd8d65b5a703c1f1f3fce35796a68fedeb`；差分使用 `git diff master...feat/multi-factory-isolation`，不是两分支 tip 的简单相减。
- 静态差分共 341 个变更文件，17,357 行新增、757 行删除。数字是全分支差分，包含文档、测试、脚本等，不是待迁功能数量。没有执行源分支测试，也未证明增量全量链路可运行。
- 对应前端/PDA版本仍需按调用接口和 Git 历史核对，不假定后端同名分支代表前端同一版本。

### 执行顺序与验收边界

| 顺序 | 增量能力 | 当前证据与迁移要求 |
| --- | --- | --- |
| 1 | 工厂管理、数据源管理、工厂与数据源绑定 | 源分支 FactoryController/ManagedDatasourceController → ManagedDatasourceService → MfFactory/MfDatasource Mapper；Base 当前 mes.runtime.factories → InMemoryFactoryCatalog。必须补齐平台库存储、管理 API/页面及运行时装配，YAML 静态目录不算产品功能已迁入。 |
| 2 | 选厂/偏好、平台与工厂角色、请求授权、会话和缓存隔离 | 对照分支与 Base 已有行为；保留无 UserFactory 准入、权限按当前厂读取、切厂清理、无首厂或默认库兜底。已有隔离基础需验证等价，不整套复制或整套丢弃。 |
| 3 | 数据源生命周期、版本绑定、Mapper/事务/异步/任务范围、DDL与元数据 | 对照 registry、绑定和任务链路；保留独立平台/业务事务、版本失效及连接错误明确失败；覆盖两厂、跨库、禁用连接和事务负例。 |
| 4 | ERP连接/同步、工单主数据与请求、归档、生产监控、人员分析及其余 MES/Wheel 增量 | 全部纳入范围，先追真实 API → Service → Mapper/外部契约及页面，按依赖分批 PORT；客户规则抽离不能成为丢弃整个通用功能的理由。外部调用验收与本地/Fixture证据分开。 |
| 5 | 增量配置、DDL、页面、迁移工具与测试 | 有效配置和 schema 随消费者迁移；测试保留有效场景并适配新契约，过期实现测试与临时探针不照搬。实际连接、公司数据和凭证不复制。 |

开发/生产是运行环境，工厂是业务范围，数据源是存储连接。当前 DEV/PROD 工厂是开发测试装配；后续工厂与数据源管理以平台库记录为权威，配置文件负责平台库与基础环境，避免环境名成为工厂身份规则。

本条是用户范围确认与静态差分记录，不是完成清单。下一实现闭环从工厂与数据源管理开始；各能力必须分别记录已迁入、部分迁入、缺失及实际验收结果。本轮没有修改业务代码、数据库或运行服务，没有提交/推送/部署。


> 2026-10-06 当前：Phase 1 全量基础系统迁移持续执行，六模块与 Source 系统页面已接入，整体 MIGRATING。下方 Phase 0 计数/候选矩阵为历史盘点；当前进度以[续接快照](MES_BASE_REALIGNMENT_REPORT.md#2026-10-06-登录隔离与浏览器工作包续接快照)和[验收记录](../reviews/2026-10-06-phase1-verification.json)为准。

2026-10-05；状态：候选策略；common 首包已迁入验证，其余待实施。Source Product=ktg-mes；现有功能默认进入候选。COPY/PORT/REDESIGN/DROP/REVIEW是策略，不用DONE掩盖部分重写。代码复用许可/所有权独立核实，来自旧项目本身不是拒绝理由。

- COPY：继承通用实现，可改package/依赖；不重新发明。
- PORT：保留字段、业务设计和页面流程，适配Vue3、HTTP、Scope/授权及必要Service编排。
- REDESIGN：功能必留，仅重做有证据的耦合/模型/事务边界；不丢产品流程。
- DROP：只排除有明确证据的专有/工资/责任扣款/测试/Secret/生产数据；组合功能须拆通用部分。
- REVIEW：写明待核实项；仍在候选中，不等于默认不迁。

YES为静态源码存在，PARTIAL为链路未完整关联或非DB接口；Web/PDA是API消费证据，未关联用REVIEW而不用NO。Base NO仅依据Base正式main/src未有该模块。Wheel是155个Controller候选的镜像/变体，不另复制一套Core；在每项Notes保留变体核对要求。优先级P0系统/最小依赖，P1生产交付闭环，P2可选扩展，P3剥离项；顺序仍受依赖约束。

| Module | Feature | Old Backend | Old Web | PDA | Base Current | Strategy | Priority | Notes |
|---|---|---|---|---|---|---|---|---|
| System / Platform | [F-207 Base](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | PARTIAL：独立HTTP模型/校验；非旧BaseController迁入 | PORT | P0 | 继承公共分页/导出等能力；真实HTTP模型、DTO和权限边界适配；不恢复HTTP200错误。 |
| System / Platform | [F-208 Cache](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | YES (1 consumer) | REVIEW 未关联 | PARTIAL：范围Key；无缓存监控页 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-209 Captcha](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | REVIEW 未关联 | NO | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-210 Common](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-211 Gen](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | REVIEW 未关联 | PARTIAL：CLI/16产物；无旧生成管理页 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-212 Login](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-213 Minio](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | YES (3 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-214 OpenApiClient](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-215 OpenApiClientRoute](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-216 OpenApiDiagnostics](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-217 OpenApiToken](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-218 OpenApiVerification](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-219 PreviewLogin](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (5 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | REVIEW | P2 | 通用价值/来源或数据真相尚未确认；检查实际使用、配置/模拟数据、客户规则及替代入口后定案，不能因实现旧而DROP。 |
| System / Platform | [F-220 Server](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | YES (1 consumer) | REVIEW 未关联 | NO | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-221 Swagger](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-222 SysAttachment](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-223 SysAutoCode](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | YES (37 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-224 SysAutoCodePart](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-225 SysAutoCodeRule](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (36 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-226 SysConfig](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-227 SysDept](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (5 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-228 SysDictData](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | YES (5 consumer) | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-229 SysDictType](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-230 SysIndex](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-231 SysJob](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (2 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-232 SysJobLog](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-233 SysLogin](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-234 SysLogininfor](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-235 SysMenu](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-236 SysMessage](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-237 SysNotice](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | YES (3 consumer) | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-238 SysOperlog](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (1 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-239 SysPost](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (2 consumer) | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-240 SysProfile](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (11 consumer) | YES (4 consumer) | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-241 SysRegister](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (3 consumer) | YES (3 consumer) | NO | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-242 SysRole](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (5 consumer) | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-243 SysUser](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | YES (11 consumer) | YES (4 consumer) | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-244 SysUserOnline](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | YES (1 consumer) | REVIEW 未关联 | NO | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-245 Test](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | DROP | P3 | 工资明细/公司财务责任结算，或显式测试发送/演示接口；只排除专有与测试部分，相关报工数量能力另保留。 |
| System / Platform | [F-246 User](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-247 UserTask](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-248 WechatMiniappOpenId](MES_FEATURE_INVENTORY.md) | YES / STATIC_CHAIN | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | PORT | P0 | 继承现有字段、查询、页面操作和业务规则；适配Vue3/HTTP/权限/FactoryScope、将Controller编排移入Service。不因三层旧代码而整体重写。 |
| System / Platform | [F-249 WechatSubscribeNoticeTest](MES_FEATURE_INVENTORY.md) | YES / STATIC_PARTIAL_OR_NON_DB | REVIEW 未关联 | REVIEW 未关联 | NO：无对应正式MES/功能模块 | DROP | P3 | 工资明细/公司财务责任结算，或显式测试发送/演示接口；只排除专有与测试部分，相关报工数量能力另保留。 |

## 非Controller与交付能力补表

| Module | Feature | Old Backend | Old Web | PDA | Base Current | Strategy | Priority | Notes |
|---|---|---|---|---|---|---|---|---|
| Common | Excel/date/tree/dict工具 | YES | YES | 部分 | PARTIAL | COPY | P0 | 许可与版本核对；范围及DTO适配PORT，不复制客户模板数据 |
| ERP | MasterDataSync/ItemProductionSync | YES | YES工单主数据 | 选择器消费 | NO | REDESIGN | P1 | 保留状态查询/claim/run token，连接与Factory适配 |
| Jobs | TaskService/通知/提醒 | YES | Quartz管理 | 通知消费 | 仅heartbeat | REVIEW | P1 | 逐方法拆通用任务/工资/邮箱/厂特定规则 |
| UI | 选择器/树/上传/分页/打印组件 | 配套API | YES | 部分 | 只有通用CRUD组件 | PORT | P0 | 冻结旧产品交互，不以通用CrudPage替代业务页 |
| PDA | 网络/打印设置/扫码入口 | 部分 | 不适用 | YES页面；设置路由待核实 | NO | PORT | P1 | 设备验证待做，禁止复制真实网络值 |
| Config | Druid/YAML/profiles有效配置 | YES | Druid iframe | 网络设置 | Druid装配/YAML部分 | PORT | P0 | 只迁有消费者的有效key；Redis/分页/文档需整条能力核对 |
| Assets | Secret/客户数据/生产seed/账号/硬编码真实URL | 存在风险 | 存在风险 | 存在风险 | 不得进入 | DROP | P0 | 只排除值与身份；功能能力保留 |
| Source | COPY所有权/第三方许可 | 待核 | 待核 | 待核 | 官方模板MIT已记录 | REVIEW | P0 | 原项目LICENSE不能证明每项公司资产可公开复用；逐包证明，不阻挡功能盘点 |

完整未关联Web/PDA/API列表见Inventory第4节，统一REVIEW/P2，核入口、动态URL与所属功能后归入对应行。本矩阵所有策略是Phase 0提议，代码第二步等待用户决定。

## 业务阶段（模块级候选，不进行本轮深审）

| Module | Feature | Old Backend | Old Web | PDA | Base Current | Strategy | Priority | Notes |
|---|---|---|---|---|---|---|---|---|
| Master Data | 物料/分类/单位/客户供应商/BOM/SOP/车间工作站/资源 | 源码有 | 页面有 | 部分移动API | NO | REVIEW | 后续 | 默认候选，目标PORT；迁移时核字段/许可/Factory范围 |
| Production | 工序/路线/工单/任务/计划排程/报工/确认 | 源码有 | 页面有 | 报工/流程卡等页面有 | NO | REVIEW | 后续 | 不先创造新Domain；成熟流程继承，只局部重做有证据耦合 |
| Equipment / Calendar | 设备点检维修/工装/班组/排班 | 源码有 | 页面有 | 部分API | NO | REVIEW | 后续 | 以实际交付依赖排序 |
| Quality | 模板/检验/不良/缺陷/步骤/记录 | 源码有 | 页面有 | 不良页面有；现场检验页该仓未找到 | NO | REVIEW | 后续 | 不良通用能力保留，工资责任扣款拆出 |
| Warehouse | 库存/入出退领调拨/盘点/条码/托盘 | 源码有 | 页面有 | 扣料/入库/调拨/条码/托盘有 | NO | REVIEW | 后续 | 区分MES本地库存与ERP真相；不在本轮深审 |
| Dashboard / WIP | 生产月汇总/监控/看板/WIP | 源码有 | 页面有 | 未统一确认 | NO | REVIEW | 后续 | 区分真实SQLServer/模拟样本；不整块DROP |
| ERP | 查询/同步/BOM/工单/库存/入库领退料/状态回传 | 源码有 | 配套入口有 | 配套页面有 | 仅未配置失败壳 | REVIEW | 后续 | 通用产品动作保留，供应商连接/协议独立；实现时局部REDESIGN |
| Mobile / PDA | 扫码/报工/流程卡/领料/检验/查询/网络打印 | 部分专用API | 不适用 | 页面/API有 | NO | REVIEW | 后续 | 硬件/网络/运行证明待做，不凭Web宣称通过 |
| Other | 销售询价/订单/通知/报表打印/编号查询 | 源码有 | 页面有 | 部分 | NO | REVIEW | 后续 | 通用打印/通知优先候选，销售可选；专有数据值DROP |

259功能族是扫描候选数，不是逐业务评审完成数；优先工作包以本报告基础设施阶段为准。

## 本轮实施原则补充

用户已明确授权三份文档完成后本轮进入基础设施和系统设置代码迁移。随后明确纠正：已有系统模块是迁移旧代码，不是新增CRUD；不得先生成新实现替代旧功能。先COPY可直接复用的代码，PORT必要依赖/namespace/HTTP/权限/Vue3部分；迁移阶段完全忽略Codegen Skill，仅后续新增功能开发使用。迁移来源记录SOURCE_PRODUCT_PORT，不属于绕过生成。新创建的源文件路径不等于新增产品功能。


### 2026-10-05 最新执行纠正：迁移阶段不使用Codegen

用户明确要求迁移阶段忽略Codegen Skill；不调用、不生成、不作为门槛或例外审批。此前文档关于迁移时使用Skill或辅助骨架的表述由本条取代。现有Codegen代码/产物保留，开发期再使用。系统工具中的旧生成管理能力先冻结，不让其建设阻挡基础系统迁移；基础模块优先admin/common/framework/system/quartz，generator后置为开发工具。源产品COPY/PORT优先；旧架构可沿用，当前Base包结构可以替换。


## 2026-10-05 Phase 1 全量迁移执行记录（进行中）

用户已授权 Source Product 基础系统全量 COPY/PORT；本轮不调用 Codegen，不迁 MES Domain、不提交/推送/部署。已建立 mes-common / mes-system / mes-framework / mes-quartz / mes-generator / mes-admin Maven reactor，源码与配置按职责迁入；Dept/Post/User/Role/Menu/Dict/Config/Notice、日志、在线会话、Quartz、监控前后端正在接通。状态 **MIGRATING**，源码已存在、构建通过均不等于 VERIFIED。

已通过：`zsh -ic 'mvn -B -ntp package -DskipTests'`（package-8）；Node22 下 UI `npm run build`（build-3）。隔离验收数据库仅从本项目自有测试库复制，V1 不改，V2/V3 Flyway 已执行；旧表/开发数据归档保留。原项目只读；本轮开始前两端 dirty 内容备份在 target/phase1/baseline。

实际失败并修复：Boot4/Jakarta 依赖和 Excel/POI 兼容；Vue3 slot/SFC 类型、显式 .vue 引用；Flyway 元数据权限探测使 Druid 连接失效（迁移改用独立连接，未扩大 MySQL 共享服务元数据权限）；重复导入模板权限注解；Quartz resultMap 中 id/result 顺序；打包插件输出薄 JAR 与可执行 JAR 路径差异。凭证仅存忽略目录、模式0600，文档不记录。

尚未验收：真实完整启动、所有页面实际操作、导入/导出、资源归属/权限负例、任务/消息/附件/个人资料全链路、正式 mvn verify/UI test、浏览器验证。后续记录逐包补齐，不能把这一段当作 Phase 1 完成回执。


### Phase 1 执行追加：系统链路、真实 MySQL 与安全合并（2026-10-05）

状态仍为 **MIGRATING**。六个 Maven 模块已接入正式 Source 系统调用链，旧简化平台写接口仅在显式 TEST ONLY 条件下注册；原开发表和数据保留归档，不改已应用 V1–V4。系统登录/Redis 在线会话、组织用户角色、公告消息、附件及监控在本轮独立 schema 上运行；Factory Foundation 保留。

本包证据：此前 `mvn -B -ntp verify`（verify-1）86 项/0失败/3跳过；Source 系统真实 MySQL + HTTP 合成操作首轮 26/26（target/phase1/acceptance-crud.json）；Node22 UI build-8 通过；后端 package-22 通过；backend-11 完整启动，19 个实际接口检查为200。以上分别为回归测试、真实测试引擎、构建与本地运行证据，**不相互替代、不代表完整浏览器验收**。浏览器已实际新增部门，排序必填错误提示有效；余下操作继续执行。

新增合并：Source Role 显式 PLATFORM/FACTORY 表单；Source Quill 完整工具栏、私有公告图片/视频引用与后端 DOM 解析过滤；Druid stat/wall YAML 消费；一次性验证码原子取出；Quartz 立即执行经独立 DB claim、run token 与真实日志，调度副作用延后到提交后，支持显式确认死亡后的受控恢复。Minio SDK 与 YAML 配置结构接入 FileStorage，无源环境值、无公开对象 URL；**Minio 外部实例尚未验证**。

实际失败：新增 Druid Settings 重载构造使 YAML Binder 未绑定，backend-10 启动失败；已移除重载并通过 backend-11。Minio 9 的 JVM HTTP 依赖及 Long 参数兼容已修复。npm/Maven 依赖写入受沙箱限制时用审批沿用用户缓存，未建项目 .m2/.mvn/private mirror。Phase1 文件目录已从原服务目录复制隔离，原文件保留；仅本轮18081进程重启，原18080/15173服务未动。

待验证边界：新 Source 安全测试、Quartz实际启停/执行/恢复、Excel导入导出、附件归属负例、完整页面操作及全部矩阵；新构建与当前运行JAR版本仍按日志逐次记录。未提交、推送、部署或迁入正式MES业务。


## 2026-10-06 Phase 1 追加：完整 Source 系统、编号工具、真实验收（仍在进行）

有效目标是 Source Product 基础系统全量 COPY/PORT，迁移阶段不调用 Codegen Skill/CLI。现有正式结构为 common/system/framework/quartz/generator/admin 六模块，旧简化平台只在显式 TEST ONLY 条件注册。Factory Foundation、独立 Scope/权限/资源和 Mapper ownership 保留，未迁正式 MES Domain，未提交/推送/部署。

- Source Dept/Post/User/Role/Menu/Dict/Config/Notice/Message/Attachment、日志、Redis 在线、Excel、Quartz和监控已经接入前后端与真实测试库；整体状态 **MIGRATING**，未把源码或 build 当作 VERIFIED。
- Batch10 增补 Source 自动编码规则/组成/结果三表及旧完整页面。COPY 保留 Source 字段；PORT 预览不占号、明确 POST 生成、平台事务内规则/计数行锁、周期隔离和长度溢出拒绝，无 JVM 锁/ThreadLocal 可变计数。仅通用平台编号，不自动注册 Factory/MES 编号消费者。V6 已应用。
- 真实 MySQL/HTTP：组织用户首轮26/26；文件/Quartz/导出首轮29/32，3项 DTO 回写兼容失败已保留，修复后的回写与真实Excel导入追加11/11；自动编码17/17，包含24并发请求生成唯一连续号码、精确长度边界、预览不占号、无权限403。首次管理员空库初始化/重复拒绝2/2，独立18082进程登录/系统接口6/6，该临时进程已停止。详见 `../reviews/2026-10-06-phase1-verification.json`，这些不是完整浏览器验收。
- 浏览器已实际新增/编辑部门、岗位，新增字典类型与 PENDING 数据；参数新增已执行，仍继续核验。图片证据保留在自有 target/phase1，最终另提供可查看结果。后续用户/角色/菜单、密码、任务、文件、导入导出和监控仍要逐项闭环。
- 后端上一轮 verify-4：91 run / 0 failure / 0 error / 3 skip（88通过）；当前最新源码仍需最终 verify/smoke。package-37通过；UI Node22 test 18/18、build通过（ui-check-4）。实际 run JAR 为 phase1-runtime-10，18081/backend-17；UI15174/ui-runtime-4。18080/15173原服务未动。
- 修复 Source 编辑 VO 的只读元数据/数值排序、角色部分状态 DTO、服务端新建 ID、Source菜单目录按显式管理权限查看（不能用 magic userId）、角色菜单树权限、任务日志详情 path variable，以及原表 start_time/end_time 的映射。V7只加实测耗时、停用迁移生成而没有真实接口的按钮，保留数据与分配，不改已应用V1–V6。
- Source在线消息 PORT 为仅接收、带Authorization的SSE，提交后按持久化接收人刷新；不恢复匿名WebSocket客户端任意转发。Source浏览器/OS有界解析，审计投影保留requestId/actorId/factoryId/scope/耗时，未采集请求体。SSE交付/撤销仍待真实验收。
- Vue3已合并 Source图标/布局工具、隐藏菜单路由、缓存/参数、手动刷新权限、字典失败可重试、私有 FileUpload/ImageUpload 文件选择及预览。密码弹窗去掉原Source显示新密码的成功提示，长度约束与后端对齐；后续涉及新凭证的浏览器操作按工具规则交给用户填写。
- Redis平台键现在按可信 Platform JDBC 身份派生命名空间，也允许显式 YAML 的 MES_SYSTEM_CACHE_NAMESPACE；会话、Source缓存、验证码、密码锁同域。Factory业务缓存仍须显式FactoryId，不能复用平台命名空间代替厂范围。跨数据库证明正在执行。

实际失败与处置：Druid wall 拒绝自动编码列表原 WHERE 1=1，已改 Source XML 的 where 结构，未放宽SQL防火墙；新任务控制器变量名与Druid SDK getter编译失败已修复；首次初始化V7重复添加Source原有start_time失败，仅在本轮自有空库确认无DDL应用后清除失败历史记录并重新迁移，原失败日志保留。UI一次启动误用全局Node18失败后明确切Node22；CHOKIDAR环境值没有使Vite8刷新旧模块，改显式 MES_DEV_WATCH_POLLING=true 的 watch消费者并仅重启本轮UI。组件加载/API响应异步导致过早读取DOM/填入查询框，已改为等待可见目标并限定弹窗；不能将这些尝试当作通过。

剩余边界：完整浏览器矩阵、最新回归/打包 smoke、Source权限/会话撤销/锁、SSE、任务调度恢复、外部Minio实例、部署/生产。状态按实际证据逐项更新，不能提前宣告Phase1完成。

## 2026-10-06 当前迁移与验收状态

此表覆盖上方Phase0历史“Base NO/未关联”结论，不删除历史盘点。来源/差异见source-product-migration-manifest.json，详细命令见MES_BASE_REALIGNMENT_REPORT最新工作包。

| 功能 | Source → 当前Base | 策略/实际证据 | 当前边界 |
| --- | --- | --- | --- |
| Common/Framework/YAML/认证 | ktg-common/ktg-framework → mes-common/mes-framework/admin | SOURCE_PRODUCT_PORT；Java21/Jakarta/Redis/验证码/注册/密码锁；安全59、注册29、验证码21通过 | 浏览器新凭证提交待用户；未部署 |
| 系统组织/用户/角色/菜单/字典/参数 | Source实体/Mapper/XML/Controller/完整Vue → 对应system/API | Source字段与交互保留；CRUD26、权限/范围59、实际动态路由/字典/组织/岗位编辑 | 用户创建密码/重置/导入最终UI提交未完成；已有API证明分别列 |
| 公告/附件/消息/个人工作区 | SourceSysNotice/Attachment/Message与本人inbox/profile | notice24、Excel/API11、SSE34；真实私有PNG/本人消息已读/资料和合成头像 | 浏览器下载artifact未取得；MinIO实例未验证 |
| 自动编码 | SourceRule/Part/Result → Source页面与行锁编号 | API17+24并发；浏览器预览/生成/组成 | 平台工具；无MES编号消费者 |
| Quartz/JobLog | SourceSysJob/日志/cron/启停/手动 → 受控DBclaim | 最新26/26；暂停手动运行及实际日志浏览器通过 | RAMJobStore单节点；集群能力未扩展 |
| Server/Cache/Druid | Source监控页面 → 实际OSHI/Redis/Druid | 本机OSHI8卷、Redis INFO、真实SQL/Wall浏览器；有授权无凭证 | JDK应用卷fallback未诱导运行；非生产监控 |
| 系统工具/Swagger | Sourcetool/swagger/menu → Springdoc3.0.3+SwaggerUI5.33.1 | 21/21及外部SDK复核，实际浏览器OpenAPI3.1 | Generator保留后置；不算Codegen运行 |
| F-214/216/217/218 OpenAPI通用 | Sourcecommon/system/framework/OpenApiClientController/提交Vue/API → Base | SOURCE_PRODUCT_PORT；原client/API表字段；71+29+18真实API，7定向，浏览器列表/详情/两组目录 | 浏览器新Secret/赋权提交未自动执行；数据范围默认关闭 |
| F-215 Source路线范围 | Route授权/旧DataScopeGuard依赖ProRoute | 用户明确选择通用可选MES校验器开关，开启缺策略失败；未迁工艺 | 按未来领域实现范围；不迁Source路线表/业务依赖 |
| UserTask/微信/PDA/打印业务等 | 原业务单据/渠道/设备能力 | 候选/依赖边界保留；不自动扩展本轮 | 对应业务或设备授权后继续，不写成已迁/已验收 |

当前三个菜单模块和V1–V10完整DDL可查看，新的空隔离MySQL导入426字段比对5/5。整体MIGRATING：尚缺人接手的凭证UI动作/下载artifact及外部实例设备验证。没有新增正式MES Domain，没有使用Codegen，没有Agent提交/推送/部署。

## 2026-10-06 多工厂分支工作包

| 能力 | SOURCE_PRODUCT_PORT 与适配 | 证据与边界 |
| --- | --- | --- |
| 数据源/工厂管理 | Source表、AES-GCM、DDL、分页、Vue3；Base字符串FactoryId、revision/enterpriseId | dev/prod真实库迁移；管理/隔离/分页/DDL验证；授权浏览器验收待做 |
| 同步协调基础 | mes_master_sync / SyncCoordinator；factory_id + sync_key / run_token | 真实MySQL、双JVM抢占、跨厂独立、恢复/旧令牌拒绝通过；外部执行器未迁 |
| 生产监控 | Source productionmonitor DTO/Service/Mapper/XML/页面 | DEFERRED_ARCHIVED；已从运行产品撤出，源码与原测试备份，未来重新审查 |
| 人员分析 | Source personnelanalysis与记录钻取 | DEFERRED_ARCHIVED；已从运行产品撤出，源码与原测试备份，未来重新审查 |
| ERP/WMS/外部MES实现 | 当前范围排除 | 未引入客户连接/外部调用/具体同步流程 |

## 2026-10-12：业务阶段决策入口

本轮不全量迁旧Java/Vue。按Business Capability重新筛选，简单能力迁Schema后续Codegen，复杂能力只审查，
DROP须具体理由，GAP只记录。新的[业务迁移矩阵](../migration/MES_BUSINESS_MIGRATION_MATRIX.md)
是本阶段入口；这里保留早期候选/迁移历史，不把历史PORT策略当作当前业务实施结论。
