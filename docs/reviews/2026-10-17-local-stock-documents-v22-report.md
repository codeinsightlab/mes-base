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
