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
