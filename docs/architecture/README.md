# MES Base 架构决策

2026-10-05，状态 Accepted。本阶段仅基础能力，部署及生产验收另行进行。

| 决策 | 内容 |
| --- | --- |
| [ADR-001](ADR-001-factory-scope.md) | Factory 业务身份、Scope、执行桥与任务生命周期 |
| [ADR-002](ADR-002-factory-runtime-binding.md) | 运行时身份/版本、事务固定、池替换与 Mapper ownership |
| [ADR-003](ADR-003-factory-permission-model.md) | 认证、选厂、权限、偏好和 HTTP 分类 |
| [ADR-004](ADR-004-business-persistence-topology.md) | Dedicated 实现、Shared 契约与验收计划 |
| [ADR-005](ADR-005-erp-integration-boundary.md) | 独立 ERP 连接边界与未配置失败 |

采用一个 Maven 工程与 package 边界，暂不拆模块或微服务。`server` 负责装配与 HTTP 协议；`core` 定义业务身份、范围与授权 Port；`platform` 是安全/错误/执行桥；`infrastructure` 实现本地目录、缓存键和 JDBC/MyBatis；`integration.erp` 定义独立 ERP Port。Core 不依赖这些实现。

原 Factory 阶段的验收边界见既有报告；本轮新增 Platform 管理、身份会话、Quartz 管理与 Vue3 UI，见 ADR-006 和 Platform/Codegen 报告。仍无真实外部连接、MES 同步领域、持久化 Quartz 集群 JobStore 或生产验收。

- [ADR-006 — Platform / Web Base 与来源](ADR-006-platform-and-web-base.md)
