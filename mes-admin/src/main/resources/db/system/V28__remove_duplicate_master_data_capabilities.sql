-- 撤回旧车间与计量单位菜单、按钮和角色授权，保留正式基础数据入口。
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE component IN ('md/mdWorkshop/index','md/mdUnitMeasure/index')
       OR perms LIKE 'business:md:workshop:%' OR perms LIKE 'business:md:unit:measure:%'
);
DELETE FROM sys_menu
WHERE component IN ('md/mdWorkshop/index','md/mdUnitMeasure/index')
   OR perms LIKE 'business:md:workshop:%' OR perms LIKE 'business:md:unit:measure:%';

-- 清理已撤回页面的列偏好与同步状态，不影响其他页面和同步流。
DELETE FROM sys_user_table_preference
WHERE table_key IN ('business:md:workshop:list','business:md:unit:measure:list')
   OR url IN ('/basic/mdWorkshop','/basic/mdUnitMeasure','/md/mdWorkshop','/md/mdUnitMeasure');
DELETE FROM mes_master_sync WHERE sync_key IN ('codegen.md_workshop','codegen.md_unit_measure');
