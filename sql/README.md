# 系统 SQL 入口

当前运行 Schema 由 `mes-admin/src/main/resources/db/platform/V1__platform.sql` 和
`mes-admin/src/main/resources/db/system/V2__…V10__*.sql` 顺序构建。V1 保留原简化底座；V2 将旧表重命名保存，再建立 Source/RuoYi 完整字段表；V3–V10 补通用菜单、字典、消息附件、会话版本、自动编码、监控字段、系统接口菜单和 OpenAPI 通用表/管理菜单。没有复制公司数据或账号。

推荐在空的隔离测试 Schema 用 `mes.platform.migrate=true` 交由 Flyway 管理，成功后关闭迁移开关。现有库不要重新执行 CREATE，也不要修改已应用版本。

需要独立 SQL 文件时执行：

```sh
/usr/bin/python3 scripts/export-phase1-sql.py
```

输出 `target/phase1/sql/mysql-phase1.sql` 和来源 SHA256 清单，直接从当前十份迁移导出，不维护另一套表字段。只用于空的独立 MySQL Schema；文件包含通用产品菜单和字典初始化，无账号。手工导入不会建立 Flyway 历史，随后必须保持 `mes.platform.migrate=false`，不能让应用再执行 V1–V10。

`ruoyi/sys-schema.sql` 是官方固定版本的 CREATE-only 对照基线，不含 Base 的范围/会话/附件扩展，不能作为当前运行 Schema 的初始化文件。实际字段与验收见 [迁移报告](../docs/development/MES_BASE_REALIGNMENT_REPORT.md) 和 [验收记录](../docs/reviews/2026-10-06-phase1-verification.json)。
