---
name: mes-crud-codegen
description: 为 MES Base 的已有单表生成 MyBatis Mapper/XML/Example、Spring CRUD 与统一 Vue3 页面，默认放入 mes-business，支持指定模块目录与 Java 包。用于基础单表开发；不替代旧产品迁移或复杂业务设计。
---

# MES CRUD Codegen

在 mes-base 根使用 `./scripts/mes-codegen`，先读根 AGENTS.md；参数、Schema 支持范围与证据见 [Codegen 使用说明](../../../docs/development/codegen.md)。持久化使用 MBG MyBatis3 / XMLMAPPER，上层模板使用项目 Spring 事务与 Base UI，不手写替代生成器。

## 范围与位置

- 只生成基础单表代码：Entity、Example/Criteria、Mapper + XML、Service/Controller、请求/响应契约、CRUD 页面/API/菜单与骨架测试。不自动增加锁查询、业务状态、外部集成或无必要的 ExtraMapper。
- 默认后端模块 `mes-business`，业务包 `dev.mesbase.modules.<module>`。`--output` 指定后端仓库根，`--backend-module` 指定根下模块目录，`--package` 指定 Java 包，`--frontend-root` 指定 UI 根。`--module` 是 API/页面功能模块标识，独立于 Maven 模块。
- 默认 `FACTORY_SCOPED`，只决定业务 SessionFactory 包归属、事务与 HTTP 权限范围；业务数据按 Factory 路由到独立库，不要求 factory_id，不注入 Factory 字段、方法参数或 SQL 谓词。基础设施仍拒绝缺 Scope、无效连接与事务内切厂。
- GLOBAL 必须显式选择，使用 dev.mesbase.platform.* 与 masterTransactionManager；业务使用 dev.mesbase.modules.* 与 businessTransactionManager。生产 *Mapper 自动扫描，不生成注册清单或 PersistenceOwner 注解。
- 可为以后拆包指定其他承载模块；必须遵守固定扫描根。生成命令不自动创建 Maven 构建配置、修改依赖或装配第三方数据库。

权限统一为 `<承载模块标识>:<完整表名>:read/create/update/delete`，mes-business 使用 business，mes-erp 使用 erp；表名不截取前缀，表名中的 `_` 替换为 `:`，例如 `business:pro:workorder:read`。Java/Vue/菜单 JSON 与 `docs/generated/<module>/*-menu.sql` 同步，不绑定物理数据库名。

`--module` 未指定时默认取表名第一个 `_` 前的部分，例如 pro_workorder → pro；没有 `_` 的表使用 business。显式 `--module` 优先，`--package` 可独立指定。不使用 `--model` 参数。

## 工作流

1. 核对当前任务、工作区、已有模块与真实 metadata。开发连接 profile 不等于 Factory Runtime；只读 JDBC metadata，不采样数据或显示凭证。迁移不调用本 Skill；复杂业务先设计用例。
2. 通过 `--dry-run` 检查 Schema、目标模块/包、接口与 manifest；缺连接或不支持 Schema 明确失败，不回退其他库。
3. 去掉 dry-run 发布。默认拒绝覆盖；`--overwrite` 仅在本次明确替换原生成文件时使用，仍先检查用户后处理。CLI 自动放置前后端，不重复手工搬运。
4. 检查 Mapper + XML + Example 以及 Service/Controller 的实际调用链。Service 使用标准 Mapper 方法与具名 Spring 事务，分页复用 PageHelper；HTTP 响应在 Controller 转换。Example 仅供服务端构造，不接受请求传入任意 SQL/排序；基础 MBG 条件写入的空条件范围由实际业务调用方审查。Selective 的 null 表示跳过，不代表明确清空。
5. 核对 EndpointAccess、角色权限与资源范围；生成中心 sys_menu INSERT SQL 供审查，不自动执行或授予角色。业务规则、唯一性与引用约束在实际业务开发中补充；生成成功不等于业务验收。
6. UI 复用 CrudPage/request/permission；检查查询、表单、精度、loading/empty/error、删除确认、权限按钮和窄屏。long/decimal 保持字符串精度，不猜状态含义。
7. Java 按仓库 IDEA formatter 格式化，运行定向生成代码执行测试、Maven test/verify 与 git diff --check；UI 使用 .nvmrc 对应已安装 Node 执行 test/build。涉及打包启动再运行 packaged smoke。
8. 交付实际生成位置、命令、后处理与证据边界。Fixture/Mock 不代替真实数据库；提交、推送、部署单独遵循授权。

## 命令

```sh
./scripts/mes-codegen --datasource=business-dev --table=md_example \
  --module=masterdata --business-name='示例配置' --dry-run

./scripts/mes-codegen --datasource=business-dev --table=md_example \
  --module=masterdata --business-name='示例配置' \
  --backend-module=mes-erp --package=dev.mesbase.modules.erp.masterdata --dry-run
```

支持单主键 Long/Integer/String 的普通单表；具体类型及敏感/引用字段拒绝范围见使用说明。`--config=/absolute/developer.properties` 加载受控开发配置，不输出凭证。骨架测试与 person Mock 验收不建生产表、连接或角色。
