# Base MES — 质量管理 V2 差异审查

日期：2026-10-18。结论：**CONDITIONAL GO，允许按本文最小方案进入实施；当前实现尚未达到目标 V2，不构成 V2 上线验收。**

复用现有 ProductionDefect、QualityTreatment、ProductionQuantityService、QualityRelease 和生产交接链。新增工序所属不良类型及工废/料废分类扩展，扩展处置事实与交互；不重建不良主子单，不新增检验前置门禁，不修改生产数量算法，不自动操作库存或 ERP。

## A. 真实代码与数据库基线

### A.1 工作区与范围

| 仓库 | 分支 / HEAD | 审查基线 |
|---|---|---|
| `/Users/user/work/lixin/mes-base` | main / `5793986eda8b52eb6d24a84640b43f5d192e2cb3` | 当前工作树，包含 6 个已修改文件及 2 个未跟踪报告 |
| `/Users/user/work/lixin/mes-base-ui` | main / `94167e0c25fd9cb4dfcac5ecef369f6e8b715546` | 当前工作树，包含已修改页面/API及未跟踪 QualityTreatmentPanel 等产物 |
| `/Users/user/work/heri/ktg-mes` | master / `14f486d24c0162758ee10dc05aa69253760be89e` | 干净工作树，只读参考，排除 ktg-wheel |

已读个人工程入口、工程原则、协作流程、Java/前端规范、知识治理及三个仓库 AGENTS。已搜索 docs、doc、knowledge 对应主题；当前 V2 差异审查按用户指定文件新建，与既有实施报告交叉引用，保留原时间线。

后端已有修改包括 ProductionQuantityService、ProductionReportSubmitService、ProductionExecutionStatus、两个 HTTP 测试及 ProductionQualityQuantityTest；数量投影的 `writable` 已在当前工作树区分工单/执行生命周期。前端不良页及 API 相对 HEAD 已加入 SAME_NODE、处置面板与名称显示；不能按 HEAD 旧页面认定当前没有这些能力。本轮未修改这些文件、生产代码、配置、测试或迁移。

### A.2 本轮真实 PROD 只读结果

初始指定 SIMPLE_B 的 `192.168.18.25:3306` 连接两次超时，包括沙箱外重试。用户随后明确“用prod”，本轮才切换到 PROD 只读核实；未切换任何 Factory 模式或运行服务。

| 项目 | 本轮读取结果 |
|---|---|
| 实例 | `110.42.227.83:3306`，MySQL 8.0.40，hostname `VM-4-11-centos` |
| 实例 UUID | `21328a5f-b5fa-11ef-88d3-5254005efa1b` |
| 中心库 | `mes_base_prod`，迁移最新 V40 成功记录 |
| Factory | ID/code `PROD`，ENABLED，LOCAL，integration_revision=1，ERP provider/config_ref 均 NULL |
| 数据源 | ID=1，ENABLED，真实绑定 `110.42.227.83:3306/mes_base_prod_business` |
| 业务迁移 | 最新 V38 成功记录；本轮只读取历史记录，没有执行 Flyway validate/migrate |
| 工序 | 8 条；`operation` 有 9 列，无 factory_id，唯一键 `uk_operation_code(code)` |
| 不良 | 3 个 declaration、3 条版本，均 CREATE/SAME_NODE，各数量10；PROCESS 1条、MATERIAL 2条 |
| 处置 | ASSESS/INDEPENDENT 3条、ALLOCATE 3条、START 1条、RESULT 1条，共8条；分配报废26、返工4，处理4、恢复4、失败0 |
| 质量放行 | RELEASE 8条；这里只是事实行数，不是当前有效额度 |
| 关联核查 | 不良关联 Execution/确切路线节点缺失数0；关联节点 operation_name 缺失数0 |
| 事实保护 | 不良、处置、放行三表均存在拒绝 UPDATE/DELETE 的触发器，共6个 |

以上数量来自同一只读数据查询事务。原始申报30、恢复4并不等于全厂当前可交接26；它们跨 Execution，不能替代统一生产投影。PROD 本轮样本没有 OVERLAP、UNVERIFIED、退工序/退仓或历史订正样本，不据此声明这些分支已真实验证。

脱敏证据保存在 `.local/quality-v2-gap-review-20261018/{baseline,schema,quality-data}.txt`。读取使用 INFORMATION_SCHEMA、固定 SELECT、`START TRANSACTION READ ONLY`；没有 DDL/DML、触发器破坏探针或业务命令。初期旧 mysql-dev 客户端文件实际指向另一实例的 dev 账号，只读取到实例身份及可见库名即停止；未将其作为目标业务证据。

### A.3 源码证据索引

后文 E 编号均指向当前真实文件；方法名和 SQL 名用于定位，结论范围以所列实现为准。

| 编号 | 文件 / 核心位置 |
|---|---|
| E01 | [ProductionDefectController](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/controller/ProductionDefectController.java)：page/detail/history/create/revise/voidDeclaration、Version白名单、权限 |
| E02 | [ProductionDefectService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/ProductionDefectService.java:100)：execute、validate、nodes、names、fingerprint、replay |
| E03 | [ProductionDefectModels](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/domain/ProductionDefectModels.java)：Facts、Command、NodeTotal、Node |
| E04 | [MesProductionDefectMapper.xml](/Users/user/work/lixin/mes-base/mes-business/src/main/resources/dev/mesbase/business/quality/dao/MesProductionDefectMapper.xml)：insert、Latest、selectLatest、existsExecutionHistory |
| E05 | [QualityTreatmentService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/QualityTreatmentService.java:122)：summarize、nodeTotals、assertMutable、assessCreatedVersion、execute、append、effective |
| E06 | [QualityTreatmentModels](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/domain/QualityTreatmentModels.java)、[QualityTreatmentController](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/controller/QualityTreatmentController.java) |
| E07 | [ProductionQuantityService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:174)：load、calculate、validateChange、positiveReportBlockReason；[ExecutionQualityReferenceQueryImpl](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/ExecutionQualityReferenceQueryImpl.java) |
| E08 | [QualityReleaseService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/QualityReleaseService.java:67)：state/execute；[ProductionReceiptService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReceiptService.java:100)：receive/reverse |
| E09 | [OperationServiceImpl](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/impl/OperationServiceImpl.java)、[RoutingReferenceRules](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/RoutingReferenceRules.java) |
| E10 | [RoutingServiceImpl](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/impl/RoutingServiceImpl.java:231)：publish；[ExecutionPathService](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathService.java:87)：view/select/confirm；[ProductionChainLocks](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionChainLocks.java) |
| E11 | [不良页面](/Users/user/work/lixin/mes-base-ui/src/views/quality/productionDefect/index.vue)、[不良API](/Users/user/work/lixin/mes-base-ui/src/api/quality/productionDefect.ts) |
| E12 | [QualityTreatmentPanel](/Users/user/work/lixin/mes-base-ui/src/components/business/quality/QualityTreatmentPanel.vue)、[处置API](/Users/user/work/lixin/mes-base-ui/src/api/quality/qualityTreatment.ts) |
| E13 | [工序页面](/Users/user/work/lixin/mes-base-ui/src/views/production/operation/index.vue)、[ProductionReportManager](/Users/user/work/lixin/mes-base-ui/src/components/business/production/ProductionReportManager.vue)、[现场报工页](/Users/user/work/lixin/mes-base-ui/src/views/production/productionReporting/index.vue) |
| E14 | [V26不良](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V26__production_defect.sql)、[V32处置](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V32__quality_treatment_facts.sql)、[V35放行/接收/完工](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V35__local_production_closure.sql) |
| E15 | [V3工序](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V3__operation_routing.sql)、[V9路线图](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V9__routing_graph.sql)、[V15去业务工厂列](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V15__remove_business_factory_columns.sql)、[V7删除早期质量表](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V7__remove_early_quality_tables.sql) |
| E16 | [WasteCategory](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/domain/WasteCategory.java)、[DefectDestination](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/domain/DefectDestination.java)、[LocalProductionDefectErpGateway](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/integration/LocalProductionDefectErpGateway.java) |

### A.4 历史验收与本轮证据边界

- [V1.6 LOCAL生产闭环报告](2026-10-17-local-production-end-to-end-v16-report.md)：历史 SIMPLE_B 五节点样本，E/Q/交接/接收/库存94，质量处置后必须重新放行。
- [V2.2 LOCAL仓储报告](2026-10-17-local-stock-documents-v22-report.md)：历史 PROD 正式库存单据、库存 Owner、接收隔离及人工作废工单接收边界。
- [V2.3全链报告](2026-10-17-local-full-chain-v23-acceptance.md)：原判定保留；C03报废6/恢复4→94，C04待核实阻断受影响工序及下游，C11晚不良保留既有交接/库存。
- [V2.3.1收尾报告](2026-10-18-base-mes-v231-case12-closure.md)：历史 CASE12 补验及 writable 修复 GO，C01～C11沿用历史结果；没有据此宣称本轮重跑。
- [Phase2A报告](2026-10-16-production-execution-quality-phase2a-report.md)、[Phase2B/V1.3报告](2026-10-16-production-quality-phase2b-quantity-integration-report.md)、[原不良报告](2026-10-16-production-defect-phase1-implementation-report.md)用于理解演进，均以当前源码覆盖旧能力描述。

本轮执行的是代码/API契约/前端源码审查与真实 PROD 数据库只读核查。未运行 Maven/npm、未提交业务请求、未登录浏览器或做截图/视觉审查，未启动/停止/重启服务。现有8081服务是否加载当前 dirty 源码未核实；历史18081/15173验收实例已在原报告中清理。因此不声明当前运行API/UI通过，不把测试源码存在当作测试成功。

## B. 当前质量领域完整调用链

### B.1 单条申报及版本

E11 `submit → productionDefect.create` → E01 `POST /api/quality/defects`（create权限）→ E02 `create/execute` → E04 `insert` → `mes_production_defect`。

E02先校验命令及可信登录操作者指纹、查 requestKey；按 E10 WorkOrder→Execution 数据库锁序锁定后重查幂等和当前版本，E05 assertMutable保护已处理事实；验证实际路径、数量、引用、去向，再追加完整版本。非VOID可同步追加 ASSESS；末尾调用 E07 validateDefectChange重算当前投影，再由本地Gateway确认。整段使用具名业务事务、READ_COMMITTED及异常回滚。

REVISE/VOID分别经 `/{id}/revisions`、`/{id}/void`，需要 expectedRevision 和原因；追加 revision，不 UPDATE旧行。最新VOID不再允许新版本；已存在处置事件或被其他 OVERLAP引用的申报不能随意订正/作废。原 requestKey 同摘要返回原版本，同key不同内容/操作者冲突。申报ID由服务端UUID生成，requestKey当前允许非空最长64字符，并不强制UUID；处置requestId则要求规范UUID。（E02/E05）

### B.2 核实、处置、结果

E12 → `POST /api/quality/defects/{id}/treatment` → E06（revise权限）→ E05 execute：校验UUID、锁原Execution、重查版本/重放、验证LOCKED实际路径，append不可变事件，E07重算，再返回summary/history。持久化复用 MesQualityTreatmentMapper/XML/Example，不存在正式通用UPDATE/DELETE入口。（E05/E06/E14）

动作链为 `ASSESS → ALLOCATE → START → RESULT`；另有 `CANCEL_ALLOC / CANCEL_START / CORRECT_RESULT` 追加有符号修正，父事件、申报版本及剩余额度均须匹配。ASSESS一版一次；重新核实需要在允许时追加申报版本，不修改原核实事件。（E05）

### B.3 数量、放行、交接

E07通过 ProductionDefectQuantityQuery → ExecutionQualityReferenceQueryImpl → E05 nodeTotals取得最新非VOID质量贡献。正常报工仍从 ProductionQuantityMapper取得不可变报工净量。

- 原始申报量 `defectQty`：最新非VOID记录总量，包括重复观察和待核实。
- `directDefectQty`：仅 INDEPENDENT；`recoveredQty`：仅该独立申报可信结果恢复；OVERLAP不再次扣减。
- `netDefectQty = directDefectQty - recoveredQty`。上游有效产出依实际选定路径传播；历史超额作为投影差额，不回写旧REPORT。
- UNVERIFIED保留原始数量但不猜独立；E07 positiveReportBlockReason阻断受影响节点及下游新增正报工。这是现有数量核实边界，**不等于新增必检/检验覆盖门禁**，本轮保留。
- E08 QualityRelease.state把当前路径、投入、节点数量与不良/处置事实身份纳入basis；待核实、未处置、返工未完成等使当前放行无效。恢复不自动放行。
- E08 ProductionReceipt.receive同时检查当前数量、正式放行basis、额度、来源与目标仓库。正式仓储接收/库存属于后续现有用例，不由不良登记或处置决定代办。

## C. 工序、不良类型与 Execution

### C.1 稳定身份和历史

`operation(id,code,name,description,status,sort,external_id,revision)`是**当前Factory业务库内复用的基础资料**，不是中心库跨厂全局工序。可被多条路线及同路线多个节点引用；同编码跨业务库不代表同一身份。V3曾有factory_id，V15已移除，PROD当前Schema与现模型一致。（E09/E15、A.2）

`routing_node.operation_id → operation.id`；路线发布由 E10 publish 冻结 operation_code/name/description。Execution固定 product_id、workorder_id、routing_id；`mes_execution_path_node(execution_id,sequence_no,routing_node_id)`保存实际选择顺序。实际工序必须通过该路径节点解析，而非接受客户端operationId；同一个operation出现于多个node时仍需nodeId区分加工位置。（E10、E15、PROD Schema）

当前没有“唯一当前正在加工节点”的持久化游标，实际路径可有多道已报工节点。V2不能用最大nodeId、最后一笔报工或排序猜现场工序；应从现有现场上下文带入nodeId并在服务端证明属于该Execution确切路径，独立管理页才提供受控实际节点选择。（E02 nodes、E07 load、E10 view）

历史读取不以operation.enabled过滤。E09删除工序受routing_node引用保护；E10有质量历史后禁止换实际路径，发布路线内容受保护。因此工序改名不覆盖已发布node快照，停用也不应隐藏历史。E02列表/history名称来自节点快照，单条detail/创建响应调用不带names的view，名称可能为空；不能把这解释成历史身份丢失。缺旧快照时须标未知或明确“当前名称”，不能把当前目录值冒充历史。（E01/E02/E07/E09/E10）

### C.2 不良类型调查与 A/B 判断

当前Base质量源码、前端正式views/API、迁移及PROD表目录中，**未找到正在使用的工序所属不良类型对象或关联**。Facts只有自由文本defectReason、wasteCategory，没有defectCode/typeId。V1的qc_defect/qc_index已被V7撤回，PROD目录中也没有两表；旧配置excludeTables和历史文档引用只是遗留历史线索，不是现行字典入口。（E03/E14/E15、A.2）

历史项目 [QcDefect](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/qc/domain/QcDefect.java)有 defectCode/name、indexType、level，没有operation_id；不满足目标归属。仅作参考，不恢复其全局字典或复制旧不良主子业务。

**推荐模式A：Operation直接拥有OperationDefectType，以子表operation_id实现1:N。** 模式B在数据关系上同样可行，但当前Base没有可复用的在用质量类型对象，不能为了“复用”恢复已撤回qc_defect再迁移它。若实施前出现新并行对象，重新核实其真实归属后才选择B，不同时维护两套。

复用E09标准CRUD、生成Mapper/XML/Example、版本并发及引用删除模式；前端在E13既有工序详情tabs增加“不良类型”，与现有人员配置一样绑定已保存工序，不要求逐路线配置。这里只是配置父子归属，不建立不良申报主子单。

## D. ProductionDefect 差异

| 审查项 | 当前事实与判断 |
|---|---|
| 单条还是总量 | 每次CREATE一条独立declaration；Facts没有items集合或主单总数。多次提交允许多条，已满足逐次申报基础（E02/E03） |
| 数量 | 单条正数，DECIMAL(20,6)；单条不能大于对应节点reportedQty，独立贡献总量还由统一数量投影校验。不是先填写总量再拆类型（E02/E07） |
| 工序 | affected/discovered必填，responsible可空；校验在实际选定路径，但没有类型→operation校验。旧三节点语义保留，新现场表单只显示一个可信实际工序，不扩建双工序模型（E02/E03） |
| Execution/产品 | executionId固定，affectedNodeId固定；产品通过Execution可信关联。当前不良表不重复存product_id，不新增客户端任意产品（E02、A.2） |
| 重复申报 | 相同请求身份去重；不同requestKey不推测为重复实物。独立/重叠由ASSESS及依据确认，不能按同名类型自动去重（E02/E05） |
| 来源 | 未见不良表source/provider或ERP外部单据号；Gateway只返回localSuccess、externalExecuted=false，不执行ERP。不得据此宣称ERP质量已处理（E16） |
| 后续核实 | 追加mes_quality_treatment ASSESS，不改原不良行；新申报版本默认重新待核实（E05） |
| 编辑/删除 | 无普通PUT/DELETE；未处理时可受控追加REVISE/VOID，已处理或被重叠引用后assertMutable拒绝（E01/E02/E05） |
| 快照 | 现有node已冻结工序名称；缺类型ID/类型code-name快照、类别事实。无需重建ProductionDefect，只增必要列及新DTO转换（E03/E10/E14） |

**必须保留的细节：** 旧记录在操作回放前不能重新套用今天的类型启停、默认类别或工厂模式。旧VOID继承原Facts而不重新检查旧目标仓库启停；新增字段后也要继续允许历史合法作废，不要求未知旧类型突然必填。已处理记录的类别/类型纠错不能借普通REVISE绕过assertMutable；V2首版可继续禁止，若确需纠错再设计独立追加事实。（E02/E05）

## E. 工废 / 料废类别差异（按最终确认修正）

2026-10-18 用户最终确认：“工费 / 料费”为笔误。此前独立费用模型建议作废，本节及下文以最终工废/料废语义为准，不作为原费用设计的实施依据。

保留现有WasteCategory和waste_category，PROCESS=工废、MATERIAL=料废，仅增加BOTH=工废＋料废。类型默认列采用default_waste_category；不新增独立类别列或模型，既有waste_category保持NOT NULL。

默认类别必须显式选择；普通申报采用服务端核对的默认类别，授权覆盖使用business:quality:defect:waste:override。提交所选wasteCategory和expectedTypeRevision，配置变化应冲突，历史类别与类型快照不因默认改变而重解释。

统计按PROCESS/MATERIAL/BOTH互斥分桶，BOTH只计一次。历史原值直接读取、不转换、不回填，不新增历史UNKNOWN类别桶。类型快照历史为空仍显示“历史未记录”。类别与处置方式、仓库去向独立。

## F. QualityTreatment 差异及即时/延后处置

### F.1 现有语义映射

| 目标 | 当前表达 | 能力与边界 |
|---|---|---|
| SCRAP | ALLOCATE.scrap_qty；RESULT.failed_qty | 支持直接报废及返工失败转报废，不另扣第二次不良；支持部分、多次分配（E05） |
| REWORK | ALLOCATE.rework_qty → START → RESULT | 决定、开始、实际结果分离；父事件余额保护，成功只恢复原损失，不新增REPORT（E05/E07） |
| RETURN_OPERATION | 不良Facts.destinationType=ROUTING_NODE + targetNodeId | 当前是登记去向，按**discovered节点之前**的实际路径校验，可指定更早节点；不是可执行返工目标（E02 validate） |
| RETURN_WAREHOUSE | Facts.destinationType=WAREHOUSE + targetWarehouseId | 仅校验已启用仓库、保存登记；没有库存动作。E05正式处置除ASSESS外要求SAME_NODE，所以还不是完整正式退仓决定（E02/E05/E16） |

当前处置事件routing_node_id固定为原affectedNodeId，没有独立处理目标；E05非ASSESS统一要求INDEPENDENT且SAME_NODE。**因此不能通过把退工序/退仓重新显示在下拉框，宣称四种处置已闭环。** 旧去向登记不可自动迁为“已处置”，也不应为返工而要求管理员把事实改成SAME_NODE来掩盖目标。（E05/E12）

### F.2 最小扩展方向

继续使用ALLOCATE表达分配决策、START表达返工执行、RESULT表达确认结果，不新增替代状态机。ALLOCATE增加明确decisionType及目标快照，SCRAP/REWORK仍复用现有数量列；RETURN_OPERATION/RETURN_WAREHOUSE增加可计量的returnQty，用于占用同一待处置余额。每个新分配事件单一决策，旧混合scrap+rework事件按原字段解释，不回写拆分。

- REWORK可选择当前原节点或合法已经过的上游节点；新target_node_id表示实际处理位置，原routing_node_id继续表示数量归属，禁止将损失改扣目标工序。START继承对应ALLOCATE目标，RESULT继承START，累计恢复只能作用于原申报并遵守原额度。
- “已在所选路径”不等于“已加工经过”：候选应结合既有可信加工事实及人工处置依据校验，拒绝未选分支、下游及尚未经过节点，不能只用路径顺序。单件实物流转没有SN证明时须明示数量级人工依据。
- RETURN_OPERATION是退回决定；若后续要返工，必须在原事件链受控消耗/转换其剩余额度，不能再创建一份可同时花费的REWORK分配。首版推荐把“退回上游返工”映射为REWORK+target并显示退工序去向；纯退回决定的终结边界须L节确认。
- RETURN_WAREHOUSE只保存退仓决定、数量、目标及依据，不调用LocalStockService、WarehouseIntegration或库存单据创建。它不恢复数量，不表示实物已接收；对正向交接不能自动给额度。
- 处置类型/目标必须在事件保存，不能仍靠申报行唯一destinationType控制未来所有部分分配；原申报去向保留为历史意图。历史SAME_NODE事件缺新target时只按原事实解释为原节点，不UPDATE回填。
- summarize要纳入退回占用余额、混合处置及撤回，releaseBasis继续冻结全部事件身份。既有State优先复用，退工序/退仓用decisionType筛选；不要只因有退仓决定就显示“已实物完成”。新增状态确有必要时再给证明，不能先建整套状态机。

上述扩展改变的是质量事件的决策、目标和剩余额度投影；向ProductionDefectQuantityQuery仍提供相同D/R/U契约，**不修改ProductionQuantityService公式**。若上游返工被要求自动重跑后续全部工序、增加新的正常产出或改变DAG，则超过本次范围，不能假借本方案实现。

### F.3 一次用户提交能否合并

**可以，但目前API只支持申报+可选ASSESS，不支持同时ALLOCATE。** E03 Command.assessment会由E02→E05 assessCreatedVersion直接写核实事件；create入口只有create权限，而独立treatment入口要求revise。当前没有基于“质量人员”名称的硬编码身份判定。因此若产品要求只有质量授权者核实，必须在服务端共同用例入口补授权；只限制新按钮会留下旧CREATE携带assessment的旁路。（E01/E02/E05/E06）

| 路径 | 推荐提交及结果 | 不得省略 |
|---|---|---|
| 立即处置 | 同一业务事务受控追加不良事实、类别快照、ASSESS、ALLOCATE；全部成功或全部回滚；START/RESULT仍是后续实际执行事实 | create、核实/处置授权，独立/重叠依据、版本/锁/额度/幂等检查 |
| 延后处置 | create只写一条申报及类别，默认UNVERIFIED；授权人员再ASSESS和ALLOCATE；现场初始意图若需保存明确标为未生效意图 | 不自动标INDEPENDENT，不把“想报废”当已确认报废，不把意图计入处置额度 |

只有已核实INDEPENDENT才可正式分配；OVERLAP不再次分配/扣减，UNVERIFIED只能保存意图。不能为“一键”默认独立。初始意图建议复用新增可空字段记录在申报完整版本中，真正处置另外追加事件；未选意图不必人为默认SAME_NODE。

聚合请求建议沿用一个客户端UUID并保存命令摘要；子事件ID可由外层UUID+动作稳定派生或显式传入并纳入摘要。重放必须在读取当前配置默认、启停和重新授权业务动作前识别原结果；同UUID改类型/类别/意图拒绝。旧requestKey摘要算法必须保持可验证，新旧命令版本明确区分，不能因新增字段导致旧成功请求失去重放能力。新Controller只调用一个用例Service；不得在Controller或前端串行两次HTTP后宣称事务原子。

## G. 当前前端交互与建议

本节为源码交互审查，无本轮截图、尺寸或可访问性实测结论。

- **工序配置（E13）**：现有工序详情有信息/人员tabs，可直接增加绑定operationId的不良类型嵌入面板，先保存工序再配置；独立配置权限，撤权/切厂清空候选。
- **逐次申报（E11）**：当前本来就是一条数量、一条原因，没有总量主单。问题是影响/发现/责任节点、工废料废、去向等字段均面向管理录入，缺产品上下文、工序类型和类别。现场从Execution详情或报工上下文打开单条弹窗，产品/工序自动只读展示，填写类型、数量、类别、备注和可选意图。管理页保留历史复杂字段只读，不以新简化表单重解释旧事实。
- **核实/处置（E12）**：当前需打开历史面板后选ASSESS、ALLOCATE、START、RESULT，技术动作暴露较多。针对整条报废/整条返工提供一键填量提交，部分处理仍允许输入数量；系统计算pending，不新增“先填写总不良量”的步骤。后端事实继续分开。
- **待核实/待处置等列表（E11/E12）**：当前列表主要展示申报，summary在详情读取，无按处置状态/类型/类别筛选的正式列表契约。建议服务端分页汇总，不能逐行请求details或只按当前页统计。混合部分处置同时显示余额、返工中/报废量，保留既有State，退工序/退仓另以决策类型过滤。
- **重叠候选（E12）**：relatedOptions来自外层当前页rows，可能漏掉同Execution其他页的合法原申报；服务端仍校验INDEPENDENT。应提供有界分页候选查询，不能扩大当前页数组当完整候选。
- **结果不确定（E11/E12）**：处置面板有sessionStorage原命令恢复、只允许原请求重查；申报有稳定requestKey但没有同等跨刷新恢复，且摘要内容修改后继续同key会冲突。V2提交应冻结完整命令，明确拒绝后才允许改内容换key；网络未知先查原结果。
- **数量显示（E13）**：管理面板已区分原申报、独立、待核实、恢复及未恢复；现场报工页有“有效不良”列绑定defectQty，容易把含重复/待核实的原始量当净扣减。改标签/响应展示到明确指标，不改数量算法。

## H. 目标数据结构及 API 建议

以下均为建议，未创建表或Flyway；实际类型/长度以正式设计及真实metadata核实。新业务表不加factory_id，不建全局字典或路线节点配置。

| 对象 | 最小建议字段/约束 | 历史策略 |
|---|---|---|
| operation_defect_type | id BIGINT；operation_id；code/name；ENABLED/DISABLED；default_waste_category；revision；必要sort/remark。operation_id创建后固定，跨工序同名不同ID；同工序code去重约束实施前确认 | 无旧行迁入；已引用禁止删除，允许停用。工序删除保护须同时检查该配置，不能只检查routing_node |
| mes_production_defect增量 | defect_type_id、defect_type_code_snapshot、defect_type_name_snapshot、defect_type_revision；复用既有waste_category，必要初始意图字段。工序身份从原affected_node→operation解析，优先复用节点工序快照，不重复建可编辑operation_id | 旧类型快照NULL，类别保持原值；旧revision及指纹保持。新字段通过受控申报入口必需校验，不用数据库默认伪造旧事实 |
| mes_quality_treatment增量 | decision_type、target_node_id、target_warehouse_id、return_qty及必要目标展示快照；原routing_node_id、parent_id、五类数量、审计事实保留 | 旧事件依原动作和数量解释；不批量改写旧目标/状态；处理和恢复原损失的关系不变 |

不新增不良主单/明细、责任工序新实体、实际金额字段或第二套质量账本。工序被停用时：禁止新增该工序的配置和新现场申报（建议），历史查询/原UUID重放/已有申报核实处置/合法撤销继续可用；晚申报旧事实通过现有授权管理通道保留，不因普通现场开关抹除事实。类型停用同理，已有版本续读旧快照；若追加订正而类型不变，不能强制替换为今天启用的新类型。

建议接口草案，命名可按现路由风格收敛：

| 接口 | 用途 / 服务端约束 |
|---|---|
| `/api/production/operation/{operationId}/defect-types` GET/POST，`/{typeId}` PUT/DELETE | 配置管理；URL父ID绑定、对象归属、revision、引用删除保护；独立read/create/update/delete权限 |
| `/api/quality/defects/executions/{id}/nodes/{nodeId}/declaration-context` GET | 返回Execution产品、确切节点/operation、允许类型、默认类别/配置revision和当前用户可覆盖能力；不能以目录缓存代替提交校验 |
| `/api/quality/defects/declarations` POST（或现接口明确V2 DTO） | 单条申报；requestId、executionId、nodeId、typeId、expectedTypeRevision、wasteCategory、qty、备注/时间、可选assessment/initialDisposition。无客户端productId、operationId、操作者字段；复用原Service/Mapper与事务 |
| 原 `/{id}/treatment` POST | 扩展decision/target和必要额度字段，保留原动作、原命令回放及父事件预算；旧非SAME_NODE登记不自动升级为正式处置 |
| 原不良列表及新增有界summary/statistics GET | 服务端按工序/类型/类别/派生状态筛选统计，明确观察量与净扣减口径；统计以最新非VOID版本及核实语义为准，不累计历史revision |

类型→operation归属必须在提交事务核实：Execution→确切routing/path node→operation→type.operation_id；客户端即使传同名其他工序typeId也拒绝。引用配置的修改/删除与申报并发需共享锁或条件revision策略，定义锁序并测试；不能先读启用、随后无约束删除配置。快照与事实写入同业务事务，不在响应阶段临时JOIN今天的类型名替代历史。

新增表按真实metadata经项目Codegen Skill生成完整persistence/backend及必要测试，限制会绕过业务规则的CRUD，沿用既有MBG Mapper/XML/Example、具名事务与PageHelper；本轮未调用生成器。修改现有生成文件时遵守MES-CODEGEN标记保护，不能重新生成覆盖并行手工改动。

## I. 兼容性与数量传播风险

| 风险 | 具体证据 / 最小控制 |
|---|---|
| 历史丢失或被误分类 | E04 latest/版本查询、E14触发器；新增列可空，历史不INNER JOIN启用类型，保留PROCESS/MATERIAL原值，不UPDATE旧账本 |
| 配置改名/停用破坏历史 | E02 names/E10发布快照已保护工序；新类型必须冻结名称/代码和类别。新配置变更只作用于后续申报 |
| 跨工序同名类型误选 | 当前E03无类型约束；新增服务端type.operation_id严格等于实际节点operation_id，比较ID而非名称 |
| 重复扣减 | E05 nodeTotals/E07 calculate；报废/退回决定不再次增加D，OVERLAP不扣，BOTH不能被金额统计反向计入数量 |
| 重复处置 | E05原UUID/parent/预算/版本锁；退回数量必须占同一预算，撤回亦追加事件，不能重复分配已花额度 |
| 返工自动增产 | E05 RESULT恢复原申报，E07计算原节点R；处理目标可变不等于损失节点改派，不写REPORT、不补齐下游加工 |
| 退仓自动库存 | E16仅本地确认；新增退仓也只决策，不能调用LocalStockService或创建正式仓储单据。验收前后库存与库存动作零变化 |
| LOCAL/EXTERNAL边界 | E02/E05没有独立Factory模式/来源准入，质量事实本身共享同一核心；不能把本地登记ACK当ERP生效。E08正式放行明确LOCAL_ONLY、交接校验来源。新退仓候选/决定需按已持久化工单/仓库来源与当前允许模式校验，异常失败关闭，历史按原身份处理 |
| 权限旁路 | E01 CREATE携带assessment只需create，E06后续核实需revise；若核实属质量人员，旧/新所有入口需共同授权检查，不能只修新聚合入口 |
| 放行/交接事实绕过 | E08 basis含质量事实身份。新增处置/意图必须明确哪些是生效依据；正式处置后原basis失效，重新放行，历史Q/R/A不改写 |
| 终态/晚申报 | E02未统一套正常报工writable；E07当前writable仅正常报工。不能借前端简化把历史质量登记一并禁掉，也不能自动撤销COMPLETED/原库存 |
| 逐次申报增长上限 | E05 declarations最多1000、executionEvents最多5000，超界fail；新事件更密集可能触及上限。首版保留显式边界并评估业务量；达限不截断金额/数量继续成功，不为本轮顺手重写算法 |

源代码未显示质量登记直接写LOCAL库存或外部HTTP；这是静态结论。PROD本轮为LOCAL，仅只读检查，不能由它推出EXTERNAL真实运行或新退仓功能已获验证。

## J. 差异矩阵

| 能力 | 当前实现 | 目标实现 | 差异 | 建议改造 | 风险 |
|---|---|---|---|---|---|
| 工序不良类型 | operation稳定、routing_node引用，无在用子类型（E09/E10/E15、PROD） | Operation 1:N | 缺子配置 | 新OperationDefectType，operation_id固定 | 误建全局N:M |
| 不良类型配置 | 工序信息/人员tabs（E13） | 工序详情维护 | 缺类型API/UI | 复用CRUD及嵌入面板 | 父ID转移/删除引用 |
| 单条申报 | 一次CREATE一事实，无主单总数（E02/E03/E11） | 每次一事件 | 已具基础 | 增DTO字段，复用原账本 | 重建主子单破坏追溯 |
| 工序身份校验 | 校验实际节点，无类型归属（E02） | 实际node→operation→type | 缺类型约束/可信上下文展示 | 服务端解析产品/工序 | 同名/跨工序选型 |
| 工废/料废类别 | PROCESS/MATERIAL工废料废（E16） | PROCESS/MATERIAL/BOTH | 既有类别缺BOTH | 复用WasteCategory/默认/覆盖权限 | 历史误推、BOTH双计 |
| 缺陷历史快照 | 原因文本、node工序快照（E02/E10） | 类型/类别历史稳定 | 缺类型快照 | 类型code/name/revision及类别冻结 | 配置改名重写历史展示 |
| 不良数量统计 | 最新版本、D/R/U统一传播（E04/E05/E07） | 自动分组且不重复扣 | 缺类型/类别维度 | 新查询统计复用原量口径 | 原始量冒充净扣减 |
| 独立/重叠核实 | ASSESS及依据，默认UNVERIFIED（E05） | 保留 | 主要是入口权限/交互差异 | 保留事实，统一核实授权 | 为一键默认独立 |
| 报废 | ALLOCATE.scrap、RESULT.failed（E05） | SCRAP | 已实现核心 | UI语义映射/一键填量 | 再扣D或恢复报废 |
| 返工 | SAME_NODE分配/开始/结果（E05） | 合法已过实际工序可选 | 缺事件级目标 | 目标与归属分离，原额度链复用 | 自动重走DAG/增产 |
| 退工序 | 申报去向登记，上游按discovered（E02） | 正式退回决定/目标追溯 | 非正式处置闭环 | 扩展事件目标和预算，确认纯退回语义 | 双份返工额度 |
| 退仓库 | 启用仓登记，无库存（E02/E16） | 正式决定，无库存 | 缺处置事件支持 | 新decision+returnQty/目标 | 被当实际接收/放行 |
| 页面交互 | 单条申报+历史内技术动作（E11/E12） | 上下文自动填、即时或延后 | 缺类型/类别，步骤可简化 | 一个弹窗多个后端事实 | 前端多HTTP伪原子 |
| 权限 | read/create/revise/void；CREATE可ASSESS（E01/E06） | 配置、申报、核实/处置、类别覆盖可区分 | 缺细分及统一核实检查 | 复用EndpointAccess+Service共同准入 | 旧入口绕过新权限 |
| 历史兼容 | 不可变版本、处置、放行、节点保护（E02/E05/E08/E14） | 继续读/重放/合法撤销 | 新字段与旧必填契约冲突 | nullable旧数据、版本化DTO/指纹 | 批量补值/旧UUID失效 |
| LOCAL生产回归 | V1.6、V2.2、V2.3.1历史验收，当前PROD事实可读（A.4） | 原闭环及新类型/处置均通过 | V2尚无实施或验收 | 定向+全量+真实API/UI | 把历史GO冒充V2 GO |
| 正式放行与接收 | basis校验、来源、额度及独立接收（E08） | 保留 | 新事件纳入依据需验证 | 保留原入口与事实 | 自动放行/自动入库 |

## K. 分期实施计划与验收

### Phase 1 — 工序不良类型

新增正式迁移、真实metadata生成后端，完成配置API/权限/工序详情面板及引用删除保护。表结构不得写factory_id；同名跨工序不同身份。配置改名/停用、跨工序typeId、并发revision及切厂/撤权是交付必测。真实目标按用户最终授权再次核实，不能沿用本轮只读授权执行写迁移。

### Phase 2 — 单条申报及类别

扩展原不良版本、类型快照、wasteCategory、上下文GET及新申报DTO；冻结默认/覆盖，保护旧key指纹，修正legacy wasteCategory读取。普通操作者只申报，核实/处置授权在服务端共同执行。不得增加检验完成门禁或修改Quantity公式。

Phase1/2可同一版本发布：无新类型配置的申报入口无法闭环，但代码/迁移仍分步可审查；配置先建、旧数据可读，再启用V2写入口。新字段缺失拒绝只针对新命令，历史读取和旧成功回放不受影响。

### Phase 3 — 处置语义与交互

先保持SCRAP/原节点REWORK闭环并做即时/延后聚合；随后在同一质量事件链内补上游目标、退回决策预算和查询。Phase3不能缩减成只换下拉标签；RETURN_OPERATION/WAREHOUSE及上游返工是实际服务/事件扩展。L节处置终结和恢复边界确认后，才启用这些决策。

### Phase 4 — 自动回归与真实验收

| 层级 | 必须覆盖 |
|---|---|
| 单元/契约 | PROCESS/MATERIAL/BOTH桶；类型归属；类别覆盖；默认改变后原UUID重放；新旧指纹兼容；完整快照/BigDecimal精度 |
| 真实Service/XML事务Fixture | 单条多次申报、同工序独立/重叠/待核实、部分多次分配、开始/结果/修正、并发超预算拒绝、聚合失败整笔回滚；旧CREATE assessment授权旁路 |
| 数量不变量 | 原REPORT不变、D只扣一次、失败不再扣、R不超过原损失；上游返工仅恢复原归属损失、不新增正常报工；晚申报的历史超额投影保持 |
| 历史/数据库 | 新迁移不改旧校验和；旧类型未知、类别原值保留；触发器仍在；旧name/status变更后历史稳定；已处理申报不得普通改删 |
| 放行/仓储 | 返工后不自动放行；原basis失效需重新放行；退仓决定前后库存/动作不变；既有Receipt/Acceptance/严格完工/取消后受控处理保持 |
| 权限/分库/来源 | 同ID不同厂类型/不良隔离、无权403、切厂迟到响应；LOCAL/EXTERNAL模式与来源矩阵、配置异常失败关闭；不使用真实ERP调用替代本轮范围 |
| 前端 | 类型候选随工序清理、默认类别展示/覆盖、即时/延后、部分处理、待核实/已报废/返工/退回筛选、原UUID未知恢复、分页重叠候选；实际浏览器与窄屏另列 |
| 真实验收 | 用明确授权的现有PROD及绑定，在备份/冲突检查后经正式API准备标记样本；Schema/数据/API/UI分层记录。复演V1.6核心数量及V2.3 C03/C04/C11、来源/并发场景，不改已有事实 |

可复用测试源码：[ProductionDefectContractTest](/Users/user/work/lixin/mes-base/mes-business/src/test/java/dev/mesbase/business/quality/ProductionDefectContractTest.java)、[ProductionQualityV13HttpTest](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityV13HttpTest.java)、[ProductionQualityLifecycleHttpTest](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleHttpTest.java)、[QualityReleaseHttpTest](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/QualityReleaseHttpTest.java)、[LocalFullChainV23HttpTest](/Users/user/work/lixin/mes-base/mes-admin/src/test/java/dev/issuefixture/LocalFullChainV23HttpTest.java)。前端复用productionDefect/qualityTreatment/productionReport相关tests。均已核对存在及行为断言，本轮未运行；实施时更新受影响旧入口测试，不保留失真兼容断言。

实施后的标准检查为项目要求的定向测试及`mvn -B -ntp verify`、Node22前端test/build；涉及打包再跑packaged smoke。自动化通过不能替代真实目标API/UI或EXTERNAL验收。

## L. 需用户决策的问题

不再询问已确认的Operation 1:N、单条申报、检验可选、三类类别、不改Quantity算法和退仓不动库存。以下是现实现与新目标之间尚需明确的细节，本文给出推荐默认方案供下一轮采用：

1. **谁可确认及覆盖类别？** 推荐普通create只申报，现有revise作为首版质量核实/处置准入；类别覆盖另设waste:override，配置管理单独授权。若拆出assess/treat专用权限，需同时治理旧CREATE assessment入口，且不自动给角色增权。
2. **退回决定是否完成处置？** 推荐退仓决定只完成“决定”，不代表实物已处理或允许正向交接；纯退工序同理。需要确认哪些退回量可退出待处置、哪些仍阻止放行/完工，以及以后如何受控撤回/转返工；不能凭字段名直接放行。
3. **上游返工的恢复确认点。** 推荐实际目标可为已过上游节点，可信RESULT只恢复原不良损失，授权确认人需对恢复依据负责；不自动证明中间/末道加工已重做。若业务要求逐道返工路线，则另立任务，不能修改原锁定DAG或原Quantity公式。
4. **新“当前工序”与旧发现/影响节点。** 推荐V2现场同一实际node映射为affected/discovered，旧两者不同记录原样保留。旧退工序规则明确按discovered上游；新同一node上下文无歧义，管理历史入口不可静默改成affected基准。
5. **工废/料废类别（已确认）** 新旧申报复用既有必填类别，扩展BOTH；不新增平行模型，不修改历史。类别与处置方式及仓库去向独立。

这些问题不阻止Phase1配置准备；影响权限和处置生效的部分应在对应Phase开放前明确，不以暂定值伪造历史或启用有歧义的正式动作。

## M. 最终建议与交付边界

**CONDITIONAL GO。** 现有单条不良账本、独立/重叠核实、不可变报废/返工事实与统一数量服务具有可复用基础；PROD真实Schema和存量事实支持该判断。最小路线是“一个工序子配置 + 原不良版本增量 + 原处置事件增量 + 现有页面上下文简化”，不需要另建质量平台。

条件为：类别复用既有语义且历史原值保留、服务端类型归属与共同授权、事件级处理目标及单一预算、退回不冒充实物流转、原UUID和不可变历史兼容，以及分层回归通过。当前四种目标处置尚未一一形成正式能力，不能把登记去向写成已实施RETURN_OPERATION/RETURN_WAREHOUSE，也不能把当前LOCAL证据外推ERP。

本轮交付A～M与差异矩阵、数据/API草案、实施分期和决策项；只新增本报告及忽略目录中的脱敏读取证据。未修改业务代码、Schema、配置、测试数据；未运行写API/SQL、测试构建、部署或服务启停。现有dirty文件逐文件哈希复核另存同证据目录，报告创建前基线不变。

## 2026-10-21：不良品管理页面执行逻辑与交互复审（只读）

范围：用户现有5173的 `/quality/productionDefect`，沿用 TEST_DEV_PERF_20261019；未切厂，未创建/订正/作废申报，未提交处置，未访问ERP。使用 Product Design audit 流程查看真实页面，再核对当前前后端源码。服务起初不可用，用户自行重启后恢复；Agent未启动、停止或重启服务。不是SIMPLE_B数据库验收，不以旧报告或先前截图作为本轮界面证据。

### 实际步骤和证据

截图保存于 mes-base-ui `.local/quality-flow-audit-20261021/`，均已打开核对。加载中的截图被排除。

1. 列表读取（01-list.png）：可读取，显示14332项；交互健康较差。首屏长UUID、Execution数字ID、版本、申报动作主导，无工单/产品名称、处理状态及待处置/返工余额。页面有两个新增入口，正常申报与历史兼容申报并列；详情/历史、处置/返工两个按钮由同一个 showHistory 处理。
2. 新增申报入口（02-new-declaration.png）：窗口可打开/关闭，没有提交。起点只要求执行单元和实际工序，所需身份信息依赖额外选择；未进一步选择执行、类型或填写数量，不宣称完成现场申报验收。现场新增通过V2类型化声明，历史兼容通过旧Facts命令，两者业务约束不同，不能由用户靠按钮名称猜。
3. 已有记录处置（03-treatment.png）：可读取首条真实申报，数量41，已报废41、返工0；列表仍只显示“申报”。详情将不可变版本历史、处置汇总、编辑表单和事件表放进同一长弹窗。当前未恢复不良41表示仍是损失，并不表示还有41待处理，窗口没有优先展示已存在API里的 pendingDispositionQty / pendingStartQty。
4. 处置动作选择（04-action-options.png）：已报废记录仍默认“分配报废/返工”，下拉仍列开始返工、录入结果、撤分配、取消开始量、订正结果。本样本历史去向不是SAME_NODE，确认按钮被禁用；字段与操作选项仍呈编辑态，解释与可用性脱节。仅打开下拉，未修改或提交。
5. 独立查询统计（05-statistics-error.png）：主列表有记录时汇总最初显示空表/0；手动点击查询汇总后本次出现“连接失败或超时，请重试”。此为本次运行错误证据，不证明总数为0或查询算法错误，也不据此断言数据库慢查询根因。

### 按影响排序的发现

- **高：历史兼容提示与后端准入冲突。** QualityTreatmentPanel对所有 destinationType != SAME_NODE 的记录统一提示“历史退工序/退仓登记，返工前管理员订正”；UNSPECIFIED也被归入该文案。本次样本显示“已报废”且已有ALLOCATE事件。ProductionDefectService.execute在订正/作废前调用QualityTreatmentService.assertMutable，而后者拒绝同申报的任何非ASSESS事件。因此对这类已处理记录，提示的“先订正再返工”路径会被服务拒绝。该拒绝为当前源码证据，未在真实业务上试提交；不能为了让提示可执行而放开事实保护。
- **高：管理列表隐藏了处理状态。** 申报/订正/作废是版本动作，与未处理/已报废/返工中无关；本次41全部报废的记录在列表依旧显示申报。用户不能从列表识别下一步，也没有处理状态筛选。
- **高：当前状态没有收敛可执行动作。** QualityTreatmentPanel.load及提交成功后，除UNVERIFIED外统一选ALLOCATE；独立不良统一展示六种处理选项，不根据可分配/可开始/可确认余额和现有父事件收敛。后端有额度/父事件/状态校验，前端却把选择成本交给用户，常在填写后才知道不可做。
- **中：业务身份被技术身份取代。** 列表首列UUID占280px，Execution为数字ID和英文标题；普通用户看不出哪张工单、哪个产品。应先显示工单号、产品、执行编号及工序，UUID与版本放详情审计信息。不能推测不存在的产品/单位映射，需复用真实执行关联投影。
- **中：两个入口复用同一混合弹窗。** index.vue中的详情/历史和处置/返工都调用showHistory；读者与操作者都看到相同长表单。新增/历史兼容并列又让正常登记路径不唯一。建议保留一个主要新增入口，兼容/订正/作废降为明确的管理员操作。
- **中：申报版本和处置事件混用“历史”。** mes_production_defect负责“申报内容及版本”，mes_quality_treatment负责“每次实际处理事件”。两者保留是必要的，当前窗口没有清楚区分“这笔不良现在怎样”“本次做什么”“过去发生了什么”。保留现有事实Owner，不另建一套质量引擎。
- **中：列表、统计不是同一查询快照。** DefectStatisticsPanel仅接收executionId，不接收workorderId；统计的日期/类别/核实状态也不作用于主列表。统计watch只清理，不自动load；申报保存只load主列表，处置changed只loadHistory，不更新统计。尚未执行真实写入验证，但源码已证明没有统一刷新链。不能将这一缺陷表述为已证明统计值计算错误。
- **中：核实重叠的候选受当前页限制。** QualityTreatmentPanel.relatedOptions仅从父组件当前rows中取同执行未VOID项；主列表每页20条，同执行其他页的原声明无法成为候选。候选也未提前筛为已核实独立不良，后端另行拒绝非法关联。该项是源码证据，未切换业务数据制造重叠操作。

### 当前业务主线（源码事实）

现场登记执行/实际工序/不良类型/数量 → 核实是否为独立不良或与已有记录重叠 → 独立不良分配直接报废或所属节点返工 → 开始返工 → 确认恢复与失败报废 → 只读查看事件。重叠观察不再重复扣减。管理员订正/作废、撤回未用分配、取消未加工开始量、订正返工结果是受约束的异常操作，不应与普通主线并列。历史来源与已处理事实继续按现有保护规则，不为了UI简化改写。

例如申报10，直接报废3、安排返工7，返工完成后恢复5、失败报废2：应展示累计已报废5、已恢复5、待处置0、待开始0、返工中0，损失仍是5。不能把“当前未恢复不良5”误解释为还有5个未处理，也不能把恢复另记正常报工。

### 建议的页面组织（未实施）

不良记录 / 处理流水 / 统计三个职责明确的视图；记录列表优先工单/产品/工序、申报数量、待核实/待处置/待返工/返工中/已报废/已恢复等数量与状态，历史记录明确只读。单笔详情先给数量去向概况，再按当前状态提供明确动作，再给只读处理流水与申报版本。待核实只给核实；待处置给确认报废/安排返工；待开始给开始返工；返工中给记录结果；已完成默认只读。管理员纠错进“更多”，动作可用性依据业务服务规则及实时事实，最终校验仍由后端承担。

### 可访问性和验证边界

实际输入有标签、弹窗有标题及关闭入口；列表四个紧邻的小链接、长ID换行、长弹窗及不可用按钮原因不清晰增加识别/键盘操作成本。未执行完整键盘、读屏、对比度或WCAG验收，不把截图审查当作合规证明。未验证正常新申报写入、部分返工、纠错、事务回滚或ERP。未修改业务代码/配置/SQL，未运行编译和测试；本轮只追加本节审查与本地截图，保护其他未提交修改。

## 2026-10-21：按用户确认收敛为返回申报节点 / 转仓入库（实施）

本节替代上节“建议的页面组织（未实施）”中普通操作者继续执行分配、开始、结果确认的建议。用户明确两种新处置：返回本申报影响节点后走正常生产报工；选择指定仓库生成正式入库单，仓库用途及最终处理由业务/ERP决定。不增加库存隔离维度、不选择其他返工节点、不把转仓等同报废；既有版本、旧事件及其历史语义保留。

### 调用链与数量规则

- 新入口 `POST /api/quality/defects/{declarationId}/dispositions`，权限 `business:quality:defect:revise`。Controller只做协议映射；QualityDispositionService持有businessTransactionManager / READ_COMMITTED事务，复用ProductionChainLocks按工单→执行锁定，核对申报版本、实际锁定路径、独立归属、待处置余额、工单/执行状态及BusinessSourceRules。请求只接收UUID、expectedRevision、destination、quantity、warehouseId、reason；不接收可信操作者、产品或处理时间。
- RETURN_NODE：不可变质量事件记录`rework_qty`，同时在ProductionReportService追加关联`source_quality_event_id`的负ADJUSTMENT，不更新/删除原报工。按节点一次有界读取原报工及订正，内存分配、一次批量写入；未覆盖金额则整笔回滚。原直接不良的在制扣减贡献同步减去返回量。例如原报工100、不良10、有效90；返回10后净报工90、在制不良0、有效仍90，正常报工10后有效100。返回本身不增加合格产出，也不绕过下游事实保护。后续产生新的不良需正常新申报。
- TRANSFER_WAREHOUSE：事件`transfer_qty`与`target_warehouse_id/stock_document_id`关联已过账的正式OTHER_RECEIPT。复用IWmInventoryService创建零余额库存来源、StockDocumentService创建/明细/过账、LocalStockService管理余额，全部处于同一业务事务；不直接改余额，不把它标成合格生产成品入库。库存批次沿用执行编号，产品和单位来自可信执行/物料，用户只选目标仓。需额外拥有`business:wm:stock:document:create/post`；工单/仓库/当前模式必须同来源。
- EXTERNAL仓库目前没有已实现的ERP入库命令，明确报INTEGRATION_NOT_CONFIGURED并且不产生本地入库/镜像/质量成功事件。未开发真实ERP入库，不调用ERP、不自动降级LOCAL。该边界不能表述为ERP转仓已完成。
- 新事件UUID与规范请求及可信操作者摘要绑定；相同UUID同内容只返回原结果，不重复入库/回退报工，不同内容拒绝。前端结果未知时保留原请求供人工重查，不自动生成新UUID。旧端点及历史事件的兼容逻辑保留，页面不再产生旧的分配/开始/结果/撤回/订正命令；已有待确认旧命令仍原样调用原端点。
- 服务端记录新处理的时间和操作者。旧Command中的客户端时间仍参与原指纹核对以保留重试兼容，但不再作为新事实的处理时间。
- QualityTreatment汇总及统一ProductionQuantityService使用“返回量减少活动不良，负报工同步释放额度”的同一事实；DefectStatistics当前净不良减去返回量。工单进度独立展示`qualityTransferredQty`，未处理剔除返回/转仓量；转仓不累计到报废，未完成扣除已转仓量，返回量仍等待正常报工产出。

### 前端与文件职责

QualityTreatmentPanel详情拆成数量概况、本次处理、处理流水、申报版本；概况突出待处置/已返回/已转仓，旧数量折叠。普通独立不良只展示两项处置，转仓复用WarehouseSelect；取消日期录入。流水保留处理方式、数量、操作人、时间、原因，审计ID/旧细分数量/入库单和目标仓库ID放展开行；日期复用parseTime。主页面文案与新语义同步，详情和处理入口进入对应标签，历史去向不开放新改派。旧历史并未自动转换为新的正式转仓。

主要后端文件：QualityDispositionController/Service新增处置用例；QualityTreatmentController/Service/Models负责事件及查询；ProductionReportService及Mapper负责不可变报工回退；WorkOrderProgressQueryService/ExtraMapper、DefectStatisticsExtraMapper调整查询口径。V47新增字段及索引，MesQualityTreatment与MesProductionReport的Entity/Example从真实迁移后metadata经Codegen Skill生成并采用，带自定义查询的XML合入生成部分且移除生成归属标记；不暴露新生成的基础CRUD写入口。

### 数据库执行与证据边界

起初误沿用了AGENTS旧SIMPLE_B验收库约定，已向用户明确披露并在用户指定后改用PERF；不隐藏或倒退该已执行操作。SIMPLE_B通过正式Flyway从V37执行至V47；迁移前备份Schema、触发器及受影响原列/原行，迁移后核对原事实未变。备份在`.local/quality-destination-20261021/schema-backup-1792570660878`。

本轮最终目标经中心绑定和服务器UUID校验为`TEST_DEV_PERF_20261019 → datasource 9 → 192.168.18.25:3306/mes_base_dev_perf_test`，模式LOCAL。正式Flyway从V45执行V46/V47，执行后validate成功；没有手工DDL或repair绕过历史。备份在`.local/quality-destination-20261021/perf-schema-backup-1792571278968`。比较只针对备份原主键及原列，避免将用户并行追加的新数据当作本轮变更；原有报工/质量事实核对一致。V47耗时约4分半，实际为MySQL报工表结构变更，不是另建测试库。

迁移后读取验证四个新列存在，V46/V47 success=true；用当前Mapper的完整Base_Column_List实际读取最近10行成功。EXPLAIN采用idx_quality_treatment_recorded、估计10行、Backward index scan，无filesort。真实页面仍为用户现有5173及现有后端，保持PERF选厂，已验证真实只读列表、详情标签、历史流水与日期/数量展示。截图：mes-base-ui `.local/quality-disposition-20261021/events.png`。未向用户存量业务提交新的处置或入库，不把H2闭环称为PERF真实写入验收；用户重启新版后端后才可对新接口执行运行验收。未启动、停止或重启用户服务，未访问ERP。

### 检查记录

- `mvn -B -ntp verify`：全量通过；含既有权限/分库/事务回归。新增QualityDispositionHttpTest覆盖返回后正常报工且不双计、同UUID重放与改内容冲突、转仓正式POSTED单据及库存只增长一次、超量及无效仓库拒绝、过账失败回滚零库存来源/草稿/质量事件、库存权限与来源不匹配拒绝、并发仅一笔消费成功。WorkOrderProgressHttpTest补充返回/转仓不算报废且未处理清零的真实SQL Fixture验证。
- 新增字段使LegacyBatchPersistenceTest的独立H2表定义缺列，已同步至当前契约；新业务代码枚举硬编码由现有BusinessEnumContractTest发现，已改用现有枚举。失败没有通过放宽校验绕过。
- UI `npm test`：72文件、418测试通过。更新了失效的旧六步提交测试，保留未知请求、原UUID、切厂/撤权迟到响应和旧请求原样重放覆盖；不是删除安全边界来维持通过。
- UI `npm run build`：通过；保留既有大chunk构建提示。`/usr/bin/python3 scripts/smoke-packaged.py`：10通过、0失败（独立打包检查，不连接开发库，不操作用户服务）。
- 未提交/推送代码。已有无关dirty工作保留。回退业务代码时需保留V47迁移和不可变事实，禁止删除新事实或降Schema；新处置落地后不得通过回退旧代码解释/改写新事件。

收尾复核：混合旧流程未完成返工与新处置时，状态优先保留旧的IN_PROGRESS / WAITING_REWORK，不能因未分配余额已返回/转仓而误显示全部完成；增加对应HTTP Fixture。前端删除不再使用的旧动作状态和六步表单字段，保留冻结Command原样重放。最终前端全量仍为72文件418测试通过，构建通过；新增反例明确要求4xx（过账失败为409），不能将500当作正常业务拒绝。

最终回执：16:43 的 `mvn -B -ntp verify` 再次BUILD SUCCESS，其中mes-admin 582项、0失败、0错误、48项需外部环境的测试跳过；QualityDispositionHttpTest 7项全部通过。最后一次打包smoke结果仍为10通过0失败。真实库证据仅含上述绑定、正式迁移、存量核对和只读查询/页面，不将跳过的真实业务写入验收表述为已通过。

## 2026-10-22：对照 ERPNext 官方源码复审详情概念（未实施）

用户明确参考仓库为 frappe/erpnext。以正式发布标签 v16.52.0、提交 `90cb7f9243048a44311777d60a1cec8ca05ceec9` 为本节源码基准，避免将develop移动分支或同名NextERP分叉当成官方实现。只通过GitHub只读API取得指定文件，未安装或运行ERPNext。证据清单与用户提供截图副本位于`.local/erpnext-quality-review-20261022/`；截图只包含“已报废 / 核实依据”的局部，以下页面判断同时以当前QualityTreatmentPanel源码佐证，未进行新的完整浏览器流程/可访问性验收。

### 官方源码事实

1. Quality Inspection：`erpnext/stock/doctype/quality_inspection/quality_inspection.json`定义来料/出货/过程检验、关联业务单据/物料/批次、检验明细、检验人、备注及Accepted/Rejected/Cancelled状态。`quality_inspection.py`的on_update/on_submit按Stock Settings更新质检引用；不能从Rejected推出已报废或已转仓。`erpnext/controllers/stock_controller.py`的validate_qi_submission/validate_qi_rejection明确按配置Stop或Warn，不是无条件所有单据拒绝。
2. Non Conformance：`erpnext/quality_management/doctype/non_conformance/non_conformance.json`维护问题主题、程序、详情、纠正措施、预防措施和Open/Resolved/Cancelled；Python Controller没有库存过账编排。它是程序不符合项/问题追踪，不能直接当作本项目逐笔不良数量申报与库存交易的等价物。
3. 库存动作：Stock Entry负责实际收/发/转移库存；Purchase Receipt存在accepted/rejected quantities与rejected warehouse。官方文档分别说明Material Receipt与Material Transfer，以及采购收货中拒收数量的分仓过账；采购场景不能直接等同于生产报废闭环。来源：https://docs.frappe.io/erpnext/stock-entry 、https://docs.frappe.io/erpnext/purchase-receipt 。
4. 纠正作业：Job Card的JS通过明确的Corrective Job Card按钮打开纠正工序选择；Python `make_corrective_job_card`创建独立纠正作业卡，保存for_job_card/for_operation，设置is_corrective_job_card并重置新卡工时/数量等。普通Job Card数量校验将纠正卡排除；另有纠正工序成本累计。这与本项目已获确认的“返回节点后正常报工”模型不同，不能宣称本项目负ADJUSTMENT释放额度就是ERPNext原生实现。本次不擅自切换业务模型。

可复核源码永久链接：
- https://github.com/frappe/erpnext/blob/90cb7f9243048a44311777d60a1cec8ca05ceec9/erpnext/stock/doctype/quality_inspection/quality_inspection.json
- https://github.com/frappe/erpnext/blob/90cb7f9243048a44311777d60a1cec8ca05ceec9/erpnext/controllers/stock_controller.py
- https://github.com/frappe/erpnext/blob/90cb7f9243048a44311777d60a1cec8ca05ceec9/erpnext/quality_management/doctype/non_conformance/non_conformance.json
- https://github.com/frappe/erpnext/blob/90cb7f9243048a44311777d60a1cec8ca05ceec9/erpnext/manufacturing/doctype/job_card/job_card.js
- https://github.com/frappe/erpnext/blob/90cb7f9243048a44311777d60a1cec8ca05ceec9/erpnext/manufacturing/doctype/job_card/job_card.py

### 当前页问题及建议（尚未改代码）

- 用户截图中的“已报废”是Summary.state文案；“核实依据”是ASSESS.basis，即是否独立/重叠申报的判定说明，不是化验/检验标准，也不是报废原因。当前把它做成顶部全宽可折叠块，既抢占视觉层级又没有揭示具体业务含义。建议放到处理记录或审计明细，以“申报核实说明”表达；申报原因及实际处置原因分别展示，不能相互替代。
- “旧流程数量记录”来自allocatedScrapQty/allocatedReworkQty等旧动作汇总，不是普通操作者需要理解的新业务模块。历史不可变事实须保留，但默认概况应按真实语义呈现“历史已报废”等已确认数量，完整旧动作留在处理记录；不能把尚未使用的分配、旧返工完成、成功恢复等全部简单合并为已返工或已报废。
- “本次处理”是写入新事件的表单，不是已有的一条处理记录；名称缺少对象，且当前未按pendingDispositionQty=0隐藏表单，完成记录仍可能给人可继续处理的错觉。建议详情默认只读，在有待处理数量且后端状态/权限允许时显示“处理不良”按钮，点击进入“选择返工/转仓、数量、目标仓库（转仓时）、原因”的短表单。已完成展示明确结果，不留下空的操作标签。
- “当前净损失”实际是生产数量传播口径defectQty-recoveredQty-returnedQty，不是最终实物报废数量，转仓仍可能包含其中。建议不作为普通详情主指标。概况突出申报、待处理、已退回返工、已转仓及确有历史依据的已报废；“已退回返工”不能写成“返工完成”。
- 建议组织：详情显示工单/产品/工序/原因等基本信息、数量去向和处理记录；版本记录放审计入口。保留两种用户确认的新处置，不照搬ERPNext完整质量管理模块，也不据此新增质量审批平台或改变库存模型。

本节只追加审查记录及本地来源清单/截图副本；没有修改业务代码、配置或SQL，没有业务写入、构建测试、ERP调用或服务启停。产品设计上下文preflight显示没有已保存上下文，本轮依据用户明确提供的项目、截图及仓库。局部截图和源码不能证明完整键盘/读屏/响应式行为，也不能当作ERPNext部署界面验收。

## 2026-10-22：新版不良记录页面实施与验证

本节取代前文关于继续提供历史兼容申报入口、恢复旧命令格式和展示旧流程数量的实施建议。用户明确：当前产品处于开发阶段，外部导入生产数据用于验证产品，应由导入/转换过程适配当前模型；不要求产品为导入数据保留旧交互。后端及前端根 AGENTS.md 已分别写入该规则。正常事实审计、权限、幂等和已发布迁移约束继续适用；本轮未删除业务数据或改写已发布迁移。

### 页面与调用变化

- 主列表突出工单/产品、不良情况、申报工序、数量、处理状态和申报时间；只保留一个“登记不良”入口。查询复用 QueryForm 原生 submit，扫码入口继续使用既有组件。
- 详情默认只读，显示基本信息、申报/待处理/退回/转仓数量及处理时间线；版本和技术标识折叠到“操作留痕”。核实与处理通过明确按钮打开短表单，已完成或停产对象不显示处理按钮。取消旧六种动作选择和旧返工加工/恢复/失败数量表格。
- 本次质量操作只提供退回申报节点、转入指定仓库。时间与操作者由后端记录；退回后的产出使用正常报工，转仓继续复用正式库存单据链。页面不把“已退回”写成返工完成。
- 前端只使用当前 ASSESS/RETURN_NODE/TRANSFER_WAREHOUSE 调用与新的待确认请求键，不恢复旧命令格式。结果未知仍冻结原请求/UUID，切厂、撤权、迟到响应及脏表单保护保留。
- 首页共享处置流水同步显示核实、退回返工、转仓数量及入库单，移除五段旧返工数量列。
- 列表后端增加受分页结果限定的工单/产品/状态批量投影与处置汇总；先关闭 PageHelper，再做辅助批量查询，不按行读取。增加 HTTP Fixture 验证新字段、退回状态与工厂隔离。

### 验证记录

- `mvn -B -ntp verify`：BUILD SUCCESS，当前 Surefire XML 合计583测试、0失败、0错误。日志 `/tmp/quality-page-20261022-verify.log`。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10通过、0失败；独立打包探针，不操作用户开发服务。
- Node22 `npm test`：73文件、424测试通过；`npm run build`通过（既有大包告警保留）。最终增加缺生产上下文时隐藏操作的反例，并重新完成全量测试和构建。
- 已核对真实绑定：TEST_DEV_PERF_20261019 → datasource9 → 192.168.18.25:3306/mes_base_dev_perf_test，LOCAL；服务器UUID核验通过。以实际Mapper XML提取的参数化SQL只读读取20条记录，20条均关联工单。未新增SQL迁移、未写入业务数据。
- 用户已重启开发后端，但随后实际5173查询返回服务不可用；8081监听进程的直连也被重置，未获得重启后正常HTTP响应。已请求用户在构建结束后重新启动，再继续浏览器列表/详情验收。不得将上述Fixture、构建或只读SQL证据当作完整运行验收。
- 未访问真实ERP。EXTERNAL转仓正式入库适配仍未配置，保持明确失败，不伪装LOCAL成功。旧后端动作处理与导入数据转换不是本次页面改版完成项；旧导入状态不会被页面猜测为新处置结果。

本轮运行阻碍截图：`.local/quality-page-20261022-runtime.png`。当前仅确认页面结构已热更新，未将错误空态当作无业务数据。

### 2026-10-22 08:17 用户再次重启后的运行复验

用户重启后的进程68235正常监听8081，`/health`返回200/UP。刷新5173页面会话后，不良列表及详情读取正常，工单/产品批量投影已在实际页面显示；登记不良窗口可打开，空表单禁止提交，可正常关闭。未提交任何业务写入。截图：`.local/quality-page-20261022-restarted.png`、`.local/quality-page-20261022-detail.png`。此前“连接重置”的运行阻碍已解除，不能继续沿用为当前状态。

当前PERF库只读复核：15598条声明的command_version均为NULL；处置事件包含ASSESS15596、ALLOCATE15582、START8、RESULT8，没有RETURN_NODE/TRANSFER_WAREHOUSE样本。首屏旧导入记录因此显示“状态待确认”，详情旧动作显示“未识别的操作”，没有开放新版处理按钮。本轮不把旧分配转换为新转仓或退回结果，也不为验收修改已有生产事实。当前运行证据覆盖读取与登记窗口开闭；新版两种处置仍以此前HTTP Fixture/UI测试为证据，尚未形成此库新版数据的浏览器提交闭环。此数据准备缺口应在导入/验证材料侧处理，不能以产品历史兼容分支修补。
