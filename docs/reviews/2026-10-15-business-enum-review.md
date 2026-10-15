# Business 业务枚举审查与替换

审查日期：2026-10-15。范围：`mes-business/src/main/java/dev/mesbase/business` 全包与相应 Mapper XML。

## 源码事实与实施

HTTP Controller → 业务 Service / Rules → 生成 Mapper / Criteria 或 WorkOrderExtraMapper 是本轮检查的调用链。原硬编码集中在判断、赋值、合法值集合、ERP事实转换与关联查询；现有 ERP 结果中的四个嵌套 enum 缺少稳定 value、中文说明及反向转换。

本轮新增 12 个枚举，并完善 4 个既有嵌套枚举：

| 范围 | 枚举 | 用途 |
| --- | --- | --- |
| shared/enums | EnableStatus | 基础资料、设备、工序、仓库与库存的启停 |
| basic/domain | MaterialType | ITEM 物料、PRODUCT 产品 |
| production/domain | RoutingStatus、WorkOrderStatus、ProductionExecutionStatus、ExecutionPathStatus | 工艺版本、工单、执行资料、路径状态 |
| issue/domain | MaterialIssueStatus、MaterialIssueSubmissionStatus、MaterialIssueOperation | 主单状态、提交尝试状态、操作类型 |
| integration/warehouse | ExternalEffect | 外部副作用事实 |
| integration/warehouse | ErpActionResult.Status、Mode（完善） | ERP动作结果、执行模式 |
| integration/warehouse | ErpIssueQueryResult.Outcome、DocumentStatus（完善） | 查询确定性、外部单据状态 |
| directory/domain | DirectoryStatus、DirectoryDeletionFlag | 共享中心目录的启停、删除标识 |

所有枚举含显式中文 description、稳定 String value、final 字段、getter、from 和有序 toMap。未知值及 null 的 from 返回 null；完整合法值校验由原业务入口使用 from 并拒绝非法输入，状态动作子集继续由原规则限定。启停/物料类型的 null 输入现在明确进入 BAD_REQUEST，不再由 Set.of.contains(null) 意外抛异常。ERP模式的未知编码在配置/作废边界明确拒绝。

字符串编码保持兼容，没有把现有 VARCHAR/CHAR 改为 byte；持久化转换改用 getValue，现有 enum 响应类型和序列化方式未改。WorkOrderExtraMapper 的产品工艺查询及阻塞领料查询通过 OGNL bind 从相应枚举获取 value，再用 `#{}` 参数化。

点检 subject_type 是来源字典编码，provider 是可配置实现标识；没有证据表明它们是封闭集合，因此保留字典/配置契约。节点号 N 前缀、执行单号临时 PENDING、权限码、配置键、SQL列名和文案不属于枚举状态。enable_flag 等透传字段原本无硬编码行为，本轮未添加新的必填或合法值限制。历史迁移中的编码和默认值保留。

## 长期规则

已追加项目 `AGENTS.md`「2026-10-15 用户确认：业务枚举与统一模板（强制）」：明确模板结构、String/byte 选择、未知值处理、语义分离、getValue 持久化、SQL参数化、字典边界及生成后适配。数值模板保留用户给定的 DELETED/INVALID/DISABLE/VALID 语义；现有字符串参考为 EnableStatus.java。

生成器已有“不推断业务枚举”的边界，本轮未修改生成模板或重新生成业务文件。手工触及生成文件时按规则检查并移除其归属标记；未改文件保留原样。工作区其他既有修改继续保留，不属于本轮枚举改动。

## 验证与边界

- `mvn -B -ntp -pl mes-business -am test`：通过；business 模块 90 项，0失败、0错误、0跳过。
- `BusinessEnumContractTest`：16个枚举的反向解析、未知值/null、值唯一性、中文描述、有序且独立的选项 Map；关键历史编码兼容；Java和Mapper XML不再重复已登记的业务枚举字符串字面量。
- `WorkOrderEnumSqlTest`：解析真实 Mapper XML，验证产品工艺查询绑定值；在隔离 H2 Fixture 执行真实生成的阻塞领料 SQL，覆盖本地/外部完整作废证明、未知结果、不一致模式及其他工单。
- `./scripts/format-java`：本轮35个 Java 文件全部完成格式化。
- `mvn -B -ntp verify`：全量8个模块 BUILD SUCCESS；business 90项全部通过，完整构建中的按环境条件跳过项不算真实环境验收。
- `git diff --check`：通过。未运行打包服务启动 smoke（本轮不涉及启动装配），未进行 HTTP/UI 或目标 MySQL 验收。

本轮没有新增 Schema 或数据迁移，没有执行目标业务库写入，没有启动、停止或重启用户服务。H2结果不代表指定 MySQL 验收库、HTTP/UI 或真实 ERP 已验证。回滚应仅撤销本轮枚举、调用点、测试及文档增量，不能重置工作区已有修改。
