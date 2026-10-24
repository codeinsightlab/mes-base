# 文档导航

先看[项目介绍](../README.md)，再按任务选择下列指南。当前规则与历史证据分开维护；具体开发仍须遵守仓库AGENTS。

| 任务 | 当前入口 |
| --- | --- |
| 查看产品状态与计划 | [项目README](../README.md)、[下一阶段计划](development/next-stage-plan.md) |
| 安装配置、账号与保留工具 | [平台指南](development/platform.md) |
| 理解数据空间、权限与事务 | [当前架构](architecture/README.md)、[请求准入](development/request-admission.md)、[LOCAL/ERP边界](architecture/local-erp-business-boundary.md) |
| 理解生产与库存 | [投产与BOM](development/production-input.md)、[库存](development/warehouse-inventory.md) |
| 开发业务 | [生成器](development/codegen.md)、[主子单据](development/master-detail-documents.md)、[业务目录](development/business-directory.md)、[缓存](development/business-cache.md) |
| 升级与维护 | [数据库版本](development/database-upgrades.md)、[维护证据](operations/data-maintenance/README.md) |
| 查来源和授权 | [来源清单](legal/)、根目录LICENSE和NOTICE |
| 查历史 | [归档入口](archive/README.md)、[专项审查](reviews/)、[早期迁移证据](migration/) |

## 维护约定

- 一个当前主题保留一个权威说明。正文写现行流程、边界和必要实例；小修复只更新差异，不复制整份规划。
- 日期化调查、测试回执和旧方案进入历史区域，保留日期、出处及取代关系；历史命令不是当前操作入口。
- `development`中的业务生成配置不等于可直接执行的通用模板；使用前显式核对目标、选表和写入模式，不在本轮移动这些仍被AGENTS/配置/测试引用的资产。
- `operations/data-maintenance`中的已执行SQL和回执是证据，正式Flyway迁移独立保留；不将二者合并成可重复运行的安装脚本。
- `generated`、`prototypes`和旧迁移矩阵保留各自证据范围，不作为产品能力或待办的第二权威来源。

本轮已收敛生成器、平台和架构入口。生产专题、ADR、规划和升级说明仍有历史章节，后续在对应主题变更时继续整理，不宣称全量文档已清理。
