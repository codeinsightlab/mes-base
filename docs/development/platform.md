# Source 系统与 Factory Foundation 操作边界

2026-10-06，Phase1 MIGRATING。本文按当前 Source Product 链路更新；此前简化 UUID Platform CRUD/数据库会话/全局配置缓存说明已被迁移替代。实际命令、版本、失败和未验证项见 [报告](MES_BASE_REALIGNMENT_REPORT.md) 与 [验收 JSON](../reviews/2026-10-06-phase1-verification.json)。

## YAML 与装配

通用配置 `mes-admin/src/main/resources/application.yml`，环境配置 application-dev.yml / application-prod.yml，受控外部文件通过 `--spring.config.additional-location=file:/absolute/config.yml` 明确加载。Java Configuration 负责对象装配/校验/关闭，不储存环境连接值。本轮运行须显式 dev + target/phase1/application-phase1.yml，不能以工作区裸启动命令代替。

| 配置 | 实际消费者 |
| --- | --- |
| mes.system.enabled、mes.platform.* | SystemConfiguration；DruidPools、PlatformPersistence、Flyway |
| mes.runtime.enabled、mes.runtime.factories | FactoryRuntimeConfiguration；物理库/连接/版本/Mapper 登记校验 |
| spring.datasource.druid.* | DruidPools；stat/wall 真实过滤器与监控 |
| spring.data.redis.* | Redis 客户端、RedisCache、SystemCacheNamespace |
| token.expire-time | TokenService；哈希 opaque token 的身份/版本与 TTL |
| user.password.*、ruoyi.captcha.* | PasswordPolicy、密码重试锁、CaptchaService、登录/注册 |
| xss.* | 通用输入/XSS 防护及富文本服务端净化 |
| springdoc.* | SDK 构建文档；默认 SDK MVC 入口关闭，显式授权 Controller 对外 |
| minio.* | 私有存储适配器；未提供外部 MinIO 连接时不伪造成功 |

prod 连接由外部提供，迁移默认关闭；本轮未启用 prod。工作区 dev/default profile 有用户调整，未经其隔离验收，连接值不抄入本文。所有本轮测试只使用自有 mes_base_phase1_* Schema，未连接其他默认库。前端 MES_DEV_API_TARGET 只允许 loopback；本轮18081/15174，Redis16379，旧18080/15173保留。

## 身份、会话与系统对象

空库没有内置账号或角色；通用产品菜单/字典来自迁移。首次管理员使用 scripts/mes-platform-admin 的空用户表检查和平台事务，Source bigint 身份/显式 PLATFORM role/menu grants，无 userId=1 超权，无 Factory grants。MES_BOOTSTRAP_PASSWORD 由调用者受控提供，应用 PasswordPolicy 来自当前 dev/prod 配置，不写日志。

POST /api/platform/session 为正式入口，Source userName 字段由适配层与 username 登录请求对应；成功返回 accessToken/expiresAt，DELETE 注销。Redis key 按平台 DB 身份命名空间隔离，令牌仅存哈希身份和会话版本；禁用、重置密码、强退撤销，当前角色/权限每次请求查 DB。重试锁使用数据库规范化的 Source userName，大小写/重音等价身份不能绕过。注册 /api/system/register 仅当 sys.account.registerUser 显式 true 才开，不能自行赋角色或 Token；验证码一次消费。dev 密码最小长度默认5，prod默认12，UTF8 BCrypt 最大72字节，UI读取服务端策略。

/api/system/** 是 Source 系统正式可写链路。旧简化 Platform 用户/角色/菜单/字典/配置/Job API 默认关闭，仅隔离历史 Fixture 测试显式打开；V2 保存旧表，不存在两套正式可写系统对象。源字段/分页/导入导出沿用 Source 成功结构，失败真实 HTTP+requestId；不用 HTTP200 冒充失败。

PLATFORM role 无 factoryId，FACTORY role 必須绑定有效厂。有效空角色身份可登录、选启用厂和 AUTHENTICATED；AUTHORIZED 缺当前范围权限拒绝。Source DataScope 是平台组织数据范围，不能当 Factory 准入。本人/外部 ID 校验在后端；数据权限 SQL 片段由受控服务生成，客户端 params 注入拒绝。无 User↔Factory绑定、首厂回退或跨厂权限缓存。

字典/参数缓存属于平台数据库命名空间，刷新只能处理当前命名空间；Factory配置按明确 kind/factoryId 隔离。操作日志保留 Source 字段与 requestId/actor/scope/costTime，但请求体、Token/Secret/密码不采集。

## 文件、消息与工具

文件默认私有，需 owner+scope+resource。personal 为本人；avatar 绑定本人；notice-media 上传要当前公告写权限，保存后绑定 Source noticeId，非 owner 读取还须当前已发布公告/查询权限。资源名和 fileId 不能当授权。Local storage 私有 UUID 路径防 symlink/穿越；MinIO只迁结构/适配器，真实对象服务尚未验收。附件不公开裸路径，无公司 bucket/账号。

消息保留 Source 表/管理页与本人 inbox/read/SSE 通用能力；sender 来自服务端身份，收件人必须有效且属于被授权平台数据范围。回调地址不执行外部调用；客户微信/邮件渠道不复制。SSE 已断开的响应不转写 JSON 错误。

自动编码保留 Source rule/part/result 和完整组成页面。预览不占号，生成必须 POST；平台事务下规则/计数行锁、周期隔离和溢出拒绝，不用 JVM 锁。当前仅平台编号工具，无 Factory/MES编号消费者。

/api/tool/swagger/json 使用 tool:swagger:view 平台权限，由 Springdoc3.0.3 SDK生成实际 DTO/Controller 契约。/v3/api-docs 和公共 Swagger UI 未登记；Vue3 SwaggerUI5.33.1同源当前会话请求，无外部 validator或认证持久化。

## Quartz 与真实监控

Source SysJob/SysJobLog 完整字段及 Cron/启停/立即运行/日志 UI 对接受控任务。暂停禁止自动触发，但授权手动运行一次仍允许；已领取任务手动再次运行409。相同 DB claim/run token 跨入口复用，执行日志区分状态和请求关联。

合成崩溃恢复必须确认原 worker 已停止、原 run token一致、节点条件匹配，并记录 UNKNOWN后清理；不能只凭超时解锁活跃任务。定时器仍是单节点 RAMJobStore，未交付集群 fire 去重或持久化集群misfire协调；内置 platform/factory-heartbeat 只是受控测试入口，不是业务同步。

Server Monitor读取本机OSHI/JDK；OSHI无挂载时只标明应用所在卷的JDK来源，不编造完整磁盘。Cache Monitor读取本轮Redis INFO/command统计；Druid只显示受授权平台池及参数化SQL模板/计数/WallFilter结果，无连接凭证。实际 Source UI和数据已验证，部署/生产监控未验证。

## SQL、验收与后续

Flyway V1–V9包含 Source完整 sys_* 与范围/会话/附件/编码扩展，旧表归档保存。scripts/export-phase1-sql.py输出原迁移的有序SQL和哈希，不另维护字段；已在新的空独立MySQL库导入并比对真实metadata。SQL不创建用户/角色。手工导入无Flyway history，之后 migrate=false；空库Flyway方式优先。

mvn -B -ntp verify；涉及打包再 /usr/bin/python3 scripts/smoke-packaged.py；UI Node22 npm run test / npm run build。Foundation/PlatformHttp测试显式关闭真实Runtime以隔离用户默认profile。测试源码、Fixture、真实API、浏览器、部署分别记录。浏览器新凭证/导入提交需要用户接手，下载产物尚未取得，不能标记全量验收完成。

OpenApi客户端/Token通用部分仍为候选；其Route授权/DataScopeGuard直接依赖未迁ProRoute，需随对应Domain分离并复用新Factory约束，不能用旧route存在性判断替代资源范围。UserTask查询真实仓储/报工单据，随MES业务后置。微信/PDA需要实际渠道配置与设备验收，Generator按授权保留后置。
