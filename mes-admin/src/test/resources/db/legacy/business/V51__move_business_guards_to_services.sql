-- Business edit/delete rules belong to services and shared parent-row transactions.
-- No business rows or source identities are changed by this migration.
DROP TRIGGER IF EXISTS mes_defect_no_delete;
DROP TRIGGER IF EXISTS mes_defect_no_update;
DROP TRIGGER IF EXISTS mes_execution_completion_no_delete;
DROP TRIGGER IF EXISTS mes_execution_completion_no_update;
DROP TRIGGER IF EXISTS mes_legacy_mapping_no_delete;
DROP TRIGGER IF EXISTS mes_legacy_mapping_no_update;
DROP TRIGGER IF EXISTS mes_quality_release_no_delete;
DROP TRIGGER IF EXISTS mes_quality_release_no_update;
DROP TRIGGER IF EXISTS mes_quality_treatment_no_delete;
DROP TRIGGER IF EXISTS mes_quality_treatment_no_update;
DROP TRIGGER IF EXISTS mes_receipt_execution_guard;
DROP TRIGGER IF EXISTS mes_receipt_no_delete;
DROP TRIGGER IF EXISTS mes_receipt_no_update;
DROP TRIGGER IF EXISTS mes_report_no_delete;
DROP TRIGGER IF EXISTS mes_report_no_update;
DROP TRIGGER IF EXISTS mes_warehouse_acceptance_no_delete;
DROP TRIGGER IF EXISTS mes_warehouse_acceptance_no_update;
DROP TRIGGER IF EXISTS mes_workorder_cancellation_no_delete;
DROP TRIGGER IF EXISTS mes_workorder_cancellation_no_update;
DROP TRIGGER IF EXISTS wm_stock_action_no_delete;
DROP TRIGGER IF EXISTS wm_stock_action_no_update;
DROP TRIGGER IF EXISTS wm_warehouse_source_immutable;
DROP TRIGGER IF EXISTS workorder_source_immutable;
