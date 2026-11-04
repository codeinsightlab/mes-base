-- 仅为已持有相同领料更新权限的角色补齐独立菜单；不改变任何角色有效权限集合。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT oldGrant.role_id,target.menu_id
FROM sys_role_menu oldGrant
JOIN sys_menu oldMenu ON oldMenu.menu_id=oldGrant.menu_id AND oldMenu.perms='business:mes:material:issue:update'
JOIN sys_menu target ON target.path='productionInput' AND target.parent_id=2512 AND target.perms=oldMenu.perms
WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu currentGrant WHERE currentGrant.role_id=oldGrant.role_id AND currentGrant.menu_id=target.menu_id);
-- 生产管理目录没有权限，只补导航父节点，不给同级其他菜单授权。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT granted.role_id,parent.menu_id
FROM sys_role_menu granted
JOIN sys_menu child ON child.menu_id=granted.menu_id AND child.path='productionInput' AND child.parent_id=2512
JOIN sys_menu parent ON parent.menu_id=child.parent_id AND parent.menu_type='M' AND (parent.perms IS NULL OR parent.perms='')
WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu currentGrant WHERE currentGrant.role_id=granted.role_id AND currentGrant.menu_id=parent.menu_id);
