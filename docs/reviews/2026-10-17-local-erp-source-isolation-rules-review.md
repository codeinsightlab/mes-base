# LOCAL / ERP 来源隔离规则固化审查

日期：2026-10-17。类型：静态业务/架构审查与规范变更。业务决定见 [正式方案 V1.0](../architecture/local-erp-business-boundary.md)。本报告不是数据库或运行验收。

## 交付与旧规则修订

- 后端 `AGENTS.md`：新增同日优先级章节，固化单 ERP、可信来源、三方同源、权威库存、同步边界、停旧启新与历史保护；纠正旧 ERP 章节以每厂旧配置作为主入口的表述，改为托管快照为准；区分业务来源与 Extra 外部事实。
- 前端 `../mes-base-ui/AGENTS.md`：新增只读来源、选择器/请求准入、参考库存、模式锁与历史原身份限制，不复制后端全部规则。
- `docs/architecture/local-erp-business-boundary.md`：新建用户指定标题的项目正式方案，含“V1严格来源隔离与停旧启新迁移策略”、准入表、实现差异矩阵及后续 Schema/API/Service/UI 顺序。
- `docs/architecture/ADR-005-erp-integration-boundary.md`：追加最终决定与现行方案链接，保留历史修订，不恢复第三数据源设计。
- 本文件：记录可复查源码依据、实际检查及未验证边界。

在两仓库项目文档及 Markdown 搜索了“业务边界设计方案”“LOCAL+ERP”“混合单据”“无缝迁移”，未定位题名一致原稿或用户列出的承诺原文；未伪造逐字原文修订。方案第 6 节按用户给出的旧建议建立裁决表。未发现需修改个人全局规范的冲突，未修改个人规则文件。

## 源码证据与差异

下列路径均相对后端仓库，UI 路径相对相邻前端仓库；核对的是当前含已有 V1.5/V1.6 未提交工作的源码。

| 依据 | 静态事实 | 新规则缺口 |
|---|---|---|
| `mes-admin/src/main/java/dev/mesbase/server/managed/FactoryIntegrationRegistry.java`，snapshot/configure；`ManagedDatasourceService.java`，saveFactory | 新厂缺省 LOCAL，Registry 校验 provider/ref/连接及模式锁 | 已有基础；没有完整业务清理/盘点确认/切换时间/历史配置归档流程 |
| `mes-system/src/main/java/dev/mesbase/system/factory/ManagedFactoryMapper.java` | 保存 runtime_mode/provider/ref/revision/locked | 只能证明持久化源码，未核对真实库 |
| UI `src/views/system/factory/index.vue` | 模式与单 provider/ref 选择，integrationLocked 禁编辑，解绑不解锁提示 | 没有正式迁移 UI |
| `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderEntity.java`；`production/service/WorkOrderService.java`，create/update/assign | 无 LOCAL/EXTERNAL 来源；普通保存仍接受外部编号，经 Extra 保存 | 不具备可信外部创建来源/禁止改源与模式准入；外部编号不构成来源证明 |
| `mes-admin/src/main/resources/db/business/V21__workorder_external_extra.sql` | external_id/code 迁到 Extra，注释明确历史来源尚未核实 | 不可批量当 EXTERNAL 或按当前模式回填 |
| `mes-business/src/main/java/dev/mesbase/business/wm/domain/WmWarehouseEntity.java` | 无 LOCAL/EXTERNAL 字段 | 无仓库来源能力 |
| `mes-business/src/main/java/dev/mesbase/business/wm/service/impl/WmWarehouseServiceImpl.java`，upsert/write | 按 code 定位旧行，更新名称/状态/备注，未筛来源 | 真实 QueryPort 接入后存在覆盖同码 LOCAL 的可能；未见该路径删除逻辑 |
| `mes-business/src/main/java/dev/mesbase/business/wm/sync/UnconfiguredWmWarehouseQueryAdapter.java` | 默认抛 SYNC_QUERY_NOT_IMPLEMENTED | 未证明真实 ERP 已覆盖数据，风险与已发生事实须区分 |
| `mes-business/src/main/java/dev/mesbase/business/wm/service/impl/WmInventoryServiceImpl.java`，requireMirrorMode/upsert | CONNECTED 模式限制；库存键定位、引用保护、余额写入 | 不能仅凭当前模式识别遗留 LOCAL，来源级保护尚缺 |
| `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/WarehouseIntegration.java`，configuredMode/submissionSelection/cancellationMode | 托管快照模式及 provider/binding 选择；历史领料按冻结来源核实/作废 | 选择接口未接收工单/仓库来源，不能证明三方严格隔离 |
| `mes-business/src/main/java/dev/mesbase/business/wm/service/LocalStockService.java`，execute/receiveProduct/restoreIssue | 新动作有 LOCAL 模式门，历史恢复使用冻结投入 | 模式门不等于来源门；既有冻结不是完整来源迁移 |

全量差异矩阵、未来最小实施顺序见正式方案第 4/5 节；为避免两份实施计划漂移，本报告不重复维护。

## 实际静态检查与边界

- 修改前以 `git ls-files -co --exclude-standard -z` 清单及 SHA-256 保存两仓库 1936 个文件基线；修改后逐文件比较，包含既有未提交/未跟踪产物。
- `git diff --check`：后端全量通过。前端本轮 `git diff --check -- AGENTS.md` 通过；前端全量检查仍有修改前已存在的 `src/views/issue/mesMaterialIssue/index.vue:3` 行尾空格，未为文档任务修改业务代码；该文件哈希与基线相同。不能将前端全量检查报告为通过。
- 本 Agent 仅编辑上述 5 个规范/文档文件。基线复核另检测到 WmStockActionCreateCommand.java、WmStockActionUpdateCommand.java 在本轮期间被其他操作删除；本 Agent 未执行该删除，也未恢复或覆盖。其余已有源码、测试、SQL、配置与基线一致；这些同期工作区变化不归因于本轮文档实施。新增 Markdown 相对链接目标及关键章节检查通过。
- 规则明确区分当前已实现、确认目标、代码缺口和未来扩展；来源隔离/迁移没有以文档变更冒充实现。
- 未执行 SQL、数据库写入、Maven/前端测试构建、API/UI/ERP 运行验收；未启停任何业务服务，未修改历史事实，未 Git commit。真实库、已部署服务及厂商业务生效均未验证。
