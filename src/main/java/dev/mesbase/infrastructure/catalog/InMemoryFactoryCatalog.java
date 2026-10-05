package dev.mesbase.infrastructure.catalog;
import java.util.*;
import dev.mesbase.core.factory.*;
/** Local bootstrap adapter; no durable administration is provided in phase one. */
public final class InMemoryFactoryCatalog implements FactoryCatalog {
    private final Map<FactoryId,Factory> entries;
    public InMemoryFactoryCatalog(List<Factory> factories) {
        Map<FactoryId,Factory> map=new HashMap<>(); Set<String> codes=new HashSet<>();
        for(Factory factory:factories) { if(map.putIfAbsent(factory.id(),factory)!=null || !codes.add(factory.enterpriseId()+"/"+factory.code())) throw new IllegalArgumentException("Duplicate factory identity/code"); }
        entries=Map.copyOf(map);
    }
    public Optional<Factory> find(FactoryId id) { return Optional.ofNullable(entries.get(id)); }
    public List<Factory> enabled(int offset,int limit) { return entries.values().stream().filter(f -> f.status()==FactoryStatus.ENABLED).sorted(Comparator.comparing(f -> f.id().value())).skip(offset).limit(limit).toList(); }
}
