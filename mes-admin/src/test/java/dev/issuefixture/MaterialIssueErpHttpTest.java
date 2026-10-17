package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import dev.mesbase.business.integration.warehouse.WarehouseErpAdapter;
import dev.mesbase.business.integration.warehouse.MaterialIssueCancelCommand;
import dev.mesbase.business.integration.warehouse.QueryMaterialIssueCommand;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.core.factory.FactoryScope;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static dev.issuefixture.MaterialIssueVoidHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: HTTP + two isolated H2 factories; no real ERP.
 */
class MaterialIssueErpHttpTest {
    static final String H = MaterialIssueHttpTest.H, Q = H + "/1/erp-status", R = H + "/1/actions/reconcile-erp";

    static class Vendor implements WarehouseErpAdapter {
        public boolean supportsProvider(String provider) {
            return "test".equals(provider);
        }

        Outcome outcome = Outcome.FOUND;
        DocumentStatus state = DocumentStatus.EFFECTIVE;
        CountDownLatch entered, release;
        boolean timeout;

        public boolean supports(FactoryScope s, String key) {
            return key.equals("TEST_" + s.factoryId().value());
        }

        public ErpActionResult cancelMaterialIssue(FactoryScope s, MaterialIssueCancelCommand c) {
            return new ErpActionResult(Status.UNKNOWN, Mode.CONNECTED, c.externalReference(), "private vendor message");
        }

        public ErpActionResult queryMaterialIssueCancellation(FactoryScope s, MaterialIssueCancelCommand c) {
            return cancelMaterialIssue(s, c);
        }

        public ErpIssueQueryResult queryMaterialIssue(FactoryScope s, QueryMaterialIssueCommand c) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (entered != null) {
                entered.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (timeout) throw new IllegalStateException("TEST ONLY timeout");
            return new ErpIssueQueryResult(outcome, state, Mode.CONNECTED, "ERP-1", "ERP-CODE-1", "token=PRIVATE raw vendor message");
        }
    }

    @Configuration
    static class VendorConfig {
        @Bean
        Vendor vendor() {
            return new Vendor();
        }
    }

    static AnnotationConfigWebApplicationContext connected() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, VendorConfig.class);
        c.refresh();
        FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "test", "TEST_A"));
        MaterialIssueHttpTest.externalFixture(c);
        return c;
    }

    static void seed(AnnotationConfigWebApplicationContext c, String state) throws Exception {
        MaterialIssueHttpTest.mvc(c).perform(full(post(H)).content(MaterialIssueHttpTest.HEADER))
                .andExpect(status().isOk());
        var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
        j.update("UPDATE mes_material_issue_erp_extra SET source_execution_mode='CONNECTED',provider='test',external_effect='UNKNOWN',external_binding_key='TEST_A',external_reference='ERP-1' WHERE id=1");
        j.update("UPDATE mes_material_issue SET status=? WHERE id=1", state);
    }

    @Test
    void migratedOpaqueBindingDoesNotBecomeAVerifiedProvider() throws Exception {
        try (var c = connected()) {
            seed(c, "ERP_UNKNOWN");
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            String migration = Files.readString(Path.of("mes-admin/src/main/resources/db/business/V22__unverified_legacy_erp_provider.sql"));
            j.execute(migration);
            assertEquals("test", j.queryForObject("SELECT provider FROM mes_material_issue_erp_extra WHERE id=1", String.class));
            j.update("UPDATE mes_material_issue_erp_extra SET provider=external_binding_key,last_operation=NULL WHERE id=1");
            j.execute(migration);
            MaterialIssueHttpTest.mvc(c).perform(full(get(Q))).andExpect(status().isConflict());
            assertEquals("ERP-1", j.queryForObject("SELECT external_reference FROM mes_material_issue_erp_extra WHERE id=1", String.class));
        }
    }

    @Test
    void workorderExtraFollowsTransactionalCreationAndDeletion() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String body = "{\"code\":\"WO_EXTRA_TEST\",\"productId\":1,\"routingId\":1,\"plannedQty\":10}";
            m.perform(full(post("/api/production/workorder")).content(body.replace("10}", "10,\"externalId\":\"FORGED\"}")))
                    .andExpect(status().isConflict());
            m.perform(full(post("/api/production/workorder")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.source").value("LOCAL"));
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE id=2 AND external_id IS NULL AND provider IS NULL", Integer.class));
            m.perform(full(delete("/api/production/workorder/2")).content("{\"revision\":0}"))
                    .andExpect(status().isNoContent());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE id=2", Integer.class));
        }
    }

    @Test
    void localQueryIsReadOnlyAndPermissionsAndScopeAreIndependent() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isOk());
            m.perform(full(get(Q))).andExpect(status().isOk())
                    .andExpect(jsonPath("result.executionMode").value("LOCAL_ONLY"))
                    .andExpect(jsonPath("result.outcome").value("NOT_FOUND"))
                    .andExpect(jsonPath("result.externalId").isEmpty());
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            assertEquals(0L, j.queryForObject("SELECT operation_version FROM mes_material_issue_erp_extra WHERE id=1", Long.class));
            m.perform(post(R).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(get(Q).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(get(Q).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            m.perform(get(Q).header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            m.perform(full(post(R))).andExpect(status().isOk());
            assertEquals("RECORDED", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
        }
    }

    @Test
    void foundNotFoundUnknownAndTimeoutPreserveFactsAndOnlyEffectiveAdvancesPending() throws Exception {
        for (Outcome outcome : Outcome.values())
            try (var c = connected()) {
                seed(c, "ERP_UNKNOWN");
                var v = c.getBean(Vendor.class);
                v.outcome = outcome;
                var m = MaterialIssueHttpTest.mvc(c);
                String text = m.perform(full(get(Q))).andExpect(status().isOk())
                        .andExpect(jsonPath("result.outcome").value(outcome.name())).andReturn().getResponse()
                        .getContentAsString();
                assertFalse(text.contains("PRIVATE"));
                m.perform(full(post(R))).andExpect(status().isOk());
                m.perform(full(post(R))).andExpect(status().isOk());
                var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
                assertEquals(outcome == Outcome.FOUND ? "RECORDED" : "ERP_UNKNOWN", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
                assertEquals("ERP-1", j.queryForObject("SELECT external_reference FROM mes_material_issue_erp_extra WHERE id=1", String.class));
                assertEquals(outcome.name(), j.queryForObject("SELECT last_outcome FROM mes_material_issue_erp_extra WHERE id=1", String.class));
                v.timeout = true;
                m.perform(full(get(Q))).andExpect(status().isOk())
                        .andExpect(jsonPath("result.outcome").value("UNKNOWN"));
            }
    }

    @Test
    void cancelledReconcilesVoidButEffectiveNeverResurrectsVoidedAndPendingApprovalDoesNotAdvance() throws Exception {
        try (var c = connected()) {
            seed(c, "VOID_UNKNOWN");
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            j.update("UPDATE mes_material_issue SET void_operation_id='stable' WHERE id=1");
            var m = MaterialIssueHttpTest.mvc(c);
            var v = c.getBean(Vendor.class);
            v.state = DocumentStatus.CANCELLED;
            m.perform(full(post(R))).andExpect(status().isOk());
            assertEquals("VOIDED", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
            assertEquals("REVERSED", j.queryForObject("SELECT external_effect FROM mes_material_issue_erp_extra WHERE id=1", String.class));
            v.state = DocumentStatus.EFFECTIVE;
            m.perform(full(post(R))).andExpect(status().isOk())
                    .andExpect(jsonPath("lastMessage").value("ERP与MES状态冲突，保持MES状态，需项目级人工处理"));
            assertEquals("VOIDED", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
        }
        try (var c = connected()) {
            seed(c, "ERP_UNKNOWN");
            c.getBean(Vendor.class).state = DocumentStatus.PENDING_APPROVAL;
            MaterialIssueHttpTest.mvc(c).perform(full(post(R))).andExpect(status().isOk());
            assertEquals("ERP_UNKNOWN", c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
        }
    }

    @Test
    void lateEffectiveQueryCannotOverwriteNewVoidOperation() throws Exception {
        try (var c = connected(); var pool = Executors.newFixedThreadPool(2)) {
            seed(c, "RECORDED");
            var m = MaterialIssueHttpTest.mvc(c);
            var v = c.getBean(Vendor.class);
            v.entered = new CountDownLatch(1);
            v.release = new CountDownLatch(1);
            var pending = pool.submit(() -> m.perform(full(post(R))).andReturn().getResponse().getStatus());
            assertTrue(v.entered.await(10, TimeUnit.SECONDS));
            m.perform(full(post(MaterialIssueVoidHttpTest.voidPath(1)))).andExpect(status().isOk());
            v.release.countDown();
            assertEquals(409, pending.get(10, TimeUnit.SECONDS));
            assertEquals("VOID_UNKNOWN", c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
        }
    }

    @Test
    void providerSwitchKeepsHistoricalConnectedProviderAndBinding() throws Exception {
        try (var c = connected()) {
            seed(c, "ERP_UNKNOWN");
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.local("A"));
            MaterialIssueHttpTest.mvc(c).perform(full(get(Q))).andExpect(status().isOk())
                    .andExpect(jsonPath("result.executionMode").value("CONNECTED"));
        }
    }
}
