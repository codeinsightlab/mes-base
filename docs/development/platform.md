# 平台配置与运行

Base MES采用一套模块化服务、一个中心库和按Factory选择的独立业务库。底座来源、模块及产品范围见[项目README](../../README.md)，当前架构见[架构入口](../architecture/README.md)。

## 环境准备

准备Java 21、Maven、MySQL 8和Redis。复制 `config/application-dev.example.yml` 或prod示例到 `mes-admin/src/main/resources/application-dev.yml` / `application-prod.yml`，填写自己的环境参数；真实profile被Git忽略。

| 配置 | 用途 |
| --- | --- |
| mes.platform.* | 中心库、中心迁移开关和文件根目录 |
| spring.data.redis.* | 会话和缓存服务 |
| mes.runtime.enabled / datasource-key | 受管工厂数据源及32字节Base64加密密钥 |
| server.address / port | 当前profile监听地址 |
| mes.codegen.profiles.* | 开发期metadata连接，不是运行期工厂绑定 |

工厂、业务数据源及其绑定通过平台管理维护，不在YAML按Factory覆盖运行模式。连接密码由管理入口加密保存；更换已有数据源密钥需正式迁移密文，不只替换配置。

### 业务数据源连接失败与恢复

业务连接池只在请求获取连接时创建或补充连接，不运行后台重连。同一数据源身份和配置版本的运行池、首次建池及DDL临时池共用一个恢复状态和探测预算：一轮最多尝试连接5次（含首次），仍失败则冷却60秒；冷却期间请求快速失败。冷却到期不会自动连接，由随后第一个命中的请求探测恢复，成功后恢复正常使用，失败则重新冷却。关闭失败候选池不会清除冷却状态。状态属于当前节点，不跨节点共享；更新数据源配置版本使用独立状态。

每个数据源独立控制建池与恢复探测，不共享哈希分片锁。同数据源的冷池准备等待最多1秒。业务池获取连接时按同源协调：正常冷启动或扩容期间，其他请求在借连接超时范围内等待，获得机会后重新检查空闲连接、容量及冷却状态；不会仅因已有请求正在建连接就报错。协调等待与池容量等待共享本次借连接的剩余时间，等待超时或中断不触发熔断。若前一个请求已耗尽5次尝试并进入冷却，等待者直接收到冷却失败，不另起一轮重试；若已恢复，等待者继续正常取连接。连接池用满时仍按借连接超时等待，不将容量不足当成连接故障熔断。连接、读取与借连接超时继续生效；5次尝试的总耗时可能超过一次前端请求超时。

池退役、替换及最后租约释放只在注册锁内更新状态，实际关闭在锁外执行。关闭中状态防止重复关闭，关闭失败保留待重试状态；物理数据库身份预留也不在全局锁内执行关闭操作。A池慢关闭不会占用B查询注册状态或获取租约所需的锁。

业务池使用按需连接，因此不使用公共Druid配置中的`initial-size`、`min-idle`及后台保活/空闲校验设置；借用时校验连接，空闲任务仅负责回收。中心库和其他非业务池保持各自原有策略。已有定时配置协调任务只处理版本与禁用后的池退役，不承担连接恢复。

切换工厂可以取代尚未完成的切换，取消旧工厂的读取请求，并拒绝旧响应回写新工厂状态。已提交的业务写请求不因切厂而自动取消或重放；结果不确定时使用原请求身份核实。故障业务库不作为切换到其他已授权工厂的前置条件；中心库、认证服务及共享网络仍是系统共同依赖。

保存工厂偏好先通过`GET /api/foundation/preference`读取`revision`（无损十进制字符串），再以`PUT`提交`factoryId`和该版本。服务端条件更新并递增版本，迟到写入返回`409 / RESOURCE_CONFLICT`；当前仍有效的切换可重新读取版本后有限重试，网络错误或结果未知不自动重放写入。偏好接口属于平台身份范围，不解析旧工厂；它不授予工厂业务权限。此契约要求中心库先应用`V2__factory_preference_revision.sql`，前后端应配套更新，避免新旧服务同时写入偏好；旧客户端缺版本会被拒绝。旧V57等迁移链须先按[既有库切换规则](database-upgrades.md#既有库切换到-v1)完成受控接管，不能直接执行新V2或绕过校验。

```sh
mvn -B -ntp verify
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

默认profile为dev；prod显式指定 `--spring.profiles.active=prod`。配置变更需重新构建/加载相应运行产物，前端API代理目标应与实际监听端口一致。本机构建可能包含私有profile，按该环境的运行产物管理。

应用打包会重新生成薄 JAR，再由 Spring Boot 加入当前依赖，避免仅修改业务模块后增量打包复用旧的可执行包。运行中的应用使用启动时的独立副本，不能直接运行随后会被 Maven 覆盖的输出文件。

## 中心初始化与业务库升级

空中心库没有通用默认账号。首次管理员使用 `scripts/mes-platform-admin`，以实际配置为目标，密码通过 `MES_BOOTSTRAP_PASSWORD` 受控提供；脚本检查空用户表，不能用作已初始化系统的通用升级入口。

```sh
scripts/mes-platform-admin --username=admin --config=mes-admin/src/main/resources/application-dev.yml --migrate
```

执行前先准备目标库、配置和应用JAR，核实权限及密码策略。命令中的账号是初始化示例，不是系统内置凭证。

中心库迁移由FactoryFlyway或 `scripts/mes-center-flyway` 受控执行，活动链为 `db/center`；业务库由 `scripts/mes-business-flyway` 分别升级。两库均从当前 `V1__init.sql` 开始，既有库需按升级指南受控重建基线。`mes.platform.migrate=true` 不升级业务库。完整目标核对、备份、版本及失败恢复流程见[数据库升级](database-upgrades.md)。

不再提供Phase1拼接SQL导出或旧空归档清理脚本。`sql/ruoyi`仅是来源Schema对照，不作为当前初始化脚本；正式Flyway版本和已执行维护证据保留。

## 账号、权限与工厂

- 用户、组织、角色和菜单在中心库统一维护；生产操作人员使用sys_user。
- 平台管理与工厂业务授权分开。平台角色可按授予的业务权限访问工厂，工厂角色只在所属Factory生效；选厂不授予权限。
- 空工厂环境仍可登录和访问已授权平台管理；普通业务访问须有有效选厂及业务库绑定。
- 当前工厂无效时返回专用 `FACTORY_SELECTION_REQUIRED`，前端重新选厂，不自动换厂或重放写请求。
- 登录使用 `/api/session`，认证、撤销和事务固定规则见[请求准入](request-admission.md)。权限由后端当前请求校验，不信任前端按钮或传入身份。

Factory可按物理工厂、业务或年度配置数据空间，但不会自动结转、封账或跨空间汇总。LOCAL/EXTERNAL来源规则见[业务边界](../architecture/local-erp-business-boundary.md)。

## 文件、OpenAPI与监控

私有文件需证明所有者、范围与资源关联，不提供公共裸路径。系统用户会话与外部OpenAPI身份独立；客户端管理、Secret和API授权不自动授予MES业务访问。

Quartz保留任务、Cron、启停和日志；同步协调使用数据库抢占和run token。调度器不承担生产自动完结，Execution/工单按正式入库审核规则收尾。系统运行监控与生产业务统计的验收范围分开。

## 保留工具

| 工具 | 用途与前提 |
| --- | --- |
| scripts/mes-business-codegen | 显式配置的独立生成器，见[生成说明](codegen.md) |
| scripts/mes-business-flyway | 正式业务库版本维护，见[升级说明](database-upgrades.md) |
| scripts/mes-platform-admin | 空中心库管理员初始化 |
| scripts/smoke-packaged.py | 隔离配置下检查打包启动；会创建自己的临时进程，不代替业务验收 |
| scripts/check-private-config.py | 选定工作树/索引/版本的凭据模式检查，不是完整安全审计 |
| scripts/format-java | 使用本机IntelliJ格式化器，需满足脚本列出的安装路径/环境 |
| scripts/run-local-backend | 开发辅助启动，要求已有JAR；仅在明确启动授权下使用 |
| scripts/run-local-redis | 开发辅助，需事先准备.local下的二进制和配置，不是通用安装器 |
| scripts/performance/summarize-production-chain.py | 离线读取历史性能journal并输出报告，不发生产请求；不证明当前链路性能 |

旧业务写入压测、固定旧环境的读探针、一次性Source转换SQL及旧生成器启动器已移除。历史结果仍保留，不能用旧回执宣称当前版本已经复验。

## 检索选择框候选数量

公共检索选择框的候选上限由系统参数 `sys.ui.searchSelect.limit` 维护，在 `/system/config` 中修改，初始值为20，允许1至100的整数。选择框不提供分页或“加载更多”，通过关键词缩小匹配范围；已选项回显不受当前候选集合限制。

中心迁移 V4 仅在键名不存在时初始化20，不覆盖管理员已有值。该参数通过现有 `/api/common/config/{key}` 登录用户读取白名单提供，普通业务用户无需参数管理权限；新增、修改时由参数服务校验范围。参数管理沿用既有缓存失效机制，前端在下一次检索前重新读取，同一时刻的读取合并，不缓存旧值到下一轮检索。缺失、非法或读取失败时展示错误，不静默回退硬编码数量。
