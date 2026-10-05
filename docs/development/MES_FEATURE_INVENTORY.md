# MES Feature Inventory

> 2026-10-06 当前：Phase 1 全量基础系统迁移持续执行，六模块与 Source 系统页面已接入，整体 MIGRATING。下方 Phase 0 计数/候选矩阵为历史盘点；当前进度以[续接快照](MES_BASE_REALIGNMENT_REPORT.md#2026-10-06-登录隔离与浏览器工作包续接快照)和[验收记录](../reviews/2026-10-06-phase1-verification.json)为准。

状态：Phase 0 静态盘点；2026-10-05。产品母体为ktg-mes/ktg-mes-ui/heri-uni-app，所有现有功能默认进入候选。无代码迁移、无旧服务/数据库操作。策略是待实施分类，不是许可结论或业务验收。

证据入口：[机器可检索的源码清单](../reviews/2026-10-05-mes-realignment-evidence.json)、[迁移矩阵](MES_MIGRATION_MATRIX.md)、[方向重审](MES_BASE_REALIGNMENT_REPORT.md)。清单保存Controller/Service/Mapper/Entity/XML SQL候选、Web API/页面字段/权限、PDA页面注册与API关联；不收集配置值、凭证、数据库记录。

## 1. 覆盖范围与证据等级

扫描旧后端main Java 2937文件、Controller候选 427（含BaseController等非HTTP基类）、Mapper XML 439，Web views 250个Vue文件（含子组件），PDA pages 37个Vue文件、两端API 204文件，`sql/`中23个含CREATE的脚本。Base当前91个main Java、15个package、5个views；没有正式MES业务模块。

这些计数不是产品功能数；Wheel镜像、Web/PDA输送入口和主从表分别保留来源，再归并为259个后端功能族。归并只整理候选，不认定Wheel与主链路语义相同。原项目长期运行是用户提供的背景；本轮仅源码存在性/链路静态核对，没有证明每个功能在现场完整可用。历史DDL不等于当前部署schema；动态菜单未查询，不把Vue文件存在当作菜单可达。

本轮特有边界：旧Web `system/openApiClient/index.vue`已有用户修改；PDA未跟踪log/和pkg-business等均保留。pkg-business当前只有.DS_Store，所给PDA仓库未找到热处理/现场检验对应页面，后端移动接口存在须标为“后端有、该PDA未找到”。

## 2. 产品能力摘要

| 类别 | 旧产品已经具备的源码能力 | Base差距与迁移判定 |
|---|---|---|
| Platform / System | 用户资料/部门岗位角色菜单、数据权限、密码、字典参数、公告消息、登录/操作日志、在线用户、附件、导入导出、流水号、Quartz、Codegen管理、Swagger、Druid/服务/缓存监控 | Base有简化用户/角色/菜单/字典/参数/日志/文件/任务及CLI；部门岗位公告在线、导入导出和监控页缺失。PORT；通用工具COPY候选。 |
| MES Master Data | 单位、物料分类/物料/产品、客户供应商、产品BOM/SOP、车间/工作站及设备/人员/工装资源 | Base无；以旧字段/查询/选择器迁入，不设计新Material Domain。PORT。 |
| Calendar / Equipment / Tooling | 班组成员、节假日、排班计划班次日历、设备类型设备、点检项目计划、维修及工装 | Base无；成熟CRUD与操作入口PORT；点检/维修流程继承并做范围适配。 |
| Production | 工序/内容/成员、工艺路线组成/产品/制程BOM、任务及投料、工单及BOM、月计划/工单申请、排程甘特、报工、流程卡数量/追溯、扣料、物料确认/入库确认、步骤检验 | Base无；路线/工单/任务主体PORT，报工和外部副作用局部REDESIGN；不凭名称整体重建。 |
| Quality | 检验项/模板/适用产品、IQC/IPQC/OQC及明细、缺陷及检验缺陷记录、不良申报、原因、步骤检验、现场检验记录/看板 | Base无；检测定义/记录PORT；责任人/扣款部分核实拆出，工资不迁。 |
| Material / Warehouse | 仓库库区库位、库存、入库/领料/退料/消耗/产出/销售出入与退货、盘点、调拨、装箱、条码/SN、托盘、条码拆分合并 | Base无；标准单据与库存规则PORT；ERP提交/审核边界REDESIGN。 |
| Planning / Dashboard / WIP | 月计划、需求、安全库存、MRP、最低采购、工单需求、产量月汇总、生产监控/大屏、质检看板、SQLServer WIP与模拟WIP | Base无；计划候选PORT，WIP真实/模拟区分REVIEW；公司产值/天气/安全展示不默认等于MES核心。 |
| ERP Integration | 主数据/批次/BOM/库存/工单查询同步、生产入库申请/确认/撤审、领退补料、调拨、报废、条码事务；手工与定时同步 | Base仅未配置失败的接口壳，无真实业务适配。保留旧产品能力，供应商协议/连接和MES规则分开；REDESIGN有证据边界。 |
| Mobile / PDA | 账号资料公告、工作台、扫码报工/不良/扣料、入库申请、调拨审核、流程卡追溯、委外、托盘、打印、条码拆分合并、网络/打印设置、主厂与Wheel页面 | Base无PDA；保留现场操作次序和字段，PORT；设备/网络/打印为真实PDA待验收，不能用Web替代。 |
| Other | UReport/HiPrint及标签打印、编号查询、业务通知、OpenAPI客户/路由授权/令牌、销售询价/订单/价格、工资结算和专用演示 | 打印/通用API/通知候选保留；销售可选REVIEW；工资/责任扣款和显式测试接口DROP专有部分，不能丢报工通用流程。 |

## 3. 基础设施与系统设置逐项盘点

按用户最新指令，业务只保留第2节模块级盘点；下列聚焦基础设施。后端259个功能族及未关联条目作为机器候选清单保留，不作为已完成逐业务深审。STATIC_CHAIN仅证明源码依赖候选，不证明运行。

### System / Platform

| ID / 功能 | 后端入口、Service、持久化 | Web / PDA | Base | 策略 |
|---|---|---|---|---|
| F-207 Base（web层通用数据处理） | [BaseController.java](/Users/user/work/heri/ktg-mes/ktg-common/src/main/java/com/ktg/common/core/controller/BaseController.java)；Service 0 / XML 0 / Entity-DTO-VO 2；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | PARTIAL：独立HTTP模型/校验；非旧BaseController迁入 | PORT |
| F-208 Cache（缓存监控） | [CacheController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/monitor/CacheController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/cache/index.vue)；PDA 未静态关联（非证实不存在） | PARTIAL：范围Key；无缓存监控页 | PORT |
| F-209 Captcha（验证码操作处理） | [CaptchaController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/common/CaptchaController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [login.vue](/Users/user/work/heri/ktg-mes-ui/src/views/login.vue) 等3处；PDA 未静态关联（非证实不存在） | NO | PORT |
| F-210 Common（通用请求处理） | [CommonController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/common/CommonController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-211 Gen（代码生成 操作处理） | [GenController.java](/Users/user/work/heri/ktg-mes/ktg-generator/src/main/java/com/ktg/generator/controller/GenController.java)；Service 2 / XML 2 / Entity-DTO-VO 3；STATIC_CHAIN | Web [editTable.vue](/Users/user/work/heri/ktg-mes-ui/src/views/tool/gen/editTable.vue) 等3处；PDA 未静态关联（非证实不存在） | PARTIAL：CLI/16产物；无旧生成管理页 | PORT |
| F-212 Login（登录控制器） | [LoginController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/LoginController.java)；Service 2 / XML 7 / Entity-DTO-VO 11；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-213 Minio（文件上传接口） | [MinioController.java](/Users/user/work/heri/ktg-mes/ktg-common/src/main/java/com/ktg/common/config/MinioController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/so/salesorder/index.vue) 等3处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-214 OpenApiClient（OpenApiClient） | [OpenApiClientController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/OpenApiClientController.java)；Service 1 / XML 3 / Entity-DTO-VO 16；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-215 OpenApiClientRoute（OpenApiClientRoute） | [OpenApiClientRouteController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/OpenApiClientRouteController.java)；Service 2 / XML 4 / Entity-DTO-VO 7；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-216 OpenApiDiagnostics（OpenApiDiagnostics） | [OpenApiDiagnosticsController.java](/Users/user/work/heri/ktg-mes/ktg-framework/src/main/java/com/ktg/framework/openapi/OpenApiDiagnosticsController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-217 OpenApiToken（OpenApiToken） | [OpenApiTokenController.java](/Users/user/work/heri/ktg-mes/ktg-framework/src/main/java/com/ktg/framework/openapi/OpenApiTokenController.java)；Service 0 / XML 0 / Entity-DTO-VO 2；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-218 OpenApiVerification（第二组实际接口，用于检查分组授权效果，不读取MES业务数据。） | [OpenApiVerificationController.java](/Users/user/work/heri/ktg-mes/ktg-framework/src/main/java/com/ktg/framework/openapi/OpenApiVerificationController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-219 PreviewLogin（预览登录控制器） | [PreviewLoginController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/PreviewLoginController.java)；Service 2 / XML 5 / Entity-DTO-VO 9；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/so/customerProductPrice/index.vue) 等5处；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | REVIEW |
| F-220 Server（服务器监控） | [ServerController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/monitor/ServerController.java)；Service 0 / XML 0 / Entity-DTO-VO 2；STATIC_PARTIAL_OR_NON_DB | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/server/index.vue)；PDA 未静态关联（非证实不存在） | NO | PORT |
| F-221 Swagger（swagger 接口） | [SwaggerController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/tool/SwaggerController.java)；Service 0 / XML 0 / Entity-DTO-VO 0；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-222 SysAttachment（附件Controller） | [SysAttachmentController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysAttachmentController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-223 SysAutoCode（SysAutoCode） | [SysAutoCodeController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysAutoCodeController.java)；Service 0 / XML 0 / Entity-DTO-VO 0；STATIC_PARTIAL_OR_NON_DB | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/cal/plan/index.vue) 等37处；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-224 SysAutoCodePart（SysAutoCodePart） | [SysAutoCodePartController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysAutoCodePartController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [part.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/autocode/part.vue)；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-225 SysAutoCodeRule（SysAutoCodeRule） | [SysAutoCodeRuleController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysAutoCodeRuleController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/cal/plan/index.vue) 等36处；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-226 SysConfig（参数配置 信息操作处理） | [SysConfigController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysConfigController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/config/index.vue)；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-227 SysDept（部门信息） | [SysDeptController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysDeptController.java)；Service 1 / XML 2 / Entity-DTO-VO 5；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/dept/index.vue) 等5处；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-228 SysDictData（数据字典信息） | [SysDictDataController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysDictDataController.java)；Service 2 / XML 2 / Entity-DTO-VO 3；STATIC_CHAIN | Web [data.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/dict/data.vue)；PDA [index.vue](/Users/user/work/heri/heri-uni-app/pages/defective/index.vue) 等5处 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-229 SysDictType（数据字典信息） | [SysDictTypeController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysDictTypeController.java)；Service 1 / XML 2 / Entity-DTO-VO 3；STATIC_CHAIN | Web [data.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/dict/data.vue) 等3处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-230 SysIndex（首页） | [SysIndexController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysIndexController.java)；Service 0 / XML 0 / Entity-DTO-VO 0；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-231 SysJob（调度任务信息操作处理） | [SysJobController.java](/Users/user/work/heri/ktg-mes/ktg-quartz/src/main/java/com/ktg/quartz/controller/SysJobController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/job/index.vue) 等2处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-232 SysJobLog（调度日志操作处理） | [SysJobLogController.java](/Users/user/work/heri/ktg-mes/ktg-quartz/src/main/java/com/ktg/quartz/controller/SysJobLogController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [log.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/job/log.vue)；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-233 SysLogin（登录验证） | [SysLoginController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysLoginController.java)；Service 3 / XML 9 / Entity-DTO-VO 15；STATIC_CHAIN | Web [login.vue](/Users/user/work/heri/ktg-mes-ui/src/views/login.vue) 等3处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-234 SysLogininfor（系统访问记录） | [SysLogininforController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/monitor/SysLogininforController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/logininfor/index.vue)；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-235 SysMenu（菜单信息） | [SysMenuController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysMenuController.java)；Service 1 / XML 3 / Entity-DTO-VO 8；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/menu/index.vue) 等3处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-236 SysMessage（查询当前人的消息） | [SysMessageController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysMessageController.java) 等2处；Service 2 / XML 6 / Entity-DTO-VO 8；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/message/index.vue)；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-237 SysNotice（公告 信息操作处理） | [SysNoticeController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysNoticeController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/system/notice/index.vue)；PDA [index.vue](/Users/user/work/heri/heri-uni-app/pages/notice/index.vue) 等3处 | NO：无对应正式MES/功能模块 | PORT |
| F-238 SysOperlog（操作日志记录） | [SysOperlogController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/monitor/SysOperlogController.java)；Service 1 / XML 1 / Entity-DTO-VO 2；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/operlog/index.vue)；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-239 SysPost（岗位信息操作处理） | [SysPostController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysPostController.java)；Service 1 / XML 2 / Entity-DTO-VO 3；STATIC_CHAIN | Web [worker.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/md/workstation/components/worker.vue) 等2处；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-240 SysProfile（个人信息 业务处理） | [SysProfileController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysProfileController.java)；Service 2 / XML 5 / Entity-DTO-VO 8；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/so/customerProductPrice/index.vue) 等11处；PDA [index.vue](/Users/user/work/heri/heri-uni-app/pages/mine/avatar/index.vue) 等4处 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-241 SysRegister（注册验证） | [SysRegisterController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysRegisterController.java)；Service 2 / XML 6 / Entity-DTO-VO 8；STATIC_CHAIN | Web [login.vue](/Users/user/work/heri/ktg-mes-ui/src/views/login.vue) 等3处；PDA [index.vue](/Users/user/work/heri/heri-uni-app/pages/index.vue) 等3处 | NO | PORT |
| F-242 SysRole（角色信息） | [SysRoleController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysRoleController.java)；Service 4 / XML 8 / Entity-DTO-VO 14；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/md/config/index.vue) 等5处；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-243 SysUser（用户信息） | [SysUserController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/SysUserController.java)；Service 3 / XML 7 / Entity-DTO-VO 9；STATIC_CHAIN | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/mes/so/customerProductPrice/index.vue) 等11处；PDA [index.vue](/Users/user/work/heri/heri-uni-app/pages/mine/avatar/index.vue) 等4处 | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-244 SysUserOnline（在线用户监控） | [SysUserOnlineController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/monitor/SysUserOnlineController.java)；Service 1 / XML 0 / Entity-DTO-VO 3；STATIC_PARTIAL_OR_NON_DB | Web [index.vue](/Users/user/work/heri/ktg-mes-ui/src/views/monitor/online/index.vue)；PDA 未静态关联（非证实不存在） | NO | PORT |
| F-245 Test（swagger 用户测试方法） | [TestController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/tool/TestController.java)；Service 0 / XML 0 / Entity-DTO-VO 1；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | DROP |
| F-246 User（登录控制器） | [UserController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/UserController.java)；Service 3 / XML 7 / Entity-DTO-VO 11；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | PARTIAL：简化模型/API/通用Web，不等于旧功能完整迁入 | PORT |
| F-247 UserTask（查询待处理的任务和单据） | [UserTaskController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/UserTaskController.java)；Service 1 / XML 1 / Entity-DTO-VO 1；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-248 WechatMiniappOpenId（微信小程序openId绑定入口。） | [WechatMiniappOpenIdController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/WechatMiniappOpenIdController.java)；Service 2 / XML 1 / Entity-DTO-VO 6；STATIC_CHAIN | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | PORT |
| F-249 WechatSubscribeNoticeTest（微信订阅消息测试发送入口。） | [WechatSubscribeNoticeTestController.java](/Users/user/work/heri/ktg-mes/ktg-admin/src/main/java/com/ktg/web/controller/system/WechatSubscribeNoticeTestController.java)；Service 1 / XML 0 / Entity-DTO-VO 4；STATIC_PARTIAL_OR_NON_DB | Web 未静态关联（非证实不存在）；PDA 未静态关联（非证实不存在） | NO：无对应正式MES/功能模块 | DROP |
## 4. 业务与PDA盘点边界

业务候选统一进入迁移清单，当前不逐条确认模型/状态机或制定新Domain。详细静态路径仅存证据JSON，未来迁移对应模块时核对真实链路。PDA页面和API清单保留；pkg-business无业务页、动态导航/设备/旧数据库/菜单可达性待核实。本轮业务不标记DONE，也不以未关联为DROP。

## 5. 非Controller能力与工具

| 能力 | 源码证据 | 策略与需核对项 |
|---|---|---|
| 通用Excel导入导出/日期字符串/字典/树 | [ExcelUtil.java](/Users/user/work/heri/ktg-mes/ktg-common/src/main/java/com/ktg/common/utils/poi/ExcelUtil.java) | COPY候选：核许可和依赖；分页/DTO/范围PORT，不复制账号或数据；不要重写一个等价Excel库。 |
| 手工/定时主数据同步与状态记录 | [MasterDataSyncService.java](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/erp/lot/service/MasterDataSyncService.java) | REDESIGN局部边界：继承DB claim/run token及状态查询；按厂加scope，恢复规则单独验证。 |
| 定时ERP/托盘/防锈/状态核对 | [TaskServiceImpl.java](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/timedtask/service/impl/TaskServiceImpl.java) | REVIEW逐方法：产品同步/状态核对保留，邮箱/工资任务剥离；不迁Job seed或任意反射。 |
| 生产状态/财务协作服务 | [ProductionManagementService.java](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/service/ProductionManagementService.java) | REDESIGN：生产数量/完工能力保留，财务工资依赖剥离，不整块DROP。 |
| 业务通知与交接提醒 | [MesNotificationServiceImpl.java](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/notification/service/impl/MesNotificationServiceImpl.java) | PORT能力；供应商发送适配/配置独立，不迁Token/收件人。 |
| 工单ERP Gateway | [OapiWorkorderErpGateway.java](/Users/user/work/heri/ktg-mes/ktg-mes/src/main/java/com/ktg/mes/pro/request/gateway/OapiWorkorderErpGateway.java) | PORT/REDESIGN协议边界；不增加所有CRUD Gateway。 |
| Web选择器/字典/上传/分页/打印组件 | [components](/Users/user/work/heri/ktg-mes/../ktg-mes-ui/src/components) | COPY产品/交互候选，Vue2实现PORT到Vue3；目录逐项引用保留在后续包清单。 |
| PDA扫码/打印/网络设置 | [index.vue](/Users/user/work/heri/ktg-mes/../heri-uni-app/pages/setting/index.vue) | PORT；pages.json该设置入口有注释，需查实际导航和设备依赖，不称现场已验证。 |

## 6. 成熟交互的迁移保留项

每个PORT/REDESIGN在实施前冻结旧页面的查询条件、字段/字典、表格列、工具栏/行按钮、表单/弹窗、选择器/联动、权限、错误/空状态及用户动作顺序，再做Vue3/TS适配。迁移阶段不调用Codegen Skill；旧代码、交互和业务规则直接作为迁移输入。字段是否重命名、删除、改变必填必须逐项说明。

具体已人工核对：路线页面有编号/名称/启用/备注查询展示、MES编号/自动编号、路线组成/产品/BOM子页面和按钮权限；工单页面有详情/排程/确认中心；用户有导入模板/导入导出/岗位部门/角色授权/状态；报工有数量/不良/审核/结算/完工，结算与工资可拆但报工不能消失；IQC/领料有主从单据与执行。以上链路的证明和限制见重审报告第6节。


### 2026-10-05 最新执行纠正：迁移阶段不使用Codegen

用户明确要求迁移阶段忽略Codegen Skill；不调用、不生成、不作为门槛或例外审批。此前文档关于迁移时使用Skill或辅助骨架的表述由本条取代。现有Codegen代码/产物保留，开发期再使用。系统工具中的旧生成管理能力先冻结，不让其建设阻挡基础系统迁移；基础模块优先admin/common/framework/system/quartz，generator后置为开发工具。源产品COPY/PORT优先；旧架构可沿用，当前Base包结构可以替换。


## 2026-10-05 Phase 1 全量迁移执行记录（进行中）

用户已授权 Source Product 基础系统全量 COPY/PORT；本轮不调用 Codegen，不迁 MES Domain、不提交/推送/部署。已建立 mes-common / mes-system / mes-framework / mes-quartz / mes-generator / mes-admin Maven reactor，源码与配置按职责迁入；Dept/Post/User/Role/Menu/Dict/Config/Notice、日志、在线会话、Quartz、监控前后端正在接通。状态 **MIGRATING**，源码已存在、构建通过均不等于 VERIFIED。

已通过：`zsh -ic 'mvn -B -ntp package -DskipTests'`（package-8）；Node22 下 UI `npm run build`（build-3）。隔离验收数据库仅从本项目自有测试库复制，V1 不改，V2/V3 Flyway 已执行；旧表/开发数据归档保留。原项目只读；本轮开始前两端 dirty 内容备份在 target/phase1/baseline。

实际失败并修复：Boot4/Jakarta 依赖和 Excel/POI 兼容；Vue3 slot/SFC 类型、显式 .vue 引用；Flyway 元数据权限探测使 Druid 连接失效（迁移改用独立连接，未扩大 MySQL 共享服务元数据权限）；重复导入模板权限注解；Quartz resultMap 中 id/result 顺序；打包插件输出薄 JAR 与可执行 JAR 路径差异。凭证仅存忽略目录、模式0600，文档不记录。

尚未验收：真实完整启动、所有页面实际操作、导入/导出、资源归属/权限负例、任务/消息/附件/个人资料全链路、正式 mvn verify/UI test、浏览器验证。后续记录逐包补齐，不能把这一段当作 Phase 1 完成回执。


### Phase 1 执行追加：系统链路、真实 MySQL 与安全合并（2026-10-05）

状态仍为 **MIGRATING**。六个 Maven 模块已接入正式 Source 系统调用链，旧简化平台写接口仅在显式 TEST ONLY 条件下注册；原开发表和数据保留归档，不改已应用 V1–V4。系统登录/Redis 在线会话、组织用户角色、公告消息、附件及监控在本轮独立 schema 上运行；Factory Foundation 保留。

本包证据：此前 `mvn -B -ntp verify`（verify-1）86 项/0失败/3跳过；Source 系统真实 MySQL + HTTP 合成操作首轮 26/26（target/phase1/acceptance-crud.json）；Node22 UI build-8 通过；后端 package-22 通过；backend-11 完整启动，19 个实际接口检查为200。以上分别为回归测试、真实测试引擎、构建与本地运行证据，**不相互替代、不代表完整浏览器验收**。浏览器已实际新增部门，排序必填错误提示有效；余下操作继续执行。

新增合并：Source Role 显式 PLATFORM/FACTORY 表单；Source Quill 完整工具栏、私有公告图片/视频引用与后端 DOM 解析过滤；Druid stat/wall YAML 消费；一次性验证码原子取出；Quartz 立即执行经独立 DB claim、run token 与真实日志，调度副作用延后到提交后，支持显式确认死亡后的受控恢复。Minio SDK 与 YAML 配置结构接入 FileStorage，无源环境值、无公开对象 URL；**Minio 外部实例尚未验证**。

实际失败：新增 Druid Settings 重载构造使 YAML Binder 未绑定，backend-10 启动失败；已移除重载并通过 backend-11。Minio 9 的 JVM HTTP 依赖及 Long 参数兼容已修复。npm/Maven 依赖写入受沙箱限制时用审批沿用用户缓存，未建项目 .m2/.mvn/private mirror。Phase1 文件目录已从原服务目录复制隔离，原文件保留；仅本轮18081进程重启，原18080/15173服务未动。

待验证边界：新 Source 安全测试、Quartz实际启停/执行/恢复、Excel导入导出、附件归属负例、完整页面操作及全部矩阵；新构建与当前运行JAR版本仍按日志逐次记录。未提交、推送、部署或迁入正式MES业务。


## 2026-10-06 Phase 1 追加：完整 Source 系统、编号工具、真实验收（仍在进行）

有效目标是 Source Product 基础系统全量 COPY/PORT，迁移阶段不调用 Codegen Skill/CLI。现有正式结构为 common/system/framework/quartz/generator/admin 六模块，旧简化平台只在显式 TEST ONLY 条件注册。Factory Foundation、独立 Scope/权限/资源和 Mapper ownership 保留，未迁正式 MES Domain，未提交/推送/部署。

- Source Dept/Post/User/Role/Menu/Dict/Config/Notice/Message/Attachment、日志、Redis 在线、Excel、Quartz和监控已经接入前后端与真实测试库；整体状态 **MIGRATING**，未把源码或 build 当作 VERIFIED。
- Batch10 增补 Source 自动编码规则/组成/结果三表及旧完整页面。COPY 保留 Source 字段；PORT 预览不占号、明确 POST 生成、平台事务内规则/计数行锁、周期隔离和长度溢出拒绝，无 JVM 锁/ThreadLocal 可变计数。仅通用平台编号，不自动注册 Factory/MES 编号消费者。V6 已应用。
- 真实 MySQL/HTTP：组织用户首轮26/26；文件/Quartz/导出首轮29/32，3项 DTO 回写兼容失败已保留，修复后的回写与真实Excel导入追加11/11；自动编码17/17，包含24并发请求生成唯一连续号码、精确长度边界、预览不占号、无权限403。首次管理员空库初始化/重复拒绝2/2，独立18082进程登录/系统接口6/6，该临时进程已停止。详见 `../reviews/2026-10-06-phase1-verification.json`，这些不是完整浏览器验收。
- 浏览器已实际新增/编辑部门、岗位，新增字典类型与 PENDING 数据；参数新增已执行，仍继续核验。图片证据保留在自有 target/phase1，最终另提供可查看结果。后续用户/角色/菜单、密码、任务、文件、导入导出和监控仍要逐项闭环。
- 后端上一轮 verify-4：91 run / 0 failure / 0 error / 3 skip（88通过）；当前最新源码仍需最终 verify/smoke。package-37通过；UI Node22 test 18/18、build通过（ui-check-4）。实际 run JAR 为 phase1-runtime-10，18081/backend-17；UI15174/ui-runtime-4。18080/15173原服务未动。
- 修复 Source 编辑 VO 的只读元数据/数值排序、角色部分状态 DTO、服务端新建 ID、Source菜单目录按显式管理权限查看（不能用 magic userId）、角色菜单树权限、任务日志详情 path variable，以及原表 start_time/end_time 的映射。V7只加实测耗时、停用迁移生成而没有真实接口的按钮，保留数据与分配，不改已应用V1–V6。
- Source在线消息 PORT 为仅接收、带Authorization的SSE，提交后按持久化接收人刷新；不恢复匿名WebSocket客户端任意转发。Source浏览器/OS有界解析，审计投影保留requestId/actorId/factoryId/scope/耗时，未采集请求体。SSE交付/撤销仍待真实验收。
- Vue3已合并 Source图标/布局工具、隐藏菜单路由、缓存/参数、手动刷新权限、字典失败可重试、私有 FileUpload/ImageUpload 文件选择及预览。密码弹窗去掉原Source显示新密码的成功提示，长度约束与后端对齐；后续涉及新凭证的浏览器操作按工具规则交给用户填写。
- Redis平台键现在按可信 Platform JDBC 身份派生命名空间，也允许显式 YAML 的 MES_SYSTEM_CACHE_NAMESPACE；会话、Source缓存、验证码、密码锁同域。Factory业务缓存仍须显式FactoryId，不能复用平台命名空间代替厂范围。跨数据库证明正在执行。

实际失败与处置：Druid wall 拒绝自动编码列表原 WHERE 1=1，已改 Source XML 的 where 结构，未放宽SQL防火墙；新任务控制器变量名与Druid SDK getter编译失败已修复；首次初始化V7重复添加Source原有start_time失败，仅在本轮自有空库确认无DDL应用后清除失败历史记录并重新迁移，原失败日志保留。UI一次启动误用全局Node18失败后明确切Node22；CHOKIDAR环境值没有使Vite8刷新旧模块，改显式 MES_DEV_WATCH_POLLING=true 的 watch消费者并仅重启本轮UI。组件加载/API响应异步导致过早读取DOM/填入查询框，已改为等待可见目标并限定弹窗；不能将这些尝试当作通过。

剩余边界：完整浏览器矩阵、最新回归/打包 smoke、Source权限/会话撤销/锁、SSE、任务调度恢复、外部Minio实例、部署/生产。状态按实际证据逐项更新，不能提前宣告Phase1完成。
