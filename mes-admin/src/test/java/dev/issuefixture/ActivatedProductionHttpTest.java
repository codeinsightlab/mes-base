package dev.issuefixture;

import dev.mesbase.business.legacy.dao.LegacyHistoryMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Node;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real H2 production commands; only the immutable opening-evidence reader is mocked.
 */
class ActivatedProductionHttpTest {
    @Test
    void importedFactsUseNormalAuditedAdjustmentWithoutMigrationActivationGate() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{Opening.class}, 40, 30, 30, 30, 30)) {
            var j = ProductionWarehouseFixture.db(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                j.update("DELETE FROM " + table);
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

    @Configuration(proxyBeanMethods = false)
    static class Opening {
        @Bean
        LegacyHistoryMapper openingEvidence() {
            var m = mock(LegacyHistoryMapper.class);
            when(m.openingInput(1L)).thenReturn(new BigDecimal("100.000000"));
            var nodes = new ArrayList<Node>();
            for (long node = 11; node <= 15; node++)
                nodes.add(new Node(node,
                        new BigDecimal(node == 11 ? "100.000000" : "90.000000"),
                        new BigDecimal(node == 12 ? "10.000000" : "0.000000"),
                        new BigDecimal(node == 12 ? "10.000000" : "0.000000"), "{}"));
            when(m.nodes(1L)).thenReturn(List.copyOf(nodes));
            return m;
        }
    }

    @Test
    void openingQuantitiesRemainReadableWithoutInboundAndParentRemainsOpen() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{Opening.class}, 100, 90, 90, 90, 90)) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                j.update("DELETE FROM " + table);
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
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{Opening.class,
                ProductionReportingHttpTest.Extra.class}, 100, 90, 90, 90, 80)) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                j.update("DELETE FROM " + table);
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
    void balancedOpeningWithUnprovenHistoricalDispositionCannotComplete() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{Opening.class}, 100, 90, 90, 90, 90)) {
            var mapper = c.getBean(LegacyHistoryMapper.class);
            var pending = mapper.nodes(1L).stream().map(n -> new Node(n.routingNodeId(), n.legacyNet(),
                    n.grossCorrection(), n.effectiveDefect(), n.evidence(),
                    n.routingNodeId() == 12 ? new BigDecimal("10") : BigDecimal.ZERO)).toList();
            when(mapper.nodes(1L)).thenReturn(pending);
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
