# Factory Foundation clean-room 迁移执行报告

> 日期：2026-10-05。目标目录：`/Users/user/work/lixin/mes-base`。状态：本阶段实现及本地验收完成，停止于 Factory Foundation。正式部署、生产库/ERP联调及后续业务迁移均未执行。

本轮独立创建一个 Java21 / Spring Boot 模块化单体，初始化 Git main，没有复制旧工程，也没有恢复 UserFactory 关系。有效账号允许空角色认证和进入启用厂；当前厂 Permission 决定 AUTHORIZED 能力。最终 **55项测试通过，0失败/错误/跳过；11项打包检查通过，0失败**。

## 1. 输入、有效结论与 clean-room 范围

已读取全链路能力审查中的身份/范围、敏感资产、架构与阶段建议；未合并分支专项审查及 evidence index；重点复核最新第17节和证据第7节，以及分支的范围、访问选择、runtime binding、事务与注册机制。master只核对公共安全/错误/范围边界，最新外部应用范围存在性门禁不套用为普通用户必须有厂角色。

| 输入 | 定位/版本 | 使用方式 |
| --- | --- | --- |
| 全链路审查 | `/Users/user/work/heri/ktg-mes/docs/mes/2026-10-05-mes-base-capability-review.md` | clean-room资产边界、最小单体与迁移顺序 |
| 分支专项审查 | `/Users/user/work/heri/ktg-mes/docs/mes/2026-10-05-factory-foundation-branch-review.md`，第17节为当前有效结论 | 取代早期UserFactory准入假设，确定认证/范围/权限分离 |
| Foundation证据索引 | `/Users/user/work/heri/ktg-mes/docs/mes/2026-10-05-factory-foundation-branch-evidence.md`，第7节 | 对照失败场景、连接/任务/缓存和分类缺口 |
| 后端分支 | `feat/multi-factory-isolation` / `bdc299dade855177a3e275199ffde6e72310e393` | 只读设计与问题证据，不merge/cherry-pick/复制 |
| 当前后端master | `310c6ef1bb37d2533fe893579ba837c353b76926` | 最新平台参考；本轮开始/末检HEAD一致，工作区保持clean |

旧审查文档仍在原目录，只读；本目录的规格/ADR重新表述语义，没有复制旧Java、Mapper/XML、DDL、配置、页面或专有数据。默认环境不含任何公司地址、凭证、角色/菜单/字典、ERP组织或专有接口。合成probe的Schema仅在测试源创建；主JAR不含测试类或H2驱动。

旧Web现有 `src/views/system/openApiClient/index.vue` 修改，以及移动目录原有未跟踪项均未恢复或覆盖。末检状态、HEAD与已跟踪diff指纹只作保护记录，见 verification.json；不是对未跟踪/忽略文件的完整前后快照。本轮所有文件写入与Git初始化均限新目录，未切换旧仓库分支或写入旧项目。

## 2. 创建的工程与技术选择

单个 Maven module：`dev.mesbase:mes-base:0.1.0-SNAPSHOT`，没有空子module或微服务。

```text
mes-base/
├── pom.xml / .mvn/ / AGENTS.md / README.md
├── src/main/java/dev/mesbase/
│   ├── core/factory, core/security, core/error
│   ├── platform/execution, platform/web
│   ├── infrastructure/catalog, security, cache, persistence
│   ├── integration/erp
│   └── server
├── src/main/resources/application.properties
├── src/test/java/dev/mesbase/          # TEST ONLY probe/账号及HTTP故障入口
├── scripts/smoke-packaged.py
└── docs/architecture/ + docs/reviews/
```

55个主Java文件、7个测试Java文件。依赖与构建输出保存在项目内 `.m2/`、`target/`，由Git忽略；公开依赖仅从项目配置的 Maven Central 下载，不沿用公司仓库/凭证配置。Git已初始化main，尚无commit/push。

Java使用本机 Amazon Corretto 21.0.4，Maven实际3.9.10。Spring官方当前Stable为4.1.1、兼容Java17–26，本轮因此选择4.1.1，并验证公开依赖可解析与Java21构建：[官方系统要求](https://docs.spring.io/spring-boot/system-requirements.html)。不沿用旧Java8/Boot2或RuoYi脚手架；MyBatis直接装配，无旧XML或动态数据源框架。

`server`负责HTTP DTO和装配；Core没有Spring/ThreadLocal/Adapter依赖。平台与基础设施消费显式Scope。真正的业务Service/Mapper/Port应随后续领域进入，本阶段不提前造CRUD/工单/报工框架。

## 3. 已实现能力、证据与仍有限制

| 能力 | 新实现 | 验证/边界 |
| --- | --- | --- |
| Enterprise→Factory | 独立Enterprise值、Factory企业归属/编码/名称/启用状态、FactoryId | 无连接/ERP字段；目录adapter为本地内存，不是持久组织后台 |
| FactoryScope | 仅一个FactoryId字段的不可变record | Core范围与用户、权限、连接完全分离 |
| FactoryContext | bind/current/required/clear、嵌套/异常finally恢复、事务pin | Core显式收Scope，不调用ThreadLocal |
| Authentication | Spring Security凭证校验，账号启停/不存在/错误密码拒绝 | 有效空角色账号可认证；默认无账号，demo/test合成账号 |
| Authorization | Actor/Permission/Role/AuthorizationService；角色按厂，逐次查询grant | A权限不会授予B；无默认admin/账号编号例外；无完整IAM后台 |
| HTTP分类 | 方法级PUBLIC/AUTHENTICATED/AUTHORIZED；启动检查、MVC门 | 所有7个主映射都显式分类；非MVC新入口仍需单独设计 |
| FactoryPreference | 显式Factory优先，有效lastFactory恢复，失效时空Scope | 不作为授权；不默认编号1/首厂；进程重启丢失偏好 |
| RuntimeBinding | factoryId/datasourceIdentity/revision；可信resolver、registry | 无Scope/未知厂/缺池/版本错即失败；没有Business fallback |
| Dedicated持久化 | Factory→独立pool；同厂嵌套Spring本地事务；固定connection | 单厂/双厂真实H2同号读写隔离；不是生产目标DB联调 |
| Pool替换与排空 | 递增revision、新pool接管新工作、旧pool按lease排空 | 旧事务遇版本变化拒绝/回滚；节点内机制，无集群即时撤销承诺 |
| Mapper/session/TM | 显式归属、每runtime独立会话与TM、独立平台会话/TM | 缓存前/每调用校验；跨厂/连接/版本/线程/外部事务拒绝；无分布式事务 |
| ExecutionScope | PLATFORM清Scope，FACTORY必填；显式capture受控executor | finally恢复、CallerRuns、无用户身份传播；没有实际Quartz业务任务 |
| Cache | 结构化Platform/Business CacheKey，环境/企业/厂/namespace/key | A/B相同key不冲突；无业务Scope失败；只交付键契约，不是Redis存储 |
| ERP Foundation | ConnectionBinding/Provider/Resolver/Adapter Port | 默认NOT_CONFIGURED抛明确422；没有真实供应商适配器或空占位类 |
| 错误/关联ID | 真实400/401/403/404/409/422/500，405/415协议错误；安全DTO | 服务端保留原异常链；前端不出SQL/路径/连接/原始原因；ID由服务器生成 |
| Shared契约 | FactoryOwned/ScopedIdentity/SharedDatabasePersistence | 仅设计与验收计划；未注册实现，也没有共享业务表/行隔离证明 |

主映射是 `/health`、Factory选项、当前Scope、本人Preference、受控Foundation access、ERP配置查看及安全 `/error`。HTTP成功返回明确DTO/数组，错误体包含errorCode/message/requestId，不迁旧AjaxResult。DTO拒绝不支持的客户端身份字段。完整协议与运行说明见README。

## 4. 借鉴的设计思想与独立改动理由

| 来源问题/旧行为 | 本轮提炼的思想 | Base选择及验收 |
| --- | --- | --- |
| 有效账号+空角色允许登录/选厂，初审曾误判为缺准入表 | scope与能力许可分离 | 保留确认后的语义，不建UserFactory；真实HTTP200/403负例 |
| 混合上下文、缺范围cache退全局、部分异步缺Scope | 不可变范围、显式捕获、finally恢复 | Scope缩至FactoryId；连接/ERP/授权独立；无Scope缓存失败 |
| 严格路由/identity+revision、默认入口选厂 | 无Business默认库、版本化registry | 同样严格但独立实现；去掉首厂/固定编号回退；池lease排空 |
| 会话按Mapper归属固定，但漏清单导致误路由 | 所有者必须可检查 | 明确注解+注册集合，未知归属拒绝；平台/业务各自会话/TM |
| 事务初选固定，单检查getConnection不足 | SQL/缓存入口前及事务末尾校验 | 代理、Executor、callback末尾、beforeCommit、Scopepin；真实回滚验证 |
| 平台与Factory任务区分，任务传播不是授权传播 | ExecutionScope明确语义 | 受控池显式scope、CallerRuns也清身份；不迁旧业务调度 |
| 固定外部连接/ERP组织夹在Factory中 | 连接解析作为Integration Port | NOT_CONFIGURED明确失败；不复制供应商配置/单据实现 |
| 主线真实HTTP错误和requestId是公共方向 | 安全错误/关联ID由平台统一 | 独立错误模型，不复制OpenAPI安全链或应用表；真实HTTP检查 |

每项背景、备选方案、决定、测试及尚未覆盖均写入ADR。以上是需求/问题及技术约束提炼，不是源码复用说明。

## 5. 明确没有迁移

没有WorkOrder、Routing、Reporting、WIP、Quality、WMS、生产监控、人员分析、工单归档、热处理、wheel、客户/销售/工资/设备专有业务；没有Web/PDA/UniApp页面；没有实际ERP单据、外部SQL或HTTP调用。

没有RuoYi权限代码全集、菜单/角色/字典、UserFactory表、JWT/Redis Session复制、生产Schema或既有Mapper。没有Quartz JobStore、实际定时同步、消息/事件总线、完整共享库隔离、数据源/权限管理后台或数据迁移脚本。`RuntimeFixture.Probe`是TEST ONLY，不成为任何后续业务模型。

## 6. 真实执行命令与结果

命令工作目录全部为 `/Users/user/work/lixin/mes-base`；`JAVA_HOME`设置为上述Java21安装位置。临时JAR验证只启动本项目随机loopback端口并关闭自己的进程，不操作原服务。

| 执行 | passed | failed/error | 结果证据 |
| --- | --- | --- | --- |
| `JAVA_HOME=<本机Java21> mvn -s .mvn/settings.xml -B -ntp test`（初次基础编译） | 0测试 | 0 | 当时尚无测试，不能作为验收；`target/validation/build-initial.log` |
| 同命令（HTTP加入后编译） | 未执行测试 | 编译1错误 | Role与Spring注解同名导入冲突，修正；`build-compile.log` |
| 同命令（第一轮完整测试） | 39/50 | 3 failures + 8 errors | Mapper代理反射访问受包可见性阻挡；改为委托MyBatis InvocationHandler；`build-tests.log` |
| 同命令（修复后） | 52/52 | 0/0 | `build-tests-fixed.log` |
| `JAVA_HOME=<本机Java21> mvn -s .mvn/settings.xml -B -ntp verify` | 54/54 | 0/0 | 补输入/内部错误及池所有权负例；JAR打包成功；`verify.log` |
| 同`verify`命令（最终） | **55/55** | **0/0** | 增补平台会话跨线程保护；`verify-final.log`、Surefire XML |
| `MES_JAVA_BIN=<本机Java21/bin/java> python3 scripts/smoke-packaged.py`（最终） | **11/11** | **0** | `packaged-smoke.json`、`smoke-final.log`、default/demo进程日志 |

测试计数来自本轮最终Surefire XML，不使用旧工程/审查测试通过数。

| 最终测试类 | run | failures/errors/skipped | 重点 |
| --- | --- | --- | --- |
| FactoryScopeTest | 5 | 0/0/0 | 绑定/嵌套/异常/事务pin |
| ExecutionScopeTest | 6 | 0/0/0 | PLATFORM、Factory必填、受控线程、CallerRuns、身份清理 |
| AuthorizationPreferenceCacheTest | 8 | 0/0/0 | 空角色、A/B权限、偏好、缓存、ERP缺配置 |
| DedicatedDatabasePersistenceTest | 20 | 0/0/0 | 单/多厂双库、事务/版本/所有权、回滚、排空与线程归属 |
| FoundationHttpTest | 13 | 0/0/0 | 真实Tomcat HTTP账号/权限/范围/错误/并发/关联ID |
| ArchitectureBoundaryTest | 3 | 0/0/0 | 未分类门、Core依赖、probe只在tests |
| 合计 | **55** | **0/0/0** | 无跳过 |

打包检查包括：JAR无测试类/H2、默认profile存活与401、demo存活与401、无角色A/B均200、无角色AUTHORIZED403、reader A200/B403、ERP未配置422。脚本自建短期合成密码，只留内存，不打印或存盘；强制classpath本地配置与loopback，排除继承的Spring/MES服务配置及Java注入环境项。两个进程均已结束。

安全500测试故意制造合成内部异常，日志的ERROR是预期负例，原链留服务端，HTTP只给稳定500消息。H2是真实JDBC执行的内存Fixture，不是生产库；HTTP是真实loopback请求，账号/角色仍是测试adapter；打包demo不是外部联调。没有Redis/ERP/生产库连接、目标数据库方言验收、UI设备、部署或生产观测。

原始日志在target，Git忽略；可随构建重新生成。可保留的机读摘要、JAR SHA256及末检源仓状态为本报告旁的 `2026-10-05-factory-foundation-verification.json`。

## 7. 部署拓扑验收状态

| 拓扑 | 状态 | 本轮证明 | 不能推导的结论 |
| --- | --- | --- | --- |
| 单厂独立库 | **IMPLEMENTED** | 同一Factory/Core/adapter，Factory A→真实H2 DBA | 生产MySQL/PostgreSQL部署已通过 |
| 多厂独立库 | **IMPLEMENTED** | 同一代码A/B→不同H2库，同ID读写/并发隔离；无Scope及事务换厂失败 | 任意运维连接别名都安全、跨节点即时撤销或分布式事务 |
| 多厂共享库 | **DESIGNED** | 中性Scope/FactoryOwned/port与ADR-004的行级测试计划 | 已存在可运行Shared adapter或SQL行级范围保护 |

单厂/多厂区别只在Fixture目录与绑定数量。主代码没有SingleFactoryMode或Single/Multi分支。Shared实现不得把多个Factory挂到Dedicated身份再宣称隔离；需另行通过完整行级查询/写入/关联/缓存/约束计划。

## 8. 已知风险、未验证边界与下一阶段

1. 默认只有空配置和安全门；demo/test是本地内存目录、角色和偏好，不是持久平台产品。正式试点需真实身份/目录/授权/偏好适配器及独立安全配置，不携带公司现有数据。
2. Basic仅是本地基础方案；HTTPS/限流/密码策略/持久审计与生产入口治理未交付，不应直接按公网生产服务发布。
3. Registry、配置map、线程围栏与排空均节点内；真实驱动故障、节点崩溃、集群配置失效/停用、池关闭异常恢复和提交窗口协调尚未验证。连接identity与实际schema一致性由未来配置适配器核对，不能用opaque命名证明物理库不同。
4. 平台/业务是独立本地事务，交叉事务拒绝，不提供XA或外部回滚。只支持显式工作单元及同厂REQUIRED嵌套，未交付任意注解传播、跨库Join/写入或REQUIRES_NEW业务框架。
5. Shared只有设计；缓存只有键契约；ERP只有Port/缺配置失败；调度只有执行scope生命周期。没有文件ownership、业务对象范围或外部组织隔离的运行证明，因为对应业务尚未迁入。
6. 当前以一个Enterprise、本地Foundation为范围；多企业SaaS、真实业务状态/唯一性/幂等、业务数据库Schema、前端及现场设备均未交付。

建议下一阶段先 **Routing**：定义最小工艺/版本语义，携带Factory身份，按厂对象范围和角色能力做独立Schema/Port及验收；再考虑 **WorkOrder** 的权威与路线引用；然后 **Reporting** 的生产事件/数量/幂等与外部结果边界。这只是顺序建议，本轮没有开始任何下一阶段文件、实体、API或数据迁移。

回滚是停用/移除这个独立新工程及其本地产物，不需要回退任何旧系统。本轮停止于Foundation，后续阶段等待用户决策。

## 9. 新建文档回执与自检

| 文件 | 新建内容 | 证据/代码影响 |
| --- | --- | --- |
| `README.md` | 构建、启动、demo、HTTP、能力与限制 | 新工程入口，区别默认空配置/Fixture/真实部署 |
| `AGENTS.md` | 新Base边界、clean-room、后续阶段门 | 规则仅约束新项目，旧工程未改 |
| `docs/architecture/README.md` | 架构与ADR导航 | 单module/package职责 |
| `ADR-001-factory-scope.md` | 纯Scope及线程/任务生命周期决策 | 对应Scope/Executor已测；无实际业务Job |
| `ADR-002-factory-runtime-binding.md` | 版本、排空、事务与Mapper归属决策 | 对应真实H2本地Fixture；无生产/集群验收 |
| `ADR-003-factory-permission-model.md` | 认证/选厂/权限/偏好/HTTP决策 | 对应真实HTTP；无完整IAM |
| `ADR-004-business-persistence-topology.md` | Dedicated实现、Shared设计与未执行计划 | 状态明确，未提前造共享业务表 |
| `ADR-005-erp-integration-boundary.md` | ERP Port/连接分离及缺配置失败决策 | 仅未配置测试，无实际ERP调用 |
| 本迁移报告及verification.json | 输入、执行、测试、状态、风险、后续建议 | 原始验证可定位；只实现新Foundation代码 |

自检：没有空Maven module、不必要业务分层、匿名业务Map、Entity直接返回、吞异常伪造成功、单多厂分叉、默认业务库/默认首厂、无Scope全局缓存或供应商空实现。Map只用于内部注册/授权/索引；HTTP用明确DTO；SQL只出现在新合成测试Mapper；Scope与连接/ERP/用户隔离。未merge、cherry-pick、commit、push、部署或改旧工程。
