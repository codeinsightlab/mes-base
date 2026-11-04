-- 工单 ERP 同步准备使用独立业务权限；只登记按钮，不给任何现有角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT 'ERP工单同步准备',p.menu_id,49,'#','F','workorder:sync',1,1,'0','0','e10-v20',NOW()
FROM sys_menu p WHERE p.perms='workorder:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:sync');
