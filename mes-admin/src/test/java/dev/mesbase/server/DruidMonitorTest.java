package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.persistencefixture.business.BusinessProbeMapper;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.ManagedDatasourceService.DatasourceView;
import dev.mesbase.server.monitor.DruidMonitorService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY center and two independent Factory H2 pools.
 */
class DruidMonitorTest {
    private DruidDataSource pool(String label) throws Exception {
        var pool = new DruidDataSource();
        pool.setName(label);
        pool.setUrl("jdbc:h2:mem:" + UUID.randomUUID());
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setInitialSize(0);
        pool.setMinIdle(0);
        pool.setMaxActive(4);
        pool.setValidationQuery("SELECT 1");
        pool.setFilters("stat");
        pool.init();
        try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
            statement.executeQuery("SELECT 'TEST_ONLY_LITERAL' AS " + label + "_marker").close();
        }
        return pool;
    }

    private DatasourceView row(boolean initialized, String status) {
        return new DatasourceView(7L, "业务库", "SECRET_HOST", 3306, "SECRET_DB", "SECRET_USER", status, 1,
                initialized ? "MANAGED" : "UNMANAGED", "F", "示例工厂", "已加入管理", "已加载", initialized, null, null, null, "TEST_NODE");
    }

    @Test
    void centerAndBusinessPoolsAreSelectableAndMetricsAndSqlRemainIsolated() throws Exception {
        try (var master = pool("master"); var bus = pool("bus"); var second = pool("second"); var registry = new RuntimeRegistry()) {
            registry.registerDedicated(new FactoryId("F"), new DatasourceIdentity("managed-7"), 1, bus, () -> {
            }, Set.of(BusinessProbeMapper.class));
            registry.registerDedicated(new FactoryId("G"), new DatasourceIdentity("managed-8"), 1, second, () -> {
            }, Set.of(BusinessProbeMapper.class));
            var other = new DatasourceView(8L, "第二业务库", "SECRET_HOST", 3306, "SECRET_DB", "SECRET_USER", "ENABLED", 1,
                    "MANAGED", "G", "第二工厂", "已加入管理", "已加载", true, null, null, null, "TEST_NODE");
            var managed = mock(ManagedDatasourceService.class);
            when(managed.countSources()).thenReturn(2L);
            when(managed.list(0, 19)).thenReturn(List.of(row(true, "ENABLED"), other));
            when(managed.detail(7)).thenReturn(row(true, "ENABLED"));
            when(managed.detail(8)).thenReturn(other);
            var service = new DruidMonitorService(master, managed, registry);
            long borrowed = bus.getConnectCount();
            var catalog = service.sources(1, 20);
            assertEquals(3, catalog.total());
            assertEquals(3, catalog.rows().size());
            var names = Map.of("platform", "master", "business:7", "bus", "business:8", "second");
            for (var entry : names.entrySet()) {
                var snapshot = service.snapshot(entry.getKey(), 100);
                assertEquals(entry.getValue(), snapshot.pool().name());
                assertEquals("INITIALIZED", snapshot.source().state());
                assertEquals(1, snapshot.sql().size());
                assertTrue(snapshot.sql().getFirst().sql().contains(entry.getValue() + "_marker"));
                assertFalse(snapshot.sql().getFirst().sql().contains("TEST_ONLY_LITERAL"));
                assertFalse(snapshot.wall().enabled());
            }
            assertEquals(borrowed, bus.getConnectCount(), "Monitoring must not borrow a business connection");
            String json = new ObjectMapper().writeValueAsString(service.snapshot("business:7", 100));
            assertFalse(json.contains("SECRET_"));
            assertFalse(json.contains("jdbc:"));
            assertThrows(FoundationException.class, () -> service.snapshot("missing", 100));
            assertThrows(FoundationException.class, () -> service.snapshot("jdbc:mysql://arbitrary", 100));
            assertThrows(FoundationException.class, () -> service.snapshot("platform", 0));
            assertThrows(FoundationException.class, () -> service.sources(0, 20));
            assertThrows(FoundationException.class, () -> service.sources(1, 101));
        }
    }

    @Test
    void unloadedAndDisabledBusinessRecordsHaveNoFabricatedZeroMetrics() throws Exception {
        try (var master = pool("master"); var registry = new RuntimeRegistry()) {
            var managed = mock(ManagedDatasourceService.class);
            when(managed.countSources()).thenReturn(1L);
            when(managed.detail(7)).thenReturn(row(false, "ENABLED"));
            var service = new DruidMonitorService(master, managed, registry);
            var unloaded = service.snapshot("business:7", 100);
            assertEquals("NOT_INITIALIZED", unloaded.source().state());
            assertNull(unloaded.pool());
            assertTrue(unloaded.sql().isEmpty());
            assertNull(unloaded.wall());
            when(managed.detail(7)).thenReturn(row(false, "DISABLED"));
            assertEquals("DISABLED", service.snapshot("business:7", 100).source().state());
            assertEquals(Set.of(), registry.mapperTypes());
        }
    }

    @Test
    void paginationSpansFixedAndManagedSourcesWithoutDroppingRows() throws Exception {
        try (var master = pool("master")) {
            var managed = mock(ManagedDatasourceService.class);
            when(managed.countSources()).thenReturn(1L);
            when(managed.list(0, 1)).thenReturn(List.of(row(false, "ENABLED")));
            var service = new DruidMonitorService(master, managed, null);
            assertEquals(List.of("platform"), service.sources(1, 1).rows().stream().map(DruidMonitorService.Source::id)
                    .toList());
            assertEquals(List.of("business:7"), service.sources(2, 1).rows().stream()
                    .map(DruidMonitorService.Source::id).toList());
            assertTrue(service.sources(3, 1).rows().isEmpty());
        }
    }

    @Test
    void aPoolWithoutSqlCollectionStillReportsItsPoolAndExplicitlyDisablesSqlStatistics() throws Exception {
        try (var source = new DruidDataSource()) {
            source.setName("TEST_ONLY_no_stat");
            source.setUrl("jdbc:h2:mem:" + UUID.randomUUID());
            source.setValidationQuery("SELECT 1");
            source.setInitialSize(0);
            source.init();
            var snapshot = new DruidMonitorService(source, (ManagedDatasourceService) null, null).snapshot("platform", 100);
            assertFalse(snapshot.pool().sqlStatisticsEnabled());
            assertTrue(snapshot.sql().isEmpty());
            assertFalse(snapshot.wall().enabled());
        }
    }
}
