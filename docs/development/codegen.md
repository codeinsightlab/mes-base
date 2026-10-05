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

## 可复现 TEST ONLY 验收

```sh
mvn -B -ntp verify
# 使用 UI 所需已安装 Node22，Maven仍为用户环境
scripts/verify-codegen-fixture
```

脚本创建自有临时 H2 metadata（无业务行），一条 CLI 自动放置 Base target 与 UI target，后处理合成状态枚举/中文状态/备注 textarea，再 javac 真实产物和测试骨架、vue-tsc/Vite 真实生成页。`--serve` 需显式合成 MES_TEST_ONLY_PASSWORD，可启动隔离 localhost 验收；不继承 MES_/SPRING_ 生产配置。TEST ONLY Server 与表不进 JAR。测试输出/manifest 在 target/codegen-acceptance 与 target/validation；使用示例不等于 MES 业务已迁入。

现已验证 H2 合成库及 PostgreSQL 兼容 SQL 形状；未做真实 PostgreSQL/MySQL 联调。生成 SQL 当前双引号标识符与 LIMIT/OFFSET，其他方言需先适配并测试，不凭 JDBC 能连接就声明 CRUD 方言兼容。
