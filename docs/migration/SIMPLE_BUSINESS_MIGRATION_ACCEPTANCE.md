> 当前 Quality 决定（2026-10-13）：DEFERRED；历史质检表与验证证据仅作 HISTORICAL。详见本文末尾。

# 第一批简单业务迁移验收

2026-10-12；用户批准的六项 SIMPLE，采用当前 Base Codegen，不复制 Source CRUD。
Source 只读，使用 `ktg-mes` 的 Schema 快照、Java 规则与 `ktg-mes-ui` 页面验证字段消费；未访问 Source 数据库或导入公司数据。

## 模型和边界

- V2 创建六张新表，全部 factory_id NOT NULL，表和每列有中文 COMMENT。旧 V1 迁移不改 checksum，旧 md_unit_measure/md_workshop 表留存为历史资产，旧生成 Controller/Service/Mapper/XML/UI/测试已退役，不作为第二套 CRUD。
- 编码表由 UNIQUE(factory_id,code) 保护厂内唯一；安全库存由 UNIQUE(factory_id,material_id) 保护一物料一配置。没有物理外键，引用和被引用删除由 BasicDataRules 与当前业务事务检查。
- 服务端赋 Factory，写入 DTO 和外部 DTO 不接收 factory_id；单表 SQL 均复用真实 MBG Mapper/XML/Example。范围过滤包含列表、详情、更新、批删、同步匹配；按 Factory 的独立数据库路由继续保留。
- 分类防环路、父分类和引用删除；物料校验分类/单位、ITEM/PRODUCT 与状态；工作站可无车间；安全库存非负、上限不小于下限、允许清空上限。没有生产执行、库存计算、BOM/MRP 或采购流程。
- 权限按本轮拆为 list/query/create/update/delete/sync。配置支持已有 read 查询约定和本轮分离约定；六项采用分离约定。Sync 独立授权，不是 create 别名。
- 沿用真实 Prototype 的 ObjectProvider→单个 QueryPort Bean→未配置 fallback；旧 TEST Resolver 已在此前删除，当前没有可复用 ERP Resolver，所以不新造。SyncCoordinator 先成功查询再按 Factory+sync key 抢占，独立 Spring SyncWriter 事务写入，保留 run token 保护。
- Material 标准 DTO 使用单位/分类编码，由 SyncWriter 映射本地 ID；外部来源 ID/code 不作本地主键；ERP-only 字段在 Adapter 边界忽略。其余五项为有类型的同步壳子/未配置接入点，未实现真实 ERP Adapter。
- 生成器显式 tableOverrides.serverScopeColumn=factory_id，不根据字段名称猜主体；配置仅选本批六表。业务校验、关系编码映射与页面优化是生成后的已审查后处理，重新 write/replace-generated 会覆盖手工规则，重生成需审查。

## 逐项报告

### 物料产品分类

| 字段 | 结果 |
|---|---|
| MODULE | 物料产品分类 |
| SOURCE_TABLE | md_item_type |
| TARGET_TABLE | material_category |
| SCHEMA_CHANGE | 父分类为空表示根；保留 sort/remark，删除 ancestors、分类快照及预留字段 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/material-category/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

### 计量单位

| 字段 | 结果 |
|---|---|
| MODULE | 计量单位 |
| SOURCE_TABLE | md_unit_measure |
| TARGET_TABLE | unit_measure |
| SCHEMA_CHANGE | 编码、名称、可空符号、状态；不迁换算系统 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/unit-measure/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

### 工作站

| 字段 | 结果 |
|---|---|
| MODULE | 工作站 |
| SOURCE_TABLE | md_workstation |
| TARGET_TABLE | workstation |
| SCHEMA_CHANGE | 统一 Workstation；workshop_id 可空；不迁线边库、设备、工序、条码副作用 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/workstation/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

### 车间

| 字段 | 结果 |
|---|---|
| MODULE | 车间 |
| SOURCE_TABLE | md_workshop |
| TARGET_TABLE | workshop |
| SCHEMA_CHANGE | 可选组织层；不迁 ERP 绑定、名称快照、部门和无当前消费者的面积 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/workshop/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

### 物料产品

| 字段 | 结果 |
|---|---|
| MODULE | 物料产品 |
| SOURCE_TABLE | md_item |
| TARGET_TABLE | material |
| SCHEMA_CHANGE | 最小 MES 投影；分类/单位为本地引用；ITEM/PRODUCT 沿用源标识；外部标识沿既有物料边界保留 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/material/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

### 安全库存

| 字段 | 结果 |
|---|---|
| MODULE | 安全库存 |
| SOURCE_TABLE | md_item.safe_stock_flag/min_stock/max_stock |
| TARGET_TABLE | safety_stock |
| SCHEMA_CHANGE | 物料粒度；min_qty 为安全下限，max_qty 可空；不重复增加 safety_qty 或采购参数 |
| CODEGEN_RESULT | 当前 Base Codegen；真实 MySQL metadata；GENERATED；MBG Entity/Example/Mapper/XML、CRUD、显式 VO、前端、权限、测试 |
| SYNC_CAPABILITY | ERP_SYNC=true；独立 :sync；POST /api/material/safety-stock/actions/sync；未配置 422 / ERP_SYNC_NOT_CONFIGURED |
| CONVERTER | scripts/migration/ktg-mes/simple-base.sql；按编码映射本地主键；实际 MySQL 合成 Source 验证通过 |
| MYSQL_RESULT | V2 首次成功、重复零变更；中文 COMMENT、NOT NULL、唯一索引、逻辑引用验证通过 |
| API_RESULT | 实际 Redis 登录/准入、Spring、MySQL CRUD 与对应规则通过；Material 另验 TEST create/skip/update/403/NOT_CONFIGURED |
| UI_RESULT | 实际浏览器新增、查询/读取、编辑、删除通过；核心四项状态和边界有 API 及浏览器证据 |
| MENU_LOCATION | 基础数据；物料产品→物料产品分类→计量单位→工作站→车间→安全库存配置 |
| REMAINING_DEBT | 真实 ERP Adapter 未接入；不宣称生产验收或导入了真实 Source 数据 |

## 实际验证与证据

- 主开发业务库：当前配置 mes_base_dev_business。首次 Flyway V2 applied=1；再次 migrationsExecuted=0。B 验收库独立应用 V1/V2；不是中心 Flyway history。
- Codegen 主报告 `.local/simple-business-20261012/codegen-report.json`：6 supported / 0 rejected；测试骨架另一次只选 test 生成，未覆盖业务规则。权限调整在隔离输出审查后只拷贝 Controller/Permissions/菜单/测试。
- 实际 API：81 项 CRUD/规则/不同物理库 A/B 检查；Material 5 项同步检查；同物理库插入另一 factory_id 的 TEST ONLY 行，另验 6 项列表/详情/更新/删除/同步边界。这些临时越界行已清除。
- Converter：实际 MySQL、明确 TEST ONLY 合成 Source；六表共 7 行，树父子、单位/分类/车间引用、1.2345/9.8765 小数转换正确；合成 Source 库和转换行已清除。
- 浏览器：六页 CRUD；核心四页查询/编辑/状态；物料被安全库存引用时删除失败；移除配置后可删除。A→B 清空业务数据；B 未配置显示“暂未配置 ERP 同步数据来源”，保留登录身份；A Material TEST 同步显示获取/新增/更新/跳过并刷新。
- 1440、1280、1920 桌面宽度检查；窄桌面采用既有表格横向滚动与 tooltip，不创建独立 UI 风格。截图见本目录 simple-business*.jpg。
- IDEA 格式化、Maven/前端 test/build、打包 smoke 与 diff 检查实际结果见 SIMPLE_BUSINESS_MIGRATION_EVIDENCE.json。

## 环境和未验证边界

首次使用了指向另一开发实例的旧 `.local/mysql-dev.cnf`，新增菜单已事务撤销；后续中心与业务库均按当前 application-dev.yml/Codegen 实际地址核对，未改现有 Factory 绑定。Factory A 使用已有工厂 1，B 为本次独立临时验收工厂。
开发中心库串行应用基础数据菜单，并向开发平台管理员授予本批权限用于 UI 验收；菜单脚本本身不含角色授权。未提交、推送、部署或重启用户原有服务。
既有 V1 历史表未删除，未处理质量/设备/工单代码。真实 ERP、多节点同步恢复、批量性能和生产环境不在本轮证明范围。逻辑引用检查没有物理 FK 的全路径数据库约束，未来非 Service 写入入口须继续遵守同一规则。

## 2026-10-13：撤回提前实现的 Quality（当前决定）

Quality = **DEFERRED / NOT_IMPLEMENTED**。`qc_index`、`qc_defect` =
**REMOVED_FROM_CURRENT_BASE**。Previous：GENERATE / SCHEMA_READY → REVIEW / RECONSIDER；
Current：移除简单 CRUD、同步占位、前端页面/菜单和 Source → Base Converter。
旧表结构、映射验证、Source 引用与审查结论均为 **HISTORICAL**，不再构成当前迁移准入或正式转换断言。
撤回原因：Quality 必须等生产执行模型稳定以后整体设计；简单表不等于简单领域。

生产主链优先：Operation → Routing → RoutingVersion / RoutingNode / RoutingEdge → WorkOrder →
ProductionExecution / Reporting → Basic WIP → Completion。该顺序是后续实施方向，
不是对当前未实现环节的验收声明；本轮保留已有生产及资源工作区改动。
主链稳定后再审查 Inspection Definition / Plan / Requirement / Execution / Result、Defect /
Defect Record、Disposition / Rework / Scrap / Release 与 RoutingNode / RoutingVersion、
WorkOrder、ProductionExecution、Reporting 的关系。现在不确定最终表名或 Schema，不保留 Quality 空壳。

执行文件清单、数据库证据与验证边界见 [Quality 撤回回执](QUALITY_EARLY_IMPLEMENTATION_REMOVAL.md)。
