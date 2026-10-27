
-- 来源：docs/generated/production/mesProductionExecution-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产基础', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产基础', 0, 0, 'production', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '生产执行单元', order_num = 0, path = 'mesProductionExecution',
    component = 'production/mesProductionExecution/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M') directory)
WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产执行单元', directory.menu_id, 0, 'mesProductionExecution', 'production/mesProductionExecution/index',
       'C', 'business:mes:production:execution:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'production' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '生产执行单元列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产执行单元列表', page.menu_id, 0, '#', 'F', 'business:mes:production:execution:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:production:execution:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产执行单元详情', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:production:execution:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产执行单元详情', page.menu_id, 1, '#', 'F', 'business:mes:production:execution:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:production:execution:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产执行单元新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:production:execution:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产执行单元新增', page.menu_id, 2, '#', 'F', 'business:mes:production:execution:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:production:execution:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产执行单元修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:production:execution:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产执行单元修改', page.menu_id, 3, '#', 'F', 'business:mes:production:execution:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:production:execution:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产执行单元删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:production:execution:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:production:execution:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产执行单元删除', page.menu_id, 4, '#', 'F', 'business:mes:production:execution:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:production:execution:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:production:execution:delete' AND menu_type = 'F');

-- 生产执行与节点派工统一从工单进入；保留菜单编号、独立权限及角色绑定。
-- 隐藏的旧路由仅用于兼容书签，前端跳转到生产工单。
UPDATE sys_menu SET visible = '1'
WHERE menu_type = 'C' AND component IN ('production/mesProductionExecution/index', 'production/mesExecutionAssignment/index')
  AND perms IN ('business:mes:production:execution:list', 'business:mes:execution:assignment:list');
