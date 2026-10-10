package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Node-local owner of versioned pools; replacements drain borrowed units of work.
 */
public final class RuntimeRegistry implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeRegistry.class);

    static final class Runtime {
        final DatasourceIdentity identity;
        final long revision;
        final DataSource datasource;
        final FactoryId factoryId;
        final Runnable closePool;
        final Set<Class<?>> mappers;
        int leases;
        boolean retired;
        boolean poolClosed;

        Runtime(FactoryId factoryId, DatasourceIdentity identity, long revision, DataSource datasource,
                Runnable closePool, Set<Class<?>> mappers) {
            this.factoryId = factoryId;
            this.identity = identity;
            this.revision = revision;
            this.datasource = datasource;
            this.closePool = closePool;
            this.mappers = Set.copyOf(mappers);
            for (Class<?> mapper : mappers) {
                if (!MapperPackages.matches(mapper, PersistenceOwner.Kind.BUSINESS))
                    throw new IllegalArgumentException("BUSINESS mapper package required");
            }
        }

        void drain() {
            if (retired && leases == 0 && !poolClosed) {
                closePool.run();
                poolClosed = true;
            }
        }
    }

    public final class Lease implements AutoCloseable {
        private final Runtime runtime;
        private boolean released;

        private Lease(Runtime runtime) {
            this.runtime = runtime;
        }

        Runtime runtime() {
            return runtime;
        }

        public void verify(BusinessRuntimeBinding binding) {
            synchronized (RuntimeRegistry.this) {
                if (released || runtime.poolClosed || !runtime.factoryId.equals(binding.factoryId())
                        || !runtime.identity.equals(binding.datasourceIdentity()) || runtime.revision != binding.revision())
                    throw new FoundationException(ErrorCode.CONFLICT);
            }
        }

        public void close() {
            synchronized (RuntimeRegistry.this) {
                if (!released) {
                    released = true;
                    runtime.leases--;
                    tryDrain(runtime);
                }
            }
        }
    }

    private final Set<DataSource> ownedPools = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<DatasourceIdentity, Runtime> current = new HashMap<>();
    private final Set<Runtime> live = new HashSet<>();
    private final Map<DatasourceIdentity, FactoryId> dedicatedOwners = new HashMap<>();
    private boolean closed;

    /**
     * A prepared pool transfers ownership only on success. Failed preparations are closed.
     */
    public synchronized void registerDedicated(FactoryId factoryId, DatasourceIdentity identity, long revision,
                                               DataSource datasource, Runnable closer, Set<Class<?>> mappers) {
        Objects.requireNonNull(factoryId);
        Objects.requireNonNull(identity);
        Objects.requireNonNull(datasource);
        Objects.requireNonNull(closer);
        Objects.requireNonNull(mappers);
        if (ownedPools.contains(datasource)) throw new FoundationException(ErrorCode.CONFLICT);
        if (revision < 1) {
            closer.run();
            throw new IllegalArgumentException("Invalid revision");
        }
        if (closed) {
            closer.run();
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var owner = dedicatedOwners.get(identity);
        if ((owner != null && !owner.equals(factoryId)) || live.stream().anyMatch(runtime ->
                !runtime.poolClosed && runtime.identity.equals(identity) && !runtime.factoryId.equals(factoryId))) {
            closer.run();
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        Runtime previous = current.get(identity);
        if (previous != null && previous.revision >= revision) {
            closer.run();
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        Runtime replacement;
        try {
            replacement = new Runtime(factoryId, identity, revision, datasource, closer, mappers);
        } catch (RuntimeException | Error failure) {
            closer.run();
            throw failure;
        }
        live.removeIf(runtime -> runtime.poolClosed);
        live.add(replacement);
        current.put(identity, replacement);
        dedicatedOwners.put(identity, factoryId);
        ownedPools.add(datasource);
        if (previous != null) {
            previous.retired = true;
            tryDrain(previous);
        }
    }

    public synchronized Set<Class<?>> mapperTypes() {
        Set<Class<?>> result = new HashSet<>();
        current.values().forEach(runtime -> result.addAll(runtime.mappers));
        return Set.copyOf(result);
    }

    public synchronized boolean contains(BusinessRuntimeBinding binding) {
        var runtime = current.get(binding.datasourceIdentity());
        return !closed && runtime != null && runtime.revision == binding.revision()
                && binding.factoryId().equals(dedicatedOwners.get(binding.datasourceIdentity()));
    }

    /**
     * Observation only: never creates a pool or acquires a connection/lease.
     */
    public synchronized Optional<DataSource> observedDatasource(BusinessRuntimeBinding binding) {
        return contains(binding) ? Optional.of(current.get(binding.datasourceIdentity()).datasource) : Optional.empty();
    }

    /**
     * Stop new acquisition; admitted leases remain valid until released, then the retired pool closes.
     */
    public synchronized void retire(DatasourceIdentity identity) {
        var runtime = current.remove(identity);
        dedicatedOwners.remove(identity);
        if (runtime != null) {
            runtime.retired = true;
            tryDrain(runtime);
        }
    }

    public synchronized Lease acquireDedicated(BusinessRuntimeBinding binding) {
        if (closed) throw new FoundationException(ErrorCode.CONFLICT);
        Runtime runtime = current.get(binding.datasourceIdentity());
        if (runtime == null) throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        if (runtime.revision != binding.revision()) throw new FoundationException(ErrorCode.CONFLICT);
        var owner = dedicatedOwners.get(binding.datasourceIdentity());
        if (owner == null || !owner.equals(binding.factoryId())) throw new FoundationException(ErrorCode.CONFLICT);
        runtime.leases++;
        return new Lease(runtime);
    }

    private void tryDrain(Runtime runtime) {
        try {
            runtime.drain();
        } catch (RuntimeException failure) {
            // Publication/commit already succeeded; cleanup failure must not close the accepted replacement.
            LOG.error("Retired pool cleanup pending identity={} revision={}", runtime.identity, runtime.revision, failure);
        }
    }

    public synchronized void retryRetired() {
        live.stream().filter(runtime -> runtime.retired).forEach(this::tryDrain);
        live.removeIf(runtime -> runtime.poolClosed);
    }

    public synchronized void close() {
        closed = true;
        RuntimeException failure = null;
        for (Runtime runtime : live) {
            runtime.retired = true;
            try {
                runtime.drain();
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        current.clear();
        if (failure != null) throw failure;
    }
}
