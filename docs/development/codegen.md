# MES Codegen 使用说明

2026-10-10 更新。稳定 API 为 `MesCodegenService.generate(CodegenRequest)`；开发入口为可执行 `scripts/mes-codegen`。使用用户已配置的 Java21/Maven；脚本仅在 JAR 缺失或主源码更新时调用标准 Maven package，不创建 .m2/.mvn。

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
