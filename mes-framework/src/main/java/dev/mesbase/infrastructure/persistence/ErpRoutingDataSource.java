package dev.mesbase.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import javax.sql.DataSource;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * All external Mappers share one SessionFactory; each transaction pins one configured source id.
 */
public final class ErpRoutingDataSource extends AbstractDataSource implements TransactionRouting, AutoCloseable {
    private record Selection(String id, DataSource source, ErpSourceContext.Pin pin) {
    }

    private final Object key = new Object();
    private final Map<String, DataSource> sources;

    public ErpRoutingDataSource(Map<String, DataSource> sources) {
        if (sources == null || sources.isEmpty())
            throw new IllegalArgumentException("ERP datasource identifiers are required");
        sources.keySet().forEach(ErpSourceContext::validate);
        this.sources = Map.copyOf(sources);
    }

    /**
     * Read-only view of already configured pools, independent of transaction routing.
     */
    public Map<String, DataSource> configuredSources() {
        return sources;
    }

    private Selection selected() {
        Selection selected = (Selection) TransactionSynchronizationManager.getResource(key);
        if (selected == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return selected;
    }

    public void begin() {
        String id = ErpSourceContext.required();
        DataSource source = sources.get(id);
        if (source == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        TransactionSynchronizationManager.bindResource(key, new Selection(id, source, new ErpSourceContext.Pin(id)));
    }

    public void verify() {
        Selection selected = selected();
        if (!selected.id().equals(ErpSourceContext.required())) throw new FoundationException(ErrorCode.CONFLICT);
    }

    public Object suspend() {
        Selection selected = (Selection) TransactionSynchronizationManager.unbindResource(key);
        selected.pin().close();
        return selected;
    }

    public void resume(Object state) {
        Selection selected = (Selection) state;
        TransactionSynchronizationManager.bindResource(key, new Selection(selected.id(), selected.source(), new ErpSourceContext.Pin(selected.id())));
    }

    public void end() {
        Selection selected = (Selection) TransactionSynchronizationManager.unbindResourceIfPossible(key);
        if (selected != null) selected.pin().close();
    }

    @Override
    public Connection getConnection() throws SQLException {
        verify();
        return selected().source().getConnection();
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        throw new SQLException("Request credentials cannot override configured ERP source identity");
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (DataSource source : new HashSet<>(sources.values()))
            if (source instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception error) {
                    if (failure == null) failure = new IllegalStateException("ERP pool closure failed", error);
                    else failure.addSuppressed(error);
                }
            }
        if (failure != null) throw failure;
    }
}
