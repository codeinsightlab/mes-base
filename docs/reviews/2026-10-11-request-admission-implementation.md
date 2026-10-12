# 认证、授权与路由改造验收

> 现行路由规则见文末“后续实施：路由缓存简化”。同日先前的代际、持久失效和请求 reservation 为历史记录，已被最新用户决定取代；账号安全撤销机制保留。

2026-10-11。已完成实现和规则同步；真实部署联调与同环境 HTTP 性能对测尚未验收。当前行为见 [请求准入](../development/request-admission.md)。

## 改动与保留

- TokenService/UserSessionStore：随机 Bearer、固定有效期保持；Redis v2 安全 Identity + 用户会话索引/Lua，正常请求一次无密码账号/角色查询，移除空 setLoginUser 和扫描 revokeUser。
- SessionAccountMapper/SessionRevocationService/V19：禁用、删除、改密同事务写认证版本与持久撤销记录；提交后 Redis 交付失败明确返回已提交/待重试，DB fence 拒绝旧会话，后台条件确认与恢复。
- PublicEndpointMatcher/FoundationConfiguration：显式 PUBLIC 路径及 HTTP 方法，其他请求认证；保留 EndpointAccess 操作授权及漏配启动检查，合并重复启动检查；system=false Basic fixture 保持。
- SystemPermissionService/SysMenuServiceImpl：当前角色、权限、菜单分类请求内复用，后续请求读取撤权；结构化缓存键隔离真实字符串工厂 ID 与空平台范围。删除角色修改后刷新空会话的旧兼容调用。
- FactoryBusinessDataSource/RuntimeRegistry/ManagedDatasourceService：新事务读取有效绑定；SQL/缓存/提交只验固定身份与租约。旧租约退休后继续，包括首次取连接；同 identity 的旧 owner 未排空不能移交。普通名称编辑不退休；暖 resolver 查询不持全局同步锁。跨节点已初始化池批量核对，关闭失败可恢复且不误关已发布新池。未引入 Redis metadata 缓存。
- UI request：响应体读取后继续验证上下文，旧响应/旧 401 不影响新厂或新登录。SESSION_REVOCATION_PENDING 不误称 DB 回滚、不建议重复提交。
- 后端/UI AGENTS、ADR-002/003/004/006、platform、架构瘦身及延迟报告已同步；旧长期 Redis 授权 generation 提案标记未实施/已撤销。smoke 的不存在路由匿名预期更新为默认认证 401。

保留平台完整 URL 范围、共享/个人入口、平台业务与所属工厂权限并集、部门 dataScope、无 userId=1 绕过、私有资源 owner/resource/scope、两个生产 SessionFactory/TM、固定包扫描、缺厂/连接失败不回退及现有生成器/Vue3 规范。未改 ktg-mes、外部连接、Quartz 真实执行器或 OpenAPI 消费者；没有构建在线迁库系统。切库仍可能有旧事务写旧库、新事务写新库重叠。

## 实际验证

| 命令/证据 | 结果 | 边界 |
| --- | --- | --- |
| IDEA `scripts/format-java` 及 `--check`，本轮 26 个 Java 文件 | 通过 | 未格式化其他任务文件 |
| `mvn -B -ntp test -Dmes.test.redis=true` | 223 项，219 通过，4 跳过，0 失败/错误 | 跳过项为未 opt-in 真实 MySQL；admin208/framework8/generator3/openapi2/business2 |
| `mvn -B -ntp verify -Dmes.test.redis=true` | 同上，BUILD SUCCESS | 真正执行测试及打包，不是 skipTests |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 10 项通过，0 失败 | 自有隔离 loopback Java 进程，无用户服务重启，无业务 JDBC |
| UI Node22.23.1 `npm test` | 23 文件、127 项通过 | 网络/会话/选厂为 fixture；不是真实浏览器 |
| UI `npm run build` | 通过 | 已有大 chunk 提示保留 |
| 两仓 `git diff --check` | 通过 | 含当前其他任务工作区变更 |
| IndexedRedisSessionTest | 真实独立 Redis/Lua 通过 | 自有临时端口/目录，退出测试时停止，不使用现有 Redis |

会话专项覆盖安全字段、当前部门/角色、旧凭证拒绝、实际禁用/改密/删除 Service、事务回滚不发送撤销、提交后 Redis 故障的持久记录与重试、旧交付条件确认；H2 的 SYSDATE 仅作测试时钟适配。Redis 专项验证登录/撤销并发、新版本会话保留、注销及绝对 TTL。HTTP 及授权用例覆盖共享/平台/工厂负例、默认认证、非 MVC/静态、权限合并、请求复用与后续撤权。事务用例保留并补充切厂/跨库、挂起恢复、并发 A/B、异常清理、固定旧绑定、新独立准入、退休租约及关闭失败恢复。

## 查询数与耗时

相同工单分页路径 `/api/pro/pro-workorder?offset=0&limit=20`：旧报告为开发库真实 HTTP **16 中心 + 2 业务 SELECT**，预热约 1065–1438ms；新真实生产类链路的隔离 H2 fixture 为 **6 中心 + 2 业务 SELECT**。中心重复查询减少 10 次，SQL/缓存/提交不再重读完整管理配置。新预算：精简账号/当前角色 1、入口厂 1、全菜单 1、当前权限 1、事务绑定 2。

最终 verify 的 20 次 H2 样本（另有 5 次预热）p50 **3.61ms**、p95 **8.99ms**，详见 `target/validation/pro-workorder-request-budget.json`。该样本无 HTTP socket、Redis payload 为 synthetic stub，中心/业务 H2 使用真实 Mapper 和 Spring 事务；旧样本使用真实开发 MySQL/Redis/HTTP。**不能直接比较耗时或声称真实接口已降到 3.61ms**。完整 SecurityFilterChain 由另一组嵌入 Tomcat HTTP 测试验证，不由预算用例替代。

未完成真实多实例 MySQL、同环境新旧 HTTP 延迟对测、Redis Cluster、真实浏览器回归；未执行真实迁移/生产操作。原因是本轮保护用户服务与真实数据，真实 MySQL opt-in 未配置。部署需要先正常应用 V19；v2 格式启用后旧格式会话需重新登录。数据库提交/Redis 交付不是原子性证明，失败恢复依靠持久 outbox 和准入 fence。

## 工作区与版本

开始后端 main/560600b22e27eab36c3838d66427f032288709b0，前端 main/da75942a02df5d4f07e2cf8f0cc44e4e051ebdc8。参考 ktg-mes master/42a81cc577dc77b05a6a67f6be0a9466e40784e1，其 application.yml 既有修改保持只读。

工作期间本次工具调用之外出现提交：后端 main/319a005b352d8665eb93c992402a971bee7a00d3、前端 main/2e9c8c1b48993bd35fc051b8e681212c549ae4ed。后端提交包含本輪部分 admin 基础设施/测试及其他任务 CodegenAcceptanceTest；前端提交包含本轮 request/测试/规则及既有目录组件任务。**本任务没有执行 commit/push/reset/switch，也没有回退这些外部提交**；其余本轮文件仍位于当前未提交工作区。最终测试针对当前源码及这些变化共同运行，不宣称上述外部提交单独包含完整交付。

已保护既有业务目录、代码生成器、Skill/codegen 文档及其他任务修改，没有把它们算作本轮实现。当前源码与本轮文件清单/哈希另记录在 `target/validation/request-admission-source.json`；构建产物不是部署。

## 2026-10-11 回执复审：撤销记录与请求生命周期

### 登录后用户信息读取失败修复

用户提供的真实异常明确落在 `SystemSessionController.info -> SystemPermissionService.scopedPermissions -> List.copyOf`。目录菜单允许 perms 为 NULL，权限查询返回集合中的 NULL 在请求快照复制时触发 NullPointerException；不是事务 commit 日志导致，也不能描述为密码校验失败。

修复：`selectScopedPermissions` SQL 排除 NULL/空白权限；Service 在缓存前过滤 NULL/空白值，保留逗号拆分、平台/业务分类及请求内复用。未修改菜单目录本身、角色授权、Redis 会话格式或数据库结构。回归用例包括含 NULL/空白/逗号权限的请求快照，以及真实 H2 Mapper 查询带 NULL 目录权限仍正常授权。定向 RoleScopePermissionTest 5 项通过；修改的两个 Java 文件 IDEA 格式化及检查通过，`mvn -B -ntp test` BUILD SUCCESS，`git diff --check` 通过。未重启用户服务，真实浏览器重新登录仍需服务加载修复后验收。

开发日志调整：`application-dev.yml` 对 `OwnedTransactionManager` 和 `SessionAccountMapper.pending` 单独设 INFO，关闭 Spring JDBC 事务生命周期及后台空队列 SQL 的 DEBUG，保留其他 Mapper SQL DEBUG 与错误日志。`Initiating transaction commit` 来自 Spring 事务管理器，并不表示登录异常。配置需在服务重新加载后生效；本次未重启用户服务。用户报告登录失败，本次未取得当前登录响应或对应后端异常，不能把旧日志、迁移缺失或 Redis 故障作为已确认根因。

本次为当前源码静态复审，未重跑上文测试、未迁移真实数据库、未重启服务。上文测试数量及通过结论属于原实施回执，不能当作本次重新验证结果。

结论：保留 `sys_session_revocation` 合理。其作用是保存中心库提交后 Redis 撤销的可恢复交付意图，不是权限缓存或会话资料表。入口 `TokenService.resolve` 一次查询当前账号/角色并校验认证版本，因此 Redis 交付滞后不会让旧版本会话通过新请求准入；已准入请求不重新验账号版本。当前方案不是整个 Token 周期完全不访问数据库，明确保留一次账号/角色查询，权限与菜单仍按请求读取。

源码支持账号变更与入队同事务、Redis 提交后交付、取较大 revision 合并同用户事件，以及按 userId+revision 删除已交付记录。延迟旧交付不会删除较新记录；Redis 撤销只删除低版本会话，保留新版本登录。重复交付允许幂等，当前无需为了多实例重复消费再增加分布式抢占锁。

需要修正或完善的内容：

- **P2 重试公平性：** `SessionAccountMapper.xml` 的 pending 固定 `ORDER BY user_id LIMIT 100`。若前 100 条持续失败，后续记录长期得不到交付机会。账号准入版本校验仍有效，但持久恢复不能保证公平推进。建议引入可执行时间与公平排序/游标，失败退避后让其他记录继续，并覆盖超过 100 条且部分持续失败的测试。
- **P2 批量入队查询：** `SessionRevocationService.enqueue` 对每个 ID 调用一次 revision 查询。批量删除账号会产生 N 次中心查询。建议一次批量读取版本，保留当前同事务和提交后交付语义。
- **记录含义需澄清：** afterCommit 撤销成功没有立即确认删除；所有入队记录都保留到后台再次交付并确认。因此表中存在记录不代表 Redis 撤销一定失败。当前行为安全但有重复交付，可保留并修正文案，或用独立新事务立即按版本确认，不能复用已提交连接。
- **容量边界待验证：** `UserSessionStore.REVOKE` 在一个 Lua 脚本中遍历该用户全部会话，当前没有活跃会话数量上限。应按实际登录量验证脚本阻塞时间；epoch 键无 TTL，也应明确保留及账号删除后的清理策略，避免直接随意设置 TTL 打破并发发行保护。

事务生命周期源码符合已确认方向：`FactoryBusinessDataSource.begin` 解析绑定并取租约，`verify` 检查固定工厂/连接身份及租约而不重新读取管理配置；`RuntimeRegistry.Lease.verify` 不因 runtime retired 拒绝旧租约。配置切换允许新事务走新库、旧事务继续旧库，这一重叠是约定行为，不能称为在线迁库一致性保证。

回执的性能证据边界合理：工单预算测试源码断言 6 次中心与 2 次业务查询，但 H2、synthetic Redis、无 HTTP socket 的耗时不能与旧真实 MySQL/Redis/HTTP 耗时直接对比。真实 MySQL、多实例、真实浏览器和同环境延迟仍待验收。本次仅追加审查结论，未修改业务代码。


## 2026-10-11 恢复公平性、批量版本与 Lua 容量修复

本节取代上节“需要完善”的重试公平性、N 次版本查询及记录含义结论，保留原时间线。本轮为实现与验收，不是只读复审。开始后端 main/0657b13a6fc9ee0822080e70dded6e421cdfd610、前端 main/6702f527be7c0e58acf424255eacbe0b196a5799。保护已有报告修改、未跟踪 V18/V19、会话/授权源文件和其他代码生成任务文件；本轮仅增量修改撤销 Service/Mapper/XML、会话测试、新增 V20 与容量测试、请求准入文档及本报告。前端无代码改动。

### 源码与行为

- `SessionAccountMapper.pending`：每轮最多 100 条到期记录，按 `next_attempt_at,user_id` 稳定排序，索引覆盖到期与排序字段。`failed` 根据本次读取的 attempts 计算 5/10/20/40/80/160/300 秒退避，最高 300 秒；下次时间通过数据库 CURRENT_TIMESTAMP/TIMESTAMPADD 计算，避免实例时钟差决定退避。并发失败写入取较晚时间，仍匹配 user_id + revision。没有抢占锁，多实例允许重复交付。
- `enqueue`：较新 revision 才重置 attempts/last_attempt/last_error/next_attempt_at，旧或同版本入队保持原退避；revision 赋值最后执行，避免 MySQL 顺序赋值影响比较。所有字段和账号变更仍在同一个中心事务提交。
- `SessionRevocationService.enqueue`：集合去重，每 500 个 ID 一次参数化批量版本 SELECT，随后批量入队；空集合无动作，集合/元素为空或账号缺失明确失败，调用方账号事务回滚，不静默遗漏。当前用户批量删除没有已找到的集合总上限，因此只限制 SQL 批大小，不增加产品限制。afterCommit 捕获的是该事务内读取的不可变版本列表，不逐用户查询。
- afterCommit 成功后仍不立即开启确认事务，后台再次幂等交付后按版本删除。记录表示“尚未持久确认完成”，可能已成功送达 Redis。last_error 使用“交付或确认失败”的安全固定摘要，不能把 Redis 已成功、数据库确认失败误称为 Redis 失败。失败状态记录本身暂不可写时记录原异常及 suppressed 异常并继续本批其他记录；原记录仍在库中，数据库恢复后可继续重试。
- 提交后 Redis 失败返回 HTTP 503 / `SESSION_REVOCATION_PENDING`，账号修改已提交，调用方应提示已保存/撤销待恢复，不应当作回滚或重复提交账号修改。新请求的账号状态/认证版本 fence 继续拒绝旧会话；已准入请求和已开启业务事务继续。角色、权限和菜单仍按请求读取，没有新增 Redis 权限缓存或额外请求查询。

### Fixture 验收

`SessionAdmissionTest` 使用实际 Mapper XML、隔离 H2 MySQL 模式和 Spring 中心事务；只有原有 SysUser SQL 的 SYSDATE 做测试时钟适配，撤销 SQL 未被改写。新增覆盖：250 条记录中前 100 条持续失败，其余 150 条在随后两批获交付；新版本从 300 秒旧退避重新即时准入；旧确认及旧失败更新返回 0，不修改新记录；模拟两个消费者重复持有/交付同一记录；失败退避最高 300 秒、同版本不清除退避；1,001 个去重账号只调用 3 次批量 versions 查询，逐用户 revision 查询 0 次；空/重复/缺失 ID；Redis 已成功但确认失败后重复交付可恢复。保留回滚不发送、提交后 Redis 失败恢复、账号禁用/删除/改密及已准入身份不变测试。`SystemTokenFilterAdmissionTest` 保留已准入 redispatch 复用与后续请求重新验证。

这不是实际部署多实例/真实 MySQL 测试；多实例交错用延迟旧执行者模拟，Lua 幂等另由真实隔离 Redis 验证。

### 真实隔离 Redis 容量证据

`RedisRevocationCapacityTest` 通过自有随机 loopback 端口和临时目录启动/停止 Redis 8.10.2，禁用持久化，不连接/清理用户现有 Redis。Java21；独立客户端分别执行撤销和连续 PING。每种规模 5 个用户样本，90% 旧版本、10% 新版本活跃会话及 1 个失效索引项，使用实际 UserSessionStore.issue/revoke；验证旧会话删除、新会话保留、失效索引清理、重复/延迟旧版本撤销不误删、发行旧版本被拒，以及最终索引排空。耗时为客户端端到端 wall time，含 Redis/网络/客户端调度，首次脚本加载也计入；PING 样本很少，不是生产 p99 或压测结论。

详细最终测量及命令结果追加于下方。可复跑原始结果为 `target/validation/redis-revocation-capacity.json`，target 不作为生产数据或部署证据。

结论：Lua 遍历成本随用户会话数增长，万级会话对其他命令有约几十毫秒阻塞，不能称为无风险。当前未获得生产单用户活跃会话分布、Redis 并发负载及响应 SLO，测量不足以确定合理的产品登录上限；本轮不增加会话上限，不拆分原子脚本。若实际出现万级单用户会话或该阻塞超出共享 Redis 延迟预算，应另行决定容量边界/原子限额；不能用本机样本证明生产安全。

epoch 仍无 TTL：其值是账号版本的发行下界。会话到期或账号删除后直接删除它，可能让延迟旧发行再次写入；账号 DB fence 虽仍保护准入，不等于保留了 Redis 发行保证。目前每个曾发行/撤销账号保留一个小键，没有自动清理，用户数量增长会增加键数。清理前须证明旧发行已终止、用户 ID 不复用、认证版本不回退；本轮不随意添加 TTL。

### 迁移与部署边界

新增 `V20__session_revocation_retry_schedule.sql`，只添加 next_attempt_at 与索引；未改既有 V19。尚未应用 V19 的环境先按序应用 V19/V20，已有 V19 的环境仅需 V20；现有记录获得默认当前时间，立即可被轮询。部署前必须确保 V20 已就绪，旧结构无法执行新 pending SQL。本轮没有执行真实迁移、重启用户服务、提交或推送。可回滚新 Java/XML 至旧版本，新增列/索引可以保留，不应删除未确认记录。

未验证：真实 MySQL 方言/索引执行计划与迁移、多节点部署、生产会话规模和 Redis 延迟 SLO、Redis Cluster。前端协议不变，本轮未重新运行 UI 测试/build 或浏览器验收。打包 smoke 只验证隔离 fixture 启动与路由，不代替 system=true 的真实数据库迁移验收。

### 最终执行结果

| 活跃会话数 | 撤销中位数 ms | 撤销最大 ms | 重复撤销中位数 ms | 并发 PING 最大 ms |
| --- | --- | --- | --- | --- |
| 10 | 0.849 | 2.110 | 1.170 | 1.449 |
| 100 | 1.136 | 2.190 | 0.740 | 4.232 |
| 1000 | 1.975 | 2.280 | 0.301 | 2.117 |
| 10000 | 19.489 | 25.479 | 0.924 | 25.486 |

最终 verify 样本，单独 PING 基线 p95 0.774ms。此前两次独立执行万级撤销中位数约 19–20ms、最大约 20–27ms；本地调度有波动，小样本不能解释为严格的服务器执行时长。

- IDEA 定向格式化及 `scripts/format-java --check`：4 个修改/新增 Java 通过。
- `mvn -B -ntp test -Dtest=SessionAdmissionTest,IndexedRedisSessionTest,RedisRevocationCapacityTest,SystemTokenFilterAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false -Dmes.test.redis=true`：11 项通过，0 失败/错误/跳过；日志 `/tmp/mes-revoke-target-last.log`。
- `mvn -B -ntp verify -Dmes.test.redis=true`：233 项，229 通过，4 真实 MySQL opt-in 跳过，0 失败/错误，BUILD SUCCESS；日志 `/tmp/mes-revoke-verify-last.log`。包含当前既有代码生成/响应契约等工作区测试，不把其他任务产物算作本轮改动。
- 首次定向验证暴露测试 Supplier 缺返回值、空值检测 List.of 的 contains(null) 行为、H2 不支持 IF 表达式及 SQL 注释分割问题；已修复，采用 MySQL/H2 均支持的 CASE，最终测试没有对撤销 SQL 做方言替换。以上为开发迭代失败，不作为通过证据。
- 最终打包 `/usr/bin/python3 scripts/smoke-packaged.py`：10 通过、0 失败，自有隔离 fixture Java 进程；日志 `/tmp/mes-revoke-smoke-last.log`。
- 两仓 `git diff --check` 通过；结束分支/提交与本节开始相同，没有执行 Git 写操作，前端工作区仍清洁。本节文档追加后再次执行检查通过。

文档更新：本报告追加本节源码、Fixture、隔离 Redis 表格与部署边界；`docs/development/request-admission.md` 同步记录含义、公平排序/退避、批量查询、V20 与 epoch 保留策略。实际修改撤销机制代码，未改变登录数量、请求授权、事务或前端契约。

## 2026-10-11 风险修复回执复审

本次核对当前 Service/Mapper/XML/V20、SessionAdmissionTest、RedisRevocationCapacityTest，以及留存的 `/tmp/mes-revoke-target-last.log`、`/tmp/mes-revoke-verify-last.log`、smoke 日志与容量 JSON；未重跑这些测试，未修改业务代码或执行部署。现有日志支持定向 11 项通过、verify 总计 233 项且 4 项跳过、smoke 10 项通过；容量表与 JSON 的 5 次样本一致。上述证据对应原执行时工作区，后续权限空值修复已有独立测试记录，不将旧 verify 说成覆盖之后全部变更的重新验收。

结论：本轮三项机制修复与容量测量可接受，未发现需要重新设计的新增阻断问题。到期时间排序与失败退避解决已发现的固定用户 ID 饥饿；较新版本重置等待，旧确认/失败更新按版本隔离；批量版本查询保留账号事务一致性。记录含义与数据库已提交的错误语义已澄清。保留 Lua、暂不限制登录数量的结论与测量局限相符，不等于生产容量安全证明。

保留边界：300 秒是退避上限，不是全队列完成时间保证，仍受轮询、积压、实例调度和故障持续时间影响。1,001 个账号只有 3 次版本查询，但另有 3 次批量入队与 1,001 次提交后同步 Redis 撤销；大批量账号操作的 HTTP 耗时并未由此验收，不应描述成总计只有 3 次 I/O。真实 MySQL 的 V20 迁移、索引计划和顺序赋值语义仍须部署前验证。此次只补充回执评价，`git diff --check` 通过。

## 2026-10-11 实施：业务授权与路由元数据缓存

> 历史记录：下述路由代际、V21 运行依赖、持久失效交付、防回填及请求 reservation 保证已由同日后续“路由缓存简化”用户决定取代；不再作为现行约束。账号安全与授权规则仍有效。

本节覆盖前文“业务每请求读取全菜单”“HTTP 事务 begin 重读工厂/数据源”“6+2 查询预算”的当前实现结论，保留历史测试时间线。来源为用户本轮明确决定及 ktg-mes FactoryAccessService.executionContext / FactoryMetadataCache / ManagedDatasourceService 的只读机制参考，不迁入 ERP、默认库、JWT 或外部源。

### 实际改动及产品边界

- `SystemPermissionService.scopedPermissions` 不再读取全菜单或扣除平台菜单代码；当前账号/有效角色及按厂权限继续逐请求查询，请求内复用。URL 平台管理须 PLATFORM 角色、对应平台权限，工厂业务使用平台业务权限与所属工厂角色权限并集；共享/私有资源及部门 dataScope 不变，无编号1绕过。
- 菜单归属仍用于导航、角色分配及启动/菜单编辑事务审查。按钮继承父菜单（忽略按钮自身 URL），/api 前缀按完整路径剥离。启动结合实际 AUTHORIZED URL 审查权限码跨范围复用，并检查所有非删除工厂角色的平台菜单授权（含当前停用角色）。发现问题明确报错，不自动删授权；真实存量数据本轮未审查。角色分配原约束保留。之前可能被静默过滤的错误配置现在会阻止启动，这是明确的迁移行为变化。
- `RoutingMetadataStore` 用共享 namespace hash 缓存路由必需定义（passwordCipher 保持加密），一次 Lua 读取代际/epoch/payload，命中不查工厂/数据源。缺失用 `RoutingMetadataMapper.definition` 一次 JOIN 加载、CAS 发布；不存在的请求工厂 ID 不写共享 hash，避免任意 ID 使缓存无限增长。多个同时 miss 可各自读取 DB，但同代际发布等价，失效/重建后旧读取发布被拒。没有缓存运行对象或权限。
- V21 `sys_routing_metadata` 是中心配置代际/失效交付确认单行。管理增改删、启停、解绑与推进代际同事务，提交后统一 Redis 失效；失败仍有持久未确认意图，后台 5 秒重送/条件确认。TTL 300 秒仅兜底，旧确认不确认新代际、旧失败不覆盖新记录。
- 已有精简账号/角色 SELECT 同时投影最低路由代际到 `AdmissionAccount`，不是新增 authRevision，也不进入 Redis 身份。Redis READ 原子清空低于该代际的内容，因此 DB 提交后失效失败也不能让后续请求用旧缓存准入。没有新增独立 HTTP 中心查询；非 HTTP 无该投影时独立读取代际。Redis 读/发布失败或连续 CAS 冲突拒绝 503，无本地旧值兜底。
- 提交后失效失败为 503/ROUTING_INVALIDATION_PENDING，配置已经保存，不应重复提交；不宣称回滚或 DB/Redis 原子。成功提交后也保留意图到后台持久确认。退休回调先于可能失败的失效回调，Redis 故障不能跳过本节点池退休。
- `BusinessRequestSnapshot` 存于 HTTP 请求属性，Core Scope 不变。入口保留版本 reservation，但不建池/取连接；Spring begin 复用快照懒取租约，事务/SQL/cache guard 不读新管理配置。同 HTTP 多事务及 REQUIRES_NEW 使用原快照，非 HTTP 独立事务新准入。请求结束/ASYNC onComplete 释放 reservation，异常及线程 Scope/PageHelper 清理保留。
- `RuntimeRegistry` 按具体 runtime 保留使用资格；旧池退休不影响已有 snapshot/lease，甚至旧冷快照在新版本出现后也能创建退休池而不替换新 current。同版本停用再启用也创建新 active 池，新请求不加入旧退休池；旧 poolId 的关闭不会删除新池物理证据。旧 owner 的 reservation（尚未建池也算）和 lease 未排空不移交同数据源身份。
- Managed 准备采用版本分段锁，同组并发只建 1 池；物理 JDBC 检查在短全局发布锁外，发布原子检查跨源物理库重复。失败准备关闭未发布池。普通工厂名称编辑失效 metadata 而不退休池；绑定/状态/版本变化退休，后台核对负责跨节点闲池回收。2 DataSource / 2 SessionFactory / 2 TM 和固定包扫描不变。

随机 Bearer、有效期/不续期、会话撤销、每请求账号/角色/权限、资源 owner/scope、部门范围、前端选厂/旧响应保护均未改。本轮没有改变前端实现、Quartz 真实执行器或 OpenAPI 消费者，也没有建设在线迁库系统。切库允许在途旧请求写旧库、新请求写新库的重叠，需部署/运维另行安排。

### 请求预算与性能证据边界

条件：有效 Bearer、显式 X-Factory-Id、metadata 命中、池已暖、工单分页 offset=0/limit=20，非空结果。

| 情况 | Redis 正常调用 | 中心 SELECT | 业务 SELECT |
| --- | --- | --- | --- |
| 暖工单分页 | 会话 read 1；路由 READ Lua 1 | 2：账号/角色/最低代际 1，当前厂权限 1 | 2：count + list |
| metadata 首次 miss | 上述 + PUBLISH Lua 1 | 上述 + 工厂/数据源 JOIN 1 | 暖池仍 2 |
| 冷池/缺显式选厂/非 HTTP | 不能套用暖请求预算 | 无头可能多偏好读 1；default miss 多候选和定义；非 HTTP 多代际读 | 冷池 JDBC 初始化与 server_uuid/DATABASE 探测另计 |

`ProWorkorderRequestBudgetTest` 执行实际 TokenService/SystemTokenFilter/入口授权/Managed 快照/事务/分页 Mapper，并逐请求断言 Redis 调用与真实 StatementHandler SQL；首请求 3 中心、后续 2 中心，业务均 2，无完整用户密码/全菜单/暖 metadata SQL。相对同链路原 6+2 去除 4 次中心 SELECT。原最初 16+2 是旧开发库 HTTP 证据，不能直接与新 H2 样本比较耗时。

最终结果与测试命令在下方记录。JSON 为 target/validation/pro-workorder-request-budget.json；H2+synthetic Redis、无 HTTP socket，耗时含 JVM/fixture 调度，仅回归参考，不是真实服务延迟证明。单独的隔离 Redis 用例证明真实 Lua，而不把预算 fixture 说成真实 Redis/多厂 MySQL 性能。

### 新增和保留验证

- RoutingMetadataRedisTest：自有随机 loopback Redis、临时目录、两个独立连接；失效传播、迟到回填 CAS、控制 hash 消失重建、延迟旧失效、事务回滚不交付、提交后失效失败持久恢复、最低代际清旧缓存、旧确认/失败不影响新代际、读/发布故障无 fallback。DB 为 H2。
- ManagedColdPoolTest：真实 Managed 准备/锁路径，Druid 与物理 JDBC probe 为 mock；16 并发请求仅创建 1 池；旧冷 snapshot 在新版本之后仍能完成并关闭旧池；同版本重新准入不使用退休池，旧池独立关闭；探测失败关闭未发布池。
- RequestSnapshotTransactionTest：两个独立 H2 库，真实 Spring begin/suspend/resume/commit/rollback/cleanup；退休后第一次连接、同请求多事务/REQUIRES_NEW、异常回滚、切厂拒绝、A/B 并发、请求异常/ASYNC 完成释放及冷 owner 移交拒绝。
- RoleScopePermissionTest：真实按角色/厂 Mapper SQL、合并权限及后续撤权、业务授权没有全菜单调用、URL 平台负例、历史错误授权拒绝、跨范围同码拒绝、按钮父归属、分配/导航保留。
- 原 DedicatedDatabasePersistenceTest、PersistenceDeepAuditTest、SessionAdmissionTest、安全 HTTP、资源/部门范围、装配与生成器测试继续通过，没有删除隔离/事务负例以降低测试要求。ManagedRuntimeMySqlTest 的旧 resolve 建池断言已迁到真实 begin；真实 MySQL opt-in 没有启用。

### 部署前要求与未验证边界

V21 是新的中心迁移文件，仅新增恢复状态表/单行；未执行真实迁移。运行新代码前须按序准备 V21，缺表不提供旧结构兼容。共享中心库的各节点须相同 namespace/JDBC URL 表示；不同环境/项目须使用独立 mes.system.cache-namespace。所有管理写节点须升级并推进代际，旧节点/人工 SQL 不具备即时失效协议；混合版本上线、备份回退或手工绕过需受控安排，不能用 TTL 宣称一致。

未验证：真实 MySQL V21/SQL 计划、多节点部署、真实管理变更/池故障、真实同环境 HTTP 延迟、新旧库数据一致性、Redis Cluster/故障转移/备份恢复、浏览器/UI。没有访问/修改生产、执行真实迁移、重启用户服务、Git 提交/推送。前端源码未改，本轮不重跑 UI test/build；旧响应保护原验收不等于此次浏览器重测。packaged smoke 用 system/platform/runtime=false 的隔离默认进程，无真实 Redis/JDBC，不代替 system=true 的部署验收。

开始/当前后端 main/7fb750199d7088ce2145c5f78750260c15f2d363；参考 ktg-mes master/42a81cc577dc77b05a6a67f6be0a9466e40784e1，参考 application.yml 已有修改只读。既有 Codegen Skill/codegen 文档、AGENTS 和性能报告修改保留，本轮只追加/修改相关规则段；没有写 application.yml。工作开始曾记录配置 dirty，最终该文件未显示差异，来源无法由本轮工具确定，不将其变化算作本轮实现。前端工作区清洁。

### 本轮最终执行结果

- IDEA 定向格式化及 `./scripts/format-java --check`：30 个本轮修改/新增 Java 文件通过（检查日志 `/tmp/mes-route-format-check-complete.log`）；没有格式化无关 Java。
- 定向最终池/事务/预算回归：`mvn -B -ntp test -Dtest=ManagedColdPoolTest,RequestSnapshotTransactionTest,ProWorkorderRequestBudgetTest,PersistenceDeepAuditTest,DedicatedDatabasePersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`：41 项通过，0 失败/错误/跳过，含同版本重新准入；日志 `/tmp/mes-route-versions2.log`。后续仅补齐程序式注册的冷 reservation owner 保护与未知工厂不入缓存，再由最终全量 verify 覆盖。
- `mvn -B -ntp test -Dmes.test.redis=true`：242 项，238 通过、4 个真实 MySQL opt-in 跳过、0 失败/错误；日志 `/tmp/mes-route-test-final.log`。这是同版本重新准入补充前的全量 test，最终补充由下一条 verify 覆盖，不能将这一条说成最终源码的单独 test 复跑。
- 最终 `mvn -B -ntp verify -Dmes.test.redis=true`：243 项，239 通过、4 个真实 MySQL opt-in 跳过、0 失败/错误，BUILD SUCCESS；日志 `/tmp/mes-route-verify-final-retry.log`。包括最终缓存未知 ID 不发布、同版本新旧池、Lua 与事务用例，verify 已执行 Maven test 阶段及最终打包。
- 真实隔离 Redis：8.10.2，自有临时端口/目录、禁用持久化，两个独立 Lettuce 客户端，RoutingMetadataRedisTest 通过。未接触用户现有 Redis；另包含既有会话 Lua 验证。与 H2 中心事务混合，不能称为真实 MySQL 多实例验收。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10 通过、0 失败；日志 `/tmp/mes-route-smoke-final-retry.log`，产物 target/validation/packaged-smoke.json。自有默认隔离进程执行 loopback HTTP，未重启用户服务。
- 两仓 `git diff --check` 通过；结束后端 HEAD 仍 7fb750199d7088ce2145c5f78750260c15f2d363，前端工作区清洁；未执行 Git 写操作。
- 最终工单 fixture 20 次样本（5 次预热之外）：p50 4.560ms、p95 6.943ms；实际预算 Redis 逻辑调用 2、中心 SELECT 2、业务 SELECT 2。逻辑调用不是 Redis 脚本内部命令数；脚本首次加载/失败重试可增加 wire 命令。无真实 HTTP/Redis/MySQL 时延对测，不对外承诺该毫秒数。

首次全量验证暴露 InfrastructureContextTest 的“权限服务零调用”旧断言（启动现在审查 catalog），最初角色用例仍期待业务权限集合的旧菜单减法；均迁移到真实新入口和 URL 负例。冷池 fixture 初版 mock Connection 与 DruidPooledConnection 协变返回不符，已修正。最终运行不保留这些失真断言。上述迭代失败不算通过证据。

补充最终回执核对：此前一次完整 verify 在最后追加“未知工厂不写共享 hash”的测试断言编译前启动，因此不把那次通过作为新增断言的覆盖证据。重新完整 verify（/tmp/mes-route-verify-receipt.log）中新增 RoutingMetadataRedisTest（含 UNKNOWN 断言）通过，但已有 RedisRevocationCapacityTest 执行约 3499 秒并在新版本会话存活检查失败；用例会话绝对有效期为 600 秒，结果与长时间运行跨越到期相符，未据此修改会话实现或放宽断言，也未证明具体系统暂停原因。随后不改源码再次完整 verify（最终日志 /tmp/mes-route-verify-final-retry.log）约 49.6 秒通过，243 项/4 跳过，UNKNOWN 断言已编译并通过；最终打包 smoke 10/10。容量失败和之前通过均保留，最终结论采用本次覆盖完整源码的重跑。

### 同日独立复核：是否过度设计

只读核对 V21、RoutingMetadataMapper、FactoryMetadataCache 和 RoutingMetadataStore，
没有重新运行测试。单行 generation 是跨节点路由配置版本的持久事实；
delivered_generation 表达 Redis 交付确认，两者职责不同。请求账号准入顺带读取
generation，保证失效交付失败时新请求仍可拒绝旧缓存；后台交付不承担中止已准入请求。
因此新增单行表本身不判为过度设计；无独立请求 SQL 不代表代际投影没有数据库成本。
epoch 防 Redis hash 过期/删除后，同代际旧发布混入新缓存实例，不只是重复数据库版本。

发现可收敛行为：changed 成功失效不立即持久确认，后台再 invalidate；脚本条件
g<=ARGV[1] 在同代际重复交付也 DEL 整个 hash 并更换 epoch。提交后新请求刚填好的
暖缓存会被随后确认重试再次清空；多节点重复交付或确认失败可重复产生冷缓存和 CAS
冲突。建议同代际且已有合法 epoch 的交付幂等，只有代际落后/状态缺失才清空重建；
需保留 hash 丢失重建和迟到旧发布防护测试，不直接把所有 <= 机械改为 <。

全局单行代际及整 hash 失效会使一个工厂修改影响其他工厂缓存，也串行化管理写入，
目前管理低频可接受，不据此提前拆分每工厂版本或引入通用事件框架。固定 5 秒轮询
每实例重复读取属于有限维护成本；持续失败应审查退避和日志限频。当前建议是保留
最小持久代际/恢复机制，优化重复失效；未证明真实性能、MySQL 多实例或全部池并发正确性。

## 2026-10-11 后续实施：路由缓存简化

用户确认低频配置接受短暂 DB/缓存不一致。本节取代此前路由代际、V21 运行依赖、持久失效、防回填和请求 reservation 约束；账号安全撤销不变。SOURCE_PRODUCT_PORT：保留请求内绑定与节点池方向，不照搬 ktg-mes 的 hash 共用 TTL 或 generation Lua。

### 删除及保留

- 删除 AdmissionAccount 与账号 SQL 的路由代际投影，复用原 SysUserResult 的精简无密码查询。RoutingMetadataMapper 只保留绑定 JOIN，默认候选及定义也合为一次 JOIN；旧 ManagedFactoryExtraMapper/XML 删除，默认选择测试迁到真实新入口。
- RoutingMetadataStore 删除 generation/epoch/Redis Lua；FactoryMetadataCache 删除代际推进、确认/失败/轮询恢复与确认事务；删除 ROUTING_INVALIDATION_PENDING。不新增路由 outbox 或框架。
- 删除 RuntimeRegistry.Reservation、registerReserved、hasVersion 及历史冷池保留。请求只保存 binding 与懒取租约函数，删除专为 reservation 释放存在的 AsyncListener。已取得事务租约才保护旧池，包括尚未取 JDBC 连接者。新活动版本拒绝旧候选替换；请求尚未开事务不保证能取得旧池。
- 保留平台 URL/角色证明、业务权限合并、资源归属、业务授权去全菜单、Redis 身份、每请求账号/角色/权限、authRevision、会话撤销 outbox/Lua/恢复、V19/V20 中文备注和有效期。两个 DS/SF/TM、固定扫描、无默认回退、双向跨库、挂起恢复、异常清理保留。
- 暖池直接租借；同数据源身份分段锁冷创建，失败候选只关闭一次，关闭失败可重试。仅已有租约排空关闭；同版本新旧池独立，旧池关闭不删除新实例物理证据。普通名称编辑不退休池。

### 缓存及管理变更语义

Redis string 独立键 `factory-routing:entry:binding:<id>` 与 `factory-routing:entry:default`，沿用 SystemCacheNamespace 项目/环境隔离。显式基础配置 `mes.datasource.metadata-ttl-seconds: 300`。每条 SET 单独带固定 TTL，GET 不续期，其他键写入不延长旧键。未知 ID/无默认候选不缓存；只存定义与加密 cipher，不存明文密码、运行资源或授权。

审查 ManagedDatasourceService.save/status/delete/saveFactory/deleteFactory：原事务注册提交后淘汰，数据源变更查询相关绑定厂列表，工厂变更按自身 ID，全部清理 default；回滚不淘汰。淘汰失败记录 ERROR，接口返回原修改成功结果，不伪装回滚、不鼓励重复提交。不保证全节点立即生效。允许旧读取在淘汰后回填，TTL 从回填发布时间起算，300 秒不是从管理提交起算的绝对收敛上限，也不限制在途请求寿命。Redis 读/发布失败 503，缓存过期后 DB 失败明确失败；无本地过期兜底。

旧租约完成不受管理变化干涉。退休池不接受新租约；旧快照不阻止退休关闭，也不保证新事务成功。短暂缓存仍启用时可能创建新的活动池，原跨节点池核对及条目过期使其收敛，不承诺即时停用或在线迁库。真正切库仍可能有新旧库写入重叠。

### 查询预算与链路

入口 Redis 恢复身份 → 中心精简账号/角色准入 → Redis 定义（miss 一次 JOIN）→ 请求授权并保存 binding → Spring begin 取池租约 → SQL/固定身份 guard → completion 归还/排空。

显式 Factory、AUTHORIZED 工单页、暖池，不含后台维护和启动审查：

| 元数据状态 | Redis 逻辑往返 | 中心 SELECT | 业务 SELECT |
| --- | --- | --- | --- |
| 命中 | 2：身份 HGET + 定义 GET | 2：账号/角色 + 当前权限 | 2：count + page |
| 未命中 | 3：上述 GET + SET 带 TTL | 3：上述查询 + 绑定 JOIN | 2 |

请求内与事务 begin 不重复 metadata I/O。偏好可另加中心查询，default miss 一次 JOIN；非 HTTP 独立事务不读路由代际。冷池另计 JDBC 建连与业务/中心各一次物理身份探测；定期池核对 2 次中心查询另计。ProWorkorderRequestBudgetTest 首请求断言中心 3 次，后续 2 次，并拒绝全菜单、密码字段及重复配置查询。

### 迁移、工作区与部署边界

后端 main/7fb750199d7088ce2145c5f78750260c15f2d363；UI main/6702f527be7c0e58acf424255eacbe0b196a5799（干净、未修改）；ktg-mes master/42a81cc577dc77b05a6a67f6be0a9466e40784e1 只读，原 application.yml 修改未触碰。原 codegen/Skill、独立架构/过度设计审查等工作区改动保留。

V21 此前新增未跟踪，Git 全历史无该文件提交；此前执行记录只有 H2，未执行真实迁移。但不能在禁止访问用户真实数据库的范围内确认是否由他人手工应用，因此按用户要求保留原文件、不重写可能已应用迁移、不新增 DROP。代码和测试无该表依赖；新安装执行完整历史迁移仍会建立无用途历史表。确认全部目标环境未应用后才可另行移除文件；真实表清理另行授权。本轮没有真实迁移、用户服务重启、提交/推送、前端修改或生产访问。

### 实际验证

- IDEA 定向格式化及 `--check`：15 个 Java 通过，日志 `/tmp/mes-routing-simple-format-check.log`。
- 首次定向失败：拆除投影时填错 SysUser 类型路径；改为复用原结果映射。重跑定向 27 项通过，日志 `/tmp/mes-routing-simple-target2.log`。其后补充关闭失败重试测试，由最终 verify 覆盖。
- 首次 `clean verify` 失败：装配要求的显式 TTL 配置缺失；基础 YAML 补齐后重跑，未放宽配置要求。日志 `/tmp/mes-routing-simple-verify.log`。
- 最终 `mvn -B -ntp verify -Dmes.test.redis=true`：全 reactor 250 项，246 通过、4 个 MySQL opt-in 跳过，0 失败/错误。以本次命令日志 `/tmp/mes-routing-simple-verify-final.log` 为证据，不混入其他运行的 XML 报告。
- 自有 loopback Redis、两个独立 Lettuce 客户端：独立 TTL、命中/其他写入不续期、提交淘汰/回滚、失败后过期、允许回填、未知 ID、读取/发布故障及过期后 DB 故障通过；数据库为 H2。会话真实 Lua/容量测试通过，安全撤销没有误删。
- ManagedColdPoolTest 5 项通过：16 并发创建 1 池、旧候选拒绝/清理、同版本租约排空、探测失败、关闭失败重试且不影响活动池。RequestSnapshotTransactionTest 3 项及既有 20 项分库/13 项深审、权限/HTTP/会话专项通过。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10/10，日志 `/tmp/mes-routing-simple-smoke.log`。自有默认 loopback 进程，无真实 Redis/JDBC，不重启用户服务。
- 工单 H2/synthetic Redis 预算 2 中心 + 2 业务；20 个计时样本 p50 4.644ms、p95 16.662ms。仅 Fixture 回归，不与早期 HTTP 16+2/一秒实测计算提升幅度。
- 两仓 `git diff --check` 通过。真实 MySQL/执行计划、多实例服务部署、Redis Cluster/故障转移/备份恢复、真实同环境 HTTP 性能与浏览器/UI 未验证。
- 额外检查最终 JAR 及嵌套模块：AdmissionAccount、RuntimeRegistry.Reservation、旧 ManagedFactoryExtraMapper/XML 均不存在，基础 YAML 包含显式 300 秒 TTL。执行期间出现的其他工作区改动（包括 FoundationInitializationTest）未覆盖，测试计数以最终命令日志为准。

## 2026-10-12 失效选厂恢复与候选列表整改

用户确认：以专用异常码进入选厂页面，避免恢复流程扩大；首页和选厂页复用候选请求，候选必须是启用且绑定启用数据源的工厂。

此前只读核查中心数据：当前 admin 的偏好为工厂 2，该工厂启用但 datasource_id 为空，JOIN 投影的 datasource 为空与数据库状态一致。
这项事实能解释当前 binding 的 null，不能据此认定 Redis 淘汰失败；本轮未完成对应 Redis 条目的读比对，未手工清缓存或修改用户偏好。
另一个账号在离开页面后工厂被停用是用户报告的触发场景，不将两者混作同一条已复现数据库记录。

### 修改范围与行为

- 新增 `ErrorCode.FACTORY_SELECTION_REQUIRED`，HTTP 为 422。ErrorWriter/Advice 仅将业务 URL 范围的工厂缺失、删除、停用、未配置运行时转成该码；内部原因和异常链不变。
- request 只识别此专用码；读取错误响应体后先校验当前身份/工厂/revision，旧错误不能打断新上下文。普通业务 422、403、503 不触发选厂。
- Auth 保留 Token/有效期及平台授权，清空失效工厂 ID、业务菜单/权限并递增上下文版本。登录/恢复期间出现此码不注销，Router/App 进入 `/select-factory`。
- 选厂页和原首页恢复流程复用 `loadFactoryOptions()` 及 `/api/foundation/factories`。选定可用工厂后回工作台，不重放失败请求；零候选可暂不选厂进入平台/个人工作台。
- ManagedFactoryMapper.enabled 在分页前过滤 ENABLED 工厂与已绑定 ENABLED 数据源。后台管理 all/list 不变；未绑定、悬空引用、数据源停用及工厂停用不出现在候选中。
- 两仓 AGENTS 与请求准入文档同步恢复契约。不修改 Schema、迁移、缓存 TTL/淘汰策略、租约或授权边界，不修改生成器。

### 验证与边界

- 第一次 `mvn -B -ntp test` 失败于 BusinessDirectoryTest 的旧缺厂 HTTP 400 断言；替换为专用 422 契约并保留权限/初始化负例，未放宽行为校验。
- 最终 `mvn -B -ntp -Dmes.test.redis=true verify`：253 项，249 通过、4 个真实 MySQL opt-in 跳过，0 失败/错误。日志 `/tmp/mes-factory-recovery-verify.log`。
- 新增/扩展 HTTP 契约、禁用/删除后重新选择、候选 SQL 在分页前过滤（H2）、普通错误不映射、内部原因保留；既有分库/事务/授权/隔离 Redis 验证通过。
- Node v22.23.1：`npm run test` 132 通过，`npm run build` 通过（既有 bundle 大小提示）；8 个变更前端文件定向 ESLint 通过。新增请求专用码/普通 422 区分、旧错误不干扰新上下文、登录和刷新保留身份、重新选厂及零候选回归。
- IDEA 9 个变更 Java 定向格式化与 `--check` 通过；`scripts/smoke-packaged.py` 自有隔离进程 10/10；两仓 `git diff --check` 通过。
- 尚未验证用户服务编译重启后的真实浏览器闭环与真实 MySQL 新 SQL；未重启用户服务、修改真实数据库、手工淘汰缓存、提交或推送。用户已有配置、密码工具和性能报告改动保留。回滚范围为本节列出的代码/测试/规则变更，无数据库迁移。

## 2026-10-12 用户要求恢复 OpenAPI 移除部分

### 目标与来源

用户明确指出没有确认移除客户端管理、密钥/Token、API 授权、独立外部认证链与管理页面，要求回滚这些移除部分。
恢复使用本仓库已迁入版本：后端 `1c2ada6^`、前端 `cef3072^`，不回退整个提交，不改变此前请求准入/路由缓存/工厂恢复工作。

### 修改范围

- 恢复 common/system/framework/admin 的客户端模型、DTO/VO、Mapper/XML、具名 Spring 事务 Service、客户端 Controller、Redis Token/Lua、限流、API grants、独立外部 SecurityFilterChain、数据范围策略开关与目录/诊断。
- 恢复前端 OpenAPI 应用页面与 API、侧栏图标。一次性 Secret 只在创建/重置时展示，关闭/离开即清空；不保存到浏览器存储。
- SystemTokenFilter 跳过完整 /open-api 路径段；FoundationAccessInterceptor 明确处理外部 OpenApiPrincipal，禁止把 appKey 当成后台用户或解析其选厂偏好。EndpointCatalog 校验独立外部入口声明，不将其划到 MES 工厂授权链。平台/工厂权限规则与双库拓扑不变。
- 删除被取代的 mes-openapi 仅文档模块/reactor/profile 与重复元数据模型，标准构建包含完整实现，运行仍由 mes.system.enabled/open-api.enabled 控制。当前已有 open-api.enabled 取值保留，补齐原限流/TTL/namespace/data-scope 配置。
- 按当前单表 Extra 规则，旧 OpenApiClientExtraMapper 的三种单表访问并入主 Mapper/XML；Service 仍承担凭证规则与事务。没有调用 Codegen 替代源产品迁移，没有创建第二套单表 CRUD。
- V10、V16 等历史迁移字节不改。新增 V22 恢复 OpenAPI 菜单/五个按钮，保留客户端及 API grants；V16 已删除的角色关联无法推断，不自动授权，部署后须管理员重新分配。
- 替换旧退役/仅文档验证为恢复验证，更新打包检查确认恢复 Controller 存在且不残留单表 Extra。Source Product MIT 随 framework JAR 及文档保留。README、两仓 AGENTS、Platform 指南、依赖来源文档同步；历史退役结论标明已由用户恢复决定取代。

### 验证与限制

- 定向 OpenApiRestorationTest：生产 Spring Security/Service/Mapper XML，私有 loopback Redis，H2 中心表，内部身份为明确 synthetic fixture。覆盖应用默认禁用、启用、Token 发行、逐 API 授权与授权撤销、两种 Token 互不通用、撤销 Token、停用及重新启用旧 Token 拒绝、重置 Secret 后凭证/Token 失效、目录与敏感字段裁剪。
- OpenApiDataScopeVerificationTest 7 项：关闭策略仍保留授权，启用要求唯一策略、策略调用/拒绝及诊断豁免。V16→V22 补偿及重复执行测试：菜单恢复、不重复、不销毁客户端/API grants、不自动补发角色授权。
- 首轮恢复编译通过；显式 import 整理时遗漏 JDK 注解及静态导入，已补齐并重跑。新增前端 fixture 的 Vue Options 类型编译错误已修正；不放宽类型检查。
- 首次打包 smoke 在并发构建窗口读取不到目标 JAR，重试后 10/10；为排除被删除类的增量构建残留，最终执行 clean verify 并重新检查打包。
- 真实 MySQL、用户运行服务重启后的浏览器菜单/客户端流程、多实例 Redis/生产部署未验收；未执行真实迁移、重启用户服务、提交或推送。
- 工作期间发现外部追加 next-stage-plan、architecture-review 及 multi-node-deployment-review 文档，保留其内容；不把外部审查证据归入本轮验证。回滚只涉及本节实现/配置/新迁移/测试/文档，无真实数据库回滚。

### 本轮最终检查结果

- `mvn -B -ntp -Dmes.test.redis=true clean verify`：259 项，255 通过、4 项真实 MySQL 跳过，0 失败/错误；日志 `/tmp/mes-openapi-restore-clean-verify.log`。
- IDEA 定向格式化及 `--check`：62 个 Java 文件通过。日志 `/tmp/mes-openapi-final-format-check.log`。
- Node v22.23.1：`npm run test` 134 项通过；`npm run build` 通过，既有大 bundle 提示保留；恢复页面/API/回归测试定向 ESLint 通过。
- 最终 `/usr/bin/python3 scripts/smoke-packaged.py`：10/10，自有隔离启动进程，不连接用户数据库或重启用户服务；检查最终标准 JAR 包含客户端 Controller、恢复后的认证实现，且不残留仅文档模块或单表 Extra。
- 两仓 `git diff --check` 通过。仅完成代码恢复和本地隔离验证；部署须应用 V22、重新分配相关平台菜单权限并重启用户后端，再验收真实页面。


## 2026-10-12 OpenAPI 页面与开发者文档补齐

- 修正前端 `system/openApiClient` 授权弹窗复选行固定高度导致的内容重叠；操作列统一 flex 对齐、间距与按钮尺寸。真实浏览器核对三项操作均为 20px 高且顶部一致，授权选项无重叠；未保存授权、创建应用、重置凭证或切换应用状态。
- 从 Source Product 迁入只读 `/openapi-docs/#start` 页面，替换公司名称及 logo，保留来源/许可记录；接入 Vite 与 Nginx 代理及页面入口。后端显式公开文档资源和专用契约，实际外部 API 认证/授权保持不变。专用 Springdoc delegate 在路径过滤阶段仅接受 `/open-api/`，不修改全局分组配置。
- 用户首次重启后，真实浏览器已验证快速开始、认证、错误码、接口搜索与详情，契约包含 Token 和三项诊断接口。文档不执行 API、不保存凭证。页面证据在 `assets/2026-10-12-openapi/`。
- 后续错误响应示例修正曾因原始 Schema 泛型导致编译失败，已改用显式 `Schema<?>`；清除 code/msg 的成功示例，并使用 OpenAPI 3.1 类型集合表达固定 null 的错误 data。加强的 HTTP 负例发现全局路径配置能纳入内部注解接口，已通过 delegate 路径过滤修正。最终真实嵌入式 HTTP 测试验证内部路径不进入外部文档、错误字段示例及静态资源/调用权限边界。
- 最终 `mvn -B -ntp -Dmes.test.redis=true verify`：260 项，256 通过，4 项真实 MySQL 跳过，0 失败/错误；日志 `/tmp/mes-openapi-docs-verify-fixed.log`。IDEA 四文件格式检查通过。`scripts/smoke-packaged.py --openapi-docs`：14/14，自有隔离进程；日志 `/tmp/mes-openapi-docs-fixed-smoke.log`。
- 本轮前端 test 134 项、build、页面及代理相关定向 ESLint 通过；两仓 diff 检查通过。现有 bundle 大小提示保留。
- 最新后端泛型、错误字段及路径过滤修正尚未由用户重新加载到 8081；隔离 HTTP/打包检查不替代该运行服务验收。未操作真实数据库、重启用户服务、提交或推送。外部新增业务迁移与功能清单修改保留。回滚仅限本节文档页面、代理、专用文档后端及 UI 样式/测试，既有 OpenAPI 恢复不属于本节回滚范围。
