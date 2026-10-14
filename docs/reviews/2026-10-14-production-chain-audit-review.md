# Report B：Production Chain Audit

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
