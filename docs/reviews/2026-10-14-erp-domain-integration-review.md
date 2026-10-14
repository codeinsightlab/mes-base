# Report A：ERP Integration Implementation

2026-10-14。本轮 Phase A 开发报告；Phase B 另见生产主链审查报告。只修改 Base/其 UI，参考旧项目只读；未由本 Agent 提交、推送、部署或重启用户服务。入场已有领料作废/主链/UI修改，本轮逐项适配并保留，不能将所有 git diff 归为本轮工作。

## 架构落地结果

复用 `WarehouseErpAdapter` 作为仓储领域接口，名称沿用但职责明确为ERP领域业务契约。`WarehouseIntegration` 继续负责当前厂配置和唯一实现选择；`LocalWarehouseErpService` 是默认领域实现，真实厂商领域实现及 Gateway 当前不存在。MES协调Service调用此边界，供应商HTTP字段/错误码不进入MES。没有另建ERP配置、第三数据库、插件或工作流引擎。

- 领域接口：local创建、作废、稳定作废操作查询、领料业务状态查询。创建命令当前只覆盖本地后台建单；真实出库完整明细载荷、提交入口和厂商创建/提交/审核流程未实现。主子单头与明细仍分别保存，不把创建空单头当作真实出库。
- 默认实现：创建/作废为SUCCEEDED + LOCAL_ONLY、无外部ID；查询为NOT_FOUND + UNKNOWN业务状态 + LOCAL_ONLY，明确没有ERP单据/审核事实。只有本地实现会在原本地建单事务内被调用，它没有网络副作用。
- Provider选择：复用 `mes.integration.warehouse.factories.<FactoryId>.mode`；查询CONNECTED还要求同前缀`.provider`与Extra原provider相同、原binding唯一匹配领域实现。缺配置/错供应商/无实现失败，CONNECTED不回退本地。作废复用既有原binding匹配能力，即使当前模式切本地也不会本地跳过原CONNECTED事实；它只执行唯一匹配的原外部领域实现，不制造本地冲销。
- 标准响应：既有 `SUCCEEDED/REJECTED/UNKNOWN` 对应SUCCESS/FAILED/UNKNOWN；结果来源 `LOCAL_ONLY/CONNECTED` 独立。保留原外部主键，拒绝来源或身份不匹配结果；诊断使用安全标准摘要，不直接展示供应商消息。没有HTTP200自动成功逻辑或真实厂商HTTP解析器。

## ERP Extra Schema与生成

领域专用表比分散泛型对象类型更简单：主键等于对应MES对象ID，领域对象类型由表名固定；可靠关联通过同一短事务及业务Service维护，不新增外键、非主键唯一约束或Factory列。

- V20：`mes_material_issue_erp_extra` 保存创建来源、外部影响、binding、外部ID/编号、外部业务状态、作废模式/结果/安全诊断、最近操作/确定性结果/核实时间、`operation_version`。领料主表保留MES状态和本地作废operationId/原状态/时间；迁出7个外部事实列。
- V21：`workorder_erp_extra` 保留旧外部生产单据ID/编号及可空provider。历史/手工录入标识不意味着真实ERP对应关系，provider未知保留NULL；工单核心表迁出两列。没有预建工单ERP业务流程。
- V22：旧opaque binding不能证明Provider，纠正V20迁移行曾以binding填充provider的推定；仅限无后续业务核实记录的历史CONNECTED行，外部ID保持。本库实际CONNECTED=0，此次未改变现有业务事实。未核实来源保持NULL，V1明确拒绝查询，不猜供应商。
- 原主表Entity的少量兼容访问器仅作为Service从Extra批量填充的读模型投影；新生成主表Mapper/XML/Example不再映射、读取或写入外部列。现有VO/API兼容，外部事实物理权威为Extra。
- Codegen Skill从已核对的真实SIMPLE_B metadata生成完整 persistence/backend/test 审查副本。采用生成Mapper/XML/Example/Entity及受限基础Service；主表仅采用必要持久化适配，保留已有业务Service。Extra普通写方法明确拒绝，不装配生成Extra Controller，不开放人工标记ERP成功。原始完整生成后端/测试保存在审查输出，不把生成骨架测试当作验收。
- 显式复现配置：`docs/development/material-issue-erp-codegen.yaml`、`workorder-erp-codegen.yaml`，输出到自有target审查目录，不覆盖手工业务代码。报告 `target/erp-implementation/*codegen-report.json`：4张表的两次生成，零拒绝。
- V20/V21/V22均按正式Flyway执行、中文表/字段COMMENT齐全；不改已执行V18/V19。备份范围及原值逐列比对：迁移前10张领料、迁移前29张工单完整一致；当前主表无外部事实列、两个Extra关联无孤立主单。

实际目标：`SIMPLE_B → datasource 8 → 192.168.18.25:3306/mes_base_dev_business_simple_b`，工厂/数据源ENABLE。证据：`target/erp-implementation/{migration.log,migration21.log,data-preservation.json,issues-before.tsv,workorders-before.tsv}`。合成测试标签保留于指定库；未清空/覆盖用户资料。

## 领料ERP查询与核实

`MaterialIssueErpService.queryErpStatus`：可信FactoryScope定位本库MES对象和Extra，短只读事务取快照、事务外领域查询，返回标准结果/MES状态/最近核实摘要。只读查询不落日志或修改业务状态，不接收外部身份或factory参数。

`reconcileErpStatus`：查询在事务外；短READ_COMMITTED写事务重锁领料、重取Extra并检查operation_version、MES状态和voidOperationId；落地领域规则在 `MaterialIssueErpRules`。MES与Extra同一business事务，冲突409，不用旧结果覆盖新动作。单头/明细增改删及作废参与Extra版本；旧作废查询也校验版本。

| 结果与前置状态 | 本轮落地 |
| --- | --- |
| LOCAL_ONLY | 记录本地核实结论，不伪造ERP事实、不自动改MES状态 |
| UNKNOWN/NOT_FOUND | 保持MES/已知外部身份，不授权重建、不等同创建失败 |
| FOUND + PENDING_APPROVAL/DRAFT/REJECTED/UNKNOWN | 保存已查询到的ERP状态，保持MES，留人工处理 |
| FOUND + EFFECTIVE，MES为ERP_PENDING/ERP_UNKNOWN | 领域规则推进RECORDED/CONFIRMED；此分支以测试替身验证，目前没有真实CONNECTED提交入口生成这些状态 |
| FOUND + CANCELLED，MES为VOID_PENDING/VOID_UNKNOWN且有稳定作废身份 | 推进VOIDED/SUCCEEDED/CONNECTED/REVERSED |
| VOIDED + EFFECTIVE，或非作废流程查到CANCELLED | MES保持，Extra保存实际ERP状态及冲突摘要，external_effect置UNKNOWN，继续阻止工单产品订正 |

接口：GET `/api/issue/mes-material-issue/{id}/erp-status`；POST `/{id}/actions/reconcile-erp`。两者分别使用 `business:mes:material:issue:erp:query` 与 `:erp:reconcile`，核实不借普通query权限。原作废及reconcile-void入口仍在，对应void权限。

Factory隔离由当前请求固定的业务库/事务租约维持，跨厂相同ID合法；带ID入口先在本库定位单据，从服务端Extra构造身份。历史来源不明/原provider不匹配拒绝，不自动认定本地成功。

## 前端与人工恢复范围

复用领料 `MasterDetailDocument` 动作槽，增加 `MaterialIssueErpAction` 两按钮及API：按独立权限显示，只对已保存且无未保存修改的当前单据操作；展示来源、外部单号、查询确定性、外部业务状态、最近核实摘要；核实后刷新。切厂/撤权/换单/卸载清理，拒绝迟到结果；没有ERP状态输入框或人工成功按钮。

本轮只读查询+核实同步及既有作废核实。没有真实供应商，继续审核、真实冲销、CONNECTED创建及ERP业务恢复联调未实现；不提供假恢复/自动重试/Saga。Default创建不等于库存出库或路径锁定。

## AGENTS.md修改

将旧“所有Adapter不编排业务”限定为传输Gateway/Adapter，明确ERP领域实现可编排厂商内部步骤；旧ERP条目收敛为 `ERP Integration Architecture`：领域职责、标准三结果/来源、Extra、HTTP业务判断、短事务、操作版本、独立权限、历史来源、禁止重复未知创建、人工恢复与V1边界。重读检查后，与中心+业务库、Factory无表内列、主子分别保存和代码生成要求一致。ADR-007及旧实施报告追加指向此时点，不覆盖历史验证。

## 测试与运行边界

最终结果见下方完成记录。关键测试：

- `LocalWarehouseErpTest`：默认创建/作废/查询不伪造外部事实，缺配置/厂商缺失/历史来源不匹配拒绝。
- `MaterialIssueErpHttpTest`：两独立H2工厂HTTP Fixture；FOUND/NOT_FOUND/UNKNOWN/超时，安全摘要、只读查询、权限/Scope拒绝、重复核实、待审核不推进、作废终态不复活、迟到EFFECTIVE不能覆盖新作废；Extra创建/删除生命周期。
- 原 `MaterialIssueVoidHttpTest`：原P0产品保护、稳定作废身份、三结果与重复点击、跨厂/独立权限/并发单一认领继续通过。
- `MaterialIssueVoidMySqlTest`：指定SIMPLE_B真实事务，LOCAL_ONLY查询无版本写入、核实记录及MES状态、作废、保留原产品、产品订正/并发一致性；测试替身不能代表真实厂商。
- WorkOrder/Execution/MaterialIssue真库测试，RequestBudget追加真实批量Extra查询，保留当前请求准入预算及无N+1验证。
- UI `materialIssueErp.test.ts`及原作废测试：独立权限/ID精度/只读/刷新/清理/迟到响应/脏表单；完整test/build。

实际用户服务经现有5173代理登录后，本轮GET新查询及原领料list均500/INTERNAL_ERROR，因此当前真实HTTP/UI仍未通过。浏览器到现有5173仅确认登录页可达，未在登录页面伪造业务验收。需用户重载匹配V22的新构建，并审查执行两权限菜单SQL/配置授权，再复验。没有以旧报告的历史UI/API成功替代本轮结果。迁移会让旧包含已删除外部列的Mapper不兼容，旧构建回滚须先恢复匹配Schema；不能只换回旧JAR。

初次完整检查曾有4个既有Mapper测试类加载/扫描失败；未改生产扫描规则，后续完整运行通过。初次本轮fixture/构造签名适配失败均已修正；以最终运行记录为准，不把中间失败抹去。

## 文件与回滚

任务新增/适配文件清单见完成记录。回滚应协调整个Schema/代码版本：业务备份已保留，采用新的正式反向迁移恢复字段并从Extra回填，核对期间新数据，不能改写Flyway历史或只部署旧版本。菜单仅生成，未执行/自动授予角色；没有真实ERP外部副作用。本轮Phase B停止于审查，不继续生产路径/报工开发。


## 最终完成记录（2026-10-14）

- `JAVA_HOME=已安装Java21 MES_VOID_MYSQL_ACCEPTANCE=true MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true`：413项、0失败、0错误、10项跳过，BUILD SUCCESS（14:07:45）。包含7个新增ERP HTTP Fixture用例、2个本地领域单测和指定MySQL查询/核实/作废/工单一致性用例。
- Node22.23.1 `npm test`：38文件/199项通过；`npm run build`：vue-tsc与Vite通过，仅已有大chunk警告。
- `/usr/bin/python3 scripts/smoke-packaged.py`：最终打包烟测10通过/0失败，结果见 `target/erp-implementation/smoke-final.log`，自有随机端口、无用户服务操作。
- V20/V21/V22真实Flyway migrate+validate；逐字段备份比对通过。最后只读核对：两个Extra无孤立主单、核心表无迁出的外部事实列、WorkOrder重复code组0、CONNECTED领料0。
- 用户服务真实HTTP：2个受保护查询均500；真实浏览器仅登录页可达。等待用户加载匹配V22构建、配置独立权限后复验；没有真实ERP联调。

任务新增/适配文件（与入场副本比较；不把ErrorCode、原Controller等先前dirty文件列为本轮新修改）：

- `../mes-base-ui/src/api/issue/mesMaterialIssue.ts`
- `../mes-base-ui/src/components/business/MaterialIssueErpAction.vue`
- `../mes-base-ui/src/views/issue/mesMaterialIssue/index.vue`
- `../mes-base-ui/tests/materialIssueErp.test.ts`
- `AGENTS.md`
- `mes-admin/src/main/resources/db/business/V20__material_issue_erp_extra.sql`
- `mes-admin/src/main/resources/db/business/V21__workorder_external_extra.sql`
- `mes-admin/src/main/resources/db/business/V22__unverified_legacy_erp_provider.sql`
- `mes-admin/src/test/java/dev/issuefixture/MaterialIssueErpHttpTest.java`
- `mes-admin/src/test/java/dev/issuefixture/MaterialIssueHttpTest.java`
- `mes-admin/src/test/java/dev/issuefixture/MaterialIssueMySqlTest.java`
- `mes-admin/src/test/java/dev/issuefixture/MaterialIssueVoidHttpTest.java`
- `mes-admin/src/test/java/dev/issuefixture/MaterialIssueVoidMySqlTest.java`
- `mes-admin/src/test/java/dev/issuefixture/ProductionExecutionMySqlTest.java`
- `mes-admin/src/test/java/dev/mesbase/server/WorkOrderMySqlTest.java`
- `mes-admin/src/test/java/dev/mesbase/server/WorkOrderRequestBudgetTest.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/ErpActionResult.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/ErpIssueQueryResult.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/LocalWarehouseErpService.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/MaterialIssueCreateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/QueryMaterialIssueCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/WarehouseErpAdapter.java`
- `mes-business/src/main/java/dev/mesbase/business/integration/warehouse/WarehouseIntegration.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/MaterialIssueErpController.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/dao/MesMaterialIssueErpExtraMapper.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraCreateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraExample.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraPage.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraQueryDto.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraResource.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueErpExtraUpdateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueExample.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/IMesMaterialIssueErpExtraService.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueErpFacts.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueErpRules.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueErpService.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueRules.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueVoidService.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueErpExtraServiceImpl.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueItemServiceImpl.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueServiceImpl.java`
- `mes-business/src/main/java/dev/mesbase/business/production/dao/WorkOrderExtraMapper.java`
- `mes-business/src/main/java/dev/mesbase/business/production/dao/WorkorderErpExtraMapper.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraCreateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraExample.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraPage.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraQueryDto.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraResource.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderErpExtraUpdateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/production/domain/WorkorderExample.java`
- `mes-business/src/main/java/dev/mesbase/business/production/service/IWorkorderErpExtraService.java`
- `mes-business/src/main/java/dev/mesbase/business/production/service/WorkOrderService.java`
- `mes-business/src/main/java/dev/mesbase/business/production/service/WorkorderErpFacts.java`
- `mes-business/src/main/java/dev/mesbase/business/production/service/impl/WorkorderErpExtraServiceImpl.java`
- `mes-business/src/main/resources/dev/mesbase/business/issue/dao/MesMaterialIssueErpExtraMapper.xml`
- `mes-business/src/main/resources/dev/mesbase/business/issue/dao/MesMaterialIssueMapper.xml`
- `mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkOrderExtraMapper.xml`
- `mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkorderErpExtraMapper.xml`
- `mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkorderMapper.xml`
- `mes-business/src/test/java/dev/mesbase/business/issue/LocalWarehouseErpTest.java`
- `mes-business/src/test/java/dev/mesbase/business/production/WorkOrderExecutionProtectionTest.java`


新增知识文档：本报告含架构/Schema/查询核实/权限/测试/风险/文件与回滚；Report B含职责/调用路径/矩阵/分级/决策/停止点。ADR-007及旧领料作废报告追加2026-10-14后续修订链接，保留时间线。规则AGENTS更新；业务代码仅在Phase A修改，Phase B生产源码哈希复核无变化。没有更新个人记忆。


## 用户重启后的真实复验补充（2026-10-14）

用户已明确完成重启，本Agent未操作服务生命周期。此时点取代前文“当前服务500”的等待状态，保留其历史记录。

- 重新登录现有5173前端代理；核对SIMPLE_B → datasource8 → 192.168.18.25:3306/mes_base_dev_business_simple_b，工厂/数据源启用，Flyway业务版本22。
- 执行 `python3 target/erp-implementation/restarted-acceptance.py`：10次真实HTTP请求结果符合本轮可验证路径。工厂context、navigation、领料list/detail均200；ERP查询和核实POST因缺少新独立权限均403/FORBIDDEN；未认证的两个新入口均401。未携带Factory头的查询也被权限门拒绝403，此用例不宣称验证了成功授权后的空Scope行为。
- 当前账号没有 `business:mes:material:issue:erp:query` 和 `business:mes:material:issue:erp:reconcile`；未自动执行菜单SQL/授予角色权限。成功LOCAL_ONLY查询、核实落地及其真实页面按钮操作尚未完成，等待用户配置或明确授权权限变更。
- 浏览器真实登录、进入领料列表、打开已有TEST ONLY单据22成功。显示“已作废 · LOCAL_ONLY”，单头字段禁用、明细只读；两个ERP新按钮在缺权限时数量均为0，符合按权限隐藏。当前只验证一个浏览器视口与现有只读单据，未扩大为完整响应式/写操作验收。
- 读取与被拒绝的核实请求前后单据详情完全一致。本轮没有写入/作废新的业务数据、没有修改业务代码、没有改角色权限、没有真实厂商调用。

证据：`target/erp-implementation/restarted-acceptance.json` 与 `restarted-ui-permission.png`。当前状态为用户服务已恢复、本轮新功能的成功路径被独立权限配置阻塞；后续不需要再次重启来解决本次403，应先完成权限配置并重新加载会话权限。


## 用户授权配置后的真实成功闭环（2026-10-14）

用户明确纠正选择并授权“你自己来授权”，本次仅按该授权执行权限配置与复验，取代前文等待配置的当前状态。

- 核对中心库现有账号admin/user100、角色100「平台管理员」PLATFORM；该角色仅关联admin。配置前备份领料菜单及该角色全部菜单关联。
- 在中心库事务中执行已审查的 `docs/generated/material-issue-erp-menu.sql`，创建按钮2586（erp:query）、2587（erp:reconcile），父菜单2564；仅增量关联角色100。逐集合比对原角色菜单全部保留，新增集合恰为这两个ID，未改其他角色。平台角色的业务权限按既有规则作用于当前选定启用Factory，不新建工厂专属权限机制。
- 重新读取当前用户/工厂权限：两项权限已生效。`python3 target/erp-implementation/authorized-acceptance.py` 的14次真实HTTP验收符合预期：LOCAL_ONLY查询200、核实200、重复核实200；GET前后Extra逐行相同；核实后MES原VOIDED状态、已知来源及外部ID/编号保持为空；Extra保存RECONCILE/NOT_FOUND及核实时间。
- 历史来源未知单据5的查询与核实均409/ISSUE_EXTERNAL_UNVERIFIED，Extra不变；两个未登录入口401；无效工厂422/FACTORY_SELECTION_REQUIRED；不存在单据404。未访问其他真实业务库来替代SIMPLE_B验收。
- 浏览器在现有5173恢复登录与SIMPLE_B，打开TEST ONLY领料22：两个ERP按钮已显示且可操作；真实点击查询及核实完成，显示 `LOCAL_ONLY · NOT_FOUND · UNKNOWN` 与“本地模式，没有ERP单据或审核事实”，最近核实NOT_FOUND。原已作废单头/明细仍只读，没有被恢复或生成外部身份。截图 `target/erp-implementation/authorized-ui-reconcile.png`。
- 当前真实HTTP/UI验收通过，仅限上述本地模式及拒绝边界；CONNECTED的FOUND/UNKNOWN及并发仍为此前测试替身/Fixture证据，没有真实厂商联调。没有修改业务代码、没有新建业务数据或重启服务，仅更新已有TEST ONLY单据的核实元数据及用户已授权的中心权限配置。

权限配置证据 `target/erp-implementation/permission-grant/{before.tsv,apply.sql,after.tsv,result.json}`；HTTP证据 `authorized-acceptance.json`。回滚权限时仅撤销角色100到2586/2587的本轮关联，确认没有后续其他角色引用再处理新菜单；不得覆盖原角色完整授权集合。
