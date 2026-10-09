package dev.mesbase.infrastructure.cache;

import java.util.Optional;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.error.*;

public record BusinessCache(String environment, String enterprise) {
    public CacheKey key(FactoryScope scope, String namespace, String key) {
        if (scope == null) throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
        return new CacheKey(environment, enterprise, CacheKey.Kind.BUSINESS, Optional.of(scope.factoryId()), namespace, key);
    }

    public CacheKey currentKey(String namespace, String key) {
        return key(FactoryContext.required(), namespace, key);
    }
}
