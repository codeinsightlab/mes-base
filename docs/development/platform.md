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

```sh
mvn -B -ntp verify
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

默认profile为dev；prod显式指定 `--spring.profiles.active=prod`。配置变更需重新构建/加载相应运行产物，前端API代理目标应与实际监听端口一致。本机构建可能包含私有profile，按该环境的运行产物管理。

## 中心初始化与业务库升级

空中心库没有通用默认账号。首次管理员使用 `scripts/mes-platform-admin`，以实际配置为目标，密码通过 `MES_BOOTSTRAP_PASSWORD` 受控提供；脚本检查空用户表，不能用作已初始化系统的通用升级入口。

```sh
scripts/mes-platform-admin --username=admin --config=mes-admin/src/main/resources/application-dev.yml --migrate
```

执行前先准备目标库、配置和应用JAR，核实权限及密码策略。命令中的账号是初始化示例，不是系统内置凭证。

中心库迁移由FactoryFlyway受控执行，包含SQL和Java迁移；业务库由 `scripts/mes-business-flyway` 分别升级。`mes.platform.migrate=true` 不升级业务库。完整目标核对、备份、版本及失败恢复流程见[数据库升级](database-upgrades.md)。

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

配置演进、原迁移与真实环境回执见[平台历史](../archive/development/platform-history.md)，不作为当前命令入口。
