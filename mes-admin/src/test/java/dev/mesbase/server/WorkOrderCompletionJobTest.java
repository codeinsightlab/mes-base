package dev.mesbase.server;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.acceptance.QueuedCompletionExecutor;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.List;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.business.production.domain.WorkOrderCompletionRequested;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderCompletionJobTest {
    @Test
    void aDatabaseClaimConflictStopsThisFactoryAndErpFactoriesAreNotLocallyCompleted() {
        var tasks = new QueuedCompletionExecutor();
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A"), factory("B")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        when(sync.claim(eq(new FactoryScope(new FactoryId("A"))), eq("workorder-completion"), any()))
                .thenThrow(new FoundationException(ErrorCode.SYNC_RUNNING));
        var job = new WorkOrderCompletionJob(factories, (scope, task) -> FactoryIntegrationContext.run(
                "A".equals(scope.factoryId().value()) ? FactorySnapshotFixture.local("A")
                        : FactorySnapshotFixture.erp("B", "TEST_ONLY", "binding"), task), sync, completion, mock(ProductionCompletionService.class), tasks);
        job.scheduled();
        tasks.runAll();
        verify(completion, never()).candidates(any());
        verify(sync, never()).claim(eq(new FactoryScope(new FactoryId("B"))), any(), any());
    }

    @Test
    void losingExecutionOwnershipStopsTheRemainingCandidatesAndRecordsFailure() {
        var tasks = new QueuedCompletionExecutor();
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        var run = new SyncCoordinator.Run(new FactoryScope(new FactoryId("A")), "workorder-completion", "TEST-TOKEN");
        when(sync.claim(any(), any(), any())).thenReturn(run);
        when(completion.candidates(0L)).thenReturn(List.of(1L, 2L));
        when(completion.reconcile(1L, run)).thenThrow(new FoundationException(ErrorCode.SYNC_EXECUTION_LOST));
        new WorkOrderCompletionJob(factories, (scope, task) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.local("A"), task), sync, completion, mock(ProductionCompletionService.class), tasks).scheduled();
        tasks.runAll();
        verify(completion, never()).reconcile(2L, run);
        verify(sync).fail(run);
        verify(sync, never()).succeed(any(), any());
    }

    @Test
    void anInvalidOldCandidateDoesNotStarveLaterBalancedOrders() {
        var tasks = new QueuedCompletionExecutor();
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        var run = new SyncCoordinator.Run(new FactoryScope(new FactoryId("A")), "workorder-completion", "TEST-TOKEN");
        when(sync.claim(any(), any(), any())).thenReturn(run);
        when(completion.candidates(0L)).thenReturn(List.of(1L, 2L));
        when(completion.candidates(2L)).thenReturn(List.of());
        when(completion.reconcile(1L, run)).thenThrow(new FoundationException(ErrorCode.REPORT_PATH_REQUIRED));
        when(completion.reconcile(2L, run)).thenReturn(true);
        new WorkOrderCompletionJob(factories, (scope, task) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.local("A"), task), sync, completion, mock(ProductionCompletionService.class), tasks).scheduled();
        tasks.runAll();
        verify(completion).reconcile(2L, run);
        verify(sync, atLeastOnce()).progress(run, 2);
        verify(sync).succeed(run, null);
        verify(sync, never()).fail(any());
    }

    @Test
    void distinctFactoriesRunInParallelWithFreshIsolatedFactoryContexts() throws Exception {
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A"), factory("B")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        var entered = new CountDownLatch(2);
        var finished = new CountDownLatch(2);
        when(sync.claim(any(), any(), any())).thenAnswer(i -> {
            FactoryScope scope = i.getArgument(0);
            assertEquals(scope, FactoryContext.required());
            assertEquals(scope.factoryId(), FactoryIntegrationContext.required().factoryId());
            entered.countDown();
            if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Factories blocked one another");
            return new SyncCoordinator.Run(scope, "workorder-completion", "TEST-" + scope.factoryId().value());
        });
        doAnswer(i -> {
            finished.countDown();
            return null;
        }).when(sync).succeed(any(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var job = new WorkOrderCompletionJob(factories, (scope, task) -> FactoryIntegrationContext.run(
                    FactorySnapshotFixture.local(scope.factoryId()
                            .value()), task), sync, completion, mock(ProductionCompletionService.class), pool);
            job.scheduled();
            assertTrue(finished.await(10, TimeUnit.SECONDS));
            assertTrue(FactoryContext.current().isEmpty());
        }
    }

    @Test
    void sameFactoryHintsCoalesceAndWorkArrivingDuringTheRunGetsAnotherClaim() {
        var tasks = new QueuedCompletionExecutor();
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        var executions = mock(ProductionCompletionService.class);
        var scope = new FactoryScope(new FactoryId("A"));
        var run = new SyncCoordinator.Run(scope, "workorder-completion", "TEST-TOKEN");
        when(sync.claim(any(), any(), any())).thenReturn(run);
        var job = new WorkOrderCompletionJob(mock(FactoryCatalog.class), (s, t) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.local("A"), t), sync, completion, executions, tasks);
        when(executions.reconcile(1L, run)).thenAnswer(i -> {
            job.completed(new WorkOrderCompletionRequested(scope, 7L));
            return true;
        });
        job.reported(new ProductionCompletionRequested(scope, 1L));
        job.reported(new ProductionCompletionRequested(scope, 1L));
        assertEquals(1, tasks.size());
        tasks.runAll();
        verify(executions, times(1)).reconcile(1L, run);
        verify(completion, times(1)).reconcile(7L, run);
        verify(sync, times(2)).claim(any(), any(), any());
        assertEquals(0, tasks.size());
    }

    @Test
    void rejectedQueueNeverExecutesOnTheCommittingThreadAndSweepRecoversLostHints() {
        var tasks = new QueuedCompletionExecutor();
        var attempts = new AtomicInteger();
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var scope = new FactoryScope(new FactoryId("A"));
        var run = new SyncCoordinator.Run(scope, "workorder-completion", "TEST-TOKEN");
        when(sync.claim(any(), any(), any())).thenReturn(run);
        var completion = mock(WorkOrderCompletionService.class);
        var executions = mock(ProductionCompletionService.class);
        when(completion.executionCandidates(0L)).thenReturn(List.of(1L));
        var job = new WorkOrderCompletionJob(factories, (s, t) -> FactoryIntegrationContext.run(
                FactorySnapshotFixture.local("A"), t), sync, completion, executions, t -> {
            if (attempts.getAndIncrement() == 0) throw new RejectedExecutionException("TEST ONLY saturation");
            tasks.execute(t);
        });
        job.reported(new ProductionCompletionRequested(scope, 1L));
        verify(sync, never()).claim(any(), any(), any());
        job.scheduled();
        tasks.runAll();
        verify(executions).reconcile(1L, run);
        verify(sync).succeed(run, null);
    }

    private static Factory factory(String id) {
        return new Factory(new FactoryId(id), "TEST", id, "TEST ONLY " + id, FactoryStatus.ENABLED);
    }
}
