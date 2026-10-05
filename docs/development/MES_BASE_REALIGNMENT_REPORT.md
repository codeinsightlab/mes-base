# MES Base Realignment Report

> 当前执行：2026-10-06 Phase 1 持续迁移，见文末续接快照。第1–10节属于2026-10-05纠偏时的历史事实，不能作为当前源码和运行状态。

2026-10-05；Phase 0 纠偏审查。用户已将优先级调整为“先基础设施和系统设置，再业务”，并授权本轮完成文档后迁移基础设施；已有功能直接COPY/PORT旧代码，不先新增或生成CRUD。业务只作模块级清单，不继续设计新Domain。

## 1. Current State

客观结论：当前Base主要是 **B：MES开发框架**，兼有 **C：部分RuoYi通用能力重写 + Factory Foundation**。不能称可交付的MES Base。也未形成D意义的完整重新设计MES；它目前仍停在基础设施与合成探针阶段。

当前后端一个Maven应用，91个main Java文件、15个package；UI正式views只有Login/Home/PlatformPage/Files/NotFound五个。`src/main/java/dev/mesbase`没有Material/Routing/WorkOrder/Reporting等正式模块，UI也没有正式MES模块目录。测试/target生成页不能算产品模块。

RuoYi复用已证实的内容主要为Velocity初始化及Service/Mapper模板，系统能力采用简化重写。`V1__platform.sql`的sys_user只有id/username/display_name/password_hash/status/created_at，sys_role只有id/name/kind/factory_id/status；和老项目原字段相差明显。`PlatformPage.vue`一个通用页面承载多数平台对象，没有继承旧用户部门树、导入导出、菜单树/缓存/图标等完整产品交互。已有运行/测试证明安全与装配的部分行为，不能证明源产品功能已迁入。

## 2. Goal Misalignment

目标重新定义：ktg-mes是Source Product、功能与产品设计的主要来源；从真实产品抽取可复用能力，去公司化并修复必要问题。不是先造高级Foundation，再根据表重新发明MES。

偏离事实：

1. 只按上游表职责重建简化字段，替代原字段/状态/审计契约。
2. 把部门/岗位/公告/在线/监控按“暂不需要”DROP；这是错误排除理由，已由用户纠正。
3. Vue2页面只作参考，通用CrudPage替代成熟字段/查询/弹窗/联动，产品资产没有完整继承。
4. clean-room被扩大解释为旧项目代码/Schema/配置一律不复制，导致来源本身成为阻碍。
5. 以Foundation/Fixture/构建进展推进“底座成熟”，但没有形成可开启和交付的标准MES能力。
6. 本轮又把基础系统迁移称为新增CRUD、准备Codegen，这是同一偏差；用户已纠正，实际未执行该生成。

纠偏原则：Reuse before Reinvent，Migrate before Rewrite；COPY/PORT默认优先。REDESIGN必须说明具体错误/耦合，只重做必要边界并保留产品功能。DROP必须是专有/数据/Secret/错误或一次性能力的明确证据，不能因为旧、Vue2或不DDD而DROP。

## 3. Package / Module Review

### 3.1 真实布局

| 当前package | main文件数 | 判断 |
|---|---:|---|
| core.factory / core.security / core.error | 9 / 4 / 2 | Factory身份和错误/权限类型有用途；不要求成为独立DDD Core模块 |
| platform.management | 14 | 用户/角色/菜单/字典/参数聚合在PlatformService/Contracts，过于集中，修改一项要理解多个功能 |
| infrastructure.platform | 3 | 一个PlatformMapper/PlatformRows放多表，缺少旧系统按对象的可发现性 |
| infrastructure.persistence | 14 | 独立库事务/ownership/绑定校验有证据；Service使用入口较复杂，应收纳到framework的持久化支持 |
| platform.web / platform.security / platform.execution | 7 / 2 / 3 | HTTP权限/错误和上下文恢复有价值，属于framework职责，不是产品模块 |
| platform.codegen | 12 | 可保留效率工具；业务代码大段拼在Renderer，后续迁移模板适配应集中，而非成为产品设计来源 |
| integration.erp | 6 | 只有NOT_CONFIGURED provider和连接验证接口；不是ERP功能实现，过早展示为可插拔Integration会误导 |
| infrastructure.catalog / security / cache | 2 / 1 / 3 | 内存目录/偏好、演示授权、Key构造；需区分生产实现与测试/设计边界 |
| server | 9 | 入口、装配、演示、协议混在一起；可归admin入口与framework配置，不新增Application层 |

不存在正式`domain/application/business/common`模块；因此不能指控已有一套机械五层DDD。问题主要是技术分区优先、产品对象弱、配置和CRUD接入步骤多，以及平台对象聚合过度。

### 3.2 一个CRUD的层数与新增成本

平台链路：Controller → PlatformService → PlatformStore → PlatformPersistence → OwnedSqlSession → PlatformMapper → SQL。前三个中的Store负责一处事务/SQL异常转换，Persistence/Session是基础设施，不应宣传为六层业务架构；但普通开发者必须跨server/platform.management/infrastructure.platform/infrastructure.persistence才能跟踪一项设置。

生成厂CRUD：Controller → IService/ServiceImpl → BusinessPersistence.transaction → DedicatedDatabasePersistence → Resolver/RuntimeRegistry/OwnedSqlSession → Mapper/XML。业务仍是Controller/Service/Mapper三层，没有必要新增ApplicationService/DomainService/Port。

当前工具每表生成16文件，GLOBAL多一个注册配置共17：Entity、Create/Update、Query、VO、PageVO、Permission、Mapper/XML、IService/Impl、Controller、测试骨架、API、Vue、权限说明，GLOBAL再加MapperConfiguration。旧RuoYi标准骨架一般以Entity/Mapper/XML/IService/Impl/Controller/API/Vue/菜单SQL为主；Base增加DTO/VO与权限说明有安全价值，但每页独立PageVO、固定一对Service接口/实现及逐模块注册类可简化。文件数不等于耗时，本轮未测开发时间，不能断言耗时倍增。

main源码有14个public顶层interface候选；Mapper是MyBatis代理、Authorization/FileResourcePolicy有lambda/策略用途，不能全部按“只有一个实现”删掉。ErpAdapter只有声明且无实现，SharedDatabasePersistence没有运行实现；不应作为当前交付架构负担。完整清单在证据JSON，Renderer中的生成字符串不算运行实现。

### 3.3 具体问题与影响

- 平台按技术目录分散，而按功能集中成单Service/Mapper；不利于旧SysUser/SysDept/SysPost等迁入和定位。
- 过多Java文件使用单行压缩，虽能运行但不利于Source Product对比/审查；后续仅对迁入/触及文件按旧可读风格调整，不顺手全库格式化。
- 静态演示/占位实现与生产能力共处main，文档容易把接口壳当真实实现。
- 新Mapper必须白名单注册有安全原因；常规开发应由模块内固定配置一次完成，不能要求每个Service理解pool/lease/revision。
- FactoryPreference目前是内存偏好，Runtime目录从YAML读取，企业/工厂管理和偏好持久化尚非交付能力。保留已有约束，但当前不横向新增Foundation。
- 厂数1应只是配置数据；旧Wheel镜像不应照搬成第二套Base主链路，但变体中的通用流程必须进入候选，不能整包DROP。

## 4. What To Keep / Adjust / Remove

下表是建议，不授权或执行删除。REMOVE只指待替换的形态，不指把功能删掉。

| 当前能力 | 分类 | 理由与实施边界 |
|---|---|---|
| FactoryId/FactoryScope，无User↔Factory准入，认证/选厂/平台权限/厂权限独立 | KEEP | 防串厂约束明确；可移入老架构common/framework，包名不必保留 |
| Mapper ownership、独立Platform/Business事务、不回退默认库 | KEEP | 防错库的实际约束；作为framework持久化能力复用，不让业务自行选数据源 |
| RuntimeRegistry lease/revision与事务校验 | ADJUST | 已有负例证明价值；复用但集中到framework，业务只调用受控事务入口，不扩大Shared/集群 |
| 用户/角色/菜单/字典/参数的简化模型与单PlatformService/Mapper | ADJUST/替换 | 用源产品按对象的实现/完整字段替换，不维护两套长期可写真相；只保留必要新范围扩展 |
| 随机会话/撤销/逐请求权限 | ADJUST | 保留安全行为，验证能否接入旧Login/Security调用链；不因为已有实现就先排除RuoYi认证能力 |
| HTTP真实错误、requestId、显式endpoint分类 | KEEP | 接入旧Controller/BaseController，不恢复HTTP200伪成功 |
| Resource Scope/私有文件owner-policy | KEEP | 迁旧上传组件和文件流程时维持边界，不能恢复裸公共下载 |
| Codegen/CLI/Skill | ADJUST | 迁移阶段不使用；仅后续新增功能开发可用，迁移直接COPY/PORT源代码 |
| Shell/request/permission/dict/upload组件 | ADJUST | 可继续Vue3/TS基础；系统页继承旧产品设计，不能全挤进通用CrudPage |
| Cache范围Key规则 | KEEP | 实际缓存引擎/旧Redis能力单独迁；只有Key不等于缓存系统已迁入 |
| Druid/YAML dev/prod装配 | KEEP/ADJUST | 已有装配；继续原有效key/过滤器/监控能力，当前尚非完整Druid迁移 |
| main中无消费的ErpAdapter、仅NOT_CONFIGURED的provider拓展形式 | REMOVE候选 | 后续真实ERP迁移用源产品动作界定接口；保留未配置明确失败，当前不删除 |
| SharedDatabasePersistence接口占位 | REMOVE/移入设计资料候选 | 没有运行实现，本轮不扩大Shared DB；不为展示未来架构留业务接入负担 |
| 测试探针/演示与生产混淆的说明 | ADJUST | Fixture不算MES模块；保留历史产物/资料，未经授权不清理 |

## 5. Proposed Package Structure

根据用户最后指令，**优先沿用老项目RuoYi模块架构，当前Base结构不具备必须保留的优先权**。旧实际Maven模块为ktg-admin/common/framework/system/quartz/generator/mes/wheel/sqlserver。沿用成熟六个基础模块；mes留作后续业务迁入，wheel/sqlserver按功能/供应商边界审查，不照搬第二套Core或默认外部连接。

建议目标（待逐包落地，不在文档任务里直接搬空现仓库）：

```text
mes-base（parent；统一版本和构建）
├─ mes-admin       启动、Web入口/系统和监控Controller、dev/prod YAML
├─ mes-common      原通用工具、公共DTO/错误/分页、FactoryId/Scope等纯类型
├─ mes-framework   原Security/MyBatis/Druid/Redis等适配；Factory请求上下文、权限和数据库ownership
├─ mes-system      按user/role/menu/dept/post/dict/config/notice等对象的Service/Mapper/Entity/DTO
├─ mes-quartz      原任务与日志能力；受控执行登记、范围、claim/run token
├─ mes-generator   原生成管理功能 + 保留现有CLI/Skill（效率工具）
└─ mes-mes         后续md/pro/qc/wm/cal/dv/tm等业务；本轮不填新Domain
```

命名以源项目RuoYi惯例为主，不机械增加domain/application/port层。Controller只收参协议，Service规则/事务，Mapper访问数据；外部主动调用才用原可证明边界的Gateway。一套Core、一套MES代码，Factory count=1不分叉。

现有core纯类型→common；platform.web/security/execution与infrastructure.persistence→framework；Platform各对象→system；server入口→admin；codegen→generator；Job→quartz。保留已验证行为，允许替换具体类和package。Java21/Boot4/Jakarta/Vue3可以保持，升级与包搬迁分开验证。

前端保持单Vue3应用，沿源产品可发现路径组织`views/system/*`、`views/monitor/*`、`views/tool/*`，对应`api/system/monitor/tool`、layout/components/store/router；后续`views/mes/*`。迁入旧页面查询、列、按钮、弹窗/联动和字典/权限设计，不要求所有页变成一张通用表。

影响：pom依赖和主类扫描、资源路径/Mapper XML、显式registration、CLI launcher、测试引用、Flyway位置、前端import/catalog/routes、打包和local启动脚本。逐包边界验证；不先批量改包再试图修编译。当前脏文件保持；旧三端只读；禁止reset/rm或提交推送部署。

## 6. Feature Gap 与基础设施核对

业务模块级清单见[Inventory](MES_FEATURE_INVENTORY.md)，基础设施详细[Matrix](MES_MIGRATION_MATRIX.md)。427个Controller候选包括155个Wheel控制器、非HTTP基类和旧接口；不能用这个数量宣称427个运行功能。259个归并族只是静态候选，不是逐业务验收，本轮不继续深化。

基础系统已核对链路：

| 功能 | Source Product证据 | Base具体缺口 / 迁移办法 |
|---|---|---|
| 用户 | SysUserController→ISysUserService/Impl→SysUserMapper/XML；Web system/user/index+authRole/profile；dept/post关联 | 原字段/部门树/岗位/导入模板/导出/状态/授权；直接PORT原代码和页面，DTO/密码白名单与Scope适配 |
| 角色/菜单 | SysRole/SysMenu Controller/Service/XML，role authUser/selectUser，menu/index，permission store/getRouters | 保留role_key/data_scope/排序/树选择/菜单component/icon/cache/可见性；平台/厂权限显式扩展，不以userFactory替代 |
| 部门/岗位 | SysDept/SysPost完整Controller/Service/Mapper/XML；Web system/dept/post | 当前缺失；迁原树/唯一性/引用/删除/状态规则，不生成新CRUD替代 |
| 字典/参数 | SysDictType/Data/Config链路与Web dict/data/config | 原字段、默认/排序/样式/状态、缓存刷新/配置类型/审计；当前简化页面不足，COPY/PORT |
| 公告/消息 | SysNotice/SysMessage、mobile消息入口及Web notice/message；PDA notice | 当前无公告/消息；迁原操作和读取流程，收件人/权限/附件在服务端证明 |
| 日志/在线 | SysOperlog/Logininfor/Online链路；Web monitor对应页 | 原筛选/详情/清理/导出/在线查询与撤销能力；当前仅简化日志列表，不复制请求体采集Secret |
| Quartz | SysJob/Log、quartz service/util/mapper与Web job | 迁原时间/状态/日志/界面能力，invoke_target保留字段但执行改受控登记；保留scope和DB claim，不恢复任意反射 |
| Codegen | GenController/Table/Column services+gen_table/schema与Web tool/gen编辑/导入/预览/同步/下载 | CLI不是管理模块，迁原管理流程后适配当前模板/metadata与Scope，不用生成器造产品 |
| 文件/导入导出/编号 | Common/Attachment/Minio、ExcelUtil、SysAutoCodeRule/Part、上传/选择器/分页/打印组件 | 功能候选COPY/PORT，私有文件/资源范围/批量校验；真实存储连接与号码seed不带入 |
| Druid/服务/缓存/文档/表单设计 | DruidConfig/Properties与dev配置；Web monitor/druid/server/cache、tool/swagger/build | Druid池已装配，stat/wall/web统计/监控与其他工具尚缺；旧依赖升级、鉴权适配，不能把新安全summary冒充全部原监控 |

字段基线：原`ry_20210908.sql`17张sys表；官方固定版18张（另有notice_read）与182列的纯DDL已保存并在前包隔离库验证，但**应用仍使用简化V1**。sys_dept/sys_post/sys_role_dept/sys_user_post/sys_notice缺失于活动V1；sys_file/session/catalog_revision是Base扩展。不能直接把官方DDL覆盖现有V1、自动丢历史数据或因原字段不用而删列。基线以原产品契约为主，官方文件用于来源核对；新增列/扩展必须写清来源、意义和兼容路径。

## 7. Config Migration

配置也迁功能链路，不能仅复制一份无人消费的YAML：

- 原Druid master/pool/stat/wall配置有DruidConfig/Properties消费者；继承有效pool/监控配置。连接值外置；保留Platform/Business显式绑定，不恢复默认MASTER/slave/erp/hr/wms兜底。
- 原server/Tomcat/multipart/logging/messages是有效基础设施配置，迁key与消费者；厂或客户特有日志/存储路径外置。
- Redis原服务承担会话/验证码/字典/缓存等；不能只加Redis host/password来宣称已迁入，需要同时迁消费者并核Boot4键路径。会话权限不缓存跨厂长寿命快照。
- MyBatis/PageHelper、token/captcha/password-lock、springdoc/xss各自依赖和调用链同时迁；新HTTP/JSON校验保留，不能恢复业务200失败。
- dev/prod环境分离、所有环境凭证与真实URL外置；旧profile中ERP/HR/WMS/Mongo/微信/邮件/minio等只迁已选通用功能所需key，不复制旧客户连接/值。
- 根LICENSE有MIT声明但不能单凭根文件证明每项公司扩展可以公开复用。来源/所有权记录独立于迁移策略；在当前用户授权范围内抽取通用能力，无法核实的文件标REVIEW，不将整个旧产品降为参考。

## 8. Migration Phases（基础设施先行）

| 阶段 | 交付 | 完成标准 |
|---|---|---|
| 0 方向纠偏 | 本三文档、规则/ADR和Source Product候选索引 | 不再默认重写/排除旧功能；业务只模块级，不新增Domain |
| 1A 来源与旧基础模块装配 | admin/common/framework/system/quartz按旧架构迁入；generator作为开发工具另排；统一依赖与dev/prod有效配置 | 独立构建/打包启动、有效key有消费者、无客户Secret；Factory安全行为接入；无第二套长期主链路 |
| 1B 完整系统字段和设置 | user/role/menu/dept/post/dict/config/notice/profile+文件/导入导出 | 原字段/状态/树/关联/审计/界面逐项对照；兼容迁移/回滚边界明确；不能用新CRUD替代成熟功能 |
| 1C 监控与系统工具 | 原日志/在线/Druid/服务/缓存/Quartz/文档/表单工具；生成管理另排 | 原动作可操作、后端权限负例、UI真实调用、配置统计有实测来源；不是CLI/接口壳替代 |
| 2 最基础MES主数据 | 原md与必要cal/dv/tm候选 | 在阶段1后按物料/资源依赖迁；仍优先PORT旧代码 |
| 3 生产基础与现场执行 | 原工序/路线/工单/任务/报工/流程卡、PDA | 从真实流程和字段迁入，不另起新Domain；需要的ERP接口合同早对齐 |
| 4 质量/库存/追溯/WIP | 按原真实依赖逐功能 | 检验定义依赖先于现场录入；原本地与ERP真相边界明确 |
| 5 ERP适配与可交付 | 各生产/库存动作所需供应商适配与恢复 | 外部合同在阶段2/3前识别，按需求迁入，不能将所有ERP等待到最后导致前包不能操作 |

本轮代码授权仅基础设施/系统设置；业务实施待后续。阶段1以COPY/PORT为主，不用新Domain或Codegen替代。模块拆分与原代码迁入应选小闭环验证，不能先重新造Foundation再等待业务。

## 9. 审查命令、结果与边界

已执行：五仓`pwd/git status --short/git branch --show-current/git rev-parse HEAD`；`rg --files`按源码目录盘点；本地Python扫描Java imports/接口实现候选、Controller annotations、Mapper namespace/XML SQL、Entity/DTO/VO、Vue API引用/权限/字段、PDA pages.json和历史DDL CREATE。扫描范围排除node_modules/.git/target/dist/unpackage/uni_modules/log和生产数据dump。首次脚本把.sql同名目录当文件失败，修为is_file并只扫描sql/；文档脚本默认python编码失败，显式UTF-8后成功。扫描脚本位于target/realignment，安全结果落入版本化证据JSON；不保存配置值或业务数据。

本轮无旧数据库访问、无业务设备/服务运行验证；无Maven/UI新测试用于这次文档审查；前包83/18/11等历史测试只证明当时版本，不提升为本轮迁移证明。服务未启动/停止/切换。尚未执行部门/岗位等CRUD生成、代码迁入、包搬迁或表覆盖；详细Phase1实际进展必须另追加，不能提前写DONE。

## 10. 用户最后纠正与执行入口

“基础设施迁移不是新增CRUD”“最好沿用老项目架构，不合适可以推翻重新来”现为执行方向。当前分包可以替换，不因已投入而延续；Factory等保留的是已验证安全行为，不是强制保留旧类/包。小范围规则修正可立即执行；代码复制/适配从来源、模块依赖和新旧启动链对照开始，保护现有脏工作，不直接批量删除。下一代码包不得再以“生成sys_post/dept CRUD”为起点，应以原system/common/framework代码迁入为起点。


### 2026-10-05 最新执行纠正：迁移阶段不使用Codegen

用户明确要求迁移阶段忽略Codegen Skill；不调用、不生成、不作为门槛或例外审批。此前文档关于迁移时使用Skill或辅助骨架的表述由本条取代。现有Codegen代码/产物保留，开发期再使用。系统工具中的旧生成管理能力先冻结，不让其建设阻挡基础系统迁移；基础模块优先admin/common/framework/system/quartz，generator后置为开发工具。源产品COPY/PORT优先；旧架构可沿用，当前Base包结构可以替换。


## 11. Phase 1A 第一包：旧 common 通用代码迁入

SOURCE_PRODUCT_PORT：迁入 DateUtils、StringUtils、StrFormatter、Convert、CharsetKit、UtilException、UUID、IdUtils 八个完整源码，仅替换 namespace；另抽取 StringUtils 所需 HTTP/HTTPS 常量。未调用 Codegen Skill/CLI，未生成 CRUD。保留源项目 MIT 作者与许可，来源/HEAD/hash/修改见 [来源清单](../legal/source-product-migration-manifest.json)。commons-lang3 3.20.0 改为显式依赖，与已有传递依赖版本相同，未升级。

实际命令 `zsh -ic 'mvn -B -ntp verify -Dmes.test.mysql.config=target/local-dev/application-local.yml'`：86 项，失败/错误/跳过均 0，包含此次 3 项源接口兼容测试及已有真实测试库验证。`zsh -ic '/usr/bin/python3 scripts/smoke-packaged.py'`：11 项通过，使用临时随机端口/合成账号，只终止自己创建的进程。八份完整源码归一 namespace 后与源文件一致；9 项 hash 校验及 JAR 内许可校验通过。shell 缓存写入受限警告未影响通过；辅助文档脚本编码错误已修复；许可检查最初误判 BOOT-INF/classes 路径，实际 META-INF/licenses 验证通过。见 [验收 JSON](../reviews/2026-10-05-common-migration-verification.json)。

这是 common 首包，尚未接入系统模块调用链，不代表完整基础设施迁移。未覆盖已应用数据库迁移，未改 UI 产品代码，未重启已有查看服务，未提交/推送/部署；用户已有 Codegen 实现差异保留。第9节为本包前的审查快照。下一包沿旧 common/framework/system 依赖链迁入，不生成用户/角色/菜单/部门/岗位替代功能。


## 2026-10-05 Phase 1 全量迁移执行记录（进行中）

用户已授权 Source Product 基础系统全量 COPY/PORT；本轮不调用 Codegen，不迁 MES Domain、不提交/推送/部署。已建立 mes-common / mes-system / mes-framework / mes-quartz / mes-generator / mes-admin Maven reactor，源码与配置按职责迁入；Dept/Post/User/Role/Menu/Dict/Config/Notice、日志、在线会话、Quartz、监控前后端正在接通。状态 **MIGRATING**，源码已存在、构建通过均不等于 VERIFIED。

已通过：`zsh -ic 'mvn -B -ntp package -DskipTests'`（package-8）；Node22 下 UI `npm run build`（build-3）。隔离验收数据库仅从本项目自有测试库复制，V1 不改，V2/V3 Flyway 已执行；旧表/开发数据归档保留。原项目只读；本轮开始前两端 dirty 内容备份在 target/phase1/baseline。

实际失败并修复：Boot4/Jakarta 依赖和 Excel/POI 兼容；Vue3 slot/SFC 类型、显式 .vue 引用；Flyway 元数据权限探测使 Druid 连接失效（迁移改用独立连接，未扩大 MySQL 共享服务元数据权限）；重复导入模板权限注解；Quartz resultMap 中 id/result 顺序；打包插件输出薄 JAR 与可执行 JAR 路径差异。凭证仅存忽略目录、模式0600，文档不记录。

尚未验收：真实完整启动、所有页面实际操作、导入/导出、资源归属/权限负例、任务/消息/附件/个人资料全链路、正式 mvn verify/UI test、浏览器验证。后续记录逐包补齐，不能把这一段当作 Phase 1 完成回执。


### Phase 1 执行追加：系统链路、真实 MySQL 与安全合并（2026-10-05）

状态仍为 **MIGRATING**。六个 Maven 模块已接入正式 Source 系统调用链，旧简化平台写接口仅在显式 TEST ONLY 条件下注册；原开发表和数据保留归档，不改已应用 V1–V4。系统登录/Redis 在线会话、组织用户角色、公告消息、附件及监控在本轮独立 schema 上运行；Factory Foundation 保留。

本包证据：此前 `mvn -B -ntp verify`（verify-1）86 项/0失败/3跳过；Source 系统真实 MySQL + HTTP 合成操作首轮 26/26（target/phase1/acceptance-crud.json）；Node22 UI build-8 通过；后端 package-22 通过；backend-11 完整启动，19 个实际接口检查为200。以上分别为回归测试、真实测试引擎、构建与本地运行证据，**不相互替代、不代表完整浏览器验收**。浏览器已实际新增部门，排序必填错误提示有效；余下操作继续执行。

新增合并：Source Role 显式 PLATFORM/FACTORY 表单；Source Quill 完整工具栏、私有公告图片/视频引用与后端 DOM 解析过滤；Druid stat/wall YAML 消费；一次性验证码原子取出；Quartz 立即执行经独立 DB claim、run token 与真实日志，调度副作用延后到提交后，支持显式确认死亡后的受控恢复。Minio SDK 与 YAML 配置结构接入 FileStorage，无源环境值、无公开对象 URL；**Minio 外部实例尚未验证**。

实际失败：新增 Druid Settings 重载构造使 YAML Binder 未绑定，backend-10 启动失败；已移除重载并通过 backend-11。Minio 9 的 JVM HTTP 依赖及 Long 参数兼容已修复。npm/Maven 依赖写入受沙箱限制时用审批沿用用户缓存，未建项目 .m2/.mvn/private mirror。Phase1 文件目录已从原服务目录复制隔离，原文件保留；仅本轮18081进程重启，原18080/15173服务未动。

待验证边界：新 Source 安全测试、Quartz实际启停/执行/恢复、Excel导入导出、附件归属负例、完整页面操作及全部矩阵；新构建与当前运行JAR版本仍按日志逐次记录。未提交、推送、部署或迁入正式MES业务。


## 2026-10-06 Phase 1 追加：完整 Source 系统、编号工具、真实验收（仍在进行）

有效目标是 Source Product 基础系统全量 COPY/PORT，迁移阶段不调用 Codegen Skill/CLI。现有正式结构为 common/system/framework/quartz/generator/admin 六模块，旧简化平台只在显式 TEST ONLY 条件注册。Factory Foundation、独立 Scope/权限/资源和 Mapper ownership 保留，未迁正式 MES Domain，未提交/推送/部署。

- Source Dept/Post/User/Role/Menu/Dict/Config/Notice/Message/Attachment、日志、Redis 在线、Excel、Quartz和监控已经接入前后端与真实测试库；整体状态 **MIGRATING**，未把源码或 build 当作 VERIFIED。
- Batch10 增补 Source 自动编码规则/组成/结果三表及旧完整页面。COPY 保留 Source 字段；PORT 预览不占号、明确 POST 生成、平台事务内规则/计数行锁、周期隔离和长度溢出拒绝，无 JVM 锁/ThreadLocal 可变计数。仅通用平台编号，不自动注册 Factory/MES 编号消费者。V6 已应用。
- 真实 MySQL/HTTP：组织用户首轮26/26；文件/Quartz/导出首轮29/32，3项 DTO 回写兼容失败已保留，修复后的回写与真实Excel导入追加11/11；自动编码17/17，包含24并发请求生成唯一连续号码、精确长度边界、预览不占号、无权限403。首次管理员空库初始化/重复拒绝2/2，独立18082进程登录/系统接口6/6，该临时进程已停止。详见 `../reviews/2026-10-06-phase1-verification.json`，这些不是完整浏览器验收。
- 浏览器已实际新增/编辑部门、岗位，新增字典类型与 PENDING 数据；参数新增已执行，仍继续核验。图片证据保留在自有 target/phase1，最终另提供可查看结果。后续用户/角色/菜单、密码、任务、文件、导入导出和监控仍要逐项闭环。
- 后端上一轮 verify-4：91 run / 0 failure / 0 error / 3 skip（88通过）；当前最新源码仍需最终 verify/smoke。package-37通过；UI Node22 test 18/18、build通过（ui-check-4）。实际 run JAR 为 phase1-runtime-10，18081/backend-17；UI15174/ui-runtime-4。18080/15173原服务未动。
- 修复 Source 编辑 VO 的只读元数据/数值排序、角色部分状态 DTO、服务端新建 ID、Source菜单目录按显式管理权限查看（不能用 magic userId）、角色菜单树权限、任务日志详情 path variable，以及原表 start_time/end_time 的映射。V7只加实测耗时、停用迁移生成而没有真实接口的按钮，保留数据与分配，不改已应用V1–V6。
- Source在线消息 PORT 为仅接收、带Authorization的SSE，提交后按持久化接收人刷新；不恢复匿名WebSocket客户端任意转发。Source浏览器/OS有界解析，审计投影保留requestId/actorId/factoryId/scope/耗时，未采集请求体。SSE交付/撤销仍待真实验收。
- Vue3已合并 Source图标/布局工具、隐藏菜单路由、缓存/参数、手动刷新权限、字典失败可重试、私有 FileUpload/ImageUpload 文件选择及预览。密码弹窗去掉原Source显示新密码的成功提示，长度约束与后端对齐；后续涉及新凭证的浏览器操作按工具规则交给用户填写。
- Redis平台键现在按可信 Platform JDBC 身份派生命名空间，也允许显式 YAML 的 MES_SYSTEM_CACHE_NAMESPACE；会话、Source缓存、验证码、密码锁同域。Factory业务缓存仍须显式FactoryId，不能复用平台命名空间代替厂范围。跨数据库证明正在执行。

实际失败与处置：Druid wall 拒绝自动编码列表原 WHERE 1=1，已改 Source XML 的 where 结构，未放宽SQL防火墙；新任务控制器变量名与Druid SDK getter编译失败已修复；首次初始化V7重复添加Source原有start_time失败，仅在本轮自有空库确认无DDL应用后清除失败历史记录并重新迁移，原失败日志保留。UI一次启动误用全局Node18失败后明确切Node22；CHOKIDAR环境值没有使Vite8刷新旧模块，改显式 MES_DEV_WATCH_POLLING=true 的 watch消费者并仅重启本轮UI。组件加载/API响应异步导致过早读取DOM/填入查询框，已改为等待可见目标并限定弹窗；不能将这些尝试当作通过。

剩余边界：完整浏览器矩阵、最新回归/打包 smoke、Source权限/会话撤销/锁、SSE、任务调度恢复、外部Minio实例、部署/生产。状态按实际证据逐项更新，不能提前宣告Phase1完成。


## 2026-10-06 登录、隔离与浏览器工作包续接快照

用户因电量暂停后明确恢复；继续 Phase 1，不创建新任务、不提交/推送/部署。后端 main/975a9df4687f882bf251ca09bfb242015d33beff、UI main/18e372c2e6da0c6ab9738d562f99bf0d5bf3c6ca 未改变。迁移差异及原用户修改均保留，基线备份在 `target/phase1/baseline`；Source 三端只读。

### 已完成及证据

- 请求的 admin 账号经正式 Source User API 创建，userId105，显式 PLATFORM 角色100，无厂角色。实际 API 登录200；浏览器登录与工作台已验证。仅忽略目录 `target/phase1/admin-login.json` 保存私有登录输入，模式0600；`acceptance-admin.json` 为无凭证证据。
- 密码策略迁为 YAML 消费：基础/prod min12，dev min5，UTF-8 BCrypt 上限72字节；登录超限401，用户新增/重置、Excel和个人修改使用同一策略。UI 从 `/api/system/info` 读取精确政策。首次管理员 CLI 仍需复核其长度策略，不能假设已统一。
- Redis namespace 必须绑定可信 Platform JDBC 身份；显式 name 只是标签，不能覆盖数据库身份。独立 peer schema 复制本轮合成测试数据并共享自有Redis，交叉 token 双向401、相同用户/配置键隔离、真实 Druid SQL/Wall、受认证 SSE 提交后刷新/接收人归属/注销终止共 **34/34**，命令 `/usr/bin/python3 target/phase1/accept-isolation-and-stream.py`，日志 `isolation-and-stream-5.log`，记录 `acceptance-isolation-and-stream.json`。
- 浏览器实际创建/编辑 Dept201、Post2，创建 DictType114/数据PENDING，创建 Config101、空权限Role102。Menu2232指向已编译 `system/config/index`，仅为已有平台参数权限的导航别名；角色100保存分配，手动刷新权限后实际打开 `/system/qa-config-20261006?from=browser`，真实参数数据可见。截图 `browser-dynamic-route.png`。
- 浏览器 Source 用户104编辑已保存：昵称、部门201、岗位2回填生效，已有角色保留；截图 `browser-user-edited.png`。Source User 的 DictSelect 已实际显示数据库字典“正常/停用”。用户状态点击尚未完成；密码/新增用户/导入凭证的 UI 用户交接仍待完成，不把 API 创建冒充浏览器创建。
- 最新后端回归 `zsh -ic 'mvn -B -ntp verify'`：verify-9成功，93 run/0 failure/0 error/3 skip（90通过）；3项 MySqlDevelopmentTest 未启用环境跳过，独立真实 Source HTTP/MySQL 证据另计。UI 上轮 ui-check-6：21测试/build通过；最新 ui-check-7 正在执行。打包 smoke 尚需按最终源码重跑。

### 实際失败、修正与未通过边界

1. ConfigKey 自定义参数403及字符串误走 success(msg) 已修正：显式平台 query 权限可取；接口返回 data。Druid limit=0 原500，改为400。SSE 首轮 Inbox 使用标题模糊匹配的测试假设错误，Source XML 是精确相等；改验收输入后34项通过，失败日志1–4保留。
2. 菜单管理曾读取 identity-only LoginUser 的空权限集，导致新菜单不可发现；改为每请求实时平台权限。已用实际菜单管理及动态路由验证，无 magic admin ID/长期权限缓存。
3. Source 数据范围的常量 `1=0` 被 Druid wall 拒绝，Self 用户详情500；改为绑定拒绝布尔，空/无效角色也拒绝范围。详情和角色详情按用户ID校验，越界返回403。scoped-security-3已证明本人200、外国用户详情/角色详情/状态操作403，客户端 params[dataScope] 注入无效。
4. Source User 详情的只读 roleId 回传未被 UserForm忽略，浏览器保存400；已补齐已知只读字段，实际页面保存通过，未放宽任意未知字段。
5. 厂角色新增 Mapper 漏写 kind/factory_id，真实负例看到厂角色成为 PLATFORM/null；已补齐 insert字段并保留生成ID。错误合成账号108及角色105已通过正式接口停用，既有角色不批量改写。verify-9通过；实际持久化/权限完整重跑仍待完成，不能先标 VERIFIED。
6. Quartz 首轮每2秒调度和立即执行撞 DB claim，409被误当失败；隔离立即执行后再启密集Cron，已经实测 SCHEDULER 日志。日志字段是 Source stopTime，测试错读 endTime导致 KeyError；验收脚本已修正，恢复段仍待继续。失败日志 quartz-schedule-1/2保留；两次脚本finally均成功暂停自己的新任务。
7. 本轮重启曾漏显式18081而尝试18080端口，backend-22启动失败；原18080进程未停止。已显式地址/端口恢复到18081。早先构建SDKgetter/Source XML/首次bootstrap迁移/UI刷新等失败仍在前文和 target 原日志，不覆盖为通过。
8. Element Plus dropdown-item 无单一DOM根，v-hasPermi指令失效并告警；已改为同一平台权限的模板判断（用户/角色/Quartz），保持后端最终授权，需浏览器核对和 ui-check-7。

### 运行现场与恢复命令

| 现场 | 本轮版本/日志 | 所有权与边界 |
|---|---|---|
| Main backend18081 | `target/phase1/phase1-runtime-16.jar`，`backend-24.log`，exec session70216 | 本轮自有，可按已授权生命周期维护；先确认真实PID，不能依赖旧session跨重启存活 |
| UI15174 | `ui-runtime-4.log`，exec session59200；Node22，显式watch polling | 本轮自有；Token仅内存，页面刷新或过期需重新登录 |
| Peer18082 | runtime14，`backend-peer-4.log`，session41695；独立peer schema | 本轮自有，隔离/锁证明用，所有任务暂停；完成后只停止该peer进程 |
| Redis16379 | 官方Redis8.2.10，`target/phase1/redis.conf`，session21349 | 本轮自有，loopback、私有密码；不使用/停止用户已有Redis |
| 原18080/15173 | PID55129/52881（续接只读核对仍在） | 原现场保留；不能当新Source系统，不停止它们 |

忽略目录配置与输入继续保留：`target/phase1/application-phase1.yml`、`peer-config.yml`、`redis.conf`、`target/local-dev/mysql-client.cnf`。本轮主 schema `mes_base_phase1_20261005_46ae8d`，peer `mes_base_phase1_peer_20261006_4a9ece`；只查/写自有测试库，不查实例其他业务库。V1–V8已应用，V1不改。私有文件根 `target/phase1/files` 与原服务文件隔离。

若机器重启，先检查端口/PID与Git，再只恢复缺失的本轮服务。后端确切命令（不输出配置内容）：

```sh
zsh -ic 'exec java -jar target/phase1/phase1-runtime-16.jar --server.port=18081 --server.address=127.0.0.1 --spring.profiles.active=dev --spring.config.additional-location=file:target/phase1/application-phase1.yml'
```

UI在 `/Users/user/work/lixin/mes-base-ui` 沿用 `.nvmrc` Node22，`MES_DEV_API_TARGET=http://127.0.0.1:18081 MES_DEV_WATCH_POLLING=true` 与Vite显式15174；不要改变全局Node。不要重新创建数据库/管理员或清理旧资料。

### 准确续接顺序

1. 完成新的厂角色持久化/同token实时变更/厂A和B/禁用-启用/密码撤销/在线force logout/跨库密码锁脚本：`accept-scoped-security.py`。每次角色名需带唯一后缀，旧重复名称422是验收输入问题；失败快照JSON与日志保留。
2. 完成 `accept-quartz-schedule.py` 的实际Cron、canonical job、run-state、matching token/暂停/年龄/显式死亡确认恢复，恢复故障由本轮SQL注入**合成中断claim**，不能宣称真实进程崩溃或集群验收。
3. 继续浏览器 User状态/既有角色分配、Excel导出与模板/导入、Notice富文本私有媒体、Message/Inbox、文件上传下载与确认取消、Quartz创建启停执行日志、监控真实数据、Profile及窄屏错误重试。新密码/认证凭证由用户填写并提交（浏览器工具要求）；用户先前回答“稍后操作”，不能当作已完成。
4. 最终 verify、packaged smoke、Node22 test/build；Source只读Git/hash和原DefaultMesCodegenService基线字节保护核对。刷新 source-product-migration-manifest 的新增源码/哈希/安全PORT理由；DDL独立脚本需补齐V6–V8；校正README/Platform/架构及本矩阵的过时快照。
5. 所有 Backend+Frontend+DB+Permission+真实操作通过才将对应功能 VERIFIED。外部Minio实例、部署/生产、Shared DB和集群尚未验证；不新增这些范围，不迁正式MES领域。
