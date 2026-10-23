-- 合并分类与物料入口，保留原菜单主键及角色权限，不新增业务授权。
SET @material_page=(SELECT menu_id FROM sys_menu WHERE component='basic/material/index' AND menu_type='C' LIMIT 1);
SET @material_list=(SELECT menu_id FROM sys_menu WHERE perms='business:material:list' AND menu_type='F' LIMIT 1);
SET @category_page=(SELECT menu_id FROM sys_menu WHERE component='basic/materialCategory/index' AND menu_type='C' LIMIT 1);

-- 原页面本身携带的列表权限转交同权限按钮，随后页面成为共同工作区。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT rm.role_id,@material_list FROM sys_role_menu rm
WHERE rm.menu_id=@material_page AND @material_list IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM sys_role_menu granted WHERE granted.role_id=rm.role_id AND granted.menu_id=@material_list);
UPDATE sys_menu SET menu_name='物料管理',path='materials',component='basic/materials/index',perms=NULL
WHERE menu_id=@material_page;
UPDATE sys_menu SET parent_id=@material_page WHERE parent_id=@category_page AND @material_page IS NOT NULL;
UPDATE sys_menu SET menu_name='物料分类列表',menu_type='F',path='#',component=NULL,parent_id=@material_page,visible='1'
WHERE menu_id=@category_page AND @material_page IS NOT NULL;
