# Production Core V1 Verification Report

2026-10-14，当前结论 **NO-GO（最后一次用户服务重启复验待完成）**。标准 LOCAL_ONLY 页面闭环已经真实跑通；最新全量 Maven 重验已通过，最后一处开工时间精度修正待用户再次重启后复验，不能将旧进程冒充最新代码。实施范围见 [Implementation Report](2026-10-14-production-core-v1-implementation-report.md)，复验步骤见 [User Acceptance Guide](../development/production-core-v1-acceptance.md)。

## 停止条件与证据等级

| 条件 | 当前结果 | 真实证据 |
| --- | --- | --- |
| SCHEMA_READY | PASS | 指定 SIMPLE_B / 数据源8 / 192.168.18.25:3306 / mes_base_dev_business_simple_b；V23 success=1；23项 Flyway validate；重复 migrate=0；三表中文 COMMENT/普通索引/仅主键约束核对 |
| CODE_READY | PASS | 四表 codegen 成功；最新后端 package SUCCESS；前端 build SUCCESS；两仓 diff --check 无问题 |
| TEST_PASSED | PASS | 16:51最新全量446 tests，0 failure/error，10 skipped；主链12项与真实MySQL项通过；日志初次失败、单独复跑及全量重验的时间线保留 |
| MYSQL_VERIFIED | PASS | 真实指定库业务测试、UI与HTTP记录、路径和提交快照核对、存量7表逐行保留 |
| API_VERIFIED | PARTIAL | 现有用户服务29项正/负例通过；最新一处 startTime canonical precision 修正待再次重启复验 |
| UI_VERIFIED | PASS（已运行版本） | 5173真实账号/工厂完整页面操作，双批次/六位小数、提交冻结、LOCKED、RUNNING、开工后作废拒绝；最新精度修正无UI行为改动 |
| BUSINESS_FLOW_VERIFIED | PASS（LOCAL_ONLY已运行版本） | 浏览器创建工单66 → EXE-72 → 版本99 A→B→C→E → 领料67/两条明细 → CONFIRMED/LOCKED → RUNNING；仍不覆盖真实ERP |

## 命令与结果

证据根目录 `.local/production-core-v1-evidence/` 为私有本地审查材料，含备份，不能公开上传；没有保存或输出凭证、令牌、请求体日志。

| 检查 | 命令 / 方法 | 结果 / 文件 |
| --- | --- | --- |
| 代码生成 | `scripts/mes-business-codegen docs/development/production-core-v1-codegen.yaml .local/production-core-v1-evidence/codegen-report.json` | 4表成功，无拒绝；真实metadata；产物只在审查输出采用需要的基础后端 |
| 全量较早检查 | Java21；`MES_CORE_MYSQL_ACCEPTANCE=true MES_VOID_MYSQL_ACCEPTANCE=true MES_EXECUTION_MYSQL_ACCEPTANCE=true MES_ISSUE_MYSQL_ACCEPTANCE=true mvn -B -ntp verify -Dmes.test.workorder.mysql=true` | `verify.log` SUCCESS：437 tests，0 failure/error，10 skipped；这是16:17较早版本，不作为最新全量通过 |
| 中间全量 | 同上 | `verify-final.log` 16:40：446 tests（framework15、codegen30、business82、admin319），1 failure、0 error、10 skipped；核心测试全部通过，日志容量清理断言失败 |
| 失败用例复跑 | `mvn -B -ntp test -pl mes-admin -am -Dtest=ProductionLogbackTest -Dsurefire.failIfNoSpecifiedTests=false` | `logging-failure-recheck.log`：实际3项运行PASS，未改任务外实现/断言 |
| 最终全量重验 | 完整相同环境门禁的 `mvn -B -ntp verify -Dmes.test.workorder.mysql=true` | `verify-recheck.log` 16:51 SUCCESS：446 tests，0 failure/error，10 skipped；所有相关真实MySQL项再次运行 |
| 故障及并发定向 | ProductionCoreV1HttpTest | `fault-and-race-tests.log`：12项PASS，外部成功/本地阶段3回滚后，原操作核实恢复，无再次 create |
| 最新打包 | `mvn -B -ntp package -DskipTests` | `package-final.log` 16:41 SUCCESS；跳测试打包仅供用户重启，不替代 TEST_PASSED |
| 前端测试 | Node22；`npm test -- --run` | `ui-test-final.log` 39文件205项PASS；包含6项新主链组件测试 |
| 前端构建 | `npm run build` | `ui-build-final.log` SUCCESS；现有大chunk提示，未扩大到优化任务 |
| 打包烟测 | `/usr/bin/python3 scripts/smoke-packaged.py` | `smoke.log` 10通过/0失败，独立自有临时进程；未操作用户服务 |
| Flyway | `.local/production-core-v1-evidence/validate.jsh`，标准Flyway实例/正式business目录/目标23 | `flyway-validate.log`：23 migrations validated，当前23，repeat=0 |
| MySQL | opt-in ProductionCoreV1MySqlTest、既有Issue/Execution/Void/WorkOrder真实库测试 | 最新运行1项Core真实业务链PASS；所有相关真实库定向项通过；新增TEST_ONLY记录保留 |
| HTTP | `.local/production-core-v1-evidence/http-acceptance.py`，5173代理、现有登录及权限，无绕过 | `http-acceptance.json` 29项PASS；保留工单65/Execution71/领料66/submission5 |
| 存量保留 | `check-preservation.py`，只读逐行比较 | `preservation.json` 七表基线ID均未变化/丢失，列头一致 |

全量十项跳过为各自显式环境门禁的其他基础设施验收；不能视为这些基础设施已通过。真实 Core MySQL 本次未跳过。H2/MockMvc并发测试证明受控线程竞争行为，真实MySQL测试证明事务/Mappers及指定库业务链；未运行多JVM厂商故障场景或真实ERP，不将两者混写。

## 覆盖与数据核对

ProductionCoreV1HttpTest 12项覆盖完整根→终点路径、非法缺边/重复节点/关键节点绕过/旧revision、同工序不同节点身份、空明细与非法数量/引用、CONNECTED缺实现先拒绝、REJECTED受控重开与历史、UNKNOWN/NOT_FOUND/待审核不重建、不在外部调用时持锁、冻结载荷/路径/开工、多单同路径并发、作废中阻断开工、作废后不解锁、已运行作废拒绝、权限/跨厂、迟到结果保护及阶段3失败恢复。关联旧测试继续验证历史领料、旧批次只读、发布版本冻结、工单分配、数据库隔离。

真实样本（浏览器）：

| 对象 | 核对结果 |
| --- | --- |
| WorkOrder66 | `QA_CORE_V1_UI_1791966344486`，产品1，默认版本64，已下达，计划10.000001 |
| Execution72 | EXE-72，实际版本99，目标10.000001，RUNNING，开始2026-10-14 16:40:01，结束NULL |
| Path72 | LOCKED，revision1，锁定16:39:16，按顺序节点1169/1170/1171/1173（A/B/C/E） |
| Issue67 | CONFIRMED，submission6；attempt SUCCEEDED，完整immutable command_json |
| Item53 / 54 | TEST_CORE_UI_LOT_A / B，数量1.123456 / 2.000001，父单67固定 |
| Extra67 | source_execution_mode LOCAL_ONLY，external_reference=NULL，external_code=NULL；无外部库存凭证 |

`mysql-final.tsv` 包含真实绑定、Schema/注释/索引/约束、样本执行/路径/领料/Extra、明细核对。新三表缺中文comment数=0，只有主键为唯一约束，业务表无factory字段。旧20个ProductionBatch和48个BatchRouteNode保持不变。

迁移前已采集备份与初次Flyway日志，但并行任务删除整个root target目录导致原材料丢失。本报告保留这一限制：当前可重现的逐行对比基线是 **V23完成后、真实HTTP/UI验收前** 的7表快照（workorder38、Execution3、Issue20、Extra20、Item4、Batch20、BatchNode48），不是迁移前字节级备份。V23仅新增结构/扩状态字段，没有更新或删除历史业务行；历史读取/保护测试通过。不能宣称已保存迁移前完整字节比对证据。

## HTTP 与浏览器

29项真实HTTP包括工单下达、Execution创建、未领料开工409、选路/旧revision409、无明细提交400、双批次保存、LOCAL_ONLY确认/重复提交同attempt、锁路修改409、提交后普通编辑409、GET查询只读Extra版本不变、原操作核实、开工RUNNING及重复时间相同、RUNNING作废409、COMPLETED普通编辑409、Create RUNNING422、未登录401、无有效Factory422。现有管理员授权读取复核，未更改角色。

浏览器通过页面完成上述标准主链，提交前开工禁用、提交后字段禁用和明细只读、路径锁定无法编辑、开工后列表“执行中”与开始时间刷新。普通编辑无status/起止时间输入，RUNNING实际版本不可改；已运行领料“作废→确认”显示冲突拒绝，保留CONFIRMED。页面既有错误文案仍较通用，详情可结合requestId排查，不宣称每个错误都有领域中文说明。

截图：

- `.local/production-core-v1-evidence/ui-selected-path.png`：领料前SELECTED，开工禁用。
- `.local/production-core-v1-evidence/ui-confirmed-issue.png`：本地确认、双明细、主子冻结。
- `.local/production-core-v1-evidence/ui-running-locked.png`：RUNNING与不可改原路径。
- `.local/production-core-v1-evidence/ui-void-rejected.png`：开工后的领料作废被拒绝。

## 失败、未验证与边界

1. 最初独立迁移runner target20导致新表未创建，codegen TABLE_NOT_FOUND；已纠正target23并真实执行/复核。不能把初次no-op当迁移成功。
2. 首轮HTTP暴露开工首响应含纳秒而MySQL DATETIME不保留；数据库开工幂等成立，已修正生产代码返回持久化时间。最新包已完成，待用户第二次重启后重新验证首次与重复响应严格相同；当前不冒充已装载最新包。
3. 最终全量初次受到并行日志测试expectedSlow未定义编译错误；其后外部工作修正，重验到日志容量清理断言失败。未修改任务外测试/日志实现、未删断言或跳该类以伪造全量通过。
4. 中心库既有failed V17未repair；本轮无新增中心迁移/菜单/角色操作。当前已运行服务的指定绑定、权限和主链成功不代表中心迁移历史已修复。
5. CONNECTED只在TEST ONLY Adapter中覆盖成功、拒绝、未知与恢复，无生产假厂商；LOCAL_ONLY真实验收不等于ERP库存扣减。没有真实厂商联调、完工、报工或入库证据。

当前可以查看保留样本并按Guide理解流程；**正式GO只等待最新代码重启后的真实服务复验**。日志测试的中间失败已通过单独及全量重验，不能承诺此异步容量用例今后不再抖动。本轮不扩大到日志修复或后续生产能力，不提交、推送、部署或清理验收数据。
