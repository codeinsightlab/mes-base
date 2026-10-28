-- Convert legacy material-issue identities and lines into the unified stock-document identities.
-- Historical stock actions are linked to the new lines; no inventory action is replayed or inserted.
-- Run only after the new application version is loaded and business writes are quiesced.
-- The existing source_material_issue_id column is the in-session mapping carrier and is dropped below.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v70_converge_material_issue$$
CREATE PROCEDURE mes_v70_converge_material_issue()
BEGIN
    DECLARE bad_rows BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO bad_rows
    FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name IN (
        'mes_material_issue','mes_material_issue_item','mes_material_issue_input',
        'mes_material_issue_submission','wm_stock_document','wm_stock_document_line','wm_stock_action',
        'mes_production_execution','material','md_unit_measure','wm_warehouse'
    );
    IF bad_rows<>11 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required pre-convergence tables are missing; this version refuses partial or uninitialized schemas';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'mes_material_issue' table_name,'id' column_name UNION ALL
        SELECT 'mes_material_issue','issue_no' UNION ALL SELECT 'mes_material_issue','status' UNION ALL
        SELECT 'mes_material_issue','issue_time' UNION ALL SELECT 'mes_material_issue','workorder_code' UNION ALL
        SELECT 'mes_material_issue','product_id' UNION ALL SELECT 'mes_material_issue','product_code' UNION ALL
        SELECT 'mes_material_issue','flow_card_no' UNION ALL SELECT 'mes_material_issue','routing_id' UNION ALL
        SELECT 'mes_material_issue','source_mode' UNION ALL SELECT 'mes_material_issue','void_result_status' UNION ALL
        SELECT 'mes_material_issue','execution_id' UNION ALL SELECT 'mes_material_issue','submission_id' UNION ALL
        SELECT 'mes_material_issue','workorder_id' UNION ALL SELECT 'mes_material_issue','remark' UNION ALL
        SELECT 'mes_material_issue','create_by' UNION ALL SELECT 'mes_material_issue','create_time' UNION ALL
        SELECT 'mes_material_issue','update_by' UNION ALL SELECT 'mes_material_issue','update_time' UNION ALL
        SELECT 'mes_material_issue','void_operation_id' UNION ALL SELECT 'mes_material_issue','void_from_status' UNION ALL
        SELECT 'mes_material_issue','void_requested_at' UNION ALL SELECT 'mes_material_issue','void_finished_at' UNION ALL
        SELECT 'mes_material_issue_item','id' UNION ALL SELECT 'mes_material_issue_item','issue_id' UNION ALL
        SELECT 'mes_material_issue_item','unit_id' UNION ALL SELECT 'mes_material_issue_item','material_id' UNION ALL
        SELECT 'mes_material_issue_item','warehouse_id' UNION ALL SELECT 'mes_material_issue_item','batch_no' UNION ALL
        SELECT 'mes_material_issue_item','quantity' UNION ALL SELECT 'mes_material_issue_item','remark' UNION ALL
        SELECT 'mes_material_issue_item','material_code' UNION ALL SELECT 'mes_material_issue_item','unit_code' UNION ALL
        SELECT 'mes_material_issue_item','warehouse_code' UNION ALL
        SELECT 'mes_material_issue_input','id' UNION ALL SELECT 'mes_material_issue_input','issue_id' UNION ALL
        SELECT 'mes_material_issue_input','execution_id' UNION ALL SELECT 'mes_material_issue_input','source_mode' UNION ALL
        SELECT 'mes_material_issue_input','stock_applied' UNION ALL SELECT 'mes_material_issue_input','confirmed_at' UNION ALL
        SELECT 'mes_material_issue_input','calculation_json' UNION ALL SELECT 'mes_material_issue_input','stock_id' UNION ALL
        SELECT 'mes_material_issue_submission','id' UNION ALL SELECT 'mes_material_issue_submission','issue_id' UNION ALL
        SELECT 'mes_material_issue_submission','path_revision' UNION ALL SELECT 'mes_material_issue_submission','command_json' UNION ALL
        SELECT 'mes_material_issue_submission','status' UNION ALL
        SELECT 'wm_stock_document','id' UNION ALL SELECT 'wm_stock_document','document_no' UNION ALL
        SELECT 'wm_stock_document','document_type' UNION ALL SELECT 'wm_stock_document','status' UNION ALL
        SELECT 'wm_stock_document','reason' UNION ALL SELECT 'wm_stock_document','remark' UNION ALL
        SELECT 'wm_stock_document','source_workorder_id' UNION ALL SELECT 'wm_stock_document','source_execution_id' UNION ALL
        SELECT 'wm_stock_document','source_request_json' UNION ALL SELECT 'wm_stock_document','create_by' UNION ALL
        SELECT 'wm_stock_document','create_time' UNION ALL SELECT 'wm_stock_document','update_by' UNION ALL
        SELECT 'wm_stock_document','update_time' UNION ALL SELECT 'wm_stock_document','posted_by' UNION ALL
        SELECT 'wm_stock_document','posted_at' UNION ALL SELECT 'wm_stock_document','revision' UNION ALL
        SELECT 'wm_stock_document_line','id' UNION ALL SELECT 'wm_stock_document_line','document_id' UNION ALL
        SELECT 'wm_stock_document_line','line_no' UNION ALL SELECT 'wm_stock_document_line','material_id' UNION ALL
        SELECT 'wm_stock_document_line','warehouse_id' UNION ALL SELECT 'wm_stock_document_line','stock_id' UNION ALL
        SELECT 'wm_stock_document_line','batch_no' UNION ALL SELECT 'wm_stock_document_line','bin_code' UNION ALL
        SELECT 'wm_stock_document_line','unit_id' UNION ALL SELECT 'wm_stock_document_line','quantity' UNION ALL
        SELECT 'wm_stock_document_line','remark' UNION ALL SELECT 'wm_stock_document_line','stock_action_id' UNION ALL
        SELECT 'wm_stock_document_line','revision' UNION ALL
        SELECT 'wm_stock_action','id' UNION ALL SELECT 'wm_stock_action','source_id' UNION ALL
        SELECT 'wm_stock_action','source_type' UNION ALL SELECT 'wm_stock_action','action_type' UNION ALL
        SELECT 'wm_stock_action','reference_action_id' UNION ALL SELECT 'wm_stock_action','material_id' UNION ALL
        SELECT 'wm_stock_action','unit_id' UNION ALL SELECT 'wm_stock_action','quantity_delta' UNION ALL
        SELECT 'wm_stock_action','warehouse_id' UNION ALL SELECT 'wm_stock_action','batch_no' UNION ALL
        SELECT 'wm_stock_action','stock_id' UNION ALL SELECT 'wm_stock_action','execution_id' UNION ALL
        SELECT 'wm_stock_action','document_line_id' UNION ALL SELECT 'wm_stock_action','bin_code' UNION ALL
        SELECT 'mes_production_execution','id' UNION ALL SELECT 'mes_production_execution','workorder_id' UNION ALL
        SELECT 'mes_production_execution','routing_id' UNION ALL
        SELECT 'material','id' UNION ALL SELECT 'material','code' UNION ALL
        SELECT 'md_unit_measure','measure_id' UNION ALL SELECT 'md_unit_measure','measure_code' UNION ALL
        SELECT 'wm_warehouse','id' UNION ALL SELECT 'wm_warehouse','code'
    ) required_column
    LEFT JOIN information_schema.columns actual_column
      ON actual_column.table_schema=DATABASE()
     AND actual_column.table_name=required_column.table_name
     AND actual_column.column_name=required_column.column_name
    WHERE actual_column.column_name IS NULL;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A required conversion column is missing; this version refuses a partial schema';
    END IF;

    SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) INTO bad_rows
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND (
        (table_name='mes_material_issue_input' AND index_name IN ('idx_input_execution','idx_input_issue'))
        OR (table_name='mes_material_issue_submission' AND index_name='idx_issue_submission')
        OR (table_name='wm_stock_document' AND index_name='idx_stock_document_material_issue')
    );
    IF bad_rows<>4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A required pre-conversion index is missing; this version refuses a partial schema';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name IN (
        'mes_material_issue_erp_extra','wm_stock_document_production_context',
        'wm_stock_document_production_line_context'
    );
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V69 is incomplete or an obsolete context table exists';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND (
        (table_name='wm_stock_document' AND column_name='source_material_issue_id')
        OR (table_name='wm_stock_action' AND column_name='document_line_id')
        OR (table_name='mes_material_issue' AND column_name IN ('source_mode','void_result_status','execution_id','submission_id','issue_time'))
        OR (table_name='mes_material_issue_input' AND column_name IN ('issue_id','execution_id','source_mode','stock_applied','confirmed_at','calculation_json'))
        OR (table_name='mes_material_issue_submission' AND column_name IN ('issue_id','path_revision','command_json','status'))
    );
    IF bad_rows<>17 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required pre-convergence columns are incomplete or already partially migrated';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND (
        (table_name='mes_material_issue_input' AND column_name='document_id')
        OR (table_name='mes_material_issue_submission' AND column_name='document_id')
        OR (table_name='mes_material_issue_input' AND column_name='business_occurred_at')
    );
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Document identity columns already exist; partial V70 state requires explicit repair';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue
    WHERE issue_no IS NULL OR status IS NULL OR status NOT IN ('DRAFT','CONFIRMED','VOIDED')
       OR source_mode IS NULL OR source_mode <> 'LOCAL_ONLY';
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Material issue status/source is not safely convertible';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_item x
    LEFT JOIN mes_material_issue i ON i.id=x.issue_id
    WHERE i.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material issue line has no legacy header';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_input f
    LEFT JOIN mes_material_issue i ON i.id=f.issue_id
    WHERE i.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production-input fact has no legacy header';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_action a
    LEFT JOIN mes_material_issue i ON a.source_id=CAST(i.id AS CHAR)
    WHERE a.source_type='MATERIAL_ISSUE' AND a.action_type='ISSUE' AND i.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material-issue inventory action has no legacy header identity';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_action a
    JOIN mes_material_issue i ON a.source_id=CAST(i.id AS CHAR)
    LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    LEFT JOIN wm_stock_document_line l ON l.id=a.document_line_id
    WHERE a.source_type='MATERIAL_ISSUE' AND a.action_type='ISSUE'
      AND a.document_line_id IS NOT NULL
      AND (d.id IS NULL OR l.id IS NULL OR l.document_id<>d.id);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material-issue action already references a conflicting document line';
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
       OR (f.id IS NOT NULL AND i.status='DRAFT')
       OR (f.id IS NOT NULL AND i.status='CONFIRMED' AND (NOT (f.stock_applied <=> 1)
            OR f.confirmed_at IS NULL OR i.void_result_status IN ('SUBMITTING','UNKNOWN','SUCCEEDED')))
       OR (f.id IS NOT NULL AND i.status='VOIDED' AND (NOT (f.stock_applied <=> 0) OR f.confirmed_at IS NULL
            OR i.void_operation_id IS NULL OR NOT (i.void_result_status <=> 'SUCCEEDED')));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material issue line or production-input fact is incomplete';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
    LEFT JOIN mes_material_issue_submission s ON s.issue_id=i.id
       AND s.id=i.submission_id
    WHERE (f.id IS NOT NULL AND (NOT (i.execution_id <=> f.execution_id)
        OR NOT (i.source_mode <=> f.source_mode)
        OR s.id IS NULL OR NOT (i.submission_id <=> s.id)
        OR s.status<>'SUCCEEDED'))
       OR (f.id IS NULL AND (i.submission_id IS NOT NULL OR i.void_operation_id IS NOT NULL
        OR i.void_from_status IS NOT NULL OR i.void_requested_at IS NOT NULL
        OR i.void_finished_at IS NOT NULL OR i.void_result_status IS NOT NULL));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production input, submission, or reversal identity is inconsistent';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
    WHERE i.flow_card_no IS NOT NULL OR (f.id IS NULL AND i.issue_time IS NOT NULL);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A historical flow-card or draft business time has no lossless production-fact destination';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN mes_material_issue_input f ON f.issue_id=i.id
    JOIN mes_production_execution e ON e.id=f.execution_id
    WHERE NOT JSON_VALID(f.calculation_json)
       OR NOT (i.workorder_id <=> e.workorder_id)
       OR NOT (i.workorder_code <=> JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.order.workorderCode')))
       OR NOT (i.product_id <=> CAST(JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.order.productId')) AS UNSIGNED))
       OR NOT (i.product_code <=> JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.order.productCode')))
       OR NOT (i.routing_id <=> e.routing_id);
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A legacy production snapshot differs from its retained input calculation or Execution identity';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_item x
    LEFT JOIN mes_material_issue_input f ON f.issue_id=x.issue_id
    LEFT JOIN material m ON m.id=x.material_id
    LEFT JOIN md_unit_measure u ON u.measure_id=x.unit_id
    LEFT JOIN wm_warehouse w ON w.id=x.warehouse_id
    WHERE (f.id IS NOT NULL AND (NOT JSON_VALID(f.calculation_json)
        OR NOT (x.material_code <=> JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.stock.materialCode')))
        OR NOT (x.unit_code <=> JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.stock.unitCode')))
        OR NOT (x.warehouse_code <=> JSON_UNQUOTE(JSON_EXTRACT(f.calculation_json,'$.stock.warehouseCode')))))
       OR (f.id IS NULL AND (NOT (x.material_code <=> m.code)
        OR NOT (x.unit_code <=> u.measure_code) OR NOT (x.warehouse_code <=> w.code)));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A material, unit, or warehouse code snapshot has no exact retained destination';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (SELECT i.id
          FROM mes_material_issue i
          LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
          LEFT JOIN mes_material_issue_submission s ON s.issue_id=i.id
          GROUP BY i.id
          HAVING (COUNT(DISTINCT f.id)=0 AND COUNT(DISTINCT s.id)<>0)
              OR (COUNT(DISTINCT f.id)=1 AND COUNT(DISTINCT s.id)<>1)) orphan_submission_facts;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production issue has orphaned or duplicate submission facts';
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
    JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
       AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
    LEFT JOIN wm_stock_action reversal ON reversal.reference_action_id=a.id
       AND reversal.action_type='REVERSAL'
    WHERE a.id<>f.id OR (i.status='VOIDED' AND NOT (reversal.id <=> i.void_operation_id));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production-input or reversal request identity differs from its stock action';
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
    FROM (
        SELECT i.id
        FROM mes_material_issue i
        JOIN wm_stock_document d ON d.source_material_issue_id=i.id
           AND d.document_type='MATERIAL_ISSUE'
        JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
           AND a.action_type='ISSUE' AND a.source_id=CAST(i.id AS CHAR)
        LEFT JOIN wm_stock_document_line l ON l.document_id=d.id
        GROUP BY i.id,d.id,a.id
        HAVING COUNT(l.id)<>1
            OR COALESCE(SUM(CASE WHEN l.line_no=1 AND l.stock_action_id=a.id THEN 1 ELSE 0 END),0)<>1
    ) invalid_line_maps;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An existing issue document line identity cannot be mapped by line number';
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

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    LEFT JOIN mes_material_issue_input f ON f.issue_id=i.id
    WHERE i.issue_no<>d.document_no
      AND (f.id IS NULL OR i.issue_no<>CONCAT('INPUT-',f.id));
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A differing historical issue number is not recoverable from its retained request identity';
    END IF;

    ALTER TABLE wm_stock_document
        MODIFY create_by VARCHAR(64) NULL,
        MODIFY create_time DATETIME(6) NULL,
        MODIFY update_by VARCHAR(64) NULL,
        MODIFY update_time DATETIME(6) NULL;

    ALTER TABLE wm_stock_document_line
        MODIFY warehouse_id BIGINT NULL,
        MODIFY stock_id BIGINT NULL;

    INSERT INTO wm_stock_document(document_no,document_type,status,reason,remark,source_workorder_id,
        create_by,create_time,update_by,update_time,posted_by,posted_at,revision,source_execution_id,
        source_material_issue_id,source_request_json)
    SELECT i.issue_no,'MATERIAL_ISSUE',
        CASE WHEN i.status='DRAFT' THEN 'DRAFT' ELSE 'POSTED' END,
        '生产领料历史单据迁移',i.remark,i.workorder_id,
        i.create_by,i.create_time,i.update_by,i.update_time,NULL,NULL,0,i.execution_id,i.id,NULL
    FROM mes_material_issue i
    LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    WHERE d.id IS NULL;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    LEFT JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    WHERE d.id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Not every legacy issue has an explicit document identity mapping';
    END IF;

    INSERT INTO wm_stock_document_line(document_id,line_no,material_id,warehouse_id,stock_id,batch_no,bin_code,
        unit_id,quantity,remark,stock_action_id,revision)
    SELECT d.id,1,x.material_id,x.warehouse_id,f.stock_id,x.batch_no,a.bin_code,
        x.unit_id,x.quantity,x.remark,a.id,0
    FROM mes_material_issue_item x
    JOIN wm_stock_document d ON d.source_material_issue_id=x.issue_id
        AND d.document_type='MATERIAL_ISSUE'
    LEFT JOIN mes_material_issue_input f ON f.issue_id=x.issue_id
    LEFT JOIN wm_stock_action a ON a.source_type='MATERIAL_ISSUE'
        AND a.action_type='ISSUE' AND a.source_id=CAST(x.issue_id AS CHAR)
    LEFT JOIN wm_stock_document_line existing_line ON existing_line.document_id=d.id
        AND existing_line.line_no=1
    WHERE existing_line.id IS NULL;

    UPDATE wm_stock_action a
    JOIN mes_material_issue i ON a.source_id=CAST(i.id AS CHAR)
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
       AND d.document_type='MATERIAL_ISSUE'
    JOIN wm_stock_document_line l ON l.document_id=d.id AND l.line_no=1
    SET a.document_line_id=l.id
    WHERE a.source_type='MATERIAL_ISSUE'
      AND a.action_type='ISSUE'
      AND a.document_line_id IS NULL;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue i
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    LEFT JOIN wm_stock_document_line l ON l.document_id=d.id AND l.line_no=1
    WHERE l.id IS NULL OR l.material_id<>(
        SELECT x.material_id FROM mes_material_issue_item x WHERE x.issue_id=i.id)
       OR l.unit_id<>(SELECT x.unit_id FROM mes_material_issue_item x WHERE x.issue_id=i.id)
       OR l.quantity<>(SELECT x.quantity FROM mes_material_issue_item x WHERE x.issue_id=i.id)
       OR NOT (l.warehouse_id <=> (SELECT x.warehouse_id FROM mes_material_issue_item x WHERE x.issue_id=i.id))
       OR NOT (l.batch_no <=> (SELECT x.batch_no FROM mes_material_issue_item x WHERE x.issue_id=i.id));
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unified line conversion did not preserve source material facts';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_action a
    JOIN mes_material_issue i ON a.source_id=CAST(i.id AS CHAR)
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
       AND d.document_type='MATERIAL_ISSUE'
    JOIN wm_stock_document_line l ON l.document_id=d.id AND l.line_no=1
    WHERE a.source_type='MATERIAL_ISSUE' AND a.action_type='ISSUE'
      AND (a.document_line_id IS NULL OR a.document_line_id<>l.id);
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A historical inventory action is not linked to its document line';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM mes_material_issue_input f
    JOIN mes_material_issue i ON i.id=f.issue_id
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
       AND d.document_type='MATERIAL_ISSUE'
    JOIN mes_material_issue_submission s ON s.id=i.submission_id AND s.issue_id=i.id
    WHERE i.execution_id<>f.execution_id OR s.status<>'SUCCEEDED'
       OR s.id<>i.submission_id;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production input conversion preconditions changed during document creation';
    END IF;

    ALTER TABLE mes_material_issue_input
        ADD COLUMN document_id BIGINT NULL COMMENT 'Unified stock-document identity',
        ADD COLUMN submission_id BIGINT NULL COMMENT 'Original production-input submission fact',
        ADD COLUMN path_revision INT NULL COMMENT 'Frozen execution-path revision',
        ADD COLUMN business_occurred_at DATETIME(3) NULL COMMENT 'Original production-issue business time; distinct from posting and reversal audit time',
        ADD COLUMN void_operation_id VARCHAR(64) NULL COMMENT 'Original LOCAL reversal operation identity',
        ADD COLUMN void_from_status VARCHAR(20) NULL COMMENT 'Original production lifecycle state before reversal',
        ADD COLUMN void_requested_at DATETIME NULL,
        ADD COLUMN void_finished_at DATETIME NULL,
        ADD COLUMN void_result_status VARCHAR(20) NULL COMMENT 'LOCAL reversal result only';

    ALTER TABLE mes_material_issue_submission
        ADD COLUMN document_id BIGINT NULL COMMENT 'Unified stock-document identity';

    UPDATE mes_material_issue_input f
    JOIN mes_material_issue i ON i.id=f.issue_id
    JOIN wm_stock_document d ON d.source_material_issue_id=i.id
        AND d.document_type='MATERIAL_ISSUE'
    JOIN mes_material_issue_submission s ON s.id=i.submission_id AND s.issue_id=i.id
    SET f.document_id=d.id,f.submission_id=s.id,f.path_revision=s.path_revision,
        f.business_occurred_at=i.issue_time,
        f.void_operation_id=i.void_operation_id,f.void_from_status=i.void_from_status,
        f.void_requested_at=i.void_requested_at,f.void_finished_at=i.void_finished_at,
        f.void_result_status=i.void_result_status;

    UPDATE mes_material_issue_submission s
    JOIN wm_stock_document d ON d.source_material_issue_id=s.issue_id
        AND d.document_type='MATERIAL_ISSUE'
    SET s.document_id=d.id;

    SELECT COUNT(*) INTO bad_rows FROM mes_material_issue_input WHERE document_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A production-input fact was not mapped to a stock document';
    END IF;
    SELECT COUNT(*) INTO bad_rows FROM mes_material_issue_submission WHERE document_id IS NULL;
    IF bad_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A submission fact was not mapped to a stock document';
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
END$$
CALL mes_v70_converge_material_issue()$$
DROP PROCEDURE mes_v70_converge_material_issue$$
DELIMITER ;
