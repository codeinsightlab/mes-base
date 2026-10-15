package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import javax.sql.DataSource;

import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring owns propagation and rollback; this extension only guards database identity.
 */
public final class OwnedTransactionManager extends JdbcTransactionManager {
    private static final Object OWNER = new Object();
    private final Object identity = new Object();
    private final TransactionRouting routing;

    public OwnedTransactionManager(DataSource source) {
        super(source);
        routing = source instanceof TransactionRouting router ? router : null;
    }

    @Override
    protected Object doGetTransaction() {
        checkOwner(identity);
        if (routing != null && TransactionSynchronizationManager.hasResource(OWNER)) routing.verify();
        return super.doGetTransaction();
    }

    private static void checkOwner(Object expected) {
        Object owner = TransactionSynchronizationManager.getResource(OWNER);
        if ((owner != null && owner != expected)
                || (owner == null && TransactionSynchronizationManager.isActualTransactionActive()))
            throw new FoundationException(ErrorCode.CONFLICT);
    }

    /**
     * True only when an active transaction belongs to a different database manager.
     */
    public boolean foreignTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.getResource(OWNER) != identity;
    }

    public void verifyAccess() {
        if (routing == null && PersistenceReadContext.active(this)) return;
        checkOwner(identity);
        if (routing != null) routing.verify();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        checkOwner(identity);
        if (routing != null) routing.begin();
        try {
            super.doBegin(transaction, definition);
            TransactionSynchronizationManager.bindResource(OWNER, identity);
        } catch (RuntimeException | Error failure) {
            if (routing != null) routing.end();
            throw failure;
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (routing != null) routing.verify();
        super.doCommit(status);
    }

    private record Suspended(Object connection, Object owner, Object routingState) {
    }

    @Override
    protected Object doSuspend(Object transaction) {
        Object connection = super.doSuspend(transaction);
        Object owner = TransactionSynchronizationManager.unbindResource(OWNER);
        return new Suspended(connection, owner, routing == null ? null : routing.suspend());
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        Suspended suspended = (Suspended) suspendedResources;
        if (routing != null) routing.resume(suspended.routingState());
        super.doResume(transaction, suspended.connection());
        TransactionSynchronizationManager.bindResource(OWNER, suspended.owner());
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(OWNER);
            if (routing != null) routing.end();
        }
    }
}
