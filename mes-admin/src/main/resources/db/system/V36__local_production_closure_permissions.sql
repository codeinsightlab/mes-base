-- 本地生产全链业务命令独立授权；仅登记按钮，不给现有角色自动授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '本地工单用料维护',p.menu_id,40,'#','F','workorder:material:update',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='workorder:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:material:update');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '质量放行历史',p.menu_id,41,'#','F','business:quality:release:read',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:release:read');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '正式质量放行',p.menu_id,42,'#','F','business:quality:release:create',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:release:create');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '撤回质量放行',p.menu_id,43,'#','F','business:quality:release:revoke',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:release:revoke');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '成品接收历史',p.menu_id,44,'#','F','business:wm:acceptance:read',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:acceptance:read');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '成品实际接收',p.menu_id,45,'#','F','business:wm:acceptance:create',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:acceptance:create');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '成品接收逆向',p.menu_id,46,'#','F','business:wm:acceptance:reverse',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:wm:inventory:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:wm:acceptance:reverse');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '执行严格完工',p.menu_id,47,'#','F','business:production:execution:complete',1,1,'0','0','local-production-v16',NOW()
FROM sys_menu p WHERE p.perms='business:mes:production:execution:list' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:production:execution:complete');
