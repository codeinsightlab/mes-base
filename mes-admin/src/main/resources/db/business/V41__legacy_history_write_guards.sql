-- 兼容尚未重启的旧应用进程：历史对象在数据库层同样禁止正常生产写入。
-- 只有同一原子事务内的已审核运行批次及对应对象映射可建立历史报工，不使用会话或全局跳过开关。

DELIMITER $$

CREATE TRIGGER mes_history_report_insert BEFORE INSERT ON mes_production_report
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) AND NOT EXISTS (SELECT 1 FROM mes_legacy_source_mapping m JOIN mes_legacy_migration_batch b ON b.id=m.batch_id WHERE m.entity_kind='EXECUTION' AND m.entity_id=NEW.execution_id AND b.status='RUNNING') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_execution_insert BEFORE INSERT ON mes_production_execution
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL') AND NOT EXISTS (SELECT 1 FROM mes_legacy_source_mapping m JOIN mes_legacy_migration_batch b ON b.id=m.batch_id WHERE m.entity_kind='WORKORDER' AND m.entity_id=NEW.workorder_id AND b.status='RUNNING') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_execution_update BEFORE UPDATE ON mes_production_execution
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL' OR EXISTS (SELECT 1 FROM workorder w WHERE w.id=OLD.workorder_id AND w.status='HISTORICAL') OR EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_execution_delete BEFORE DELETE ON mes_production_execution
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR EXISTS (SELECT 1 FROM workorder w WHERE w.id=OLD.workorder_id AND w.status='HISTORICAL') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_order_update BEFORE UPDATE ON workorder
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_order_delete BEFORE DELETE ON workorder
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_order_new_reference BEFORE INSERT ON workorder
FOR EACH ROW
BEGIN
 IF NEW.status<>'HISTORICAL' AND EXISTS (SELECT 1 FROM mes_legacy_source_mapping m WHERE m.entity_kind='ROUTING' AND m.entity_id=NEW.routing_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_routing_update BEFORE UPDATE ON routing
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_legacy_source_mapping m WHERE m.entity_kind='ROUTING' AND m.entity_id=OLD.id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER mes_history_routing_delete BEFORE DELETE ON routing
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_legacy_source_mapping m WHERE m.entity_kind='ROUTING' AND m.entity_id=OLD.id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_production_defect_insert BEFORE INSERT ON mes_production_defect
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_quality_treatment_insert BEFORE INSERT ON mes_quality_treatment
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_quality_release_insert BEFORE INSERT ON mes_quality_release
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_production_receipt_insert BEFORE INSERT ON mes_production_receipt
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_warehouse_acceptance_insert BEFORE INSERT ON mes_warehouse_acceptance
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_execution_completion_insert BEFORE INSERT ON mes_execution_completion
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_material_issue_input_insert BEFORE INSERT ON mes_material_issue_input
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_mes_execution_assignment_insert BEFORE INSERT ON mes_execution_assignment
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_wm_stock_action_insert BEFORE INSERT ON wm_stock_action
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_assignment_update BEFORE UPDATE ON mes_execution_assignment
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) OR EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=OLD.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_material_issue_insert BEFORE INSERT ON mes_material_issue
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL') OR EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

CREATE TRIGGER history_material_issue_update BEFORE UPDATE ON mes_material_issue
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL') OR EXISTS (SELECT 1 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id WHERE e.id=NEW.execution_id AND (e.status='HISTORICAL' OR w.status='HISTORICAL')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$

DELIMITER ;
