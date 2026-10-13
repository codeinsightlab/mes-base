# Source → Base 最小验证 SQL

2026-10-12。历史首轮保存五份单表 `INSERT INTO ... SELECT ...`，未执行真实数据转换。
不是生产升级入口，也没有建立 ETL/迁移框架。来源及字段映射见
[Schema 审查](../../../docs/migration/SIMPLE_SCHEMA_REVIEW.md)。

## 执行前提

1. 仅使用已获准的单工厂隔离真实数据副本。脚本中的 `ktg_mes_isolated` 是隔离副本库名占位，
   不是已存在或已获授权的库；没有访问 Source 的任何数据库。真实副本库名由执行者核实后显式替换。
   若副本包含多厂，先核实并取对应工厂切片，不能把全量数据赋予一个工厂。
2. 在现有业务测试连接上先核对 `SELECT DATABASE()` 为 `mes_base_dev_business`。
   副本需要位于同一获准测试实例，或先在获准范围内导入；脚本不会连接其他实例。
3. 在同一 SQL 会话显式设置 `@target_factory_id` 为已核实的启用工厂 ID。
   无默认值；缺少该变量且存在待导入行时，目标 `factory_id NOT NULL` 会拒绝写入。
   字段身份不是授权，不能据此替代 Base FactoryScope/分库/权限。
4. 目标对应表必须为空。脚本不覆盖既有数据、不使用 `IGNORE`、`REPLACE`、upsert 或 `NOT EXISTS`
   掩盖主键冲突。只迁单厂，保留来源主键；不同来源厂的 ID 重用不能合并到同一业务库。
5. 核对实际副本 metadata 与审查采用的 Source 结构一致。执行前检查非空、长度、重复编码、
   Y/N 标记、类型字典、单位主引用，以及 DECIMAL 的范围/舍入。
   不使用 `--force`；发生错误应停止并回滚当前会话，不能继续到 COMMIT。
6. 车间 `dept_id` 暂置 NULL。只有核实旧部门 → Base 中心目录 ID 映射后才补关系，
   不把源库部门主键当成现有中心目录主键。负责人保持来源文本。

## 映射与核对

每份 SQL 保留来源主键、主要业务字段和四个审计字段；新增工厂身份使用显式变量；
未使用的 `attr1..4` 不迁。车间的 `attr1` 已有 ERP 仓库语义，是本轮剥离的外部绑定，
并非无意义字段；`admin_unit_code/employee_code/warehouse_code/dept_name` 同样不复制。
没有软删除过滤：Source 当前规则为物理删除，不能自行推断 `del_flag`。

导入后核对行数、主键集合、每个字段映射、主单位引用、工厂身份、重复编码及精度差异。
真实数据转换通过也只证明测试副本映射；不等于 API、权限、并发或生产业务验收。
本轮验证只使用事务内合成输入并回滚，未留存测试业务记录。

## 2026-10-13 当前转换范围

Quality = DEFERRED。quality-index.sql 与 quality-defect.sql 已删除，不再提供检测项或缺陷转换。
此前两份映射及合成验证属于 HISTORICAL，Source 证据保留在 docs/migration，不能继续作为正式转换断言。
其他转换脚本保持原有范围和执行前提。
