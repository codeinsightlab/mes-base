# ktg-mes 生产入库专项审查与 Base ProductionReceipt 候选模型

审查日期：2026-10-16。结论：**CONDITIONAL GO**。建议复用现场业务经验，按 Base 的 ProductionExecution / ExecutionPath / ProductionQuantityService 建立入库事实；**不建议直接迁移旧 Receipt 服务、数量算法或 ERP 状态表**。本报告是审查和候选设计，不是实施授权、已批准 ADR 或建表文件。

## 0. 范围、版本与证据等级

- 严格只读读取 Source Product 三仓及 Base 前后端；本轮唯一项目写入是此审查文档。未修改业务代码、配置、SQL、迁移、生成产物；未调用 Codegen，未执行数据库命令、ERP/API 写入、构建、测试、提交、推送、部署或服务启停。
- 工厂已使用该产品是任务提供的背景。本次取得的是**当前工作区源码事实**，不能证明某个工厂部署了哪个入口或该入口已通过现场验收。下文“有调用者”指静态前端调用、Controller 或 Service 引用，不等同线上流量。
- 开始已检查五仓的路径、Git status、分支和相关文档；Source 后端 `master`，仅 `ktg-admin/src/main/resources/application.yml` 已脏；Source Web `master`，检验模板页面已脏；UniApp `feat/multi-factory-isolation`，`config.js` 已脏。未读取或输出这些配置中的凭证。
- Base 两仓 `main`，已有 ProductionReport、Quality、Routing、领料、测试和报告等并行改动；审查包含这些未提交源码，不把它们归为本轮成果。关键源码指纹见附录。未替其他会话验收或修改工作成果。
- 依据 Source [AGENTS.md](/Users/user/work/heri/ktg-mes/AGENTS.md:10)，`ktg-wheel` 已退出代码审查依据。本轮结论不使用其实现；前端仍发现 wheel 地址，作为兼容/客户分流线索记录，不能据此评价当前 ktg-mes 后端。
- 已搜索 Base `docs/` 内容及文件名，检查 `doc/`、`knowledge/`（不存在），没有同主题 Receipt 专项报告；参考生产身份、报工、质量、库存已有报告，故新建本文件。旧报告只作时间线，当前事实以本轮读取源码为准。

| 仓库 | 审查时 HEAD（工作区另有改动） |
|---|---|
| `/Users/user/work/heri/ktg-mes` | `42a81cc577dc77b05a6a67f6be0a9466e40784e1` |
| `/Users/user/work/heri/ktg-mes-ui` | `e5252afef962f31544acafc24421083c7e41e55d` |
| `/Users/user/work/heri/heri-uni-app` | `f109f7937fff51d4395d574890b57f3280d10d5f` |
| `/Users/user/work/lixin/mes-base` | `4679154eccb61444f1c0f795c9620a54ec6e207d` |
| `/Users/user/work/lixin/mes-base-ui` | `02a6554baff657f6c8dfe42cebdcbc9e5f863473` |

**重要快照差异：**任务描述为质量数量预留。本次 Base 的 `ProductionQuantityService` 已注入 `ProductionDefectQuantityQuery`，按最新非 VOID 不良版本聚合，再计算 `reportedQty - defectQty`，节点 `qualityIntegrated=true`（B1、B2）。这属于正在变化的未提交工作区能力，不能称为已部署、质量已放行或本轮验证通过。现有 Phase 1/V2 报告中的“未接入”与新源码并非同一时间点。

交付前新增的并行 [Phase 2B 报告](/Users/user/work/lixin/mes-base/docs/reviews/2026-10-16-production-quality-phase2b-quantity-integration-report.md:123) 已记录数量集成、自动化和指定MySQL测试，结论为“实现与隔离验收GO、正式启用NO-GO”，还需存量报工口径核验及用户运行中HTTP/UI复验。本轮只阅读其报告并核对对应源码，未独立重跑其测试，不把它的验证记录计为本轮执行结果。审查结束时四份关键源码指纹均未变化；该并行报告也不是本轮创建。

## A. 源系统真实业务与完整调用链

### A1. Web 新确认链：本地申请 → ERP 5901 → 本地入库确认 → ERP 5A00

实际 Web 入口既有独立列表，也嵌入工单确认中心：

1. `src/views/mes/pro/inboundConfirm/index.vue`，以及 `workorder/confirmCenter/inboundConfirm.vue`；后者 `confirmMaterialIssues`、`submitForm`、`handleApprove` 在169、186、197行。`workorder/index.vue:307–308` 导入申请与入库确认组件。共享 `InboundConfirmList.vue` 只集中列表和按钮规则。
2. `src/api/mes/pro/inboundConfirm.js` → `/mes/pro/inbound-confirm`：POST create、PUT `/{id}/approve`，对应 `ProductionInboundConfirmController#create/approve`（55、70行）。后端有独立 `add`、`approve`、`remove` 权限；未要求申请人和审核人必须不同。
3. `ProductionInboundConfirmServiceImpl#create:140`：工单存在；至少选择一张已审核、ERP 身份存在且尚未绑定的领料确认；全部属于同一工单。生成 `IB-UUID`，写 `ProInboundConfirmMapper#insert` → `pro_inbound_confirm`；再条件绑定 `pro_material_issue_confirm.inbound_confirm_id`，检查每次更新为1。
4. `approve:272`：ExtraMapper `claimPending` 将 0→3；`buildRequisitionForm:383` 取工单号、手填入库数量、流程卡、产品批次；读取 ERP MO 第一条/第一产品和工作中心匹配的第一员工；`MoReceiptRequistionManagerImpl#createMoReceiptRequistion:71` → `OApiHttpUtils.insertService` → `ywms.mo_receipt_requistion.create`。申请数量直接映射 `ACCEPTED_QTY / REQUEST_QTY / REQUEST_INVENTORY_QTY`。
5. 返回单号后写本地 ERP 单号；调用 5901 confirm，再 read 取单头不可变 ID，更新本地1已通过、审核人和时间；Controller 返回 `AjaxResult.success(VO)`。这里是**入库申请**完成，不是正式库存入账。
6. 正式入库由 `workorder/confirmCenter/inboundReceiptConfirm.vue#submitForm:112`（独立 `inboundReceiptConfirm/index.vue` 也复用它）→ `src/api/mes/pro/inboundReceiptConfirm.js` → POST `/mes/pro/inbound-receipt-confirm` → `ProductionInboundReceiptConfirmController#create:60`。
7. `ProductionInboundReceiptConfirmServiceImpl#create:119`：读上游已通过5901并核对身份；拒绝已被任意入库确认引用的申请；检查仓库存在、`inboundFlag=true`、本地累计不超过申请量。生成 `IR-UUID`，`ProInboundReceiptConfirmMapper#insert` → `pro_inbound_receipt_confirm`，**随即调用 approve**，并非等待另一个独立审批动作。
8. `approve:159` 条件抢占本条记录；重新读5901单头身份、已审核未关闭明细；校验本地申请额度和 ERP 返回 `ACCEPTED_QTY` 合计；`allocateReceiptDetails:298` 分配本次数量；读取工单产品特征；`MoReceiptManagerImpl#insertMoReceiptWithExplicitLotByMoReceiptRequistion:228` → 5A00 create，随后显式 `confirmMoReceipt`、read 单头ID，再更新本地1和外部身份并返回VO。**没有调用本地库存事务服务。**

证据：S1–S8。确认 Service 的单表 Mapper/XML 分别对上述两个本地表读写，ExtraMapper 承载抢占、下游引用和累计；没有一套隐藏的 Receipt 明细持久化。ERP create/confirm/read 是外部 HTTP，不能画成和本地 SQL 的原子事务。

### A2. 旧 PDA 普通申请链：扫码材料/流程批次 → ERP 5901

`pages/work/index.vue:72` 导航 → `pkg-issue/pages/moReceiptRequistion/index.vue`，已在 `pages.json:166` 注册。`getwork:224` 扫码拆材料品号/批号，调用 `getTicketInformation`；`getApplicationNumber:335` 查询可申请量；`submit:173` → `api/moReceiptRequistion/index.js#insert5901` → POST `/mes/mobile/in-bound-requests/createMoReceiptRequisition`。

`InBoundRequestsController#createMoReceiptRequisition:106` → `InBoundRequestsMobileServiceImpl#createMoReceiptRequisition:138`：读 ERP 工单、第一产品、工作中心员工；读 WMS 条码规则；按1004等规则处理批次，必要时创建 ERP `ITEM_LOT`；Manager create5901、confirm5901，失败尝试删除5901；返回 ERP success 列表。**该普通路径未先创建本地 `pro_inbound_confirm` 或 `pro_inbound_receipt_confirm`。**

工单定位读 `ErpIssueReceiptMapper` 的 ERP 领料来源，再用本地 `ProWorkorderMapper` 找工单；额度读取本地 `pro_buckle / pro_reporting_work / pro_route_process` 和 ERP5901查询。具体数量见 C2。Controller 此方法未见 `@PreAuthorize` 和 `@Validated`；是否有网关或全局权限约束未通过运行配置证明，不能说匿名可调用，也不能说具备等价业务权限。

前端235行可跳 wheel 页面，wheel API 使用 `/wheel-mobile/...`，属于保留的客户分流线索；按 Source 规则不扩审其后端。证据：S9、S10。

### A3. PDA 调拨审核页面中的“入库审核”：直接 ERP 5A00

`pkg-issue/pages/confirmTransferDoc/index.vue:299` → `api/transferDoc/transferAudit.js#createMoReceipt` → POST `/mes/mobile/transfer-audit/createMoReceipt` → `TransferAuditController:48` → `TransferAuditMobileServiceImpl#createMoReceiptAndConfirm:56`。

查询时 `getApplyStorageInfo:48` 读已审核、未关闭5901；提交时读登录人部门对应车间，使用**前端提交的5901明细、数量、员工/部门/工作中心字段**，调用 `MoReceiptManagerImpl#insertMoReceiptAndConfirmByMoReceiptRequistion:198` → create5A00 → confirm5A00 → 返回列表。未见提交时重新执行新 Web 链的本地源单、Execution额度和身份校验；普通 Manager 可为缺失产品批次生成编码。无上述本地确认表写入，也无本地库存入账。

尤其198行方法在209行直接调用 HTTP confirm，没有经过 `OApiResponseUtils.getSuccessOrError` 检查其返回业务结果；成功返回不能充分证明审核成功。证据：S11、S12。

### A4. 合并流程卡入库：多次5901 → 本地合并表 → 5A00

`pages/work/index.vue:164` → `pages/processmerge/processmerge.vue`（根 `pages.json:10`）→ `api/processmerge/processmerge.js`。实际调用 `batchInsertDocNo5901`（页面868行）、`mergeInsertDocNo5901`（720行）。

`OapiMoReceiptRequistionController#batchInsertDocNo5901:66` 循环调用 `OapiMoReceiptRequistionServiceImpl#insertDocNo5901:92`；每次先ERP5901创建/审核，再由代理调用 `insertProReceiptMerge:184`，`ProReceiptMergeMapper#insert` → `pro_receipt_merge`。`mergeInsertDocNo5901:240` 按材料映射产品和流程卡查合并记录，构建多行5A00，调用旧 `MoReceiptServiceImpl#addMoReceiptAndConfirm(..., true)`（此参数使该方法不审核），随后显式审核并将合并记录 `status=1`。

这条路径能在旧模型中合并多个工单行，但不具有 Base Execution 身份。服务 `getProBuckleInfo:396` 只取所查报工最大 `recordId` 的数量回填所有扣料行，**不是末道有效产出的可靠聚合**；提交也未统一使用 A2 的额度算法。合并选择与查询未见统一的已处理条件抢占，存在重放风险。证据：S13、S14。

### A5. 断阶工艺的报工自动入库

不能漏掉报工里的外部副作用：

- Web `controller/web/WorkReportController:116/134` → `ProReportingWorkServiceImpl#insertProReportingWork:106`，`nextProcess==null && isForgeJoiningRoute(routeId)` 时，在131–152行构建5A00、创建/审核，再写 `RouteBrokenStepMapper#insert` → `pro_route_broken_step`，记录入库外部号和断阶前后材料/产品批次、本次报工数量。
- PDA `controller/mobile/WorkReportController:79` → `WorkReportMobileServiceImpl#saveSelfWorkReport:252`，275–305行执行同类末道自动5A00及断阶记录。普通报工全量完成提示在308行以后，自动入库分支在此之前，因此不能声称“必须全部报满才入库”。
- `ProRouteServiceImpl#isForgeJoiningRoute:40` 实际读 `pro_route.forge_joining_flag`，不是通用完工规则。`MoReceiptManagerImpl#addMoReceiptAndConfirm:111` 在193行同样没有解析 confirm 的业务返回；旧 `MoReceiptServiceImpl:155` 亦如此。
- 普通批次完结提示来自报工材料剩余与 `countStatus`，可能更新一批报工状态并提示“该批次可申请入库”；它不等于独立完工审核，也不证明库存入账。

证据：S15、S16、S12。此客户/工艺特例不迁移为 Base 报工默认副作用；Base 当前正式报工不会自动 Receipt 或 COMPLETED。

### A6. 本地库存产品入库：与 ERP 链独立

Web `src/views/mes/wm/productrecpt/index.vue` / `line.vue` → `src/api/mes/wm/productrecpt.js` / `productrecptline.js` → `/mes/wm/productrecpt` / `productrecptline`。

`WmProductRecptController#add:102` → `WmProductRecptServiceImpl#insertWmProductRecpt:69` → `WmProductRecptMapper` → `wm_product_recpt`；明细类似写 `wm_product_recpt_line`。`execute:183` 读取主单/明细，`getTxBean` SQL按 `recpt_id` 联主明细，取明细产品、批次、仓库、数量和主单工单，映射来源单据/行。

随后 `StorageCoreServiceImpl#processProductRecpt:266`：线边虚拟库减库存，目标库加库存；线边出库设置 `storageCheckFlag=false`，明确允许报工未及时情况下的负库存。每条库存事务调用 `WmTransactionServiceImpl#processTransaction:41` → `WmMaterialStockMapper#load/update/insert` → `wm_material_stock`，及 `WmTransactionMapper#insertWmTransaction` → `wm_transaction`。Controller 最后把入库单设 FINISHED，返回 AjaxResult.success。无 ERP 请求。

后端 execute 未检查初始单据状态或使用条件更新抢占；`processTransaction` 只有 JVM `synchronized`，未按来源单行去重，顺序重放也不能防止。删除主单只删明细/主单，未见库存逆向事务。前端隐藏完成单操作不能补足此边界。证据：S17–S19。

### A7. 查询、打印与版本判断

工单页 `moReceiptRequistion/moReceiptRequistion.vue` / `moReceipt/moReceipt.vue` 经对应 `src/api/mes/erp/*.js` → `/mes/erp/moReceiptRequistion/list` / `moReceipt/list` → ERP Query Service / `ErpMoReceiptRequistionDMapper`、`ErpMoReceiptDMapper`，SQL读 ERP `MO_RECEIPT_REQUISTION(_D)`、`MO_RECEIPT(_D)`、`ITEM / ITEM_LOT / WAREHOUSE`。这是外部单据查询，不是新 MES Receipt 创建入口。`/mes/print/moReceipt(Requistion)/{docNo}` 是打印读取；timedtask 中对应实体/Mapper不是新的生产入库命令实现。

新旧链均仍有静态调用者，不能仅因有新确认中心就认定旧PDA、合并、自动入库和本地库存链已废弃。现有SQL `production-confirm-v2-20260827.sql` 体现5901与5A00确认的拆分历史；本次以当前分离 Service 为准，不把旧字段/旧迁移推成现行统一流程。实际各厂菜单、权限分配、入口流量、ERP版本及旧链下线情况未知。证据：S20。

## B. 现有数据结构与字段归属

以下为当前 Entity/Mapper和仓库DDL定义，**未连接数据库核对物理结构/数据**。本地关联是代码/字段逻辑关系，不宣称全部有外键。完整字段以链接文件为准，表中列入业务相关列。

| 表/来源 | 关键字段与关系 | 审查判断 |
|---|---|---|
| `pro_inbound_confirm` | `id,request_no,workorder_id,workorder_code,item_code,route_id,process_id,xtransfer_no,item_lot_code,warehouse_code,inbound_qty,confirm_status,approve_by,approve_time,remark,create/update_by/time`；另有 `erp_requisition_doc_no/id,erp_sync_status,error_message` | 每行一份5901申请，无本地产品明细表。申请量与外部状态混在核心行；`warehouse_code`字段存在，但当前create/build表单未赋该字段，不能假定已选仓入5901 |
| `pro_inbound_receipt_confirm` | `id,request_no,workorder_id/code,source_inbound_confirm_id,receipt_qty,warehouse_code/name,confirm_status,approve_by/time,remark,create/update_by/time`；`erp_receipt_doc_no/id,erp_sync_status,error_message` | 每行关联一份申请；产品/批次靠上游；没有 `execution_id`、独立入库发生时间（仅审核/创建时间）、不可变冲销链 |
| `pro_material_issue_confirm` / `_detail` | 主单 `workorder_id/code,product_code,route_id,xtransfer_no,output_qty,inbound_confirm_id,confirm_status,erp_issue_doc_no/id`；明细 `material_issue_confirm_id,material_item_code,material_lot_code,qty_per,issue_qty,unit_code` | 一份申请可绑定多份领料；每份领料只能被一份申请占用，不是严格领料与入库全局一对一；材料明细不是产品入库明细 |
| `pro_receipt_merge` | `id,workorder_code,product_code,item_code,item_name,specification,item_lot_code,attr1,xtransfer_no,receipt_no,quantity,uuid,merge_doc_no,status,create_by/time,update_time` | 每行保存5901号和合并分组；`receipt_no`实际5901号；`item_lot_code/attr1`还有截断分段编码语义，不能当通用生产批次 |
| `wm_product_recpt` / `_line` | 主单 `recpt_id,recpt_code/name,workorder_id/code/name,item_id/code/name,specification,unit_of_measure,warehouse/location/area_id/code/name,recpt_date,status`；明细 `line_id,recpt_id,material_stock_id,item_id/code/name,specification,unit_of_measure,quantity_recived,batch_code,warehouse/location/area...,expire_date`，另有审计/备注/attr字段 | 真实主子单，库存事务实际以明细产品/数量/仓位为准；主单重复产品仓位不代表业务一致性已校验。无Execution/Report引用 |
| `wm_material_stock` / `wm_transaction` | 库存现有量；事务来源类型/单号/单ID/行ID、物料/批次/仓位、正负数量、关联事务、时间/工单 | 本地库存权威及变更记录，不能与ERP链合算成一套已入库量 |
| `pro_route_broken_step` | `inbound_doc_no,before_work_id,before_route_id,before/after_item_code,before/after_lot_code,storage_number`等 | 特定断阶转换事实，非通用Receipt或Execution身份 |
| `pro_workorder` / `pro_buckle` / `pro_reporting_work` / `pro_reporting_work_material` / `pro_route_process` | 工单 `quantity,quantity_produced,quantity_changed,quantity_scheduled,status`；扣料 `quantity,material_doc_no,item_code,item_lot_code,product_code,route_id`；报工 `reporting_work_quantity,workorder_code,route_id,process_id,item_code,xtransfer_no,status`；材料余量；路线前后工序 | 入库查询的来源；流程卡/材料批次与工单承担隐式身份，不能迁入为 Base 第二身份模型 |
| ERP `MO_RECEIPT_REQUISTION / _D` | 单头ID、DOC_NO、工作中心、员工部门、ApproveStatus/CLOSE；明细ID、MO/产品行来源、ITEM/LOT/XTRANSFER、REQUEST_QTY、ACCEPTED_QTY、RECEIPT_QTY、单位/仓库等 | ERP申请，不等于实际入库；字段名语义要以真实ERP契约核对 |
| ERP `MO_RECEIPT / _D` | 单头ID/DOC_NO、日期/交易日、组织/工作中心/部门员工；明细ITEM、ITEM_FEATURE、LOT、MO、5901父单号/行号、ACCEPTED_QTY/INVENTORY_QTY、仓库/库位、审核状态 | ERP正式入库外部身份与入账结果；供应商字段不进入MES核心 |

结构证据：S6、S8、S14、S17、S18、S20、S21。`production-confirm.sql:50–106` 中 request_no有唯一索引，但 `source_inbound_confirm_id` **仅普通索引**；request_no由服务端每次生成UUID，不能据此声称客户端重试幂等。主表两个确认记录不是“同一入库单的头与明细”。

MES可保留的语义：生产执行、产出产品、产品批次、交接数量/单位、目的仓库、发生时间、操作/确认人、事实类型、原事实引用、原因。工单/确切工艺/领料/报工应通过Execution及历史追溯取得；必要快照只供审计，不成为新权威。

ERP专有语义：5901/5A00、ERP单头/明细ID、审批/关闭码、组织厂代码、工作中心展开字段、部门生效日期、产品特征/来源RTK、单别和外部库存单位。应留在供应商映射/外部执行记录；不能把 ERP `ApproveStatus` 直接当 MES Receipt 状态。

## C. 准入、数量、路径、状态与取消规则

### C1. 各入口的实际准入并不统一

| 问题 | 源系统实际答案 |
|---|---|
| 谁能创建 | 新Web按独立add权限；申请审核按approve。入库create只有add检查，却在Service立即approve生成ERP5A00，approve权限只管独立重试端点。PDA提交端点未见方法级权限；需运行授权核对。不是可证明的“固定仓管角色” |
| 必须先领料 | 新Web必须至少一份同工单已审核/ERP存在领料；PDA正常扫码定位依赖投料/ERP领料；自动入库依赖报工链已有规则。本地wm入口没有此条件。不能提升为统一Receipt规则 |
| 所有工序报完/独立完工确认 | 新Web不读报工，不验证末道、全工序完成或工单状态；PDA额度允许未完结/完结/部分入库报工。报工自动入库在特定末道按本次数量发生。没有跨入口统一独立完工审批 |
| 审核或审批 | 申请有单步审核动作和ERP审核；5A00新建即执行审核，PDA也直接审核。没有证明多级审批、申请审核分人或质量放行 |
| 部分/分批 | PDA可输入部分申请、直接ERP可多次；新Web一申请只能被一份Receipt确认引用，即使仅入60/100，其余40也无法再从该申请选择。累计SUM存在不代表真正支持拆分入库 |
| 重复入库 | 同一本地确认ID有状态抢占；跨新建请求/旧入口没有统一Execution累计锁和客户端幂等。部分入口允许业务分批，但重试重复与业务分次没有可靠区分 |
| 取消/冲销 | 新Web为物理删除，可能先ERP删除/反审核再删除并重读核对，申请有下游时拒绝；不是不可变库存冲销。本地wm删除未见库存冲回。没有统一正负冲销流水或已发货库存可逆性证明 |

### C2. 数量权威和算法

1. **计划量**：`ProWorkorder.quantity`；另有 `quantityProduced/Changed/Scheduled`。审查到的入库服务没有以它们统一控制上限，不能将 `quantityProduced` 命名当实际入库累计。
2. **“批次数量”**：扣料 `ProBuckle.quantity`、领料 `output_qty`、流程卡与报工材料余额各承担不同含义；不是独立冻结的Execution目标。新Web默认入库申请量是所选领料 `outputQty` 相加（页面175行），可编辑，服务端未据此建立报工上限。
3. **PDA报工累计与可申请量（源码公式）**：

   `R = Σ pro_reporting_work.reporting_work_quantity`，限定产品+`xtransfer_no`+指定报工状态，再取所选路线 `next_process_id=0` 查询结果的第一工序。

   `A = Σ ERP5901查询返回的 ACCEPTED_QTY`，查询条件为第一份报工的工单、产品、流程/批次，固定第1页30条。

   **`可申请量 = R − A`**。这里减的是5901申请数量，不是5A00实际入库量；报工查询最初传 `workorderCode=null`，多工单共产品/流程卡可能混合，再用第一条的工单扣申请；路线也取第一条。源码未减本地Receipt、不良/报废，也未钳制负值。查询超过30条时可能少扣。前端qty validator检查显示的额度，**提交Service没有重算这个额度**，请求绕过/查询后竞争均存在风险。证据S9、S10、S7。
4. **新Web申请**：`inbound_qty`为请求正数；仅校验领料关系/路线工序配对；未算末道有效产出、累计申请、工单计划上限、质量扣减。可多份领料，但不可重复选择同一份、每份不可再次绑定；不是按实际产出额度分配。
5. **新Web正式入库**：create/approve检查 `其他非REJECTED本地receipt_qty + 本次数量 ≤ source.inbound_qty`；另要求 `本次 ≤ Σ ERP5901明细ACCEPTED_QTY`。后者在代码被命名available，但DTO只直映 `ACCEPTED_QTY`，**没有以 `ACCEPTED_QTY − RECEIPT_QTY` 计算剩余**；ERP查询在部分入库后是否返回剩余、CLOSE怎样变化，需要真实响应证明，不能把变量名当契约。FAILED/PROCESSING也占本地SUM，删除才释放；并发create没有锁同一上游，只有每记录抢占。证据S5、S6、S7、S8。
6. **合并/自动/本地库存**：分别使用合并行 quantity、本次末道报工量、入库明细 quantity_recived，没有同一套全系统上限。合并不是可靠末道聚合，自动入库也不是全批剩余入库量。
7. **完工、不良和报废**：普通报工完结提示依据材料余量状态；“末道存在报工”与“批次完结”并不等价。此次所有入库额度/命令路径未见统一读不良报废事实并扣减。旧报工前置数量已有材料/上一工序限制，但不能据此推定入库端实现了质量净产出算法；更不能再次扣每工序总不良。

因此，题设 `可入库 = 末道有效产出 − 已入库` **不是旧系统各入口共同实现**。只在Base建议中采用经过定义的等式，不能把它写成Source已验证规则。

### C3. 工艺与完成

- PDA按 `next_process_id=0`、`get(0)`定位终点；自动入库按 `findNextProcess==null`。没有Execution锁定路径、可选分支终点身份或多终点汇合数量契约。
- Web申请只在 routeId/processId都非空时校验它们配对；不是确认它们属于工单执行版本、不是末道校验。可空也意味着不能声称有强工艺门禁。
- 旧完结标记能写整组报工状态、特定工艺能报工即库存入库；没有独立Base Execution生命周期。把此逻辑复制到Base会混淆生产事实、生产结束与库存交接。
- Base `ExecutionPathValidator` 明确校验一个root→sink连续路径（B3），可从已选路径取得终点；Routing DAG本身不意味着已支持并行多终点产出合并。Receipt V1只消费当前已锁定单路径结果；超出此边界先返回不支持，不在Receipt中设计DAG汇合算法。

### C4. ERP一致性与失败窗口

- **积极经验**：新确认链保存ERP业务号+单头不可变ID；重读区分不存在、核对失败、身份不一致；删除不确定时重读，不盲删其他身份；部分命令解析ERP业务success/error，值得复用这些规则。
- **5901事务风险**：申请 `approve` 以 `@Transactional(rollbackFor=Exception.class)` 包住抢占、HTTP、单号回写；catch写FAILED后再抛出Runtime ServiceException。按正常Spring事务语义，这些状态/外部身份更新也会回滚。ERP成功后read/本地提交失败时，可能回到PENDING而ERP单据仍存在。这里只是源码推理，未做故障注入；现有补偿并不能证明关闭全部窗口。
- **5A00差异**：Receipt create/approve未标事务（delete有事务），因此与5901不一样，逐步保存可能留下FAILED/已知ERP号，禁止有ERP号时直接retry。若HTTP create在返回单号前超时，外部可能成功而本地只留FAILED且无号；再次approve可创建另一单。先查源单再insert没有跨请求原子抢占。
- **旧链业务响应漏检**：S12中的直接confirm没有校验ERP执行码/success/error；`OApiHttpUtils#post` HTTP200只反序列化，非200还可能返回null。不能把HTTP200或无异常当入账。
- **非完整幂等**：本地每次生成UUID而非复用客户端请求；外部号模板 `5901/5A00-日期001` 也不是可证明的稳定业务幂等。不能假定ERP自动改号/自动防重策略。合并循环部分成功无整单回滚；请求未知、人工重试与成功后本地失败未形成统一对账。
- **取消**：ERP 5A00删除可触发反审核再删；5901有下游保护；本地物理删除会丢事实，且ERP撤销已成功、本地删除回滚仍可能不一致。未证明负库存、已转出/已用库存的退回规则。
- **库存权威**：ERP链未调用本地库存；本地wm链未调用ERP。二者必须明确工厂实际权威，不能双记增加两份库存。

## D. 与 Base 的差异与复用分级

| 维度 | ktg-mes实际 | Base当前源码能力 | 建议 |
|---|---|---|---|
| 生产身份 | 工单+产品/材料批次/流程卡，多处第一条推导 | ProductionExecution独立ID、工单/产品/确切Routing | 只以execution_id定位，不恢复ProductionBatch |
| 数量依据 | PDA报工−申请；Web申请手填；合并行/本次报工/库存明细各异 | ProductionQuantityService逐已选节点；报工不可变流水与订正 | 复用统一结果，仅新增Receipt自身累计，禁止复制报工聚合 |
| 工艺路径 | next_process_id=0或findNextProcess，缺选路身份 | 已选/锁定ExecutionPath；单root→sink | 终点以当前锁定路径的routing_node_id确定，不按显示顺序/工序名称猜 |
| 领料关联 | 领料被单次占用，或材料批次查工单 | MaterialIssue→Execution，多材料明细；开工要求成功确认领料 | 追溯经Execution；不重复建立Receipt逐材料绑定或强一对一 |
| 不良扣减 | 入库端无统一计算 | 未提交源码已注入latest非VOID不良并验证上下游 | 核实集成/放行口径后复用；不重复扣减，不宣称质量验收完成 |
| 入库事实 | 申请/正式ERP确认/断阶/本地wm多套 | 本轮搜索未见ProductionReceipt表或服务 | 建独立不可变生产交接事实 |
| ERP对接 | 5901/5A00深嵌MES，部分链只返回ERP数据 | 领料已有Port/Adapter、UNKNOWN/LOCAL_ONLY区分 | 复用原则与现有授权基础，不直接扩展领料DTO冒充Receipt协议 |
| 本地库存 | wm事务/线边库转成品库 | WmInventory为库存CRUD/外部同步；未见Receipt入账流水能力 | 不把库存表普通PUT当本地库存入账闭环 |
| 管理员订正 | 状态/数据可改，删除不保证库存补偿 | REPORT/ADJUSTMENT、受控动作、共同锁 | Receipt冲销需真实库存动作；数量订正不改原事实 |

**可直接复用（语义/现有Base能力）：**分批交接的现场需求、区分申请与实际入库、目的仓库、外部单号与不可变ID核对、专用权限与删除引用保护经验；Base现有Execution/选路/统一数量/用户身份/分库事务和锁顺序可直接作为开发基础。没有建议原样复制的旧端到端入库Service。

**修改后复用：**扫码→定位→查看额度→填数量/批次/仓库→确认→结果核对的交互；主子页面如后续确有必要则用现有MasterDetailDocument；ERP查询/创建/确认/撤销的供应商知识应进Adapter，并补业务结果、幂等、UNKNOWN核对和外部身份映射。

**不应迁移：**材料批次代生产身份、旧Batch/BatchRouteNode；领料只能引用一次的额度模型；报工/质量第二聚合；流程卡编码与厂商单别/固定日期/默认批次；报工内自动库存；ERP状态挤入核心；物理删除正式事实；JVM锁、前端按钮、随机新UUID充当幂等；旧ExtraMapper分层习惯不能覆盖Base规则。

**当前无法确认：**各厂实际使用哪些旧入口、真实ERP ACCEPTED_QTY和部分入库/撤销语义、ERP防重/单号策略、现场质量放行、是否必须仓管审核/分人、产品批次形成规则、源库实际约束/触发器、并发及超时实际表现、当前用户服务是否已加载新Base质量代码。均不以本次静态结果代替运行证明。

## E. ProductionReceipt V1 最小候选模型（逻辑字段，不生成Schema）

### E1. 推荐先一张事实表，而不是无条件头+明细

默认一个Receipt事实对应 **一个Execution、一个产出产品批次、一个目的仓库、一次确定数量的实物交接**。一个Execution可有多条Receipt，60+40自然表达，不因领料只发生一次就限制后续入库。V1暂不把多个Execution合成一张单；有真实跨Execution签收/整单审批/统一ERP单头需求后，再决定主单+明细，不能仅因ERP有单头明细就先造两表。

候选名 `mes_production_receipt`；以下是供评审的最小字段语义，**不是DDL、真实metadata或已定模型**：

| 字段组 | 候选字段/类型 | 来源与规则 |
|---|---|---|
| 事实身份 | `id bigint`主键；`receipt_no varchar(64)` | 服务端生成可展示单号；不可因单号可读就宣称并发唯一 |
| 执行关联 | `execution_id bigint`必需 | 当前业务库Execution；工单/产品/确切工艺经其取得，不由客户端再传另一权威 |
| 产出批次 | `product_lot_code varchar(100)`可空或由物料追溯规则要求 | 与Execution分开；不得由原材料批次、流程卡或19113001默认填充。一次Execution可拆成不同产品批次的Receipt |
| 目的/数量 | `warehouse_id bigint`；`quantity_delta decimal(20,6)` | 单位与Execution产出产品单位一致（必要审计快照）；正数收货、负数冲销；精度最终对齐现有产品/库存真实metadata，不能静默转换四位库存精度 |
| 原事实 | `record_type varchar(20)` RECEIPT/REVERSAL；`reference_receipt_id bigint`可空 | 正向事实无引用；冲销引用原事实、校验同Execution/产品/仓库/批次，累计冲销不超过原事实。错误归属不修改原记录，受控冲销后重录 |
| 可信人和时间 | `operator_user_id bigint, recorded_by_user_id bigint, occurred_at datetime, recorded_at datetime` | 操作/实际交接时间与录入时间区分；录入身份取认证sys_user；姓名快照按确切审计需要加，不建Personnel |
| 原因 | `reason varchar(500)` | 冲销/补录必需；正向备注可选 |

不放 `factory_id`、可维护的 `reported_qty/defect_qty/remaining_qty`、ERP单号/状态、第二执行路径或单一 `material_issue_id`。路径终点与数量核验上下文如需留审计，可保存只读快照或既有审计记录，但不允许Receipt快照反推当前数量。

事实写入后不普通UPDATE/DELETE。V1默认不含申请草稿/多级审批表；如业务确认必须“先申请、再实收”，不能把申请写入正向入库事实，应另行定义申请/预占与实际事实的边界，模型随之调整。已撤销历史仍保护Execution和关联主数据，不能因净量为0就删除身份。

### E2. 事实与库存入账、ERP Extra

- Receipt表示已确认的生产实物交接事实；**库存权威另有入账结果**。仅记录意图/等待ERP不能标成已入库事实；UI分别展示“交接已记录”“库存未接入/待入账/已入账/结果未知”。若现场把“生产入库”严格定义为库存已记账，则第一阶段只能称交接登记，正式Receipt完成必须等第二阶段入账，需在实施前决定。
- 阶段1可以保存人工确认的实物交接事实并明确 `LOCAL_ONLY/未执行库存动作`；这不是本地库存增加成功，也不是ERP成功。不为此预建供应商框架。
- 阶段2有真实ERP需求时建立Receipt专用外部映射/执行记录，候选 `mes_production_receipt_erp_extra` 保存 `receipt_id, provider/binding, external_doc_id/no, external_line_id, external_status_raw, operation_id, result, checked_at, safe_error` 等。支持1:N外部步骤/重试/撤销时不要硬设单行Extra；映射不是用来替代可靠的提交操作身份/结果记录。
- 对外发出前持久化操作身份和数量占用，事务提交后再发HTTP；明确SUCCEEDED/REJECTED/UNKNOWN。UNKNOWN保持占用并核对，不能释放余额后再创建新单。确定失败才释放意图占用；确定成功只把该占用转换为实际Receipt/入账结果，不能“实际量+原预占”双扣。
- 库存已实际变更后，Receipt冲销必须对应库存权威允许的真实逆向动作并核对成功；不能单纯追加负数释放额度却未退库。业务信息勘误与真实冲销分开，不用正负数量伪造库存回退。

### E3. 并发/幂等的建模约束

V1所有写入、反向、报工订正和影响产出的质量订正需共享当前 `WorkOrder → Execution` 锁和具名business事务；沿用已有锁机制，不新增JVM锁。读完当前Quantity、Receipt累计，再验额度并写入，检查影响行数。串行化“不同请求”的数量竞争与幂等“同一请求重放”是两回事。

项目禁止擅自新增非主键UNIQUE。候选可使用**请求UUID作为主键的操作表**（同key保存payload摘要、动作、主体、结果；已有报工方案只能参考，不能直接复用其report专用表），或经用户决定新增业务唯一约束。哪一种需在正式模型中明确；本轮不新增约束，也不以无约束的先查再插宣称安全。多Execution时需固定锁序，V1不为假想需求扩展。

## F. 数量与一致性：仅复用一个产出权威

### F1. 当前可复用契约与明确禁用项

`ProductionQuantityService#getExecutionQuantities(executionId)`：校验确切Routing和已选路径，读取按Execution/node聚合的报工净增量、latest非VOID质量贡献，沿路径计算input/output（B1、B2）。应由该服务提供已选终点有效产出，或在现有服务中增加明确命名的入库可用产出视图；ReceiptService不再写一套SQL SUM报工/质量。

**不能用** `NodeQuantity.availableQty` 当入库额度：当前含义是 `inputQty − reportedQty`，即剩余可报工量；全部报满时为0，但仍可能有100可交接。也不能用 `getWorkorderQuantities.lastNodeReportedQty`，现SQL只SUM末节点报工而未扣质量（B2）。应取单Execution已选终点 `effectiveOutputQty`，并要求路径锁定/数据有效；`qualityIntegrated=true`只证明质量贡献被读入，不证明已经检验放行。

Phase 2B明确新报工口径为包含不良的加工完成总量；存量是否按良品量录入仍未从业务来源核验。若原100已是扣过5的良品量，再扣独立不良5会发生双扣，不能凭SQL扫描无倒挂证明没有这个问题。不良latest版本消除了版本重复累计，但尚无实物序列身份自动识别多个declaration是否申报了同一件；Receipt不得另造去重或扣减规则，需要沿用Quality明确的申报口径并完成启用前核验。

### F2. 建议数量定义

- `O`：统一数量服务返回的已选终点有效产出；是否另受质量放行/待处理限制，由Quality提供明确结果，不能在Receipt复制扣减。
- `R`：Receipt正向实际交接量加已核实冲销负量的净累计；它只管理Receipt事实，不取ERP5901申请量，不把ERP查询量再加一遍。
- `P`：阶段2已发出/待执行/UNKNOWN的尚未转换成实际事实的占用；阶段1无外部意图则P=0。
- `rawRemaining = O − R − P`。新正向命令要求 `q>0 && q≤rawRemaining`；显示可新入量可取max(0,rawRemaining)，**同时保留负差额冲突**，不能钳成0隐藏历史超额。
- 不把各工序reportedQty相加为产出；不把各工序defectQty相加再从终点扣一次。质量在统一服务按节点扣减并沿路径传递，Receipt只消费最终O。

### F3. 订正、质量追加与库存事实冲突

现有Base报工/质量写入校验上下游，但没有Receipt事实，故**还没有 `R+P≤O` 的跨模块边界**。接入时必须同时覆盖管理员报工订正、质量CREATE/REVISE/VOID、Receipt录入/冲销和身份删除保护；不能只在Receipt按钮上校验。

建议允许管理员保存真实生产订正，同时把 `R+P>O` 暴露为明确差额，冻结新增正向Receipt，由库存权威核实多交接/少报工/在库质量处置。该差额可从事实实时推导，无需预建复杂异常平台。若选择“订正先拒绝，先处理库存”，也必须提供可记录真实情况的受控路径，不能逼人改假报工以匹配库存。此为P0业务决定，不能由本报告隐式批准。

不能自动改原报工/Receipt；不能因为质量晚申报就自动退库存。晚发现的不良可能已经在库或已转出，其质量处置和库存动作应单独处理。清零Receipt净额后仍保留全历史及引用保护。

## G. 汽车零部件案例 A～F

以下为候选规则演算，**不是已运行测试**。

| 案例 | 判断与执行边界 |
|---|---|
| A：目标100，终点有效95，申请95 | 不以target=100拒绝部分完工；取统一O=95，在质量准入已满足、R=P=0、路径/仓库/权限有效时允许实际交接95。只“申请”不能增加R；不能仅因末道量95自动把Execution置COMPLETED |
| B：O100，先60、再40、第三次1 | 锁Execution后分别取得余额100、40、0；前两次允许，第三次拒绝。相同request重复回同结果，不能再记60；并发两次60只有一个可成功。不得沿用一5901只可引用一次的旧模型 |
| C：末道报工100，独立不良5 | 若5是该终点有效缺陷且无重复处置，统一Quantity给O95，Receipt最多95，不再扣5。若5归因上游，必须由统一数量服务验证下游100是否冲突，不能Receipt自行算100−5。若质量集成/准入无法确认，推荐正式入库阻止；可登记草稿/待核实但不增加R、不入库存。临时人工质量放行只有在用户明确限定适用范围、角色、原因和审计后评估，不能默认合格或开一个随意配置开关 |
| D：已交接90，末道100订正85 | 原Receipt90和报工历史保持。按建议策略接受真实订正后展示差额5，禁止新增正向Receipt，库存责任人核查；如果实际仅收到85，受控库存逆向成功后追加−5引用原Receipt；如果实际在库90，则核查报工记录/实物，不能制造−5虚假退库。若库存已耗用，不能承诺可冲销。是否采用该策略由P0决定 |
| E：三材料装配成一产品 | Receipt只关联Execution与产出产品/产品批次。通过Execution追溯MaterialIssue及三材料批次，不强制把所有材料行复制到每次Receipt。需要按具体产出批次分摊材料、召回谱系时另立真实需求；Execution级追溯不等于已证明逐件材料谱系 |
| F：A厂ERP，B厂MES本地库存 | 同一ReceiptService负责Execution/数量/权限/事实与一致性规则；按明确库存权威调用ERP或本地库存能力。A走外部操作身份、UNKNOWN和核对；B在同一业务库事务中用受控库存流水/余额规则记账。当前Base库存CRUD还不是这项本地入账能力，不能PUT数量冒充验收。两者都关联同一Receipt操作，不复制ReceiptService，不同时增加ERP与本地权威库存 |

## H. 分阶段实施路线与验收门槛

### 阶段1：生产交接/入库事实独立闭环

先确认事实发生点、单/多Execution、产品批次、质量准入与订正差额策略；冻结可消费的Quantity契约。建立最小事实、正向/受控反向、查询追溯、权限、幂等、统一锁、历史引用保护。前端复用当前扫码身份与设计系统，不再按材料反推Execution；若改用主子单则必须复用MasterDetailDocument。

验收必须覆盖A/B、精确decimal、跨厂同本地主键、越权/切厂迟到响应、并发额度与同key重放、路径终点、历史删除保护、报工/质量订正与Receipt竞争。未接库存时明确交接登记/未入账，不宣布库存闭环；质量口径不明确时停止正式正向动作。按正式实施授权使用真实验收Factory/业务绑定执行迁移及验证，本轮不执行。

### 阶段2：ERP / 本地库存适配

由真实工厂决定库存权威；分别实现Receipt专用Port/Adapter和本地受控入账，不引入外部数据库会话工厂。ERP实现建单/审核/核对/撤销及映射，可靠操作记录覆盖超时、已知失败、外部成功本地失败；UNKNOWN不盲重试。验证一个Receipt只产生一次有效库存作用，释放占用/转换事实无双扣；本地库存需流水、余额、并发/冲销验证，不能复用旧负线边库存默认规则。

必须有真实ERP字段/结果样本和指定工厂的入账/冲销核对，Fixture不替代；无供应商配置明确失败或未接入。需要重复核对任务时遵守既有mes_master_sync协调，不提前建事件总线/流程引擎。

### 阶段3：质量放行与Execution完工联动

数量与质量缺失/冲突的准入边界在阶段1就必须明确；此阶段再补完整质量放行、晚发现处置、返工/报废后的库存协调及受控Execution完工条件。生产完成、交接完成、库存入账分别呈现；报满/入满不自动完工。多终点DAG如成为真实需求先扩展现有ExecutionPath/Quantity契约，再让Receipt消费结果，不能在入库模块另算汇合。

## I. 风险分级

| 等级 | 风险/证据 | 必须处理的阶段 |
|---|---|---|
| P0 | Receipt究竟是申请、实物交接还是库存记账完成；旧链混合（A1–A6） | 正式建模前统一语义和库存权威，否则同一状态无法跨A/B厂解释 |
| P0 | 可入库产出/质量放行口径尚未冻结；当前Quantity源码已变、旧报告仍预留，存量总量/良品口径未核实（B1、B2、Phase 2B） | 明确有效产出和质量未接入行为，防止既有良品量再次扣不良，禁止把0缺陷当已放行 |
| P0 | 入库90后产出订正85；报工/质量旧边界不含Receipt（F3） | 确定允许差额还是先阻止订正及恢复路径，不能自动篡改库存事实 |
| P0 | 单/多Execution与产品批次需求决定一表还是主子；幂等所需主键/约束与项目规则 | 确认V1边界及可靠操作身份；不擅自加UNIQUE |
| P1 | Web申请不验末道，PDA额度仅查询、未在提交重算，合并取最新记录（C2） | Receipt V1统一Quantity结果、共同锁和范围负例 |
| P1 | 新Web一申请只能一Receipt，与分批需求冲突；count→insert且源ID无唯一约束（S5/S6/S21） | 移除领料/申请单次占用模型；串行化同Execution额度 |
| P1 | 同记录抢占不等于跨请求幂等；多入口可超额/重复（C4） | 同key同payload重放、异payload冲突；所有入口共规则 |
| P1 | 5901事务回滚丢外部身份；5A00超时无号可重试；旧confirm漏业务结果（C4） | 接真实库存前可靠操作身份/UNKNOWN/核对；未解决禁止正式对外提交 |
| P1 | 本地wm重复execute、单据删除无库存逆转、线边可负（A6） | 不直接迁移；本地库存适配时建立受控幂等入账/冲销 |
| P1 | ERP状态进核心、删除已发生事实、批次/身份混用、add隐含approve权限（B/C1） | 新模型隔离，限制生成CRUD旁路，明确动作权限和身份保护 |
| P1 | Quantity.availableQty含义不同；工单末道汇总未扣质量；库存四位精度与报工六位精度（F1/E1） | 使用正确契约，确定单位/精度，禁止silent rounding |
| P2 | 多Execution整单审批、多产品联产、逐件材料谱系、多终点DAG | 有真实需求后单独扩展，不能提前叠加第二身份/数量服务 |
| P2 | 大数据性能、自动对账运营与更完整统计 | 按实际规模补索引/分页/计划和运维；基本未知结果核对不能延后 |

## J. 最终结论与最多五个决策问题

**CONDITIONAL GO：建议建设Base ProductionReceipt；不建议直接迁移ktg-mes入库逻辑。** 源系统证明了现场需要扫码定位、分次交接、仓库选择、申请和入库分离以及外部身份核对等能力；当前实现没有统一的生产身份、净产出额度、质量准入、跨入口幂等与库存一致性边界。直接复制会引入已经被Base取代的数量/身份和客户ERP耦合。

成立条件：正式建模前解决P0；阶段1闭合事实/额度/权限/幂等/历史保护；对接库存前关闭对应P1一致性窗口。此结论不是批准开始编码、迁移数据或生产发布。

1. **V1业务完成点**：先交付明确“未记库存”的生产交接登记，还是必须ERP/本地库存入账成功才算交付？首个实际工厂以哪个系统为库存权威？
2. **单据粒度与批次**：是否接受V1每条Receipt只对应一个Execution/产品批次/仓库；产品批次在哪个真实业务动作确定，哪些产品必须有批次？如果必须跨Execution一单，请给出整单签收/审核需求。
3. **质量准入**：是否同意质量有效产出/准入无法确定时阻止正式入库；确需临时人工放行时，由谁依据什么事实授权，并如何留痕？
4. **历史差额**：是否采用“真实报工/质量订正可保存，入库超额显式挂差额并冻结新增，由库存权威处理”的策略；还是要求先处理库存才允许订正？
5. **幂等持久化**：按当前禁止擅加UNIQUE规则，是否采用请求UUID主键操作记录；若必须沿用bigint主键+request_id方案，是否明确授权所需数据库唯一约束？

## 附录：关键源码定位、验证回执与快照

以下路径均为本次读取的真实本地源码。行号对应审查时工作区；并行改动可能使其漂移，方法名是稳定检索入口。

| 编号 | 文件/关键位置 | 证明内容 |
|---|---|---|
| S1 | [工单内申请页面](/Users/user/work/heri/ktg-mes-ui/src/views/mes/pro/workorder/confirmCenter/inboundConfirm.vue:164)；[工单内入库页面](/Users/user/work/heri/ktg-mes-ui/src/views/mes/pro/workorder/confirmCenter/inboundReceiptConfirm.vue:99) | 领料选择、默认量、建单/审核/删除；5A00建立即执行 |
| S2 | [申请Controller](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/controller/ProductionInboundConfirmController.java:52)；[入库Controller](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/controller/ProductionInboundReceiptConfirmController.java:57) | URL、动作权限、Validated、Service入口 |
| S3 | [申请Service](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProductionInboundConfirmServiceImpl.java:140) | create140、delete193、approve272、form383、route401、identity440 |
| S4 | [领料绑定SQL](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/pro/ProMaterialIssueConfirmExtraMapper.xml:12) | 可选领料、条件绑定、释放 |
| S5 | [正式入库Service](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProductionInboundReceiptConfirmServiceImpl.java:119) | create119、approve159、delete269、allocate298、身份388 |
| S6 | [入库抢占/累计SQL](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/pro/ProInboundReceiptConfirmExtraMapper.xml:4)；[未引用申请查询](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/pro/ProInboundConfirmExtraMapper.xml:39) | 单记录CAS、sumReceiptQty、count源单、无Execution累计锁 |
| S7 | [5901 Manager](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/manager/impl/MoReceiptRequistionManagerImpl.java:50) | 申请查询第1页30条、create71、query172、业务结果解析 |
| S8 | [5A00 Manager](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/manager/impl/MoReceiptManagerImpl.java:228)；[5901明细DTO](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/pojo/dto/MoReceiptRequistionResultDto.java:40) | 显式批次、产品特征、MO_NO、父单号/行；ACCEPTED_QTY直接映射 |
| S9 | [PDA普通Service](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/mobile/impl/InBoundRequestsMobileServiceImpl.java:69)；[报工筛选SQL](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/pro/ProReportingWorkMapper.xml:282) | getRange69–134、create138、工单定位187；workorder为空可跨工单汇集 |
| S10 | [PDA普通页面](/Users/user/work/heri/heri-uni-app/pkg-issue/pages/moReceiptRequistion/index.vue:113)；[API](/Users/user/work/heri/heri-uni-app/api/moReceiptRequistion/index.js:3)；[Controller](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/controller/mobile/InBoundRequestsController.java:105) | 前端额度校验、扫码/提交、后端无提交额度重算 |
| S11 | [PDA审核Service](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/mobile/impl/TransferAuditMobileServiceImpl.java:48)；[页面提交](/Users/user/work/heri/heri-uni-app/pkg-issue/pages/confirmTransferDoc/index.vue:299) | 5901查询和5A00直接创建 |
| S12 | [Manager直接confirm](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/manager/impl/MoReceiptManagerImpl.java:187)；[旧Service直接confirm](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/service/impl/MoReceiptServiceImpl.java:144) | 193/209行、155行业务审核结果漏解析 |
| S13 | [旧5901/合并Controller](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/controller/OapiMoReceiptRequistionController.java:63)；[合并页面](/Users/user/work/heri/heri-uni-app/pages/processmerge/processmerge.vue:720) | batch/merge入口仍有调用者 |
| S14 | [旧5901/合并Service](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/service/impl/OapiMoReceiptRequistionServiceImpl.java:92)；[合并Mapper](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/merge/ProReceiptMergeMapper.xml:1) | create、insertProReceiptMerge184、merge240、最新报工396 |
| S15 | [PDA报工自动入库](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/mobile/impl/WorkReportMobileServiceImpl.java:275)；[Web报工自动入库](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProReportingWorkServiceImpl.java:126) | 特定末道报工即5A00与断阶记录；不是统一Receipt |
| S16 | [断阶开关](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProRouteServiceImpl.java:40) | forge_joining_flag |
| S17 | [本地入库Controller](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/wm/controller/WmProductRecptController.java:162)；[主单Mapper](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/wm/WmProductRecptMapper.xml:78)；[明细Mapper](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/wm/WmProductRecptLineMapper.xml:1) | remove/execute/getTxBean、主子数据来源 |
| S18 | [本地库存入库编排](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/wm/service/impl/StorageCoreServiceImpl.java:266) | 线边库减、目的库加、允许线边负库存 |
| S19 | [库存余额与流水](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/wm/service/impl/WmTransactionServiceImpl.java:41) | synchronized、余额读改写和事务流水，无来源去重 |
| S20 | [ERP正式入库查询](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/erp/ErpMoReceiptDMapper.xml:18)；[ERP申请查询](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/resources/mapper/erp/ErpMoReceiptRequistionDMapper.xml:1) | ERP实际表/关联字段；查询不代表入库命令 |
| S21 | [确认表DDL](/Users/user/work/heri/ktg-mes/sql/mes/production-confirm.sql:50)；[工厂初始化DDL](/Users/user/work/heri/ktg-mes/scripts/mf-upgrade/sql/009-new-factory-business-init.sql:3915) | 确认两表、领料表及本地wm主子定义；非线上metadata |
| S22 | [业务结果工具](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/oapi/utils/OApiResponseUtils.java:23)；[HTTP序列化/响应](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/oapi/utils/OApiHttpUtils.java:45) | Fastjson→HTTP→Response；HTTP200与ERP业务结果分离；read空结果判未找到的范围需真实契约确认 |
| B1 | [统一数量服务](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:52) | load、quality注入、calculate137；availableQty与effectiveOutputQty含义 |
| B2 | [报工数量SQL](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/production/dao/ProductionQuantityMapper.xml:4)；[质量有效贡献SQL](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/quality/dao/MesProductionDefectMapper.xml:113) | 报工净增量、工单末道原始汇总；latest非VOID，非历史总和 |
| B3 | [选路验证](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathValidator.java:18)；[正式开工](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathService.java:148) | root→sink实际路径；开工需成功领料/无未知状态、确切工艺/下达工单 |
| B4 | [共同锁](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionChainLocks.java:37)；[正式报工](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReportSubmitService.java:91) | WorkOrder→Execution；幂等重放、数量、资格、正式开工 |
| B5 | [库存Service](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/wm/service/impl/WmInventoryServiceImpl.java:124)；[本地领料Adapter](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/integration/warehouse/LocalWarehouseErpService.java:9) | 库存CRUD不是流水入账；localSuccess不是ERP或Receipt库存作用 |
| B6 | [Execution删除保护](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionExecutionRules.java:118)；[质量Service](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/ProductionDefectService.java:83) | 既有历史引用/共同锁；尚无Receipt事实可约束 |

验证回执：执行了 `pwd`、五仓 `git status --short / branch --show-current / rev-parse HEAD`、`rg --files` 与 `rg -n` 的全局入口/调用者/SQL/文档搜索、`nl -ba / sed` 定位阅读及关键文件SHA256核对。没有运行测试（源码存在不等于通过），没有运行服务/API/真实数据库/ERP/浏览器/PDA验收，不声称并发或失败恢复已经复现。

关键Base快照SHA256（用于识别并行变化，不代表HEAD内容）：

- ProductionQuantityService.java：`0fab9dcbfe0acc43042b3c7ec6ea8a4a2550d895f58aa3950036931f5519945f`
- ExecutionPathService.java：`8f8a6c61b0756a0e479553e64a779fbcc01624269c42ddd8db389e21e5270d89`
- ProductionReportSubmitService.java：`26f79ff81b41d7042fa2e4eac9b3828406f5647fc4101b14f649cb89a1cd5221`
- MesProductionDefectMapper.xml：`42df982a267c573514a39381ead7b41c6940004772e5f2bf20894fe113846f96`

文档回执：新增0/A–J及附录，覆盖源码调用链、字段、业务规则、复用分级、逻辑模型、数量一致性、A–F演算、实施路线、P0/P1/P2与五项决策。**未修改业务代码；没有保存或执行DDL。**


---

## 2026-10-16：ProductionReceipt V1 实施与验收（后续实施记录）

本节由用户后续《ProductionReceipt V1 完整实施》授权产生，保留上方只读 Source 审查及其当时结论。**条件交付：Schema、查询、不可变历史和冲销已实现；正式正向交接仍为 NO-GO。** 当前 Quality 只有不良数量汇总，没有稳定的放行/阻止流转契约；未增加任何默认放行或配置旁路。

### A. 当前代码与依赖契约

调用链：独立 `productionReceipt/index.vue` → `productionReceipt.ts` → `ProductionReceiptController` → `ProductionReceiptService` → 生成的 `MesProductionReceiptMapper` 及跨表只读 `ProductionReceiptQueryMapper`。Service 复用 `ProductionChainLocks.execution`、`ProductionQuantityService.getExecutionQuantities`、`ProductionIdentityService`、`BusinessDirectoryService.userLabels` 和当前业务库 `WmWarehouseMapper`。

当前 Quantity 先按确切实际路径计算节点 `effectiveOutputQty = reportedQty - defectQty`；Receipt 直接读取已锁定路径最后一个节点的 effectiveOutputQty，不使用 availableQty，不自行 SUM 报工/质量或查询最新工艺版本。`ProductionDefectQuantityQuery.nodeTotals` 仅提供不良数量，不提供质量放行。并行 Phase 2B 的存量总加工量口径核验和运行启用边界也未由 Receipt 代替确认。

本轮没有修改 Operation/Routing/RoutingNode/RoutingEdge 模型、生产数量算法、报工和质量规则、库存或 ERP 业务。新 Service 不依赖库存写入 Service 或 ERP Gateway。

### B. 最终 Schema 与约束

- 正式迁移：[业务 V28](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V28__production_receipt.sql)，只追加新迁移，不修改历史版本。
- 精确目标：SIMPLE_B，数据源 8，192.168.18.25:3306/mes_base_dev_business_simple_b；执行前重新读取中心 mf_factory JOIN mf_datasource 核实启用绑定，检查不存在 Receipt 表。
- 一张 `mes_production_receipt`；产出物料由 Execution 唯一引用，不复制库存余额或建立第二套数量模型；没有 factory_id。
- **UUID 直接作为事实主键**：`id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY`，API 的 requestId 直接映射 id；reference_receipt_id 同为 VARCHAR(36)。用户给出的 BIGINT 是建议字段，本轮采用其要求先评估的 UUID 业务操作主键方案，不重复保存恒等的 request_id 列，不新增第二张幂等表，也不新增非主键 UNIQUE。
- receipt_no 使用 PR-/RV- 加 UUID。其他列沿用用户建议；DECIMAL(20,6)、DATETIME(3)，表和每列均有中文 COMMENT。
- 普通索引：execution_id/create_time/id，reference_receipt_id，warehouse_id/occurred_at。
- CHECK 限制正向为正且无引用；冲销为负、有引用、有原因；主键为小写 UUID 形状。
- `mes_receipt_no_update` / `mes_receipt_no_delete` 禁止修改删除事实；`mes_receipt_execution_guard` 拒绝删除存在任何交接历史的 Execution。没有物理外键、级联删除或额外唯一约束。
- 执行记录：`.local/receipt-v1-20261016/{center-before.tsv,business-before.tsv,migration.log,schema-facts-after.tsv}`。业务 Flyway 已执行并 validate 到28，不是仅保存 SQL。
- 已调用 [mes-crud-codegen Skill](/Users/user/work/lixin/mes-base/.agents/skills/mes-crud-codegen/SKILL.md)，专用配置 [production-receipt-codegen.yaml](/Users/user/work/lixin/mes-base/docs/development/production-receipt-codegen.yaml)，真实 metadata 生成 Entity/Example/Mapper/XML（1 supported / 0 rejected，`target/receipt-codegen/report.json`）。按本轮明确要求只生成 persistence，不发布通用 CUD；在全量配置排除 Receipt，防止未来生成器重新开放旁路。手改 Mapper/XML 时已移除其生成归属标记，未修改的 Entity/Example 保留标记。

### C. Service/API 实现与文件

核心文件：

- [ProductionReceiptService.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReceiptService.java)：事务、幂等、额度、质量失败关闭、冲销及查询。
- [ProductionReceiptController.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionReceiptController.java)：五项 HTTP 入口、独立权限、输入白名单及 VO 转换，ID/decimal 返回字符串。
- `production/domain/ProductionReceiptModels.java`、`ProductionReceiptRow.java`：类型化业务查询模型和关联显示字段。
- 生成 `MesProductionReceiptEntity.java`、`MesProductionReceiptExample.java`、`MesProductionReceiptMapper.java` 与 Mapper XML；单表 netQuantity/originalBalance 扩展仍在同一个生成 Mapper 内，不新增单表 Extra。
- `ProductionReceiptQueryMapper.java/XML`：仅 JOIN Execution/WorkOrder/Material/Warehouse 的列表和详情，分页由 PageHelper + 数据库 `ORDER BY r.create_time DESC,r.id DESC` 完成。
- `mes-common/.../ErrorCode.java`：追加六个交接诊断错误，不覆盖并行 Quality 错误。

API：GET /api/production/receipts、GET /summary?executionId=、GET /{uuid}、POST /、POST /{uuid}/reverse。正向参数仅 executionId/warehouseId/productLotCode/quantity/requestId；冲销仅 quantity/reason/requestId，quantity 填正数，Service 保存负变化；整笔冲销传当前原始余额，锁内重新验证。

写事务固定 `businessTransactionManager`、READ_COMMITTED，公共锁顺序 WorkOrder → Execution。当前正向要求执行 RUNNING/COMPLETED、确切路径 LOCKED、当前库仓库存在且 ENABLED，再验证额度及质量门槛；当前仓库模型无 inboundFlag 等额外属性，因此没有臆造仓库用途字段。当前认证用户同时作为正向 operator/recordedBy；实际 HTTP 对伪造 recordedBy 的未知字段返回400，未采用其值。

冲销仅能引用 RECEIPT，锁内检查原始净余额；继承 execution、warehouse、lot、operator、occurredAt，recordedBy/createTime 为本次可信录入身份/时间。仓库后来停用、当前质量未知或产出减少不阻止合法冲销。普通 UPDATE/DELETE HTTP 入口未生成。

### D. 数量与上游订正差额

O=Quantity 实际末道 effectiveOutputQty；R=Receipt 自有不可变流水 SUM(quantity_delta)；remaining=O-R（保留负数）；excess=max(0,R-O)。未选择/未锁定路径时 O/remaining/excess 返回 null 及明确边界，不用0伪装已知产出。Quantity 查询失败正常传播，不把质量异常吞为全部合格；独立流水分页仍可单独查询。

真实 MySQL Service 测试：原始历史夹具 R90，末道报工订正100→85成功，查询 remaining=-5/excess=5，冲销5后excess=0；随后完成原始余额冲销，不改报工/质量/库存或执行状态。负报工订正经真实 ProductionReportService.adjust 执行，Receipt 未增加跨模块拒绝条件。

同一测试经真实 Quality 命令新增末道不良2、订正3、作废，对应 O=83、82、85；Receipt 不再减一次不良。Quality 终止版本保留，未删除事实。测试结束 Receipt 净额0。

### E. 幂等与并发

数据库主键保证跨 Execution 的 UUID 原子排他，不依赖查询后插入或 JVM 锁。锁后复查已存在事实；同动作、同身份、同规范化批次/原因、同数量（数值比较）重放原事实。不同动作、payload、操作者或 Execution 返回 RECEIPT_IDEMPOTENCY_CONFLICT，不返回另一业务的事实。跨 Execution 同时首次插入碰撞由主键拒绝并整体回滚。

同 Execution 的同请求并发冲销经共同锁后重放，仅一条事实；原始剩余20时两个15并发，只允许一笔，另一笔409。已成功事实的重放不依赖当前额度或质量状态，客户端另外 GET 最新 summary；事实响应不会被伪装为最新余额。

**正向60+40、第3次1、80+两个15的额度边界目前只有纯数量规则测试；正式 receive 成功/并发成功链因质量门槛不能真实验收，未标记 PASS。** 不为测试关闭生产质量保护。跨 Execution 并发碰撞实现有主键保证，真实服务已验证顺序跨 Execution 复用拒绝，未单独对两个 Execution 同时首次正向提交作成功验收。

### F. 独立页面、权限和交互

- 页面：[productionReceipt/index.vue](/Users/user/work/lixin/mes-base-ui/src/views/production/productionReceipt/index.vue)；API：[productionReceipt.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/productionReceipt.ts)；菜单清单：`src/menus/production/productionReceipt.json`。
- 正式中心 [V31 菜单迁移](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/system/V31__production_receipt_menu.sql) 已执行并 validate，中心库 mes_base_dev。独立菜单2613，路由 `/production/productionReceipt`，父目录2512；四权限 list/detail/create/reverse，仅写菜单，不自动授予任何已有角色。
- 按工单/Execution/仓库/精确产品批次查询；不选择 Execution 也能分页查看全部交接，不需进入工单详情。选择执行后显示产品、确切工艺名称/版本、末道名称、目标、O/R/remaining/excess、质量阻塞原因及实际路径状态。非零差额单独告警。
- 复用 WorkOrderSelect/ExecutionSelect/WarehouseSelect、PageHeader、Pagination、request、当前权限及主题样式。名单人员按本页去重 IDs 经 BusinessDirectory 一次批量回显；关联物料/仓库/执行信息使用跨表投影，没有散落名称缓存或每行查询。
- 正向按钮服从服务端 canReceive，当前明确禁用并显示质量阻塞。冲销默认原始当前余额，可改部分数量并必须填原因。冲销需 reverse 权限，前端读取余额同时需要 detail 权限；后端 reverse 自身仍独立授权，不以 UI 隐藏替代检查。
- 显著保留“生产交接已登记，尚未执行库存入账。”；不提示库存或 ERP 入库成功。
- 切厂/Execution/权限版本变化立即清理流水、摘要、对话框和待重试命令；列表、摘要、详情、历史分页分别使用序列拒绝迟到回填。关闭对话框即失效相关查询；编辑关闭先确认，取消则保留输入。未知提交保留原 UUID/冻结 payload，仅可安全重试；明确4xx允许修正。确认成功后刷新失败仍保留成功记录号。
- 390宽度筛选改为整列布局、触控44px；桌面保留表内横向滚动和固定操作列。未改 WorkOrder 页面或将交接嵌入多层详情。

### G. 自动化验证

| 验证 | 本轮结果 / 证据 |
|---|---|
| `mvn -B -ntp verify` | PASS，591项总数，实际执行559，32项环境门控跳过，0失败/错误；`/tmp/receipt-verify-final.log` |
| 后端 Receipt 定向最终回归 | Quantity规则2 + 双库H2真实XML/事务/HTTP5，通过；`/tmp/receipt-directed-final.log` |
| 指定 MySQL 测试 | `MES_RECEIPT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionReceiptMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`，1大场景通过；包含真实上游订正/质量命令及并发/触发器；`/tmp/receipt-mysql-quality.log` |
| 前端全量 | PASS，53文件302项，其中Receipt14项；`/tmp/receipt-ui-all-final.log` |
| 前端构建 | `npm run build` PASS；`/tmp/receipt-ui-final-build.log`，仅既有大chunk提示 |
| 打包独立冒烟 | `python3 scripts/smoke-packaged.py` 10通过/0失败；`/tmp/receipt-smoke.log`；独立测试进程，不启停用户服务 |
| 格式/差异 | 已格式化本轮手工 Java；git diff --check 两仓通过；未批量格式化并行代码 |

测试文件：`ProductionReceiptQuantityTest.java`、`ProductionReceiptHttpTest.java`、`ProductionReceiptMySqlTest.java`、前端 `tests/productionReceipt.test.ts`。H2 测试复用共享 MaterialIssueHttpTest，仅追加两个 Mapper 类型及 import，不替换并行 fixture 修改；MySQL 实际检查原有 Receipt/报工/质量记录保留与库存全表逐字段不变。纯规则、H2、MySQL、运行中 API/UI 证据分别记录，不能互相替代。

### H. 真实 MySQL / API / 浏览器证据

正式迁移已执行。用户确认重启加载新增接口；Agent 未启动、停止或重启用户服务。运行入口为现有8081和5173。

- `.local/receipt-v1-20261016/live-api-evidence.json`：真实登录、独立分页/摘要/详情/筛选、只读账号写入403、无 Receipt 授权的原admin403、另一工厂403、质量未核验正向409且事实数不变、真实部分冲销3/重放/异参冲突/禁止冲销REVERSAL/历史查询，共16项检查。
- `live-final-evidence.json`：浏览器冲销剩余7后余额0，历史两条；冲销实际人员/发生时间等继承字段与原事实完全一致；伪造 recordedBy 返回400；原事实重放稳定，summary仍重新读取净额0。
- 指定库隔离 TEST Execution123/124/125，使用新建TEST工单和TEST路线夹具；未向客户工单写事实。第一次 MySQL 验收的测试期望行数误写6，实际5，修正断言后重跑；第一次样本123仍保留且净交接0，不删除失败测试痕迹。
- 最终 MySQL测试样本125/工单116；业务报工和质量命令校验通过，质量版本最终VOID；样本124/工单115用于运行中HTTP/UI，原历史夹具10经API冲销3+浏览器冲销7，净额0。所有 RECEIPT 正向行均明确 TEST历史夹具直接准备，**不是 receive 命令成功证据**；生产正向命令未绕过质量阻塞。
- 两个新建且仅用于验收的FACTORY角色：146/147，SIMPLE_B；账号6244 TEST_RECEIPT_V1_WRITE、6245 TEST_RECEIPT_V1_READ，写角色仅四交接权限及必要只读选择器，读角色只有list/detail及只读选择器。不改变原角色授权，不扩大普通用户权限；凭证仅本地忽略文件，未写报告或输出。
- 浏览器真实冲销成功并刷新净额0；查看原事实余额0及两条冲销历史。另以真实只读账号6245登录，独立页面有真实流水且无登记/冲销按钮（`ui-read-only.png`）；结束停在该TEST只读账号的SIMPLE_B交接页，viewport已恢复默认。真实切厂后旧摘要/流水/表单清空且无权限界面，随后回到工作台，未回填旧厂数据。
- 1280/1440/1920/390均实际设置viewport并截图，documentWidth等于viewport，无页面横向溢出，表格内部按设计滚动。证据：`ui-1280.png`、`ui-success-1440.png`、`ui-1920.png`、`ui-390.png`、`ui-history.png`、`ui-factory-cleared.png`；390先发现筛选固定宽度不齐，已局部调整并复核。
- **真实撤权未完成**：`GET /api/system/role/146`两次15秒超时，未得到完整角色授权快照，未执行撤权或覆盖其授权。`revoke.log`留证；没有将失败尝试称为通过。撤权状态清理及迟到响应由前端自动化覆盖。真实另一工厂查询403、只读写403已有API证据。
- 未进行真实网络丢包/超时故障注入、真实双业务库的Receipt成功写入（正向被阻塞）；对应隔离/迟到结果使用H2及前端受控异步测试，不标作运行环境成功证据。

### I. Git、并行保护与未完成事项

开始/结束后端HEAD `4679154eccb61444f1c0f795c9620a54ec6e207d`，前端HEAD `02a6554baff657f6c8dfe42cebdcbc9e5f863473`。两仓已有 Quality Phase2B、ReportV2、领料等dirty/untracked修改，本轮未覆盖、回滚、提交、推送或部署；Source Product未写入。状态快照位于本轮 `.local` 目录 backend-status/frontend-status。

除新增Receipt文件、迁移、测试及本节追加外，本轮仅在ErrorCode追加交接码、全量codegen配置排除新事实表、共享测试追加Mapper白名单和import；没有改写并行Quantity/Quality/ExecutionPath/Routing业务代码，前端全局样式已有修改也未作为本轮成果。

剩余：质量稳定放行/阻止流转契约及环境口径核验；随后接入正向gate、重新验收真实60+40/超量/并发80+15+15、跨Execution首次同UUID碰撞及全过程业务闭环。平台角色详情超时原因未在Receipt任务内重构排查，真实撤权复验未完成。库存入账、ERP及自动完工均不属于本轮，不是后续待补的Receipt副作用。

回滚边界：未提交代码可按本轮文件范围人工撤回；业务/菜单迁移已正式执行，不删除Flyway历史、不DROP事实表、不回写或删除事实。未来调整必须新增迁移；错误事实仅经受控冲销。保留TEST样本及其终止历史供复核。

### J. 交付判断

| 标记 | 结论 | 适用边界 |
|---|---|---|
| SCHEMA_READY | PASS | 指定真实库 V28 / 中心 V31已执行并校验，生成已完成 |
| API_VERIFIED | PARTIAL | 查询/冲销/幂等/权限拒绝已真实通过；正向业务成功未放行 |
| UI_VERIFIED | PARTIAL | 独立页面、真实冲销/历史、四宽度、切厂验证；真实撤权受平台接口超时阻塞 |
| BUSINESS_VERIFIED | BLOCKED | 无质量放行契约，正式正向交接必须失败关闭 |

**CONDITIONAL GO：仅可使用已验证的查询、不可变历史和合法冲销。正式正向生产交接 NO-GO。** 本轮不把未验证的质量状态默认为通过，也不把历史夹具或幂等重放称为正式交接成功。
