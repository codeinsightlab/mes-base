# 数据库初始化与升级

版本化Schema由正式Flyway迁移维护，不提供拼接SQL作为第二条安装路径。

| 对象 | 迁移来源 | 执行入口与历史表 |
| --- | --- | --- |
| 中心库 | db/platform、db/system及注册的Java迁移 | FactoryFlyway；flyway_schema_history |
| 各物理业务库 | mes-admin/src/main/resources/db/business | scripts/mes-business-flyway；flyway_business_schema_history |

`mes.platform.migrate=true`只控制中心库，不升级业务库。空中心库初始化管理员见[平台指南](platform.md)，该初始化入口不能用作已有系统的常规升级命令。

## 升级前

1. 在中心管理中核实目标Factory、启用状态、来源模式及Datasource绑定，记录真实host、port、database和配置版本。
2. 连接目标数据库核对 `DATABASE()`、服务器身份及当前Flyway历史，检查全部待执行版本和影响。
3. 在维护窗口备份Schema、迁移历史和受影响数据，并确认可恢复。凭证、备份和业务快照不提交Git。
4. 按本次发布确定目标版本，避免并发升级、DDL及旧应用继续写入产生冲突。一个物理业务库只执行一次，不按Factory数量重复执行。

## 业务库操作

使用环境或受控凭据机制提供 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD`；密码不写进命令参数或公共文件。另显式设置本次发布的数字目标版本 `MES_SCHEMA_TARGET`。

```sh
: "${MES_SCHEMA_TARGET:?请设置本次发布的业务库目标版本}"
scripts/mes-business-flyway info "$MES_SCHEMA_TARGET"
# 核对目标、备份和Pending项后执行
scripts/mes-business-flyway migrate "$MES_SCHEMA_TARGET"
scripts/mes-business-flyway validate "$MES_SCHEMA_TARGET"
scripts/mes-business-flyway info "$MES_SCHEMA_TARGET"
```

脚本使用项目Maven Profile及受管Flyway/MySQL版本，不需要启动或重启应用。migrate必须给出目标版本，默认执行全部未应用版本直至目标；不是只执行同名文件。

入口禁用clean、自动baseline和out-of-order。升级前独立validate可能因尚未应用版本失败，应先理解实际错误；不能用repair或改checksum掩盖缺失、失败或不一致的历史。

## 升级后与恢复

- 同时检查成功迁移历史、真实Schema、数据及正式业务读取。validate只证明脚本历史一致，不证明业务已验收。
- MySQL DDL不保证整份迁移原子回滚；中断后核对已完成DDL和失败记录，再制定恢复方式。
- 已发布迁移不可重写，不手工执行SQL后伪造Flyway历史。新增修复使用后续正式版本。
- 回退应用不会自动回退Schema。结合备份和升级后新事实制定恢复或向前修复，不覆盖新增业务事实。
- 数据库升级、应用构建、部署、重启和业务验收分别执行并记录。不要因升级脚本成功就宣称新服务已生效。

## 维护记录

环境身份、备份位置、执行输出、验收结果及失败恢复证据保存在部署方的受控记录中；本机开发证据使用被Git忽略的 `.local/knowledge/`。公开指南只维护可复用步骤，不追加某个测试库的逐次回执。
