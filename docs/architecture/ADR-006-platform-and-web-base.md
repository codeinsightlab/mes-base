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
Scaffold 必须继续经过 UI review 与优化，不自动注册生成路由或给角色赋权。

## 拒绝与后果
不迁旧 FactoryContext、动态库兜底、公司 profile、AjaxResult failure、反射任意 Bean job、公共 URL 文件、Redis Factory 权限缓存。
Department / Post / Notice / 在线用户 / Monitor 等 P1 暂无本轮必要价值，DROP；未来需求需独立决定。
Quartz 引擎复用，调度仅管理时间；DB 抢占 + run token，任务名 allowlist，显式范围。
当前允许的内置 heartbeat 是平台验证入口，不代表 ERP / MES 同步已经实现；外部命令必须另定义 UNKNOWN、幂等和恢复。
