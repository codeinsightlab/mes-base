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
