-- 用户修订：分类维护恢复独立页面，物料工作区分类树仅负责筛选。
-- V43 已执行，保留历史迁移，按原菜单主键恢复，不扩大角色权限。
SET @category_page=(SELECT menu_id FROM sys_menu WHERE perms='business:material:category:list' AND menu_type='F' AND path='#' LIMIT 1);
SET @material_page=(SELECT menu_id FROM sys_menu WHERE component='basic/materials/index' AND menu_type='C' LIMIT 1);
SET @basic_parent=(SELECT parent_id FROM sys_menu WHERE menu_id=@material_page);
UPDATE sys_menu SET menu_name='物料分类',menu_type='C',path='materialCategory',component='basic/materialCategory/index',parent_id=@basic_parent,visible='0'
WHERE menu_id=@category_page AND @basic_parent IS NOT NULL;
UPDATE sys_menu SET parent_id=@category_page
WHERE parent_id=@material_page AND menu_type='F' AND perms LIKE 'business:material:category:%' AND menu_id<>@category_page AND @category_page IS NOT NULL;
