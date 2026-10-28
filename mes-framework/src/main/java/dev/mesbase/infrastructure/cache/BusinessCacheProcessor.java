package dev.mesbase.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import dev.mesbase.common.cache.DetailEntityCache;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict.Scope;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.common.core.redis.RedisKeySegment;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;
import java.lang.reflect.Type;
import java.lang.reflect.Parameter;
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
        return "business:{factory:" + RedisKeySegment.encode(factory) + ":resource:" + business + "}:";
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

    public String queryKey(Object[] arguments, Parameter[] parameters, BusinessCacheQuery query) {
        try {
            var namedArguments = new TreeMap<String, Object>();
            for (int i = 0; i < arguments.length; i++) {
                String name = i < parameters.length && parameters[i] != null && parameters[i].isNamePresent()
                        ? parameters[i].getName() : "arg" + i;
                if (sensitiveName(name) || containsSensitiveField(arguments[i])) return null;
                namedArguments.put(name, arguments[i]);
            }
            var conditions = new TreeMap<String, Object>();
            conditions.put("arguments", namedArguments);
            var pagination = new TreeMap<String, Object>();
            pagination.put("offset", query.cacheOffset());
            pagination.put("limit", query.cacheLimit());
            pagination.put("sort", query.cacheSort());
            conditions.put("pagination", pagination);
            JsonNode canonical = json.valueToTree(conditions);
            String serialized = json.writeValueAsString(readableConditionNode(canonical));
            if (serialized.length() > 2048) return null;
            return "conditions:" + serialized;
        } catch (Exception failure) {
            return null;
        }
    }

    private boolean containsSensitiveField(Object value) {
        if (value == null) return false;
        try {
            return sensitiveNode(json.valueToTree(value));
        } catch (IllegalArgumentException failure) {
            return true;
        }
    }

    private boolean sensitiveNode(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (sensitiveName(field.getKey()) || sensitiveNode(field.getValue())) return true;
            }
        } else if (node.isArray()) {
            for (var item : node) if (sensitiveNode(item)) return true;
        }
        return false;
    }

    private JsonNode readableConditionNode(JsonNode node) {
        if (node.isObject()) {
            var result = json.createObjectNode();
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                result.set(RedisKeySegment.encode(field.getKey()), readableConditionNode(field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            var result = json.createArrayNode();
            for (JsonNode item : node) result.add(readableConditionNode(item));
            return result;
        }
        if (node.isTextual()) return json.getNodeFactory().textNode(RedisKeySegment.encode(node.textValue()));
        return node.deepCopy();
    }

    private static boolean sensitiveName(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*(password|secret|token|credential|authorization).*");
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
