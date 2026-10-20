> 当前持久化规范：2026-10-09 收敛为中心库 + 多业务库；只有 masterSqlSessionFactory/businessSqlSessionFactory。下方 2026-10-06 的三工厂与外部源章节为历史记录，已被文末修订取代。

# Source 系统与 Factory Foundation 操作边界

2026-10-11 认证与路由准入已更新：随机 Bearer + 安全 Redis 身份、逐请求当前授权及请求内复用、中心 outbox 撤销恢复、固定事务租约、新事务重新准入。旧“逐 SQL/提交前查询最新管理版本并撤销在途操作”不再适用。完整链路、键、部署格式变化及验证边界见 [请求准入](request-admission.md)。其他产品权限、资源、生成器及数据库归属规则保持。

2026-10-06，Phase1 MIGRATING。本文按当前 Source Product 链路更新；此前简化 UUID Platform CRUD/数据库会话/全局配置缓存说明已被迁移替代。实际命令、版本、失败和未验证项见 [报告](MES_BASE_REALIGNMENT_REPORT.md) 与 [验收 JSON](../reviews/2026-10-06-phase1-verification.json)。

## YAML 与装配

通用配置在mes-admin/src/main/resources/application.yml；真实dev/prod参数直接写在同目录的application-dev.yml / application-prod.yml，现有文件按工作区状态保留，本轮不改变其跟踪方式。由Spring Boot标准profile自动选择，中心库与业务库保留现有profile配置。2026-10-09 起不再导入本地 ERP 配置，只装配中心库与 Factory 业务库。默认dev；prod用--spring.profiles.active=prod。首次在新机器使用时，从config里的两个无密码example复制到资源目录、填连接并构建；本地JAR会包含本机profile配置。

Spring Boot 4.1.1有效错误属性为spring.web.error.include-message/include-stacktrace/include-binding-errors，原server.error.*自4.0被标记level=error，已修正。server.address/port仍然有效，直接放在实际profile文件中。

| 配置 | 实际消费者 |
| --- | --- |
| mes.system.enabled、mes.platform.* | PersistenceConfiguration；DruidPools、PlatformPersistence、Flyway |
| Mapper固定包 | system/quartz/platform→中心；mes/modules→业务；不按单个Mapper配置 |
| mes.runtime.enabled、mes.runtime.datasource-key | FactoryRuntimeConfiguration；数据库工厂目录、密码解密、物理库/连接/版本/Mapper 登记校验 |
| spring.datasource.druid.* | DruidPools；stat/wall 真实过滤器与监控 |
| spring.data.redis.* | Redis 客户端、RedisCache、SystemCacheNamespace |
| token.expire-time | TokenService；哈希 opaque token 的身份/版本与 TTL |
| user.password.*、ruoyi.captcha.* | PasswordPolicy、密码重试锁、CaptchaService、登录/注册 |
| xss.* | 通用输入/XSS 防护及富文本服务端净化 |
| springdoc.* | SDK 构建文档；默认 SDK MVC 入口关闭，显式授权 Controller 对外 |
| open-api.* | 外部专用安全链、Token/Lua限流、API目录/授权；业务数据范围开关与独立校验器 |
| minio.* | 私有存储适配器；未提供外部 MinIO 连接时不伪造成功 |

dev正式开发库为 `mes_base_dev`，业务库为 `mes_base_dev_business`，单开发Factory=DEV；Codegen元数据指向业务库，二者不合库。prod按用户新指令使用同一测试实例的独立mes_base_prod / mes_base_prod_business，独立mes_base_prod账号、PROD厂、Redis DB2及.local/prod-files，迁移默认关闭；其真实连接已填入标准prod资源文件。本轮仅验证prod-profile测试环境，未连接真实生产。前端MES_DEV_API_TARGET只允许loopback；18081/15174，Redis16379 DB1。历史phase验收与日常开发分开记录。

## 身份、会话与系统对象

空库没有内置账号或角色；通用产品菜单/字典来自迁移。首次管理员使用 scripts/mes-platform-admin 的空用户表检查和平台事务，Source bigint 身份/显式 PLATFORM role/menu grants，无 userId=1 超权，无 Factory grants。MES_BOOTSTRAP_PASSWORD 由调用者受控提供，应用 PasswordPolicy 来自当前 dev/prod 配置，不写日志。

POST /api/platform/session 为正式入口，Source userName 字段由适配层与 username 登录请求对应；成功返回 accessToken/expiresAt，DELETE 注销。Redis key 按平台 DB 身份命名空间隔离，令牌仅存哈希身份和会话版本；禁用、重置密码、强退撤销，当前角色/权限每次请求查 DB。重试锁使用数据库规范化的 Source userName，大小写/重音等价身份不能绕过。注册 /api/system/register 仅当 sys.account.registerUser 显式 true 才开，不能自行赋角色或 Token；验证码一次消费。dev 密码最小长度默认5，prod默认12，UTF8 BCrypt 最大72字节，UI读取服务端策略。

/api/system/** 是 Source 系统正式可写链路。2026-10-06旧简化Platform用户/角色/菜单/字典/配置/Job实现和专用测试已删除，生产不再接受mes.legacy-test-only开关。V1–V10迁移不改checksum；新开发库从空库运行迁移，仅经空表检查移除phase1_legacy_*归档，历史非空归档不删。源字段/分页/导入导出沿用 Source 成功结构，失败真实 HTTP+requestId；不用 HTTP200 冒充失败。

PLATFORM role 无 factoryId，FACTORY role 必須绑定有效厂。有效空角色身份可登录、选启用厂和 AUTHENTICATED；AUTHORIZED 缺当前范围权限拒绝。Source DataScope 是平台组织数据范围，不能当 Factory 准入。本人/外部 ID 校验在后端；数据权限 SQL 片段由受控服务生成，客户端 params 注入拒绝。无 User↔Factory绑定、首厂回退或跨厂权限缓存。

字典/参数缓存属于平台数据库命名空间，刷新只能处理当前命名空间；Factory配置按明确 kind/factoryId 隔离。操作日志保留 Source 字段与 requestId/actor/scope/costTime，但请求体、Token/Secret/密码不采集。

## 文件、消息与工具

文件默认私有，需 owner+scope+resource。personal 为本人；avatar 绑定本人；notice-media 上传要当前公告写权限，保存后绑定 Source noticeId，非 owner 读取还须当前已发布公告/查询权限。资源名和 fileId 不能当授权。Local storage 私有 UUID 路径防 symlink/穿越；MinIO只迁结构/适配器，真实对象服务尚未验收。附件不公开裸路径，无公司 bucket/账号。

消息保留 Source 表/管理页与本人 inbox/read/SSE 通用能力；sender 来自服务端身份，收件人必须有效且属于被授权平台数据范围。回调地址不执行外部调用；客户微信/邮件渠道不复制。SSE 已断开的响应不转写 JSON 错误。

自动编码保留 Source rule/part/result 和完整组成页面。预览不占号，生成必须 POST；平台事务下规则/计数行锁、周期隔离和溢出拒绝，不用 JVM 锁。当前仅平台编号工具，无 Factory/MES编号消费者。

/api/tool/swagger/json 使用 tool:swagger:view 平台权限，由 Springdoc3.0.3 SDK生成实际 DTO/Controller 契约。/v3/api-docs 和公共 Swagger UI 未登记；Vue3 SwaggerUI5.33.1同源请求，无外部 validator或认证持久化。系统接口携带内部会话；外部 /open-api/** 只接受独立 OpenApiBearer，不自动携带后台 Token/选厂头。

## Quartz 与真实监控

Source SysJob/SysJobLog 完整字段及 Cron/启停/立即运行/日志 UI 对接受控任务。暂停禁止自动触发，但授权手动运行一次仍允许；已领取任务手动再次运行409。相同 DB claim/run token 跨入口复用，执行日志区分状态和请求关联。

合成崩溃恢复必须确认原 worker 已停止、原 run token一致、节点条件匹配，并记录 UNKNOWN后清理；不能只凭超时解锁活跃任务。定时器仍是单节点 RAMJobStore，未交付集群 fire 去重或持久化集群misfire协调；内置 platform/factory-heartbeat 只是受控测试入口，不是业务同步。

Server Monitor读取本机OSHI/JDK；OSHI无挂载时只标明应用所在卷的JDK来源，不编造完整磁盘。Cache Monitor读取本轮Redis INFO/command统计；Druid只显示受授权平台池及参数化SQL模板/计数/WallFilter结果，无连接凭证。实际 Source UI和数据已验证，部署/生产监控未验证。

## SQL、验收与后续

Flyway V1–V10包含 Source完整 sys_* 与范围/会话/附件/编码扩展，旧表归档保存。scripts/export-phase1-sql.py输出原迁移的有序SQL和哈希，不另维护字段；已在新的空独立MySQL库导入并比对真实metadata。SQL不创建用户/角色。手工导入无Flyway history，之后 migrate=false；空库Flyway方式优先。

mvn -B -ntp verify；涉及打包再 /usr/bin/python3 scripts/smoke-packaged.py；UI Node22 npm run test / npm run build。Foundation/PlatformHttp测试显式关闭真实Runtime以隔离用户默认profile。测试源码、Fixture、真实API、浏览器、部署分别记录。浏览器新凭证/导入提交需要用户接手，下载产物尚未取得，不能标记全量验收完成。

OpenAPI通用链路已迁入并通过真实数据库/API验收；Route授权/旧DataScopeGuard依赖ProRoute，按用户本轮决定从Base解耦，具体范围由未来MES校验器表达。UserTask查询真实仓储/报工单据，随MES业务后置。微信/PDA需要实际渠道配置与设备验收，Generator按授权保留后置。

## OpenAPI：客户端、API 授权与可选数据范围

Source 为 common/openapi、system/openapi、framework/openapi 与 OpenApiClientController/页面，DDL 来自 Source sql/openapi/20260930-open-api-platform.sql。通用字段、DTO/VO、行锁、版本撤销、Redis Lua、当前 MVC 目录和分组页面沿用；Java21/Boot4/Jakarta、JavaBean、Security7 专用链与平台持久化归属是 PORT 部分。前端 Source 工作文件有用户修改，因此读取提交 HEAD 的页面，不覆盖或使用其未提交内容。

后台管理 `/api/system/open-api/clients` 的 list/query/add/edit/reset/authorize 是独立 PLATFORM 权限。新菜单不自动赋权；本轮只通过正式 Role API 给自有管理员角色追加这些菜单，原菜单绑定全部保留。创建默认禁用，Secret 只在创建/重置响应展示，SQL 仅保存 BCrypt 摘要。列表和详情没有 Secret/hash。后台通用 @Log 不采集凭证返回。

外部身份为 OpenApiPrincipal，不是 SysUser/LoginUser。专用链只匹配 /open-api/**；Source @OpenApiEndpoint 的 API code 在当前 DB 授权后才调用。内部用户 Token 无法进入此链，外部 Token 无法进入 /api/system/**；没有凭 appKey 查询内部账号、默认厂或长寿命角色缓存。未来 Factory 业务仍须显式 FactoryScope、可信 Mapper/数据库与资源范围策略。

```yaml
open-api:
  enabled: ${MES_OPEN_API_ENABLED:true}
  namespace: ${MES_OPEN_API_NAMESPACE:base}
  token-ttl-seconds: ${MES_OPEN_API_TOKEN_TTL_SECONDS:7200}
  rate-window-seconds: ${MES_OPEN_API_RATE_WINDOW_SECONDS:60}
  token-rate-limit: ${MES_OPEN_API_TOKEN_RATE_LIMIT:20}
  api-rate-limit: ${MES_OPEN_API_RATE_LIMIT:120}
  data-scope:
    enabled: ${MES_OPEN_API_DATA_SCOPE_ENABLED:false}
```

配置统一放 application.yml，dev/prod 可用环境或受控外部 YAML 覆盖。`data-scope.enabled=false` 只关闭业务范围扩展；身份、客户端状态/凭证版本、API code 授权、限流仍执行。开启时必须安装唯一 `OpenApiDataScopeValidator` Bean，缺失或重复使启动失败。校验器接收已验证外部身份、API code、实际请求与 HandlerMethod；不要求工艺/路线字段，不能访问默认业务库补缺。Source `@OpenApiDataScopeIgnore` 仅用于不读业务数据的诊断方法，不豁免认证、API授权和限流。当前没有正式MES实现或开启状态的业务数据库验收；7项定向测试证明安装策略的调用/拒绝/诊断豁免，真实启动证明缺策略开启失败。

Token 是 Source 随机 opaque 凭证。Lua 原子写入摘要记录和当前客户端恢复材料：有效期内重复申请返回同 Token 并重置TTL，普通 API 不续期；禁用/启用/重置递增数据库版本，旧 Token 下一请求失效；撤销删除摘要键。为保留 Source 重复申请语义，受控 Redis 的客户端记录含恢复用 Token，SQL/日志不存明文 Token 或 Secret。Redis 所有键附当前平台 DB 身份，不靠 namespace 字符串冒充隔离；本轮实际同Redis、同clientId/版本/授权的双库负例通过。未验证Redis Cluster部署。

通用实际接口是 diagnostics.ping/client/capabilities 和 verification.ping/client（共5个code）。capabilities只列当前已授权且仍注册的接口；不提供 MES 单据、工艺或用户数据。成功沿用 Source code/msg/data/requestId；外部错误 HTTP400/401/403/429/503/500 与 errorCode/msg/data/requestId 对应，401带WWW-Authenticate，响应no-store。当前操作关联ID和安全日志有实际证据，无请求体/凭证/Token采集。


## 2026-10-06 开发环境与旧代码收尾

管理员凭证/临时库备份在忽略的.local（目录0700），真实profile文件在资源目录且0600/Git忽略；不输出凭证。迁移初始化用 `scripts/mes-platform-admin --username=admin --config=mes-admin/src/main/resources/application-dev.yml --migrate`，密码由MES_BOOTSTRAP_PASSWORD受控提供；日常启动migrate=false。清空新开发库中的空迁移归档用 `/usr/bin/python3 scripts/clean-empty-development-archives --mysql-config=.local/mysql-dev.cnf`；脚本固定mes_base_dev并检查每张表无数据，重复执行无副作用，非空归档明确拒绝。

两个异常目录 `mes-admin/${project.parent.basedir}` 与 `mes-admin/Users` 是旧构建路径错误留下的编译产物，已移出源码目录到target/dev-cleanup归档；当前POM使用maven.multiModuleProjectDirectory，复建不再生成。旧demo内置账号及PlatformServiceTest/PlatformHttpTest、V1-only迁移测试、TestOnlyCodegenFixture及旧验收脚本退役；RuntimeFixture及单/多厂、跨库、线程、权限等有效测试保留。Maven测试显式使用test profile，隔离私有dev配置。

7个phase库删除前备份至 `.local/backups/20261006-phase`；原4个旧dev schema保留。详细当前运行证据及失败见迁移报告本日“dev/prod配置与临时数据库收尾”章节。

Redis16379已停止旧phase进程并从旧AOF/RDB恢复到 `.local/redis-runtime`，配置 `.local/redis.conf`，固定本轮版本二进制 `.local/bin/redis-server`，日志 `.local/logs/redis.log`；`scripts/run-local-redis`可单独重启，不占用其他Redis端口。开发后端日志 `.local/logs/backend-console.log`。当前18081已改为正式开发配置，15174代理health通过；旧phase网页Token与新增表单不应继续作为开发库身份或数据使用。


## 2026-10-06 配置偏好纠正与失效属性修正

按用户要求撤销额外config导入层。两个标准profile文件直接填写真实值，公共application.yml只留共用设置，清理重复的默认端口/Redis连接/空JDBC及文件根；连接占位符已全部移除。config仅保留两个无密码示例，旧runtime双厂示例删除。git rm --cached仅取消实际profile跟踪、本地文件仍存在；未提交或推送。运行脚本不再使用MES_LOCAL_CONFIG或additional-location，普通Spring属性覆盖仍可显式通过启动参数传入。

prod测试库从空库执行原V1–V10，并经全空检查移除16个归档，得到27系统表/0业务表，单显式PLATFORM admin由标准guard建立，无DEV/PROD厂权限。首次凭证在.local/production-test-admin.json，dev凭证不变。清理脚本只接受两个明确自有schema（默认dev，显式--database mes_base_prod），拒绝非空归档；没有访问未知库。


## 三库会话工厂与 Spring 事务（2026-10-06，以下旧入口由文末固定包修订取代）

PersistenceOwner.PLATFORM/BUSINESS/ERP 分别绑定 platformSqlSessionFactory/businessSqlSessionFactory/erpSqlSessionFactory。ERP启用后共三个工厂；未启用时不创建ERP连接。业务会话工厂只有一个，通过FactoryBusinessDataSource在Spring事务开始时租借当前厂的版本化连接池；RuntimeRegistry只管理池，不为每个池创建会话工厂。没有HR/WMS/外部MES类型和按方法切数据库的第二套注解。

Mapper在自身声明归属，自动注册标准MyBatis-Spring MapperFactoryBean；无逐Mapper Bean、中央类清单或Registration记录。Service写入用例使用具名@Transactional，分析查询使用businessTransactionManager只读事务。OwnedTransactionManager沿用源分支扩展Spring事务生命周期的方式，只维护库身份/厂身份和租约；传播、回滚、挂起恢复由Spring负责。跨库事务和直接跨库Mapper访问拒绝；中心元数据查询使用显式独立只读连接，不能执行更新。提交/回滚副作用使用Spring TransactionSynchronization。

SystemUnit、SystemUnitAspect、AnalyticsUnit/Aspect及手工注册配置已移除。PlatformPersistence/BusinessPersistence程序式入口仅供既有基础设施、CLI和生成骨架兼容，底层同样使用Spring事务与共享会话工厂，不再自行管理嵌套事务。同步run_token、任务抢占、文件ownership、权限与连接版本校验保留。旧Foundation ERP占位接口及只有NOT_CONFIGURED的解析/Adapter模型移除；本阶段仅装配ERP SQL连接，不实现ERP业务流程、API或同步执行器。

用户提供的SQL Server连接仅进入.local/erp.yml（0600），不进入本文件或通用配置。连接探针只尝试SELECT 1：TCP可达，JDBC因TLS协议协商失败，未执行SQL；未修改加密、证书或JVM协议策略。H2三库测试不等于该ERP库已联调通过。

### 外部多数据源接入模板（2026-10-06）

外部连接统一使用 `PersistenceOwner.Kind.ERP` 和一个 `erpSqlSessionFactory`。`mes.erp.datasources` 以 `erp-main`、`hr`、`wms` 等字符串标识登记连接，标识不是新的 PersistenceOwner 枚举，也不由 FactoryScope 推导。配置模板见 `config/erp-datasources.example.yml`，Adapter 和 Mapper 模板见 `docs/development/templates/erp/`；请求/结果类型与 SQL 必须来自后续真实外部契约，模板不创建外部业务表或同步流程。

接入时填写连接配置、给 Mapper 声明 ERP 归属，再在 Adapter 内用 `ErpTransactions.execute("erp-main", () -> mapper.query(query))`。Mapper 自动扫描，无需改中央清单。类型放在同包时使用简单类名，跨包时在顶部添加显式 import。连接标识由服务端配置/规则决定，不能把客户端任意标识直接作为连接授权。

`ErpTransactions` 仅在 Spring TransactionTemplate 获取连接前选择标识。事务开始后固定该连接；同标识嵌套按 Spring REQUIRED 处理，不同标识嵌套明确拒绝；事务结束或异常后恢复并清理线程上下文。缺少标识、未知连接和 ERP 未启用均不得回退默认库。切到另一外部库需结束前一事务，再分别调用入口；不能把多库操作当成原子事务。同步任务仍需独立遵守 run_token 与恢复约束。

Druid 监控 Controller 明确注入 `platformDataSource`，保持现有平台池监控契约；业务与 ERP 路由池不能通过单个 DruidDataSource 强制转换注入。三库场景无需设置全局 @Primary。

### 2026-10-06 多数据源监控与工作台范围扩展

上述平台池契约保留于旧 Druid 接口；新增 `/api/monitor/druid/sources`（分页目录）与 `/api/monitor/druid/snapshot`（按受控 sourceId 读取单池）经 DruidMonitorService 覆盖 platform、business:<id> 和 external:<配置标识>。外部 WMS/HR/ERP 等复用已配置 Druid 池，不增加 SessionFactory；RuntimeRegistry 只观察当前有效业务池，不为监控创建连接或加载业务池。未初始化、停用、未绑定明确返回状态而非假零。两接口均要求平台 monitor:druid:list，工厂同名权限不能越权。

快照为本节点 UUID/时间、连接池指标、对应方言的参数化 SQL 模板及 Wall 计数白名单；不暴露连接 URL/账号/密码、SQL 参数或原异常。统计开关缺失明确表达。此能力不等于数据库服务器全局监控或多节点聚合，Web/Spring 统计也未迁入。

SystemSessionController.info 增加 workspaces 有效角色范围摘要，不存入长期 Token；SystemPermissionService 经 SysRoleMapper 查询有效角色，再用 ManagedFactoryMapper.enabledIds 的参数化批量查询筛选启用工厂，最多两次查询。避免真实开发库 sys_role.factory_id 的 utf8mb4_unicode_ci 与 mf_factory.factory_id 的 utf8mb4_0900_ai_ci 直接 JOIN 产生 MySQL 1267；不修改 Schema。可选工厂不是角色授权证明。

2026-10-06 18:37 标准 Maven verify 加显式 WorkspaceAccessMySqlTest 开发配置通过；180 项中 177 通过、3 未 opt-in 跳过，打包 smoke 4 通过。当前浏览器可见中心、ERP 和未初始化业务三源；五池隔离由 H2 fixture 验证，真实 HR/WMS 和 SQLServer 业务连通未验证。实现、RCA、UI 三档证据与边界追加于 mes-base-ui/docs/acceptance/ui-industrial-upgrade/ROUND2.md。


### 固定包扫描与外部连接注解（2026-10-06，当前接入规范）

| SessionFactory | 自动扫描包及其子包 |
| --- | --- |
| platformSqlSessionFactory | dev.mesbase.system / dev.mesbase.quartz / dev.mesbase.platform |
| businessSqlSessionFactory | dev.mesbase.mes / dev.mesbase.modules |
| erpSqlSessionFactory | dev.mesbase.external |

Mapper 按 `*Mapper` 接口命名，放入对应包即可，平台/业务/外部 Mapper 均无需 PersistenceOwner 注解或新增注册。使用标准 MyBatis-Spring MapperScannerConfigurer，包根不得重叠，旧归属注解与包冲突会启动失败。PersistenceOwner 仅保留程序式旧 fixture 的兼容校验，不是生产扫描依据。旧 mes.persistence.mapper-packages 属性退出接入方式。

外部连接仍在 mes.erp.datasources 下配置。外部访问类使用 `@ErpSource("hr")`，类内方法直接调用 Mapper；需要不同标识的入口可在方法上覆盖为 `@ErpSource("wms")`。该注解组合标准具名 Spring @Transactional，连接选择切面先于事务拦截器；不需要再写 transactions.execute。旧 ErpTransactions 从生产代码移除，只存在于低层路由测试辅助代码。模板见 docs/development/templates/erp/。只有 ERP 会话工厂识别此连接选择，平台固定、业务仍按 Factory 路由。

使用可代理的非 final 类和 public 方法，通过 Spring 注入的代理调用；同类自调用不触发新的连接注解或事务。事务已开始后不允许变更连接标识，缺标识/未配置标识明确失败；不同外部库的操作不能作为一个本地原子事务。连接标识由服务端声明，不接收客户端任意数据库选择。

## 2026-10-09：中心库 + 多业务库（当前规范）

生产只创建 masterDataSource、businessDataSource 两个入口，分别绑定 masterSqlSessionFactory/businessSqlSessionFactory 与 masterTransactionManager/businessTransactionManager。mes.platform.* 配置键、平台权限和 PLATFORM 角色语义保留；master 表示同一个中心库，不增加别名工厂。

businessDataSource 为 FactoryBusinessDataSource，一个入口按 Factory 在 RuntimeRegistry 中租借对应版本的独立池。工厂数量可以为 1 或 N；不是把全部业务库压成一个物理库。缺范围拒绝，事务内切厂/跨中心业务拒绝；既有缓存前 guard、版本失效、租约排空、回滚和异常恢复继续有效。当前不支持共享业务库。

移除 mes.erp 配置、外部源/选源注解、路由器、第三工厂/TM、SQL Server 驱动和外部接入模板。旧 .local/erp.yml 不再导入；用户私有文件没有删除或采集。该文件若仍在磁盘，仅是用户保存的数据，不属于运行时配置入口。

Druid sources/snapshot 仅列中心库 platform 与 business:<id>；PLATFORM/BUSINESS 是监控 API 和权限的现有语义。监控不借业务连接、不创建业务池；未加载/停用继续明确返回状态。UI 同步移除 EXTERNAL 类型，权限、过期响应隔离和 SQL 脱敏保留。OpenAPI、Redis、私有存储与通用同步协调不属于被删除的外部数据库框架。

验证结果见 ADR-004 的 2026-10-09 修订及本轮验收收据；历史章节链接到已删除模板不再是接入入口。


### 2026-10-10 OpenAPI 仅基础设施可选模块

#### 2026-10-12 业务开发前能力核对

当前源码再次确认：mes-admin 仅在 Maven openapi profile 中依赖 mes-openapi，
公共配置 open-api.enabled=false。启用需同时有模块依赖和运行开关；启用后仅装配
实时接口目录及文档元数据，不恢复外部客户端、Secret/Token、授权或公开HTTP入口。
OpenApiEndpoint 是目录标记，不替代 EndpointAccess/认证。若界面仍有旧OpenAPI应用
菜单，应核查旧菜单数据与V16迁移，不靠打开开关恢复已移除页面/Controller。

自动编码是中心库编号工具，与mes-codegen生成Java/Vue代码无关。组成支持FIXCHAR、
INPUTCHAR、NOWDATE、SERIALNO，可配置顺序、长度、补位和流水号周期。
preview不登记、不预留；generate在masterTransactionManager事务中锁规则/计数并
写入sys_auto_code_result，当前周期按规则/cycle_key共享，不是默认按Factory独立计数。
当前HTTP预览/生成均为平台授权接口，没有工厂业务生成入口。
未来业务需要编号时按实际用例设计调用和权限；不能在已开启业务库事务中直接调用
中心写事务，若提前分配号应明确业务回滚可留空号，不承诺跨库原子提交或连续无断号。

上述两项不阻断普通CRUD与单业务库内编排开发；OpenAPI完整对外身份能力及工厂编号
接入只在实际业务需求出现时实施，不为基础收尾恢复旧框架。本节为源码核对，未启用
模块、修改业务实现、消耗真实编号、重启服务或重新运行测试。

本节取代此前 OpenAPI 客户端、Token、授权及数据范围接入说明；历史验收仅证明当时版本，不代表当前产品能力。

**SOURCE_PRODUCT_PORT 审查：** Source 的 `ktg-common/.../openapi/OpenApiEndpoint.java` 定义接口标识；`ktg-framework/.../openapi/OpenApiCatalogService.java` 只读取 MVC HandlerMapping；`ktg-admin/.../OpenApiDocumentationConfig.java` 混合了文档、旧令牌安全声明、错误协议及报表占位符处理。当前 Base 原迁移分散在 common/framework/system/admin，且客户端 Controller、授权 Mapper、专用安全链和 Foundation 外部身份分支彼此强耦合，不符合本次基础设施范围。

**保留：** 独立 `mes-openapi/pom.xml`，包 `dev.mesbase.openapi`，迁移方法/类元数据及目录模型、MVC 实时发现；目录按合法声明与 `/open-api/v1/**` 筛选；文档 OperationCustomizer 为已声明的方法补充元数据。动态引用指依赖与开关控制装配，目录每次读取当前已注册映射，不做任意反射调用、插件热加载或自动暴露内部 Controller。

**舍弃：** 旧消费者应用管理、Secret/Token、API grants、工艺路由授权、SysUser/角色/菜单关联、数据权限策略及绕过注解、旧 Filter/Interceptor/ResponseAdvice、诊断/verification HTTP 接口及客户端管理 UI。没有迁移 MES 业务 API，没有新增外部数据库或 SessionFactory。

**使用：** `mvn -B -ntp -Popenapi verify` 将可选模块放入 admin 依赖；宿主项目也可直接引入 `dev.mesbase:mes-openapi:${project.version}`。运行设置 `open-api.enabled=true` 后由 Boot AutoConfiguration.imports 加载，无需修改宿主扫描包。不引入依赖或关闭开关均不装配。模块无公开 Controller、GroupedOpenApi Bean、安全链、Mapper、Redis 或用户依赖。文档仅补充宿主已有受控 Swagger 入口元数据；Base 仍关闭 Springdoc 默认公共文档路由。

```java
import dev.mesbase.openapi.OpenApiEndpoint;

// 后续实际接口还必须按宿主规则声明 EndpointAccess，独立设计认证、授权及 Request/Response。
@OpenApiEndpoint(value = "example.read", description = "接口描述")
```

**数据库兼容：** V10 已应用的 checksum 不变；V16 删除旧 OpenAPI 菜单及 sys_role_menu 关联。历史 open_api_client/open_api_client_api 数据表保留为无运行时引用的遗留数据，未自动销毁客户端记录。旧客户端 Token 将不再由应用接受。未执行真实数据库迁移；若需要销毁遗留表另做数据审查。

**验证：** OpenApiInfrastructureTest 覆盖默认/显式关闭、独立于 system 启用、空目录、实时注册/撤销映射、内部路径/非法标识/漏标排除及文档元数据。InfrastructureContextTest 保留双 DataSource/SessionFactory/事务管理器、单内部安全链及 Quartz 关闭证明；RequestStateLifecycleTest 保留请求 ID/MDC、分页和 Factory 异常清理。测试及打包结果见本节后续回执；Fixture 不等于真实数据库或业务验收。

**2026-10-10 验证回执：**

- `mvn -B -ntp test`、`mvn -B -ntp clean verify`、`mvn -B -ntp -Popenapi verify` 均通过。最新 Surefire 共 187 项，183 通过、4 个真实 MySQL opt-in 用例跳过；其中动态装配/目录 2 项及 V16 H2 迁移 1 项通过。
- 默认包 `/usr/bin/python3 scripts/smoke-packaged.py` 10/10；启用包 `... --openapi` 11/11。真实本地 Java 进程证明默认不含模块、启用包包含模块并自动装配；旧 Token/诊断与公共分组文档均 404，health 200、受保护上下文 401。无业务 JDBC 或测试账号。
- 首次启用包因为 GroupedOpenApi Bean 注册未分类 openapiYaml 路由，被 EndpointCatalog 启动检查拒绝；已删除该 Bean，只保留受控宿主入口的 OperationCustomizer，重跑 verify 与打包 smoke 通过。未放宽端点启动检查。
- 独立模块打包包含 Source Product MIT 许可；补许可后 `mvn -B -ntp -Popenapi -DskipTests package` 通过（此前测试已通过）。
- IDEA 仅格式化本次 12 个 Java 文件，格式检查通过；Git hooks 已安装；后端与 UI `git diff --check` 通过。用户既有 codegen/CrudPage 工作未覆盖。
- UI `npm run test` 最新 84/84；构建曾被并行 CRUD 工作中的测试类型错误阻断，最终结果见下条回执。
- 未执行真实 MySQL V16 迁移、提交、推送或部署；历史客户端数据未销毁。回滚代码/模块配置需同时恢复旧 UI 和权限链，已执行数据库迁移的环境需另行审查，不能默认恢复旧授权。

- 最终 UI `npm run build` 通过（Vue/TypeScript 检查与 Vite 生产构建）；此前并行测试类型错误已由工作区其他改动解决，本任务未修改该 CRUD 测试。现有 bundle 体积提示仍保留，未顺手优化。

## 2026-10-10 工厂偏好迁移与默认选择

中心库新增 V17__factory_user_preference.sql，不带 seed 或菜单 SQL；应用前执行现有 Flyway 中心库迁移流程。system 启用自动装配持久偏好，表缺失明确失败，不退回内存掩盖迁移遗漏。GET /api/foundation/context 使用认证身份恢复偏好或默认厂；PUT /api/foundation/preference 只接收 factoryId，通过当前用户 ID 参数化 upsert。登录、刷新恢复服务端上下文，手工切换持久化后加载厂菜单/权限。

默认规则见 ADR-003 新修订：有效启用偏好优先，否则从启用厂且绑定启用业务库中选择工厂 ID 最小值（纯数字按数值），零候选为空。候选查询只读中心 metadata，不初始化业务池；业务请求继续校验路由版本/租约。本次未执行真实数据库迁移。

验收记录：本次后端迁移定向 4/4、前端 113 项和 build、打包 smoke 10/10；最新全量后端测试受其他任务 RoleScopePermissionTest fixture 错误阻塞，详见工厂迁移报告。真实数据库迁移及浏览器交互未验。部分源码进入同工作区其他任务提交，但 V17 尚未提交；部署必须包含 schema 与全套偏好链路。

### 2026-10-10 平台角色可持有业务权限

用户确认的新规则：PLATFORM 角色不绑定工厂，可持有平台和业务权限；业务操作必须具备有效工厂上下文，实际数据仍按该工厂独立库路由。FACTORY 角色只允许分配业务权限，只在所属工厂生效。当前工厂授权为平台角色业务权限与所属工厂角色业务权限的并集，平台操作仍只认可 PLATFORM 角色；不授予所有权限、不绕过 EndpointAccess、不复制权限到长寿命 Token。

SysMenuMapper 的 XML 查询同步合并 PLATFORM + 当前 FACTORY 角色；SystemPermissionService 排除平台菜单/权限，SysMenuService 的工厂导航排除平台菜单。MenuScopePolicy 以 system/tool/monitor 路径段、父节点及平台权限前缀判定平台菜单，按钮继承上层范围。角色菜单保存按同一规则拒绝 FACTORY 分配平台菜单，原有错误绑定在运行时不能获得平台权限。菜单分配树按角色类型返回：PLATFORM 全部可分配菜单，FACTORY 仅业务菜单。前端切换类型清空旧勾选并重新查询，拒绝迟到的其他类型响应。现有角色菜单绑定与菜单权限名称没有自动迁移或赋权。

修改入口：SysMenuMapper.java/XML、SystemPermissionService、MenuScopePolicy、SysRoleServiceImpl、SysMenuServiceImpl/ISysMenuService、SysMenuController；UI system/menu API 和 system/role 页面。AGENTS 同步角色规则。默认工作台选择仍沿用已有角色 workspace 契约，本轮不由可选工厂推断新授权。

验证：修改 Java 使用仓库 IDEA formatter；mvn -B -ntp verify BUILD SUCCESS，packaged smoke 10 项通过，最终 RoleScopePermissionTest 定向测试再次通过。隔离 H2 使用实际 MyBatis SQL 覆盖平台业务权限跨选定工厂、工厂角色越厂拒绝、平台权限错误绑定拒绝、停用角色、缺工厂上下文及菜单分配限制；不是对真实角色执行赋权。UI 22 文件/115 项测试、build、定向 ESLint 与两仓库 diff --check 通过。真实浏览器只验证平台角色规则提示并取消编辑，截图 UI docs/acceptance/ui-industrial-upgrade/role-platform-business-permissions.png；未保存角色、执行菜单 SQL、重启用户 8081 后端或宣称真实业务 CRUD 通过。

### 2026-10-10 首页权限回归修复

真实浏览器首页五个监控请求均展示“当前范围没有此操作权限”；前端已经按平台权限放行请求。根因是 SystemConfiguration.platformAuthorization 调用 granted(user, null, permission)，但上一轮将 null 工厂上下文直接拒绝，误伤所有平台检查（包含参数设置、附件等调用）。将含糊的 granted(user, factory, permission) 替换为 grantedPlatform 与 grantedFactory，并迁移全部调用入口（SystemConfiguration、SysConfigServiceImpl、SysAttachmentServiceImpl）：平台只查询有效 PLATFORM 角色的实际授权；业务缺少或空白工厂 ID 仍拒绝，业务入口仍由 FactoryScope 和 EndpointAccess 校验。新增平台允许、无授权/停用拒绝、工厂角色不能获取平台权限及越厂拒绝的回归断言。

同时 workspaces 增加 platformBusiness：按有效 PLATFORM 角色实际持有的业务权限计算，沿用 MenuScopePolicy 排除平台菜单及平台按钮权限。首页业务工作台选择和当前工厂提示据此接受平台业务权限；factoryIds 仍只描述有效 FACTORY 角色绑定，不将可选工厂当作权限。业务工作台继续为明确标记的示例数据，没有新增业务授权或真实指标查询。平台监控仍按独立平台权限读取。

验证结果与运行边界见本轮回执；用户持有的 8081 服务未由 agent 重启，服务端新契约与权限修复须重启后生效。

### 2026-10-10 URL 范围统一与共享入口迁移

HTTP 范围改由 EndpointScopePolicy 按 /system、/tool、/monitor 完整路径段判断；EndpointAccess 仍声明访问级别与操作权限，并在启动时与每个映射 URL 校验一致。平台管理要求有效 PLATFORM 角色；AUTHORIZED 另查实际授权。前端所有请求（包括 SSE）在有选厂时携带 X-Factory-Id；平台/个人/公共读取入口清除业务 FactoryContext，不解析该请求头为数据范围。业务接口才解析有效选厂并按平台业务权限与当前厂角色权限并集校验。菜单按完整父子路径解析范围，移除权限名称前缀推断；按钮继承路径，绝对路径从自身根重新判定。

迁移：/api/platform/session → /api/session；/api/system/info → /api/session/info；/api/system/getRouters → /api/session/menus；/api/system/factory-navigation → /api/factory/navigation；/api/system/user/profile → /api/personal/profile；消息入口 → /api/personal/inbox；字典类型数据与客户端配置读取 → /api/common/dict/{type}、/api/common/config/{key}。旧映射删除，调用方、协议测试、SSE 注册路径与过滤配置同步迁移。共享配置仍由 Service 限制允许键或检查实际权限。私有文件 /api/platform/files 是已有个人资源契约，地址保持稳定以保护数据库/富文本中的引用，owner/resource 校验保留且不绑定业务工厂。

生成器本轮没有改动：用户明确其只负责纯代码生成，不承担整个 MES 的业务控制；撤回拟添加的模块名限制与路由改写。生成结果进入 MES 时仍须满足 MES 的运行规则。AGENTS 同步此边界。

验证：启动/运行路径一致性、三前缀与路径边界、平台有无工厂请求头均不绑定业务上下文、平台角色与具体权限分别校验、非平台角色管理拒绝、个人入口免平台角色、业务缺范围拒绝和真实 MyBatis 合并权限测试。测试与构建结果见本轮回执；未重启用户 8081 服务，未保存角色、执行 SQL 或改变实际授权。前后端接口契约需同步生效。

本轮最终验证（2026-10-10）：受影响 Java 使用 scripts/format-java 的 IDEA 原生格式化；mvn -B -ntp verify 于 16:06 BUILD SUCCESS；scripts/smoke-packaged.py 10 passed / 0 failed。UI npm test 22 文件 / 117 项通过，npm run build 通过，受影响请求/API/Auth 文件定向 ESLint 与两仓库 git diff --check 通过。路由迁移初次验证发现旧消息路径 fixture 导致 404，已迁移到实际个人消息入口后完整 verify 通过。上述 HTTP/SQL 使用隔离 fixture，不代表用户 8081 运行中的服务已经升级。


### 2026-10-11：改造前登录 Token 链路的历史核对

本轮仅静态读取当前 Base 与 `/Users/user/work/heri/ktg-mes` 的源码，未修改认证代码、配置、服务或 Redis 状态，未执行登录/注销采样。

Base 实际链路为 POST /api/session → SystemSessionController → SysLoginService（验证码、Redis 重试锁、当前账号/密码/状态检查、登录日志）→ TokenService.createToken。它用 SecureRandom 生成32字节，再编码为43字符的无 padding Base64URL Token；Redis 的 mes:system:session:<SHA-256(Token)>（经现有数据库命名空间封装）只保存 Identity 和 TTL，原始 Token 返回客户端，不是 JWT。响应 accessToken/expiresAt；UI 保存 Token/有效期/选厂偏好，后续带 Authorization: Bearer。

每次请求 SystemTokenFilter → TokenService.resolve：格式校验、哈希、Redis 查身份，再通过用户 Service/Mapper 读取当前账号，校验启用/未删除、userId、authRevision；重新构造 LoginUser 放到 Spring SecurityContext。FoundationAccessInterceptor 另做 URL 范围、当前厂及真实权限检查；Token 不包含权限。DELETE /api/session 删除当前摘要会话。当前默认 TTL60分钟（可配置），没有 ktg-mes 的 verifyToken/refreshToken 滑动续期，setLoginUser 保留为空实现。

### 2026-10-11：Redis 会话快照历史评估（已由实现取代）

> 当前已实现 v2 白名单身份、一次精简账号/角色 fence 和可恢复用户索引撤销；下文保留改造前证据，不是待执行规则。

用户提出入口直接从 Redis 获取用户作为本次请求身份快照。方向合理：登录仍校验数据库账号和密码，普通请求从 Redis 恢复经过裁剪的会话身份，不必每次关联查询完整 SysUser。会话不缓存长期角色/权限，不携带固定工厂或数据库绑定；接口授权与业务路由仍按各自生命周期处理。

前提是补齐账号变更到会话失效的链路。当前 auth_revision 在改密、状态变更时更新；TokenService.resolve 靠每请求查询数据库发现变化。若去掉该查询而只读取旧会话，数据库中的 revision 不会自动让 Redis 会话失效。现有 revokeUser 遍历全部会话，不能直接作为规模化失效方案；应有用户会话索引或明确的账号版本校验机制，并处理登录与撤销并发。

注销删除当前会话；改密、禁用、删除等账号变更须使后续请求拒绝旧会话。数据库提交与 Redis 失效并非原子事务，提交后失效失败需要明确报错、可靠补偿和可接受的生效窗口，不能声称天然即时撤销。Redis 不可用或会话缺失时拒绝认证，不静默改走另一套身份路径。

缓存模型应排除 password、联系方式和持久化角色对象等不必要字段；如确有部门字段参与数据范围，需明确部门变更的刷新策略。请求身份快照不等于长期权限快照，也不等于数据源 metadata 缓存。保持现有 Bearer 格式即可采用此方案，JWT 不是必要条件。

本节记录建议及源码核对，未修改认证实现、规则或数据库；未验证改造后的撤销、并发或性能行为。

### 2026-10-11：长期授权快照历史提案（未实施，已撤销）

> 用户最终决定每请求查询当前角色/权限，仅请求内复用；明确不实施 Redis 长期权限缓存或全局授权 generation。下方提案不再作为实施要求。

用户进一步要求 Token 有效期间正常请求从 Redis 取得身份与权限，新增菜单、调整授权时明确刷新机制，并允许已完成入口校验的请求执行完毕。现行“不缓存权限、每请求读取数据库”规则应在实施时同步修改；本节为方案评估，不表示已实施。

源码事实：Base SystemPermissionService 每次读取角色/权限，业务授权另读取全菜单作 URL 分类；未发现角色、菜单、用户角色变更与授权 Redis 缓存失效的完整链路。ktg-mes TokenService.refreshToken 清空缓存角色/权限，FactoryAuthorizationService.authorize 每请求读取当前工厂角色、菜单权限和部门范围，且明确不回写共享 Redis 登录权限。源项目角色/菜单更新主要写数据库，因此其当前业务授权新鲜度来自重新查询，不能复制后宣称已有 Redis 授权更新机制。

建议首版采用全局授权 generation：Redis 会话保存安全身份；授权快照独立按 userId + PLATFORM 或 FACTORY/factoryId 缓存，并带 generation。平台授权快照保留有效平台角色标志；工厂快照按既有规则合并平台业务权限和当前工厂角色业务权限。不得把单个 Token 的权限集合用于所有工厂。

入口只读取 Redis 会话、当前 generation 与对应快照；命中同版本后，本次请求复用不可变授权结果。缓存缺失或版本变化才查询数据库重建。重建前后必须检查 generation，发布以版本比较保护，版本已变则丢弃重试；权限集合、角色标志与可见菜单需保持同一版本。使用纯 Redis 获取不能证明快照绝对新鲜：数据库提交到 generation 更新之间仍有窗口，必须单独定义失效生效边界。

失效入口覆盖：用户角色分配/撤销、角色菜单分配、角色启停/删除/范围变更、菜单权限或路径/父级/状态/可见性变更，以及实际参与数据权限的部门关系变化。数据库成功提交后推进全局 generation；回滚不推进。全局版本避免遍历用户和在线 Token，代价是一次管理变更后所有用户的旧授权快照陆续重建；如实测规模不适合，再细化用户/工厂版本，不预建复杂依赖索引。

新增菜单本身不授予用户权限；给角色绑定且用户持有该有效角色后，下一次成功校验新版本的请求重建并生效。授权变更不要求重新登录。账号禁用、删除、改密仍通过独立会话撤销机制处理，不仅靠授权缓存过期。

数据库与 Redis 双写失败需有可恢复的持久失效记录与重试，并明确失效未完成的管理操作状态；不能仅 afterCommit 执行 INCR、失败后等待 TTL 而宣称变更已全面生效。直接 SQL 修改不经过这些入口，必须执行明确的刷新流程。Redis 丢失 generation 时不能恢复成可能匹配旧快照的默认版本。

前端也需识别服务端授权版本变化并刷新身份/菜单/按钮权限，清理撤权页面及旧请求结果；仅改变后端缓存不会自动刷新已打开的页面。无需以 WebSocket 推送作为首版前提。本轮只核对源码并追加方案，未运行改造后的缓存/并发/性能验证。

当前 ktg-mes 是 UUID 会话标识＋HS512 签名 JWT；JWT 中引用 UUID，Redis 按 UUID 保存会话；JwtAuthenticationTokenFilter 在 getLoginUser 后 verifyToken，临近到期刷新 Redis TTL。当前源代码 refreshToken 已清空 cached.user.roles/roleIds 和 cached.permissions，因此不能以“源项目必须缓存长期权限”证明更换 JWT 的必要性。

结论：Base 复用了 Redis 会话职责和若干 Source 类名，但 Token 格式、存储模型、请求校验及续期语义均已改造，属于认证 REDESIGN，而非纯 COPY/PORT。ADR-006 有另选 opaque Bearer 的历史记录；其中“数据库 SHA-256 hash”的早期描述不能作为当前 Redis 实现的事实。TokenService 的 PORT 注释和空 setLoginUser 兼容方法容易掩盖上述差异。是否恢复源方案或保留当前方案需按用户确定的范围单独处理，本轮没有实施变更。


### 2026-10-11：改造前 Security 配置差异

> 当前已改为显式 PUBLIC 方法放行、其余 authenticated；下文 anyRequest().permitAll() 是改造前事实，不能作为现行接入说明。

针对用户提供的 ktg-mes SecurityConfig，重新核对 Base 的 FoundationConfiguration.security：当前入口为 @Bean SecurityFilterChain，禁用 formLogin/CSRF/requestCache，STATELESS；mes.system.enabled=true 时禁用 Basic。SystemTokenFilter 放在 BasicAuthenticationFilter 前恢复身份。

最重要的差异不是配置语法，而是 Base 使用 anyRequest().permitAll()，Spring Security 的请求授权阶段不做 ktg-mes 的默认 authenticated 限制；PUBLIC/AUTHENTICATED/AUTHORIZED 移到 EndpointAccess + MVC FoundationAccessInterceptor，EndpointCatalog 在启动期要求 MVC 方法显式声明。已有 MVC 方法仍做认证/权限校验，不能把 permitAll 等同于所有 API 都能匿名访问；但它不是源项目 filter 层默认需登录的等价迁移，也不能单靠 MVC 检查证明其他 Servlet/filter 入口覆盖。

注销改为 DELETE /api/session 调用 TokenService 删除 Redis 会话，不通过 Spring LogoutFilter；JWT 过滤器已被随机凭证 SystemTokenFilter 替换。此次所搜生产目录未发现源项目 CorsFilter 注册或 EnableMethodSecurity/@PreAuthorize 方法授权装配；源项目公开业务/静态资源名单未原样照搬。旧 Controller 中 PreAuthorize import 不证明原授权机制仍在运行。

源码判断：Base 确实改变了安全职责划分，不仅升级配置写法。若回归来源结构，可由 Spring Security 负责明确公开入口及其余请求 authenticated，MVC/方法授权负责平台角色、当前厂和具体权限；认证协议是否回归 JWT 是独立决定。本轮没有修改实现，也未进行运行验证。

## 2026-10-12：数据源密码格式、加载流程与解密排查

本节基于当前工作区源码、两个本地 profile 的脱敏核对及 Java 21 隔离验证。保留用户在 `DatasourcePasswordCipher` 中已有的未提交调试代码，不记录密码、密钥或实际密文。已检索同主题 `docs/` 内容；`doc/`、`knowledge/` 不存在，因此在本文追加操作说明。

### 配置与持久化格式

| 位置 | 当前格式与消费者 |
| --- | --- |
| `mes.platform.password` | 数据库明文密码；`PersistenceConfiguration.masterDataSource` 原样交给 `DruidPools.create`，再调用 `setPassword`。 |
| `mes.codegen.profiles.<profile>.password` | 数据库明文密码；`CodegenCli` 绑定 profile 后原样调用 `DriverManagerDataSource.setPassword`。 |
| `spring.data.redis.password` | Redis 原始口令配置，不由 `DatasourcePasswordCipher` 处理；当前 dev 为空。 |
| `mes.runtime.datasource-key` | AES 密钥的标准 Base64 表示，不是数据库密码，也不是加密后的密码；解码后必须为 32 字节。 |
| 中心库 `mf_datasource.password_cipher` | 托管业务库密码的 AES-256-GCM 密文；正式保存入口使用 `encrypt` 产生，建池时使用 `decrypt` 还原。此次未读取真实表内记录。 |

配置中的密码不会自动经过此类解密。`DruidPools` 只允许 `stat`、`wall` 过滤器；本次生产源码及 POM 搜索未发现 Jasypt、`ENC(...)` 或自定义环境解密入口。不能把该类生成的密文直接替换到现有 YAML 的 `password`，否则当前路径会把密文本身当作数据库密码。

### 实际加载顺序

1. `application.yml` 默认 profile 为 dev；dev/prod 参数位于同目录对应资源文件。`scripts/run-local-backend` 启动已打包 JAR 并显式指定 dev；不能把修改源码资源文件等同于运行中服务或既有 JAR 已重新加载。
2. `PersistenceConfiguration.masterDataSource` 使用 `mes.platform.url/username/password` 创建中心库连接池，提供 `masterSqlSessionFactory`。
3. `FactoryRuntimeConfiguration.managedDatasources` 在 Runtime 启用时读取 `mes.runtime.datasource-key`，构造 `DatasourcePasswordCipher`；此时校验密钥格式，不代表所有业务库密码已经验证。
4. 当前厂路由通过 `FactoryMetadataCache` 读取定义；Redis 未命中时由 `RoutingMetadataMapper` 查询中心库 `mf_factory` 与 `mf_datasource`。定义保留 `password_cipher`，缓存读取本身不解密。
5. `ManagedDatasourceService.acquire` 在需要建立相应版本的业务池时，对 `source.passwordCipher()` 解密，再调用 `DruidPools.create`；已有池复用时不重复解密。DDL 导出创建临时池时也调用解密。业务入口仍为一个 `businessSqlSessionFactory` 和按 Factory 租借的物理池。

### 当前 main 解密失败的直接原因

脱敏比较确认：当前 `DatasourcePasswordCipher.main` 的构造参数与 dev 的 `mes.runtime.datasource-key` 相同，标准 Base64 解码后为 32 字节；`decrypt` 参数与 dev 的中心库及代码生成 profile 明文密码相同，它不是该类生成的密文。

使用当前源码在临时目录编译并由独立验证入口调用，结果为：构造成功；解密抛出 `IllegalStateException`，直接原因是 `IllegalArgumentException`，失败位置在 `java.util.Base64$Decoder`。因此本次在 Base64 解析阶段已经失败，尚未进入 AES/GCM 校验，不能归因为密钥不匹配。

`decrypt` 将 Base64/长度等输入错误与加密算法异常统一包装为“数据源密码无法解密，请检查运行环境密钥”，原始 cause 保留，但提示不能区分输入格式错误与密钥错误。当前调试 main 还硬编码了真实凭据材料并准备输出解密结果；这些内容不应进入提交或日志。本轮未改动该 main。

### 正确生成和使用另一个密文

优先使用平台的数据源管理保存入口 `POST/PUT /api/system/datasource`：传入数据库明文密码，`ManagedDatasourceService.save` 自动加密后保存到 `password_cipher`；不要先自行加密再提交，否则会二次加密。已有数据源的编辑受禁用、未绑定工厂和 revision 校验约束，不能绕过这些条件直接修改。

需要离线生成时，复用同一运行环境的 `mes.runtime.datasource-key`，调用现有 `encrypt`。下面变量由受控配置/输入提供，不把真实值写入源码：

```java
DatasourcePasswordCipher cipher = new DatasourcePasswordCipher(encodedKey);
String encryptedPassword = cipher.encrypt(plainPassword);
String restoredPassword = cipher.decrypt(encryptedPassword);
```

格式为标准 `Base64(12 字节随机 IV + 密文 + 16 字节认证标签)`，不带 `ENC(...)` 包装；每次随机 IV 使同一密码产生不同密文。只做 Base64 编码不能生成这种密文。dev/prod 当前密钥不同，应使用保存目标对应的密钥；不能只替换主密钥而保留旧密文。

### 验证与边界

- 使用用户 Java 21 的 `javac` / `java`，在临时目录编译当前 Cipher 和无真实凭据字面量的诊断入口；没有运行会打印明文的原 main。编译与执行均 exit 0。
- 当前 main 输入的失败原因复现；合成密码加密/解密往返通过；同一密码两次密文不同；错误密钥及篡改密文均以 `AEADBadTagException` 拒绝。
- 本次只修改本操作文档，未修改业务代码、配置、数据库或运行服务；未执行 Maven 全量测试，也未验证真实数据库密文及服务进程的最终生效配置。
- 源码入口：`DatasourcePasswordCipher`、`FactoryRuntimeConfiguration`、`PersistenceConfiguration`、`DruidPools`、`FactoryMetadataCache`、`ManagedDatasourceService`、`ManagedDatasourceMapper`、`RoutingMetadataMapper`、`CodegenCli`。

### 同日补充：实际数据库密码不同，追查连接目标与配置快照

用户补充：数据库客户端使用同一主机、端口、用户名，但实际密码不同，并表示 IDEA 启动后端可以查询。为区分“代码把配置当作明文”和“目标数据库实际接受该密码”，继续做脱敏配置比较及只读连接验证。

配置证据：

- 当前 `application-dev.yml` 与 `target/mes-admin/classes/application-dev.yml` 的中心库 URL、用户名、密码及数据源主密钥一致；IDEA 保存的 `MesBaseApplication` 运行配置没有发现 profile、密码或连接参数覆盖项。
- `target/mes-base-0.1.0-SNAPSHOT.jar` 的修改时间为 2026-10-11 21:18；当前源码 dev 配置的修改时间为 2026-10-12 08:54。时间只表示本地文件快照，不证明修改者或运行时加载时间。
- 当前 dev 配置与该 JAR 内 dev 配置相比，中心库和 codegen URL 的主机不同；端口、库名、URL 参数、用户名、密码保持一致。因此不是这两份快照之间密码被加密转换，而是连接目标变化、凭据沿用。

本次真实连接结果：

| 检查 | 结果 |
| --- | --- |
| MySQL 客户端使用当前 dev 中心库 URL 对应地址及配置凭据 | 返回 1045 / SQLSTATE 28000，身份验证被拒绝。 |
| 从现有 JAR 提取同版本 MySQL JDBC 驱动，原样使用当前 dev URL、用户名、密码 | 同样返回 1045 / 28000；未进入查询。 |
| 使用同一 JDBC 驱动及 JAR 内旧 dev URL、用户名、密码 | 连接成功；只执行 `SELECT CURRENT_USER(), VERSION()`，认证用户名与配置一致，匹配 `%` 主机规则，服务端版本 8.0.40。 |

结论：配置中的值是旧连接目标仍接受的明文凭据；当前连接目标拒绝这组凭据。现有证据支持“切换连接目标后沿用旧环境凭据”的配置不匹配问题，不支持“该值是实际密码的密文”。1045 本身不能单独排除账号主机授权等其他认证条件，但两个目标的接受结果已直接验证。

本次未取得用户数据库客户端的实际密码，也未取得所述 IDEA 成功查询时的进程配置，不能确认那次查询使用的地址、启动时间或连接池状态。若预期连接当前 YAML 的目标，应使用该目标中对应账号的有效明文密码；如果预期连接旧目标，则应校对 URL。不能用 `encrypt` 或 `decrypt` 修正这种目标/凭据不匹配。

验证未输出密码、密钥、完整 JDBC URL 或账号名。只执行身份只读查询，未修改数据库、配置、代码或启停服务；只追加本节，未运行 Maven 全量测试。

## 2026-10-12 OpenAPI 客户端能力恢复（当前有效）

用户明确要求回滚移除部分，本节取代 2026-10-10 的仅元数据模块结论及 2026-10-12 早期“完整能力后置”的范围说明；保留原文作为历史。
恢复来源是后端 `1c2ada6^` 与前端 `cef3072^` 的已迁入版本。客户端管理、一次性 Secret、Redis Token、接口授权、独立外部认证链、管理页面与平台诊断均恢复。

实现归属恢复为 common/openapi、system/openapi（中心 Mapper/XML/Service）、framework/openapi（外部认证/协议）与 admin 平台 Controller。
删除被取代的仅文档 mes-openapi 模块、reactor 项和 -Popenapi profile，标准构建即可包含实现，不保留两份同名 Catalog Bean。
按当前单表 Extra 规则，将旧 OpenApiClientExtraMapper 的查凭证、锁行及版本更新方法并入 OpenApiClientMapper/XML，不恢复单表 Extra；行为仍由业务 Service 和具名 Spring 事务承载。
启用条件是 `mes.system.enabled=true` 与 `open-api.enabled=true`；现有应用开关取值保留，补齐原 namespace/TTL/限流/data-scope 配置。
启用后 `/api/system/open-api/clients` 仍要求平台角色及相应 system:openApiClient 权限；外部 `/open-api/**` 只使用独立 OpenApiPrincipal/API grants，不作为 SysUser、不解析用户偏好或 X-Factory-Id。

新建应用默认禁用，Secret 仅创建/重置时返回并设置 no-store；列表/详情不返回 secretHash。启停/重置递增凭证版本，后续外部请求校验当前客户端状态/版本；API 授权逐请求读取，撤销后不再放行。
`POST /open-api/token` 用 appKey/appSecret 发行随机 oa_ Token；有效期内重复申请复用并刷新申请有效期，普通接口不续期；`POST /open-api/token/revoke` 撤销当前 Token。这套协议与内部随机 Bearer 不互通。
数据范围开关仅影响业务扩展校验，不关闭凭证/API 授权/限流。打开 data-scope 时仍要求唯一 OpenApiDataScopeValidator；没有具体业务范围时保持关闭，不迁入客户工艺实体或默认业务库兜底。

V10/V16 历史迁移保持原样；新增 V22 恢复系统目录下 OpenAPI 页面及五个按钮权限，保留客户端/授权数据，不猜测 V16 已删除角色关联。
部署后需管理员重新分配菜单权限；V22 不自动赋予任何角色或外部客户端权限。本轮仅写迁移文件，未执行真实迁移。
验证与未验证边界见请求准入实施报告的 2026-10-12 OpenAPI 恢复记录。

### 2026-10-12 补齐开发者文档与管理页异常修复

恢复 Source Product `ktg-admin/src/main/resources/static/openapi-docs/{index.html,app.js,style.css}` 的只读开发者文档。
入口 `/openapi-docs/#start`，启动说明、认证、错误码、接口搜索及契约详情均保留；公司名称替换为 MES Base，原公司 logo 不复制，使用本项目 SVG。
文档唯一网络请求为匿名 GET `/v3/api-docs/open-api`，不申请 Token、不保存凭证、不提供在线执行。

独立文档安全链仅允许六个确定 GET 资源（页面、JS/CSS/SVG、外部 JSON），其他方法和文档目录下其他路径拒绝。
外部契约以 Springdoc SDK delegate 生成，只收录 Token Controller 或明确 OpenApiEndpoint；不注册额外 GroupedOpenApi 默认路由，不开放内部 Swagger/后台接口。
HTTP 方法显式 PUBLIC；只读文档可见不表示应用获得接口调用权。SDK 契约、错误码和例子取当前实现，不静态硬编码业务 API。
Vite 与部署 Nginx 模板补入文档/精确 schema 代理；应用管理工具栏新增“API 接入文档”链接。

管理页接口授权多行 Checkbox 改为自动高度，保持弹窗 body 滚动及固定底部；列表时间列适当加宽。
这是原恢复版本未完整适配当前 Element Plus 默认高度导致的显示异常，不修改权限保存与凭证业务。
实际浏览器验证与测试记录见实施报告；后端新增 Controller 必须重新编译加载后才能访问文档入口。

### 2026-10-13：删除菜单的关联清理与启动审查容错

用户在清理菜单后遇到 MenuScopePolicy.require / Menu does not exist 导致启动失败。
根因是 SystemPermissionService.installEndpointScopes 的启动审查读取 sys_role_menu 中工厂角色
的菜单 ID，但已删除菜单的历史关联仍被当作有效菜单强制解析。require 也用于新授权写入，
不能通过全局关闭它的存在性校验来解决。

本轮新增 MenuScopePolicy.find，启动/管理审查跳过不存在菜单的历史关联并记录数量警告；
权限读取原有 SQL 内连接 sys_menu，失效关联不会授予权限。仍存在的工厂角色平台菜单授权、
跨平台/业务重复权限码、层级循环继续拒绝；新建/修改角色明确提交不存在菜单时仍由 require
拒绝。没有通过猜测把缺失菜单归为业务菜单，也没有全局放宽授权。

正常菜单删除现在允许菜单已分配给角色：Controller 移除“已分配不能删除”拦截，Service 使用
原 masterTransactionManager 事务删除对应 sys_role_menu 关联及菜单，并继续审查真实授权。
不删除角色、用户或其他菜单关联。有子菜单仍拒绝，Service 也保留该保护；审查或数据库失败时
菜单与关联一起回滚。删除已废弃的 checkMenuExistRole 接口/实现/Mapper 查询，避免留下失效分支。
直接 SQL 删除历史菜单的孤儿关联不会在启动时自动清理数据库，只忽略其无效授权。

验证：全 reactor 生产源码 `mvn -B -ntp -DskipTests compile` 通过。`mvn -B -ntp test`
在旧 mes-business 测试编译阶段失败，测试仍引用已移除的 dev.mesbase.modules.dv/material/md/pro/qc
类，保留用户现有业务与生成器改动，未以删除这些测试来伪造整仓通过。

隔离编译执行 MenuDeletionTest、RoleScopePermissionTest、MenuScopePolicyTest 共 12 项全部通过；
加载当前 reactor classes、实际中心 Mapper/XML、H2 与 Spring 事务代理，验证包含孤儿关联的
Spring 上下文启动、权限不扩大、真实 Controller → Service 删除、角色保留、其他关联保留、
事务回滚、子菜单保护及显式不存在菜单授权拒绝。新增测试在 src/test，临时执行器仅在
 target/menu-deletion-check，原始日志 /private/tmp/menu-delete-focused-test.log。
Java IDEA 格式化与 diff 检查通过。未执行真实数据库清理、重启服务或部署；不声明实际应用
已启动或完整 Maven 测试已通过，用户需加载新编译产物后重启验证原故障。

### 2026-10-20：首页工作台真实接口接入可行性审查

本轮为当前工作区源码审查，不修改业务代码、配置或数据库，不启动/停止/重启服务。
未执行 HTTP、浏览器或真实数据库验收；下列“已有接口”表示源码中存在正式调用链，
不表示当前运行进程已加载、账号已授权或目标库已有可展示数据。
本节补充 2026-10-10 首页记录：业务模块已增加正式能力，但首页仍使用原示例组件。

#### 当前入口与数据来源

- `mes-base-ui/src/views/Home.vue` 根据服务端 workspaceAccess 决定工作台；
  业务模式和平台显式预览都挂载 `BusinessWorkspace.vue`。
- `BusinessWorkspace.vue` 只导入 `src/lib/businessWorkspaceSample.ts`，
  生产概览、近期报工、质量检验、待办及四项 KPI 全部为静态示例；factoryName 只影响展示。
  页面持续标注“示例数据”，没有将示例伪装为真实结果，但已经不能用其判断业务是否接入。
- `PlatformWorkspace.vue` 经 `useWorkspaceStatus.ts` 使用五个真实只读接口：
  `/api/monitor/online/list`、`/api/monitor/job/list`、`/api/monitor/server`、
  `/api/monitor/cache`、`/api/monitor/operlog/list`。分别检查平台权限、展示失败/未授权、
  按 revision 和卸载丢弃旧结果。在线数是会话数，任务数是配置数，不代表设备在线或任务执行成功。

#### 接入对应关系与缺口

| 首页区域 | 当前正式能力 | 结论 |
| --- | --- | --- |
| 工单列表 | GET `/api/production/workorder` → WorkOrderService → WorkorderMapper，分页并返回产品、计划量、来源、状态与分配摘要 | 可直接复用现有 API 适配层；工序、产线、完成量不在此列表中，不能沿用示例字段硬映射 |
| 执行与数量 | `/api/production/mes-production-execution`；GET `/api/production/report-quantities/executions/{id}`、`/workorders/{id}`；GET `/api/production/executions/{id}/closure` | 存在真实执行、工序净报工、有效产出、放行/交接/接收口径；首页批量汇总需专用查询，避免逐工单逐执行请求 |
| 近期报工 | GET `/api/production/reports` → ProductionReportService.page → MesProductionReportMapper | executionId、routingNodeId 必填，目前只支持指定执行/工序台账；要显示全厂近期记录需补分页查询，复用原事实表和业务规则 |
| 质量区域 | GET `/api/quality/defects`、`/api/quality/defects/statistics`；单声明处置与单执行放行查询 | 可展示不良记录、工废/料废及净损失等真实统计；不能解释为首检、巡检、抽样任务或“已合格” |
| 待办 | 已有执行、路径、报工候选和质量处置/放行规则，但所审查入口中没有首页统一待办查询 | 需定义任务来源、可操作状态、当前人/全厂范围及权限；不得仅凭存在执行或不良记录就生成“我的待办” |
| 班次、工时、合格率 | 当前报工 Ledger 包含数量增量、操作者、时间等，不含示例的 hours、qualified、待确认状态 | 当前接口不足以一一替换；首版移除这些列/指标或明确未接入，不能根据时间或无不良记录猜测 |

#### 实施建议与必须保留的边界

1. 保留平台工作台真实监控；将正式业务工作台接入真实工单、执行、不良记录及质量统计，
   详情跳转现有业务页。显式示例预览可继续独立存在，但禁止作为真实接口失败兜底。
2. 首页概览和全厂近期报工缺少直接适配的接口，应补有界、只读的查询用例。
   Controller → Service → 注入 Mapper，使用业务事务和既有分页机制，复用业务事实与权威规则；
   不新增生产事实体系，不为首页调用真实 ERP，不用前端遍历全部分页汇总。
3. 完成量必须先明确是末工序净报工、扣除净不良后的有效产出、已放行量、已交接量还是仓库接收量。
   `ProductionQuantityMapper.workorderQuantities` 的 lastNodeReportedQty 为末节点数量增量之和，
   不是自动扣净不良后的合格量；应结合 ProductionQuantityService 和 closure 语义。
   报工须考虑调整/冲销，质量须考虑版本、重叠、不确定数量及返工回收；不能直接加原始记录。
4. KPI 要明确时间范围、时区、状态与来源范围；没有班次契约时取消“本班次”承诺。
   不同产品/单位不能未经换算统一累加为“件”；比率分母为零或证据不足时展示不可计算。
   单页数量不能冒充全厂总计，不同分组的 quality statistics.total 不能当不良单数。
5. 有效选厂与逐模块业务权限共同决定读取。workspaceAccess 仅决定工作台入口，不能代替数据授权。
   未选厂时不请求业务 API；无权限、真实空数据、失败分别展示。切厂/撤权/卸载清理列表、指标、
   弹窗并拒绝迟到结果。历史 LOCAL/EXTERNAL 可查询不等于允许跨来源新操作。
6. 替换时同步调整 `tests/homeWorkspaces.test.ts` 中的示例断言，保留平台预览不改范围、
   撤权清理等有效行为测试，新增真实读取的权限与异步边界验证。
   最终使用核实绑定后的 SIMPLE_B 配置验收库与现有服务进行 API/UI 回读，
   识别已有合成测试标签，不把测试业务数据宣称为现场生产数据。

结论：可以接入真实接口和真实业务库数据，首版具备基础；完整保留示例全部内容则存在语义和查询缺口。
本轮未实施接入，未运行测试/构建；生产数量相关文件有用户未提交修改，审查未覆盖或改写这些工作。
