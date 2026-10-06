# MES Codegen 使用说明

2026-10-05。稳定 API 为 `MesCodegenService.generate(CodegenRequest)`；开发入口为可执行 `scripts/mes-codegen`。使用用户已配置的 Java21/Maven；脚本仅在 JAR 缺失或主源码更新时调用标准 Maven package，不创建 .m2/.mvn。

## 数据源与命令
默认配置 `mes.codegen.default-datasource=dev`，dev.url/username/password 来自 MES_CODEGEN_URL/USER/PASSWORD，空 URL 是已知但不可用，不误判未知。外部配置可声明多个 `mes.codegen.profiles.<name>.url/username/password/driver-class-name`；`--config=file:/absolute/developer.properties` 加载，凭证仅环境/受控配置，禁止命令或报告打印。开发 profile 与运行时 Factory 无关。

```sh
./scripts/mes-codegen --datasource=business-dev --schema=PUBLIC --table=md_example \
  --module=masterdata --business-name='示例配置' --scope=FACTORY_SCOPED --dry-run
```

确认 JSON metadata/manifest 后删除 --dry-run。默认后端根为 cwd（必须 Base），前端根为相邻 mes-base-ui；`--output` / `--frontend-root` 指定绝对验收目录；`--package=dev.mesbase.modules.masterdata`、`--author` 可选。GLOBAL package 也可 dev.mesbase.platform.*；所有名称/路径被检查。`--overwrite` 只替换带生成标识的旧文件，不能覆盖无标识用户文件，但会替换带标识的后处理代码，使用前须审查工作区。

stdout 仅安全 JSON，stderr 为稳定错误码，失败 exit2；UNKNOWN_DATASOURCE / DATASOURCE_UNAVAILABLE / TABLE_NOT_FOUND / UNSUPPORTED_SCHEMA / OUTPUT_CONFLICT / OUTPUT_FAILURE 区分。额外开发 JDBC driver 可用 MES_CODEGEN_DRIVER_CLASSPATH；它不是运行时库绑定。生产 JAR 不带 H2。

## Metadata 与产物
只用 JDBC DatabaseMetaData 的 tables/columns/PK/index/importedKeys；getIndexInfo 使用 approximate=true，不运行应用业务 SQL、不读取数据行。类型映射 Long/Integer/String/BigDecimal/LocalDateTime/LocalDate/Boolean；记录 nullable/default/comment/index/unique。单主键 Long/Integer/String，无外键；FACTORY_SCOPED 必须非空字符串 factory_id，非 PK unique 必须含该列。secret/password/token/credential/private_key 与未知主体/引用字段拒绝；复合 PK、未知类型、其他复杂表 Domain First。

FACTORY_SCOPED 16 文件：Entity、Mapper、XML、Service、Impl、Controller、Query/Create/Update DTO、VO、PageVO、Permission、Test 骨架、API TS、Vue、permission Markdown。GLOBAL 多一个 PlatformMapperRegistration 配置。Bigint/decimal 响应字符串防前端精度丢失；状态仅原值，需要 Agent 依据真实语义抽取枚举与 options。

按两根目录预检冲突/路径/符号链接 → 临时渲染 → 发布 Backend/Frontend → 哈希 manifest → 清理。普通失败回滚本次发布；进程崩溃不能保证两个仓库跨目录原子发布，需要依据 manifest 复核。Dry-run 不创建输出。

## 接入与后处理
厂 SQL list/count/detail/update/delete 全部 factory_id 谓词，create 由服务端 Scope 赋值；独立库不能删除该身份。Service 显式 BusinessPersistence，生成 Mapper 必须进入目标厂 RuntimeRegistry 的可信装配白名单，工具不建生产连接/RuntimeBinding。GLOBAL 使用独立 PlatformPersistence，配置仅在 Platform enabled 时注册；仍需平台 Permission，不能作为厂业务绕过。

生成权限 `module:entity:read/create/update/delete` 明确声明，不自动建菜单/角色/授权。菜单对应 `/module/lowerCamelEntity` 的 Base UI 编译视图；动态 Router 只从编译 catalog 选择。审核 DTO/VO、输入/状态规则、唯一性/事务、资源 ID/关联范围、SQL/索引、UI、测试后才能交付。标准单表复用生成 Mapper；复杂业务新增 ExtraMapper。引用、聚合、cache/import/export 未生成，不能声称已实现隔离。

## 当前验收入口（2026-10-06更新）

`mvn -B -ntp verify` 保留CodegenAcceptanceTest的metadata/生成产物/冲突/范围等当前测试。旧 `scripts/verify-codegen-fixture` / `scripts/verify-codegen-http` 与TestOnlyCodegenFixture依赖已退役Platform API、旧单模块classpath与旧UI页面，现已删除。旧运行记录仅作历史证据，不能作为当前命令或当前端到端验收结论。

真实MySQL metadata/runtime测试仍须显式、独立、可销毁的双厂测试配置；不会默认对日常开发库建探针表。新开发库的业务schema为空，本次未生成新的CRUD，也未调用Codegen Skill。

### YAML profile入口

应用与CLI共享application.yml及dev/prod profiles；推荐外部配置 `--config=file:/absolute/developer.yml`。兼容已有properties开发文件，不强迫用户转换；仓库主配置与本轮受控连接已使用YAML。CLI仍独立于Factory Runtime，启动Codegen不创建池绑定或自动授予菜单/角色。
