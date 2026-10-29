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
            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, issue));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk());
            String operation = db.queryForObject("SELECT r.id FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, issue);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk());
            assertEquals(operation, db.queryForObject("SELECT r.id FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, issue));
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
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
            db.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_reversal CHECK(action_type<>'REVERSAL')");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + result.get("documentId").asText() + "/actions/void")))
                    .andExpect(status().isInternalServerError());
            assertEquals(before, tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void reconciliationWithoutAnOriginalReverseFactCannotCreateStock() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = result.get("documentId").asText();
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/reconcile-void")))
                    .andExpect(status().isConflict());
            assertEquals(before, tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void missingStockEffectDoesNotPretendAnInputWasSuccessfullyReversed() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = result.get("documentId").asText();
            db.update("UPDATE mes_material_issue_input SET stock_applied=FALSE WHERE document_id=?", document);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/reconcile-void")))
                    .andExpect(status().isConflict());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
