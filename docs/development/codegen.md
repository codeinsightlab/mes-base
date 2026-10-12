# MES Codegen 使用说明

## 当前生成器构建与启动入口（2026-10-12）

生成器代码、模板及配置处理均在 mes-generator。独立构建命令：

```sh
mvn -B -ntp -pl mes-generator -am verify
./scripts/mes-business-codegen docs/development/codegen-config.review.yaml target/business-codegen/report.json
```

脚本使用 `mes-generator/target/mes-generator-*-cli.jar`，不再依赖整个 MES 应用 JAR 或启动应用。
模块保留普通 JAR 供应用复用，并额外产出 cli classifier 的可执行 JAR，包含 metadata 所需的
MySQL JDBC 驱动。配置仍是唯一参数来源；mode/overwrite 取当前 YAML，不自动生成或覆盖业务代码。
以下日期化记录中的旧打包应用入口属于历史行为。


## 可选同步生成与统一配置（2026-10-12）

以下为当时的生成记录；当前同步合入主 Service/Controller，模板统一与模块归属以文末最新记录为准。

正式参数入口唯一为 `docs/development/codegen-config.review.yaml`。本项目没有生成配置网页、配置数据库表或独立下载接口；YAML 就是持久配置，BusinessCodegenCli/Runner 与 Java API 读取并传递同一参数。dry-run 与 write 共用 metadata、模板和格式化流水线，报告文件名不是开关。

- `generateSync`：布尔值，工具默认 false，历史缺参也为 false；当前统一配置按用户要求显式 true。
- `sync.matchKey`：物理列名；`primary-key` 表示明确选择 metadata 主键，不自动把 ERP 编号当 MES 自增 ID。可用 `tableOverrides.<table>.syncMatchKey` 覆盖。开启时配置必需，字段不存在、不是 String/Long/Integer 或被审计字段排除则预检拒绝，不写半套文件。匹配列来自配置和真实 metadata，不猜 code/name/status/externalId。
- `sync.batchMaximum`：明确的批次上限，当前 1000，范围 1..10000。当前同步生成支持 FACTORY_SCOPED；平台 CRUD 不受影响，平台同步需另行解决其协调协议，不能用默认工厂兜底。
- `selection.excludeTables`：现有排除选项；按用户确认加入 `flyway_business_schema_history`，值存在统一配置，不在工具代码硬编码技术表黑名单。
- 标准 CRUD 仍有四权限；开启时增加 `<permission>:sync`，Java 常量、独立 Controller、API、按钮、菜单 SQL/声明及权限文档一致。SQL add-or-update，保留菜单 ID/角色关联，只生成不执行。

开启时额外生成：External DTO（字段仅来自 Schema）、QueryPort、默认未接入 Adapter、SyncResult、SyncService、SyncWriter、SyncController 共七个 Java 文件。默认 Adapter 不注册为竞争 Bean，ObjectProvider 在没有真实 Port Bean 时选择默认；未来注册单个具体 Port Bean（或明确 Primary）即可替换。默认返回 422 / SYNC_QUERY_NOT_IMPLEMENTED，明确“暂未接入外部同步查询接口”，不返回空列表/假成功。

查询先在本地写事务之外完成；默认查询失败不写业务数据或同步状态。外部查询可并发，查询成功后复用 SyncCoordinator 按当前 Factory＋表标识抢占，抢占成功才执行写批次。SyncWriter 为独立 Spring Bean，具名事务实际经代理调用；字段/批内重复键校验、一次 IN 匹配、已存在重复键拒绝、按匹配键新增/变化更新/相同跳过、Nullable 清空和检查受影响行数。生成器不推断状态枚举；业务状态和映射规则由使用方补齐。没有新增 ExtraMapper 或第二套 SQL。

前端复用 `components/business/SyncButton.vue`：独立权限、确认、loading/防重复、取消不请求、四计数提示、成功刷新、真实错误和切厂/离开后的迟到结果处理。false 产物没有同步 import、注入、API、常量、按钮或 loading；当前手写 Material 能力仍保留，未因工具默认 false 而删除。

旧 Java API 构造器保持默认 false，新 CodegenRequest 支持 generateSync/syncMatchKey；旧 CLI 增加 `--generate-sync=true|false --sync-match-key=COLUMN`，但本项目仍只用统一 YAML 入口，不从旧 CLI 填隐藏项目默认值。

### 覆盖与关闭开关的实际行为

ScaffoldWriter 采用预检＋覆盖带 MES-CODEGEN 标记文件，不做增量合并，也没有独立文件的历史清理/所有权清单机制。因此 true→false 的新产物不含同步，但旧七个独立 Java 文件不会自动删除，可能仍参与编译/装配。必须审查后逐个移除下列已生成文件；不删除业务数据、手写 Material、整个业务目录，也不自动撤销数据库菜单/权限。清理旧源文件后须检查旧 class/JAR 残留（必要时保留自有报告/备份后 clean build）。

以下清单仅来自本次实际发布 JSON，不扫描手写业务：

- `dv_subject`：
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/ExternalDvSubject.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/DvSubjectQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/UnconfiguredDvSubjectQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/DvSubjectSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/DvSubjectSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/sync/DvSubjectSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/dv/controller/DvSubjectSyncController.java`

- `md_unit_measure`：
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/ExternalMdUnitMeasure.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/UnconfiguredMdUnitMeasureQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/controller/MdUnitMeasureSyncController.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/ExternalMdWorkshop.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/UnconfiguredMdWorkshopQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/controller/MdWorkshopSyncController.java`

- `md_workshop`：
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/ExternalMdUnitMeasure.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/UnconfiguredMdUnitMeasureQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdUnitMeasureSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/controller/MdUnitMeasureSyncController.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/ExternalMdWorkshop.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/UnconfiguredMdWorkshopQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/sync/MdWorkshopSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/md/controller/MdWorkshopSyncController.java`

- `pro_workorder`：
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/ExternalProWorkorder.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/ProWorkorderQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/UnconfiguredProWorkorderQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/ProWorkorderSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/ProWorkorderSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/sync/ProWorkorderSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/pro/controller/ProWorkorderSyncController.java`

- `qc_defect`：
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/ExternalQcDefect.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/UnconfiguredQcDefectQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/controller/QcDefectSyncController.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/ExternalQcIndex.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/UnconfiguredQcIndexQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/controller/QcIndexSyncController.java`

- `qc_index`：
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/ExternalQcDefect.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/UnconfiguredQcDefectQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcDefectSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/controller/QcDefectSyncController.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/ExternalQcIndex.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexQueryPort.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/UnconfiguredQcIndexQueryAdapter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncResult.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncWriter.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/sync/QcIndexSyncService.java`
  - `mes-business/src/main/java/dev/mesbase/modules/qc/controller/QcIndexSyncController.java`

### 当前六表生成结果

使用统一 YAML：selection.tables=[]、排除 Flyway、generateSync=true、write/replace-generated。真实业务库读取 metadata 后预检 6 supported / 0 rejected，实际发布 162 个产物。匹配键为 dv_subject.subject_code、md_unit_measure.measure_code、md_workshop.workshop_code、pro_workorder.workorder_code、qc_defect.defect_code、qc_index.index_code，均明确配置并经 metadata 校验。

报告 `target/codegen-sync/generated.json`；20 个已有目标文件的覆盖前备份在 `target/codegen-sync/backup/manifest.json`。未对真实业务表或菜单执行 INSERT/UPDATE/DDL，不授予角色，不重启用户服务，不提交/推送。

注意普通索引不保证与其他 CRUD 入口的并发唯一性；真实 ERP 实现仍需审查匹配键的来源映射、数据库排序/大小写规则及业务状态约束。重复现存键明确拒绝，不能据此承诺跨节点 exactly-once 或生产验收。


## 当前调用与新业务流程（2026-10-11）

新增业务使用 mes-crud-codegen Skill，通过 `scripts/mes-business-codegen CONFIG_YAML REPORT_JSON` 读取显式 YAML，调用 ConfiguredCodegenService。旧 `scripts/mes-codegen` 及下方旧参数说明保留为历史入口，不用于替代用户当前配置。

先按 AGENTS 第 13.0 节建模和判断复杂度：用户已有完整模型就沿用；否则 Agent 补齐当前需求所需的最小模型。新表与每列必须有数据库 COMMENT，主键外新增索引只用普通索引，不擅自增加 UNIQUE/外键。可先保存项目 `.sql`，实际建表按授权执行；生成器只读取实际存在的表 metadata，不执行建表 SQL。

表设计完成后，不管简单或复杂都生成完整基础后端；先保存 SQL 的情况待实际建表后读取 metadata 生成，不支持结构明确报告并完善生成机制。简单业务使用生成 CRUD；纯数据访问可直接调用生成的 Mapper。即使仅操作单表，只要包含业务校验、状态转换、计算或副作用，就由对应业务 Service 封装；聚合 Service 调用这些业务能力编排流程、协调结果及事务，不能直接改表绕过规则。不强制经过生成 CRUD Service，不增加纯转发包装或循环依赖。需要真实状态规则时引入小领域；当前不自动生成主子表协调，不能用生成编辑入口绕过状态规则。规则只描述职责，不代表项目已有特定业务模块。

前端按需要生成或暂不使用。只生成后端通常配置 selection.artifacts: [persistence, backend, test]；至少包含 persistence 与 backend，单独 backend 不含 Mapper/XML/Entity/Example。需要前端/菜单时增加 frontend/menu 或使用 [all]，不因不生成页面而省略后端。

Extra 使用规则：单表操作必须复用生成的 Mapper/XML/Example，禁止另建 ExtraMapper/Extra XML 或以 DAO/Repository 改名重复实现。单表统计、批量、复杂条件及状态规则不属于例外，生成能力缺失修补通用生成机制和测试。仅跨表关联查询（JOIN 或涉及其他表的关联子查询）允许自定义 Extra，不能把单表 CRUD 放进去；业务 Service 分别调用多个生成 Mapper 不需要 Extra。当前生成器不自动生成跨表 Extra。下方历史“复杂业务新增 ExtraMapper”及“标准单表能力无法表达可用 Extra”的表述均由此规则替代。

从后端仓库根调用：

```sh
mvn -B -ntp verify
./scripts/mes-business-codegen docs/development/codegen-config.review.yaml target/business-codegen/report.json
```

命令第二个参数只是报告位置。运行前读取配置的实际 execution；用户当前配置可能是 write/replace-generated，不得把名为 preview.json 的报告当成预览开关。

| 配置 | 含义 |
| --- | --- |
| selection.tables: [] | 所选库全部表；新功能只需目标表时明确填列表 |
| selection.tables: [pro_workorder] | 仅选该表；tableOverrides 不参与选表 |
| execution.mode: dry-run | 渲染和预检，不发布代码；仍写报告，仍检查文件冲突 |
| write + fail | 生成新文件，相同内容保留，不同内容拒绝 |
| write + replace-generated | 覆盖带生成标记的文件，手工修改也会被替换 |
| 独立 target 输出配置副本 | 写可审查副本，保留原项目文件及用户配置 |

所有项目取值以用户指定配置为准；完整参数与注释见 [配置文件](codegen-config.review.yaml)，不能从旧 CLI 隐含默认值补齐。模块取命名策略和 fallback，无 `_` 本项目 fallback 为 business，可配置。持久化/backend/frontend/menu/test 可独立选择，backend 不包含 persistence。缺项、不支持表或冲突时全批预检失败，不发布半套文件。

菜单 SQL 更新已有、插入缺失，保留菜单 ID 和角色绑定；只生成，不自动执行、赋权或重启服务。生成后的工程与业务验收见 AGENTS 第 10/14/15 节；真实生成器入口实现及证据见本文末尾“mes-business 配置读取与批量调用入口”。

本次整理仅更新 AGENTS、Skill 与本说明，没有修改生成器、业务代码或用户配置，没有执行建表/生成/服务重启。以下时间线保留当时事实，历史默认值、INSERT 和 Factory 字段描述不作为当前规则；以本节和 AGENTS 当前约定为准。

## 历史入口及实现时间线

2026-10-10 入口：API 为 `MesCodegenService.generate(CodegenRequest)`；开发入口为 `scripts/mes-codegen`。旧脚本仅在 JAR 缺失或主源码更新时调用标准 Maven package；当前新脚本需先构建。

## 数据源与命令
默认配置 `mes.codegen.default-datasource=dev`，dev/prod的实际url/username/password直接写入对应application profile的mes.codegen.profiles字段，不再用MES_CODEGEN_*占位符。外部配置可声明多个 `mes.codegen.profiles.<name>.url/username/password/driver-class-name`；`--config=/absolute/developer.properties` 加载，凭证仅环境/受控配置，禁止命令或报告打印。开发 profile 与运行时 Factory 无关。

```sh
./scripts/mes-codegen --datasource=business-dev --schema=PUBLIC --table=md_example \
  --module=masterdata --business-name='示例配置' --scope=FACTORY_SCOPED --dry-run
```

确认 JSON metadata/manifest 后删除 --dry-run。默认后端仓库根为 cwd（必须 Base），后端产物写入其下 `mes-business`，前端根为相邻 mes-base-ui；`--output` / `--frontend-root` 指定绝对验收目录；`--backend-module=mes-business` 指定目标 Maven 模块目录，`--package=dev.mesbase.modules.masterdata` 指定 Java 包，`--author` 可选。`--module` 是功能模块标识，独立于上述目录与包名；可省略，默认取表名第一个 `_` 前的部分，没有 `_` 时用 `business`，显式值优先。默认 scope 为 FACTORY_SCOPED；GLOBAL 必须显式选择。GLOBAL package 必须 dev.mesbase.platform.*，FACTORY_SCOPED 必须 dev.mesbase.modules.*；所有名称/路径被检查。`--overwrite` 只替换带生成标识的旧文件，不能覆盖无标识用户文件，但会替换带标识的后处理代码，使用前须审查工作区。

stdout 仅安全 JSON，stderr 为稳定错误码，失败 exit2；UNKNOWN_DATASOURCE / DATASOURCE_UNAVAILABLE / TABLE_NOT_FOUND / UNSUPPORTED_SCHEMA / OUTPUT_CONFLICT / OUTPUT_FAILURE 区分。额外开发 JDBC driver 可用 MES_CODEGEN_DRIVER_CLASSPATH；它不是运行时库绑定。生产 JAR 不带 H2。

## Metadata 与产物
只用 JDBC DatabaseMetaData 的 tables/columns/PK/index/importedKeys；getIndexInfo 使用 approximate=true，不运行应用业务 SQL、不读取数据行。类型映射 Long/Integer/String/BigDecimal/LocalDateTime/LocalDate/Boolean；记录 nullable/default/comment/index/unique。单主键 Long/Integer/String，无外键；业务库按当前 Factory 路由隔离，不要求 factory_id 或复合 unique。不按字段名推断敏感、主体或引用业务并拒绝生成；响应字段及授权由使用方审查。复合 PK、未知类型、外键等超出当前结构支持的表明确拒绝。

GLOBAL / FACTORY_SCOPED 20 文件：MBG Entity、Example/Criteria、Mapper、XML，以及 Service、Impl、Controller、Query/Create/Update DTO、业务 Page、VO、PageVO、Permission、Test 骨架、API TS、Vue、菜单 JSON、permission Markdown、中心库菜单 INSERT SQL。GLOBAL 通过固定 platform 包自动扫描，FACTORY_SCOPED 通过固定 modules 包自动扫描，Mapper 不生成 PersistenceOwner 注解，不额外生成 Mapper 注册配置。Bigint/decimal 响应字符串防前端精度丢失；状态仅原值，需要 Agent 依据真实语义抽取枚举与 options。

按两根目录预检冲突/路径/符号链接 → 临时渲染 → 发布 Backend/Frontend → 哈希 manifest → 清理。普通失败回滚本次发布；进程崩溃不能保证两个仓库跨目录原子发布，需要依据 manifest 复核。Dry-run 不创建输出。

## 接入与后处理
基础生成不注入 Factory 字段、方法参数或 SQL 谓词；真实 Schema 自有字段按普通字段处理。Factory 路由、缺 Scope 拒绝和事务内切厂保护由基础设施负责。Service 构造注入 Mapper，使用具名 Spring 事务：GLOBAL 为 masterTransactionManager，FACTORY_SCOPED 为 businessTransactionManager。读方法 readOnly=true，写方法 rollbackFor=Exception.class；生成 Mapper 按固定 platform/modules 包自动扫描，工具不建生产连接/RuntimeBinding。GLOBAL 配置仅在 Platform enabled 时注册，仍需平台 Permission，不能作为厂业务绕过。

生成权限 `<承载模块标识>:<完整表名>:read/create/update/delete`（mes-business 对应 business；不截取表名前缀），生成 `src/menus/<module>/<entity>.json` 菜单描述与 `docs/generated/<module>/<entity>-menu.sql` INSERT SQL，不自动执行或授予角色权限。菜单对应 `/module/lowerCamelEntity` 的 Base UI 编译视图；动态 Router 只从编译 catalog 选择。审核 DTO/VO、输入/状态规则、唯一性/事务、资源 ID/关联范围、SQL/索引、UI、测试后才能交付。标准单表复用生成 Mapper；复杂业务新增 ExtraMapper。引用、聚合、cache/import/export 未生成，不能声称已实现隔离。

## 当前验收入口（2026-10-06更新）

`mvn -B -ntp verify` 保留CodegenAcceptanceTest的metadata/生成产物/冲突/范围等当前测试。旧 `scripts/verify-codegen-fixture` / `scripts/verify-codegen-http` 与TestOnlyCodegenFixture依赖已退役Platform API、旧单模块classpath与旧UI页面，现已删除。旧运行记录仅作历史证据，不能作为当前命令或当前端到端验收结论。

真实MySQL metadata/runtime测试仍须显式、独立、可销毁的双厂测试配置；不会默认对日常开发库建探针表。新开发库的业务schema为空，本次未生成新的CRUD，也未调用Codegen Skill。

### YAML profile入口

应用与CLI共享application.yml及标准dev/prod profiles，默认读取打包的对应资源配置；--config可作为显式额外配置使用。兼容已有properties开发文件，不强迫用户转换；仓库主配置与本轮受控连接已使用YAML。CLI仍独立于Factory Runtime，启动Codegen不创建池绑定或自动授予菜单/角色。


## person 前端验收（2026-10-10）

以下段落记录此前验收版本，新生成位置与范围见文末“MBG 基础生成版本”。这是用户指定的 TEST ONLY mock：H2 表 `person(id BIGINT IDENTITY PRIMARY KEY, name VARCHAR(64) NOT NULL)`，仅两个字段，使用 GLOBAL，避免为了演示新增 factory_id。测试通过 JDBC metadata 调用真实 CodegenCli，产物输出到后端 `target/codegen-person/backend` 和相邻 UI `target/codegen-person`，不进入生产源码、数据库或路由。

在 Base 根目录生成：

```sh
mvn -B -ntp -Dtest=CodegenPersonAcceptanceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

在 mes-base-ui 使用 `.nvmrc` 对应 Node，打开独立验收页：

```sh
npm run codegen:preview
```

地址 `http://127.0.0.1:18190/acceptance/person`。验收入口直接导入生成的 Vue、API 和菜单描述，复用正式 Shell、CrudPage、查询栏、工具栏、编号与分页组件。内存接口支持搜索、分页、增改删，刷新页面恢复 26 条合成记录；底部控制可检查只读权限、请求失败与空列表。编号示例超过 JS 安全整数范围，用字符串保持精度。业务数据库 HTTP CRUD 未在此 mock 页面验收。

专用类型和构建检查：

```sh
npx vue-tsc --noEmit -p tests/codegen-person/tsconfig.json
npm run codegen:preview:build
```

此轮修复 Velocity Map 的 `size` 属性冲突，字段长度改用 `maxLength`，确保 name 生成 64 字符限制。生产接入仍须审查菜单持久化、编译路由 catalog 与角色授权。


### person 源码审查目录（2026-10-10）

用户要求将生成产物放进前后端项目审查代码风格。通过真实 CLI 将后端发布到 `mes-admin/src/main/java/dev/mesbase/platform/acceptance`、Mapper XML 到同模块 resources、骨架测试到同模块 test；前端发布到 `src/views/acceptance/person/index.vue`、`src/api/acceptance/person.ts` 和 `src/menus/acceptance/person.json`。生成后 Java 使用项目原生 IDEA formatter。

仅为源码审查样例：PersonController / PersonServiceImpl 增加 `mes.codegen.person-review.enabled=true` 显式开关，默认缺省关闭，不创建 person 表或生产角色/菜单。UI 的独立验收入口改为加载这些 src 产物，正式 router 不注册此 Mock 页面。`CodegenPersonAcceptanceTest` 仍向 target 生成新的原始产物，避免测试覆盖经审查源码；生成模板与审查副本的格式或边界改动应分别复核。


### 生成模板工程规范整改（2026-10-10，用户继续审查）

确认并修正：原模板生成压缩 Java 字符串、回调式 persistence.transaction、Service 直接返回 HTTP VO、GLOBAL DAO 冗余 factory 参数、所有 Java 文件挤在同一包。此轮修改生成器与命名模板后重新生成 person，采用模块根包下 controller / controller.vo / service / service.impl / dao / domain；不新增 Domain Service 或 Repository 层。

Service 构造注入 MyBatis-Spring Mapper，类可被 Spring 代理，读写均明确事务管理器。Service 返回 Entity / 业务 Page；Controller 显式转换 VO，维持 items/total/offset/limit、ID/decimal 字符串的既有前端契约。GLOBAL Mapper 不生成 factory 参数；厂 Mapper 保留 factory_id 谓词，Service 验证显式 Scope 等于当前 Scope。旧平铺 person Java/XML 已删除，骨架测试迁移到新包结构。

后端结构由可读 Velocity 模板生成，一条逻辑语句一行，显式 import，无共享 Service→Web VO。前端 API 和 fields/props 模板展开为可读多行，继续复用 CrudPage / request / permission。未在此轮重写通用 CrudPage 的完整实现，也未将表驱动脚手架宣称为完整领域模型。

稳定 CLI 在暂存 Java 上调用仓库 scripts/format-java（原生 IDEA + Project.xml），成功后才发布并计算 manifest 哈希；格式化失败明确 TEMPLATE_FAILURE，不发布半成品。Dry-run 同样格式化暂存内容以保证哈希契约，不创建产物目录。稳定 scripts/mes-codegen 自动传递 --format-java；直接 Java API / CodegenCli.run 的合同测试不依赖本机 IDEA，确保 Ubuntu CI 的 verify 可运行。需要与 IDEA 完全一致的交付以稳定脚本为入口，或显式给 CodegenCli 传 --format-java。

定向验收已替换旧 Persistence 直调测试：真实编译新包结构、通过 Spring AOP 代理与 SqlSessionTemplate 执行 GLOBAL/FACTORY CRUD；覆盖完整写入后读失败回滚、Scope 不匹配、缺 Scope、外厂资源 ID、两个 Factory 独立数据库、业务字段与 HTTP 字符串转换。H2 Fixture 是隔离测试，真实 MySQL/业务库验收仍未运行。Person 源码审查副本继续保留默认关闭的 TEST ONLY 开关，不建正式表或授予角色权限。

### Mapper / Provider 设计比较评审（2026-10-10，尚未实施）

用户提供 ItemMapper（ItemExample / ItemSqlProvider 与内联 SQL 混用）及 IamAccountDAO extends IamAccountMapper 示例，要求先评审，明确不照搬注解中的 SQL。此节记录源码静态事实与候选方向，不表示已批准更换 Mapper 架构。

当前 mapper.java.vm / mapper.xml.vm 为每表生成固定 list/count/detail/insert/update/delete；查询按可编辑字段生成字符串 LIKE 或其他类型相等，条件仅 AND，排序固定主键，更新全部可编辑字段。优点是列、参数及 Factory 谓词明确；不足是范围、IN、NULL、OR 与受控排序表达弱，生成与自定义 SQL 缺少清晰文件边界。Person XML 的 resultMap 子元素缩进也仍有缺陷。service-impl.java.vm 另行计算 offset/limit 与 count，项目 PersistenceFactories / PageUtils / 系统 Controller 已采用 PageHelper；当前生成分页应纳入后续整改，不能将第二套分页当作默认长期惯例。

示例值得借鉴的是字段条件对象、明确的标准 CRUD 方法语义及生成基础文件与定制查询的分离。Provider 是 SQL 生成机制，Example 是条件模型，不等同于跨实体通用 BaseMapper。附件没有 ItemSqlProvider / ItemExample 实现，尚不能确认 SQL 参数化、排序白名单、空条件写入保护或分页上限。

示例中不应复制的内容：

- listByWhere 使用 #{where} / #{orderby} 绑定完整谓词和排序表达式；绑定值不能代替 SQL 语法。这种写法不能实现其命名所表达的任意条件/排序，不可通过直接改为接收用户原始 ${} 字符串修复。
- deleteByPrimaryKey 被改写为 status=-1，隐藏逻辑删除语义，与 Base 新 CRUD 物理删除约定不符。
- Item 全量更新包含 shop_id / gmt_create；若字段承担归属或创建审计语义，不能作为普通可编辑字段。应按真实字段契约制定更新白名单。
- updateByExample 的空条件写入保护未知；必须显式防止意外全表更新。Factory 条件必须位于用户 OR 条件组之外，不能由调用方自由组合或覆盖。
- Selective 更新通常跳过 null，不能直接当作允许显式清空字段的 PATCH；写入应区分未提供、赋值、清空。
- DAO 名称不能照搬：Base 固定扫描 *Mapper，自定义查询优先使用 ExtraMapper 与独立 SQL 文件；锁查询按实际用例放在具名 Spring 事务内，不作为所有 CRUD 默认能力。

建议保留现有 MyBatis-Spring、两个 SessionFactory、具名 Spring 事务与包扫描边界。增强字段类型明确的条件表达、受控排序和生成/定制文件边界；复杂查询继续 ExtraMapper。若选择原生 Provider，可用 @SelectProvider 等仅绑定 Provider，SQL 正文放独立 Provider；若 Mapper 完全不接受 SQL 绑定注解，则采用 XML + 类型条件对象。两者都能实现更丰富条件，不能将 SQL 表达能力归因于注解。抽取共享 SQL 构造逻辑应以真实重复能力为依据，不预建任意表名、字段名或原始 SQL 入口。

后续验收除 person 外需覆盖 Factory 表，验证分页契约、OR 分组、IN/NULL/范围、排序拒绝、空条件写入拒绝、显式清空及跨厂 ID 操作。此轮未修改业务代码或生成模板，未运行新实现测试、真实数据库或性能验收。

机制参考：[MyBatis SQL Provider](https://mybatis.org/mybatis-3/java-api.html)、[MyBatis Generator Example 条件](https://mybatis.org/generator/generatedobjects/exampleClassUsage.html)、[MyBatis 参数绑定与字符串替换](https://mybatis.org/mybatis-3/sqlmap-xml.html)。

#### 用户明确实现方向（2026-10-10）

用户明确采用 Mapper 接口 + XML，不采用 SQL Provider 注解绑定或注解中的 SQL；附件仅用于借鉴条件构造与 CRUD 复用思路。上述 Provider 候选不再作为此次改造方向。后续条件对象只携带结构化条件与参数，XML 使用 if / choose / foreach / where / set 及 sql / include 表达和复用 SQL；不引入原始 where / orderby 字符串入口。MyBatis 原生 SQL Builder 构造的是 SQL 字符串，Provider 也不等于自动生成 Mapper XML 文件；动态脚本可能交由 XML 语言驱动解析，不改变本次明确的 Mapper + XML 选型。本次仅更新评审记录，尚未实施生成器改造。

#### 用户进一步澄清生成能力范围（2026-10-10）

用户的核心目标是生成完整、通用的单表持久化能力，在单表范围内尽量减少 ExtraMapper，而不只是将条件查询转移到 ExtraMapper。前述优先分出定制查询的建议范围过宽，应以本节澄清为准。完整单表访问能力与跨实体运行时通用 BaseMapper 是不同问题；本次优先解决前者。

MyBatis Generator 的 MyBatis3 / XMLMAPPER 路线已有 Entity、Example / Criteria、Mapper 与 XML 配套生成机制，应优先评估直接复用 MBG 并通过插件适配现有生成流水线，而非另造等价条件框架。现有 RuoyiTemplateRenderer 是 Velocity 模板实现，本轮未查见 generator 模块依赖 MBG；接入版本、Metadata 输入、类型映射、文件发布/重复生成保护和 IDEA 格式化仍须验证，不声明已接入。

能力候选分为：MBG 标准主键及条件查询/count、insert/insertSelective、主键及条件 update/selective、物理 delete；单表扩展的 exists、主键集合查询/删除、与现有 PageHelper 协同的列表查询、主键加锁查询。锁方法需按实际数据库方言启用并在 Service 具名事务中调用；不是 MBG 自动具备的标准接口。集合输入需有边界和空集合语义。条件写入需拒绝空业务条件，Factory 谓词不能当作已提供业务条件而允许误操作整厂数据。

单表通用扩展可生成到同表 Mapper/XML 中，不要求每表新增空 DAO 或 ExtraMapper；若保留 DAO/Mapper 区分，区分依据是生成职责，而非机械增加转发层，仍须遵守生产 *Mapper 扫描及避免重复注册。ExtraMapper 留给跨表查询、业务专用投影/复杂统计及标准单表能力确实无法表达的 SQL。

拟优先复用 MBG 插件补充 Factory 强制条件、受控排序、条件写入保护、不可修改字段和常用单表扩展，保持业务规则、权限与事务在 Service。此节是需求与候选范围评审，未改生成器实现；参见 [MBG 生成 XML](https://mybatis.org/generator/generatedobjects/sqlmap.html)、[Mapper 方法](https://mybatis.org/generator/generatedobjects/javaclient.html)、[插件机制](https://mybatis.org/generator/reference/pluggingIn.html)。


### MBG 基础生成版本（2026-10-10，取代前述持久化候选与旧样例）

依据用户最终要求仅做基础生成。采用 MBG 1.4.2 的 MyBatis3 / XMLMAPPER 原生生成器，向其传入既有 JDBC Metadata，经命名插件输出 Entity、Example、Mapper 与 XML；不另造查询框架，不采用 Provider/注解 SQL。只读 Metadata 入口、两仓库冲突预检、暂存/发布与 IDEA 格式化保持现有链路。MBG 默认标准能力包含主键及条件 CRUD、count、Selective、IN/NULL/范围/OR；不增加锁查询、exists/集合批量 API 或业务策略。Example 的 orderByClause 与条件属于可信服务端构造，不可直接接收前端原始 SQL；标准条件更新/删除不会额外自动保护空条件，业务调用方需明确控制范围。

Service 使用 selectPage/selectById/insert/updateById/deleteById，对应 selectByExample/selectByPrimaryKey/insert/updateByPrimaryKey/deleteByPrimaryKey。查询 DTO 转换为 Example，分页使用 PageHelper.offsetPage（适配既有 HTTP offset/limit），总数由 PageHelper 获取；Controller 同步调用并显式转换响应，继续 items/total/offset/limit 与 ID 字符串契约。普通 PUT 全量更新保留明确 null；Selective 仅是持久化可选能力，不直接冒充 PATCH。

默认后端模块 `mes-business` 已加入 reactor 与 mes-admin 依赖。位置示例：

```sh
# 默认 mes-business + dev.mesbase.modules.masterdata，业务事务及厂权限范围
./scripts/mes-codegen --table=person --module=masterdata --business-name=人员 --dry-run
# 将基础代码放入另一个已接入构建的模块及包；不会装配第三方数据库
./scripts/mes-codegen --table=person --module=masterdata --business-name=人员 \
  --backend-module=mes-erp --package=dev.mesbase.modules.erp.masterdata --dry-run
# GLOBAL 仍按中心库包、事务和权限归属，可显式指定承载模块
./scripts/mes-codegen --table=person --module=acceptance --business-name=人员 \
  --scope=GLOBAL --backend-module=mes-admin --package=dev.mesbase.platform.acceptance --dry-run
```

`--output` 是仓库根，`--backend-module` 是其下单一模块目录名（拒绝穿越与符号链接），`--frontend-root` 独立设置 UI 根。自定义模块/包不等于自动新增 reactor/dependency 或连接外部数据库；业务包保留 modules 扫描根，中心包保留 platform 扫描根。

新版 person 来源为原两字段 H2 Metadata fixture，后端审查文件位于 mes-business/src/main/java/dev/mesbase/modules/acceptance，XML 在同模块 resources 对应 dao 包，骨架测试在同模块 src/test。旧 mes-admin 的 platform acceptance 样例撤除，避免保留两套旧入口。PersonController / PersonServiceImpl 的审查副本仍以 mes.codegen.person-review.enabled=true 默认关闭；该开关是样例后处理，不属于普通模板。未创建生产 person 表或授予角色/菜单。前端 src/api/views/menus 的对应文件重新生成，独立 Mock 验收切换为合成厂权限上下文，仍使用内存数据。

本轮验证：

- 真实稳定 CLI 从两字段 H2 metadata 生成并原生 IDEA 格式化 19 份产物，发布时核对 manifest SHA-256；仅 Controller/Service 样例增加默认关闭开关。
- Maven 定向测试及最终 `mvn -B -ntp verify` 通过；报告合计 190 项、0 failure/error、4 项真实数据库测试按既有条件跳过。`mvn -B -ntp clean verify` 清除旧 XML 构建残留后通过，后续长文本方法适配再次 verify 通过。
- 生成代码执行验收覆盖标准 Example OR/IN/LIKE 与参数绑定、Selective 保留未提供字段、分页总数及清理、两个 Factory 独立库相同 ID、缺 Scope 拒绝、写入后失败回滚和显式目标模块/包。补充 LONGVARCHAR 使用 MBG WithBLOBs 标准方法的生成 Java 编译验证；不将该编译测试称为真实长文本数据库执行。
- 新业务模块加入后，装配测试替换“业务 Mapper 集合为空”的废弃断言，验证 PersonMapper 实际只装配到 businessSqlSessionFactory；继续验证两个 SqlSessionFactory/DataSource/TransactionManager 的确切名称与数量。
- 原生 IDEA formatter 检查 25 个受影响 Java 文件通过；最后调整的两个 Java 文件再次检查通过。两仓库 `git diff --check` 通过。Skill `quick_validate.py` 通过（验证器 PyYAML 在 /private/tmp 隔离环境安装，未改项目 Python/Maven 环境）。
- UI 使用已安装 Node 22.23.1：`npm run test` 84 项通过，build、codegen:preview:build、lint 均通过；构建保留既有 chunk size 提示。浏览器 18190 Mock 验证新增、查询、编辑、确认删除，恢复为原 26 条记录；截图在相邻 UI target/codegen-person-preview/person-mbg.png。
- 最终打包 `scripts/smoke-packaged.py` 10 passed / 0 failed；打包文件不再含旧 platform acceptance XML。

证据边界：真实 MySQL 多厂 CRUD 未验收；浏览器仍是内存 Mock，不创建生产 person 表或菜单授权，不接入 ERP。回退应按本轮生成器/模板、模块接入和样例变更逐项处理，保留其他已有未提交工作，不整仓重置。

### 2026-10-10：SQL 标识符与库名解耦

JDBC metadata 的 catalog/schema 仅用于准确定位生成来源，不写入运行 SQL。MBG 的 ignoreQualifiersAtRuntime 开启，生成 `from person`，不生成 `"public"."person"`；连接选定当前库/Schema。更换连接库名无需重新生成；运行连接仍须指向对应表所在的库/Schema。

取消全表、全列强制引用。普通名称 `person`、`id`、`name` 原样生成；通用 SQL 关键字及 JDBC 驱动报告的关键字使用 metadata 引用符（MySQL 反引号、H2 双引号）。同一规则用于 XML、Example 条件与 Service 排序，用户输入仍参数绑定。当前 identifier 校验不接受空格等特殊字符，未扩展特殊命名 Schema 的支持范围。

生成器与 person 审查副本（XML、Example、Service）同步更新，保留样例默认关闭开关。验证以本轮 Maven 测试、IDEA formatter 与 diff 检查为准；H2 fixture 不代表真实 MySQL 验收。

本轮实际检查：修改 Java 的 IDEA formatter 通过；重新生成审查文件后 `mvn -B -ntp test` BUILD SUCCESS，0 failures/errors，4 项真实数据库测试按既有条件跳过；`git diff --check` 通过。新增回归断言覆盖无 Schema 前缀、普通名称不引用、保留字在 XML/Example/排序中的一致引用；已有 H2 生成代码 CRUD/分页/隔离/回滚执行测试继续通过。未执行真实 MySQL 或部署验收。

### 2026-10-10：完整表名权限与菜单 INSERT SQL

权限改为 `<承载模块标识>:<完整表名>:操作`，操作固定 read/create/update/delete。mes-business → business，mes-erp → erp；不以 acceptance 等 `--module` 页面分组作为权限前缀。表名完整保留，例如 business:md_person:read，不缩为 business:person:read；也不包含物理库名或 Schema。Java/HTTP 权限、Vue CrudPage、菜单 JSON 和 SQL 同步生成。

新增第 20 份产物 `docs/generated/<module>/<tableCamel>-menu.sql`：中心库 sys_menu 目录 M、页面 C、四操作 F，页面带 read 权限，查询按钮也使用 read。ID 由数据库生成，通过目录 path/页面权限查父节点；NOT EXISTS 支持串行重跑。SQL 独立供审查执行，不进入 Flyway、不自动写库、不包含角色绑定。现有目录重名、页面权限重复及并发执行须由执行者审查；本次只生成文件。

本轮验证：稳定 `./scripts/mes-codegen` 从 person 两字段 H2 metadata 重新生成 20 份项目产物，逐文件 SHA-256 与 manifest 一致；Controller/Service 保留默认关闭的审查开关。菜单 SQL 在从 V2 Source Product 迁移文件提取的真实 sys_menu DDL 上进行 H2 MySQL 模式执行，串行执行两次均维持 6 行/4 个不同权限，按钮父节点正确。定向断言覆盖 mes-erp 自定义承载模块及 gen_demo_config 完整表名。

Java IDEA formatter、`mvn -B -ntp verify`、两仓库 diff 检查通过；packaged smoke 10 passed/0 failed。UI 使用 Node 22.23.1：84 项测试、build、codegen:preview:build、lint 通过。Skill quick_validate 通过。修正真实 MySQL 可选验收中的旧产物数量和强制引号断言，但这轮 4 项真实数据库测试按既有配置条件跳过；未执行生产菜单 SQL、角色授权或真实 MySQL 验收。

### 2026-10-10 统一集合删除与 CrudPage 权限

生成器仍生成 read/create/update/delete 四个权限。查询、新增、修改分别绑定 read/create/update；单删、批删共用 delete。CrudPage 不再接受 write 通配授权，没有 read 时不请求列表；权限变更立即刷新按钮、撤销 read 后清空数据。批删仅在 API 有 delids 且具有 delete 权限时显示，表格提供当前页勾选，空选择禁用。

Controller 删除统一为 DELETE /api/<module>/<entity>，请求体为 ID 数组；不再生成旧 DELETE /{id}、独立 /delids 或 deleteById Service。前端 remove(id) 包装单元素数组，delids(ids) 使用相同 removeIds 函数。Service deleteByIds 在具名 Spring 事务中构造 Example 主键 IN，调用 MBG deleteByExample；拒绝空集合、null/重复 ID 和超过 100 条，影响行数与请求数不符抛冲突异常回滚。SQL 参数化，不循环单删，不增加 ExtraMapper。

通过 Skill/稳定 CLI 从既有两字段 H2 metadata dry-run 后重新生成 person 到 mes-business 及相邻 UI；审查副本 Controller/Service 恢复原默认关闭开关。菜单 SQL 仍为四个按钮权限，不新增 delids 授权或执行中心库 SQL。

验证：Maven verify 通过；生成 H2/Spring 测试覆盖单元素、多元素删除，空/重复/null/超限拒绝及缺失 ID 导致整批回滚；菜单 SQL 重复执行保持六行/四个权限。打包 smoke 10 项通过。UI 20 文件/101 项通过，正式 build 和 codegen:preview:build 通过。真实浏览器 18191 为 Mock：批删两条 26→24，单删 24→23，只读切换隐藏全部写按钮，随后刷新恢复 26 条。18190 旧服务缓存代码，未重启用户服务；真实业务 HTTP 删除未验证。全库 lint 被 auth.ts 既有九项样式错误阻断，本轮相关文件定向 lint 通过。

### 2026-10-10 pro_workorder 真实开发库生成检查

用户要求对 mes_base_dev_business 新建的 pro_workorder 调用 Skill 检查产物。执行 ./scripts/mes-codegen --datasource=dev --table=pro_workorder --module=pro --business-name=生产工单 --dry-run；返回 UNSUPPORTED_SCHEMA，未发布任何生成文件。

只读 JDBC metadata 已确认连接 catalog 为 mes_base_dev_business，表有 34 列、单主键 workorder_id（BIGINT），没有数据库外键。未查询业务行、未修改表、未执行菜单 SQL。metadata 诊断留在 target/codegen-workorder-review/metadata.txt，未记录连接凭证。

当前第一阻断：JdbcMetadataReader.javaType 未支持 JDBC DOUBLE（8），quantity、quantity_produced、quantity_changed、quantity_scheduled 四列均为 DOUBLE。后续阻断：DefaultMesCodegenService 对非主键 *_id 一律拒绝（factory_id 除外），product_id、client_id、parent_id、mo_id 会被拒绝；即使数据库无外键也会拦截。该表的单主键、VARCHAR、BIGINT、INT、DATETIME 均在现有支持范围。

结论：当前生成器无法为该表生成基础 CRUD，未达到用户对真实单表脚手架的预期。本轮仅执行生成与定位，未调整生成器支持范围或篡改 Schema。扩展应明确浮点请求/响应精度契约、普通关联 ID 字段的生成边界；不能把客户/产品选择、父工单树、ERP 关联或单据状态流程自动声称为已实现。普通 CRUD 与实际生产工单业务仍需区分。

### 2026-10-10 pro_workorder 支持扩展与真实生成

用户批准扩展后，JdbcMetadataReader 支持 DOUBLE/FLOAT→Double、REAL→Float；保留数据库字段类型，不转换成 DECIMAL。取消非主键 *_id 后缀拒绝，仍拒绝敏感/所有者等明确特殊字段及真实外键，普通关联 ID 不自动生成关联查询或业务校验。BIGINT 响应继续用字符串；浮点响应为 number，UI 数值输入，沿用实际浮点精度。

生成字段增加 searchable 元数据，默认 String 的 code/name 后缀可查询，CrudPage 使用此标记，兼容未声明标记的旧 code/name 页面。工单编码、名称、来源单据、产品/客户编码名称、批次号均出现在查询栏；后端 QueryDto、Example 与页面保持一致。

create_by/create_time/update_by/update_time 只读展示，不接受写入 Command，也不自动赋值。更新先读取现有 Entity 再覆盖允许写入的业务字段，保留审计字段；不增加状态流程、树操作、ERP 调用或产品/客户选择。生成的业务 Controller/Service 使用与持久化装配一致的 mes.system.enabled=true 开关，修正关闭持久化时生成 Bean 仍注入 Mapper 的问题；测试 fixture 显式打开对应装配标识。

命令：./scripts/mes-codegen --datasource=dev --table=pro_workorder --module=pro --business-name=生产工单，先 dry-run，修正模板后对本轮生成文件使用 --overwrite 再生成。真实 catalog=mes_base_dev_business，34 列，20 个产物；后端 mes-business/src/main/java/dev/mesbase/modules/pro、XML 同模块 resources 对应 dao 包，前端 src/views/pro/proWorkorder/index.vue、src/api/pro/proWorkorder.ts、src/menus/pro/proWorkorder.json。菜单 INSERT SQL 在 mes-business/docs/generated/pro/proWorkorder-menu.sql；权限 business:pro_workorder:read/create/update/delete；页面 /pro/proWorkorder，API /api/pro/pro-workorder。未执行菜单 SQL、授予角色或修改真实表/数据。

验证结果：修改的 Java 已使用仓库 IDEA formatter 格式化；mvn -B -ntp verify 为 BUILD SUCCESS，scripts/smoke-packaged.py 为 passed=10/failed=0。前端测试 20 个文件、102 项通过，build 通过；CrudPage、字段类型、相关测试及新生成页面/API 的定向 ESLint 通过。两个仓库 git diff --check 通过。最终 20 个产物的 SHA256 与生成清单一致，未手改工单示例。

页面结构已在独立 TEST ONLY 预览 http://127.0.0.1:18192/ 检查列表和新增表单：使用真实生成页面/CrudPage，列表固定为空、写请求拒绝，不访问业务数据。截图在 UI docs/acceptance/ui-industrial-upgrade/workorder-generated-list.png 和 workorder-generated-form.png。未重启用户 5173/8081 服务，未完成真实 CRUD/角色授权验收。

### 2026-10-10 CrudPage 权限范围按菜单路径推导

CrudPage 未传 permissionScope 时复用 UI isPlatformMenu：/system、/tool、/monitor 本身及子路径使用 platform，其余使用 factory，采用路径段边界而不是任意字符串 startsWith。正式页面使用当前路由 URL；无路由的独立组件预览以权限前缀转换路径判定。权限代码本身不改写，仍逐 action 调用 hasPermission，不混用平台/工厂授权列表。显式 permissionScope 保留给确定的范围契约；GLOBAL 生成页仍明确 platform，普通业务生成页取消固定 factory。API 请求的 scope 和后端 EndpointAccess 不由菜单路径改写。

同步更新页面模板及已生成 person/proWorkorder 页面。前端测试 20 文件/107 项通过，build、定向 ESLint 与两仓库 diff --check 通过；Maven CodegenAcceptanceTest 定向测试通过。本轮没有执行菜单 SQL、角色赋权、数据库写入或重启服务，未声称真实角色权限端到端验收。

### 2026-10-10 权限分隔符与模块默认值

用户约定完整表名仍保留，权限中的下划线替换为冒号：pro_workorder → business:pro:workorder:read/create/update/delete；不改物理表名、SQL 列名、类名、API 路径或字段名。--module（不是 --model）变为可选：pro_workorder 默认 pro，md_item_detail 默认 md，无下划线的 person 默认 business；显式 --module/--package 保留优先级。默认 Maven 承载模块仍为 mes-business。

真实生成命令 ./scripts/mes-codegen --datasource=dev --table=pro_workorder --business-name=生产工单 --overwrite，无 --module 仍输出到原 pro 目录。Java 权限、Vue、菜单声明与 SQL 同步更新。菜单 SQL 对含下划线表先更新该表四项旧权限，再执行原 INSERT，保留已有菜单 ID 和角色绑定；未执行真实菜单 SQL/赋权/写业务表。AGENTS 和仓库 codegen Skill 同步新规则。

CrudPage 区分无读取权限与空结果：没有 read 时显示“当前范围没有读取权限”，不渲染空表或发起查询。收到不符合 items/total 的分页契约时显示“列表响应格式无效”，不加入 rows/data 多结构兜底。测试使用实际生成 Workorder 页面、API 与 CrudPage，固定非空 items 验证大整数 ID、工单编号/名称及浮点数量显示，未访问数据库。

浏览器 admin 的当前工厂 DEV 上未取得本页读取权限，旧视图“暂无记录”已替换为明确权限提示。用户确认业务权限分配在平台角色；后端工厂权限查询明确要求 FACTORY + 当前 factory_id，不补充 PLATFORM 权限。角色分配页允许勾选这些业务菜单与实际生效规则不一致。是否扩展平台角色业务授权或限制跨范围分配，等待用户选择，不自动扩展授权边界。

本轮验证：改动 Java 经 IDEA formatter；mvn -B -ntp verify 为 BUILD SUCCESS，packaged smoke 10 项通过；新增旧权限迁移/菜单 ID 保持测试后，CodegenAcceptanceTest 定向测试再次通过。前端 21 文件/109 项通过，build、定向 ESLint 与两仓库 diff --check 通过。最终 20 个生成文件 SHA256 与清单一致。无读取权限截图在 UI docs/acceptance/ui-industrial-upgrade/workorder-permission-denied.png。未重启用户服务，未宣称真实工单 CRUD 验收通过。


### 2026-10-10 基础单表不生成 DISTINCT

按用户约定，基础生成代码不提供 DISTINCT 查询开关。MBG 插件在 Java/XML DOM 生成阶段移除 Example 的 distinct 字段、setDistinct/isDistinct 与 clear 中的复位语句，同时移除普通及 BLOB 列表查询的 `<if test="distinct">`。不改物理表、条件、排序或分页；特殊业务去重由对应业务查询明确实现。

同步移除 person/pro_workorder 的既有生成残留；工单 XML 中用户已删除的 DISTINCT 及其他排版修改保留，不执行整套覆盖再生成。定向测试通过真实 MBG 渲染检查普通及长文本生成产物，并编译生成的 Java；普通查询继续由既有 Mapper 执行测试验收。

验证：5 个改动 Java 文件经 IDEA 原生格式化；生成定向测试 13 项通过，`mvn -B -ntp verify` BUILD SUCCESS，packaged smoke 10 passed / 0 failed，`git diff --check` 通过。本轮未执行数据库写入或重启用户服务。

### 2026-10-11 CLI 必填、默认值与固定约定审查

只读核对 CodegenCli、CodegenRequest、CodegenProfiles、DefaultMesCodegenService、RuoyiTemplateRenderer、MyBatisPersistenceRenderer、scripts/mes-codegen 及实际模板；未生成或修改业务代码，未读取连接凭证或业务数据。

- CLI 必填只有 --table、--business-name。--datasource 可省略，但必须配置有效 mes.codegen.default-datasource 及对应连接 profile；当前公共 application.yml 的默认 profile 名为 dev。不是在生成器中写死物理库名。
- 可选值：--module 默认取首个下划线前缀，无前缀用 business；--scope 默认 FACTORY_SCOPED；--backend-module 默认 mes-business；--package 默认 dev.mesbase.modules.<module> 或 dev.mesbase.platform.<module>；--output 直接 CLI 默认 cwd，但脚本先 cd 后端根；--frontend-root 默认后端根相邻 mes-base-ui；--author 默认 MES Base；--schema 默认未指定；--config 默认未指定。
- 开关：--dry-run、--overwrite、--format-java、--help。默认拒绝覆盖；脚本自动开启 IDEA 格式化。无 --model 参数。CodegenCli 自身 help 把 scope 写成必填外观，与实际默认行为不一致。
- 固定工程耦合：Java 包必须属于 modules/platform 根且与 scope 对应；事务管理器固定 businessTransactionManager/masterTransactionManager；生成接口固定 /api/<module>/<表名下划线转连字符>；权限取承载模块去掉 mes- 前缀＋完整表名下划线转冒号＋四操作；包层次固定 controller/service/service.impl/dao/domain。
- 固定模板约定：分页默认20、最大100；批删最大100且复用 delete 权限；字符串校验上限 min(metadata长度,10000)；菜单 SQL 写中心 sys_menu，顶级 parent_id=0、目录/页面 order_num=0、list 图标、codegen 创建人，并含旧权限名 UPDATE。没有自定义父菜单、图标、API 根或权限覆盖参数。
- 字段策略：create_by/create_time/update_by/update_time 被固定识别为审计字段，不进入普通写入字段；敏感字段名匹配直接拒绝；user_id/user_name/file_id/external_id/owner_id/created_by/updated_by 等固定名单也拒绝整表。后者把资源/业务猜测用于生成准入，应按“纯基础生成器”边界重新评估，不能宣称全部是 JDBC 元数据限制。
- 持久化固定 MBG MyBatis3/XMLMAPPER，单主键仅 Long/Integer/String；有外键 metadata 的表拒绝；不生成 distinct。SQL 不带 catalog/schema，保留字才加标识符引号。表名与字段来自真实 metadata，没有固定 person/pro_workorder 表或 acceptance 模块。
- launcher 固定 target/mes-base-0.1.0-SNAPSHOT.jar，版本或打包名称变化时会失效；这是工具路径硬编码，值得整改。路径/名称校验及不自动装配 Maven 模块仍按当前工具边界处理。

证据边界：本节为静态审查，git diff --check 仅验证文档空白；未执行新的生成、编译、数据库或 UI 验收。

### 2026-10-11 person 审查样例路由与 admin 权限定位

源码审查：`mes-business/src/main/java/dev/mesbase/modules/acceptance/controller/PersonController.java` 声明 `/api/acceptance/person`，Controller 与 `service/impl/PersonServiceImpl.java` 均要求 `mes.codegen.person-review.enabled=true`。当前资源 YAML 未声明该开关；缺省条件不成立，不注册 Controller/Service，因此默认运行时没有对应 MVC 路由。主应用扫描 `dev.mesbase`，mes-admin 已依赖 mes-business，源码中未发现扫描包或模块依赖遗漏。

`No mapping for GET /api/acceptance/person` 表示 MVC 未找到处理器；此时未进入 `FoundationAccessInterceptor.preHandle` 的方法授权判断，不能归因于 admin 缺权限。若通过外部配置启用并重启，该 URL 归业务范围，GET 要求有效 Factory 上下文和 `business:person:read` 权限；登录用户名 admin 不决定路由是否注册，也不替代显式授权。

证据边界：本次只检查源码、配置与已有日志，未读取当前服务的有效 Environment，未复现 HTTP、执行测试、重启服务、启用样例或修改业务代码。外部配置、环境变量或启动参数可能覆盖资源 YAML；文档中的默认关闭结论不能替代当前进程条件评估。该样例来源为 H2 metadata fixture，启用开关不证明目标业务库已有 person 表。


### 2026-10-11 取消字段名业务黑名单

按用户要求删除 DefaultMesCodegenService 的敏感关键词和主体/引用字段名拒绝逻辑；user_id、owner_id、password、secret、access_token 等按真实 metadata 作为普通字段生成。上文 CLI 审查中这两种拒绝策略为整改前事实，本节取代其现行结论。保留类型、单主键、命名冲突及外键等实际能力校验，不改变既有审计字段策略。AGENTS 与 Skill 同步边界。新增合成 H2 表的生成、字段保留及产出 Java 编译回归；未修改业务表或执行真实业务生成。验证：两份 Java 经 IDEA formatter；mvn -B -ntp verify 为 BUILD SUCCESS，CodegenAcceptanceTest 13/13（含真实生成 Java 编译）通过。首轮测试断言忽略 H2 大写 metadata，修正后通过。本次修改文件 diff --check 通过；全仓检查受其他改动 SysUserMapper.xml 第248行缩进空白影响，未修改该文件。

打包 smoke：scripts/smoke-packaged.py 未通过，/open-api/token 返回401，与脚本预期不符；涉及当前其他安全入口改动，未修改相关代码或测试。本次生成器验证通过不代表全仓最终验收完成。

### 2026-10-11 mes-business 配置读取与批量调用入口

（2026-10-12 后续迁移已替代此入口归属，见文末。）入口代码为 mes-business 的 modules/codegen/BusinessCodegenConfiguration、BusinessCodegenRunner、BusinessCodegenCli；只作为开发工具，不注册 HTTP Controller、运行期 Service 或定时任务。mes-business 显式依赖 mes-generator，参数适配调用 ConfiguredCodegenService，不重写 MBG 或通过修改生成文件替代模板。

使用：先 `mvn -B -ntp verify` 构建，然后 `./scripts/mes-business-codegen docs/development/codegen-config.review.yaml target/business-codegen/preview.json`。配置路径、仓库根和报告路径传给独立 PropertiesLauncher 主类；不启动用户服务。脚本按构建实际 JAR 文件查找，不写死版本号；不自动构建，构建最新后执行。

当前新入口读取配置的明确值和推导策略：连接 profile、schema、全量/指定/排除表、指定产物、模块前缀与 fallback、表注释显示名、包与输出布局、模板目录、HTTP 路由、事务、条件注解、权限、审计字段、长度与分页/批删限制、UI 引用、菜单及执行策略。缺必要项报错（包括必须显式声明的 null schema），未支持的策略报错，不静默忽略。四种 CRUD 操作和 MyBatis3/XMLMAPPER 是当前明确支持的生成能力，不声称支持任意动作或持久化框架。

新生成入口不限制 Java 包为 dev.mesbase，测试用 sample.generated 包实际生成并编译；项目模板仍引用 Base 的 EndpointAccess、CrudPage 等应用契约，换项目必须提供适配的模板。旧 CodegenCli 路径保留且仍有历史默认值，不宣称旧入口已经完成配置迁移。

全量使用 JDBC metadata 枚举所选库的 TABLE，不采样业务行；先校验所有表，结构不支持时记录 FAILED_PREFLIGHT，禁止任何表发布。所有产物统一 writer 预检及回滚。dry-run 检查已有文件冲突；冲突不会为了预览而自动切换覆盖策略。后端、前端、菜单、测试与 persistence 可独立筛选；backend 分组是 Service/Controller/请求响应模型，persistence 单独选择，frontend 分组是 API 与页面，menu 分组含菜单 JSON、SQL 和权限说明。

菜单模板调整为 UPDATE 已有＋INSERT 缺失。目录按 parent/path/type，页面与按钮按 permission/type 定位，保留 menu_id 与角色关系。重跑会更新配置决定的名称、路径、component、图标、排序和父级。需串行执行；没有新增唯一约束，不承诺并发重跑的防重效果。菜单 SQL 从不自动在真实中心库执行。

实际开发库预览：原配置 tables=[]、artifacts=[all]、dry-run、fail、无表覆盖；读取到 pro_workorder，模块 pro，显示名来自表注释“生产工单表”，包 dev.mesbase.modules.pro，API /api/pro/pro-workorder，权限 business:pro:workorder。已有生成代码与新产物冲突，报告记录 OUTPUT_CONFLICT，未覆盖原代码。

为代码审查创建 target/business-codegen/isolated-review.yaml，仅将输出改到 target/business-codegen/generated/{backend,frontend} 并改为 write；其他值沿用用户配置。实际生成结果及文件 manifest 在 target/business-codegen/generated-report.json。样例来自真实 metadata，不读取业务数据；生成的 SQL 未执行。

验证：BusinessCodegenRunnerTest 覆盖全量/指定、可配置 fallback/包/开关/分页、产出 Java 编译、缺参数、部分产物、批量失败不发布，以及菜单 SQL 首次新增/再次更新/保持角色绑定。菜单执行证据来自合成 H2；不能当作真实中心库执行证明。验收命令与最后结果追加于下方。

本轮最终验收：任务 Java 文件经 IDEA formatter；`mvn -B -ntp verify` BUILD SUCCESS，BusinessCodegenRunnerTest 5/5（含生成 Java 编译与 H2 菜单重跑）通过；`scripts/smoke-packaged.py` 10/10；`git diff --check` 通过。真实开发连接只读取 metadata，全量为1张表；原配置预检按 fail 策略拒绝已有代码冲突。隔离配置实际生成20个文件；没有执行真实菜单 SQL、角色赋权或重启用户服务。未修改前端运行代码，因此未运行新的 UI 构建；UI 生成模板变化由产物检查覆盖，不宣称真实页面验收。


### 2026-10-12 同步开关与六表覆盖后的最终验证

- `mvn -B -ntp -Dmes.test.redis=true verify`：275 项，271 通过、4 项真实 MySQL 跳过，0 失败/错误；最终日志 `/tmp/mes-sync-final-verify.log`。
- BusinessCodegenRunnerTest：历史缺参/默认 false、true 的七文件/按钮/权限产物、预览与发布清单及哈希一致、false 新产物无引用、旧独立文件未被自动删除、错误匹配键拒绝全批发布、技术表排除均通过。
- CodegenSyncAcceptanceTest：没有 code/name/status/externalId 的真实 H2 表，生成 Java 真正编译；默认 Service 装配启动、未接入异常、无协调/业务写入；实际 Spring 代理的 Writer 验证新增/重复跳过/变化更新/Nullable 清空/中途 SQL 失败整批回滚。
- 现有 Material 的 HTTP/H2 范围、权限、两厂隔离、新增/更新/跳过/回滚测试继续通过；生产 TEST Adapter 与旧 Resolver/Properties 已从打包业务模块中确认不存在，七个 SyncController（手写 Material＋六表生成）存在。
- 覆盖后前端 140 项测试、build 和生成页/API/共享按钮定向 ESLint 通过；日志 `/tmp/mes-sync-regenerated-ui-test.log`、`/tmp/mes-sync-regenerated-ui-build.log`、`/tmp/mes-sync-generated-ui-lint.log`。确认、权限、防重复、成功刷新、取消、未接入真实提示、切厂迟到响应行为均保持。
- IDEA 161 个相关/生成 Java 文件格式检查通过；两仓 diff 检查通过。首次生成运行测试因 macOS 临时目录符号链接被工具安全拒绝，改用真实路径后通过，没有放宽路径保护。
- 最终 packaged smoke 14/14：`/tmp/mes-sync-final-smoke2.log`。首次 smoke 出现 Logback/AOP 类加载异常；检查嵌套依赖确认对应类存在，未改变依赖，原样重跑通过且 JAR 哈希前后相同；未证明首次异常根因，不把它冒充实现修复。
- 真实业务库只读取 metadata，实际外部 ERP、真实 MySQL 写入、运行中 8081 服务的最新代码、菜单 SQL/角色授予和浏览器同步闭环未验收。未重启用户服务、执行真实迁移或业务写入、提交/推送。原有外部 MasterSyncMapper 修改保留，不计入本轮改动。


## 2026-10-12 第一批 SIMPLE：显式范围列与分离查询权限

当前配置仅选择 material_category/unit_measure/workstation/workshop/material/safety_stock，全部 generateSync=true。此前 md/qc/dv/pro 六表覆盖记录是历史生成器试验，不表示这些业务已获本批迁移授权；旧 MdUnitMeasure/MdWorkshop 生成产物已由精简模型替换。

`tableOverrides.<table>.serverScopeColumn` 是可选显式配置；存在时要求实际非空 String 列且 factory 范围，写入与同步输入排除此列，服务端从 FactoryContext 赋值，并生成 scoped Example 与 ID 范围校验。未提供时不自动猜 factory_id。列匹配兼容 JDBC metadata 大小写。

`backend.permission.actions` 支持 `[read, create, update, delete]` 和 `[list, query, create, update, delete]`；本批按用户请求使用后者。generateSync=true 独立追加 sync；false 的新输出没有同步产物。菜单、Controller、Permissions、前端 CrudPage list/detail action 和文档一致。生成 ScaffoldTest 包含独立同步权限和未配置失败合同。

本批人工 CRUD 和同步共用 BasicDataRules；分类树、引用/删除、状态和库存上下限在业务模块后处理，不写入通用模板。Material 外部关系编码→本地 ID 映射同样属于业务后处理。真实执行和边界见 docs/migration/SIMPLE_BUSINESS_MIGRATION_ACCEPTANCE.md。

## 2026-10-12 通用业务缓存模板接入

以已验证的 Material 实现为依据，Factory 范围的基础 CRUD 现在自动接入 `mes-framework` 的通用业务缓存处理器；Global/platform 生成结果不添加业务缓存依赖。此行为属于当前 Base 模板能力，不添加隐藏连接配置或缓存 key 拼接代码。

- 新增 `resource.java.vm`，生成领域侧 `<ClassName>Resource.PREFIX`。取值直接复用最终配置解析后的权限资源前缀（prefix + 完整表名下划线转冒号，不含操作）。如 `material_category` 得到 `business:material:category`，不使用页面 module 名替代资源名。权限、缓存注解及同步淘汰共用该常量，Service 不依赖 Controller。
- `query.java.vm`：业务查询实现 `BusinessCacheQuery`，暴露有效 offset、配置中的 pageDefault 和实际主键排序；原筛选字段全部保留。平台查询不实现该接口。
- `service-impl.java.vm`：业务 selectPage/selectById 添加 LIST/DETAIL 注解；insert 淘汰 LIST，updateById/deleteByIds 淘汰 LIST_AND_DETAIL。原业务事务管理器、分页和 Mapper/Example 调用不变，内部 requireRecord 仍直接查询，不以自调用绕过缓存代理。
- `sync-writer.java.vm`：注入同一 BusinessCacheProcessor；实际更新计数大于 0 淘汰列表与全部详情，仅新增淘汰列表，全跳过不登记。调用仍在 Spring 写事务内，提交后才生效，回滚不淘汰；默认外部查询未接入的显式失败保持。
- `permissions.java.vm`：业务权限由 Resource.PREFIX 拼接操作，最终权限字符串不变；平台权限保持原形式。
- `scaffold-test.java.vm`：业务生成测试覆盖查询参数约定、完整资源前缀及各操作缓存注解/范围。基础业务产物增加一个 Resource 文件；同步产物仍按 generateSync 独立选择。

缓存本身的 offset < 100、TTL、类型恢复、工厂/资源隔离、提交后时机及 SCAN 规则见 [business-cache.md](business-cache.md)。若后处理增加用户/部门/主体数据权限或详情裁剪，需在使用方审查缓存隔离，不能认为生成注解已经解决该类业务权限差异。

验证采用实际生成后的 Java 编译、Mapper XML 和 H2 SQL，通过 Spring 代理执行生成的 Service/SyncWriter；缓存连接为明确标记的内存 Redis fixture，证明序列化字节及淘汰调用，不冒充生产 Redis。测试以 material_category 验证完整资源前缀，并以数据库字段临时变化证明列表/详情确实命中缓存。覆盖手工增改删、同步新增/更新/跳过及外层事务回滚，保留既有不同工厂、SQL、事务和默认查询未配置的验收。

本轮使用用户配置的副本，仅选 material_category，输出到独立 target/business-cache-template，保持原配置、前端源码及现有物料业务实现。未执行菜单 SQL、真实表 DDL、业务写入、部署、提交或推送。

### 本轮验证结果

- Java 原生格式化及 `git diff --check` 通过。
- `mvn -B -ntp test` 与 `mvn -B -ntp verify` 全 reactor 通过。BusinessCodegenRunnerTest 9 项，包括生成物料分类后实际编译、缓存命中类型恢复、Spring 代理下增改删及同步淘汰/回滚；既有 CodegenAcceptanceTest、CodegenSyncAcceptanceTest、CodegenPersonAcceptanceTest 保持通过。Global 负例确认不生成 Resource/BusinessCache 依赖。
- 最新 JAR 执行 `./scripts/mes-business-codegen target/business-cache-template/configuration.yaml target/business-cache-template/report.json`：真实 metadata 1 supported、0 rejected，GENERATED，共 28 个产物。Resource.PREFIX 实际为 business:material:category。
- 使用验收测试依赖 classpath 编译真实 metadata 生成的全部 Java（含生成测试），通过。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10 passed / 0 failed。
- 仅在隔离 target 写入产物，未重新生成覆盖正式 Material/MaterialCategory 的业务代码。前端模板本轮未改；未把生成成功/编译通过当成真实业务库运行验收。

### 2026-10-12：中性命名、主 Service 同步与个人列配置

渲染类统一命名 `BusinessTemplateRenderer`，调用方和测试同步迁移。历史审查中的旧类名保留为当时证据，
MIT 来源声明仍在 legal 文档。生成文件头不再宣称使用 RuoYi 产品流水线。

开启 generateSync 时，主 IService/ServiceImpl 提供 sync 和 upsert，主 Controller 承载原
`/actions/sync` 接口及独立同步权限。不再生成 SyncService、SyncController、SyncWriter。
sync 不开启写事务：先读取来源，再通过 SyncCoordinator 抢占，调用 ObjectProvider 获取主 Service
的 Spring 代理执行带具名事务的 upsert。动态缓存淘汰保留提交后回调。来源 DTO/QueryPort、未配置
适配器和结果类型保留明确的输入契约。

重生成的发布器在同一预检/回滚流程中删除带 MES-CODEGEN 标记的三个废弃类；无标记文件拒绝，
dry-run 仅报告 removed=true。删除精确限定本业务旧生成路径，失败恢复原文件。

可选 tableOverrides.frontendTemplate 指向模板根下的完整 Vue 模板；可选
serviceImplementationTemplate/controllerTemplate/externalInputTemplate/resourceTemplate 指向完整 Java 模板。
路径必须相对且符合对应 `.vue.vm`/`.java.vm` 扩展名，禁止越界。六个已有基础业务的 reviewed 模板保留
经审查的关联选择和 BasicDataRules，而后由生成器发布；通用默认模板仍负责新业务。
这类项目完整模板具有固定包和业务契约，调整模块/包/字段时也必须审查这些显式覆盖模板。

列表模板无条件生成 `${permission}:list` tableKey 并复用 CrudPage 通用列配置能力。
存储、API、URL 规则及最小接入见 [个人列配置](table-preferences.md)。

本次正式重生成记录：`docs/generated/codegen/2026-10-12-column-preferences.json`，对应显式配置同目录
`.yaml`，只选择 backend/frontend/test。六业务 126 项产物（含 18 个废弃类删除）；随后全量测试、
前端构建和 clean package 通过。clean 不能省略，否则删除的旧 Java 源码对应的 class 可能仍留在旧
构建目录并进入 JAR。按用户要求，后续真实页面验收由用户自行完成。

### 2026-10-12：修改 javaPackage 后的完整模板包名不一致（源码检查）

用户将 output.javaPackage 改为 `dev.mesbase.business.{module}`。BusinessCodegenRunner:97 正确展开
配置，并用于 CodegenRequest.packageName。BusinessTemplateRenderer.emitJava:107–114 依配置计算
目标路径及 packageName，但对已有 package 声明的完整源码模板直接输出 rendered，未核对声明是否
与目标路径一致。

reviewed/MaterialCategoryController.java.vm:2 以及其余 reviewed Java 模板固定写入
`dev.mesbase.modules.material` 的 package 和生成类型 import，因此输出到新的 business 路径仍声明
旧 modules 包。当前新目录的 MaterialCategoryController 和 ServiceImpl 已静态确认同样问题。
这是上轮引入完整业务覆盖模板时遗漏的配置兼容性；前述“固定包”描述不能视为合理豁免。

正确的修复范围包括：完整模板的 package 使用 packageName，各生成类型 import 使用对应
controller/domain/dao/service/response/sync 包变量，并在发布前校验 Java 声明包与输出路径一致。
此外 BasicDataRules 是现有手写业务类，依赖旧 Entity/Mapper；只改生成类型 import 后，新包 Entity
与其规则签名仍需单独核对，不能宣称包名更改等于整个业务模块已迁移。当前 Mapper 自动扫描仍使用
项目固定包，新的 dev.mesbase.business 包是否装配不属于此次包声明检查的已验证结果。

本节仅记录源码诊断；未修改生成器、模板、用户配置或新生成文件，未运行本轮编译或重新生成。

### 2026-10-12：修复包参数化并集中生成器归属

前节仅诊断的问题现已修复：所有 reviewed Java 模板的 package 使用 `${packageName}`；生成类型 import
分别使用 domainPackage、daoPackage、servicePackage、responsePackage、controllerPackage、syncPackage。
EndpointAccess、FactoryContext 等基础设施 import 保留真实固定包。BasicDataRules 按业务 service 包引用；
如果完整业务迁入另一个根包，该手写规则及应用 Mapper/组件扫描仍需一起迁移，生成器不代替应用配置扫描。
发布前校验 Java package 声明与输出路径一致，完整模板写错包时 TEMPLATE_FAILURE，整批不写文件。

CLI、BusinessCodegenConfiguration、BusinessCodegenRunner 及其配置/集成测试迁入 mes-generator，
统一 `dev.mesbase.platform.codegen` 包。模板资源（含 47 个 .vm）也从 mes-admin 迁入
`mes-generator/src/main/resources/codegen`，classpath 模板名称不变。项目配置只更新 templates.root；
用户设置的 output.javaPackage 等参数保留。scripts/mes-business-codegen 使用新的 loader.main。
检查 scripts 下 .py 文件，没有旧业务 codegen 入口引用，故未修改无关 Python 文件。

按用户授权从 HEAD 恢复生成测试误删的 233 个 mes-business 文件，再执行上述迁移；不回滚其他修改。
不完整的未跟踪测试生成输出（6 个 XML 与 6 个测试）保存在
`/private/tmp/mes-codegen-test-output-yjutc1w3`，移出编译路径，内容未删除。
新增生成回归覆盖六个 reviewed 业务模板、同步开关、新根包及不同子包配置，并保留固定基础设施 import。
生成验证使用独立 target 目录，不在本次任务中把旧业务整体迁入新包，也不修改业务数据库或菜单。

验证结果：`mvn -B -ntp clean verify` 全 reactor 通过；迁移后的 CLI 按当前项目配置复制到隔离输出
目录生成六张表，报告 GENERATED / 0 rejected，所有 Java package 与路径一致，MaterialCategoryController
的业务 import 实际为 dev.mesbase.business.material.*，固定 EndpointAccess import 不变。
产物位于 `target/package-import-generation`，报告 `target/package-import-generation-report.json`。
`/usr/bin/python3 scripts/smoke-packaged.py` 为 10 passed / 0 failed。打包核对 CLI 与 47 个 .vm 模板仅归属
mes-generator，不再位于 mes-business 或 mes-admin 的资源目录。mes-business 不再依赖 mes-generator。
本次通过的是当前恢复后的项目及生成工具编译/测试；隔离生成的新根包业务尚未做完整运行期迁移，
不宣称其 BasicDataRules、Mapper 扫描及组件装配已迁入新根包。未执行视觉验收、数据库写入或部署。

后续工作区出现再次正式生成的 dev.mesbase.business 业务源码：一次整仓 compile 实际因新包缺少
BasicDataRules 失败。这不同于前述恢复后 clean verify 的通过结果，不能用旧结果声称新业务产物通过。
用户随后明确本轮只保证生成器，正式代码生成由用户操作；不继续迁移业务规则/Mapper 扫描，不修改
新生成业务文件。最终验证限定 mes-generator 及其上游依赖；本次修复不会自动迁移使用方的业务包。

### 2026-10-12：用户最终确认统一通用模板（替代 reviewed 覆盖）

所有代码生成统一使用 codegen/base 通用模板。通用能力不满足需求时修改通用模板与测试，不复制一套
按业务命名的模板。此前 reviewed 是为保留六个已审查业务交互而增加的覆盖层，带入了 BasicDataRules
等未由通用生成器提供的依赖；本轮按用户确认删除目录及其覆盖机制，不继续保留这套平行实现。

TableOverride 仅保留 module、displayName、syncMatchKey、serverScopeColumn；配置中的五种模板路径
覆盖属性移除，旧覆盖属性明确拒绝，不静默接受。用户修改的模块名、包名与其他参数保留。
模板统一由 templates.root 定位，不存在每表专属模板选取。Java 输出拒绝空模板，以及 package 与
输出路径不一致的模板；回归测试编译六种表名、开关同步两种情况的通用生成源码，验证新根包和
自定义子包 import，保证生成 Service 不依赖 BasicDataRules 等物料专用类。

生成器额外产出可执行 `-cli.jar`；普通 JAR 继续供应用复用，脚本直接运行独立 CLI，生成器更新不再
要求整个业务应用先能编译。正式业务生成由用户执行，本轮不再重生成或迁移业务源码。

最终验证：`mvn -B -ntp -pl mes-generator -am verify` 通过（生成器 17 项测试全部通过），其中通用
模板对六种表名 × 同步开关两种情况共 12 组生成源码执行真实 Java 编译，通过新根包与自定义子包
引用检查。独立 CLI JAR 启动及非法参数退出码检查通过，包含 MySQL 驱动且没有 reviewed 资源；
`sh -n scripts/mes-business-codegen`、`git diff --check` 通过。该启动检查未执行业务生成。
最新状态只声明生成器及其上游依赖验证通过，不使用此前整仓快照结果替代用户当前业务源码状态。
