package dev.mesbase.server;

import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.ManagedDatasourceService.FactoryForm;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * TEST ONLY: center configuration SQL and a preloaded synthetic pool; never creates a MySQL connection.
 */
class ManagedAdmissionTest {
    @Test
    void nameEditKeepsPoolAndRemoteUnbindRetiresItWhileExistingLeaseCompletes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE mf_factory(factory_id VARCHAR(64),enterprise_id VARCHAR(64),factory_code VARCHAR(32),"
                + "factory_name VARCHAR(64),datasource_id BIGINT,status VARCHAR(16),revision BIGINT)");
        jdbc.execute("CREATE TABLE mf_datasource(datasource_id BIGINT,datasource_name VARCHAR(64),db_host VARCHAR(64),"
                + "db_port INT,db_name VARCHAR(64),db_username VARCHAR(64),password_cipher VARCHAR(64),status VARCHAR(16),revision BIGINT)");
        jdbc.execute("INSERT INTO mf_factory VALUES('A','TEST ONLY','A','Before',1,'ENABLED',1)");
        jdbc.execute("INSERT INTO mf_datasource VALUES(1,'TEST ONLY','invalid.test',3306,'TEST_ONLY','TEST_ONLY','TEST_ONLY','ENABLED',1)");
        var platform = new PlatformPersistence(source, Set.of(ManagedFactoryMapper.class, ManagedDatasourceMapper.class, RoutingMetadataMapper.class));
        var closed = new AtomicInteger();
        try (var registry = new RuntimeRegistry()) {
            var binding = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("managed-1"), 1);
            registry.registerDedicated(binding.factoryId(), binding.datasourceIdentity(), 1, source,
                    closed::incrementAndGet, Set.of());
            var cache = RoutingCacheFixture.cache(platform);
            var service = new ManagedDatasourceService(platform, mock(DatasourcePasswordCipher.class), registry,
                    source, null, Set.of(), Path.of("target/TEST-ONLY-unused-ddl"), cache);
            var loaded = new ConcurrentHashMap<Long, BusinessRuntimeBinding>();
            loaded.put(1L, binding);
            ReflectionTestUtils.setField(service, "initialized", loaded);
            assertEquals(binding, service.resolve(new FactoryScope(binding.factoryId())));
            service.saveFactory(new FactoryForm("A", "TEST ONLY", "A", "After", 1L, "ENABLED", 1L), false);
            assertTrue(registry.contains(binding));
            assertEquals(0, closed.get());
            // This lease has not acquired a JDBC connection yet, and remains usable after retirement.
            var lease = registry.acquireDedicated(binding);
            jdbc.update("UPDATE mf_factory SET datasource_id=NULL,revision=3 WHERE factory_id='A'");
            platform.run(() -> {
                cache.changed(List.of("A"));
                return null;
            });
            service.reconcileInitializedPools();
            assertThrows(FoundationException.class, () -> registry.acquireDedicated(binding));
            assertThrows(FoundationException.class, () -> service.resolve(new FactoryScope(binding.factoryId())));
            lease.verify(binding);
            assertEquals(0, closed.get());
            lease.close();
            assertEquals(1, closed.get());
            assertEquals(Map.of(), loaded);
        }
    }
}
