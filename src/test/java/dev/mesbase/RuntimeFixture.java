package dev.mesbase;
import com.zaxxer.hikari.*;
import java.util.*;
import org.apache.ibatis.annotations.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.*;
/** TEST ONLY synthetic probes. Not a production entity, migration or schema. */
final class RuntimeFixture implements AutoCloseable {
    static final FactoryScope A=new FactoryScope(new FactoryId("A")), B=new FactoryScope(new FactoryId("B"));
    record Probe(int id,String payload) {}
    @PersistenceOwner(PersistenceOwner.Kind.BUSINESS)
    interface ProbeMapper {
        @Select("SELECT payload FROM foundation_probe WHERE id = #{id}") String read(int id);
        @Insert("INSERT INTO foundation_probe(id,payload) VALUES(#{id},#{payload})") int insert(Probe probe);
    }
    @PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
    interface PlatformMapper { @Select("SELECT payload FROM foundation_probe WHERE id = #{id}") String read(int id); }
    interface UnownedMapper { @Select("SELECT 1") int read(); }
    final InMemoryFactoryCatalog catalog;
    final RuntimeRegistry registry=new RuntimeRegistry();
    final Map<FactoryId,BusinessRuntimeBinding> bindings=new HashMap<>();
    final ConfiguredBusinessPersistenceResolver resolver;
    final DedicatedDatabasePersistence persistence;
    final Map<String,HikariDataSource> pools=new HashMap<>();
    RuntimeFixture(int factoryCount) throws Exception {
        List<Factory> factories=new ArrayList<>();
        for(String name:factoryCount==1 ? List.of("A") : List.of("A","B")) factories.add(new Factory(new FactoryId(name),"synthetic-enterprise",name,"TEST ONLY "+name,FactoryStatus.ENABLED));
        factories.add(new Factory(new FactoryId("C"),"synthetic-enterprise","C","TEST ONLY disabled",FactoryStatus.DISABLED));
        factories.add(new Factory(new FactoryId("D"),"synthetic-enterprise","D","TEST ONLY no runtime",FactoryStatus.ENABLED));
        catalog=new InMemoryFactoryCatalog(factories);
        for(String name:factoryCount==1 ? List.of("A") : List.of("A","B")) {
            HikariDataSource pool=pool(name); pools.put(name,pool); install(pool,name);
            registry.registerDedicated(new FactoryId(name),new DatasourceIdentity("db-"+name),1,pool,pool::close,Set.of(ProbeMapper.class));
            bindings.put(new FactoryId(name),new BusinessRuntimeBinding(new FactoryId(name),new DatasourceIdentity("db-"+name),1));
        }
        resolver=new ConfiguredBusinessPersistenceResolver(catalog,bindings); persistence=new DedicatedDatabasePersistence(resolver,registry);
    }
    static HikariDataSource pool(String name) {
        HikariConfig config=new HikariConfig(); config.setJdbcUrl("jdbc:h2:mem:"+name+"_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(2); config.setMinimumIdle(0);config.setConnectionTimeout(1000); config.setPoolName("test-only-"+name+"-"+UUID.randomUUID());
        return new HikariDataSource(config);
    }
    static void install(HikariDataSource pool,String payload) throws Exception {
        try(var connection=pool.getConnection();var statement=connection.createStatement()) { statement.execute("CREATE TABLE foundation_probe(id INTEGER PRIMARY KEY,payload VARCHAR(100) NOT NULL)"); }
        try(var connection=pool.getConnection();var statement=connection.prepareStatement("INSERT INTO foundation_probe(id,payload) VALUES (?,?)")) { statement.setInt(1,1);statement.setString(2,payload);statement.executeUpdate(); }
    }
    String read(FactoryScope scope) { return persistence.transaction(scope,s -> s.mapper(ProbeMapper.class).read(1)); }
    public void close() { registry.close(); }
}
