# MES Base 工程规则

状态 Active；2026-10-05。执行非简单工程任务先读 `/Users/user/Documents/agents/AGENTS.md` 并按实际栈路由，读取目标目录更具体规则。用户当前授权优先，保护已有工作。

## 1. 项目目标
以 ktg-mes / ktg-mes-ui / heri-uni-app 为 Source Product，抽取可复用产品能力，去公司化、去客户定制并修正必要架构问题。优先沿用原 RuoYi 模块与 Controller → Service → Mapper；现有 Base 分包可以调整或替换，保留已验证的 Factory/权限/资源/数据库隔离行为。先迁基础设施和系统设置，再迁业务；不为迁移重新创造 MES Domain。

## 2. 单代码 / 单架构
一套 Core；单厂等于 Factory count=1。不复制服务、不分 Single/Multi 两套主链路，不为简单 CRUD 增层。Core 不依赖 Spring/ThreadLocal/具体基础设施，也不依赖 SysUser/SysRole 持久化 Entity。

## 3. FactoryScope
Scope 只含 FactoryId；不等于 Permission、DataSource 或 ERP Connection。无 UserFactory 准入：有效空角色账号可登录、选择启用 Factory 和访问 AUTHENTICATED；AUTHORIZED 缺权限拒绝。偏好只是 UX，不能选首厂或魔法 0/1。PLATFORM 角色显式 kind、无 factoryId；FACTORY 角色必须所属厂。权限按每次请求当前厂读取，不进长寿命 Token/跨厂缓存。

## 4. Controller / Service / Mapper
Controller 收参、基础校验、协议返回；不做业务。Service 编排、规则、状态转换、事务和外部协调；Mapper 数据访问，不做授权决策。默认单 Service，只有长期真实复杂协调才拆分。外部主动调用经 Port/Adapter，Adapter 不访问 Mapper/编排业务。

## 5. Domain / DTO / VO / Persistence
请求/响应/持久化/外部契约隔离；Entity 不直接作为 API。Service 完成 Entity→VO、展示语义与状态枚举；禁止业务匿名 Map / 条件散参，允许索引集合和框架元数据 Map。字段来源、赋值、使用清楚；不能用字段兜底掩盖契约。可选字段不因文档或假想业务变必填。

## 6. 外部系统
Adapter DTO 不泄漏 Core。ERP/WMS/Equipment 外部凭证、连接与 Factory 位置独立；未配置明确失败。不得因复制旧项目引入客户单据、URL、固定厂或供应商规则。

## 7. 权限 / Resource Scope
HTTP 每个方法显式 PUBLIC/AUTHENTICATED/AUTHORIZED；缺注解启动失败。标准 CRUD 默认 AUTHORIZED，平台与厂权限独立。后端是最终边界，前端隐藏不等于授权。带 ID 操作必须校验资源所属 Scope/主体；不信任客户端 userId/userName/factoryId。文件必须 owner + scope + resource 证明，不能公共裸路径下载；新资源必须提供服务端 policy。

## 8. 事务 / 幂等 / UNKNOWN
本地事务不能回滚外部 HTTP；外部命令结果区分 SUCCEEDED/REJECTED/UNKNOWN。保留原异常链及 requestId，对外安全真实 HTTP 错误；不吞异常/伪造成功。跨平台/业务事务拒绝；线程上下文嵌套及异常恢复/清理，跨线程不得传持久化 Session。同步任务需要 DB 条件抢占、run token、跨入口复用及明确恢复，不能依赖 JVM 锁或 Quartz 单节点参数。

## 9. 数据库 / SQL / Ownership
Platform/Master、Business、External 分开；Mapper 显式归属和 SessionFactory 登记，缺 Scope/连接/版本不回退平台、首库或默认 Business。独立库仍保留 Factory 业务身份；Shared DB 未实现，启用须通过 ADR-004 行范围验收。参数化 SQL、固定/受控排序、分页数量边界、避免 N+1；unique/reference/聚合/cache/import/export 都需范围审查。业务 cache 缺 Scope 不能全局回退。复用标准 Mapper，复杂查询/局部业务更新放 ExtraMapper，不污染生成 CRUD；授权决策在 Service。新 CRUD 物理删除，不能批量更改历史表契约。Schema 来自真实 metadata；新迁移不带客户数据/seed。

## 10. 测试 / 验收
先检查 pwd/git status/branch 与真实调用链，再改最小范围。测试源码存在不等于通过，build 不等于业务验收。按影响执行定向测试、范围/资源负例、真实生成 SQL/契约和 UI，明确静态/Mock/Fixture/本地/生产证据。Java21/标准 Maven 沿用用户环境，不创建项目 .m2/.mvn/private mirror；稳定工具 launcher 可调用标准 Maven。`mvn -B -ntp verify`；涉及打包启动再执行 `scripts/smoke-packaged.py`（本机可用 `/usr/bin/python3`）。UI 使用 .nvmrc 已安装 Node，不改全局默认；执行 test/build。TEST ONLY 业务探针在 src/test 或自有 target，不包装为生产领域。

## 11. Git / 工作区
只改当前任务；不重置、不覆盖用户修改、不危险清理，不顺手重构/升级。确认目标路径，旧公司项目只读。提交/推送/部署需对应授权；本地构建不是部署。

## 12. 文档 / 证据
代码和文档一致；重要结论附方法/文件/测试证据，无法证明写明无法证明。报告简洁记录做了什么、文件、实际 command/passed/failed、风险和未验证边界；可复用判断依个人治理规范进入项目 docs。禁止输出凭证/令牌/密码；审计不采集请求体。

## 13. Migration / Development
迁移阶段忽略 Codegen Skill，不调用、不要求先生成 CRUD，不让工具定义产品字段或页面。已有功能以源项目代码、配置、Schema和页面流程为输入，优先 COPY / PORT，必要时局部重构；REDESIGN / DROP 必须有具体证据。迁移记录 SOURCE_PRODUCT_PORT 即可，不需申请 Codegen 例外。仅在后续新增功能开发阶段，适合表驱动的标准 CRUD 优先调用 mes-crud-codegen Skill，并完成业务、SQL、权限及 UI 审查。

## 14. Skill / 工具边界
新增开发功能先检查适用 Skill；迁移工作不调用 Codegen Skill。Skill 调用底层工具而不重实现工具，完成后仍需项目测试/业务审查，不能跳过架构约束。开发期仓库 Skill 入口：`.agents/skills/mes-crud-codegen/SKILL.md`；配置/CLI 详见 docs/development/codegen.md。

## 15. UI Base
复用 mes-base-ui 的统一 Shell/CrudPage/request/dict/permission，不为每页创造视觉体系。代码生成页只是 Functional Scaffold；必须检查查询、Toolbar、表格/列顺序、枚举/日期/精度、表单分组、loading/empty/error、确认删除、分页、窄屏、中文/tooltip/按钮权限并优化。API 只读真实契约，不新增 AjaxResult/rows/data 多结构兜底。客户端凭证不持久化；选厂清除旧授权、数据和对话框，过期响应不回填。

## 16. clean-room / Source Product
clean-room 不等于禁止复用旧项目。通用源码、有效配置键、Schema和页面产品资产默认进入迁移候选；按所有权/授权/许可核对来源，保留许可及修改记录，无法确认的部分标 REVIEW。不得带入公司身份、客户/生产数据、Secret、内部账号、真实连接值、客户专用规则或不应复用的公司资产；混合实现先抽离专有部分，不整块舍弃功能。保留 Factory/权限/ownership 行为，允许更换实现和分包；拒绝默认库兜底、HTTP200错误、任意反射 Job。AGENTS 定规则、Skill 定开发工作流、工具执行，不能用架构或工具反过来定义迁移产品。
