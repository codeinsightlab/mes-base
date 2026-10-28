package dev.issuefixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TEST ONLY: isolated H2 checks actual LOCAL commands and transaction rollback. */
class LocalMaterialIssueTransactionHttpTest {
    @Test
    void localApprovalAndCancellationUseRealStockFactsWithoutWarehouseCommands() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String command = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10");
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc, command));
            String issue = result.get("documentId").asText();
            String document = result.get("documentId").asText();
            assertEquals("SUCCEEDED", db.queryForObject("SELECT status FROM mes_material_issue_submission WHERE document_id=?", String.class, issue));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk());
            String operation = db.queryForObject("SELECT void_operation_id FROM mes_material_issue_input WHERE document_id=?", String.class, issue);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk());
            assertEquals(operation, db.queryForObject("SELECT void_operation_id FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals("SUCCEEDED", db.queryForObject("SELECT void_result_status FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, issue));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void reversalFailureRollsBackCancellationIdentityInputAndStockTogether() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "mes_material_issue_submission", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
            db.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_reversal CHECK(action_type<>'REVERSAL')");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + result.get("documentId").asText() + "/actions/void")))
                    .andExpect(status().isInternalServerError());
            assertEquals(before, tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void pendingLocalCancellationContinuesTheOriginalOperationWithOneRealReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String issue = result.get("documentId").asText();
            String operation = UUID.randomUUID().toString();
            db.update("UPDATE mes_material_issue_input SET void_operation_id=?,void_from_status='CONFIRMED',void_requested_at=CURRENT_TIMESTAMP WHERE document_id=?", operation, issue);
            db.update("UPDATE mes_material_issue_input SET void_result_status='UNKNOWN' WHERE document_id=?", issue);
            String requestedAt = db.queryForObject("SELECT void_requested_at FROM mes_material_issue_input WHERE document_id=?", String.class, issue);
            String path = StockDocumentHttpTest.ROOT + "/" + result.get("documentId").asText() + "/actions/reconcile-void";
            mvc.perform(full(post(path))).andExpect(status().isOk());
            mvc.perform(full(post(path))).andExpect(status().isOk());
            assertEquals(operation, db.queryForObject("SELECT void_operation_id FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals(requestedAt, db.queryForObject("SELECT void_requested_at FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals("SUCCEEDED", db.queryForObject("SELECT void_result_status FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE id=? AND action_type='REVERSAL'", Integer.class, operation));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

    @Test
    void unprovenPendingCancellationDoesNotCreateAResultOrStockFact() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String issue = result.get("documentId").asText();
            String operation = UUID.randomUUID().toString();
            db.update("UPDATE mes_material_issue_input SET void_operation_id=?,void_from_status='CONFIRMED',void_requested_at=CURRENT_TIMESTAMP WHERE document_id=?", operation, issue);
            db.update("UPDATE mes_material_issue_input SET void_result_status='UNKNOWN' WHERE document_id=?", issue);
            db.update("UPDATE mes_material_issue_input SET stock_applied=false WHERE document_id=?", issue);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + result.get("documentId").asText() + "/actions/reconcile-void")))
                    .andExpect(status().isConflict());
            assertEquals("UNKNOWN", db.queryForObject("SELECT void_result_status FROM mes_material_issue_input WHERE document_id=?", String.class, issue));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, issue));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE id=?", Integer.class, operation));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

}
