# ProductionExecution V1 实现与验收

2026-10-14；Base 两仓库 main。本轮为新增业务；本Agent未执行提交、推送或部署，未启动、停止或重启用户服务。
按个人 Engineering Rules、项目 AGENTS 和 mes-crud-codegen Skill 实施。

## A. 实际契约审查

- 当前 `workorder`：`id/code/product_id/planned_qty/routing_id/status/revision`，状态 DRAFT/RELEASED。Service 是 `production/service/WorkOrderService.java`，实际 HTTP 为 `/api/production/workorder`。工单现有 `production_batch` 与 `batch_route_node` 管理批次和已选路径；本次不转换批次、不复用材料批次作执行身份、不接入出库确认。
- 工单的 routing_id 是现有批次的固定版本。本次 Execution 独立持有 routing_id；不同 Execution 可以与工单默认工艺不同，也可以彼此不同。本次不改工单／批次原工艺语义。工单产品变更和删除入口增加 Execution 反向引用保护，工单默认工艺变更不会改写已有 Execution。
- `routing.id` 是版本主键；另有 `code/version/status/revision/published_at`。`RoutingServiceImpl` 发布和冻结内容，`routing_product` 保存适用产品（material_id/code）；已发布版本不可按草稿删除。新建执行、纠正到另一个版本、首次开工要求 status=PUBLISHED 且 published_at 非空，产品适用关系通过 material_id 校验；既有已开始记录仍可引用曾发布后停用的确切版本。
- `mes_material_issue` 单头保存工单、产出、routing_id、flow_card_no、issue_no 与 DRAFT/RECORDED 资料状态；明细 `mes_material_issue_item` 保存领料材料、自由批次号、DECIMAL(20,6) 数量、单位和仓库。原单头工单分支以工单 routing_id 为准；本次只在 execution_id 非空时改为 Execution 工艺权威。
- 当前业务表无 factory_id，ID 为所在业务库的本地主键；同 ID 在不同工厂库中合法。HTTP 的 FactoryContext、权限和固定事务租约决定查哪一个库，不跨库搜索或比较。
- 参考项目只读核对 `ProWorkorder.java` 与 `WmIssueHeader.java` 的工单编号和领料工单关联。本次不复制 ERP 专有字段，实际实现契约以上述 Base 代码为准。
- Operation/Routing/RoutingNode/RoutingEdge 源码、模板和其发布／冻结规则未修改。入场后端干净；前端已有大量 dirty 文件，增量关联只修改已有领料单头 API/page，其余入场修改保留。

## B. 最终 Schema / 编号

正式新增迁移：`mes-admin/src/main/resources/db/business/V16__production_execution.sql`，不修改 V11/V12/V15。

| 字段 | 类型 / 可空 | 语义 |
| --- | --- | --- |
| id | BIGINT 自增主键 | 本库生产身份 |
| execution_no | VARCHAR(64) NOT NULL | 服务端生成 EXE-{id}，不可订正 |
| workorder_id | BIGINT NOT NULL | 唯一所属工单，创建后不可换单 |
| product_id | BIGINT NOT NULL | 服务端从工单确定，创建后不可更换 |
| routing_id | BIGINT NOT NULL | 当前执行的确切已发布版本 |
| target_qty | DECIMAL(20,6) NOT NULL | 正计划数量，不等于产出 |
| status | VARCHAR(20) NOT NULL，默认 READY | READY/RUNNING/COMPLETED/CANCELLED 管理资料状态 |
| start_time / end_time | DATETIME NULL | 实际开始／结束记录 |
| remark | VARCHAR(500) NULL | 备注 |
| create_by / update_by | VARCHAR(64) NULL | 服务端登录 sys_user ID 字符串 |
| create_time / update_time | DATETIME NULL | 服务端审计时间 |

共14列，每列及表提供非空中文数据库 COMMENT。除主键外仅普通索引：
`idx_execution_no(execution_no)`、`idx_execution_workorder(workorder_id)`、
`idx_execution_product(product_id)`、`idx_execution_routing(routing_id)`。
没有 UNIQUE／外键／表内工厂约束。

execution_no 不开放客户端输入；事务中先插入临时 PENDING，取得数据库自增主键，随后写入 EXE-{id} 后提交。
已提交编号的唯一性来自已分配主键，不依赖随机碰撞、JVM 锁或先查后插。临时值不对正常事务外读取可见；失败整笔回滚。
重试 POST 表示创建另一次执行，不使用参数组合 getOrCreate；已有身份必须使用返回的 ID 查询或订正，不重发创建请求冒充更新。
编号与 EXE:二维码均为当前业务库内身份，不承诺跨厂全局唯一。

V1 创建必须选择工艺，不支持未选工艺的预建身份。创建仅 READY 且时间为空；不要求先领料、设备或工作站。
允许管理员在无开始记录且无领料关联前修正 routing_id；工单／产品从创建起保持身份稳定。
已有开始时间或 RUNNING/COMPLETED 状态，或任何领料关联后，禁止换工艺。开始记录不能清空，已开始状态不能回退 READY；
RUNNING 需要开始时间，COMPLETED 还需要结束时间，结束不得早于开始。其他状态订正没有扩展为正式生产状态机。
删除拒绝已启动或有关联的 Execution；批删中任意错误整批回滚。

## C. MaterialIssue 增量

V16 为单头追加 `execution_id BIGINT NULL` 中文 COMMENT，并添加普通 `idx_issue_execution(execution_id)`。
旧记录保持 NULL，routing_id、flow_card_no 和明细结构保留。

- 非空 Execution 必须存在于当前请求固定的业务库；使用生成 Mapper 的同值 UPDATE 在同一业务事务中锁定 Execution。
- 客户端提供的工单／产品／工艺若与 Execution 冲突则拒绝；省略时由 Execution 明确填入。工单编码、产品编码继续从实际引用记录取得。
- Execution 工艺不要求等于工单默认工艺；不从其他表选择最新版本。
- 空 execution_id 保留既有管理员资料入口：有工单时仍使用该工单绑定的具体版本，无工单时按原显式引用规则校验。
- 已关联单头不能换 Execution、解除关联或直接物理删除，避免抹掉执行事实引用后绕过冻结。可订正其他合法资料及正计划数量；无执行关联的旧资料仍按原规则删除（有明细时仍拒绝）。
- Execution 删除、领料关联新增／订正共用父行锁；WorkOrder 变更／删除与创建 Execution 共用工单锁。锁使用生成 Mapper/Example，不新增单表 Extra SQL。
- 同步适配与 ERP 操作未新增；领料不会自动开工或推进状态。

## D. 身份查询接口与示例

权限资源 `business:mes:production:execution`，独立 list/query/create/update/delete。
CRUD `/api/production/mes-production-execution` 使用生成 Controller，单查／更新 `/{id}`，删除为 ID 数组。
身份查询使用相同 query 权限：

```http
GET /api/production/identity?code=EXE:100001
Authorization: Bearer <已有会话>
X-Factory-Id: SIMPLE_B
```

以下是契约示例，不是真实验收库业务记录：

```json
{
  "executionId": "100001", "executionNo": "EXE-100001", "qrCode": "EXE:100001",
  "workorderId": "2001", "workorderCode": "WO-2001",
  "productId": "301", "productCode": "PART-301", "productName": "传动零件",
  "routingId": "401", "routingCode": "ROUTE-301", "routingVersion": 2,
  "status": "READY", "targetQty": "100.000000"
}
```

创建示例：`{"workorderId":"2001","routingId":"401","targetQty":"100","status":"READY","remark":"验收样本"}`。
关联领料示例：`{"issueNo":"QA-ISSUE-1","executionId":"100001","status":"RECORDED"}`。

解析协议只接受 `EXE:[1-9][0-9]{0,18}` 且不超过 Long.MAX_VALUE；不忽略大小写／空格，不接 ERP 材料码映射。
错误使用现有 `errorCode/message/requestId` 协议：
格式错误 400/EXECUTION_QR_INVALID；当前库不存在 404/EXECUTION_NOT_FOUND；无权限 403/FORBIDDEN；
引用／数量／时间错误 422/EXECUTION_INVALID；历史变更 409/EXECUTION_FROZEN；删除已引用 409/EXECUTION_REFERENCED。
缺工厂继续由基础设施返回 422/FACTORY_SELECTION_REQUIRED。

ProductionIdentityService 仅只读事务和生成 Mapper 查询；Controller 白名单显式把 ID、数量转字符串，工艺版本数字保持数值。
扫码不调用任何写 Mapper、不决定业务动作、不产生领料／报工／入库。前端提供只读“扫码查询／身份码”弹窗，
切厂、撤权和卸载清空结果并拒绝迟到响应。未接摄像头权限或二维码图片生成。

## E. Codegen 与 Extra Code

已读取 `.agents/skills/mes-crud-codegen/SKILL.md`、使用说明和用户当前项目配置。
使用专用显式配置 `docs/development/production-execution-codegen.yaml`，沿用已核对 source 和项目参数，
只选 mes_production_execution、all产物、generateSync=false、write/fail。用户原全量配置未修改。

生成报告 `target/production-execution-codegen-report.json`：GENERATED，一表、21产物。
包括 Entity/Example/Mapper/XML、Create/Update/Query/Page/Resource、Service接口与实现、Controller/权限/VO、
前端 API/页面/菜单JSON、菜单 SQL／权限说明、两项基础 scaffold 测试。
MaterialIssue 在隔离 target 路径仅重新生成单头 persistence 并审查复制，原手写业务层保留并增量适配，明细未覆盖。

额外代码／后处理：

- `ProductionExecutionRules`：引用、正数量、时间、事实冻结、父行锁和审计；生成 ServiceImpl 调用规则并形成稳定编号。
- `ProductionIdentityService` + `ProductionIdentity`、Controller/VO：单一只读身份查询。
- 生成输入删除 executionNo/productId 两个服务端权威字段，前端只读展示；不建立匿名Map或隐式全局响应转换。
- MaterialIssue 单头 Entity/Example/XML 生成 executionId，Controller/VO/输入/查询/Service/Rules 追加关联；UI API和表单追加可空执行ID。
- WorkOrderService 及原预算测试注入 Execution Mapper，保护工单删除和产品身份变更。
- ErrorCode 增加五个明确执行错误；菜单文档对齐既有“生产基础”目录。
- `ExecutionIdentityLookup.vue` 与 productionIdentity.ts、四项前端行为测试。
- HTTP双库、MySQL回滚验收和工单反向保护测试。

未新增手写持久化 Mapper/DAO/Repository、Facade、规则引擎、Handler注册中心或第二套 CRUD。所有单表 SQL 使用生成接口。
手工变更的生成文件移除 MES-CODEGEN 所有权；这些文件再次生成不同内容会 OUTPUT_CONFLICT，不能恢复标记覆盖。
纯生成文件保留生成归属；格式化使用项目 formatter。菜单SQL仅生成，没有执行、迁移中心库或赋予任何角色权限。

## F. 实际验证与状态

目标库绑定已核对：Factory SIMPLE_B，datasource_id=8，192.168.18.25:3306/mes_base_dev_business_simple_b，
Factory/DataSource 都 ENABLED。V16前该库版本15、领料单头1条、工单8条、无执行表。
受影响领料单头已用 mysqldump single-transaction 备份到 `.local/production-execution-20261014/material-issue-before.sql`，
未清空、覆盖或迁移用户已有业务数据。Flyway只执行该库V16并validate成功；没有另建验收库或工厂。

| 验证 | 实际结果 / 边界 |
| --- | --- |
| 基线 `mvn -B -ntp verify` | BUILD SUCCESS，生成前最新CLI |
| Flyway filesystem V16 + validate | SCHEMA_READY，指定SIMPLE_B真实库 |
| `scripts/mes-business-codegen docs/development/production-execution-codegen.yaml target/production-execution-codegen-report.json` | CODE_GENERATED，一表21产物 |
| 双库 HTTP Fixture | 3项 ProductionExecutionHttpTest + 原2项MaterialIssueHttpTest通过；真实生成XML和事务，合成身份／H2，不是真登录服务 |
| `MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify` | BUILD SUCCESS；401 run / 0 failure / 0 error / 11 skipped；两项LIVE MYSQL测试使用指定库，显式回滚 |
| Java formatter与外部类型引用检查 | 本轮38个Java文件；无全限定类型代替import |
| Node22 `npm test` | 34文件／185测试通过，含4项执行UI测试；jsdom |
| Node22 `npm run build` | vue-tsc与生产构建通过；既有大chunk警告 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 10通过／0失败；隔离打包启动，不是真实模块业务验收 |
| 两仓库 diff --check | 通过 |
| 本轮前端定向 ESLint | 通过；不格式化任务外dirty文件 |
| 现有5173真实代理 | 工厂上下文／既有工单200；本轮两个新接口404/NOT_FOUND |
| 现有浏览器 | 当前账号生产菜单只有生产工单；新菜单未安装／授权，不能作新页面验收 |

测试覆盖：相同工单参数创建独立身份、不同确切发布版本、未发布／不适用／首次开工时已停用版本拒绝、
前置工艺订正、多领料关联、工单／工艺冲突拒绝、空执行兼容、执行／工单越库ID拒绝、两个库本地主键重合合法、
稳定编号、正数量与6位精度、开始记录保护、单删／批删原子拒绝、query/write权限负例、缺工厂、
二维码格式／超界／不存在、扫码前后执行表完整相同且无新增领料、切厂／撤权后迟到响应拒绝。
真实MySQL复用既有有效工单与已发布工艺，只用同值锁不更改其数据；所有本轮业务测试记录回滚。
没有留下用于用户真实服务复验的合成业务结果，因此不将回滚测试称为完整业务验收。

开发中曾修正错误响应断言字段（code改为errorCode）；后增负例测试有一次括号语法编译失败，修正后重跑。
失败不隐藏，最终通过命令与边界以verification JSON为准。

状态：SCHEMA_READY / CODE_GENERATED 已达到；API_VERIFIED / UI_VERIFIED / BUSINESS_VERIFIED 未达到。
自动化 HTTP/H2、指定 MySQL 和 jsdom通过不替代这三个真实服务状态。

## G. 未完成范围、风险与复验

- 未开发完整领料、正式启动入口、工序报工、入库、数量汇总、动态路径执行、规则框架、ERP、SN、质检或返修。
- 没有Report/Receipt表，未添加虚假关联查询。引入这些事实表时必须同任务补上删除／订正／并发保护。
- 用户现有后端未加载新能力。按项目第10节“不得自行启动、停止或重启用户服务”，必须由用户加载新构建后继续真实API/UI验收；本轮未重启。
- 菜单SQL需要独立审查应用到已有中心库；管理员角色须显式授予五项权限，生产读角色授予list/query。没有自动给账号扩大权限。
- 在SIMPLE_B真实服务中，用明确合成标签创建多个Execution并关联领料，随后通过身份查询／列表／UI读取，记录数据与结果，才可以称BUSINESS_VERIFIED。
  后续测试写入前必须检查编码冲突与备份，不删除用户原数据。有关联测试身份的物理删除被保护，不应绕过业务规则清理。
- 新页面需真实1440/1920/1280、错误、删除取消和权限／切厂检查；当前只完成构建和jsdom行为测试。
- 同值UPDATE用于生成Mapper行锁，依赖项目既有MySQL“匹配行数”行为。HTTP/H2与指定MySQL通过；尚未做新链路多连接压力／死锁恢复验收。
- 其他工厂库尚未执行V16；切换到未迁移业务库可能缺表。应在对应库按正式迁移部署后使用，不回退中心或其他工厂库。
- 手工生成文件保护意味着未来全量replace-generated可能冲突；需审查补丁或显式重新移交所有权，不能自动覆盖。
- 当前直接管理CRUD没有完整状态机／操作日志；状态与日期订正权限应只授予管理员。工单产品从Execution创建起冻结，强于“有事实后冻结”的最低要求。

回退必须单独处理源码与已执行Flyway，源码删除不能回退已建表；没有篡改history、自动drop表或危险清理。

## H. Reporting / Receipt 接入要求

后续单据或生产事实直接保存当前库 `execution_id`，按当前FactoryContext查Execution，再取其workorder_id/product_id/routing_id。
工序报工必须验证节点属于该确切routing_id，不读取工单默认或最新工艺；实际产出／报工／入库数量各自保存事实，不回写target_qty冒充完成量。
需要消耗关联时另外引用真实material_issue_id/item_id，不把材料批次或issue_no作为Execution身份。
一个Execution可关联多次报工和入库；不按SN强拆身份，不要求先领料。
新增事实写入和Execution删除／身份订正必须复用同一执行父行锁，新增反向引用保护并覆盖并发／权限／回滚负例。
二维码只查身份，具体业务入口自行授权和校验，不由码前缀自动执行流程。

## Change Summary / Document Created

新增最小生产身份CRUD、稳定编号、领料可空关联及只读扫码查询；保护历史身份及现有工单产品。
数据库仅指定SIMPLE_B追加V16；未执行菜单／授权、未部署。前端旧dirty工作和另一Agent工艺源文件保留。
本报告提供A-H审查、模型、接口、生成／额外代码、测试与复验边界；verification JSON保存最终状态和本轮文件哈希。

## 工作区状态补充

本Agent未执行任何git提交／推送。入场前端有大量未提交修改；任务期间前端变为干净，新增执行UI文件已纳入当前HEAD `205f40b`（2026-10-14 08:26:13，提交标题init）。未重置、改写或撤销该提交；不能将前端当前状态报告为仍全部未提交。后端本任务改动仍保留在工作区。

## 后端重启后的验收接续

用户选择自行重启并配置菜单权限后继续验收。现有两个新增接口已从404变为403，说明新代码已加载。
`/api/session/info`确认验收会话为admin，`/api/factory/navigation`返回的Execution权限仍为空；目前等待用户权限配置，未发起业务写入。
所需权限为business:mes:production:execution:list/query/create/update/delete；扫码复用query。
菜单文件为`mes-business/docs/generated/production/production-execution-menu.sql`；本Agent不代替用户执行菜单或赋权。
测试专用接续脚本`.local/production-execution-20261014/live-acceptance.py`已准备，使用已有会话／SIMPLE_B，
运行前会核对绑定、读取可用工单和两个发布版本、检查合成标签冲突，再创建独立执行与领料供用户复验。
无Execution的管理员领料样本、有关联事实的历史保护和扫码只读均有实际HTTP断言；结果写入同目录live-acceptance-result.json。
用户权限配置完成前不把403转换为成功，也不绕过后端授权。
本次后续复查前端出现`src/views/production/workorder/index.vue`新的并行未提交修改，本Agent未修改该文件。


## 生产主链收敛（方案 A）实施报告 — 2026-10-14

本节覆盖前述 V1 审查中 WorkOrder/ProductionBatch 仍为活跃写入模型的结论。当前实施以 Execution 为唯一活跃执行身份。
本轮未提交、推送、部署或重启用户服务；并行任务提交导致仓库 HEAD 变化，不属于本 Agent 的 Git 操作。入场快照和修改前文件保存在 `.local/production-chain-20261014/`。

### A. 修改前后模型

| 对象 | 修改前 | 当前职责 |
| --- | --- | --- |
| WorkOrder | 必填 Batch，批次数量合计必须等于计划数量，共用工艺 | 独立生产需求：产品、计划量、日期、已发布默认工艺、DRAFT/RELEASED；允许零 Execution |
| ProductionBatch | 分批、选线性路径、出库尝试和锁定 | 仅保存历史资料，不进入新执行主链 |
| BatchRouteNode | 保存旧批次已选线性路径 | 只读历史，不转换为执行节点或完整 DAG |
| ProductionExecution | 与旧批次并存 | 唯一活跃执行身份，自己的确切工艺版本权威 |
| MaterialIssue | 已有可空 execution_id | 延续 V1 规则；关联执行时从 Execution 确定工单、产品和工艺，历史空关联仍可维护 |

### B. 实际代码变更

- `WorkOrderModels`：Save 移除批次命令；Summary/Detail 增加 Allocation 查询模型。删除退役的 BatchInput/IssueResult。
- `WorkOrderSaveRequest`：Web 边界显式识别旧 batches 字段（包括空数组或显式 null），返回 410 LEGACY_BATCH_READ_ONLY，避免静默丢失旧请求数据。
- `WorkOrderService`：create/update/release 不创建或推进旧批次；工单删除遇历史批次拒绝；历史批次存在时保护原产品与默认工艺。保留版本 revision 与行锁。删除未接入真实业务的 beginIssue/confirmIssue 和旧选路写入规则。
- `WorkOrderExtraMapper`：真正的 WorkOrder LEFT JOIN Execution 汇总，一页工单只增加一次批量聚合查询，沿用已有 Mapper，无单表 Extra。
- `WorkOrderController`：返回上述统计，并提供 `GET /api/production/workorder/{id}/allocation`，使用 workorder:query 权限；数量按十进制字符串返回，本地主键按字符串返回。
- `ProductionExecutionRules`：普通更新到 RUNNING/COMPLETED 或设置开始时间前检查所属工单 RELEASED，READY 预建不受此限制。
- `ErrorCode`：补充未下达、旧模型只读、历史引用保护错误。
- Codegen 项目配置全量排除 production_batch/batch_route_node，防止生成重新开放旧写入。生成器本身未重构。
- 删除不再表达当前业务的 BatchPathPolicy 与测试；新测试保留权限、两库隔离、并发 revision、真实 SQL、事务回滚和历史数据保护。

### C. Schema

本轮无需新增 Flyway：现有 workorder、mes_production_execution、mes_material_issue.execution_id、旧历史表及索引足够支持查询与关联。未修改 V13/V14/V15/V16，未调整工艺表，不做批次到 Execution 的自动迁移。

重新核对验收目标：SIMPLE_B → datasource 8 → 192.168.18.25:3306/mes_base_dev_business_simple_b，工厂和数据源均 ENABLED。09:46 左右实查为 WorkOrder 10、旧 Batch 20、旧路径 48、Execution 0、MaterialIssue 2；当前没有留下本轮合成生产记录。

### D. 旧 Batch 兼容

- 工单详情中的 batches、`GET /api/production/batch/{id}` 保留，只读历史。
- 旧路径 PUT 兼容入口保留权限与当前库存在性校验，然后明确拒绝 410，不能推进路径或身份。
- create/update 输入 batches 明确拒绝；无隐式“双写”、getOrCreate、历史状态推断或身份映射。
- 删除存在旧批次的工单返回 409，保护历史；ISSUED/LOCKED 不表示新的 Execution RUNNING/COMPLETED。
- 真实 MySQL 回滚测试逐行比对旧 Batch 和路径，没有更新或删除。

### E. Execution 业务规则与查询

- 同工单多 Execution，允许不同已发布适用产品工艺；编号由已有 EXE-{id} 机制稳定生成。
- 默认工艺变更不改写 Execution；实际 routing_id 不自动选最新版本。已开始或存在领料关联的冻结规则继续有效。
- DRAFT 允许 READY，正式开始要求 RELEASED。无需先领料；本轮没有正式报工准入或生产状态机。
- `allocatedTargetQty = SUM(target_qty)`，`remainingTargetQty = planned_qty - SUM(target_qty)`，`excessTargetQty = MAX(SUM(target_qty)-planned_qty,0)`。当前统计全部 Execution 状态（含 CANCELLED），明确为目标量统计；没有实际完成量语义。
- 1000 计划、400+300 目标：700/300/0；400+900：1300/-300/300。统计同时出现在列表、详情及独立查询。
- V1 二维码解析保持 `/api/production/identity?code=EXE:{id}`、query 权限和当前业务库查询；扫描无业务写入。

### F. 前端收敛

- WorkOrder 页面只保留需求字段；无需 Batch 可提交。默认工艺明确标注；列表和详情显示分配、带符号剩余和超额目标量。
- 详情嵌入 `ProductionExecutionList`，父工单通过既有 CrudPage fixedValues 固定；独立执行页面复用同组件和同 API/fields，避免两套 CRUD。
- WorkOrderSelect 支持有权限的工单搜索、缺 list 权限时的显式 ID 输入；切厂/权限变化清空，拒绝迟到响应。实际工艺列与默认工艺区分。
- 历史批次折叠区清晰标为“历史只读”，无新选路和分批按钮；旧锁定记录只按历史解释。
- MaterialIssue 的已完成主子单据改动和共享组件没有被本轮覆盖。

### G. 验收证据

实际命令：

```sh
MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true
# mes-base-ui，使用 .nvmrc 已安装 Node 22.23.1 的 PATH
npm test
npm run build
/usr/bin/python3 scripts/smoke-packaged.py
```

- Maven 最终 BUILD SUCCESS（09:45:02）：398 项，0 failure、0 error、10 skipped。包含 SIMPLE_B 真库 WorkOrder、Execution、MaterialIssue 三个回滚测试；含两库 HTTP Fixture、并发订正 revision、拒绝旧写入、版本权威、DRAFT 开工拒绝、数量汇总和历史保护。
- 第一次完整检查发现测试取 JDK 事务代理实现类错误，改为接口取 Bean 后重跑通过；不属于业务失败。前期定向检查暴露的 Jackson 3 JSON 类型和 Fixture 统计精度问题已修复。
- 前端 36 个测试文件、189 项通过；vue-tsc 与构建通过。只保留既有大 chunk 警告。
- packaged smoke：10 passed、0 failed，启动的是脚本自有随机端口独立实例，未操作用户服务。
- Java formatter 已用于本轮 13 文件；git diff --check 通过。
- 实际重新登录 admin 成功，现有 Execution list 200；新增 WorkOrder allocation 404，用户服务仍为本轮修改前构建。已请用户按项目规则自行加载新构建后继续真实 API/UI，未用 Fixture 提高验收等级。
- TEST ONLY 真实服务验收脚本已准备，先检测新接口，再写唯一标签的合成工单/执行/领料；备份并复核历史批次路径，保留复验数据。当前未执行写入。

### H. 风险与后续工作

本次必须完成的余项：用户服务加载最新构建后，真实登录环境复验创建工单、Execution、领料、扫码与 UI；当前不宣称完成。

Reporting 开发时：引用 execution_id，校验当前库关联、RELEASED 工单和确切有效工艺；工序节点必须属于 Execution.routing_id 的 DAG，明确并行及可选分支语义。Report/Receipt 事实追加身份锁定与删除保护，不伪造不存在的关联校验；按 sys_user 保存报告人/操作者标识。

低优先级：是否让 CANCELLED 不计入目标分配须另行明确产品规则；历史批次归档/删除表需要单独迁移及真实数据依据；可改善执行列表工单/产品/工艺名称展示，目前保留生成 CRUD 的稳定 ID 契约。当前没有扩展 ERP、生产完工汇总或运行引擎。

### I. 最终状态（等待用户服务复验）

| 状态 | 证据与结论 |
| --- | --- |
| SCHEMA_READY | 是，复用已应用 Schema，无新增迁移 |
| CODE_GENERATED | 复用此前 V1 生成的 Mapper/CRUD；本轮无新表，未重复生成 |
| API_VERIFIED | 本轮真实用户服务未验证；两库 HTTP Fixture 通过不等于此状态 |
| UI_VERIFIED | 本轮真实浏览器业务未验证；189 个 jsdom 测试与构建通过 |
| BUSINESS_VERIFIED | 部分：指定真库 Service/SQL 与回滚验证通过，真实服务/UI 端到端尚未完成 |


### 用户重启后的真实验收补充 — 2026-10-14 11:09～11:18

用户确认已重启后继续验收，未由 Agent 操作用户服务。本补充替代上节 I 的待验收状态，保留此前 404 的时间线。

- 重新登录 admin，当前工厂 SIMPLE_B；再次核对 datasource 8 / 192.168.18.25:3306 / mes_base_dev_business_simple_b。
- 执行 `python3 .local/production-chain-20261014/live-acceptance.py`：33 次真实 HTTP 请求按预期通过，包括 200 正例及 400/404/409/410/422 负例。启动后旧会话返回 401，重新登录后测试通过；没有跳过授权。
- 真正创建无 Batch 的工单、同工单两个独立 Execution（routing_id 64/65），分配 400+300=700 后订正为 400+900=1300，剩余 -300、超额 300。DRAFT 开工拒绝、下达后无领料开工成功。
- Execution 16 关联两张领料单，另建 execution_id=NULL 的兼容资料。工艺矛盾拒绝，关联执行删除与换工艺拒绝；未发布工艺和不存在的工单拒绝。
- EXE:16 三次身份查询返回正确工单、产品、确切工艺、状态；执行与领料记录查询前后一致。错误格式 400/EXECUTION_QR_INVALID，不存在 404/EXECUTION_NOT_FOUND。
- 历史 production_batch/batch_route_node 备份保存于 `.local/production-chain-20261014/legacy-before.tsv`，API 与浏览器操作结束再次逐行比较完全相同（20/48）。未改变任何工艺定义。
- 真实浏览器使用用户现有 5173：登录、菜单、工单查询及关联 Execution 列表、1300/-300/300 统计、身份码查询成功；错误格式清除旧结果并显示错误。新增工单实际表单提交成功，计划 12.000001，0 Batch/0 Execution，数据库复核一致；删除确认取消后资料保留。
- 历史工单 TEST-WO-UI-20261014 折叠区只读展示旧批次19001/19002和原路径；没有旧选路写入按钮。切 DEV 后返回工作台，无旧工单内容，随后恢复 SIMPLE_B。
- 浏览器只验证 admin 的上述用例；低权限账号、完整响应式尺寸矩阵、浏览器创建/订正 Execution 或领料未逐项重跑。权限负例、两库隔离、迟到响应与写入保护分别由前述 HTTP Fixture/前端自动化/真实 API 覆盖，不扩大浏览器证据。
- 当前扫码页面错误格式提示沿用统一错误消息“输入不符合要求，请检查字段”；后端 errorCode 明确，前端更具体的中文提示可作为后续体验改进，本轮无新增二维码映射。

保留合成验收数据（仅指定 SIMPLE_B）：

| 记录 | ID/编号 | 状态与目的 |
| --- | --- | --- |
| API 工单 | 18 / QA_CHAIN_20261014_b6251f91 | RELEASED，计划1000，0旧Batch/2Execution |
| Execution | 15 / EXE-15 | RUNNING，实际routing64，目标400，无领料也可开工 |
| Execution | 16 / EXE-16 | READY，实际routing65，目标900，两张领料关联 |
| 领料单 | 19、20 | 测试标签_A/_B，关联Execution16 |
| 兼容领料单 | 21 | 测试标签_LEGACY，execution_id NULL |
| UI 工单 | 19 / QA_CHAIN_UI_20261014_b6251f91 | DRAFT，计划12.000001，0Batch/0Execution |

没有自动清理或删除测试记录，保留给用户复验。测试脚本每次运行创建新身份，不可将重复运行当作对既有身份的更新。

证据目录 `.local/production-chain-20261014/`：`live-acceptance-result.json`（全部请求、状态和保留ID）、`ui-acceptance-result.json`（浏览器验证范围）、`verification.json`，截图 `ui-workorder.png`、`ui-identity.png`、`ui-created-demand.png`、`ui-legacy.png`。这些本地证据不包含登录凭证；会话文件另存，仅本地使用。

| 最终状态 | 本轮证据 |
| --- | --- |
| SCHEMA_READY | 是，既有 Schema 足够，本轮零迁移 |
| CODE_GENERATED | 复用 V1 生成产物，本轮无需重新生成 |
| API_VERIFIED | 是，指定环境真实登录33次请求通过 |
| UI_VERIFIED | 是，限上述真实浏览器用例；其余范围明确未逐项重跑 |
| BUSINESS_VERIFIED | 是，限本轮生产身份及主链收敛范围；未涉及正式报工/入库/ERP |

下一阶段仍按 E/H 的 execution_id、确切 Routing DAG、RELEASED 与事实锁定规则接入 Reporting/Receipt。本轮没有剩余必须修复的主链问题，后续业务及体验限制如上。


### 2026-10-14：本轮完整生产主链只读审查

新增[Report B](2026-10-14-production-chain-audit-review.md)，覆盖最终WorkOrder/Execution/MaterialIssue/Routing及ERP Extra。旧报告“后续Reporting”的安排需按本轮确认的完整路径语义调整：优先正式领料、选路/锁路及开工准入，再做路径内报工；不否定此前身份主链验收，也不把旧API/UI结果当作本轮V22验证。Phase B没有实施建议的生产业务改造。
