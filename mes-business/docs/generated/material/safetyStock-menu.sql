-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '安全库存配置', order_num = 0, path = 'safetyStock',
    component = 'material/safetyStock/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:safety:stock:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '安全库存配置', directory.menu_id, 0, 'safetyStock', 'material/safetyStock/index',
       'C', 'business:safety:stock:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '安全库存配置查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:read' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置查询', page.menu_id, 0, '#', 'F', 'business:safety:stock:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:read' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置新增', page.menu_id, 1, '#', 'F', 'business:safety:stock:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:read' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置修改', page.menu_id, 2, '#', 'F', 'business:safety:stock:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:read' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置删除', page.menu_id, 3, '#', 'F', 'business:safety:stock:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:read' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置同步', page.menu_id, 4, '#', 'F', 'business:safety:stock:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:sync' AND menu_type = 'F');
