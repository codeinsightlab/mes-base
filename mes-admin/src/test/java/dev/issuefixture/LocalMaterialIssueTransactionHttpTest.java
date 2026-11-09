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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: isolated H2 checks actual LOCAL commands and transaction rollback.
 */
class LocalMaterialIssueTransactionHttpTest {
    @Test
    void materialReturnsUseProductionEntryAndLaterReversalOffsetsOnlyTheUnreturnedQuantity() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String documentId = result.get("documentId").asText();
            var detail = new ObjectMapper().readTree(mvc.perform(full(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    StockDocumentHttpTest.OUT_ROOT + "/" + documentId))).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString());
            String lineId = detail.get("lines").get(0).get("id").asText();
            String returnCommand = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"stockId\":\"1\","
                    + "\"quantity\":\"2\",\"expectedQuantity\":\"9980\",\"expectedAvailable\":\"9980\","
                    + "\"reason\":\"TEST ONLY return before production\",\"documentId\":\"" + documentId
                    + "\",\"documentLineId\":\"" + lineId + "\"}";
            mvc.perform(full(post("/api/wm/local-stock/return")).content(returnCommand))
                    .andExpect(status().isNotFound());
            var returned = new ObjectMapper().readTree(mvc.perform(full(post(ProductionInputHttpTest.ROOT
                    + "/documents/" + documentId + "/returns")).content(returnCommand)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals("RETURN", returned.get("actionType").asText());
            assertEquals(detail.get("lines").get(0).get("stockActionId").asText(), returned.get("referenceActionId").asText());
            assertEquals(0, new BigDecimal("9982").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));

            String reverseCommand = StockDocumentHttpTest.cancellationBody(mvc, documentId);
            mvc.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + documentId + "/actions/cancel"))
                            .content(reverseCommand))
                    .andExpect(status().isForbidden());
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(reverseCommand))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("REVERSED"));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='RETURN'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?",
                    Boolean.class, documentId));
        }
    }

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
            String requestBody = StockDocumentHttpTest.cancellationBody(mvc, document);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(requestBody))
                    .andExpect(status().isOk());
            String operation = db.queryForObject("SELECT r.id FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, issue);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(requestBody))
                    .andExpect(status().isOk());
            assertEquals("REVERSED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, issue));
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
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                    .toList();
            db.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_reversal CHECK(action_type<>'REVERSAL')");
            String document = result.get("documentId").asText();
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isInternalServerError());
            assertEquals(before, tables.stream()
                    .map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void aDifferentCancellationRequestCannotRepeatAnExistingReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = result.get("documentId").asText();
            String firstRequest = StockDocumentHttpTest.cancellationBody(mvc, document);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(firstRequest)).andExpect(status().isOk());
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                    .toList();
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isConflict());
            assertEquals(before, tables.stream()
                    .map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
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
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isConflict());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
