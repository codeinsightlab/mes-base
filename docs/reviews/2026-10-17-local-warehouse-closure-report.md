# Base MES V1.5 — LOCAL Warehouse Closure 实施与验收

日期：2026-10-17。当前结论：**GO（本轮 LOCAL 仓储范围）**。源码、全量构建测试、指定 MySQL、现有服务 API 与真实浏览器闭环已通过。用户重启后端并明确授权为 admin 配置 SIMPLE_B 专用验收角色；授权和运行结果见 L、N。各测试层级及未验证边界分别记录，不扩大到真实 ERP 厂商或未开放生产能力。

## A. 工作区与并行修改保护

后端起点 `e36ea0ace325616bd584b3d2a1c992b76157438a`，main，原工作区干净；前端起点 `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`，main。前端原有生产、质量、工厂配置及 MasterDetailDocument 等未提交改动全部保留。本轮只新增仓储组件/API/测试并增量修改原库存页/API。起点记录与前端原始 patch 位于 `.local/warehouse-closure-20261017/`。Source Product 未修改；未提交、推送、部署或启动/停止/重启用户服务。

输入：两仓库 AGENTS、个人工程规范、Phase A 审查、V1.4 实施及运行验收记录。FactoryIntegrationSnapshot 与历史 query 路由沿用当前提交，不重新实现工厂模式配置。

## B. 实际调用链

扫码投产 → ProductionInputService 准备 → MaterialIssueSubmissionService 准备事务 → WarehouseIntegration 事务外执行（LOCAL 无外部动作）→ 原确认事务持有 WorkOrder→Execution→Issue 锁 → MaterialIssueInputFacts → LocalStockService 锁库存、核对身份、扣库并追加动作 → Input 确认 → 路径锁定 → Issue/Submission/Extra 保存 → 原事务提交。库存 Owner 的生产方法使用 `MANDATORY`，没有 `REQUIRES_NEW`。

独立仓储入口：LocalStockController → LocalStockService（具名业务事务、READ_COMMITTED）→ 原 WmInventoryMapper 与 WmStockActionMapper。余额与流水同事务，读写均走当前 Factory 的业务库。

## C. LOCAL / ERP 职责迁移

ProductionInputSupplyService 保留模式分派、公共计算、calculationKey 与生产编排；LOCAL 物料/单位/库存/仓库/工单/用料配置查询迁入 `wm.service.LocalProductionSupply`。保留数据库分页、合法空结果、唯一候选语义、配置工单索引及 10,000 配置上界，超过 101 张已下达工单的既有真 MySQL只读测试通过。`T × q / P` 仅保留原公共实现，ProductionQuantityService 未修改。

CONNECTED 继续查询 ProductionErpAdapter，正式动作交 WarehouseErpAdapter，不读取镜像判断领料成功、不扣 LOCAL 余额。缺 Adapter/配置或来源冲突明确失败。

## D. wm_stock_action 安全与模型

复用已执行 V34，未改写历史迁移，未清空历史数据。现有字段覆盖库存来源、物料/基本单位/仓库/批次/库位、双余额前后值、有符号变化、来源业务、UUID、可信操作者、发生与记录时间、原因及原动作引用。

删除生成的 HTTP/Service INSERT、UPDATE、DELETE 和两份可伪造动作写 DTO；保留只读查询与生成持久化基础。移除被手工修改文件的 MES-CODEGEN 归属标记。数据库原 UPDATE/DELETE 不可变触发器保留，真实 MySQL 两种修改均被拒绝。流水列表按记录时间及 UUID 倒序，每次读数据库，避免新增动作后旧列表缓存；不可变详情仍可缓存。

## E. 余额写入规则

- 新库存普通 CRUD 只建立零余额来源，不能携带正余额。锁定物料/仓库引用后核对完整库存身份，跨节点创建由实际引用行锁串行；不新增 UNIQUE，不以裸先查再插宣称并发安全。
- 来源创建后物料、仓库、批次、库位固定；普通编辑只允许状态/备注等描述资料，并在库存锁内检查余额未被客户端改写。此处采用比“有引用后固定”更严格的来源身份规则。
- 有动作或 Input 引用的来源不能删除；非零余额来源也不能删除。旧表单不能覆盖已提交余额。
- LOCAL 库存同步与直接 upsert 均拒绝；CONNECTED 同步保持原 run_token、Factory、所有权、提交前检查和恢复机制。镜像行如被本地动作/Input引用则拒绝覆盖。
- 实存≥0、可用≥0、可用≤实存；小数最多6位、整数最多14位。旧可用为空只按原约定解释为实存，首次正式动作冻结真实前后值。
- 库存锁后重新检查身份和余额。正式 LOCAL 余额增减只在 LocalStockService，镜像同步是独立且受模式保护的写入口；其余额不参与 ERP 正式交易判定。

## F. 期初建账

`POST /api/wm/local-stock/opening`。只允许余额为零、无任何库存动作及 Input 引用的来源，支持合法零数量，必须有依据。UUID 主键和规范指纹负责重放/冲突；不能再次期初覆盖余额。存量正余额保持原样，不自动补造期初或日常入库，也没有对历史余额再次加量。

## G. 原材料入库

`POST /api/wm/local-stock/receipt`。引用已受控建立的库存来源，从服务端取得物料、仓库、批次/库位与基本单位，正数量追加双余额和 RECEIPT 动作。原因字段承载来源/外部凭据/备注；操作者、时间及前后余额不接受客户端指定。不引入单位换算、采购/结算或新的主子表模型，不修改生产事实。

## H. 库存调整

`/gain` 盘盈、`/loss` 盘亏、`/correction` 有符号业务纠错。每项独立后端权限，必须有原因、UUID及当前实存/可用快照。盘亏请求正数量，由业务计算负增量；纠错禁止零增量。快照不符拒绝，绝对余额不直接覆盖。扣减不足回滚，不留下动作。

## I. 正式领料及撤销

MaterialIssueInputFacts 管理冻结事实、确认和 stock_applied 兼容标记；冻结 LOCAL Input 时由 Owner 在原准备事务内短暂锁定并核对库存来源，避免首个引用与零余额来源删除竞争，此阶段不扣库；库存身份与余额变更交 LocalStockService。新正式领料动作 UUID 使用原 Input UUID，来源关联 Issue 与 Execution。重复确认不重复扣减；历史已确认 Input 不重新扣减。

原整单撤销仍经 VoidOperation/UNKNOWN/投产额度预留与 Quantity 校验；LOCAL 恢复核对物料、仓库、批次、库位和单位，恢复量为原领料量减累计合法退料，防止退料后整单撤销重复归还。撤销动作使用原 VoidOperation UUID并关联原 ISSUE 动作。历史 stock_applied 可恢复，但不伪造历史 ISSUE；重复撤销不重复恢复。

原 provider 现在在取消与取消核实时校验。新提交在既有 Submission.command_json 中冻结 provider/binding、Factory 配置版本和连接配置版本；查询/取消契约携带原 Source。旧 JSON 缺 source（包括历史 `{}`）按明确旧契约兼容，不用当前配置补造历史版本。厂商实现仍须按原版本处理/拒绝，未开发真实 ERP 厂商或跨版本凭据恢复机制。

## J. 生产退料

`POST /api/wm/local-stock/return`。必须引用已确认且 stock_applied 的原 LOCAL Input/领料、原库存来源和原 ISSUE 动作；持有相同生产链锁及库存锁，累计退料不得超过原领料数量，重放不再加库存。

本轮保守政策：仅未开工且没有任何报工、质量和交接事实时允许材料退回。已有事实或历史无正式 ISSUE 动作的部分退料受控拒绝，不假定实物归属。退料不修改 output_qty 或产品投产额度；需要减少投入时继续走原领料撤销及 ProductionQuantityService 校验。材料数量不能与产品投入直接相减。

## K. 表、迁移与代码生成

无新业务表、无业务表 factory_id、无新增唯一/外键约束。复用已经按真实 metadata 生成的库存及 V34 动作 Entity/Example/Mapper/XML；新增的是业务命令，不重复手写基础 CRUD。WmStockActionMapper 增加参数化退料 SUM 查询，不新建单表 ExtraMapper。

新增中心正式迁移 `db/system/V35__local_warehouse_permissions.sql`。执行前以真实中心绑定核对 SIMPLE_B→datasource8→`192.168.18.25:3306/mes_base_dev_business_simple_b`，Factory/数据源 ENABLED、模式 LOCAL；备份库存/动作与仓储菜单范围，确认新增权限无冲突。通过项目 FactoryFlyway 正式 migrate/validate，中心 V34→V35 成功；业务库保持 V34。迁移仅登记8个权限，迁移执行前后sys_role_menu总数未变，没有自动给任何角色授权；用户随后明确授权的独立验收角色配置见 L。证据 `mysql-preflight.log`、`verified-target.yml`、`before-*.yml`。

## L. API、权限及 UI

新增独立权限：`business:wm:stock:{opening,receipt,gain,loss,correction,return}`，只读 `business:wm:stock:action:{list,query}`。普通库存 CRUD 权限不能执行这些命令。

前端复用 `/stock/wmInventory`、原 CrudPage 和业务选择器。行操作“库存业务 / 流水”打开 LocalStockPanel，显示来源身份、双余额、动作前后值、增减、批次/库位、单位、来源、操作人、时间、原因和原动作。余额字段只读；普通新增固定零余额。无流水权限明确提示，无命令权限不显示表单；原领料状态/撤销入口沿用 `/issue/mesMaterialIssue`。

用户明确授权后，通过现有角色及用户分配页面建立角色149 `TEST_LOCAL仓储验收`（key=`test-local-warehouse-v15-simple-b`），类型 FACTORY、factoryId=SIMPLE_B，仅绑定上述8个权限，并分配给 admin100。SQL只读审计确认权限集合及数量准确，admin原角色100/148保留；没有授予其他工厂或额外菜单权限。证据 `role149-permissions.yml`、`admin-roles-before.yml`、`admin-roles-final.yml` 及角色分配截图。

所有 ID/decimal 保留字符串，后端使用 BigDecimal。写命令携带余额快照；未知结果冻结 UUID 和原参数，按账号/工厂/来源保存会话请求，重开重查原请求；后续拒绝不抹去先前未知身份。切厂、撤权、关闭与卸载失效迟到响应。当前采用单条库存命令，无主子表，不新增主子页面或通用后端框架。

## M. 幂等、并发与故障恢复

H2 Spring MVC/真实事务测试已覆盖：500→800→600→620→615；同 UUID 重放及不同内容冲突；重复退料/撤销、超额退料、库存不足、旧快照；事实写入口关闭、权限拒绝；扣库后 Input 确认失败、动作插入失败、路径锁定失败均整体回滚。新增不同产品的两个 Execution 同库存竞争，屏障保证两次准备均完成后再确认，只有一次扣减可成功；历史 `{}` Submission 保持可撤销。

UNKNOWN仍保留原身份和预留，不自动重建或外部写重试。真实 MySQL已验证同库存并发同UUID只加一次、指纹冲突、精确净撤销、来源删除/旧编辑拒绝和动作触发器。故障注入证据目前为H2，不冒充MySQL故障注入。

## N. 验收结果与证据层级

| 层级 | 实际结果 |
|---|---|
| 定向后端 | phase-b、phase-b3-c4及edge-tests日志；编译、HTTP、H2、原投产、取消、CRUD、同步token及旧协议通过 |
| 全量后端 | 最终 `mvn -B -ntp verify` 8模块全部成功（10:33:36，`verify-delivery.log`），包含旧JSON兼容、历史恢复及库存身份测试 |
| 前端 | 最终全量57文件、335测试通过（`ui-test-delivery.log`）；vue-tsc与Vite生产build成功（`ui-build-delivery.log`）；仅既有大chunk警告 |
| 打包 | 最终 `/usr/bin/python3 scripts/smoke-packaged.py` 独立随机端口隔离测试进程10项通过、0失败（`smoke-delivery.log`）；未动用户服务 |
| 真 MySQL | mysql-tests/mysql-final日志：真实LOCAL投产回归、101+工单读取及两组仓储闭环通过，未Mock ERP成功 |
| 用户复验数据 | SIMPLE_B库存22为真实浏览器完整闭环，批次 `TEST_UI_WAREHOUSE_20261017_1031`、库位TEST_UI_BIN，Execution227、Issue99，余额615和5笔动作；Input保留产品投入100，stock_applied=true。另保留库存18（Execution220/Issue94）余额615和5笔动作。库存16/17/20为并发及净撤销分支，均明确TEST记录 |
| 运行服务/浏览器 | 现有5173/8081、admin100、SIMPLE_B，真实浏览器创建零来源→期初500→入库300→扫码正式领料200（产品投入100）→原单部分退料20→盘亏5，实存/可用615，5笔真实动作；1280/1440/1920截图已检查，流水可水平查看；最终恢复SIMPLE_B并保留库存详情页 |
| 隔离/权限 | H2两个独立业务库及既有全量回归；真实服务3个既有TEST身份在SIMPLE_B/工厂1请求流水与入库均403（12项）；真实浏览器切到工厂1时原SIMPLE_B数据清除且退出无权限业务页，恢复SIMPLE_B后库存22仍615。撤权中迟到响应由前端自动测试验证，未在运行服务撤销admin角色 |

初始失败均保留在过程日志：共享Fixture未装载Receipt/新Mapper、旧取消Fixture缺provider、旧库存CRUD测试仍期待裸余额写入；已迁移到当前真实入口/规则，没有保留伪通过分支。前端新增测试曾用structuredClone复制Vue代理失败，改为JSON协议快照后通过。

运行超时核实：退料首次请求期间现有后端健康检查亦超时，随后观测进程更替。页面保留原UUID `1f97fd11-d38e-4de2-a41d-5547a2dfcffa`，服务恢复后点击“核实原请求”，只产生一笔RETURN +20，再完成盘亏。未新建UUID重做退料。此证据验证客户端原身份保留与恢复，不据此推断超时的根因。

最终数据库只读复核：库存22的5笔双余额增量和均615；原Issue99 Input产品投入仍100；库存18仍615；既有库存9–14与起点快照一致（身份、数量及实际导出的列值；YAML时间对象不构成精确时间戳备份证明）。前端原17个已跟踪修改文件diff哈希未变。证据均在 `.local/warehouse-closure-20261017/`，主要为 `live-readback.yml`、`live-permission-negative.json`、`parallel-protection.json`。

## O. 明确保留的后续能力

质量放行、正向ProductionReceipt、成品接收入库、Execution正式完工均未开放；原门禁和数量核心保持。既有ReportV2、管理员幂等、QualityV1.3、单节点返工/纠错、Receipt历史差额沿全量回归，不将其历史运行证据冒充本轮重测。无真实ERP厂商、无外部数据库、无通用流程引擎。

## P. 判定

**GO（本轮 LOCAL 范围）：** 库存唯一Owner、不可变动作、原子领料及净撤销、受控期初/入库/调整/退料、独立权限及UI已经实现并验证。新权限通过正式V35登记，专用验收角色经用户明确授权配置；指定MySQL及现有服务/浏览器闭环完成。故障注入与管理员撤权迟到响应的证据分别限于H2/前端自动测试，真实ERP厂商与生产环境均未验证。上述保守退料门禁、历史事实兼容及未开放能力保持明确边界。

已生成正式动作后不得直接回退到可裸写余额的旧后端。若需回滚，暂停新交易、核对动作和余额，采用仍保留本轮写入边界的前向修复；不删流水、不回写V34、不盲重试UNKNOWN。V35只登记权限，不扩大角色授权。
