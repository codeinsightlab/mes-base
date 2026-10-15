START TRANSACTION;
-- 工单仅保留执行摘要；生产执行恢复独立页面，复用菜单编号与既有角色绑定。
UPDATE sys_menu SET menu_name='生产执行',visible='0'
WHERE menu_type='C' AND component='production/mesProductionExecution/index' AND perms='business:mes:production:execution:list';
-- 派工在生产执行页面操作，旧独立派工地址兼容跳转生产执行。
UPDATE sys_menu SET visible='1'
WHERE menu_type='C' AND component='production/mesExecutionAssignment/index' AND perms='business:mes:execution:assignment:list';
COMMIT;
