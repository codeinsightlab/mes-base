# MES Base

Java21 / Spring Boot4.1.1 的模块化单体，正在将 Source Product 的通用基础系统 COPY/PORT 到 Base。系统管理、系统监控、系统工具和已有 Factory Foundation 已接入；当前 Phase1 仍有浏览器验收边界，未迁正式 MES Domain，未部署。

## 当前入口

UI `http://127.0.0.1:15174` → 本轮后端 `127.0.0.1:18081`。用户请求的 admin 已经正式创建，具有显式 PLATFORM 角色；凭证不写文档。原 `15173/18080` 服务保留。

应用在本机，数据库是用户指定的远程测试 MySQL8.0.40，使用本轮独立的 `mes_base_phase1_*` Schema；Redis 是本轮私有实例 `127.0.0.1:16379`。这不是本地 MySQL 实例或生产证据。

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
| mes-system | SysUser/Dept/Post/Role/Menu/Dict/Config/Notice/Message/Attachment/AutoCode 的 Source 字段、Service、Mapper/XML |
| mes-framework | 认证、会话、验证码、密码锁、数据范围、审计、私有文件及 Factory 安全/持久化桥 |
| mes-quartz | Source 任务/日志、Cron、受控执行、数据库 claim/run token、恢复 |
| mes-generator | 既有生成实现保留，迁移阶段没有调用 Codegen Skill 或 CLI |
| mes-admin | 启动类 dev.MesBaseApplication、Controller、YAML 消费/对象装配及 Flyway |

UI 位于相邻 `../mes-base-ui`，Vue3/TypeScript/Router4/Pinia/Element Plus/Vite。Source 查询、字段、表单、组织岗位关联、角色菜单、字典参数、公告消息、日志、监控和工具页已 PORT；API 与实际浏览器完成项分别记录。

## 构建与本轮隔离启动

沿用用户 Java21 和标准 Maven，不创建项目 `.m2/.mvn` 或私有 mirror：

```sh
mvn -B -ntp verify
/usr/bin/python3 scripts/smoke-packaged.py
zsh -ic 'scripts/run-local-backend'
```

启动器默认端口18081，显式 dev 和已存在的 `target/phase1/application-phase1.yml`（忽略、0600），可用 `MES_LOCAL_PORT` / `MES_LOCAL_CONFIG` 指定。此受控文件包含本轮隔离连接、Redis、私有文件根和可信 Factory Runtime 映射，不应清空 target 后直接续跑。

工作区默认 profile/dev 连接有用户本轮调整；裸 `java -jar` 不等于已验收启动方式。本轮数据库测试显式使用隔离外部 YAML，未连接后来写入 dev 的其他 Schema。启动前先检查端口/进程所有权，不能停止原服务。打包产物含当前工作区配置，未作为部署包交付。

仅验证空装配时明确避开 dev/prod：

```sh
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --server.port=0
```

## 正式契约

Source 系统接口成功沿用 AjaxResult/TableDataInfo；Foundation、会话、私有文件按各自明确 DTO 返回。所有失败是真实 HTTP 状态及 `errorCode/message/requestId`，前端按真实契约适配。服务端生成 X-Request-Id，审计不采集请求体、凭证或令牌。

`/api/platform/session` 是正式登录/注销入口；`/api/system/**` 是唯一正式可写系统对象链路。旧简化 CRUD 默认关闭、旧表重命名归档保存。Redis 会话按平台数据库命名空间保存哈希键和身份/版本；权限每请求读取，无长寿命 Token 权限快照。

认证、选厂、平台权限、厂业务权限分别处理，无 User↔Factory 准入绑定，无 userId=1 特权。HTTP 显式分类；平台与业务 Mapper ownership 分开。缺 Scope/连接/版本不回退平台库或首库。文件下载/预览必须证明 owner+scope+resource，公告图片绑定真实 noticeId；无公共裸 URL。

Quartz 保留 Cron/启停/手动运行/日志。暂停只禁止自动触发，授权用户可执行一次；目标是受控登记任务，执行须 DB claim/run token。多节点 cron fire 永久去重与集群 JobStore 未交付。Shared DB、ERP 实际调用、微信/PDA渠道及正式 MES Domain 未自动扩展。

## SQL 与来源

Flyway 为 platform V1 + system V2–V9。导出命令直接合并当前迁移，不另维护第二套字段：

```sh
/usr/bin/python3 scripts/export-phase1-sql.py
```

输出 `target/phase1/sql/mysql-phase1.sql` 和 SHA256 清单。只用于空的隔离库，包含通用产品菜单/字典，无账号或公司业务数据。手工导入不写 Flyway 历史，后续须 `mes.platform.migrate=false`；优先空库应用 Flyway 初始化。许可与逐文件来源位于 docs/legal，Source 仓库只读。
