# Base MES V2.0 — 鼎捷 E10 工单、仓库同步准备实施报告

日期：2026-10-17（Asia/Shanghai）。**受证据约束的部分交付：已完成决策固化、四个只读 SDK 消费操作和候选预览；未实现/开放正式 EXTERNAL 同步。** 本文不把候选读取、Mock 或旧 SQL 同步称为标准 E10 已联调。

## A. 当前代码与 SDK 基线

|仓库|基线与边界|
|---|---|
|mes-base|开始 HEAD f6537778f12ca8f4a118e29b25cb159b54057e1e；期间其他工作先推进到 d7c6f951956aeb25fa42bfbec2b36da755a1f7f5，结束核对为 34937882832cfccb04a6037027e26b85de37daf9；并行提交已包含本轮部分后端文件，本任务未执行 commit/push/reset，保留所有并行配置整合/测试修改|
|mes-base-ui|HEAD 94167e0c25fd9cb4dfcac5ecef369f6e8b715546，保留原有生产仓储/配置改动|
|lixin-common-capability|HEAD ffbe5725adbcbbc4a542ec2f411f24bc84fe83b8，未修改 SDK 源码、README 或 Skill；本次不影响 SDK Skill，无需更新|
|ktg-mes|用户补充指示后只读检查 master/14f486d24c0162758ee10dc05aa69253760be89e，检查前后工作区干净；不读取/调用其真实 ERP 连接|

实际装配 `com.lixin:lixin-common-capability-digiwin-e10:0.1.0-SNAPSHOT`。现有打包制品内 SDK SHA256 为 `2267e3574dfd6ac5c4d1121b52cb397acbef3b021f96b9245bf46083a09ff98e`。已读取 V1.9、V1.7、V1.8、业务边界、ADR-005、SDK README/接入 Skill、P2 修复与阶段收尾报告，并核对当前 SDK 源码和真实消费入口。

## B. AGENTS 与架构修订

- 根 `AGENTS.md` 新增“2026-10-17 — Digiwin E10 集成范围与人工补偿策略”：只验实际操作；无关 8 操作/10 属性不阻断；UNKNOWN 保留原身份、冻结请求、单号及摘要，由授权人员留痕人工核实；不要求远端 MES UUID 唯一检索，不自动重建、改派或补偿。
- `docs/architecture/ADR-005-erp-integration-boundary.md` 追加 V2.0 决策，保留历史时间线；明确人工恢复取代远端 UUID 强制前提，以及 Query 成功和可信写入是不同门槛。
- `docs/architecture/local-erp-business-boundary.md` 修订 UNKNOWN 表述并追加当前验收边界，不放宽来源、迁移或事实保护。
- `docs/development/digiwin-e10-consumer.md` 追加四个消费方法、权限/API、单页预览语义和未接入的正式转换/落库边界。

上述文档修订与本轮消费/预览业务代码相匹配，不宣称迁移流程或人工核实 UI 已实现。

## C. Maven / Spring 装配

复用已有 `DigiwinE10Configuration`、`DigiwinE10MoQueries`、`FactoryOapiService` 和 Registry。runtime=true 时装配消费及预览服务；配置、启动和能力 GET 不请求 ERP。客户端按 factory/reference/configurationRevision 隔离惰性创建并关闭。每次实际请求检查 ERP、DIGIWIN_E10、当前工厂、引用和版本；缺配置/过期版本拒绝，不降级 LOCAL。

没有新增 Gateway、HTTP、认证/签名、第三数据源、定时任务或第二套生产核心。SDK 共用 Header/JSON/错误处理直接复用，异常链保留，不记录请求体或凭证。

## D. MO Query/Read 与稳定身份

实现 `queryMo` 和 `readMo(docNo)`，通过 SDK `.mo().query/read` 调用指定 `e10.oapi.wo.*` 操作。Read 使用类型化 DOC_NO/Read_0001；仅为进程内明确调用接口，不开放任意报文 HTTP 转发。不自动从候选构造定位键。

**ktg-mes 当前源码结论（用户要求补查）：**

|问题|旧项目实际行为|对本轮标准 E10 的意义|
|---|---|---|
|稳定工单身份|`ErpWorkOrderMapper.xml:23` 读取 MO_ID，`ErpWorkOrderServiceImpl.java:126` 经 `checkMoIdUnique` 匹配；`ProWorkorderMapper.xml:278` 按 mo_id 查已有记录|可以确认旧 SQL 同步用 MO_ID，不可直接证明新 Query 的 sys_MainId 等于 MO_ID|
|单号作用域|`ErpWorkOrderMapper.xml:37` 限 Owner_Org_RTK=PLANT、当前 plantCode；`:87` 按 DOC_NO 查询还要求 ApproveStatus=Y|旧代码也没有把单号作为无组织作用域的全局身份|
|产品|`ErpWorkOrderServiceImpl.java:130` 按 ITEM_CODE 查本地 MdItem；查不到置 productId=0|可复用“品号关联”的来源关系；Base 必须拒绝缺映射，不移植魔法 0|
|单位|ERP SQL 经 BUSINESS_UNIT_ID 关联 UNIT，UNIT_CODE → unitOfMeasure|需验证标准 OAPI unit_no 与 Base 产品/单位的一致性，不能猜换算|
|状态|`ErpWorkOrderMapper.xml:19` 直接 MO.STATUS → 本地 status；同步过滤 ApproveStatus=Y|旧实现没有 Base DRAFT/RELEASED 转换；审核状态和生产状态不能混为一谈|
|路线|该工单 SQL/保存链没有确切路线版本映射；旧 ProWorkorder 没有 Base 要求的 routingId|不能从旧同步补出 Base 产品适用的已发布路线版本|
|旧 OAPI|`OApiService.java:56/61` 是 ywms.mo.read/query；Mo DTO 使用大写 DOC_NO/PLAN_QTY 等，MoManager 按单号查询/Read|不是本轮 e10.oapi.* 默认小写响应契约，不能复制字段别名或猜 sys_MainId 身份|

没有把 SQL Server MO_ID、旧 ywms 的 MO_ID/sys_MainId、标准 E10 doc_no 直接等同。本轮未取得目标部署的可信定位/状态证据，故不生成 externalId。

## E. CMS Query/Read 与仓库身份

新增 `queryWarehouse` 和 `readWarehouse(warehouseCode, ownerOrgRtk, plantCode)`，复用 `.warehouse().query/read`。三个 ReadKey 字段准确为 WAREHOUSE_CODE、Owner_Org.RTK、Owner_Org_PLANT_PLANT_CODE；不 trim/重命名或从仓库编码推导组织。

ktg-mes 的 `WarehouseServiceImpl.java:35` 调用 `ywms.warehouse.query`，DTO 只映射 WAREHOUSE_CODE/WAREHOUSE_NAME；`wm/service/impl/WmWarehouseServiceImpl.java:37` 每页 200、按仓库编码查本地记录，新增或更新名称，满页继续。该链没有仓库 ERP 稳定 ID、组织/据点身份、启停状态，也没有仓库 Read。其他单据引用仓库时才带当前 erpPlantCode，例如 `MoReceiptManagerImpl.java:182`。不能把这些写单引用字段当成仓库 Query 已返回的可信组织身份。

因此旧“同码更新”不能直接迁入 Base，亦不能把 approve_status 直接当 ENABLED/DISABLED。本轮保留单页候选、warehouse_no/name/site/原审核状态；ownerOrg 缺失留空。

## F. WorkOrder 可信写入入口与转换

既有 `WorkOrderService.syncExternal(ExternalPlan, Run)` 保留，没有绕过写入。**本轮没有完成 E10 → ExternalPlan 转换和正式工单同步执行器。** 原因是标准响应稳定身份、产品/单位/确切路线版本及状态转换未确认，旧项目 SQL 规则无法补齐这些契约。

LOCAL/NULL 同码拒绝、CANCELLED 不复活、Execution/领料冻结、provider/binding/version、事务与提交前 owner 门继续由已有入口保护；不能用本轮没有写入来替代对真实 E10 写入的验收。

## G. Warehouse 可信写入入口与转换

保留 `WmWarehouseServiceImpl.sync/upsert` 及既有 EXTERNAL/身份/版本门。**未注册 E10 WmWarehouseQueryPort，未构造 ExternalWmWarehouse，未写 MES 仓库、库存镜像或本地库存动作。** 缺仓库复合定位/启停状态证据时保留明确失败，不返回伪造空同步。

仓库页面将无实现的旧写同步按钮替换为受控准备入口；这不代表服务端已能同步。

## H. Factory/source/run token/权限

新 `DigiwinE10SyncPreview`、`DigiwinE10PreviewController` 只提供候选与能力。工单权限 workorder:sync；仓库沿用 business:wm:warehouse:sync。真实拦截器测试验证四个端点未登录 401、仅 CRUD/仅工厂头 403、其他工厂 403，以及对应权限下仅预览。

V39 经正式中心 Flyway 在 **192.168.18.25:3306/mes_base_dev** 执行并 validate；执行前核对 SIMPLE_B → datasource 8 → 192.168.18.25:3306/mes_base_dev_business_simple_b、双侧 ENABLED，备份完整 sys_menu。只新增 1 个 workorder:sync 菜单按钮，不修改 sys_role_menu；工厂模式/绑定/版本逐项不变，SIMPLE_B 仍 LOCAL。备份 `.local/e10-v20-20261017/center-menu-before.json`（600）。

预览流使用 SyncCoordinator 独立键 e10.mo.preview/e10.warehouse.preview，五分钟租约，抢占后才能读；完成前校验 owner，失败记录 FAILED，保留原失败和状态写回异常。不推进同步水位。协调器成功仅表示该页读取完成，响应 status=PREVIEW_ONLY，所有写入计数为零；不同于正式 workorder.erp/codegen.wm_warehouse 同步流。

## I. 分页、重试、幂等与冲突

Query 页码 1..10000，页大小 1..100；页面固定每页 20，一次显式操作只请求一页，不做自动无限翻页。超过请求页大小的响应拒绝。

旧 Condition/Order 类证明旧 ywms 使用 field_name/operator/value 和 field_name/order_type，但旧工单、仓库调用传空 orders；没有标准 E10 下的稳定排序验收。故保留空条件/排序的候选浏览，并明确不承诺全量完整性。满页只能说明可能还有数据；同码候选不按编码去重，不自动匹配 LOCAL，不制造外部 ID。

没有自动重试、断点写入或增量水位。本轮不宣称正式同步的重复执行、冲突计数及部分写入恢复已完成；来源/事实门的已有测试与本轮实际写入证据分开。

## J. 前端交互

新增 `src/api/production/e10Preview.ts`、`src/components/business/E10SyncPreview.vue`，复用现有工单和仓库页面、request/auth/Element Plus。ERP 模式+独立权限+后端 supported 才显示“ERP同步准备”；能力 GET 不请求 ERP，打开后先显示阻断原因，点击只读查询才请求候选。

显示页码、原业务状态、精确十进制数量、获取及零新增/更新计数、错误信息；没有“同步完成”的成功提示。关闭/切厂/撤权/卸载清除候选并拒绝迟到响应。未改 EXTERNAL 普通编辑规则或 LOCAL 新增行为。没有新建独立 CRUD 页面。

## K. 自动化与工程验证

证据目录 `.local/e10-v20-20261017/`（不提交，禁止包含明文凭证）。

- SDK Java8 定向：`E10MoDcxmlContractTest,E10MoGatewayChainTest,E10GatewayRequestTest,E10GatewaySuccessResultTest` 共 38 项通过。HeaderParity 显式 TEST_ONLY 身份、固定回环目的地另跑 2 项通过；第一次因关闭 IT 开关跳过的 1 项不计入通过。SDK 未修改，未回归无关 P2-1 操作。
- 消费/预览定向：四个路由及 Read 键、数量、空页、同码候选保留、未知状态、owner 丢失、并发抢占拒绝、失败不成功、不重试、配置版本和工厂隔离；HTTP 真实拦截器覆盖独立权限。
- `mvn -B -ntp verify` 首轮暴露本轮夹具默认 JSON 浮点解析损失及旧仓储直接调用缺快照；已分别改为十进制夹具并补真实 SDK 解码断言、为旧仓储回归显式进入 TEST ONLY FactorySnapshotFixture。未放宽生产规则或删除断言。后续全量 8 模块通过；最终 verify-latest.log 再次八模块 SUCCESS，admin 阶段共 494 项、0 失败/错误、45 项环境专项显式跳过（实际执行 449 项）。本轮消费 11、预览 8、HTTP 1，共 20 项全部通过。
- MySQL 定向：SourceIsolationMySqlTest 与 E10PreviewPermissionMigrationTest 共 2 项通过、0 跳过。前者只执行会被不可变来源触发器拒绝的更新并 rollback；后者只正式登记权限。迁移首次备份因 Jackson2 未装时间模块失败，发生在 Flyway 之前；改为保留时间字符串的备份后正式执行成功，不绕过备份或迁移记录。
- 前端：Node 22.22.3，61 文件/362 项全通过，vue-tsc 与 build 通过；保留既有大 chunk 提示。
- 本轮 Java 文件均经项目 IntelliJ formatter；本轮差异检查通过。全 UI 工作区 diff-check 另发现并行文件 issue/mesMaterialIssue/index.vue 的已有行尾空白，不为本任务改写该文件。
- 不运行 packaged smoke：该脚本会启动/停止独立服务；本轮未获得此类服务生命周期授权，用户明确要求不启停服务。静态检查打包内 SDK 不等于启动验收。

## L. 真实 MySQL / API / UI / E10 证据分级

|证据|本轮实际结果|不代表|
|---|---|---|
|真实 MySQL|指定中心 V39 迁移/校验、SIMPLE_B 绑定和 LOCAL 不变、来源触发器拒绝检查通过|真实 E10 身份或同步写入|
|真实现有 API|5173 代理登录和 SIMPLE_B context 200；工单列表 200、total=24、本页20条全部 LOCAL；仓库列表在25秒超时，未读取成功|新后端端点已被用户进程加载，或 LOCAL 全生产闭环重新验收|
|真实浏览器|现有 5173 工单页成功读取 LOCAL 来源/数量、ERP 准备入口隐藏；仓库页显示连接失败/超时，没有伪造成功数据|ERP 模式新 UI、三档视口、实际 EXTERNAL 写后读回|
|HTTP Fixture|新四端点的真实 MVC/授权拦截器+Mock 候选服务通过|部署后 API 或真实 ERP|
|SDK 回环|MO/CMS 四路由、DTO/JSON、公共 Header/MD5、异常及数量等通过|实际目标 E10 接受定位字段|
|真实 ERP|**请求 0；未授权目标读取，未沿用 ktg-mes 配置作为本轮授权**|任何真实 Query/Read、写单或正式同步|

首轮真实 API 读取有15秒超时；再次只读检查保留了逐项结果，仓库仍超时，不用测试成功遮盖该未通过项。未修改/重启用户服务，也未因超时启动替代服务或切库。未新增 EXTERNAL 业务数据或测试工厂冒充 ERP。

## M. 未完成接口与 UNKNOWN 人工恢复边界

未完成：标准 E10 稳定身份确认、产品/单位/路线/状态转换、Warehouse QueryPort、正式同步执行器、全量稳定分页/去重、写入统计/冲突清单、EXTERNAL 写后 API/UI 读回、真实 E10 只读联调；现有仓库服务超时也尚未定位。旧工单的 productId=0、BeanUtils 覆盖事实、同码仓库合并及客户专用 SQL 不移植。

下一步需要目标标准 E10 的 Query/Read 脱敏响应/官方部署绑定，以及当前厂可用产品、单位、确切路线映射。ktg-mes 补查已明确旧源规则，但不能补造标准响应缺失字段。缺正式迁移流程时继续保持 SIMPLE_B LOCAL 锁定。

UNKNOWN 用户决策已固化；保留原 requestId/operationKey/冻结请求/已知 ERP 单号/摘要/provider/binding，可能已发送的创建禁止自动重试或转 LOCAL，授权人员留痕核实；明确未发送才可按原状态重发。本轮没有实现人工核实 UI、真实 ERP 命令、自动幂等/补偿引擎，也没有把 UNKNOWN 变成成功。

## N. 判定与变更回执

**CONDITIONAL GO：决策固化、SDK 只读消费和受控单页候选预览，可进入后续部署验证。NO-GO：正式 E10 工单/仓库可信同步及 EXTERNAL 真实写入。** 本任务原始目标未全部实现，限制来自尚未确认的契约/映射、真实调用授权与服务验收问题；不以离线通过替代。

修改范围：上述4份规则/架构/接入文档、本报告；E10 消费/配置/预览服务与 Controller；V39；消费/预览/HTTP/迁移测试和旧 LOCAL 仓储快照夹具；前端 API/组件、原工单/仓库页及4项组件测试。其他现有脏文件为并行工作，不归入本轮完成清单。

回滚：仅撤回本轮源码/前端改动，不 reset 或覆盖其他工作。V39 已执行须保留 Flyway 记录；本次无角色赋权、ERP 绑定或 EXTERNAL 事实需要撤回，不手工删除已执行迁移。实际发布/用户服务重启仍由用户控制。
