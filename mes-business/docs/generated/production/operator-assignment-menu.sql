
-- 来源：docs/generated/production/mesExecutionAssignment-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产基础', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产基础', 0, 0, 'production', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '生产节点派工', order_num = 0, path = 'mesExecutionAssignment',
    component = 'production/mesExecutionAssignment/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M') directory)
WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '生产节点派工', directory.menu_id, 0, 'mesExecutionAssignment', 'production/mesExecutionAssignment/index',
       'C', 'business:mes:execution:assignment:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'production' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '生产节点派工列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产节点派工列表', page.menu_id, 0, '#', 'F', 'business:mes:execution:assignment:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:execution:assignment:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产节点派工详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:execution:assignment:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产节点派工详情', page.menu_id, 1, '#', 'F', 'business:mes:execution:assignment:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:execution:assignment:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产节点派工新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:execution:assignment:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产节点派工新增', page.menu_id, 2, '#', 'F', 'business:mes:execution:assignment:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:execution:assignment:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产节点派工修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:execution:assignment:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产节点派工修改', page.menu_id, 3, '#', 'F', 'business:mes:execution:assignment:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:execution:assignment:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '生产节点派工删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:execution:assignment:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:execution:assignment:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '生产节点派工删除', page.menu_id, 4, '#', 'F', 'business:mes:execution:assignment:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:execution:assignment:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:execution:assignment:delete' AND menu_type = 'F');

-- 来源：docs/generated/production/mesOperationUser-menu.sql
-- 保留已有菜单 ID 和角色绑定，请在配置的菜单数据库串行执行。
-- 目录按 parentId + directoryPath 定位，显示名称不作为目录唯一标识。

UPDATE sys_menu SET menu_name = '生产基础', order_num = 0, icon = 'list'
WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, icon, create_by, create_time)
SELECT '生产基础', 0, 0, 'production', 'M', 'list', 'codegen', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M'
);

UPDATE sys_menu SET menu_name = '工序能力人员', order_num = 0, path = 'mesOperationUser',
    component = 'production/mesOperationUser/index', icon = 'list',
    parent_id = (SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE parent_id = 0 AND path = 'production' AND menu_type = 'M') directory)
WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, perms, icon, create_by, create_time)
SELECT '工序能力人员', directory.menu_id, 0, 'mesOperationUser', 'production/mesOperationUser/index',
       'C', 'business:mes:operation:user:list', 'list', 'codegen', CURRENT_TIMESTAMP
FROM sys_menu directory
WHERE directory.parent_id = 0 AND directory.path = 'production' AND directory.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C');

UPDATE sys_menu SET menu_name = '工序能力人员列表', order_num = 0, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序能力人员列表', page.menu_id, 0, '#', 'F', 'business:mes:operation:user:list',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:operation:user:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序能力人员详情', order_num = 1, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:operation:user:query' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序能力人员详情', page.menu_id, 1, '#', 'F', 'business:mes:operation:user:query',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:operation:user:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:query' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序能力人员新增', order_num = 2, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:operation:user:create' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序能力人员新增', page.menu_id, 2, '#', 'F', 'business:mes:operation:user:create',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:operation:user:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:create' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序能力人员修改', order_num = 3, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:operation:user:update' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序能力人员修改', page.menu_id, 3, '#', 'F', 'business:mes:operation:user:update',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:operation:user:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:update' AND menu_type = 'F');
UPDATE sys_menu SET menu_name = '工序能力人员删除', order_num = 4, path = '#',
    parent_id = (SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
        WHERE perms = 'business:mes:operation:user:list' AND menu_type = 'C') page)
WHERE perms = 'business:mes:operation:user:delete' AND menu_type = 'F';
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, perms, create_by, create_time)
SELECT '工序能力人员删除', page.menu_id, 4, '#', 'F', 'business:mes:operation:user:delete',
       'codegen', CURRENT_TIMESTAMP
FROM sys_menu page
WHERE page.perms = 'business:mes:operation:user:list' AND page.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'business:mes:operation:user:delete' AND menu_type = 'F');

-- 生产执行与节点派工统一从工单进入；保留菜单编号、独立权限及角色绑定。
-- 隐藏的旧路由仅用于兼容书签，前端跳转到生产工单。
UPDATE sys_menu SET visible = '1'
WHERE menu_type = 'C' AND component IN ('production/mesProductionExecution/index', 'production/mesExecutionAssignment/index')
  AND perms IN ('business:mes:production:execution:list', 'business:mes:execution:assignment:list');

-- 工序能力人员从工序编辑进入，保留授权与旧地址兼容。
UPDATE sys_menu SET visible = '1'
WHERE menu_type = 'C' AND component = 'production/mesOperationUser/index'
  AND perms = 'business:mes:operation:user:list';
