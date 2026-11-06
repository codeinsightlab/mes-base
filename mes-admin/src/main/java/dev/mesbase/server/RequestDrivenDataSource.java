package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

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
        long started = System.nanoTime();
        long budget = Math.max(1, maxWaitMillis);
        recovery.check();
        init();
        // Coordinate acquisition, not connection use. Waiters recheck the pool and circuit after
        // the current borrower finishes; ordinary cold start/expansion is not an outage.
        return recovery.recover(() -> {
            if (!recovery.cooling() && getPoolingCount() > 0) {
                try {
                    return super.getConnection(1);
                } catch (SQLException failure) {
                    if (isClosed() || Thread.currentThread().isInterrupted()) throw failure;
                    // Druid discarded stale idle connections. Use this same attempt to replace them.
                }
            }
            if (getPoolingCount() == 0 && getActiveCount() < getMaxActive()) {
                var physical = createPhysicalConnection();
                if (!put(physical)) physical.getPhysicalConnection().close();
            }
            return super.getConnection(remainingWait(started, budget));
        }, () -> isClosed() || Thread.currentThread().isInterrupted() || getActiveCount() >= getMaxActive(),
                remainingWait(started, budget));
    }

    private static long remainingWait(long started, long budget) {
        return Math.max(1, budget - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
}
