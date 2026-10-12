-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = 'pro', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'pro' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT 'pro', 0, 0, 'pro', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'pro' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '生产工单', order_num = 0, path = 'proWorkorder',
    component = 'pro/proWorkorder/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'pro' AND menu_type = 'M') directory)
WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产工单', directory.menu_id, 0, 'proWorkorder', 'pro/proWorkorder/index',
       'C', 'business:pro:workorder:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'pro' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '生产工单查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单查询', page.menu_id, 0, '#', 'F', 'business:pro:workorder:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单新增', page.menu_id, 1, '#', 'F', 'business:pro:workorder:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单修改', page.menu_id, 2, '#', 'F', 'business:pro:workorder:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单删除', page.menu_id, 3, '#', 'F', 'business:pro:workorder:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单同步', page.menu_id, 4, '#', 'F', 'business:pro:workorder:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:sync' AND menu_type = 'F');
