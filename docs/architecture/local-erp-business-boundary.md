# Base MES — LOCAL / ERP 业务边界设计方案 V1.0

本文定义 LOCAL / ERP 的来源、库存权威及受控切换边界。生产数量与收尾规则见[生产使用说明](../usage/production.md)，单据创建和审核见[仓储使用说明](../usage/inventory.md)。规则不等于所有 ERP 命令和模式迁移流程均已交付；实际接通范围须按部署版本核实。

生产收货依据正式入库单审核及库存记账。生产来源须校验 Execution、产品、可入库数量和仓库来源；不设置独立放行。LOCAL 由唯一库存服务记账，EXTERNAL 需要 ERP 权威生效证据，未接通的 ERP 入库不能通过 LOCAL 单据假成功或回退。

## 1. 已确认业务规则

### 工厂模式与业务来源

Factory 运行模式为 LOCAL / ERP，决定当前允许的新业务体系。新厂默认持久化 LOCAL；每厂最多一个有效 ERP，类型、连接及认证引用沿用 FactoryIntegration。服务端可存在多个候选配置定义，但工厂当前仅能绑定一个有效 provider/configurationRef，不建立多 ERP 优先级或自动路由。ERP 配置缺失、错误、不可解析或接口不可用时明确失败，禁止降级 LOCAL。

WorkOrder 和 Warehouse 均须具有可信持久化来源：LOCAL 由 MES 本地创建，EXTERNAL 由当前工厂绑定 ERP 同步创建。来源由服务端可信入口赋值，普通请求不得选择或修改；同步不得改变既有来源。同编码不证明同一业务身份，禁止按编码自动合并 LOCAL/EXTERNAL。已有编码冲突须明确拒绝或经受控映射处理，不因来源拆分擅自放宽现有约束。

业务来源不是当前模式的别名，也不是库存动作的 sourceType、领料 sourceMode 或旧 externalId 非空判断。工厂切换不改变历史来源。业务表继续不含 factory_id；外部标识、映射、provider/binding 及配置版本证据沿用相应 Extra 边界。

### 正式操作准入

| 当前模式 | 工单来源 | 原材料仓来源 | 成品仓来源 | 新正式业务 |
|---|---|---|---|---|
| LOCAL | LOCAL | LOCAL | LOCAL | 允许进入既有业务、权限及数量校验 |
| ERP | EXTERNAL | EXTERNAL | EXTERNAL | 映射、连接及权威业务契约均有效才允许 |
| 任意 | LOCAL | EXTERNAL | 任意 | 拒绝 |
| 任意 | EXTERNAL | LOCAL | 任意 | 拒绝 |
| 任意 | 有效来源 | 与工单一致 | 与工单不一致 | 拒绝 |
| LOCAL | EXTERNAL | EXTERNAL | EXTERNAL | 模式不允许，拒绝 |
| ERP | LOCAL | LOCAL | LOCAL | 模式不允许，拒绝 |
| 任意 | 任一缺失或异常 | 任一缺失或异常 | 任一缺失或异常 | 拒绝，不猜测来源 |

要求是“工单＝原材料仓＝成品仓”，适用于生产领料、退料、成品入库及关联正式仓储交易。不同阶段必须使用该生产业务的可信关联和冻结依据校验；尚无所需来源或关联证据不能视为通过。历史核实/撤销是按原事实处理的受控入口，不以当前模式重新创建业务，不以本表阻断所有历史查询。

### 库存与生产事实权威

- LOCAL 仓库的权威余额与动作由 LocalStockService 管理；EXTERNAL 仓库的权威库存和仓储交易属于 ERP。本地 EXTERNAL 镜像只供检索、参考、展示和对账，不能作为正式库存操作的最终决策依据。
- ERP 同步只管理 EXTERNAL 的 ERP 权威字段，不得 UPDATE/DELETE LOCAL 记录、LOCAL 权威库存或历史库存动作。当前业务查询依赖 ERP 权威接口或真实业务命令的权威校验。
- ERP 可更新其主数据和未冻结计划字段；不能覆盖已锁定 Execution、RoutingPath、Report、Quality、Receipt 和库存事实。ERP 当前计划与 MES 历史冲突时保留历史并形成明确冲突。
- ERP 出库单创建成功不等于实际出库或可信投产；须验证对应 ERP 的业务生效契约。UNKNOWN 保留原 requestId、operationKey、冻结请求、已知 ERP 单号及响应摘要和 provider/binding；V1 不要求 ERP 支持 MES UUID 唯一检索，由授权人员人工查询 ERP 并记录原因、依据、操作者和结果。NOT_FOUND 不自动授权重新创建，可能已发送的创建不得自动重试。
- Execution、Report、Quality、ProductionQuantityService 只有一套。来源差异影响准入和权威适配，不复制生产模型或数量计算链路。

## 2. V1严格来源隔离与停旧启新迁移策略

V1 不实现 LOCAL Execution 转 ERP Execution、领料转 ERP 出库、已报工数量自动转 ERP 报工、在制品自动接续、双库存自动平衡、跨系统补偿或同一执行中途改变来源。用户接受一次性业务中断与人工重新操作。

受权迁移必须按顺序完成：

1. 停止创建新的 LOCAL 生产及仓储业务，检查未完成工单、执行、领料、报工、库存及 UNKNOWN 外部操作；不能安全终止的未决状态必须先解决。
2. 可作废业务经正式受控入口处理；已有不可变事实的业务按权限、状态和引用规则终止或保留历史，禁止直接改库强制作废。
3. 人工盘点实际库存、在制品、未消耗材料及已完成产品，保留盘点和 ERP 期初完成的明确确认依据。
4. 在 ERP 准备库存期初、工单及外部业务，通过正式同步取得新的 EXTERNAL 工单和仓库，验证当前权威接口、映射及生产准入。
5. 明确授权、记录切换时间、保留历史配置版本和原执行来源，再启用 ERP 新业务。新业务用当前配置；历史核实/撤销沿原冻结来源/provider/binding，缺原配置或适配能力必须明确失败，禁止重新指向当前 ERP。
6. 操作员依据真实事实，通过正式补录或新执行登记需要接续的活动。补录必须区分历史加工与当前新增加工、保存操作依据；重新报工不代表实物重新加工，禁止伪造重复生产或重复扣减库存。

必须保留 ProductionExecution/实际路径、MaterialIssue/冻结投入、ProductionReport/调整流水、ProductionDefect/处置、ProductionReceipt、wm_stock_action 及原外部操作、幂等和审计记录。历史可退出活跃列表，不能为了迁移删除事实或被事实引用的主数据。仅无正式事实且无依赖的普通主数据可以按既有删除/归档规则受控处理。

没有正式迁移流程时维持现有 integrationLocked 机制；普通工厂编辑不得切换已有历史的模式，解绑不解锁。现有保守锁定不是已实现迁移审批、盘点确认或历史配置归档的证明。

## 3. 复用与责任边界

- FactoryIntegrationRegistry 校验工厂当前配置；Snapshot / Context 提供 mode、ERP 类型、配置引用和必要版本。FactoryScope 继续仅含 FactoryId，不新增字典模式或全局 Resolver。
- WarehouseIntegration 配合业务 Service 校验当前模式、WorkOrder/Warehouse 可信来源、原操作来源及必要 ERP 映射/连接；ProductionErpAdapter、WarehouseErpAdapter 承接权威查询与完整业务行为。业务来源校验不得仅靠页面过滤或某一个新入口，须覆盖普通 CRUD、同步及命令的可绕过路径。
- LocalStockService 保留本地权威动作；ERP HTTP 不纳入 LOCAL 数据库原子事务承诺。现有权限、资源范围、Factory 分库、具名事务、版本/租约及不可变事实规则继续适用。
- 供应商标识、映射、配置版本归既有集成/Extra 边界；LOCAL/EXTERNAL 是 MES 业务身份。不能以“ERP 字段不进核心表”为由省略可信业务来源。
