package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import java.sql.SQLException;

/** Business connections are replenished by borrowers, never by a background reconnect loop. */
public class RequestDrivenDataSource extends DruidDataSource {
    public static final int MAX_ATTEMPTS = 5;
    public static final long COOLDOWN_MILLIS = 60_000;
    private final DatasourceRecovery recovery;

    public RequestDrivenDataSource(DatasourceRecovery recovery) { this.recovery = recovery; }

    @Override
    protected void createAndStartCreatorThread() {
        // Borrowers below are the sole connection creators. The idle reaper may only close connections.
    }

    @Override
    public DruidPooledConnection getConnection(long maxWaitMillis) throws SQLException {
        recovery.check();
        init();
        // Healthy pools retain concurrent borrowing. There is no creator thread to race the retry budget.
        if (!recovery.cooling() && getPoolingCount() > 0) {
            try {
                return super.getConnection(1);
            } catch (SQLException failure) {
                if (isClosed()) throw failure;
                // Stale idle connections have been discarded; recover using the same bounded probe.
            }
        }
        if (!recovery.cooling() && getActiveCount() >= getMaxActive())
            return super.getConnection(Math.max(1, maxWaitMillis));
        return recovery.recover(() -> {
            if (getPoolingCount() == 0 && getActiveCount() < getMaxActive()) {
                var physical = createPhysicalConnection();
                if (!put(physical)) physical.getPhysicalConnection().close();
            }
            return super.getConnection(1);
        }, () -> isClosed() || Thread.currentThread().isInterrupted() || getActiveCount() >= getMaxActive());
    }
}
