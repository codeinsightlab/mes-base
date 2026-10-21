package dev.issuefixture;

import java.util.UUID;
import java.util.List;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.server.WorkOrderCompletionJob;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import java.util.concurrent.atomic.AtomicInteger;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY H2 exact five-operation 100 -> defect10 -> scrap6/recover4 -> E94 closure.
 */
class ProductionCompletionHttpTest {
    @Test
    void strictCompleteRequiresAll94AndKeepsTarget100AndIdempotentTerminalFact() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2"); // TEST ONLY unused sibling
            String defect = ProductionQualityV13HttpTest.defect(m, 12, "10", "INDEPENDENT", null);
            String started = ProductionQualityV13HttpTest.start(m, defect);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("90")))
                    .andExpect(status().isConflict());
            ProductionQualityV13HttpTest.execute(m, defect, ProductionQualityV13HttpTest.command("RESULT", started, "0", "0", "4", "4", "0"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("94")))
                    .andExpect(status().isOk());
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            String a = handover(m, "60"), b = handover(m, "20");
            accept(m, a, "60");
            accept(m, b, "20");
            String root = "/api/production/executions/1";
            var state = new ObjectMapper().readTree(m.perform(ProductionCoreV1HttpTest.full(get(root + "/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("94.000000"))
                    .andExpect(jsonPath("received").value("80.000000"))
                    .andExpect(jsonPath("accepted").value("80.000000"))
                    .andExpect(jsonPath("remainingHandover").value("14.000000")).andReturn().getResponse()
                    .getContentAsString());
            String id = UUID.randomUUID()
                    .toString(), body = "{\"requestId\":\"" + id + "\",\"expectedBasisHash\":\"" + state.get("basisHash")
                    .asText() + "\",\"reason\":\"TEST ONLY 严格完工\"}";
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isConflict());
            String last = handover(m, "14");
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isConflict());
            accept(m, last, "14");
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("effectiveQty").value("94.000000"));
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
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/select-path"))
                    .content("{\"revision\":1,\"routingNodeIds\":[11,12,13,14,15]}")).andExpect(status().isConflict());
        }
    }

    @Test
    void unprocessedInputCannotCompleteEvenWhenAllOutputWasReleasedAndAccepted() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(80, 80, 80, 80, 80)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("80")))
                    .andExpect(status().isOk());
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            accept(m, handover(m, "80"), "80");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("canComplete").value(false))
                    .andExpect(jsonPath("blockedReason").value("投入数量不等于末道有效报工与最终不良损失之和，不能完工"));
            j.update("DELETE FROM mes_production_execution WHERE id=2"); // TEST ONLY unused sibling
            // TEST ONLY legacy formal stamp with an unbalanced physical input must not close its parent.
            j.update("UPDATE mes_production_execution SET status='COMPLETED' WHERE id=1");
            j.update("INSERT INTO mes_execution_completion(id,execution_id,effective_qty,released_qty,receipt_qty,accepted_qty,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES(?,1,80,80,80,80,?,?,1,CURRENT_TIMESTAMP,'TEST ONLY unbalanced legacy stamp')",
                    UUID.randomUUID().toString(), "0".repeat(64), "0".repeat(64));
            job(c).scheduled();
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    static WorkOrderCompletionJob job(ApplicationContext context) {
        var catalog = mock(FactoryCatalog.class);
        when(catalog.enabled(0, 100)).thenReturn(List.of(
                new Factory(new FactoryId("A"), "TEST", "TEST-A", "TEST ONLY A", FactoryStatus.ENABLED),
                new Factory(new FactoryId("B"), "TEST", "TEST-B", "TEST ONLY B", FactoryStatus.ENABLED)));
        when(catalog.enabled(100, 100)).thenReturn(List.of());
        var coordinator = context.getBean(SyncCoordinator.class);
        when(coordinator.claim(any(), eq("workorder-completion"), any())).thenAnswer(invocation ->
                new SyncCoordinator.Run(invocation.getArgument(0), "workorder-completion", UUID.randomUUID()
                        .toString()));
        return new WorkOrderCompletionJob(catalog, (scope, task) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.current(context, scope.factoryId().value()), task), coordinator,
                context.getBean(WorkOrderCompletionService.class));
    }

    static String release(String qty) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY\"}";
    }

    static String handover(MockMvc m, String qty) throws Exception {
        String id = UUID.randomUUID().toString();
        m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT))
                        .content("{\"requestId\":\"" + id + "\",\"executionId\":1,\"warehouseId\":1,\"productLotCode\":\"TEST-FINISHED\",\"quantity\":\"" + qty + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    static void accept(MockMvc m, String receipt, String qty) throws Exception {
        m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/production-acceptances/receipts/" + receipt))
                        .content(WarehouseAcceptanceHttpTest.command(UUID.randomUUID().toString(), qty)))
                .andExpect(status().isOk());
    }
}
