# Complex Business Backlog

> 第二轮现实必要性/当前范围/优先级以[MES_CAPABILITY_REVIEW](MES_CAPABILITY_REVIEW.md)为准。
> 本文保留第一轮Source复杂性事实；列入backlog不等于该能力一定属于Base默认产品。

2026-10-12；本轮只做静态审查与后续任务分类，没有实现以下能力。
Source 根目录：`/Users/user/work/heri/ktg-mes`；下列路径相对其
`ktg-mes/src/main/java/com/ktg/mes/`。表来自仓库 DDL/Mapper，不是生产metadata。
各项后续先确定能力最小边界；确认合理基础表后再用Base Codegen生成基础持久化，
复杂行为由相应业务Service执行，不要求全部经过生成CRUD Service。

## Material / Product / Classification

- Source Tables：`md_item、md_item_type、md_product_bom、md_product_sop、erp_item、erp_item_production`。
- Source Services：`md/service/impl/MdItemServiceImpl.java、ItemTypeServiceImpl.java`；`erp/service/impl/ErpItemServiceImpl.java`。
- 调用链：`MdItemController.add/edit → 分类查询 → 资料CRUD → WmBarCodeUtil.generateBarCode`。
- 已识别规则：分类提供物料/产品标识与名称快照；编辑关闭安全库存时清零上下限；编码/名称查重；创建有条码副作用。
- 问题与不能Generate原因：单表Service看似CRUD，真实Controller协调分类/条码，分类有层级；不能把完整能力当纯资料录入。
- 下一阶段：拆出可生成的基础资料；核对单位/分类/库存策略、BOM引用、条码生成和ERP来源边界。没有决定沿用冗余分类名称或浮点库存量。

## Operation / Process

- Source Tables：`pro_process、pro_process_user、pro_process_content、pro_process_inspection、qc_defective_cause、process_file_relation、process_external_mapping`。
- Source Service：`pro/service/impl/ProProcessServiceImpl.java`；文件/外部映射分别有Service。
- 调用链：`ProProcessController → insertProProcess(batchAdd) → 工序Mapper + 人员Mapper + 不良原因Service → addDefaultAdminUser`。
- 已识别规则：批量创建复制关联人员及不良原因；固定用户名admin被自动关联；正式/临时计件单价互斥并校验期限；批删清关联。
- 问题与不能Generate原因：多表写入、检验/文件与财务子能力混入一张工序模型，CRUD不能表达复制/联动删除/权限语义。
- 下一阶段：保留工序事实、内容及真实生产关联；去固定admin和工资子规则，按实际依赖选生成基础表及一个业务Service，不预建泛型领域框架。

## Routing

- Source Tables：`pro_route、pro_route_process、pro_route_product、pro_route_product_bom`。
- Source Services：`pro/service/impl/ProRouteServiceImpl.java、ProRouteProcessServiceImpl.java、ProRouteProductServiceImpl.java`。
- 调用链：`工艺维护 → 路线/工序/产品/BOM`；`报工 → findPreProcess/findNextProcess/checkKeyProcess`。
- 已识别规则：按产品筛启用路线；工作站提供当前工序；关键工序参与报工；锻接标记触发特殊入库/断接。
- 问题与不能Generate原因：路线维护影响执行语义；`checkKeyProcess`取启用列表第一个，未证明多路线稳定选择；实体没有可确认的路线版本/发布冻结字段。
- 下一阶段：核对路线唯一选择、工序顺序/关键点、执行快照与变更影响；版本差距见GAP。基础关系CRUD不能替代执行契约。

## WorkOrder / Task / Request / Archive

- Source Tables：`pro_workorder、pro_workorder_bom、pro_task、pro_task_issue、pro_workorder_request、pro_workorder_history、pro_workorder_archive_batch`。
- 核心入口：`pro/controller/ProWorkorderController.java`；`pro/service/impl/ProWorkorderServiceImpl.java`；`pro/request/service/WorkorderRequestService.java、WorkorderProductionService.java`；`pro/archive/service/impl/ProWorkorderArchiveServiceImpl.java`。
- 调用链：`add/edit → 工单 → Controller.generateBomLine/删除旧BOM`；`dofinish → 更新任务 → 更新工单`；`getInfo → ERP工单同步 → 本地读取`。
- 已识别规则：父子工单/ancestors、产品BOM展开、产品变更重建BOM、任务与工单完工联动；请求/归档为其他入口。
- 问题与不能Generate原因：Controller事务/跨Service编排、读入口有外部副作用、多个生命周期入口；普通编辑/删除可能绕过完成规则。
- 下一阶段：明确状态权威、可编辑条件、BOM/任务事务、读命令拆分与归档语义。本轮不触及Base已有`pro_workorder`或十万条测试样本。

## Reporting / Feedback

- Source Tables：`pro_reporting_work、pro_reporting_work_material、pro_feedback、pro_workorder、pro_route_process、pro_buckle、pro_material、pro_route_broken_step`。
- Source Services：`pro/service/impl/ProReportingWorkServiceImpl.java`；`service/mobile/impl/WorkReportMobileServiceImpl.java`；材料Service。
- 调用链：`报工入口 → PreVerify → ExistPermission → quantityVerity → executeInsert → 末序判断/入库创建及确认`。
- 已识别规则：首序须领料、后序须上序报工；按材料可用量/BOM用量计算可报上限；当前人员工序准入；最后工序完成数量匹配后提示申请入库；锻接路线可创建ERP入库并记录前后批次。
- 问题与不能Generate原因：跨表数量、状态、完成判定和外部命令；Service返回AjaxResult/Map、事务自调用入口及外部失败窗口须后续核实。静态没有证明多节点/重复报工安全。
- 下一阶段：冻结数量口径、业务状态与可重试边界；保留报工事实，剥离工资计算，独立验收本地事务与ERP业务响应。

## Material Issue / Production Inbound

- Source Tables：`pro_buckle、pro_material、pro_material_issue_confirm、pro_material_issue_confirm_detail、pro_inbound_confirm、pro_inbound_receipt_confirm、pro_route_broken_step、wm_warehouse`。
- Source Services：`erpapi/service/impl/IssueReceiptServiceImpl.java`；`pro/service/impl/ProductionMaterialIssueServiceImpl.java、ProductionInboundConfirmServiceImpl.java、ProductionInboundReceiptConfirmServiceImpl.java`。
- 调用链：领料/入库确认入口→数量/物料/仓库资格→外部创建/审核→本地状态及单号关联。
- 已识别规则：仓库`inbound_flag`仅用于入库申请联动；车间`attr1`被作为ERP仓库；固定仓库9998也存在；断接记录保存前后品号/批次。
- 问题与不能Generate原因：外部HTTP不在本地事务内，业务响应、重复请求、结果UNKNOWN和恢复不能由CRUD解决；不把固定客户仓库规则带进Base。
- 下一阶段：先审独立的MES确认事实；未来真实集成另设有边界任务，不在Base装配外部数据库/第三会话工厂。

## FlowCard / Transfer

- Source Tables：`mes_transfer_config、mes_transfer_config_action、mes_transfer_action_def、mes_transfer_scene_def、mes_transfer_record、mes_transfer_action_log、pro_trans_order、pro_trans_consume`。
- Source Services：`service/transfer/impl/TransferApplicationServiceImpl.java`、`TransferRecordPrepareServiceImpl.java`；`service/transfer/action`。
- 调用链：`confirmTransfer/confirmTransferOrderReceive → TransferActionFacade.execute → 动作匹配/权限/Adapter → 记录、动作日志、通知`。
- 已识别规则：出厂/入场为不同动作；Adapter缺失、未匹配、执行失败明确返回失败；批量入场逐项结果；调拨先核对来源准备记录。
- 问题与不能Generate原因：配置/动作/记录/外部执行结果共同决定状态；流程批号入口不等于已有完整、通用、版本冻结的FlowCard主模型。
- 下一阶段：明确通用动作与源定制Adapter边界，保留动作身份/日志和结果语义；不建立通用工作流引擎。

## Heat Treatment

- Source Tables：`mes_equipment、mes_heat_treatment_record、mes_heat_treatment_photo、mes_heat_treatment_param_template、mes_heat_treatment_param_submit、mes_heat_treatment_param_record`。
- Source Service：`md/service/impl/HeatTreatmentServiceImpl.java`。
- 调用链：创建→设备解析/运行记录检查→批次子项；结束/转序→关联新记录；参数提交→明细/审计；追溯→相关记录集合。
- 已识别规则：设备编码来自mes_equipment；运行设备/批次要核对；quantity是辅助参考，不是库存守恒或ERP扣减；参数有记录级身份。
- 问题与不能Generate原因：多表、参数模板、执行状态、批次关联和追溯；不能因mes_equipment字段少就单独宣布完整Equipment业务简单。
- 下一阶段：核对热处理与dv_machinery的设备身份、状态和参数事实；保持原数量语义，不提前加数量守恒约束。

## Quality Workflow / Step Inspection / Nonconformance

- Source Tables：质量模板/检测关联、`qc_iqc/ipqc/oqc`及行、`inspection_record、pro_process_step、pro_step_inspection_config、pro_step_inspection_record、qc_special_product_inspection、qc_defective`及责任/材料关联。
- Source Services：`md/service/impl/InspectionRecordServiceImpl.java`；`qc/QcIqcServiceImpl、QcIpqcServiceImpl、QcOqcServiceImpl、QcDefectiveServiceImpl`；`pro/stepinspection/service/impl/StepInspectionServiceImpl.java、StepInspectionRecordServiceImpl.java`。
- 调用链：送检→身份+检验单唯一处理→检验结果→首次通知；工步检测→解析生产对象→工序/终端/配置准入→记录；传统检验→缺陷汇总/状态执行。
- 已识别规则：送检人从登录态取得；并发送检捕获唯一键冲突；结果可修改、不通过须原因；只有首次从待检进入结果状态发送通知；工步配置按终端/启用检查。
- 问题与不能Generate原因：身份、防重复、结果修改/通知和工步关联有真实规则，生成普通更新可绕过。Source已有质量与部分复检标识，不能整体标GAP。
- 下一阶段：按检验用例确定契约/状态/主体、通知失败边界；保留不良事实，分离责任财务规则。具体处置闭环差距见GAP。

## Traceability / WIP

- Source Tables：报工/材料、`pro_route_broken_step、mes_transfer_action_log、mes_heat_treatment_record`；WIP展示Mapper关联这些来源与ERP数据。
- Source Services：`service/mobile/impl/FlowRetrospectMobileServiceImpl.java`；`pro/controller/mobile/ProcessTraceabilityMobController.java`；`md/HeatTreatmentServiceImpl`；`dashboard/SimulationWipServiceImpl`。
- 调用链：品号+批次→断接前后身份→工单；热处理相关记录查询；WIP展示→报工/入库/领料聚合。
- 已识别规则：批次发生品号变化；追溯需要跨生产记录；SimulationWip实际读取生产指标，并非纯伪造数据。
- 问题与不能Generate原因：谱系查询/数量状态跨能力；移动入口直接get(0)，空结果与多级关系未在本轮证明；有显示数量不等于有完整WIP状态台账。
- 下一阶段：优先迁真实Query Service和空/多结果语义；通用谱系/在制缺口只登记，不现在设计或开发。

## Warehouse / Inventory

- Source Tables：`wm_warehouse、wm_storage_location、wm_storage_area、wm_material_stock、wm_transaction`及收/退/领/调/盘单据、行表。
- Source Services：`wm/service/impl/StorageCoreServiceImpl.java、WmTransactionServiceImpl.java`及各单据Service/Controller。
- 调用链：单据执行→StorageCore→正负库存事务→库存记录；生产领料同时出原库、入线边库。
- 已识别规则：库存事务类型决定增减；退料/退货与入库方向不同；线边库具有既定来源；批次/供应商/物料多维身份。
- 问题与不能Generate原因：一组字典加单据CRUD不是库存守恒/幂等/审核撤销；本轮未验证锁或库存并发。
- 下一阶段：先审仓库主档是否可独立生成，再核对库存事实、事务/冲销及MES/WMS/ERP主责，不建外部库框架。

## Work Center / Equipment / Scheduling / Query Reports

- Source Tables：工作站与设备/人员/工装关系，`dv_machinery_type/dv_machinery/dv_check_*/dv_repair*`；`cal_*、pro_monthly_plan、pro_task、pm_*`；报工/工单查询。
- Source Services：`md/MdWorkstationServiceImpl`及关系Service；`dv/DvMachineryTypeServiceImpl、DvCheckPlanServiceImpl、DvRepairServiceImpl`；`pro/plan/service/MonthlyPlanService.java`；`pro/productionmonitor/service/impl/ProductionMonitorServiceImpl.java`；`pro/personnelanalysis/service/PersonnelAnalysisServiceImpl.java`。
- 已识别复杂点：工作站绑定工序和资源；设备类型parent/ancestors；点检计划与维修主子；月计划涉及产品/车间及执行。生产监控/人员分析是跨表查询能力。
- 推荐处理：先拆基础资源/日历主档，再审排班和生产用例。复杂统计优先PORT为Query Service，不机械引入Aggregate。
  日历、工装、客户供应商、BOM/SOP的完整规则尚未深审，继续REVIEW，不伪装SCHEMA_READY。

## 2026-10-12 第二轮：默认范围与分析次序

- P0：WorkOrder、Operation/Routing、Reporting、基础WIP联合分析；必要依赖为产品执行投影、单位口径、执行资源。
- P1：质量执行/缺陷事实、实际投入/产出移交；不先迁qc_index/qc_defect来锁定模型。
- DEFER：设备维护（含dv_subject）、热处理行业模块、跨场所流转、全仓储、高级排程、多级谱系。
- REVIEW：BOM/SOP、工装日历、行政/安全/托盘/专用报表等未深审能力；不以Source有模块证明默认必要性。

纠正重点：qc_index的配置CRUD隶属复杂检测模型；qc_defect目前是独立字典，未证实执行记录引用它，
不能把整个Defect Management的复杂性当作已存在字典耦合。dv_subject是点检/保养项目，
不是生产Operation。Workshop层级可选，独立单位主数据/通用换算需权威与用途证明。
这些是迁移决策修正，不是本轮实现方案或开工授权。
