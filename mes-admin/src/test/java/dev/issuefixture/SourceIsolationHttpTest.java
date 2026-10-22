package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.service.WorkOrderService;
import java.util.UUID;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: real controllers, mapper SQL and transaction boundaries on two independent H2 databases.
 */
class SourceIsolationHttpTest {
    @Test
    void localWorkorderCreationHasTrustedSourceAndForgedSourceCannotOverrideIt() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String body = "{\"code\":\"TEST_SOURCE_NEW\",\"productId\":1,\"plannedQty\":\"100\",\"routingId\":1}";
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.source").value("LOCAL"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/workorder"))
                            .content(body.replace("TEST_SOURCE_NEW", "TEST_FORGED")
                                    .replace("\"routingId\":1", "\"routingId\":1,\"source\":\"EXTERNAL\"")))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.source").value("LOCAL"));
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
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/input/calculate")).param("code", "M1#L001")
                            .param("workorderId", "1").param("stockReference", "1").param("outputQty", "1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            j.update("UPDATE workorder SET source=NULL WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/input/calculate")).param("code", "M1#L001")
                            .param("workorderId", "1").param("stockReference", "1").param("outputQty", "1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals("10000.0000", j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)
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
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/approve"))
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
            var input = new WorkOrderModels.Save("TEST_EXTERNAL_SYNC", 1L, new BigDecimal("100"), 1L, null, null, null, null, null, null);
            FactoryIntegrationContext.run(snapshot, () -> {
                var result = service.syncExternal(new WorkOrderService.ExternalPlan(input, "ERP-ORDER-1", "ERP-MO-1", WorkOrderStatus.RELEASED), run);
                assertEquals("EXTERNAL", result.workorder().getSource());
                assertEquals("TEST-BINDING", j.queryForObject("SELECT binding_key FROM workorder_erp_extra WHERE id=?", String.class, result.workorder()
                        .getId()));
                var collision = new WorkOrderModels.Save("WO1", 1L, new BigDecimal("100"), 1L, null, null, null, null, null, null);
                assertThrows(FoundationException.class, () -> service.syncExternal(new WorkOrderService.ExternalPlan(collision, "OTHER-ERP-ID", null, WorkOrderStatus.RELEASED), run));
                assertThrows(FoundationException.class, () -> service.update(result.workorder().getId(), input));
                assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED, assertThrows(FoundationException.class, () -> c.getBean(WarehouseIntegration.class)
                        .submissionSelection(scope)).code());
            });
            assertEquals("LOCAL", j.queryForObject("SELECT source FROM workorder WHERE id=1", String.class));
        }
    }

}
