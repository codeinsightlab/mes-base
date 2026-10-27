START TRANSACTION;
-- 将已有生产数量查询按钮提升为独立菜单，复用原菜单编号、权限及全部角色绑定。
-- 只改变导航归属，不新增角色授权，不改变报工业务权限。
UPDATE sys_menu SET menu_name='生产数量管理', parent_id=(SELECT d.menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='production' AND menu_type='M') d),
 order_num=30,path='productionReport',component='production/productionReport/index',menu_type='C',icon='list',visible='0'
WHERE perms='business:production:report:quantity' AND menu_type IN ('F','C');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '生产数量管理',p.menu_id,30,'productionReport','production/productionReport/index','C','business:production:report:quantity','list',1,1,'0','0','report-v1',NOW()
FROM sys_menu p WHERE p.parent_id=0 AND p.path='production' AND p.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='business:production:report:quantity');
UPDATE sys_menu SET parent_id=(SELECT p.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:production:report:quantity' AND menu_type='C') p)
WHERE perms='business:production:report:detail' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '报工流水及调整历史查询',p.menu_id,30,'#','F','business:production:report:detail',1,1,'0','0','report-v1',NOW()
FROM sys_menu p WHERE p.perms='business:production:report:quantity' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='business:production:report:detail');
UPDATE sys_menu SET parent_id=(SELECT p.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:production:report:quantity' AND menu_type='C') p)
WHERE perms='business:production:report:supplement' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '管理员历史报工补录',p.menu_id,30,'#','F','business:production:report:supplement',1,1,'0','0','report-v1',NOW()
FROM sys_menu p WHERE p.perms='business:production:report:quantity' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='business:production:report:supplement');
UPDATE sys_menu SET parent_id=(SELECT p.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:production:report:quantity' AND menu_type='C') p)
WHERE perms='business:production:report:adjust' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '管理员报工数量订正',p.menu_id,30,'#','F','business:production:report:adjust',1,1,'0','0','report-v1',NOW()
FROM sys_menu p WHERE p.perms='business:production:report:quantity' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='business:production:report:adjust');
UPDATE sys_menu SET parent_id=(SELECT p.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:production:report:quantity' AND menu_type='C') p)
WHERE perms='business:production:report:void' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '管理员原始报工整笔冲销',p.menu_id,30,'#','F','business:production:report:void',1,1,'0','0','report-v1',NOW()
FROM sys_menu p WHERE p.perms='business:production:report:quantity' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms='business:production:report:void');
COMMIT;
