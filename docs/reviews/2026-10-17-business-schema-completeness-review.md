# SIMPLE_B 业务库表结构完整性审查

审查日期：2026-10-17。用户要求按 `flyway_business_schema_history` 及正式业务迁移审查并补齐指定库。

## 结论

目标库已完整应用当前仓库 V1～V34，无待执行迁移，未发现需要补建的表、字段或索引。
本次没有执行写入 SQL，没有修改 Flyway 历史、历史迁移文件、业务代码或用户服务。
不因用户要求“补齐”而重建已被正式迁移退役的对象，也没有新增验收库。

## 真实目标核验

使用当前 `mes-admin/src/main/resources/application-dev.yml` 的中心配置连接中心库，读取
`mf_factory JOIN mf_datasource`，核对结果：

| 项目 | 实际值 |
|---|---|
| 中心库 | mes_base_dev |
| Factory | SIMPLE_B |
| 数据源 ID | 8 |
| 主机及端口 | 192.168.18.25:3306 |
| 业务库 | mes_base_dev_business_simple_b |
| 工厂及数据源状态 | 均为 ENABLED |

业务连接凭证文件的目标主机、库名及用户名与当前绑定核对一致；报告与命令输出不包含凭证。

## Flyway 与实际结构核验

正式脚本位置为 `mes-admin/src/main/resources/db/business`，业务历史表为
`flyway_business_schema_history`。没有使用中心库的 `db/platform`、`db/system` 或中心历史表。

- 使用现有 Flyway 12.4.0 对该目录执行 `validate()`、`info()`：34 个迁移校验通过，
  当前版本 34，pending=0，V1～V34 均为 SUCCESS，历史 checksum 与解析脚本一致。
- 额外比对实际 `information_schema`，不以迁移历史成功推断实际表必然存在。
  任务专用只读脚本按数字版本顺序解释 CREATE / ALTER / DROP，形成最终结构后比对目标库。

| 对象 | 迁移最终定义 | 目标库 | 结果 |
|---|---:|---:|---|
| 业务表 | 35 | 35 | 无缺少或额外业务表 |
| 业务字段 | 358 | 358 | 名称、类型、可空、默认值、自增与显式字段注释一致 |
| 主键、唯一及普通索引 | 109 | 109 | 名称、唯一属性、列及列顺序一致 |
| 触发器 | 11 | 11 | 名称、所属表、事件与执行时机一致 |
| CHECK 约束 | 4 | 4 | 名称及所属表一致 |

以上数量不含 `flyway_business_schema_history` 的表、字段及索引。
触发器动作正文、CHECK 表达式已保存为元数据和 Schema 快照，但本轮自动比对未做表达式语义等价证明。
35 张业务表均执行只读 `COUNT(*)` 成功，确认真实库读取可用；未通过 HTTP/UI 验证业务行为。

## 已退役对象

以下表不属于当前最终结构，目标库未保留，符合对应迁移，不应补建：

- `personnel`：V6 退役独立人员投影。
- `qc_index`、`qc_defect`：V7 退役早期质量表。
- `routing_step`：V9 迁移到图节点及边后由 V10 退役。
- `md_workshop`、`md_unit_measure`：V17 移除重复主数据表。

## 证据与执行边界

本地证据目录：`.local/business-schema-audit-20261017/`，目录权限为 0700；仅留本地，
备份连接配置权限为 0600，不提交含凭证文件。

- `BusinessSchemaAudit.java`、`flyway-audit.log`：当前中心绑定核验、正式 Flyway validate/info 结果。
- `compare_schema.py`、`structure-comparison.json`：只读结构解释及比对结果，含 34 个源脚本 SHA-256。
- `schema-before.sql`：真实库完整无数据 Schema 快照，包含触发器；不是数据备份。
- `columns.tsv`、`indexes.tsv`、`triggers-constraints-history.tsv`：实际元数据及迁移历史。
- `table-read-counts.tsv`：35 张业务表只读计数结果，不采集业务记录内容。

命令结果：Java 任务入口退出码 0；Python 结构比对 `issues=[]`；mysqldump Schema 导出退出码 0；
全部表只读计数成功；`git diff --check` 通过。
首次 mysqldump 因旧 mysql 客户端配置含不受支持的 `database=` 选项退出，改用任务专用
0600 配置副本移除该选项后导出成功；没有因此修改数据库或原连接配置。

没有缺项，因此不调用 migrate 执行无必要的迁移写入，不使用 repair、baseline 或修改校验和掩盖差异。
未新增 SQL、未覆盖用户数据，故没有受影响业务数据范围需要备份或回滚。
未运行 Maven、业务测试、HTTP/UI 验收；本次证据是实际目标库结构审查与只读访问，
不代表生产业务全链路验收。没有启动、停止、重启服务，未提交、推送或部署。
