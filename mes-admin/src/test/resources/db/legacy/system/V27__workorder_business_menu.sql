-- 菜单串行迁移，无角色自动授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,status,create_by,create_time)
SELECT '生产管理',0,20,'production','M','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='production' AND menu_type='M');
-- 工单模型替换旧技术CRUD：保留菜单ID及原角色关联，新增动作不自动赋权。
UPDATE sys_menu SET path='workorder',component='production/workorder/index',menu_name='生产工单',
 parent_id=(SELECT id FROM (SELECT MIN(menu_id) id FROM sys_menu WHERE parent_id=0 AND path='production' AND menu_type='M') d),
 perms='workorder:list',status='0'
WHERE component='pro/proWorkorder/index' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,status,create_by,create_time)
SELECT '生产工单',d.menu_id,20,'workorder','production/workorder/index','C','workorder:list','0','migration',CURRENT_TIMESTAMP
FROM sys_menu d WHERE d.parent_id=0 AND d.path='production' AND d.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='production/workorder/index' AND menu_type='C');
UPDATE sys_menu SET perms='workorder:list',menu_name='工单列表' WHERE perms='business:pro:workorder:list' AND menu_type='F';
UPDATE sys_menu SET perms='workorder:query',menu_name='工单详情' WHERE perms='business:pro:workorder:query' AND menu_type='F';
UPDATE sys_menu SET perms='workorder:create',menu_name='工单新增' WHERE perms='business:pro:workorder:create' AND menu_type='F';
UPDATE sys_menu SET perms='workorder:update',menu_name='工单编辑' WHERE perms='business:pro:workorder:update' AND menu_type='F';
UPDATE sys_menu SET perms='workorder:delete',menu_name='工单删除' WHERE perms='business:pro:workorder:delete' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '下达工单',p.menu_id,20,'#','F','workorder:release','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:release' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '批次详情',p.menu_id,20,'#','F','batch:query','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='batch:query' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '选择批次路径',p.menu_id,20,'#','F','batch:selectRoute','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='batch:selectRoute' AND menu_type='F');
DELETE FROM sys_role_menu WHERE menu_id IN(SELECT menu_id FROM sys_menu WHERE perms='business:pro:workorder:sync');
DELETE FROM sys_menu WHERE perms='business:pro:workorder:sync';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '工单列表',p.menu_id,10,'#','F','workorder:list','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:list' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '工单详情',p.menu_id,10,'#','F','workorder:query','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:query' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '工单新增',p.menu_id,10,'#','F','workorder:create','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:create' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '工单编辑',p.menu_id,10,'#','F','workorder:update','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:update' AND menu_type='F');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,status,create_by,create_time)
SELECT '工单删除',p.menu_id,10,'#','F','workorder:delete','0','migration',CURRENT_TIMESTAMP FROM sys_menu p
WHERE p.component='production/workorder/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='workorder:delete' AND menu_type='F');
