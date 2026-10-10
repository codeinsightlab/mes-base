package dev.mesbase.server;

import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import java.nio.file.Path;
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
    ManagedDatasourceService managedDatasources(Environment env,
                                                PlatformPersistence persistence,
                                                @Qualifier("masterDataSource") DataSource masterDataSource,
                                                RuntimeRegistry registry,
                                                OwnerMapperCatalog catalog) {
        if (!env.getProperty("mes.platform.enabled", Boolean.class, false))
            throw new IllegalArgumentException("Factory Runtime requires explicit Platform persistence");
        var key = env.getRequiredProperty("mes.runtime.datasource-key");
        var mappers = catalog.types(PersistenceOwner.Kind.BUSINESS);
        return new ManagedDatasourceService(persistence,
                new DatasourcePasswordCipher(key), registry, masterDataSource, DruidPools.settings(env), mappers, Path.of(env.getRequiredProperty("mes.platform.file-root"))
                .resolve("managed-ddl"));
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
        return service::resolve;
    }

}
