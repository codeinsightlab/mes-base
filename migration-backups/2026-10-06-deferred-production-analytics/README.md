# 生产监控与人员分析归档

2026-10-06：按用户决定，两项业务从当前产品移除，基础架构先行。

`source.tar.gz` 包含 66 个功能文件和 10 个接入快照；`manifest.json` 记录仓库 HEAD、逐文件 SHA-256、大小和用途。备份已逐文件解压读取并校验哈希。归档路径以 `mes-base/`、`mes-base-ui/` 区分两个仓库，不参与 Maven、Vite、组件或 Mapper 扫描。未收集私有连接配置、凭证、运行目录或业务数据。保留原 Source Product MIT 许可和迁移来源快照。

移除后端整个 mes-mes 模块（两项业务的 Controller/Service/DTO/VO/Mapper/XML/专属测试），移除父 POM 与 admin 依赖、analytics 开关，以及仅供人员分析使用的 SystemUserLookup / UserLookupQuery / SysUserDisplayExtraMapper 与 XML。前端移除两项页面及 API；混合组件回归保留基础组件测试，会话恢复测试改用 TEST ONLY 权限。生产装配验证不再依赖业务 Mapper；已有独立探针继续验证隔离和事务。系统监控、工厂/数据源、同步协调和持久化基础保留。

菜单通过 V15 前向迁移清理；不改 V13 校验和，不删业务数据。已有数据库执行 V15 前仍可能有旧菜单，本次没有执行真实数据库迁移。

后续恢复只以 feature_source 为候选源码；integration_context 用来理解旧接入，不能覆盖当前 POM、配置或历史迁移。需要重新验证 Schema/字典、真实 SQL、Factory/权限/资源范围、响应契约和页面，再以新迁移接入菜单。不直接解压覆盖工作区，也不恢复旧开关作为接入方式。

验证结果记录在 docs/architecture/architecture-slimming-audit.md 的本轮补充中。
