> 历史归档：2026-10-24从 `docs/architecture/README.md` 拆出。下文保留原阶段事实和决定；旧命令、接口及配置不是当前运行入口，部分工具已退役。当前用法见[文档导航](../../README.md)。

# MES Base 架构决策

2026-10-09 当前入口：六个 JAR 模块 common/system/framework/quartz/generator/admin；仅中心库 + 按 Factory 隔离的独立业务库，两套 DataSource/SqlSessionFactory/TransactionManager。无外部 JDBC 路由或 Shared 实现。以根 README、AGENTS §9 和 ADR-004 最后决定为准。先完善 Base，生成器/UI 对齐在业务接入阶段处理。下方日期化记录保留追溯，不能当作当前装配。

2026-10-05，状态 Accepted。本阶段仅基础能力，部署及生产验收另行进行。

| 决策 | 内容 |
| --- | --- |
| [ADR-001](../../architecture/ADR-001-factory-scope.md) | Factory 业务身份、Scope、执行桥与任务生命周期 |
| [ADR-002](../../architecture/ADR-002-factory-runtime-binding.md) | 运行时身份/版本、事务固定、池替换与 Mapper ownership |
| [ADR-003](../../architecture/ADR-003-factory-permission-model.md) | 认证、选厂、权限、偏好和 HTTP 分类 |
| [ADR-004](../../architecture/ADR-004-business-persistence-topology.md) | Dedicated 实现、Shared 契约与验收计划 |
| [ADR-005](../../architecture/ADR-005-erp-integration-boundary.md) | 历史外部连接决定（2026-10-09 被 ADR-004 取代） |

2026-10-06 Source Product PORT 后采用一个 Maven reactor、六个模块和一套 Core，不拆微服务。mes-admin/common/framework/system/quartz/generator 沿用 Source 模块职责；具体目录以根 README 为准。`server` 负责装配与 HTTP 协议；`core` 定义业务身份、范围与授权 Port；`platform` 是安全/错误/执行桥；`infrastructure` 实现本地目录、缓存键和 JDBC/MyBatis；`integration.erp` 定义独立 ERP Port。Core 不依赖这些实现。

原 Factory 阶段的验收边界见既有报告；本轮新增 Platform 管理、身份会话、Quartz 管理与 Vue3 UI，见 ADR-006 和 Platform/Codegen 报告。本轮已连接用户指定远程测试 MySQL 的隔离 Schema 和私有 Redis。仍无 MES 同步领域、实际ERP调用、持久化 Quartz集群JobStore或生产验收。

- [ADR-006 — Platform / Web Base 与来源](../../architecture/ADR-006-platform-and-web-base.md)

## 2026-10-06 历史结构与瘦身审计（已被 2026-10-09 决定取代）

上面的六模块及 `integration.erp` Port 描述是早期迁入记录，已由当前代码取代。当前是七个 JAR 模块：common/system/framework/quartz/generator/mes/admin；生产 Mapper 按固定包绑定 platform/business/erp 三组工厂，ERP 多连接由 `@ErpSource` 与独立路由数据源负责。
未接入的 Enterprise/Shared 占位已删除，ADR-001/004 保留历史与未来验收边界。真实隐式请求链、Bean/Context/依赖清单、删减证据及待决事项见 [Architecture Slimming Audit](../../architecture/architecture-slimming-audit.md)。

## 2026-10-09 当前持久化结构

中心库 masterSqlSessionFactory/masterDataSource/masterTransactionManager；多业务库共用 businessSqlSessionFactory/businessDataSource/businessTransactionManager，按 Factory 路由独立池。ERP/HR/WMS/其他外部 JDBC 框架及模板退出 Base。此前三工厂说明属于历史快照，当前规范见 ADR-004 和 AGENTS 第 9 节。
