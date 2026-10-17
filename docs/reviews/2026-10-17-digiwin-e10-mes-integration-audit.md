# Base MES V1.9 — 鼎捷 E10 集成与生产仓储业务链专项审查

日期：2026-10-17（Asia/Shanghai）。性质：只读代码、契约、现有制品和证据审查。本文是本轮唯一新增文件；没有修改业务/Gateway/配置/数据库，没有调用 ERP、启停服务或执行 Git 提交、推送、重置。

## A. 执行摘要与 GO / NO-GO

**NO-GO：当前不能宣称 Base MES 已完成真实 E10 EXTERNAL 生产仓储闭环。GO：已有独立 SDK、MES 可选装配和工厂约束下的显式工单 Query 消费入口，可作为下一阶段基础。**

审查期间另一 Agent 正在接入 SDK。按用户最新提示重新读取最终可见 POM、两个消费方类、配置、文档及测试产物，已撤回早期“MES 未引入依赖”的临时观察。最终基线为：

| 仓库 | HEAD | 工作区与证据边界 |
|---|---|---|
| `/Users/user/work/lixin/mes-base` | `35b2e109e5361afa27e38d808941cd8f3695ef28` | 审查包含未提交的 E10 消费接入：mes-admin POM、Registry、application.yml、integration/e10 两个类及测试、消费文档；不是仅审查 HEAD |
| `/Users/user/work/lixin/lixin-common-capability` | `ffbe5725adbcbbc4a542ec2f411f24bc84fe83b8` | 本轮读取时工作区干净；独立 Java 8 SDK |
| `/Users/user/work/lixin/mes-base-ui` | `94167e0c25fd9cb4dfcac5ecef369f6e8b715546` | 包含原有大量生产仓储未提交修改，按当前工作区审查并保留 |

六个直接回答：

1. **Gateway 是否在 MES 运行链生效？** Maven 声明、打包 classpath、可选 Spring 配置和显式 `queryMo` 调用代码均存在；回环 Spring 测试通过。默认开关关闭，没有业务 Service/Controller/同步任务调用该查询类，不能证明正在运行的用户服务有可用 E10 Bean，更不能证明真实 ERP 已被 MES 调用。
2. **哪些真实 ERP 操作已打通？** 当前 MES 没有可证明的真实 E10 业务闭环。SDK 历史报告证明指定身份/入口下的品号 Query 成功一次；这不是 MES 工单、领料或入库成功。工单等用户提供的运行响应是补充样本，不等于本消费接入已经实调。
3. **哪些只有接口或 Mock？** E10 网关操作与已知 DTO 是实际实现；MES `ProductionErpAdapter` / `WarehouseErpAdapter` 的真实 E10 实现缺失，仓库/库存 QueryPort 默认明确失败；领料、撤销、UNKNOWN 和创建→读取→审核链有 Fixture/回环测试，未证明真实 E10 副作用。
4. **距正式领料、报工、成品入库最小缺口？** 可信工单/仓库同步及产品、单位、路线映射；权威库存和用料查询；领料创建/审核/生效判据与原操作核实；ERP 来源下复用 MES 质量放行；带冻结来源、远端身份、幂等和 UNKNOWN 的成品接收，以及基于可信外部接收的正式完工。报工核心已经存在，不应另建 ERP 生产数量核心，也不应把“ERP 报工回传”凭空变为当前闭环必需条件。
5. **P2-1 是否阻断近期接口？** 已知 8 操作/10 属性均不属于 MO 工单、CMS 仓库、IM 库存或 MO 领料/入库操作；不构成这些接口的直接阻断。仍须分别验证这些候选接口自己的写入契约、定位键、业务枚举与生效语义。
6. **下一轮最小闭环？** 先做“已授权 ERP 工厂 → E10 工单 Query/Read 与仓库 Query → 映射校验 → 正式 EXTERNAL 同步 → MES 页面读回”的只读外部数据闭环；随后做“一张工单、一种物料、一条领料、一次真实生效核实 → MES 可信投入与首次报工”。UNKNOWN 原操作核实须与第一项写入同时实现，不能最后补。

## B. Gateway 装配链路及依赖拓扑

### B.1 六层证据不能合并

| 层级 | 当前事实 | 可得结论 |
|---|---|---|
| Maven 声明 | [mes-admin/pom.xml:10](/Users/user/work/lixin/mes-base/mes-admin/pom.xml:10) 引入 `com.lixin:lixin-common-capability-digiwin-e10:0.1.0-SNAPSHOT` | 已声明依赖 |
| classpath | 只读检查 `target/mes-base-0.1.0-SNAPSHOT.jar`，存在 `BOOT-INF/lib/lixin-common-capability-digiwin-e10-0.1.0-SNAPSHOT.jar` | SDK 确实进入现有打包产物；不是只见 POM |
| Spring Bean | [DigiwinE10Configuration:21–80](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/integration/e10/DigiwinE10Configuration.java:21)，由 `@SpringBootApplication(scanBasePackages="dev.mesbase")` 扫描，显式开关控制 | 回环 ApplicationContext 测试证明可装配；当前默认配置不装配；用户进程实际状态 UNVERIFIED |
| MES 业务调用 | [DigiwinE10MoQueries.queryMo:34–48](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/integration/e10/DigiwinE10MoQueries.java:34) 实际调用 `.mo().query`；全 main 源码搜索只有其自身和配置类引用它 | 存在可调用的消费类；没有业务同步入口接线 |
| 真实请求到达 E10 | 消费文档明确真实请求 0，默认开关 false；本轮未访问服务或 ERP | UNVERIFIED，不能以回环请求代替 |
| 厂商业务验收 | SDK 历史品号只读记录；MES 无真实工单/仓储成功证据 | EXTERNAL 生产仓储 NO-GO |

打包内 SDK SHA-256：`2267e3574dfd6ac5c4d1121b52cb397acbef3b021f96b9245bf46083a09ff98e`，与接入方文档记录一致。本轮没有重新构建或解析远程依赖。接入方既有 dependency:tree 记录为 HttpClient 4.5.14、Jackson 2.21.5、SLF4J 2.0.18；独立 SDK 使用 Jackson 2，MES 使用的 tools.jackson 与之共存，现有消费测试已编译运行，未见由此产生的实际冲突。

补充制品核对：同一可执行 JAR 的 `BOOT-INF/classes/dev/mesbase/server/integration/e10/` 含 `DigiwinE10Configuration`、其 Host/Service/Settings、`DigiwinE10MoQueries` 及 Binding 共6个class。消费装配代码和SDK依赖都已打包；此检查未加载Spring或触发查询，不能升级为用户进程Bean存在的证明。

### B.2 实际拓扑

```text
Spring 配置（默认关闭）
  → FactoryIntegrationRegistry.requireConnection(reference)
  → 每个 reference 创建/复用 E10OapiClient，退出 close
  → DigiwinE10MoQueries.queryMo（当前无生产调用方）
  → OapiMoGateway.mo().query → E10Gateway → E10OapiClient → HTTP

现有正式投产/领料
  → ProductionInputSupplyService / MaterialIssueSubmissionService
  → ProductionErpAdapter / WarehouseIntegration → WarehouseErpAdapter
  → [缺少 E10 实现；不能到达上述 Query 消费类或 Gateway]

生产交接 → WarehouseAcceptanceService → LOCAL 门 → LocalStockService
  [ERP 模式在 LOCAL 门被拒绝，未分派至 acceptProductionReceipt]
```

SDK 是无 Spring 自动装配的 Java 8 JAR，不依赖 Base MES、FactoryContext 或数据库；Base 为 Java 21 / Spring Boot 4。没有 all-starter、第三数据源或第二套 Digiwin HTTP/签名实现。新增消费代码在 mes-admin 集成边界，业务生产核心没有 E10 DTO/import。

### B.3 配置、身份和生命周期

[Configuration.digiwinE10MoQueries:48–80](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/integration/e10/DigiwinE10Configuration.java:48) 绑定 `mes.integration.digiwin-e10.connections`；启用但无连接、缺身份字段、provider 不符或 endpoint 与既有 registry.serviceUrl 不同明确失败。`host.id` 允许显式空字符串，其余身份不默认补值；service.id 原样使用，不把 OAPI_MO 当服务身份。

[Registry.requireConnection/valid/snapshot:51–96](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/managed/FactoryIntegrationRegistry.java:51) 复用既有连接引用，检查厂归属、启用、provider、revision、HTTP(S) URL、组织、可解析凭证引用。credential-reference 是 MES 连接准入要求，本消费类没有把它冒充 E10 Token；SDK 不获取/刷新 Token。E10 host/service 身份由独立显式配置提供，真实账套及组织对应关系尚未联调确认。

Client 按启动时 binding map 固定并复用；部分装配失败会关闭已创建的 Client；Bean destroyMethod=close。`queryMo` 每次检查 mode=ERP、provider、Factory、reference、configurationRevision；不符发送前拒绝。没有热更新客户端、自动翻页、启动探测、健康检查、Controller 或定时任务。配置版本改变后不自动接管旧 Client；绑定身份变更应使用新引用和正式停旧启新流程。

## C. Digiwin P2-1 剩余风险

已读取 [P2/P3 原报告](/Users/user/work/lixin/lixin-common-capability/docs/reviews/digiwin-e10-gateway-contract-p2-fixes.md)、[最新阶段收尾](/Users/user/work/lixin/lixin-common-capability/docs/reviews/digiwin-e10-stage-closeout.md)、[SDK README](/Users/user/work/lixin/lixin-common-capability/README.md:326) 和 [接入 Skill](/Users/user/work/lixin/lixin-common-capability/skills/digiwin-e10-client-usage-skill.md)。最新状态为 **DEFERRED / CONTRACT_UNVERIFIED**，不是已修复，也没有增加运行时禁用。P2-2 文档/API 编译口径和 P3 确定性问题已修复，不能继续套用旧失败状态。

本轮直接遍历 `scripts/e10-generated-contracts.json` 的 operation/request 节点，确认恰好以下 8 项及 10 个 `jsonPath=null / WIRE_BINDING_UNCONFIRMED` 属性：

| 操作 | 内部属性 → 已知交换 Alias | 未知事项及近期影响 |
|---|---|---|
| `OAPI_AP_EMP_CLAIM_DOC.ReqConfirm` | `REQ_CONFIRM_DATE` → `application_confirm_date` | JSON 位置/表示未知；员工报销，不在本次生产链 |
| `OAPI_AP_EMP_LOAN_DOC.ReqConfirm` | `REQ_CONFIRM_DATE` → `application_confirm_date` | 同上；员工借款 |
| `OAPI_PO_SUPPLIER.ReqApproval` | `Owner_Org_SUPPLY_CENTER_SUPPLY_CENTER_CODE` → `om_region_id`；`Owner_Org_COMPANY_COMPANY_CODE` → `om_company_id` | 同上；供应商申请审核 |
| `OAPI_PO_SUPPLIER_FI.ReqConfirm` | `ApproveStatus` → `approve_status` | 同上；供应商财务 |
| `OAPI_PO_SUPPLIER_PURCHASE.ReqConfirm` | `ApproveStatus` → `approve_status` | 同上；供应商采购 |
| `OAPI_SD_CUSTOMER.ReqApproval` | `Owner_Org_SALES_CENTER_SALES_CENTER_CODE` → `om_region_id`；`Owner_Org_COMPANY_COMPANY_CODE` → `om_company_id` | 同上；客户申请审核 |
| `OAPI_SD_CUSTOMER_FI.ReqConfirm` | `ApproveStatus` → `approve_status` | 同上；客户财务 |
| `OAPI_SD_CUSTOMER_SALES.ReqConfirm` | `ApproveStatus` → `approve_status` | 同上；客户销售 |

这些是默认项目根 Request.Properties 相对定位键的差集，**不是已经证实必须由调用方发送的必填字段**。额外字段不在当前类型化 Key 中；不能据此宣称真实调用一定失败，也不能称请求完整。官方服务参数证明外层可用 `std_data.parameter.data_keys` 和 `dataExchangeProjectID`，没有证明这 10 属性应放在 data_keys 的哪一级、用内部键或 Alias、采用何种值类型。

请求/响应的独立源码核对示例：[EmpClaimDocContract.ReqConfirmRequest/ReqConfirmKey:2555–2582](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/contract/ap/EmpClaimDocContract.java:2555) 的 `setKeys` 确实生成 data_keys，Key只含公司定位键与DOC_NO，没有申请确认日期。文件其他响应模型含 application_confirm_date，不能据此倒推出 ReqConfirm 的请求位置。这是输入缺口，不能用“同文件已有同名响应字段”消除。

[E10Gateway.execute:77–88](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/E10Gateway.java:77) 严格限制顶层字段；`E10DataKeysRequest.setDataKeys(JsonNode)` / `E10DataRequest.setDatas(JsonNode)` 可保留已知容器内的 JSON；底层 `execute(serviceName, parameter)` 接受原参数树。**有受控扩展技术入口不等于已确认这 10 项的绑定，也不是绕过契约风险的依据。** 本轮不补字段、不研究处理器实现、不发送探测。

主证据 ZIP SHA-256 为 `7fe9dad5000e86c7a9323760a5bc0b7a27a74aa382a6c78e07c9de913c5573e8`。P2 报告已逐项定位 AP/PO/SD 的 StdRestfulInfoContainer、默认 DataExchangeProjectContainer、PropertyMappings、DataLocator；Server.FunctionPackage 仅引用 .NET 程序集，没有实际处理器代码或八项完整官方请求报文。现有资料不能填补 JSON 绑定。**阻断这 8 项的正式新业务采用，直到取得逐项官方绑定或经授权的准确成功样本；不阻断已明确的 MO/CMS/IM 候选接口进入各自验收。**

另有独立风险：3 类型/12 操作缺独立模型、13 条集合路径缺父 Alias、复合定位键部署绑定未证、MO_RECEIPT 的尾随空格 Alias、自定义项目和部署版本差异。它们不是 P2-1 的同一个问题；例如 `is_generate_org_ledger_entry ` 必须保留尾空格，不能自行 trim，更不能据其字段名臆测库存已过账。

## D. 工单查询与同步

当前真实源码调用止于 [DigiwinE10MoQueries.queryMo:34–48](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/integration/e10/DigiwinE10MoQueries.java:34)。它要求显式 pageNo/pageSize、schema/count 标志、conditions/orders，调用 `e10.oapi.wo.list.data.query.get`，返回 `E10QueryBusinessResponse<MoQueryRow>`。SDK 无自动翻页或增量水位；MES 尚无分页采集、按更新时间过滤、稳定排序、去重、删除语义、断点重启的业务同步调用方，也无 Query/Read → ExternalPlan 转换器。conditions/orders 内部格式不能猜测。

[WorkOrderService.syncExternal:195–259](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/WorkOrderService.java:195) 是真正存在的受信落库入口，但全生产源码未发现调用它的同步执行器。其能力：

- 必须当前 EXTERNAL/ERP 准入，run 必须属于当前 Factory 且 syncKey=`workorder.erp`，执行前、处理后及提交前验证 SyncCoordinator owner。
- externalId 必须非空且不超过 128；输入不能夹带另一套外部身份。同 provider/binding 外部 ID 不得指向另一工单；同编码已有 LOCAL 或 NULL 明确拒绝。
- 新工单固定 source=EXTERNAL；Extra 冻结 externalId/code、provider、bindingKey、configurationRevision，不在核心中存厂字段。
- 已有工单必须同 EXTERNAL 身份/版本、状态 DRAFT；已有 Execution、旧 Batch 或阻塞领料时拒绝。ERP RELEASED 后也不能借此入口再次覆盖计划；这是当前保守冻结规则，不是增量更新已完成。
- 只写计划、产品、路线、时间、备注及允许状态；不写报工、质量、投入、接收等 MES 不可变事实。

`MoQueryRow` 已有 doc_no、plan_qty 等查询字段，`sys_MainId` 属于运行观察扩展；**MES 到底采用哪个稳定外部 ID、如何处理单号作用域和变更仍 UNKNOWN**。不能自动将 doc_no、显示编码或 sys_MainId 当成已验收的稳定映射。还缺物料/单位/工艺路线的实际匹配和 ERP 审核/结案状态到 DRAFT/RELEASED 的经确认转换。已有生成 DTO 不能完成这些业务决策。

## E. 仓库同步、库存镜像与权威查询

[WmWarehouseServiceImpl.sync/upsert:197–317](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/wm/service/impl/WmWarehouseServiceImpl.java:197) 复用 `WmWarehouseQueryPort`；[默认 Adapter.query:13](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/wm/sync/UnconfiguredWmWarehouseQueryAdapter.java:13) 明确 `SYNC_QUERY_NOT_IMPLEMENTED`。没有 E10 QueryPort 实现，没有调用 `OapiCmsGateway.warehouse().query/read`。

同步核心已有：最大 1000 输入、重复 code/externalId 拒绝、同编码仅可更新 EXTERNAL、同 provider/binding 外部身份不能改派、映射版本一致性、run token 和提交前 owner 校验。新仓库 source 固定 EXTERNAL，Extra 绑定本地仓库 ID 与传入外部 ID。LOCAL/NULL 同码在写入前拒绝，不覆盖。**外部 ID 的 E10 组合规则、据点/仓库键如何转成稳定 ID 尚未实现。** 官方 ReadKey 是 `WAREHOUSE_CODE`、`Owner_Org.RTK`、`Owner_Org_PLANT_PLANT_CODE`，不能只取 warehouse_no 推定全账套唯一。

[WmInventoryServiceImpl.sync/upsert:237–307](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/wm/service/impl/WmInventoryServiceImpl.java:237) 只在 CONNECTED 镜像模式工作，使用 `WmInventoryQueryPort`；默认同样未实现。按物料/仓库/批次/库位粒度校验，要求可信 EXTERNAL 仓库映射，不改变 LOCAL 仓权威。Query 在 claim/upsert 之前执行，持有业务写锁时没有该外部 Query 调用；未来 Adapter 仍须限定全量/分页边界，当前没有自动抓完 ERP 分页的证明。

`OapiImGateway.itemWarehouse().query` 为品号仓库数量；`itemWarehouseBin().query` 含批次/库位/数量，可作为候选。它们的 inventory_qty 不是已证明可领用数量，也不证明物料可用、质量放行、单位换算或负库存政策。正式生产供给 [ProductionInputSupplyService.query:57–75](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionInputSupplyService.java:57) 在 CONNECTED 下要求唯一 `ProductionErpAdapter`，由该 Adapter 提供当前权威库存、工单与需求依据；当前无实现，不能用 wm_inventory 镜像替代。

前端 [wmInventory:155–160](/Users/user/work/lixin/mes-base-ui/src/views/wm/wmInventory/index.vue:155) 明确“外部镜像仅供参考”，同步按钮仅 ERP 模式；[wmWarehouse:112–119](/Users/user/work/lixin/mes-base-ui/src/views/wm/wmWarehouse/index.vue:112) 限 LOCAL 编辑、来源只读。没有发现前端直接构造 E10 Header 或调用 E10 地址。

## F. ERP 正式领料链路

### F.1 已有 MES 编排

[ProductionInputService.submit:142–163](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionInputService.java:142) 挂起外层业务事务，先查原 UUID，原请求重放不再外部创建；在事务外计算权威供给，短事务校验计算指纹、锁 WorkOrder/Execution、创建或复用永久生产身份、领料及冻结投入。CONNECTED 不设置本地 stockId。

[MaterialIssueSubmissionService.prepare:138–197](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueSubmissionService.java:138) 在锁内校验执行/路径/领料明细，写 submission attempt，冻结 `ISSUE-SUBMIT-<attemptId>`、路径版本、工单外部 ID/code、产品/物料、单位、批次、数量、仓库本地与 externalWarehouseId、provider/binding、Factory revision/configurationRevision 和计算依据。

短事务提交后 [executePrepared:111–135](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueSubmissionService.java:111) 调 WarehouseIntegration；结果回到另一短事务，复核 submission/version/status 后更新。全 main 实现搜索仅有 `LocalWarehouseErpService implements WarehouseErpAdapter`，未见 E10 实现。`ProductionErpAdapter` 仅有 query Port，没有实现，不应按接口名宣称其发送了领料。

### F.2 缺少的 E10 语义

SDK `issueReceipt().create/confirm/read/query/disConfirm/voidOperation` 均有真实路由、DTO 与序列化实现；尚无 MES 到这些 DTO 的转换、单据类型/据点/员工/部门/工单/特征/批次/库位/单位映射和厂商步骤编排。原 operationKey 尚未映射为 ERP 可查的唯一业务字段；Gateway correlationId 不发送给 ERP，不能充当幂等键。

Create、Confirm、DisConfirm、Void 是不同操作；不能把创建当审核、审核当实际库存出库、作废当库存返还。**哪些步骤构成目标部署的实际出库、生效状态码是什么、库存不足由哪个已确认业务错误判定，目前 UNKNOWN。** ERP 最终命令应判定库存约束，预览仅是当时观察；不能以本地镜像预扣证明 ERP 受理。

[safe/apply:200–245](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueSubmissionService.java:200) 要求 CONNECTED 成功有 externalReference 且 stockEffective=true；只有建单成功而未证明出库，转 SUBMIT_UNKNOWN。**stockEffective 由厂商 Adapter 的业务判据产生，SDK 不计算，当前没有真实生产方赋值。** 既有 `ErpActionResult` 五参构造默认 false，避免创建成功自动投入。

EFFECTIVE 核实分支可确认投入并锁路径；`MaterialIssueInputFacts.confirm:55–67` 只对 LOCAL_ONLY 调本地库存 Owner，CONNECTED 只确认 MES 投入，不再本地扣库存。[ExecutionPathService.start:150–183](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathService.java:150) 要求已锁路径、CONFIRMED 领料及 CONNECTED+CONFIRMED externalEffect+EFFECTIVE documentStatus，才置 RUNNING。领料确认本身不直接置 RUNNING；首次正式报工等调用既有 start 合同。

UNKNOWN 保留 attempt/UUID/source；[MaterialIssueErpService.query/reconcile:74–131](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueErpService.java:74) 查询原 operationKey/外部身份，核实后版本比较写回。NOT_FOUND 明确不能重建，DRAFT/PENDING 不提升可信投入。当前有恢复入口，但没有 E10 按原请求定位及判断 EFFECTIVE 的实现，故不能自动恢复真实 E10 超时。

## G. 生产交接、质量与 ERP 成品接收

[ProductionQuantityService.load:57–93](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionQuantityService.java:57) 从MES已确认投入、确切实际路径、报工净量和质量影响汇总数量，没有E10调用或用ERP计划量替换有效报工的分支。[ProductionReportSubmitService.submit:97–133](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReportSubmitService.java:97) 复用来源准入、数量校验、人员资格与正式start合同，写MES事实；没有为EXTERNAL另建报工核心。QualityRelease.state:67–90使用这同一数量依据和MES质量处置事实生成放行依据指纹，不从ERP计划直接派生可放行额度。ERP来源后续可复用这些规则，前提是领料/投入及来源已可信。

[ProductionReceiptService.receive:99–136](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionReceiptService.java:99) 是 MES 生产交接事实：要求运行、锁定路径、同来源仓库、有效产出和质量额度，可按剩余额度多次交接；没有 E10 网络请求，也没有直接增加库存。它本身没有 LOCAL_ONLY 模式门；ERP 链被其质量依据与后续仓储接收缺口阻塞，不能笼统称整个交接类只支持 LOCAL。

**当前明确阻断点：** [QualityReleaseService.execute:130–132](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/quality/service/QualityReleaseService.java:130) 拒绝 ERP 模式并提示“须由真实质量集成实现”。这不是已存在 ERP 质量集成的证明。已确认边界要求 Quality/ProductionQuantity 仍由 MES 决定，最小改造应按可信 EXTERNAL 输入复用 MES 质量规则和不可变放行事实，不能为解除该门直接伪造外部放行或强制新建 ERP 质量子系统。

[WarehouseAcceptanceService.accept:99–122/local:191–193](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/wm/service/WarehouseAcceptanceService.java:99) 在同源校验后明确 LOCAL_ONLY，写事实时直接 `LocalStockService.receiveProduct`。`WarehouseErpAdapter.acceptProductionReceipt` 只有默认拒绝方法，WarehouseIntegration 没有暴露相应分派，正式 Service 没有调用；不是一个换 Bean 就能完成的入口。

Schema 也是 LOCAL 接收模型：[V35:30–45](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/db/business/V35__local_production_closure.sql:30) 要求 stock_id、stock_action_id 非空，没有远端接收意图、原外部单号、部分生效、配置冻结或 UNKNOWN 生命周期。现有 `ProductionReceiptAcceptanceCommand` 只有产品/批次/仓库编码、数量、source 等，缺业务单位、可信外部仓库 ID 及足够工单/明细定位；后续可从可信映射补全，不能直接把本地 ID/Code 当 E10 外键。

SDK `moReceipt().create/confirm/read/query` 是生产入库候选，`moReceiptRequistion()` 是另一类型的入库申请，当前只有 Query/Read/审核/逆向等入口，**没有该类型的 Create 方法**。不能根据中文“申请”拼出不存在的创建接口，也不能把交接保存成功当远端入库成功。

终态另有独立门禁：[ProductionCompletionService.complete:124–126](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionCompletionService.java:124) 在来源校验后只允许 LOCAL_ONLY。其 summary:83–108 已按有效产出、质量、交接、实际接收和未决领料计算完工条件；即使未来接通 E10 入库，该显式 LOCAL 门也不能使 EXTERNAL 执行完工。最小实现应复用该数量判定和不可变完工事实，以可信外部接收为依据扩展准入；不能仅因 ERP 建单成功就置 COMPLETED。

最小外部接收需：每次接收 UUID/分批数量先冻结意图与原来源，在事务外执行 E10 工作流；按远端明确生效的数量/明细确认 MES 不可变接收事实，拒绝不产生正向接收，超时保留 UNKNOWN 并阻止超额重发；部分成功逐项核对。不能在持有生产链锁的现有 accept 事务中直接塞 HTTP，不能为 ERP 填虚拟本地 stockId/stockActionId。当前没有该远端流程，因而没有“ERP 拒绝但本地仍入库”的已接通路径；这属于待实现边界，不是已经发生的事故。

## H. Factory、source 和冻结绑定

证据：[FactoryIntegrationSnapshot](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/execution/FactoryIntegrationSnapshot.java)、[Context](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/execution/FactoryIntegrationContext.java)、[Executor](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/execution/FactoryIntegrationExecutor.java)、Registry、[BusinessSourceRules](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/BusinessSourceRules.java:77)、[WarehouseIntegration](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/integration/warehouse/WarehouseIntegration.java:28)。

| 场景 | 当前源码行为 | 证据/限制 |
|---|---|---|
| LOCAL 工单+LOCAL 原料/成品仓，LOCAL 模式 | 通过来源门，继续权限/数量/状态规则 | BusinessSourceRules.current/warehouses/execution；不是保证任意业务都成功 |
| EXTERNAL 工单+EXTERNAL 仓，ERP 模式 | 要求 Extra ID/provider/binding/revision；正式供给/领料还要求唯一真实 Adapter | 来源准入已实现；E10 Adapter 缺失，实际业务拒绝 |
| LOCAL 工单+EXTERNAL 仓，或反向 | 拒绝 BUSINESS_SOURCE_CONFLICT | 不能用 UI 隐藏代替后端校验 |
| ERP 配置缺失/异常 | Registry/托管 snapshot 明确失败 | WarehouseIntegration 在 managed runtime 不回退 legacy/LOCAL |
| EXTERNAL 映射缺失/版本变化 | 拒绝新业务/同步接管 | BusinessSourceRules.order/warehouses/binding |
| ERP 同步遇同码 LOCAL/NULL | WorkOrder/Warehouse 写前拒绝 | WorkOrderService:220，WmWarehouseServiceImpl:282 |
| 历史 LOCAL 原 UUID | 重放优先读取原事实；合法历史逆向用 frozenLocal | 不通过当前模式创建新的交易 |
| 原外部 UNKNOWN | 沿原 provider/binding/sourceContext 分派查询 | 缺原 Adapter/配置能力明确失败；尚无 E10 历史版本解析实现 |
| CANCELLED 新正向业务 | 活跃状态门拒绝，锁序与取消共用 WorkOrder 锁 | ProductionChainLocks.requireActive；原 UUID 及合法历史核实另行处理 |
| 176 张 NULL 已作废工单 | 同码同步先因 source=NULL 被拒，非 DRAFT/不同外部 ID 也不能更新 | 不靠批量回填 LOCAL/EXTERNAL 接管；本轮没有重新查库或改这 176 张 |

V36 来源不可变 Trigger 对已有非空来源阻止修改；NULL 不自动成为可信来源。V37 取消事实不可 UPDATE/DELETE。FactoryScope 只含 FactoryId，业务按独立库路由，不新增业务 factory_id。当前 HTTP 请求快照和后台 Executor 是上下文入口，不从调用参数选账套。

冻结 sourceContext 是标识与版本，不是完整旧连接配置归档。新增 `DigiwinE10MoQueries` 只按当前 ERP 快照/启动 Client 校验，不能原样拿去查询已切换账套的历史 UNKNOWN。未来写 Adapter 必须能精确选择原配置版本，或在原版本不可用时拒绝并人工核实；不能改用“当前连接”。

## I. 事务、幂等、UNKNOWN 与错误模型

### I.1 锁与提交边界

正式领料是“短事务 prepare（WorkOrder→Execution→Issue，LOCAL 时再到 Stock）→ 事务外 Adapter → 短事务 compare/apply”；ProductionInputSupply 的远端 query 同样在 NOT_SUPPORTED 方法中，返回后短只读事务核验本地映射。领料撤销 [MaterialIssueVoidService.perform:87–159](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/issue/service/MaterialIssueVoidService.java:87) 也先保存稳定撤销身份、解锁，再调用，最后复核版本写回。未见这些现有编排在 WorkOrder/库存写锁中等待 ERP HTTP；这只是源码边界，当前无真实 E10 写调用的运行锁证据。

远端成功后本地确认失败，已提交 intent 仍保留，不能重发 Create。响应丢失时 UUID 读回同一 attempt；NOT_FOUND 不证明未创建。远端与 MES 的两个提交不原子，缺 E10 原单据查找意味着暂时只能保留 UNKNOWN 并人工核实。工单作废只改后续准入，不回滚此前领料。

### I.2 逐类幂等判断

| 接口类别 | MES 身份 | ERP 唯一身份 / 查询 / 幂等 | 自动恢复结论 |
|---|---|---|---|
| 工单/仓库同步 | SyncCoordinator Factory+syncKey+runToken；Extra 外部 ID | 真实稳定 ID、增量水位与映射待实现 | 无实际 E10 同步任务，不存在已验收恢复 |
| 供给/库存 Query | 当前合法 Factory/binding，显式条件 | 无写入，但批次/单位/可用量语义待确认 | 不自动重试或跨页；不能把查询快照当锁 |
| 领料 Create/Confirm | request UUID、submissionId、operationKey、冻结 commandJson | ERP 可查唯一业务标识/唯一约束 UNKNOWN；返回 doc_no 可用于后续定位，但首次超时可能未取得 | MES 本地不重建；E10 核实实现缺失，不可自动完成 |
| 领料撤销 | voidOperationId、原外部 reference、sourceContext | DisConfirm/Void/退料与实际返还契约 UNKNOWN | 只核实原撤销，不重复写；人工受阻项保留 |
| 成品接收 | LOCAL 已有 UUID/指纹；外部 intent 尚无 | 远端分批/明细与请求关联、幂等 UNKNOWN | 不可开始真实写闭环 |
| ERP 工单作废 | MES V1.8 UUID 只用于本地取消事实 | SDK Void 路由存在，MES 远端取消用例/核实/幂等缺失 | EXTERNAL 当前明确拒绝 |

SDK 禁自动重试/跳转，既没有 ERP 幂等保证，也没有因为携带 MES UUID 就获得该保证。部分成功须逐项保存/核对，不能整体重发或吞掉成功项。

### I.3 错误分类与消费缺口

[E10Exception:4–29](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/E10Exception.java:4) 保留 Reason、Outcome、HTTP 状态、响应、correlationId、cause；[E10OapiClient.execute:106–198](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/E10OapiClient.java:106) 和 E10Gateway 共同保证正常返回前检查 HTTP、execution、逐项结果及已知 DTO 结构。当前 API 已不是旧文档中的分层 isHttpSuccess/requireBusinessResponse。

| 场景 | SDK/现有 MES 行为 | 尚缺的业务映射 |
|---|---|---|
| 参数错误 | INVALID_REQUEST/NOT_SENT；本消费类不完整 Query 参数先 IllegalArgumentException | 写 Adapter 应转明确未发送拒绝，不能全部 UNKNOWN |
| 配置缺失/无 Adapter/版本错 | FoundationException INTEGRATION_NOT_CONFIGURED 或来源冲突；无 LOCAL 降级 | 对业务入口保持可诊断、安全错误 |
| 401/403/其他非 2xx | HTTP_STATUS，保留 HTTP 码与投递确定性 | HTTP 鉴权与 ERP 内层鉴权需按准确返回识别，不能靠猜字符串 |
| HTTP 成功但业务拒绝/部分成功 | EXECUTION / RESULT_ERROR；保留成功/失败项 | ERP 库存不足等官方错误码、已生效项与未生效项映射 UNKNOWN |
| 超时/断连 | TIMEOUT/CONNECTION，可能 UNKNOWN | 查原 operation，不重发 Create |
| 业务成功但是否库存生效未知 | SDK 只证明本次操作成功，MES safe 转 UNKNOWN | Adapter 缺权威生效判据 |
| 已明确库存生效 | MES 需 stockEffective=true / query EFFECTIVE+稳定 ID | 当前只有契约/Fixture，没有真实 E10 判定实现 |

`DigiwinE10MoQueries` 原样传播 E10Exception，是正确的底座保留。现有领料 submit 的 catch RuntimeException、撤销 catch Exception、核实的普通 RuntimeException 分支会统一变 UNKNOWN 并写安全摘要；保守地阻止重复写，但不能区分 NOT_SENT、明确业务拒绝和部分成功，且会把 exception/cause 交给日志。未来 E10 写 Adapter 应在该边界前结构化转换并保留安全关联信息，不打印完整 ERP cause/正文；当前没有证据证明已经泄露真实信息。

## J. ERP 工单作废边界

[WorkOrderCancellationService.cancel:70–100](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/WorkOrderCancellationService.java:70) 仅允许 DRAFT/RELEASED；非空来源必须 LOCAL，NULL 只走专用历史终止分支；仍要求当前 LOCAL 准入。UUID 重放和取消事实独立于后续模式，不改来源、Execution、路径、投入、报工、质量、仓储或库存，不强制 COMPLETED 改 CANCELLED。

EXTERNAL 明确 EXTERNAL_SOURCE_READ_ONLY，不能在 ERP 已领料后以本地状态终止代替远端撤销。SDK `MoGateway.voidOperation` 已映射 `e10.oapi.wo.data.invalid`，但其请求键、前置状态、已领料工单的合法远端处理及 UNKNOWN 核实未在 MES 实现/真实验收。存在 SDK 方法不等于远端工单作废业务已打通，也不能自动按名称选择 DisConfirm→Void 顺序。

领料 UNKNOWN 与撤销 UNKNOWN 使用各自既有查询/核实入口，按原操作冻结身份处理，不能因 WorkOrder 后来取消而把原副作用抹掉。已有 source=NULL 176 张的正式 API 取消与库存不变证据来自 [V1.8 报告](/Users/user/work/lixin/mes-base/docs/reviews/2026-10-17-workorder-cancellation-v18-report.md)，本轮未重新执行清理、读库或写库。

## K. 真实能力矩阵

Gateway=SDK 路由/已知模型实现，Adapter=真实 E10 MES 适配，Service=可复用 MES 业务用例，Schema=当前仓库迁移定义，测试=该端到端能力的既有离线证据，真实 E10=本 MES 用例运行证据。IMPLEMENTED 不代表已部署/实调；PARTIAL 表示只覆盖该行的一部分；Schema 不冒充当前真实库 metadata。没有以名称猜测 E10 生效能力。

| 业务能力 | Gateway | Adapter | Service | Schema | 测试 | 真实E10验证 |
|---|---|---|---|---|---|---|
| 工单查询 | IMPLEMENTED | PARTIAL | MISSING | IMPLEMENTED | PARTIAL | UNVERIFIED |
| 工单同步 | IMPLEMENTED | MISSING | PARTIAL | IMPLEMENTED | PARTIAL | UNVERIFIED |
| 仓库查询 | IMPLEMENTED | MISSING | CONTRACT_ONLY | IMPLEMENTED | PARTIAL | UNVERIFIED |
| 仓库同步 | IMPLEMENTED | MISSING | PARTIAL | IMPLEMENTED | PARTIAL | UNVERIFIED |
| ERP库存查询 | IMPLEMENTED | MISSING | PARTIAL | IMPLEMENTED | PARTIAL | UNVERIFIED |
| ERP领料单创建 | IMPLEMENTED | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIAL | UNVERIFIED |
| ERP领料实际生效 | PARTIAL | MISSING | PARTIAL | IMPLEMENTED | PARTIAL | UNVERIFIED |
| ERP领料撤销 | PARTIAL | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIAL | UNVERIFIED |
| ERP单据UNKNOWN核实 | PARTIAL | MISSING | PARTIAL | PARTIAL | PARTIAL | UNVERIFIED |
| ERP成品入库 | IMPLEMENTED | MISSING | CONTRACT_ONLY | PARTIAL | PARTIAL | UNVERIFIED |
| ERP成品接收生效 | PARTIAL | MISSING | MISSING | MISSING | PARTIAL | UNVERIFIED |
| ERP工单作废 | IMPLEMENTED | MISSING | MISSING | PARTIAL | PARTIAL | UNVERIFIED |

工单查询 Adapter=PARTIAL 特指新增按厂约束的消费类，并不表示已有 ProductionErpAdapter 实现。生效/撤销 Gateway=PARTIAL 指候选原子接口存在，但没有被证明等价于所需完整业务结果。UNKNOWN Schema=PARTIAL 指领料已有 intent/Extra，成品外部接收没有。ERP 工单作废 Schema=PARTIAL 仅有本地取消事实，缺远端操作记录。测试 PARTIAL 仅指相关 SDK 路由/回环或 MES Fixture，不是各行真实 E10 验收。

## L. 操作编码、DTO 与映射清单

### L.1 当前消费与近期候选入口

以下 E10 编码直接取当前 Gateway `@E10OapiOperation` 与原 ZIP 索引；除第一行 Query 外，没有 MES 生产调用方。表中“候选”是对既有能力的实施建议，不代表已选定厂商业务步骤。

| 类型/业务与操作编码 | Gateway Java 方法 | 请求 DTO → 响应 DTO | MES 调用/转换位置、P2-1 |
|---|---|---|---|
| MO 工单 Query `e10.oapi.wo.list.data.query.get` | `OapiMoGateway.mo().query`，MoGateway:25 | E10QueryRequest → E10QueryBusinessResponse<MoQueryRow> | DigiwinE10MoQueries:48 显式入口；未转 ExternalPlan；不受已知8项影响 |
| MO 工单 Read `e10.oapi.wo.details.data.read.get` | `.mo().read`，MoGateway:34 | MoContract.ReadRequest / E10DataKeysRequest → E10OperationBusinessResponse<MoContract.ReadResponseData> | 缺 MES 调用方；用于详情/用料候选；不受8项影响 |
| CMS 仓库 Query `e10.oapi.warehouse.list.data.query.get`；Read `e10.oapi.warehouse.details.data.read.get` | `OapiCmsGateway.warehouse().query/read`，WarehouseGateway:25/34 | E10QueryRequest / WarehouseContract.ReadRequest → WarehouseQueryRow / WarehouseContract.ReadResponseData，分别置于 Query/OperationBusinessResponse | 缺 WmWarehouseQueryPort 实现；不受8项影响 |
| IM 品号仓库 Query `e10.oapi.item.warehouse.list.data.query.get` | `OapiImGateway.itemWarehouse().query`，ItemWarehouseGateway:25 | E10QueryRequest → QueryBusinessResponse<ItemWarehouseQueryRow> | 缺库存/供给 Adapter；不受8项影响 |
| IM 批次库位 Query `e10.oapi.item.warehouse.bin.list.data.query.get` | `.itemWarehouseBin().query`，ItemWarehouseBinGateway:25 | E10QueryRequest → QueryBusinessResponse<ItemWarehouseBinQueryRow> | 批次库存候选，缺实际转换；不受8项影响 |
| MO 领退料 Create `e10.oapi.picking.return.data.create` | `OapiMoGateway.issueReceipt().create`，IssueReceiptGateway:79 | IssueReceiptContract.CreateRequest / E10DataRequest → OperationBusinessResponse<IssueReceiptContract.CreateResponseData> | WarehouseErpAdapter.createMaterialIssue 待实现；不受8项影响 |
| MO 领退料 Confirm `e10.oapi.picking.return.data.approve` | `.issueReceipt().confirm`，:43 | IssueReceiptContract.ConfirmRequest / E10DataKeysRequest → 对应 ConfirmResponseData | 缺厂商编排/库存生效判断；不受8项影响 |
| MO 领退料 Read `e10.oapi.picking.return.details.data.read.get`；Query `e10.oapi.picking.return.list.data.query.get` | `.issueReceipt().read/query`，:61/52 | ReadRequest / E10QueryRequest → ReadResponseData / IssueReceiptQueryRow | queryMaterialIssue 待实现；不受8项影响 |
| MO 领退料 DisConfirm `e10.oapi.picking.return.data.disapprove`；Void `e10.oapi.picking.return.data.invalid`；Delete `e10.oapi.picking.return.data.delete` | `.issueReceipt().disConfirm/voidOperation/delete`，:34/70/25 | 对应 DisConfirmRequest/VoidRequest/DeleteRequest（E10DataKeysRequest 子类）→ 对应 ResponseData | cancelMaterialIssue 及核实待实现；三者均不能预认定为已返库存；不受8项影响 |
| MO 生产入库 Create `e10.oapi.wo.receipt.data.create`；Confirm `e10.oapi.wo.receipt.data.approve` | `OapiMoGateway.moReceipt().create/confirm`，MoReceiptGateway:79/43 | MoReceiptContract.CreateRequest / ConfirmRequest → 对应 CreateResponseData / ConfirmResponseData | acceptProductionReceipt 待实现且无 Service 分派；不受8项影响 |
| MO 生产入库 Read `e10.oapi.wo.receipt.details.data.read.get`；Query `e10.oapi.wo.receipt.list.data.query.get` | `.moReceipt().read/query`，:34/25 | MoReceiptContract.ReadRequest / E10QueryRequest → ReadResponseData / MoReceiptQueryRow | 外部接收核实缺失；不受8项影响 |
| MO 生产入库 DisConfirm `e10.oapi.wo.receipt.data.disapprove`；Void `e10.oapi.wo.receipt.data.invalid` | `.moReceipt().disConfirm/voidOperation`，:52/61 | 对应请求（E10DataKeysRequest 子类）→ 对应 ResponseData | 逆向能力候选，未选择合法业务顺序；不受8项影响 |
| MO 入库申请 Query `e10.oapi.wo.receipt.requisition.list.data.query.get`；Read `e10.oapi.wo.receipt.requisition.details.data.read.get` | `.moReceiptRequistion().query/read` | E10QueryRequest / MoReceiptRequistionContract.ReadRequest → MoReceiptRequistionQueryRow / ReadResponseData | 仅为候选查阅边界；本类型没有 Create；不受8项影响 |
| MO 工单 Void `e10.oapi.wo.data.invalid`；DisConfirm `e10.oapi.wo.data.disapprove` | `.mo().voidOperation/disConfirm`，MoGateway:70/61 | MoContract.VoidRequest / DisConfirmRequest → 对应 ResponseData | MES EXTERNAL取消拒绝，没有远端取消调用方；不受8项影响 |

Java 完整响应名均以 `E10` 开头，上表部分行省写 QueryBusinessResponse/OperationBusinessResponse 仅为表格缩写；实际类型以方法签名为准。所有非 Query（含 Read）用 getRecords，Query 用 getRows/getCnt。Gateway 的公开入参为公共父类，Contract.*Request 是带类型化 setKeys/setRecords 的便利子类，不是另一套 HTTP 客户端。

### L.2 实际序列化形状、稳定身份与生效标准

这些形状是当前 DTO/Client 的源码序列化事实；占位值用于说明字段，**不是目标 ERP 已接受的完整可发送业务报文**。所有操作共用唯一 `std_data.parameter` 信封，serviceName 放在 SDK 构造的 digi-service.name，不拼 URL。Query 的确定形状：

```json
{"std_data":{"parameter":{"page_size":20,"page_no":1,"is_get_schema":false,"is_get_count":false,"conditions":[],"orders":[]}}}
```

queryProjectID/dataExchangeProjectID 只在调用方显式设置时发送，null 省略，不补默认方案。Key 和 Data 容器分别为：

```json
{"std_data":{"parameter":{"data_keys":[{"DOC_NO":"<原单据号>"}]}}}
{"std_data":{"parameter":{"datas":[{"om_site_id":"<已确认据点>","doc_type_no":"<已确认单据类型>"}]}}}
```

第一种 DOC_NO 形状适用于表中已确认使用简单单据键的 MO 操作；不得推广到 CMS 复合仓库键或其他服务。第二种只展示共有的已知 Alias，不构成必填清单，也不是能完成创建的最小报文。

| 接口组 | 已知 JSON 字段/官方证据 | 稳定 ID 与请求幂等 | 业务生效、测试与未知边界 |
|---|---|---|---|
| MO Query/Read | Query 列 doc_no/plan_qty 等；Read 用 DOC_NO；`IntegrationObjects/OAPI_MO/StdRestfulInfoContainer.dcxml` 与 `OAPI_MO_MO/{QueryProjectContainer,DataExchangeProjectContainer}.dcxml` | doc_no、观察扩展 sys_MainId 只是候选；MES 最终稳定 ID=UNKNOWN；Query 非写幂等场景 | SDK 检查合法成功响应/rows；不证明 ERP 工单已允许投产。Consumer 34项、SDK全量及MO回环；真实本MES查询未验证 |
| CMS Warehouse | ReadKey 的 WAREHOUSE_CODE、Owner_Org.RTK、Owner_Org_PLANT_PLANT_CODE；Query om_site_id/warehouse_no；来源同类型 DCXML 与 OAPI_CMS 索引 | 可信据点+仓库身份编码方案 UNKNOWN，不能只用显示编号；写幂等不适用 | 查询成功不证明可出/入库。SDK合同/回环；MES同步无E10测试链 |
| IM Inventory | itemWarehouse/itemWarehouseBin Query 列 inventory_qty、warehouse_no；明细有 lot_no/storage_spaces_no/单位字段，来源对应 QueryProjectContainer 与 OAPI_IM 索引 | 库存粒度应包含物料/特征/据点/仓库/批次/库位/单位中的已确认维度；具体映射 UNKNOWN | inventory_qty 到可领用量/质量/单位规则 UNKNOWN；无真实 MES 库存验收 |
| IssueReceipt Create | datas 根含 om_site_id/doc_type_no/doc_date/property_type/workstation_no/source_no/remark，集合 picking_return_detail 与 picking_return_wo_detail；单身含 wo_no/material_item_no/item_feature_no/warehouse_no/storage_spaces_no/lot_no/business_unit_no/picking_return_business_qty 等，见该类型 DataExchangeProjectContainer Create_0001 | Create 响应 doc_no 可供后续读取；MES operationKey 的远端落点、唯一约束和查询条件均 UNKNOWN；source_no 有既定来源业务语义，不能擅自塞UUID | 逐项成功仅为创建成功；库存在何步真正生效 UNKNOWN。SDK DTO/回环不等于真实创建 |
| IssueReceipt Confirm/Read/Query/逆向 | 审核/读取/撤销/作废/删除为独立路由，键 DOC_NO；Read/Query 含审核与数量信息，准确枚举未知 | 已得原单号才可明确定位；首创超时无单号时按原请求查找 UNKNOWN | stockEffective/EFFECTIVE 必须经厂商实际生效证据；DisConfirm/Void/Delete 不预认定返库存；写入与核实均未实调 |
| MoReceipt Create | datas 根 production_stock_in_detail；明细 wo_no/production_item_no/item_feature_no/accepted_qty/scrap_qty/damage_qty/business_unit_no/warehouse_no/storage_spaces_no/lot_no/accepted_inventory_qty 等，来源 MO_RECEIPT Create_0001 | MES接收UUID→远端可查身份 UNKNOWN；返回doc_no不解决首次超时定位；分批/明细对应关系未实现 | 创建/审核与生效分别验证；Read 有尾空格 Alias `is_generate_org_ledger_entry `，不改写；SDK Mock链，无真实入库 |
| MoReceipt Read/Query/逆向 | DOC_NO 定位；Query doc_no/approve_status/明细数量，来源同类型 Query/Exchange 项目 | 按原单据核实可用底座；无 MES 外部接收意图与原版本恢复实现 | 如何证明实际库存接收/部分接收/逆向恢复 UNKNOWN |
| MoReceiptRequistion Query/Read | 固定 requisition 路由与该类型项目，不与 receipt 混用 | 单号/明细映射仍需业务确认；不拿 Query 当创建 | 仅生成查询/现有操作合同；没有 Create，不能计划调用不存在方法 |
| MO Void/DisConfirm | DOC_NO，MoContract 对应 Key、ResponseData 及默认项目 | MES本地取消UUID没有发送到ERP；远端幂等/核实 UNKNOWN | 不能证明允许取消已领料工单，更不能推断库存自动逆向；仅 SDK合同/Mock |

本轮核对了 ZIP 哈希及 MO/CMS 索引、领退料/入库项目 XML 可解析性，读取生成清单的字段/路径与 Gateway 方法；完整定义来源为 `docs/contracts/digiwin-e10/source/IntegrationObjects.zip`，资料入口见其 README。P2 的 8 项未闭合绑定不在上述候选集中；日期/数字/布尔交换标量保留 JsonNode，官方字段声明不是全部必填或部署枚举证明。未取得可靠证据的具体值、幂等字段、状态码和原子性全部保留 UNKNOWN。

## M. P0 / P1 / P2 / P3 问题明细

本轮没有证实可达的跨 Factory/账套写入或不可逆库存错误，**无已确认 P0**。不能把缺实现推断成已发生库存事故，也不能因当前关闭而免除未来写入前的安全验收。

| 编号/优先级 | 文件、类、方法、行号 | 当前行为及影响 | 最小处理建议 |
|---|---|---|---|
| F01 / P1 | DigiwinE10MoQueries.queryMo:34–48；WorkOrderService.syncExternal:195–259；WmWarehouseServiceImpl.sync:197–204；默认 QueryAdapter.query:13 | 工单消费 Query 存在但无人调用；仓库Port无E10实现，缺映射/分页/同步执行器，无法获得正式 EXTERNAL 计划与仓库 | 复用现有 SyncCoordinator 和 syncExternal/upsert，新增最小厂商转换/QueryPort，不新建同步平台 |
| F02 / P1 | ProductionInputSupplyService.query:63–69；WarehouseIntegration.submissionSelection/createMaterialIssue:78–114；WarehouseErpAdapter.createMaterialIssue/queryMaterialIssue 默认方法 | 无 E10 Production/Warehouse Adapter，缺权威库存、需求及领料生效；完整命令无法到 E10 | 实现同一厂商边界内的权威供给、领料工作流、原操作查询和实际生效判据 |
| F03 / P1 | QualityReleaseService.execute:130–132；ProductionCompletionService.complete:124–126 | ERP 模式无条件拒绝 MES 正式放行和完工，挡住质量→交接→接收→终态，与单一 MES 生产核心目标存在未闭合实施边界 | 在原核心内明确 EXTERNAL 的可信来源准入，复用质量/数量/完工规则，以可信接收为依据；不要假造ERP质量成功 |
| F04 / P1 | WarehouseAcceptanceService.accept:104–121、append:150–153、local:191–193；WarehouseErpAdapter.acceptProductionReceipt 默认方法；V35:30–44 | 接收强依赖本地 stockId/StockAction/Owner；无外部分派和远端intent/UNKNOWN持久化，不能真实成品入库 | 最小外部接收命令与意图/确认事实扩展，HTTP移到锁外，按生效量确认，不伪造本地库存动作 |
| F05 / P1（首次写入前） | MaterialIssueSubmissionService.prepare:179–184；MaterialIssueErpService.query:83–107；SDK E10Gateway:87–88 | MES UUID/operationKey 未绑定ERP可查唯一字段；首次创建超时可能无单号，现有Port不能自行核实 | 与厂商确认唯一业务关联及查找，先实现恢复再开放写入；无法核实保留受阻并人工处理 |
| F06 / P2 | MaterialIssueSubmissionService.executePrepared:117–120；MaterialIssueVoidService.perform:133–135；MaterialIssueErpService.query:90–94 | 通用catch压为UNKNOWN，缺NOT_SENT/明确拒绝/部分成功的厂商映射；日志接收整个异常cause | E10 Adapter先按Reason/Outcome/逐项结果安全转换；普通日志只保留允许的分类和关联ID |
| F07 / P2 | DigiwinE10MoQueries.queryMo:35–40；MaterialIssueSubmitCommand.Source；WarehouseIntegration.query/execute | 新Query只认当前snapshot/启动Client；历史操作虽冻结版本，尚无真实E10原版本Client选择/保留能力 | 首个写Adapter明确原provider/binding/configRevision解析；失效时拒绝，禁止重指当前账套 |
| F08 / P2（仅8项） | SDK脚本 e10-generated-contracts.json 的C节8操作 request.unresolvedRequestProperties；对应Contract Key/Gateway Javadoc | 10属性wire路径未知，类型化Key不完整；不直接阻断MO/CMS/IM近期链 | 保持延期，不采用这8操作；后续独立授权取得官方绑定后再研究 |
| F09 / P2 | SDK MoReceiptContract.ReadResponseData 的 `is_generate_org_ledger_entry ` Alias；WarehouseContract.ReadKey；D/E/L所列版本/复合键 | 即使避开P2-1，仍有部署字段表示、复合定位、审核枚举和真实生效证据缺口 | 针对近期实际操作逐项用官方工具/脱敏成功样本核对，不能全局trim或推断状态 |
| F10 / P3 | docs/development/digiwin-e10-consumer.md:153–172；POM:14 | SNAPSHOT可变；本次制品已记录hash，但后续同版本不保证同内容；六层验收口径容易被“引入完成”混淆 | 保留制品hash/构建证据并在后续集成报告延续六层状态，不把默认关闭smoke描述为E10运行验收 |

F01–F05 是真实闭环交付缺口，不是要求本轮补代码。F06/F07 是写Adapter落地前必须解决的消费/恢复风险，当前没有真实E10写入口可证明已触发。ERP工单远端作废缺失见J/K，当前明确拒绝符合边界，不为尽快投产擅自放开。

以上类的完整绝对路径见 B–J 的定位链接；对应类名、方法和一基行号均来自本次工作区。对“缺实现”的证据是跨 mes-*/src/main 搜索实现及调用方，仅找到本地Warehouse实现与两个Unconfigured QueryPort；测试里的匿名/Fixture Adapter不计生产实现。

补充精确定位：F07 的历史分派在 [WarehouseIntegration.query/adapter/execute:117–140](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/integration/warehouse/WarehouseIntegration.java:117)，验证原provider及binding选择，configurationRevision的实际旧客户端解析仍需厂商Adapter实现；F09 的尾空格字段在 [MoReceiptContract.ReadResponseData:147–148](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/contract/mo/MoReceiptContract.java:147)，仓库复合键在 [WarehouseContract.ReadKey:39–64](/Users/user/work/lixin/lixin-common-capability/lixin-common-capability-digiwin-e10/src/main/java/com/lixin/capability/digiwin/e10/contract/cms/WarehouseContract.java:39)。

## N. 最小实施阶段与验收路线

| 阶段 | 最小模块/接口增量 | 依赖与阻塞 | 测试要求及完成标准 |
|---|---|---|---|
| 1：消费装配与工厂身份确认 | 复用已接入 mes-admin 配置/Query类和Registry；明确经授权ERP Factory的endpoint、host/service、reference/revision，不新建Resolver | 当前默认关闭且无已验收真实配置；已有历史工厂的模式锁不能绕过 | 默认关闭零请求、缺配置/跨厂/错provider/ref/revision发送前拒绝、Client关闭；受权只读验证只用目标身份，不修改SIMPLE_B或自动切模式 |
| 2：工单+仓库只读同步闭环（推荐下一轮） | mes-admin E10转换/QueryPort，复用 WorkOrderService.syncExternal、WmWarehouseServiceImpl.upsert 和SyncCoordinator；补最小调用用例/权限入口 | 确认稳定ID、据点、状态、产品/单位/路线映射、分页排序/水位；无额外通用ERP平台 | 回环DTO精确别名、空页/多页/去重/断点、LOCAL/NULL同码拒绝、旧版本/冻结Execution拒绝；明确授权后指定Factory/业务库备份并正式同步，页面读回EXTERNAL；176张取消工单及LOCAL验收样本不变 |
| 3：单材料领料生效→首次MES报工 | 实现 ProductionErpAdapter.query、WarehouseErpAdapter.create/query；复用既有投产/领料/数量/报工核心 | 权威库存/用料、单位/特征/批次映射、单据类型与真实出库判据、ERP唯一请求关联和首创超时查找 | 在首次真实写之前覆盖NOT_SENT、业务拒绝、库存不足、部分成功、超时但已生效、响应丢失、同UUID并发、NOT_FOUND不重建、原版本查询；经独立真实写授权后证明只扣一次ERP库存、MES投入一次、LocalStock不动、路径锁定及首次报工 |
| 4：MES质量→交接→ERP成品接收→完工 | 原QualityRelease及ProductionCompletion受控支持EXTERNAL；WarehouseIntegration/Adapter最小外部分派、接收intent/原绑定/生效确认及必要Schema | F03/F04；目标E10入库流程、分批/单位/生效量和原操作查询 | 同一数量核心；分批/部分成功、不超交、不重复入库、ERP拒绝不确认、UNKNOWN不重发、冻结配置恢复、不可变接收及完工事实，LOCAL库存无误调用；独立授权后真实库存和MES额度对账 |
| 5：有限逆向及厂商验收 | 按实际用例实现领料撤销/入库逆向；ERP工单取消单独明确合法语义 | 远端已领料/已入库/已完成约束与实际库存返还证据 | 按原操作查询和权限；不把DisConfirm/Void等同返库存；前后事实/余额/来源对账及用户复验；不开发LOCAL在制自动迁移 |

UNKNOWN、原版本恢复、安全错误映射应随阶段3/4分别完成，阶段5不能成为它们延期到真实写入之后的理由。真实 ERP 测试、目标工厂模式变更和指定库写入都需要下一轮明确实施范围，本轮未执行。保持一套 ProductionQuantity/Execution/Quality/Receipt 和现有两个适配边界，不重建 OAPI 底座、不引入第三数据库。

## O. 已验证与未验证边界

### O.1 本轮实际执行的只读检查

- 读取三仓库规则、HEAD/status/diff、现行来源边界/ADR-005、V1.7/V1.8报告、ERP领域报告，以及新增消费装配/测试/文档。历史ADR中已退出的第三数据源方案不是当前实施建议。
- 用 rg 追踪 POM、main 实现/调用方、同步Port、事务注解、锁、原UUID、绑定版本、质量与仓储门、前端入口；读取目标DTO/当前Gateway实现和错误模型。
- 读取既有 Surefire XML：MES `DigiwinE10ConsumerTest` 34/34、`FactoryIntegrationConfigurationTest` 4/4，失败/错误/跳过均0；SDK现有13份XML合计168/168，未发现IT报告。**本轮没有重跑这些测试。**
- 只读检查现有MES可执行JAR中的SDK及SHA-256，与接入文档一致；没有启动该JAR。SDK源码/目录实现和打包依赖分别核对，不能仅凭同SNAPSHOT版本推断同内容。
- 读取并核对生成清单中8项/10属性、近期候选字段与路由；重算官方ZIP哈希，内存解析所需MO/CMS XML，不执行公式、DLL或导入ERP。
- 本轮实际运行 `python3 scripts/generate-e10-gateways.py --check`（SDK仓库）：退出0，`Checked 18 groups / 1022 methods; CSV/JSON agree`。这是只读生成一致性检查，没有重新生成或写SDK。

### O.2 既有证据的适用范围

| 证据 | 已记录结果 | 本轮使用限制 |
|---|---|---|
| SDK P2/P3及阶段收尾报告 | E10168/168、reactor375/375、709键值声明、1022路由及803交换合同 | 既有离线结果，未重新跑reactor；不证明8项绑定或真实写入 |
| MES消费接入文档/现有XML | 全量verify710项（668通过、42环境门控跳过）；最后定向38/38；默认关闭打包smoke10/10；示例只编译 | 全量/smoke来自另一Agent本次接入记录；本轮读取XML确认最终38项和制品，不冒称本轮运行，也不以关闭E10的smoke证明ERPBean运行 |
| SDK只读联调报告第13节 | 指定身份/入口的 `e10.oapi.item.list.data.query.get`，HTTP200、execution.code字符串0、5条rows，一次成功；先前失败保留 | 历史真实品号查询；不扩大成工单、库存、写入或本MES调用成功；本轮ERP请求0 |
| SDK MO链测试/运行响应衍生Fixture | 创建→读取→审核、超时UNKNOWN、部分成功、字段保留及路由 | 回环/样本证据；即使响应源于用户真实环境也不是当前MES写入验证 |
| V1.6/V1.7/V1.8既有报告 | 指定SIMPLE_B LOCAL闭环、来源隔离与176张正式取消、余额/事实保护 | 历史真实MySQL/API/UI证据，不是本轮重新核对的数据状态，也不是E10验证 |

### O.3 明确未验证

未查询任何真实数据库或更改Factory模式；未检查用户当前进程的Spring Bean、classpath、endpoint可达性或服务版本；未发送E10只读或写请求；未验证部署版本、真实权限/账套、MO/仓库/库存实际过滤条件、稳定ID、ERP唯一约束、库存生效/不足错误、分批接收与逆向语义、ERP与MES跨系统原子性或UNKNOWN自动恢复。没有新增测试、构建、Flyway、同步任务或代码修复；未改AGENTS及其他Agent的接入文件。

### O.4 原始任务逐项交付核对

| 原prompt要求 | 本报告交付位置 | 证据边界 |
|---|---|---|
| 依赖/版本、Bean、配置、真实拓扑及六层状态 | B | POM、当前源码、制品与既有回环装配证据；用户进程状态未验证 |
| P2-1十项问题、8操作/10属性、可扩展性、JSON及阻断范围 | C、L | 生成清单、DTO、底层参数白名单、原始定义及已保存证据；未知绑定不猜测 |
| 工单查询/分页/增量、稳定ID、同步、冻结及历史保护 | D | Query入口、syncExternal真实写入口及缺调用方；真实E10同步未验证 |
| 仓库查询/同步、同码来源保护、库存镜像与Owner | E、H | QueryPort、来源与绑定校验、同步Service、前端；不把镜像当权威 |
| 领料载荷、创建/审核/实际生效、RUNNING及库存不足 | F、I、L | 冻结命令、三阶段事务、safe/apply/start；E10判据和错误码UNKNOWN |
| 成品交接、单位/批次/仓库映射、分批、接收事实及Owner | G、L | 既有LOCAL实现、外部Port及Schema缺口；无真实外部接收写入 |
| 工单取消、EXTERNAL限制、已领料及原UNKNOWN | J、H、I | V1.8正式取消Service与原身份恢复边界；SDK Void不等于MES远端取消 |
| 单一生产数量、MES质量、不可变路径/事实及完工 | D、G | 数量/报工/质量/完工源码，明确ERP模式门禁与最小复用点 |
| 十种Factory/source/历史场景、176张NULL取消保护 | H | 源码规则与既有测试/报告；本轮没有改库或重跑176张清理 |
| 锁范围、事务、逐接口幂等、错误分类和人工受阻 | I | 现有协调流程与SDK分类；不以UUID代替ERP唯一性保证 |
| 12行×6维能力矩阵、限定单元格值 | K | Gateway/Adapter/Service/Schema/测试/真实E10分开判断 |
| 操作编码、方法、请求/响应DTO、JSON、调用/转换、ID、幂等、生效、官方证据、测试、P2影响 | L | 按实际消费与近期候选分列；无实现或无可靠部署证据明确UNKNOWN |
| P0–P3、文件/类/方法/行、行为/影响与最小建议 | M及各节源码链接 | 无已确认P0；P1闭环缺口、P2恢复/映射风险、P3制品证据分别列出 |
| 最小阶段、模块/接口/依赖/阻塞/测试/完成标准 | N | 仅实施建议，未擅自进入开发或真实ERP操作 |
| A–O完整报告及最终六问 | A–O，六问集中于A | 已交付；验证与未验证明确区分 |

本轮交付仅此审查报告。上述GO仅适用于已明确的源码/离线装配基础；真实EXTERNAL生产仓储仍NO-GO。后续实施以独立任务和逐操作验收为界。
