-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'qc', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '质量检测项', order_num = 0, path = 'qcIndex',
    component = 'qc/qcIndex/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M') directory)
WHERE perms = 'business:qc:index:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '质量检测项', directory.menu_id, 0, 'qcIndex', 'qc/qcIndex/index',
       'C', 'business:qc:index:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'qc' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '质量检测项列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项列表', page.menu_id, 0, '#', 'F', 'business:qc:index:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量检测项详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项详情', page.menu_id, 1, '#', 'F', 'business:qc:index:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量检测项新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项新增', page.menu_id, 2, '#', 'F', 'business:qc:index:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量检测项修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项修改', page.menu_id, 3, '#', 'F', 'business:qc:index:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量检测项删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项删除', page.menu_id, 4, '#', 'F', 'business:qc:index:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量检测项同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:index:list' AND menu_type = 'C') page)
WHERE perms = 'business:qc:index:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量检测项同步', page.menu_id, 5, '#', 'F', 'business:qc:index:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:index:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:index:sync' AND menu_type = 'F');
