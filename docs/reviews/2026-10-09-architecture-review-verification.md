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

---

## 6. 2026-10-11 独立业务开发准入审查（追加；不改写前述历史结果）

### 6.1 结论与适用版本

**FAIL：当前版本不具备结束基础建设、正式开始首个业务模块的资格。** 唯一已证实的准入阻断是 F1：真实运行时启用后，零可用工厂会使认证后的工厂恢复入口失败，前端新登录随后被清除。这是可局部修复的基础入口缺陷，不要求全仓重构。F2、F3 为非阻断后续事项；E1–E3 为证据缺口，不冒充已发现缺陷。

- **简单 CRUD：** 生成、编译、基础持久化及权限契约已有可复用证据，可以继续模型设计和隔离生成，但当前不正式放行基础建设收尾。F1 修复并通过真实运行时的无工厂/无角色回归后，可重新评为限定范围的 CONDITIONAL PASS；首次真实业务库联调前须补 E1。
- **复杂业务编排：** 具名事务、固定快照、回滚与挂起恢复已有 H2 证据；尚不能据此宣称真实多厂、双 JVM 崩溃恢复或外部协调已验收。F1 修复后可开展单工厂、单业务库内的 Service 编排开发；依赖跨节点恢复、真实同步执行器或外部副作用的流程，应先补相关 E1/E2 与该用例的幂等/恢复验收。不能通过中心库＋业务库本地事务拼出分布式原子性。
- 本结论针对下列 **S2 文件快照**，不是仅针对 HEAD，也不是生产上线认证。实施回执只用于定位文件和测试，没有作为正确性证据。

| 仓库 | 分支 / HEAD | S2 工作区状态 |
| --- | --- | --- |
| `/Users/user/work/lixin/mes-base` | `main` / `7fb750199d7088ce2145c5f78750260c15f2d363` | 42 项既有修改/未跟踪文件；其中包含本轮待审查实现，未提交 |
| `/Users/user/work/lixin/mes-base-ui` | `main` / `6702f527be7c0e58acf424255eacbe0b196a5799` | clean |

S2 于 **2026-10-11 20:36:42 +08:00** 固定。文件清单及逐文件 SHA-256：`/private/tmp/mes-base-admission-20261011/final-snapshot/{mes-base,mes-base-ui}-snapshot.json`；执行副本在同目录下的两仓。复制前后核对文件未变化；私有开发连接配置未复制、未用于测试。前端使用现有 Node 22.23.1 与只读复用的 node_modules，没有改变全局 Node 默认值。

最初审查期间后端确有并行修改，已按用户要求等待。实现聊天后来因网络错误结束，不能据此认定实施完成；用户再次要求继续后固定 S2。早期 S1 的 verify 曾有 3 个错误，原因是中间版本测试 fixture 未创建新增路由代数表；S2 已包含 fixture 修正，独立重跑通过。**S1 不用于证明当前版本通过，S2 也不抹去 S1 的历史失败。**

### 6.2 发现（按严重性）

#### F1 / P1 / 阻断：零可用工厂导致 AUTHENTICATED 恢复入口返回 400

**场景：** 启用 `mes.runtime.enabled`，请求没有显式工厂且没有有效偏好，中心库中尚无可自动选择的业务工厂，或所有自动候选均已失效。用户身份有效，角色可以为空；即便有平台角色，前端登录仍会执行工厂恢复。

**真实调用链与位置：** UI `auth.login` → `loadNavigation` → `restoreFactory`（`mes-base-ui/src/stores/auth.ts:99,118,146`）→ `/api/foundation/factories`、`/api/foundation/context` → Token/会话认证 → URL 分类为 FACTORY → `selection.resolve` 返回空 → `FoundationAccessInterceptor.preHandle` 无条件调用 `FactoryContext.required()` → `RequestBoundaryFilter` 写出 400 `FACTORY_SCOPE_REQUIRED` → UI `login` catch 清除新登录态（`auth.ts:121`）。

- [FoundationAccessInterceptor.java:88](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/web/FoundationAccessInterceptor.java:88)：只检查非平台、admission 非空，没有保留 AUTHENTICATED 空 Scope。
- [FoundationConfiguration.java:135](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/FoundationConfiguration.java:135)：运行时存在时，生产装配确实提供 `managed::admit`。
- [FoundationController.java:27](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/FoundationController.java:27)：上述两入口和 preference 仅要求 AUTHENTICATED；`FoundationService.context()` 本可返回 `factoryId=null`。
- [auth.ts:146](/Users/user/work/lixin/mes-base-ui/src/stores/auth.ts:146)：恢复流程必须等待这两个接口。

**已验证事实：** 独立 `EmptyFactoryProbe.java` 使用真实拦截器、Controller、Service、请求过滤器，空工厂 catalog 与合成已认证身份；保持其余条件相同，仅切换 admission 是否存在：无 admission 时 context=200/null、factories=200/[]；有 admission 时两者均为 400/FACTORY_SCOPE_REQUIRED，admission 回调甚至未被调用。异常后线程 Scope 均清空。日志在 `probes/empty-factory.log`。

**影响与推理：** 新环境正常认证后不能进入平台 UI 完成工厂配置；所有候选失效也可能阻断恢复。前端清除登录态由当前源码直接推导，本轮没有执行真实账号的浏览器登录。现有 `FoundationHttpTest` 14 项通过，但测试显式关闭 runtime，因而漏掉此分支，不证明 F1 正常。该问题违背“有效空角色账号可登录，零候选保持空 Scope，AUTHENTICATED 可访问”的已确认规则。

**最小整改：** 将仅需身份的工厂发现/上下文/偏好操作与必须取得业务绑定的请求准入分开，保留空 Scope；不能为了修复而给缺工厂的业务 SQL 加默认库回退。增加 runtime 启用时的零候选、空角色、有平台角色、无绑定和从空状态选择有效工厂回归，并补登录恢复联测。本轮未修改代码。

#### F2 / P2 / 不单独阻断普通 CRUD：已提交的配置变更被 UI 显示为普通失败重试

**场景与链路：** 管理员保存数据源/工厂 → `ManagedDatasourceService.save/saveFactory`（`mes-admin/.../managed/ManagedDatasourceService.java:248,325`）在中心事务内推进 generation → 提交后 Redis 失效失败 → [FactoryMetadataCache.java:108](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/managed/FactoryMetadataCache.java:108) 抛出 `ROUTING_INVALIDATION_PENDING`（HTTP 503，安全消息明确“已保存，请勿重复提交”）→ [request.ts:33](/Users/user/work/lixin/mes-base-ui/src/lib/request.ts:33) 仅识别 SESSION_REVOCATION_PENDING/BUSINESS_REJECTED，替换为“服务暂时不可用，请稍后重试”。工厂配置页 `src/views/system/factory/index.vue:44` 仍停留在失败的保存流程。

**已验证：** `RoutingMetadataRedisTest` 实跑确认事务已提交、pending 持久存在、新请求读取新代数、恢复后确认；另用当前 request.ts 转译后的独立 Node 探针输入真实错误码与消息，得到保留错误码、丢失“已提交”语义的上述通用提示。日志 `probes/request-ui.log`。没有向真实管理接口写数据。

**影响：** 用户可能重复提交已经生效的管理配置，造成冲突或额外版本/池刷新；未发现此处导致旧配置越权或串库。账号变更的 SESSION_REVOCATION_PENDING 已有专用文案，不能把它的正确性推广到新增路由协议。

**最小整改：** 前端明确处理该错误码，显示“已保存、刷新待恢复、勿重复提交”，刷新登记状态并提供查询/恢复反馈；不要将 afterCommit 失败描述成数据库回滚。进入管理配置交付验收前处理。

#### F3 / P2 / 不阻断：15 秒超时只覆盖响应头，响应体可长期等待

**场景与链路：** fetch 收到 200 或错误响应头后，服务器/代理未结束响应体 → [request.ts:25](/Users/user/work/lixin/mes-base-ui/src/lib/request.ts:25) 的 finally 已清除 AbortController 定时器 → `response.json()`、`response.text()`（:41）或 blob（:43）继续等待。列表/保存的 loading 可能长期不结束。

**已验证：** 当前模块的独立 Node 探针返回立即完成的 headers 与永不完成的 text promise，观察到请求未结束、有效 deadline timer=0、signal 未 abort。此为控制流验证，不是实际网络故障压测；没有声称所有真实请求必然卡住。

**最小整改：** 将截止时间覆盖到完整 body 消费，保留 body 前后上下文版本检查与旧 401 防护。无需以任意 200ms 标准判失败，也无需全站视觉整改。

### 6.3 证据缺口（与缺陷分开）

| 编号 | 尚未证明的边界 | 已有证据 / 缺口与准入影响 |
| --- | --- | --- |
| E1 | 真实 MySQL 的干净迁移、两个业务库隔离、Druid 物理身份与重连 | 本机发现 mysql-client，但 `command -v mysqld docker` 均无结果，无自有隔离 MySQL 服务；未使用用户连接配置或真实数据库。4 个 MySQL 用例按门控跳过。H2 的回滚/隔离及 Mock Druid 的冷池证据不能替代它。首次真实业务库联调前补做；阻止无条件 PASS。 |
| E2 | 两个完整应用进程中的缓存/撤销恢复、进程在提交/失效/确认之间崩溃，Redis 实际重启 | 实跑自有 Redis、两个独立 Lettuce 连接及服务对象、并发 Lua/旧回填、故障注入与恢复方法；不是两个完整 JVM，不是 kill/restart 故障注入。池并发用线程＋H2/Mock，文档中的周期恢复由实现与定向方法测试支持，未证明实际崩溃后定时调度全链。涉及多节点恢复的复杂流程前补做。 |
| E3 | 登录后浏览器端到端、真实会话与 UI 交互 | UI 127 项、构建、生成页面构建均通过，覆盖旧响应/旧 401/切厂等；本轮未获取凭证，依赖登录的浏览器验证暂停。F1 的 UI 影响为已证实后端失败＋前端源码推理，不称浏览器实测。 |

迁移静态核对：`PersistenceConfiguration.java:38–42` 在 masterDataSource 发布前按显式开关执行 `db/platform`＋`db/system` Flyway；platform V1、system V2–V21 顺序存在。V19/V20 提供会话撤销记录/重试，V21 提供单行路由 generation/delivered_generation。启用 system 的当前账号 SQL 已依赖 V21，升级必须先完成该迁移。**打包 smoke 用的是无真实连接的默认配置，只证明包和基础匿名链路能启动，不能证明全功能干净 MySQL 启动。**

### 6.4 A–F 审查覆盖与已验证行为

| 范围 | 源码链路及独立证据 | 判断 |
| --- | --- | --- |
| A 请求/授权 | `request.ts` → `SystemTokenFilter` → `TokenService.resolve` → `SessionAccountMapper.account` → `EndpointScopePolicy` / `EndpointCatalog` → `FoundationAccessInterceptor` → `SystemPermissionService` → scoped grant SQL → Controller/Service/Mapper。非公开默认认证；每方法显式注解与 URL 范围启动检查。`FoundationHttpTest` 14、`EndpointScopePolicyTest` 2、`RoleScopePermissionTest` 6、`SystemTokenFilterAdmissionTest` 2 通过。 | 平台/共享/私有入口区分、完整路径段分类、当前厂与他厂授权负例有证据；F1 是剩余缺口。请求头不决定 URL 归属，平台分页带头也未绑定业务库。 |
| A 菜单与权限码 | `SystemPermissionService.java:60` 不再读取全菜单；`SysMenuMapper.selectScopedPermissions` 合并 PLATFORM 与当前 FACTORY 的有效授权。`SystemConfiguration.java:56` 启动构建 endpoint permission→scope 并审计；`SystemPermissionService.java:97` 拒绝跨范围复用权限码和工厂角色历史平台授权；角色分配、菜单增改删继续校验。`MenuScopePolicy` 解析父子完整 URL、按钮继承。 | 请求路径不按权限前缀猜范围；平台角色持有任意权限符合规则。角色测试的部分断言确由 false 改 true，但增加了真实 URL 拒绝链、历史脏授权拒绝和重复码检查，未以这类变更本身断言“越权”。直接绕过管理服务修改数据库后的在线漂移未测试，不能作为受支持配置入口。 |
| B 元数据缓存 | `SessionAccountMapper.xml` 的密码外账号/角色查询同时取得 routingGeneration；`FactoryMetadataCache` → Redis `RoutingMetadataStore` → miss 才查 factory LEFT JOIN datasource → generation+epoch fenced publish；请求内复用定义。绑定、启停、删除、连接定义/版本写入均调用同事务 changed。 | 冷/暖 SQL 计数实测；Lua 两客户端旧读取回填被拒绝、同代数 hash 重建 epoch 隔离、旧失效不覆盖新代数、提交失败回滚、提交后失效失败恢复均通过。Redis 读/发布失败拒绝准入，不使用旧本地回退。 |
| B 缓存安全/环境 | `SystemCacheNamespace` 以显式 namespace 与中心 JDBC identity 隔离；payload 为 `FactoryExecutionMetadata`，密码仅密文；解密只在池准备阶段。Redis 不存 Connection/Session/pool。未知 ID 不做共享负缓存，避免无限增长。 | 源码/已有 namespace 与缓存测试支持；不宣称进行了生产 Redis 数据扫描。有效绑定 hash 全局失效与 300 秒 TTL 可形成冷加载波动，属后续性能事项。 |
| B 快照/租约 | `ManagedDatasourceService.admit:430` → `BusinessRequestSnapshot` 预留版本 → 事务 begin 获取该预留的池租约 → MyBatis guard / Spring commit → 归还 lease → request finish 归还 reservation。HTTP 内后续独立事务、REQUIRES_NEW 均沿用请求快照；无 HTTP 的独立事务才重新准入。 | `RequestSnapshotTransactionTest` 3、`ManagedColdPoolTest` 4、`ManagedAdmissionTest` 1 通过：尚未取连接的旧请求、版本更新、同版本禁用后再准入、16 路冷启动仅建一池、异厂并发、异步完成、回滚/异常清理均有定向证据。冷池测试的 JDBC physical probe 是 Mock，明确不是 MySQL。 |
| B 固定拓扑/事务 | `PersistenceConfiguration`、`OwnerMapperRegistration/OwnerMapperCatalog` 固定平台三包、业务两包；两个 DataSource 入口、两个具名 SqlSessionFactory、两个事务管理器；Core FactoryScope 仅 FactoryId。 | `PersistenceAssemblyTest` 4、`InfrastructureContextTest` 1、`PersistenceDeepAuditTest` 13 等通过：缺 Scope/绑定失败，无默认库，双向跨库拒绝、事务内切厂拒绝、挂起恢复、池关闭失败重试。未要求中断已准入请求/事务。 |
| C 会话/撤销 | 随机 Bearer、安全身份与绝对有效期；无滑动续期。每请求重新读取密码外账号状态、auth revision 和角色，权限在请求内复用。用户禁用/删除/改密写账号版本与 outbox，同事务提交；afterCommit Redis 索引撤销，失败返回 SESSION_REVOCATION_PENDING；版本先挡住旧会话。注销直接撤销当前 session。 | `SessionAdmissionTest` 7、`IndexedRedisSessionTest` 1、`RedisRevocationCapacityTest` 1 通过。覆盖真实用户变更 Service 入口、失败后已提交状态、250 项公平重试、1001 用户分批、5–300 秒退避、同版本不重置、旧版本确认、确认失败再恢复；不扫描全部在线 session。进程 crash 见 E2。 |
| D 生成器/工作流 | 实际 Skill、review YAML、`scripts/mes-business-codegen`、BusinessCodegenCli/Runner/Configuration、模板、ScaffoldWriter、person/pro_workorder 产物均已读取。新配置完全显式，旧 CLI 仍有默认值但没有被本次验收用作新入口替代。 | `BusinessCodegenRunnerTest` 5、`CodegenAcceptanceTest` 13、person acceptance 等通过；独立 CLI 见 §6.6。单表使用 MBG Mapper/XML/Example；Service 方法与 MBG BLOB/普通方法对应；无单表 Extra。 |
| D 使用边界 | 包/承载 Maven 模块/API 模块/权限前缀/属性开关/输出位置来自配置；字段名 user_id/owner_id/password/secret 不作为拒绝理由，生成能力测试包含此场景。`pro_workorder` 单删/批删共用 delete 权限与 Service 批删链，数量/ID 校验，影响行数不符回滚。 | 生成能力不等于自动安全放行所有字段；敏感白名单和状态入口由使用方审查。表/字段 COMMENT、普通索引和先建模后生成的规则已在 AGENTS/Skill 明确，生成器不私自新增业务约束。 |
| E 前端契约 | `request.ts` snapshot(token,factoryId,revision)；响应头和 body 后检查；旧 401 先判过期；选厂清授权/数据/对话框；storage 只存 token/有效期/偏好，恢复向服务端重新读取。`menuWorkspaces.ts` 完整段分类，auth 分平台/业务 permission。 | UI test 23 文件/127 项及 build 通过；F2/F3 为本轮独立复现的剩余错误处理问题。权限按钮、分页/错误、恢复与切厂有组件/逻辑证据，无全站视觉结论。 |
| E 目录组件 | `mes-business/.../directory/controller/BusinessDirectoryController` → `BusinessDirectoryService` → 既有中心 sys_user/dept/post Mapper、master 只读事务；六个 GET。`BusinessDirectorySelect` 版本与授权变化清理候选/选项。 | `BusinessDirectoryTest` 4 和前端目录测试通过；人员响应仅 ID/名称/编码/部门 ID，未暴露 SysUser 密码、联系方式、角色。中心目录是跨厂共享引用，不声称按厂过滤目录行。 |
| F 运行基础 | 完整 Maven verify/打包 smoke/UI test/build；Druid Settings 到实际 setter 已逐项对照；平台与业务请求预算分别计数。 | 结果见 §6.5–6.6。未把 SQL 数量换算成延迟，也未使用任意 200ms 阈值。 |

只读参考 `ktg-mes` 的 ProWorkorder Controller/Service/Mapper：原产品包含 BOM、完成动作等协调；Base 当前 `pro_workorder` 是生成 CRUD 样本，不能冒充旧工单完整流程已迁移。复杂业务按“对应业务 Service 承载规则，聚合 Service 编排；纯数据访问可用生成 Mapper”接入；不得用普通编辑/删除绕过新状态规则，也不强制复杂流程使用生成 CRUD 页面。

### 6.5 新链路、调用预算与池成本

```text
前端 request（Token + 已选 X-Factory-Id + revision）
  → Redis 会话读取 → 中心账号/角色/认证版本/路由 generation 查询
  → URL 完整段分类 → 本请求权限 SQL（无全菜单范围扫描）
  → 业务：Redis 绑定元数据 → 固定请求快照 + reservation
  → Controller → Service 具名事务 → 对应版本池 lease
  → Mapper/XML/Example → 业务数据库 → 提交/回滚 → 归还 lease
  → 请求完成归还 reservation；退休池无 reservation/lease 后关闭
平台 URL → 仅平台角色/权限 → 中心 Mapper，不因选厂头接入业务池
```

计数单位：Redis 为客户端命令/脚本往返，不是 Lua 内部 HGET/DEL 次数；SQL 为应用 statement，不含 TCP/TLS 握手、驱动元数据、池健康检查及后台轮询。均为有权限、有结果的分页例子；无权限请求会更早结束。

| 场景 | Redis | 中心库 SQL | 业务库 SQL | 证据 |
| --- | --- | --- | --- | --- |
| 平台岗位分页 `/api/system/post/list?pageNum=1&pageSize=20`，携带工厂 A | 1 会话 | 4：账号/角色/代数 1、授权 1、count 1、rows 1 | 0 | 独立 PlatformBudgetProbe，25 次；真实 TokenFilter/Interceptor/SysPostController/Service/Mapper，H2，Redis payload Mock；路由 Redis read=0 |
| 业务工单分页，显式 A，暖元数据＋暖池 | 2：会话 1＋绑定 read Lua 1 | 2：账号/角色/代数 1＋授权 1 | 2：count＋rows | ProWorkorderRequestBudgetTest，25 次中后续 24 次；无全菜单、无 factory/datasource 查询 |
| 同请求模型，冷元数据＋暖池 | 3：前述 2＋publish 1 | 3：前述 2＋定义 JOIN 1 | 2 | 同测试首次请求。publish 冲突可重试最多 3 轮，非固定此数 |
| 冷池且绑定元数据已暖 | 前述暖业务预算 | 前述＋物理中心身份探测 1 | 前述＋业务物理身份探测 1 | ManagedDatasourceService.prepare 静态推导，冷池 Mock 测试验证路径；真实建连成本未测 |
| 空闲后首请求 | 元数据未过期时同暖路径；过期则冷加载 | 同对应路径，可能另有连接校验/重连 | 同对应路径，可能 SELECT 1/重连 | Druid 配置推导，未做真实 MySQL 空闲首请求计时 |

未携带 X-Factory-Id 时不能套用显式 A 预算：会读取持久偏好；无有效偏好走 default 缓存，冷 default loader 查询默认候选及定义（2 条）。工厂选项/导航本来需要额外列表/菜单查询，不能算作业务分页热路径回归。

Druid YAML 实值：initial/minIdle=0、maxActive=5、maxWait=5000ms、connectTimeout=5000ms、socketTimeout=30000ms、eviction=60000ms、min/max idle eviction=300000/900000ms、testWhileIdle=true、testOnBorrow/Return=false、SELECT 1、stat/wall。`DruidPools.Settings` 校验初始/最小不超过最大、上限 100、正超时/驱逐区间等，create 将这些值传给实际池；不能只因 YAML 存在就称已绑定。

累积成本为中心池＋本实例触达的各工厂/版本业务池；每池最多 5 连接，旧新版本在快照/事务排空期间重叠，无全局连接配额。minIdle=0 会回收物理空闲连接，不等于 pool 对象自动消失。冷加载使用 64 条带锁，同条带的冷请求可能等待；JDBC 超时不构成整条请求排队上限。尚无证据表明普通暖分页仍有原先全菜单/重复路由 I/O 放大；F3 的 body 等待已复现，应后续收敛。

### 6.6 实际执行、结果与跳过项

所有构建/生成/启动均在自有 `/private/tmp/mes-base-admission-20261011` 内的副本/探针进行。没有启动、停止或重启用户服务；Redis 测试只启动并结束自己的随机本地端口实例。没有执行任何用户库迁移、菜单 SQL、建表或授权。

| 实际命令/入口 | 结果与证据位置（下列路径相对审查 tmp 根） |
| --- | --- |
| 规则读取、`git status --short`、`git branch --show-current`、`git rev-parse HEAD`、逐文件 SHA-256 | 读取个人 AGENTS 及任务路由规范、两仓/目标作用域规则、参考项目规则；记录初始、S1/S2 与结束变化。S2 两 manifest 保留完整 dirty 清单 |
| S1 `mvn -B -ntp verify -Dmes.test.redis=true` | 234 项：227 通过、3 error、4 skip；中间版本 V21 fixture 缺表。`maven-verify.log`。随后 `mvn -B -ntp package -DskipTests` 只为诊断包启动，成功不代替 verify；S1 smoke 10/10 |
| **S2 `mvn -B -ntp verify -Dmes.test.redis=true`** | **BUILD SUCCESS；汇总 243 项，239 通过、0 failure/error、4 skip**；`final-snapshot/verify.log` 与各 surefire XML。Redis 的 3 个门控测试实际运行，不是跳过 |
| **S2 `/usr/bin/python3 scripts/smoke-packaged.py`** | **passed 10 / failed 0**；`final-snapshot/smoke.log`、副本 `target/validation`；随机端口独立 JAR，无开发连接配置 |
| `PATH=/Users/user/.nvm/versions/node/v22.23.1/bin:$PATH npm test` | **23 文件、127 项通过**；`final-snapshot/ui-test.log`。S1 亦通过，但结论采用 S2 |
| 同 Node 的 `npm run build` | **vue-tsc＋Vite 成功**；`final-snapshot/ui-build.log`；大 chunk 提示是后续性能债务 |
| 同 Node 的 `npm run codegen:preview:build` | **生成 person 页面类型检查＋构建成功**；`final-snapshot/codegen-ui-build.log` |
| `javac -cp <S2 surefire classpath> -d probes probes/EmptyFactoryProbe.java`；`java -cp <probes:classpath> EmptyFactoryProbe` | exit 0，断言复现 F1 的两组 200/400 对照；`probes/empty-factory.log`。exit 0 代表探针确认缺陷，不代表产品通过 |
| 同样编译并运行 `dev.mesbase.server.PlatformBudgetProbe` | 25 次断言通过；`probes/platform-budget.log`。探针首次因提取 fixture DDL 的换行形式失败，修正仅在 tmp 的提取器后通过，不是产品 SQL 缺陷 |
| Node 将 S2 request.ts 用现有 TypeScript 转译，注入 503 与悬空 body 响应 | F2/F3 复现；`probes/request-probe-module.mjs`、`probes/request-ui.log`；未连接真实 API |
| H2 `org.h2.tools.RunScript` → 明确隔离 `probes/codegen/metadata` 文件库 | 仅建立 person 与按现有 XML 字段构造的 pro_workorder 合成 metadata，无用户数据；表/字段 comments，不是 MySQL metadata 证明 |
| `JAVA_TOOL_OPTIONS=-Dloader.path=<现有 H2 jar> ./scripts/mes-business-codegen <probes/codegen/selected.yaml> <selected.json>` | 新配置入口、打包 CLI；write/fail、独立输出，实际 native formatter=true。**只 person，20 文件** |
| 同入口 `<all-preview.yaml> <all-preview.json>` | tables=[]，**两表、40 个规划文件、DRY_RUN**；tableOverrides 不影响选表 |
| 同入口 `<all-write.yaml> <all-write.json>` | **两表、40 文件 GENERATED**；逐文件 SHA-256 与报告 **40/40 一致**；26 个生成主 Java 文件独立 javac 成功，`probes/codegen/compile.log` |
| 在隔离 person API 文件加入 TEST ONLY 手工变更后，同入口 all-write/fail 再运行 → conflict.json | **预期 exit 2 / FAILED_PREFLIGHT / OUTPUT_CONFLICT**；发布前后全部现有 40 文件 hash 不变。`probes/codegen/audit-results.json`；没有覆盖原仓 |
| 两原仓 `git diff --check`、`git diff --cached --check` | 追加报告前均 exit 0；报告追加后再次核验，见结束记录 |

代码生成的单表 CRUD 数据执行、BLOB 方法、保留字、物理删除/批删、回滚/跨厂负例、菜单 SQL 重跑保留 ID/角色关联由本轮实际运行的 CodegenAcceptanceTest / BusinessCodegenRunnerTest 支持。菜单 SQL 只在 H2 合成 sys_menu/sys_role_menu 中重复执行，未写用户中心库。没有测试“无唯一约束时并发跑菜单 SQL 防重”；文档已明确要求串行执行。

跳过的 4 项：`MySqlDevelopmentTest.distinctRuntimeOwnershipRollbackAndNoFallback`、`MySqlDevelopmentTest.realMetadataProducesUnqualifiedMySqlManifest`、`WorkspaceAccessMySqlTest.currentMixedCollationSchemaWorksThroughProductionMapperAndWallFilter`、`ManagedRuntimeMySqlTest.mysqlLifecycleAndDistributedSync`。原因均为未提供自有隔离 MySQL，不借用用户真实库。真实登录浏览器、全功能 MySQL 干净启动、双 JVM 崩溃恢复、部署、生产压力/合规验收未执行。未为只读审查格式化原仓或修改测试断言。

### 6.7 进入业务后的债务与生产前验收

F1 是本次必须先修的基础缺陷。F2/F3、大包按需拆分、格式与局部可读性、冷缓存 stampede/全局失效波动、条带锁等待、每实例工厂池总量、过期会话用户索引/永久认证 epoch 的容量治理，均可按有边界任务处理，不以架构形式阻止简单业务开发。代码生成的覆盖策略已明确：现有正式 YAML 为 write/replace-generated，后续开发必须先读最新配置，不能当成默认预览；本次全程只用独立配置副本。

复杂业务应先建模并按已确认字段/状态落表，表和字段有 COMMENT，主键之外普通索引规则照常；并发唯一性确需数据库约束时单独请用户决定。生成完整基础后端，前端按需求选用；业务规则放在对应 Service/策略，聚合 Service 复用能力，纯数据访问使用生成 Mapper。单表复杂条件/统计/批量不是另写 ExtraMapper 的理由；真实跨表 JOIN 才使用 Extra。不能把生成的普通更新/删除入口作为绕过状态规则的路径。

**生产上线前另需完成：** 在与部署一致的 MySQL/Redis/JDK 条件下完成全量干净迁移与升级、真实多厂数据库及混合 collation、双应用进程失效/撤销/崩溃恢复、Druid 连接异常和闲置重连/旧池排空、总连接预算与有代表性的并发延迟；真实浏览器负权限/切厂/过期响应/已提交待恢复协议；备份恢复、实际密钥及缓存 namespace 配置、资源策略与运维恢复演练。外部系统、Quartz 真实执行器和 OpenAPI 消费者若进入交付，再做其专属身份、幂等和 UNKNOWN 恢复验收。这些不是本次生产认证，也不要求在开始所有普通后台开发前全部完成。

### 6.8 工作区保护与结束核对

本轮对原仓仅追加本报告，未改业务代码、生成器、配置或 AGENTS；未提交、推送、迁移或访问用户真实数据库。引用源码行号以 S2 为准。结束比对结果在下方追加，已有历史章节保持原文。

结束检查时间：**2026-10-11 20:49–20:51 +08:00**。两仓 HEAD/分支未变，UI 仍 clean。相对 S2，除本报告外的外部变化为：

- `AGENTS.md`：建表/新增字段 COMMENT 与 SQL 说明明确改为中文；已读取新增要求。本报告没有改 AGENTS。
- `mes-admin/src/main/resources/db/system/V19__indexed_session_revocation.sql`、`V20__session_revocation_retry_schedule.sql`、`V21__routing_metadata_generation.sql`：增加/改为中文 COMMENT 和说明。结构字段/索引未变，但迁移文件字节和 Flyway 校验和可能变化。已应用 V19/V20 的环境是否发生校验冲突尚未验证，不能凭“仅注释”免除升级验收；未执行 repair 或任何数据库操作。
- `docs/reviews/2026-10-11-pro-workorder-latency-review.md`、`docs/reviews/2026-10-11-request-admission-implementation.md` 被外部更新，新增 `docs/reviews/2026-10-11-overdesign-review.md`；这些外部报告不作为本审查的通过证据。

因此，**S2 的 Maven/打包与迁移资源证据只对应 S2，不能覆盖结束时这三个 SQL 文件的新版本**。F1–F3 所引用的后端 Java/前端源码与 S2 一致，缺陷结论仍有效；Java 事务/缓存行为测试仍对应相同实现。当前工作区仍判 FAIL，未因并发文档/注释变化自动关闭发现或给出新版本 PASS。

结束时两仓 staged/unstaged `git diff --check` 均 exit 0；本报告原有前五节的 **15,834 字节历史前缀逐字节未改**。完整结束清单与检查输出保存于 `/private/tmp/mes-base-admission-20261011/final-workspace-check.json`。`.idea/codeStyles/Project.xml` 虽不在 git 枚举内，另行比对仍与 S2 相同，不计为外部修改。

## 7. 2026-10-11 初始化空工厂修复

用户确认初始化应允许零工厂登录。本轮局部修复 F1，保留第 6 节历史 FAIL 及 S2
证据，不以本轮修复直接宣布全系统准入 PASS。

- EndpointScopePolicy 明确工厂发现/选择入口：foundation/factories、context、preference。
  这些入口保留身份认证与有效选厂检查，但不准备业务绑定或连接池。
- FoundationAccessInterceptor 对身份级访问保留空 Scope；实际 AUTHORIZED 业务访问
  仍 required，已选厂的其他业务入口仍执行绑定准入。数据库事务也保留自身准入，
  没有默认工厂/中心库回退。平台管理继续验证平台角色及操作权限。
- 新增 FoundationInitializationTest，使用真实 Controller/Service/拦截器/异常协议和
  请求边界过滤器，通过 MockMvc 安装非空 runtime admission callback，避免原测试关闭
  runtime 而遗漏该分支。验证空角色账号空列表/空上下文、平台配置可达、从空状态选择
  无绑定工厂、缺绑定业务拒绝、有效绑定业务准入、匿名/非平台拒绝及线程清理。
  Catalog 和 admission 为隔离 fixture，不冒充真实 Managed/MySQL/Redis 完整启动验收。
- AGENTS 补充初始化规则。检查 UI auth.restoreFactory/selectFactory：空上下文清空选厂，
  不请求业务导航；复用现有会话恢复测试，不改前端生产代码。

验证：3 个 Java 文件 IDEA 格式化及 --check 通过；定向 Maven test 20 项通过
（新回归 6 项、原 FoundationHttpTest 14 项）；完整 mvn -B -ntp test 共 250 项，
243 通过、7 跳过（真实 MySQL 4 项及 opt-in Redis 3 项），无失败。
Node 22.23.1 下 npm run test -- tests/sessionRestore.test.ts 共 10 项通过。
日志：/tmp/mes-initialization-{targeted,full-test,format-check}.log。

完整 mvn -B -ntp verify 同样 250 项、243 通过/7 跳过，BUILD SUCCESS；
/usr/bin/python3 scripts/smoke-packaged.py 10/10；两仓 git diff --check 通过。
日志 /tmp/mes-initialization-verify.log、/tmp/mes-initialization-smoke.log。
打包 smoke 为隔离默认配置，不代表运行时启用且真实 MySQL/Redis 环境的初始化联测。

未执行真实数据库迁移或重启用户服务，未进行真实账号浏览器联测，不涉及缓存简化、
F2/F3 修复或业务功能新增；保护工作区已有并行改动，未提交或推送。

---

## 8. 2026-10-11 普通业务开发限定复审（独立验证）

本节仅复审 §6 的 F1/F2/F3、§7 初始化修复、路由缓存简化及其直接回归。最新用户决定优先：cache-aside、提交后淘汰、独立固定 TTL、允许短暂旧值；不再以代际/epoch/持久失效重试或“请求尚未开事务也保有旧池资格”为准入条件。账号认证版本和会话撤销保持原规则。§6 的 FAIL 与 §7 实施记录完整保留，实施回执没有作为测试证据。

### 8.1 原发现的当前状态

| 原发现 | 当前判定 | 本轮独立证据与边界 |
| --- | --- | --- |
| **F1** | **关闭原缺陷**；新运行时配置缺口见 N1，不混同为 F1 复发 | FoundationInitializationTest 6 项通过；另补实际 Spring 装配探针：开启 system/platform/runtime，使用生产 FoundationConfiguration、FactoryRuntimeConfiguration、PersistenceConfiguration、SystemConfiguration、真实 Managed 服务/Mapper/Redis Token 恢复与权限 SQL，执行 11 个 MockMvc HTTP 场景及双会话工厂/事务管理器断言。空角色、零工厂返回 `[]`/`factoryId=null`；存在启用但无绑定工厂时无默认候选；平台角色可访问真实 `/api/system/factory`，空角色 403；可保存无绑定工厂偏好；业务缺 Scope=400、无权限=403、缺绑定=422，直接业务 DataSource 无 Scope 也拒绝。**探针显式提供 TTL=300；中心库为 H2，自有 Redis，MVC 没有 socket；测试 token 由实际 TokenService 发行，未端到端调用密码登录接口，未使用真实账号/浏览器。** |
| **F2** | **被新方案取代**；不再修复废弃的 ROUTING_INVALIDATION_PENDING 协议 | 对实际管理 Controller 发起授权 POST，故障注入只令 Redis invalidate 抛错：HTTP 200、返回新工厂、中心记录已提交，并记录淘汰失败日志。路由旧错误码/代际确认/持久重试已从运行源码移除。账号 SESSION_REVOCATION_PENDING 保留，7 项 SessionAdmissionTest 和前端独立协议探针确认仍表述“已保存、撤销重试、勿重复提交”。 |
| **F3** | **仍存在，P2 后续项；不单独阻断普通 CRUD** | request.ts 未修改：fetch headers 完成后 finally 清除 timer，随后 body 消费仍可等待。本轮分别模拟成功 JSON/text、错误 JSON、Blob 的悬空 body，四种情况均为 `pending=true, activeDeadlineTimers=0, aborted=false`。是模块控制流证据，不是实际慢网络压测。 |

F1 的改动位置：[FoundationAccessInterceptor.java:88](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/web/FoundationAccessInterceptor.java:88) 只在非发现/选择入口、有实际 Scope 时调用 admission；[EndpointScopePolicy.java:26](/Users/user/work/lixin/mes-base/mes-framework/src/main/java/dev/mesbase/platform/web/EndpointScopePolicy.java:26) 精确列出三个工厂发现/选择路径。AUTHORIZED 仍在 :85 使用 required Scope，没有把业务授权变成可空，也没有默认库回退。`FoundationInitializationTest` 的 catalog/admission 本身是 fixture；本次增加的实际装配证据不依赖其模拟 callback。

前端 `src/stores/auth.ts:99,146,162` 的登录/恢复/选厂逻辑未改：空上下文进入 selectFactory('', false)，清空业务授权并跳过业务导航，不因此清除 token。现有 sessionRestore.test.ts 10 项（含空候选恢复、新登录后的持久化）随全套 UI 测试独立通过；不是沿用 §7 的回执结果。密码登录到真实浏览器仍未验证。

F2 真实链路：[ManagedDatasourceService.java:307](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/managed/ManagedDatasourceService.java:307) saveFactory → PlatformPersistence 中心事务 → [FactoryMetadataCache.java:75](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/managed/FactoryMetadataCache.java:75) 注册 afterCommit → 删除受影响 binding 与 default → :84 捕获淘汰异常、记录 ERROR → Controller 返回已提交结果。异常注入探针使用实际安全链、权限 SQL 和 Controller，仅替换删除调用为失败；没有修改真实配置或数据库。

F3 位置仍为 [request.ts:25](/Users/user/work/lixin/mes-base-ui/src/lib/request.ts:25)、:29、:41、:43。后续最小修正是把 deadline 延续到完整 body 消费，保留上下文检查和旧 401 防护，本轮不修改实现。

### 8.2 新增回归与发布风险

#### N1 / P1 / 当前仓库默认准入阻断：必需的 TTL 参数未接入配置

**场景及链路：** 使用当前公共 application.yml 和示例配置的参数集合启用实际运行时 → FoundationConfiguration.accessRules → selection → FactoryRuntimeConfiguration.runtimeCatalog → managedDatasources → factoryMetadataCache → routingMetadataStore → [FactoryRuntimeConfiguration.java:45](/Users/user/work/lixin/mes-base/mes-admin/src/main/java/dev/mesbase/server/FactoryRuntimeConfiguration.java:45) 执行 `env.getRequiredProperty("mes.datasource.metadata-ttl-seconds", Long.class)` → Spring 上下文创建失败。

**源码事实：** 当前 [application.yml:48](/Users/user/work/lixin/mes-base/mes-admin/src/main/resources/application.yml:48) 的 mes 配置没有 datasource.metadata-ttl-seconds；两个 config/application-*.example.yml 也没有该键。`docs/development/request-admission.md:34` 却声称 application.yml 已显式配置 300 秒。未读取用户私有 profile，因此不推断某个正在运行的用户服务是否恰好另有该参数。

**已验证：** 原样固定副本执行 `mvn -B -ntp verify -Dmes.test.redis=true`，实际生产配置装配测试 InfrastructureContextTest 报 `Required key 'mes.datasource.metadata-ttl-seconds' not found`，BUILD FAILURE。全体 250 项中 245 通过、1 error、4 MySQL skip；不是因 Redis/MySQL 不可用，也不是 F1 的旧 runtime=false 测试。只在命令行追加 `-Dmes.datasource.metadata-ttl-seconds=300` 后，**同一源码** verify 成功（246 通过、4 skip）；上述实际装配探针同样显式注入这个值。

**影响：** 当前标准验证入口为红，按文档提供的配置启用 runtime 的 Spring 装配不可用。这是一项很小但具体的配置接入遗漏，阻断对“当前仓库正常基线可直接进入业务”的签字；不是要求恢复强一致缓存或重新建设基础设施。已证明显式补参的隔离环境可继续普通开发验证，但不能把诊断补参后的绿色结果记为原样版本通过。

**最小整改建议（未实施）：** 在项目实际加载的公共配置中显式提供正 TTL（例如已经约定的 300 秒），或统一明确的外部配置入口并同步示例/测试/文档；然后不依赖临时补参运行标准 verify，并核对 runtime 启用的装配。无需修改缓存方案、业务代码或权限设计。

#### N2 / P1（升级条件触发）/ 不单独阻断新环境普通开发：V19/V20 校验和已变化

当前 V19/V20 相对 HEAD 增加中文数据库 COMMENT/说明，字段与索引语义未变，**文件校验和已经变化**。本次用实际 Flyway 在自有 H2 内先应用 HEAD 版本，再换为本次快照文件执行 migrate：明确收到两个 checksum mismatch：

| 迁移 | HEAD 应用校验和 | 本次文件校验和 |
| --- | --- | --- |
| V19 | -1383834418 | -214897213 |
| V20 | 1534405106 | -767963553 |

位置：`mes-admin/src/main/resources/db/system/V19__indexed_session_revocation.sql:1`、`V20__session_revocation_retry_schedule.sql:1`；生产迁移调用在 `PersistenceConfiguration.java:40–42`。对**已应用对应旧字节、并在启动时执行 Flyway migrate/validate** 的环境，这是明确的升级门禁。H2 验证证明校验机制会拒绝，不证明用户真实 MySQL 已应用旧文件或真实服务已启动失败。本轮没有读取真实 schema history、执行真实迁移、repair 或重启。

最小处理：发布前确认目标环境的已应用版本/校验和，保留已应用迁移的原始文件，必要的注释改动通过新前向迁移表达；不能无条件指导 repair。当前 request-admission.md 的迁移说明重点处理 V21，对 V19/V20 仍只写“按序应用”，尚不能替代本升级核对。

**V21 处置单列：** V21 仍是未跟踪文件，`git log --all -- <V21>` 无提交历史；内容仍会创建 `sys_routing_metadata`。运行代码/测试没有任何该表读写、投影、后台交付或错误协议引用。文档明确保留可能已被手工应用的历史迁移，不自动删除/重写或 DROP。故运行机制已移除，历史 schema 产物没有完全删除；这是有说明的兼容遗留，不因多一张不用的状态表阻断普通开发。Git 无历史不能证明所有真实环境从未应用。旧 Redis hash 键空间也不再被读取；不对用户 Redis 执行清理。

### 8.3 限定结论及适用范围

**FAIL（针对当前仓库原样准入基线），唯一新增普通开发阻断为 N1。** 原 F1 已关闭，F2 被新方案取代；F3 不升级为阻断。缓存简化本身没有发现越权、串厂/串库、已开启事务失效或生成链路不可用的新增缺陷。

| 问题 | 结论 |
| --- | --- |
| 1. 是否可以开始简单 CRUD | **当前仓库原样基线暂不正式放行，先补 N1。** 显式提供 TTL=300 的隔离环境已验证可运行，生成/CRUD 能力可复用；无需重开全仓建设。补齐配置并通过标准验证后，可按普通业务范围重新签 CONDITIONAL PASS。 |
| 2. 是否可以开始单业务库内 Service 编排 | **与简单 CRUD 相同，只卡 N1 配置接入。** 单厂单业务库的事务、回滚、挂起恢复、权限和隔离有证据；不要求恢复请求级 reservation，也不要求尚未开事务的旧请求可以创建旧版本池。跨中心/业务库原子提交、外部系统/真实同步执行器不在这次放行范围。 |
| 3. 是否具备真实环境联调条件 | **不能按现有证据宣布已具备。** 技术入口具备，须先提供有效 TTL、独立中心/业务 MySQL 与隔离 Redis、处理 N2 的目标环境迁移历史，再验证全功能启动及真实业务库连接。环境未验证不是另一个源码 FAIL；未访问用户真实配置/数据库。 |
| 4. 生产上线前还缺什么 | 完整 MySQL 迁移/升级、真实多厂数据库与池身份/异常/空闲重连、真实登录与切厂浏览器联测、生产配置/连接总量/权限负例、账号撤销故障恢复与实际部署拓扑验收。缓存采用允许短旧值的新契约，不追加跨节点立即一致或持久失效 outbox 要求。 |

N2 只在对应旧环境升级阶段阻断；F3、缓存短暂旧值、冷加载重复查询、池总量和 UI 构建大 chunk 等均作为后续事项，不借此阻止已正确配置的普通业务实现。

### 8.4 缓存简化直接回归核对

| 检查点 | 本轮事实 / 验证 |
| --- | --- |
| 固定 TTL、命中不续期、其他键不延长旧值 | RoutingMetadataStore 使用独立 string key，GET 只读，SET 携带 Duration；真实独立 Redis 的 RoutingMetadataRedisTest 测量剩余 TTL，读取 A/写 B 不延长 A，分别到期。**参数的生产接入遗漏见 N1，不能混为缓存算法失败。** |
| 提交后淘汰与派生缓存 | FactoryMetadataCache.changed 在事务内注册 callback，批量删除受影响 binding 与 default；Managed 的增/改/绑定/解绑/启停/删路径均调用。真实 Redis＋H2 测试断言 commit 后两键消失、rollback 不淘汰；实际管理 POST 的失效故障响应已补证。 |
| 淘汰失败与旧回填 | 淘汰失败只记录 ERROR，旧值等待独立 TTL；两个客户端按“淘汰→旧发布”的顺序重现允许的旧值回填，到期后消失。不是 CAS 或强一致证明。TTL 从该次发布起计，不是管理提交的绝对墙钟截止；慢请求可晚回填，文档已说明。无读续期、无本地无限旧值回退。 |
| Redis/DB 故障 | 读/发布异常返回 ROUTING_METADATA_UNAVAILABLE，过期后中心库不可用明确失败；真实 Redis 测试加故障注入通过。不把这个读取错误与已提交管理操作的正常返回混淆。 |
| 命中/未命中预算 | ProWorkorderRequestBudgetTest 实跑 25 次生产 filter/interceptor/Controller/Service/Mapper 链（H2、Redis stub）：暖请求中心 SQL=2（账号/角色、授权）、业务 SQL=2（count/rows）、session read=1、routing GET=1；首次冷绑定仅增加中心 JOIN=1、SET=1。默认选厂的候选与定义也已合成一次 JOIN。不是实网延迟数据。 |
| 旧机制/死代码 | 生产源码与测试搜索确认没有 AdmissionAccount/routingGeneration、路由代际 mapper 方法、路由 Lua/epoch、ROUTING_INVALIDATION_PENDING、持久失效重试、reservation/registerReserved；SysUser 账号投影回归现有字段。V21 历史迁移仍保留，见 N2。会话 UserSessionStore 中的 epoch/Lua 是有效账号撤销机制，不能当路由死代码删除。 |
| 池复用/冷并发/租约 | ManagedColdPoolTest 5 项：16 并发同源仅建一池、旧冷快照不能覆盖活跃新池、同版本重新准入的新旧池独立排空、旧池关闭失败重试不关闭新池、候选失败关闭；Druid/JDBC physical probe 为 Mock，中心为 H2。 |
| 已开启事务、切厂及清理 | RequestSnapshotTransactionTest 3 项、PersistenceDeepAuditTest 13 项、双 SessionFactory 装配等通过：事务已取得租约后继续完成；旧快照不开新事务也可被拒绝；双向跨库拒绝、事务内切厂拒绝、挂起恢复、回滚、异常线程清理继续有效。仅因旧“请求预留池”断言被删除不判退化，其契约已被用户取代。 |
| 权限/会话 | RoleScopePermissionTest 6、SystemTokenFilterAdmissionTest 2、SessionAdmissionTest 7、真实 Redis 会话/容量测试通过。逐请求权限不变、工厂/平台角色边界不变，账号 authRevision/outbox 公平重试未随路由缓存简化删除。 |
| 生成器 | 与 §6 S2 的文件 SHA-256 对比，codegen 源码、模板、CLI、review YAML、Skill 均未变化。verify 已重新执行受运行时变化影响的生成 CRUD/事务路由验收（CodegenAcceptanceTest 13、BusinessCodegenRunnerTest 5、CodegenPersonAcceptanceTest 1 等）。未另跑无影响的批量生成矩阵、未访问 metadata 真实库。已有生成能力结论可沿用，不能扩大为新业务状态规则自动正确。 |

### 8.5 固定版本、执行记录与证据等级

**快照 R1：2026-10-11 21:04:38 +08:00。** 后端 main / `7fb750199d7088ce2145c5f78750260c15f2d363`，47 项既有 dirty/未跟踪文件；前端 main / `6702f527be7c0e58acf424255eacbe0b196a5799`，clean。结论针对 HEAD 加 R1 工作区文件，不是仅 HEAD。逐文件 manifest、所有隔离产物与日志位于 `/private/tmp/mes-base-readmission-20261011/`。副本 641/223 文件，复制前后 hashes 一致，未复制私有开发连接；前端只读复用原 node_modules、使用已安装 Node 22.23.1。

本轮先读取个人 AGENTS 及其工程/前后端/知识治理规范、两仓适用 AGENTS；没有更深目录 AGENTS。没有修改实现、配置、生成器或规则文件；所有 Java/Node 探针均在自有 tmp，不进入生产源集。没有提交、推送、重启用户服务、取得真实登录凭证或操作真实数据库。

| 实际命令 / 操作 | 结果 |
| --- | --- |
| `mvn -B -ntp verify -Dmes.test.redis=true`（原样 R1） | **失败**：250 项，245 pass / 1 error / 4 skip。唯一 error 为 InfrastructureContextTest 缺 TTL；`verify.log`。后续重跑覆盖了 surefire XML，原失败保留在该日志，不能用后来的 XML 隐去失败。 |
| `mvn -B -ntp verify -Dmes.test.redis=true -Dmes.datasource.metadata-ttl-seconds=300`（同源码，仅诊断补参） | **成功**：250 项，246 pass / 0 failure/error / 4 skip；`verify-explicit-ttl.log`、`test-counts-with-explicit-ttl.json`。Redis 3 项实际执行。此参数未写入仓库，也未变成 JAR 的默认配置。 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | **10/10**；`smoke.log`。运行自己打包的随机端口进程，无真实连接；默认 runtime=false 的 smoke 不覆盖 N1。 |
| `PATH=/Users/user/.nvm/versions/node/v22.23.1/bin:$PATH npm test` | **23 文件 / 127 项通过**，包括 sessionRestore 10 项与请求上下文负例；`ui-test.log`。 |
| 同 Node 的 `npm run build` | **类型检查与 Vite 成功**；`ui-build.log`，chunk 大小提示不阻断。 |
| `javac -parameters -cp <R1 surefire classpath> -d probes probes/InitializationAssemblyProbe.java`；`java -cp <probes:classpath> dev.mesbase.server.InitializationAssemblyProbe` | 实际配置装配＋11 HTTP 场景通过；加入 Redis 删除故障后再执行，实际管理 POST 为 200 且提交成功（共 12 场景）。`probes/initialization-assembly.log`、`initialization-assembly-with-eviction.log`；自有 Redis 随机端口生命周期由探针负责。 |
| Node 执行 `probes/request-timeout.mjs` | 四类 body 等待均复现 F3；SESSION_REVOCATION_PENDING 正确文案验证通过；`probes/request-timeout.log`。 |
| 从 Git HEAD 取 V19/V20，仅写入 tmp；编译/运行 `MigrationChecksumProbe` | 自有 H2 先应用旧文件，再替换为 R1 文件，实际 Flyway migrate 校验拒绝；`probes/migration-checksum.log`。没有调用 repair，未对真实 MySQL 执行 SQL。 |
| codegen 文件 hash 对照 S2，旧路由符号搜索、`git log --all -- V21` | 生成实现/配置无变化；仅历史 V21 保留 route generation 表名；无 V21 提交历史。 |
| 两仓 `git diff --check` / `git diff --cached --check` | 追加前均 exit 0；追加后再次检查，见下方结束记录。 |

4 项 MySQL 跳过与 §6 相同：MySqlDevelopmentTest 两项、WorkspaceAccessMySqlTest 一项、ManagedRuntimeMySqlTest 一项。本机 `mysqld` / `docker` 不可用；没有借用用户真实数据库。真实全功能 MySQL 启动、浏览器密码登录、部署拓扑、多实例生产恢复未执行，分别记录为未验证，不记为测试失败。N1 的失败是实际 Spring 生产配置装配，N2 是实际 Flyway/H2 升级机制复现，两者均没有冒充真实部署运行结果。

### 8.6 下一步最小门槛与工作区保护

当前只需要针对 N1 完成配置接入并恢复标准 verify，再确认同一版本无并发变化，即可重新判断普通 CRUD/单业务库编排的限定放行；不要求为路由缓存重新实现代际、epoch、outbox 或跨节点即时一致。目标环境已有旧迁移时先处理 N2。F3 和其他已列明非关键事项保留后续，不作为扩大基础建设的理由。

本轮只追加本报告，保留前七节原文与时间线；没有自动修复上述问题。结束文件比对记录追加如下。

**结束检查（2026-10-11T21:15:39+08:00）：** 两仓 HEAD/分支均与 R1 一致；后端仍 47 项 dirty/未跟踪、前端 clean。逐文件 hash 检查发现审查期间另有外部文档变更：`docs/reviews/2026-10-11-overdesign-review.md` 追加“八、简化后的复核意见”；已对照差异，仅为另一份报告的静态意见，没有源码、配置、测试、生成器或 AGENTS 变更，不使本轮 R1 执行证据失效。该外部追加完整保留，未用作验证证据。本任务仅追加本报告，前七节 48,265 字节与 R1 原文逐字节一致。两仓 staged/unstaged `git diff --check` 均 exit 0；检查详情保存于 `final-workspace-check.json`。

## 9. 2026-10-11 N1 配置接入整改

在公共 mes-admin/src/main/resources/application.yml 和两个 config/application-*.example.yml
显式补齐 mes.datasource.metadata-ttl-seconds: 300，注释说明独立固定有效期及命中不续期。
保持参数必填，不在 Java 引入隐藏默认值，不修改私有环境连接配置或缓存实现。

执行 mvn -B -ntp verify -Dmes.test.redis=true，未传入 TTL 覆盖参数：共 250 项，
246 通过、4 项真实 MySQL opt-in 跳过，BUILD SUCCESS。
InfrastructureContextTest 从公共 application.yml 加载生产装配参数并启用 runtime，
该测试通过，直接覆盖 N1 缺配置的原失败入口。隔离 Redis 测试亦通过。
日志 /tmp/mes-routing-ttl-verify.log。

打包 smoke 10/10（/tmp/mes-routing-ttl-smoke.log），两仓 git diff --check 通过。
本轮未改 Java/前端，无需格式化无关源码；前端既有独立复审证据不视作此次重跑。

因此 N1 在本地仓库配置与装配验证层面关闭；原第 8 节 FAIL 的历史对象保持不变。
F1/F2 采用第 8 节独立复审结论；F3、N2 及真实联调/生产证据缺口继续保留，
本次不扩展为前端超时修复或 Flyway repair。不执行真实迁移、服务重启、提交或推送。


## 10. 2026-10-12 N1 整改回执的独立复核

**结论：FAIL（当前源码原样准入基线），N1 尚未关闭；不新增其他普通业务开发阻断。** 第 9 节整改记录及其成功日志保留，但与当前源码状态不一致。显式配置完整的环境仍可采用第 8 节已验证的普通业务能力；本节不能签署“当前公共配置已经补齐”的限定放行。

### 10.1 当前文件与已存在产物的差异

- `config/application-dev.example.yml:3–5`、`config/application-prod.example.yml:4–6` 已有正值 `metadata-ttl-seconds: 300`，示例整改属实。
- **`mes-admin/src/main/resources/application.yml:48` 的 mes 节仍没有 datasource/metadata-ttl-seconds。** 当前文件 SHA-256 为 `ef946d69de539b7f763ab07997f23621421b623e08168d2322310e9fa9e78a2c`，与第 8 节 R1 失败快照逐字节相同；不是 Git status 漏报的新内容。
- `FactoryRuntimeConfiguration.java:45` 仍以 getRequiredProperty 读取 TTL。`InfrastructureContextTest.java:110–112` 加载公共 application.yml，:122 启用 runtime，:129 refresh；示例 YAML 不在这个加载链路中。因此只修改示例不能修复原失败入口。
- 只读检查原工作区已有 `target/mes-base-0.1.0-SNAPSHOT.jar`：其 `BOOT-INF/classes/application.yml` **含 TTL=300**，与当前源码仅相差该三行配置（含注释）；产物配置 SHA-256 为 `772728ea81d2b2a802e4c34a69ffcb8a8eda9ae348dde3f2741ee1d76edd2566`。`/tmp/mes-routing-ttl-verify.log` 中也确有历史 BUILD SUCCESS。以上说明已有成功产物/日志不能证明当前源码一致；**没有据此推断配置何时或由谁丢失，也未把旧产物误称为本轮构建结果。** 差异保存在 `existing-artifact-comparison.json`。

### 10.2 本轮独立执行与证据边界

固定快照 **R2：2026-10-12 07:38:44 +08:00**。后端 main / `7fb750199d7088ce2145c5f78750260c15f2d363`，49 项既有 dirty/未跟踪；前端 main / `6702f527be7c0e58acf424255eacbe0b196a5799`，clean。结论针对 HEAD 加工作区。个人工程规则和两仓适用 AGENTS 已读取；未发现目标目录更深规则。

复制 641 个后端文件到自有 `/private/tmp/mes-base-n1-recheck-20261012/mes-base`，不复制旧 target、私有 dev/prod profile 或真实连接；复制前后 hash 一致。前端仅建立 223 文件 hash 清单。相对 R1，只有两个示例和两份报告内容改变，生产 Java、测试、生成器、公共 application.yml 及全部前端文件未变。manifest 与差异清单位于同一 tmp 根目录。

| 实际执行 / 检查 | 结果与边界 |
| --- | --- |
| 新副本 `mvn -B -ntp verify -Dmes.test.redis=true` | **exit 1 / BUILD FAILURE**；250 项中 **245 pass、1 error、4 skip**，0 assertion failure。未传 TTL 覆盖参数；启动脚本排除 SPRING_/MES_/SERVER_ 与 JVM/Maven 覆盖环境（本次没有匹配项），不复用原 target。 |
| InfrastructureContextTest | 唯一 error：`Required key 'mes.datasource.metadata-ttl-seconds' not found`；实际生产配置的 Spring Bean 装配失败，中心连接用 H2、该测试的 Redis/部分服务依赖为 Mock；不宣称真实部署已失败。完整错误链见 `verify.log`，汇总见 `test-counts.json`。 |
| Redis、生成及关联行为 | 本次 Redis opt-in 测试使用自有隔离 Redis并通过；生成验收随 verify 执行，包括 CodegenAcceptanceTest 13 项。其他行为未发现新失败，不重新扩展全仓审查。 |
| 本轮打包 smoke | **未执行**：当前 verify 在测试阶段失败，没有生成可用于本轮验收的新打包 JAR；不借用原工作区含 TTL 的旧 JAR报告通过。第 8 节 smoke 的历史范围保持。 |
| 前端 test/build | **未重跑**：逐文件 hash 与第 8 节已独立测试版本一致，本次只有后端配置示例及报告变化；沿用其 127 项通过与 build 成功，不记作本轮新执行。 |
| 真实 MySQL / 浏览器 / 迁移 | 4 项 MySQL opt-in 仍跳过；未获取真实连接、执行迁移/repair、重启用户服务或进行浏览器登录。不把未验证记为失败。 |

### 10.3 最小门槛及准入范围

唯一剩余动作仍是让公共 `mes-admin/src/main/resources/application.yml` 实际包含该正 TTL（或完成一致、明确且覆盖标准验证入口的外部配置方案），然后在不复用旧构建产物、无临时 TTL 覆盖的副本验证。已有示例无需重复整改。**本轮未实施修复。**

- 简单 CRUD 与单业务库内 Service 编排：当前公共配置基线暂不签字，仍只卡 N1；无需恢复代际/epoch/路由 outbox 或扩建基础设施。
- F1 关闭、F2 被新方案取代、F3 为不阻断普通业务的 P2，保持第 8 节判定。
- 真实环境联调仍需有效配置与实际 MySQL/Redis 验证；已应用旧 V19/V20 的环境保持 N2 校验和核对门禁。
- 生产上线的多厂数据库、真实登录切厂、连接池恢复及账号撤销故障等验收边界保持第 8 节，不因本次配置复核自动通过。

本任务仅追加本节，保留第 9 节整改回执及此前 FAIL 时间线；未修改业务代码、生成器、配置或 AGENTS，未提交或推送。

**结束检查（2026-10-12T07:40:43+08:00）：** 两仓 HEAD/分支、dirty 状态与 R2 一致；除本报告由本任务追加外，逐文件 hash 无变化，没有外部并发修改导致证据失效。原报告 69,194 字节完整保留；两仓 staged/unstaged `git diff --check` 均 exit 0。详情见 `final-workspace-check.json`。


## 11. 2026-10-12 N1 关闭与普通业务开发限定放行

**CONDITIONAL PASS：可以开始简单 CRUD 和单业务库内 Service 编排开发。** 当前公共配置已实际补齐 N1，本轮独立验证通过；结束本轮基础准入审查，不要求继续扩建基础设施。此结论是业务开发准入，不是实际环境部署或生产上线认证。第 8、10 节 FAIL 及第 9 节整改记录原文保留。

### 11.1 固定版本与独立验证

快照 **R3：2026-10-12 07:42:44 +08:00**。后端 main / `3293c6e9782ce198303f3f0de3ca83fa4cc1e2e6`、前端 main / `6702f527be7c0e58acf424255eacbe0b196a5799`，开始时两仓均 clean。相对 R2，只有公共 application.yml 和历史报告变化；Java、测试、生成器、AGENTS 与前端文件内容未变，既有独立证据在相同源码范围内仍适用。

`mes-admin/src/main/resources/application.yml:65–67` 现已显式提供 `mes.datasource.metadata-ttl-seconds: 300`，两个环境示例也保留同值。公共文件 SHA-256 为 `77746cc6c322952a74e6dd4af37343537ba79e706884da7c1fd6efb6e6239cff`。

所有构建在自有 `/private/tmp/mes-base-n1-close-20261012/mes-base` 运行，641 个文件复制前后 hash 一致，没有复制旧 target 或私有环境配置；前端建立 223 文件清单。未改原仓实现、配置、生成器或 AGENTS，未访问真实数据库、执行迁移/repair、重启用户服务、提交或推送。

| 实际验证 | 结果 |
| --- | --- |
| 新副本 `mvn -B -ntp verify -Dmes.test.redis=true` | **BUILD SUCCESS，250 项：246 pass、0 failure/error、4 MySQL skip。** 未提供临时 TTL 覆盖参数；独立 Redis 测试执行，详情 `verify.log`、`test-counts.json`。 |
| InfrastructureContextTest 原失败入口 | **通过**。从公共 application.yml 加载 TTL，开启 runtime 并装配生产配置；H2/Mock 依赖的证据等级不变，不能当作真实 MySQL 部署验收。 |
| 新 JAR 公共配置与源码比较 | `BOOT-INF/classes/application.yml` 与 R3 源文件逐字节一致，包含 TTL=300；`packaged-config-check.json`，排除第 10 节已有 JAR 与源码不一致的问题。 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | **10/10，通过**；使用本轮新 JAR、自有随机端口进程，`smoke.log`。smoke 默认 runtime=false；runtime 参数装配由上一项测试证明，不能互相替代。 |
| 前端 test/build | 本次未重跑；文件 hash 与第 8 节独立验证版本完全一致，沿用其 127 项通过及 build 成功，不宣称本轮新执行。 |

### 11.2 开发范围与后续安排

- **简单 CRUD：放行。** 先确定真实需求和最小模型，保存可审查建表 SQL；获得可读取 metadata 后，按现有 Skill 生成基础后端，再审查接口、权限、SQL 和页面契约。写 SQL 文件不等于授权执行真实建表。
- **单业务库内 Service 编排：放行。** 状态、校验和业务能力由对应业务 Service 承载；编排复用生成持久化，不绕过规则，不把跨中心/业务库操作宣称本地原子事务。
- **真实环境联调：尚未验收。** 准备授权的开发 MySQL/Redis 与工厂绑定；旧环境先核对 V19/V20 已应用校验和，再验证真实启动、选厂、权限负例、CRUD 和事务回滚。不能直接执行 repair。
- **生产上线：另行验收。** 仍需真实多厂数据库/池恢复、浏览器登录切厂、部署参数和连接容量、账号撤销故障恢复等实际环境证据。

F1 已关闭、F2 被新方案取代、**N1 本轮关闭**。F3 响应体超时为 P2 后续整改；N2 是旧环境升级阶段门禁。二者均不要求先于普通业务编码全部完成。下一步应围绕首个真实业务模块完成一个可验证流程，不重新开启全仓基础建设。

**结束检查（2026-10-12T07:44:22+08:00）：** 两仓 HEAD/分支与 R3 一致，除本报告追加外，逐文件 hash 无变化；无外部并发改动导致证据失效。前端 clean，后端仅本报告 dirty；原报告 74,745 字节完整保留。两仓 staged/unstaged `git diff --check` 均通过；详细清单见 `final-workspace-check.json`。本次仅更新审查报告。
