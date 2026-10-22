package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.acceptance.QueuedCompletionExecutor;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.server.WorkOrderCompletionJob;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;

/**
 * TEST ONLY H2 exact five-operation 100 -> defect10 -> warehouse10 -> good output90 closure.
 */
class ProductionCompletionHttpTest {
    @Test
    void manualCompensationCompletesProductionBeforeWarehouseAndKeepsImmutableSnapshot() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1"); // TEST ONLY unused sibling
            String defect = QualityWorkflowHttpTest.defect(m, 12, "10", "INDEPENDENT", null);
            QualityDispositionHttpTest.bindBatch(j, "000LOT-001");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("90")))
                    .andExpect(status().isConflict());
            QualityWorkflowHttpTest.transfer(m, defect, "10");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("90")))
                    .andExpect(status().isOk());

            UnifiedInboundHttpTest.receive(m, "60", "000LOT-001");
            UnifiedInboundHttpTest.receive(m, "20", "000LOT-001");
            String root = "/api/production/executions/1";
            var state = new ObjectMapper().readTree(m.perform(ProductionCoreV1HttpTest.full(get(root + "/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("90.000000"))
                    .andExpect(jsonPath("canComplete").value(true)).andReturn().getResponse()
                    .getContentAsString());
            String id = UUID.randomUUID()
                    .toString(), body = "{\"requestId\":\"" + id + "\",\"expectedBasisHash\":\"" + state.get("basisHash")
                    .asText() + "\",\"reason\":\"TEST ONLY 严格完工\"}";
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("effectiveQty").value("90.000000"));
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
            // Execution completion leaves the parent RELEASED until the periodic job observes it.
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            var job = job(c);
            j.update("INSERT INTO mes_production_execution(id,workorder_id,execution_no,product_id,routing_id,target_qty,status) VALUES(9000,1,'TEST-NOT-FINISHED',1,1,1,'READY')");
            job.scheduled();
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            j.update("DELETE FROM mes_production_execution WHERE id=9000");
            // Ownership lost after the UPDATE must roll back the entire business transaction.
            var coordinator = c.getBean(SyncCoordinator.class);
            var checks = new AtomicInteger();
            doAnswer(invocation -> {
                if (checks.incrementAndGet() == 4) throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
                return null;
            }).when(coordinator).assertOwner(any());
            job.scheduled();
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            doNothing().when(coordinator).assertOwner(any());
            job.scheduled();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("writable").value(false))
                    .andExpect(jsonPath("boundary").value("工单已完工，禁止继续正常报工"));
            var revision = j.queryForObject("SELECT revision FROM workorder WHERE id=1", Integer.class);
            job.scheduled();
            assertEquals(revision, j.queryForObject("SELECT revision FROM workorder WHERE id=1", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("100.000000", j.queryForObject("SELECT target_qty FROM mes_production_execution WHERE id=1", BigDecimal.class)
                    .toPlainString());
            // Production is terminal; remaining warehouse work still observes its own release/stock gates.
            UnifiedInboundHttpTest.receive(m, "10", "000LOT-001");
            assertEquals(new BigDecimal("80.000000"), j.queryForObject("SELECT accepted_qty FROM mes_execution_completion WHERE execution_id=1", BigDecimal.class));
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/select-path"))
                    .content("{\"revision\":1,\"routingNodeIds\":[11,12,13,14,15]}")).andExpect(status().isConflict());
        }
    }

    @Test
    void unprocessedInputCannotCompleteEvenWhenAllOutputWasReleasedAndAccepted() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(80, 80, 80, 80, 80)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("80")))
                    .andExpect(status().isOk());

            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            UnifiedInboundHttpTest.receive(m, "80");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("canComplete").value(false))
                    .andExpect(jsonPath("blockedReason").value("投入数量不等于末道有效报工与最终不良损失之和，不能完工"));
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1"); // TEST ONLY unused sibling
            // TEST ONLY legacy formal stamp with an unbalanced physical input must not close its parent.
            j.update("UPDATE mes_production_execution SET status='COMPLETED' WHERE id=1");
            j.update("INSERT INTO mes_execution_completion(id,execution_id,effective_qty,released_qty,receipt_qty,accepted_qty,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES(?,1,80,80,80,80,?,?,1,CURRENT_TIMESTAMP,'TEST ONLY unbalanced legacy stamp')",
                    UUID.randomUUID().toString(), "0".repeat(64), "0".repeat(64));
            job(c).scheduled();
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    static TestJob job(ApplicationContext context) {
        var catalog = mock(FactoryCatalog.class);
        when(catalog.enabled(0, 100)).thenReturn(List.of(
                new Factory(new FactoryId("A"), "TEST", "TEST-A", "TEST ONLY A", FactoryStatus.ENABLED),
                new Factory(new FactoryId("B"), "TEST", "TEST-B", "TEST ONLY B", FactoryStatus.ENABLED)));
        when(catalog.enabled(100, 100)).thenReturn(List.of());
        var coordinator = context.getBean(SyncCoordinator.class);
        when(coordinator.claim(any(), eq("workorder-completion"), any())).thenAnswer(invocation ->
                new SyncCoordinator.Run(invocation.getArgument(0), "workorder-completion", UUID.randomUUID()
                        .toString()));
        var tasks = new QueuedCompletionExecutor();
        return new TestJob(new WorkOrderCompletionJob(catalog, (scope, task) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.current(context, scope.factoryId().value()), task), coordinator,
                context.getBean(WorkOrderCompletionService.class), context.getBean(ProductionCompletionService.class), tasks), tasks);
    }

    record TestJob(WorkOrderCompletionJob job, QueuedCompletionExecutor tasks) {
        void scheduled() {
            job.scheduled();
            tasks.runAll();
        }
    }

    static String release(String qty) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY\"}";
    }

}
