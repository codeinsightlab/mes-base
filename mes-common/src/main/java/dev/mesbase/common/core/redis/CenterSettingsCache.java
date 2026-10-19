package dev.mesbase.common.core.redis;

import dev.mesbase.infrastructure.persistence.CommitActions;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Committed center settings only. Display values may be stale for at most one finite cache lifetime.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class CenterSettingsCache {
    public static final int TTL_SECONDS = 30;
    private static final String VERSION = "committed-v1:";
    private static final Logger LOG = LoggerFactory.getLogger(CenterSettingsCache.class);
    private final RedisCache redis;
    private final CommitActions commits;

    public CenterSettingsCache(RedisCache redis, CommitActions commits) {
        this.redis = redis;
        this.commits = commits;
    }

    public <T> T get(String key) {
        // A transaction may hold a snapshot or its own uncommitted writes: never share either.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return null;
        try {
            return redis.getCacheObject(VERSION + key);
        } catch (RuntimeException failure) {
            LOG.warn("Settings cache read unavailable; using database type={}", failure.getClass().getSimpleName());
            return null;
        }
    }

    public void put(String key, Object value) {
        if (value == null || TransactionSynchronizationManager.isActualTransactionActive()) return;
        try {
            redis.setCacheObject(VERSION + key, value, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            LOG.warn("Settings cache fill unavailable type={}", failure.getClass().getSimpleName());
        }
    }

    public void invalidate(String key) {
        afterCommit(() -> {
            redis.deleteObject(VERSION + key);
            redis.deleteObject(key); // Retire the previous permanent-cache key as well.
        });
    }

    public void clear(String prefix) {
        afterCommit(() -> {
            redis.deleteObject(redis.keys(VERSION + prefix + "*"));
            redis.deleteObject(redis.keys(prefix + "*"));
        });
    }

    private void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException failure) {
                // DB has committed. Finite TTL bounds failed invalidations and concurrent old fills.
                LOG.warn("Committed settings cache invalidation pending expiry type={}",
                        failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) commits.afterCommit(safe);
        else safe.run();
    }
}
