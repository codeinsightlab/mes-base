package dev.mesbase.infrastructure.persistence;
import java.util.Map;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.error.*;
public final class ConfiguredBusinessPersistenceResolver implements BusinessPersistenceResolver {
    private final FactoryCatalog catalog;
    private volatile Map<FactoryId,BusinessRuntimeBinding> bindings;
    public ConfiguredBusinessPersistenceResolver(FactoryCatalog catalog,Map<FactoryId,BusinessRuntimeBinding> bindings) { this.catalog=catalog; replace(bindings); }
    /** Trusted configuration boundary, never a request-supplied datasource key. */
    public void replace(Map<FactoryId,BusinessRuntimeBinding> next) {
        next.forEach((key,value) -> { if(!key.equals(value.factoryId())) throw new IllegalArgumentException("Factory binding mismatch"); }); bindings=Map.copyOf(next);
    }
    public BusinessRuntimeBinding resolve(FactoryScope scope) {
        Factory factory=catalog.find(scope.factoryId()).orElseThrow(() -> new FoundationException(ErrorCode.FACTORY_NOT_FOUND));
        if(factory.status()!=FactoryStatus.ENABLED) throw new FoundationException(ErrorCode.FACTORY_DISABLED);
        BusinessRuntimeBinding binding=bindings.get(scope.factoryId());
        if(binding==null) throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        return binding;
    }
}
