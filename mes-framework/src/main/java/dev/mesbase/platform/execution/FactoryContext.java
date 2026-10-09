package dev.mesbase.platform.execution;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread bridge only. Domain code receives an explicit FactoryScope.
 */
public final class FactoryContext {
    private static final ThreadLocal<FactoryScope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<FactoryScope> PINNED = new ThreadLocal<>();

    private FactoryContext() {
    }

    public static Optional<FactoryScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static FactoryScope required() {
        return current().orElseThrow(() -> new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED));
    }

    public static void bind(FactoryScope scope) {
        Objects.requireNonNull(scope);
        check(scope);
        CURRENT.set(scope);
    }

    public static void clear() {
        check(null);
        CURRENT.remove();
    }

    private static void check(FactoryScope next) {
        if (PINNED.get() != null && !PINNED.get().equals(next)) throw new FoundationException(ErrorCode.CONFLICT);
    }

    public static <T> T withScope(FactoryScope scope, Supplier<T> action) {
        FactoryScope before = CURRENT.get();
        if (scope == null) clear();
        else bind(scope);
        try {
            return action.get();
        } finally {
            if (before == null) clear();
            else bind(before);
        }
    }

    public static void runWithScope(FactoryScope scope, Runnable action) {
        withScope(scope, () -> {
            action.run();
            return null;
        });
    }

    public static final class Pin implements AutoCloseable {
        private final FactoryScope previous;
        private boolean closed;

        private Pin(FactoryScope scope) {
            previous = PINNED.get();
            check(scope);
            PINNED.set(scope);
        }

        public void close() {
            if (!closed) {
                closed = true;
                if (previous == null) PINNED.remove();
                else PINNED.set(previous);
            }
        }
    }

    public static Pin pinCurrent() {
        return new Pin(required());
    }

    /**
     * Used by infrastructure for an entire business transaction, including commit/rollback.
     */
    public static <T> T pinned(FactoryScope scope, Supplier<T> action) {
        if (!required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        FactoryScope previous = PINNED.get();
        check(scope);
        PINNED.set(scope);
        try {
            return action.get();
        } finally {
            if (previous == null) PINNED.remove();
            else PINNED.set(previous);
        }
    }
}
