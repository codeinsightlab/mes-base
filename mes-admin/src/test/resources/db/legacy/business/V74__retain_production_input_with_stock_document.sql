-- 生产投入结构：仅补齐缺失对象，现存对象须与正式定义一致。
-- 先核实全部现存定义和方向语义，再仅补缺失结构；不重放数据转换或库存动作。
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v74_retain_input_structure$$
CREATE PROCEDURE mes_v74_retain_input_structure()
BEGIN
DECLARE bad BIGINT DEFAULT 0;
DECLARE object_count BIGINT DEFAULT 0;
DECLARE direction_count BIGINT DEFAULT 0;
SELECT COUNT(*) INTO bad FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name IN ('mes_material_issue_input','wm_stock_document','wm_stock_document_line');
IF bad<>3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal input and stock document tables are required';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='document_id';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='document_id' AND column_type='bigint' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting mes_material_issue_input.document_id definition';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='business_occurred_at';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='business_occurred_at' AND column_type='datetime(6)' AND column_default IS NULL AND extra='' AND is_nullable='YES';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting mes_material_issue_input.business_occurred_at definition';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction' AND column_type='varchar(3)' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting wm_stock_document.direction definition';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='create_request_id';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='create_request_id' AND column_type='varchar(36)' AND column_default IS NULL AND extra='' AND is_nullable='YES' AND character_set_name='ascii' AND collation_name='ascii_bin';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting wm_stock_document.create_request_id definition';
END IF;
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='warehouse_id' AND column_type='bigint' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting stock line warehouse_id';
END IF;
SELECT COUNT(*) INTO bad FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='stock_id' AND column_type='bigint' AND column_default IS NULL AND extra='';
IF bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting stock line stock_id';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND index_name='uk_input_document';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND index_name='uk_input_document' AND non_unique=0 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='document_id'));
IF object_count<>1 OR bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting uk_input_document definition';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='uk_stock_document_create_request';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='uk_stock_document_create_request' AND non_unique=0 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='create_request_id'));
IF object_count<>1 OR bad<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting uk_stock_document_create_request definition';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='idx_stock_document_direction_state';
IF object_count<>0 THEN
SELECT COUNT(*) INTO bad FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='idx_stock_document_direction_state' AND non_unique=1 AND sub_part IS NULL AND collation='A' AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL AND ((seq_in_index=1 AND column_name='direction') OR (seq_in_index=2 AND column_name='status') OR (seq_in_index=3 AND column_name='id'));
IF object_count<>3 OR bad<>3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting idx_stock_document_direction_state definition';
END IF;
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
SELECT COUNT(*) INTO bad FROM wm_stock_document WHERE document_type IS NULL OR document_type NOT IN ('PURCHASE_RECEIPT','PRODUCTION_RECEIPT','OUTSOURCE_RECEIPT','MATERIAL_RETURN_RECEIPT','REWORK_RECEIPT','SALES_RETURN_RECEIPT','DEFECT_RECEIPT','SCRAP_RECEIPT','OTHER_RECEIPT','MATERIAL_ISSUE','OTHER_ISSUE');
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown stock document type requires explicit review';
END IF;
SELECT COUNT(*) INTO direction_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction';
IF direction_count<>0 THEN
SELECT COUNT(*) INTO bad FROM wm_stock_document WHERE direction IS NULL OR direction<>CASE WHEN document_type IN ('MATERIAL_ISSUE','OTHER_ISSUE') THEN 'OUT' ELSE 'IN' END;
IF bad<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing stock direction conflicts with formal document type';
END IF;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='document_id';
IF object_count=0 THEN
ALTER TABLE mes_material_issue_input ADD COLUMN document_id BIGINT NULL COMMENT '统一库存单据主键；转换完成前为空';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND column_name='business_occurred_at';
IF object_count=0 THEN
ALTER TABLE mes_material_issue_input ADD COLUMN business_occurred_at DATETIME(6) NULL COMMENT '生产投入实际业务发生时间，独立于请求建立和确认时间';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction';
IF object_count=0 THEN
ALTER TABLE wm_stock_document ADD COLUMN direction VARCHAR(3) NULL COMMENT '库存方向IN或OUT';
UPDATE wm_stock_document SET direction=CASE WHEN document_type IN ('MATERIAL_ISSUE','OTHER_ISSUE') THEN 'OUT' ELSE 'IN' END;
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='create_request_id';
IF object_count=0 THEN
ALTER TABLE wm_stock_document ADD COLUMN create_request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '草稿创建请求UUID，独立于审核请求';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction' AND is_nullable='NO';
IF object_count=0 THEN
ALTER TABLE wm_stock_document MODIFY COLUMN direction VARCHAR(3) NOT NULL COMMENT '库存方向IN或OUT';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mes_material_issue_input' AND index_name='uk_input_document';
IF object_count=0 THEN
ALTER TABLE mes_material_issue_input ADD UNIQUE KEY uk_input_document(document_id);
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='uk_stock_document_create_request';
IF object_count=0 THEN
ALTER TABLE wm_stock_document ADD UNIQUE KEY uk_stock_document_create_request(create_request_id);
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND index_name='idx_stock_document_direction_state';
IF object_count=0 THEN
ALTER TABLE wm_stock_document ADD KEY idx_stock_document_direction_state(direction,status,id);
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='ck_stock_document_direction';
IF object_count=0 THEN
ALTER TABLE wm_stock_document ADD CONSTRAINT ck_stock_document_direction CHECK(direction IN ('IN','OUT'));
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='warehouse_id' AND is_nullable='NO';
IF object_count=1 THEN
ALTER TABLE wm_stock_document_line MODIFY COLUMN warehouse_id BIGINT NULL COMMENT '库存来源仓库；草稿允许缺失，审核必须有效';
END IF;
SELECT COUNT(*) INTO object_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_stock_document_line' AND column_name='stock_id' AND is_nullable='NO';
IF object_count=1 THEN
ALTER TABLE wm_stock_document_line MODIFY COLUMN stock_id BIGINT NULL COMMENT '库存来源主键；草稿允许缺失，审核必须有效';
END IF;
END$$
CALL mes_v74_retain_input_structure()$$
DROP PROCEDURE mes_v74_retain_input_structure$$
DELIMITER ;
