# 数据库版本升级

业务库使用项目入口 `scripts/mes-business-flyway`，复用根 POM 的 `business-flyway` Profile 和项目管理的 Flyway / MySQL 驱动版本。无需单独安装 Flyway CLI，不编译业务模块、不启动或重启服务。首次执行可能需要下载 Maven 插件依赖。

## 中心库与业务库

| 对象 | 迁移来源 | 历史表 | 入口 |
| --- | --- | --- | --- |
| 中心库 | `db/platform`、`db/system`、V34 Java Migration | `flyway_schema_history` | `FactoryFlyway`，用户受控启动时显式启用 `mes.platform.migrate=true` |
| 每个物理业务库 | `mes-admin/src/main/resources/db/business` | `flyway_business_schema_history` | `scripts/mes-business-flyway` |

`mes.platform.migrate=true` 不升级业务库。`mes-platform-admin --migrate` 同时涉及空库管理员初始化，不作为已初始化环境的通用升级入口。中心 V34 依赖环境配置，不能仅拼 SQL 目录替代完整 `FactoryFlyway`。本脚本仅支持业务库，不自动遍历工厂。

## 1. 核实目标和备份

在实际环境中心库核对工厂绑定，不从旧文档或库名推测目标：

```sql
SELECT f.factory_id, f.status AS factory_status, f.runtime_mode,
       d.datasource_id, d.revision, d.status AS datasource_status,
       d.db_host, d.db_port, d.db_name
FROM mf_factory f
JOIN mf_datasource d ON d.datasource_id = f.datasource_id
WHERE f.factory_id = '明确的目标工厂ID';
```

连接业务库记录 `SELECT @@server_uuid, DATABASE();` 和版本历史，核对实际 host/port/database。不同物理库分别升级，共用物理库只执行一次。检查所有待执行版本及其影响。

维护窗口备份受影响范围：Schema、触发器、完整历史表，以及数据变更涉及的数据。可使用权限 0600 的 MySQL 专用配置文件传递凭证：

```sh
mysqldump --defaults-extra-file=/安全路径/mysql-backup.cnf \
  --single-transaction --routines --triggers --events \
  --no-tablespaces --set-gtid-purged=OFF 目标业务库 > /安全路径/升级前.sql
```

核实备份配置中的 host/port/user，确认备份成功、可恢复；备份期间避免并发 DDL。凭证、备份均不提交 Git。

## 2. 查看并升级

在项目根目录执行，替换连接占位符；账号需要本次迁移所需权限。密码不写命令参数或版本管理文件：

```zsh
export FLYWAY_URL='jdbc:mysql://目标主机:3306/目标业务库?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export FLYWAY_USER='升级账号'
read -s 'FLYWAY_PASSWORD?数据库密码: '
echo
export FLYWAY_PASSWORD

scripts/mes-business-flyway info 49
```

核对 `info` 的数据库身份与全部 Pending 项，确认和备份计划一致后执行。`migrate` 默认先校验已有迁移，再升级；严格 `validate` 放在升级后。Flyway 12.4 的升级前独立 `validate` 会因尚未执行的版本返回 `Detected resolved migration not applied`，不能把这一情况误判为 checksum 损坏，也不应使用 repair：

```sh
scripts/mes-business-flyway migrate 49
scripts/mes-business-flyway validate 49
scripts/mes-business-flyway info 49
unset FLYWAY_PASSWORD
```

`49` 为本次示例，未来替换为实际发布版本。`migrate` 必须显式指定版本；`info` / `validate` 可省略。目标版本表示执行全部未应用版本直至该版本，不是只执行对应文件。V48 升 V49 应仅有 V49 Pending。

入口只开放 info / validate / migrate，固定业务目录和历史表，禁用 clean、自动 baseline 和 out-of-order。等价底层调用为 `mvn -B -ntp -N -Pbusiness-flyway flyway:migrate -Dflyway.target=49`，不使用 `-X` 输出含敏感配置的调试日志。

## 3. 验证和失败恢复

- 同时检查 Flyway 成功记录、真实 Schema 和正式业务入口。`validate` 校验脚本及历史一致性，不证明业务验收完成。
- V49 应有唯一成功记录，脚本列出的 21 个迁移写保护触发器应全部消失；来源、来源证据和其他正式事实保护继续保留。通过正式工单入口引用同一迁移工艺复验。
- 不改已发布 SQL，不手工运行 SQL 后补写 Flyway 历史。Checksum 不一致、缺历史或 failed 项须先定位，不用 repair / baseline / 清空数据库掩盖。
- MySQL DDL 不保证整份迁移原子回滚。中断后核对哪些 DDL 已生效及失败历史，再恢复；V49 的 `IF EXISTS` 不代表可忽略失败记录。
- 回退应用不恢复触发器。必要时根据备份及升级后业务变化制定受审查的向前迁移，不倒改版本记录或覆盖新事实。
- 数据库升级、应用部署和重启分别执行。需要重启用户服务时由用户操作，脚本不会启停服务。

实际 V49 升级和残留清理证据见 [2026-10-22 审查记录](../reviews/2026-10-20-legacy-production-migration-v22-batch-acceptance.md)。

2026-10-22 并行开发例外：V49 已开始执行时，另一工作新增 V48.1。用户明确授权合并升级后，维护命令单次设置 `-Dflyway.outOfOrder=true` 补执行 V48.1，之后恢复常规入口的禁止乱序设置并通过全部 50 份迁移严格校验。未修改 V49 或伪造版本历史。今后正常发布应递增版本，避免在已发布最大版本之前插入新脚本；乱序执行不是常规升级步骤。
