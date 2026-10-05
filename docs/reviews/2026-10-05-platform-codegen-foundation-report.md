# MES Base Platform Foundation / Codegen / Skill 建设与迁移报告

2026-10-05；目标 `/Users/user/work/lixin/mes-base` 与 `/Users/user/work/lixin/mes-base-ui`。本轮是迁移执行及可复现验收，保持原 Factory Foundation，不进入正式 MES Domain。旧 ktg-mes / ktg-mes-ui / heri-uni-app 只读参考；未修改、cherry-pick 或复制公司源码、配置、数据、角色/菜单/字典/Job。

## 1. 交付结论

| 标记 | 状态 | 判定范围 |
| --- | --- | --- |
| PLATFORM_READY | YES | 本轮要求的 P0 用户/身份/角色/菜单/权限/字典/配置/审计/文件/Quartz 管理已在隔离平台库及真实 HTTP 验收 |
| CODEGEN_READY | YES | Service + CLI、默认/显式开发 profile、只读 metadata、自动放置、真实生成 Java/XML CRUD 执行与 Vue 编译成立 |
| SKILL_READY | YES | 支持的仓库 `.agents/skills` 目录、18 步流程、UI 后处理及实测 CLI，frontmatter 校验通过 |
| MES_DOMAIN_READY | NO | 尚无真实 Routing/WorkOrder/Reporting/Quality/WIP 领域，按要求停在平台与开发机制阶段 |

YES 表示本轮本地底座/脚手机制验收成立，不等于生产数据库、集群调度、外部集成或部署已通过。不存在“基本完成”替代状态。逐项限制见第 8 节；机器可读证据见 [verification JSON](2026-10-05-platform-codegen-verification.json)。

## 2. RuoYi 来源、许可与迁移矩阵

核实官方 [RuoYi-Vue 固定提交](https://github.com/yangzongzhuan/RuoYi-Vue/tree/13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3) `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3` 和 [MIT LICENSE](https://github.com/yangzongzhuan/RuoYi-Vue/blob/13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3/LICENSE)。先确认许可再导入：保留 LICENSE、官方 VelocityInitializer、service.java.vm 和 mapper.xml.vm 原件及哈希 [来源清单](../legal/ruoyi-source-manifest.json)。只改造能确认属于上游的部分；公司版本的文件不作为可复制来源。MIT 同时嵌入 JAR 的 META-INF/licenses，不只保留在源码文档。

实际复用为 Apache Velocity 引擎、官方初始化模式及 Service/Mapper XML 模板骨架；改造后的模板参与实际渲染和执行测试。不是仅保存上游文件而由另一套假生成器代替。其他 Base 专有 DTO/VO、Scope、Controller、ServiceImpl、Vue3/API 模板按新契约实现。Quartz、Spring Security/BCrypt、Flyway 复用成熟库。旧 Boot2/javax/Entity 对外契约、Redis LoginUser/权限快照与新 Boot4/Jakarta/分库/新授权结构不兼容，所以以通用能力模式重写适配，不宣称完整复制 RuoYi 平台实现。

| 能力 | 旧文件/模块参考 | 来源归属 | 方式 | Base 目标与改造 | 实测 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| Generator engine / 初始化 | ruoyi-generator VelocityInitializer | 固定官方 MIT | MIGRATE | platform/codegen/RuoyiVelocityInitializer；实例级 UTF8、classpath、严格模板 | CodegenAcceptanceTest + CLI | MIGRATED |
| Service / Mapper XML 模板 | vm/java/service.java.vm、vm/xml/mapper.xml.vm | 固定官方 MIT | MIGRATE/适配 | resources/codegen/base；DTO/VO、Factory 谓词、PLATFORM/BUSINESS | 编译+Scoped/Global CRUD | MIGRATED |
| Metadata / Generator 契约 | generator GenUtils/GenTable/VelocityUtils 通用模式 | 旧公司版仅只读；官方模式参考 | REWRITE | JDBC metadata；MesCodegenService、Profiles、CLI、Writer | profile/type/index/default/无行查询/冲突 | REWRITTEN |
| Authentication / Token | framework/security、LoginUser、TokenService | 旧文件不复制 | REWRITE | SessionService/PlatformTokenFilter；opaque DB hash/TTL/revoke，不缓存权限 | Service + 真实 HTTP | REWRITTEN |
| User / password / status | system user、SysUserController | 通用能力；公司修改不复制 | REWRITE | PlatformService/typed contracts；BCrypt、UUID、禁用/改密撤销 | PlatformServiceTest/HttpTest | REWRITTEN |
| Role / user-role / role-menu | system role 与关联 Mapper | 通用关系；不迁旧数据 | REWRITE | kind PLATFORM/FACTORY + factoryId CHECK、原子批量授权 | A/B/平台角色负例 | REWRITTEN |
| Menu / Permission | SysMenuController/Service、前端 permission | 通用模式；旧菜单不复制 | REWRITE | 显式 EndpointAccess；navigation 分平台/当前厂，孤立授权子菜单可见 | HTTP 403、菜单/按钮差异 | REWRITTEN |
| Dict | SysDictType/Data、DictData UI | 通用能力；旧字典值拒绝 | REWRITE | clean schema、typed CRUD、DB revision cache | CRUD/跨实例失效/浏览器 CRUD | REWRITTEN |
| Config | SysConfigController/Service | 通用能力；真实配置拒绝 | REWRITE | GLOBAL/FACTORY、scope_key 唯一、环境 Secret | 范围/缓存/更新/敏感 key 拒绝 | REWRITTEN |
| Quartz engine | ruoyi-quartz timing | 公共 Quartz 库 | MIGRATE | Quartz2.5.1 引擎，时间与 DB claim 分开 | PLATFORM/FACTORY 执行 | MIGRATED |
| Quartz management / logging | SysJobController、ScheduleUtils、JobLog | 任意反射/旧任务不复制 | REWRITE | 白名单 handler、CRUD/启停/手工、scope/token/UNKNOWN 日志 | 同 DB 并发抢占、token 校验、错误传播 | REWRITTEN |
| Oper / login log | LogAspect/SysOperLog/SysLogininfor | 通用模式；旧日志不迁 | REWRITE | requestId/actor UUID/factoryId/executionScope；只存安全元信息 | HTTP audit + login + job | REWRITTEN |
| File | CommonController/FileUploadUtils | 旧公开路径不迁 | REWRITE | FileStorage/LocalPrivateFileStorage；owner/scope/resource policy | 所有者/跨厂/大小/清理 | REWRITTEN |
| Platform clean schema | 上游 sys_* 表职责 | 官方 schema 只看职责，零 seed 复制 | REWRITE | db/platform/V1__platform.sql；Flyway显式开关/独立平台库 | 隔离 H2 Flyway | REWRITTEN |
| Web shell / store / request | layout/Sidebar/Navbar/TagsView/router/store | 公司前端仅模式参考 | REWRITE | Vue3/TS/Router4/Pinia/ElementPlus；动态路由仅编译 catalog | 18 tests/build + browser | REWRITTEN |
| Web Dict/Pagination/Upload/管理页 | components、system 通用页 | 未复制 Vue2/公司页面 | REWRITE | CrudPage/DictSelect/PrivateUpload/PlatformPage | UI+HTTP+组件测试 | REWRITTEN |
| Department / Post | SysDept/SysPost | P1通用能力 | DROP | 当前无必要领域关系，不建立无用表 | 范围决策 | DROPPED |
| Notice / Online / Monitor / Cache monitor / Server monitor | 旧系统/监控模块 | P1通用能力 | DROP | 当前无明确价值；不迁旧监控地址或任务 | 范围决策 | DROPPED |
| wheel/销售/财务/工资/查号/客户 MES / ERP 单据 | 公司模块 | 公司专有 | DROP | 不进入 Base | 工作区/文件来源检查 | DROPPED |
| 旧 FactoryContext / DynamicDataSource / 兜底库 / magic admin / AjaxResult failure | 历史基础机制 | 与新 Foundation 冲突 | DROP | 保留新 Scope/Binding/真实HTTP错误 | 原 Foundation 55 tests + smoke | DROPPED |
| 公司 profile/URL/Secret/账号/角色/菜单/字典/Job/数据/部署连接 | 公司配置与数据库 | 禁止迁移 | DROP | 环境输入/空 clean schema/显式 bootstrap | 产物无连接/凭证/公司包 | DROPPED |

P1 的 DROPPED 是本轮取舍，不表示永久不能按新需求实现。没有迁公司 P0 改造源码，也没有迁历史数据。

## 3. Platform 与 Factory 融合

**身份与角色。** 无角色账号可以登录、建立启用 FactoryScope、访问 AUTHENTICATED；AUTHORIZED 按当前厂 DB 权限拒绝。无 UserFactory、factoryId=0 或 userId=1 超管。PlatformBootstrap 仅在空用户表通过受控本地工具建立新 UUID 平台角色、当前显式平台权限和全新管理菜单，不赋厂权限/通配符；正常启动无默认账号。

**Token。** 默认/demo 保留原 Basic 验收。Platform 显式启用后，随机 Bearer 的 SHA256 留在平台 DB，TTL/登出/禁用/改密可撤销，Token 仅身份，Factory/Permission 不进 Session 快照。UI 凭证只在内存，不写浏览器持久存储。有效 Scope 与数据源/ERP/权限保持独立。

**Persistence。** PlatformMapper 显式 PLATFORM，独立 SqlSessionFactory/transaction；生成业务 Mapper 显式 BUSINESS，复用 DedicatedDatabasePersistence/RuntimeRegistry。GLOBAL 生成单独 PlatformMapperRegistration 配置。原跨库事务拒绝、线程/lease/版本/池排空不被取代。默认平台关闭、FactoryCatalog/Business runtime 仍空；不从平台 CRUD 自动拼生产绑定。Shared DB 仍仅契约。

**管理与审计。** Typed request/VO 与持久化记录分离，页量100、关联批量200。Dict/Config 事务 bump DB revision、跨实例缓存失效。普通 Config 拒绝 Secret 类 key，GLOBAL/FACTORY 是明确配置语义。日志保存 requestId/actor UUID/Factory/PLATFORM-FACTORY/结果，不采集密码、Token 或请求体。错误真实400/401/403/404/409/422/500及requestId；上传超传输限额为413。

**Quartz/文件。** 时间引擎复用，任务只调用 allowlist handler，显式 ExecutionScope，DB 条件 claim + run token，运行中不可改/删，失败 UNKNOWN/异常传播，长账号解析成 actor UUID 后写日志。默认两个 heartbeat 仅底座验证。私有文件通过 UUID storage/最大大小/owner+scope+resource 再校验；默认仅 personal，未来业务附件需自己的 FileResourcePolicy。

关键代码：`server/PlatformConfiguration.java`、`platform/management/PlatformController.java`、`PlatformService.java`、`SessionService.java`、`JobService.java`、`FileService.java`、`platform/security/PlatformTokenFilter.java`、`infrastructure/platform/PlatformMapper.java`、`resources/db/platform/V1__platform.sql`。操作入口见 [平台指南](../development/platform.md)。

## 4. Web Base 决策与界面后处理

创建前选择 Vue3/TypeScript/ElementPlus/Router4/Pinia/Vite，见 [ADR-006](../architecture/ADR-006-platform-and-web-base.md)。旧 Vue2 页面不整体移植。已安装 Node22 供本任务使用，不改用户 Node16 默认值。

实际包含 Layout/Sidebar/Navbar/TagsView、平台与厂菜单、白名单动态路由、permission directive、统一 request/error/401/timeout/上下文过期拒绝、DictSelect、pagination、私有 upload/download 和系统管理页。动态路由仅授权菜单匹配 `src/views/module/entity/index.vue` 的编译模块，不能服务端随意 import，也不自动给新产物授权。

共享 CrudPage 已实现中文查询/重置/Toolbar、列宽与 tooltip、枚举标签、日期/decimal 字符串格式、创建/详情编辑、分组表单、忙碌/错误/空状态、删除确认及取消、分页与窄屏。大整数/decimal 不转 JS number；nullable 参考值展示“—”。合成后处理明确 0停用/1启用与颜色、备注 textarea、编号中文标签，业务枚举不由工具猜。

真实浏览器完成：A厂菜单/生成列表、新建、编辑、筛选/重置、完整 decimal、大数精度、取消删除保留、确认删除刷新；切 B 厂清空菜单/旧数据，返回权限错误且无写按钮。平台 Admin 查看用户页，字典类型新建/编辑/删除。1440px桌面及390px窄屏检查，最终窄屏 bodyWidth=innerWidth=390；自建测试记录均清理。

![生成 CRUD 最终桌面（TEST ONLY）](assets/2026-10-05-platform-codegen/mes-codegen-desktop.png)

![生成 CRUD 窄屏（TEST ONLY）](assets/2026-10-05-platform-codegen/mes-codegen-mobile.png)

[大数/创建编辑证据](assets/2026-10-05-platform-codegen/mes-codegen-crud-created.png)、[B厂拒绝证据](assets/2026-10-05-platform-codegen/mes-codegen-factory-denied.png)、[平台用户页](assets/2026-10-05-platform-codegen/mes-platform-users.png)、[字典编辑证据](assets/2026-10-05-platform-codegen/mes-platform-dict.png)。这些均为隔离合成数据，不是生产现场。

## 5. Codegen 与合成 Demo

`MesCodegenService.generate(CodegenRequest)` + `scripts/mes-codegen` 已真实可执行；配置通过 Base ConfigData/Binder，默认开发 dev 或显式 profile。UNKNOWN/UNAVAILABLE/缺表/不支持/冲突分别返回，safe JSON 不打印密码。JDBC DatabaseMetaData 读取表/字段/类型/可空/默认/注释/PK/index/unique/FK；不读取业务行，测试代理禁止应用 Statement 调用。getIndexInfo 使用 approximate 模式。

产物 FACTORY_SCOPED 16个，GLOBAL17个：Entity、Mapper/XML、Service/Impl、Controller、Query/Create/Update、VO/PageVO、Permission、基础测试、前端 API/Vue、permission Markdown；GLOBAL 多注册配置。没有复制旧 menu SQL 或擅自写 grant。按两个根目录预检 → 临时 stage → 后端/前端发布 → manifest hash → finally清理；正常发布失败回滚，本地跨目录进程崩溃原子性未保证。

FACTORY_SCOPED SQL 的 list/count/detail/update/delete 有 factory_id 条件，create 由服务端 Scope 赋值，update 不能移厂。DTO 不收主体/厂身份；跨厂 999 ID 的 detail/update/delete 明确404。GLOBAL 使用平台 owner/permission/session；生成 GLOBAL Java/XML 已真实编译和 CRUD执行。标准单表复用生成 Mapper，当前未为了页面复制 ExtraMapper SQL；未来复杂查询才扩展 ExtraMapper。

合成表 `gen_demo_config` 仅 TEST ONLY：identity bigint id、factory_id、code/name、默认 status、decimal18,4、timestamp、nullable remark、unique(factory_id,code)。建表时无业务行；在 CLI 完成后启动合成服务才注入测试行。CLI 不指定 datasource 的默认 dev 验收、dry-run、真实生成、自动落点、状态后处理、javac/生成测试骨架、vue-tsc/Vite 构建全部执行。

实际落点：

- Backend `mes-base/target/codegen-acceptance/backend/src/main/java/dev/mesbase/modules/testonly/`：GenDemoConfigEntity、Mapper、IGenDemoConfigService、ServiceImpl、Controller、QueryDto、CreateCommand、UpdateCommand、Vo、PageVo、Permissions；XML 在相同 package 资源目录；测试和 permission 文档各一份。
- Frontend `mes-base-ui/target/codegen-acceptance/frontend/src/api/testonly/genDemoConfig.ts` 和 `src/views/testonly/genDemoConfig/index.vue`。
- Manifest `mes-base/target/validation/codegen-cli-manifest.json`；交付归档在 verification JSON。
- 后处理 GenDemoConfigStatus 是 TEST ONLY 额外枚举，不是正式业务领域。生成 fixture 路由、Server 在 target/src/test，生产 JAR 不含合成表/Server/H2。

完整用法与接入步骤见 [Codegen指南](../development/codegen.md)。`--serve` 可复现本地浏览器环境；`scripts/verify-codegen-http --test-only --port=<fixture-port>` 独立验证真实生成 Controller。

## 6. Skill 与 AGENTS 迁移

仓库 Skill 位于 `.agents/skills/mes-crud-codegen/SKILL.md`，属于 [Codex 支持的仓库 Skill 目录](https://learn.chatgpt.com/docs/build-skills)。新会话可自动发现；当前会话未刷新时根 AGENTS 提供精确可读路径，不能把目录存在冒充已刷新本次会话的 Skill catalog。工具先实现并通过测试后创建 Skill，无重复生成器脚本；Skill 调用稳定 CLI。

触发：新增表驱动业务/基础配置/标准CRUD/后台页/前后端生成。18步覆盖业务识别、适用判断、datasource/table/metadata、CLI、文件核对、Backend/Frontend自动落点、Factory/Permission/Resource/Domain/SQL、UI review/优化、两端测试与交付。20项 UI 检查强制；复杂领域 Domain First，绕过记录 CODEGEN_BYPASS_REASON。

| 分类 | 从旧 AGENTS 取舍 | Base 表达 |
| --- | --- | --- |
| A 迁移 | 第1/3/4/5/6/7/14节职责、最小实现、契约、异常、工作区/证据 | Controller不业务、Service规则/事务、Mapper仅数据、DTO/VO/Persistence隔离、不伪成功、最小改动、自检、真实证据 |
| A 迁移/补强 | 第8节优先通用Mapper、复杂扩展；第9节状态枚举；开发期授权/数量/并发底线 | 简单生成Mapper复用、复杂ExtraMapper、枚举稳定语义、SQL参数/分页/N+1、资源所属与事务 |
| B 重写 | RuoYi ktg-*依赖、Java8/Boot2、AjaxResult、生成模板Entity契约、旧目录与执行方案 | Core/Platform/Infrastructure/Integration、Java21/Boot4、真实HTTP、typed contracts、显式Mapper ownership/Scope/Binding |
| B 重写 | 原生成代码保护、普通系统权限/菜单模式 | Codegen First + Skill First，模板基础与Agent后处理分开；Platform role ≠ Factory role |
| C 拒绝 | 客户ERP单号/地址/Factory、微信检验订阅、热处理数量/设备、旧小程序分包、公司OpenAPI路径/具体表/profile | 本轮无对应领域需求；只保留授权/外部调用/状态/契约通用原则，具体业务不进Base |
| C 拒绝 | 旧动态库/FactoryContext/魔法管理者/失败HTTP200/历史补丁 | 使用新Foundation，禁止机械复制旧契约 |

根 `mes-base/AGENTS.md` 完成16类规则；UI `AGENTS.md` 只补 Vue3/目录/请求/精度/组件/测试，不大量复制公共规则。明确“所有适合表驱动的标准 CRUD 必须优先 Skill，Skill 可用不得从零手写完整 CRUD”。AGENTS 定规则、Skill定流程、Service/CLI执行，未把完整 Java 实现塞入规则。

## 7. 实际验证

使用用户 `zsh -ic` 加载的 Java21 Corretto / Maven3.9.10 和既有用户 Maven 配置/缓存；未恢复用户主动删除的项目 .m2/.mvn，未自建 mirror。前端切换已安装 Node22，不更改全局默认。

| 实际 command / 方式 | passed | failed | 证据范围 |
| --- | --- | --- | --- |
| `zsh -ic 'mvn -B -ntp verify'` | 80 | 0 | 9 JUnit suite，含原 Foundation55、Codegen6、Platform Service13、Platform HTTP6；最终JAR打包成功 |
| `zsh -ic 'nvm use 22 >/dev/null && npm run test && npm run build'`（UI根） | 18 tests + build | 0 | 5测试文件；request/CRUD/permission/组件/动态路由撤销；Vue TS/Vite |
| `zsh -ic 'nvm use 22 >/dev/null && scripts/verify-codegen-fixture'`（Base根） | 全流程、16产物 | 0 | 默认profile+dry-run+实际placement+javac+真实生成Vue TS/build |
| `MES_TEST_ONLY_PASSWORD=<合成临时值> scripts/verify-codegen-http --test-only --port=<loopback fixture>` | 21 | 0 | 真实Controller：401/400/403/404、分页、跨厂 ID、创建更新删除、Admin不带厂权、登出 |
| `/usr/bin/python3 scripts/smoke-packaged.py`（用户Java环境） | 11 | 0 | 默认/demo打包启动、基础认证/厂/ERP未配置；不冒充生产JDBC |
| `quick_validate.py .agents/skills/mes-crud-codegen`（临时PyYAML） | Skill is valid | 0 | 命名/frontmatter/placeholder；不单独冒充行为验收 |
| CUA真实浏览器人工流程 | 上述完整流程 | 0未解决 | Desktop/mobile截图、CRUD/取消/筛选/B拒绝、平台菜单/字典 |
| `git diff --check`、产物/包/路径与sources检查 | PASS | 0 | 未提交/推送/部署；旧公司工作区未改 |

认证测试涵盖正确/错误密码、disabled、无角色、logout、可控Clock expiry；Factory角色A/B授权和平台权限互不替代。Dict/ConfigCRUD与跨Service实例缓存失效，Quartz PLATFORM/FACTORY/无厂拒绝/上下文恢复/DB互斥/错误UNKNOWN，private File所有者/厂/资源/大小均有真实隔离 DB 测试。

早期执行中发现并修正了 dict保留字、Job历史日志FK阻止删除、构建符号/模板等问题；最终结果以上表为准。Skill校验的初次环境缺PyYAML，在临时目录安装后通过，未改系统Python。测试中的故意异常服务端日志不表示失败；按Surefire结果判定。临时fixture服务与Vite已停止，未停止用户服务。

## 8. 限制、风险与下一阶段

- 未联调真实 PostgreSQL/MySQL、生产catalog/账号/业务库、ERP或部署；Shared DB未实现。H2 fixture真实SQL/HTTP不是生产验收。
- Generator当前 SQL 是双引号标识符 + LIMIT/OFFSET；已验证H2，不自动宣称MySQL/其他方言兼容。只支持单PK/简单类型/无FK表；未知敏感或引用字段拒绝，不通过删除字段偷过。复杂领域、聚合/关联/状态机/cache/import/export/幂等/外部一致性需Domain设计和独立实现。
- 保留 metadata default，但CRUD参数是否采用默认需业务审查；nullable编辑使用显式null，不能把Schema默认等同每次修改默认。状态不猜数字语义。
- DB Job claim提供跨入口同时互斥，Quartz RAM时间触发不提供跨节点同一fire永久去重/集群misfire。重启不按超时释放可能仍活跃的任务；崩溃token需确认worker死亡并按原token条件恢复/记录UNKNOWN。正式长期同步必须补独立lease/heartbeat/恢复与幂等验收，当前仅heartbeat。
- UI相关授权选择器/厂选择当前获取有界100条，超大组织需要分页选择设计；尚无SSO、登录限流、生产审计/Session留存清理或对象存储适配。私有文件默认只允许personal，业务ResourcePolicy未由本轮虚构。
- 正常Codegen失败回滚/临时清理成立，但两个仓库跨目录进程崩溃不具原子性；显式overwrite会替换仍有生成标识的人工后处理，必须审查diff/manifest。
- UI build仍有>500kB vendor chunk提示（ElementPlus等），未掩盖阈值；构建通过，正式前端部署前可按实际性能需求拆包。
- 公共Bootstrap显式最初管理员工具是本机受控动作，不是匿名后台接口；平台管理员不自动拥有任意厂业务。默认平台不启用、生产RuntimeBinding不自动产生。

建议下一阶段先选择目标真实开发数据库及独立运行时装配，做平台Schema/Factory绑定的真实联调、部署配置与恢复策略验收，再由用户明确选择一个标准CRUD或Domain First领域；本轮不自动开始真实MES迁移。

## 9. 文件交付与自检

后端新增 `platform/codegen`、`platform/management`、`platform/security`、`infrastructure/platform`、PlatformConfiguration、显式Mapper注册、clean migration、模板和4个工具脚本；修改基础HTTP安全/错误以适配平台，Core只增加409错误类型，未重构Factory业务设计。新增3个JUnit类与TEST ONLY Server。前端新建Vue3工程、共享UI、系统管理、18测试及锁文件。文档新增ADR006、合法来源/许可、Codegen/Platform指南、本报告/verification/截图、仓库Skill、根及UI规则；更新README/架构索引。既有Factory报告/.gitignore用户未提交内容保留，不归为本轮新改造。

自检通过：未增加不必要业务分层；DTO/VO/Persistence分开；无业务Map替代契约；无吞异常/伪成功；Mapper不判断调用者权限；显式Factory与资源范围；未改公司/无关模块；没有为了架构设计假想MES；生成产物经过实际执行与UI后处理。没有git commit/push/deploy。

## 10. 交付完整性与收尾复核（2026-10-05）

结论：本轮 P0 Platform / Codegen / Skill 的本地功能交付和验收已完成，但不能称为“全部收尾干净”。运行现场已收尾，版本管理、临时辅助文件和一处说明仍有遗留。原 READY 标记仅表示第7节的本地验收，不表示已提交、已部署或生产就绪。

本次是交付复核：核对现有源码、构建产物、验收日志、文件哈希与进程，并重新执行 Skill 校验和 `git diff --check`；没有重新运行全套 Maven、前端、HTTP、浏览器验收，没有修改业务代码或重新生成 CRUD。

| 检查项 | 当前结果 | 证据及边界 |
| --- | --- | --- |
| 后端验收记录 | PASS | 当前 Surefire XML 合计80 tests，failure/error/skipped均为0，与第7节最终日志一致；`src/`与`pom.xml`无晚于最终JAR的输入修改 |
| 前端及HTTP验收记录 | PASS | 前端最终日志18 tests + build通过；源码/测试/构建配置无晚于`dist/index.html`的修改；HTTP结果21 passed/0 failed。属于此前隔离验收记录，本次未重跑 |
| 生成产物完整性 | PASS | 16个落点文件按manifest及2个已登记后处理hash复核，全部一致；后处理状态枚举存在 |
| 生产包边界 | PASS | JAR不含TEST ONLY Server、GenDemoConfig模块或H2依赖，内置MIT许可与源码许可文件逐字节一致 |
| 工具与规则 | PASS | 4个稳定CLI/验收脚本可执行；本次Skill校验输出`Skill is valid!`；现有文档本地链接复核未发现断链 |
| 临时运行现场 | PASS | 未发现本任务TEST ONLY Java/Vite服务运行；未发现Codegen staging或TEST ONLY数据库临时目录残留；未停止用户服务 |
| 工作区格式及Maven目录 | PASS | `git diff --check`通过；两端均没有恢复项目`.m2/.mvn` |
| 后端版本管理 | 未收尾 | `/Users/user/work/lixin/mes-base`处于`main`，13个已跟踪路径修改、66个未跟踪文件，未commit/push。其中`.gitignore`、`AGENTS.md`、`README.md`及既有Factory报告/verification在本轮建设开始前已有修改，不能把全部差异归为本轮或直接批量提交 |
| 前端版本管理 | 未收尾 | `/Users/user/work/lixin/mes-base-ui`不是Git仓库，`git rev-parse --show-toplevel`返回`fatal: not a git repository`；当前前端仅有本地文件，没有可审查的Git提交基线 |
| 临时辅助文件 | 有遗留 | `/private/tmp`仍有34个`mes-*.py`辅助脚本、`mes-codegen-upstream`下载缓存及`mes-skill-validator`临时依赖目录；脚本包含此前Factory阶段文件，未按前缀批量删除。构建`target/`、UI`dist/`与`node_modules/`是忽略的验收产物/依赖，保留以便复核 |
| 使用说明一致性 | 需修正 | UI `README.md:17`仍说需要手工在router注册生成组件；实际`src/router.ts:7`和`src/lib/dynamicRoutes.ts:4`已按授权菜单匹配编译组件并注册/撤销路由。管理员配置Menu/Role与不自动赋权仍正确，本次只记录差异 |

待收尾事项：确定前端版本管理归属；审查并区分后端既有差异与本轮交付后再提交；修正UI路由说明；确认临时文件归属后清理。当前“检查”未授权本次创建Git仓库、commit/push或删除既有临时资料，因此本次仅更新本报告与verification。

第8节真实数据库、部署、Quartz持久调度/崩溃恢复、Shared DB及正式MES领域等边界继续保留；它们不是本轮已验证能力。没有发现构建后业务源码继续变化、生成产物缺失或本任务服务仍在运行的证据。

## 11. 下一阶段交接与Git状态更新（2026-10-05）

用户要求将下一阶段规划写入文件，由新会话依据文档继续工作。执行入口见[下一阶段规划与新会话交接](../development/next-stage-plan.md)，包含环境约定、最少阅读顺序、P0-A收尾、P0-B真实开发数据库与可信运行时装配、P0-C生成闭环、后续领域路线、验收门槛和可直接粘贴的新会话指令。

本次写文档前再次核对：后端`main`/`975a9df4687f882bf251ca09bfb242015d33beff`、前端`main`/`18e372c2e6da0c6ab9738d562f99bf0d5bf3c6ca`，两端`git status --short`均为空，前端已成为Git仓库。因此第10节“后端未提交、前端无Git”是已被后续本地提交取代的历史状态，不再作为当前阻碍；本次未创建仓库、提交或推送，远端和部署状态未核对。

临时资料/路由说明与生产能力限制仍需按新会话实时状态核对。本次仅新增规划、更新README入口及报告/verification交接记录，未修改业务代码，未重跑功能验收，未启动服务或执行下一阶段实施。


## 12. P0-A 收尾与 P0-B 独立差距分析（2026-10-05）

### 实时基线及 P0-A 结果

后端 `main` / `975a9df4687f882bf251ca09bfb242015d33beff`，前端 `main` / `18e372c2e6da0c6ab9738d562f99bf0d5bf3c6ca`。执行 `pwd`、`git status --short`、`git branch --show-current`、`git rev-parse HEAD`：前端干净；后端既有 README、报告、verification 修改及未跟踪 next-stage-plan，全部保留，仅追加本轮证据。第10节旧 Git 状态继续作为历史快照。

已修正 UI README 的手工路由说明，并澄清 ADR-006：工具不创建菜单/授权、不修改 router；运行期 `src/router.ts:7` 的 `import.meta.glob` 产生 catalog，`src/lib/dynamicRoutes.ts:4` 根据授权菜单匹配两级已编译组件，先撤销旧路由再安装新路由。平台页面由已有固定路由处理。生成工具成功不等于可访问或有权限。

进程检查使用 `ps -axo pid=,comm=` 与候选 Java/Node 的 `lsof -a -p <pid> -d cwd -Fn`，避免输出命令参数或凭证。没有发现以两端仓库为 cwd 的 Java/Vite 应用；backend cwd 的 Node 为 ChatGPT CUA 工具。该检查不能证明其他 cwd 启动的服务不存在，因此没有按端口/PID管理任何既有服务。

临时清单逐项保存在 verification.nextStageExecution.p0a.temporaryHelpers（路径、UID、大小、SHA256、路径引用）。34 个脚本属于当前用户 UID501，但 UID/前缀不能证明创建任务：其中 mes-migration-schema-preflight.py 引用旧 ktg-mes，mes-migration-read-aggregates.py 没有 Base 路径引用，mes-final-report-write.py 同时引用 Base/旧 MES。其余主要引用 Base。这只是静态归属线索，不执行辅助脚本、不打开旧仓库。两个缓存目录 mes-codegen-upstream / mes-skill-validator 均存在。全部保留；用户未授权删除。target/dist/node_modules 继续保留作为忽略产物，不能充当已提交源码证据。

P0-A 通过，临时资料处置为保留。业务代码未修改。必要检查：

| 实际命令 | 结果与边界 |
| --- | --- |
| 两端 pwd / git status --short / git branch --show-current / git rev-parse HEAD | PASS，以上实时基线 |
| zsh -ic 'java -version && mvn -version' | PASS，Corretto21.0.4 / Maven3.9.10；安装目录名称为3.6.1不代表版本 |
| zsh -ic 'nvm use 22 >/dev/null && node -v && npm run test -- src/lib/dynamicRoutes.test.ts'（UI） | PASS，Node22.23.1，1 test / 0 failed；验证匹配编译组件及撤销，不是浏览器验收 |

没有业务代码变化，未重跑完整后端 verify、UI test/build 或 packaged smoke。原80/18/21/11项为此前 H2 合成验收，不计成本轮真实数据库证据。

### P0-B 源码差距及可审查装配方案

状态：差距分析完成，真实开发环境待确认，P0-B 未通过；P0-C 尚未执行。没有连接数据库、启动/停止服务或安装数据库。

| 调用链/能力 | 当前源码事实 | 下一步最小实施边界 |
| --- | --- | --- |
| 登录→选厂→当前厂权限 | Platform 开启后身份/授权由 PlatformService/SessionService 持久化；Foundation FactoryCatalog 默认空，偏好内存 | 受控配置装配目录，保留无 UserFactory 准入；无需新增厂管理 CRUD/偏好表 |
| FactoryScope→resolver→Binding→Registry→Dedicated transaction→Owned Mapper | resolver 映射、Registry 默认空；已具备 owner/version/事务固定/无兜底能力 | 显式开发配置适配器只负责可信目录、Binding、独立池装配，复用原持久化实现 |
| 物理隔离 | Registry 校验 identity owner、revision、池对象，无法识别不同 URL/identity 实际指向同一物理库（ADR-002已记录） | Platform/A/B 三个独立数据库；实际 catalog/schema 与权限负例验证，不能用 identity 名称当物理隔离证明 |
| Mapper ownership | 注册时要求 BUSINESS，未知 Mapper 不可取 | 从代码中固定可信名单或类型化注册 Port 获取 Mapper，禁止配置 Class.forName/包扫描；TEST ONLY Mapper 留 src/test/target |
| 数据源生命周期 | Registry 已支持租约排空与 close，Platform 池由 Spring close | 构造失败关闭已建池；全部装配验证完成后发布，不提供热加载/管理 API；关闭只作用本轮自有资源 |
| 迁移与驱动 | JAR 有 PostgreSQL/Flyway PostgreSQL 模块；V1 clean DDL 在 H2验收；只含内部 catalog revision 初值，无客户seed | 真实确认目标方言后执行首次/重复迁移、重启、约束及管理员重复初始化拒绝；不重写已应用 V1 |
| Codegen profile | dev profile 独立于 Factory Runtime；双引号与 LIMIT/OFFSET | P0-B通过后按 Skill、真实 metadata 和16文件 manifest验收，不能由 JDBC 连接成功推导SQL兼容 |

建议方案（未实施）：若用户确认 PostgreSQL，使用专用本地实例中的 Platform、Business A、Business B 独立数据库及最小权限账号；loopback 新端口，连接值只放受控环境/忽略配置，不写日志、报告、CLI参数。启动时校验显式厂ID、状态、datasource identity/revision、重复厂/绑定、物理库标识以及可信Mapper；先准备并验证所有独立池，再生成 resolver/registry；任何装配失败整体关闭新资源并失败启动，不回退默认或 Platform。目录按配置重建，偏好重启丢失应显式说明；UI token刷新重新登录仍是原契约。默认环境保持空目录/无业务连接，demo不被开发装配隐式覆盖。此方案不增加 Factory CRUD、连接管理平台、Shared、ERP、正式MES模块。

真实验收待执行：平台迁移/约束/首次管理员/重启数据；角色菜单字典配置、私有文件、注册heartbeat；A/B同ID与业务键、未知/禁用厂、缺Scope/绑定/Mapper、版本冲突、401/403、平台管理员无厂权及配置/日志凭证审查。持久化和UI证据须在真实引擎上的合成开发数据取得，禁止回退H2后标为通过。

集中缺失信息已询问：引擎及版本；专用实例来源/安全配置位置；是否授权创建数据库及启动停止本轮自有DB/后端/UI；独立库/端口/持久卷生命周期。未回答前不操作数据库或用户服务。P0-C依赖P0-B验收，不提前生成正式模块。


## 13. 用户纠正迁移目标：RuoYi 三个模块前后端（2026-10-05）

本节依据用户当前明确要求，覆盖第2节与ADR-006对通用 Department/Post/Notice/Online/Monitor 的阶段性 DROP。目标调整为截图中的系统管理、系统监控、系统工具三个完整功能分组，前后端均需交付，再根据实际持久化模型形成DDL并本地启动。公司专有业务、Shared DB、ERP、集群调度仍不在范围；既有 Factory Foundation 和安全模型保留。能力迁移允许按 Vue3/当前平台契约适配，不以恢复旧框架/Redis权限快照/Druid/反射Job为验收要求。

### 官方清单及当前缺口

官方基线为原来源清单中的固定提交 `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3`。本轮通过 GitHub API 读取完整 tree（truncated=false）与 `sql/ry_20260417.sql` 菜单定义；仅用作功能清单证据，未执行或复制其建表/INSERT/账号/角色数据。该固定提交目录不含 ruoyi-ui，前端实现基线需按其官方独立前端来源进一步核实，不能声称已经获取或迁移上游UI。

| 分组 | 功能 | 后端现状 | 前端现状 | 待完成 |
| --- | --- | --- | --- | --- |
| 系统管理 | 用户/角色/菜单 | CRUD、密码重置、用户角色/角色菜单授权、PLATFORM/FACTORY独立权限 | 通用管理页与授权/重置操作 | 原版操作级差异待逐项审核；当前不具备完整组织关系、查询/导入导出等功能，不能写成完整迁移 |
| 系统管理 | 部门/岗位 | 未实现 | 未实现 | 部门树、岗位、受控用户关联及对应页面；组织不授予Factory准入 |
| 系统管理 | 字典/参数 | CRUD、范围配置、缓存版本 | 管理页 | 完整查询/操作级对照与真实数据库验收 |
| 系统管理 | 通知公告 | 未实现 | 未实现 | 发布状态/内容展示/管理与资源权限 |
| 系统管理 | 操作日志/登录日志 | 安全审计与分页读取 | 只读管理页 | 查询、详情、允许的清理/导出能力及权限逐项对照 |
| 系统监控 | 在线用户 | DB会话存在，但无在线会话管理API | 未实现 | 分页展示安全会话信息、强制注销；严禁暴露token/hash，在线口径必须说明 |
| 系统监控 | 定时任务 | 注册handler、CRUD/启停/手工执行及执行日志 | 已有任务页/执行按钮/日志页 | 查询及完整任务交互对照；不开放任意反射或提前做集群调度 |
| 系统监控 | 数据监控/服务监控 | 未实现监控接口 | 未实现 | 基于现有Hikari/真实JVM与OS指标实现等价能力，不把静态样例称实测；敏感URL/SQL/凭证不输出 |
| 系统监控 | 缓存监控/缓存列表 | 有字典/配置缓存，无监控管理API | 未实现 | 查看实际应用缓存与受控失效；当前没有Redis，不能伪造Redis监控数据，也不为复刻页面自动引入Redis |
| 系统工具 | 表单构建 | 未实现 | 未实现 | Vue3表单设计、预览和导出，明确是否保存设计及其Schema |
| 系统工具 | 代码生成 | 已有真实metadata/Service/CLI/16文件产物 | 缺代码生成管理页 | 受控开发profile、表/列配置、预览/生成UI；不接受任意连接或生产路径，由同一Codegen Service执行 |
| 系统工具 | 系统接口 | 无接口文档页面/契约端点 | 未实现 | 受授权的当前真实API文档与交互，不展示测试或不存在的接口 |
| 分组导航 | 三个顶层入口 | bootstrap为平铺管理菜单 | shell已支持菜单树，尚未配置三个功能分组 | 随真实已实现功能登记三个目录，不用空页面充当完整迁移 |

源码检查入口：PlatformController / PlatformService / JobService / SessionService / PlatformBootstrap，UI src/lib/platform.ts / views/PlatformPage.vue，CodegenCli / MesCodegenService。以上为源码事实；原80项后端/18项前端/H2/浏览器证据仍只证明此前局部底座。

### DDL与运行边界

现有唯一已实现平台DDL为 `src/main/resources/db/platform/V1__platform.sql`，共15张平台表：sys_user、sys_role、sys_menu、sys_dict_type、sys_dict_data、sys_config、sys_job、sys_oper_log、sys_logininfor、sys_job_log、sys_file、sys_session、sys_user_role、sys_role_menu、platform_catalog_revision。它不包含部门、岗位、公告、生成配置等待实现表，不称为三模块完整DDL。监控快照不必建持久表，表单构建若只预览/导出同样不必造表。后续表由实际模型与选定方言形成增量迁移；不导入官方seed，不改写可能已应用的V1。

先确认隔离数据库环境后，对适合标准CRUD的新增表执行 mes-crud-codegen Skill；部门树/主体关系/敏感生成执行需要受控设计，不能把模型削成普通CRUD以通过工具。完成三模块代码、增量DDL、平台迁移/首次管理员/权限菜单，再用用户Java21/Maven和已安装Node22跑必要门禁与真实浏览器，使用独立端口启动自有服务，不操作未知既有服务。

环境观察：PATH有MySQL客户端；command -v docker / psql 未发现对应命令（不代表磁盘不存在或服务未安装）。尚未确认引擎/实例/凭证，未连接数据库、未启动应用。本轮已集中询问目标隔离DB；数据库确定前可以继续源码差距与契约设计，不能完成真实metadata生成或声称run成功。

命令证据：GitHub API tree与contents读取成功；raw.githubusercontent.com读取SQL出现curl(35) Connection reset，按环境权限重试仍失败，改用官方GitHub contents API成功。未执行下载SQL。两端基线与此前一致，保留已有文档差异。当前仅新增范围纠正与事实报告，未完成新功能迁移。


## 14. 指定MySQL测试库与YAML dev/prod装配（2026-10-05）

### 授权及当前运行事实

用户明确提供其MySQL测试实例连接并授权直接创建SQL；后续要求环境参数尽量使用YAML、dev/prod profiles，拒绝把环境清单写在Java配置类。连接验证得到MySQL Community Server 8.0.40，连接使用TLS。没有查询或更改实例其他业务库、用户数据、客户Schema；仅创建本轮独立开发库与专用数据库账号，应用不使用root。

本轮独立库（CHARACTER SET utf8mb4 / COLLATE utf8mb4_bin）：

- `mes_base_dev_20261005_572b2e_platform`（platform）
- `mes_base_dev_20261005_572b2e_a`（a）
- `mes_base_dev_20261005_572b2e_b`（b）

建库DDL在自有忽略目录 `target/local-dev/create-databases.sql`；账号只被授予以上三个库的权限。既有 `db/platform/V1__platform.sql` 在独立Platform库通过Flyway首次和重复迁移，没有重写V1；15张平台表与Flyway历史表已建立。还没有部门/岗位/公告等新增模块表，因此不能称三个RuoYi模块完整DDL。

当前服务：本轮后端pid55129，loopback18080，前端Vite loopback15173（自有exec session62279）。UI `http://127.0.0.1:15173` / backend `/health`返回200；开发UI代理显式指向18080。浏览器实际显示登录页，未在本轮重新执行登录后的完整页面CRUD矩阵。服务保留供用户本地查看，停止仅限这两个本轮服务；root不是应用账号。首次平台管理员`dev-admin`已建立，随机合成登录资料在 `target/local-dev/login.json`（0600），不输出密码。

### 配置与代码变更

- `application.properties`迁为`application.yml`；新增`application-dev.yml`/`application-prod.yml`，默认dev。prod要求外部平台URL/账号/密码/文件根，默认不迁移、不带测试连接、无默认账号/业务库。
- 连接、Factory清单/启用状态、identity/revision、端口、Pool参数、文件根、会话TTL、上传上限、Codegen profiles都在YAML。受控本地连接在忽略的`target/local-dev/application-local.yml`（0600），旧的本轮properties配置已移除。示例 `config/application-runtime.example.yml`仅环境占位符。
- 删除本轮临时的LocalDevelopmentConfiguration设计，改为通用FactoryRuntimeConfiguration：只根据`mes.runtime.enabled`绑定YAML/构造对象/校验所有者与物理库/关闭池，不用Java profile类定义环境。现阶段物理库探测为MySQL（server_uuid + database）；并未声称PostgreSQL Runtime同等验收。
- 默认空目录/空resolver/空registry仍保持；Runtime显式开启才装配，必须同时启用Platform，拒绝与demo混用。显式可信BusinessMapperRegistration由代码提供，YAML不能提供class名或包扫描。保留无UserFactory准入、无Scope/绑定/版本/Mapper回退与既有Foundation事务规则。偏好仍内存，重启不承诺持久化。
- pom加入MySQL JDBC/Flyway MySQL模块；TableMetadata增加受控identifierQuote，JdbcMetadataReader从真实driver取值，Renderer使用MySQL反引号或H2/PostgreSQL双引号，保留参数化SQL。实际16文件MySQL manifest/render通过；真实生成CRUD SQL/UI完整执行仍属P0-C未验收。
- 启动脚本 `scripts/run-local-backend` 使用用户Java环境与显式外部YAML；fixture/smoke入口同步改为application.yml。前端Vite支持受控loopback代理目标；未改变全局Node。

Factory/Codegen/persistence改动可用Git逐文件回滚；独立开发库/账号/私有文件没有删除，数据库回滚须明确只针对本轮资源，不能把源码回滚当数据恢复。仓库已有README/报告/verification/计划修改保留；过程中发现DefaultMesCodegenService出现本Agent未执行的格式变更，按并行用户编辑保留，未覆盖或声称本轮实现。

### 实际验证与失败修正

| 实际命令/动作 | 最终结果 | 证据边界 |
| --- | --- | --- |
| nc -vz -w 5 <用户测试host> 3306；mysql受控defaults-extra-file读取VERSION/TLS并创建独立库 | PASS，MySQL8.0.40 | 真实指定测试实例，仅自有开发库；不是生产验收 |
| zsh -ic 'mvn -B -ntp verify -Dmes.test.mysql.config=target/local-dev/application-local.yml' | PASS，83 tests，0 failures/errors/skipped | 80既有测试 + 3 opt-in真实MySQL测试；真实迁移/重复迁移/重连约束、A/B同ID/回滚/错误物理绑定/缺Scope/绑定/Mapper、真实metadata/16产物 |
| UI zsh -ic 'nvm use 22 >/dev/null && npm run test && npm run build' | PASS，18 tests/build | 配置迁移相关回归；保留>500KB chunk提示，不等于三模块页面验收 |
| zsh -ic '/usr/bin/python3 scripts/smoke-packaged.py' | PASS，11 checks | 最终JAR、loopback default/demo合成HTTP；无生产/真实业务SQL |
| /usr/bin/python3 target/local-dev/verify-live-http.py | PASS，47 checks | 本地实际JAR→真实MySQL合成开发数据；身份/空角色/选厂/403/撤权/A-B/平台无厂权、角色菜单/字典配置/日志/两种heartbeat/私有文件owner与scope |
| 后端停止本轮pid并重新启动 + restart script | PASS，3 checks | 重登后配置marker/私有文件持久化，YAML重建A/B目录；偏好不宣称持久化 |
| zsh -ic '/usr/bin/python3 target/local-dev/verify-config-profile.py' | PASS，3 checks | prod缺外部连接失败；JAR只含三份YAML入口；无废弃local-dev类；没有连接生产 |
| 两端 git diff --check；受控凭证与docs/日志扫描 | PASS | 密码未写源码/文档/日志，连接/login文件0600；不提供全集环境输出 |

失败与修正：首次Maven被沙箱拒绝写用户现有缓存，审批后使用同一用户缓存成功，没有项目.m2/.mvn/mirror。首轮新增生成测试因macOS临时路径符号链接触发INVALID_REQUEST，改toRealPath。第二轮生成测试因最大1连接被建表连接占用而Hikari超时，先释放建表连接再生成，最终通过。启动现场曾误用尚未包含Runtime的新旧交界JAR（pid49438），该进程已明确停止；随后新Runtime JAR启动/重启通过。YAML迁移后发现target/classes残留旧application.properties进入JAR，精确移除废弃产物后重建，最终JAR检查确认无properties。交互zsh的oh-my-zsh缓存/zcompdump/nice出现沙箱权限提示，Java/Maven/Node命令仍按用户配置正常执行；未修改用户shell配置。

本轮保留合成重启marker配置、admin私有TEST_ONLY文件及安全审计/Job日志作为持久化证据；临时角色/菜单/reader/字典已由验收脚本删除，MySQL测试probe表已由测试清理。没有删除原34个辅助脚本或缓存目录。临时受控验收脚本在自有target中，非正式MES模块，不在生产JAR。

### 工作包状态与未完成边界

P0-B核心真实引擎装配与平台闭环已通过以上验收；禁用厂/运行版本替换/并发完整矩阵仍主要沿用既有Dedicated/Foundation测试，没有全量重跑真实MySQL所有变体。P0-C仅完成MySQLmetadata/引号/16文件基础验证，未运行稳定CLI到真实生成Controller/CRUD SQL/完整浏览器闭环，保持未通过。三个RuoYi通用模块缺项仍按第13节推进，未标记完整迁移；没有新增正式MES领域、Shared、ERP或集群调度，没有提交/推送/部署。


## 15. 用户指出字段迁移偏差：官方Schema与Base重建Schema（2026-10-05）

用户询问为何字段没有按RuoYi迁移。此次只读对照官方固定提交的sql/ry_20260417.sql（此前通过官方GitHub API读取）与当前V1__platform.sql；没有修改代码或数据库。

核对结论：现有平台并非保留原字段/语义的RuoYi迁移，而是重建通用职责的简化Schema。官方sys_user有user_id/dept_id/user_name/nick_name/email/phonenumber/sex/avatar/password/status/del_flag/login_ip/login_date/pwd_update_date/create_by/create_time/update_by/update_time/remark等；Base仅id/username/display_name/password_hash/status/created_at。sys_role由role_id/role_name/role_key/role_sort/data_scope等变为id/name/kind/factory_id/status。sys_menu由menu_id/menu_name/component/menu_type/visible/perms/icon等变为id/parent_id/name/path/permission/sort_order/kind。sys_config、sys_job同样改名并删减多项官方字段。不是只替换技术栈或增加Factory范围。

此前ADR与报告记录了REWRITE，但对用户的“迁移RuoYi”目标没有充分解释“重写并简化表结构”的具体后果；不能因底座已运行就认定符合完整迁移期望。Spring/Jakarta/Vue3升级和显式Factory安全适配不能自动证明原用户/菜单/组织/审计字段可删除；禁止复制公司数据/配置也不等于禁止依法复用官方Schema。

纠偏方向：以官方三模块字段与功能语义为对照基线，逐字段标注保留/必要适配/新增及其理由，恢复此前未有充分依据的删减；FactoryScope、平台/厂权限、显式Mapper ownership继续保留，不恢复魔法管理员/任意反射Job/生产seed。此方向尚未实施；已应用V1不重写，数据库重建或破坏性变更需要明确处置范围，优先后续增量迁移。当前测试证明运行和安全边界，不证明官方字段/功能完整迁移。此前P0-B通过仅指简化Base底座的真实引擎装配。


## 16. 原项目确认、结构核对与用户暂停（2026-10-05）

用户提供原项目 `/Users/user/work/heri/ktg-mes`，只读核对时 Git master/HEAD `310c6ef1bb37d2533fe893579ba837c353b76926`、工作区干净。其 `sql/ry_20210908.sql` 含17张sys表。已核实官方固定版本 `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3` 的18张sys表包括通知已读表；差异列见 `docs/legal/ruoyi-schema-manifest.json`。原项目Java8/Druid1.2.8；该官方固定版本实际上Boot4.1.0/Druid1.2.28，不能以“官方仍为Boot2/javax”作为简化重写的理由。前述Boot2判断只适用于旧ktg项目。

实际新增：`sql/ruoyi/sys-schema.sql`逐条保留官方CREATE TABLE结构，18表182字段，无DROP/INSERT；来源、哈希及原项目字段比较记录在schema manifest。已在指定远程测试MySQL的独立 `mes_base_dev_20261005_572b2e_ruoyi` 库执行，metadata字段名称/顺序完全一致、各表零行。未覆盖旧V1，未切换应用；这只是结构核对，不是完整迁移或前后端闭环。实际命令 `python3 target/local-dev/verify-ruoyi-schema.py`，结果在同目录 `ruoyi-schema-verification.json`。

Druid改动：Platform/Factory Runtime/管理员CLI接入Druid1.2.28；连接池参数读取 `spring.datasource.druid` YAML，Hikari只保留test scope。新文件 `server/DruidPools.java` 负责属性校验/对象装配/关闭，环境值仍为YAML。原项目Druid master/slave/erp/hr/wms及默认MASTER回退未复制；stat/wall过滤器与监控页面尚未实现，其他有效配置尚未完整迁移。旧环境连接、账号和客户业务值未复制到Base。

本包实际测试 `zsh -ic 'mvn -B -ntp verify -Dmes.test.mysql.config=target/local-dev/application-local.yml'`：83项，失败0/错误0/跳过0，包含真实MySQL Factory Druid连接测试。首次编译失败：管理员CLI仍引用Hikari；将CLI一起接入Druid后通过。首次字段核对断言失败：manifest解析漏识别longblob类型notice_content；DDL原字段未丢失，修正manifest为182列后核对通过。已启动本包packaged smoke，最终结果按verification记录。未重复UI测试（本包无UI代码修改），未完成Druid前后端监控、正式模型/Mapper/API改造、原配置全量有效性核对或P0-C闭环。

用户明确要求暂停并在新任务继续：停止新增实现、数据库写入和服务切换；现有18080/15173查看服务未重启，因此仍为前包版本。所有未提交改动、隔离库及旧临时资料保留；没有提交/推送/部署。用户未接受本轮迁移结果，不将当前产物标记为“完整RuoYi迁移”或验收通过。新任务应从用户完整迁移目标重新确认实施路线，而非沿用最小能力重写作为终态。
