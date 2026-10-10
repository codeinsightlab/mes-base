---
name: mes-crud-codegen
description: 为 MES Base 的已有数据库表生成标准 CRUD 后端与 Vue3 后台页面，自动放入 Base/Base UI，再完成范围、权限、业务和 UI 审查。适用于表驱动列表、基础配置、增删改查和管理页面；复杂 MES 领域先设计领域，仅生成持久化骨架。
---

# MES CRUD Codegen

在 mes-base 根运行稳定入口 `./scripts/mes-codegen`；不依赖 RuoYi 内部 Controller，也不重写生成器。先读根
AGENTS.md，详细配置、支持范围及 Mapper 接入见 [Codegen 使用说明](../../../docs/development/codegen.md)。UI 优先复用相邻
mes-base-ui 的 CrudPage、request、permission 与设计样式。

## 标准工作流

1. **理解业务**：明确本轮对象、生命周期、资源拥有者和交付边界，保护已有修改。
2. **判断是否适合 Codegen**：简单单表 CRUD 优先生成。Factory Foundation、WorkOrder、Routing version、Reporting、WIP、Material
   Consumption、Quality workflow、Traceability、ERP/OpenAPI、Authorization、幂等与外部一致性必须 Domain
   First。绕过记录 `CODEGEN_BYPASS_REASON` 和受限生成范围，不能因表存在就设计领域。
3. **识别 datasource**：使用开发 profile；默认读 `mes.codegen.default-datasource`，显式 `--datasource`。开发连接不是
   factoryId / RuntimeBinding；不显示或落盘凭证。
4. **识别 table**：核对当前真实 metadata；必须显式选择 GLOBAL 或 FACTORY_SCOPED。没有开发连接时不能凭页面猜 Schema。
5. **读取 metadata**：以 `--dry-run` 调用下述命令，检查字段类型、nullable、default、comment、PK、unique、index。只读 JDBC
   metadata，禁止 SELECT / 采样业务行。
6. **调用 mes-codegen**：检查 dry-run manifest 后去掉 `--dry-run`。未知 profile、不可连接、缺表、schema
   不支持、输出冲突分别处理；不得改成首库/平台库或伪造成功。
7. **检查文件**：核对 manifest 的路径、package/import、API、哈希、DTO/VO、测试和 permission
   产物。默认拒绝覆盖用户文件；`--overwrite` 只在明确要求替换原生成文件时使用，后处理内容同样可能被替换。
8. **Backend 自动放置**：CLI 已完成临时渲染 → Base 发布与清理；核对实际文件，不手工重复搬运。
9. **Frontend 自动放置**：CLI 同时完成 → Base UI 发布；核对相邻根目录或 `--frontend-root`，不让 Vue 长期留在后端。
10. **Factory Scope Review**：独立库仍保留 Factory 身份；查 list/count/detail/update/delete 的谓词与创建赋值。审查
    unique、foreign/reference、aggregation/cache/import/export；未生成不等于已覆盖。GLOBAL 只能进入 PLATFORM，不能用它绕过业务范围。
11. **Permission Review**：全部 HTTP 方法有 PUBLIC/AUTHENTICATED/AUTHORIZED 分类；业务 CRUD 默认 AUTHORIZED。对照生成
    read/create/update/delete 标识配置显式角色权限，不能自动授予角色或将平台 Admin 等同厂角色。
12. **Resource Scope Review**：对所有 ID、fileId/externalId/reference 验证资源所属范围和主体；factoryId/userId/userName
    不得来自请求授权事实。不同厂资源 ID 必须拒绝。敏感或引用表被工具拒绝时先设计受控用例。
13. **Service / Domain 与 SQL Review**：在 Service 完成状态枚举、校验、事务及 Entity→VO；Mapper 只 CRUD。参数化、固定排序、最多
    100 页量、避免 N+1；不要让数据库默认值与 nullable 编辑意图混淆。Factory Mapper 需显式登记于对应 RuntimeRegistry，GLOBAL
    产物提供独立注册配置；权限/菜单/数据源不自动绑定。
14. **UI Review**：逐项检查页面结构、查询区、Toolbar、表格密度、列顺序、状态、日期、数量、Dialog/Drawer、表单分组、loading、empty、error、destructive
    action、confirm、pagination、responsive、中文文案、tooltip、permission button。用真实合成/开发页面确认，不只检查 Vue 源码。
15. **UI 优化**：复用 Base UI 组件；依据业务调整 options/枚举、只读字段、textarea、列宽/顺序、必填/可空提示。bigint 和 decimal
    保留字符串精度；状态语义不能根据裸数字猜。完成桌面与窄屏检查；取消删除不能改变数据，失败可重试，选厂清空旧对话框/数据。只生成
    Functional Scaffold 不能结束任务。
16. **Backend tests**：运行模块定向测试和适用的 Maven 验证，验证未认证、无权、缺 Scope、跨厂 ID、输入、SQL
    和事务。测试源码存在不代表通过；Fixture 单独标注。涉及打包/启动时运行 packaged smoke。
17. **Frontend tests/build**：按 UI `.nvmrc` 的已安装 Node 版本，运行 `npm run test`、`npm run build`
    ；实际测试生成页面，不能只跑旧组件测试。受控本地浏览器验证完整 CRUD 和权限切换；不启动读取生产配置的应用。
18. **交付报告**：记录实际命令、通过/失败、生成清单、后处理、UI 证据、范围/Mapper 选择、限制和未验证环境。提交/推送/部署遵循已有授权，不自动迁下一个
    MES 领域。

## 稳定命令

在 Base 根执行（替换表/profile/module/business-name 为当前真实目标，不采用示例作为生产业务）：

```sh
./scripts/mes-codegen --datasource=business-dev --table=md_example \
  --module=masterdata --business-name='示例配置' --scope=FACTORY_SCOPED --dry-run
```

默认后端根为当前 Base，前端为相邻 mes-base-ui。`--output=/absolute/backend-root`
和 `--frontend-root=/absolute/frontend-root` 用于隔离验收；`--config=file:/absolute/developer-config.properties`
加载外部开发配置。缺连接保持 UNAVAILABLE；不创建项目 .m2/.mvn、不改用户 Maven 环境。

支持单主键 Long/Integer/String、无外键的普通表，Factory 必须非空字符串 factory_id；不接受 secret/token/credential/password
或隐式主体/引用字段。复杂表停止生成并记录 bypass，不能偷偷删除字段。测试示例用 `scripts/verify-codegen-fixture`；它只操作自有
TEST ONLY target 目录，不是生产业务。

完成生成后，显式添加业务所需菜单（路径对应 `/module/lowerCamelEntity` 编译页面）与角色授权；Base UI
仅注册授权菜单匹配的编译页面，不执行服务端提供的任意模块 URL。
