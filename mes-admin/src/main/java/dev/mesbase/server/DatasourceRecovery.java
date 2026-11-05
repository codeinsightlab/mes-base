package dev.mesbase.server;

import com.alibaba.druid.pool.DruidPooledConnection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One node-local circuit and probe budget for one managed datasource identity and revision. */
public final class DatasourceRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(DatasourceRecovery.class);
    public static final class Unavailable extends SQLTransientConnectionException {
        Unavailable(String message) { super(message, "08001"); }
        Unavailable(String message, Throwable cause) { super(message, "08001", cause); }
    }
    @FunctionalInterface
    public interface Borrow { DruidPooledConnection get() throws SQLException; }
    private final ReentrantLock probe = new ReentrantLock();
    private final LongSupplier nanoTime;
    private final String identity;
    private volatile boolean cooling;
    private volatile long retryAfter;

    public DatasourceRecovery(LongSupplier nanoTime) { this("independent-business-pool", nanoTime); }
    public DatasourceRecovery(String identity, LongSupplier nanoTime) {
        this.identity = identity;
        this.nanoTime = nanoTime;
    }

    public boolean cooling() { return cooling; }

    public void check() throws SQLException {
        if (cooling && nanoTime.getAsLong() - retryAfter < 0)
            throw new Unavailable("Business datasource is cooling down");
    }

    public synchronized void failed() {
        if (!cooling || nanoTime.getAsLong() - retryAfter >= 0) {
            retryAfter = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS);
            cooling = true;
            LOG.warn("Business datasource cooling down identity={} cooldownMs={}", identity, RequestDrivenDataSource.COOLDOWN_MILLIS);
        }
    }

    public DruidPooledConnection recover(Borrow borrow, BooleanSupplier abort) throws SQLException {
        check();
        if (!probe.tryLock())
            throw new Unavailable("Business datasource connection probe in progress");
        try {
            check();
            SQLException last = null;
            for (int attempt = 0; attempt < RequestDrivenDataSource.MAX_ATTEMPTS; attempt++) {
                try {
                    var connection = borrow.get();
                    boolean recovered = cooling;
                    cooling = false;
                    if (recovered) LOG.info("Business datasource recovered identity={}", identity);
                    return connection;
                } catch (SQLException failure) {
                    if (abort.getAsBoolean()) throw failure;
                    last = failure;
                }
            }
            failed();
            throw new Unavailable("Business datasource connection failed; cooling down", last);
        } finally { probe.unlock(); }
    }
}
