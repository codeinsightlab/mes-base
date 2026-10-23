package dev.issuefixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.business.quality.controller.QualityDispositionController;
import dev.mesbase.business.quality.service.QualityDispositionService;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static dev.issuefixture.QualityWorkflowHttpTest.JSON;
import static dev.issuefixture.QualityWorkflowHttpTest.Q;
import static dev.issuefixture.QualityWorkflowHttpTest.amount;
import static dev.issuefixture.QualityWorkflowHttpTest.context;
import static dev.issuefixture.QualityWorkflowHttpTest.defect;
import static dev.issuefixture.QualityWorkflowHttpTest.quantities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY routed H2 with real transactions, ledger and stock owners. No ERP calls.
 */
class QualityDispositionHttpTest {
    @Configuration
    static class Validation {
        @Bean
        Validator validator() {
            return new LocalValidatorFactoryBean();
        }
    }

    static final Class<?>[] EXTRA = {Validation.class, QualityDispositionService.class, QualityDispositionController.class, WmInventoryServiceImpl.class};

    static String command(String id, String destination, String qty, String warehouse) {
        return "{\"requestId\":\"" + id + "\",\"expectedRevision\":1,\"destination\":\"" + destination + "\",\"quantity\":\"" + qty + "\",\"warehouseId\":" + warehouse + ",\"reason\":\"TEST ONLY disposition\"}";
    }

    static void bindBatch(JdbcTemplate jdbc, String batchNo) {
        jdbc.update("UPDATE mes_production_execution SET batch_no=? WHERE id=1", batchNo);
    }

    static JsonNode dispose(MockMvc m, String declaration, String body) throws Exception {
        return JSON.readTree(m.perform(full(post(Q + "/" + declaration + "/dispositions"), "A").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /**
     * TEST ONLY immutable historical return, never a current workflow write.
     */
    static void historicalReturn(JdbcTemplate jdbc, String declaration, String key, String quantity) throws Exception {
        String value = String.join("\n", "DISPOSITION", declaration, "1", "1", "RETURN_NODE",
                new BigDecimal(quantity).stripTrailingZeros().toPlainString(), "", "TEST ONLY disposition");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,operator_user_id,recorded_at,reason,returned_qty,transfer_qty) SELECT ?,?,declaration_id,revision_no,execution_id,affected_node_id,'RETURN_NODE',1,CURRENT_TIMESTAMP,'TEST ONLY disposition',?,0 FROM mes_production_defect WHERE declaration_id=? AND revision_no=1",
                key, hash, new BigDecimal(quantity), declaration);
        jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,source_quality_event_id,operator_user_id,recorded_by,report_time,reason,create_time,request_id,command_fingerprint) SELECT r.execution_id,r.routing_node_id,'ADJUSTMENT',?,r.id,?,r.operator_user_id,1,r.report_time,'TEST ONLY historical return',CURRENT_TIMESTAMP,?,? FROM mes_production_report r JOIN mes_production_defect d ON d.execution_id=r.execution_id AND d.affected_node_id=r.routing_node_id WHERE d.declaration_id=? AND r.record_type='REPORT' ORDER BY r.id DESC LIMIT 1",
                new BigDecimal(quantity).negate(), key, UUID.randomUUID().toString(), "x".repeat(64), declaration);
    }

    @Test
    void rejectsNewReturnButReplaysHistoricalReturnWithoutChangingItsQuantities() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var key = UUID.randomUUID().toString();
            String body = command(key, "RETURN_NODE", "10", "null");
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A").content(body))
                    .andExpect(status().isConflict());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE source_quality_event_id IS NOT NULL", Integer.class));
            historicalReturn(j, id, key, "10");
            var result = dispose(m, id, body);
            assertEquals("PENDING", result.path("receiptState").asText());
            amount(result, "returnedQty", "10");
            amount(result, "pendingDispositionQty", "0");
            assertEquals("RETURNED", result.get("state").asText());
            assertEquals(0, new BigDecimal("90").compareTo(j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=1 AND routing_node_id=12", BigDecimal.class)));
            var q = quantities(m);
            amount(q.get("nodes").get(1), "effectiveOutputQty", "90");
            dispose(m, id, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE source_quality_event_id=?", Integer.class, key));
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            m.perform(full(post(ProductionReportingHttpTest.ROOT), "A").content(ProductionReportingHttpTest.body(12, "10")))
                    .andExpect(status().isOk());
            amount(quantities(m).get("nodes").get(1), "effectiveOutputQty", "100");
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A").content(command(key, "RETURN_NODE", "9", "null")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void allDefectsUseTheirPostedReceiptWithoutAZeroNormalReceiptAndRequireExactProof() throws Exception {
        try (var c = context(EXTRA, 100, 100, 100, 100, 100)) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var jdbc = ProductionWarehouseFixture.db(c);
            var declaration = defect(mvc, 15, "100", "INDEPENDENT", null);
            bindBatch(jdbc, "000ALLBAD-001");
            var body = command(UUID.randomUUID().toString(), "WAREHOUSE", "100", "1");
            var first = dispose(mvc, declaration, body);
            assertEquals("RECEIVED", first.path("receiptState").asText());
            dispose(mvc, declaration, body);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE source_execution_id IS NOT NULL", Integer.class));
            FactoryContext.runWithScope(new FactoryScope(new FactoryId("A")), () -> {
                var owner = c.getBean(QualityTreatmentService.class);
                assertNull(owner.receiptBlockReason(1L));
                var operation = jdbc.queryForObject("SELECT operation_id FROM wm_stock_action WHERE document_line_id IS NOT NULL", String.class);
                jdbc.update("UPDATE wm_stock_action SET operation_id=? WHERE document_line_id IS NOT NULL", UUID.randomUUID()
                        .toString());
                assertNotNull(owner.receiptBlockReason(1L));
                jdbc.update("UPDATE wm_stock_action SET operation_id=? WHERE document_line_id IS NOT NULL", operation);
                assertNull(owner.receiptBlockReason(1L));
                jdbc.update("UPDATE wm_stock_action SET execution_id=2 WHERE document_line_id IS NOT NULL");
                assertNotNull(owner.receiptBlockReason(1L));
                jdbc.update("UPDATE wm_stock_action SET execution_id=1 WHERE document_line_id IS NOT NULL");
                jdbc.update("UPDATE wm_stock_action SET source_id='unrelated-document' WHERE document_line_id IS NOT NULL");
                assertNotNull(owner.receiptBlockReason(1L));
            });
        }
    }

    @Test
    void transferPostsExactlyOneInboundAndNeverCallsItScrap() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            bindBatch(j, "000LOT-001");
            String body = command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1");
            var result = dispose(m, id, body);
            amount(result, "transferredQty", "10");
            assertEquals(false, result.has("allocatedScrapQty"));
            var e = result.get("events").get(result.get("events").size() - 1);
            String doc = e.get("stockDocumentId").asText();
            assertEquals("POSTED", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, doc));
            assertEquals("DEFECT_RECEIPT", j.queryForObject("SELECT document_type FROM wm_stock_document WHERE id=?", String.class, doc));
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE batch_no='000LOT-001' ", BigDecimal.class)));
            assertEquals("000LOT-001", j.queryForObject("SELECT batch_no FROM wm_stock_document_line WHERE document_id=?", String.class, doc));
            assertEquals("000LOT-001", JSON.readTree(m.perform(full(get(Q), "A"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .get("items").get(0).get("batchNo").asText());
            // Replaying the original operation reads its immutable result before new-batch admission.
            j.update("UPDATE mes_production_execution SET batch_no=NULL WHERE id=1");
            dispose(m, id, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE source_execution_id IS NOT NULL", Integer.class));
        }
    }

    @Test
    void overQuantityAndInvalidWarehouseRollbackAllFacts() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            for (String body : new String[]{command(UUID.randomUUID()
                    .toString(), "RETURN_NODE", "11", "null"), command(UUID.randomUUID()
                    .toString(), "WAREHOUSE", "10", "999")})
                m.perform(full(post(Q + "/" + id + "/dispositions"), "A").content(body))
                        .andExpect(status().is4xxClientError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type IN ('RETURN_NODE','TRANSFER_WAREHOUSE')", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
        }
    }

    @Test
    void inboundPostingFailureRollsBackDraftInventoryAndDisposition() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            bindBatch(j, "000LOT-001");
            j.update("UPDATE unit_measure SET status='DISABLED' WHERE id=1");
            var response = m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                    .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1"))).andReturn().getResponse();
            assertEquals(409, response.getStatus());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE batch_no='000LOT-001' ", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='TRANSFER_WAREHOUSE'", Integer.class));
        }
    }

    @Test
    void warehouseRequiresStockPermissionsAndSameSource() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            m.perform(post(Q + "/" + id + "/dispositions").header("Authorization", "Bearer REVISE")
                            .header("X-Factory-Id", "A").contentType("application/json")
                            .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                    .andExpect(status().isForbidden());
            j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                            .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                    .andExpect(status().isConflict());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
        }
    }

    @Test
    void competingDispositionsCannotSpendTheSamePendingQuantity() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90);
             var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            bindBatch(j, "000RACE-001");
            var barrier = new CyclicBarrier(2);
            Callable<Integer> request = () -> {
                barrier.await();
                return m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                                .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                        .andReturn().getResponse().getStatus();
            };
            var a = pool.submit(request);
            var b = pool.submit(request);
            assertEquals(Set.of(200, 409), Set.of(a.get(), b.get()));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='TRANSFER_WAREHOUSE'", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE status='POSTED'", Integer.class));
        }
    }

    @Test
    void partialReceiptsRemainPendingUntilTheWholeQuantityIsReceived() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var id = defect(mvc, 12, "10", "INDEPENDENT", null);
            bindBatch(ProductionWarehouseFixture.db(c), "000PARTIAL-001");
            var partial = dispose(mvc, id, command(UUID.randomUUID().toString(), "WAREHOUSE", "6", "1"));
            assertEquals("PARTIAL", partial.path("receiptState").asText());
            amount(partial, "pendingDispositionQty", "4");
            assertEquals("PENDING", partial.get("state").asText());
            var complete = dispose(mvc, id, command(UUID.randomUUID().toString(), "WAREHOUSE", "4", "1"));
            assertEquals("TRANSFERRED", complete.get("state").asText());
            assertEquals("RECEIVED", complete.path("receiptState").asText());
            assertEquals(false, complete.has("pendingStartQty"));
        }
    }

    @Test
    void listProjectsTrustedContextAndProcessingStatesForTheCurrentFactory() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var jdbc = ProductionWarehouseFixture.db(c);
            jdbc.update("UPDATE material SET specification='Φ20×30' WHERE id=1");
            bindBatch(jdbc, "000LOT-001");
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            historicalReturn(jdbc, id, UUID.randomUUID().toString(), "10");
            var capture = new RecentProductionReportHttpTest.SqlCapture();
            c.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(capture);
            var list = JSON.readTree(m.perform(full(get(Q), "A")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            var row = list.get("items").get(0);
            assertEquals(id, row.get("declarationId").asText());
            assertEquals("WO1", row.get("workorderCode").asText());
            assertEquals("1", row.get("executionId").asText());
            assertEquals("M1", row.get("productCode").asText());
            assertEquals("000LOT-001", row.get("batchNo").asText());
            assertEquals("Φ20×30", row.get("specification").asText());
            assertEquals(1, capture.statements.stream().filter(sql -> sql.contains("count(")).count());
            assertTrue(capture.statements.stream().filter(sql -> sql.contains("from mes_quality_treatment"))
                    .allMatch(sql -> sql.contains("d.id in") && sql.contains("t.declaration_revision=d.revision_no")
                            && !sql.contains("request_hash") && !sql.contains("operator_user_id")));
            var summary = JSON.readTree(m.perform(full(get(Q + "/" + id + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(summary.get("state"), row.get("processingState"));
            assertEquals("LOCAL", row.get("source").asText());
            assertEquals("RETURNED", row.get("processingState").asText());
            amount(row, "pendingQuantity", "0");
            amount(row, "returnedQuantity", "10");
            var other = JSON.readTree(m.perform(full(get(Q), "B")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals(0, other.get("total").asInt());
        }
    }

    @Test
    void listIgnoresOldRevisionAssessmentAndKeepsDecimalQuantities() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = ProductionDefectHttpTest.add(m,
                    ProductionDefectHttpTest.command(UUID.randomUUID().toString(), 0, "10"), "A");
            var revised = JSON.readTree(ProductionDefectHttpTest.command(UUID.randomUUID().toString(), 1, "6.12"));
            ((ObjectNode) revised).remove("assessment");
            m.perform(full(post(Q + "/" + id + "/revisions"), "A").content(revised.toString()))
                    .andExpect(status().isOk());
            var list = JSON.readTree(m.perform(full(get(Q).param("limit", "1"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var row = list.get("items").get(0);
            assertEquals(1, list.get("total").asInt());
            assertEquals(2, row.get("revisionNo").asInt());
            assertEquals("UNVERIFIED", row.get("processingState").asText());
            amount(row, "pendingQuantity", "6.12");
            amount(row, "returnedQuantity", "0");
            var empty = JSON.readTree(m.perform(full(get(Q).param("offset", "1").param("limit", "1"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(1, empty.get("total").asInt());
            assertEquals(0, empty.get("items").size());
        }
    }

    @Test
    void transferRejectsMissingBatchWithoutStockWrites() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                            .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                    .andExpect(status().isConflict());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE material_id=1 AND batch_no LIKE '000LOT-%'", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='TRANSFER_WAREHOUSE'", Integer.class));
        }
    }

    @Test
    void returnAndNormalReportingUseOneQuantityBasisEvenWithDownstreamReports() throws Exception {
        try (var c = context(EXTRA, 100, 100, 100, 100, 100)) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var jdbc = ProductionWarehouseFixture.db(c);
            var id = defect(mvc, 12, "10", "INDEPENDENT", null);
            var before = quantities(mvc);
            historicalReturn(jdbc, id, UUID.randomUUID().toString(), "4");
            var returned = quantities(mvc);
            for (int i = 0; i < 5; i++)
                assertEquals(before.get("nodes").get(i).get("effectiveOutputQty"), returned.get("nodes").get(i)
                        .get("effectiveOutputQty"));
            amount(returned.get("nodes").get(1), "returnedQty", "4");
            amount(returned.get("nodes").get(1), "availableQty", "4");
            jdbc.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            mvc.perform(full(post(ProductionReportingHttpTest.ROOT), "A").content(ProductionReportingHttpTest.body(12, "4")))
                    .andExpect(status().isOk());
            var produced = quantities(mvc);
            amount(produced.get("nodes").get(1), "reportedQty", "100");
            amount(produced.get("nodes").get(1), "effectiveOutputQty", "94");
            amount(produced.get("nodes").get(4), "effectiveOutputQty", "94");
        }
    }

    @Test
    void scrapReceiptCreatesAndApprovesLinkedDocumentAndReplayDoesNotReceiveTwice() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("UPDATE wm_warehouse SET warehouse_type='SCRAP' WHERE id=1");
            String declaration = defect(m, 12, "10", "INDEPENDENT", null);
            bindBatch(j, "000SCRAP-001");
            String key = UUID.randomUUID().toString();
            String body = command(key, "WAREHOUSE", "10", "1");
            var result = dispose(m, declaration, body);
            var event = result.path("events").get(result.path("events").size() - 1);
            String documentId = event.path("stockDocumentId").asText();
            assertEquals("SCRAP_RECEIPT", j.queryForObject("SELECT document_type FROM wm_stock_document WHERE id=?", String.class, documentId));
            assertEquals(key, j.queryForObject("SELECT post_request_id FROM wm_stock_document WHERE id=? AND status='POSTED' AND posted_by=1 AND posted_at IS NOT NULL", String.class, documentId));
            var document = JSON.readTree(m.perform(full(get(StockDocumentHttpTest.ROOT + "/" + documentId), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(declaration, document.path("qualitySource").path("sourceDeclarationId").asText());
            assertEquals(key, document.path("qualitySource").path("sourceTreatmentId").asText());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line l JOIN wm_stock_action a ON a.id=l.stock_action_id AND a.document_line_id=l.id WHERE l.document_id=?", Integer.class, documentId));
            dispose(m, declaration, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE batch_no='000SCRAP-001'", BigDecimal.class)));
        }
    }

}
