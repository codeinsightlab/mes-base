# Base MES V1.8 — WorkOrder 正式作废与历史测试工单清理

日期：2026-10-17。作废只终止未来正常生产，不自动逆向库存、投入、报工、质量或仓储事实。最终执行统计及判定见 J–M。

## A. 代码与并行修改保护

基于当前 main 工作区（包含已验收但未提交的 V1.5/V1.6/V1.7 修改）增量实现。开始前保存两仓 HEAD/status/diff及 tracked/untracked 文件SHA256于本机 `.local/cancellation-v18-20261017/baseline.json`、`*-before.patch`。不重置、提交、推送或部署，不处理无关修改。

后端HEAD `e36ea0ace325616bd584b3d2a1c992b76157438a`，前端HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`，结束时均未改变。最终任务增量为后端30文件、前端5文件（含代码、迁移、生成持久化、测试与文档）；其余基线文件保持，详见task-delta.json/preservation-audit.json。Java显式导入与生成标记保护已检查。后端diff检查通过；前端仅有任务前已有的领料页面第3行尾空格，本轮未修改该文件，不顺手覆盖并行工作。

读取两仓AGENTS、个人工程规范、[业务边界](../architecture/local-erp-business-boundary.md)、[ADR-005](../architecture/ADR-005-erp-integration-boundary.md)、V1.6/V1.7报告与V1.7 N节。复用既有Controller→Service→Mapper、UUID协议、生产链锁和库存Owner，没有复制Production服务、修改数量公式或重写质量传播。

## B. WorkOrder状态及取消事实

WorkOrderStatus增加CANCELLED，保留DRAFT/RELEASED。正式命令仅允许DRAFT或RELEASED→CANCELLED，关联Execution处于RUNNING或已有事实不阻止作废；不修改Execution状态，也不制造完工事实。普通编辑/下达/删除及可信同步不能恢复CANCELLED。

新增不可变 `mes_workorder_cancellation`：id即请求UUID，包含workorder_id、request_fingerprint、reason、operator_user_id、recorded_at、previous_status、source_snapshot、previous_revision。主键保护UUID；按workorder_id建立普通索引。每工单取消事实唯一性由工单行锁、终态和同事务状态更新保证，不额外添加未授权UNIQUE。

`WorkOrderCancellationService`在业务事务中锁工单，先核对原UUID，再检查状态/来源/可选版本，插入事实及条件更新工单状态、revision+1和update_time。服务端确定操作者和微秒时间；不信任客户端状态、source、Factory或身份。失败回滚两项写入。

## C. 权限与来源兼容

正式API：`POST /api/production/workorder/{id}/actions/cancel`，独立权限 `production:workorder:cancel`；读取事实为 `GET /api/production/workorder/{id}/cancellation`，沿用workorder:query。响应只读白名单：workOrderId/status/cancellationId/cancelledAt/cancelledBy/reason/previousStatus/sourceSnapshot。

LOCAL在当前LOCAL模式可取消。NULL仅经该终止分支允许，不变为LOCAL，不创建生产、库存或ERP交易。EXTERNAL缺可信远端取消契约明确拒绝；异常模式不回退。原UUID重放先于新模式/状态准入。

中心V37仅登记权限，没有自动赋权。核对admin原有效作废授权为0后，用户在本次会话明确授权单项验收角色。通过正式角色新增及用户绑定API创建角色151 `TEST_LOCAL工单作废验收`（key TEST_CANCEL_V18_SIMPLE_B，kind FACTORY，factoryId SIMPLE_B），仅menu2634对应上述1项权限，绑定user100；保留原角色。数据库读回确认仅该项授权，不对其他用户或工厂赋权。

## D. 生产链写入门禁

复用ProductionChainLocks及BusinessSourceRules：取消事务与正向命令争用同一WorkOrder行锁，保持WorkOrder→Execution→Issue→Stock顺序；没有全局synchronized或跨ERP事务。

- Execution创建、启动、路径选择、影响生产的编辑及新增派工在持锁写入前检查所属工单。
- ProductionInput新预览/计算、prepare、首次投入、MaterialIssue主单/明细/首次提交校验取消状态。已提交原意图核实不视为新意图。
- 正式报工、管理员新增报工和增加产出的调整拒绝；原负向纠错/作废仍按既有规则。
- QualityRelease新增放行、ProductionReceipt正向交接、WarehouseAcceptance正向接收及Owner正向收货、ProductionCompletion新完工拒绝。
- Owner直接验证新投入时先取得工单锁；实际接收入口先按链锁取得Execution，原UUID识别后才执行新正向状态门禁，避免只靠外层HTTP约束。

默认工单列表排除CANCELLED，显式status=CANCELLED仍可历史查询；新生产工单候选及后端准入均排除。来源、锁定路径、数量、质量、权限与资源范围的原规则继续执行。

## E. 历史事实及库存保护

取消不删除Execution/路径/领料，不改报工、质量、交接、接收或库存。历史查询继续可用；界面明确“所属工单已作废”并保留原Execution状态。

原UUID重放、原UNKNOWN核实、既有合法退料、库存冲销、接收逆向、负向报工纠错和质量历史纠错不由取消状态笼统阻断，仍须满足原事实、冻结来源、数量余额及终态约束。历史恢复不是新增投产或重复外部创建。未自动进行任何退料、库存返还或逆向补偿。

## F. 并发、幂等和UNKNOWN

同UUID同载荷/操作者返回原事实；同UUID不同载荷拒绝；不同UUID对已取消工单拒绝。不同工厂独立业务库中的相同本地主键/UUID不跨库引用。可选expectedRevision在首次执行检查，重放仍使用原请求。

自动化覆盖双取消竞争仅一条事实、作废更新失败时取消事实回滚、取消后的原UNKNOWN投入只核实一次创建、原接收UUID重放及合法接收逆向。真实MySQL专项竞争取消与新Execution创建，验证工单锁串行：已提交正向事实保留，取消先提交则新创建拒绝。取消事实UPDATE/DELETE由真实数据库Trigger拒绝。

## G. Schema、Codegen及Flyway

业务 [V37](../../mes-admin/src/main/resources/db/business/V37__workorder_cancellation.sql) 已通过指定SIMPLE_B正式Flyway执行并validate；中心 [V37](../../mes-admin/src/main/resources/db/system/V37__workorder_cancellation_permission.sql) 经FactoryFlyway正式机制执行并validate。status为字符串，无需改列类型。未修改任何已执行旧迁移或V1.7来源不可变Trigger。

所有新增表/字段中文COMMENT，无业务factory_id，无新外键或次级UNIQUE。事实表UPDATE/DELETE Trigger保护历史。

使用mes-crud-codegen Skill及 [V1.8配置](../development/workorder-cancellation-v18-codegen.yaml)，从已迁移的真实metadata生成完整persistence/backend/test，GENERATED 1、拒绝0。完整scaffold在target/cancellation-v18-codegen；采用Entity/Example/Mapper/XML基础，正式只开放专用取消命令/事实查询，不发布普通事实CRUD。固定业务包自动扫描，不新增生产Mapper清单。

## H. 前端作废交互

工单列表及详情复用WorkOrderCancellation组件，DRAFT/RELEASED、LOCAL/NULL且有独立取消权限时开放。原因必填，二次确认明确“不退料、不冲销报工、不返还库存”；成功刷新工单及Execution摘要。

CANCELLED显示作废人、时间、原因和原状态，隐藏编辑/下达/删除及用料维护；固定到已作废工单的生产执行页面禁用新增编辑。执行列表批量读取并展示所属工单状态，不把READY/RUNNING伪装为CANCELLED。历史查询仍可筛选CANCELLED。

沿用useClosureCommand，sessionStorage按账号/工厂/工单保存原UUID和冻结载荷。关闭、重开、切厂、撤权和迟到响应不创建第二次意图；UNKNOWN只核实原请求，已作废或当前模式改变仍可重放原UUID。取消事实仅在详情读取，列表不逐行读取取消事实。

## I. 自动化、MySQL、API和UI验收

证据目录：`/Users/user/work/lixin/mes-base/.local/cancellation-v18-20261017/`，备份和令牌不提交。

- `mvn -B -ntp verify`：8模块SUCCESS；verify-final.log，admin阶段460项、0失败/错误、42项显式环境专项跳过。
- 后续扩展取消HTTP：6项通过，包含事务回滚、UNKNOWN核实和历史逆向；mysql-directed.log。
- 前端：59文件、354项通过，TypeScript检查及构建成功；ui-checked.log、ui-checked-build.log。保留已有大chunk提示。
- 打包冒烟：10 passed/0 failed；smoke.log。
- 真实MySQL：LOCAL Owner 2、Quality V1.3 2、V1.7来源Trigger 1通过；取消并发/不可变Trigger及Receipt专项最终结果见mysql-rerun.log。
- 初次Receipt真实测试因与本轮独立API创建库存重叠，全库库存不变断言失败；没有修改断言或抹去日志，停止并发验收写入后串行重跑。新的取消MySQL测试最初按实现类获取JDK事务代理失败，改为按业务接口获取后重跑；均为测试执行/装配问题，不冒充已通过。
- 全量回归中原请求预算夹具缺status，经默认排除CANCELLED条件暴露；改为显式DRAFT/LOCAL，保留原查询预算断言后通过。

真实独立验收使用自有15173前端→18081后端，不改用户5173/其他服务。新TEST ONLY工单227、Execution257，经正式原料入库100、领料20/投产10、五节点报工10、质量放行10、交接10、实际接收8，保持RUNNING。浏览器填写原因并二次确认后正式作废；数据库及API确认原Execution/事实不变，原料80、成品8。六类新正向API均WORKORDER_CANCELLED；原投入/报工/放行/交接/接收和取消UUID重放正常，跨厂请求403。independent-result.json及ui/cancelled-history-{1280,1440,1920}.png提供证据。原UNKNOWN核实为明确Fixture证据，不冒充真实ERP服务。

## J. 176张测试工单实际作废结果

**176/176成功，失败0，新增取消事实176条，原source=NULL保持176张。** 严格从V1.7原附录提取ID，逐项与原清单、实时数据库及API读取核对；41张DRAFT、135张RELEASED，均为用户明确确认的测试数据。没有扩展目标，没有作废LOCAL正式验收工单208/209/218。

执行前重新核对SIMPLE_B→datasource8→192.168.18.25:3306/mes_base_dev_business_simple_b、启用状态及LOCAL锁定模式。保存28表一致性快照batch-before.json（权限600），再一次性保存batch-plan.json的176个独立UUID和原版本；每张工单通过正式API独立事务取消，固定原因为“V1.7历史测试工单清理，用户授权作废”。每次记录工单ID、请求UUID、载荷、HTTP200及原事实响应；没有SQL UPDATE status或直接Service调用。重试仅允许原UUID，不另建意图；本次无目标失败。

batch-results.json保存完整176条回执；首张原UUID再次重放仍返回同一取消事实。默认活跃列表已排除全部目标；显式CANCELLED历史查询总数179（含本轮其他3张独立TEST ONLY取消样本），不能将该179误报为本次授权目标数量。UI抽查207显示原NULL来源、作废人100、固定清理原因及原Execution RUNNING，历史仍可读取。

## K. 前后数据库一致性核对

批量前后分别以只读REPEATABLE READ事务取得同一真实业务库28表快照，逐行逐字段比较：

|对象|预期及实际变化|
|---|---|
|workorder|仅目标176张status→CANCELLED、revision+1、update_time更新；其他字段逐项不变，source仍NULL|
|mes_workorder_cancellation|仅新增176条；每目标恰好1条，原状态/版本、操作者100、固定原因及UUID符合执行计划|
|其余26表|完全一致；没有新增、删除或改写任何记录|

对比包括全部Execution157条、库存30条、库存动作107条、投入116条、报工326条、缺陷188条、质量处置176条、质量放行4条、交接84条、仓储接收9条及路径/批次/领料等。上述数量为专项测试及独立验收完成后的批量基线；专项测试产生的显式TEST ONLY事实发生在该基线之前，不冒充清理176张导致的变化。

工单208/209/218逐字段及关联事实不变；旧Execution233/247仍COMPLETED，E/Q/R/A分别94/10；库存22仍615。工厂绑定、LOCAL模式、锁定状态及来源配置不变。

独立227的作废前后原料80/成品8，事实保持。之后另行用正式原接收逆向API显式退回1并重放同UUID，余额7，证明作废不阻断合法历史恢复；此变化单独记录于historical-reverse-result.json，发生在批量基线之前，不能归因于取消。

batch-verification.json：cancelled176、failed0、cancellationFactsAdded176、sourceNullPreserved176、allOther26TablesExactlyUnchanged=true。快照SHA256：before `1013b8be0a30d7463372edff42c7359aabd50d8372516a12548e283fcb9b5496`；after `801e714abd20371ce80936ab77c17ea14dd5328989266e166093ed09183b54d1`。SQL只用于正式迁移、明确TEST ONLY专项夹具及只读校验，176张取消全部由正式业务API执行。

## L. 仍存在的业务边界

没有ERP远端取消、自动仓储补偿、WIP迁移、复杂工单关闭/归档或工单恢复。取消不消除实物、在制、质量差额或UNKNOWN责任，现场仍须通过正式单据处理。

取消终态不提供恢复为DRAFT/RELEASED；回退代码不能作为抹除取消事实或重开这些工单的方式。已执行V37和历史事实保留，不手工删除迁移记录或取消事实。

其他业务库未在本轮执行V37，部署新二进制前须按正式迁移机制治理。真实Factory1负例仅证明当前账号被403拒绝，不宣称其他业务库完整功能已验收。已有本地事务不能回滚外部HTTP的边界保持。

## M. 判定

**GO（V1.8正式作废能力及本次SIMPLE_B指定176张清理范围）。** 已实现、自动化及真实MySQL/API/UI通过，176/176取消及全表一致性验证成功，无失败ID。

真实MySQL最终为7项通过：Owner2、Quality V1.3两项、Source1，以及串行重跑的Receipt1、Cancellation1，均0跳过。取消专项验证真实行锁竞争、同UUID重放和事实UPDATE/DELETE拒绝；后续取消HTTP六项另行通过。最初失败保留日志及原因，不修改断言伪造通过。

只停止未来生产，不承诺ERP取消或自动补偿；其他业务库正式迁移和后续发布仍单独执行。本轮没有Git提交、推送或部署。独立前端15173、后端18081在验收完成后关闭，浏览器临时标签关闭并恢复视口；自有进程记录见cleanup.json，用户服务不动。

### 逐ID成功回执

每条结果均source=NULL、status=CANCELLED、HTTP200；下表UUID同时为requestId与cancellationId，固定原因如J节。完整请求/响应和时间保存在本机batch-results.json。

|WorkOrder ID|requestId / cancellationId|Result|
|---|---|---|
|1|37972d46-fb1f-47c0-b416-f5f8ca9c77ce|CANCELLED / 200|
|2|cec47225-cda5-48a8-a5dd-4cd8f0494eb8|CANCELLED / 200|
|4|17ca9b93-c205-4c30-a185-259b40e64f50|CANCELLED / 200|
|6|14c850cc-5cdb-4aa4-8868-56f10d599a64|CANCELLED / 200|
|8|2c6e9915-7466-4964-ac50-b067d842e69c|CANCELLED / 200|
|10|f10b8802-f75f-4b9b-807c-7667ba657b6e|CANCELLED / 200|
|11|52485f57-28e9-4712-bb54-49073e600c0c|CANCELLED / 200|
|13|89bf0938-34c5-43a0-8599-031f5f1ead67|CANCELLED / 200|
|14|faa2734d-eb47-4d20-b830-92fde06780f5|CANCELLED / 200|
|16|87f2031c-24a5-47ed-9c80-33004215d57e|CANCELLED / 200|
|18|8d83b626-a682-4655-a638-b2cace6f0eee|CANCELLED / 200|
|19|3721791e-dc7f-4a16-a671-9b4493a208e0|CANCELLED / 200|
|20|1c97bf3f-0050-4d35-9ecf-77a074b08ee1|CANCELLED / 200|
|21|278b26de-f69d-4857-9625-f6e47620ea1c|CANCELLED / 200|
|22|a6cbb633-747c-4e0b-ba8f-1ddf69229862|CANCELLED / 200|
|23|42e8fc4b-1ab8-43bb-ac2b-cad306418c75|CANCELLED / 200|
|25|79d2f1fb-57c3-4183-8be4-210357802cb1|CANCELLED / 200|
|26|999aabd9-4e99-4ab8-a952-20b9ac619a37|CANCELLED / 200|
|27|cb2cc2ed-0bf8-4e3c-b74d-123757bc94f7|CANCELLED / 200|
|29|5a656cc7-3e7f-4a5e-9a14-9602c09310ce|CANCELLED / 200|
|30|d8875891-c34f-4923-a1cd-6858433ebc3d|CANCELLED / 200|
|32|7826e0ac-ae2d-450a-9529-99a3827c5331|CANCELLED / 200|
|33|cb40a6f4-45ff-42ab-9050-1769d28c4a9b|CANCELLED / 200|
|35|809267db-e7c4-4f98-b06b-e95803724109|CANCELLED / 200|
|36|bf695207-f2d4-407f-839d-6a8d2a2998f4|CANCELLED / 200|
|38|c5c8a180-ecd4-439a-af39-c038979a2580|CANCELLED / 200|
|39|7dbbbb1d-406e-4b4a-b4df-1eeb1ca82ae2|CANCELLED / 200|
|41|e165ea88-7ebf-4bca-bc1f-f5b5124a07e1|CANCELLED / 200|
|42|2d385654-d9c6-4590-b84d-2dc75c21c856|CANCELLED / 200|
|44|43f600fe-dc8c-448a-a3a6-820180958c0f|CANCELLED / 200|
|45|4caa8bc9-3f10-4589-afc4-a13c366e6b88|CANCELLED / 200|
|47|0e0e6f21-89a4-463c-813f-c047ea145160|CANCELLED / 200|
|48|3bf7fa46-b748-499d-be9a-2779c6726f8b|CANCELLED / 200|
|50|4d91c0e8-1f17-40c0-9294-222a330af9bd|CANCELLED / 200|
|51|c9ff1e51-a62f-4e7b-ab15-4a5bf8d044ae|CANCELLED / 200|
|53|53ee0206-45bc-45db-a2a1-a3512296cdf2|CANCELLED / 200|
|54|11e31e33-030a-4f69-b5ea-5ff2e0a3ecff|CANCELLED / 200|
|56|b4b4086d-0d8d-49c9-83cf-e81b93b8e9eb|CANCELLED / 200|
|57|55d98439-caa6-41bf-a3a0-ece9d2229210|CANCELLED / 200|
|58|680a398e-9440-4496-bc10-8e9abc9cf634|CANCELLED / 200|
|59|3ac4c8de-148f-4d4c-83c1-893dc3aafc07|CANCELLED / 200|
|60|52a03678-281c-4bd5-b37b-9be25947701c|CANCELLED / 200|
|61|dcee77b3-bf1a-4c8b-bf6c-c066fa55ca1b|CANCELLED / 200|
|62|0e3b0756-1f9e-45ea-8288-36bf281ff38e|CANCELLED / 200|
|64|d5abd298-11a6-4508-8625-c31a31235c0d|CANCELLED / 200|
|65|ad227a75-55d2-42f9-aa8e-be08f78e1750|CANCELLED / 200|
|66|5adac49f-5a38-4123-9763-d47b43b1e7ac|CANCELLED / 200|
|67|e7eb6cd3-6428-47a9-8138-739710b76907|CANCELLED / 200|
|68|7d45ea34-9663-400c-b5b6-d066d4a5497f|CANCELLED / 200|
|69|2e28521b-b98c-4cca-97ce-911aac5b05d5|CANCELLED / 200|
|71|28ba9313-9246-4ebe-a514-cf943fc020ae|CANCELLED / 200|
|72|5874456e-ccb0-4169-8ad3-6efa6b7be76d|CANCELLED / 200|
|73|ddf733c4-fa8b-4d34-baba-ffb53f8cc26c|CANCELLED / 200|
|77|276dd522-9453-4c90-9dc8-d52cc56eeb8d|CANCELLED / 200|
|80|2df09b8d-4da7-476d-847b-db34ad4a97eb|CANCELLED / 200|
|81|6d2ecb3c-901d-4fb9-97e8-3ff267f6d3e5|CANCELLED / 200|
|83|87088503-7367-46ba-83f2-2561d7264cee|CANCELLED / 200|
|84|637461ba-7821-4a96-a500-28c412d6ba85|CANCELLED / 200|
|85|69c2df50-5101-48e8-9394-178bc697ec26|CANCELLED / 200|
|86|3ea98f06-044b-4a53-999d-88cb11affd79|CANCELLED / 200|
|87|815d39f5-c9c7-4ea4-ac59-01fc17929219|CANCELLED / 200|
|88|82079a60-c0e1-4e9a-ab22-047ba1fdac07|CANCELLED / 200|
|89|d6a2e3a8-5709-45a8-a0f9-f1fb1684258c|CANCELLED / 200|
|90|fd76268d-5ba4-446f-b4af-c1b5aa1a9f1e|CANCELLED / 200|
|91|65dfc037-f0b6-4d41-98d8-a0ce87d04ce6|CANCELLED / 200|
|92|c73633fa-e09e-47a8-a9bb-187127a36ae9|CANCELLED / 200|
|93|3c596f6b-cd60-4412-bad0-d4ac88741a4c|CANCELLED / 200|
|94|0d682bb6-cd8a-47bb-bf40-736d46b429ee|CANCELLED / 200|
|95|a4588b55-58f5-4c97-920c-d6975c74387c|CANCELLED / 200|
|96|593ecd5b-e58a-42d7-bc0e-ca250e2e2238|CANCELLED / 200|
|97|47bc6a39-eb6a-42ad-a3d5-e1ac9fefa4ab|CANCELLED / 200|
|98|c1491ffd-d333-4834-a884-48e02bbb2de1|CANCELLED / 200|
|99|38f78add-5546-4314-837f-e1524d126c3b|CANCELLED / 200|
|100|219567f8-3605-4d00-bb5e-014df267dd03|CANCELLED / 200|
|101|58bd21d3-22a6-42ac-81a4-7842e1f33641|CANCELLED / 200|
|102|1f4dffbe-0a83-4745-9739-080d49791211|CANCELLED / 200|
|103|43fa081c-6597-4b3d-861b-7ae4494c8d1b|CANCELLED / 200|
|104|e83fab56-93fd-4e88-b4c9-cd1bc0a7501a|CANCELLED / 200|
|105|f6faf10b-22c2-4f8a-888a-ee0ed8456ad6|CANCELLED / 200|
|106|029ab0b9-1469-4091-998c-7763cfa608a2|CANCELLED / 200|
|107|f4eee162-900f-4ea9-ad22-67eb24dafcae|CANCELLED / 200|
|108|70854813-ea12-4367-9ddb-58cf2342a563|CANCELLED / 200|
|109|42a85175-d56b-4617-b88b-5e51a19fd957|CANCELLED / 200|
|110|8ac5e539-3cc7-4b66-bf96-3fc933099ed0|CANCELLED / 200|
|111|b974e8b5-5c74-4561-9610-dd698fd3b83b|CANCELLED / 200|
|112|81a0f7d4-80e6-45b5-8185-7b944d4c772a|CANCELLED / 200|
|113|f58ae216-aa5b-41b6-a6ea-5d0e04f512c7|CANCELLED / 200|
|114|355d5634-b860-43dc-8160-c83b215985aa|CANCELLED / 200|
|115|3db5860d-31f9-4770-8a1a-11fe0ddc2902|CANCELLED / 200|
|116|79313f13-4ea8-4a4a-97da-4505299ff224|CANCELLED / 200|
|117|7b3de12e-ef6a-41c0-aca2-6ed9bda01752|CANCELLED / 200|
|118|88cf2283-79e7-4e41-b1f3-c689d5724e3c|CANCELLED / 200|
|119|1cdc6a84-c350-4991-9a3b-d940e2119b18|CANCELLED / 200|
|120|4298f6a8-153c-4a9c-85b2-dac5d13d5d90|CANCELLED / 200|
|121|2cd10db9-d891-4079-8620-bb53e2063f9f|CANCELLED / 200|
|122|9cb28db2-d735-47b2-9116-c3d257865fbe|CANCELLED / 200|
|123|0a1f6113-aee9-4e62-a704-631845097e9d|CANCELLED / 200|
|124|5e8b7ee2-8bfb-4419-9e17-d131d4347524|CANCELLED / 200|
|125|f6535297-afdd-4112-99ff-534d86bc2d11|CANCELLED / 200|
|126|95099f1f-d3e4-4edc-9acd-2f410620c936|CANCELLED / 200|
|127|f3ccdb44-7577-4642-9b50-1ee1fa2b2879|CANCELLED / 200|
|128|62685557-da3f-4fa4-a1be-6ff5e35f7968|CANCELLED / 200|
|129|b8526910-54fb-4b51-a8c3-313a7b34230a|CANCELLED / 200|
|130|e504baf9-90cf-405b-88d2-9781d92d85c7|CANCELLED / 200|
|131|dabce275-6d2f-4bfb-83dc-d603932c6377|CANCELLED / 200|
|133|8fb1a41a-aaf8-4041-8de9-86cbf593c44c|CANCELLED / 200|
|134|c68e3f04-4a75-496d-9ad0-a578fc1570bc|CANCELLED / 200|
|135|a78a0732-9359-46f1-94cc-21f917ffc747|CANCELLED / 200|
|136|d47e47ef-73cf-453b-9457-e0459b4d38bd|CANCELLED / 200|
|137|8f8c4310-74dd-44bb-b135-50678e34f40d|CANCELLED / 200|
|138|5434e65b-c5f7-4522-b185-aa34f94337c0|CANCELLED / 200|
|139|aef3bbaf-2093-490b-8733-0611e035654a|CANCELLED / 200|
|140|7594ff45-3d0d-47b8-88fc-b12a4bc6cbdf|CANCELLED / 200|
|141|e65f2e36-27bf-49e3-90f3-3e9a4e8c03e8|CANCELLED / 200|
|142|3eaf2f05-cf4e-4658-9c18-dcf97dbc0119|CANCELLED / 200|
|143|f217d067-e503-46cd-bcb9-14d024ab4f32|CANCELLED / 200|
|144|17e67ca5-3930-4c5f-ab68-80d4c1890a58|CANCELLED / 200|
|145|4bd712b0-906b-4985-97bb-f15ada8b8b2c|CANCELLED / 200|
|146|3c9410c7-a191-4aa8-9e1c-f0b139e02412|CANCELLED / 200|
|148|42780671-fbf2-4521-86c9-2027d9fbe802|CANCELLED / 200|
|149|eba93633-ed97-442a-bf76-32f25a488b06|CANCELLED / 200|
|150|6f093d60-16f0-479f-a2cd-f7a841038f25|CANCELLED / 200|
|151|2f15720c-d38a-459a-a969-667f037a441f|CANCELLED / 200|
|152|d2c56c34-e297-48c8-865e-b4d0370fa99d|CANCELLED / 200|
|153|6aa22ffc-2031-4e39-908e-765b4126e6e0|CANCELLED / 200|
|154|9bed0865-dbf3-4814-b89c-8f15c3ef3ce0|CANCELLED / 200|
|155|dfc4d9d7-f530-4e93-be36-c0c382dd133c|CANCELLED / 200|
|156|aeccdb19-7f6d-481a-9c44-f19d62a21f8d|CANCELLED / 200|
|157|ae8abbd5-9ca7-4978-879d-aa3cf47acc84|CANCELLED / 200|
|158|87243ec1-2307-4f36-b59f-bb959c16918c|CANCELLED / 200|
|159|1416c6cb-13fc-46ee-9376-536142300a7e|CANCELLED / 200|
|160|75e28104-e8e0-435d-a3ff-e4cfc0075d24|CANCELLED / 200|
|161|ac4d0c15-00b6-4c8b-be13-d3a486079762|CANCELLED / 200|
|163|4eb0917e-c8e8-480c-99da-afb7d6cc6870|CANCELLED / 200|
|164|d015a0bc-c12c-4f4a-b423-8e7fdfa9ee3b|CANCELLED / 200|
|165|3f006035-b823-4817-83c1-88565c08bdd3|CANCELLED / 200|
|166|060c025a-baca-43d3-9b8c-38e2ec803e66|CANCELLED / 200|
|167|07fddf59-7a27-4ff9-b23a-18507a3095e9|CANCELLED / 200|
|168|fb626f9b-98c0-4619-85f4-6b6f4d915cb0|CANCELLED / 200|
|169|abec72f0-12c0-4934-8c85-7ba343eec0b4|CANCELLED / 200|
|170|b75e6c19-78c2-4378-b0b8-f66a01898d80|CANCELLED / 200|
|171|f35c14d7-2a65-441a-a6d7-81a9131d1327|CANCELLED / 200|
|173|d0d84637-4c35-408e-b856-65ff01a5c8cf|CANCELLED / 200|
|174|009741c2-489c-4c48-a81e-963626282b99|CANCELLED / 200|
|175|d9b57af2-fc68-4fbc-ad24-f3bcac2199c4|CANCELLED / 200|
|176|84fdd1f5-62e7-49de-8d66-8280747c96f7|CANCELLED / 200|
|177|236212f8-8ec4-43b3-ad66-032a9ace528a|CANCELLED / 200|
|178|b77734dd-c50a-4e57-8a1a-85016671e590|CANCELLED / 200|
|179|89feb3fb-7464-4165-9b5f-eedeb029ce01|CANCELLED / 200|
|180|3e501923-e2e2-49c2-bdc6-2e41f93f0b5f|CANCELLED / 200|
|181|f6c5f736-0d1b-4dc2-a22d-47c32f6f8fbb|CANCELLED / 200|
|182|297ac3f8-1e7b-48d6-a3e3-7230b5d4268b|CANCELLED / 200|
|183|c72e67d3-88c0-4a91-bbf0-1308f3d5338c|CANCELLED / 200|
|184|aa879d2f-295c-4961-8108-4a28cccc16a2|CANCELLED / 200|
|185|d66610fb-d0f5-48fa-9375-f0f1ebc0369e|CANCELLED / 200|
|186|9c77b857-0a41-4f44-9051-81b1bf0525df|CANCELLED / 200|
|187|9a6da0ea-0f04-4a0f-9bd4-347989fede84|CANCELLED / 200|
|188|7ef5b190-7642-455a-bd71-5501bc1a2a93|CANCELLED / 200|
|190|39b5b6d6-8589-47a0-8a82-bbaecaf129ab|CANCELLED / 200|
|191|5755695c-c990-4b28-8ff2-c8bad39b4536|CANCELLED / 200|
|192|68d0f34e-b647-4b3f-859a-fef6b8fddef6|CANCELLED / 200|
|193|9adcb008-5b93-4566-a48c-38c8eb3c571c|CANCELLED / 200|
|194|f91183ae-5959-440e-9bc9-2003c7ceee7d|CANCELLED / 200|
|195|ff026437-fec5-462d-a356-996c07b8863d|CANCELLED / 200|
|196|2fb0232f-8e61-467e-a563-0b2e545c0a2c|CANCELLED / 200|
|197|915fd1e4-6e0e-44c7-8496-1f4241451ce5|CANCELLED / 200|
|198|05ef4073-edd9-46cf-9367-899a2c192c5c|CANCELLED / 200|
|199|cc7dcfd2-376d-4dc3-ad28-bc8e0dc3aff7|CANCELLED / 200|
|200|e245de57-80dc-493c-bb23-b0fd7827dbd4|CANCELLED / 200|
|201|b2b9d168-0298-4da4-93c0-795919b6301d|CANCELLED / 200|
|202|a8f8e5a9-0a2c-4d7e-9c54-f4dd5c008f75|CANCELLED / 200|
|203|951a4dcc-2ad0-48e7-a701-ab436784c064|CANCELLED / 200|
|204|29326582-72ab-4775-82f1-31fb3c22299b|CANCELLED / 200|
|205|592c3e91-ad1e-406f-b934-cc8176a8299b|CANCELLED / 200|
|206|c8388271-625f-4da5-a3a4-b896ae0c88ab|CANCELLED / 200|
|207|f5142a76-d3d5-40b6-9154-0a721dd31158|CANCELLED / 200|
