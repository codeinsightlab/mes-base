# Base MES — 配置体系收敛与废弃配置清理 V1.0

日期：2026-10-17。结论：**CONDITIONAL GO**。确定性清理、自动化和只读数据保护验证通过；新 JAR 未由本任务部署或重启，真实 ERP 联调、未知部署配置与正式迁移流程不在已验证范围。

## A. 原配置体系与权威关系

开始时 HEAD 为 `d7c6f95`。保存基线时已有并行 E10 修改：AGENTS、ADR-005、local-erp-business-boundary、DigiwinE10MoQueries；前端存在大量原有未提交修改，SDK 干净。基线状态、diff、HEAD 和文件 SHA-256 位于 `.local/config-consolidation-20261017/`，私有资料不进入报告或 Git。

|概念|原入口|本轮后的权威|处理|
|---|---|---|---|
|当前 Factory 业务模式|受管快照；非托管时 WarehouseIntegration 可读按厂 YAML|FactoryIntegrationSnapshot / Context / Registry|移除正常运行 YAML 兼容分支，缺快照失败|
|工单/仓库业务来源|持久化 source、BusinessSourceRules|保持原实现|不从当前模式推断、回填或改写|
|E10 如何连接|厂级加密 OAPI 配置、稳定引用和版本|保持原实现|不复制 YAML endpoint/provider，不移除 host/service|
|非 E10 通用注册|mes.integration.connections 及可解析凭证引用|仍有 Registry 消费|REVIEW，未证明废弃，不删除|
|生产用料|数据库用料优先，无数据库行时允许旧工单 YAML|保持原实现|不改变数量公式或兼容开关|
|历史领料及撤销|持久化 LOCAL_ONLY/CONNECTED、Source、provider/binding、操作 UUID|保持原实现|不改派当前 ERP 或当前模式|
|池/账号/存储/日志/限流等部署参数|Profile、环境变量、框架配置|保留职责明确的原入口|不把框架间接消费误判为无用|

## B. 配置清单与方法

逐键证据见 [配置清单](2026-10-17-configuration-inventory.md)：515 个键、动态键族或独立配置上下文记录；KEEP 433、REMOVE 7、REPLACE 13、REVIEW 62。相同族的实例、文档写法和测试声明单独计行，**不等于删除了 13 套业务配置**。

清单包含声明/消费文件与行号、用途、代码默认值或声明值、来源、状态、风险和决定。私有 dev/prod 的真实值全部省略，凭证类默认值脱敏。`[TEST]` 只表示测试；`[DOC]` 包含历史文档引用，不等于当前部署生效。Codegen 输入文件与 Spring 运行属性明确区分。

扫描使用 `rg --files`、Java 属性/条件装配检索、YAML AST（含行号）、脚本与 XML 占位符扫描，以及动态键的人工调用链核对。完整可复核扫描脚本保存于私有基线目录 `build-inventory.py`；RuoYi 删除前位置固定引用初始 HEAD，避免期间外部提交改变历史证据。

覆盖 application*.yml、config 示例、原型和生成器 YAML、Environment/getRequiredProperty、Binder、ConfigurationProperties、ConditionalOnProperty、JVM/环境变量、main/test 配置、Shell/Python launcher、Logback XML、Maven 属性、前端三种 .env、Vite/Nginx 目标及文档示例。仓库未发现 bootstrap 文件、properties 配置文件、POM profile 或后端 Docker/Compose 入口。未发现独立 WarehouseIntegrationProperties / WarehouseIntegrationMode；实际公共枚举为 ErpActionResult.Mode，仍在使用，未删除。

外部进程注入的任意凭证属性名、操作系统环境或未提供的部署文件不能静态穷举，保留 REVIEW，不宣称已证明所有未知部署配置无用。

## C. 旧 Warehouse YAML 全部消费链

清理前正常运行消费者仅为 `WarehouseIntegration`：

1. `snapshot` 在受管模式缺快照时拒绝，但 Runtime 关闭时允许返回 null。
2. `configuredMode` 先读快照，否则拼装 `mes.integration.warehouse.factories.<factoryId>.mode`。
3. `submissionSelection` 无快照时继续读取同前缀 provider/binding-key，影响新的领料与生产供料。
4. 多个 HTTP/H2/MySQL 测试通过旧 YAML 选择 LOCAL_ONLY/CONNECTED；ERP 部分已手工建立快照，LOCAL 部分却依赖生产兼容逻辑。
5. 唯一另一个 main 消费方是已发布 `V34__FactoryBusinessIntegration.legacy/migrate`：一次性导入旧环境身份，不是日常路由。

下游链包括 `BusinessSourceRules.current/order/warehouses`、领料 CRUD/提交、ProductionInputSupplyService、LocalStockService 及生产接收/完工准入；没有以“调用方可能不受管”为理由保留新的本地兜底。

清理后 main 中只有 **V34 历史导入**仍出现旧前缀；WarehouseIntegration 已无 Environment 依赖。V34 源码、checksum、FactoryFlyway 注册与迁移正负例全部保留，未修改已发布迁移。未升级旧环境仍须按原迁移契约受控升级；升级后不得继续使用 YAML 决定业务模式。

## D. Factory 模式收敛与关联清理

`WarehouseIntegration.snapshot` 强制 `FactoryIntegrationContext.required()` 并校验 factoryId；LOCAL 映射 LOCAL_ONLY，ERP 映射 CONNECTED。上下文缺失、跨厂、缺唯一 Adapter、错误 provider/binding 都明确拒绝，Runtime 关闭也不会放行。

`MaterialIssueSubmissionService.prepare` 在既有准入后直接取得 required 快照，移除新命令冻结 Source 时已经不可达的 null 快照兼容分支。此改动不修改历史读取或历史事实的 null 处理。

相关测试统一使用 `src/test/.../FactorySnapshotFixture`，显式构造 A/B/SIMPLE_B 的合成 LOCAL 快照，ERP 场景显式指定 provider/reference/version；未知工厂拒绝，没有把旧生产模式机制简单改名后搬到新配置。请求过滤器沿用真实权限与业务事务入口，跨线程仍显式进入快照。

Factory 初始化默认 LOCAL、模式锁定、解绑不解锁、固定请求快照和分库边界均未修改。

## E. ERP 身份和连接配置

Factory 的 runtimeMode/provider/reference/revision/locked 决定业务体系和绑定；工厂加密 OAPI 参数决定如何连接。现有 E10 分支不再从通用 YAML 注册中选择 E10，endpoint 不与通用注册重复维护。host 的七字段及 service 的三字段完整保留，空 host.id 的显式语义、账套、服务标识和配置版本检查保持。

`DigiwinE10Configuration` 仍按 Factory Runtime 条件装配；装配和保存参数不发送请求，缺 Factory/ERP 上下文不能调用。默认配置的 Runtime 关闭状态及对应 Bean 负例保留；实际 dev 中 Runtime 启用不等于允许 LOCAL 工厂发 ERP 请求。没有重新增加 `digiwin-e10.enabled` 或另一套模式开关。

SDK 原生 HTTP 默认参数仍为连接/借连接 3000ms、响应 10000ms、整体 30000ms，最大连接 20、响应 4MiB。MES 没有额外 YAML 覆盖这些参数。没有新增虚构 Token/Secret 字段，没有修改 SDK Gateway/DTO 或 SDK AGENTS。

执行期间并行会话新增 MO/CMS Query/Read 和同步预览代码；本任务未编辑这些 E10 文件或 V39 权限迁移。最终全量验证包含当时可见共享代码，但不据此宣称本任务实现了同步或真实 E10 联调。

## F. 生产历史兼容配置

保留 `mes.production.input.legacy-config.enabled`（代码默认 true），以及 `mes.production.input.factories.<factoryId>.workorders.<id>.material-id/total-required-qty/work-center/warehouse-ids`。

`LocalProductionSupply.query` 会合并数据库需求与显式旧候选；只要存在数据库用料行，即使停用、材料/单位不符或仓库不匹配，也不回退该工单 YAML。只有没有数据库行时才读取旧参数，工单候选继续要求可信 LOCAL、RELEASED 和匹配物料/仓库。历史投入与重放另用冻结事实。

私有 dev 中工单 117 的旧用料参数保留。没有将“已有数据库表”或“当前工单已取消”直接当作删除所有历史兼容的充分证据；也未自动导入这些参数、回填 source 或改变比例与数量公式。

## G. 删除/替代项及逐项证据

|原 Key / 分支|原声明与消费者|新权威或替代|原因|验证|
|---|---|---|---|---|
|mes.integration.warehouse.factories.<factoryId>.mode|私有 application-dev.yml:3–7；WarehouseIntegration.configuredMode|FactoryIntegrationSnapshot.mode|受管权威已有，非托管兼容会形成第二模式入口|WarehouseModeAuthorityTest、来源矩阵、LOCAL 全链路|
|同前缀 provider / binding-key|WarehouseIntegration.submissionSelection 动态读取；测试 MapPropertySource|快照 erpType/configurationRef|新业务不能按 YAML 改派连接|精确 Adapter 匹配、ERP 失败不转 LOCAL、历史 ERP 核实/撤销|
|无快照时 Runtime=false 放行及 Source null 冻结|WarehouseIntegration.snapshot、MaterialIssueSubmissionService.prepare|required 快照及原持久化 Source|消除新业务无可信模式进入的路径；不改变历史 null 事实处理|缺快照拒绝、投入/退料/历史旧提交逆向|
|ruoyi.name|application.yml 原声明；RuoYiConfig.name/getName/setName|Spring application.name 继续负责应用名|本仓无该 getter/模板消费者，绑定字段不产生行为|全量编译/verify、Profile 解析、打包 smoke|
|ruoyi.version|application.yml 原声明；RuoYiConfig.version/getVersion/setVersion|构建版本继续由 Maven 决定|同上，旧字段无运行消费|同上|
|ruoyi.copyright-year|RuoYiConfig.copyrightYear getter/setter，未有有效声明|无运行行为需要替代|全仓静态调用、模板及动态配置检查仅有定义|全量 verify|
|ruoyi.demo-enabled|RuoYiConfig.demoEnabled getter/setter|无运行消费者|不是当前业务/演示开关，删除失效绑定|全量 verify|
|ruoyi.address-enabled|RuoYiConfig.addressEnabled getter/setter|无运行消费者|无地址解析行为读取该字段|全量 verify|
|ruoyi.captcha-type|RuoYiConfig.captchaType getter/setter|CaptchaService 读取 ruoyi.captcha.type|旧平面字段不被使用；保留实际验证码配置|Profile、账号/验证码全量回归|
|user.password 标量占位|config/application-dev.example.yml:38、prod.example.yml:39|密码策略 user.password.*；空平台初始化使用受控 CLI|无标量消费者，空字符串会误导为账号密码配置|ConfigurationProfilesTest 校验其不再是标量|
|mes.material.enabled|docs/prototypes/material-sync/application-test-only.yaml 与历史 README|mes.business.enabled|物料 Controller/Service 已按统一业务开关装配，旧开关无消费者|Profile 解析、Material 同步/业务全量测试|
|mes.material.test-adapter.enabled 文档建议|原型 README:15，仅历史示例|测试源码显式注册 Adapter|生产中已无此条件或测试 Adapter，撤销现行启用建议|全量 verify；README 保留当时验收时间线并置顶修订|

RuoYiConfig 类未整体删除：profile/getImportPath/getDownloadPath 仍被 FileUtils/ExcelUtil 等消费。Minio、私有文件授权和现有导入导出未改。没有删除 ErpActionResult.Mode、LocalWarehouseErpService、历史 Source DTO 或实际 Adapter 接口。

## H. 保留配置及理由

- 池、Redis、缓存 TTL、事务路由、日志、Servlet 上传和 SpringDoc：有框架/绑定器/脚本真实消费，不能因无业务字面引用删除。
- mes.platform/runtime/system/business.enabled：区分持久化及模块装配，均不替代 Factory 当前模式；关闭模块是实际测试/空平台/部署边界。
- open-api.*：独立外部消费者身份、权限与限流；不是访问鼎捷的出站配置。data-scope.enabled 是有校验器要求的真实安全开关，默认 false 不能解释为废弃。
- ruoyi.profile 及嵌套 ruoyi.captcha.*：分别用于文件/Excel 工作目录及实际验证码链。
- Codegen 输入、Java/Node launcher、MES_API_TARGET、开发轮询和 Logback 实例目录：真实工具/部署行为，未更改全局环境。
- 已发布 V34 和旧单据 LOCAL_ONLY/CONNECTED：历史升级和事实语义，不能为清理改写或删除。

## I. REVIEW 项及未验证边界

1. 非 E10 `mes.integration.connections.<reference>.*`：Registry 仍能校验并生成候选；未提供全部外部部署和历史绑定清单，不能证明无用途。保留，缺/错连接继续失败，不新增配置或多 ERP 路由。
2. `mes.production.closure.internal-crud.enabled`：默认关闭生成 CRUD，有多个生成 Controller/Service 和模板引用；涉及正式事实入口保护，不能通过清理直接开放或批量删除。保持关闭及既有权限/业务边界。
3. 仅文档存在的旧 ERP 数据源、外部环境名或未确认开关：区分历史记录、框架动态消费和真实部署注入，逐键标 REVIEW。没有因为 UNKNOWN 就判定为废弃。
4. 历史工单供料的全量退役需要单独证明历史消费者、冻结事实和停用策略；本轮未做数据迁移。
5. 真实 E10 Query/Read、历史真实 ERP provider/binding 恢复与模式迁移未验证，未发送真实请求；不是全链路 ERP GO。

## J. AGENTS 与文档变化

后端 AGENTS 新增“2026-10-17 — 配置唯一权威与废弃配置管理”，固化单一快照权威、禁止按厂 YAML 覆盖、持久化 source、ERP 连接分工、缺配置失败、历史冻结恢复、动态/历史/运行证据审查及 V34 不可重写；原 ERP 小节的非托管兼容建议已改为引用新规则。

ADR-005 追加同日收敛决定，ADR-007 修正当前模式入口与“默认本地成功”措辞，factory-business-integration-acceptance 置顶区分 V34 历史导入与现行运行规则。物料原型 YAML/README 更新当前开关并保留历史验收时间线。本报告与逐键清单作为长期证据。

未修改前端或 SDK AGENTS；未为配置清理改变生产数量、质量规则或库存 Owner。

## K. 测试、构建与运行证据

所有 Maven 使用用户 Java 21 和标准仓库，未引入私有 .m2/mirror。真实 ERP 测试门控显式关闭：`E10_IT_ENABLED=false E10_MO_IT_ENABLED=false WXPUSHER_REAL_TEST=false`。

|执行|结果/证据等级|
|---|---|
|Java 定向：LocalWarehouseErpTest、BusinessSourceRulesTest、领料/撤销/ERP/投入/来源/Core/接收/库存 HTTP|55/55，Mock/H2/回环；日志 /tmp/mes-config-cleanup-directed.log|
|新增 WarehouseModeAuthorityTest、只读数据库初始审计|3/3；旧 YAML 在 Runtime true/false 下不能覆盖快照，缺快照拒绝，ERP Adapter 缺失或失败无 LOCAL 回退|
|mvn -B -ntp verify（最终）|BUILD SUCCESS；709 项，664 通过、45 门控跳过，0 失败/错误；/tmp/mes-config-cleanup-verify-final.log|
|ConfigurationProfilesTest|真实解析所有 application*.yml 和示例，无重复键、旧模式/E10 YAML；dev/prod 示例使用 Spring ConfigData 合并与绑定，无数据库/Redis/ERP启动|
|前端受影响测试|factoryOapi、factoryIntegration、request，3 文件36/36；/tmp/mes-config-cleanup-ui-tests.log；未修改前端源码|
|scripts/format-java|本轮清理 Java 文件及新增测试通过；并行 E10 文件未由本任务格式化|
|/usr/bin/python3 scripts/smoke-packaged.py|10/10；隔离回环端口，不启停用户服务；/tmp/mes-config-cleanup-smoke.log|
|git diff --check|本轮后端与前端环境文件检查通过；不处理无关脏文件|
|真实 MySQL 前后只读核对|4 个中心工厂记录及指定业务库42张表的行数/内容 SHA-256 全部相同；176个指定 ID 全部 CANCELLED|
|现有 LOCAL 页面/API只读复核|SIMPLE_B 的工单默认活跃列表24条；已作废筛选179条。指定176张是否仍作废由 SQL 逐 ID 证明，不以列表总数替代。仅筛选/GET，无业务写入|

只读 MySQL 命令：`MES_CONFIGURATION_READONLY_ACCEPTANCE=true MES_CONFIG_AUDIT_PHASE=before/after ... -Dtest=ConfigurationConsolidationReadOnlyTest -Dsurefire.failIfNoSpecifiedTests=false test`。连接由用户私有 dev 中心配置解析，再核实 SIMPLE_B 的启用数据源 ID/主机/端口/数据库绑定；目标为 `192.168.18.25:3306/mes_base_dev_business_simple_b`，没有另建或替换库。指纹文件仅保存计数/哈希，无业务原文，位于私有基线目录。

首轮全量失败点是旧历史撤销测试只设置 FactoryScope、未显式取得快照。共享工作区已将该测试准备动作改为显式 LOCAL 快照，保留原撤销与库存逆向断言，最终全量通过。首次只读审计的日期类型序列化问题也已改为 JDBC 字符串指纹并复跑通过；这些失败不计为成功证据。

H2/Mock 测试覆盖新 LOCAL 投入 UUID 重放、原 LOCAL 退料、UNKNOWN 核实、原库存动作逆向、生产接收重放、当前模式改变后按原 provider/binding 恢复、来源隔离、工单取消与活跃列表过滤。没有在真实业务库新增领料/报工/质量/接收或库存记录来验收。真实数据库后测全表哈希完全一致。

浏览器截图：`.local/config-consolidation-20261017/cancelled-orders-readonly.png`。运行中服务未由本任务替换，因此该只读页面证明现有服务状态；本轮新代码的运行证据为最终打包 smoke 和自动化，不能称为已部署验收。

## L. 并行修改保护与工作区变化

基线文件包括 backend/frontend/sdk 的 status、diff、HEAD、SHA-256。未 reset、清理、提交、推送、改真实 Factory 或业务数据；没有新增执行 SQL 或调用 ERP 写操作。

执行期间观察到 HEAD 从 `d7c6f95` 外部变为 `3493788`（reflog: commit init），该提交收纳了当时共享工作区变更，**不是本任务调用 Git 提交**，执行者不能从现有证据确定。本任务保留该状态、不撤回或重新提交。最终证据基于共享代码快照，不将并行 E10 实现归为本轮清理成果。

前端基线内只有 workorder/index.vue、wmWarehouse/index.vue 的哈希在任务期间因并行工作变化，本任务未编辑这些文件；SDK 基线文件全部哈希一致。新增的并行文件不在最初哈希集合中，另记录最终状态；不宣称其他会话没有改动。

## M. 交付结论与后续生效

**CONDITIONAL GO：配置清理范围完成并通过验证。** 正常新业务只有 FactoryIntegrationSnapshot 模式权威，旧仓储 YAML 路由已退出；历史事实与合法恢复未因清理改变，无法证明无用的配置已保留并列明。

新 JAR 已由 verify 生成，但需要用户按现有服务所有权自行安排重启后才能用于部署复验。本任务没有为追求运行 GO 擅自启停服务、切厂、迁移业务数据或发送真实 E10 请求。回滚应针对本轮明确代码/配置项进行审查；不得恢复缺快照放行或用 YAML 改派历史操作。V34 和已有迁移记录不回写、不删除。
