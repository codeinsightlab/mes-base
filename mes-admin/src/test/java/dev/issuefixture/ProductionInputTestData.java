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
            jdbc.update("UPDATE mes_material_issue_input SET output_qty=? WHERE document_id=?", qty, document);
            return;
        }
        jdbc.update("INSERT INTO wm_stock_document(id,document_no,document_type,status,reason,source_workorder_id,source_execution_id,create_by,create_time,update_by,update_time,revision) SELECT ?,?,'MATERIAL_ISSUE','POSTED','TEST ONLY frozen input',workorder_id,id,'1',CURRENT_TIMESTAMP,'1',CURRENT_TIMESTAMP,0 FROM mes_production_execution WHERE id=?", document, "TEST_INPUT_" + execution, execution);
        jdbc.update("INSERT INTO mes_material_issue_submission(id,document_id,path_revision,command_json,status,created_at,finished_at) VALUES(?,?,1,'{}','SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", document, document);
        jdbc.update("INSERT INTO mes_material_issue_input(id,request_hash,document_id,execution_id,submission_id,input_kind,output_qty,source_mode,calculation_json,confirmed_at,stock_applied,created_at) VALUES(?,?,?,?,?, 'PRODUCTION_INPUT',?,'LOCAL_ONLY','{}',CURRENT_TIMESTAMP,FALSE,CURRENT_TIMESTAMP)", UUID.randomUUID()
                .toString(), "0".repeat(64), document, execution, document, qty);
    }
}
