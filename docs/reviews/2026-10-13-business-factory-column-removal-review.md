# 业务工厂字段移除与重新生成报告

2026-10-13。用户决定按独立业务库隔离，要求业务表移除工厂字段并重新生成。规则已落入 AGENTS 第9.0节和14.1节、ADR-001及Codegen说明。本轮没有执行Git提交／推送，没有Source Product修改，没有操作用户现有服务。

## 目标与结论

执行前通过中心 mf_factory JOIN mf_datasource 核对 SIMPLE_B：数据源ID **8**，主机 **192.168.18.25**，端口 **3306**，库 **mes_base_dev_business_simple_b**，工厂及数据源均启用；不是仅凭库名判断。真实验收均使用此已配置数据源，没有新建数据库、工厂或平台身份。

发现并移除16张业务表的factory_id。迁移前有数据的表全部只包含SIMPLE_B，无混厂数据。V15已成功执行，整个验收业务库的工厂／租户列数为0。原有 **688条**记录，排除删除的factory_id后逐条比较，其他业务数据完全一致。

中心工厂主数据、数据源绑定、工厂角色／偏好、文件范围和mes_master_sync等控制面标识保留。请求选厂、权限、事务租约、资源归属与缓存仍由基础设施实现；没有取消Factory上下文，也没有加入同库多厂模式。

## 实际表清单与数据保全

| 表 | 迁移前记录 | 验收后记录 | 非工厂字段逐条比较 |
| --- | --- | --- | --- |
| `dv_subject` | 0 | 0 | 一致 |
| `equipment` | 0 | 0 | 一致 |
| `material` | 46 | 46 | 一致 |
| `material_category` | 1 | 1 | 一致 |
| `md_unit_measure` | 0 | 0 | 一致 |
| `md_workshop` | 0 | 0 | 一致 |
| `mes_material_issue` | 0 | 0 | 一致 |
| `mes_material_issue_item` | 0 | 0 | 一致 |
| `operation` | 589 | 589 | 一致 |
| `routing` | 46 | 46 | 一致 |
| `safety_stock` | 0 | 0 | 一致 |
| `unit_measure` | 4 | 4 | 一致 |
| `wm_inventory` | 0 | 0 | 一致 |
| `wm_warehouse` | 0 | 0 | 一致 |
| `workshop` | 1 | 1 | 一致 |
| `workstation` | 1 | 1 | 一致 |

工单和生产批次此前V14已经移除工厂列，路线节点／边／适用产品从父级及业务库取得归属，本轮不重复创建另一套范围。

正式迁移：`mes-admin/src/main/resources/db/business/V15__remove_business_factory_columns.sql`。
现有包含factory_id的索引被重建为其业务列索引，保留已有unique的逻辑唯一性；未新增业务唯一要求或物理外键，没有删除整表或用户数据。备份在自有target目录：完整受影响表数据及SHOW CREATE TABLE；数据备份权限0600，校验值见verification.json，不将原始数据／凭证写入报告。MySQL DDL不能承诺整体事务回滚，执行前已核对混厂及索引；未修改任何已执行旧迁移checksum或repair历史。

## 重新生成与适配

Skill：`.agents/skills/mes-crud-codegen/SKILL.md`。先执行标准Maven verify取得最新生成器，再从迁移后的真实MySQL metadata生成16表审查副本，GENERATED／16 supported／0 rejected／306个产物，generateSync=false，无同步产物。

调用：`./scripts/mes-business-codegen target/database-scope-removal/codegen.yaml target/database-scope-removal/codegen-report.json`。显式仅选受影响16表、write/fail，副本输出自有target。实际采用 **64个标准Entity/Example/Mapper/XML**，包与当前basic／production／issue／wm／md／dv目录一致。

其余既有手工Service、Controller、DTO和页面仅作关联字段适配，不把生成基础CRUD覆盖为工单／版本／图模型的业务入口：

- 清理业务Entity、VO、请求／查询／同步输入、toView和前端字段／类型中的factory_id或factoryId。
- 清理业务SQL工厂谓词、ID查找后的重复工厂比较和服务端工厂字段赋值；WorkOrderExtraMapper的产品适用路线JOIN仍保留，只移除工厂参数／列／条件。
- 保留当前库引用存在、产品／工单一致性、确切已发布版本、版本冻结、材料／仓库引用删除保护、明细父单保护及事务。
- 原来借factory_id同值UPDATE取得的引用行锁改用生成Mapper的主键同值UPDATE，逐个更新自身ID，不使用一个ID覆盖整组记录、不改业务字段。管理批次最多100；同步输入保留既有有界限制（路线每批100、每路线最多100节点／产品），集合读取后按ID顺序锁定，无外部查询N+1、无新锁Mapper或SQL实现。
- 移除开发生成配置serverScopeColumn，所有开发执行配置generateSync=false。保留既有手工同步及协调框架，未来生成暂不新增同步功能。
- 正式生成源现在为 `.local/codegen-simple-b.yml` 的acceptance profile，来自本轮已核对的绑定；该连接快照被Git忽略且权限0600。后续需核对最新绑定，不能使用未迁移旧库重新生成工厂列。
- 主配置补齐当前模块路径映射；重生成手工文件仍会OUTPUT_CONFLICT，不能恢复标记强行覆盖。手工改动的生成文件已解除生成归属，普通重新生成的标准持久化仍保留标记。
- 菜单审查SQL没有执行，没有角色赋权。Source Product三个仓库只读；用户其他工单／批次开发内容继续保留，关联处仅同步无工厂字段契约及验收数据源。

## 测试迁移与实际结果

旧单库内塞入OTHER工厂行的fixture不再代表当前模型，改为当前库缺失引用负例，同时保留两套独立A/B H2数据库。补充A/B都存在本地主键1时各自读取自身单据，且响应没有factoryId的验证；不是把分库退化为行级租户过滤。

领料、库存MySQL测试改为从真实中心绑定读取SIMPLE_B，在真实MySQL执行生成XML CRUD、精度／索引／COMMENT检查，测试行rollback。旧工序／路线测试不再使用另建转换库或临时中心工厂，改在已配置B执行真实业务事务；中心协调明确为H2 fixture、ERP查询为合成Port，仅测试协调SQL作H2 DATE_SUB方言适配，生产中心Mapper未改。

| 检查 | 结果 | 证据边界 |
| --- | --- | --- |
| 前置 mvn -B -ntp verify | passed | 保护入场代码基线并构建CLI |
| V15 Flyway仅本轮迁移 | success=1 | 只执行SIMPLE_B，未执行中心库迁移 |
| Codegen真实metadata | 16表／306产物／0拒绝 | 64个持久化正式采用，其余审查副本不冒充正式发布 |
| MES_ISSUE_MYSQL_ACCEPTANCE=true MES_STOCK_MYSQL_ACCEPTANCE=true mvn -B -ntp -Dmes.test.operation.mysql=true verify | 394 tests／0 failure／0 error／9 skipped；BUILD SUCCESS | 3个真实MySQL测试及既有fixture；skip不冒充通过 |
| 原数据逐条比较 | 16表／688条，非工厂内容一致 | 实际配置B；不输出原始数据 |
| Node22 npm test | 33 files／177 passed | Vitest／jsdom，不是浏览器 |
| Node22 npm run build | passed | vue-tsc及构建；既有大chunk提示保留 |
| 实际修改UI定向ESLint | passed | 全目录检查另发现正在并行维护的工单页／API原有20条错误与40条警告，未顺手改写 |
| IntelliJ原生format-java --check | 114文件passed | 业务／关联测试；未以其他formatter代替 |
| scripts/smoke-packaged.py | 10 passed／0 failed | 自有隔离测试进程，不是用户现有服务 |
| 两仓库git diff --check | passed | 本轮没有执行提交、推送或危险清理 |

开发过程修正了移除字段后留下的构造／查询引用、引用锁空UPDATE、紧凑页面字段删除和H2协调方言问题；最终检查以上述通过结果为准。没有把早期失败日志当作已通过。

## 运行与后续边界

**真实登录API／UI尚未复验**。按用户AGENTS服务管理规则，没有启动、停止或重启现有后端和5173前端；已请求用户重启后端并刷新5173后，在SIMPLE_B继续复核。新JAR位置 `target/mes-base-0.1.0-SNAPSHOT.jar`。旧服务若仍运行旧Mapper，读已删列会失败，不能用旧服务结果判断新代码；旧通过截图也不代表本版本已通过。

其他已配置业务库未在本轮擅自升级。上线／切入它们前，必须核对各库历史、备份并执行适用的正式迁移；旧库残留factory_id NOT NULL时，新代码新增可能失败。不能只升级代码而不升级对应业务库。中心工厂／授权／同步标识继续保留。

回退需分别审查代码、schema和数据备份；删除源码不等于回退DDL，不删除Flyway history、不自动drop表或覆盖用户记录。私有连接快照如因密码／绑定变更过期，按最新已授权绑定更新，不回退错误数据库。

## 文档回执

- AGENTS.md：新增9.0“业务表不保存工厂字段”，修正14.1生成边界；业务与控制面分开。
- ADR-001-factory-scope.md：追加2026-10-13纯业务库隔离决定，保留历史。
- codegen.md：追加当前数据源、16表重生成／手工保护、同步关闭及部署边界。
- 本review与verification.json：真实目标、表／数量、数据保全、生成、命令、passed与未verified边界；没有凭证或原始行。
- 原领料／仓库review：追加旧factory列与行级校验被取代说明；保留旧阶段API/UI证据。

本轮修改业务代码和Schema；没有实现新的生产流程或ERP接口。
## 2026-10-14 现有服务重启后续验收

用户确认已重启后端并刷新前端。现有 5173 代理到 127.0.0.1:8081；GET /health 返回 200、status=UP。现有 admin 会话选择 SIMPLE_B。重新查询中心绑定为数据源 8、192.168.18.25:3306/mes_base_dev_business_simple_b，工厂和数据源均 ENABLED。

真实浏览器完成仓库列表、新增及库存列表、新增、详情、更新、刷新读取。合成仓库 TEST-FID-20261014-WH（ID 9）与合成库存（ID 9）保留供用户复验。库存引用既有物料 20023032001（ID 2），初始数量 12.3456，更新后 13.4567，可用数量 10.1234。真实 MySQL 精确查询确认写入值和引用一致。未修改既有物料或删除数据。列表和表单未展示工厂字段；业务库 factory/tenant 字段仍为 0。

领料页面暂被菜单登记阻塞：中心 sys_menu 查询 path=issue 或 component LIKE 'issue/%' 为 0 行；直接打开 /issue/mesMaterialIssue 显示“页面尚未登记，请返回菜单选择可用功能”。已请求执行已生成菜单 SQL 的具体授权，未自动执行或调整角色授权。

截图：assets/2026-10-14-factory-column-removal/inventory.jpg、issue-unregistered.jpg。本轮真实仓库/库存页面调用现有 API 并通过数据库核对；未独立采集认证 HTTP 状态及响应契约。领料 CRUD、父单删除保护、权限负例和工艺版本规则尚未在当前运行服务复验，原测试证据独立保留。未把新增两条合成数据后的状态等同于此前 688 行快照。

本轮未修改业务代码，仅追加验收文档与截图；没有操作用户服务生命周期。

## 2026-10-14 用户授权执行领料菜单 SQL

用户明确允许执行 `mes-business/docs/generated/issue/material-issue-menu.sql`，要求保留角色绑定、不新增角色授权。执行前在 `.local/issue-menu-20261014/before.tsv` 保存受影响菜单及完整角色菜单绑定（权限 0600）。在当前中心库事务内执行该 SQL，提交后新增菜单 ID 2563–2575，共 13 条；包含领料目录、单据/明细页面及各自 5 个 CRUD 权限按钮。before/after 角色绑定集合比较完全一致。

刷新真实 5173 页面后，SIMPLE_B 的 admin 仍看不到领料目录。数据库核对其角色为 100 / platform-admin（平台管理员），全部新领料菜单的角色绑定数量为 0。此时阻塞由“菜单未登记”变为“现有角色未授权”，不是生成代码或业务数据库失败。

已准备 `.local/issue-menu-20261014/grant-platform-admin.sql`：只为经 role_id、role_key、kind 三项限定的平台管理员角色 100 添加上述 13 个菜单绑定，使用 NOT EXISTS 保留已有绑定；尚未执行，另行请求用户授权。该授权会使此平台角色的领料业务权限在其选定的有效工厂生效，并非仅限 SIMPLE_B。未修改业务代码、账号或角色定义，未重启服务；领料真实业务验收仍待权限前提满足。

## 2026-10-14 用户授权角色及领料真实验收完成

用户随后明确允许执行角色授权 SQL；事务提交后 role_id=100 的新领料菜单绑定为 13 条，刷新现有 5173 页面，领料出库目录、单据及明细页面均出现。只有这组获授权绑定新增，既有绑定未删除或改写。授权备份与执行脚本保存在 `.local/issue-menu-20261014/`，没有改业务代码或重启服务。

SIMPLE_B 真实验收单 `TEST-FID-20261014-ISSUE`（ID 5）创建成功；流程卡号允许自由格式，资料状态 DRAFT。明细 ID 9 引用材料 ID 2、单位 ID 3、仓库 ID 9，服务端快照分别为 20023032001、01、TEST-FID-20261014-WH。自由批次号“自由批次/验收-20261014”原样保存，数量 1.234567 创建及读回后，成功更新为 2.345678，真实 MySQL 值一致。明细编辑页禁用父单 ID 修改；父单详情正确分页加载该明细，六位小数及快照完整，无工厂字段。

负例：父单编辑引用实际 DRAFT 且 published_at=NULL 的工艺版本 ID 1 被拒绝，页面提示通用输入错误，数据库 routing_id 仍为 NULL；明细编辑引用不存在的单位 ID 999999999 被拒绝，随后恢复为有效 ID 3 才能保存。用户在动作前确认后，对已有明细的验收父单 ID 5 执行删除请求，页面提示“数据冲突，请刷新后重试”；精确查询确认父单 ID 5 和明细 ID 9 均保留，删除保护通过。拒绝文案为全局通用消息，不能据此宣称已验证特定错误码；本轮未独立抓取认证 HTTP 响应体/状态。

截图：`assets/2026-10-14-factory-column-removal/issue-header-detail.jpg`、`issue-item-detail.jpg`、`issue-routing-rejected.jpg`、`issue-parent-delete-rejected.jpg`。最终数据库证据 `.local/issue-menu-20261014/business-final.tsv`；factory/tenant 列数仍为 0。保留 4 条明确合成验收记录供用户复验：仓库 9、库存 9、领料单 5、领料明细 9；未删除其他数据。

当前 API/UI VERIFIED 的范围限定为上述真实页面调用现有服务及配置业务库核对。未执行独立无权限账号负例、发布工艺正例和真实跨厂同 ID 比较；之前定向测试的相应证据继续独立保留。未连接 ERP，未扣减库存，未进行生产执行。此前菜单及授权阻塞已解除，历史章节保留其时间线。报告及机器验收 JSON 已更新，本轮未修改业务代码，因此未重复运行已通过的 Maven/UI 构建；git diff --check 通过。

