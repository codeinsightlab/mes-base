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
