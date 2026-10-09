package dev.mesbase.platform.execution;

import java.util.Objects;
import java.util.Optional;

import dev.mesbase.core.factory.FactoryScope;

public record ExecutionScope(Kind kind, Optional<FactoryScope> factory) {
    public enum Kind {PLATFORM, FACTORY}

    public ExecutionScope {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(factory);
        if ((kind == Kind.FACTORY) != factory.isPresent())
            throw new IllegalArgumentException("Execution scope mismatch");
    }

    public static ExecutionScope platform() {
        return new ExecutionScope(Kind.PLATFORM, Optional.empty());
    }

    public static ExecutionScope factory(FactoryScope scope) {
        return new ExecutionScope(Kind.FACTORY, Optional.of(scope));
    }

    public static ExecutionScope captureFactory() {
        return factory(FactoryContext.required());
    }

    public void run(Runnable task) {
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        try {
            FactoryContext.runWithScope(factory.orElse(null), task);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }
}
