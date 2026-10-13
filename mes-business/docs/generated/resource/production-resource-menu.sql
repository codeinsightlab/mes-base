
-- 来源：docs/generated/basic/equipment-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '设备', order_num = 0, path = 'equipment',
    component = 'basic/equipment/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:equipment:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '设备', directory.menu_id, 0, 'equipment', 'basic/equipment/index',
       'C', 'business:equipment:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:equipment:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '设备查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:equipment:read' AND menu_type = 'C') page)
WHERE perms = 'business:equipment:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备查询', page.menu_id, 0, '#', 'F', 'business:equipment:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:equipment:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:equipment:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:equipment:read' AND menu_type = 'C') page)
WHERE perms = 'business:equipment:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备新增', page.menu_id, 1, '#', 'F', 'business:equipment:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:equipment:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:equipment:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:equipment:read' AND menu_type = 'C') page)
WHERE perms = 'business:equipment:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备修改', page.menu_id, 2, '#', 'F', 'business:equipment:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:equipment:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:equipment:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:equipment:read' AND menu_type = 'C') page)
WHERE perms = 'business:equipment:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备删除', page.menu_id, 3, '#', 'F', 'business:equipment:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:equipment:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:equipment:delete' AND menu_type = 'F');

-- 来源：docs/generated/resource/personnel-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产资源', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'resource' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产资源', 0, 0, 'resource', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'resource' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '生产人员', order_num = 0, path = 'personnel',
    component = 'resource/personnel/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'resource' AND menu_type = 'M') directory)
WHERE perms = 'business:personnel:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产人员', directory.menu_id, 0, 'personnel', 'resource/personnel/index',
       'C', 'business:personnel:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'resource' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:personnel:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '生产人员查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:personnel:read' AND menu_type = 'C') page)
WHERE perms = 'business:personnel:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产人员查询', page.menu_id, 0, '#', 'F', 'business:personnel:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:personnel:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:personnel:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产人员新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:personnel:read' AND menu_type = 'C') page)
WHERE perms = 'business:personnel:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产人员新增', page.menu_id, 1, '#', 'F', 'business:personnel:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:personnel:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:personnel:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产人员修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:personnel:read' AND menu_type = 'C') page)
WHERE perms = 'business:personnel:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产人员修改', page.menu_id, 2, '#', 'F', 'business:personnel:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:personnel:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:personnel:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产人员删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:personnel:read' AND menu_type = 'C') page)
WHERE perms = 'business:personnel:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产人员删除', page.menu_id, 3, '#', 'F', 'business:personnel:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:personnel:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:personnel:delete' AND menu_type = 'F');

-- 可选关联账号复用中心只读人员目录；独立查询权限，不授予平台账号管理权限。
UPDATE sys_menu SET menu_name='关联账号查询', parent_id=(SELECT p.menu_id FROM
    (SELECT menu_id FROM sys_menu WHERE perms='business:personnel:read' AND menu_type='C') p)
WHERE perms='business:directory:personnel:read' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '关联账号查询',p.menu_id,5,'#','F','business:directory:personnel:read','codegen',CURRENT_TIMESTAMP
FROM sys_menu p WHERE p.perms='business:personnel:read' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='business:directory:personnel:read' AND existing.menu_type='F');
