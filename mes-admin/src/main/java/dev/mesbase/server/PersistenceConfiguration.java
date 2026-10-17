package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import dev.mesbase.infrastructure.cache.BusinessCacheAspect;
import dev.mesbase.infrastructure.persistence.BusinessPersistence;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DedicatedDatabasePersistence;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import dev.mesbase.server.schema.FactoryFlyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = BusinessCacheAspect.TRANSACTION_ORDER)
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class PersistenceConfiguration {
    @Bean
    static OwnerMapperRegistration ownerMapperRegistration(Environment environment) {
        return new OwnerMapperRegistration(environment);
    }

    @Bean(destroyMethod = "close")
    DataSource masterDataSource(Environment env) {
        DruidDataSource source = DruidPools.create(DruidPools.settings(env), "platform", env.getRequiredProperty("mes.platform.url"), env.getRequiredProperty("mes.platform.username"), env.getRequiredProperty("mes.platform.password"));
        try {
            if (env.getProperty("mes.platform.migrate", Boolean.class, false))
                FactoryFlyway.configure(source, env).load()
                        .migrate();
            return source;
        } catch (RuntimeException | Error failure) {
            source.close();
            throw failure;
        }
    }

    @Bean
    OwnedTransactionManager masterTransactionManager(@Qualifier("masterDataSource") DataSource source) {
        return new OwnedTransactionManager(source);
    }

    @Bean
    SqlSessionFactory masterSqlSessionFactory(@Qualifier("masterDataSource") DataSource source,
                                              @Qualifier("masterTransactionManager") OwnedTransactionManager manager,
                                              OwnerMapperCatalog catalog) {
        return PersistenceFactories.create(source, catalog.types(PersistenceOwner.Kind.PLATFORM), configuration -> {
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.system.openapi.domain");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.quartz.domain");
            PersistenceFactories.pagination(configuration);
        }, manager::verifyAccess);
    }

    @Bean
    PlatformPersistence platformPersistence(@Qualifier("masterSqlSessionFactory") SqlSessionFactory factory,
                                            @Qualifier("masterTransactionManager") OwnedTransactionManager manager,
                                            OwnerMapperCatalog catalog) {
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
    SqlSessionFactory businessSqlSessionFactory(FactoryBusinessDataSource source,
                                                @Qualifier("businessTransactionManager") OwnedTransactionManager manager,
                                                OwnerMapperCatalog catalog) {
        return PersistenceFactories.create(source, catalog.types(PersistenceOwner.Kind.BUSINESS), PersistenceFactories::pagination, manager::verifyAccess);
    }

    @Bean
    BusinessPersistence businessPersistence(@Qualifier("businessSqlSessionFactory") SqlSessionFactory factory,
                                            @Qualifier("businessTransactionManager") OwnedTransactionManager manager,
                                            OwnerMapperCatalog catalog) {
        return new DedicatedDatabasePersistence(factory, manager, catalog.types(PersistenceOwner.Kind.BUSINESS));
    }

}
