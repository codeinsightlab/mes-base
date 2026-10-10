# MES Base 工程规则

状态 Active；2026-10-09。执行非简单工程任务先读 `/Users/user/Documents/agents/AGENTS.md`
并按实际栈路由，读取目标目录更具体规则。用户当前授权优先，保护已有工作。

## 1. 项目目标

以 ktg-mes / ktg-mes-ui / heri-uni-app 为 Source Product，抽取可复用产品能力，去公司化、去客户定制并修正必要架构问题。优先沿用原
RuoYi 模块与 Controller → Service → Mapper；现有 Base 分包可以调整或替换，保留已验证的
Factory/权限/资源/数据库隔离行为。先迁基础设施和系统设置，再迁业务；不为迁移重新创造 MES Domain。

## 2. 单代码 / 单架构

一套 Core；单厂等于 Factory count=1。不复制服务、不分 Single/Multi 两套主链路，不为简单 CRUD 增层。Core 不依赖
Spring/ThreadLocal/具体基础设施，也不依赖 SysUser/SysRole 持久化 Entity。

## 3. FactoryScope

Scope 只含 FactoryId；不等于 Permission、DataSource 或 ERP Connection。无 UserFactory 准入：有效空角色账号可登录、选择启用
Factory 和访问 AUTHENTICATED；AUTHORIZED 缺权限拒绝。偏好只是 UX，不授予权限。显式选厂优先，其次恢复有效偏好；无有效偏好时，仅从启用工厂且绑定启用业务库的候选中自动选择：一个选它，多个选工厂 ID 最小者。纯数字 ID 按数值比较，其他 ID 按字符串排序，数字 ID 优先；同数值以原 ID 排序作稳定裁决。零候选保持空 Scope，不使用魔法 0/1 或回退中心库。PLATFORM 角色显式 kind、无
factoryId；FACTORY 角色必须所属厂。平台角色可持有任意权限，其业务权限在选定的启用工厂上下文中生效；工厂角色仅可持有业务权限，且仅在所属工厂生效。工厂业务授权合并平台角色业务权限与当前工厂角色业务权限，不向工厂角色开放平台授权。权限按每次请求当前厂读取，不进长寿命 Token/跨厂缓存。

## 4. Controller / Service / Mapper

Controller 收参、基础校验、HTTP View 转换和协议返回；不做业务。Service 编排、规则、状态转换、事务和外部协调；Mapper 数据访问，不做授权决策。默认单
Service，只有长期真实复杂协调才拆分。外部主动调用经 Port/Adapter，Adapter 不编排业务、不访问平台/业务 Mapper。

## 5. Domain / DTO / VO / Persistence

响应契约按消费者与风险分级：普通内部后台 CRUD 可直接返回经审查安全的 Entity；敏感/复杂接口及外部消费者必须隔离响应模型。Service 返回 Entity/Domain/Business DTO/Query Model，业务语义与状态由 Service 定义；需要 Entity/Domain→HTTP Result/VO 时在 Controller 的 Web 边界转换，通用 Service 不依赖 Web Result。禁止业务匿名 Map /
条件散参，允许索引集合和框架元数据 Map。字段来源、赋值、使用清楚；不能用字段兜底掩盖契约。可选字段不因文档或假想业务变必填。

### 5.1 Java 导入与类型引用（2026-10-06，用户要求）

所有 Java 源码（含生产代码、测试及生成模板）中的外部类型必须通过文件顶部显式 `import`
导入，在类体、方法签名、泛型、注解、类字面量及静态调用中使用简单类名。禁止用 `xx.xx.xx.ClassName`
全限定类名代替正常导入，也不得以通配符导入规避显式导入。唯一例外是当前文件实际存在同名类型冲突：保留一个简单类名，仅对发生冲突的类型使用全限定名。包声明、import
声明以及配置/反射/AOP 所需的类名字符串不属于类引用。新增或修改 Java 文件时检查此规则；不因本规则覆盖无关的已有改动。

## 6. Base 边界（2026-10-09 用户要求）

当前 Base 只提供中心库 + 按 Factory 隔离的多业务库。不装配 ERP、HR、WMS 或其他外部数据库，不保留外部源枚举、选源注解、连接模板或第三个会话工厂。未来真实集成需求另立有边界任务，不能预建占位框架。OpenAPI 外部消费者身份与私有存储属于已有产品能力，不等于外部数据库路由。

## 7. 权限 / Resource Scope

HTTP 每个方法显式 PUBLIC/AUTHENTICATED/AUTHORIZED；缺注解启动失败。标准 CRUD 默认 AUTHORIZED，平台操作只认平台角色；业务操作在当前工厂上下文使用平台角色业务权限与所属工厂角色业务权限的并集。后端是最终边界，前端隐藏不等于授权。带
ID 操作必须校验资源所属 Scope/主体；不信任客户端 userId/userName/factoryId。文件必须 owner + scope + resource
证明，不能公共裸路径下载；新资源必须提供服务端 policy。

## 8. 事务 / 幂等 / UNKNOWN

本地事务不能回滚外部 HTTP；外部命令结果区分 SUCCEEDED/REJECTED/UNKNOWN。保留原异常链及 requestId，对外安全真实 HTTP
错误；不吞异常/伪造成功。跨平台/业务事务拒绝；线程上下文嵌套及异常恢复/清理，跨线程不得传持久化 Session。同步任务需要 DB
条件抢占、run token、跨入口复用及明确恢复，不能依赖 JVM 锁或 Quartz 单节点参数。

### 8.1 同步任务分布式互斥（强制）

来源：ktg-mes `feat/multi-factory-isolation` 的 AGENTS「定时同步任务并发控制」及 `mes_master_sync` 实现。Base
迁入通用同步协调基础；只保留通用协调机制，不装配外部连接或具体同步流程。

- 所有新增或改造的可重复执行同步任务，使用 `mes_master_sync`
  数据库状态表统一跨节点抢占。工厂同步流以 `factory_id + sync_key` 唯一标识，并由数据库唯一约束保证；不能退化为跨厂全局锁。
- 执行前以条件更新抢占，仅 `status != 'RUNNING'` 可进入运行；必须检查受影响行数为 1，失败明确返回“同步正在进行”，不得继续执行或等待同流任务。
- 每次抢占生成唯一 `run_token`。进度、完成和失败更新必须同时校验 `factory_id + sync_key + run_token` 及 `RUNNING`
  状态；检查更新结果，失去执行权后停止本轮处理，禁止旧任务覆盖新任务或继续推进同步水位。
- 手工同步、Quartz 定时任务和其他入口复用同一套抢占、执行权校验与恢复逻辑。`sys_job` 只负责调度，不替代同步状态和跨节点互斥；禁止仅以
  JVM 锁、`ReentrantLock`、`synchronized`、静态标志或 Quartz `concurrent` 控制同步并发。
- 记录开始时间、结束时间、上次成功时间、处理数量、错误摘要和必要同步水位；失败不得推进成功水位，错误保留异常链，对外不泄漏凭证或内部信息。
- 必须支持节点崩溃遗留 `RUNNING` 的恢复：以活动时间/租约判断超时，恢复更新同时校验旧 `run_token`、`RUNNING`
  和超时条件，并检查受影响行数；重新抢占生成新令牌。活动续期与恢复应避免竞争覆盖，禁止无条件按同步键解锁或永久锁死。
- 状态令牌只能保护对应数据库更新，不能自动隔离已发出的外部副作用；后续接入真实执行器时必须在处理边界校验执行权，并按实际写入契约设计幂等与恢复。通用协调机制不依赖
  ERP 厂代码、外部客户端或固定外部业务类型；未配置执行器明确失败，不返回假成功。
- 验收必须覆盖多节点同流竞争只有一个执行者、不同工厂互不阻塞、各入口共用抢占、旧令牌更新被拒绝、崩溃超时恢复及恢复后旧执行者停止。测试与真实数据库证据分别记录。

## 9. 数据库 / SQL / Ownership（2026-10-09 用户要求）

架构只采用中心库 + 多业务库的分库模式。生产只装配两个 DataSource 入口、两个 SqlSessionFactory 和两个 Spring 事务管理器；物理业务池按 Factory 动态租借，不按池新增会话工厂。缺 Scope、连接或版本明确失败，不回退中心库、首库或默认业务库。跨中心/业务事务及事务内切厂明确拒绝。

业务版本/租约、退休池排空、缓存前 statement guard 和异常清理继续保留。参数化 SQL、受控排序、分页边界及避免 N+1；unique/reference/聚合/cache/import/export 都需范围审查。业务 cache 缺 Scope 不能全局回退。复杂查询放 ExtraMapper，授权决策在 Service。新 CRUD 物理删除；Schema 来自真实 metadata，不带客户数据/seed。

### 9.1 固定包与 SessionFactory 归属

| SessionFactory | DataSource | TransactionManager | 固定扫描包 |
| --- | --- | --- | --- |
| `masterSqlSessionFactory` | `masterDataSource` | `masterTransactionManager` | `dev.mesbase.system`、`dev.mesbase.quartz`、`dev.mesbase.platform` |
| `businessSqlSessionFactory` | `businessDataSource` | `businessTransactionManager` | `dev.mesbase.mes`、`dev.mesbase.modules` |

Mapper 按 `*Mapper` 命名，固定包及其子包自动扫描。禁止逐 Mapper 配置、中央类清单、手工 Mapper Bean 或要求生产 Mapper 标注 PersistenceOwner。包不得重叠，重复名称及旧归属注解冲突明确失败。PersistenceOwner 只保留 PLATFORM/BUSINESS 的既有程序式 fixture 校验；PLATFORM 是中心库平台语义，不是第三个库。

生成器 GLOBAL 使用 dev.mesbase.platform.*，FACTORY_SCOPED 使用 dev.mesbase.modules.*；不生成 Mapper 归属注解或注册清单。不存在 external 扫描组或 ERP 连接选择入口。

### 9.2 Spring 事务与验收

Service 使用具名 Spring 事务；传播、提交、回滚和挂起恢复由 Spring 管理。业务事务固定 Factory 与连接身份，不能跨线程传 Session。不自动拆分跨库事务，不宣称中心库和业务库本地原子提交。中心 metadata 独立只读窗口保留既有受控入口和只读防护。

修改装配、扫描或路由时，验证两个工厂/入口的确切 Bean 名称及数量、中心/业务 Mapper 归属、不同 Factory 独立库、缺 Scope 拒绝、跨库事务双向拒绝、版本失效、回滚、挂起恢复、池关闭与异常清理。Fixture 通过不等于真实多厂数据库验收。

## 10. 测试 / 验收

先检查 pwd/git status/branch 与真实调用链，再改最小范围。测试源码存在不等于通过，build 不等于业务验收。按影响执行定向测试、范围/资源负例、真实生成
SQL/契约和 UI，明确静态/Mock/Fixture/本地/生产证据。Java21/标准 Maven 沿用用户环境，不创建项目 .m2/.mvn/private mirror；稳定工具
launcher 可调用标准 Maven。`mvn -B -ntp verify`；涉及打包启动再执行 `scripts/smoke-packaged.py`
（本机可用 `/usr/bin/python3`）。UI 使用 .nvmrc 已安装 Node，不改全局默认；执行 test/build。TEST ONLY 业务探针在 src/test 或自有
target，不包装为生产领域。

每次代码或配置改动都必须审查关联的测试、调用链、配置入口和生成模板。若本次改动使关联测试、旧入口、兼容分支、适配器或其他链路内容失效，必须在同一任务中删除或替换这些已废弃内容，不得为了维持表面通过而遗留无效代码或失真的测试。删除前保留仍验证业务行为、权限、隔离、事务和错误边界的测试，并将它们迁移到新的真实入口。

## 11. Git / 工作区

只改当前任务；不重置、不覆盖用户修改、不危险清理，不顺手重构/升级。确认目标路径，旧公司项目只读。提交/推送/部署需对应授权；本地构建不是部署。

## 12. 文档 / 证据

代码和文档一致；重要结论附方法/文件/测试证据，无法证明写明无法证明。报告简洁记录做了什么、文件、实际
command/passed/failed、风险和未验证边界；可复用判断依个人治理规范进入项目 docs。禁止输出凭证/令牌/密码；审计不采集请求体。

## 13. Migration / Development

迁移阶段忽略 Codegen Skill，不调用、不要求先生成 CRUD，不让工具定义产品字段或页面。已有功能以源项目代码、配置、Schema和页面流程为输入，优先
COPY / PORT，必要时局部重构；REDESIGN / DROP 必须有具体证据。迁移记录 SOURCE_PRODUCT_PORT 即可，不需申请 Codegen
例外。仅在后续新增功能开发阶段，适合表驱动的标准 CRUD 优先调用 mes-crud-codegen Skill，并完成业务、SQL、权限及 UI 审查。

### 13.1 CRUD Development

新增单表、常规主子表及列表/搜索/详情/增改删，以项目约定的 RuoYi Codegen 和 mes-crud-codegen Skill 为默认入口，不作为最后兜底。已有生成能力覆盖时，禁止从零重复手写整套前后端 CRUD。生成后允许移动文件、调整命名、补充业务/Factory/权限规则与前端视觉优化，仍须完成契约和安全验收；不让工具定义产品字段。

### 13.2 Domain Business

生产监控、排产、报工聚合、工单进度、复杂统计、流程、OpenAPI 及跨表规则等非标准 CRUD，建立明确业务 Service/Query Service/Application Service，不强塞进生成 CRUD。Service 负责规则、业务编排和事务，按需要返回 Entity/Business DTO/Query Model/Domain Model，不依赖 HTTP Result。仅有明确状态、规则和行为时引入 Domain Service/Aggregate，不机械创造 Domain 层。Controller 不承担跨 Service 业务编排。

### 13.3 Response Contract

- **内部后台标准 CRUD：** 经审查不含敏感字段、不暴露内部持久化状态、前端结构基本一致且无明显契约风险的 Entity，可以直接返回；不强制 Result/VO，不为形式分层复制模型。
- **敏感/复杂接口：** 包含 password/salt/secret 等敏感字段、字段裁剪、多表聚合、模型重组或明确 ID/状态/枚举转换时，必须使用 Result/VO 白名单，Controller 显式转换。认证及一次性凭证使用已授权专用协议，不混入普通响应。
- **小程序/ToC/Public/OpenAPI/合作方接口：** 必须独立 Request/Response DTO，禁止直接暴露 Persistence Entity；审查隐私、权限、数据范围、内部数据库结构、ID 精度、状态枚举、版本兼容及契约稳定性。外部契约不得随数据库结构任意变化。

只在需要转换时引入 Converter/Assembler：简单同语义复制允许 BeanUtils.copyProperties，目标不定义敏感字段，ID 等特殊字段显式赋值；重复/复杂映射按领域组织。转换器仅转换模型，不查询数据库、不调用 Service、不做业务判断、不管理事务。分页转换保留原 PageHelper 总数再转换 rows；集合工具不猜测业务类型。

### 13.4 Global Response Rule

禁止新增全局业务 ResponseAdvice/AOP 隐式替换响应模型，禁止 Object project(Object) 通过 instanceof 集中猜测业务响应，禁止全局递归 Long→String。ID 类型与语义转换由接口的显式契约决定，数量/总数/耗时保持数值。框架标准协议处理仅在有明确架构理由时允许，不承担业务 View 投影。敏感字段不得仅依靠 JsonIgnore 或运行时类型分支隔离。

### 13.5 Migration Compatibility

旧 MES 迁移与新增开发采用不同规则：迁移尽量原样 COPY/PORT，行为兼容优先，在权限、Factory 隔离、事务和安全不退化的前提下保持接口，不要求一次性理想化重写，不调用 Codegen 替代已有产品。历史结构记录为 migration debt，按有边界任务逐步治理；不新增隐式基础设施。新增开发遵守上述 Base 规则，不复制旧的不良模式。兼容目标不得用于豁免安全/隔离要求。

## 14. Skill / 工具边界

新增开发功能先检查适用 Skill；迁移工作不调用 Codegen Skill。Skill 调用底层工具而不重实现工具，完成后仍需项目测试/业务审查，不能跳过架构约束。开发期仓库
Skill 入口：`.agents/skills/mes-crud-codegen/SKILL.md`；配置/CLI 详见 docs/development/codegen.md。

### 14.1 基础生成范围与目标位置（2026-10-10 用户约定）

mes-codegen 只生成基础单表持久化与后台 CRUD。持久化复用 MyBatis Generator 的 MyBatis3 / XMLMAPPER：Entity、Example/Criteria、Mapper + XML；Service/Controller 必须同步调用该真实生成接口，分页复用 PageHelper，不新增注解 SQL、锁查询、业务状态规则或无必要的 ExtraMapper。

默认承载 Maven 模块为 `mes-business`。`--output` 指定后端仓库根，`--backend-module` 指定根下目标模块（默认 mes-business），`--package` 指定 Java 包；`--module` 是页面/API功能模块，不等于 Maven 模块；未指定时取表名首个 `_` 前的部分，没有 `_` 时使用 `business`，显式指定优先。默认 scope 为 FACTORY_SCOPED，默认包 `dev.mesbase.modules.<module>`；GLOBAL 显式选择并使用 `dev.mesbase.platform.<module>`。可生成到其他承载模块用于后续拆包，但必须保持第 9.1 节的固定包与 SessionFactory 归属；新 Maven 模块的创建、依赖接入另行显式处理，不随任意生成命令自动改变 reactor 或数据库拓扑。

业务隔离依赖按当前 Factory 路由的独立业务库。基础生成器不要求表含 factory_id，不注入 Factory 字段、Mapper/Service Factory 参数或 SQL Factory 谓词；若真实表本来有该字段，按普通 Schema 字段处理。缺 Scope 拒绝、连接/版本与事务内切厂保护继续由既有基础设施负责。范围选项只决定包归属、事务管理器及 HTTP 权限范围，不构造表内租户业务。Example 仅供服务端使用，不直接绑定 HTTP 请求中的 SQL 条件/排序字符串；业务开发负责批量写入条件和业务约束。

## 15. UI Base

复用 mes-base-ui 的统一 Shell/CrudPage/request/dict/permission，不为每页创造视觉体系。代码生成页只是 Functional
Scaffold；必须检查查询、Toolbar、表格/列顺序、枚举/日期/精度、表单分组、loading/empty/error、确认删除、分页、窄屏、中文/tooltip/按钮权限并优化。API
只读真实契约，不新增 AjaxResult/rows/data
多结构兜底。按用户2026-10-06要求保存刷新登录态，浏览器只保存Token/有效期/选厂偏好，不保存密码或权限；恢复须重新校验服务端身份与授权，过期/401/注销清除。选厂清除旧授权、数据和对话框，过期响应不回填。

## Java code style

Java formatting is repository-controlled by `.editorconfig` and `.idea/codeStyles/Project.xml`, using the IntelliJ IDEA native formatter. Generated and hand-written Java should follow IDEA's normal readable style as closely as possible; do not rely on a personal alternative formatter.

Avoid long lines and compressed statements. Keep one logical statement per line. Wrap long method declarations, parameter lists, annotations, calls, chained calls, conditions, generic types, and lambdas at readable boundaries. Do not join multiple fields, statements, or blocks onto one line merely to reduce file length. Prefer the existing IDEA-style line breaks and four-space indentation over dense code.

After modifying Java files, run from the repository root:

```sh
./scripts/format-java path/to/Changed.java
mvn -B -ntp test
git diff --check
```

The formatter script formats only the Java files passed to it; do not reformat unrelated Java files in a task. A task must not be reported complete if formatting, tests, or `git diff --check` fails.


## 16. clean-room / Source Product

clean-room 不等于禁止复用旧项目。通用源码、有效配置键、Schema和页面产品资产默认进入迁移候选；按所有权/授权/许可核对来源，保留许可及修改记录，无法确认的部分标
REVIEW。不得带入公司身份、客户/生产数据、Secret、内部账号、真实连接值、客户专用规则或不应复用的公司资产；混合实现先抽离专有部分，不整块舍弃功能。保留
Factory/权限/ownership 行为，允许更换实现和分包；拒绝默认库兜底、HTTP200错误、任意反射 Job。AGENTS 定规则、Skill
定开发工作流、工具执行，不能用架构或工具反过来定义迁移产品。

### 14.2 生成权限与菜单 SQL（2026-10-10 用户要求）

标准 CRUD 权限为 `<承载模块标识>:<完整表名>:read/create/update/delete`。`mes-business` 使用 `business`，`mes-erp` 使用 `erp`，只移除模块名开头的 `mes-`；完整表名不截取下划线前缀、不转换驼峰、不替换为物理数据库名；权限中的 `_` 统一替换为 `:`，例如 `business:pro:workorder:read`。`--module` 继续决定 API/页面目录，不再决定权限前缀。Java 常量、Controller、Vue 页面、菜单声明与菜单 SQL 使用同一生成值。

同时生成中心库 `sys_menu` 目录/页面/四操作的 INSERT SQL，置于承载模块 `docs/generated/<module>/*-menu.sql`，作为可审查产物，不自动迁移或执行，不写角色授权。使用当前 Source Product 菜单列及数据库生成 ID，不硬编码菜单 ID；运行前审查已有目录/页面是否重名，串行重跑不重复插入。
