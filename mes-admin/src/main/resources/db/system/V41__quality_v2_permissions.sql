-- 工序不良类型与工废/料废类别覆盖权限登记，不自动修改任何角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '查看工序不良类型',p.menu_id,0,'#','F','business:operation:defect:type:read',1,1,'0','0','quality-v2',NOW() FROM sys_menu p WHERE p.perms='operation:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:operation:defect:type:read');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '创建工序不良类型',p.menu_id,1,'#','F','business:operation:defect:type:create',1,1,'0','0','quality-v2',NOW() FROM sys_menu p WHERE p.perms='operation:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:operation:defect:type:create');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '修改工序不良类型',p.menu_id,2,'#','F','business:operation:defect:type:update',1,1,'0','0','quality-v2',NOW() FROM sys_menu p WHERE p.perms='operation:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:operation:defect:type:update');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '删除工序不良类型',p.menu_id,3,'#','F','business:operation:defect:type:delete',1,1,'0','0','quality-v2',NOW() FROM sys_menu p WHERE p.perms='operation:list' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:operation:defect:type:delete');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '覆盖申报工废/料废类别',p.menu_id,4,'#','F','business:quality:defect:waste:override',1,1,'0','0','quality-v2',NOW() FROM sys_menu p WHERE p.perms='business:quality:defect:read' AND p.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:defect:waste:override');
