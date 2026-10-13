-- MES-CODEGEN: 本批次合并菜单 SQL。

-- 来源：docs/generated/wm/wmInventory-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '物料库存', order_num = 1, icon = 'list'
WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '物料库存', 0, 1, 'stock', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '库存管理', order_num = 0, path = 'wmInventory',
    component = 'wm/wmInventory/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M') directory)
WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '库存管理', directory.menu_id, 0, 'wmInventory', 'wm/wmInventory/index',
       'C', 'business:wm:inventory:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'stock' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '库存管理列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理列表', page.menu_id, 0, '#', 'F', 'business:wm:inventory:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '库存管理详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理详情', page.menu_id, 1, '#', 'F', 'business:wm:inventory:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '库存管理新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理新增', page.menu_id, 2, '#', 'F', 'business:wm:inventory:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '库存管理修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理修改', page.menu_id, 3, '#', 'F', 'business:wm:inventory:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '库存管理删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理删除', page.menu_id, 4, '#', 'F', 'business:wm:inventory:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '库存管理同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:inventory:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:inventory:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '库存管理同步', page.menu_id, 5, '#', 'F', 'business:wm:inventory:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:inventory:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:inventory:sync' AND menu_type = 'F');

-- 来源：docs/generated/wm/wmWarehouse-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '物料库存', order_num = 1, icon = 'list'
WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '物料库存', 0, 1, 'stock', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '仓库管理', order_num = 0, path = 'wmWarehouse',
    component = 'wm/wmWarehouse/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'stock' AND menu_type = 'M') directory)
WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '仓库管理', directory.menu_id, 0, 'wmWarehouse', 'wm/wmWarehouse/index',
       'C', 'business:wm:warehouse:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'stock' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '仓库管理列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理列表', page.menu_id, 0, '#', 'F', 'business:wm:warehouse:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '仓库管理详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理详情', page.menu_id, 1, '#', 'F', 'business:wm:warehouse:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '仓库管理新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理新增', page.menu_id, 2, '#', 'F', 'business:wm:warehouse:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '仓库管理修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理修改', page.menu_id, 3, '#', 'F', 'business:wm:warehouse:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '仓库管理删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理删除', page.menu_id, 4, '#', 'F', 'business:wm:warehouse:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:delete' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '仓库管理同步', order_num = 5, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:wm:warehouse:list' AND menu_type = 'C') page)
WHERE perms = 'business:wm:warehouse:sync' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '仓库管理同步', page.menu_id, 5, '#', 'F', 'business:wm:warehouse:sync',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:wm:warehouse:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:wm:warehouse:sync' AND menu_type = 'F');
