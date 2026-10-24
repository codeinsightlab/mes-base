# 主子单据组件

主子单据前端按 AGENTS 优先复用本组件，不沿用生成的单表页面；后端仍必须针对主表和明细表分别生成完整基础代码（persistence + backend，通常同时test）。前端页面复用与后端代码生成是独立要求，不得将前端例外扩大成后端跳过生成。

## 已核对的后端复用链路

领料页面将现有 mesMaterialIssue.ts / mesMaterialIssueItem.ts 的API对象传给组件，二者实现CrudApi，通过统一request访问 /api/issue/mes-material-issue 和 /api/issue/mes-material-issue-item。现有Controller委托原业务Service，Service使用生成的MesMaterialIssueMapper / MesMaterialIssueItemMapper与Example/XML，在businessTransactionManager事务内保留引用、工艺及删除规则。组件复用的是这些既有CRUD契约、分页、权限及数据库路由能力，不是一套新通用主子聚合后端。需要整单原子保存时仍须新增有明确业务边界的聚合接口。

前端 `src/components/document/MasterDetailDocument.vue` 负责单据列表、打开/新建主单、上方表单、下方明细、主单删除确认、未保存返回提醒和上下文清理。`DocumentFields.vue` 负责字段控件与业务选择器插槽，同时由普通 CrudPage 的弹窗复用。组件不定义领料字段、工艺状态或引用规则；后端继续是最终规则边界。

业务页面显式传入 headerApi/itemApi（既有 CrudApi 契约）、headerFields/itemFields（Field[]）、headerPermission/itemPermission（不含动作后缀）、parentKey（例如 issueId），及可选的两组 tableKey。权限动作当前复用 list/query/create/update/delete，范围为 factory。通过 field-productId、field-materialId 等插槽提供业务选择器，组件不按字段名猜业务语义。实例见 mes-base-ui/src/views/issue/mesMaterialIssue/index.vue；当前全部字段含生产执行关联保存在同目录上层 fields.ts。

主单先保存取得字符串 ID，之后才挂载明细区域。明细 CrudPage 使用 embedded 和 fixedValues：查询参数、创建/更新命令在最终构造时固定父单 ID；搜索、表单和默认列均不显示该固定字段，不允许草稿覆盖它。分页只加载当前主单明细，ID 和 decimal 保持字符串。managedEditing 用于主单列表：新增/编辑交给单据页面，列表的单删、批删、查询、分页与列设置仍保留。普通单表 CrudPage 默认行为不变。

主单和明细沿用独立保存接口，按钮明确表示保存主单或保存明细。主单已保存后明细失败不自动回滚主单；需要整单原子保存的模块必须由业务 Service 提供聚合事务命令，不能靠前端多次请求模拟。组件不新增审批/同步/ERP 流程。不在切厂后恢复旧草稿、重放旧命令或接受迟到结果；关闭/切厂/撤权/卸载均使旧请求失效。

领料单据正式入口为 /issue/mesMaterialIssue。旧 /issue/mesMaterialIssueItem 只跳转统一入口，中心旧菜单隐藏但保留 ID、权限与角色关联。已删除旧 IssueRecordDetails 独立详情实现及其失效测试，权限和上下文测试迁到真实主子组件；旧明细 API 保留供统一页面使用。手工维护的页面、菜单 JSON/SQL不带生成归属标记；领料生成配置不再产出前端及菜单。基础 Mapper/XML/Example 的生成仍可复用，不新建第二套数据访问。

当前限制：未保存提醒覆盖页面内部“返回列表”，不提供浏览器关闭或跨路由草稿恢复；无整单原子提交。
