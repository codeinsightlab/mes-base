-- MES-CODEGEN: generated compensation for the existing material workspace menus.
-- Keep menu IDs and role-menu rows. This SQL registers permission definitions only.

UPDATE sys_menu SET menu_name='基础数据',order_num=0,icon='List'
WHERE parent_id=0 AND path='basic' AND menu_type='M';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,icon,create_by,create_time)
SELECT '基础数据',0,0,'basic','M','List','codegen',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='basic' AND menu_type='M');

SET @basic_directory=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='basic' AND menu_type='M' ORDER BY menu_id LIMIT 1);

-- Material workspace route is plural by product contract; do not generate the singular basic/material route.
UPDATE sys_menu SET menu_name='物料管理',parent_id=@basic_directory,order_num=0,path='materials',
    component='basic/materials/index',menu_type='C',perms='business:material:list',icon='List'
WHERE component='basic/materials/index' AND menu_type='C' AND @basic_directory IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '物料管理',@basic_directory,0,'materials','basic/materials/index','C','business:material:list','List','codegen',CURRENT_TIMESTAMP
WHERE @basic_directory IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='basic/materials/index' AND menu_type='C');
SET @material_page=(SELECT menu_id FROM sys_menu WHERE component='basic/materials/index' AND menu_type='C' ORDER BY menu_id LIMIT 1);

UPDATE sys_menu SET menu_name='物料列表',order_num=0,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:list' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料列表',@material_page,0,'#','F','business:material:list','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料详情',order_num=1,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:query' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料详情',@material_page,1,'#','F','business:material:query','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料新增',order_num=2,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:create' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料新增',@material_page,2,'#','F','business:material:create','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料修改',order_num=3,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:update' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料修改',@material_page,3,'#','F','business:material:update','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料删除',order_num=4,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:delete' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料删除',@material_page,4,'#','F','business:material:delete','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:delete' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料同步',order_num=5,path='#',component=NULL,parent_id=@material_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:sync' AND menu_type='F' AND @material_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料同步',@material_page,5,'#','F','business:material:sync','codegen',CURRENT_TIMESTAMP
WHERE @material_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:sync' AND menu_type='F');

-- The category page and unit selector are dependencies of the material workspace.
UPDATE sys_menu SET menu_name='物料分类',parent_id=@basic_directory,order_num=1,path='materialCategory',
    component='basic/materialCategory/index',menu_type='C',perms='business:material:category:list',icon='List'
WHERE component='basic/materialCategory/index' AND menu_type='C' AND @basic_directory IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '物料分类',@basic_directory,1,'materialCategory','basic/materialCategory/index','C',
       'business:material:category:list','List','codegen',CURRENT_TIMESTAMP
WHERE @basic_directory IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='basic/materialCategory/index' AND menu_type='C');
SET @category_page=(SELECT menu_id FROM sys_menu WHERE component='basic/materialCategory/index' AND menu_type='C' ORDER BY menu_id LIMIT 1);
UPDATE sys_menu SET menu_name='物料分类列表',order_num=0,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:list' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类列表',@category_page,0,'#','F','business:material:category:list','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料分类详情',order_num=1,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:query' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类详情',@category_page,1,'#','F','business:material:category:query','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料分类新增',order_num=2,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:create' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类新增',@category_page,2,'#','F','business:material:category:create','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料分类修改',order_num=3,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:update' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类修改',@category_page,3,'#','F','business:material:category:update','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料分类删除',order_num=4,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:delete' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类删除',@category_page,4,'#','F','business:material:category:delete','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:delete' AND menu_type='F');
UPDATE sys_menu SET menu_name='物料分类同步',order_num=5,path='#',component=NULL,parent_id=@category_page,menu_type='F',status='0',visible='1'
WHERE perms='business:material:category:sync' AND menu_type='F' AND @category_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料分类同步',@category_page,5,'#','F','business:material:category:sync','codegen',CURRENT_TIMESTAMP
WHERE @category_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:material:category:sync' AND menu_type='F');

UPDATE sys_menu SET menu_name='计量单位',parent_id=@basic_directory,order_num=2,path='unitMeasure',
    component='basic/unitMeasure/index',menu_type='C',perms='business:unit:measure:list',icon='List'
WHERE component='basic/unitMeasure/index' AND menu_type='C' AND @basic_directory IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '计量单位',@basic_directory,2,'unitMeasure','basic/unitMeasure/index','C',
       'business:unit:measure:list','List','codegen',CURRENT_TIMESTAMP
WHERE @basic_directory IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='basic/unitMeasure/index' AND menu_type='C');
SET @unit_page=(SELECT menu_id FROM sys_menu WHERE component='basic/unitMeasure/index' AND menu_type='C' ORDER BY menu_id LIMIT 1);
UPDATE sys_menu SET menu_name='计量单位列表',order_num=0,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:list' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位列表',@unit_page,0,'#','F','business:unit:measure:list','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='计量单位详情',order_num=1,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:query' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位详情',@unit_page,1,'#','F','business:unit:measure:query','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='计量单位新增',order_num=2,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:create' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位新增',@unit_page,2,'#','F','business:unit:measure:create','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='计量单位修改',order_num=3,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:update' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位修改',@unit_page,3,'#','F','business:unit:measure:update','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='计量单位删除',order_num=4,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:delete' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位删除',@unit_page,4,'#','F','business:unit:measure:delete','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:delete' AND menu_type='F');
UPDATE sys_menu SET menu_name='计量单位同步',order_num=5,path='#',component=NULL,parent_id=@unit_page,menu_type='F',status='0',visible='1'
WHERE perms='business:unit:measure:sync' AND menu_type='F' AND @unit_page IS NOT NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '计量单位同步',@unit_page,5,'#','F','business:unit:measure:sync','codegen',CURRENT_TIMESTAMP
WHERE @unit_page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:unit:measure:sync' AND menu_type='F');
