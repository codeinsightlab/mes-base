# MES Capability Review

> **2026-10-12 第一批实施：用户已批准六项按 SIMPLE 迁入，ERP_SYNC 全部启用。**
> 当前决定和验收见 [SIMPLE_BUSINESS_MIGRATION_ACCEPTANCE](SIMPLE_BUSINESS_MIGRATION_ACCEPTANCE.md)。
> 第二轮审查保留为历史；其中对本批六项的 DEFER/REDESIGN 不再决定本轮范围，其他业务未获本轮实施授权。


2026-10-12；第二轮，仅审查与修正迁移决策。Source为`/Users/user/work/heri/ktg-mes`的
`master / 42a81cc577dc77b05a6a67f6be0a9466e40784e1`，排除`ktg-wheel`。
必要页面只读核对`/Users/user/work/heri/ktg-mes-ui`。本轮没有数据库连接、DDL、Codegen、
业务代码、前端、Foundation、测试实现或服务修改。

## 1. 当前决定与判断方法

撤回首轮“五个简单表已经是可迁入的简单能力”的结论。五表物理保留，迁移状态全部回到
`REVIEW / RECONSIDER`；`SCHEMA_READY`仅保留为首轮已建表/验证的技术事实。
本轮没有确认任何独立能力`MIGRATE_NOW`，不因已经建表而反向证明产品必要性。

判断顺序：制造现场需要什么→Source实际流程→能力复杂度→Schema/代码复用。
`CORE/COMMON/OPTIONAL/NOT_BASE`是对当前Base最小生产执行产品的评审判断，
不是对所有行业的强制标准，也不是市场统计。`COMMON`不自动等于核心闭环第一批必须实现。
高复杂度也不等于全部重写；Source规则、字段来源、SQL、页面动作和有效算法仍是复用事实。

现实边界参考：[OPC Foundation ISA-95说明](https://reference.opcfoundation.org/specs/OPC-10030/4.2.3)
区分生产运行与企业计划/物流，强调制造系统之间需要交换物料、设备、人员等信息；
[ISA-95公开介绍](https://www.isa.org/standards-and-publications/isa-standards/isa-95-standard)
提供活动边界参考。它们不要求一个Base同时实现ERP/WMS/QMS/APS/EAM，也不规定本文五张表。
下面的默认范围由本项目最小执行闭环推导，不冒称标准给出了同名CORE分类。

复杂度按**能力真实操作**判断；单表CRUD简单性另外记录。
复用评审仅对`CORE/COMMON`且Source复杂度已理解者准入；其他行的`DEFER/DROP`
只是范围处理，未准入者不评定具体Schema可复用，不承诺生成或代码PORT。
`PORT`需要成熟度和适配成本证据，本轮静态证据不足以批准整套PORT。

## 2. Capability Review Matrix

Reality Reason逐项说明：现场问题、用户、生产阶段、缺少后的影响、系统主责和适用范围。
E编号指向第6节的实际Source入口；“一轮入口”只表示已识别，未深审者保持UNKNOWN。

| Capability | Source Evidence | MES Necessity | Reality Reason | Source Complexity | Dependencies | Strategy | Decision |
|---|---|---|---|---|---|---|---|
| Material / Product执行投影 | E04、ERP物料来源XML、MdItem Entity | CORE | 工艺员/作业员在派工、报工识别生产对象、规格和数量单位；没有它会混淆执行对象。ERP/PLM可权威维护，MES保存执行所需投影；跨行业通用。不是复制完整物料主数据系统。 | SIMPLE | 单位语义、工艺、BOM、条码、ERP来源 | ADAPT_SCHEMA / GENERATE | IMPLEMENTED / ACCEPTED |
| Operation执行定义 | E05、pro_process及人员/检验/内容关联 | CORE | 工艺员定义要做的作业，作业员按工序执行、班组长看进度；没有作业身份无法定位报工。MES需要可执行定义，工艺权威可来自PLM；普遍适用，但计件单价不是核心。 | COMPLEX | 人员、Routing、检测、文件 | REDESIGN | REDESIGN_LATER |
| Routing / 执行顺序 | E06、pro_route/process/product/BOM | CORE | 工艺员/班组长在工单下达及转序确定先做什么、何时可做下一步；缺少后进度和完工无依据。MES负责采用执行顺序，复杂路线可由上游提供；简单生产可只有一步。 | COMPLEX | 产品、Operation、WorkOrder、Reporting | REDESIGN | REDESIGN_LATER |
| WorkOrder / Job Execution | E07、E08及pro_task | CORE | 计划员下达、班组长派工、作业员执行、生产主管关闭；没有执行任务就无法对应计划与现场结果。ERP可产生需求，MES负责现场执行状态；多数制造通用。 | COMPLEX | Routing、产品、数量、任务、Reporting | REDESIGN | REDESIGN_LATER |
| Production Reporting | E09、E10、移动WorkReportController | CORE | 作业员在工序完成时记录实际人/时间/产出，班组长核对进度；缺少后只有计划没有执行事实。MES主责，工资只是下游用途；多数生产方式适用，采集方式可人工或设备。 | COMPLEX | 工单、工序、材料可用量、WIP、质量、入库 | REDESIGN | REDESIGN_LATER |
| 基础WIP / 工序进度 | E09.executeMaterial、E10状态更新、E22 | CORE | 班组长/作业员在待加工、加工、待转序和完工时核对剩余量与所在工序；没有它无法判断下一步及避免重复累计。MES主责；基础语义通用，全仓位库存台账并非默认要求。 | COMPLEX | 工单、Reporting、Routing、投入/产出 | REDESIGN | REDESIGN_LATER |
| 基础Execution Traceability | E09执行事实、E20批次查询、E18关联记录 | CORE | 生产/质量人员在异常追查时回答哪个对象由谁何时在哪道工序生产；缺少后无法解释报工与异常来源。MES保留执行关联；批次/序列号粒度按产品确定，不强制复杂谱系。 | COMPLEX | 工单、工序、执行对象、报工、质量结果 | REDESIGN | REDESIGN_LATER |
| 工作站基础执行位置 | E03、md_workstation | CORE | 用户批准的简单位置目录，供未来执行引用；不迁设备/人员/线边库/条码。统一术语 Workstation，车间可选。 | SIMPLE | 可选 Workshop | ADAPT_SCHEMA / GENERATE | IMPLEMENTED / ACCEPTED |
| Quality Inspection定义与执行 | E11、E12、E13、E23、E26 | COMMON | 质量工程师配置要求，检验员在来料/工序/完工检验记录结果；缺少后MES无法挂接检测依据与结果。MES执行、QMS可供规则；常见，但完整IQC/OQC、SPC和审批体系不是所有基础MES必需。 | COMPLEX | 产品、工序/工步、模板、阈值、单据、缺陷、通知 | REDESIGN | REDESIGN_LATER |
| Defect Management / 不良事实 | E13、QcDefectiveServiceImpl、E10 | COMMON | 检验员/作业员发现不良，质量/生产人员核对缺陷数量与影响；缺少后良品/不良及原因难解释。MES记录执行事实，QMS可负责处置；常见，不默认迁责任扣款。 | COMPLEX | 检验单/行、工序、报工、材料、不良申报 | REDESIGN | REDESIGN_LATER |
| 缺陷分类目录（质量域子能力） | E14；执行记录未找到qc_defect ID引用 | COMMON | 质量工程师统一缺陷描述便于录入与统计；缺少目录仍可记录文本缺陷，但分类一致性下降。可由QMS供码表；常见辅助能力，须先确定质量执行如何引用。 | UNKNOWN | 质量定义、缺陷记录/分类契约 | DEFER | REVIEW |
| Unit Of Measure语义/目录投影 | E01、ERP UNIT来源、前端单位Store | COMMON | 物料/工艺人员及作业员在录入数量和检测值时使用单位，避免数量口径混淆；没有本地维护页面仍可使用权威投影。ERP可唯一权威；单位语义通用，独立主数据表不是必需。 | SIMPLE | Material、BOM、检测、报工；缓存/双来源 | ADAPT_SCHEMA / GENERATE | IMPLEMENTED / ACCEPTED |
| 通用单位换算能力 | change_rate仅确认存储/VO/表单使用 | OPTIONAL | 工程员在不同计量口径之间转换时需要规则；单单位生产不用转换。权威宜与ERP/物料规则一致，MES只在真实执行需要时使用；不是多数项目默认要一个全局换算引擎。 | UNKNOWN | 物料专属换算、精度、有效期、权威来源 | DEFER | DEFER |
| Workshop / 可选生产组织层级 | E02、E03、E25；workshop页面 | OPTIONAL | 多车间主管按车间分组资源与报表；单层工厂可直接按WorkCenter组织，缺少Workshop不破坏执行。MES可保留组织投影，部门不是同一概念；层级需求随工厂规模变化。 | SIMPLE | Factory、WorkCenter、部门；Source ERP仓库/组织绑定 | ADAPT_SCHEMA / GENERATE | IMPLEMENTED / ACCEPTED |
| 物料投入/领用执行事实 | E16、E09数量前置、E25 | COMMON | 配料员/作业员在开工和投料时核对实际投入，生产/质量人员追查材料来源；缺少后材料相关数量/追溯不足。MES存执行事实，ERP/WMS可负责领料单及库账；材料敏感流程常见，非所有报工入口都须领料单。 | COMPLEX | BOM用量、批次、工单、库存、ERP命令 | REDESIGN | REDESIGN_LATER |
| Production Receipt / 产出移交 | E17、E09末序入库 | COMMON | 生产/仓库人员在完工交接时确认产出与移交对象；缺少后完工与仓库收货可能脱节。MES记录产出/移交，ERP/WMS可开入库单；常见，ERP 5A00创建/审核不等于核心完工。 | COMPLEX | Reporting、工单、批次、仓库资格、ERP核对 | REDESIGN | REDESIGN_LATER |
| BOM / 作业指导引用 | MdProductBom、MdProductSop；E04、E07 | COMMON | 工艺员给出用料和作业要求，作业员生产时查看；缺少后执行依据不足。PLM/ERP可维护，MES需任务引用/投影；常见，完整BOM编辑与版本管理不必默认由MES主责。 | UNKNOWN | 产品、投入规则、文件及执行依据 | DEFER | REVIEW |
| 设备执行身份与可用状态 | dv_machinery、E03、E18 | COMMON | 派工员/作业员在机器执行场景选择资源、确认是否可用；缺少后设备执行归属难解释。MES引用资源/可用状态，资产权威可在EAM；机器生产常见，纯手工作业不必维护设备台账。 | COMPLEX | WorkCenter、设备主责、运行记录、停机 | REDESIGN | REDESIGN_LATER |
| 设备点检/保养/维修 | E15、dv_subject/check_subject/repair_line、点检页面 | OPTIONAL | 点检员/维修员在班前检查、定期维护和故障维修使用项目/计划；缺少会减少维护支持，不使工单报工天然失效。EAM/CMMS常为主责；有价值但不是基础MES默认维护套件。 | COMPLEX | 设备、项目、计划/状态、维修明细、人员 | DEFER | DEFER |
| Heat Treatment专用执行 | E18及热处理参数/照片/记录 | OPTIONAL | 炉台作业员/工艺员在热处理加工记录设备、批次和参数；缺少影响热处理行业执行，但普通装配不缺此能力。属于特定工艺MES，非客户私有就应DROP；按行业扩展接入。 | COMPLEX | 设备、批次、参数、转序、追溯 | DEFER | DEFER |
| 跨场所FlowCard / 动作配置 | E19及mes_transfer_* | OPTIONAL | 运输/接收/生产人员在跨场所移交时确认出入场动作；不跨场所的生产只需基本转序。MES可跟踪移交，物流/WMS处理运输库房；不是默认通用动作引擎。 | COMPLEX | 批次、路线、权限、动作Adapter、外部单据 | DEFER | DEFER |
| 全仓储/库存事务管理 | E21、StorageCoreServiceImpl、wm_* | OPTIONAL | 仓管员收发、盘点、调拨时维护实物库存；MES缺少全仓储仍可消费WMS/ERP供料结果。线边量可在MES，全仓储属WMS边界；一体化项目需要，非默认必装。 | COMPLEX | 仓库/库位、批次、单据、库存流水、冲销 | DEFER | DEFER |
| 基础生产查询 / 监控 | ProductionMonitorServiceImpl、PersonnelAnalysisServiceImpl、E22 | COMMON | 班组长/主管在班中查看进度和异常；没有查询看不到已经记录的执行事实。MES应提供基础查询，多屏预测和复杂分析可独立；通用，但不继承客户专用展示口径。 | COMPLEX | 报工、工单、WIP、查询口径/范围 | DEFER | REVIEW |
| 高级计划/排程/MRP | MonthlyPlanService、pm相关Mapper及排程页面 | OPTIONAL | 计划员做需求展开、有限能力优化及重排；没有它仍可执行外部下达计划。APS/ERP通常主责，MES可提供可执行任务队列；高级优化不是基础闭环默认要求。 | COMPLEX | 日历、产能、BOM、需求、库存、ERP来源 | DEFER | DEFER |
| 工装寿命 / 班组日历 | tm_*、cal_*一轮入口 | OPTIONAL | 工装管理员/班组长在资源寿命、轮班管理使用；不用相应模型仍可按已有任务执行。MES可引用，EAM/HR/APS也可主责；常见制造扩展但实际规则未深审。 | UNKNOWN | 设备、资源、人员、排班、寿命 | DEFER | REVIEW |
| 通用多级批次谱系 | E20断接追溯、E18热处理关联 | OPTIONAL | 质量/追溯人员在拆批、合批、多级装配时追根；无此功能会缺高级召回/谱系，而基础执行记录仍可成立。MES按工艺需求保存关联；非所有项目需要泛化多级图。 | UNKNOWN | 投入/产出、批次变换、Reporting、质量 | DEFER | DEFER |
| 财务/工资/采购销售/供应商完整档案 | E24、E09.computeAmount、fd/pm/so/erp入口 | NOT_BASE | 财务、采购、销售人员做结算和企业交易；没有这些不会使现场工序无法报工。ERP/财务主责；普遍存在不等于基础MES应复制整套模块，执行所需供应商/物料身份可投影。 | UNKNOWN | 企业交易/主数据、成本、人事 | DROP | DROP |
| 固定管理员/仓库/客户版式规则 | E05固定admin、E25仓库9998；报表版式待核 | NOT_BASE | 特定现场为默认授权/固定仓库映射服务；其他企业不具相同人员或编码。属于部署/客户规则，不是MES生产能力。去规则而保留工序/领料；版式所有权不明的部分继续REVIEW。 | MODERATE | 源特定账号、外部编码、客户用途 | DROP | DROP |
| 考勤/安全/托盘/通知/专用报表 | hr/check/pallet/dashboard/mail/report一轮入口 | REVIEW | 使用者和触发场景须逐项确认；可能是生产支持、行政或客户用途，没有统一的核心缺口。HR/EHS/WMS等主责各异；不因Source模块存在就默认纳入，也不整组武断DROP。 | UNKNOWN | 具体生产场景、设备/人员、外部消费者 | DEFER | REVIEW |

## 3. 已建五表重审

下表的“保留”是保留物理表/业务事实候选，不是冻结目标Schema。
撤回五表的提前GENERATE/ADAPT_SCHEMA准入，保留首轮技术证据；当前不生成、不改DDL、不删表。

| 表 / 所属能力 | MES Necessity | Source Business Meaning | Source Complexity | Dependency | Base Recommendation |
|---|---|---|---|---|---|
| md_unit_measure / Unit Of Measure | 单位语义/投影COMMON；独立维护及换算OPTIONAL | 启用单位下拉、主单位/换算比例维护；物料/BOM/模板/工单页面消费；ERP也返回UNIT编码 | MODERATE；局部CRUD确实简单，权威/执行换算未确认 | 物料、报工、质量模板、前后端范围缓存、ERP单位来源 | REVIEW / RECONSIDER；保留单位代码/名称投影候选，撤回独立主数据与通用换算默认迁入 |
| md_workshop / Production Organization | Workshop层OPTIONAL；执行资源身份CORE | 源工作站创建强制查车间并复制code/name；车间还提供ERP仓库/部门/员工绑定 | MODERATE；单表CRUD简单，但组织与绑定语义不能仅按列决定 | Factory、WorkCenter、部门与Source ERP收发料 | REVIEW / RECONSIDER；作为可选层级暂存，不能强制所有Base采用Factory→Workshop→WorkCenter |
| qc_index / Quality Inspection Model | COMMON，非独立核心字典 | 检测项定义→产品/检测类型模板→检验单行标准/阈值→结果/缺陷统计 | COMPLEX（所属完整能力）；配置表局部CRUD简单 | qc_template/index/product、工单/产品/工序、检验单/行、缺陷 | REVIEW / RECONSIDER；保留检测定义业务事实，先定质量执行模型，再决定是否沿用此表 |
| qc_defect / Defect Management的目录候选 | 统一缺陷分类COMMON；此Source字典实际接入仍REVIEW | 独立字典与自动编号；已审执行记录保存defectName/Level/Quantity，没有defectId | 自身CRUD SIMPLE；所属缺陷执行COMPLEX；字典接入UNKNOWN | 未来分类/结果契约；Source执行记录没有已证明的稳定字典引用 | REVIEW / RECONSIDER；撤回单独GENERATE，不能将孤立字典当作质量闭环已具备 |
| dv_subject / Equipment Maintenance | OPTIONAL，默认Base不必包含 | CHECK点检内容/标准项目，页面选入点检计划；维修明细也有项目字段但当前页面可直接录入文本 | COMPLEX（点检计划/设备/状态/维修能力）；字典CRUD SIMPLE | dv_check_plan/machinery/subject、dv_repair/line、设备/维修人员 | REVIEW / DROP_CANDIDATE（默认范围）/DEFER（维护扩展）；物理表保留，撤回默认MES迁入 |

### 3.1 单位：需要单位语义，不等于需要独立单位主数据

Source `MdUnitMeasureServiceImpl`提供CRUD和启用下拉；`UnitMeasureCache`按Factory cacheKey
保存revision/data，AOP在成功写入、事务提交后失效，前端`unitMeasure/Load`也有缓存。
这些是已有实现事实，不能为了“简单CRUD”忽略，但缓存本身也不使业务自动成为复杂领域。
物料与模板主要保存单位字符串；ERP物料/工单查询通过`UNIT`取得编码。
因此不能证明本地目录就是唯一权威，也不能仅凭ERP存在就宣布本地目录无用。

全量检索本轮Source主模块Java/XML的`getChangeRate/change_rate`：除Entity、Mapper、VO投影、
CRUD和下拉，未发现计算报工/库存时使用换算比例的稳定执行链。前端确认可填写和展示。
结论限于已审源码：Source“存了换算配置”，没有证明“执行换算业务已成熟”。
Base需要数量与单位口径明确；是否保留写入式目录、只读投影、物料专属换算，仍待具体执行需求。

### 3.2 车间：Source强制的层级不等于通用Base的必选层级

工作站Controller.add/edit实际查车间并复制名称，再选择/初始化线边库、生成条码。
车间本身CRUD无删除下游引用保护；SourceMapper保存部门/ERP绑定并由领料/入库调用消费。
必须分开执行资源身份、组织分组、人员部门、仓库绑定，不把这些字段组合永久作为通用实体。
多数需要资源分组的工厂可保留Workshop；单层资源布局可以Factory→WorkCenter。
这里的“可选”不否定车间概念价值，只撤回默认强制层级与已建表设计的产品准入。

### 3.3 检测项：已经确认是检测模型的一部分

`QcTemplateIndexController.add/edit`从`qc_index`复制编码/名称/类型/工具。
`QcTemplateIndex`另外定义方法、标准值、单位和上下限；模板与产品/检测类型关联。
`QcIpqcController.add`查工单产品与工序→按产品/检测类型取模板→建头→generateLine复制检测要求。
`QcDefectRecordController.updateList`写缺陷后更新检验行与表头CR/MAJ/MIN数量/比例。
实体、Mapper、页面及跨表操作证明：检测项只有code/name/type/tool不是完整检测执行模型。
先决定要求版本/结果依据/工序关联，之后才能讨论字典表生成，不能先迁表再用表定义领域。

### 3.4 缺陷字典：不能凭领域名称伪造关联

当前`IQcDefectService/QcDefectMapper`引用集中于该字典Controller、Service、Mapper、Entity；
前端`qcdefect` API调用也集中于独立管理页。`qc_defect_record`保存描述、等级、数量及单据/行引用，
没有defectId；缺陷录入页面可直接编辑描述/等级。
这是“已审主模块未找到稳定字典消费”的负证据，不是证明现场没有其他消费者。

因此，字典CRUD判SIMPLE本身并非事实错误；错误是未审质量领域的引用/结果语义就批准GENERATE。
不能反向捏造“qc_defect更新自动影响质量判定”。复杂的是缺陷执行/统计/不良申报能力，
不是这些名称相近的表之间必然有强外键。

### 3.5 点检项目：确定为维护项目，不是生产Operation

`checkplan/subject.vue`以`subjectType="CHECK"`选择字典项目，把planId加入选中对象再调用新增接口。
点检项目包含内容、标准、类型与启用标记。`DvCheckPlanController.edit`在完成状态要求设备和项目；
草稿才可删计划，并在事务内删除设备/项目关联。维修明细具有subject字段，但当前维修页面允许文本填写，
不能说所有维修都强制引用字典。
使用者是点检/维修人员，流程是设备检查与维护；对MES可有用，但当前默认Base不建设EAM/CMMS套件。
不是公司私有资产，仅从默认产品范围移出；未来设备维护扩展再决定是否复用。

## 4. 复杂在哪里：Source真实操作与复用价值

| 能力 | 实际调用链/变化 | 已识别复杂点 | 值得复用 / 必须重新决定 |
|---|---|---|---|
| WorkOrder | E07.add→工单→generateBomLine；edit可重建BOM；dofinish更新所有任务再更新工单；E08详情触发ERP同步 | 父子/BOM/任务联动、状态权威、数量/完工入口；移动报工E10还有完成状态更新。手工dofinish代码直接置FINISHED，不能宣称已核对全部数量或质量 | 复用计划/产品/数量/任务事实及入口差异；重新划定MES执行状态、完成规则、读命令和BOM事务，不把完整ERP工单生命周期复制为Core |
| Reporting | E09.PreVerify/ExistPermission→quantityVerity→executeInsert→executeMaterial；E10移动入口；末序锻接可创建确认ERP入库 | 首序材料/BOM数量、上序产出、人员工序准入、acceptedQty扣减/下序累计、状态、报工事实、工资计算、末序入库/断接；移动入口有JVM锁，不证明跨节点幂等 | 复用数量来源/分支和生产事实；剥离工资与客户锻接规则；不同入口共享执行规则、事务与重复请求语义；没有证明当前全部状态一贯正确 |
| Routing / Operation | E05新增复制人员/不良原因并绑定admin；E06按产品启用路线、工作站工序判关键工序及前后序 | 配置影响执行、人员/检测关联、单价有效期、路线选择与实时改配置；表少不等于执行关系简单 | 复用工序/序号/关键点/产品关联事实；不迁固定admin/计件价格；决定执行采用的路线依据和变更影响 |
| Quality | E11模板引用检测项→E12工单/产品/工序/模板生成检验行→E13缺陷统计；E23送检结果与首次通知；E26工步/终端准入 | 要求与结果分离、质量判定依据、缺陷数量、单据状态/草稿删除、多表事务、人员身份、唯一送检、外部通知 | 复用检测要求/结果/缺陷事实；区分模板版本、单次结果、缺陷目录及处置。没有证据批准整体照搬所有IQC/OQC/SPC/责任财务 |
| Material Issue | E16.create/update在事务内协调头/明细与待审更新；approve/核对走外部链；E09首序还读ERP MO材料 | 来源工单/BOM、投入数量、审核、外部创建/确认、本地链接/UNKNOWN、恢复与删除限制 | 复用实际投入与数量规则；完整ERP领料单是外部适配，不作为所有Base开工的默认前置 |
| Production Receipt | E17.create核对来源/占用；approve claimPending→核对余量→创建ERP 5A00→确认→读取核对；delete按状态/关联拒绝或条件删除 | 多入口数量占用、数据库抢占、外部结果、本地/外部失配及恢复；本地事务无法撤回HTTP | 复用产出移交/来源/数量事实；MES完工与ERP/WMS入库拆主责，保留真实拒绝/不确定结果，不预建外部连接 |
| WIP / Traceability | E09扣当前acceptedQty并建/增下一序材料；E10另更新完成标记；E22聚合展示；E20当前/断接后批次查询 | 数量累计、工序与批次身份变化、多入口状态；显示WIP不等于统一台账；E20.get(0)的空/多级语义未验证 | 复用基础执行关联与查询口径；先保证基本剩余量/进度/因果一致，再讨论通用多级谱系与全库存守恒 |
| WorkCenter / Equipment | E03创建资源还查车间/工序、初始化线边库、生成条码；E15设备点检计划；E18设备运行/热处理记录 | 一个新增资源动作影响库存/条码；执行资源与资产维护、特定工艺状态混合 | 复用资源身份/所属及运行事实；核心仅执行引用，Workshop层级、维护、热处理按需求另定，不从Source整体创造万能资源领域 |

生产监控类复杂SQL可复用为Query Service候选；不因复杂度高机械引入Aggregate。
本轮没有执行Source测试/服务，不以注解存在证明事务或并发成立，不以老系统长期使用证明每条链成熟。

## 5. 最小闭环、后续优先级与GAP

建议Base默认业务闭环：

`执行对象/单位口径 → 工单及采用的工序顺序 → 执行资源/人员 → 开始与报工 → 工序剩余量/进度 → 完工 → 可查询的执行记录`

有物料投入的流程加入投入/产出关联；要求检验的流程挂接质量结果，不能用缺失结果伪造通过。
“数量有合格/不良口径”不等于默认建设全QMS；“有执行资源”不等于必须建Workshop/设备维护；
“完工”不等于必须自动生成ERP入库单。路线可单步，生产对象可工单/批次/序列号，不预建全部模式。

后续分析优先级：

1. **P0：WorkOrder + Operation/Routing + Reporting + 基础WIP一起定最小闭环。**
   先确定同一生产对象的状态/数量/完工依据，不能把四个独立CRUD误作闭环。
2. **P0依赖：Material/Product投影、单位口径、执行资源身份。**
   确定本地必要字段与上游权威；不先锁定独立单位/车间层级。
3. **P1：质量执行与缺陷事实；按实际投入/移交流程审Material Issue/Production Receipt。**
   三者与报工有实际Source依赖，但完整QMS/ERP单据不默认成为Core。
4. **P2：设备维护、热处理、跨场所流转、全仓储、高级排程及多级谱系。**
   有价值时另立能力任务；未知的日历、工装、行政/大屏模块继续REVIEW。

四项GAP重审见[MES_BUSINESS_GAP](MES_BUSINESS_GAP.md)：
路线版本冻结`COMMON_GAP`（执行依据可回查更基础）；基础WIP口径`CORE_GAP`（仅最小进度/数量，Source已有局部事实）；
通用多级批次谱系`OPTIONAL_GAP`；质量处置闭环`REVIEW`（候选需要OPTIONAL，完整缺失尚未证明）。
GAP值是后续分析必要性，不是本轮建表授权；不能从“架构应有”推断Source现场完全没有。

## 6. Source Evidence入口

证据等级均为静态源码/页面。更完整的Entity、XML、页面及文件指纹在
[SOURCE_BUSINESS_EVIDENCE.json](SOURCE_BUSINESS_EVIDENCE.json)的`round2_review`中。

| ID | 可定位的入口 | 说明 |
|---|---|---|
| E01 | [MdUnitMeasureServiceImpl:28](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/service/impl/MdUnitMeasureServiceImpl.java:28) | 下拉/VO与CRUD；另核对UnitMeasureCache/Aspect |
| E02 | [MdWorkshopController:97](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/controller/MdWorkshopController.java:97) | 编码/名称查重与普通维护 |
| E03 | [MdWorkstationController:121](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/controller/MdWorkstationController.java:121) | 车间/工序/线边库/条码协调 |
| E04 | [MdItemController:70](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/controller/MdItemController.java:70) | 分类赋值、条码、安全库存规则 |
| E05 | [ProProcessServiceImpl:145](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProProcessServiceImpl.java:145) | 复制关系、默认admin与价格规则 |
| E06 | [ProRouteProcessServiceImpl:103](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProRouteProcessServiceImpl.java:103) | 关键工序、前后序、启用路线 |
| E07 | [ProWorkorderController:280](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/controller/ProWorkorderController.java:280) | 任务和工单完成状态联动 |
| E08 | [ProWorkorderServiceImpl:130](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProWorkorderServiceImpl.java:130) | 详情读入口触发ERP同步 |
| E09 | [ProReportingWorkServiceImpl:391](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProReportingWorkServiceImpl.java:391) | executeInsert→executeMaterial、数量/状态、工资、末序 |
| E10 | [WorkReportMobileServiceImpl:177](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/mobile/impl/WorkReportMobileServiceImpl.java:177) | 移动入口状态与数量处理 |
| E11 | [QcTemplateIndexController:86](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/qc/controller/QcTemplateIndexController.java:86) | 检测项进入模板 |
| E12 | [QcIpqcController:106](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/qc/controller/QcIpqcController.java:106) | 产品/工序→模板→头/行 |
| E13 | [QcDefectRecordController:127](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/qc/controller/QcDefectRecordController.java:127) | 缺陷写入及头/行数量汇总 |
| E14 | [QcDefectController:86](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/qc/controller/QcDefectController.java:86) | 孤立字典/流水号；外部引用未查到 |
| E15 | [DvCheckPlanController:106](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/dv/controller/DvCheckPlanController.java:106) | 完成要求设备/项目、草稿删除及事务 |
| E16 | [ProductionMaterialIssueServiceImpl:129](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProductionMaterialIssueServiceImpl.java:129) | 领用头/明细、数量、审批/外部核对 |
| E17 | [ProductionInboundReceiptConfirmServiceImpl:155](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/service/impl/ProductionInboundReceiptConfirmServiceImpl.java:155) | 审批抢占与ERP入库/确认/核对 |
| E18 | [HeatTreatmentServiceImpl:160](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/service/impl/HeatTreatmentServiceImpl.java:160) | 设备运行记录及热处理关系 |
| E19 | [TransferApplicationServiceImpl:135](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/transfer/impl/TransferApplicationServiceImpl.java:135) | 流转动作匹配/Adapter/状态 |
| E20 | [ProcessTraceabilityMobController:31](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/controller/mobile/ProcessTraceabilityMobController.java:31) | 品号/批次、断接当前/后阶查询 |
| E21 | [WmTransactionServiceImpl:41](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/wm/service/impl/WmTransactionServiceImpl.java:41) | 库存增减/负库存判断/流水与JVM锁 |
| E22 | [SimulationWipServiceImpl:148](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/dashboard/service/impl/SimulationWipServiceImpl.java:148) | 真实生产来源的WIP聚合，不按simulation名DROP |
| E23 | [InspectionRecordServiceImpl:148](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/service/impl/InspectionRecordServiceImpl.java:148) | 送检身份、唯一冲突、结果修改与首次通知 |
| E24 | [FinancialManagementServiceImpl:75](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/impl/FinancialManagementServiceImpl.java:75) | 生产记录被用于工资/结算，不迁财务用途 |
| E25 | [IssueReceiptServiceImpl:621](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/service/impl/IssueReceiptServiceImpl.java:621) | 固定仓库9998；另有workshop.attr1绑定 |
| E26 | [StepInspectionServiceImpl:98](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/stepinspection/service/impl/StepInspectionServiceImpl.java:98) | 终端/启用/生产对象/工序准入 |

## 7. 十个直接答案与验证边界

1. 真正Core：生产对象、作业/顺序、执行任务、资源引用、报工、基本WIP进度和执行事实追溯；不是这些表名的合集。
2. 常见标准候选：单位投影、检验执行、缺陷事实、用料与产出移交、基础查询。Workshop、维护、热处理、全仓储和高级排程可选。
3. Source特有：默认admin关联、固定ERP仓库/组织/单据规则、计件工资和客户版式。去特有规则，不丢通用工序/领料/质量事实。
4. 简单表隶属复杂领域：qc_index对应检测定义/执行；dv_subject对应设备点检维护；工序/路线/状态配置也必须跟随领域；qc_defect的执行接入并未证明。
5. 首轮误判：qc_index、dv_subject被当独立简单能力；qc_defect未经接入审查被批准GENERATE。单位/车间的局部CRUD简单性不是错，默认迁入必要性及Schema锁定缺证据。
6. 已建表：物理五表全部暂留；业务事实候选保留单位语义、可选组织分组、检测定义。没有任何一张被批准作为最终Base表结构。
7. 撤回：五表的提前生成/适配准入；尤其qc_defect字典接入和dv_subject默认范围。后者标DROP_CANDIDATE，不执行DROP。
8. 优先分析：工单/路线/报工/WIP联合闭环，接着执行投影与资源，再按真实流程审质量、投入与产出移交。
9. 最小闭环见第5节；上游权威主数据可以投影，基础MES不强制维护完整企业档案。
10. 值得以后补：先统一基础进度/数量与可回查执行依据；高级版本发布、多级谱系和处置体系按真实需求，不作为“架构漂亮”必建能力。

已验证：Source静态调用链/字段/页面、引用检索范围、本文证据链接及DDL/Converter未改。
未验证：现场实际使用、生产metadata/数据、Source测试/HTTP/UI运行、完整复检/处置链、外部消费者、跨节点并发。
未运行Maven/UI测试：本轮只改审查文档，不修改任何实现；首轮构建记录仍是历史记录，不宣称本轮重验通过。

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
