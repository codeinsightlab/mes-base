# 单材料扫码投产 V1：独立 MES 与可选 ERP

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
