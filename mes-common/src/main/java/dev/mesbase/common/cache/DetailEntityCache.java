package dev.mesbase.common.cache;

import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.mesbase.common.core.redis.RedisCache;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Shared canonical entity details with batch misses and guarded fills; never makes authorization decisions.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class DetailEntityCache {

    private static final Logger LOG = LoggerFactory.getLogger(DetailEntityCache.class);
    private final RedisCache redis;
    private final int ttl;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public DetailEntityCache(RedisCache redis, Environment environment) {
        this.redis = redis;
        ttl = environment.getProperty("mes.business.cache.ttl-seconds", Integer.class, 300);
        if (ttl < 1) throw new IllegalArgumentException("Reference cache TTL must be positive");
    }

    public record Generation(boolean available, String value) {
    }

    public Generation generation(String namespace) {
        try {
            return new Generation(true, redis.getCacheObject(DetailCacheKeys.version(namespace)));
        } catch (Exception failure) {
            LOG.warn("Detail cache version unavailable namespace={} cause={}", namespace, failure.getClass()
                    .getSimpleName());
            return new Generation(false, null);
        }
    }

    public <T> Map<Long, T> getAll(String namespace, Collection<Long> requested, Class<T> type,
                                   Function<T, Long> identity, Function<List<Long>, Map<Long, T>> loader) {
        return getAll(namespace, requested, type, identity, loader, null);
    }

    public <T> Map<Long, T> getAll(String namespace, Collection<Long> requested, Class<T> type,
                                   Function<T, Long> identity, Function<List<Long>, Map<Long, T>> loader,
                                   Set<String> allowedFields) {
        var ids = requested.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() > 200) throw new IllegalArgumentException("At most 200 reference IDs");
        if (ids.isEmpty()) return Map.of();
        var generation = generation(namespace);
        if (!generation.available()) return Map.copyOf(loader.apply(ids));
        var result = new LinkedHashMap<Long, T>();
        try {
            var values = redis.getCacheObjects(ids.stream().map(id -> DetailCacheKeys.detail(namespace, id)).toList());
            if (values != null) for (int i = 0; i < ids.size(); i++) {
                Object value = values.get(i);
                if (value instanceof byte[] bytes) {
                    T row = json.readValue(bytes, type);
                    if (!Objects.equals(ids.get(i), identity.apply(row)))
                        throw new IllegalStateException("Detail identity mismatch");
                    result.put(ids.get(i), row);
                }
            }
        } catch (Exception failure) {
            LOG.warn("Detail batch cache unavailable; querying database namespace={} cause={}", namespace, failure.getClass()
                    .getSimpleName());
            return Map.copyOf(loader.apply(ids));
        }
        var missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            var loaded = loader.apply(missing);
            var writes = new LinkedHashMap<String, byte[]>();
            for (var id : missing) {
                var row = loaded.get(id);
                if (row == null) continue;
                if (!Objects.equals(id, identity.apply(row)))
                    throw new IllegalStateException("Detail loader identity mismatch");
                result.put(id, row);
                try {
                    ObjectNode value = json.valueToTree(row);
                    if (allowedFields != null) value.retain(allowedFields);
                    writes.put(DetailCacheKeys.detail(namespace, id), json.writeValueAsBytes(value));
                } catch (Exception failure) {
                    throw new IllegalArgumentException("Detail cannot be encoded", failure);
                }
            }
            write(namespace, generation, writes);
        }
        return Map.copyOf(result);
    }

    public void write(String namespace, Generation generation, Map<String, byte[]> values) {
        if (!generation.available()) return;
        try {
            redis.setCacheObjectsIfVersion(DetailCacheKeys.version(namespace), generation.value(), values, ttl);
        } catch (Exception failure) {
            LOG.warn("Detail cache fill failed namespace={} cause={}", namespace, failure.getClass().getSimpleName());
        }
    }

    /**
     * Advance the shared detail write fence; old in-flight fills are rejected atomically.
     */
    public void invalidate(String namespace) {
        invalidate(namespace, true);
    }

    public void invalidate(String namespace, boolean removeDetails) {
        try {
            redis.setCacheObject(DetailCacheKeys.version(namespace), UUID.randomUUID().toString());
            if (removeDetails) redis.deleteByPattern(DetailCacheKeys.detailPattern(namespace));
        } catch (Exception failure) {
            LOG.error("Reference invalidation failed; values remain bounded by TTL namespace={} cause={}", namespace, failure.getClass()
                    .getSimpleName());
        }
    }
}
