# Base MES HTTP 日志审查与 P0 改造

日期：2026-10-14；分支 main。范围：HTTP 技术日志；不改数据库、业务响应、权限、操作/登录/任务审计。不提交、推送、部署或重启用户服务。用户已有 production-chain-audit-review.md 修改不触碰。

## Phase 1：修改代码前的只读事实

### 调用链及 requestId 生命周期

`mes-framework/src/main/java/dev/mesbase/platform/web/RequestBoundaryFilter.java`（HIGHEST_PRECEDENCE+20）在 Security 外生成 UUID，存在 mes.requestId 属性时继承，忽略所有客户端 X-Request-Id；设置响应 Header 和 MDC，finally 恢复原 requestId、FactoryContext，并清理 PageHelper/请求绑定。ASYNC 派发复用属性；ERROR 默认被 OncePerRequestFilter 跳过。属性仅检验非空，尚无字符/长度限制。未恢复完整 MDC。无第二个 UUID HTTP 生成器。

`mes-admin/src/main/java/dev/mesbase/server/FoundationConfiguration.java` 普通 Security 链；`OpenApiDocumentationSecurityConfiguration` 文档链；`mes-framework/src/main/java/dev/mesbase/framework/openapi/OpenApiSecurityConfig.java` OpenAPI 链（order 0）互斥。SystemTokenFilter 的 Servlet 注册已禁用，只在 Security 链运行；Basic/Bearer、拒绝处理器保持既有响应。`FoundationAccessInterceptor` 解析 Factory/当前权限；`BusinessRequestSnapshot` 固定绑定，事务租约由基础设施负责。

`FoundationErrorAdvice` 是普通 MVC 唯一异常 Advice，ServiceException 为401/403/422，FoundationException 使用现有 ErrorCode，未预期异常500并ERROR完整堆栈。MVC异常处理器没有将异常转HTTP200。但 Source Controller 仍有主动返回 AjaxResult.error 的普通结果：SysUserController/SysRoleController/SysDictTypeController 的编码冲突，SysJobController 的Cron/白名单校验等；这些可为HTTP200且body失败。本任务保留这些响应，摘要没有businessSuccess字段。`ErrorWriter` 负责Filter/Security现有错误协议。OpenAPI专用 `OpenApiExceptionResolver` 优先接管MVC异常，`OpenApiRequestFilter` 接管Filter异常；沿用其AjaxResult/errorCode/requestId契约，不重新设计。

### HTTP 技术日志与缺口

普通HTTP无统一开始/结束日志；默认Boot Logback配置无显式MDC requestId。OpenAPI已有 finally INFO访问日志（客户端/API/时间/状态/错误码/peerIp/脱敏异常链），同步返回即记录，异步下可能提前记录。没有请求体/query/token/cookie采集。Foundation已知4xx打印WARN堆栈，5xx也为WARN；OpenAPI系统错误仅INFO截断类型/源码链。`FactoryMetadataCache.read` ERROR后包装抛出，后续Advice再打印链，存在重复。审计持久化失败/退休池清理/缓存提交后淘汰失败属于独立副作用，不删除其错误记录。业务ERP UNKNOWN捕获是业务结果处理，不作为普通异常重抛重复。

### 数据库日志（只读，不修改）

|对象/表|Entity / Mapper / Service|触发/数据源/事务|Factory与重叠|
|---|---|---|---|
|sys_oper_log|mes-system SysOperLog / SysOperLogMapper(XML) / SysOperLogServiceImpl|mes-framework LogAspect @Log，finally写入；masterSqlSessionFactory/masterDataSource；具名masterTransactionManager REQUIRED|factoryId来自FactoryContext，requestId/actorId/scope/耗时；不采集body，技术摘要不能替代操作审计|
|sys_logininfor|mes-system SysLogininfor / SysLogininforMapper(XML) / SysLogininforServiceImpl|SysLoginService成功/失败及SysRegisterService注册；中心库，插入masterTransactionManager REQUIRED|模型有factoryId/scope，登录入口通常未绑定工厂，写requestId/actorId；技术401摘要不替代登录审计|
|sys_job_log|mes-quartz SysJobLog / SysJobLogMapper及JobClaimMapper / SysJobLogServiceImpl、ControlledJobRunner|手工/Quartz执行与恢复，PlatformPersistence具名中心事务完成claim/release/log|factoryId/executionScope/runToken；手工带HTTP id，调度独立UUID，不强制后台MDC|
|mes_master_sync|mes-system/sync MasterSync / MasterSyncMapper；mes-framework/sync SyncCoordinator，既有状态表|中心库同步抢占/续期/结束；状态协调而非HTTP访问日志|factory_id+sync_key+run_token，不修改|

现有审计Service REQUIRED不宣称跨中心/业务原子审计；审计AOP自身失败为best effort，错误独立记录。Quartz ExecutionScope清理/恢复安全及Factory上下文，未普遍设置/传播HTTP MDC。ControlledExecutor显式传递ExecutionScope、不自动传MDC，队列满时可能在调用线程执行；MessageEventHub有独立SSE心跳线程。普通SLF4J MDC不自动跨线程；本任务不将不相关后台任务绑定HTTP。

### P0方案与停止条件

复用RequestBoundaryFilter与既有错误处理，不新增通用业务AOP。保留外部Header忽略策略，服务端属性限制ASCII安全字符和128长度；完整MDC保存/恢复。显式注册REQUEST/ASYNC/ERROR，处理嵌套ERROR时仅绑定MDC，不重做数据源准入。Filter保存开始时间、原路径、最终response及现有Factory/可信userId快照；ServletRequestListener.requestDestroyed一次输出最终status/耗时，覆盖异步与错误派发，不依赖数据库。排除路径通过配置管理。

OpenAPI访问日志在同一请求销毁时输出同一completed事件并保留专用字段；系统错误ERROR完整类型/源码堆栈并保留凭证消息脱敏。普通已知拒绝INFO无堆栈，5xx与未预期异常ERROR；请求内异常链去重，保留非HTTP原日志。应用console/file pattern显式输出MDC。

预计修改：RequestBoundaryFilter、完成监听器/Servlet注册、FoundationErrorAdvice、SystemTokenFilter身份快照、FactoryMetadataCache重复异常记录入口、OpenApiRequestFilter、application.yml、测试。现有响应、工厂/安全行为无修改必要；可以业务代码零侵入。未发现重大冲突，进入Phase 2。

## 非 P0 与未验证边界

不改变审计事务、Quartz业务/任务标识、SSE协议、外部ERP日志、SQL DEBUG策略或采样/日志平台。服务端完整异常可能含SQL/内部路径，受运维日志访问控制；OpenAPI保留更严格消息脱敏。HTTP200仅运输状态，不推断业务成功。生产访问量、真实用户服务热加载/部署、真实数据库审计写入不由源码阅读证明。

## Phase 2：实现与行为差异（Change Summary）

全部生产修改限于基础设施，业务Controller/Service/Mapper、数据库审计实现均未修改。无新依赖、表、SQL、迁移、数据库访问或数据源切换。HTTP错误响应、status、Factory选择/权限规则保持既有契约。

|文件（仓库相对路径）|修改前 → 修改后|
|---|---|
|mes-framework/src/main/java/dev/mesbase/platform/web/RequestBoundaryFilter.java|单个MDC键恢复 → 完整MDC恢复；服务端属性仅非空 → 安全ASCII、1–128字符校验；外部Header仍一律忽略。记录请求状态，异步/错误派发继承ID，原清理规则保留；未处理异常只记录后原样抛出|
|mes-framework/src/main/java/dev/mesbase/platform/web/HttpRequestCompletionListener.java|新增Servlet生命周期监听器，REQUEST/ASYNC/ERROR之后一次完成日志，单调耗时、实际终态HTTPstatus、原始method/path、已取得Factory/userId快照；不解析body/query/敏感Header，不查询身份/工厂数据|
|mes-admin/src/main/java/dev/mesbase/server/HttpLoggingConfiguration.java|明确现有边界Filter注册顺序、async支持和REQUEST/ASYNC/ERROR；复用原Filter Bean，不重复注册SecurityFilter；注册完成监听器和配置排除路径|
|mes-admin/src/main/resources/application.yml|Boot Logback默认level pattern → level显式包含MDC requestId（默认console/file均使用）；新增mes.http.logging.exclude-paths，空值可关闭排除|
|mes-framework/src/main/java/dev/mesbase/platform/web/HttpFailureLogging.java|新增现有异常边界共用的日志策略；4xx已知拒绝INFO无堆栈，5xx/未预期ERROR保留链，按请求内Throwable身份/原因链去重；不接管异常或改变传播|
|mes-framework/src/main/java/dev/mesbase/platform/web/FoundationErrorAdvice.java|已知Foundation WARN堆栈/Service WARN → INFO无堆栈且稳定错误码；系统异常调用共用ERROR策略；所有ResponseEntity/body映射保持|
|mes-framework/src/main/java/dev/mesbase/framework/security/SystemTokenFilter.java|只从已认证LoginUser保存userId（无新查询）；准入基础设施错误统一ERROR，响应仍既有503；预期安全拒绝只需现有完成摘要|
|mes-framework/src/main/java/dev/mesbase/framework/openapi/OpenApiRequestFilter.java|Security Filter finally提前访问日志/MDC重复绑定 → 由共用完成监听器输出一次completed事件；保留clientId/appKey/apiCode/errorCode/peerIp，系统异常另一个failed ERROR事件包含完整源码/cause/suppressed堆栈，去除可能含凭证的异常message。安全上下文清理及错误转换保持|
|mes-admin/src/main/java/dev/mesbase/server/managed/FactoryMetadataCache.java|ERROR后再次Advice打印同一异常 → HTTP记录包装异常并共享去重；非HTTP原ERROR行为保留。提交后缓存淘汰失败为独立副作用日志，不修改|
|mes-admin/src/test/java/dev/mesbase/HttpLoggingHttpTest.java|新增真实随机端口Tomcat、安全链及请求销毁测试；同步/业务/系统/401/403、多Factory、Callable、直接async.complete、超时、ERROR派发、日志次数和配置排除|
|mes-admin/src/test/java/dev/mesbase/platform/web/HttpLoggingBoundaryTest.java|新增完整MDC/Factory恢复、服务端属性/外部Header、嵌套ERROR、最终status/userId、异常去重、503准入、日志字段注入测试|
|mes-admin/src/test/java/dev/mesbase/framework/openapi/OpenApiLoggingTest.java|新增共用完成事件、400拒绝/500系统异常、完整40帧及suppressed、消息/Token脱敏测试（Mock Servlet与真实Filter）|
|mes-admin/src/test/java/dev/mesbase/HttpLoggingAuditPreservationTest.java|新增原LogAspect AOP成功/失败及原SysLoginService成功/拒绝回归，审计接收端为Mock，验证原记录数/status/requestId/actor/Factory|

### requestId与异步边界

客户端不获得控制服务端关联ID的权力；合法服务端属性可以继承，不额外建立第二套ID。HTTP REQUEST/ASYNC/ERROR派发期间绑定MDC，每次返回恢复所有原MDC字段。嵌套ERROR仅绑定/恢复MDC，不重做Factory清理/准入。完成监听器自身也保存/恢复MDC。非HTTP任务、Callable/DeferredResult/AsyncContext.start的生产者工作线程不自动继承HTTP MDC；异常回到ASYNC派发和最终完成事件仍关联同一ID。SSE只在连接结束时记完成，连接持续时间即duration。

### 日志示例（简化展示，ID/耗时为说明值）

```text
INFO  event=http.request.completed requestId=9a9f50bd-4229-4650-91ec-116510a42c1f method=GET path=/api/foundation/context status=200 durationMs=12 factoryId=A userId=42
INFO  event=http.request.rejected requestId=19f1d41b-62fc-4d16-9b36-757e6ca23a39 errorCode=BUSINESS_REJECTED
INFO  event=http.request.completed requestId=19f1d41b-62fc-4d16-9b36-757e6ca23a39 method=POST path=/api/example status=422 durationMs=5 factoryId=A userId=42
ERROR event=http.request.failed requestId=6bd00ef2-e265-4e4f-8aa9-60a4e99f4d83 errorCode=INTERNAL_ERROR
java.lang.IllegalStateException: ... [完整原始堆栈与cause，仅普通内部HTTP]
INFO  event=http.request.completed requestId=6bd00ef2-e265-4e4f-8aa9-60a4e99f4d83 method=GET path=/api/example status=500 durationMs=8 factoryId=A userId=42
INFO  event=http.request.completed requestId=532d7f6e-6fcf-4aa1-b286-0c8046e2b171 method=GET path=/api/foundation/context status=401 durationMs=2 factoryId=null userId=null
INFO  event=http.request.rejected requestId=218e9a44-8627-41ea-a71b-aa7c409afba2 errorCode=FORBIDDEN
INFO  event=http.request.completed requestId=218e9a44-8627-41ea-a71b-aa7c409afba2 method=GET path=/api/foundation/access status=403 durationMs=4 factoryId=B userId=42
```

failed/rejected和completed是不同语义事件；每个请求只有一条completed。Security原生拒绝可以只记录completed，不额外打印拒绝堆栈。OpenAPI系统错误完整源码类型/栈保留，但异常消息继续脱敏。clientId为外部身份，不伪装成内部userId。

## 验证结果与证据等级

- 工作区早期定向：`mvn -B -ntp test -Dtest=FoundationHttpTest,AsyncRequestBoundaryHttpTest,SystemTokenFilterAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false`：18通过，0失败/错误/跳过；`/tmp/mes-http-log-target.log`。
- 日志新增测试最终12项：7边界、2真实Tomcat组合用例、1OpenAPI组合、2审计回归；均通过。这些数量是JUnit用例数量，单个组合用例包含多个HTTP场景。
- 本轮12个Java文件IDEA格式检查通过，最后修改的2个文件再次check通过（含最终OpenAPI Filter外系统异常脱敏分支）；`/tmp/mes-http-log-format-check.log`、`/tmp/mes-http-log-format-check-last.log`、`/tmp/mes-http-log-format-check-final.log`。`git diff --check`本轮路径通过。
- **共享工作区全量verify未通过**：最初并行编辑MesMaterialIssueEntity重复成员，`/tmp/mes-http-log-verify.log`；最终复跑时production规则仍在变更，`LocalWarehouseErpTest`导入已不存在的MaterialIssueCreateCommand，业务模块testCompile报2处找不到符号，`/tmp/mes-http-log-workspace-final-verify.log`。不是本轮日志代码错误，不修复/还原并行任务文件；未将全量工作区标成通过。
- **隔离副本最终全量verify通过**：基线`c649d2da8d5e517fc917727d9676424243e24788`的Git archive + 本轮13个代码/配置/测试文件，目录`/private/tmp/mes-http-logging-validation-20261014`；SHA-256确认这些文件与共享工作区最终版本一致。使用标准Maven/Java21，不创建私有Maven缓存。
  命令：`env PATH=/opt/homebrew/bin:/Users/user/apache-maven-3.6.1/bin:/usr/bin:/bin:/usr/sbin:/sbin mvn -B -ntp verify -Dmes.test.redis=true`。425项，总计415通过，0失败/错误，10项真实MySQL opt-in跳过，BUILD SUCCESS；`/tmp/mes-http-log-isolated-final.log`。测试用独立Redis/H2 Fixture及随机端口Tomcat，不操作用户服务。
- 初次隔离verify因测试子进程命中旧Intel Redis报Bad CPU type，重跑命令仅限定本次PATH，使用已安装ARM Redis8.10.2；不修改全局环境/项目配置。
- **隔离副本打包烟测**：`/usr/bin/python3 scripts/smoke-packaged.py`：10通过，0失败；`/tmp/mes-http-log-smoke-final.log`；自有临时隔离JAR进程，不启动/停止/重启用户服务。不将副本产物替换用户运行JAR。
- 没有改前端，未运行UI构建/浏览器验收；没有写SQL/数据库，无真实审计数据库写入验收，没有部署/生产观测。用户当前运行服务未复验，不宣称日志改动已经加载。

## 剩余风险与回滚

1. 不自动传播MDC到异步生产者或不相关后台线程；自建后台代码自行修改MDC后不清理的风险不由HTTP边界覆盖。
2. 未预期Filter异常原样继续抛给容器；应用异常链已去重，但Tomcat自身可能再次记录Servlet错误，不能为去除容器日志而吞异常/改变传播。本轮真实ERROR派发验证完成摘要只有一次。
3. 新摘要只含白名单字段，不含body/query/header/token/cookie/password；method/path/Factory及OpenAPI文本字段有界且替换空白/等号，防止日志字段注入。路径段本身若被业务设计为敏感信息，仍会出现在path；普通系统异常完整message/cause可能包含内部SQL/路径，现有SQL DEBUG也不在本轮全量脱敏范围。OpenAPI保留严格消息脱敏。
4. 每个未排除HTTP请求一条INFO，拒绝/系统异常另有语义事件；高访问量和长期SSE的量/时序需部署后观察，本轮不增加采样/日志平台。
5. 可回滚本轮13个代码/配置/测试文件及本Review；不触碰并行修改。没有数据迁移回滚需求。

## Document Created

本文件新增Phase 1调用链/requestId/审计/安全证据，以及Phase 2修改清单、示例、验证/失败和剩余风险。业务代码与数据库审计实现未修改；基础设施代码有上述P0变更。证据覆盖源码、Mock/Fixture、真实嵌入式HTTP和隔离JAR；不覆盖当前用户服务/真实MySQL/部署生产。
