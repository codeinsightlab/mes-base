> 2026-10-17 配置收敛：下文 V34 YAML 导入是已发布迁移的历史契约。当前正常业务（含非托管入口）缺 FactoryIntegrationSnapshot 即失败，旧仓储 YAML 已从 dev 移除；测试显式构造快照。E10 参数由工厂中心配置维护。现状与验证见 [配置收敛报告](../reviews/2026-10-17-configuration-consolidation-review.md)。

# 工厂业务运行模式与 ERP 配置验收

日期：2026-10-17。任务独立于 Production × Warehouse 架构审查。

## 已实现

工厂管理沿用 `/api/system/factory` 的新增、编辑、列表、详情，以及原平台权限。新增 `/api/system/factory/integration-options?factoryId=...` 返回该工厂可用的 `reference/provider/revision` 白名单。新工厂默认 LOCAL；ERP 必须校验本厂连接引用、provider、启用状态、配置版本、服务地址、组织及认证引用。LOCAL 不接受 ERP 字段；旧客户端省略新字段时保留已存模式与引用。异常不回退 LOCAL。

中心 `mf_factory` 增加五个字段：`runtime_mode`、`erp_provider`、`erp_config_ref`、`integration_revision`、`integration_locked`。没有业务表/第三数据源变更。旧配置以 `mes.integration.warehouse.factories.<id>.mode=CONNECTED` 为明确 ERP 声明，保留其 provider/binding-key；非法模式或缺失的 ERP 身份在 DDL 前拒绝。原来没有模式的工厂按 LOCAL 兼容初始化。

所有迁入旧工厂均锁定业务权威；新建未绑定厂可配置，首次绑定后永久锁定。模式、供应商、绑定引用的普通编辑被拒绝，包括解绑再编辑。此策略有意比“有历史才锁定”更保守，避免跨库历史查询和并发写入之间的竞态。既有空厂也需要后续专门迁移流程才能改权威。名称、状态等正常维护保持原流程；配置本身无效时保存明确失败，应修复服务端连接定义。

没有可复用的独立 ERP 连接管理表或供应商枚举。复用现有服务端配置机制及 Adapter binding/provider 标识，不新增连接管理页面、不添加厂商实现。新增连接定义示例（全部为说明值，不是已部署连接）：

```yaml
mes:
  integration:
    connections:
      erp-binding-v1:
        factory-id: FACTORY_A
        provider: DINGJIE
        service-url: https://erp.invalid/api
        credential-reference: private.erp.factory-a
        organization: TEST_ORGANIZATION
        enabled: true
        revision: 1
```

`credential-reference` 指向服务端 Environment 可解析的凭证属性，由现有环境变量、受保护配置或部署凭证注入提供其值；示例不包含真实凭证。连接引用代表不可变集成身份，供应商/组织/账套身份变化应使用新引用及专门迁移。连接定义未配置、无效或停用时 ERP 请求拒绝；没有 Adapter 实现时实际业务能力仍拒绝。测试中的 DINGJIE 只是合成 provider 标识，不代表已接入鼎捷。

## 上下文与历史兼容

```java
var integration = FactoryIntegrationContext.required();
integration.factoryId();
integration.mode();
integration.erpType();
integration.configurationRef();
integration.revision();
integration.configurationRevision();
```

HTTP 沿用已解析的中心 metadata，准入时将只读集成快照加入 `BusinessRequestSnapshot`，每个业务请求复用一次解析结果。快照不含服务地址、组织、密码、Token、Secret 或认证配置内容；不扩充只含 FactoryId 的 FactoryScope。使用 v2 路由缓存 key，旧缓存 JSON 不会被当作缺省 LOCAL。

非 HTTP/异步代码注入 `FactoryIntegrationExecutor`，在真正执行线程调用 `executor.run(scope, task)`。Quartz 工厂入口也显式使用它。工厂配置在执行线程解析，任务期间 pin 工厂范围，异常和完成后恢复/清理；新线程不自动继承上下文。已准入 HTTP 请求不能被另一份快照覆盖。独立事务租约和权限仍按原基础设施规则运行。

`WarehouseIntegration` 新提交的配置选择来自快照；历史提交、查询、取消沿用已冻结的 source mode/provider/binding，不读取当前模式重新解释历史事实。查询和提交要求 Adapter 明确声明支持冻结的 provider；缺少对应实现拒绝，不伪造本地成功。历史取消维持原来冻结 binding 选择能力。没有修改 ProductionQuantityService 数量算法，没有重构生产、领料、仓储 Service。

## 修改文件

- 中心模型与 SQL Mapper：`mes-system/.../factory/ManagedFactory.java`、`ManagedFactoryMapper.java`、`FactoryExecutionMetadata.java`、`RoutingMetadataMapper.java`。
- 模式及错误码：`mes-common/.../core/factory/FactoryRuntimeMode.java`、`core/error/ErrorCode.java`。
- 上下文：`mes-framework/.../platform/execution/FactoryIntegrationSnapshot.java`、`FactoryIntegrationContext.java`、`FactoryIntegrationExecutor.java`，以及 `infrastructure/persistence/BusinessRequestSnapshot.java`。
- 平台配置用例：`mes-admin/.../server/managed/ManagedDatasourceService.java`、`FactoryIntegrationRegistry.java`、`FactoryMetadataCache.java`；API `web/controller/system/ManagedFactoryController.java`。
- 装配：`mes-admin/.../server/FactoryRuntimeConfiguration.java`、`SystemConfiguration.java`、`PersistenceConfiguration.java`；Quartz `mes-quartz/.../util/ControlledJobRunner.java`。
- 正式迁移与统一注册：`mes-admin/.../server/schema/V34__FactoryBusinessIntegration.java`、`FactoryFlyway.java`；原管理 CLI `platform/management/PlatformAdminCli.java` 使用同一迁移入口。
- 集成分派：`mes-business/.../integration/warehouse/WarehouseIntegration.java`、`WarehouseErpAdapter.java`。
- 前端：`mes-base-ui/src/views/system/factory/index.vue`、`src/api/system/datasource.ts`、`tests/factoryIntegration.test.ts`。
- 新验收：`FactoryIntegrationConfigurationTest`、`FactoryIntegrationLiveTest`。
- 关联测试适配：`ManagedAdmissionTest`、`ManagedColdPoolTest`、`FactoryPreferenceMigrationTest`、`RoutingMetadataRedisTest`、`WorkOrderRequestBudgetTest` 的真实 Mapper fixture 列；`MaterialIssueErpHttpTest`、`ProductionCoreV1HttpTest`、`LocalWarehouseErpTest` 的冻结执行方契约；`ProductionDefectMigrationMySqlTest` 使用统一中心迁移注册。
- 架构记录：追加 `docs/architecture/ADR-005-erp-integration-boundary.md` 2026-10-17 节；本验收文档。

没有修改正在审查的生产仓储方案/报告、既有未提交业务代码或本任务外 UI 文件。本任务没有执行 git add/commit/push，也没有操作服务生命周期；工作区 HEAD 曾在执行期间由外部操作前移，未对此回退或重写。

## 正式数据库迁移

真实中心目标：`192.168.18.25:3306/mes_base_dev`。先通过原中心配置、datasource ID/host/port/db 与工厂状态核对用户指定的 `SIMPLE_B → 192.168.18.25:3306/mes_base_dev_business_simple_b` 绑定，并实际连接业务库只读核对 DATABASE()。未另建库或工厂替代验收环境。

V34 为正式 Flyway Java migration，通过生产共用 `FactoryFlyway` 注册并写入 `flyway_schema_history`。新 SQL 保存在 Java migration 中，由 Flyway 执行；没有手工执行 DDL 后绕过迁移记录。V34 已执行并 validate 通过，五个新列均有中文 COMMENT；旧四个工厂 `1/2/DEV/SIMPLE_B` 保留，均为 LOCAL、integration_revision=1，权威锁定。没有修改业务库 Schema 或业务数据。

执行前备份位于 `.local/factory-integration-20261017/factory-before.txt`，目标绑定证据为 `verified-binding.txt`。真实库验收在不存在同编码的前提下创建 `TEST_FACTORY_INT_20261017` 合成工厂：验证缺省 LOCAL、改为 ERP/DINGJIE/test-a 后版本与回显、无效 provider 拒绝；最终删除本轮合成工厂并确认数量为零。测试连接定义只存在于测试进程，不写入用户配置；当前真实工厂均保持原 LOCAL。

首轮真实库测试在迁移成功后因合成 factory_code 超过既有长度失败；缩短合成编码后重跑通过。没有覆盖用户记录。数据库证据摘要见同目录 `verification.txt`（包含清理前合成行）；最终清理断言在测试中通过。

## 验证记录

- `mvn -B -ntp verify`：全模块通过，629 项，0 failure，0 error，38 项默认关闭的 opt-in 验收跳过。最终格式和上下文防护补充后再次全量复验通过（09:51:04）；629 项含 38 项跳过，实际执行 591 项。
- `MES_FACTORY_INTEGRATION_ACCEPTANCE=true mvn -B -ntp -Dtest=FactoryIntegrationLiveTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；正式迁移、真实中心持久化、目标业务库绑定及显式任务上下文验证。没有真实 ERP 调用。
- `npm run test`：Node `.nvmrc` 的已安装 Node22，56 文件 / 329 项全部通过，含新增三项页面测试（缺省、回显与提交、配置错误和迟到响应）。
- `npm run build`：通过；存在原有大 chunk 提示。
- Java 使用项目 `scripts/format-java` 格式化本任务修改的生产文件及新测试。
- 首轮后端全量出现测试辅助 class 缺失以及 Java migration 命名错误，已修正类命名并完整重编译；后续全量通过。新增测试验证跨厂拒绝、配置停用/失效/凭证缺失拒绝、快照稳定、禁止请求内覆写、线程不继承、异常清理以及切厂拒绝。原权限、选厂、双库路由/事务回归随全量执行。

真实浏览器打开现有 5173：先显示会话恢复服务不可用，重试后进入登录页并提示登录已失效；现有 8081 `/health` 为 HTTP 200/UP。没有取得有效登录，因此未完成真实工厂页面、三档宽度和 ERP 选择保存 UI 联调；单元/构建不冒充该证据。用户服务没有启停，已运行进程是否包含最终代码未确认。打包 smoke 会启动服务，因此本任务按禁止启停服务的限制未执行；没有部署或厂商联调声明。

## 回退与后续

Schema 为兼容增加列；不删除列、不改写已执行 V34、不复位现有工厂模式或 Flyway 历史。若回退应用代码，保留原 ERP provider/binding 服务端配置及历史外部事实。模式迁移、历史未决操作、库存对账和实际厂商业务实现另立流程。

完成真实 UI 联调需要用户自行重启最终后端构建并在现有 5173 登录。平台/业务权限和数据源绑定仍由服务端最终校验；当前没有可供真实前端选择的 ERP 厂商连接，不创建假连接作为正式配置。
