-- 独立扫码投产入口复用既有正式领料更新权限，不新增或扩大任何角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '扫码投产',p.menu_id,35,'productionInput','production/productionInput/index','C','business:mes:material:issue:update','list',1,1,'0','0','production-input-v1',NOW()
FROM sys_menu p WHERE p.menu_id=2512 AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE path='productionInput' AND parent_id=p.menu_id);
