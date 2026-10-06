package dev.mesbase.infrastructure.persistence;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Lightweight Adapter entry: choose the id before Spring acquires a connection. */
public final class ErpTransactions {
    private final PlatformTransactionManager manager;
    public ErpTransactions(PlatformTransactionManager manager) { this.manager = manager; }
    public <T> T execute(String sourceId, Supplier<T> work) {
        return ErpSourceContext.withSource(sourceId, () -> new TransactionTemplate(manager).execute(status -> work.get()));
    }
}
