-- Apply after mes-business/docs/generated/material/material-menu.sql, in the center database.
-- Add-or-update only; do not grant roles automatically. Execute serially.
UPDATE sys_menu SET menu_name='物料同步',order_num=4,path='#',
 parent_id=(SELECT page.menu_id FROM (SELECT menu_id FROM sys_menu
 WHERE perms='business:material:read' AND menu_type='C') page)
WHERE perms='material:sync' AND menu_type='F';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '物料同步',menu_id,4,'#','F','material:sync','material-prototype',CURRENT_TIMESTAMP
FROM sys_menu WHERE perms='business:material:read' AND menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='material:sync' AND menu_type='F');
