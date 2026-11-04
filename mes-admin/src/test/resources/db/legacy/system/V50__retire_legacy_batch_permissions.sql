-- Remove retired batch/path permissions and grants; no live endpoint uses them.
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE menu_type='F' AND perms IN ('batch:query','batch:selectRoute','workorder:history:query')
);
DELETE FROM sys_menu
WHERE menu_type='F' AND perms IN ('batch:query','batch:selectRoute','workorder:history:query');
