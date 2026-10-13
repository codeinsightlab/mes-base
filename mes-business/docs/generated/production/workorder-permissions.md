# 工单第一阶段权限

workorder:list/query/create/update/delete/release；batch:query/selectRoute。批次创建、编辑、删除通过草稿主单事务维护，使用主单动作权限；不开放子对象任意CRUD。下达及路径选择独立权限；出库锁定仅受信任服务端调用，无HTTP锁定入口。V27保留旧工单菜单ID和原CRUD角色绑定，不自动授予新权限。
