-- MES-CODEGEN: 本批次合并菜单 SQL。

-- 来源：docs/generated/production/operation-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产基础', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产基础', 0, 0, 'production', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '工序', order_num = 0, path = 'operation',
    component = 'production/operation/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M') directory)
WHERE perms = 'operation:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工序', directory.menu_id, 0, 'operation', 'production/operation/index',
       'C', 'operation:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'production' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '工序列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'operation:list' AND menu_type = 'C') page)
WHERE perms = 'operation:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序列表', page.menu_id, 0, '#', 'F', 'operation:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'operation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'operation:list' AND menu_type = 'C') page)
WHERE perms = 'operation:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序详情', page.menu_id, 1, '#', 'F', 'operation:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'operation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'operation:list' AND menu_type = 'C') page)
WHERE perms = 'operation:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序新增', page.menu_id, 2, '#', 'F', 'operation:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'operation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'operation:list' AND menu_type = 'C') page)
WHERE perms = 'operation:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序修改', page.menu_id, 3, '#', 'F', 'operation:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'operation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'operation:list' AND menu_type = 'C') page)
WHERE perms = 'operation:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序删除', page.menu_id, 4, '#', 'F', 'operation:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'operation:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'operation:delete' AND menu_type = 'F');

-- 来源：docs/generated/production/routing-menu.sql
-- MES-CODEGEN: reviewed add-or-update menu SQL; no role grants.
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产基础', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产基础', 0, 0, 'production', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '工艺路线', order_num = 0, path = 'routing',
    component = 'production/routing/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M') directory)
WHERE perms = 'routing:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工艺路线', directory.menu_id, 0, 'routing', 'production/routing/index',
       'C', 'routing:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'production' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '工艺路线列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'routing:list' AND menu_type = 'C') page)
WHERE perms = 'routing:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工艺路线列表', page.menu_id, 0, '#', 'F', 'routing:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'routing:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工艺路线详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'routing:list' AND menu_type = 'C') page)
WHERE perms = 'routing:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工艺路线详情', page.menu_id, 1, '#', 'F', 'routing:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'routing:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工艺路线新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'routing:list' AND menu_type = 'C') page)
WHERE perms = 'routing:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工艺路线新增', page.menu_id, 2, '#', 'F', 'routing:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'routing:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工艺路线修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'routing:list' AND menu_type = 'C') page)
WHERE perms = 'routing:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工艺路线修改', page.menu_id, 3, '#', 'F', 'routing:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'routing:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工艺路线删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'routing:list' AND menu_type = 'C') page)
WHERE perms = 'routing:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工艺路线删除', page.menu_id, 4, '#', 'F', 'routing:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'routing:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'routing:delete' AND menu_type = 'F');


-- 本轮生命周期动作与同步使用独立权限，不隐式授予角色。
UPDATE sys_menu SET menu_name='同步',parent_id=(SELECT page.menu_id FROM
(SELECT menu_id FROM sys_menu WHERE perms='operation:list' AND menu_type='C') page),status='0'
WHERE perms='operation:sync' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '同步',page.menu_id,10,'#','F','operation:sync','codegen',CURRENT_TIMESTAMP
FROM sys_menu page WHERE page.perms='operation:list' AND page.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='operation:sync' AND existing.menu_type='F');
UPDATE sys_menu SET menu_name='同步',parent_id=(SELECT page.menu_id FROM
(SELECT menu_id FROM sys_menu WHERE perms='routing:list' AND menu_type='C') page),status='0'
WHERE perms='routing:sync' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '同步',page.menu_id,10,'#','F','routing:sync','codegen',CURRENT_TIMESTAMP
FROM sys_menu page WHERE page.perms='routing:list' AND page.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='routing:sync' AND existing.menu_type='F');
UPDATE sys_menu SET menu_name='发布与启停',parent_id=(SELECT page.menu_id FROM
(SELECT menu_id FROM sys_menu WHERE perms='routing:list' AND menu_type='C') page),status='0'
WHERE perms='routing:publish' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '发布与启停',page.menu_id,10,'#','F','routing:publish','codegen',CURRENT_TIMESTAMP
FROM sys_menu page WHERE page.perms='routing:list' AND page.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='routing:publish' AND existing.menu_type='F');
UPDATE sys_menu SET menu_name='复制',parent_id=(SELECT page.menu_id FROM
(SELECT menu_id FROM sys_menu WHERE perms='routing:list' AND menu_type='C') page),status='0'
WHERE perms='routing:copy' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '复制',page.menu_id,10,'#','F','routing:copy','codegen',CURRENT_TIMESTAMP
FROM sys_menu page WHERE page.perms='routing:list' AND page.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='routing:copy' AND existing.menu_type='F');
UPDATE sys_menu SET menu_name='创建新版本',parent_id=(SELECT page.menu_id FROM
(SELECT menu_id FROM sys_menu WHERE perms='routing:list' AND menu_type='C') page),status='0'
WHERE perms='routing:version' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '创建新版本',page.menu_id,10,'#','F','routing:version','codegen',CURRENT_TIMESTAMP
FROM sys_menu page WHERE page.perms='routing:list' AND page.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='routing:version' AND existing.menu_type='F');
