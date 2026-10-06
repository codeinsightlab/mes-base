package dev.mesbase;

import com.zaxxer.hikari.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.platform.codegen.*;

import dev.mesbase.RuntimeAssemblyFixture.FactorySettings;
import org.apache.ibatis.annotations.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL test, restricted to explicitly provisioned development databases. */
class MySqlDevelopmentTest {
    @TempDir Path temporary;
    Properties properties;
    HikariDataSource platform;
    List<FactorySettings> factories;

    @PersistenceOwner(PersistenceOwner.Kind.BUSINESS)
    public interface ProbeMapper {
        @Select("SELECT payload FROM test_only_runtime_probe WHERE factory_id=#{factory} AND id=1")
        String read(String factory);
        @Update("UPDATE test_only_runtime_probe SET payload=#{payload} WHERE factory_id=#{factory} AND id=1")
        int update(@Param("factory") String factory,@Param("payload") String payload);
    }

    @BeforeEach void connect() throws Exception {
        String path=System.getProperty("mes.test.mysql.config");
        Assumptions.assumeTrue(path!=null,"Real MySQL test requires explicit isolated configuration");
        properties=new Properties();
        var loader=new org.springframework.boot.env.YamlPropertySourceLoader();
        for(var source:loader.load("mysql-test",new org.springframework.core.io.FileSystemResource(path))) {
            var enumerable=(org.springframework.core.env.EnumerablePropertySource<?>)source;
            for(var key:enumerable.getPropertyNames()) properties.setProperty(key,String.valueOf(enumerable.getProperty(key)));
        }
        platform=pool(properties.getProperty("mes.platform.url"));
        factories=new ArrayList<>();
        for(int i=0;i<2;i++) {
            String prefix="mes.runtime.factories["+i+"].";
            factories.add(new FactorySettings(properties.getProperty(prefix+"id"),"development",
                    "DEV_"+i,"TEST ONLY "+i,FactoryStatus.ENABLED,properties.getProperty(prefix+"datasource-identity"),1,
                    properties.getProperty(prefix+"url"),properties.getProperty(prefix+"username"),properties.getProperty(prefix+"password")));
        }
        for(var settings:factories) try(var source=pool(settings.url());var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS test_only_runtime_probe (id INTEGER PRIMARY KEY,factory_id VARCHAR(64) NOT NULL,payload VARCHAR(100) NOT NULL)");
            try(var insert=connection.prepareStatement("INSERT INTO test_only_runtime_probe VALUES (1,?,?) ON DUPLICATE KEY UPDATE factory_id=VALUES(factory_id),payload=VALUES(payload)")) {
                insert.setString(1,settings.id());insert.setString(2,settings.id());insert.executeUpdate();
            }
        }
    }

    HikariDataSource pool(String url) {
        if(url==null || !url.contains("/mes_base_dev_")) throw new IllegalArgumentException("TEST ONLY development database required");
        var config=new HikariConfig();config.setJdbcUrl(url);
        config.setUsername(properties.getProperty("mes.platform.username"));config.setPassword(properties.getProperty("mes.platform.password"));
        config.setMaximumPoolSize(1);config.setMinimumIdle(0);return new HikariDataSource(config);
    }

    @AfterEach void close() throws Exception {
        if(platform==null)return;
        for(var settings:factories) try(var source=pool(settings.url());var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS test_only_runtime_probe");
            statement.execute("DROP TABLE IF EXISTS test_only_codegen_probe");
        }
        platform.close();
    }

    @Test void distinctRuntimeOwnershipRollbackAndNoFallback() {
        try(var assembly=RuntimeAssemblyFixture.prepare(factories,platform,Set.of(ProbeMapper.class),new dev.mesbase.server.DruidPools.Settings(0,0,2,5000,5000,30000,60000,300000,900000,"SELECT 1",true,false,false,"stat,wall"))) {
            var resolver=new ConfiguredBusinessPersistenceResolver(assembly.catalog(),assembly.bindings());
            var persistence=new DedicatedDatabasePersistence(resolver,assembly.registry());
            var a=new FactoryScope(new FactoryId("A"));var b=new FactoryScope(new FactoryId("B"));
            assertEquals("A",persistence.transaction(a,s->s.mapper(ProbeMapper.class).read("A")));
            assertEquals("B",persistence.transaction(b,s->s.mapper(ProbeMapper.class).read("B")));
            assertNull(persistence.transaction(a,s->s.mapper(ProbeMapper.class).read("B")));
            assertThrows(IllegalStateException.class,()->persistence.transaction(a,s->{s.mapper(ProbeMapper.class).update("A","rollback");throw new IllegalStateException("TEST ONLY rollback");}));
            assertEquals("A",persistence.transaction(a,s->s.mapper(ProbeMapper.class).read("A")));
            assertThrows(FoundationException.class,()->persistence.transaction(null,s->null));
            assertThrows(FoundationException.class,()->persistence.transaction(new FactoryScope(new FactoryId("missing")),s->null));
            assertThrows(FoundationException.class,()->persistence.transaction(a,s->s.mapper(RuntimeFixture.PlatformMapper.class)));
            resolver.replace(Map.of());assertThrows(FoundationException.class,()->persistence.transaction(a,s->null));
        }
        var second=factories.get(1);var first=factories.get(0);
        var duplicate=new FactorySettings(second.id(),second.enterpriseId(),second.code(),second.name(),second.status(),second.datasourceIdentity(),1,first.url(),second.username(),second.password());
        assertThrows(IllegalArgumentException.class,()->RuntimeAssemblyFixture.prepare(List.of(first,duplicate),platform,Set.of(ProbeMapper.class),new dev.mesbase.server.DruidPools.Settings(0,0,2,5000,5000,30000,60000,300000,900000,"SELECT 1",true,false,false,"stat,wall")));
    }

    @Test void realMetadataProducesMySqlQuotedManifest() throws Exception {
        try(var source=pool(factories.get(0).url());var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE test_only_codegen_probe(id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id VARCHAR(64) NOT NULL,code VARCHAR(40) NOT NULL,status INTEGER NOT NULL,remark VARCHAR(500),UNIQUE(factory_id,code))");
            connection.close();
            var generator=new DefaultMesCodegenService(new CodegenProfiles("mysql-dev",Map.of("mysql-dev",source)));
            var request=new CodegenRequest(null,null,"test_only_codegen_probe","testonly","合成MySQL探针","dev.mesbase.modules.testonly","TEST ONLY",CodegenRequest.ScopeKind.FACTORY_SCOPED,CodegenRequest.OverwritePolicy.FAIL,false,temporary.toRealPath().resolve("backend"),temporary.toRealPath().resolve("frontend"));
            var result=generator.generate(request);assertEquals(16,result.files().size());assertEquals("`",result.metadata().identifierQuote());
            var mapper=result.files().stream().filter(f->f.path().endsWith("Mapper.xml")).findFirst().orElseThrow();
            String xml=Files.readString(Path.of(mapper.path()));assertTrue(xml.contains("`factory_id`"));assertTrue(xml.contains("`test_only_codegen_probe`"));
        }
    }
}
