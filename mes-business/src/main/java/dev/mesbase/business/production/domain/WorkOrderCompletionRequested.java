package dev.mesbase.business.production.domain;

import dev.mesbase.core.factory.FactoryScope;
import java.util.Objects;

/**
 * Committed execution completion hint. Carries identity only, never a session or a captured integration binding.
 */
public record WorkOrderCompletionRequested(FactoryScope scope, Long workorderId) {
    public WorkOrderCompletionRequested {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(workorderId);
    }
}
