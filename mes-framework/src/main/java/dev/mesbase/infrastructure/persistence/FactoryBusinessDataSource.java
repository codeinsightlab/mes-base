package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One business SessionFactory; each Spring transaction pins one versioned Factory pool.
 */
public final class FactoryBusinessDataSource extends AbstractDataSource implements TransactionRouting {
    private record Selection(BusinessRuntimeBinding binding, RuntimeRegistry.Lease lease, FactoryContext.Pin pin) {
    }

    private final Object key = new Object();
    private final BusinessPersistenceResolver resolver;
    private final RuntimeRegistry registry;

    public FactoryBusinessDataSource(BusinessPersistenceResolver resolver, RuntimeRegistry registry) {
        this.resolver = resolver;
        this.registry = registry;
    }

    public void begin() {
        var snapshot = BusinessRequestSnapshot.current(FactoryContext.required());
        BusinessPersistenceResolver.Acquisition acquired = snapshot
                .map(value -> new BusinessPersistenceResolver.Acquisition(value.binding(), value.acquire()))
                .orElseGet(() -> resolver.begin(FactoryContext.required(), registry));
        BusinessRuntimeBinding binding = acquired.binding();
        RuntimeRegistry.Lease lease = acquired.lease();
        FactoryContext.Pin pin = null;
        try {
            pin = FactoryContext.pinCurrent();
            TransactionSynchronizationManager.bindResource(key, new Selection(binding, lease, pin));
        } catch (RuntimeException | Error failure) {
            if (pin != null) pin.close();
            lease.close();
            throw failure;
        }
    }

    public void verify() {
        Selection selected = (Selection) TransactionSynchronizationManager.getResource(key);
        if (selected == null) throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
        if (!selected.binding().factoryId().equals(FactoryContext.required().factoryId()))
            throw new FoundationException(ErrorCode.CONFLICT);
        selected.lease().verify(selected.binding());
    }

    public Object suspend() {
        Selection selected = (Selection) TransactionSynchronizationManager.unbindResource(key);
        selected.pin().close();
        return selected;
    }

    public void resume(Object state) {
        Selection selected = (Selection) state;
        // Restore the suspended transaction's scope, never pin an unrelated scope left by suspended work.
        FactoryContext.bind(new FactoryScope(selected.binding().factoryId()));
        TransactionSynchronizationManager.bindResource(key, new Selection(selected.binding(), selected.lease(), FactoryContext.pinCurrent()));
    }

    public void end() {
        Selection selected = (Selection) TransactionSynchronizationManager.unbindResourceIfPossible(key);
        if (selected != null) {
            try {
                selected.pin().close();
            } finally {
                selected.lease().close();
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        verify();
        return ((Selection) TransactionSynchronizationManager.getResource(key)).lease()
                .runtime().datasource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Request credentials cannot override managed database identity");
    }
}
