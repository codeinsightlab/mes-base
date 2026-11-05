package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Business connections are replenished by borrowers, never by a background reconnect loop. */
public class RequestDrivenDataSource extends DruidDataSource {
    public static final int MAX_ATTEMPTS = 5;
    public static final long COOLDOWN_MILLIS = 60_000;
    private static final Logger LOG = LoggerFactory.getLogger(RequestDrivenDataSource.class);
    private final ReentrantLock probe = new ReentrantLock();
    private final LongSupplier nanoTime;
    private volatile long retryAfter;
    private volatile boolean cooling;

    public RequestDrivenDataSource() {
        this(System::nanoTime);
    }

    RequestDrivenDataSource(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    protected void createAndStartCreatorThread() {
        // Borrowers below are the sole connection creators. The idle reaper may only close connections.
    }

    private void checkCooldown() throws SQLException {
        if (cooling && nanoTime.getAsLong() - retryAfter < 0)
            throw new SQLTransientConnectionException("Business datasource is cooling down", "08001");
    }

    @Override
    public DruidPooledConnection getConnection(long maxWaitMillis) throws SQLException {
        checkCooldown();
        init();
        // Healthy pools retain concurrent borrowing. There is no creator thread to race the retry budget.
        if (!cooling && getPoolingCount() > 0) {
            try {
                return super.getConnection(1);
            } catch (SQLException failure) {
                if (isClosed()) throw failure;
                // Stale idle connections have been discarded; recover using the same bounded probe.
            }
        }
        if (!cooling && getActiveCount() >= getMaxActive())
            return super.getConnection(Math.max(1, maxWaitMillis));
        if (!probe.tryLock())
            throw new SQLTransientConnectionException("Business datasource connection probe in progress", "08001");
        try {
            checkCooldown();
            SQLException lastFailure = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    if (getPoolingCount() == 0 && getActiveCount() < getMaxActive()) {
                        var physical = createPhysicalConnection();
                        if (!put(physical)) physical.getPhysicalConnection().close();
                    }
                    var connection = super.getConnection(1);
                    boolean recovered = cooling;
                    cooling = false;
                    if (recovered) LOG.info("Business datasource recovered pool={}", getName());
                    return connection;
                } catch (SQLException failure) {
                    if (isClosed() || Thread.currentThread().isInterrupted()) throw failure;
                    // Pool saturation is not an outage and must not open the circuit.
                    if (getActiveCount() >= getMaxActive()) throw failure;
                    lastFailure = failure;
                }
            }
            retryAfter = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MILLIS);
            cooling = true;
            LOG.warn("Business datasource cooling down pool={} attempts={} cooldownMs={}",
                    getName(), MAX_ATTEMPTS, COOLDOWN_MILLIS);
            throw new SQLTransientConnectionException("Business datasource connection failed; cooling down", "08001", lastFailure);
        } finally {
            probe.unlock();
        }
    }
}
