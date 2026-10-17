package dev.issuefixture;

import dev.mesbase.business.integration.warehouse.WarehouseErpAdapter;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand;
import dev.mesbase.business.integration.warehouse.MaterialIssueCancelCommand;
import dev.mesbase.business.integration.warehouse.QueryMaterialIssueCommand;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.core.factory.FactoryScope;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import org.springframework.context.annotation.Primary;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.session.SqlSessionFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY vendor, real HTTP + generated SQL + two independent business databases.
 */
class ProductionCoreV1HttpTest {
    static final String E = "/api/production/mes-production-execution", A = "/api/production/executions/1/actions/", H = MaterialIssueHttpTest.H;
    static final String EXEC = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"12\",\"status\":\"READY\"}";
    static final String HEADER = "{\"issueNo\":\"TEST_CORE_1\",\"status\":\"DRAFT\",\"executionId\":\"1\"}";

    static MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer FULL").header("X-Factory-Id", "A").contentType("application/json");
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static void seed(AnnotationConfigWebApplicationContext c) throws Exception {
        var j = db(c);
        j.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_name,is_key,display_order) VALUES(1,1,'A',1,'相同工序',false,1),(2,1,'B',1,'相同工序',false,2),(3,1,'C',2,'分支C',false,3),(4,1,'D',3,'分支D',false,4),(5,1,'E',4,'终点',false,5)");
        j.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,1,2),(1,2,3),(1,2,4),(1,3,5),(1,4,5)");
        var m = MaterialIssueHttpTest.mvc(c);
        m.perform(full(post("/api/production/workorder/1/actions/release")).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        m.perform(full(post(E)).content(EXEC)).andExpect(status().isOk());
    }

    static void select(MockMvc m) throws Exception {
        m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[\"1\",\"2\",\"3\",\"5\"]}"))
                .andExpect(status().isOk());
    }

    static void issue(MockMvc m, int id) throws Exception {
        m.perform(full(post(H)).content(HEADER.replace("TEST_CORE_1", "TEST_CORE_" + id))).andExpect(status().isOk());
        m.perform(full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM.replace("\"issueId\":\"1\"", "\"issueId\":\"" + id + "\"")))
                .andExpect(status().isOk());
    }

    static class Vendor implements WarehouseErpAdapter {
        public boolean supportsProvider(String provider) { return "test".equals(provider); }

        volatile Status result = Status.SUCCEEDED;
        volatile Outcome outcome = Outcome.FOUND;
        volatile DocumentStatus document = DocumentStatus.EFFECTIVE;
        volatile CountDownLatch entered, release;
        volatile boolean pauseCancel, throwOnCreate;
        volatile MaterialIssueSubmitCommand snapshot;
        final AtomicInteger creates = new AtomicInteger(), cancels = new AtomicInteger();

        public boolean supports(FactoryScope scope, String key) {
            return key.equals("TEST_" + scope.factoryId().value());
        }

        void pause() {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (entered != null) {
                entered.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }

        public ErpActionResult createMaterialIssue(FactoryScope scope, MaterialIssueSubmitCommand command) {
            creates.incrementAndGet();
            snapshot = command;
            pause();
            if (throwOnCreate) throw new IllegalStateException("TEST ONLY timeout");
            return new ErpActionResult(result, Mode.CONNECTED, result == Status.SUCCEEDED ? "ERP-" + command.issueId() : null, result == Status.SUCCEEDED ? "DOC-" + command.issueId() : null, "PRIVATE secret raw response");
        }

        public ErpActionResult cancelMaterialIssue(FactoryScope scope, MaterialIssueCancelCommand command) {
            cancels.incrementAndGet();
            if (pauseCancel) pause();
            return new ErpActionResult(Status.SUCCEEDED, Mode.CONNECTED, command.externalReference(), "cancelled");
        }

        public ErpActionResult queryMaterialIssueCancellation(FactoryScope scope, MaterialIssueCancelCommand command) {
            return new ErpActionResult(Status.SUCCEEDED, Mode.CONNECTED, command.externalReference(), "cancelled");
        }

        public ErpIssueQueryResult queryMaterialIssue(FactoryScope scope, QueryMaterialIssueCommand command) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ErpIssueQueryResult(outcome, document, Mode.CONNECTED, "ERP-1", "DOC-1", "PRIVATE");
        }
    }

    @Configuration
    static class FaultConfig {
        @Bean
        AtomicBoolean failNextPathLock() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        MesExecutionPathMapper faultPathMapper(SqlSessionFactory sessions, AtomicBoolean failNextPathLock) {
            var delegate = new SqlSessionTemplate(sessions).getMapper(MesExecutionPathMapper.class);
            return (MesExecutionPathMapper) Proxy.newProxyInstance(MesExecutionPathMapper.class.getClassLoader(), new Class<?>[]{MesExecutionPathMapper.class}, (proxy, method, args) -> {
                if (method.getName()
                        .equals("updateByPrimaryKey") && "LOCKED".equals(((MesExecutionPathEntity) args[0]).getPathStatus()) && failNextPathLock.compareAndSet(true, false))
                    throw new IllegalStateException("TEST ONLY local phase-three persistence failure");
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        }
    }

    @Configuration
    static class VendorConfig {
        @Bean
        Vendor vendor() {
            return new Vendor();
        }
    }

    static AnnotationConfigWebApplicationContext connected(boolean vendor) {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true", "mes.integration.warehouse.factories.A.mode", "CONNECTED", "mes.integration.warehouse.factories.A.provider", "test", "mes.integration.warehouse.factories.A.binding-key", "TEST_A", "mes.integration.warehouse.factories.B.mode", "LOCAL_ONLY")));
        c.register(MaterialIssueHttpTest.Web.class);
        if (vendor) c.register(VendorConfig.class, FaultConfig.class);
        c.refresh();
        return c;
    }

    @Test
    void localFullFlowMultipleIssuesAndLifecycleCannotBeForged() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("CONFIRMED"))
                    .andExpect(jsonPath("externalReference").isEmpty());
            var locked = db(c).queryForObject("SELECT locked_at FROM mes_execution_path WHERE id=1", String.class);
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isOk());
            assertEquals(1, db(c).queryForObject("SELECT COUNT(*) FROM mes_material_issue_submission", Integer.class));
            issue(m, 2);
            m.perform(full(post(H + "/2/actions/submit"))).andExpect(status().isOk());
            assertEquals(locked, db(c).queryForObject("SELECT locked_at FROM mes_execution_path WHERE id=1", String.class));
            m.perform(full(post(A + "select-path")).content("{\"revision\":1,\"routingNodeIds\":[1,2,4,5]}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(A + "start"))).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("RUNNING"));
            var started = db(c).queryForObject("SELECT start_time FROM mes_production_execution WHERE id=1", String.class);
            m.perform(full(post(A + "start"))).andExpect(status().isOk());
            assertEquals(started, db(c).queryForObject("SELECT start_time FROM mes_production_execution WHERE id=1", String.class));
            m.perform(full(post(H + "/1/actions/void"))).andExpect(status().isConflict());
            m.perform(full(put(E + "/1")).content(EXEC.replace("READY", "COMPLETED"))).andExpect(status().isConflict());
            m.perform(full(post(E)).content(EXEC.replace("READY", "RUNNING")))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM production_batch", Integer.class));
        }
    }

    @Test
    void completePathUsesNodeIdentityAndRejectsGapsKeyBypassAndOldRevision() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            for (var path : List.of("[1,3,5]", "[2,3,5]", "[1,2,3]", "[1,2,2,3,5]", "[1,2,999,5]"))
                m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":" + path + "}"))
                        .andExpect(status().isUnprocessableEntity());
            db(c).update("UPDATE routing_node SET is_key=true WHERE id=3");
            m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[1,2,4,5]}"))
                    .andExpect(status().isUnprocessableEntity());
            select(m);
            assertEquals(List.of(1L, 2L, 3L, 5L), db(c).queryForList("SELECT routing_node_id FROM mes_execution_path_node ORDER BY sequence_no", Long.class));
            m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[1,2,3,5]}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(A + "start"))).andExpect(status().isConflict());
            m.perform(full(get("/api/production/executions/1/path")))
                    .andExpect(jsonPath("nodes[0].operationName").value("相同工序"));
        }
    }

    @Test
    void draftNeedsExecutionItemsAndValidQuantitiesAndDoesNotCreatePrematureAttempts() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            m.perform(full(post(H)).content(HEADER)).andExpect(status().isOk());
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isBadRequest());
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM mes_material_issue_submission", Integer.class));
            m.perform(full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM.replace("99999999999999.123456", "0")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM))
                    .andExpect(status().isOk());
            db(c).update("UPDATE mes_material_issue_item SET quantity=-1 WHERE id=1");
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isBadRequest());
            m.perform(full(post(H)).content(MaterialIssueHttpTest.HEADER)).andExpect(status().isOk());
            m.perform(full(post(H + "/2/actions/submit"))).andExpect(status().isConflict());
            m.perform(full(post(H)).content(HEADER.replace("\"executionId\":\"1\"", "\"executionId\":\"1\",\"productId\":\"999\"")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void connectedMissingImplementationFailsBeforeIntentAndNeverFallsBack() throws Exception {
        try (var c = connected(false)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isUnprocessableEntity());
            assertEquals("DRAFT", db(c).queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM mes_material_issue_submission", Integer.class));
        }
    }

    @Test
    void rejectedHasControlledReopenAndKeepsAttemptHistory() throws Exception {
        try (var c = connected(true)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            v.result = Status.REJECTED;
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(jsonPath("status").value("SUBMIT_REJECTED"));
            String key = v.snapshot.operationKey();
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isOk());
            assertEquals(1, v.creates.get());
            assertEquals("SELECTED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
            m.perform(full(post(H + "/1/actions/reopen-rejected"))).andExpect(jsonPath("status").value("DRAFT"));
            v.result = Status.SUCCEEDED;
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(jsonPath("status").value("CONFIRMED"));
            assertNotEquals(key, v.snapshot.operationKey());
            assertEquals(2, db(c).queryForObject("SELECT COUNT(*) FROM mes_material_issue_submission", Integer.class));
            assertTrue(db(c).queryForObject("SELECT command_json FROM mes_material_issue_submission ORDER BY id DESC LIMIT 1", String.class)
                    .contains("99999999999999.123456"));
            assertEquals("ERP-1", db(c).queryForObject("SELECT external_reference FROM mes_material_issue_erp_extra WHERE id=1", String.class));
        }
    }

    @Test
    void timeoutAndNotFoundDoNotResubmitAndEffectiveQueryLocksOriginalPath() throws Exception {
        try (var c = connected(true)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            v.throwOnCreate = true;
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(jsonPath("status").value("SUBMIT_UNKNOWN"));
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isOk());
            assertEquals(1, v.creates.get());
            for (var outcome : List.of(Outcome.UNKNOWN, Outcome.NOT_FOUND)) {
                v.outcome = outcome;
                m.perform(full(post(H + "/1/actions/reconcile-erp")))
                        .andExpect(jsonPath("mesStatus").value("SUBMIT_UNKNOWN"));
            }
            v.outcome = Outcome.FOUND;
            v.document = DocumentStatus.PENDING_APPROVAL;
            m.perform(full(post(H + "/1/actions/reconcile-erp")))
                    .andExpect(jsonPath("mesStatus").value("SUBMIT_UNKNOWN"));
            v.document = DocumentStatus.EFFECTIVE;
            m.perform(full(post(H + "/1/actions/reconcile-erp"))).andExpect(jsonPath("mesStatus").value("CONFIRMED"));
            assertEquals("LOCKED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
            assertEquals(1, v.creates.get());
        }
    }

    @Test
    void submissionInFlightFreezesPayloadPathAndStartAndRepeatedClicksShareIdentity() throws Exception {
        try (var c = connected(true); var pool = Executors.newFixedThreadPool(2)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            v.entered = new CountDownLatch(1);
            v.release = new CountDownLatch(1);
            var pending = pool.submit(() -> m.perform(full(post(H + "/1/actions/submit"))).andReturn().getResponse()
                    .getStatus());
            assertTrue(v.entered.await(10, TimeUnit.SECONDS));
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(jsonPath("status").value("SUBMITTING"));
            m.perform(full(post(A + "select-path")).content("{\"revision\":1,\"routingNodeIds\":[1,2,4,5]}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(A + "start"))).andExpect(status().isConflict());
            m.perform(full(put(H + "/1")).content(HEADER)).andExpect(status().isConflict());
            m.perform(full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM))
                    .andExpect(status().isConflict());
            v.release.countDown();
            assertEquals(200, pending.get(10, TimeUnit.SECONDS));
            assertEquals(1, v.creates.get());
            assertEquals(1, v.snapshot.lines().size());
        }
    }

    @Test
    void voidInFlightBlocksStartAndSuccessfulVoidNeverUnlocksPath() throws Exception {
        try (var c = connected(true); var pool = Executors.newFixedThreadPool(2)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isOk());
            v.pauseCancel = true;
            v.entered = new CountDownLatch(1);
            v.release = new CountDownLatch(1);
            var pending = pool.submit(() -> m.perform(full(post(H + "/1/actions/void"))).andReturn().getResponse()
                    .getStatus());
            assertTrue(v.entered.await(10, TimeUnit.SECONDS));
            m.perform(full(post(A + "start"))).andExpect(status().isConflict());
            v.release.countDown();
            assertEquals(200, pending.get(10, TimeUnit.SECONDS));
            m.perform(full(post(A + "start"))).andExpect(status().isConflict());
            assertEquals("LOCKED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
        }
    }

    @Test
    void newActionsEnforceFactoryAndIndependentPermissions() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            m.perform(post(H + "/1/actions/submit").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(post(H + "/1/actions/submit").header("Authorization", "Bearer FULL").header("X-Factory-Id", "B")
                    .contentType("application/json")).andExpect(status().isNotFound());
            m.perform(get("/api/production/executions/1/path")).andExpect(status().isForbidden());
            assertEquals(0, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_execution_path", Integer.class));
        }
    }

    @Test
    void concurrentIssuesConfirmOneImmutablePathAndNeverReplaceItsLock() throws Exception {
        try (var c = connected(true); var pool = Executors.newFixedThreadPool(2)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            issue(m, 2);
            var v = c.getBean(Vendor.class);
            v.entered = new CountDownLatch(2);
            v.release = new CountDownLatch(1);
            var first = pool.submit(() -> m.perform(full(post(H + "/1/actions/submit"))).andReturn().getResponse()
                    .getStatus());
            var second = pool.submit(() -> m.perform(full(post(H + "/2/actions/submit"))).andReturn().getResponse()
                    .getStatus());
            assertTrue(v.entered.await(10, TimeUnit.SECONDS));
            v.release.countDown();
            assertEquals(200, first.get(10, TimeUnit.SECONDS));
            assertEquals(200, second.get(10, TimeUnit.SECONDS));
            assertEquals(2, db(c).queryForObject("SELECT COUNT(*) FROM mes_material_issue WHERE status='CONFIRMED'", Integer.class));
            assertEquals(4, db(c).queryForObject("SELECT COUNT(*) FROM mes_execution_path_node WHERE execution_id=1", Integer.class));
            assertEquals(1, db(c).queryForObject("SELECT revision FROM mes_execution_path WHERE id=1", Integer.class));
            assertEquals("LOCKED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
        }
    }

    @Test
    void resultAfterNewerReconcileDoesNotOverwriteAndOriginalOperationRemainsRecoverable() throws Exception {
        try (var c = connected(true); var pool = Executors.newFixedThreadPool(2)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            v.entered = new CountDownLatch(1);
            v.release = new CountDownLatch(1);
            v.outcome = Outcome.UNKNOWN;
            var pending = pool.submit(() -> m.perform(full(post(H + "/1/actions/submit"))).andReturn().getResponse()
                    .getStatus());
            assertTrue(v.entered.await(10, TimeUnit.SECONDS));
            m.perform(full(post(H + "/1/actions/reconcile-erp"))).andExpect(status().isOk());
            v.release.countDown();
            assertEquals(200, pending.get(10, TimeUnit.SECONDS));
            assertEquals("SUBMITTING", db(c).queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
            v.outcome = Outcome.FOUND;
            v.document = DocumentStatus.EFFECTIVE;
            m.perform(full(post(H + "/1/actions/reconcile-erp"))).andExpect(jsonPath("mesStatus").value("CONFIRMED"));
            assertEquals(1, v.creates.get());
            assertEquals("LOCKED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
        }
    }

    @Test
    void externalSuccessWithLocalCommitFailureIsRecoverableWithoutAnotherCreate() throws Exception {
        try (var c = connected(true)) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            select(m);
            issue(m, 1);
            var v = c.getBean(Vendor.class);
            c.getBean(AtomicBoolean.class).set(true);
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(status().isInternalServerError());
            assertEquals("SUBMITTING", db(c).queryForObject("SELECT status FROM mes_material_issue WHERE id=1", String.class));
            assertEquals("SELECTED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
            assertEquals(1, v.creates.get());
            var before = db(c).queryForList("SELECT * FROM mes_material_issue_erp_extra WHERE id=1");
            m.perform(full(get(H + "/1/erp-status"))).andExpect(status().isOk());
            assertEquals(before, db(c).queryForList("SELECT * FROM mes_material_issue_erp_extra WHERE id=1"));
            m.perform(full(post(H + "/1/actions/submit"))).andExpect(jsonPath("status").value("SUBMITTING"));
            assertEquals(1, v.creates.get());
            m.perform(full(post(H + "/1/actions/reconcile-erp"))).andExpect(jsonPath("mesStatus").value("CONFIRMED"));
            assertEquals("LOCKED", db(c).queryForObject("SELECT path_status FROM mes_execution_path WHERE id=1", String.class));
            assertEquals("ERP-1", db(c).queryForObject("SELECT external_reference FROM mes_material_issue_erp_extra WHERE id=1", String.class));
            assertEquals(1, v.creates.get());
        }
    }
}
