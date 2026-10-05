# MES Base — Platform / Factory Foundation

一个独立创建的 Java 21 / Spring Boot 4.1.1 模块化单体。当前包含 Platform Foundation、Factory Foundation、Codegen 与相邻 Vue3 Base UI；尚无正式 MES 业务。

## 构建与验收

需要 Java 21、Maven 3.6.3+。在本目录运行：

```sh
mvn -B -ntp verify
python3 scripts/smoke-packaged.py
```

先将 `JAVA_HOME` / `PATH` 指向 Java 21；macOS 可用 `/usr/libexec/java_home -v 21` 找到安装位置。打包启动验证脚本也支持 `MES_JAVA_BIN=/path/to/java`。使用标准 Maven 配置及默认用户级依赖缓存（通常为 `~/.m2/repository`）；项目不创建 `.m2/.mvn` 或自定义 Maven mirror。代码、文档、测试与构建产物仍在本项目内。构建产物与原始验证日志在 `target/`。

## 启动

```sh
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --server.port=0
```

默认只绑定 loopback；端口默认 8080，`--server.port=0` 使用空闲端口，适合避免影响现有服务。默认无账号、无工厂、无业务库和 ERP 连接，`/health` 返回进程存活状态，其他接口按分类要求认证。

可显式开启本地合成 demo：先在当前进程环境自行设置不少于 12 字符的新 `MES_DEMO_PASSWORD`，再运行：

```sh
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo --server.port=0
```

Demo 有 A/B 两个合成 Factory，账号 `visitor` 没有角色，账号 `reader` 只拥有 A 厂的 Foundation 查看权限；二者密码都来自上述本地环境变量。没有固定默认密码、业务表或数据库连接。关闭 demo 后这些账号和工厂均不存在。

默认/demo 保留无会话 HTTP Basic 验收；显式开启 Platform 后增加数据库身份、可撤销 Bearer 会话与管理后台，角色为空不阻止认证。开放部署必须配置 HTTPS；SSO 尚未交付。

## HTTP 契约

成功返回明确 DTO 或 DTO 数组，错误使用真实 HTTP 状态及 `errorCode/message/requestId`；不采用 AjaxResult。所有返回都有服务端生成的 `X-Request-Id`，客户端同名值不决定服务端关联标识。

| 方法 | 路径 | 分类 | Factory 语义 |
| --- | --- | --- | --- |
| GET | `/health` | PUBLIC | 无 Scope，仅进程存活 |
| GET | `/api/foundation/factories?offset=0&limit=20` | AUTHENTICATED | 启用 Factory 选项，limit 最大 100 |
| GET | `/api/foundation/context` | AUTHENTICATED | 可返回空 factoryId；空角色可建立 Scope |
| PUT | `/api/foundation/preference` | AUTHENTICATED | `{"factoryId":"A"}`；只保存本人偏好 |
| GET | `/api/foundation/access` | AUTHORIZED | 当前厂 `foundation:access:read` |
| GET | `/api/foundation/erp/connection` | AUTHORIZED | 当前厂 `foundation:erp:inspect`；未配置明确 422 |
| ANY | `/error` | PUBLIC | 安全错误出口；直接访问返回 500 |

请求 `X-Factory-Id` 明确选厂；缺少时仅恢复仍存在且启用的本人偏好。没有首厂或编号 1 回退；AUTHORIZED 缺 Scope 返回 400，无当前厂 Permission 返回 403。业务库和 ERP 解析完全独立于选厂，不自动授予权限。

## 实现与边界

单个 Maven module，以 `dev.mesbase.core`、`platform`、`infrastructure`、`integration.erp`、`server` 划分职责。Core 不引用 Spring、ThreadLocal 或具体基础设施。

- Factory/不可变 Scope、独立 Actor/Role/Permission 契约、有效偏好恢复。
- 上下文嵌套/异常恢复、PLATFORM/FACTORY 任务、受控 executor 及 CallerRuns 身份/范围清理。
- Dedicated JDBC/MyBatis 运行时、明确 Mapper 归属、独立平台/业务 SqlSessionFactory 与事务管理器、版本与所有者校验、旧池按 lease 排空。
- 平台/业务结构化缓存键契约；未提供 Redis 或缓存存储。
- ERP NOT_CONFIGURED 与最小 Port；未实现供应商调用。
- Shared DB 只交付契约与测试计划，尚无共享库实现；不能将多个厂配置到独立库适配器的同一身份来替代它。

运行时注册为受信任的程序装配 API，不是面向客户端的数据源管理接口。默认 Spring 装配的 resolver 和 registry 都为空；需要独立配置适配器注册 pool、Mapper 和 Factory binding 后才能执行业务持久化。独立库用真实 H2 JDBC 的 TEST ONLY probe 验证，没有在主代码中创建 probe 表或模拟工单。

详细证据和限制见 [迁移报告](docs/reviews/2026-10-05-factory-foundation-migration-report.md)，决策见 [架构索引](docs/architecture/README.md)。
# mes-base

## Platform / Codegen / Agent 入口

- [Platform 配置与首次管理员](docs/development/platform.md)
- [Codegen CLI、metadata、生成与接入](docs/development/codegen.md)
- [mes-crud-codegen Skill](.agents/skills/mes-crud-codegen/SKILL.md)
- [本轮迁移与验收报告](docs/reviews/2026-10-05-platform-codegen-foundation-report.md)
- [下一阶段规划与新会话交接](docs/development/next-stage-plan.md)

标准 CRUD 从 AGENTS → Skill → `scripts/mes-codegen` 进入；生成后仍需 Scope/Permission/Resource/Domain/SQL/UI/测试审查。UI 位于相邻 `../mes-base-ui`，不是公司业务页面迁移。
