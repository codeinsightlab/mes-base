-- 旧转换元数据不再作为生产数量或业务身份的来源。
-- 已有数据须先完成受控转换、逐事实核验及库外备份；本迁移不提供转换能力。
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_retire_legacy_quantity_metadata$$
CREATE PROCEDURE mes_retire_legacy_quantity_metadata()
BEGIN
 DECLARE present_tables INT DEFAULT 0;
 DECLARE bad BIGINT DEFAULT 0;
 SELECT COUNT(*) INTO present_tables FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_name IN ('mes_legacy_source_mapping','mes_legacy_migration_batch') AND table_type='BASE TABLE';
 IF present_tables NOT IN (0,2) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata retirement is partial; investigate before retry';
 END IF;
 IF present_tables=2 THEN
  SELECT COUNT(*) INTO bad FROM mes_legacy_migration_batch WHERE status='RUNNING';
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy conversion is still running'; END IF;
  SELECT COUNT(*) INTO bad FROM mes_legacy_source_mapping m
   WHERE m.entity_kind='EXECUTION' AND NOT EXISTS (
    SELECT 1 FROM mes_material_issue_input f JOIN wm_stock_document d ON d.id=f.document_id
     JOIN wm_stock_document_line l ON l.document_id=d.id JOIN wm_stock_action a ON a.id=l.stock_action_id
    WHERE f.execution_id=m.entity_id AND d.source_execution_id=f.execution_id
     AND d.document_type='MATERIAL_ISSUE' AND d.status='POSTED'
     AND f.input_kind='PRODUCTION_INPUT' AND f.source_mode='LOCAL_ONLY'
     AND f.confirmed_at IS NOT NULL AND f.stock_applied=TRUE
     AND f.output_qty=CAST(JSON_UNQUOTE(JSON_EXTRACT(m.conversion_payload,'$.inputQty')) AS DECIMAL(20,6))
     AND l.quantity=f.output_qty AND a.quantity_delta=-f.output_qty AND a.execution_id=f.execution_id);
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source production input has no equivalent formal document, input and inventory action'; END IF;
  SELECT COUNT(*) INTO bad FROM mes_legacy_source_mapping m
   WHERE m.entity_kind='REPORT' AND NOT EXISTS (SELECT 1 FROM mes_production_report r WHERE r.id=m.entity_id);
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source report identity is missing from the formal ledger'; END IF;
  SELECT COUNT(*) INTO bad FROM mes_legacy_source_mapping m
   WHERE m.entity_kind='QUALITY_DECLARATION' AND NOT EXISTS (
    SELECT 1 FROM mes_production_defect d WHERE d.id=m.entity_id
     AND d.declaration_id=JSON_UNQUOTE(JSON_EXTRACT(m.conversion_payload,'$.declarationId'))
     AND d.defect_qty=CAST(JSON_UNQUOTE(JSON_EXTRACT(m.conversion_payload,'$.quantity')) AS DECIMAL(20,6)));
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source defect identity or quantity is missing from the formal ledger'; END IF;
  SELECT COUNT(*) INTO bad FROM mes_legacy_source_mapping m FORCE INDEX(idx_legacy_target)
   WHERE m.entity_kind='NODE_QUALITY_CONVERSION' AND NOT EXISTS (
    SELECT 1 FROM mes_legacy_source_mapping original FORCE INDEX(idx_legacy_parent)
     JOIN mes_production_report p FORCE INDEX(PRIMARY) ON p.id=original.entity_id
     JOIN mes_production_report r FORCE INDEX(idx_report_reference) ON r.reference_report_id=p.id
      AND r.execution_id=p.execution_id AND r.routing_node_id=m.entity_id
      AND r.record_type='ADJUSTMENT' AND r.quantity_delta>0
    WHERE original.entity_kind='REPORT' AND original.parent_id=m.parent_id);
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source node correction is missing from the formal report ledger'; END IF;
  SELECT COUNT(*) INTO bad FROM information_schema.key_column_usage
   WHERE referenced_table_schema=DATABASE() AND referenced_table_name IN ('mes_legacy_source_mapping','mes_legacy_migration_batch');
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata still has foreign key consumers'; END IF;
  SELECT COUNT(*) INTO bad FROM information_schema.triggers
   WHERE trigger_schema=DATABASE() AND event_object_table NOT IN ('mes_legacy_source_mapping','mes_legacy_migration_batch')
   AND (action_statement LIKE '%mes_legacy_source_mapping%' OR action_statement LIKE '%mes_legacy_migration_batch%');
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata still has trigger consumers'; END IF;
  SELECT COUNT(*) INTO bad FROM information_schema.views WHERE table_schema=DATABASE()
   AND (view_definition LIKE '%mes_legacy_source_mapping%' OR view_definition LIKE '%mes_legacy_migration_batch%');
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata still has view consumers'; END IF;
  SELECT COUNT(*) INTO bad FROM information_schema.routines WHERE routine_schema=DATABASE()
   AND routine_name<>'mes_retire_legacy_quantity_metadata'
   AND (routine_definition LIKE '%mes_legacy_source_mapping%' OR routine_definition LIKE '%mes_legacy_migration_batch%');
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata still has routine consumers'; END IF;
  SELECT COUNT(*) INTO bad FROM information_schema.events WHERE event_schema=DATABASE()
   AND (event_definition LIKE '%mes_legacy_source_mapping%' OR event_definition LIKE '%mes_legacy_migration_batch%');
  IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy metadata still has event consumers'; END IF;
  DROP TABLE mes_legacy_source_mapping,mes_legacy_migration_batch;
 END IF;
END$$
CALL mes_retire_legacy_quantity_metadata()$$
DROP PROCEDURE mes_retire_legacy_quantity_metadata$$
DELIMITER ;
