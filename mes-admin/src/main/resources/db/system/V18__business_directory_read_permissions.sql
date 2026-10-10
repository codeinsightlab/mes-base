-- Permission-only catalog entries; no page route and no automatic role grants.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '业务人员查询',0,1,'business/directory/personnel','F','business:directory:personnel:read','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='business:directory:personnel:read');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '业务部门查询',0,2,'business/directory/departments','F','business:directory:department:read','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='business:directory:department:read');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '业务岗位查询',0,3,'business/directory/posts','F','business:directory:post:read','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='business:directory:post:read');
