package dev.mesbase.infrastructure.cache;

import dev.mesbase.common.cache.ReferenceLabelCache;

import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.cache.ReferenceLabelCache.Label;
import java.util.HashMap;
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

class ReferenceLabelCacheTest {
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
        public void setCacheObjects(Map<String, byte[]> values, int ttl) {
            this.values.putAll(values);
        }
    }

    @Test
    void coldAndPartialMissUseOneDeduplicatedBatchAndWarmReadsUseNoDatabase() {
        var cache = new ReferenceLabelCache(new Memory(), new MockEnvironment());
        var calls = new AtomicInteger();
        var first = cache.getAll("factory:A:operation", List.of(2L, 1L, 1L), ids -> {
            calls.incrementAndGet();
            assertEquals(List.of(1L, 2L), ids);
            return Map.of(1L, new Label(1L, "A", "甲", null), 2L, new Label(2L, "B", "乙", null));
        });
        assertEquals(2, first.size());
        cache.getAll("factory:A:operation", List.of(1L, 2L), ids -> {
            throw new AssertionError("warm lookup queried database");
        });
        cache.getAll("factory:A:operation", List.of(2L, 3L), ids -> {
            calls.incrementAndGet();
            assertEquals(List.of(3L), ids);
            return Map.of(3L, new Label(3L, "C", "丙", null));
        });
        assertEquals(2, calls.get());
        assertEquals(Map.of(), cache.getAll("factory:A:operation", List.of(), ids -> {
            throw new AssertionError("empty lookup queried database");
        }));
    }

    @Test
    void factoryNamespacesAreIsolatedAndRetiredLoaderCannotRepublishAfterInvalidation() {
        var cache = new ReferenceLabelCache(new Memory(), new MockEnvironment());
        cache.getAll("factory:A:routing", List.of(1L), ids -> Map.of(1L, new Label(1L, "A", "甲厂", 1)));
        var other = cache.getAll("factory:B:routing", List.of(1L), ids -> Map.of(1L, new Label(1L, "B", "乙厂", 2)));
        assertEquals("乙厂", other.get(1L).name());
        cache.getAll("users", List.of(1L), ids -> {
            cache.invalidate("users");
            return Map.of(1L, new Label(1L, null, "旧名", null));
        });
        assertEquals("新名", cache.getAll("users", List.of(1L), ids -> Map.of(1L, new Label(1L, null, "新名", null)))
                .get(1L).name());
    }

    @Test
    void cacheFailureFallsBackOnceButDatabaseFailureIsNeverHidden() {
        var redis = mock(RedisCache.class);
        when(redis.getCacheObject(any())).thenThrow(new IllegalStateException("TEST ONLY REDIS"));
        var cache = new ReferenceLabelCache(redis, new MockEnvironment());
        assertEquals("姓名", cache.getAll("users", List.of(1L), ids -> Map.of(1L, new Label(1L, null, "姓名", null)))
                .get(1L).name());
        assertThrows(IllegalArgumentException.class, () -> cache.getAll("users", List.of(1L), ids -> {
            throw new IllegalArgumentException("TEST ONLY SQL");
        }));
    }
}
