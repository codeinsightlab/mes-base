-- 正式生产报工独立入口，复用生产管理目录；不自动扩大任何角色授权。
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,icon,is_frame,is_cache,visible,status,create_by,create_time)
SELECT '生产报工',p.menu_id,40,'productionReporting','production/productionReporting/index','C','business:production:report:submit','list',1,1,'0','0','report-v2',NOW()
FROM sys_menu p WHERE p.menu_id=2512
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='business:production:report:submit');
