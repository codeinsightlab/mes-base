# 文档导航与维护边界

产品定位、能力和运行准备先看[项目README](../README.md)。这里区分当前指南、工程资产与历史证据，避免将一次验收或旧阶段记录当成当前操作说明。

## 按任务阅读

| 任务 | 阅读入口 |
| --- | --- |
| 了解产品和后续工作 | [README](../README.md)、[当前规划](development/next-stage-plan.md) |
| 配置平台、账号与工厂 | [平台说明](development/platform.md)、[请求准入与事务](development/request-admission.md) |
| 理解业务数据空间和来源 | [架构入口](architecture/README.md)、[LOCAL/ERP边界](architecture/local-erp-business-boundary.md) |
| 使用生产、BOM与库存 | [投产与BOM](development/production-input.md)、[库存规则](development/warehouse-inventory.md) |
| 开发业务模块 | [代码生成器](development/codegen.md)、[主子单据](development/master-detail-documents.md)、[业务目录](development/business-directory.md)、[缓存](development/business-cache.md) |
| 升级与维护 | [数据库升级](development/database-upgrades.md)、[数据维护证据边界](operations/data-maintenance/README.md) |
| 维护双托管 | [GitHub/Gitee说明](development/dual-hosting.md)；贡献主线约定以维护者最新决定为准 |
| 查来源和授权 | [许可与来源](legal/)；根目录LICENSE、NOTICE继续生效 |

部分现有指南仍混有日期化历史章节，尚未完成内容拆分；读取具体规则时须结合后续用户决定、适用AGENTS和当前代码，不能机械执行旧命令。导航不代表这些长文已全部收敛。

## 目录职责

- `development/`：当前开发/业务用法；现有历史段落拟单独归档，配置资产拟按用途分组。
- `architecture/`：当前架构入口和有时间线的决策。原决策保留，入口只描述当前有效方案。
- `legal/`：许可证、来源清单和归属证据，独立保留。
- `reviews/`、`migration/`：专项审查、迁移和验收历史，不作为正常安装或运行入口。
- `operations/data-maintenance/`：已执行维护及核对回执，SQL属于事实证据，不是可重复运行的通用工具。
- `generated/`、`prototypes/`：生成报告和试验资产，须标明适用阶段，不作为正式升级入口。

## 2026-10-24：文档与脚本收敛审查

Owner：项目维护者。范围为两仓已跟踪文档、后端脚本、声明和引用，以及生成器实际入口；仅静态审查，不执行脚本、代码生成、数据库操作或业务验收。当前任务不移动/删除资产、不改工程规则及生成器实现。README补充RuoYi底座来源与生成器重构介绍。

### 基线与主要问题

后端审查时有173个已跟踪docs文件（约8.4MB），其中106份Markdown，reviews占83个文件，development占41个且包含22份YAML配置。scripts已跟踪21个文件，其中包含README和SQL，并非21个可执行工具。前端仅docs/development.md被跟踪；磁盘中大量截图、日志及验收附件被忽略，不等于公开仓库内容。

- 当前指南混合历史：codegen.md为936行、platform.md为638行，旧阶段与后续修订并存。架构入口仍有“六模块/基础阶段”等旧描述，与当前七模块及MES业务不一致。
- 当前产品状态散在多个审查、迁移矩阵和规划文件中。同一事实的旧结论与后续修正需要读者自行拼接。
- 正式配置与一次生成配置混放：22份development YAML不能一律当作当前推荐模板，也不能未经引用审查一起移动。
- 旧工具仍可被误用：历史导入SQL、旧生成入口和过期性能流程与正式工具放在同一个scripts树。
- 独立SQL导出不是完整初始化方式：export-phase1-sql.py只收集SQL，当前system目录缺V34 SQL、对应迁移是Java类；其连续版本检查会拒绝当前集合。此为静态推演，未执行导出，也未改迁移。

### 建议收敛方式（尚未批量实施）

1. 公共入口按任务组织，集中到本导航中的几个主题；主题正文只维护当前用法、边界和必要实例。
2. 将长文中的日期化过程、命令回执和旧版本结论完整转入历史区域；保留日期、证据等级、来源链接及已取代标识，不改写原结论。先处理codegen.md、platform.md、架构README和规划的当前摘要。
3. 当前路线图只由next-stage-plan.md的有效摘要维护，旧迁移矩阵与阶段报告退出当前任务清单。该文件目前有其他任务未提交修改，不在本轮覆盖。
4. 保留一个正式生成器使用指南；项目实际配置与示例配置分开，过往单次配置留作证据。迁移路径时同步更新AGENTS、Skill、脚本/配置及测试引用。
5. 历史报告先改为非推荐阅读入口；是否整体迁入archive及是否从默认开源发行包排除，另按引用清单实施。仅搬目录不会减少文件体积。
6. 新增文档必须有新主题或独立操作用途；小修复更新当前指南，验收只补必要差异，不复制整份规划或另造同主题报告。本轮不另建独立审查报告。

### 脚本逐项处置建议

以下是审查建议，不是已经删除/修复的状态。不能因少引用而直接判定工具无用；正式升级、来源和证据边界继续保留。

| 当前文件 | 建议 | 依据/后续检查 |
| --- | --- | --- |
| scripts/mes-business-codegen | 保留为正式入口 | 独立generator CLI、显式配置，当前AGENTS/Skill及业务配置引用 |
| scripts/mes-business-flyway | 保留 | 正式业务库info/validate/migrate；不归入旧系统一次性导入 |
| scripts/mes-platform-admin | 保留 | 空库管理员初始化，须继续保留目标和空表检查 |
| scripts/smoke-packaged.py | 保留 | 打包产物检查，与业务验收分开 |
| scripts/check-private-config.py | 保留 | 私有配置/显式凭据模式检查，需说明检查范围 |
| scripts/tests/test_private_config.py | 保留 | 上述检查器的合成测试 |
| scripts/format-java | 保留并标注环境前提 | 当前使用本机IntelliJ格式化器，新贡献者不一定具备同路径环境 |
| scripts/run-local-backend | 开发辅助，非产品必需 | 本地启动脚本，不自动启停用户服务；主README保留标准Java启动 |
| scripts/run-local-redis | 开发辅助，非通用安装器 | 依赖.local中的Redis二进制和配置，不能当新机器开箱即用命令 |
| scripts/mes-codegen | 退役候选，须联动检查 | 旧CLI依赖应用JAR且可隐式package，与正式独立CLI并存；检查CodegenCli用途、测试、配置及Skill后处理 |
| scripts/clean-empty-development-archives | 一次维护工具退役候选 | 固定旧开发/生产测试库名，仅用于旧空归档清理，不作为正常运维入口 |
| scripts/export-phase1-sql.py | 退役或正式重设范围 | 不含Java V34，当前连续版本检查不通过；sql/README也仍按旧十版本描述，不能宣称导出可初始化当前系统 |
| scripts/migration/ktg-mes/README.md | 历史说明归档候选 | 不是当前数据转换授权，需保留证据和原引用 |
| scripts/migration/ktg-mes/simple-base.sql | 一次性导入退役候选 | 仍使用旧factory_id等结构；按已确认一次性导入退役规则清理写入口，不恢复兼容 |
| scripts/migration/ktg-mes/operation-routing.sql | 一次性导入退役候选 | 旧离线转换SQL，不能作为当前工艺升级入口 |
| scripts/migration/ktg-mes/equipment-subject.sql | 一次性导入退役候选 | 旧副本导入写入口，与正常设备管理无关 |
| scripts/performance/local-production-chain.py | 停用旧流程，重建基准另立任务 | 仍调用旧receipts、production-acceptances和显式complete流程，投产码也为旧格式；无法验收当前入库审核收尾 |
| scripts/performance/audit-production-chain.py | 与基准一起复核 | 历史性能数据核查脚本，不先认定当前Schema适用 |
| scripts/performance/production-core-readonly.py | 历史环境探针归档或参数化 | 固定测试工厂、主机和库断言；只读不等于适用于任意部署 |
| scripts/performance/read-production-chain.py | 与基准一起复核 | 依赖旧性能journal和记录分布，不因无文本引用直接删除 |
| scripts/performance/summarize-production-chain.py | 可保留报告处理能力 | 汇总已有样本；确认输入格式后与历史写入流程分开维护 |

上述路径在此按原名列出便于检索，未把旧SQL或写入性能脚本改列为推荐命令。

### 完成收敛的验收条件

- 首次读者能从README与本导航找到安装、开发、升级和当前规则，不必遍历验收时间线。
- 每个当前主题只有一个权威说明，历史保留完整出处和取代关系。
- 所有被移动/退役文件的AGENTS、Skill、配置、源码、测试、构建与文档链接完成核查；不留下可误执行的旧入口。
- 不删除许可证、来源、正式Flyway迁移、维护回执或业务事实；不以目录整理为理由重写历史迁移。
- 文档链接、配置/脚本引用和适用检查通过，真实执行与静态证据分别报告。
