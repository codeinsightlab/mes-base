-- SOURCE_PRODUCT_PORT: generic system tools/Swagger menu, no role grants or company seed.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,create_by,create_time)
SELECT '系统工具',0,3,'tool','M','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='tool' AND menu_type='M');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,create_by,create_time)
SELECT '系统接口',menu_id,1,'swagger','tool/swagger/index','C','tool:swagger:view','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE parent_id=0 AND path='tool' AND menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='tool/swagger/index');
