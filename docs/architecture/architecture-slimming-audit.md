> 2026-10-09：以下为当时架构快照。当前持久化已收敛为中心库 + Factory 多业务库；外部连接、第三工厂及相关测试退出，中心工厂名改为 masterSqlSessionFactory。当前规范与验收见 ADR-004 的 2026-10-09 修订。历史结论不作为现行接入规则。

# Architecture Slimming Audit

审查日期：2026-10-06。基线：`main` / `342bf4d`，包括任务开始时用户已有的暂存修改。
项目实际名称和路径为 `mes-base`。本轮只检查架构及基础设施，不判断 MES 业务逻辑、SQL、API 或页面是否正确。
审查期间工作区另有监控/工作空间授权改动进入，均保持原样。第 9 节区分本轮减法与最终工作区快照，最终验证包含该快照。
验收状态：**PARTIAL**。本轮审计和证据充分的减法已落地；后续全量 verify 出现新增用例失败和测试编译失败，工作区测试仍在并发变化，见第 9 节，不能宣称当前工作区全绿。

证据分级：下文未另标注的事实来自当前源码、配置、引用搜索和 Git；测试为本地单元/Mock/H2 fixture；打包启动为独立本地进程。
真实开发数据库、SQL Server、Redis、MinIO、部署和生产行为不在本轮验证范围。源码复杂不等于应该删除。
先完成本报告的发现与删除证据，再执行第 3 节限定的减法。最终执行结果追加在第 9 节。

## 1. Current Architecture Map

### 1.1 Modules

根 POM 聚合七个 JAR 模块，加根 POM 共八个 reactor 项目：

```text
mes-common
  <- mes-system
    <- mes-framework
      <- mes-quartz / mes-generator / mes-mes
        <- mes-admin (Boot executable)
```

`mes-admin` 直接依赖 `mes-mes`、`mes-quartz`、`mes-generator`；后三者通过 framework/system 引入 common。
`core` 值对象及 Port 位于 mes-common，不引用 Spring/ThreadLocal；但 common 的 Maven 依赖本身仍很宽，见第 7 节。
启动类 `dev.MesBaseApplication` 只扫描 `dev.mesbase`，开启 `exposeProxy=true`。旧根 `src/main` 尚有空目录，**没有任何文件**，不再是生产源码。

### 1.2 DataSources / SqlSessionFactory / Mapper binding / TransactionManager

装配事实来自 `server/PersistenceConfiguration`、`FactoryRuntimeConfiguration`、`DruidPools`，及 `infrastructure/persistence` 下的类。

| 启用状态 | DataSource Bean 数 | SqlSessionFactory Bean 数 | TransactionManager Bean 数 |
| --- | ---: | ---: | ---: |
| 仅基础配置，显式 default/test profile 且 system=false | 0 | 0 | 0 |
| system=true，erp=false | 2 | 2 | 2 |
| system=true，erp=true | 3 | 3 | 3 |

基础关闭模式还会程序式创建一个 `DedicatedDatabasePersistence` 内部的 business 数据源/工厂/管理器，但不注册为这三类 Bean；目录和池为空，访问不会降级到平台库。
因此 **Bean 数不等于全部 Java 对象数，也不等于物理连接池数**。

`application.yml` 的 `spring.profiles.default=dev`，所以无参数启动不能等同于上述关闭模式。当前 dev/prod 文件均声明 system/platform/runtime=true；基础 ERP 开关为 false，实际部署可通过额外配置/环境覆盖。
本轮只报告配置键/启用开关及敏感字面量的存在性，不输出真实连接值或凭证；未对用户正在运行的服务做 Bean 远程 introspection。表中数量是条件装配事实，开启三组的 H2 fixture 与关闭模式的打包进程分别验证。

| Bean | JDBC 实际职责 | SessionFactory | 固定扫描包及当前生产 Mapper 数 | 管理器 |
| --- | --- | --- | --- | --- |
| platformDataSource | 单个固定 Druid 平台池 | platformSqlSessionFactory | system/quartz/platform，33 | platformTransactionManager |
| businessDataSource | FactoryBusinessDataSource；依据当前 Factory 租借 RuntimeRegistry 的版本化池 | businessSqlSessionFactory | mes/modules，4 | businessTransactionManager |
| erpDataSource | ErpRoutingDataSource；持有 N 个服务端配置的外部 Druid 池 | erpSqlSessionFactory | external，0 | erpTransactionManager |

完整包名前缀均为 `dev.mesbase`，子包生效。没有一个 Factory 一个工厂，也没有一个外部连接标识一个工厂。
物理池数量为一个平台池 + 当前节点已创建的业务池（另可能有等待租约归还的旧池）+ N 个 ERP 池；业务池懒创建，不全部成为 Spring Bean。
CodegenCli 和 PlatformAdminCli 另有显式、短生命周期的 JDBC 工具对象，不属于 Web Bean 拓扑。

Mapper 注册链路：

```text
PersistenceConfiguration.static @Bean OwnerMapperRegistration
 -> BeanDefinitionRegistryPostProcessor
 -> OwnerMapperCatalog 先扫固定包、检查重叠/重名/旧注解冲突
 -> 注册 platform/business/[erp] MapperScannerConfigurer
 -> 标准 MyBatis-Spring 扫 *Mapper 接口，建立 MapperFactoryBean
 -> 各自显式 sqlSessionFactoryBeanName

PersistenceFactories.create
 -> Configuration.addMapper(同一组接口，加载接口/XML)
 -> SqlSessionFactoryBean
 -> SqlSessionTemplate/标准 Spring session 生命周期
```

这里确实存在两次类路径扫描，见 P1-01；不是旧的中央 Mapper 类清单。固定包常量 `MapperPackages.ROOTS` 是包边界，**不是逐 Mapper 清单**。
没有生产 `MfMapperScanConfig`、`MfMapperCatalog`、`DynamicDataSource`、`DataSourceContextHolder`、`@DataSource`、`@DS` 或 `AbstractRoutingDataSource` 子类。
现有两个路由数据源继承 `AbstractDataSource`，与固定工厂分层一起使用。

### 1.3 ERP 多库切换的实际负责层

```text
外部访问类/方法 @ErpSource("hr")，经 Spring 代理进入
 -> ErpSourceAspect (HIGHEST_PRECEDENCE + 100)
 -> ErpSourceContext.select("hr")
 -> 注解组合的 Spring TransactionInterceptor / erpTransactionManager
 -> OwnedTransactionManager.doBegin
 -> ErpRoutingDataSource.begin：按 id 查配置池，pin 身份
 -> Spring 获取并绑定 JDBC Connection
 -> external *Mapper / erpSqlSessionFactory
 -> PersistenceStatementGuard 校验身份
 -> ErpRoutingDataSource.getConnection -> 所选物理池.getConnection
 -> Spring 提交/回滚/cleanup；切面 finally 恢复选择
```

切面负责**声明选择的生命周期**，路由数据源负责**选择物理池及事务固定**；Mapper 包负责**工厂归属**。
它们解决不同问题，不能以“都有选库行为”删除任何一层。业务 Factory 不推导 ERP 标识。
ERP 当前生产 Mapper 数为 0，也没有生产 `@ErpSource` 调用者；这是已明确要求保留的连接装配能力，模板及实际 Spring/H2 代理测试是使用证据，不能以暂未接业务删除。
缺标识/配置、嵌套换标识、跨 owner 事务均拒绝。生产 `ErpTransactions` 已不存在，仅 src/test 保留低层测试助手。

### 1.4 Spring Infrastructure Map

| 创建入口 | 核心对象及连接方式 |
| --- | --- |
| MesBaseApplication 的组件扫描 | Service/Controller/Advice、RequestBoundaryFilter、SpringUtils；条件按配置启停 |
| FoundationConfiguration | selection/preferences、基础 Security 链、FoundationAccessInterceptor、HTTP endpoint 启动检查；system/runtime 关闭时提供空目录、拒绝授权及不可登录的用户服务 |
| PersistenceConfiguration | 三组数据源/会话工厂/事务管理器，标准 scanner 注册，PlatformPersistence/BusinessPersistence 和 CommitActions |
| FactoryRuntimeConfiguration | DB 管理的目录/resolver、RuntimeRegistry、ManagedDatasourceService；不从 YAML 厂清单装配生产池 |
| SystemConfiguration | Token Filter 与禁 Servlet 注册的 registration、用户/授权适配、私有存储、Quartz、缓存预热、同步协调器 |
| SystemRedisConfiguration | 名为 redisTemplate 的对象模板；显式 XssFilter Servlet 注册 |
| OpenApiSecurityConfig | properties、专用 Clock/JSON、Order(0) 的外部认证链；两个 Filter 在链内直接 new |
| OpenApiWebConfig | 专用授权 Interceptor、数据范围验证组合 |
| Boot/MVC/Security/Redis 默认设施 | HandlerMapping、Servlet/Security 框架链、RedisConnectionFactory、StringRedisTemplate 等；自定义 Bean 存在时按框架条件退让 |
| SmartInitializingSingleton | endpoint 注解完整性检查、字典/配置预热、jobs.init 和 Scheduler.start；启动会隐式执行这些动作 |

没有自定义 BeanPostProcessor、ImportBeanDefinitionRegistrar 或自定义 FactoryBean；有一处自定义 registry postprocessor，以及 SpringUtils 的 BeanFactoryPostProcessor。
Qualifier 主要区分三套数据源/管理器/工厂、MVC HandlerMapping 与 OpenAPI JSON/Clock，是明确边界；不是靠默认库解决歧义。
SystemConfiguration 的两个 `@Primary` 原用于替代基础用户/授权 Bean；现在基础 Bean 与系统 Bean 已由互斥条件区分，可去掉。
配置中 `mes.runtime=true` 还要求 platform guard 和 system 装配，并非任意 feature flag 组合都能启动；见 P1-04。

### 1.5 Security FilterChain / Servlet Filter / Interceptor

| Filter | 注册及范围 | 顺序/清理 | 会执行两次吗 |
| --- | --- | --- | --- |
| RequestBoundaryFilter | @Component，Servlet 全局 | HIGHEST_PRECEDENCE+20；FactoryContext/MDC finally 恢复 | 无第二个注册入口；默认跳过 ASYNC/ERROR dispatch |
| XssFilter | SystemRedisConfiguration 内 FilterRegistrationBean | HIGHEST_PRECEDENCE+30，配置的 system/monitor URL | 不是独立 Filter Bean，只有 Servlet 注册 |
| SystemTokenFilter | SystemConfiguration Bean；基础 Security 链 addFilterBefore BasicAuthenticationFilter | 仅内部请求，ASYNC dispatch 重认证 | 对应 FilterRegistrationBean.enabled=false，**就是为防 Boot 自动 Servlet 注册** |
| OpenApiRequestFilter | OpenAPI Security 链内 new | SecurityContextHolderFilter 之前；协议错误/日志/MDC 清理 | 不是 Bean，不会被 Servlet 自动注册 |
| OpenApiAuthenticationFilter | 同上 | UsernamePasswordAuthenticationFilter 之前 | 不是 Bean，无双注册 |

开启 system/open-api 时有两条 SecurityFilterChain，FilterChainProxy 按首个匹配链处理：Order(0) 的 `/open-api[/…]`，其后基础链。
system 关闭时仅基础链提供不可登录的 Basic 入口；开启 system 后 Basic 关闭，使用内部 Redis 会话 token。
基础链的 permitAll 不代表 HTTP 授权公开：所有非框架 HandlerMethod 都通过 FoundationAccessInterceptor 和 EndpointAccess；缺注解由启动 catalog 拒绝。
OpenAPI 请求先经过最高优先级的 OpenApiAuthorizationInterceptor，再经过 FoundationAccessInterceptor；外部 app identity 不转为内部 SysUser。

没有项目自定义 CORS Filter/CorsConfigurationSource/cors 注册，没有启用框架 LogoutFilter；注销由显式系统会话 API 执行。
没有仍运行的 Preview/JWT 认证链。TokenService 是随机凭证、服务端 Redis 会话；OpenApiTokenService 是独立外部令牌协议。
Constants 中 JWT_* 名称只是历史常量，不能当作还存在 JWT 认证的证据。
OpenApiResponseAdvice 只对 `/open-api` 做固定 AjaxResult 协议检查和 requestId 装饰，仍由全局 Advice 注册触发；它不做业务 Entity 投影。保留现有外部协议，见 P2-03。

### 1.6 Transaction / MyBatis / Scheduler / Event

三个具名 OwnedTransactionManager 都继承 Spring JdbcTransactionManager，无 `@Primary`、默认名 transactionManager 或自定义全局事务选择器。
源码 `@Transactional` 显式指向 platform/business，ERP 经组合注解指向 erp；没有裸事务注解依赖默认选择。
OwnedTransactionManager 仅在 Spring begin/commit/suspend/resume/cleanup 中保护 owner、路由、版本和租约，不实现 XA/JTA。
PlatformPersistence/DedicatedDatabasePersistence 的 TransactionTemplate 与声明式事务共用同一个管理器；它们不是另一套提交/回滚引擎，但存在两种调用形态，见 P1-03。

每个生产工厂安装 `PersistenceStatementGuard`、`PageInterceptor`。前者每次 Executor query/update/cursor 验证 owner/范围及独立元数据读取只能 SELECT；后者做 PageHelper 分页，显式 autoRuntimeDialect=true/reasonable=false。
没有第二个自定义 MyBatis 插件、TypeHandler 或 TypeHandler 自动扫描。

Quartz 由 SystemConfiguration 直接创建 StdSchedulerFactory；当前使用默认 RAMJobStore，启动钩子从平台 sys_job 恢复调度定义。
QuartzJobExecution/QuartzDisallowConcurrentExecution 经 SpringUtils 获取 ControlledJobRunner；JobClaimMapper 做数据库抢占，Quartz 本机 concurrent 注解不充当分布式锁。
mes_master_sync 的 SyncCoordinator 是另一种业务同步流（factory_id + sync_key + run_token + 活动时间），不是 sys_job 调度器的第二实现；保留用户已要求的分布式同步基础。
没有生产 `@Async` / `@Scheduled` / Spring EventListener / ApplicationEventPublisher / TransactionalEventListener。
消息变更是显式 MessageEvents.changed 回调，提交后经 CommitActions 触发；MessageEventHub 有单独 15 秒 SSE heartbeat 线程及 close 清理，不是 Spring 事件总线。

## 2. Hidden Runtime Mechanisms

### 2.1 真实内部 HTTP 链路

```text
HTTP Request
 -> characterEncodingFilter（Boot，order=HIGHEST_PRECEDENCE）
 -> RequestBoundaryFilter：请求 ID、MDC、FactoryContext 起止
 -> XssFilter（system 开启且 URL 匹配）
 -> formContentFilter（Boot，order=-9900）
 -> requestContextFilter（Boot，order=-105）
 -> Spring Security FilterChainProxy：基础链
      -> 框架 SecurityContext 生命周期 / headers 等
      -> SystemTokenFilter（system=true）或 BasicAuthenticationFilter（system=false）
 -> DispatcherServlet
 -> FoundationAccessInterceptor：EndpointAccess、身份/选厂/授权、bind FactoryScope
 -> Controller
      -> LogAspect（仅 @Log）
 -> 被调用的 Service Spring 代理
      -> DataScopeAspect（仅 @DataScope，order=10）
      -> TransactionInterceptor（仅事务方法）
 -> OwnedTransactionManager：数据库身份与 Factory/lease 固定
 -> MyBatis-Spring Mapper / SqlSessionTemplate
 -> PageInterceptor / PersistenceStatementGuard 的 Executor 链
 -> 固定 platform 池或 FactoryBusinessDataSource 所选业务池
 -> JDBC
 <- ErrorAdvice/正常响应；Spring 事务清理；全局 Filter finally 清理
```

不是每个 Service 都经过每个切面，ERP 切面不参与普通平台/业务调用；不用事务的 platform 读取不会虚构一个事务。
`@Log` 在 Controller 上，`@DataScope` 在被调用 Service 上，二者不是同一个代理中的固定 AOP 次序。
MyBatis 包装顺序由工厂 addInterceptor 顺序及 PageHelper Executor 调用决定，不把插件简单画成两个 Servlet 环节。

### 2.2 外部 HTTP 链路

```text
RequestBoundaryFilter
 -> 首匹配的 OpenAPI Security 链
      -> OpenApiRequestFilter：另一个请求 ID / 错误协议 / 访问日志
      -> 框架 SecurityContextHolderFilter
      -> OpenApiAuthenticationFilter：外部凭证或 token 入口限流
      -> 框架 AuthorizationFilter
 -> OpenApiAuthorizationInterceptor：apiCode/grant/rate/data-scope
 -> FoundationAccessInterceptor：明确的外部身份协议边界
 -> Controller / Service / platform MyBatis（按实际方法）
 -> OpenApiResponseAdvice 或 OpenApiExceptionResolver
 <- OpenAPI Filter finally、全局 Filter finally
```

| Mechanism | Trigger | Scope | Hidden Side Effect | Necessary |
| --- | --- | --- | --- | --- |
| Spring 组件/条件扫描 | Boot startup | 应用 | 自动创建或排除 Bean | 必要；feature flag 关系需整理 |
| OwnerMapperRegistration + Catalog | system startup | 三个固定包组 | 两次扫描、程序式注册 scanner、反射载入接口 | 注册必要；双扫描可简化，P1-01 |
| SmartInitializingSingleton | singleton ready | 启动 | 校验 endpoint、读取平台库/Redis、启动 Quartz | 必要，应显式记录启动依赖 |
| FoundationAccessInterceptor | MVC HandlerMethod | HTTP | 选厂、查询权限、写 FactoryContext | 安全边界，保留 |
| RequestBoundaryFilter | Servlet request | 请求线程 | ID/MDC、清理及错误捕获 | 必要；ASYNC 生命周期待验证，P1-05 |
| OpenAPI Filter/Interceptor/Advice | 外部路径 | 外部协议 | 认证/限流/访问日志/响应装饰 | 必要；重复 ID 是 P1-02 |
| LogAspect | @Log | Controller 代理 | finally 持久化操作审计 | 有真实注解调用，保留 |
| DataScopeAspect | @DataScope | Service 代理 | 修改入参 BaseEntity.params.dataScope | 有真实查询消费和自调用代理，保留 |
| ErpSourceAspect | @ErpSource，经代理 | 外部调用 | 在事务取连接前设置/恢复外部标识 | 时序边界，保留 |
| Spring TransactionInterceptor | 具名事务/组合注解 | 事务方法 | 开始、传播、提交、回滚 | 框架能力，保留 |
| OwnedTransactionManager | 上述事务/Template | 当前线程事务 | owner 资源、pin、lease、挂起/恢复 | 保护隔离，保留 |
| PersistenceStatementGuard | MyBatis Executor | SQL | owner/版本检查、独立读取禁止写入 | 真实防旁路能力，保留 |
| PageHelper | 显式 startPage 后 Mapper | 当前线程 | 分页 ThreadLocal、SQL 改写 | 有调用；平台元数据读取清理/恢复原分页 |
| OwnedSqlSession Proxy | 回调取得 mapper | 回调/创建线程 | 再包装 MyBatis 代理，校验到期/跨线程使用 | 有额外安全语义，不能直接删 |
| SpringUtils | 静态 getBean/getAopProxy | JVM/context | Service locator、取当前 AOP 代理 | 当前活跃，P1-06 |
| SpringCommitActions | 显式注册完成回调 | Spring 事务 | 提交后缓存/池/文件/消息/调度效果 | 真实完成语义，保留 |
| Quartz 与 SSE executor | 启动/定时 | 后台线程 | 非请求线程执行，显式 Scope 或不传 Scope | 均有生命周期；不是重复 Spring scheduler |

### 2.3 ThreadLocal / Context 生命周期

项目自己声明 **3 个 holder，5 个 ThreadLocal 字段**；没有 InheritableThreadLocal。

| Context | 谁 set | 谁 get | 谁 remove/恢复 | 生命周期及异步判断 |
| --- | --- | --- | --- | --- |
| FactoryContext.CURRENT | FoundationAccessInterceptor.bind、withScope/runWithScope | FoundationService、业务路由、日志、任务、业务入口 | RequestBoundaryFilter finally；withScope finally | 正常请求/受控任务有恢复；不自动传线程；ASYNC dispatch 缺完整证据，P1-05 |
| FactoryContext.PINNED | FactoryBusinessDataSource 的 Pin；Dedicated 回调 pinned | bind/clear/切厂检查 | Spring routing.end / suspend、Pin.close；pinned finally | 事务生命周期，旧 scope 不能覆盖正在运行的事务 |
| ErpSourceContext.CURRENT | ErpSourceAspect.select | ERP routing | 切面 finally restore，空父值 remove | 必须经同步代理；后台新线程须重新从声明入口开始 |
| ErpSourceContext.PINNED | ErpRoutingDataSource.begin/resume | select 换库检查 | Spring end/suspend -> Pin.close | 事务生命周期；不传播连接到异步 |
| PersistenceReadContext.ACTIVE | PlatformPersistence.readOnly 的 independent | OwnedTransactionManager、StatementGuard | independent finally 恢复或 remove | 为业务事务内独立读平台绑定提供 SELECT-only 窗口；不同于 Factory/ERP 身份 |

框架状态也必须纳入链路：SecurityContextHolder 由 Security 链建立/清理，ExecutionScope 执行前清用户身份、finally 恢复；RequestContextHolder 由 MVC/Boot request filter 管理；TransactionSynchronizationManager 存 owner/route/连接并由 Spring 清理；AopContext.currentProxy 由 Spring AOP 在 exposeProxy 调用期间设置/恢复；MDC 由两种请求 Filter finally 恢复。
PageHelper 的分页状态由 PageUtils/BaseController/少数显式 PageHelper.startPage 设置，插件在正常 SQL 生命周期清理；PlatformPersistence.readOnly 临时 clear/恢复。请求边界和错误 Advice 没有兜底 clearPage，见 P1-08。
没有另一个 UserContext/TenantContext/DataSourceContextHolder 与 FactoryScope 重复。连接版本/身份资源属于事务数据，不属于授权。
三种自建 holder 都可找到可靠正常路径的 finally/close；未据此宣称所有异步重派发均已安全。

## 3. Overengineering Candidates

等级表示本轮的删减把握，不等于安全漏洞严重性。P0 为证据充分的减法；P1/P2 保留运行机制，需要后续有边界决定。

### P0 明确可以删除或直接简化

| 编号/位置 | 当前作用与调用证据 | 为什么是残留 | 删除/简化后的影响 | 风险及决定 |
| --- | --- | --- | --- | --- |
| P0-01 common/utils/html/HTMLFilter.java | 旧手写 sanitizer；只有自身和迁移清单提及 | EscapeUtil.clean 已直接转 RichTextSanitizer/Jsoup；当前 Filter/notice 都走新实现 | 删除旧实现；清洗行为不变 | 多重零引用确认；删除，保留来源记录 |
| P0-02 system/service/ISysAttachmentService.java | 六个旧 CRUD 签名；无实现、注入或调用 | SysAttachmentServiceImpl 已是独立 concrete Service，使用当前私有文件契约 | 删除未实现的旧接口，不改现有 Service/API | 不是删除附件功能；删除 |
| P0-03 infrastructure/persistence/SharedDatabasePersistence.java | DESIGN CONTRACT ONLY；无实现/注册/测试引用 | 只是未来 Shared 占位及嵌套 identity | 删除占位；Shared 仍禁止以 Dedicated 启用，ADR-004 计划保留 | 没有活动路由/事务代码；删除 |
| P0-04 core/factory/Enterprise.java | 无引用 record | Factory 持有 enterpriseId String，目录/数据库未使用 Enterprise 类型 | 删除未接入模型，不删企业归属字段 | 不改变业务契约；删除并追加 ADR 历史说明 |
| P0-05 application.yml | platform.session-ttl-seconds、runtime.factories 空默认、pagehelper.helper-dialect/reasonable | 前者无读取；厂绑定已由 DB 管理；PageInterceptor 属性在工厂明确设置，未接 starter/property binder | 删除 4 个无生产效果的默认 property | MySqlDevelopmentTest 另读 opt-in fixture 文件的 factories，不是此默认配置；保留该低层测试输入 |
| P0-06 SystemConfiguration | systemAccounts/factoryAuthorization 上两个 @Primary；syncCoordinator 注入同一 PlatformPersistence 两次 | system=false 基础 Bean 与 system=true 的系统 Bean 已互斥；重复参数未使用 | 去掉 Primary 和无用参数；Bean 选择仍显式 | 用生产配置 fixture 验证单一用户/授权 Bean 与安全链装配 |
| P0-07 ArchitectureBoundaryTest | 两个测试遍历旧空 src/main 目录，实际 0 文件 | 模块拆分后静态验证没有同步 | 改为真实模块源码、非空断言；保留原三种边界测试 | 这是修复失真验证，不变更生产架构 |

### P1 强烈建议简化 — Needs Manual Decision

每项均**未删除关键机制**。

| 编号/位置 | 当前作用/调用证据 | 架构问题 | 简化影响及风险 |
| --- | --- | --- | --- |
| P1-01 OwnerMapperRegistration / OwnerMapperCatalog / PersistenceFactories | Catalog 校验包/重名，标准 scanner 创建代理，addMapper 加载 XML；PlatformPersistence 和 codegen 回调又需类型集合 | 标准扫描外再做一次类路径扫描，registry postprocessor 又注册 scanner；新开发者要同时理解三处 | 可评估减少一次扫描/手工注册，但必须保留包归属、重名冲突、ERP 启停与 XML 装配负例。不能只换 @MapperScan 便删除所有 guard |
| P1-02 RequestBoundaryFilter + OpenApiRequestFilter + OpenApiHttp | 前者 mes.requestId/响应头/MDC；后者独立属性/新 UUID/覆盖响应头/MDC | 一次外部请求有两个 ID；外部响应/访问日志用内层 ID，基础错误/其他日志可能用外层 ID | 统一权威 ID 有收益；外部错误协议及异常时序需专门验收，不能删外部 Filter 的限流异常/日志职责 |
| P1-03 PlatformPersistence / DedicatedDatabasePersistence / OwnedSqlSession + 生成 Service 模板 | 正常业务有声明式具名事务/直调 Mapper；管理、同步、Quartz、CLI、生成骨架仍有 callback/额外 Proxy | 两种访问形态继续维护；“兼容”注释不代表可删 | 若统一入口必须同步迁移真实管理链/生成骨架/测试；readOnly 独立平台 SELECT、跨线程/过期 mapper guard 有实质价值。本轮不改业务 Service/生成契约 |
| P1-04 mes.system / mes.platform / mes.runtime 条件 | 平台工厂由 system 装配；runtime guard 和 GLOBAL 生成模板仍读 platform.enabled | platform.enabled 不再直接开启平台持久化，名称与实际装配关系不一致 | 需要决定配置组合及兼容迁移；现在不能删除 platform.enabled，因为生产 guard 和模板确实消费 |
| P1-05 RequestBoundaryFilter + FoundationAccessInterceptor | 全局 Filter 默认跳过 ASYNC，Interceptor preHandle 可 bind，未实现 afterCompletion/afterConcurrentHandlingStarted 清理；SystemTokenFilter 明确参加 ASYNC；已有 SSE 入口 | ASYNC 重派发再次 bind 后可能没有同线程 Factory/MDC 生命周期清理 | 静态风险，未复现。需按实际 Servlet ASYNC/ERROR 路径验证并决定清理策略；普通同步/ControlledExecutor finally 测试不覆盖它，本轮不改 ThreadLocal 安全边界 |
| P1-06 SpringUtils / exposeProxy=true | DictUtils/MessageUtils/Quartz/部分 HTTP 入口 getBean；用户/角色/部门 getAopProxy(this) 重进 @DataScope 查询 | 静态 locator 隐藏依赖、自调用依赖当前线程暴露代理 | 可逐入口显式注入；现在删除 exposeProxy 或 SpringUtils 会破坏已有 DataScope 和 Quartz。不能作为无调用工具删除 |
| P1-07 根 POM 27 个直接依赖由所有子模块继承 | common 也带 Web/Security/Redis/MyBatis/Quartz/Velocity/POI/驱动等 | 源码边界比编译依赖边界窄，依赖来源不易发现 | 应按实际模块归属整理依赖；会影响全部模块/测试/运行打包，尤其根 POM 已有用户暂存修改，本轮保留 |
| P1-08 PageUtils / BaseController / RequestBoundaryFilter | startPage 写框架 ThreadLocal；正常 Mapper 分页结束才由插件清理；HTTP fixture 的 AfterEach 另有 clearPage | startPage 后、进入 Mapper 前的异常路径缺请求 finally 兜底，可能遗留给复用线程 | 静态生命周期风险，未复现跨请求污染；需独立验证前置异常/异步/元数据读取恢复，不在本轮改业务或分页边界 |
| P1-09 scripts/format-java（用户已有暂存修改） | 调 IDEA native CLI，-s 指向 .idea/codeStyles 目录；本机 IDEA 已运行 | 原入口先报实例锁，隔离启动后报 SchemeImportException，CLI 需要 code_scheme XML 文件 | 本轮用独立临时 config/system 目录和同一 Project.xml 的 code_scheme 导出、IDEA_FORMATTER override 完成原生格式化；未覆盖用户脚本。后续需修正持久入口/实例隔离 |
| P1-10 dev/prod profile 及 Maven resources 打包 | 两份 profile 是 Git 跟踪文件，并出现在实际 JAR 的 BOOT-INF/classes；仅存在性检查发现 dev 3 处/prod 4 处非空敏感字段字面量，值未输出 | 本地/生产连接配置与通用可分发产物耦合，无参数启动默认激活 dev；不能把 smoke 未加载它们理解为 JAR 没包含它们 | 字面量是否为有效凭证未验证，但 tracked/packaged 是已证实事实；需部署 Owner 决定外置配置、环境注入及历史值处置，本轮不替用户改连接/密钥、不部署此产物 |

### P2 可继续观察

| 编号/位置 | 当前作用/调用证据 | Possible Accidental Abstraction / 判断 | 影响、风险及处理 |
| --- | --- | --- | --- |
| P2-01 ControlledExecutor | 生产无 submit 调用、不是 Bean；ExecutionScopeTest 验证排队/CallerRuns/关闭，ExecutionScope 被 Quartz 使用 | 有限线程池 + 显式 Scope 是额外语义，但 executor 本体目前只有测试消费者 | 可评估是否仅保留 ExecutionScope；ADR-001 仍认领此执行契约，未直接删 |
| P2-02 MesCodegenService -> DefaultMesCodegenService | 一个实现，CLI/测试直接构造 default | Possible Accidental Abstraction：目前没有动态策略选择 | 缺少删接口收益，既有工具 API 可供未来消费者，保留并观察；不新增实现 |
| P2-03 OpenApiResponseAdvice | supports=true，路径内 AjaxResult 固定协议检查/requestId 装饰 | 全局注册、局部生效；有当前外部协议能力，不是失效业务投影 advice | 可以考虑显式协议入口，但不在本轮改 API；保留 |
| P2-04 Common 的反射/Excel/Convert/UUID/静态工具 | Excel 注解、字典、导入导出等仍有引用；ReflectUtils 用于嵌套属性/注解 handler | 体量大、源项目风格；不能据此认为 Java/Spring 可直接等价替代 | 整类未证明 dead；无大规模方法级删减，保留来源许可 |
| P2-05 Constants.JWT_*、PartTypeEnum/CycleMethodMnum、ProductionMonitorTimelineVO | 常量/类型部分无外部引用；MES VO/枚举不在纯基础设施范围 | 旧命名不是旧机制仍运行的证据 | 记录候选，不扩大本轮到业务模型清理 |
| P2-06 spring.datasource.type / upstream codegen 资源 | type 非 Druid Settings 字段；本项目没有 JDBC starter；upstream 资源用于来源追溯 | 当前没有直接主动读取，但属于框架配置/来源资产 | 不因 grep 0 删除框架键或合法溯源资源；无需复制或另建基础设施 |

## 4. Dead Code Candidates

删除前对 P0-01..04 以 simple name、全限定名及路径检索后端、测试、模板、脚本、POM、配置、XML、SPI 和 `mes-base-ui`（只读）。
排除 `.git`/`target` 等生成物；另检查当前产物残留和 Git 历史。每个候选无 stereotype、没有 @Bean 创建者；扫描规则也不会注册它。
引用记录只剩自身、旧事实报告或来源清单，历史文字不算运行调用。

| 必须检查的维度 | HTMLFilter | ISysAttachmentService | SharedDatabasePersistence | Enterprise |
| --- | --- | --- | --- | --- |
| 1 Java reference（定义之外） | 0 | 0 | 0 | 0 |
| 2 Spring Bean reference | 0 | 0 | 0 | 0 |
| 3 annotation scan | 无 stereotype | 无 stereotype | 非 Mapper/无 stereotype | 无 stereotype |
| 4 XML | 0 | 0 | 0 | 0 |
| 5 MyBatis XML | 0 | 0 | 0 | 0 |
| 6 configuration property | 0 | 0 | 0 | 0 |
| 7 reflection | 0；Excel handler 也无引用 | 0 | 0 | 0 |
| 8 SPI / META-INF/spring / spring.factories | 无项目注册 | 无项目注册 | 无项目注册 | 无项目注册 |
| 9 tests | 0；当前 sanitizer 有测试 | 0；当前附件 HTTP fixture 保留 | 0；Dedicated 拒 Shared 负例保留 | 0；Factory 身份测试保留 |
| 10 scripts | 0 | 0 | 0 | 0 |
| 11 Maven configuration | 无类名/特殊注册 | 无 | 无 | 无 |
| 12 frontend/API indirect dependency | 0；不改变 XSS API | 0；不删除附件路由 | 0 | 0；enterpriseId 字段仍在 |
| 13 Git history | e292789 迁入已不使用；279065a 格式化 | e292789 同时有 concrete 实现；279065a 格式化 | e292789 前期设计占位 | e292789 前期值对象占位 |

Git 的提交消息几乎均为 `init`，本报告依据实际 diff/blame/-S/-G 与当前调用链，不从消息推测动机。
e292789 中 EscapeUtil.clean 已调用 RichTextSanitizer，不是本轮换 sanitizer；删除不会改变清洗策略。
Shared 与 Enterprise 在旧 ADR/历史 review 中存在，删除后追加当前状态说明，不重写历史。
HTMLFilter/附件接口在合法来源清单中保留 source/target hash 和 PORT 记录，并追加移除原因，不能为了删除代码而抹掉来源。

## 5. Legacy Architecture

```text
早期 src/main 单源码树、共享库占位、Enterprise record
 -> 七模块真实源码 + Dedicated 固定业务工厂 / DB 管理的目录
 -> 旧根空目录、失真静态测试、Shared/Enterprise 占位（本轮处理后者及测试）

源 RuoYi/Source Product 通用 HTMLFilter、附件 CRUD 接口
 -> RichTextSanitizer / 私有附件 concrete Service
 -> 未接入的旧实现/旧接口（本轮删除）

中央 Mapper 清单 -> 每 Mapper PersistenceOwner -> 固定包分组
 -> 当前 Catalog + 标准 scanner；旧注解只作程序式 fixture 兼容

旧手工 SystemUnit/AnalyticsUnit、多个业务会话、ErpTransactions 生产 callback
 -> 三组标准 Spring 工厂/具名事务 + @ErpSource
 -> 生产旧类已不在，测试 ErpTransactions 仍提供低层路由输入

旧 PlatformTokenFilter / LegacyPlatformCondition / 全局响应业务投影
 -> SystemTokenFilter / 专用 OpenAPI 链 / 显式 Web View
 -> 旧链/旧业务 ResponseAdvice 已无生产类；保留的是固定 OpenAPI 协议装饰
```

相关时间线的 diff 集中在 `8cb871f`、`fcc8757`、`70bcc12`；后两次持久化演进以当前包与声明式连接为准。
旧 docs/MES_BASE_REALIGNMENT_REPORT 中的早期结论不是当前注册规则。架构 README 的“六模块/integration.erp Port”描述已过时，入口按本审计修正并保留时间线。
旧 MySqlDevelopmentTest 的 factories 输入是明确 opt-in 的测试装配，不再是生产 YAML 模型；不要把它通过理解为 DB 管理 Runtime 全链路通过。

## 6. Duplicate Infrastructure

| 看似重复的机制 | 事实关系 | 本轮决定 |
| --- | --- | --- |
| 固定 Mapper 工厂 + business Factory 路由 + ERP 注解路由 | 工厂 owner / 业务身份 / 外部连接三种不同边界 | 保留，不能恢复默认库兜底 |
| Catalog 扫描 + MapperScannerConfigurer + addMapper | 前两者重复扫描；第三者是 MyBatis statement/XML 注册，不是第三套 Spring Bean 注册 | 标为 P1-01；保留校验和标准扫描 |
| TransactionTemplate + @Transactional | 同一个 Spring manager 的两种入口；仍有管理/同步/生成骨架消费者 | P1-03，不删有效调用链 |
| owner Spring 资源 + Factory/ERP pin + StatementGuard + OwnedSqlSession Proxy | 事务归属、禁止切范围、每 SQL 复查、回调到期/跨线程防护分别生效 | 部分重复校验但非无额外能力；保留 |
| RequestBoundaryFilter + OpenApiRequestFilter | Factory 边界与外部错误/日志职责不同；请求 ID 确实重复 | P1-02；不整块删 Filter |
| Foundation interceptor + OpenAPI interceptor | 内部 scope/endpoint 规则与外部 grant/API 元数据不同 | 保留 |
| sys_job 抢占 + mes_master_sync 抢占 | 调度执行实例与跨入口 Factory 同步流不同 | 保留；本轮不评估具体同步业务 |
| 本地存储 + MinIO Adapter | 两个真实协议实现/配置入口 | 保留 FileStorage 接口，无新增 wrapper |
| 对象 RedisTemplate + StringRedisTemplate | 源系统对象缓存与外部 token/原子限流字符串操作 | 序列化/协议不同；非重复认证 |

包职责仍可辨认：common 是源基础工具/协议，core 是纯身份/范围 Port，platform 是 Web/执行桥及工具/管理模型，infrastructure 是实现，server 是 Boot 装配，framework 是已迁入的运行设施。
同名词跨模块并不意味着重复实现；framework 与 infrastructure 的命名交叉、common 中宽依赖是理解成本，但本轮不迁包装修。

## 7. Dependency Cleanup

执行 `mvn -B -ntp dependency:tree`；实际以绝对 outputFile 保存的最终文件为 mes-admin 的聚合闭包（reactor 各模块写同一文件，最后一次覆盖，不能称为八份树）。
日志/树保存于 `target/architecture-audit`。8 个 POM 共 **40 个直接 dependency 声明**：8 个 reactor 内部边、32 个外部声明；根 POM 27 个声明全体继承。
没有 dependencyManagement 自建版本框架、项目自定义 annotation processor 清单、遗留 MyBatis-Plus/动态数据源 starter 或第二个事务库。

| 依赖组 | 当前消费者/特殊入口 | 决定 |
| --- | --- | --- |
| WebMVC / Security / Validation / AspectJ / JDBC / MyBatis-Spring | 真实 Web、安全链、具名事务、标准 Mapper 及注解切面 | 保留 |
| Redis starter / PageHelper / POI / commons-lang3 | token/缓存/限流、分页、Excel 反射导出、StringUtils | 保留，未用 dependency:analyze 误删 |
| Druid / MySQL / PostgreSQL / SQL Server / Flyway 插件 | 显式池、配置驱动类、迁移/CLI；驱动无 import 不代表无使用 | 保留；本轮不连接真实库 |
| Jsoup / Kaptcha / OSHI / Quartz / Velocity | 当前 sanitizer、验证码反射 creator、监控、任务、生成器 | 保留 |
| MinIO + 显式 okhttp-jvm | MinioClient 间接使用；本地解析的 okhttp-5.3.2.jar 有 0 个 class，okhttp-jvm 同版有 336 个 class/OkHttpClient | JVM 依赖必要，不能按源码 0 imports 删除 |
| Jackson 2 + Jackson 3 | 项目/OpenAPI/XSS 等显式用 com.fasterxml；Boot4/Flyway 用 tools.jackson | 两个命名空间同时存在，不是简单同包版本重复。合并涉及协议/转换，Needs Manual Decision |
| Swagger annotations / springdoc | 注解及受权限约束的 Swagger 工具；默认 SDK 文档端点关闭 | 保留 |
| Lombok / test starter / H2 / Hikari | 编译 getter/annotation processor；测试 fixture | 保留；打包已排除 Lombok/H2/测试实体 |

Maven plugins：Surefire 固定根 workingDirectory/test profile，Jar/Boot 插件形成根 target 的可执行 JAR，均有真实职责。
任务开始时根 POM 已暂存用户的 formatter/Spotless 调整，本轮不编辑 POM、不重复安装 formatter、不升级版本。
**没有找到可充分证明无运行价值且可以单独删除的依赖**；依赖瘦身的主要候选是归属/继承收窄（P1-07），不是猜测式删库。

## 8. Recommended Architecture

删除 P0 后自然保留当前一套 Core、七模块、三种 owner 的会话工厂及具名 Spring 事务。
Factory 的 DB 管理和版本化租约保留；ERP 独立连接标识及取连接前的注解选择保留；HTTP 安全链、显式 EndpointAccess、资源/范围边界保留。
减少四个没有运行调用的类、失效配置和多余 Bean 选择/参数；验证从真实模块源码开始，关键装配增加明确的 H2/Mock fixture 证据。

不新增架构框架、接口、Facade、Manager、事件总线或业务 Domain；不重写 Service/SQL/API/UI。
下一步若另行授权，应按 P1 单项验证边界再做减法，不能用本报告当作直接删除数据源/切面/事务/上下文的授权证明。

## 9. Architecture Audit Result / Verification

### Before / After

只统计各模块 `src/main`，不含测试、target、旧根空目录或前端。Configuration 计入 Boot 配置和嵌套/CLI 配置；共有 11 个声明，分布在 10 个源文件。

| 指标 | Before | After（本轮减法） | 最终工作区快照 |
| --- | ---: | ---: | ---: |
| Java 顶层类型 / 源文件 | 463 | 459 | 461 |
| Configuration 声明（含 Boot/嵌套/CLI） | 11 | 11 | 11 |
| Filter 类型 | 5 | 5 | 5 |
| MVC Interceptor 类型 | 2 | 2 | 2 |
| Aspect 类型 | 3 | 3 | 3 |
| 自建 Context holder / ThreadLocal 字段 | 3 / 5 | 3 / 5 | 3 / 5 |
| persistence 包基础设施类型 | 25 | 24 | 24 |
| 路由 DataSource 实现 | 2 | 2 | 2 |
| DataSource / SessionFactory / TransactionManager Bean（system+erp 开启） | 3 / 3 / 3 | 3 / 3 / 3 | 3 / 3 / 3 |
| @Bean 声明 | 47 | 47 | 47 |
| POM 直接依赖声明（含 reactor 内部边） | 40 | 40 | 40 |

本轮减法快照为 18:10 的 459 个类型；最终工作区为 18:22 的 461 个类型。
额外两个类型是并发进入的 `server/monitor/DruidMonitorService` 和 `system/domain/WorkspaceRoleScope`。
同期还有监控/会话入口、权限读取、Mapper、RuntimeRegistry/ErpRoutingDataSource 的观察方法及 PersistenceAssemblyTest 修改，均未由本轮编辑或格式化。
计数用于说明范围，不作为架构成功评分。18:22 成功 verify 的 570 个输入前后哈希一致；其后新增 DruidMonitorTest / WorkspaceAccessTest，18:25 verify 的 572 个源码/资源/POM/脚本输入前后也一致，生产源码未再改变。
18:28 再次 verify 期间 DruidMonitorTest 改动、WorkspaceAccessMySqlTest 新增，因此该次运行没有稳定的最终输入快照。停止重复构建，不覆盖或删除这些并发工作。

### Removed

- HTMLFilter、未实现的 ISysAttachmentService、SharedDatabasePersistence（含嵌套 ScopedIdentity）、Enterprise 四个顶层类型。
- application.yml 的三个配置块中四个无生产效果 property：旧 session TTL、空 YAML 厂清单、两个未绑定的 PageHelper 属性。
- 合法来源清单保留两份旧 Source Product 文件的来源/哈希/PORT 记录，追加 REMOVED_UNUSED 及原因；不移除许可。

### Simplified

- SystemConfiguration 删除两处已无歧义的 @Primary 及一个重复、未使用的注入参数。
- ArchitectureBoundaryTest 的三项断言保留；Core 扫描真实 14 个 Java 文件，probe 检查扫描实际模块 src/main；空扫描不再通过。
- InfrastructureContextTest 新增一项装配集成检查，直接使用生产 Configuration，而不是重新实现数据源/安全/事务设施。
- smoke-packaged.py 增加四个旧顶层类型及嵌套字节码的打包排除断言、实际 Filter 注册/链路日志。Maven 已自行移除旧编译输出；针对本轮删除类型的残留检查为 0，没有执行 broad clean。

### Kept intentionally / Needs Manual Decision

三组工厂、标准 Spring/MyBatis 事务与 session、Factory/ERP 路由、owner/lease/StatementGuard、三个 Context、两条认证链、DataScope/Log/ERP 切面、同步和 Quartz 等保留。
固定包 + Factory 路由 + ERP 标识不是重复；callback 和 OwnedSqlSession 目前仍有生成器/管理/同步调用，不能当作死代码删除。
P1-01..10 和 P2-01..06 的理由、调用者、风险与影响见第 3 节；本轮没有替换其运行边界。

### Verification

| 实际命令/检查 | 结果 | 证据与边界 |
| --- | --- | --- |
| mvn -version | Java 21.0.4 / Maven 3.9.10 | 沿用用户标准 launcher/repository；没有项目 .m2/.mvn 或新 mirror |
| mvn -B -ntp dependency:tree -DoutputFile=…/target/architecture-audit/dependency-tree.txt | PASS | 聚合闭包、版本和 SPI/驱动/间接库消费者一并审查 |
| 修改前 mvn -B -ntp test | PASS：170 项，167 通过 / 3 跳过 | baseline-test.log；旧空扫描通过不能算源码边界证据 |
| 定向 test -pl mes-admin -am -Dtest=ArchitectureBoundaryTest,InfrastructureContextTest,PersistenceAssemblyTest,ErpSourceAnnotationTest -Dsurefire.failIfNoSpecifiedTests=false | PASS：9/9 | targeted-test.log；真实 Spring/MyBatis/H2、Mock 外围依赖 |
| IDEA_VM_OPTIONS=… IDEA_FORMATTER=… ./scripts/format-java（仅 3 个本轮 Java 文件） | PASS：3 次各 1 scanned / 1 formatted / OK，exit 0 | format-native.log；原生 IDEA 2023.3.2，项目 code_scheme 未更改。原入口实例锁/目录参数失败记录于 format*.log；临时 wrapper 没有使用另一套 formatter |
| mvn -B -ntp test | PASS：171 项，168 通过 / 3 跳过 / 0 失败错误 | test.log；包含完整原有测试及本轮装配检查 |
| mvn -B -ntp verify（18:22 成功快照） | PASS：compile/test/package/repackage 完成，8 reactor 项目 SUCCESS；171 项，168 通过 / 3 跳过 | verify.log 为本轮瘦身快照；verify-final.log 包含同期生产改动，18:22:16 完成，生成 target/mes-base-0.1.0-SNAPSHOT.jar |
| mvn -B -ntp verify（18:25 完整测试快照） | **FAIL**：176 项，170 通过 / 3 跳过 / 2 失败 / 1 错误；mes-admin 未进入本次 package | verify-final2.log，18:25:41 完成；新增的两个并发测试共 5 项，本轮两种架构检查及原有测试仍通过，失败详情如下 |
| mvn -B -ntp verify（18:28 最新运行） | **FAIL**：mes-framework:testCompile 找不到 OpenApiProperties，全量测试/打包未执行完成 | verify-final3.log，18:28:40 完成，OpenApiDataScopeVerificationTest.java:22；该源文件未由本轮更改，交付检查时源码和 class 均存在。尚未证明构建失败的具体原因；运行期间另有测试变化，不推断为死代码或本轮删除所致 |
| /usr/bin/python3 scripts/smoke-packaged.py | PASS：4/4 | smoke-final.log、target/validation/packaged-smoke.json / packaged-default.log；18:22 最近成功构建的 JAR，独立 loopback 进程、显式 default profile，未加载 dev/prod profile 或真实连接。不能称为失败 verify 后重新打包成功 |
| git diff --check | PASS | 本轮工作树 diff；新增文件也另查尾空格/换行 |
| git diff --cached --check（额外检查） | 既有失败，exit 2 | 用户原已暂存 docs/reviews/2026-10-06-server-package-review.md:3/4 的 Markdown 尾空格；保持原样，不宣称整个 index 无问题 |
| 显式 Java import / manifest JSON | PASS | 3 个新增/修改 Java 文件，排除包/import 与类名字符串后无 FQN/通配符违规；清单可解析 |
| 原有暂存文件/删除状态/完整 index 指纹 | 全部一致 | preexisting-work.json 对比；原有 10 个状态项未被本轮覆盖、格式化或重新 stage |

日志及 before/after 类型清单、tests-summary.json / tests-summary-final.json / tests-summary-final2.json 位于 `target/architecture-audit`。Git hooks 原已设为 `.githooks`，没有重复安装或改用户 hook。
最终类型计数为 inventory-final.json；verification-inputs-final2-before.json / verification-inputs-final2-after.json 内容一致，verification-inputs-final3-before.json / verification-inputs-final3-after.json 有上述并发测试差异。
tests-summary-final2.json 从本轮 verify-final2.log 提取，避免共享 Surefire 报告被其他运行覆盖后混淆统计。
3 个跳过项是 MySqlDevelopmentTest 的 2 项、ManagedRuntimeMySqlTest 的 1 项，必须显式提供隔离测试库才运行；本次通过项不能称为真实数据库/外部联调通过。

18:25 完整测试快照的失败均来自本轮未编辑的并发新增测试：

- `DruidMonitorTest.paginationSpansFixedAndManagedSourcesWithoutDroppingRows:83`：分页结果断言失败。
- `DruidMonitorTest.sqlServerTemplatesUseTheirOwnDialectAndDoNotExposeLiteralParameters:94`：SQL 展示断言失败。
- `WorkspaceAccessTest.scopesComeFromEnabledRolesAndFactoriesRatherThanRoleNamesOrFactoryAvailability:31`：直接创建 PlatformPersistence 的 fixture 初始化失败，Mapper XML 的 `SysRole` type alias 无法解析。

这两个测试直接构造监控服务或持久化/权限服务，均未加载 SystemConfiguration，也未引用本轮删除的四个类型；本轮生产装配检查在同一次失败运行中通过。
本轮按“不要重构业务功能/不要审查业务逻辑正确性”的范围规则保留并发功能及其用例。它们之后已有更新，上述用例结果只对应记录的快照。
当前全量验收未通过，须待并发工作和构建输入稳定、处理最新编译失败后重新 verify；不删除失败用例或跳过它们来制造成功。

新增装配 fixture 证明：生产配置可共同创建三种 DataSource/SqlSessionFactory/TransactionManager；平台/业务 Mapper 注册；唯一用户服务与 Factory 授权 Bean，无 Primary 兜底；两条 Security 链及内链 Token Filter 禁 Servlet 双注册；Quartz 的真实 RAMJobStore 初始化、start、close/shutdown。
它使用 H2、Mock 业务服务/RedisConnectionFactory，缓存预热和 job 数据读取是 Mock，**不是全生产 Service + 真数据库/Redis 的启动验收**。既有隔离/ERP 代理测试另验证实际 SQL、回滚、缺 Scope、换库拒绝、挂起恢复与旧 lease 防覆盖。

打包日志实际全局 Servlet Filter 顺序（关闭 system 的 smoke）：

```text
characterEncodingFilter -2147483648
 -> requestBoundaryFilter -2147483628
 -> formContentFilter -9900
 -> requestContextFilter -105
 -> springSecurityFilterChain -100
```

该进程的真实 Security 链：

```text
DisableEncodeUrlFilter -> WebAsyncManagerIntegrationFilter -> SecurityContextHolderFilter
 -> HeaderWriterFilter -> BasicAuthenticationFilter -> SecurityContextHolderAwareRequestFilter
 -> AnonymousAuthenticationFilter -> SessionManagementFilter
 -> ExceptionTranslationFilter -> AuthorizationFilter
```

system=true 时 Basic 关闭、SystemTokenFilter 插入，其 XSS/内部 token/OpenAPI 注册由源码及新增生产配置 fixture 证明；没有把关闭模式的 smoke 宣称为真实开启模式的 Servlet/外部服务验收。
JAR 条目名检查同时证实 application-dev.yml/application-prod.yml 被打包；敏感字面量只做存在性统计，不输出值、有效性或连接细节，见 P1-10。本地产物生成成功不等于适合公开分发。
未运行前端 build/UI（本轮未改 UI）、真实 MySQL/SQL Server/Redis/MinIO、实际异步跨请求污染复现或生产部署；没有提交/推送，也没有重启用户服务。
回滚只恢复本轮四个删除文件、SystemConfiguration/application.yml/两个架构测试/smoke 及对应审计/ADR/来源清单追加，不使用 reset/clean 覆盖原有工作。

## 10. 2026-10-06 补充：复用 Spring 标准数据源路由

用户追问数据源切换为何自实现后，补充核对 Spring 官方 API。此前“路由职责必要、当前有装配调用”的事实，不足以证明整套自定义路由实现必要。

当前 ErpRoutingDataSource 继承 AbstractDataSource，自行保存连接池映射、查找目标池并转发 getConnection。
Spring 的 [AbstractRoutingDataSource](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html) 已提供 targetDataSources、determineCurrentLookupKey、连接转发和已解析池映射；通用选库部分应优先复用。
这是强烈建议简化的补充候选，不能只以“有调用者”认定现有实现合理。

项目额外语义仍需明确保留或以测试证明等价：事务内换标识立即拒绝、跨 owner 事务拒绝、上下文异常恢复、连接池关闭，以及禁止请求凭证覆盖配置身份。
Spring 的 [DataSourceTransactionManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/DataSourceTransactionManager.html) 已管理线程绑定连接和事务生命周期；连接绑定本身不等于校验应用层标识中途变化。
采用标准路由时应不设置默认目标、关闭 lenient fallback，并明确拒绝缺失标识；仅设置 lenientFallback=false 仍允许 null 标识使用已配置默认库。

业务 Factory 路由另有版本/租约/旧池生命周期，不能直接推断与固定 ERP 连接映射完全等价，须分别审查。
本次仅更新架构判断和官方依据，未修改业务或基础设施 Java 代码、未实施替换、未运行新的等价性测试；现有第 9 节验证快照保持历史含义。

## 11. 2026-10-06 补充：生产监控与人员分析后置

用户明确决定：当前集中建设基础架构，撤出过早接入的生产监控与人员分析，后续业务接入时重新审查。本节覆盖此前保留这两项分析业务的范围结论。

- 移除整个 `mes-mes` 模块、父 POM 模块注册、admin 依赖及 `mes.analytics.enabled` 开关；当前 Maven 为 6 个 JAR 模块加父 POM，生产 business Mapper 数量为 0。
- 移除前端两项业务页面、组件和 API；保留混合回归中的基础组件用例，会话恢复验证使用 TEST ONLY 权限。系统监控、工作区、工厂/数据源、同步协调与三工厂隔离基础保留。ECharts 仍供系统缓存监控使用。
- 追踪并移除基础模块中仅为人员分析存在的 `SystemUserLookup`、`UserLookupQuery`、`SysUserDisplayExtraMapper` 和 XML，避免业务依赖留在基础设施里。
- 基础装配测试不再要求人员分析 Mapper 存在，改为验证生产业务 Mapper 为空；已有独立 TEST ONLY 探针继续验证 Factory A/B 路由、事务固定、跨 owner 拒绝与 ERP 隔离。没有为维持扫描计数增加生产业务探针。
- 保留 V13 原文件；新增 V15 前向迁移清理功能菜单、功能权限及角色菜单关联，仅删除已空的迁移创建生产分组，保留工厂/数据源及其他菜单。没有修改真实数据库或删除业务数据。已有实例应用 V15 前仍可能保留旧菜单。

备份见 [归档说明](../../migration-backups/2026-10-06-deferred-production-analytics/README.md)。`source.tar.gz` 中有 66 个功能文件和 10 个接入快照，`manifest.json` 记录仓库 HEAD、SHA-256 与文件用途；全部 76 项重新读取归档并校验哈希。包含原许可及迁移来源快照，不包含私有配置、凭证或业务数据，不参与构建。接入快照供审查，不能直接覆盖当前配置与历史迁移。

实际验证：

| 验证 | 结果 / 边界 |
| --- | --- |
| IDEA 原生 formatter，当前 `.idea/codeStyles/Project.xml` | 3 个修改/新增 Java 文件格式化通过。仓库目前不存在 `scripts/format-java`；本次直接调用已安装 IDEA `format.sh`，使用独立临时配置与从当前 Project 导出的同一 code scheme，未新增或替换仓库工具。 |
| `mvn -B -ntp test` | 通过；随后对最终源码执行干净 verify，排除删除源码的残留 class。 |
| `mvn -B -ntp clean verify` | BUILD SUCCESS；160 项，156 通过、4 个 opt-in 真实 MySQL 用例跳过，无失败或错误。 |
| V13 → V15 菜单迁移 fixture | 2 项通过，实际执行迁移资源；验证空分组撤回、共享分组/自定义菜单/基础管理及业务标记保留、角色菜单无孤儿。仅 H2 MySQL mode，不等于真实 MySQL 迁移验收。 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 5 项通过；安装包不含 mes-mes、业务类/Mapper 或人员查询适配器，隔离启动检查通过。未重启用户服务。 |
| UI Node 22：`npm test` / `npm run build` | 17 套、82 项测试通过；类型检查与生产构建通过。src 与 dist 均无两项业务引用。既有大 chunk 警告仍在。 |
| UI `npm run lint` | 未通过：41 个已有错误、1 个警告。本次修改的组件回归测试单独 lint 通过；sessionRestore 原始归档快照复现同一第 8 行错误。其他报错文件没有在本任务中修改。没有扩大本次范围修复用户工作。 |
| `python3 scripts/export-phase1-sql.py` | 导出 V1..V15 的有序 SQL，通过版本连续性检查；未执行导入。 |
| 两仓库 `git diff --check` / 备份 / 原有工作 | 通过；除本任务明确改动的报告与会话测试，原有脏文件哈希保持不变。未提交、推送或部署。 |

日志与原有工作哈希快照保存于 `target/deferred-business-removal/`，打包检查详情位于 `target/validation/`。未来业务恢复必须重新审查 Schema/SQL、Factory 与权限、响应契约和页面，不能将归档或原业务单元测试当作当前业务验收。

## 12. 2026-10-06 补充：五组 P1 判断复核

本次只复核当前源码与 POM，未修改生产代码、配置或测试，未执行新的污染复现测试。业务撤出没有自动消除以下基础设施问题。

| 项目 | 当前证据与结论 |
| --- | --- |
| P1-01 | OwnerMapperCatalog 构造器扫描固定包，OwnerMapperRegistration 随后注册 MapperScannerConfigurer 再扫同包；PersistenceFactories.create 再 addMapper。两次类路径扫描与职责耦合已确认；addMapper 是 MyBatis 内部注册，不能算第三次扫描或据此认定重复 Spring Bean。归属、重名、ERP 开关及类型集合消费者仍须保留。 |
| P1-02 | RequestBoundaryFilter 创建 UUID 写入 mes.requestId、响应头和 MDC；OpenApiRequestFilter 独立创建 UUID 写入 OpenApiHttp.REQUEST_ID，并覆盖响应头和 MDC。双 ID 已确认，OpenAPI 过滤器仍负责错误协议、访问日志和 SecurityContext 清理。统一 ID 可作为独立小改动，保留这些职责。 |
| P1-05 / P1-08 | FactoryContext 为普通 ThreadLocal，RequestBoundaryFilter 同步 finally 清理/恢复；未发现同线程 A→B 异步异常污染的完整验证，不能宣称已证实跨厂泄漏。PageUtils.startPage 设置状态，请求边界未统一 clearPage；SystemFileController.list 先 startPage，随后 SysAttachmentServiceImpl.list 的 policy 可在调用 Mapper 前抛错，是实际可触发的异常路径。需要先执行同线程异常退出与下一请求测试，再修复；测试自身 @AfterEach 的 clearPage 不能作为生产清理证据。 |
| P1-06 | SpringUtils.getBean 仍用于 DictUtils、MessageUtils、菜单权限及 Quartz 执行入口；用户/角色/部门 Service 仍用 getAopProxy(this)，其实现依赖 AopContext.currentProxy；启动类 exposeProxy=true。隐式依赖已确认，应逐消费者迁移并保持授权/数据范围拦截，不能直接删除工具或代理开关。 |
| P1-07 | 根 POM 有 27 个直接依赖，其中 19 个未声明 scope、默认 compile，其余包含 runtime/test。子模块继承父 dependencies，编译边界过宽成立。但 mes-common 当前已经包含 SpringUtils、RedisCache、BaseController、SecurityUtils、PageUtils 等框架代码，并同时承载 dev.mesbase.core 包。依赖下沉应匹配真实消费者；仅改 POM 无法禁止同一模块中 Core 导入合法供 common 使用的 Spring。纯 Core 的编译隔离需要另行明确模块边界，或采用包级架构检查，不能借依赖治理机械搬迁全部历史 common。 |

建议顺序：先对 ThreadLocal 写独立复现测试，同时以小改动统一 Request ID；Mapper 注册单独治理；依赖下沉先盘点消费者、限定目标边界；Service Locator 与当前代理依赖渐进处理。结构问题、可达风险和已执行的运行证据分别记录。

## 13. 2026-10-06 实施：P1 注册、请求状态与依赖优化

用户授权按第 12 节方向实施。没有重构业务规则或恢复已归档分析功能；三工厂归属、事务保护、权限和外部错误协议继续保留。

### 13.1 最终改动

- **P1-01：单次 Mapper 扫描。** `OwnerMapperRegistration` 直接使用标准 `ClassPathMapperScanner`，扫描候选同时交给 `OwnerMapperCatalog` 校验和记录。Catalog 不再自行扫描，也不再注册一层 MapperScannerConfigurer Bean。标准 Scanner / MapperFactoryBean 创建 Spring 代理；保留包重叠、接口重名、旧 owner 注解冲突、ERP 禁用拒绝规则。三组固定包和对应 SessionFactory 没有变。
- **保留必要的 MyBatis 初始化。** 生产工厂仍通过 `PersistenceFactories.create` 将已发现接口注册到 MyBatis，加载 XML。实际尝试仅等 MapperFactoryBean 初始化后注册，会使 `ManagedDatasourceService` 初始化阶段的程序式平台 Mapper 访问失败；装配测试复现此问题，因此保留提前初始化。它没有重新扫描类路径或重复创建 Spring Bean。程序式 fixture / 独立会话的注册路径仍有效，没有保留失败尝试中新建的 scanned 工厂入口。
- **P1-02：统一请求 ID。** RequestBoundaryFilter 负责创建并缓存服务器 ID，复用请求属性跨 dispatch 保持身份；OpenApiRequestFilter、OpenApiHttp、ErrorWriter 使用同一 ID。OpenAPI 错误协议、访问日志、SecurityContext 清理保留。客户端 X-Request-Id 不作为服务器 ID。
- **P1-05 / P1-08：修复请求线程状态。** 边界 Filter 显式参与 ASYNC dispatch，在进入和退出时清理 PageHelper；Scope 清理与分页/MDC 清理采用嵌套 finally，避免一个清理异常跳过另一个。原有嵌套 Scope/MDC 恢复规则保留。没有隐式传播 FactoryScope 到任意后台线程；后台作用域仍须显式通过 withScope 管理。
- **P1-06：有界清理静态查 Bean。** SysMenuServiceImpl 的权限服务、SysJobController 的执行器改为显式注入，移除这三个 getBean 调用点。Quartz 创建的作业对象、公共静态工具，以及用户/角色/部门 Service 的 getAopProxy 调用仍保留；exposeProxy=true 继续支持现有授权/数据范围拦截。这些剩余消费者作为后续渐进治理项。
- **P1-07：依赖按消费者下沉。** 根 POM 不再声明供所有子模块继承的直接依赖（27 → 0），自定义版本进入 dependencyManagement，Boot 管理的版本沿用其 BOM。common/system/framework/quartz/generator/admin 显式声明各自依赖；测试依赖只在有测试的模块以 test 声明，驱动与数据库迁移扩展由 admin 以 runtime 装配。没有升级库版本或新增纯 Core 模块。common 仍容纳历史 Spring/Redis/Web/分页工具；Core 包的禁止依赖检查扩展到 MyBatis/PageHelper，增加父 POM 无直接依赖的回归约束。

### 13.2 复现与验证

先新增 RequestStateLifecycleTest，未修复时 3 项均失败：Mapper 前异常留下 Page、模拟 ASYNC 异常留下 Scope、OpenAPI 内外 ID 不同。修复后同一测试 3 项通过，测试检查状态发生在测试清理钩子之前。

新增 AsyncRequestBoundaryHttpTest，实际启动隔离嵌入式 Tomcat，限制为一个 Servlet worker。异步正常响应及异常响应中设置 Scope/Page 后，下一请求在边界 Filter 外观察状态为空，且响应记录证明复用了同一个线程；正常异步响应还验证初始请求属性、ASYNC 属性、MDC 与响应头 ID 一致。2 项通过。此证据覆盖 Servlet REQUEST/ASYNC 生命周期，不替代任意后台执行器或真实业务数据库验收。

新增 MapperRegistrationGuardTest，重名、注解归属冲突、禁用 ERP 时存在外部 Mapper 的 3 项负例通过，断言实际根因，避免将其他启动失败误当作 guard 生效。原生产装配和 H2 三工厂隔离、路由/回滚/固定连接测试继续通过。

| 命令/证据 | 结果 |
| --- | --- |
| IDEA 原生 format.sh + 当前 Project code scheme | 本任务涉及的 18 个 Java 文件格式化通过；仓库仍无 scripts/format-java，沿用第 11 节的原生 formatter 临时入口。未格式化其他文件。 |
| `mvn -B -ntp test` | 完整测试通过；新增真实异步用例随后经定向测试及最终 clean verify 验证。 |
| `mvn -B -ntp clean verify` | 最终 BUILD SUCCESS；全 reactor 179 项，175 通过、4 个 opt-in 真实 MySQL 用例跳过，0 失败/错误。 |
| common 的过滤 `dependency:tree` | 查询 MyBatis/Quartz/Flyway/Druid/Velocity，无匹配依赖；没有把 common 宣称为无 Spring 的纯 Core。 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 6 项通过；本地隔离 JAR 启动及 HTTP 边界通过；保留 SQL Server/MySQL/PostgreSQL 驱动与 Flyway 扩展，继续排除 H2、测试及归档业务代码。 |
| `git diff --check` / 构建输入哈希 | 通过；记录的 Java/POM/打包脚本输入在最终构建及检查之间没有漂移。 |

复现、格式化、最终构建、依赖树及打包日志保存到 `target/p1-architecture-optimization/`；打包检查 JSON 在 `target/validation/`。没有连接真实业务/外部数据库、执行真实迁移、重启用户服务，未执行提交/推送/部署。工作期间仓库 HEAD 有其他提交推进，本任务未重置工作区或回退这些提交。
