package dev.mesbase.infrastructure.persistence;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** TEST ONLY low-level routing fixture; application code uses ErpSource. */
public final class ErpTransactions {
    private final PlatformTransactionManager manager;
    public ErpTransactions(PlatformTransactionManager manager) { this.manager = manager; }
    public <T> T execute(String sourceId, Supplier<T> work) {
        String previous = ErpSourceContext.select(sourceId);
        try { return new TransactionTemplate(manager).execute(status -> work.get()); }
        finally { ErpSourceContext.restore(previous); }
    }
}
