package dev.mesbase.server;

import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.FactoryMetadataCache;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import dev.mesbase.server.integration.e10.FactoryOapiService;
import dev.mesbase.platform.execution.FactoryIntegrationExecutor;
import dev.mesbase.common.core.redis.RoutingMetadataStore;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import org.springframework.data.redis.core.StringRedisTemplate;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Database-managed Factory catalog and lazy node-local runtimes; no YAML factory authority.
 */
@Configuration

@ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "true")
public class FactoryRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    RuntimeRegistry runtimeRegistry() {
        return new RuntimeRegistry();
    }

    @Bean
    RoutingMetadataStore routingMetadataStore(StringRedisTemplate redis, SystemCacheNamespace namespace,
                                              Environment env) {
        return new RoutingMetadataStore(redis, namespace, Duration.ofSeconds(env.getRequiredProperty("mes.datasource.metadata-ttl-seconds", Long.class)));
    }

    @Bean
    FactoryMetadataCache factoryMetadataCache(PlatformPersistence persistence, RoutingMetadataStore store,
                                              RoutingMetadataMapper mapper) {
        return new FactoryMetadataCache(persistence, store, mapper);
    }

    @Bean
    ManagedDatasourceService managedDatasources(Environment env,
                                                PlatformPersistence persistence, ManagedDatasourceMapper sources,
                                                ManagedFactoryMapper factories,
                                                @Qualifier("masterDataSource") DataSource masterDataSource,
                                                RuntimeRegistry registry,
                                                OwnerMapperCatalog catalog, FactoryMetadataCache metadata,
                                                FactoryIntegrationRegistry integrations) {
        if (!env.getProperty("mes.platform.enabled", Boolean.class, false))
            throw new IllegalArgumentException("Factory Runtime requires explicit Platform persistence");
        var key = env.getRequiredProperty("mes.runtime.datasource-key");
        var mappers = catalog.types(PersistenceOwner.Kind.BUSINESS);
        return new ManagedDatasourceService(persistence, sources, factories,
                new DatasourcePasswordCipher(key), registry, masterDataSource, DruidPools.settings(env), mappers, Path.of(env.getRequiredProperty("mes.platform.file-root"))
                .resolve("managed-ddl"), metadata, integrations);
    }

    @Bean
    FactoryIntegrationRegistry factoryIntegrations(Environment environment, FactoryOapiService oapi) {
        return new FactoryIntegrationRegistry(environment, oapi);
    }

    @Bean
    FactoryOapiService factoryOapiService(ManagedFactoryMapper mapper, PlatformPersistence persistence,
                                          FactoryMetadataCache metadata, Environment environment) {
        return new FactoryOapiService(mapper, persistence,
                new DatasourcePasswordCipher(environment.getRequiredProperty("mes.runtime.datasource-key")), metadata);
    }

    @Bean
    FactoryIntegrationExecutor factoryIntegrationExecutor(ManagedDatasourceService service) {
        return service::runIntegrated;
    }

    @Bean
    FactoryCatalog runtimeCatalog(ManagedDatasourceService service) {
        return new FactoryCatalog() {
            public Optional<Factory> find(FactoryId id) {
                return service.find(id);
            }

            public Optional<Factory> defaultFactory() {
                return service.defaultFactory();
            }

            public List<Factory> enabled(int offset, int limit) {
                return service.enabled(offset, limit);
            }
        };
    }

    @Bean
    BusinessPersistenceResolver runtimeResolver(ManagedDatasourceService service) {
        return new BusinessPersistenceResolver() {
            public BusinessRuntimeBinding resolve(FactoryScope scope) {
                return service.resolve(scope);
            }

            public Acquisition begin(FactoryScope scope, RuntimeRegistry registry) {
                return service.begin(scope);
            }
        };
    }

}
