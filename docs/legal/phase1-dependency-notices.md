# Phase1 PORT 依赖与来源

2026-10-06。逐文件 Source/目标 SHA256 在 source-product-migration-manifest.json；Source Product MIT 原文和官方 RuoYi MIT 原文继续保留。新增 SDK 只由包管理器引入，无手写等价的 OpenAPI、Excel或文件下载库。

| 依赖 | 固定版本 | 许可与来源 | 用途 |
| --- | --- | --- | --- |
| FileSaver | 2.0.5 | MIT；[官方版本](https://github.com/eligrey/FileSaver.js/tree/v2.0.5)，实际 node_modules/package.json | Source Excel 与私有文件下载 |
| Springdoc OpenAPI WebMVC API | 3.0.3 | Apache2.0；[官方 POM](https://raw.githubusercontent.com/springdoc/springdoc-openapi/v3.0.3/pom.xml) | Boot4 MVC 契约/DTO schema SDK；只登记显式授权的应用 Controller |
| Swagger UI Dist | 5.33.1 | Apache2.0；[官方版本](https://github.com/swagger-api/swagger-ui/tree/v5.33.1)，实际 node_modules/package.json | Vue3 系统接口页；关闭外部 validator及认证持久化 |
| Swagger Annotations Jakarta | 2.2.47 | Apache2.0；[官方仓库](https://github.com/swagger-api/swagger-core)，已在 Springdoc 打包依赖中核对 | Source OpenAPI DTO/接口注解；显式供 common/framework 编译使用，保持既有 SDK 版本 |

Source SwaggerController/tool/swagger 页面使用 Source permission/menu产品入口。旧 Springfox/公共iframe与Boot4、安全分类不兼容，PORT到当前SDK并复用原入口，未自行实现Swagger。兼容选择依据 [Springdoc官方兼容说明](https://springdoc.org/v2/)，实际 Boot4.1.1 起动和接口验收另见 Phase1 JSON；上游支持说明不替代本项目运行证据。

注册、文件、公告、Quartz 等字段和流程保持 Source 来源，Java21/Jakarta、DTO与资源/权限、安全HTTP适配是修改部分。新调试/验收数据只在本轮隔离Schema和私有target中；无Source公司seed或环境值。

OpenAPI Source 原有 Lombok 注解展开为等价 JavaBean/构造器，没有引入新 Lombok 依赖或调用 Codegen。业务数据范围按用户本轮决定迁为通用校验器开关，Source 工艺依赖没有迁入。源 UI OpenApiClient 工作文件有未提交修改，实际使用固定 HEAD 内容；来源清单记录该读取方式和对应哈希。

## 2026-10-10 OpenAPI 基础设施拆包

仅迁移 OpenApiEndpoint、MVC catalog 与目录模型至独立 mes-openapi；旧客户端/Token/授权/数据范围/UI 退役。模块沿用 Springdoc 3.0.3，没有增加 SDK 版本；源码来源与修改范围见 platform.md 的同日章节。Source Product MIT 原文随独立 JAR 放入 META-INF/licenses/source-product-MIT-LICENSE.txt，宿主原有许可继续保留。此前权限与 UI 说明仅为历史迁移证据。

## 2026-10-12 OpenAPI 移除部分恢复

按用户明确要求，从 Base 提交 `1c2ada6^` 恢复客户端/凭证/API 授权/独立认证链，前端从 `cef3072^` 恢复页面与 API。
采用已迁入的 Java21/Boot4/Security7/MyBatis/Vue3 版本，不从源公司工作区带入新配置、数据或 Secret，不增加依赖版本。
撤销仅文档模块的 reactor/profile 与重复目录模型，恢复到原 common/system/framework/admin 归属；Source Product MIT 许可保留在文档并随 framework JAR 打包。
2026-10-10 的退役说明为历史记录，由本节恢复决定取代；不改 V10/V16 的历史校验和。

### 2026-10-12 开发者文档资源补迁

迁入 Source 的只读 openapi-docs HTML/JS/CSS，来源与 SHA256 追加到 source-product-migration-manifest.json。
公司名称已替换，原公司 logo 未迁入；SVG 为本项目新建资源。Boot4 文档入口/只读安全链沿用源思路，使用现有 Springdoc3 SDK，不引入外部 CDN 或额外依赖。
