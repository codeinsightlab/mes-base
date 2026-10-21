package dev.mesbase.server;

import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationExecutor;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Factory-scoped periodic reconciliation, with database leases shared across nodes.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCompletionJob {
    private static final Logger LOG = LoggerFactory.getLogger(WorkOrderCompletionJob.class);
    private final FactoryCatalog factories;
    private final FactoryIntegrationExecutor integration;
    private final SyncCoordinator coordinator;
    private final WorkOrderCompletionService completion;

    public WorkOrderCompletionJob(FactoryCatalog factories, FactoryIntegrationExecutor integration,
                                  SyncCoordinator coordinator, WorkOrderCompletionService completion) {
        this.factories = factories;
        this.integration = integration;
        this.coordinator = coordinator;
        this.completion = completion;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void scheduled() {
        for (int offset = 0; ; offset += 100) {
            var page = factories.enabled(offset, 100);
            if (page.isEmpty()) return;
            for (var factory : page) {
                var scope = new FactoryScope(factory.id());
                try {
                    integration.run(scope, this::reconcileFactory);
                } catch (FoundationException failure) {
                    if (failure.code() != ErrorCode.SYNC_RUNNING)
                        LOG.error("Work-order completion failed for factory {}", factory.id().value(), failure);
                } catch (RuntimeException failure) {
                    LOG.error("Work-order completion failed for factory {}", factory.id().value(), failure);
                }
            }
        }
    }

    private void reconcileFactory() {
        // An ERP order's lifecycle belongs to its ERP. Missing/illegal snapshots fail closed.
        if (FactoryIntegrationContext.required().mode() != FactoryRuntimeMode.LOCAL) return;
        var run = coordinator.claim(FactoryContext.required(),
                "workorder-completion", Duration.ofMinutes(5));
        long processed = 0;
        long after = 0;
        try {
            while (true) {
                coordinator.progress(run, processed);
                var ids = completion.candidates(after);
                if (ids.isEmpty()) break;
                for (Long id : ids) {
                    coordinator.progress(run, processed);
                    if (completion.reconcile(id, run)) processed++;
                    after = id;
                }
            }
            coordinator.succeed(run, null);
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            throw failure;
        }
    }
}
