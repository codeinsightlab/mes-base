# Base MES — 生产核心性能与 SQL 调用链专项审查

日期：2026-10-19。性质：只读代码审查、既有 Trace 复核、新的 DEV MySQL 只读观测；没有实施业务优化。

## 1. 执行摘要

正式报工最值得分批处理的三处是：**单用户资格校验取消无消费者的 COUNT；在已持有 WorkOrder→Execution 锁的事务内显式传递权威身份，避免 start/requireActiveExecution 重走锁链；复用本次锁定路径的节点快照，避免数量与 start 重复装载。** 第一项为 A 类局部冗余；后两项为 B 类候选，必须先补真实并发证明，不能直接删除公共入口的锁。

读接口另有两个明确候选：流水页为验证节点而完整计算数量/质量（C）；闭环查询沿数量投影与放行依据两次加载同一组质量事实（B/C）。数量查询本身没有逐节点 JDBC N+1，质量投影内部却有 declarations×events 的 Java 重扫。

最大正确性风险是把“重复主键/同值 UPDATE”整体当作冗余，绕开首次父子锁、锁后身份核对、UUID 双查、投产确认及当前数量/质量校验；其次是把 READ_COMMITTED 下普通 Operation 读取或独立 Master 窗口变成跨请求缓存。当前代码已使用显式锁定读，不能继续把旧 UPDATE 缺点当作未修复的当前问题。

最大性能瓶颈按场景区分：RUNNING 的大量串行数据库往返与数据库提交等待；READY 另有共享 Routing 排他锁的跨 Execution 串行化风险。历史 77ms COMMIT 有新日志 IO 指标佐证，但**尚不能归因到某个请求的 fsync、宿主磁盘或挂载类型**。历史 READY 最大 5.77 秒也**尚无足够等待链证据证明全部由 Routing 造成**。

本轮重新核对了目标绑定、正式 SQL 计划、持久化参数及 Performance Schema，重新运行原 901 闭环 17 项审计，全部通过。没有新增 HTTP 写负载，没有新增 DEV 测试事实，没有触碰 PROD，没有启动、停止或重启服务。

## 2. 版本、环境和证据等级

### 2.1 工作区存在并行提交，不能只比较 HEAD

开始时 HEAD=`9b709348fc4fbae58743f16e9fb9bf4536732e75`、main，已有 40 个 tracked 修改和 5 个未跟踪文件，涉及锁定读 Mapper/Service、生成器、测试、application.yml 和历史审查文档。本轮过程中其他工作将这些内容提交为 `b107f7f`；本轮没有执行 git add/commit/push。中途详细快照因此显示既有 dirty=0，不能将它误称为“开始时干净”。未回滚任何并行改动。

在并行提交后冻结了 1,164 个生产 Java/XML/YAML 文件的逐文件 SHA256，结束核验见工作区证据。本轮新增内容仅为本报告、只读诊断 Python 和证据。没有构建或更换业务产物；本地 `mes-admin/target` 已不存在，不能宣称重新核验了其 JAR。

|证据版本|产物/口径|本轮使用方式|
|---|---|---|
|H：原 DEV 基线|JAR `0a54a3080bc1f0e8ba2efbb0d0477ee56b42f78b86d327af0bce6fbab3b3f5d9`；READY84/RUNNING69/重放8|历史单请求 Trace、历史负载，明确非本轮新测|
|B：现场已有锁定读诊断|artifact 记载 JAR `f7025c0e99013aa0ccbc8167258299f5beb764c7df483d150c6f61639b3a62b8`；READY68/RUNNING57/重放8|既有另一轮测试证据；本轮复核 Mapper/协议/服务器一致性，不冒充自行复测|
|S：当前源码|锁链改为 `selectByPrimaryKeyForUpdate`；工作区后续并行提交 b107f7f|本轮静态审查对象|
|N：本轮新观测|2026-10-19 14:45、14:47 CST 两组只读采样、EXPLAIN ANALYZE、901审计|本轮真实 DB 证据；无 HTTP P50/P95 新结果|

现场已有 PID69473 的 `IsolatedLauncher`，18082/Redis17479，指向另一轮 `.local/for-update-20261019/B-load`。未调用其写 API。其解包 runtime 的 `BOOT-INF/lib/mes-business-0.1.0-SNAPSHOT.jar` 中55份现存业务 Mapper XML 与当前资源逐文件一致；这只能核对 XML，不能替代已丢失完整应用 JAR 的重新哈希或全部 Java 字节码一致性证明。B 的完整产物信息来自该轮 artifact，完整重建可重复性尚未由本轮验证。

### 2.2 数据库核验与干扰

本轮 mysql 原生连接通过私有 option 文件取得凭据，未输出密码。实查中心绑定：Factory `TEST_DEV_PERF_20261019`、ENABLED、LOCAL → datasource **9**、ENABLED → **192.168.18.25:3306/mes_base_dev_perf_test**。服务器 UUID=`03dbc952-7403-11ef-802c-0242ac130002`，MySQL8.2.0，hostname=`10dab82856fb`。业务正式历史表 `flyway_business_schema_history` 最新 V39 success=1；DEV Master V40属于历史报告已完成项，本轮不执行迁移。

第一次探查错误地使用默认 `flyway_schema_history` 查询业务库，得到表不存在；随后查表并纠正为正式业务历史表，保留首次错误证据，没有创建替代表。成功诊断见 confirmed 子目录。

历史导入时全库报工 90,104 条，原901部分90,100条。现场数量随另一轮负载增长：前置92,827，第一组92,984，第二组93,616；查询间不是同一个快照。这不是数据污染结论，也不把增长归因本轮。计划、全局 IO 差分受到并行测试影响，不能作为独占基准。

### 2.3 证据入口

- [新只读观测及实际计划](evidence/2026-10-19-production-core-performance-review/confirmed/mysql-readonly.json)、[执行语句清单](evidence/2026-10-19-production-core-performance-review/confirmed/queries.json)。
- [首次观测（含表名错误）](evidence/2026-10-19-production-core-performance-review/mysql-readonly.json)。
- [本轮原901复核](evidence/2026-10-19-production-core-performance-review/original901-audit.json)。
- [8类既有Trace调用栈/指纹索引](evidence/2026-10-19-production-core-performance-review/call-chain-evidence.json)。
- [生产源码逐文件冻结](evidence/2026-10-19-production-core-performance-review/workspace-start.json)、[结束保护核验](evidence/2026-10-19-production-core-performance-review/workspace-final.json)。
- 历史基线：[DEV报告最新§13](2026-10-19-dev-production-chain-performance.md)、[报工审查最新成功采样及后续说明](2026-10-18-production-report-sql-audit.md)、[十万事实报告](2026-10-18-local-production-chain-performance.md)。这些文档早期阶段性 NO-GO 不作为当前缺陷。

## 3. HTTP 入口审查矩阵

下表 SQL 均为注明版本的**既有动态实测**，不是静态预算。Mapper 不含驱动探针、事务控制；完整协议数另列。N 未重新执行 HTTP 测量，故无当前 HTTP 实测列。H耗时为单成功请求，不是P50/P95。

|HTTP入口|核心Service|Mapper SQL数 / 完整协议数|主要耗时证据|锁/事务及Owner|优化优先级|
|---|---|---:|---|---|---|
|POST `/api/production/reporting` READY|ProductionReportSubmitService→ExecutionPathService|H 67/84；B 59/68|H HTTP549.315ms，JDBC423.923ms，服务器105.587ms|Business RC写事务；WO→E→Routing；资格独立Master窗口|P1身份/路径复用；Routing先补证|
|同入口 RUNNING|同上|H55/69；B49/57|H474.097/359.636/100.721ms；B单样本403.382ms非分位数|Business RC；WO→E，重复重入；无READY Routing锁|P1|
|同入口 原UUID重放|submit→replay|H3/8；B3/8|H43.996ms|先UUID命中即返回；不重新作新业务准入|D保留|
|GET `/api/production/report-quantities/executions/{id}`|ProductionQuantityService|H14/19|H122.834ms，JDBC77.227ms|Business只读；无排他锁；入口Master认证授权|P1路径读取|
|GET `/api/production/reports?executionId=…&routingNodeId=…`|ProductionReportService.page|H16/23|H154.989ms，JDBC92.414ms|Business只读；先完整数量校验；用户名批量Master读取|P1专用成员校验|
|GET `/api/production/executions/{id}/closure`|ProductionCompletionService.summary|H21/28|H191.381ms，JDBC130.900ms|Business RC只读；入口Master授权|P1/B质量事实共享|
|GET `/api/production/mes-production-execution/{id}`|生成Execution查询Service|H3/10|H58.687ms|Business只读/既有缓存、租约guard|P2，未发现与闭环相同重算|
|GET `/api/production/reporting/context`|submitService.context→identity/quantity/path/eligibility|未测|静态发现quantity后再次paths.view|Business RC只读；资格Master窗口|P2|
|GET `/api/production/report-quantities/workorders/{id}`|ProductionQuantityService.getWorkorderQuantities|未测|单批workorderQuantities+分页COUNT+批量routeLabels；SQL内相关子查询|Business只读|P2，未证明应用层N+1|
|GET `/api/production/report-operators`|OperationUserDirectory.page|未测|全菜单+资格分页；公共分页total有消费者|Master具名只读事务|P2，不能全局关COUNT|
|POST `/api/production/input`、领料/撤销、质量放行、交接/接收/完工|见§10|本轮未测|不以源码调用点猜成功SQL数|Business写事务/分阶段外部协调|关联复用，保持原业务边界|

H的Master：正式写5个Mapper（入口身份/授权2＋菜单/资格COUNT/SELECT3）；查询基础入口通常2个。Business活动事务期间Master窗口独立物理连接，不能按线程transactionActive=true归为同一物理事务。源码依据：`OperationUserDirectory:63–83`、`PlatformPersistence.readOnly:49`。

## 4. 实际调用图、SQL与事务

### 4.1 正式报工

```text
HTTP token/account + scoped permissions [Master，入口准入]
ProductionReportingController.submit
└─ ProductionReportSubmitService.submit [Business，READ_COMMITTED，REQUIRED]
   ├─ UUID SELECT（锁前）
   ├─ ProductionChainLocks.execution
   │  ├─ Execution普通读：发现workorderId
   │  ├─ WorkOrder FOR UPDATE
   │  └─ Execution FOR UPDATE＋父身份比较
   ├─ UUID SELECT（锁后）
   ├─ BusinessSourceRules.execution→order/issue/items/receipts/warehouses
   ├─ ProductionQuantityService.validateReportChange→load
   │  ├─ Execution + paths.view→Execution/routeLabel/nodes/opLabels/path/selected/edges
   │  ├─ inputBalance、selected、nodeTotals、WorkOrder
   │  └─ ProductionDefectQuantityQuery→QualityTreatment.nodeTotals
   │     └─ executionEvents + declarations → 内存投影
   ├─ ReportOperatorEligibilityService.requireEligible
   │  ├─ OperatorQualificationRules.node→Execution/Node/Operation
   │  ├─ requireCapability→Operation→OperationUserDirectory.requireValid
   │  │  └─ 独立Master只读：全菜单/分页COUNT/用户SELECT
   │  └─ capability COUNT、assignment COUNT（有指派时再查本人COUNT）
   ├─ optional workstation SELECT
   ├─ ExecutionPathService.start [加入外层写事务]
   │  ├─ locks.execution→requireActiveExecution→再locks.execution＋WO读
   │  ├─ READY: checkReadyReferences→WO锁/来源/物料/Routing锁/适用产品
   │  ├─ requireSelected→path/selected/nodes/edges
   │  ├─ READY: confirmed issue count/rows/extras、Execution UPDATE＋回读
   │  └─ RUNNING:校验后返回
   └─ report INSERT＋回读→COMMIT→提交后缓存淘汰→HTTP序列化
```

代码定位：[submit](../../mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReportSubmitService.java)、[locks](../../mes-business/src/main/java/dev/mesbase/business/production/service/ProductionChainLocks.java)、[数量权威](../../mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java)、[path](../../mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathService.java)。事务内readOnly方法的REQUIRED加入外层写事务，不产生额外业务提交，也不会把外层改为只读。Master窗口另论。

### 4.2 数量、流水、闭环的调用图

```text
QUANTITY → ProductionQuantityService.getExecutionQuantities → load → project
LEDGER → ProductionReportService.page → getNodeQuantity → 同一load/project
       → generated report COUNT + SELECT OFFSET/LIMIT → 批量userLabels [Master窗口]
CLOSURE → ProductionCompletionService.summary
        ├─ Execution SELECT
        ├─ QualityReleaseService.state
        │  ├─ quantity.load/project（质量events/declarations一组）
        │  ├─ treatments.releaseBasis（再次events/declarations一组）
        │  └─ latest release LIMIT1（无COUNT）
        ├─ receipt.netQuantity、acceptance.netExecution
        ├─ completion LIMIT2（无COUNT）
        └─ RUNNING且前置满足时才查unresolved issue COUNT
```

闭环H样本是已完成Execution，所以末尾未决领料COUNT短路未执行；不能将21Mapper硬编码为所有状态预算。闭环没有调用`ProductionIdentityService`，实际身份由Execution→quantity/path取得，不应凭名称给它追加扫码解析成本。交接/接收与库存引用在该GET中用net聚合，**没有逐条加载wm_stock_action**；当前DTO也不承诺展开完整库存动作证据链。

### 4.3 指纹与SQL分类

|Mapper/SQL|H指纹示例|分类/Owner|约束|
|---|---|---|---|
|Execution.selectByPrimaryKey|6b45cf1cada81b8c|非锁定读/Business|发现父身份或加载状态，需分次分析|
|Workorder.selectByPrimaryKey|4a41f7d4ec61096c|非锁定读/Business|来源、可写状态等不同消费者|
|Workorder同值UPDATE→当前FOR UPDATE|旧8b3ee13de9e58a57；B71ac059ca0e92830|排他锁/Business|不是乐观锁，当前已替换|
|Execution同值UPDATE→当前FOR UPDATE|旧b919a531c1b50f71；B19b7b747b68a4484|排他锁/Business|保持WO→E顺序、锁后当前读|
|selected.selectByExample|991adf456ef4e3b6|路径读/Business|按sequence_no,id；确切路径版本|
|ProductionQuantityMapper.nodeTotals|53609daed9d58422|事实聚合/Business|一次取全路径，不是每节点发SQL|
|inputBalance|39c297f88dd66c03|投产事实聚合/Business|CONFIRMED/SUCCEEDED/来源/外部生效条件不能删除|
|report.selectByRequestId|3bdacbe7e1e6246c|幂等读/Business|锁前锁后双查+UUID唯一索引|
|SysMenuMapper.selectMenuList|42901ab277869e8d|资格metadata/Master|完整URL分类，按钮继承父菜单|
|OperationUserDirectory.select_COUNT|45dbec23b5ea8e01|分页COUNT/Master|单人requireValid未消费total|

全部SQL、Mapper、执行顺序、Owner、实际autoCommit、指纹和调用栈由§2.3索引可追溯；禁止把Mapper与Native/JDBC嵌套耗时相加。

## 5. Execution / WorkOrder 逐次读取复核

以下编号为H的Mapper序号，便于与原Trace一一对齐。**均发生在同一个报工Business写事务内。** 初次锁取得后至报工前没有释放；下表中“可复用”均指未来显式持锁视图的B类候选，不是删除当前独立入口校验的授权。

### 5.1 Execution：READY10次 / RUNNING9次

|H序号 READY（RUNNING）|调用者/目的|锁与写入位置|判断|
|---|---|---|---|
|4（4）|locks.execution首次普通读，发现workorderId|WO/E锁前|D保留父身份发现，禁止先锁E反转顺序|
|8（8）|首次锁后E读取，核对父身份/状态|WO/E已锁，无业务写|D当前已合并到FOR UPDATE，不能再从B减一次|
|15（15）|quantity.load读取E数量身份|已锁，尚无状态写|B传递已锁E可省1|
|16（16）|paths.view再次读取同一E|同上|B让view接收服务端E可省1；独立GET仍查询|
|29（29）|qualification.node校验E与RoutingNode关系|同上|B传递已锁E可省1，节点归属校验保留|
|39（39）|start再次locks.execution的父发现|原锁仍持有，无E写|B正式submit可复用；独立start仍须首次锁链|
|43（43）|start再次锁后E读|同上|B当前为FOR UPDATE重入；可与前一组一起设计|
|44（44）|requireActiveExecution内再次父发现|同上|B没有新鲜度增益所需的解锁窗口，但公共方法不能裸删锁|
|48（48）|requireActiveExecution再一次锁后E读|同上|B同上|
|65（无）|READY状态UPDATE后的E回读|READY→RUNNING写后|D保留或等价返回规范化持久值；当前注释要求DATETIME精度一致|

当前B仍加载10/9次E对象，但拆为普通SELECT7/6＋FOR UPDATE3/3；**不是当前仍发10/9条普通主键SELECT**。状态UPDATE之后的回读不应拿READY之前的对象代替。

### 5.2 WorkOrder：READY8次 / RUNNING6次

|H序号 READY（RUNNING）|调用者/目的|锁与写入位置|判断|
|---|---|---|---|
|6（6）|首次locks.order，取得当前WO|首次WO锁后|D当前并入FOR UPDATE；保留锁与当前状态|
|10（10）|sources.order：来源、当前模式、必要ERP映射|WO已锁，无WO写|B复用WO可以，但来源/模式/extra/binding校验不能省|
|28（28）|quantity.load：正常报工writable边界|同上|B可使用相同锁定WO|
|41（41）|start锁链中的WO读取|原WO锁仍持有|B当前并入重入FOR UPDATE|
|46（46）|requireActiveExecution内锁链WO读取|同上|B可消除整组重入，但独立命令保留锁|
|49（49）|requireActiveExecution最后普通读WO，拒绝已作废|同上|B使用已锁WO执行同一requireActive判断，可省1|
|51（无）|READY.validateReferences再次锁WO|同上|B已有锁视图可复用；RELEASED/产品匹配仍必须验证|
|52（无）|READY.validateReferences→sources.order又读WO|同上|B可复用锁定WO；不跳过EXTERNAL extra校验|

当前B为普通WO SELECT4/3＋FOR UPDATE4/3。H中锁后普通读与同值UPDATE共8/6次SQL的消除已经属于其他工作完成的变化：READY Mapper67→59、RUNNING55→49；包括驱动探针减少后的协议84→68、69→57。不要在下一轮预算重复计算此收益，也不将B单样本较快当吞吐提升证明。

### 5.3 能安全减少多少

**无需再解释业务的A类身份SELECT删除数为0；有明确持锁前提的B类普通读最小首批4处**：quantity.load的E、其paths.view的E、qualification.node的E、requireActiveExecution末尾WO。它们之间没有相关状态写；仍需通过全部共用写入口与回滚/撤权/变更反例。

更大的B候选是正式submit→start中的两次重入`locks.execution`（当前每次3条SQL），加末尾WO读，至多涉及7条当前Mapper往返。它与上面4处中的末尾WO重叠，**不可相加为11**。进一步WO来源/数量读取复用与READY参考校验也有机会，但本轮不给未经实现验证的总减数。最初父身份发现、首次WO/E当前锁定读、写后规范化回读属于D。

所需设计是用例内部的显式锁定上下文（E、WO、确切路径及事务边界），不是ThreadLocal“锁过了”标志或跨请求事实cache。任何REQUIRES_NEW、外部往返后新事务、Scope变化、状态写后必须拒绝旧视图或刷新。

## 6. 其他重复SQL与发现分类

|SQL/Mapper|入口|H次数|重复原因|可否优化|必要约束|
|---|---|---:|---|---|---|
|资格select_COUNT|正式submit/目录内部requireValid|1|复用需要total的分页query，但requireValid只看items|A：有界单用户调用禁COUNT，最多省1|保留公共目录分页total；PageHelper正确清理|
|Operation.selectByPrimaryKey|正式submit|2|node()与requireCapability→capability()重复operation()|B：合并资格校验候选|普通读未锁Operation；启停可并发，需明确最后资格检查时点，不因相同hash判A|
|selected.selectByExample|正式submit|3|paths.view取ID；quantity另取sequence；start.requireSelected再取|B：单次锁定节点模型复用|LOCKED、routingId、顺序连续、分支及路径校验保留|
|RoutingNode/Edge批量读|正式submit|各2|quantity路径与start.requireSelected|B|已发布图不可改与实际路径锁定证明；不跨事务|
|events + latest declarations|正式submit|各1|数量单一权威必需|D，未发现该入口重复质量load|不能按闭环的重复结论误删报工质量读取|
|events + latest declarations|CLOSURE|各2|quantity.nodeTotals与releaseBasis分别装载|B/C：共享一次本次质量事实集，最多省2 SQL|保留同一数量算法、放行basisHash和事件身份；RC多次读取可见变化，需定义一致视图|
|Execution SELECT|CLOSURE|3|summary、quantity、paths.view|B/C|read接口无锁，不套用写事务的“已锁安全”论证|
|完整quantity.load|LEDGER|1（包含12个Business Mapper）|getNodeQuantity用于验证成员却计算整条数量质量|C：专用存在性/路径成员查询|保持不存在E、错routing、未选节点、历史可读语义；不能放宽资源边界|
|全菜单读391行|requireValid|1|MenuScopePolicy构造URL/父菜单范围|C：有界资格/菜单投影|不能按权限字符串前缀判平台；菜单祖先与按钮继承规则需等价|
|receipt.summary和receive中的quantity|生产交接|静态各存在2次load路径，动态未测|直接quantity后再release.state→quantity|B，关联候选|处于同一E锁下且无相关写入才共享；累计交接/放行检查保留|
|inputBalance/nodeTotals|QUANTITY|各1|投产与报工事实聚合|D保留|禁止另建公式或缓存余额|

“A可最小删除”仅表示该SQL的结果未被调用者使用，不表示本轮修改；其他B/C均需后续实现与反例验证。

## 7. 锁顺序、Routing热点与竞态

### 7.1 当前锁顺序及生命周期

|入口|当前锁序|保护的不变量|
|---|---|---|
|正式报工/质量/交接/接收/完工|发现E父身份→WO→E；READY再Routing|同工单/执行数量、状态、作废、路径、事实追加互斥|
|READY.start|WO→E→重复WO/E→READY参考WO→Routing|PUBLISHED+publishedAt、确切routingId/产品适用、RELEASED；随后确认投产和状态切换|
|领料关联更新|WO IDs排序→E IDs排序→Issue IDs排序|等待中父关联改变拒绝，防止明细转移后拿错旧锁|
|正式投产/投产撤销/退料|WO→E/Issue→Inventory|投入资格/数量与库存事实同事务；外部阶段单独协调|
|WarehouseAcceptance→LocalStock.receiveProduct|WO→E→重复WO/E→Inventory|交接、接收额度、库存动作与余额一致|
|StockDocument.post|有关联WO先锁WO；单据头→相关E→Inventory按stockId/id排序|聚合单据额度、库存扣增与动作唯一性|
|Routing.publish|Routing revision条件写→Material IDs→Operation IDs|发布图与引用快照、启用状态；发布后图冻结|
|WorkOrder.binding|Material→Routing|产品启用与已发布适用工艺|

所有锁都依赖外层具名写事务保留至commit/rollback。`ProductionChainLocks`自身未声明事务；独立调用其方法不构成完整持锁协议。普通非锁SELECT不会自行阻塞写入，也不延长其他事务的排他保护。

start的Routing锁取得较晚，在quantity/资格Master窗口之后；一旦取得，后续仍有路径、领料/extra查询、状态写、报工写、回读和COMMIT。当前同一Routing的不同WO/E确实要争同一个排他记录锁；把UPDATE换FOR UPDATE缩短SQL表达，**没有改变X锁兼容性或释放时点**。

### 7.2 为什么冻结版本后还读主Routing

Execution持有的是具体版本记录routing_id，发布后图/产品关系冻结，不是任意“当前最新版”；newVersion创建新行，不能改派旧执行。可是该行的PUBLISHED/DISABLED状态仍可由availability修改。`ProductionExecutionRules.validateReferences:167–183`对newBinding=true验证PUBLISHED和publishedAt，READY.start由此决定能否首次进入RUNNING；还验证产品适用。直接删除该锁会出现“启动读到PUBLISHED，停用提交，启动随后仍提交”的决策窗口。

RUNNING.start跳过checkReadyReferences，所以历史生产不会每次为此锁Routing；仍会requireSelected读取已发布图与确切路径。图/产品的不可变字段读取可以在本事务复用；状态是否允许新开工的当前读不能用旧路径的LOCKED标志替代。普通路径view中的routeLabel只是展示读取，不承担READY发布状态准入。

### 7.3 更小安全范围：候选而非结论

可以优先研究把READY对不可变字段与“允许新开工”状态的读取分离，使用**共享锁定读**协调多个启动者和availability/publish的排他修改，而不是让所有启动者互斥。必须核对同事务后续是否升级Routing锁、所有编辑/同步/发布入口是否统一互斥以及批量锁序；不能直接改成普通SELECT，也不能提前提交释放WO/E锁。单纯让Routing锁晚取得，仍要保留至提交，并不会消除提交等待期间的串行队列。

这是B类高风险候选。没有完成真实“多E共享Routing同时开工＋停用/发布竞争”反例前，**不存在本轮已证明可直接落地的更小锁范围**。

### 7.4 锁顺序反转与死锁边界

报工→start中的重复WO/E是同一事务重入，不能据调用栈显示E后又WO就断言新增死锁：本事务已持有同一WO锁。退料持库存后再次取原E也要检查此前已通过locks.issues取得了对应WO/E，不可只按文本顺序判反转。

相邻工艺入口有真实静态反向顺序：WorkOrder.binding先Material再Routing；Routing.publish先锁Routing再Material。若发布事务锁DRAFT Routing等待产品锁，另一工单请求已锁产品并等待该Routing，可能成环；工单最终会因DRAFT非法而失败，但它是在取得Routing锁后才校验，**非法/迟到请求仍可参与等待环**。这是源码可达的死锁风险，不是本轮捕获到的MySQL死锁。不要因普通生产只引用PUBLISHED就宣称全域不存在锁序反转；也不因此在本轮扩大修复范围。

### 7.5 真实MySQL等待链分析

本轮执行`performance_schema.data_lock_waits`联合请求锁/阻塞锁的`data_locks`，收集ENGINE事务ID、双方线程、schema/table/index、LOCK_MODE/STATUS/DATA。两组各30次（查询间0.2秒，另含连接/查询开销）均为空；全局row_lock_waits/time在第二组窗口差分为0。查询模板、时间戳及60份结果已保存。**这证明采样点没有观察到DEV等待边，不证明READY预热无等待。**

Performance Schema statement/transaction current/history启用，waits current/history/history_long及statement history_long未启用；没有改consumer、清空digest或强开采集。data_lock_waits提供当前边，不是历史回放；空结果无法还原上午的5.77秒。

H历史在含READY的相邻阶段观察到3/7/15次新增全局row wait及921/6409/26249ms累计等待，只能与共享Routing串行机制相互支持，不能确定每条等待的对象。现有另一轮脚本中的“holder rollback”即使完成也锁的是WO/E，不能当作Routing等待链。该脚本源码不是本轮运行结果。

本轮不新造争用：另一轮正在同一DEV Factory产生负载，现有Routing1–3均被原901使用，Routing4也属于既有测试资源，未建立本轮独立工艺、两份READY对象和独占采样窗口。只复用Factory和用户身份不足以证明共享引用隔离。后续验证须独立TEST标签/账号/Stable UUID/资源清单，先证明无原901引用，再正式HTTP准备数据；捕获双方线程对应请求、Routing PRIMARY的相同LOCK_DATA，核对等待开始/结束与commit，并保留不同Routing对照。缺此证据，Q2的历史因果仍标E。

## 8. 数量、质量、闭环及流水专项

### 8.1 数量权威与QA-03

`ProductionQuantityService.load`单次读取inputBalance、报工nodeTotals、质量nodeTotals，再按实际有序路径统一project/calculate。报工调用一次load，不是每节点查报工或质量。路径节点却在view取得ID后又为sequence检查加载，属于有界重复；routing/op名称使用批量目录，没有逐节点标签查询。

`QualityTreatmentService.executionEvents:98–107`取至5001条，>5000失败；`declarations:109–115`取至1001个latest declaration，>1000失败，**先检查数量后过滤VOID**。因此假如已有超限历史，数量GET、流水前置数量、放行和闭环均可失败，不能简单忽略超出部分或只查最近事件。VOID历史也占latest declaration上限；revision历史行数不是这里的1000计数口径。

可达性必须区分：正常申报在INSERT后执行`quantities.validateDefectChange`（ProductionDefectService:188–194），处置在append后同样执行该校验（QualityTreatmentService:235–237），异常由外层rollbackFor事务回滚。因此已有1000/5000时新增越界正常应失败并回滚，而不是成功提交1001/5001后自然令报工瘫痪。导入、历史版本、非正式写入留下超限事实仍是可达输入边界；正常流程则存在容量达到上限后不能再追加质量事实的问题。当前DEV max_declarations=0、max_events=0，不构成真实大质量数据验证。

没有找到本轮可确认通过的1000/1001、5000/5001端到端边界结果；不能从小质量fixture测试推出大历史容量安全。后续独立建立边界fixture/隔离MySQL验证，保留历史全部事实和质量公式，本轮不重写领域。

质量nodeTotals与releaseBasis虽然每次仅批量2次SQL，但每个declaration的summarize都扫描整组events，接近O(D×T)，在上限附近有Java成本风险。可考虑按(declarationId,revision)一次分组再复用原summarize，不改业务公式；本轮没有重质量样本，不给耗时收益预测。

### 8.2 闭环

已证明同一GET内质量events与latest declarations各查2次，原因是数量投影与放行basis分开装载；报工累计nodeTotals、inputBalance各1次。没有逐节点SQL N+1；有重复质量快照及3次Execution身份读取。`QualityReleaseService.state`只构造一次数量投影，不能把它说成在closure中“数量load两次”。真正重复完整quantity发生在ProductionReceiptService.summary/receive的直接load后又调用release.state。

建议通过一次质量事实装载同时产出nodeTotals与releaseBasis，再由现有ProductionQuantityService计算数量；保持basisHash包含声明版本/事件身份，不能仅缓存净不良数代替审计依据。对于无锁RC只读summary，新快照与原多次可能不一致读取之间的语义差异要明确评估；写入口共享时必须位于已锁E且无相关事实写入区间。

闭环netReceipt/netAcceptance为聚合查询，completion以LIMIT2检查唯一性，latestRelease LIMIT1禁COUNT；这些不是分页冗余。输出DTO没有逐条展开报工、接收或库存动作宽表，本轮没有证据把DTO装配列为最大瓶颈。

### 8.3 操作者资格

requireValid只看items非空，total无消费者，现有`PageHelper.offsetPage(offset,limit)`默认COUNT确实多一次；validUsers同样不消费total。已有select按userId/ids有界并使用EXISTS关联启用账号、有效role、当前Factory或PLATFORM业务授权，最小方案首先仅对内部调用关闭COUNT，再考虑单用户专用布尔查询。公共page继续返回total。

全菜单391行、业务菜单163个是H事实，不宣称当前菜单规模未变。MenuScopePolicy依赖完整URL分类/祖先和按钮继承，不能只按permission前缀筛选；缩小菜单结果需要专门投影与矩阵证明，COUNT优化不必与它捆绑。

正式HTTP授权针对调用者操作权限；资格校验针对操作者启用状态/角色的业务授权及Operation capability/Execution assignment，不是简单重复授权。业务事务中必须保留独立Master只读窗口；普通目录查询用注入Mapper和master只读事务。当前窗口autocommit的多条读取不是中心业务跨库原子快照，不能承诺角色撤销会中止已准入的业务事务。

撤权/禁用按项目准入规则在新请求及新的资格读取生效；已取得授权不追溯撤销。优化必须继续每次内部调用按当前Factory查询、失败关闭，无长期cache，不信任客户端user/role/factory，不用旧Token权限替代目录判定。严格“资格校验后撤权必能阻止commit”当前不承诺，也不在本轮暗中新增这种语义。

### 8.4 流水与实际扫描行数

正式入口强制executionId和routingNodeId，**不存在当前Controller暴露的无条件跨Execution全历史分页**。page先getNodeQuantity完整投影，再对该节点COUNT与分页；所以质量历史异常会连带阻断历史流水查询，专用成员校验值得优先实现，但必须保持资源存在/路径成员与历史读取契约。

SQL按`create_time desc,id desc`稳定排序；现有`idx_report_execution_node(execution_id,routing_node_id)`定位，排序索引没有同时覆盖两过滤列与完整排序。不能据此立即认定缺索引：

|N真实EXPLAIN ANALYZE样本|实际访问与返回|数据库侧时间/边界|
|---|---|---|
|Execution901/节点1 COUNT|覆盖索引20行，聚合1行|首次约0.038ms；预热单次|
|同节点第一页/尾页|索引20行，小范围sort，返回20|首次约0.215/0.187ms；该节点只有20条，尾页OFFSET0，不是深分页|
|当前最多报工节点Execution907/节点1|258行，OFFSET238，排序258后返回20|第二组约1.93ms；并行负载下样本|
|901的五节点nodeTotals|5条路径，报工索引20行×5loops，总100行→5组|首次约11.2ms、第二组约0.548ms，实际有临时聚合；不是5次JDBC往返|
|诊断性无过滤全局OFFSET90000|首次全表扫描93009行，排序取90020，返回20|首次约289ms、第二组约1180ms；**不是正式LEDGER API测量**|

原始计划含全部actual rows/loops/time，confirmed再取一组，值可能因并行写入、缓存和调度变化。mysql命令clientWall包含进程/建连/网络，不是HTTP耗时或纯RTT。正式单Execution页的好性能不能推导全局深分页能力；当前无证据优先新增全局索引，也不在本轮执行DDL。

## 9. DEV COMMIT与部署成本归因

N实查：`innodb_flush_log_at_trx_commit=1`、`sync_binlog=1`、`log_bin=ON`、`innodb_flush_method=fsync`，buffer pool128MiB，io_capacity200，datadir=/var/lib/mysql/；均未更改。配置为持久化成本提供可能路径，不单独证明77ms来自fsync。

P_S新证据：binlog和innodb_log_file的文件IO instrument启用且TIMED=YES；逐wait history未启用。第二组约12秒观测的全局差分：

|指标|差分|解释|
|---|---:|---|
|statement/sql/commit|293次，累计67.589秒|并发累计，平均约230.68ms；另一轮负载+其他连接，非本轮HTTP结果|
|binlog文件全部IO|194次，累计7.304秒|其中MISC95次/7.254秒，约76.35ms/次|
|InnoDB日志文件全部IO|1101次，累计10.542秒|其中MISC180次/10.363秒，约57.57ms/次|
|Innodb_os_log_fsyncs|+183|发生实际持久化同步，但不是按某个请求归属|
|Innodb_log_waits|+0|没有该计数新增，不证明所有提交等待为0|
|全局row_lock_waits/time|+0/+0|该观测窗无新增；不能否认历史READY等待|

MISC包含同步及其他文件操作，不能把SUM_TIMER_MISC直接改名fsync时间；日志线程操作与提交线程可能不同，一个group commit也可能对应多个请求。累计IO、commit总和都不是窗口墙钟，不能相互加总。新证据增强“日志持久化/提交链有显著成本”的候选判断，仍缺逐请求事件关联、OS块设备await/队列、宿主卷/overlay/远程盘挂载和磁盘类型，**没有确认存储根因**。

没有当前授权的DB宿主只读OS通道证据；未猜磁盘、未重新尝试口令、未据容器hostname推断SSD或NAS。下一步由具备只读宿主访问者采集挂载映射、iostat/设备队列及对应时间窗，再与固定JAR请求的物理连接和提交事件关联。禁止以降低sync_binlog/flush策略替代诊断。

网络/JVM/池的排序证据：H每条普通查询JDBC多为数毫秒而服务端读较短，READY/RUNNING Execution主键累计JDBC44.857/37.596ms，WorkOrder31.912/24.120ms；这是多次往返合计。H RUNNING HTTP474.097、JDBC互斥并集359.636、服务器100.721ms，差额包含网络、驱动/探针等，不全是网络；HTTP剩余也包含框架/映射/agent，不全是Java CPU。

历史16并发RUNNING为22.377req/s、P95 1312.57ms；测量窗无新增行等待，业务池等待约0但Master累计401ms，READY预热另有排队。这支持先减串行往返和追查提交，不能证明提高Druid maxActive会有效。历史GC/CPU采样不足以指向CPU或GC为主因。本轮未重新测JVM/池、未复用正在运行的另一轮测量来报告新的P50/P95。

## 10. P2关联写入口与UUID恢复

|入口/流程|复用与重复路径|错误与恢复边界|
|---|---|---|
|领料/正式投产|ProductionInputService.submit NOT_SUPPORTED；prepare具名Business事务，WO→E/Issue，quantity/path/source验证|执行前先按原requestId查input；外部executePrepared不纳入本地原子承诺|
|投产结果读取|executePrepared完成后另开事务result→quantity|若结果查询失败，前面事实可能已提交；原UUID重放不再次创建/扣料，但同一故障仍可能令result重读失败，应核对持久input/issue状态，不换UUID|
|投产撤销/领料作废|复用WO/E/Issue锁和原身份；本地库存逆向仍由LocalStock唯一写者|UNKNOWN/PENDING按原provider/binding查询；不得自动重建、转LOCAL或改派|
|质量放行|locks.execution、requireActive重入、state→quantity+releaseBasis|原UUID/hash查已提交release；状态/basis变更不把重放变成新放行|
|生产交接|锁前后UUID双查，WO/E；quantity后release.state再quantity|append后的主键回读在事务内，失败应整体回滚；commit后响应丢失用原UUID|
|仓储接收|WO/E→LocalStock重复WO/E→Inventory；release.state|acceptance与stock action同Business事务，原UUID/hash可重放；不能据HTTP500假定未入库|
|正式完工|WO/E→summary→release/quantity→READY参考Routing校验→completion+E状态|UUID/hash在新增业务检查前回放；最终冻结数量保留，不改写历史|
|库存单据post|单头/库存排序锁、动作UUID；返回detail|detail在方法返回/事务提交前执行，查询异常会回滚；不能全部归为“提交后查询失败”|

提交后还有cache淘汰/序列化/网络响应失败窗口。BusinessCacheProcessor对redis删除异常记录错误，但引用目录invalidate发生在该try之外；不能声称所有afterCommit失败都被吞掉。原UUID恢复依赖身份、payload/hash和当前HTTP访问准入，绝不是“无论账号已停用仍可调用”。正常报工返回记录的回读在同一事务内，不是提交后再跑一次业务查询。

本轮未新执行这些写入口；以上是源码与既有恢复契约审查，未冒充真实故障注入结果。

## 11. 并发反例与现有测试覆盖

本轮只审查测试源码，**没有运行Maven/JUnit，也没有执行opt-in真实写库测试**。表内“有测试”不代表本轮通过。

|反例|现有证据/源码|还需证明|
|---|---|---|
|相同UUID并发重放|ProductionReportingHttpTest.concurrentDuplicatesAndOverreportRemainSerializedWithAdminAdjustments（H2 HTTP）；ProductionReportMySqlTest.realMysqlLostResponseAndConcurrentReplayAppendEachIntentOnlyOnce（管理员报工真实MySQL候选）|当前锁定视图方案下正式submit多实例真MySQL|
|相同UUID不同载荷|ProductionReportingHttpTest.trustedIdentityStartReplayAndChangedPayload|并发不同载荷；跨E UUID唯一约束；锁后双查|
|同E不同UUID争最后数量|ProductionReportingHttpTest并发数量；ProductionReportMySqlTest.realMysqlConcurrentCommandsCannotExceedConfirmedInput…|新方案正式submit与调整共用锁、只一赢家|
|报工与作废|ProductionChainLocksTest父先子/父变化拒绝；另轮concurrency.py含cancel场景|脚本存在不算结果；两种提交顺序及原UUID重放|
|READY与工艺停用/发布|Routing生命周期合同、OperationRoutingMySqlTest发布不可改；RoutingVersionMySqlTest关注版本复制|未找到针对正式READY与availability的完整当前通过证据；必须补同/不同Routing等待图|
|报工与质量处置|ProductionQualityQuantity/Propagation等规则、ProductionQualityLifecycleHttpTest部分并发删除/申报|它们不等价于report与treatment并发；要补净不良/返工/放行basis竞争|
|报工与正式完工|ProductionCompletionHttpTest完成/幂等H2闭环|未找到完整真MySQL双命令并发结果；不能用“完工缺依据失败”代替竞争|
|停用/撤权与资格|OperationUserDirectoryTest.permissionRevocationAndBulkLookupAreCurrent；OperatorQualificationTest.inactiveOrUnauthorizedIdentityIsRejected|取消COUNT后验证下次读取生效、既有准入不追溯语义，不强加跨库原子撤销|
|Factory/绑定/权限变化|RequestSnapshotTransactionTest退役池/并发厂；目录和FactoryScope测试|视图不能跨新事务/厂；冷/热cache路径及无Scope拒绝|
|回滚/缓存失效|ProductionReportingHttpTest.insertFailureRollsBackStartAndRetryUsesSameRequest；LockingReadCacheTest（H2）|新方法传递视图后的回滚、commit后淘汰失败、请求重放；不改为会话级事实缓存|

另轮B Trace证明FOR UPDATE真实执行，Mapper flushCache=true/useCache=false可避免普通缓存复用；H2 cache测试不是MySQL互斥证明。现有测试不能整体替代本专项所需的优化前后并发矩阵。

## 12. 优化候选排序及最小实施路线

|优化项|可减少SQL次数（当前B基线）|预计节省成本|正确性风险|修改范围|优先级|
|---|---:|---|---|---|---|
|内部requireValid/validUsers禁COUNT|单次调用最多1条Master|一次资格COUNT往返；H约5.3ms单样本，不承诺HTTP下降同数|低，public page保total|OperationUserDirectory及定向测试|P1/批1|
|正式submit显式锁定身份，start内部接受持锁上下文|保守首批4处普通读；重入整组候选至多7条，含重叠不可累加|减少串行往返与持WO/E锁时长；收益须测|中高，公共start仍锁/核对|submit/locks/path/quantity/eligibility小批次|P1/批2|
|锁定路径节点快照复用|保守1条；完整3→1需额外证明|减少selected读与模型构造|中，路径/工艺身份冻结|quantity/path内部模型|P1/批3|
|LEDGER只做有界路径成员校验|替代现有12条Business前置链，准确净减少待实现；不直接填12|避免无关quantity/quality重算及质量容量耦合|中，历史可读与资源拒绝需等价|report.page及专用查询/DTO|P1/批4a|
|CLOSURE共享质量事实集|最多2条质量SQL，另有E重复读机会|减少历史传输与重复summarize|中，RC视图/basisHash|quality/quantity/completion内部模型|P1/批4b|
|Operation启用校验合并|候选1条|一次普通PK往返|中，并发启停时点|资格规则小改|P2|
|单用户有界菜单/资格投影|次数未必降低，减少返回行/参数|391行历史菜单传输与Java过滤|中高，权限矩阵|中心查询＋MenuScopePolicy契约|P2|
|READY共享Routing更小锁模式|查询数可不变|目标是减少跨E排队，不是减SQL|高，工艺停用/发布/锁升级|独立批次，先争用证明|P2验证后决定|
|日志/存储/部署调查|0|可能影响每次提交；当前待归因|无业务变更；任何调参另立授权|宿主只读观测/同拓扑测量|与批1并行优先调查|
|新增索引、扩池、改持久化参数|未证明|未量化|当前无依据|本轮不建议实施|E保留|

每批只做一项主要变化，固定同一JAR/配置/账号/Factory/数据范围，准备、登录、预热、采样与正式测量分离。每批必须交付：

1. **前后真实SQL预算**：Mapper、实际JDBC execute、驱动SELECT、事务控制、协议SQL及服务器计数对齐；READY/RUNNING/replay分开；不将已由其他工作消除的84→68、69→57再次计为本批收益。
2. **前后HTTP P50/P95**：相同数据、相同并发/拓扑与独占窗口，多样本记录分布和错误；当前本轮未测，不能填预计百分比。READY预热单列，不能用RUNNING吞吐掩盖。
3. **真实MySQL反例**：§11矩阵逐项执行/说明影响范围，至少原UUID并发、异payload、最后数量、作废/质量/完工、工艺变更、回滚；记录等待链与提交顺序，H2不替代。
4. **不变量**：UUID唯一、quantity单一权威、已确认投产、path锁定/确切版本、quality basis/处置、来源与模式、固定Factory/Owner/租约；无跨请求事实缓存。
5. **历史保护**：原901与17项审计前后通过、核心事实100011不变、原标签以外资源不改；备份和独立测试资源清单，不清理不可变事实。
6. **工作区与发布边界**：前后HEAD/dirty逐文件哈希，保护并行工作；依项目规则执行定向及必要verify，重启仍由用户执行；提交/推送/部署需各自授权。

建议顺序：先做小而确定的COUNT优化，同时只读查提交链；再做持锁身份/路径的局部复用；随后读接口专用投影；最后在真等待图与工艺竞态证明支持下决定Routing锁。没有证据支持“先迁库/调持久化再看代码”或“先删一致性锁再测”。

## 13. 最终六问与交付边界

**Q1：正式报工前三项？** 内部单用户资格去COUNT；持WO/E锁后的显式身份与start锁链复用；锁定路径节点快照复用。A/B类别和非累加预算见§5/12。

**Q2：READY 5.77秒是否真由Routing？** 当前排他锁确有跨E串行机制；历史全局计数支持其为重要候选。本轮60次实时等待图为空，没有历史逐请求链，不能确认那5.77秒的排他归因。共享锁定读是需证明的较小锁范围候选，不能直接实施。

**Q3：E/WO多少可复用？** 当前对象加载仍为E10/9、WO8/6，但一部分已经是锁定读。首批可论证B候选4处普通身份读；整组重入另涉最多7条当前SQL，预算重叠。A类可直接宣布删除的身份SELECT为0；首次父发现/WO-E当前锁/写后规范化回读保留。

**Q4：数量/闭环有重复质量或N+1？** 单次报工/数量load质量各1组，无逐节点SQL N+1；闭环质量2组可共享，内部D×T重扫需关注；交接另有完整quantity两次。流水页前置全数量投影是独立访问方式问题。

**Q5：77ms能定位fsync吗？** 新P_S确认日志文件IO显著及fsync计数增长，未取得按请求/操作类型和宿主设备映射，根因仍未确认；不把MISC平均或全局commit平均改名为该77ms。

**Q6：先代码还是部署？** 确定的小SQL优化先分批做，同时调查日志提交与网络拓扑；持锁身份/路径优化须并发证明；Routing改锁后置；不先改Druid/MySQL持久化策略。该排序来自短服务端读取、多次往返、显著提交和未定位READY等待，而非笼统“数据库慢”。

本轮新增本报告的§1–13及证据索引，诊断工具`scripts/performance/production-core-readonly.py`仅执行SELECT/SHOW/EXPLAIN ANALYZE、校验目标server/binding，不开启监控、不加锁、不写业务数据。新17项审计全部0差异，901闭环100011核心事实保持；这不是全部并发或重质量容量验收。诊断Python AST、证据JSON解析、文档相对链接、逐文件生产源码哈希、`git diff --check`与`check-private-config.py`通过；私有配置检查只含选定工作树，不含Git历史。证据目录沿用既有Git忽略规则，留在本地供复核。没有生产源码/前端/Flyway/配置/索引/事务/池参数修改，没有构建、运行服务、提交、推送或部署。
