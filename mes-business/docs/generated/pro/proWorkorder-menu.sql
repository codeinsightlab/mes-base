-- MES-CODEGEN: run in the center database after review; no role grants.
-- Directory path follows --module; permission prefix follows --backend-module.
-- Rename this table's previous permissions while preserving menu IDs and role bindings.
UPDATE sys_menu SET perms = 'business:pro:workorder:read'
WHERE perms = 'business:pro_workorder:read';
UPDATE sys_menu SET perms = 'business:pro:workorder:create'
WHERE perms = 'business:pro_workorder:create';
UPDATE sys_menu SET perms = 'business:pro:workorder:update'
WHERE perms = 'business:pro_workorder:update';
UPDATE sys_menu SET perms = 'business:pro:workorder:delete'
WHERE perms = 'business:pro_workorder:delete';

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT 'pro', 0, 0, 'pro', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'pro' AND menu_type = 'M'
);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产工单', directory.menu_id, 0, 'proWorkorder', 'pro/proWorkorder/index',
       'C', 'business:pro:workorder:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'pro' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:read' AND menu_type = 'C');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单查询', page.menu_id, 0, '#', 'F', 'business:pro:workorder:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:read' AND menu_type = 'F');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单新增', page.menu_id, 1, '#', 'F', 'business:pro:workorder:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:create' AND menu_type = 'F');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单修改', page.menu_id, 2, '#', 'F', 'business:pro:workorder:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:update' AND menu_type = 'F');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单删除', page.menu_id, 3, '#', 'F', 'business:pro:workorder:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:delete' AND menu_type = 'F');

