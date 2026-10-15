package dev.mesbase.server;

import dev.mesbase.system.factory.RoutingMetadataMapper;
import dev.mesbase.common.core.redis.RoutingMetadataStore;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.server.managed.FactoryMetadataCache;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * TEST ONLY synthetic cache; real SQL and production routing, no Redis timing claims.
 */
public final class RoutingCacheFixture {
    private RoutingCacheFixture() {
    }

    public static RoutingMetadataStore store() {
        var store = mock(RoutingMetadataStore.class);
        Map<String, String> values = new ConcurrentHashMap<>();
        when(store.read(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        doAnswer(call -> {
            values.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(store).publish(anyString(), anyString());
        doAnswer(call -> {
            Collection<String> fields = call.getArgument(0);
            fields.forEach(values::remove);
            return null;
        }).when(store).invalidate(any());
        return store;
    }

    public static FactoryMetadataCache cache(PlatformPersistence persistence) {
        return new FactoryMetadataCache(persistence, store(), persistence.mapper(RoutingMetadataMapper.class));
    }
}
