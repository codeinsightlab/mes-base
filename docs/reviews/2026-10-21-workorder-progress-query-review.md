# 工单进度检索与不良状态展示

日期：2026-10-21。范围：`/api/production/workorder-progress` 及首页工单进度组件。
前序刷新机制见 [首页表格刷新记录](2026-10-21-home-table-refresh-review.md)。本轮保留表格刷新期间挂载、旧上下文响应拒绝及权限清理。

## 检索与分页

旧检索将工单号、产品编码和产品名称以跨表 OR、`%keyword%` 查询。现改为独立可选参数：

| 参数 | 语义 |
| --- | --- |
| workorderCode | 工单号字面前缀，最多 64 字符 |
| productCode | 产品编码字面前缀，最多 64 字符 |
| status | 合法工单状态；空值排除作废；ALL 包含全部 |
| offset / limit | 分页边界沿用现有校验，默认 0 / 5 |

两个编码条件同时填写使用 AND。输入先去首尾空白，空值不生成条件。
`prefixPattern` 转义 `!`、`%`、`_`，末尾只追加一个 `%`，配合 `LIKE ? ESCAPE '!'`；用户输入的通配字符按字面处理，SQL 继续参数化。
产品名称继续展示，但不作为检索条件。旧 keyword 参数与前端旧入口已移除，前后端应一并更新。

Page 仅由 Service 组装，Controller 直接返回明确字段白名单的业务查询结果；Controller 不再定义第二份 Page/Row 或逐行转换。
ID 与数量仍使用精确字符串。权限仍为当前工厂的 `workorder:list`，未新增权限或跨库访问。

## 用户确认的不良状态口径

| 状态 | 展示与计算 |
| --- | --- |
| 明确转报废 | 计入已确认报废，包含直接报废及返工失败转报废 |
| 退回指定工序返工（ROUTING_NODE） | 忽略本表报废与处置统计，不当作报废损耗 |
| 独立不良未分配处置 | 明确展示未处理 |
| 已分配所属工序返工、尚未开始 | 展示待返工 |
| 所属工序返工已开始、结果未确认 | 展示返工中 |
| 尚未核实归属的申报 | 展示未处理（待核实申报），不当作确认报废；该数是申报量，不能宣称唯一实物数量 |
| OVERLAP 重叠申报 | 不重复计数 |
| 已订正 / 作废 | 只读当前申报版本，排除最新为 VOID 的申报；旧版本处置不混入当前版本 |

以 `mes_production_defect` 最新版本和 `mes_quality_treatment` 不可变处置账本为来源，每页工单一次批量读取，不逐工单查询。
处置净量遵循既有 QualityTreatmentService：直接报废和失败结果按带符号事件求和；取消分配、取消开始、结果订正按负向事件扣回。
待处置＝申报量－净直接报废－净返工分配；待返工＝净分配－净开始；返工中＝净开始－净结果处理。
异常净量不截断成成功的 0：报废、处置和待完工差额返回未知并显示核实提示。

页面数量含义：

- 正式完工良品：仍来自 `mes_execution_completion.effective_qty`，不把报工量提前计入。
- 良品缺口：`max(计划－正式完工良品, 0)`。
- 待完工差额：`max(计划－正式完工良品－已确认报废, 0)`；是计划比较数，可包含未生产、在制、待返工和待核实，不能当作实物库存或严格未加工量。
- 良品达成率：正式完工良品 / 计划。报废不增加良品达成率。

例如计划 100、正式完工良品 90：确认报废为 0 时，待完工差额为 10；确认报废为 10 时，待完工差额为 0，但良品缺口仍为 10、良品达成率仍为 90%。
本轮只改变查询与展示，不补写质量事实、不改变生产完工状态、仓储库存或 ERP 行为。

## 修改文件

后端均位于当前仓库：

- `mes-business/.../production/domain/WorkOrderProgress.java`：查询、唯一 Page 与安全结果模型。
- `mes-business/.../production/service/WorkOrderProgressQueryService.java`：参数、分页与数量投影。
- `mes-business/.../production/controller/WorkOrderProgressController.java`：明确参数并直接返回 Service 结果。
- `mes-business/.../production/dao/WorkOrderProgressExtraMapper.java` 和对应 XML：前缀条件、分页完工与质量批量事实查询。
- `mes-business/src/test/java/dev/mesbase/business/production/WorkOrderProgressSqlTest.java`：真实 BoundSQL 参数与版本约束。
- `mes-admin/src/test/java/dev/issuefixture/WorkOrderProgressHttpTest.java`：HTTP、状态计算、权限、工厂隔离与异常事实测试。
- `mes-admin/src/test/java/dev/issuefixture/WorkOrderProgressMySqlTest.java`：配置验收库只读查询与 EXPLAIN。

前端位于 `/Users/user/work/lixin/mes-base-ui`：

- `src/api/production/workOrderProgress.ts`、`src/composables/useWorkOrderProgress.ts`：明确检索参数和响应类型，沿用刷新与上下文清理。
- `src/components/WorkOrderProgress.vue`：两个检索框、报废和不良多状态、良品缺口及待完工差额。
- `tests/workOrderProgress.test.ts`、`tests/workOrderProgressView.test.ts`：参数、刷新连续性与不同状态分别渲染。

修改文件均已检查生成标记。未覆盖其他迁移、ProductionQuantityService 等已有未提交工作。
任务前备份位于 `.local/workorder-progress-search-20261021/before/`。回退应按本轮文件差异恢复，禁止整体重置工作区；前后端接口字段须同步回退。

## 实际验证

1. 定向后端及真实数据库：

   ```sh
   MES_WORKSPACE_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am -Dtest=WorkOrderProgressMySqlTest,WorkOrderProgressHttpTest,WorkOrderProgressSqlTest -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   2 项 SQL 测试、4 项路由 H2 HTTP 测试和 2 项真实 MySQL 测试通过。真实数据库仅 SELECT / EXPLAIN，无 DDL、DML 或伪造样本。
   验收库通过现有 AcceptanceBusinessDatabase 核实中心绑定：工厂 SIMPLE_B、数据源 8、`192.168.18.25:3306/mes_base_dev_business_simple_b`，启用状态有效。
   新质量查询读取到 3 条当前申报投影，未新增或修改这些数据。

2. 真实生成 SQL 的 EXPLAIN（当前小样本估算，不能等同大规模耗时对比）：

   | 条件 | 实际计划 |
   | --- | --- |
   | 无编码条件，LIMIT 5 | w 使用 PRIMARY index，估算 5 行 |
   | 工单号前缀 | w 使用 uk_workorder_code range，估算 1 行 |
   | 产品编码前缀 | m 使用 uk_material_key range，估算 1 行；w 使用 idx_workorder_product ref，估算 9 行 |
   | 两个前缀 | w 使用 uk_workorder_code range，估算 1 行 |

   未新增索引或迁移。OR 并非必然无法使用索引；本次消除的是旧跨表 OR 和前导通配符的具体查询方式。

3. 前端 Node 22.23.1：`npm run test`，68 文件、408 测试通过；`npm run build` 通过，保留既有 chunk 大小提示。
4. 修改 Java 已执行 `scripts/format-java`；范围内 `git diff --check` 通过。
5. 全量 `mvn -B -ntp verify` 通过；mes-business 194 项测试无失败，mes-admin 572 项测试无失败（其中 48 项按环境条件跳过），其余模块也通过。
6. `/usr/bin/python3 scripts/smoke-packaged.py`：10 项通过、0 失败。使用脚本自有隔离进程验证打包产物，未操作用户已有服务，未访问真实 ERP。

日志：`/tmp/mes-progress-quality-mysql-final-binding.log`、`/tmp/mes-progress-quality-ui-tests.log`、`/tmp/mes-progress-quality-ui-build.log`、`/tmp/mes-progress-quality-verify-final.log`、`/tmp/mes-progress-quality-smoke.log`。
更早阶段曾发现新增查询列顺序与 record 构造顺序不一致，定向测试失败；已修正列顺序，最终测试全部通过，没有隐藏失败断言。
全量枚举契约检查也发现新增 SQL 中重复声明状态字面量，已改为从现有枚举 bind 并参数化，最终全量与真实数据库定向验证均重新通过。

## 运行边界

未启动、停止或重启用户服务。现有 5173 页面仅验证新控件与状态列布局，沿用用户当前选定的 TEST_DEV_PERF_20261019 工厂，未擅自切厂。
运行中的旧后端尚不提供新增质量字段，前端明确显示“未取得统计”，不把缺失统计当作 0。
完整状态数据已在实际 Mapper / HTTP Fixture 验证，不能冒充当前运行中服务的端到端验收；需由用户重启后端后复验。
页面截图：`.local/workorder-progress-search-20261021/home-quality-states.png`。
未调用真实 ERP。
