# 查询表单与公共扫码组件

当前生效方案：标准表单 submit，见文末“后续决定”。全局回车监听已删除；以下保留初始实现的历史过程。

日期：2026-10-21。下述前端相对路径以 `/Users/user/work/lixin/mes-base-ui` 为根。UI 的 `docs/` 被该仓库既有规则忽略，正式验证记录保存在相邻后端的受跟踪文档目录。初始阶段修改前端公共交互和三个扫码入口；后续阶段适配查询表单及生成模板/测试。未修改业务状态或数据库，未操作用户已有服务。

## 初始实现（已废弃）：全局查询的实际处理

`src/main.ts` 注册 `queryEnter` 插件，`src/lib/queryEnter.ts` 在 document 捕获阶段各注册一个 keydown / keyup 监听，应用卸载时移除。采用事件委托，后续挂载的页面和 Teleport 弹窗也生效；不遍历挂载按钮、不为每个页面加事件、不拼查询参数、不调用 API。

处理顺序：

1. 只处理普通输入框中的无修饰 Enter，排除中文输入法组合事件和 keyCode 229、多行输入、只读/禁用输入、下拉选择器、日期选择器，以及显式关闭区域。
2. 以输入框最近的 form 为范围，只接受现有查询区协议 `.filter-panel` / `.el-form--inline` 或显式 `data-query-form`；编辑表单即使含单独查询按钮也不自动接管。
3. 优先寻找 `data-query-submit` 按钮；没有标记时，兼容既有文案“搜索…”、“查询…”、“定位执行”、“筛选”，仅有一个可见候选才处理。隐藏按钮不参与；多个候选不猜测。
4. 取消该 Enter 的浏览器默认提交和原按键传播，调用当前按钮 `.click()`。分页重置、表单校验和业务查询继续由该按钮既有处理器执行。
5. 配对拦截 keyup，避免旧页面的 `@keyup.enter` 再触发一次。长按重复事件、禁用和 loading 按钮不点击；保存、删除、登录等按钮不作为候选。

开发入口：标准列表继续使用既有 `<el-form class="filter-panel">`，无需额外添加回车事件。非标准只读查询可添加 `data-query-form`。多查询按钮必须以 `data-query-submit` 指定目标；特殊区域可设 `data-query-enter="off"`。保留既有页面回车绑定，公共捕获只在已处理事件中阻止其重复，不批量改写历史页面。

静态检查已遍历 Vue 模板：现有静态“搜索/查询/筛选/定位执行”列表入口均在 form 内，并沿用 filter-panel / inline 协议；无公共样式的执行身份查询已在本次修改中显式标记。表单外“查询原请求结果”是恢复命令入口，未纳入列表快捷键。

## 公共扫码组件

`src/components/scanner/CodeScanner.vue` 仅管理获取码值的交互：人工输入/粘贴/键盘式扫码枪输入，选择图片或拖拽图片识别。提供 `modelValue`、`buttonLabel`、`placeholder`、`disabled`、`contextKey`、`link`；输出 `update:modelValue` 和 `confirm`。不访问业务 Store、权限或 API，不生成生产身份、不解释码值、不打开二维码内 URL。

```vue
<CodeScanner
  v-model="code"
  :disabled="busy || !allowed"
  :context-key="auth.factoryId + ':' + auth.revision"
  @confirm="query"
/>
```

图片识别成功仅写入窗口草稿并显示成功提示。用户确认后才回填外部 model 并发出 confirm；接入页面据此执行既有只读查询。真实投产、领料、报工仍须各页原有授权、校验和提交。本轮不增加摄像头能力。

图片在浏览器本地经 `createImageBitmap`、Canvas 和动态导入的 [jsQR](https://github.com/cozmo/jsQR/blob/master/README.md) 识别，不上传；固定依赖 `jsqr@1.4.0`，不依赖特定浏览器的 BarcodeDetector。只接受 PNG/JPEG/WebP，最大 10 MB、1600 万像素，解码边长最多 2048，码值最多 2048 字符；图像位图在成功或失败时 close。识别失败明确提示，清空旧草稿，不以旧码冒充新结果。关闭、切厂、撤权、上下文变化及卸载失效异步序列，迟到结果不能重开窗口或回填。

本轮复用入口：

- `src/views/production/productionInput/index.vue`：扫码投产查询库存与工单。
- `src/views/production/productionReporting/index.vue`：生产报工定位执行。
- `src/components/business/production/ExecutionIdentityLookup.vue`：扫码查询和身份详情内重新录入；关闭立即清理，原有查看及选用流程保留。

## 验证与证据边界

- `tests/queryEnter.test.ts`：事件委托、当前表单隔离、动态挂载、原 keyup 和原 submit 单次触发、IME 确认后不落入旧 keyup.enter、禁用/loading/隐藏、IME/下拉/多行、重复按键、标记/退出机制、编辑表单排除及清理。
- `tests/CodeScanner.test.ts`：人工录入、拖拽和文件选择、成功须确认、错误不复用旧码、多个图片拒绝、关闭/切厂/撤权后的迟到识别拒绝。
- `tests/qrImage.test.ts`：真实 QR 像素由 jsQR 解码、长 ID 保留、空白图片失败、文件约束、异常时释放位图。Fixture `tests/fixtures/qr-execution.{json,png}` 为 ZXing 3.5.1 生成的合成测试码 `EXE:9007199254740993`，不是真实执行身份，不曾向业务接口提交。
- 现有 `localhost:5173`，用户当前选定 TEST_DEV_PERF_20261019：工艺列表填写独有测试前缀后只按 Enter，真实接口返回“暂无工艺路线 / 共 0 条”，随后重置。未切厂。
- 真实浏览器通过文件选择识别合成 PNG，输入框显示完整码值及成功提示，最后取消，没有点击“使用此码值”，没有真实投产/报工/ERP 命令。拖拽事件经组件测试验证，未冒充原生桌面拖拽验收。
- 1280 / 1440 / 1920 三档浏览器布局检查，无弹窗超出视口；首轮检查后补充显式识别成功提示并复验。
- 截图：`.local/query-enter-scanner-20261021/enter-query.png`、`.local/query-enter-scanner-20261021/scanner-recognized.png`。

最终检查（Node 22.23.1）：

- `npm run test`：71 个文件、423 项测试通过（`/tmp/mes-enter-scan-tests-final.log`）。
- `npm run build`：TypeScript 与 Vite 构建通过（`/tmp/mes-enter-scan-build-final.log`）；保留既有 chunk 大小提示，二维码库为动态独立 chunk。
- 新增公共模块、组件和三个测试文件执行定向 `npx eslint`，无错误或警告（`/tmp/mes-enter-scan-lint-final.log`）。
- 前端 `git diff --check` 通过。生产代码和被修改入口均已检查，没有需移除的 MES-CODEGEN 归属标记。
- 生成模板 `mes-generator/src/main/resources/codegen/base/index.vue.vm` 沿用 CrudPage，其公共查询表单已经满足协议，无需修改模板或重新生成页面。

现有工作区其他未提交改动全部保留。回退仅移除 main 插件注册、还原上述三个入口本轮接入行，删除新增组件/测试/Fixture，并移除 jsqr 依赖；禁止整体 reset 或覆盖已有修改。

## 2026-10-21 后续决定：改为标准表单 submit（当前生效）

用户否定了全局捕获按键及按钮文案匹配。上文“全局查询的实际处理”为已废弃实现及历史验证，不再是开发契约；扫码能力保留。

### 当前实现及变更范围

- 删除 `mes-base-ui/src/lib/queryEnter.ts`、`tests/queryEnter.test.ts`，移除 `src/main.ts` 中插件导入与注册。
- 新增 `src/components/QueryForm.vue`：不监听键盘或搜索 DOM，仅包装现有 ElForm 的 `@submit.prevent` 并 emit submit；透传原有 model / inline / size / class 等属性与插槽，保留 queryForm ref 的 resetFields 能力，DOM 中不增加布局容器。
- 32 处实际查询表单统一使用 QueryForm；查询按钮设置 native-type=submit，移除 click 查询与输入框 keyup.enter。重置按钮显式为 button，继续各页原处理。工单查询原 click 的“先回第一页再 load”已移到 submit，不因改造丢失。
- 复用入口包括 CrudPage、首页工单进度、独立生产/质量查询、执行身份查询、人员选择及旧平台管理查询。标准 CRUD 和后续生成页统一继承 CrudPage → QueryForm，不逐字段生成事件。
- CodeScanner 及 jsqr 依赖、图片拖拽/选择/人工录入、确认后才交给业务页面的行为保留；仅清理已无用途的 data-query-* 属性。
- 本轮被修改页面均已检查生成归属标记；无目标文件含需移除的 MES-CODEGEN 标记。通用模板的必要生成标记保留。

```vue
<QueryForm class="filter-panel" inline @submit="search">
  <!-- 条件字段 -->
  <el-button native-type="submit" type="primary">查询</el-button>
  <el-button native-type="button" @click="reset">重置</el-button>
</QueryForm>
```

### 生成器与防回退

`mes-generator/src/main/resources/codegen/base/index.vue.vm` 更新原生查询 submit 的共享契约说明，生成产物仍复用已改造的 CrudPage，不复制另一套查询表单或引入额外开关。`BusinessTemplatePackageTest` 实际渲染启用/不启用同步两种模板，验证字段契约与公共组件委托，拒绝重新生成 queryEnter、逐字段按键监听或独立表单。

没有为此重新生成、覆盖已有业务代码，也没有读取或变更真实库 metadata。上述属于 TEST ONLY 生成器渲染测试，不能冒充新业务建表/数据库验收。

前端新增 `tests/QueryForm.test.ts` 验证原生按钮提交、单次 submit、默认刷新被阻止、resetFields、model 和属性透传、禁用/多行/选择器不会被组件合成查询；`tests/querySubmitContract.test.ts` 遍历真实 Vue 模板，检查每个现有静态查询按钮的原生 submit 与无重复 click/按键路径，并确认全局模块已移除。旧全局拦截测试删除，原 CrudPage 权限、上下文、删除确认与扫码测试继续保留并通过。

### 实际验证

- Node 22.23.1：`npm run test`，72 个文件 / 419 项测试通过；相比此前删除了 8 项全局拦截测试，新增了 4 项公共表单/源码契约测试。
- `npm run build`：TypeScript / Vite 构建通过；既有 chunk 大小提示保留。
- 新增 QueryForm 与测试文件定向 ESLint 无错误或警告；前端 diff --check 通过。
- Java 测试经 scripts/format-java 格式化；`mvn -B -ntp -pl mes-generator -am -Dtest=BusinessTemplatePackageTest -Dsurefire.failIfNoSpecifiedTests=false test`：7 项通过。
- `mvn -B -ntp verify`：全 reactor 通过。
- 实际浏览器：当前 5173、当前 TEST_DEV_PERF_20261019，工序公共 CRUD 页输入合成查询前缀后按 Enter 返回“暂无记录 / 共0条”，重置恢复；旧管理页参数设置同样回车得到空结果，resetFields 正常清空。未切厂，未提交新增/修改/删除或 ERP 命令。
- 公共表单检查 1280 / 1440 / 1920 三档布局；由于透传到原 ElForm，没有增加布局层或更改视觉样式。
- 截图以 UI 仓库为根：`.local/query-submit-20261021/native-submit.png`、`.local/query-submit-20261021/legacy-native-submit.png`。这些证明现有真实浏览器和 API 的原生提交；生成器由模板渲染测试和共享组件测试覆盖，不宣称所有生成结果逐页进行过浏览器验收。

日志：`/tmp/mes-submit-tests.log`、`/tmp/mes-submit-build-final.log`、`/tmp/mes-submit-lint.log`、`/tmp/mes-submit-codegen-tests.log`、`/tmp/mes-submit-verify.log`；`/usr/bin/python3 scripts/smoke-packaged.py` 最终 10 项通过、0 失败（`/tmp/mes-submit-smoke.log`），为自有隔离进程检查，未操作用户服务。适配期间构建检查发现批量编辑产生的闭合标签错误，已修正；原 CrudPage 测试的 Element Plus mock 未提供运行时 ElForm，公共组件调整为沿用项目全局组件并仅 type import FormInstance，原隔离/权限测试保留，不删除失败断言。

本轮原文件备份：UI `.local/query-submit-20261021/before/`。回滚仅按本轮差异恢复这些查询表单及模板，不能整体 reset；此前全局键盘拦截已明确废弃，不作为回滚默认方案。无数据库、权限、数据来源或业务命令变化；未操作用户已有服务。

## 2026-10-23 执行定位统一函数与用户扫码行为（当前实施）

### 用户决定、原链复核与结构结论

用户明确报工、不良申报等扫码均以“工单＋产出品号＋批次”定位同一个生产执行器，要求所有Execution扫码走一个函数；type只是使用场景。原mes_production_scan_identity是每个绑定一条，缺当前操作者、场景、逐次结果/错误、耗时，不足以分析重复使用、失败率或行为变化。更严重的是原解析/投产、requiredBatch、质量列表和质量入库证明、执行引用保护均读取它，将扫码绑定作为业务准入；不是普通行为日志。此次重新追踪正式页面和后端调用链后整体退役关联，没有仅隐藏入口。

正式新结构分两项：Execution.batch_no保存生产批次；mes_production_scan_activity逐次保存用户定位尝试。主键关联的生产事实仍引用execution_id；行为表不作为Locator索引、库存凭据或删改引用。旧表保留历史数据，旧Entity/Example/Mapper/XML移除，已发布迁移保留。新表只有主键和普通查询索引，无factory_id（按既有Factory分库），不开放事件新增/编辑/删除CRUD。

### 统一契约与正式调用链

- 前端`src/api/production/productionScan.ts`唯一`scanExecution(type, code, inputMethod='TEXT'): Promise<ProductionIdentity>`，复用request的Factory/身份/迟到响应机制，POST `/api/production/scan`，不trim码值。
- Controller只解析type/code/inputMethod、校验并经BeanConversions返回统一白名单；拒绝额外字段，不能伪造用户、时间、结果。Service按场景检查当前用户权限，再统一调用ProductionIdentityService.resolve，type不切换定位算法或业务处理器。报工使用原report:submit，不强制附加execution:query；不良使用原defect:create，执行查询使用原execution:query。
- CodeScanner确认仍第一参数返回字符串，第二参数TEXT/IMAGE；图片识别后人工修改归TEXT。它只负责采集；报工、身份查询、独立不良申报三个定位入口都走同一函数。报工得到identity后才按EXE:id读context，正式报工和不良提交仍为原明确命令；刷新context/节点/类型以及已知ID详情不会伪记扫码。
- 身份结果包含executionId、工单、产品、工艺版本、状态、计划数及batchNo；移除旧identityType。MES2只无损编码工单码、产出品号、批次，无绑定主键或摘要。EXE:id保留详情读取。材料投产仍用“材料品号#批次”获取可信库存来源；新建/复用Execution在原父工单锁内按正式三元组执行，不混成执行查询。
- 新查询先使用受控Example普通索引过滤，再以原文精确比较，并按id分页读尽collation近似项；不存在/重复精确命中分别拒绝，不选第一条。不同工单同品号同批次各自定位；同厂并发投产共用工单锁避免重复创建。普通Execution编辑保留原batch且领域规则冻结身份。

### 行为分析可用范围与可信边界

字段包括事件UUID、scan_type、input_method、user_id、execution_id、outcome、error_code、duration_ms、occurred_at、request_id。用户和结果来自服务端，输入方式来自客户端声明，不能据此证明真实硬件扫码。服务端耗时不包含浏览器图片识别、用户等待或网络耗时。不保存原始码、图片、异常内容和请求/响应全文，避免把任意扫码内容写入审计。

该模型足以按人/场景/时间统计已提交定位次数、失败率及服务端耗时，也可按目标Execution追查定位活动。它不提供打开→取消→识别失败→确认的完整漏斗、不证明业务报工/入仓成功、不承诺每个浏览器动作均已送达，不能把扫码次数当产量。审计为分析用best-effort：定位读事务结束后再由独立业务事务落事件；日志故障记录`production.scan.activity.failed`及异常链，不改变定位成功或掩盖授权/来源/数据库原错误。恢复时按requestId/activityId定位数据库或授权故障并修复，后续扫码继续记录；不伪造丢失事件或重放领料/报工/入仓来补日志。不预建通用补偿、埋点SDK或分析大屏。

切厂/撤权/切换中/卸载及Lookup关闭立即失效请求；不良替换执行前确认丢弃脏字段，等待确认期间再次核对请求序号、Factory及权限，未决申报不允许扫码改目标。扫码输入仍不会直接提交领料、报工、质量或库存。

### 数据演进、生成与回退

V52在核实的mes_base_dev_perf_test经正式Flyway执行并验证；同日独立维护只把10764个固定迁入执行的可信批次落正式字段。当前11874批次均非空，原执行/旧绑定12984条记录的旧列逐条相同。无状态、数量、库存或历史处理改写；不制造旧扫码事件。具体SQL身份、备份和核验见数据维护归档与质量专题同日实施修正。

使用mes-crud-codegen技能从真实V52 metadata生成Execution与扫码行为的完整基础后端审查副本，配置`docs/development/production-scan-codegen.yaml`、报告`.local/execution-scan-20261023/codegen-report.json`，两表supported、零rejected。只合入需要的持久化及Execution响应字段，保留原FOR UPDATE的flushCache/useCache保护和手写业务Service；事件CRUD基线留在target审查副本，不装配可达写口。旧投产专用配置移除scan_identity，全量配置排除旧绑定和独立事件，避免重生成开放写接口。未运行菜单SQL或改角色授权。

应用回退保留新增字段/事件和原证据，不能删表/改迁移记录或回滚原生产事实。旧包仍依赖旧绑定，不能正确处理新模型创建而未写旧绑定的执行，因此回退包不等于恢复新业务；优先暂停新增并修复新包，禁止为了回退补造旧绑定。旧MES1新定位/投产拒绝；原已提交UUID的冻结JSON指纹和SHA256算法不变，先读取原结果，不重新扣库存。独立批次维护SQL已执行，不得重跑；若新发现数据冲突，先隔离具体记录再按受控维护处理，不恢复运行期读旧payload兜底。

### 本次验证（与旧章节分列）

- 后端全reactor `mvn -B -ntp verify`：900项，851执行通过、49条件跳过、0失败/错误，已打包；日志`/tmp/mes-execution-scan-verify.log`。
- 定向两轮分别61项、13项通过。后一轮含4个HTTP/H2真实事务代理测试、3个精确定位纯规则测试、5个Service/Controller合同测试及1个真实MySQL只读映射测试；不是把Mock当真实服务。覆盖旧表移走后可定位、权限/Factory/无身份/无Scope、原UUID、跨工单同批次、审计故障不替换原结果、质量收尾原证明仍有效。
- 真实MySQL：核实Factory/DS/LOCAL和server_uuid；V52迁移、53份严格校验、10764批次维护已实际执行；当前映射SQL以生产样本读取三元组定位、Q证明、原审核恢复及进度通过。EXPLAIN使用idx_execution_order_product_batch、估算1行；这是索引使用证据，不是主键/普通索引吞吐性能等价证明。
- 前端Node22全量78文件/481项通过，build通过；定向边界4文件/48项通过。日志`/private/tmp/mes-base-ui-scan-boundaries-all-tests.log`、`/private/tmp/mes-base-ui-scan-boundaries-build.log`。两仓差异检查通过，任务30个Java文件格式检查通过。
- 浏览器：当前5173/TEST_DEV_PERF_20261019实看不良登记新增扫码定位按钮、公共图片/文本弹窗及取消；报工深链页面显示MES2说明并使用同一控件。截图`.local/execution-scan-20261023/ui-common-scan.png`、`ui-reporting-scan.png`。未点击确认发送新scan请求，旧后端尚未由用户重启；新POST真实服务落库、真实图片识别定位、重启后迁入不良批次回显及三档宽度仍待验，不能沿用前次入库真实闭环当本轮证据。
- 当前真实行为表0条是未调用新服务的真实结果，没有伪造日志。HTTP/H2的成功/失败事件持久化不等于真实服务已验收。未启停用户服务、提交、推送或部署。

## 2026-10-23 用户追加：物理删除旧扫码表（执行前准备）

用户追加要求物理删除mes_production_scan_identity和废弃代码，并将SQL并入V52。运行期旧Entity/Example/Mapper/XML已在上一阶段删除；本次进一步清理H2底座，不再创建/补列旧表，HTTP测试改为断言旧表不存在，再验证同一正式扫码/报工上下文链。定向49项通过，Java格式化及差异检查通过。

重新核实TEST_DEV_PERF_20261019/DS9 revision2/LOCAL及目标mes_base_dev_perf_test和server_uuid。已新备份11874执行、1110旧绑定、0条行为事件和53条迁移历史，旧绑定与Execution.batch_no承接冲突0、触发器依赖0。私有证据`.local/scan-identity-retirement-20261023/`；`V52.applied.sql`保存已执行原文、`V52.proposed.sql`为用户要求的合并删表审查副本、`drop-table.review.sql`为删表语句。

截至本节：旧表仍存在，未执行DROP、未修改正式V52或Flyway历史。由于V52已执行，直接改原文将造成checksum冲突；已向用户列出新增V53与明确例外修订V52历史两种处理方式，等待版本处理选择。没有将未执行草稿放入正式Flyway目录，也没有启停用户服务。旧HTTP身份已返回401，本轮未验证运行中JVM是否已加载新实现，不以此冒充新代码运行验收。

## 2026-10-23 V53 删除完成（替代上一节待选择状态）

用户确认“保留已执行V52，新增V53删表，其他库顺序升级到V53”。已新增正式`V53__drop_retired_production_scan_identity.sql`并在重新核实的mes_base_dev_perf_test经项目Flyway执行；旧表已物理删除，非仅逻辑退役。V52原文和旧历史不变，无repair、无手工历史修改。旧4个持久化文件已在前阶段删除；本次清理测试底座旧建表/补列，当前H2从不建立该表，正式HTTP测试在确认表不存在时仍定位、读报工上下文并记录独立行为。全量生成排除项保留，避免对尚未升级的其他库误生成旧业务代码；已发布V29/V31/V52必须保留历史升级顺序，不当作残留业务恢复。

重新备份旧1110条绑定和DDL、11874条执行、行为表及53条迁移历史；批次承接冲突、外键/触发器/视图依赖0。info仅V53 Pending，migrate成功，validate通过54份迁移。写后旧表存在数0、缺正式批次0，执行11874条全部字段及原53条迁移历史逐条一致，只新增V53成功历史。私有证据`.local/scan-identity-retirement-20261023/`，公开审计摘要`docs/operations/data-maintenance/2026-10-23-scan-identity-retirement-receipt.json`。删除前记录通过备份保留，不把一条绑定伪造为过去扫码行为。

其他库使用正式版本链升级到53，执行前按升级说明重新核实来源、批次承接和备份，旧应用节点不能继续访问已删表。没有自动遍历其他库，没有启停用户服务。当前真实服务身份过期，本轮不宣称已验证运行中JVM或真实扫码POST；数据库真实迁移和映射只读验证与HTTP/H2分列。

本轮最终验证：`MES_INBOUND_SQL_MYSQL_ACCEPTANCE=true mvn -B -ntp verify`全reactor通过，总900项、实际852项通过（含1项真实perf库只读Mapper验收）、48项条件跳过、0失败/错误；日志`/tmp/mes-scan-retirement-verify.log`。此前清理定向49项通过；2个改动Java文件格式检查、两仓差异检查通过。打包JAR内V52/V53 SQL与工作区逐字节一致。未改前端业务代码，不重复跑无关UI测试；未启停用户服务或宣称真实新扫码HTTP验收。

## 2026-10-23 编码纠正：工单号:品号#批次号（当前有效）

用户明确指出MES2前缀分支不符合实际标签，正式执行扫码必须是`工单号:品号#批次号`，例如`5100-260701568:20213023001#260727103`。本节替代前文MES2/Base64方案；旧章节保留当时实现记录，不再作为当前编码合同。

ProductionScanCode.parse改为按第一个ASCII冒号分工单，再按其后第一个ASCII井号分品号，余下全部文本保留为批次。encode直接拼接同格式，删除Base64编解码和MES2前缀判断；校验非空、字段上限、控制字符及非法Unicode，不trim、不归一大小写/前导零/空格。批次可含井号、冒号；工单号不能包含分隔冒号，品号不能包含分隔井号，编码时明确拒绝无法表示的字段，未引入转义或另一套协议。原料来源查询继续采用原“材料品号#批次”契约，不混同执行定位。

ProductionIdentityService仅对无井号的EXE:ID走现有详情主键读取，其余统一按三元组解析，避免工单号恰好为EXE时误分流；公共扫码各场景仍返回同一个ProductionIdentity，生产二维码输出、投产结果和页面说明同步更新。EXE详情/内部刷新保留；新MES1/MES2执行定位拒绝。原投产命令字段、冻结JSON、UUID优先重放及SHA256请求摘要算法没有改变，旧原UUID仍不重新领料或扣库。业务定位、权限、Factory、审计、数量和库存证明不变。

补充验证覆盖实际字面码、中文/前导零/大小写/尾空格、批次内分隔符、空字段/畸形文本/旧前缀拒绝，以及EXE作为业务工单号和EXE:ID两条路径。前端公共扫码透传测试改用真实文本格式，不继续以MES2样例掩盖契约错误。仅修改解析/显示和相关测试，无数据库Schema或数据更改，V52/V53不变，无需新增SQL迁移。

## 2026-10-23 继续收敛：单一生产码、独立规则与Execution主键

用户进一步明确只认可工单号:品号#批次号。本节取代上节仍保留EXE详情文本和材料#批次的当前约定；历史实现记录保留，不再作为现行合同。ProductionCodeRules独立承担解析、校验和格式化，ProductionScanCode仅为值对象。所有生产扫码共用此规则，ID详情及报工刷新显式使用executionId，不再将主键拼成另一种扫码文本。已有请求优先按原UUID/冻结指纹核实，不能因协议清理重新扣库；历史无批次时不伪造EXE码。

审查执行编号：ProductionExecutionRules曾写PENDING，创建Service取得id后写EXE-id；普通索引不提供独立业务编号语义，正式关联均使用execution_id。删除活跃executionNo字段、生成赋值、筛选、响应和重复展示，不将旧字段换成另一份id别名。复杂查询保留原executionId；执行器本身使用id。冻结的旧单据JSON不修改，旧迁移保留原文。

V54只删冗余列和索引；核实perf绑定后备份11874执行和54条原Flyway历史，数据库依赖0；正式迁移54及55份校验成功，其他列与原历史逐条不变。从真实V54 metadata通过mes-crud-codegen生成审查副本，只采用执行器Entity/Example/Mapper/XML，保留FOR UPDATE与flushCache/useCache保护。未改库存、状态或数量，未启停服务。新包加载前旧服务不具备新契约，不能把Schema校验当真实服务验收。

### 本次最终验证与运行边界

- 最新后端 `MES_INBOUND_SQL_MYSQL_ACCEPTANCE=true mvn -B -ntp verify` 全reactor成功：902项，854执行通过、48条件跳过、0失败/错误；日志`/tmp/mes-execution-id-final-verify.log`。包含真实perf库只读Mapper查询、规范码/ID读取一致、V54旧列不存在及质量入仓关联读取，不写业务数据。
- 定向首轮发现查询测试使用未声明参数，改到正式calculate入口验证工单不匹配；完整首轮发现执行器创建响应断言误用executionId，改为id并断言executionNo不存在。修正后最新完整检查全通过；此前失败日志保留，不把失败轮次写为通过。
- HTTP/H2覆盖规范码、旧码拒绝、工单名EXE合法三元组、精确批次/歧义、ID详情无批次返回null、id筛选、跨厂/权限、投产来源/并发/事务、原UUID旧格式重放和旧冻结executionNo JSON原样保持。历史指纹SHA256的输入/算法不变。首次投产创建及batch持久化仍在同一工单父锁事务，没有事务外中间态。
- 前端当前全量79文件/491测试、build通过：`/private/tmp/mes-base-ui-canonical-all-tests.log`、`/private/tmp/mes-base-ui-canonical-build.log`；保留既有大chunk提示。新增选择器ID筛选测试，二维码fixture真实编码规范文本并通过解码。前端不另建解析器。
- 72个本轮Java文件格式检查、两仓git diff --check通过。打包JAR中V52/V53/V54与工作区逐字节一致，ProductionCodeRules存在，退役绑定类和ProductionScanCode$MaterialLot不存在。
- 真实MySQL只执行核实后的V54结构升级、备份和只读核验；没有真实ERP、库存、报工、质量或状态写入。48项条件测试未执行，不代表真实业务验收。浏览器报工页当前显示“暂时无法恢复登录状态 / 服务暂时不可用”；未验证新服务HTTP或真实页面交互，需用户加载最新包后重启再验。没有自行启停服务、提交、推送或部署。
