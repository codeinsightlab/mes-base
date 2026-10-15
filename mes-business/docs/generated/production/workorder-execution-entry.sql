START TRANSACTION;
-- 生产执行与节点派工统一从工单进入；保留菜单编号、独立权限及角色绑定。
-- 隐藏的旧路由仅用于兼容书签，前端跳转到生产工单。
UPDATE sys_menu SET visible = '1'
WHERE menu_type = 'C' AND component IN ('production/mesProductionExecution/index', 'production/mesExecutionAssignment/index')
  AND perms IN ('business:mes:production:execution:list', 'business:mes:execution:assignment:list');
COMMIT;
