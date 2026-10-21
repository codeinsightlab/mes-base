# 业务首页列表刷新稳定性修复

日期：2026-10-21。入口：用户现有 `http://localhost:5173/`，重点为工单进度翻页、查询时表格宽度变化。

## 原因与修改

真实浏览器复现：原实现点击“下一页”后，`.workorder-progress .el-table` 在加载期间不存在，
页面显示“正在读取…”，返回后整个表格和分页重新创建。源码对应
`useWorkOrderProgress.load()` 每次先 `clear()`，以及 `WorkspaceReadState` 加载分支替换默认 slot。
通用 `CrudPage` 保留已有表格和行，以 `v-loading` 表示刷新。本次沿用该机制。

前端增量文件（相对 `/Users/user/work/lixin/mes-base-ui`）：

- `src/components/WorkspaceReadState.vue`：增加可选 `loadingOverlay`；启用后 ready/loading 共用同一个内容容器，
  使用项目已有 Element Plus 加载遮罩和 aria-busy。默认关闭，其他状态展示保持原语义。
- `src/components/WorkOrderProgress.vue`：采用加载遮罩，增加稳定 row-key 和表格 width:100%；保留筛选、分页、
  数量显示、当前权限和 API 契约。
- `src/composables/useWorkOrderProgress.ts`：同一上下文刷新保留已加载行、总数和更新时间，成功后替换；失败清空数据并显示错误。
  切厂、身份/权限版本变化、撤权和卸载仍立即清空，并以请求序号拒绝迟到结果。
- `src/composables/useBusinessWorkspace.ts`、`src/components/BusinessWorkspace.vue`：同样将业务首页工单概览、
  近期报工、不良列表改为刷新时保留已有数据和表格，避免“刷新工作台”重建这些列表；失败仍与空列表区分。
- `tests/workOrderProgress.test.ts`、`tests/businessWorkspace.test.ts`：增加同厂刷新保留、失败清理和上下文清理断言。
- `tests/workOrderProgressView.test.ts`：延迟 Mock 验证翻页/查询期间表格及分页 DOM 节点不变、总数和原行保留、
  成功后替换数据、撤权后移除以及迟到响应不恢复。

未修改通用 CrudPage、API、后端、数据库、数量算法、业务权限或页面样式体系。
以上多个文件入场时已是用户未提交内容；本次仅在其基础上增加刷新行为修复，没有覆盖、重置或提交其他工作。

## 实际验证

使用 `.nvmrc` 对应已安装 Node v22.23.1，不改变全局 Node。

- `npm run test`：68 文件、407 项通过。
- 最后修正新增测试的 TypeScript 类型后，
  `npm run test -- tests/workOrderProgress.test.ts tests/workOrderProgressView.test.ts tests/businessWorkspace.test.ts tests/businessWorkspaceView.test.ts`：
  4 文件、16 项再次通过。
- `npm run build`：vue-tsc 和 Vite 通过；保留已有大分块提示。首次构建发现新增测试的 Promise 泛型及
  DOMWrapper.exists 类型问题，修正后重跑通过，没有忽略类型检查。
- 前端仓库 `git diff --check` 通过。本轮未修改的后端 `scripts/legacy/migrate_quality.py` 存在入场/并行尾部空格，未处理。

真实浏览器复用用户现有 5173 服务，没有启动、停止或重启服务。只做登录和只读 UI 操作，未提交业务数据。
浏览器使用当前既有 `TEST_DEV_PERF_20261019` 工厂选择，没有切厂，也没有把此次 UI 验证声明为 SIMPLE_B 数据库验收。

| 视口宽度 | 翻页前表格宽度 | 翻页后表格宽度 | 额外证据 |
|---|---:|---:|---|
| 1280 | 982 | 982 | 实际捕获 loading=true、遮罩存在、原5行及表格仍在 |
| 1440 | 1134 | 1134 | 翻页成功，页面内部按既有布局响应 |
| 1920 | 1598 | 1598 | 翻页成功；无匹配查询0行、重置5行后宽度仍为1598 |

1440/1920 的即时取样时请求可能已完成，不将其写成捕获了完整加载过程；加载中保留表格的真实证据来自1280，
组件延迟 Mock 则覆盖稳定 DOM 身份。临时视口覆盖已恢复。

本地证据：`.local/home-table-refresh-20261021/before/` 保存本轮开始前相关文件副本，
`home-progress-after.png` 为真实页面完整表格及分页截图。
日志：`/tmp/mes-home-refresh-all-tests.log`、`/tmp/mes-home-refresh-targeted-final.log`、`/tmp/mes-home-refresh-build.log`。

验证边界：真实页面未人为制造后端故障、撤权或切厂；这些负例通过 Mock/组件测试验证。
首页既有报工接口不可用提示继续按原错误语义呈现，本次没有修复或掩盖后端问题。
没有运行 Maven、写 SQL、访问 ERP、提交、推送或部署。

回滚仅撤销上述前端刷新增量，保留入场未提交功能；必要时用本地 before 副本对照，不能整体覆盖并行改动。

## 2026-10-21：取消重复工单概览，最近报工与不良处置流水

用户确认：保留完整工单进度，删除下方重复的“工单概览”页签及其专用 orders 请求；首页质量区展示处理事件流水，不展示声明列表，也不将同一声明合并成最新版本。最近报工和不良处置流水各固定最多 10 条，表格高度 420px，内部滚动；无无限加载、总数和翻页控件。草稿/已下达/待执行/执行中指标及工厂待办仍按原接口语义工作。

实现与契约（前端相对 mes-base-ui）：

- `src/components/BusinessWorkspace.vue`、`src/composables/useBusinessWorkspace.ts`：删除 orders 数据资源/请求及重复页签，默认近期报工；两个最近流水表设置 height=420、row-key=id，继续保留刷新遮罩、失败与空态区分、切厂/撤权/卸载清理及迟到响应拒绝。
- `src/api/production/workspace.ts`、后端 RecentProductionReportController / QueryService / ExtraMapper：`GET /api/production/reports/recent` 返回数组，服务固定 10 条；移除 PageHelper、offset/limit 输入及两个 Page 包装。SQL 在派生表内显式选择所需字段、主键倒序 LIMIT 10，再关联执行/工单/产品/单位/工序，外层保留稳定顺序；未使用 SELECT *。不再 COUNT。旧后端仍返回分页对象时，前端明确报响应契约不匹配，不把它当作无报工。
- `src/api/quality/qualityTreatment.ts`、后端 QualityTreatmentController / Service / MesQualityTreatmentMapper：新增 `GET /api/quality/treatments/recent`，直接读取 mes_quality_treatment 最近 10 条并批量回显操作人/确认人，复用既有 Event HTTP 投影。原声明查询、历史分页、单声明处置查询与写入地址保留。质量 Mapper 手工扩展时移除了实际修改文件的 MES-CODEGEN 标记。
- 处置 UUID 不代表时间顺序，使用 recorded_at DESC,id DESC；同一声明跨多个版本、撤回及订正事件均逐条保留。展示动作、直接报废、分配/开始返工、加工、恢复、失败报废、操作者/确认者、实际处理及登记时间、原因；正负数来自原事件，不冒充当前余额。动作标签与 QualityTreatmentPanel 共用已有 API 模块中的 treatmentActionLabel。
- 新增正式业务迁移 `V46__quality_treatment_recent_index.sql`，仅为 mes_quality_treatment(recorded_at,id) 建联合索引，不修改质量事实及历史迁移。

SQL 与索引证据：通过既有 AcceptanceBusinessDatabase 核实 SIMPLE_B 中心绑定，实际目标仍为数据源 8、192.168.18.25:3306/mes_base_dev_business_simple_b；仅执行 SELECT/EXPLAIN。真实报工计划显示 mes_production_report 使用 PRIMARY 倒序 index 扫描、估算读取 10 行，后续主键关联；外层对至多 10 条结果排序并不等同主表全量排序。处置流水当前计划为 ALL、176 行、Using filesort。不能宣称分页插件必然截断索引，也不能把新增 SQL 的预期收益当作已部署收益。

迁移执行阻碍：真实 flyway_business_schema_history 最新成功版本为 V37；V38–V45 尚在前面，包含任务外的库存、历史迁移与生产激活规则。因此 V46 已保存但未执行，未跳过历史手工建索引，未擅自整体推进这些迁移。处置查询去掉 COUNT、限制返回 10 条已验证，但该验收库的排序索引收益仍未完成。没有对真实数据库执行 DDL/DML、修改模式/绑定或访问 ERP。

验证：`MES_WORKSPACE_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am -Dtest=RecentProductionReportMySqlTest,RecentProductionReportHttpTest,ProductionDefectHttpTest -Dsurefire.failIfNoSpecifiedTests=false test` 共 10 项通过；HTTP Fixture 覆盖固定 10 条、精确字符串 ID/decimal、调整流水、同一声明多个处理事件不合并、UUID 与时间反序、跨厂隔离、缺范围及缺权限拒绝；StatementHandler 捕获 SQL 断言没有 COUNT。真实库同时执行报工及处置实际 Mapper XML。前端 Node22.23.1 全量 72 文件/420 项测试通过，vue-tsc 与生产 build 通过（原有大分块提示）。Java 已格式化，修改范围 diff --check 通过；全量 `mvn -B -ntp verify` 通过（mes-admin 573 项、0失败/0错误、48项按环境跳过），`/usr/bin/python3 scripts/smoke-packaged.py` 10 项通过、0 失败，使用脚本自有隔离进程。

曾按最初理解试验“最近声明”，真实库报缺少 defect_type_id；用户随后明确改成“处置流水”，已删除该试验接口、Service/Mapper/模型及测试，不为任务外声明 Schema 缺失绕开字段或伪造数据。最终读取 mes_quality_treatment 已通过真实库验证。

运行边界：复用现有 5173 与原 TEST_DEV_PERF_20261019 选厂，未切厂或写业务数据；浏览器确认重复页签已移除。当前运行后端尚未加载新最近数组契约/处置端点，页面分别明确显示契约不匹配/404接入待办；不把 Fixture 数据或空白表当成运行中成功。需要用户加载新后端后复验真实10行及滚动。未启动、停止或重启用户服务。仅回滚本节所列增量可撤销本功能；数据未改写，V46未执行时直接撤回本次迁移文件即可，禁止覆盖并行提交或其他工作区修改。

日志：`/tmp/mes-home-recent-targeted-treatment.log`、`/tmp/mes-home-recent-ui-tests-final.log`、`/tmp/mes-home-recent-ui-build-final.log`、`/tmp/mes-home-recent-format-final.log`、`/tmp/mes-home-recent-verify.log`、`/tmp/mes-home-recent-smoke.log`。

真实页面当前后端未加载的边界截图：mes-base-ui `.local/home-recent-ledgers-20261021/current-backend-boundary.png`。固定高度与全部真实10行的浏览器验收尚待新后端加载，不将截图里的接入待办写成成功。


### 后续用户调整：10条完整展开

取消上述两张最近流水表的 `height=420`，保留最多10条数据和稳定 row-key，让实际10条记录完整展开，不再在表格内部纵向滚动，统一随整页滚动。未补造空白记录，横向列溢出仍由既有表格处理；不修改接口、排序、数据库或其他待办。定向前端10项测试和生产build通过，范围diff检查通过。真实5173当前后端已加载新接口，两类流水均实际返回10行；逐个页签核对表体clientHeight=scrollHeight=770，无内部纵向溢出。沿用TEST_DEV_PERF_20261019选厂，未重启服务或操作业务数据。截图：mes-base-ui `.local/home-recent-ledgers-20261021/ten-rows-expanded.png`。日志 `/tmp/mes-home-ten-rows-test.log`、`/tmp/mes-home-ten-rows-build.log`。
