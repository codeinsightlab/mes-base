package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2: formal input, report adjustments and defects use current production queries.
 */
class FormalInputProductionHttpTest {
    private static void formalOpening(AnnotationConfigWebApplicationContext context) throws Exception {
        var jdbc = ProductionWarehouseFixture.db(context);
        String documents = "SELECT id FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'";
        jdbc.update("DELETE FROM mes_material_issue_input WHERE document_id IN (" + documents + ")");
        jdbc.update("DELETE FROM wm_stock_document_line WHERE document_id IN (" + documents + ")");
        jdbc.update("DELETE FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'");
        ProductionInputTestData.confirmed(jdbc, 1, new BigDecimal("100"));
        jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,reason,create_time) SELECT execution_id,routing_node_id,'ADJUSTMENT',10,id,operator_user_id,1,report_time,'TEST ONLY reviewed quantity correction',CURRENT_TIMESTAMP FROM mes_production_report WHERE execution_id=1 AND routing_node_id=12 AND record_type='REPORT' ORDER BY id LIMIT 1");
        QualityWorkflowHttpTest.defect(MaterialIssueHttpTest.mvc(context), 12, "10", "INDEPENDENT", null);
    }

    @Test
    void importedFactsUseNormalAuditedAdjustmentWithoutMigrationActivationGate() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class}, 40, 30, 30, 30, 30)) {
            var j = ProductionWarehouseFixture.db(c);
            formalOpening(c);
            Long original = j.queryForObject("SELECT MIN(id) FROM mes_production_report WHERE execution_id=1 AND routing_node_id=15", Long.class);
            var http = MaterialIssueHttpTest.mvc(c);
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("writable").value(true))
                    .andExpect(jsonPath("nodes[0].availableQty").value("60.000000"));
            http.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/" + original + "/adjustments"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-1\",\"reason\":\"TEST ONLY audited correction\"}"))
                    .andExpect(status().isOk());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE record_type='ADJUSTMENT' AND reference_report_id=?", Integer.class, original));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[4].reportedQty").value("29.000000"));
        }
    }

    @Test
    void formalInputQuantitiesRemainReadableAndParentRemainsOpen() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class}, 100, 90, 90, 90, 90)) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            formalOpening(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            // Execution has consumed its own 100; the order still has 900 of demand available.
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("historical").doesNotExist())
                    .andExpect(jsonPath("productionInputQty").value("100.000000"))
                    .andExpect(jsonPath("confirmedInputQty").value("100.000000"))
                    .andExpect(jsonPath("nodes[1].reportedQty").value("100.000000"))
                    .andExpect(jsonPath("nodes[1].defectQty").value("10.000000"))
                    .andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
            http.perform(ProductionCoreV1HttpTest.full(post("/api/production/mes-production-execution"))
                            .content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"901\",\"status\":\"READY\"}"))
                    .andExpect(status().isMethodNotAllowed());
            http.perform(ProductionCoreV1HttpTest.full(post("/api/production/mes-production-execution"))
                            .content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"900\",\"status\":\"READY\"}"))
                    .andExpect(status().isMethodNotAllowed());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void lastReportPreservesOpeningQuantitiesAndDoesNotCloseExecutionOrOrder() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, ProductionReportingHttpTest.Extra.class}, 100, 90, 90, 90, 80)) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            formalOpening(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("PENDING"))
                    .andExpect(jsonPath("canComplete").doesNotExist());
            http.perform(ProductionCoreV1HttpTest.full(post(ProductionReportingHttpTest.ROOT))
                    .content(ProductionReportingHttpTest.body(15, "10"))).andExpect(status().isOk());
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
        }
    }

    @Test
    void pendingFormalDefectKeepsClosureBlocked() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class}, 100, 90, 90, 90, 90)) {
            formalOpening(c);
            QualityWorkflowHttpTest.defect(MaterialIssueHttpTest.mvc(c), 12, "10", null, null);
            var http = MaterialIssueHttpTest.mvc(c);
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("receiptState").value("PENDING"))
                    .andExpect(jsonPath("canComplete").doesNotExist());
            var q = QualityWorkflowHttpTest.quantities(http);
            QualityWorkflowHttpTest.amount(q.path("nodes").get(1), "unverifiedDefectQty", "10");
            QualityWorkflowHttpTest.amount(q.path("nodes").get(4), "effectiveOutputQty", "90");
        }
    }

}
