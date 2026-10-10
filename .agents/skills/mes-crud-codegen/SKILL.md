---
name: mes-crud-codegen
description: 根据显式项目配置和已有表 metadata 生成 MBG Mapper/XML/Example、Spring 单表 CRUD 与统一 Vue3 页面。用于新增简单业务及复杂业务中的基础单表部分，不替代用户建模、旧产品迁移或复杂业务编排。
---

# MES CRUD Codegen

## 当前入口

先读取仓库 AGENTS.md 第 13/14 节及 [Codegen 使用说明](../../../docs/development/codegen.md) 的“当前调用与新业务流程”。使用用户指定的配置；未指定时读取 docs/development/codegen-config.review.yaml，其当前内容决定所有项目参数，不假定执行模式或覆盖策略。

```sh
mvn -B -ntp verify
./scripts/mes-business-codegen docs/development/codegen-config.review.yaml target/business-codegen/report.json
```

脚本需要最新打包 JAR，不自动构建、不启动运行期服务。报告名称不决定是否预览。旧 scripts/mes-codegen 为历史 CLI，不读取此 YAML，不作为新增业务的默认入口；历史参数只在明确维护旧调用时参考文档。

## 模型与生成范围

- 用户模型优先。无完整模型时由 Agent 按 AGENTS 建模，表与每列提供数据库 COMMENT，主键外仅新增普通索引。建表 SQL 的保存、执行和真实 metadata 读取是不同步骤，不擅自对真实库执行 DDL。
- 简单和复杂业务都为生成能力支持的表生成完整基础后端，供其他业务复用；跨表协调、状态规则和业务动作尽量由一个代表业务用例的 Service 聚合生成的基础 Service 编排，需要时引入小领域。生成入口不能绕过业务规则。前端可以不生成，也可以生成后暂不使用。
- 当前结构支持单主键 Long/Integer/String 的单表，不支持复合主键、未知类型或实际物理外键。不得根据 user_id、owner_id、password 等字段名推断业务并拒绝整表；敏感响应及主体/资源授权由使用方审查。
- 复用 MBG MyBatis3/XMLMAPPER 生成 Entity、Example/Criteria、Mapper/XML，上层 Spring Service/Controller 与真实 Mapper 同步；分页用 PageHelper，不生成 DISTINCT、注解 SQL、锁查询、Factory 字段或谓词。
- 单表操作一律复用生成的 Mapper/XML/Example，禁止生成或手写 ExtraMapper/Extra XML，不能以自定义 DAO/Repository 改名绕过。条件复杂、单表统计、批量或状态规则均不构成例外；能力不足修补通用生成机制，不只补某个表。只有跨表关联查询允许 Extra，且不复制单表 CRUD；生成器不自动生成跨表 Extra，Service 聚合多个生成 Service 也不要求 Extra。
- 目标目录/包、事务、HTTP 路由/范围、权限和 UI 引用由配置显式提供。Base 使用方核对固定包扫描、平台 URL 及双库约定；生成器不改 Maven reactor、不装配连接、不授予角色。

## 工作流

1. 判断业务复杂度、沿用或补齐模型，确认已有表或可审查建表 SQL。生成需要真实 JDBC metadata；未执行建表时不能把 SQL 文件当作可读取的表，也不能悄悄回退 fixture。
2. 核对配置。selection.tables: [] 为所选库全量，非空只生成指定表；新功能通常显式选目标表，不能因空列表覆盖无关功能。excludeTables 排除表，artifacts 独立选产物，tableOverrides 只覆盖命名而不选表。完整后端至少选 [persistence, backend]，不能误以为 backend 包含 Mapper/XML；通常连同骨架测试选 [persistence, backend, test]。需要前端及菜单再加 frontend/menu，或使用 [all]。配置缺项/不支持策略明确失败，不猜默认值。
3. 预览用 dry-run；写入新增文件用 write/fail；获授权重新生成用 write/replace-generated，仍审查已有手工修改。已有不同内容可能在 dry-run 阶段冲突，不能自动改覆盖策略。审查副本可复制配置到自有 target 并明确修改输出位置/模式，保留用户原配置。
4. 核对生成 Mapper/Example/SQL、Service/Controller、输入与响应、资源权限及事务。Example 只供服务端构造，不接收原始 HTTP SQL/排序。Selective 的 null 是跳过字段，不代表清空。
5. 菜单 SQL 为 add-or-update，保留 menu_id/角色关联；只生成，不自动执行或赋权，串行重跑不承诺并发防重。单删/批删统一 DELETE 数组接口，共享 delete 权限。
6. 页面复用 CrudPage/request/permission，核对 items/total 契约、字段精度、查询条件、表单、权限按钮与错误状态；不自动推断人员组件、状态字典等业务语义。必要调整应落模板/配置，不只修示例。
7. 按 AGENTS 格式化改动的 Java，执行相应生成/业务测试及 Maven verify；改 UI 时按前端规则 test/build，涉及打包再执行 smoke。交付目录、报告、实际命令、后处理和未验证边界；生成成功不等于已建表、执行菜单或服务部署。
