package dev.mesbase.infrastructure.cache;

import dev.mesbase.common.cache.DetailEntityCache;

import dev.mesbase.common.core.redis.RedisCache;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class DetailEntityCacheTest {
    record TestEntity(Long id, String code, String name, Integer version) {}
    private Map<Long,TestEntity> getAll(DetailEntityCache cache, String namespace, List<Long> ids,
            Function<List<Long>, Map<Long,TestEntity>> loader) {
        return cache.getAll(namespace, ids, TestEntity.class, TestEntity::id, loader);
    }
    static class Memory extends RedisCache {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public <T> T getCacheObject(String key) {
            return (T) values.get(key);
        }

        @Override
        public <T> void setCacheObject(String key, T value) {
            values.put(key, value);
        }

        @Override
        public List<Object> getCacheObjects(List<String> keys) {
            return keys.stream().map(values::get).toList();
        }

        @Override
        public void setCacheObjectsIfVersion(String key, String expected, Map<String, byte[]> values, int ttl) {
            if (Objects.equals(expected, this.values.get(key))) this.values.putAll(values);
        }
        @Override public void deleteByPattern(String pattern) {
            String prefix = pattern.substring(0, pattern.length()-1);
            values.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @Test
    void coldAndPartialMissUseOneDeduplicatedBatchAndWarmReadsUseNoDatabase() {
        var memory = new Memory();
        var cache = new DetailEntityCache(memory, new MockEnvironment());
        var calls = new AtomicInteger();
        var first = getAll(cache, "factory:A:operation", List.of(2L, 1L, 1L), ids -> {
            calls.incrementAndGet();
            assertEquals(List.of(1L, 2L), ids);
            return Map.of(1L, new TestEntity(1L, "A", "甲", null), 2L, new TestEntity(2L, "B", "乙", null));
        });
        assertEquals(2, first.size());
        getAll(cache, "factory:A:operation", List.of(1L, 2L), ids -> {
            throw new AssertionError("warm lookup queried database");
        });
        getAll(cache, "factory:A:operation", List.of(2L, 3L), ids -> {
            calls.incrementAndGet();
            assertEquals(List.of(3L), ids);
            return Map.of(3L, new TestEntity(3L, "C", "丙", null));
        });
        assertEquals(2, calls.get());
        assertEquals(Set.of("factory:A:operation:detail:MQ", "factory:A:operation:detail:Mg", "factory:A:operation:detail:Mw"), memory.values.keySet());
        assertEquals(Map.of(), getAll(cache, "factory:A:operation", List.of(), ids -> {
            throw new AssertionError("empty lookup queried database");
        }));
    }

    @Test
    void factoryNamespacesAreIsolatedAndRetiredLoaderCannotRepublishAfterInvalidation() {
        var cache = new DetailEntityCache(new Memory(), new MockEnvironment());
        getAll(cache, "factory:A:routing", List.of(1L), ids -> Map.of(1L, new TestEntity(1L, "A", "甲厂", 1)));
        var other = getAll(cache, "factory:B:routing", List.of(1L), ids -> Map.of(1L, new TestEntity(1L, "B", "乙厂", 2)));
        assertEquals("乙厂", other.get(1L).name());
        getAll(cache, "users", List.of(1L), ids -> {
            cache.invalidate("users");
            return Map.of(1L, new TestEntity(1L, null, "旧名", null));
        });
        assertEquals("新名", getAll(cache, "users", List.of(1L), ids -> Map.of(1L, new TestEntity(1L, null, "新名", null)))
                .get(1L).name());
    }

    @Test
    void cacheFailureFallsBackOnceButDatabaseFailureIsNeverHidden() {
        var redis = mock(RedisCache.class);
        when(redis.getCacheObject(any())).thenThrow(new IllegalStateException("TEST ONLY REDIS"));
        var cache = new DetailEntityCache(redis, new MockEnvironment());
        assertEquals("姓名", getAll(cache, "users", List.of(1L), ids -> Map.of(1L, new TestEntity(1L, null, "姓名", null)))
                .get(1L).name());
        assertThrows(IllegalArgumentException.class, () -> getAll(cache, "users", List.of(1L), ids -> {
            throw new IllegalArgumentException("TEST ONLY SQL");
        }));
    }
}
