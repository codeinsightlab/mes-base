-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '工作站', order_num = 0, path = 'workstation',
    component = 'material/workstation/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:workstation:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工作站', directory.menu_id, 0, 'workstation', 'material/workstation/index',
       'C', 'business:workstation:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '工作站列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站列表', page.menu_id, 0, '#', 'F', 'business:workstation:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站详情', page.menu_id, 1, '#', 'F', 'business:workstation:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站新增', page.menu_id, 2, '#', 'F', 'business:workstation:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站修改', page.menu_id, 3, '#', 'F', 'business:workstation:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站删除', page.menu_id, 4, '#', 'F', 'business:workstation:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:list' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站同步', page.menu_id, 5, '#', 'F', 'business:workstation:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:sync' AND menu_type = 'F');
