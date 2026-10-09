# 2026-10-09 架构评审复核（独立复跑证据）

> 性质：**只读第三方复核**。目的：独立验证《2026-10-09 架构评审》与《评审行动方案》§五 / §5.6 的整改声明是否与仓库事实一致。
> 本文**不新增建议清单**——唯一台账仍是 `2026-10-09-architecture-review-actions.md` §五（第 237 行起）与 §5.6（第 313 行起，行号已核对）。本文只记录「声明 → 实测证据 → 结论」。
> 复核期间两仓均被并行进程提交，时点快照见 §1。未轮换凭据、未改源码/配置/数据库、未触碰用户索引与暂存内容。

## 1. 时点与仓库快照

| 时刻 | 后端 mes-base | 前端 mes-base-ui |
| --- | --- | --- |
| 复核开始 | HEAD `261b0af`，20 提交，88 项改动**全部暂存未提交**，领先远端 2 | HEAD `ebfb270`，领先远端 2，14 项改动未提交 |
| 复核结束 | HEAD `8fe1e69`「refactor: finalize Base persistence foundation and review fixes」，21 提交，**领先远端 3（未推送）**，工作区干净，0 tag | HEAD `b07041e`「refactor: align Base UI and add verification workflow」，**12 = 12 已推送**，工作区干净，0 tag |

远端 tip 实测（`git ls-remote origin`）：后端 `43e6532f14ec3e96fcf14c9de023af7328e33c03`，与 §5.6 声明一致；前端 `b07041ef2e24215f38bc1bd373595b336aef3f97`。

## 2. 逐项验证

| # | 声明 | 独立证据（实测） | 结论 |
| --- | --- | --- | --- |
| 1 | 私有配置取消跟踪 + 加入 .gitignore | `git ls-files` 无两个 profile（exit 1）；`git check-ignore -v` 命中 `.gitignore:8/9`；`git ls-tree -r HEAD`、`origin/main`、全部 `for-each-ref` 引用树均 0 命中；本地文件保留且权限 `0600` | **确认** |
| 2 | 历史凭据已清除（5 个值） | 从本地两个 profile 提取 **5 个非空凭据值**（password / datasource-key），对 `git cat-file --batch-all-objects` 全量 **2,592 对象 / 1,298 blob（含不可达）** 扫描：**0 命中**。另：`git log --all -- <profile>` = 0，reflog 空、无 stash | **确认** |
| 3 | 无其它明文凭据残留 | 全库 yml/yaml/properties/resources/config 路径下 30 处非空 `password/datasource-key/secret-key/access-key` 行**全部为 `${...}` 占位或变量引用**（含历史 `verify-codegen-http` 的 `password=os.environ[...]`、旧 `smoke-packaged.py` 的运行时随机口令） | **确认（无真实字面量）** |
| 4 | 示例配置账号改为占位符 | `HEAD:config/application-{dev,prod}.example.yml`：`username: your-db-user`，`password: ""`，`datasource-key: ""` | **确认** |
| 5 | 防复发：守卫 + hooks + 安装脚本 | `scripts/check-private-config.py`、`java-git-hook.py`、`format-java`、`install-git-hooks`、`.githooks/pre-commit|pre-push` 均在提交内；`core.hooksPath=.githooks`。**实跑守卫三种模式均 exit 0**：无参（索引）、`--staged`、`--revision HEAD`。pre-commit 对部分暂存 Java 明确拒绝自动重暂存 | **确认** |
| 6 | 无用依赖移除 | `mes-admin/pom.xml` 仅剩 `flyway-core` + `flyway-mysql`；全仓 pom `postgresql` 0 命中。**独立复跑 `scripts/smoke-packaged.py`：passed 6 / failed 0**，断言含「排除 postgresql / 保留 mysql + flyway-mysql」 | **确认** |
| 7 | Java FQN 导入改为显式导入 | FQN 注解 `@dev.mesbase.platform.web.EndpointAccess` 由 38 处降至 **1 处**，且仅存在于 `mes-generator/.../RuoyiTemplateRenderer.java:79` 的**模板字符串**（属已延后的生成器） | **确认（存量见 N5）** |
| 8 | 失效 UI 删除 | 提交 `b07041e` 删除 `src/views/PlatformPage.vue`、`src/lib/platform.ts`；全仓无残留引用；`CrudPage.vue`、`lib/crud.ts` 保留；`crud.test.ts` 移除依赖已删 `/api/platform/users` 的用例；`druidMonitor.test.ts` 由 `EXTERNAL/sqlserver` 对齐为 `BUSINESS/mysql` | **确认** |
| 9 | 同步回归单测 | `SyncCoordinatorTest.java` 含 **8 个 `@Test`**，无 `@Disabled/@Tag/Assumptions` 门控，默认运行 | **确认** |
| 10 | 后端 190 项 / 186 通过 / 4 跳过 | **独立复跑 `mvn -B -ntp verify`：BUILD SUCCESS**，7 模块全 SUCCESS。surefire 汇总：framework 15 + generator 3 + admin 172 = **190**，skipped 4 | **确认** |
| 11 | Java 格式检查通过 | 抽样 `./scripts/format-java --check`（IntelliJ 2023.3.2）：3 文件 OK，exit 0 | **确认（抽样 3/57）** |
| 12 | 文档入口与历史标记 | actions §五=第 237 行、§5.6=第 313 行与引用一致；根 README 第 9/15/49 行更新；`architecture/README.md` 顶部新增 2026-10-09 当前入口、ADR-005 标历史；`2026-10-06-server-package-review.md` 加历史快照头；主评审新增 §六 | **确认（漂移见 N1）** |
| 13 | 两仓 `git diff --check` 通过 | 后端 worktree/cached 均 exit 0；前端 exit 0 | **确认** |

## 3. 本轮新发现

- **N1（P1，需你裁定）三处文档/收据与现状矛盾。** 整改已在本轮复核期间提交（后端 `8fe1e69`、前端 `b07041e`，后者已推送），且历史已按 §5.6 清理；但：
  - 根 `README.md:49` 仍写「历史提交仍含旧凭据，本轮未轮换凭据或重写历史」；
  - 主评审 §六仍写「凭据仍存在于历史 / 本地 origin/main 快照」（`2026-10-09-architecture-review.md` 尾部）；
  - `target/validation/architecture-review-remediation.json` 的 `not_performed` 仍列 `history rewrite`、`commit or push`。
  三处均已被 §5.6 与实测推翻（HEAD/origin 引用树无私有 profile、全对象库 0 命中、两仓已提交）。建议按当前事实回写，避免后来者按 README 误判「历史仍有凭据」而重复处置。
- **N2（P2）`.workbuddy/memory/2026-10-09.md` 已随 `8fe1e69` 进入版本库**，而 `.gitignore` 未忽略 `.workbuddy/`。这是 agent 内部工作记忆，进入产品仓库会形成噪音（且后端该提交尚未推送，仍可调整）。建议决定 `.workbuddy/` 是否纳入忽略。
- **N3（P2）空目录 36 个仍在**（非 `target/` 约 30 个），含根骨架 `src/main/java/dev/mesbase/**`（14 个）、`mes-framework/.../integration/erp`、`mes-admin/src/test/.../persistencefixture/erp`、`docs/development/templates/erp`、以及本次改动新产生的 `mes-framework/src/main/java/dev/mesbase/framework/persistence`。你已在 U11 声明为低优先级、不列准入门槛——此处仅记录现状与新增项。
- **N4（P2）凭据守卫的覆盖边界（应登记为已知缺口）。** `check-private-config.py` 仅检查路径以 `config/` 开头或含 `/src/main/resources/`、且扩展名 `.yml/.yaml/.properties` 的文件，键名仅识别 `password|datasource-key|secret-key|access-key`。**未覆盖**：`src/test/resources/**`、`.env`、`.json`、`.xml`、脚本文件（`.sh/.py`）、以及 `token|secret|apiKey|username` 等键名。与文档「不保证覆盖所有 Secret 命名」一致，但建议把上述清单显式写进 README，避免被当成全能闸门。
- **N5（P2）生成器仍产出全限定注解。** `RuoyiTemplateRenderer.java:79` 直接拼接 `@dev.mesbase.platform.web.EndpointAccess(...)`，生成代码会带 FQN 注解。生成器已按决定延后，此处只登记——业务接入对齐 UI / `rows/total` 时应一并收敛。
- **N6（P3）§5.1 存量未清。** 除生成器外仍有 **36 个生产文件 / 95 处 FQN 类型引用**（如 `CaptchaService`、`SysLoginService`、各 `*Form.java`）。本轮声明范围就是 10 个文件，故属已知范围外存量，非回退。
- **N7（P3）格式门禁不可在 CI 复现。** `.idea/` 被 gitignore，`format-java` 依赖的 `.idea/codeStyles/Project.xml` 不在仓库，因此 CI 只跑 guard + verify + smoke，格式仅靠本地 hooks（文档已承认是本地护栏）。同时 `AGENTS.md` 记录了 `format-java`/hooks，但**未记录 `check-private-config.py`**（README 有提）。

## 4. 未能独立验证的边界

- **前端聚合 `npm test` 未复现通过。** Node 22.23.1 下默认 `forks` 池：`Test Files 1 passed (1) / Tests 14 passed (14) / Errors 16 errors`，120s 中 **import 占 91%**；单独重跑（排除并发争抢）与 `--pool=threads --maxWorkers=4` 均同一结果。而**单文件** `--pool=threads --maxWorkers=1 tests/permissions.test.ts` 通过（3/3，53s 中 environment 占 95%）。判定为**本机沙箱吞吐/隔离限制**（`eslint .` 亦耗时 3 分 8 秒），非工程缺陷；但「17 文件 / 81 项通过」这一聚合数字**本轮没能独立复现**。另：静态计数（`it|test` 调用）约 65 处，与 81 口径不一致，未判定哪个正确。
- **前端 `npm run build` 未复跑**（避免触发本机 dist 护栏），仅确认 `lint` 独立通过（exit 0）。
- 未执行真实 MySQL / Redis / 多厂数据库 / 浏览器 / 部署 / 远端 CI 验收；未轮换任何凭据、未迁移 `mf_datasource` 密文；未验证 GitHub 不可达缓存与他人克隆中的副本（§5.6 已自认无法声明全平台擦除）。
- 未对凭据守卫做对抗性负例（避免污染你的索引与暂存），仅以只读方式复核其正则与路径过滤逻辑。
- 未验证 `origin/main` 之外的前端引用；`git ls-remote` 曾出现 HTTP2 / 502 抖动，最终以成功返回为准。

## 5. 复跑命令（本次实际执行）

```sh
# 后端
JAVA_HOME=.../corretto-21.0.4 /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp verify
/usr/bin/python3 scripts/smoke-packaged.py
/usr/bin/python3 scripts/check-private-config.py [--staged|--revision HEAD]
./scripts/format-java --check <3 files>
# 全对象库凭据扫描（含不可达）：git cat-file --batch-all-objects --batch → 逐 blob 匹配本地 profile 的 5 个凭据值
# 前端
npm run lint
npx vitest run [--pool=threads --maxWorkers=1 tests/permissions.test.ts]
```

结论：§2 的 13 项声明**全部与仓库事实一致**（第 10 项全量复现，第 11 项抽样）。P0 凭据处置在提交后依然成立。遗留问题集中在 **N1 文档/收据漂移**与 **§4 前端聚合测试未复现**，两项均需你裁定后再动。
