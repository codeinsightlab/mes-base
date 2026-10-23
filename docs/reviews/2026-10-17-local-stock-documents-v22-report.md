# Base MES V2.2 — LOCAL 正式出入库单据与库存闭环实施及验收

日期：2026-10-17。判定：**GO（V2.2 LOCAL 实现及用户明确授权的 PROD 验收范围）**。

已实现正式其他入库、其他出库及主子单据页面。真实 MySQL/API 完成入库100、出库30、余额70；浏览器完成作废工单人工补入2，成品余额8→10。正式过账仅调用原 LocalStockService，未建立第二套余额 Owner。已发布迁移不改写，未开发 ERP 出入库 Adapter。

## A. 工作区和并行修改保护

后端 `/Users/user/work/lixin/mes-base`，前端 `/Users/user/work/lixin/mes-base-ui`。开始时后端 main/HEAD `34937882832cfccb04a6037027e26b85de37daf9`，前端 main/HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。两仓完整 diff、原 dirty/untracked 文件 SHA256 保存于 `.local/stock-documents-v22-20261017/baseline.json` 和 `*-before.patch`。

执行期间检测到后端 HEAD 被外部更新为 `1336af6bfaca5567aeaa1bc874c18f6daf3f5f19`，其中已包含本轮 V38 SQL；Agent 未执行 commit、push、reset 或恢复旧 HEAD。按开始时字节哈希复核，原后端27个变更文件全部未改、未丢失；原前端59个变更文件均保留，仅以下四个因本轮必要复用而增量修改：CrudPage、MasterDetailDocument、WorkOrderSelect、localStock API类型。证据 `preservation.json`。

遵守个人工程入口、后端/前端规则及当前来源约束；复核 V1.5–V1.8、V2.1 报告及现有库存/生产调用链。SDK 与 E10 并行修改未改动。所有本机证据、凭证、备份均位于 Git 忽略的本轮 `.local` 目录，不写入本报告。

## B. 当前仓储 Schema 与复用分析

真实目标最初指定 SIMPLE_B，但192.168.18.25:3306在普通及解除沙箱后的连接均超时。用户随后明确要求“用prod的环境”，据此重新核实并实际采用：

| 项目 | 核实结果 |
|---|---|
| Factory | PROD，ENABLED，LOCAL |
| 数据源 | 1，ENABLED |
| 中心库 | 110.42.227.83:3306/mes_base_prod |
| 业务库 | 110.42.227.83:3306/mes_base_prod_business |
| 开始业务版本 | V37 |
| 开始中心版本 | V39 |
| 开始工单 / 库存 / 库存动作 | 0 / 0 / 0 |

保存 `binding.txt`，迁移前以 mysqldump 对两库做一致性备份 `before.sql`。没有沿用本机同名库、替换工厂绑定、切换模式或创建新验收库。

既有 wm_inventory 是物料+仓库+批次+库位的受控余额来源；quantity/available_quantity 为 DECIMAL(20,6)，不含 factory_id。wm_stock_action 是不可变库存事实。MaterialIssue 是生产领料领域；ProductionReceipt 是生产交接事实；WarehouseAcceptance 是该交接的实际接收。它们均不适合改造成通用其他出入库凭证，因此新增最小头/行两表，继续复用原库存和事实表。

## C. 单据领域模型

新增 wm_stock_document、wm_stock_document_line。类型为 OTHER_RECEIPT / OTHER_ISSUE，状态仅 DRAFT→POSTED 或 DRAFT→CANCELLED。主单包含原因、备注、服务端审计、过账/作废人及时间、共享revision、成功过账原UUID及指纹；行包含确切库存身份、基本单位、正数量、实物依据及库存动作关联。

单号按既有 ProductionReceipt 的服务端UUID编号约定，复用 IdUtils 生成 SD-UUID，不接受客户端单号，也不把平台自动编码事务嵌入业务事务。类型与关联作废工单在创建后不可变。请求DTO不开放状态、操作者、过账时间、动作ID、目标余额或source。Controller 显式白名单转换，ID/revision/decimal 使用字符串协议。

草稿主单与明细分别保存，不宣称整单草稿原子保存。任一行增删改与主单编辑共享头锁/revision；过期版本拒绝。每单最多100行，单页最多100条，行查询有界；POSTED/CANCELLED不能编辑，主单没有 DELETE 接口。草稿作废不改变库存。

## D. LOCAL 库存 Owner 集成

StockDocumentController → StockDocumentService → LocalStockService.postDocument → 原 change 方法 → 原 wm_inventory / wm_stock_action。

新单据Service没有直接更新库存余额的SQL或Mapper调用。正式出库同时检查实存、可用数量，不允许负数；不足返回 STOCK_INSUFFICIENT，并说明整单未过账。每行校验库存ID、物料、仓库、批次、库位和基本单位完全匹配，启用状态有效。

没有库存行时，前端通过已有 IWmInventoryService 的受控创建入口建立零余额来源，再选择来源并正式过账；不会直接插入带期初余额的库存。原库存来源删除保护增加对单据明细的引用检查，不能删除草稿已经引用的零余额来源。

## E. 多明细事务、锁与幂等

正式命令为 `POST /api/wm/stock-documents/{id}/actions/post`，请求只含规范小写UUID及expectedRevision。单一 businessTransactionManager / READ_COMMITTED 事务覆盖头锁、行验证、全部库存变更、动作插入、行关联及POSTED状态；无每行独立事务。

普通单据锁头后按stockId、lineId确定顺序取得全部库存锁。关联作废工单时先锁原工单，再锁头及执行链，最后锁库存；原工单锁也串行化不同补入库单对同一剩余额度的认领。库存锁使用已有生成Mapper的同值UPDATE，不另建锁查询或单表Extra。

首行库存动作主键采用原过账UUID，其余动作UUID由原UUID与行ID确定生成；现有动作主键同时拒绝跨单复用同一原UUID。每条动作另存 operation_id、document_line_id；source_type为OTHER_RECEIPT/OTHER_ISSUE，source_id为正式主单ID，保留前后双余额、库存身份、操作人及原因。旧事实新增列保持NULL，不回写历史。

POSTED同UUID同指纹返回原事实；同UUID不同载荷、不同UUID重复过账均拒绝。成功原UUID重放先读取原事实，不重新按当前模式解释历史LOCAL动作。指纹包含单据ID、UUID、原expectedRevision和可信操作者，已提交内容由共享revision及只读终态冻结。

## F. 工单取消后人工入库

关联入口仅接受可信LOCAL且CANCELLED的工单。过账时产品必须等于原工单产品；明细必须有实际清点依据。额度严格采用：

`原生产交接净量 − 原仓储实际接收净量 − 已过账关联其他入库量`

查询原执行链、交接、实际接收及已有人工单据，均有上界并在工单/执行锁内核对。没有交接依据时额度为0，不能用计划量、累计报工或质量放行数量推定实物。无SN/容器身份，本轮不声称逐件实物追踪；操作人员仍须对所选批次/库位及清点依据负责。

真实样本：工单1、执行1、产品2、成品库存6。正常交接10、正常实际接收8；工单经正式API作废后，浏览器创建单据7，人工接收2。之后再补1返回 STOCK_DOCUMENT_BASIS_INVALID。产品不匹配、非作废工单、没有交接证据的作废工单分别被拒绝。未给旧事实补来源、改状态或伪造数量。

## G. 生产接收与普通入库隔离

正常链路仍为 ProductionReceipt→WarehouseAcceptance→LocalStockService.receiveProduct；人工链路为 StockDocument→LocalStockService.postDocument。新人工单据不创建ProductionReceipt、QualityRelease或WarehouseAcceptance。

人工入库前后逐表快照对比：43张业务表中，仅 wm_stock_document、wm_stock_document_line、wm_inventory、wm_stock_action 改变，其余39张完全相同，包括原Execution、路径、投入、报工、质量、生产交接及仓储实际接收事实。前后SHA256：

- before-manual：`d5d6c33583e31ea126906821089218017ba3d55cad11df983d2af9e9b38d8810`
- after-manual：`6be6093b740e37ef246be33da840d3c826bea414f03928c6421edd3ffa2f2406`

正常收发样本库存1仍为70；原生产原料库存5仍为80；成品库存6为10，其中生产实际接收8、人工其他入库2，两类来源分别可查。

## H. 权限与前端

中心V40登记以下6项，不自动赋权：business:wm:stock:document:{list,query,create,update,cancel,post}。query沿用项目详情动作命名；明细增删改均属于update，不额外开放通用删除权限；post独立于update。

用户随后明确授权新建仅限PROD的工厂验收角色并绑定admin。通过正式角色API创建角色101、key=test-stock-v22-prod，确切6项权限（菜单2400–2405），给用户100绑定，保留原平台角色100。SQL读回与role-result.json证明权限范围，没有把权限追加到原平台角色或其他用户。库存动作详情继续要求既有库存动作查询权限。

页面 `/stock/stockDocuments` 复用 MasterDetailDocument、DocumentFields、embedded/fixedValues CrudPage、MaterialSelect、WarehouseSelect、WorkOrderSelect、request及权限机制。类型/状态/单号筛选共用同一单据列表，提供物料、LOCAL仓库、确切库存来源、批次、库位、单位、数量、原因、作废、过账、关联工单及动作查询。

通用组件只增量支持动作权限显式映射、头部脏数据时明细只读、按显式版本字段刷新明细；默认调用方行为保留。新单据头有未保存修改时不能编辑明细，避免刷新覆盖未保存输入；过账UNKNOWN时冻结原UUID与载荷，不能另建意图或继续编辑。切厂、撤权、关闭和卸载失效迟到响应。发现的过账后动作显示滞后已改为按主单revision刷新。

真实浏览器完成关联工单选择、主单保存、明细选择及保存、库存影响确认、正式过账、只读显示、动作查询与库存70/10查询。最终1280、1440、1920视口均满足document.scrollWidth=clientWidth；表内允许横向滚动。截图位于本轮ui目录。最终验收标签控制台无error/warn；调试期间存在Element Plus resize通知，未通过屏蔽错误处理。

用户另明确要求关闭登录图形验证码。通过正式系统参数API设置 sys.account.captchaOnOff=false，并核实原8081及独立18081的/api/login-options均返回false；没有改密码、绕过认证或修改默认配置模板。配置变更保留生效。

## I. Schema、Codegen、Flyway

新增正式业务 [V38](../../mes-admin/src/main/resources/db/business/V38__local_stock_documents.sql)，中心 [V40](../../mes-admin/src/main/resources/db/system/V40__local_stock_document_permissions.sql)。在已核实并备份的PROD实际执行，业务V38、中心V40均通过正式Flyway机制migrate及validate；中心沿用FactoryFlyway注册历史Java迁移。没有repair、绕过迁移记录或重写旧SQL。

新增表/列中文COMMENT完整（缺注释列0），无factory_id，无新增业务UNIQUE/外键；只有主键及普通索引。库存动作原不可变触发器保留。Schema增量扩展现有动作表，没有第二套流水。

采用mes-crud-codegen Skill及 [V2.2配置](../development/stock-documents-v22-codegen.yaml)，从已迁移的真实PROD MySQL metadata生成完整persistence/backend/test：3 supported、0 rejected。完整基础Scaffold保存在target/stock-documents-v22-codegen；生产采用头/行及库存动作扩展的Entity/Example/Mapper/XML，业务Service与专用Controller承接状态机。未发布可直接写POSTED/动作事实的通用CRUD。生成Mapper扩展保留原sumReturns查询；手工维护的文件不恢复生成归属。

其他工厂业务库未在本轮迁移。后续部署新二进制至其他库前须执行正式V38；本轮不宣称SIMPLE_B已更新。

## J. 单元、HTTP、MySQL测试

| 证据层 | 结果与边界 |
|---|---|
| 后端全量 | `mvn -B -ntp verify`成功；729项、684通过、45条件跳过，0失败/错误。verify-final.log。跳过的既有真实库条件测试不算通过。 |
| 新H2 HTTP | StockDocumentHttpTest 7项通过，真实生成XML+Spring事务；包括生命周期、重放、不同载荷、无明细、版本、跨A/B数据库、权限、回滚注入、多行同库存、并发、ERP门禁、取消额度及生产事实保护。 |
| 原LOCAL回归 | LocalWarehouseHttpTest等旧领料/退料、质量、交接、实际接收、严格完工、来源隔离、工单作废及E10同步合同测试随verify通过。StockHttpTest引用保护Fixture已适配新表，没有删除原行为测试。 |
| 真实MySQL | 业务迁移/metadata、正式HTTP事务及并发；原库存动作UPDATE/DELETE均由真实触发器返回SQLSTATE45000，原事实未改。mysql-immutability.json。 |
| 前端 | 最终全量62文件/369项通过；Node22.23.1，test/build通过。字符串ID/decimal、UUID保留及迟到响应测试在内。构建只有既有大chunk提示。 |
| 打包 | `scripts/smoke-packaged.py` 10通过、0失败；构建不等于部署。 |
| Java格式 | 仅本轮修改/采用文件执行IDEA格式化，日志均有formatted完成记录。 |

首次全量验证发现StockReferenceRules新增引用依赖使旧仓储Fixture缺Mapper，已在原Fixture补齐真实生成Mapper及正式迁移片段；未弱化生产保护。中间一次HttpLoggingHttpTest出现chunked EOF，随后原测试随完整verify通过，未修改其生产代码或测试断言。

Java格式化后追加定向23项测试通过（业务枚举3、仓储6、原LOCAL7、新单据7），post-format-tests.log。

后端git diff --check通过；前端本轮改动文件通过。前端全仓检查仍能看到开始前即有的issue/mesMaterialIssue/index.vue第3行尾空格，原文件字节保持，不为本轮清理并行修改。该既有差异不冒充本轮新增问题。

## K. 真实API/UI闭环

本轮独立15173前端→18081后端，使用prod Profile与真实PROD中心/业务绑定；不替换用户5173/8081服务，不使用Mock充当真实验收。

| 样本/场景 | 真实结果 |
|---|---|
| 单据1 OTHER_RECEIPT | 库存1，0→100 |
| 单据2 OTHER_ISSUE | 同库存100→70 |
| 两笔原UUID重放 | 返回原事实，余额仍70 |
| 超量出库71 | STOCK_INSUFFICIENT，随后草稿正式作废 |
| 单据4两行，第二库存不足 | 全单拒绝，第一行未扣，动作关联全空，主单仍DRAFT |
| 同单并发过账 | 一次成功、一次409，独立库存10→3 |
| 与库存调整竞争 | 仅库存调整成功，独立库存80→30；单据不足被拒 |
| 与正式生产领料竞争 | 仅其他出库成功，独立库存80→20；生产领料失败，无透支 |
| 作废工单浏览器补入库 | 单据7，2件；成品8→10，原生产事实不变 |
| 再认领1、无交接证据、错误产品 | 均拒绝；测试草稿经正式入口作废 |
| 非法UUID、旧revision、同UUID不同payload、另一UUID重复过账 | 分别400/409，无余额变化 |
| 真实Factory/权限 | PROD绑定及角色精确读回；未授权Factory请求拒绝。真正两个物理业务库隔离由A/B隔离Fixture证明，本轮不声称完成第二个真实库验收。 |

所有API请求、原UUID和回执保存在api.jsonl/live-state.json，正常结果normal-result.json、人工入库manual-result.json、竞争races-result.json、负例negative-result.json；不会在结果未知时重新生成正式操作UUID。

## L. 数据一致性与历史保护

两库迁移前备份、人工接收前后逐表快照均保留。本轮新数据全部使用TEST_V22标签或TEST ONLY原因，未覆盖或清空用户数据；不存在将其他环境历史事实拷贝到PROD的操作。并发样本使用独立库存，不影响100−30=70主样本或生产8+2=10样本。

原角色保留，新增角色仅依用户授权。Factory模式、ERP绑定、SDK服务身份未更改。作废测试工单不自动逆向报工或退料；保留实际库存事实、失败草稿及已作废负例供复验，不删除已过账动作或生产历史。

## M. 未完成范围与部署边界

本轮不开发采购/销售/WMS全套、盘点、跨仓调拨、ERP Adapter、已过账整单逆向、工单作废自动退料、自动库存补偿或分布式事务。没有证明交接净量时的人工额度审批机制未建设，采用明确阻断。没有SN/容器逐件追踪。

真实ERP未调用、未验收；PROD始终LOCAL。SIMPLE_B连接不可达，未执行其V38。45项条件跳过不等于真实验收。原用户8081运行包未替换；新二进制/前端产物已构建，用户现有服务仍需按其运行流程加载新包后才能使用新API。最终后端产物为target/mes-base-0.1.0-SNAPSHOT.jar，SHA256为`1bccb75c88272d8722653b8d4981e6376b781e011ee0cd8e101b78749be4ee54`；最终打包后再次冒烟10/10通过。

独立验收后端PID54787（18081）及前端PID58782（15173）均已关闭，启动中使用的临时后端53945、前端54297也已停止。用户原后端40599（8081）、前端12465（5173）仍保持运行；cleanup.json提供端口核对证据。原8081通过正式Factory上下文再次读回库存70、成品10、原生产实际接收8及工单CANCELLED，见existing-service-readback.json。验证码关闭和用户授权的新角色配置按用户指令保留，不随验收服务退出撤销。

## N. 判定与回滚

**GO：LOCAL最小正式其他入库/出库闭环，及用户明确授权的PROD真实MySQL/API/UI验收。** 不是ERP发布、其他工厂部署或完整WMS验收结论。

代码回退只处理本轮差异，不覆盖开始时或执行期间出现的并行修改。已有V38/V40迁移及已过账事实不能通过删表、删流水或重写迁移回退；旧二进制可按原协议读取原字段，新增单据停止新增处理并保留历史。需要库存纠正时使用正式受控业务入口另行处理，不自动冲销本轮事实。原服务加载新包及其他工厂迁移属于后续部署动作。

## 2026-10-21 — 移除交接流程，统一入库单

### 当前业务契约（替代旧流程的新业务定义）

用户确认收敛为两个事实入口：生产结束由执行单元数量天地合及质量处置结束自动推进，再检查全部执行与工单数量；库存入库由正式入库单确认生效，并同事务成功写入库存动作及余额。入库不是仅指成品入库；沿用一套 StockDocumentService / 主子单据 / LocalStockService，普通物料入库无需生产执行，生产来源入库额外关联执行单元并检查其产出与可信放行。允许分批入库、生产完工后入库，生产状态不等待仓储。

沿用已发布的 OTHER_RECEIPT / OTHER_ISSUE 持久化编码兼容旧单据，对用户显示“入库单 / 出库单”，不建立第二套成品入库单表、余额所有者或专用交接状态。没有扩展为采购、销售等尚无证据的业务模块。

### 模型与实际调用链

- V44 为现有 wm_stock_document 增加可空 source_execution_id 与普通索引，生产来源创建后固定；与旧 source_workorder_id（已作废工单的历史人工补录）不能混用。生成器从指定 DEV 真 metadata 生成完整基础后端及测试到独立 target，再取 Entity/Example/XML 适配既有领域服务；保留原 Mapper 的行锁能力，未复制第二套 CRUD 或自动开放生成写入口。`.local/unified-inbound-20261021/codegen.json` 记录生成结果，私有连接不进入 Git。
- 入库单草稿/明细不改变库存；POST `/api/wm/stock-documents/{id}/actions/post` 在 WorkOrder→Execution→Document→Inventory 锁序内重新检查来源、运行模式、产品/单位/仓库/批次/库位和额度。累计本单全部明细后校验，不逐行独立放行超额；库存按稳定顺序锁定，整单状态、明细动作关联、库存动作和余额一起提交或回滚。原过账 UUID/版本/操作者重放规则继续生效。
- 生产来源额度 = min(可信放行,末道有效产出) − 累计已入库。累计值由 StockDocumentExtraMapper 跨表汇总已过账单据/明细/实际库存动作，并合并历史 mes_warehouse_acceptance 净额；旧交接量不会被当成已入库，也不会为其自动补库存。两个工厂独立库，所有查询/事务仍走固定 Factory 业务租约。旧来源、ERP 缺连接或模式异常不回退 LOCAL；真实 ERP 入库尚未实现，不将 LOCAL 的 POSTED 当成 ERP 生效证据。
- 作废工单历史补入库额度也扣除已经通过统一入库单过账的数量，避免“旧接收＋新入库＋历史补录”重复入库。原历史余量仍需实物依据，不自动用计划量或报工量创建库存。
- 原 LocalStockService 的原料 receipt 直写仅保留已成功原 UUID 的重放；新请求返回 STOCK_DOCUMENT_REQUIRED。期初、退料、盘盈亏和纠错仍按原权限和事实门禁处理，不作为另一套日常入库入口。

### 移除与历史保留

旧 ProductionReceiptService / WarehouseAcceptanceService 仅保留分页事实与原记录查询；旧交接创建/冲销、仓储接收/逆向、待交接/待接收实时摘要及 LocalStockService.receiveProduct 写链路已删除。删除未使用的 ERP 交接接收壳契约，以及旧仓储接收的隐藏生成 CRUD/Service/VO/Resource，保留历史 Entity/Mapper、Schema、引用保护及不可变事实。旧写接口不可再用，成功旧操作按原 UUID 读取事实核实结果。

生产完工的公开 Summary/Fact 不再携带交接进度字段；旧完工事实的 receipt_qty 历史列保留，新事实固定为0，accepted_qty 保存完工时累计实际入库快照，对外命名 inboundQty。不会因后续入库回填或重算旧完工事实。

前端新业务统一进入出入库单；生产闭环和库存操作面板均通过服务端授权菜单解析链接，支持自定义菜单父路径，无权限不造裸路由。生产来源使用现有执行选择器，允许 RUNNING / COMPLETED。旧流程仅留隐藏历史页面、筛选、按 UUID 核实和入库历史追溯，没有新增/逆向按钮或 POST API。UNKNOWN 意图不自动重建；通用过账及旧原料请求继续保留原 UUID 的核实能力。

### 真实迁移与数据边界

核实目标：中心库192.168.18.25:3306/mes_base_dev；TEST_DEV_PERF_20261019，LOCAL/ENABLED → 数据源9/revision2 → 同实例 mes_base_dev_perf_test。业务库由V43正式升级V44，Flyway校验44项通过。中心库原停在V40，经项目 FactoryFlyway 正式机制顺序执行已有V41权限登记与本次V42菜单退役，校验42项通过；不修改任何已发布checksum，不 repair、不跳过Java V34迁移。

业务迁移前备份现有1111张仓储单据，原1814条交接、1812条接收事实不改写。V44不回填生产来源，复核1111张旧单据 source_execution_id 均空。菜单保留原ID与角色关联，关闭旧4项写权限、隐藏旧导航并更名统一入口；没有自动为现有角色补授权。备份和小型验证日志保存在 `.local/unified-inbound-20261021/`，不新增验收JAR副本或全库备份。

新增两条跨表汇总 SELECT 已保存并在上述目标库只读执行/EXPLAIN；执行查询使用 idx_stock_document_execution / idx_acceptance_execution，工单汇总使用执行关联索引及对应单据/动作关联。Schema与SQL真实执行不代表新Java已部署，也不代表已通过真实入库命令变更库存。本轮没有自行重启用户服务。

### 验证与部署边界

旧两步写流程测试已移除或转为明确历史fixture；保护数量/来源/权限/并发/事务的断言迁往统一入库入口，保留原来源矩阵、库存所有者、质量晚纠正、作废工单及生产自动完工测试。新增覆盖普通物料入库、生产入库、草稿不动库存、过账幂等、分批额度、同执行多单并发、整单后行失败回滚及原UUID重试、旧实际入库扣减、作废历史补录防重复、无放行拒绝、原料直写拒绝和旧写入口关闭。

真实页面复验遇到权限边界：平台管理员role100及其他现有角色未绑定仓储单权限，页面不应绕过服务端授权。是否扩展该角色通用出入库能力已单独请求用户授权；未收到批准前不新增角色菜单关联。前端测试/构建和H2/MockMvc行为证据与真实服务/页面部署证据分别记录。

回退须先停用新单据过账入口并保留生产来源校验；不能让忽略 source_execution_id 的旧JAR继续处理新生产入库草稿。保留已过账不可变事实，不 DROP 历史表/新来源列，不以回滚代码恢复交接或清空库存。

最终验证回执：`verify-delivery.log` 的全量标准 `mvn -B -ntp verify` 通过（admin统计564项、47项按既有外部条件门禁跳过）；随后精简历史接收状态查询并补齐父工单状态/来源/模式的可入库提示，`verify-contract-final.log` 对最终实现42项定向verify通过并重新打包。该复验仍覆盖实际过账时的锁内校验，不将只读额度提示当成授权。前端最终67文件404项全部通过（`/tmp/unified-inbound-ui-delivery.log`），最终构建通过；打包隔离smoke 10项通过。JAR检查确认旧生成接收写Controller/Service和未使用的ERP交接接收契约均不存在，新统一汇总Mapper与XML已打包。前后端diff whitespace检查通过。

尚未取得：用户现有服务重新加载新Java后的真实命令/API及三档页面验收。现有服务没有被Agent重启；角色100尚未取得新增的通用仓储单权限，授权问题待用户选择，不能把上述H2/构建/打包证据写成该账号真实入库已成功。部署后入口使用服务端菜单配置（当前中心菜单为“物料库存 / 出入库单”），不能以源码目录wm拼猜页面URL。

2026-10-21 用户随后明确授权平台管理员角色100。核实中心目标仍为192.168.18.25:3306/mes_base_dev、角色为启用的platform-admin后，先备份该角色原菜单关联，再执行幂等授权SQL，新增现有菜单2636—2641的list/query/create/update/cancel/post共6项权限；关联总数由384增至390，未删除原授权。数据库复核6项均启用，旧交接/仓储接收4项写权限仍停用。SQL、角色备份和执行回执分别为`.local/unified-inbound-20261021/grant-role100.sql`、`role100-menu-before.sql`和`grant-role100.log`。该授权已完成；用户刷新菜单后可使用“物料库存 / 出入库单”。新Java仍需用户重启后端加载，尚未将该授权复核视为真实入库命令验收。


## 2026-10-23：当前库存入口、类型与ERP边界复核（只读）

### 范围与能力矩阵

读取当前工作树 StockDocumentController/Service、StockDocumentType、LocalStockService、StockReferenceRules、WmInventory/WarehouseServiceImpl、QualityDispositionService、BusinessSourceRules、WarehouseIntegration、MaterialIssue提交/核实/撤销、E10同步读取与writer，以及库存单/动作/来源选择、质量处置前端及对应API。阅读StockDocumentHttpTest、UnifiedInboundHttpTest、LocalWarehouseMySqlTest和前端stockDocuments测试的相关源码，没有运行它们。没有访问数据库、ERP、业务接口或浏览器；没有读凭证。未完整覆盖所有同步writer、质量数量、BOM和平台安全，详见跨模块台账。

| 类型 | 后端草稿保存 | 后端审核/来源 | 当前正式入口与执行者 |
| --- | --- | --- | --- |
| PURCHASE_RECEIPT | 可 | 禁止，采购来源未接通 | API草稿；当前页面类型槽无此项 |
| PRODUCTION_RECEIPT | 可，必须Execution | 可，当前生产/质量放行/净入库额度 | 生产闭环深链和库存API→LocalStockService；普通页面有类型断点 |
| OUTSOURCE_RECEIPT | 可 | 禁止，委外来源未接通 | API草稿，页面无此项 |
| MATERIAL_RETURN_RECEIPT | 可 | 禁止，该单据来源未接通 | API草稿；另有未开工LOCAL原领料退料命令 |
| REWORK_RECEIPT | 可 | 禁止，返工任务/检验来源未接通 | API草稿，页面无此项；不能冒充不良入库 |
| SALES_RETURN_RECEIPT | 可 | 禁止，销售退货来源未接通 | API草稿，页面无此项 |
| DEFECT_RECEIPT | 普通create禁止，质量用例内部可创建 | 正式TRANSFER_WAREHOUSE事件/版本/数量匹配才可审核 | 不良申报→处置→不良仓→正式单据/明细/审核→LocalStockService |
| SCRAP_RECEIPT | 同上 | 同上，目标SCRAP仓 | 同上，已决定报废实物入仓，不等于最终核销 |
| OTHER_RECEIPT | 可 | 可，原因/实物依据；可选合法作废工单依据 | 统一库存单页面→LocalStockService |
| OTHER_ISSUE | 可 | 可，一般发出原因/实物依据 | 统一库存单页面→LocalStockService；不等同质量最终核销闭环 |

证据：`mes-business/src/main/java/dev/mesbase/business/wm/domain/StockDocumentType.java:9–18`、`wm/service/StockDocumentService.java:147–175,254–305`。以下短路径从`dev/mesbase/business/`起；UI表示相邻前端。

**已有正式实现：** LOCAL零余额来源、受控期初/调整/亏损、扫码领料扣库及原净额撤销、未开工受控退料、草稿/明细/审核、生产及质量来源校验、同事务余额+不可变动作、分页动作读取。**部分实现：** 追溯/对账事实和查询已有，未证明自动全账对账工具；生产入库后端可达而页面契约有缺陷；E10读取/同步准备已有。**明确功能缺口：** 已审核统一单据撤销/冲销、入质量仓后的专用返工发出/质量跨仓/最终核销及五类未接通来源。**待验证：** 当前Schema/绑定、真实成功样本、并发和当前测试/运行版本。

### 实际调用链与事务保护

- UI `views/wm/stockDocuments/index.vue:56–62` → `api/wm/stockDocuments.ts:29–39,49–62` → `wm/controller/StockDocumentController.java:62–103` → `StockDocumentService.create/putLine/approve` → `LocalStockService.postDocument:246–273` → `change:301–350` → Inventory update + StockAction insert。StockDocumentService:67为具名业务事务/READ_COMMITTED/异常回滚。审核锁主单/生产链，307–311按库存/明细排序锁库存，313–331记录动作关联、POSTED、审核人/时间及原UUID/hash；库存改变385–389拒负余额、负可用和可用超过实存，更新/流水检查受影响行数。
- 生产依据：StockDocumentActions:18 → execution basis → StockDocumentService:335–351 → QualityReleaseService.state + StockDocumentExtraMapper.netProductionInbound。XML:4–15同时计当前POSTED正式单据及原warehouse_acceptance事实，新单据须有关联库存动作。历史只读证据不授权恢复旧接收写流程。
- 质量转仓：UI QualityTreatmentPanel:84–87,124–127 → disposition → `quality/service/QualityDispositionService.java:90–161` → active/独立/版本/余额校验 →126正式批次→127引用锁→128–132限定DEFECTIVE/SCRAP→正式createQualityReceipt/putLine→recordDisposition→approve。全部同LOCAL事务；不是枚举或测试替身。当前真实可追溯样本仍待验，不能用旧样本失败当作必须增加历史兼容入口。
- 退料：UI LocalStockPanel:44–50 → localStock API → LocalStockService:114–127,153–178，只允许原LOCAL、stockApplied、未开工且无报工/质量/receipt事实，累计退量不超过原领料。154行明确不改产品投入，不能把材料量直接减产品量；减少投入走`issue/service/MaterialIssueVoidService.java:103–116,147–149`与InputFacts.voidConfirmed。该保守政策在旧仓储专题58–62行已有明确记录，本轮当前源码亦吻合；“全退材料但产品额度仍在”作为首厂场景限制/口径核对，不直接改判为回归缺陷，不宣称已支持开工后余料退库。

### 当前问题、影响与验收方向

1. **PLAN-01确定页面契约断点：** UI库存单58行只允许OTHER入/出，59行却只在OTHER入库可选Execution；API原样发类型；Service167–170只接受PRODUCTION_RECEIPT+Execution，普通菜单路径保存必被拒。完工深链预置正确类型（UI32–34；ProductionClosurePanel:42），不据此断言深链必失败。现有前端测试38行只直接传正确枚举，不覆盖真实页面槽。修正已有类型目录和来源联动，保留后端门禁；验收普通菜单/深链、类型回显/切换、草稿无库存动作、审核一次记账、无来源类型拒绝。优先于新返工/处置能力。
2. **QA-02待复现并发风险：** putLine215–240/validateIdentity404–418普通读库存后插明细；WmInventoryServiceImpl202–219持库存锁查引用后删除；StockReferenceRules67–75虽然查明细，不能阻止尚未提交引用。V38明细表仅索引、未见后续外键补护（检索V8/V30/V34/V38/V44/V48及后续业务迁移）。可能留下悬空草稿，审核会再锁/拒绝，不扩大成已过账负库存。新增/换源与删除需共同锁序，真实MySQL双事务验收，保留无引用正常删除；本轮未复现。
3. **专用质量库存生命周期为新功能缺口：** `StockDocumentService.cancel:203–213`只作废草稿，Controller37–122无posted reverse；LocalStockService.reverse222–235是原领料逆向，不是质量入库冲销。检查wm/quality服务及前端warehouse范围，检索reversal/reverse/TRANSFER_WAREHOUSE/stockDocument/核销/返工/对账，未找到质量仓库存→返工、原质量来源贯穿跨仓、正式最终处置服务。一般OTHER_ISSUE能扣账不等于原质量可处置净量闭环。按首厂实际需要拆独立业务服务，复用统一Owner；跨仓扣增同事务、原来源不丢、不得重复投入/核销。库存→返工依赖返工身份规则；不良→报废→最终出库无需等待任意上游返工全部完成。

### LOCAL/EXTERNAL与真实ERP的区分

`production/service/BusinessSourceRules.java:70–84,94–104,113–133,187–215`校验持久化来源、FactoryIntegration模式、同来源仓库及EXTERNAL映射/provider/binding/revision。WmWarehouseServiceImpl133–136服务端写LOCAL来源，155–168普通编辑拒EXTERNAL；WmInventoryServiceImpl141–167仅建LOCAL零余额、179–195禁止改身份/余额、288–309同步限定EXTERNAL并保护事实引用。没有根据当前模式重写旧来源。

`integration/warehouse/WarehouseIntegration.java:27–37`读取受管快照，40–58历史撤销按原冻结来源/provider/binding，112–115必须唯一匹配adapter，否则失败关闭。全仓检索implements WarehouseErpAdapter：main仅LocalWarehouseErpService；Vendor/TestAdapter在test。**真实ERP库存命令尚未交付**，但不能因此说ERP全无实现：`mes-admin/.../integration/e10/DigiwinE10SyncService.java:55–75,82–145,167–189`已有真实Query/Read、部署契约校验、有界全量扫描、唯一身份核对和失败关闭，DigiwinE10SyncWriter:29将已完成远端读取的候选交给本地可信同步。真实目标/账套/环境契约本轮未验。

UNKNOWN链为`issue/service/MaterialIssueSubmissionService.java:105–144,200–251`先冻结意图、事务外外部调用、落结果；异常或未确认库存生效保留UNKNOWN，重复提交不重建；`MaterialIssueErpService.java:110–134`按原快照查询后重新锁记录及版本核实。当前可证明“人触发原身份查询核实”，未找到独立人工录入调查依据并裁定结果的DTO/入口（issue包检索人工/manual/evidence/basis/verify/resolve），不可宣传通用人工裁定系统已交付。V1人工恢复边界继续保留，不排自动跨系统补偿或新增ERP JDBC。

### 本轮测试证据与计划修正

只阅读测试源码：StockDocumentHttpTest:57–100/129–146/187–232/262–283（收发、幂等、回滚、并发审核、来源拒绝）；UnifiedInboundHttpTest:61–82/99–135/175–225（草稿零动作、额度/并发、整体回滚、旧写口退役）；LocalWarehouseMySqlTest相关真实库测试代码及UI adapter测试。**未运行，历史通过不是当前通过**，没发现引用创建对删除的双事务覆盖。

A提前纳入PLAN-01；D拆库存二次处置和返工发出，取消全部硬依赖C；五类来源、采购销售/委外和ERP库存写能力后置；F仍要求真实交付/恢复证据。没有恢复旧接收入口，没有实施业务变更。


## 2026-10-23 实施追加：正式入库审核驱动Execution与工单收尾

本节取代此前“生产完工独立于入库”“自动报工完工/扫描”和本日只读审查的相冲突实施建议，历史正文不改写。当前业务决定以next-stage-plan顶部用户确认节为准。

- **唯一自动触发：** StockDocumentService.approve在库存动作及POSTED保存后，同业务事务调用ProductionCompletionService.afterInboundApproved。正常来源直接取sourceExecutionId，质量来源从原TRANSFER事件取可信Execution，均先父工单→全部Execution按ID→单据/库存加锁。质量在写事件后内部approve，收尾能看见本事务质量与库存事实。
- **数量与状态：** 沿唯一ProductionQuantityService取I、末道G、各实际工序净不良D；formalProductionInbound只计POSTED生产单据并要求明细与库存动作的执行/操作UUID、数量/可用增量、前后余额、物料/仓库/单位/批次/库位/操作者/hash、唯一关联及无逆向完全一致。invalidProductionInboundLines显式阻止无证明明细。I=G+D、R=G、质量逐申报入仓及其余门禁通过才写FULLY_RECEIVED与既有completion事实；历史COMPLETED不转义、不回填。工单进一步校验全部执行、Σ(R+D)=P、分配/未决/未归属投入，Q不再加一次。
- **合法审核与条件不足分开：** 正常数量不足、计划不足/超额、未决领料等返回实际未收尾原因，合法库存不回滚。基础设施、来源、授权及持久化错误传播并按同事务处理；持久化收尾失败会回滚本次审核及全部库存动作，不留下“已成功但无可靠补漏”的异步窗口。
- **保留质量放行：** 已核实不良已从G扣除，待入仓不再阻挡好品正式放行；未核实继续阻挡。正常入库仍受当前正式放行额度/版本、产品/来源及历史实际入库额度限制。旧netProductionInbound继续扣已存在历史接收，避免因新R口径而恢复旧对象的入库额度；新FULLY收尾仅接受新正式证明。
- **恢复：** 入库已合法提交但普通阻断后来解除，通过AUTHORIZED `/api/production/executions/{id}/actions/recheck-receipt`。使用既有complete权限，锁同链并从latestInboundAuditId取得原可信正常/质量审核UUID，复用唯一收尾条件，无已审事实则拒绝收尾；不重复库存/放行、不重开旧终态、不启动扫描。质量候选与正常候选分别复用同一正式证明SQL，不另设弱校验。原审核重放仍只核原内容，不靠重建UUID恢复。
- **前端：** 统一类型目录及PRODUCTION_RECEIPT/Execution联动，普通菜单/深链均可形成正确契约；质量专用类型禁止手填create。不良模块不新增调拨、处置出库或返工；收尾面板显示待入库/部分入库/完全入库（Q先入也为部分）、执行和工单各自原因及受控复查，不把单次审核成功说成工单已完结。

关键实现：`production/service/ProductionCompletionService`（summary/lockInboundChain/afterInboundApproved/recheckReceipt）、`WorkOrderCompletionService`、`wm/service/StockDocumentService.approve`、`wm/dao/StockDocumentExtraMapper.xml`（formalProductionInbound/invalidProductionInboundLines/latestInboundAuditId）；质量完整证明由`ProductionDefectListExtraMapper.receiptProofRows`复用。后端路径均从mes-business的dev/mesbase/business起。

测试覆盖重写到当前正式审核入口：ProductionCompletionHttpTest的60/40、正常90/上游不良10的两种审核先后、全不良/零正常单、多执行、计划不足/超额、投入未闭合、未决领料、并发同UUID/不同审核、持久化失败回滚及原UUID安全重试、无证据/权限拒绝、两类入库后的受控复查；UnifiedInboundHttpTest保留草稿零动作、正式放行额度、旧实际入库扣额及多行整体回滚。最终运行结果与历史证据区分，统一见next-stage-plan第七节验证回执。

无Schema/Flyway新增，无真实业务库写入，无历史终态/事实转换。本轮初次误选SIMPLE_B的只读检查因V48缺字段失败，未写入该库。用户随后明确默认测试库mes_base_dev_perf_test，重新核实TEST_DEV_PERF_20261019 / datasource9 revision2 / LOCAL及实际DATABASE()后，当前正常/质量证明和进度Mapper真实只读执行通过（1项测试）。两仓AGENTS已写入固定默认测试库规则。只读通过不代表真实收尾写入、并发或部署验收。用户旧服务进程未重启，旧进程中的任务不会因源码删除立即消失；需用户重启加载新构建后复验。旧包不认识新FULLY_RECEIVED，后续已有新事实时不能靠改回COMPLETED/删事实回退。

最终本轮验证：完整verify886项（837执行通过、49条件跳过）、0失败/0错误并打包；指定perf库后的定向32项全部通过（31 H2 + 1真实MySQL只读）。UI76文件454项及build通过，Java36文件格式与两仓差异检查通过。此前失败及测试口径修正在计划第七节保留，不将真实SQL只读成功冒充真实入库写入验收。

## 2026-10-23 重启后真实入库验收追加

本次重新核实perf库、Factory TEST_DEV_PERF_20261019/DS9 revision2/LOCAL/业务版本51。独立TEST_INBOUND_1023_103838对象通过正式HTTP准备原料并扫码投产；正常工单1916/Execution12368通过真实菜单审核60（单据1113）后为部分入库，经Execution面板深链新建并审核40（1122）后FULLY_RECEIVED、父COMPLETED。两次草稿分别核对余额0/60，实际落库生产类型与Execution来源正确。另1917/12369（R90先）、1918/12370（Q10先）、1919/12371（全不良Q100、零正常单）均由最后合法审核同步收尾，R+D贡献100，Q不再加一次。

7张生产/质量已审单据原UUID各重复至少2次，3笔质量入仓命令重放，库存动作/完成事实及余额不增；同UUID改内容409。新增11单据、11明细、15库存动作，与4原料入库、4领料、4正常收货、3质量收货一致；每执行completion唯一。写前后13表338831条既有记录逐主键摘要一致、无删除，范围外不作全库不变保证。独立测试事实保留，无直接SQL写库存/状态，无Schema迁移或服务启停。

真实浏览器已观察部分入库阻断、深链预填及完全入库，工作台四单贡献100/差额0。本次未进行真实并发/故障注入，不能据此扩大第七节已有Mock/H2结论。详细对象、请求、只读备份与截图路径及质量申报权限边界见next-stage-plan第八节；历史“未真实写入”是前一实施阶段快照，本节覆盖本次已完成的真实场景。
