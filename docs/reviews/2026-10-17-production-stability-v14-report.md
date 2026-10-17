# Production Stability V1.4 整改报告

状态（2026-10-17 09:25 更新）：用户再次重启后，原超时阻碍已解除；五个 Phase 主要业务已补真实 API/UI 证据。已验证整改链路局部 GO，完整验收仍有边界及一个错误请求返回500问题，不能宣称整个 MES 生产闭环 GO。历史失败保留，最新证据见第9节。

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

## 7. 全量和真实验收

- 前端全量 55 文件/326 项通过，最终 build 通过；日志 `/private/tmp/stability-ui-full.log`、`/private/tmp/stability-build-final.log`。构建只有既有大包提示，无编译错误。
- 后端最终默认 `mvn -B -ntp verify` 通过（`verify-complete.log`）：620 项，0 失败、0 错误，37 项独立环境验收按开关跳过。指定 SIMPLE_B 的真实 MySQL 开关用例另行验证，共18项通过，见后文；不将默认跳过解释为真实验证通过。
- 实际浏览器在用户现有 5173、SIMPLE_B、admin 登录下，已只读查看质量处置和新增纠错表单、原事件及可信姓名。1280/1440/1920 宽度截图保存在 `.local/stability-v14-20261017/ui/`，已恢复浏览器原尺寸。此时 8081 仍为旧代码，不能将表单展示当作新后端写入通过。
- 真实用户服务最初 GET `/api/production/input?code=...` 为旧版400/BAD_REQUEST。用户随后确认已重启，监听进程已变为68213，启动日志确认08:59:51正常启动；重启后的运行服务请求超时，详见下节。Agent没有自行启动/停止/重启服务。
- 没有修改用户权限；只读角色和范围负例主要由 HTTP/H2 回归提供，真实用户撤权操作未进行。

## 8. 保留问题、风险及结论

- 配置式 LOCAL 用料的产品化维护仍为后续独立议题；本轮只提供有界、正确的候选过渡查询，没有新建 BOM 表或改变来源。
- CONNECTED 继续是原 Adapter 契约，不宣称真实 ERP 厂商实现、ERP 故障恢复或实体库存对账已验收。
- Quality 的声明/事件总量保护、单节点返工范围、没有实物序列追踪的边界保持；UNKNOWN 未明确前不创建替代意图。待核实投产额度减少保留阻塞。
- sessionStorage 仅保证同浏览器标签会话的恢复，不能替代跨设备操作台账；服务端 UUID 幂等和原领料查询仍为最终恢复依据。
- 工作区有既有并行修改，均保留；未发现需覆盖其他 Agent 文件的冲突。
- **此前结论（已由第9节补验更新）NO-GO：代码整改、默认全量和18项指定MySQL验收通过；重启后的运行服务扫码/数量请求超时，真实 API/UI 写入及运行服务101张以上工单的扫码GET验收未完成。不能仅凭自动化宣称整个 MES 实物生产闭环 GO，正向 Receipt 原门禁继续保留。**


### 最终回归发现与处理记录

第一次默认全量发现管理员命令指纹中的动作字符串触发枚举约束，已改成独立 AdminAction 枚举，未放宽检查。随后发现旧数量 HTTP 测试缺少 UUID、派工测试准备漏填 assigned_by/assigned_at，已按真实入口补齐，保留原负例断言。

带真实数据库开关的整仓回归中，撤销 fixture 缺少 V1.3 处置服务装配；资格 fixture 又假设现有用户没有已配置能力，与用户当前数据冲突。前者补齐真实 QualityTreatmentService，后者改用事务内新建的 TEST 工艺/工序/节点及执行，不删除现有能力、不修改现有定义，测试后回滚。此轮失败保留于 `verify-final.log`，后续定向修复重跑和默认全量结果单独记录，不抹去失败证据。


### 修复后的数据库重跑

`MES_VOID_MYSQL_ACCEPTANCE=true MES_OPERATOR_MYSQL_ACCEPTANCE=true MES_DEFECT_MYSQL_ACCEPTANCE=true mvn -B -ntp -pl mes-admin -am -Dtest=MaterialIssueVoidMySqlTest,OperatorAssignmentMySqlTest,ProductionDefectMySqlTest,ProductionQualityV13HttpTest -Dsurefire.failIfNoSpecifiedTests=false test`：12 项通过，0 失败、0 错误、0 跳过；见 `mysql-recovery-rerun.log`。其中 3 项真实指定 MySQL，9 项 H2/HTTP（追加 RESULT 纠错并发重放断言）。撤销结果 `TEST_VOID_b261e1db`，工单193/领料89；资格隔离定义回滚，原用户能力未删除；不良事实 UPDATE/DELETE 被数据库拒绝。

带真实库开关的前一轮中，另外 15 项 MySQL 验收已通过：质量/报工并发6、Receipt1、Execution1、投产1、管理员报工3、质量 V1.3及纠错2、扫码候选只读GET1。更改的 fixture 定向重跑后，实际 MySQL 验收共 18 项通过；它们与后续默认全量 verify 分开计证据，不把失败的整轮日志改称成功。缓存为这些 DB fixture 的 mock/容错入口，非真实 Redis 性能或淘汰验收。

真实浏览器补验：在现有服务中通过链接直接打开领料89，显示 `TEST_VOID_b261e1db`、VOIDED/LOCAL_ONLY 与只读明细；返回列表成功。截图 `ui/recovery-linked-issue.png`。这验证了新前端链接与既有真实单据读取，未执行 ERP query/reconcile 写入或浏览器业务写入。


### 用户重启后的真实服务阻碍

用户已明确回复“已经重启”，不再把未验收原因归为用户未重启。只读核对：8081由新Java进程68213监听，启动日志正常，`/health`返回200，使用现有验收身份的菜单GET返回200。实际已下达工单数为123；Agent未删除、关闭或修改已有工单以降低数量。

但带合法扫码内容的 `/api/production/input`、`/api/production/report-quantities/executions/204`、应快速返回格式错误的 `/api/production/input?code=INVALID` 均在6–15秒探测中超时。现有5173浏览器执行真实扫码GET，也最终显示“连接失败或超时”，没有候选结果。直连、前端代理和沙箱外只读探测均未完成该请求；没有因此改代理、权限、服务参数或重启进程。

进程线程栈只读采集未检出Java死锁，app/error日志未记录扫码请求业务异常，access日志尚无该扫码请求完成记录。现有菜单和部分领料读取仍有200完成记录；当前不足以确定是连接传输、请求入口还是运行实例中的其他问题，不归因于数量公式，也不宣称本轮代码已通过运行服务验收。证据：`runtime-thread.txt`、`runtime-supply.json`、服务 `.local/logs/MacBook-Pro-3.local-68213/` 及 `ui/runtime-input-timeout.png`。

因此没有在未知状态下继续发起报工或质量业务写入。待解决运行请求超时后，仍须补验：真实候选GET、管理员三命令重放、纠错和Receipt差额读回、节点候选/POST一致性、恢复查询及浏览器写入；真实撤权、切厂负例目前主要是自动化证据。打包启动smoke未执行，因为它会启动服务，而本轮禁止Agent自行启动用户服务。

### 最终检查

- 两仓库HEAD未变化，无提交/推送/部署；`git diff --check` 通过。
- 所有本轮修改Java已用项目 `scripts/format-java` 格式化。
- V33已正式迁移并validate；原四类不可变事实表共8个不可变触发器保持，相关真实负例验证通过。
- 用户已有前端修改及未跟踪质量组件保留，未做危险清理。


## 9. 2026-10-17 09:17–09:25 用户现有服务补验

### 环境与权限

用户明确“已经重启，按照我的服务来API/UI验收”。使用现有后端 `127.0.0.1:8081`（Java PID73992）及前端 `5173`，Agent未启动、停止或重启任何服务。再次核对中心绑定：`SIMPLE_B → datasource8 → 192.168.18.25:3306/mes_base_dev_business_simple_b`，工厂及数据源均ENABLED；已下达工单123张，业务Flyway最新V33成功。

本次后端HEAD为 `e908bbc6a75b725dacaf3af81b295a440e3114b1`，已不同于最初实现基线；前端HEAD仍为 `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。后端开始时仅报告未提交，前端18项已有修改/未跟踪内容保持原状。本次只更新报告和忽略目录中的验收证据，未修改业务代码、Schema或角色权限，没有Git提交/推送/部署。

admin原会话身份读取200；既有TEST Receipt写/读账号和正式报工账号均通过真实登录接口取得会话。admin缺少Receipt和正式报工权限时403，改用已授权的既有TEST账号，不扩大授权。写入全部限定SIMPLE_B的TEST单据。开始前保存质量摘要及事件至 `live-before.json`，新增命令只追加事实，不更新或删除历史。

### 各Phase真实运行结果

| 范围 | 真实证据 | 结果 |
|---|---|---|
| Phase1 | 123张RELEASED下合法品号/批次GET，库存13/仓库9/工单117；offset0返回total1、1条，offset20返回total1、空页 | 200；5173页面显示“已加载1/1张匹配工单”，唯一候选默认选中，原超时未复现 |
| Phase2旧事实重放 | 补录214、订正215、冲销216原UUID/原内容，另4并发原UUID | 均200返回原事实；改内容409 REPORT_REQUEST_CONFLICT；缺UUID400 REPORT_REQUEST_ID_REQUIRED；原3条合计仍0 |
| Phase2浏览器新写入 | 工单188/Execution204：补录237 +2、订正238 -1、冲销239 -1 | 三次提交后页面与真实数据刷新；原命令逐笔重放均200、ID不变，净量恢复0 |
| Phase2首次并发 | 新UUID四请求同时补录1，随后模拟响应丢失原样重放 | 全部返回同一240，仅一条事实；冲销241后净量0；最终Execution204共8条流水、合计0 |
| 正式报工V2回归 | 既有真实投产Execution127/节点1393，TEST报工用户提交1并重放 | 返回同一242；admin追加冲销243；前后节点数量对象完全一致 |
| Phase3纠错及差额 | Execution206原RESULT纠错UUID重放；浏览器追加事件4b157905…，处理3/恢复2/失败1 | 200；恢复量1→2、净不良9→8、末道E91→92；Receipt已交98，超交7→6 |
| Phase3恢复与依赖 | 新纠正事件ab4a3c9f…恢复处理3/恢复1/失败2；原CANCEL_START/CANCEL_ALLOC重放；尝试撤回已消耗部分 | 恢复E91/超交7；合法重放200，已消耗撤回409；原RESULT仍为处理4/恢复3/失败1，未被改写；原申报事件共9条 |
| Phase4候选/POST一致 | TEST申报69a3a9e7…在D节点1651登记UNVERIFIED2；实际路径A1648/B1649/C1650/D1651/E1652 | 候选A/B/C为NO_QUANTITY（样本已报满，非质量冻结），D/E为QUALITY_PENDING；POST D409 QUALITY_ATTRIBUTION_REQUIRED，明确D名称及待核实2 |
| Phase4核实后 | 将该TEST观察核实为原B不良的OVERLAP，保留申报和核实事实 | D/E不再QUALITY_PENDING；E91、已交98、超交7保持；正向Receipt原质量门禁仍在 |
| Phase5原请求恢复 | 原投产UUID e2e8b811…连续重查两次；领料89外部只读状态查询 | 都200；两次返回相同issue81/execution127、投产额度320；领料89显示VOIDED/LOCAL_ONLY，不伪造ERP成功 |
| Phase5派工保护 | 新TEST READY Execution212，未选路径、无领料/报工，现有合格TEST用户6241派工17 | 改工艺409 EXECUTION_FROZEN；删除409 EXECUTION_REFERENCED；再次GET确认执行212/工艺64/派工17均保留 |
| 权限负例 | 既有只读TEST账号调用补录和质量处置 | 均403；不同操作者测试使用的Receipt账号也在权限入口403，未到指纹比较，不能计为真实“已授权不同操作者指纹拒绝” |

### 浏览器视觉、导航及数据收尾

真实5173执行扫码候选查询、质量结果纠错、管理员补录/负订正/整笔冲销；点击查询原领料ERP状态返回LOCAL_ONLY提示。刷新后数量及流水保持正确。质量弹窗1280/1440/1920截图均已检查，表单在弹窗内部滚动，未发现遮挡操作的布局异常；所有临时viewport已reset。

切厂SIMPLE_B→DEV后旧领料89详情清空并返回概览，没有展示B的单据；随后恢复SIMPLE_B。没有对DEV写入业务数据。最终标签保留生产数量管理204供复验，工厂恢复原值。

截图：`ui/live-supply.png`、`ui/live-quality-success-1440.png`、`ui/live-quality-1280.png`、`ui/live-quality-1920.png`、`ui/live-report-supplement.png`、`ui/live-final-1440.png`、`ui/live-recovery-query.png`、`ui/live-switch-dev.png`（均位于 `.local/stability-v14-20261017/`）。

原始API证据：`live-api-0917.json`（含初轮错误探测，不改写失败）、`live-readback.json`、`live-api-more.json`、`live-gates.json`、`live-replays.json`、`live-formal.json`、`live-recovery.json`、`live-input-replay.json`、`live-protection.json`；最终数据库核对 `live-final-db.txt`。凭证仅在private文件中，不进入报告/API证据。

质量纠错新增两笔相反差量后恢复原有效数量；TEST待核实观察已核实为OVERLAP，未遗留新质量阻塞。报工两个样本均追加冲销恢复原数量。READY执行212/派工17特意保留TEST备注，供复验引用保护；未删除规划引用，也未删除已有用户工单。期间未创建领料、未调用ERP外部写。

### 新发现、证据边界与当前结论

1. 初次验收脚本GET `/api/production/reports?executionId=204` 漏传必需的 `routingNodeId`，服务返回500。日志确认为 `MissingServletRequestParameterException` 未映射400；补齐节点1024后200，真实页面始终带节点，正常链路通过。此为错误请求的协议异常，仍需单独收敛，不能把500写成通过。本次未为验收扩展修改通用异常处理。
2. 当前没有SUBMITTING/SUBMIT_UNKNOWN/VOID_PENDING/VOID_UNKNOWN真实领料样本；未伪造运行状态或发ERP命令制造UNKNOWN。上述状态下的原意图保留和查询按钮仍以既有自动化为证据，实际服务仅补确认态投产重放、VOIDED原单查询和页面入口。
3. 未实际撤销/扩大用户权限；已有只读账号的403是真实负例，动态撤权与迟到响应仍主要由自动化证明。节点上游样本已报满，实际证明的是未被质量门禁冻结，未将其声称为上游正向报工成功。复杂门禁矩阵其他动作仍参考定向回归。
4. 本次未改代码，未重复运行已经通过的620项默认verify、326项前端测试及build；它们属于上一轮日志证据。本轮后端 `git diff --check` 通过，前端现有 `src/views/issue/mesMaterialIssue/index.vue:3` 留有一处行末空格，本次未覆盖该文件。
5. **已验证整改业务局部GO**：101张限制修复、管理员三命令和首次并发幂等、正式报工V2回归、质量纠错/守恒/Receipt差额、D/E候选与POST门禁一致、READY派工保护均有用户现有服务证据。原“请求超时导致无法验收”阻碍已解除。**完整验收仍NO-GO**：错误请求500尚存，真实UNKNOWN恢复/动态撤权等未验收；不宣称整个MES实物生产闭环或真实ERP集成GO，Receipt正向原门禁保持。
