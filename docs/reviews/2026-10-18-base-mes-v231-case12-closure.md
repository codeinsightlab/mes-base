# Base MES V2.3.1 — LOCAL 全链路收尾与 CASE12 真实多厂补验

日期：2026-10-18。结论：**writable P2 修复 GO；CASE12 GO；V2.3 LOCAL 本次约定范围整体 GO。**

本轮补足两个真实 MySQL 业务库、真实登录后的授权差异、同 ID 隔离、来源伪造拒绝、有界并发、数据源异常及浏览器证据。CASE01～CASE11 **沿用 [V2.3 报告](2026-10-17-local-full-chain-v23-acceptance.md) 的历史真实结果，没有本轮重跑的宣称**；原报告当时的 NO-GO 保留。V2.2 来源与单据契约见 [V2.2 报告](2026-10-17-local-stock-documents-v22-report.md)。本结论不是 ERP 验收、部署或发布授权。

## 1. 授权、工作区与保护

依据本轮用户请求，创建一个独立测试 Business Schema，通过正式平台接口创建 Factory、DataSource、最小权限角色与专用账号；业务准备仅写新 B 库。用户此前授权自行启停验收服务，沿用独立 18081/15173 与临时回环 Redis 17379；原 8081/5173 不替换、不重启。

| 项目 | 执行前后 |
|---|---|
| Backend | `/Users/user/work/lixin/mes-base`；main；HEAD `5793986eda8b52eb6d24a84640b43f5d192e2cb3`，未变化 |
| Frontend | `/Users/user/work/lixin/mes-base-ui`；main；HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`，未变化 |
| 后端原 dirty | 2 个文件：已有 LocalFullChainV23HttpTest 修改及 V2.3 报告，字节哈希全部未变 |
| 前端原 dirty | 64 个文件；62 个哈希未变；仅 ProductionReportManager.vue、productionReport.test.ts 因本轮修复增量修改，已有 UNKNOWN/UUID/质量门禁内容保留 |
| 保护证据 | `.local/case12-v231-20261018/{backend,frontend}-before.json`、`*-before.patch`、`*-preservation.json` |

已读取个人工程入口、工程原则、工作流、Java/前端规范、知识治理及两个仓库适用 AGENTS，核对既有报告、生产命令、Factory/权限/路由、Flyway。未提交、推送、部署，未调用真实 ERP，未覆盖并行修改，未重写已发布迁移。

## 2. 真实环境与创建前检查

| 项目 | 核实值 |
|---|---|
| MySQL | `110.42.227.83:3306`；MySQL 8.0；hostname `VM-4-11-centos` |
| 实例 UUID | `21328a5f-b5fa-11ef-88d3-5254005efa1b` |
| 管理中心库 | `mes_base_prod`，原 Flyway V40 |
| 原 Factory / DataSource | PROD / 1；ENABLED；LOCAL；ERP provider/config 均 NULL |
| 原业务库 | `mes_base_prod_business`，业务 Flyway V38 |
| 新库 | `mes_base_v23_factory_b_test`，创建前不存在 |
| 新 Factory 编码/ID | `V23_FACTORY_B_TEST`，创建前无冲突 |
| DDL 能力 | 当前连接账号具备 CREATE/ALTER 等所需权限；没有新增 MySQL 账号或修改原账号权限 |
| 字符集/排序规则 | 沿用原业务库 `utf8mb4 / utf8mb4_unicode_ci` |

创建前保存实例、权限、数据库存在性、真实绑定及迁移记录到 `preflight.xml`；原库 43 张业务表完整逐表 SELECT 快照为 `prod-before.json`，与 V2.3 收尾快照完全一致。另私密保存原 Factory、DataSource、admin 用户及原角色/授权行。凭证、Token、密文不写入报告或 Git。

只执行一次 CREATE DATABASE 创建该新库。之后使用项目现有 `db/business` 正式迁移目录、`flyway_business_schema_history`、目标 V38，通过 Flyway `migrate()` + `validate()` 初始化；**38 项全部成功**，未 baseline/repair、手工冒充迁移记录或复制 PROD 数据。新库 44 张表（含迁移历史），列结构与原 PROD 逐列比较完全一致。历史迁移自身的建表/清理动作仅发生在本轮空白新库。

正式平台流程依次为创建数据源、启用数据源、创建并绑定 LOCAL Factory。新业务库没有复制 sys_user、admin 密码或任何生产事实；专用系统账号在中心库通过正式 API 新建独立随机密码。数据源连接使用已核实的 MySQL 连接账号，密码由现有正式加密配置机制保存。

## 3. 新增资源与权限

| 资源 | ID / 范围 |
|---|---|
| Business Schema | `mes_base_v23_factory_b_test` |
| Factory | `V23_FACTORY_B_TEST`，名称 `TEST_V231_CASE12 隔离工厂B`，enterprise `prod-test` |
| DataSource | 2，名称 `TEST_V231_CASE12`；同实例 3306 / 新库；最终 ENABLED |
| Factory 模式 | LOCAL，ERP provider/config 均 NULL；未修改原 PROD 模式/绑定 |
| 测试角色 | 102，`test-v231-case12-b`，FACTORY，所属 B |
| 测试账号 | 101，`test_v231_case12`，仅角色 102，无 PLATFORM 角色 |
| 基础业务数据 | 单位 1、物料 1、LOCAL 仓库 1、零余额库存来源 1 |
| 正式业务事实 | OTHER_RECEIPT 单据 1、行 1、库存动作 1 条（ID/原 UUID `6454e193-9a22-4164-b5b9-6dbd0aef1bbb`）；库存由 0 正式入库 11 |
| 测试标签 | `TEST_V231_CASE12`；标签、具体请求、原 UUID、资源 ID 分别保存在 resources.json / fixtures.json / api.jsonl |

测试角色只获 7 个不同业务权限：`business:wm:inventory:{list,query}` 与 `business:wm:stock:document:{list,query,create,update,post}`。菜单 2335 为无权限的父目录，2336/2337 共用 list 权限；其余为 2338、2400～2403、2405。最初缺父目录使 UI 无菜单，经正式角色 API 加入该无权限父目录；没有增加业务权限、平台权限、生产访问权、删除/作废/同步权。

原 admin 100 仍仅原角色 100、101；原角色定义及 role-menu 关联逐行快照完全相同，未给 admin 增权。测试账号在 B 可访问，不因能发现/选择 PROD 而取得其业务权限；最终偏好恢复为 B。

## 4. writable 修复与动作范围

调用链为数量 Controller → ProductionQuantityService → ExecutionPathService / ProductionQuantityMapper / 生成 Mapper。此前 `writable` 只由路径 LOCKED 推导，导致取消工单或完工执行仍为 true。

现在该字段精确定义为：**数量投影对应的正常正向报工生命周期开放**，条件为工单 RELEASED、Execution READY/RUNNING、实际路径 LOCKED。它不表示当前人员有权、不表示还有可报余量、不替代派工/能力/投产/质量检查，也不统一授权历史订正、冲销、质量或库存操作。

- 将正式报工已有 READY/RUNNING 判断移入现有 ProductionExecutionStatus 枚举，正式命令、候选查询、数量投影共用；未增加第二套生命周期框架。
- 数量查询注入既有 WorkorderMapper 只读读取工单状态，不使用写锁或手动 Session。未锁定、未选择、未下达、取消、完工及非法状态均不开放正常报工。
- `validateChange` 与节点质量阻断仍按实际 LOCKED 路径和数量/质量约束执行；这两个既有历史纠错入口不再误用正常报工 `writable`。正常报工仍经原正式 Service 生命周期门禁。
- 前端 ProductionReportManager 明确显示不可正常报工及原因；历史补录/订正/冲销使用路径状态和各自权限、质量及正式服务约束。没有因 `writable=false` 把合法负向历史订正一起关闭，也没有宣称历史命令无条件可写。
- `calculate` 的有效输入/报工/不良/恢复/历史超额传播公式完全未修改，原 UUID 成功重放仍先于当前生命周期判断。

新增 H2 HTTP 测试覆盖 READY、RUNNING、COMPLETED、CANCELLED 工单、DRAFT 工单、SELECTED 未锁路径、无路径，以及关闭后负向历史订正；既有构造器测试适配新增只读 Mapper。前端新增关闭正常报工但仍可依权限提交历史负向订正的交互回归。

真实 PROD 接口补验：C08 工单9已取消/执行7，数量 `writable=false`，新正式报工返回 **409 / WORKORDER_CANCELLED**；C01 执行12已完工，数量 `writable=false`，新正式报工返回 **422 / EXECUTION_INVALID**。两次前后数量响应完全相同，原完成执行的成功报工 UUID 原样重放返回原事实。完整业务表前后相等，拒绝没有追加事实。

## 5. CASE12 真实验收结果

| 子项 | 真实执行与结果 | 判定 |
|---|---|---|
| D1 物理绑定 | PROD→数据源1→旧库；B→数据源2→新库；FactoryContext 按头选择正确 | PASS |
| D1 同 ID | 两库合法自动生成的库存 ID=1：PROD 原批次、余额70；B 独立标签、余额11。未改任何主键 | PASS |
| D1 数据可见性 | B 仅1条库存；B 查询仅在 PROD 存在的库存31返回404，未跨库检索 | PASS |
| D2 登录有权 | 专用有效会话在 B 查询库存/单据、创建并过账入库成功 | PASS |
| D2 登录无权 | 同一有效测试会话，手工指定 PROD 头，列表与详情均403/FORBIDDEN；平台用户管理与未授予的仓库创建也403 | PASS |
| D2 匿名 | 库存、工厂上下文匿名请求401/UNAUTHENTICATED，与有效会话403分开记录 | PASS |
| D2 切厂/切人 | 测试用户 B→PROD→B 不串权限；admin 保持原权限按厂读取；前端注销换身份、切厂/刷新结果一致 | PASS |
| D3 LOCAL | B Factory LOCAL、仓库可信 LOCAL、ERP provider/config NULL；零余额来源经正式 OTHER_RECEIPT 入库11 | PASS |
| D3 来源拒绝 | 正式仓库、工单、其他入库请求伪造 EXTERNAL、显式 NULL（另测 INVALID）均400；没有创建伪造对象或混源事实 | PASS |
| D3 重放 | 新 B 入库原 UUID 重放响应相同；仍仅一个库存动作，余额11。原 PROD 完工报工 UUID 重放亦返回原事实 | PASS |
| D4 并发 | 4个客户端工作线程、24次 A/B 交替读取，全部返回所属库70/11，无加压或写竞争 | PASS |
| D4 请求清理 | 实际服务日志可见8个复用 HTTP 工作线程分别服务两厂；随后无 Factory 头的 B 用户按本人偏好读B=11，匿名仍401 | PASS |
| D4 无绑定 | 仅临时解除新 B 的绑定，读取422/FACTORY_SELECTION_REQUIRED，无 PROD 回退 | PASS |
| D4 连接错误 | 仅新 B 数据源临时指向同主机不可用端口1，真实读取两次安全500/INTERNAL_ERROR，无库存数据/静默默认库返回；finally恢复3306与原B绑定后11，PROD仍70 | PASS |

数据源负例通过正式平台 API 对本轮新资源执行“解除绑定→禁用→编辑→启用→绑定”，finally 始终恢复；原 PROD 的数据源、Factory 和 LOCAL 模式未参与变更。500 的分类现状作为非阻断观察保留，不冒称503或故障恢复时业务成功；本轮没有为改善异常映射扩大代码范围。

来源负例属于**正式创建边界拒绝非法来源**，不是直接写入 EXTERNAL/NULL 历史后再测持久化矩阵。按照本轮禁止伪造非法业务历史的要求，没有 SQL 造非法记录；也没有真实 ERP 模式、ERP 绑定或远端调用的验收宣称。LOCAL 结论不外推至 E10。

## 6. 浏览器验收

真实 15173 前端 → 18081 已修复后端 → PROD 中心及两个实际 MySQL 业务库，无 Mock/Fixture 冒充。

- 测试账号正常登录，B 库存页展示 ID1/11、LOCAL来源、独立物料/仓库，只有库存和单据菜单；未授予的物料/仓库检索控件按权限禁用。
- 切到 PROD 后清空业务数据并回工作台，显示当前厂没有业务授权；通过原库存地址进入/刷新只显示未登记页面和无授权菜单，没有库存泄漏。真实对应 API 的403另行记录，不把 UI 路由提示当 HTTP403。
- 注销并以原 admin 登录，读取 PROD 库存 ID1/70；选择 B 后重新进入库存页，只显示 B 的1条/11；刷新保留 B 上下文。切厂回工作台、清空旧页面符合既有行为。
- 数量页选择 EXE-7、EXE-12，分别显示“工单未下达或已作废”“执行已关闭或状态非法”，并明确当前不可继续正常报工；实际节点和不可变流水保持可查，历史纠错入口不被误删。未在浏览器对 PROD 提交新的业务事实。

截图：`ui-b-stock.png`、`ui-prod-forbidden.png`、`ui-a-stock.png`、`ui-admin-b-stock.png`、`ui-b-refresh.png`、`ui-cancelled-writable.png`、`ui-completed-writable.png`，位于本轮私密证据目录。最终数量截图在请求完成后保存。

## 7. 测试与数据核对

| 验证 | 实际结果 |
|---|---|
| 后端 | `mvn -B -ntp verify` 成功；735项，690通过、0失败、0错误、45条件跳过；184个测试套件，按 Surefire XML 去重汇总 |
| 专项 | ProductionReportHttpTest 7/7；ProductionReportingHttpTest 9/9；WorkOrderCancellationHttpTest 6/6；ProductionCompletionHttpTest 1/1；ProductionQualityLifecycleHttpTest 9/9；LocalFullChainV23HttpTest 5/5，均随完整verify运行 |
| 首次验证 | 一处旧测试直接构造 ProductionQuantityService 缺新依赖，编译失败；已适配并重跑完整 verify 成功，不隐去中间失败 |
| 前端 | Node22.23.1；实际脚本 `npm --prefix ../mes-base-ui run test -- --run`，62文件、370通过、0失败；`npm --prefix ../mes-base-ui run build` 成功，保留既有大chunk提示 |
| 打包冒烟 | `/usr/bin/python3 scripts/smoke-packaged.py`：10通过、0失败；独立无真实连接的打包检查，不替代业务验收 |
| 差异检查 | 后端通过；前端全仓仍有既有 mesMaterialIssue/index.vue:3 尾空格，该文件哈希未变；本轮两个前端修改文件无新增空白问题 |
| 原业务数据 | 43张业务表完整行值与本轮开始相同，同时等于 V2.3 收尾快照；31库存、53库存动作及原生产/质量/交接历史保留 |
| 原中心配置 | 原 PROD Factory、数据源1、admin 用户、原角色定义与全部原授权关联 XML 快照相同 |
| 新业务库 | 44表含迁移历史；38条成功迁移；仅单位/物料/仓库/库存/单据/行/库存动作各1条，其余业务表为空；无 EXTERNAL/NULL 仓库，无生产报工/质量/交接事实 |

新库的唯一库存动作将实存/可用从0变11，与库存和单据行一致；原 UUID 重放不增加动作。45个既有环境条件跳过不是通过，不用于证明真实 CASE12；本轮真实 MySQL/API/UI 证据独立保存。

## 8. 交付、观察与可控清理

本轮业务代码改动仅现有 ProductionExecutionStatus、ProductionQuantityService、ProductionReportSubmitService；后端两个测试文件适配/新增覆盖。前端仅 ProductionReportManager 及其测试增量调整。未新增业务迁移、业务表、Mapper、通用生命周期框架、数量公式或 ERP 功能。

所有证据在 Git 忽略的 `.local/case12-v231-20261018/`：`preflight.xml`、`flyway.log`、`resources.json`、`fixtures.json`、脱敏 `api.jsonl`、`case12-results.json`、`datasource-error-results.json`、`writable-live-results.json`、`final-binding-and-permissions.json`、`mysql-final-audit.json`、`preservation-final.json`、前后快照、测试日志及截图。凭证/会话另置仅本机权限文件，不在报告中展示。`evidence-manifest.json` 提供交付证据哈希。

保留观察：不可用物理数据源当前响应安全500，而非更精细的503；已证明失败关闭，无默认库回退或事实污染。未承诺生产观测、长时压力、真实 E10 或非法历史来源清理。原用户8081仍是其原运行包；修复只在本轮独立服务验收，构建产物不是部署。

**清理与回滚：** 原 PROD 无业务数据变动，无余额回滚需求。代码回滚只能反向应用本轮增量，两个原已 dirty 的前端文件须参照开始补丁，禁止整仓 reset 或覆盖。新库和 Factory/数据源/账号/角色及已过账测试事实全部保留供复验，未自动删除。后续清理须另行授权：先导出本轮资源与事实，停用专用账号/角色、受控解除新 Factory 绑定，再按正式依赖保护处理平台资源，最终仅在明确授权下归档或删除本轮新 Schema；不得触及 PROD/原角色/旧 V2.3 数据，不以逐行 SQL 删除不可变事实绕过保护。

运行服务的最终停止及原服务保留结果见 `runtime-cleanup.json`。本轮创建的独立验收服务已关闭；新库、Factory、DataSource、账号、角色按用户要求保留。原5173/8081继续运行。任务完成后停止，等待后续指令。
