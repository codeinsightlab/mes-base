package dev.mesbase.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import dev.mesbase.common.cache.DetailEntityCache;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict.Scope;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.platform.execution.FactoryContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import com.github.pagehelper.Page;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Typed values use the existing Redis template/namespace; only cache I/O failures degrade.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessCacheProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessCacheProcessor.class);
    private final RedisCache redis;
    private final DetailEntityCache references;
    private final SpringCommitActions commits;
    private final int ttl;
    private final JsonMapper json = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).findAndAddModules().build();

    public BusinessCacheProcessor(RedisCache redis, SpringCommitActions commits, Environment environment) {
        this.redis = redis;
        references = new DetailEntityCache(redis, environment);
        this.commits = commits;
        ttl = environment.getProperty("mes.business.cache.ttl-seconds", Integer.class, 300);
        if (ttl < 1) throw new IllegalArgumentException("Business cache TTL must be positive");
    }

    public String prefix(String business) {
        validateBusiness(business);
        String factory = FactoryContext.required().factoryId().value();
        // Encode arbitrary factory IDs to avoid delimiters and Redis glob metacharacters.
        String segment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(factory.getBytes(StandardCharsets.UTF_8));
        // Length-delimit the resource to isolate even a nested namespace such as business:material:list.
        return "business:factory:" + segment + ":resource:" + business.length() + ":" + business + ":";
    }

    public DetailEntityCache.Generation detailGeneration(String resourcePrefix) {
        return references.generation(resourcePrefix);
    }

    public void writeDetail(String resourcePrefix, String key, Object value, DetailEntityCache.Generation generation) {
        if (value == null) return;
        try {
            references.write(resourcePrefix, generation, Map.of(key, json.writeValueAsBytes(value)));
        } catch (Exception failure) {
            LOG.warn("Detail cache serialization failed key={} cause={}", key, failure.getClass().getSimpleName());
        }
    }

    public static void validateBusiness(String business) {
        if (business == null || (business.length() > 256 || !business.matches("[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z0-9_-]+)+")))
            throw new IllegalArgumentException("Business cache namespace must be a permission resource prefix, e.g. business:material");
    }

    public String queryHash(Object[] arguments, BusinessCacheQuery query) {
        try {
            byte[] bytes = json.writeValueAsBytes(Map.of("arguments", arguments,
                    "offset", query.cacheOffset(), "limit", query.cacheLimit(), "sort", query.cacheSort()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw new IllegalArgumentException("Business cache query cannot be serialized stably", failure);
        }
    }

    public JavaType returnType(Type type) {
        return json.constructType(type);
    }


    public Object read(String key, JavaType type) {
        try {
            Object cached = redis.getCacheObject(key);
            if (cached == null) return null;
            if (!(cached instanceof byte[] bytes)) throw new IllegalStateException("Invalid business cache payload");
            return json.readValue(bytes, type);
        } catch (Exception failure) {
            LOG.warn("Business cache read failed; querying database key={} cause={}", key, failure.getClass()
                    .getSimpleName());
            return null;
        }
    }

    public void write(String key, Object value) {
        if (value == null) return;
        try {
            redis.setCacheObject(key, json.writeValueAsBytes(value), ttl, TimeUnit.SECONDS);
        } catch (Exception failure) {
            LOG.warn("Business cache write failed key={} cause={}", key, failure.getClass().getSimpleName());
        }
    }

    /**
     * Capture scope now, register only for successful completion; callback never reads FactoryContext.
     */
    public void evict(String business, Scope scope) {
        if (scope == null) throw new IllegalArgumentException("Business cache eviction scope required");
        evictCaptured(prefix(business), scope);
    }

    void evictCaptured(String captured, Scope scope) {
        Runnable action = () -> {
            references.invalidate(captured, false);
            try {
                redis.deleteByPattern(captured + "list:*");
                if (scope == Scope.LIST_AND_DETAIL) redis.deleteByPattern(captured + "detail:*");
            } catch (Exception failure) {
                LOG.error("Database write succeeded; business cache eviction failed prefix={} scope={} cause={}",
                        captured, scope, failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) commits.afterCommit(action);
        else action.run();
    }
}
