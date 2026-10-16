# Report B：Production Chain Audit

> 2026-10-14 后续任务已实施 Production Core V1 的选路、正式领料与开工。本文仍是实施前只读审查时间线；当前实现与最终门禁分别见 [Implementation Report](2026-10-14-production-core-v1-implementation-report.md) 和 [Verification Report](2026-10-14-production-core-v1-verification-report.md)，不要将本文旧缺口或旧验收状态当作当前源码结论。

> 本文下方新增的「Production Chain Audit Report：独立端到端复核」是用户重新发起只读审查后的最新结论。此前章节保留历史时间线；特别是此前“V14普通code索引/编号并发重复”的判断有误，已由本次源码及真实Schema证据撤回，不能作为修复依据。

2026-10-14，Phase A最终最终全量Maven检查通过后进行只读审查。Phase A实施/运行边界见[Report A](2026-10-14-erp-domain-integration-review.md)。本阶段没有修改生产业务代码、执行数据写入或实现下述建议；没有以类名、旧报告或测试源码存在代替当前实现证据。

## 生产链路现状与业务对象职责

| 对象 | 当前代码事实 | 边界 |
| --- | --- | --- |
| WorkOrder | 生产需求：产品、计划量、默认确切工艺版本、DRAFT/RELEASED、revision；允许无Execution创建，发布校验产品与已发布适用版本 | 没有ERP生产工单创建/发布调用；默认工艺不是所有Execution的唯一实际版本 |
| ProductionExecution | 当前唯一的新执行身份；服务端EXE-{id}、固定所属工单/产品、独立routing_id、目标量、管理状态与时间 | 可以领料前预建READY；尚无实际路径/工序进度/产出事实/ERP批次身份 |
| ProductionBatch / BatchRouteNode | 20个旧批次、48个旧路径节点的历史读取；工单输入batches及选路写入口明确拒绝，未转换为Execution | Mapper历史表存在不等于新业务仍使用它们；不得恢复第二套写模型或按相同ID猜关联 |
| MaterialIssue / Item | 主子资料，单头可关联Execution；关联时服务端从Execution确定工单/产品/实际版本。多个领料单可关联同Execution；多个物料批次可成为明细 | DRAFT/RECORDED不代表ERP审核或库存扣减；新建单头可无明细，主子分别保存；空execution_id是管理员/历史兼容入口 |
| Routing / Node / Edge | 工艺版本DAG定义；边决定拓扑，display_order不决定执行；发布保存产品/工序快照；草稿编辑与revision条件更新；发布后只能启停或建新版本 | 不含执行路径、节点进度、AND/OR执行引擎。已开始Execution可引用曾发布后停用的原版本 |
| ERP Integration / Extra | 仓储领域本地创建/作废/查询及核实，原外部事实独立Extra；工单旧外部标识已迁Extra，provider历史未知 | 当前无生产ERP领域消费者、真实供应商实现、Gateway、继续审核或真实出库提交；Extra结构不意味着已联调 |

生产操作者/未来报工人按当前项目决定引用sys_user，不恢复Personnel模型。

## WorkOrder → Execution → MaterialIssue真实调用路径

1. `/api/production/workorder` → `WorkOrderController#create` → `WorkOrderService#create` → 产品/已发布适用工艺校验 → generated WorkorderMapper + WorkorderErpFacts 同事务保存。没有批次必填或ERP调用。
2. `/{id}/actions/release` → Service父行锁/revision检查 → MES RELEASED。没有真实ERP下达。
3. `/api/production/mes-production-execution` → Controller → `MesProductionExecutionServiceImpl#insert` → `ProductionExecutionRules#initialize`：同库工单/产品、确切适用已发布工艺、READY空时间 → 持久化稳定EXE-{id}。不要求先领料；同工单允许多个Execution以及不同已发布版本。
4. `/api/issue/mes-material-issue` → Controller → `MesMaterialIssueServiceImpl#insert` → `MaterialIssueRules#validate` 锁Execution/工单校验引用 → 单头保存 → LOCAL_ONLY领域创建结果 → Extra保存。该本地调用没有外部副作用；不是整单提交、库存出库、选路或锁路。明细另由Item Service固定父单维护。
5. Execution普通PUT → `ProductionExecutionRules#updating/validateValues`：工单RELEASED是RUNNING/COMPLETED或开始时间的门禁；时间顺序与部分冻结保护。没有专门开工动作、成功领料/已选路径准入或报工驱动的完成。
6. 领料作废：准备事务锁单/工单，检查Execution未开始，保存稳定voidOperationId及VOID_PENDING → 事务外仓储领域调用 → 短事务重锁并校验操作身份/Extra版本 → VOIDED、恢复原MES状态（明确拒绝）或VOID_UNKNOWN。
7. 查询/核实通过服务端Extra定位ERP；核实经领域规则及本地原子落地，UNKNOWN/NOT_FOUND不重建、不推进；作废与外部EFFECTIVE冲突不恢复MES，保留待处理事实。

旧参考项目仅核对 `IssueReceiptServiceImpl#insertDocNo5600Merge`（1152）、`checkAndInsertBuckle`、`docNo5600Merge`及创建/审核/查询方法（654、660、708起）：原流程关联MO docNo、物料批次和routeId，先记录本地扣料再协调OAPI，业务响应解析独立于HTTP200。它不能证明Base已实现同一流程，也不能证明供应商自动改变MO状态；本轮未复制厂商字段或外部数据库框架。

## Routing DAG与实际执行路径

当前只有DAG和Execution.routing_id，V16的14个Execution字段没有路径身份、锁定时间/操作身份或节点序列。旧batch_route_node只服务旧Batch历史。`WorkOrderService#selectPath`明确410；不存在新Execution选路API或报工表。

已发布内容不可变规则完整覆盖当前HTTP编辑/内容保存/删除入口：`RoutingServiceImpl#touch`草稿限定及revision条件更新，发布生成快照；同步发现已发布版本变化时新增DRAFT版本，不覆盖原已发布内容。RoutingNode/Edge没有独立可绕过父版本的HTTP写入口。启停不更改原节点/边。测试 `RoutingGraphValidatorTest`、`RoutingLifecycleContractTest`与本次完整检查支持此结论，不等于真实执行选路验收。

当前版本冻结早于真实出库：只要Execution有任意领料单头（含DRAFT/VOIDED），`hasIssues`即禁止换版本/删除；这保护历史引用，不能当作“选择完整路径并成功出库锁定”。后续同版本的A-B-C-E与A-B-D-E两条执行路径应分别保存在各自Execution，不能修改Routing DAG或复用旧Batch写链路。

最小后续设计（建议，未实施）：

- 以Execution为唯一父对象保存一条有序节点路径，每个节点引用其确切routing_id的RoutingNode；校验节点唯一、相邻边存在、连续顺序及完整起止规则。现有多入口/多出口DAG是合法定义，完整路径起止/关键节点规则需确认，不擅自加AND/OR引擎。
- 首次正式领料提交时，在Execution行锁下校验并保存选定路径、稳定领料操作身份与待处理状态；外部调用在事务外。PENDING/UNKNOWN禁止重选，保留同一操作核实；得到满足领域要求的成功后，短事务校验身份/版本并锁定原路径。
- 后续补料单只沿用已锁定路径，不重复选路/外部创建。多单只作废其中一单不能自动解锁；全部作废且未开工/无事实时是否允许重选由业务规则明确决定。
- 开工从明确命令入口校验工单RELEASED、所需领料已确认、路径锁定、无未知/冲突；报工引用execution_id和选定节点，在该路径顺序规则内推进。数量、操作者与实际事实独立记录，不把目标量/管理员COMPLETED当产量。
- 新路径Schema经模型确认、正式迁移、真实metadata Codegen生成基础后端；前端复用现有主子/执行组件。物理并发唯一约束如确有必要须用户决定，不自行新增。没有创建SQL或新生产代码。

## 工单、实际生产批次与外部身份

- ERP WorkOrder ↔ MES WorkOrder：没有已验证映射或同步/下达领域调用。workorder_erp_extra的一对一主键是MES存储关联；并不证明ERP与MES也是1:1，也不保证外部ID全库唯一。当前验收库有1条带旧外部标识的工单，provider未知，不能据此推断真实ERP关系。
- Execution承担生产批次身份，一个WorkOrder可多Execution；不能把库存批次等同WorkOrder或恢复第二套可写ProductionBatch。当前材料批次号是Item普通可空字符串，不是执行编号。
- 一Execution可多领料单，一领料单可多物料/批次。结构支持多批供料，尚未实现库存存在性/余额/BOM消耗或工序投料事实。
- 将来确有ERP执行批次号或产成品库存批号时，按各自外部事实/库存语义独立保存到相应Extra或真实库存事实，不能改写Execution本地主键/业务编号；目前无需求证明，不创建壳子。
- CANCELLED仍计入分配量：`allocationForOrders`对全部Execution SUM(target_qty)、COUNT(id)，没有状态过滤。remaining可为负、excess明确显示；没有分配硬上限。此前需求已将其称作目标量统计，此次不能擅自判错。若改为可用计划占用量，需要确认取消释放、重新激活和超额分配规则，再同时调整服务端/列表/统计/测试。

## 状态机、事务与生产编排矩阵

当前WorkOrder只有DRAFT/RELEASED。Execution管理状态READY/RUNNING/COMPLETED/CANCELLED不是完整生产状态机：普通PUT校验值与部分冻结，不检查所有前后态；允许符合时间条件的订正。MES状态与ERP状态独立；库存、报工、实际完成量未实现。

| 业务动作 | MES当前实现 | ERP领域调用 | 成功状态 | 异常状态 | 人工恢复 | 缺口 |
| --- | --- | --- | --- | --- | --- | --- |
| 创建工单 | Service校验需求/工艺、revision与本库持久化 | 未实现 | DRAFT | BAD_REQUEST/CONFLICT/引用拒绝 | 无执行/历史事实前可订正 | ERP映射未确认；编号并发唯一无DB保障 |
| 发布工单 | 锁行+revision+适用发布工艺 | 未实现 | RELEASED | 冲突/引用拒绝 | 无回撤动作 | 真实ERP下达、后续整单完成未实现 |
| 创建Execution | 独立稳定身份、确切发布工艺、READY空时间 | 未实现 | READY | EXECUTION_INVALID/引用拒绝 | 无开工/领料前可订正版本 | 实际路径、正式生命周期、数量占用决策 |
| 选择执行路径 | 未实现；旧Batch路径写入410 | 未实现 | 未实现 | LEGACY_BATCH_READ_ONLY（旧入口） | 未实现 | Execution有序节点、完整路径校验与锁定 |
| 创建领料单 | 主子资料分别保存、引用校验 | LOCAL_ONLY创建；CONNECTED完整提交未实现 | DRAFT/RECORDED资料状态 | 引用/格式/状态拒绝 | 本地资料编辑；查询/核实已有外部事实 | 明细原子提交、库存出库、ERP完整创建/审核、锁路 |
| 作废领料单 | 三段事务、稳定操作、Extra版本保护 | 默认本地cancel；Connected按原binding，真实厂商未接入 | VOIDED；LOCAL_ONLY外部影响NONE | VOID_UNKNOWN/VOID_PENDING；REJECTED恢复原态 | 同一作废操作查询、独立ERP核实 | 拒绝后的新操作重试、真实冲销、开工并发门禁 |
| 开始生产 | 普通PUT到RUNNING并提供startTime，要求RELEASED | 未实现 | RUNNING管理状态 | 工单未下达/冻结/时间拒绝 | 允许部分管理员订正 | 成功领料+路径锁定+待核实排除准入未实现 |
| 工序报工 | 未实现 | 未实现 | 未实现 | 未实现 | 未实现 | 路径顺序、节点身份、数量、sys_user事实 |
| 完工 | 正式完工未实现；可普通PUT维护COMPLETED/endTime | 未实现 | COMPLETED管理状态 | 时间/工单/冻结拒绝 | 管理员订正，非报工补偿 | 工序汇总/实际产出/库存入库/工单关闭未实现 |

作废后的工单产品订正只有在无Execution/旧Batch保护、所有关联领料已可信作废时才能放行；LOCAL_ONLY要求NONE，CONNECTED要求SUCCEEDED/REVERSED。任何Execution（含CANCELLED）仍冻结其工单产品身份，不能宣称“所有领料作废就一定能改产品”。

## P0 / P1 / P2问题与建议

证据分级：下表源码事实和明确缺口已核对；并发后果是基于锁/事务顺序的推理，未在真实ERP复现。当前无真实厂商/CONNECTED事实，未发现可证实的当前跨厂污染、重复ERP单据或错误路径已执行的P0；不能把“未实现路径”写成“错误路径已经运行”。原领料P0产品保护在本轮Extra迁移后HTTP Fixture及指定MySQL继续通过。

| 级别/模块与代码位置 | 当前实现/触发条件 | 实际影响 | 建议修复 | 现有数据影响 | Schema |
| --- | --- | --- | --- | --- | --- |
| P1 Execution路径，V16；WorkOrderService.java:249 | 只有routing_id，旧选路拒绝；分支DAG下正式开工 | 无法证明某执行采用哪条完整路径，也不能约束未来报工；当前无报工事实 | 首次正式领料选路+稳定意图+成功锁路+start门禁最小闭环 | 既有Execution没有可推断路径，须明确迁移/补选，禁止按展示顺序猜 | 是，新执行路径事实 |
| P1 Execution状态，ProductionExecutionRules.java:70/124 | 普通编辑传RUNNING/COMPLETED与时间，无领料/路径/报工准入 | 可以维护“已结束”而没有正式生产完成证据，尚非真实产量 | 正式开工/完工命令与实际事实，保留有权限的订正语义，不把普通编辑当执行器 | 审查既有管理状态，不能自动视为报工完成或批量回退 | 路径/报工需新增；仅入口限制可先无Schema |
| P1 MaterialIssue提交，MesMaterialIssueServiceImpl.java:113；Item Service | 单头可RECORDED但无明细；CONNECTED仍为行政资料，未调用完整create | 资料保存不等于实际领料/库存/ERP成功；无创建意图、完整载荷或锁路 | 明确submit用例：校验完整明细、固定Execution与路径、保存稳定操作、事务外领域调用、核实落地 | 原DRAFT/RECORDED不推定外部成功；历史来源不明仍待核实 | 正式意图/路径关联可能需迁移；复用现有Extra |
| P1 作废↔开工并发，MaterialIssueVoidService.java:88/121；ProductionExecutionRules.java:98 | 准备事务检查未开工，但事务外ERP期间开工入口不检查VOID_PENDING/UNKNOWN；落地不重查执行 | 静态可形成“准备作废时未开工、结果落地时已开工”的窗口；真实库存/ERP启用后会影响已用料安全，当前未联调 | 开工与作废共享准入规则/固定锁顺序；pending/unknown阻止开工；不能仅落地时拒绝已发生的外部冲销 | 复查已有pending与开始时间，保留冲突，不自动恢复/再冲销 | 门禁本身无；正式start事实可另立模型 |
| P1 WorkOrder编号并发，WorkOrderService.java:330；V14普通code索引 | uniqueCode先查后插，无唯一约束/同键锁；同编号并发建单 | 有两个事务同时查0并插入的可能，产生同编号不同本地主键；尚无ERP重复创建证据 | 先确认code唯一要求；需要DB唯一/幂等约束时请用户决定并检查存量冲突，不宣称当前并发安全 | 只读审查是否已有重复，治理不得覆盖/合并需求 | 如选择数据库唯一需迁移并获决定 |
| P1 作废拒绝后的恢复，MaterialIssueVoidService.java:83/134；MaterialIssueRules.java:94 | REJECTED恢复原态但保留voidOperationId；重复cancel返回原结果，普通编辑仍冻结 | 没有允许新尝试的授权入口，真实业务拒绝后需项目级处理 | 有真实供应商时新增明确的新操作重试/继续处理，旧操作不可复用写；当前不加假审核 | 保留旧拒绝/身份；不清空历史字段冒充没发生 | 视操作历史要求；当前最小状态可复用Extra |
| P2 查询关联管理，MaterialIssueEntity/WorkorderEntity及两Facts类 | 为现有VO保留未映射的外部投影访问器，真实持久化已归Extra | Entity名称容易使后续代码误以为字段在核心表；生成Mapper直读不能得到这些投影 | 后续真实供应商接入时以明确Business Query Model承接复杂联表响应；维护注释/不恢复主表映射 | 无需重写数据，本轮已有兼容服务填充 | 否 |
| P2 Schema注释，V13 planned_qty及V16状态注释 | 旧工单注释仍称“等于批次数量合计”，当前允许零Execution/超额且旧Batch只读 | metadata理解/生成展示容易沿用已退役语义 | 新正式迁移更新注释，保留旧Flyway；不以文档更改生产规则 | 只改metadata，不改值 | 是，COMMENT治理；未实施 |

CANCELLED计量、是否允许超额分配、完整路径起止/关键节点要求属于待决策项，不列为已证实P0缺陷。RoutingGraphValidator当前明确允许多入口/多出口且拒绝孤立发布节点（测试42行起），不能在只读审查中改成单入口/单出口规则。

## 推荐最小闭环顺序与待决策

先完成当前V22构建的用户服务重载、两权限配置与真实HTTP/UI复验；目前500不能作为生产就绪。然后优先补“Execution完整路径 → 正式领料提交 → 领域成功/UNKNOWN核实 → 锁定路径 → 开工准入”，再做路径内工序报工、产出汇总/完工及库存入库。原因是现有DAG仅定义可能性，直接开发报工会缺少应执行节点、分支和领料事实的权威依据。真实供应商接入按仓储领域实现，不先建设通用流程框架。

用户需确认：

1. LOCAL_ONLY正式领料的成功是否意味着MES投料确认/锁路，是否本轮同时承担库存扣减，还是库存能力另立任务；当前单头保存明确只是资料。
2. 完整路径是否可从任一入口到任一出口；关键节点必经规则、不同不连通组件是否允许分别作为路线分支。
3. 全部领料已确认作废、未开始且无报工事实时是否允许解除路径锁/重新选择；多单部分作废不能默认解锁。
4. CANCELLED目标量是否释放分配，是否允许重新激活；剩余/超额统计与硬限制如何定义。
5. ERP生产工单与MES需求的对应关系、是否同一ERP单据允许多个MES工单；真正ERP/库存批次身份有哪些真实消费者。
6. 当前验收库重复code组为0，不代表并发约束成立。WorkOrder.code是否必须并发唯一；如必须，是否批准必要DB约束，先复核存量冲突。
7. 既有未关联Execution的领料资料及已RUNNING/COMPLETED但没有路径的记录如何衔接正式主链；不得自动猜路径/生产事实。

## 已验证/未知边界与停止点

本次当前源码、正式Schema、真实SIMPLE_B metadata和只读聚合支持上述事实；指定库目前2个Execution（READY/RUNNING各1），其中1个RUNNING没有关联领料；已有16个空Execution领料、2个关联领料，包含历史/TEST ONLY数据，不能把这些数量当真实生产经营指标。20个旧Batch/48个旧路径仍存在，源码写入口退出。数量为审查时快照，后续验收标签增加会改变总数。

本轮已验证的是独立生产身份、引用/工艺冻结、目标分配查询、本地领料资料、默认本地作废和ERP查询/核实规则的测试闭环；真实用户服务此时仍500、新UI权限未配置，未验证V22端到端。没有真实供应商ERP联调、正式出库、执行路径、工序报工、真实完工或库存入库。

**当前生产主链只能跑通“需求 → 预建执行身份 → 本地领料资料/本地作废与核实”，并可管理订正执行状态；下一步最值得补齐的是正式领料与完整路径锁定及开工准入，而不是直接把管理COMPLETED当生产闭环。**

Phase B到此停止，等待架构与业务语义审查；未开发上述路径、报工、库存、厂商恢复或自动补偿。


### 收尾来源校正与只读边界

审查报告复核发现Phase A的V20曾从opaque binding推定历史provider；已回到Phase A追加V22纠正及回归测试，不实施任何本报告建议的生产改造。本库CONNECTED历史数为0，原外部ID不变，未核实provider拒绝操作。最终重新核对生产代码与只读快照，除上述新迁移/验收外，生产主链业务实现未因Phase B改变。


### 用户重启后的运行边界更新（2026-10-14）

现有用户服务的领料列表/详情已200，V22绑定核对一致；前述500不再是当前状态。ERP查询/核实目前缺独立权限而返回403，未登录返回401，真实UI按权限隐藏两个新按钮。成功路径仍待权限配置，生产主链审查结论及停止点不变。本补充仅更新运行证据，未修改生产业务代码；完整证据范围见Report A末尾重启复验章节。


### 用户授权后的验收状态（2026-10-14）

admin角色100增量配置两个ERP权限后，14次真实HTTP与浏览器查询/核实操作通过，SIMPLE_B本地模式闭环已完成，原权限关联保留。前述500和403等待状态不再是当前状态。CONNECTED仍无真实供应商，生产完整路径/正式领料/开工准入建议尚未实施，Phase B停止点保持。详情及证据见Report A最新章节。

## Production Chain Audit Report：独立端到端复核（2026-10-14）

### 1. 范围、证据与重要更正

本次从当前 Controller、Service、Mapper/XML、迁移、Vue 页面及测试源码重新追踪，没有沿用上次报告作为实现依据。后端分支 main；已有未提交后端、测试、文档及前端改动均保留。本次未修改业务代码、Flyway、测试、数据库数据或运行配置，未运行会写入数据的测试，未重启、提交或部署。仅追加本报告；用户审查期间另行明确要求新增 SQL 自动执行，按最新授权单独补入 AGENTS.md，未因此实施生产功能或生成 SQL。

结束时当前HEAD为 `c649d2d`；审查期间工作区由外部操作提交了既有变更（`d54e241`）及规则/报告入口说明（`c649d2d`）。本Agent没有执行git add/commit，未覆盖这些操作；末次复核新增提交内容没有引入本次未审查的生产实现。

本次真实数据库操作全部为 SELECT：核对中心绑定、information_schema、迁移记录、聚合及现有记录。目标为 `SIMPLE_B → datasource_id=8 → 192.168.18.25:3306/mes_base_dev_business_simple_b`，工厂/数据源均 ENABLED，business Flyway V1–V22 success=1。当前有 WorkOrder 37、Execution 2、Issue 19、Item 3、旧 Batch 20、旧路径节点 48、Inventory 1；主单缺失 Extra 数均为0。只读快照会随用户后续操作变化，不是生产交付验收。

**更正旧结论：** V14 第3–5行明确新增 `UNIQUE KEY uk_workorder_code(code)`；真实库 information_schema.STATISTICS 也返回 NON_UNIQUE=0。`WorkOrderService#saveOrder:350–355`捕获 DuplicateKeyException。因此撤回旧章节“WorkOrder编号无数据库唯一保护”的P1；单独先查后插仍不是并发保障，但本项目已有数据库最终保障。本轮未发现工单编码重复，无需为此再建索引。

历史证据仅作历史证据：读取 `target/erp-implementation/verify-final.log`，其中 ProductionChainHttpTest 4、ProductionExecutionHttpTest 3、MaterialIssueErpHttpTest 7、MaterialIssueVoidHttpTest 5、RoutingGraphValidatorTest 3均记录0失败/错误；这些主要是隔离 Fixture，不是供应商联调。本次没有重跑。`authorized-acceptance.json`记录上一轮14次真实HTTP，含会写核实元数据的POST；本轮只读取文件，没有重复执行。历史UI截图只证明当时查询/核实页面操作。不能由上述记录推出当前所有源文件、当前运行进程及整个生产链已重新通过验收。

### 2. 架构与对象关系

```text
WorkOrder（需求、产品、计划量、默认确切Routing版本）
  ├─ Workorder ERP Extra（外部标识；来源可能尚未核实）
  └─ 0..N ProductionExecution（唯一新生产执行身份、EXE-{id}）
       ├─ 固定WorkOrder/产品，绑定一个确切Routing版本
       └─ 0..N MaterialIssue → 0..N Item（物料、仓库、可空batch_no）
                         └─ ERP Extra（来源、外部单据、操作版本、核实事实）
Routing → RoutingProduct + RoutingNode(Operation快照) + RoutingEdge（DAG定义）
旧 ProductionBatch → BatchRouteNode（仅历史读取；有效写入口已拒绝）
WmInventory（物料+仓库汇总）——尚未与领料、执行、产出协调
```

这些逻辑关联在当前 Factory 路由的业务库内成立，业务表不再带 factory_id。执行编号、生产执行主键、物料库存批次字符串、ERP工单/单据身份有不同语义，不互相替代。

### 3. 实际调用路径与统一保护

以下路径均为当前源码事实，未在本轮发起写请求：

1. 工单页面 `views/production/workorder/index.vue:78–95` → `api/production/workorder.ts` → `WorkOrderController:105–120` → `WorkOrderService#create:160` / `release:211` → WorkorderMapper + WorkorderErpFacts。创建DRAFT；发布锁工单、检查revision及适用PUBLISHED版本，转RELEASED。没有ERP工单同步/发布调用。
2. Execution独立页/工单嵌入 ProductionExecutionList → `/api/production/mes-production-execution` → Controller:61/68 → ServiceImpl:101/124 → ProductionExecutionRules:61/70 → generated ExecutionMapper。创建READY空起止时间，事务中生成EXE-{id}；普通PUT负责管理订正。
3. 领料统一主子页 → `MasterDetailDocument#save:77–100`先保存单头 → IssueController → IssueServiceImpl#insert:113 → MaterialIssueRules#validate:91 → IssueMapper.insert → 仅LOCAL_ONLY调用 WarehouseIntegration.localCreate → LocalWarehouseErpService.createMaterialIssue → Extra.initialize。同一业务事务保存单头/Extra；此本地调用没有HTTP。明细之后通过 ItemController → ItemServiceImpl#insert:104 → Rules#validate(Item):142 → ItemMapper维护，另一个事务。
4. 作废按钮 → `/{id}/actions/void` → MaterialIssueVoidController:20 → VoidService#perform:75。准备短事务持久化UUID、VOID_PENDING和Extra版本；事务外cancel；落地短事务检查operation/version，按结果VOIDED、VOID_UNKNOWN或恢复原态。`reconcile-void`只查询同一次作废，不重复冲销。
5. 查询按钮 → GET `/{id}/erp-status` → MaterialIssueErpService#queryErpStatus:71：短只读快照→事务外领域查询→白名单QueryView，不写库。核实按钮 → POST `/{id}/actions/reconcile-erp` → #reconcileErpStatus:109：快照→外部查询→锁单头、校验Extra版本/作废操作/旧MES状态→领域规则→MES与Extra原子保存。
6. WarehouseIntegration按Factory配置选实现；本地模式→LocalWarehouseErpService；CONNECTED查询按历史provider/current provider、binding及唯一supports匹配选Adapter。生产源码只有本地实现，Test Vendor在src/test；不存在真实厂商Gateway或真实提交/审核编排。

统一Scope/权限：`FoundationAccessInterceptor:71–102`按URL、X-Factory-Id及请求快照解析当前厂并检查动作权限；`FactoryBusinessDataSource:31–53`取得并固定业务租约；`OwnedTransactionManager#doBegin/doCommit/doCleanupAfterCompletion`检查事务归属、固定租约并清理。BusinessCacheProcessor:48将Factory纳入缓存键，没有表内工厂过滤兜底。本次源码与同库关联检查未发现跨厂绕过；真实两厂隔离依赖历史Fixture/历史验收，不在本轮新增验证。

主要权限：WorkOrder使用 `workorder:list/query/create/update/release/delete`；Execution使用 `business:mes:production:execution:*`；Issue使用 `business:mes:material:issue:*`，Item使用对应`:item:*`；作废使用`:void`，ERP查询/核实分别`:erp:query`、`:erp:reconcile`。每个HTTP方法显式AUTHORIZED。ERP Extra没有普通HTTP编辑Controller，生成的内部Service/Mapper存在不等于对外开放。

### 4. 生产链路编排矩阵

标记范围是目标业务动作是否完整，而非“有类/有表”。IMPLEMENTED可仅限本地明确能力；PARTIAL有模型/部分规则但闭环不完整；STUB只有契约/预留；NOT_IMPLEMENTED没有对应能力。验收列的历史证据均未在本轮重跑。

| 业务动作 | 当前入口 | MES 编排 | ERP 调用 | 持久化 | 状态保护 | 实际验收 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 工单创建 | POST /api/production/workorder；工单页 | 产品/工艺/计划量校验，DRAFT，Extra同事务 | 无；ERP同步未实现 | workorder + workorder_erp_extra | 引用锁、code数据库唯一 | 当前Schema SELECT；历史Fixture | IMPLEMENTED（手工本地创建）；ERP同步NOT_IMPLEMENTED |
| 工单发布 | /workorder/{id}/actions/release；下达按钮 | DRAFT→RELEASED，适用发布版本 | 无 | workorder.revision/status | 父行锁+revision | 历史Fixture；本轮未下达 | IMPLEMENTED（MES发布） |
| Execution创建 | POST /api/production/mes-production-execution；独立/嵌入页 | 固定工单产品、EXE编号、READY | 无 | mes_production_execution | 本库引用、事务、稳定身份 | 当前2条记录；历史Fixture | IMPLEMENTED（执行身份） |
| 工艺版本绑定 | Execution POST/PUT，版本选择器 | 校验确切适用已发布版本；允许同工单不同版本 | 无 | execution.routing_id | 有领料单头/已开始后冻结；开始时要求启用发布版 | 当前两Execution绑定不同版本；历史Fixture | IMPLEMENTED（版本绑定） |
| 实际路径选择 | 无Execution入口；旧batch path返回410 | 无完整路径枚举/校验/持久化 | 无 | 新路径表不存在 | 无；DAG冻结不能替代路径锁 | 当前Schema、源码检索 | NOT_IMPLEMENTED |
| 领料创建 | Issue主子CRUD页 | 单头/明细分别保存、Execution引用校验 | 本地create；CONNECTED完整create仅契约 | Issue/Item/Extra；不扣库存 | 父单锁、状态/引用校验；无正式提交幂等意图 | 当前19单/3明细；历史HTTP | PARTIAL（资料维护已有；正式领料未闭环） |
| 领料确认出库 | 无 | 无实际出库确认、锁路径编排 | 无真实出库；本地SUCCESS不代表实物移动 | 无独立出库确认事实 | 无 | 未实现，不可验收 | NOT_IMPLEMENTED |
| 领料作废 | actions/void、reconcile-void；按钮 | 三段式、保留历史、重复不写外部 | 本地cancel；CONNECTED契约/选择器，真实冲销未接入 | MES作废意图+Extra结果 | 行锁、operation/version、开始后拒绝；存在并发窗口 | 当前14条VOIDED本地记录；历史Fixture/HTTP | IMPLEMENTED（本地）；CONNECTED实际冲销PARTIAL |
| ERP状态查询 | GET erp-status；查询按钮 | 服务端身份、只读结果 | local NOT_FOUND；CONNECTED Query契约 | 不写 | 模式/provider/binding匹配、独立权限 | 历史真实GET；当前Extra SELECT | IMPLEMENTED（查询协调）；厂商STUB |
| ERP核实同步 | POST reconcile-erp；核实按钮 | 白名单规则、状态冲突保留、MES/Extra同事务 | 只查不外部写 | Extra检查时间/版本；有限MES推进 | 重锁、version/operation/status防迟到 | 历史真实LOCAL_ONLY POST与Fixture；本轮不写 | IMPLEMENTED（协调）；厂商STUB |
| 报工 | 无 | 无累计/工序/产量规则 | 无 | 无ProductionReporting表 | 无 | 源码+Schema检索 | NOT_IMPLEMENTED |
| 完工 | 无正式动作；Execution PUT可填COMPLETED | 只有管理状态/时间校验，无产出判断 | 无 | status/end_time，无完工事实 | 无工序完成准入 | 当前代码、历史Fixture | PARTIAL（管理状态）；正式完工NOT_IMPLEMENTED |
| 生产入库 | 无；WmInventory CRUD不是入库 | 无Receipt聚合/库存流转 | 无生产入库领域方法 | 无ProductionReceipt | 无 | 源码+Schema检索 | NOT_IMPLEMENTED |

### 5. WorkOrder / Execution业务身份审查

- WorkOrder是需求：允许没有Batch/Execution创建，不自动生成执行单元；无ERP同步入口。DRAFT才能编辑/删除，RELEASED无回撤/关闭能力。发布要求产品存在及适用PUBLISHED工艺，计划量>0；没有ERP下达成功门禁。
- 产品更改由WorkOrderService:176–183保护：任意Execution（含CANCELLED）存在则拒绝；旧Batch也保护产品/默认工艺；有效或未知领料阻止产品变更。countBlockingIssues XML:25–29只有可信VOIDED且LOCAL_ONLY/NONE或CONNECTED/REVERSED才解除“领料本身”约束。全部作废也不解除仍存在Execution/旧Batch/RELEASED的约束。
- WorkOrder外部ID/code在V21后物理上已迁Extra，真实Schema主表没有external/erp字段。Entity保留未映射投影，Controller请求仍允许人工录入外部ID/code，Facts#save:43–49写Extra；这不是已验证ERP映射，也没有provider/外部同步保证。不能宣称外部身份已不可人工维护。
- allocation SQL:14–22 = SUM全部Execution.target_qty；remaining=planned−sum；excess=max(sum−planned,0)。CANCELLED计入，允许超额。工单页明确展示“含全部状态，不代表完成量”，这是当前统计语义，是否改为可分配余额须产品决策。真实工单18计划1000、合计1300，并非本轮制造的测试数据。
- Execution有独立编号、本地ID、工单/产品/确切版本；一工单多Execution，一Execution多领料，一单多物料/批次结构成立。允许在DRAFT工单及未领料时预建READY。batch_no只是可空文本，未验证库存批次存在、可用数量、单位换算或BOM。
- Execution创建不接受客户端产品身份；产品从工单固定，更新不允许换工单/产品/编号。有任意领料单头（含DRAFT/VOIDED）或开始事实后，不可换routing_id、不能回READY或清开始时间；删除有事实/领料关系的Execution被拒绝。
- target_qty、已填start_time的具体值、end_time、remark及部分状态仍可订正。没有严格前后态矩阵：无事实的CANCELLED可回READY；满足工单/版本/时间条件时CANCELLED可转RUNNING/COMPLETED；已有开始事实不能回READY，但可在RUNNING/COMPLETED/CANCELLED之间订正。取消是否可恢复尚未形成正式用例。
- **真实快照**：EXE-15 / WorkOrder18 / routing64是RUNNING，有start_time、0领料单；EXE-16 / routing65是READY，有2张RECORDED单但两单0明细。数据与源码一致，不能凭管理状态证明实际开工或领料完成。
- ProductionBatchMapper/BatchRouteNodeMapper只在WorkOrderService历史查询使用；旧创建请求batches由WorkOrderSaveRequest:22拒绝，旧selectPath:249返回LEGACY_BATCH_READ_ONLY。有效写链路没有恢复第二套Batch；Mapper保留CRUD方法不等于公开写用例。

### 6. Routing DAG与选定路径

RoutingServiceImpl#saveContent:216 / publish:224 / touch:391只修改草稿，revision条件更新；发布保存产品及Operation快照，已发布只能启停/新版本/复制，不直接改拓扑。RoutingGraphValidator限制100节点/400边，校验节点唯一、端点属于图、无自环/重复边、无环；发布多节点时拒绝孤立节点。它没有枚举root→sink完整路径，也不强制单起点/单终点或整个图弱连通，不能把定义合法性当作具体Execution路径合法性。

新Execution只有routing_id，无selectedPath、节点序列、path revision、lock time或出库锁定操作。旧batch_route_node保存的是旧身份历史，不能按相同ID或display_order借给Execution。当前没有“选路”与“锁路”的区别、首次出库锁路/补料复用或锁后修改规则；没有报工可核对路径节点。当前LOCAL_ONLY成功没有触发锁路，也没有错误地宣称锁路已完成；危险在于后续若直接拿create SUCCESS/EFFECTIVE当实物出库，会把不同事实混为一谈。

最小建议：在Execution下持久化一条有序完整路径；验证同确切版本、首节点合法起点、尾节点合法终点、相邻edge存在、无重复且序号连续。多入口/出口、isKey是否必经须明确；不建设AND/OR并行引擎。保存选择与锁定分开；首次正式出库意图引用不可变的路径修订，PENDING/UNKNOWN期间禁止重选；首次可信出库确认幂等锁定，多张后续领料只沿用并校验同一路径。作废一单不自动解锁路径，全部冲销后能否重选需明确政策。

### 7. MaterialIssue与ERP领域编排、人工恢复

**创建**：当前是管理员资料CRUD，不是完整领料命令。单头可直接RECORDED且0明细，Controller/Rules并未要求先齐明细。仓储MaterialIssueCreateCommand仅含issueId/operationId/issueNo/workorderId/executionId，没有物料、批次、数量、单位、仓库、选定路径或可信ERP工单身份。仅LOCAL_ONLY调用本地create，结果返回值未参与业务状态推进；Extra记录LOCAL_RECORD/SUCCEEDED，外部影响NONE。CONNECTED只存UNVERIFIED，不调用真实create。缺少正式创建意图、整单快照、外部幂等契约及创建失败/未知恢复入口。不存在已知真实ERP重复创建事故的证据。

**架构**：仓储领域接口组织合理，MES不依赖厂商create/submit/approve细节；未来Vendor Service可自主编排内部步骤，Gateway应只处理通讯。目前生产/MasterData领域消费者及入库方法未实现；不应为齐目录制造空层。缺失厂商不等于现有架构失败，但CONNECTED提交是STUB，不能交付实际领料。

**配置**：WarehouseIntegration#configuredMode:23在无厂配置时返回null；查询/作废缺配置明确失败，不回退本地。Issue CRUD在null时仍允许资料保存、Extra标UNKNOWN/UNVERIFIED；这属于现有行政入口，并非可执行集成“默认成功”。LOCAL_ONLY必须当前厂显式配置才能调用localCreate。CONNECTED查询严格检查provider，作废选择主要基于历史binding及supports；未来真实实现必须证明supports同时核验该厂配置/原来源，不能只凭任意binding字符串接受旧单。

**Extra/核实**：Issue Extra保存来源/外部身份/状态/operation_version/诊断，主表只保留MES作废意图。主单、明细增改删及核实/作废均推进Extra版本；核实检查快照版、操作ID与MES状态，旧结果不能覆盖新作废。UNKNOWN/NOT_FOUND不允许重建或直接标成功，LOCAL_ONLY始终NOT_FOUND/UNKNOWN且不造ERP身份。CONNECTED FOUND只有身份符合快照才采信；PENDING_APPROVAL不推进；EFFECTIVE仅推进ERP_PENDING/ERP_UNKNOWN到RECORDED；CANCELLED仅在既有作废意图下推进VOIDED。两个ERP创建等待态目前只被恢复规则识别，没有正式创建入口产出，不能把它们算作完整状态机。

**作废**：保留单头/明细和voidOperationId；已有开始事实的Execution拒绝新作废；有效/UNKNOWN领料继续保护产品。重复作废读取原结果，不重复外部写；UNKNOWN人工query同操作。明确REJECTED恢复原MES态，却保留voidOperationId，普通编辑被拒绝，重复cancel不会创建新尝试，缺明确人工新尝试/继续审核入口。真实已出库是否“冲销”不能仅靠单据cancel概念，Vendor领域必须提供可信逆向业务事实；当前没有真实出库/冲销实现。

**不允许伪造成功**：Issue普通CRUD仅DRAFT/RECORDED、不接受Extra/作废结果；不能PUT成VOIDED或修改外部效果。RECORDED本身可人工选，必须持续解释为本地资料状态。Execution普通编辑COMPLETED确实可人工维护，但不是ERP成功/生产产量证明，应在正式执行闭环中限制生命周期入口。

### 8. 报工、完工、入库及库存现状

生产主源码、前端/API、全部business迁移和真实Schema均未发现ProductionReporting、ProductionReceipt、ProductionCompletion、ExecutionRoute/SelectedPath或MaterialLot；结论为**未实现**。WmInventory已经实现CRUD、引用/数量校验和同步Port，表在V8创建、V15去Factory列，按material_id+warehouse_id唯一；同步缺真实QueryAdapter时失败。它没有批次粒度、流水、预占、领料扣减或生产入库行为；Issue/Production代码不调用InventoryMapper。

后续最小关系建议（未设计完整QC/WIP系统）：报工绑定execution_id + 选定路径节点 + reporter_user_id(sys_user)，独立业务事实/幂等身份累计合格、不良数量，不从target_qty推断实际产量；是否逐工序、顺序/返工和累计限额须产品确认。Execution用例依据选定终点及可验证报工事实判断完工；WorkOrder需求完成是汇总政策。生产入库单引用Execution/实际合格产出，仓储领域负责ERP入库内部步骤，Extra保存外部事实；MES完工与ERP入库成功保持不同事实，失败或UNKNOWN人工查询/核实，不能自动回滚已发生生产事实。

### 9. 事务、并发与异常时序 A–F

| 场景 | 当前已具备 | 未具备/后果及证据等级 |
| --- | --- | --- |
| A ERP创建成功、MES更新失败 | 作废有先存意图/后调用；核实可用服务端已有身份查询 | 正式create从未接入，不能证明能找回创建结果、幂等重试。现有create command无完整载荷；未来稳定operation必须先持久化，按operation查回已有外部身份，禁止重建未知结果。当前是缺口而非已复现事故 |
| B 创建成功、审核失败 | 结果模型可含已知externalReference；核实识别PENDING_APPROVAL且不推进 | 无Vendor步骤/阶段持久化/继续审核用例。仅模型支持不证明审核失败仍能恢复。后续应保留身份与阶段，人工继续同单，不从头create |
| C ERP超时 | 作废捕获异常为VOID_UNKNOWN，保留意图；query异常UNKNOWN；NOT_FOUND不等于失败 | 创建超时链未实现；查询FoundationException直接错误返回，不伪造成功；真实供应商未验证。现有Fixture包含timeout规则，未在本轮运行 |
| D 已出库、锁路径失败 | 无现成路径锁定动作，因此未发生当前代码可验证的锁路失败 | 缺少意图预先绑定路径、确认后原子落地，若未来接入外部会有不可回滚窗口。必须冻结预选版本/路径、pending拦截开工与重选，再可信确认落地，失败靠同操作核实 |
| E 多人并发修改 | WorkOrder/Routing revision；Issue父锁/Extra版；Execution行锁；父子引用锁；code DB唯一 | Execution及Issue普通编辑没有客户端expected revision，合法字段同态更新可能后写覆盖前写。Void准备先Issue→WorkOrder→Execution，而Execution更新Execution→WorkOrder，存在锁反转；作废事务外时开工不检查pending/unknown。静态风险，未做真实并发写验证 |
| F 旧结果覆盖完成/作废 | ERP核实重锁并比较Extra版/operation/status，VOIDED不因EFFECTIVE复活；request.ts上下文检查和ErpAction generation拒迟到回填 | 这些保护只覆盖当前核实/上下文，不代表未来报工/完工链；同一单据普通编辑没有客户端revision。历史Fixture lateEffectiveQueryCannotOverwriteNewVoidOperation支持核实保护，非本轮重验 |

特别的可行窗口：Void准备事务检查Execution未开始后释放锁→另一请求将Execution更新RUNNING（只要求工单RELEASED、有效版本和start_time）→外部作废执行/结果落地。落地只检查Issue操作/Extra版，不重读Execution。单纯在落地拒绝不能撤销已发出的外部冲销；必须在开始入口和准备阶段共同阻止竞争。

### 10. P0 / P1 / P2 问题清单

本轮**没有确认的P0**：没有真实CONNECTED提交、库存扣减或Execution路径执行，未发现已发生跨厂污染/重复ERP单据。下面P1是明确闭环缺失或现有实现的可定位风险；未规划完整的模块不机械评为P0。File:Line以本次源码为准，后续改动需重核。

| Issue ID / Severity / Module | File:Line | Current Behavior / Trigger Condition | Business Impact | Recommended Minimal Fix | Schema Change Required? | Affected Existing Data? |
| --- | --- | --- | --- | --- | --- | --- |
| PC-01 / P1 / MaterialIssue闭环 | MesMaterialIssueServiceImpl.java:113–138；MaterialIssueCreateCommand.java:6 | 保存单头即可RECORDED；明细另存；CONNECTED正式提交缺失；将保存当领料时触发 | 无法证明整单已齐、真实出库及外部单据；后续直接接create会缺幂等/恢复 | 保留资料CRUD，另设submit用例校验完整快照、固定Execution/路径、先存操作、事务外领域调用、短事务核实落地 | 是或扩充既有意图；Extra复用 | 有；历史RECORDED不能自动升级出库成功 |
| PC-02 / P1 / Execution路径闭环 | V16__production_execution.sql:2；WorkOrderService.java:249；RoutingGraphValidator.java:24 | 只有版本/DAG，无完整路径选择/持久化/锁定；分支工艺开始生产 | 无法追踪实际路线、约束报工节点或证明出库锁路 | Execution单条完整路径、选择/锁定分离，首次确认幂等锁，后续领料沿用 | 是 | 有；2个Execution须明确补选，不能从旧Batch/展示序猜 |
| PC-03 / P1 / Execution生命周期 | ProductionExecutionRules.java:70–79、109、124–133；前端fields.ts:59–84 | 普通PUT可进入RUNNING/COMPLETED，没领料/路径/报工准入；取消可复用 | 当前“已开始/已结束”不保证生产事实；库中EXE-15零领料RUNNING | 明确start/complete命令、准入规则；普通编辑限制 lifecycle；单独授权订正必要事实 | 开工限制本身可无；正式路径/报工需新增 | 有；不能把既有COMPLETED自动视为真实完工或批量回退 |
| PC-04 / P1 / 作废与开工竞争（已有行为缺陷） | MaterialIssueVoidService.java:88、109、119–134；ProductionExecutionRules.java:109 | 准备释放锁后，开始入口不检查VOID_PENDING/UNKNOWN；外部期间开始 | 可形成已开始执行仍被作废供料；真实ERP接入后影响已用料安全 | 两动作共享准入/统一锁序；pending/unknown阻止start，准备作废锁Execution并检查状态 | 门禁无 | 需审查关联已开始/pending；当前无pending数据，不自动再冲销 |
| PC-05 / P1 / 锁序反转（已有行为风险） | MaterialIssueVoidService.java:139–148、88；MesProductionExecutionServiceImpl.java:125；ProductionExecutionRules.java:100–104 | Void取得WorkOrder再等Execution；Execution更新反向等待WorkOrder | 数据库可能死锁中止合法动作，pending恢复体验不稳定；未复现真实写竞争 | 制定当前用例一致锁序，复读引用及状态，新增隔离并发测试 | 否 | 否，不重写历史 |
| PC-06 / P1 / 人工恢复 | MaterialIssueVoidService.java:82–83、130–134；MaterialIssueRules.java:94 | REJECTED保留voidOperationId；重复返回旧结果，编辑被冻结；真实拒绝后再处理 | 操作可追踪但无授权新尝试/继续处理，项目级介入才能恢复 | 接真实Vendor时补明确人工新尝试/继续同外部单入口，保留旧操作历史；不无限重试 | 视操作历史最小模型决定 | 有；保留原拒绝及外部身份，不能清空冒充未发生 |
| PC-07 / P2 / 管理编辑并发 | MesProductionExecutionServiceImpl.java:124–143；MesMaterialIssueServiceImpl.java:146–173；MesMaterialIssueItemServiceImpl.java:126–145 | 行锁序列化但无客户端expected revision；两客户端从旧值编辑同字段 | 后写覆盖合法订正，非跨厂/外部重复写事故 | 视订正并发需求补expected revision；沿用Extra版本也须提供正确前端契约 | Execution需字段；Issue可复用现有版本但需契约审查 | 可增量，不覆盖历史数据 |
| PC-08 / P2 / 语义与metadata | V13__workorder_batch_route.sql:7；WorkOrderSaveRequest.java:22；WorkorderErpFacts.java:43 | planned_qty注释仍写批次合计；人工外部标识可写Extra但未验证 | 文档/生成字段可能误导，将外部ID或管理状态当已核实事实 | 后续正式迁移修正注释；响应明确外部来源未核实，人工标识不能充当ERP成功 | 注释治理需正式迁移；投影改造可无 | 不推断/删除历史外部标识 |

外部FACT EFFECTIVE/CANCELLED当前没有独立“实物已出库/已冲销库存”结果语义；这列为PC-01未来接入的契约要求，不指控当前不存在的厂商错误。取消占用量、恢复取消、超额分配、多出口完整路径属于产品决策，不单独标缺陷。

### 11. 最小分阶段路线（建议，未实施）

| 阶段 | 为什么先做 / 最小交付范围 | 前置依赖 / 暂缓 | Codegen或业务编排 | 真实数据验收建议 |
| --- | --- | --- | --- | --- |
| 下一轮：领料确认与路径锁定闭环 | 一次完成“完整明细+Execution单路径选择→正式提交意图→可信实际出库确认→路径幂等锁定→start门禁”；同时修PC-04/05。保留单头/明细分开编辑，正式submit不等于整单编辑原子保存 | 明确本地实际出库事实来源/仓储接口成功含义、路径起止政策；暂缓报工、完工、入库、AND/OR引擎、Saga | 新路径/必要操作事实经正式建模/迁移/真实metadata Codegen persistence+backend；协调Service手写复用generated Mapper，收紧绕过入口 | 在SIMPLE_B复制为明确测试标签的最小工单/Execution/分支DAG/多批次明细，覆盖空明细拒绝、无路径拒绝、重复submit/确认、多单不重复锁路、UNKNOWN不重发、pending禁止start、并发作废；现有EXE-15/16只用于只读基线，不冒充已确认出库 |
| 第二阶段：选定路径报工 | 先有稳定生产身份/路线/供料事实，才累计每节点合格不良产出和sys_user记录；不建立全面QC/WIP | 前阶段及累计/顺序/返工政策；暂缓ERP入库、完整库存批次系统 | 新报工表基础Codegen，Reporting业务规则/事务手写 | 同一测试Execution多次/重复/越路径节点/无权限/不同厂/超量报工，保留实际明细及累计核对 |
| 第三阶段：完工事实 | 依据明确终点/产量规则形成Execution完工，明确WorkOrder汇总；不能PUT状态代替 | 已验证报工；需良品/不良/计划量与结束条件决定；暂缓复杂排产/QC | 新事实表如需要用Codegen，完成判断与聚合业务编排 | 节点未完成拒绝、分次完工、数量汇总、幂等/订正/多Execution工单状态 |
| 第四阶段：生产入库与ERP恢复 | 根据真实合格产出构造Receipt，仓储领域编排厂商内部步骤，独立Extra与人工核实 | 完工/可入库数量政策、真实Vendor契约及验收环境；暂缓全库存平台重写 | 主子表后端Codegen，统一主子UI，Receipt提交/ERP协调手写 | SIMPLE_B内带来源标签的完工样例；真实Vendor测试厂验证创建后审核失败/超时/本地落地失败/人工核实，CONNECTED未接入时必须明确未验收 |

首次闭环不必先迁工单ERP同步。优先使本地生产事实正确；真实Vendor接入可以并行于该闭环的契约准备，但不能在缺幂等意图/路径快照时先把HTTP塞进单头CRUD。LOCAL_ONLY只代表无外部写；若采用人工仓库确认，必须保存有权限操作者、时间、数量及确认来源，不能借本地create SUCCESS伪造实际出库。

### 12. 需要用户决策的设计空白

1. 实际出库确认以ERP可信库存业务结果为准，还是允许LOCAL_ONLY下仓库人工确认？采购/领料创建或审核何时真正改变库存，应由Vendor契约证明。
2. DAG是否允许多个起点/终点、单Execution路径是否必须经过isKey，何为完整路径；保持单路径，不扩展并行引擎。
3. 是否所有目标生产领料必须关联Execution；保留空Execution的历史/行政资料，但正式生产submit应有明确要求。是否允许工单DRAFT领料、补料及多张单到达的开工条件也需明确。
4. CANCELLED是否释放计划分配、是否可恢复；是否允许超额目标；当前统计“全部状态目标量合计”可继续保留并另提供可分配量，不擅自改旧含义。
5. 全部领料冲销且未开工时是否可重选路径；已开始后补料、退料应走何种独立恢复规则，不能直接解锁历史。
6. 后续报工采用逐工序还是末工序、累计/返工政策及良品/不良完成条件；完工与入库是否可分批，何时关闭WorkOrder。

上述是后续实施的必要决策清单，本次审查不等待回答、不提前开发。

### 最终独立回答 A–D

**A. 当前真实跑到哪一步？** 可维护/发布工艺DAG、手工创建/发布工单、创建独立Execution并绑定版本、保存领料单头与明细；本地作废、ERP只读查询及有限核实协调已存在。生产主链停在“领料资料保存”，没有实际出库确认、完整执行路径及锁路、报工、真实完工或入库闭环。可以把Execution管理状态改RUNNING/COMPLETED，不能据此声称生产链跑通。

**B. 最危险的三个缺口？** ①正式领料没有完整载荷/稳定提交意图/实际出库事实；②Execution没有选定完整路径且普通编辑能越过领料/路径门禁；③作废与开工有事务外竞争窗口及相反锁序。均为P1级闭环缺失或风险，真实CONNECTED后果尚未验证。

**C. 下一轮只实现哪个最小闭环？** “完整领料提交＋可信出库确认＋Execution单路径锁定＋开工门禁”，共同持久化意图、统一并发规则与人工核实；不同时建设报工/完工/入库。LOCAL_ONLY也必须有明确实际确认事实来源。

**D. 是否须推翻设计？** 没有证据要求推翻。保留WorkOrder需求、Execution唯一执行身份、Routing版本DAG、MaterialIssue主子资料及ERP Extra职责，增量补执行路径和正式命令/事实即可；不恢复ProductionBatch写模型，不重写生产模块。

本次已停止于审查与用户明确要求的规则补充，没有开始上述实现。

## 2026-10-15 当前生产链路更新（产品匹配工艺优化后）

本节覆盖此前“未实现选路/正式领料/报工”的历史结论。依据本轮重读当前 Controller/Service、现有实施报告及本轮只读 API/UI；本轮仅修改工单前端联动并新增回归测试，没有重新执行整条生产链的写入验收。

| 环节 | 当前实现与入口 | 当前边界 |
| --- | --- | --- |
| 工序能力 | 工序编辑中配置 sys_user 能力；启用账号、有效角色及当前厂业务授权决定候选资格 | 不是独立 Personnel 主数据，也不是自动排产 |
| 工艺 | 产品适用关系、确切版本、节点/边 DAG、发布快照 | 工单候选为所选 PRODUCT 的已发布适用版本；暂未放开其他物料类型 |
| 工单 | 创建/编辑 DRAFT、下达 RELEASED、默认 ID DESC、默认路线图及执行摘要 | 表示需求；不承担派工、开工或完整报工管理；下达不是 ERP 下达 |
| 生产执行 | 独立 `/production/mesProductionExecution`；EXE 编号、目标数量、确切 routing 版本、人员派工 | 同工单可有多个 Execution 和不同适用版本；尚非自动排程/产能优化 |
| 实际路径 | `ExecutionPathService.select/view` 校验真实边和确切版本；UNSELECTED / SELECTED / LOCKED | 完整定义 DAG 与实际已选路径分别展示；不复用旧 BatchRouteNode |
| 正式领料 | 主子单据独立保存；`MaterialIssueSubmissionService.submit/apply` 保存提交身份，成功确认并锁定所选路径；UNKNOWN 可核实 | LOCAL_ONLY 可完成本地确认；当前没有真实供应商 ERP 生产闭环，领料确认不等于库存扣减 |
| 开工 | `ExecutionPathService.start` 要求已下达工单、LOCKED 路径、成功确认领料且无悬而未决的领料/作废 | 正式动作写 RUNNING/开始时间，不能把管理状态修改当作完整开工流程 |
| 生产数量 | 独立 `/production/productionReport`；按工单/执行查询目标、路径节点数量、上游输入、累计及剩余，流水详情/历史 | 要求实际路径；仅锁定路径允许数量写入 |
| 管理员报工维护 | `ProductionReportService` 历史补录、正负订正、整笔冲销；追加事实，保留原流水 | 上下游累计约束仍生效；不是正式扫码/现场报工入口 |
| 人员准入衔接 | 工序能力、执行派工与 `ReportOperatorEligibilityService.requireEligible` 已有规则 | 当前生产源码没有调用 requireEligible 的正式报工入口；管理员补录只按历史操作人员存在性等规则维护，不能宣称现场人员报工闭环已接通 |
| 完工/质量/入库 | 有执行管理状态；质量预留字段及未接入提示 | 报工不自动判定完工；未实现正式质检、生产入库，以及上一阶段产出自动形成下一阶段投入的库存闭环 |

当前可表述为：**工艺准备 → 工单需求/下达 → 独立执行与派工/选路 → 正式领料确认锁路 → 开工 → 管理员数量流水维护**。正式现场报工、质量、自动完工和生产入库仍待后续独立需求，不能称为从投料到入库的全生产闭环。

本轮验证：后端全量 verify、前端267项测试及构建通过；实际工单选择器 API/UI验证0/1/9个匹配工艺及唯一自动选中。其他链路的真实写入证据沿用对应实施报告的历史验收，本轮仅重读代码，没有重跑或扩大生产业务。

### 2026-10-16 正式生产报工 V2 已接入

原“正式现场报工尚无入口”的结论由本轮实现替代：新增独立 `/production/productionReporting`，通过既有身份、锁定路径、人员资格和数量服务追加不可变 REPORT，支持请求幂等及首次正式开工联动。指定 SIMPLE_B 的真实API、两人并发、浏览器及数据库验收已完成，仅写新增 TEST 工单91/Execution101。质量扣减、自动完工和生产入库仍不在本轮范围；实体扫码枪/真实断网未验收。完整契约、A–J报告及证据见 [正式报工V2实施报告](2026-10-16-production-report-v2-implementation-report.md)。


---

## 2026-10-16 专项：多次领料、统一扫码身份与生产数量模型（只读审查）

**结论：方案方向 CONDITIONAL GO；当前代码直接按新规则使用 NO-GO。** 当前已有 Execution、实际路径、不可变报工/质量及公共锁，后续工序和末道计算基本可复用；首节点投产额度、旧码生产语义、永久唯一绑定及首次投产命令尚未形成闭环。不能只把 targetQty 改成现有材料明细 SUM，或仅放开 READY 限制。

本节是审查与候选方案，不是已批准 ADR、迁移或实施结果。按用户“首轮只读”要求，未修改业务代码/配置/测试，未运行 Codegen/Flyway，未生成 SQL/Schema 文件，未写数据库、调用写 API、运行项目测试/构建或启停服务。**本轮唯一仓库写入是向本文追加审查结果及位置矩阵。** 仅执行源码检索、Git/文件只读检查、指定库 SELECT/metadata/SHOW，以及内存中的 Decimal/旧解析器推演；案例推演不等于已通过业务集成测试。

### 0. 范围、版本与现状证据

| 仓库 | 分支 / HEAD | 开始状态 |
|---|---|---|
| Base 后端 | main / 4679154eccb61444f1c0f795c9620a54ec6e207d | 有既存 ReportV2、Receipt、Quality、领料和文档等 dirty/untracked |
| Base 前端 | main / 02a6554baff657f6c8dfe42cebdcbc9e5f863473 | 有既存报工、质量、样式及 Receipt dirty/untracked |
| Source 后端 | master / 7fd7bec2a8eea09261ade1e822127932ff3207d9 | clean |
| Source Web | master / 42481d53867fcb77cb53bd807433b084fd73bfea | clean |
| Source UniApp | feat/multi-factory-isolation / f109f7937fff51d4395d574890b57f3280d10d5f | config.js 既存修改，未读取其配置内容 |

Source 当前 HEAD 已不同于旧专项报告快照，因此本轮重新核对扫码和领料实现，不把旧报告当成当前源码。未审查已退出依据的 ktg-wheel。已检索 docs/（含领料、执行、报工、质量、Receipt 报告），doc/、knowledge/ 无对应入口；本主题属于已有生产主链审查，故追加本文，不建立同主题平行报告。并行工作以当前共享工作区 dirty 源码为准，未启动子 Agent，也未把其他任务的测试回执算作本轮测试。

真实只读核对：中心 mf_factory JOIN mf_datasource 确认为 SIMPLE_B → 数据源8 → 192.168.18.25:3306/mes_base_dev_business_simple_b，Factory/数据源均 ENABLED；业务 Flyway 最新28、中心31，均 success=1。information_schema 已确认下述实际列结构；当前库未发现 BOM、input 或 binding 名称的表，结合全业务源码搜索未发现正式 BOM/投产额度/扫码绑定实现。名称检索只能作为辅证，否定结论同时来自实际字段和调用链。

### A. 当前实际调用链和表结构

#### A1. MaterialIssue 链路

`领料出库 MasterDetailDocument → header/item API → MesMaterialIssueController / MesMaterialIssueItemController → 对应 ServiceImpl → MaterialIssueRules → 生成 Mapper/XML → mes_material_issue / mes_material_issue_item`。

正式动作：`MaterialIssueSubmitAction → POST /api/issue/mes-material-issue/{id}/actions/submit → MaterialIssueSubmissionController → MaterialIssueSubmissionService`。外部核实走 `MaterialIssueErpController → MaterialIssueErpService → reconcileSubmission`；作废走 `MaterialIssueVoidController → MaterialIssueVoidService`。

源码位置：[领料创建](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueServiceImpl.java:111)、[领料规则](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueRules.java:96)、[领料明细更新](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueItemServiceImpl.java:124)、[正式领料 prepare/apply](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueSubmissionService.java:123)、[领料作废两阶段](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueVoidService.java:78)。

| 当前表 | 关键真实列 / 事实角色 | 新需求缺口 |
|---|---|---|
| mes_material_issue | issue_no、execution_id、workorder_id、product_id、flow_card_no、routing_id、issue_time、status、submission_id、void_* | **没有本次计划产出数量、投产/纯补料类型或生产批次绑定字段**；flow_card_no 只是普通流程卡字符串 |
| mes_material_issue_item | material_id/material_code、batch_no、quantity DECIMAL(20,6)、unit_id、warehouse_id | quantity 是材料领用量；batch_no 是来源材料批次，不能相加得到产品产出或当生产批次 |
| mes_material_issue_submission | id、issue_id、path_revision、command_json、status、created_at、finished_at | 保存应用层冻结的请求载荷和提交状态；status/finished_at 会更新，不是投产额度流水。当前命令 JSON 只有材料明细量，没有产品投产额度/BOM版本 |
| mes_material_issue_erp_extra | source_execution_mode、external_effect、provider/binding、外部身份、document_status、last_outcome、operation_version及作废结果 | 外部业务证明及迟到结果防护，不提供产品投产量 |
| mes_production_execution | id、execution_no、workorder_id、product_id、routing_id、target_qty、生命周期字段 | target_qty 注释是“计划执行数量，不代表最终产出数量”；无产出累计列或生产批次别名 |
| mes_execution_path / _node | Execution主键、确切routing_id、状态/修订号，sequence_no/routing_node_id | 已有完整实际选定路径，第一次可信领料确认永久锁定 |
| mes_production_report | REPORT/ADJUSTMENT、quantity_delta、原始引用、request_id、可信人员/时间 | 不可变加工完成总量事实；不是只录合格量 |
| mes_production_defect | declaration_id/revision_no、CREATE/REVISE/VOID、affected_node_id、defect_qty | 最新非VOID版本的不良贡献；不能把所有历史版累计 |
| mes_production_receipt | RECEIPT/REVERSAL、quantity_delta、原始引用、仓库/批次/人员/时间 | 消耗末道有效产出，独立保留历史差额，不是库存入账 |

Schema 演进应连读业务 V11/V12/V15/V16/V18/V19/V20/V23/V25/V26/V27/V28，而不是只看 V11 中已移除的 factory_id 和旧精度；实际库已无业务 factory_id。

#### A2. 提交、确认、失败和多次领料的真实边界

| 状态/模式 | 当前语义 | 将来作为额度依据的判定 |
|---|---|---|
| DRAFT / RECORDED | 可编辑资料，未执行正式提交 | 0；不能因有主单/明细就授予额度 |
| SUBMITTING | prepare 已持久化稳定提交尝试，等待结果 | 0 新增额度；保留身份及原操作 |
| SUBMIT_UNKNOWN / UNKNOWN | 外部异常、结果不完整或业务状态不确定 | 0 新增额度；只核实原操作，不自动重建Execution/单据 |
| SUBMIT_REJECTED / REJECTED | 可信明确拒绝；不是任意超时/HTTP失败 | 0；仅满足无外部身份等条件时可 reopen，保留旧尝试 |
| LOCAL_ONLY + CONFIRMED | LocalWarehouseErpService 返回本地域成功，路径锁定；不调用ERP | 按明确本地确认规则记一次投产额度；**不证明ERP或MES库存已扣，也不自动证明实物已流转** |
| CONNECTED + CONFIRMED | SUCCEEDED业务结果、可信外部身份、有效单据，Extra记录CONFIRMED/EFFECTIVE | 仅这些业务条件匹配且对应投产类型时记一次额度；HTTP200不等于业务成功 |
| VOID_PENDING / VOID_UNKNOWN | 原确认事实的撤销结果尚未确定 | 不得仅因主单已不叫CONFIRMED就提前扣减既有额度；必须保护待减额度不被继续消耗 |
| VOIDED +可信SUCCEEDED | 作废已核实；CONNECTED需REVERSED、LOCAL_ONLY为NONE | 在同一最终本地事务追加负额度，保留原始引用，不能擦除原额度事实 |
| 作废REJECTED | 恢复 void_from_status，外部单据仍有效 | 不扣额度；解除相应待减保护 |

`prepare` 使用已有单据ID幂等：submission_id 已存在即返回原单，不重发；同一单据并发受共同锁保护。不同单据各有自己的 attempt 和 `ISSUE-SUBMIT-{attempt.id}`，**不是同一扫码或同一新建请求的全局去重**。当前没有新增投产额度，因此不能声称已经防住“额度重复增加”。

同一 Execution 可关联多张领料头，schema 没有 execution_id UNIQUE；READY、未开始、同工单/产品/版本时，各单可分别正式确认，已LOCKED路径的 confirm 为同修订幂等返回。**RUNNING 后新正式提交被 prepare 拒绝**，前端 [领料执行选择器](/Users/user/work/lixin/mes-base-ui/src/components/business/production/ProductionExecutionSelect.vue:16) 也只查 READY，因此只有开工前多单支持，未完整支持车间持续投产。首次领料 CRUD 必须引用已存在 Execution，既不会建 Execution，也无扫码绑定；有意不在扫码读取 GET 中创建生产事实。

已提交/已确认主单和明细的普通编辑、删除受到 submission_id/void_operation_id 等保护；不是普通 UPDATE 覆盖已确认数量。服务仍会 UPDATE 主单状态和Extra，并为明确拒绝 reopen 清空当前 submission_id；其用途是工作状态投影，不是新增产出额度或不可变额度订正。当前无独立额度订正命令。

### B. targetQty 全部读写位置与语义矩阵

| 类别 | 核心位置 | 当前用途 / 后续影响 |
|---|---|---|
| DB与模型 | V16 target_qty；MesProductionExecutionEntity；生成Example/Mapper/XML | 非空DECIMAL(20,6)，Entity读写、条件查询及生成INSERT/UPDATE；不删除列 |
| 创建 | [执行创建/修改](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/impl/MesProductionExecutionServiceImpl.java:99) insert；CreateCommand；Controller POST | 用户输入计划量，经规则校验后持久化，不来自领料/BOM/ERP |
| 修改 | 同Service updateById；UpdateCommand；Controller PUT /api/production/mes-production-execution/{id} | 管理后台正式入口能修改目标数量；取得WorkOrder→Execution锁后更新 |
| 数值/状态校验 | [执行修改规则](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionExecutionRules.java:90) updating/validateValues | target>0、14整数/6小数；有报工时拒绝低于任一节点净报工量的target。**没有“RUNNING一律不许扩量”规则**；保持原状态/时间等合法值即可继续评审target修改。当前测试以READY载荷更新RUNNING被拒绝不能证明target不可扩量，它触发的是状态变更冻结 |
| 首节点权威 | [统一数量计算](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:137) `var input=view.targetQty()`；load及三条计算/校验入口 | **计划量被同时当作首工序当前输入上限**，为本轮最关键语义耦合；报工/质量全链校验间接依赖 |
| 身份展示 | [扫码身份解析](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionIdentityService.java:34)；ProductionIdentity / IdentityVo / IdentityController | “计划执行数量”，不等于实产；新扫码绑定不应改变其含义 |
| 数量HTTP | ProductionReportModels.ExecutionQuantity/ExecutionSummary；ProductionReportViews；ProductionReportController | 返回计划target与节点数量；新增实际投入字段应独立，不能把input偷偷塞回target字段 |
| 正式报工 | ProductionReportingController.Context与页面identity.targetQty | 目标显示；真正报工上限来自Quantity节点availableQty，不在页面算计划减累计 |
| 交接 | ProductionReceiptController.Summary.targetQty和页面“执行目标” | 只展示计划；交接O取末节点effectiveOutputQty，**不把target当末道产出** |
| 工单计划摘要 | [工单计划分配查询](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkOrderExtraMapper.xml:15) | SUM所有关联Execution.target_qty形成allocated/remaining/excess；没有排除CANCELLED。是计划分配展示，不是累计投产或实产，也未构成新增领料的硬上限 |
| 数量工单摘要 | ProductionQuantityMapper.workorderQuantities | targetQty为计划，lastNodeReportedQty为末道**净报工总量（含不良）**；该字段未扣质量，不得改名冒充有效产出 |
| 前端编辑/检索 | mesProductionExecution/fields.ts targetQty可编辑；ProductionExecutionList→CrudPage；API QueryDto | “计划执行数量”是用户编辑字段；列表查询支持target等值，变更语义时保留字段兼容并审查编辑规则 |
| 前端只读消费者 | ExecutionIdentityLookup、WorkOrderExecutions、ProductionReportManager、productionReport页、productionReporting页、productionReceipt页 | 都应继续标成计划；新展示分别命名“有效投产输入”“末道有效产出”，避免出现三种数量的同名“目标/产出” |
| 测试/审计SQL | ProductionQualityQuantityTest、ProductionReport/Core/Chain/Execution/Receipt及MySQL夹具、WorkOrderRequestBudget、前端测试 | 旧测试大量直接给target制造首节点输入；历史审计SQL也拿target做报工上限。迁移时必须补真实有效投产额度夹具并更新审计依据，不通过改target假装测试新来源 |

完整检索索引及逐文件行号见本节附录，含生成SQL、VO、测试和构造器的55个文件、109处位置；额外检查了字段经CrudPage和Service记录构造器传递的调用链。不存在 Report/Quality/Receipt 为维护实际累计产出而反复 setTargetQty 的生产调用：实际 setter 写入位于执行创建/编辑 Service，而非这些事实模块。

结论：保留target_qty及其计划/历史参考价值，**停止用它决定实际投产额度**。当前字段可编辑，不能宣称它已是不可变历史快照；若要求“初始计划快照不可修改”，需下一轮明确并实现，而不是本轮靠改名获得历史可信性。不得为了新输入模型删除既有字段或把旧target自动回填成已确认投入。

### C. MaterialIssue 可否成为首节点输入权威

**可以由领料业务域负责，但不能直接汇总当前表。** 已有确认/作废/尝试身份和锁可复用，缺少的是独立的产品投产额度事实、投产/补料区分、BOM计算依据及额度调整规则。

建议最小业务记录：每次投产申请明确“本次计划产出数量”、投产类型、BOM/用料依据和计算后材料行快照；纯补料的产品额度为0。材料kg/根/件与产出件的换算必须有权威BOM/用料规则、计量单位、损耗及舍入规范。**Base未接入该能力，Source的ERP工单耗用比不能未经建模直接当Base BOM。**

候选持久化比较（仅逻辑方案，未生成/执行Schema）：

| 方案 | 能否满足 | 审查建议 |
|---|---|---|
| 直接SUM现有issue_item.quantity | 不行，单位/材料不同，无产出额度与补料类型 | NO-GO |
| 扩展领料头产出额度/类型，确认后冻结，汇总有效主单；同时留确认/作废/订正审计 | 理论可行，必须处理全部中间状态及部分额度订正，不仅加一个数值字段 | 只有明确限制为固定确认额度+受控整单撤销、所有修订可追溯时才可作为更小替代方案 |
| 领料域新增一张不可变投产额度流水 | 适合分次投产、独立补料、部分撤回/订正和幂等追溯 | **本需求推荐**；记录本模块额度变化，不重复保存Execution累计或报工产出 |

推荐流水的最小语义：主键为稳定确认/订正操作身份，execution_id、issue_id、source_submission_id或明确源事件、quantity_delta、原始额度引用、可信人员/时间、原因；只在可信确认事务追加正额度，可信撤销/订正追加负额度。每个确认源事件最多一条额度，可通过确定性的事件主键在数据库原子保证；手动订正使用自身请求UUID。不能仅用先查后插防止重复，也不能把所有成功submission重试当作新投产。正向额度引用的计划产品数量及BOM依据在准备时冻结，不能确认后再从可变BOM重算。

一个新建投产命令的请求幂等与“同批仅一个Execution”是两件事：两个用户的不同请求可以合法分别追加100/200，但相同请求重放不能新建两张单。现有按issueId提交幂等不足以解决新建阶段；下一轮需在现有单据/意图记录落实数据库级请求唯一身份。若选择新增非主键UNIQUE，须另获明确授权，本轮不添加约束。

### D. 统一扫码绑定：必要性、最小候选与旧码边界

#### D1. Source原始格式及实际语义

- [旧调拨标签编码](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/print/service/impl/TransferDocServiceImpl.java:45)：按已有物料条码规则输出 `itemCode#lotCode` 或 `itemCode#date#serial`，后者末尾serial长度2/3。LotCodeUtils另有旧格式和前缀处理；不同页面不是一个严格统一解析契约。
- [旧领料扫码解析](/Users/user/work/heri/heri-uni-app/pkg-issue/pages/issueReceipt/index.vue:344)：trim、去除一次字面`\000026`后split('#')，`code[0]`当品号，其他**值不等于品号**的片段join('')作批次。前导零作为字符串保留；没有完整分隔符/长度/大小写规范，也未证明实际扫码枪前缀字节都能正确处理。
- 旧领料表单明确标“材料品号”“批号”“流程批号”；`IssueReceiptController.getWorkorder`先调用RouteBrokenStepService.getAfterItemAndLot，可能转换断阶前后材料，再按ERP材料匹配工单。[旧领料服务](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/service/impl/IssueReceiptServiceImpl.java:1151) 中qty注释也是物料投入量，而非本次产出计划量。
- 报工 `pkg-feedback/pages/feedback/index.vue:409` 使用相似解析，页面数量标“合格数量/合格转序数量”；质量 defective 页面及入库申请 moReceiptRequistion也复用品号/批次定位；但当前新的 inspection/check、inspection/submit 页面扫描的是独立检验单码，支持URL中的inspectionCode参数，并非该品号/批次生产码。**旧报工合格量与Base含不良总加工量口径不同**，不能迁历史数后再扣一次Quality。
- 旧入库查询 `InBoundRequestsController.getTicketInformation` 明确把itemCode称为材料品号，沿ProBuckle追溯，再关联产出产品；不存在“所有旧码第一段必为当前产出品号”的证据。不能用某条扫码流程证明所有生产/检验入口已使用同一身份。

内存复现旧解析：`A#12#34`与`A#1#234`都得到(A,1234)；`A#A#001`与`A#001`都得到(A,001)。这是当前宽松解析可接受输入的碰撞示例，不声称每个示例都是现场有效标签；恰好说明必须按确认的真实格式校验，不能继续任意filter/join。`001#0007`仍为字符串(001,0007)，不能Number化。

实际库 material.code 与既有材料batch_no使用 utf8mb4_0900_ai_ci；新身份键不能不加审查地继承大小写/重音/空格比较规则。先由权威系统确定品号canonical code、批次大小写及空格语义，再选明确匹配的字符比较方式，禁止擅自lowercase、截断或丢前导零。批次长度现有材料128、Receipt产品批次100；不能128字符扫码后静默截断成100，更不能把材料batch_no自动塞进Receipt.product_lot_code。

#### D2. Base现状与最小绑定

[扫码身份解析](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionIdentityService.java:34) 当前只接受严格`EXE:[1-9][0-9]{0,18}`，最终检查Long范围；resolve只读当前业务库的Execution/WorkOrder/Material/确切发布工艺。没有品号+批次解析、绑定或新建行为。当前报工有扫码输入，领料/Quality/Receipt页面主要选择Execution；统一后台resolver并不等于四个页面已经统一接入。

建议一个**业务库内的永久绑定记录**，不新建生产批次实体、不复制ProductionBatch：

| 逻辑字段 | 最小语义 |
|---|---|
| scan_item_code | 经权威确认/规范化的生产扫码品号；不擅称等于产出product_code |
| production_lot_code | 经确认的生产周期批次；原材料共享批次不能直接冒名 |
| execution_id | 唯一内部Execution引用，必须与指定WorkOrder/产品/确切工艺一致 |
| batch_source / source_reference | 已确认的批次权威来源及必要证明引用，不包含外部凭证 |
| bound_by / bound_at | 服务端可信绑定人员和时间 |

数据库必须唯一约束**规范化后的(品号,生产批次)**，范围自然是当前业务库，不加factory_id。绑定更新/删除禁用，增加Execution删除保护；已完成、取消、提交失败或UNKNOWN后不释放、不重新绑定。扫码成功只返回Execution，不替代每个业务入口的权限/资源/状态校验。旧`EXE:{id}`继续用于精准管理/追溯；原始issueId/submissionId/reportId/receiptId分别定位历史动作。

数据库键候选须下一轮定案：① 两字段联合主键直接保证唯一，但当前Codegen Skill声明仅支持单主键，需先完善生成机制；② UUID单主键+两字段UNIQUE，与现有生成器契约相容，但**非主键UNIQUE需用户明确批准后才可实施**。这里只比较，不选择、建表或申请执行。不能用按Execution加锁来代替跨Execution的扫码唯一键，不能仅靠应用SELECT排重。若选择其他字符串主键编码，必须无损无歧义且校验原始元组，不能简单拼接或只按Hash猜同一码。

#### D3. 原二维码全流程通用的成立条件

可复用原格式，但需先证明该现场码能唯一标识一个生产周期。**同一原材料批次若供多个工单/周期，便与“该码永久绑定一个Execution”直接冲突**；此时不能在后台猜目标、选最新Execution或自动复用已完成记录。应由批次权威系统提供新生产批次/一对一映射，现场仍可保持品号+批次的扫码形式；是否更换标签内容必须由真实业务决定，不能宣称完全无感兼容。

首次绑定一旦本地准备事务提交，即使ERP失败、UNKNOWN或后来取消也保留该Execution和绑定；同码重试核实/补做原流程。若在准备事务提交前校验失败，整个事务回滚，未形成持久化绑定。ERP工单、产出产品、既绑定Execution所属工单/版本冲突明确409，不重绑、不自动切工单。原材料追溯仍留在issue_item，不能压成一个生产批次覆盖多种材料批次。

### E. ProductionQuantityService 最小改动点

保留一套Quantity解释器，候选只读契约`ProductionInputQuantityQuery`由**领料业务域**实现，返回本Execution可信累计投产输入及明确的未核验/待减保护信息。默认注入Mapper、当前business事务；不调用领料写Service，不缓存余额、不增加Facade或规则引擎。

调用方向为 `IssueCommands → Quantity → InputQuery → 额度Mapper`，InputQuery不能再调用Quantity/IssueCommands形成循环。生成单表Mapper负责额度SUM；确需关联主单/源事件资格时才用跨表查询，不在Quantity复制一套状态SQL。

最小修改面：

1. load读取明确的inputQty；calculate将它作为显式参数或独立模型字段，替换当前`view.targetQty()`首节点初始化。**不要把inputQty伪装成targetQty回传。**
2. 同时调整getExecutionQuantities、validateReportChange/validateChange、validateDefectChange的入口，含静态候选计算测试；不能只改查询而写入仍用target。
3. 后续节点保持现有顺序：I1=有效投产累计；R=REPORT+ADJUSTMENT净和；D=最新有效不良贡献；E=R-D；I下一=E；A=I-R。继续要求0≤D≤R≤I，最后节点E即实际产出；不合计所有工序报工或再次合计扣全路径不良。
4. 额度增加/减少在已有WorkOrder→Execution公共锁下校验，使用与Report/Quality相同的business事务；减少须将候选I带入完整链验证后才追加事实。
5. 可信正向领料确认、header/attempt/Extra更新、路径锁定及额度追加在同一个最终本地事务提交，重放不二次加额；外部HTTP仍在两次本地事务之间，不能包成“全部原子”。
6. 报工和领料变动后下一次context/quantities查询自然得到新额度；前端成功刷新/主动刷新，不需要反复UPDATE Execution、自动给所有后续工序加量或新建累计表。服务器写入时重新读锁内余额，不能相信旧页面。
7. 历史target/材料明细不足以证明旧投产额；迁移前核验可信来源。缺证据的历史执行需明确“投入口径未核验”，不能回退target或把未知一律当0后声称兼容。

现有路径逻辑可保留：[数量来源及路径核验](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:56)核对确切routing、实际selected节点、连续sequence、合法边/root-to-sink；[实际路径验证](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathValidator.java:18)不按display_order推测执行连接。`ProductionQuantityMapper.nodeTotals`只汇总实际选中节点，[不良最新有效版本汇总](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/quality/dao/MesProductionDefectMapper.xml:113)只取最新非VOID；[交接产出/差额](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReceiptService.java:61)已正确消费末节点E。当前工单数量摘要lastNodeReportedQty仍是gross，若要新增有效产出统计，应扩展既有Quantity查询能力，避免前端误读或逐Execution产生N+1。

### F. Report / Quality / Receipt / WorkOrder / MaterialIssue 兼容性

| 模块 | 可复用部分 | 必须处理的边界 |
|---|---|---|
| Report V2 | [正式报工](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReportSubmitService.java:92)：共同锁、请求幂等、能力/派工、确切路径及Quantity验证 | 首节点改用新投入来源。READY首次报工仍经正式开工；RUNNING后正式领料当前被拒绝，且每次正式报工都会调用paths.start。该方法对任何SUBMITTING/UNKNOWN/VOID_PENDING/VOID_UNKNOWN会全局拒绝，不能只改领料一处就承诺持续并发生产。可先保留保守门禁并明确影响，若要用既有额度继续报工，须另行明确待新增/待减少的不同规则 |
| 管理员Report V1 | [管理员报工](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReportService.java:55)：历史补录、正负订正/整笔冲销也复用Quantity | 不走V2的paths.start，所以待撤销输入的保护必须进入共有数量写校验，不能只改正式报工页面 |
| Quality | [不良版本命令](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/ProductionDefectService.java:98)：相同锁、版本化不良、候选D增量验证 | 只替换首节点I的权威；继续D≤R及下游R≤前节点E。历史报工总量/合格量口径核验不可省略，qualityIntegrated不代表放行 |
| Receipt | 末节点E-Rreceipt、超额差额、原始冲销和不自动库存副作用 | 输入来源变动不改Receipt公式；上游真实订正可保留差额并冻结新增。**当前requireQualityRelease始终失败关闭**，正的数量余额不是已允许实物交接 |
| WorkOrder | 产品/默认版本、多个Execution、计划分配展示 | plannedQty、SUM计划target、可信累计投入、末道E是四种不同指标。未发现当前执行创建/领料提交的计划硬上限；如果要求总投入≤工单计划，需在同一个WorkOrder锁下汇总所有Execution的可信额度/必要待确认占用，不能各Execution单独判断。当前计划分配SQL还含CANCELLED，须明确计划统计口径而非自动丢历史事实 |
| MaterialIssue | 主子单据、尝试快照、Extra、版本核对、UNKNOWN核实、共同锁 | 首次准备中可创建/绑定Execution；后续复用；投产与纯补料分型；RUNNING追加策略；冻结产出额度及BOM依据；可信确认才增加、受控订正才减少 |

Execution仍只管理身份、WorkOrder/产出物料、确切版本、实际路径和生命周期；不增“报工累计/良品累计/交接累计”等反复更新列。生命周期已有READY/RUNNING/COMPLETED/CANCELLED枚举，但本次查到的正式动作是选路/开工，普通更新冻结状态/起止时间；不能凭枚举就宣称完工/取消流程已完整实现，更不能以交接满额自动补完工。

### G. 并发、UNKNOWN、撤销和历史追溯风险

| 优先级 | 风险与证据 | 下一轮必需措施 |
|---|---|---|
| P0 | 旧扫码材料批次与目标生产批次不一定同一业务含义 | 取真实标签/权威批次证明，核对同码是否跨工单/周期复用；冲突不自动绑定 |
| P0 | 首节点只靠静态target，极小材料领料确认后也可能取得完整计划报工上限 | 明确产品投产事实+BOM/用料依据，Quantity改唯一输入来源，未知口径失败关闭 |
| P0 | 按CONFIRMED状态SUM会在VOID_PENDING/UNKNOWN时提前减少既有额度，或重放确认重复加额 | 不可变正负额度+稳定源事件主键，主单状态只是投影，未知结果不猜 |
| P0 | 外部作废“检查→释放锁→HTTP→再入事务”期间仍可能新增首道报工 | **准备时按候选额度检查，并持久化待减保护；所有首工序正向报工入口共同遵守，核实成功才记负额度，失败则解除，UNKNOWN保留**。不能只在最终落库时发现倒挂，因为ERP取消已无法本地回滚 |
| P0 | 两个工单/Execution争同一新扫码；WorkOrder锁不同，单Execution锁保护不了 | 数据库对规范化扫码元组永久唯一；失败创建整个本地准备事务回滚，不遗留第二个Execution；跨工单命中返回冲突 |
| P1 | 新建请求重复生成多张issue，后续每张都各自成功；扫码相同不等于请求相同 | 新建阶段有数据库级请求幂等，分别测试“不同合法分次请求”与“同request重放” |
| P1 | UNKNOWN被当失败重建，或者ERP查询NOT_FOUND被当作没有副作用 | 复用已持久化operationKey；只能核实原请求，NOT_FOUND仍不构成安全重建证明。绑定和Execution不自动释放 |
| P1 | RUNNING提交、前端READY选择器及每次报工start的全局门禁互相冲突 | 同时评审这三处；确认持续领料、已有有效额度及待减额度的语义，避免只放前端按钮 |
| P1 | 已绑定但尚无领料成功的Execution仍可能走当前READY物理删除入口 | 绑定追加后即纳入Execution删除/身份保护，永久保留完成/取消/失败历史关联 |
| P1 | 多Execution计划/投入/实产和CANCELLED统计混淆 | 分字段、分来源返回，不用SUM target冒充投入，也不把所有节点报工相加 |
| P1 | 旧报工“合格量”被迁成含不良总量后又扣Quality | 核验来源并明确迁移解释，不直接套旧合格量到新gross模型 |

“工序链内部不可倒挂”和“Receipt历史差额”必须分开：前者仍拒绝候选0≤D≤R≤I不成立的变更；后者不反向禁止已满足工序链条件的真实上游订正，而是保留Receipt事实、显示差额并冻结新增交接。

首次领料可适配当前事务架构：先锁WorkOrder，读取已绑定身份；无绑定时校验工单产品/适用确切工艺及必要路径选择，在同一本地事务创建READY Execution、永久扫码绑定、领料单/明细和冻结提交意图；提交本地准备后才调用外部。并发唯一冲突必须回滚本轮新建并在新事务按胜出的绑定核对归属，不能捕获错误后继续使用本轮临时Execution。首次选路在领料确认前已能执行，第一次可信领料确认负责锁路，因此**不存在必须“先领料成功才能建Execution”的架构循环**；当前只是UI/调用顺序要求预建Execution。分叉必须明确选定真实路径，不猜完整图为一条线。

### H. 八个业务案例推演（本轮无业务写入）

采用Python Decimal在内存按已确认公式复核，未运行生产Java方法、未创建数据库样本。案例6/7仅做源码/时序分析，未执行真实并发/ERP。下面的“期望一致”不能当当前APP集成PASS。

| 案例 | 新规则期望与推演 | 当前代码能否直接满足 |
|---|---|---|
| 1 投产100+200，A报60 | I=300，A剩余240，Decimal断言成立 | 不行。若target仍100，只剩40；预先设target300虽算出240，却在第二次确认前也已有全部额度，不证明领料驱动 |
| 2 投产300，A=100+50，B=40+30 | A净报150/剩余150；B输入150/净报70/剩余80 | 后续算法一致；当前只有target300这个前提，没有由多次投产求I的真实来源 |
| 3 投产300，A报200/D10，B报100 | A剩余100；A有效190；B输入190/剩余90 | 当前Quantity对该数学关系一致；首节点来源仍需改 |
| 4 纯补料不扩量 | I仍300 | 当前根本不读issue数量，数值恰好不变；但没有补料类型，不能宣称已实现区分 |
| 5 I300/A报280，撤100 | 候选I200，R280>I200，必须拒绝/保持原额 | 数学推演拒绝。当前RUNNING作废被粗粒度阻止，target降到200也会被报工净量校验拒绝，但不存在投产额度撤回模型；不能据此称案例已业务闭环 |
| 6 两人首次扫同品号+批次 | 最多一个持久化绑定/Execution；不同合法领料意图仍可复用它 | 未实现绑定，也没有旧码resolver；当前独立创建可产生两个Execution，WorkOrder串行也不能替代缺失唯一键。真实并发未运行 |
| 7 首次ERP UNKNOWN | 新额度0、同一已绑定Execution/同一原操作核实，不重建 | 当前已有单据/attempt的UNKNOWN保护，V2开工也拒绝；但没有旧码永久绑定或额度事实。Quantity在SELECTED路径仍可显示target派生输入，不能当可信投产。真实ERP未运行 |
| 8 A300，B250/D10，Receipt净200 | 末道E240，数量剩余40，Decimal断言成立 | Quantity→Receipt公式已一致，未重复扣上游D；但当前正式交接质量gate未具备，因此**数量余额40≠当前可成功新增交接40** |

额外时序：首次100确认后开工，再投产200——当前提交被READY门禁拒绝；若只移除门禁，则首节点仍target，不会自动得到新额度。外部取消前检查I300/A150可撤100后放锁，期间若A再报100，最终I200/A250会倒挂；候选方案必须用上述待减保护在外部调用前封住该窗口。

### I. 分阶段最小实施建议（未实施）

1. **先冻结三项输入契约**：真实扫码样本及原材/生产批次权威映射；BOM/用料来源、单位/损耗/舍入及版本冻结；扫码唯一键与请求幂等的数据库方案。若旧材料批次跨周期复用，先解决业务冲突，不先写自动绑定。
2. **领料域补事实与永久绑定**：最小投产额度记录、投产/补料类型、提交意图及绑定历史保护。通过正式迁移与生成器工作流实施，不能复活ProductionBatch；涉及新UNIQUE或复合主键生成限制时先作明确决策。历史可信投入核验单列，不把target补成假领料事实。
3. **统一Quantity切换首节点来源**：同时改查询/Report V1/V2/Quality/额度减少验证，继续共同锁与全链约束；移除报工量必须≤计划target的权威混用，计划编辑规则按其真实计划语义保留/评审。Receipt计算与事实保持。
4. **首次/再次领料及页面整合**：首次准备创建并绑定、再次按同码定位同Execution、纯补料0额度；只读resolver供四业务页面复用，所有命令仍传精确Execution及请求身份。保留主子单据组件，不给通用GET加写入副作用；持续领料的READY/RUNNING/UNKNOWN门禁一起验收。
5. **上线前完整验收**：八案例真实服务/指定MySQL回归；增加不同工单争码、大小写/空格/前导零/分隔符、同请求重放/同码不同合法请求、外部UNKNOWN跨重启核实、额度撤销与V1/V2首道报工并发、Quantity与Quality订正双顺序、Receipt超额保留及跨厂同本地主键隔离。前端切厂/撤权/迟到响应、真实扫码设备需分别留证。未完成这些不宣称新业务GO。

不建议：更新Execution累计投入/产出；从原材数量推最终产出；把新投入同时加到所有后续节点；按最新Routing推历史实际路径；建立通用规则引擎、生产Facade或新的人员/批次壳；为本轮提前改Quality/Routing/库存。

### J. 最终结论与五个明确回答

| 问题 | 回答 |
|---|---|
| 1 是否需要新增投产额度流水表？ | **本需求推荐新增一张归领料域的不可变额度流水**，承载分次确认、冲销/订正与审计；并非数学上只能加表。如果下一轮明确压缩为“领料头冻结额度+仅整单撤销”，可比较直接头汇总，但仍必须补齐事实和完整状态审计，不能直接用现有数据 |
| 2 是否可以直接汇总现有MaterialIssue？ | **当前不可以**：没有产品投产额度/补料类型/BOM依据。不得SUM材料明细，也不能用CONFIRMED状态一个条件忽略作废处理中与UNKNOWN |
| 3 是否需要调整Execution.targetQty语义？ | **需要收敛为计划语义，保留列和兼容展示**；停止首节点输入权威及投入/实产统计依赖，不反复随领料/报工更新。不可自动把现有可编辑值宣称为可信历史投入或不可变快照 |
| 4 首次领料创建Execution是否符合事务架构？ | **符合本地准备事务架构，但当前未实现**。Execution/绑定/意图同库事务创建；外部调用仍分阶段，不能宣称跨ERP原子；失败/UNKNOWN后复用已提交身份，必须先有明确实际路径 |
| 5 品号+批次能否真正全流程通用？ | **可以作为已验证生产身份的统一查找入口，当前不能无条件承诺原码全部兼容**。先证明生产批次一对一权威；若是跨周期共享原材料码，必须解决映射冲突。统一resolver及四页面接入后仍保留各业务授权/资源/状态校验，历史按精确业务ID追溯 |

**架构方向：CONDITIONAL GO。当前按新规则直接运行：NO-GO。** 待解决的是投入事实、身份权威和数据库并发边界，不需要另造一套工序数量引擎。当前质量放行未具备，Receipt正向仍保持NO-GO；本轮审查不改变其状态。

### 附录：targetQty逐文件检索位置索引

检索范围为两个Base仓库的所有生产源码/SQL/前端及测试，并补充ExecutionQuantity位置构造与通用CRUD传递检查；生成持久化命中归为基础读写能力，不代表存在公开旁路。下列是本轮当前工作区行号，后续代码改动可能漂移。主线语义以B矩阵及方法名为准。

| 文件 | 当前行号 | 分类 |
|---|---|---|
| [UI/src/api/production/mesProductionExecution.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/mesProductionExecution.ts:11) | 11, 29, 30 | 前端类型/展示/编辑 |
| [UI/src/api/production/productionIdentity.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/productionIdentity.ts:16) | 16 | 前端类型/展示/编辑 |
| [UI/src/api/production/productionReceipt.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/productionReceipt.ts:4) | 4 | 前端类型/展示/编辑 |
| [UI/src/api/production/productionReport.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/productionReport.ts:4) | 4, 8 | 前端类型/展示/编辑 |
| [UI/src/api/production/productionReporting.ts](/Users/user/work/lixin/mes-base-ui/src/api/production/productionReporting.ts:6) | 6 | 前端类型/展示/编辑 |
| [UI/src/components/business/production/ExecutionIdentityLookup.vue](/Users/user/work/lixin/mes-base-ui/src/components/business/production/ExecutionIdentityLookup.vue:66) | 66 | 前端类型/展示/编辑 |
| [UI/src/components/business/production/ProductionReportManager.vue](/Users/user/work/lixin/mes-base-ui/src/components/business/production/ProductionReportManager.vue:96) | 96 | 前端类型/展示/编辑 |
| [UI/src/components/business/production/WorkOrderExecutions.vue](/Users/user/work/lixin/mes-base-ui/src/components/business/production/WorkOrderExecutions.vue:27) | 27 | 前端类型/展示/编辑 |
| [UI/src/views/production/mesProductionExecution/fields.ts](/Users/user/work/lixin/mes-base-ui/src/views/production/mesProductionExecution/fields.ts:52) | 52 | 前端类型/展示/编辑 |
| [UI/src/views/production/productionReceipt/index.vue](/Users/user/work/lixin/mes-base-ui/src/views/production/productionReceipt/index.vue:122) | 122 | 前端类型/展示/编辑 |
| [UI/src/views/production/productionReport/index.vue](/Users/user/work/lixin/mes-base-ui/src/views/production/productionReport/index.vue:61) | 61 | 前端类型/展示/编辑 |
| [UI/src/views/production/productionReporting/index.vue](/Users/user/work/lixin/mes-base-ui/src/views/production/productionReporting/index.vue:79) | 79 | 前端类型/展示/编辑 |
| [UI/tests/productionExecution.test.ts](/Users/user/work/lixin/mes-base-ui/tests/productionExecution.test.ts:16) | 16 | 测试/夹具 |
| [UI/tests/productionPages.test.ts](/Users/user/work/lixin/mes-base-ui/tests/productionPages.test.ts:52) | 52 | 测试/夹具 |
| [UI/tests/productionReceipt.test.ts](/Users/user/work/lixin/mes-base-ui/tests/productionReceipt.test.ts:15) | 15 | 测试/夹具 |
| [UI/tests/productionReport.test.ts](/Users/user/work/lixin/mes-base-ui/tests/productionReport.test.ts:15) | 15, 72 | 测试/夹具 |
| [UI/tests/productionReporting.test.ts](/Users/user/work/lixin/mes-base-ui/tests/productionReporting.test.ts:10) | 10 | 测试/夹具 |
| [Backend/mes-admin/src/main/resources/db/business/V16__production_execution.sql](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V16__production_execution.sql:8) | 8 | Schema |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ExecutionRouteViewHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ExecutionRouteViewHttpTest.java:18) | 18 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/OperatorAssignmentHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/OperatorAssignmentHttpTest.java:97) | 97 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/OperatorAssignmentMySqlTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/OperatorAssignmentMySqlTest.java:118) | 118 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionChainHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionChainHttpTest.java:83) | 83, 137 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionCoreV1HttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionCoreV1HttpTest.java:54) | 54 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionExecutionHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionExecutionHttpTest.java:22) | 22 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleHttpTest.java:57) | 57 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleMySqlTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleMySqlTest.java:106) | 106 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityAuditMySqlTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityAuditMySqlTest.java:37) | 37 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityQuantityHttpTest.java:25) | 25 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionReceiptHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionReceiptHttpTest.java:69) | 69 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionReceiptMySqlTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionReceiptMySqlTest.java:56) | 56 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionReportHttpTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionReportHttpTest.java:86) | 86, 131 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/issuefixture/ProductionReportMySqlTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionReportMySqlTest.java:131) | 131 | 测试/夹具 |
| [Backend/mes-admin/src/test/java/dev/mesbase/server/WorkOrderRequestBudgetTest.java](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/mesbase/server/WorkOrderRequestBudgetTest.java:182) | 182 | 测试/夹具 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/MesProductionExecutionController.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/MesProductionExecutionController.java:110) | 110 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionIdentityController.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionIdentityController.java:30) | 30 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionReceiptController.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionReceiptController.java:71) | 71, 95 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionReportingController.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/ProductionReportingController.java:58) | 58 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/MesProductionExecutionVo.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/MesProductionExecutionVo.java:14) | 14 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/ProductionIdentityVo.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/ProductionIdentityVo.java:6) | 6 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/ProductionReportViews.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/controller/vo/ProductionReportViews.java:35) | 35, 41, 58, 69 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionCreateCommand.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionCreateCommand.java:16) | 16 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionEntity.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionEntity.java:18) | 18, 76, 77, 80, 81 | 持久化 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionExample.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionExample.java:410) | 410, 415, 420, 425, 430, 435, 440, 445, 450, 455, 460, 465 | 持久化 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionQueryDto.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionQueryDto.java:23) | 23 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionUpdateCommand.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/MesProductionExecutionUpdateCommand.java:16) | 16 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/ProductionIdentity.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/ProductionIdentity.java:8) | 8 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/domain/ProductionReportModels.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/domain/ProductionReportModels.java:40) | 40, 45 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionExecutionRules.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionExecutionRules.java:100) | 100, 101, 181, 182 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionIdentityService.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionIdentityService.java:45) | 45 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:61) | 61, 62, 87, 88, 140, 159 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/java/dev/mesbase/business/production/service/impl/MesProductionExecutionServiceImpl.java](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/impl/MesProductionExecutionServiceImpl.java:65) | 65, 66, 103, 132 | 业务/HTTP模型 |
| [Backend/mes-business/src/main/resources/dev/mesbase/business/production/dao/MesProductionExecutionMapper.xml](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/production/dao/MesProductionExecutionMapper.xml:10) | 10, 79, 111, 116, 136, 137, 177, 178, 230, 231, 269, 297, 298, 333 | 持久化 |
| [Backend/mes-business/src/main/resources/dev/mesbase/business/production/dao/ProductionQuantityMapper.xml](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/production/dao/ProductionQuantityMapper.xml:19) | 19, 25 | 持久化 |
| [Backend/mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkOrderExtraMapper.xml](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkOrderExtraMapper.xml:17) | 17, 18, 19 | 持久化 |
| [Backend/mes-business/src/test/java/dev/mesbase/business/production/ProductionQualityQuantityTest.java](/Users/user/work/lixin/mes-base/mes-business/src/test/java/dev/mesbase/business/production/ProductionQualityQuantityTest.java:47) | 47, 88 | 测试/夹具 |

只读保护：本轮项目输出前对五仓共7728个已跟踪/未忽略文件建立内容指纹（排除本报告）；结束复核报告外文件变化0、新增0、HEAD变化0，两Base仓库已有并行工作均保留，git diff --check通过。现有MySQL凭证仅交由既有连接配置使用，未输出或修改；未将旧测试源码存在当作本轮测试通过。


## 2026-10-16 追加：ktg-mes ERP 扫码领料与工单匹配业务专项审查（只读）

### 范围、结论等级与时间线

本节只研究正常 ERP 成功后的现场业务，不评价历史代码结构，不迁移或实现业务。Source 的 ktg-wheel 排除。没有调用 ERP/OAPI、没有数据库写入、没有运行构建或迁移；下述“实际规则”指当前工作区源码可证规则，不冒充部署观测。

| 仓库 | 本次核对 HEAD | 工作区 |
| --- | --- | --- |
| mes-base | `4679154eccb61444f1c0f795c9620a54ec6e207d` | 已有生产、质量、交接及测试等未提交改动，保留 |
| mes-base-ui | `02a6554baff657f6c8dfe42cebdcbc9e5f863473` | 保留已有未提交改动 |
| ktg-mes | `7fd7bec2a8eea09261ade1e822127932ff3207d9` | `docs/architecture/transaction-ownership-audit.md` 已有并行修改，未触碰 |
| ktg-mes-ui | `42481d53867fcb77cb53bd807433b084fd73bfea` | 干净 |
| heri-uni-app | `f109f7937fff51d4395d574890b57f3280d10d5f` | `config.js` 已有修改，未触碰 |

**对上一节结论的限定与补充：**“扫码小程序 qty 直接作为主材料领用量”仍然成立，但不能概括 Source 全部领料入口。当前 Source 另有生产确认中心，明确输入 `outputQty` 并按 ERP 工单用料计算全部材料。这是本轮新增核对的关键业务证据。用户要求的“先验库存，再选工单，再按预计产出计算”是可以组合现有能力形成的目标流程，但旧普通小程序并非完整按这个顺序执行。

证据路径约定：下文 `S:` = `/Users/user/work/heri/ktg-mes/ktg-mes/src/main/`；`U:` = `/Users/user/work/heri/heri-uni-app/`；`W:` = `/Users/user/work/heri/ktg-mes-ui/`；`B:` = `/Users/user/work/lixin/mes-base/`。行号均为本次工作区版本。

### A. 原有扫码领料的真实业务流程

#### A1. 普通小程序入口

1. `U:pkg-issue/pages/issueReceipt/index.vue:330` 解析 `品号#批次`。品号是待领用材料品号，不等于工单产出品号；工单另有 `productCode`。码本身没有工单、仓库、生产中心或 Execution 信息。
2. 请求 `/mes/oapi/issueReceipt/workorder`。后端先查断阶映射：原品号/批次 → 后阶品号/批次，无映射沿用原值。
3. 按映射后**材料品号**匹配 ERP `MO_D`，取得 MO_ID；再到当前 MES 业务库找候选工单，限定状态 `1/2/3`，最后保留单号前缀 `5100/5103/5105`。
4. 对每个候选工单，用其工作中心 `attr1` 找 `md_workshop.workshop_code`，再以车间 `attr1` 确定仓库，查询该仓库的品号/批次库存，返回“工单 → 库存数量”映射。
5. 页面一个候选自动选中，多个由现场用户选择。选择工单后展示产出品号、工作中心名称、库存参考量，按产出品号查工艺，并加载该 ERP 工单其他材料。
6. 用户输入主材料 `qty`，选择配件库存批次/数量及流程卡。提交普通 5600 合并领料：主件 qty 直接作为主材料领用量，配件按所选明细数量提交；ERP 工作中心、仓库、材料单位、工单材料行来自后端读取的工单与车间映射。
7. 本地扣料记录保存材料、物料批次、工单、产出品号、流程卡、路线、领料数量和操作者；ERP 正式创建 5600，并按 `issue-receipt-confirm-enabled` 决定是否继续审核。普通入口没有即时刷新本地工单状态。
8. 报工时再按工单+流程卡查 ERP 领料单，读取尚未计入本地的单据，按材料品号累计到对应路线的首工序材料余额。

**顺序差异：**普通旧入口实际上先找工单，再按候选工单仓库查库存；库存 0 不会从上述候选集合自动删除。`selectRemainderNumber` 只取查询首行并转为整数参考量，也没有库存大于 0 的查询条件。不能把它称为已实现“先唯一定位有效库存，再查询候选”的准入规则。这只说明现有业务行为，与 ERP 正式扣减成功假设不冲突。

断阶还有一个契约边界：后端库存查询使用映射后的品号与批次，但普通接口只把映射品号 `itmeCode` 返回；页面提交批次仍取扫码解析值。因此适配契约必须同时明确原始码与最终库存身份，不能由前端猜映射后的批次。

主要证据：`S:java/com/ktg/mes/erpapi/controller/IssueReceiptController.java:63,104`；`S:java/com/ktg/mes/erpapi/service/impl/IssueReceiptServiceImpl.java:271,1082,1187,1242`；`S:java/com/ktg/mes/pro/service/impl/RouteBrokenStepServiceImpl.java:28`；`U:api/issueReceipt/issueReceipt.js`；`U:pkg-issue/pages/issueReceipt/index.vue:249,344,475`。

#### A2. 生产确认中心入口

Web `生产领料确认` 是另一条已存在链路：选择工单 → 输入**预计产出数量** → 计算全部材料 → 填材料批次/可选流程卡和路线 → 保存 → 审核创建 ERP 5600。它不是扫码查库存入口，也没有在该表单中提供库存批次选择。

创建/更新由服务端重算用料，前端只提交工单、预计产出、路线、流程卡及材料品号/批次，不信任前端计算数量。审核后保存 ERP 单号、不可变单头 ID、APPROVED/EXISTS、审核人/时间。原有本地单头保存 outputQty，明细保存单耗和计算用料快照。

证据：`W:src/views/mes/pro/materialIssueConfirm/components/MaterialIssueConfirmForm.vue:4,19,23,28,134`；`S:java/com/ktg/mes/pro/service/impl/ProductionMaterialIssueServiceImpl.java:128,133,198,378`。其通用工单选择器只传单号关键字、分页；`ProWorkorderMapper.xml:112` 的状态过滤是可选参数，该表单没有固定传扫码入口的 `1/2/3` 条件，不能宣称两个入口筛选完全一致。

### B. ERP 库存、工单和组织过滤的实际规则与字段

| 环节 | 真实条件/字段 | 业务含义和边界 |
| --- | --- | --- |
| 断阶 | `before_item_code + before_lot_code` → `after_item_code + after_lot_code` | 库存查询身份可变化；不是无条件保持扫码品号 |
| ERP 材料适用工单 | `MO.MO_ID = MO_D.MO_ID`；`MO_D.ITEM_ID = ITEM.ITEM_BUSINESS_ID`；`ITEM.ITEM_CODE = itemCode` | 按已展开到工单的材料行匹配，不是按产出品号相等，不是临时遍历通用 BOM |
| 扫码日期窗口 | `MO.DOC_DATE >= DATEADD(MONTH,DATEDIFF(MONTH,1,GETDATE()),0)` | SQL Server 当月月初表达式；DATEDIFF 的起点1是1900年1月2日，不是当前时间减一天。不能简化为“所有未关闭工单” |
| 本地候选 | 当前业务库 `pro_workorder.mo_id IN (...) AND status IN (1,2,3)` | 排除其他状态；没有现场读取字典，本轮保留原码。仓库测试样例将其标为未生产、已发料、生产中，不能以测试样例证明部署字典 |
| 单别 | `workorderCode.startsWith(5100/5103/5105)` | 普通扫码入口保留厂内生产单别；不是所有 ERP 生产单均可选 |
| 工厂/组织 | 工单同步 SQL：`MO.Owner_Org_RTK='PLANT'`，关联 `MO.Owner_Org_ROid=PLANT.PLANT_ID`，`PLANT_CODE=FactoryContext.erpPlantCode()` | 归属厂别在同步入口过滤，候选再与当前厂业务库交集；不是扫码材料 SQL 自己直接带 PLANT 条件 |
| 审核 | 同步和按单号同步 SQL 都有 `MO.ApproveStatus='Y'` | 审核条件在同步时成立；普通扫码候选没有实时复核此字段，不能证明同步后一直未撤审 |
| 来源/中心 | `MO.SOURCE_ID_RTK`；`SOURCE_ID_ROid` 关联 `WORK_CENTER.WORK_CENTER_CODE`，映射本地工单 `attr1`；来源类型映射 `attr2` | 正式领料要求 WORK_CENTER，委外来源不支持。未发现按当前用户部门、指定生产中心白名单进一步过滤候选 |
| 仓库选择 | 工单 `attr1` → `md_workshop.workshop_code` → `md_workshop.attr1` 仓库编码 | 仓库随选定工单工作中心确定，不是用户任意跨仓领料 |
| 扫码库存 | `ITEM_ID_ITEM_CODE=itemCode`；`ITEM_LOT_ID_LOT_CODE=lotCode`；`WAREHOUSE_ID_WAREHOUSE_CODE=工作中心映射仓库` | ERP 真实库存查询服务 `ywms.item_warehouse_bin.query`；首行值仅作旧页参考 |
| 配件库存 | 同一库存服务，材料品号+映射仓库+`INVENTORY_QTY>0`，按批次升序 | 页面可分选多个批次；不是任意仓库混用 |
| 库存维度 | 返回 `BIN_ID_BIN_CODE`、`WAREHOUSE_ID_WAREHOUSE_CODE`、`WAREHOUSE_ID_Owner_Org_PLANT_PLANT_CODE`、`BIN_ID_Parent_Owner_Org_PLANT_PLANT_CODE`、`PLANT_CODE`、`UNIT_CODE`、`INVENTORY_QTY` | 同品号批次可能多仓/多库位；码不含这些维度。该普通查询只显式过滤仓库，没有额外 PLANT/bin 条件；返回有字段不等于已作过滤 |
| 剩余生产/材料 | 读到 `PLAN_QTY/COMPLETED_QTY/REQ_QTY`、`REQU_INCLUDE_MULT_QTY/ISSUED_QTY/UN_ISSUED_QTY` | 扫码候选 SQL 没有 `PLAN_QTY-COMPLETED_QTY>0`，产出计算也没有以 UN_ISSUED_QTY 截断；不能宣称存在严格剩余未生产额度门禁 |

精确 SQL：`S:resources/mapper/erp/ErpMoMapper.xml:23`；`S:resources/mapper/pro/ProWorkorderMapper.xml:135`；`S:resources/mapper/erp/ErpWorkOrderMapper.xml:37,44,87`；`S:resources/mapper/erp/ErpMoDMapper.xml:58`。厂别注入：`S:java/com/ktg/mes/erp/service/impl/ErpWorkOrderServiceImpl.java:64,110`。库存：`IssueReceiptServiceImpl.java:1082,1128`；`S:java/com/ktg/mes/manager/impl/IssueReceiptManagerImpl.java:37`；`S:java/com/ktg/mes/erpapi/pojo/ItemWarehouseBin.java:6`。

**跨组织匹配结论：**正常、正确绑定和同步的当前厂业务库可通过工单交集排除其他厂；生产中心只用于展示及选仓，未限定只能当前某中心。不能从源码证明“同产品多中心只会返回一个”或“仓库编码在所有 ERP 组织全局唯一”。实际绑定、仓库主数据归属和 ERP 服务端隐含过滤未访问运行环境核实；不据此宣称发生过跨组织错误。

### C. 产出数量和自动领料计算依据

#### C1. 生产确认中心：与本轮目标最接近的公式

设工单计划产出 `P = pro_workorder.quantity`（由 ERP `MO.PLAN_QTY` 同步）；本次计划产出 `q = outputQty > 0`。

每种材料 i：

- `T_i = SUM(本工单 MO_D.REQU_INCLUDE_MULT_QTY)`，只取正需求，同品号合并。
- `qtyPer_i = ROUND_HALF_UP(T_i / P, 12)`。
- `issueQty_i = ROUND_HALF_UP(T_i × q / P, 6)`；直接用原总需求计算，避免先舍入单耗再乘产出。
- 必须提交工单全部可领材料，每个品号恰好一次；不能在这个入口随意省略一种材料或改算出的数量。
- 单位来自 ERP 材料 `UNIT.UNIT_CODE`，没有另行做重量/件数转换、包装取整或跨单位合并。
- 系统使用 ERP 已给出的 REQU_INCLUDE_MULT_QTY，未另外叠加损耗率。该字段是否包含特定 ERP 工艺损耗、固定耗用等，由 ERP 定义和工单展开决定；源码不足以证明精确组成。
- 当前按品号合并并沿用首条单位；上线契约应要求同品号行单位一致，否则先由 ERP 规范换算，不能直接把不同单位数量相加。

例：ERP 计划 P=1000件，A总需求2000个，B总需求500kg。此次 q=100件，A=200.000000个，B=50.000000kg。此例是公式演算，不是现场 ERP 响应。

当前确认入口额外要求：同单非空材料批号去重后最多一个，空批次允许；不是“所有物料必须各自具有同一个物理批次”的普遍制造规则。它与小程序配件可选不同批号不同，也不足以覆盖控制臂装配多原材独立批次。

证据：`ProductionMaterialIssueServiceImpl.java:378-493`（方法范围，正式引用定位378）；`ErpWorkOrderMapper.xml:13`、`ErpMoDMapper.xml:58`。

#### C2. 普通小程序不能直接当同一公式

主材料 `qty` 原样写 `INVENTORY_QTY/ISSUE_RECEIPT_QTY`。配件页则取 `MO_D.REQU_INCLUDE_MULT_QTY / MO.PLAN_QTY`，使用 `form.qty × 配件单耗` 算目标量，再从库存批次分配。没有把主材料 qty 除以主材料单耗后先换算成产出。因此只有主材单耗为1等特定条件下，两条入口的“输入100”才可等价，不能笼统说原小程序输入的是计划产出。

配件计算用 JavaScript Number，SQL 比值未指定 Java 统一舍入；生产确认中心则明确采用12/6位 HALF_UP。Base 适配优先使用已存在的服务端预计产出公式，不能照搬普通页数量含义。

纯补料方面：`docNo5600Merge` 有 qty<=0 时省略主材的分支，但普通 Controller 只有 qty>0 才调用服务，页面 min=1；所以不能据注释宣称普通扫码入口已支持纯补料。确认中心同样强制 outputQty>0 且全部材料重算。现有入口未形成稳定的“补材料但不增加投产量”业务契约。另有不良页面补料弹窗及 `/mes/qc/defective/supplementMaterial/...` 接口，但本次核对 `QcDefectiveServiceImpl.supplementMaterial:1017` 只校验数量/材料/工单，没有正式ERP领料或数量贡献写入；页面 `submitSupple:580` 当前调用 `addDefective`，构造的补料data并未调用5600（相关调用为注释）。所以“有补料按钮/接口”不代表补料已完成正式业务闭环。

证据：`IssueReceiptServiceImpl.java:271,325,846`；`IssueReceiptController.java:74`；`U:pkg-issue/pages/issueReceipt/index.vue:176,304`；`U:pkg-issue/pages/issueReceipt/selectedAccessory.vue:59,73,193`；`S:java/com/ktg/mes/erp/service/impl/ErpMoServiceImpl.java:28`。

### D. 同批次多次领料的原有聚合规则

旧系统没有因再次领料而创建一个 Base 式执行身份。每次领料追加记录和 ERP 单据；材料消费归集主要是：

`工单号 + 流程卡号 + routeId + 首工序processId → 各材料 acceptedQty 余额`。

`updateMaterial` 按工单+流程卡查询 ERP 领料单；排除5704/5705退料单号，按单号去重，已在 `pro_material` 记录的单号跳过；新单读取 `ISSUE_RECEIPT_QTY`，按材料品号加入首道材料余额，并记录本地已吸收的 ERP 单据。故重复扫码不等于重复贡献，**新成功领料单**才贡献新增材料数量；旧代码去重/锁只能说明此入口意图，不构成已验证的跨节点并发保证。

正式报工校验按材料剩余量折算：`可报工量 = MIN_i(acceptedQty_i / qtyPer_i)`，除法5位 HALF_UP；首工序要求已具备全部工单材料，报工按用量消耗余额。多材料追加时，只有瓶颈材料改善，才能提高可生产数量，并非把所有材料数量相加，也不是简单累计 pro_buckle.quantity 当产出。

注意普通“获取可报工参考量”接口另有主材过滤与乘单耗表达式，和正式写入校验不同；本报告以正式写入校验说明业务门禁，不把页面参考量当最终权威。

完结限制也分入口：普通 insert5600 先按本地扣料记录查报工完结，匹配工单、产出品号及物料批次作为 xtransferNo，存在非未完结记录即拒绝再次投料。生产确认中心 approve 调用“不能换工单”校验后直接 insertBuckle，没有调用同一完结校验。因此不能断言所有旧入口一致禁止完结后领料；Base 应明确由 Execution 最终状态统一拒绝，而不是照搬某条旧入口差异。

证据：`IssueReceiptServiceImpl.java:804,830,903,1151,1215`；`S:java/com/ktg/mes/pro/service/impl/ProReportingWorkServiceImpl.java:169,668,1148,1855`；`ProductionMaterialIssueServiceImpl.java:217`。

### E. 原材料批次与生产身份的真实关系

1. **品号+批次首先是库存材料身份。** 不包含 ERP 工单或生产中心。工单产出品号可不同；断阶还可以转换材料身份。同码本身不能证明唯一生产活动。
2. **旧系统叠加了不能换工单的应用规则。** `checkSameDocCodeBuckle` 按材料品号+物料批次查历史工单；发现已有工单且目标不在其中时拒绝。单次入口和生产确认中心传真实物料批次；普通合并入口却传 xtransferNo，而查询仍匹配 item_lot_code。主材流程卡等于批次时通常相合，配件批次不同时不能视为同一个严格约束。
3. **同码用于两个工单不是旧普通业务的明确许可。** 单次/确认入口意图拒绝；合并入口取决于流程卡与物料批次的关系。ERP 本身是否允许分配同原材批次给多工单，不能由这段 MES 限制反推。
4. **原材料多产出批次关系没有明确独立实体。** 物料批次、流程卡、产出品号与工单在不同查询中组合使用；能证明多个 ERP 领料单可进入同工单/流程卡/路线，不能证明“一原材批次分给多产出批次”的完整正式用例已经实现。
5. **仓库不应机械成为生产身份。** 同生产活动可分次从允许仓库领料；反过来同物料批次在两个仓库有库存，也不自动代表同一个 Execution。仓库属于材料来源事实。
6. **跨厂是上下文，不是码内属性。** Base 应继续使用既有 Factory→业务库隔离；不向业务表补 factory_id，也不让某厂的绑定命中另一厂。
7. **并发首扫未形成永久绑定证明。** 本轮看到的是先查扣料历史再追加记录，不能据此保证两个用户同时首扫、分别选不同工单时仅一个成功。没有执行数据库并发试验，也没有用静态分析假造并发PASS。

可沿用为 Base 永久绑定准入的是：可信库存身份、合法工单材料适用性、当前组织/中心、已确定的生产流程卡身份、工单产品与确切工艺版本、不可变 Execution 归属；不能未经决定把任意 raw item+lot 在整个生命周期永久独占给一个 Execution。

证据：`S:java/com/ktg/mes/pro/service/impl/ProBuckleServiceImpl.java:37,66,96,123`；`S:resources/mapper/pro/ProBuckleMapper.xml:175`；`IssueReceiptServiceImpl.java:1215`。

### F. Base MES 最小适配契约（建议，未实施）

保留 ProductionExecution、ExecutionPath、MaterialIssue、ProductionQuantityService、ProductionReport、Quality、Receipt 的对象职责，不复制 Batch/ProBuckle，不创建第二套数量算法。以下是边界输入/输出契约，不是新增数据库模型或DDL。

| 契约 | 最小必需内容 | 权威和使用方式 |
| --- | --- | --- |
| ERP扫码库存结果 | 原始扫码品号/批次；解析后的规范身份；断阶后的实际材料/批次及映射来源；库存记录/仓库/库位、所属厂别、单位、可用数量及读取时间 | 服务端返回明确库存候选；多仓/多库位保留多行，禁止首行冒充唯一库存；选定工单后校验来源仓属于允许中心/组织 |
| ERP工单候选 | 不可变MO_ID、单号、产出品号/名称、PLAN_QTY、COMPLETED_QTY、审批/状态原值和来源、Owner组织/厂别、WORK_CENTER、允许仓库、匹配的MO_D材料行 | 由ERP已展开工单用料判适用，不按产品相似度；当前厂、来源类型、中心和正式状态在服务端确认，多个合法候选用户选 |
| 本次计划与计算清单 | 本次outputQty与产出单位；ERP工单计划基数；材料行身份、总需求、单耗、计算数量、单位、库存分配；计算来源/时间或版本 | 复用Source服务端预计产出公式；逐材料/库存批次分配；提交时冻结，ERP确认量若不同必须明确核对，不能用请求值假装确认值 |
| 正式领料数量贡献 | 既有issue/submission/operationKey/externalReference；executionId；已确认的各材料数量/单位；本次生产贡献数量和原因 | 只有可信正式成功贡献一次；重放不重复贡献，新合法分次单可贡献；失败/UNKNOWN沿用现有防护，不改为新建重试 |
| 扫码与Execution建立 | 规范库存身份 + 明确生产身份（流程卡或其他已决定的批次身份） + 所选工单 → 唯一executionId | 只读扫库存不创建生产事实；确认动作再建立/复用绑定。已有绑定核对工单/产品/确切工艺；终态不重开；并发绑定需持久化原子保证，不能只靠不同工单各自锁 |

推荐正常链路：扫码 → 查真实库存候选 → 用材料行/组织/中心筛工单 → 用户选择 → 输入本次计划产出 → 服务端计算多材料 → 分配库存批次 → 确认原绑定或建立一个 Execution → 复用既有选路和正式领料提交 → 成功贡献 → 共用 ProductionQuantityService 查询/校验 → 多次正式报工 → 原Quality/Receipt流程。

数量接法需保留两个语义：Execution.targetQty 仍是计划；正式投产输入来自可信领料贡献。普通追加投产可以在全部计算材料确认且单位一致时贡献对应 outputQty；纯补料不应无声明地增加计划产出贡献。Source的多材料瓶颈法可作为材料充足性依据，不能又创建第二个可写投产权威。旧方法“补瓶颈材料即可增加可报量”与拟采用“显式追加投产贡献”并不等价，须按H3决定。

**当前 Base 缺口，不能声称仅接API就已实现：** `MaterialIssue` 已支持多单关联同Execution，但单头无outputQty/投产或补料类型，明细没有工单用料计算快照；`ProductionIdentityService.resolve` 当前只支持 `EXE:{id}`，没有旧码解析/永久绑定；`MaterialIssueSubmissionService.prepare` 当前仅允许READY未开工Execution；`ProductionQuantityService.calculate` 首节点输入仍来自targetQty。需后续有边界任务在既有领料事实/契约中补齐能力，当前不更改Schema或生产模型。不得把本次“最小方案”说成上述能力已完成，也不能靠改targetQty伪造实际投产。

Base 证据：`B:mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueEntity.java:5`；`.../issue/service/MaterialIssueSubmissionService.java:125`；`.../production/service/ProductionIdentityService.java:39,54`；`.../production/service/ProductionQuantityService.java:140`。Quality与Receipt保持原边界和实际放行契约，不由ERP领料成功代替质检或交接事实。

### G. 八场景核对及沿用/改变清单

| 场景 | Source可证行为 | Base适配结论（未实施） |
| --- | --- | --- |
| 1 同品号同批次多次领料 | 同工单/流程卡/路线多个新ERP单据按材料累加；报工消耗余额 | 同一生产身份复用同一Execution；不同正式分次单各贡献一次，targetQty不承担累计投入；多材料充足性不能按材料数量求和 |
| 2 同原材批次用于两个ERP工单 | 候选材料匹配可得到两单；已有扣料换单规则意图拒绝，合并入口键关系有差异 | 是否允许原材复用取决于生产身份决策。若允许，raw lot作材料来源，一码返回多个生产关联，用户明确选生产身份；不能继续承诺仅凭raw码唯一定位所有业务 |
| 3 两个仓库都有同品号批次 | 逐候选工单工作中心映射仓库查；配件也限制映射仓库；未提供跨仓任选 | 库存候选保留仓/库位/组织，按候选工单允许来源选择；不把两仓库存首行或不加区分地混合 |
| 4 同产品两个生产中心工单 | 按材料可同时命中；选择用户决定；中心用于选仓，没有按用户中心固定排除 | 展示中心/组织/仓库；若两者都授权且适用则用户选，否则按明确业务策略过滤，不按产品自动取第一单 |
| 5 控制臂装配多材料 | 确认中心按各材料总需求分别算，但限制同单非空材料批号最多一个；小程序配件可分批 | 沿用全部材料及各自单位计算；材料各自库存批次不能被统一流程卡替代；不沿用全材料同批号限制 |
| 6 已完成Execution再扫原批次 | 普通入口存在完结拒绝；确认中心没有相同门禁；不存在Base式Execution统一身份 | 返回已绑定终态且禁止继续领料/报工；若原材可复用，要显式选择新的生产身份，不复活旧Execution |
| 7 投产100按BOM算料 | 已有确认中心：MO_D总需求×100/工单计划产出；ERP工单用料是实际来源 | 优先复用该服务端公式与6位HALF_UP，保存单位/计算依据；不是把100直接写每种材料数量 |
| 8 两用户同码选择不同单 | 两候选可见；先查历史再写入不证明并发互斥；未做真实并发验证 | 同一“生产身份”原子绑定只能胜出一个Execution，冲突者复查并提示。若只是可复用原材码，两个不同生产身份可各领各料，并由ERP库存成功确认，不混成一个Execution |

**可直接沿用的业务内容：**材料品号/批次扫码；断阶映射；MO_D材料适用性；Factory/Owner厂别匹配；WORK_CENTER及仓库映射；多候选选择；预计产出驱动的全部材料计算；ERP材料单位；多张成功领料单归同生产活动；报工与领料事实分开；既有ERP核对/UNKNOWN保护。

**需要明确改变的内容：**把两条旧入口收敛为本轮指定顺序；库存身份完整化，保留多仓多库位；候选的中心/状态/组织统一准入；原材批次与生产身份分清；控制臂等多材料允许独立材料批次；纯补料与追加投产明确区分；终态Execution统一拒绝追加；数量权威从计划targetQty转为可信贡献；首扫绑定与请求幂等分别保障。以上均为后续建议，本轮没有修改。

**不能无条件迁移的历史限定：**5100/5103/5105单别、按月日期窗口、19113001特殊流程卡处理、指定“通用工艺”的旧默认策略、全材料同批号、raw lot跨工单独占。它们是Source具体规则，不应未经业务确认成为通用Base固定逻辑。

### H. 必须由用户决定的三个问题

1. **原材料复用和生产身份：同原材品号+批次能否用于多个工单/多个生产活动？** 若能，推荐原码继续查库存，以流程卡/明确生产批次身份绑定唯一Execution；原码复扫可出现多个关联，由用户选择。若现场坚持“一码永久唯一Execution”，就必须接受这码承担生产批次身份以及原材料不可跨生产身份复用的限制。两者不能同时承诺。
2. **生产中心可选范围：同厂同材料适用的两个中心工单是否都能选？** 推荐显式确定“当前岗位/产线中心允许集合+Owner厂别+允许仓库”，没有限定时保留全部合法候选由用户选；不得默默假设用户部门就是ERP工作中心。部署真实PLANT/中心/仓库映射及状态字典仍需在后续只读运行核实。
3. **纯补料是否增加可报产出？** 推荐明确“追加投产q→完整材料计算→成功贡献q；纯补料→材料数量增加、产出贡献0”。若要沿用旧材料瓶颈法，即补足短缺材料自动扩大可报产出，则应采用同一材料折算权威，不能同时再按outputQty累加而重复增加额度。

### 本轮证据与交付边界

- 已完成：普通小程序按钮/API→Controller→Service→ERP查询SQL/OAPI参数→扣料/领料单→报工材料归集；Web预计产出入口→服务端计算/审核；Base当前身份、领料和数量入口的静态对照；八场景逐项判定。
- 未执行：ERP库存/工单实时查询、部署配置及字典读取、数据库样本/并发、真实扫码设备/UI、领料/审核/撤销ERP命令。没有用ERP成功假设替代这些运行证据。
- 仅在Base本报告追加本节，未修改业务代码、前端、配置或Schema；Source始终只读，没有启停服务或提交推送。
- 报告整理前后对五仓库已跟踪/未忽略文件做SHA-256复核（排除本报告及不在范围的ktg-wheel）：HEAD均未改变；仅Source已有并行文档 `docs/architecture/transaction-ownership-audit.md` 内容发生外部变化，其余已取样文件无变化。该并行文档未被本Agent写入，本轮没有源码冲突。
- 结论：已能确定复用的业务规则及两条入口差异；唯一Execution绑定和纯补料贡献须先按H确定，不能直接把raw item+lot当永久执行身份后进入实施。
