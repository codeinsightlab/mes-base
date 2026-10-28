package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TEST ONLY historical confirmed-input fixtures; never a live ERP success or production backfill.
 */
final class ProductionInputTestData {
    private ProductionInputTestData() {
    }

    static void confirmed(JdbcTemplate jdbc, long execution, BigDecimal qty) {
        long document = -execution;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE id=?", Integer.class, document) > 0) {
            jdbc.update("UPDATE wm_stock_document SET production_output_qty=? WHERE id=?", qty, document);
            return;
        }
        jdbc.update("INSERT INTO wm_stock_document(id,document_no,document_type,status,reason,source_workorder_id,source_execution_id,create_by,create_time,update_by,update_time,revision) SELECT ?,?,'MATERIAL_ISSUE','POSTED','TEST ONLY frozen input',workorder_id,id,'1',CURRENT_TIMESTAMP,'1',CURRENT_TIMESTAMP,0 FROM mes_production_execution WHERE id=?", document, "TEST_INPUT_" + execution, execution);
        jdbc.update("UPDATE wm_stock_document SET production_request_id=?,production_request_hash=?,production_path_revision=1,production_confirmation_operation_id=?,production_confirmation_status='SUCCEEDED',production_submission_created_at=CURRENT_TIMESTAMP,production_submission_finished_at=CURRENT_TIMESTAMP,production_input_kind='PRODUCTION_INPUT',production_output_qty=?,production_source_mode='LOCAL_ONLY',production_calculation_json='{}',production_confirmed_at=CURRENT_TIMESTAMP,production_stock_applied=FALSE,production_request_created_at=CURRENT_TIMESTAMP WHERE id=?", UUID.randomUUID().toString(), "0".repeat(64), "TEST_INPUT_" + execution, qty, document);
    }
}
