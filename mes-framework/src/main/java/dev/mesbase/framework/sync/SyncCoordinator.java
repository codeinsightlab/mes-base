package dev.mesbase.framework.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.sync.MasterSync;
import dev.mesbase.system.sync.MasterSyncMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * All manual/scheduled sync executors use this database-backed coordinator. No process-local task lock.
 */
public final class SyncCoordinator {
    public record Run(FactoryScope scope, String syncKey, String runToken) {
    }

    private final MasterSyncMapper mapper;
    private final PlatformPersistence persistence;
    private final FactoryCatalog factories;

    public SyncCoordinator(PlatformPersistence persistence, MasterSyncMapper mapper, FactoryCatalog factories) {
        this.mapper = mapper;
        this.persistence = persistence;
        this.factories = factories;
    }

    private static void changed(int n) {
        if (n != 1) throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
    }

    private static String id(FactoryScope scope) {
        if (scope == null) throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
        return scope.factoryId().value();
    }

    public Run claim(FactoryScope scope, String key, Duration lease) {
        String factory = id(scope);
        if (key == null || !key.matches("[a-zA-Z0-9_.:-]{1,96}") || lease == null || lease.getSeconds() < 1 || lease.getSeconds() > 86400)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var f = factories.find(scope.factoryId())
                .orElseThrow(() -> new FoundationException(ErrorCode.FACTORY_NOT_FOUND));
        if (f.status() != FactoryStatus.ENABLED) throw new FoundationException(ErrorCode.FACTORY_DISABLED);
        String token = UUID.randomUUID().toString();
        return persistence.run(() -> {
            mapper.ensure(factory, key);
            var old = mapper.find(factory, key);
            if ("RUNNING".equals(old.status()) && mapper.recover(factory, key, old.runToken(), lease.getSeconds()) != 1)
                throw new FoundationException(ErrorCode.SYNC_RUNNING);
            if (mapper.claim(factory, key, token) != 1) throw new FoundationException(ErrorCode.SYNC_RUNNING);
            return new Run(scope, key, token);
        });
    }

    public MasterSync state(FactoryScope scope, String key) {
        return mapper.find(id(scope), key);
    }

    public void assertOwner(Run run) {
        var state = persistence.transactionManager().foreignTransactionActive()
                ? persistence.readOnly(s -> s.mapper(MasterSyncMapper.class).find(id(run.scope()), run.syncKey()))
                : state(run.scope(), run.syncKey());
        if (state == null || !"RUNNING".equals(state.status()) || !run.runToken().equals(state.runToken()))
            throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
    }

    /**
     * Executors renew before each bounded batch and stop if the conditional update fails.
     */
    public void progress(Run run, long count) {
        if (count < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        persistence.run(() -> {
            changed(mapper.progress(id(run.scope()), run.syncKey(), run.runToken(), count));
            return null;
        });
    }

    public void succeed(Run run, LocalDateTime cursor) {
        persistence.run(() -> {
            changed(mapper.finish(id(run.scope()), run.syncKey(), run.runToken(), "SUCCEEDED", cursor, null));
            return null;
        });
    }

    public void fail(Run run) {
        persistence.run(() -> {
            changed(mapper.finish(id(run.scope()), run.syncKey(), run.runToken(), "FAILED", null, "同步失败，请通过运行标识查阅后台日志"));
            return null;
        });
    }
}
