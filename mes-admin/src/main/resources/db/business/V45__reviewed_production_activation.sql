-- An immutable, source-linked authorization admits reviewed opening quantities to normal production.
-- Original reports, mappings, paths and routing facts remain immutable; no stock is created here.
DELIMITER $$
CREATE TRIGGER history_activation_execution_transition BEFORE UPDATE ON mes_production_execution
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production activation migration in progress';
 END IF;
END$$
CREATE TRIGGER history_activation_order_transition BEFORE UPDATE ON workorder
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production activation migration in progress';
 END IF;
END$$
DROP TRIGGER mes_history_execution_update$$
CREATE TRIGGER mes_history_execution_update BEFORE UPDATE ON mes_production_execution
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL'
  OR EXISTS (SELECT 1 FROM workorder WHERE id=NEW.workorder_id AND status='HISTORICAL') THEN
  IF OLD.status<>'HISTORICAL' OR NEW.status<>'RUNNING'
   OR NOT (NEW.id <=> OLD.id) OR NOT (NEW.workorder_id <=> OLD.workorder_id)
   OR NOT (NEW.execution_no <=> OLD.execution_no) OR NOT (NEW.product_id <=> OLD.product_id)
   OR NOT (NEW.routing_id <=> OLD.routing_id) OR NOT (NEW.target_qty <=> OLD.target_qty)
   OR NOT (NEW.end_time <=> OLD.end_time) OR NOT (NEW.remark <=> OLD.remark)
   OR NOT (NEW.create_by <=> OLD.create_by) OR NOT (NEW.create_time <=> OLD.create_time)
   OR NEW.start_time IS NULL
   OR NOT EXISTS (
    SELECT 1 FROM mes_legacy_source_mapping a
    JOIN mes_legacy_source_mapping e ON e.id=a.parent_id
    JOIN mes_legacy_migration_batch b ON b.id=a.batch_id
    JOIN workorder w ON w.id=NEW.workorder_id
    WHERE a.entity_kind='PRODUCTION_ACTIVATION' AND a.entity_id=OLD.id
     AND e.entity_kind='EXECUTION' AND e.entity_id=OLD.id AND a.source_hash=e.source_hash
     AND a.rule_version='internal-production-activation-v1' AND b.rule_version=a.rule_version
     AND b.status='RUNNING' AND w.status='RELEASED' AND w.source='LOCAL'
   ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reviewed production activation required';
  END IF;
 END IF;
END$$
DROP TRIGGER mes_history_order_update$$
CREATE TRIGGER mes_history_order_update BEFORE UPDATE ON workorder
FOR EACH ROW
BEGIN
 IF OLD.status='HISTORICAL' OR NEW.status='HISTORICAL' THEN
  IF OLD.status<>'HISTORICAL' OR NEW.status<>'RELEASED'
   OR NOT (NEW.id <=> OLD.id) OR NOT (NEW.code <=> OLD.code)
   OR NOT (NEW.source <=> OLD.source) OR NEW.source<>'LOCAL'
   OR NOT (NEW.product_id <=> OLD.product_id) OR NOT (NEW.routing_id <=> OLD.routing_id)
   OR NOT (NEW.planned_qty <=> OLD.planned_qty)
   OR NOT (NEW.planned_start_at <=> OLD.planned_start_at) OR NOT (NEW.planned_end_at <=> OLD.planned_end_at)
   OR NOT (NEW.remark <=> OLD.remark) OR NOT (NEW.create_time <=> OLD.create_time)
   OR NEW.revision<>OLD.revision+1
   OR NOT EXISTS (
    SELECT 1 FROM mes_legacy_source_mapping a
    JOIN mes_legacy_source_mapping w ON w.id=a.parent_id
    JOIN mes_legacy_migration_batch b ON b.id=a.batch_id
    WHERE a.entity_kind='ORDER_ACTIVATION' AND a.entity_id=OLD.id
     AND w.entity_kind='WORKORDER' AND w.entity_id=OLD.id AND a.source_hash=w.source_hash
     AND a.rule_version='internal-production-activation-v1' AND b.rule_version=a.rule_version AND b.status='RUNNING'
   ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reviewed production activation required';
  END IF;
 END IF;
END$$
DROP TRIGGER history_activation_execution_transition$$
DROP TRIGGER history_activation_order_transition$$
DELIMITER ;
