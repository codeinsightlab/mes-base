# 工单与历史记录权限

工单：workorder:list/query/create/update/delete/release。工单只管理需求，不创建批次、投入、Execution 或路径事实。
旧工单批次/路径接口、页面及专用权限已退役。V50 删除 `batch:query`、`batch:selectRoute`、`workorder:history:query` 菜单项及角色授权；V27/V47 已发布迁移保持原文。
`production_batch` 和 `batch_route_node` 由业务迁移 V68 退役删除。当前 Execution 使用自身正式批次及锁定路径；已发布报工、质量和入库事实沿现有只读查询保留。
