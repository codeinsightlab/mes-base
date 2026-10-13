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
| Publish/Freeze Strategy | 发布至少一个产品/物料和一个步骤，引用必须同厂且启用；发布时冻结产品与工序编码/名称快照。PUBLISHED→DISABLED 保留内容；停用不删除历史；重新启用需独立发布权限 |
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
- routing_step：id、routing_id、sequence、operation_id、operation_code/name（发布快照）、is_key、description。通过父 Routing 继承 Factory，所有入口先检查父级。
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

沿用**当前代码** Material 的 QueryPort → 未配置 fallback / 实际 Adapter → SyncCoordinator → 主 Service 事务入口 → 生成 Mapper。
Prototype 文档的旧 TEST Factory ERP Resolver 已退役，当前不恢复 TEST-only Resolver 或第三个数据源。
QueryPort 接收 FactoryScope，由接入 Adapter 明确解析该厂 ERP 配置；本轮默认返回 ERP_SYNC_NOT_CONFIGURED。
同步和人工维护均可用；不要求 ERP 必配。操作权限用用户要求的 operation:*/routing:*，publish/copy 独立。
外部查询在业务写事务外；中心同步令牌与业务提交不宣称原子。执行权在批次边界校验，已发出的数据库副作用不能被中心令牌自动撤销。

## GENERATED_PART / EXTRA_CODE

Codegen 配置使用当前用户 YAML 的参数副本，仅选本批表、write/fail，不修改全量用户配置。
原始生成结果与报告保留在自有 target 中；后处理范围和不能直接重生成的生命周期文件在验收章节记录。
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
