-- 稳定来源身份的数据库锁：也保护尚不存在的Mapping；仅主键，不保存工厂字段。
CREATE TABLE mes_legacy_source_lock (
 id CHAR(64) NOT NULL COMMENT '实例数据库来源身份摘要，与Mapping稳定身份一致',
 PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史迁移跨批次跨进程来源身份锁';

INSERT INTO mes_legacy_source_lock(id)
 SELECT id FROM mes_legacy_source_mapping WHERE entity_kind IN ('WORKORDER','UNIT','MATERIAL','OPERATION');

DELIMITER $$
-- 替换旧执行授权期间保持更严格的保护，不存在未保护的DDL间隙。
CREATE TRIGGER mes_history_execution_transition_guard BEFORE INSERT ON mes_production_execution
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical execution authorization migration in progress';
 END IF;
END$$
DROP TRIGGER mes_history_execution_insert$$
CREATE TRIGGER mes_history_execution_insert BEFORE INSERT ON mes_production_execution
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM workorder w WHERE w.id=NEW.workorder_id AND w.status='HISTORICAL')
 AND NOT EXISTS (
  SELECT 1 FROM mes_legacy_source_mapping m JOIN mes_legacy_migration_batch b ON b.id=m.batch_id
  WHERE m.entity_kind='WORKORDER' AND m.entity_id=NEW.workorder_id AND b.status='RUNNING'
 ) AND NOT EXISTS (
  SELECT 1 FROM mes_legacy_source_mapping a
  JOIN mes_legacy_source_mapping w ON w.id=a.parent_id
  JOIN mes_legacy_migration_batch b ON b.id=a.batch_id
  WHERE a.entity_kind='APPEND_AUTHORIZATION' AND a.entity_id=NEW.workorder_id
   AND w.entity_kind='WORKORDER' AND w.entity_id=NEW.workorder_id
   AND a.source_hash=w.source_hash AND a.rule_version=w.rule_version AND a.rule_version=b.rule_version
   AND b.status='RUNNING'
   AND JSON_CONTAINS(b.approved_scope,JSON_QUOTE(JSON_UNQUOTE(JSON_EXTRACT(a.conversion_payload,'$.commandHash'))))
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
 END IF;
END$$
DROP TRIGGER mes_history_execution_transition_guard$$
DELIMITER ;
