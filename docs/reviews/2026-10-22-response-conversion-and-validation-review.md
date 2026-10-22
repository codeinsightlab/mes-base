# 2026-10-22 响应转换、空值与通用校验审查

## 决定与范围

用户最终确认：保留生成的XxxPage（Entity查询结果）和XxxPageVo（HTTP响应）；不能把查询结果封装误判成重复VO转换。移除逐字段get/set及同名构造器搬运，Controller统一调用属性和分页转换函数。此前试行的直接返回PageHelper及其缓存适配已撤回，25个生成查询模型和page模板均保留。没有执行提交、推送、用户服务启停或真实ERP调用。

前后端AGENTS均追加规则：禁止空值传播三元表达式；可空数据保留真实空值，必需引用明确校验；复用字符串工具及ParameterChecks，不在每个Service重复定义trim/text/paging/invalid/conflict。领域状态、权限、来源、事务、幂等以及对应错误码继续由业务链路负责。

## 实施

- 公共PageData统一查询/响应形状，不依赖分页插件。BeanConversions使用现有Jackson 3按目标类型映射，支持record及嵌套泛型集合；convertPage保留total/offset/limit。
- 普通生成CRUD的列表、详情、新增、更新及系统响应Assembler复用公共转换。工单进度计算仍在Service，数值和ID不在Service格式化为展示字符串；Controller统一转换。质量、执行、库存等批量名称派生字段通过明确的辅助record补充；转换不产生额外查询。
- 生成器保留page.java.vm与page-view.java.vm，未来生成Controller同样使用公共转换与通用分页校验。未修改SDK、数据库Schema或业务数据。
- 真实null不再转换成字符串"null"；对应附件/消息的空对象契约测试明确更新这一预期，非空ID及其余字段继续与旧投影比较。
- BigDecimal转换为普通十进制字符串，Long不经过double；Date复制毫秒时间，HTTP日期格式留到最终输出。新增测试验证超出JS安全整数的ID、六位小数、科学记数表示、嵌套record、空页、空值、日期格式差异、原始类型必填失败及原有生成模型。
- 通用校验保留文本长度、必填性、分页上限与调用方指定的错误码；StringUtils.trim(null)与Apache trim(null)语义不同，质量请求保留原null语义。新增Java语法级约束测试，覆盖多行及反向空值三元式。
- 已发布V34迁移不纳入字符串风格改造，其无关工具替换已还原。保留并行仓库分类改动，不将其作为本轮交付。

## 验证结果

- 属性转换/参数校验/分页缓存及质量、进度HTTP定向测试通过。
- 系统响应字段、日期、空值、敏感字段与HTTP契约定向测试通过。
- 第一次全量verify的各模块测试均零失败、零错误（admin584项中48项按环境跳过）；打包阶段共享classes中的文件暂时缺失导致组包失败，随后该文件重新出现，不能将这次verify报告为成功。
- 最终隔离副本 `/private/tmp/mes-response-review-final` 基于 `17a3659` 加本轮任务文件构建，排除尚在并行开发的仓库分类改动；不代表这些并行改动已验收。使用现有本地开发配置完成Profile绑定测试，配置内容未输出；仅为测试进程选择本机原生Redis。
- 在该副本执行 `PATH=/opt/homebrew/bin:$PATH /Users/user/apache-maven-3.6.1/bin/mvn -B -ntp verify`：BUILD SUCCESS，837项、零失败、零错误，48项按环境跳过。日志：`/tmp/mes-isolated-final-verify.log`。
- 在该副本执行 `/usr/bin/python3 scripts/smoke-packaged.py`：10项通过、零失败。探针使用独立临时端口及自身进程，未重启用户服务。日志：`/tmp/mes-isolated-final-smoke.log`。
- 前端执行 `npm test`：73个测试文件、424项通过；`npm run build`通过。日志：`/tmp/mes-review-ui-tests.log`、`/tmp/mes-review-ui-build.log`。
- 真实数据库、ERP及用户运行服务验收不在本轮测试证据中。
