# MES Base 架构评审报告（后端 mes-base + 前端 mes-base-ui）

> 2026-10-12 多节点部署与横向扩容复核见本文 §七；基于当日工作区与定向测试，前文评分、旧架构和旧验收结果不代表本轮部署结论。

> 2026-10-09 后续复核：原评分与发现保留为历史评审；当前阶段、纠正意见和整改见 [行动方案 §五](2026-10-09-architecture-review-actions.md#五2026-10-09-复核与整改当前有效)。同步测试不存在、强制立即改造生成器、直接合并已应用迁移等结论不再作为当前执行依据。

- 评审日期：2026-10-09
- 评审对象：`/Users/user/work/lixin/mes-base`（Java21 / Spring Boot 4.1.1）、`/Users/user/work/lixin/mes-base-ui`（Vue3 / TS / Vite）
- 评审性质：**只读架构评审**。未修改任何源码、配置、数据库或服务；本报告为新增文档。
- 评审目的：判断该架构能否作为后续业务开发的 Base 开启，并给出评分。
- 证据边界：本次实际执行 `mvn -B -ntp verify`（真实结果：172 run / 0 fail / 0 error / 4 skipped / BUILD SUCCESS）；其余为静态源码、配置与 Git 元数据核验。**未**启动服务、**未**连接远程 MySQL、**未**重跑前端测试（命令被拒绝）、**未**做浏览器验收。

---

## 一、总体结论

| 对象 | 得分 | 评价 |
| --- | --- | --- |
| 后端 mes-base（架构本体，剔除凭据事故） | **7.7 / 10** | 架构设计明显高于一般 RuoYi 派生产品，具备作为 Base 的水准 |
| 后端 mes-base（含凭据事故） | **7.1 / 10** | 被 P0 安全事故整体拉低 |
| 前端 mes-base-ui | **6.3 / 10** | 工程底座合格，但"统一 CRUD 壳"未落地、类型与契约仍是迁移态 |
| **合并判定** | **有条件放行（Conditional Go）** | 架构方向正确，**当前状态不可直接开启**，需先清 3 个阻断项 |

**一句话结论**：这是一套"设计已经想清楚了、但还没打扫干净"的基础架构。核心的 Factory 隔离、中心库+多业务库拓扑、HTTP 显式分类三项做得相当扎实，值得作为 Base；但凭据已进版本库并推到远端、当前状态全部未提交、前端统一 CRUD 约定未收敛，这三件事不解决，业务一铺开就会把成本固化。

---

## 二、后端评分明细

| # | 维度 | 权重 | 得分 | 加权 |
| --- | --- | --- | --- | --- |
| B1 | 分层与模块边界 | 12% | 7.5 | 0.90 |
| B2 | Factory 隔离与持久化拓扑 | 16% | 9.0 | 1.44 |
| B3 | 认证 / 授权 / HTTP 分类 | 12% | 8.5 | 1.02 |
| B4 | 事务一致性与同步互斥 | 10% | 8.0 | 0.80 |
| B5 | 安全与密钥管理 | 14% | 3.0 | 0.42 |
| B6 | 测试与可验证性 | 12% | 8.0 | 0.96 |
| B7 | 文档与治理一致性 | 10% | 7.0 | 0.70 |
| B8 | 工程卫生 / 可维护性 | 14% | 6.0 | 0.84 |
| | | | **合计** | **7.08** |

### B1 分层与模块边界 — 7.5

**做得好**

- Controller → Service → Mapper 三层清晰，`mes-admin` 只做装配与 HTTP，`mes-system`/`mes-quartz` 承载 Source 业务，符合 AGENTS 第 4 节。
- HTTP 边界转换集中：`mes-admin/src/main/java/dev/mesbase/web/view/**` 下按领域组织 `XxxResult` + `XxxAssembler`（user/role/dept/post/menu/dict/log/file/message/autocode），`PageResultMapper` 统一分页转换。这是比"Service 直接返回 VO"更干净的做法，值得保留。
- `mes-generator`（359 行）只做元数据读取与模板渲染，未污染生产链路，符合 AGENTS 第 14 节"迁移期不调用 Codegen"。

**扣分项**

- **包被横向拆散到多个模块**：
  - `dev.mesbase.infrastructure.persistence` 同时存在于 `mes-common`、`mes-framework`、`mes-admin`（test）三个模块；
  - `dev.mesbase.platform.*` 散落在 `mes-common`（management.FileStorage）、`mes-framework`（web/security/execution）、`mes-generator`（codegen）、`mes-admin`（management、server）四处；
  - 结果：读代码时无法靠包名判断归属，也无法用模块依赖强制 Core 边界。
- **`mes-common` 是 11,731 行的杂物间**：它同时装着 `dev.mesbase.core`（应纯净）与 `XssFilter`、`ExcelUtil`、`RedisCache`，其 pom 直接依赖 `spring-boot-starter-webmvc` / `security` / `data-redis` / `pagehelper` / `poi`。Core 的"不依赖 Spring"只能靠测试守，无法靠模块结构守。
- `dev.mesbase.core` 物理上位于名为 `mes-common` 的模块内，命名与职责不符；若后续做 `mes-core` 独立模块成本很低，收益明显。

### B2 Factory 隔离与持久化拓扑 — 9.0（本次评审最亮的部分）

**做得好**

- `OwnedTransactionManager`（`mes-framework/.../OwnedTransactionManager.java`）在 Spring 标准 `JdbcTransactionManager` 上只加"数据库身份守卫"：`doBegin` 绑定 owner、`doCommit` 前 `routing.verify()`、`doSuspend/doResume` 完整处理 owner 与 routing state、`doCleanupAfterCompletion` 用 finally 解绑。传播/回滚/挂起交给 Spring —— 这正是 AGENTS 第 9.2 节要求的形态。
- `FactoryBusinessDataSource` 每个 Spring 事务 pin 一个版本化池，`verify()` 同时校验 `factoryId` 一致、`resolver.resolve()` 未变、lease 有效；`getConnection(user, pwd)` 直接抛异常拒绝请求侧凭证覆盖 —— 堵住了"调用者任意选库"。
- `RuntimeRegistry` 的池替换语义完整：`registerDedicated` 做 revision 递增校验 + 同池重复注册拒绝 + owner 冲突拒绝 + 失败即 `closer.run()`；旧 runtime 标 retired 后 `leases==0` 才关池（真实排空，不是伪排空）。
- `PersistenceStatementGuard` 挂在每个 SqlSessionFactory 的 MyBatis 拦截链上，且 `setCacheEnabled(false)` + `LocalCacheScope.STATEMENT` —— 把"缓存绕过范围校验"这条路提前封死了。多数同类项目做不到这一点。
- **物理库去重**：`ManagedDatasourceService.physicalIdentity()` 用 `SELECT @@server_uuid, DATABASE()` 取真实物理身份，业务库与平台库同物理库、或两个不同 `datasourceIdentity` 指向同一物理库时明确 `RESOURCE_CONFLICT`。这是对 ADR-002"无法仅凭 opaque identity 识别同物理 schema"这一已知缺口的正面补齐。
- DDL 快照写盘带 `rwx------` 目录 + `rw-------` 文件 + 原子 move + 16MB 上限，细节到位。

**扣分项**

- `physical` 去重表是**节点本地内存**（`private final Map<DatasourceIdentity,String> physical`），多节点部署时该保护退化为"每节点自查"；跨节点指同一物理库仍会被不同节点接受。ADR-002 已把这条列为待决，但代码里没有留可见的告警或文档交叉引用。
- `ManagedDatasourceService` 是 249 行的"上帝服务"：同时承担平台库 CRUD、密码加解密、连接池创建、物理身份探测、DDL 快照、注册表编排、分页。虽然 `synchronized` 用在节点本地配置变更上是合理的，但建议按 DDL/池生命周期/管理 CRUD 拆分，否则它是未来第一个失控点。
- `Shared`（多厂共享库）仍是 DESIGNED，registry 的 dedicated-only 限制是显式的、安全的，但要清楚：**当前只支持"一厂一库"**。业务若出现"多厂共库"需求，需要新实现 + ADR-004 的 7 条验收，不能靠改配置糊过去。

### B3 认证 / 授权 / HTTP 分类 — 8.5

**做得好**

- **注解覆盖率 100%**：实测 181 个方法级 mapping 全部带 `@EndpointAccess`（其中 38 处为全限定写法，见 B8）。`EndpointCatalog.required()` 在 `SmartInitializingSingleton` 中遍历 `requestMappingHandlerMapping` 强制校验，缺注解直接启动失败 —— 这条"启动即失败"的承诺是真的，不是文档口号。
- `FoundationAccessInterceptor` 的分支处理正确：匿名/未认证 → 401；外部 OpenAPI principal **不能**被当成内部 SysUser，也不能隐式解析选厂偏好，且只允许 `AUTHENTICATED + PLATFORM scope`；否则 403。
- 平台权限与工厂权限是两条独立链路（`PlatformAuthorization` vs `AuthorizationService`），`permissionScope` 显式声明，`PLATFORM + 无显式选厂` 时不绑定 Scope。符合 AGENTS 第 3/7 节。
- **`isAdmin()` 全部硬编码 `return false`**（`SysUser`、`SysRole`、`SecurityUtils`），即 RuoYi 的 `userId==1` 超级管理员特权已被彻底移除，同时保留调用点兼容。这是对 AGENTS"无 userId=1 特权"最诚实的实现方式。
- 错误契约统一：`FoundationErrorAdvice` 把 `ServiceException` 映射为 401/403/422，方法/媒体类型 405/415，上传超限 413，兜底 500，全部带 `requestId`，且 `include-message/stacktrace/binding-errors: never`。

**扣分项**

- `authorizeHttpRequests(requests -> requests.anyRequest().permitAll())` + 拦截器兜底。ADR-003 已如实披露"新增非 MVC Servlet/WebSocket 需要独立审核"，但这是一个**依赖人工纪律的安全模型**。建议后续加一个启动期断言：扫描容器内所有 `Servlet`/`Filter`/`WebSocketHandler` Bean，未在白名单内即启动失败。
- 未迁移的能力（登录限流、密码策略、TLS、SSO）在 ADR-003 已列明未实现，属于诚实声明；但作为"base"，**投产前必须有明确的加固清单**，否则容易被误当成"已经安全"。
- `SystemFileController` 的 5 个方法全部是 `AUTHENTICATED` 而非 `AUTHORIZED`，授权下沉到 `SysAttachmentServiceImpl.metadata()` 的 owner/scope/resource 三要素校验 —— 校验逻辑本身正确（`!owned && !notice` → 404），但**读起来像"文件接口无权限"**。建议至少加注释或在接口层补 resource 级权限，降低误读与被后续开发照抄的风险。

### B4 事务一致性与同步互斥 — 8.0

**做得好**

- 两个事务管理器具名（`masterTransactionManager` / `businessTransactionManager`），Service 显式指定 `transactionManager`；跨中心/业务事务双向拒绝在 `checkOwner` 里实现（`owner != expected` 或"已有实际事务但无 owner"→ 409）。
- `SpringCommitActions` 提供 `afterCommit/afterRollback`，`ManagedDatasourceService` 用它做池退役与 DDL 缓存失效 —— 副作用延后到提交后，不会被回滚的事务误触发。
- `SyncCoordinator` 完整实现 AGENTS 8.1：`ensure → recover(stale) → claim`，`run_token` 校验贯穿 `progress/succeed/fail`，`changed(n)` 强制 `n==1` 否则 `SYNC_EXECUTION_LOST`，`assertOwner` 让旧执行者主动停下。设计是对的。

**扣分项**

- **同步协调的关键路径缺少自动化验证**：4 个 skipped 测试正是 opt-in 的真实 MySQL 用例（含 `ManagedRuntimeMySqlTest`、`WorkspaceAccessMySqlTest` 等），而 `SyncCoordinator.progress/succeed/fail/assertOwner` 在 `src/test` 中我未找到覆盖。AGENTS 8.1 要求的"多节点同流竞争只有一个执行者、旧令牌更新被拒绝、崩溃超时恢复"目前**只有设计，没有可复现证据**。这是 Base 阶段可以接受、但进入业务前必须补的。
- `SyncCoordinator.claim()` 中 `recover` 与 `claim` 两次条件更新在同一个 `PlatformPersistence.run(...)` 事务里，逻辑正确；但 `recover` 成功后旧执行者是新 token 的持有者之外的人，缺少"恢复事件写审计"的入口，运维可观测性偏弱。
- `Server.destroyMethod="shutdown"` 的 Quartz `Scheduler` 是 `StdSchedulerFactory` 手工构造的 RAM JobStore，多节点 cron fire 去重未交付（README 已如实标注）。

### B5 安全与密钥管理 — 3.0（**P0，必须处理**）

**做得好**

- `DatasourcePasswordCipher` 用 AES/GCM/NoPadding + 每次随机 12 字节 IV，强制 32 字节 Base64 密钥，密文长度与格式校验到位。
- 敏感字段防泄漏做了三件事：`DatasourceForm.toString()` 返回 `[REDACTED]`、password 字段 `@JsonProperty(WRITE_ONLY)`、`DatasourceView` 不包含密码字段。
- 文件下载带 `X-Content-Type-Options: nosniff` 且强制 `Content-Disposition: attachment`，`resource` 白名单限定 `personal/avatar/notice-media`。

**P0 问题：真实凭据已提交进 Git，并已存在于远端 `origin/main`**

实测证据（未打印任何凭据值，仅判定类型）：

| 检查 | 结果 |
| --- | --- |
| `git ls-files \| grep application-.*yml` | `application-dev.yml`、`application-prod.yml` **被 Git 跟踪** |
| `.gitignore` | 仅含 `target/ .idea/ *.iml .env .local/`，**不含**这两个 profile |
| `.git/info/exclude` | 无相关规则 |
| `git show origin/main:.../application-dev.yml` 中非空 `password` 行 | **3 行** |
| 非空 `datasource-key` 行 | **1 行**（32 字节 AES 主密钥） |
| 值类型判定 | 主机为**外部地址**（非本机/内网）；username 12 字符、password 54 字符、key 46 字符，均**非占位符特征** |
| 远端 | `origin = https://github.com/codeinsightlab/mes-base.git`；`git branch -r --contains a16ce71` 命中 `origin/main` |

同时 README 第 9 行声称："真实连接保存在 **Git 忽略**且权限为0600的 `application-dev.yml`，不随 Maven target 清理"，第 43 行声称"两个真实配置在本机保留、权限0600，并**已取消Git跟踪**"。**该声明与事实不符。**

影响面：`datasource-key` 是 `mf_datasource` 中所有业务库密码密文的主密钥，泄漏等于全部托管数据源凭据可解。仓库经查为私有（匿名访问 GitHub 返回 404），暴露面限于有仓库权限的人，但**密钥已进入历史，必须按已泄漏处理**。

> 处置建议（需你授权，本次未执行）：① 立即轮换远程测试 MySQL 账号密码与 Redis 口令；② 生成新的 `mes.runtime.datasource-key` 并重加密 `mf_datasource` 密文；③ 两个 profile 改为本地忽略文件（`.gitignore` 增加 `application-dev.yml`/`application-prod.yml` 并 `git rm --cached`）；④ 决定是否用 `git filter-repo` 重写历史并强推（需评估协作方影响）。参考 `config/application-dev.example.yml` 作为仓库内模板。

### B6 测试与可验证性 — 8.0

**实测结果**（本次亲自执行，非引用文档）：

```
JAVA_HOME=corretto-21.0.4 mvn -B -ntp verify
→ Tests run: 172, Failures: 0, Errors: 0, Skipped: 4
→ Reactor: mes-common/mes-system/mes-framework/mes-quartz/mes-generator/mes-admin 全部 SUCCESS
→ Total time: 18.8s
```

**做得好**

- 测试层级配置合理：`DedicatedDatabasePersistenceTest`(20) 用真实 H2 JDBC 验双厂同号隔离、换厂、换连接/版本、嵌套回滚、代理逃逸；`FoundationHttpTest`(14) 在真实嵌入 Tomcat 上发 HTTP 验 401/403/400/422/409；`PersistenceAssemblyTest` 断言生产装配的**精确 Bean 名称与数量**；`ArchitectureBoundaryTest` 用源码扫描强制 `dev.mesbase.core` 不含 `ThreadLocal`/`org.springframework`/`dev.mesbase.infrastructure` 等，并断言 reactor 父 pom 不向所有模块注入 dependencies。
- 测试不是形式主义：`SystemResponseHttpTest`(46) + `SystemResponseContractTest`(18) 覆盖响应契约，`MapperRegistrationGuardTest` 覆盖包重叠/重名 Mapper 启动失败。
- 测试目录结构清晰：`persistencefixture/{platform,business,conflict,duplicate}`、`responsefixture`、`invalidfixture` 明确区分"生产的"与"TEST ONLY 探针的"，且 `ArchitectureBoundaryTest` 断言探针不出现在生产源中。

**扣分项**

- **无 CI 配置**（两个仓库都没有 `.github/` / pipeline），Lint+Test+Build 无门禁，"verify 通过"依赖人工记得跑。
- 4 个 skipped 恰恰是最有价值的真实 MySQL 用例；`-DskipTests` 之外的 opt-in 机制没有文档化的统一入口。
- 无覆盖率数据，无法量化"业务 Service 层"的验证深度。
- 前端测试本次未能独立复现（命令被拒绝），子代理报告 `vitest run` 存在 worker 超时（15/16 文件），需在你方标准环境复跑确认是环境限制还是配置问题。

### B7 文档与治理一致性 — 7.0

**做得好**

- 治理密度很高：21 份 md / 4,028 行，6 篇 ADR，每篇带明确的"决定 / 候选方案 / 拒绝方案 / 尚未覆盖"，`docs/reviews/*.json` 存机器可读证据，`docs/legal/` 保留 RuoYi 与 Source Product 的 MIT 许可与逐文件来源清单。这在同类项目里属于上乘。
- **诚实度值得表扬**：ADR-004 明确写 "Shared implementation pending"、ADR-005 标注 "Superseded (2026-10-09)" 并保留追溯、多处主动写明"未验证边界/不能证明"。评审中我最看重的就是这种不自我美化。
- AGENTS.md 第 8.1 节的同步互斥规则、第 9 节的持久化拓扑、第 5.1 节的显式 import 规则都可执行、可检查，不是空话。

**扣分项（这也是 B7 失分主因）**

- **同一份文档反复叠加历史层，读者无法判断"当前是什么"**。`docs/architecture/README.md` 依次出现"2026-10-06 当前结构…六模块 + `integration.erp` Port""2026-10-06 当前结构与瘦身审计…七个 JAR 模块 + platform/business/erp 三组工厂""2026-10-09 当前持久化结构…"三段互相否定的"当前"。README 正文与 ADR 也各有大量"此前…现由本节覆盖"。
- `docs/reviews/2026-10-06-server-package-review.md` 仍把"三类 SqlSessionFactory""ERP 路由""ERP 池创建"当作当前事实描述，而 ERP 已于 2026-10-09 全部移除（实测：全仓 `mes-*/src/main` 中 `erp/Erp/ERP` 关键字命中 **0 次**）。
- README 关于凭据 Git 状态的声明与事实相反（见 B5）。
- `docs/development/next-stage-plan.md`（395 行）本质是 append-only 工作日志，"有效计划"藏在文件中部，新会话入口成本高。

### B8 工程卫生 / 可维护性 — 6.0

| 问题 | 实测证据 | 严重度 |
| --- | --- | --- |
| **当前状态未提交** | `git status --porcelain` = 63 项（50 M / 11 D / 2 ??）；HEAD `cb0b34b` 仍是"init"，最近提交 2026-10-06。ERP 移除 + 双工厂收敛这一整轮成果**尚未进入版本控制** | P1 |
| **30 个空目录遗留** | 含仓库根整棵死骨架 `src/main/java/dev/mesbase/{common,core,infrastructure,integration/erp,platform,server}/**`（模块化改造后未清理）；`mes-framework/.../integration/erp`、`docs/development/templates/erp`（ERP 移除残留）；`mes-admin/.../web/view/{config,notice}`、`mes-admin/src/test/.../{persistencefixture/erp, platform/security, platform/management}` | P2 |
| **基线携带死 Schema** | `V1__platform.sql` 建的 14 张 `sys_*` 表，在 `V2__source_product_system.sql` 中全部 `RENAME TO phase1_legacy_sys_*`。**任何新装实例都会长出 14 张无人读取的 `phase1_legacy_*` 表**。V1 头注释自称 "PostgreSQL / H2 acceptance dialect"，实际只是 V2 的铺垫 | P1 |
| **全限定类名违反自家规则** | 38 处 `@dev.mesbase.platform.web.EndpointAccess(...)` 分布在 10 个文件（SysConfigController 8、OpenApiClientController 9、SysNoticeController 5、SysJobLogController 5、OpenApiDiagnosticsController 3…），直接违反 AGENTS §5.1"禁止用全限定类名代替正常导入" | P2 |
| **双装配并存** | `mes.system.enabled=false`（默认）与 `true` 两套 Bean 图并存：`InMemoryFactoryCatalog(空)`、`AuthorizationService→false`、`UserDetailsService→抛异常`、`ConfiguredBusinessPersistenceResolver(Map.of())` vs 真实链路。默认值 `false` 意味着**裸启动出来的是空壳**，容易被误读为"能跑" | P2 |
| 依赖声明不诚实 | `mes-admin/pom.xml` 引入 `postgresql` driver + `flyway-database-postgresql`（runtime），但 `V2–V15` 全是 MySQL 方言（`AUTO_INCREMENT`/`ENGINE=InnoDB`/`ON UPDATE CURRENT_TIMESTAMP`），`physicalIdentity()` 用 `SELECT @@server_uuid`，`DatasourceDdlWriter` 也是 MySQL 路径。**PostgreSQL 实际不可用**，属死依赖 | P2 |
| 注入风格混用 | 36 个文件 89 处 `@Autowired`（含 `SysAttachmentServiceImpl` 的字段注入），与构造注入的现代写法混用 | P2 |
| 死代码兼容层 | `isAdmin()` 三处 `return false` 保留调用点（设计正确但需注释说明"故意永假"）；`OwnerMapperRegistration` 仍是手写 `BeanDefinitionRegistryPostProcessor` 而非纯 `@MapperScan`（虽已改用标准 `ClassPathMapperScanner`，意图达成） | P3 |

**做得好**：`TODO/FIXME/HACK` 命中 0；`printStackTrace` 仅 1 处（`ServletUtils`）；`ReentrantLock` 0 处；`System.getenv` 0 处（配置全走 profile）；无 `JdbcTemplate` 绕过 MyBatis。

---

## 三、前端 mes-base-ui 评分明细

| # | 维度 | 权重 | 得分 | 加权 |
| --- | --- | --- | --- | --- |
| F1 | 技术栈与工程化 | 15% | 8.5 | 1.28 |
| F2 | 统一基础设施收敛度 | 25% | 5.0 | 1.25 |
| F3 | 权限 / 租户前端实现 | 20% | 8.0 | 1.60 |
| F4 | 契约贴合度与类型安全 | 20% | 4.5 | 0.90 |
| F5 | 测试与构建门禁 | 20% | 6.5 | 1.30 |
| | | | **合计** | **6.33** |

**规模**：`src` 下 70 个 `.vue` + 51 个 `.ts`，共 13,101 行；`tests` 16 个测试文件。

### F1 技术栈与工程化 — 8.5

Vue 3.5.43 / TS 5.9（`strict: true`）/ Router4 / Pinia3 / Element Plus 2.14.7 / Vite 8.3.2 / Vitest 5，`.nvmrc=22`、`engines>=22.18`。ESLint 10 flat config（含 `@stylistic`）。目录分层干净：`api / components / composables / lib / router / stores / utils / views`，`lib/` 下有 `request.ts / crud.ts / dynamicRoutes.ts / sessionStorage.ts / status.ts / dataPresentation.ts / workspaces.ts` 等基础设施模块，结构意图清晰。扣分：无 Prettier、无 `.github` CI；`no-explicit-any` 在 ESLint 中被显式关闭（见 F4）。

### F2 统一基础设施收敛度 — 5.0（前端最大短板）

- **`CrudPage.vue` 只有 1 个使用方，而该使用方是死代码（2026-10-09 二次核验后修正）**：`grep -rl CrudPage src` → 仅 `src/views/PlatformPage.vue`；而 `PlatformPage.vue` **既不在 `router.ts` 的静态路由中**（实测静态路由只有 register / autocode parts / inbox / login / files / profile / user-auth / role-auth / dict-data / job-log / home / 404），**又被 `lib/dynamicRoutes.ts` 显式排除**（`!menu.path.startsWith('/platform/')`）。它配套的 `src/lib/platform.ts` 定义了 users / roles / menus / dict-types / dict-data / configs / jobs / audit / login-logs / job-logs 一整套 CRUD，全部指向 `/api/platform/{resource}` —— 而后端 `/api/platform/**` 实测**只有 `session`（2 个方法）和 `files`**，其余端点不存在（docs 也记载"旧简化 Platform 实现已删除"）。**结论：`CrudPage.vue` + `PlatformPage.vue` + `lib/platform.ts` 是旧简化平台的残留 UI，不可达且指向已删除的后端。**
- **分页契约实际只有一套在生产运行，但生成器指向了另一套**：`items/offset/limit` 在后端**没有任何生产实现**（全仓 `mes-*/src/main` 中 `items` 仅命中 `RuoyiTemplateRenderer.java:47` 的模板字符串）。生产的 18 个 Controller 全部返回 `TableDataInfo(rows/total)`（含 `ManagedDatasourceController.list`，用的是 `getDataTable(...)` 而非 `items`）。而**代码生成器**产出的是另一套：`RuoyiTemplateRenderer` 生成 `XxxPageVo(items,total,offset,limit)`，`codegen/base/api.ts.vm` 声明 `Page { items; total; offset; limit }`，`codegen/base/index.vue.vm` 直接 `import CrudPage from '@/components/CrudPage.vue'`。**所以生成器是"新契约 + 死壳"的组合**：生成产物自身自洽（后端 `PageVo` + 前端 `items` 成对），但与 27 个手写迁移页面的 `rows/total` 完全不通约，且复用的是那个已死的 `CrudPage`。这才是"统一 CRUD 壳未落地"的真正机制 —— 不是没人用，而是**壳和新契约都悬在半空，没有一条真实后端链路支撑**。
- 巨型页面未拆：`views/system/user/index.vue`(731)、`role/index.vue`(634)、`components/Crontab/result.vue`(560)、`monitor/job/index.vue`(532)、`system/menu/index.vue`(449)。均为 Source PORT 原样搬入的产物。

### F3 权限 / 租户前端实现 — 8.0

`stores/auth.ts` 实现平台/工厂两级权限（`hasPermission(code, scope)`），`selectFactory` 切厂时清理旧数据并重新拉取 `factory-navigation`；`lib/sessionStorage.ts` 只持久化 token / expiresAt / factoryId 并做有效期校验，**不存密码、菜单、权限** —— 与后端 AGENTS 声明一致。`lib/request.ts` 按 scope 注入 `X-Factory-Id`，401 触发 `unauthenticated()` 清理，并用 revision 守卫防止过期响应回填。`lib/dynamicRoutes.ts` 只允许注册已编译的 `views/**/index.vue` catalog，杜绝服务端下发任意组件路径 —— 这一点做得比大多数后台模板好。

### F4 契约贴合度与类型安全 — 4.5

- **`any` 泛滥**：`src/api` 下 123 处 `any`，全 `src` 共 148 处 / 27 个文件。`api/system/user.ts` 15 处、`datasource.ts` 14 处、`role.ts` 14 处；`listUser(query: any)`、`getUser(userId: any)` 等签名完全没有按后端契约定义类型，字段靠猜。
- 与 B8 的"全限定注解"同源：这是**迁移期"先让它跑起来"的欠账**，不是设计错误，但作为 Base 会直接被复制 38 次。
- 值得肯定：`lib/request.ts` 是**单一路径**的 `fetch` 封装（`utils/request.ts` 只做 query 拍平），没有出现"多结构兜底"（无 `rows ?? data ?? items` 之类猜测），这守住了 AGENTS 第 15 节"不新增多结构兜底"的底线。

### F5 测试与构建门禁 — 6.5

16 个测试文件覆盖 `request`、`permissions`、`sessionRestore`、`dynamicRoutes`、`crud`、`workspaces`、`dataPresentation`、`apiEnvironment`、`eslintRegression` 等，主题选择是对的（守基础设施而非页面细节）。`vue-tsc --noEmit` 在 `strict` 下通过（子代理实测）。扣分：**无 CI**；本次未能独立复现 `npm test`（命令被拒绝），子代理报告 16 个文件中 15 个因 vitest worker 超时未完成、仅 `request.test.ts` 跑通 14 例，该现象需在你方标准环境复跑确认；`npm run build` 在我与子代理两次尝试中均触发本机安全护栏（`dist/` 批量删除保护），属**本机环境现象而非源码缺陷**，但"默认构建命令在干净 dist 下不能一键完成"这件事本身对 CI 就绪度是减分项。

### 遗留

`src/views/mes/pro/personnelAnalysis`、`src/views/mes/pro/productionMonitor/components` 为空目录（V15 已从 DB 删除对应菜单，向后端归档保持一致）；未发现 ERP 残留、未发现注释掉的大段代码、未发现 TODO/FIXME。

---

## 四、"能否作为 Base 开启"的判定

### 判定：有条件放行（Conditional Go）

**架构方向正确、设计质量达标**，三个核心命题（Factory 单架构、中心库+多业务库、HTTP 显式分类）不仅想清楚了，而且用可运行代码 + 可执行测试证明了，没有退化成"文档架构"。这在 RuoYi 派生的多厂产品里属于上游水平。

**但当前这个 commit 状态不能直接开启业务开发**，必须先清以下阻断项：

| 级别 | 阻断项 | 为什么阻断 | 建议动作 |
| --- | --- | --- | --- |
| **P0** | 凭据 + 主密钥已提交并存在于 `origin/main` | 主密钥泄漏 = 全部托管数据源凭据可解；且 README 声明与事实相反 | 轮换 DB/Redis 凭据 → 换 `datasource-key` 并重加密 `mf_datasource` → 两 profile 移出跟踪并加 ignore → 评估历史重写 |
| **P1** | 63 项改动未提交，HEAD 仍是 ERP 时代 init | 没有可回滚的基线，"Base"不存在于版本库 | 在清理 P0 之后、清理死目录之后，做一次权威提交并打 tag（如 `base-0.1`） |
| **P1** | 新装实例带 14 张死表 `phase1_legacy_sys_*` | Base 的 Schema 基线必须是干净的；否则每个新环境都继承历史垃圾，且 Flyway 后续无法清理 | 新基线：合并 V1+V2 为单一 V1（或让 V1 直接建 Source Schema），把 legacy rename 段移入"仅升级路径"的可选迁移 |
| **P1** | 前端生成器指向死壳 + 新契约无后端支撑 | 生成器产出 `items` 契约并复用不可达的 `CrudPage.vue`；手写页面用 `rows/total`。业务开发一铺开，两条路各走一半，两边都不通约 | 二选一并**只在全仓存在一套**：推荐保留生产的 `rows/total` 并把 `api.ts.vm` / `index.vue.vm` / `RuoyiTemplateRenderer` 改齐，或反过来给 `items` 补真实后端实现。同时删除 `PlatformPage.vue` / `lib/platform.ts` / 无引用组件 |
| **P2** | 30 个空目录 / 死骨架 / PostgreSQL 死依赖 / 38 处 FQN / 双装配默认值 | 不影响正确性，但持续误导读者与后续开发者 | 清目录、删死依赖、按 §5.1 统一 import、把 `mes.system.enabled` 默认值改为显式 |

**当前可以立即开始的部分**：`dev.mesbase.core` 的领域模型、`web/view/**` 的 Result/Assembler 约定、`OwnedTransactionManager` + `RuntimeRegistry` 的隔离契约、`@EndpointAccess` 的分类纪律 —— 这四项已经稳定，业务开发可以直接依赖，不必等 P0/P1 清理完成（但**不要**在清理前继续新增依赖 `phase1_legacy_*` 或旧分页契约的代码）。

**"地基验收"缺口（进入业务前应补，不等于 P0/P1）**：
1. `SyncCoordinator` 的多节点抢占/令牌失效/崩溃恢复需要真实 MySQL 的可复现证据（AGENTS 8.1 的验收清单目前无测试）。
2. `Shared`（多厂共库）保持 DESIGNED，业务不得假设可用。
3. 前端 `npm test` / `npm run build` 需在标准 CI 环境复跑确认。
4. 登录限流、TLS、生产级密码策略（ADR-003 已声明未实现）需在首次对外暴露前完成。

---

## 五、未验证边界

- 未启动后端服务，未连接远程 MySQL / Redis，未执行任何真实 CRUD、选厂、跨库事务或权限负例；本次运行证据仅限 `mvn -B -ntp verify`（172/0/0/4）。
- 未重跑前端 `npm test` / `npm run build`（命令被拒绝），前端测试结论部分依赖子代理报告，已标注。
- 未做浏览器验收，未验证 UI 与真实后端契约的端到端一致性（"rows vs items"分叉的实际影响因此未能定论）。
- 未执行打包 smoke（`scripts/smoke-packaged.py`）、未执行 `scripts/format-java --check`。
- Git 远端可达性、仓库可见性仅通过匿名 HTTP 探测推断为私有，未验证组织权限配置。
- 本报告为只读评审结论，**未修改任何源文件、配置、数据库或服务**，未提交、未推送、未部署。


## 六、2026-10-09 后续复核说明

本轮按用户阶段决定先整改 Base，生成器/UI 契约改造留到业务接入。同步 MySQL 用例确实存在并含双独立 JVM 探针，默认跳过只能证明本轮未启用，不能证明无测试；新增控制流程单测不等于真实多节点验收。仓库 private 经登录态 GitHub API 确认，凭据仍存在于历史/本地 origin/main 快照；本地取消跟踪不关闭历史风险。

不采用仅凭行数、空目录、Prettier 缺失或主观分数判断架构准入；保留迁移功能、两入口持久化和既有授权/事务守卫，不改 Flyway 旧 checksum、不实现 Shared、不以 tag 替代验收。完整逐项采纳/不采纳理由与实测收据集中维护在行动方案 §五，避免平行清单。

## 七、2026-10-12 多节点部署与横向扩容复核

### 7.1 结论、范围与证据

**当前具备扩展为多节点的主架构，但不能直接将完整实例复制多份并宣称集群就绪。**
普通 HTTP 会话、当前权限与 Factory 请求上下文具备跨节点工作的基础；调度一致性、本地文件、物理库重复绑定防护、共享缓存发布与部署运维仍有边界。
不需要因为横向扩容重建一套 Core 或拆为微服务。

- 对象：`mes-base` 的 `main` / HEAD `c49bd0c` 加当时已有未提交改动；前端仅追踪消息页的推送与轮询消费者。
- 源码事实：生产装配 → HTTP/Service → Redis/Mapper/事务/文件适配器；本节列出可定位的文件与行号。
- 测试事实：本轮运行 6 个测试类、25 项，0 failure / 0 error / 0 skipped，BUILD SUCCESS。Redis 为测试自行启动的临时实例；SQL 为 H2；部分池探测为 Mock。
- 未验证：两套完整应用进程、负载均衡、真实 MySQL 并发/故障、MinIO 外部实例、Redis Cluster、滚动发布、生产容量。
- 本轮未修改业务代码、配置或数据库，未启动/停止用户服务，未提交、推送或部署；只追加本报告。既有工作区改动保留。

### 7.2 已有的多节点基础

| 能力 | 当前源码事实 | 边界 |
| --- | --- | --- |
| 身份会话 | `TokenService:56-117` 从共享 Redis 恢复随机 Bearer；每请求由中心库读取账号状态、认证版本、角色；Security 为 STATELESS | 同一集群必须共享中心库、Redis DB 和完整缓存命名空间；不要求用粘性会话保存身份 |
| 撤销会话 | `SessionRevocationService:41-103` 在账号事务中入 outbox；Redis Lua 按版本撤销，失败重试；账号版本在 Redis 交付失败时仍拒绝旧身份 | 多节点重试可重复交付，Lua/条件确认负责幂等；重复轮询会增加中心库/Redis 负载 |
| Factory 与事务 | `ManagedDatasourceService:426-481`、`RuntimeRegistry:164-180` 固定请求绑定、事务租约；每节点独立建池 | 本地池和 ThreadLocal 属于请求执行资源，本身不意味着不能部署多节点；已获租约事务按约定继续完成 |
| 通用同步协调 | `SyncCoordinator:42-91`、`MasterSyncMapper:8-13`、`V12__master_sync_coordination.sql` 使用 factory + sync key 唯一键、条件抢占、run token、数据库时间恢复 | 当前没有接入真实同步执行器；不能将协调器等同于所有 Quartz 任务已具有集群语义，也不能隔离已发出的外部副作用 |
| 自动编码 | `SysAutoCodeGenerationServiceImpl:43-103` 在中心事务内调用 `AutoCodeExtraMapper.lockRule` 的 FOR UPDATE | 避免依赖 JVM 计数器；热点规则仍串行，编码业务约束和跨节点时钟需另验 |
| 共享文件适配入口 | `SystemConfiguration:125-130` 已有 local / minio 两个 FileStorage 实现 | 默认仍为 local；存在适配器不等于已完成共享存储部署 |

### 7.3 扩容发现与触发场景

#### M1 / P1：每节点独立调度，数据库运行锁没有计划触发去重

证据：`SystemConfiguration:96-120` 手工创建 StdSchedulerFactory，仅消费线程数并在每节点启动恢复任务；
`SysJobServiceImpl:65-71,103-145,203-245` 只修改当前节点 Scheduler；
`QuartzJobExecution:18` / `QuartzDisallowConcurrentExecution:20` 只把 job ID 交给 runner；
`JobClaimMapper:7-10` 抢占条件为 running_token IS NULL，释放后清空 token，没有计划触发时刻或持久化 fire ID。

由此推导：A、B 对同一次 Cron 先后触发，A 完成并释放后 B 仍能抢占，可能执行两次；慢任务重叠时只有一个能抢占，但不构成同次触发去重。
在 A 修改 Cron，B 可继续按旧 Cron 触发；在 A 新增的任务，B 未装载，A 故障后无法直接由 B 接手。
数据库状态检查会阻止已暂停/删除任务实际执行，不能误报为“暂停后必然继续执行业务”，但 B 的旧 trigger 仍存在。
进程崩溃留下 running_token 后，当前 sys_job 恢复需要暂停、匹配 token、超过 5 分钟及显式确认死亡；不是自动故障转移。
当前可执行目标仅两种 heartbeat，因此已确认的是调度基础缺口，不能声称真实 MES 同步已重复写入。

建议：先明确单一调度角色或持久化集群调度方案，补同次触发/业务幂等与恢复验收。
当前没有独立 scheduler enabled 开关；关闭 mes.system.enabled 会一起关闭系统能力，不可当作现成隔离方案。
也不能仅增加 spring.quartz 配置：自建 factory 不自动消费其余配置，且 init 中有 scheduler.clear()；接入共享 JobStore 前必须审查启动清理和任务同步语义。

#### M2 / P1（条件触发）：独立本地磁盘导致跨节点文件不可见

证据：`application.yml:48-50` 默认 local；`SystemConfiguration:125-130`；
`LocalPrivateFileStorage:27-49` 按本地 root 写文件，并在缺少文件或大小不符时返回 NOT_FOUND。
附件元数据共享并不会共享文件字节：A 上传、B 下载即可失败；A 故障后粘性会话也无法提供其磁盘内容。
共用可靠挂载可以消除这一条件，但当前配置本身未证明存在共享挂载。
建议使用已实现的私有共享 FileStorage 适配并迁移既有文件，补跨节点上传/下载/删除及节点失效验证。

#### M3 / P1（错误配置触发）：物理业务库防重复检查仅在节点内

证据：`V11__managed_factory_datasource.sql:16,27` 只约束 host/port/db name 文本和 datasource ID；
`ManagedDatasourceService:105,464-478,524-528` 用本地 physical map 对实际 server UUID/database 做冲突检查；
`RuntimeRegistry:88-91,113-117` 的归属状态同样是节点内。

两个不同主机别名可以指向同一个物理 schema，从而形成两个不同 datasource ID；A 节点只加载工厂 F1，B 只加载 F2 时，双方都看不到对方的 physical map。
业务隔离依赖独立数据库，此时可能让两厂访问同一批业务数据；同节点加载两份定义时才可能暴露冲突。
这不是已发现真实配置重复，而是静态确认的全局防护缺口。
上线前应核对所有绑定的物理身份，并使中心侧绑定校验能够执行全局隔离约束；不能把节点内 map 当作集群保护。

#### M4 / P2：参数和字典共享缓存可能被滚动启动或并发旧读取覆盖

证据：`SystemConfiguration:104-109` 每节点启动预热；`SysConfigServiceImpl:81-91,128-183` 在事务内写 Redis、无版本发布；
`SysDictTypeServiceImpl:132-139` 批量回填字典；`DictUtils:31-33` 与 `RedisCache:32-35` 写入不设置 TTL。

例：B 启动读到旧参数后暂停，A 更新参数并提交新值，B 随后预热将旧值覆盖，缓存不会自动过期。
事务提交前 Redis 发布也可能暴露未提交值；数据库回滚不撤回已经写入的缓存。
这是既有并发一致性风险，多节点和滚动发布增加触发机会。
建议另立小范围任务处理提交后失效、过期恢复和启动预热竞争；不与下述已确认的路由缓存取舍混为一谈。

#### M5 / 已接受边界：工厂路由不是即时全节点切换

证据：`FactoryMetadataCache:57-88`、`RoutingMetadataStore:26-35`、`application.yml:65-67`；
`ManagedDatasourceService:503-521` 仅定期核对本节点已初始化池。
路由定义固定 TTL 为 300 秒，允许失效前开始的读取在失效后回填；淘汰失败记录日志并等过期。
TTL 从 SET 起算，不能承诺管理提交后最多 300 秒全部生效；慢读和已准入长请求可扩大墙钟窗口。
5 秒池核对不是全节点准入屏障，旧缓存仍可能重新建池；已获租约事务继续使用原连接。

这是 2026-10-11 用户确认的取舍，本轮不建议恢复已删除的代际/outbox 协议。
部署与运维必须接受停用/改绑收敛窗口，真正切库安排停止新写入和排空；不能宣称在线迁库无损或即时撤销全部事务。

#### M6 / P2：跨节点消息实时通知缺失，轮询可恢复

证据：`MessageEventHub:13-20` 仅保存本进程 SSE 连接；`SysMessageServiceImpl:127-129` 提交后调用本进程 events.changed；
`mes-base-ui/src/views/Inbox.vue:8-9` 只处理 inbox-changed，页面可见时每 30 秒重新读取。
A 写消息、用户 SSE 在 B 时收不到即时事件；数据库消息仍存在，可由轮询/手动刷新读出，不能写成消息丢失。
同一用户粘性路由不能确保其他发送者或后台任务与接收者落在同节点。
建议按产品时效要求选择跨节点事件传播或明确接受轮询；同时验证代理 SSE 超时/缓冲与重连。

#### M7 / 容量风险：连接池和中心准入负载随实例数增加

证据：`DruidPools:36-59` 每个物理池独立创建；`application.yml:21-25` 的 initial/minIdle/maxActive 为 5/10/20；
`RuntimeRegistry` 正常启用池没有按工厂闲置时间卸载机制；每节点 reconcile 与 session outbox 重试独立运行。
若 N 个节点都访问 F 个工厂，稳定版本、没有退休池和临时探测池时，总连接配置上界约为 N × (1 + F) × 20。
例如 4 节点、10 工厂，上界约 880，分布在中心库及各业务库；每个库约 80，不是单库 880。排空期间新旧池并存会进一步增加连接。
这是配置预算，不是实际连接数、吞吐或容量测量。中心库仍承担每请求账号/权限读取；同一个工厂数据库不会因为应用实例增多而自动分片。
扩容前按每库 max_connections、实际热工厂数、CPU/慢 SQL 和 Redis 延迟确定池配额，做阶梯压测。

#### M8 / 部署前提与故障接管缺口

- `SystemCacheNamespace:18-26` 始终将完整中心 JDBC URL 的 SHA-256 加入 Redis 前缀，即使显式配置 namespace 也如此。同一库使用不同别名、参数顺序或 URL 参数会分裂会话/验证码/缓存。集群须统一完整 URL、namespace、Redis 实例及逻辑 DB；当前本机 profile 不能直接当多机模板。
- `FactoryRuntimeConfiguration:61-64` / `DatasourcePasswordCipher:37-44` 要求节点共享正确的数据源密钥；密钥不一致会出现只有部分节点能连接业务库。时区、时钟、版本和缓存模型也需统一。
- `FoundationController:25-26` 的 /health 固定返回 UP；它不检查本节点到中心库/Redis 的可用性。若负载均衡只用它，依赖失联节点仍可能接流量。未找到已交付的负载均衡/readiness/滚动发布拓扑；没有据此断言框架完全不支持优雅退出。
- `DruidMonitorService` 和 DDL 文件快照属于节点内观测，负载均衡后要保留节点标识，不能当作集群总量。
- 应用多节点不等于 Redis 分片集群就绪。`UserSessionStore:18-48,58-86` 的 Lua 同时访问 session/epoch/index 键，键未设计同一 hash tag，撤销/注销脚本还有动态键访问。Redis Cluster 要求一次 Lua 的相关键在同槽，不能直接切换分片模式；参见 [Redis 官方集群说明](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)。本轮未运行 Cluster，不把共享单实例 Redis 测试称为分片兼容验证。
- 没有发现覆盖所有业务写请求的通用幂等机制；负载均衡故障转移不能无条件重放非幂等写请求。中心库/Redis 自身高可用、恢复后的会话/任务状态还需独立验收。

### 7.4 建议的最小落地顺序（尚未实施）

1. 保持单代码与中心库 + 多业务库，先统一集群配置、共享私有文件、物理库绑定核对及连接预算。
2. 隔离调度职责或完成持久化集群调度，解决定义同步、同次触发重复、崩溃恢复；接真实执行器时补业务幂等。
3. 修补参数/字典缓存发布竞争；消息按实时性要求补传播或明确轮询边界；路由缓存沿用已确认的一致性合同。
4. 补依赖就绪检查和摘流/排空；用同一中心库、同一共享 Redis 的两个完整应用进程、真实业务数据库与负载均衡完成验收。

双节点验收至少覆盖：A 登录 B 访问；A 改密/禁用 B 拒绝后续旧会话；同 Token 并发不同 Factory 无串厂；别名指向同物理库拒绝；A 上传 B 下载；同次 Cron 去重、跨节点修改 Cron 和 kill 后恢复；配置变化按约定收敛且旧租约正确排空；滚动启动不回填旧参数；某节点依赖断连被摘流；负载逐级增加时测每库连接数/中心 SQL/Redis 延迟。

### 7.5 本轮实际验证回执

```sh
mvn -B -ntp -pl mes-admin -am -Dtest=SyncCoordinatorTest,SessionAdmissionTest,IndexedRedisSessionTest,RoutingMetadataRedisTest,RequestSnapshotTransactionTest,ManagedColdPoolTest -Dsurefire.failIfNoSpecifiedTests=false -Dmes.test.redis=true test
```

| 测试 | 数量 | 本轮结果与证据等级 |
| --- | --- | --- |
| SyncCoordinatorTest | 8 | 通过；Mock 控制流，非真实 DB 竞争 |
| SessionAdmissionTest | 7 | 通过；H2 账号版本/outbox 与 Mock Redis |
| IndexedRedisSessionTest | 1 | 通过；自有临时 Redis 的真实 Lua，并发发行/撤销/注销 |
| RoutingMetadataRedisTest | 1 | 通过；两个独立 Redis 客户端 + H2，固定 TTL、允许旧回填、故障拒绝 |
| RequestSnapshotTransactionTest | 3 | 通过；H2 固定租约、退休、并发上下文 |
| ManagedColdPoolTest | 5 | 通过；H2/Mock 物理探测的冷池准备与清理 |

合计 25 通过，0 失败/错误/跳过；Maven 用时 12.676 秒。日志 `/tmp/mes-base-multinode-audit-20261012.log`。
日志里的退休池关闭失败和缓存淘汰失败属于测试注入，不能误报生产故障。
现有 `ManagedRuntimeMySqlTest` 包含双独立 JVM 抢占探针，但本轮未运行；没有新增测试，也未执行全量 verify、打包 smoke 或前端 test/build。
本轮测试只复核已有基础能力；M1/M2/M3/M4/M6 的具体跨节点故障场景是源码推导，未冒充部署复现。
文档追加后 `git diff --check` 通过；本轮仅新增本报告的顶部导航与 §七，已有源码、配置和前端工作区修改未作处理。
