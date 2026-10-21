package dev.mesbase.business.production.domain;

import dev.mesbase.core.factory.FactoryScope;
import java.util.Objects;

public record ProductionCompletionRequested(FactoryScope scope, Long executionId) {
    public ProductionCompletionRequested {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(executionId);
    }
}
