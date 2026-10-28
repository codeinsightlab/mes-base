package dev.issuefixture;

import java.math.BigDecimal;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: isolated routed H2; all closing commands use formal HTTP approval and real mapped SQL.
 */
class ProductionCompletionHttpTest {
    static void oneExecution(JdbcTemplate j, String plan) {
        j.update("DELETE FROM mes_production_execution WHERE id=2");
        j.update("UPDATE workorder SET planned_qty=? WHERE id=1", new BigDecimal(plan));
    }

    @Test
    void splitNormalInboundClosesOnlyAt100AndOriginalApprovalReplaysAfterClosure() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "60");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            String id = UnifiedInboundHttpTest.draft(m, "1", "40");
            String command = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1);
            for (int replay = 0; replay < 2; replay++)
                m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(command))
                        .andExpect(status().isOk());
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line WHERE batch_no='TEST-FINISHED'", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                    .content(command.replace("\"1\"", "\"2\""))).andExpect(status().isConflict());
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("FULLY_RECEIVED"));
        }
    }

    @Test
    void administratorFinalNodeAdjustmentUsesOrdinaryInboundApprovalAndNoReleaseFact() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            j.update("UPDATE mes_production_report SET quantity_delta=90 WHERE execution_id=1 AND routing_node_id=13");
            var original = j.queryForMap("SELECT * FROM mes_production_report WHERE execution_id=1 AND routing_node_id=13");
            String reportId = original.get("ID").toString();
            String command = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"10\",\"reason\":\"TEST ONLY administrator verified omitted final output\"}";
            m.perform(full(post("/api/production/reports/" + reportId + "/adjustments")).content(command))
                    .andExpect(status().isOk());
            assertEquals(original, j.queryForMap("SELECT * FROM mes_production_report WHERE id=?", reportId));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "100");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion WHERE released_qty IS NULL", Integer.class));
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("FULLY_RECEIVED"))
                    .andExpect(jsonPath("released").doesNotExist())
                    .andExpect(jsonPath("completion.releasedQty").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void knownDefectAndNormalInboundCanBeApprovedInEitherOrder(boolean qualityFirst) throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            QualityDispositionHttpTest.bindBatch(j, "TEST-FINISHED");
            UnifiedInboundHttpTest.stock(j);
            String defect = QualityWorkflowHttpTest.defect(m, 12, "10", "INDEPENDENT", null);
            if (qualityFirst) QualityWorkflowHttpTest.transfer(m, defect, "10");
            UnifiedInboundHttpTest.receive(m, "90");
            if (!qualityFirst) {
                assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
                QualityWorkflowHttpTest.transfer(m, defect, "10");
            }
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, new BigDecimal("90").compareTo(j.queryForObject("SELECT accepted_qty FROM mes_execution_completion", BigDecimal.class)));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }

    @Test
    void terminalGross100WithDefect10ContributesOnlyNormal90PlusLoss10WithoutAddingQualityInboundAgain() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            QualityDispositionHttpTest.bindBatch(j, "TEST-FINISHED");
            UnifiedInboundHttpTest.stock(j);
            String defect = QualityWorkflowHttpTest.defect(m, 15, "10", "INDEPENDENT", null);
            UnifiedInboundHttpTest.receive(m, "90");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            QualityWorkflowHttpTest.transfer(m, defect, "10");
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("90.000000"))
                    .andExpect(jsonPath("defectQty").value("10.000000"))
                    .andExpect(jsonPath("receivedQty").value("90.000000"))
                    .andExpect(jsonPath("receiptState").value("FULLY_RECEIVED"));
            assertEquals(0, new BigDecimal("100").compareTo(j.queryForObject(
                    "SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=1 AND routing_node_id=15", BigDecimal.class)));
            assertEquals(0, new BigDecimal("90").compareTo(j.queryForObject("SELECT accepted_qty FROM mes_execution_completion", BigDecimal.class)));
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject(
                    "SELECT SUM(l.quantity) FROM wm_stock_document d JOIN wm_stock_document_line l ON l.document_id=d.id WHERE d.document_type='DEFECT_RECEIPT' AND d.status='POSTED'", BigDecimal.class)));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }

    @Test
    void allDefectClosesOnQualityApprovalWithoutZeroNormalDocument() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            QualityDispositionHttpTest.bindBatch(j, "TEST-FINISHED");
            String defect = QualityWorkflowHttpTest.defect(m, 11, "100", "INDEPENDENT", null);
            QualityWorkflowHttpTest.transfer(m, defect, "100");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='PRODUCTION_RECEIPT'", Integer.class));
            assertEquals(0, j.queryForObject("SELECT accepted_qty FROM mes_execution_completion", BigDecimal.class)
                    .signum());
        }
    }

    @Test
    void allDefectPendingBlockerCanRecoverFromQualityAuditWithNoNormalReceipt() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            QualityDispositionHttpTest.bindBatch(j, "TEST-FINISHED");
            String defect = QualityWorkflowHttpTest.defect(m, 11, "100", "INDEPENDENT", null);
            j.update("INSERT INTO mes_material_issue(issue_no,workorder_id,product_id,routing_id,execution_id,status) VALUES('TEST ONLY pending',1,1,1,1,'DRAFT')");
            QualityWorkflowHttpTest.transfer(m, defect, "100");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='PRODUCTION_RECEIPT'", Integer.class));
            j.update("UPDATE mes_material_issue SET status='SUBMIT_REJECTED' WHERE issue_no='TEST ONLY pending'");
            for (int repeat = 0; repeat < 2; repeat++)
                m.perform(full(post("/api/production/executions/1/actions/recheck-receipt")))
                        .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("FULLY_RECEIVED"));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void onlyTenReportedAndReceivedOutOf100CannotClose() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            j.update("UPDATE mes_production_report SET quantity_delta=10 WHERE routing_node_id=13");
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "10");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "10");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "99"})
    void planMismatchDoesNotRollBackLegalInboundOrPretendParentComplete(String plan) throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, plan);
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "100");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            StockDocumentHttpTest.balance(j, "100");
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("orderBlockedReason").isString());
        }
    }

    @Test
    void closurePersistenceFailureRollsBackStockDocumentAndSameApprovalCanRetry() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            String id = UnifiedInboundHttpTest.draft(m, "1", "100");
            String command = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1);
            j.execute("ALTER TABLE mes_execution_completion ADD CONSTRAINT test_only_closure_failure CHECK(accepted_qty != 100)");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(command))
                    .andExpect(status().is5xxServerError());
            StockDocumentHttpTest.balance(j, "0");
            assertEquals("DRAFT", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, id));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            j.execute("ALTER TABLE mes_execution_completion DROP CONSTRAINT test_only_closure_failure");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(command))
                    .andExpect(status().isOk());
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
        }
    }

    @Test
    void siblingExecutionsCloseParentOnlyAfterBothInboundTransactions() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("UPDATE workorder SET planned_qty=110 WHERE id=1");
            j.update("UPDATE mes_production_execution SET status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=2");
            ProductionInputTestData.confirmed(j, 2, new BigDecimal("10"));
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) SELECT 2,routing_node_id,'REPORT',10,1,1,CURRENT_TIMESTAMP,'TEST ONLY sibling',CURRENT_TIMESTAMP FROM mes_production_report WHERE execution_id=1");
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "100");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            String second = UnifiedInboundHttpTest.draft(m, "2", "10");
            UnifiedInboundHttpTest.confirm(m, second, 1);
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @Test
    void concurrentSixtyFortyApprovalsSerializeOneClosureAndAllStock() throws Exception {
        try (var c = ProductionWarehouseFixture.context(); var workers = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            var ids = List.of(UnifiedInboundHttpTest.draft(m, "1", "60"), UnifiedInboundHttpTest.draft(m, "1", "40"));
            var barrier = new CyclicBarrier(2);
            var tasks = ids.stream().map(id -> workers.submit(() -> {
                barrier.await();
                return m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                                .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1))).andReturn()
                        .getResponse().getStatus();
            })).toList();
            assertEquals(200, tasks.get(0).get());
            assertEquals(200, tasks.get(1).get());
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }

    @Test
    void concurrentSameApprovalUuidReplaysAfterWinnerClosesExecution() throws Exception {
        try (var c = ProductionWarehouseFixture.context(); var workers = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            String id = UnifiedInboundHttpTest.draft(m, "1", "100");
            String command = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1);
            var barrier = new CyclicBarrier(2);
            var tasks = IntStream.range(0, 2).mapToObj(ignored -> workers.submit(() -> {
                barrier.await();
                return m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                        .content(command)).andReturn().getResponse().getStatus();
            })).toList();
            assertEquals(200, tasks.get(0).get());
            assertEquals(200, tasks.get(1).get());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }

    @Test
    void unresolvedIssueDoesNotInvalidateLegalStockButBlocksClosure() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            j.update("INSERT INTO mes_material_issue(issue_no,workorder_id,product_id,routing_id,execution_id,status) VALUES('TEST ONLY pending',1,1,1,1,'DRAFT')");
            UnifiedInboundHttpTest.receive(m, "100");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
            // TEST ONLY: the separately tested issue workflow has resolved the former blocker.
            j.update("UPDATE mes_material_issue SET status='SUBMIT_REJECTED' WHERE issue_no='TEST ONLY pending'");
            for (int repeat = 0; repeat < 2; repeat++)
                m.perform(full(post("/api/production/executions/1/actions/recheck-receipt")))
                        .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("FULLY_RECEIVED"));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }

    @Test
    void recheckCannotCloseWithoutPostedInboundOrBypassItsExplicitPermission() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.draft(m, "1", "100");
            m.perform(post("/api/production/executions/1/actions/recheck-receipt")
                            .header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(full(post("/api/production/executions/1/actions/recheck-receipt")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("PENDING"))
                    .andExpect(jsonPath("blockedReason").value("没有与正式库存动作一致的已审核生产或质量入库，不能复查收尾"));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "unit", "warehouseSource", "missingProduct", "missingLine"})
    void formalInboundRejectsPostedFactsWhoseProductUnitOrLocalSourceProofWasCorrupted(String damage) throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            oneExecution(j, "100");
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "60");
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receivedQty").value("60.000000"));
            // TEST ONLY corruption: production never mutates the immutable posted facts this way.
            switch (damage) {
                case "product" -> {
                    j.update("UPDATE wm_stock_document_line SET material_id=999");
                    j.update("UPDATE wm_stock_action SET material_id=999");
                }
                case "unit" -> {
                    j.update("UPDATE wm_stock_document_line SET unit_id=999");
                    j.update("UPDATE wm_stock_action SET unit_id=999");
                }
                case "warehouseSource" -> j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
                case "missingProduct" -> j.update("DELETE FROM material WHERE id=1");
                case "missingLine" -> j.update("DELETE FROM wm_stock_document_line");
                default -> throw new IllegalArgumentException(damage);
            }
            m.perform(full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receivedQty").value("0"))
                    .andExpect(jsonPath("blockedReason").value("正式生产入库明细与库存动作不一致"));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void retiredManualCommandOnlyReplaysExactHistoricalIdentity() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String request = UUID.randomUUID()
                    .toString(), basis = "f".repeat(64), reason = "TEST ONLY historical complete";
            String hash = ProductionClosureRules.hash(1L, basis, reason, 1L);
            j.update("INSERT INTO mes_execution_completion(id,execution_id,effective_qty,released_qty,receipt_qty,accepted_qty,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES(?,1,100,0,0,0,?,?,1,CURRENT_TIMESTAMP,?)", request, basis, hash, reason);
            j.update("UPDATE mes_production_execution SET status='FULLY_RECEIVED',end_time=CURRENT_TIMESTAMP WHERE id=1");
            String body = "{\"requestId\":\"" + request + "\",\"expectedBasisHash\":\"" + basis + "\",\"reason\":\"" + reason + "\"}";
            m.perform(full(post("/api/production/executions/1/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(request));
            m.perform(full(post("/api/production/executions/1/actions/complete")).content(body.replace(reason, "TEST ONLY altered")))
                    .andExpect(status().isConflict());
            m.perform(full(post("/api/production/executions/2/actions/complete")).content(body))
                    .andExpect(status().isConflict());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @Test
    void existingStockStillConsumesInboundAllowanceWithoutManufacturingFormalReceipt() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String old = ProductionWarehouseFixture.legacy(m, j);
            ProductionWarehouseFixture.legacyAccepted(j, old, "60");
            UnifiedInboundHttpTest.receive(m, "40");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            StockDocumentHttpTest.balance(j, "100");
        }
    }
}
