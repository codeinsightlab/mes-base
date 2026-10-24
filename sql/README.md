# Schema与数据库版本

当前运行Schema以项目正式Flyway迁移为准：

- 中心库：`mes-admin/src/main/resources/db/platform`、`db/system`及注册的Java迁移，由FactoryFlyway执行。
- 工厂业务库：`mes-admin/src/main/resources/db/business`，由 `scripts/mes-business-flyway` 显式指定目标版本执行。

先核实目标和Factory绑定、备份，再按[数据库升级说明](../docs/development/database-upgrades.md)执行。中心库和业务库独立升级，不改写已发布版本、不手工运行SQL后补造迁移历史。

`sql/ruoyi/sys-schema.sql`仅作为RuoYi来源Schema对照，不含Base扩展，不用于当前初始化。旧Phase1拼接导出缺少Java迁移，现已移除；不再提供另一套SQL安装路径。

首次环境准备见[平台指南](../docs/development/platform.md)。具体环境执行记录由部署方受控保留，不作为公开安装步骤。
