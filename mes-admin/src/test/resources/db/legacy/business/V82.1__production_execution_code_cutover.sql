-- Additive cutover: preserve original execution IDs and all business facts during deployment.
-- Runtime protocol is owned by ProductionQrCode; the frozen SQL escaping/order below matches it.
-- VARBINARY uses exact UTF-8 bytes, including trailing spaces, with a full <=2048-byte index.
SET SESSION group_concat_max_len=1048576;
DELIMITER $$
CREATE PROCEDURE migrate_production_execution_codes()
BEGIN
 DECLARE bad BIGINT DEFAULT 0;
 SELECT COUNT(*) INTO bad FROM mes_production_execution e
 LEFT JOIN workorder w ON w.id=e.workorder_id LEFT JOIN material m ON m.id=e.product_id
 WHERE w.id IS NULL OR m.id IS NULL;
 IF bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution parent identity missing'; END IF;
 SELECT COUNT(*) INTO bad FROM (SELECT execution_id FROM mes_production_combination
 WHERE execution_id IS NOT NULL GROUP BY execution_id HAVING COUNT(*)>1) duplicates;
 IF bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Multiple planning records bind one execution'; END IF;
WITH combinations AS (
 SELECT c.execution_id,
 GROUP_CONCAT(DISTINCT BINARY CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.material_code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',IF(i.batch_no IS NULL,'~',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F')))
 ORDER BY BINARY i.material_code,BINARY i.batch_no SEPARATOR '$') AS materials
 FROM mes_production_combination c JOIN mes_production_combination_item i ON i.combination_id=c.id
 WHERE c.execution_id IS NOT NULL GROUP BY c.execution_id
), identities AS (
 SELECT e.id,CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(w.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),':',
 COALESCE(c.materials,CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(m.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(e.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F')))) AS code
 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id
 JOIN material m ON m.id=e.product_id LEFT JOIN combinations c ON c.execution_id=e.id
)
 SELECT COUNT(*) INTO bad FROM identities WHERE code IS NULL OR OCTET_LENGTH(code)>2048;
 IF bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution code absent or exceeds protocol byte limit'; END IF;
WITH combinations AS (
 SELECT c.execution_id,
 GROUP_CONCAT(DISTINCT BINARY CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.material_code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',IF(i.batch_no IS NULL,'~',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F')))
 ORDER BY BINARY i.material_code,BINARY i.batch_no SEPARATOR '$') AS materials
 FROM mes_production_combination c JOIN mes_production_combination_item i ON i.combination_id=c.id
 WHERE c.execution_id IS NOT NULL GROUP BY c.execution_id
), identities AS (
 SELECT e.id,CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(w.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),':',
 COALESCE(c.materials,CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(m.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(e.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F')))) AS code
 FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id
 JOIN material m ON m.id=e.product_id LEFT JOIN combinations c ON c.execution_id=e.id
)
 SELECT COUNT(*) INTO bad FROM (SELECT BINARY code FROM identities GROUP BY BINARY code HAVING COUNT(*)>1) duplicates;
 IF bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Different existing executions have the same preserved code'; END IF;
 ALTER TABLE mes_production_execution ADD COLUMN execution_code VARBINARY(2048) NULL COMMENT '不可变生产唯一码，UTF8原文字节，生成规则见ProductionQrCode' AFTER id;
 UPDATE mes_production_execution e
 JOIN workorder w ON w.id=e.workorder_id JOIN material m ON m.id=e.product_id
 LEFT JOIN (
 SELECT c.execution_id,
 GROUP_CONCAT(DISTINCT BINARY CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.material_code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',IF(i.batch_no IS NULL,'~',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F')))
 ORDER BY BINARY i.material_code,BINARY i.batch_no SEPARATOR '$') AS materials
 FROM mes_production_combination c JOIN mes_production_combination_item i ON i.combination_id=c.id
 WHERE c.execution_id IS NOT NULL GROUP BY c.execution_id
) c ON c.execution_id=e.id
 SET e.execution_code=CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(w.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),':',COALESCE(c.materials,CONCAT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(m.code,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'),'#',REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(e.batch_no,'%', '%25'), ':','%3A'),'#','%23'),'$','%24'),'~','%7E'),'/', '%2F'),CHAR(92),'%5C'),'?','%3F'))));
 ALTER TABLE mes_production_execution ADD UNIQUE KEY uk_execution_code(execution_code);
END$$
CALL migrate_production_execution_codes()$$
DROP PROCEDURE migrate_production_execution_codes$$

CREATE PROCEDURE migrate_production_code_reference(IN tbl VARCHAR(64),IN old_col VARCHAR(64),IN new_col VARCHAR(64))
BEGIN
 DECLARE indexes_sql LONGTEXT;
 SET @query=CONCAT('SELECT COUNT(*) INTO @bad FROM `',tbl,'` t LEFT JOIN mes_production_execution e ON e.id=t.`',old_col,'` WHERE t.`',old_col,'` IS NOT NULL AND e.id IS NULL');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 IF @bad>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Orphan production execution reference'; END IF;
 SET @query=CONCAT('ALTER TABLE `',tbl,'` ADD COLUMN `',new_col,'` VARBINARY(2048) NULL COMMENT ''生产唯一码，关联Execution.execution_code''');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 SET @query=CONCAT('UPDATE `',tbl,'` t JOIN mes_production_execution e ON e.id=t.`',old_col,'` SET t.`',new_col,'`=e.execution_code');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 SELECT GROUP_CONCAT(CONCAT('ADD ',IF(non_unique=0,'UNIQUE ',''),'KEY `',LEFT(CONCAT(index_name,'_code'),64),'` (',columns_sql,')') SEPARATOR ', ')
 INTO indexes_sql FROM (
 SELECT s.index_name,s.non_unique,
 GROUP_CONCAT(CONCAT('`',IF(s.column_name=old_col,new_col,s.column_name),'`',IF(s.sub_part IS NULL,'',CONCAT('(',s.sub_part,')'))) ORDER BY s.seq_in_index SEPARATOR ',') AS columns_sql
 FROM information_schema.statistics s
 WHERE s.table_schema=DATABASE() AND s.table_name=tbl AND s.index_name<>'PRIMARY'
 AND s.index_name IN (SELECT k.index_name FROM information_schema.statistics k WHERE k.table_schema=DATABASE() AND k.table_name=tbl AND k.column_name=old_col)
 GROUP BY s.index_name,s.non_unique
 ) definitions;
 IF indexes_sql IS NOT NULL THEN
 SET @query=CONCAT('ALTER TABLE `',tbl,'` ',indexes_sql);
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
 END IF;
 SET @query=CONCAT('ALTER TABLE `',tbl,'` MODIFY COLUMN `',old_col,'` BIGINT NULL COMMENT ''切换期间保留的原执行主键；新版生产不再使用''');
 PREPARE q FROM @query; EXECUTE q; DEALLOCATE PREPARE q;
END$$
CALL migrate_production_code_reference('mes_execution_assignment','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_execution_completion','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_execution_path_node','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_material_issue_input','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_production_defect','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_production_receipt','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_production_report','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_production_scan_activity','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_quality_release','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_quality_treatment','execution_id','execution_code')$$
CALL migrate_production_code_reference('mes_warehouse_acceptance','execution_id','execution_code')$$
CALL migrate_production_code_reference('wm_stock_action','execution_id','execution_code')$$
CALL migrate_production_code_reference('wm_stock_document','source_execution_id','source_execution_code')$$
DROP PROCEDURE migrate_production_code_reference$$
ALTER TABLE mes_execution_path ADD COLUMN execution_code VARBINARY(2048) NULL COMMENT '生产唯一码'$$
UPDATE mes_execution_path p JOIN mes_production_execution e ON e.id=p.id SET p.execution_code=e.execution_code$$
CREATE PROCEDURE check_production_path_codes()
BEGIN
 IF EXISTS(SELECT 1 FROM mes_execution_path WHERE execution_code IS NULL)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution path identity cannot be converted'; END IF;
END$$
CALL check_production_path_codes()$$
DROP PROCEDURE check_production_path_codes$$
ALTER TABLE mes_execution_path MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT,
 ADD UNIQUE KEY uk_execution_path_code(execution_code)$$
DELIMITER ;
