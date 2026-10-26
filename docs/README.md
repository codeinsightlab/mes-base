# Base MES 文档

公共文档围绕四件事组织：**如何使用、开发、部署和维护 Base MES**。只有可复用、与当前产品有关、对其他使用者有意义的内容进入版本控制。

| 目的 | 阅读入口 |
| --- | --- |
| 了解产品和范围 | [项目介绍](../README.md)、[路线图](roadmap.md) |
| 使用生产和仓储 | [生产流程](usage/production.md)、[库存与审核](usage/inventory.md) |
| 开发与扩展 | [生成器](development/codegen.md)、[主子单据](development/master-detail-documents.md)、[物料数量规则](development/quantity-rules.md)、[共享目录](development/business-directory.md)、[缓存](development/business-cache.md)、[列偏好](development/table-preferences.md)、[Java格式](development/java-code-style.md) |
| 部署与维护 | [平台配置](development/platform.md)、[数据库升级](development/database-upgrades.md) |
| 理解安全和集成 | [当前架构](architecture/README.md)、[请求与事务](development/request-admission.md)、[LOCAL/ERP边界](architecture/local-erp-business-boundary.md)、[E10接入](development/digiwin-e10-consumer.md) |
| 贡献和同步代码 | [贡献指南](../CONTRIBUTING.md) |
| 查授权与来源 | 根目录LICENSE、NOTICE及[来源声明](legal/) |

## 什么应当提交

- 当前使用说明、开发契约、部署维护步骤，以及确有长期价值的设计说明。
- 可复用的示例、测试夹具和工具配置属于工程资产，放到config或测试/工具目录；不是所有YAML、JSON、SQL都属于文档。
- 生成器配置位于[config/codegen](../config/codegen/)，不自动执行。正式数据库版本仅由Flyway维护。

## 什么不应当提交

本机代理、个人端口/路径、开发账号、指定测试库排障、一次性验收回执、截图/日志、对话交接和阶段流水账。它们保存在Git忽略的 `.local/knowledge/` 或自有证据目录；已有本地材料可以继续保留，但不作为公共指南的依赖。

不通过把开发流水账改名为archive继续公开。公共说明直接更新当前规则；本地保留原始证据。对已发布Git历史不做自动重写，来源许可和正式迁移不因文档精简而移除。
