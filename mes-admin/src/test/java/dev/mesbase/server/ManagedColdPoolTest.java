package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: real resolver/locks with constructed Druid pools and physical JDBC probes mocked.
 */
class ManagedColdPoolTest {
    private record Fixture(JdbcTemplate jdbc, ManagedDatasourceService service,
                           RuntimeRegistry registry) implements AutoCloseable {
        public void close() {
            registry.close();
        }
    }

    private Fixture fixture() throws Exception {
        var center = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(center);
        jdbc.execute("CREATE TABLE mf_factory(factory_id VARCHAR(64),enterprise_id VARCHAR(64),factory_code VARCHAR(32),factory_name VARCHAR(64),datasource_id BIGINT,status VARCHAR(16),revision BIGINT)");
        jdbc.execute("CREATE TABLE mf_datasource(datasource_id BIGINT,datasource_name VARCHAR(64),db_host VARCHAR(64),db_port INT,db_name VARCHAR(64),db_username VARCHAR(64),password_cipher VARCHAR(64),status VARCHAR(16),revision BIGINT)");
        jdbc.execute("INSERT INTO mf_factory VALUES('A','TEST ONLY','A','Fixture',1,'ENABLED',1)");
        jdbc.execute("INSERT INTO mf_datasource VALUES(1,'TEST ONLY','invalid.test',3306,'TEST_ONLY','TEST_ONLY','CIPHER','ENABLED',1)");
        var persistence = new PlatformPersistence(center, Set.of(ManagedFactoryMapper.class, ManagedDatasourceMapper.class, RoutingMetadataMapper.class));
        var registry = new RuntimeRegistry();
        var platform = mock(DataSource.class);
        when(platform.getConnection()).thenAnswer(call -> probe("center"));
        var settings = new DruidPools.Settings(0, 0, 5, 1000, 1000, 1000, 1000, 0, 1000, "SELECT 1", false, false, false, "stat");
        var service = new ManagedDatasourceService(persistence, persistence.mapper(ManagedDatasourceMapper.class), persistence.mapper(ManagedFactoryMapper.class), mock(DatasourcePasswordCipher.class), registry, platform, settings,
                Set.of(), Path.of("target/TEST-ONLY-unused-ddl"), RoutingCacheFixture.cache(persistence));
        return new Fixture(jdbc, service, registry);
    }

    private static Connection probe(String database) throws Exception {
        var connection = mock(DruidPooledConnection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT @@server_uuid,DATABASE()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("TEST ONLY SERVER");
        when(rows.getString(2)).thenReturn(database);
        return connection;
    }

    @Test
    void concurrentColdTransactionsCreateOnePoolAndReleaseEveryLease() throws Exception {
        try (var fixture = fixture(); var workers = Executors.newFixedThreadPool(8)) {
            List<DruidDataSource> created = new CopyOnWriteArrayList<>();
            var gate = new CountDownLatch(1);
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 16; i++)
                tasks.add(workers.submit(() -> {
                    var request = new MockHttpServletRequest();
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                    try (var construction = mockConstruction(DruidDataSource.class, (pool, context) -> {
                        created.add(pool);
                        when(pool.getConnection()).thenAnswer(call -> probe("business-A"));
                    })) {
                        if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timeout");
                        var scope = new FactoryScope(new FactoryId("A"));
                        fixture.service.admit(scope);
                        var snapshot = BusinessRequestSnapshot.current(scope).orElseThrow();
                        try (var lease = snapshot.acquire()) {
                            lease.verify(snapshot.binding());
                        }
                    } finally {
                        BusinessRequestSnapshot.finish(request);
                        RequestContextHolder.resetRequestAttributes();
                    }
                    return null;
                }));
            gate.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
            assertEquals(1, created.size());
            var binding = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("managed-1"), 1);
            assertTrue(fixture.registry.contains(binding));
            verify(created.getFirst(), never()).close();
            fixture.registry.retire(binding.datasourceIdentity());
            verify(created.getFirst(), times(1)).close();
        }
    }

    @Test
    void oldColdSnapshotDoesNotCreateHistoricalPoolAfterNewVersionIsActive() throws Exception {
        try (var fixture = fixture(); var pools = mockConstruction(DruidDataSource.class, (pool, context) ->
                when(pool.getConnection()).thenAnswer(call -> probe("business-A")))) {
            var request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            var scope = new FactoryScope(new FactoryId("A"));
            fixture.service.admit(scope);
            var old = BusinessRequestSnapshot.current(scope).orElseThrow();
            var newer = new BusinessRuntimeBinding(scope.factoryId(), old.binding().datasourceIdentity(), 2);
            fixture.registry.registerDedicated(scope.factoryId(), newer.datasourceIdentity(), 2,
                    mock(DataSource.class), () -> {
                    }, Set.of());
            assertThrows(FoundationException.class, old::acquire);
            assertTrue(fixture.registry.contains(newer));
            assertEquals(1, pools.constructed().size());
            verify(pools.constructed().getFirst(), times(1)).close();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void sameVersionReadmissionCreatesFreshPoolAndOldPoolDrainsIndependently() throws Exception {
        try (var fixture = fixture(); var pools = mockConstruction(DruidDataSource.class, (pool, context) ->
                when(pool.getConnection()).thenAnswer(call -> probe("business-A")))) {
            var scope = new FactoryScope(new FactoryId("A"));
            var oldRequest = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(oldRequest));
            fixture.service.admit(scope);
            var old = BusinessRequestSnapshot.current(scope).orElseThrow();
            var oldLease = old.acquire();
            oldLease.verify(old.binding());
            fixture.registry.retire(old.binding().datasourceIdentity());
            var newRequest = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(newRequest));
            fixture.service.admit(scope);
            var fresh = BusinessRequestSnapshot.current(scope).orElseThrow();
            try (var lease = fresh.acquire()) {
                lease.verify(fresh.binding());
            }
            assertEquals(2, pools.constructed().size());
            oldLease.verify(old.binding());
            verify(pools.constructed().getFirst(), never()).close();
            oldLease.close();
            BusinessRequestSnapshot.finish(oldRequest);
            verify(pools.constructed().getFirst()).close();
            verify(pools.constructed().get(1), never()).close();
            try (var lease = fresh.acquire()) {
                lease.verify(fresh.binding());
            }
            BusinessRequestSnapshot.finish(newRequest);
            fixture.registry.retire(fresh.binding().datasourceIdentity());
            verify(pools.constructed().get(1)).close();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void failedRetiredCloseIsRetriedWithoutClosingActiveReplacement() throws Exception {
        try (var fixture = fixture(); var pools = mockConstruction(DruidDataSource.class, (pool, context) ->
                when(pool.getConnection()).thenAnswer(call -> probe("business-A")))) {
            var scope = new FactoryScope(new FactoryId("A"));
            var acquired = fixture.service.begin(scope);
            var oldPool = pools.constructed().getFirst();
            doThrow(new IllegalStateException("TEST ONLY close failure")).doNothing().when(oldPool).close();
            fixture.registry.retire(acquired.binding().datasourceIdentity());
            acquired.lease().verify(acquired.binding());
            acquired.lease().close();
            verify(oldPool, times(1)).close();
            var fresh = fixture.service.begin(scope);
            fixture.registry.retryRetired();
            verify(oldPool, times(2)).close();
            fresh.lease().verify(fresh.binding());
            verify(pools.constructed().get(1), never()).close();
            fresh.lease().close();
        }
    }

    @Test
    void failedPhysicalProbeClosesUnpublishedPool() throws Exception {
        try (var fixture = fixture(); var pools = mockConstruction(DruidDataSource.class, (pool, context) ->
                when(pool.getConnection()).thenThrow(new SQLException("TEST ONLY unavailable database")))) {
            assertThrows(FoundationException.class, () -> fixture.service.begin(new FactoryScope(new FactoryId("A"))));
            assertEquals(1, pools.constructed().size());
            verify(pools.constructed().getFirst()).close();
        }
    }
}
