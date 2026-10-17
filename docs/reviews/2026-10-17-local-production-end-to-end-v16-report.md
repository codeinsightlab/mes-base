# Base MES V1.6 — LOCAL Production End-to-End Closure

日期：2026-10-17。判定 **GO（LOCAL 单材料、执行级数量放行范围）**。Phase 1–6 已完成：指定 SIMPLE_B 真实样本 EXE-233 从原料入库、MES 工单需求、扫码领料、五节点报工、质量处置，到放行、实际成品接收及正式完工。最终 E/Q/净交接/净接收/成品库存均94，计划100；未交接或未接收14时均拒绝完工。未验证真实ERP厂商。

## A. 基线与并行修改保护

后端 `/Users/user/work/lixin/mes-base`，main，HEAD `e36ea0ace325616bd584b3d2a1c992b76157438a`；前端 `/Users/user/work/lixin/mes-base-ui`，main，HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。开始时后端已有 V1.5 未提交改动（41 个实际存在的变更文件），前端有生产、质量、登录及仓储等 27 个已变更文件；已保存完整 dirty patch 与逐文件哈希至 `.local/production-v16-20261017/{backend,frontend}-baseline.json` 及 `*-before.patch`。

本轮在既有 V1.5 之上增量修改供料、准备、库存 Owner、交接、公共 fixture 等必要链路，其他并行内容不覆盖。未 reset、commit、push、部署或启停用户已有服务。用户后续明确授权 Agent 自启验收服务并在结束后关闭，独立验收端口为15173→18081；运行包复制到本轮.local目录，后续构建不覆盖运行中的jar。读取了两仓库规则、个人工程路由与 Java/前端规范、V1.5 报告、Phase A 和 V1.4 记录。新业务使用 mes-crud-codegen Skill；不重新设计 FactoryIntegration 或 ProductionQuantityService。收尾逐文件哈希复核：原后端41文件中31未变、10个为本轮必要供料/库存/fixture适配；原前端27文件中26未变，仅LocalStockPanel新增成品动作标签。两仓库HEAD未变，证据 baseline-preservation-final.json。

## B. 工单用料维护及历史兼容

`WorkorderMaterialController → WorkorderMaterialService →` 生成的 `MesWorkorderMaterialMapper`。嵌入现有工单详情的 WorkorderMaterialPanel 支持材料、基本单位、总需求、当前工单计划依据、仓库范围、工作中心、启用状态与乐观版本维护；操作者和时间由服务端取得。

`mes_workorder_material.id` 直接采用工单主键，一工单一个数据库用料权威。存在数据库行时，无论停用、改料或不匹配，都不回退该工单旧 YAML。旧配置默认继续作为尚无数据库需求的历史工单兼容来源；`mes.production.input.legacy-config.enabled=false` 可整体停用兼容。不会自动导入历史 TEST 配置。

已有任何领料意图（含准备、UNKNOWN及历史领料）后禁止修改用料，比仅禁止确认后修改更保守。维护与准备复用工单锁；计算依据包含需求版本，准备事务在工单锁内再次校验版本与材料/单位/比例，阻止计算后换料竞争。原公式 `ROUND_HALF_UP(T × outputQty / P,6)` 保留。计划依据不匹配时不继续供料，需在没有事实时重新维护。已冻结 Input 的 calculation_json 不回写。

材料/仓库引用复用已有行锁；删除引用受保护。工单无事实且可删除时，在原事务删除附属用料，避免孤儿。仓库 JSON 数组按数值排序规范化，最大100项且受字段长度约束。LOCAL 候选继续数据库分页、批量产品与单位读取、10,000需求/配置上界。

## C. 正式质量放行模型

`QualityReleaseController → QualityReleaseService`，复用 ProductionQuantityService、锁定路径、QualityTreatmentService 的有界批量质量投影，不修改 REPORT、路径或有效产出。

采用执行级**累计授权总量快照**，不是多次累加同一额度：每笔 RELEASE 明确当前总授权数量 Q，引用上一笔事实，要求 expectedReleaseId 匹配；REVOKE 以关联原事实的新不可变记录撤回为0。全部命令带规范 UUID、可信操作者指纹、依据和时间。无SN/容器，故不声称逐件放行。

末道E及确切路径、节点数量、可信投入和质量申报/处置事实身份构成 basis_hash。当前依据不符、超E、待核实观察、未处置或未完成返工时 Q=0，历史授权仍返回 historicalReleased。返工成功不自动放行；OVERLAP不重复扣减；新放行不得超过E。历史放行、交接和库存不会因晚不良自动修改。依据变化后须业务人员重新明确放行。

## D. ProductionReceipt正向交接

复用 `ProductionReceiptService`、原不可变流水与UUID重放。原 `RECEIPT_QUALITY_UNVERIFIED` 门禁保留，改为消费当前有效正式放行；正向交接只允许RUNNING，且新增量同时不超过 E−R 与 Q−R。支持分批，交接本身不调用库存 Owner。

摘要保留 E−R、历史超E差额，并增加当前Q、剩余放行额度、仓库净接收、待接收和超放行差额。原交接冲销只允许冲销“原净量−已实际接收量”；仓库已有依赖时须先受控逆向仓库接收。终态交接冲销本轮保守拒绝，须专项历史纠正，不删除完工事实。

## E. 成品仓储接收及库存动作

`WarehouseAcceptanceController → WarehouseAcceptanceService → LocalStockService.receiveProduct`。先取得 WorkOrder→Execution，再由原 Owner 锁库存，接收事实、双余额和 wm_stock_action 同一业务事务。没有第二个库存余额写入实现。

引用原RECEIPT、Execution产品、目标仓库和产品批次，选择由现有库存CRUD受控建立的零余额来源。产品、仓库、批次及原业务身份由服务端核对；来源本身固定，不能用请求伪造余额、操作者或单位。每笔实际接收才增加库存；原Receipt可分多次接收，累积不得超过其当前净量。待接收数量可解释且单独展示。

`mes_warehouse_acceptance.id` 与同事务库存动作UUID一致，库存动作类型 PRODUCT_RECEIPT / PRODUCT_REVERSAL、来源 PRODUCTION_RECEIPT，原业务与动作可追溯。逆向只关联原正接收，受原净接收余额及当前实存/可用保护；物料、仓库、批次、库位及冻结基本单位必须仍一致。不能将被消耗的库存扣成负数。当前质量失效/历史超交阻止新的接收；质量变化不自动改库存。已完工的逆向本轮保守拒绝。

ERP仅增加 `ProductionReceiptAcceptanceCommand` 与 WarehouseErpAdapter 默认拒绝契约；未配置真实厂商时明确失败，不回退LOCAL，也不在本地事务调用ERP HTTP。

## F. Execution严格完工

`ProductionCompletionController → ProductionCompletionService.complete`。独立正式命令及权限，不通过CRUD改status/endTime。锁内核对RUNNING、工单/产品/发布工艺与确切路径、可信投入、原领料无未决状态、质量已处置、无待核实及关键未完成返工、当前正式放行及数量一致性。

严格要求 `R=E`、`R≤Q≤E`、`仓库净接收=R`；不要求E达到targetQty。完工事实记录 E/Q/R/实际接收、依据摘要、UUID、操作者、时间和原因；事实追加与执行COMPLETED/endTime同事务。并发和原UUID重放复用执行锁，不重复创建终态事实；不同新UUID不能再完成一次。命令带客户端看到的依据摘要，依据变化须重新核对。

终态后普通投产、正式报工、新领料及路径变更拒绝，已成功命令的原请求重放仍可查询。历史质量申报/受控纠正沿原语义保留，可以使当前数量与完工快照出现差额；不自动撤销COMPLETED或改写旧报工。终态交接/接收逆向无专项业务依据，本轮不开放。

## G. 全链数量演算及事实追溯

主样本 `TEST_V16_1017_112829`：工单208、执行233、产品87、原材料86、路线246（节点1719→1720→1721→1722→1723），原料库存23、成品库存24。工单用料由MES页面保存，T=200kg、P=100件、版本1、允许仓库10；原料正式入库500，扫码投产100并正式扣料200，剩余300。

| 阶段 | E | 当前Q | 净交接R | 净接收 | 成品库存24 | 结果 |
|---|---:|---:|---:|---:|---:|---|
| A–E各正常报工100 | 100 | 0 | 0 | 0 | 0 | 无放行交接被拒 |
| B晚不良10、明确独立、报废6/返工4 | 90 | 0 | 0 | 0 | 0 | 未核实/返工未完禁止放行 |
| B返工RESULT成功4 | 94 | 0 | 0 | 0 | 0 | 不自动放行 |
| 页面明确放行94、交接60+20 | 94 | 94 | 80 | 0 | 0 | 交接本身不入库 |
| 页面实际接收首批60 | 94 | 94 | 80 | 60 | 60 | 待接收20、待交接14 |
| 第二批实际接收20 | 94 | 94 | 80 | 80 | 80 | COMPLETE拒绝剩余14 |
| 再交接14，尚未接收 | 94 | 94 | 94 | 80 | 80 | COMPLETE拒绝待接收14 |
| 页面实际接收14并COMPLETE | 94 | 94 | 94 | 94 | 94 | COMPLETED，targetQty仍100 |

主样本放行UUID `137df328-be91-4bd5-abc7-c3d0593300c5`；交接UUID：60=`3339ac8c-c6f4-457f-8b06-c46c0bd0b6c8`、20=`d8481810-a518-4a51-bbfa-86563328c679`、14=`eb120f6d-790e-4cc6-b9ff-2ea7e9e6deb5`。正式完工UUID `44c417cb-868c-4ea3-81e3-eeb1bf77cd28`，3次重放仍只有一条完工事实；正常报工5条保留。Input UUID `8b671300-7dc9-4652-9b72-2c0ea2ca8654`，正式领料单100已CONFIRMED，output=100、stock_applied=true，冻结比例/来源仍在原Input。

真实逆向回归在主样本80阶段完成：受控库存调整-75模拟后续消耗（非真实出货），实存5时逆向10拒绝；调整+75恢复模拟减量后，逆向原接收10，再实际接收10。调整净额0，接收净额回到80；最终接收流水为60+20−10+10+14=94，库存动作可解释全部增减。

独立晚不良样本工单209/EXE-234、原料库存25/成品库存26：先真实放行98、交接98、接收98，再申报并核实B独立不良10。当前E90/Q0、历史放行98、R98/接收98/库存98，超E8；新交接和未完成处置时新放行拒绝，不修改历史和库存。此样本明确保留RUNNING以供复核差异，不代表主样本完工失败。

## H. 事务、幂等、并发和UNKNOWN

统一顺序：工单→执行→领料（相关命令）→库存；仓储不先锁库存再回头锁执行。原Owner的生产/接收方法采用MANDATORY，没有独立提交。不同库存来源也共用相同执行锁控制同一交接累计接收。UUID主键冲突转换为明确业务冲突，不泄漏数据库异常。

定向H2已覆盖：并发同交接不能超收；UUID重放；接收后生产冲销依赖；逆向净量；已消耗库存不能逆向为负；接收事实插入失败时余额与库存动作整体回滚；ERP模式拒绝LOCAL回退。晚申报真实质量10后，历史放行/交接98及库存98保留，E90/超交8可见；待核实阻止放行。

前端 `useClosureCommand` 为放行、撤回、完工、接收及逆向保留原UUID/参数到账号+工厂+业务对象的sessionStorage；原交接也补齐同标签会话恢复。关闭、切厂、撤权和卸载失效迟到响应；先前UNKNOWN后的后续4xx不删除原身份。不创建替代意图，不自动ERP重试。会话存储不保证跨设备恢复，服务端UUID与事实是最终依据。

## I. Schema、生成、迁移、权限及UI

业务正式新增 `V35__local_production_closure.sql`：mes_workorder_material、mes_quality_release、mes_warehouse_acceptance、mes_execution_completion。表/列均有中文COMMENT，无factory_id、无新增业务唯一或外键约束；并发由对应主记录锁与UUID主键保障。三类新事实共6个不可变UPDATE/DELETE触发器；旧V34未改写。

目标核实：SIMPLE_B→datasource8→192.168.18.25:3306/mes_base_dev_business_simple_b，LOCAL/ENABLED。已备份存量库存及迁移历史，经正式Flyway迁移并validate，业务V34→V35。新表真实metadata经 Skill 生成完整persistence/backend/test（4 supported/0 rejected），配置 `docs/development/production-closure-v16-codegen.yaml`。移除生成的普通写方法及伪造事实Create/Update请求，保留生成Mapper/XML/Example；手工改动文件移除MES-CODEGEN标记，不恢复生成器归属。

中心正式 `V36__local_production_closure_permissions.sql` 已迁移并validate，登记8项新权限，迁移本身未自动改变角色授权。包括workorder:material:update、quality release读/放行/撤回、wm acceptance读/接收/逆向、production execution complete。生成器不自动赋权。

用户随后明确授权给admin建立仅限SIMPLE_B的专用角色，包含上述8项、既有Receipt四项与正式报工submit共13项，保留原角色。角色方案 `.local/production-v16-20261017/role-plan.json`；首次角色配置前菜单GET返回500，彼时未写入；用户重启后通过正式角色API创建并分配。已按授权完成：角色150，key=`test-local-production-v16-simple-b`，FACTORY/SIMPLE_B，确切13项；admin原角色100/148/149保留。`role-verified.yml`核对中心库真实关联；不是迁移自动赋权。

页面复用：工单详情嵌入WorkorderMaterialPanel；执行行操作嵌入ProductionClosurePanel；现有生产交接页嵌入WarehouseAcceptancePanel并显示完整数量摘要；原LocalStockPanel显示新增成品动作。ID/decimal为字符串，后端BigDecimal；来源选择有分页，流水/历史有分页。弹层默认外部点击/Esc关闭，有编辑/UNKNOWN时确认后关闭。没有为每种事实另开可写CRUD管理页。

## J. 定向和全量测试

证据目录 `.local/production-v16-20261017/`，各层分开：

- phase1.log：工单需求替代/停用不回退/事实后冻结、投产及V1.5仓储回归通过。
- phase2-checked.log：正式放行、依据失效与Quality V1.3回归通过。早期fixture漏报工reason/引用及decimal协议断言失败均已修正，原日志保留。
- phase3-rerun.log：无放行门禁、分批94→60+20→剩余14、重放/超额、原冲销及历史差额通过。
- phase4.log、phase5-rerun.log：仓储原子、并发及严格完工94用例通过。
- reference-check.log：新引用保护、原库存CRUD、完工及业务枚举合约通过。
- final-boundaries.log：晚不良历史超额、未核实放行、ERP隔离、已消耗逆向保护、接收失败回滚等通过。
- verify-delivery.log：`mvn -B -ntp verify` 全部8模块通过（11:23:29）；修正旧错误文案后的 verify-live-fix.log 再次8模块通过（11:36:05）。
- ui-tests-delivery-complete.log：最终58文件344项全部通过；ui-build-delivery-complete.log：最终生产构建通过，仅原有分块大小提示。包含新增命令字段白名单、完工后只读、具体安全拒绝原因、UNKNOWN与迟到响应回归。此前341/342项日志保留。
- Java使用项目IntelliJ原生scripts/format-java，仅处理本轮变更；smoke-live-fix.log：最终 `/usr/bin/python3 scripts/smoke-packaged.py` 10项通过、0失败；smoke-delivery.log前一版本也通过。
- 复核按环境变量启用的旧MySQL测试，发现其共享测试上下文缺少新增质量放行依赖；在 ProductionDefectMySqlTest.context 注册真实 QualityReleaseService / WarehouseIntegration（同一 Mapper/事务），保留旧不可变、并发及质量数量断言。首轮失败日志 mysql-regression.log 保留；mysql-regression-rerun.log 使用 `MES_RECEIPT_MYSQL_ACCEPTANCE=true MES_QUALITY_V13_MYSQL=true`，3项全部通过、0跳过（11:28:56）。该真实DB测试包含明确历史fixture，不冒充本轮API全链。

默认跳过的真实数据库用例不算本轮执行；HTTP/H2不等于MySQL或现有服务，页面可见不等于正式业务成功。

## K. 真实MySQL/API/UI验收

真实目标再次核对：SIMPLE_B→数据源8→`192.168.18.25:3306/mes_base_dev_business_simple_b`，LOCAL/ENABLED。新增数据均为本轮TEST标签；没有直接SQL补改业务记录。主数据、路线、工单、库存来源、原料入库、开工/报工、质量处置、交接均使用正式业务API；**工单用料、扫码正式投产、质量放行、首批/末批实际接收、完工正反例均实际操作MES页面**。不能将这套混合API/UI验收描述成每个主数据字段都人工点击。

运行前段使用原5173/8081完成到交接80。用户随后澄清端口并明确授权独立启停，后段采用15173前端→18081后端，复制固定新jar运行，同一个SIMPLE_B样本接续。未操作用户原有服务。旧API会话到期后，按用户明确说明使用开发页面已有登录信息重新正常登录，认证信息只保存在私有.local文件，不写入报告。

| 验证 | 真实结果 / 证据 |
|---|---|
| 完整主链及94完工 | G表；live-state.json、live-api.jsonl、live-fact-links.json、live-readback.yml |
| 并发同交接接收20 | 两不同UUID同时提交：200/409；同成功UUID额外3次重放不多入库 |
| 无放行、超E放行、UNVERIFIED、未完成返工 | 正式API409；返工成功后Q仍0，页面明确放行后Q94 |
| 已接收后生产冲销依赖 | 正式API409，无删除/自动冲销 |
| 库存不足逆向 / 合法逆向 | 库存5时逆向10拒绝；恢复后逆向10并接收10，原动作保留 |
| 完工门禁 | R80/A80拒绝未交接14；R94/A80拒绝待接收14；R94/A94成功 |
| 终态新增与重放 | 新投产409、新正式报工422；原完工、Receipt及接收UUID重放成功，事实数量不增加 |
| 晚不良历史超额 | EXE-234真实E90/Q0/历史Q98/R98/A98/库存98/超E8；页面和API一致 |
| Factory/权限 | admin在工厂1的6项新接口403；已有无新权限TEST账号读取放行/接收均403；不新增任何授权 |
| UI切厂 | 切老厂ID1后原列表/闭环状态清空，显示当前范围无读取权限；恢复SIMPLE_B |
| 不可变事实 | 原放行、接收、完工、交接、库存动作、REPORT各UPDATE/DELETE共12次触发45000；失败事务回滚，原行未变 |
| 存量保护 | 原14条库存的物料/仓库/批次/库位/状态/余额及相关标识均保持；V1.5库存22仍615 |
| 新Schema | 正式Flyway业务V35/中心V36已迁移并validate；未改旧迁移 |

`.local/production-v16-20261017/ui/` 保存实际页面证据：工单需求、投产确认、R80/A60差额、晚不良超额、切厂拒绝、最终完工。1280/1440/1920下闭环抽屉宽1160，页面无横向溢出；流水在表格内滚动。最终 `completed94-final-1440.jpg` 与 `completed94-1920-full.jpg` 显示COMPLETED及94快照。

联调修复：放行误带完工字段会被后端严格400，现前端按动作构造精确请求（放行无expectedBasisHash、完工无quantity/expectedReleaseId），真实页面放行/完工已成功；明确安全业务拒绝显示剩余数量或质量处置原因；终态隐藏普通新增命令；交接页不再对已接收记录固定提示“尚未入库”。未知响应保留原UUID的行为另由前端失联测试验证，不把页面提交中的loading状态当成真实UNKNOWN故障证明。

验收进程已按用户授权关闭：本轮后端PID78644、前端PID82173；18081/15173连接复核均已释放，记录 runtime-cleanup.json。临时浏览器页关闭、尺寸恢复；用户已有服务未操作。

## L. 产品与集成边界

仅LOCAL单材料，执行级数量授权，无逐件/SN/容器归属。沿用单节点返工，不做多材料装配、复杂BOM/MRP、多节点返工、完整WMS或真实ERP厂商。晚质量变更保留历史完工与库存，显示差额并暂停不合法新增，不能自动补冲销或负库存。单位换算未引入，所有库存动作按物料基本单位处理。

旧YAML仅明确兼容，不能和数据库需求竞争权威；历史计算、旧Input和原领料恢复仍沿原规则。回滚不能退回裸写库存/事实CRUD的旧入口；有新事实后优先前向修正，保留动作与完工事实，不清空新表、不覆盖已执行Flyway、不盲重试UNKNOWN。

## M. 判定

**GO：LOCAL单材料完整闭环。** 主样本233已由正式业务命令与页面贯通，数量、库存、历史与严格完工互相解释；不是Schema、历史TEST或Mock冒充业务验收。指定数据库保留主样本233（完工94）和边界样本234（晚不良历史超额8），用户可在SIMPLE_B复验。

真实ERP厂商、多材料、多节点返工、真实发货/后续消耗流程均不在GO范围。ERP模式拒绝/无Adapter不回退由HTTP/H2及契约测试覆盖，未修改真实工厂模式来制造证明；库存不足逆向的真实负例使用明确标注的受控调整模拟消耗。当前没有逐件实物身份，不承诺SN级放行或跨设备UNKNOWN恢复。
