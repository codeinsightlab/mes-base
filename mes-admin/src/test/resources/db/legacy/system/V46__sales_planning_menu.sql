-- 为销售客户、销售订单和生产计划基础页面登记中心菜单与操作权限，不自动绑定角色。
-- 页面路由和组件对齐现有前端入口；明细表不建立独立菜单。

UPDATE sys_menu SET menu_name='销售管理', order_num=1, icon='ShoppingCart'
WHERE parent_id=0 AND path='sales' AND menu_type='M';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,icon,create_by,create_time)
SELECT '销售管理',0,1,'sales','M','ShoppingCart','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='sales' AND menu_type='M');

UPDATE sys_menu SET menu_name='客户管理',order_num=1,path='customer',component='sales/customer/index',icon='User',
    parent_id=(SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='sales' AND menu_type='M') directory)
WHERE perms='business:mes:customer:list' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '客户管理',directory.menu_id,1,'customer','sales/customer/index','C','business:mes:customer:list','User','migration',CURRENT_TIMESTAMP
FROM sys_menu directory WHERE directory.parent_id=0 AND directory.path='sales' AND directory.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C');

UPDATE sys_menu SET menu_name='销售订单',order_num=2,path='order',component='sales/order/index',icon='Document',
    parent_id=(SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='sales' AND menu_type='M') directory)
WHERE perms='business:mes:sales:order:list' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '销售订单',directory.menu_id,2,'order','sales/order/index','C','business:mes:sales:order:list','Document','migration',CURRENT_TIMESTAMP
FROM sys_menu directory WHERE directory.parent_id=0 AND directory.path='sales' AND directory.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C');

UPDATE sys_menu SET menu_name='生产计划',order_num=1,path='plan',component='production/plan/index',icon='Calendar',
    parent_id=(SELECT directory.menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='production' AND menu_type='M') directory)
WHERE perms='business:mes:production:plan:list' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,create_by,create_time)
SELECT '生产计划',directory.menu_id,1,'plan','production/plan/index','C','business:mes:production:plan:list','Calendar','migration',CURRENT_TIMESTAMP
FROM sys_menu directory WHERE directory.parent_id=0 AND directory.path='production' AND directory.menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C');

-- 客户管理操作权限。
UPDATE sys_menu SET menu_name='销售客户列表',order_num=0,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C') page) WHERE perms='business:mes:customer:list' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售客户列表',page.menu_id,0,'#','F','business:mes:customer:list','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:customer:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售客户详情',order_num=1,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C') page) WHERE perms='business:mes:customer:query' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售客户详情',page.menu_id,1,'#','F','business:mes:customer:query','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:customer:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售客户新增',order_num=2,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C') page) WHERE perms='business:mes:customer:create' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售客户新增',page.menu_id,2,'#','F','business:mes:customer:create','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:customer:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售客户修改',order_num=3,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C') page) WHERE perms='business:mes:customer:update' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售客户修改',page.menu_id,3,'#','F','business:mes:customer:update','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:customer:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售客户删除',order_num=4,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:customer:list' AND menu_type='C') page) WHERE perms='business:mes:customer:delete' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售客户删除',page.menu_id,4,'#','F','business:mes:customer:delete','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:customer:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:customer:delete' AND menu_type='F');

-- 销售订单操作权限。
UPDATE sys_menu SET menu_name='销售订单列表',order_num=0,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C') page) WHERE perms='business:mes:sales:order:list' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售订单列表',page.menu_id,0,'#','F','business:mes:sales:order:list','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:sales:order:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售订单详情',order_num=1,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C') page) WHERE perms='business:mes:sales:order:query' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售订单详情',page.menu_id,1,'#','F','business:mes:sales:order:query','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:sales:order:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售订单新增',order_num=2,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C') page) WHERE perms='business:mes:sales:order:create' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售订单新增',page.menu_id,2,'#','F','business:mes:sales:order:create','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:sales:order:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售订单修改',order_num=3,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C') page) WHERE perms='business:mes:sales:order:update' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售订单修改',page.menu_id,3,'#','F','business:mes:sales:order:update','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:sales:order:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='销售订单删除',order_num=4,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:sales:order:list' AND menu_type='C') page) WHERE perms='business:mes:sales:order:delete' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '销售订单删除',page.menu_id,4,'#','F','business:mes:sales:order:delete','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:sales:order:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:sales:order:delete' AND menu_type='F');

-- 生产计划操作权限。
UPDATE sys_menu SET menu_name='生产计划列表',order_num=0,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C') page) WHERE perms='business:mes:production:plan:list' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生产计划列表',page.menu_id,0,'#','F','business:mes:production:plan:list','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:production:plan:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='F');
UPDATE sys_menu SET menu_name='生产计划详情',order_num=1,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C') page) WHERE perms='business:mes:production:plan:query' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生产计划详情',page.menu_id,1,'#','F','business:mes:production:plan:query','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:production:plan:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:query' AND menu_type='F');
UPDATE sys_menu SET menu_name='生产计划新增',order_num=2,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C') page) WHERE perms='business:mes:production:plan:create' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生产计划新增',page.menu_id,2,'#','F','business:mes:production:plan:create','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:production:plan:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:create' AND menu_type='F');
UPDATE sys_menu SET menu_name='生产计划修改',order_num=3,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C') page) WHERE perms='business:mes:production:plan:update' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生产计划修改',page.menu_id,3,'#','F','business:mes:production:plan:update','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:production:plan:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:update' AND menu_type='F');
UPDATE sys_menu SET menu_name='生产计划删除',order_num=4,path='#',parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='business:mes:production:plan:list' AND menu_type='C') page) WHERE perms='business:mes:production:plan:delete' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生产计划删除',page.menu_id,4,'#','F','business:mes:production:plan:delete','migration',CURRENT_TIMESTAMP FROM sys_menu page WHERE page.perms='business:mes:production:plan:list' AND page.menu_type='C' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:mes:production:plan:delete' AND menu_type='F');
