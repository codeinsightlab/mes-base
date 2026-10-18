# Base MES V2.3 — LOCAL 生产与仓储多场景端到端验收

验收日期：2026-10-17～18（Asia/Shanghai）。证据目录：`.local/full-chain-v23-20261017/`，已忽略且私有。真实数据统一标签 `TEST_V23_1017_222938`，每组追加 `_C01`～`_C11`；C12拒绝探针使用独立C12标签。

**判定：NO-GO（V2.3无条件全范围验收），原因是核心CASE12仍缺真实第二Factory及已登录无权账号证据，不是已发现库存或数量P0/P1。** CASE01～11真实API/MySQL场景PASS，14项浏览器关键操作均执行，隔离来源/权限测试通过。不能把隔离测试或历史V1.6/V2.2结果改写成CASE12真实PASS。用户定义的GO要求12组全部PASS，CONDITIONAL GO只允许非核心环境阻碍；本报告不放宽该判据。

已测的**单材料、五节点LOCAL生产与仓储闭环具备真实可重复能力**：正常100、报废/返工后94、质量门禁后70均可完成；库存、冻结投入和不可变事实可解释；并发和响应丢失未造成双记账。这个结论限于本报告范围，不能推出多材料、复杂BOM、逐件SN、多节点返工、真实ERP或全部部署均可靠。

## A. 环境、授权、工作区基线

- Backend `/Users/user/work/lixin/mes-base`，main；起始HEAD `1336af6bfaca5567aeaa1bc874c18f6daf3f5f19`。Frontend `/Users/user/work/lixin/mes-base-ui`，main，HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。保存两仓status、完整dirty diff及逐文件哈希：`{backend,frontend}-baseline.json/.diff`。
- 原后端30个、前端64个已有变更文件最终哈希全部保持。新增本轮 `mes-admin/src/test/java/dev/issuefixture/LocalFullChainV23HttpTest.java` 和本报告；未修改业务代码、核心数量公式、前端代码、正式配置或SQL迁移。
- 过程中后端HEAD由外部推进至 `5793986eda8b52eb6d24a84640b43f5d192e2cb3`（init），包含原V2.2工作及本轮新测试的较早版本。Agent未执行reset、commit、push或回滚该外部提交；后续两项并发测试是在该测试文件继续增量添加。HEAD差异与内容保护见`final-audit.json`，不把外部提交归因于Agent。
- 已读两仓AGENTS、个人入口及工程原则/协作/Java/前端/知识治理规范，复核V1.5、V1.6、V1.7、V1.8、V2.1、V2.2记录及正式Flyway。V1.5报告自身保留当时未完成状态，本轮不据它宣布完成；V1.6/V2.2只作为调用链参考，不算本轮执行证据。
- 初始仅只读核对；用户本轮明确回复“授权本轮 PROD 验收写入”后才建立TEST_V23数据。先存`business-before.json`，包含真实44张表（含Flyway历史）；后续业务快照排除Flyway，覆盖43张业务表。
- Factory `PROD` / Prod测试工厂，ENABLED / LOCAL；数据源1 ENABLED → `110.42.227.83:3306/mes_base_prod_business`；中心库`110.42.227.83:3306/mes_base_prod`。ERP provider/config为空且当前LOCAL合法，不切模式、不改绑定。业务Flyway V38、中心V40；本轮未迁移或执行写SQL。真实SQL只有SELECT/SHOW/metadata。
- 当前中心仅一个PROD Factory、一个admin用户100。沿用原PLATFORM角色100及既有PROD验收角色101（六项V2.2权限），没有新增、放宽或撤销权限。模式及绑定见`binding.json`、`master-final-readonly.tsv`；`master-preservation.json`逐字段确认Factory绑定/模式及admin角色权限与任务前完全相同。
- SIMPLE_B的192.168.18.25:3306本轮连接仍超时，沙箱外同样超时；没有切到其他库或自行新建验收Factory/数据库。
- 独立前端15173→后端18081，prod profile，运行JAR按现有脚本复制到私有只读副本。原5173/PID12465、8081/PID40599未操作。中途原Redis16379停止，认证GET返回503/RedisCommandTimeout，未进入业务命令；为独立后端启临时127.0.0.1:17379 Redis，重启独立后端并正常重新登录，未更改用户原服务或认证规则。验证码沿用用户此前明确关闭的配置。
- UNKNOWN注入仅使用本机临时代理18082；把一笔真实后端HTTP200响应丢弃，未Mock业务结果。收尾本轮18081/15173/18082/17379全部停止，浏览器临时页关闭、尺寸恢复；原5173/8081仍在监听。`runtime-cleanup.json`。

## B. 12组结果矩阵

此处“PASS”表示该组实际业务变化经正式API/UI执行并由MySQL核对；不表示每一步均通过浏览器。

| 场景 | 结果 | WorkOrder / Execution | 关键真实结果 |
|---|---|---|---|
| 01 标准单材料闭环 | PASS | 3 / 12 | E=Q=R=A=100，COMPLETED；原料100−100=0，成品100 |
| 02 分批交接/接收 | PASS | 4 / 3 | R40+35+25，A30+(10+35)+25=100，COMPLETED |
| 03 不良/报废/返工 | PASS | 5 / 4 | 五节点100；不良10、报废6、恢复4；E/Q/R/A94，COMPLETED，计划100 |
| 04 UNVERIFIED门禁 | PASS | 6 / 5 | A90+10、B80，不良10；C/D/E70；E/Q/R/A70，COMPLETED |
| 05 部分领料/合法退料 | PASS | 7 / 6 | 原料200−100+20=120；净领料80，冻结产品投入100未增加，READY |
| 06 多明细原子过账 | PASS | — / — | 30+80失败余额100/无动作；改30+20后POSTED，余额50 |
| 07 正式出库/领料竞争 | PASS | 8 / — | 库存100；出库70成功、领料60拒绝，余额30，无半截领料事实 |
| 08 作废/人工入库 | PASS | 9 / 7 | R10/A8；作废后人工入2，库存10；再入1拒绝，Execution保留RUNNING |
| 09 作废/报工竞争 | PASS | 10 / 8 | CANCEL先提交，竞争报工409；报工0条、Execution保留READY，原领料100保留 |
| 10 接收合法逆向 | PASS | 11 / 9 | 接收20→出库15余5；逆向10拒绝；正式恢复15后逆向10，再接收10；最终20/COMPLETED |
| 11 晚申报质量 | PASS | 12 / 10 | E90/Q0/历史Q100/R100/A100/库存100；新增放行/交接/接收拒绝 |
| 12 Factory/source/权限 | BLOCKED（真实部分已通过） | — / — | 真实伪造source400/失效Factory422/匿名401/原LOCAL UUID重放PASS；真实跨厂/已登录无权用户/混源组合BLOCKED |

## C. 数据准备、API/UI步骤与逐场景演算

每组使用独立原料/产品、LOCAL原料仓/成品仓、批次、五节点路线和UUID；公共单位及五个工序/用户资格只提供相同基础定义，不共享场景余额。原料余额全部通过OTHER_RECEIPT建立，库存来源CRUD只建零余额。API准备脚本`live.py`，真实请求/响应/状态/Factory/时间为`api.jsonl`，原UUID与结果为`live-state.json`，场景关联清单为`final-audit.json.caseEvidence`。没有直接SQL造业务状态或事实。

01. **标准链**：页面新建工单3、维护需求100/计划100并下达；页面创建及过账OTHER_RECEIPT单23为库存9入100；页面扫码正式投产，UUID `c87e467e-fdd2-4554-a496-219fe126814a`，Execution12、领料12，冻结比例1:1，实际路径A→B→C→D→E锁定。页面首工序报工100（流水35），其余四节点正式API报工100。页面正式放行100，UUID `ff008821-cd18-45f5-b7ec-7ff20c5412be`；正式API交接/实际接收100；页面正式完工，UUID `fa67642d-913f-495e-a709-192dcf38e532`，快照E/Q/R/A100。原完工与入库过账UUID重放不新增事实；终态新报工拒绝；同过账UUID改revision拒绝。`c01-final.log`、`ui/c01-{workorder-created,material,input,report100,completed100}.png`、`c01-api-closure-prep.log`。

02. **分批**：Execution3，页面两次交接40、35，原UUID分别 `32b433c8-7049-440b-8fd9-7857abbaefe8`、`508496ff-dbd4-426d-bf67-7797ebcea769`；页面对首笔实际接收30。API再交接25；A30和A75时正式完工均拒绝。对第二笔原交接直接收45被拒，实际第二批45拆成首笔剩余10+第二笔35；最后实际接收25。最终R40+35+25=100，A30+10+35+25=100，成品100，COMPLETED。交接不提前增加库存。`c02-final.log`、`ui/c02-handover40-35.png`、`ui/c02-accept30.png`。

03. **不良/返工**：Execution4五节点各正式报100；页面申报B不良10，申报ID `3d246278-976a-4b40-ab2e-aef1cdd4f7a6`，页面核实INDEPENDENT并分配报废6/返工4。返工未结束时放行拒绝；API正式START/RESULT恢复4后E94、Q仍0。正式放行94、交接94、接收94后完工；target仍100。五条原REPORT各100保持，损失6传播使下游历史超额6可解释；不把返工作为新报工。`c03-final.log`、`C03_final`、`ui/c03-{defect-created,quality-allocation}.png`。

04. **UNVERIFIED**：Execution5先A90/B80，B申报10待核实；B/C新增正向报工、放行、交接均拒绝；A合法追加10成功，未错误阻断上游。正式核实独立并报废10后，C/D/E各报70；E70/Q70/R70/A70，严格完工成功。原A90/B80记录仍在。`cases-api-resume.log`、`C04_final`及`api.jsonl`各错误码。

05. **领料/退料**：Execution6，原料入200，正式领料100，未开工且无报工/质量/交接，合法退20→余额120、净领料80。原领料与退料UUID重放余额不变，再退81拒绝。冻结产品投入100保持，查询productionInputQty100未增加；退料不重写calculation_json或原投入。保留READY供复验，不强行退已开工执行。`cases-api.log`、`C05_final`。

06. **原子性**：库存19入100，OTHER_ISSUE两行30+80；首次409后余额仍100、无明细生效动作、DRAFT。正式改第二行20（revision2→3），以新UUID整单过账30+20，最终50；成功UUID重放不变，POSTED普通修改409/删除405。失败载荷没有拿来冒充新版本。额外UI/UNKNOWN库存31独立批次：正式入10、页面创建出库单33/过账3，最终7，不混入核心库存19的50。初次本机脚本误按聚合响应取line字段抛KeyError，未发修改请求；按真实line响应id修正后继续，原失败日志保留，这是探针错误。`cases-api.log`、`cases-api-resume.log`、`unknown-*`。

07. **真实竞争**：库存21入100；线程Barrier同时发OTHER_ISSUE70和正式生产领料60，出库200，领料409 RESOURCE_CONFLICT。起止ns在`C07_race`；请求时间区间重叠，未用顺序执行冒充并发。最终30；失败方没有Execution、确认Input/Issue或库存动作残留（自增序号有间隙不等于业务事实）。`concurrency-mysql.tsv`、`C07_race`、`final-audit.json`。

08. **取消与人工入库**：Execution7，正式领料10、五节点报10、Q10/R10/A8。页面作废WorkOrder9（最终确认后API实读CANCELLED），全表前后仅`workorder`、`mes_workorder_cancellation`改变。页面创建OTHER_RECEIPT单31，显式选已作废LOCAL工单9；API添加匹配产品/仓库/批次/库位的实物明细2，页面过账，UUID `d99e8d6d-8b1a-46db-874b-cfbb305a39cb`。库存24=8+2=10，再入1拒绝且负例草稿正式作废。原UUID重放不增加；新领料/报工/完工拒绝。Execution保留RUNNING，原A8与pending2历史保留；人工2不会变成WarehouseAcceptance。`c08-before.json`→`c08-cancelled.json`→`c08-manual.json`，两个`c08-*-immutability.json`逐表证明；`c08-final.log`。

09. **取消并发**：Execution8正式投入100尚未报工，Barrier同时取消WO10与正式报工A100。实测取消先提交（22:33:45.708412），报工409 WORKORDER_CANCELLED，数据库REPORT0条。取消原UUID重放不新建，后续新报工继续拒绝。Execution保持READY、原领料保留，不自动返库。该轮没有声称观察到所有锁调度顺序；隔离四轮并发另保留结果。`C09_race`、`concurrency-mysql.tsv`。

10. **合法逆向**：Execution9接收20→正式OTHER_ISSUE消耗15，余5；逆向10被拒且原接收保留。正式OTHER_RECEIPT恢复15后，以新不可变逆向事实−10，原UUID重放仍库存10；逆向11超原净量拒绝。再实际接收10，净接收20、库存20，严格完工后新逆向1拒绝。恢复通过正式库存动作，无SQL余额改写。`C10_final`、`cases-api-resume.log`。

11. **晚质量**：Execution10先报工/放行/交接/接收100，后B申报并核实独立10、正式报废10。当前E90/Q0，历史Q100、R100/A100及库存100保持；excess10可见，新的超量放行、交接和接收全部拒绝。保留RUNNING和差额供复核，不改原五条100报工。`C11_final`、`cases-api-resume.log`。

12. **分层证据**：真实POST伪造EXTERNAL/NULL工单source、EXTERNAL仓source均400，无造出混源数据；不存在Factory头读取返回专用422；匿名过账/作废401；原LOCAL操作UUID重放不变。真实环境只有PROD/admin，因此没有真实第二工厂同ID跨库读取、已登录无权用户403、持久化EXTERNAL/NULL混源工单及ERP异常绑定场景。隔离SourceIsolationHttpTest、FactoryIntegrationConfigurationTest、StockDocumentHttpTest、WorkOrderCancellationHttpTest等本轮重新运行覆盖这些准入/权限合同；不能升格为真实CASE12 PASS。`c12.log`、`unauthenticated.jsonl`、`C12_result`。

### 场景库存与仓储单据索引

以下单据ID包括该组开账、负例及额外UI探针；精确状态、行号和UUID在state/API/MySQL快照中，不以单据ID数量代替成功事实。

| 场景 | 原料库存ID / 最终数量 | 成品库存ID / 最终数量 | StockDocument IDs |
|---|---|---|---|
| C01 | 9 / 0.000000 | 10 / 100.000000 | 23 |
| C02 | 11 / 0.000000 | 12 / 100.000000 | 15 |
| C03 | 13 / 0.000000 | 14 / 94.000000 | 16 |
| C04 | 15 / 0.000000 | 16 / 70.000000 | 17 |
| C05 | 17 / 120.000000 | 18 / 0.000000 | 18 |
| C06 | 19 / 50.000000 | 20 / 0.000000 | 25, 26, 30, 33 |
| C07 | 21 / 30.000000 | 22 / 0.000000 | 19, 27 |
| C08 | 23 / 90.000000 | 24 / 10.000000 | 20, 31, 32 |
| C09 | 25 / 0.000000 | 26 / 0.000000 | 21 |
| C10 | 27 / 80.000000 | 28 / 20.000000 | 22, 28, 29 |
| C11 | 29 / 0.000000 | 30 / 100.000000 | 24 |

## D. 生产数量与质量核对

- 可信投入取正式确认的Input；首节点限投入，后续节点限紧邻上游有效产出，不用工单计划倒推事实。C01/C02当前100；C03由10−4恢复得净损失6、E94；C04 B80−10得70；C11晚申报导致当前90而历史Q/R/A100不变。
- C03返工成功后明确读得Q0，随后才正式放行94；没有自动放行。C04待核实只阻断受影响节点及下游；上游A追加10成功。独立/重叠不重复扣减、报工调整/订正与返工失败分支由本轮重新执行的ProductionQualityV13HttpTest/ProductionQualityQuantityHttpTest验证（隔离）。
- 5条真实Completion均冻结E=Q=R=A，执行COMPLETED；分别100、100、94、70、20。计划与有效产出不强行相等。C08原取消后Execution状态和A8保持，C11历史超额10保留，均不能被人工库存单抹平。
- 本轮只读读取真实不可变触发器元数据（21条）；没有实库UPDATE/DELETE破坏探针，遵守“真实SQL只读”约束。真实API历史行与初始快照完全相同，隔离测试继续验证非法入口和事务回滚。

## E. 库存余额、动作与引用核对

`final-audit.json`对31个确切库存身份逐一计算来源创建时初始0+全部历史quantity_delta及available_delta；另以本轮任务前快照为初值，只叠加本轮新动作复核；53条动作前后余额链连续且非负，全部与当前双余额一致，失败0。包含原V2.2的8个来源及本轮23个零余额创建来源；不合并仓库/批次/库位。

27条POSTED明细逐项匹配stockActionId、document_line_id、operation_id=主单postRequestId、source_id、stock_id和绝对数量。所有WarehouseAcceptance的id=stockActionId且数量/来源一致。原始V2.2业务行逐行包含比较全部保持；不是只对总量相等。取消和人工入库分别只改变允许的2/4张表。

证据：`business-before.json`、`business-final.json`、`final-audit.json`、`stock-readonly-audit.json`、`fact-link-readonly-audit.json`（后两项是写入前旧样本只读审查，不算新场景）、`c08-*-immutability.json`。

## F. 并发、UUID、UNKNOWN与错误路径

- 真实竞争：C07库存100中70/60只能一方成功；C09取消/报工的请求区间重叠，取消后无新增REPORT。错误不等于自动回滚外部HTTP；本轮LOCAL命令均为业务DB事务，未调用真实ERP。
- 成功原UUID重放：C01完工/库存过账，C02～C11各受控命令，C05投产/退料，C08人工单，C10逆向。改载荷或改revision的同UUID拒绝；多明细失败后修改版本采用新UUID。
- 真正响应丢失：页面建出库单33，明细35/库存31。代理将真实已提交200响应丢弃，记录`unknown-committed-response.json`；UUID `88ee1cb0-e5a3-45ec-ac97-47ea215ed654`，库存10→7。页面出现错误并保留原UUID；刷新后重新打开POSTED单仍显示同UUID，只允许核实原请求。页面确认及后续正式API原样重放后，MySQL仍一条−3动作、余额7。`ui/unknown-{before-reload,restored}.txt`、`ui/unknown-{after-commit,restored,resolved}.png`、`unknown-mysql-final.tsv`。
- 未制造真实ERP UNKNOWN、不切换真实Factory；浏览器切厂/撤权/迟到响应及UNKNOWN在后续4xx仍保留由本轮前端369测试覆盖，不把单Factory页面重载说成真实跨厂故障注入。
- 两项追加隔离并发：交接冲销60与实际接收60竞争同一R80，必须200/409且A≤R/库存=A；质量不良10与放行100同时提交，允许先放行后依据失效或先变更而拒绝放行，最终Q必须0、历史REPORT不变。交付实测冲销/接收为409/200；放行先成功200后质量依据变化，当前Q0；四轮取消/报工均取消200、报工409。见`verify-delivery.log`中TEST_V23 exploratory/CASE09。

## G. Factory、来源、权限

PROD物理绑定与LOCAL模式在本轮始终未改；普通创建来源由服务端固定，UI不授予权限，Factory头不伪造权限。没有创建EXTERNAL测试记录、没有猜测历史source、没有把缺ERP配置降级成LOCAL。真实唯一工厂/唯一admin环境限制已列CASE12；本轮不通过新建数据库、切模式或给无权用户授予权限制造通过。

需要补全CASE12真实验收时，应使用用户明确指定、已配置且可用的第二工厂/业务库和具备已知授权差异的真实验收账号；先核对物理绑定及来源，再执行只读跨厂/403和混源拒绝。当前只保留该缺口，没有擅自创建账号/Factory/外部绑定。

## H. 浏览器、MySQL/API与自动化证据

14项关键操作均在真实页面执行：工单新建/下达、用料保存、扫码正式投产、首工序正式报工、不良申报/核实/分配、正式质量放行、两笔分批交接、实际接收30、Execution完工、工单作废及最终确认、其他入库创建/过账、其他出库主单创建/过账/UNKNOWN核实、选择作废工单的人工入库、查看库存动作/来源。

明确混合边界：C01入库主单/明细及过账均UI；人工入库和UNKNOWN出库的明细由正式API准备，主单创建、关联工单选择与过账为UI；C03返工START/RESULT、其他工序/其他场景主要为API。没有Mock页面或把首页明确标注的示例数据当证据。UI字段/成功状态截图和正式GET响应、MySQL回读共同证明提交；未为早期所有UI操作保存浏览器原始网络包，不虚构该层证据。

1280/1440/1920检查真实人工入库页，document scrollWidth=clientWidth；表内横向滚动、长编码省略显示，未将其说成每个页面所有宽度都测完。`ui/live-responsive.json`及`ui/c08-manual-posted-{1280,1440,1920}.png`。完工截图`ui/c01-completed100.png`，动作截图`ui/c08-stock-action.png`，UNKNOWN证据如F。

- `mvn -B -ntp verify`：734项，689通过、45条件跳过，失败0、错误0；8模块BUILD SUCCESS，证据`verify-delivery.log`。45项条件跳过不算真实MySQL通过；此前`verify.log`/`verify-final.log`及定向新测试3项通过记录保留。
- 前端Node22.23.1：62文件/369测试全部通过，`ui-tests.log`；`npm run build`通过，`ui-build.log`，只有既有大chunk提示。前端代码未修改。
- 新测试LocalFullChainV23HttpTest：分批原交接预算、取消/报工四轮并发、退料冻结投入、交接冲销/接收竞争、质量依据/放行竞争共5项，均为独立H2+真实Service/生成XML/Spring事务；中心身份为既有Fixture，不能冒充真实登录权限或MySQL。
- IntelliJ格式化只处理本轮新测试；后端diff --check通过。前端全仓diff --check仍有任务前领料页第3行尾空格，哈希未变，未顺手改动。

## I. 缺陷及本轮改动

**P2：作废工单的数量投影仍返回writable=true。** 实库WO1已CANCELLED，GET `/api/production/report-quantities/executions/1`返回LOCKED/writable=true；WO9/C09同类状态也不能仅靠此字段作为正式操作许可。最小复现保存在`readonly-api.jsonl`，请求及原响应未删。

源码：ProductionQuantityController→ProductionQuantityService.load→ExecutionPathService.view/ProductionQuantityMapper；writable当前以路径LOCKED派生，未表达工单取消/执行终态。正式正向命令另经ProductionChainLocks/来源规则：C08/C09新投产、报工、完工实测拒绝，故本轮证据不支持“已越权写入”或P1库存缺陷。建议后续澄清字段是路径数量可计算还是业务可写，并在页面结合正式生命周期门禁；本轮按P2只记录，不改核心公式或扩大修复。

未发现经本轮证实的未解决P0/P1。Redis16379中断是运行环境故障，恢复连接没有修改认证/会话安全规则。本机脚本KeyError是探针响应形状误解，修正后原失败和续跑日志同时保留。新增5项有意义的回归测试；业务源码、前端、Schema、角色权限不改。报告及证据不是业务代码修复。

## J. 未验证边界与复验入口

- CASE12真实第二Factory/同ID跨库读取、已登录无权账号403、真实EXTERNAL/NULL持久化组合、ERP非法配置失败关闭：环境不具备，未通过改模式或伪造来源造数。隔离通过独立列证，不补记真实PASS。
- 真实ERP领退料、远端UUID、跨系统补偿、多材料/BOM、SN级放行、多节点返工、跨设备UNKNOWN恢复不在本轮实现范围。
- 扫码不同批次建立不同Execution、同扫码复用、用料版本冻结、批次库位隔离、顺序错误、重叠不良及同UUID并发由本轮重新运行的既有隔离测试覆盖。交接冲销/接收及质量依据/放行补充隔离竞争；未将它们写成真实多节点部署或真实MySQL竞争。真实竞争专指C07/C09。
- 原已有事实的触发器活动破坏探针未在真实库运行；本轮只读查触发器/对比历史。默认跳过45项条件测试按未执行记。
- 本轮测试数据全部保留供用户复验，未清空已过账单据、失败草稿/取消事实或晚不良差额；复验应加载支持V2.2的新后端包，选择PROD并按TEST_V23_1017_222938检索。原8081运行的是原用户二进制，本轮没有替换部署它。
- 全部真实请求UUID、单据ID、前后状态、数量、失败错误码分别位于`live-state.json`、`api.jsonl`、`final-audit.json`、`business-*.json`；截图/DOM为`ui/`。真实账号、Token、配置凭证只在忽略私有目录，不进Git或本报告。

## K. 判定与交付回执

**已测单材料LOCAL生产/仓储闭环可信且可重复；V2.3无条件全范围验收NO-GO。** 11组真实场景PASS、库存与事实一致、无已证实P0/P1；第12组部分真实检查PASS，但核心多厂/授权差异场景仍BLOCKED。按用户GO定义不能宣称12/12通过，也不能用Fixture或旧报告填补缺口。

交付本报告A～K、5项隔离测试及独立私有证据包；未修改业务代码/前端/正式配置/SQL迁移，未由Agent提交或推送。原30/64个变更文件哈希保持；本轮独立服务和浏览器已清理，原5173/8081保留。回退仅需审查本轮测试与文档差异，不回滚真实业务事实，不恢复已废弃入口或重写迁移。
