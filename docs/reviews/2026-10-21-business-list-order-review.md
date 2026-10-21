# 业务列表主键倒序审查

审查日期：2026-10-21。范围：`mes-business` 的全部 50 个 Controller，沿列表、目录、候选及嵌套明细入口追踪 Service、Example、Mapper SQL；包括这些入口使用的中心用户目录。平台管理列表、ERP 远端候选契约不在本次修改范围。

## 1. 规则与证据

- 有自增主键的记录列表按该主键 `DESC`，在数据库分页前排序；不使用取回单页后再排序。
- 自增依据来自业务 Flyway 建表/后续迁移及 Mapper，不能仅按 Long 类型判断。特别是 ERP Extra、工单用料、当前执行路径是共享父主键，交接、库存动作、质量放行及完成事实使用 UUID。
- 图节点、路径、单据行号和事件重放属于结构/计算语义；不把它们改成记录列表顺序。聚合统计没有对应单行主键，保留分组字段排序。
- 本次无 Schema、写入 SQL、连接绑定、权限或事务规则变更，不调用真实 ERP，不重启用户服务。

## 2. 逐类结果

| 查询类别 | 结论与调整 |
| --- | --- |
| 基础资料：单位、分类、物料、车间、工作站、安全库存、设备 | selectPage 由 id 升序改 id desc |
| 设备点检保养项目 | subject_id 改 subject_id desc |
| 仓库、库存 | selectPage 由 id 升序改 id desc |
| 领料主单、领料明细、提交尝试 | selectPage 由 id 升序改 id desc；提交命令读取明细的既有顺序保持 |
| 工序、工艺版本 | 正式列表改 id desc，替换 sort 或 published_at 优先排序 |
| 执行单元 | 原 id DESC 保留，修正此前仍为 id 的缓存排序标识 |
| 实际路径节点记录、工序人员能力、节点派工 | 独立记录列表改 id desc；工艺图及选中路径的节点顺序保持 |
| 工单、工单进度、仓储单据 | 既有 id desc 保留 |
| 工单详情中的历史生产批次集合 | id 升序改 id desc |
| 工艺编辑引用的工序/物料、工单适用工艺候选 | 编码/版本优先排序改 id desc，保留原过滤、发布条件及数量上限 |
| 人员候选能力行、有效用户、历史报工用户目录 | 能力行 id desc、中心用户 user_id desc；既有有效账号/角色/工厂权限校验保持 |
| 业务目录的部门、岗位 | dept_id desc、post_id desc；工艺/工序目录本来就是 id desc |
| 投产可用库存 | 增加 id desc；可投产工单本来就是 id DESC |
| 报工流水、报工订正列表、近期报工 | 去掉 create_time 优先，统一 id desc |
| 不良申报当前版本列表、版本历史 | 列表改 id desc；latest 单版本查找仍按 revision_no desc；内部批量质量投影保持既有顺序 |
| 工序不良类型、退回仓库候选 | sort/id 或 id 升序改 id desc |
| 无自增主键的 ERP Extra、工单用料、当前执行路径 | 保留原排序，不扩大本次自增规则 |
| UUID 交接、仓储接收、库存动作、质量放行、执行完成 | 保留时间/业务序号或既有排序 |
| 工艺定义图、实际执行路径、历史批次路径、仓储单据行号 | 保留 display_order / sequence_no / execution_order / line_no 的结构语义 |
| 质量处置重放、库存及取消操作加锁 | 保留原计算/加锁顺序，不改变命令执行语义 |
| 不良汇总、数量汇总、目录按 IDs 批量回显 | 无独立记录分页排序契约，保留分组/索引语义 |

## 3. 缓存与生成契约

修改 selectPage 的同时审查 QueryDto.cacheSort，采用与实际 SQL 相同的倒序标识，使旧升序列表缓存不会命中新查询。手工修改的生成 QueryDto 已按工程规则移除对应文件的 MES-CODEGEN 归属标记。

生成的 Mapper Java/XML 保持原样；不良申报 selectLatest 的固定 SQL 排序由 Service 的 PageHelper.setOrderBy("id desc") 在分页处覆盖，不编辑生成 Mapper。其他生成 Mapper 的列表通过 Service 的 Example.orderByClause 设置排序。

BusinessTemplateRenderer 根据真实主键 metadata 的 autoIncrement 标志产生 defaultOrderBy；Service 与 QueryDto 模板共享这一值。自增主键生成 DESC，非自增主键维持原有方向。未批量重新生成业务文件，未覆盖手工业务规则。

## 4. 验证与边界

- 领料 HTTP Fixture 回归：创建三张主单，验证筛选后第一页 3/2、第二页 1、总数和 B 厂空集；使用真实 Mapper 与分页插件。
- 近期报工 HTTP Fixture：较大 ID 的 create_time 早于较小 ID，仍要求大 ID 在前；保留精度、权限、分页和 A/B 隔离断言。
- 生成模板测试：执行生成 Service 时检查传入 Example.orderByClause 与 QueryDto.cacheSort 都是 id desc。
- 人员目录真实 Mapper Fixture：将既有升序预期迁移为倒序，保留工厂/平台角色资格验证。
- 最终 `mvn -B -ntp verify`：BUILD SUCCESS，耗时 1 分 39 秒。各模块累计 802 项测试，失败 0、错误 0、跳过 48；跳过项包含需显式启用的真实数据库验收。
- 已对全部修改 Java 文件执行项目 IntelliJ formatter，`git diff --check` 通过。旧人员目录、生成 Person 以及生成 GLOBAL CRUD 分页验收中的升序预期已迁移为倒序，保留原业务/权限/隔离断言。
- 上述 Fixture 不代表已部署服务或真实 SIMPLE_B 数据库验收；本次不重启服务。回滚仅需撤销本任务差异，保护原有迁移报告和 V43 工作区内容。

业务代码已修改；用户原有迁移报告和 V43 文件不属于本次修改范围。
