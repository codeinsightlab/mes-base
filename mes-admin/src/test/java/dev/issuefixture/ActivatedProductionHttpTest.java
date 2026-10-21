package dev.issuefixture;

import dev.mesbase.acceptance.QueuedCompletionExecutor;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Node;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.server.WorkOrderCompletionJob;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
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
    @Configuration(proxyBeanMethods = false)
    static class Opening {
        @Bean
        LegacyMigrationMapper openingEvidence() {
            var m = mock(LegacyMigrationMapper.class);
            when(m.activatedInput(1L)).thenReturn(new BigDecimal("100.000000"));
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
    void openingQuantitiesSurviveCompletionAndParentRemainsOpenForAnotherExecution() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(new Class<?>[]{Opening.class,
                AutomaticProductionCompletionHttpTest.Async.class}, 100, 90, 90, 90, 90);
             var worker = Executors.newSingleThreadExecutor()) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                j.update("DELETE FROM " + table);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            // Execution has consumed its own 100; the order still has 900 of demand available.
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("historical").value(false))
                    .andExpect(jsonPath("productionInputQty").value("100.000000"))
                    .andExpect(jsonPath("confirmedInputQty").value("100.000000"))
                    .andExpect(jsonPath("nodes[1].reportedQty").value("100.000000"))
                    .andExpect(jsonPath("nodes[1].defectQty").value("10.000000"))
                    .andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
            c.getBean(WorkOrderCompletionJob.class)
                    .reported(new ProductionCompletionRequested(new FactoryScope(new FactoryId("A")), 1L));
            worker.submit(c.getBean(QueuedCompletionExecutor.class)::runAll).get();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
            http.perform(ProductionCoreV1HttpTest.full(post("/api/production/mes-production-execution"))
                            .content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"901\",\"status\":\"READY\"}"))
                    .andExpect(status().isUnprocessableEntity());
            http.perform(ProductionCoreV1HttpTest.full(post("/api/production/mes-production-execution"))
                            .content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"900\",\"status\":\"READY\"}"))
                    .andExpect(status().isOk());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void unfinishedOpeningExecutionAcceptsNormalLastReportAndThenAutomaticallyCompletes() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(new Class<?>[]{Opening.class,
                AutomaticProductionCompletionHttpTest.Async.class, ProductionReportingHttpTest.Extra.class}, 100, 90, 90, 90, 80);
             var worker = Executors.newSingleThreadExecutor()) {
            var j = ProductionWarehouseFixture.db(c);
            var http = MaterialIssueHttpTest.mvc(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                j.update("DELETE FROM " + table);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("canComplete").value(false));
            http.perform(ProductionCoreV1HttpTest.full(post(ProductionReportingHttpTest.ROOT))
                    .content(ProductionReportingHttpTest.body(15, "10"))).andExpect(status().isOk());
            worker.submit(c.getBean(QueuedCompletionExecutor.class)::runAll).get();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            http.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[4].effectiveOutputQty").value("90.000000"));
        }
    }

    @Test
    void formalImportedDefectReplacesOpeningLossWithoutSubtractingItTwice() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(new Class<?>[]{Opening.class}, 100, 90, 90, 90, 90)) {
            var fixtureDb = ProductionWarehouseFixture.db(c);
            for (String table : List.of("mes_material_issue_input", "mes_material_issue_item", "mes_material_issue_submission",
                    "mes_material_issue_erp_extra", "mes_material_issue"))
                fixtureDb.update("DELETE FROM " + table);
            var mapper = c.getBean(LegacyMigrationMapper.class);
            var before = mapper.nodes(1L);
            var converted = before.stream().map(n -> new Node(n.routingNodeId(), n.legacyNet(),
                    n.grossCorrection(), BigDecimal.ZERO, n.evidence())).toList();
            // The MySQL operation commits the conversion marker and declaration atomically.
            // This H2 fixture mocks only the opening reader; formal quality uses actual SQL/services.
            when(mapper.nodes(1L)).thenReturn(converted);
            var http = MaterialIssueHttpTest.mvc(c);
            String declaration = ProductionQualityV13HttpTest.defect(http, 12, "10", "INDEPENDENT", null);
            var q = ProductionQualityV13HttpTest.quantities(http);
            ProductionQualityV13HttpTest.amount(q.path("nodes").get(1), "reportedQty", "100");
            ProductionQualityV13HttpTest.amount(q.path("nodes").get(1), "defectQty", "10");
            ProductionQualityV13HttpTest.amount(q.path("nodes").get(1), "effectiveOutputQty", "90");
            var summary = ProductionQualityV13HttpTest.execute(http, declaration,
                    ProductionQualityV13HttpTest.command("ALLOCATE", null, "10", "0", "0", "0", "0"));
            assertEquals("SCRAPPED", summary.path("state").asText());
            q = ProductionQualityV13HttpTest.quantities(http);
            ProductionQualityV13HttpTest.amount(q.path("nodes").get(4), "effectiveOutputQty", "90");
            var j = ProductionWarehouseFixture.db(c);
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(90, j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=1 AND routing_node_id=12", Integer.class));
        }
    }

}
