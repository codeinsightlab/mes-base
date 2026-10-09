# MES Base 下一阶段规划与新会话交接

## 2026-10-09 当前有效范围

先完善 Base：中心库 + 按 Factory 隔离的多业务库，系统/权限/资源/事务/监控/同步协调基础。无 ERP/HR/WMS 外部 JDBC 框架，不实现共享业务库。后续业务接入时集中改造生成器，贴合现有 UI、`rows/total` 与 Source 页面惯例；本轮不改模板、不提前创建统一业务壳。

架构评审的逐项复核、已整改和待审查清单见 [评审行动方案 §五](../reviews/2026-10-09-architecture-review-actions.md#五2026-10-09-复核与整改当前有效)。以下 2026-10-06 段落是历史证据与计划，三工厂、业务分析接入及外部占位等旧安排不再生效。

## 2026-10-06 当前范围：基础架构先行，业务分析后置

用户决定撤出生产监控和人员分析。两项能力的后端 `mes-mes`、前端页面/API 及专属测试已归档到 `migration-backups/2026-10-06-deferred-production-analytics/`，不再参与构建或 Mapper 扫描。V15 撤销菜单和角色菜单关联；V13 保留原样。此前关于继续迁入或默认关闭这两项能力的内容属于历史记录，由本节覆盖。

当前保留工厂/数据源、权限/资源隔离、系统监控、同步协调和三工厂持久化基础。隔离与事务验证继续使用独立 TEST ONLY 探针，不能依赖尚未接入的业务。后续恢复需重新审查业务 Schema、SQL、权限、Factory 范围及页面契约；归档不是验收通过。


## 2026-10-06 中心库兼容数据已导入dev

用户授权mes_master → mes_base_dev，保留两个源工厂并另配开发业务库。已完成兼容数据/ID/范围映射导入：用户1750、角色34、部门83、岗位4、字典类型50/数据178；当前admin密码/平台权限保留。新增工厂1/2均DISABLED且无数据源，原DEV保留。没有复制原连接、任务、日志、同步水位或旧超级管理员特判；源菜单不对应已实现范围的授权未复制。当前dev实际Flyway14，prod本轮未改。

真实数据库29项及Maven90通过/3显式fixture跳过、smoke4/4；没有HTTP/UI验收。导入备份、边界、冲突和恢复方式见迁移报告最新“中心库dev数据导入”章节与机器证据。下一步为两厂配置独立开发业务库，迁入真实业务Schema，显式分配当前可用分析权限，再做分厂SQL/页面验收。

## 2026-10-06 开始迁移后的实际状态（优先于下方历史规划）

SOURCE_PRODUCT_PORT 已接入数据库工厂/数据源管理和通用同步协调，dev/prod 测试库应用 V11–V13，原 V1–V10 checksum 保留。工厂不再由 YAML 创建；实际配置仍直接填入私有 profile。开发服务18081已使用新包，UI15174保留；外部实现未迁入。

生产监控、人员分析 Java/XML/Vue 已迁入，Source 12项服务单元测试通过。当前业务 Schema/状态字典来源已询问用户，尚待确认；两个入口默认关闭，没有推测建表或执行真实分析 SQL。下一步读取当前 Schema → 核对四个分析 Mapper及字典 → 迁移真实结构 → 显式分配管理/FACTORY权限 → 分厂真实SQL及页面验收。数据源管理的授权浏览器操作与窄屏验收也待完成。

本轮 Maven93项：91通过、2显式双厂 fixture 测试跳过；其中真实 MySQL 管理/同步20项，含双独立JVM抢占。UI27项和构建通过，打包smoke4/4，dev HTTP8项、prod测试库基础检查9项通过。证据与边界见迁移报告最新章节，整体迁移仍未完成，未提交/推送/部署。

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

## 2026-10-06 dev/prod配置与临时数据库收尾

本轮按用户要求转入日常开发环境：默认dev，实际值直接写入 `mes-admin/src/main/resources/application-dev.yml`；系统库mes_base_dev，独立业务库mes_base_dev_business，Factory=DEV，Codegen仅指向业务库。实际dev/prod连接直接写在资源profile，0600且Git忽略；初始admin凭证、phase备份在.local。无import层，config仅保留无密码example。prod测试库另建mes_base_prod/mes_base_prod_business、独立账号、Redis DB2/PROD厂；未部署真实生产。dev18081/UI15174，Redis16379 DB1；Redis二进制/配置/AOF/RDB/日志已迁到.local，用scripts/run-local-redis启动，不再依赖target运行文件。

原7个mes_base_phase1_*库已备份，已切换dev服务并删除；4个旧dev schema保留。V1–V10checksum保持，新空库已初始化并移除16张空归档；只含通用产品菜单/字典/参数与首个显式PLATFORM管理员，无工厂授权及业务测试数据。

已删除旧简化Platform实现/旧demo账号、PlatformHttpTest/PlatformServiceTest/TestOnlyCodegenFixture、V1-only迁移测试及2个旧验收脚本；当前Factory单/多厂、跨库/线程/权限/资源及Source系统测试保留。最后验证与实际运行结果见迁移报告“dev/prod配置与临时数据库收尾”与 `../reviews/2026-10-06-development-cleanup-verification.json`。此前phase验收记录保留作历史，下面旧runtime/库/账号描述不再作为启动入口；既有前端迁移/验收缺口不因开发库整理自动通过。


更新日期：2026-10-06。当前 Phase 1 继续执行；P0-A/B/C 和 Phase 0 结论保留为历史快照。

## 方向纠偏后的有效计划

用户已恢复执行，目标是 Source Product 基础系统全量 COPY/PORT，迁移阶段忽略 Codegen。六个 Maven 模块及 Source 系统/监控页面已接入；2026-10-06复核确认前端外壳、导航/标签、路由、会话和登录外观仍有既有Base自写实现，Source Layout/Navbar/TagsView/Settings未完整迁入，不能称整个前端沿用原架构迁移完成。当前整体 **MIGRATING**，继续真实数据库、权限和浏览器验收，不重扫整个系统、不进入正式 MES Domain。

最新实时基线：后端 main/4891535f735b18ca87918d8bcff1546663a53d02（用户期间新增提交）、前端 main/d2b19714cb5018d07fa3171b76585eab45b325ce；Agent未提交/推送/部署。用户启动类、profile/dev连接等调整保留；不能reset旧HEAD或裸启动未知Schema。

本轮后端 runtime23/18081/backend-34.log，UI15174，Redis16379；原18080/15173保留。数据库是用户远程测试MySQL8.0.40，独立mes_base_phase1_*合成库。当前菜单系统管理/系统监控/系统工具及OpenAPI页面可查看；admin105仍显式PLATFORM，无Factory准入绑定。

本次工作包：安全59、注册29、验证码21；公告24、Quartz26、Swagger21；OpenAPI71+双库29+最小权限/输入18、数据范围策略7；V1–V10导出426字段/新空库5。最新标准Maven verify-22总100/97通过/3跳过，UI ui-check-12为21+build，packaged smoke-6为11。实际API、浏览器、Fixture、外部/生产边界分别记录，旧包没有统一重复到最新runtime。

OpenAPI按用户新决定：Source客户端/Token/API授权PORT，业务数据范围YAML默认关闭，开启需唯一通用MES校验器；没有ProRoute依赖/工艺范围页面。迁移仍忽略Codegen。SourceUI有原有OpenApiClient修改，新PORT使用提交HEAD，Source不改。

当前规划顺序：已具备条件的基础设施/API工作包完成 → 补齐已确认的Source前端布局/导航/标签/设置迁移缺口，并完成相应交互验收 → 用户接手浏览器新凭证/重置/注册/导入提交，取得下载artifact后复核 → 汇总Phase1验收；MinIO/PDA/微信/集群/正式MES范围按实际授权/实例触发，不拿Fixture补生产证据。不要先开新MES Domain。前端来源复核见迁移报告“2026-10-06 前端迁移来源复核与表述纠正”，页面PORT不能替代完整外壳迁移。

tab6已恢复用户新增表单：qa_ui_user_20261006、部门200、岗位2、空角色/正常/空密码；只读查询尚不存在。此前用户答复“稍后操作”不等于已保存，不能自动填写或提交新密码。tab7保留OpenAPI页面作为可见迁移入口。

详细过程、失败、命令、服务处置和未验证项见[最新工作包报告](MES_BASE_REALIGNMENT_REPORT.md#2026-10-06-公告任务系统工具与-openapi-工作包验收)及[机器验收](../reviews/2026-10-06-phase1-verification.json)。本轮所有私有配置/身份在target，不能打印、清空或作为公司数据复制。

> 下方第1–8节是2026-10-05初始P0/Codegen历史规划，保留追溯；其中“本轮只写规划/不迁Source/Codegen优先”不适用于当前Phase1。当前执行以本节和用户最新指令为准。

## 1. 下一阶段目标

先把现有 Platform / Factory / Codegen / UI 底座接入隔离的真实开发数据库，完成可以重复执行的本地启动、运行时装配、授权和生成 CRUD 验收，再进入正式 MES 领域建设。

本轮只写规划和交接，不新增业务代码，不启动数据库或应用，不提交、推送或部署。新会话的执行范围以用户届时的指令为准；本文中的后续路线不自动授权全部实施。

推荐顺序：收尾复核 → 真实开发库与运行时装配 → Codegen真实数据库闭环 → 首个MES领域设计 → 按领域分批实现。此前Factory报告建议先Routing；当前应先补齐底座真实数据库闭环，再进入Routing。

## 2. 接手时的事实基线

| 项目 | 2026-10-05本次核对结果 |
| --- | --- |
| 后端目录 | `/Users/user/work/lixin/mes-base` |
| 前端目录 | `/Users/user/work/lixin/mes-base-ui` |
| 后端Git基线 | `main`，HEAD `975a9df4687f882bf251ca09bfb242015d33beff`；写本文前工作区干净 |
| 前端Git基线 | `main`，HEAD `18e372c2e6da0c6ab9738d562f99bf0d5bf3c6ca`；写本文前工作区干净，已是独立Git仓库 |
| Git边界 | 两端已有本地提交；本次没有创建仓库或提交。远端推送、部署状态未核对，不据本地HEAD推断 |
| 后端 | Java21 / Spring Boot4.1.1 / MyBatis；当前单Maven模块 |
| 前端 | Vue3 / TypeScript / Router4 / Pinia / Element Plus / Vite；`.nvmrc`为22 |
| 已有本地验收 | 后端80 tests、前端18 tests与build、生成HTTP21项、打包smoke11项通过；详见建设报告和verification |
| 证据性质 | 合成H2 + 隔离HTTP + 真实浏览器；不是生产库、真实PostgreSQL/MySQL或部署验收 |
| 已完成能力 | Factory Foundation、P0 Platform、Codegen Service/CLI、Vue3 UI、仓库Skill和AGENTS |
| 未开始能力 | 正式Routing、WorkOrder、Reporting、Quality、客户ERP及生产监控等MES领域 |

**历史状态修正：** [建设报告第10节](../reviews/2026-10-05-platform-codegen-foundation-report.md)记录的“后端13处修改/66个未跟踪文件、前端无Git”是上一轮检查快照。本次两端已提交且干净，不能在新会话继续将这两项当作未完成工作。本文写入后，后端出现文档差异是正常结果；新会话仍须重新检查实时Git状态，不机械相信快照。

临时文件、UI README路由说明和生产能力限制尚不能据Git干净认定已处理。是否有临时服务也须在新会话重新核对。

## 3. 最少阅读入口

按以下顺序读取，避免重新加载全部历史聊天、旧公司仓库和工程规则目录：

1. 本文；个人规则入口 `/Users/user/Documents/agents/AGENTS.md`，按本轮任务路由读取必要规范。
2. 后端 [AGENTS](../../AGENTS.md)；涉及前端时读 [UI AGENTS](../../../mes-base-ui/AGENTS.md)。
3. [建设与验收报告](../reviews/2026-10-05-platform-codegen-foundation-report.md)第7—10节及追加交接记录、[verification](../reviews/2026-10-05-platform-codegen-verification.json)。不要把READY解释为生产就绪。
4. [Platform指南](platform.md)、[Codegen指南](codegen.md)。
5. 涉及Factory/runtime时按需读 [架构索引](../architecture/README.md)及ADR-001—004；涉及平台授权、上游来源时读 [ADR-006](../architecture/ADR-006-platform-and-web-base.md)。
6. 新增表驱动CRUD必须先读 [mes-crud-codegen Skill](../../.agents/skills/mes-crud-codegen/SKILL.md)。即使当前会话没有自动发现Skill，也直接读取该路径执行。

旧公司后端 `/Users/user/work/heri/ktg-mes`及相关旧前端仅在用户明确要求复核某条调用链时只读参考；不能把当前工作目录仍在旧仓库误当作修改目标。

## 4. 必须保留的设计和环境约定

- 不推翻Factory Foundation，不恢复旧FactoryContext、动态库兜底或单厂/多厂两套代码。
- 不新增User ↔ Factory准入绑定。有效空角色用户可以登录、选择启用Factory和访问AUTHENTICATED；AUTHORIZED必须有当前厂权限。PLATFORM管理员不自动获得Factory业务权。
- FactoryScope只表达Factory身份；权限、Business datasource、ERP connection分别解析。无连接明确失败，不回退Platform库、首厂、首库或魔法0/1。
- Platform和Business持久化及事务边界独立，Mapper显式登记归属。Factory业务SQL仍携带范围身份，关联资源必须校验所属范围。
- DTO/VO/Persistence对象分离；Service做规则与转换。标准CRUD优先生成Mapper，复杂扩展使用ExtraMapper；领域先设计，绕过生成器记录`CODEGEN_BYPASS_REASON`。
- 后端成功是明确DTO/VO，失败是真实HTTP错误。新Base不恢复旧AjaxResult及前端多结构兜底。
- 不复制客户源码、Schema、seed、账号、厂、菜单、字典、Job、凭证或ERP地址。许可和官方来源保留在[来源清单](../legal/ruoyi-source-manifest.json)。
- 使用用户配置的Java/Maven环境，**不要重建项目`.m2/.mvn`或私有mirror**。此前实测为Corretto21.0.4 / Maven3.9.10；Maven安装目录名字不能作为实际版本依据。
- 前端使用已安装Node22，用户全局默认Node16不变。测试脚本需要Node和Maven时，在同一交互shell加载环境。
- 此前交互shell的`python3`出现`Bad CPU type`；本机验收用`/usr/bin/python3`。Skill结构校验依赖临时PyYAML，不能假定新会话该临时目录仍存在，也不能因此改系统Python。
- 不输出环境变量全集、密码、Token或完整连接凭证。开发凭证放受控环境/忽略的本地配置，文档仅列变量名和占位结构。

## 5. 工作包与验收门槛

### P0-A：收尾及基线确认

目标：确认当前提交与现有交付匹配，解决文档与临时资料遗留。

执行内容：

1. 两端分别检查`pwd`、`git status --short`、`git branch --show-current`和HEAD；保留后来新增的用户修改。不要重复初始化Git，也不要把已解决的历史脏工作区当作当前事实。
2. 检查当前是否存在本任务的Java/Vite进程。只管理可证明由本轮启动的服务，不按端口或旧PID杀用户进程。
3. 修正UI `README.md:17`的手工router注册说明：实际组件按授权菜单匹配编译catalog并注册/撤销，仍需管理员配置Menu/Role，不自动赋权。核对ADR-006“自动注册”的表达是否需要区分工具自动赋菜单与运行期菜单路由。
4. 复核`/private/tmp/mes-*.py`、`mes-codegen-upstream`、`mes-skill-validator`归属。上一轮发现34个辅助脚本，包含早期Factory阶段；先列清单和保留依据，再在用户授权清理范围内删除，不用通配符批量清理。
5. 保留可追溯证据。`target/`、UI`dist/`、`node_modules/`是忽略产物，不能当源码已提交证据，也不必为追求空目录全部删除。

通过标准：历史/当前差异区分清楚；路由文档与源码一致；临时进程与文件清单有处置记录；没有任务外代码改动。仅文档或临时资料变化不要求重跑完整功能矩阵。

### P0-B：真实开发数据库与可信运行时装配

目标：现有底座在**隔离真实数据库引擎 + 合成数据**上可启动、可持久化、可按厂路由；不读取生产或客户库。

先决条件：明确目标引擎及版本、开发实例来源、Platform库和Business库/Schema布局、生命周期及安全的连接配置。当前JAR带PostgreSQL驱动，因此建议优先PostgreSQL；这只是建议，用户未确认目标引擎。若选择MySQL，先核对驱动、Flyway模块、平台迁移和生成SQL方言，不能把JDBC可连当作CRUD兼容。

信息不足时，先做源码/配置差距分析和可审查的装配方案，集中询问缺失的开发环境条件；不要使用发现的公司数据库补缺，也不要擅自启动/停止用户已有服务或安装数据库。

关键源码入口：

- `server/FoundationConfiguration.java`：默认FactoryCatalog为空，偏好为内存，Business resolver映射为空，RuntimeRegistry为空。
- `server/PlatformConfiguration.java`：显式启用Platform DataSource/Flyway、身份/授权、Job和文件能力。
- `infrastructure/persistence/ConfiguredBusinessPersistenceResolver.java`、`BusinessRuntimeBinding.java`、`RuntimeRegistry.java`、`DedicatedDatabasePersistence.java`：既有受信任装配和独立库能力。
- `src/main/resources/db/platform/V1__platform.sql`：平台clean migration；`application.properties`：显式环境入口。

执行内容：

1. 给出最小开发装配方案，复用现有Port/Registry/Mapper ownership。可先使用受控开发配置适配器；**不默认新增Factory后台CRUD、数据源管理API或连接池管理平台**。若确需持久化FactoryCatalog/偏好，先证明当前用例需要并明确最小Schema及授权。
2. 准备隔离Platform库及A/B独立Business库。业务实例不能用同一身份伪装两厂独立库；Shared DB仍不在本工作包范围。
3. 在真实引擎执行平台迁移，验证首次/重复启动、Schema约束、首次管理员和重启后的平台数据。已应用迁移出现问题时使用后续增量迁移，不重写已应用V1来掩盖历史。
4. 通过受控装配建立启用Factory、DatasourceIdentity/version、RuntimeBinding、可信Mapper名单，验证解析失败、禁用Factory与缺Mapper不会回退其他库。
5. 明确偏好重启语义：当前内存偏好不能写成已持久化；Token只驻前端内存，刷新重新登录属于现有契约。
6. 验证身份/角色/菜单/字典/配置、私有文件和heartbeat Job在真实库运行。只验证当前注册handler，不虚构MES同步或ERP任务。

通过标准：可复现启动/停止说明；迁移及重启成功；Platform/Biz ownership成立；A/B运行时不会混用；401/403/缺Scope/错误绑定等负例通过；平台管理员无厂业务权；日志和配置无凭证泄漏。记录具体引擎/版本、实际命令和结果，标注“真实引擎上的合成开发数据”，仍不能称生产验收。

失败处置：数据库或环境不可用就记录未验证项，继续独立静态工作；不能回退H2后将本工作包标为通过。业务源码变化按影响补定向测试和完整必要门禁。

### P0-C：Codegen → Backend → UI 的真实数据库闭环

依赖：P0-B通过，目标SQL方言已确认。

执行内容：

1. 在隔离开发库创建明确TEST ONLY的简单单表。可沿用`gen_demo_config`语义，按真实metadata定义单PK、`factory_id`、状态与可选备注，避免FK/复杂领域引用；不复制客户表。
2. 先读Skill，运行稳定CLI `--dry-run`，核对metadata/16文件manifest，再一条命令完成两端落点。合成产物保持在隔离验收目录，不把TEST ONLY模块登记进正式生产路由/JAR。
3. 完成状态枚举、输入约束、DTO/VO、厂范围、唯一性/事务和UI后处理；业务Mapper经可信运行时名单登记，不为探针放开任意扫描。确需方言修正时限定模板/适配边界并加入目标引擎测试。
4. 管理员按明确授权配置Menu/Role，验证编译组件动态路由注册/撤销；生成器不自动授予权限。
5. 运行真实生成Controller和SQL的列表/分页/详情/创建/更新/物理删除；验证A/B同ID或冲突业务键、缺厂、无角色、平台管理员不带厂权、非法状态及权限撤销。
6. 真实浏览器覆盖登录、选厂、生成页CRUD、取消删除、查询重置、decimal/bigint精度、loading/empty/error、厂切换清理旧数据、过期响应与窄屏。沿用Base UI，不只确认页面能打开。
7. 针对冲突、重复生成、显式overwrite、失败回滚和临时清理执行适用验收；不要用“生成成功”代替产物/SQL/UI成功。保留已记录的跨目录进程崩溃非原子性边界。

通过标准：一条CLI落点及manifest可复核；真实目标引擎上CRUD和跨厂负例通过；前后端测试/build通过；浏览器截图及实际操作有记录；生成探针不污染生产包；本轮服务结束后停止，忽略验收目录及证据有明确归属。

### P1-A：首个正式MES领域设计

依赖：P0-B/C通过，且用户明确选择该领域及范围。推荐先Routing，再WorkOrder，再Reporting；这不是预先承诺批量迁移。

设计前须确认：

- 主数据权威来源、最小用户动作、厂归属、状态/版本、唯一键与引用语义。
- 输入DTO、输出VO、Permission、资源ID范围、事务与并发、外部副作用/UNKNOWN边界。
- 查询/详情/统计/导出/关联数据的范围规则；当前不实现的能力单独写明。
- 真实表结构或明确设计的新Schema；使用生成器的范围及`CODEGEN_BYPASS_REASON`。

Routing重点先确认工艺/路线/步骤/版本与发布后修改语义；不能把它直接拆成几张无约束CRUD。先形成可审查的领域设计及验收用例，再按该新会话的授权进入实现，不替用户猜业务规则。

此前多工厂分支中的生产监控等非多厂改动，保留在未来迁移候选范围内；需要进入对应工作包时读取专项审查文档并重新核对调用链。本轮不因底座READY就整体搬入，不能因“非多厂”而自动丢弃。

通过标准：领域边界和接口/状态/数据来源能审核，实施范围明确；实际实现必须完成后端规则/SQL、前端动作和负例闭环。`MES_DOMAIN_READY`仍为NO，直到选定正式领域的实际验收通过并说明具体范围。

### P1-B：随真实部署/长期任务需求触发的专项

这些是上线门槛或后续专项，不默认与P0-B/C捆绑全部实现：

| 触发条件 | 必须解决的事项 |
| --- | --- |
| 长期业务同步/多节点调度 | DB claim/run token之上补lease/heartbeat、确认死亡后的恢复、重复fire幂等、失败UNKNOWN与运维处置；不能只依赖RAM Quartz或定时超时解锁 |
| 对外开放登录/正式部署 | TLS、明确认证方式、登录限流、凭证管理、日志/会话留存与清理、数据库备份/迁移回滚、私有文件持久卷或存储适配 |
| Shared DB真实需求 | 按ADR-004设计实现和全部行范围验收，不修改独立库身份来伪装共享实现 |
| 业务文件引用 | 增加对应FileResourcePolicy证明owner/factory/resource，不放宽personal限制或公开裸URL |
| 大规模组织/实测性能问题 | 当前100条选择器边界与前端大chunk按实际数据/测量设计，避免无依据的全局优化 |
| ERP/设备真实对接 | 独立Factory连接、外部契约、幂等/结果核对、UNKNOWN与补偿；HTTP成功不等于业务成功 |

## 6. 可复用的环境与验收命令

新会话先确认实际环境，再选用下面命令。不要打印全部环境变量或自行重建用户Maven配置。

```sh
# 分别在两端执行；不要在旧ktg-mes里跑Base构建
cd /Users/user/work/lixin/mes-base
pwd
git status --short
git branch --show-current
zsh -ic 'java -version && mvn -version'

# 后端必要完整门禁：使用用户交互shell配置
zsh -ic 'mvn -B -ntp verify'
zsh -ic '/usr/bin/python3 scripts/smoke-packaged.py'

# 前端；依赖已可用时不必重复npm ci
cd /Users/user/work/lixin/mes-base-ui
pwd
git status --short
git branch --show-current
zsh -ic 'nvm use 22 >/dev/null && node -v'
zsh -ic 'nvm use 22 >/dev/null && npm run test && npm run build'

# 2026-10-06：旧Platform fixture脚本退役，当前生成/隔离回归包含在Maven verify中
```

2026-10-06：旧verify-codegen-fixture/http及TestOnlyCodegenFixture已退役，它们依赖已删除Platform链路及旧模块/UI；历史证据保留，不能再按旧命令运行。当前CodegenAcceptanceTest及Factory隔离测试继续由标准Maven验证。

2026-10-06配置已改为标准资源profile内的实际YAML值，不再使用上述MES_*连接占位符。首次管理员仍用scripts/mes-platform-admin与临时MES_BOOTSTRAP_PASSWORD；Codegen读当前profile的mes.codegen.profiles，开发metadata不等于Factory runtime。具体约束和参数见两个指南，不在计划内写真实值。

## 7. 每个工作包结束时留下的交付

1. 追加同主题事实报告和verification：实际引擎/环境、输入版本、命令、passed/failed、负例、截图/产物、未验证项和临时服务处置。
2. 修改用法时同步Platform/Codegen指南及README；改变架构决定时按需新增ADR，不用历史报告覆盖现行规则。
3. 在本文追加工作包状态、具体文件链接和下一步，保留此前快照。代码完成、测试通过、本地提交、推送、部署分别记录。
4. 保留两端Git差异清单和可审查结果；提交/推送/部署按用户届时授权，不自动处理任务外修改。

结束条件按工作包逐项判定，不能以“无报错”“build通过”或“Git干净”统一宣称全部能力完成。

## 8. 新会话可直接使用的启动指令

下面文字可直接粘贴到新会话：

> 继续MES Base建设。先读取 `/Users/user/work/lixin/mes-base/docs/development/next-stage-plan.md`，按个人和项目AGENTS加载必要规范，确认后端 `/Users/user/work/lixin/mes-base` 与前端 `/Users/user/work/lixin/mes-base-ui` 的实时Git基线。以本文交接事实为起点，先执行P0-A中可在现有授权内完成的收尾核对和文档修正，再推进P0-B/C的隔离真实开发数据库与Codegen闭环。目标数据库/实例/启动权限不明确时先完成差距分析，集中询问缺失信息，不读公司或生产库。保留Factory Foundation、无UserFactory准入、显式Permission/Resource Scope，使用用户配置的Java/Maven与已安装Node22，不创建项目.m2/.mvn。不新增正式MES模块，不推送或部署；未经授权不删除旧临时资料。详细结果写入项目文档，每个工作包更新计划和验收证据，结束给简洁回执。

## 9. 进度记录

| 日期 | 工作包 | 状态 | 证据/下一步 |
| --- | --- | --- | --- |
| 2026-10-05 | 交接与规划 | 已完成文档；未执行下一阶段 | 两端Git基线实时核对；本计划、README及建设报告交接入口已写入 |
| 2026-10-05 | P0-A | 待新会话复核 | Git遗留已解决；临时资料、路由说明和运行现场仍需按实时状态复核 |
| 2026-10-05 | P0-B / P0-C | 未开始 | 目标真实开发引擎/实例及隔离配置待明确 |
| 2026-10-05 | P1-A / P1-B | 未开始，按需求触发 | 先底座闭环，再由用户明确正式领域/部署范围 |


### 2026-10-05 执行追加：P0-A 完成，P0-B 差距分析完成

- P0-A：PASS（临时资料全部保留）；两端 HEAD 与基线一致。后端既有3个文档修改和未跟踪计划受保护，前端初始干净。UI README / ADR-006 路由行为已校准，动态路由定向测试1项通过；未修改业务代码。
- P0-B：环境待确认，未通过。已核对默认空装配及现有 resolver/registry/owner/事务链，形成最小可信开发配置方案；待用户明确真实开发引擎/实例及本轮自有服务生命周期后实施并执行真实验收。
- P0-C：NOT_RUN，依赖P0-B；生成前必须执行 mes-crud-codegen Skill，TEST ONLY产物留隔离目录。
- 详细过程、命令、临时清单和未验证边界见[报告第12节](../reviews/2026-10-05-platform-codegen-foundation-report.md#12-p0-a-收尾与-p0-b-独立差距分析2026-10-05)及[verification.nextStageExecution](../reviews/2026-10-05-platform-codegen-verification.json)。本文前部“尚未执行”属于交接时快照，以本追加为当前进度。
- 本轮未启动/停止服务、删除旧临时资料、提交、推送或部署。下一动作：明确开发环境后继续P0-B；不重复全系统扫描。


### 2026-10-05 用户调整目标：先补齐RuoYi三个模块

当前执行目标增加：系统管理、系统监控、系统工具前后端功能完整迁移，按实际模型形成DDL并在隔离本地开发库run起来。此前通用能力DROP不再作为排除依据；正式MES领域仍不开始。完整迁移矩阵与数据库边界见[报告第13节](../reviews/2026-10-05-platform-codegen-foundation-report.md)。P0-B真实数据库仍需确定，新增表驱动CRUD依真实metadata执行Skill；不把现有CLI或通用管理页视为完整RuoYi迁移。下一步：用户指定开发引擎/实例后持续补齐功能、增量迁移及真实本地验收。


### 2026-10-05 执行追加：真实MySQL、YAML dev/prod、本地启动

用户已明确指定其测试实例，MySQL8.0.40；本轮独立Platform/A/B库、专用开发账号与平台迁移已完成，未读取实例其他业务库。环境与Factory映射改为YAML/dev/prod，通用Java装配仅保留安全/生命周期职责，不在class中定义环境；主入口已由application.properties迁为application.yml。

P0-B核心验收通过：83项后端（其中3项真实MySQL）、18项UI/build、11项packaged smoke、47项真实MySQL HTTP、重启3项、profile/JAR检查3项。P0-C仅metadata/16产物与MySQLquote通过，真实生成CRUD/UI闭环尚未通过。RuoYi三个模块缺项仍见第13节，不因启动成功标为完整迁移。

本地backend18080 / UI15173保持运行供查看；本轮服务管理与精确pid、受控配置、测试命令、失败修正、并行用户修改保护及边界见[报告第14节](../reviews/2026-10-05-platform-codegen-foundation-report.md)。没有提交/推送/部署，旧临时资料保留。此前“目标实例待确认”由本追加取代。下一包按已确认MySQL与YAML方式补齐三模块并继续P0-C，不再重复询问目标数据库。


### 2026-10-05 用户暂停：本轮迁移未符合预期

状态 PAUSED（用户明确指令）；完整RuoYi迁移未验收。原项目已明确为 `/Users/user/work/heri/ktg-mes`，预期是完整RuoYi基础代码、sys字段和有效配置迁入Base，三个模块前后端齐全；当前简化平台实现不满足终态。结构基线18表182字段在新隔离测试库核对通过，Druid后端装配83项回归通过；应用尚未切换官方schema、监控/UI及其余配置未完整迁移。详细文件、失败及测试边界见报告第16节和verification.originalProjectPause。保留全部差异及现有查看服务，不提交/推送/部署/清理。新任务先核对实时两端Git和服务状态，不能把本包数据库结构准备当作完整迁移。


### 2026-10-05 Phase 0纠偏与新的代码授权

用户明确：Source Product为ktg-mes三端；当前Base分包可推翻替换，优先沿用老RuoYi架构；先基础设施和系统设置，再业务。业务只作模块级清单，不再深审/设计Domain。已授权本轮三份文档后进入基础设施代码迁移，但迁移必须COPY/PORT源代码，**忽略Codegen Skill**。此前P0-C/Codegen First是开发流程，不再作为迁移前置。

入口：[MES_FEATURE_INVENTORY.md](MES_FEATURE_INVENTORY.md)、[MES_MIGRATION_MATRIX.md](MES_MIGRATION_MATRIX.md)、[MES_BASE_REALIGNMENT_REPORT.md](MES_BASE_REALIGNMENT_REPORT.md)；证据为../reviews/2026-10-05-mes-realignment-evidence.json。真实判断：当前是MES开发框架+部分平台重写/Factory Foundation，未继承完整MES产品。两端AGENTS与ADR-006已纠正；没有执行新CRUD生成或大规模搬包/删除。旧Web有用户openApiClient修改，PDA有未跟踪文件，全部保护。下个代码包从原admin/common/framework/system/quartz依赖和调用链迁入，generator作为开发工具后置，不用新脚手架替代原系统功能。


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

### 2026-10-06 本轮工作包结束与下一步

公告/任务/系统工具/SQL包完成，OpenAPI通用链路已PORT并完成真实API/双库/最小权限/策略开关验收；全Phase1仍MIGRATING。六模块/三菜单/426字段DDL和Source页面有可查看入口；SourceRoute/UserTask不伪装为通用已完成能力。

续接先看本页有效计划和最新报告/JSON，核对两端实时HEAD、私有配置存在与服务归属。无需重跑所有旧历史包；当前代码/环境改变后按影响补验证。先检查qa_ui_user_20261006是否已由用户保存，再核对关联部门/岗位/空角色与实际登录；密码不从聊天收集。下载artifact、人接手凭证UI动作/外部实例未验收不得标完成。只管理本轮服务，不提交/推送/部署，不删除旧临时证据。

### 2026-10-06 UI 第一轮续接（Phase 1 仍 MIGRATING）

mes-base-ui 已建立 Modern Industrial 设计语言：tokens、Shell/Login/Home、当前 System/Monitor/Quartz/Tool/OpenAPI 共用布局和状态基础。后续页面遵守相邻 `mes-base-ui/docs/UI_DESIGN_SYSTEM.md` 与前端 AGENTS，复用 PageHeader/StatusTag/RightToolbar/Pagination/CrudPage、filter-panel/table-toolbar/panel；不自行创造主题/状态/间距/卡片或生产数据。Source 字段和流程继续保留。

本轮27个 UI测试及 vue-tsc/Vite build 通过。旧 phase 环境下1440主要页面与部门新增/编辑/停用/删除取消、查询/分页、OpenAPI 更多/授权目录取消已实际执行；Login1920/1280/窄屏及无效登录错误已验证。旧合成数据已随独立环境清理工作退役，不能按旧账号/记录继续验收。

当前 UI_BACKEND_BLOCKER：15174/health UP，但18081登录500，日志缺 MyBatis ExceptionUtil 类；已请求仅重启当前实例的用户确认。当前 JAR/配置已就绪，恢复后使用当前干净开发环境重新验证后台1920/1280、侧栏折叠、Tags刷新/关闭、列显隐/树折叠及末轮修正截图，并更新 `mes-base-ui/docs/acceptance/ui-round1/README.md`。UI轮不自行修改后端源码/数据库/配置，不通过关闭安全检查恢复登录；不把旧证据或视觉完成当作 Phase 1 完成。
