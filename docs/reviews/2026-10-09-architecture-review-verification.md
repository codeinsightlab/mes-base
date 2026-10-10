# 2026-10-09 架构评审复核（独立复跑证据）

> 性质：**只读第三方复核**。目的：独立验证《2026-10-09 架构评审》与《评审行动方案》§五 / §5.6 的整改声明是否与仓库事实一致。
> 本文**不新增建议清单**——唯一台账仍是 `2026-10-09-architecture-review-actions.md` §五（第 237 行起）与 §5.6（第 313 行起，行号已核对）。本文只记录「声明 → 实测证据 → 结论」。
> 复核期间两仓被并行进程反复提交/推送，时点快照见 §1。未轮换凭据、未改源码/配置/数据库、未触碰用户索引与暂存内容。
> **修订说明**：本文件曾被并行进程在 `941e051` 中提交并在 `9c8a278` 推送，但那次快照发生在两次编辑之间，导致推送版结论引用了表里并不存在的「第 14 项」、且 §4 与结论自相矛盾。本次修订补齐 §2 第 14–16 项、§3 的 N2 关闭状态与 N8，并把 §4 改写为与结论一致。

## 1. 时点与仓库快照

| 时刻 | 后端 mes-base | 前端 mes-base-ui |
| --- | --- | --- |
| 复核开始 | HEAD `261b0af`，20 提交，88 项改动**全部暂存未提交**，领先远端 2 | HEAD `ebfb270`，领先远端 2，14 项改动未提交 |
| 复核中段 | HEAD `8fe1e69`「refactor: finalize Base persistence foundation and review fixes」，21 提交，领先远端 3 | HEAD `b07041e`「refactor: align Base UI and add verification workflow」，**已推送** |
| 复核结束时（并行进程又推了一轮） | 远端 `origin/main` = `9c8a278`（新增 `2f38a89` 格式工具修复、`9c8a278` 本报告与记忆文件）；本地 `3d9fc12`，**领先远端 3**，工作区干净 | `b07041e`，本地=远端 |

远端 tip 实测（`git ls-remote origin`）：后端由 `43e6532`（§5.6 记录值）推进到 `9c8a278`——**整改代码、CI/hooks 脚本、本复核报告与 agent 记忆文件都已进入 `origin/main`**。逐提交核对 `43e6532..origin/main` 的 5 个提交树：**没有一个包含 `application-{dev,prod}.yml`**。

## 2. 逐项验证

| # | 声明 | 独立证据（实测） | 结论 |
| --- | --- | --- | --- |
| 1 | 私有配置取消跟踪 + 加入 .gitignore | `git ls-files` 无两个 profile（exit 1）；`git check-ignore -v` 命中 `.gitignore:8/9`；`git ls-tree -r HEAD`、`origin/main`、全部 `for-each-ref` 引用树均 0 命中；本地文件保留且权限 `0600` | **确认** |
| 2 | 历史凭据已清除（5 个值） | 从本地两个 profile 提取 **5 个非空凭据值**（password / datasource-key），对 `git cat-file --batch-all-objects` 全量扫描：推送前 **2,592 对象 / 1,298 blob**、推送后 **2,652 对象 / 1,315 blob**（均含不可达）——两次都是 **0 命中**。另：`git log --all -- <profile>` = 0，reflog 空、无 stash | **确认** |
| 3 | 无其它明文凭据残留 | 该检查**与具体值无关**：枚举对象库中**所有**含非空 `password/datasource-key/secret-key/access-key` 字面量的 blob（15 个），逐条辨明**全部是 `${...}` 占位或变量引用**——含历史 `verify-codegen-http` 的 `password=os.environ[...]`（缺变量即 `SystemExit`）、旧 `smoke-packaged.py` 的运行时随机口令。故**不存在任何**历史明文凭据，与值是否轮换无关 | **确认（无真实字面量）** |
| 4 | 示例配置账号改为占位符 | `HEAD:config/application-{dev,prod}.example.yml`：`username: your-db-user`，`password: ""`，`datasource-key: ""` | **确认** |
| 5 | 防复发：守卫 + hooks + 安装脚本 | `scripts/check-private-config.py`、`java-git-hook.py`、`format-java`、`install-git-hooks`、`.githooks/pre-commit|pre-push` 均在提交内；`core.hooksPath=.githooks`。**实跑守卫三种模式均 exit 0**：无参（索引）、`--staged`、`--revision HEAD`。pre-commit 对部分暂存 Java 明确拒绝自动重暂存 | **确认** |
| 6 | 无用依赖移除 | `mes-admin/pom.xml` 仅剩 `flyway-core` + `flyway-mysql`；全仓 pom `postgresql` 0 命中。**独立复跑 `scripts/smoke-packaged.py`：passed 6 / failed 0**，断言含「排除 postgresql / 保留 mysql + flyway-mysql」 | **确认** |
| 7 | Java FQN 导入改为显式导入 | FQN 注解 `@dev.mesbase.platform.web.EndpointAccess` 由 38 处降至 **1 处**，且仅存在于 `mes-generator/.../RuoyiTemplateRenderer.java:79` 的**模板字符串**（属已延后的生成器） | **确认（存量见 N6）** |
| 8 | 失效 UI 删除 | 提交 `b07041e` 删除 `src/views/PlatformPage.vue`、`src/lib/platform.ts`；全仓无残留引用；`CrudPage.vue`、`lib/crud.ts` 保留；`crud.test.ts` 移除依赖已删 `/api/platform/users` 的用例；`druidMonitor.test.ts` 由 `EXTERNAL/sqlserver` 对齐为 `BUSINESS/mysql` | **确认** |
| 9 | 同步回归单测 | `SyncCoordinatorTest.java` 含 **8 个 `@Test`**，无 `@Disabled/@Tag/Assumptions` 门控，默认运行 | **确认** |
| 10 | 后端 190 项 / 186 通过 / 4 跳过 | **独立复跑 `mvn -B -ntp verify`：BUILD SUCCESS**，7 模块全 SUCCESS。surefire 汇总：framework 15 + generator 3 + admin 172 = **190**，skipped 4 | **确认（全量）** |
| 11 | Java 格式检查通过 | **全量** `./scripts/format-java --check`：整改提交内 **49** 个 Java 文件通过（`passed (49 files)`）；`2f38a89` 修掉 `-dry` 可能挂起的问题后，用**修复后的实现**对 `43e6532..HEAD` 的 **69** 个 Java 文件复跑，仍 `passed (69 files)`，exit 0 | **确认（全量 ×2）** |
| 12 | 文档入口与历史标记 | actions §五=第 237 行、§5.6=第 313 行与引用一致；根 README 第 9/15/49 行更新；`architecture/README.md` 顶部新增 2026-10-09 当前入口、ADR-005 标历史；`2026-10-06-server-package-review.md` 加历史快照头；主评审新增 §六 | **确认（漂移见 N1）** |
| 13 | 两仓 `git diff --check` 通过 | 后端 worktree/cached 均 exit 0；前端 exit 0 | **确认** |
| 14 | 前端 17 文件 / 81 项通过 | Node 22.23.1**默认 `forks` 池在本机沙箱下失败**（17 文件仅 1 个跑完，16 个 worker 启动超时，120s 中 import 占 91%）；改 `--pool=threads --maxWorkers=1` 顺序跑完：**`Test Files 17 passed (17)` / `Tests 81 passed (81)`**，耗时 1,546s | **确认（须换池）** |
| 15 | 「`ManagedRuntimeMySqlTest` 含双 JVM 抢占等测试」 | `mes-admin/src/test/java/dev/mesbase/ManagedRuntimeMySqlTest.java`（143 行）：第 108 行用 `ProcessBuilder` 起 **2 个真实子 JVM**（`java -cp <classpath> …`），第 115 行断言两子进程日志中恰有 **1 条 `PROBE_ACQUIRED` + 1 条 `PROBE_BUSY`**；另含同进程同流竞争、异厂互不阻塞（L94）、旧执行者被剥夺（L103 `assertOwner` → `SYNC_EXECUTION_LOST`）。门控为 `Assumptions.assumeTrue(configPath!=null)`，故默认 4 skipped | **确认（静态）** |
| 16 | 前端 `npm run build` 通过 | 本机**未能跑完**：`vue-tsc --noEmit` 已通过（流程已走到 vite 阶段），卡在 `vite:prepare-out-dir` 清空 `dist/assets`（69 项 > 阈值 50）被**本机 safe-delete 护栏**拦截（`SAFE_DELETE_BULK_CONFIRM_REQUIRED`）——非工程报错。`dist/` 已 gitignore 且未跟踪，仓库仍干净 | **部分验证** |
| 17 | （附加）本地领先远端的 3 个提交未回归核心隔离契约 | 剥掉注释与空白后逐字节比对 `2c77be0` 的 6 个核心类（`FactoryContext`/`OwnedTransactionManager`/`ExecutionScope`/`ControlledExecutor`/`DatasourceIdentity`/`BusinessCache`）：**全部完全相同 → 仅格式化与 Javadoc 折行，无语义改动**；该提交其余内容是把 `.workbuddy/memory/*` 移出版本库。`941e051` 只动 `.gitignore` 与文档，`3d9fc12` 只动文档 1 行 | **确认（无回归）** |

## 3. 本轮新发现

- **N1（P1，需你裁定）三处文档/收据与现状矛盾。** 整改已提交并推送，历史已按 §5.6 清理；但：
  - 根 `README.md:49` 仍写「历史提交仍含旧凭据，本轮未轮换凭据或重写历史」；
  - 主评审 §六仍写「凭据仍存在于历史 / 本地 origin/main 快照」（`2026-10-09-architecture-review.md` 尾部）；
  - `target/validation/architecture-review-remediation.json` 的 `not_performed` 仍列 `history rewrite`、`commit or push`。
  三处均已被 §5.6 与实测推翻（各引用树无私有 profile、全对象库 0 命中、两仓已提交并推送）。**现在这三处都已随 `origin/main` 公开**，后来者按 README 会误判「历史仍有凭据」而重复处置。**（复核末尾再次确认：`README.md:49` 与主评审 §六原文均未变；`architecture-review-remediation.json` 的 `not_performed` 仍含 `history rewrite`、`commit or push`。）**
- **N2（P2）→ 已关闭。** `.workbuddy/memory/*` 曾随 `8fe1e69` 进库；并行进程已在 `941e051` 把 `.workbuddy` 加入 `.gitignore`、并在 `2c77be0` 将其移出版本库。实测：`.gitignore:11-12` 含 `.workbuddy / .workbuddy/memory/MEMORY.md`，`git ls-files` 无 `.workbuddy/*`。**残留**：`9c8a278` 已把这两个记忆文件推送出去，历史里仍有副本（无害，仅噪音）。
- **N3（P2）空目录 36 个仍在**（非 `target/` 约 30 个），含根骨架 `src/main/java/dev/mesbase/**`（14 个）、`mes-framework/.../integration/erp`、`mes-admin/src/test/.../persistencefixture/erp`、`docs/development/templates/erp`、以及本次改动新产生的 `mes-framework/src/main/java/dev/mesbase/framework/persistence`。你已在 U11 声明为低优先级、不列准入门槛——此处仅记录现状与新增项。
- **N4（P2）凭据守卫的覆盖缺口已由实测证实**（此前只是读代码推断）。在**临时 git 仓库**里放入真凭据后对照跑（未触碰 mes-base 的索引与暂存）：
  - **按设计拒绝 ✔**：`config/*.yml` 的字面量 `password`、字面量 `datasource-key`、`${X:非空fallback}`、私有 profile 路径本身、`password :`（键后带空格）；占位 `${...}` 与空值按设计放行 ✔。
  - **实测漏检 ✘（6 类，真凭据可通过闸门）**：① `src/test/resources/**`（路径过滤只匹配 `/src/main/resources/`）；② `.env`；③ `config/*.json`（②③因扩展名只认 `.yml/.yaml/.properties`）；④ YAML 列表项内的 `- password: <字面量>`（正则要求行首即键名）；⑤ 键名 `secret`；⑥ 键名 `apiKey`。
  - 结论：闸门对「主 resources 下的 yml 字面量」有效，对其它路径/文件类型/键名无效——建议把这 6 类显式写进 README，避免被当作全能闸门。
- **N5（P2）生成器仍产出全限定注解。** `RuoyiTemplateRenderer.java:79` 直接拼接 `@dev.mesbase.platform.web.EndpointAccess(...)`，生成代码会带 FQN 注解。生成器已按决定延后——业务接入对齐 UI / `rows/total` 时应一并收敛。
- **N6（P3）§5.1 存量未清。** 除生成器外仍有 **36 个生产文件 / 95 处 FQN 类型引用**（如 `CaptchaService`、`SysLoginService`、各 `*Form.java`）。本轮声明范围就是 10 个文件，故属已知范围外存量，非回退。
- **N7（P3）格式门禁不可在 CI 复现。** `.idea/` 被 gitignore，`format-java` 依赖的 `.idea/codeStyles/Project.xml` 不在仓库，因此 CI 只跑 guard + verify + smoke，格式仅靠本地 hooks（文档已承认是本地护栏）。同时 `AGENTS.md` 记录了 `format-java`/hooks，但**未记录 `check-private-config.py`**（README 有提）。
- **N8（P3）清理收据的 `unreachable_objects_pruned: true` 已过期。** 实测 `git fsck --unreachable` 仍有不可达对象（22 tree + 3 blob 量级）。3 个 blob 经鉴定均为**中间版本**：`fa2bec7f`=旧版 `.workbuddy/memory/2026-10-09.md`、`6b978aba`=旧版 `SyncCoordinator.java`、`d87b524c`=旧版行动方案（311 行 vs 现 317 行）；**三者均不含非空凭据键**，故无安全影响——它们是在清理之后的新提交/编辑过程中产生的。建议把该字段改成「清理时点成立」或「清理后未再 prune」。
- **N9（P3，提示）`format-java --check` 的实现在复核之后被改过。** `2f38a89`「fix: make native Java format checks terminate reliably」把 `-dry` 改为「格式化临时副本再比对」，理由写的是旧 IDEA 的 `-dry` 关机阶段可能挂住。这恰好说明**该门禁的实现本身还不稳定**；同一提交还改动了 `FactoryBusinessDataSource.java` 与 `FoundationAccessInterceptor.java` 两个生产类。

## 4. 未能独立验证的边界

- **前端聚合 `npm test`：需要换池才跑得动，但已复现通过。** 默认 `forks` 池 17 文件只有 1 个跑完（`Errors 16`，120s 中 import 占 91%），`--pool=threads --maxWorkers=4` 同样失败，单独重跑（排除并发争抢）结果一致；`--pool=threads --maxWorkers=1` 则全量全绿（1,546s，environment 53% + import 47%）。判定为**本机沙箱吞吐/隔离限制**（`eslint .` 亦需 3 分 8 秒），非工程缺陷。**远端 CI 未运行**，默认池在托管 runner 上是否正常仍未知。
- **前端 `npm run build` 未能完整跑完**：`vue-tsc --noEmit` 通过，但 vite 清空 `dist/` 被本机 safe-delete 护栏拦截（见 §2 第 16 项）。即「类型检查通过、产物构建未验证」。
- 未执行真实 MySQL / Redis / 多厂数据库 / 浏览器 / 部署 / 远端 CI 验收；未轮换任何凭据、未迁移 `mf_datasource` 密文；未验证 GitHub 不可达缓存与他人克隆中的副本（§5.6 已自认无法声明全平台擦除）。`ManagedRuntimeMySqlTest` 的双 JVM 用例**只做了静态核对，未真实复跑**（4 skipped 即为此）。
- 凭据守卫的负例已在**独立临时仓库**中完成（结果见 N4），因此**未扰动 mes-base 的索引、暂存与你已有的改动**；未在 mes-base 本体内制造违规提交。
- **本地领先远端的 3 个提交（`941e051`/`2c77be0`/`3d9fc12`）含并行进程对核心隔离类的改动**（`FactoryContext`、`OwnedTransactionManager`、`ExecutionScope`、`ControlledExecutor`、`BusinessCache`、`DatasourceIdentity`）。本文**不评价**这些改动——它们不属于本次整改声明范围，且我未参与其设计意图。
- 未验证 `origin/main` 之外的前端引用；`git ls-remote` 曾出现 HTTP2 / 502 / credential-lock 抖动，最终以成功返回为准。

## 5. 复跑命令（本次实际执行）

```sh
# 后端
JAVA_HOME=.../corretto-21.0.4 /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp verify
/usr/bin/python3 scripts/smoke-packaged.py
/usr/bin/python3 scripts/check-private-config.py [--staged|--revision HEAD]
./scripts/format-java --check <提交内全部 49 个 Java 文件>
# 全对象库凭据扫描（含不可达）：git cat-file --batch-all-objects --batch → 逐 blob 匹配本地 profile 的 5 个凭据值
# 逐提交树核查：for c in $(git rev-list 43e6532..origin/main); do git ls-tree -r --name-only $c | grep application-(dev|prod).yml; done
# 凭据守卫负例：mktemp -d 起临时 git 仓库，放入真凭据后跑 check-private-config.py --staged（不触碰本仓索引）
# 前端
npm run lint
npx vitest run [--pool=threads --maxWorkers=1]
```

结论：§2 的 **17 项声明全部与仓库事实一致**，其中第 10、11、14 项为**全量复现**（后端 190 项、Java 格式门禁、前端 17 文件 81 项），第 16 项为**部分验证**（类型检查过、构建被本机护栏拦截）。P0 凭据处置在整改提交与推送之后依然成立（推送前后两次全对象库扫描均 0 命中）。**N2 已被并行进程关闭**；仍需你裁定的是 **N1 文档/收据漂移**（现已随 `origin/main` 公开）、**N4 守卫 6 类实测漏检**、**N8 收据字段过期**，以及 **N9 格式门禁实现自身的稳定性**。
