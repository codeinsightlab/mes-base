# 工单与历史批次权限（2026-10-15）

工单：workorder:list/query/create/update/delete/release。工单只管理需求，不新建旧批次或路径。
历史批次：batch:query，仅 GET /api/production/batch/{id}，按当前业务库读取旧状态与所选节点。
旧 batch:selectRoute 不再对应任何运行时接口或前端按钮；历史 V27 及已有授权记录保留，不能赋予路径写入能力。
历史批次存在时保留工单删除及产品/默认工艺修改保护。新执行使用 ProductionExecution 及其确切 Routing 版本。
