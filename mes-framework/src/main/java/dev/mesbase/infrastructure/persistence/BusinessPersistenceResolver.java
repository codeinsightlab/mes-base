package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.factory.FactoryScope;

public interface BusinessPersistenceResolver {
    record Acquisition(BusinessRuntimeBinding binding, RuntimeRegistry.Lease lease) {
    }

    BusinessRuntimeBinding resolve(FactoryScope scope);

    default Acquisition begin(FactoryScope scope, RuntimeRegistry registry) {
        var binding = resolve(scope);
        return new Acquisition(binding, registry.acquireDedicated(binding));
    }
}
