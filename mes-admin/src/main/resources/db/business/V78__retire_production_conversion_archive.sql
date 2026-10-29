-- 一次性生产转换验收完成后退役库内审计副本。
-- 执行前须完成完整库外备份、完整性校验及消费者核实；不删除正式生产事实。
DELIMITER $$
CREATE PROCEDURE mes_retire_conversion_archive()
BEGIN
 DECLARE bad BIGINT DEFAULT 0;
 SELECT COUNT(*) INTO bad FROM information_schema.tables WHERE table_schema=DATABASE()
  AND table_name IN ('mes_legacy_source_mapping','mes_legacy_migration_batch','mes_legacy_source_lock');
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy conversion retirement is not complete'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE()
  AND referenced_table_name='mes_production_conversion_archive';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conversion archive has foreign key consumers'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.views WHERE table_schema=DATABASE() AND view_definition LIKE '%mes_production_conversion_archive%';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conversion archive has view consumers'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND (event_object_table='mes_production_conversion_archive' OR action_statement LIKE '%mes_production_conversion_archive%');
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conversion archive has trigger consumers'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.routines WHERE routine_schema=DATABASE() AND routine_name<>'mes_retire_conversion_archive' AND routine_definition LIKE '%mes_production_conversion_archive%';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conversion archive has routine consumers'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.events WHERE event_schema=DATABASE() AND event_definition LIKE '%mes_production_conversion_archive%';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conversion archive has event consumers'; END IF;
 DROP TABLE IF EXISTS mes_production_conversion_archive;
END$$
CALL mes_retire_conversion_archive()$$
DROP PROCEDURE mes_retire_conversion_archive$$
DELIMITER ;
