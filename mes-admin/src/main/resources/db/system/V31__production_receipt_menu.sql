-- 独立交接管理入口，不授予角色；新按钮继承业务菜单范围。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '生产交接管理',p.menu_id,50,'productionReceipt','production/productionReceipt/index','C','business:production:receipt:list','list',1,1,'0','0','receipt-v1',NOW()
FROM sys_menu p WHERE p.menu_id=2512 AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:production:receipt:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,is_frame,is_cache,visible,status,create_by,create_time)
SELECT a.name,p.menu_id,a.ord,'#','F',a.perm,1,1,'0','0','receipt-v1',NOW()
FROM sys_menu p JOIN (
 SELECT '交接详情' name,1 ord,'business:production:receipt:detail' perm
 UNION ALL SELECT '登记交接',2,'business:production:receipt:create'
 UNION ALL SELECT '交接冲销',3,'business:production:receipt:reverse'
) a ON 1=1 WHERE p.perms='business:production:receipt:list'
AND NOT EXISTS(SELECT 1 FROM sys_menu x WHERE x.perms=a.perm);
