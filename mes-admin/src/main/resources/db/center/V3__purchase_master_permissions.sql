-- 采购基础资料菜单；不自动授予角色，不提供报价改写或删除权限。

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '供应商管理',m.menu_id,80,'purchaseSupplier','purchase/purchaseSupplier/index','C','business:purchase:supplier:list','list','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.parent_id=0 AND m.path='basic' AND m.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:supplier:list');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '供应商管理查询',m.menu_id,0,'#','F','business:purchase:supplier:query','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:supplier:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:supplier:query');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '供应商管理新增',m.menu_id,1,'#','F','business:purchase:supplier:create','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:supplier:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:supplier:create');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '供应商管理修改',m.menu_id,2,'#','F','business:purchase:supplier:update','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:supplier:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:supplier:update');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '供应商管理删除',m.menu_id,3,'#','F','business:purchase:supplier:delete','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:supplier:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:supplier:delete');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '采购参考报价',m.menu_id,80,'purchaseReferencePrice','purchase/purchaseReferencePrice/index','C','business:purchase:reference:price:list','list','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.parent_id=0 AND m.path='basic' AND m.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:reference:price:list');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '采购参考报价查询',m.menu_id,0,'#','F','business:purchase:reference:price:query','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:reference:price:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:reference:price:query');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '采购参考报价新增',m.menu_id,1,'#','F','business:purchase:reference:price:create','migration',CURRENT_TIMESTAMP
FROM sys_menu m WHERE m.perms='business:purchase:reference:price:list' AND m.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:purchase:reference:price:create');
