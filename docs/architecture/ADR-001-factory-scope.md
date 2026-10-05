# ADR-001：纯 FactoryScope 与显式执行范围

- 日期：2026-10-05；状态：Accepted。
- 来源问题：旧执行上下文同时表达业务厂、连接版本、外部组织和角色，容易把建立范围误读为授予权限；部分异步入口没有范围，缓存缺范围退为全局。
- 旧系统行为：以 ThreadLocal 承接请求与任务，有嵌套恢复思路，但范围中含运行时/集成字段；非受控线程存在传播缺口。这里只提炼问题，不复用实现。

## 决定与候选方案

采用不可变 `FactoryScope(FactoryId)`。Factory 含企业归属、编码、名称、启用状态，不含连接配置；Enterprise 表达组织上级。选择纯值对象，拒绝让 Factory 对象承担数据库/ERP/用户身份。

`FactoryContext` 只在平台/基础设施边界桥接，Core 的用例和 Port 收显式 Scope。ThreadLocal 不自动继承；bind/current/required/clear，withScope/runWithScope 在 finally 恢复父值。事务由基础设施 pin 当前厂，连 clear 都不能绕过固定范围。

任务使用 `ExecutionScope.PLATFORM` 或 `FACTORY`；PLATFORM 显式清 Scope，FACTORY 必须携带厂身份。受控 executor 有线程/队列边界、显式 capture 和 CallerRuns；执行时清除用户 SecurityContext，最终恢复调用前状态，所以队列饱和也不会借用请求的业务身份。后台能力授权将来需要独立服务账号/任务授权，FactoryScope 本身不会提供它。

候选是全局可变 Session 或 InheritableThreadLocal，前者并发串用，后者隐式跨线程并难以复用线程池，均不采用。无需新增异步框架、Quartz 任务或消息总线。

## 测试与尚未覆盖

`FactoryScopeTest` 验证绑定、缺 Scope、嵌套、异常和事务 pin；`ExecutionScopeTest` 验证 PLATFORM、Factory 必填、工作线程、CallerRuns、身份清理、关闭拒绝。`ArchitectureBoundaryTest` 检查 Core 不引用 ThreadLocal/框架/实现。

尚未实现持久调度、节点崩溃恢复、JobStore、消息投递或业务事件。未来跨线程业务事件必须显式带 FactoryId。当前以一个 Enterprise 的本地部署为范围；多企业身份与目录裁剪另需设计。
