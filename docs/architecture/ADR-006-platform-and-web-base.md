# ADR-006 — Platform / Web Base 与 RuoYi 来源

2026-10-05，Accepted。本轮明确授权 Platform + Codegen；保留已实现 Factory Foundation。

## 来源与许可
官方 RuoYi-Vue 提交 `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3`（MIT）。许可保留于 `docs/legal/ruoyi-MIT-LICENSE.txt`。
来源： https://github.com/yangzongzhuan/RuoYi-Vue/tree/13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3 。
旧 ktg generator 与 frontend 只读观察目录、元数据模型和交互模式；不复制无法证明属于官方上游的文件。
官方模板作为 Velocity 的迁移基础，Schema 只重建通用表职责，不复制任何 INSERT、配置、真实用户、角色、菜单、字典、Job 或客户业务。

## 平台与 Factory 融合
PlatformPersistence 独立 SessionFactory / transaction manager；BusinessPersistence 不更换。
PLATFORM Permission 显式注解，与默认 FACTORY Permission 分离。角色 kind 与 factoryId CHECK；无 0 或超级用户 ID。
有效无角色账号可登录和选择启用厂；权限每请求加载。身份会话使用随机 opaque Bearer、数据库 SHA-256 hash、TTL、撤销；不迁 JWT+Redis LoginUser 或权限快照。
平台默认不开启；显式环境启用，数据库凭证和文件存储根来自环境。迁移 opt-in，没有内置账号或默认密码。

## Vue3 决策（前端建立前）
选择 Vue3 + TypeScript + Vue Router4 + Pinia + Element Plus + Vite，重建通用后台交互模式。
Vue2 / Element UI 已不适合新工程生命周期；旧公司页面不能整体迁移。当前生成器将公开 Velocity 模板流水线适配为 Vue3 契约，不依赖 Vue2 页面兼容层。
维护成本集中在少量统一 Shell / request / permission / dictionary / pagination / CRUD 组件。
使用已安装 Node22（`nvm use 22`），不修改用户默认 Node16；Java21 / Maven 沿用用户 shell 环境。
功能模式：Layout / Sidebar / Navbar / TagsView / 动态菜单 / Store / Permission / Request / Dict / Pagination / Upload / 系统管理。
Scaffold 必须继续经过 UI review 与优化；生成工具不自动创建菜单、修改路由配置或给角色赋权。运行期 Router 根据管理员配置后返回的授权菜单，匹配编译 catalog 动态注册/撤销组件路由。

## 拒绝与后果
不迁旧 FactoryContext、动态库兜底、公司 profile、AjaxResult failure、反射任意 Bean job、公共 URL 文件、Redis Factory 权限缓存。
Department / Post / Notice / 在线用户 / Monitor 等 P1 暂无本轮必要价值，DROP；未来需求需独立决定。
Quartz 引擎复用，调度仅管理时间；DB 抢占 + run token，任务名 allowlist，显式范围。
当前允许的内置 heartbeat 是平台验证入口，不代表 ERP / MES 同步已经实现；外部命令必须另定义 UNKNOWN、幂等和恢复。


## 2026-10-05 范围修正：三个RuoYi通用模块

用户明确要求系统管理/系统监控/系统工具前后端均迁移。上文 Department/Post/Notice/Online/Monitor 的 DROP 是先前阶段取舍，现被此要求覆盖，转为待实现；不再以“无当前价值”排除。复用通用能力时保留当前Factory/权限/Mapper ownership/真实HTTP契约，不复制公司数据或恢复被拒绝的旧机制。完整能力、DDL与本地运行尚未完成；逐项现状见建设报告第13节。


## 2026-10-05 Source Product纠偏（取代先前迁移取舍）

ktg-mes不再仅为参考：源代码、有效配置、Schema与Vue2页面产品资产默认进入COPY/PORT候选。架构优先沿用原RuoYi模块和三层；现有Base分包可以替换，安全约束按行为保留。去除专有身份/数据/Secret/连接值，核所有权与来源，不因为来自旧项目而整块重写。迁移阶段忽略Codegen Skill，只有后续新增开发使用；先基础设施/系统设置，业务仅模块级清单。本ADR旧“只重建表职责”“旧页面不能迁”“无当前价值DROP”的内容均由此次用户方向覆盖。具体问题/建议/依赖见docs/development/MES_BASE_REALIGNMENT_REPORT.md。


## 2026-10-06 Source Product PORT 的实际落点

此前单模块、Department/Post/Notice/Online DROP、只重建官方字段、DB token、不能迁旧页面等取舍属于历史，已被 SourceProduct 全量基础系统授权覆盖。当前六模块及 Source Sys表/字段/分页/页面流程正式接入；旧简化链路默认关闭、旧表归档，不新建双主系统。

认证仍使用安全opaque Bearer；Redis按平台DB命名空间存哈希身份/版本，权限每次查DB，未照搬 JWT里持久化LoginUser授权快照。Source平台DataScope保留，FactoryScope/PLATFORM-FACTORY角色/Mapperownership/owner resource policy继续独立。成功协议可沿用Source AjaxResult/rows，失败维持真实HTTP+requestId。

Boot4无法直接运行Source旧Springfox/公共iframe：PORT到Springdoc3.0.3与Vue3SwaggerUI5.33.1。SDK默认MVC端点关闭，只登记显式tool:swagger:view控制器。暂停Quartz仍允许授权manual运行，所有入口共用DBclaim。源码/实际API/浏览器与部署证据分开，验收仍在进行；详情见当前功能矩阵和Phase1 JSON。
