package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.acceptance.QueuedCompletionExecutor;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.ExecutionScope;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.server.WorkOrderCompletionJob;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import java.math.BigDecimal;
import dev.mesbase.core.factory.FactoryId;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY actual HTTP, mapped SQL, post-commit listener and fresh worker against isolated H2.
 */
class AutomaticProductionCompletionHttpTest {
    @Configuration(proxyBeanMethods = false)
    static class Async {
        @Bean
        QueuedCompletionExecutor completionQueue() {
            return new QueuedCompletionExecutor();
        }

        @Bean
        WorkOrderCompletionJob completionJob(ApplicationContext context, QueuedCompletionExecutor tasks,
                                             SyncCoordinator sync, WorkOrderCompletionService workorders,
                                             ProductionCompletionService executions) {
            when(sync.claim(any(), any(), any())).thenAnswer(i -> new SyncCoordinator.Run(i.getArgument(0), "workorder-completion", UUID.randomUUID()
                    .toString()));
            return new WorkOrderCompletionJob(mock(FactoryCatalog.class), (scope, task) -> FactoryIntegrationContext.run(
                    FactorySnapshotFixture.current(context, scope.factoryId()
                            .value()), task), sync, workorders, executions, tasks);
        }
    }

    @Test
    void lastFormalReportAutomaticallyCompletesExecutionAndParentWithoutReleaseOrWarehouse() throws Exception {
        try (var c = ProductionWarehouseFixture.context(Async.class, ProductionReportingHttpTest.Extra.class);
             var workers = Executors.newSingleThreadExecutor()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            j.update("DELETE FROM mes_production_report WHERE routing_node_id=13");
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var tasks = c.getBean(QueuedCompletionExecutor.class);
            String request = UUID.randomUUID().toString();
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReportingHttpTest.ROOT))
                    .content(ProductionReportingHttpTest.body(13, "80", request))).andExpect(status().isOk());
            assertEquals(1, tasks.size());
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            workers.submit(tasks::runAll).get();
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/executions/1/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("canComplete").value(false));
            ProductionReportingHttpTest.submit(m, 13, "20");
            workers.submit(tasks::runAll).get();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_warehouse_acceptance", Integer.class));
            assertTrue(j.queryForObject("SELECT reason FROM mes_execution_completion", String.class)
                    .startsWith("系统自动"));
            // Original HTTP UUID remains replayable after both parent and child complete; no new hint/fact.
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReportingHttpTest.ROOT))
                    .content(ProductionReportingHttpTest.body(13, "80", request))).andExpect(status().isOk());
            assertEquals(0, tasks.size());
            // Production completion cannot manufacture stock or waive explicit output release.
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            String document = UnifiedInboundHttpTest.draft(m, "1", "100");
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/post"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("STOCK_DOCUMENT_BASIS_INVALID"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1"))
                    .content(ProductionCompletionHttpTest.release("100"))).andExpect(status().isOk());
            UnifiedInboundHttpTest.confirm(m, document, 1);
            assertEquals(0, j.queryForObject("SELECT accepted_qty FROM mes_execution_completion", BigDecimal.class)
                    .signum());
        }
    }

    @Test
    void missedHintsAreRecoveredByTheSweepAndOldLeaseCannotCommitExecutionCompletion() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var j = ProductionWarehouseFixture.db(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            var sync = c.getBean(SyncCoordinator.class);
            var job = ProductionCompletionHttpTest.job(c);
            var count = new AtomicInteger();
            doAnswer(i -> {
                if (count.incrementAndGet() == 3) throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
                return null;
            }).when(sync).assertOwner(any());
            job.scheduled();
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            doNothing().when(sync).assertOwner(any());
            job.scheduled();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            job.scheduled();
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @Test
    void pendingQualityBlocksAutomaticCompletionAndFinalQualityResultTriggersIt() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(new Class<?>[]{Async.class}, 100, 100, 100, 100, 100);
             var workers = Executors.newSingleThreadExecutor()) {
            var j = ProductionWarehouseFixture.db(c);
            j.update("DELETE FROM mes_production_execution WHERE id=2");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            var m = MaterialIssueHttpTest.mvc(c);
            var tasks = c.getBean(QueuedCompletionExecutor.class);
            String defect = QualityWorkflowHttpTest.defect(m, 12, "10", "INDEPENDENT", null);
            QualityDispositionHttpTest.bindBatch(j, "000LOT-001");
            workers.submit(tasks::runAll).get();
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            QualityWorkflowHttpTest.transfer(m, defect, "10");
            workers.submit(tasks::runAll).get();
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(new BigDecimal("90.000000"), j.queryForObject("SELECT effective_qty FROM mes_execution_completion", BigDecimal.class));
        }
    }

    @Test
    void onlyCommittedTransactionalEventsEnqueueAndRollbackIsSilent() throws Exception {
        try (var c = ProductionWarehouseFixture.context(Async.class)) {
            var tasks = c.getBean(QueuedCompletionExecutor.class);
            var scope = new FactoryScope(new FactoryId("A"));
            var tx = new TransactionTemplate(c.getBean("businessTransactionManager", PlatformTransactionManager.class));
            ExecutionScope.factory(scope)
                    .run(() -> FactoryIntegrationContext.run(FactorySnapshotFixture.local("A"), () -> {
                        c.publishEvent(new ProductionCompletionRequested(scope, 1L));
                        assertEquals(0, tasks.size());
                        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                            c.publishEvent(new ProductionCompletionRequested(scope, 1L));
                            assertEquals(0, tasks.size());
                            throw new IllegalStateException("TEST ONLY rollback");
                        }));
                        assertEquals(0, tasks.size());
                        tx.executeWithoutResult(status -> {
                            c.publishEvent(new ProductionCompletionRequested(scope, 1L));
                            assertEquals(0, tasks.size());
                        });
                        assertEquals(1, tasks.size());
                    }));
        }
    }
}
