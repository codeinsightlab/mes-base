# `dev.mesbase.server` 包审查

审查日期：2026-10-06  
范围：`mes-admin/src/main/java/dev/mesbase/server` 及其直接测试/配置引用  
性质：静态代码与仓库文档审查；未删除或修改业务代码，未据此宣称运行时淘汰完成。

## 结论摘要

该包当前不是整体淘汰对象。它同时承载应用启动装配、三库/工厂运行时装配、Source Foundation HTTP 边界和迁移兼容逻辑。当前最明确的治理方向是拆分职责，而不是直接删除整个包。

## 分类

### 当前应保留

| 类 | 证据与判断 |
| --- | --- |
| `DruidPools` | 被 `PersistenceConfiguration`、`FactoryRuntimeConfiguration`、`server.managed.ManagedDatasourceService`、CLI 和测试直接使用；仍负责平台、业务运行时和 ERP 池创建。 |
| `PersistenceConfiguration` | 当前三类 `SqlSessionFactory`、事务管理器、Flyway、ERP 路由和生产装配测试的入口；不能删除。 |
| `FactoryRuntimeConfiguration` | `mes.runtime.enabled=true` 时负责数据库工厂目录、密码解密、运行时池和业务解析器；当前迁移计划明确要补齐工厂/数据源管理，不能视为过时代码。 |
| `SystemConfiguration` | 当前系统 Token、Quartz、文件存储、同步协调、用户认证和平台/工厂授权 Bean 的生产装配入口；不能删除。 |
| `SafeErrorController` | 公共 `/error` 的安全错误响应入口，有 `EndpointAccess(PUBLIC)` 和现有错误契约证据；除非由统一 Web 错误层替代，否则保留。 |
| `FoundationController` / `FoundationService` | `/health`、工厂选项、FactoryScope 上下文、偏好和访问验证仍有 `FoundationHttpTest` 与文档证据；不是无用占位接口。 |

### 当前应列为淘汰/替换候选

| 类/区域 | 原因 | 不能立即删除的边界 |
| --- | --- | --- |
| `OwnerMapperRegistration` | 当前工程规则已确定由固定包自动扫描绑定三类 SessionFactory，禁止生产依赖中央 Mapper 清单、手工注册或逐 Mapper 归属配置。该类仍在生产 `PersistenceConfiguration` 中注册 `MapperScannerConfigurer`，与目标架构冲突。 |
| `PersistenceOwner`/`OwnerMapperCatalog` 生产路径 | `OwnerMapperRegistration` 依赖它们发现、分类和校验 Mapper；目标规则只允许既有 fixture 兼容校验，不应继续作为生产扫描依据。 |
| `FoundationConfiguration` 的 disabled/fallback 分支 | `mes.system.enabled=false`、`mes.runtime.enabled=false` 时创建空授权、空用户、内存 Factory、空 Business resolver 等，主要服务于 Foundation fixture 和分阶段启动。生产正式配置已进入 Source 系统链路，长期可删除这些 fallback，但必须先迁移/拆分 `FoundationHttpTest` 的测试装配。 |
| `SystemRedisConfiguration` | 功能仍可能需要，但实现使用全限定类名和单独配置类，和当前显式 import 规则、系统基础设施聚合方向不一致；更适合作为 Redis/XSS 配置重构时合并或迁入基础设施配置，不宜单独保留为长期边界。 |

## 推荐淘汰顺序

1. 先实现固定包的标准 MyBatis-Spring 扫描，并让生产配置不再创建 `OwnerMapperRegistration`；保留它仅供 TEST ONLY fixture 过渡。
2. 删除生产对 `OwnerMapperCatalog` 的依赖后，再清理生产 `PersistenceOwner` 分类和中央清单；同步更新 `PersistenceAssemblyTest`，保留包重叠/归属负例为扫描器测试。
3. 把 `FoundationConfiguration` 的 fallback 明确移动到测试配置或独立 test fixture；确认默认启动 profile 不再依赖无系统模式后再删除。
4. 在系统配置拆分任务中合并 `SystemRedisConfiguration`，同时完成 Java 显式 import 整理。
5. 最后再考虑包名拆分：`server` 只保留应用装配，基础设施配置迁入更具体的 `platform`/`persistence` 入口；这属于结构治理，不应与本轮直接删除混在一起。

## 未验证事项

- 本审查未启动服务，也未验证删除候选后的 Spring Bean 图。
- 未确认所有外部部署 profile 是否仍需要 `mes.system.enabled=false` 的 fallback。
- 未执行固定包扫描替换，因此不能把 `OwnerMapperRegistration` 标记为已退役。
- 当前结论是静态源码、测试源码和项目文档证据，不等于生产运行时淘汰证明。

## 2026-10-06 追查：关于“已自动装配”的确认

当前源码尚未达到“生产 Mapper 已由固定包自动装配”的状态：`PersistenceConfiguration` 仍创建 `OwnerMapperRegistration`，该类仍注册三个 `MapperScannerConfigurer`；`PersistenceConfiguration` 和 `FactoryRuntimeConfiguration` 仍注入 `OwnerMapperCatalog`，并从 catalog 取得 Mapper 集合。因此不能仅凭项目文档中的目标架构描述删除这些生产类。

如果后续完成真正的固定包自动扫描，测试应按职责处理，而不是全部删除：

- 应替换/淘汰：`PersistenceAssemblyTest` 中直接构造 `OwnerMapperRegistration`、断言 `OwnerMapperCatalog` 数量以及旧 `mes.persistence.mapper-packages` 属性的测试；它们验证的是旧装配机制。
- 必须保留并改写到新装配入口：三库 SessionFactory 隔离、不同 Factory 业务连接、ERP 路由、事务边界、重叠包/同名 Mapper 启动失败等测试。
- 应保留：Foundation HTTP、权限、FactoryScope、错误响应、运行时数据源生命周期和业务服务测试；这些测试验证产品行为，不属于旧 Mapper 注册机制。
- 生成器模板 `RuoyiTemplateRenderer` 仍生成 `PersistenceOwner`/`MapperPackages` 相关断言，完成自动装配迁移时也必须同步修改，不能只删除 `server` 包测试。
