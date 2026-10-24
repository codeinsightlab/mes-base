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

姓名展示重启续验：用户再次确认后端已重启后，刷新真实 Chrome 仓库页面并恢复原登录/工厂上下文。当前 TEST_DEV_PERF_20261019 的9条仓库行中，创建人与修改人均正确显示“平台管理员”，不再显示用户 ID 100；横向滚动到审计列完成截图核对。证据：`.local/warehouse-type-20261022/audit-name-runtime.png`。此前“姓名真实页面显示待重启续验”已关闭。本次仅进行页面只读核验和追加报告，没有修改仓库或用户数据；详情姓名的自动化 API 断言仍以此前 H2 测试证据为准。

## 2026-10-22 入库类型与审核实施期间的问题清单

| 编号 | 状态 | 问题与证据 | 影响 | 后续修复与验收 |
| --- | --- | --- | --- | --- |
| INBOUND-001 | 暂缓；入库类型、审核及接口验证完成后修复 | 不良列表已按执行ID查询正式扫码身份的material_lot_code，但最新10条申报对应执行的扫码身份、正式领料投入均为0条。冻结来源xtransfer_no存在，用户确认它就是材料到成品延续的批次，例如执行11287为241112107、执行12355为260727103。问题在上游正式数据未落入当前批次身份链路，不是前端漏展示。 | 列表显示“批次未记录”；需要可信批次的质量入库不能通过正式校验。不能用执行编号代替、临时拼接批次或直接写库存完成验收。 | 待本轮类型/审核工作结束，单独修复上游批次写入与既有数据的受控治理；核对工单、产品、执行和批次一致性，保留前导零与原文。验收要求执行能关联可信批次、列表/详情/入库明细/库存批次一致，冲突明确拒绝。 |

用户明确要求本阶段不修补上游批次，不新增旧系统快照查询兜底，也不修改既有执行/扫码/库存事实。此清单记录待办，不代表已补齐数据。实际目标已核对为TEST_DEV_PERF_20261019、datasource9、192.168.18.25:3306/mes_base_dev_perf_test；源证据与最新10条申报的只读检查保存在本地工作记录，不含凭证。

当前样本的另一项前置问题（INBOUND-002，待本轮功能完成后治理）：最新申报仍含旧来源的UNSPECIFIED去向和ALLOCATE处理动作，关联执行已完成。它们可查询，但不构成当前正式处置命令已生效的证明；不能通过恢复旧流程、重开生产或删除事实来强行制造入库成功。接口验证应记录原拒绝码与完整业务前置条件，后续通过明确的数据治理方案解决。

## 2026-10-22 入库业务类型与审核入口

业务类型扩展为采购入库、生产完工入库、委外加工入库、退料入库、返工入库、销售退货入库、其他入库、不良品入库、报废品入库，另保留其他出库。方向由枚举确定，不把所有新入库类型当作出库；库存增减仍由唯一LocalStockService执行。

- 新建只保存DRAFT草稿，不产生库存动作；正式执行入口为 `POST /api/wm/stock-documents/{id}/actions/approve`。审核通过时同一事务写库存动作、余额、审核人、审核时间和原请求UUID，状态编码继续使用既有POSTED（页面显示“已审核”），不重写既有事实。旧actions/post HTTP命令移除，当前前端、测试及执行脚本同步改为审核；既有专门执行权限business:wm:stock:document:post保留用于审核执行，不增加或暗授新权限。
- 类型目录 `GET /api/wm/stock-documents/types` 提供中文类型、业务来源、所需关联依据和审核可用性。采购、委外、退料、返工、销售退货的来源尚未接入本单据链路，可存草稿，但服务端禁止审核；“有类型选项”不代表对应来源业务已经实现。退料已有领退料能力也不能当作已完成新单据的来源余额关联。
- 新生产完工入库必须明确选PRODUCTION_RECEIPT并关联执行单元，继续检查当前来源/模式、正常产出、正式质量放行及已入库数量，原子限制超收。既有OTHER_RECEIPT中的生产事实继续计入已收数，避免重新放大额度；不改写历史类型。
- 不良品/报废品入库只能从质量处置正式入口创建，普通建单不能伪造质量来源。目标仓必须分别为DEFECTIVE/SCRAP；质量入口创建相应类型的主单及明细，关联原申报/处置流水，再调用正式审核能力。审核验证处置请求ID、入库单ID、接收仓库及数量一致，成功后才提交库存动作。审核失败全部回滚，原UUID重放不重复入库。
- 质量单据详情返回qualitySource关联，可查看原不良记录、处置流水及执行标识；关联取自正式处置记录的单据引用和审核UUID，不依赖备注解析或客户端提供的假来源。工废/料废分类保留在原申报，不能把“返工入库”用于未合格的不良品。此次仅LOCAL库存；5705/5704/1601/1F05/1602是用户提供的ERP业务参考，不代表已实现或调用真实ERP操作。
- 不新增表、列或修改存量数据；现有document_type长度足以保存新增编码，关联复用既有正式quality treatment→stock document引用。不补上游批次、不恢复一次性导入代码；INBOUND-001/002留待本部分完成后修复。最新10条样本应通过正式HTTP命令验证，不能用Java服务探针或SQL直接入库替代。

### 2026-10-22 最终构建、用户重启及正式接口续验

最终代码验证：标准 Maven `mvn -B -ntp clean verify` 成功，合计846项、0失败、0错误、48项环境门控跳过；最后一次查询读取保护调整后，执行 `mvn -B -ntp -pl mes-admin -am verify -Dtest=StockDocumentHttpTest,UnifiedInboundHttpTest,QualityDispositionHttpTest -Dsurefire.failIfNoSpecifiedTests=false`，29项全部通过并重新生成最终JAR。打包隔离烟测使用该最终JAR，10项通过，不连接用户业务库或ERP。前端全量74文件/433项通过，最后一次审核按钮保护调整后定向7项通过、生产构建通过。Java格式化和前后端diff空白检查通过。完整命令日志位于本机 `/tmp/mes-inbound-approval-*`；未运行的环境门控测试不计作真实验收。

用户确认最终后端已重启后，使用现有8081服务、正式登录和Factory请求上下文执行HTTP验证；再次只读核对中心绑定：TEST_DEV_PERF_20261019、datasource9、192.168.18.25:3306/mes_base_dev_perf_test、LOCAL，两个目标仓库10/11均为启用LOCAL仓，类型分别DEFECTIVE/SCRAP。没有自行重启服务、修改工厂模式或访问真实ERP。

- `GET /api/foundation/context` 返回200，Factory正确。
- `GET /api/wm/stock-documents/types` 返回200：九种入库加其他出库共10项，五类尚未接通来源的类型明确approvalAvailable=false。
- `GET /api/quality/defects?offset=0&limit=10` 返回200、10行、total=15597；这些旧样本不阻塞查询。
- 对已冻结最新10条申报32150至32141（合计130），逐条通过 `POST /api/quality/defects/{declarationId}/dispositions` 提交固定UUID及原数量，目标报废仓11。每条响应均已保存，没有直接调用Java Service或写SQL入库，也没有自动重试、修改样本或绕过校验。

| 申报记录ID | HTTP | 原错误码 | 实际结果 |
| --- | --- | --- | --- |
| 32150、32143 | 409 | RESOURCE_CONFLICT | 关联工单已完成，活动工单校验拒绝 |
| 32149、32148、32147、32146、32145、32144、32142、32141 | 409 | QUALITY_TREATMENT_CONFLICT | 关联执行已完成，当前正常生产处置准入拒绝 |

以上是本次实际最先触发的拒绝条件；批次缺失、UNSPECIFIED去向及旧ALLOCATE动作是已核实的其他前置缺口，不能声称本次HTTP已执行到对应校验。INBOUND-001/002继续待治理，不恢复旧流程、不重开已完成生产、不伪造批次使验收变绿。

命令后只读复核：按10个原请求UUID查处置流水、审核单据及库存动作均为0；单据总数仍1111，库存动作仍4030条、quantity_delta汇总仍90973.920000；仓库10/11库存行仍为0，与命令前备份逐项一致。故真实结果是“10条已核对并实际调用，0条成功入库”，尚未完成这10条数据的库存业务闭环。新建不动库存、审核原子入库、质量创建并审核与幂等的成功路径已由H2 HTTP集成测试验证，不能冒充这次真实开发库成功入库。

本地证据位于忽略目录 `.local/quality-inbound-20261022/`：latest10-before-api.json、request-manifest.json、requests/、responses/、api-results.json、binding-live.json、context-live.json、types-live.json、defects-list-live.json、after-api-readonly.json。登录凭证和会话仅在私有文件中用于本地请求，不纳入Git或报告正文。本次续验仅追加此文档，没有改动业务代码或数据库事实。


## 2026-10-24 统一生产领料出库及生产入库便捷命令

本节补充当前正式实现；2026-10-22章节中“正式质量放行”不再表示独立放行门禁，当前以有效报工、质量数量及正式入库事实统一校验和收尾。旧执行回执保留，不把其样本拒绝或原有成功路径重新宣称为本轮真实验收。

- 普通仓储单仍为 `create → putLine → 独立approve`。新增／保存主单和明细只写DRAFT，不改库存；POSTED表示审核通过且库存已生效，不增加审批后的第三阶段。审核记录原UUID、操作者、时间及逐行库存动作。
- 新增 `MATERIAL_ISSUE` 正式生产领料出库类型，禁止普通自由填类型伪造来源；从 `POST /api/production/input/drafts` 创建冻结BOM草稿，再在本页面审核。保存时无Execution/投入/库存事实；审核经当前生产校验建立可信领料关联，调用同一个 `StockDocumentService.approve → LocalStockService.postDocument`。直接便捷投产 `POST /api/production/input` 也复用此链路，全部LOCAL事实同事务提交或回滚。
- 新增 `POST /api/production/executions/{id}/inbound`，请求为 `{requestId, stockId, quantity, reason}`。后端从Execution及库存行取得产品、仓库、基本单位、批次原文与库位，编排正式新建、明细、审核；返回与普通仓储单相同的详情白名单。严格审核有效净产出减累计已入库余额，正常和质量审核继续触发既有Execution/工单收尾检查。便捷入库要求创建与审核权限；领料新建／便捷命令按创建、明细修改及审核动作要求权限，已有领料草稿审核只要求原业务与审核权限，不要求审核人拥有新建／修改权限，不改变角色授权。
- 不良／报废入仓继续 `QualityDispositionService.createQualityReceipt → putLine → approve`，不重复实现库存、不新增返工或入仓后物流处置。管理员订正及报工不增加自动完结触发。
- 领料草稿原请求被冻结，明细不开放脱离BOM的手改；原因／备注仍可保存。数量或依据变化需作废草稿后按新请求建立当前BOM草稿，审核时重验依据与额度。明细保留精确批次／单位／仓库／库位；冻结依据损坏时仅该区域返回明确错误，原头及明细继续可读，审核仍严格拒绝。
- 旧手工领料页不再新增/确认缺正式投入事实的LOCAL领料；保留已存事实、UNKNOWN核实及合法撤销。已有历史已确认记录不补建仓储单、不补扣库存、不重写UUID或冻结请求。

V61及用户批准的V62已通过正式Flyway在 `TEST_DEV_PERF_20261019 → datasource9 → LOCAL → 192.168.18.25:3306/mes_base_dev_perf_test` 执行并validate成功。V61增加服务端关联及冻结请求两可空列，V62为原请求UUID增加唯一索引，跨工单并发不能重复建草稿。执行前分别核实绑定、DATABASE()、存量和冲突并备份；执行后确认Schema及原单据所有列不变。完整调用映射、生成与迁移证据、验证结果及运行交接统一追加在 [生产投入实施记录](production-input.md#2026-10-24统一正式出入库与审核链路实施)，避免另建平行报告。当前运行旧服务、新代码真实验收待重启，单元／H2结果不冒充指定MySQL业务验收。
