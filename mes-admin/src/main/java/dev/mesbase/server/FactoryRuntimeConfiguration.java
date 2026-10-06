package dev.mesbase.server;

import dev.mesbase.core.factory.*;
import dev.mesbase.infrastructure.persistence.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.*;

/** Database-managed Factory catalog and lazy node-local runtimes; no YAML factory authority. */
@Configuration

@ConditionalOnProperty(name="mes.runtime.enabled",havingValue="true")
public class FactoryRuntimeConfiguration {
    @Bean(destroyMethod="close") RuntimeRegistry runtimeRegistry() { return new RuntimeRegistry(); }
    @Bean dev.mesbase.server.managed.ManagedDatasourceService managedDatasources(Environment env,
            dev.mesbase.framework.persistence.SystemUnit unit, PlatformPersistence persistence,
            DataSource platformDataSource, RuntimeRegistry registry,
            ObjectProvider<BusinessMapperRegistration> registrations) {
        if(!env.getProperty("mes.platform.enabled",Boolean.class,false)) throw new IllegalArgumentException("Factory Runtime requires explicit Platform persistence");
        var key=env.getRequiredProperty("mes.runtime.datasource-key");
        var mappers=new HashSet<Class<?>>();registrations.orderedStream().forEach(r -> mappers.add(r.mapper()));
        return new dev.mesbase.server.managed.ManagedDatasourceService(persistence,unit,
            new dev.mesbase.server.managed.DatasourcePasswordCipher(key),registry,platformDataSource,DruidPools.settings(env),mappers,java.nio.file.Path.of(env.getRequiredProperty("mes.platform.file-root")).resolve("managed-ddl"));
    }
    @Bean FactoryCatalog runtimeCatalog(dev.mesbase.server.managed.ManagedDatasourceService service) {
        return new FactoryCatalog() {
            public Optional<Factory> find(FactoryId id) { return service.find(id); }
            public List<Factory> enabled(int offset,int limit) { return service.enabled(offset,limit); }
        };
    }
    @Bean BusinessPersistenceResolver runtimeResolver(dev.mesbase.server.managed.ManagedDatasourceService service) { return service::resolve; }

}
