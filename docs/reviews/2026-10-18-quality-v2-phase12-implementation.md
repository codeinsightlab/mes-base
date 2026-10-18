# Base MES Quality V2 — Phase 1 + Phase 2 实施与验收

日期：2026-10-18。结论：**CONDITIONAL GO：代码、隔离回归及构建交付；PROD 迁移、真实写入与浏览器业务验收 BLOCKED（本轮未获真实写入授权）**。本报告以[差异审查](2026-10-18-quality-v2-gap-review.md)为依据，仅实施工序不良类型、现场逐次申报、费用分类、自动统计及核实权限修复。

## A. 工作区保护与修改清单

- 后端 `/Users/user/work/lixin/mes-base`，main，HEAD `5793986eda8b52eb6d24a84640b43f5d192e2cb3`；前端 `/Users/user/work/lixin/mes-base-ui`，main，HEAD `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`。未 reset、commit、push。
- 已重新核对个人工程入口、适用工程原则/Java/前端/知识治理及仓库规则；读取 Codegen Skill、当前配置、差异报告及 V1.6、V2.2、V2.3、V2.3.1 的既有验收结论。旧项目只读。
- 保存任务开始时75个dirty/untracked文件SHA256及两个仓库diff：`.local/quality-v2-phase12-20261018/{before-sha256.json,mes-base-before.patch,mes-base-ui-before.patch}`。
- `ProductionQuantityService`、ProductionReportSubmitService、ProductionExecutionStatus及V2.3.1既有生命周期改动保留；数量服务与开始时逐字节一致。本轮不调整E/D/R/U算法，也不修改原Q/R/A事实Owner。
- 5个原已dirty的前端文件叠加本轮改动：`src/api/quality/productionDefect.ts`、`src/views/production/productionReporting/index.vue`、`src/views/quality/productionDefect/index.vue`、`tests/productionDefect.test.ts`、`tests/productionReporting.test.ts`。没有用HEAD覆盖用户版本。
- 另一个并行任务修改了原已存在的 `scripts/performance/summarize-production-chain.py`，并新增性能报告；本轮未编辑这些内容，不能把这部分变化宣称为本轮产物或哈希未变。其余原始文件保护结果见 `preservation-check.json`。

| 本轮文件组 | 职责 |
|---|---|
| `mes-admin/src/main/resources/db/business/V39__operation_defect_types_and_cost.sql` | 新类型配置表、不良快照/费用/指纹版本增量，历史waste可空 |
| `mes-admin/src/main/resources/db/system/V41__quality_v2_permissions.sql` | 注册5个权限，不授予任何角色 |
| `docs/development/quality-v2-phase12-codegen.yaml`、`codegen-config.review.yaml` | 显式生成审查副本；全量生成排除受控类型表，避免普通CRUD重开 |
| `quality/domain/OperationDefectTypeEntity/Example`、`quality/dao/OperationDefectTypeMapper`及XML | 真实JDBC metadata生成的单表持久化 |
| `quality/service/OperationDefectTypeService`、`quality/controller/OperationDefectTypeController`、`production/service/RoutingReferenceRules` | 父工序归属、版本、并发唯一性和双向删除引用保护 |
| `quality/domain/MesProductionDefectEntity/Example`、原Mapper XML、`ProductionDefectModels`、`ProductionDefectV2Models`、`DefectCostCategory` | 增量模型、历史字段及新旧命令契约 |
| 原 `ProductionDefectService/Controller`、新 `ProductionDefectV2Controller`、`mes-common/.../ErrorCode` | 复用原追加链、共同核实授权、费用权限、UUID/快照与响应 |
| `DefectStatistics`、`DefectStatisticsService/Controller`、`DefectStatisticsExtraMapper`及XML | 跨不良、节点、处置事实的服务端分页统计 |
| 前端 `qualityV2.ts`、`OperationDefectTypePanel`、`DefectDeclarationPanel`、`DefectStatisticsPanel` | 配置、申报恢复、汇总交互 |
| 既有工序页、不良页、现场报工页及不良API | 嵌入新入口，保留历史面板，修正数量标签 |
| `QualityV2HttpTest`、既有 MaterialIssue/ProductionDefect HTTP及MySQL测试夹具、前端qualityV2及旧页面测试 | 新行为、授权/并发/历史回归，适配真实依赖 |

后端文件组位于 `mes-business/src/main/{java/dev/mesbase/business,resources/dev/mesbase/business}`，前端位于上述UI仓库；完整本轮文件清单及哈希另见证据目录 `delivery-manifest.json`。

## B. OperationDefectType 正式模型

新增 `operation_defect_type`：id、operation_id、code、name、default_cost_category、status、sort、remark、revision、create_by/time、update_by/time。无factory_id、物理外键或新增非主键UNIQUE。

`OperationDefectTypeService.save`通过既有 `RoutingReferenceRules.lockOperations` 在业务事务内锁定父工序；在同一锁内核查编码及revision后写入。code采用strip+大写规范化，同工序编码唯一，跨工序可重名同码。所有配置创建、更新、删除及类型申报读取共享父工序锁；不是无约束的“先查再插”。此保证针对正式Service写入口，不承诺绕过服务的任意SQL也有数据库唯一约束保护。

父operation_id只由URL绑定，不存在请求体转移字段；新建要求有效启用工序，更新必须expectedRevision。类型启停沿用ENABLED/DISABLED；默认费用必须显式为LABOR/MATERIAL/BOTH，UNKNOWN不能用于新配置。用户ID及时间由服务端写入。

已被任意历史版本引用的类型拒绝物理删除，包括最新VOID的历史；未引用类型可物理删除。工序删除同时检查类型配置及原routing_node引用，避免留下孤儿配置。历史不良读取不JOIN今天启用的类型，不因停用而隐藏历史。

## C. Flyway、Codegen 与 Schema

| 项目 | 实际状态 |
|---|---|
| PROD真实绑定 | 本轮只读再次确认：PROD/ENABLED/LOCAL → datasource1/ENABLED → `110.42.227.83:3306/mes_base_prod_business`；中心 `mes_base_prod` |
| 当前真实迁移 | 中心V40、业务V38；未执行本轮V41/V39，未修改旧迁移 |
| 当前真实质量数据 | 原不良3条、处置8条；新类型表尚不存在 |
| V39 | 已保存正式迁移；在隔离H2 MySQL兼容Fixture执行适配DDL及metadata验证；**未在真实MySQL执行** |
| V41 | 已保存正式权限迁移；未执行，也未新增admin/角色授权 |
| Codegen | 已使用mes-crud-codegen工作流，调用现有BusinessCodegenRunner；隔离H2真实JDBC metadata中2表GENERATED、0 rejected |
| 产物审查 | 完整persistence/backend/test先生成至 `target/quality-v2-generated`；采用生成持久化，受控用例替换可绕过规则的CRUD，无第二套单表Mapper |

生成配置 `docs/development/quality-v2-phase12-codegen.yaml` 显式输出审查目录、选择2表及persistence/backend/test，生成连接为忽略目录中的fixture配置。实际开发期调用 `.local/quality-v2-phase12-20261018/GenerateQuality.java`，先建隔离H2结构再向现有Runner传入该DataSource；这不是从SQL文本伪造metadata，也不是MySQL生产Schema生成成功的宣称。

旧不良Entity/Example从metadata重新生成，保留MES-CODEGEN归属；原手工Mapper XML保留不可变写入及Latest自定义查询，仅采用新生成列映射/INSERT片段。新增类型持久化保留生成标记；手工业务类不冒充生成产物。未把审查副本中的普通不良CRUD发布到正式源码。

V39只新增可空快照列及普通索引，并将legacy waste_category改可空；不更新旧账本、删除触发器或回填费用。上线前必须走正式迁移、检查/备份目标及校验历史，不得用手工DDL绕过Flyway记录。当前未获真实写授权，不能把“SQL已保存”写成“PROD已执行”。

## D. 工序配置 API 与前端

路径 `/api/production/operation/{operationId}/defect-types`：

| 方法 | 权限 | 行为 |
|---|---|---|
| GET | `business:operation:defect:type:read` | 父对象存在性校验、分页，最多100 |
| POST | `business:operation:defect:type:create` | 创建属于该工序的类型 |
| PUT `/{id}` | `business:operation:defect:type:update` | 编辑及启停，expectedRevision |
| DELETE `/{id}?expectedRevision=` | `business:operation:defect:type:delete` | 未引用才允许物理删除 |

编码重复 `DEFECT_TYPE_DUPLICATE`；版本变化 `DEFECT_TYPE_STALE`；类型不可用 `DEFECT_TYPE_INVALID`；已引用删除 `DEFECT_TYPE_REFERENCED`。跨父工序对象返回NOT_FOUND，不依据名称自动转移。

工序详情新增“不良类型”tab，复用既有CrudPage编辑器、分页和权限，未保存工序不能配置子类型。默认费用须明确选择；新建表单不静默填写LABOR。嵌入面板分别保存、上报pending/busy，保留未保存关闭确认和切厂/撤权/卸载迟到响应清理。

## E. ProductionDefect 增量及历史字段

新增可空列：defect_type_id、defect_type_code_snapshot、defect_type_name_snapshot、defect_type_revision、cost_category、command_version。最后一项仅区分指纹契约，旧命令保持NULL、新现场命令记V2。

`ProductionDefectService.declare → 原execute`复用同一WorkOrder→Execution锁序、原requestKey唯一键、不可变完整版本、assertMutable、实际路径与原数量校验。没有新账本Owner或主子单。

新申报以Execution确切LOCKED实际节点解析Operation；验证路径及路线身份，不推测最后一次报工节点。defectType必须属于该Operation，父工序及类型须有效，配置revision须匹配。同Operation重复出现时仍以具体nodeId定位。产品来自Execution→Material。新事实 `affectedNodeId=discoveredNodeId=nodeId`，不改旧的不同节点记录。

新V2 waste_category保存NULL；旧CREATE与旧无类型申报的REVISE仍要求显式PROCESS/MATERIAL，未借可空Schema放宽原契约。旧REVISE/VOID保留原类别及类型/费用快照，不强制给旧记录补类型；已处理或被重叠引用的申报继续受assertMutable保护。新快照不接受普通REVISE篡改。

列表、详情、版本历史及创建/重放响应均投影新增字段；旧费用NULL响应为UNKNOWN，页面显示“历史未记录”，不将非PROCESS一律显示为料废。本模块此前无正式导出接口，本轮未另建导出Owner；未来导出必须沿用同一白名单和UNKNOWN语义。

备注可空；为兼容旧defect_reason必填契约，空备注的新申报以当时类型名称作为分类说明写入，客户端没有被迫填写额外技术原因或节点字段。不把该说明当作已核实的责任原因。

## F. 费用归类、默认与覆盖权限

- LABOR/MATERIAL/BOTH独立于旧工废/料废、报废/返工及仓库去向；无金额核算或仓库映射。
- 上下文返回类型默认费用与revision。普通用户提交的费用必须等于该版本默认；不同费用需要 `business:quality:defect:cost:override`，后端通过现有Factory授权服务判定。
- 客户端提交实际选中费用和expectedTypeRevision；配置已变化返回冲突，不自动换用新默认。可显式刷新配置后重选。
- 已提交的实际费用、类型code/name/revision冻结；未来配置变化不重解释旧事实。
- UNKNOWN是历史读取投影，不能用于新配置或新现场申报；旧PROCESS/MATERIAL没有批量转换成新费用。
- V41只登记权限，不给admin或现有角色增权。

## G. 新申报 API、UUID、Fingerprint 与 UNKNOWN

| 路径 | 权限 / 用途 |
|---|---|
| GET `/api/quality/defects/executions/{id}/declaration-nodes` | create；复用当前实际路径节点候选 |
| GET `/api/quality/defects/executions/{id}/nodes/{nodeId}/declaration-context` | create；产品、工序、启用类型分页、默认费用、revision、canOverrideCost |
| POST `/api/quality/defects/declarations` | create；规范UUID、Execution、node、type、expectedTypeRevision、quantity、costCategory、remark、occurredAt，可选授权assessment |
| GET `/api/quality/defects/declarations/requests/{requestId}` | create；仅当前用户在当前Factory写入的原操作事实，未找到404 |

新DTO无客户端productId、operationId、操作者或费用金额。Controller只做协议转换，原Service负责业务追加。新入口可带授权assessment，但本轮不合并ALLOCATE/START/RESULT，不进入Phase3。

原指纹算法保持不变，旧requestKey仍允许最长64字符非UUID。V2摘要以版本前缀、可信用户及规范化载荷生成，decimal/时间精度处理明确。查原key及摘要发生在重新读取当前类型启停/默认费用之前；同UUID同载荷返回原版本，不因配置变动失去回放；不同载荷返回幂等冲突。旧接口与V2不能将同一key冒认为另一命令。

前端在发送前将完整V2载荷与UUID保存到按账号+Factory隔离的sessionStorage；超时/5xx/已不确定后的403均保留原意图。重新打开/刷新后恢复，支持查原结果和原样重试；404只表示尚未读到已提交事实，不能据此生成新UUID。明确初次4xx拒绝后才允许修改并新提交。

存储不可用时不发送；存储损坏时阻止新申报并提示人工核对，不静默替换原意图。撤权清空页面及请求代次，但保留恢复存储，恢复授权后才继续查询。切厂/换Execution/卸载后旧响应不回填页面。

现场报工页可以从已选真实节点展开简化申报；独立不良页的“新增申报”打开V2，历史兼容创建仅在具备create+revise时展示。旧历史查询、核实、报废和返工面板保留，后台旧CREATE兼容接口仍可使用，不能据此把旧无类型记录伪装成新V2数据。

## H. 核实权限旁路修复

原 `ProductionDefectService.create/revise` 和新 `declare` 共同调用核实授权检查。携带assessment就必须具有当前Factory的revise权限，无权明确403，不静默忽略。按现有请求授权服务及请求内权限复用，不按角色名字硬编码质量人员，不把权限存入长期Token。

| 用户权限 | 旧/新CREATE | 携带assessment | 直接treatment |
|---|---|---|---|
| 仅create | 可以普通申报 | 403，无新增核实/不良事实 | 403 |
| 仅revise | 403 | 403 | 可以核实现有合法申报 |
| create+revise | 可以 | 可在原申报事务中核实，仍需合法依据/额度 | 可以 |
| 无权限 | 403 | 403 | 403 |

费用覆盖独立，不因拥有revise而自动取得。原HTTP鉴权及后续处置服务的版本、父事件和余额规则继续有效。

## I. 自动汇总语义

`GET /api/quality/defects/statistics` 使用read权限，支持Execution、Operation、defectType、费用、起止时间和核实状态过滤，PageHelper分页，最多100组。时间筛选针对申报occurred_at；起止倒置及非法枚举明确拒绝。

`DefectStatisticsExtraMapper`跨不良账本、routing_node和按申报版本聚合的质量事件查询，先排除旧revision/最新VOID，再聚合；属于确有跨表语义的Extra，不复制单表CRUD。

输出原始申报、独立不良、重叠观察、待核实、恢复及净不良。恢复含原有有符号CORRECT_RESULT修正，只在INDEPENDENT内贡献；净不良=独立量-可信恢复。报废/返工失败不再次扣减。

按Operation ID、type ID、费用分桶；类型改名不拆成新类型，`typeNameVariants`标示是否观察到多个历史名称，显示名取一项确定的历史快照，不伪装为当前配置名。每条事实的完整快照仍在历史接口。LABOR/MATERIAL/BOTH/UNKNOWN四桶互斥，BOTH只占一桶，不把两个“涉及费用”指标相加当总不良。

前端查询服务端汇总，不累计当前分页。现场报工页将旧绑定defectQty的“有效不良”改成“原始申报量”，另列独立不良、待核实、已恢复及当前净不良；数量公式未改。

## J. 原质量与生产数量回归

新增 `QualityV2HttpTest` 使用双独立H2业务库、真实Service/XML/Spring事务和HTTP鉴权，覆盖配置、归属、费用、核实授权、历史、并发、统计与原报废返工串联。不是实际部署或真实MySQL双厂证明。

| 测试矩阵 | 本轮证据 |
|---|---|
| 同工序多类型、跨工序同码同名不同身份、重复码、跨父转移、版本冲突、停用、删除引用/未引用、权限拒绝 | 新V2 HTTP测试及真实生成Mapper |
| 实际路径节点、类型归属、默认/覆盖、LABOR/MATERIAL/BOTH保存、UNKNOWN新写拒绝、配置变化与原UUID重放 | 新V2 HTTP测试；403/409/422等拒绝分开断言 |
| 同UUID并发、不同载荷拒绝、同码并发一个成功、同ID双厂独立 | 新V2 HTTP测试，共用正式父工序及执行数据库锁 |
| 仅create/仅revise/两者/无权，旧CREATE、新CREATE、直接处置 | 新V2权限矩阵与旧ProductionDefectHttpTest |
| 独立4、重叠2、待核实1、返工处理2/恢复1/失败1 → 原始7、净不良3 | 新V2统计/处置串联；类型改名后仍为一个类型桶 |
| 旧非UUID重放，REVISE只算最新3而非2+3，VOID后归零，UNKNOWN不误推 | 新V2历史统计与原指纹契约测试 |
| 独立/重叠/UNVERIFIED、报废、原节点返工、失败、晚不良、basis失效、原交接/接收保留 | 既有ProductionQualityV13/Quantity/Lifecycle、QualityRelease、ReleasedReceipt等随verify回归 |
| 工单取消、Execution完工、writable及合法历史负向订正、LOCAL库存/来源隔离 | 既有WorkOrderCancellation、ProductionReport、LocalFullChainV23及仓储测试随verify回归 |
| 前端默认费用、精确字符串、原UUID未知重试/重新打开、404、撤权、坏存储、切厂迟到响应、配置revision和服务端统计 | qualityV2.test.ts及旧productionDefect/productionReporting等测试 |

旧断言有两项按明确的新业务契约替换，未削弱数量保护：

1. `ProductionDefectHttpTest.permissionsAndConcurrentCommands`原先允许CRUD用户携带assessment返回200，这是本轮要修的旁路；替换为403，再移除assessment证明普通create仍200，并保留原并发/历史断言。
2. 前端旧“有效不良”标签断言改为原始量/待核实/恢复/净不良标签；原精确数量及不可报节点断言保留。旧管理表单测试转向明确的“历史兼容申报”，普通用户验证只看到V2新增入口。

共享HTTP夹具接入V39适配DDL、新Mapper和显式授权Fixture；既有条件MySQL夹具只补明确的测试授权依赖，没有为生产增加fallback或启用真实写测试。

## K. 构建、MySQL/API/UI 实际验收

最终命令、数量和状态见下表及证据目录。本轮先跑原基线verify成功，再实施和跑定向/完整回归。

| 验证 | 实际结果 |
|---|---|
| 后端完整verify | `mvn -B -ntp verify`成功；741项、696通过、45条件跳过、0失败/错误，185套件；`verify-acceptance.log` |
| 前端完整test | Node22.23.1，63文件、379项通过、0失败；`ui-tests-complete.log` |
| 前端build | Node22.23.1；vue-tsc+Vite，`ui-build-complete.log`；仅既有大chunk/样式弃用提示 |
| Java格式化 | 项目IntelliJ原生formatter，23个本轮Java文件及最终新增断言；日志 `format-*.log`，IDE退出阶段既有插件警告单列 |
| 新前端文件lint | 项目ESLint定向检查/修正，无错误；未对原已dirty页面做全仓格式化 |
| 差异检查 | 后端通过；前端全仓有任务前领料页第3行尾空格，原文件SHA保持，本轮文件定向检查通过 |
| 打包smoke | `/usr/bin/python3 scripts/smoke-packaged.py`：10通过、0失败；`smoke-final.log`。独立回环打包检查，无真实业务数据库 |
| 真实PROD只读 | 绑定、迁移版本、类型表未建及原质量行数已核实，`prod-readonly.txt`及`prod-readonly-final.txt`；最终新增权限登记数0，新类型表数0，仍为V38/原3条不良与8条处置 |
| 真实MySQL迁移/写API | **BLOCKED**：用户本轮明确禁止仅沿用历史PROD写授权；未执行V39/V41，未新增生产测试事实 |
| 真实浏览器业务写入 | **BLOCKED**：未部署本轮后端/迁移、未获真实写授权，未启动/停止/重启用户5173/8081服务 |

真实只读不等于新功能在PROD可用。隔离MockMvc HTTP不冒充部署服务；Vue组件测试和build不冒充实际浏览器业务验收。

失败与修正记录：

- `backend-first.log`：旧CRUD携带assessment的200断言被新403正确拒绝；按上述新断言替换后通过。
- `verify-first.log`：枚举合同检查发现历史UNKNOWN投影使用裸字符串；改为领域枚举及SQL绑定常量，未豁免或删除该测试。
- `ui-tests-first.log`：旧“有效不良”标签断言失败，按新正确语义替换。
- `ui-build-final.log`：新增测试helper参数误推断为单个Vue组件类型；改为Vue Component类型后build通过，未放宽生产TypeScript配置。
- 后续结果不隐藏上述失败；中途通过的verify不替代最后新增断言后的完整结果。

## L. 历史事实及 Factory 隔离

没有改Factory模式、ERP绑定、角色授权或现有生产数据；没有真实ERP调用、LOCAL库存动作、自动放行或自动仓储单据。新表/模型/DTO不带factory_id，沿当前Factory业务库路由，缺Scope仍由基础设施拒绝。

类型删除保护检查全部申报版本；旧record_action/revision/request指纹及触发器契约保留。新字段NULL不被猜成当前默认；历史查询不按类型启停过滤。原成功UUID优先查原事实，配置停用/改费后仍可重放；后续核实/处置继续关联原申报及确切版本。

本轮双厂同ID配置隔离由H2 Fixture验证；真实PROD当前LOCAL，不能据此宣称新质量功能在EXTERNAL环境已经验证。历史V2.3.1的真实多厂结论保留原证据边界，不冒充本轮新类型的真实双厂验收。

## M. 未完成范围与受控后续

- PROD V39/V41正式迁移、Flyway validate、迁移后的MySQL/API/UI写验收未执行；须取得本轮明确授权，复核绑定、冲突和受影响范围备份后通过正式机制执行。不会因本报告落盘而自动修改PROD。
- 未部署本轮包，用户原服务未启停。新代码要求匹配迁移，不新增旧Schema静默兼容回退。
- 未开发Phase3新的退工序/退仓正式预算或新返工目标；不新增强制检验、成本金额、ERP质量单据、不良主子单或替代质量状态机。
- 当前质量事件聚合仍保留原1000申报/5000事件边界；本轮统计在数据库分页，但未擅自重写原数量服务扩容。未做生产规模压测。
- 若将来要改已处理记录的费用/类型，不能走普通REVISE绕过assertMutable；本轮没有增加该纠错能力。

代码回退只反向撤回本轮增量，不能reset已有dirty文件。迁移尚未在PROD执行，无生产数据回滚需求；将来执行后不得为回退删除已被引用配置或不可变事实，也不重写迁移历史。

## N. 判定与交付回执

**CONDITIONAL GO**：Phase1/2代码与隔离验证可审查交付；上线及真实PROD GO仍以迁移和正式验收完成为条件。新增类型、费用、逐次事实和汇总沿用原质量Owner；核实权限旁路已在服务端封闭，原数量服务算法不变。

交付本报告A～N、正式迁移与代码、测试矩阵和忽略目录中的脱敏证据。未提交、推送或部署。未把SKIPPED、H2/Mock、只读或旧版本验收写成真实PROD写入通过。
