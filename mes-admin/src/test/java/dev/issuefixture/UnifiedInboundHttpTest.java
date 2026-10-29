package dev.issuefixture;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real document/stock transactions and generated XML on two isolated routed H2 databases.
 */
class UnifiedInboundHttpTest {
    static String draft(MockMvc m, String execution, String qty) throws Exception {
        return draft(m, execution, qty, "TEST-FINISHED");
    }

    static String draft(MockMvc m, String execution, String qty, String batchNo) throws Exception {
        String ruleCode = execution == null ? "OTHER_RECEIPT" : "PRODUCTION_RECEIPT";
        String body = "{\"ruleCode\":\"" + ruleCode + "\",\"direction\":\"IN\",\"documentType\":\"" + (execution == null ? "OTHER_RECEIPT" : "PRODUCTION_RECEIPT") + "\",\"reason\":\"TEST ONLY inbound\"" + (execution == null ? "" : ",\"sourceExecutionId\":\"" + execution + "\"") + "}";
        var d = StockDocumentHttpTest.JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT)).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = d.get("document").get("id").asText();
        m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines")).content(StockDocumentHttpTest.line("0", "1", batchNo, qty)
                .replace("\"binCode\":\"BIN1\"", "\"binCode\":null"))).andExpect(status().isOk());
        return id;
    }

    static void confirm(MockMvc m, String id, int revision) throws Exception {
        m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                .toString(), revision))).andExpect(status().isOk());
    }

    static String receive(MockMvc m, String qty) throws Exception {
        return receive(m, qty, "TEST-FINISHED");
    }

    static String receive(MockMvc m, String qty, String batchNo) throws Exception {
        String id = draft(m, "1", qty, batchNo);
        confirm(m, id, 1);
        return id;
    }

    static void stock(JdbcTemplate j) {
        j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
    }

    @Test
    void aDraftDoesNotAffectStockAndPostingIsAtomicIdempotentAndIndependentOfProductionFinish() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            String id = draft(m, "1", "60");
            StockDocumentHttpTest.balance(j, "0");
            String intent = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1);
            for (int i = 0; i < 2; i++)
                m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(intent))
                        .andExpect(status().isOk()).andExpect(jsonPath("document.sourceExecutionId").value("1"));
            StockDocumentHttpTest.balance(j, "60");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_warehouse_acceptance", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE execution_id=1 AND document_line_id IS NOT NULL", Integer.class));
            m.perform(full(get(StockDocumentHttpTest.ROOT + "/executions/1/basis"))).andExpect(status().isOk())
                    .andExpect(jsonPath("remaining").value("40.000000"));
        }
    }

    @Test
    void ordinaryMaterialInboundUsesTheSameDocumentWithoutProductionOrQualityFacts() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            String id = draft(m, null, "20");
            confirm(m, id, 1);
            StockDocumentHttpTest.balance(j, "20");
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE source_execution_id IS NULL AND status='POSTED'", Integer.class));
        }
    }

    @Test
    void productionInboundWithoutReleaseStillRejectsCombinedLinesAboveNetOutput() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            String id = draft(m, "1", "60");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines")).content(StockDocumentHttpTest.line("1", "1", "TEST-FINISHED", "50")
                    .replace("\"binCode\":\"BIN1\"", "\"binCode\":null"))).andExpect(status().isOk());
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                    .toString(), 2))).andExpect(status().isConflict());
            StockDocumentHttpTest.balance(j, "0");
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER-BATCH", "test-finished", "TEST-FINISHED "})
    void productionApprovalRejectsDifferentBatchTextWithoutStockOrClosureFacts(String wrongBatch) throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(2,1,1,?,0,0,'ENABLED')", wrongBatch);
            String id = StockDocumentHttpTest.JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT))
                                    .content("{\"direction\":\"IN\",\"ruleCode\":\"PRODUCTION_RECEIPT\",\"documentType\":\"PRODUCTION_RECEIPT\",\"sourceExecutionId\":\"1\",\"reason\":\"TEST ONLY mismatched batch\"}"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document")
                    .get("id").asText();
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines"))
                            .content(StockDocumentHttpTest.line("0", "2", wrongBatch, "1")
                                    .replace("\"binCode\":\"BIN1\"", "\"binCode\":null")))
                    .andExpect(status().isOk());
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isConflict());
            assertEquals("DRAFT", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, id));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(0, BigDecimal.ZERO.compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class)));
        }
    }

    @Test
    void formalProofRejectsBatchMismatchEvenWhenTheLineAndStockActionAgree() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            String id = receive(m, "60");
            var transaction = new TransactionTemplate(c.getBean("businessTransactionManager", PlatformTransactionManager.class));
            FactoryContext.runWithScope(new FactoryScope(new FactoryId("A")), () -> {
                var proof = c.getBean(StockDocumentExtraMapper.class);
                transaction.executeWithoutResult(status -> {
                    assertEquals(0, new BigDecimal("60").compareTo(proof.formalProductionInbound(1L)));
                    assertEquals(0, proof.invalidProductionInboundLines(1L));
                });
                // TEST ONLY corruption injection; each proof read gets a fresh fixed business lease/session.
                j.update("UPDATE wm_stock_document_line SET batch_no='test-finished' WHERE document_id=?", id);
                j.update("UPDATE wm_stock_action SET batch_no='test-finished' WHERE document_line_id IN (SELECT id FROM wm_stock_document_line WHERE document_id=?)", id);
                transaction.executeWithoutResult(status -> {
                    assertEquals(0, BigDecimal.ZERO.compareTo(proof.formalProductionInbound(1L)));
                    assertEquals(1, proof.invalidProductionInboundLines(1L));
                });
                j.update("UPDATE wm_stock_document_line SET batch_no='TEST-FINISHED' WHERE document_id=?", id);
                j.update("UPDATE wm_stock_action SET batch_no='TEST-FINISHED' WHERE document_line_id IN (SELECT id FROM wm_stock_document_line WHERE document_id=?)", id);
                transaction.executeWithoutResult(status -> {
                    assertEquals(0, new BigDecimal("60").compareTo(proof.formalProductionInbound(1L)));
                    assertEquals(0, proof.invalidProductionInboundLines(1L));
                });
            });
        }
    }

    @Test
    void concurrentDocumentsShareTheExecutionLockAndCannotSpendTheSameOutput() throws Exception {
        try (var c = ProductionWarehouseFixture.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            var ids = List.of(draft(m, "1", "60"), draft(m, "1", "60"));
            var barrier = new CyclicBarrier(2);
            var tasks = ids.stream().map(id -> pool.submit(() -> {
                barrier.await();
                return m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                        .toString(), 1))).andReturn().getResponse().getStatus();
            })).toList();
            var statuses = List.of(tasks.get(0).get(), tasks.get(1).get());
            assertTrue(statuses.contains(200) && statuses.contains(409));
            StockDocumentHttpTest.balance(j, "60");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void actualHistoricalReceiptsCountAgainstNewDocumentsWithoutRewritingHistory() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String old = ProductionWarehouseFixture.legacy(m, j);
            ProductionWarehouseFixture.legacyAccepted(j, old, "60");
            var before = j.queryForList("SELECT * FROM mes_warehouse_acceptance");
            String excessive = draft(m, "1", "50");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + excessive + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                    .toString(), 1))).andExpect(status().isConflict());
            receive(m, "40");
            StockDocumentHttpTest.balance(j, "100");
            assertEquals(before, j.queryForList("SELECT * FROM mes_warehouse_acceptance"));
        }
    }

    @Test
    void cancelledHistoricalRecoveryAlsoDeductsAlreadyPostedUnifiedInbound() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String old = ProductionWarehouseFixture.legacy(m, j);
            ProductionWarehouseFixture.legacyAccepted(j, old, "60");
            receive(m, "20");
            WorkOrderCancellationHttpTest.cancel(m);
            m.perform(full(get(StockDocumentHttpTest.ROOT + "/executions/1/basis"))).andExpect(status().isOk())
                    .andExpect(jsonPath("canReceive").value(false));
            m.perform(full(get(StockDocumentHttpTest.ROOT + "/cancelled-workorders/1/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("remaining").value("0.000000"));
            StockDocumentHttpTest.balance(j, "80");
        }
    }

    @Test
    void secondLineStockFailureRollsBackTheWholeDocumentAndSameUuidCanBeRetried() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            String id = draft(m, "1", "50");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines")).content(StockDocumentHttpTest.line("1", "1", "TEST-FINISHED", "30")
                    .replace("\"binCode\":\"BIN1\"", "\"binCode\":null"))).andExpect(status().isOk());
            String intent = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 2);
            j.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_only_post_failure CHECK(quantity_delta != 30)");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(intent))
                    .andExpect(status().is5xxServerError());
            StockDocumentHttpTest.balance(j, "0");
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals("DRAFT", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, id));
            j.execute("ALTER TABLE wm_stock_action DROP CONSTRAINT test_only_post_failure");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(intent))
                    .andExpect(status().isOk());
            StockDocumentHttpTest.balance(j, "80");
        }
    }

    @Test
    void rawReceiptCannotBypassTheDocumentLifecycle() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            stock(j);
            m.perform(full(post("/api/wm/local-stock/receipt")).content(LocalWarehouseHttpTest.body(UUID.randomUUID()
                            .toString(), "1", "0", null)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("STOCK_DOCUMENT_REQUIRED"));
            StockDocumentHttpTest.balance(j, "0");
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void formerHandoverAndAcceptanceCommandsAreGoneButHistoryRemainsReadable() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String id = ProductionWarehouseFixture.legacy(m, j);
            ProductionWarehouseFixture.legacyAccepted(j, id, "60");
            m.perform(full(get("/api/production/receipts/" + id))).andExpect(status().isOk());
            m.perform(full(get("/api/wm/production-acceptances/receipts/" + id + "/history")))
                    .andExpect(status().isOk());
            m.perform(full(post("/api/production/receipts")).content("{}")).andExpect(status().isMethodNotAllowed());
            m.perform(full(post("/api/wm/production-acceptances/receipts/" + id)).content("{}"))
                    .andExpect(status().isNotFound());
            StockDocumentHttpTest.balance(j, "60");
        }
    }
}
