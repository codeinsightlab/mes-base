# 认证、授权与路由改造验收

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
