-- Retire material-issue ERP query/reconciliation entrypoints and their grants.
-- Local production approval, cancellation and immutable business facts are retained.
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE perms IN (
        'business:mes:material:issue:erp:query',
        'business:mes:material:issue:erp:reconcile'
    )
);
DELETE FROM sys_menu
WHERE perms IN (
    'business:mes:material:issue:erp:query',
    'business:mes:material:issue:erp:reconcile'
);
