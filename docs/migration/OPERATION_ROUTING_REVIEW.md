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

最终同步顺序调整经用户编译重启后，再完成 4 个运行服务检查：Operation/Routing 同流 RUNNING 均在来源查询前返回 409 / SYNC_RUNNING；未配置来源均返回各自专用 422，真实中心同步状态变为 FAILED，无遗留 RUNNING。测试状态按精确 run_token 清理。

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

最终同步修改经用户再次编译重启后，4 个同步检查通过，但随后两厂的新列表查询、详情和 `/api/foundation/context` 又出现 `500 / INTERNAL_ERROR`。只读进程检查确认：8081 进程 13:03:42 启动，运行的 `target/mes-base-0.1.0-SNAPSHOT.jar` 在 13:18:21 被后续构建改写，且进程仍打开多个构建时期的 JAR 文件。运行中覆盖 JAR 可能影响延迟类加载；尚未取得当前异常栈，不把这一推断写为已确认根因。当前运行服务的最终读接口复验待恢复，先前 API/UI 通过记录保留其发生时点。

用户确认自行重启并恢复后，再通过现有 5173 → 8081 完成 15 项最终读取检查：Factory 1 / SIMPLE_B 的无缓存新查询、列表、存在记录的详情以及带工厂头和无工厂头的发现/上下文全部通过。真实浏览器重新打开已发布 v2，冻结提示、历史工艺要求和只读字段正常，见 [恢复后的路线详情](operation-routing-restored.png)。本次运行异常已恢复并闭合复验；没有取得原异常栈，覆盖运行 JAR 仍记录为可能原因，不宣称已证明根因。此次仅更新证据，不重建 JAR 或重启服务。

用户补充授权尝试在同一数据源绑定新测试工厂。真实中心库 `uk_factory_datasource` 拒绝复用，未创建 TEST_OR_SHARED，未删除约束或改绑现有工厂。当前候选实际为 3 个；本批双工厂验收使用 1 与 SIMPLE_B 的独立业务库。未自行启动/重启用户开发服务；不新增持久开发端口。

### 测试与无效入口清理

删除 10 个重复、仍按已退役模块验证的旧 ScaffoldTest 和不再有生产 Mapper 的旧 acceptance XML；有价值的缓存、同步、HTTP、范围、目录与请求预算断言迁到当前 business.* 入口。没有用删除边界断言掩盖初始编译失败。保留并改造有效的 Operation 缓存/事务合同测试，增加 Routing 生命周期合同、真实 MySQL 同步和 UI 顺序测试。为自己启动服务的 OperationRoutingAcceptanceLauncher 已删除。

测试分级：合同/Fixture、真实 MySQL synthetic port、真实 Source 转换、当前运行服务 API、真实浏览器分别记录；不把 synthetic ERP 当成真实 ERP 接入。

### SOURCE_RULES_REUSED / SOURCE_COMPLEXITY_NOT_REUSED / DEFERRED

本批闭合的是定义、线性顺序、多产品、版本与冻结、范围/引用及可选同步入口。源规则复用与未迁复杂性沿用上文，继续 DEFERRED：WorkOrder/Reporting/WIP 实现、执行引用迁移、资源资格关系、默认路线、分支/并行/返工、质量/BOM/工资、真实 ERP Adapter、生产发布与 Source 写入。

打包启动 smoke 未运行：用户明确要求使用既有服务、不自行启动服务；Maven verify 的打包与测试不能代替独立 packaged smoke。测试使用的自有副本保留供复验；回滚需审查当前业务数据，不能直接 DROP 已使用表或改已执行 Flyway checksum。

### 检查回执

- `mvn -B -ntp -Dmes.test.redis=true -Dmes.test.operation.mysql=true verify`：BUILD SUCCESS，376 条，372 通过、4 个其他 MySQL opt-in 检查未开启而跳过，0 failure/error；本批 OperationRoutingMySqlTest 实际执行通过。
- Node 22：`npm test` 160 通过；`npm run build` 通过；本批 API/页面/顺序规则/request 与相关测试的 eslint 通过。既有大 chunk 警告保留。
- IDEA formatter 最终校验 74 个本批 Java 文件及双仓库 `git diff --check` 通过；已被任务外库存功能继续修改的 ErrorCode 不纳入本次最终格式检查，不格式化并行工作。
- 测试账号及只读角色已停用，浏览器恢复管理员和原 Factory 1；分页/范围负例已精确清理。
- 汇总证据：[OPERATION_ROUTING_EVIDENCE.json](OPERATION_ROUTING_EVIDENCE.json)。未经本 Agent 提交/推送/部署；任务中 HEAD 有外部提交变化，既有及并行改动均保留。

## 2026-10-13 第二批：Routing Definition Graph（完成本地验收）

### CURRENT_MODEL / TARGET_MODEL / KEEP / ADAPT / REMOVE

当前一条 `routing` 行已代表 `(factory_id, code, version)` 的明确版本；继续 KEEP，不增加 `routing_version`。KEEP Operation、RoutingProduct、DRAFT/PUBLISHED/DISABLED、独立操作权限、当前 Factory、revision、同步抢占及发布快照。ADAPT Routing 内容、发布、复制、新版本、同步和 Converter 为 Node + Edge。REMOVE 正式 Step Entity/Example/Mapper/XML/VO/内容契约与 UI 上下移执行关系；`ExternalRoutingStep` 仅作为明确要求保留的 LINEAR 来源输入，转换后不持久化 Step。

目标为 `operation + routing + routing_product + routing_node + routing_edge`。同一 Operation 可出现于不同节点，节点唯一键是 `(routing_id,node_code)`，边唯一键是 `(routing_id,from_node_id,to_node_id)`，两项按本轮用户明确要求建立；不建物理外键。节点编码使用大小写敏感的字母/数字/下划线/短横线；`display_order` 是 UI 提示。保存保留相同 node_code 的节点 ID，复制/新版本分配新 ID 并重映射边，客户端不能提交其他路线的节点主键来改绑定。

### ROUTING_STEP_MIGRATION / GRAPH_RULES

V3/V4 不修改。新增 V9 创建 Node/Edge，将旧步骤 ID、引用和全部快照原样复制，以旧 sequence 相邻关系生成边。开发库 8 步 → 8 节点/4 边，SIMPLE_B 0 → 0/0，Source 转换副本 524 步 → 524 节点/481 边；快照差异、非法边均 0。已备份旧路线相关数据；V9 阶段保留旧表，直到 Graph API/UI 验证完成。

Graph 验收后执行 V10：旧步骤与节点引用/快照不一致时明确失败，防止 V9 后旧服务继续写入造成静默丢失。四个验收 Schema 均到 V10，旧表及临时校验过程均为 0，Node/Edge 数量未变，Flyway validate 通过。正式模型不再保留 routing_step。旧 Step 生成配置同步删除，当前配置为 `docs/development/routing-graph-codegen.yaml`，生成完整后端到审查副本，仅 8 个 Entity/Example/Mapper/XML 持久化文件发布到业务模块；没有发布 Node/Edge 技术 CRUD。通用用户配置未修改。

每版本上限 100 产品、100 节点、400 边；无自环、重复边、跨路线端点或环。沿用最多一个关键点，允许重复 Operation 与多个入口/出口。草稿允许暂时孤立节点，发布须至少一个产品和节点；多节点发布拒绝孤立节点，单节点合法。不引入只能单入口/出口的约束。服务端一次父级 CAS 事务完成校验、节点/边和产品保存，错误回滚修订号及所有内容。发布冻结产品、节点、边及工序快照；新版本和复制保留完整拓扑。

### ERP_SYNC_GRAPH / SOURCE_CONVERTER

ExternalRouting 明确二选一：LINEAR steps 或 GRAPH nodes/edges，禁止混合输入。LINEAR 按来源序号排序生成相邻边，允许 10/20 等非连续来源序号；GRAPH 保留明确连接。仍使用现有协调器与独立业务写事务，已发布来源变化只创建新草稿，不重写发布图。未配置来源继续明确失败。

Converter 将每条 Source 路线独立转为节点与相邻边，不按名称合并路线。同一来源重复工序不再自动拒绝，来源排序歧义仍拒绝。当前冻结 Source 副本未找到可确认的同产品共同前后段候选。用户本轮补充“你来造数据吧，我也提供不了”，历史 workaround 验收调整为明确 SYNTHETIC：两条线性路径 `N10→N20→N30A→N40`、`N10→N20→N30B→N40` 由人工明确映射到一张分支汇合图，不宣称是真实历史案例。真实旧路线若需合并仍为 `MANUAL_GRAPH_MAPPING_REQUIRED`，Source 不写入。

### LINEAR_RESULT / BRANCH_RESULT / MERGE_RESULT / VERSION_BEHAVIOR / PUBLISH_FREEZE

正式 MySQL/Spring Service 与真实 API Fixture 均通过：LINEAR 为 4 节点/3 边，BRANCH 为 4 节点/3 边，BRANCH+MERGE 为 5 节点/5 边。重复工序节点合法；修改显示排序不改变拓扑且保留原节点 ID。复制及新版本生成新节点 ID，按 node_code 重映射全部边；原发布图及快照不变，下一版本可修改草稿。数据库 Node/Edge 唯一性、CAS 并发一成功一 409、非法图全量回滚、跨路线物理边拒绝、孤立发布拒绝和单节点合法均得到验证。多入口/多出口规则由纯规则测试覆盖，不把它当作执行调度证明。

### MYSQL_RESULT / API_RESULT / UI_RESULT

V9/V10 真实 MySQL 迁移通过；新建隔离转换库执行新版 Converter，得到 587 Operation、43 Routing、524 Node、481 Edge、46 Product，原坏路线 45 及坏来源步骤 565 继续拒绝。无非法图引用或空表/列中文 COMMENT。

用户编译重启后，现有 5173 → 8081 完成 80 项 Graph API 检查，全部通过；另有并发写入 [200,409]。退役后再完成两厂及浏览器创建图的 8 项读取复验。测试 Session 过期时重新登录，不修改会话准入规则。

真实浏览器创建 `TEST-GRAPH-UI-20261013`：5 个节点、5 条连接；测试工序在不同节点复用。实际操作节点新增/选择/删除（关联边同步移除）、连接新增/删除、成环拒绝、保存后重新读取、发布只读和创建 v2 完整拓扑。发布版本 ID=23，新版本 ID=24；V10 后重新打开发布图正常。真实读取 LINEAR/BRANCH/MERGE，反向 display_order 的线性图仍按 Edge 显示正确依赖。

1280/1440/1920 图预览均可读；保持统一 Shell/抽屉、白底 SVG，不引入图框架或技术菜单。验收后恢复默认 viewport、管理员和 Factory 1。截图：[1280 草稿](routing-graph-draft-1280.png)、[1440 草稿](routing-graph-draft-1440.png)、[1920 草稿](routing-graph-draft-1920.png)、[已发布汇合图](routing-graph-published-1440.png)、[v2 草稿](routing-graph-version2-1440.png)、[线性](routing-graph-linear-1440.png)、[分支](routing-graph-branch-1440.png)。

最终视觉复核将长图从整体缩小调整为保留节点尺寸、横纵滚动，避免长路线文字随 viewBox 缩成不可读小字；调整后再次通过 171 个前端测试、build、eslint 并真实复查三档宽度：[最终 1280](routing-graph-final-1280.png)、[最终 1440](routing-graph-final-1440.png)、[最终 1920](routing-graph-final-1920.png)。本次浏览器实际图为 5 节点/5 边，不把未实际创建的 100 节点长图写为浏览器验收。

### 本批检查回执

- `mvn -B -ntp -Dmes.test.redis=true -Dmes.test.operation.mysql=true verify`：387 条，382 通过、5 个其他 MySQL opt-in 检查跳过，0 failure/error，BUILD SUCCESS。
- V10 后 `mvn -B -ntp -Dmes.test.operation.mysql=true -Dtest=OperationRoutingMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test` 再次通过，证明图生命周期及 synthetic ERP 写入不依赖旧表；没有重新打包或重启运行服务。
- Node 22：171 个前端测试通过，build 与本批 eslint 通过；24 个 Java 文件 IDEA format/check 与双仓库 diff 检查通过。
- 汇总：[ROUTING_GRAPH_EVIDENCE.json](ROUTING_GRAPH_EVIDENCE.json)；用户授权构造案例：[ROUTING_GRAPH_SYNTHETIC_MAPPING.json](ROUTING_GRAPH_SYNTHETIC_MAPPING.json)。负例临时草稿及 B 厂临时工序精确清理，明确 TEST ONLY 的发布图/版本保留供回看；未更改用户的原路线或 Source。
- 旧表退役后不能直接运行旧 Step 程序；回退需停写、核对本批新 Graph 数据并按迁移前备份制定恢复方案，不能把分支强行按显示序号压成线性路线。未提交、推送或部署，保留任务外并行修改；生产与真实 ERP Adapter 不在本次验收范围。

### DEFERRED_EXECUTION_SEMANTICS

WorkOrder 执行、ProductionExecution、并行调度、AND/OR/XOR Join、条件、动态分支、返工、循环、运行 Token，以及节点工作站/设备资格均不实施。未来 WorkOrder 直接引用确切 `routing.id`，不用另加 RoutingVersion 表；人员直接引用 sys_user，沿用当前账号授权规则。


## 2026-10-13 验收库位置纠正（等待目标确认）

用户明确要求真实验收及本次转换结果使用已经配置的验收库。此前另建 `mes_base_graph_conversion_20261013` / `mes_base_oproute_conversion_20261013` 完成转换，未绑定到配置工厂，导致现有页面无法复验转换数据；这是本次验收位置选择错误，不将隔离转换库称为用户配置验收环境。AGENTS 第 10 节新增此项约束。

当前中心配置实际有两个同名 `mes_base_dev_business`：工厂 1（老厂）对应 192.168.18.25:3306；工厂 DEV（开发工厂）对应 110.42.227.83:3306。工厂 2（公司测试环境）未绑定数据源。已发起目标确认，确认前不写入任一候选库。

对工厂 1 的只读预检查：与转换批次的工序编码冲突 0、路线编码及版本冲突 0；缺少 45 个被路线引用的产品及两个来源计量单位。落库必须补齐这些依赖、按编码映射目标主键并保留已有数据；禁止原样复制子表 ID。未修改业务代码，尚未将转换结果写入配置验收库。


## 2026-10-13 已配置验收库落库完成

用户明确指定 `192.168.18.25:3306/mes_base_dev_business_simple_b`，绑定工厂 `SIMPLE_B`，作为专用验收业务库；此决定取代上一节待确认状态。中心配置绑定已核对，所需七张业务表均存在，无需建表或修改 Flyway。AGENTS 第 10 节已写入明确目标及缺表时采用正式迁移的约束。

从已完成 DAG 转换的隔离结果库导入本次源系统转换数据，使用单事务、编码/版本冲突校验、提交前数量断言，并按目标库新主键映射工序、路线、节点、边和产品。写入 587 条工序、43 个版本一草稿路线、524 个节点、481 条边、46 条产品关联；补齐 45 个产品及 2 个来源计量单位。未覆盖已有记录；导入前七表一致性备份和执行 SQL 保存在忽略的 `.local/routing-graph-20261013`。旧隔离转换库暂保留，不擅自删除。

真实 MySQL 复核：工序字段差异 0、节点字段及工序映射差异 0、边映射缺失 0。经现有 5173 服务登录并携带 `X-Factory-Id: SIMPLE_B` 读取，工序总数 589（原有 2 + 导入 587）、路线总数 43；43 个路线详情的节点、边及产品数量逐条核对通过，状态均为 DRAFT。工厂 1 路线总数仍为 17，隔离结果未改变。源系统坏路线仍按原转换拒绝清单排除，不伪造修复。

本次未修改业务代码、未构建或启停服务。新一轮浏览器控制获取状态超时，因此本次落库后的浏览器切厂/页面显示未完成验证，不能将 API 成功当作 UI 通过；用户在现有页面选择“简单业务验收 B”即可读取这批数据。证据见 [验收库落库结果](ROUTING_GRAPH_ACCEPTANCE_DB_RESULT.json)。旧阶段的测试、API 和截图证据保留历史时间线，不冒充本次全部重跑。
