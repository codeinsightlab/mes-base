-- Restore OpenAPI administration retired by V16; preserve applied migration checksums.
-- Client records and API grants were retained by V16. Deleted role-menu grants cannot be inferred.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,create_by,create_time)
SELECT 'OpenAPI应用',menu_id,16,'openApiClient','system/openApiClient/index','C','system:openApiClient:list','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE parent_id=0 AND path='system' AND menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='system/openApiClient/index');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '应用查询',menu_id,1,'#','F','system:openApiClient:query','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE component='system/openApiClient/index'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:openApiClient:query');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '新建应用',menu_id,2,'#','F','system:openApiClient:add','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE component='system/openApiClient/index'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:openApiClient:add');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '应用状态',menu_id,3,'#','F','system:openApiClient:edit','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE component='system/openApiClient/index'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:openApiClient:edit');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '密钥重置',menu_id,4,'#','F','system:openApiClient:reset','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE component='system/openApiClient/index'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:openApiClient:reset');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '接口授权',menu_id,5,'#','F','system:openApiClient:authorize','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE component='system/openApiClient/index'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:openApiClient:authorize');

-- No automatic role grants or external API grants. Administrators must review and assign them.
