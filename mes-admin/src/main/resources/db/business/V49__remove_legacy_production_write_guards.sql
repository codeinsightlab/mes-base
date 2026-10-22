-- Legacy production import and activation are retired. Historical source
-- records remain preserved, while their former write-blocking guards no
-- longer apply to normal production use.
DELIMITER $$

DROP TRIGGER IF EXISTS mes_history_report_insert$$
DROP TRIGGER IF EXISTS mes_history_execution_insert$$
DROP TRIGGER IF EXISTS mes_history_execution_update$$
DROP TRIGGER IF EXISTS mes_history_execution_delete$$
DROP TRIGGER IF EXISTS mes_history_order_update$$
DROP TRIGGER IF EXISTS mes_history_order_delete$$
DROP TRIGGER IF EXISTS mes_history_order_new_reference$$
DROP TRIGGER IF EXISTS mes_history_routing_update$$
DROP TRIGGER IF EXISTS mes_history_routing_delete$$
DROP TRIGGER IF EXISTS history_mes_production_defect_insert$$
DROP TRIGGER IF EXISTS history_mes_quality_treatment_insert$$
DROP TRIGGER IF EXISTS history_mes_quality_release_insert$$
DROP TRIGGER IF EXISTS history_mes_production_receipt_insert$$
DROP TRIGGER IF EXISTS history_mes_warehouse_acceptance_insert$$
DROP TRIGGER IF EXISTS history_mes_execution_completion_insert$$
DROP TRIGGER IF EXISTS history_mes_material_issue_input_insert$$
DROP TRIGGER IF EXISTS history_mes_execution_assignment_insert$$
DROP TRIGGER IF EXISTS history_wm_stock_action_insert$$
DROP TRIGGER IF EXISTS history_assignment_update$$
DROP TRIGGER IF EXISTS history_material_issue_insert$$
DROP TRIGGER IF EXISTS history_material_issue_update$$

DELIMITER ;
