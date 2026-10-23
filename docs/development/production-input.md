# 单材料扫码投产 V1：独立 MES 与可选 ERP

> 阅读入口：本文保留 2026-10-16、2026-10-20 的历史记录。当前物料/BOM源码审查与分阶段建议见文末 **2026-10-23：物料与单阶 BOM 完善方案**；早期 MES1/永久绑定、配置模式入口等描述不作为当前契约。新方案尚未实施，也不覆盖历史冻结请求。

2026-10-16。用户补充决定：本轮不实现真实 ERP；MES 必须能独立产生、管理生产数据，ERP 仅保留可选接口。当前以独立 MES 模式验收，CONNECTED 仅作契约验证，不把它标为真实 ERP 联调。

## 模式与事实主责

每个 Factory 只有一条生产链：WorkOrder → ProductionExecution/ExecutionPath → MaterialIssue → ProductionQuantityService → Report → Quality → Receipt。无 ERP 时，这些数据及业务行为由 MES 本地承担；接 ERP 后由适配器提供外部库存/工单用料依据和外部正式领料结果，仍回到相同的领料与执行事实，不再建立第二套生产状态。

配置唯一模式入口沿用：

```yaml
mes:
  integration:
    warehouse:
      factories:
        SIMPLE_B:
          mode: LOCAL_ONLY  # 独立 MES；切换为 CONNECTED 才要求 ERP 适配器
```

`CONNECTED` 必须同时装配当前厂/绑定对应的 `ProductionErpAdapter`（查询库存及工单）和 `WarehouseErpAdapter`（正式领料/核实/作废）。缺实现或绑定明确返回 INTEGRATION_NOT_CONFIGURED，不自动降级成本地，也不新增外部数据库或第三个 SessionFactory。

## 独立模式的本地数据

- 使用 MES 物料、单位、仓库、已下达工单及已发布适用工艺。
- 库存管理可维护 `batchNo`、可选 `binCode` 及库存/可用数量。历史无批次库存仍保留，不推导批次、不作为某个扫码批次的库存依据。
- 选中的是一个明确库存主键，展示仓库/库位/批次/单位/可用数量与读取时间。多个来源不取首行合并。
- 本轮本地单材料用料来源是管理员显式维护的工单配置，非默认1:1、非 ERP BOM 替身。工单没有完整配置时不进入可投产候选。

配置示例（仅解释结构，ID应替换为当前厂真实业务对象，不执行该示例）：

```yaml
mes:
  production:
    input:
      factories:
        SIMPLE_B:
          workorders:
            '117':
              material-id: '1'
              total-required-qty: 20
              warehouse-ids: '9'
              work-center: '本厂明确的工作中心'
```

P取当前本地工单计划产出，T取该工单配置的材料总需求，q取本次预计产出；`ROUND_HALF_UP(T*q/P,6)`。材料单位来自本地物料单位，库存数量也以此单位维护，不进行隐含换算或叠加损耗。配置仅声明本轮单材料需求；不具备本地多材料装配/BOM维护能力。多材料不得压缩为首条材料，也不能通过此入口绕过真实已知的多材料需求。

当前明确上限：单次库存最多100个来源、候选最多100张已下达工单，超限明确拒绝；不截断后冒充完整候选。需要大规模生产查询时另行增加受控分页/按物料索引查询，不提高无界循环预算。

已选工单与生产中心/允许仓库都来自当前厂配置；本地中心名称是本地管理依据，不能标为已核实的 ERP 组织。工单配置变更需随用户服务配置加载，页面没有写配置权限。

## 扫码契约

- 原生执行码：`EXE:{正整数ID}`。
- 生产码：`MES1.{base64url(UTF-8产出品号)}.{base64url(UTF-8材料品号)}.{base64url(UTF-8批次)}`，无padding。
- UTF-8解码严格拒绝非法字节；重编码必须等于原码。三字段不trim、不lowercase、不截断、不做Unicode规范化，保留前导零。允许品号/批次本身含 `#`、`.`、中文等字符。
- 旧码在投产查询中只接受精确的 `材料品号#批次` 两段；不filter/join多段。旧码在后续业务查询中只有唯一永久绑定才可解析；多个产出身份明确返回 PRODUCTION_IDENTITY_AMBIGUOUS。
- SHA-256(完整规范MES1编码)作为绑定表主键，不依赖业务主数据的大小写不敏感collation。完整编码再次核对以拒绝散列碰撞。检索字段与本地批次字段采用 utf8mb4_0900_bin；绑定编码/摘要采用ascii_bin。
- 不同工厂在各自业务库独立绑定；终态不释放。绑定其他工单或实际工艺版本/路径冲突拒绝，不隐式重绑。
- 本轮不复用断阶转换；未来适配器若支持，必须显式补齐原始身份、最终身份及转换依据，不能在前端推导。

## 投产事实、幂等与数量

采用方案A：`mes_material_issue_input` 是每次领料紧密关联的冻结确认事实，包含请求UUID、内容摘要、领料/执行、投产类型、outputQty、来源模式、完整计算依据、首次确认时间及本地库存应用标记。原有Submission、Extra及领料作废审计继续管理状态；当前只支持整单撤销，不增加部分撤销/随意额度编辑入口，不另建额度流水或Execution累计缓存。

正常PRODUCTION_INPUT必须q>0。MATERIAL_SUPPLEMENT保留零产出贡献语义，当前提交明确拒绝，因为没有独立受控的补料数量来源。

准备事务同时完成WorkOrder锁、首次Execution、永久绑定、实际路径、领料主子单、冻结输入和Submission意图。外部/本地确认调用在准备事务之后；确认事务同时落地本地库存扣减、确认时间和路径锁定。CONNECTED命令也携带冻结Input快照，供应商适配器无需猜测库存主键、库位、产出或计算依据。

请求UUID直接作为冻结输入主键；同UUID不同内容拒绝，同UUID重放不重复外部创建。不同合法请求可以复用同一执行分别贡献。并发绑定冲突先回滚整笔失败准备，再读取胜出绑定；不遗留失败方Execution。新请求不因UNKNOWN自动释放绑定。

- `confirmedInputQty`：所有可信确认且尚未成功作废的投产贡献。
- `reservedInputQty`：已进入VOID_PENDING/VOID_UNKNOWN的待减贡献。
- `productionInputQty = confirmedInputQty - reservedInputQty`：当前可支用投入，作为首工序保护上限。
- 无撤销窗口时，首输入就是累计确认投入；撤销窗口期间明确展示确认量/预留量/可支用量，预留不冒充已成功作废事实。
- 后续输入仍等于实际前一节点净报工减有效不良；可报=输入减当前报工，末道有效产出仍供Receipt消费。

撤销准备在共同执行锁下先持久化待减状态，再校验扣除该额度后的全路径。失败整笔回滚，不发起外部撤销；通过后所有报工入口都看到同一预留。成功作废后去掉贡献并恢复本地已扣材料一次；拒绝释放预留；UNKNOWN保留预留。

RUNNING追加不改targetQty、路径、routing_id、开始时间；未确认增加不增加额度、不阻断既有RUNNING的可用数量。COMPLETED/CANCELLED拒绝追加。已有历史Execution无可信投产事实时额度为0，不自动以targetQty或材料数量补账。

## HTTP与页面

独立页面 `/production/productionInput`，权限复用 `business:mes:material:issue:update`；正式菜单由system V32迁移，V33仅将新入口关联给已经持有同一个领料更新权限的角色，并补齐无权限目录父节点；前后有效权限集合一致，不新增业务授权。

- GET `/api/production/input?code=...`：只读库存及工单候选。
- GET `/api/production/input/preview?code=...&workorderId=...`：工单定义图或已绑定执行的确切版本/已选路径，只读。
- GET `/api/production/input/calculate`：服务端计算与calculationKey；提交时重算并检查依据一致，防止界面预览后配置变化而静默换料。
- POST `/api/production/input`：requestId、code、workorderId、stockReference、outputQty、routingId、routingNodeIds、inputKind、calculationKey；没有允许用户覆盖issueQty的字段。
- GET `/api/production/identity`：统一只读解析。Quality/Receipt复用同一前端查询组件选择精确执行，保留执行查询权限和各自写授权；没有借扫码扩大授权。

界面一个工单自动选中、多个用户选择；库存来源明确选择；路径按实际相邻边逐步选择。分支不按display_order串联。已绑定身份显示原执行，终态提示禁止投产。切厂/撤权/修改扫码或选择时清理并拒绝迟到响应；请求失败保存原requestId重查，不自动生成新请求。

## 运维与历史边界

- business V29/V30/V31、system V32/V33须通过正式Flyway执行。V30将库存从“材料+仓库唯一”改成支持材料批次/库位的来源记录，保留存量、仅建普通索引；不是给每批库存新增全局唯一约束。
- 本轮验收配置仅针对 TEST 工单117及TEST批次库存，不是客户生产配置；服务由用户重启加载。
- 已有普通领料资料没有outputQty时不自动产生投产贡献；需要历史迁移须另立可审查任务与可信来源。
- Quality/Receipt原有准入继续生效；生产交接不代表库存入库，不实现ERP5901/5A00或多材料装配。

## 2026-10-20：投产与报工入口填充契约复核（只审查）

本节基于当前源码及5173真实浏览器只读观察。旧章节的YAML模式入口、候选100条等历史描述不作为当前规则：当前模式复用FactoryIntegration权威配置；候选已经分页；本地用料优先mes_workorder_material，只有缺数据库用料行时才有仍保留的历史配置兼容。没有修改代码、配置、数据库或服务。

### 1. 扫码投产应填写什么

- 扫码框接受精确的“材料品号#材料批次”，例如`RM001#LOT001`（仅格式示意，不是本次真实库存），或系统产生的完整MES1生产身份码；不接受工单号、单独材料品号、EXE执行码。材料品号及批次按原值匹配，不自动去空格或规范化。
- MES1由产出品号、材料品号、批次的UTF-8 base64url无padding组成；应使用系统生成码，不由操作员手工拼编码。
- GET /api/production/input → ProductionInputSupplyService → 当前模式对应供料源。LOCAL由LocalProductionSupply读取启用物料/单位、批次库存、启用LOCAL仓库及已下达LOCAL工单；用料、单位和允许仓库须匹配。EXTERNAL候选由当前绑定适配器提供并校验来源；本轮未调用ERP。
- 工单候选只有总数1时自动选中；多张必须手选。库存来源不自动取首条，必须明确选仓库/批次/库位；outputQty由人填“本次预计成品产出量”，不是材料领用量。
- GET calculate按`材料总需求 × 本次预计产出 / 工单计划量`计算材料领用量，四舍五入至6位小数。服务端返回calculationKey；页面不能手填或覆盖issueQty。
- 首次身份无绑定时人工选择相邻工序组成完整路径；已有绑定时显示原执行的确切版本和锁定路径。
- POST携带requestId/code/workorderId/stockReference/outputQty/routingId/routingNodeIds/inputKind/calculationKey；正式确认可能创建执行、永久生产身份绑定、领料主子单、冻结投产事实并扣库存，不是只保存表单。未知结果保存原请求，不能新建替代请求。

### 2. 现场界面证据

步骤1：进入扫码投产，入口可访问，但输入框窄且placeholder截断，按钮远离输入框。截图：`target/production-entry-audit-20261020/01-production-input-audit.png`。

原因是UI全局styles.css `.filter-panel .el-input`的184px!important，>=1800时208px!important及1281–1799时160px!important，覆盖本页width:100%。父form-item flex:1只扩大空白，不扩大输入框。建议将扫码入口按独立扫描任务排版，在页面作用域解决固定宽度覆盖；保留真实协议和权限，不直接全局放宽全部查询框。

### 3. 证据边界

当前浏览器选中TEST_DEV_PERF_20261019，保留其已有DevTools和缩放状态；没有改厂、没有连接数据库、没有构造库存/工单数据或正式提交。本轮投产只观察初始入口，候选和计算填充来自源码审查与现有Fixture测试；不能称为SIMPLE_B真实投产验收。Node22运行productionInput.test.ts与productionReporting.test.ts共20项通过。报工填充、占位错误及UI原因详见[报工报告2026-10-20追加章节](../reviews/2026-10-16-production-report-v2-implementation-report.md)。截图为本次原生Chrome窗口捕获，已打开保存文件复核；未进行完整键盘、读屏及响应式三档验收。

### 2026-10-20 后续实施：统一扫码输入布局

用户确认后已修改`mes-base-ui/src/views/production/productionInput/index.vue`：标签置顶，扫码输入最大640px、44px高，查询按钮相邻12px，空间不足自然换行；页面作用域覆盖公共!important固定宽度，未修改全局查询栏。增加材料品号#批次的格式示例和MES1完整码说明、aria标签/说明关联。候选、数量、路径、幂等恢复、权限和请求协议不变。报工页同步采用相同布局。

现有5173浏览器两页面在1280/1440/1920下扫码框均640px、按钮间距12px，documentWidth与视口相等；临时视口已恢复。截图保存`/private/tmp/production-input-fixed-{1280,1440,1920}.png`及对应`production-reporting-fixed-*`，覆盖初始扫码区域。Node22全量67文件404测试通过，生产构建通过（保留已有deep选择器和包体积提示）。未发送投产/报工POST、未重启服务；未验证实际扫码枪、真实新投产及本轮未处理的报工未知结果退出恢复风险。回滚只需撤销两个页面本次template/style差异，不回滚同工作区其他修改。

## 2026-10-23：物料与单阶 BOM 完善方案

### 1. 推荐结论、时间线与证据边界

**建议首期交付“可发布的单阶、单组件 BOM → 工单选定并冻结用料 → 累计额度约束的正式领料”，复用现有统一物料、工单用料、库存动作、冻结请求和生产数量链路。** 单材料不等于1∶1；用“基准产出数量＋组件标准用量”表达比例。多阶展开、多材料装配、MRP、采购、成本、替代料、联副产品和拆合批后置。

本节状态为**审查与待讨论建议，尚未实施**。证据标记：**S**源码事实，**O**官方资料事实，**I**推理，**R**建议，**U**未验证。后续数字、编码均为合成示例，不含客户数据。

- S：Base后端审查基线 `16e5857d797d5e9fc0b5a8b8cd1ffaf4c8069e36`，开始时工作树/暂存区干净；UI基线 `d0551346c7c404fb57a0097b630c34cc74fa69eb`，有既有页面/样式修改和未跟踪测试，全部保留。
- S：核实只读来源实际目录为 `/Users/user/work/heri/ktg-mes`、`/Users/user/work/heri/ktg-mes-ui`、`/Users/user/work/heri/heri-uni-app`。旧后端 `23e445ec` 工作树干净；旧UI `42481d5` 与小程序 `f109f79` 有既有改动，未触碰。依来源规则排除ktg-wheel。
- 已读个人工程入口及Principles、Workflow、Java、Frontend、Knowledge Governance，目标及来源根规则；检索docs/doc/knowledge内容及文件名。同主题最接近本文，故追加而不新建平行方案。历史报告仅提供线索，当前结论重新核对源码，不搬用历史数据库统计。
- U：未运行测试、构建或浏览器业务操作，未连接数据库、调用真实ERP、启停服务或部署。Schema事实仅来自仓内定义，未查部署Schema；测试源码和官方文档不代表当前系统运行通过。
- 本轮用户要求优先于历史描述及当前根规则中的旧语义：标签中间段是**材料品号**；Execution按**工单＋批次原文**唯一；首次正式领料成功才创建；结束后新增可信投入可恢复未完成。下文明确这些与当前实现的差距，不将其写成已具备能力。历史冻结请求不改写。

### 2. 物料、BOM、工单和领料的责任边界（R）

| 对象 | 首期需要完善的职责 | 边界 |
| --- | --- | --- |
| 物料/单位 | 统一编码、名称、分类、基本单位、启停及引用保护；同一物料可为上游产出、下游材料 | 不拆三套原料/半成品/成品主数据；类别不决定库存能否领用，不增加无证据的重量/尺寸/损耗必填项 |
| BOM | 某产出在基准数量下需要的唯一组件、单位、标准量及可识别发布版本 | 不承载领料实绩、批次、仓位、Execution或收尾状态 |
| 工单用料 | 选择BOM或可信ERP需求，冻结该工单的唯一用料依据及计划额度 | 不在领料时跟随当前默认BOM，不与人工T/P或YAML竞争权威 |
| 正式领料/投入 | 验证确切库存、累计材料及产出额度、并发和幂等；形成事实 | 不默认1∶1，不因补料扩大计划，不用行政领料绕过正式投入 |
| Execution/生产数量 | 成功投入、路径、报工、不良、入库及当前缺口；同工单同批次一个执行 | 标签打印/扫码次数不产生投入；新增事实恢复办理不删除历史结束事实 |
| 仓储 | 来源校验、草稿/审核、库存动作与追溯；审核驱动完全入库及工单收尾 | 不恢复定时完结；不良仅申报/入仓，仓内流转归仓储，不引入专门返工流程 |

首期只需单阶BOM：A→B、B→C各有一张工单和单阶配方，即可跨工单生产。把C需求递归展开成A是物料计划功能；它不能替代B的真实库存、领用或批次追溯。

### 3. ktg-mes调用链与能力矩阵（S）

位置约定：`K=/Users/user/work/heri/ktg-mes`；`KJ=K/ktg-mes/src/main/java/com/ktg/mes`；`KX=K/ktg-mes/src/main/resources/mapper`；`W=/Users/user/work/heri/ktg-mes-ui`；`M=/Users/user/work/heri/heri-uni-app`。以下路径加行号可精确定位本轮快照，未来修改可能移动行号。

| 编号/能力层级 | 入口→Service→Mapper/定义 | 源码结论 |
| --- | --- | --- |
| K01 主数据存在/同步 | `K/scripts/mf-upgrade/sql/009-new-factory-business-init.sql:786–810`；`KJ/erp/service/impl/ErpItemServiceImpl.java:224–274` | 一张md_item，item_or_product和分类并存；同步新增时按首字符区分原料/半成品/成品分类，itemOrProduct写ITEM；已有物料保留本地分类，仅更新ERP管理字段。品号首字符是来源专用规则，不复制到Base。 |
| K02 本地BOM实体/Schema | `KJ/md/domain/MdProductBom.java:21–58`；初始化SQL `:833–855` | md_product_bom每行父item_id→子bom_item_id，quantity为使用比例，带组件单位/启用；没有独立表头、基准产出量、版本、默认版、损耗。脚本quantity为double(12,4)，父子唯一键存在；不等于部署库已核实。 |
| K03 BOM页面/CRUD | `W/src/views/mes/md/mditem/index.vue:322`→`W/src/views/mes/md/mditem/components/itembom.vue:97,166,241,274`→`/mes/md/bom`→`KJ/md/controller/MdProductBomController.java:34–109`→`KJ/md/service/impl/MdProductBomServiceImpl.java:42–94`→`KX/md/MdProductBomMapper.xml:29–45,89` | 普通CRUD确实存在，Service基本转发。启用仅普通字段，无独立发布/提交/版本修订命令；查询显式传enableFlag才过滤。 |
| K04 校验边界 | `KJ/md/controller/MdProductBomController.java:81–98`；`W/src/views/mes/md/mditem/components/itembom.vue:80–81,147–150,260–275` | 新增只有直接自身比较且用Long的==，编辑未见同检查；未见全图循环、启用材料、正比例、单位一致性服务端校验。UI允许0、四位小数，选料默认比例1，不是后端正数规则。重复组件仅有初始化唯一键证据。 |
| K05 “阶”来源 | `KX/md/MdProductBomMapper.xml:67–85`；`KJ/pro/controller/ProWorkorderController.java:248–267` | SQL递归生成level、上限50；Java递归约20层。阶由父子关系推导，不是正式存储层级或ERP同步阶字段；深度截断不能替代循环检测。 |
| K06 手工工单复制 | `KJ/pro/controller/ProWorkorderController.java:136–139,188–211`；编辑`:152–160`→`KJ/pro/controller/ProWorkorderBomController.java:75–102` | 创建按产品取组件，复制身份/单位，需求=工单计划×quantity，未传enableFlag；产品/数量变化删除重建；工单用料还可独立CRUD，无版本冻结。展开根层读工单行，下层仍读当前BOM，不是全部层级快照。 |
| K07 当前工单页真实展示 | `W/src/views/mes/pro/workorder/index.vue:227–231`→`W/src/views/mes/pro/workorder/items/item.vue:68,125–133`→`W/src/api/mes/erp/workorder.js:3–8`→`KJ/erp/controller/ErpWorkOrderController.java:25`→`KJ/erp/service/impl/ErpWorkOrderServiceImpl.java:171–172`→`KX/erp/ErpModMapper.xml:22–26` | 当前实际按moId读erp_mo_d。目录中的bom/bom.vue和遗留import不能证明页面仍消费本地pro_workorder_bom。 |
| K08 ERP BOM同步 | `KJ/erp/service/impl/ErpBomServiceImpl.java:42–85,93–121`→`KX/erp/ErpBomMapper.xml:15–27`；`KJ/erp/service/impl/ErpThailandBomServiceImpl.java:29–60`→`KX/erp/ErpThailandBomExtraMapper.xml:14–23` | 已审核父子品号/QTY_PER同步到本地父子关系，按键覆盖；未同步版本/基准量/损耗/生效期，不能代表版本化BOM。多ERP版本行可能折叠为同一父子集合（I）。 |
| K09 仅字段存在 | `KX/WmTransferRelated/WmBOMMapper.xml:7–18,56–60` | ERP查询DTO映射STANDARD_BATCH_QTY/YIELD_RATE/VERSION_TIMES，不证明本地正式生产用这些字段计算。 |
| K10 新申请仅材料建议 | `KJ/pro/request/service/WorkorderProductionService.java:38–69,83–152`；`pro/request/domain/WorkorderMaterialSnapshot.java:4–10` | 明示BOM仅建议清单，suggest不读quantity；无BOM让审核员补材料。快照只有材料/批次/单位，无标准量、比例、BOM版本；不能据Snapshot名字认定数量已冻结。 |
| K11 小程序正式领料 | `M/pkg-issue/pages/issueReceipt/index.vue:249–275`→`M/api/issueReceipt/issueReceipt.js:3–20`→`KJ/erpapi/controller/IssueReceiptController.java:61–81`→`KJ/erpapi/service/impl/IssueReceiptServiceImpl.java:1151–1227` | 正式走insert5600，支持主材料＋配件；材料匹配依据是实时ERP MO/MoD，非本地md_product_bom累计限额。当前Service`:1286–1287`恒选5600，不沿用历史5650活跃路径说法。 |
| K12 正式材料/单位/数量 | `KJ/erpapi/service/impl/IssueReceiptServiceImpl.java:120–134,168–169,280–292,336–337,360–390` | 实时MO中找材料，未找到拒绝；单位来自MoD，输入qty写InventoryQty/IssueReceiptQty。已审入口未见“累计领用＋本次≤计划×比例”的服务端保护或显式单位换算；ERP自己是否拒绝超领属U。 |
| K13 比例/配件/扫码 | `KX/erp/ErpModMapper.xml:58–62`；`KJ/erpapi/service/impl/IssueReceiptServiceImpl.java:846–868`；`M/pkg-issue/pages/issueReceipt/selectedAccessory.vue:65–67`、`M/pkg-issue/pages/issueReceipt/index.vue:344–379` | ERP比例=需领量/计划量；getQtyPer实时读MO、5位HALF_UP，配件UI建议=本次qty×qtyPer。不是版本冻结或服务端强校验。旧扫码trim/拆材料#批次及专用批次逻辑不符合Base原文标签要求。 |
| K14 追加/退料/行政领料 | `KJ/erpapi/service/impl/IssueReceiptServiceImpl.java:1153–1159,1217–1223`；`KJ/erpapi/controller/IssueReceiptController.java:84–97`→Service`:418–446,639–656`；`KJ/wm/controller/WmIssueHeaderController.java:192–209` | 旧扣料完结后拒绝投料，与本轮恢复要求不同；退料按MO材料及输入qty构造ERP退料。旧仓储领料检查明细非空→库存交易→结束，不读BOM。 |
| K15 报工消耗另一来源 | `KJ/pro/controller/ProFeedbackController.java:278`→`KJ/wm/service/impl/WmItemConsumeServiceImpl.java:168–245` | 消耗=pro_route_product_bom比例×本次报工，另一路比例权威；缺线边库存仍生成无库存关联消耗行并允许负库存后核销，不能作为Base不足回滚的参考。 |
| K16 同步入口/启用边界 | `KJ/erp/lot/controller/WorkorderMasterDataController.java:20,67–71`→`KJ/erp/lot/service/WorkorderMasterSyncService.java:39–44`→ErpBomService.synchronizeBoms；`KJ/erp/service/impl/ErpBomServiceImpl.java:97–121` | 存在POST /mes/md/workorder-master/sync-bom，异步提交BOM同步；新增关系启用Y，已有关系保留启用标识。只读已审核ERP BOM，未见撤审/删除传播。`W/src/api/pro/workorderRequest.js:4,20`有封装，但视图未见syncBom调用；命名Bean方法可供Quartz调用，不证明当前任务已注册/启用或运行。 |
| K17 删除链 | `KJ/md/controller/MdProductBomController.java:104–109`→`KJ/md/service/impl/MdProductBomServiceImpl.java:80–82`→`KX/md/MdProductBomMapper.xml:162–167`；物料删除`KJ/md/controller/MdItemController.java:120–124`→`KJ/md/service/impl/MdItemServiceImpl.java:71–78`→`KX/md/MdItemMapper.xml:153–162` | 均直接物理删除，已审链未见BOM/工单/生产引用保护。初始化BOM无FK仅是脚本事实，不能据此断言部署库无其他保护。 |

**特殊情况汇总：** 未配置本地BOM可生成零工单用料，新申请可以补清单；“多个有效BOM”实际是一组组件关系，没有版本选择。零比例UI/Schema允许；停用组件未在工单复制/建议入口主动过滤；单位字符串不构成换算证明。现有CRUD、同步、工单复制、正式ERP领料、路线消耗分别存在，但没有统一版本化BOM＋冻结用料＋累计材料额度的完整证据。上述均静态审查，未验证源项目运行、ERP审核状态或部署Schema。

关键源码直达：[产品BOM CRUD](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/md/controller/MdProductBomController.java:81)、[工单用料生成](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/controller/ProWorkorderController.java:188)、[正式领料](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erpapi/service/impl/IssueReceiptServiceImpl.java:1151)。

### 4. Base已有能力、可复用部分与确定缺口（S）

位置约定：`B=/Users/user/work/lixin/mes-base`；`J=B/mes-business/src/main/java/dev/mesbase/business`；`X=B/mes-business/src/main/resources/dev/mesbase/business`；`V=B/mes-admin/src/main/resources/db/business`；`U=/Users/user/work/lixin/mes-base-ui`。

| 编号 | 源码位置 | 能力与边界 |
| --- | --- | --- |
| B01 统一物料/单位 | `J/basic/domain/MaterialEntity.java:4–23`、`J/basic/domain/MaterialType.java:6–8`；`V/V2__simple_business_capabilities.sql:53–69` | 共用material，类型ITEM/PRODUCT，无独立半成品实体；具备分类/单位/启停。 |
| B02 已有计量身份保护 | `J/basic/service/BasicDataRules.java:78–88,149–155`；`J/basic/service/MaterialUnitRules.java:73–120` | 已有编码、单位、状态、类型校验；库存/历史动作/单据/需求/领料/工艺/工单/执行引用阻止换基本单位；删除单位也保护引用。不能继续把旧报告的单位保护问题当现缺口。 |
| B03 工单用料不是空壳 | `V/V35__local_production_closure.sql:2–15`；`J/production/service/WorkorderMaterialService.java:43–45,76–128` | mes_workorder_material.id=工单ID，一单一材料；T总需求、P保存时计划、单位、仓库范围、工作中心、revision/status。是有效比例权威，无BOM/version ID。 |
| B04 冻结时点 | `J/production/service/WorkorderMaterialService.java:80–87,100–126`；`J/production/service/WorkOrderService.java:359–370` | 保存锁工单、乐观revision、正需求和启用引用；DRAFT/RELEASED均可改，有任意领料头即冻结，含准备/UNKNOWN。下达只校验产品/路线，不检查/冻结用料。 |
| B05 来源优先级 | `J/wm/service/LocalProductionSupply.java:86–113,155,161–172` | 有数据库需求行绝不回退YAML；停用/计划不匹配返回空候选。无行才受legacy-config开关读取历史配置。来源引用带需求revision，提交锁内再核对。不可未经盘点删除，也不宜长期保留两条新比例权威。 |
| B06 计算/单材料 | `J/production/service/ProductionInputSupplyService.java:98–147` | 正需求按materialCode去重，只允许一种；材料、unitId/unitCode与库存一致。issueQty=HALF_UP(T×q/P,6)，无损耗/换算/累计补差。零负外部行先被过滤，不是完整外部需求有效性校验。 |
| B07 量纲/精度 | `J/wm/service/LocalProductionSupply.java:116–155`；`J/production/domain/ProductionQuantityInput.java:14–23` | 产出单位取产品，材料单位取材料；支持件产出/kg材料。新人工数量最多2位，计算/存储仍6位；单位表无每单位精度策略，不能擅自把所有“件”设整数。 |
| B08 库存选择 | `J/wm/service/LocalProductionSupply.java:130–150`；`U/src/views/production/productionInput/index.vue:45–48,104` | 按材料/批次/允许仓库找库存，精确原文复核批次、启用LOCAL仓/正可用量；不固定原料仓。UI只自动选唯一工单，尚未见唯一库存自动选中；100库存来源上界拒绝，非无界选择。 |
| B09 冻结/重放 | `J/production/service/ProductionInputSupplyService.java:131–138`；`J/production/service/ProductionInputService.java:149–172,220–240`；`J/issue/service/MaterialIssueInputFacts.java:50–78` | calculationKey、提交重算、锁内版本检查；UUID先查旧事实，异内容拒绝。calculation_json冻结完整依据，重放不重算/重建，合法撤销读原量和原库存。应复用并补BOM字段。 |
| B10 已有产出额度 | `J/production/service/ProductionInputService.java:169–214`；`X/production/dao/WorkOrderExtraMapper.xml:18–34`；`J/production/service/ProductionExecutionRules.java:208–211` | 工单锁串行输入，confirmedInputQty＋pending＋本次q超过target才校验其他执行target＋新target≤计划再增加target。已有产出防超，不等于已有累计材料防舍入超额。 |
| B11 当前定位 | `J/production/service/ProductionInputService.java:183–202`；`J/production/service/ProductionIdentityService.java:61–66,91–110`；`V/V52__execution_batch_and_scan_activity.sql:2–4` | 现按工单/产品/批次找，多条报歧义，V52普通索引；未见工单＋批次数据库唯一保证。标签中间段按产出品号校验，与本轮材料品号语义不同。 |
| B12 创建与扣库窗口 | `J/production/service/ProductionInputService.java:158–166,191–195`；`J/issue/service/MaterialIssueSubmissionService.java:113–137,237–238`；`J/issue/service/MaterialIssueInputFacts.java:36–39,56–68`；`J/wm/service/LocalStockService.java:186–215,317–319,386–390` | prepare先建READY执行、领料及冻结意图并提交，库存仅锁/检查；随后结果事务才扣库/确认。prepare已不足则整笔回滚；两事务间被耗尽时，后事务回滚可能留下READY执行和SUBMITTING意图（I，未并发复现）。不满足成功才创建。 |
| B13 结束追加 | `J/production/service/ProductionInputService.java:199–200,255–261`；`J/issue/service/MaterialIssueSubmissionService.java:149–150` | 只允许READY/RUNNING及PRODUCTION_INPUT；COMPLETED/FULLY_RECEIVED不能追加，零产出自由补料不支持。恢复未完成是新增工作。 |
| B14 行政多明细 | `J/issue/service/MaterialIssueRules.java:157–182`；`J/issue/service/MaterialIssueSubmissionService.java:159–163`；`J/issue/service/MaterialIssueInputFacts.java:56–58` | 行政领料1—100行，基础校验，无BOM比例；无input fact不贡献正式投入。不能宣传已具备多材料生产。 |
| B15 ERP仅接口 | `J/production/service/ProductionInputSupplyService.java:73–92`；`J/integration/production/ProductionErpAdapter.java` | CONNECTED需唯一适配器；生产Java检索未见生产实现，测试mock不是ERP用料接通证据；缺配置失败关闭。 |

检索业务Java、Flyway及UI basic/API未发现产品BOM主表/正式维护链路；routing_product是工艺适用关系，不是BOM。此结论限定仓内已审范围，不推断实际部署库没有额外表。

关键源码直达：[工单用料维护](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/WorkorderMaterialService.java:76)、[比例计算](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionInputSupplyService.java:141)、[Execution准备与额度](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionInputService.java:169)、[当前标签语义](/Users/user/work/lixin/mes-base/mes-business/src/main/java/dev/mesbase/business/production/service/ProductionIdentityService.java:61)。

### 5. ERPNext官方参考表（O事实与R建议分列）

本轮“nexterp”按用户约定解释为**ERPNext**，并非确认该拼写就是官方产品名。已核对[Frappe官方ERPNext页面](https://frappe.io/erpnext)及其官方文档/仓库链接。固定源码：[v15.123.1官方发布](https://github.com/frappe/erpnext/releases/tag/v15.123.1)，commit `40b66a19896d1321964e4fc0746f6bc903a6ffd9`；访问日期均为**2026-10-23**。这是本次研究基线，不宣称是所有主版本中的最新版本。在线文档持续更新，BOM页含v16内容；具体实现以该tag核实，不混用develop。

| O：官方依据/事实 | 设计目的与前提 | R：Base采用/后置/不采用 |
| --- | --- | --- |
| [Item文档](https://docs.frappe.io/erpnext/item)：统一Item、库存单位及制造属性 | 同一物料可在不同工单扮演不同角色 | 采用统一主数据原则，复用material，不新建半成品身份链 |
| [BOM文档](https://docs.frappe.io/erpnext/bill-of-materials)；[bom.py:1159](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L1159)：组件量/BOM.quantity×目标量；源码注释避免先用已舍入单位用量 | 表达非1∶1并降低截断误差 | 首期采用基准量＋标准量，保留原始分子分母；Base继续BigDecimal，不移植float实现 |
| [bom.py:610](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L610)：stock_qty=conversion_factor×qty；[UOM文档](https://docs.frappe.io/erpnext/uom)及[bom.py:620](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L620)有整数约束 | 同一物料不同录入单位须归一库存；单位有可分性 | 首期组件直接用库存基本单位；产品件与材料kg是比例，不是单位换算。跨单位换算及每单位精度有真实需求再建，未知系数拒绝 |
| [BOM文档](https://docs.frappe.io/erpnext/bill-of-materials)提交后结构不可普通改；[bom.py:554](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L554)管理默认，[1273](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L1273)校验active/submitted | 区分草稿和生产可引用版本 | 采用发布/修订/停用。多版本明确选定；默认最多为可见建议，首期可不建自动默认，不照搬自动补默认 |
| [work_order.py:1232](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/work_order/work_order.py#L1232)复制required_items；[173](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/work_order/work_order.py#L173)、[stock_entry.py:1259](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/stock/doctype/stock_entry/stock_entry.py#L1259)及[2980](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/stock/doctype/stock_entry/stock_entry.py#L2980)仍有重读BOM | 工单需求与实际领用对照；复制子表不等于以后永不重算 | 采用复制到工单；Base额外冻结完整请求，不以ERPNext文档证明旧UUID安全 |
| [多阶BOM文档](https://docs.frappe.io/erpnext/managing-multi-level-bom)：直接组件与展开表不同，子装配分开生产时关闭Use Multi-Level BOM | 区分领库存B与继续制造B所需A | 多阶展开后置；B入库存后给第二张工单领用，不能同时扣B和下阶A |
| [bom.py:1048](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L1048)计算process_loss，[153](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L153)/[403](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/bom/bom.py#L403)定义/处理独立scrap_items；[stock_entry.py:788](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/stock/doctype/stock_entry/stock_entry.py#L788)在对应制造路径减少成品量 | 配方损耗、产出损失及有库存身份的废料不同 | 标准损耗/联副产后置；现不良照实际申报/入仓，不将不良率自动加进领料额 |
| [work_order.py:1280](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/work_order/work_order.py#L1280)累计转移、[1376](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/work_order/work_order.py#L1376)耗用；[stock_entry.py:1294](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/stock/doctype/stock_entry/stock_entry.py#L1294)按剩余需求限制但有配置分支；[work_order.py:480](https://github.com/frappe/erpnext/blob/v15.123.1/erpnext/manufacturing/doctype/work_order/work_order.py#L480)存在超产百分比 | 区分需求、材料转移、耗用和产出，不是所有设置下严格禁超 | 采用事实累计原则；不采用超领容差/放宽开关。ERPNext仓间转移不是Base已确认投入，不证明本轮累计差额法已由ERPNext实现 |

其他成熟ERP本轮不扩展：ERPNext已能回答具体模型问题；Base标签、成功创建、UUID、LOCAL/EXTERNAL隔离仍按本轮自身约束设计，不能仅因ERP具备某功能就列入首期。

### 6. 最小领域模型与唯一权威（R；本轮无DDL）

| 对象/关系 | 必要字段语义 | 生命周期/约束 |
| --- | --- | --- |
| Material→Unit | 复用id/code/name/category/basic unit/status | 原料/产品是使用角色；沿用计量身份保护并纳入新增BOM引用，不新增“阶”必填项 |
| BOMVersion→产出Material | 本地id、可识别编码/修订、产出materialId、基准产出量B、产出单位、状态、发布审计 | B>0；发布结构不普通编辑，改量/换料新建修订；引用版本不物理删除，可停止新选用 |
| BOMVersion→唯一组件 | 组件materialId、标准用量C、组件单位 | C>0；首期恰好一种组件一条标准行，组件用库存基本单位；拒绝自引用、重复、多组件、停用材料/单位。组件可以是半成品，不自动连其BOM/展开 |
| WorkOrder→用料快照 | 复用mes_workorder_material工单权威职责；补选定BOM身份/修订或可信外部依据、产出/组件身份及代码/单位快照、B/C、计划P、材料额T、规则版本/精度、修订审计 | 下达前确定并冻结；运行只读工单快照。仓库范围/中心属工单供料条件，不是BOM全局固定仓库；旧T/P不编造BOM编号 |
| 领料意图→冻结投入 | 复用UUID/hash、来源及provider/binding、用料快照版本、q/m、库存身份、批次、累计前后值、计算规则、确认和动作关联 | 未决占用额度但不贡献正式投入；重放/撤销沿原冻结事实，不能随BOM改版回算 |
| Execution→工单＋批次 | 复用id、产出产品、batchNo、路线、当前状态 | Factory库内逻辑唯一键为工单id＋批次原文，产品由工单确定；所有入口与数据库共同保证，不以材料不同另建。精确文本保留空格/大小写/前导零，不能采用折叠尾空格的唯一比较 |
| 产出入库→库存→下单领料 | 复用入库明细/动作的源执行关联及下一次确切库存来源 | 批次不是全局库存ID，同批不同物料/仓库不合并，不仅凭批次证明上游 |

关系：`产出物料←BOM版本→唯一组件`；`BOM版本→工单用料快照→多笔领料/投入事实→工单＋批次Execution`。未来实施复用MasterDetailDocument/DocumentFields，不把BOM头/行作为两个独立正式CRUD入口；主子基础后端仍依正式metadata生成，发布/引用保护由业务Service限制。本轮没有生成、建表或执行SQL。

**选择与冻结：** 零BOM可存准备草稿但不能下达；唯一有效版可预填并展示，多版必须明确选，服务端拒绝缺选/歧义，不能取第一条。建议下达时复制冻结，打印只对已确定快照工单开放，避免打印后换料。草稿未打印可重选；已打印无业务事实如需改料，须明确撤回工单准备、提示标签失效/重打，不新建永久打印/扫码绑定，不以打印记录建立生产事实引用保护。存在领料意图后继续现保守冻结边界。

**收敛比例权威：** 新LOCAL工单以BOM作准备来源，以工单快照作生产唯一权威；绑定后不另开手改T覆盖BOM比例。复用工单用料查询、仓库/权限/乐观修订及旧事实保护。旧单有可信T/P则照原证据运行，不能标成“来自BOM”；YAML须盘点消费者、配置及冻结请求后正式退役，不自动导入、不批量回算、不改历史calculation_json。不能仅看到新BOM就宣布WorkorderMaterialService废弃。

### 7. 数量公式、累计额度及例子（R）

**量纲：** P工单计划、q本次预计产出及正式投入Q均为产出产品单位；C标准材料量、T计划材料额、每笔m和累计M为组件库存基本单位。“正式投入100件”是授权加工产出额度，不是领100kg，不是已经报工/入库100件。

设B为基准产出量、C为标准组件量；不先截断C/B。新快照建议 `T=Round(C×P/B,s)`，`F(x)=Round(C×x/B,s)`，F(P)=T。s沿用当前计算/存储6位、HALF_UP作为最小兼容建议，**不是业务超领容差**。B/C新人工维护与当前2位输入规则须明确衔接，不借BOM无声放宽所有输入；比例可用放大基准表达。旧工单仅有可信T/P时保留规则版本 `F_old(x)=Round(T×x/P,s)`，不补造B/C。

在**工单共同锁内**，Q为成功且有效的产出贡献＋未决占用，M为同范围成功/未决的材料占用：

`本次m = F(Q+q) − M`

同时校验q>0、Q+q≤P、M+m≤T、既有执行分配总量≤计划、m>0且按单位精度可表示、确切库存充足。累计范围为**全工单全部批次/Execution**，不能每个执行都领一遍总额度。现有其他执行target仍占计划，不能被公式夺走。预览包含快照及累计版本，提交前变化则明确陈旧计算，不能替换已提交UUID。

- 待发送/提交中/UNKNOWN占q、m但不增加正式投入；明确未生效并经正式恢复结束才释放。M不是仓库余额，不能从报工量或打印次数倒推。行政领料不制造投入；不得成为生产超领旁路。
- 最后一笔Q+q=P时m=T−M，补齐累计差额；冻结每笔m、累计前后值和规则版本，不改旧事实。取消/核实释放或确认原占用，乱序发生后重新校验余量；不可表示的零/负差额不能隐式发负数或少领。
- 正式作废沿原m和原q及依赖保护处理；物理退料不当然重新授予产出或补料额度，不得凭净库存循环补料。零q自由补料仍不开放；“追加”是带q和m的新正式投入，工单P不自动变大。

**例1：1个产出对应2.5个材料单位。** B=1件、C=2.5kg（等价B=2件、C=5kg）；计划P=100件，T=250kg。

| 同工单/批次分笔 | 本次q（件） | 累计Q（件） | 本次m（kg） | 累计M（kg） | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 首次 | 20 | 20 | 50 | 50 | 成功后首次创建Execution |
| 第二笔 | 30 | 50 | 75 | 125 | 追加原Execution |
| 第三笔 | 40 | 90 | 100 | 225 | 剩10件/25kg |
| 此时申请11 | 11 | 拟101 | 拟27.5 | 拟252.5 | 拒绝，不扣库/增贡献/改计划 |
| 合法末笔10 | 10 | 100 | 25 | 250 | 达计划额度，再加1件拒绝 |

若90件已加工、入库且执行已结束，末笔10件成功投入可依据新事实恢复未完成；原90件报工、库存、审核及结束事件全部保留。不能因旧90件入库就继续标完全入库。工单曾收尾时，追加命令还应受控恢复当前办理状态并保留旧收尾事件；已作废/来源冲突/UNKNOWN不绕过保护。当前不支持此恢复，见B13。

**例2：材料和产出单位不同。** kg/件是配方比例，不必声明“1件=2.5kg”单位换算。如果同一材料库存以g而BOM录kg，才需要可信换算。首期可直接按库存基本单位录B=2件、C=5000g；这里1000g/kg只是合成例中明确的物理换算，不自动配置到系统，更不能推广到箱/件、米/件。无可信换算即拒绝正式领料。

**例3：分次取整与累计额度。** P=3件、B=3件、C=2kg，三次q=1，独立HALF_UP每笔0.666667，累计2.000001超2。累计法F(1)=0.666667、F(2)=1.333333、F(3)=2，对应三笔0.666667、0.666666、0.666667，合计2。C=1kg/B=3件时三笔应为0.333333、0.333334、0.333333，合计1；当前逐笔算法仅0.999999。若材料只能整数领用，不能三笔都向上取整；默认合并到可表示的批量，具体不可分单位需确认，不擅自设置精度0。

**例4：改版后旧工单/旧UUID。** V1=1件/2.5kg，工单冻结P100/T250；UUID-X确认20件/50kg。发布V2=1件/3kg后，新工单明确选V2才按300kg计划；旧工单追加30件仍领75kg。重放UUID-X仅读原50kg和结果，不查V2；UUID-X改30件冲突。合法撤销按冻结50kg及原动作恢复，不退60kg；不改原calculation_json/历史UUID/已发布迁移。

### 8. 准备打印→正式领料→创建/追加→追溯（R）

1. **准备并冻结。** 当前Factory/权限下选产出、计划、已发布路线及明确BOM；服务端验证唯一组件、单位、数量和来源，形成工单快照。缺依据可读草稿，禁止正式投入。
2. **预打印。** 服务端从快照唯一组件生成“工单号:材料品号#批次原文”，合成例 `WO100:A#0007#X` 的完整批次是 `0007#X`。按第一个冒号及随后第一个井号分隔，不trim/改大小写/前导零，公共ProductionCodeRules集中解析/格式化，调用方不复制。打印不创建Execution/投入/库存/永久扫码绑定，不占数量。可以领料前且尚无库存时打印，打印本身不证明库存存在。
3. **验证并选库存。** 标签材料必须等于工单冻结唯一组件，校验模式/来源/启用/权限；材料用于查库存、完整标签用于生产定位。库存批次须原文相等；符合条件一条自动选，多条由人选仓库/库位，零条拒绝，不固定原料仓或半成品仓。打印和领料分别按相应权限处理。
4. **复用执行。** 先校验材料，再按工单id＋批次原文找执行。已有执行的正式材料不同则冲突，不改绑/替料/新建第二个；相同材料换合法仓位可追加，新批次新执行。产品身份由工单确定。所有用户定位扫码走公共函数及原场景权限；已有id刷新显式读id，不伪记扫码，行为表不作为事实依赖。
5. **LOCAL成功事务。** 在现有业务Service/共同锁/Mapper体系内，同一LOCAL事务验证累计额及库存、扣库、保存领料和冻结投入，首次创建执行并绑定批次/路线，与领料成功一并提交，已有则追加。任一步失败整体回滚，首次不遗留Execution。必须消除现LOCAL两事务窗口，不能只把create语句移到方法末尾；普通Execution新增、提交及恢复入口共同遵守，不再为新业务预建空批次执行。既有执行/历史事实不因此删除或改造。
6. **EXTERNAL边界。** 先独立持久化意图、请求/用料/额度占用，可预留工单＋批次但不提前建立正式执行；外部HTTP在本地事务外。可信实际领料生效后短事务创建/关联执行和投入；UNKNOWN不增正式量、不释放占用、不盲重建、不切LOCAL。远端成功本地失败沿原provider/binding/身份人工核实恢复，不承诺跨系统原子。现submission要求execution_id，未来需连同契约调整，不能宣称现成接口可直接满足。
7. **结束后追加。** 成功新投入重算加工/入库缺口并恢复当前办理；历史报工/审核/结束事件不删除、不回滚，不清endTime或completion行冒充恢复。后续再次入库审核驱动完全入库和工单收尾；不恢复定时完结。
8. **A→B→C跨工单。** WO-B领A批次0007、产B批次0007，B审核入库动作关联E-B；WO-C冻结B→C配方，领确切B库存0007并产C批次0007。追溯为E-C→领B动作/库存→B入库明细/动作→E-B→领A事实。批次相同不等于同库存身份。若库存一行含多个上游入库动作，应展示全部可证明来源，不能假定唯一上游；来源分配需求另行明确，不偷加拆合批。查不到来源显示待确认，不按同批次猜链。

### 9. LOCAL与EXTERNAL可信依据（R）

- LOCAL：MES发布BOM→工单明确选择/冻结→现本地供料链。新业务只读数据库工单快照；工单、材料库存仓、成品仓遵守当前模式及来源一致性，不能因同编码合并LOCAL/EXTERNAL身份。
- EXTERNAL：当前Factory受管绑定的ProductionErpAdapter提供经验证的ERP工单组件、数量、单位和修订/原身份证据；优先该工单可信需求，不以MES当前默认BOM覆盖/叠加。若只给T/P，就冻结T/P及来源，不猜ERP原BOM版本/B/C。合法单组件快照进入同一生产核心。
- 真实读取仍须另行明确目标、账套/组织/环境和调用范围授权；本轮只研究公开官方资料。复用FactoryIntegrationSnapshot/Context/Registry和HTTP适配，不增ERP数据库路由、不改绑定、不并开双业务模式。缺适配/映射、多材料、零负用量、单位不明或来源冲突禁止投入，不能取首行或默认1∶1。
- 同步不得改LOCAL、已冻结需求/生产事实；ERP新计划与已投入冲突则保留事实并报冲突。UNKNOWN和历史撤销沿原来源/配置版本，不能按当前模式改派。现有接口/Mock不代表ERP仓储写已完成。

### 10. 分阶段工作包、依赖、验收与后置范围（R；全部未实施）

| 工作包 | 范围/依赖 | 验收条件 |
| --- | --- | --- |
| P0 方案定稿 | 本节讨论；明确末节少量业务选择；盘点用料/YAML/旧UUID和全写入口 | 标签材料语义、成功创建、冻结、量纲、累计占用及结束恢复为同一契约；无双权威；权限/来源/引用入口明确 |
| P1 物料与单阶BOM准备 | 依P0；复用物料/单位保护，最小版本化头/组件、发布/修订/停用及统一主子页 | 零负量、自引用、重复、多组件、停用材料/单位拒绝；多版本明确选；发布结构不可原地改，引用版本不能删；跨厂隔离，不按仓类别硬限制 |
| P2 工单快照收敛 | 依P1；复用WorkorderMaterialService职责，下达冻结，旧T/P只按原证据保留，明确YAML退役门槛 | 缺用料不能下达；打印无执行/数量/库存副作用；新版不影响旧单；未决意图阻止换依据；原UUID/撤销不回算；计划改动不静默扩额 |
| P3 LOCAL领料与执行 | 依P2；材料标签、库存选择、累计双额度、成功同事务首次创建、原执行追加和结束恢复 | 100件/250kg及双向舍入例；全工单多执行总额受限；同批并发只有一个执行；并发抢余量只合法一笔；库存不足/事务异常无首次执行和扣库残留；UNKNOWN占用不被穿透；中间意图拒绝/释放及乱序确认不改后续冻结金额，无法补差明确拒绝；不同材料同批拒绝；结束追加留旧事实生新缺口 |
| P4 追溯及完整LOCAL验收 | 依P3和现有入库审核/来源链 | A→B→C真库存链；0007及含#批次原文一致；精确到材料/仓位/动作/上游源执行，多来源可解释；草稿不入库存、审核一次记账、重放不重复；跨厂/权限/迟到响应/合法撤销保护，无定时完结 |
| P5 EXTERNAL有需求再接入 | 依P0/P2契约、真实供应商证据及单独授权 | 真目标/单位/版本来源明确；缺配置失败关闭；拒绝/UNKNOWN/远端成功本地失败保原身份，核实一次落地；无盲重建/改派/跨系统原子承诺，Mock与真ERP分列 |

P1—P4是首期LOCAL范围，P5不阻塞独立MES，也不能提前宣称ERP生产可用。未来涉及表，先正式建模、Flyway迁移、真实metadata生成基础后端；本节无执行SQL。真实数据库验收默认且仅 `mes_base_dev_perf_test`，每次重新核实Factory→启用Datasource→模式→host/port/database及实际DATABASE()、存量/冲突和范围备份；旧报告SIMPLE_B不授权切库。服务由用户重启。规则/Mock/数据库并发/API/UI/真实ERP证据分别记录。

明确后置：多阶递归/阶次缓存、多组件齐套、替代料、超领容差、损耗率、联副产、采购/MRP/成本、专门返工工单、任意单位换算、拆合批及跨系统自动补偿。后续有真实需求先明确模型和验收，不能用单材料入口取首条承接。

### 11. 用户待确认项与最小默认建议

已确认的13项前提直接执行，不重复发问；字段命名、索引细节、Service拆分、页面布局和测试组织由实施方按规则负责。

| 确需确认 | 最小默认建议 | 未确认边界 |
| --- | --- | --- |
| 是否采用下达冻结、之后才允许预打印？ | 采用；草稿可重选，下达不普通换料/比例；无事实订正明确撤回准备/重打，不把旧标签解释成新材料 | 本轮只列方案，不改当前下达逻辑 |
| 首期是否需要组件BOM单位与库存基本单位不同？哪些实际单位不可分？ | 先统一库存基本单位；材料/产品单位可以不同；沿用6位计算/新人工2位，真实需要再建换算/单位精度 | 不猜箱件等系数，不擅自设整数，也不每笔向上取整 |
| 工单曾收尾但还有计划余额时，追加命令是否同时受控恢复工单办理？ | 采用一个有权限和审计的追加命令，保留旧收尾事件；新投入产生未完成缺口，再经入库审核收尾 | 不开放普通编辑改状态，不能另建同批执行绕过 |

不需本轮决定损耗率、超领容差、替代料、成本或多阶规则；默认不启用这些能力，不擅自写入“0%”作为已确认业务事实。

### 12. 本轮文档回执

2026-10-23在现有本文追加本节并增加页首阅读入口，保留2026-10-16/20时间线。内容为当前调用链、能力矩阵、ERPNext固定版本参考、最小模型、数量例子、生产/追溯流程、分阶段验收与待确认项。

仅修改必要审查文档；未修改业务代码、测试、配置、Schema、数据库或只读来源。实际验证为源码/官方资料交叉阅读、引用文件及起始行存在性检查、数量示例Decimal复算和文档差异检查（不等于业务测试）；官方tag源码通过GitHub API读取，网页抓取个别超时/缓存未命中未作为证据，未运行业务测试或真实验收。讨论后另行授权实施，本轮不自动编码、建表、迁移、启停、调用ERP、部署或推送。
