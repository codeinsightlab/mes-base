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

## 双 V63 历史的受控归并

早期发布曾包含两种不同的 V63：工艺产品展示快照清理、销售与生产计划建表。两份原文保存在 `db/history/business-v63`，不再参与活动扫描；V64 数量规则保持原文。活动链使用 V65 归并销售/计划结构，V66 归并工艺产品快照清理。

已有环境必须先区分实际成功历史，不得把两份 V63 依次执行。只有在专门授权、完整备份、独立演练及维护窗口确认后，才能使用当前受管 Flyway 对唯一缺失的 V63 作受限 repair。执行前要求：全部既有历史成功，其他脚本及 checksum 完全匹配，校验错误仅为该 V63 缺失和预期尚未应用的 V64/V65/V66。已有 V64 的环境只应待执行 V65/V66。不得忽略其他校验错误或对齐无关 checksum。

repair 后逐字段比较原历史，要求全部原行不变，仅追加对应 V63 的成功 `DELETE` 标记；它表示不再解析该历史版本，不删除原成功记录或业务数据。然后通过正式入口升级到 V66 并 validate/info。新库从活动链正常初始化，无须 repair；不能 baseline 跳过初始化。

V65 只接受五表全缺或列、索引、约束、引擎与排序规则完全一致。全缺时创建，全同时保留数据，部分存在或结构漂移时拒绝。V66 只接受匹配定义的两列快照同时存在或同时缺失；存在时一次 ALTER 去除两列，身份关联保持原值。MySQL 部分 DDL 失败后必须停止，保留失败历史和已完成结构，不反复 repair、手动补列或直接重跑；依据备份与实际状态制定恢复方案。

中心菜单单独维护：复用 `FactoryFlyway` 注册的全部 SQL 与 Java 迁移，不能仅扫描 SQL 或调用管理员初始化代替升级。菜单定义升级不等于角色获得授权；跨环境应按菜单路径/权限及角色 kind、职责、Factory 范围审查，不复制主键、账号或用户角色关系。无法证明语义一致的授权保持待决定。

## 升级后与恢复

- 同时检查成功迁移历史、真实Schema、数据及正式业务读取。validate只证明脚本历史一致，不证明业务已验收。
- MySQL DDL不保证整份迁移原子回滚；中断后核对已完成DDL和失败记录，再制定恢复方式。
- 已发布迁移不可重写，不手工执行SQL后伪造Flyway历史。新增修复使用后续正式版本。
- 回退应用不会自动回退Schema。结合备份和升级后新事实制定恢复或向前修复，不覆盖新增业务事实。
- 数据库升级、应用构建、部署、重启和业务验收分别执行并记录。不要因升级脚本成功就宣称新服务已生效。

## 维护记录

环境身份、备份位置、执行输出、验收结果及失败恢复证据保存在部署方的受控记录中；本机开发证据使用被Git忽略的 `.local/knowledge/`。公开指南只维护可复用步骤，不追加某个测试库的逐次回执。
