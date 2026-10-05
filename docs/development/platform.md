# Platform Foundation 操作边界

2026-10-05。默认 disabled；Foundation 默认/demo 验收方式保持。显式 MES_PLATFORM_ENABLED=true、MES_PLATFORM_URL/USER/PASSWORD、MES_FILE_ROOT（私有绝对路径）；可选 MES_PLATFORM_MIGRATE=true 执行 clean Flyway。当前 PostgreSQL 驱动随 JAR，此前Schema验收在合成H2，现已完成指定MySQL8.0.40独立开发库验收（报告第14节），仍非部署/生产验收。FactoryCatalog / BusinessRuntimeBinding 默认仍为空，显式开启Runtime从YAML受信任装配，不由 Platform CRUD 自动注册生产工厂/库。

## 首次管理员与登录
平台空 Schema 不预置账号、角色、菜单。设置受控环境 MES_BOOTSTRAP_PASSWORD（不少于12字符），运行 `scripts/mes-platform-admin --username=<new-name> [--migrate]`；CLI 在平台库事务检查空用户表，首次建立 UUID 平台账号、显式 PLATFORM role 和当前平台管理菜单/权限，无 wildcard、无 factory grant、无 userId1 特权。重复执行拒绝；脚本不打印密码。

POST /api/platform/session 使用 username/password，返回 accessToken/expiresAt；Bearer 为随机 opaque token，DB 只保存 SHA256，TTL/注销/禁用/密码重置撤销，权限每请求读取。DELETE session 注销。无角色用户可认证、选择启用厂、AUTHENTICATED 成功；当前厂未授权的 AUTHORIZED 403。现有 Basic Foundation 路径保留，本地可用；对外部署需 TLS。UI token 只存内存，刷新重新登录。

## 管理与范围
/api/platform 用户、角色、菜单、字典、配置、Jobs、操作/登录/执行日志使用独立 PLATFORM 权限。user-role/role-menu 原子替换最大200；页量最大100。角色为 PLATFORM/FACTORY，厂角色必须有效厂。navigation?scope=PLATFORM 无厂权限污染；默认 FACTORY 按显式当前范围读取。系统表 never Business Mapper。

Dict/Config 修改事务 bump 数据库 catalog revision，缓存每次读取检查版本，多进程不会永久保留旧值。Config kind=GLOBAL/FACTORY 与 scope_key 唯一，Factory 优先、GLOBAL fallback 是配置读取明确语义；secret/password/token 等 key 禁止，敏感配置来自环境，不进普通配置表。

Quartz 管理持久化 Job 与日志，时间调度为内存 Quartz；任务来自注册 handler allowlist，禁止任意反射 bean/class。内置 platform-heartbeat / factory-heartbeat 仅底座测试，不是业务同步。每次运行 DB 条件 claim + unique token，结束校验 token，失败记录 UNKNOWN 并传播。正在执行时不允许修改/删除；多节点同时 claim 不会并行运行，但 RAM trigger 不提供跨节点同一 cron fire 的永久去重/集群 misfire 协调。当前不自动把运行超过10分钟当作死亡；节点崩溃遗留 token 需确认原 worker 已停止，由运维按 id+原token 条件清理并记录 UNKNOWN 后再调度，不能只按超时释放活跃任务。上线长期业务任务前应单独交付 lease/heartbeat/恢复和重复 fire 幂等策略。

私有文件最大10MB，上传/元信息/下载验证 ownerId、可选厂范围与 resource。默认仅 personal；未来业务 fileId 必须注册服务端 FileResourcePolicy 证明所属资源，不能把客户端资源名当权限。LocalPrivateFileStorage 使用 UUID 存储路径、拒绝 symlink/路径穿越，FileStorage port 可适配对象存储；不公开静态 URL。

审计记录 requestId/actorId/factoryId/executionScope/route pattern/result，不记录请求体/密码/token。业务成功与审计失败区分；本地事务与文件系统或外部效果不构成分布式事务。正式留存/清理、SSO/限流与目标部署验收按后续明确需求实施。

UI 相邻 mes-base-ui，Node22，`npm ci && npm run test && npm run build`；本地 dev `/api` proxy 127.0.0.1:8080，不内置身份与业务库。授权菜单可注册已编译 CRUD 视图，不能从服务端任意 URL 导入模块。实际资源约束以后台为准。


## YAML 与 dev/prod 配置（2026-10-05）

主入口为 `src/main/resources/application.yml`；默认profile为dev，`application-dev.yml`仅配置本地环境行为，`application-prod.yml`由显式profile启用且要求外部提供平台连接/凭证/文件根。生产迁移默认关闭，不用dev连接兜底。两种profile都不会内置账号、工厂连接或默认业务库。

端口、上传/会话边界、Platform/Business池参数、迁移开关与Codegen开发profile均在YAML。受控外部YAML用 `--spring.config.additional-location=file:/absolute/config.yml` 加载，连接凭证不进入仓库。Runtime列表结构见 `config/application-runtime.example.yml`；该示例只表达配置形状，无凭证。生产凭证通过环境或受控外部文件注入，不能将测试库写进application-prod.yml。

`mes.runtime.enabled=true`时通过通用FactoryRuntime装配器读取YAML的`mes.runtime.factories`，沿用现有resolver/registry/persistence；Java只负责对象装配、pool关闭、物理库唯一性和Mapper ownership校验，不保存环境数据。现阶段物理库验证适配MySQL，不能据此称PostgreSQL Runtime已完成。开启Runtime必须同时启用Platform，不能与synthetic demo混用。可信Mapper由代码注册，不接受YAML中的任意class名/扫描包。

当前本轮外部配置在忽略的`target/local-dev/application-local.yml`（0600），由 `zsh -ic 'scripts/run-local-backend'` 使用；必须先用用户Java21/Maven完成构建。该目录是开发运行资料，不可在准备验证前清空target；凭证需要重新建立时由用户提供受控来源。前端支持 `MES_DEV_API_TARGET=http://127.0.0.1:<backend-port>`，仅loopback，默认8080不变；本轮使用18080与15173，不触碰未知服务。
