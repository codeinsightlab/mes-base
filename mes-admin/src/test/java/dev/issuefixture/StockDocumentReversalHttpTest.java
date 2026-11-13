package dev.issuefixture;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated H2 coverage for the unified document lifecycle and append-only stock actions.
 */
class StockDocumentReversalHttpTest {
    @Test
    void receiptAndIssueDocumentsReverseAcrossMultipleBatchesWithTraceableMovements() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            for (String type : List.of("OTHER_RECEIPT", "OTHER_ISSUE")) {
                boolean inbound = "OTHER_RECEIPT".equals(type);
                String root = inbound ? StockDocumentHttpTest.ROOT : StockDocumentHttpTest.OUT_ROOT;
                JsonNode created = StockDocumentHttpTest.create(mvc, type, null);
                String id = created.get("document").get("id").asText();
                if (inbound) {
                    StockDocumentHttpTest.add(mvc, id, "0", "1", "L001", "5");
                    StockDocumentHttpTest.add(mvc, id, "1", "2", "L002", "8");
                } else {
                    StockDocumentHttpTest.addOut(mvc, id, "0", "1", "L001", "5");
                    StockDocumentHttpTest.addOut(mvc, id, "1", "2", "L002", "8");
                }
                mvc.perform(full(post(root + "/" + id + "/actions/approve"))
                                .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 2)))
                        .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("POSTED"));

                var postedBalance1 = db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class);
                var postedBalance2 = db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class);
                String requestId = UUID.randomUUID().toString();
                String command = StockDocumentHttpTest.cancellationBody(mvc, id, inbound ? "IN" : "OUT", requestId,
                        "TEST ONLY correct an incorrectly posted stock document");
                var result = StockDocumentHttpTest.JSON.readTree(mvc.perform(full(post(root + "/" + id + "/actions/cancel"))
                                .content(command))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("document.status").value("REVERSED"))
                        .andExpect(jsonPath("document.reversalRequestId").value(requestId))
                        .andExpect(jsonPath("document.reversalReason").value("TEST ONLY correct an incorrectly posted stock document"))
                        .andReturn().getResponse().getContentAsString());
                assertEquals(0, new BigDecimal("5").compareTo(new BigDecimal(result.get("lines").get(0).get("quantity")
                        .asText())));
                assertEquals(0, new BigDecimal("8").compareTo(new BigDecimal(result.get("lines").get(1).get("quantity")
                        .asText())));
                assertEquals(4, result.get("stockMovements").size());
                assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE source_id=? AND action_type='REVERSAL'", Integer.class, id));
                assertEquals(1, db.queryForObject("SELECT COUNT(DISTINCT operation_id) FROM wm_stock_action WHERE source_id=? AND action_type='REVERSAL'", Integer.class, id));
                assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id AND r.document_line_id=l.id WHERE l.document_id=? AND r.action_type='REVERSAL'", Integer.class, id));
                assertEquals(0, BigDecimal.ZERO.compareTo(postedBalance1.add(db.queryForObject("SELECT SUM(quantity_delta) FROM wm_stock_action WHERE source_id=? AND stock_id=1 AND action_type='REVERSAL'", BigDecimal.class, id))
                        .subtract(new BigDecimal("10000"))));
                assertEquals(0, BigDecimal.ZERO.compareTo(postedBalance2.add(db.queryForObject("SELECT SUM(quantity_delta) FROM wm_stock_action WHERE source_id=? AND stock_id=2 AND action_type='REVERSAL'", BigDecimal.class, id))
                        .subtract(new BigDecimal("10000"))));
                assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
                assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class)));
                assertReversalFields(db, id, requestId,
                        "TEST ONLY correct an incorrectly posted stock document", inbound,
                        postedBalance1, postedBalance2);

                mvc.perform(full(post(root + "/" + id + "/actions/cancel")).content(command))
                        .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("REVERSED"));
                mvc.perform(full(post(root + "/" + id + "/actions/cancel"))
                                .content(StockDocumentHttpTest.cancellationBody(mvc, id, inbound ? "IN" : "OUT",
                                        UUID.randomUUID().toString(), "TEST ONLY different request")))
                        .andExpect(status().isConflict());
                mvc.perform(full(post(root + "/" + id + "/actions/approve"))
                                .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(),
                                        result.get("document").get("revision").asInt())))
                        .andExpect(status().isConflict());
                mvc.perform(full(get(root).param("status", "REVERSED")))
                        .andExpect(status().isOk()).andExpect(jsonPath("items[0].status").value("REVERSED"));
            }
        }
    }

    private static void assertReversalFields(JdbcTemplate db, String documentId, String requestId, String reason,
                                             boolean inbound, BigDecimal postedBalance1,
                                             BigDecimal postedBalance2) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT r.id, r.request_hash, r.document_line_id, r.operation_id, r.stock_id, r.material_id,
                       r.unit_id, r.warehouse_id, r.batch_no, r.bin_code, r.action_type, r.quantity_delta,
                       r.available_delta, r.quantity_before, r.quantity_after, r.available_before,
                       r.available_after, r.source_type, r.source_id, r.reference_action_id, r.execution_code,
                       r.operator_user_id, r.occurred_at, r.recorded_at, r.reason,
                       o.id AS original_id, o.action_type AS original_action_type,
                       o.source_type AS original_source_type, o.source_id AS original_source_id,
                       o.document_line_id AS original_line_id, o.quantity_delta AS original_delta,
                       o.execution_code AS original_execution_code, l.id AS line_id
                FROM wm_stock_action r
                JOIN wm_stock_action o ON o.id = r.reference_action_id
                JOIN wm_stock_document_line l ON l.id = r.document_line_id
                WHERE r.source_id = ? AND r.action_type = 'REVERSAL'
                ORDER BY r.stock_id
                """, Long.valueOf(documentId));
        assertEquals(2, rows.size());
        for (var row : rows) {
            long stockId = ((Number) row.get("stock_id")).longValue();
            BigDecimal postedBalance = stockId == 1L ? postedBalance1 : postedBalance2;
            BigDecimal delta = (BigDecimal) row.get("quantity_delta");
            String expectedBatch = stockId == 1L ? "L001" : "L002";

            assertNotNull(row.get("id"));
            UUID.fromString((String) row.get("id"));
            assertNotNull(row.get("request_hash"));
            assertTrue(((String) row.get("request_hash")).matches("[0-9a-f]{64}"));
            assertEquals(row.get("original_line_id"), row.get("document_line_id"));
            assertEquals(row.get("line_id"), row.get("document_line_id"));
            assertEquals(requestId, row.get("operation_id"));
            assertTrue(stockId == 1L || stockId == 2L);
            assertEquals(1L, ((Number) row.get("material_id")).longValue());
            assertEquals(1L, ((Number) row.get("unit_id")).longValue());
            assertEquals(1L, ((Number) row.get("warehouse_id")).longValue());
            assertEquals(expectedBatch, row.get("batch_no"));
            assertEquals("BIN1", row.get("bin_code"));
            assertEquals("REVERSAL", row.get("action_type"));
            assertEquals(0, delta.compareTo(((BigDecimal) row.get("original_delta")).negate()));
            assertEquals(0, delta.compareTo((BigDecimal) row.get("available_delta")));
            assertEquals(0, postedBalance.compareTo((BigDecimal) row.get("quantity_before")));
            assertEquals(0, new BigDecimal("10000").compareTo((BigDecimal) row.get("quantity_after")));
            assertEquals(0, postedBalance.compareTo((BigDecimal) row.get("available_before")));
            assertEquals(0, new BigDecimal("10000").compareTo((BigDecimal) row.get("available_after")));
            assertEquals(inbound ? "OTHER_RECEIPT" : "OTHER_ISSUE", row.get("original_action_type"));
            assertEquals(inbound ? "OTHER_RECEIPT" : "OTHER_ISSUE", row.get("source_type"));
            assertEquals(row.get("original_source_type"), row.get("source_type"));
            assertEquals(documentId, row.get("source_id"));
            assertEquals(row.get("original_source_id"), row.get("source_id"));
            assertEquals(row.get("original_id"), row.get("reference_action_id"));
            assertNull(row.get("execution_code"));
            assertEquals(row.get("original_execution_code"), row.get("execution_code"));
            assertNotNull(row.get("operator_user_id"));
            assertEquals(row.get("operator_user_id").toString(), db.queryForObject(
                    "SELECT update_by FROM wm_inventory WHERE id=?", String.class, stockId));
            assertEquals(row.get("occurred_at"), row.get("recorded_at"));
            Timestamp recordedAt = (Timestamp) row.get("recorded_at");
            Timestamp inventoryUpdatedAt = db.queryForObject(
                    "SELECT update_time FROM wm_inventory WHERE id=?", Timestamp.class, stockId);
            assertNotNull(recordedAt);
            assertNotNull(inventoryUpdatedAt);
            assertTrue(Duration.between(recordedAt.toLocalDateTime(), inventoryUpdatedAt.toLocalDateTime())
                    .abs().compareTo(Duration.ofSeconds(1)) <= 0);
            assertEquals(reason, row.get("reason"));
        }
    }

    @Test
    void receiptReversalRestoresMultipleMaterialsAndBatches() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            ProductionInputHttpTest.multiMaterial(db);
            String id = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null)
                    .get("document").get("id").asText();
            StockDocumentHttpTest.add(mvc, id, "0", "1", "B01", "5");
            String secondMaterialLine = StockDocumentHttpTest.line("1", "3", "C01", "7")
                    .replace("\"materialId\":\"1\"", "\"materialId\":\"2\"")
                    .replace("\"binCode\":\"BIN1\"", "\"binCode\":\"BIN2\"");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines")).content(secondMaterialLine))
                    .andExpect(status().isOk());
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 2)))
                    .andExpect(status().isOk());

            String command = StockDocumentHttpTest.cancellationBody(mvc, id, "IN", UUID.randomUUID().toString(),
                    "TEST ONLY multi-material reversal");
            var result = StockDocumentHttpTest.JSON.readTree(mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/cancel"))
                            .content(command))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("document.status").value("REVERSED"))
                    .andReturn().getResponse().getContentAsString());

            assertEquals(4, result.get("stockMovements").size());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE source_id=? AND action_type='REVERSAL'", Integer.class, id));
            assertEquals(0, new BigDecimal("120").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(0, new BigDecimal("100").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=3", BigDecimal.class)));
            assertEquals(0, new BigDecimal("5").compareTo(db.queryForObject("SELECT quantity FROM wm_stock_document_line WHERE document_id=? AND material_id=1", BigDecimal.class, Long.valueOf(id))));
            assertEquals(0, new BigDecimal("7").compareTo(db.queryForObject("SELECT quantity FROM wm_stock_document_line WHERE document_id=? AND material_id=2", BigDecimal.class, Long.valueOf(id))));
        }
    }

    @Test
    void draftCancellationRemainsSeparateFromPostedReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String id = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null).get("document").get("id").asText();
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/cancel"))
                            .content("{\"expectedRevision\":\"0\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("CANCELLED"));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

    @Test
    void receiptReversalIsRejectedWhenLaterStockUseWouldMakeBalanceNegative() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String receipt = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null).get("document").get("id")
                    .asText();
            StockDocumentHttpTest.add(mvc, receipt, "0", "1", "L001", "5");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + receipt + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isOk());
            String issue = StockDocumentHttpTest.create(mvc, "OTHER_ISSUE", null).get("document").get("id").asText();
            StockDocumentHttpTest.addOut(mvc, issue, "0", "1", "L001", "10002");
            mvc.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + issue + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isOk());
            assertEquals(0, new BigDecimal("3").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + receipt + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, receipt, "IN", UUID.randomUUID()
                                    .toString(), "TEST ONLY negative balance guard")))
                    .andExpect(status().isConflict());
            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, receipt));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("3").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
