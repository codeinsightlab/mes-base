# MES Base 架构决策

2026-10-05，状态 Accepted。本阶段仅基础能力，部署及生产验收另行进行。

| 决策 | 内容 |
| --- | --- |
| [ADR-001](ADR-001-factory-scope.md) | Factory 业务身份、Scope、执行桥与任务生命周期 |
| [ADR-002](ADR-002-factory-runtime-binding.md) | 运行时身份/版本、事务固定、池替换与 Mapper ownership |
| [ADR-003](ADR-003-factory-permission-model.md) | 认证、选厂、权限、偏好和 HTTP 分类 |
| [ADR-004](ADR-004-business-persistence-topology.md) | Dedicated 实现、Shared 契约与验收计划 |
| [ADR-005](ADR-005-erp-integration-boundary.md) | 独立 ERP 连接边界与未配置失败 |

2026-10-06 Source Product PORT 后采用一个 Maven reactor、六个模块和一套 Core，不拆微服务。mes-admin/common/framework/system/quartz/generator 沿用 Source 模块职责；具体目录以根 README 为准。`server` 负责装配与 HTTP 协议；`core` 定义业务身份、范围与授权 Port；`platform` 是安全/错误/执行桥；`infrastructure` 实现本地目录、缓存键和 JDBC/MyBatis；`integration.erp` 定义独立 ERP Port。Core 不依赖这些实现。

原 Factory 阶段的验收边界见既有报告；本轮新增 Platform 管理、身份会话、Quartz 管理与 Vue3 UI，见 ADR-006 和 Platform/Codegen 报告。本轮已连接用户指定远程测试 MySQL 的隔离 Schema 和私有 Redis。仍无 MES 同步领域、实际ERP调用、持久化 Quartz集群JobStore或生产验收。

- [ADR-006 — Platform / Web Base 与来源](ADR-006-platform-and-web-base.md)

## 2026-10-06 当前结构与瘦身审计

上面的六模块及 `integration.erp` Port 描述是早期迁入记录，已由当前代码取代。当前是七个 JAR 模块：common/system/framework/quartz/generator/mes/admin；生产 Mapper 按固定包绑定 platform/business/erp 三组工厂，ERP 多连接由 `@ErpSource` 与独立路由数据源负责。
未接入的 Enterprise/Shared 占位已删除，ADR-001/004 保留历史与未来验收边界。真实隐式请求链、Bean/Context/依赖清单、删减证据及待决事项见 [Architecture Slimming Audit](architecture-slimming-audit.md)。
