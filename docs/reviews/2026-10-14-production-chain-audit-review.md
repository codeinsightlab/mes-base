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
