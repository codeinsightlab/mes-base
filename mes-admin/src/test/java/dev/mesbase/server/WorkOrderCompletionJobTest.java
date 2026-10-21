package dev.mesbase.server;

import dev.mesbase.acceptance.FactorySnapshotFixture;
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
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderCompletionJobTest {
    @Test
    void aDatabaseClaimConflictStopsThisFactoryAndErpFactoriesAreNotLocallyCompleted() {
        var factories = mock(FactoryCatalog.class);
        when(factories.enabled(0, 100)).thenReturn(List.of(factory("A"), factory("B")));
        when(factories.enabled(100, 100)).thenReturn(List.of());
        var sync = mock(SyncCoordinator.class);
        var completion = mock(WorkOrderCompletionService.class);
        when(sync.claim(eq(new FactoryScope(new FactoryId("A"))), eq("workorder-completion"), any()))
                .thenThrow(new FoundationException(ErrorCode.SYNC_RUNNING));
        var job = new WorkOrderCompletionJob(factories, (scope, task) -> FactoryIntegrationContext.run(
                "A".equals(scope.factoryId().value()) ? FactorySnapshotFixture.local("A")
                        : FactorySnapshotFixture.erp("B", "TEST_ONLY", "binding"), task), sync, completion);
        job.scheduled();
        verify(completion, never()).candidates(any());
        verify(sync, never()).claim(eq(new FactoryScope(new FactoryId("B"))), any(), any());
    }

    @Test
    void losingExecutionOwnershipStopsTheRemainingCandidatesAndRecordsFailure() {
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
                FactorySnapshotFixture.local("A"), task), sync, completion).scheduled();
        verify(completion, never()).reconcile(2L, run);
        verify(sync).fail(run);
        verify(sync, never()).succeed(any(), any());
    }

    @Test
    void anInvalidOldCandidateDoesNotStarveLaterBalancedOrders() {
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
                FactorySnapshotFixture.local("A"), task), sync, completion).scheduled();
        verify(completion).reconcile(2L, run);
        verify(sync).progress(run, 2);
        verify(sync).succeed(run, null);
        verify(sync, never()).fail(any());
    }

    private static Factory factory(String id) {
        return new Factory(new FactoryId(id), "TEST", id, "TEST ONLY " + id, FactoryStatus.ENABLED);
    }
}
