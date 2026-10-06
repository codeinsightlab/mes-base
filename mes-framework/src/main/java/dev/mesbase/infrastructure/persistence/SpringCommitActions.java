package dev.mesbase.infrastructure.persistence;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Effects follow Spring's actual completion, including nested rollback-only decisions. */
public final class SpringCommitActions implements CommitActions {
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Active Spring transaction required");
    }
    public void afterCommit(Runnable action) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    public void afterRollback(Runnable action) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) action.run();
            }
        });
    }
}
