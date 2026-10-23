-- EXECUTED maintenance record; not a Flyway migration or a reusable business entry.
-- User authorized reopening the executions in the reported workorder 1914.
-- Target verified: TEST_DEV_PERF_20261019 / datasource 9 / mes_base_dev_perf_test.
-- Server UUID: 03dbc952-7403-11ef-802c-0242ac130002; latest successful Flyway: V54.
-- Restricted backup and executable guards:
-- .local/workorder-1914-readability-20261023/reopen.py and before.json.
-- Guards checked exact 11 IDs, COMPLETED, non-null start_time, no posted production
-- documents or warehouse acceptance; fact snapshots and parent compared unchanged.
-- Do not rerun: completed immutable facts remain and are not canceled by this update.
USE mes_base_dev_perf_test;
START TRANSACTION;
SELECT id FROM workorder WHERE id = 1914 FOR UPDATE;
SELECT id FROM mes_production_execution
WHERE workorder_id = 1914 ORDER BY id FOR UPDATE;
UPDATE mes_production_execution
SET status = 'RUNNING', end_time = NULL
WHERE workorder_id = 1914
  AND id IN (12357,12358,12359,12360,12361,12362,12363,12364,12365,12366,12367)
  AND status = 'COMPLETED';
-- Execution wrapper verified affected rows = 11; any mismatch rolls back.
SELECT ROW_COUNT();
-- Execution wrapper compared every other execution column and all linked facts
-- with restricted backup before commit, then reread committed execution rows.
COMMIT;
