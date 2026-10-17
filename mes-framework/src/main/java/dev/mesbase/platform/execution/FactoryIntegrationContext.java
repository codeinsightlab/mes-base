package dev.mesbase.platform.execution;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;
import java.util.Optional;

/** HTTP reads its admitted snapshot; background work must explicitly resolve and enter a task. */
public final class FactoryIntegrationContext {
    private static final ThreadLocal<FactoryIntegrationSnapshot> TASK = new ThreadLocal<>();

    private FactoryIntegrationContext() { }

    public static Optional<FactoryIntegrationSnapshot> current() {
        var scope = FactoryContext.current();
        if (scope.isEmpty()) return Optional.empty();
        var task = TASK.get();
        if (task != null) {
            if (!task.factoryId().equals(scope.get().factoryId())) throw new FoundationException(ErrorCode.CONFLICT);
            return Optional.of(task);
        }
        return BusinessRequestSnapshot.current(scope.get()).map(BusinessRequestSnapshot::integration);
    }

    public static FactoryIntegrationSnapshot required() {
        return current().orElseThrow(() -> new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED));
    }

    /** Caller resolves fresh configuration on the executing thread, never captures HTTP credentials. */
    public static void run(FactoryIntegrationSnapshot snapshot, Runnable task) {
        var previous = TASK.get();
        if (previous != null && !previous.equals(snapshot)) throw new FoundationException(ErrorCode.CONFLICT);
        try {
            FactoryContext.runWithScope(new FactoryScope(snapshot.factoryId()), () -> {
                TASK.set(snapshot);
                try (var pin = FactoryContext.pinCurrent()) { task.run(); } finally { if (previous == null) TASK.remove(); else TASK.set(previous); }
            });
        } finally {
            if (previous == null) TASK.remove(); else TASK.set(previous);
        }
    }
}
