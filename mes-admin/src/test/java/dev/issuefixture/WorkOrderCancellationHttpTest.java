package dev.issuefixture;

import java.math.BigDecimal;
import tools.jackson.databind.ObjectMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: real HTTP, generated SQL, transactions, two independent business databases.
 */
class WorkOrderCancellationHttpTest {
    static final String CANCEL = "/api/production/workorder/1/actions/cancel";

    static String body(String id) {
        return "{\"requestId\":\"" + id + "\",\"reason\":\"TEST ONLY formal cancellation\"}";
    }

    static void cancel(MockMvc m) throws Exception {
        m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(body(UUID.randomUUID().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
    }

    @Test
    void draftUnknownSourceIdempotencyAndPermission() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE workorder SET source=NULL,status='DRAFT' WHERE id=1");
            String id = UUID.randomUUID().toString(), b = body(id);
            m.perform(post(CANCEL).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(b)).andExpect(status().isForbidden());
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(b)).andExpect(status().isOk())
                    .andExpect(jsonPath("previousStatus").value("DRAFT"));
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(b)).andExpect(status().isOk())
                    .andExpect(jsonPath("cancellationId").value(id));
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(b.replace("formal cancellation", "changed")))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(body(UUID.randomUUID().toString())))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder/1/actions/release"))
                    .content("{\"revision\":1}")).andExpect(status().isConflict());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
            assertEquals(null, j.queryForObject("SELECT source FROM workorder WHERE id=1", String.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/workorder")))
                    .andExpect(jsonPath("total").value(0));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/workorder")).param("status", "CANCELLED"))
                    .andExpect(jsonPath("total").value(1));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/workorder/1/cancellation")))
                    .andExpect(jsonPath("cancellationId").value(id));
            m.perform(post(CANCEL).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B")
                    .contentType("application/json").content(b)).andExpect(status().isOk());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
        }
    }

    @Test
    void externalAndVersionAndBadCommandsRejected() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content("{\"requestId\":\"bad\",\"reason\":\"x\"}"))
                    .andExpect(status().isBadRequest());
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL))
                            .content(body(UUID.randomUUID().toString()).replace("}", ",\"expectedRevision\":9}")))
                    .andExpect(status().isConflict());
            j.update("UPDATE workorder SET source='EXTERNAL' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(body(UUID.randomUUID().toString())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("EXTERNAL_SOURCE_READ_ONLY"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
        }
    }

    @Test
    void runningWithWarehouseFactsCancelsWithoutChangingAnyFactAndRejectsForwardCommands() throws Exception {
        try (var c = ProductionWarehouseFixture.context(ProductionInputHttpTest.Extra.class)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String receipt = ProductionWarehouseFixture.legacy(m, j);
            String acceptance = ProductionWarehouseFixture.legacyAccepted(j, receipt, "60");
            var tables = List.of("mes_production_execution", "mes_material_issue", "mes_material_issue_input", "mes_production_report", "mes_production_defect", "mes_quality_treatment", "mes_quality_release", "mes_production_receipt", "mes_warehouse_acceptance", "wm_inventory", "wm_stock_action");
            var baseline = tables.stream().map(t -> j.queryForList("SELECT * FROM " + t + " ORDER BY id")).toList();
            cancel(m);
            assertEquals(baseline, tables.stream().map(t -> j.queryForList("SELECT * FROM " + t + " ORDER BY id"))
                    .toList());
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionCoreV1HttpTest.E))
                            .content(ProductionCoreV1HttpTest.EXEC)).andExpect(status().isMethodNotAllowed());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/input/calculate")).param("code", "WO1:M1#L001")
                            .param("workorderId", "1").param("stockReference", "1").param("outputQty", "1"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("WORKORDER_CANCELLED"));
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionCoreV1HttpTest.H))
                    .content(ProductionCoreV1HttpTest.HEADER)).andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReportHttpTest.ROOT + "/supplements"))
                            .content(ProductionReportHttpTest.supplement(11, "1"))).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("WORKORDER_CANCELLED"));
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.ROOT))
                            .content("{\"documentType\":\"PRODUCTION_RECEIPT\",\"sourceExecutionId\":1,\"reason\":\"TEST ONLY\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("WORKORDER_CANCELLED"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/executions/1/actions/complete"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedBasisHash\":\"test\",\"reason\":\"TEST ONLY\"}"))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/production-acceptances/receipts/" + receipt + "/history")))
                    .andExpect(status().isOk());
            assertEquals(0, new BigDecimal("60").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

    @Test
    void failedLocalInputLeavesNothingToReconcileAndCancellationPreventsAnotherInput() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            var integration = c.getBean(WarehouseIntegration.class);
            doReturn(new ErpActionResult(Status.UNKNOWN, Mode.LOCAL_ONLY, null, "TEST interrupted local transaction"))
                    .when(integration).createMaterialIssue(any(), any(), any());
            var body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionInputHttpTest.ROOT)).content(body))
                    .andExpect(status().isConflict());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            cancel(m);
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionInputHttpTest.ROOT)).content(body))
                    .andExpect(status().isConflict());
            verify(integration, times(1)).createMaterialIssue(any(), any(), any());
        }
    }

    @Test
    void factInsertRollsBackIfWorkorderUpdateFails() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.execute("ALTER TABLE workorder ADD CONSTRAINT test_cancel_failure CHECK(status <> 'CANCELLED')");
            m.perform(ProductionCoreV1HttpTest.full(post(CANCEL)).content(body(UUID.randomUUID().toString())))
                    .andExpect(status().is5xxServerError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
        }
    }

    @Test
    void concurrentDifferentIntentsCreateOneFact() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var barrier = new CyclicBarrier(2);
            var futures = List.of(1, 2).stream().map(n -> pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(CANCEL))
                        .content(body(UUID.randomUUID().toString()))).andReturn().getResponse().getStatus();
            })).toList();
            var results = List.of(futures.get(0).get(), futures.get(1).get());
            assertTrue(results.contains(200));
            assertTrue(results.contains(409));
            assertEquals(1, ProductionInputHttpTest.db(c)
                    .queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
        }
    }
}
