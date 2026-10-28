-- One-time production import writers have been retired. This table contains
-- coordination identities only; source mappings, batch evidence and archives stay.
-- Preflight: verify deployed writers are absent and take a full external backup.
-- Refuse unexpected schema or an import still marked RUNNING before the DDL.
SET @legacy_lock_retirement_check = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='mes_legacy_source_lock') = 1
    AND EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='mes_legacy_source_lock'
        AND column_name='id' AND column_type='char(64)' AND column_key='PRI')
    AND NOT EXISTS (SELECT 1 FROM mes_legacy_migration_batch WHERE status='RUNNING')
    AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
      WHERE referenced_table_schema=DATABASE() AND referenced_table_name='mes_legacy_source_lock'),
    'SELECT 1',
    'SELECT * FROM `legacy_source_lock_retirement_precondition_failed`');
PREPARE legacy_lock_retirement_check FROM @legacy_lock_retirement_check;
EXECUTE legacy_lock_retirement_check;
DEALLOCATE PREPARE legacy_lock_retirement_check;
DROP TABLE mes_legacy_source_lock;
-- MySQL DDL commits implicitly. If interrupted after DROP, inspect the schema and
-- Flyway history; restore the verified external backup before an authorized retry.
-- Do not use repair to conceal a failed or partially applied migration.
