# 仓库／库存实现与验收

2026-10-13，main，本轮未提交／推送。用户粘贴需求为两个 SIMPLE 业务，不扩展 WMS。

## 初轮结论（登录前，已由下文续验更新）

Warehouse / Inventory = IMPLEMENTED_ACCEPTANCE_PENDING。Schema、真实建表、Codegen、必要业务规则、菜单 SQL、基础测试与构建已完成；登录后的真实服务 API 与完整浏览器验收仍待用户登录，不能标记 COMPLETE。

## 实现与调用链

- Codegen 配置 `docs/development/warehouse-inventory-codegen.yaml`，显式仅选择两张表、generateSync=true、write/fail；保留原用户全量配置。
- 实际 `scripts/mes-business-codegen ... target/warehouse-inventory-codegen/report.json`：GENERATED，2 supported / 0 rejected，真实 MySQL metadata。
- 生成路径：mes-business 的 `dev.mesbase.business.wm` Controller → Service → 生成 Mapper/XML/Example；前端 `src/views/wm/{wmWarehouse,wmInventory}/index.vue` → `src/api/wm` → CrudPage。
- 业务引用规则：StockReferenceRules，同厂校验、库存引用写入与删除共享父行锁、禁止删除被引用的 Warehouse 和 Material。库存列表通过 WmInventoryItem 批量补名称，由 Controller 转换 HTTP VO。
- 同步独立权限、Factory＋sync_key 抢占、run_token 校验、库存物料＋仓库复合匹配、整批回滚。没有真实 ERP Port Bean 时明确失败。
- 手工改动的生成文件已移除生成归属标记；保持生成器模板中的必要标记。未新增单表 ExtraMapper/DAO。
- 前端复用统一组件、状态选择、MaterialSelect/WarehouseSelect，有界分页及切厂/撤权清理；Bigint 和 Decimal 字符串保持精度。共享 CrudPage 只增加搜索字段 slot，保留进入任务前已有状态选项与编辑字段 slot。

## 数据库执行事实

业务库 `mes_base_dev_business`：Flyway V8 成功，history version=8 / success=1。
两表实际存在，所有表/字段有中文数据库 COMMENT；两组业务唯一键已确认，未建物理 FK。
真实 MySQL 生成 SQL 测试验证 DECIMAL(18,4) 精度及两组唯一键；测试记录事务回滚，TEST_STOCK_ 范围仓库数量=0。回滚测试可能消耗 MySQL 自增序列，不留下业务行。

中心库：本轮 V26 菜单 SQL 已直接执行并查询确认，目录物料库存、仓库在前、库存在后，各自 list/query/create/update/delete/sync；没有为角色分配新权限。
**V26 未通过中心库 Flyway 链执行，不能声称中心库 Flyway 已完成。**

### 中心库历史事故与恢复边界

Agent 没有先核对中心库 Flyway history，就调用了完整中心库 migrate：原 history 停在 V14，本次实际成功应用 V15（延后生产监控/人员分析菜单）和 V16（撤回 OpenAPI 菜单），随后 V17 因 `mf_user_preference` 已存在而失败，留下 V17 success=0。
这是本轮执行失误，不是业务库 V8 失败。V15/V16 含删除相应菜单及角色菜单关联的 SQL；没有执行前快照，不能证明受影响关联数或恢复原有授权。

为恢复当前代码已支持的 OpenAPI 菜单，直接执行已有 V22 add-if-missing SQL，已查询确认 1 个页面和 5 个按钮。未赋予角色权限，未推断旧授权。此操作不是 V22 Flyway 已执行，也无法恢复删除前的菜单 ID/自定义配置或角色绑定。
未删除已有表、未改历史 migration、未执行 Flyway repair 或伪造成功历史。后续需以备份／管理侧事实核对旧菜单授权，并单独协调中心库 migration history；不得无审查再次运行完整中心库 migrate。

## 验证

| 命令／方法 | 结果 | 证据边界 |
| --- | --- | --- |
| `MES_STOCK_MYSQL_ACCEPTANCE=true mvn -B -ntp verify` | BUILD SUCCESS；384 run / 0 failure / 0 error / 9 skipped | 包含 6 项新增 HTTP/H2＋真实 MySQL测试；9 skip 保留其环境边界 |
| `StockHttpTest` | 5 passed | HTTP 授权与事务，独立 A/B H2 数据库，合成身份；不是实际 Redis 登录或真实 A/B MySQL服务验收 |
| `StockMySqlTest` | 1 passed | 开发 MySQL 上真实生成 XML、Decimal、唯一键和回滚；直接 Mapper 验收，不冒充服务端登录验收 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 10 passed / 0 failed | 打包结构与已有 smoke，不替代浏览器业务验收 |
| Node22 `npm test` | 31 files / 163 passed | 含 3 项新增引用清理／迟到响应／同步权限隐藏测试 |
| Node22 `npm run build` | passed | 保留已有大 chunk 提示 |
| 新增前端文件定向 ESLint | passed | CrudPage 原有 40 errors / 9 warnings；移除本轮搜索 slot 构造进入任务前版本后，规则与消息数量完全一致，未扩大问题 |
| `scripts/format-java --check` | 44 files passed | 本轮业务 Java；额外生成器契约测试亦使用原生格式化 |
| 两仓库 `git diff --check` | passed | 未提交、未推送 |

新增 HTTP/H2 测试覆盖仓库及库存 CRUD、独立 sync 权限、默认未接入拒绝、A/B 分库及跨厂详情拒绝、非法物料/仓库引用、重复库存键、引用仓库删除拒绝、批次重复键拒绝、同物料多仓库同步、Decimal 等值跳过、更新和非法批次回滚；运行令牌在处理途中或提交前失效均回滚本轮写入。

生成器通用未接入模板此前错误引用物料专用错误码，已改为 `SYNC_QUERY_NOT_IMPLEMENTED`，更新 scaffold 模板及 CodegenSyncAcceptanceTest，旧既有业务 Adapter 未重生成。

## 运行与浏览器边界

用户已有 5173 → 8081 页面：进入任务时服务返回 500，浏览器显示无法恢复登录；未停止／重启用户 8081。

本轮独立前端 `http://127.0.0.1:15175`、后端 `127.0.0.1:18082`，最终 JAR 已启动，中心库自动 migration=false。健康请求通过；两模块匿名 GET 均 401。当前系统装配会自行启动 Quartz，Spring auto-startup 选项不能替代它；没有修改其任务配置。

浏览器初次全局 inventory / Chrome 调用超时；改用 IAB 后可用，已打开真实登录页。没有账号凭证／已登录验收会话，已请求用户在页面登录；当前未执行登录后动态菜单、CRUD、选择器、分页、切厂、权限隐藏的完整实际浏览器流程。不能以 jsdom／MockMvc 补称浏览器通过。

两临时进程与 IAB 页保留供继续验收。用户登录后需要确认实际角色拥有本轮模块权限及 `business:material:list/query`，不自动分配权限。

## 延后与保护

`WAREHOUSE_LOCATION = DEFERRED`；`BATCH_INVENTORY = DEFERRED`。未做库位、批次、冻结、在途、预占、成本、流水、库存扣减或 WMS。
保留前端进入任务前未提交改动，及期间其他执行者新增的 routing/schema/source evidence 文档改动。没有旧公司项目修改。

## 后续／回退

完成真实 API 和浏览器验收后才将 Warehouse / Inventory 标记 COMPLETE；首先核对中心库历史事故的菜单授权影响。
代码可按本轮具体路径撤回；真实库、菜单、授权和 Flyway history 必须分别审查，不能通过删表／批量删除迁移历史声称安全回退。生成文件手工所有权已解除，不允许恢复标记消除覆盖冲突。

## 登录后续验（2026-10-13）

用户已登录并自行配置可用权限；Agent 仅打开角色对话框检查并取消，没有保存角色或自行赋权。

### 已通过的真实服务／浏览器项目

- `物料库存 → 仓库管理／库存管理` 动态菜单与页面均可用，真实登录账号 admin，Factory 1。
- 新建 Warehouse ID 9，编码 `QA_STOCK_20261013`，备注 TEST ONLY；编辑名称、编码查询、详情回显和服务端审计字段已确认。重复编码拒绝，明确业务文案已复测。
- Inventory ID 9，引用现有 TEST ONLY 物料 ID 3 与本轮仓库 ID 9；物料／仓库选择器、新增、编辑、引用回显和组合查询通过。库存从 `12345678901234.1234` 编辑为 `12345678901234.4321`，可用量 `9876543210987.0001`，页面与真实 MySQL 查询一致，无浮点截断。
- 再次创建相同物料＋仓库库存被拒绝，无重复记录。
- 两个实际同步入口经过确认后均显示“暂未接入外部同步查询接口”；未伪造 ERP 成功。真实 ERP upsert 仍为 Port fixture 测试证据。
- 分页大小 20→10、生效后的页码／总数检查通过；本轮仅保留 1 条记录，没有实际跨页大数据验收。
- 1440／1920／1280 桌面截图及 DOM 页面宽度检查通过；扩大两数量列至 220、引用名称列至 260，桌面数量不再截断；需要时表内横向滚动。

### Factory B 迁移与隔离

第一次切到 SIMPLE_B 发现独立库 `mes_base_dev_business_simple_b` 缺两表，页面真实报错。只读核对其 Flyway 1..7 均成功；用户随后明确授权“允许仅执行 V8”。
迁移设置 `target=8`，只执行 1 项 V8，history V8 success=1，没有重放旧迁移或执行中心库链。复测 B 两列表均 0 条、无服务器错误，A 两条验收记录仍可读取；真实两个业务库 SQL 查询结果为 A 仓库／库存各 1 条，B 各 0 条。切厂后旧选择和记录清空。

### 用户明确排除的删除验收

用户选择“保留记录，不执行实际删除”。已取消删除弹窗，**没有发送删除请求**；Warehouse ID 9／Inventory ID 9 保留，未修改或删除现有物料。
因此真实删除及引用仓库删除拒绝不宣称通过；它们仍由已有真实 MyBatis SQL／Spring 事务／MockMvc fixture 覆盖。本次用户决定优先于最初实际删除验收清单。

### 续验改动与最终检查

实际重复键验收发现前端将专用错误降成通用冲突。仅在已有安全错误白名单补齐 STOCK_KEY_DUPLICATE、STOCK_REFERENCE_INVALID、WAREHOUSE_REFERENCED、MATERIAL_INVENTORY_REFERENCED、SYNC_QUERY_NOT_IMPLEMENTED；5 项新增请求测试证明准确文案、不泄露原始内部消息且不触发选厂／退出。

Crud Field 增加可选 `columnMinWidth`，共享 CrudPage 默认行为保持；两模块仅按实际内容配置宽度。保留 request.ts／request.test.ts／CrudPage 原有用户改动。
验收 Vite 进程未检测到文件更新，已只重启本轮 15175 进程并启用 polling；用户 5173 与 8081 未停止。

最终前端 tests：31 files / **168 passed**；build 通过；本轮新增文件与 request/crud/请求测试的定向 ESLint 通过；两仓库 diff --check 通过。未修改新的后端 Java，不重复 Maven 构建；上一轮全量 verify／smoke 结论继续适用。

截图：`assets/2026-10-13-stock/inventory-{1440,1920,1280}.jpg`、`inventory-factory-B.jpg`。桌面尺寸截图文件的像素宽度分别已确认为 1440／1920／1280。视口最终已 reset。

### 实际证据边界与当前状态

本轮授权范围的桌面基础验收完成。实现状态 Warehouse / Inventory = IMPLEMENTED；桌面浏览器基础验收 = PASSED_WITH_USER_EXCLUSIONS。
权限隐藏／无 sync 权限拒绝、非法物料／仓库 ID 和运行令牌失权负例仍为组件／HTTP fixture 证据，当前 admin 的真实浏览器未换为受限账号，也未提交伪造引用。
390px 窄屏已观察：全局壳的页面宽度 417px，导航关闭状态在工具中未能确认；这一既有全局壳／工具边界未扩大成 WMS 模块改造，窄屏不标记通过。
中心库 V17 失败历史和先前 V15/V16 的旧角色绑定核对仍未解决，不能因 B 库 V8 成功而改写为中心库 Flyway 成功。无提交／推送／部署。


## 2026-10-13 后续决定：改为纯业务库隔离

本报告前文的表内factory_id、同厂行校验及带工厂前缀索引是当时实现记录，已由用户新决定取代。
已配置SIMPLE_B业务库通过V15移除工厂列；业务代码、DTO和页面已重新生成／适配，不再叠加
行级工厂隔离。引用存在性、稳定工艺版本、事务、权限和父子保护保留。领料两表现在共30列。
详情及最新证据见 [业务工厂字段移除报告](2026-10-13-business-factory-column-removal-review.md)。
本次未自行重启用户服务，旧API/UI通过记录不代表无工厂字段版本已完成真实服务验收。


## 2026-10-16 中心 Flyway V17 恢复完成

用户明确追加授权定位并修复后，已实时复核中心库 `192.168.18.25:3306/mes_base_dev` 与 SIMPLE_B 绑定、备份受影响范围，调用正式 Flyway repair 仅移除失败 V17，并通过 `skipExecutingMigrations` 逐项纳管已存在且核实过的 V17/V19/V20/V21/V23 结构，其余版本正常执行至 V29。没有改写历史 SQL、手工伪造 history、删除偏好表或自动恢复无法证明的旧角色授权。

真实结果：29 项迁移校验通过，failed/pending 均为零；354 个原菜单 ID、885 条角色菜单关联、4 条工厂偏好、个人列偏好及路由代次保持原样。不良管理菜单 V29 已正式执行。原有正常迁移入口 MySQL 测试 1 项通过、BUILD SUCCESS；未修改业务代码或重启服务。详细方法、备份、证据及其他未验收边界见 [ProductionDefect 报告的中心迁移恢复记录](2026-10-16-production-defect-phase1-implementation-report.md#2026-10-16-中心迁移恢复与菜单落库用户追加授权)。本追加不改写事故发生时的记录，也不证明旧删除授权已经恢复。
