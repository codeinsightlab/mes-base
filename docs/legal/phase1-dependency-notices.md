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
