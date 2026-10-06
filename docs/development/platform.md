# Source 系统与 Factory Foundation 操作边界

2026-10-06，Phase1 MIGRATING。本文按当前 Source Product 链路更新；此前简化 UUID Platform CRUD/数据库会话/全局配置缓存说明已被迁移替代。实际命令、版本、失败和未验证项见 [报告](MES_BASE_REALIGNMENT_REPORT.md) 与 [验收 JSON](../reviews/2026-10-06-phase1-verification.json)。

## YAML 与装配

通用配置在mes-admin/src/main/resources/application.yml；真实dev/prod参数直接写在同目录的application-dev.yml / application-prod.yml，两个真实文件留在本机、0600、Git已取消跟踪并忽略。由Spring Boot标准profile自动选择，无spring.config.import转接、无连接环境占位符。默认dev；prod用--spring.profiles.active=prod。首次在新机器使用时，从config里的两个无密码example复制到资源目录、填连接并构建；本地JAR会包含本机profile配置。

Spring Boot 4.1.1有效错误属性为spring.web.error.include-message/include-stacktrace/include-binding-errors，原server.error.*自4.0被标记level=error，已修正。server.address/port仍然有效，直接放在实际profile文件中。

| 配置 | 实际消费者 |
| --- | --- |
| mes.system.enabled、mes.platform.* | SystemConfiguration；DruidPools、PlatformPersistence、Flyway |
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
