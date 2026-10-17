# 鼎捷 E10 SDK 消费方接入

日期：2026-10-17。当前入口为工厂管理的“E10 OAPI 配置”；参数属于工厂中心配置，禁止在 dev/prod YAML 中维护第二份 E10 配置。

## 工厂页面与持久化

用户先保存工厂，再在工厂列表对应行打开“E10 OAPI 配置”。表单维护完整 endpoint、host 的
ver/prod/timezone/ip/id/lang/acct，以及 service 的 prod/ip/id。host.id 必须显式提供，允许空字符串。
时区及 service.id 原样使用，不补模块后缀、不猜测其他身份；旧系统 service.namespace 不属于本 SDK 契约。

用户明确指定 `ktg-mes` dev 的 `oapi.YWMS` 为本次参数来源。真实值只通过管理页面填写，不写进文档、
测试、样例或日志。原先短暂写入私有 dev 文件的参数已移除，默认 application.yml 的 E10 装配开关也已移除。

复用现有中心表 `mf_factory`，正式 Flyway V38 增加：

- `oapi_config_ref`：首次保存生成的不透明稳定连接引用。
- `oapi_config_cipher`：整个连接参数 JSON，经现有 AES-GCM 机制加密。
- `oapi_config_revision`：连接参数版本；保存同时校验并递增工厂 revision，拒绝并发覆盖。

只扩展已有工厂基础设施，不新增业务表、独立 CRUD、凭证中心或第三数据源。运行环境仍需要既有
`mes.runtime.datasource-key`，它是部署密钥，不能移到普通工厂响应或浏览器存储中。

`GET/PUT /api/system/factory/{id}/oapi` 均要求平台角色及 `system:factory:edit`，
GET 是有权限管理员专用的连接维护响应，普通工厂列表、详情和连接候选不返回参数或密文。
保存检查字段完整性、长度、控制字符和 HTTP(S) URL；不含 URL 账号、查询参数或片段。
配置损坏或密钥不匹配明确拒绝，不降级、不发送请求。

## 与运行模式分离

保存参数只做连接准备，不切换 LOCAL/ERP，不写 `erp_provider/erp_config_ref`，不解锁工厂，
不创建 EXTERNAL 业务记录，不写库存，也不向 ERP 发送请求。页面显示“真实 ERP 读取尚未验证”。
SIMPLE_B 继续保持既有 LOCAL 权威与模式锁定；配置准备完成不等于已授权切换。

新工厂按既有工厂表单选择 ERP 类型和该厂连接，仍受 Registry 和原有模式锁定控制。
一个工厂只保存一个 E10 配置；进入 ERP 或存在 ERP 绑定后，本入口只读，连接身份变更必须走受控迁移。
不提供普通表单覆盖已冻结身份的路径，本次未实现模式迁移流程。

FactoryIntegrationRegistry 读取当前厂的数据库 E10 配置，按 factoryId、provider、reference、revision
准入；旧 YAML 中的 DIGIWIN_E10 连接不再进入候选或运行快照。非 E10 的既有通用 Adapter 配置保留原行为。
普通中心查询使用构造器注入 Mapper；只有业务事务活动时，才使用既有独立中心只读窗口。

## SDK 装配与业务调用

继续使用 `com.lixin:lixin-common-capability-digiwin-e10:0.1.0-SNAPSHOT`，
不复制 SDK 的认证、Header、HTTP、序列化和错误解析。没有新增 Token 字段或虚构凭证来满足旧认证引用。

Factory Runtime 启用时装配查询消费方；启动不读取所有连接、不创建 SDK 客户端、不发送网络探测。
显式 Query 要求已有合法 FactoryIntegrationContext，mode=ERP、provider=DIGIWIN_E10，
工厂、连接引用及配置版本与数据库一致。首次业务调用惰性创建并缓存对应客户端，退出时统一关闭。
LOCAL、缺上下文、跨厂、错误引用或过期配置均在请求发送前拒绝。

当前只提供既有 `DigiwinE10MoQueries.queryMo(E10QueryRequest)`，内部复用
`OapiMoGateway.mo().query`。请求显式提供 pageNo/pageSize、isGetSchema/isGetCount、conditions/orders。
SDK 异常继续原样传播，不转为空结果、不自动重试，不记录完整报文或身份。
没有新增 ERP 查询按钮、自动同步、库存扣减或工单业务写入。

## 验证与历史记录

初次 SDK 接入曾完成 Maven 依赖解析、710 项全量测试统计、38 项旧装配定向测试及 10 项打包 smoke。
这些只描述当时 YAML 装配入口的离线证据；该入口现已由工厂持久化配置替代，相应旧测试已删除或迁移，
不能沿用旧统计证明本次实现。当前测试与数据库、UI 实际结果在本节后续记录。

本次保留回环 HTTP 的真实 SDK 请求/响应、Header、BigDecimal、空页、HTTP 错误、部分失败、
超时 UNKNOWN 无重试及关闭行为检查，并增加加密持久化、工厂隔离、锁定、并发版本和回滚检查。
真实 ERP Query/Read 尚未执行；保存或读回参数不代表真实 E10 联调完成。

## 回滚边界

回滚前停止新增连接配置，保留已保存的加密参数和 V38 迁移记录；不删除历史迁移或通过回填 YAML 绕过工厂配置。
应用回退须评估旧版本对新配置的不可见性；任何已启用 ERP 工厂不得因配置缺失降级 LOCAL。

### 本轮自动化及真实数据库执行结果

- `mvn -B -ntp verify`：BUILD SUCCESS，690 项，647 通过、43 项环境门控跳过，0 失败/错误。
  原 YAML 必填/启动装配测试已随入口退役替换，不将测试数减少解释为业务覆盖丢失。
- 最终追加权限 HTTP 负例后定向测试：17 项通过，覆盖真实 Controller/权限拦截器、
  平台角色与工厂编辑权限、匿名/越权拒绝、非法请求、加密持久化、隔离、锁定、并发版本、回滚及 SDK 回环。
- `MES_FACTORY_OAPI_MIGRATION=true ... -Dtest=FactoryOapiLiveMigrationTest,... test`：
  实际核实中心 `192.168.18.25:3306/mes_base_dev`，SIMPLE_B 仍绑定
  `192.168.18.25:3306/mes_base_dev_business_simple_b`；正式 Flyway 从 V37 迁到 V38，记录成功。
  三个新增列中文 COMMENT 非空，全部工厂原模式/provider/ref/revision/锁定字段保持不变。
  备份位于 Git 忽略的 `.local/factory-oapi-20261017/`，数据备份权限为 0600。
- 前端 `npm run test`：60 文件、358 项全部通过；`npm run build` 通过，保留既有大包体积提示。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10/10；隔离回环端口测试，不接管用户服务。
- 新 JAR 已包含 OAPI 服务和 V38；源码与打包 dev 均无 E10 连接参数。后端及本轮前端文件
  `git diff --check` 通过；前端其他并行修改中的 `issue/mesMaterialIssue/index.vue` 既有尾空格未处理。

日志：`/tmp/mes-factory-oapi-verify.log`、`/tmp/mes-factory-oapi-http-test.log`、
`/tmp/mes-factory-oapi-combined.log`、`/tmp/mes-factory-oapi-ui-test-final.log`、
`/tmp/mes-factory-oapi-ui-build-final.log`、`/tmp/mes-factory-oapi-smoke.log`。

### 前端实际配置与读回

用户自行重启新 JAR 后，使用既有登录会话，在 `http://localhost:5173/system/factory`
的 SIMPLE_B 行打开配置弹窗，逐项填写用户指定的 ktg-mes dev OAPI 参数并点击保存。
页面返回“已保存 · 配置版本 1”，关闭后重新打开，全部字段从服务端正确恢复，空 host.id 保留。

真实 MySQL 只读复核通过：SIMPLE_B 的配置密文可以由运行环境密钥解密，endpoint、host 七字段、
service 三字段均与指定源逐项一致，配置版本为 1；runtime_mode 仍为 LOCAL，erp_provider/erp_config_ref
仍为空，未修改业务运行权威。Flyway 38 条迁移全部验证通过，无新增迁移执行。
命令环境 `MES_FACTORY_OAPI_MIGRATION=true MES_FACTORY_OAPI_READBACK=true`，定向
`FactoryOapiLiveMigrationTest` 1/1 通过；日志 `/tmp/mes-factory-oapi-live-readback.log`。

浏览器检查包括初始空配置、保存状态、关闭后重新读回，以及 390×844 窄屏字段滚动和固定底部操作。
临时视口已恢复。截图保存在 `.local/factory-oapi-20261017/oapi-saved.png`，包含连接身份参数，
仅作本地验收资料，不提交 Git。仓库配置按用户后续要求暂停，本轮没有改动仓库配置或实施仓库迁移。
未执行任何真实 ERP Query/Read 或写入，也未完成工单、仓库同步业务验收。


## 2026-10-17 V2.0：MO/CMS 只读消费与同步准备

复用现有工厂加密配置、Registry、Snapshot/Context 和 `E10OapiClient`，不复制 Header、认证、HTTP 或 JSON 错误解析。客户端惰性创建，启动/配置保存/能力检查均不请求 ERP。

`DigiwinE10MoQueries` 提供 `queryMo(E10QueryRequest)`、`readMo(docNo)`、`queryWarehouse(E10QueryRequest)`、`readWarehouse(warehouseCode, ownerOrgRtk, plantCode)`。Query 必须显式分页、schema/count、conditions/orders；页码 1..10000、页大小 1..100。Read 使用 SDK 类型化 Key 和 Read_0001，保留 DOC_NO 或三个仓库定位字段，不自动从 Query 响应猜 ReadKey。Read 目前仅为受上下文约束的进程内接口，没有公开原始 DTO/报文 HTTP 入口。

当前没有目标部署下的可信身份及映射证明，因此 **未开放正式同步**。没有 E10 `ExternalPlan`/`ExternalWmWarehouse` 转换或 `WmWarehouseQueryPort` 注册；既有可信写入入口及其来源/冻结门不变。此处是受证据约束的部分交付，不是 EXTERNAL 同步完成。

现有工单、仓库页增加 ERP 同步准备：

|接口|权限|行为|
|---|---|---|
|GET /api/production/workorder/erp-preview|workorder:sync|返回支持情况、writeEnabled=false、阻断原因；不请求 ERP|
|POST /api/production/workorder/erp-preview?pageNo=1&pageSize=20|workorder:sync|只读取一页工单候选|
|GET /api/wm/wm-warehouse/erp-preview|business:wm:warehouse:sync|返回仓库准备能力；不请求 ERP|
|POST /api/wm/wm-warehouse/erp-preview?pageNo=1&pageSize=20|business:wm:warehouse:sync|只读取一页仓库候选|

POST 使用 SyncCoordinator 的 e10.mo.preview / e10.warehouse.preview 独立键、五分钟租约和 run token。开始/完成前校验执行权，失败记录 FAILED 并保留异常链；不设同步水位，不自动重试或调度。协调器 SUCCEEDED 仅表示该页读取完成，HTTP status 始终为 PREVIEW_ONLY，created/updated/skipped=0。执行权丢失不发布成功预览。

没有证据支持 orders 内部格式与全局稳定排序，故显式空条件/排序只用于候选浏览；满页仅提示可能有下一页，不宣称全量。保留同码候选及其原状态，禁止按显示编码去重或推导 externalId；缺字段显示为空，不将 approve_status 猜成启停状态。数量经 SDK BigDecimal 转十进制字符串，浏览器不先转 Number。

V39 正式中心迁移只登记 workorder:sync，不授予现有角色。页面还要求 ERP 模式、独立同步权限和后端 supported；切厂/撤权/关闭/卸载均清除候选并拒绝迟到响应。仓库原来始终可点但默认未接入的写同步按钮由准备入口替代，避免无实现时提供写入承诺。后端 Service 是最终模式门。

真实 ERP 读取需本轮明确授权目标、组织/账套/环境及调用范围；已有 OAPI 配置不等于读取授权。SIMPLE_B 保持 LOCAL，本轮未对 ERP 发请求、未切换模式、未修改绑定。真实写入和实时 ERP 联调未验收。当前结果与完整缺口见 [V2.0 实施报告](../reviews/2026-10-17-digiwin-e10-workorder-warehouse-sync-v20-report.md)。


## 2026-10-17 V2.1：Dry Run 与正式同步

原 `/erp-preview` GET/POST 保留能力和单页 `PREVIEW_ONLY`。新增两个基础路径的 `/dry-run`、`/commit` POST：

- `/api/production/workorder/erp-preview`，权限 `workorder:sync`。
- `/api/wm/wm-warehouse/erp-preview`，权限 `business:wm:warehouse:sync`。

请求只接受 `pageSize`（1–20）与 `maxPages`（1–5）。provider、binding、Factory 和连接版本从当前受管快照读取；客户端不能提交映射、身份契约或旧 Dry Run 计划。正式同步重新读取并校验，最大100条、90秒远端处理预算（单次在途请求仍受SDK超时控制）；不自动重试、不推进增量水位。最后一页满且超限时整批拒绝写入，需要缩小已核实的服务端过滤范围。逐条事务，不承诺跨页原子提交。

可选 `FactoryOapiSettings.syncContract` 保存在现有加密 OAPI 配置，沿用平台工厂编辑权限、工厂 revision 和连接版本锁；没有另建配置表。已有 JSON 无此字段仍可解码，但提交关闭。契约字段如下：

|字段|要求|
|---|---|
|commitEnabled|显式开放提交；本轮未给真实工厂设置|
|workorder / warehouse|该类型已核实的 Rule，缺失则只读|
|evidence|目标环境验收证据引用，非空；TEST ONLY 字样仅用于隔离夹具|
|identityPointers|1–4个经证实的 JSON Pointer；一个保留原值，多个保存无歧义 JSON 数组字符串；总长≤128|
|sitePointer / expectedSite|必须提供，逐条比对目标据点|
|organizationPointer / expectedOrganization|组织维度存在时必须成对提供，不从编码推断|
|statusPointer / statuses|明确的ERP原状态→MES映射；工单只接受DRAFT/RELEASED，仓库只接受ENABLED/DISABLED|
|approvalPointer / approvedValues|工单审核字段及认可原值，不能将接口成功当审核通过|
|productKey|CODE 或 EXTERNAL_CODE，经确认的 ERP 品号与 MES 产品编码关系|
|conditionsJson / ordersJson|目标服务已核实的完整 JSON 数组字符串（避免应用Jackson3与SDK Jackson2类型跨HTTP绑定）；ordersJson数组非空且应以稳定唯一身份完成排序；不猜测字段拼写|
|readRequired|是否必须 Read；Read 返回唯一记录并与 Query 身份一致才使用，不混拼两次返回|
|readCodePointer / readOwnerPointer / readPlantPointer|仓库Read所需三个真实定位值的来源；缺失返回READ_KEY_INCOMPLETE|

工单字段采用 SDK 的 `doc_no/item_no/unit_no/plan_qty/plan_start_date/plan_complete_date`，仓库采用 `warehouse_no/warehouse_name`。单位必须与启用的MES基础单位编码一致；无换算证据不换算数量。计划日期只接受ISO本地日期/日期时间，不猜时区。产品必须唯一、启用，发布且适用的MES路线必须唯一。

Dry Run 输出逐条action/reasons、产品/路线ID和字符串数量、当前工厂与绑定版本，以及可创建/可更新/无变化/冲突/阻断统计；业务表及库存不写入，协调表仅保存本次运行状态。正式提交新增实际created/updated，返回COMMITTED/PARTIAL_SUCCESS/FAILED；异常通过runToken关联保留完整后台异常链。中断不把未处理记录算成功。协调表保留处理数量，细分统计由HTTP结果与带runToken的日志提供；当前不增加另一套同步审计表。

配置维护不会发送ERP请求。是否允许真实读取必须另按目标账套/组织/环境授权核实，本轮没有真实 E10 调用。已绑定连接的参数仍只读，不能借syncContract绕过受控迁移。
