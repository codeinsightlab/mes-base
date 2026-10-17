-- LOCAL仓储命令与只读流水复用库存页面；各动作独立授权，不扩大任何角色权限。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '期初建账',p.menu_id,20,'#','F','business:wm:stock:opening',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:opening');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '原材料入库',p.menu_id,21,'#','F','business:wm:stock:receipt',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:receipt');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '库存盘盈',p.menu_id,22,'#','F','business:wm:stock:gain',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:gain');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '库存盘亏',p.menu_id,23,'#','F','business:wm:stock:loss',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:loss');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '库存业务纠错',p.menu_id,24,'#','F','business:wm:stock:correction',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:correction');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '生产退料',p.menu_id,25,'#','F','business:wm:stock:return',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:return');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '库存动作流水',p.menu_id,26,'#','F','business:wm:stock:action:list',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:action:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '库存动作详情',p.menu_id,27,'#','F','business:wm:stock:action:query',1,1,'0','0','local-warehouse-v15',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:action:query');
