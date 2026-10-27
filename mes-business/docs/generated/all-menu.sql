
-- 来源：docs/generated/basic/material-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '物料产品', order_num = 0, path = 'material',
    component = 'basic/material/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:material:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '物料产品', directory.menu_id, 0, 'material', 'basic/material/index',
       'C', 'business:material:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '物料产品列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品列表', page.menu_id, 0, '#', 'F', 'business:material:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品详情', page.menu_id, 1, '#', 'F', 'business:material:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品新增', page.menu_id, 2, '#', 'F', 'business:material:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品修改', page.menu_id, 3, '#', 'F', 'business:material:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品删除', page.menu_id, 4, '#', 'F', 'business:material:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品同步', page.menu_id, 5, '#', 'F', 'business:material:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:sync' AND menu_type = 'F');

-- 来源：docs/generated/basic/materialCategory-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '物料产品分类', order_num = 0, path = 'materialCategory',
    component = 'basic/materialCategory/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:material:category:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '物料产品分类', directory.menu_id, 0, 'materialCategory', 'basic/materialCategory/index',
       'C', 'business:material:category:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '物料产品分类列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类列表', page.menu_id, 0, '#', 'F', 'business:material:category:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类详情', page.menu_id, 1, '#', 'F', 'business:material:category:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类新增', page.menu_id, 2, '#', 'F', 'business:material:category:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类修改', page.menu_id, 3, '#', 'F', 'business:material:category:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类删除', page.menu_id, 4, '#', 'F', 'business:material:category:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '物料产品分类同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:material:category:list' AND menu_type = 'C') page)
WHERE perms = 'business:material:category:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '物料产品分类同步', page.menu_id, 5, '#', 'F', 'business:material:category:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:material:category:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:material:category:sync' AND menu_type = 'F');

-- 来源：docs/generated/basic/safetyStock-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '安全库存配置', order_num = 0, path = 'safetyStock',
    component = 'basic/safetyStock/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:safety:stock:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '安全库存配置', directory.menu_id, 0, 'safetyStock', 'basic/safetyStock/index',
       'C', 'business:safety:stock:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '安全库存配置列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置列表', page.menu_id, 0, '#', 'F', 'business:safety:stock:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置详情', page.menu_id, 1, '#', 'F', 'business:safety:stock:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置新增', page.menu_id, 2, '#', 'F', 'business:safety:stock:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置修改', page.menu_id, 3, '#', 'F', 'business:safety:stock:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置删除', page.menu_id, 4, '#', 'F', 'business:safety:stock:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '安全库存配置同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:safety:stock:list' AND menu_type = 'C') page)
WHERE perms = 'business:safety:stock:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '安全库存配置同步', page.menu_id, 5, '#', 'F', 'business:safety:stock:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:safety:stock:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:safety:stock:sync' AND menu_type = 'F');

-- 来源：docs/generated/basic/unitMeasure-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '计量单位', order_num = 0, path = 'unitMeasure',
    component = 'basic/unitMeasure/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:unit:measure:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '计量单位', directory.menu_id, 0, 'unitMeasure', 'basic/unitMeasure/index',
       'C', 'business:unit:measure:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '计量单位列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位列表', page.menu_id, 0, '#', 'F', 'business:unit:measure:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位详情', page.menu_id, 1, '#', 'F', 'business:unit:measure:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位新增', page.menu_id, 2, '#', 'F', 'business:unit:measure:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位修改', page.menu_id, 3, '#', 'F', 'business:unit:measure:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位删除', page.menu_id, 4, '#', 'F', 'business:unit:measure:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '计量单位同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:unit:measure:list' AND menu_type = 'C') page)
WHERE perms = 'business:unit:measure:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '计量单位同步', page.menu_id, 5, '#', 'F', 'business:unit:measure:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:unit:measure:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:unit:measure:sync' AND menu_type = 'F');

-- 来源：docs/generated/basic/workshop-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '车间', order_num = 0, path = 'workshop',
    component = 'basic/workshop/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:workshop:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '车间', directory.menu_id, 0, 'workshop', 'basic/workshop/index',
       'C', 'business:workshop:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '车间列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间列表', page.menu_id, 0, '#', 'F', 'business:workshop:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间详情', page.menu_id, 1, '#', 'F', 'business:workshop:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间新增', page.menu_id, 2, '#', 'F', 'business:workshop:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间修改', page.menu_id, 3, '#', 'F', 'business:workshop:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间删除', page.menu_id, 4, '#', 'F', 'business:workshop:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '车间同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:workshop:list' AND menu_type = 'C') page)
WHERE perms = 'business:workshop:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '车间同步', page.menu_id, 5, '#', 'F', 'business:workshop:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:workshop:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:workshop:sync' AND menu_type = 'F');

-- 来源：docs/generated/basic/workstation-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '工作站', order_num = 0, path = 'workstation',
    component = 'basic/workstation/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:workstation:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工作站', directory.menu_id, 0, 'workstation', 'basic/workstation/index',
       'C', 'business:workstation:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
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

-- 来源：docs/generated/dv/dvSubject-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '设备点检保养项目', order_num = 0, path = 'dvSubject',
    component = 'dv/dvSubject/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:dv:subject:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '设备点检保养项目', directory.menu_id, 0, 'dvSubject', 'dv/dvSubject/index',
       'C', 'business:dv:subject:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
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

-- 来源：docs/generated/pro/proWorkorder-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '基础数据', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '基础数据', 0, 0, 'basic', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '生产工单', order_num = 0, path = 'proWorkorder',
    component = 'pro/proWorkorder/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'basic' AND menu_type = 'M') directory)
WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产工单', directory.menu_id, 0, 'proWorkorder', 'pro/proWorkorder/index',
       'C', 'business:pro:workorder:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'basic' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '生产工单列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单列表', page.menu_id, 0, '#', 'F', 'business:pro:workorder:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单详情', page.menu_id, 1, '#', 'F', 'business:pro:workorder:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单新增', page.menu_id, 2, '#', 'F', 'business:pro:workorder:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单修改', page.menu_id, 3, '#', 'F', 'business:pro:workorder:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单删除', page.menu_id, 4, '#', 'F', 'business:pro:workorder:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产工单同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:pro:workorder:list' AND menu_type = 'C') page)
WHERE perms = 'business:pro:workorder:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产工单同步', page.menu_id, 5, '#', 'F', 'business:pro:workorder:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:pro:workorder:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:pro:workorder:sync' AND menu_type = 'F');
