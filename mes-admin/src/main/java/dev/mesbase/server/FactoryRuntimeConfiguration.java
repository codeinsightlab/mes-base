package dev.mesbase.server;

import dev.mesbase.core.factory.*;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/** Explicit local MySQL development assembly; never enabled by a Codegen profile. */
@Configuration

@ConditionalOnProperty(name="mes.runtime.enabled",havingValue="true")
public class FactoryRuntimeConfiguration {
    public record FactorySettings(String id, String enterpriseId, String code, String name,
                                  FactoryStatus status, String datasourceIdentity, long revision,
                                  String url, String username, String password) {
        @Override public String toString() { return "FactorySettings[REDACTED]"; }
    }

    public record Assembly(FactoryCatalog catalog, Map<FactoryId,BusinessRuntimeBinding> bindings,
                           RuntimeRegistry registry) implements AutoCloseable {
        @Override public void close() { registry.close(); }
    }

    @Bean(destroyMethod="close") Assembly runtimeAssembly(Environment env,
            DataSource platformDataSource, ObjectProvider<BusinessMapperRegistration> registrations) {
        if(!env.getProperty("mes.platform.enabled",Boolean.class,false)) throw new IllegalArgumentException("Factory Runtime requires explicit Platform persistence");
        if(Arrays.asList(env.getActiveProfiles()).contains("demo")) throw new IllegalArgumentException("Factory Runtime cannot use synthetic demo configuration");
        var settings=Binder.get(env).bind("mes.runtime.factories",Bindable.listOf(FactorySettings.class))
                .orElseThrow(() -> new IllegalArgumentException("Explicit development factories required"));
        var mappers=new HashSet<Class<?>>();
        registrations.orderedStream().forEach(r -> mappers.add(r.mapper()));
        var pool=DruidPools.settings(env);
        return prepare(settings, platformDataSource, mappers,pool);
    }

    /** Prepare all physical pools before exposing a catalog/binding/registry to requests. */
    public static Assembly prepare(List<FactorySettings> settings, DataSource platform, Set<Class<?>> mappers,DruidPools.Settings poolSettings) {
        if(settings.isEmpty() || settings.size()>100) throw new IllegalArgumentException("Invalid development factory count");
        var factories=new ArrayList<Factory>();
        var bindings=new HashMap<FactoryId,BusinessRuntimeBinding>();
        var identities=new HashSet<DatasourceIdentity>();
        for(var s:settings) {
            if(s.enterpriseId()==null || s.enterpriseId().isBlank() || s.code()==null || s.code().isBlank()
                    || s.name()==null || s.name().isBlank() || s.status()==null)
                throw new IllegalArgumentException("Incomplete development factory");
            var factory=new Factory(new FactoryId(s.id()),s.enterpriseId(),s.code(),s.name(),s.status());
            factories.add(factory);
            if(s.url()==null || !s.url().startsWith("jdbc:mysql://") || s.username()==null
                    || s.username().isBlank() || s.password()==null || s.password().isBlank())
                throw new IllegalArgumentException("Explicit MySQL development connection required");
            var identity=new DatasourceIdentity(s.datasourceIdentity());
            if(!identities.add(identity) || bindings.putIfAbsent(factory.id(),
                    new BusinessRuntimeBinding(factory.id(),identity,s.revision()))!=null)
                throw new IllegalArgumentException("Duplicate development factory/runtime");
        }
        var catalog=new InMemoryFactoryCatalog(factories);
        var registry=new RuntimeRegistry();
        var physical=new HashSet<String>();
        try {
            physical.add(physicalIdentity(platform));
            for(var s:settings) {
                var pool=DruidPools.create(poolSettings,"business-"+s.datasourceIdentity(),s.url(),s.username(),s.password());
                try {
                    if(!physical.add(physicalIdentity(pool))) throw new IllegalArgumentException("Development databases must be physically distinct");
                    var binding=bindings.get(new FactoryId(s.id()));
                    registry.registerDedicated(binding.factoryId(),binding.datasourceIdentity(),binding.revision(),pool,pool::close,mappers);
                } catch(RuntimeException | Error failure) { pool.close();throw failure; }
            }
            return new Assembly(catalog,Map.copyOf(bindings),registry);
        } catch(RuntimeException | Error failure) {
            try { registry.close(); } catch(RuntimeException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private static String physicalIdentity(DataSource source) {
        try(var connection=source.getConnection()) {
            if(!connection.getMetaData().getDatabaseProductName().equals("MySQL"))
                throw new IllegalArgumentException("Local development assembly requires MySQL");
            try(var statement=connection.prepareStatement("SELECT @@server_uuid, DATABASE()");var rows=statement.executeQuery()) {
                if(!rows.next() || rows.getString(1)==null || rows.getString(2)==null)
                    throw new IllegalArgumentException("Explicit physical development database required");
                return rows.getString(1)+"/"+rows.getString(2);
            }
        } catch(SQLException failure) { throw new IllegalStateException("Development database verification failed",failure); }
    }

    @Bean FactoryCatalog runtimeCatalog(Assembly assembly) { return assembly.catalog(); }
    @Bean ConfiguredBusinessPersistenceResolver runtimeResolver(Assembly assembly) {
        return new ConfiguredBusinessPersistenceResolver(assembly.catalog(),assembly.bindings());
    }
    @Bean(destroyMethod="") RuntimeRegistry runtimeRegistry(Assembly assembly) { return assembly.registry(); }
}
