# Production Stability V1.4 整改报告

状态：实施中，最终验证结论待补齐。未经真实验收的项目不计通过。

## UNVERIFIED 节点影响矩阵（改造前先行确定）

以实际锁定路径 A→B→C→D→E、D 存在待核实不良为例：

| 动作 | A/B/C | D/E | 保留的约束 |
|---|---|---|---|
| 正常新增报工、管理员正数补录 | 允许 | 阻塞，返回 D 的工序名称与核实提示 | 原输入、余额、路径、资格、派工约束 |
| 负数订正、整笔冲销 | 按真实影响验证 | 按真实影响验证 | 不增加任一下游历史倒挂；不低于已确认直接不良及当前节点待核实申报依据 |
| 质量申报 | 允许登记 | 允许登记 | 申报量仍不得超过所在节点报工；既有事实一致性 |
| 质量核实 | 原契约 | 原契约 | 关联独立申报、版本、归属及数量一致性 |
| 返工确认及纠错 | 原申报所属节点 | 原申报所属节点 | 单节点、父事件、净分配/开始/处理额度、非负和 UUID |
| 投产额度减少 | 保守阻塞 | 保守阻塞 | 输入减少影响整条路径，现模型缺少未核实申报的实物归属证明，暂无法证明最小安全范围 |
| 正向 Receipt | 保持既有门禁 | 保持既有门禁 | 不开放质量放行；历史交接差额按当前有效产出投影 |

待核实不良不自动计为独立损失，也不作为确定的正常流转授权；D/E 的新增正向报工阻塞。负订正的待核实依据使用当前节点待核实总额作保守上界（可能包含重复观察），避免减少后无法解释申报；不能证明的情况拒绝，不以位置在上游替代传播校验。正常数量公式保持不变。


## 1. 基线、范围与状态

- 后端开始及当前 HEAD：`6fbbeb668827992cb6803b2539265d85a290e5e8`，`main`；开始时只有未跟踪的本轮输入审查报告。
- 前端开始 HEAD：`94167e0c25fd9cb4dfcac5ecef369f6e8b715546`，`main`。保留既有 ProductionReport/ProductionDefect/QualityTreatment 的未提交改动，在相关位置增量修改，没有重置或覆盖整份页面。
- 基线 HEAD/status/diff 摘要：`.local/stability-v14-20261017/baseline.json`。原审查报告保持不变。
- 未修改 Source Product、LOCAL/ERP Port 与 Adapter、WarehouseIntegration 选源、库存权威、ProductionExecution 永久身份、生产数量核心公式或原不可变事实。没有提交、推送、部署、权限扩张或启动/停止/重启用户服务。

## 2. Phase 1：扫码候选查询

原实现先读取全厂 RELEASED 工单数量并限制为 100，导致与扫码材料无关的工单也能使请求失败。现在先枚举当前厂现有配置的工单用料 ID，再匹配材料、合法库存仓库，最后由数据库筛选 RELEASED 和候选 ID，并按 `id DESC` 执行数据库分页及总数统计。未配置或无匹配工单返回合法空结果，不加载全厂工单。

过渡规模边界：不假设存在 BOM 表；每厂最多枚举 10,000 个配置工单 ID，SQL 的 ID IN 范围最多 10,000。超过时明确报配置规模错误，不把它解释成全厂工单业务上限，也不截断候选。依赖可枚举 Spring PropertySource 中的既有配置键，仍需后续独立议题决定本地用料维护方式。既有库存来源数量限制保持不变，本轮不扩展库存查询契约。

GET `/api/production/input?code=...&offset=0&limit=20` 返回 `total/offset/limit`，limit 为 1–100；UI 可继续加载匹配工单，只有总候选数为 1 时自动选择。原 CONNECTED Adapter 签名及三参数 Context 构造器保持兼容，现有 Adapter 完整结果在边界分页；未来厂商的真正远端分页不是本轮已实现能力。静态候选集合分页验证无重复漏项；跨 HTTP 页的并发配置/状态变更不构成数据库快照，变更后应重新查询。

正式计算指定 `workorderId` 单独重新查询，不受首页位置影响；提交保留原工单、用料、库存及计算依据复核。

修改文件（后端前缀 `mes-business/src/main/java/dev/mesbase/business/`）：

- `integration/production/ProductionSupply.java`
- `production/service/ProductionInputSupplyService.java`
- `production/controller/ProductionInputController.java`
- `mes-admin/src/test/java/dev/issuefixture/ProductionInputHttpTest.java`
- 新增 `mes-admin/src/test/java/dev/issuefixture/ProductionStabilitySupplyMySqlTest.java`
- UI：`src/api/production/productionInput.ts`、`src/views/production/productionInput/index.vue`、`tests/productionInput.test.ts`

证据：`phase1-test.log` 的 7 项测试通过（6 项 H2/HTTP，1 项指定真实 MySQL 只读 MVC GET）。覆盖全厂 99/100/101/1000 张工单和合法候选 0/1/20/200、指定非首页工单计算、分页精确覆盖。真实 MySQL 验证时确认已下达工单至少 101 张，GET 200 且原工单不变。此证据是新代码 + 真实 MySQL + MVC，不冒充用户正在运行的 8081 已重载。

## 3. Phase 2：管理员报工幂等

复用既有 `request_id` 唯一键，新增命令指纹列，指纹绑定动作（SUPPLEMENT/ADJUST/VOID）、原报告/执行/节点、规范化参数及可信登录操作者。先查询重放，取得原 Execution 锁后再次查询；同一请求只追加一次，数量和原报告余额校验保持原入口。并发跨执行的相同 UUID 由原数据库唯一键拒绝，事务回滚。

同 UUID 同规范内容同操作者返回原流水；不同内容、不同操作者、不同动作或与正式 V2 UUID 冲突返回 409。整笔冲销重放直接返回原冲销结果，不重新按变化后的余额计算。管理员和正式报工互相拒绝复用对方的请求身份。

兼容策略：HTTP 缺失或非规范 UUID 明确返回 400 `REPORT_REQUEST_ID_REQUIRED`，提示升级客户端，不执行无幂等写入。历史记录保持 NULL，不补写历史。保留的 Java 便利构造器只为每个新建命令对象生成一次 UUID；重试必须复用同一对象/显式 UUID，不能重建命令。前端冻结未知结果的 UUID 和原参数；首次未知后的重试即使收到 403 等拒绝，也不会认定原请求未落库并更换 UUID。原请求保存在当前浏览器 sessionStorage，按用户名/工厂/执行隔离；重开后仍重放原命令。明确事务拒绝后才允许编辑新意图；请求身份冲突保留原请求供核实。

修改文件：

- `mes-admin/src/main/resources/db/business/V33__admin_report_command_fingerprint.sql`
- `mes-business/.../production/domain/{ProductionReportModels,MesProductionReportEntity,MesProductionReportExample}.java`
- `mes-business/.../production/service/{ProductionReportService,ProductionReportSubmitService}.java`
- `mes-business/src/main/resources/dev/mesbase/business/production/dao/MesProductionReportMapper.xml`
- `mes-common/src/main/java/dev/mesbase/core/error/ErrorCode.java`
- `mes-admin/src/test/java/dev/issuefixture/{MaterialIssueHttpTest,ProductionReportHttpTest,ProductionReportMySqlTest,ProductionReportingHttpTest,ProductionQualityQuantityHttpTest}.java`
- UI：`src/api/production/productionReport.ts`、`src/components/business/production/ProductionReportManager.vue`、`tests/productionReport.test.ts`

迁移已执行并验证：SIMPLE_B → datasource 8 → `192.168.18.25:3306/mes_base_dev_business_simple_b`，Factory 和数据源均 ENABLED。执行前最新 business V32，原报工 152 条，已备份 `.local/stability-v14-20261017/before-report.sql`；正式 Flyway migrate/validate 到 V33 成功。新增 nullable CHAR(64) 中文 COMMENT，沿用原唯一键与触发器，没有修改旧迁移。

Codegen Skill 从迁移后的真实 metadata 完整生成 persistence/backend/test 到 `target/stability-v14-codegen`；配置及报告在 `.local/stability-v14-20261017/report-codegen.{yaml,json}`。合并 Entity/Example/XML 的新增字段并保留手写 `selectByRequestId`；未安装会绕过不可变事实的生成 CRUD 写入口。手工维护的落地文件不带生成归属标记。

证据：`phase2-test.log` 9 项通过，含 3 项真实 MySQL。新增验收 Execution 184，三个命令各只保存一条流水，净报工为 0；不同内容/操作者、并发重放、响应丢失后重试及原事实触发器均验证。

## 4. Phase 3：受控质量纠错

复用 `mes_quality_treatment`，没有新表或生产身份。新增三种动作，仍复用原质量 revise 权限、可信操作者、原因、时间、UUID 指纹、Execution 锁和事务：

- `CANCEL_ALLOC`：关联 ALLOCATE，撤回非负数量，保存负向分配纠正；撤回后的返工额度不能低于该分配下净 START 量。报废分配撤回仅回到待处置，不直接恢复为合格品。
- `CANCEL_START`：关联 START，只取消净 START 减有效 RESULT 后的未处理余额；已处理部分不能直接取消。
- `CORRECT_RESULT`：关联原 RESULT，命令填写订正后有效处理、成功、失败总量，保存与当前有效结果的差额。三者非负，成功+失败=处理；处理总量不得增加。原结果增加加工、跨节点、跨申报、跨版本或突破父额度均拒绝，新增加工需另记 RESULT。

汇总对原事件及其纠正增量求和，返工净分配≥净开始≥净处理≥0，成功恢复+失败=净处理，直接报废分配+返工净分配≤原不良，恢复≤原直接不良。待处置=原不良−直接报废分配−返工净分配；待开始=返工净分配−净开始；处理中=净开始−净处理。后续 START/RESULT 使用纠正后的父余额，不能重新消耗已撤销额度。原节点净不良/传播/有效产出/Receipt 差额沿原 ProductionQuantityService 投影刷新；不改变正常报工事实。

明确边界：支持同节点原事实的记录纠错及成功/失败重分类；不重算实物身份、不修改锁定路径、不增加 RESULT 已处理量、不将直接报废分配自动转合格、不支持多节点返工。若实际实物已离开该节点，当前模型没有新的实物移动契约，本轮不提供跨节点撤回按钮。

修改文件：

- `mes-business/.../quality/domain/QualityTreatmentModels.java`
- `mes-business/.../quality/service/QualityTreatmentService.java`
- `mes-admin/src/test/java/dev/issuefixture/{ProductionQualityV13HttpTest,ProductionQualityV13MySqlTest}.java`
- UI：`src/api/quality/qualityTreatment.ts`、`src/components/business/quality/QualityTreatmentPanel.vue`、`tests/qualityTreatment.test.ts`

纠错前端同样保存未知请求并在重开时恢复；曾经未知的重试即使被拒绝也保留身份。

证据：`phase3-test.log` 8 项 HTTP + 2 项真实 MySQL 通过。真实纠错 Execution 186：不良 10，结果从处理4/成功3/失败1纠正为处理3/成功1/失败2，末道有效产出91，历史交接98，差额7；原 RESULT 与正常报告逐行不变，纠正 UUID 重放仅一条，DELETE 继续由不可变触发器拒绝。H2 进一步覆盖部分取消、依赖超额拒绝、已订正处理量不能再次增加。

## 5. Phase 4：节点门禁和候选一致性

按文首矩阵实施；`writable` 只表示实际路径具备写入前提，不再因任意节点待核实而冻结整个执行。正数命令从实际路径上首次待核实节点开始阻塞；负数命令执行原传播一致性校验及当前申报依据校验。`positiveReportBlockReason` 被正式候选 GET、正式 POST、管理员命令和页面投影共用。返回 `QUALITY_PENDING` 候选和包含工序编码/名称、待核实数量及“先核实质量记录”的说明，POST 使用相同原因和 `QUALITY_ATTRIBUTION_REQUIRED`。

页面即使默认隐藏不可报节点也显示质量阻塞原因；受影响节点的产出显示为待核实数量投影，不能被误读为已放行流转量。投产减少继续保守阻塞，Receipt 正向质量放行原门禁保持。

修改文件：

- `mes-business/.../production/service/{ProductionQuantityService,ProductionReportSubmitService}.java`
- 新增 `mes-business/.../production/domain/ProductionQualityPendingException.java`
- `mes-business/.../production/controller/ProductionQuantityErrorAdvice.java`
- `mes-business/.../production/controller/vo/ProductionReportViews.java`
- `mes-admin/src/test/java/dev/issuefixture/{ProductionQualityV13HttpTest,ProductionReportingHttpTest}.java`
- UI：`src/api/production/productionReport.ts`、`src/components/business/production/ProductionReportManager.vue`、`src/views/production/productionReporting/index.vue` 及相关 tests。

证据：`phase4-test.log` 18 项通过：D 待核实时 A/B/C 可继续正向报工，D/E 阻塞；D 的安全负订正可执行，削弱申报依据或扩大下游倒挂拒绝；正式候选 AVAILABLE/QUALITY_PENDING 与实际 POST 一致。

## 6. Phase 5：操作恢复与规划派工保护

投产 UNKNOWN/SUBMITTING 结果不再使“原请求重查”按钮失效；浏览器当前标签会话保存原请求，重开同用户/同工厂页面恢复，仍调用原 POST 的只读重放路径，不重复外部创建。提供“打开本次领料单并核实”直达链接。通用 MasterDetailDocument 只公开既有 open 能力，由领料业务页读取 issueId；切厂清理，不自动跨厂打开旧 ID。

领料 `SUBMITTING/SUBMIT_UNKNOWN` 明确显示查询/核实原领料结果，复用既有 ERP query/reconcile 权限及接口；`VOID_PENDING/VOID_UNKNOWN` 继续复用既有“核实作废结果”。没有新增后台重试、UNKNOWN 自动建新请求或架构选源改变。

READY 已派工的执行，删除返回 EXECUTION_REFERENCED，改工艺返回 EXECUTION_FROZEN；保留派工引用，不能无声删规划或跨版本遗留派工。派工增删与 Execution 修改继续取得公共执行锁。未改变只调整计划数量/备注等原合法操作。

修改文件：

- `mes-business/.../production/service/ProductionExecutionRules.java`
- `mes-admin/src/test/java/dev/issuefixture/{MaterialIssueHttpTest,OperatorAssignmentHttpTest,OperatorAssignmentMySqlTest,ProductionExecutionMySqlTest,MaterialIssueVoidMySqlTest}.java`：公共 fixture 装配真实 V24/Mapper，移除原两个测试重复 DDL。
- UI：`src/views/production/productionInput/index.vue`、`src/components/document/MasterDetailDocument.vue`、`src/views/issue/mesMaterialIssue/index.vue`、`src/components/business/MaterialIssueErpAction.vue`；`tests/{productionInput,materialIssue}.test.ts`。

证据：`phase5-test.log` 8 项通过；READY + 无正常报工 + 规划派工场景拒绝删除和改版本，引用保持不变。前端覆盖 UNKNOWN 可重查、重开复用 UUID、精确直达领料、切厂和迟到响应清理。

## 7. 全量和真实验收（待最终追加结果）

- 前端全量目前 326 项通过，构建通过；最终日志 `.local/stability-v14-20261017/` 及 `/private/tmp/stability-*.log`。
- 后端最终 `mvn -B -ntp verify` 同时启用指定 SIMPLE_B 的扫码、报工、质量返工、投产、撤销、Receipt、人员/派工、Execution 和质量数量 MySQL 验收开关，运行中。
- 实际浏览器在用户现有 5173、SIMPLE_B、admin 登录下，已只读查看质量处置和新增纠错表单、原事件及可信姓名。1280/1440/1920 宽度截图保存在 `.local/stability-v14-20261017/ui/`，已恢复浏览器原尺寸。此时 8081 仍为旧代码，不能将表单展示当作新后端写入通过。
- 真实用户服务 GET `/api/production/input?code=...` 当前仍为 400/BAD_REQUEST；已通过异步问题请求用户重启，未擅自启动/停止/重启服务。新后端真实登录 API 和浏览器写入验收待加载代码后执行。
- 没有修改用户权限；只读角色和范围负例主要由 HTTP/H2 回归提供，真实用户撤权操作未进行。

## 8. 保留问题、风险及结论

- 配置式 LOCAL 用料的产品化维护仍为后续独立议题；本轮只提供有界、正确的候选过渡查询，没有新建 BOM 表或改变来源。
- CONNECTED 继续是原 Adapter 契约，不宣称真实 ERP 厂商实现、ERP 故障恢复或实体库存对账已验收。
- Quality 的声明/事件总量保护、单节点返工范围、没有实物序列追踪的边界保持；UNKNOWN 未明确前不创建替代意图。待核实投产额度减少保留阻塞。
- sessionStorage 仅保证同浏览器标签会话的恢复，不能替代跨设备操作台账；服务端 UUID 幂等和原领料查询仍为最终恢复依据。
- 工作区有既有并行修改，均保留；未发现需覆盖其他 Agent 文件的冲突。
- **当前 NO-GO：自动化与指定库证据正在收尾，用户现有服务尚未加载新后端，真实 API/UI 写入和101工单运行服务 GET 未完成。即使本轮全部整改通过，也不代表整个 MES 实物生产闭环 GO，正向 Receipt 原门禁继续保留。**


### 最终回归发现与处理记录

第一次默认全量发现管理员命令指纹中的动作字符串触发枚举约束，已改成独立 AdminAction 枚举，未放宽检查。随后发现旧数量 HTTP 测试缺少 UUID、派工测试准备漏填 assigned_by/assigned_at，已按真实入口补齐，保留原负例断言。

带真实数据库开关的整仓回归中，撤销 fixture 缺少 V1.3 处置服务装配；资格 fixture 又假设现有用户没有已配置能力，与用户当前数据冲突。前者补齐真实 QualityTreatmentService，后者改用事务内新建的 TEST 工艺/工序/节点及执行，不删除现有能力、不修改现有定义，测试后回滚。此轮失败保留于 `verify-final.log`，后续定向修复重跑和默认全量结果单独记录，不抹去失败证据。


### 修复后的数据库重跑

`MES_VOID_MYSQL_ACCEPTANCE=true MES_OPERATOR_MYSQL_ACCEPTANCE=true MES_DEFECT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am -Dtest=MaterialIssueVoidMySqlTest,OperatorAssignmentMySqlTest,ProductionDefectMySqlTest,ProductionQualityV13HttpTest -Dsurefire.failIfNoSpecifiedTests=false test`：12 项通过，0 失败、0 错误、0 跳过；见 `mysql-recovery-rerun.log`。其中 3 项真实指定 MySQL，9 项 H2/HTTP（追加 RESULT 纠错并发重放断言）。撤销结果 `TEST_VOID_b261e1db`，工单193/领料89；资格隔离定义回滚，原用户能力未删除；不良事实 UPDATE/DELETE 被数据库拒绝。

带真实库开关的前一轮中，另外 15 项 MySQL 验收已通过：质量/报工并发6、Receipt1、Execution1、投产1、管理员报工3、质量 V1.3及纠错2、扫码候选只读GET1。更改的 fixture 定向重跑后，实际 MySQL 验收共 18 项通过；它们与后续默认全量 verify 分开计证据，不把失败的整轮日志改称成功。缓存为这些 DB fixture 的 mock/容错入口，非真实 Redis 性能或淘汰验收。

真实浏览器补验：在现有服务中通过链接直接打开领料89，显示 `TEST_VOID_b261e1db`、VOIDED/LOCAL_ONLY 与只读明细；返回列表成功。截图 `ui/recovery-linked-issue.png`。这验证了新前端链接与既有真实单据读取，未执行 ERP query/reconcile 写入或浏览器业务写入。
