# 代码生成器

生成器位于 `mes-generator`，用于从真实表结构生成基础持久化、CRUD和页面。它已从RuoYi来源模板演进为独立CLI，不需要启动MES应用；复杂业务规则仍由业务Service实现。

## 当前调用与新业务流程

1. 先确定业务模型及正式Schema，核实元数据连接的目标。配置文件不能替代已存在的数据库表。
2. 读取[项目配置](codegen-config.review.yaml)。这是项目取值记录，不是对所有表执行写入的默认授权；新任务应复制配置并显式选表、目标及产物。
3. 初次审查设置 `execution.mode: dry-run`、`execution.overwrite: fail`，检查报告和文件冲突。预览读取真实metadata，并会写JSON报告，但不发布生成代码。
4. 审核后改为 `write`；需覆盖生成文件时明确选择 `replace-generated`，不得以覆盖手工文件来消除冲突。
5. 检查生成SQL、接口、权限、事务和UI，补齐业务规则，再执行适用测试。生成成功不代表已执行DDL、菜单SQL或部署。

```sh
mvn -B -ntp -pl mes-generator -am verify
scripts/mes-business-codegen 配置文件.yaml target/business-codegen/report.json
```

脚本要求恰有一个 `mes-generator/target/*-cli.jar`，不自动构建、不启动服务。配置和报告路径从仓库根解析；报告文件名不决定预览或写入模式。旧应用JAR启动脚本已退役。

## 配置与产物

配置字段的完整注释以[项目YAML](codegen-config.review.yaml)为准。

| 配置区域 | 职责 |
| --- | --- |
| source | 元数据连接配置、profile及schema；不猜目标，不回退示例库 |
| selection | 目标表、排除表及产物；空tables表示全选，tableOverrides不代替选表 |
| naming / output / templates | 显式模块命名、包/目录、模板和文档输出 |
| backend / fields / limits | 接口、事务管理器、权限范围、字段与分页/批量边界 |
| frontend / menu | Vue组件、API和菜单路径；菜单SQL只生成不执行，不授予角色 |
| execution | 预览/写入、覆盖、格式化及全量预检策略 |

| 产物选择 | 内容 |
| --- | --- |
| persistence | MBG的Entity、Example/Criteria、Mapper和XML |
| backend | Spring基础Service/Controller及请求响应模型，不隐含persistence |
| frontend | 复用Vue 3 CrudPage的基础页面与API |
| menu | 菜单和权限资产 |
| test | 骨架测试；不代替业务测试 |
| all | 全部产物；不是新任务必须选择的范围 |

新增业务通常至少选择 `persistence`、`backend`、`test`；主子单据的每张表分别生成基础后端，正式页面复用[主子单据组件](master-detail-documents.md)，不机械生成两张独立CRUD页面。

## 生成边界

- 当前面向单主键Long/Integer/String单表，不支持复合主键、未知类型或实际物理外键；支持范围以配置校验和metadata预检为准。
- 单表查询复用生成的Mapper/XML/Example；只有跨表关联才使用ExtraMapper。生成器不猜业务状态、人员、敏感响应或资源授权。
- Base按独立业务库隔离，业务表不额外保存factory_id；这属于项目建模规则，不是通用生成器按字段名拒绝其他项目的规则。
- `MES-CODEGEN`标记表示生成器维护归属。手工接管时按AGENTS移除标记；已有不同内容且没有可覆盖标记时返回 `OUTPUT_CONFLICT`，不强行恢复标记。
- 当前项目 `generateSync: false`。开启可选同步生成需另行明确配置和适用业务，不代表已接通ERP；关闭开关不会自动删除以前生成的独立文件。
- 生成器仍有内部/测试使用的Java入口和配置类型；本次只移除旧shell启动器，未删除这些仍有调用的Java类。

Agent工作流见[Codegen Skill](../../.agents/skills/mes-crud-codegen/SKILL.md)。早期方案、生成记录与测试回执完整保留在[生成器历史](../archive/development/codegen-history.md)。
