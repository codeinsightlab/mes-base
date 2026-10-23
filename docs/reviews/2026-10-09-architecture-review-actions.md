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


## 八、2026-10-18 P1 阶段 A 整改

### 8.1 授权、基线与只读复核矩阵

本次用户单独授权五项 P1 的源码/测试整改，覆盖先前“只规划”边界；明确不实施七项 P2、不改 SyncCoordinator、不轮换实际密钥、不修改真实托管密文、不重写 Git 历史、不提交/推送/部署。开始时后端 HEAD 为 `01415b78ccdbb5e6457362e09f479e35d491d504`，分支 `main`；已有质量 V2、性能脚本及审查/规划文档改动。修改前保存工作树和暂存区差异，逐文件限定本任务范围。工作期间检测到外部提交使 HEAD 变为 `a793e0b7de5bc6f938b8aacbb7b9624ef0ac62c6`，其中包含原质量改动及部分阶段 A 中间内容；Agent 未执行项目暂存/提交操作，也没有回退该提交。按原 HEAD、禁用 rename 检测后的差异核对，28 项原有非本任务差异保持一致。

| 问题 | 当前源码复核 | 需要修正或补充的判断 | 最小实施范围 |
| --- | --- | --- | --- |
| QA-06 | 调试 main 包含真实 dev 主密钥及解密输出，事实成立 | 未证明 prod 同密钥；同密钥还保护 OAPI 配置；删常量不能关闭暴露事件 | 删除调试入口，保留 AES-GCM API/格式；扩展源码扫描与合成负例；轮换仅给操作方案 |
| QA-07 | HTTP 调四参数 importUser，无外层事务，this 写入绕过代理，事实成立 | 循环继续处理错误行、成功行保留、最后汇总失败；前端无逐行结构化结果。先前规划的整批原子建议不适用本次保契约整改 | 逐行经过现有 Spring Service 代理；账号、关系和撤销 outbox 同事务；保留字符串/AjaxResult 格式，明确已提交范围 |
| QA-08 | authDataScope 无事务，先更新/删除后验证部门，事实成立 | 部门为中心共享身份，没有部门 Factory 字段，不能新造工厂过滤；事务之外还需同角色互斥 | 中心事务、角色行锁、写前部门集合校验和专用字段更新；关联重叠编辑/删除入口共用锁 |
| QA-09 | 三类 Service 在提交前写永久缓存，改名漏旧键，事实成立 | afterCommit 单独不能解决 Redis 失败/旧值回填；现有路由缓存决定不适用于本缓存 | 提交后失效、有限 TTL、新键版本、事务内不回填；账号安全开关直接 DB |
| QA-01 | 编辑和 upsert 均可换单位，事实成立 | Material 是 Factory 独立业务库身份，不是跨厂共享中心物料；计划引用和安全库存也有数量语义 | 复用生成 Mapper 行锁；冻结已有数量/历史/引用的单位；单位删除保护；保留普通字段编辑及合法撤销 |

证据等级：STATIC 为调用链；UNIT 为无外部服务规则测试；MOCK 包括 H2、Spring 代理、故障替身及 Fixture；MYSQL/REDIS 必须分别有真实引擎/状态；HTTP 只计真实运行服务请求。MockMvc 不记作真实 HTTP。

### 8.2 实施结果与关键设计

| 问题 | 修改文件与职责 | 本轮实施结果 | 证据入口 | 关闭状态 |
| --- | --- | --- | --- | --- |
| QA-06 | `mes-admin/.../server/managed/DatasourcePasswordCipher.java`；`scripts/check-private-config.py`；`scripts/tests/test_private_config.py`；`P1CipherTest` | 移除 main、密钥常量与输出。正常加解密格式不变；生产 Java 的构造字面量、明显 Secret 变量和跨行构造纳入扫描。仅报告路径/行号，不回显值 | Python 合成负例、运行随机密钥 API 回归、工作树扫描 | 源码部分已实施；运维轮换待授权，事件未关闭 |
| QA-07 | `mes-system/.../service/impl/SysUserServiceImpl.java`；`P1CorrectnessTest` | ObjectProvider 获取真实 ISysUserService 代理后调用 insertUser/updateUser，沿用 masterTransactionManager。单行失败回滚，其他成功行保留；账户写入影响行数异常拒绝；提交后撤销失败标为“已提交、待自动重试”，不伪称回滚。单次最多 200 行，BCrypt 在行写事务外执行 | 关联/用户/Outbox 写后故障与最终状态、正常新增/更新、中间行失败及重复导入、已提交待撤销测试 | 实施完成，MYSQL/真实 HTTP 尚未验收 |
| QA-08 | `SysRoleServiceImpl.java`、既有 `SysRoleMapper.java`；`P1CorrectnessTest` | authDataScope 使用具名中心事务；SELECT 主角色 FOR UPDATE 后校验类型/不可变范围和部门集合，拒绝不存在/已删除/重复部门。专用 updateDataScope 只写数据范围字段，避免 SysRole 默认布尔值覆盖其他元数据；普通角色更新、状态更新和删除先取同一角色锁，批删按 ID 排序 | 写后故障回滚、原 scope/集合保留、并发整体替换；既有平台/工厂权限回归 | 实施完成，MYSQL 锁及真实 HTTP 尚未验收 |
| QA-09 | `CenterSettingsCache.java`、`DictUtils.java`、`DictDataLookup.java`、既有字典查询接口、三个配置/字典 Service；`P1CorrectnessTest`、`P1SettingsRedisTest` | 复用 CommitActions；新值只由事务外读取回填。写入提交后失效旧/新键，回滚/提交失败不动缓存。加载/重置改为失效后懒加载；Excel 正反向字典转换通过 common 的最小查询端口复用既有字典 Service，未命中/过期后仍可加载，不依赖永久预热。无额外 JVM 本地副本；不改路由/会话缓存 | 回滚、注入 commit 失败、批量故障、改键/改类型、真实 Redis 过期和失效故障 | 实施完成，MYSQL 组合验收尚缺 |
| QA-01 | `MaterialUnitRules.java`、`BasicDataRules.java`、`MaterialServiceImpl.java`、`UnitMeasureServiceImpl.java`、`MaterialIssueRules.java`；`P1MaterialUnitTest`；两项既有 Fixture 适配 | 编辑/同步先锁物料再读当前身份；同步按主键排序锁已有行。余额含零余额、动作、仓储明细、用料、领料、工艺产品、工单、独立 Execution 历史及安全库存引用阻止换单位。单位锁与引用创建共享；删除覆盖直接历史单位引用。单位删除使用 READ_COMMITTED；不改历史数值/单位、来源、冻结 Execution 或撤销规则 | 实际生成 SQL、逐类引用负例、同步旁路、非计量编辑、库存创建并发；既有生产/仓储/质量测试 | 实施完成，MYSQL 并发与真实服务闭环尚缺 |

**导入协议：** 成功返回原字符串成功消息；包含失败行仍抛 ServiceException，保留现有响应形状，消息明确其他成功行已提交。新用户才设置本次初始密码，更新不擅自重置密码；状态/密码更新继续复用既有账号版本与撤销 outbox。行数上限是请求工作量保护，不代表已完成 200 行响应时间 SLA 验收；Excel 解析仍受现有上传限制，不宣称改造成流式导入。

**缓存一致性：** 新逻辑键为 `committed-v1:` + 原 key，再沿用 SystemCacheNamespace 的中心库命名空间。旧永久键不再读，提交后同时清理旧键。普通配置/字典采用最多 30 秒的单条缓存寿命，命中不续期；Redis 失效失败和并发旧读迟到回填允许短暂陈旧，不能宣称线性一致。该窗口从发布旧值计时，不承诺从 DB 提交瞬间起严格 30 秒内收敛。日志记录失败类型；缓存读/填失败回到数据库，DB 失败继续传播。所有 `sys.account.*` 账号开关直接读取 DB，不因 Redis 旧值开放注册或关闭验证码；事务内读不使用/发布共享缓存。旧节点必须完成升级才适用该协议。

**计量锁协议：** 物料更新与既有库存、工单/工艺、用料引用创建共享物料主键行锁；领料明细和安全库存新增补齐同一物料锁。单位采用生成 Mapper 同值 UPDATE 锁定；顺序为已有业务链锁 → 物料 → 单位，单位删除只锁单位并检查引用，不反向锁物料。库存已存在就禁止单位变更，因此动作/投产依赖已有库存/用料时不另加第二套计量锁。业务库隔离继续由 Factory 数据源负责，不增加表内 Factory 条件。生产数量权威及 SyncCoordinator 未修改。

### 8.3 安全轮换操作方案（仅方案，未执行）

1. **冻结范围：** 由运维核实实际使用暴露主密钥的环境及中心库配置；目前仅已确认 dev 匹配，prod 不推定受影响。盘点 `mf_datasource.password_cipher`、`mf_factory.oapi_config_cipher` 全部消费者、版本和连接池/缓存；不按 SIMPLE_B 业务库名选择轮换目标。
2. **备份与窗口：** 单独获得轮换授权，记录责任人、中心库身份、记录数、版本及恢复条件。对受影响密文和身份/版本做加密且限权的备份，不落明文。冻结配置写入，由用户安排依赖服务的维护/停启窗口，避免旧进程读取新密文。
3. **重加密：** 在受控进程中用旧密钥解密、随机生成新 32 字节密钥后重加密；保持既有 AES-GCM 格式，不输出密码/配置明文/密钥。中心库条件更新同时匹配原身份、版本及旧密文，核对影响行数；两类密文统一盘点和切换。工厂 OAPI 的普通编辑有模式锁，不能通过临时解绑、改模式来重加密，维护写入须单独审查。
4. **切换与验证：** 私有运行密钥与密文协调切换，清理元数据缓存，按用户操作单重启/冷建池。验证全部有效密文可由新密钥读出、原文内存比较一致、旧密钥不能解密新密文、冷池/DDL 使用正常以及 OAPI 配置本地解码；真实 ERP 调用仍另需授权。检查日志与构建产物不含明文。
5. **数据库账号密码：** 主密钥轮换不会改变 DB 账号密码。只有证据表明账号密码也暴露或实际策略要求时，另行安排账号轮换、连接可用性和撤销旧凭据；本轮没有证明调试解密字面量是当前有效账号密码。
6. **失败与历史：** 任一记录/版本冲突停止切换，不混用新旧密钥长期兜底；受控备份用于离线恢复和向前重加密，不把恢复暴露密钥作为长期上线方案。盘点历史提交和分发副本；删 main 不会撤销泄漏，未经授权不重写 Git 历史或强推。实际轮换完成前 QA-06 保持未关闭。

### 8.4 测试收据与真实环境边界

最终测试收据见下表及 [阶段 A 机器收据](evidence/2026-10-18-p1-stage-a/results.json)。测试源码位于 `mes-admin/src/test/java/dev/mesbase/server/P1*Test.java`，共用 `P1TestDatabase`。

- MYSQL 目标：来自当前私有 dev 的中心连接，主机 `192.168.18.25:3306`。测试仅计划 `test_only_p1_<随机UUID>` 新 Schema，`Factory=TEST_ONLY_UNBOUND`，不绑定运行 Factory，不复用 `mes_base_dev`/SIMPLE_B 业务表。拟建表为当前 Entity 派生的合成最小结构，拟 INSERT/UPDATE/DELETE 仅为测试生成记录，测试后 DROP 自有 Schema；不代表正式 Flyway 或生产完整 Schema 验收。
- 连接在 MySQL 握手阶段 EOF/CommunicationsException，沙箱外重试相同；没有进入建库/建表或业务 DML，MYSQL **未通过**。不能把日志中的拟定 Schema 名当成已创建。机器没有可用 mysqld、容器运行时，未擅自安装或改共享服务。
- REDIS 使用测试自行启动、结束的回环随机端口进程，独立命名空间；不连接或停止用户 Redis。完整 30 秒过期窗口有实测，不靠 Mock 的 TTL 参数断言替代。
- 没有修改前端、生产配置、Flyway 或生成模板；不执行 QA-02/03/04/05/10/11/12。没有针对五项 P1 的真实业务 HTTP、浏览器 UI、SIMPLE_B 业务数据复验、ERP 请求、密钥轮换、用户服务重启、提交/推送/部署。

| 级别 | 测试/命令 | 最终结果 |
| --- | --- | --- |
| UNIT | `python3 -m unittest discover -s scripts/tests -p test_private_config.py` | 4 通过；包括跨行构造、字面量拒绝及不回显 |
| STATIC | `python3 scripts/check-private-config.py --working-tree`；`./scripts/format-java --check` + manifest 中 23 个 Java 路径；`git diff --check` | 均通过；原密钥不在当前 Cipher 源码、class、打包 class 中；不代表历史已消除 |
| UNIT/MOCK/REDIS | `mvn -B -ntp -Dtest=P1CorrectnessTest,P1MaterialUnitTest,P1CipherTest,P1SettingsRedisTest -Dmes.test.redis=true -Dsurefire.failIfNoSpecifiedTests=false test` | 11 通过：6 + 3 + 1 + 1；其中 Redis 为真实独立进程，DB 为 H2 |
| 默认全集 | `mvn -B -ntp verify` | **752 总数，706 通过、46 跳过、0 失败、0 错误；BUILD SUCCESS**。跳过项不是已验收 |
| MYSQL | `MES_P1_MYSQL=true mvn -B -ntp -Dtest=P1CorrectnessTest,P1MaterialUnitTest,P1SettingsRedisTest -Dmes.test.redis=true -Dsurefire.failIfNoSpecifiedTests=false test` | JDBC 握手失败；未到 Schema 创建及断言。该连接尝试发生在最后新增回归前，仅作环境阻断证据，不宣称最终 MySQL 用例已验证 |
| 打包/回环 HTTP | `/usr/bin/python3 scripts/smoke-packaged.py` | **10 通过、0 失败**；真实自有 Java 进程 /health 与未认证拒绝边界；不加载业务 JDBC/账号，不替代五项 P1 的业务 HTTP 验收 |

`P1CorrectnessTest` 的 6 项覆盖新增/更新、角色/岗位/账号/outbox 写后故障、整行回滚、中间行失败与重复导入、提交后撤销失败、角色并发及缓存回滚/commit 故障。`P1MaterialUnitTest` 的 3 项包含逐类历史引用、非计量编辑、同步负例、历史单位删除，以及库存/生产用料引用持锁期间另一事务换单位被阻塞后拒绝；使用真实生成 SQL，但引擎为 H2。`P1SettingsRedisTest` 实测旧键失效、回滚不发布、Redis 删除故障、迟到回填 TTL、30 秒实际过期与 Excel 正反向转换重新读取。未执行真实投产 HTTP/真实 MySQL 并发，不把上述协议级 Fixture 扩写成这些证明。

### 8.5 交付与 GO / NO-GO

| 判定项 | 结论 |
| --- | --- |
| 代码整改是否完成 | **是，GO 仅限代码评审及继续验收**；本轮源码实现、定向回归、默认 verify、格式与打包 smoke 通过 |
| 隔离真实数据库验证是否完成 | **否，NO-GO**；MySQL 握手阻断，Redis 独立实测通过不能替代 MySQL |
| 五项 P1 是否关闭 | **否**；QA-06 仅源码处置，实际密钥轮换未授权；其余四项仍缺真实 MySQL/业务验收 |
| 生产部署是否可 GO | **NO-GO**；补真实事务/锁/回滚及业务 HTTP 验收，并按受影响环境完成单独授权的密钥处置后重新评估 |

剩余风险：既有历史数据是否已受旧单位变更影响尚未核实，本轮不自动修数；200 行导入上限未做满负载时延承诺；普通展示字典/配置保留明确的 30 秒单条缓存陈旧窗口；混合运行旧节点不能声称已应用新缓存协议。七项 P2 保持原台账，未扩展实施。回滚必须按本任务文件差异逐段处理，保留外部提交和质量改动，不能整仓 reset；不得以回滚代码代替泄漏密钥处置。

### 8.6 本轮变更回执

- 本文件：追加 §八，记录复核矩阵、实现文件、导入粒度、角色锁、缓存/Excel 消费者、计量保护、轮换方案、分级证据与 GO/NO-GO；保留 §六/七历史事实。
- `docs/development/next-stage-plan.md`：增加后续执行记录，明确阶段 A 已获单独授权及逐行导入契约对原建议的覆盖，不改后续 P2 范围。
- `evidence/2026-10-18-p1-stage-a/results.json`：完整命令、默认测试逐类计数、环境失败与未验证边界；`source-manifest.csv`：25 个本任务源码/工具文件 SHA-256；`verification.txt`：必要脱敏收据。临时完整日志可能被系统清理，持久结论不依赖它们独立存在。

**本轮修改业务代码及测试。** 未改运行配置、Flyway、前端或 SyncCoordinator，未执行真实密文写入、密钥轮换、用户服务启停、Git 提交/推送或部署。源码冻结 hash 与最终验证一致；工作期间外部提交及原有修改保护结果见 §8.1。


## 九、2026-10-18 使用 PROD 连接继续阶段 A 验收

### 9.1 授权边界与环境预检

用户明确授权读取私有 `application-prod.yml`，只在 PROD 实例上创建自有随机测试 Schema，并在隔离得到证明后执行 MySQL/Redis/真实 HTTP 验收；禁止写现有 PROD 业务表、修改真实工厂/数据源/OAPI、运行现有库 Flyway、启停现有服务、轮换密钥及 Git 提交/推送。本轮没有修改项目源码、测试源码、前端、配置、迁移或 SyncCoordinator。

开始 HEAD 为 `a793e0b7de5bc6f938b8aacbb7b9624ef0ac62c6`，保留阶段 A 及用户性能报告等已有改动。已保存本轮开始的工作树/暂存区差异。上一轮阶段 A 的 25 个源码/工具 hash 全部匹配，本轮没有重复执行其静态规则审查。

| 预检项 | 实际结果 |
| --- | --- |
| 配置入口 | 本机私有 `mes-admin/src/main/resources/application-prod.yml`，不复制到证据目录 |
| 实际端点及默认 catalog | **110.42.227.83:3306 / mes_base_prod**；JDBC `getCatalog()` 与配置一致 |
| JDBC 握手 | **通过**；预检连接使用有限 connect/socket timeout |
| 数据库 | **MySQL 8.0.40** |
| 默认存储引擎 | **InnoDB**；没有创建测试表，因此不声称测试表引擎已验收 |
| 当前会话隔离级别 | JDBC `4` = **REPEATABLE READ**；未修改全局隔离配置 |
| 直接授权（账号身份脱敏） | `USAGE ON *.*`；`ALL PRIVILEGES ON mes_base_prod.*`；`ALL PRIVILEGES ON mes_base_prod_business.*`；未显示任意独立库的创建权限 |
| 原配置迁移开关 | `mes.platform.migrate=false`，未修改/启用 |
| 独立 Schema 权限核验 | 见 §9.2，**1044 / 42000，拒绝** |

权限和连接元数据通过独立 JDBC 诊断进程读取，未启动 PROD Profile 应用。异常只记录类型、SQLSTATE、vendor code，不记录密码、Secret、主密钥、原始认证异常消息。脱敏结果见 [预检 JSON](evidence/2026-10-18-p1-prod-acceptance/preflight.json)。

### 9.2 隔离 Schema 创建与清理记录

- 本轮唯一拟建 Schema：`test_only_p1_55937b54e0444e158ba99214719b4706`。
- 运行标记：`55937b54e0444e158ba99214719b4706`。命令仅为 `CREATE DATABASE` 随机新库，指定 utf8mb4；没有 `IF NOT EXISTS` 或回退到现有库。
- 实际结果：**SQLSyntaxErrorException / vendor code 1044 / SQLSTATE 42000**。与 SHOW GRANTS 的权限范围一致：当前账号不能创建这一本轮测试库。
- 成功创建记录不存在；没有创建测试表、写入标记表或测试业务数据。自有数据库资源数为 **0**，无需 DROP；没有对现有 PROD Schema 执行 DDL/DML、migrate/repair/clean，也没有更改工厂或连接配置。
- 创建结果见 [权限核验 JSON](evidence/2026-10-18-p1-prod-acceptance/schema-permission.json)。不能把这个拟定库名写成“已创建、已清理”。

**隔离准入未通过，真实 MYSQL 与业务 HTTP 验收均为 BLOCKED。** 未尝试 GRANT、切换其他账号、利用现有库权限建测试表或调用真实业务账号执行破坏性用例。

最小解阻条件：由有权运维提供专用测试库的创建/管理及其中所需 DDL/DML、索引和触发器权限；更小的授权范围是由 DBA 预建明确命名的隔离测试库，仅对这些库授权，不需要向 Agent 开放现有 PROD 表写入。完整应用必须有独立测试中心库和业务库；需要证明两个 Factory 的分库路由时，应另有两个独立测试业务库。当前两个已有 PROD 库的 ALL PRIVILEGES 不能当成此类隔离授权。本轮不提供或执行扩大生产账号权限的操作。

### 9.3 测试启动副作用及路由检查

没有直接以 `spring.profiles.active=prod` 启动测试应用。已确认以下真实装配风险：

- `SystemConfiguration` 自定义 `startSystemJobs` 会执行 Job init 并调用 `scheduler.start()`，不能仅假定 Spring Quartz 的 auto-startup 开关可阻止它；该配置也开启 Spring scheduling。
- `SessionRevocationService.retryPending` 有定时 outbox 交付/更新；`ManagedDatasourceService` 有定时连接资源协调。系统缓存初始化也会清理当前连接配置所决定的 Redis 命名空间。
- `PersistenceConfiguration` 直接由 `mes.platform.url` 创建 Master DataSource；Master Mapper 与 TransactionManager 会使用其 catalog。业务 DataSource 再由受管工厂绑定解析，不能只改一个 JDBC 默认库就宣称隔离完成。
- ERP 连接由受管 Factory 集成快照解析；没有隔离中心库、测试 Factory/业务绑定和显式禁止外部执行的测试装配前，不能证明运行实例不会取得真实 ERP 目标。本轮没有读取真实 ERP 配置或发起 ERP 请求。

因此没有装配测试 Master/Business SqlSessionFactory、TransactionManager 或 FactoryContext，也没有创建测试绑定、修改真实绑定或启动新的业务 HTTP 服务；这些运行时隔离检查是未执行，不是通过。已有 `P1TestDatabase` 硬编码 dev 配置且使用 Entity 派生简化 DDL，不符合本轮 PROD 正式 Schema 要求，**未将它的 MYSQL 分支用于本轮验收，也未修改它来绕过权限阻断**。

打包 smoke 仅启动其自有随机回环端口进程，强制加载 classpath application.yml、明确 default profile，无业务 JDBC/账号；其健康与未认证接口测试不记为本轮 P1 业务 HTTP 验收。

### 9.4 实际测试矩阵及状态证据

| 编号 | 实际测试环境 | 测试数量 | 真实 DB 结果 | HTTP 结果 | GO/NO-GO |
| --- | --- | --- | --- | --- | --- |
| QA-06 | 当前冻结源码、随机测试密钥 | Cipher 1 项 | 不要求本轮轮换；未写真实密文 | 本项无业务写 HTTP 验收 | **部分完成**，实际轮换前不关闭 |
| QA-07 | 本地 H2 + Spring 事务代理/MyBatis；PROD 仅预检 | 导入相关 3 项本地；MYSQL 0 | **BLOCKED**，没有真实回滚前后状态 | **BLOCKED，0 请求** | **NO-GO** |
| QA-08 | 本地 H2/真实行锁 SQL；PROD 仅预检 | 授权及并发 1 项本地；MYSQL 0 | **BLOCKED**，未执行真实连接竞争 | **BLOCKED，0 请求** | **NO-GO** |
| QA-09 | 独立回环 Redis + H2；PROD 仅预检 | 中心提交/回滚 2 项本地 + Redis 1 项；MYSQL 0 | **BLOCKED**；Redis 单独实测不能代替 MySQL | **BLOCKED，0 请求** | **NO-GO** |
| QA-01 | H2 + 实际生成 Mapper/物料锁协议；PROD 仅预检 | 单位保护 3 项本地；MYSQL 0 | **BLOCKED**，MySQL 锁/死锁/超时及撤销均未验收 | **BLOCKED，0 请求** | **NO-GO** |

没有 MySQL 测试事务、测试 COMMIT/ROLLBACK、两连接并发时间线或锁等待/死锁指标可供报告。连接预检成功不等于这些用例通过，指标记为 N/A，不能写“0 次死锁/全部回滚正确”。正常导入/更新、关联与 outbox 失败、角色删除/状态竞争、安全权限负例、缓存 commit/Redis 故障、单位引用及撤销等所需真实业务用例仍全部待完成。

独立 Redis 定向测试实际验证提交后失效、回滚不发布、改名清旧键、删除失败后的有限 TTL、旧值回填、实际 30 秒过期及 Excel 字典转换重载；其 DB 是 H2，不记录为真实 MySQL + Redis 组合验收。普通展示配置/字典的 30 秒单条发布寿命及允许迟到回填窗口保持原设计，不宣称从 DB 提交瞬间起强一致；`sys.account.*` 直接 DB 的本地回归不扩写为本轮 PROD 数据核验。

### 9.5 本轮回归收据与变更回执

最终回归结果和命令见下表及 [机器结果](evidence/2026-10-18-p1-prod-acceptance/results.json)。默认回归清除外部验收 opt-in，禁止它们转向共享数据库；P1 定向日志确认 DB product 为 H2。Redis 测试独立创建/销毁本轮进程，不连接生产 Redis，不执行 FLUSHDB/FLUSHALL。

| 验证 | 实际命令/入口 | 本轮结果与边界 |
| --- | --- | --- |
| PROD 连接预检 | 临时 `ProdPreflight.java`，复用现有 JDBC runtime | 连接、版本、引擎、会话隔离及 SHOW GRANTS 读取成功；凭据不进入命令行/报告 |
| 独立库创建权限 | 临时 `SchemaPermissionProbe.java`，单次随机 CREATE DATABASE | 1044 / 42000；未创建资源，无清理动作 |
| P1 定向 | `mvn -B -ntp -Dtest=P1CorrectnessTest,P1MaterialUnitTest,P1CipherTest,P1SettingsRedisTest -Dmes.test.redis=true -Dsurefire.failIfNoSpecifiedTests=false test` | **11 通过、0 失败**；Cipher 1、Correctness 6、Material 3、真实 Redis 1；数据库为 H2 |
| 默认回归 | `mvn -B -ntp verify`，清除外部验收 opt-in 和继承的 JVM 属性覆盖，显式 `MES_P1_MYSQL=false` | **752 总数、706 通过、46 跳过、0 失败/错误，BUILD SUCCESS**；含 Factory/权限/库存/生产/质量既有回归，跳过不等于验收 |
| 打包 smoke | `/usr/bin/python3 scripts/smoke-packaged.py` | **10 通过、0 失败**；自有进程回环 HTTP 与产物结构，不是 P1 业务 HTTP |

本轮持久 JSON 证据为 `preflight.json`、`schema-permission.json`、`results.json`，另有 `verification.txt` 脱敏摘要；命令与默认测试逐类数量可从 results.json 复核。临时完整日志位于 `/private/tmp/mes-p1-prod-acceptance/`，不作为唯一长期证据。

本轮仅追加本文件 §九及对应脱敏证据；没有修改项目源码。私有 PROD 配置 hash 不变，上一轮 25 个源码/工具 hash 全部匹配；除本文件外，本轮开始时的所有已有工作树差异保持一致，暂存区不变。没有执行密钥轮换、现有服务启停/部署、Git 提交或推送。

**最终状态：MYSQL BLOCKED；P1 业务 HTTP BLOCKED；五项 P1 未整体关闭；生产 NO-GO。** 下一步只缺可合法使用的隔离数据库权限/资源，不扩大到阶段 B，也不以连接成功或已有本地测试通过替代验收。

## 十、2026-10-18 阶段 A：复用 Factory B 的真实 MySQL / HTTP 验收

本节接续 §九的 CREATE DATABASE 权限阻塞，保留此前结果。用户本轮明确授权现有独立 Factory B 的专用测试数据写入，不重新执行十万级压测，不写真实 PROD 业务数据或中心权限。本轮结论：**QA-01 在本次九类正确性验收范围内 GO；QA-07/08/09 真实 Master 验收 BLOCKED；QA-06 部分完成；阶段 A 整体仍 NO-GO。** 不外推为生产迁移、所有 Factory、ERP 或完整业务流程已验收。

### 10.1 实际环境与隔离门槛

- 正式中心查询确认 `V23_FACTORY_B_TEST → datasource 2 → 110.42.227.83:3306/mes_base_v23_factory_b_test`，Factory revision=9、DataSource revision=14、均 ENABLED；LOCAL、integration revision=1，ERP provider/config/oapi reference 均空。原 `PROD → datasource 1 → mes_base_prod_business` 保持原绑定，没有重绑。
- 从受管数据源密文解出既有业务凭证并实际连接，不以库名或 TEST 名称代替验证。MySQL **8.0.40 / InnoDB**；连接默认 **REPEATABLE READ**，物料写入 Service 及并发探针显式 **READ COMMITTED**。业务账号具备所需 DML 权限，且实际权限比本轮需求更广；本轮未利用这些额外权限修改其他库、账户或全局设置。本机备份管理员连接只用于最初只读元数据查询，后续测试使用受管业务数据源凭证。
- Master 仍是共享 `mes_base_prod`。可见库中没有已授权的专用中心测试 Schema。既有 CASE12 专用账号/角色位于共享 Master，名称带 TEST 不能证明可开展故障注入、删除或授权竞争；没有对其做这些写入。DEV Schema 不自动视为隔离 Master。
- B 库存在其他连接，不能宣称独占。开始保存 **121,752 条既有记录**的主键/全字段 SHA256，以及 Factory/数据源绑定；本轮使用标签 `TEST_P1_20261018_0c44f4cc`，所有写入对象均本轮创建。CASE12 原有七张表记录逐字段与原性能验收前快照一致。
- TEST ONLY 独立 Spring Boot 实例使用真实两个 SqlSessionFactory 和两个 TransactionManager，运行时确认 Bean 不同；生产包扫描、动态路由、事务租约及业务 Mapper 保留。测试拦截器禁止 Master Mapper 写入，并在业务 SQL 前核对 Factory B 和连接 catalog。没有将 Master 路由到 B，也没有运行旧 P1TestDatabase 的建库/简化表分支。
- 禁用 `startSystemJobs`、缓存预热和 Scheduled 注解处理器；Quartz 仅创建且未启动。Redis 为本轮独立回环实例、独立命名空间；登录/操作审计写入本地 TEST ONLY sink，真实 BCrypt、TokenService、账号状态、当前角色及 HTTP 权限校验保留。此适配是保护共享 Master 的审计落点调整，不是认证/权限 Mock；不构成生产中心审计持久化验收。没有实际 ERP、消息通知或外部同步调用。

### 10.2 B 库 V38 / 当前源码 V39 的差异及恢复

初始 B 库正式迁移停在 V38，当前源码已包含质量 V39 Mapper。正式投产请求 UUID `f44e012d-8f0e-4498-9921-99b3504218e1` 首次 HTTP500/INTERNAL_ERROR（requestId `bbe13888-d7de-43d9-b903-532006d93c22`），实际 SQL 读取 `mes_production_defect.defect_type_id` 报 MySQL 1054。

**HTTP500 不等于整体回滚：** 真实查询证明 Input 已 CONFIRMED、Issue904 已 CONFIRMED、Execution903 已创建，库存1808从2扣至0；异常发生在已提交后的 `ProductionQuantityService` 结果读取。没有生成新 UUID 重发。第一次正式撤销同样因缺列返回500（requestId `09e757b6-d6b7-4251-9e03-d614a7e48579`），撤销前后 Issue/Input/Stock/Action 全字段完全相同，Issue仍 CONFIRMED，证明该次撤销事务的 VOID_PENDING 更新回滚。

按项目“缺表走正式迁移”规则补齐验收前提：只对已经核实的 B 独立验收库执行**既有、未修改的 `V39__operation_defect_types.sql`**，target固定39，使用正式 Flyway 记录。执行前备份受影响表结构和38条迁移记录，确认 `mes_production_defect` 历史行数为0、待建配置表不存在；V39新增空表、可空字段及索引，无历史回填。Flyway迁移自身校验和迁移后validate均通过，executed=1，B库当前39。预先单独调用validate曾因pending39拒绝，未写库；改为正式migrate自身的迁移前校验后成功，没有repair/clean、没有忽略checksum，也没有改写迁移文件。原PROD中心和业务库均未迁移。

迁移后以**原UUID/原冻结请求**重放返回200，Issue/Execution仍904/903，流水集合不增加；正式撤销返回200/VOIDED，库存恢复2，再次撤销200但流水不增加。保留迁移前500及数据库已提交状态，不能把本次恢复改写为从未失败。该证据还说明：当前源码部署到其他仍为V38的库前，需匹配正式迁移；本轮没有升级这些库。

### 10.3 QA-01 九类验收与真实事务

| 场景 | 真实入口与对象 | 结果 |
|---|---|---|
| 1 无引用修改 | HTTP Material6，Unit3→4→3 | 200，数据库回读一致；首轮失败探针释放锁后允许的修改最终使Material6为Unit4，属于本轮对象，不影响历史记录 |
| 2 库存余额保护 | Material7 / Stock1806，正式 OTHER_RECEIPT入10 | 改单位409 / RESOURCE_CONFLICT，Unit3不变 |
| 3 零余额历史保护 | 正式 OTHER_ISSUE出10，Stock1806=0，保留两条历史动作及单据行 | 改单位409，Unit3及历史动作单位不变 |
| 4 领料引用 | Material8 / Issue903 / Item903，正式DRAFT主单和明细 | 改单位409，原单位不变 |
| 5 工艺/工单数量引用 | Product9 / Routing4 / Node16 / WorkOrder903、904 | 工艺保存、工单创建后分别改单位409；WorkOrder903通过正式作废入口为CANCELLED后仍拒绝改单位 |
| 6 同步写入保护 | 实际Spring代理 `IMaterialService.upsert` 输入合成 ExternalMaterial，目标Material7 | RESOURCE_CONFLICT；数据库Unit3不变。只验实际同步落库Service保护，不声称真实ERP同步已验收 |
| 7 单位删除/新增引用竞争 | Unit5；实际IUnitMeasureService删除与实际IMaterialService创建Material11 | 两个独立MySQL连接；引用方提交后，删除方RESOURCE_CONFLICT；Unit5和Material11均存在，无悬空引用 |
| 8 库存新增/改单位竞争 | Material10 / Stock1807 | 持锁方复用正式StockReferenceRules与生成MyBatis Mapper创建本轮零余额引用；正式物料Service改单位等待后RESOURCE_CONFLICT，Unit3不变。此竞争的引用写入是规则+Mapper层，HTTP库存创建已在场景2独立通过 |
| 9 合法生产撤销 | WorkOrder904 / Execution903 / Input原UUID / Issue904 / Stock1808 | V39后正式HTTP撤销成功；库存2→0→2，原负向动作不改写，追加引用原动作的正向2；重复撤销不增事实，撤销后物料改单位仍409 |

额外真实回滚证明：Material13在具名业务事务中先查询Unit3，调用实际事务代理更新后同连接查询Unit4，再主动抛出TEST ONLY异常；事务结束后新事务直接SQL查询仍Unit3。`service-results.json`完整记录 **3→4（未提交）→3（回滚后）**，不以HTTP失败或缓存回读代替数据库证明。首次撤销500前后的全字段比较另见 `production-boundary-result.json`。

并发时序：事务A先取实际行锁，事务B另一个连接执行竞争操作，750ms检查B仍未完成，放行A创建引用并提交，B结束并被业务冲突拒绝。库存竞争连接 **97663/97662**，耗时1306ms；单位竞争 **97662/97663**，耗时1020ms。本轮两组未发生死锁/锁等待超时异常，不推断全实例没有死锁。尝试直接经路由数据源读取performance_schema锁表时，因为没有业务事务被基础设施拒绝，所以**没有取得InnoDB锁表指标**；不把750ms屏障或总耗时写成数据库精确锁等待统计。连接ID、阻塞屏障和最终状态分别留证。

独立应用实际HTTP共 **72次：200×61、401×1、403×1、409×7、500×2**；两个500均为V39补齐前，补齐后的恢复请求没有500。401=UNAUTHENTICATED，已存在B限定测试账号尝试物料编辑得到403=FORBIDDEN，没有修改账号或角色；409=RESOURCE_CONFLICT。68次在脱敏HTTP日志，另4次为探针续接登录/新增物料、确认已提交后的登录/首次撤销，分别有私有运行日志、状态和数据库比较证据。HTTP请求成功与真实DB最终状态分开核对。

### 10.4 历史数据保护、资源清单与清理

- 最终全部 **121,752 条开始时既有记录逐主键/全字段hash不变，无缺失**；原工单/Execution状态、历史UUID、库存余额及动作均包含在比较中；绑定及版本不变。CASE12原Stock1仍11，原七张表的既有记录一致。
- 原17项关联审计开始/结束均PASS：901个闭环、90,100条报工、100,011条核心事实；17项bad全部0。审计按原journal的确切工单/执行/库存ID关联，不能拿库内总数减法代替历史完整性。
- 本轮新增48条业务/关联记录及1条Flyway记录，详见 `created-resource-ids.json`；标签不覆盖原 `TEST_PERF_20261018`。主要ID：Unit3/4/5、Material6～13、Warehouse4、Stock1806/1807/1808、WorkOrder903/904、Execution903、Issue903/904、Routing4。原流水保留，新增5条库存动作，其中生产撤销正向动作明确引用原扣减动作。原性能闭环之外新增1个执行，不计作新的十万级验收。
- 测试记录全部保留供复验；没有删除不可变事实，也没有DROP任何Schema或表。原型并发探针因缺受管后台集成上下文而失败，未作为通过证据；重装配后用独立Material10完成规则+Mapper竞争，避免把探针错误当作产品修复。另有测试工具编译、重复消费文件及只读核验字段拼写问题均已更正，保留私有原日志；未因此修改业务实现。
- 本轮所有自启应用和Redis监听均已关闭，8081/5173仍监听；未启停、重启或重新部署用户服务。没有Git提交、推送或历史改写。

### 10.5 关闭矩阵、回归与最小剩余条件

| 问题 | 使用数据库 | MySQL真实测试 | HTTP验收 | 是否关闭 |
|---|---|---|---|---|
| QA-01 | Factory B正式Schema，V38→正式V39 | 九类场景通过；额外真实代理回滚通过 | 实际认证/权限及正反例通过；迁移前500与原UUID恢复均留证 | **GO，本轮约定范围关闭** |
| QA-07 | 仅共享Master可用 | BLOCKED | 写入验收BLOCKED | NO-GO |
| QA-08 | 仅共享Master可用 | BLOCKED | 写入验收BLOCKED | NO-GO |
| QA-09 | 仅共享Master；独立Redis可用 | 真实Master事务/缓存组合BLOCKED | 写入验收BLOCKED | NO-GO |
| QA-06 | 独立密钥运维 | 本轮不轮换 | 不适用 | 保持部分完成 |

QA-07/08/09最小解阻条件：明确授权的专用中心测试Schema（正式中心结构）、可隔离Master绑定的测试服务与专用测试身份/角色/部门/配置/字典；Redis沿用独立实例。B业务库、共享中心库中的TEST名称或备份管理员权限都不能替代这一条件。QA-06仍需另行授权真实密钥轮换。

本轮重新运行：P1定向 **11通过/0失败**（H2与真实独立Redis，含30秒TTL实际过期）；`mvn -B -ntp verify` **752总数、706通过、46跳过、0失败/错误，189类，BUILD SUCCESS**；打包smoke **10通过/0失败**。这些回归与上述真实MySQL业务验收分别记账，跳过项不计通过，smoke不代替P1业务HTTP。

证据目录：[`evidence/2026-10-18-p1-factory-b-acceptance/`](evidence/2026-10-18-p1-factory-b-acceptance/)，包括 `results.json`、真实最终查询SQL/JSON、绑定、并发与回滚、17项审计前后结果、资源ID、历史保护、回归及清理回执。私有原始日志、全行hash与迁移结构备份位于 `.local/p1-factory-b-20261018/`；TEST ONLY源码在自有 `target/p1-factory-b-acceptance/`，不是生产模块。

**修改回执：** 未修改业务源码、生产配置、连接池配置、SyncCoordinator、前端或已发布Flyway文件。新增TEST ONLY验收工具、脱敏证据及本节；实际修改数据仅为授权B库的专用测试记录及正式V39 Schema/迁移记录。上一轮25个源码/工具fingerprint全部匹配，开始时已有工作区补丁和暂存区在报告更新前完全一致。阶段B及其他P2不开展。QA-01关闭不改变五项P1整体仍未完成的状态，最终整体 **NO-GO**。

## 十一、2026-10-18 PROD key 复核与工厂创建意图澄清（暂停待目标业务库）

用户先说明已修复PROD datasource-key并允许不可用时重新创建数据库环境；Agent误解为另建完整隔离Master+Business环境。后续用户明确纠正：意图是**通过MES创建新的Factory和DataSource，使用当前环境密钥，并使用PROD数据库连接信息**。本节以这一纠正为准，不把此前独立Master建库路线视为用户最终需求，也不把已有QA-07/08/09 BLOCKED写成已完成。

实际只读复核：当前application-prod.yml JDBC连接110.42.227.83:3306/mes_base_prod成功，MySQL8.0.40/InnoDB/REPEATABLE READ；配置账号仍仅在mes_base_prod及mes_base_prod_business拥有Schema内权限，*.*仅USAGE。当前配置key解密既有DataSource2密码仍报AEADBadTagException；没有输出key/密码、改写旧密文或修改PROD配置。新数据源应由MES用当前key正式加密，不能通过改旧密文解决本次新建需求。

误解路径的实际副作用已如实保留：使用上一轮已核实的受管业务连接凭证，在同实例创建 `test_only_p1_master_f58fdf52f29d4db89f6aab633dfecf9b` 和 `test_only_p1_business_f58fdf52f29d4db89f6aab633dfecf9b`；执行了部分正式Flyway初始化。用户纠正后定位并SIGTERM停止本轮专用Provision进程，子进程退出143，官方bootstrap没有执行；没有启动该环境应用、测试账号或Redis，没有创建MES的Factory/DataSource记录。停止后只读核对：Master51表且本轮owner标记存在，Business24表且owner标记暂未恢复（初始化尚未完成）；因此两个库不能宣称就绪，不应直接拿来验收。本轮没有擅自DROP，资源及创建/停止证据保存在 `.local/p1-master-20261018/environment.json`、`stopped-misinterpreted-provisioning.json` 与私有迁移日志，TEST ONLY工具在 `target/p1-master-acceptance/`。

原PROD中心/业务记录、Factory B绑定/性能事实及用户服务未被上述初始化操作修改。未提交/推送代码，未执行密钥轮换。后续仅沿用MES正式管理入口，当前待明确新DataSource的目标业务库：PROD profile中的mes_base_prod是中心库，不得作为Factory业务库；已绑定的业务库也不能未经核实被新Factory共享。新Schema还需目标连接账号具备对应范围权限，不能把key修复视为建库授权或数据库GRANT自动修复。

### 11.1 用户提供 key 的只读实测

用户随后提供一个候选key并要求实际解密。TEST ONLY探针通过标准输入接收该值，不把key写入文件或输出日志；读取当前PROD中心库DataSource2 revision14的现存password_cipher，解密仍失败，根因类型AEADBadTagException。该候选key与磁盘application-prod.yml中的datasource-key不相同。中心库握手PASS；目标仍110.42.227.83:3306/mes_base_v23_factory_b_test；因解密失败，未尝试业务握手。没有修改配置、密文、数据或运行服务。不能由此确定原加密key是哪一把，也不代表已检查运行进程的实际环境覆盖。脱敏结果在 `.local/p1-master-20261018/provided-key-check-redacted.json`；探针源码不含key，位于 `target/p1-key-check/CheckProvidedKey.java`。

### 11.2 第二个候选 key 验证成功（取代此前当前 key 不匹配的判断）

用户提供第二个候选key后，重新只读查询DataSource2 revision14的现存密文：候选值与本次读取的磁盘PROD配置datasource-key相同，AES-GCM解密PASS；以解密后的既有凭证实际连接110.42.227.83:3306/mes_base_v23_factory_b_test，JDBC握手PASS、catalog匹配、MySQL8.0.40。中心握手同样PASS。未回显或落盘候选key/解密密码，未由Agent修改PROD配置、密文或数据；此前失败保留为当时对应候选值的实测，不能继续宣称当前配置key无法解密DataSource2。此结果不解决新Schema权限或隔离Master缺失，也不代表新Factory/DataSource已创建。脱敏结果：`.local/p1-master-20261018/second-candidate-key-redacted.json`。

## 十二、2026-10-18 PROD 版本：隔离 Master 的 QA-07 / QA-08 / QA-09 真实验收

用户本轮明确授权提供隔离Master测试库及测试身份，完成剩余三项；QA-06真实密钥轮换继续独立处理。本节取代 §十中三项环境BLOCKED状态，保留此前失败、误解和权限证据。**QA-07/08/09本轮真实MySQL、Redis及必要HTTP全部通过，可在约定范围关闭；QA-01沿用§十的真实通过结果。QA-06仍部分完成，五项整体尚不能宣称全部完成。**

### 12.1 环境、正式结构、身份与隔离证明

使用当前代码、`prod` profile、当前PROD datasource-key；为独立实例提供受限私有覆盖配置，将Master指向专用库，使用独立回环Redis。未修改application-prod.yml、数据库账号权限、生产凭证、主密钥或既有密文。

| 对象 | 实际值 / 证据 |
|---|---|
| MySQL实例 | `110.42.227.83:3306`，MySQL8.0.40、InnoDB，连接默认REPEATABLE READ |
| 独立Master | `test_only_p1_master_f58fdf52f29d4db89f6aab633dfecf9b` |
| 独立业务库 | `test_only_p1_business_f58fdf52f29d4db89f6aab633dfecf9b` |
| 归属 | 本轮既有创建记录、runId `f58fdf52f29d4db89f6aab633dfecf9b`、数据库owner表相符；不是只按TEST名称判断 |
| 正式迁移 | Master完整V41，FactoryFlyway校验通过；业务库从已成功V22续跑17个正式迁移至V39，迁移后校验通过，恢复owner标记；不执行repair/clean，不重写迁移 |
| 专用身份 | 官方空库PlatformAdminCli初始化 `test_p1_admin`，userId100；另经正式HTTP创建 `p1_factory_only`，只分配本轮Factory角色 |
| MES管理对象 | 在**隔离Master**经正式HTTP创建Factory=`P1_FACTORY`、DataSource=1、LOCAL、Factory revision1 / DataSource revision2；不是共享PROD中心的数据源1，也未修改原PROD或Factory B绑定 |
| 新数据源密文 | 用当前PROD key重新只读解密PASS，真实业务库JDBC握手PASS，catalog确认为本轮独立业务库；候选key与私有测试覆盖及当前PROD key相同，均不回显 |

独立应用真实装配 `masterSqlSessionFactory` / `businessSqlSessionFactory` 及两个具名OwnedTransactionManager，并在启动核对两组Bean不同。中心Mapper仍通过正式扫描归属Master，Service为实际Spring事务代理；没有把中心Mapper挂到业务库，没有使用H2、简化表或MockMvc替代本节真实验收。正常HTTP使用真实BCrypt、TokenService、Redis会话及当前角色/权限；服务层故障探针的操作者从本轮真实管理员记录构造，仅用于直接Service测试，HTTP认证另行真实执行。

TEST ONLY装配在SQL prepare前检查实际连接catalog，仅允许上述两个独立库，拒绝原PROD及B库引用；所有记录到的应用MyBatis SQL实际catalog均为独立Master（本轮三项均属中心业务）。管理接口携带本轮Factory头也没有错误切到业务库。SQL记录为参数化prepare文本，不记录密码、key或参数值，不作为事务数量统计。

独立实例不占8081/5173；移除Quartz初始化/启动、缓存预热入口，并在测试装配中禁止Scheduled任务实际执行、清理调度处理器。没有调用ERP、外部同步或消息通知。审计日志正常写入独立Master，本节不使用§十的本地审计sink。为测试登录关闭的captcha开关仅存在于独立Master；原PROD安全开关未改。提交故障通过TEST ONLY连接包装器注入；两个Druid监控构造入口在测试装配中取得原池，池参数不改。

### 12.2 QA-07：用户导入 / 账号版本 / outbox

真实Service层 **12个断言覆盖原10类要求**；实际HTTP另验证成功、部分失败、重复导入。真实MySQL状态见 `service-cases.json`：

1. 正常导入新增与更新，查询确认账号、角色和岗位完整；实际对象为 `SysUserServiceImpl$$SpringCGLIB`，逐行self代理事务生效。
2. 分别在真实 `SysUserRoleMapper.batchUserRole`、`SysUserPostMapper.batchUserPost` **SQL执行后**注入异常，新账号消失，关系总数恢复，不能只靠异常返回判断回滚。
3. 分别在真实用户UPDATE、`SessionAccountMapper.enqueue` SQL之后注入异常，完整比较同一用户的nickName、status、auth_revision、角色、岗位与outbox前后；结果完全相同。
4. 中间行引用不存在的角色被拒绝；首尾成功行保留，失败行及其关系不存在。正式Excel HTTP中间行Bean校验失败同样保留首尾行，返回422/BUSINESS_REJECTED并明确其他成功行已提交。
5. 在独立真实Redis创建本轮用户旧版本会话，状态变更及改密分别使MySQL auth_revision增加、outbox持久化、旧会话索引撤销；不是只检查方法被调用。
6. 重复导入不生成第二个账号或重复角色/岗位关系；遵循既有逐行提交契约，不宣称整批原子导入。

本轮案例前缀及确切userId、roleId、postId、版本/outbox快照均在脱敏案例和最终数据库清单中。异常注入是SQL之后的测试故障，不假称真实发生过磁盘故障或新增FK约束失败。

### 12.3 QA-08：角色数据范围 / 回滚 / 三组并发

真实Service层 **12个断言覆盖原8类要求**；正常修改、无效部门、PLATFORM/FACTORY边界还通过实际HTTP验证。

- 正常替换后data_scope和部门关系一致；无效部门被拒绝，原范围和部门集合保持不变。
- 在范围UPDATE、删除旧部门关系、批量插入新部门关系SQL后分别注入异常；比较角色范围/状态/kind/factoryId及部门集合，全部回滚至原状态。具体before/after对象保留，不以请求失败替代证明。
- 角色kind/所属Factory创建后不可变；Factory账号访问平台dataScope接口403/FORBIDDEN；Factory角色携带平台权限、PLATFORM角色携带Factory绑定均422/BUSINESS_REJECTED且未生成无效角色。

| 并发场景 | 真实MySQL连接（持锁 / 等待） | 750ms屏障 / MySQL锁表 | 最终结果 |
|---|---|---|---|
| 两次数据范围替换，Role107 | 98118 / 98116 | B仍等待；performance_schema确认98116等待98118；总1084ms | 最终为后一完整部门集合，无混合关系 |
| 数据范围修改与删除，Role108 | 98116 / 98118 | B仍等待；锁表确认反向等待；总1021ms | 沿现有系统角色软删除契约del_flag=2，旧部门关系清除；不是要求角色物理行消失 |
| 数据范围修改与状态更新，Role109 | 98118 / 98116 | B仍等待；锁表确认等待；总969ms | status=1，已提交data_scope及部门集合保留 |

每组A先调用真实Service取得角色锁，B以另一连接进入竞争操作；记录锁等待后才放行A提交，再检查B完成及数据库终态。没有用JVM锁代替数据库锁，没有用H2推断MySQL。三组完成时未出现死锁或锁等待超时，不外推为全实例从无死锁。

### 12.4 QA-09：真实 MySQL + Redis 的缓存提交边界

**12个断言及1个事务内观测点覆盖原10类要求**，配置/字典管理另有真实HTTP正例：

- 正常提交、共享缓存写入及有限TTL；外层事务内读到自己的未提交值，同时Redis仍保持原已提交值；主动异常后MySQL及Redis均恢复/保持原状态，事务内没有发布共享值。
- **真实提交连接故障：** 在COMMIT即将发出前，从本轮连接读取CONNECTION_ID，以控制连接核实该ID确属本轮独立Master后，仅KILL这个自有未提交连接。真实JDBC提交失败，MySQL回滚，新配置行数0、共享缓存不存在，afterCommit未发布。没有KILL其他连接、MySQL服务或用户应用；确切连接ID见 `fault-injections.json`。
- **真实Redis删除失败：** 仅在本轮独立Redis临时用ACL拒绝DEL/UNLINK，取得真实NOPERM错误；配置UPDATE仍提交，数据库为COMMITTED，共享缓存暂为BEFORE；finally恢复ACL。没有关闭整个Redis来抹掉陈旧缓存，也没有触及生产Redis。
- 配置键重命名后旧/新键正确失效；字典类型重命名后类型、数据行及旧/新缓存一致；另验证字典重命名事务回滚。
- **并发迟到回填：** 独立MySQL读线程读出OLD后暂停，写线程提交NEW并清缓存，再释放旧读线程，真实观察到数据库NEW/缓存OLD；等待真实30.5秒后，Service重新从MySQL读取NEW并以有限TTL发布。
- 向 `sys.account.*` 本轮测试键写入与数据库相反的缓存值，Service仍返回数据库值，证明安全开关不依赖缓存。DictUtils在到期后仍通过真实服务读取字典标签。

**设计仍允许短暂陈旧：** TTL为每次缓存发布后的30秒，迟到旧读也可能从其发布时重新起算；长读本身的时间不能被忽略。不承诺强一致，也不承诺数据库提交起严格30秒内全部读者可见新值。实际Redis证据不等于已部署环境Redis故障演练。

### 12.5 HTTP、原环境保护、回归与探针边界

完成实际HTTP **11类验收检查**。连同环境准备、错误断言校准和续跑，共56次HTTP：200×43、422×9、403×2、401×1、404×1，没有500。404来自探针误用了已不存在的旧configKey路径，后改用源码实际 `config/{id}` 和 `common/dict/{type}`；最初将业务拒绝预期为400，已按真实422契约校准。保留这些原始结果，不能把它们描述成产品接口缺陷或删除失败日志。所有写入结果均通过真实MySQL最终状态复核。

测试工具还曾有AtomicReference故障标记比较、角色软删除断言及Mapper未回填ID等适配错误，均只修测试工具，保留原始私有日志；最后分别使用已通过的用户、角色、缓存组结果汇总，**36个通过断言+1个已验证事务观测点**。没有为让探针通过修改或弱化生产实现；故障注入点对应真实SQL，回滚状态可独立复核。

共享PROD中心14张管理表开始479条记录。结束观察到另一个标记 `test_sqltrace_v39_b` 的Role103在21:01:54新增，另增1条用户角色、2条角色菜单关联；该标记不是本轮对象。11张表全量hash一致；对另3张表排除新增Role103及关联后，hash与本轮开始完全一致，证明原479条管理记录未变。共享库并非完全静止，本轮没有回滚这些并行新增，也不归因它们的业务来源。本轮Factory、DataSource、用户标记在共享中心查询数量均0；应用SQL catalog记录及写入前门禁进一步证明测试写入仅进入独立Master。此保护结论覆盖上述管理表，不冒充所有生产库的全量快照。

本轮没有对Factory B业务库写入；QA-01源码/工具25项fingerprint全部一致，沿用§十真实验收及历史保护证据，没有重复十万级造数、压测或宣称本轮重跑其业务闭环。

重新运行：

| 检查 | 本轮实际结果 | 证据边界 |
|---|---|---|
| P1定向（显式真实Redis） | 11通过、0失败 | H2/Spring+独立Redis回归；本节实际MySQL案例另记 |
| `mvn -B -ntp verify` | 752总数，706通过、46跳过，0失败/错误，189类，BUILD SUCCESS | 跳过不计通过 |
| packaged smoke | 10通过、0失败 | 独立产物/基础HTTP冒烟，不代替本节业务HTTP |
| 真实MySQL/Redis/HTTP | 三项要求覆盖通过 | 正式Schema、实际Service/Mapper、认证权限和独立应用 |

自启应用及Redis均已停止，最后实例55759/55681无监听，原8081/5173仍监听；未重启、停止或部署用户服务。测试库、唯一owner标记、Factory、DataSource、专用身份及测试数据**保留供复核**，没有DROP或删除原环境数据。管理员凭证仅保存在本机0600私有文件 `.local/p1-master-20261018/credentials-private.json`，不在报告回显。

### 12.6 最终关闭状态与修改回执

| 问题 | 使用数据库 | MySQL真实测试 | HTTP验收 | 是否关闭 |
|---|---|---|---|---|
| QA-01 | 原Factory B | 沿用§十真实PASS，源码fingerprint不变 | 沿用§十真实PASS | GO，已关闭 |
| QA-07 | 本轮隔离Master V41 | PASS，10类要求/12断言 | 成功、部分失败、重复导入PASS | **GO，可关闭** |
| QA-08 | 本轮隔离Master V41 | PASS，8类要求/12断言、3组真实锁竞争 | 正例及部门/权限负例PASS | **GO，可关闭** |
| QA-09 | 本轮隔离Master V41+独立Redis | PASS，10类要求/12断言+1观测 | 配置/字典管理PASS | **GO，可关闭** |
| QA-06 | 独立密钥运维 | 本轮没有真实轮换 | 不适用 | 仍部分完成 |

**剩余条件只有QA-06真实密钥轮换及其独立运维验收；未执行、未假称通过。四项正确性P1可关闭，五项阶段A整体全完成仍NO-GO。** 本轮不构成生产部署授权、真实ERP验收或所有Factory/业务迁移均通过。

本轮未改业务源码、前端、生产配置、连接池参数、SyncCoordinator、已发布迁移或生产密钥/密文；未提交/推送。新增/修订仅TEST ONLY工具、隔离环境私有配置/数据与本节及脱敏证据，更新规划入口的执行状态。生产配置hash、原25项源码/工具hash、暂存区与开始时工作区补丁在报告更新前全部保持一致。

证据：[`evidence/2026-10-18-p1-master-acceptance/`](evidence/2026-10-18-p1-master-acceptance/)，含 `results.json`、37个案例条目、故障注入、参数化SQL prepare轨迹、HTTP结果、真实最终SQL/数据库清单、原环境比较、回归和服务清理。TEST ONLY源码：`target/p1-master-acceptance/`；私有原始日志与失败探针：`.local/p1-master-acceptance-20261018/`。没有把缺失/失败的原型探针记录从时间线中抹去。

## 十三、2026-10-23 对照长期产品规划的当前工作树复核（只读源码审查）

### 13.1 输入、范围与证据等级

输入：用户《Base MES 产品长期规划 V1.0》及 `docs/development/next-stage-plan.md` 当日 A—F。后端 HEAD `7302242`，前端 HEAD `e5025d9`，均以 HEAD 加当前已修改、未跟踪文件为准。开始时后端已有三个文档修改及 `docs/operations/`，前端已有大量生产、质量、库存和工作台改动，全部保留。三个子 Agent 只读追踪生产、质量数量、仓储 ERP，主 Agent 复读关键跨模块链并统一更新文档。

本次是**围绕产品规划的业务入口与横切边界审查，不是全仓审计**。未覆盖所有平台管理、设备、OpenAPI、文件和生成器实现。没有读取私有连接配置或凭证，没有连接数据库、业务 HTTP、浏览器或 ERP；没有执行构建、完整测试、lint、迁移或服务烟测。下列“已有实现”只说明当前源码可达，不证明部署或首厂验收通过。

### 13.2 主 Agent 独立复核的横切链

以下路径相对后端仓库；`UI/` 表示相邻 `mes-base-ui/`。行号以本次工作树为准。

| 链路 | 本次确认的实现及局限 | 精确证据 |
| --- | --- | --- |
| HTTP 授权与工厂 | 每方法显式访问分类，完整 URL 段区分平台/共享/业务；选厂头不授予权限，业务使用当前 Factory 授权 | `EndpointCatalog.java:10,30`、`FoundationAccessInterceptor.java:51–115`、`EndpointScopePolicy.java:10–48`，均在 `mes-framework/.../platform/web/`；抽查正式报工、质量处置、仓储审核及进度 Controller。角色部署矩阵待验证 |
| 请求与固定事务 | 管理绑定准入后安装不可变请求快照；事务 begin 取得租约，访问与提交核对固定身份，禁止跨 owner；请求结束清理上下文 | `mes-admin/.../managed/ManagedDatasourceService.java:475–479`；`mes-framework/.../persistence/FactoryBusinessDataSource.java:30–53`、`OwnedTransactionManager.java:29–69`、`PersistenceStatementGuard.java:29–32`、`platform/web/RequestBoundaryFilter.java:49–83`。未实测两厂并发与池退休 |
| 缓存 | 业务 key 必须带 Factory；只读事务命中前后校验租约，写事务不发布查询结果；成功提交后失效。目录用户白名单不含凭证与权限 | `BusinessCacheAspect.java:65–78`、`BusinessCacheProcessor.java:51–60,124–137`；`business/directory/service/BusinessDirectoryService.java:57,203–229,296–305`。Redis 当前行为待验证，不泛化成所有缓存无风险 |
| 同步执行权 | 协调表 claim/progress/finish 检查令牌；当前工作站 upsert 未接收 Run，只有写前后外层 progress。九类同模式仍存在，接真实 writer 前必须整改 | `framework/sync/SyncCoordinator.java:42–93`；`basic/service/impl/WorkstationServiceImpl.java:191–217`；另搜索 Material/MaterialCategory/SafetyStock/UnitMeasure/Workshop/DvSubject/Operation/Routing 的 claim/upsert，及 `mes-generator/.../base/service-impl.java.vm:139–162`。对比库存、仓库 writer 已传 Run 并检查 beforeCommit。九类外部 QueryPort 当前默认显式失败；未证明部署发生过失权写入 |
| 前端范围清理 | 请求头、成功体、错误体、blob 后校验 token/Factory/revision；旧 401 不应清除新上下文。工作台按权限/Factory/revision/卸载同步失效请求序列 | `UI/src/lib/request.ts:5–9,31–36,69–71`；`UI/src/composables/useBusinessWorkspace.ts:35–75`、`useWorkOrderProgress.ts:22–55`；`UI/src/stores/auth.ts:158–225`。源码成立，当前浏览器撤权/切厂待验 |

测试源码已检查 `RequestSnapshotTransactionTest`（49、97、148行）、`AsyncRequestBoundaryHttpTest:136`、`DetailEntityCacheTest:62,88,102` 和 `UI/tests/request.test.ts`。它们分别含固定事务、异步清理、缓存及迟到响应场景；**本轮未运行，不能引用历史通过替代当前结果**。

### 13.3 当前确定缺陷与待复现风险（按首厂影响排序）

#### PLAN-01：普通出入库页生产来源与类型契约断裂（确定缺陷，静态可达）

- 触发：从出入库菜单新建默认“其他入库”，选择生产 Execution 后保存。
- 当前行为：`UI/src/views/wm/stockDocuments/index.vue:58` 的自定义槽只给 OTHER_RECEIPT/OTHER_ISSUE，59行只在 OTHER_RECEIPT 启用生产选择；`UI/src/api/wm/stockDocuments.ts:49` 原样发送类型。后端 `wm/service/StockDocumentService.java:162–170` 明确要求带 Execution 时类型为 PRODUCTION_RECEIPT，否则 BAD_REQUEST。
- 影响：普通页面无法按提示建立生产入库草稿，也无法选择采购等已有类型草稿。违反“正式页面可达”与入库来源类型区分目标。后端拒绝是正确门禁，不应放宽。
- 边界：`ProductionClosurePanel.vue:42` 深链可预置 PRODUCTION_RECEIPT/Execution（页面32–34行）；不能据上述问题断言所有生产入库路径都必然失败。其类型回显、禁用选择及保存仍待浏览器验证。
- 测试与修复：`UI/tests/stockDocuments.test.ts:38` 手工传正确 PRODUCTION_RECEIPT，仅验证 API，漏了页面槽。最小方向为页面使用已有类型目录、按类型启用来源选择并清理不匹配来源；质量类型仍只能由质量处置生成。验收菜单与完工深链两个真实入口，以及草稿零动作、审核一次入账及不支持类型拒绝。

#### PLAN-02：现场报工结果未确认时可丢失原命令（确定恢复缺陷；重复事实待运行复现）

- 触发：服务器可能已提交报工，但客户端响应丢失；随后再次定位、刷新或离页后返回。
- 当前行为：`UI/src/views/production/productionReporting/index.vue:20,32–40` 仅用 ref 保存 pending，locate 直接 clear；76–77行输入/定位按钮未按 pending/busy 禁用。51行后续提交新建 UUID。同页网络/5xx 重试保留原命令（62–67行），但跨定位/页面没有恢复。
- 影响：若还有可报额度，使用新 UUID 可再次追加同一物理完成数量；`production/service/ProductionReportSubmitService.java:113–119,155–161` 的原 UUID/payload/actor 重放不能识别新 UUID。不是宣称所有重试必定重复，也不是后端无幂等。
- 测试与修复：`UI/tests/productionReporting.test.ts:40–44` 只保同页重试，55–62行还期待切执行清空 pending。投产页32–39、75–83行已有按账号/厂 sessionStorage 恢复，不能泛称所有命令均缺恢复。最小方向复用已有恢复惯例，持久化冻结命令并在核实前阻止替代提交；切厂时只清页面数据，旧命令隔离保存。验收提交后丢响应、再定位、刷新、重新登录/切厂/撤权及同 UUID 不同内容冲突，不盲目重试 ERP 创建。

#### QA-02 / QA-11：引用创建与物理删除边界（当前风险保留）

- QA-02：`wm/service/StockDocumentService.java:215–240,404–418` 仅锁主单后普通读库存再插明细；`wm/service/impl/WmInventoryServiceImpl.java:202–219` 锁库存、检查引用、删除。零余额库存可能在读取与引用提交之间被删除，形成草稿孤立引用。仍为待真实双事务复现风险，不声称已破坏过账余额。最小方向共同锁协议和稳定锁序，覆盖新增/换来源对删除，不能只补按钮。
- QA-11：`basic/service/EquipmentReferenceRules.java` 的工作站删除保护只检查 equipment；`production/service/ProductionReportSubmitService.java:126` 只确认工作站存在，随后写入报工引用。须同时保护已有历史引用和并发创建；精确生产链见下节。本轮未删工作站，未复现竞态。
- 两项验收均应包含无引用允许、已有事实拒绝、并发只能一方成功、原历史仍可查。不通过增加业务表 factory_id 或绕过既有 Mapper 解决。

#### QA-04：完整响应超时（本轮隔离探针重新确认）

`UI/src/lib/request.ts:28–30` 在 fetch 响应头完成后清除 timer；错误 json 与成功 text/blob 在其后。主 Agent 使用当前源文件、TypeScript 本地转译、内存 fake fetch + ReadableStream，立即响应头、1300ms 后给体，`timeoutMs=1000`：JSON 1323ms 成功、错误体1302ms后 HTTP_ERROR、blob1302ms成功；三者 aborted=false。无网络、无凭证、无临时文件、无业务写入。第一次误用默认 Node16 缺少所需 Web API，探针 TypeError；切已安装 Node22.23.1后上述三例完成，未更改全局 Node。

这是**当前源码的隔离契约证据**，不是浏览器/代理实测。现有 request 测试有体后切厂守卫，没有体延迟截止时间用例。最小修复应覆盖普通响应体和错误归一化，并为 SSE 保留独立长连接生命周期；验收头快体慢、错误体/下载卡住及切厂中断，不丢失命令原身份。

### 13.4 工作台与首厂运维的当前证据

`UI/src/composables/useBusinessWorkspace.ts:9–18` 的草稿/已下达工单、READY/RUNNING Execution、近期报工和不良流水均调用正式 API。`RecentProductionReportController.java:28–31` → `RecentProductionReportQueryService.recent` → `production/dao/RecentProductionReportExtraMapper.xml:4–23` 只取最近10条，再关联工单/产品/单位/工序；质量近期流水 `MesQualityTreatmentMapper.xml:103–106` 亦限10条，不能据它们推全厂不良率或日/月合格产量。

工单进度入口 `WorkOrderProgressController.java:39–44` → `WorkOrderProgressQueryService.java:43–67` → `WorkOrderProgressExtraMapper.xml:4–56` 使用正式完工事实及最新未VOID申报。完成率以正式完工/计划计算；`pendingCompletionQty=max(计划−正式完工−转仓量,0)`（Service112行），**不是生产在制数，也不是报工进度**。未知/不一致处置返回 null 和 issue（96–109行）。工作台可钻到执行数量和工单，但没有由当前代码证明延期风险、人员/工作站日周月有效产出、贯穿全部质量与库存的统一时间线已交付。这些属新增功能缺口，不要求重做已有页面。

已实际阅读 `scripts/mes-business-flyway:5–43`、`docs/development/database-upgrades.md`、`UI/deploy/nginx.conf.template`、`scripts/smoke-packaged.py` 与 `platform/web/RequestBoundaryFilter.java:53–55,89–96` / `HttpFailureLogging.java:24–45`。已有受控版本升级、备份示例、反向代理及 requestId 定位基础。烟测脚本会启动进程，因此本轮未执行；升级/数据维护文档的当日执行记录都是此前工作，未在本轮重验。没有找到完整首厂恢复演练证据（检索 docs/development、reviews、operations、README、scripts 的 backup/restore/恢复演练/RTO/RPO/交付）；这只说明当前交付证据不足，不断言环境没有备份。首厂需覆盖中心库+业务库+私有文件/对象存储、恢复所需密钥的受控保管、应用及迁移版本和恢复后事实/库存核对，本轮不读取任何密钥。

下一阶段与 A—F 的处置、三个工作包见[计划当日复核追加](../development/next-stage-plan.md)。没有自动实施。


### 13.5 生产主链复核与能力边界

实际检查前端productionInput/productionReporting、ExecutionActions、WorkorderMaterialPanel、LocalStockPanel及API；后端对应Controller、WorkOrderService、ProductionExecutionRules、ProductionInputSupply/ProductionInput、ExecutionPath、ReportSubmit/Report、资格规则、生产链锁、完工服务/Job、issue提交/事实/撤销、LocalProductionSupply/LocalStock；Workorder/Execution/Report锁和UUID SQL。未逐一穷尽Routing发布、工单作废负例、每项CRUD缓存、全部资格维护页面及数量SQL；没有运行测试/接口/数据库。以下路径从`mes-business/src/main/java/dev/mesbase/business/`起。

| 能力 | 当前判定及正式调用链 |
| --- | --- |
| 工单、独立Execution、工艺版本/路径 | 已有。`production/service/WorkOrderService.java:300–332,359–406`草稿/修订/下游保护；`ProductionExecutionRules.java:102–135`拒普通编辑状态、历史路线/目标改写；`ExecutionPathService.java:159–188`校验实际DAG路径，已有领料/锁定/质量历史后不可重选，201–207正式领料确认锁定。模板分支不等于Execution同时走多条路线 |
| 扫码正式投入 | 已有。UI productionInput:75–79→ProductionInputController:56–89→ProductionInputService:148–170原UUID重放/重算calculationKey→173–215锁工单、绑定正式扫码身份、创建Execution/选路→218–245保留计划额度、建一条领料明细与InputFact→`issue/service/MaterialIssueSubmissionService.java:235–242`→MaterialIssueInputFacts.confirm:56–68→LocalStockService.issue。当前新建写正式批次身份，不能用旧样本缺身份直接判定新链同样缺失 |
| 用料/BOM | 单材料投入已有，完整BOM/齐套投产未实现。WorkorderMaterialService:43–45,112–126每工单一个材料需求行；ProductionInputSupplyService:93–104正式计算拒多个不同材料，同材料可求和并校验单位/仓库；ProductionInputService:228–245正式扫码只建一条issue item。行政领料允许1–100明细（MaterialIssueSubmissionService:156–163），但无InputFact时confirm直接返回（InputFacts:56–65），不贡献产品投入或扫码库存扣减；不能称多材料生产已实现，也不能把行政单确认当扫码扣库验收 |
| 完成报工/不强制派工 | 已有。UI productionReporting:48–67→productionReporting API→ProductionReportingController:64–68→ProductionReportSubmitService:109–152。可信登录人取SecurityUtils；`ReportOperatorEligibilityService.java:44–51`能力必需、无派工可报、有派工才限本人；`OperatorQualificationRules.java:100–105`能力行仍必需，不等同持有报工权限 |
| 首报开工、关闭与历史 | 已有。SubmitService:128调ExecutionPathService.start:225–263，READY/RUNNING、工单已下达、精确工艺、锁路径、正式CONFIRMED领料，CONNECTED还要库存可信生效。首报自动履行正式开工合同，无需新增开始/结束两次操作。ProductionChainLocks:124–154先工单后Execution；Mapper XML各103/104行FOR UPDATE，串行化同工单事实 |
| 正负报工/重放 | 已有。SubmitService:113–119锁前后查原UUID，155–161核同执行/节点/数/人/站；成功重放允许之后关闭/资格变化但不添事实。管理纠正追加ADJUSTMENT，不原地改旧报工。未知页恢复缺口见PLAN-02；未验证真实竞争 |
| 手动/自动完工 | 已有，不重排为待开发。ProductionCompletionService:94–113检查数量守恒/质量处理安全/未决领料；118–137手动LOCAL、UUID+basis；142–171自动执行权检查；174–198不可变完工事实。WorkOrderCompletionJob:63–79提交后异步提示并10分钟扫描补漏，94–110独立线程重建上下文。异步部署/调度时延待验，不把事件发布等同已经完工 |
| 工作站 | 部分实现。SubmitService:126–127与ProductionReportService:94–95只查存在，正式事实可保存workstationId；现场页51行固定null。WorkstationServiceImpl:151–164→BasicDataRules→EquipmentReferenceRules:44–51只检查equipment引用，漏不可变报工引用。已用站可能被删是确定保护缺口，并发报工/删除仍待复现；先共同锁/历史引用保护，再开放现场选择/启用资格 |
| 退料/领料撤销 | 未开工无事实的原LOCAL库存退料和净额整单撤销已有；开工后余料退回未覆盖。退料保持产品投入额度是现行明确政策，不把材料数直接减产品数。首厂需明确该组合能否满足实物管理，详见仓储专题；未获新业务决定不直接改算法 |

已阅读ProductionReportingHttpTest、ProductionInputMySqlTest、ProductionReportMySqlTest及UI productionReporting.test.ts；存在可信身份/重放、超报、锁共享、回滚及分支样例，均未在本轮执行。真实库测试带数据写入及结果文件，故未运行。部分前端测试明确锁定丢pending的当前行为，应在PLAN-02修复时同步替换，不能只追求旧测试继续通过。

### 13.6 质量/仓储交叉复核与结论修正

主 Agent 重新读ProductionQuantityService:303–334、ProductionReportService.returnToNode:165–208、QualityDispositionService:90–159、QualityReleaseService.stateFor:81–104、StockDocumentService.productionBasis:336–351及ProductionCompletionBalance:14–26，复核子Agent之间的数量/来源/事务结论，未发现需要通过另建生产核心解决的矛盾。

- **QA-03补证：** QualityTreatmentService:135–148单执行1000最新申报/5000全部事件，先限额后VOID过滤；281–305批量是整批共享预算；返回申报工序还受ProductionReportService:170–173的10000节点报工预算。属确定源码限制和首厂容量风险，规模/响应影响待验；质量测试现有断言上限拒绝，并非只补测试就能解除。
- **QA-05保留：** 数量查询仍经质量动作Service回环；不是启动故障。改为同一有效事实投影与独立分页历史，保留唯一算法和严格写边界。
- **晚质量部分实现：** 允许新增记录、旧放行失效、旧入库事实保留且后续入库被挡；已完工工单的待核实申报不能走现核实/处置，属于关闭后独立质量业务缺口，不能简单放开关闭门禁。100/10/返回4/重报/再次不良/晚申报/已入库数字详见[质量当日复核](2026-10-18-quality-v2-gap-review.md)。没有恢复历史废弃流程建议。
- **质量入库已有：** 同LOCAL事务创建正式单据/明细/处置/审核；“尚缺后续生命周期”不等于“报废入库未实现”。十类型、草稿/审核/执行者/正式入口矩阵和来源隔离/ERP证据见[仓储当日复核](2026-10-17-local-stock-documents-v22-report.md)。POSTED统一单据没有正式冲销入口，现reverse是领料净额逆向，不能借用为质量库存核销。
- **ERP分层：** 主Agent再次检索implements WarehouseErpAdapter，main仅LocalWarehouseErpService，供应商替身在test；WarehouseIntegration唯一匹配不到时失败关闭。E10真实Query/Read和候选写入源码已有，但本轮没有真实环境契约/调用证据。人触发原身份查询核实不等于已有通用人工裁定入口；不把这些缺口变成LOCAL交付依赖。

QA-01/07/08/09、QA-06环境轮换及QA-12 lint此次未全面重验，保留历史时间线，不重判全部待开发或全部当前通过。QA-10当前静态消费者风险存在，默认未接真实查询不能宣称已发生失权提交；应在对应同步接入前验收。未按模块/页面/测试数量计算完成比例。

### 13.7 文档与验证回执

本轮主Agent仅更新本台账、既有质量/仓储专题和next-stage-plan当日章节，保留原日期段落。执行了工作树/HEAD/源码/测试源码/文档搜索及当前request源文件隔离探针；四个本轮文档diff空白与新增本地链接目标检查通过；三个专题原正文前缀完整保留。前端关键文件差异量与开始一致，后端原有无关文档/operations差异保留。没有运行当前业务测试、构建、lint、数据库读取/写入、真实业务/ERP请求、浏览器、进程启停、提交、推送或部署。当前可交付判定是**LOCAL单材料候选范围，待关键修复与真实验收后放行**，不宣称首厂已经可上线。


## 十四、2026-10-23 Execution 原有完结门禁专项核对（只审查）

用户要求核对原门禁，本节不实施新增入库检查。后续讨论已明确不良模块只负责申报及入仓，仓内物流归仓储；专门返工能力拟退出当前范围，但代码尚未调整，以下仍如实描述当前实现。用户提出的是保留原检查并增加“是否已审核入库”，并未要求全部产出入库或重做数量算法。

### 14.1 真实入口与共同检查

- 手动：ProductionCompletionController的POST `/api/production/executions/{id}/actions/complete`，需`business:production:execution:complete`权限 → ProductionCompletionService.complete（118行）。
- 自动：末道正式报工提交后发布ProductionCompletionRequested（ProductionReportSubmitService:150），部分报工调整及质量变化也发布事件；WorkOrderCompletionJob在提交后异步处理，另每10分钟扫描补漏 → ProductionCompletionService.reconcile（142行）。自动路径不冒用HTTP登录权限，以工厂受管配置和数据库run token执行。
- 两条路径均调用ProductionCompletionService.summary（94—113行），并在实际落完工事实前调用ProductionExecutionRules.checkReadyReferences（138行）。普通Execution编辑禁止更改status/startTime/endTime（ProductionExecutionRules:111），不能通过基础编辑跳过正式完结。

当前业务条件：

| 原门禁 | 当前代码条件 |
| --- | --- |
| 执行状态 | 必须RUNNING |
| 实际路径 | LOCKED且节点非空 |
| 正式投入 | confirmedInputQty必须大于0，reservedInputQty必须为0 |
| 质量安全 | 无待核实观察；申报归属不能UNVERIFIED；独立不良pendingDispositionQty必须为0 |
| 数量合法 | 各节点rawEffectiveOutputQty不得为负，unverifiedDefectQty为0，netDefectQty不得为负 |
| 数量守恒 | confirmedInputQty = 末道effectiveOutputQty + 各节点netDefectQty之和；不是各工序报工累加，不以计划数量或入库数量替代 |
| 领料无未决 | 关联领料仅允许CONFIRMED / VOIDED / SUBMIT_REJECTED，其余状态阻断 |
| 工单与引用 | 来源/当前模式/相关仓库符合既有BusinessSourceRules；工单必须RELEASED，产品存在，工艺当前为PUBLISHED且有发布时间、适用于工单产品 |
| 不重复完结 | 已有正式完工事实不能再次创建；原请求重放按原指纹返回 |

数量检查证据：`mes-business/.../production/domain/ProductionCompletionBalance.java:13—24`。质量安全证据：`mes-business/.../quality/service/QualityReleaseService.java:81—104`。**safe与basisValid不同：完结只要求safe，不要求已经建立有效的正式质量放行记录，也不要求released等于effective。**

### 14.2 两条入口的额外保护与入库关系

手动入口：UUID、原因、可信操作者指纹；工单→Execution锁；同UUID原样重放；显式LOCAL_ONLY模式；expectedBasisHash必须等于当前质量/数量依据。自动入口：核对工厂及workorder-completion执行权，多次assertOwner；有最新报工且recordedBy非空；生成稳定自动完工UUID，事务内追加事实及更新状态。全厂自动扫描仅处理LOCAL；不是通过页面按钮执行。

**现有门禁没有“是否入库”检查，也没有“全部入库”检查。** ProductionCompletionService.append:181在已决定通过后，才调用StockDocumentExtraMapper.netProductionInbound记录acceptedQty，允许0；它是完结时快照，不是门禁。自动测试源码AutomaticProductionCompletionHttpTest:65的用例明确以“无正式放行/仓储也能自动完结”为当前合同，随后才办理入库。本轮只读该测试，没有运行。

入库独立条件：StockDocumentService.productionBasis:336—351及productionExecution:354—359均允许RUNNING/COMPLETED，所以入库不要求先完结，没有天然循环；但必须满足当前正式质量放行、来源、产品及剩余额度规则。

若后续实施“增加是否入库”，应放入共同业务判定，让手动与自动一致。注意当前approve完成后未见发布ProductionCompletionRequested；增加门禁后，报工触发可能先被拦住，审核入库后需重新触发检查，或由现有10分钟扫描补漏，不能只增加条件就声称会立即自动完结。本节不实现任何一种方案。

当前netProductionInbound统计已审核且与库存动作对应的生产入库/既有OTHER生产来源入库，加既有净仓储接收；不含当前不良/报废质量入库。若“已入库”将质量入库也算在内，需要按正式质量事件→单据→库存动作关联定义，而非直接复用该数值。全不良、部分入库时是否满足用户的存在性门禁留在具体设计中明确，本轮不擅自改为全量闭合。

### 14.3 验证及文档回执

实际执行源码/引用检索并读取手动与自动服务、任务、数量/质量规则、来源/引用校验、库存入库与统计SQL、对应测试及前端入口源码；未运行测试、构建、浏览器、数据库或服务，不宣称实时环境通过。仅追加本台账第十四节，保留既有dirty内容；未修改业务代码、测试、配置、SQL或数据，未启停服务、提交或推送。


## 十四、2026-10-23 实施追加：不良入仓与入库驱动收尾

后续用户决定取代§十三及旧计划中的新返工扩展、末道报工自动完结和定时扫描。实施权威见next-stage-plan顶部用户确认及第七节；原审查时间线保留。

- 新Execution FULLY_RECEIVED与旧COMPLETED分离；普通CRUD不得设置或变更新终态，已收尾执行路线/目标及领料、投入撤销继续冻结。Execution列表/详情缓存经原提交后失效机制清除，工单/进度当前查询不引入第二份缓存或第二套生产算法。
- 库存审核和质量内部审核同LOCAL事务、同父工单→全部Execution→单据/库存锁序，在动作/POSTED/质量事件可见后统一收尾；使用唯一G/D、严格R和逐申报Q证明，条件不足与真实错误区分。工单按非空/全部执行及R+D=P判定，不修改计划、不增加短结。
- 专用WorkOrderCompletionJob、WorkOrderCompletionExecutorConfiguration、QueuedCompletionExecutor测试替身、ProductionCompletionRequested/WorkOrderCompletionRequested及对应失效测试/候选SQL退役；报工/调整/质量申报/核实不再发布终态提示。公共SyncCoordinator/Quartz未删、未用异步队列补漏。
- 老complete只核实/重放原精确身份，不新建；新的显式recheck-receipt只依据可信已审UUID复用唯一规则，填补合法入库后普通阻断解除且无后续审核的恢复路径，不重复库存/放行，不让无正式审核的对象关闭。沿用显式授权/Factory固定事务/引用保护。
- 工作台完成数与百分比改为新FULLY执行的R+D贡献；好品缺口按P-R，待收尾差额按P-(R+D)，不再重复加Q；历史COMPLETED及其completion事实不冒充新完全入库。质量不可解释派生量继续待确认/null。
- 前端移除新RETURN/旧manual complete命令与按钮，保留历史及原未知请求只读核实；正常入库类型/Execution契约修正，切厂/撤权/迟到响应测试覆盖新收尾/复查；独立的报工未知恢复、完整响应体超时、工作站引用和质量容量问题未捆绑修复。

主Agent复核了子Agent共享Service与SQL、正式放行在正常/质量反序时的门禁、质量事件先于审核、并发原UUID在胜者收尾后仍可重放，以及恢复查询两路证明。首轮隔离用例暴露并修正零数量测试fixture、测试Factory上下文和record SQL列序错误；完整检查发现旧ProductionChain测试仍期待有Execution时可改工单路线，按现有WorkOrderService冻结规则更正，并补CRUD不能设置FULLY_RECEIVED的负例，未放宽生产保护。

详细行为、测试结果、真实/替身边界、Schema与历史影响、重启后复验及限制在计划第七节、仓储/质量专题当日实施追加维护；没有新建平行报告。未提交、推送、部署或启停用户服务。

本次验证收尾：最终完整verify共886项（837执行通过、49条件跳过），0失败/0错误并打包；UI76文件454项及build通过。用户随后指定默认真实测试库mes_base_dev_perf_test，两仓AGENTS已写入；重新核实TEST_DEV_PERF_20261019/DS9 revision2/LOCAL后，当前Mapper真实MySQL只读测试通过。最初误选SIMPLE_B的V48失败仅保留检查历史，无数据写入，不作为指定测试库缺陷。真实业务审核写入及登录后的浏览器闭环未验收。

## 十五、2026-10-23 用户重启后指定perf库正式验收

本轮在mes_base_dev_perf_test/TEST_DEV_PERF_20261019/DS9 revision2/LOCAL/V51完成4张独立工单1916—1919、执行12368—12371的正式入口真实验收：正常60/40（真实页面菜单及深链）、R90先/Q10后、Q10先/R90后、全不良100（无零量正常单），均仅由入库审核触发FULLY_RECEIVED和父COMPLETED；进度贡献100、待收尾差额0。原审核UUID重复、3笔质量入仓原命令重放及同UUID改内容拒绝已实际验证。写前后13张事实表338831条原记录存在且摘要相同，独立测试事实保留。无直接SQL业务写、业务源码变更、迁移或服务启停。

本轮新增权限证据：新测试工序的V2类型配置create被403拒绝，未改权；质量场景通过当前正式事实申报API完成，不冒充V2页面类型化申报已验。真实并发、故障注入等范围没有扩大。请求/对象、数据保护与截图见next-stage-plan第八节及仓储/质量同日真实验收追加，旧实施阶段未真实写入的记录保留为当时时间线。
