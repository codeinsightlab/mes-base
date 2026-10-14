# 领料出库模块审查与基础建设报告

2026-10-13；目标分支 main。SOURCE_PRODUCT_REVIEW + 基于事实的新核心模型；用户本轮明确要求 Codegen，优先于迁移阶段通常 COPY/PORT 的规则。本轮未提交、推送或部署。Source Product 三仓库严格只读；未访问源数据库或调用 ERP。

## 结论与状态

两表核心、真实开发库建表、生成基础后端和前端、必要关联规则、构建及基础验收已完成。**SCHEMA_READY / CODE_GENERATED**。

**API_VERIFIED=false / UI_VERIFIED=false / BUSINESS_VERIFIED=false**：MockMvc 与真实 MySQL Mapper 测试不能替代登录后的真实服务 API；Vitest 不能替代真实浏览器。菜单 SQL 只生成、未执行，没有新增角色授权。其他 Factory 业务库尚未应用 V11/V12。完整生产领料入口、报工、库存扣减和 ERP 命令均不在本轮范围。

机器可读证据与生成／最终文件校验值：`docs/reviews/2026-10-13-material-issue-verification.json`。可复用来源线索来自旧审查记忆，本轮已重新读取当前源码；源系统运行行为未重新验证。

## A. 源系统审查

以下均为当前源码／SQL 文件事实，不能代替真实部署数据库或 ERP 行为。

### A1. 普通 PDA 5600

`heri-uni-app/pkg-issue/pages/issueReceipt/index.vue:249` submit → `api/issueReceipt/issueReceipt.js:3` → POST `/mes/oapi/issueReceipt/insert5600/{docNo}/{itemCode}/{itemLotCode}/{qty}`，query 提供流程卡和路线，body 提供配件 → `ktg-mes/src/main/java/com/ktg/mes/erpapi/controller/IssueReceiptController.java:64` → `IssueReceiptServiceImpl.insertDocNo5600Merge:1152` → `checkAndInsertBuckle` → `ProBuckleServiceImpl.insertBuckle:123` → `ProBuckleMapper.xml` → `docNo5600Merge:248` → `OApiService.ISSUE_RECEIPT_CREATE` → 可选确认。

- 主料与配件分别写 `pro_buckle`，不是完整的本地领料单头。`material_doc_no` 是工单号，不是 ERP 5600 单号。
- 材料品号由材料输入及 ERP MoD 核对；本地名称／规格来自 MdItem；产出品号从 ProWorkorder.productCode 取得。不能将两种品号合并。
- `IssueReceipt` / `IssueReceiptD` 分别表达 ERP `ISSUE_RECEIPT` / `ISSUE_RECEIPT_D`；`Mo` / `MoD` 表达 ERP 工单单头／材料。D 保存材料、材料批次、领料量、单位、仓库、工单关联及流程卡。
- 领料 qty 为提交输入。主料仅在 qty>0 时进入 ERP 明细，配件逐项添加；旧本地 checkAndInsertBuckle 与此非正数量分支不完全一致，不能迁移该历史约定作为新核心规则。
- 支持分次投料，检查既有材料／批次／工单及报工完结状态；调用时流程卡与材料批次有混用迹象，不能将其当成 Base 的材料批次全局唯一规则。
- 页面根据工单产品查询路线并选择 routeId；旧 routeId 没有 Base 已发布版本语义。
- 方法本地 @Transactional 不包含 ERP HTTP。外部建单成功后本地异常仍可能回滚；旧异常／补偿不能证明 ERP 已回滚。
- 外部 MES syncCreateLotProcess 被 catch/log，是客户集成，不能迁入本轮。

源码 Schema：`scripts/mf-upgrade/sql/009-new-factory-business-init.sql:1590`。pro_buckle 包含 id、工单、材料品号、材料批次、流程卡、route_id、quantity DECIMAL(20,6)、产出品号、材料描述、审计及预留字段；只有主键，没有材料批次唯一约束。

### A2. 轮毂 PDA 5650

带特殊扫码格式的轮毂页面 → `ktg-wheel/.../controller/web/WheelWmIssueReceiptController.java` → `WheelIssueReceiptServiceImpl.insertErpAndLocalIssueReceipt:671` → docNo5650 → insertMaterial:627 → `WheelProMaterialMapper.xml`。

源码先建 ERP 5650，遇异常按工单／流程卡查询已有外部单据，随后写本地 pro_material。该表有 issue_doc_no、工单、流程卡、路线、当前工序、JSON detail；每条明细含材料、材料批次、实际选料量和单耗。源 Schema `009-new-factory-business-init.sql:1773` 的 issue_doc_no 有既有 UNIQUE。Base 不复制其 ERP 单号唯一性或 JSON 明细，不迁入 firstProcess／报工材料初始化。

### A3. 标准仓储领料单

`ktg-mes-ui/src/views/mes/wm/issue/index.vue` + `line.vue` → `src/api/mes/wm/issueheader.js` / issueline.js → `WmIssueHeaderController` / `WmIssueLineController` → 对应 Service／Mapper。

`WmIssueHeader` 保存工单、工作站、任务、客户、仓库、单据日期及状态；`WmIssueLine` 保存材料、quantityIssued、批次、单位、仓库、库区库位等。Schema 位于 `009-new-factory-business-init.sql:3483/3521`。Controller 的删除编排和 execute 库存事务是历史设计，本轮只提取单头／明细、数量、单位和仓库语义，不迁入库存流水、库位、客户或任务。

### A4. 生产确认中心领料

`ktg-mes-ui/src/views/mes/pro/materialIssueConfirm/index.vue` / `components/MaterialIssueConfirmForm.vue` → `src/api/mes/pro/materialIssueConfirm.js` → `ProductionMaterialIssueConfirmController` → `ProductionMaterialIssueServiceImpl`。

- create:128 通过工单、适用路线、材料需求及提交的批次创建本地 `pro_material_issue_confirm/detail`。
- calculateMaterialRequirements:359：读取 ERP 工单材料总需求，`qtyPer=totalRequiredQty/workorder.quantity`（12位），`issueQty=totalRequiredQty*outputQty/workorder.quantity`（6位）。`outputQty` 是预计产出；不能视为实际报工数量。
- approve:192 写扣料记录，创建 ERP 5600，再读取不可变外部单头 ID、保存外部核对和审批状态。该流程具备条件抢占、失败记录及撤销／删除核对，超出本轮基础 CRUD。
- `validateRoute:481` 校验路线属于当前工单产品；这是可复用关联规则，Base 引用发布版本时复用 routing_product 冻结品号进行匹配。
- source 同批次约束和按工单需求必须提交全部材料是该入口规则，不能无条件推广为基础领料资料约束。
- Schema：`sql/mes/production-confirm.sql` 与 `009-new-factory-business-init.sql:1786/1820`。主表有请求号、工单、产出、路线、流程卡、预计产出量、审核与外部身份；明细有材料、批次、单耗、issue_qty DECIMAL(18,6)、单位快照。

### A5. 报工、生产入库和撤销

旧报工 `ProReportingWorkServiceImpl.selectReportNumberByStatus:1684` 按材料＋批次查扣料取得产出品号，再查报工状态；`checkBuckleUnfinishedStatus:1855` 按工单、产出品号和批次／流程卡查完结状态。报工量来自报工记录，扣料量是投入量，两者不是同一个事实。

新生产入库 `ProductionInboundConfirmServiceImpl.create:142` 接收 materialIssueConfirmIds，校验领料确认已审核、同工单、外部身份有效、尚未被占用，并条件绑定入库确认；删除时释放关联。该路径提供了明确的本地单据 ID 引用证据，不能声称所有旧入库都使用这种关系。

源存在反审核／删除 ERP、重复领料查询和配件补领行为。是否允许退料、可领／可报数量、单号分配及批次扫码规则仍取决于具体 ERP／部署配置；本轮没有运行证据，不能作为 Base 固定规则。

### A6. 分类结论

| 分类 | 结论 |
| --- | --- |
| 源码已确认 | 多入口并存，工单→产出产品与材料不同，材料批次为普通业务数据，重复材料批次可存在；新确认中心已有单头／明细与入库 ID 关联 |
| 推断／未证实 | 当前现场优先使用哪个入口、ERP是否推进工单状态、外部批次／单号实际分配与权限、真实源数据库结构和数据 |
| 不迁移 | ERP内部ID／状态、5600／5650类型码、仓库特殊编码、外部MES同步、JSON明细、报工初始化、流程卡数量算法和审批补偿 |
| 通用能力 | 单头／材料明细、工单与产出区分、普通批次字符串、数量与单位、仓库引用、同厂边界、确切工艺版本、服务器审计 |
| 后续执行模块 | 扫码、需求量计算、工序进度、报工引用、实际入库引用与历史保护、外部幂等和UNKNOWN恢复 |

## B. Base Schema 与依赖

Base 使用 MySQL 8.2，中心库＋按 Factory 独立业务库。读取 `V2__simple_business_capabilities.sql`、`V3__operation_routing.sql`、现有 ProWorkorderMapper/XML、`V8__warehouse_inventory.sql` 与当前 Routing Service 后确认：正式物料表 material 同时承载材料／产品；单位 unit_measure；工单 pro_workorder 现有量为 Double 且含旧ERP字段，本轮不修改；仓库 wm_warehouse；routing.id 是确切版本，published_at 表达曾发布。RoutingNode/Edge/Operation 并行改动未修改。

本轮两个 Factory 字段由后端上下文填写，客户端输入契约不接受；物理分库和表内 scope 双重边界。pro_workorder 无 factory_id，关联校验使用当前固定业务库，不造额外 Factory 字段。

### mes_material_issue

| 字段 | 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 领料出库单本地主键 |
| `factory_id` | `VARCHAR(64)` | 否 | 所属工厂标识，由服务端当前上下文填写 |
| `issue_no` | `VARCHAR(64)` | 否 | MES领料出库单编号，不代表外部单据主键 |
| `workorder_id` | `BIGINT` | 是 | 本地生产工单主键，逻辑引用当前业务库工单 |
| `workorder_code` | `VARCHAR(64)` | 是 | 生产工单编号快照，有本地工单时由服务端填写 |
| `product_id` | `BIGINT` | 是 | 产出产品主键，逻辑引用本厂物料产品资料 |
| `product_code` | `VARCHAR(64)` | 是 | 产出品号快照，不等于明细材料品号 |
| `flow_card_no` | `VARCHAR(128)` | 是 | 来源流程卡号，普通业务字符串，不自动生成 |
| `routing_id` | `BIGINT` | 是 | 执行工艺确切版本主键，引用已发布的路线版本 |
| `issue_time` | `DATETIME` | 是 | 领料业务发生时间，空值表示尚未提供 |
| `status` | `VARCHAR(16)` | 否 | 资料状态，DRAFT草稿，RECORDED已记录，不表示外部审核状态 |
| `remark` | `VARCHAR(500)` | 是 | 领料出库单备注 |
| `create_by` | `VARCHAR(64)` | 是 | 创建人系统用户标识，由服务端填写 |
| `create_time` | `DATETIME` | 是 | 创建时间，由服务端填写 |
| `update_by` | `VARCHAR(64)` | 是 | 最后修改人系统用户标识，由服务端填写 |
| `update_time` | `DATETIME` | 是 | 最后修改时间，由服务端填写 |

### mes_material_issue_item

| 字段 | 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 领料出库明细本地主键 |
| `factory_id` | `VARCHAR(64)` | 否 | 所属工厂标识，由服务端当前上下文填写 |
| `issue_id` | `BIGINT` | 否 | 所属本地领料出库单主键 |
| `material_id` | `BIGINT` | 否 | 领用材料主键，逻辑引用本厂物料产品资料 |
| `material_code` | `VARCHAR(64)` | 是 | 领用材料品号快照，由服务端从物料资料填写 |
| `batch_no` | `VARCHAR(128)` | 是 | 来源材料批次号，普通字符串，不校验编码规则 |
| `quantity` | `DECIMAL(20,6)` | 否 | 本次领料数量，保留六位小数，不等于生产或报工数量 |
| `unit_id` | `BIGINT` | 是 | 本次领料计量单位主键，空值表示未提供 |
| `unit_code` | `VARCHAR(64)` | 是 | 本次领料单位编码快照，由服务端填写 |
| `warehouse_id` | `BIGINT` | 是 | 领料来源仓库主键，空值表示未提供 |
| `warehouse_code` | `VARCHAR(64)` | 是 | 来源仓库编码快照，由服务端填写 |
| `remark` | `VARCHAR(500)` | 是 | 领料明细备注 |
| `create_by` | `VARCHAR(64)` | 是 | 创建人系统用户标识，由服务端填写 |
| `create_time` | `DATETIME` | 是 | 创建时间，由服务端填写 |
| `update_by` | `VARCHAR(64)` | 是 | 最后修改人系统用户标识，由服务端填写 |
| `update_time` | `DATETIME` | 是 | 最后修改时间，由服务端填写 |

### 索引、关系与设计理由

- 单头主键 id；普通索引 (factory_id,issue_no)、(factory_id,workorder_id)、(factory_id,workorder_code)、(factory_id,routing_id)、(factory_id,flow_card_no)。不承诺单号并发唯一，不以先查再插伪称安全。
- 明细主键 id；普通索引 (factory_id,issue_id)、(factory_id,material_code,batch_no)、(factory_id,material_id)、(factory_id,warehouse_id)。不为材料＋批次建立唯一关系。
- issue_id → mes_material_issue.id；material_id/product_id → material.id；workorder_id → pro_workorder.workorder_id；unit_id → unit_measure.id；warehouse_id → wm_warehouse.id；routing_id → routing.id。全部为逻辑引用，无物理外键。
- 单头不重复存材料／批次／汇总量：一单可有多个材料及批次，数量有不同单位，不任意求和。
- 单据编号必填但不自动生成；业务时间、工单、产出、流程卡和路线可空，支持只建立基础资料。若提供工单、产品或路线则校验；不能按编码隐式选择最新路线。
- 材料数量必须为正，保留14位整数＋6位小数。实际退款／负数调整另立用例；源扣料 DECIMAL(20,6) 导致本轮新增 V12 从最初 V11 的4位改为6位，未修改已执行 V11 或其checksum。
- 单位／仓库可空，不因候选字段列表强制必填；材料、单位和仓库编码由服务器在订正写入时形成快照；读取不动态拼最新资料。可选产品与工单文字编码允许在没有本地关联时作为普通业务标识保存；有本地关联时由服务器确定。
- status 仅 DRAFT／RECORDED，表达资料状态，不宣称实际出库、库存成功或 ERP 审核。未建立复杂状态流转。
- 排除ERP单头ID、单号关联、接口参数、同步标志、审核状态及报工／入库字段；本轮无需ERP扩展表。未来确有集成时以本地 issue_id 关联有明确消费者的扩展表。

迁移：`mes-admin/src/main/resources/db/business/V11__material_issue.sql`、`V12__material_issue_quantity_precision.sql`。所有32列与两表均有中文COMMENT。独立执行位置仅包含本轮迁移，执行前核对开发库 history 0..10 均成功；应用V11后再应用V12，最终history成功。没有执行中心库迁移、Flyway repair、旧表删除或源库转换。

只读转换预览：`scripts/migration/ktg-mes/material-issue-review.sql`，显式只引用隔离样本 schema issue_source_fixture；未执行。确认单可评估按请求号投影；扣料不能按工单＋批次重建真实领料单头。本轮没有已批准的源ID→BaseID映射，不提供假可用的数据导入。

## C. Codegen、权限与最小补充

Skill：`.agents/skills/mes-crud-codegen/SKILL.md`，读取当前项目配置与说明后建立本任务显式执行配置 `docs/development/material-issue-codegen.yaml`，仅选两表，all产物，write/fail，包 dev.mesbase.business.issue，路径与包逐文件核对一致；原用户全量配置未改。

前置 `mvn -B -ntp verify` 成功；实际 `./scripts/mes-business-codegen docs/development/material-issue-codegen.yaml target/material-issue-codegen/report.json` 为 GENERATED / 2 supported / 0 rejected / 40 files，真实MySQL metadata，无手写Mapper/XML替代。生成器不支持主子表整体生成，本轮保留独立单头与明细CRUD页，单头详情分页查看材料明细，不伪装成一个原子保存接口。

生成文件完整清单：

- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueExample.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/dao/MesMaterialIssueMapper.java`
- `mes-business/src/main/resources/dev/mesbase/business/issue/dao/MesMaterialIssueMapper.xml`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueCreateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueUpdateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueQueryDto.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssuePage.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/vo/MesMaterialIssueVo.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/vo/MesMaterialIssuePageVo.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueResource.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/MesMaterialIssuePermissions.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/IMesMaterialIssueService.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueServiceImpl.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/MesMaterialIssueController.java`
- `../mes-base-ui/src/api/issue/mesMaterialIssue.ts`
- `../mes-base-ui/src/views/issue/mesMaterialIssue/index.vue`
- `../mes-base-ui/src/menus/issue/mesMaterialIssue.json`
- `mes-business/src/test/java/dev/mesbase/business/issue/MesMaterialIssueScaffoldTest.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemExample.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemEntity.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/dao/MesMaterialIssueItemMapper.java`
- `mes-business/src/main/resources/dev/mesbase/business/issue/dao/MesMaterialIssueItemMapper.xml`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemCreateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemUpdateCommand.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemQueryDto.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemPage.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/vo/MesMaterialIssueItemVo.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/vo/MesMaterialIssueItemPageVo.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/domain/MesMaterialIssueItemResource.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/MesMaterialIssueItemPermissions.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/IMesMaterialIssueItemService.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/service/impl/MesMaterialIssueItemServiceImpl.java`
- `mes-business/src/main/java/dev/mesbase/business/issue/controller/MesMaterialIssueItemController.java`
- `../mes-base-ui/src/api/issue/mesMaterialIssueItem.ts`
- `../mes-base-ui/src/views/issue/mesMaterialIssueItem/index.vue`
- `../mes-base-ui/src/menus/issue/mesMaterialIssueItem.json`
- `mes-business/src/test/java/dev/mesbase/business/issue/MesMaterialIssueItemScaffoldTest.java`
- `mes-business/docs/generated/issue/material-issue-menu.sql`
- `mes-business/docs/generated/issue/material-issue-permissions.md`

### 必要手工补充

- `MaterialIssueRules.java`：当前厂引用、工单／产出一致性、确切曾发布工艺版本与产出适用关系、正数量与精度、编码快照及 sys_user ID 审计。未创建额外业务Service、ExtraMapper或另一套持久化。
- 两个生成 ServiceImpl 调用规则；单头有明细拒绝删除，明细不能跨单转移。生成同值UPDATE锁住父行，使明细新增与单头删除在本地事务内串行；批删包含无效ID时整批回滚。未开发主子表整体保存、扫码或库存副作用。
- 三个明细输入／查询契约分别裁剪派生编码、同步6位数量精度。前端API类型与只读字段同步；两页复用CrudPage、MaterialSelect／WarehouseSelect；IssueRecordDetails 为只读用户提供详情及有界20行明细分页，切厂／撤权清空且拒绝迟到回填。
- 手工改动的生成文件已移除MES-CODEGEN归属标记；未手改的生成文件保留标记。将来replace-generated遇到这些路径会OUTPUT_CONFLICT，不能自动恢复归属或覆盖。
- 测试：`mes-admin/src/test/java/dev/issuefixture/MaterialIssueHttpTest.java`、`MaterialIssueMySqlTest.java`、前端 `tests/materialIssue.test.ts`。测试配置／数据只在测试目录，不进生产包。

### 权限与菜单

- 单头：business:mes:material:issue:list/query/create/update/delete。
- 明细：business:mes:material:issue:item:list/query/create/update/delete。
- 普通用户应只授予两表list/query；管理员订正才授予create/update/delete。后端每个HTTP方法AUTHORIZED，普通读角色写入403的fixture负例已通过。没有按前端按钮替代服务端授权，也未硬编码admin用户名。
- 菜单SQL为 `mes-business/docs/generated/issue/material-issue-menu.sql`，add-or-update，目录“领料出库”与两页／动作；未执行SQL、未赋权。单头查看材料列表需要明细list，材料／仓库选择器需要对应目录list/query权限。

### 同步

现有 SyncCoordinator/mes_master_sync 提供 Factory＋sync_key 抢占、run_token进度和租约恢复，生成模板提供QueryPort→查询→抢占→SyncWriter事务。ERP_SYNCABLE不等于ERP_REQUIRED。本模块关闭generateSync：未确认外部不可变身份、单头／明细匹配键及原子批次边界；材料＋批次非唯一，issue_no没有唯一性承诺，MES自增ID不能假设等于ERP ID。

不为满足模板而猜一个主键、创建假ERP QueryAdapter或拆成相互独立的单头／明细同步。后续确认契约后复用既有Coordinator与生成Mapper，入口使用独立sync权限，整体批次由一个领料用例协调。当前CRUD不依赖外部连接，未装配ERP数据库、未发ERP请求。

**配置注意**：原全量配置generateSync=true且默认matchKey=code，新的两表没有code列；不能用原配置全量重生成本模块。使用本任务两表配置；后续全量生成前须显式排除本模块或确认支持的同步／覆盖策略。不能用本地ID猜映射，也不能为覆盖手工文件擅自恢复标记。

## D. 实际验收

| 命令／检查 | 结果 | 边界 |
| --- | --- | --- |
| 前置 mvn -B -ntp verify | BUILD SUCCESS | 构建最新生成器，未冒充业务运行 |
| IssueDatabase只读预检＋仅本轮Flyway filesystem迁移 | V11/V12成功，最终开发库version12 | 仅mes_base_dev_business；中心库及其他Factory未迁移 |
| scripts/mes-business-codegen 本任务配置 | GENERATED，40产物 | 真实JDBC metadata；后处理文件另列 |
| MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify | 394 run，0 failure，0 error，10 skipped；BUILD SUCCESS | 含本轮4项scaffold、2项HTTP/H2、1项真实MySQL |
| MaterialIssueHttpTest | 2 passed | 真生成XML＋两套独立H2＋合成身份，不是真登录 |
| MaterialIssueMySqlTest | 1 passed | 真开发MySQL，数量6位、重复批次、CRUD／回滚、COMMENT／索引；不经过登录HTTP |
| Node22 npm test | 32 files / 174 passed | 3项新增：两页只读权限、6位数量展示、切厂迟到详情拒绝；jsdom |
| Node22 npm run build | passed | vue-tsc与生产构建；保留既有大chunk提示 |
| 新增UI文件定向ESLint | passed | 没有格式化其他执行者文件 |
| scripts/format-java --check 本轮Java | 33 files passed | IntelliJ原生格式化 |
| /usr/bin/python3 scripts/smoke-packaged.py | 10 passed / 0 failed | 隔离配置打包与协议smoke，非本模块登录CRUD |
| 两仓库git diff --check | passed | 未提交／推送 |

HTTP/H2覆盖单头／明细新增、查询、详情、明细订正、删除与父单保护、分页总数、同批重复合法、精度、服务器快照／审计、不同厂独立库与越厂ID拒绝、草稿路线拒绝、错误材料／父单／数量拒绝、无厂失败及只读角色写入拒绝。MySQL测试全部业务记录rollback，TEST_ISSUE_单头计数0；回滚可能消费自增序列，未留下客户业务数据。

开发过程两次测试先失败后修正：新HTTP fixture权限谓词写反；MySQL COMMENT列数断言误写33（实际32）。均修正后重跑全量通过，不掩盖失败。定向ESLint6条格式错误已修正。初版4位数量以追加V12纠正而非篡改已执行迁移。

尚未完成：真实服务登录CRUD、真实A/B MySQL服务分厂、动态菜单、浏览器1440/1920/1280及删除取消／权限／切厂场景、实际ERP和完整生产业务。不将这些标记VERIFIED。当前运行服务未重启；依赖后续菜单SQL审查执行／权限配置和新JAR启动，不能因编译通过称已有服务包含新模块。

## E. 后续业务入口边界

本轮接口仅为管理员数据订正。未来生产领料应建立一个有边界的用例Service：接收已确认的工单、材料／批次和确切工艺版本，通过生成Mapper完成单头＋明细的一个本地事务，而不是让Controller依次调用两个CRUDHTTP。

“本地创建→ERP创建→本地提交或回滚”可以描述正常路径：本地事务内形成单据，外部明确SUCCEEDED后提交；外部明确REJECTED可以回滚本地。但本地回滚不会撤销已发送ERP命令。超时、断连、ERP成功后本地提交失败是UNKNOWN／外部残留，必须持久化可核对的请求身份／结果并设计幂等与恢复；不能将它们伪装成明确失败。ERP单号、不可变ID及核对状态留在有实际消费者的扩展关系，不塞入核心；未在本轮实现。

未来报工以mes_material_issue.id为领料来源，材料消耗定位mes_material_issue_item.id；一个报工对应多个领料／材料行时用明确关系表达，不以材料品号＋批次冒充唯一键。数量各自保留真实单位和事实，工艺使用已选routing_id，不能动态拿最新路线。后续报工／入库成为引用者后，应补充禁止历史关键字段订正／物理删除或可审查订正机制。

## F. 问题、风险与回退

### 当前已解决

- 区分材料／产出／工单号与ERP领料号；两表模型、无猜测唯一键、同厂引用、非正数量与6位精度、稳定路线版本、明细父单保护及输入审计边界。
- 生成目录与包一致；单表操作仅生成Mapper/XML/Example；手工所有权解除；并行Routing／Stock文件未修改。

### 后续业务开发／上线前

- 按目标Factory逐库审查执行V11/V12，菜单SQL串行审查执行并由管理侧授予读／订正权限；不擅自修改中心迁移history。
- 在真实新JAR登录环境执行API/UI验收；本轮不自动重启已有服务、配置账号、授予角色。
- 确认扫码入口、材料批次／流程卡区别、是否要求本地工单／产品、适用路线遗漏时的业务策略。核心字段合理可空不代表生产入口允许缺失。
- 真同步必须确认身份、原子主子表、幂等、分布式抢占及UNKNOWN；单号唯一性若成为生产必需，需用户确认数据库约束，不能用无约束先查再插。
- 当前物料／单位／仓库删除入口未新增“被领料引用”反向限制，编码快照仍保存历史；本轮未修改其他执行者模块。执行模块落地前应在共享删除边界补齐并发引用保护。确切已发布路线目前拒绝删除；未复制第二工艺模型。
- 后续报工／入库引用时收紧记录订正与删除，并明确报工对多张领料单的数量分配。

### 低优先级

- 工单／单位／工艺版本当前可填精确ID，未新增选择器；生成基础列表按ID与编码审查，后续体验可以渐进改善。
- 基础关联错误复用既有BAD_REQUEST/NOT_FOUND/RESOURCE_CONFLICT；公共ErrorCode正在并行修改，本轮未覆盖该文件。后续补更具体的中文错误码，不把文案提升为本轮数据风险。
- 大chunk提示为既有构建提示，本轮不扩展打包重构。没有承诺真实大数据分页性能或极端并发。

回退必须按本轮新增文件与数据库状态分别审查；删除源码不等于回退Flyway或已建表。没有删除迁移记录或自动drop表。Source Product、现有Routing／Warehouse及全部入场前dirty变更保留。

## Change Summary / Document Created

本轮业务代码：生成两表CRUD并最小补充引用、父子一致性和审计规则；不触发外部业务效果。数据库只增加领料表及精度迁移；菜单／角色未执行／变更。完整生成文件和最终sha256见机器证据。

- 本报告：A源事实、B逐表Schema、C文件／权限／同步、D验收、E后续调用边界、F风险与回退；静态、fixture、MySQL和未验证运行边界分别记录。
- verification.json：状态、真实metadata、40产物的生成／最终hash、命令与证据等级；未包含凭证或客户数据。
- material-issue-codegen.yaml：仅本轮两表显式执行参数，无凭证，不替换原用户全量配置。
- material-issue-review.sql：隔离样本只读转换投影，未执行，明确不能凭旧扣料记录重建领料单头。


## 2026-10-13 后续决定：改为纯业务库隔离

本报告前文的表内factory_id、同厂行校验及带工厂前缀索引是当时实现记录，已由用户新决定取代。
已配置SIMPLE_B业务库通过V15移除工厂列；业务代码、DTO和页面已重新生成／适配，不再叠加
行级工厂隔离。引用存在性、稳定工艺版本、事务、权限和父子保护保留。领料两表现在共30列。
详情及最新证据见 [业务工厂字段移除报告](2026-10-13-business-factory-column-removal-review.md)。
本次未自行重启用户服务，旧API/UI通过记录不代表无工厂字段版本已完成真实服务验收。
