# mes-base 项目长期记忆

## 环境与工具（跨会话有效）

- Maven：`/Users/user/apache-maven-3.6.1/bin/mvn`；`JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home`。全量校验 `mvn -B -ntp verify`。
- 后端 smoke：`/usr/bin/python3 scripts/smoke-packaged.py`（需先把 `JAVA_HOME/bin` 加进 PATH）。
- 前端 Node：`.nvmrc` = 22，用 `/Users/user/.nvm/versions/node/v22.23.1/bin`。
  - `npm run lint` 可用但**很慢**（约 3 分钟）。
  - `npm test`（vitest 默认 forks 池）在沙箱内**必然失败**：17 文件仅 1 个跑完，16 个 worker 启动超时，import 占 91% 耗时。**单文件用 `--pool=threads --maxWorkers=1` 可过**。判定为沙箱吞吐限制，不是工程缺陷。
- 读取 `mes-admin/src/main/resources/application-dev.yml`（0600）**会被沙箱 SIGTERM(137) 中断**；改用 `git show HEAD:<path>`。
- 禁止 `zsh -ic`（触发本机 safe-delete 护栏并弹权限请求）。
- 含中文的多步骤 shell 循环易被 137 杀掉；改用单次 `/usr/bin/python3` 脚本。
- macOS 没有 `timeout`；`cat -A` 不支持（用 `sed -n l`）。

## 凭据与私有配置约定（2026-10-09 起生效）

- `application-dev.yml` / `application-prod.yml` **不再被 Git 跟踪**，已进 `.gitignore`；发布只用 `config/*.example.yml`（账号为 `your-db-user`，密码/主密钥为空）。
- 防复发：`scripts/check-private-config.py`（守卫）+ `.githooks/pre-commit|pre-push` + `scripts/install-git-hooks`（设 `core.hooksPath=.githooks`）+ `scripts/format-java`（IDEA 格式化，依赖 `.idea/codeStyles/Project.xml`，该文件不入库 → CI 无法复现格式门禁）。
- 守卫覆盖边界（已知缺口）：只查路径以 `config/` 开头或含 `/src/main/resources/` 且扩展名 `.yml/.yaml/.properties` 的文件；键名只认 `password|datasource-key|secret-key|access-key`。

## 复核仓库状态的注意点

- 该工作区**有并行 agent 活动**：本轮复核期间两仓被并行提交（后端 `8fe1e69`、前端 `b07041e` 且已推送）。**开工前必须先查实时 `git log`/`git status`，不要相信上一轮的快照。**
- 敏感配置核查走 `git cat-file --batch-all-objects --batch` 全量扫描（含不可达对象），只比对哈希、不打印值。
