-- Run only after the code-based production application has replaced the old application.
-- Back up both planning tables and all affected fact/reference columns before this retirement.
DELIMITER $$
CREATE PROCEDURE verify_production_code_retirement()
BEGIN
 IF EXISTS(SELECT 1 FROM mes_production_execution WHERE execution_code IS NULL)
 OR EXISTS(SELECT 1 FROM mes_execution_path p LEFT JOIN mes_production_execution e ON e.execution_code=p.execution_code WHERE e.id IS NULL)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution or path has no trustworthy code'; END IF;
END$$
CALL verify_production_code_retirement()$$
DROP PROCEDURE verify_production_code_retirement$$
CREATE PROCEDURE retire_production_id_reference(IN tbl VARCHAR(64),IN old_col VARCHAR(64),IN new_col VARCHAR(64),IN remove_column BOOLEAN)
BEGIN
 DECLARE drops_sql LONGTEXT;
 SET @query=CONCAT('SELECT COUNT(*) INTO @bad FROM `',tbl,'` t LEFT JOIN mes_production_execution e ON e.execution_code=t.`',new_col,'` WHERE (t.`',old_col,'` IS NOT NULL AND (e.id IS NULL OR e.id<>t.`',old_col,'`)) OR (t.`',new_col,'` IS NOT NULL AND e.id IS NULL)');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 IF tbl NOT IN ('wm_stock_document','wm_stock_action','mes_production_scan_activity') THEN
 SET @query=CONCAT('SELECT COUNT(*) INTO @missing FROM `',tbl,'` WHERE `',new_col,'` IS NULL');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 IF @missing>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required production execution code is missing'; END IF;
 END IF;
 IF @bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution code cutover is incomplete; stop retirement'; END IF;
 IF remove_column THEN
 SELECT GROUP_CONCAT(CONCAT('DROP INDEX `',index_name,'`') SEPARATOR ',') INTO drops_sql
 FROM (SELECT DISTINCT index_name FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name=tbl AND column_name=old_col AND index_name<>'PRIMARY') old_indexes;
 -- Combine retirement and required-code enforcement into one table rebuild.
 SET @query=CONCAT('ALTER TABLE `',tbl,'` ',IF(drops_sql IS NULL,'',CONCAT(drops_sql,',')),' DROP COLUMN `',old_col,'`',
 IF(tbl IN ('wm_stock_document','wm_stock_action','mes_production_scan_activity'),' ',CONCAT(', MODIFY COLUMN `',new_col,'` VARBINARY(2048) NOT NULL COMMENT ''生产唯一码，关联Execution.execution_code''')));
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 END IF;
END$$
CALL retire_production_id_reference('mes_execution_assignment','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_execution_completion','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_execution_path_node','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_material_issue_input','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_production_defect','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_production_receipt','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_production_report','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_production_scan_activity','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_quality_release','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_quality_treatment','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('mes_warehouse_acceptance','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('wm_stock_action','execution_id','execution_code',FALSE)$$
CALL retire_production_id_reference('wm_stock_document','source_execution_id','source_execution_code',FALSE)$$
CALL retire_production_id_reference('mes_execution_assignment','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_execution_completion','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_execution_path_node','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_material_issue_input','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_production_defect','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_production_receipt','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_production_report','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_production_scan_activity','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_quality_release','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_quality_treatment','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('mes_warehouse_acceptance','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('wm_stock_action','execution_id','execution_code',TRUE)$$
CALL retire_production_id_reference('wm_stock_document','source_execution_id','source_execution_code',TRUE)$$
DROP PROCEDURE retire_production_id_reference$$
ALTER TABLE mes_production_execution MODIFY COLUMN execution_code VARBINARY(2048) NOT NULL$$
ALTER TABLE mes_execution_path MODIFY COLUMN execution_code VARBINARY(2048) NOT NULL$$
DROP TABLE mes_production_combination_item$$
DROP TABLE mes_production_combination$$
DELIMITER ;
