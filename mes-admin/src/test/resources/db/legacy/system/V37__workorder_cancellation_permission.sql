-- 正式作废独立授权，仅登记，不自动分配给任何现有角色。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '正式作废工单',p.menu_id,48,'#','F','production:workorder:cancel',1,1,'0','0','workorder-v18',NOW()
FROM sys_menu p WHERE p.perms='workorder:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='production:workorder:cancel');
