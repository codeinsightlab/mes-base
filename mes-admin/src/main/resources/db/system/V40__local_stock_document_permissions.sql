-- 正式仓储单据权限登记，不修改任何角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '其他出入库单',p.menu_id,30,'stockDocuments','wm/stockDocuments/index','C','business:wm:stock:document:list',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.path='stock' AND p.parent_id=0 AND p.menu_type='M' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '查看单据',p.menu_id,0,'#','F','business:wm:stock:document:query',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.perms='business:wm:stock:document:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:query');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '创建草稿',p.menu_id,1,'#','F','business:wm:stock:document:create',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.perms='business:wm:stock:document:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:create');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '维护草稿及明细',p.menu_id,2,'#','F','business:wm:stock:document:update',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.perms='business:wm:stock:document:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:update');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '作废草稿',p.menu_id,3,'#','F','business:wm:stock:document:cancel',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.perms='business:wm:stock:document:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:cancel');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '正式过账',p.menu_id,4,'#','F','business:wm:stock:document:post',1,1,'0','0','stock-v22',NOW() FROM sys_menu p WHERE p.perms='business:wm:stock:document:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:stock:document:post');
