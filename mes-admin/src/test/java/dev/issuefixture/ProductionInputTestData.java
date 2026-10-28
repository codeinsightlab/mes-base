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
        long issue = -execution;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM mes_material_issue WHERE id=?", Integer.class, issue) > 0) {
            jdbc.update("UPDATE mes_material_issue_input SET output_qty=? WHERE issue_id=?", qty, issue);
            return;
        }
        jdbc.update("INSERT INTO mes_material_issue(id,issue_no,workorder_id,product_id,routing_id,execution_id,status,submission_id,remark,source_mode) SELECT ?,?,workorder_id,product_id,routing_id,id,'CONFIRMED',?,'TEST ONLY frozen historical input fixture','LOCAL_ONLY' FROM mes_production_execution WHERE id=?", issue, "TEST_INPUT_" + execution, issue, execution);
        jdbc.update("INSERT INTO mes_material_issue_submission(id,issue_id,path_revision,command_json,status,created_at,finished_at) VALUES(?,?,1,'{}','SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", issue, issue);
        jdbc.update("INSERT INTO mes_material_issue_input(id,request_hash,issue_id,execution_id,input_kind,output_qty,source_mode,calculation_json,confirmed_at,stock_applied,created_at) VALUES(?,?,?,?, 'PRODUCTION_INPUT',?,'LOCAL_ONLY','{}',CURRENT_TIMESTAMP,FALSE,CURRENT_TIMESTAMP)", UUID.randomUUID()
                .toString(), "0".repeat(64), issue, execution, qty);
    }
}
