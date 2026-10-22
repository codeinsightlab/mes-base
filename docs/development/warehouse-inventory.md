# 仓库与库存

## 2026-10-21 当前入库入口

日常入库统一使用“出入库单”中的入库单，支持普通物料及带执行单元来源的生产产出。草稿不改变库存，确认过账在同一事务写库存动作及余额。原料 receipt 直写不再接受新请求，旧交接/接收仅保留历史查询；生产结束由数量闭合及质量处置判定，不等待入库。Factory仍按独立业务库隔离、业务表无factory_id；下文2026-10-13为历史设计记录，不能作为当前Schema或新入口依据。当前实现及验证见[统一入库记录](../reviews/2026-10-17-local-stock-documents-v22-report.md)。

2026-10-13。本轮为两个 SIMPLE 业务，提供完整 CRUD 和可替换查询 Port 的同步壳子。

## 模型与契约

- `wm_warehouse`：工厂、编码、名称、启停状态、备注与统一审计字段；唯一键 `(factory_id, code)`。
- `wm_inventory`：工厂、正式 `material` 引用、仓库引用、`DECIMAL(18,4)` 库存数量与可空可用数量、状态、备注和审计字段；唯一键 `(factory_id, material_id, warehouse_id)`。
- 状态为 `ENABLED / DISABLED`。可用数量空值表示未提供，不推导冻结或在途库存。工厂与审计字段均由服务端赋值，客户端不能指定主体或工厂。
- 中文表与字段数据库 COMMENT；无物理外键，业务服务校验逻辑引用与同厂归属。按现有分库路由，同时保留真实 Schema 的工厂列限制。
- Warehouse、Material 删除与库存引用写入共享数据库行锁；引用仓库或物料不能删除。复用生成 Mapper/XML/Example，不增加单表 ExtraMapper 或第二套 SQL。

## 用户入口与权限

动态菜单 `物料库存 → 仓库管理 / 库存管理`。目录路径 `stock`，页面分别为 `wm/wmWarehouse/index` 和 `wm/wmInventory/index`。

| 对象 | HTTP | 权限前缀 |
| --- | --- | --- |
| 仓库 | `/api/wm/wm-warehouse` | `business:wm:warehouse` |
| 库存 | `/api/wm/wm-inventory` | `business:wm:inventory` |

各前缀具有独立 `list/query/create/update/delete/sync`。DELETE 请求体为 ID 数组，POST `actions/sync` 使用独立同步权限。生成菜单 SQL 不替角色赋权。

库存页面复用 CrudPage，物料/仓库通过有界分页搜索选择器选择；字段 ID 和数量保留字符串精度。库存列表在 Service 内按页批量查询物料与仓库名称，由 Controller 显式投影，避免 N+1。切厂或撤权清理候选并丢弃迟到响应。

## 生成与手工规则边界

配置：`warehouse-inventory-codegen.yaml`；入口 `scripts/mes-business-codegen`。
完整基础后端、前端、菜单、权限与同步脚手架来自真实 MySQL metadata。库存生成时以 `material_id` 生成基础脚手架，再按本轮允许的 Sync Upsert 扩展改为物料＋仓库复合键。

`EXTRA_CODE_REASON` 记录于 StockReferenceRules、库存列表 Query Item、两 Service 同步/唯一错误边界和前端引用入口。手工修改的生成文件已经移除 MES-CODEGEN 标记；再次生成遇到 OUTPUT_CONFLICT 必须保留手工实现，不恢复标记强行覆盖。未改动生成文件保持生成归属。

仅修改通用同步模板的默认错误：从物料专用 `ERP_SYNC_NOT_CONFIGURED` 改为 `SYNC_QUERY_NOT_IMPLEMENTED`，同步更新模板测试与生成契约验收；其他既有业务 Adapter 未在本轮重生成。

## ERP_SYNC

`WmWarehouseQueryPort` / `WmInventoryQueryPort` 为外部数据入口，默认未接入 Adapter 明确返回 `422 / SYNC_QUERY_NOT_IMPLEMENTED`。
查询先在本地事务外完成。具体 ERP Adapter 应将物料和仓库来源引用解析为当前 Factory 的本地主键；核心表不增加 ERP 专属字段。没有真实 ERP 连接或真实 ERP 数据验收。

仓库按工厂＋编码 upsert；库存按工厂＋物料＋仓库 upsert。同步保留新增、更新、跳过计数，数值比较忽略 DECIMAL 尾随零；批内重复键拒绝，非法引用整批回滚。复用 SyncCoordinator 与中心库 `mes_master_sync`，独立同步键及 run_token；处理边界与事务提交前验证执行权，失权后停止并回滚本轮写入。

## 迁移与部署

业务 Flyway 新增 `db/business/V8__warehouse_inventory.sql`。菜单新增 `db/system/V26__warehouse_inventory_menu.sql`，由生成 add-or-update SQL 派生，保留菜单 ID/角色关联并明确仓库在前。
实际执行记录、中心库旧迁移不一致与未完成验收见 [本轮验收](../reviews/2026-10-13-warehouse-inventory-review.md)。中心库旧迁移失败时不能把菜单 SQL 的直接执行写成 V26 Flyway 已执行。

本轮不提交、不推送、不部署。回退业务代码不会自动回退真实库；两表包含业务数据后不得直接删表，菜单授权及历史迁移须分别审查。

## 延后范围

`WAREHOUSE_LOCATION = DEFERRED`，`BATCH_INVENTORY = DEFERRED`。未建立库位、批次、流水、库存扣减、冻结、预占、在途、成本、入出库或 WMS。

## 2026-10-13 登录后续验

主业务库与 SIMPLE_B 独立业务库均已实际迁移 V8；B 库经用户明确授权、target=8，仅执行本轮迁移。
桌面真实页面的菜单、查询、新增、编辑、引用选择、Decimal 精度、重复键及默认同步失败已通过。
用户明确要求保留 Warehouse／Inventory ID 9，实际删除未执行。受限账号权限隐藏和非法引用负例的证据仍来自组件／HTTP fixture；390px 全局壳问题及中心库历史核对边界见续验报告。

字段协议新增可选 `columnMinWidth`，两数量列 220px、引用名称列 260px。安全请求错误白名单补齐本轮业务错误，不输出数据库原始错误消息。

## 2026-10-22 仓库类型主数据能力

### 范围与编码

仓库类型只表达业务用途，独立于 LOCAL/EXTERNAL 来源、库存质量状态和所有权。本轮没有增加或改变库存操作准入、质量处置、报废核销、返工、委外对账、工单或 Execution 流转规则。

| 持久化/API 编码 | 中文显示 | 语义 |
| --- | --- | --- |
| RAW_MATERIAL | 原材料仓 | 采购原材料存放用途 |
| WIP | 在制品仓 / 半成品仓 | 尚未完成全部工序的在制品或半成品 |
| FINISHED_GOODS | 成品仓 | 已达到成品入库条件的产品 |
| INSPECTION | 待检仓 | 尚未完成质量判定的物料 |
| DEFECTIVE | 不良品仓 | 已确认质量异常、等待处置的物料 |
| SCRAP | 报废品仓 | 已决定报废、尚待核销或实物清理的物料 |
| OUTSOURCING | 委外仓 | 存放供应商处且仍需企业管理的物料用途 |

采用既有业务 enum 模板 `WarehouseType`：显式 value/description、from、LinkedHashMap toMap。没有增加可维护字典、额外用途字段或第二套后端类型体系；前端选择项与后端枚举编码一致。

### 数据库迁移、存量和恢复

正式迁移 `mes-admin/src/main/resources/db/business/V48__warehouse_type.sql` 新增 `wm_warehouse.warehouse_type VARCHAR(32) NULL DEFAULT NULL`（含中文 COMMENT）及普通索引 `idx_wm_warehouse_type`。Flyway 记录防止重复部署执行；未改写既有迁移。

迁移前核实中心 `mf_factory → mf_datasource` 的启用状态、host/port/db_name，备份仓库表和业务 Flyway 历史后，通过 Flyway 执行并 validate：

| 开发工厂 | 核实的业务数据库（192.168.18.25:3306） | 版本 | 迁移前/后仓库数 | 待补录 |
| --- | --- | --- | --- | --- |
| SIMPLE_B | mes_base_dev_business_simple_b | 47 → 48 | 7 / 7 | 7 |
| TEST_DEV_PERF_20261019（当前浏览器工厂） | mes_base_dev_perf_test | 47 → 48 | 8 / 8 | 8 |

没有根据名称中的 RAW/FG 等词推测类型，没有修改或删除原仓库、库存、库存动作、业务单据。以上只代表这两个已核实的开发数据库；未连接或修改生产库，未将其他工厂视作已升级。

空值表示“待补录”。现有仓库可读取；本地仓库在下一次普通编辑保存时必须选择七种合法类型之一，新建也必须选择。数据库保持可空，不在此次部署强制全量补齐。由业务负责人逐项确认用途，通过现有权限下的正式编辑入口补录；确认所有写入来源（含将来 ERP 映射）均能提供可信类型、且存量无空值后，再另立正式迁移评估 NOT NULL，不能现在猜默认值。

EXTERNAL 仓库继续遵守原有“仅同步维护、普通编辑拒绝”规则。当前外部同步契约没有可信类型字段，因此同步新建的 EXTERNAL 仓库类型为空；不会猜类型或开放普通编辑绕过来源保护。外部类型映射/维护入口需要单独确认；当前两个验收库的原仓库均为 LOCAL。

备份及证据在忽略目录 `.local/warehouse-type-20261022/`：`warehouse-before.sql`、`perf-warehouse-before.sql`、`migration.log`、`perf-migration.log`、`migration-result.txt`、`perf-migration-result.txt`。备份包含原数据，仅保留本地，不纳入 Git。

恢复优先回退应用并保留新增可空列及索引，不删类型数据、不倒退/伪造 Flyway 历史。MySQL DDL 不保证整个迁移事务回滚；若异常中断，先检查列、索引、迁移历史和原始错误，再按实际落点修复后执行正式 Flyway validate/migrate，不盲目重放 ALTER 或清空数据库。需要物理撤销时另建向前迁移，并事先导出已维护类型；不自动执行删除列。

### 后端与 API 契约

- Entity/Example/Mapper XML 从 V48 后的真实 JDBC metadata，通过 `mes-crud-codegen` 生成隔离审查副本后合入字段变更；保留原有加锁查询，没有新增 ExtraMapper 或数据访问体系。修改后的生成文件已移除生成归属标记。
- CreateCommand、UpdateCommand 增加必填 `warehouseType`；HTTP 校验非空、最大 32 字符，Service 通过 WarehouseType.from 拒绝非法编码（BAD_REQUEST / HTTP 400）。空白、中文名称、大小写变形及未知编码均拒绝。
- QueryDto 增加可选 `warehouseType`，Service 校验后使用生成 Criteria 精确筛选，不拼接用户 SQL。查询 DTO 仍保留分页、来源等全部过滤条件，类型参与既有缓存 queryHash。
- 详情及分页行增加 `warehouseType` 和派生 `warehouseTypeLabel`；空值标签为“待补录”。Controller 仍通过 BeanConversions.convert / convertPage 执行响应白名单转换，Page 查询模型和 total/offset/limit 不变；Service 不生成 HTTP VO。
- 原 URL `/api/wm/wm-warehouse`、`/{id}`、权限 `business:wm:warehouse:{list,query,create,update,delete}`、具名业务事务、Factory 分库、可信来源和审计字段机制不变。新增/修改继续按原规则提交后淘汰列表/详情缓存。
- 这是写请求契约的显式扩展：旧调用方必须同时增加合法 warehouseType；不会静默默认原材料仓。没有增加全局响应拦截机制。

示例创建/编辑主体：`{"code":"WH-01","name":"仓库一","warehouseType":"RAW_MATERIAL","status":"ENABLED","remark":null}`。

示例查询：`GET /api/wm/wm-warehouse?warehouseType=FINISHED_GOODS&offset=0&limit=20`；行返回 `warehouseType="FINISHED_GOODS"`、`warehouseTypeLabel="成品仓"`。历史行编码为 null（具体 null 输出沿用应用序列化策略），标签明确返回“待补录”。

### 前端与布局

`mes-base-ui/src/api/wm/wmWarehouse.ts` 增加联合类型、固定选项及显示函数；`src/views/wm/wmWarehouse/index.vue` 复用 CrudPage，增加类型筛选、列表列和必填选择项。详情由原编辑加载 detail 接口回显，未另造详情页面。没有默认选项，也不允许自定义输入类型字符串。错误、保存/loading、切厂/撤权及迟到响应处理继续复用原组件和 request。

编码为 null 显示“待补录”；字段完全未返回显示“类型信息未返回”，避免把旧后端或错误契约伪装成已确认空值；未知编码显示“未知类型”。中文仅作标签，不参与业务规则判断。

真实 Chrome 的 1802px 常规宽屏及 390×844 窄屏已检查：类型选项完整、弹窗自适应、必填标记可见、列表保持表内横向滚动，取消未保存表单未触发写入。截图：`.local/warehouse-type-20261022/form-wide.png`、`form-narrow.png`、`list-narrow.png`。浏览器尺寸已恢复。此处是页面展示检查，不代表运行中旧后端已升级。

### 验证结果与边界

- 单元：WarehouseTypeTest 验证七编码、from 未知值、顺序/独立 Map、响应转换精度和标签；另验证不同类型筛选进入不同缓存 key，分页元数据保持。
- H2 数据库/API 集成：WarehouseTypeHttpTest 使用真实 Controller/Service/MyBatis SQL/Spring 事务与两个独立业务库，覆盖七类型创建及筛选、详情标签、类型编辑、非法/缺失/null/空白拒绝、历史待补录和同本地主键的跨厂读写隔离；类型变更前后库存余额和历史明细不变，未追加库存动作。
- 原仓库/库存回归：StockHttpTest、LocalWarehouseHttpTest、WmWarehouseScaffoldTest 通过，保留来源限制、库存闭环、引用及事务边界。测试夹具补齐 V48，仓库夹具补装 PageInterceptor，使分页 total 在测试中真实可验证。
- 真实开发 MySQL：显式启用的 WarehouseTypeMySqlTest 核对 SIMPLE_B 绑定、V48 记录、字段 metadata，七类型经真实生成 Mapper 写入/读取/筛选/修改并在 finally 回滚；无测试仓库残留，库存汇总及历史明细汇总保持。该测试验证持久化，不替代运行服务 API 或真实多厂 MySQL 验收。
- 最终定向后端回归 22 项（mes-business 5 项、mes-admin 17 项，含显式启用的真实 MySQL 1 项）全部通过，0 跳过；覆盖新增缓存测试后的最终源码。
- 前端行为：`tests/warehouseType.test.ts` 覆盖中文选项、无默认值、新建/编辑必填、详情回显、类型编码提交、筛选、切厂清理、ERP只读和缺失字段区分。
- 全量后端 `mvn -B -ntp verify` 成功；日志中的四个测试模块合计 842 项，其中 49 项环境门控跳过，0 失败/错误（此轮全量统计含最初 1 项 WarehouseTypeTest，随后增加的缓存单测另经定向验证）。未启用的真实 MySQL/Redis等环境验收不算通过。
- 前端全量 74 个文件 / 429 项测试通过，`npm run build` 通过，改动的 API/页面/测试 ESLint 通过；构建保留原有大 chunk 提示。本轮没有修改依赖。
- 证据：本地 `generator-build.log`、`codegen-report.json`、`verify.log`、`final-focused-tests.log`、`mysql-test.log`、`cache-unit-test.log`、`ui-all-tests.log`、`ui-build.log`、`ui-lint.log`；测试源码位于 mes-business 的 WarehouseTypeTest、mes-admin 的 WarehouseTypeHttpTest/WarehouseTypeMySqlTest 和 UI tests/warehouseType.test.ts。

初次交付时的运行态限制（已由下方重启续验关闭）：当时已打开的页面连接旧后端，类型字段尚未返回；没有自行启动/停止/重启用户服务。已请求用户重启后端，真实页面新增/编辑持久化、详情及筛选仍待重启后续验。两个开发库迁移已完成，生产与其他工厂部署未执行，真实多厂 MySQL 与生产业务验收未执行。

### 业务影响及部署注意

源码检索表明新类型仅用于仓库主数据写入/查询/展示，没有被领料、退料、生产入库、调拨、盘点、质量/报废、委外或工单/Execution 流转读取为准入条件。已有库存和历史单据只引用仓库身份，不会因类型编辑被改写。现有质量处置去向和来源准入继续原规则；不能把 DEFECTIVE/SCRAP/OUTSOURCING/INSPECTION 的标签当作完整业务闭环。

部署顺序：核实每个实际目标的 Factory 绑定及非生产范围 → 备份 → 正式 Flyway V48/validate → 同步发布后端与前端 → 用户重启后端 → 按权限在正确工厂验收 → 人工确认补录。新后端在未升级业务库上会查询缺失列，不能先发布后端后补迁移。运行中的旧接口可能忽略新筛选参数，必须重启后按真实返回验证，不能仅看页面有下拉框即判通过。

本 Agent 未执行 Git 提交或推送。工作期间检测到其他操作推进 HEAD 至 `147585a`，包含部分本任务文件；未回退该提交，也未清理无关工作区修改。

本次完成的是仓库类型主数据能力；不代表不良品处置、报废核销、委外或质量检验业务闭环已完成。


### 2026-10-22 用户重启后真实页面续验

用户确认“已重启”后，在现有 Chrome `http://localhost:5173/stock/wmWarehouse` 验证；当前工厂为 `TEST_DEV_PERF_20261019`，重新核对中心绑定到 `192.168.18.25:3306/mes_base_dev_perf_test`。未自行操作任何服务生命周期，未访问生产数据库。

真实页面及数据库结果：

1. 刷新并恢复原登录身份后，8 条原仓库均显示“待补录”，旧的“类型信息未返回”提示消失。
2. 新增独立测试仓库 `TEST_TYPE_UI_20261022` / `TEST ONLY 仓库类型验收`，填写编码及名称后不选类型点击保存，表单保持打开，记录未创建；选“原材料仓”后保存成功。真实数据库新记录 ID=9、warehouse_type=RAW_MATERIAL，列表正确显示中文。
3. 打开该测试行编辑，通过真实详情读取回显“原材料仓”；修改为“报废品仓”并停用，保存成功。再次打开详情回显“报废品仓”与“停用”，数据库为 SCRAP / DISABLED。
4. 真实页面按“报废品仓”筛选返回 1 条（仅测试行），按“原材料仓”筛选返回 0 条，验证修改后的筛选与列表刷新；最后重置筛选，退出编辑表单。
5. 前后核对 8 条原仓库 id/code/name/warehouse_type/status 一致；库存 2216 行、quantity 与 available_quantity 汇总均为 90973.920000；历史单据明细 1111 行、quantity 汇总 202446.000000；库存动作 4030 条，均保持原值。测试仓库无库存行。本次没有创建库存或业务单据。

测试记录明确停用留存（没有删除任何仓库）：当前该开发库仓库共 9 条，其中原有 8 条仍待补录；SIMPLE_B 原有 7 条仍待补录。测试行只用于本轮页面验收，不代表真实仓库的业务分类。

证据在 `.local/warehouse-type-20261022/`：`runtime-before.txt`、`runtime-after.txt`（程序逐字比对通过）、`runtime-test-row.txt`、`runtime-scrap-filter.png`、`runtime-raw-filter-empty.png`。页面真实新增、修改、详情及筛选的待重启验收项已完成。本次续验只更新本文证据，未修改业务代码；七类型组合、非法 API 请求和多厂隔离仍分别依赖此前明确标注的单元/H2 API 测试及真实 MySQL 持久化测试，不冒充本次真实页面全部逐项执行。

### 2026-10-22 创建人、修改人显示姓名

用户指出仓库页面审计列不应直接显示用户 ID。本轮保留持久化 create_by/update_by 及原 API createBy/updateBy 审计身份，新增 createByName/updateByName，列表与详情通过既有 BusinessDirectoryService.userLabels 解析姓名（无昵称时使用账号名称，用户不存在显示“未知用户”）。前端审计列改为绑定姓名字段，不再直接显示数字 ID。

WarehouseQueryService 复用原仓库详情/分页及其缓存，收集当前页去重后的创建人与修改人 ID，一次批量解析（100行最多200个用户）；中心用户读取继续沿用用户目录既有的独立只读窗口和规范缓存，不跨库 JOIN，不在 Controller 查询用户，不进行逐行 I/O。Controller 使用 BeanConversions.convertWith/convertPage 补充显式姓名关联，分页模型、权限及 total/offset/limit 不变。新增/修改写响应仍沿用原审计 ID，写成功后的列表刷新提供姓名；没有因姓名展示改变审计事实或添加数据库迁移。

验证：WarehouseAuditNamesTest 的批量去重、姓名/账号回退、用户缺失及审计 ID 保持测试通过；WarehouseTypeHttpTest 增加真实 H2 API 列表和详情的姓名断言。相关后端定向 17 项通过；前端 warehouseType.test.ts 共6项通过，新增审计姓名列绑定及姓名显示断言；前端构建及改动文件 ESLint 通过。日志位于 `.local/warehouse-type-20261022/audit-name-tests.log`、`audit-name-ui-tests.log`、`audit-name-ui-build.log`、`audit-name-lint.log`。

本轮未调整数据库或已有仓库记录。已请求用户重启后端，姓名的真实页面显示待重启续验；此前仓库类型的真实页面验收结果不受影响。回退本轮姓名投影和页面字段即可恢复旧展示，无数据回滚需求。
