# 数据库初始化与升级

中心库和业务库分别维护独立的 Flyway 版本链，当前均从 `V1__init.sql` 开始。初始化结构以收敛时的正式 MySQL DDL 为依据，要求 MySQL 8.0 或以上版本。

| 对象 | 活动迁移目录 | 执行入口 | 历史表 |
| --- | --- | --- | --- |
| 中心库 | `mes-admin/src/main/resources/db/center` | `FactoryFlyway` / `scripts/mes-center-flyway` | `flyway_schema_history` |
| 各物理业务库 | `mes-admin/src/main/resources/db/business` | `scripts/mes-business-flyway` | `flyway_business_schema_history` |

`mes.platform.migrate=true` 只控制中心库，不升级业务库。中心迁移不再注册旧版 Java 数据转换。两个目录中后续变更各自使用 `V2__...sql`、`V3__...sql`，不得改写已发布的 V1。

## 空库初始化

先由部署方创建目标数据库，使用 `utf8mb4` 字符集。V1 只在空库创建当前正式表、索引、约束和注释；不创建数据库，不删除旧表，不转换旧业务事实。

中心 V1 同时提供产品菜单、基础字典、全局单据编码规则及两个元数据单例。它不包含用户、角色、角色授权、工厂、数据源、ERP连接、业务记录、日志、会话或已经使用的编码计数。初始管理员通过[平台指南](platform.md)的正式入口创建。

使用环境或受控凭据机制提供 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD`，密码不能写入命令参数或公共文件。例如，中心和业务库分别设置自己的 JDBC URL，然后执行：

```sh
# 中心库凭据和地址已核实
scripts/mes-center-flyway info 1
scripts/mes-center-flyway migrate 1
scripts/mes-center-flyway validate 1

# 切换为已核实的业务库凭据和地址
scripts/mes-business-flyway info 1
scripts/mes-business-flyway migrate 1
scripts/mes-business-flyway validate 1
```

管理员初始化和 Flyway 版本维护是不同职责；已有中心库不能通过管理员初始化代替升级。

## 既有库切换到 V1

这是一次不兼容的版本链收敛：旧中心 V57 / 业务 V85 等历史不能直接使用新链 validate，也不能在已有表的库上执行 V1 建表。其他环境不会自动转换，必须分别安排维护窗口。

切换必须满足以下条件：

1. 核实服务器身份、`DATABASE()`、中心 Factory 与 Datasource 绑定、启用状态和来源模式。复制库的远端绑定不代表本机副本已接入应用；不能顺便修改绑定或访问远端。
2. 停止相关写入，由运行负责人安排应用维护。Agent 不自行启动、停止或重启用户服务。
3. 完整备份两库，包含结构、数据、迁移历史和必要数据库对象；另外保留旧迁移原文，核对备份可读取及校验和。
4. 比对实际结构与对应 V1 的全部表、字段、索引、约束、字符集及排序规则。V1 去除的表级 `AUTO_INCREMENT` 当前值不属于结构差异。存在缺失或额外业务结构、迁移失败或未完成转换时，先处理冲突，不能用 baseline 掩盖它们。
5. 在取得明确重建历史授权后，仅重建对应 Flyway 历史表，通过受管 Flyway 的 `baseline` 操作登记版本 1。保留全部业务表及原数据，不执行 V1 的建表或种子 INSERT。
6. 执行正式入口的 validate、info；核对两库各一条版本 1 的 `BASELINE` 记录，并比较全部非历史表的结构、行数及数据校验和。

`BASELINE` 表示现有结构已被人工核对并接管，不表示 V1 SQL 在该库执行成功，也不记录 V1 SQL checksum。空库安装才会产生 V1 的 `SQL` 历史及 checksum。已有数据保护和空库初始化必须分别验证。

普通维护脚本只开放 info、validate、migrate，不提供重置历史的日常入口。受控切换时使用项目受管的 Maven Profile（`center-flyway` / `business-flyway`）执行 baseline，显式固定迁移目录、历史表和 `baselineVersion=1`；不得使用 clean、repair、自动 baseline 或改 checksum 绕过问题。

切换中断后停止继续升级，依据备份恢复原历史及匹配的旧应用制品。未发生业务结构变更时不恢复业务数据，避免覆盖维护窗口外的新事实。

## 后续升级

1. 在中心管理核实目标 Factory、来源模式及 Datasource 绑定，记录真实 host、port、database 和配置版本。
2. 连接目标数据库核对服务器身份、当前 Flyway 历史及全部 Pending 迁移，备份受影响范围。
3. 确定目标版本。先完成结构和必要事实转换，再加载依赖新结构的应用；MySQL DDL 不能依赖事务回滚恢复。
4. 为对应库设置受控连接凭据，执行 `info VERSION`、`migrate VERSION`、`validate VERSION`、`info VERSION`。一个物理业务库只执行一次，不按 Factory 数量重复执行。
5. 检查结构、数据及相关业务读取，分别记录 SQL 已保存、数据库已执行与业务已验收的范围。

脚本使用项目 Maven Profile 及受管 Flyway/MySQL 版本，不需要启动应用。migrate 必须显式给出目标版本；默认执行全部尚未应用版本直到目标。入口禁用 clean、自动 baseline 和 out-of-order，不能忽略失败或校验错误。

## 历史迁移与回归夹具

旧 V1–V57 中心链、旧业务链（包括分支 V63 原文）位于 `mes-admin/src/test/resources/db/legacy`，旧 Java 转换迁移位于测试源码。它们仅用于已有隔离回归测试，不打包、不参与生产 Flyway 扫描，也不作为新安装或旧环境直接升级的入口。旧结构夹具仍须按当前 Mapper 契约补足必要字段。

旧环境中的业务事实、冻结请求、原身份、来源绑定及审计证据继续保留。版本编号收敛不授权清除业务数据，不恢复已经退役的业务入口，也不替代旧环境必要的事实转换。来源许可与版权说明见 `docs/legal/`。
