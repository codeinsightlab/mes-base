-- Retire only independent quality release write permissions and their role grants.
-- Read-only history permission and the immutable business audit ledger remain intact.
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE perms IN ('business:quality:release:create', 'business:quality:release:revoke')
);
DELETE FROM sys_menu
WHERE perms IN ('business:quality:release:create', 'business:quality:release:revoke');
