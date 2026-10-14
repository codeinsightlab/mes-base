# ADR-007：仓储领域 Adapter 与领料作废

2026-10-14，Accepted，用户任务明确授权。沿用中心库加工厂业务库；本决定只引入 HTTP/业务 Port，不恢复已退役的第三数据库框架。

当前 Base 领料 DRAFT/RECORDED 仅表示本地管理资料，既有 Service 没有外部出库调用，不能将 RECORDED 当 ERP 成功。历史来源按 UNVERIFIED 保留，禁止自动本地作废。

采用 WarehouseErpAdapter 的 cancelMaterialIssue 与 queryMaterialIssueCancellation。后者有真实 VOID_PENDING/UNKNOWN 消费者，仅查询稳定操作身份，不重复冲销；“未找到/仍处理中”必须 UNKNOWN，REJECTED 必须是已确认终态。未来 ProductionErpAdapter 在出现生产用例时新增内聚方法，当前不建空接口或厂商实现。

按 `mes.integration.warehouse.factories.<FactoryId>.mode` 显式配置 LOCAL_ONLY/CONNECTED，缺配置拒绝。仅 SIMPLE_B 本地开发配置显式 LOCAL_ONLY，默认/生产不回退。CONNECTED 原连接由单据 external_binding_key 和 Adapter supports(scope,key) 验证，不能用当前 LOCAL_ONLY 覆盖原外部来源；无唯一匹配实现失败。

本地创建记录只在已明确 LOCAL_ONLY 时保存 source_execution_mode=LOCAL_ONLY/external_effect=NONE；其他行政 CRUD 不伪造真实出库影响。未来真实外部出库消费者必须在其事务中保存 CONNECTED、确切 binding/reference 与 CONFIRMED/UNKNOWN。现有普通命令不可写来源字段，无人工核实壳接口。

准备事务认领单据，保存 UUID 操作及原状态，进入 VOID_PENDING；外部调用在事务外；落地事务同时验证操作身份和 pending/unknown 状态。SUCCEEDED 变 VOIDED：LOCAL_ONLY 保持 external_effect=NONE，CONNECTED 确认外部撤销才为 REVERSED，REJECTED 恢复有效原状态且记录拒绝，UNKNOWN 保留待核实并阻止产品变更。重复 cancel 不重发操作；pending/unknown 只允许 query 核实。进程崩溃或外部成功后本地提交失败也保留 pending 操作，可以查询同一身份恢复。

WorkOrder 行锁串行化产品变更和领料关联、作废，存在非 VOIDED 或未确认 SUCCEEDED 及与来源一致的影响事实（LOCAL_ONLY/NONE 或 CONNECTED/REVERSED） 的领料时拒绝产品变化。作废历史单据保留原产品快照，工单后续订正不重写它。Execution 与旧 Batch 的历史身份约束独立保留。普通单头和明细写入、删除不允许绕过作废，权限为独立 business:mes:material:issue:void。

默认本地成功显式 LOCAL_ONLY，不冒充 ERP。Adapter 不访问 MES Mapper/事务，不判断厂商于业务 Service。无外部供应商配置、注册中心、任务平台、Reporting/Receipt/WIP 或跨系统原子性承诺。

验证与未验证边界记录于对应实施报告。TEST ONLY Adapter 只在 src/test，不能代表真实 ERP 联调。CONNECTED 来源历史核实、被拒绝后的新操作授权重试、供应商真实连接与响应转换属于后续真实接入；当前重发同一作废请求始终幂等返回原结果。


## 2026-10-14：ERP领域规范与Extra迁移修订

前文V18外部事实保存在领料主表的结构由V20取代，V19保留历史纠正。V21同时迁出工单旧外部单据身份；原表Entity少量兼容投影从Extra填充，主表Mapper不映射外部事实。复用WarehouseErpAdapter作为仓储领域业务接口，LocalWarehouseErpService提供明确本地结果；厂商领域实现可编排其内部流程，Gateway仅通讯，不写MES库。新增只读领料查询和独立权限核实，通过Extra operation_version及MES操作身份拒绝迟到结果；已作废不自动恢复，冲突保留为待处理事实。工单/领料删除同步维护Extra生命周期。

原取消操作继续按原绑定唯一匹配厂商实现，不将原CONNECTED单据自动本地作废；新的领料业务状态查询要求当前provider与原来源一致，跨provider历史迁移不在V1范围。无真实供应商、继续审核或完整出库提交入口，测试替身不能证明ERP联调。实施及最新验收边界见[Report A](../reviews/2026-10-14-erp-domain-integration-review.md)。原文旧测试/界面验收保留其时间点，不替代本轮真实HTTP/UI结果。
