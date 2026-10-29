-- 领料退役：旧对象全缺时仍须核对完整正式目标，不能凭缺表视作成功。
-- 旧对象全缺且正式目标完整时跳过；部分缺失、冲突或未转换事实明确失败。
-- 不运行转换器，不插入、更新或重放任何生产事实、审核及库存动作。
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v75_retire_issue_tables$$
CREATE PROCEDURE mes_v75_retire_issue_tables()
BEGIN
DECLARE bad BIGINT DEFAULT 0;
DECLARE object_count BIGINT DEFAULT 0;
DECLARE legacy_count BIGINT DEFAULT 0;
SELECT COUNT(*) INTO legacy_count FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('mes_material_issue','mes_material_issue_item','mes_material_issue_erp_extra','mes_material_issue_submission');
IF legacy_count<>0 AND legacy_count<>4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partial old issue schema requires explicit recovery';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name IN ('mes_material_issue_input','wm_stock_document','wm_stock_document_line','wm_stock_action','mes_production_execution');
IF bad<>5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal retirement target tables are incomplete';
END IF;
SELECT COUNT(*) INTO bad FROM (SELECT 'mes_material_issue_input' table_name, 'id' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'document_id' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'execution_id' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'input_kind' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'source_mode' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'output_qty' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'confirmed_at' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'stock_applied' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'stock_id' column_name UNION ALL SELECT 'mes_material_issue_input' table_name, 'business_occurred_at' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'id' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'document_type' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'direction' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'status' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'create_request_id' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'source_execution_id' column_name UNION ALL SELECT 'wm_stock_document' table_name, 'source_workorder_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'document_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'line_no' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'stock_action_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'material_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'warehouse_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'batch_no' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'unit_id' column_name UNION ALL SELECT 'wm_stock_document_line' table_name, 'quantity' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'id' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'stock_id' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'action_type' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'document_line_id' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'reference_action_id' column_name UNION ALL SELECT 'wm_stock_action' table_name, 'execution_id' column_name UNION ALL SELECT 'mes_production_execution' table_name, 'id' column_name UNION ALL SELECT 'mes_production_execution' table_name, 'workorder_id' column_name) required_column LEFT JOIN information_schema.columns c ON c.table_schema=DATABASE() AND c.table_name=required_column.table_name AND c.column_name=required_column.column_name WHERE c.column_name IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal retirement target columns are incomplete';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='document_id' AND column_type='bigint' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting retirement target document_id';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='business_occurred_at' AND column_type='datetime(6)' AND column_default IS NULL AND extra='' AND is_nullable='YES';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting retirement target business_occurred_at';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction' AND column_type='varchar(3)' AND column_default IS NULL AND extra='' AND is_nullable='NO';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting retirement target direction';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='create_request_id' AND column_type='varchar(36)' AND column_default IS NULL AND extra='' AND is_nullable='YES' AND character_set_name='ascii' AND collation_name='ascii_bin';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting retirement target create_request_id';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='warehouse_id' AND column_type='bigint' AND is_nullable='YES' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting formal line warehouse_id';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='stock_id' AND column_type='bigint' AND is_nullable='YES' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting formal line stock_id';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND index_name='uk_input_document';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND index_name='uk_input_document' AND non_unique=0 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='document_id'));
IF object_count<>1 OR bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting uk_input_document definition';
END IF;
END IF;
IF object_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing target index uk_input_document';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='uk_stock_document_create_request';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='uk_stock_document_create_request' AND non_unique=0 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='create_request_id'));
IF object_count<>1 OR bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting uk_stock_document_create_request definition';
END IF;
END IF;
IF object_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing target index uk_stock_document_create_request';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='idx_stock_document_direction_state';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='idx_stock_document_direction_state' AND non_unique=1 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='direction') OR (seq_in_index=2 AND column_name='status') OR (seq_in_index=3 AND column_name='id'));
IF object_count<>3 OR bad<>3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting idx_stock_document_direction_state definition';
END IF;
END IF;
IF object_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing target index idx_stock_document_direction_state';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='ck_stock_document_direction';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.table_constraints t
JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
WHERE t.constraint_schema=DATABASE() AND t.table_name='wm_stock_document'
AND t.constraint_name='ck_stock_document_direction' AND t.constraint_type='CHECK' AND t.enforced='YES'
AND REGEXP_REPLACE(UPPER(REPLACE(c.check_clause,'_utf8mb4','')), '[^A-Z,]', '') IN ('DIRECTIONININ,OUT','DIRECTIONINOUT,IN');
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting ck_stock_document_direction definition';
END IF;
END IF;
IF object_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing formal direction constraint';
END IF;
SELECT COUNT(*) INTO bad FROM wm_stock_document WHERE document_type IS NULL OR document_type NOT IN ('PURCHASE_RECEIPT','PRODUCTION_RECEIPT','OUTSOURCE_RECEIPT','MATERIAL_RETURN_RECEIPT','REWORK_RECEIPT','SALES_RETURN_RECEIPT','DEFECT_RECEIPT','SCRAP_RECEIPT','OTHER_RECEIPT','MATERIAL_ISSUE','OTHER_ISSUE') OR direction IS NULL OR direction<>CASE WHEN document_type IN ('MATERIAL_ISSUE','OTHER_ISSUE') THEN 'OUT' ELSE 'IN' END;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal document direction or type is unresolved';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_input f
LEFT JOIN wm_stock_document d ON d.id=f.document_id
LEFT JOIN mes_production_execution e ON e.id=f.execution_id
WHERE d.id IS NULL OR e.id IS NULL OR d.document_type<>'MATERIAL_ISSUE' OR d.direction<>'OUT'
OR NOT(d.source_execution_id<=>f.execution_id) OR NOT(d.source_workorder_id<=>e.workorder_id);
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted or inconsistent formal input identity';
END IF;
SELECT COUNT(*) INTO bad FROM wm_stock_document d
LEFT JOIN mes_material_issue_input f ON f.document_id=d.id
WHERE d.document_type='MATERIAL_ISSUE' AND f.id IS NULL
AND (d.status='POSTED' OR d.source_execution_id IS NOT NULL);
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Material issue document is missing its formal input';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_input f
WHERE f.confirmed_at IS NOT NULL AND NOT EXISTS (
 SELECT 1 FROM wm_stock_document_line l JOIN wm_stock_action a ON a.id=l.stock_action_id
 WHERE l.document_id=f.document_id AND a.document_line_id=l.id AND a.action_type='ISSUE'
 AND a.execution_id=f.execution_id AND a.stock_id=f.stock_id);
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Confirmed input is missing original inventory action association';
END IF;
IF legacy_count=0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='mes_material_issue_input' AND column_name='issue_id') OR (table_name='wm_stock_document' AND column_name='source_material_issue_id'));
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old references remain without their source tables';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='document_id' AND is_nullable='NO';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Final document identity must be required';
END IF;
ELSE
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='mes_material_issue_input' AND column_name='issue_id') OR (table_name='wm_stock_document' AND column_name='source_material_issue_id'));
IF bad<>2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old source mapping is incomplete before retirement';
END IF;
SELECT COUNT(*) INTO bad FROM (SELECT 'mes_material_issue' table_name, 'id' column_name UNION ALL SELECT 'mes_material_issue' table_name, 'status' column_name UNION ALL SELECT 'mes_material_issue' table_name, 'issue_time' column_name UNION ALL SELECT 'mes_material_issue' table_name, 'execution_id' column_name UNION ALL SELECT 'mes_material_issue' table_name, 'workorder_id' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'issue_id' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'material_id' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'warehouse_id' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'batch_no' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'unit_id' column_name UNION ALL SELECT 'mes_material_issue_item' table_name, 'quantity' column_name UNION ALL SELECT 'mes_material_issue_submission' table_name, 'issue_id' column_name UNION ALL SELECT 'mes_material_issue_submission' table_name, 'status' column_name UNION ALL SELECT 'mes_material_issue_erp_extra' table_name, 'id' column_name UNION ALL SELECT 'mes_material_issue_erp_extra' table_name, 'source_execution_mode' column_name UNION ALL SELECT 'mes_material_issue_erp_extra' table_name, 'external_effect' column_name UNION ALL SELECT 'mes_material_issue_erp_extra' table_name, 'void_result_status' column_name) source_column LEFT JOIN information_schema.columns c ON c.table_schema=DATABASE() AND c.table_name=source_column.table_name AND c.column_name=source_column.column_name WHERE c.column_name IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old source columns are incomplete';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_item x LEFT JOIN mes_material_issue i ON i.id=x.issue_id WHERE i.id IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted orphan in mes_material_issue_item';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_submission x LEFT JOIN mes_material_issue i ON i.id=x.issue_id WHERE i.id IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted orphan in mes_material_issue_submission';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_erp_extra x LEFT JOIN mes_material_issue i ON i.id=x.id WHERE i.id IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted orphan in mes_material_issue_erp_extra';
END IF;
SELECT COUNT(*) INTO bad FROM mes_material_issue_input x LEFT JOIN mes_material_issue i ON i.id=x.issue_id WHERE i.id IS NULL;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted orphan in mes_material_issue_input';
END IF;
 SELECT COUNT(*) INTO bad FROM (SELECT i.id FROM mes_material_issue i LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id LEFT JOIN wm_stock_document_line l ON l.document_id=d.id GROUP BY i.id HAVING COUNT(DISTINCT d.id)<>1 OR COUNT(l.id)<>1) invalid_mapping;
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source document and target line mapping is not unique'; END IF;
 SELECT COUNT(*) INTO bad FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE() AND referenced_table_name IN ('mes_material_issue','mes_material_issue_item','mes_material_issue_erp_extra','mes_material_issue_submission');
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Old issue tables still have foreign key consumers'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue i
 LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id
 WHERE d.id IS NULL OR d.document_type<>'MATERIAL_ISSUE' OR d.direction<>'OUT';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing unified material issue document'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue_item x
 JOIN wm_stock_document d ON d.source_material_issue_id=x.issue_id
 LEFT JOIN wm_stock_document_line l ON l.document_id=d.id AND l.line_no=1
 WHERE l.id IS NULL OR NOT(l.material_id<=>x.material_id)
 OR NOT(l.warehouse_id<=>x.warehouse_id) OR NOT(l.batch_no<=>x.batch_no)
 OR NOT(l.unit_id<=>x.unit_id) OR NOT(l.quantity<=>x.quantity);
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted material issue line'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue_input f
 LEFT JOIN mes_material_issue i ON i.id=f.issue_id
 LEFT JOIN wm_stock_document d ON d.id=f.document_id
 LEFT JOIN mes_production_execution e ON e.id=f.execution_id
 WHERE i.id IS NULL OR d.id IS NULL OR e.id IS NULL
 OR NOT(d.source_material_issue_id<=>i.id)
 OR NOT(f.execution_id<=>i.execution_id)
 OR NOT(d.source_execution_id<=>i.execution_id)
 OR NOT(d.source_workorder_id<=>i.workorder_id)
 OR NOT(e.workorder_id<=>i.workorder_id)
 OR NOT(f.business_occurred_at<=>i.issue_time);
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unconverted production input reference'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue_input f
 JOIN mes_material_issue i ON i.id=f.issue_id
 LEFT JOIN wm_stock_action a ON a.id=f.id
 LEFT JOIN wm_stock_document_line l ON l.id=a.document_line_id
 WHERE i.status IN ('CONFIRMED','VOIDED') AND
 (a.id IS NULL OR a.action_type<>'ISSUE' OR NOT(l.document_id<=>f.document_id));
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing original inventory action association'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue_submission WHERE status IS NULL OR status<>'SUCCEEDED';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unresolved original submission'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue_erp_extra
 WHERE source_execution_mode IS NULL OR source_execution_mode<>'LOCAL_ONLY' OR external_effect IS NULL OR external_effect<>'NONE';
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unmapped external material issue facts'; END IF;
 SELECT COUNT(*) INTO bad FROM mes_material_issue i JOIN mes_material_issue_input f ON f.issue_id=i.id
 LEFT JOIN wm_stock_action a ON a.id=f.id
 WHERE i.status='VOIDED' AND NOT EXISTS(SELECT 1 FROM wm_stock_action r WHERE r.reference_action_id=a.id AND r.action_type='REVERSAL' AND r.document_line_id=a.document_line_id);
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original reversed input lost its inverse stock action association'; END IF;
 WITH original_totals AS (
 SELECT f.execution_id,i.workorder_id,SUM(CASE WHEN i.status='CONFIRMED' AND s.status='SUCCEEDED' AND f.confirmed_at IS NOT NULL AND f.stock_applied=1 AND COALESCE(e.void_result_status,'')<>'SUCCEEDED' THEN f.output_qty ELSE 0 END) qty
 FROM mes_material_issue_input f JOIN mes_material_issue i ON i.id=f.issue_id JOIN mes_material_issue_submission s ON s.issue_id=i.id JOIN mes_material_issue_erp_extra e ON e.id=i.id
 WHERE f.input_kind='PRODUCTION_INPUT' AND f.source_mode='LOCAL_ONLY' GROUP BY f.execution_id,i.workorder_id
 ), current_totals AS (
 SELECT f.execution_id,d.source_workorder_id workorder_id,SUM(f.output_qty) qty
 FROM mes_material_issue_input f JOIN wm_stock_document d ON d.id=f.document_id AND d.source_execution_id=f.execution_id
 WHERE f.input_kind='PRODUCTION_INPUT' AND f.source_mode='LOCAL_ONLY' AND f.confirmed_at IS NOT NULL AND f.stock_applied=1 AND d.document_type='MATERIAL_ISSUE' AND d.status='POSTED'
 AND NOT EXISTS(SELECT 1 FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id AND r.action_type='REVERSAL' WHERE l.document_id=d.id)
 GROUP BY f.execution_id,d.source_workorder_id
 ), differences AS (SELECT execution_id,workorder_id,qty FROM original_totals UNION ALL SELECT execution_id,workorder_id,-qty FROM current_totals)
 SELECT COUNT(*) INTO bad FROM (SELECT execution_id,workorder_id FROM differences GROUP BY execution_id,workorder_id HAVING SUM(qty)<>0) changed_totals;
 IF bad<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal production contribution totals do not equal original facts'; END IF;
ALTER TABLE mes_material_issue_input MODIFY COLUMN document_id BIGINT NOT NULL COMMENT '统一库存单据主键', DROP COLUMN issue_id;
DROP TABLE mes_material_issue_submission,mes_material_issue_erp_extra,mes_material_issue_item,mes_material_issue;
ALTER TABLE wm_stock_document DROP COLUMN source_material_issue_id;
END IF;
END$$
CALL mes_v75_retire_issue_tables()$$
DROP PROCEDURE mes_v75_retire_issue_tables$$
DELIMITER ;
