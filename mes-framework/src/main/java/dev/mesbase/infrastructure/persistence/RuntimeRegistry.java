package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/** Node-local owner of versioned pools; replacements drain borrowed units of work. */
public final class RuntimeRegistry implements AutoCloseable {
    static final class Runtime {
        final DatasourceIdentity identity; final long revision; final DataSource datasource;
        final Runnable closePool; final Set<Class<?>> mappers;
        int leases; boolean retired; boolean poolClosed;
        Runtime(DatasourceIdentity identity,long revision,DataSource datasource,Runnable closePool,Set<Class<?>> mappers) {
            this.identity=identity; this.revision=revision; this.datasource=datasource; this.closePool=closePool; this.mappers=Set.copyOf(mappers);
            for (Class<?> mapper : mappers) {
                PersistenceOwner owner = mapper.getAnnotation(PersistenceOwner.class);
                if (owner == null || owner.value() != PersistenceOwner.Kind.BUSINESS)
                    throw new IllegalArgumentException("Explicit BUSINESS mapper ownership required");
            }
        }
        void drain() { if(retired && leases==0 && !poolClosed) { poolClosed=true; closePool.run(); } }
    }
    public final class Lease implements AutoCloseable {
        private final Runtime runtime; private boolean released;
        private Lease(Runtime runtime) { this.runtime=runtime; }
        Runtime runtime() { return runtime; }
        public void verify(BusinessRuntimeBinding binding) { RuntimeRegistry.this.verify(runtime,binding); }
        public void close() { synchronized(RuntimeRegistry.this) { if(!released) { released=true; runtime.leases--; runtime.drain(); } } }
    }
    private final Map<DatasourceIdentity,Runtime> current=new HashMap<>();
    private final Map<DatasourceIdentity,FactoryId> dedicatedOwners=new HashMap<>(); private boolean closed;
    /** A prepared pool transfers ownership only on success. Failed preparations are closed. */
    public synchronized void registerDedicated(FactoryId factoryId,DatasourceIdentity identity,long revision,DataSource datasource,Runnable closer,Set<Class<?>> mappers) {
        Objects.requireNonNull(factoryId); Objects.requireNonNull(identity); Objects.requireNonNull(datasource); Objects.requireNonNull(closer); Objects.requireNonNull(mappers);
        for(Runtime existing:current.values()) {
            if(existing.datasource==datasource) throw new FoundationException(ErrorCode.CONFLICT);
        }
        if(revision<1) { closer.run(); throw new IllegalArgumentException("Invalid revision"); }
        if(closed) { closer.run(); throw new FoundationException(ErrorCode.CONFLICT); }
        var owner=dedicatedOwners.get(identity);
        if(owner!=null && !owner.equals(factoryId)) { closer.run(); throw new FoundationException(ErrorCode.CONFLICT); }
        Runtime previous=current.get(identity);
        if(previous!=null && previous.revision>=revision) { closer.run(); throw new FoundationException(ErrorCode.CONFLICT); }
        Runtime replacement;
        try { replacement=new Runtime(identity,revision,datasource,closer,mappers); }
        catch(RuntimeException | Error failure) { closer.run(); throw failure; }
        current.put(identity,replacement); dedicatedOwners.put(identity,factoryId);
        if(previous!=null) { previous.retired=true; previous.drain(); }
    }
    public synchronized Set<Class<?>> mapperTypes() {
        Set<Class<?>> result = new HashSet<>(); current.values().forEach(runtime -> result.addAll(runtime.mappers)); return Set.copyOf(result);
    }
    public synchronized boolean contains(BusinessRuntimeBinding binding) {
        var runtime=current.get(binding.datasourceIdentity());
        return !closed && runtime!=null && runtime.revision==binding.revision()
                && binding.factoryId().equals(dedicatedOwners.get(binding.datasourceIdentity()));
    }
    /** Remove from acquisition immediately; outstanding leases drain and fail their next version check. */
    public synchronized void retire(DatasourceIdentity identity) {
        var runtime=current.remove(identity); dedicatedOwners.remove(identity);
        if(runtime!=null) { runtime.retired=true; runtime.drain(); }
    }
    public synchronized Lease acquireDedicated(BusinessRuntimeBinding binding) {
        if(closed) throw new FoundationException(ErrorCode.CONFLICT);
        Runtime runtime=current.get(binding.datasourceIdentity());
        if(runtime==null) throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        if(runtime.revision!=binding.revision()) throw new FoundationException(ErrorCode.CONFLICT);
        var owner=dedicatedOwners.get(binding.datasourceIdentity());
        if(owner==null || !owner.equals(binding.factoryId())) throw new FoundationException(ErrorCode.CONFLICT);
        runtime.leases++; return new Lease(runtime);
    }
    private synchronized void verify(Runtime runtime,BusinessRuntimeBinding binding) {
        if(closed || current.get(binding.datasourceIdentity())!=runtime || !runtime.identity.equals(binding.datasourceIdentity()) || runtime.revision!=binding.revision()) throw new FoundationException(ErrorCode.CONFLICT);
    }
    public synchronized void close() {
        closed=true; RuntimeException failure=null;
        for(Runtime runtime:current.values()) { runtime.retired=true; try { runtime.drain(); } catch(RuntimeException e) { if(failure==null) failure=e; else failure.addSuppressed(e); } }
        current.clear(); if(failure!=null) throw failure;
    }
}
