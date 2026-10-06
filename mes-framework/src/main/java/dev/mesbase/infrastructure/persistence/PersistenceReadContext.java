package dev.mesbase.infrastructure.persistence;

import java.util.function.Supplier;

/** Internal independent platform metadata reads are never platform writes. */
final class PersistenceReadContext {
    private static final ThreadLocal<OwnedTransactionManager> ACTIVE = new ThreadLocal<>();
    static boolean active() { return ACTIVE.get() != null; }
    static boolean active(OwnedTransactionManager manager) { return ACTIVE.get() == manager; }
    static <T> T independent(OwnedTransactionManager manager, Supplier<T> work) {
        OwnedTransactionManager previous = ACTIVE.get(); ACTIVE.set(manager);
        try { return work.get(); } finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
