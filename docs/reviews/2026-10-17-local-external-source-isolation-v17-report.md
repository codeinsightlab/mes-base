# Base MES V1.7 — LOCAL / EXTERNAL 来源隔离实施与验收

日期：2026-10-17。结论：**CONDITIONAL GO**，适用于已核实来源的 SIMPLE_B LOCAL 新业务。来源隔离、真实 LOCAL 闭环及拒绝路径已验证；不代表真实 ERP 厂商执行、历史来源全部核实或其他业务库迁移完成。

## A. 当前源码与规则基线

- Backend `/Users/user/work/lixin/mes-base`，main，HEAD `e36ea0ace325616bd584b3d2a1c992b76157438a`。
- Frontend `/Users/user/work/lixin/mes-base-ui`，main，HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。
- 以个人 Engineering Rules、两仓 AGENTS、[业务边界](../architecture/local-erp-business-boundary.md)、[ADR-005](../architecture/ADR-005-erp-integration-boundary.md)、[规则审查](2026-10-17-local-erp-source-isolation-rules-review.md)及 V1.5/V1.6 报告为输入。
- 开始前保存 tracked/untracked 文件 SHA256、状态与完整差异于 `.local/source-v17-20261017/baseline.json` 和 `*-before.patch`。保留全部已有未提交工作；没有 reset、commit、push 或部署。原先已删除的两个 StockAction Command 未恢复。
- 复用既有 FactoryIntegrationSnapshot / Context / Registry、WarehouseIntegration、生产数量、质量及库存 Owner；没有新增第二套生产核心或模式配置。

## B. source 模型与真实迁移

真实目标已核对：`SIMPLE_B → datasource_id=8 → 192.168.18.25:3306/mes_base_dev_business_simple_b`；Factory、数据源 ENABLED；runtimeMode=LOCAL、integrationLocked=true、provider/ref 为空。最终数据库与 API 再次核对，未切换工厂模式或连接。

新增正式业务 Flyway [V36](../../mes-admin/src/main/resources/db/business/V36__business_source_isolation.sql)，**已通过正式 Flyway 执行成功**，保留迁移记录和校验，不改旧迁移：

- workorder.source、wm_warehouse.source：LOCAL / EXTERNAL，nullable，无默认值；NULL 表示历史待核实，不隐式 LOCAL。
- workorder_erp_extra 增 binding_key、configuration_revision。
- wm_warehouse_erp_extra 保存外部稳定 ID、provider、binding、配置版本，主键复用仓库 ID。
- 两个 BEFORE UPDATE trigger 拒绝已有非空来源变更或清空。首次 NULL 回填仅用于明确审查后的受控路径，普通 HTTP 无此入口。
- 新表、每个字段均有中文 COMMENT；没有业务 factory_id、新 UNIQUE 或外键。

迁移前备份全部 178 张工单及 Extra、3 个仓库、26 条库存和历史事实统计于本机私有 `audit-before.json`。结合原 V1.6 正式 API 证据、冻结 LOCAL 投入及 LOCAL Owner 流水，仅回填明确可证的工单 **208、209** 和仓库 **9、10、11** 为 LOCAL。没有根据当前模式、编码或 Extra 缺失推断来源。

其余 **176 张工单保留 NULL**：历史列表/详情仍可读取，新正式交易拒绝；具体 ID 见附录，原编码、状态清单保留于本机 `unresolved-workorders.json`。未批量改源、改编码或清数据。最终记录为 LOCAL 工单 17、待核实 176、LOCAL 仓库 5；新增 LOCAL 包括本轮真实 MySQL 测试和正式闭环的显式 TEST ONLY 数据。

最终 `readback-final.json` 对原 178 张工单/Extra、3 仓库、26 库存逐字段比较（只排除新增字段），`originalRowsPreserved=true`。原主键、关联和余额均保留。来源 Trigger、中文 COMMENT 和 V36 success 同时验证。

## C. WorkOrder 来源保护

`WorkOrderService` 普通创建由服务端固定 LOCAL，并执行当前工厂 LOCAL 准入；请求模型没有来源写字段。实际 Boot 对伪造 source 请求返回 400；独立测试宿主忽略未知 JSON 字段时也不能覆盖服务端 LOCAL。

普通编辑、删除、下达限制 LOCAL；EXTERNAL 权威计划字段不能靠普通 CRUD 权限编辑。来源不明确、模式不允许、映射缺失均拒绝。已有 Execution、锁定路径及计划修改保护继续执行。

新增内部 `syncExternal(ExternalPlan, Run)`：仅当前 ERP 配置及正确工厂/同步键/run token 执行，创建 EXTERNAL、保存外部身份/provider/binding/version；提交前复核执行权。同码 LOCAL/NULL、外部身份重复、绑定或配置版本接管均拒绝。保留原编码唯一约束，不自动合并或重命名。没有新增公开同步 HTTP、批量导入旁路或真实厂商读取实现。

## D. Warehouse 来源保护

`WmWarehouseServiceImpl` 普通创建固定 LOCAL；普通更新/删除仅当前模式允许的 LOCAL。EXTERNAL、未知来源不可通过普通编辑改权威字段。已有引用删除保护保留。

可信仓库同步保存 EXTERNAL 与稳定外部仓库 ID。同步只更新身份、provider、binding、配置版本一致的 EXTERNAL；遇到 LOCAL/NULL 同码、不同外部身份或绑定版本变化拒绝，不抢占本地仓库。沿用数据库同步协调，不以 JVM 锁替代 run token。库存同步仅允许当前 ERP 模式及可信 EXTERNAL 仓库，不能更新 LOCAL 权威余额。

LocalStockService 新期初/入库/领料/退料/调整等动作校验 LOCAL 仓库与当前模式。EXTERNAL 本地库存仅是镜像；普通库存编辑、删除和零余额新建同样不能绕过来源规则。

## E. 三方同源矩阵与边界

`BusinessSourceRules` 复用已有配置入口，在真实业务 Service 进行最终校验；选择器只是用户体验。ID 来自当前路由业务库，不新增表内工厂字段。批量读取已知仓库/映射，避免逐行查询。

| 工单 | 原料仓 | 已选择成品仓 | 当前模式 | 新正式业务 |
|---|---|---|---|---|
| LOCAL | LOCAL | LOCAL | LOCAL | 允许，仍受数量/质量/状态/权限约束 |
| EXTERNAL | EXTERNAL | EXTERNAL | ERP | 来源通过后校验同一 provider/binding/version 及真实 ERP 能力；无 Adapter 不成功 |
| LOCAL | EXTERNAL | 任意 | 任意 | 409 拒绝 |
| EXTERNAL | LOCAL | 任意 | 任意 | 409 拒绝 |
| 已可信 | 同工单 | 不同工单来源 | 任意 | 409 拒绝 |
| NULL/非法 | 任意 | 任意 | 任意 | 409 拒绝新正式交易 |
| LOCAL | LOCAL | LOCAL | ERP | 拒绝新 LOCAL，允许有原冻结证据的历史读取/重放/合法恢复 |
| EXTERNAL | EXTERNAL | EXTERNAL | LOCAL | 拒绝 |

已知关联在 ProductionInput 预览/计算/提交、MaterialIssue 创建/明细/首次提交、Execution 创建与引用、正式报工、交接、实际仓储接收和严格完工处检查。成品仓尚未选择时只检查当前已知对象；选择成品仓和接收时完成剩余检查，未新增强制提前绑定字段。

## F. 同步过滤与编码冲突

工单受控同步、仓库同步、库存同步不写 LOCAL；不覆盖 Execution、Report、Quality、Receipt、库存动作事实。工单有执行或相关事实时，权威计划更新仍被原计划门禁保护。

工单/仓库已有编码空间继续保留，冲突明确拒绝并保留两边原记录，交管理员人工核实。未移除唯一约束、自动重命名、删本地记录或引入双来源编码空间。

测试 `SourceIsolationHttpTest.trustedWorkorderSyncCreatesExternalAndRejectsLocalCodeAndBindingTakeover` 验证内部可信同步；来源矩阵与绑定版本测试使用隔离 Fixture。真实 SIMPLE_B 的合成 EXTERNAL 仅证明 HTTP/UI 拒绝，**不冒充真实 ERP 同步证明**。

## G. 原操作与 Factory 配置保护

整厂新业务按当前有效模式准入，缺配置、缺 Adapter、超时或异常不回退 LOCAL。普通工厂编辑的锁定模式变更在真实 API 返回 `409 FACTORY_MODE_CHANGE_REQUIRES_MIGRATION`，原配置未变。

领料冻结原 operationKey、执行模式、provider、binding、版本和外部仓库稳定 ID。`MaterialIssueSubmitCommand.Line.externalWarehouseId` 来自已核实映射，首次外部提交缺映射拒绝。历史旧命令兼容读取，不重新猜外部身份。

`ErpActionResult.stockEffective` 显式区分创建成功与实际生效。仅 SUCCEEDED 而未证明库存生效时保留 UNKNOWN 与原外部引用，不确认生产投入；按原单据查询 EFFECTIVE 后方可核实。旧构造协议默认未生效，不能制造假成功。原 UNKNOWN 请求不重建、改派或本地补扣。

## H. 新业务准入及历史兼容

新业务使用可信持久化 source 与当前配置；历史读取、相同 UUID 重放先走原幂等事实，不能用当前模式重解释历史。历史 LOCAL 领料确认/冲销、接收冲销使用冻结 LOCAL 证明与原引用；NULL 历史来源仅在调用方已证明原 LOCAL 操作时允许安全恢复，EXTERNAL 不允许进入 LOCAL Owner。测试覆盖切到 ERP 后原 LOCAL 恢复与外部拒绝。

没有切换 SIMPLE_B 模式来制造真实恢复用例：模式变化场景为 Fixture 证明；真实 API 验证原 UUID 重放、原事实不变。历史来源未知不等于删除历史或禁止查询。

## I. Controller / Service / Mapper / 前端

- Controller 保持收参、权限及白名单响应；source 为只读投影，列表支持来源筛选。
- Service 集中复用 BusinessSourceRules；WorkOrder/Warehouse、生产投入/正式报工、领料、交接/接收、完工及库存 Owner 增加相应边界检查。
- Mapper/XML/Example 使用真实 metadata 生成基础，沿用业务 SqlSessionFactory；不建立第二套数据访问层。
- Factory navigation 提供服务端当前 runtimeMode；前端仅保存在当前会话内存，切厂/撤权清理。
- 工单、仓库来源只读；NULL 明确“历史待核实”。WarehouseSelect、WorkOrderSelect 按新业务来源过滤；历史查询保留全来源检索。
- CrudPage 的 editableRow 限制行编辑、删除、批选，EXTERNAL 即使有普通 CRUD 权限也不可操作。
- 库存 warehouseSource 由仓库查询派生，不重复持久化 source；EXTERNAL 标注“库存仅参考”，LOCAL 模式不显示同步按钮。
- LocalStockPanel 保留流水与原 UNKNOWN 请求恢复，外部/未知来源不提供新增 LOCAL 动作；单独权限用户仍默认可用的首个动作。
- ID、decimal、分页、权限、切厂迟到响应沿用原契约；未变更生产数量公式与质量传播核心。

## J. Schema / Codegen / Flyway

使用项目 mes-crud-codegen Skill，配置 [source-isolation-v17-codegen.yaml](../development/source-isolation-v17-codegen.yaml)，从真实 SIMPLE_B metadata 为四表生成 persistence + backend + test，结果 GENERATED 4、拒绝 0；完整 scaffold 位于 `target/source-v17-codegen`。

采用 Entity/Example/Mapper/XML；保留既有工单与仓库业务 Service/Controller 并加门禁，没有上线可绕过规则的 Extra CRUD。手工适配 WorkorderEntity 外部投影时移除生成标记；纯生成产物保留归属。不存在为满足生成而添加新的业务入口、权限或主数据。

V36 SQL 已保存、已执行、已验证；受控历史回填为单独审查事务，未改写 Flyway 文件或伪造记录。本轮未改中心权限、角色、Factory 绑定或模式。其他已配置业务库尚未执行 V36，部署新二进制前应按正式迁移治理，不能把本次 SIMPLE_B 验证推广到其他库。

## K. 验证证据

本机私有证据目录：`/Users/user/work/lixin/mes-base/.local/source-v17-20261017/`，不提交凭证、Token、客户数据或完整数据库备份。

| 验证 | 结果 / 证据 |
|---|---|
| 定向源码/HTTP/来源矩阵 | BusinessSourceRulesTest、SourceIsolationHttpTest、同步/冻结身份/UNKNOWN/库存响应契约通过；覆盖八种三方组合、两种模式、缺映射/版本变化及无 Adapter |
| 最终 `mvn -B -ntp verify` | 8 个 reactor 模块 SUCCESS；verify-complete.log |
| 前端 `npm run test` | 58 文件、347 项通过；ui-complete.log |
| 前端 `npm run build` | 成功；ui-complete-build.log；保留已有大 chunk 提示 |
| 最终真实 MySQL | 6 项、0 失败/错误/跳过；mysql-complete.log |
| 最终打包 `scripts/smoke-packaged.py` | 10 passed / 0 failed；smoke-complete.log |
| 原数据保护/目标绑定 | readback-final.json：原记录字段保持、V36 success、176 待核实、5 LOCAL 仓库 |
| 最终包实际 API | final-api.json/log；新闭环、旧闭环、来源投影、重放、外部拒绝 |
| 真实来源负例 | live-negative.json：12 项；混原料仓、混工单、未知工单、混成品仓、领料明细绕过、外部编辑、Owner、伪造来源、跨厂权限、模式锁定 |
| 真实 UI | ui/：工单来源/完成状态，仓库和库存 1280/1440/1920，LOCAL 选择器及外部 Owner 只读 |

真实 MySQL 命令：

```sh
MES_WAREHOUSE_MYSQL_ACCEPTANCE=true MES_RECEIPT_MYSQL_ACCEPTANCE=true \
MES_QUALITY_V13_MYSQL=true MES_SOURCE_V17_MYSQL=true \
mvn -B -ntp -pl mes-admin -am test \
-Dtest=LocalWarehouseMySqlTest,ProductionReceiptMySqlTest,ProductionQualityV13MySqlTest,SourceIsolationMySqlTest \
-Dsurefire.failIfNoSpecifiedTests=false
```

6 项包含 LOCAL Owner 2、Receipt 1、Quality V1.3 2、来源不可改 MySQL Trigger 1。来源改写尝试 SQLSTATE 45000，事务回滚。测试创建独立 TEST ONLY 数据，不覆盖原数据。

### SIMPLE_B 实际闭环

使用用户明确授权自行启停的独立前端 15173 → 后端 18081，最终包替换后再次读取/重放；未启停用户 5173/8081。沿用既有 admin 权限，无新增授权。

标签 `TEST_V17_1017_135326`：新 LOCAL 工单 **218**，原料/成品仓 **12/13**，库存 **31/32**，Execution **247**。正式入原料 100 → 领料 20 / 投产 10 → 五节点各报工 10 → 质量放行 10 → 正向交接 10 → 实际原子接收 10 → 严格完工。最终 **E=Q=R=A=10，COMPLETED，原料 80、成品 10**。相同投入、接收、完工 UUID 重放返回原 ID，不重复扣料/入库/完工。

旧 V1.6 Execution **233** 仍 COMPLETED、E/Q/R/A=94；V1.5 stock **22** 仍为 615。最终读取含可信仓库来源，原 V1.6 晚不良样本保持原记录。

合成负例 WorkOrder219/Warehouse15/Stock34 明确 TEST ONLY、零余额、无真实 ERP：实际 HTTP 409、来源列参考提示、无编辑/删除/本地动作均验证后，仅删除本轮无依赖合成负例及空领料草稿。清理前检查无 Execution、领料/交接/库存动作引用，未删除任何原存量或正向不可变事实。正向闭环保留供用户复验。清理后五个仓库均 LOCAL；普通编辑自建 LOCAL 仓12仅改备注仍保持 LOCAL。证据 negative-cleanup.log、post-cleanup.json。

后端 `git diff --check` 通过。前端全量 diff 检查仅报任务前已有的 `src/views/issue/mesMaterialIssue/index.vue:3` 尾空格；baseline SHA256 证明本轮未修改该文件，不顺手清理。Java 改动检查显式 import、生成标记保护；任务增量清单 task-delta.json。正式生产数量核心与质量算法未重写。

## L. 未实现与证明边界

- 没有真实鼎捷/SAP 等厂商 Adapter，没有真实 ERP 权威余额、扣料/接收/取消或同步联调；Fixture 证明来源准入和失败语义，不能证明厂商业务成功。
- 没有多 ERP、多组织路由、自动模式迁移、WIP 无缝接续、双账平衡或跨系统补偿。
- 未将历史 LOCAL 自动转换 EXTERNAL，未将不可变事实迁入 ERP 重新记账，未解锁模式。
- 未证明 176 张历史工单来源；继续只读历史和受控原操作恢复，后续补证后再单独处理，不猜测。
- 工厂 1 的真实 API 仅证明当前账号该操作被 403 拒绝；其他业务库的运行/迁移未在本轮验证。两库隔离的自动化证据与真实多厂数据库证据分开。

## M. 发布判断

**CONDITIONAL GO**：SIMPLE_B 已确认 LOCAL 来源的新业务及原 V1.6 闭环可用，混源、外部普通编辑与错误进入 LOCAL Owner 均有门禁与验证。

条件：历史未知工单继续隔离；其他业务库部署前正式迁移；真实 EXTERNAL 正式业务必须另外完成厂商能力与生效契约验收。本次不发布真实 ERP 业务，也不执行模式切换。独立验收服务在交付前关闭，详见本机 cleanup.json；用户服务不动。

## 附录：176 张历史待核实工单 ID

1, 2, 4, 6, 8, 10, 11, 13, 14, 16, 18, 19, 20, 21, 22, 23, 25, 26, 27, 29, 30, 32, 33, 35, 36, 38, 39, 41, 42, 44, 45, 47, 48, 50, 51, 53, 54, 56, 57, 58, 59, 60, 61, 62, 64, 65, 66, 67, 68, 69, 71, 72, 73, 77, 80, 81, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 163, 164, 165, 166, 167, 168, 169, 170, 171, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207。

逐 ID 的原编码/状态保留在本机私有证据清单，避免将业务数据复制进共享文档。

## N. 2026-10-17 测试工单受控清理

用户已确认附录176张工单全部为测试数据，允许安全作废，不再开发历史来源核实。本节追加后续处理事实，保留前文实施时的历史结论；没有因此回填LOCAL或放宽不可变保护。

### 范围与备份

从本报告原附录提取176个唯一ID，与V1.7私有清单逐项比对一致。重新读取中心绑定并验证 SIMPLE_B → datasource8 → 192.168.18.25:3306/mes_base_dev_business_simple_b；Factory和数据源启用，LOCAL且锁定。没有扩大到其他工单或工厂。

动作前，以只读REPEATABLE READ事务备份27张相关表，包括工单/Extra、Execution、批次/路径、领料/提交/投入、报工、缺陷/处置/放行、交接/接收、库存及动作。备份位于本机 `.local/source-cleanup-v17-20261017/before.json`，权限600；同目录保存目标清单、逐ID结果、动作日志及前后校验。没有向共享文档复制凭证或完整业务数据。

当前176张均source=NULL：DRAFT41、RELEASED135，关联Execution136个（READY95、RUNNING41）。没有COMPLETED工单或Execution。55张没有Execution；112张关联投入/提交、报工、质量或仓储类记录，详细表名和计数见results.json。

### 实际执行与统计

|对象|已作废|已安全删除|已完成保留|受阻保留|
|---|---:|---:|---:|---:|
|目标工单176张|0|0|0|176|
|关联Execution136个|0|13|0|123|

工单没有正式cancel/void/terminate动作，WorkOrderStatus只有DRAFT/RELEASED。WorkOrderService.delete先要求可信LOCAL、再要求DRAFT且无Execution/领料/旧批次；因此176张NULL工单均不能经现有入口删除。实际对目标工单1调用原删除API，返回409 BUSINESS_SOURCE_CONFLICT，未变更数据。普通Execution编辑也禁止状态变化。没有临时开发通用状态修改或将领料作废冒充工单作废。

最初8081未运行；用户随后明确回复“启动”，本轮据此启动独立18081后端，使用已验收的V1.7 runtime-v17.jar。没有替换或部署用户服务、没有执行迁移或新增权限。使用现有admin身份、X-Factory-Id=SIMPLE_B和原正式API，逐个读取工单与Execution并核对身份/READY/未开工，再单个调用DELETE /api/production/mes-production-execution；13次均204，随后GET均404。原Service事务内再次检查引用和锁定路径。

安全删除的13个Execution（工单仍保留）：WO89/EXE99、WO117/EXE126、WO128/EXE138、WO145/EXE156、WO160/EXE172、WO186/EXE201、WO196/EXE213、WO197/EXE215、WO198/EXE217、WO199/EXE219、WO200/EXE221、WO201/EXE223、WO202/EXE225。现有删除规则同时删除13个未锁定路径头及39个草稿路径节点；这些对象无开工、派工、领料、报工、质量、交接或库存事实，不属于已锁定生产路径或不可变历史。

其余Execution因开工/运行、领料、报工、派工、质量或锁定路径等规则保留；即使无单项不可变事实，也不能用普通编辑伪造CANCELLED。既有领料作废只负责原领料/投入，不等于工单或Execution终止，不为本轮清理冲销正式事实。

V1.7新生产候选已按可信source过滤NULL，正式后端也拒绝其进入新交易；本轮不将历史列表伪装为已经清空。没有COMPLETED目标，因此“已完成保留”是0，不强制改为CANCELLED。

### 逐工单ID结果

下表全部工单结果为“受阻保留”，工单本身未写入。W=无正式作废动作且NULL禁止普通删除；R=已下达，普通删除仅DRAFT；E=关联Execution没有正式取消入口；F=关联投入/提交、报工、质量或仓储记录；B=旧批次引用。完整原状态、事实表名/计数及逐Execution原因保存在results.json。表内“删除”只代表成功删除无引用Execution，不代表工单删除。


|ID|source / status|Execution before -> result|Facts and references|Reasons|WorkOrder result|
|---|---|---|---|---|---|
|1|NULL / DRAFT|-|-|W,B|BLOCKED / RETAINED|
|2|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|4|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|6|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|8|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|10|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|11|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|13|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|14|NULL / RELEASED|-|-|W,R,B|BLOCKED / RETAINED|
|16|NULL / DRAFT|-|-|W,B|BLOCKED / RETAINED|
|18|NULL / RELEASED|15:RUNNING -> RETAINED, 16:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|19|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|20|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|21|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|22|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|23|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|25|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|26|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|27|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|29|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|30|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|32|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|33|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|35|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|36|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|38|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|39|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|41|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|42|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|44|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|45|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|47|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|48|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|50|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|51|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|53|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|54|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|56|NULL / RELEASED|61:RUNNING -> RETAINED|mes_material_issue_submission:1, mes_production_defect:10|W,R,E,F|BLOCKED / RETAINED|
|57|NULL / RELEASED|62:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|58|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|59|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|60|NULL / RELEASED|65:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|61|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|62|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|64|NULL / RELEASED|70:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|65|NULL / RELEASED|71:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|66|NULL / RELEASED|72:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|67|NULL / RELEASED|73:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|68|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|69|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|71|NULL / RELEASED|78:RUNNING -> RETAINED|mes_material_issue_submission:1|W,R,E,F|BLOCKED / RETAINED|
|72|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|73|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|77|NULL / RELEASED|90:READY -> RETAINED|mes_production_report:14|W,R,E,F|BLOCKED / RETAINED|
|80|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|81|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|83|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|84|NULL / RELEASED|94:READY -> RETAINED|mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|85|NULL / RELEASED|95:READY -> RETAINED|mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|86|NULL / RELEASED|96:READY -> RETAINED|mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|87|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|88|NULL / RELEASED|98:READY -> RETAINED|mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|89|NULL / RELEASED|99:READY -> DELETED|-|W,R,E|BLOCKED / RETAINED|
|90|NULL / RELEASED|100:READY -> RETAINED|mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|91|NULL / RELEASED|101:RUNNING -> RETAINED|mes_material_issue_submission:1, mes_production_report:6|W,R,E,F|BLOCKED / RETAINED|
|92|NULL / RELEASED|102:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|93|NULL / RELEASED|103:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|94|NULL / RELEASED|104:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|95|NULL / RELEASED|105:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|96|NULL / RELEASED|106:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|97|NULL / RELEASED|107:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|98|NULL / RELEASED|108:READY -> RETAINED|-|W,R,E|BLOCKED / RETAINED|
|99|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|100|NULL / RELEASED|110:READY -> RETAINED|mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|101|NULL / RELEASED|111:READY -> RETAINED|mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|102|NULL / RELEASED|112:READY -> RETAINED|mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|103|NULL / RELEASED|113:READY -> RETAINED|mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|104|NULL / RELEASED|114:READY -> RETAINED|mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|105|NULL / RELEASED|115:READY -> RETAINED|mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|106|NULL / RELEASED|116:READY -> RETAINED|mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|107|NULL / RELEASED|117:READY -> RETAINED|mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|108|NULL / RELEASED|118:READY -> RETAINED|mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|109|NULL / RELEASED|119:READY -> RETAINED|mes_production_report:1, mes_production_defect:3|W,R,E,F|BLOCKED / RETAINED|
|110|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|111|NULL / RELEASED|121:READY -> RETAINED|mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|112|NULL / RELEASED|122:READY -> RETAINED|mes_production_report:2, mes_production_defect:5|W,R,E,F|BLOCKED / RETAINED|
|113|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|114|NULL / RELEASED|123:RUNNING -> RETAINED|mes_production_report:4, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|115|NULL / RELEASED|124:RUNNING -> RETAINED|mes_production_report:4, mes_production_receipt:8|W,R,E,F|BLOCKED / RETAINED|
|116|NULL / RELEASED|125:RUNNING -> RETAINED|mes_production_report:4, mes_production_defect:3, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|117|NULL / RELEASED|126:READY -> DELETED, 127:RUNNING -> RETAINED, 227:READY -> RETAINED|mes_material_issue_input:4, mes_material_issue_submission:4, mes_production_report:4, wm_stock_action:2|W,R,E,F|BLOCKED / RETAINED|
|118|NULL / RELEASED|128:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|119|NULL / RELEASED|129:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|120|NULL / RELEASED|130:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|121|NULL / RELEASED|131:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|122|NULL / RELEASED|132:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|123|NULL / RELEASED|133:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|124|NULL / RELEASED|134:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|125|NULL / RELEASED|135:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|126|NULL / RELEASED|136:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:3|W,R,E,F|BLOCKED / RETAINED|
|127|NULL / RELEASED|137:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:4, mes_production_defect:3, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|128|NULL / RELEASED|138:READY -> DELETED, 139:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|129|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|130|NULL / RELEASED|141:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|131|NULL / RELEASED|142:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|133|NULL / RELEASED|144:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|134|NULL / RELEASED|145:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|
|135|NULL / RELEASED|146:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|136|NULL / RELEASED|147:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|137|NULL / RELEASED|148:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|138|NULL / RELEASED|149:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|139|NULL / RELEASED|150:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|140|NULL / RELEASED|151:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|141|NULL / RELEASED|152:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|142|NULL / RELEASED|153:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|143|NULL / RELEASED|154:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:3, mes_quality_treatment:2|W,R,E,F|BLOCKED / RETAINED|
|144|NULL / RELEASED|155:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:4, mes_production_defect:2, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|145|NULL / RELEASED|156:READY -> DELETED, 157:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|146|NULL / RELEASED|158:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|148|NULL / RELEASED|160:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:5, mes_quality_treatment:3|W,R,E,F|BLOCKED / RETAINED|
|149|NULL / RELEASED|161:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|150|NULL / RELEASED|162:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|151|NULL / RELEASED|163:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|152|NULL / RELEASED|164:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|153|NULL / RELEASED|165:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|154|NULL / RELEASED|166:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|155|NULL / RELEASED|167:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|156|NULL / RELEASED|168:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|157|NULL / RELEASED|169:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|158|NULL / RELEASED|170:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:3, mes_quality_treatment:2|W,R,E,F|BLOCKED / RETAINED|
|159|NULL / RELEASED|171:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:4, mes_production_defect:3, mes_quality_treatment:2, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|160|NULL / RELEASED|172:READY -> DELETED, 173:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|161|NULL / RELEASED|174:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|163|NULL / RELEASED|176:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:5, mes_quality_treatment:3|W,R,E,F|BLOCKED / RETAINED|
|164|NULL / RELEASED|177:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|165|NULL / RELEASED|178:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|
|166|NULL / RELEASED|-|-|W,R|BLOCKED / RETAINED|
|167|NULL / RELEASED|180:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2|W,R,E,F|BLOCKED / RETAINED|
|168|NULL / RELEASED|181:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:2, mes_quality_treatment:8, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|169|NULL / RELEASED|182:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|
|170|NULL / RELEASED|183:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|171|NULL / RELEASED|184:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:3|W,R,E,F|BLOCKED / RETAINED|
|173|NULL / RELEASED|186:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:7, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|174|NULL / RELEASED|187:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|175|NULL / RELEASED|188:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|
|176|NULL / RELEASED|189:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|177|NULL / RELEASED|190:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|178|NULL / RELEASED|191:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|179|NULL / RELEASED|192:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|180|NULL / RELEASED|193:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|181|NULL / RELEASED|194:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|182|NULL / RELEASED|195:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|183|NULL / RELEASED|196:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:2, mes_quality_treatment:1|W,R,E,F|BLOCKED / RETAINED|
|184|NULL / RELEASED|197:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:1, mes_production_defect:3, mes_quality_treatment:2|W,R,E,F|BLOCKED / RETAINED|
|185|NULL / RELEASED|198:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:4, mes_production_defect:3, mes_quality_treatment:2, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|186|NULL / RELEASED|201:READY -> DELETED, 202:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|187|NULL / RELEASED|203:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2|W,R,E,F|BLOCKED / RETAINED|
|188|NULL / RELEASED|204:READY -> RETAINED, 212:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:8|W,R,E,F|BLOCKED / RETAINED|
|190|NULL / RELEASED|206:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:2, mes_quality_treatment:10, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|191|NULL / RELEASED|207:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|192|NULL / RELEASED|208:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|
|193|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|194|NULL / DRAFT|-|-|W|BLOCKED / RETAINED|
|195|NULL / RELEASED|210:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:2, mes_production_defect:5, mes_quality_treatment:3|W,R,E,F|BLOCKED / RETAINED|
|196|NULL / RELEASED|213:READY -> DELETED, 214:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2, wm_stock_action:2|W,R,E,F|BLOCKED / RETAINED|
|197|NULL / RELEASED|215:READY -> DELETED, 216:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, wm_stock_action:3|W,R,E,F|BLOCKED / RETAINED|
|198|NULL / RELEASED|217:READY -> DELETED, 218:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, wm_stock_action:3|W,R,E,F|BLOCKED / RETAINED|
|199|NULL / RELEASED|219:READY -> DELETED, 220:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, wm_stock_action:2|W,R,E,F|BLOCKED / RETAINED|
|200|NULL / RELEASED|221:READY -> DELETED, 222:RUNNING -> RETAINED|mes_material_issue_input:2, mes_material_issue_submission:2, mes_production_report:2, wm_stock_action:2|W,R,E,F|BLOCKED / RETAINED|
|201|NULL / RELEASED|223:READY -> DELETED, 224:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, wm_stock_action:3|W,R,E,F|BLOCKED / RETAINED|
|202|NULL / RELEASED|225:READY -> DELETED, 226:READY -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, wm_stock_action:2|W,R,E,F|BLOCKED / RETAINED|
|203|NULL / RELEASED|228:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:3|W,R,E,F|BLOCKED / RETAINED|
|204|NULL / RELEASED|229:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:4, mes_production_defect:3, mes_quality_treatment:2, mes_production_receipt:5|W,R,E,F|BLOCKED / RETAINED|
|205|NULL / RELEASED|230:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:7, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|206|NULL / RELEASED|231:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4, mes_production_receipt:1|W,R,E,F|BLOCKED / RETAINED|
|207|NULL / RELEASED|232:RUNNING -> RETAINED|mes_material_issue_input:1, mes_material_issue_submission:1, mes_production_report:5, mes_production_defect:1, mes_quality_treatment:4|W,R,E,F|BLOCKED / RETAINED|

### 前后验证及交付边界

结束后再次按同一绑定读取27张表。逐行逐字段比较证明，仅上述13个Execution、13个未锁定路径头及39个草稿节点被原API删除；没有新增行或改写保留行。其余24张表全部完全一致，包含176张目标工单、全部库存26条、库存动作91条、投入108条、报工298条、缺陷179条、质量处置157条、质量放行3条、交接71条及仓储接收7条。

LOCAL样本工单208、209、218及其关联记录均未变化，V1.5库存22仍615。before/after两个动作前快照SHA256一致；final快照仅有预期删除差异。完整校验和、删除ID及统计见本机verification.json，逐ID结果见results.json，正式API结果见api.jsonl/actions.json。

本轮没有直接写入SQL、来源回填、状态重写、新权限、Schema变更或迁移，没有修改业务代码，没有Git提交/推送或部署。用户后续授权启动的独立18081后端在处理结束后关闭，其他服务未启停。未运行构建/自动化测试冒充正式清理动作；实际证据是现有业务API及真实数据库前后比较。

**处理结论：部分清理完成。** 13个无引用Execution已安全删除；176张工单仍受阻保留，作废目标未实现。按用户明确边界，不为清理开发危险通用状态修改，也不再开发历史来源核实；未来若要终止这些工单，需要另有正式、带事实保护的业务终止能力，本轮没有预建或开放。NULL来源继续被现有新生产候选和正式准入排除，历史仍可查询。

## O. 2026-10-17 V1.8 后续处理结果

用户确认附录176张均为测试数据，不再开发历史来源核实。V1.8 新增正式受权限保护、幂等的工单CANCEL命令后，已通过指定SIMPLE_B正式API完成176/176作废及176条取消事实，原source仍NULL。全表前后对比仅上述状态/版本/更新时间与取消事实变化，Execution、投入、报工、质量、仓储和库存未修改。N节的受阻结论是新增正式能力前的历史状态，已由本次后续处理解决；原13个安全删除Execution是此前轮次，不属于V1.8取消副作用。详见 [V1.8实施报告](2026-10-17-workorder-cancellation-v18-report.md)。
