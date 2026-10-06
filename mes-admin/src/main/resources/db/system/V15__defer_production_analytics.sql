-- Defer the two business features; retain V13 unchanged for existing Flyway histories.
-- Remove their role grants before menus. Factory/datasource management is unaffected.
DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE component IN ('mes/pro/productionMonitor/index', 'mes/pro/personnelAnalysis/index')
       OR perms LIKE 'mes:pro:productionMonitor:%'
       OR perms LIKE 'mes:pro:personnelAnalysis:%'
);

DELETE FROM sys_menu
WHERE component IN ('mes/pro/productionMonitor/index', 'mes/pro/personnelAnalysis/index')
   OR perms LIKE 'mes:pro:productionMonitor:%'
   OR perms LIKE 'mes:pro:personnelAnalysis:%';

-- Only remove migration-created production groups that have become empty.
DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT parent.menu_id FROM sys_menu parent
    LEFT JOIN sys_menu child ON child.parent_id = parent.menu_id
    WHERE parent.parent_id = 0 AND parent.path = 'mes/pro' AND parent.menu_type = 'M'
      AND parent.legacy_id IS NULL AND parent.create_by = 'migration'
      AND child.menu_id IS NULL
);

-- DISTINCT keeps this derived table materialized on MySQL, avoiding target-table reuse.
DELETE FROM sys_menu
WHERE menu_id IN (
    SELECT menu_id FROM (
        SELECT DISTINCT parent.menu_id FROM sys_menu parent
        LEFT JOIN sys_menu child ON child.parent_id = parent.menu_id
        WHERE parent.parent_id = 0 AND parent.path = 'mes/pro' AND parent.menu_type = 'M'
          AND parent.legacy_id IS NULL AND parent.create_by = 'migration'
          AND child.menu_id IS NULL
    ) empty_groups
);
