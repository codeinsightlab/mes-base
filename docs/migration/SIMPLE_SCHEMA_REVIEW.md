> 当前 Quality 决定（2026-10-13）：DEFERRED；历史质检表与验证证据仅作 HISTORICAL。详见本文末尾。

# 第一轮简单业务 Schema 审查

> 第二轮状态：五表全部REVIEW / RECONSIDER，具体见[MES_CAPABILITY_REVIEW](MES_CAPABILITY_REVIEW.md)。
> 本文保存第一轮建表、映射和测试的技术证据，不再批准五个能力独立GENERATE/ADAPT_SCHEMA。

2026-10-12；五个能力均已完成 Schema，尚未 BUSINESS_VERIFIED。
能力决策见 [Matrix](MES_BUSINESS_MIGRATION_MATRIX.md)。

## 来源与实际范围

Source schema 采用当前 `scripts/mf-upgrade/sql/009-new-factory-business-init.sql`，
对照 `doc/设计文档/数据库设计/mes-md.sql、mes-qc.sql、mes-dv.sql`。
它是仓库初始化结构，不冒充现场真实 metadata；例如车间的新字段不能只从早期设计 DDL 取得。
五个候选逐一核对同名 Controller、ServiceImpl、Entity、Mapper XML，以及
`ktg-mes-ui/src/views/mes/{md/unitmeasure,md/workshop,qc/qcindex,qc/qcdefect,dv/subject}/index.vue`。
文件指纹见 [SOURCE_BUSINESS_EVIDENCE.json](SOURCE_BUSINESS_EVIDENCE.json)。

五张目标表均为业务库 / FACTORY_SCOPED；本轮无 GLOBAL 主数据表。
此次用户要求每表保存 `factory_id`，优先于原 Codegen 不注入工厂字段的迁移约定；
仅加入显式资料身份，不新建 Factory 权限/路由体系。字段类型沿用现有 FactoryId 长度 `VARCHAR(64)`，
非空且无默认值。当前测试库有 DEV 和 1 两个启用绑定，不能声称已验证各厂独立业务库。

## 五个简单能力的事实与决定

| Capability | 已核对行为 | Source问题 / 决定 | 后续CRUD注意 |
|---|---|---|---|
| 单位 | 按编码/名称/主单位/启用查询；CRUD；启用下拉/缓存；换算关系由primary_id/change_rate表示 | DOUBLE与Java BigDecimal不一致，改DECIMAL(18,6)；保留主单位关系及Y/N | 主单位必须属当前厂；执行换算、引用保护和范围缓存不是Schema验收内容 |
| 车间 | 编码/名称查重、启用列表、CRUD；页面还有ERP组织/员工/仓库字段 | area改DECIMAL(18,2)；不复制外部组织/仓库绑定；保留可空dept_id，INT→BIGINT；负责人保留文本 | 查重/部门引用进Service；旧部门ID不能未经映射复制；名称不强制数据库唯一 |
| 检测项字典 | 编码/名称查重、类型过滤、工具文本、CRUD | 主要业务字段保留，去无明确使用的attr1..4；没有工作流状态，不新造status | 类型字典值需核对；引用于模板不等于本轮迁模板/检验执行 |
| 缺陷字典 | 类型/等级过滤；CRUD；创建时AutoCodeUtil分配编码 | 保留500长度描述及编码，不迁扣款责任模型 | Codegen普通create需接现有流水号或明确输入契约；不能让模板绕过创建语义 |
| 点检保养项目 | 编码/类型/启用筛选、内容/标准文本、CRUD | Source新DDL名称NOT NULL但页面未必填、早期DDL可空，本轮允许NULL；类型不再默认未解释的0 | 当前subject_type页面必选，但存储允许旧/未知值为空；Source查重用相等判断NOT_UNIQUE，旧代码不迁 |

检测项、缺陷、点检项目属于 GENERATE：主要字段与职责合理，只做工厂身份及小调整。
单位与车间属于 ADAPT_SCHEMA：数值精度/外部绑定/引用结构有明确变化。
没有迁移 Source Controller/Service/Mapper，也没有实现状态流或复杂主子表。

## 必检的十四项

| 项目 | Source审查与Target处理 |
|---|---|
| 1 主键 | 保留measure_id/workshop_id/index_id/defect_id/subject_id，BIGINT自增；不机械改成id，不设置旧200起始值 |
| 2 factory_id | Source五表DDL无该字段；Target显式VARCHAR(64)非空、无默认值；字段不赋予权限 |
| 3 code / business key | 保留各业务编码VARCHAR(64)；Service未来按当前工厂解释；名称不当主键 |
| 4 unique constraint | Source五表初始化DDL只有主键；有些入口做先查再写。Target仅主键唯一，不把普通(factory_id,code)索引说成并发唯一 |
| 5 status | 单位/车间/点检保留enable_flag Y/N；单位保留primary_flag Y/N；检测项/缺陷无启停字段，不新增假想状态 |
| 6 name / description | 保留来源长度：名称255、缺陷描述500、点检内容500、标准255；不统一缩成255造成截断 |
| 7 audit fields | 四个审计字段名称保持；账号VARCHAR(64)、时间DATETIME；可空、无NOW()伪造历史，不存密码/令牌 |
| 8 soft delete | 五表无有效软删流程；Source Mapper是真DELETE。Target不增加del_flag |
| 9 sort | 未发现五个能力的独立排序字段；不新建sort，后续列表使用受控稳定主键排序 |
| 10 FK / reference | 单位primary_id→本厂主单位；车间dept_id→中心目录。逻辑关系保留，不建物理FK；模板/计划等下游引用留待迁移审查 |
| 11 nullable | 保留必需编码/名称/类型/内容；负责人/面积/主引用/换算率/标准/审计可空；点检名称、类型可空。没有为了文档把可选字段强制必填 |
| 12 field type | 两个DOUBLE改DECIMAL；dept_id改BIGINT；Source字典编码保留字符串，不推测枚举数字 |
| 13 length | 字符长度按来源逐字段保持，factory_id为64；所有新增列和表均有非空中文数据库COMMENT |
| 14 index / Source历史字段 | 编码、启用、分类、引用使用普通联合索引且factory_id领先；不增加UNIQUE/FK/CHECK；attr1..4明确逐能力处理，车间attr1是ERP仓库而不是无用字段 |

这里的唯一性检查结论是“没有要求或新增数据库业务唯一约束”。未来若必须并发唯一，
按项目规则先请用户决定约束，不用先查再插宣称安全。

## Source → Target 字段映射

共同映射：各原主键、编码、名称、业务文本、标记、remark、create_by/create_time/update_by/update_time
同名保留；新增 `factory_id ← 显式@target_factory_id`，没有0/1/DEV默认值；
来源五表的 `attr1..4` 不迁。备注有通用说明语义，保留，不因删除预留字段一起删除。

| Source | Target | 映射原因 |
|---|---|---|
| md_unit_measure.primary_id | primary_id | 保留原ID引用；只适用同一来源厂的隔离数据 |
| md_unit_measure.change_rate DOUBLE(12,4) | DECIMAL(18,6) | CAST；新容量/小数位放宽，转换前后核对舍入 |
| md_workshop.area DOUBLE(12,2) | DECIMAL(18,2) | CAST，维持面积口径，来源未证明平方米，不写死单位 |
| md_workshop.charge | charge | 保留文本，不猜user_id/name |
| md_workshop.dept_id | dept_id | 关系保留但Converter暂置NULL；待验证中心目录ID映射后补关联 |
| md_workshop.dept_name | DROP | 展示名称由目录读取，避免保存第二份名称真相 |
| md_workshop.attr1 | DROP（ERP仓库绑定） | 页面及IssueReceiptServiceImpl.getAttr1证明已使用；剥离外部集成字段，不否定通用仓库关系 |
| md_workshop.admin_unit_code/employee_code/warehouse_code | DROP（外部绑定） | 页面展示ERP部门、员工、自收入库编码；本阶段Base不装配外部连接 |
| dv_subject.subject_name | 可空subject_name | 保留原值；对新记录允许未填写名称，内容仍必填 |
| dv_subject.subject_type | 无默认值、可空subject_type | Converter保留实际原值（包括0）；不替用户推断字典语义 |

转换 SQL 在 [scripts/migration/ktg-mes](../../scripts/migration/ktg-mes/README.md)。
本轮没有真实隔离数据副本，所以未执行真实数据导入；五份脚本可独立审查。
合成测试只是验证 INSERT SELECT、精度、字段删除及NULL处理，不证明真实历史数据没有脏值。

## Flyway 与执行边界

唯一新增 DDL：`mes-admin/src/main/resources/db/business/V1__simple_business_master_data.sql`。
MySQL语法，UTF8MB4/InnoDB，无公司seed、角色菜单授权或连接值。
当前启动仅装配中心Flyway；本轮不扩展Foundation，业务迁移显式执行一次。
使用已有Flyway库，locations仅`filesystem:mes-admin/src/main/resources/db/business`，
history=`flyway_business_schema_history`，现存非空测试库baselineVersion=0后应用业务V1。
0基线只声明既有表不属于此迁移，不校验/认可已有表结构；不repair、不改旧中心history。
以后也必须指定独立业务history和仅业务locations，不能把V1混入中心`db/platform`/`db/system`。
新业务库同样使用此history；Schema版本检查/多厂自动升级不在本轮范围。

本轮执行前校验目标库名及五表/业务history不存在，拒绝覆盖。MySQL DDL隐式提交，
部分失败不会由本地事务自动撤销；本次首次执行成功。回滚需业务表仍为空并独立审批清理，
不提供或执行自动DROP，不触及原34张表。

## 验证记录

脱敏机器回执：[SCHEMA_VERIFICATION.json](SCHEMA_VERIFICATION.json)。
任务自有后续日志位于`.local/business-migration-20261012/`，私有连接文件0600且被Git忽略。
首次执行后，共享工作区的其他构建清理了`target/`，首次Flyway/build/smoke日志及旧表DDL摘要文件丢失；
首次结果可由本次聊天工具输出确认，后续重新核对数据库history/metadata并保存回执。

- 实际MySQL/Flyway：首次应用1个迁移，validate成功，第二次应用0。
- 实际metadata：检查五表主键、factory_id无默认值/不可空、普通索引、中文COMMENT、
  DECIMAL、可空性、无物理FK/CHECK及预留/外部绑定字段移除。
- 合成SQL：Converter读取会话内临时来源表，写目标后核对映射并回滚；缺factory_id写入被拒绝。
  没有创建Source数据库或永久副本，没有留存合成业务数据。
- 原34表仍存在，原中心history的版本/描述/类型/成功标记与初始读取一致。迁移只包含五个CREATE。
  原表前后SHOW CREATE摘要比较因前置文件丢失未完成，不能宣称逐表摘要一致。
- `mvn -B -ntp verify`、打包smoke和`git diff --check`结果以机器回执为准。
- 未验证：真实旧数据、生成代码、HTTP CRUD/权限/引用/并发/缓存、两厂独立库、UI及部署。
  这些边界不阻止本轮SCHEMA_READY，也不能被建表或build代替。

## 共享工作区构建门禁

首次`mvn -B -ntp verify`与打包smoke成功（日志随后被并行clean移除）。
重新保存日志的verify曾在另一任务新增的`OpenApiDocumentationContract.java:65`编译失败；
该文件随后由其他任务修正，本轮未修改它。最新verify已越过编译，但
`dev.docsfixture.OpenApiDocumentationHttpTest.publicReadOnlyAssetsAndExternalOnlyContractAreAvailable`
在第75行失败：外部API文档包含测试期内部路径，断言expected=false/actual=true。
这是另一任务的OpenAPI生产/测试新增文件，与本轮五表DDL无调用依赖。
本轮不删除该测试或修正其业务代码；当前完整构建门禁仍未通过，不能以首次成功替代最新失败。
打包smoke重试遇到被clean后的JAR缺失；首次进程退出0，最新包验收待全局构建通过。

## 2026-10-12 第二轮：Schema事实与业务准入分离

首轮已建表/COMMENT/索引/合成映射验证仍是历史事实，本轮未重连数据库或重跑这些检查。
存在简单CRUD不能证明所属能力简单，也不能证明它属于Base默认范围。
单位权威与换算用途、Workshop层级、Quality模板/执行/缺陷关系以及设备维护边界，
在[MES_CAPABILITY_REVIEW](MES_CAPABILITY_REVIEW.md)分别重审。
五表物理暂留；不改已应用Flyway、不删表、不继续Codegen。无最终Schema保留决定。

## 2026-10-13：撤回提前实现的 Quality（当前决定）

Quality = **DEFERRED / NOT_IMPLEMENTED**。`qc_index`、`qc_defect` =
**REMOVED_FROM_CURRENT_BASE**。Previous：GENERATE / SCHEMA_READY → REVIEW / RECONSIDER；
Current：移除简单 CRUD、同步占位、前端页面/菜单和 Source → Base Converter。
旧表结构、映射验证、Source 引用与审查结论均为 **HISTORICAL**，不再构成当前迁移准入或正式转换断言。
撤回原因：Quality 必须等生产执行模型稳定以后整体设计；简单表不等于简单领域。

生产主链优先：Operation → Routing → RoutingVersion / RoutingNode / RoutingEdge → WorkOrder →
ProductionExecution / Reporting → Basic WIP → Completion。该顺序是后续实施方向，
不是对当前未实现环节的验收声明；本轮保留已有生产及资源工作区改动。
主链稳定后再审查 Inspection Definition / Plan / Requirement / Execution / Result、Defect /
Defect Record、Disposition / Rework / Scrap / Release 与 RoutingNode / RoutingVersion、
WorkOrder、ProductionExecution、Reporting 的关系。现在不确定最终表名或 Schema，不保留 Quality 空壳。

执行文件清单、数据库证据与验证边界见 [Quality 撤回回执](QUALITY_EARLY_IMPLEMENTATION_REMOVAL.md)。
