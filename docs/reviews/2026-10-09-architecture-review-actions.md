# MES Base 评审后续行动方案（P0 / P1 / P1）

> 原方案是历史只读建议。当前执行范围、纠正意见与整改结果以本文 §五为准：先完善 Base，生成器与改造后 UI 对齐留到业务接入阶段。

- 日期：2026-10-09
- 配套：`docs/reviews/2026-10-09-architecture-review.md`
- 性质：**行动方案与文件清单，全部只读勘察得出**。本文档未执行任何处置动作 —— 未轮换凭据、未改 `.gitignore`、未 `git rm --cached`、未重写历史、未修改源码、未编写测试代码。

---

## 一、P0：凭据与主密钥处置

### 1.1 已确认的事实（全部可复现）

| 项 | 实测结果 |
| --- | --- |
| 被跟踪的敏感文件 | `mes-admin/src/main/resources/application-dev.yml`、`application-prod.yml`（`git ls-files` 命中） |
| 忽略规则覆盖情况 | `.gitignore` 仅含 `target/ .idea/ *.iml .env .local/`，不含二者；`.git/info/exclude` 无相关规则 |
| 明文范围 | 外部 DB 地址（非本机/内网）、账号、**3 行非空 password**、**1 行非空 `datasource-key`**（32 字节 AES 主密钥） |
| 远端 | `origin = https://github.com/codeinsightlab/mes-base.git`；匿名访问返回 404，判为私有仓库 |
| 远端已含密钥 | `git branch -r --contains a16ce71` → `origin/main` 命中 |
| 仓库规模 | 总提交 **20**，`origin/main` **18**，单分支 `main`，**0 个 tag**；仅 5 个提交触及该文件 |
| 含明文的提交 | `8cb871f`（3 行 password）、`29eeec4`（1 行）、`2467430`（1 行）；`4891535`、`e292789` 干净（0 行） |
| 可用工具 | `git filter-repo` **未安装**；`bfg` **未安装** |

**判定：密钥已离开本机（推到 GitHub），必须按"已泄漏"处理。仅重写历史不够。**

### 1.2 处置顺序（严格按此顺序，否则会白做）

**第 0 步 —— 先止血，再清理。**

在后端侧确认并执行：
1. 轮换远程测试 MySQL 的所有账号密码（`mes_base_dev` / `mes_base_dev_business` / `mes_base_prod` / `mes_base_prod_business` 及共享同一实例的账号）。
2. 轮换 Redis 口令（`127.0.0.1:6379` 的 DB1/DB2 所用实例）。
3. 生成新的 `mes.runtime.datasource-key`（32 字节 Base64）。**这一步不能只换配置** —— 该 key 是 `mf_datasource.password_cipher` 的主密钥，换 key 必须同时用旧 key 解出、用新 key 重新加密所有已有密文，否则工厂业务库连接会全部解不开并抛 `RUNTIME_NOT_CONFIGURED`。
4. 因为轮换了 DB 密码，`mf_datasource` 中已存的密文**同时失效**（旧密文解出来的是旧密码）—— 所以第 3 步的重加密不是"可选优化"，是"必须一起做"。

> 关键点：**先轮换，后改配置**。如果先改配置再轮换，中间会有一段服务连不上；如果只改配置不轮换，等于没做。

**第 1 步 —— 把两个 profile 移出版本控制。**

```sh
cd /Users/user/work/lixin/mes-base
printf '\n# Real local profiles hold credentials. Never track them.\nmes-admin/src/main/resources/application-dev.yml\nmes-admin/src/main/resources/application-prod.yml\n' >> .gitignore
git rm --cached mes-admin/src/main/resources/application-dev.yml mes-admin/src/main/resources/application-prod.yml
```

同时把工作区里两个文件里已失效的旧值替换为新值（下一步的历史重写只处理历史，不处理工作区）。

**第 2 步 —— 决定历史是否重写。**

三个选项，按你的协作者规模选：

| 选项 | 做法 | 适用 | 代价 |
| --- | --- | --- | --- |
| **A. 重建基线（推荐）** | 仓库仅 20 个提交、全是 "init"、单分支、0 tag、无 tag 保护 —— 直接把当前（清理后）状态做成一个权威基线提交，用一个新仓库替换（或 `git checkout --orphan` + 单提交） | 没人协作、远端就是本人的 | 丢 20 个 "init" 的提交历史；但这些提交本身无信息量 |
| **B. filter-repo 重写** | `brew install git-filter-repo` 后 `git filter-repo --path <file> --invert-paths`（或 `--replace-text` 替换敏感行），再强推 | 需要保留提交历史 | 所有协作者必须重新 clone；远端需允许强推 |
| **C. 不重写** | 只做第 0/1 步，历史里留着旧值 | 旧值已轮换且确认无人能访问远端 | 旧密码/旧 key 永久留在仓库里；将来任何人 clone 都能看到当时的连接信息与 key 形态 |

选 A 或 B 时，请同步确认：`origin` 是否有其他协作者、是否有 CI 引用旧 commit、GitHub 上是否有 fork。

**第 3 步 —— 顺带修掉两个仓库模板里的真实账号。**

实测 `config/application-dev.example.yml` 与 `config/application-prod.example.yml` 的 `username` 是**非空字面量（长度 12 / 13）**，而非占位符（password 与 datasource-key 均为空值，未泄漏）。账号名敏感度低于密码，但作为示例模板仍应改为 `your-db-user` 之类的占位符。这两行在 `.gitignore` 所覆盖范围之外，需要单独提交修改。

> 未验证项：我无法确认模板里的账号是否与真实账号一致 —— 直接读取这两个 0600 文件的进程被本机沙箱中断（连续三次 SIGTERM），仅通过 `git show` 验证了历史版本。建议你自行比对一次。

### 1.3 防复发（建议一并做）

- 加一个**提交前钩子**：`git diff --cached --name-only | grep -E 'application-(dev|prod)\.yml$'` 命中即拒绝，同时扫描 `datasource-key:|password:\s*\S` 模式。仓库已有 `.githooks/` 目录，是现成的落点。
- 把"凭据只走环境注入"写成一条可检查的规则，避免下次再出现"文档说忽略了、实际没忽略"这种**声明与实践背离**（本次 P0 的根因就是这个背离，而不是有人故意提交密码）。
- 同步修正 `README.md` 第 9 行与第 43 行关于"Git 忽略 / 已取消Git跟踪"的表述 —— **目前这两处描述与事实相反**。

---

## 二、P1：分页契约选型与文件清单

### 2.1 实测的契约分布

**生产后端：只有一套，`TableDataInfo(rows/total)`** —— 18 个 Controller：

```
mes-common/.../common/core/controller/BaseController.java          (getDataTable 工厂)
mes-admin/.../view/PageResultMapper.java                          (rows/total 转换)
mes-admin/.../monitor/SysLogininforController.java
mes-admin/.../monitor/SysOperlogController.java
mes-admin/.../monitor/SystemOnlineController.java
mes-admin/.../system/ManagedDatasourceController.java
mes-admin/.../system/ManagedFactoryController.java
mes-admin/.../system/SysAutoCodePartController.java
mes-admin/.../system/SysAutoCodeRuleController.java
mes-admin/.../system/SysConfigController.java
mes-admin/.../system/SysDictDataController.java
mes-admin/.../system/SysDictTypeController.java
mes-admin/.../system/SysMessageController.java
mes-admin/.../system/SysNoticeController.java
mes-admin/.../system/SysPostController.java
mes-admin/.../system/SysRoleController.java
mes-admin/.../system/SysUserController.java
mes-admin/.../system/SystemFileController.java
mes-admin/.../system/SystemInboxController.java
mes-quartz/.../controller/SysJobController.java
mes-quartz/.../controller/SysJobLogController.java
```

**`items/offset/limit` 在后端没有任何实现**：全仓 `mes-*/src/main` 中 `items` 仅命中 `mes-generator/.../RuoyiTemplateRenderer.java:47`（生成 `PageVo(items,total,offset,limit)` 模板字符串）。

**前端消费方 27 个（`.rows`）**：

```
src/api/system/role.ts                        src/views/monitor/operlog/index.vue
src/components/FileUpload/index.vue           src/views/system/autocode/index.vue
src/components/PlatformWorkspace.vue          src/views/system/autocode/part.vue
src/components/userSelect/single.vue          src/views/system/config/index.vue
src/composables/useDruidMonitor.ts            src/views/system/datasource/index.vue
src/views/Files.vue                           src/views/system/dict/data.vue
src/views/Inbox.vue                           src/views/system/dict/index.vue
src/views/monitor/druid/index.vue             src/views/system/factory/index.vue
src/views/monitor/job/index.vue               src/views/system/message/index.vue
src/views/monitor/job/log.vue                 src/views/system/notice/index.vue
src/views/monitor/logininfor/index.vue        src/views/system/post/index.vue
src/views/monitor/online/index.vue            src/views/system/role/authUser.vue
                                              src/views/system/role/index.vue
                                              src/views/system/role/selectUser.vue
                                              src/views/system/user/index.vue
```

**前端消费方 4 个（`.items`）** —— 其中 2 个与分页无关：

```
src/components/CrudPage.vue      ← 分页契约（死壳，见下）
src/views/PlatformPage.vue       ← 分页契约（死页面，见下）
src/App.vue                      ← 与分页无关
src/lib/workspaceTabs.ts         ← 与分页无关
```

### 2.2 关键发现：`items` 链路整体不可达

| 文件 | 状态 | 证据 |
| --- | --- | --- |
| `src/views/PlatformPage.vue` | **不可达** | 不在 `router.ts` 静态路由中；`lib/dynamicRoutes.ts` 显式排除 `menu.path.startsWith('/platform/')` |
| `src/lib/platform.ts` | **不可达** | 仅被 `PlatformPage.vue` 引用，定义 users/roles/menus/dict-types/dict-data/configs/jobs/audit/login-logs/job-logs 全部指向 `/api/platform/{resource}` |
| `src/components/CrudPage.vue` | **不可达** | 仅被 `PlatformPage.vue` 引用 |
| `src/lib/crud.ts` | **仅服务于死壳** | 被 `CrudPage.vue` 与 `tests/crud.test.ts` / `tests/CrudPage.test.ts` 引用 |
| 后端 `/api/platform/**` | **不存在** | 实测仅有 `SystemSessionController`（2 个方法）与 `SystemFileController`（`/api/platform/files`）；`/api/platform/users`、`/roles`、`/jobs` 等无对应 Controller |

**这解释了一个此前的疑问**：`CrudPage` 不是"没人愿意用"，而是**它服务于一个已被删除的旧简化平台，页面不可达、后端不存在**。

### 2.3 生成器指向了这条死链路

| 位置 | 内容 |
| --- | --- |
| `mes-generator/.../RuoyiTemplateRenderer.java:47` | 生成 `public record XxxPageVo(List<XxxVo> items, long total, int offset, int limit)` |
| `mes-admin/src/main/resources/codegen/base/api.ts.vm` | 声明 `export interface Page { items: XxxVo[]; total: number; offset: number; limit: number }` |
| `mes-admin/src/main/resources/codegen/base/index.vue.vm` | `import CrudPage from '@/components/CrudPage.vue'` + `:api="api" :fields="fields"` |
| `mes-admin/src/main/resources/codegen/base/service.java.vm` | 生成 `XxxPageVo list(...)` |

生成产物**自身是自洽的**（后端 `PageVo(items)` + 前端 `items` 成对），所以按生成器走的新 CRUD 能跑通；但它与 27 个手写迁移页面的 `rows/total` **完全不通约**，并且复用的是那个已死的 `CrudPage`。**两条路各走一半，这正是必须在开启业务前定死的分叉点。**

### 2.4 两个选项与影响面

**选项 A —— 统一到生产的 `rows/total`（改动小，推荐）**

- 改 3 处生成器侧：`RuoyiTemplateRenderer.java:47`（`PageVo` → `TableDataInfo` 形态或保留 `TableDataInfo`）、`api.ts.vm`（`items` → `rows`）、`index.vue.vm`（不再 import 死 `CrudPage`，或指向一个新建的、以 `rows` 为契约的新壳）。
- 前端新增 1 个真正的统一壳（或把 `CrudPage.vue` 的契约从 `items` 改为 `rows` 并重新定位为"生产壳"，同时删除 `PlatformPage.vue` / `lib/platform.ts`）。
- 27 个手写页面**零改动**。
- 删除：`src/views/PlatformPage.vue`、`src/lib/platform.ts`、`tests/CrudPage.test.ts`（若保留壳则改为按 `rows` 重写）。

**选项 B —— 统一到 `items/offset/limit`（改动大）**

- 需要为 18 个 Controller 实施 `PageVo(items)`，逐个替换 `getDataTable(...)` / `PageResultMapper.map(...)`，并保留 `PageHelper` 的 `total`。
- 27 个前端文件全部要改 `.rows` → `.items`（且 `api/*.ts` 里目前签名是 `any`，改动会顺带触发类型补齐）。
- 好处是与生成器现状一致，坏处是**动到存量已验证的接口契约**，需要重跑系统响应契约测试（`SystemResponseHttpTest` 46 项 / `SystemResponseContractTest` 18 项）并同步前端 27 处。

**建议**：选 A。理由 —— 存量 18 个 Controller + 27 个前端页面是**已通过测试的既成事实**，改它们等于把"清理技术债"变成"重做已验证功能"；而生成器只涉及 4 个文件、且业务尚未开始，改动成本最低、风险最小。

### 2.5 无论选哪个，都需要一并确认

- `PageResultMapper.map()` 依赖 `new PageInfo<>(rows).getTotal()` 从 PageHelper 的 `Page` 里取总数 —— 修改契约时必须保留这个"先取总数再转换 rows"的顺序，否则分页 total 会变成当前页条数。这是现有实现里已经处理对的一个细节，不要回退。
- 前端 `lib/request.ts` 目前是**单一路径、无多结构兜底**的干净封装，两个选项都应保持这一点，不要引入 `rows ?? items ?? data` 之类的猜测。

---

## 三、P1：SyncCoordinator 多节点验收方案（设计，不含测试代码）

> 按你的既有边界（审查任务不补测试用例），以下仅为**验收方案设计**，未编写任何测试代码。是否落地由你决定。

### 3.1 现状

`mes-framework/.../sync/SyncCoordinator.java` 已实现 AGENTS 8.1 要求的机制：
`ensure → find → recover(stale 时才生效) → claim(条件更新) → changed(n==1)`，`progress/succeed/fail` 均校验 `factory_id + sync_key + run_token + RUNNING`，`assertOwner` 供执行器轮次间自检。

`PersistenceDeepAuditTest`(10) 覆盖了持久化深审，但**未覆盖 `SyncCoordinator` 的多节点语义**；4 个 skipped 是 opt-in 真实 MySQL 用例。AGENTS 8.1 的"验收必须覆盖"清单目前**没有可复现证据**。

### 3.2 需要覆盖的 6 个场景（对应 AGENTS 8.1 验收条款）

| # | 场景 | 断言要点 | 证据形态 |
| --- | --- | --- | --- |
| 1 | 同流竞争：两个执行者同时 `claim(DEV, "k")` | 恰好一个拿到 `Run`，另一个收到 `SYNC_RUNNING`（409），且**不得等待/重试** | 两进程/两线程 + DB 中 `run_token` 只存在一个 |
| 2 | 不同工厂互不阻塞 | `claim(DEV,"k")` 与 `claim(PROD,"k")` 可并发成功 | 两行 `mes_master_sync` 各自 RUNNING |
| 3 | 旧令牌更新被拒绝 | 用已失效的 `run_token` 调 `progress/succeed/fail` | 全部抛 `SYNC_EXECUTION_LOST`，且 DB 状态未被旧任务覆盖 |
| 4 | 崩溃超时恢复 | 模拟持有者不再续期，另一个执行者 `claim` 同流 | 旧记录被标为 `FAILED`，新 `run_token` 生成，新执行者成功进入 |
| 5 | 恢复后旧执行者停止 | 场景 4 之后，旧持有者调用 `assertOwner` | 抛 `SYNC_EXECUTION_LOST`，旧执行者不再推进同步水位 |
| 6 | 各入口共用抢占 | 手工入口与 Quartz 入口对同一 `factory_id + sync_key` | 二者互斥（不能同时 RUNNING），且都不依赖 JVM 锁 |

### 3.3 方案要点（避免做成"通过但没验证"）

- **必须是真实 MySQL、两个独立连接或独立进程**：同进程内两个线程共享 `PlatformPersistence` 无法证明跨节点语义（H2 亦可，但需注意 H2 的隔离级别与 MySQL 不同，建议以真实 MySQL 为主证据）。
- **数据库观测优先于返回值断言**：场景 1 与 3 的结论必须从 `mes_master_sync` 表实际行读出来（`status` / `run_token` / `updated_at`），而不是只看方法有没有抛异常。
- **场景 4 需要可控时钟或小租约**：`lease` 最短 1 秒（`claim` 已校验 `1..86400`），用 `Duration.ofSeconds(1)` + 等待即可，无需注入时钟。
- **场景 5 是当前最容易漏的一条**：`assertOwner` 存在但没有证据表明调用方真的在批次边界调用它。这属于**实现侧的调用点问题**，不只是测试问题 —— 建议在方案里一并确认真实执行器会在每批处理前调用。
- 这些用例属于 `@Tag("mysql")` / opt-in 类别，应提供**一条显式命令**（如 `mvn -B -ntp verify -Dgroups=mysql`）并写入 README，避免继续以 "4 skipped" 状态存在。

### 3.4 与 P1 其他事项的依赖

场景 1、2 需要两个真实工厂 + 两个独立业务库（`mf_factory` / `mf_datasource` 至少要有一条 ENABLED 记录且已 register）。而 ADR-004 明确"真实多厂部署和生产验收未执行"，因此这条验收同时也是**首次真实多厂装配的验收**，建议与"新装基线 Schema 清理"（报告 §四 第 3 项）一起做，避免在带 14 张 `phase1_legacy_*` 死表的库上留下证据。

---

## 四、执行顺序建议汇总

```
第 0 步  轮换 DB / Redis 凭据 + 换 datasource-key 并重加密 mf_datasource     ← 唯一紧急项
第 1 步  .gitignore 补齐 + git rm --cached 两个 profile + 改 example 模板账号
第 2 步  决定历史重写方案（A 重建基线 / B filter-repo / C 不重写）
第 3 步  分页契约选型（建议 A：统一到 rows/total）+ 删除不可达 PlatformPage/lib/platform
第 4 步  清理 30 个空目录 + 仓库根死骨架 src/main/java/dev/mesbase/**
第 5 步  修 README/ADR 与现状不符的表述（ERP 三工厂、Git 忽略声明）
第 6 步  合并 V1+V2 为干净基线 Schema，消除 phase1_legacy_* 死表
第 7 步  补 SyncCoordinator 多节点验收（与第 6 步一起，首次真实多厂装配）
第 8 步  做一次权威提交并打 tag（如 base-0.1）—— 到这一步"Base"才真正存在
```

第 0–2 步是 P0，建议在你开始任何业务开发之前完成。第 3 步决定业务开发的写法，必须在**第一个新页面诞生之前**定死，否则会成为既成事实。


## 五、2026-10-09 复核与整改（当前有效）

### 5.1 范围与结论

用户当前决定：先完善 Base 基础，接入业务时再集中改造生成器，使之贴合已改造 UI。不是本轮将迁移页面统一重写为生成壳，也不是删除生成器的全部资产。原评审与本方案前四节保留为当时的建议，冲突处以本节为准。

本轮读取两份评审、后端/前端 AGENTS、个人工程规则，检查两个工作区状态后按源码追踪；已有 ERP 移除、双入口持久化、事务与监控改动保留。没有修改真实数据库、轮换账号或密钥、改变 Flyway 历史、提交/推送/打 tag、重启用户服务。

总体判断：凭据防复发、契约收敛方向、默认回归、工程门禁及当前文档入口合理；把分数、空目录/行数、未提交状态、统一壳或 Shared 作为 Base 的技术准入条件不合理。不能从“默认构建跳过真实测试”推导“没有测试或从未验证”。

### 5.2 合理项及本轮处置

| 原评审项 | 当前证据与处置 | 状态 |
| --- | --- | --- |
| B5：私有 profile 被跟踪 | 原索引和本地 origin/main 快照均含 dev/prod，安全计数分别为 3/4 个非空 password、各 1 个非空 datasource-key；未打印值。`.gitignore` 补规则，`git rm --cached` 仅移出索引，磁盘文件保留 | 本地整改完成；远端/历史风险未关闭 |
| B5：仓库可见性 | 登录态 `gh api repos/codeinsightlab/mes-base` 返回 visibility=private；私有不证明无人接触，缓存 origin/main 不是本轮实时远端分支内容检查 | 私有已验证；访问者/历史传播未知 |
| 防复发 | 新增 `check-private-config.py`，提交前检查索引，推送检查目标提交树，CI 检查仓库配置；拒绝私有路径、非空凭据字面量，接受空值/环境占位，不打印值；Git hooks 使用 IDEA 格式化/检查 Java，部分暂存拒绝自动重暂存 | 本地安装完成；不扫描全部历史，也不保证覆盖所有 Secret 命名 |
| 示例账号 | 两个 config profile 的 platform/codegen 用户名统一为 your-db-user；空密码/空主密钥保留，主密钥注释改为环境独立生成与已有密文迁移要求 | 完成；原账号名是否真实不作为泄漏结论 |
| B8：PostgreSQL 死依赖 | 移除 admin runtime 的 PostgreSQL driver/Flyway 插件；保留 MySQL 和 test-only H2；同步改 smoke，断言 MySQL 保留且 PostgreSQL 不入包 | 完成 |
| B8：Java FQN/通配导入 | 10 个生产 Java 文件改为显式 import + 简单类名，包含 38 处 EndpointAccess 和同文件其他外部类型；IDEA 格式化；权限值、接口与流程保持 | 完成；不全仓重排无关文件 |
| F2：旧平台 UI 残留 | router 未注册 PlatformPage，dynamicRoutes 排除 /platform；旧 /api/platform/users 等后端不存在。删除 PlatformPage.vue/lib/platform.ts 及只验证旧 DTO 的测试；保留 crud.ts 纯格式/字段白名单测试和 CrudPage 组件测试 | 完成 |
| B4/B6：同步默认回归缺口 | 新增 SyncCoordinatorTest 8 项控制流程单测：非法输入/禁用厂、活动租约拒绝、条件抢占失败、新令牌、旧令牌 progress/succeed/fail 拒绝、旧执行者停止、负数进度拒绝 | 完成；Mock 不证明 SQL、多节点或真实 DB |
| B6/F5：无 CI | 两仓新增工作流：后端私有配置检查 + Maven verify + 隔离 packaged smoke；前端 npm ci/lint/test/build；现有 lint 格式问题仅按 ESLint 修复 5 个文件 | 配置完成；尚未 push，远端 CI 未运行；IDEA 格式由本地 hooks 保障 |
| B7：当前/历史混杂 | 根 README 修正取消跟踪与风险表述；architecture/README、next-stage-plan 顶部增加当前阶段；旧 server-package review 加历史标记；本轮保留原评审正文并追加复核 | 完成，不篡改历史验收 |

### 5.3 不采纳或待用户审查的独立清单

| 编号 | 原建议/判断 | 不合理或不足之处 | 建议裁定 |
| --- | --- | --- | --- |
| U01 | 业务前必须立刻统一生成器和所有页面 | 与用户阶段决定冲突；迁移页面有真实 rows/total 链路，生成器是后续接入资产 | 延后到业务接入；届时优先沿用 rows/total/pageNum/pageSize 和现有 UI，不加猜测兜底 |
| U02 | CrudPage 不可达所以是死壳，应直接删除/重建 | 页面不可达不等于组件不可复用；模板仍导入 CrudPage，删除会破坏生成资产 | 本轮保留 CrudPage/crud.ts/有效测试；只删失效平台入口 |
| U03 | SyncCoordinator 无测试、只有设计 | ManagedRuntimeMySqlTest 已存在双 JVM、竞争、异厂、progress 旧令牌、恢复和 assertOwner；旧迁移报告还记录了历史 MySQL 结果 | 改为本轮没有真实复跑证据，补默认控制流回归，不重造已有测试 |
| U04 | 同步协调验收必须先有两业务库并 register | 协调操作通过中心 PlatformPersistence/MasterSyncMapper，claim 校验启用厂，不解析业务 runtime；测试的独立厂也可无数据源 | 协调中心库验收与真实多业务库路由验收分别记录 |
| U05 | mvn verify -Dgroups=mysql 是现成真实验收入口 | 当前测试用 System property + Assumptions，无 mysql Tag；该命令不会自动启用四个用例 | 使用实际 opt-in 属性，见 5.4；不要误称已运行 |
| U06 | 直接合并/改写已应用 V1+V2，Flyway 后续无法清理 | 已有 dev/prod 测试部署与非空历史迁移；V2/V3 真正读取 legacy 表迁数据/权限。改文件会改 checksum，且新 migration 或已有受控脚本可以清理 | 拒绝直接改旧 migration；如需新装基线，另审“升级兼容/空归档处理/独立基线”方案 |
| U07 | 20 个 init 历史没价值，默认重建仓库/强推 | 提交信息不能代表内容、回滚与引用价值；轮换与传播处置也不能靠重写历史替代 | 历史保留/重写/重建由用户决定，不自动执行 |
| U08 | 必须先轮换全部共享账号，再改配置/重加密 | 未证明所有账号共用凭据；DB 密码与加密主密钥是两个变化。重加密旧密码不会更新成新密码；无通用零中断顺序 | 单独核对受影响账号、保存可恢复密文并协调切换；只轮换相关凭据 |
| U09 | 凭据必须改为只走环境注入 | 当前已授权直接私有 profile；要求不入仓库合理，强制更换配置路径缺少收益证据 | 保留当前私有 profile 工作流，部署时另选受控注入方案 |
| U10 | 未提交说明 Base 不存在，必须打 tag 才存在 | 可回滚/可复现基线是交付治理，dirty 不是架构缺陷；打 tag 不证明验收 | 完成验收后另行授权提交/标签；当前不提交已有无关修改 |
| U11 | 清 30 个空目录是阻断项 | Git 不跟踪空目录；根骨架不在 reactor，未证明运行影响 | 本地卫生低优先级，不列 Base 准入门槛，不危险清理 |
| U12 | 拆 mes-core、拆 249 行“上帝服务”、拆所有大页面/改注入风格 | 依赖隔离可加强，但行数/包跨模块/风格混用不足以证明职责错误；迁移规则优先 COPY/PORT | 保留为有证据的后续设计议题，不在本轮批量重构 |
| U13 | 文件接口 AUTHENTICATED 读起来像无权限，改 AUTHORIZED | owner/scope/resource 是服务端资源授权，个人附件未必需要后台菜单权限；增加 blanket permission 会改变有效空角色能力 | 保留现有授权模型；补说明与负例优于凭阅读印象改语义 |
| U14 | permitAll 要扫描所有 Servlet/Filter/WebSocket Bean 白名单 | 非 MVC 入口需审查合理；笼统 Bean 白名单可能误拒框架过滤器，也不能完整证明容器入口覆盖 | 有具体新入口/暴露面再设计登记与负例，不现在预建一套框架 |
| U15 | 缺 Prettier、覆盖率或本机安全护栏导致构建失败应扣架构分 | 已有 ESLint/IDEA；覆盖率数字不代表隔离质量；本机护栏不是 CI 源码失败。本轮 test/build 可复现 | 用实际规则与关键行为验收，主观评分不作为准入条件 |
| U16 | Shared 保持 DESIGNED，是当前必须补的地基 | 用户只采用分库模式，Shared 不在当前产品边界 | 不实现 Shared；保留历史 ADR 设计追溯 |
| U17 | physical 内存去重需加强跨节点保证 | 节点本地限制确实不能作为全局保证；尚无当前多节点真实部署证明 | 合理但待设计：审查中心 metadata/物理身份唯一性、别名与并发登记；不凭内存表直接宣称全局隔离 |
| U18 | 登录限流/密码策略全部未实现 | 系统登录已有密码 retry/lock-time、验证码、PasswordRules；OpenAPI 有独立速率限制。不能等同系统全链路限流、TLS/SSO已交付 | 按实际登录入口列缺口，TLS/暴露环境在首次对外部署前验收，不整体重建安全模型 |
| U19 | 删除/替换 OwnerMapperRegistration 为纯 MapperScan | 当前用标准 ClassPathMapperScanner，还执行重复名/包冲突/归属守卫；并非逐 Mapper 手工 Bean | 无等效守卫证据不替换，只按生产两入口验收 |
| U20 | 默认 false 说明正常启动就是空壳，应直接启用全部装配 | application.yml 默认 profile 为 dev，私有 dev/prod 明确 enabled=true；default 隔离空装配供测试/smoke，强改会触及真实连接 | 保留显式隔离启动，文档区分装配存活与系统业务就绪 |

### 5.4 真实验收与远程操作的边界

已有真实测试入口（本轮未执行）：

```sh
mvn -B -ntp -pl mes-admin -am test \
  -Dtest=ManagedRuntimeMySqlTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmes.test.managed.config=/absolute/path/to/private-isolated-profile.yml \
  -Dmes.test.managed.factory=EXPLICIT_TEST_FACTORY
```

此用例不是只读：包含管理更新、临时工厂、状态表写入、恢复与清理，且当前源码限制中心库名为 mes_base_dev/mes_base_prod。执行前必须确认隔离配置、既有 factory/数据源与影响范围。默认 4 个 skipped 不能当作真实验证通过。已有用例可补 stale succeed/fail 的真实 DB 负例与抢占后状态观测；手工/Quartz 共用协调及批次停机必须待真实同步执行器接入后验收，当前不能拿 heartbeat 伪装业务同步。

凭据：本地取消跟踪不是撤回远端内容，也不是轮换。账号、Redis、主密钥及 mf_datasource 密文的协调切换属于独立远程处置；不得自动轮换共享实例所有账号。历史策略、真实 DB/多厂验收、新装 Schema 策略、提交/tag/推送留给用户审查，不因这轮静态与本地测试宣称生产准入完成。

### 5.5 本轮验证收据

- Java21/标准 Maven：`mvn -B -ntp test` 与 `mvn -B -ntp verify` 均 BUILD SUCCESS。全 reactor 190 项：framework 15（新增 8）、generator 3、admin 172；186 通过，4 个真实 MySQL opt-in 跳过，0 失败/错误。原评审的 172 是 admin 汇总，不是全 reactor 总数。
- `scripts/format-java` 及 `--check`：本轮 11 个 Java 文件通过（10 个生产文件 + 1 个测试文件）。首次 import 简化出现内部类型匹配错误，已修复后完整复跑；不改变权限枚举或 HTTP 契约。生成器/模板最终无本轮改动。
- `/usr/bin/python3 scripts/smoke-packaged.py`：6 通过、0 失败。JAR 不含 PostgreSQL driver/Flyway 插件；隔离 loopback 启动，未使用用户私有 dev/prod 配置，不代表真实 DB 就绪。
- Node22.23.1：`npm run lint` 通过，`npm test` 17 文件/81 项通过，`npm run build` 通过。初次 lint 41 个格式错误已按仓库规则修复；既有 deep 弃用与大 bundle 提示保留。UI 仅删除不可达入口/失效用例、更新测试请求路径和格式，不改真实业务交互；无新浏览器视觉验收。
- 配置 guard 5 个索引/提交树输入场景通过：空/环境占位允许，字面量、properties 密码、带非空 fallback 的占位、私有 profile 路径拒绝；不打印值。`git check-ignore` 与 `stat` 确认本地 profile 忽略且0600；索引取消跟踪，未提交。`scripts/install-git-hooks` 已安装，原 core.hooksPath 本已指向缺失的 .githooks。
- 两仓 `git diff --check` 通过。远端 CI 未执行、无真实 MySQL/Redis/多厂复跑、无凭据轮换/重加密、无 Schema 写入、无用户服务重启、无提交/推送/部署。

回滚本轮代码/配置仅需逐文件撤回本轮 patch；保留原用户 ERP/双入口等未提交改动。若撤回取消跟踪，使用原磁盘 profile 恢复索引会重新引入凭据风险，不建议作为常规回滚。不能使用整仓 reset/checkout 清除用户已有工作。

### 5.6 2026-10-09 用户授权后的 Git 历史清理

用户重新决定清除历史中的测试密码和主密钥，本节更新此前“未重写历史”的状态。已从本地历史及 Codex 差异快照移除两个私有 profile 的历史版本；GitHub main 经 force-with-lease 更新并通过 API 核验，最终为 `43e6532`。本地 main 为 `261b0af`，保留原本地20/远端18个提交，本地额外2个提交和暂存改动未推送。两个本地 profile 的内容保持，暂存条目保持；没有轮换凭据、修改业务源码或重启服务。本文仅追加记录，未重新暂存。

核验扫描全部本地 Git blob（包括不可达对象）：1,294 个对象，5 个实际历史凭据值均无命中；私有文件路径在历史及各引用树中不存在。旧 reflog/不可达对象已清理，临时镜像和敏感替换规则已删除。中途 YAML 匹配曾误识别密码策略块，最终按单行标量规则重新清理并修正远端；最新树相对原版仅删除两个私有 profile，密码策略保持。收据位于 `target/validation/git-history-credential-cleanup.json`。GitHub 不可达缓存及其他人的克隆未核实，不宣称全平台物理擦除。


### 5.7 2026-10-09 基础建设阶段判断与业务准入

本次为数据源/事务链路静态复查及阶段建议，不改业务代码，不运行服务或真实数据库测试，不代替全产品验收。建议停止无边界的基础扩建，冻结中心库 + Factory 独立业务库的架构，开始一个业务试点；真实双厂验收完成前，不宣称基础层已生产验收。

- 真实验收缺口：前轮默认测试中四个真实 MySQL opt-in 用例跳过。首个业务试点须覆盖双厂同号数据隔离、Scope/权限负例、回滚、事务内切厂拒绝、双向跨库事务拒绝、连接失效、切厂后 UI 旧响应不回填。Fixture/CI 不能代替真实证据。
- 业务 Schema 升级：中心库有 Flyway 入口；ManagedDatasourceService.resolve 未检查业务 Schema 版本，连接 revision 不等于表结构版本。随首批业务表提供版本化 DDL、逐厂执行记录及版本不匹配处理，不先建设通用迁移平台。
- 中心库热路径：PersistenceStatementGuard → FactoryBusinessDataSource.verify → ManagedDatasourceService.resolve；resolver 每次读取中心库工厂和数据源记录，开销随业务 SQL 数量增长，中心库故障会阻断校验。属于源码事实，无压测结论；按试点实测定向优化，保留授权/版本失效保证，不直接引入无失效策略的长期缓存。
- 跨厂阻塞：resolve 是整个 Service 的 synchronized 方法，锁内包含建池及真实连接探测，某厂首次加载慢时可能阻塞其他厂 resolver 和同锁的列表/详情。属可定位性能瑕疵，未压测；多厂并发试点前定向处理。RuntimeRegistry 亦在同步区关闭池，生命周期调整需一起审查。
- 密钥运维：cipher 使用单一环境密钥，未见 key-id 多版本轮换协议。正式上线前明确备份、恢复及密文迁移操作；历史清理不等于密钥轮换。
- 生成器/UI 对齐随业务接入完成；旧功能优先 Source Product COPY/PORT，新增标准 CRUD 再使用生成器。格式/FQN 存量和文档漂移随触及处理，不作为无限扩建 Base 的理由。

先选依赖少的现有基础资料业务，交付 Schema → Service/Mapper → 权限/Factory → API → 当前 CrudPage → 双厂真实验收闭环，再扩展工单和复杂流程。本建议不提前恢复生产分析、共享业务库或外部数据库框架。证据：PersistenceConfiguration、ManagedDatasourceService.resolve、FactoryBusinessDataSource.verify、PersistenceStatementGuard、RuntimeRegistry、DatasourcePasswordCipher。

### 2026-10-10 用户决定移除本地钩子与后端 GitHub 工作流

按用户要求删除后端 .githooks、.github，以及不再使用的 scripts/install-git-hooks、scripts/java-git-hook.py；清除本仓库 core.hooksPath。AGENTS、README 和 Java code style 文档删除安装/自动检查要求。历史整改记录保留为当时证据，不再代表现行自动门禁。IDEA formatter、手动测试与独立 check-private-config.py 检查工具保留。

验证：core.hooksPath 无配置，默认 .git/hooks 仅有 .sample 文件，无活动提交/推送钩子；当前规则与开发文档无失效脚本引用，git diff --check 通过。未修改业务代码，未操作暂存区，未提交或推送；远端已存在的工作流需本轮删除提交推送后才移除。

## 六、2026-10-18 全项目代码质量与架构复审

> 范围更正：本节实际采用核心调用链抽查，不能作为用户后续明确要求的全仓审查完成证据。下方§七补充两仓全文件清单、全量结构检查及逐模块复审；§六的具体发现和当时验证结果保留。

### 6.1 范围、版本与结论

用户确认本轮范围是全项目代码质量与架构，不限于 Quality V2。审查对象为后端与相邻 mes-base-ui 的当前工作树：后端 main / `01415b78ccdbb5e6457362e09f479e35d491d504`，前端 `94167e0c25fd9cb4dfcac5ecef369f6e8b715546`，两仓均有用户已有未提交修改。因此下述结果属于 **HEAD 加当前工作树**，不能只凭上述提交重现。

本轮按入口抽查 HTTP 分类/授权、两套持久化入口与事务、同步执行权、生产报工/数量/完工、质量申报/处置/放行、库存维护/动作/仓储单据、E10 查询与同步、前端请求与原请求恢复，并运行全量默认测试和前端构建。不是逐文件穷尽审计，不包含依赖漏洞情报扫描、真实部署安全测试或性能压测。

**结论：现有单体分层和单一生产核心方向成立，适合继续有边界迭代；本轮发现 1 项 P1、4 项 P2，不能据默认测试全绿宣布生产数据安全与容量验收完成。优先补齐基本单位的引用保护，再处理引用创建竞态和质量投影增长边界。** 未在本轮检查范围确认 P0，不代表全项目不存在 P0。

本节是本次全项目复审的事实与行动记录；原 §一至§五保留历史。2026-10-17 的生产核心专题及 2026-10-18 的质量 V2 专题仍保留各自证据范围，本节不将历史报告中的问题直接计作当前缺陷。

### 6.2 按严重程度排序的发现

#### QA-01 / P1：已有库存的物料仍可直接更换基本单位，余额会被重新解释

- **源码事实：** `MaterialController.update` → `MaterialServiceImpl.updateById` 直接把输入 `unitId` 写到已有物料（`mes-business/.../basic/service/impl/MaterialServiceImpl.java:146–165`）。`BasicDataRules.validate(MaterialEntity)` 只确认新单位存在、编码不重复等条件，没有比较旧单位或检查库存/动作/领料引用（同目录 `BasicDataRules.java:73–82`）。
- **下游事实：** `LocalStockService.change` 直接将现有余额与数量增量相加，并从**当前物料**取得单位写入新流水（`.../wm/service/LocalStockService.java:332–349`）；库存余额本身没有独立冻结单位。`identity` 又要求当前物料单位等于原投产快照单位（同文件约398行）。
- **触发与影响推理：** 一个已有 100 kg 余额和 kg 历史流水的物料改成“件”后，余额数字仍是100；下一笔动作以“件”记账并继续加减。原冻结 kg 投产的撤销还可能被单位身份校验拒绝。改单位不是普通名称编辑，现有路径缺少可证明的数量换算或停旧启新契约。
- **最小建议：** 在同一受控事务内锁定物料，已有库存或相关业务引用时拒绝直接更换基本单位；无引用时才允许。物料编辑/同步入口统一规则，保留历史单位和数量事实，不自动换算或修改旧流水。单位删除也应一起审查历史引用：当前 `deletingUnitMeasure` 只检查现有物料引用。
- **验证边界：** 当前调用链的静态证据；未修改真实物料、未在真实库复现。建议增加“余额/旧动作存在后改单位拒绝、无引用允许、并发库存创建与改单位互斥、旧投产仍可按冻结身份撤销”回归。

#### QA-02 / P2：仓储明细新增与零余额库存删除没有共同的引用锁

- **源码事实：** `StockDocumentService.putLine` 锁的是主单，随后 `validateIdentity` 普通查询库存、物料及单位，再插入明细（`.../wm/service/StockDocumentService.java:166–189,300–315`）。它没有取得库存行锁。`WmInventoryServiceImpl.deleteByIds` 锁库存后检查动作、投入和仓储明细引用，再删除零余额来源（`.../wm/service/impl/WmInventoryServiceImpl.java:203–220`，`StockReferenceRules.java:69–77`）。V38 的明细 `stock_id` 只有索引，没有外键；本轮未见后续迁移为该关系补外键。
- **可达竞态推理：** A 新建草稿明细并读到零余额来源 → B 锁库存、看不到尚未插入/提交的明细，删除并提交 → A 插入明细并提交。两边各自事务成功仍会留下悬空 `stock_id`；后续过账因查不到来源而失败。顺序执行时的删除保护不能覆盖这一时序。
- **最小建议：** 在明细引用创建/转移前与删除方共享库存锁，并统一主单/库存锁序；引用校验在锁后进行。沿用现有 Mapper 与锁机制，不另建数据访问体系。覆盖新增、换来源、删除并发场景。
- **验证边界：** 静态事务/锁序推演，未运行真实 MySQL 双连接复现；影响首先是草稿引用完整性，不据此宣称已过账余额被破坏。

#### QA-03 / P2：质量历史上限会阻断整个执行的当前数量和后续处理

- **源码事实：** `QualityTreatmentService.executionEvents` 读取某 Execution 的全部处理事件，超过5000即报冲突；`declarations` 读取最多1001条最新申报，超过1000即拒绝，之后才过滤 VOID（`.../quality/service/QualityTreatmentService.java:98–113`）。`nodeTotals`、`summary`、`details`、`releaseBasis` 复用这些读取；`ProductionQuantityService.load` 又通过 `ProductionDefectQuantityQuery` 依赖它们。
- **影响推理：** 阈值不是“历史列表下一页”，达到后会影响当前数量查询、报工校验、质量处置和放行/完工依据。已经 VOID 的申报以及已完成/已纠正的事件仍消耗预算，用户不能通过普通历史分页绕开。新增事件后的 `details`/数量校验也可能使触及上限的事务回滚，形成业务容量天花板。
- **最小建议：** 当前数量用数据库有界聚合/必要事实投影，历史明细独立分页；放行依据保持可追溯且确定，不因优化遗漏事实。明确执行生命周期容量预算和专用错误。保留失败关闭，禁止截断历史后继续算数量。
- **验证边界：** 静态阈值与调用链确认；未制造1000条申报或5000事件的真实样本。此项为旧生产专题风险的当前源码复核，非声称新发现或已修复。

#### QA-04 / P2：前端请求超时只覆盖响应头，不覆盖响应体读取

- **源码事实：** mes-base-ui `src/lib/request.ts:28–30` 在 `await fetch` 返回后的 `finally` 清除计时器；成功的 `response.text()`、错误的 `response.json()` 和 `response.blob()` 都在其后。响应体读完才做的上下文校验正确，但不能限制读体等待时长。
- **本轮隔离契约探针：** 用当前 TypeScript 源码转译后执行，替身 fetch 立即返回响应头，响应体延迟1300ms，`timeoutMs=1000`。实测1302ms后成功返回，`aborted=false`。这是替身契约证据，不是真实浏览器/代理故障复现。
- **影响：** 服务器/代理发出响应头后卡住响应体，普通列表、命令结果或下载仍可能持续等待，页面 busy 也无法依靠统一超时结束。外部调用结果恢复依赖的失败边界因此不完整。
- **最小建议：** 普通 JSON/blob 请求将截止时间覆盖到响应体读取完成，并安全归一化读体中断错误；SSE 明确使用独立的长连接生命周期和调用方取消。补“头快体慢、错误体卡住、切上下文后读体失败”的契约测试。

#### QA-05 / P2：生产数量读模型仍经质量写服务回环依赖

- **源码事实：** `ProductionQuantityService` → `ProductionDefectQuantityQuery` → `ExecutionQualityReferenceQueryImpl.nodeTotals` → `QualityTreatmentService.nodeTotals`；而 `QualityTreatmentService` 构造器又注入 `@Lazy ProductionQuantityService` 和 `@Lazy ExecutionPathService`（`.../quality/service/QualityTreatmentService.java:63–70`，`ExecutionQualityReferenceQueryImpl.java:14–39`）。接口隔离没有消除实现依赖环。
- **影响判断：** 当前测试证明应用可装配，不能把它写成启动失败。风险在于质量写编排、数量读取、历史读取和路径引用相互牵动，容量策略或事实来源变化会扩大修改与测试装配范围；QA-03 的历史读取限制也由该环传播到生产核心。
- **最小建议：** 将无副作用的质量当前事实投影与质量动作编排分清，让数量读取依赖投影，动作服务依赖相同投影和数量校验。复用现有 Mapper/DTO，不为消除环增加仅转发的 Facade，不另建第二套数量公式。
- **验证边界：** 静态依赖确认；本轮默认装配及业务测试通过，尚未实施解耦。

### 6.3 已成立的边界与额外治理项

| 范围 | 本轮确认的实现事实及限制 |
| --- | --- |
| HTTP 权限 | `EndpointCatalog` 对方法要求显式分类；`EndpointScopePolicy` 按完整 URL 段判断平台/共享/业务，`FoundationAccessInterceptor` 不把 X-Factory-Id 当权限。默认测试通过不等于部署下全部角色矩阵复验。 |
| 持久化/事务 | `OwnedTransactionManager` 拒绝跨数据库 owner，业务路由在 begin/访问/commit 校验，受控中心只读窗口仍有明确例外。未据此宣布两个库原子提交或真实多厂验收完成。 |
| LOCAL/ERP | 新业务使用 `BusinessSourceRules`、FactoryIntegrationSnapshot；WarehouseIntegration 的提交冻结选择，历史查询/撤销检查原来源与绑定。E10 同步先读远端，再进入单候选写事务；不把 SDK/替身等同真实 ERP 生效。 |
| 生产事实 | 管理员报工已有 UUID、payload/actor 指纹与重放；LocalStockService 已集中余额动作并保存不可变流水；完工、放行、仓储接收已有正式入口。旧报告“没有管理员幂等/没有完工入口”等不能继续直接引用为当前结论。 |
| 前端范围 | 通用请求在头和体读取后检查 token/factory/revision；会话恢复由服务端校验，不按浏览器旧 expiresAt 授权。原请求恢复使用按账号/工厂/资源隔离的 sessionStorage；本轮未做浏览器端到端恢复验收。 |
| SQL/生成 | 抽查业务查询使用绑定参数、固定排序和 PageHelper；MBG XML 的 `${criterion.condition}`/`${orderByClause}` 本身不等于注入，必须连同服务端构造入口判断。本轮不凭搜索命中即报注入。 |

额外治理项：`QualityTreatmentModels` 的 Action/State/Attribution 仍为无显式 value/description/from/toMap 的枚举，Service/XML 使用 `name()/valueOf()`，与当前 AGENTS 的枚举契约不一致。现有值未证明发生错误，因此列为规范债，不与 QA-01 的计量正确性风险等量排序；触及时保持存量编码，显式适配，不擅自迁移事实。

`ProductionDefectErpGateway` 当前实现只是本地申报确认，注释明确没有物理报废/返工/库存副作用；调用在本地事务内并不能据此判定存在远端长事务。将来接真实命令时必须重新设计冻结意图、UNKNOWN 和事务外调用，不能直接替换实现后沿用现在的调用位置。

不把无 CI/本地钩子作为本轮新增架构缺陷：§五记录了用户明确移除它们的决定。本轮没有重建工作流或扩大 E10 操作范围，也没有要求引入微服务、通用补偿或第二套生产核心。

### 6.4 本轮验证收据与未验证范围

| 实际运行 | 结果 | 证据级别 |
| --- | --- | --- |
| Java21 / Maven3.9.10，`mvn -B -ntp verify` | BUILD SUCCESS；各模块汇总共741项，696通过、45跳过，0失败/0错误；耗时1分55秒 | 本地构建、单元/Mock/Fixture/测试内集成；跳过项不算通过 |
| Node v22.23.1，`npm test` | 63文件、380测试通过 | 前端单元/组件/契约替身 |
| 同版本 Node，`npm run build` | vue-tsc与Vite production构建通过；存在大chunk提示 | 类型检查/构建，不是部署/性能验收 |
| `/usr/bin/python3 scripts/check-private-config.py` | selected tree检查通过 | 索引中受检查路径的私有配置字面量规则；不是全历史/全工作树秘密扫描，不证明凭据已轮换 |
| QA-04临时探针 | 1000ms超时下，1302ms读体成功且未abort | 当前源码转译、隔离fake fetch |
| `git diff --check` | 通过 | 当前差异空白检查 |

原始命令日志在 `/private/tmp/mes-base-architecture-review-maven.log`、`/private/tmp/mes-base-architecture-review-ui-test.log`、`/private/tmp/mes-base-architecture-review-ui-build.log`，属于本地临时证据，可能被系统清理。

未执行：真实数据库读写、SIMPLE_B绑定核对、正式Flyway迁移、真实ERP读取/写入、浏览器UI验收、真实MySQL并发复现、多节点/压测、打包启动smoke、依赖漏洞扫描、提交/推送/部署。默认测试可能使用测试自身的临时服务/Fixture；没有启停用户服务。45个跳过项包括显式opt-in真实库类测试，不将本轮默认结果替代其历史/真实验收。

### 6.5 建议的最小整改顺序与本轮文档回执

1. QA-01：锁住已有引用物料的计量身份，覆盖编辑/同步及单位删除；先隔离回归，再在核实后的任务目标库验收，不修改历史数量。
2. QA-02：统一仓储引用创建与删除锁协议，用双事务屏障复现并回归；不只补前端禁删提示。
3. QA-03：分离当前聚合与历史明细预算，联动 QA-05 收敛无副作用投影；用阈值前后和大量VOID/纠正事件证明数量及恢复行为。
4. QA-04：完善请求体超时生命周期，保留原请求身份恢复和SSE取消契约。

本轮仅在本文件追加“六、2026-10-18 全项目代码质量与架构复审”，保存当前发现、旧结论差异、验证结果和后续范围；未修改业务代码、配置、迁移、权限或用户已有工作。以上整改均为建议，尚未实施。

## 七、2026-10-18 按用户要求补充全仓审查

### 7.1 冻结范围与覆盖方式

用户明确要求全仓审查后，重新执行两仓 `git ls-files -co --exclude-standard` 去重，读取每个仍存在文件的内容/字节、类型和SHA-256，建立[逐文件清单](evidence/2026-10-18-whole-repo-audit/file-inventory.csv)。快照包括已有未提交修改和未忽略新文件；不只看HEAD或diff。详细计数与静态结果见[机器摘要](evidence/2026-10-18-whole-repo-audit/summary.json)。

| 类别 | mes-base | mes-base-ui | 实施的检查 |
| --- | ---: | ---: | --- |
| Java生产代码 | 1073 | — | 全文件结构/风险规则扫描，包与类名、Core依赖、HTTP访问分类声明、事务命名及自调用候选；按模块复核真实入口及写入边界 |
| 前端src文件 | — | 299 | 全文件内容扫描，API调用、精度转换、HTML写入、异步/生命周期线索；全仓ESLint，结合共享组件与领域页面复核 |
| 生产资源/模板 | 116 | — | XML解析、Mapper namespace/语句ID、动态SQL来源、生成模板事务与分页/写入契约 |
| Flyway SQL | 79 | — | business39、platform1、system39；按实际中心库合并namespace检查重复版本，复核引用/不可变事实及历史迁移边界 |
| 测试目录文件 | 204 | 66 | 后端203个Java测试及1个资源文件；前端包括辅助资源，实际测试后缀文件共63个（含src中的测试）；核对默认验证、opt-in跳过与缺失负例 |
| 构建/配置/其他 | 43 | 16 | 依赖与模块装配、启动/部署配置、产物隔离；历史可视化等非生产内容只作归属/内容规则检查 |
| 工具/规则 | 19 | — | 生成与格式/凭据检查脚本、启动脚本、隔离smoke及适用规则 |
| 文档/归档 | 145 | — | 索引、历史证据与现行规则归属，防止沿用已过时结论；不把文档结论当源码证明 |
| 合计 | **1679** | **381** | **2060文件，315173行，15969573字节** |

清单是**全仓范围覆盖**，不是“每行代码都由人工穷尽证明正确”：`content_scan`明确区分`structural-rules`与`inventory-only`；自动风险命中仅用于导航，必须复核调用链后才列缺陷。重复生成模型/Example/Mapper按全量结构检查与生成契约复核，不伪称每个getter/setter已人工逐行验收。二进制、文档归档及可视化产物不按生产Java/HTTP规则误报。manifest冻结在本节及证据文件生成之前，不递归把报告本身计入覆盖数量。

不纳入源文件清单：Git对象、被忽略的依赖/构建输出、运行数据与日志（例如node_modules、target、dist、.local）；被删除路径单独由git status识别，不当作现存源码。私有profile不复制进报告；仅为核实下述密钥问题做本地值比较。未审查上游产品仓库及第三方依赖全部源码，也未联网执行CVE扫描；它们不属于本仓源码审查结果。

### 7.2 逐模块结论及源码覆盖重点

| 模块 | 当前复核的架构边界 | 结论/相关问题 |
| --- | --- | --- |
| mes-common（104 Java） | Core Factory选择/授权类型、分页、实体协议、Redis会话索引、详情缓存、密码策略、富文本/Excel与文件工具 | Core禁用依赖规则无命中；分页20/100有界；富文本有服务端清洗、Excel导出有公式前缀处理；公共工具不自动等于可增长导入预算完善。QA-09关联Redis/DictUtils |
| mes-system（129 Java） | 用户/角色/菜单/部门/岗位/字典/配置/消息/附件、OpenAPI客户端及授权、自动编码、撤销outbox | **QA-07/08事务缺口、QA-09缓存生命周期问题**；消息个人读取固定接收人，附件owner/resource及公告权限有校验；自动编码生成使用DB规则行锁，不能因存在反射工具就判任意代码执行 |
| mes-framework（101 Java） | 两入口事务与租约、缓存、认证、HTTP/错误清理、OpenAPI独立安全链、同步协调、SSE、个人表格偏好 | 具名事务/固定owner与作用域守卫存在；OpenAPI有独立凭据版本与接口授权，表格偏好实际路由在`/api/factory`，不能按其Java包名误报共享范围冲突；**QA-10是消费者没有传令牌，不能由协调器自身测试通过推出全部执行器安全** |
| mes-quartz（20 Java） | 任务目标白名单、DB抢占/原令牌人工恢复、提交后调度、任务日志 | 未开放任意反射Job，未配置Factory执行器失败关闭；多节点调度与本地消息通知限制沿用已有专题/用户阶段决定，不追加为本轮强制扩容任务 |
| mes-generator（22 Java） | JDBC metadata、配置参数、模板、MBG、格式导入、生成覆盖/回滚及菜单SQL | 路径归一化/软链接及生成标记保护存在；模板仍复制**QA-10**。全限定类型字符串在模板中经JavaSourceImports转换，不能把模板文本命中直接算Java导入违规 |
| mes-business（601 Java） | 基础资料、设备点检、工单/工艺/路径/派工、正式投产/报工/质量/放行/交接/接收/完工、仓储单据、来源与同步 | §六QA-01至05保留；新增**QA-10/11**。Execution删除与换工艺已检查派工，不能继续引用旧报告的派工孤儿缺口；ERP写与本地事务拆开，冻结身份/UNKNOWN不应被审查建议改为盲目重试 |
| mes-admin（96 Java） | 全HTTP装配与授权Catalog、数据源托管/迁移、E10 Query/Read/逐候选同步、协议View/错误与日志、管理CLI | **QA-06源码中存在实际dev密钥**；HTTP handler文件的Mapping/EndpointAccess数量扫描无缺项候选（SafeErrorController的RequestMapping单独处理），不是通过计数证明全部权限语义正确 |
| UI（299 src文件） | 全API目录/路由、会话与切厂、统一CRUD/主子单据、目录选择器、原请求恢复、生产/仓储/质量及平台页面、上传/富文本/部署模板 | 通用body读取后的迟到响应守卫存在；源码复核排除把`Number(offset)`等分页转换误报成ID精度丢失；保留**QA-04**，新增**QA-12全仓lint失败**。只看测试/build会遗漏这一结果 |
| 79迁移+116资源/模板 | 中心platform+system与业务各自版本、当前Schema演进与生成SQL/索引 | 两个数据库迁移集合均未见重复版本；93份XML可解析，其中84份Mapper XML；无重复语句ID或Java全限定类名候选。可解析不等于真实MySQL已执行；不改写已发布迁移 |
| 构建/脚本/测试与历史资产 | Maven模块/依赖、用户服务所有权、隔离产物、secret检查实际边界、UI lint/test/build与归档 | 新跑packaged smoke10项通过；新跑lint失败；默认验证收据沿用同一会话且生产源未变化的结果。24个生产Java文件存在通配符import，作为风格规范债记录，不冒充运行故障 |

### 7.3 全仓复审新增发现

#### QA-06 / P1：实际dev数据源加密密钥硬编码在已提交Java源码

**事实：** `mes-admin/src/main/java/dev/mesbase/server/managed/DatasourcePasswordCipher.java:20–24` 的调试`main`包含可Base64解码为32字节的密钥构造常量，并有解密后向标准输出打印的调用。安全探针只记录布尔结果：该常量存在于HEAD，**等于当前application-dev.yml的数据源密钥；不等于当前application-prod.yml中的值**。未执行解密，不记录/展示密钥或解密结果。

**影响：** 获取源码者取得了dev数据源密文的解密材料；源码、密文与加密权威不能再视为隔离。未证明prod密钥泄漏，也未证明仓库公开，故不笼统列为“全部生产凭据已泄漏”。`check-private-config.py`只查选定配置格式及路径，之前通过不能排除Java硬编码。

**建议：** 优先删除调试入口/常量并轮换已匹配环境的密钥，受控重加密已有托管凭据，复核历史暴露范围；扩展源文件secret防复发检查。仅删除当前源码不能撤销旧密钥。以上需要单独整改，本轮不改密钥、不重新加密数据库、不重写Git历史。

#### QA-07 / P1：实际用户导入重载无事务，类内写入可部分提交后报失败

**入口与证据：** `SysUserController.importData:85–91`调用四参数`importUser`；`mes-system/.../SysUserServiceImpl.java:539–585`的该重载没有@Transactional，只有已被弃用且直接抛错的三参数重载有注解。四参数内调用`this.insertUser/this.updateUser`不会经过事务代理。更新路径先删/插角色、岗位，再UPDATE账号；若包含status/password，`revocations.enqueue`明确要求当前事务，缺少事务时抛错；外层catch再把它报告成导入失败。

**隔离探针：** 当前编译类经Spring TransactionInterceptor代理，Mapper替身确认`updateUser`已调用，随后真实SessionRevocationService前置校验失败；`accountWritten=true, transactionActive=false, transactionStarts=0`。这证明实际调用顺序与事务缺失；替身布尔值不是“真实数据库已写坏”的证据。真实MyBatis无外层事务时，各调用可独立提交，故账号及关系发生变化但接口报错、outbox未同事务记录的风险成立。新建用户中后续关系失败也可能留下部分对象。

**建议：** 先明确导入整批原子还是逐行提交，再把每个原子用例放进真正经过代理的具名事务；不能只给被self调用的方法再加注解。错误响应明确已提交范围，账号版本/outbox与账号更新共事务。补“中间行失败、关联写失败、启停用户触发outbox失败”的回归。导入本身也没有行数预算，不能让上传体积上限代替Excel行数及逐行BCrypt的工作量上限。

#### QA-08 / P1：角色数据权限保存失败可留下部分权限变更

**事实：** `SysRoleController.dataScope:128–135`调用`SysRoleServiceImpl.authDataScope:310–319`，Controller、Service类和该入口均无事务。该方法先`updateRole`，再删旧`sys_role_dept`，然后self调用带@Transactional的`insertRoleDept`；后者才验证每个新部门存在。部门无效或批插失败时，前两步没有共同回滚边界。

**隔离探针：** Spring代理调用无效部门样本，实际先调用权限更新和旧关系删除，然后抛ServiceException；`scopeWritten=true, oldDepartmentsDeleted=true, transactionStarts=0`。未改真实授权。权限配置部分生效属于正确性风险，不能靠HTTP最终返回失败弥补。

**建议：** 数据权限替换使用单个masterTransactionManager事务，写前验证全部关系，确保更新、删除、插入整体回滚。保留当前scope/kind不可改与平台/工厂权限边界，不扩大授权模型。

#### QA-09 / P1：中心配置/字典在提交前发布永久缓存，回滚后可能保留未提交值

**事实：** `SysConfigServiceImpl.insertConfig:128–135/updateConfig:145–155`在事务方法内部直接写Redis。`SysDictDataServiceImpl`新增、修改、删除以及`SysDictTypeServiceImpl`更新也在提交前调用DictUtils。`RedisCache.setCacheObject(key,value)`没有TTL，DictUtils使用的正是该重载；这些入口没有afterCommit屏障。

**隔离探针：** 对真实SysConfigServiceImpl建立事务代理，Mapper/Redis与事务管理器使用替身，令commit显式失败；在抛出异常前已经观察到缓存发布：`cachePublishedBeforeFailedCommit=true`。这证明调用先后，尚非真实Redis/MySQL故障注入。另有确定性失效遗漏：字典类型重命名只写新类型cache，没有删除旧类型key；原类型已预热时仍可长期读到旧列表。

**影响：** 回滚不能撤销Redis值，其他请求可读取数据库从未提交的配置/字典；无TTL时不会自动收敛。配置消费者包含公共注册开关等，因此优先于普通文案缓存。批量字典删除中后续失败也会让缓存与整体回滚结果分离。

**建议：** 复用现有提交后副作用边界，提交后统一失效旧/新key，读取回填与写事务隔离；设置明确失败/恢复边界。不能把业务库缓存已有的afterCommit保护误认为中心配置/字典也自动具备。

#### QA-10 / P2：九个现存同步writer及生成模板未携带run token

**事实：** Material、MaterialCategory、SafetyStock、UnitMeasure、Workshop、Workstation、DvSubject、Operation、Routing九个Service的`sync`在claim后只把`scope+inputs`传给upsert；writer既不接收Run，也无处理边界/提交前assertOwner。`mes-generator/src/main/resources/codegen/base/service-impl.java.vm:143–244`仍生成相同模式。对比当前Warehouse/Inventory/WorkOrder writer已经接收Run并在提交前检查，可确认不是统一基础设施自动补齐。

**影响推理：** 写事务因锁等待超过5分钟租约后被新执行者恢复抢占，旧writer仍可能继续提交，再在外层progress时才发现失权，出现数据已提交但执行权校验失败。协调表自身能拒绝旧token不代表业务写被隔离。直接Service调用upsert也缺相应执行权前置。

**当前边界：** 上述九类生产QueryPort实现目前只有显式失败的Unconfigured adapter，不能声称本环境已发生双执行者写入；因此定为P2实现债，接真实同步执行器前必须处理。`generateSync:false`只阻止后续生成，不禁用已有同步代码。

**建议：** writer和模板统一显式携带对应Run、核对factory/syncKey，按批次及commit边界验证；保留DB互斥/超时恢复，不换成JVM锁。补“旧执行者在writer中暂停→恢复抢占→旧writer拒绝提交”的真实双连接测试。

#### QA-11 / P2：工作站删除保护漏掉不可变报工事实

**事实：** `WorkstationServiceImpl.deleteByIds:152–164` → `BasicDataRules.deletingWorkstation` → `EquipmentReferenceRules.deletingWorkstations:45–53`仅检查equipment引用。`ProductionReportService.supplement`校验并写入`workstation_id`，V25正式报工表保存该引用；未见对应外键或工作站删除时的报工引用检查。

**触发与影响：** 无equipment引用但已有报工事实的工作站仍可被物理删除，历史账本留下无法解析的工作站ID。不是要求删除报工事实，也不能用“ID字段还在”证明引用完整性。

**建议：** 工作站删除检查生产事实，并让引用创建与删除共用锁协议；已使用对象保留/停用，不能删除或改写历史事实。补“存在历史报工拒绝删、无引用允许、报工/删除竞争”回归。本轮为源码证据，未操作真实工作站。

#### QA-12 / P2：当前前端全仓lint门禁不通过

**实测：** Node v22.23.1运行`npm run lint`，退出1：**6955 errors、228 warnings，涉及98个文件**；规则主要是@stylistic/缩进/间距及Vue模板格式，另含ColumnSettings的props mutation等。并非6955个业务缺陷；`vue/no-deprecated-filter`对模板中联合类型断言也可能误判，不据此宣称Vue运行失败。

**影响：** 本轮此前测试和构建通过仍成立，但“项目质量检查全部通过”不成立；当前lint无法直接作为无噪声的增量门槛。没有拿缺少CI作缺陷，也没有违背用户移除工作流的决定。

**建议：** 独立格式治理任务中区分行为规则、可自动修复格式和规则误报，建立可复现基线；保护用户脏改动。本轮不运行--fix、不批量格式化。

### 7.4 当前风险台账、证据与顺序

本轮合并§六与§七后共**12项：P1五项（QA-01、06、07、08、09），P2七项（QA-02、03、04、05、10、11、12）**。QA-05是架构维护风险，不是已经证实的启动故障。没有用低风险格式问题稀释密钥、事务和计量风险。

2026-10-18 用户要求将规划细化为实施方案：阶段任务、优先级、依赖、验收和第一阶段五项 P1 的代码/数据/环境范围统一维护在[下一阶段方案](../development/next-stage-plan.md#2026-10-18-全仓审查后的分阶段实施方案)。本台账保留问题与证据权威入口，不重复维护实施清单；五项目前均待实施，本次只更新文档。

建议顺序：先处理QA-06匹配环境密钥与防复发；再处理QA-07/08账号和授权原子性、QA-09提交后缓存、QA-01计量身份；随后补QA-02/11引用锁与事实引用保护、QA-10真实writer执行权；最后按实际规模治理QA-03/05投影及QA-04请求超时，QA-12单独做格式/规则治理。无微服务、通用补偿或生产核心重写建议。

| 验证 | 当前结果/层级 |
| --- | --- |
| Maven verify / UI test+build | 沿用同一会话§6.4的741项/380项与构建结果；此次补审未改生产源码，不为报告变更重复构建。45个跳过项仍未验证 |
| 三项Spring代理探针 | [脱敏输出](evidence/2026-10-18-whole-repo-audit/probe-results.txt)；真实当前编译业务类+Spring代理+Mapper/Redis/事务替身。探针不是完整HTTP/MySQL验收 |
| 私有配置核对 | 只比较硬编码密钥与本地dev/prod值，保存布尔值；未输出明文或解密结果，未写库 |
| 全仓XML/包/注解/迁移检查 | 93 XML解析通过，84 Mapper XML；无重复Java类、Mapper语句ID、无名@Transactional候选或数据库迁移版本冲突；发现两个无事务self调用候选并人工复核 |
| 新跑packaged smoke | `/usr/bin/python3 scripts/smoke-packaged.py`：10通过、0失败。脚本使用显式隔离配置、随机测试端口，不加载开发连接；未启停用户服务 |
| 新跑UI lint | 失败，6955错误/228警告，原始日志`/private/tmp/mes-whole-audit-ui-lint.log` |

探针源码/编译class和完整替身日志留在`/private/tmp/mes-whole-audit-probe/`，可能被系统清理；持久证据只保留必要脱敏结果。静态扫描输入清单与规则摘要可逐文件核对，不将暂存证据路径当发布产物。

未执行真实数据库写入、ERP访问、权限变更、密钥轮换、迁移、部署或用户服务重启；未新增测试到生产目录。安全修复及业务整改尚未实施。默认测试绿、XML可解析、源码扫描覆盖都不能替代真实多厂/真实MySQL竞态与完整浏览器验收。

### 7.5 文档变更回执

- 本文件：追加§七全仓覆盖、逐模块结论、七项新增发现、证据与顺序；§六增加范围更正，保留历史。
- `evidence/2026-10-18-whole-repo-audit/file-inventory.csv`：2060行冻结文件索引，含路径、类别、内容hash及扫描层级。
- 同目录`summary.json`：范围、计数、静态检查、验证收据和脱敏密钥比较结果。
- 同目录`probe-results.txt`：三项隔离探针的必要脱敏输出与证据等级。

未修改业务代码、生产配置、生成模板、迁移或已有用户改动。上述报告与清单是审查证据，不是整改完成或发布批准。
