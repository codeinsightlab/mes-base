-- Restore source business labels without replacing historical routing IDs or facts.
-- Only an immutable, explicitly scoped repair record in a RUNNING repair batch
-- permits this one-time change. Ordinary historical updates remain rejected.
DELIMITER $$
CREATE TRIGGER mes_history_routing_identity_transition BEFORE UPDATE ON routing
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_legacy_source_mapping WHERE entity_kind='ROUTING' AND entity_id=OLD.id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical routing authorization migration in progress';
 END IF;
END$$
DROP TRIGGER mes_history_routing_update$$
CREATE TRIGGER mes_history_routing_update BEFORE UPDATE ON routing
FOR EACH ROW
BEGIN
 IF EXISTS (SELECT 1 FROM mes_legacy_source_mapping WHERE entity_kind='ROUTING' AND entity_id=OLD.id) THEN
  IF NOT (NEW.id <=> OLD.id)
   OR NOT (NEW.status <=> OLD.status)
   OR NOT (NEW.description <=> OLD.description)
   OR NOT (NEW.external_id <=> OLD.external_id)
   OR NOT (NEW.source_version <=> OLD.source_version)
   OR NOT (NEW.revision <=> OLD.revision)
   OR NOT (NEW.published_at <=> OLD.published_at)
   OR LEFT(OLD.code,7)<>'LEGACY_'
   OR NOT EXISTS (
    SELECT 1 FROM mes_legacy_source_mapping original
    JOIN mes_legacy_source_mapping repair ON repair.parent_id=original.id
    JOIN mes_legacy_migration_batch batch ON batch.id=repair.batch_id
    WHERE original.entity_kind='ROUTING' AND original.entity_id=OLD.id
     AND original.rule_version='report-driven-local-v2'
     AND repair.entity_kind='ROUTING_IDENTITY_RESTORE' AND repair.entity_id=OLD.id
     AND repair.source_table='pro_route'
     AND repair.rule_version='legacy-route-identity-restore-v1'
     AND batch.rule_version=repair.rule_version AND batch.status='RUNNING'
     AND JSON_CONTAINS(batch.approved_scope,JSON_QUOTE(repair.id))
     AND JSON_LENGTH(JSON_EXTRACT(original.source_payload,'$.executions[*].key[2]'))>0
     AND JSON_CONTAINS(JSON_ARRAY(repair.source_pk),JSON_EXTRACT(original.source_payload,'$.executions[*].key[2]'))
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.source_payload,'$.route_id'))=BINARY repair.source_pk
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.source_payload,'$.route_code'))=BINARY NEW.code
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.source_payload,'$.route_name'))=BINARY NEW.name
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.old.code'))=BINARY OLD.code
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.old.name'))=BINARY OLD.name
     AND CAST(JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.old.version')) AS UNSIGNED)=OLD.version
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.new.code'))=BINARY NEW.code
     AND BINARY JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.new.name'))=BINARY NEW.name
     AND CAST(JSON_UNQUOTE(JSON_EXTRACT(repair.conversion_payload,'$.new.version')) AS UNSIGNED)=NEW.version
     AND NEW.version>0
   ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical production is read only';
  END IF;
 END IF;
END$$
DROP TRIGGER mes_history_routing_identity_transition$$
DELIMITER ;
