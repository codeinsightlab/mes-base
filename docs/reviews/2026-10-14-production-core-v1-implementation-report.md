# Production Core V1 Implementation Report

2026-10-14。实施输入为用户最终收口任务、当前源码、生产主链只读审查及 ERP Integration ADR。当前完成范围到标准领料驱动的 RUNNING；验证结论见 [Verification Report](2026-10-14-production-core-v1-verification-report.md)，页面复验见 [User Acceptance Guide](../development/production-core-v1-acceptance.md)。旧审查的缺口描述保留其时点，不作为当前实现状态。

## 最终模型与迁移

WorkOrder 保留生产需求；ProductionExecution 是唯一活跃执行身份，独立选择适用的已发布 Routing 版本。工单默认版本可以与 Execution 实际版本不同。ProductionBatch / BatchRouteNode 保持原只读入口，不自动转换、不新增历史写入。

正式业务迁移 `mes-admin/src/main/resources/db/business/V23__production_core_v1.sql` 已在 SIMPLE_B 指定业务库执行。没有改写 V1–V22，没有新增工厂字段、外键或业务唯一约束。

| 模型 | 字段与约束 | 业务规则 |
| --- | --- | --- |
| mes_execution_path | id 使用 Execution 主键；routing_id、path_status、revision、selected_at、locked_at | 主键保证一个 Execution 一条当前路径；SELECTED 与 LOCKED 分开；首个可信确认永久锁定 |
| mes_execution_path_node | id、execution_id、sequence_no、routing_node_id；普通顺序索引 | 只保存原版本节点引用与顺序，不复制 DAG；业务事务控制同一执行的节点更新 |
| mes_material_issue_submission | id、issue_id、path_revision、command_json、status、created_at、finished_at；普通 issue 索引 | 尝试主键派生稳定操作身份；完整通用命令不可变保存；历史尝试不删除 |
| mes_material_issue | 新增 nullable submission_id，status 扩为 varchar(24) | 旧数据为空引用；核心表不保存外部单据身份 |

表与所有新增字段均有中文 COMMENT。真实 metadata 经 `.agents/skills/mes-crud-codegen/SKILL.md` 和 `scripts/mes-business-codegen` 生成三张新表及领料新字段的 persistence + backend + test 审查产物。配置为 `docs/development/production-core-v1-codegen.yaml`，报告位于 `.local/production-core-v1-evidence/codegen-report.json`。采用生成 Mapper/XML/Example 和基础后端；路径、提交尝试的普通写方法明确 FORBIDDEN，不发布原始生成 CRUD Controller。手改生成文件移除归属标记；总生成配置排除三张受控表，避免后续全量生成重新开放写入口。新表前端使用业务组件，没有独立单表 CRUD 页面。

## 正式领料与 ERP 调用

入口：`POST /api/issue/mes-material-issue/{id}/actions/submit` → MaterialIssueSubmissionService → WarehouseIntegration → WarehouseErpAdapter。保存主单和明细仍是分别保存，不宣称整单原子草稿保存。

1. 本地短事务：按共享锁序固定需求、执行、单据；要求已下达工单、READY Execution、引用一致、完整路径、1–100 条有效明细、无禁止操作；冻结命令、生成 `ISSUE-SUBMIT-{attemptId}`、进入 SUBMITTING，提交事务。
2. 事务外：按已冻结 mode/provider/binding 选择仓储领域实现；传完整 MaterialIssueSubmitCommand（单据/工单/执行身份、产品、版本、路径修订及节点顺序、每条物料/数量/批次/单位/仓库）。测试断言此时无数据库事务。厂商转换、内部提交/审核编排仍由未来厂商 Service 负责。
3. 本地短事务：重新获取同链锁，核对当前 attempt、Extra operationVersion、路径修订；Extra 结果、单据状态、尝试状态和路径锁定同事务落地。任何本地失败回滚本阶段，可核实原操作恢复，禁止盲目再创建。

| 结果 | MES / 路径 | 后续入口 |
| --- | --- | --- |
| SUCCEEDED + LOCAL_ONLY | CONFIRMED，尝试 SUCCEEDED，路径 LOCKED；Extra 无外部主键/单号、externalEffect=NONE | 显示“本地模式，领料已确认”；满足其他门禁可开工 |
| SUCCEEDED + CONNECTED | 确认厂商领域操作完成，保存 Extra 身份，CONFIRMED + LOCKED | 仅契约/TEST ONLY Adapter 验证，未真实联调 |
| REJECTED | SUBMIT_REJECTED，不锁定新的路径，保留尝试与必要外部身份 | 明确拒绝且无外部身份时才允许 actions/reopen-rejected；下次新尝试，保留旧历史 |
| UNKNOWN / 调用异常 | SUBMIT_UNKNOWN，保留稳定操作身份，不推进路径或开工 | 查询与核实原操作；NOT_FOUND 也不允许重新创建 |
| 已有当前尝试重复提交 | 返回当前事实 | 不生成新 operationKey，不再外部 create |

查询仍为只读 GET erp-status；核实仍有独立权限。CONNECTED 只有 FOUND/EFFECTIVE 和可信外部身份才恢复确认；UNKNOWN、NOT_FOUND、待审核均不推进。迟到结果校验操作版本，不能覆盖更新的核实结果。LOCAL_ONLY 遗留 SUBMITTING 可以按已持久化本地意图完成本地恢复，不伪造外部单据。诊断消息安全化，不输出凭证或厂商原始异常。

CONNECTED 缺 mode/provider/binding 或可用 Adapter 时明确失败，不能退回本地模式。删除旧单头式 MaterialIssueCreateCommand，关联测试改为完整命令。没有真实厂商实现或 Gateway。

## 路径与开工

ExecutionPathValidator 检查：节点唯一、属于确切版本、根节点起点、汇节点终点、相邻有边、全部关键节点必经；允许不同节点复用同一工序。最多 100 个选定节点。选择接口必须带 revision，拒绝旧修订、非 READY、LOCKED、已有提交/作废阻断。锁定保持第一次 locked_at，后续多单确认幂等；作废不解锁。

专用入口 `POST /api/production/executions/{id}/actions/start` 检查：已下达工单、当前有效已发布版本、READY 或幂等 RUNNING、合法 LOCKED 路径、至少一张带正式提交引用的 CONFIRMED 领料、可信 Extra 事实、无 SUBMITTING/SUBMIT_UNKNOWN/VOID_PENDING/VOID_UNKNOWN。关联领料查询最多 1000 张，超过明确拒绝，不截断后宣称门禁成立。重复开工返回已保存状态及相同 startTime；首次返回重新读取的数据库时间，消除 DATETIME 精度差异。

普通 Create 仅 READY 且无起止事实；普通 Update 不允许改变 status/startTime/endTime。产品/工单关联固定；实际工艺只可在无路径和领料事实的 READY 阶段订正。没有 COMPLETED 命令，也不创造完工事实。开工门禁当前服务于标准领料驱动模式，未来其他生产模式须以明确需求扩展。

## 共享锁与历史兼容

ProductionChainLocks 统一 WorkOrder → Execution → MaterialIssue → Extra/Path。多对象按父对象 ID 排序，单据重关联提前同时锁旧、新父对象，之后复核关联；不存在先锁单据再反向获取执行的入口。路径选择、提交准备/结果、核实、作废、Execution 编辑共享父锁。开工持有 Execution 父锁，阻断同时作废；已 RUNNING 作废保守拒绝。外部调用在两次短事务之间，不持有行锁。

历史 execution_id=null 的领料继续读取、查询、既有维护和作废；标准提交要求 Execution，兼容 CRUD 不建立正式提交或路径锁。历史已 RUNNING/COMPLETED 的无路径记录不自动补事实；新 start 必须满足新门禁。旧批次 20 条和路径节点 48 条验收前后保持不变。

新路径查看使用现有 execution:query；选路、开工复用 execution:update；领料提交/重新打开复用 issue:update；ERP 查询和核实继续各自独立权限。均为 AUTHORIZED，后端校验 Scope 与资源。未新建菜单、角色或授予权限；中心库既有失败 Flyway V17 未在本轮 repair。业务 ID 在 HTTP 转为字符串，数量保留 decimal；Service 不依赖 HTTP VO。

## 页面与未实现边界

复用工单详情、ProductionExecutionList、MasterDetailDocument/DocumentFields/embedded CrudPage。加入适用版本选择、顺序路径选择与工序快照名称、保存后领料提交、本地确认提示、待核实入口、路径锁定只读及开工。历史缺工序名称展示“历史工序名称未记录”，不冒用现主数据名称。操作忙态和 generation 校验防重复点击、撤权/切厂/迟到响应清理。

未实现报工、产出汇总、完工、生产入库、QC/WIP、库存核算、真实 ERP、自动重试/Saga/MQ、复杂解锁/生产回滚、旧批次迁移。任务 Agent 未提交、推送、部署、修改参考项目或操作用户服务生命周期。工作区并行日志任务与外部提交不归为本轮；实际文件清单见 [manifest](2026-10-14-production-core-v1-files.txt)。


## 2026-10-16 单材料扫码投产与生产数量模型 V1

### A. 本轮范围、契约与修改文件

用户追加决定：不实现真实厂商 ERP，只预留适配接口；配置支持独立 MES 和 ERP 模式，独立 MES 必须能生成真实业务数据。此决定覆盖原需求中的本轮真实 ERP 联调要求，不把 LOCAL_ONLY 或测试 Adapter 记为真实 ERP 成功。

本轮复用 ProductionExecution、ExecutionPath、MaterialIssue Submission/Extra、统一数量服务及不可变报工/质量/交接事实。未恢复 Batch/BatchRouteNode，未实现多材料装配、质检放行或 ERP 入库。

核心文件（相对各自仓库根目录）：

- 库存/工单查询与计算：`mes-business/src/main/java/dev/mesbase/business/integration/production/{ProductionSupply,ProductionErpAdapter}.java`、`production/service/ProductionInputSupplyService.java`。
- 投产用例与接口：同包 `production/service/ProductionInputService.java`、`production/controller/ProductionInputController.java`。
- 身份：`production/domain/ProductionScanCode.java`、`production/service/ProductionIdentityService.java`、`production/controller/vo/ProductionIdentityVo.java`；新增 MesProductionScanIdentity Entity/Example/Mapper/XML。
- 领料贡献：`issue/service/{MaterialIssueInputFacts,MaterialIssueSubmissionService,MaterialIssueVoidService}.java`、`integration/warehouse/MaterialIssueSubmitCommand.java`；新增 MesMaterialIssueInput Entity/Example/Mapper/XML。
- 数量与生命周期：`production/service/{ProductionQuantityService,ProductionExecutionRules,ExecutionPathService}.java`、`production/dao/ProductionQuantityMapper.java`及XML、`production/domain/{ProductionInputBalance,ProductionReportModels}.java`、`production/controller/vo/ProductionReportViews.java`、`mes-common/.../ErrorCode.java`。
- 库存批次：`wm` 下 WmInventory Entity/Example/XML、Create/UpdateCommand、Controller/VO、ServiceImpl、ExternalWmInventory。
- 正式迁移：business V29/V30/V31；system V32/V33。配置说明 `docs/development/production-input.md`、生成配置 `production-input-codegen.yaml`。
- 前端新增 `src/api/production/productionInput.ts`、`src/views/production/productionInput/index.vue`、`src/menus/production/productionInput.json`、`tests/productionInput.test.ts`。
- 前端适配 productionIdentity/productionReport/wmInventory API、ExecutionIdentityLookup、ProductionExecutionSelect、ProductionReportManager；生产数量、正式报工、质量不良、交接与库存页面。
- 定向测试新增 ProductionInputRulesTest、ProductionSupplyContractTest、ProductionInputHttpTest、ProductionInputMySqlTest、ProductionInputTestData；适配既有 MaterialIssue/Report/Reporting/Quality/Receipt/Inventory 夹具和回归，不给历史生产数据自动补投入。

两张新表均先通过真实 metadata 使用 mes-crud-codegen 生成完整基础后端和测试，保留 Entity/Example/Mapper/XML；冻结事实和永久绑定不允许通用增删改，移除会绕过业务的生成 CRUD 产物及对应无效测试。没有增加通用 Facade 或第二套 Mapper。

### B. 库存与工单筛选

`LOCAL_ONLY` 从当前工厂业务库读取启用材料、启用单位、启用仓库、明确批次库存及 RELEASED 工单。仓库/库位以明确库存主键选择，保留读取时间；同批多来源不取第一条。工单还必须有管理员明确配置的本地材料需求、工作中心与允许仓库；无配置不进入候选。单候选自动选，多候选由用户选。

本地 T 是显式工单用料配置，不是自动猜 BOM 或默认1:1。当前没有本地 BOM 维护界面，配置需要随用户服务加载；上线业务须补齐各工单真实依据。查询上限100库存来源及100张已下达工单，超限拒绝而非静默截断。

`CONNECTED` 复用现有仓储模式配置，必须同时具备 ProductionErpAdapter 查询与 WarehouseErpAdapter 正式领料能力；缺实现明确失败，不自动降级本地。未来供应商实现负责其可信组织、状态和需求来源；本轮没有厂商实现、外部数据库连接或真实 ERP 写操作。

### C. 自动用料

`issueQty = ROUND_HALF_UP(T × outputQty / P, 6)`，全部使用 BigDecimal。P、T、outputQty正数；同材料需求行只在单位一致时汇总，多种必需材料明确拒绝。不重复叠加损耗，无隐含单位换算。服务端冻结材料/产出单位、库存来源与计算依据；提交重算 calculationKey，不允许前端传入领料数量覆盖计算。

### D. 永久身份

规范码 `MES1.base64url(UTF8产品).base64url(UTF8材料).base64url(UTF8批次)`，无 padding，严格 UTF8与规范重编码校验。保留大小写、前导零和原始 Unicode，不 trim/lowercase/截断。

完整规范码 SHA256 为绑定表主键，读取再次核对原码；字符列使用二进制比较。工厂以业务分库隔离，无业务 factory_id。终态不释放绑定，不同工单/确切工艺版本冲突拒绝。旧 `材料#批次` 仅两段精确解析，唯一绑定才可返回，歧义拒绝。EXE 原生码继续支持。

### E. 首次、再次与 RUNNING

准备事务使用现有数据库锁，原子创建首次 READY Execution、永久绑定、已选路径、领料主子单、冻结投入及提交意图；事务结束后才执行确认。后续合法动作复用 Execution，READY/RUNNING均支持；RUNNING保留原锁定工艺路径、开始时间和 targetQty。COMPLETED/CANCELLED禁止追加，不自动重绑。

扫码/预览/计算 GET均不创建事实。补料类型保留零投产语义，因无受控补料计算来源，当前明确拒绝 MATERIAL_SUPPLEMENT。

### F. 投产额度持久化

采用方案A：与每张领料紧密关联的冻结 `mes_material_issue_input`。请求UUID为主键，同时保存内容摘要、outputQty、类型、完整计算快照、确认时间与本地库存应用标志；复用原 Submission/Extra与作废审计，不另建额度流水或 Execution 累计缓存。新增表除主键仅普通索引。

LOCAL_ONLY确认事务一次扣减明确库存；确认状态、贡献、路径锁定同事务。整单作废一次恢复材料；身份/库存已变化则明确拒绝，不能向其他批次恢复。CONNECTED只接受对应适配链可信确认。

### G. 统一数量口径

确认投入与撤销预留分开展示：`productionInputQty = confirmedInputQty - reservedInputQty`。只有可信成功且来源/Extra一致的确认贡献参与；增加PENDING/UNKNOWN为0，减少VOID_PENDING/VOID_UNKNOWN预留可支用额度。首节点以可支用投入为上限，后续仍使用实际前节点净报工减有效不良；末节点有效产出继续供Receipt使用。

targetQty保持计划含义；无可信投入来源的历史执行输入为0，不以计划兜底、不自动造领料。查询、管理员补录/订正、正式报工、质量订正、Receipt与工单摘要共享统一服务/查询口径。

### H. Report、Quality、Receipt 回归

全量 Maven verify通过：601项（含33项需环境显式启用而跳过），0失败/错误。当前真实MySQL另行启用的相关测试：Input 1、Report 2、Quality数量6、Quality生命周期2、Receipt 1，共12项通过；不是用全量中的跳过项宣称真实验收。

真实数据只创建明确TEST样本。旧夹具改为显式TEST确认投入，而非生产回填；新增质量/报工/交接守卫继续生效。Receipt正向质量放行原有阻塞保留，历史TEST交接夹具不冒充真实现场交接。

### I. 并发、幂等、UNKNOWN与撤销

请求UUID主键保证同请求只记一次；同UUID不同内容拒绝。永久身份主键冲突使失败准备整笔回滚，再读取胜出绑定，不遗留第二个执行。H2覆盖同码首次竞争、不同请求/相同请求重放、终态/工单冲突、不同批次、跨厂相同本地ID、无适配器和多材料拒绝。

UNKNOWN故障注入保留执行、绑定与原提交身份，未确认不贡献；核实沿用原操作，不重复外部创建。撤销在共同执行锁下先预留待减额度并校验全路径，失败不发外部调用；在安全UNKNOWN窗口仍阻止超额报工。真实MySQL验证投入300、报工280时撤销100被拒绝；远端UNKNOWN仅为隔离故障注入证据，不称真实ERP。

### J. 真实环境、SQL、API与UI证据

目标已核验：SIMPLE_B → datasource 8 → 192.168.18.25:3306/mes_base_dev_business_simple_b，绑定/数据源启用。正式Flyway business 29/30/31、center 32/33均成功。执行前备份库存/领料/执行及菜单相关范围；V30仅移除旧材料+仓库唯一限制、保留存量并加批次普通索引，支持多批次/库位。

V32创建独立扫码投产菜单；真实导航初次未显示，V33仅给原已持有 `business:mes:material:issue:update` 的角色添加同权限菜单关联及无权限目录父节点。迁移前后有效角色权限集合 `cmp` 完全一致，不授予额外权限。

真实服务验收：

- 用户重启后供应查询/计算/确切路径/数量API返回200。浏览器使用TEST工单117、Execution127、库存10。
- 计划100、既有投入300、第一工序报工280。浏览器提交产出20，按P=10/T=20计算领料40；issue81 CONFIRMED，投入320、首可报40，库存9400→9360，仍同Execution127/同routing166/同已锁路径/计划100。
- 请求 `e2e8b811-c052-4555-bdfd-2185af359b35` 并发重放均200，投入仍320，只有一条该请求事实，总领料投入事实3条。
- 原生EXE、旧材料批次码、MES1码均200且executionId=127。之前用户实例连续超时，线程快照未捕获活动阻塞；用户关闭服务并明确授权临时启动后，以上请求全部正常。未把未定位的超时归因于业务代码。
- 现有READ验收账号查询/提交投产均403；未修改其权限。菜单登录导航包含独立入口。
- 浏览器1440真实完成计算与正式投产，1280/1440/1920/390检查布局；390长身份码换行、标签宽度修正，图形在自己的容器内横向滚动，页面无整体横溢。
- 投产结果跳转数量页显示计划100/确认320/预留0/可用320，首节点报280/可报40；返回重新进入不保留旧提交。SIMPLE_B→DEV切厂离开无权入口并清除旧执行，随后恢复SIMPLE_B。
- 撤权和迟到响应采用前端自动化故障注入验证；没有为了现场验收修改用户角色。真实两个业务库同码写入未执行，跨厂写入隔离由H2双库及基础设施既有回归覆盖。

命令与记录（私有凭证不入报告）：

- `mvn -B -ntp verify`：SUCCESS，601总计/33跳过，最终日志 `.local/production-input-20261016/verify-delivery.log`（此前`verify-final.log`亦通过）。
- 开启 `MES_INPUT_MYSQL_ACCEPTANCE`、`MES_REPORT_MYSQL_ACCEPTANCE`、`MES_QUALITY_REFERENCE_MYSQL`、`MES_QUALITY_QUANTITY_MYSQL`、`MES_RECEIPT_MYSQL_ACCEPTANCE` 定向测试；`mysql-regression2.log`中Report夹具缺少系统开关，补齐后`mysql-report-final.log`两项全部通过，其余真实12项中的10项在前一日志通过。
- Node22 `npm test -- --run`：54文件307通过；`npm run build`：通过（已有大chunk提示保留）；47个本轮Java文件原生格式化成功。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10通过/0失败，随机端口独立冒烟进程退出。
- `live-api-final.json`、`live-{supply,calculation,preview,quantity}.json`、权限迁移前后TSV、数据库备份、`ui-1280.jpg`/`ui-1440.jpg`/`ui-1920.jpg`/`ui-390-result.jpg`等位于同一`.local/production-input-20261016/`；不包含认证响应的公开展示。

### K. Git与未验证边界

Base起始HEAD：backend 4679154eccb61444f1c0f795c9620a54ec6e207d；UI 02a6554baff657f6c8dfe42cebdcbc9e5f863473。保留现有Report/Quality/Receipt等大量未提交工作，不reset/提交/推送/部署。本轮后端、前端文件清单保存在证据目录backend-files.txt、frontend-files.txt。

Source严格只读，本Agent未修改其文件。结束比对时ktg-mes HEAD与其事务审计文档/两个应用配置发生外部并行变化，未检查敏感配置差异、未覆盖；另外两个Source仓库HEAD与起始内容一致。这是观察到的并行变化，不宣称所有Source快照未变。

剩余边界：真实厂商ERP未实现；本地需求为配置不是BOM管理页；纯补料未开放；真实多厂写入和真实用户撤权未做。本轮不因此实现ERP、扩权或绕过质检。

### L. 结论

| 标记 | 结论 |
| --- | --- |
| SCHEMA_READY | YES，正式目标库Flyway核验 |
| API_VERIFIED | YES，真实登录、查询、提交、并发重放、三种身份、权限拒绝 |
| UI_VERIFIED | YES，真实投产和数量导航、四档宽度、切厂；撤权/迟到为自动化证据 |
| BUSINESS_VERIFIED | YES，独立MES单材料链路；A/B/D/K有真实MySQL，完整边界按上述分级测试 |
| ERP_INTEGRATION_VERIFIED | N/A，本轮用户明确只预留接口，未进行真实ERP联调 |

**CONDITIONAL GO：独立MES单材料模式可复验；需要真实工单用料配置。不得据此发布未实现ERP适配器或宣布真实质检放行/交接全闭环。**

服务清理：用户随后明确授权“临时启动服务验收，验收完必须关闭”。本Agent以最终JAR临时启动后端PID56904，完成上述身份、重放和权限API后发送TERM，退出码143，正常关闭Quartz及连接池；socket核对8081不再监听。未关闭前端5173。证据 `service-cleanup.json`、`live-backend.log`。本轮无用户服务遗留。


场景证据索引（实际ERP均不在本轮范围）：

| 场景 | 证据与边界 |
| --- | --- |
| A/B 首次100、再次200 | ProductionInputMySqlTest真实库及ProductionInputHttpTest；总投入300、同执行 |
| C 换批150 | ProductionInputHttpTest的L002独立绑定，H2 |
| D RUNNING追加 | 真实MySQL报80后追加200可报220；真实UI报280后追加20可报40 |
| E 下游数量 | ProductionInputRulesTest以投入300/A报200/不良10/B报100校验100与90边界；Quality真实MySQL回归 |
| F 末道交接 | Receipt真实MySQL/HTTP回归末道有效产出和余额约束；使用既有TEST数量夹具而非逐字复现250/10/200，质量放行仍拒绝 |
| G 同码并发 | H2首次双请求竞争与同请求重放；真实MySQL及真实HTTP同请求并发重放，真实首次双请求竞争未另做 |
| H 同身份换工单 | ProductionInputHttpTest同产品/材料/批次换合法WO3返回PRODUCTION_IDENTITY_CONFLICT，绑定计数仍1 |
| I UNKNOWN | H2故障注入，原身份保留、增加不贡献、核实不重复创建；非真实ERP |
| J 多材料 | ProductionSupplyContractTest适配器契约多材料拒绝；非真实ERP |
| K 额度减少 | 真实MySQL投入300/报280撤100拒绝；H2安全撤销UNKNOWN窗口预留及报工超额保护 |
| L 跨厂 | H2两库相同码/相同本地ID独立；真实浏览器切厂清理，未做第二真实库业务写入 |


## 2026-10-16 追加审查：本地用料依据应进入MES业务页面

范围：仅当前源码及契约审查、建议落盘；未修改业务代码/配置/Schema，未执行数据库写入、启动服务或重跑业务验收。本章节修正前一轮交付中配置方案的产品可用性判断，保留历史验收事实。

### 当前事实及原因

`ProductionInputSupplyService.local`第146–164行按Factory+workorderId从Spring Environment读取material-id、total-required-qty、work-center和warehouse-ids。缺少material-id时直接跳过工单；其余必需配置不全则失败。并且目前先读取所有RELEASED工单，超过100张直接拒绝，再在内存匹配配置，而不是按材料需求筛选数据库候选。

WorkOrderModels.Save、WorkorderEntity、WorkOrder页面当前均无工单用料需求。检索现有业务Java及正式业务迁移，未找到可直接复用的完整BOM/工单用料需求维护链。RoutingProduct仅记录工艺适用产出产品，无投入材料、单位和数量关系；MaterialIssueItem是实际领料明细，不应反推计划用料需求。

此前采用配置的实际动机：ERP实现被用户移出范围后，需要为本地计算提供可信T、仓库范围，避免默认1:1或伪造ERP用料依据；同时未实施完整BOM管理，因此用了显式配置的TEST用料来源。这是Agent实施取舍，不是用户要求或项目规则限制。把业务数据长期放配置的选择不适当：普通新增工单不能由MES操作员完成投产准备，需要运维修改部署配置并重新加载。上轮TEST真实链路成立，但不证明新工单自助维护已闭环。

### 建议的最小收敛方案（尚未实施）

1. 在工单创建/编辑中增加“用料需求”区域，保存于当前工厂业务库的工单需求明细。用户维护投入材料、需求单位、对应当前工单计划产出的材料总需求T、允许来源仓库。P继续取工单plannedQty；材料及仓库复用已有选择组件，名称通过既有目录能力回显，不手填ID。
2. 需求与工单绑定，不新增单独的简单CRUD菜单；复用既有MasterDetailDocument/DocumentFields能力及领域选择器。若以现有工单弹窗整单提交，则WorkOrderService应提供原子保存工单及需求的聚合事务，不能用多次前端请求宣称整单原子。若采用先主后子则明确主单先保存、子单固定父ID、下达前校验，按统一主子交互规则实施。
3. V1正式投产仍只支持一种有效投入材料，不因数据采用明细结构而提前放开多材料生产。工单下达时校验本地用料依据完整性；草稿允许修改，下达后需求冻结，修改须走明确版本/受控变更，不直接改变历史投产计算。
4. 本地Supply读取数据库需求，使用材料、工单状态、允许仓库做数据库候选过滤与受控分页/上限；去掉按所有已下达工单逐项读配置，以及“总工单超过100则任何扫码均拒绝”的限制。继续保持批量读取名称/单位，无循环查询。
5. 工作中心当前无可复用的正式中心主数据或工单归属模型，不能把每工单自由文本work-center当成业务授权。LOCAL_ONLY不应仅为展示强制填虚构中心；有真实组织准入需求时再复用/补齐明确关系。ERP模式保留供应商真实组织过滤，不将本地文本冒充ERP组织校验。
6. 允许仓库是本地业务规则，收敛为页面维护的业务关联；来源库存仍由扫码后明确选仓库/批次/库位，不把所有仓库静默放开。无需在本轮顺手建立完整生产中心体系。
7. 保留ProductionSupply查询/计算契约：LOCAL_ONLY取MES需求，CONNECTED取ERP可信需求；同一时点由选定模式决定来源，不混合本地与ERP数量权威、不缺数据回退、不复制第二套领料或数量状态。
8. 需求增加revision/受控版本，前端预览保留calculationKey；本地正式准备事务持有工单/需求锁后再次验证依据版本及计算并冻结快照，避免数据库需求并发修改造成预览与领料不一致。外部HTTP仍在准备事务外。已保存calculation_json保持不变，不用新需求重算历史。
9. 正式替换时移除本轮per-workorder配置读取、验收配置与对应测试假设，测试改为通过真实业务维护入口准备需求。已有TEST工单117配置如需转换，核实后走一次可追溯迁移，不把示例推广为其他工单默认需求；历史确认事实不改。
10. 配置文件只保留LOCAL_ONLY/CONNECTED、适配器部署设置等环境配置。产品级标准用料模板可在重复维护需求被证明后另行提供，再复制成工单快照；本轮不先建设完整BOM/版本引擎，也不把用料塞入Routing核心模型。

新增持久化的具体DDL、权限、生成配置与并发策略需在实施建模时审查，本轮没有创建表。新增表基础后端仍应按真实metadata通过Codegen生成，并由业务Service约束维护和下达行为；不开放绕过冻结规则的生成CRUD写入口。

结论：本地用料依据应由MES页面维护，普通新增工单不得依赖修改配置文件或重启。当前实现仅完成受配置TEST样本链路，正式独立MES业务维护能力仍有缺口；上一轮CONDITIONAL GO不应解释为已完成该能力。
