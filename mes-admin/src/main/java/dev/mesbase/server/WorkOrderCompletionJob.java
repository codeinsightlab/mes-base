package dev.mesbase.server;

import dev.mesbase.business.production.domain.WorkOrderCompletionRequested;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.ExecutionScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationExecutor;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Committed completions drive targeted reconciliation; the periodic sweep recovers missed hints.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCompletionJob {
    private static final Logger LOG = LoggerFactory.getLogger(WorkOrderCompletionJob.class);
    private final FactoryCatalog factories;
    private final FactoryIntegrationExecutor integration;
    private final SyncCoordinator coordinator;
    private final WorkOrderCompletionService completion;
    private final Executor executor;
    private final ProductionCompletionService executions;
    // Local queue coalescing only. The database lease remains the cross-node execution authority.
    private final ConcurrentMap<FactoryScope, Pending> pending = new ConcurrentHashMap<>();

    public WorkOrderCompletionJob(FactoryCatalog factories, FactoryIntegrationExecutor integration,
                                  SyncCoordinator coordinator, WorkOrderCompletionService completion,
                                  ProductionCompletionService executions,
                                  @Qualifier("workOrderCompletionExecutor") Executor executor) {
        this.executions = executions;
        this.factories = factories;
        this.integration = integration;
        this.coordinator = coordinator;
        this.completion = completion;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void completed(WorkOrderCompletionRequested event) {
        enqueue(event.scope(), event.workorderId(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reported(ProductionCompletionRequested event) {
        enqueue(event.scope(), event.executionId(), true);
    }

    @Scheduled(initialDelay = 600000, fixedDelay = 600000)
    public void scheduled() {
        for (int offset = 0; ; offset += 100) {
            var page = factories.enabled(offset, 100);
            if (page.isEmpty()) return;
            for (var factory : page) enqueue(new FactoryScope(factory.id()), null, false);
        }
    }

    private void enqueue(FactoryScope scope, Long id, boolean execution) {
        pending.compute(scope, (key, current) -> {
            if (current != null) {
                current.add(id, execution);
                return current;
            }
            var next = new Pending();
            next.add(id, execution);
            return submit(scope, next);
        });
    }

    private Pending submit(FactoryScope scope, Pending next) {
        try {
            // AbortPolicy is intentional: never run business work on the committing HTTP/scheduler thread.
            executor.execute(() -> work(scope));
            return next;
        } catch (RejectedExecutionException rejected) {
            LOG.warn("Work-order completion queue unavailable for factory {}; periodic sweep will recover", scope.factoryId()
                    .value());
            return null;
        }
    }

    private void work(FactoryScope scope) {
        boolean finished = false;
        try {
            // Resolve current managed configuration on this worker; no HTTP security/session/transaction is copied.
            ExecutionScope.factory(scope).run(() -> integration.run(scope, this::reconcileFactory));
            finished = true;
        } catch (FoundationException failure) {
            if (failure.code() != ErrorCode.SYNC_RUNNING)
                LOG.error("Work-order completion failed for factory {}", scope.factoryId().value(), failure);
        } catch (RuntimeException failure) {
            LOG.error("Work-order completion failed for factory {}", scope.factoryId().value(), failure);
        } finally {
            boolean succeeded = finished;
            pending.computeIfPresent(scope, (key, rest) ->
                    succeeded && rest.hasWork() ? submit(scope, rest) : null);
        }
    }

    private Demand take(FactoryScope scope) {
        var result = new AtomicReference<>(new Demand(false, List.of(), List.of()));
        pending.computeIfPresent(scope, (key, current) -> {
            result.set(new Demand(current.sweep, current.ids.stream().sorted().toList(), current.executions.stream()
                    .sorted().toList()));
            current.sweep = false;
            current.ids.clear();
            current.executions.clear();
            return current;
        });
        return result.get();
    }

    private void reconcileFactory() {
        var scope = FactoryContext.required();
        if (FactoryIntegrationContext.required().mode() != FactoryRuntimeMode.LOCAL) {
            take(scope);
            return;
        }
        var run = coordinator.claim(scope, "workorder-completion", Duration.ofMinutes(5));
        var progress = new Progress();
        try {
            var demand = take(scope);
            if (demand.sweep()) {
                long afterExecution = 0;
                while (true) {
                    coordinator.progress(run, progress.checked);
                    var ids = completion.executionCandidates(afterExecution);
                    if (ids.isEmpty()) break;
                    for (Long id : ids) {
                        reconcile(id, run, progress, true);
                        afterExecution = id;
                    }
                }
                long after = 0;
                while (true) {
                    coordinator.progress(run, progress.checked);
                    var ids = completion.candidates(after);
                    if (ids.isEmpty()) break;
                    for (Long id : ids) {
                        reconcile(id, run, progress, false);
                        after = id;
                    }
                }
            } else {
                for (Long id : demand.executions()) reconcile(id, run, progress, true);
                for (Long id : demand.ids()) reconcile(id, run, progress, false);
            }
            coordinator.progress(run, progress.checked);
            coordinator.succeed(run, null);
            LOG.info("Work-order completion run={} factory={} sweep={} checked={} completed={} blocked={} invalid={} firstInvalid={}",
                    run.runToken(), scope.factoryId().value(), demand.sweep(), progress.checked, progress.completed,
                    progress.checked - progress.completed - progress.invalid, progress.invalid, progress.firstInvalid);
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            throw failure;
        }
    }

    private void reconcile(Long id, SyncCoordinator.Run run, Progress progress, boolean execution) {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Completion worker interrupted");
        coordinator.progress(run, progress.checked);
        try {
            if (execution ? executions.reconcile(id, run) : completion.reconcile(id, run)) progress.completed++;
        } catch (FoundationException blocked) {
            if (!Set.of(ErrorCode.REPORT_PATH_REQUIRED, ErrorCode.REPORT_QUANTITY_CONFLICT,
                    ErrorCode.QUALITY_ATTRIBUTION_REQUIRED, ErrorCode.EXECUTION_INVALID,
                    ErrorCode.WORKORDER_NOT_RELEASED).contains(blocked.code())) throw blocked;
            if (progress.firstInvalid == null) progress.firstInvalid = id;
            progress.invalid++;
        }
        progress.checked++;
    }

    private static final class Pending {
        private boolean sweep;
        private final Set<Long> ids = new HashSet<>();

        private final Set<Long> executions = new HashSet<>();

        private void add(Long id, boolean execution) {
            if (id == null || !sweep && ids.size() + executions.size() >= 100 && !(execution ? executions : ids).contains(id)) {
                sweep = true;
                ids.clear();
                executions.clear();
            } else if (!sweep) (execution ? executions : ids).add(id);
        }

        private boolean hasWork() {
            return sweep || !ids.isEmpty() || !executions.isEmpty();
        }
    }

    private record Demand(boolean sweep, List<Long> ids, List<Long> executions) {
    }

    private static final class Progress {
        private long checked;
        private long completed;
        private long invalid;
        private Long firstInvalid;
    }
}
