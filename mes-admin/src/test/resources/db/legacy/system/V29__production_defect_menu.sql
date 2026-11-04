-- 独立质量入口，只登记权限，不改变既有角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '质量管理',0,40,'quality','M','','list',1,1,'0','0','defect-phase1',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='quality');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '不良品管理',p.menu_id,10,'productionDefect','quality/productionDefect/index','C','business:quality:defect:read','list',1,1,'0','0','defect-phase1',NOW()
FROM sys_menu p WHERE p.parent_id=0 AND p.path='quality'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:defect:read');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '不良申报',p.menu_id,1,'#','F','business:quality:defect:create',1,1,'0','0','defect-phase1',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:defect:create');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '管理员留痕订正',p.menu_id,2,'#','F','business:quality:defect:revise',1,1,'0','0','defect-phase1',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:defect:revise');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '管理员留痕作废',p.menu_id,3,'#','F','business:quality:defect:void',1,1,'0','0','defect-phase1',NOW()
FROM sys_menu p WHERE p.perms='business:quality:defect:read'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:quality:defect:void');
