-- 第一批简单业务菜单，审查后只在开发中心库串行执行；不包含角色授权。
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '物料产品', order_num = 0, path = 'material',
    component = 'material/material/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:material:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '物料产品', directory.menu_id, 0, 'material', 'material/material/index',
       'C', 'business:material:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '物料产品查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品查询', page.menu_id, 0, '#', 'F', 'business:material:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品新增', page.menu_id, 1, '#', 'F', 'business:material:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品修改', page.menu_id, 2, '#', 'F', 'business:material:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品删除', page.menu_id, 3, '#', 'F', 'business:material:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品同步', page.menu_id, 4, '#', 'F', 'business:material:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:sync' AND menu_type = 'F');

-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '物料产品分类', order_num = 0, path = 'materialCategory',
    component = 'material/materialCategory/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:material:category:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '物料产品分类', directory.menu_id, 0, 'materialCategory', 'material/materialCategory/index',
       'C', 'business:material:category:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '物料产品分类查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类查询', page.menu_id, 0, '#', 'F', 'business:material:category:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类新增', page.menu_id, 1, '#', 'F', 'business:material:category:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类修改', page.menu_id, 2, '#', 'F', 'business:material:category:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类删除', page.menu_id, 3, '#', 'F', 'business:material:category:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:read' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类同步', page.menu_id, 4, '#', 'F', 'business:material:category:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:sync' AND menu_type = 'F');

-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '计量单位', order_num = 0, path = 'unitMeasure',
    component = 'material/unitMeasure/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:unit:measure:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '计量单位', directory.menu_id, 0, 'unitMeasure', 'material/unitMeasure/index',
       'C', 'business:unit:measure:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '计量单位查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:read' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位查询', page.menu_id, 0, '#', 'F', 'business:unit:measure:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:read' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位新增', page.menu_id, 1, '#', 'F', 'business:unit:measure:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:read' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位修改', page.menu_id, 2, '#', 'F', 'business:unit:measure:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:read' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位删除', page.menu_id, 3, '#', 'F', 'business:unit:measure:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:read' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位同步', page.menu_id, 4, '#', 'F', 'business:unit:measure:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:sync' AND menu_type = 'F');

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
WHERE perms = 'business:workstation:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工作站', directory.menu_id, 0, 'workstation', 'material/workstation/index',
       'C', 'business:workstation:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '工作站查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:read' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站查询', page.menu_id, 0, '#', 'F', 'business:workstation:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:read' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站新增', page.menu_id, 1, '#', 'F', 'business:workstation:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:read' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站修改', page.menu_id, 2, '#', 'F', 'business:workstation:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:read' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站删除', page.menu_id, 3, '#', 'F', 'business:workstation:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工作站同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workstation:read' AND menu_type = 'C') page)
WHERE perms = 'business:workstation:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工作站同步', page.menu_id, 4, '#', 'F', 'business:workstation:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workstation:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workstation:sync' AND menu_type = 'F');

-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- Keep existing menu IDs and role bindings. Execute serially in the configured menu database.

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'material', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '车间', order_num = 0, path = 'workshop',
    component = 'material/workshop/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'material' AND menu_type = 'M') directory)
WHERE perms = 'business:workshop:read' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '车间', directory.menu_id, 0, 'workshop', 'material/workshop/index',
       'C', 'business:workshop:read', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'material' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:read' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '车间查询', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:read' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:read' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间查询', page.menu_id, 0, '#', 'F', 'business:workshop:read',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:read' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间新增', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:read' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间新增', page.menu_id, 1, '#', 'F', 'business:workshop:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间修改', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:read' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间修改', page.menu_id, 2, '#', 'F', 'business:workshop:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间删除', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:read' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间删除', page.menu_id, 3, '#', 'F', 'business:workshop:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间同步', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:read' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间同步', page.menu_id, 4, '#', 'F', 'business:workshop:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:read' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:sync' AND menu_type = 'F');

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

UPDATE sys_menu SET order_num=10 WHERE component='material/material/index' AND menu_type='C';
UPDATE sys_menu SET order_num=20 WHERE component='material/materialCategory/index' AND menu_type='C';
UPDATE sys_menu SET order_num=30 WHERE component='material/unitMeasure/index' AND menu_type='C';
UPDATE sys_menu SET order_num=40 WHERE component='material/workstation/index' AND menu_type='C';
UPDATE sys_menu SET order_num=50 WHERE component='material/workshop/index' AND menu_type='C';
UPDATE sys_menu SET order_num=60 WHERE component='material/safetyStock/index' AND menu_type='C';
