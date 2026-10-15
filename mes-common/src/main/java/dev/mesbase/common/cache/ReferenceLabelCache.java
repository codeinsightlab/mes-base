package dev.mesbase.common.cache;

import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.mesbase.common.core.redis.RedisCache;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Display references only, never accounts, roles, permissions or production eligibility.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class ReferenceLabelCache {
    public static final String USERS = "reference:users";

    public record Label(Long id, String code, String name, Integer version,
                        String description, String status, Long departmentId, String deletionFlag) {
        public Label(Long id, String code, String name, Integer version) {
            this(id, code, name, version, null, null, null, null);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceLabelCache.class);
    private final RedisCache redis;
    private final int ttl;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public ReferenceLabelCache(RedisCache redis, Environment environment) {
        this.redis = redis;
        ttl = environment.getProperty("mes.business.cache.ttl-seconds", Integer.class, 300);
        if (ttl < 1) throw new IllegalArgumentException("Reference cache TTL must be positive");
    }

    public Map<Long, Label> getAll(String namespace, Collection<Long> requested,
                                   Function<List<Long>, Map<Long, Label>> loader) {
        var ids = requested.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() > 200) throw new IllegalArgumentException("At most 200 reference IDs");
        if (ids.isEmpty()) return Map.of();
        String version;
        var result = new LinkedHashMap<Long, Label>();
        try {
            version = redis.getCacheObject(namespace + ":version");
            if (version == null) version = "0";
            String generation = version;
            var values = redis.getCacheObjects(ids.stream().map(id -> key(namespace, generation, id)).toList());
            if (values != null) for (int i = 0; i < ids.size(); i++) {
                Object value = values.get(i);
                if (value instanceof byte[] bytes) {
                    Label label = json.readValue(bytes, Label.class);
                    if (!Objects.equals(ids.get(i), label.id()))
                        throw new IllegalStateException("Reference identity mismatch");
                    result.put(label.id(), label);
                }
            }
        } catch (Exception failure) {
            LOG.warn("Reference cache unavailable; batch database read namespace={} cause={}", namespace, failure.getClass()
                    .getSimpleName());
            return Map.copyOf(loader.apply(ids));
        }
        var missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            var loaded = loader.apply(missing); // Database failures must propagate, never become empty names.
            var writes = new LinkedHashMap<String, byte[]>();
            for (var id : missing) {
                var label = loaded.get(id);
                if (label == null) continue; // No negative caching: newly available references can be read immediately.
                if (!Objects.equals(id, label.id()))
                    throw new IllegalStateException("Reference loader identity mismatch");
                result.put(id, label);
                try {
                    writes.put(key(namespace, version, id), json.writeValueAsBytes(label));
                } catch (Exception failure) {
                    throw new IllegalArgumentException("Reference label cannot be encoded", failure);
                }
            }
            try {
                redis.setCacheObjects(writes, ttl);
            } catch (Exception failure) {
                LOG.warn("Reference cache fill failed namespace={} cause={}", namespace, failure.getClass()
                        .getSimpleName());
            }
        }
        return Map.copyOf(result);
    }

    private static String key(String namespace, String version, Long id) {
        return namespace + ":value:" + version + ":" + id;
    }

    /**
     * Advance the generation: an old in-flight loader can only publish into its retired generation.
     */
    public void invalidate(String namespace) {
        try {
            redis.setCacheObject(namespace + ":version", UUID.randomUUID().toString());
        } catch (Exception failure) {
            LOG.error("Reference invalidation failed; values remain bounded by TTL namespace={} cause={}", namespace, failure.getClass()
                    .getSimpleName());
        }
    }
}
