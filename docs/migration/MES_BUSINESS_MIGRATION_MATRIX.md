# MES Business Migration Matrix

> **2026-10-12 第一批实施：用户已批准六项按 SIMPLE 迁入，ERP_SYNC 全部启用。**
> 当前决定和验收见 [SIMPLE_BUSINESS_MIGRATION_ACCEPTANCE](SIMPLE_BUSINESS_MIGRATION_ACCEPTANCE.md)。
> 第二轮审查保留为历史；其中对本批六项的 DEFER/REDESIGN 不再决定本轮范围，其他业务未获本轮实施授权。


> 历史第二轮：当时产品迁移决定以[MES_CAPABILITY_REVIEW](MES_CAPABILITY_REVIEW.md)为准。
> 五张已建表均重新进入REVIEW；SCHEMA_READY只保留建表技术事实。
> 下文首轮GENERATE/ADAPT_SCHEMA结论已撤回，不能作为继续建表/Codegen的授权。

2026-10-12；第一轮 BUSINESS CAPABILITY REVIEW / SCHEMA REVIEW。
Source：`/Users/user/work/heri/ktg-mes`，分支 `master`，HEAD `42a81cc577dc77b05a6a67f6be0a9466e40784e1`。
Target：`/Users/user/work/lixin/mes-base`。Source 只读，排除 `ktg-wheel`；未访问 Source 数据库。

历史总盘点保留在 [MES_FEATURE_INVENTORY](../development/MES_FEATURE_INVENTORY.md) 和
[MES_MIGRATION_MATRIX](../development/MES_MIGRATION_MATRIX.md)。本表是新的业务阶段决策，
取代历史“全量 PORT”对本轮的指导作用，不覆盖历史验收结论。
静态模块、227 个 Controller 文件、业务表清单及五个简单候选的文件摘要见
[SOURCE_BUSINESS_EVIDENCE.json](SOURCE_BUSINESS_EVIDENCE.json)。Controller 文件数不是业务能力数，
也不是已可达/已运行接口数量。没有逐行审完 Source 或证明所有源码功能在现场可用。

## BUSINESS_CAPABILITY_INVENTORY / 迁移分类

Backend 路径以 Source 的 `ktg-mes/src/main/java/com/ktg/mes/` 为前缀；Mapper XML 在
`ktg-mes/src/main/resources/mapper/`。这里的 REDESIGN 表示后续有边界审查/调整，
不表示丢弃整个能力或本轮已经实施。

| Capability | Source Tables | Source Backend | Complexity | Strategy | Schema Action | Status | Notes |
|---|---|---|---|---|---|---|---|
| Unit Of Measure / 计量单位 | md_unit_measure | MdUnitMeasureController、单位下拉 | SIMPLE | ADAPT_SCHEMA / GENERATE | unit_measure；V2 | IMPLEMENTED / ACCEPTED | 用户已批准；ERP_SYNC；不迁换算 |
| Workshop / 可选组织层级 | md_workshop | MdWorkshopController | SIMPLE | ADAPT_SCHEMA / GENERATE | workshop；V2 | IMPLEMENTED / ACCEPTED | workshop_id 可空；ERP_SYNC |
| Quality Inspection Model / 含检测项 | qc_index, qc_template_index/product及检验头/行 | QcTemplateIndexController→QcIpqcController→QcDefectRecordController | COMPLEX | REDESIGN | 既有qc_index暂留，质量整体定模型 | REVIEW / RECONSIDER | 检测配置CRUD只是复杂质量执行的一部分，暂停独立生成 |
| Defect Management / 字典接入候选 | qc_defect；执行记录为qc_defect_record及不良申报 | QcDefectController；缺陷执行消费者未找到稳定字典引用 | UNKNOWN | DEFER | 既有qc_defect暂留 | REVIEW / RECONSIDER | 字典自身CRUD仍简单；不能用孤立目录证明质量能力或迁移准入 |
| Equipment Maintenance / 含点检项目 | dv_subject, dv_check_plan/machinery/subject, dv_repair/line | DvCheckPlanController及点检项目选择页面 | COMPLEX | DEFER | 既有dv_subject暂留，默认范围不迁 | REVIEW / DROP_CANDIDATE | 属可选设备维护能力，不能因表已建就纳入默认MES |
| Material / Product 简化投影 | md_item | MdItemController、物料页面 | SIMPLE | ADAPT_SCHEMA / GENERATE | material；V2 | IMPLEMENTED / ACCEPTED | 不迁 BOM/条码/库存执行；分类与安全库存单独维护；ERP_SYNC |
| Product BOM / SOP | md_product_bom, md_product_sop | md/MdProductBomServiceImpl, MdProductSopServiceImpl | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 有产品/组件/文件依赖；未深审替代料、版本和删除一致性，不能凭名称判简单 |
| Client / Vendor 基础档案 | md_client, md_vendor | md/MdClientServiceImpl, MdVendorServiceImpl；erp/ErpSupplierServiceImpl | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 基础档案可生成候选；先区分通用联系资料与 ERP 同步来源，不全搬供应商连接 |
| Operation / Process | pro_process, pro_process_user, pro_process_content, pro_process_inspection, qc_defective_cause, process_file_relation, process_external_mapping | pro/ProProcessServiceImpl, ProcessFileManagementServiceImpl, ProcessExternalMappingServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 新增复制人员/不良原因并绑定固定 admin；临时单价有效期、检验与文件关联混入工序 |
| Routing | pro_route, pro_route_process, pro_route_product, pro_route_product_bom | pro/ProRouteServiceImpl, ProRouteProcessServiceImpl, ProRouteProductServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 前后工序、关键工序、启用路线选取、产品/BOM、锻接规则；没有可确认的路线版本冻结契约 |
| WorkOrder / Task / 工单归档与请求 | pro_workorder, pro_workorder_bom, pro_task, pro_task_issue, pro_workorder_request, pro_workorder_history, pro_workorder_archive_batch | pro/ProWorkorderController, ProWorkorderServiceImpl；pro/request、pro/archive | COMPLEX | REDESIGN | 不新增或改工单表 | BACKLOG | Controller 编排 BOM 与任务完工；详情读取触发 ERP 同步。Base 既有 pro_workorder CRUD 样板不等于业务迁移 |
| Reporting / Feedback | pro_reporting_work, pro_reporting_work_material, pro_feedback, pro_buckle, pro_material | pro/ProReportingWorkServiceImpl；service/mobile/impl/WorkReportMobileServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 上道工序、领料/BOM数量、人员工序准入、末序入库、ERP副作用与材料状态 |
| Production Material Issue / Inbound | pro_material_issue_confirm, pro_material_issue_confirm_detail, pro_inbound_confirm, pro_inbound_receipt_confirm, pro_route_broken_step | pro/ProductionMaterialIssueServiceImpl, ProductionInboundConfirmServiceImpl, ProductionInboundReceiptConfirmServiceImpl；erpapi/IssueReceiptServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 审核/确认、批次转换、库房资格及外部单据；与普通字典分开 |
| FlowCard / Transfer / 现场流转 | mes_transfer_config, mes_transfer_config_action, mes_transfer_action_def, mes_transfer_scene_def, mes_transfer_record, mes_transfer_action_log, pro_trans_order, pro_trans_consume | service/transfer/impl/TransferApplicationServiceImpl；service/transfer/action；md/MesTransferConfigAggregateServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | Source 有流程批号与动作日志，不把所有转序视为单独完整 FlowCard 主模型；动作匹配、权限与外部 adapter |
| Heat Treatment | mes_heat_treatment_record, mes_heat_treatment_photo, mes_heat_treatment_param_template, mes_heat_treatment_param_submit, mes_heat_treatment_param_record, mes_equipment | md/HeatTreatmentServiceImpl, HeatTreatmentParamTemplateServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 设备运行、批次子项、参数提交、结束/转序/追溯；quantity 当前为辅助参考值 |
| Quality Workflow / IQC-IPQC-OQC / 送检 | qc_template, qc_template_index, qc_template_product, qc_iqc, qc_iqc_line, qc_ipqc, qc_ipqc_line, qc_oqc, qc_oqc_line, inspection_record | qc/QcIqcController、QcIpqcController、QcOqcController；md/InspectionRecordServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 检验单状态、缺陷汇总、送检人身份、唯一性、结果修改及首次通知；Source 有质量能力，不能把整个 Quality 记 GAP |
| Step Inspection / 专项检测 | pro_process_step, pro_step_inspection_config, pro_step_inspection_record, qc_special_product_inspection | pro/stepinspection/StepInspectionServiceImpl、StepInspectionRecordServiceImpl；qcSpecialProductInspection | COMPLEX | REDESIGN | 不建表 | BACKLOG | 工序/工步/设备/终端准入、生产对象解析、检测值与配置；不是仅录入一张表 |
| Nonconformance / 不良申报 | qc_defective, qc_defective_user, qc_defective_responsible_user, qc_defective_cause, qc_defective_item_supplier | qc/QcDefectiveServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 保留不良事实/工序/物料；责任财务子规则单独剥离，不丢弃质量能力 |
| Traceability | pro_reporting_work, pro_reporting_work_material, pro_route_broken_step, mes_transfer_action_log, mes_heat_treatment_record | pro/controller/mobile/ProcessTraceabilityMobController；service/mobile/impl/FlowRetrospectMobileServiceImpl；md/HeatTreatmentServiceImpl | COMPLEX | REDESIGN | 不建表 | BACKLOG | 已有品号+批次、断接和热处理追溯；通用批次谱系闭环的差距单列 GAP |
| Warehouse / Inventory | wm_warehouse, wm_storage_location, wm_storage_area, wm_material_stock, wm_transaction及wm_*单据/行 | wm/StorageCoreServiceImpl, WmTransactionServiceImpl及单据Controller | COMPLEX | REDESIGN | 不建表 | BACKLOG | 主档可后续拆出；库存正负事务、线边库、出入库审核与回滚不能通用 CRUD |
| Work Center / Workstation & Resource | md_workstation, md_workstation_machine, md_workstation_worker, md_workstation_tool | md/MdWorkstationController, MdWorkstationServiceImpl及资源Service | COMPLEX | REDESIGN | 不建表 | BACKLOG | 对应工序、车间及资源绑定；单表服务简单不代表能力只含一张表 |
| Equipment / Types / Maintenance | dv_machinery_type, dv_machinery, dv_check_plan, dv_check_machinery, dv_check_subject, dv_repair, dv_repair_line, mes_equipment | dv/DvMachineryTypeServiceImpl, DvMachineryServiceImpl, DvCheckPlanServiceImpl, DvRepairServiceImpl；md/HeatTreatmentServiceImpl | COMPLEX | REDESIGN | 本轮只建独立 dv_subject | BACKLOG | 类型有 parent/ancestors；点检计划/维修明细、热处理设备重叠，先确认设备主责 |
| Tooling / 工装基础资料 | tm_tool_type, tm_tool | tm/TmToolTypeServiceImpl, TmToolServiceImpl | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 基础Service主要 CRUD；使用寿命、数量、工作站引用和页面未深审，作为下一简单候选 |
| Calendar / Shift / 班组排班 | cal_holiday, cal_shift, cal_team, cal_team_member, cal_teamshift, cal_plan, cal_plan_team | cal/*Controller, CalPlanServiceImpl | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 主档/排班分开；重叠时段、人员引用、日历计算未深审 |
| Planning / Scheduling | pro_monthly_plan, pro_task, pm_plan_need, pm_work_order_demand, pm_mrp_calculation, pm_minimum_purchase | pro/plan/MonthlyPlanService；pm/*ServiceImpl | COMPLEX | REVIEW | 不建表 | BACKLOG | 月计划存在产品/车间与下游执行依赖；MRP/采购子能力需要单独确认 Base 边界 |
| Production Monitoring / 人员分析 | pro_reporting_work及材料/工单/工序；bigdata_* | pro/productionmonitor、pro/personnelanalysis | COMPLEX | PORT | 不建统计副本 | BACKLOG_QUERY_ONLY | 可迁真实查询口径，优先 Query Service；不因统计查询机械创建 Aggregate |
| WIP Dashboard / 在制展示 | Source SimulationWipMapper 查询与生产报工/入库数据 | dashboard/SimulationWipServiceImpl | COMPLEX | REVIEW | 不建表 | REVIEW_REQUIRED | 实际汇总有真实生产数据来源，不能仅凭 simulation 命名 DROP；motorcycle/auto 专用展示与通用 WIP 分开 |
| Sales / Inquiry / Customer Pricing | so_sal_order, so_order_goods, so_order_recipient, so_customer, so_customer_product_price, so_goods_info, so_purchase_inquiry, so_inquiry_supplier, sale_check_info | so/salesorder、salesorderline、customerProductPrice、inquiry | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 表名来自对应Mapper XML；报价/销售可选边界，不伪称所有 MES 必须照搬 |
| Personnel / Attendance / Safety / Pallet | 考勤/人员、mes_safety_event、pallet_inventory及明细/邮件 | hr、check、dashboard/SafetyEventServiceImpl、pallet | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 工厂生产支持能力与行政/客户用途分别核实；当前不导入个人数据 |
| Print / Report Templates / 提醒 | pro_hiprint_template, ureport_file_tbl, pro_antirust_cycle及mail | print、report、mail、pro/ProAntirustCycle* | UNKNOWN | REVIEW | 不建表 | REVIEW_REQUIRED | 保留产品候选，未证明所有模板通用或许可明确；不复制客户版式/内网收件人 |
| 工资/计件结算子能力 | fd_finance_detail, finance_material_bad；pro_process单价及报工工资关联 | service/impl/FinancialManagementServiceImpl | COMPLEX | DROP | 不建表；不迁工资字段与计算 | EXCLUDED | DROP_REASON：当前 Base 目标为通用 MES，工资/扣款属本轮明确允许排除的行政财务规则；生产数量事实仍保留 |
| ERP 固定仓库规则 | 无独立通用表；车间外部绑定字段 | erpapi/IssueReceiptServiceImpl中的固定仓库9998及绑定读取 | SIMPLE | DROP | 车间核心中移除外部绑定 | EXCLUDED | DROP_REASON：固定外部仓库/组织编码不具有可直接复用的通用 MES 语义；不 DROP 通用领料能力 |
| 无主业务引用的备份/测试表 | pro_reporting_work_copy1/2, finance_material_bad_copy1, db_test, test_tab, temp_name | 当前ktg-mes main Java/资源未发现这些标识引用 | UNKNOWN | DROP | 不建表、不复制数据 | EXCLUDED | DROP_REASON：备份/实验资产不是运行能力；静态无引用不等于现场无人使用，外部消费者未知 |
| 路线版本冻结 / 通用 WIP / 通用谱系 / 质量处置闭环 | 未确认独立完整契约 | 见GAP证据 | UNKNOWN | GAP | 不建表 | RECORDED_NOT_IMPLEMENTED | 记录具体差距，不能把已有 Routing/WIP查询/Traceability/Quality 整体说成不存在 |

## SIMPLE_BUSINESS_LIST / SCHEMA_CREATED

以下为第一轮实际建表与当时策略的历史操作记录，Migration Status仅是结构技术状态。
当前五表业务迁移状态均为REVIEW，不能据此继续生成。

实际写入范围仅为当前 `application-dev.yml` 的 `mes.codegen.profiles.dev` 指定业务测试库
`mes_base_dev_business`。读取当前中心绑定时，启用的 `DEV` 和 `1` 都指向此测试库；
这不证明两厂独立库隔离。本轮没有调整绑定或 Foundation。

| Source Table | Target Table | Strategy | Key Changes | Factory Scope | Converter Needed | Migration Status |
|---|---|---|---|---|---|---|
| md_unit_measure | md_unit_measure | ADAPT_SCHEMA | factory_id；精确换算率；去attr | FACTORY_SCOPED | 是，unit-measure.sql | SCHEMA_READY |
| md_workshop | md_workshop | ADAPT_SCHEMA | factory_id；精确面积；去ERP绑定/名称快照；部门引用放宽 | FACTORY_SCOPED | 是，workshop.sql | SCHEMA_READY |
| qc_index | qc_index | GENERATE | factory_id；去attr；查询索引 | FACTORY_SCOPED | 是，quality-index.sql | SCHEMA_READY |
| qc_defect | qc_defect | GENERATE | factory_id；去attr；查询索引 | FACTORY_SCOPED | 是，quality-defect.sql | SCHEMA_READY |
| dv_subject | dv_subject | GENERATE | factory_id；去attr；名称可空；取消类型0默认 | FACTORY_SCOPED | 是，equipment-subject.sql | SCHEMA_READY |

首轮曾判断五表可后续Codegen，该判断现已撤回：它证明了表结构可生成，未证明能力应该独立进入Base。
只有先完成现实必要性、所属领域及引用/执行模型审查，才重新判断生成适用性；当前五表全部REVIEW。
首轮未调用Codegen，也未迁旧Java/Vue样板，第二轮继续暂停实现。

建表、索引、COMMENT、字段映射及验证证据见 [SIMPLE_SCHEMA_REVIEW](SIMPLE_SCHEMA_REVIEW.md)。
复杂能力下一步见 [COMPLEX_BUSINESS_BACKLOG](COMPLEX_BUSINESS_BACKLOG.md)；
GAP 见 [MES_BUSINESS_GAP](MES_BUSINESS_GAP.md)。

## 证据与完成边界

- Source 证据：仓库 DDL 快照、当前 Entity/Mapper/Service/Controller 与必要 Vue 页面静态审查。
  没有生产 metadata/数据/运行结果，也没有读取 Source application.yml 的连接值。
- 目标证据：实际 MySQL 8.2.0；独立 `flyway_business_schema_history`，0基线＋业务V1；
  首次应用1，validate通过，再次应用0。旧 `flyway_schema_history` 未修改。
- 真实旧数据 Converter 未运行；合成输入的 SQL 验证单独记录，不能写 BUSINESS_VERIFIED。
- 不新增唯一键、外键或 CHECK。`(factory_id, code)` 是普通索引，重复编码并发语义留待具体能力实现。
- Source 文件、既有 Base 工单/验收样板、OpenAPI 未提交工作、UI、服务与数据源绑定保持未改。
  无提交、推送、部署或生产数据库访问。

## 2026-10-12 第二轮：五表决定重审

上方五个受影响能力已改为当前状态，其他条目保留首轮Source盘点；现实必要性完整分类见第二轮文档。
下面显式保留首轮五表判断，以免修正当前状态时抹去历史。

| 表 | 首轮判断（历史） | 当前迁移状态 | 第二轮处理 |
|---|---|---|---|
| md_unit_measure | SIMPLE / ADAPT_SCHEMA / SCHEMA_READY | REVIEW / RECONSIDER | 单位语义可保留为投影候选；独立主数据与通用换算不默认迁入 |
| md_workshop | SIMPLE / ADAPT_SCHEMA / SCHEMA_READY | REVIEW / RECONSIDER | Workshop是可选组织层，不强制所有Factory下再建车间 |
| qc_index | SIMPLE / GENERATE / SCHEMA_READY | REVIEW / RECONSIDER | 归质量检测定义/执行整体模型，不能按独立字典提前生成 |
| qc_defect | SIMPLE / GENERATE / SCHEMA_READY | REVIEW / RECONSIDER | CRUD简单，但执行链未证明引用字典；撤回单独生成决定 |
| dv_subject | SIMPLE / GENERATE / SCHEMA_READY | REVIEW / DROP_CANDIDATE | 默认Base不迁设备维护套件；作为可选维护扩展DEFER，表不删 |

没有修改五表DDL/Converter或任何数据库。复杂能力的现实必要性与后续分析优先级以第二轮审查为准，
不能继续用“Source有此功能”或“表简单”决定默认迁移。

## 2026-10-12 用户批准的第一批 SIMPLE 实施

| MODULE | SOURCE_TABLE | TARGET_TABLE | Complexity | Strategy | Sync | Status |
|---|---|---|---|---|---|---|
| 物料产品分类 | md_item_type | material_category | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |
| 计量单位 | md_unit_measure | unit_measure | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |
| 工作站 | md_workstation | workstation | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |
| 车间 | md_workshop | workshop | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |
| 物料产品 | md_item | material | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |
| 安全库存 | md_item.safe_stock_flag/min_stock/max_stock | safety_stock | SIMPLE | ADAPT_SCHEMA / GENERATE | ERP_SYNC=true；人工 CRUD 可独立使用 | IMPLEMENTED / ACCEPTED |

当前六项的 Schema、规则、权限、Converter 和证据逐项见验收报告。Workshop 的 OPTIONAL 指组织层级可省略，不表示本轮暂缓；安全库存暂放基础数据，因为当前只维护物料阈值，不实施 MRP。
