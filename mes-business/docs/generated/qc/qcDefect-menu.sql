-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = 'qc', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT 'qc', 0, 0, 'qc', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '质量缺陷', order_num = 0, path = 'qcDefect',
    component = 'qc/qcDefect/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'qc' AND menu_type = 'M') directory)
WHERE perms = 'business:qc:defect:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '质量缺陷', directory.menu_id, 0, 'qcDefect', 'qc/qcDefect/index',
       'C', 'business:qc:defect:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'qc' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '质量缺陷查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:defect:read' AND menu_type = 'C') page)
WHERE perms = 'business:qc:defect:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量缺陷查询', page.menu_id, 0, '#', 'F', 'business:qc:defect:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:defect:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量缺陷新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:defect:read' AND menu_type = 'C') page)
WHERE perms = 'business:qc:defect:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量缺陷新增', page.menu_id, 1, '#', 'F', 'business:qc:defect:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:defect:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量缺陷修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:defect:read' AND menu_type = 'C') page)
WHERE perms = 'business:qc:defect:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量缺陷修改', page.menu_id, 2, '#', 'F', 'business:qc:defect:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:defect:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量缺陷删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:defect:read' AND menu_type = 'C') page)
WHERE perms = 'business:qc:defect:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量缺陷删除', page.menu_id, 3, '#', 'F', 'business:qc:defect:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:defect:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '质量缺陷同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:qc:defect:read' AND menu_type = 'C') page)
WHERE perms = 'business:qc:defect:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '质量缺陷同步', page.menu_id, 4, '#', 'F', 'business:qc:defect:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:qc:defect:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:qc:defect:sync' AND menu_type = 'F');
