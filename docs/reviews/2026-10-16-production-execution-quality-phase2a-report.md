# ProductionExecution × Quality Phase 2A 引用保护实施报告

日期：2026-10-16。范围：生命周期引用保护；不进入 Phase 2B。下述源码路径均相对于 `/Users/user/work/lixin/mes-base`，行号为本次验收时工作区快照。

## A. 仓库基线与并行保护

| 仓库 | 分支 | 开始时 HEAD |
|---|---|---|
| `/Users/user/work/lixin/mes-base` | main | `4679154eccb61444f1c0f795c9620a54ec6e207d` |
| `/Users/user/work/lixin/mes-base-ui` | main | `732d669e41c11e13c5bfcdb1a4ba3e67076e942e` |

开始时后端已有两个 dirty 报告：`2026-10-13-warehouse-inventory-review.md`、`2026-10-16-production-defect-phase1-implementation-report.md`。前端已有生产、质量、选厂、路由及测试等大量 dirty/untracked 文件；完整清单和已有文件 SHA256 保存在 `.local/production-quality-phase2a-20261016/baseline.json`。本轮没有编辑前端或 Source Product，没有清理工作区、提交、推送、部署或重启服务。

任务过程中观察到并行报工开发新增 requestId、报工提交入口、操作员资格及 V27/V30 迁移等文件；观察快照保存在同目录 `parallel-observed.json`。它们不属于本轮功能。为恢复共同测试入口，仅对并行 `MesProductionReportMapper.xml` 的 resultMap 做 `<id>` 前置修正（MyBatis DTD 要求）；保留 requestId 和 SQL 改动。共享 H2 测试 fixture 保留并行 V27 列表，并将其 MySQL ADD/ASCII 语法转换为 H2 等价 DDL；没有修改 V27 文件。没有批量格式化并行文件。

## B. 真实入口审计

| 操作 | 现有保护与源码证据 | 质量历史风险 | 本轮处理 |
|---|---|---|---|
| 删除 Execution | `production/service/ProductionExecutionRules.java:118 deleting` 已锁 WorkOrder→Execution，拒绝开始、领料、报工，LOCKED 路径拒绝；SELECTED 路径会清理 | Quality 可引用 SELECTED 路径，原流程可能删除身份 | 在清理前加入历史查询 |
| 批量删除 Execution | `production/service/impl/MesProductionExecutionServiceImpl.java:148 deleteByIds`，排序调用 deleting，单一业务事务后批量 DELETE | 与单删相同；须防止前面路径已清理后部分成功 | 复用 deleting；HTTP 验证后项拒绝时前项清理回滚 |
| 重新选择实际路径 | `production/service/ExecutionPathService.java:98 select` 已锁链、READY、revision、LOCKED 与领料检查，随后重建节点 | 未锁定路径上的历史可能悬空 | 重建前加入历史查询；LOCKED 继续沿用旧规则 |
| 更换 Execution Routing 版本 | `production/service/ProductionExecutionRules.java:90 updating`：存在路径即禁止换 routingId | Quality CREATE 必须先有合法实际路径，因此已有保护覆盖 | 不重复加查询；HTTP 验证拒绝 |
| 更换 Execution 所属工单/产品 | 同文件 `updating:94` 无条件冻结 workorderId/productId | 既有保护覆盖 | 不修改；HTTP 验证 |
| 删除 WorkOrder | `production/service/WorkOrderService.java:218 delete` / `:222`：有 Execution 即拒绝，不级联 | 已有保护覆盖 | 不修改；HTTP 验证 |
| WorkOrder 更新/作废 | `WorkOrderService.java:161 update` 有 Execution 不可改产品；默认 routing 与 Execution 实际版本分离；没有独立业务作废命令 | 无需把正常默认配置、备注冻结 | 不增加质量门禁，不新建作废语义 |
| 删除/修改 RoutingNode、RoutingEdge | `production/service/impl/RoutingServiceImpl.java:224 saveContent`（调用 touch）；无独立节点 CRUD Controller | 已发布节点不能走草稿写入口 | 复用 published 冻结，HTTP 验证节点不变 |
| 修改/删除已发布 Routing | 同文件 `updateById:182`、`deleteByIds:193`、`touch:414`，draftOnly 要求 DRAFT | 已有保护覆盖 | 不修改；HTTP 验证 |
| Routing 新版本/同步 | 同文件 `newVersion:282` / `copy:295` 复制身份；`sync:530` / `:620` 已发布版本变化新建 DRAFT，不重写已发布节点 | 不会替换原身份 | 静态核验；不增加全局冻结 |
| 通用路径 CRUD | `production/service/impl/MesExecutionPathServiceImpl.java:91,99`、`MesExecutionPathNodeServiceImpl.java:85,93` 禁止修改/删除；新增入口同样拒绝 | 无旁路 | 保留现有拒绝 |
| Execution 导入/同步/管理员入口 | 当前 Controller/Service 检索未发现独立导入/同步删除实现；管理 DELETE 调用上述 deleteByIds | 不存在第二条已知业务删除入口 | 无新入口 |

表中 `production/` 全路径前缀为 `mes-business/src/main/java/dev/mesbase/business/`。上述“无旁路”指当前应用入口，不包含绕过应用的人工数据库写入。

## C. 实际修改

1. 新增 `mes-business/src/main/java/dev/mesbase/business/quality/service/ExecutionQualityReferenceQuery.java:7`：最小跨业务只读接口，注明破坏性调用必须先持有共同链锁。
2. 新增同目录 `ExecutionQualityReferenceQueryImpl.java:21 hasAnyHistory`：注入 Quality Mapper，具名业务只读事务；null 拒绝，异常传播。
3. `mes-business/src/main/java/dev/mesbase/business/quality/dao/MesProductionDefectMapper.java:11` 和 `mes-business/src/main/resources/dev/mesbase/business/quality/dao/MesProductionDefectMapper.xml:110`：参数化 EXISTS。
4. `mes-business/src/main/java/dev/mesbase/business/production/service/ProductionExecutionRules.java:122`：物理删除前保护。
5. `mes-business/src/main/java/dev/mesbase/business/production/service/ExecutionPathService.java:106`：实际路径重建前保护。
6. `mes-common/src/main/java/dev/mesbase/core/error/ErrorCode.java:4`：`409 / EXECUTION_QUALITY_REFERENCED`，明确中文原因。

新增测试：`ExecutionQualityReferenceQueryTest`、`ProductionQualityLifecycleHttpTest`、`ProductionQualityLifecycleMySqlTest`。既有 MaterialIssue/ProductionDefect/ProductionExecution/ProductionReport/WorkOrder 测试上下文补充实际查询实现及 Mapper；共同 H2 fixture 统一加载 V26，移除质量测试重复建表。V26 触发器只在 H2 fixture 中剥离；真实 MySQL 保留原触发器。未新增第二套质量持久化。

## D. Quality 查询契约

SQL：`SELECT EXISTS(SELECT 1 FROM mes_production_defect WHERE execution_id = ?)`。没有 latest、record_action 或有效数量过滤；CREATE、REVISE、VOID 全部算历史，全部 VOID 后仍返回 true。生产模块只依赖接口，不访问质量内部 Mapper。查询不加载完整历史，不建立缓存；XML `useCache=false, flushCache=true`，基础 MyBatis `LocalCacheScope.STATEMENT`（`mes-framework/src/main/java/dev/mesbase/infrastructure/persistence/PersistenceFactories.java:22`）。

Mapper 继续由 `businessSqlSessionFactory` 归属与既有 Factory 路由提供业务库；不携带行级 factory_id。查询失败原异常向外传播，事务回滚，绝不以 false 放行。该查询只加入删除/路径重建，不加入普通更新、开工、报工、数量读取或 Quality 写入链。

真实 EXPLAIN 使用已有 `idx_defect_execution_node` 左前缀，type=ref、估算 rows=1、Using index，证据 `.local/production-quality-phase2a-20261016/mysql-explain.yml`。未新增索引或 Schema。

## E. 并发与真实数据库证据

锁复用 `ProductionChainLocks.java:37 order / :47 execution`：先定位所属工单，再锁 WorkOrder，随后锁 Execution 并重读归属。Quality 原有 `ProductionDefectService.java:79–90` 命令使用 READ_COMMITTED 业务事务，`:109` 在事实校验/写入之前取得同一链锁。删除 Service `:145` 与路径 Service `:48` 同样使用 `businessTransactionManager / READ_COMMITTED`。本轮只在持有锁之后查询历史，不引入新锁。

查询实现 REQUIRED 加入已有写事务；真实 MySQL 测试通过 Spring 容器获取代理服务及 TransactionTemplate，不以直接 new Service 或 Mock 锁替代事务。请求 Factory 由原准入固定；测试业务连接依据 `mes-admin/src/test/java/dev/mesbase/acceptance/AcceptanceBusinessDatabase.java:18 source` 读取中心真实绑定，并核对 SIMPLE_B、主机、端口、库名及工厂/源 ENABLED。目标为 datasource 8、`192.168.18.25:3306/mes_base_dev_business_simple_b`。

真实用例 `mes-admin/src/test/java/dev/issuefixture/ProductionQualityLifecycleMySqlTest.java`：

| 方法 | 结果 |
|---|---|
| `deletionAndCreationSerializeForBothCommitOrders:185` | Quality 先提交→删除质量引用409；删除先提交→CREATE EXECUTION_NOT_FOUND；等待方在首事务未释放时无法完成 |
| `pathReselectionAndCreationSerializeForBothCommitOrders:251` | Quality 先提交→重选409；重选先提交移除旧分支节点→CREATE BAD_REQUEST；不能双成功留下悬空引用 |
| `differentWorkordersRemainIndependentAndExistingIndexServesHistoryLookup:315` | 不同工单 Execution 的 CREATE 在另一事务持锁期间完成，无新增全局锁；EXPLAIN 命中既有索引 |
| `voidedHistoryRemainsAReferenceWhenEffectiveQuantityIsZero:361` | CREATE→VOID 后合计0，历史2条，exists=true；删除、重选均质量引用409 |

独立性边界：既有父 WorkOrder 锁会串行同一工单内的 Execution；本轮未改变该策略，不将不同工单的证明推广为同工单无等待。跨厂同 ID 隔离以双业务库 H2 HTTP 验证，未宣称真实双 MySQL 厂并发。

测试仅创建 `TEST_QREF_*` 的新工艺/节点/工单/Execution；复用既有启用物料、工序、仓库只读，UUID 编码冲突检查为0。每个 Harness 写入前备份已有质量历史到本地 yml，结束后逐字段比较原范围完全一致。测试自己的申报通过正式 VOID 追加版本收尾，不 UPDATE/DELETE 历史；追溯测试数据留存在验收库，七份 `TEST_QREF_*.txt` 记录实际 ID。删除胜出用例只删除本轮新建且无历史的 Execution。没有执行 Flyway、repair、修改迁移记录或 V26。

## F. 回归测试

证据目录：`.local/production-quality-phase2a-20261016/`。

| 实际命令 | 结果/证据 |
|---|---|
| `MES_QUALITY_REFERENCE_MYSQL=true mvn -B -ntp -pl mes-admin -am test -Dtest=ProductionQualityLifecycleMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`（当时3项） | 3通过、0失败/错误/跳过；`mysql-tests.log` |
| `MES_QUALITY_REFERENCE_MYSQL=true mvn -B -ntp -pl mes-admin -am test '-Dtest=ProductionQualityLifecycleMySqlTest#voidedHistoryRemainsAReferenceWhenEffectiveQuantityIsZero' -Dsurefire.failIfNoSpecifiedTests=false` | 最终1通过、0失败/错误/跳过；`mysql-void-tests.log` |
| `mvn -B -ntp verify`（最终重跑） | BUILD SUCCESS，562项：536通过、26跳过、0失败/错误；`verify-final.log`，用时1分05秒 |
| `mvn -B -ntp -pl mes-admin -am test -Dtest=ExecutionQualityReferenceQueryTest,ProductionQualityLifecycleHttpTest,ProductionDefectHttpTest,ProductionReportHttpTest,ProductionExecutionHttpTest,ProductionCoreV1HttpTest -Dsurefire.failIfNoSpecifiedTests=false` | 36通过（business查询1、admin接口35）、0失败/错误/跳过；`final-targeted.log` |
| `scripts/format-java`（仅本轮Java目标9文件，最后对两项新测试再次格式化） | 通过，`format.log`、`format-final.log`；未格式化并行文件 |

本轮修改范围 `git diff --check` 通过；全工作区检查仍报告并行报告 Mapper XML 的3处行尾空格（78/112/116），未为清理格式覆盖并行SQL。普通报工可用性的新增HTTP证据具体为既有 `/reports/supplements` 补录接口，不将其宣称为并行新增报工页面的实测。

HTTP 使用 Spring MockMvc + 实际 Controller/Service/XML/Spring 事务、双路由 H2 数据库，中心标签/目录部分 Mock；它是接口集成证据，不是用户已运行服务器的实测。9项新 HTTP 用例覆盖：无历史合法重选和删除、CREATE/REVISE/VOID 拒绝、批删回滚、跨厂同ID、工单/已发布工艺保护、查询故障关闭删除重选而普通更新报工继续、领料确认/开工/报工与未集成数量，以及删除/CREATE竞争。对应 `ProductionQualityLifecycleHttpTest.java:95–212`。单元查询测试验证真实实现调用 exists，false/true、null与数据库错误传播。

曾遇到并修正的验证失败：测试初始缺 LOCAL_ONLY 仓库配置；并行 V27 使 H2 不支持原 MySQL 多 ADD DDL；并行报告 resultMap id/result 顺序违反 DTD。第四个 MySQL 用例首次运行时测试类加载缺失，后续原命令重跑通过，不能据此认定生产逻辑有错。保留最终日志，未用伪造断言掩盖错误。

完整 verify 首次在 `OperatorQualificationTest` 出现3失败、2错误，mes-business共128项，admin尚未运行（`verify.log`）。该运行期间并行资格实现与构建产物仍在变化；本轮没有修改其规则或断言。随后定向生产/质量回归全部通过，并重新执行完整 verify，以最终日志为准。此前 `http-tests.log` 和 `targeted-tests.log` 保留的是中间失败，不作为最终通过证据。

最终 verify 模块测试统计：21/30/128/383项，共562项，其中admin跳过26项。跳过项是默认环境开关关闭的 MySQL/Redis/运行环境验收：Stock、ProductionCoreV1、ProductionExecution、ProductionReport、MaterialIssueVoid、ProductionDefect、ProductionQualityLifecycle、OperatorAssignment、ProductionDefectMigration、MaterialIssue、MySqlDevelopment、RoutingVersion、WorkspaceAccess、OperationRouting、OpenApiRestoration、RoutingMetadataRedis、RedisRevocationCapacity、WorkOrder、IndexedRedisSession、ManagedRuntime。四项 ProductionQualityLifecycleMySqlTest 虽在默认 verify 中跳过，但在上方显式打开开关的真实库运行中全部通过；不将其他跳过项视为实测通过。当前首个失败的21项 OperatorQualificationTest 最终也全部通过。

未执行：用户现有服务的网络 HTTP/UI 复验（未重启服务）；真实双厂 MySQL 测试。当前没有正常完成 Execution 或业务 VOID 命令，故“正常完工/业务作废命令回归”标记 NOT RUN / 当前无入口；已有禁止通过 CRUD 伪造 COMPLETED 的状态机校验仍通过，未为满足测试发明新命令。

## G. 边界审计

- `ProductionQuantityService.java:70–75` 仍以 reported 计算，defectQty=`BigDecimal.ZERO`、qualityIntegrated=false；本轮该文件无改动，HTTP断言0/false。未接入有效不良数量。
- 本轮没有修改 ProductionReport 数量模型/公式或写入规则；工作区 requestId/提交入口/资格改动属于并行报工开发，不归本轮结果。共享报告 XML 只修复 DTD顺序。
- `ProductionDefectService.java:133–139` 保持 insert 新版本；本轮生产代码没有更新或删除质量事实，真实验收前后既有质量范围一致。
- 未新增 Execution 状态；未实现返工、库存移动、ERP报废、工资；没有改变库存或外部调用链。测试使用已有本地 ERP acknowledgement，不调用外部 ERP。
- 未新增 Schema/索引、改 V26、清理 Flyway 或修复中心迁移。无 Codegen需求：仅在现有 Mapper补专用EXISTS，没有新增基础CRUD。

## H. 最终结论

**Phase 2A：GO（当前应用业务入口的引用保护实现及自动化/指定 MySQL 验收通过）。** 完整 verify 为536通过、26环境跳过；另行启用的4项真实 MySQL测试全部通过。此结论不等于部署或用户运行中服务/UI已复验。当前没有已知可由已审查正常管理入口删除质量历史身份的路径。

已完成范围：单删/批删、SELECTED 实际路径重建的显式历史保护；复用既有工单、工艺版本和身份冻结；VOID不解除强引用；查询故障拒绝破坏性操作；真实 MySQL 验证两种提交顺序。应用外直接 SQL、未来新增旁路及同工单串行策略不由本轮扩大处理。将来任何破坏性入口必须复用业务事务与相同链锁，再检查历史。

Phase 2B 前置条件：另行确认 defectQty 与报工/有效产出/剩余数量的业务口径、节点归属、修订/VOID补偿、已有报工冲突、并发锁及幂等契约，并重新完成数量联动和真实环境验收。本轮不自动进入 Phase 2B。
