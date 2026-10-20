# 工识 Gongnexa 商业官网 V1 — 建设与验收报告

日期：2026-10-20（Asia/Shanghai）。范围：独立官网的设计、编码、构建、运行和本地真实浏览器验收。

**结论：本地官网 V1 GO；正式公开发布 NO-GO。** 四个核心页面已完成并实际运行。公开咨询邮箱已按用户提供的信息接入。正式域名、运营主体与正式托管隐私说明尚未确定，当前产物刻意保持 noindex / robots 禁爬。未部署、提交、推送、修改 DNS 或写入数据库。

## 1. 现状审查与工作区保护

- 后端：`/Users/user/work/lixin/mes-base`，分支 main，Java/Maven 多模块。前端：`/Users/user/work/lixin/mes-base-ui`，Vue 3.5、TypeScript、Vite、Element Plus、Pinia、Vue Router。
- 已阅读个人工程入口、工程原则、协作流程、前端规范、知识治理及所涉及仓库规则。检索现有 docs、doc、knowledge；未找到现有 Gongnexa 官网或可用公开联系渠道。检查了源代码、生产/质量/仓储验收材料与忽略目录中的浏览器截图。
- 同级已有本次前序指令创建的空目录 `mes-base-website`，本轮复用它，避免另建 `mes-website`。该目录独立于两个 MES 仓库，没有初始化 Git，也未创建第二套 MES 业务前端。
- 开始时后端已有 `scripts/legacy/verify_sample_http.py` 修改，以及迁移验收报告和 TEST ONLY Launcher 未跟踪文件；前端有工作台、样式、Home、测试修改及新进度/工作台文件。结束时原有状态清单保持；本轮没有写入这些文件。没有以 reset/checkout/批量格式化覆盖并行工作。
- 本轮只读检查 MES；使用已有截图，不登录或启动 MES，不执行任何数据库读写。网站使用自己独立的 4321 端口。

## 2. 技术选型与实现边界

- Astro 5.18.2（锁文件固定实际安装版本）、TypeScript 5.9、Tailwind CSS 4 / Vite 插件，Node 22.23.1（`.nvmrc`）。静态 HTML 构建，输出 dist，兼容 Cloudflare Pages 静态托管。
- Astro 配置与静态页面方案参考 [Astro 官方配置说明](https://docs.astro.build/en/guides/configuring-astro/) 和 [项目结构说明](https://docs.astro.build/en/basics/project-structure/)。没有为本地任务注册或发布托管站点。
- 无全站 SPA 路由、ClientRouter、全屏 Loading、导航固定等待或动画阻塞。所有正式页面都是原生 `<a>` 文档导航。
- 菜单、需求清单是局部 TypeScript 交互，未引入 Vue：本轮没有需要 Vue 状态管理的复杂交互，原生实现更轻。静态内容不依赖客户端初始化；禁用 JS 时主要导航、正文、FAQ、公开邮箱仍在 HTML 中。清单提交按钮默认禁用，仅在增强脚本装配完成后启用，防止脚本失败时表单原生GET携带个人输入。该失败关闭状态由构建产物测试验证，未做浏览器禁用JS专项。
- 桌面/移动 Header、Footer、Layout 各只有一个实现；主导航和页脚导航来自同一个 `navigation` 数据源。
- `.env.example` 管理站点 URL 和可选公开邮箱。`PUBLIC_SITE_URL` 只接受 HTTPS origin，未配置时 noindex、不生成虚构 canonical；本轮已通过 TEST ONLY `.invalid` 域名验证配置分支，并不宣称该域名真实启用。

## 3. 目录结构与文件职责

```text
mes-base-website/
├── astro.config.mjs          # 静态输出、环境 URL 校验、禁止脚本内联
├── package.json / package-lock.json / .nvmrc
├── .env.example / tsconfig.json / .prettierrc.json
├── src/
│   ├── data/site.ts          # 唯一导航、FAQ、能力状态、公开邮箱
│   ├── layouts/Layout.astro # 公共文档、SEO、Header/Footer
│   ├── components/           # Brand / Header / Footer / Icon / CTA / ProductShot / Inquiry
│   ├── pages/                # index / features / demo / contact / 404 / robots.txt / sitemap.xml
│   └── styles/global.css    # 品牌 tokens、组件布局、四档响应式
├── public/
│   ├── favicon.svg / _headers
│   └── images/               # 原创品牌 SVG、OG PNG、真实产品 WebP（1440/960）
├── scripts/
│   ├── prepare-images.mjs    # 可追溯截图裁剪及压缩
│   ├── site.test.mjs         # 页面契约、链接、锚点、来源边界、脚本预算
│   └── verify-config.mjs     # URL/邮箱合法与缺省状态、canonical/OG/sitemap 分支
├── evidence/                # 真实截图、JSON、Lighthouse HTML/JSON、构建测试日志
├── docs/reviews/gongnexa-website-v1-review.md
└── README.md
```

扩展页沿用 `src/pages/solutions/`、`architecture/`、`about/` 约定，未制造空页面或无效公开菜单。

## 4. 完成页面与实际运行

官网目录：`/Users/user/work/lixin/mes-base-website`。本轮真实启动 `npm run preview`，地址 **http://127.0.0.1:4321/**；预览保留供用户查看，未操作原 MES 服务。

| 页面         | 已完成内容                                                                                           |
| ------------ | ---------------------------------------------------------------------------------------------------- |
| `/`          | 深色 Hero＋真实工作台、痛点、7项能力、目标生产链路、LOCAL/ERP边界、AI规划、产品实拍、6项FAQ、最终CTA |
| `/features/` | 7项业务能力，逐项说明问题、能力、使用、结果与适用边界；集成与部署评估说明                            |
| `/demo/`     | 当前工作台与近期报工截图；历史 LOCAL 完工截图单独标注日期/版本；无生产入口、账号或虚假立即体验       |
| `/contact/`  | 用户明确提供的邮箱 `lixinzhang0703@gmail.com`；沟通准备说明、本地需求清单、下载、邮件草稿、隐私说明  |
| `/404.html`  | 自定义错误页与返回入口；未知 URL 实际 HTTP 404                                                       |

本地构建独立，不依赖业务 API、Redis、MES 业务库或后台运行状态。复现：`npm ci` → `npm run build` → `npm test` → `npm run preview`。

## 5. 品牌与视觉

- 名称：工识 Gongnexa；产品：工识 MES；口号：让制造可见，让生产可知。长期定位为智能制造软件平台。
- 主色 tokens：深海蓝 `#0B172B`、品牌蓝 `#2563EB`、科技蓝 `#38BDF8`、浅底 `#F4F7FB`。统一容器、字号、间距、圆角与响应式断点。
- 原创线性箭头/生产路径 SVG 临时标识，不引用第三方商标，不宣称注册或独占。
- 桌面 Hero 左侧品牌承诺、右侧真实产品界面；下方依次使用问题列表、非均匀卡片、流程节点、双模式图、规划探索、截图展示和原生 FAQ，避免全站重复卡片结构。
- 系统字体，无远端字体和第三方追踪。无轮播、自动视频或无意义动画。提供 skip link、键盘焦点、减少动态效果偏好支持。
- 根据首轮 Lighthouse 修正了浅色辅助文字对比度、过小截图标签和品牌链接无障碍名称。修复后的首页移动/桌面及联系页无障碍评分均为100（自动检测不能替代完整人工无障碍审计）。

## 6. 真实产品素材与来源

没有生成或拼造软件界面，没有为截图创建或修改生产数据。所有业务像素来自已有浏览器截图；仅裁剪、缩放和 WebP 压缩。TEST 标识保留，不将样例数量当作运营实绩。

| 官网素材              | 原始路径（相对 mes-base）                                  | 处理与证据边界                                                                       |
| --------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| workspace.webp / -960 | `.local/workspace-20261020/home-1440.png`                  | 裁剪 `(0,95,1440,920)`，去掉账号/环境顶栏；当前测试工厂工作台，专项验收待完成        |
| reports.webp / -960   | `.local/workspace-20261020/reports-after-restart-1440.png` | 同上；近期 TEST ONLY 报工和调整记录；不是客户生产数据                                |
| closure.webp / -960   | `.local/full-chain-v23-20261017/ui/c01-completed100.png`   | 裁剪 `(760,0,1160,742)` 聚焦完工面板；明确标注历史 V2.3 / 2026-10-17，不冒充当前版本 |

完整来源、裁剪参数、原图与衍生图 SHA256、字节数见 `evidence/assets.json`。图片目视检查无凭证、服务器地址、客户信息、调试面板或异常弹窗。截图中的样例工厂/产品/记录编号不是公开体验地址。当前工作台和历史验收界面分开展示日期与版本，不拼接成同一当前业务演示。

## 7. 功能真实性核对矩阵

本轮重新核对源码与材料，没有重跑 MES 业务测试。下表的“已验证”仅引用明确的历史验收范围。

| 领域                     | 现有依据                                                         | 官网表述                                                                   |
| ------------------------ | ---------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 生产工单、工艺路线、执行 | UI实际入口及V1.6/V2.3闭环报告                                    | 历史 LOCAL 单材料场景已验证，计划/分配不等于完成                           |
| 现场报工                 | productionReporting 页面、报工API、V1.6/V2.3报告                 | 关联执行身份与实际路径，报工不等于放行/完工/入库                           |
| 物料、交接、入库         | V2.2仓储报告、V2.3及V2.3.1收尾报告                               | LOCAL 单材料场景验证；不宣传完整WMS或ERP库存交易                           |
| 质量                     | 质量V2 Phase1/2实施报告，含最终追加复验说明                      | 旧LOCAL处置场景有证据；V2有代码/隔离测试，真实业务待验收                   |
| 当前生产工作台           | BusinessWorkspace / WorkOrderProgress 等当前源码和2026-10-20截图 | 已实现展示与查询，当前工作台待专项验收；无OEE、实时设备采集、预测宣称      |
| 多工厂                   | 来源隔离报告及V2.3.1双业务库验收                                 | 分厂业务库/权限隔离；不外推无限规模或全场景验收                            |
| ERP                      | E10 V2.1同步实施报告，含最终追加                                 | 查询/转换/同步准备已实现；真实部署契约待验；不承诺领退料、出入库、自动补偿 |
| 工业AI Agent             | 用户给定产品演进方向                                             | 明确“规划中”，没有伪造对话或预测演示                                       |

主要依据（均位于 mes-base 的 `docs/reviews/`）：

- `2026-10-17-local-production-end-to-end-v16-report.md`：K / M，历史 LOCAL 单材料真实 MySQL/API/UI 闭环。
- `2026-10-17-local-stock-documents-v22-report.md`：真实 LOCAL 仓储验收及非ERP边界。
- `2026-10-17-local-full-chain-v23-acceptance.md`：历史11组真实场景与剩余范围；不单独把其阶段性结论当成全范围GO。
- `2026-10-18-base-mes-v231-case12-closure.md`：双业务库补验及真实/Fixture边界。
- `2026-10-18-quality-v2-phase12-implementation.md`：K～N及最终追加，真实质量V2写验收仍BLOCKED。
- `2026-10-17-digiwin-e10-sync-v21-report.md`：最终追加，真实E10仍UNVERIFIED。

没有客户案例、客户Logo、效率提升百分比、用户数或未经承诺的SLA。

## 8. 导航、Header 与交互验收

### 自动化检查通过

`npm test` **14/14 PASS**：5页语义结构与公共组件、导航及全部内部资源/路由/锚点目标；公开邮箱与无伪造接收；验收/规划状态标签；无域名时的爬虫保护；客户端JS预算；禁止内联JS以匹配CSP。

### 真实浏览器通过

- 通过 CUA 在真实浏览器完成：首页 → 产品能力 → 产品演示 → 联系咨询 → 首页；联系页后退至演示、前进至联系。当前菜单高亮正确。
- 390px 下逐一点击主导航，导航后菜单关闭；打开、按钮关闭、Escape、点击外部均验证；页面不存在人为全屏 Loading。不是客户端路由模拟。
- 产品能力质量锚点：顶部约92px，Header约73px；联系CTA锚点约92px，未遮挡。
- FAQ展开/折叠；真实素材图片加载；公开邮箱mailto目标与草稿编码；空表单5个必填/同意项拒绝；有效输入在本机生成；下载实际得到UTF-8文本；编辑后旧草稿失效。
- 联系清单只在本机处理，不产生网络业务请求、预约或服务端线索。真实邮件发送/收件尚未验证；没有发送测试邮件。
- 未知页面 HTTP404，显示公共Header/Footer，返回首页成功。正常页面控制台 warn/error 记录为空；404请求本身为预期失败响应。

证据：`evidence/interactions.json`、`browser-console.json`、`contact-draft-1440.jpg`、`mobile-menu-390.jpg`、`404-390.jpg`。所有链接目标由自动化逐一检查；浏览器实际点击覆盖主导航及代表性CTA、锚点，不把静态目标检查说成逐一人工点击。

## 9. 视觉与响应式证据

真实浏览器四页面×四宽度，**16/16**：`document.documentElement.scrollWidth === innerWidth`，无破损图片。滚动到底部使懒加载图片进入视口，再回顶部保存全页截图，检查标题、CTA、Footer、流程与卡片布局。

| 宽度 | 高度 | 完整截图（evidence/ 下）                                          |
| ---- | ---- | ----------------------------------------------------------------- |
| 1440 | 1000 | home-1440.jpg、features-1440.jpg、demo-1440.jpg、contact-1440.jpg |
| 1024 | 1000 | home-1024.jpg、features-1024.jpg、demo-1024.jpg、contact-1024.jpg |
| 768  | 1024 | home-768.jpg、features-768.jpg、demo-768.jpg、contact-768.jpg     |
| 390  | 844  | home-390.jpg、features-390.jpg、demo-390.jpg、contact-390.jpg     |

DOM尺寸与加载结果：`evidence/responsive.json`。首页 `home-*-hero.jpg` 是全页浏览器截图的顶部裁剪，未修改页面内容；`home-*-top.jpg` 保留原生窗口截图，窗口面板边界可能带空白，最终预览以全页与hero裁剪为准。

桌面由双栏Hero呈现，1024进入较宽堆叠，768/390采用移动菜单与单列主要内容；小屏能力卡为两列、主卡跨列，流程按网格换行，表单单列。截图可点击查看源图，不把产品数据表压缩成可交互移动系统宣称。

## 10. 构建、配置与工程检查

- `npm run build`：**PASS**，Astro check 0 errors / 0 warnings / 0 hints；构建4核心页＋404及robots/sitemap。
- `npm test`：**14 PASS / 0 FAIL**。初轮测试的title断言未处理格式化换行，修正断言后通过；未通过放宽业务真实性检查维持表面成功。
- `npm run format:check`：最终检查见 `evidence/format-final.log`。
- `node scripts/verify-config.mjs`：已通过4页canonical、4项sitemap、OG、robots Allow、空邮箱未配置状态、非法站点URL和邮箱拒绝。测试域 `https://release-check.invalid` 仅在独立临时输出中，不进入本地官网实际dist。
- `public/_headers` 配置CSP、nosniff、frame限制和静态资源缓存；脚本强制输出外部文件以符合 `script-src 'self'`。该托管响应头尚未在Cloudflare真实部署验收，本地Astro preview不模拟这些响应头。
- 依赖安装初轮遇npm缓存沙箱权限，按工具要求在沙箱外重试成功；未修改全局Node、npm缓存所有权或MES配置。首次构建Astro工具设置目录权限问题也由授权重试解决。
- 未运行后端Maven、原MES前端测试或业务API：本轮没有修改这些代码，官网验收不能替代MES回归。

## 11. 性能与测量条件

本机静态构建产物，Astro preview / localhost，Lighthouse **13.5.0**，HeadlessChrome **155**，修复后两次移动与一次桌面最终测量，不是线上分位数或生产用户数据。原始HTML/JSON及完整Network列表保存，网络请求缓存状态为none，实际压缩与响应差异见metrics.json。

| 最终首页测量 | Performance | Accessibility | Best Practices | SEO |       LCP | CLS | TBT |
| ------------ | ----------: | ------------: | -------------: | --: | --------: | --: | --: |
| 移动         |         100 |           100 |            100 |  66 | 1357.58ms |   0 | 0ms |
| 桌面         |         100 |           100 |            100 |  66 |  364.06ms |   0 | 0ms |

移动：Lighthouse默认移动设备配置，模拟节流（具体视口、DPR、RTT、带宽及CPU倍率见metrics.json）；桌面：preset desktop，模拟40ms RTT、10,240Kbps、CPU倍率1。联系页补充 Accessibility / Best Practices 均100。

SEO 66的明确原因是本地站点未配置正式域名，刻意noindex；不是声称SEO完全验收。正式URL分支已独立构建验证，线上爬取、站长平台和分享卡片尚未验证。

**INP未测量。** Lighthouse TBT不等于INP。站点缺少部署后的真实交互样本，不能宣称INP≤200ms已通过。已测本地LCP/CLS达到目标，发布后仍需在真实网络与设备复验。

优化结果：全站两个脚本约2.5KB未压缩，菜单约0.89KB、清单约1.63KB；公共CSS约31.6KB未压缩/约8.1KB gzip；无远端字体/第三方脚本。首屏产品图按需960/1440 WebP，约44/88KB；次级图懒加载、显式宽高避免跳动。没有首屏视频或全站水合。

修复后移动重复测量（`lighthouse-mobile-repeat.report.json`）性能100、LCP1353.88ms、CLS0、TBT0；两次均达到本地LCP/CLS目标。

首轮移动性能100、无障碍96；依据实际报告修正辅助文字和品牌链接后无障碍100。最终复测另捕获一次CLS=0.122684（性能96）：根因为移动导航默认展开样式在增强脚本加载后才折叠。已改为CSS初始固定73px Header与隐藏菜单，noscript单独提供静态导航回退，消除脚本时序引起的高度切换。问题报告保留为`lighthouse-mobile-cls-before.report.*`，修复后重新测量，不删除失败证据。首轮报告保留为`lighthouse-mobile.report.*`，最终为`lighthouse-mobile-final.report.*`。这些成绩仅针对所列页面和条件。

## 12. 未完成项、风险与发布前顺序

1. **正式发布阻塞：** 提供已确认的正式站点HTTPS origin，配置PUBLIC_SITE_URL后重建；当前发布会被robots与noindex阻止索引。没有购买域名或配置DNS。
2. **正式发布阻塞：** 确定对外运营主体与实际托管的访问日志/隐私说明，替换目前的本地静态处理说明中待确认部分。
3. 正式环境需验证HTTPS、实际安全/缓存响应头、404规则、站点资源、canonical/sitemap及分享卡片。未获部署授权，本轮不执行。
4. 邮箱由用户明确提供，mailto链接与编码已验证；实际邮件客户端打开、发送/收件未做。当前没有服务端预约接收，不提供成功反馈。若将来增加接收端，须补校验、限流、防垃圾、重复提交和真实收件确认。
5. 当前工作台、质量V2和真实ERP的业务验收边界保留。下一轮可在独立授权的演示环境完成业务验收和统一版本截图，再升级文案；不要为官网改业务事实或让源码/历史测试冒充当前交付。
6. INP、真实弱网/终端、部署后Core Web Vitals、屏幕阅读器全面人工审计未验证。Lighthouse单次lab数据不构成线上性能承诺。

## 13. 最终结论与变更回执

**本地官网V1 GO**：独立构建/运行，四页完整，共用Header/Footer、原生导航，16个响应式视图、移动菜单、FAQ、咨询清单、真实404与必要测试通过。已有真实素材且日期/版本/样例边界明确，公开邮箱真实来源明确，没有伪造产品能力或预约成功。

**正式公开发布 NO-GO**：正式域名与运营隐私信息未配置，且未部署。发布前按第12节完成具体配置和部署验收，不把本地运行结论扩大到正式发布。

### Change Summary

新增独立官网的页面、公共布局、品牌样式、素材处理、SEO配置、本地咨询交互和验收脚本。没有修改MES业务代码、权限、数据源、数据库或原有服务；没有外发邮件、提交、推送或部署。回滚仅处理本轮独立官网目录与本报告，不触碰原仓库用户改动或业务历史。

### Document Created

本报告新增1～13节，覆盖现状、选型、目录、页面、品牌、素材、真实性、导航、视觉、构建、性能、风险及结论。原始证据保存在官网evidence目录；本报告同时交付于mes-base的`docs/reviews/gongnexa-website-v1-review.md`及独立官网相同相对路径，内容一致。未修改MES业务代码。
