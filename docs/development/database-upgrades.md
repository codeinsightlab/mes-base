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

## 2026-10-22：V51 将业务触发器规则迁到服务

`V51__move_business_guards_to_services.sql` 仅删除23个既有业务触发器，不改业务数据。工单草稿编辑、下游依赖检查、退回草稿及LOCAL/EXTERNAL维护边界由当前Service负责；旧迁移中保留CREATE TRIGGER是历史升级记录，不应倒改。

目标测试业务库已通过项目入口升级到51并完成严格校验。其他物理库须独立核实目标及应用代码版本，再使用上述入口以51为目标升级；不要仅从脚本存在推断各库都已执行。应用部署/重启与数据库升级分别验收。V51并非此前未执行的工艺合并草案，本次没有合并工艺或改写工单引用。执行与验证边界见审查记录的“LOCAL规则落地与V51去触发器升级”章节。


## 2026-10-23：一次性工艺数据维护与Flyway分离

本次工艺归并只修复指定测试库的已有数据，包含固定业务ID及中断后的特定恢复范围，不应注册为所有环境共用的数据库升级。此前把它登记为V52属于归类错误，现已从Flyway扫描目录移出；SQL和原始执行回执转入[独立数据维护归档](../operations/data-maintenance/README.md)。归档只供审计，已完成的合并不能重跑。

目标库只撤销了这次误登记的V52历史，保留原始记录作为独立审计；其余52条成功历史及checksum不变。业务数据没有回滚，685个重复版本的合并、工单及事实节点引用调整和既有验收结果保持不变。当前正式Flyway业务版本为51，`scripts/mes-business-flyway validate 51`通过52份迁移，`info 51`显示51/Success。

今后同类一次性维护SQL放在Flyway扫描目录之外，按明确的目标、存量映射、事务及结果核验执行，保存独立审计，不借版本升级入口执行，不手工新增Flyway版本记录。正式结构演进和各环境需要一致执行的数据变更继续使用Flyway。此例是对本次误登记的明确纠正，不是允许清理其他成功历史或使用repair掩盖校验异常。

完整历史、合并验收和归类纠正见[审查记录](../reviews/2026-10-20-legacy-production-migration-v22-batch-acceptance.md)。没有启停或重启用户服务。

## 2026-10-23 V52 执行批次与扫码行为分离

正式文件`V52__execution_batch_and_scan_activity.sql`新增Execution.batch_no、工单/产品/批次普通索引和独立mes_production_scan_activity行为表；不新增业务库factory_id、UNIQUE或外键，不删除旧绑定数据，不改已发布V29/V31。不同于上方已归档的工艺归并，这次V52是正式公共Schema演进，没有复用旧checksum或伪造迁移历史。

本次在核实的TEST_DEV_PERF_20261019 / datasource9 revision2 / LOCAL → mes_base_dev_perf_test执行`info 52`（仅V52 Pending）、`migrate 52`、`validate 52`，迁移成功、53份严格校验通过；未启动或重启服务。1110条已有正式批次随V52搬运；10764条冻结迁入批次按独立有目标的数据维护事务执行，不放入通用迁移。备份、写后原列保持和SQL摘要见[数据维护归档](../operations/data-maintenance/README.md)。其他环境未授权升级，没有自动遍历其他Factory。

数据库升级不代表运行中旧JVM已加载新代码，需用户加载新包并重启；扫码入口、新查询及质量批次回显的真实服务验收仍须分列。代码回退保留新增字段/行为表和原历史，不降级/删除迁移记录或重跑已执行维护SQL。

## 2026-10-23 V53 物理删除退役扫码绑定表（用户确认）

用户最终选择保留已执行V52，新增`V53__drop_retired_production_scan_identity.sql`。V52原文和checksum不变，不执行repair或手改迁移记录。V53只删除`mes_production_scan_identity`；Execution正式批次和独立扫码行为表继续保留。

其他业务库按正式版本链升级：V52先新增/搬运正式批次，V53再删旧绑定表。执行前重新核实Factory→Datasource→物理库、备份旧表DDL/全部行和必要关联记录，确认每条旧绑定对应的Execution存在且batch_no与material_lot_code原文一致、无外键/触发器/视图等依赖。V52对缺失或歧义批次不会猜值，发现冲突应先受控核实，不得直接删表丢掉唯一依据。旧版应用仍会访问旧表，升级窗口须确保旧节点不再承接业务；可先升52、由用户加载新包后升53，或在用户控制的离线窗口顺序升53后启动新包。Agent不自行启停服务。

核实并准备私有连接环境后，使用`scripts/mes-business-flyway info 53`检查Pending，再`migrate 53`、`validate 53`；不要手动DROP后补历史或修改V52。已执行53的库不重复执行SQL，不以IF EXISTS掩盖手工漂移。

本次仅实际升级mes_base_dev_perf_test：TEST_DEV_PERF_20261019 / DS9 revision2 / LOCAL，原server_uuid一致。重新备份1110条旧绑定、11874条执行、行为表及53条原迁移历史，批次承接冲突和引用均0；正式info仅53 Pending，migrate成功到53，validate严格校验54份迁移通过。写后旧表不存在，11874条执行含正式批次的全部字段与写前逐条一致，53条原迁移历史全部一致，只新增V53成功记录。回执和恢复边界见[数据维护归档](../operations/data-maintenance/README.md)。未执行其他库或修改中心Factory绑定。

## 2026-10-23 V54 移除冗余执行编号

V54__remove_redundant_execution_no.sql删除mes_production_execution.execution_no及普通索引idx_execution_no，保留id、execution_id关联和batch_no。不改写V16/V45等已发布迁移；V49已退役引用旧列的历史触发器，其他库仍须核实自身依赖。

本轮核实TEST_DEV_PERF_20261019 / DS9 revision2 / LOCAL → mes_base_dev_perf_test及server_uuid，备份11874条执行、DDL及54条原迁移历史，触发器/视图/例程/外键对该列依赖0。正式info/migrate/validate 54成功，55份迁移校验通过；写后执行其余字段及原历史逐条相同。私有备份在.local/execution-id-only-20261023/，公开摘要见[回执](../operations/data-maintenance/2026-10-23-execution-id-only-receipt.json)。

其他库按版本链升级54，没有自动遍历执行。旧包仍查询旧列，升级窗口须由用户控制旧节点和加载匹配新包；Agent没有启停服务。恢复优先修复并向前升级；如需恢复Schema须根据备份与新增业务另作受控迁移，不能倒改已执行脚本、删除Flyway记录或覆盖新事实。


## 2026-10-23 V56 内部生产模型转换审计结构

V55由并行物料组成工作完成后，本任务按正式入口独立info/migrate/validate到V56，仅创建mes_production_conversion_archive（原表、原主键、完整JSON原行、本次转换标识及原因、归档时间），不修改公共升级链中的固定业务数据、不创建运行时兼容入口。目标仍为TEST_DEV_PERF_20261019 / datasource9 / LOCAL / mes_base_dev_perf_test，57份迁移校验通过；未改已发布迁移或其他Factory。

随后按用户授权执行独立数据维护事务：退役动作、旧完工归档及状态恢复，另删除指定58执行整组；44104原行留存只读审计，4条正式完全入库和库存不变。公共版本升级不自动遍历/恢复其他环境的外部数据。完整备份、原始SQL摘要、实际影响数、逐表保全及验证见[维护回执](../operations/data-maintenance/2026-10-23-production-model-normalization-receipt.json)。回退应用不会恢复旧状态模型；需要恢复数据时必须防止覆盖本次之后的正常业务。
