# 历史生产数据迁移 V2.1：增量补卡与准入验收

日期：2026-10-20（Asia/Shanghai）。承接 [V2样板报告](2026-10-20-legacy-report-driven-migration-v2-acceptance.md)。**结论：V41正向、B02/B03补卡、受控竞争恢复及正常LOCAL回归通过；B04与全部615直接执行NO-GO。564个组件可提交审批，本轮未批准或执行批量导入。** 本轮只追加明确的隔离样板、正常LOCAL回归资源和迁移基础设施。

## 1. 环境、保护与工作区

当前分支 main，HEAD `d6d403b79f71a415410cb7aacb1c912d054603a9`。开始时已有V2、平台、性能/最近报工、进度查询及质量等dirty工作，全部保留；未提交、推送、部署或重启用户服务。最终JAR来自该工作区，并不声称只包含V2.1修改。

重新核验实际实例 `192.168.18.25:3306`，server UUID `03dbc952-7403-11ef-802c-0242ac130002`；只读来源 `prod_j2eedb`，目标 `mes_base_dev_perf_test`，中心 `mes_base_dev`。Factory `TEST_DEV_PERF_20261019`、数据源9、revision2、LOCAL、双方ENABLED，与V2一致。未改绑定、账号权限或MySQL持久化参数；没有ERP命令或锻接写入。

用户明确回复“没有其他写入，可使用本轮验收窗口”。开始时保存47张表（原45张＋V40两表）的主键/全字段哈希、字段metadata、SHOW CREATE TABLE及全部触发器定义，两次目标摘要确认无其他追加。来源只复核本轮选定组件的报工、不良、材料、余额、扣料、route/节点/产品关联及主数据，没有重跑全量转换。

受控目录为 `.local/legacy-v21-20261020/`；完整原编号、账号、SQL和HTTP结果不提交。`before/`、`window-check/`、`schema-before.sql-output.txt`、`triggers-before.jsonl`、`selected-source-recheck.*` 保存执行前证据。源码manifest保护 V40/V41，Flyway记录和旧行摘要用于执行后复核。摘要不是整库可恢复物理备份；本轮不覆盖旧行，以组件事务保护新事实。

所有真实HTTP来自自建隔离验收JVM，复用正式Controller/Service、Factory/权限、受管Owner租约和业务TransactionManager。没有使用用户旧进程模拟新版本已部署。test-only启动器不进入生产JAR，自身Quartz/调度关闭。没有前端修改或5173 UI验收。

## 2. V41原版正向门禁

在V41仍为当前版本、尚未应用V42时，选取新的真实普通工艺隔离组件P1，冻结原始来源和命令，核对人员、投入、节点与主数据。通过原受管迁移Service完成：

- 新建历史工单1112、1个Execution、实际Path/节点、1条原净报工及Mapping。
- 业务事实、Mapping与批次RUNNING→COMPLETED在同一业务事务提交。
- 正式HTTP26项通过，数量/原操作人/时间正确，写入口拒绝；重复execute返回1112，零重复。

证据：`P1-command.json`、`P1-V41-result.json`、`P1-V41-http/summary.json`。这是真实新增成功，不把SQLSTATE45000拒绝当正向通过。P1运行使用V2冻结JAR生产字节；没有修改V41授权条件。

## 3. 增量契约与V42

原V41创建Execution要求“当前工单Mapping的首次批次RUNNING”。旧工单Mapping不可修改，首次批次已经COMPLETED；因此不能把原批次重新置RUNNING，也不能伪造第二个WORKORDER映射来获得新批次授权。

新增正式V42 `legacy_incremental_card_authorization`，V1–V41文件未修改：

1. `mes_legacy_source_lock`仅保存稳定身份摘要主键，不保存factory_id。按稳定顺序取得来源锁，覆盖尚未存在的工单/主数据Mapping。数据库锁跨JVM/批次生效。
2. 保留原执行授权分支，新增不可变`APPEND_AUTHORIZATION` Mapping：parent指向原工单Mapping、entity指向同一历史工单、source hash/rule必须与原Mapping一致；当前批次须RUNNING，授权中的typed command hash须在批次approved_scope中。
3. 只替换历史Execution插入授权触发器。替换期间先建立更严格的临时拒绝触发器，完成新触发器后移除临时保护，没有无保护窗口；不关闭触发器或设置会话绕过开关。
4. 新历史Report沿用V41：当前Execution Mapping的批次必须RUNNING。正常历史编辑/质量/库存/交接/完工等拒绝规则继续保留。

`LegacyHistoryImportService.append`是显式内部补卡用例：核对同一来源工单/冻结hash、LOCAL/HISTORICAL、原计划量与起止时间、原产品/单位/spec与Routing版本；只追加未映射卡。原工单、Routing、原Execution/Report及Mapping不更新。已映射卡必须逐报工校验稳定身份、hash和父节点，不能以“存在一个Mapping”跳过内容核对。已迁投入＋本批新投入不得超过原计划。

冻结命令还校验来源树中的卡身份、原报工主键/人员/时间/数量与typed字段一致，拒绝拿其他工单/报工的Mapping拼装新命令。批次审批绑定完整typed command及snapshot；同source hash换rule拒绝。同批prepare采用无字段变化的主键upsert后重读核对，跨批次execute先取得来源锁，再锁批次行。

V42通过项目正式Flyway执行并validate42项。其创建的锁表属于本轮明确新增对象；旧表字段未变。

## 4. S1逐卡结果与查询

S1原目标身份通过来源Mapping重新查得1109，原B01=1107，并非生产代码硬编码。原工单计划9600、计划时间、产品、Routing和原来源快照均不改。

| 卡 | 投入 | 本轮结果 | 事实表达 |
|---|---:|---|---|
| B01 | 2400 | 原有，未修改 | 原3报工及Mapping保护 |
| B02 | 2400 | 追加成功 | 新Execution、3节点、3条各2400报工，独立批次 |
| B03 | 2400 | 追加成功 | 新Execution、3节点、4条报工；末序净2390＋工废10＝总加工2400，有效2390 |
| B04 | 2400 | NO-GO，整卡事务回滚 | 仅两道实际报工；与原三节点完整锁定路径不相容，不能补造第三序或改旧Routing |

B03的工废保存为1条受控QUALITY_EVIDENCE及节点聚合修正，不增加虚构员工工作量，不生成正常核实、放行或库存事实。不同批次竞争B03时一个完成，另一个失败回滚后经原批次重审恢复为相同目标身份；原失败不算并发通过，锁修复后的独立组件另行复验。

B04最初在正式`ExecutionPathValidator`被拒，整个Execution/Path/Report/授权Mapping回滚，仅本轮失败批次记录保留。随后增加前置检查，在创建前拒绝不完整锁定路径。其来源原始2条报工仍保留在冻结命令和待核对清单；不能宣称已迁B04或整单完工。

新增正式只读 `GET /api/production/workorder/{id}/legacy-history`，复用既有报工详情权限，列出冻结来源全部流程卡、首次迁入批次/Execution、来源与已导报工数、源路径是否不完整、尚未迁入卡数；`productionComplete=false`。原工单详情、执行汇总、Execution、Path、报工流水及数量接口继续使用。

## 5. 竞争、恢复与发现的缺陷

真实独立JVM测试发现并修正两类问题，失败证据保留：

- `INSERT IGNORE`重复键共享锁与后续排他锁转换可能死锁；改为来源锁行`ON DUPLICATE KEY UPDATE id=id`，直接取得排他锁，字段值不变。
- 仅改upsert仍会与触发器读批次授权行形成“批次X锁→来源锁”/“来源锁→批次S锁”环。`deadlock-after-upsert.txt`保留MySQL死锁图；修正为先稳定来源锁、再锁当前批次。没有通过自动重试掩盖死锁。
- 同批prepare的“先查后插”也可能产生批次主键冲突，改为主键upsert后核对完整冻结范围，禁止覆盖审批字段。

固定组件P2用于故障注入，未提交组件没有留下事实/Mapping。六类注入实际命中：prepare前、工单插入后、首Execution插入后、Report插入前、REPORT Mapping插入前、最终COMPLETED语句之后/COMMIT之前直接JVM halt(86)。前五类失败与批次状态分离处理；prepare无批次，普通异常FAILED需重审，进程退出回到REVIEWED。提交前退出批次以同一冻结命令恢复，创建工单1122并重放同身份。

P3工单1121用于跨进程竞争，三个来源卡路径均一致。初轮已经证明不同批次创建同一来源工单只有一个提交；同批prepare失败随后由上述修复覆盖。最终冻结JAR下的新卡竞争、提交后丢响应和二次恢复结果见第10节，不把中间失败或Mock当作最终通过。

质量/投入来源、跨工单Mapping和跨报工主键拒绝用真实受管入口验证；不同规则版本使用本轮专用新批次，在真实业务事务内改变测试rule，执行拒绝后回滚，不改原V2批次。直接SQL负例始终在显式回滚事务中运行，不保留探针事实。

## 6. V41/V42负向保护

`sql-guards-results.json`记录真实MySQL拒绝：历史Report、Defect、Treatment、QualityRelease、Receipt、Acceptance、Completion、MaterialIssueInput、Assignment及stock action直接插入；历史工单/Execution/Routing更新；正常新报工原位修改。全部拒绝，显式事务回滚，没有探针残留。

正式HTTP验证历史启动/选路径/编辑、报工/补录/调整/作废、质量申报/放行、领料、交接、完工、工单下达/编辑/取消拒绝。匿名401、错误Factory422；本轮正常库存单入口还实际确认了未获相应权限的403。未修改用户权限来绕过拒绝。

服务负例包括缺审核、冻结source hash变化、command hash变化、跨工单Execution来源、跨报工身份、规则不等价。初次部分负例进程碰到Maven重编译共享classes目录的ClassNotFound，属于运行装配失败，不算业务拒绝；最终以冻结JAR重做，结果见第10节。

## 7. 普通工艺准入清单

本轮复用V2冻结来源转换结果，新增全量身份/目标冲突/路径可执行性审核，没有重复全库Dry-run或批准执行。逐组件输出原始范围、snapshot hash、规则版本、目标主数据映射/待创建清单、组件hash、冻结命令和拒绝原因。`approvedForExecution=false`，proposed命令文件hash不冒充服务端已审批typed command hash。

最终准入统计（当前主数据名称/单位/spec及实际字段长度、decimal精度复核均通过）：

| 状态 | 工单 | Execution | 报工 |
|---|---:|---:|---:|
| APPROVABLE | 564 | 8,375 | 122,155 |
| NEEDS_REVIEW | 83 | 746 | 11,772 |
| REJECTED | 70 | 927 | 16,301 |
| FORGE_EXCLUDED | 146 | 2,377 | 20,256 |

合计863/12,425/170,484。615张原结构/数量候选中额外48张存在不同卡实际路径集合不一致，1张缺可信人员匹配，最后另发现2张工单的3条SELF历史登录名/姓名快照与当前账号不一致、无足够更名依据，全部降为待核对；故合计51张额外门禁未过，不直接全部视为可审批。其他待核对包含旧工艺/工序/投入/物料身份缺证；拒绝包含无修改事件历史、负余额、数量不守恒、5700/未知损耗语义等。原因可重叠，不能把原因命中数相加当工单数。

564张可审批组件涉及206个不同报工用户，均逐一匹配，证据为`admission-release/personnel-coverage.json`。人员审核不是把S1/S2的21人匹配推广：每条候选报工检查当前两库同ID、唯一登录名、姓名与账号创建时间证明；还核对账号创建不晚于历史报工；可审批范围内无时间倒置。上述2张历史快照冲突工单不涉及本轮已提交样板，未尝试强行映射。没有创建用户。目标已有码但无可信映射需审核，已映射的ID、语义hash、规则、当前名称/产品单位/spec需一致。实际执行前仍须固定审批范围、核对冻结来源及当时绑定，不因APPROVABLE自动写入。

锻接146工单/2,377执行/20,256报工及1,185条broken原文完整保留，145条上游未解析仍待核实；本轮不修改V2锻接规则或来源清单。

最终权威清单为 `admission-release/summary.json`、`components.jsonl`、`commands/`、`personnel-coverage.json`；前期`admission/`及`admission-final/`保留为审核收紧前的时间线，不作为最终准入结论。

## 8. 新普通LOCAL完整回归

使用本轮全新`V21_LOCAL2_20261020`资源，通过真实HTTP完成：普通工单创建及编辑/下达、材料需求、正式领料投产并锁Path、READY首次报工转RUNNING、后续报工、质量放行、两次交接/接收、正式完工。5节点各2条报工，投入80，原料160真实扣至0，成品80；首次报工UUID再次发HTTP返回同一结果，没有重复报工。

核心事实21条：报工10、投产1、放行1、交接2、接收2、完工1、stock action4。17项业务审计bad均0、21/21数量正确。审计脚本以`--partial`运行，表示不是10万条压测规模；本轮单个完整闭环已经实际完成，不把压测规模标识误解为生命周期未完。

首次沿用性能脚本的库存单创建因现有账号缺该新权限403，保留新建零库存资源、没有库存动作；随后使用现有已授权的正式`/api/wm/local-stock/opening`。测试脚本曾把工单revision与独立材料需求revision混淆，409后只在确认目标无需求行时修复本轮私有journal，按需求revision0继续；没有改生产Service/权限/旧数据以迁就测试。新增脚本`verify_normal_local.py`保留这些正式入口及READY/RUNNING/UUID断言。

## 9. 已有数据与不确定边界

原901闭环17项审计通过，100,011条核心事实、90,080完工量不变。保护不仅覆盖901，还包括原S1/S2的2工单/9执行/148报工/17质量证据及所有其他原有性能事实、库存、批次、Mapping。每张旧表逐主键所有字段哈希及字段定义比对，允许的Schema增量仅V42锁表与执行授权触发器替换；最终结果见第10节。

新增数据必须归属本轮隔离历史批次或正常测试tag，失败批次和零库存准备资源保留归因；不删除不可变事实做清理。来源SQL始终只读。中心Factory/DataSource绑定、revision和模式不变。

仍有限制：B04/其他不同路径卡尚未解决完整锁定路径表达，不能通过补假报工或修改旧Routing补齐；缺人员证明的候选不审批；未部署用户旧服务/UI。任何批量执行都需要独立批准，本轮不提供自动批量入口。

## 10. 最终构建与验收补记

### 10.1 最终产物与检查

- `mvn -B -ntp verify`最终通过，2026-10-20 10:14:57 +08:00结束，日志`verify-final-2.log`。各模块报告788项测试，其中48跳过、失败/错误0；跳过的真实库测试不计作真实验收。
- 首轮完整verify有1项既有`StockDocumentHttpTest`竞争测试失败：库存单先提交时，投产准备校验可能返回400，而测试只接受409；定向7项重跑及随后完整verify通过。没有改测试放宽断言或修改库存/投产Service迁就结果，保留此时序敏感边界。
- Python31项通过（原23＋本轮8），迁移脚本语法检查、8个Java文件格式检查及`git diff --check`通过；`smoke-packaged.py`10项通过。
- 验收权威JAR为 `/Users/user/work/lixin/mes-base/.local/legacy-v21-20261020/final-artifact/mes-base-0.1.0-SNAPSHOT.jar`，110,981,450字节，SHA-256 `9ae401475408f420dbc8fff417ec6cd6bf8f177061104851afb09db2c41dd952`。test-only启动器不在生产JAR内；验收从该冻结JAR解出原样生产classes/依赖，加独立test-only启动器运行。
- `target/mes-base-0.1.0-SNAPSHOT.jar`随后被另一轮构建更新为不同摘要（`164a4dfe89e97604e9aa71eb715180513cc47d5beb3f2481acd7f965c70127c0`，记录在`artifact-current-comparison.json`）；本轮未覆盖它，也不将那份产物冒充本轮已验收JAR。

### 10.2 最终真实运行结果

- 最终JAR四独立JVM、四人屏障及持锁窗口下：两个同批请求竞争同一新卡，另一批竞争同卡/同报工，再一批请求同工单另一已映射卡。仅新增1个Execution/3条Report；两个竞争批次均COMPLETED，没有死锁或重复事实。同工单不同卡测试中一张为重放、一张为新增，未将其冒充“两张未迁卡同时新增”。
- 三个同卡调用在Service成功提交后执行真实`Runtime.halt(88)`，没有返回结果文件。以原批次/原命令重启，均回到工单1122；此前P3提交后退出也恢复为1121。`final-replay-zero-additions.json`证明重放前后全部48张目标表零新增、零字段变化。属于内部调用提交后进程退出/丢返回，不冒充HTTP网络断包实验。
- 另一个真实两卡组件，在首Execution插入后异常回滚，工单/Mapping残留0、状态FAILED。原冻结命令重审后完整执行两卡、10条报工及质量证据，再由外层真实业务事务回滚，状态恢复REVIEWED，仍无工单/Mapping残留。实际Bound SQL及参数在`final-multicard-retry-rollback-app.log`；没有将该回滚组件计入持久迁移数量。
- 最终JAR负例通过：source hash错误、command hash错误、跨工单Execution来源、跨报工来源、未审核批次、相同来源换rule（真实业务事务拒绝并回滚）。另用合法新内容hash改变原计划的TEST ONLY命令尝试同来源工单，进入CONFLICT，Mapping残留0，原工单未变；没有改来源库。
- 最终JAR正式HTTP：B03 31项/4报工、P3 51项/15报工、P2新卡30项/3报工、原S2 197项/145报工均通过；B02先前30项/3报工通过。工单历史接口明确返回4张来源卡、已迁3、未迁1、源路径不完整1、非完工。既有授权工厂操作账号读取历史接口200；匿名401、错误厂422、库存单权限403不混淆。

### 10.3 实际持久增量与保护

本轮持久迁移新增：历史WorkOrder3（P1/P2/P3）、Execution8、原净Report29、历史质量证据4、Mapping92（其中补卡授权5）。S1新增的是2个Execution/7条Report，原工单未重建；B04和多卡回滚组件没有事实残留。另有1个正常LOCAL完整闭环，10条正常报工、合计21条核心事实。

总业务增量为工单4、Execution9、Report39；23个普通验收/失败/冲突批次加1个多卡回滚批次，共24个新批次。两个正常脚本准备阶段留下的单位、产品、工序、工艺、仓库及4条库存行均归本轮tag；2条零库存准备行没有stock action，另2条库存属于已完成正常回归。没有删除这些准备资源来掩盖失败。

最终`final-verified/protection.json`核对全部原有47表行和字段结构不变，声明的新增表仅`mes_legacy_source_lock`；原V40/V41记录与源码字节不变，所有旧触发器名称保留，仅V42声明的Execution插入授权内容变化。原901审计17项全部bad=0、100,011条事实不变；正常新闭环17项bad=0、21/21事实正确，正常报工和质量事实原位UPDATE均被不可变触发器拒绝。绑定仍为datasource9/revision2/LOCAL。

本轮所有长驻隔离验收JVM均按PID/启动类/本轮目录核实后停止，其余故障/竞争JVM按测试标志自行退出，smoke自行清理；没有停用户服务或Redis。

### 10.4 A–H与门禁

| 问题 | 最终答复 |
|---|---|
| A V41后真实正向写入？ | 是，V41原版下P1已提交；V42下补卡提交及完整组件SQL/事务验证通过。 |
| B S1补卡而非第二张工单？ | B02/B03成功，仍1109且只有一个来源工单Mapping；B04因不完整锁定路径NO-GO，未造第三序。 |
| C 跨批次/进程零重复？ | 最终四JVM竞争及重放通过；中间两类死锁、prepare冲突已定位修复，失败证据保留。 |
| D 中断/丢返回恢复？ | prepare、工单后、首卡后、Report前、Mapping前、提交前halt及提交后halt均有真实证据；原命令恢复同身份，FAILED需重审、REVIEWED可按原范围继续、CONFLICT不能自动覆盖。 |
| E 615中多少可审批？ | 564工单/8,375执行/122,155报工；51张额外门禁未过。全来源分类见第7节，均未取得批量执行批准。 |
| F 正常LOCAL完整闭环？ | 通过，创建/编辑/下达→投产→READY/RUNNING报工→放行→交接/接收→完工，V41/V42未误拦截。 |
| G 原S1/S2与901未变？ | 是，连同其他既有业务行/库存/Mapping逐主键全字段保护通过；仅本轮新资源发生正常测试变化。 |
| H 批量条件？ | 已具备564个完整路径组件提交审批的基础；全部615直接执行仍NO-GO。B04/路径不一致、人员缺证等组件继续隔离；任何执行须另行批准并按冻结组件复核，本轮没有自动导入。 |

当前GO仅限已实际验收的受管迁移/补卡入口和明确的可审批组件；不是不附条件的全量发布、部署或批量写入GO。原有时序敏感测试、未部署用户服务/UI、未解析锻接关系等限制如实保留。

## 11. A–H与Change Summary

A–H结论见第10节；本轮仅推进已明确的隔离样板与准入审核，不授权全量追加。

- 迁移Service/Mapper：显式补卡、不可变来源校验、稳定来源数据库锁和并发prepare；原生产核心未重写。
- 历史查询Service/Controller：新增同工单全部来源卡的迁移进度，明确未迁/源路径不完整和非完工。
- V42：仅新增正式迁移；V1–V41不可改写。
- `scripts/legacy/prepare_component.py`、`review_v21.py`、`verify_normal_local.py`：冻结分卡命令、全量准入审核、真实正常LOCAL回归；已有HTTP核对支持同工单分卡，采集器允许明确指定V42。
- test-only启动器：受管真实JVM竞争、定点异常、提交前/后退出、规则冲突；没有生产启动钩子或公开导入HTTP。
- `test_legacy_v21.py`：新增8个合成边界回归，与原23项合计31项；不把纯规则测试当真实MySQL。
- 新增本报告第1–11节，保留失败与修复时间线、可复核私有证据及未验证边界。本轮不更新个人memory，不提交敏感原编号。
