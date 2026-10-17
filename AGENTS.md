# MES Base 工程规则

## 2026-10-17 — 配置唯一权威与废弃配置管理

- 当前 Factory 业务运行模式只能来自受管 FactoryIntegrationSnapshot / Context / Registry；禁止新增或恢复按 FactoryId/FactoryCode 在 YAML 中覆盖 LOCAL/ERP 的机制。正式业务缺少快照、模式非法或 ERP 连接不可解析时失败关闭，不回退 LOCAL；测试必须显式构造合法快照，不依赖生产兼容兜底。
- WorkOrder/Warehouse.source 是可信持久化业务来源，不得由当前模式推断、回填或改写。新业务继续校验模式与来源一致性；历史 UUID 重放、UNKNOWN 核实及合法撤销按原持久化来源、provider/binding 和版本执行，不改派当前 ERP。
- Factory 决定当前模式与绑定哪个 ERP；对应连接配置决定如何访问该 ERP。E10 参数由工厂中心配置维护，SDK 的 host/service 身份、生命周期及版本校验不能因清理而丢失；禁止另建竞争模式入口或静默兜底。
- 删除配置必须同时检查声明、动态键拼装、绑定器/Bean 条件、脚本/Profile、历史兼容、测试与运行证据，连同失效分支和测试更新；未证明废弃的配置标记 REVIEW 并保留。业务概念只能有一个权威来源，不能长期保留相互竞争的配置。
- 已发布迁移不重写。V34 对旧仓储 YAML 的一次性历史导入契约保留，仅供尚未执行该迁移的旧环境升级；它不再是正常运行模式入口。历史工单用料 YAML 及其停用开关在证明不再需要前保留，数据库用料行存在时不得回退 YAML。

## 2026-10-17 — Digiwin E10 集成范围与人工补偿策略

- Base MES 只验收实际调用的 OAPI 操作、Request/Response DTO、实际 JSON、公共 Header/认证/HTTP/序列化/错误处理与实际业务状态；不以 SDK 全量契约完整性作为本项目发布门槛。V1.9 P2-1 的 8 项操作、10 个属性不直接涉及当前 MO/CMS/IM 候选，不扩大范围修复；实际使用接口自身的缺陷仍须解决。
- V1 不要求 ERP 原生支持 MES operationKey 幂等或按 MES UUID 唯一检索，不建设跨系统自动补偿。缺少远端 UUID 回溯是用户接受的人工恢复边界，不再单独列为阻断所有 E10 开发的 P1。
- 结果不确定时保留原 requestId、operationKey、冻结请求、已取得的 ERP 单据号及响应摘要，标记 UNKNOWN；禁止自动重建、自动转 LOCAL 或改派。授权人员须人工查询 ERP，记录原因、依据、操作者和结果；未经可信核实不得宣称生效，不得修改不可变事实。明确未发送的请求可按原状态安全重新发起；可能已发送但无响应的创建不得自动重试。
- 每厂最多一个 ERP，复用 FactoryIntegration、WarehouseIntegration 与现有 SDK；LOCAL/EXTERNAL 严格隔离，保持一套生产核心与两个数据源入口。工单、仓库 Query/Read 先确认部署下的稳定身份、产品/单位/路线及状态映射，再经既有可信同步服务落库；缺证据只允许候选预览，不猜 ID、状态或映射。
- 本轮范围只含 MO/CMS Query/Read 和同步准备，不开发 ERP 领料、出库、成品入库、工单作废或自动补偿。真实读取需要目标、账套/组织/环境及调用范围的明确授权；已有配置及 Mock 成功不构成真实 ERP 读取授权，不自动切换 SIMPLE_B 模式或修改绑定。


## 2026-10-17 — LOCAL / ERP 来源隔离与迁移规则（用户确认）

本节对 LOCAL/ERP 来源及迁移的约束优先于旧文档中的交叉混合单据、自由组合库存管理权和无缝迁移建议；不放宽 Factory 分库、权限、事务、引用保护及不可变事实规则。完整业务方案与实施缺口见 [LOCAL / ERP 业务边界设计方案 V1.0](docs/architecture/local-erp-business-boundary.md)。以下规则持续生效；V1.7 实施状态、真实 SIMPLE_B 验证及历史待核实边界见 [V1.7 实施报告](docs/reviews/2026-10-17-local-external-source-isolation-v17-report.md)。

- 每个 Factory 最多一个有效 ERP，必须复用 FactoryIntegrationSnapshot / Context / Registry；新厂明确持久化 LOCAL。ERP 配置缺失、异常或不可解析必须失败，禁止降级 LOCAL、并开两套新业务或新增多 ERP 优先级/自动路由。FactoryScope 仅含 FactoryId，禁止另建全局模式 Resolver 或字典模式体系。
- WorkOrder、Warehouse 必须持久化可信来源：LOCAL 由 MES 本地创建，EXTERNAL 由当前厂绑定 ERP 同步创建；来源创建后禁止普通编辑或同步改写。同编码不等于同一身份，禁止自动合并。业务来源属于 MES 业务身份；供应商单据标识、映射及配置版本沿用独立 Extra，不在业务表增加 factory_id。
- 新正式生产领料、退料、交接对应仓储接收、成品入库及关联仓储交易，仅允许“工单来源＝原材料仓来源＝成品仓来源”，且当前运行模式允许该体系：LOCAL/LOCAL/LOCAL 或 EXTERNAL/EXTERNAL/EXTERNAL。缺来源、非法来源、模式不允许或必要映射缺失必须拒绝。WarehouseIntegration 与业务 Service 必须校验当前模式、相关对象来源、原操作来源及必要映射/连接；禁止以接口不可用或同步改来源绕过准入。
- LOCAL 仓库由 LocalStockService 管理权威库存；EXTERNAL 仓库由 ERP 管理权威库存及交易，本地镜像仅供参考、检索、展示和对账。ERP 同步仅允许管理 EXTERNAL 权威字段，禁止 UPDATE/DELETE LOCAL、改写 LOCAL 余额/历史动作，禁止覆盖已锁定 Execution、RoutingPath、Report、Quality、Receipt 和库存事实。计划与事实冲突必须保留事实并明确报冲突。Execution / Report / Quality / ProductionQuantityService 只保留一套。
- 正式 ERP 业务必须依据当前权威查询或真实命令的权威校验；出库单创建成功不等于已实际出库或可信投产，必须满足经验证的生效契约。UNKNOWN 必须保留原操作身份/幂等标识及 provider/binding 查询恢复，禁止重建或改派。复用 WarehouseIntegration、ProductionErpAdapter、WarehouseErpAdapter，禁止把 ERP HTTP 纳入 LOCAL 数据库原子事务承诺。
- LOCAL→ERP 仅采用停旧启新：停止新 LOCAL，检查未完成生产/仓储及未知外部操作，按正式权限/状态入口受控作废或终止，保留不可变历史；人工盘点实物/在制品/余料/成品，完成 ERP 期初和工单准备，同步新 EXTERNAL，验证 ERP 接口与准入，经明确授权确认切换时间后启用 ERP 新业务。补录必须区分历史加工与新增加工，禁止重复加工事实或重复扣库存。禁止自动迁移执行、领料、报工、在制品、双账平衡或跨系统补偿。
- 缺正式迁移流程时必须保留现有模式锁定，禁止普通工厂编辑切换有业务历史的模式，解绑不能解锁。将来受权迁移必须检查未决状态、盘点/期初确认、切换时间及历史配置版本；历史查询/撤销按原冻结来源与 provider/binding 执行，禁止 LOCAL↔ERP 改派。禁止为迁移删除事实及被事实引用的主数据；无事实、无依赖的普通主数据仅按既有删除/归档规则处理。
- 新增或修改工单、仓库、同步、领料、入库、撤销、模式切换时必须逐项验证本节，覆盖来源矩阵、可信创建/不可编辑、同步不碰 LOCAL、模式异常拒绝、原身份 UNKNOWN 恢复与历史撤销；文档、静态检查及旧 LOCAL 闭环验收均不能冒充来源隔离或模式迁移已实现。

## 2026-10-14 用户确认：新增 SQL 的执行与验证

任务中新增的 SQL，Agent 应负责在核实后的任务目标数据库执行并验证，不再将执行留给用户，也不为执行该任务 SQL 再次等待用户同意。执行前仍须核对真实数据源与 Factory 绑定、检查存量和冲突、备份受影响范围，保护用户已有数据；Flyway 变更通过项目正式迁移机制执行，不手工执行后绕过迁移记录。执行后核对 Schema、受影响数据及相关业务读取，明确 SQL 已保存、已执行和验证结果。目标无法确定或存在无法安全处理的冲突时说明具体阻碍，不擅自换库。明确只读的任务继续禁止新增或执行写入 SQL。需要重启用户服务时由用户重启，Agent 不自行启动、停止或重启服务。

## 2026-10-14 用户确认：主子单据统一交互与组件复用

领料、采购、销售、入库、出库等主子单据优先使用统一单据页面：主单信息在上方，绑定当前主单的明细在下方；不将主表和子表各生成一个独立简单 CRUD 页面作为正式业务入口。复用 mes-base-ui 的 `src/components/document/MasterDetailDocument.vue` 和 `DocumentFields.vue`，明细表复用支持 embedded/fixedValues 的 CrudPage；领域字段、选择器和权限由业务页面显式传入。新建主单保存后才可新增明细，父单标识由当前单据固定注入，用户不手填或转移明细所属主单。

**前端约束：** 未来遇到主表带明细的单据结构，不沿用生成的单表前端页面或拆成两个独立 CRUD 页面，优先复用本轮 MasterDetailDocument / DocumentFields；业务页面显式提供字段、API 适配、选择器和权限。主子结构不是停止后端生成的理由。

**后端约束：** 新增主子单据仍必须按第13.0/14.1节，对主表、明细表分别通过 Codegen Skill 从真实 metadata 生成完整基础后端（persistence + backend，通常同时生成 test）：Entity、Example、Mapper/XML及基础Service/Controller等；不能因为前端使用主子组件就跳过生成或手写第二套基础CRUD/Mapper。生成配置应选后端产物，不生成或沿用独立单表前端页面。主子关系、引用规则、状态、删除保护及跨表事务由业务Service实施，需限制或替换会绕过这些规则的基础CRUD入口；既有迁移功能仍遵守第13节迁移例外，不为本规则重写已验证业务。

**组件与后端复用边界：** MasterDetailDocument 通过现有 CrudApi 契约的 headerApi/itemApi，复用项目API适配层和request，调用已有主单/明细后端CRUD及生成Mapper/XML/Example；当前领料仍经原业务Service执行校验、事务及父单删除保护。本轮没有新增一套通用主子后端服务或替代数据访问实现。先设计主子用例、事务和状态规则，再复用前端组件；需要整单原子保存时由业务Service提供聚合命令和事务，前端不得把多次单表请求宣称为整单原子提交。当前领料明确主单/明细分别保存，不预建ERP或通用流程引擎。保持权限、字符串ID/decimal、分页及切厂/撤权/迟到响应清理；主子组件不定义特定业务字段或后端授权策略。

## 2026-10-13 用户确认：生产操作人员身份

Production Operator = sys_user。当前 Base 不维护独立 Personnel 主数据，不建立 User → Personnel → Factory 链路。
后续 ProductionExecution / Reporting / WorkOrderAssignment 直接引用 operator_user_id、reporter_user_id、created_by_user_id 等明确系统用户标识；Factory 授权继续使用既有 User → Role → Factory → Permission 规则。
需要历史姓名稳定时，业务事实可保存 operator_name_snapshot / reporter_name_snapshot；快照不是可维护的人员主数据。
仅在无账号现场人员、HR与账号非1:1、外协/临时人员、独立技能资质、自然人与账号生命周期分离或多账号映射等真实需求被证明后重新评估，不预留人员壳子。
既有 /api/business/directory/personnel 与 PersonnelSelect 是直接读取 sys_user 的兼容只读目录，ID就是 user_id，不代表已撤回的独立业务表。

状态 Active；2026-10-09。执行非简单工程任务先读 `/Users/user/Documents/agents/AGENTS.md`
并按实际栈路由，读取目标目录更具体规则。用户当前授权优先，保护已有工作。

## 1. 项目目标

以 ktg-mes / ktg-mes-ui / heri-uni-app 为 Source Product，抽取可复用产品能力，去公司化、去客户定制并修正必要架构问题。优先沿用原
RuoYi 模块与 Controller → Service → Mapper；现有 Base 分包可以调整或替换，保留已验证的
Factory/权限/资源/数据库隔离行为。先迁基础设施和系统设置，再迁业务；不为迁移重新创造 MES Domain。

## 2. 单代码 / 单架构

一套 Core；单厂等于 Factory count=1。不复制服务、不分 Single/Multi 两套主链路，不为简单 CRUD 增层。Core 不依赖
Spring/ThreadLocal/具体基础设施，也不依赖 SysUser/SysRole 持久化 Entity。

## 3. FactoryScope

Scope 只含 FactoryId；不等于 Permission、DataSource 或 ERP Connection。无 UserFactory 准入：有效空角色账号可登录、选择启用
Factory 和访问 AUTHENTICATED；AUTHORIZED 缺权限拒绝。偏好只是
UX，不授予权限。显式选厂优先，其次恢复有效偏好；无有效偏好时，仅从启用工厂且绑定启用业务库的候选中自动选择：一个选它，多个选工厂
ID 最小者。纯数字 ID 按数值比较，其他 ID 按字符串排序，数字 ID 优先；同数值以原 ID 排序作稳定裁决。零候选保持空 Scope，不使用魔法
0/1 或回退中心库。PLATFORM 角色显式 kind、无
factoryId；FACTORY
角色必须所属厂。平台角色可持有任意权限，其业务权限在选定的启用工厂上下文中生效；工厂角色仅可持有业务权限，且仅在所属工厂生效。工厂业务授权合并平台角色业务权限与当前工厂角色业务权限，不向工厂角色开放平台授权。权限按每次请求当前厂读取，不进长寿命
Token/跨厂缓存。

### 初始化空工厂（2026-10-11 用户确认）

零可用工厂不阻止有效账号登录或进入已授权的平台管理。
`/api/foundation/factories`、`/api/foundation/context`、`/api/foundation/preference`
属于工厂发现/选择入口，保持 AUTHENTICATED 和选厂校验，不要求业务数据库绑定；
空列表返回 `[]`，空上下文返回 `factoryId=null`。其他 AUTHENTICATED 请求允许空 Scope，
实际业务授权和数据库访问仍要求有效 Scope/绑定，不以默认工厂或中心库兜底。

2026-10-12 用户补充：工厂候选列表只返回启用且绑定启用数据源的工厂；平台工厂管理列表仍展示全部管理对象。
工厂上下文缺失、停用、删除或业务绑定不可用时，业务范围 HTTP 响应统一使用
`422 / FACTORY_SELECTION_REQUIRED`；内部异常及日志保留原原因。前端只按此专用码进入选厂页，
保留登录身份与平台权限，清空失效选厂和业务状态，不自动换厂或重放失败请求。
选厂页复用工厂发现接口；零候选可进入平台/个人工作区。普通业务 422、403 和基础设施 503 不触发此流程。

## 4. Controller / Service / Mapper


Controller 收参、基础校验、HTTP View 转换和协议返回；不做业务。Service 编排、规则、状态转换、事务和外部协调；Mapper
数据访问，不做授权决策。默认单
Service，只有长期真实复杂协调才拆分。外部主动调用经领域 Port/Service；传输 Gateway/Adapter 不编排业务、不访问平台/业务 Mapper。ERP 领域实现可以编排供应商内部业务步骤，遵守下方 ERP Integration Architecture。

## 5. Domain / DTO / VO / Persistence

响应契约按消费者与风险分级：普通内部后台 CRUD 可直接返回经审查安全的 Entity；敏感/复杂接口及外部消费者必须隔离响应模型。Service
返回 Entity/Domain/Business DTO/Query Model，业务语义与状态由 Service 定义；需要 Entity/Domain→HTTP Result/VO 时在
Controller 的 Web 边界转换，通用 Service 不依赖 Web Result。禁止业务匿名 Map /
条件散参，允许索引集合和框架元数据 Map。字段来源、赋值、使用清楚；不能用字段兜底掩盖契约。可选字段不因文档或假想业务变必填。

### 5.1 Java 导入与类型引用（2026-10-06，用户要求）

所有 Java 源码（含生产代码、测试及生成模板）中的外部类型必须通过文件顶部显式 `import`
导入，在类体、方法签名、泛型、注解、类字面量及静态调用中使用简单类名。禁止用 `xx.xx.xx.ClassName`
全限定类名代替正常导入，也不得以通配符导入规避显式导入。唯一例外是当前文件实际存在同名类型冲突：保留一个简单类名，仅对发生冲突的类型使用全限定名。包声明、import
声明以及配置/反射/AOP 所需的类名字符串不属于类引用。新增或修改 Java 文件时检查此规则；不因本规则覆盖无关的已有改动。

## 6. Base 边界（2026-10-09 用户要求）

当前 Base 只提供中心库 + 按 Factory 隔离的多业务库。不装配 ERP、HR、WMS
或其他外部数据库，不保留外部源枚举、选源注解、连接模板或第三个会话工厂。未来真实集成需求另立有边界任务，不能预建占位框架。OpenAPI
外部消费者身份与私有存储属于已有产品能力，不等于外部数据库路由。

## 7. 权限 / Resource Scope

HTTP 每个方法显式 PUBLIC/AUTHENTICATED/AUTHORIZED；缺注解启动失败。标准 CRUD 默认
AUTHORIZED，平台操作只认平台角色；业务操作在当前工厂上下文使用平台角色业务权限与所属工厂角色业务权限的并集。后端是最终边界，前端隐藏不等于授权。带
ID 操作必须校验资源所属 Scope/主体；不信任客户端 userId/userName/factoryId。文件必须 owner + scope + resource
证明，不能公共裸路径下载；新资源必须提供服务端 policy。

### 7.1 URL 范围与请求上下文（2026-10-10 用户确认）

前端有已选 Factory 时，所有请求携带 X-Factory-Id；该请求头不决定接口归属，也不授予权限。后端去掉 /api 前缀后，按完整路径段
/system、/tool、/monitor 识别平台管理接口：有效 PLATFORM 角色必需，AUTHORIZED 还需对应平台权限。平台接口不因请求头绑定
FactoryContext 或业务数据库；业务接口解析有效 Factory 上下文，按平台业务权限与当前厂 FACTORY 权限并集授权，仍保持分库与资源隔离。

会话 /api/session、个人 /api/personal、公共登录后读取 /api/common 是明确的共享入口，AUTHENTICATED 不要求平台角色；既有私有文件
/api/platform/files 保持引用地址稳定，归个人资源入口，继续验证 owner/resource，不从请求头切换资源范围。/api/factory/navigation
属业务上下文。EndpointScopePolicy 是 HTTP 路径范围入口，启动检查 URL 与 EndpointAccess 声明一致；平台管理不能声明
PUBLIC，共享入口不能承载管理权限接口。菜单按解析后的完整 URL 分类，按钮继承父菜单；不按权限字符串前缀猜范围。

代码生成器只负责代码生成及自身生成契约，不替 MES 执行业务控制，不限制用户的平台/业务模块名或自动改写模块归属；使用方负责生成结果与应用
URL、权限、数据库规则的一致性审查。

## 8. 事务 / 幂等 / UNKNOWN

本地事务不能回滚外部 HTTP；外部命令结果区分 SUCCEEDED/REJECTED/UNKNOWN。保留原异常链及 requestId，对外安全真实 HTTP
错误；不吞异常/伪造成功。跨平台/业务事务拒绝；线程上下文嵌套及异常恢复/清理，跨线程不得传持久化 Session。同步任务需要 DB
条件抢占、run token、跨入口复用及明确恢复，不能依赖 JVM 锁或 Quartz 单节点参数。

### 8.1 同步任务分布式互斥（强制）

来源：ktg-mes `feat/multi-factory-isolation` 的 AGENTS「定时同步任务并发控制」及 `mes_master_sync` 实现。Base
迁入通用同步协调基础；只保留通用协调机制，不装配外部连接或具体同步流程。

- 所有新增或改造的可重复执行同步任务，使用 `mes_master_sync`
  数据库状态表统一跨节点抢占。工厂同步流以 `factory_id + sync_key` 唯一标识，并由数据库唯一约束保证；不能退化为跨厂全局锁。
- 执行前以条件更新抢占，仅 `status != 'RUNNING'` 可进入运行；必须检查受影响行数为 1，失败明确返回“同步正在进行”，不得继续执行或等待同流任务。
- 每次抢占生成唯一 `run_token`。进度、完成和失败更新必须同时校验 `factory_id + sync_key + run_token` 及 `RUNNING`
  状态；检查更新结果，失去执行权后停止本轮处理，禁止旧任务覆盖新任务或继续推进同步水位。
- 手工同步、Quartz 定时任务和其他入口复用同一套抢占、执行权校验与恢复逻辑。`sys_job` 只负责调度，不替代同步状态和跨节点互斥；禁止仅以
  JVM 锁、`ReentrantLock`、`synchronized`、静态标志或 Quartz `concurrent` 控制同步并发。
- 记录开始时间、结束时间、上次成功时间、处理数量、错误摘要和必要同步水位；失败不得推进成功水位，错误保留异常链，对外不泄漏凭证或内部信息。
- 必须支持节点崩溃遗留 `RUNNING` 的恢复：以活动时间/租约判断超时，恢复更新同时校验旧 `run_token`、`RUNNING`
  和超时条件，并检查受影响行数；重新抢占生成新令牌。活动续期与恢复应避免竞争覆盖，禁止无条件按同步键解锁或永久锁死。
- 状态令牌只能保护对应数据库更新，不能自动隔离已发出的外部副作用；后续接入真实执行器时必须在处理边界校验执行权，并按实际写入契约设计幂等与恢复。通用协调机制不依赖
  ERP 厂代码、外部客户端或固定外部业务类型；未配置执行器明确失败，不返回假成功。
- 验收必须覆盖多节点同流竞争只有一个执行者、不同工厂互不阻塞、各入口共用抢占、旧令牌更新被拒绝、崩溃超时恢复及恢复后旧执行者停止。测试与真实数据库证据分别记录。

## 9. 数据库 / SQL / Ownership（2026-10-09 用户要求）

### 2026-10-11 用户决定：请求准入与固定事务

此决定优先于旧 ADR 的逐 SQL/提交前重新查询管理配置要求。随机 Bearer 与绝对有效期不变；Redis 只存安全身份字段，不缓存完整用户
Entity、角色或权限。请求入口一次读取无密码的账号状态/认证版本及当前角色，部门范围必需字段保留；当前权限和菜单分类在请求内复用，不建立长期权限缓存。禁用、删除、改密在账号事务内写入用户索引撤销
outbox，提交后交付 Redis，失败明确返回已提交/待重试并持续恢复；账号版本准入防止交付失败放行旧会话。注销直接撤销对应会话，不扫描全部在线会话。

已通过入口的身份及已取得的授权不因后来管理变更撤销。HTTP 入口固定请求绑定快照，不保留请求级池使用资格；业务事务 begin 取得并固定租约，事务内、缓存返回前及提交前只检查固定身份、线程/会话归属与租约，不查询最新管理配置。非 HTTP 新独立事务按当前缓存准入；请求旧快照不保证能开启新事务。禁止事务内切厂/切库及中心业务混用。退休池停止新租约，已有租约（包括尚未取连接者）继续完成，归还后关闭；关闭失败保留跟踪供重试。普通工厂名称等编辑不退休池。真正切库可能出现新旧库同时写入，本阶段不承诺在线迁库一致性。

不同 HTTP 请求可并发选不同工厂；前端在读取响应头和响应体后均拒绝过期上下文，旧 401 不得清除新身份/新选厂。Quartz 真实执行器和
OpenAPI 消费者迁移不在本轮范围。

架构只采用中心库 + 多业务库的分库模式。生产只装配两个 DataSource 入口、两个 SqlSessionFactory 和两个 Spring 事务管理器；物理业务池按
Factory 动态租借，不按池新增会话工厂。缺 Scope、连接或版本明确失败，不回退中心库、首库或默认业务库。跨中心/业务事务及事务内切厂明确拒绝。

业务版本/租约、退休池排空、缓存前 statement guard 和异常清理继续保留。参数化 SQL、受控排序、分页边界及避免
N+1；unique/reference/聚合/cache/import/export 都需范围审查。业务 cache 缺 Scope 不能全局回退。ExtraMapper 仅用于跨表关联查询，单表操作禁止新增 Extra，授权决策在
Service。新 CRUD 物理删除；Schema 来自真实 metadata，不带客户数据/seed。

### 9.0 业务表不保存工厂字段（2026-10-13 用户确认）

当前 Base 的业务隔离只采用按 Factory 路由的独立业务库，不叠加表内租户隔离。业务表不得保存 `factory_id` 或用于同一隔离目的的工厂编码等替代字段；业务 Entity、请求／响应 DTO、页面及业务 SQL 不再携带、填写、比较或过滤这些字段。不能以“防御性校验”或“可能配错库”为由重复建立行级工厂隔离。

当前库内的关联对象存在性、产品／工单一致性、确切工艺版本和父子单据一致性仍须校验。请求选厂、权限、固定事务租约、跨库事务拒绝及按 Factory 隔离的缓存继续由基础设施负责；跨厂可以有相同本地主键，不能将其视为跨厂引用或要求全局唯一。

本决定针对业务库的业务表。中心库的工厂主数据、数据源绑定、工厂角色／偏好及 `mes_master_sync` 的工厂协调键属于管理或基础设施语义，保留必要工厂标识，不因本规则删除。既有业务表和代码通过新增正式迁移及重新生成／适配治理；不改写已执行迁移，不覆盖手工规则或并行改动。

### 9.1 固定包与 SessionFactory 归属

| SessionFactory              | DataSource           | TransactionManager           | 固定扫描包                                                            |
|-----------------------------|----------------------|------------------------------|------------------------------------------------------------------|
| `masterSqlSessionFactory`   | `masterDataSource`   | `masterTransactionManager`   | `dev.mesbase.system`、`dev.mesbase.quartz`、`dev.mesbase.platform` |
| `businessSqlSessionFactory` | `businessDataSource` | `businessTransactionManager` | `dev.mesbase.business`                          |

Mapper 按 `*Mapper` 命名，固定包及其子包自动扫描。禁止逐 Mapper 配置、中央类清单、手工 Mapper Bean 或要求生产 Mapper 标注
PersistenceOwner。包不得重叠，重复名称及旧归属注解冲突明确失败。PersistenceOwner 只保留 PLATFORM/BUSINESS 的既有程序式
fixture 校验；PLATFORM 是中心库平台语义，不是第三个库。

生成器 GLOBAL 使用 dev.mesbase.platform.*，FACTORY_SCOPED 使用 dev.mesbase.business.*；不生成 Mapper 归属注解或注册清单。不存在
external 扫描组或 ERP 连接选择入口。

### 2026-10-15 用户确认：普通查询使用注入 Mapper

普通平台/中心库查询与普通业务查询通过构造器注入对应归属的 Mapper，并按需要使用具名 Spring 只读事务；不得仅因“是读取”而调用 `PlatformPersistence`、`readOnly` 回调或手动获取 SqlSession/Mapper。中心 Mapper 仍由固定包扫描绑定 `masterSqlSessionFactory`，业务 Mapper 绑定 `businessSqlSessionFactory`。

已有业务事务中确需读取中心身份/授权等 metadata 时，保留既有独立只读窗口及防护；它是明确的跨库读取边界例外，不能用于包装普通查询，也不能直接换成中心事务或删掉跨库保护。同一read/查询辅助函数被普通查询与事务内校验复用时，普通路径仍须使用注入Mapper；只有运行时确实存在其他数据库的活动事务时才允许进入独立只读窗口，不能因该函数可能被事务调用而让所有读取都走窗口。普通查询与事务内校验可复用查询规则，但需明确区分访问入口；不新增无业务语义的转发层。用户候选资格继续按启用账号、有效角色及当前工厂业务授权判定。

### 9.2 Spring 事务与验收

Service 使用具名 Spring 事务；传播、提交、回滚和挂起恢复由 Spring 管理。业务事务固定 Factory 与连接身份，不能跨线程传
Session。不自动拆分跨库事务，不宣称中心库和业务库本地原子提交。中心 metadata 独立只读窗口保留既有受控入口和只读防护。

修改装配、扫描或路由时，验证两个工厂/入口的确切 Bean 名称及数量、中心/业务 Mapper 归属、不同 Factory 独立库、缺 Scope
拒绝、跨库事务双向拒绝、版本失效、回滚、挂起恢复、池关闭与异常清理。Fixture 通过不等于真实多厂数据库验收。

## 10. 测试 / 验收

### 2026-10-13 用户确认：使用已配置验收库

当前指定验收业务库为 `192.168.18.25:3306/mes_base_dev_business_simple_b`，绑定工厂 `SIMPLE_B`。业务验收及用户复验数据使用此库；缺表时按项目正式迁移创建对应表，不另建验收库。后续若用户调整配置，以最新明确指定为准，并复核中心配置绑定。

真实数据库、数据转换、API 和 UI 验收必须使用用户已配置的验收数据源及其绑定工厂。执行前核对数据源 ID、主机、端口、数据库名及 Factory 绑定；不能仅凭库名相同认定是同一个库。目标不明确时先确认，不自行选择或创建新的验收库、转换库或工厂代替现有验收环境。
本次验收产生的转换数据和用于用户复验的业务结果必须写入该验收库，在对应工厂通过现有服务读取并复核；只在另建库完成转换或保存本地报告不算完成验收。保留来源、合成测试标签及拒绝清单，文档记录实际目标、数量、校验结果和未验证边界；不为存报告擅自新增业务表。
写入前检查已有数据与编码冲突，备份受影响范围，采用明确映射及事务，不覆盖、清空用户已有数据；不可避免的冲突先说明再处理。单元测试的临时 Fixture 不等于配置验收库，不能替代真实验收。不得自行启动、停止或重启用户服务；使用用户指定的现有 5173 前端及后端。

先检查 pwd/git status/branch 与真实调用链，再改最小范围。测试源码存在不等于通过，build 不等于业务验收。按影响执行定向测试、范围/资源负例、真实生成
SQL/契约和 UI，明确静态/Mock/Fixture/本地/生产证据。Java21/标准 Maven 沿用用户环境，不创建项目 .m2/.mvn/private mirror；稳定工具
launcher 可调用标准 Maven。`mvn -B -ntp verify`；涉及打包启动再执行 `scripts/smoke-packaged.py`
（本机可用 `/usr/bin/python3`）。UI 使用 .nvmrc 已安装 Node，不改全局默认；执行 test/build。TEST ONLY 业务探针在 src/test 或自有
target，不包装为生产领域。

每次代码或配置改动都必须审查关联的测试、调用链、配置入口和生成模板。若本次改动使关联测试、旧入口、兼容分支、适配器或其他链路内容失效，必须在同一任务中删除或替换这些已废弃内容，不得为了维持表面通过而遗留无效代码或失真的测试。删除前保留仍验证业务行为、权限、隔离、事务和错误边界的测试，并将它们迁移到新的真实入口。

## 11. Git / 工作区

只改当前任务；不重置、不覆盖用户修改、不危险清理，不顺手重构/升级。确认目标路径，旧公司项目只读。提交/推送/部署需对应授权；本地构建不是部署。

### 11.1 手工修改生成文件的保护（2026-10-13 用户要求）

- 修改代码前检查目标文件是否包含 `MES-CODEGEN` 生成归属标记。手工修改带标记的生成文件时，必须在同一次修改中移除该文件的生成归属标记，防止以后 `replace-generated` 覆盖手工改动。
- 本规则适用于前后端生成产物；仅阅读或审查文件不移除标记，不批量清理未修改文件的标记。
- 修改通用生成器模板、生成器的标记检测逻辑及测试样例时，保留其必要的标记声明和检测字符串；正常生成产物仍由生成器写入标记。
- 移除标记后，再次生成同路径且内容不同时会报 `OUTPUT_CONFLICT`。不得为消除冲突擅自恢复标记或覆盖手工修改；只有用户明确授权将该文件重新交给生成器维护时，才恢复标记。

## 12. 文档 / 证据

代码和文档一致；重要结论附方法/文件/测试证据，无法证明写明无法证明。报告简洁记录做了什么、文件、实际
command/passed/failed、风险和未验证边界；可复用判断依个人治理规范进入项目 docs。禁止输出凭证/令牌/密码；审计不采集请求体。

## 13. Migration / Development

迁移阶段忽略 Codegen Skill，不调用、不要求先生成 CRUD，不让工具定义产品字段或页面。已有功能以源项目代码、配置、Schema和页面流程为输入，优先
COPY / PORT，必要时局部重构；REDESIGN / DROP 必须有具体证据。迁移记录 SOURCE_PRODUCT_PORT 即可，不需申请 Codegen
例外。仅在后续新增功能开发阶段，适合表驱动的标准 CRUD 优先调用 mes-crud-codegen Skill，并完成业务、SQL、权限及 UI 审查。

### 13.0 新业务建模与实施顺序（2026-10-11 用户约定）

每次接入新业务，Agent 先根据用户
prompt、已确认上下文和现有模型完成建模并判断复杂度，再建表、生成或实现代码。用户已提供完整模型时严格沿用，不擅自改表名、字段、关系、类型、必填性或状态语义；有具体冲突或关键缺失时指出并补齐必要信息，不以个人偏好重做模型。用户没有完整模型时，Agent
负责形成满足当前需求的最小模型，说明实体、字段、关系、关键规则与实现路径，不预建假想业务。

- 建表可以在获授权的目标库执行，也可以先在项目内保存可审查的 `.sql` 建表文件；写 SQL
  文件不等于已执行建表，不因新增业务授权自动修改未授权的数据库。所有新建或修改的建表语句（含业务表、基础设施表、迁移 SQL 和生成模板）中，表和每个字段必须有非空、有业务含义的中文数据库 COMMENT，不能只写
  SQL 行注释或重复名称；建表相关的 SQL 说明备注也必须使用中文，技术标识可保留原文。新增字段同样必须提供中文 COMMENT。沿用当前数据库方言与迁移目录约定。
- 新业务表保留必要主键；除主键外只允许新增普通索引，不擅自新增 UNIQUE
  索引/约束、外键约束或其他数据库业务约束。逻辑关系在模型中表达，不因不建物理外键而省略关系设计。若并发唯一性或幂等性必须依赖数据库约束，先明确冲突并请用户决定，不用无约束的“先查再插”宣称并发安全。此规则不删除已有约束，也不覆盖第
  8.1 节既有同步基础设施的唯一约束。
- **简单业务：** 基础列表、搜索、详情和增改删，没有跨表一致性、状态流转或复杂协调；完成模型及可读取的真实 metadata 后直接调用
  mes-crud-codegen Skill，不能重复手写整套 CRUD。生成后仍完成接口、权限、SQL 和 UI 审查。
- **复杂业务：** 有状态规则、跨表一致性、流程、聚合或外部协调时，按第 13.2 节在业务模块建立小而明确的业务边界；真实状态、规则和行为放入相应领域模型/策略，Service
  编排用例和事务，不在 Controller 或通用生成器中塞业务规则。纯复杂查询使用 Query Service，不机械增加 Aggregate/Domain
  Service。
- **共同后端要求：** 表设计完成后，不论业务复杂度都通过 Skill 生成完整基础后端（persistence + backend）；真实 metadata 尚不可读取时先保存建表 SQL，实际建表后生成。结构超出生成能力时明确报告并完善生成机制，不静默改为手写。生成的 Mapper/XML/Example 是可复用的数据访问基础，生成的 Service/Controller 提供简单 CRUD，不要求复杂业务经过生成 Service 才能调用 Mapper。前端可不生成或生成后暂不使用。
- **混合业务：** 各表基础持久化/CRUD 复用生成器，复杂行为在该业务边界中实现。尽量创建一个代表当前业务用例的业务
  Service，负责业务能力的执行顺序、结果协调和事务边界；纯数据访问可以直接调用生成的 Mapper。即使仅操作单表，只要包含业务校验、状态转换、计算或副作用，都应由对应业务 Service 封装，聚合 Service 调用该能力，不直接改表绕过规则。不按每张表重复建立业务编排层，不增加只有转发的包装
  Service，不强制经生成的 CRUD Service 才能访问 Mapper，不建立循环调用；共同业务规则不能只存在于可被绕过的入口。生成普通编辑/删除入口不等于允许绕过状态规则，应限制或替换这些入口；避免两套
  Mapper 或 CRUD 实现。当前生成器只支持单表，主子表协调仍由业务 Service 完成。

### 13.1 CRUD Development

新增单表及常规主子表的基础单表部分，以 mes-crud-codegen Skill 为默认入口，不作为最后兜底；主子表关系和跨表事务不属于自动生成能力。已有生成能力覆盖时，禁止从零重复手写整套前后端
CRUD。生成后允许移动文件、调整命名、补充业务/Factory/权限规则与前端视觉优化，仍须完成契约和安全验收；不让工具定义产品字段。

### 13.2 Domain Business

生产监控、排产、报工聚合、工单进度、复杂统计、流程、OpenAPI 及跨表规则等非标准 CRUD，尽量建立一个代表业务用例的业务
Service/Query Service/Application Service，不强塞进生成 CRUD。Mapper 提供数据访问；对应业务 Service 封装可复用业务能力，包括单表业务规则；聚合 Service 调用这些能力编排流程，纯数据读取可以直接使用生成的 Mapper，但不得绕过既有规则。按职责组织，不把每张表或每个动作机械对应一个 Service，也不根据说明中的示例臆造项目模块。Service 负责规则、业务编排和事务，按需要返回
Entity/Business DTO/Query Model/Domain Model，不依赖 HTTP Result。仅有明确状态、规则和行为时引入 Domain
Service/Aggregate，不机械创造 Domain 层。Controller 不承担跨 Service 业务编排。

### 13.3 Response Contract

- **内部后台标准 CRUD：** 经审查不含敏感字段、不暴露内部持久化状态、前端结构基本一致且无明显契约风险的 Entity，可以直接返回；不强制
  Result/VO，不为形式分层复制模型。
- **敏感/复杂接口：** 包含 password/salt/secret 等敏感字段、字段裁剪、多表聚合、模型重组或明确 ID/状态/枚举转换时，必须使用
  Result/VO 白名单，Controller 显式转换。认证及一次性凭证使用已授权专用协议，不混入普通响应。
- **小程序/ToC/Public/OpenAPI/合作方接口：** 必须独立 Request/Response DTO，禁止直接暴露 Persistence
  Entity；审查隐私、权限、数据范围、内部数据库结构、ID 精度、状态枚举、版本兼容及契约稳定性。外部契约不得随数据库结构任意变化。

只在需要转换时引入 Converter/Assembler：简单同语义复制允许 BeanUtils.copyProperties，目标不定义敏感字段，ID
等特殊字段显式赋值；重复/复杂映射按领域组织。转换器仅转换模型，不查询数据库、不调用 Service、不做业务判断、不管理事务。分页转换保留原
PageHelper 总数再转换 rows；集合工具不猜测业务类型。

### 13.4 Global Response Rule

禁止新增全局业务 ResponseAdvice/AOP 隐式替换响应模型，禁止 Object project(Object) 通过 instanceof 集中猜测业务响应，禁止全局递归
Long→String。ID 类型与语义转换由接口的显式契约决定，数量/总数/耗时保持数值。框架标准协议处理仅在有明确架构理由时允许，不承担业务
View 投影。敏感字段不得仅依靠 JsonIgnore 或运行时类型分支隔离。

### 13.5 Migration Compatibility

旧 MES 迁移与新增开发采用不同规则：迁移尽量原样 COPY/PORT，行为兼容优先，在权限、Factory 隔离、事务和安全不退化的前提下保持接口，不要求一次性理想化重写，不调用
Codegen 替代已有产品。历史结构记录为 migration debt，按有边界任务逐步治理；不新增隐式基础设施。新增开发遵守上述 Base
规则，不复制旧的不良模式。兼容目标不得用于豁免安全/隔离要求。

## 14. Skill / 工具边界

新增开发功能先检查适用 Skill；迁移工作不调用 Codegen Skill。Skill 调用底层工具而不重实现工具，完成后仍需项目测试/业务审查，不能跳过架构约束。开发期仓库
Skill 入口：`.agents/skills/mes-crud-codegen/SKILL.md`；配置/CLI 详见 docs/development/codegen.md。

### 14.1 基础生成范围与目标位置（2026-10-10 用户约定）

2026-10-11 配置入口：项目参数由 `docs/development/codegen-config.review.yaml` 显式提供，Agent
使用 `scripts/mes-business-codegen CONFIG_YAML REPORT_JSON`
。先读取配置，不能自行填隐藏项目默认值或改用户配置。`selection.tables: []` 表示全量，非空只生成列出的表；产物选择独立。无下划线的模块包段取配置
fallback（本项目 business）。配置项必须明确提供，schema/关闭的条件注解可显式 null；可选 tableOverrides 不控制选表。菜单 SQL 使用
add-or-update，保留菜单 ID 和角色绑定，仅生成不执行。当前旧 `scripts/mes-codegen` 为旧 CLI，尚未移除其默认值；不得用它代替新配置入口。

2026-10-11 用户补充：生成器不得按 user_id、owner_id、password、secret
等字段名猜测业务语义并拒绝生成。生成准入仅判断实际生成能力与结构支持；敏感响应、主体及资源授权规则由使用方在生成后审查。

mes-codegen 默认生成基础单表持久化与后台 CRUD；可选 `generateSync` 生成同步脚手架，默认 false，历史配置缺参同样为 false。开启时必须显式配置 `sync.matchKey/batchMaximum`，可用 `tableOverrides.<table>.syncMatchKey` 指定物理匹配列；同步输入仅来自真实 Schema，不猜 code/name/status 等业务字段。默认查询实现明确抛未接入异常，不产生示例业务数据。当前同步生成只支持 Factory 范围及 String/Long/Integer 单匹配键，复杂业务规则由使用方审查。持久化复用 MyBatis Generator 的 MyBatis3 /
XMLMAPPER：Entity、Example/Criteria、Mapper + XML；Service/Controller 必须同步调用该真实生成接口，分页复用 PageHelper，不新增注解
SQL、锁查询或业务状态规则，不生成 ExtraMapper。

**Extra 边界（2026-10-11 用户确认）：** 所有单表操作必须复用生成的 Mapper/XML/Example，不得生成或手写 ExtraMapper、Extra XML，也不得通过改名为 DAO/Repository/自定义 Mapper 绕过。查询复杂、条件多、批量、单表统计或业务状态复杂都不是新增 Extra 的理由；单表业务规则由 Service/领域策略编排。生成能力不足时修补通用生成机制及相关测试，不能只为某个业务表另写单表 Extra。只有确需跨表关联（如 JOIN 或涉及其他表的关联子查询）才允许新增 ExtraMapper/XML；该 Extra 只承担跨表查询，不复制单表 CRUD，也不承担授权或业务编排。业务 Service 分别调用多个表的生成 Mapper 不等于需要 Extra；当前生成器不会自动生成跨表 Extra。

配置是项目参数取值的唯一来源，Agent 必须读取用户修改后的配置，不从旧 CLI 默认值补齐。必要参数组为
source、selection、naming、output、templates、backend、fields、limits、frontend、menu、execution；完整字段和注释见 `docs/development/codegen-config.review.yaml`
。本项目配置可被用户指定的其他配置替代，生成器不硬编码 MES 项目取值。

`output.backendRoot/backendModule/javaPackage/frontendRoot` 决定前后端位置，当前项目承载模块为
mes-business；`naming.module` 决定 API/页面功能模块，与 Maven 模块、数据库名不同。table-prefix 策略取首个分隔符前缀，无分隔符用显式
fallback（本项目 business），tableOverrides.module 优先。业务配置使用 `dev.mesbase.modules.<module>`
、businessTransactionManager、factory 权限范围；平台配置使用 `dev.mesbase.platform.<module>`
、masterTransactionManager、platform 权限范围，仍须匹配第 7.1/9.1 节。可生成到其他承载模块用于拆包；创建新 Maven
模块和接入依赖另行处理，生成命令不改变 reactor 或数据库拓扑。

调用：先 `mvn -B -ntp verify` 构建最新 JAR，再 `./scripts/mes-business-codegen CONFIG_YAML REPORT_JSON`
。执行行为由配置决定，不由报告文件名决定；当前用户配置可能已是 write/replace-generated，不得假定一定预览。预览需明确
dry-run；新增代码用 write/fail；重新生成已有文件用获授权的 write/replace-generated，会覆盖生成文件中的手工修改。需要审查副本可复制配置并输出到独立
target 目录。不得为了消除冲突自动改变覆盖策略。

业务隔离依赖按当前 Factory 路由的独立业务库。本项目业务表不含 factory_id；生成配置不使用 serverScopeColumn 建立表内隔离，不注入 Factory 字段、Mapper/Service Factory
参数或 SQL Factory 谓词。通用生成器仍按真实 metadata 工作，不按字段名拒绝其他项目的表；本项目使用方须先按第 9.0 节治理已有工厂字段，不继续生成冗余行级隔离。缺 Scope
拒绝、连接/版本与事务内切厂保护继续由既有基础设施负责。范围选项只决定包归属、事务管理器及 HTTP 权限范围，不构造表内租户业务。Example
仅供服务端使用，不直接绑定 HTTP 请求中的 SQL 条件/排序字符串；业务开发负责批量写入条件和业务约束。

### 14.2 生成权限与菜单 SQL（2026-10-11 整理）

本项目权限为 `<承载模块标识>:<完整表名下划线转冒号>:read/create/update/delete`。mes-business 对应 business，mes-erp 对应
erp；由配置 `backend.permission.prefix/resource` 显式表达，不由生成器猜物理数据库或偷偷填默认值。表名前缀不截取、不转驼峰；例如
business:pro:workorder:read。Java 常量、Controller、Vue、菜单声明和 SQL 一致。单删、批删共享 delete 权限。

生成中心库 sys_menu 目录/页面/四操作的 add-or-update SQL，位置取 output.menuSql（本项目承载模块 docs/generated/<module>/*
-menu.sql）。更新已有、插入缺失，保留菜单 ID 和角色关联，不硬编码 ID，不自动执行或写角色授权。SQL
串行执行；运行前审查重名及定位条件，不承诺没有唯一约束时并发执行防重。

## 15. UI Base

复用 mes-base-ui 的统一 Shell/CrudPage/request/dict/permission，不为每页创造视觉体系。代码生成页只是 Functional
Scaffold；必须检查查询、Toolbar、表格/列顺序、枚举/日期/精度、表单分组、loading/empty/error、确认删除、分页、窄屏、中文/tooltip/按钮权限并优化。API
只读真实契约，不新增 AjaxResult/rows/data
多结构兜底。按用户2026-10-06要求保存刷新登录态，浏览器只保存Token/选厂偏好，不保存密码或权限；2026-10-15用户更新：前端不保存或按本地时间判断Token有效期，恢复须重新校验服务端身份与授权，由后端401或注销清除。选厂清除旧授权、数据和对话框，过期响应不回填。

## Java code style

Java formatting is repository-controlled by `.editorconfig` and `.idea/codeStyles/Project.xml`, using the IntelliJ IDEA
native formatter. Generated and hand-written Java should follow IDEA's normal readable style as closely as possible; do
not rely on a personal alternative formatter.

Avoid long lines and compressed statements. Keep one logical statement per line. Wrap long method declarations,
parameter lists, annotations, calls, chained calls, conditions, generic types, and lambdas at readable boundaries. Do
not join multiple fields, statements, or blocks onto one line merely to reduce file length. Prefer the existing
IDEA-style line breaks and four-space indentation over dense code.

After modifying Java files, run from the repository root:

```sh
./scripts/format-java path/to/Changed.java
mvn -B -ntp test
git diff --check
```

The formatter script formats only the Java files passed to it; do not reformat unrelated Java files in a task. A task
must not be reported complete if formatting, tests, or `git diff --check` fails.

## 16. clean-room / Source Product

clean-room 不等于禁止复用旧项目。通用源码、有效配置键、Schema和页面产品资产默认进入迁移候选；按所有权/授权/许可核对来源，保留许可及修改记录，无法确认的部分标
REVIEW。不得带入公司身份、客户/生产数据、Secret、内部账号、真实连接值、客户专用规则或不应复用的公司资产；混合实现先抽离专有部分，不整块舍弃功能。保留
Factory/权限/ownership 行为，允许更换实现和分包；拒绝默认库兜底、HTTP200错误、任意反射 Job。AGENTS 定规则、Skill
定开发工作流、工具执行，不能用架构或工具反过来定义迁移产品。

## 业务人员 / 部门 / 岗位嵌入组件（2026-10-10）

业务模块需要人员、部门、岗位搜索或选择时，优先复用 mes-base-ui 的
`src/components/business/directory/PersonnelSelect.vue`、`DepartmentSelect.vue`、`PostSelect.vue`。
接口统一为 `/api/business/directory/{personnel|departments|posts}` 与 `/{id}`，仅提供 GET
列表和单个查询；禁止在这组能力中增加新增、修改、删除或角色分配。
复用中心库 sys_user/sys_dept/sys_post 的平台 Mapper，使用 masterTransactionManager 只读事务；不新增表、不复制目录到业务库。
HTTP 授权使用当前工厂的 business:directory:{personnel|department|post}:read 权限，不能让组件调用平台管理 API 或要求
system 权限。
中心目录为跨厂共享引用，不表示人员所属工厂；不能把选厂过滤误称为目录行隔离。人员只暴露编号、名称、人员编码和部门编号，禁止返回密码、联系方式、角色等
SysUser 敏感字段。
组件 ID 使用字符串，切厂/撤权清理已选项与候选数据、拒绝过期响应。平台管理页面保持原有管理能力。
契约与使用方式见 mes-base 的 docs/development/business-directory.md。


## 2026-10-11 用户确认：业务请求授权与路由缓存

- 业务授权按接口完整 URL 范围与动作权限执行，不逐请求全量读取菜单做权限减法；菜单分类留给导航、角色分配和启动/菜单编辑审查。权限码跨平台/业务复用或工厂角色误授平台菜单须明确拒绝并指出问题，不静默掩盖。权限/角色仍逐请求中心准入，授权只请求内复用。
- HTTP 入口从共享 Redis 元数据获得有效绑定，固定不可变请求快照；事务取得当前可用版本池租约后固定身份，管理变更不干涉已取得租约的事务。Core FactoryScope 仍仅 FactoryId。没有请求级 reservation 或旧冷池保留保证。
- 路由配置接受短暂 DB/缓存不一致：缓存命中不续期，未命中一次绑定 JOIN 后写入独立 Redis 键，每条固定 TTL，显式配置 `mes.datasource.metadata-ttl-seconds: 300`。提交后淘汰受影响工厂及默认选厂条目，回滚不淘汰；淘汰失败明确记录日志并返回已提交操作的正常结果，旧值等到过期。允许并发旧读取回填，其寿命由该条 TTL 限制。过期后数据库失败明确拒绝，Redis 读取/发布失败返回 503，不续用本地过期值或回退错误工厂。
- 不为路由缓存新增代际、epoch、失效 outbox、后台恢复或账号投影。缓存不存明文密码、Connection/Session/池或用户权限。此决定取代同日先前强一致协议；账号认证版本及会话撤销 outbox 保留。
- 普通工厂信息编辑不退休池；冷池按数据源身份分段锁准备，网络探测不持全局 resolver 锁，失败关闭候选池；已有租约排空关闭旧池。实际边界及历史迁移处理见 docs/development/request-admission.md；Fixture 不等于真实部署验收。

## OpenAPI 能力恢复（2026-10-12 用户明确要求）

恢复 ktg-mes 已迁入的客户端管理、Secret/Token、API 授权、独立外部认证链与管理页面；不能把“只保留文档”当作完整迁移，不能未经用户确认删除产品能力。
标准构建包含实现，`mes.system.enabled` 与 `open-api.enabled` 控制启用。客户端/授权使用中心库，外部身份不作为 SysUser，不解析后台选厂偏好、不接入内部角色授权。
具体 MES API 与业务范围策略按需求接入，不暴露内部 CRUD，不新增外部数据库或第三套 SessionFactory。
保留已应用迁移文件；V22 补偿 V16 菜单删除，不猜测或自动补发已删除角色授权，不执行真实迁移除非得到授权。


## 2026-10-12 可选同步生成与统一配置

正式生成唯一参数入口继续为 `docs/development/codegen-config.review.yaml`；不得另建业务专属默认配置或从旧 CLI 补项目参数。`selection.excludeTables` 排除技术表，当前明确排除 `flyway_business_schema_history`。2026-10-12 用户授权时为 `generateSync: true`、全量业务表、`write/replace-generated`；同步开关已由下述新决定取代，表选择及覆盖策略仍由实际配置决定。

2026-10-13 用户决定：当前及后续生成暂不生成同步相关内容，正式配置和开发期业务配置保持 `generateSync: false`，以后需要时再明确开启。此决定只关闭后续生成，不要求删除既有同步业务或协调框架，不自动重新生成已有文件。

生成的同步 Service 直接使用本表 QueryPort，默认未接入实现不是 Spring Bean，由 ObjectProvider 在没有真实实现时选择，避免默认与真实 Bean 竞争。外部只读查询先完成；未接入时不写业务库或同步状态。查询完成后复用已有 SyncCoordinator 抢占，成功抢占后才进入独立 Spring 代理的 SyncWriter 写事务。只读查询可并发，写入批次受已有协调协议约束。同步权限为当前生成权限前缀追加 `:sync`，菜单 SQL 同步 add-or-update、不自动执行或授予角色。现有手写 Material 同步保留 `material:sync`，Test Adapter 只在测试源目录。

生成器目前只覆盖/发布文件，不跟踪和删除历史独立文件。true 改 false 后的新产物无同步引用，但旧的 SyncController、External DTO、QueryPort、未接入 Adapter、SyncResult、SyncService、SyncWriter 必须逐文件审查移除；禁止扫描删除手写业务目录或宣称自动清理。具体六表文件清单与验证见 `docs/development/codegen.md` 的同步章节。


## ERP Integration Architecture（2026-10-14）

- MES Business Service → ERP领域接口 → 配置选择的领域实现 → 厂商Gateway。复用现有 WarehouseErpAdapter 作为仓储领域业务接口；Production/MasterData 仅在真实消费者需要时建立，不按一个API建立一个类、不集中成巨大Service。
- 领域方法表达完整业务行为；厂商实现负责其创建、提交、审核、查询等内部步骤、业务响应解析和人工恢复。Gateway仅负责认证、通讯、序列化及底层响应解析，不写MES库；MES Service不感知厂商字段、错误码或流程步骤。HTTP 200不等于业务成功。
- 托管运行时必须复用 FactoryIntegrationSnapshot / Context / Registry 的工厂配置，LOCAL 映射 LOCAL_ONLY，ERP 映射 CONNECTED；须匹配该厂绑定及 provider，缺少或歧义明确失败，不回退本地成功。旧仓储 YAML 运行分支已删除，测试显式使用 Factory 快照；仅 V34 历史迁移导入契约保留，详见“配置唯一权威与废弃配置管理”。无外部数据库装配。
- 标准结果区分 SUCCEEDED（SUCCESS）、REJECTED（FAILED）、UNKNOWN，并标明 LOCAL_ONLY/CONNECTED。阶段失败保留已知外部身份；本地模式不伪造ERP单据、审核或冲销事实。
- ERP单据身份、编号、业务状态、外部操作来源证据、操作结果及安全诊断存独立Extra；WorkOrder/Warehouse 的 LOCAL/EXTERNAL 业务来源属于 MES 业务身份，须按 2026-10-17 规则可信持久化，不以 Extra 或当前模式推断代替；随当前业务库FactoryScope隔离，不在业务表新增工厂字段或ERP事实列。MES表保存MES业务身份、状态与本地操作身份。Extra写入由MES协调逻辑管理，不开放普通编辑来伪造外部事实。
- 查询确定性 FOUND/NOT_FOUND/UNKNOWN 与外部业务状态独立。查询只读、不改MES；NOT_FOUND不证明创建失败、不授权重建。核实经领域规则，在外部查询后短事务重锁MES对象并检查Extra操作版本及本地操作身份，原子更新MES与Extra；UNKNOWN不推进，VOIDED不因EFFECTIVE恢复，冲突保留诊断供人工处理。
- ERP HTTP不在长事务或持有行锁时调用；创建/作废先持久化稳定意图，外部后果不能由本地回滚撤销。已知外部存在或结果未知不得重复创建；重复核实不产生外部写。所有编辑、明细变化与动作必须参与同一版本保护，不能让旧查询覆盖新作废。
- 查询与核实使用独立权限，可信FactoryScope定位MES记录后从服务端Extra构造外部身份；禁止客户端指定外部身份或跨厂查询。诊断安全且限长，不向前端暴露Token、Secret或供应商原始报文。
- 优先人工核实与有真实厂商实现的人工恢复，不允许人工标记成功；未接入继续审核时不制造审核业务。不建自动补偿/Saga/通用工作流、动态插件、无限重试或无需求空接口。V1不支持跨ERP历史迁移或单厂多ERP协同，历史provider不匹配明确拒绝并交项目处理。

## 2026-10-15 用户确认：业务枚举与统一模板（强制）

- `dev.mesbase.business` 中具有封闭取值的业务状态、类型、执行模式、操作结果等必须定义为 Java `enum`。业务判断、赋值、查询条件、同步转换和 Mapper XML 不得散落字符串或数字魔法值，也不得用 `static final String` / 字符串集合代替业务枚举。不同业务含义即使编码相同也分别建模，例如领料状态、提交尝试状态与外部副作用不能混用。
- 枚举以用户给定的 `Status` 形式为模板：枚举项显式声明中文 `description` 和稳定 `value`；字段使用 `private final`；构造器赋值；提供 `getDescription()`、`getValue()`、静态 `from(value)` 和静态 `toMap()`。`from` 未匹配（含可空入参）返回 `null`，调用边界必须明确拒绝非法值或处理未知，不能默认映射到成功/有效状态。`toMap()` 使用 `LinkedHashMap` 保持声明顺序，每次返回独立 Map。
- `value` 类型依据已确认的数据库和接口契约选择，不机械套用 `byte`。已有 VARCHAR/CHAR 编码保留 `String`；实际为 byte 的业务使用 `byte` / `Map<Byte, String>`。禁止仅为统一枚举样式擅自改变存量编码、数据库类型、必填性或接口序列化。持久化与协议转换显式使用 `getValue()`，不依赖 `name()` / `ordinal()` / `valueOf()` 绑定枚举名称。
- Java 业务逻辑优先解析后用枚举比较；兼容字符串 Entity/DTO 时可使用枚举 `getValue()` 比较、赋值和传入生成 Criteria。完整合法值校验复用 `from`；动作允许的状态子集由业务规则显式表达，不能因引入枚举扩大可编辑或可流转状态。
- Mapper XML 涉及枚举时从枚举 `getValue()` 获取参数（例如 `<bind>` + `#{参数}`），禁止硬编码状态，也禁止 `${}` 字符串拼接。历史已执行迁移中的编码和默认值不因此改写。
- 用户可维护字典、外部 provider 标识、单号/节点号前缀、权限码、配置键、错误文案等不凭名称臆造成封闭枚举。必须先确认真实取值语义；字典字段保留字典契约。
- 新建业务及代码生成后必须按此规范审查并适配；生成器不推断业务枚举、不制造默认状态。手工修改生成文件仍须移除该文件的 `MES-CODEGEN` 归属标记。审查关联测试、同步入口和查询 SQL，并验证枚举值兼容、反向解析、未知值、选项顺序及业务状态保护。

字符串编码模板见 `mes-business/src/main/java/dev/mesbase/business/shared/enums/EnableStatus.java`。数值编码采用相同结构，例如：

```java
public enum Status {
    DELETED("已删除", (byte) -1),
    INVALID("新建", (byte) 0),
    DISABLE("禁用", (byte) 2),
    VALID("有效", (byte) 1);

    private final String description;
    private final byte value;

    Status(String description, byte value) {
        this.description = description;
        this.value = value;
    }

    public static Status from(byte value) {
        for (Status item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }

    public static Map<Byte, String> toMap() {
        Map<Byte, String> result = new LinkedHashMap<>();
        for (Status item : values()) result.put(item.value, item.description);
        return result;
    }

    public String getDescription() { return description; }
    public byte getValue() { return value; }
}
```

实际 Java 文件顶部必须显式导入 `java.util.Map`、`java.util.LinkedHashMap`。


## 2026-10-15 用户确认：统一业务目录与引用缓存

工艺、工序、系统用户等跨页面通用选择及引用回显统一由 BusinessDirectory 提供：前端公共组件经 BusinessDirectoryController，后端业务 Service 调用 BusinessDirectoryService；禁止在各业务模块、Controller 或前端组件另建同对象的共享名称缓存。批量回显先收集本批去重 ID，统一批量读取缓存，仅对未命中 ID 执行参数化 IN 查询，再按 ID 回填；不能在行循环中逐条查询数据库或逐次远程缓存调用。

同一对象的单对象缓存统一使用经审查的 Entity，通用层提供较宽、可复用的数据能力；具体业务需要更窄字段时，在上层按需求转换 DTO/VO，不为这些转换另建投影缓存。不缓存密码、联系方式、角色、权限或生产资格；敏感 Entity 必须在进入缓存前落实安全字段边界。状态仅用于展示，授权、人员可派工资格、存在性及工单适用工艺等规则仍由相应业务服务实时校验，不能由目录缓存替代。历史事实优先使用已有姓名/工序快照，当前目录名称不能伪装成历史快照。

业务目录缓存按当前 Factory 与业务资源隔离，命中前后保留固定事务/租约检查；缺 Scope 不回退全局或中心库。中心用户目录为共享引用。对象写入成功提交后统一失效，回滚不失效；写事务不得向共享缓存发布未提交对象。Redis 故障可退回一次批量数据库查询，数据库失败必须传播。通用缓存实现仅作为目录服务的内部基础设施；原有完整实体业务缓存、认证会话和数据源路由缓存不属于名称回显，不因本约束复制或替代其规则。

单对象缓存必须复用通用详情 Key：同一数据库范围、业务资源和 ID 只使用同一个 `<资源前缀>detail:<编码ID>`，由 DetailCacheKeys 统一生成。BusinessDirectoryService 可以保留批量缓存读取和未命中 IDs 一次 IN 查询，不强制循环调用 findById/selectById；但不得另建 reference/directory/label 或 IDs 组合形式的对象缓存 Key，也不得在同一 Key 下混存 Entity 与窄 DTO/VO。批量读取与通用详情查询必须能双向命中同一份 Entity 数据，使用同一提交后失效规则。资源级失效版本仅作为通用详情的写入屏障元数据，不是另一份对象缓存。

新增目录对象时同时验证生成的新增/修改/删除及导入/同步入口均触发通用淘汰；中心用户等对象通过其统一写入边界失效。验收覆盖真实 Redis 的详情与批量双向复用、完整 Entity 字段保留、提交后刷新、回滚保留及迟到回填被拒绝，不仅验证 key 含有 detail 字样。
