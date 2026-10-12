-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'dv' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'dv', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'dv' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '设备点检保养项目', order_num = 0, path = 'dvSubject',
    component = 'dv/dvSubject/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'dv' AND menu_type = 'M') directory)
WHERE perms = 'business:dv:subject:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '设备点检保养项目', directory.menu_id, 0, 'dvSubject', 'dv/dvSubject/index',
       'C', 'business:dv:subject:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'dv' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '设备点检保养项目列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目列表', page.menu_id, 0, '#', 'F', 'business:dv:subject:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备点检保养项目详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目详情', page.menu_id, 1, '#', 'F', 'business:dv:subject:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备点检保养项目新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目新增', page.menu_id, 2, '#', 'F', 'business:dv:subject:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备点检保养项目修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目修改', page.menu_id, 3, '#', 'F', 'business:dv:subject:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备点检保养项目删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目删除', page.menu_id, 4, '#', 'F', 'business:dv:subject:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '设备点检保养项目同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:dv:subject:list' AND menu_type = 'C') page)
WHERE perms = 'business:dv:subject:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '设备点检保养项目同步', page.menu_id, 5, '#', 'F', 'business:dv:subject:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:dv:subject:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:dv:subject:sync' AND menu_type = 'F');
