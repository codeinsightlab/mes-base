package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import dev.mesbase.infrastructure.persistence.BusinessPersistence;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DedicatedDatabasePersistence;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.ErpRoutingDataSource;
import dev.mesbase.infrastructure.persistence.ErpSourceAspect;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class PersistenceConfiguration {
    @Bean
    static OwnerMapperRegistration ownerMapperRegistration(Environment environment) {
        return new OwnerMapperRegistration(environment);
    }

    @Bean(destroyMethod = "close")
    DataSource platformDataSource(Environment env) {
        DruidDataSource source = DruidPools.create(DruidPools.settings(env), "platform", env.getRequiredProperty("mes.platform.url"), env.getRequiredProperty("mes.platform.username"), env.getRequiredProperty("mes.platform.password"));
        try {
            if (env.getProperty("mes.platform.migrate", Boolean.class, false))
                Flyway.configure().dataSource(source).locations("classpath:db/platform", "classpath:db/system").load().migrate();
            return source;
        } catch (RuntimeException | Error failure) {
            source.close();
            throw failure;
        }
    }

    @Bean
    OwnedTransactionManager platformTransactionManager(@Qualifier("platformDataSource") DataSource source) {
        return new OwnedTransactionManager(source);
    }

    @Bean
    SqlSessionFactory platformSqlSessionFactory(@Qualifier("platformDataSource") DataSource source, @Qualifier("platformTransactionManager") OwnedTransactionManager manager, OwnerMapperCatalog catalog) {
        return PersistenceFactories.create(source, catalog.types(PersistenceOwner.Kind.PLATFORM), configuration -> {
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.system.openapi.domain");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.quartz.domain");
            PersistenceFactories.pagination(configuration);
        }, manager::verifyAccess);
    }

    @Bean
    PlatformPersistence platformPersistence(@Qualifier("platformSqlSessionFactory") SqlSessionFactory factory, @Qualifier("platformTransactionManager") OwnedTransactionManager manager, OwnerMapperCatalog catalog) {
        return new PlatformPersistence(factory, manager, catalog.types(PersistenceOwner.Kind.PLATFORM));
    }

    @Bean
    SpringCommitActions commitActions() {
        return new SpringCommitActions();
    }

    @Bean
    FactoryBusinessDataSource businessDataSource(BusinessPersistenceResolver resolver, RuntimeRegistry registry) {
        return new FactoryBusinessDataSource(resolver, registry);
    }

    @Bean
    OwnedTransactionManager businessTransactionManager(FactoryBusinessDataSource source) {
        return new OwnedTransactionManager(source);
    }

    @Bean
    SqlSessionFactory businessSqlSessionFactory(FactoryBusinessDataSource source, @Qualifier("businessTransactionManager") OwnedTransactionManager manager, OwnerMapperCatalog catalog) {
        return PersistenceFactories.create(source, catalog.types(PersistenceOwner.Kind.BUSINESS), PersistenceFactories::pagination, manager::verifyAccess);
    }

    @Bean
    BusinessPersistence businessPersistence(@Qualifier("businessSqlSessionFactory") SqlSessionFactory factory, @Qualifier("businessTransactionManager") OwnedTransactionManager manager, OwnerMapperCatalog catalog) {
        return new DedicatedDatabasePersistence(factory, manager, catalog.types(PersistenceOwner.Kind.BUSINESS));
    }

    @Configuration
    @ConditionalOnProperty(name = "mes.erp.enabled", havingValue = "true")
    static class ErpPersistence {
        @Bean(destroyMethod = "close")
        DataSource erpDataSource(Environment env) {
            Map<String, ErpConnectionProperties> configured = Binder.get(env).bind("mes.erp.datasources",
                Bindable.mapOf(String.class, ErpConnectionProperties.class))
                .orElseThrow(() -> new IllegalArgumentException("ERP datasource map is required"));
            Map<String, DataSource> pools = new LinkedHashMap<>();
            try {
                configured.forEach((id, connection) -> pools.put(id, DruidPools.create(DruidPools.settings(env),
                    "erp-" + id, connection.url(), connection.username(), connection.password(), connection.driverClassName())));
                return new ErpRoutingDataSource(pools);
            } catch (RuntimeException | Error failure) {
                for (DataSource pool : pools.values()) try { ((DruidDataSource) pool).close(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        record ErpConnectionProperties(String url, String username, String password, String driverClassName) {
            ErpConnectionProperties {
                if (url == null || !url.startsWith("jdbc:") || username == null || username.isBlank()
                        || password == null || driverClassName == null || driverClassName.isBlank())
                    throw new IllegalArgumentException("ERP connection requires explicit url, username, password and driver class");
            }
            @Override public String toString() { return "ErpConnectionProperties[REDACTED]"; }
        }
        @Bean
        ErpSourceAspect erpSourceAspect() {
            return new ErpSourceAspect();
        }
        @Bean
        OwnedTransactionManager erpTransactionManager(@Qualifier("erpDataSource") DataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory erpSqlSessionFactory(@Qualifier("erpDataSource") DataSource source, @Qualifier("erpTransactionManager") OwnedTransactionManager manager, OwnerMapperCatalog catalog) {
            return PersistenceFactories.create(source, catalog.types(PersistenceOwner.Kind.ERP), PersistenceFactories::pagination, manager::verifyAccess);
        }
    }
}
