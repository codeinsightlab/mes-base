# Operation + Routing 联合审查与实现

2026-10-13。Source：`/Users/user/work/heri/ktg-mes`，页面：相邻 `ktg-mes-ui`。
本轮用户明确要求 Codegen + Extra Business Logic，优先于迁移默认 COPY/PORT 规则。
实现前完成下列只读审查；后续验收追加到本文，不创建平行报告。

## OPERATION_ROUTING_REVIEW

### Operation

| 项目 | 审查结论 |
| --- | --- |
| Source Tables | 主定义 `pro_process`；子能力 `pro_process_user/content/inspection`、`qc_defective_cause`、`process_file_relation`、`process_external_mapping`；资源 `md_workstation.process_id` |
| Source Code | `pro/domain/ProProcess.java`；`pro/controller/ProProcessController.java`；`pro/service/impl/ProProcessServiceImpl.java:145-286`；`mapper/pro/ProProcessMapper.xml`；Vue `mes/pro/process/index.vue`、`detail.vue` 及子页 |
| Core Fields | process_id/code/name、attention、enable_flag；工序定义与执行记录分开 |
| Source Complexity | 新增复制人员/不良原因并自动关联固定 admin；编辑正式/临时单价和有效期；人员准入、检验、文件、计件财务均不是简单定义 CRUD |
| Keep Fields | 本地 id、服务端 factory_id、code、name、description（attention）、status、sort；external_id 可空，仅表达来源关联；revision 为并发修改控制 |
| Drop Fields | attr1..4、工资/临时单价/期限/send_status、固定 admin、冗余创建人来源、不良扣款；本轮不迁人员/文件/检验子能力 |
| ERP Fields | ERP 标识可映射 externalId；不把来源主键当 Base 主键，不建 ERP 专有列 |
| Generated Part | 真实 MySQL metadata → MBG Entity/Example/Mapper/XML、CRUD/显式 VO、前端、权限和脚架测试 |
| Extra Code | 状态/排序校验、厂内编码唯一、并发 revision、被步骤引用时禁止删除；同步与人工写入复用相同规则 |
| Sync Fields | externalId/code/name/description/status；不同步工资、人员、工时、质量或设备 |
| Converter Changes | process_code/name/attention/enable_flag → code/name/description/ENABLED 或 DISABLED；重新生成本地 ID，按编码映射步骤引用；非法或重复编码明确拒绝 |

### Routing

| 项目 | 审查结论 |
| --- | --- |
| Source Tables | `pro_route`、`pro_route_process`、`pro_route_product`、`pro_route_product_bom`；`pro_process_step` 是工序检验工步，不能当路线步骤 |
| Source Code | `pro/domain/ProRoute*.java`；`ProRouteController/ProRouteProcessController/ProRouteProductController`；相应 ServiceImpl、Mapper/XML；Vue `mes/pro/proroute/index.vue` 的组成工序/关联产品两标签页 |
| Core Fields | route_id/code/name/desc、enable_flag；route_process.route_id/process_id/order_num/key_flag；route_product.route_id/item_id |
| Source Complexity | Controller 改前序 nextProcess、禁止重复工序/序号/多个关键点；产品关系是多对多；报工实时读取前后序和关键点；启用列表取第一条，没有确定版本选择 |
| Keep Fields | code/name/description；一个物理 Routing 行代表一个版本；独立 RoutingStep、RoutingProduct，保留线性 sequence 与 is_key |
| Drop Fields | 冗余 nextProcess、甘特颜色/准备等待时间、route_code_mes、forge_joining_flag、attr1..4、产品工时/数量、步骤 BOM；检验执行规则单列 DEFERRED |
| ERP Fields | 标准 externalId/sourceVersion 仅做来源标识；Base version 服务端分配，不能直接套用 ERP 版本 |
| Generated Part | 四张表全部生成基础 persistence + backend；Routing 生命周期接入主 Service；步骤/产品最终只通过父级内容接口维护，不保留可绕过冻结的独立 CRUD 页面/接口 |
| Extra Code | 事务批量保存产品和步骤、引用校验、序号/工序去重、单关键点、发布、停用、复制、新版本、冻结、revision 并发检查和删除限制 |
| Version Strategy | 同工厂 code+version 唯一；version 从 1 开始。每个版本自己的 id。发布内容不可编辑/删除，修改创建下一草稿；复制生成新 code 的 v1 草稿 |
| Publish/Freeze Strategy | 发布至少一个产品/物料和一个步骤，引用必须同厂且启用；发布时冻结产品编码/名称与工序编码/名称/工艺要求快照。PUBLISHED→DISABLED 保留内容；停用不删除历史；重新启用需独立发布权限 |
| Sync Fields | externalId/code/name/description/sourceVersion/productCodes/steps(sequence,operationCode,description,isKey)；来源变化更新草稿，已发布/停用则创建新草稿，不自动发布 |
| Converter Changes | route → v1 DRAFT；所有产品关联保留；order_num 按升序标准化 1..N；缺失父级/工序/物料、重复顺序/工序、非 SS、多关键点拒绝整条路线；不自动宣称旧启用记录已通过 Base 发布审查 |

### 源数据与调用链证据

只读查询 `192.168.18.25/j2eedb`（与 Source application-dev.yml 一致），仅汇总写入文档，不写公司明细到 Git：

- pro_process=587；pro_route=44；pro_route_process=539；pro_route_product=47。
- 7 条路线关联多个不同产品；不能简化成 routing.product_id。
- 步骤均为 SS（539），重复 order_num=0，重复 route+process=0。
- 当前 Source 工序/路线编码重复=0；有 1 条步骤缺失父级或工序，必须记录 CONVERSION_REJECT。
- md_workstation=19；当前没有一个工序关联多个工作站的样本，但 Schema 是工作站持有工序 ID，方向并非 Operation 持有工作站。
- `ProRouteProcessServiceImpl.checkKeyProcess` 按产品取启用路线列表第一条，再从工作站获取 processId；`ProReportingWorkServiceImpl` 用 findPre/Next/First/LastProcess 和当前路线校验生产。工单/流转还持有 routeId/processId，未找到明确业务版本发布/冻结协议。

## OPERATION_SCHEMA / ROUTING_SCHEMA / ROUTING_STEP_SCHEMA

- operation：id、factory_id、code、name、description、status、sort、external_id、revision。
- routing：id、factory_id、code、name、version、status、description、external_id、source_version、revision、published_at。
- routing_step：id、routing_id、sequence、operation_id、operation_code/name/description（发布快照）、is_key、description。通过父 Routing 继承 Factory，所有入口先检查父级。
- routing_product：id、routing_id、material_id、material_code/name（发布快照）。支持一条路线多个物料/产品。
- 本批用户已允许调整 unique；采用厂内编码、code+version、route+sequence、route+operation、route+material 唯一约束，提供真实 MySQL 竞争证据。无物理 FK；Service 使用同事务引用检查与行锁，不能宣称数据库对绕过 Service 的写入有 FK 防护。
- Operation 不绑定单个 workstation/workshop/equipment；工作站已有可选 workshop_id。路线步骤的特定资源约束没有 Source 依据，本轮 DEFERRED，不虚构资格关系。

## ROUTING_VERSION_RULES / PUBLISH_RULES / REFERENCE_RULES

草稿内容保存与状态转换必须校验 revision，并在同一 businessTransactionManager 事务内更新父级与子项。
发布版内容、产品集合、步骤/序号/快照永不直接改动；停用仅改变可选状态。
WorkOrder 下一轮应保存 routingId（确切版本的行 ID）与 version，不按 code 动态读取最新记录。
已发布记录本轮一律不可删除，所以未来执行引用不因维护删除而失效；未接入 WorkOrder 的引用表或假检查器。
多条路线/版本可同时发布，本轮无默认路线；消费者必须显式选择确切 ID，不复用 Source 的 get(0)。
删除 Operation/Material 必须检查路线上所有草稿及历史版本引用；并发发布/删除在引用行锁下协调。

## ERP_SYNC_STRUCTURE

复用当前 Material 的 QueryPort、未配置 fallback、SyncCoordinator 与生成 Mapper；本批同步先抢占，再查询，查询后复查执行权，再进入主 Service 的业务写事务。
Prototype 文档的旧 TEST Factory ERP Resolver 已退役，当前不恢复 TEST-only Resolver 或第三个数据源。
QueryPort 接收 FactoryScope，由接入 Adapter 明确解析该厂 ERP 配置；本轮默认返回 ERP_SYNC_NOT_CONFIGURED。
同步和人工维护均可用；不要求 ERP 必配。操作权限用用户要求的 operation:*/routing:*，publish/copy 独立。
外部查询在业务写事务外；中心同步令牌与业务提交不宣称原子。执行权在批次边界校验，已发出的数据库副作用不能被中心令牌自动撤销。

## GENERATED_PART / EXTRA_CODE

Codegen 配置使用当前用户 YAML 的参数副本，仅选本批表、write/fail，不修改全量用户配置。
原始生成与后处理分开记录；当前 metadata 的隔离生成副本和报告在自有 target/operation-routing-review 中，稳定摘要见本报告的验收记录。
不增加单表 ExtraMapper；业务规则调用生成 Mapper/XML/Example。

## CONVERTER_MAPPING

提供最小 SQL Converter，读隔离真实 Source 副本，目标为自有验收业务库；先验证物料依赖已经转换。
Source 明细副本只保存在 Git 忽略目录/自有 MySQL 验收库。
拒绝对象记录 source ID 与原因；不以丢弃坏步骤、自动造工序或改成默认物料来达到全量成功。

## SOURCE_RULES_REUSED / SOURCE_COMPLEXITY_NOT_REUSED

复用：工序定义、启用、路线产品多对多、线性顺序、同路线禁止重复工序/序号、最多一个关键点。
不复用：固定 admin、工资/临时价格、冗余 nextProcess、锻接/客户入库、报工执行、检验/BOM、列表首条默认路线。

## DEFERRED

WorkOrder/Reporting/WIP、实际执行引用迁移、路线默认选择、审批、分支并行/返工图、BOM/质量执行、人员资格、工序与资源资格关系、真实 ERP Adapter、在线 Source 写入。
当前 Source 工作站→工序关系保留审查映射，后续执行资源任务再迁，不塞入 Operation 单工作站字段。

## MYSQL_RESULT / API_RESULT / UI_RESULT

实施前状态：未运行本批验收。后端初始 `mvn -B -ntp verify` 失败于既有 tests 引用已退役的 dev.mesbase.modules.material 包，当前生产代码已在 business.basic；先保留业务断言并迁移到真实入口，不能当作本批新增失败或删除断言掩盖。
后端初始 HEAD=3e2ff2a，工作区干净；前端已有 useTableColumns.ts/tableColumns.test.ts 修改，本轮不覆盖。


## 2026-10-13 实现与验收记录

### GENERATED_PART / EXTRA_CODE

真实业务 MySQL metadata 生成 `operation`、`routing`、`routing_step`、`routing_product`，报告为 GENERATED、4 supported、0 rejected。V4 增加工艺要求快照后，`routing_step` persistence 又从真实 metadata 生成一次（1 supported、0 rejected）；最终四表完整隔离审查副本再次生成通过。

- 配置：[operation-routing-codegen.yaml](../development/operation-routing-codegen.yaml)。原 `codegen-config.review.yaml` 可用，未被修改；原配置选表为空表示全量并允许 replace-generated，本批副本显式选四表、write/fail，避免覆盖已维护的其他业务。
- 保留 MBG Entity/Example/Mapper/XML；无单表 ExtraMapper。权限后处理为用户要求的 `operation:*` / `routing:*`，缓存仍使用 `business:operation` / `business:routing`，二者语义独立。
- 退役步骤/产品的独立 CRUD Controller、Service、DTO/VO、页面、菜单及不再适用的脚架测试，统一经父 Routing 保存；生成的 persistence 保留。
- Operation 增加厂内编码唯一、revision CAS、状态/排序及删除引用规则。Routing 主 Service 承担父子事务、顺序、引用、发布/停用、复制/新版本与冻结；Controller 显式白名单投影。
- 按 2026-10-13 的生成文件保护规则，手工维护的 Controller/Service/Command/VO/页面/API/已改合同测试移除 MES-CODEGEN 标记。未手工修改的生成 persistence 继续保留标记；RoutingStep XML 手工清理空白后也移除标记。不能直接将四表重新生成到生产路径覆盖生命周期代码。

### OPERATION_SCHEMA / ROUTING_SCHEMA / ROUTING_STEP_SCHEMA

结构见 [V3](../../mes-admin/src/main/resources/db/business/V3__operation_routing.sql) 与 [V4](../../mes-admin/src/main/resources/db/business/V4__routing_operation_instruction_snapshot.sql)。四表及每列均有中文 COMMENT。V4 单独增加 `routing_step.operation_description`，不改已执行 V3 的 checksum。

V4 不拿当前工序的工艺要求回填已有发布版，避免把今日值冒充历史值；此前发布记录无快照时保持未知。本次真实 Source 转入的路线全部为 DRAFT，正式发布时才冻结当前定义。

### ROUTING_VERSION_RULES / PUBLISH_RULES / REFERENCE_RULES

实际 API 验证：新建 v1 DRAFT → 保存产品/步骤与重排 → 发布；发布后普通编辑、内容写入和删除均拒绝。新版本为同 code 的下一 version、不同 id 的 DRAFT；已有草稿时再建版本拒绝。复制为新 code 的 v1 DRAFT。停用及重新启用保留冻结内容。

发布快照覆盖产品 code/name 和工序 code/name/description；发布后修改 Operation，历史步骤快照保持不变。UI 也显示冻结提示和只读字段。WorkOrder 将来必须引用确切 Routing 行 id 与 version，本轮未增加执行模型、引用占位表或假验证器。

同事务检查当前 Factory 下有效引用，父级先校验 Scope；非法/跨厂工序、物料及缺失资源均拒绝，失败后父 revision、产品及步骤不变。Operation/Material 被草稿或历史路线引用时不能删除。无物理 FK，不承诺绕过 Service 的 SQL 写入受此保护。批量删除最多 100 个对象，同步/子项亦有明确上限。

### ERP_SYNC_STRUCTURE

最终顺序：权限/Factory 入口 → `mes_master_sync` 条件抢占 → QueryPort(scope, maximum) → 查询后条件进度更新/复查 run_token → 主业务 Service 的 businessTransactionManager 事务 → 生成 Mapper → 条件完成更新。

Operation 每批最多 1000 条；Routing 最多 100 条，每条最多 100 产品与 100 步骤。人工维护不依赖 ERP；默认 Adapter 明确 422 / ERP_OPERATION_NOT_CONFIGURED、ERP_ROUTING_NOT_CONFIGURED。接入 Adapter 负责按 FactoryScope 解析真实 ERP 来源，本轮没有 ERP/HR/WMS 数据库路由或第三个 SqlSessionFactory。

真实 MySQL + Spring 测试使用显式 synthetic ERP QueryPort，验证新增、重复跳过、已发布来源变化生成新草稿、历史冻结、真实协调状态，以及同流 RUNNING 时第二个调用不会查询来源；查询期间令牌被替换时旧执行者不进入写入。外部只读查询不在业务写事务内；中心令牌与业务提交不是跨库原子事务，也不承诺 exactly-once。

### CONVERTER_MAPPING / MYSQL_RESULT

Converter：[operation-routing.sql](../../scripts/migration/ktg-mes/operation-routing.sql)。不执行 Source 写入；使用真实 Source 的自有隔离副本和自有转换业务库。物料/单位依赖先转换，步骤通过工序编码重映射本地 ID，序号按旧 order_num 升序规范为 1..N；路线与产品关联全部保留。

| 对象 | Source 数量 | 转换后数量 |
| --- | ---: | ---: |
| Operation | 587 | 587 |
| Routing | 44 | 43 |
| RoutingStep | 539 | 524 |
| RoutingProduct | 47 | 46 |

CONVERSION_REJECT：路线 sourceId=45 因非法步骤引用整条拒绝；步骤 sourceId=565 缺失工序。拒绝路线的全部 15 个步骤及 1 个产品关系不进入 Base，不用删除坏步骤后强行导入来放宽模型。7 条多产品路线保留；序号、工序/产品引用、Factory/version 违规均为 0，空中文 COMMENT 为 0。

开发业务库、独立 SIMPLE_B 业务库与转换库已通过 V3/V4 Flyway；真实 metadata、厂内 unique 并发竞争（一个 200、一个 409）、父子引用、版本及冻结得到验证。转换副本保留于自有 MySQL 验收 Schema，明细和凭证不进入 Git。

### API_RESULT

实际使用用户维护的 5173 → 8081 服务完成 62 个基础检查、14 个范围/引用/删除补充检查、8 个只读身份写入负例，全部通过；另验证唯一键并发及工艺要求冻结。人工 TEST ONLY 数据与真实 Source 转换证据分开。

Operation：创建/编辑/查询/删除、两工厂独立数据、厂内重复编码、非法状态/Factory 伪造、过期 revision、引用删除拒绝、sync 403 与 NOT_CONFIGURED。
Routing：创建、内容保存/重排、草稿编辑、空发布拒绝、发布冻结、复制、下一版本、已有草稿拒绝、非法/跨厂引用、原子回滚、删除限制、停用/恢复、sync 403 与 NOT_CONFIGURED。只读身份对 update/delete/publish/copy/version/sync 均 403。

### UI_RESULT

真实浏览器使用现有 `http://127.0.0.1:5173`，完成工序创建/编辑、查询从第二页回到第一页、20 条分页的第二页、删除取消、两页同步未配置提示；Routing 草稿上移/保存、产品/工序远程候选、发布确认、v1/v2 状态及历史工艺要求只读显示。内部 revision 不进入工序表单，由详情读出的修订值自动携带，保留并发校验。

仅 list/query 的 TEST ONLY 账号验证：Operation 隐藏新增/编辑/删除/sync；Routing 隐藏新增/删除/sync、草稿编辑、publish/copy/version。切换 SIMPLE_B 后原业务页面退回工作台，再进入工序页得到独立的两条 TEST ONLY 记录；无旧工厂数据回填。

实际检查 1280/1440/1920 宽度，截图：
- [草稿 1280](operation-routing-draft-1280.jpg)、[草稿 1440](operation-routing-draft-1440.jpg)。
- [发布版 1440](operation-routing-published-1440.png)、[发布版 1920](operation-routing-published-1920.jpg)。
- [只读草稿](operation-routing-readonly.png)、[只读工序](operation-readonly.png)。
- [路线同步未配置](operation-routing-sync-not-configured.png)、[工序同步未配置](operation-sync-not-configured.png)。

分页和表单测试额外创建的 19 条工序已通过 API 精确清理；跨范围负例、临时草稿和 MySQL synthetic 同步测试只清理自身数据。用户创建的 code=1 路线没有改动。用于回看冻结的 TEST-OR 路线/关联数据保留并明确标 TEST ONLY。

### 运行恢复与用户补充的同数据源工厂检查

初次“资源不存在或不在当前范围”发生于后端尚未加载新增路由；用户重启后 Operation/Routing 列表返回 200。后续旧进程出现业务超时及共享入口 500，未取得异常原因证据；新进程恢复后，无工厂头的发现/上下文、列偏好与只读登录恢复均为 200，重新完成 UI。未将 Factory 数量推定为原因，未为此修改通用基础设施。

用户补充授权尝试在同一数据源绑定新测试工厂。真实中心库 `uk_factory_datasource` 拒绝复用，未创建 TEST_OR_SHARED，未删除约束或改绑现有工厂。当前候选实际为 3 个；本批双工厂验收使用 1 与 SIMPLE_B 的独立业务库。未自行启动/重启用户开发服务；不新增持久开发端口。

### 测试与无效入口清理

删除 10 个重复、仍按已退役模块验证的旧 ScaffoldTest 和不再有生产 Mapper 的旧 acceptance XML；有价值的缓存、同步、HTTP、范围、目录与请求预算断言迁到当前 business.* 入口。没有用删除边界断言掩盖初始编译失败。保留并改造有效的 Operation 缓存/事务合同测试，增加 Routing 生命周期合同、真实 MySQL 同步和 UI 顺序测试。为自己启动服务的 OperationRoutingAcceptanceLauncher 已删除。

测试分级：合同/Fixture、真实 MySQL synthetic port、真实 Source 转换、当前运行服务 API、真实浏览器分别记录；不把 synthetic ERP 当成真实 ERP 接入。

### SOURCE_RULES_REUSED / SOURCE_COMPLEXITY_NOT_REUSED / DEFERRED

本批闭合的是定义、线性顺序、多产品、版本与冻结、范围/引用及可选同步入口。源规则复用与未迁复杂性沿用上文，继续 DEFERRED：WorkOrder/Reporting/WIP 实现、执行引用迁移、资源资格关系、默认路线、分支/并行/返工、质量/BOM/工资、真实 ERP Adapter、生产发布与 Source 写入。

打包启动 smoke 未运行：用户明确要求使用既有服务、不自行启动服务；Maven verify 的打包与测试不能代替独立 packaged smoke。测试使用的自有副本保留供复验；回滚需审查当前业务数据，不能直接 DROP 已使用表或改已执行 Flyway checksum。
