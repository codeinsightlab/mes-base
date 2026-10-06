# MES Base

Java21 / Spring Boot4.1.1 的模块化单体，正在将 Source Product 能力 COPY/PORT 到 Base。系统管理、系统监控、系统工具、工厂/数据源管理与同步协调已接入；生产监控和人员分析代码已迁入，当前业务 Schema、真实 SQL 与页面验收尚未完成，未部署。

## 当前入口

UI `http://127.0.0.1:15174` → 本轮后端 `127.0.0.1:18081`。用户请求的 admin 已经正式创建，具有显式 PLATFORM 角色；凭证不写文档。原 `15173/18080` 服务保留。

应用在本机，数据库是用户指定的远程测试 MySQL8.0.40：`mes_base_dev` 为系统开发库，`mes_base_dev_business` 为独立工厂业务库。Redis 使用 `127.0.0.1:16379` 的 DB 1。真实连接保存在 Git 忽略且权限为0600的 `mes-admin/src/main/resources/application-dev.yml`，不随 Maven target 清理。

- [当前规划与续接入口](docs/development/next-stage-plan.md)
- [迁移报告、实际命令和失败](docs/development/MES_BASE_REALIGNMENT_REPORT.md)
- [逐功能迁移矩阵](docs/development/MES_MIGRATION_MATRIX.md)
- [实际验收记录](docs/reviews/2026-10-06-phase1-verification.json)
- [配置和操作边界](docs/development/platform.md)
- [SQL 导出入口](sql/README.md)

## 模块与页面

| 模块 | 当前职责 |
| --- | --- |
| mes-common | Source 公共对象、Excel、Redis、字典及字符串等工具 |
| mes-system | SysUser/Dept/Post/Role/Menu/Dict/Config/Notice/Message/Attachment/AutoCode、OpenAPI 客户端的 Source 字段、Service、Mapper/XML |
| mes-framework | 认证、会话、验证码、密码锁、数据范围、审计、私有文件及 Factory 安全/持久化桥 |
| mes-quartz | Source 任务/日志、Cron、受控执行、数据库 claim/run token、恢复 |
| mes-generator | 既有生成实现保留，迁移阶段没有调用 Codegen Skill 或 CLI |
| mes-mes | Source 生产监控和人员分析；业务 Schema 验收前默认关闭 |
| mes-admin | 启动类 dev.MesBaseApplication、Controller、YAML 消费/对象装配及 Flyway |

UI 位于相邻 `../mes-base-ui`，Vue3/TypeScript/Router4/Pinia/Element Plus/Vite。Source 查询、字段、表单、组织岗位关联、角色菜单、字典参数、公告消息、日志、监控和工具页已 PORT；API 与实际浏览器完成项分别记录。

## 构建与 dev/prod 启动

沿用用户 Java21 和标准 Maven，不创建项目 `.m2/.mvn` 或私有 mirror：

```sh
mvn -B -ntp verify
/usr/bin/python3 scripts/smoke-packaged.py
scripts/run-local-redis
# 另一终端
scripts/run-local-backend
```

实际环境参数直接写在 `mes-admin/src/main/resources/application-dev.yml` 和 `application-prod.yml`，没有环境变量占位符或config.import转接。默认dev，prod通过 `--spring.profiles.active=prod` 激活。两个真实配置在本机保留、权限0600，并已取消Git跟踪；新机器从config/application-dev.example.yml与application-prod.example.yml复制到相应资源路径并填入连接，再构建。

```sh
# dev已在18081运行；先构建再启动以应用资源配置修改
scripts/run-local-backend
# prod配置默认8080；当前连接同一测试实例的独立prod测试库
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

dev系统/业务库为mes_base_dev / mes_base_dev_business，prod测试系统/业务库为mes_base_prod / mes_base_prod_business，分别使用独立账号、DEV/PROD厂、Redis DB1/DB2和文件目录。初始管理员凭证分别在.local/development-admin.json与.local/production-test-admin.json；prod仍启用验证码和12位密码。Redis16379由scripts/run-local-redis启动。本地JAR包含本机环境配置，只作本地运行产物，本轮未部署生产。

工厂与业务连接由系统库 `mf_factory` / `mf_datasource` 管理，不再在 YAML 创建。配置仍直接填写平台库、Redis、文件目录及 `mes.runtime.datasource-key`（32 字节 Base64 密钥）；业务密码加密保存，更换密钥必须迁移密文。新增管理权限需显式分配，平台角色不会自动获得工厂业务权限。`mes.analytics.enabled=false`，待当前业务表和状态字典核对后再启用。

仅验证空装配时明确避开 dev/prod：

```sh
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --server.port=0
```

## 正式契约

Source 系统接口成功沿用 AjaxResult/TableDataInfo；Foundation、会话、私有文件按各自明确 DTO 返回。所有失败是真实 HTTP 状态并带 requestId；内部错误用 `errorCode/message/requestId`，外部 OpenAPI 沿用 Source 的 `errorCode/msg/data/requestId`，前端按真实契约适配。服务端生成 X-Request-Id，审计不采集请求体、凭证或令牌。

`/api/platform/session` 是正式登录/注销入口；`/api/system/**` 是唯一正式可写系统对象链路。旧简化 CRUD 默认关闭、旧表重命名归档保存。Redis 会话按平台数据库命名空间保存哈希键和身份/版本；权限每请求读取，无长寿命 Token 权限快照。

认证、选厂、平台权限、厂业务权限分别处理，无 User↔Factory 准入绑定，无 userId=1 特权。HTTP 显式分类；平台与业务 Mapper ownership 分开。缺 Scope/连接/版本不回退平台库或首库。文件下载/预览必须证明 owner+scope+resource，公告图片绑定真实 noticeId；无公共裸 URL。

Quartz 保留 Cron/启停/手动运行/日志。暂停只禁止自动触发，授权用户可执行一次；目标是受控登记任务，执行须 DB claim/run token。多节点 cron fire 永久去重与集群 JobStore 未交付。Shared DB、ERP 实际调用、微信/PDA渠道及正式 MES Domain 未自动扩展。

## 外部 OpenAPI

`/system/openApiClient` 是 Source 应用管理页，`/api/system/open-api/clients` 是后台管理接口。保留源表 open_api_client / open_api_client_api 的字段、默认禁用、一次性 Secret、启停/重置递增凭证版本、分组接口授权。Source 工艺范围管理后置，没有复制 ProRoute。

`POST /open-api/token` 用 appKey/appSecret 换外部 Token，`POST /open-api/token/revoke` 撤销当前 Token；`/open-api/v1/diagnostics/**` 与 verification/** 为实际通用诊断接口。外部 Token 不取得后台用户或 Factory 权限；API 授权和当前客户端状态每次请求读取。Redis 使用平台 DB 身份命名空间，支持 Source 的有效期内重复申请复用/续期。

YAML `open-api.data-scope.enabled` 默认 false（环境变量 MES_OPEN_API_DATA_SCOPE_ENABLED）。开关只控制业务数据范围扩展，保留认证/API 授权；启用必须提供唯一 OpenApiDataScopeValidator，否则启动失败。未来 MES 可按工艺、仓库、产品等实际规则实现，不绑定工艺概念，也不能用此开关放宽 Factory/Mapper/resource ownership。完整配置与响应契约见 [Platform 指南](docs/development/platform.md)。

## SQL 与来源

Flyway 为 platform V1 + system V2–V14；V11 为工厂/数据源，V12 为同步协调，V13 为管理与分析权限菜单，V14 按源库实际结构扩大头像字段。导出命令直接合并当前迁移，不另维护第二套字段：

```sh
/usr/bin/python3 scripts/export-phase1-sql.py
```

输出 `target/phase1/sql/mysql-phase1.sql` 和 SHA256 清单。只用于空的隔离库，包含通用产品菜单/字典，无账号或公司业务数据。手工导入不写 Flyway 历史，后续须 `mes.platform.migrate=false`；优先空库应用 Flyway 初始化。许可与逐文件来源位于 docs/legal，Source 仓库只读。
