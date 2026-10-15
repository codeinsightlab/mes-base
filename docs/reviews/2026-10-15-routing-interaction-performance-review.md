# 工艺路线交互、时间与新版本链路审查

2026-10-15；mes-base / mes-base-ui，main。用户第1～3项页面优化，第4项新版本API链路审查。后端业务代码只读，保留入场 RoutingServiceImpl 的4行空行改动及日志相关并行修改。未提交、推送、执行写SQL或启动/停止/重启服务。

## 页面调整与时间来源

前端 `src/views/production/routing/index.vue` 原来显式 `close-on-click-modal=false`，现支持点击遮罩、Esc、关闭按钮。存在未保存名称/说明/产品/节点/边时通过 before-close 确认放弃，取消保留草稿。关闭开始即失效详情及选择器请求；切厂/撤权同步清理拓扑弹窗。确认期间上下文改变时不再执行旧关闭回调。

拓扑原来常驻960px详情抽屉，占据节点表前空间。现改为“查看拓扑”按钮，显示节点/连接数量；弹窗最大1440px/95vw，图区域最大70vh，复用 RoutingGraphPreview。其他消费者仍沿用420px默认高度。预览使用当前本地编辑内容，不新增接口或把预览当作保存。嵌套预览关闭不关闭父详情。

前端 AGENTS.md 新增“弹出层失焦关闭（2026-10-15 用户要求）”：点击整个弹层外部关闭，未保存内容先确认，表单内切换焦点或操作下拉不关闭；关闭及上下文变化清理迟到响应。

时间来源：RoutingServiceImpl.publish 使用 LocalDateTime.now()；正式 V3 的 published_at 是 DATETIME(6)，HTTP RoutingVo 直接使用 LocalDateTime。序列化可能包含非零小数秒，历史整秒值不带小数。原页面只 replace('T',' ')，因此混合精度直接可见；这能解释显示格式不一致，不代表时区异常或数据损坏。现复用 utils/ruoyi.ts 的 parseTime，统一展示 YYYY-MM-DD HH:mm:ss，空值显示破折号；不改后端响应、数据库精度或历史值。没有逐行核实用户两条记录的写入来源。

## 新版本入口与真实日志

调用链：routing页面 lifecycle('new-version') → ElMessageBox确认 → api.action(id,'new-version',revision) → POST /api/production/routing/{id}/actions/new-version → 既有HTTP身份/选厂/权限准入 → RoutingController.newVersion（routing:version）→ RoutingServiceImpl.newVersion（businessTransactionManager，READ_COMMITTED）→ cloneVersion → replaceContent → 生成Mapper/XML → 事务提交后缓存清理 → RoutingVo白名单。

源码行为：校验源revision、曾发布事实、同编码最新版本无草稿；复制产品/节点/边到下一版本DRAFT，分配新节点ID并映射边。不调用ERP HTTP，也不执行同步协调器。引用启用校验与同值UPDATE锁仍由 RoutingReferenceRules 实施。V12工厂列治理后的 routing 唯一键为(code,version)，并发插入冲突转换为资源冲突；不建议通过移除锁/权限或改成异步假成功优化。

本地既有 access.log（不是本轮重放）记录：

| 日期时间（+08:00） | 路线 | 服务端耗时ms |
| --- | --- | ---: |
| 2026-10-15 08:14:01.944 | 20 | 440 |
| 2026-10-15 08:16:53.434 | 120 | 537 |
| 2026-10-15 08:19:26.128 | 121 | 576 |
| 2026-10-15 08:20:15.025 | 122 | 425 |

证据：`.local/logs/MacBook-Pro-3.local-30553/access.log`、`.local/logs/MacBook-Pro-3.local-49281/access.log`。122对应 requestId=7256c33d-928a-46a9-b3bc-47d054699e45，HTTP200。这是入口总耗时，包含身份/权限、DB、提交回调等，不是SQL分段计时；没有样本分布，不能宣称p95或把425ms归因到某条SQL。

## 可优化空间（尚未修改后端）

1. **新父单的空子表处理**：cloneVersion刚insertRow，就调用通用replaceContent；后者detailRows查询新路线的node/product/edge三张空表，又删除edge/product空集合。这5次SQL在新路线复制用例中可省略。实现应区分“新父单初始化”与“已有草稿替换”，复用生成Mapper及引用规则，不新增第二套Mapper或移除旧编辑路径的删除逻辑。需覆盖产品、节点、边映射、空图、失效引用、冲突与事务回滚。
2. **同事务重复缓存失效**：insertRow与replaceContent分别dirty()，每次LIST_AND_DETAIL登记提交回调；SpringCommitActions不去重。每个回调调用RedisCache.deleteByPattern两次（list/detail），因此当前复制提交后共4次模式清理。RedisCache使用SCAN，实际成本依Redis键总量与网络而变。可在该业务用例仅登记一次，减少到2次模式清理；不能提前清理或丢失回滚语义。需要真实Redis测量，不能将扫描次数等同于固定往返数。
3. **逐条写入/锁定**：产品P、节点N、连接E逐条insert；引用物料P及去重工序O逐条同值update锁定。集合上限P/N=100、E=400。对非空产品/节点的已发布版本，按当前源码普通路径估算业务SQL次数为15+2P+O+N+E，未计管理库准入、事务命令、缓存、驱动附加行为；并非实测。较大图可考虑正式生成能力的批写，必须保留生成ID的边映射、锁顺序、影响行数与回滚。小图不建议先改基础设施。
4. **页面等待**：POST成功后先await load()刷新列表，再await open(value.id)请求详情，随后并发查询工序/物料候选。用户看到可编辑新草稿的总时间大于POST本身。可独立评估列表与详情并行刷新，但它不会降低该POST的425ms，且需测试关闭/切厂/错误行为。
5. **重复内存校验**：detailRows和graph/replaceContent多次校验同一拓扑；限100节点/400边，先级低于I/O，不凭此解释500ms。

建议先测身份/权限、业务SQL、commit及Redis清理各段，再实施前两项有明确冗余的局部优化并比较同等图规模。未新增长期缓存、第三个数据源、索引或迁移。没有重放真实写接口，以免制造新版本；没有宣称性能已改善。

## 验证与边界

- 前端新建 tests/routingPage.test.ts：3项组件回归覆盖混合时间精度、按需拓扑及父子弹层状态、未保存确认取消/确认、关闭时迟到详情、切厂清理。RoutingGraph原5项继续通过。
- 全前端测试：40文件、208项通过（jsdom/Mock接口，日志 /tmp/mes-routing-ui-test-20261015.log）。
- 构建与最终定向重验：见下方补充结果。
- 真实5173页面打开后跳转登录页；另查已有浏览器会话时Computer Use两次超时，未获得已登录页面。因此真实遮罩点击、实际截图、1440/1920/1280宽度与真实API回归未完成，不把组件测试冒充浏览器验收。
- 未修改Java/SQL，未运行Maven或真实数据库写验收，后端性能只有源码审查及已有访问日志证据。

回滚：单独撤销前端本轮AGENTS新增章节、routing页增量、RoutingGraphPreview高度变量及新增组件测试；不回退入场未提交内容。审查文档独立保留证据。

### 最终检查结果

Node v22.23.1，`npm run build`通过（vue-tsc + Vite；存在大分块提示，无构建错误）。最终关闭回调上下文保护修改后，`npm run test -- tests/routingPage.test.ts tests/routingGraph.test.ts`再次8/8通过；两仓`git diff --check`通过。未重新运行后端测试，因后端代码未改。前端代码职责限于页面交互与展示、共享图组件高度配置；权限/API/业务持久化契约保持现状。没有任何真实性能改造或加速验收结果。

## 2026-10-15 后续实施：新版本初始化优化与分页清理

用户继续授权优化 `/122/actions/new-version`。本节取代上文“尚未修改后端”的实施状态，保留上文原始审查与日志基线。

### 代码变化与边界

- `RoutingServiceImpl.cloneVersion`：新父单插入后直接复用 `validateContentReferences` 与 `writeContentRows`，传入空的既有节点列表。不再调用已有草稿的替换流程，因此省掉新路线 node/product/edge 的3次空查询和 edge/product 的2次空删除。
- `replaceContent`：现有草稿继续读取原节点、清理旧边/产品/已删除节点；复用相同引用校验与写入方法，保留已存在节点ID。新增版本与复制路线共用cloneVersion，二者都受益；普通保存与同步仍保留各自事务和业务逻辑。
- 新父单 `insertRow` 已登记提交后失效，子表初始化不再重复dirty：一次版本复制的缓存失效登记2→1，list/detail模式清理4→2。仍由原Spring提交回调执行，未改通用缓存基础设施或Redis实现。
- 保留生成Mapper/XML、引用启用/存在校验、确定顺序的行锁、节点新ID及边映射、父版本/修订号/并发唯一键冲突语义；不新增生产SQL、Schema、索引或第二套数据访问实现。新增/复制失败由原businessTransactionManager回滚。

### PageHelper finally的核实

用户询问是否自动清理。项目依赖PageHelper 6.1.1；对本机实际jar执行javap检查：`Page.close()`直接调用`PageHelper.clearPage()`；`PageHelper.afterAll()`也调用clearPage。因此本文件已有 `try (Page<...> page = PageHelper.offsetPage(...))` 的7处额外finally均移除，保留资源关闭，无论SQL前/查询中/转换时异常都会执行Page.close。没有删除其他仅startPage而缺少资源关闭的代码；这项是重复代码清理，不宣称有可测的HTTP性能收益。证据：`/tmp/mes-pagehelper-bytecode.txt`。

### 真实数据库验证方法

新增 `mes-admin/src/test/java/dev/mesbase/server/RoutingVersionMySqlTest.java`，沿用既有验收配置解析器 `AcceptanceBusinessDatabase.source()`，先核对中心配置中SIMPLE_B启用绑定、192.168.18.25:3306/mes_base_dev_business_simple_b，未替换数据源或另建库。复用原MySQL验收装配、生成Mapper、引用规则和真实Spring事务；测试专用StatementHandler拦截器仅计数/故障注入，不进入生产。

路线122只读，测试前读取其图、修订号作为对照；随机TEST-RV编码先查无冲突，涉及的新增父子记录原范围为空。复制到该独立测试编码、发布该副本后新建版本；整个测试外层事务回滚，既有引用只做原有同值UPDATE锁。事务结束核对TEST编码零记录及122图/修订号不变。失败场景在新版本边插入处注入异常，确认真实插入故障发生且整个测试副本回滚。不会在122系列制造新的草稿或发布事实；MySQL自增序列可能正常产生间隙。

首次实跑通过：P=2产品、N=5节点、E=4边、O=5去重工序；新路径实际28条业务SQL，对照原路径源码预算33条，省5条（约15%）。这个33是同输入原路径的静态预算，并非本轮旧版本实跑计数。校验源图/新图、节点ID重分配、产品关系及仅一次cache eviction调用。真实DB+Service测试不是完整HTTP入口；缓存使用原测试mock，未测Redis扫描时间。首次service计时1144ms，测试DriverManager数据源与应用暖连接池不同，不能和已有425ms HTTP日志直接比较或宣称端到端提速百分比。

最终命令与结果在下方追加。未启动/停止/重启用户服务；运行中的API仍需用户重启才能载入新代码，再按相同图规模复测HTTP。回滚只需撤销本轮Service方法增量及7处finally清理，保留入场4行空行改动、前端已完成修改和并行日志工作。

### 后端实施最终回执

- `mvn -B -ntp verify`：BUILD SUCCESS，452项中436通过、16项显式环境门控跳过；0失败/错误，日志 `/tmp/mes-routing-version-verify-20261015.log`。默认verify跳过真实MySQL测试，不能将其算作真实数据库验收。
- `mvn -B -ntp -pl mes-admin -am -Dtest=RoutingVersionMySqlTest -Dsurefire.failIfNoSpecifiedTests=false -Dmes.test.routing.version.mysql=true test`：最终显式真实MySQL测试1/1通过、0跳过，日志 `/tmp/mes-routing-version-mysql-final-20261015.log`。除前述SQL预算、复制、单次缓存登记与失败回滚外，补验已有草稿保存后旧边/节点/产品正确删除、保留节点ID不变。最终SQL仍为28条；测试副本零残留。该测试缓存为mock，不宣称真实Redis清理或HTTP时延验证。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10/10通过，独立随机端口、无用户开发连接，日志 `/tmp/mes-routing-version-smoke-20261015.log`；没有操作用户现有服务。
- `git diff --check`通过。无新增生产SQL或数据库迁移；无提交/推送。前端本轮无新增改动；入场其他日志修改未覆盖。
