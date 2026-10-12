# Material ERP 同步架构试点

> 当前实现已调整为正式未接入占位查询，TEST Adapter 移入测试目录；原单独生成配置/fixture launcher 已移除。以下保留首轮试点历史，最新调用链和验证见文末。正式生成只读 `docs/development/codegen-config.review.yaml`。

2026-10-12；ARCHITECTURE PROTOTYPE。只有 Material，不含完整物料主数据、真实 ERP 接入或通用 Integration Framework。

## 模型与实现

当前仓库没有 Material 表/页面或 ERP Resolver。采用最小 `material` 表：id、code、name、specification、unit、status、external_id、external_code；所有字段及表有 COMMENT，除主键只建 code 普通索引。业务库由既有 Factory 路由隔离，不增加 factory_id、第三个数据库入口或外部 JDBC。

稳定键选 `code`：同工厂独立库内同编码首次插入，已存在且七项业务/外部来源字段变化时更新，未变化时 skipped。specification 允许从非空更新为 null，使用生成 Mapper 的全量主键更新，不误用 Selective 跳过清空。外部批次最多 1,000 条，整批校验后一次 IN 查询现存记录，不逐物料 SELECT；写入逐条检查影响行数。空的已配置来源允许返回零条成功，未配置绝不以空结果代替错误。

返回 `fetched/created/updated/skipped`；skipped 仅表示内容未变，非法数据不悄悄跳过。外部重复编码、非法字段拒绝整批；库中同编码已有多条时返回 RESOURCE_CONFLICT，不任意选择或合并。没有 UNIQUE 索引，不能保证与其他 CRUD 写入入口之间的并发编码唯一性；这不是无约束的“先查再插”并发安全方案。

TEST Adapter 返回 M001/M002，其中 M001 有 `extraFields.erpCustomField=X`。专有字段明确忽略，不进入 MES Entity、表、响应或扩展表。本轮证明边界，不为一个字段创建扩展框架。该 Adapter 类标注 TEST ONLY，必须同时显式启用 `mes.material.enabled` 与 `mes.material.test-adapter.enabled`；默认不开启，不连接真实 ERP。

## 实际调用链

```text
src/views/material/material/index.vue
  → components/business/material/MaterialSyncButton.vue
  → api/material/sync.ts
  → POST /api/material/material/actions/sync [material:sync]
  → MaterialSyncController.sync [FactoryContext.required()]
  → MaterialSyncService.sync
      → FactoryErpResolver.resolve(scope)
      → MaterialQueryPort.queryMaterials(scope, maximum)
          → TestMaterialQueryAdapter [TEST ONLY]
      → MaterialSyncWriter.upsert [businessTransactionManager]
          → generated MaterialMapper / MaterialMapper.xml / MaterialExample
          → current Factory business database
```

同步 Controller 不接收 factoryId、数据源键或 ERP 类型；授权和当前 Factory 来自现有请求准入。配置仅包含当前物料试点的 Factory→TEST 绑定；缺键、未知类型或 Adapter 未启用均为 HTTP 422 / ERP_SYNC_NOT_CONFIGURED。此码不会触发工厂重选的前端处理，只有既有 FACTORY_SELECTION_REQUIRED 会触发。

`MaterialSyncService` 在业务事务之外复用已有 `SyncCoordinator`，以 Factory + `material.erp` 抢占，使用 run token 和五分钟恢复租约。在外部读取后、业务批次前通过条件 progress 续租和检查执行权；提交后继续条件更新进度/完成。失败尝试记录 FAILED，失败交付异常作为 suppressed 保留，日志带 Factory/run token。没有自建锁表、JVM 任务锁、调度或任务中心。

所有中心协调操作与业务写入事务分开，禁止中心/业务混合事务。单个已开启批次仍由 Spring 完成，执行权在处理边界检查，不中断正在执行的 SQL。业务提交与中心完成标记不是原子提交：提交后进度/完成失败可能返回错误，但物料已保存，重试会按编码重读并跳过相同数据。崩溃恢复/旧任务租约重叠、其他入口并发写入不能据此宣称 exactly-once；真实多节点与 MySQL 恢复场景本轮未验收，扩大规模前需专项验证。

## 七项结构判断

| 问题 | 本轮选择与理由 |
| --- | --- |
| MaterialSyncService 必要吗 | 同步用例必须有承载点；使用具体 Service，无单独接口 |
| DefaultMaterialSyncService 必要吗 | 删除候选的接口＋Default 实现拆分；只有一套编排实现，拆分无替换价值 |
| MaterialQueryPort 必要吗 | 保留，隔离外部物料契约与 MES 持久化；用于替换测试/未来真实 Adapter |
| Resolver 与 Foundation 重复吗 | 不重复：Foundation 只管理工厂/业务库，Resolver 仅选物料外部查询 Adapter，不解析业务数据库 |
| Adapter 边界清晰吗 | 仅构造 ExternalMaterial，没有 Mapper、事务或持久化依赖 |
| 哪层最可能过度设计 | 试点 Resolver 仅识别 TEST，最可能在单一来源下多余；目前保留以验证工厂绑定和缺配置失败 |
| 再简化删哪层 | 首选将 Resolver 的小分支并入 MaterialSyncService；不删 Port 边界或业务事务边界。不创建通用注册表 |

`MaterialSyncWriter` 不是空包装：执行输入校验、批量匹配、差异判断和 Upsert，且是 Spring 事务代理的独立入口。生成 CRUD Service/Controller 保持基础 CRUD；同步直接复用生成 Mapper，没有 ExtraMapper、第二套 CRUD SQL或生成器业务逻辑。

## 页面、权限和生成产物

- 基础代码来自 `mes-crud-codegen` Skill：显式配置 `codegen.yaml`，真实读取隔离 H2 的 JDBC metadata，报告 `target/material-sync-codegen/report.json` 为 GENERATED / 1 supported / 0 rejected。这是明确 fixture，不是实际 MySQL 建表或 metadata 证明。
- 原始用户 `docs/development/codegen-config.review.yaml` 未修改。生成包 `dev.mesbase.modules.material`；页面 `src/views/material/material/index.vue`，新增基础 CRUD 权限 `business:material:read/create/update/delete`。
- 同步使用用户本轮明确的 `material:sync`，不复用 create，单独 Controller、前端按钮和 `sync-menu.sql` 一致。
- `CrudPage` 仅新增可选 toolbar slot；原有 CRUD、权限、分页/删除流程不变。物料页放入同步按钮，成功刷新列表，取消不请求，错误使用后端消息；确认期间切厂/撤权不提交，过期结果不刷新新上下文。
- 页面与菜单生成后做业务后处理（toolbar、状态选项）。`write/fail` 会保护这些变化；不要为重新生成而自动切换覆盖策略。

## 可复现检查

从后端仓库根运行：

```sh
mvn -B -ntp -Dtest=MaterialSyncHttpTest,MaterialSyncBoundaryTest,MaterialScaffoldTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -Dmes.test.redis=true verify
```

MaterialSyncHttpTest 使用生产准入 Interceptor、Spring 事务与生成 Mapper/XML，中心和两个业务库为隔离 H2；账号/授权查找是 synthetic fixture，不表示真实 Redis 登录、MySQL 或 ERP 验收。外部第二次变化由测试替换 Port 的返回值，不增加运行期“修改测试 ERP”的 API。

如需重建生成 metadata，先将本机已有 H2 JAR 路径作为显式参数传入：

```sh
python3 scripts/prototypes/material-codegen-fixture.py /absolute/path/to/h2.jar
```

该脚本只重建 `target/material-sync-codegen` 的 H2 metadata，不能指定远端库，不写生成源文件。首次生成的底层命令如下；脚手架现已存在且页面有后处理，重跑 write/fail 可能报输出冲突，是正常保护。查看新生成产物时复制配置，将前后端输出改为独立 target 目录。

```sh
LOADER_PATH=/absolute/path/to/h2.jar ./scripts/mes-business-codegen \
  docs/prototypes/material-sync/codegen.yaml target/material-sync-codegen/report.json
```

## 用户环境验收步骤（未自动执行）

1. 在获授权的试验业务库审查并执行 `schema.sql`；中心库必须已有既有 V12 同步协调表。不要把物料表建到中心库。
2. 合并 `application-test-only.yaml` 到隔离验收配置，将示例工厂键换成实际已启用且绑定业务库的工厂 ID。现有 system/runtime 功能必须启用；本轮未修改用户运行配置或默认开关。
3. 中心库审查并串行执行 `mes-business/docs/generated/material/material-menu.sql`，再执行本目录 `sync-menu.sql`；由管理员明确授予 CRUD/read 及 material:sync，不自动发角色权限。
4. 用户自行加载后端，重新获取菜单/权限，打开物料页点击同步：首轮 2 created；再次 2 skipped。无同步权限看不到按钮，直接调接口返回 403；未配置工厂返回真实错误。

本轮只生成/实现及隔离验证，没有执行真实建表、菜单 SQL、授权、重启用户服务或提交/推送。样例尚不等于真实 ERP/生产验收。


## 最终本地证据

- Codegen：`LOADER_PATH=<local H2 jar> ./scripts/mes-business-codegen docs/prototypes/material-sync/codegen.yaml target/material-sync-codegen/report.json`，GENERATED，1 supported / 0 rejected。使用明确创建的隔离 H2 metadata，原配置与真实 MySQL 未修改。
- `mvn -B -ntp -Dmes.test.redis=true verify`：268 项，264 通过，4 项真实 MySQL 跳过，0 失败/错误；日志 `/tmp/mes-material-accepted-verify.log`。
- 本轮新增后端 8 项：3 项真实 Spring/MockMvc/两厂 H2 SQL 场景，4 项配置开关/未配置/未知来源/执行权失效边界，1 项生成契约；HTTP 身份与权限查找明确为 fixture。覆盖批次写入失败回滚、重复编码拒绝及 NULL 清空。
- Node v22.23.1：最终前端 140 项通过（本轮新增 6 项），build 通过；日志 `/tmp/mes-material-final-tests.log`、`/tmp/mes-material-final-build.log`。新增组件取消、成功、错误、确认期间切厂、迟到结果及生成页面成功后真实 load/API 契约均有测试。既有大 bundle 提示保留。
- IDEA 定向格式化与 26 Java 文件检查通过；前端新增文件定向 ESLint 通过，两仓 `git diff --check` 通过。
- 初轮测试编译中的 accessor 名称错误、Element Plus MessageBox fixture 类型错误已修正；未放宽生产类型或删除断言。后续检查收敛外部引用字段为 Schema 可空，不把可选字段强制必填。
- 工作期间检测到外部提交/工作区变化，当前后端 HEAD 为 `3906f0c`；保留其他 OpenAPI、工厂恢复和业务迁移工作，未由本任务提交。回滚只涉及本目录、Material 生成/同步代码及测试、ERP 错误码、前端 Material 和可选 toolbar slot，无真实数据库回滚。

- 最终打包隔离检查 `scripts/smoke-packaged.py --openapi-docs`：14/14，通过；日志 `/tmp/mes-material-final-packaged.log`。默认 Material 开关关闭；启用后的业务链路以本轮 Spring/H2 测试证明，未冒充运行中用户服务验收。


## 2026-10-12 后续调整：正式链路不返回测试物料

- 删除只有 TEST 分支的 FactoryErpResolver 和 MaterialErpProperties，不再保留无选择价值的工厂 ERP 类型配置层。
- 保留 MaterialQueryPort，MaterialSyncService 通过 ObjectProvider 获得单个实现；不存在真实 Bean 时使用 UnconfiguredMaterialQueryAdapter。默认实现不是第二个竞争注入的 Bean，注册一个真实 Port Bean 即替换默认查询，编排和写入不变。
- 默认调用明确返回 422 / ERP_SYNC_NOT_CONFIGURED，消息“暂未接入 ERP 物料查询接口”；查询在抢占之前，默认失败不写物料，也不创建同步状态记录。真实来源只读查询可能并发；查询完成后业务写入仍先抢占并校验执行权，独立 Spring 事务整批回滚。
- TestMaterialQueryAdapter 从生产源移至测试源（业务模块单元测试、admin HTTP fixture 各自的 TEST ONLY 数据）。正式产物不含硬编码 M001/M002 数据。
- 保留当前范围、批次/字段/状态/重复编码校验、按 code 新增/更新/跳过、businessTransactionManager 和 NULL 清空；extraFields 仍不入库。MaterialSyncButton 复用通用 SyncButton，当前权限仍为 material:sync。
- 当前链路：Material 页面 → MaterialSyncButton/SyncButton → sync API → MaterialSyncController → MaterialSyncService → MaterialQueryPort（默认 UnconfiguredMaterialQueryAdapter）→ SyncCoordinator → Spring 代理 MaterialSyncWriter → 生成 Mapper/XML。
- 正式生成器同步能力及六表覆盖生成独立记录于 `docs/development/codegen.md`。旧物料专用 codegen.yaml 和其孤立 H2 metadata launcher 已删除，避免形成第二个正式参数入口；测试仍保留真实 H2 metadata/SQL 验证。

- 后续调整最终验证：覆盖后的 Maven verify 275 项（271 通过、4 个真实 MySQL 跳过），前端 140 项＋build；生产 JAR 无 TEST Adapter/旧 Resolver。仍未对用户运行服务或真实 ERP 执行物料同步。完整生成参数、六表产物和关闭清理限制见 `docs/development/codegen.md`。


## 2026-10-12 第一批 SIMPLE 实施取代原型模型

正式 Material 已用当前 Base Codegen 读取实际开发 MySQL metadata 生成，Schema 以 db/business/V2__simple_business_capabilities.sql 为准；旧 schema.sql/application-test-only.yaml 仅是原型历史，不作为部署输入。
新增 factory_id、category_id、unit_id、ITEM/PRODUCT 和厂内唯一约束；标准外部 DTO 的 unitCode/categoryCode 在 SyncWriter 映射本地 ID。MaterialSyncButton/sync.ts 旧独立壳已删除，正式页面直接使用统一 SyncButton 与生成 API；权限为 business:material:list/query/create/update/delete/sync。
MaterialSyncHttpTest 已迁到新表与 QueryPort.query 契约，保留两厂、create/skip/update、NULL 清空、权限、未配置、重复来源拒绝及事务回滚测试；TEST 启动入口仅在 src/test/java/dev/simplefixture，用于本轮独立端口实际 MySQL/browser 验收，不进入正式 JAR。完整结果见迁移验收报告。
