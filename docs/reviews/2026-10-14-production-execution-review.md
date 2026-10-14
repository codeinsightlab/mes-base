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
