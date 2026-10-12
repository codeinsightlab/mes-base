# MES Business Gap

> 当前分类见本文第二轮章节及[MES_CAPABILITY_REVIEW](MES_CAPABILITY_REVIEW.md)。
> 第一轮四项记录不是四个默认必建领域；本轮只重审必要性和缺失证据。

2026-10-12；仅登记，当前不实现，不做方案设计。
GAP是具体通用能力的缺口，不能把Source已有质量、WIP查询或追溯整体说成缺失。
以下判断来自当前ktg-mes主模块的表清单、Controller地图及相关调用链；未访问现场数据库，
未审全部外部系统契约，若后续找到明确实现，应回写Source Status。

| Capability | Why Needed | Source Status | Priority | Dependency | Notes |
|---|---|---|---|---|---|
| Routing Version / 已下达路线冻结 | 工艺变更不能无依据改变在执行工单的顺序/判定历史 | pro_route/pro_route_process为可编辑当前关系；ProRoute实体无业务版本，工单路线冻结契约未找到 | P1 | Routing、WorkOrder | GAP：已审范围未找到明确版本发布/冻结能力，不把技术连接revision当工艺版本 |
| 通用WIP数量与状态闭环 | 同批次/工序在制、待转、合格、不良及入库需可核对 | 有报工材料剩余量及SimulationWip聚合展示；未确认独立、统一的WIP状态/数量权威及闭环 | P1 | Reporting、Routing、Warehouse | Source已有局部在制能力；差距是统一语义，不能直接迁展示表当完整WIP |
| 通用批次谱系：多级分拆/合并/转换 | 追溯应解释材料到产出及批次身份变化，而非只找一阶工单 | ProcessTraceabilityMobController处理当前/转换后一阶；断接与热处理有专用关联；未找到通用多级分拆合并谱系契约 | P1 | Material、Reporting、Transfer、Quality | 不删除现有专用追溯；此处只登记通用闭环缺口，复杂查询性能/范围后续验 |
| 不合格处置与验证关闭闭环 | 判定不通过后需追踪处理与最终关闭，避免仅保存最后结果 | inspection_record结果覆盖/首次通知、QC不良申报、部分复检标识已存在；未确认统一处置→复检验证→关闭的状态链 | P2 | Quality Workflow、Reporting | GAP为已审链路的缺口，不能声称Source无复检或无Quality；不预设处置方式/表结构 |

证据入口：[静态清单](SOURCE_BUSINESS_EVIDENCE.json)；
`pro/domain/ProRoute.java`、`pro/service/impl/ProRouteProcessServiceImpl.java`、
`dashboard/service/impl/SimulationWipServiceImpl.java`、
`pro/controller/mobile/ProcessTraceabilityMobController.java`、
`md/service/impl/InspectionRecordServiceImpl.java`。
路径相对于`/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/`。

下一轮先复核这些差距是否被其他Source入口覆盖；发现GAP不授予开发、建表、生成或外部集成权限。

## 2026-10-12 第二轮：GAP Necessity Review

分类是当前Base范围判断，Source Status另列；不能把Source局部实现说成整个能力不存在。
四项均不在本轮实现。参照OPC Foundation的[ISA-95活动边界](https://reference.opcfoundation.org/specs/OPC-10030/4.2.3)，
基本执行需要哪些事实由项目具体闭环决定，不意味着所有Level 3管理能力默认合并到Base。

| Capability | Gap Class | Base最小需要 / 高级边界 | Source Status与证据 | Priority / Dependency | 当前处理 |
|---|---|---|---|---|---|
| Routing Version Freeze | COMMON_GAP | 执行采用哪份工序依据、事后能回查是基础正确性；完整版本发布/审批/多版本路线管理不是最小Core必备模块 | ProRoute/ProRouteProcess及Mapper未确认业务版本/冻结字段；报工实时读路线关系。工单部分字段快照已存在，不能说完全无快照 | P1；Routing、WorkOrder、Reporting | 保留差距，缩小为可回查执行依据；不预建工艺版本套件 |
| Unified WIP | CORE_GAP | 仅限基本数量/工序状态口径：下达、累计产出、剩余可报、当前工序/完工须相互可解释；跨仓位全守恒台账、复杂缓存投影与通用库存账不是默认Core | ProReportingWorkServiceImpl.executeMaterial扣当前acceptedQty、累计下序并更新状态；移动入口有另一状态更新，SimulationWip做聚合。Source已具局部WIP事实，统一权威/全入口闭环未证 | P0；WorkOrder、Reporting、Routing | 保留最小语义一致性问题；不据此认定必须新建独立WIP表或全仓台账 |
| Generic Batch Genealogy | OPTIONAL_GAP | 基本执行对象→工单/工序/记录关联属于Core；通用多级分拆/合并/转换图及高级召回按行业需求 | 断接查询有前后品号/批次，热处理有相关记录；未找到已审范围的通用多级谱系契约，不否认专用追溯 | P2；投入/产出、Reporting、Transfer、Quality | 从默认核心缺口降为高级候选；不建设通用谱系引擎 |
| Quality Disposition Loop | REVIEW | 质量结果/不良数量可挂接执行；完整处置、审批、返工、复检关闭通常为可选QMS扩展 | inspection_record可覆盖结果/首次通知；传统检验和不良申报存在，另有复检标识。当前未审清所有处置链，不能认定Source完整能力缺失 | P2；Quality、Reporting；候选必要性OPTIONAL | 暂停“Source缺失”定案，先查实际流程和使用者，不因模型漂亮记必建GAP |

值得以后补的是最小执行正确性/进度/记录依据；高级版本发布、全谱系和全质量处置需具体业务需求。
“未证明”不是运行缺陷报告，也不是Source现场不存在的证据。
