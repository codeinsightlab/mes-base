package dev.mesbase.server.managed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.core.redis.RoutingMetadataStore;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.web.HttpFailureLogging;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.factory.FactoryExecutionMetadata;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import java.util.Collection;
import java.util.ArrayList;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Low-frequency management metadata: cache-aside with fixed TTL, no durable invalidation protocol.
 */
public class FactoryMetadataCache {
    private static final Logger LOG = LoggerFactory.getLogger(FactoryMetadataCache.class);
    private final PlatformPersistence persistence;
    private final RoutingMetadataStore store;
    private final RoutingMetadataMapper mapper;
    private final ObjectMapper json = new ObjectMapper();

    public FactoryMetadataCache(PlatformPersistence persistence, RoutingMetadataStore store,
                                RoutingMetadataMapper mapper) {
        this.persistence = persistence;
        this.store = store;
        this.mapper = mapper;
    }

    public FactoryExecutionMetadata definition(String id) {
        String field = "binding-v2:" + id;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String key = FactoryMetadataCache.class.getName() + field;
            Object cached = attrs.getRequest().getAttribute(key);
            if (cached instanceof FactoryExecutionMetadata value) return value;
            var value = read(field, () -> definitionFromDatabase(id));
            if (value != null) attrs.getRequest().setAttribute(key, value);
            return value;
        }
        return read(field, () -> definitionFromDatabase(id));
    }

    public FactoryExecutionMetadata taskDefinition(String id) {
        return read("binding-v2:" + id, () -> definitionFromDatabase(id));
    }

    public FactoryExecutionMetadata defaultDefinition() {
        var value = read("default-v2", () -> defaultFromDatabase());
        if (value != null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
            attrs.getRequest()
                    .setAttribute(FactoryMetadataCache.class.getName() + "binding-v2:" + value.factoryId(), value);
        return value;
    }

    private FactoryExecutionMetadata definitionFromDatabase(String id) {
        if (!persistence.transactionManager().foreignTransactionActive()) return mapper.definition(id);
        return persistence.readOnly(s -> s.mapper(RoutingMetadataMapper.class).definition(id));
    }

    private FactoryExecutionMetadata defaultFromDatabase() {
        if (!persistence.transactionManager().foreignTransactionActive()) return mapper.defaultDefinition();
        return persistence.readOnly(s -> s.mapper(RoutingMetadataMapper.class).defaultDefinition());
    }

    private FactoryExecutionMetadata read(String field, Supplier<FactoryExecutionMetadata> loader) {
        try {
            String cached = store.read(field);
            if (cached != null) return json.readValue(cached, FactoryExecutionMetadata.class);
            var loaded = loader.get();
            // Unknown caller-supplied IDs never create shared cache entries.
            if (loaded != null) store.publish(field, json.writeValueAsString(loaded));
            return loaded;
        } catch (JsonProcessingException | RuntimeException failure) {
            if (failure instanceof FoundationException foundation) throw foundation;
            var wrapped = new FoundationException(ErrorCode.ROUTING_METADATA_UNAVAILABLE, failure);
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpFailureLogging.known(attrs.getRequest(), wrapped);
            } else {
                LOG.error("Routing metadata read or publication failed", failure);
            }
            throw wrapped;
        }
    }

    /**
     * Called in the configuration transaction; rollback does not evict.
     */
    public void changed(Collection<String> factoryIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Configuration transaction required");
        var fields = new ArrayList<String>();
        fields.add("default-v2");
        factoryIds.stream().distinct().map(id -> "binding-v2:" + id).forEach(fields::add);
        persistence.afterCommit(() -> {
            try {
                store.invalidate(fields);
            } catch (RuntimeException failure) {
                // The configuration is committed: report success, log failure, expire stale entries normally.
                LOG.error("Routing configuration committed; cache eviction failed, waiting for fixed TTL", failure);
            }
        });
    }
}
