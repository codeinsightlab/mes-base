> 2026-10-13：本文质检引用为 HISTORICAL / DEFERRED；当前 qc_index、qc_defect 提前实现已移除。

# MES Base Realignment Report

## 2026-10-06 当前范围：基础架构先行，业务分析后置

用户决定撤出生产监控和人员分析。两项能力的后端 `mes-mes`、前端页面/API 及专属测试已归档到 `migration-backups/2026-10-06-deferred-production-analytics/`，不再参与构建或 Mapper 扫描。V15 撤销菜单和角色菜单关联；V13 保留原样。此前关于继续迁入或默认关闭这两项能力的内容属于历史记录，由本节覆盖。

当前保留工厂/数据源、权限/资源隔离、系统监控、同步协调和三工厂持久化基础。隔离与事务验证继续使用独立 TEST ONLY 探针，不能依赖尚未接入的业务。后续恢复需重新审查业务 Schema、SQL、权限、Factory 范围及页面契约；归档不是验收通过。


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


## 2026-10-06 续接：安全与 Quartz 工作包验收

实时 Git：后端 main/e292789a2f7bac4519e93685adf955428ade61ab；前端 main/d2b19714cb5018d07fa3171b76585eab45b325ce。用户已建立新的迁移提交基线；本次未提交、推送或重置。开始时后端仅 SysLoginService 规范账号锁键修正未提交，前端干净。上一节 HEAD/运行版本为之前快照。

**结果：** scoped-security-5 59/59；quartz-schedule-4 23/23。前者覆盖厂角色 kind/factoryId 持久化、无 UserFactory 登录/选厂、PLATFORM/FACTORY及当前厂权限、Self SQL范围、越界用户ID、禁用/重置/注销撤销、跨库独立密码锁和YAML 10分钟TTL。证据为本地应用连接用户指定远程测试 MySQL上的自有合成数据，不是本地MySQL实例或生产验收。

实际失败及修正：相同 MySQL 用户的大小写输入能避开按输入名称创建的密码锁；修正用持久化 userName。规范及大写变体均401，peer同名账号200，before JSON保留。scoped-security-4前段通过后因测试错误假定private YAML URL为独立行失败；按JDBC URL本身提取后5号完整通过，不输出配置。Quartz3的matching claim恢复409来自测试SQL会话SYSTEM/CST和JDBC UTC差异；CLI在自己会话设置UTC后4号通过，未改全局数据库时区。

Quartz实际立即执行和Cron均有成功日志；伪造handler/group不生效。仅向自己新建的暂停任务注入合成中断claim，验证死亡确认、年龄、matching token、重放和RECOVERED_UNKNOWN。此前自己的暂停任务103残留合成claim也以同token正式恢复，acceptance-prior-quartz-claim.json通过；未清理其他任务。不能把这一实验写成真实进程崩溃或集群证据。

实际命令：
```sh
zsh -ic 'mvn -B -ntp verify'
/usr/bin/python3 target/phase1/accept-scoped-security.py
/usr/bin/python3 target/phase1/accept-quartz-schedule.py
# UI目录
zsh -ic 'nvm use 22 >/dev/null && npm run test && npm run build'
```

verify-10：93 run/90 passed/0 failed/0 errors/3 skipped；ui-check-7：21/21和vue-tsc/Vite通过，保留大chunk提示。自有main18081/PID20258/session28074现为runtime17/backend-25.log，V1–V8校验及启动通过。自己的peer18082/session41695已正常停止，schema及文件保留；原18080/15173未停止。

下一步：剩余浏览器流程、通用基础设施处置、最终smoke、来源清单、独立DDL与配置/架构文档。整体MIGRATING；未把API通过当全部UI通过，Minio外部实例、部署/生产尚未验证。文档更新脚本首次误用JSON checks键产生KeyError、写入前退出；核实真实regression键后修正，没有覆盖旧证据。


## 2026-10-06 注册与框架兼容工作包

SOURCE_PRODUCT_PORT：RegisterBody、SysRegisterService、SysRegisterController及Source register.vue字段和确认/验证码/成功回登录流程迁入。保留 `sys.account.registerUser` 参数开关，缺失或非true关闭；登录前仅公开开关、验证码及同一YAML密码政策。注册不接受角色/部门/岗位/Factory输入，不发放角色或权限；账号XSS校验、唯一性、注册审计和插入处于明确SystemUnit。前端新增/register公共路由、密码确认和精确政策读取，关闭状态已实际浏览器验证；注册填写与提交UI仍待用户交接。

真实远程测试MySQL/localHTTP：registration-1 29/29，captcha-2 21/21。命令 `/usr/bin/python3 target/phase1/accept-registration.py`、`/usr/bin/python3 target/phase1/accept-captcha.py`。只短时开启自己schema的Source注册参数并finally恢复false。验证码测试只为自己的main进程CLI覆盖 `--ruoyi.captcha.enabled=true`，实际PNG、错误/正确一次性消费及重放401、登录与注册共享验证码均通过；API harness只读自己生成的短期Redis挑战，不输出或保存答案，不是浏览器验证码操作。captcha-1失败是测试假定UTF8存储：Source RedisCache实际JDK String序列化；按已知String原语头与限定字节长度解码后2号通过，不反序列化任意对象。验收后自己的main恢复原dev YAML，prod默认验证码仍启用。

首个管理员CLI去掉独立硬编码12字符规则，调用已有PasswordPolicy消费相同YAML与UTF8限制；已有账号未重置。Source用户角色页原返回/user/user导致404，修为/system/user，实际幂等保存回到用户列表通过，既有角色未扩大。Source公共下载接回原项目固定file-saver2.0.5及对应TS声明，Excel/私有文件统一使用成熟实现，浏览器下载重验继续；没有仅因HTTP导出成功先宣布浏览器下载成功。第一次npm安装因沙箱不能写用户既有缓存失败，按工具审批用原Node22正常安装通过，未改缓存权限/全局Node。

浏览器刷新断开SSE时，通用异常handler曾尝试向不可写的text/event-stream输出JSON，产生二次转换错误。新增明确AsyncRequestNotUsableException分支，仅记录requestId及debug异常链，不重写已不可用响应；其他业务异常仍真实HTTP错误。依据[Spring7.0.9默认异常处理](https://docs.spring.io/spring-framework/docs/7.0.9/javadoc-api/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.html#handleAsyncRequestNotUsableException(org.springframework.web.context.request.async.AsyncRequestNotUsableException,jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,java.lang.Object))，真实刷新重验待继续。

macOS下OSHI/DiskArbitration读卷失败时，Server以JDK实际读取应用所在卷容量，返回明确diskSource；UI显示“当前应用所在卷”，不伪装全部挂载卷，也不填假0。该兼容分支保留Source字段，需要运行及UI验证。

verify-11（同标准Maven命令）93/0fail/0error/3skip；ui-check-8（同Node22test/build命令）21/21和build通过。稳定runtime18，backend-26启动通过，27为临时验证码开启实验；28为恢复原dev配置后的当前main（18081）。Source只读，生成器未调用，没有新MES业务。浏览器剩余流程、最终smoke/DDL/来源与规划矩阵继续，整体MIGRATING。

## 2026-10-06 公告、任务、系统工具与 OpenAPI 工作包验收

### 实时基线和用户修改

续接时后端 main/e292789a、UI main/d2b1971；期间检测到用户新增后端本地提交，当前 main/4891535f735b18ca87918d8bcff1546663a53d02，UI HEAD不变。Agent没有执行commit/push/deploy。用户将启动类移动为 dev.MesBaseApplication，并调整默认profile/dev连接；这些修改保留，POM适配启动类，HTTP测试显式关闭真实Runtime/System以保护用户环境。本轮应用始终加载私有 target/phase1/application-phase1.yml，不访问后来写入工作区dev的其他Schema。当前剩余差异继续保留，不能reset到续接旧HEAD。

Source后端 master/310c6ef1bb37d2533fe893579ba837c353b76926干净；Source UI master/923d50ce35287c71ac7d1455bccbbfb8068e8e8a有原有 src/views/system/openApiClient/index.vue 修改。Source只读，OpenAPI页面/API实际从git show HEAD读取，逐文件来源清单记录HEAD哈希；当前清单392项，目标哈希一致。生成器实现与前包基线字节一致，迁移没有调用Codegen Skill/CLI。PDA/外部服务未宣称完成。

### 公告、Quartz、Swagger、SQL

公告媒体写权限错误地用userId匹配按username设计的授权服务，导致admin403；SysAttachmentServiceImpl两处改为服务端username。真实PNG上传/绑定公告、已发布/未发布、非owner当前查询权限、权限撤销、错误资源和绑定后删除拒绝共24/24；浏览器公告13与私有图片保存/回读。不是公共图片URL。

Source暂停任务仍允许授权手动执行一次。DB claim统一入口增加manual参数，调度只领status0，手动允许status0/1，已有runToken仍409；新工作包26/26，前23/23快照保留。UI日志路由改为Source实际 /monitor/job-log/index?jobId=105；浏览器实际执行暂停任务105一次并看到成功日志21。该任务仍暂停；未交付集群JobStore/fire永久去重。

系统工具补Source系统接口菜单/tool:swagger:view和Springdoc3.0.3/SwaggerUI5.33.1。SDK默认MVC入口关闭，显式受授权的 /api/tool/swagger/json 才提供文档；初包21/21，浏览器显示实际OpenAPI3.1和Source DTO。外部接口加入独立OpenApiBearer，Swagger外部请求不自动携带后台Token/选厂头，代理只到明确loopback目标。

scripts/export-phase1-sql.py直接导出当前有序迁移/哈希，最新V1–V10导出为 target/phase1/sql/mysql-phase1.sql。真实测试MySQL8.0.40上新建自有空Schema mes_base_phase1_sql_20261006_c57ad0并导入，426字段的类型/默认值/可空/Extra/顺序与运行库一致，用户/角色为空，三模块菜单存在，5/5。首次V1–V8检查暴露缺系统工具菜单，失败及随后V9/V10结果保留。手工导入没有Flyway history，不能随后开启migrate重放；空库Flyway优先。

### OpenAPI 的 Source PORT 与用户本轮决定

用户明确要求迁移OpenAPI，Base放宽业务数据范围、预留验证开关，未来范围不绑定工艺。迁入common/openapi、system/openapi、framework/openapi、OpenApiClientController及Source提交中的Vue/API；52份Java/XML初次PORT后适配4份Source配置、安全链和通用策略扩展。保留Source表 open_api_client / open_api_client_api 的原字段、DTO/VO、分页、默认禁用、一次性Secret、行锁、启停/重置的credential_version、Redis Lua、当前MVC API目录和分组授权。Lombok展开为等价JavaBean；没有重新定义业务字段或生成CRUD。V10无账号、凭证或公司seed，无ProRoute/路线授权表。

后台管理是显式PLATFORM list/query/add/edit/reset/authorize；新菜单不自动赋角色，测试通过正式Role API给自有角色100追加，所有已有菜单绑定保留，仍PLATFORM且无factoryId。外部客户端不是内部SysUser/Role，专用 /open-api/** 安全链保留客户端认证、当前API授权和限流；不能用内部Token调用，不能用外部Token进入系统管理。Foundation/Mapper/资源归属没有转成appKey内部账号或首厂兜底。

YAML open-api.data-scope.enabled 默认false，可通过MES_OPEN_API_DATA_SCOPE_ENABLED/受控外部YAML开启。仅控制通用业务数据范围策略；开启必须提供唯一OpenApiDataScopeValidator Bean，否则启动失败。策略接收可信外部身份、API code、请求和HandlerMethod，未来MES可按实际领域表达范围。Source诊断方法的DataScopeIgnore仅跳过不读取业务数据的范围验证，认证/授权/限流不豁免。当前没有正式MES业务校验器或已开启的业务数据库闭环；纯策略调用/拒绝/诊断豁免7/7，真实缺策略开启启动失败符合预期。

真实API包71/71覆盖创建/查询/分页/默认禁用、hash-only、无Secret详情、启停/重置/版本即时撤销、显式API授权和当前capabilities、8并发请求同Token复用、TTL、令牌撤销后不复用、内部/外部身份隔离、429与安全requestId、日志无Secret/Token、Source真实metadata/SDK。Source为重复申请复用同Token，受控Redis客户端记录保存恢复材料；SQL/日志不存明文Secret/Token，不能把此链误写成内部会话的hash-only value。普通API不续期，当前DB版本/权限每次检查。

双库29/29：同一Redis上刻意构造同clientId/credential_version/API授权，mainToken在main成功、peer401，再回main成功；不是靠不存在client来宣称隔离。peer18082验收后停止，两个客户端均禁用，库/文件保留。补包18/18：list-only平台角色可读列表及页面开关，但详情/授权403；恢复原空角色后再次403；超长UTF8错误Secret401、缺凭证400，最终客户端禁用。新合成Role无用户绑定，不删除测试证据。

浏览器已核对实际列表/详情、开关关闭、两组五个API/current grants、新建名称/备注/默认禁用表单和取消；没有自动提交浏览器新凭证或赋权保存。Secret创建/重置和授权保存的实际后端链路由独立API包验证；浏览器这些敏感提交仍未验证。截图见 docs/reviews/assets/2026-10-06-phase1/browser-openapi-detail.png、browser-openapi-grants.png。

### 实际命令与结果

| 工作包 | 实际命令 | 最新结果/证据 |
| --- | --- | --- |
| 标准后端门禁 | zsh -ic 'mvn -B -ntp verify' | verify-22.log：总100，97通过、0失败/错误、3跳过（framework7+admin93） |
| UI门禁 | zsh -ic 'nvm use 22 >/dev/null && npm run test && npm run build' | ui-check-12.log：21/21，vue-tsc/Vite通过；已有大chunk提示保留 |
| 当前打包smoke | zsh -ic '/usr/bin/python3 scripts/smoke-packaged.py' | smoke-6.log：11/11；当前runtime23/新启动类 |
| 公告 ownership | /usr/bin/python3 target/phase1/accept-notice-ownership.py | notice-ownership-3.log，24/24 |
| Quartz调度/恢复/暂停手动 | /usr/bin/python3 target/phase1/accept-quartz-schedule.py | quartz-schedule-5.log，26/26 |
| Swagger | /usr/bin/python3 target/phase1/accept-swagger.py | swagger-1.log，21/21；随后OpenAPI71包复核外部SDK |
| OpenAPI | /usr/bin/python3 target/phase1/accept-openapi.py | openapi-2.log，71/71，runtime22 |
| OpenAPI双库 | /usr/bin/python3 target/phase1/accept-openapi-isolation.py | openapi-isolation-1.log，29/29，runtime22 |
| OpenAPI最小权限/输入 | /usr/bin/python3 target/phase1/accept-openapi-boundaries.py | openapi-boundaries-1.log，18/18，runtime23 |
| 开关缺策略负例 | java -jar target/phase1/phase1-runtime-22.jar --server.port=0 --server.address=127.0.0.1 --spring.profiles.active=dev --spring.config.additional-location=file:target/phase1/application-phase1.yml --open-api.data-scope.enabled=true | openapi-missing-policy.log：预期失败，未开始服务 |
| SQL导出/空库metadata比对 | /usr/bin/python3 scripts/export-phase1-sql.py；/usr/bin/python3 target/phase1/verify-sql-export.py | sql-export-3.log，5/5，V1–V10/426字段 |
| 生成器保留 | 基线DefaultMesCodegenService与mes-generator当前文件逐字节/SHA256比较 | acceptance-codegen-preservation.json；未调用生成工具 |

所有HTTP/SQL为本机应用连接用户远程测试MySQL、独立Schema和合成数据；不是本地MySQL实例、部署或生产验收。早期包仍按原版本/时间记录，不把所有旧包写成最新runtime重新跑过。最后仅页面开关查询权限调整，runtime23补包18证明，未重复全71矩阵。

失败保留：verify-19的PORT展开漏泛型类型注解字段getter，补getApiCodes/setApiCodes后verify-20/21/22通过。OpenAPI首包把Source业务枚举非法状态预期写成400，实际422符合当前SourceBusiness契约；保留first-attempt JSON，修正验收预期后71/71，没有为了测试改Source失败语义。notice首两包分别连接尚未启动服务、缺必需role布尔字段；随后24/24。verify-14缓存权限、15–17默认profile/真实runtime继承测试环境问题记录保留，显式测试隔离后18及当前22通过。浏览器冷依赖优化/Vite重启导致内存Token清空后重新登录，未认作后台认证失败；新凭证表单已重备。

### 浏览器、当前运行与未验证边界

当前记录补用户状态关/开、原角色保存回用户页、关闭注册提示、导入file chooser取消、私有文件上传/删除取消、公告私有PNG、暂停任务手动/日志、Server真实OSHI8卷、Redis INFO、Druid真实SQL/Wall、本人站内消息发送/已读、编码预览/生成/组成、本人资料同值保存和合成头像裁剪上传、操作日志详情安全空参数、登录查询与在线会话。API成功与浏览器完成分别记录。Source日志未采集请求/响应体；没有为验证强退其他会话。

浏览器导出/私有文件下载两次未获得工具download artifact（NOT_VERIFIED），实际API已验OOXML/文件字节，不能互相冒充。用户创建/重置密码/注册/导入最终提交仍需用户亲自完成。qa_ui_user_20261006最后只读查询不存在；tab6已重备昵称/部门200/岗位2/空角色/正常/空密码表单，标handoff，无新密码输入或提交。MinIO实例、PDA设备/微信渠道、Redis Cluster、正式MES数据范围、集群调度、部署/生产未验证；Source UserTask依赖业务单据后置，Generator保持后置。整体Phase1仍MIGRATING，不把敏感UI步骤写成通过。

当前本轮后端18081 PID76918/TTY70052：runtime23、backend-34.log；启动命令 zsh -ic 'exec java -jar target/phase1/phase1-runtime-23.jar --server.port=18081 --server.address=127.0.0.1 --spring.profiles.active=dev --spring.config.additional-location=file:target/phase1/application-phase1.yml'。UI15174/Node22/ui-runtime-4.log、Redis16379仍运行供查看；peer18082已停止。原18080 PID55129/15173 PID52881未动。只停止已证明属于本轮的服务，不能按端口杀原服务。私有身份/连接文件0600，路径仅作续接，不在文档输出内容。

回滚本轮未提交差异须先核对新HEAD/用户修改；已应用V10不能改写旧版本或删除表掩盖历史，使用后续迁移。保留旧Schema、target脚本/失败/截图；未经用户授权不删除。README/UI README/platform/SQL指南、来源许可、矩阵/盘点/规划和机器验收同步更新；业务领域没有修改。

## 2026-10-06 前端迁移来源复核与表述纠正

用户询问“前端没有迁移是自己重构的吗”。本次只核对源码与纠正文档，没有修改前端实现、启动/停止服务或提交。实时后端main/4891535f、前端main/d2b1971及原有工作区差异均保留。

源码结论：当前前端是**Source系统页面PORT + 既有Base自写框架**，没有完整沿用Source前端架构。此前“前端已迁入”“完整Source系统页面”等概括不能作为整体前端迁移完成的证明。

| 范围 | 本次直接证据 | 准确结论 |
| --- | --- | --- |
| 用户管理页面 | Source与Base的src/views/system/user/index.vue均包含部门树、原查询字段、同顺序表格、增改/导入导出/授权弹窗；Base保留Options API并将slot-scope/.sync/.native等适配到Vue3 | 该页面确有旧源码迁入，不是用通用CrudPage重新生成；本次抽样不等于全功能逐项验收 |
| 分页及表格工具 | Source与Base的src/components/Pagination/index.vue、RightToolbar/index.vue结构和方法对应，事件/图标/Element Plus语法调整 | 通用组件中有直接PORT |
| 主布局、导航与标签 | Source src/layout/index.vue组合Sidebar/Navbar/TagsView/AppMain/RightPanel/Settings；Base src/App.vue直接实现app-shell/aside/header和简单tags，src/components/SidebarNode.vue为新递归组件，当前无src/layout | 主外壳是Base自写实现，Source布局组件及完整折叠/设置/标签交互未迁入 |
| 路由及状态管理 | Source src/store/modules/app.js/settings.js/tagsView.js/permission.js与layout组件配套；Base使用src/router.ts、lib/dynamicRoutes.ts和stores/auth.ts | Router4/Pinia对应实现由Base重写，不能称Source模块直接搬迁 |
| 登录外观和请求基础 | Base src/views/Login.vue为script setup登录卡片，src/utils/request.ts调用自有lib/request、systemPlugin.ts提供兼容接口 | 沿用部分Source输入/验证码/接口契约，但外观和底层实现有重新实现部分 |

来源清单有119条前端关联项，原来统一标PORT且使用同一通用changes描述；路径和SHA256只能证明所比较的文件及当前字节，不能证明实现继承或功能完整。App.vue和Login.vue现按已证实的Base重写标REDESIGN、Source仅作为比较依据；请求包装器注明Base传输上的Source契约适配。其他未逐项复核项不因此次抽样自动取得完整迁移结论。

整体缺口明确保留：Source Layout/Navbar/Sidebar/TagsView/Settings及配套外观/交互尚未完整迁入。这是产品迁移缺口；Vue3页面适配和之前API/构建测试通过不能替代它。Factory选择、内存凭证和Scope/权限等行为可以在迁入旧结构时保留，不能据此将全新外壳算作原架构迁移。

方法与边界：读取两端Git状态/HEAD，以sed/cat对照上述实际Source/Base文件，Python统计来源条目；Source只读。同步修正UI README、来源清单和有效交接计划，仅文档变化，执行JSON解析和git diff --check，不重跑前次已通过的构建/HTTP/浏览器门禁。本次没有浏览器截图对比或全119项语义审查，不改变既有各包的实际测试记录。


## 2026-10-06 dev/prod配置与临时数据库收尾

### 配置与运行环境

用户要求可运行dev/prod配置、解释异常目录、删除无用代码与过期测试，并建立干净开发库。保留开始时已有dirty工作；期间用户新增提交2467430，本轮未提交/推送/部署，未回退其提交。

默认profile改为dev；application-dev.yml从仓库根导入Git忽略的 `.local/application-dev.yml`，脚本显式加载同一文件。IDE需把Working directory设为仓库根。prod显式激活，数据库/Redis/文件根由部署方提供，迁移默认关闭、验证码强制、密码最少12位；config/application-dev.example.yml与application-prod.example.yml提供完整无凭证模板。prod仅在开发库做本地进程验证，无生产环境或部署结论。

稳定开发系统库mes_base_dev（27表），独立业务库mes_base_dev_business（0表），单Factory=DEV，Codegen只读业务metadata。均从空库开始，运行原V1–V10、不改checksum，不复制phase用户/业务数据。只有通用产品菜单/字典/参数，以及通过空用户表guard创建的admin/显式PLATFORM role/menu授权；平台角色无factoryId，无Factory权限。首管CLI补齐tool前缀授予，使新空库管理员可访问当前Swagger工具，不引入全局通配权限。凭证0600保存在.local/development-admin.json，未写入Git或终端。

16张phase1_legacy_*及phase1_archived_*空归档由scripts/clean-empty-development-archives检查后删除；重复执行移除0表。脚本固定mes_base_dev、先检查所有候选表均为空，非空拒绝；platform_catalog_revision仍被管理员事务guard使用，保留。业务库未建测试探针。HTTP验收产生的登录/操作审计保留为真实开发运行记录，既有QA会话已通过正式在线用户接口撤销。

旧18081 phase JAR已停止，新的正式JAR通过scripts/run-local-backend在18081启动。Redis16379使用原数据迁出target到.local（同版本二进制、私有配置、AOF/RDB），开发使用DB1；UI15174未改源码，代理/health已通过。Java21与标准Maven沿用用户环境。

### 异常目录与退役清单

mes-admin/${project.parent.basedir}保存未展开Maven表达式路径的compiler status；mes-admin/Users保存将绝对路径误作相对路径生成的classes、YAML与licenses。二者无业务源码，已移至target/dev-cleanup归档；当前POM使用maven.multiModuleProjectDirectory，重新verify不再生成。

删除旧简化Platform实现17个Java文件（Controller/Service/Store/Bootstrap/DTO/TokenFilter/Mapper/Rows/条件装配/文件资源策略）及旧DemoConfiguration。保留正式PlatformAdminCli、FileStorage、PlatformAuthorization、PlatformPersistence及Factory Foundation。移除Foundation生产链对旧TokenFilter的引用、旧demo分支；不会通过mes.legacy-test-only或demo恢复另一套正式身份/CRUD系统。

按用户追加要求删除PlatformServiceTest、PlatformHttpTest、TestOnlyCodegenFixture，删除MySqlDevelopmentTest中仅验证旧V1 UUID字段/catalog的迁移方法。两个旧verify-codegen脚本引用退役API、旧单模块classpath、已不存在UI页面，已删除并修正当前指南/计划入口；历史Review不覆盖。保留RuntimeFixture、CodegenAcceptanceTest、真实MySQL业务metadata/隔离可选测试、单/多厂/跨库/跨线程/ownership/权限及Source系统测试。

### 真实数据库清理与恢复

已删除7个本项目mes_base_phase1_*临时schema，列表和SHA256见本次机器验收。每个库在删除前mysqldump --single-transaction --routines --events完整备份并检查成功footer；备份位于.local/backups/20261006-phase，包含CREATE DATABASE/USE，可由授权账号导入恢复。删除前停止旧本项目服务并核对无活动查询；未KILL用户IDE中的Sleep连接。4个旧mes_base_dev_20261005_* schema保留，未访问公司/生产schema或清理其他实例。

### 验证、失败与边界

最终 `mvn -B -ntp verify`（target/dev-cleanup/verify-final-clean.log）80 run / 78 passed / 0 failure / 0 error / 2 skipped；跳过项是显式独立双厂MySQL配置测试，不能对日常dev库造探针。`/usr/bin/python3 scripts/smoke-packaged.py` 4/4：JAR不含H2/Fixture、旧Platform/Demo实现，空装配health200、未认证401。真实dev/prod本地HTTP检查15/15：默认dev导入连接、登录、密码策略5、唯一DEV厂、上下文成功、平台管理员厂业务403、Source用户列表、Swagger工具；prod启动、密码策略12、无验证码登录401；注销后401。切换后新18081登录/单用户核对、在线旧QA会话撤销和15174代理health再通过。真实MySQL核对V1–V10、27系统表、0业务表、phase schema为0；git diff --check通过。未改UI源码、未重复完整浏览器矩阵、未部署prod。

保留的失败记录：初次移动测试时引用本版本不存在的SecurityProperties.DEFAULT_FILTER_ORDER导致编译失败，随后修正且旧实现最终删除；删除旧Mapper后当前MySQL ownership负例仍引用旧Mapper导致一次编译失败，已改用独立RuntimeFixture平台探针；初始账号随机hex密码被MySQL MEDIUM策略拒绝，schema已建但账号未建，补齐复杂度后创建成功。首次HTTP检查误用旧/api/platform/me得到404、误期望缺验证码400但实际401；按当前Controller契约修正检查后15/15，不将脚本错误归因于正式系统失败。

配置/代码恢复用Git差异及target/dev-cleanup/baseline/retired归档；数据库恢复用上述私有完整备份。旧phase配置仍作为历史本地证据保留，不再作为正常启动入口，不能复用已删除schema的旧账号/Token继续操作。

本次文档更新：README的dev/prod/Redis启动方式；platform.md的稳定配置、库和退役行为及本日追加；codegen.md替换过期验收入口；next-stage-plan.md新增本日开发环境交接、保留旧快照并标为历史；本节报告及docs/reviews/2026-10-06-development-cleanup-verification.json记录命令/真实库清单/结果与边界。未改MES业务Domain。

## 2026-10-06 UI 第一轮：Modern Industrial

本工作包 UI FIRST，Phase 1 后端迁移仍 **MIGRATING**。改造相邻 mes-base-ui 当前已迁页面；本 UI 工作包没有修改 Java/Mapper/SQL/Flyway/API/权限模型，没有新增正式 MES Domain、生产指标或依赖，没有提交、推送、部署。原用户工作及其他工作包的后端调整保留。

建立统一 CSS token、浅色工作区/深蓝灰导航、Factory 上下文、Tags、PageHeader/Filter/Toolbar/Table/Dialog/Tree 和 StatusTag。Login 为克制品牌区 + 表单；Home 只显示真实用户/工厂/授权模块。System、Monitor、Quartz、自动编码、OpenAPI、Swagger 接入公共主题；个人资料/文件/消息继承主题。OpenAPI 授权/重置 Secret 移至逐项保留权限的“更多”。头像/Quill/Crontab 内部专项交互未重写。

当前改造前源码快照包含用户 dirty/untracked：31 个改变的页面，v-model、表格标签/prop、权限 code 集合保持；28 个业务 script 原样，另三项为 Login 错误加载/反馈、Home 实际导航、Redis 图表展示与尺寸生命周期。20 个 API 文件及 auth/router/request/menu-query/dynamicRoutes 逐字节相同；Source User/Role/Dept/Menu/Job/AutoCode 只读核对。此结论不等于全 Source Product 迁移已完成。

真实浏览器 1440 已查看 Home/User/Role/Menu/Dept/Post/Dict/Config/Notice、日志/在线/Quartz/Server/Redis/Druid、OpenAPI/AutoCode/Swagger；实际 User 查询/重置/显隐查询/空态/分页、部门必填校验/新增/编辑/停用、删除确认取消、菜单导航、OpenAPI 更多与真实授权目录取消。未提交新凭证/授权、删除、运行任务或强退别人会话。UI 操作当时使用旧 phase 环境；合成根部门 UI视觉验收20261006已编辑（99、停用）属于该历史环境，不能宣称当前干净开发库有此记录。独立 dev/prod 清理工作已切换后端、退役旧数据，UI 工作没有阻止或恢复该清理。

Node22 门禁：`npm run test` 7 文件/27 通过（既有21 + 本轮6），`npm run build` vue-tsc/Vite 通过；已有大 chunk 提示。早期 Tags command 隐式 any 已修复；快照测试曾被额外发现一次的28项数字不作为有效覆盖，改 `.snapshot` 后以27项为准。Login 真实无效登录/密码清理和1920/1280/560布局已验证。

**UI_BACKEND_BLOCKER：** 当前15174代理 health UP，但18081真实登录500；`.local/logs/backend-console.log` 10:50记录 requestId=c95204f2-9c06-45da-9d81-27043e01fc7b，NoClassDefFoundError/ClassNotFoundException: org.apache.ibatis.reflection.ExceptionUtil。当前 JAR 与本地配置已存在；已向用户询问仅重启当前18081运行实例以继续验收，未改后端源码/配置/数据库，也未自行停止其他工作包实例。后台1920/1280、折叠菜单、Tags操作、显隐列/树折叠和末轮修正截图仍待闭环；不把旧环境/单元/构建替代当前环境证明。

规范：相邻 `mes-base-ui/docs/UI_DESIGN_SYSTEM.md` 与前端 AGENTS；10项交付、截图、feature-preservation JSON 和门禁日志在 `/Users/user/work/lixin/mes-base-ui/docs/acceptance/ui-round1/`。敏感UI提交、受限账号浏览器负例、外部设备/部署仍按独立验收边界。整体 Phase 1 状态不变。


### 2026-10-06 追加：实际profile直接配置与失效属性修正

用户明确不喜欢环境变量占位符，并要求直接填写标准配置文件。此前为了隔离Git凭证设计的config.import中转层增加入口，已按用户偏好撤销。最终实际值直接位于mes-admin/src/main/resources/application-dev.yml和application-prod.yml，默认dev、prod以spring.profiles.active激活，不存在额外映射或import层。公共application.yml的环境占位符、重复默认端口/连接和无用空JDBC字段移除；config只保留两个无密码example供新机器复制。config里的实际dev/prod副本、.local旧dev副本和旧runtime双厂example删除。

两个含真实连接的profile通过用户批准的git rm --cached取消跟踪，仅索引删除、本地文件保留，.gitignore显式忽略且文件0600；这两项索引删除已暂存，本轮未提交/推送。正常resources打包会把本机配置带入本地JAR，本轮没有发布该JAR。运行脚本不再额外读取MES_LOCAL_CONFIG/additional-location，dev18081已重启为标准profile启动。

用户指定prod暂用同一测试MySQL实例但另建独立库。本轮创建mes_base_prod、mes_base_prod_business及独立mes_base_prod账号，从空库运行V1–V10、创建首个显式PLATFORM admin、移除16张空归档；最终27系统表/0业务表，与dev账号/库/PROD Factory/Redis DB2/文件目录隔离。初始凭证.local/production-test-admin.json，dev不重置。prod测试保持验证码开启和12位密码最小长度；没有真实生产库或生产部署。

用户反馈server.error配置报错。直接读取当前JAR中Spring Boot4.1.1的spring-configuration-metadata，旧server.error.include-message/include-stacktrace/include-binding-errors自4.0被标记deprecation.level=error，replacement为spring.web.error对应键，已修正。server.address/port仍有效，留在实际profile中。公共YAML按依赖metadata审查，未发现其他level=error键。自有mes/ruoyi/open-api/druid配置按真实消费者保留，不将IDE未知提示误判为无用业务字段。

最终mvn -B -ntp verify（target/direct-yaml/verify-standard-profiles.log）80 run /78 passed /2 skipped /0失败，packaged smoke4/4；实际标准dev/prod资源自动加载检查21/21。检查包含真实prod测试账号的验证码登录、密码策略12、唯一PROD厂；双向跨环境Token拒绝、注销，以及dev原有Source/Swagger/Factory403行为。验证码测试只读本次测试生成的自有prod Redis challenge（不关闭验证码策略、不输出验证码/凭证）。两次QA脚本读取了错误captcha字段/按UTF8误解读Redis JDK序列化缓存，均为测试脚本失败，按实际captchaOnOff/JDK String契约修正后通过；非应用配置失败。归档清理首次直接运行遇到本机PATH中的python3架构错误，已改用本机可用/usr/bin/python3并修正launcher，prod清理与幂等重复执行通过。

README启动章节、platform.md配置及本日追加、codegen.md实际profile读取说明、next-stage-plan.md交接均已更新；机器证据新增configurationFollowUp，保留上一阶段快照。没有更改MES Domain、前端或用户dev业务数据。回滚可恢复两个无密码Git入口或target/direct-yaml/baseline，但不要恢复已废弃import路径作为当前启动方式。prod额外库的初始化及授权不等于生产发布。

## 2026-10-06 多工厂分支实现工作包

SOURCE_PRODUCT_PORT 来源 feat/multi-factory-isolation，后端提交 bdc299dade855177a3e275199ffde6e72310e393。新增 V11–V13：mf_factory/mf_datasource、mes_master_sync及管理/分析菜单。原 V1–V10 checksum 保留。DEV/PROD 本项目测试连接分别导入对应系统库，业务密码 AES-256-GCM 加密，独立密钥直接位于私有 profile；不迁 Source 客户连接。工厂目录从 YAML 改为数据库，平台库仍由 YAML 引导启动。

数据源/工厂接口与 Vue3 页面已接入，显式 PLATFORM 管理权限、原分页语义与 revision 冲突检查。列表不返回密码/密文；绑定数据源不能停用/修改/删除；工厂删除检查角色、任务、同步引用。业务池按数据库目录延迟加载，校验物理库归属及 revision，失效不回退默认库。DDL 只导出表结构，缓存位于平台私有目录。菜单迁移不自动修改角色授权；现有管理员缺新权限时返回403。工厂导航按当前 FACTORY 角色菜单读取。

mes_master_sync 位于平台系统库，SyncCoordinator 按 factory_id + sync_key 条件抢占；活动、结束、失败、恢复均校验 run_token / RUNNING。恢复依据数据库活动时间和旧令牌并检查更新行数；后续执行器须分批检查 ownership、更新活动时间，旧令牌失效后停止副作用。AGENTS §8.1 已同步强制约束；未引入 ERP/WMS/外部 MES 执行器、客户连接或远程调用。

生产监控及人员分析 Java/XML/Vue 已迁入 mes-mes，业务 Mapper 显式 BUSINESS 登记，用户展示使用独立平台批量只读查询，禁止跨库 JOIN/写事务。当前 mes.analytics.enabled=false；pro_workorder/pro_reporting_work/qc_defective 等当前表 Schema/状态字典来源已询问用户。未推测建业务表、未执行真实分析 SQL，管理/分析授权页面及窄屏浏览器验收未完成。

### 实际验证

- `mvn -B -ntp verify -Dmes.test.managed.config=mes-admin/src/main/resources/application-dev.yml -Dmes.test.managed.factory=DEV`：target/migration-verify-live.log，BUILD SUCCESS，93项/91通过/2显式双厂fixture跳过。真实 MySQL 管理/同步20项，包括双独立JVM同流一个ACQUIRED/一个BUSY、跨厂同key独立、恢复/旧令牌拒绝、分页及无数据DDL。分析 Source 服务12项单元通过，不是业务SQL验收。
- UI `npm run test`：7文件/27通过；`npm run build`：vue-tsc/Vite通过，既有chunk提示。日志 target/migration-ui-test.log、migration-ui-build.log。
- `/usr/bin/python3 scripts/smoke-packaged.py`：4/4，target/migration-packaged-smoke.log。
- prod测试库基础检查9项通过（target/management-integration/prod.log）；dev HTTP8项通过（http-result.json），含登录、DB工厂目录/选厂、401/403、分析关闭404，未证明授权管理CRUD页面操作。

原本项目18081旧进程和本轮临时18082已停止，scripts/run-local-backend在18081运行新包（本轮PID42637），health200；.local/services.json已更新。另一个既有后端进程保持，prod没有持续启动或部署。未提交/推送。

工具失败修正：测试helper的Druid Wall拒绝Flyway用户变量检查，改为与正式启动一致的直接迁移DataSource，未关闭Wall；helper提取旧jar类出现byIds NoSuchMethodError，重新提取最新包后prod9项通过。文档脚本两次遇到输入编码错误，改用apply_patch完成；没有据此修改业务配置或放宽校验。

### 2026-10-06 用户指定测试管理员密码

按用户明确授权，对 mes_base_dev / mes_base_prod 中已有 admin 做一次性密码重置，未重复创建账号、未改变角色/菜单授权。使用当前 BCrypt 依赖，参数化 SQL、行锁和 auth_revision 条件更新；提交后回读，两库均验证 BCrypt 匹配及 auth_revision 加一，旧会话因此失效。私有凭证记录同步更新并保持0600，本文不记录密码。prod 本次指定密码短于全局最小12位，作为明确授权的一次性维护，不降低后续创建/重置的全局策略。执行辅助程序位于自有 target/admin-reset，无业务代码或配置修改。

dev HTTP登录验证时18081连接被拒绝，未取得本轮实际登录证明；prod亦未启动登录验收。数据库凭证验证已完成，服务运行状态以上述最新检查为准，不能沿用上一节health200作为当前在线证据。

## 2026-10-06 中心库 dev 数据导入：结构核对

用户明确要求读取 ktg-mes 中心库并导入 Base dev，并选择保留两个源工厂、另配独立开发业务库。本次授权是本机开发数据导入例外，不改变产品迁移禁止携带客户数据/真实连接的常规规则；源数据仅进入用户指定 dev 与本机私有备份，不进入 Git/产品 seed/prod。源库只读，prod未修改。实际目标名称为 mes_base_dev，而非连字符 mes-base-dev。

读取 feat/multi-factory-isolation 的 dev 配置定位 mes_master，未连接其 ERP/HR/WMS 配置。真实 information_schema 比对：源39表、目标30表、共同20表。结构与数量证据 target/central-import/comparison.json，冲突聚合 conflicts.json；不输出用户、连接凭据或业务行。

结论：RuoYi 基础 sys_* 字段多数兼容，可以使用显式列 INSERT/批处理 SQL 做适配导入，但不能将整库 mysqldump 原样恢复至 Base，也不能通用 REPLACE/INSERT IGNORE 掩盖冲突。原因：

- 部门、岗位、公告与关联表基础字段一致；已有目标角色/用户使关联 ID 必须审查。字典类型/数据结构一致，但15个字典类型ID、44个字典数据ID冲突，应按dict_type/字典值合并。
- 用户存在同名admin，须保留本地密码与显式平台权限；源warehouse_code未进入当前Base契约，Base新增auth_revision/pwd_update_date等。6条头像引用超过原目标100字符，V14按真实源metadata扩为150，避免截断。
- sys_role 的源factory_id是BIGINT NOT NULL，Base为字符串且有kind/scope_key约束；直接默认PLATFORM再写factory_id会违反角色范围检查。源34角色中1个不属于真实源工厂，不能照搬旧userId=1/魔法factoryId=0超级管理员语义。
- 菜单存在205个ID冲突，源带factory_id，Base带route_name/legacy_id；源630菜单包含未迁业务和外部页面。保留Base已实现菜单，只按明确分析权限映射FACTORY授权，不把厂角色转为平台权限。
- mf_factory的ID类型不同，源有erp_plant_code，Base需要enterprise_id/revision；源mf_datasource有真实连接/密文，不能复制到开发工厂。源mes_master_sync只有3条无factory_id/updated_at/id旧结构，与分支源码和Base现行按factory+sync_key协调不同，历史水位/运行状态不能直接照搬。
- 源sys_job的invoke_target/调度状态及qrtz_*属于旧执行环境，不能把旧任务复制成可运行Base任务；大量操作/登录/任务日志不作为干净开发库存。sys_permission/sys_role_permission移动权限、外部erp_item_lot、请求表与代码生成元数据不在当前已实现契约中。

目标库先完整mysqldump备份，再在单事务内导入兼容数据及ID/范围映射。私有备份目录.local/backups/20261006-central-import（0700），target-before.sql、选定源数据快照与SHA256为0600。备份保留现有账号密码，仅本机访问；正式文档不包含业务行或密码。

### 导入结果与真实数据库验收

数据事务已提交：新增用户1749（原admin不变）、FACTORY角色33、用户角色关联1873、角色部门关联3、部门83、岗位4、公告1、字典类型35、字典数据134、普通外观配置1、工厂2。最终用户1750、角色34、字典类型50、字典数据178、用户角色关联1874。复用15个既有字典类型和41个既有字典值；保留Base额外3个字典值及4处既有展示标签差异，不覆盖现有契约。尚未迁入用户warehouse_code契约，该原始字段保留私有快照，不伪造已实现仓库权限。

工厂ID按显式字符串保留为1/2，归现有DEV的enterprise_id，状态DISABLED、datasource_id=NULL；现有DEV保持。没有导入原业务连接/密文，独立开发业务库的配置与启用仍待完成。源一个无真实工厂的角色、2条相关/既有admin用户角色关联未迁入；不引入魔法0/1管理员。源同名admin及其岗位/平台权限保留当前Base版本，指定密码未覆盖。

源554条role-menu授权未原样复制：其不对应当前已实现的工厂分析授权；中心库对4个分析菜单的显式角色授权实际为0。Base不复制旧超级管理员特判，不通过来源账号ID自动赋权。源菜单/移动权限/外部表/任务/Quartz状态/历史日志/同步水位、初始化密码/注册开关等未导入；不能把这份受控兼容数据导入表述为原系统整库功能恢复。

单独应用V14把avatar扩大到实际源150字符，恢复6条长头像引用并逐条验证，不截断字段。Flyway此时发现本轮开始前dev真实版本为12（并非上方早期交接声称13），已按原checksum应用V13/V14；最新实际dev版本14。prod本轮未改，不能用dev版本推定prod同步完成。

真实验收29项通过：数量、原admin BCrypt匹配/平台授权、工厂不绑定源库、无用户角色/用户部门/工厂角色孤儿、未复制任务/同步状态、V14及6条头像、所有源字典type/value均可找到。证据为 docs/reviews/2026-10-06-central-library-import-verification.json，仅含schema/计数/状态，不含个人数据或凭证。`mvn -B -ntp verify` BUILD SUCCESS（target/central-import/verify.log），93项/90通过/3显式真实库fixture跳过；打包smoke4/4（smoke.log）。未改业务Java/前端，未启动服务、未做HTTP/浏览器验收，未提交/推送/部署。

失败保留：源快照首次因LocalDateTime序列化模块未登记而中止，发生在目标数据写入前，修正时间模块后重试。两次导入在最后关联检查遇到MySQL1267排序规则混用（sys_role.factory_id=utf8mb4_unicode_ci，mf_factory.factory_id=utf8mb4_0900_ai_ci），数据事务均回滚；校验SQL改为显式BINARY按Core字符串身份精确比较后通过，未关闭约束、忽略错误或截断数据。最终导入使用有界批量PreparedStatement；验证helper曾遇到javac参数及Map.of数量错误，修正后29项通过。每次重试检查目标仍为原基线才复用备份。

回滚须在停止当前dev写入、确认本次之后无需要保留的新数据后，受控恢复私有target-before.sql（备份时dev V12）；不能直接覆盖后续开发数据。源库只读未修改，prod与源外部系统未访问。

### 2026-10-06 日志页面样例：每类最近100条

用户说明只需少量真实数据查看页面，明确授权操作/登录/任务日志各100条。本节覆盖前文“不导入历史日志”的本轮范围，未恢复全部历史记录。源mes_master按日志时间DESC、源主键DESC各LIMIT100，只读；向mes_base_dev追加300条并保留原记录。操作日志0→100、登录日志17→117、任务日志0→100，事务内检查每表增加100，sys_job数量保持不变。沿用源时间、状态、人员及模块/任务等展示信息；不采集源请求体、响应体、原始异常、任务调用参数，展示摘要标记Source history sample。工厂ID明确映射1/2，新增日志使用目标自动生成ID，不覆盖旧记录，不恢复任务或推进同步水位。

目标库导入前完整备份在.local/backups/20261006-log-samples/target-before.sql；inserted-id-map.json保存300条源/目标ID映射，可按本次目标ID精确回滚，避免覆盖后续开发数据。目录0700、文件0600，SHA256已记录。机器证据追加现有central-library-import-verification.json的logSamples，只包含计数/状态，不含日志内容或凭证。无业务代码/Schema/配置修改，未改prod、源库或外部系统；未做浏览器显示验收，不重复构建无变化的业务代码。

### 2026-10-06 登录态刷新恢复

用户明确要求有效Token在刷新后保持登录，本节替代此前仅内存保存Token的约定。UI新增sessionStorage保存Token/过期时间/选厂偏好，路由进入前重新读取服务端身份、权限、菜单及启用工厂，再恢复仍有效的选厂与原动态页面；不持久化密码或授权数据。过期、401及退出清理，网络失败可重试。根和UI AGENTS同步此约定，无后端业务代码或Schema修改。

UI Node22执行npm run test（10文件51测试）与npm run build通过。实际本机dev浏览器验证/system/config整页刷新保留登录、原页面和DEV选厂，退出后刷新回登录页。异常与过期场景为单元测试证据；prod未启动验收。详细回执与截图在mes-base-ui/docs/acceptance/session-restore/README.md及refresh-preserved.png，构建日志在UI target/session-restore。本次启动自有dev服务：后端18081 health200，UI15174；验证会话已退出。未提交、推送或部署。

### 2026-10-06 验证码条件请求与管理菜单可见性

原登录/注册页面每次加载都请求/api/captchaImage读取验证码、注册开关及密码策略；dev验证码关闭时接口不生成图片，但仍有请求。新增PUBLIC /api/login-options返回显式LoginOptions VO，CaptchaService集中读取同一服务端开关；UI先读配置，只有captchaOnOff=true才请求图片。登录成功后不再额外刷新验证码，失败后重新读开关。原captchaImage接口兼容保留，后端登录验证码校验仍以服务端配置为准。

真实dev数据库确认工厂管理和数据源管理两个菜单已由V13创建，前后端模块均在当前源码与构建内。不可见原因是当前admin平台角色无对应sys_role_menu授权，重启不能补权限。按用户本轮明确授权，事务内补齐system:factory:*与system:datasource:*共10项现有菜单/操作授权，无重复；prod未改。私有精确回滚记录.local/backups/20261006-managed-menu-grants/inserted-grants.json，目录0700/文件0600。不新增默认超级管理员绕过，不修改Flyway历史脚本或赋予工厂业务授权。

mvn -B -ntp verify BUILD SUCCESS：95项，92通过，3项真实库fixture显式跳过；新增2项验证码配置测试。scripts/smoke-packaged.py 4/4。UI Node22 npm run test 11文件54测试通过，npm run build通过。重启本次自有dev后端18081（PID50110），实际/api/login-options返回captchaOnOff=false。真实浏览器登录页无验证码，登录后显示两个菜单；工厂页面加载3条、DEV编辑弹窗打开并取消；数据源页面加载1条并显示当前节点未加载状态。未执行创建/更新/删除、DDL访问或业务连接验收；prod未启动/部署。

截图在mes-base-ui/docs/acceptance/managed-menus/factory.png；验证码关闭时不请求图片与开启时请求图片的分支由UI测试证明，本轮未抓取浏览器网络面板，不把单元请求证据表述为浏览器网络抓包。测试/构建/打包日志在target/login-options以及UI target/session-restore/login-options-*.log。业务代码修改仅验证码设置契约与前端条件请求；管理模块本轮未改。


## 2026-10-06 响应投影及隐式基础设施审查

本次为当前 main 未提交工作区的静态源码审查；未修改业务代码，未运行 Maven、HTTP、数据库或 UI 验收。检索 docs/ 内容及目录，doc/、knowledge/ 不存在；本报告已有分层与迁移架构主题，因此追加，保留历史证据。历史测试通过记录不作为本次结论的运行证明。

### SystemResponseAdvice 的用途与问题

实际链路：SysUserController.list/getInfo → ISysUserService 返回实体 → TableDataInfo/AjaxResult 包装 → SystemResponseAdvice.beforeBodyWrite → SystemViews.project → HTTP 序列化。源码注释明确投影排除 password/salt/persistence params；已识别实体转白名单 record，部分 ID 转字符串。因此用途是统一响应投影；结合迁移结构推断，这是为了少改源产品 Controller/Service 的兼容补丁，无法从当前代码证明作者当时的完整动机。

- SystemResponseAdvice（mes-framework/src/main/java/dev/mesbase/framework/web/SystemResponseAdvice.java:3-12）按整个 dev.mesbase.web.controller 包生效，supports 恒为 true，不只作用于 system；还直接修改 TableDataInfo.rows。
- SystemViews（mes-system/src/main/java/dev/mesbase/system/dto/SystemViews.java:9-60）以 Object + instanceof 分支承担各系统模块投影；Map/List 递归转换，但未知类型原样返回。未遍历任意 record/POJO/数组；不能将其当作完整 Entity 隔离或敏感字段安全边界，也不能据此断言当前所有接口已经泄密。
- project 中任意递归遇到的 Long 都转字符串，不区分 ID 与数量。TableDataInfo.total 不经该递归，而已识别 VO 的 Long 字段也不会再递归；类型语义依赖容器和命中分支。SysUserView 的 dept/roles、树的 children 使用 Object，削弱编译期契约。
- AjaxResult 继承 Map，因此经 project 后成为 LinkedHashMap。OpenApiResponseAdvice 另要求 body instanceof AjaxResult；两者是否作用于同一接口、执行顺序及实际结果未验证，不宣称已发生故障。

建议：逐用例在 Service 输出明确 VO，Controller 只做协议包装；ID 精度转换按字段契约表达。待所有响应类型、敏感字段和分页契约覆盖后移除此 Advice；不能直接删除而重新暴露实体。

### 其他需要治理的设计

| 位置 | 当前源码事实 | 判断与建议 |
| --- | --- | --- |
| mes-common/.../core/domain/AjaxResult.java:133-147 | error 返回签名仍是 AjaxResult，实际抛 ServiceException；data 参数不参与处理 | 为消除 HTTP200 错误而保留旧调用形式的隐式兼容；改为 Service 显式业务异常，成功包装不负责失败控制流。当前并非仍返回 HTTP200 错误。 |
| mes-admin/.../controller/system/SysUserController.java:104-174 | 详情编排用户/角色/岗位；新增校验唯一性、编码密码；请求 Form 在 Controller 转 Entity；角色过滤还残留 false ? roles : ... | 业务编排仍留在 Controller；迁入既有代码后应逐用例收回 Service，不靠 Advice 证明分层完成。恒假分支应局部清理。 |
| mes-framework/.../persistence/SystemUnitAspect.java:16 与 SystemUnit.java:22-66 | 硬编码 Service 包/类开启平台事务，排除一个 run 方法；SystemUnit 用 ThreadLocal 和代理共享 Session | ownership 和共享事务有实际隔离价值，但事务边界依赖包名，移动 Service 可漏织入，查询也进入 transaction。保留保护机制，统一显式用例事务入口及模块登记。 |
| mes-admin/.../server/AnalyticsConfiguration.java:20-48 | 配置类内又实现 Mapper 动态代理、ThreadLocal、业务事务和具体 Service 切点 | 与 SystemUnit 重复机制但数据库 scope 不同，不能直接混用。让配置只装配，将共用机制收回受控持久化基础设施，保持平台/业务跨库事务拒绝。 |

合理机制需区别对待：FoundationErrorAdvice 做安全 HTTP 异常映射；OpenApiResponseAdvice 做外部协议装饰；OwnedSqlSession 逐次校验注册 Mapper、线程归属、关闭状态及执行权。这些职责不等同于隐藏 Entity→VO 映射，不因出现 Advice/代理/ThreadLocal 就删除。此审查覆盖响应与相关装配、事务、用户入口，不是全系统穷尽审计。

### 本次规则更新与证据边界

AGENTS.md 新增「5.1 Java 导入与类型引用」：所有 Java 生产/测试源码及生成模板显式 import、简单类名；仅实际同名冲突允许全限定名，禁止通配符绕过。包/import 声明与框架类名字符串不属于类型引用。本次未全量重排已有 Java imports，未改 UI 文件、未提交/推送/部署。验证为源码读取、rg 检索及本次文档差异检查；上述风险未作运行复现。


## 2026-10-06 显式 Controller 响应契约迁移

本节承接上节审查；用户本轮明确改为 Controller Web 边界做 Result 转换，因此取代上节“Service 返回 HTTP VO”的治理建议。Service/Domain 返回模型不改，不修改 SystemUnit/SystemUnitAspect/AnalyticsConfiguration/FactoryContext/Mapper ownership/异常体系。现有工作区原件在 target/response-migration/baseline，保护文件 SHA256 留存供完成后对照。迁移阶段不调用 Codegen。

### 修改前依赖清单（静态源码）

Advice 覆盖 dev.mesbase.web.controller 整包；仅有 Advice 直接调用 SystemViews，下面的接口是间接依赖。分页依赖 rows 的投影；total 没有被原 Advice 转成字符串。

| Controller / 路由族 | 依赖的响应与递归结构 | 直接移除的影响 |
| --- | --- | --- |
| SysUserController /api/system/user | list、详情 data/user、roles/posts、授权角色；roleIds/postIds | 用户实体含 password/salt；部门/角色嵌套及各 ID 转换丢失，BaseEntity 内部状态重新暴露 |
| SystemSessionController /api/system/info | user | 登录用户实体重新暴露；密码/salt、部门与角色 ID 契约受影响 |
| SysProfileController /api/system/user/profile | data | 同用户实体；roleGroup/postGroup 字符串不受影响 |
| SysRoleController /api/system/role | list、详情、optionselect、allocatedList/unallocatedList | SysRole、嵌套数组 menuIds/deptIds，以及授权用户 SysUser 的白名单/ID 丢失 |
| SysMenuController /api/system/menu | 列表/详情、treeselect、roleMenuTreeselect 的 checkedKeys/menus | 菜单/树 children 递归和 ID 字符串丢失 |
| SysDeptController /api/system/dept | 列表/排除子部门/详情、treeselect、roleDeptTreeselect 的 checkedKeys/depts | 部门/树 children 递归和 ID 字符串丢失 |
| SysPostController /api/system/post | 分页、详情、optionselect | 岗位 ID 和 BaseEntity 白名单丢失 |
| SysDictDataController /api/system/dict/data | 分页、详情、type | dictCode ID 变数值；dictSort 本来是 VO 内数值，应保持 |
| SysDictTypeController /api/system/dict/type | 分页、详情、optionselect | dictId ID 和内部状态隔离丢失 |
| SysConfigController /api/system/config | 分页、详情 | configId ID 和内部状态隔离丢失；configKey 字符串不受影响 |
| SysNoticeController /api/system/notice | 分页、详情 | noticeId ID 和内部状态隔离丢失 |
| SysAutoCodeRuleController /api/system/autocode/rule | 分页、详情 | ruleId ID 和内部状态隔离丢失；长度等数值保持 |
| SysAutoCodePartController /api/system/autocode/part | 分页、详情 | partId/ruleId ID 和内部状态隔离丢失；索引/步长等数值保持 |
| SysMessageController /api/system/message | 分页、详情 | messageId/senderId/recipientId ID 和内部状态隔离丢失 |
| SystemInboxController /api/system/inbox、/api/mobile/system/message | 分页 rows | 同消息；SSE 是独立事件协议，未被 project 遍历 |
| SystemFileController /api/platform/files | 分页、裸 metadata/upload | attachmentId/sourceDocId/ownerId ID 和内部状态隔离丢失；fileSize 保留 BigDecimal；download bytes 不变 |
| SysOperlogController /api/monitor/operlog | 分页 rows | operId ID 和白名单丢失；costTime 保持 Long |
| SysLogininforController /api/monitor/logininfor | 分页 rows | infoId ID 和白名单丢失 |
| CacheController /api/monitor/cache | Map 的 dbSize | 原 Long 计数被错误转成字符串；本轮明确改回 Long 数值 |

SystemViews 的 SysUserRole/SysRoleMenu/SysRoleDept/SysUserPost/SysUserRecipientQuery 分支没有当前 HTTP 输出入口；用户收件人查询 Service 实际返回 SysUser。无需为未使用分支创建响应类。

无投影依赖的已检查接口：SystemCaptcha/SysRegister/SysAutoCode 返回已有专用模型或基本类型；ManagedFactory/ManagedDatasource 返回独立 record；OpenApiClient 返回已定义 DTO，创建/重置凭证是 no-store 专用协议；SystemOnline/Druid 返回已有 record；Server 返回监控模型；Swagger 返回静态协议。project 不遍历这些 POJO/record 内部字段。Quartz、MES、generator Controller 不在 Advice 的 basePackages，沿用现有显式 DTO/VO，不扩散调整。字符串/byte[]/void/错误响应也不是实体投影依赖。

迁移保持原白名单字段、日期格式和包装，不重新设计查询、权限、写入/导入导出业务。文件 ID、FactoryId、请求/执行 ID 已为 String，不额外转换。各 Result 以原 Sys*View 字段为基线，用户/角色/部门/树的内部 Object 改为对应 Result/List 的静态类型。


### 最终分级方案（2026-10-06，新约束取代全量 Result 方案）

用户在初版实施中暂停并提供新约束，后明确授权继续：普通安全后台 CRUD 不强制复制 Result/VO；敏感、复杂、字段裁剪/转换或外部接口才严格隔离。本节取代前述“一律显式 VO”方案。上一轮已创建 16 组响应模型；最终撤掉 SysConfig、SysNotice 两组，保留 14 组均有下表所述具体理由，而非以“分层完整”作为理由。迁移债务与新增规范明确区分。

曾考虑撤掉字典/规则/日志模型，随后 Schema 与前端源码证明这会引入精度风险：V2 的 dict_id/dict_code/oper_id/info_id 和 V6 的 rule_id 是 BIGINT；前端 src/lib/request.ts 用 JSON.parse，超过 2^53-1 会丢 ID 精度。因此恢复这些接口的显式字符串 ID 契约。中间 9 组的方案及测试属于过渡步骤，不是交付结果。参数 config_id 和公告 notice_id 在 V2 是 INT（INT(5)/INT(4) 为显示宽度），最大值在 JS 安全整数范围，允许直接 Entity；尚未核对真实数据库是否偏离仓库迁移 Schema。

| 最终模型 / 接口 | 必须转换的具体理由 |
| --- | --- |
| SysUserResult / SysUserAssembler；用户列表/详情/授权列表、session info、个人资料 | password/salt、内部状态白名单隔离；嵌套角色/部门类型明确，BIGINT ID 字符串 |
| SysRoleResult、SysDeptResult、SysPostResult 及对应 Assembler | 原实体 roleSort/orderNum/postSort 是 String，现有 el-input-number 需要数值；关联用户表单/选项和 ID 契约保持一致 |
| SysMenuResult / Assembler、TreeResult / TreeAssembler | 菜单/树父子 ID、角色 checkedKeys 的字符串契约一致；明确 children 类型 |
| SysDictDataResult、SysDictTypeResult 及对应 Assembler | BIGINT 主键维持字符串，dictSort 仍数值；无全局 Long 转换 |
| SysAutoCodeRuleResult / Assembler | BIGINT ruleId 字符串精度；maxLength 数值 |
| SysAutoCodePartResult / Assembler | 不暴露内部 seriaNowNo 运行计数；partId/ruleId 字符串，索引/长度/步长数值 |
| SysMessageResult / Assembler；消息、inbox、mobile 别名 | 现有移动接口独立响应白名单，去除 deletedFlag/attr*；sender/recipient/message ID 字符串 |
| SysAttachmentResult / Assembler；文件列表/upload/metadata | 私有资源元数据白名单，去除 attr* 等内部状态；attachment/sourceDoc/owner ID 字符串；fileSize 为 BigDecimal |
| SysOperLogResult、SysLogininforResult 及对应 Assembler | BIGINT 日志 ID 字符串精度；request/actor 原字符串、costTime 数值，字段继续原白名单 |
| SysConfig、SysNotice 直接 Entity；列表/详情 | 安全标准后台 CRUD，不新增 Result；保留原字段/日期/分页结构，INT schema ID 从字符串明确恢复数值 |

BaseEntity.getParams/getSearchValue 增加 JsonIgnore，仅排除框架内部查询状态，不改变 Java getter/setter、MyBatis 或 Service 使用。这不是用户密码等敏感字段的主要保护；SysUser 的敏感字段由 Result 本身不存在这些字段保证。Converter 不访问 Service/Mapper/数据库、不处理权限或事务。PageResultMapper 只复用 PageHelper 先取总数再映射 rows，不猜测业务类型。

生产 SystemResponseAdvice 和 SystemViews 已删除，源码及最终 JAR 均无旧类/调用残留；测试源码保留 LegacySystemViews 冻结快照作旧响应 oracle，不注册 Spring、不进生产包。44 个 GET 路由（含树/组合响应/移动别名/裸文件元数据）无 Advice 的 MockMvc 检查、文件上传及缓存计数共 46 项；Result 对照/直接 Entity/分页/数值语义共 18 项。测试为合成数据与 mocked Services，不是 live 数据库或浏览器验收。

### 正式规则与最终验证回执

AGENTS.md 第4/5节对齐 Controller Web 转换与分级 Entity 规则；13.1 CRUD Development、13.2 Domain Business、13.3 Response Contract、13.4 Global Response Rule、13.5 Migration Compatibility 分别明确 Codegen 默认开发入口、非 CRUD Service、后台宽松/敏感及外部严格、禁止全局猜测、迁移优先兼容。保留5.1显式 import 与同名冲突例外。当前迁移未调用 Codegen，未新增业务 CRUD、未迁业务 Service。

最终命令 `zsh -ic 'mvn -B -ntp verify'` BUILD SUCCESS：159项，156通过、3跳过，失败/错误0；含上述64项新增响应测试。`zsh -ic '/usr/bin/python3 scripts/smoke-packaged.py'` 4/4，使用独立临时进程；未重启用户现有dev服务。首次HTTP fixture错误来自模拟List不可修改、字典查询返回错误实体类型，已修正fixture；未因此改变业务逻辑。最终源码/包内容检查：旧生产类/调用0；权限注解与本轮前一致；SystemUnit/SystemUnitAspect/AnalyticsConfiguration/FactoryContext SHA256不变。Mapper ownership/持久化/统一异常代码未修改。`git diff --check` 通过。

唯一有意 HTTP 差异：参数/公告的 configId/noticeId 从字符串恢复为数值；Redis dbSize 从错误的字符串恢复为 Long 数值。其余已识别响应模型的原字段名、包装、BIGINT字符串、日期、树、分页total以及空值保持。SysConfig/SysNotice 不新增 params/searchValue；日志未增加新字段。现有独立 DTO/record 接口保持原样。不宣称所有后台 Entity 自动安全，新增字段与新接口仍需分级审查。

证据 JSON：docs/reviews/2026-10-06-response-contract-verification.json；原始日志 target/response-migration/{compile,verify,smoke}.log。静态扫描并未发现剩余生产 SystemViews/Advice 依赖，也未发现本次保留 Entity 输出带 password/salt/secret；这不是所有未来数据内容的安全保证。未执行真实系统API/DB/浏览器验收、UI build或部署。Service/Mapper/权限/资源范围不变；未提交/推送。回滚只能按本任务的响应文件与对应baseline逐项恢复，不覆盖其他用户改动。

### SystemConfiguration 与事务封装复核（2026-10-06）

本轮仅源码审查及知识追加，不修改业务代码。Base 基线 29eeec4；源项目 ktg-mes 当前干净工作树确认为 feat/multi-factory-isolation，提交 bdc299da。已有 application-dev.yml 修改不读取、不处理。未运行构建、测试或数据库验证。

SystemConfiguration 是系统模块的 Spring 装配入口：平台数据源/迁移、平台 Mapper、认证授权、文件存储、缓存预热、调度及同步协调。装配入口本身合理；平台/业务数据库和 Mapper 归属隔离也有必要。但大量 Mapper 手工 Bean 与 SystemUnit 代理、包名事务切面，是 Base 新增实现，不能归因于源分支原有设计。

| 项目 | 源分支 | 当前 Base |
| --- | --- | --- |
| 平台事务 | MfDataSourceConfig.masterTransactionManager：Spring DataSourceTransactionManager；SysUserServiceImpl 等显式 @Transactional | PlatformPersistence 内部创建 JdbcTransactionManager + TransactionTemplate；SystemUnitAspect 按包名覆盖公开 Service 方法 |
| MyBatis 集成 | SqlSessionFactoryBean + MfMapperScanConfig，沿用 MyBatis-Spring | SqlSessionFactoryBuilder + 手工 Mapper Bean + SystemUnit/OwnedSqlSession 代理 |
| 多厂事务身份 | BusinessTransactionManager 继承 Spring 管理器，只扩展 begin/suspend/resume/cleanup 的工厂路由身份绑定 | DedicatedDatabasePersistence 保留 Scope、binding、lease 与 session 校验，并使用 Spring TransactionTemplate/同步回调 |
| 提交回调 | 可使用 Spring TransactionSynchronization | SystemUnit 自维护 committed/rolledBack 列表；AnalyticsUnit 另有 ThreadLocal/session/代理实现 |

结论：Base 没有自行实现 JDBC 提交/回滚，底层已经是 Spring；过重之处在 Spring 上方重复实现的系统 Service 事务与 Mapper 生命周期。普通系统 CRUD 的 SystemUnit 包名切面和逐 Mapper 装配缺少必须另造机制的证据，建议收敛为具名平台事务管理器、MyBatis-Spring 和显式 Service 事务边界。FactoryScope、数据库归属、事务内工厂身份不变、连接版本及失效校验应保留，通过框架扩展点承载，不应随封装一起删除。程序式 TransactionTemplate 本身是标准 Spring 用法，并非过度设计。

源码风险：SystemUnit.run 第23行在嵌套时直接执行 action，没有为内层失败标记 rollback-only。如果内层跨 Service 调用写入后抛异常，外层捕获并正常返回，外层模板可能提交内层写入；这与参与同一事务的 Spring REQUIRED 内层边界语义不同。此为源码推导，未执行复现。SystemUnitEffectsTest 现有两个测试覆盖成功嵌套和顶层失败，不覆盖吞掉内层异常、REQUIRES_NEW 或事务属性。切面还将包内公开查询默认纳入事务；包外 Service 则不会自动命中。

不能仅添加 @Transactional：PlatformPersistence.transaction 第44行拒绝已有活动事务，内部管理器也不是具名 Spring Bean；现有 Mapper 依赖 SystemUnit 当前 session。后续整改须同时替换装配、session 集成和事务边界，验证回滚、嵌套/挂起恢复、提交回调及跨厂/跨库拒绝。当前仅提出建议，不实施该架构调整。

源码入口：mes-admin/.../server/SystemConfiguration.java；mes-framework/.../framework/persistence/{SystemUnit,SystemUnitAspect}.java；mes-framework/.../infrastructure/persistence/{PlatformPersistence,DedicatedDatabasePersistence,OwnedSqlSession}.java；mes-admin/.../server/AnalyticsConfiguration.java。源分支对照：ktg-framework/.../factory/{MfDataSourceConfig,BusinessTransactionManager,MfMapperScanConfig,MapperDatasourceInterceptor}.java；ktg-system/.../service/impl/SysUserServiceImpl.java。

框架依据：[MyBatis-Spring 事务](https://mybatis.org/spring/transactions.html)、[SqlSessionTemplate](https://mybatis.org/spring/sqlsession.html)、[Spring 事务传播](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)、[TransactionSynchronization](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)。这些能力支持收敛方向，不代表依赖兼容性或整改后的运行行为已经验收。

进一步核对 Mapper 维护方式：源分支 MfMapperScanConfig 使用 ClassPathMapperScanner 自动注册 Mapper 并绑定 master/business/erp SqlSessionFactory，不需要逐个编写 @Bean。但并非完全没有人工清单：MASTER 来自 mf-master-mappers.txt，ERP 是显式类名集合，未命中两者的 Mapper 默认归 BUSINESS。因此可复用扫描与标准集成方式，不宜照搬未分类默认业务库的规则。Base 已有 PersistenceOwner 注解，可作为扫描分类依据，缺少归属或归属非法时启动失败；新增 Mapper 只在自身声明归属，无须再改中央 Set、逐 Mapper Bean 或 BusinessMapperRegistration。该方向是后续整改建议，本轮未实施。

源分支三类库进一步核对：装配层按 Catalog 将 Mapper/XML 分配到 master/business/erp 三个 SqlSessionFactory。master 直接使用配置的中心库 DataSource；business 和 erp 是独立 session 工厂但共用 FactoryDataSource 路由器。执行层 MapperDatasourceInterceptor 优先读取 Mapper 方法上的 @DataSource，其次接口注解，缺省 BUSINESS；BUSINESS 根据 FactoryContext 和受管绑定选业务连接，ERP/HR/WMS/EXTERNAL_MES 选择对应配置外部连接。ErpItemSourceMapper、ItemLotSourceExtraMapper 同时进入 ERP Catalog 并标注 @DataSource(ERP)。外部连接在此实现中来自独立配置，不等于每厂业务库绑定。历史 MASTER/SLAVE 路由标记被转换为 BUSINESS，不能用 @DataSource(MASTER) 推断中心库归属。事务执行前及连接获取时验证数据源身份，冲突拒绝。以上仅为当前源码事实；未验证真实数据库路由。

SessionFactory 数量不等于连接用途数量：master 会话工厂绑定固定中心库；business 与 erp 会话工厂都绑定同一 FactoryDataSource，后者按用途选择 BUSINESS/ERP/HR/WMS/EXTERNAL_MES 实际连接。ERP Catalog 只隔离两个指定 ERP Mapper；HR/WMS Mapper 不在该集合，装配到 businessSqlSessionFactory，再靠 @DataSource(HR/WMS) 路由外部库。因此该分支的 businessSqlSessionFactory 命名不意味着其中 Mapper 都访问业务库，erpSqlSessionFactory 也不是统一的外部库工厂。HR/WMS 有生产 Mapper 使用；本轮搜索 EXTERNAL_MES 注解仅发现集成测试使用，不能据此宣称已有生产外部 MES SQL 调用。独立 session 工厂并不意味着跨库本地事务安全；运行时身份冲突校验仍生效。


### 三库装配整改交付（2026-10-06，取代前述仅审查建议）

用户授权以提供的SQL Server连接装配外部ERP，优先参考feat/multi-factory-isolation的合理实现，并取消重复封装和手工Mapper绑定。源分支的MyBatis-Spring扫描、具名Spring事务管理器及begin/suspend/resume/cleanup身份绑定方式被沿用；Base按用户决定用PersistenceOwner取代Catalog清单，并将ERP固定为独立数据源/会话工厂，不沿用business工厂承载HR/WMS外部路由的方式。

生产装配：PersistenceConfiguration分别提供platform/business/erpSqlSessionFactory及对应事务管理器。ERP启用时共三个工厂，未启用时两个；业务工厂只创建一次，FactoryBusinessDataSource按当前Factory租借版本化业务池。RuntimeRegistry只维护池/归属/版本/租约，不再为每个池创建SqlSessionFactory。OwnerMapperRegistration自动扫描Mapper并按自身PersistenceOwner.PLATFORM/BUSINESS/ERP注册标准MapperFactoryBean；无缺省归属、中央类清单、逐Mapper Bean或额外用途注解。新增Mapper只声明归属及SQL，不修改SystemConfiguration。生成器也不再生成PlatformMapperRegistration配置，生成Java通过显式import输出。

SystemUnit、SystemUnitAspect、AnalyticsConfiguration（含AnalyticsUnit/Aspect）、BusinessMapperRegistration、PlatformMapperRegistration已删除。普通Service写入用例显式@Transactional，分析查询使用businessTransactionManager只读事务；SpringCommitActions用TransactionSynchronization处理副作用。OwnedTransactionManager只扩展数据库/工厂身份保护，传播与rollback-only由Spring处理。PlatformPersistence/BusinessPersistence保留既有基础设施、CLI及生成骨架程序式入口，使用标准Spring模板和同一会话工厂；不再保存自定义当前session或另造嵌套事务。独立平台元数据读取仍有明确只读连接边界，禁止更新，不借用业务事务连接。同步run_token、Quartz抢占、私有文件ownership、权限和连接版本校验保持。

另移除只有NOT_CONFIGURED的ERP provider/连接解析/空Adapter模型及Foundation ERP占位检查接口；该接口现在404，未新增ERP业务HTTP入口。外部凭证和连接仍独立于FactoryScope。通用application.yml默认关闭ERP；开发profile仅新增optional本地配置导入，原用户未提交的其它配置内容保留。用户提供的真实连接参数只写入Git忽略的.local/erp.yml（0600），不写通用配置、报告或验收JSON。增加MyBatis-Spring4.0.0及Boot管理的SQLServer JDBC驱动；依赖选择依据官方Spring7兼容表 https://mybatis.org/spring/ 。未新增HR/WMS/EXTERNAL_MES类型。

最终验证：标准 `zsh -ic 'mvn -B -ntp verify'` BUILD SUCCESS，跨模块165项：162通过、3跳过、0失败/错误。包含既有20项Dedicated数据库隔离测试、4项扫描/装配测试、2项真实Spring提交/嵌套rollback-only测试、3项生成Java导入测试和既有64项响应fixture。装配测试证明三个合成H2库互不混用、A/B厂路由、缺Scope拒绝、事务内切厂拒绝、跨库Mapper拒绝及REQUIRES_NEW后恢复；另直接加载生产PersistenceConfiguration，证明标准工厂与现有Mapper可装配。既有生成器测试实际编译/运行生成的CRUD骨架。

真实外部连接：TCP端口探测成功；只读SELECT1探针在建立JDBC连接时因TLS协议协商失败（SQLState08S01），未建立连接、未执行SQL、未读取业务数据。未关闭加密/跳过证书或调整JVM协议策略；因此真实ERP联调未通过。未执行ERP业务写入、外部同步流程、真实多节点/多厂生产数据库验收或UI验证，未重启用户dev服务，未提交/推送/部署。

最终打包smoke 4/4通过，含已删除类在主JAR及模块JAR中无残留；显式import扫描0违规，git diff --check通过。打包验收与静态检查见 docs/reviews/2026-10-06-persistence-verification.json；原始最终日志保存target/validation/persistence-{verify,smoke}.log。只清除了本轮删除源码对应的过期生成.class文件，未清理其它target证据。回滚按本轮持久化/装配/Service事务文件、对应文档规则和本地ERP导入逐项恢复，保护用户原有开发配置修改。ADR-004/005按时间线追加修订；platform/codegen操作文档及AGENTS与当前装配一致。

### 启动注入修复与外部多连接模板（2026-10-06）

DruidController 原构造器按 DataSource 类型注入，三库启用后有三个候选 Bean，导致启动失败。已显式 @Qualifier("platformDataSource")，保持平台池监控契约；编译已启用 parameters，根因是缺少明确归属。PersistenceAssemblyTest 加载生产装配配置与该 Controller，启用三套数据源并校验监控池为 platform。检查生产 DataSource 消费处，其他装配入口已有显式 qualifier。

外部连接统一 PersistenceOwner.ERP，通过 mes.erp.datasources 标识映射与 ErpRoutingDataSource 共用第三个 SqlSessionFactory。ErpTransactions 以标准 Spring TransactionTemplate 在取连接前选择标识；缺标识/未知连接明确失败，事务内换标识拒绝，异常后清理。配置及 Adapter/Mapper 模板位于 config/erp-datasources.example.yml、docs/development/templates/erp/；没有新增外部业务领域、表或业务 SQL。原本地私有 ERP 配置已调整为映射结构，未记录凭证。

标准 Maven verify 成功，最终计数见 docs/reviews/2026-10-06-persistence-verification.json。新增4项 H2 路由测试覆盖三标识、无默认库、事务固定身份、嵌套/REQUIRES_NEW恢复、回滚与线程隔离。打包启动smoke 4/4通过（默认无业务连接配置）；三数据源装配与该 Controller 的证据来自定向生产配置测试。日志 target/validation/startup-routing-verify.log。未重启用户开发服务，未重新尝试真实ERP JDBC；真实数据库联调仍未通过，不宣称实际dev完整启动已验证。

2026-10-06 用户澄清续做范围为外部多数据源路由实现。重新核对当前代码：ERP 工厂绑定 ErpRoutingDataSource，读取 mes.erp.datasources 映射，按标识切换；所有外部 Mapper 仍为 ERP ownership。再次执行 ErpRoutingTest、PersistenceAssemblyTest，共8项全部通过，日志 target/validation/external-routing-confirm.log；结构化记录追加 external_routing_revalidation。此轮未新增业务代码、未操作用户开发进程、未执行真实外部业务 SQL。


### 固定包扫描与声明式外部路由交付（2026-10-06）

按用户新偏好用固定包决定会话工厂归属。MapperPackages 定义平台(system/quartz/platform)、业务(mes/modules)、外部(external)三个互斥包集合，标准 MapperScannerConfigurer 分别绑定对应工厂；不逐 Mapper 写归属注解，不登记中央类清单。生产 Mapper 的重复归属注解移除；OwnerMapperCatalog 按包整理兼容基础设施所需集合，拒绝包重叠、同名 Mapper 和旧注解与包冲突。程序式测试 fixture 的旧 PersistenceOwner 校验保留，不参与生产扫描。

默认采用外部访问类/方法的 ErpSource 标识，类级声明可以复用整个类，方法可覆盖。注解组合具名 Spring @Transactional，选择切面先于事务拦截器执行；Mapper 直接调用，不再传回调。ErpTransactions 移出生产，仅低层路由测试辅助类保留；打包检查明确排除此旧生产类。缺配置失败、事务内换标识拒绝、跨库事务拒绝、Factory/租约身份规则保留。ErpSourceAnnotationTest 用实际 Spring 代理/H2 验证选择发生在取连接前、类级复用、方法覆盖、嵌套冲突、回滚和异常清理。

生成器不再发出 PersistenceOwner 注解，按 Scope 限定 GLOBAL 为 platform 包、FACTORY_SCOPED 为 modules 包；CLI 默认包也按 Scope 选择。生成的骨架验收断言改为包归属；Java 导入同步显式化。模板不提供外部业务表、客户 SQL 或运行数据。

标准不带参数的 Maven verify 被范围外既有 Spotless 格式问题阻断；未格式化无关文件。限定 Spotless 到本轮相关源码后 verify BUILD SUCCESS：170项，167通过、3跳过、0失败/错误。打包启动smoke 4/4通过，生产 JAR 无 ErpTransactions 类；git diff --check 通过。日志 target/validation/fixed-package-verify.log 与 unrestricted-spotless-failure.log。未重启用户服务、未运行真实外部业务 SQL、未提交/推送/部署。

文档同步：AGENTS.md 第9节改为固定包与注解约束；platform.md 追加“固定包扫描与外部连接注解”当前接入规范；codegen.md 更新 Scope/包约束与生成行为；ADR-005 追加本日“固定包归属和声明式外部连接”决策与时机原因，旧记录保留时间线。结构化验证记录追加 fixed_packages_and_declarative_erp。业务流程未增加，变更为持久化装配、连接选择与生成骨架。

## 2026-10-09 持久化收敛

本轮用户新要求取代此前三工厂和外部连接接入规范：只保留中心库 + Factory 多业务库，工厂名 masterSqlSessionFactory/businessSqlSessionFactory。移除外部选源、ERP owner/装配/模板/SQL Server 驱动；监控 UI 同步仅显示中心与业务池。历史 Source Product 审查不修改，当前规范见 ADR-004 与 AGENTS 第 9 节。未修改业务 SQL、业务流程或 ktg-mes。

### 2026-10-09 验收收据

- `mvn -B -ntp test` 与 `mvn -B -ntp verify` 均通过；全 reactor 共 182 项（framework 7、generator 3、admin 172），178 通过，4 个未 opt-in 真实 MySQL 测试跳过，0 失败/错误。
- IDEA 原生格式化与 `scripts/format-java --check`：37 个本次修改 Java 文件通过；仓库原缺该 launcher，本轮补齐，复用既有 Project.xml，使用临时独立 IDE state，不影响用户正在运行的 IDEA。
- `/usr/bin/python3 scripts/smoke-packaged.py`：6 个检查通过。产物不含已删除外部类/SQL Server driver（含内嵌模块 JAR 检查），隔离 packaged loopback 启动通过；不使用用户开发配置。
- UI：Node22.23.1，`npm test` 82 项通过，`npm run build` 通过；已存在的 deep 语法和大 bundle 提示保留。两个仓库 `git diff --check` 通过。
- 中途测试失败为旧三 TM 数量断言与监控 Mock 未提供缺失项错误，已更新为新双入口事实并完成复跑；业务/中心隔离、回滚、Scope/版本/租约和异常清理用例继续运行。
- 结构化收据：`target/validation/two-factory-convergence.json`。未做真实多厂数据库、生产部署或浏览器验收；未提交/推送，未重启用户服务。修改基础设施装配、关联事务 Bean 引用及监控类型，没有修改业务 SQL、Schema 或业务流程。
