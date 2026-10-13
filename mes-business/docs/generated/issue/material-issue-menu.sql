-- MES-CODEGEN: 本批次合并菜单 SQL。

-- 来源：docs/generated/issue/mesMaterialIssue-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '领料出库', order_num = 1, icon = 'list'
WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '领料出库', 0, 1, 'issue', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '领料出库单', order_num = 0, path = 'mesMaterialIssue',
    component = 'issue/mesMaterialIssue/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M') directory)
WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '领料出库单', directory.menu_id, 0, 'mesMaterialIssue', 'issue/mesMaterialIssue/index',
       'C', 'business:mes:material:issue:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'issue' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '领料出库单列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库单列表', page.menu_id, 0, '#', 'F', 'business:mes:material:issue:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库单详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库单详情', page.menu_id, 1, '#', 'F', 'business:mes:material:issue:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库单新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库单新增', page.menu_id, 2, '#', 'F', 'business:mes:material:issue:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库单修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库单修改', page.menu_id, 3, '#', 'F', 'business:mes:material:issue:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库单删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库单删除', page.menu_id, 4, '#', 'F', 'business:mes:material:issue:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:delete' AND menu_type = 'F');

-- 来源：docs/generated/issue/mesMaterialIssueItem-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '领料出库', order_num = 1, icon = 'list'
WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '领料出库', 0, 1, 'issue', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '领料出库明细', order_num = 0, path = 'mesMaterialIssueItem',
    component = 'issue/mesMaterialIssueItem/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'issue' AND menu_type = 'M') directory)
WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '领料出库明细', directory.menu_id, 0, 'mesMaterialIssueItem', 'issue/mesMaterialIssueItem/index',
       'C', 'business:mes:material:issue:item:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'issue' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '领料出库明细列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库明细列表', page.menu_id, 0, '#', 'F', 'business:mes:material:issue:item:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:item:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库明细详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:item:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库明细详情', page.menu_id, 1, '#', 'F', 'business:mes:material:issue:item:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:item:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库明细新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:item:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库明细新增', page.menu_id, 2, '#', 'F', 'business:mes:material:issue:item:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:item:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库明细修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:item:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库明细修改', page.menu_id, 3, '#', 'F', 'business:mes:material:issue:item:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:item:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '领料出库明细删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:material:issue:item:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:material:issue:item:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '领料出库明细删除', page.menu_id, 4, '#', 'F', 'business:mes:material:issue:item:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:material:issue:item:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:material:issue:item:delete' AND menu_type = 'F');
