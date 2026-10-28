-- Convert legacy material-issue identities and lines into the unified stock-document identities.
-- Historical stock actions are linked to the new lines; no inventory action is replayed or inserted.
DELIMITER $$
CREATE PROCEDURE mes_v70_converge_material_issue()
BEGIN
    DECLARE bad_rows BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue
    WHERE status NOT IN ('DRAFT','CONFIRMED','VOIDED')
       OR source_mode IS NULL OR source_mode <> 'LOCAL_ONLY';
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Material issue status/source is not safely convertible';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT issue_no FROM mes_material_issue GROUP BY issue_no HAVING COUNT(*) <> 1) duplicate_numbers;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy material issue numbers are not unique';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN wm_stock_document d ON d.document_no=i.issue_no
    WHERE d.document_type <> 'MATERIAL_ISSUE'
       OR d.source_material_issue_id IS NULL
       OR d.source_material_issue_id <> i.id;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy issue number collides with an unrelated stock document';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_document
    WHERE source_material_issue_id IS NOT NULL AND document_type <> 'MATERIAL_ISSUE';
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A non-issue document still carries a legacy issue reference';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_document d
    LEFT JOIN mes_material_issue i ON i.id=d.source_material_issue_id
    WHERE d.document_type='MATERIAL_ISSUE' AND d.source_material_issue_id IS NOT NULL AND i.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stock document has an orphaned legacy issue reference';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT i.id
          FROM mes_material_issue i
          LEFT JOIN mes_material_issue_item x ON x.issue_id=i.id
          GROUP BY i.id
          HAVING COUNT(x.id)<>1) non_single_line_issues;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material issue does not have exactly one source line';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_item x
    JOIN mes_material_issue i ON i.id=x.issue_id
    LEFT JOIN mes_material_issue_input f ON f.issue_id=x.issue_id
    WHERE x.unit_id IS NULL OR (f.id IS NULL AND i.status<>'DRAFT')
       OR (f.id IS NOT NULL AND i.status='CONFIRMED' AND (NOT (f.stock_applied <=> 1) OR f.confirmed_at IS NULL))
       OR (f.id IS NOT NULL AND i.status='VOIDED' AND (f.stock_applied <> 0 OR f.confirmed_at IS NULL
            OR i.void_operation_id IS NULL OR i.void_result_status <> 'SUCCEEDED'));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material issue line or production-input fact is incomplete';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN mes_material_issue_input f ON f.issue_id=i.id
    JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
       AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
    LEFT JOIN wm_stock_action reversal ON reversal.reference_action_id=a.id
       AND reversal.action_type='REVERSAL'
    WHERE i.status='VOIDED' AND reversal.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A voided issue has no linked original inventory reversal';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN mes_material_issue_input f ON f.issue_id=i.id
    LEFT JOIN mes_material_issue_submission s ON s.issue_id=i.id AND s.id=i.submission_id
    WHERE f.source_mode <> 'LOCAL_ONLY' OR s.id IS NULL OR s.status <> 'SUCCEEDED';
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production-input request lacks a successful LOCAL submission fact';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_item x
    JOIN mes_material_issue_input f ON f.issue_id=x.issue_id
    LEFT JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
        AND a.action_type='ISSUE' AND a.source_id=CAST(x.issue_id AS CHAR)
    WHERE a.id IS NULL OR a.material_id<>x.material_id OR a.unit_id<>x.unit_id
       OR a.quantity_delta<>-x.quantity OR NOT (a.warehouse_id <=> x.warehouse_id)
       OR NOT (a.batch_no <=> x.batch_no) OR NOT (a.stock_id <=> f.stock_id)
       OR NOT (a.execution_id <=> f.execution_id);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A prior inventory action does not match its material-issue fact';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT source_material_issue_id
          FROM wm_stock_document
          WHERE document_type='MATERIAL_ISSUE' AND source_material_issue_id IS NOT NULL
          GROUP BY source_material_issue_id HAVING COUNT(*) <> 1) duplicate_links;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A legacy issue is linked to multiple stock documents';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
       AND d.document_type='MATERIAL_ISSUE'
    LEFT JOIN mes_material_issue_item x ON x.issue_id=i.id
    LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
    LEFT JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
       AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
    LEFT JOIN wm_stock_document_line l ON l.document_id=d.id AND l.stock_action_id=a.id
    WHERE x.id IS NULL OR a.id IS NULL OR l.id IS NULL
       OR l.material_id<>x.material_id OR l.unit_id<>x.unit_id OR l.quantity<>x.quantity
       OR NOT (l.warehouse_id <=> x.warehouse_id) OR NOT (l.batch_no <=> x.batch_no)
       OR NOT (l.stock_id <=> f.stock_id) OR NOT (l.bin_code <=> a.bin_code)
       OR (a.document_line_id IS NOT NULL AND a.document_line_id<>l.id);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An already-linked document line or action does not match its source';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT i.id
          FROM mes_material_issue i
          LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
          GROUP BY i.id
          HAVING COUNT(f.id)>1) duplicate_inputs;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A legacy issue has multiple production-input facts';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_submission s
    LEFT JOIN mes_material_issue i ON i.id=s.issue_id
    WHERE i.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A submission fact has no legacy issue mapping';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT source_id FROM wm_stock_action
          WHERE source_type='MATERIAL_ISSUE' AND action_type='ISSUE'
          GROUP BY source_id HAVING COUNT(*) <> 1) duplicate_actions;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Material-issue inventory-action identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_document d
    JOIN mes_material_issue i ON i.id=d.source_material_issue_id
    LEFT JOIN mes_material_issue_item x ON x.issue_id=i.id
    LEFT JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
        AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
    LEFT JOIN wm_stock_document_line l ON l.document_id=d.id AND l.stock_action_id=a.id
    WHERE d.document_type='MATERIAL_ISSUE'
      AND (x.id IS NULL OR a.id IS NULL OR l.id IS NULL);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An already-linked issue document lacks its matching action line';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_document d
    JOIN mes_material_issue i ON i.id=d.source_material_issue_id
    JOIN mes_material_issue_item x ON x.issue_id=i.id
    LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
    JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
        AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
    JOIN wm_stock_document_line l ON l.document_id=d.id AND l.stock_action_id=a.id
    WHERE d.document_type='MATERIAL_ISSUE'
      AND (l.material_id<>x.material_id OR l.unit_id<>x.unit_id OR l.quantity<>x.quantity
        OR NOT (l.warehouse_id <=> x.warehouse_id) OR NOT (l.batch_no <=> x.batch_no)
        OR NOT (l.stock_id <=> f.stock_id) OR NOT (l.bin_code <=> a.bin_code));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An existing document line differs from its legacy item';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    WHERE d.id IS NOT NULL
      AND (NOT (d.source_workorder_id <=> i.workorder_id)
        OR NOT (d.source_execution_id <=> i.execution_id)
        OR d.status <> CASE WHEN i.status='DRAFT' THEN 'DRAFT' ELSE 'POSTED' END);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing stock-document source differs from the legacy issue';
    END IF;

    CREATE TABLE wm_stock_document_production_context (
        document_id BIGINT NOT NULL COMMENT 'Unified stock-document identity',
        legacy_issue_id BIGINT NOT NULL COMMENT 'Immutable source identity retained for migration traceability',
        original_issue_no VARCHAR(64) NOT NULL COMMENT 'Original production issue number; may differ from document_no',
        legacy_status VARCHAR(24) NOT NULL COMMENT 'Original production-input lifecycle state',
        source_mode VARCHAR(20) NULL COMMENT 'Persisted source fact; NULL remains unknown',
        product_id BIGINT NULL,
        product_code VARCHAR(64) NULL,
        flow_card_no VARCHAR(128) NULL,
        routing_id BIGINT NULL,
        issue_time DATETIME NULL,
        legacy_remark VARCHAR(500) NULL,
        legacy_create_by VARCHAR(64) NULL,
        legacy_create_time DATETIME NULL,
        legacy_update_by VARCHAR(64) NULL,
        legacy_update_time DATETIME NULL,
        PRIMARY KEY (document_id),
        UNIQUE KEY uk_issue_context_legacy_identity (legacy_issue_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Production-specific immutable facts attached to unified stock documents';

    CREATE TABLE wm_stock_document_production_line_context (
        line_id BIGINT NOT NULL COMMENT 'Unified stock-document line identity',
        legacy_item_id BIGINT NOT NULL COMMENT 'Immutable source line identity retained for migration traceability',
        material_code VARCHAR(64) NULL,
        unit_code VARCHAR(64) NULL,
        warehouse_code VARCHAR(64) NULL,
        legacy_create_by VARCHAR(64) NULL,
        legacy_create_time DATETIME NULL,
        legacy_update_by VARCHAR(64) NULL,
        legacy_update_time DATETIME NULL,
        PRIMARY KEY (line_id),
        UNIQUE KEY uk_issue_line_context_legacy_identity (legacy_item_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Production-specific immutable line snapshots attached to unified stock-document lines';

    ALTER TABLE wm_stock_document
        MODIFY create_by VARCHAR(64) NULL,
        MODIFY create_time DATETIME(6) NULL,
        MODIFY update_by VARCHAR(64) NULL,
        MODIFY update_time DATETIME(6) NULL;

    ALTER TABLE wm_stock_document_line
        MODIFY warehouse_id BIGINT NULL,
        MODIFY stock_id BIGINT NULL;

    ALTER TABLE mes_material_issue_input
        ADD COLUMN document_id BIGINT NULL COMMENT 'Unified stock-document identity',
        ADD COLUMN submission_id BIGINT NULL COMMENT 'Original production-input submission fact',
        ADD COLUMN path_revision INT NULL COMMENT 'Frozen execution-path revision',
        ADD COLUMN void_operation_id VARCHAR(64) NULL COMMENT 'Original LOCAL reversal operation identity',
        ADD COLUMN void_from_status VARCHAR(20) NULL COMMENT 'Original production lifecycle state before reversal',
        ADD COLUMN void_requested_at DATETIME NULL,
        ADD COLUMN void_finished_at DATETIME NULL,
        ADD COLUMN void_result_status VARCHAR(20) NULL COMMENT 'LOCAL reversal result only';

    ALTER TABLE mes_material_issue_submission
        ADD COLUMN document_id BIGINT NULL COMMENT 'Unified stock-document identity';

    CREATE TEMPORARY TABLE mes_v70_issue_document_map (
        legacy_issue_id BIGINT NOT NULL PRIMARY KEY,
        document_id BIGINT NOT NULL UNIQUE
    ) ENGINE=MEMORY;

    INSERT INTO mes_v70_issue_document_map(legacy_issue_id,document_id)
    SELECT d.source_material_issue_id,d.id
    FROM wm_stock_document d
    WHERE d.document_type='MATERIAL_ISSUE' AND d.source_material_issue_id IS NOT NULL;

    INSERT INTO wm_stock_document(document_no,document_type,status,reason,remark,source_workorder_id,
        create_by,create_time,update_by,update_time,posted_by,posted_at,revision,source_execution_id,
        source_material_issue_id,source_request_json)
    SELECT i.issue_no,'MATERIAL_ISSUE',
        CASE WHEN i.status='DRAFT' THEN 'DRAFT' ELSE 'POSTED' END,
        '生产领料历史单据迁移',i.remark,i.workorder_id,
        i.create_by,i.create_time,i.update_by,i.update_time,NULL,NULL,0,i.execution_id,i.id,NULL
    FROM mes_material_issue i
    LEFT JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=i.id
    WHERE m.legacy_issue_id IS NULL;

    INSERT INTO mes_v70_issue_document_map(legacy_issue_id,document_id)
    SELECT d.source_material_issue_id,d.id
    FROM wm_stock_document d
    LEFT JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=d.source_material_issue_id
    WHERE d.document_type='MATERIAL_ISSUE' AND d.source_material_issue_id IS NOT NULL
      AND m.legacy_issue_id IS NULL;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    LEFT JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=i.id
    WHERE m.document_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Not every legacy issue has an explicit document identity mapping';
    END IF;

    INSERT INTO wm_stock_document_production_context(document_id,legacy_issue_id,original_issue_no,
        legacy_status,source_mode,product_id,product_code,flow_card_no,routing_id,issue_time,legacy_remark,
        legacy_create_by,legacy_create_time,legacy_update_by,legacy_update_time)
    SELECT m.document_id,i.id,i.issue_no,i.status,i.source_mode,i.product_id,i.product_code,i.flow_card_no,
        i.routing_id,i.issue_time,i.remark,i.create_by,i.create_time,i.update_by,i.update_time
    FROM mes_material_issue i JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=i.id;

    CREATE TEMPORARY TABLE mes_v70_issue_item_order (
        legacy_item_id BIGINT NOT NULL PRIMARY KEY,
        legacy_issue_id BIGINT NOT NULL,
        document_id BIGINT NOT NULL,
        line_no INT NOT NULL,
        stock_action_id VARCHAR(36) NULL,
        UNIQUE KEY uk_issue_document_line (document_id,line_no),
        UNIQUE KEY uk_issue_item_action (stock_action_id)
    ) ENGINE=MEMORY;

    INSERT INTO mes_v70_issue_item_order(legacy_item_id,legacy_issue_id,document_id,line_no,stock_action_id)
    SELECT x.id,x.issue_id,m.document_id,
        ROW_NUMBER() OVER (PARTITION BY x.issue_id ORDER BY x.id),a.id
    FROM mes_material_issue_item x
    JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=x.issue_id
    LEFT JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
        AND a.action_type='ISSUE' AND a.source_id=CAST(x.issue_id AS CHAR);

    SELECT COUNT(*) INTO bad_rows
    FROM mes_v70_issue_item_order o
    JOIN wm_stock_document_line l ON l.document_id=o.document_id
       AND l.stock_action_id=o.stock_action_id
    WHERE o.stock_action_id IS NOT NULL
      AND (l.material_id<>(SELECT x.material_id FROM mes_material_issue_item x WHERE x.id=o.legacy_item_id)
        OR l.quantity<>(SELECT x.quantity FROM mes_material_issue_item x WHERE x.id=o.legacy_item_id));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing stock-document line differs from its source item';
    END IF;

    INSERT INTO wm_stock_document_line(document_id,line_no,material_id,warehouse_id,stock_id,batch_no,bin_code,
        unit_id,quantity,remark,stock_action_id,revision)
    SELECT o.document_id,o.line_no,x.material_id,x.warehouse_id,f.stock_id,x.batch_no,a.bin_code,
        x.unit_id,x.quantity,x.remark,a.id,0
    FROM mes_v70_issue_item_order o
    JOIN mes_material_issue_item x ON x.id=o.legacy_item_id
    LEFT JOIN mes_material_issue_input f ON f.issue_id=x.issue_id
    LEFT JOIN wm_stock_action a ON a.id=o.stock_action_id
    LEFT JOIN wm_stock_document_line existing_line ON existing_line.document_id=o.document_id
       AND existing_line.stock_action_id=o.stock_action_id
    WHERE existing_line.id IS NULL;

    UPDATE mes_v70_issue_item_order o
    JOIN wm_stock_document_line l ON l.document_id=o.document_id AND l.line_no=o.line_no
    SET o.stock_action_id=COALESCE(o.stock_action_id,l.stock_action_id);

    INSERT INTO wm_stock_document_production_line_context(line_id,legacy_item_id,material_code,unit_code,
        warehouse_code,legacy_create_by,legacy_create_time,legacy_update_by,legacy_update_time)
    SELECT l.id,x.id,x.material_code,x.unit_code,x.warehouse_code,x.create_by,x.create_time,x.update_by,x.update_time
    FROM mes_v70_issue_item_order o
    JOIN wm_stock_document_line l ON l.document_id=o.document_id AND l.line_no=o.line_no
    JOIN mes_material_issue_item x ON x.id=o.legacy_item_id;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_item x
    LEFT JOIN wm_stock_document_production_line_context c ON c.legacy_item_id=x.id
    WHERE c.line_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Not every legacy item has an explicit line identity mapping';
    END IF;

    UPDATE wm_stock_action a
    JOIN mes_v70_issue_item_order o ON a.source_type='MATERIAL_ISSUE'
       AND a.action_type='ISSUE' AND a.source_id=CAST(o.legacy_issue_id AS CHAR)
    JOIN wm_stock_document_line l ON l.document_id=o.document_id AND l.line_no=o.line_no
    SET a.document_line_id=l.id
    WHERE a.document_line_id IS NULL;

    UPDATE mes_material_issue_input f
    JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=f.issue_id
    JOIN mes_material_issue i ON i.id=f.issue_id
    JOIN mes_material_issue_submission s ON s.id=i.submission_id AND s.issue_id=i.id
    SET f.document_id=m.document_id,f.submission_id=s.id,f.path_revision=s.path_revision,
        f.void_operation_id=i.void_operation_id,f.void_from_status=i.void_from_status,
        f.void_requested_at=i.void_requested_at,f.void_finished_at=i.void_finished_at,
        f.void_result_status=i.void_result_status;

    UPDATE mes_material_issue_submission s
    JOIN mes_v70_issue_document_map m ON m.legacy_issue_id=s.issue_id
    SET s.document_id=m.document_id;

    SELECT COUNT(*) INTO bad_rows FROM mes_material_issue_input WHERE document_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production-input fact was not mapped to a stock document';
    END IF;
    SELECT COUNT(*) INTO bad_rows FROM mes_material_issue_submission WHERE document_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A submission fact was not mapped to a stock document';
    END IF;
    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_action a JOIN mes_v70_issue_item_order o
       ON a.source_type='MATERIAL_ISSUE' AND a.action_type='ISSUE'
      AND a.source_id=CAST(o.legacy_issue_id AS CHAR)
    JOIN wm_stock_document_line l ON l.document_id=o.document_id AND l.line_no=o.line_no
    WHERE a.document_line_id IS NULL OR a.document_line_id<>l.id;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A historical inventory action is not linked to its migrated document line';
    END IF;

    ALTER TABLE mes_material_issue_input
        MODIFY document_id BIGINT NOT NULL,
        DROP INDEX idx_input_execution,
        DROP INDEX idx_input_issue,
        DROP COLUMN issue_id,
        ADD UNIQUE KEY uk_material_issue_input_document (document_id),
        ADD KEY idx_input_execution (execution_id,document_id);

    ALTER TABLE mes_material_issue_submission
        MODIFY document_id BIGINT NOT NULL,
        DROP INDEX idx_issue_submission,
        DROP COLUMN issue_id,
        ADD KEY idx_submission_document (document_id,id);

    ALTER TABLE wm_stock_document
        DROP INDEX idx_stock_document_material_issue,
        DROP COLUMN source_material_issue_id;

    DROP TABLE mes_material_issue_item;
    DROP TABLE mes_material_issue;
    DROP TEMPORARY TABLE mes_v70_issue_item_order;
    DROP TEMPORARY TABLE mes_v70_issue_document_map;
END$$
CALL mes_v70_converge_material_issue()$$
DROP PROCEDURE mes_v70_converge_material_issue$$
DELIMITER ;
