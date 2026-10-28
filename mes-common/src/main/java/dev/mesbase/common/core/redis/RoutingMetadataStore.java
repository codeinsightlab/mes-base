package dev.mesbase.common.core.redis;

import java.time.Duration;
import java.util.Collection;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Shared routing definitions only; each key has an independent fixed lifetime.
 */
public class RoutingMetadataStore {
    private final StringRedisTemplate redis;
    private final SystemCacheNamespace namespace;
    private final Duration ttl;

    public RoutingMetadataStore(StringRedisTemplate redis, SystemCacheNamespace namespace, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Positive metadata TTL required");
        this.redis = redis;
        this.namespace = namespace;
        this.ttl = ttl;
    }

    private String key(String field) {
        int separator = field.indexOf(':');
        String readable = separator < 0 ? RedisKeySegment.encode(field)
                : field.substring(0, separator + 1) + RedisKeySegment.encode(field.substring(separator + 1));
        return namespace.key("factory-routing:entry:" + readable);
    }

    public String read(String field) {
        return redis.opsForValue().get(key(field));
    }

    public void publish(String field, String payload) {
        redis.opsForValue().set(key(field), payload, ttl);
    }

    public void invalidate(Collection<String> fields) {
        redis.delete(fields.stream().map(this::key).toList());
    }
}
