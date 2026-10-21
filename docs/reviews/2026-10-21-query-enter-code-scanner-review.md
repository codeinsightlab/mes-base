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
