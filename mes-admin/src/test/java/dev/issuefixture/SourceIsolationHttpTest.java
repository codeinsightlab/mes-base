package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real controllers, mapper SQL and transaction boundaries on two independent H2 databases.
 */
class SourceIsolationHttpTest {
    @Test
    void localWorkorderCreationHasTrustedSourceAndForgedSourceCannotOverrideIt() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String body = "{\"ruleCode\":\"WORK_ORDER\",\"code\":\"TEST_SOURCE_NEW\",\"productId\":1,\"plannedQty\":\"100\",\"routingId\":1}";
            body = WorkorderMaterialFixture.command(ProductionInputHttpTest.db(c), body, null);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.source").value("LOCAL"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder"))
                            .content(body.replace("TEST_SOURCE_NEW", "TEST_FORGED")
                                    .replace("\"routingId\":1", "\"routingId\":1,\"source\":\"EXTERNAL\"")))
                    .andExpect(status().isBadRequest());
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "TEST_PROVIDER", "TEST_A"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder"))
                            .content(body.replace("TEST_SOURCE_NEW", "TEST_MODE_BYPASS")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
        }
    }

    @Test
    void mixedRawWarehouseAndUnknownOrderCannotPreviewOrSubmitProduction() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/combinations"))
                            .content("{\"workorderId\":\"1\",\"quantity\":\"1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            j.update("UPDATE workorder SET source=NULL WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/combinations"))
                            .content("{\"workorderId\":\"1\",\"quantity\":\"1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals("10000.000000", j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)
                    .toPlainString());
        }
    }

    @Test
    void finishedWarehouseMismatchAndInboundBypassAreRejectedWithoutStockChanges() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            String document = UnifiedInboundHttpTest.draft(m, "1", "10");
            j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(UnifiedInboundHttpTest.approval(m, document)))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void trustedWorkorderSyncCreatesExternalAndRejectsLocalCodeAndBindingTakeover() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var service = c.getBean(WorkOrderService.class);
            var j = ProductionInputHttpTest.db(c);
            var scope = new FactoryScope(new FactoryId("A"));
            var snapshot = new FactoryIntegrationSnapshot(scope.factoryId(), FactoryRuntimeMode.ERP, "TEST-ERP", "TEST-BINDING", 1, 1L);
            var run = new SyncCoordinator.Run(scope, "workorder.erp", "TEST-OWNER");
            var input = new WorkOrderModels.Save("TEST_EXTERNAL_SYNC", 1L, new BigDecimal("100"), 1L, null, null, null, null, null, null, null);
            FactoryIntegrationContext.run(snapshot, () -> {
                var withoutRouting = new WorkOrderModels.Save("TEST_EXTERNAL_NO_ROUTE", 1L, new BigDecimal("100"), null, null, null, null, null, null, null, null);
                assertThrows(FoundationException.class, () -> service.inspectExternal(new WorkOrderService.ExternalPlan(withoutRouting, "ERP-NO-ROUTE", "ERP-NO-ROUTE", WorkOrderStatus.RELEASED)));
                var result = service.syncExternal(new WorkOrderService.ExternalPlan(input, "ERP-ORDER-1", "ERP-MO-1", WorkOrderStatus.RELEASED), run);
                assertEquals("EXTERNAL", result.workorder().getSource());
                assertEquals("TEST-BINDING", j.queryForObject("SELECT binding_key FROM workorder_erp_extra WHERE id=?", String.class, result.workorder()
                        .getId()));
                var collision = new WorkOrderModels.Save("WO1", 1L, new BigDecimal("100"), 1L, null, null, null, null, null, null, null);
                assertThrows(FoundationException.class, () -> service.syncExternal(new WorkOrderService.ExternalPlan(collision, "OTHER-ERP-ID", null, WorkOrderStatus.RELEASED), run));
                assertThrows(FoundationException.class, () -> service.update(result.workorder().getId(), input));
            });
            assertEquals("LOCAL", j.queryForObject("SELECT source FROM workorder WHERE id=1", String.class));
        }
    }

}
