package dev.issuefixture;


import dev.mesbase.business.integration.warehouse.WarehouseErpAdapter;
import dev.mesbase.business.integration.warehouse.MaterialIssueCancelCommand;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.core.factory.FactoryScope;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class MaterialIssueVoidHttpTest {
    static final String H = MaterialIssueHttpTest.H, W = "/api/production/workorder/1";

    static String voidPath(long id) {
        return H + "/" + id + "/actions/void";
    }

    static MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder r) {
        return r.header("Authorization", "Bearer FULL").header("X-Factory-Id", "A").contentType("application/json");
    }

    static String productChange() {
        return "{\"code\":\"WO1\",\"productId\":\"2\",\"plannedQty\":\"1000\",\"routingId\":\"3\",\"revision\":0}";
    }

    static void secondProduct(AnnotationConfigWebApplicationContext c) {
        var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
        j.update("INSERT INTO material(id,code,name,unit_id,material_type,status) VALUES(2,'M2','另一产品',1,'PRODUCT','ENABLED')");
        j.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R2','另一产品工艺',1,'PUBLISHED',0,CURRENT_TIMESTAMP)");
        j.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(3,2,'M2','另一产品')");
    }

    @Test
    void localVoidUnlocksProductButPreservesHistoryAndCannotBeForged() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            secondProduct(c);
            m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER.replace("RECORDED", "DRAFT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("externalEffect").value("NONE"));
            m.perform(full(put(W)).content(productChange())).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("WORKORDER_ISSUE_REFERENCED"));
            m.perform(full(put(H + "/1")).content(MaterialIssueHttpTest.HEADER.replace("RECORDED", "VOIDED")))
                    .andExpect(status().isBadRequest());
            m.perform(post(voidPath(1)).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(full(post(voidPath(1)))).andExpect(status().isOk()).andExpect(jsonPath("status").value("VOIDED"))
                    .andExpect(jsonPath("voidExecutionMode").value("LOCAL_ONLY"))
                    .andExpect(jsonPath("externalEffect").value("NONE"));
            var first = m.perform(full(get(H + "/1"))).andReturn().getResponse().getContentAsString();
            assertEquals(first, m.perform(full(post(voidPath(1)))).andReturn().getResponse().getContentAsString());
            m.perform(full(put(H + "/1")).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isConflict());
            m.perform(full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM))
                    .andExpect(status().isConflict());
            m.perform(full(put(W)).content(productChange())).andExpect(status().isOk())
                    .andExpect(jsonPath("workorder.productId").value("2"));
            m.perform(full(get(H + "/1"))).andExpect(status().isOk()).andExpect(jsonPath("productId").value("1"));
            m.perform(post(voidPath(1)).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void missingModeUnknownHistoryAndConnectedMissingAdapterFailClosed() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isOk());
            j.update("UPDATE mes_material_issue_erp_extra SET external_effect='UNVERIFIED',source_execution_mode=NULL WHERE id=1");
            m.perform(full(post(voidPath(1)))).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("ISSUE_EXTERNAL_UNVERIFIED"));
            j.update("UPDATE mes_material_issue_erp_extra SET external_effect='CONFIRMED',source_execution_mode='CONNECTED',external_binding_key='ORIGINAL',external_reference='ERP-1' WHERE id=1");
            m.perform(full(post(voidPath(1)))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("INTEGRATION_NOT_CONFIGURED"));
            assertEquals("RECORDED", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
            c.getEnvironment().getPropertySources().remove("TEST ONLY");
            m.perform(full(post(voidPath(1)))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("INTEGRATION_NOT_CONFIGURED"));
        }
    }

    static class TestAdapter implements WarehouseErpAdapter {
        final AtomicInteger writes = new AtomicInteger(), queries = new AtomicInteger();
        Status outcome = Status.SUCCEEDED;
        CountDownLatch entered, release;

        public boolean supports(FactoryScope s, String binding) {
            return binding.equals("TEST_" + s.factoryId().value());
        }

        public ErpActionResult cancelMaterialIssue(FactoryScope s, MaterialIssueCancelCommand c) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            writes.incrementAndGet();
            if (entered != null) {
                entered.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            return new ErpActionResult(outcome, Mode.CONNECTED, c.externalReference(), "TEST ONLY");
        }

        public ErpActionResult queryMaterialIssueCancellation(FactoryScope s, MaterialIssueCancelCommand c) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            queries.incrementAndGet();
            return new ErpActionResult(Status.SUCCEEDED, Mode.CONNECTED, c.externalReference(), "TEST ONLY verified");
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        TestAdapter testAdapter() {
            return new TestAdapter();
        }
    }

    static AnnotationConfigWebApplicationContext connected() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true", "mes.integration.warehouse.factories.A.mode", "CONNECTED", "mes.integration.warehouse.factories.B.mode", "LOCAL_ONLY")));
        c.register(MaterialIssueHttpTest.Web.class, TestConfig.class);
        c.refresh();
        return c;
    }

    static void source(AnnotationConfigWebApplicationContext c) {
        c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                .update("UPDATE mes_material_issue_erp_extra SET source_execution_mode='CONNECTED',external_effect='CONFIRMED',external_binding_key='TEST_A',external_reference='ERP-1' WHERE id=1");
    }

    @Test
    void externalResultsAndQueryReconciliationAreNotRepeatedWrites() throws Exception {
        for (Status outcome : Status.values())
            try (var c = connected()) {
                var m = MaterialIssueHttpTest.mvc(c);
                secondProduct(c);
                var adapter = c.getBean(TestAdapter.class);
                adapter.outcome = outcome;
                m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isOk());
                source(c);
                if (outcome == Status.SUCCEEDED)
                    c.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("CURRENT MODE SWITCH TEST", Map.of("mes.integration.warehouse.factories.A.mode", "LOCAL_ONLY")));
                m.perform(full(post(voidPath(1)))).andExpect(status().isOk())
                        .andExpect(jsonPath("voidResultStatus").value(outcome.name()));
                m.perform(full(post(voidPath(1)))).andExpect(status().isOk());
                assertEquals(1, adapter.writes.get());
                if (outcome != Status.SUCCEEDED)
                    m.perform(full(put(W)).content(productChange())).andExpect(status().isConflict());
                if (outcome == Status.UNKNOWN) {
                    m.perform(full(post(H + "/1/actions/reconcile-void"))).andExpect(status().isOk())
                            .andExpect(jsonPath("status").value("VOIDED"));
                    assertEquals(1, adapter.writes.get());
                    assertEquals(1, adapter.queries.get());
                }
            }
    }

    @Test
    void pendingClaimIsSingleOwnerAndBlocksDemandCorrection() throws Exception {
        try (var c = connected(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            secondProduct(c);
            var adapter = c.getBean(TestAdapter.class);
            adapter.entered = new CountDownLatch(1);
            adapter.release = new CountDownLatch(1);
            m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isOk());
            source(c);
            var first = pool.submit(() -> m.perform(full(post(voidPath(1)))).andReturn().getResponse().getStatus());
            assertTrue(adapter.entered.await(10, TimeUnit.SECONDS));
            m.perform(full(post(voidPath(1)))).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("VOID_PENDING"));
            m.perform(full(put(W)).content(productChange())).andExpect(status().isConflict());
            adapter.release.countDown();
            assertEquals(200, first.get(10, TimeUnit.SECONDS));
            assertEquals(1, adapter.writes.get());
        }
    }

    @Test
    void concurrentIssueCreationAndProductCorrectionKeepReferencesConsistent() throws Exception {
        try (var c = MaterialIssueHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            secondProduct(c);
            var start = new CountDownLatch(1);
            var issue = pool.submit(() -> {
                start.await();
                return m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER.replace("\"status\"", "\"productId\":\"1\",\"status\"")))
                        .andReturn().getResponse().getStatus();
            });
            var correction = pool.submit(() -> {
                start.await();
                return m.perform(full(put(W)).content(productChange())).andReturn().getResponse().getStatus();
            });
            start.countDown();
            int i = issue.get(10, TimeUnit.SECONDS), w = correction.get(10, TimeUnit.SECONDS);
            assertTrue(i == 200 && w == 409 || i == 400 && w == 200);
        }
    }
}
