-- Correct the V69-V72 material-issue convergence after its data rollback.
-- These versions remain in Flyway history. No original business tables are deleted.
-- The corresponding one-time data rollback must complete before this migration.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v73_rollback_issue_convergence$$
CREATE PROCEDURE mes_v73_rollback_issue_convergence()
BEGIN
 DECLARE bad_rows BIGINT DEFAULT 0;
 SELECT COUNT(*) INTO bad_rows FROM wm_stock_document WHERE production_request_id IS NOT NULL OR production_workorder_code IS NOT NULL OR production_product_id IS NOT NULL OR production_product_code IS NOT NULL OR production_routing_id IS NOT NULL OR production_request_hash IS NOT NULL OR production_input_kind IS NOT NULL OR production_output_qty IS NOT NULL OR production_source_mode IS NOT NULL OR production_calculation_json IS NOT NULL OR production_request_created_at IS NOT NULL OR production_confirmed_at IS NOT NULL OR production_business_occurred_at IS NOT NULL OR production_stock_applied IS NOT NULL OR production_path_revision IS NOT NULL OR production_confirmation_operation_id IS NOT NULL OR production_confirmation_command_json IS NOT NULL OR production_confirmation_status IS NOT NULL OR production_submission_created_at IS NOT NULL OR production_submission_finished_at IS NOT NULL OR production_confirmed_path_node_ids IS NOT NULL OR production_void_operation_id IS NOT NULL OR production_void_from_status IS NOT NULL OR production_void_requested_at IS NOT NULL OR production_void_finished_at IS NOT NULL OR production_void_result_status IS NOT NULL OR production_original_issue_no IS NOT NULL OR production_original_remark IS NOT NULL OR production_original_create_by IS NOT NULL OR production_original_create_time IS NOT NULL OR production_original_update_by IS NOT NULL OR production_original_update_time IS NOT NULL;
 IF bad_rows<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Data rollback must precede removal of convergence columns'; END IF;
 SELECT COUNT(*) INTO bad_rows FROM wm_stock_document_line WHERE create_by IS NOT NULL OR create_time IS NOT NULL OR update_by IS NOT NULL OR update_time IS NOT NULL;
 IF bad_rows<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Line audit rollback must precede structure correction'; END IF;
 SELECT COUNT(*) INTO bad_rows FROM mes_material_issue WHERE source_mode IS NOT NULL OR void_result_status IS NOT NULL;
 IF bad_rows<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Transitional source fields contain facts; refuse correction'; END IF;
 ALTER TABLE wm_stock_document
  DROP INDEX uk_stock_document_production_request,
  DROP INDEX idx_stock_document_direction_state,
  DROP COLUMN production_request_id,
  DROP COLUMN production_workorder_code,
  DROP COLUMN production_product_id,
  DROP COLUMN production_product_code,
  DROP COLUMN production_routing_id,
  DROP COLUMN production_request_hash,
  DROP COLUMN production_input_kind,
  DROP COLUMN production_output_qty,
  DROP COLUMN production_source_mode,
  DROP COLUMN production_calculation_json,
  DROP COLUMN production_request_created_at,
  DROP COLUMN production_confirmed_at,
  DROP COLUMN production_business_occurred_at,
  DROP COLUMN production_stock_applied,
  DROP COLUMN production_path_revision,
  DROP COLUMN production_confirmation_operation_id,
  DROP COLUMN production_confirmation_command_json,
  DROP COLUMN production_confirmation_status,
  DROP COLUMN production_submission_created_at,
  DROP COLUMN production_submission_finished_at,
  DROP COLUMN production_confirmed_path_node_ids,
  DROP COLUMN production_void_operation_id,
  DROP COLUMN production_void_from_status,
  DROP COLUMN production_void_requested_at,
  DROP COLUMN production_void_finished_at,
  DROP COLUMN production_void_result_status,
  DROP COLUMN production_original_issue_no,
  DROP COLUMN production_original_remark,
  DROP COLUMN production_original_create_by,
  DROP COLUMN production_original_create_time,
  DROP COLUMN production_original_update_by,
  DROP COLUMN production_original_update_time,
  DROP COLUMN direction,
  MODIFY COLUMN `create_by` varchar(64) NOT NULL COMMENT '服务端确定的创建用户主键',
  MODIFY COLUMN `create_time` datetime(6) NOT NULL COMMENT '服务端创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL COMMENT '服务端确定的最后维护用户主键',
  MODIFY COLUMN `update_time` datetime(6) NOT NULL COMMENT '服务端最后维护时间';
 ALTER TABLE wm_stock_document_line
  DROP COLUMN create_by,
  DROP COLUMN create_time,
  DROP COLUMN update_by,
  DROP COLUMN update_time,
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '库存身份中的LOCAL仓库主键',
  MODIFY COLUMN `stock_id` bigint NOT NULL COMMENT '现有受控库存来源主键，先建立零余额来源';
 ALTER TABLE mes_material_issue DROP COLUMN source_mode, DROP COLUMN void_result_status;
 SET @rollback_next_id=(SELECT COALESCE(MAX(id),0)+1 FROM wm_stock_document);
 SET @rollback_sql=CONCAT('ALTER TABLE wm_stock_document AUTO_INCREMENT=',@rollback_next_id);
 PREPARE rollback_stmt FROM @rollback_sql; EXECUTE rollback_stmt; DEALLOCATE PREPARE rollback_stmt;
 SET @rollback_next_id=(SELECT COALESCE(MAX(id),0)+1 FROM wm_stock_document_line);
 SET @rollback_sql=CONCAT('ALTER TABLE wm_stock_document_line AUTO_INCREMENT=',@rollback_next_id);
 PREPARE rollback_stmt FROM @rollback_sql; EXECUTE rollback_stmt; DEALLOCATE PREPARE rollback_stmt;
END$$
CALL mes_v73_rollback_issue_convergence()$$
DROP PROCEDURE mes_v73_rollback_issue_convergence$$
DELIMITER ;
