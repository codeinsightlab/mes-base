# Base MES V2.1 — E10 工单与仓库同步实施及验收

日期：2026-10-17。状态：离线执行链路与 prod 的 LOCAL 空库读取验收通过，最终结果见文末追加记录。真实 E10 契约仍为 UNVERIFIED，未启用真实 EXTERNAL 提交。

## A. Baseline 与并行工作保护

后端起始 HEAD `34937882832cfccb04a6037027e26b85de37daf9`；两仓初始 patch、相关脏文件哈希保存在 `.local/e10-v21-20261017/baseline.json`、`mes-base-before.patch`、`mes-base-ui-before.patch`。配置清单及配置收敛报告属于并行工作，未覆盖；前端原有 V1.6/V1.7/V1.8 页面改动保留，仅在原页面接入同步完成刷新。检查 baseline 中非本轮前端文件哈希，无其他差异。旧 ktg-mes 只读；SDK 未改源码；无 reset、commit、push。

本轮新增复杂同步编排和既有 Service 检查，不新增业务表、Mapper、通用同步平台或基础 CRUD，不涉及 Codegen。业务生成文件未手工修改；未修改 ProductionQuantityService、QualityRelease、LocalStockService 和 UUID/UNKNOWN 恢复核心。

## B. LOCAL 仓库超时及 prod 404

原真实链路：5173 → 8081 `GET /api/wm/wm-warehouse?offset=0&limit=20` → WmWarehouseController.list → WmWarehouseServiceImpl.selectPage → Mapper → 当前 Factory 的 wm_warehouse。没有 E10 调用。

旧进程 JFR 显示 Spring 记录参数绑定阶段缺失 BindParam，异常处理同时缺少 ModelAndViewDefiningException、HttpFailureLogging、ThrowableProxy；失败发生在 Service/SQL 之前。还观察到运行 JAR 在进程启动后被构建替换。证据保存在 `.local/e10-v20-followup-20261017/warehouse-errors.jfr`、`warehouse-errors.json`。用户重编译重启后，仓库列表 200/7条/92ms，详情200/40ms，库存200/30条/41ms，工单200/24条/80ms，见 `restart-recheck.json`。不将该运行时故障归为 E10、分页 SQL 或数据库锁。

`run-local-backend` 改为每次启动复制只读0400 JAR到私有运行目录，避免 Maven 替换正在运行的压缩包；新增 MES_LOCAL_PROFILE/MES_LOCAL_JAR 启动参数，默认行为仍 dev。Maven 支持 `mes.artifact.directory`，本轮打包到私有验收目录，原 target JAR 哈希未变。假 Java 启动夹具确认构建后运行副本内容不变，没有为该测试启动真实服务。

后续用户明确授权切换 prod 并重启。真实 prod 配置缺 `mes.business.enabled`，首次启动后登录成功但仓库404，日志明确 No mapping，Controller 的条件装配未满足。已在私有 prod 配置和可提交 prod 示例补 `mes.business.enabled=true`，Profile 测试增加工作环境业务装配断言；未改 Factory 模式、绑定和连接身份。

## C. MO/CMS 消费与转换

继续复用 DigiwinE10MoQueries、OapiMoGateway/OapiCmsGateway、FactoryOapiService 和 SDK 的 Query/Read，无新 Gateway。保留 SDK 原始 source 做字段级验证，不用 BeanUtils，不按大小写或名称猜兼容字段。

DigiwinE10SyncMapping 把 MO 转为 WorkOrderService.ExternalPlan，把 CMS 转为 ExternalWmWarehouse。MO 使用 doc_no、item_no、unit_no、plan_qty、plan_start_date、plan_complete_date；仓库使用 warehouse_no、warehouse_name。HTTP 数量和业务ID保持字符串，不将 BigDecimal 转为 JS number。

Read 是受管契约的显式选项。MO 用已确认单号定位，CMS 必须提供真实 warehouseCode/ownerOrgRtk/plantCode 三个独立值；缺失只返回 READ_KEY_INCOMPLETE，不发送伪造 Read。Read 只接受唯一结果并与 Query 编码、已有稳定身份一致，完整使用 Read 记录，不把两次返回混拼成看似完整的记录。

## D. 稳定身份、组织与版本

E10ExternalIdentity 只从已确认的 JSON Pointer 读取字段：单字段保留值，多字段使用无歧义 JSON 数组元组，总长≤128。不建立哈希身份，不等同 MO_ID 与 sys_MainId。缺契约/字段返回 IDENTITY_UNVERIFIED。逐条检查目标据点与配置中必要组织维度。

可选 E10SyncContract 存在现有加密工厂 OAPI 配置内，沿用配置版本、平台编辑权限和 ERP 绑定后的只读锁。没有新增模式入口或 YAML Factory 覆盖。已有配置缺该字段仍可解码，正式提交关闭。conditionsJson/ordersJson 使用有界 JSON 数组字符串跨应用 Jackson3/SDK Jackson2 边界，避免 JsonNode 抽象类型无法绑定；两套 Jackson 的序列化/反序列化合同测试通过。

每次远端批处理及本地写入前都复核当前 factory/ref/configurationRevision。客户端只提交分页上限，不能传 provider、binding、身份契约或旧 Dry Run 计划绕过范围。

## E. 产品、单位、路线、数量及状态

- 经确认的 ERP 品号与 MES CODE 或 EXTERNAL_CODE 精确关系；唯一、启用才允许。缺产品、多匹配、禁用均阻断，无 productId=0。
- ERP unit_no 必须与启用的产品基础单位 code 完全相同；没有换算证据就拒绝，不换算、截断或舍入数量。
- ERP 提供生产计划；MES 选择该产品适用的唯一 PUBLISHED Routing。多个或缺失均 MAPPING_REQUIRED，不要求 ERP 提供 MES 路线版本，不选第一条。
- 数量为正，满足 DECIMAL(20,6)；日期仅接受 ISO 本地日期/日期时间，结束不能早于开始，不猜时区。
- 工单审核与业务状态分别按已核实原值转换；只允许 DRAFT/RELEASED。仓库只允许 ENABLED/DISABLED。未知原状态 STATUS_UNVERIFIED，不从 execution.code=0 推定审核、启用或生效。

## F. Dry Run 与可信 Service 一致性

新增 WorkOrderService.inspectExternal、IWmWarehouseService.inspectExternal；与实际写入共用来源、外部身份、配置版本、同码、状态和事实规则。Dry Run 的引用检查只 SELECT，不用锁引用的同值 UPDATE，不写 WorkOrder/Warehouse/库存。运行协调表的 claim/progress/finish 是独立基础设施记录。

|场景|结果/行为|
|---|---|
|合法新对象|CREATE，Dry Run实际新增为0|
|合法EXTERNAL计划变更|UPDATE，提交时再次检查|
|相同身份和完整计划重放|UNCHANGED，不更新revision和事实|
|LOCAL/NULL同码、异身份接管、旧配置版本|CONFLICT|
|CANCELLED|CONFLICT，不恢复|
|已有Execution/旧批次/阻断领料后修改计划|CONFLICT，保留事实|
|缺产品、单位不一致、非唯一发布路线、数量/日期不合法|MAPPING_REQUIRED|
|缺稳定身份证据|IDENTITY_UNVERIFIED|
|未审核/未知状态|STATUS_UNVERIFIED|
|Read键缺失、Query/Read不一致或分页超限|UNSUPPORTED，原因保留|

测试发现并修复只读检查的事务传播问题：被映射为逐条冲突的 FoundationException 不应令只读外层事务抛 UnexpectedRollbackException。只读 inspect 明确 noRollbackFor；实际同步异常仍由写事务回滚。

## G. 正式执行器与提交门禁

DigiwinE10SyncService 执行受控 Query/Read → 完整有界扫描 → 转换检查 → DigiwinE10SyncWriter 单候选事务 → 原 syncExternal/upsert。只有当前 ERP/DIGIWIN_E10、绑定及版本相符、对应证据契约存在且 commitEnabled 显式启用时开放提交；逐条映射缺失仍拒绝。

正式键复用 workorder.erp / codegen.wm_warehouse；Dry Run 独立键 e10.mo.dry-run / e10.warehouse.dry-run；原单页预览键保留。HTTP/其他正式入口共享原协调器，服务在处理和 beforeCommit 检查 run token，失败不推进成功水位。没有 JVM 锁，也没有跨系统自动补偿。

## H. Factory/source/权限/run token

所有4个新增POST端点经现有 FoundationAccessInterceptor，工单 workorder:sync，仓库 business:wm:warehouse:sync。真实拦截器测试覆盖401、未授权403、其他Factory403、正确权限到达用例。原4个能力/预览端点测试保留。业务服务继续使用 FactoryIntegrationContext 和 BusinessSourceRules，业务表不增加 factory_id。

## I. 分页、幂等、部分成功与失败

最多5页、每页20条、100条候选；90秒远端处理预算，单个在途请求仍受SDK超时限制。排序和条件由目标环境证据契约提供。没有契约只读取一页用于阻断分析；最后满页超过范围上限时不写任何候选，不把截断扫描声明为全量成功。

跨页相同身份且内容完全相同去重；相同身份不同内容或同码不同身份在任何写入前全部标冲突。重复运行通过原外部映射与正式同步键保持幂等。一次记录一个事务，前面已提交不因后续冲突回滚；返回 COMMITTED/PARTIAL_SUCCESS/FAILED 和实际 created/updated。基础设施中断保留已提交数量、failureReason、runToken和完整后台异常链，停止后续记录；无自动重试。协调表保存处理数量，细分统计在结果和关联日志中，不新增同步审计表。

## J. 前端

复用 E10SyncPreview.vue：原候选页、Dry Run、受服务端能力和权限控制的正式同步、逐条action/reasons和实际数量；提交要求先有可执行Dry Run，但服务端仍重新读取。完成/部分成功/请求中断后刷新主列表。切厂、撤权、关闭、卸载清理并拒绝迟到响应。LOCAL 不展示ERP同步入口。该用例的请求超时上限120秒，其余请求默认15秒不变。

## K. SDK 与定向自动化

SDK公共协议/HTTP/错误/业务结果/MO链及原始响应定向118项通过，0跳过；无真实ERP连接，不扩大修复已延期P2-1。

隔离 A/B H2 工厂，实际 Mapper、业务Service、Spring事务：E10SyncExecutionTest 10项通过。覆盖工单/仓库新增、更新、重复、无变化、LOCAL/NULL同码、CANCELLED、Execution冻结、数量/单位/日期/路线/审核/身份、Read正常与错配及缺定位键、分页空页/重复/超限、版本变化、部分成功、失权后停止、beforeCommit失权使业务及Extra真实回滚、B库不受A影响。远端是明确 TEST ONLY Fixture，run token 在此夹具中为 Mock；协调器本身的竞争、旧令牌和恢复测试由全量框架/数据库Fixture套件覆盖，不将该夹具称为真实ERP或真实多节点部署。

## L. 全量、MySQL、API/UI 证据

- 后端全量 verify：8模块通过；admin507项、0失败/错误、45项环境专项跳过。Profile业务开关修复后再次全量结果待文末追加。
- 前端61文件365项通过，vue-tsc/build通过，保留既有大chunk提示。范围diff-check通过；全UI工作区另有任务外领料页原有行尾空白，未顺手修改。
- 初轮指定dev MySQL验收遇握手EOF；用户随后说明MySQL正在重建并要求暂停，未换库冒充通过。
- 用户后来明确授权切prod并重启：已复核110.42.227.83:3306，中心mes_base_prod/V39，PROD→datasource1→mes_base_prod_business/V37，LOCAL、ERP provider/ref为空。两条来源不可变触发器存在，工单/仓库/库存动作均0条；只读核对，没有写合成EXTERNAL。
- prod登录前健康检查和5173代理均200，未认证仓库和E10能力401。首次完整prod启动后发现业务接口404，已定位为配置装配开关缺失并修复。
- 验收登录获用户当次验证码授权；通过原账号正常登录，未关闭验证码、创建账号、修改权限/密码或伪造会话。后续业务结果见文末。
- 运行证据在 `.local/e10-v21-20261017/`。SDK和ktg源码均未修改；原target JAR未覆盖，新进程使用私有副本。

## M. 真实 E10 证据边界

未取得目标账套下 MO_ID/sys_MainId/组织唯一性、仓库复合键值、状态语义与品号/单位关系的新证据；未安装任何真实同步契约。没有真实 E10 Query/Read，更无写操作。PROD及SIMPLE_B均未切ERP，未修改绑定。ktg-mes的SQL字段和标准SDK字段只作为源码事实，不能作为部署身份确认。

## N. 未实现范围

不开发ERP领料、退料、出入库、工单作废远端命令、自动补偿、模式迁移、单位换算平台、远端MES UUID唯一查询。没有授权真实ERP，因而不提供真实ERP全链路GO。

## O. 判定与变更回执

完整转换/Dry Run/正式执行器与隔离Service落库已实现并通过，真实E10提交为 CONDITIONAL GO：须先在明确授权的目标环境确认身份、状态和映射并保存受控契约，再验收对应ERP工厂。无契约时失败关闭。当前prod的LOCAL运行验收与该条件独立。

本轮代码集中于E10契约/转换/执行/Controller、工单和仓库共享检查、启动副本与可选打包输出目录、prod业务装配开关；前端原组件/API/列表刷新、受限请求超时及相关测试。ADR-005、业务边界和E10接入说明追加V2.1规则及配置合同。本报告记录实现、故障与证据。回退应按本轮明确差异审查，不恢复旧YAML模式入口或删除业务事实；没有执行Git提交或数据库回滚。


## 2026-10-17 最终追加：prod 已启动并完成只读运行验收

用户明确授权切换prod、重启和本次验证码登录。实际启动 `prod` Profile，端口8081沿用现有5173代理；Redis仍用prod配置的数据库2。第一次本任务进程38598已正常终止，当前40599；未处理无关进程。修复后的私有JAR SHA256为 `f115e7246d797394c1e545c2a6b0974572d30f1d30abc7ea14b09df1ee237c93`，运行副本只读，不会被后续Maven覆盖。标准prod配置端口原为8080，本次明确CLI覆盖为8081以保持既有前端代理连接；未改中心/业务数据库URL。

`verify-prod-business.log`：再次8模块SUCCESS，admin507项、0失败/错误、45项专项跳过；ConfigurationProfilesTest 2项、E10SyncContractTest 2项、执行器10项和HTTP权限2项均通过。代码格式化只改本轮Java文件；脚本语法与后端diff-check通过。

|真实用户服务检查|结果|
|---|---|
|8081健康、5173代理健康|200 UP|
|prod管理员正常验证码登录|200，user_id=100，未关闭验证码或重设密码|
|Factory上下文|PROD，LOCAL，绑定datasource1，ERP provider/ref仍空|
|仓库列表|200；首个业务池冷启动2896ms，后续149–219ms；0条，无404/超时|
|库存列表|200，191ms；0条|
|库存页仓库选择器|真实仓库API200，149ms；空候选，无错误|
|工单列表|200，197ms；0条|
|LOCAL页面ERP同步入口|未显示，未触发ERP读取|
|数据库复核|工单0、仓库0、库存动作0，未提交业务测试数据|

证据：`prod-business-launch.json`、`prod-business-runtime.log`、`prod-acceptance.json`、`prod-business-after-readonly.tsv`、`prod-warehouse.png`。接口状态和时延来自真实HTTP访问日志，页面0条与实际MySQL一致；没有将工作台自带示例卡片当作生产数据。

当前prod为空库，因此本轮真实服务证据只证明装配、认证/权限、Factory路由、选择器及读取链，不证明带数据生产闭环或真实E10写入。EXTERNAL写入、更新、回滚和重复同步由明确隔离A/B数据库的实际Service测试证明；真实E10身份、状态及正式业务提交仍需前述条件，不能将本轮切prod解释为ERP模式启用。此前dev来源触发器MySQL测试的握手失败没有改记为通过，prod本轮只读检查单独记载。

LOCAL空库读取验收GO；完整E10同步实现为CONDITIONAL GO，条件限于真实部署契约与ERP工厂验收，未以该缺口跳过本地正式执行器实现。
