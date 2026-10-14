# Production Core V1 Implementation Report

2026-10-14。实施输入为用户最终收口任务、当前源码、生产主链只读审查及 ERP Integration ADR。当前完成范围到标准领料驱动的 RUNNING；验证结论见 [Verification Report](2026-10-14-production-core-v1-verification-report.md)，页面复验见 [User Acceptance Guide](../development/production-core-v1-acceptance.md)。旧审查的缺口描述保留其时点，不作为当前实现状态。

## 最终模型与迁移

WorkOrder 保留生产需求；ProductionExecution 是唯一活跃执行身份，独立选择适用的已发布 Routing 版本。工单默认版本可以与 Execution 实际版本不同。ProductionBatch / BatchRouteNode 保持原只读入口，不自动转换、不新增历史写入。

正式业务迁移 `mes-admin/src/main/resources/db/business/V23__production_core_v1.sql` 已在 SIMPLE_B 指定业务库执行。没有改写 V1–V22，没有新增工厂字段、外键或业务唯一约束。

| 模型 | 字段与约束 | 业务规则 |
| --- | --- | --- |
| mes_execution_path | id 使用 Execution 主键；routing_id、path_status、revision、selected_at、locked_at | 主键保证一个 Execution 一条当前路径；SELECTED 与 LOCKED 分开；首个可信确认永久锁定 |
| mes_execution_path_node | id、execution_id、sequence_no、routing_node_id；普通顺序索引 | 只保存原版本节点引用与顺序，不复制 DAG；业务事务控制同一执行的节点更新 |
| mes_material_issue_submission | id、issue_id、path_revision、command_json、status、created_at、finished_at；普通 issue 索引 | 尝试主键派生稳定操作身份；完整通用命令不可变保存；历史尝试不删除 |
| mes_material_issue | 新增 nullable submission_id，status 扩为 varchar(24) | 旧数据为空引用；核心表不保存外部单据身份 |

表与所有新增字段均有中文 COMMENT。真实 metadata 经 `.agents/skills/mes-crud-codegen/SKILL.md` 和 `scripts/mes-business-codegen` 生成三张新表及领料新字段的 persistence + backend + test 审查产物。配置为 `docs/development/production-core-v1-codegen.yaml`，报告位于 `.local/production-core-v1-evidence/codegen-report.json`。采用生成 Mapper/XML/Example 和基础后端；路径、提交尝试的普通写方法明确 FORBIDDEN，不发布原始生成 CRUD Controller。手改生成文件移除归属标记；总生成配置排除三张受控表，避免后续全量生成重新开放写入口。新表前端使用业务组件，没有独立单表 CRUD 页面。

## 正式领料与 ERP 调用

入口：`POST /api/issue/mes-material-issue/{id}/actions/submit` → MaterialIssueSubmissionService → WarehouseIntegration → WarehouseErpAdapter。保存主单和明细仍是分别保存，不宣称整单原子草稿保存。

1. 本地短事务：按共享锁序固定需求、执行、单据；要求已下达工单、READY Execution、引用一致、完整路径、1–100 条有效明细、无禁止操作；冻结命令、生成 `ISSUE-SUBMIT-{attemptId}`、进入 SUBMITTING，提交事务。
2. 事务外：按已冻结 mode/provider/binding 选择仓储领域实现；传完整 MaterialIssueSubmitCommand（单据/工单/执行身份、产品、版本、路径修订及节点顺序、每条物料/数量/批次/单位/仓库）。测试断言此时无数据库事务。厂商转换、内部提交/审核编排仍由未来厂商 Service 负责。
3. 本地短事务：重新获取同链锁，核对当前 attempt、Extra operationVersion、路径修订；Extra 结果、单据状态、尝试状态和路径锁定同事务落地。任何本地失败回滚本阶段，可核实原操作恢复，禁止盲目再创建。

| 结果 | MES / 路径 | 后续入口 |
| --- | --- | --- |
| SUCCEEDED + LOCAL_ONLY | CONFIRMED，尝试 SUCCEEDED，路径 LOCKED；Extra 无外部主键/单号、externalEffect=NONE | 显示“本地模式，领料已确认”；满足其他门禁可开工 |
| SUCCEEDED + CONNECTED | 确认厂商领域操作完成，保存 Extra 身份，CONFIRMED + LOCKED | 仅契约/TEST ONLY Adapter 验证，未真实联调 |
| REJECTED | SUBMIT_REJECTED，不锁定新的路径，保留尝试与必要外部身份 | 明确拒绝且无外部身份时才允许 actions/reopen-rejected；下次新尝试，保留旧历史 |
| UNKNOWN / 调用异常 | SUBMIT_UNKNOWN，保留稳定操作身份，不推进路径或开工 | 查询与核实原操作；NOT_FOUND 也不允许重新创建 |
| 已有当前尝试重复提交 | 返回当前事实 | 不生成新 operationKey，不再外部 create |

查询仍为只读 GET erp-status；核实仍有独立权限。CONNECTED 只有 FOUND/EFFECTIVE 和可信外部身份才恢复确认；UNKNOWN、NOT_FOUND、待审核均不推进。迟到结果校验操作版本，不能覆盖更新的核实结果。LOCAL_ONLY 遗留 SUBMITTING 可以按已持久化本地意图完成本地恢复，不伪造外部单据。诊断消息安全化，不输出凭证或厂商原始异常。

CONNECTED 缺 mode/provider/binding 或可用 Adapter 时明确失败，不能退回本地模式。删除旧单头式 MaterialIssueCreateCommand，关联测试改为完整命令。没有真实厂商实现或 Gateway。

## 路径与开工

ExecutionPathValidator 检查：节点唯一、属于确切版本、根节点起点、汇节点终点、相邻有边、全部关键节点必经；允许不同节点复用同一工序。最多 100 个选定节点。选择接口必须带 revision，拒绝旧修订、非 READY、LOCKED、已有提交/作废阻断。锁定保持第一次 locked_at，后续多单确认幂等；作废不解锁。

专用入口 `POST /api/production/executions/{id}/actions/start` 检查：已下达工单、当前有效已发布版本、READY 或幂等 RUNNING、合法 LOCKED 路径、至少一张带正式提交引用的 CONFIRMED 领料、可信 Extra 事实、无 SUBMITTING/SUBMIT_UNKNOWN/VOID_PENDING/VOID_UNKNOWN。关联领料查询最多 1000 张，超过明确拒绝，不截断后宣称门禁成立。重复开工返回已保存状态及相同 startTime；首次返回重新读取的数据库时间，消除 DATETIME 精度差异。

普通 Create 仅 READY 且无起止事实；普通 Update 不允许改变 status/startTime/endTime。产品/工单关联固定；实际工艺只可在无路径和领料事实的 READY 阶段订正。没有 COMPLETED 命令，也不创造完工事实。开工门禁当前服务于标准领料驱动模式，未来其他生产模式须以明确需求扩展。

## 共享锁与历史兼容

ProductionChainLocks 统一 WorkOrder → Execution → MaterialIssue → Extra/Path。多对象按父对象 ID 排序，单据重关联提前同时锁旧、新父对象，之后复核关联；不存在先锁单据再反向获取执行的入口。路径选择、提交准备/结果、核实、作废、Execution 编辑共享父锁。开工持有 Execution 父锁，阻断同时作废；已 RUNNING 作废保守拒绝。外部调用在两次短事务之间，不持有行锁。

历史 execution_id=null 的领料继续读取、查询、既有维护和作废；标准提交要求 Execution，兼容 CRUD 不建立正式提交或路径锁。历史已 RUNNING/COMPLETED 的无路径记录不自动补事实；新 start 必须满足新门禁。旧批次 20 条和路径节点 48 条验收前后保持不变。

新路径查看使用现有 execution:query；选路、开工复用 execution:update；领料提交/重新打开复用 issue:update；ERP 查询和核实继续各自独立权限。均为 AUTHORIZED，后端校验 Scope 与资源。未新建菜单、角色或授予权限；中心库既有失败 Flyway V17 未在本轮 repair。业务 ID 在 HTTP 转为字符串，数量保留 decimal；Service 不依赖 HTTP VO。

## 页面与未实现边界

复用工单详情、ProductionExecutionList、MasterDetailDocument/DocumentFields/embedded CrudPage。加入适用版本选择、顺序路径选择与工序快照名称、保存后领料提交、本地确认提示、待核实入口、路径锁定只读及开工。历史缺工序名称展示“历史工序名称未记录”，不冒用现主数据名称。操作忙态和 generation 校验防重复点击、撤权/切厂/迟到响应清理。

未实现报工、产出汇总、完工、生产入库、QC/WIP、库存核算、真实 ERP、自动重试/Saga/MQ、复杂解锁/生产回滚、旧批次迁移。任务 Agent 未提交、推送、部署、修改参考项目或操作用户服务生命周期。工作区并行日志任务与外部提交不归为本轮；实际文件清单见 [manifest](2026-10-14-production-core-v1-files.txt)。
