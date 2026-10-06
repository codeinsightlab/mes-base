package dev.mesbase.infrastructure.persistence;

import com.github.pagehelper.PageHelper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TEST ONLY H2 characterization. Assertions preserve current behavior, including defects. */
class PersistenceDeepAuditTest {
    private static final FactoryScope A = new FactoryScope(new FactoryId("AUDIT_A"));
    private static final FactoryScope B = new FactoryScope(new FactoryId("AUDIT_B"));

    @PersistenceOwner(PersistenceOwner.Kind.BUSINESS)
    interface BusinessMapper {
        @Select("SELECT label FROM audit_marker")
        String label();

        @Update("UPDATE audit_marker SET label = 'changed'")
        int change();
    }

    @PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
    interface PlatformMapper {
        @Select("SELECT label FROM audit_marker")
        String label();

        @Update("UPDATE audit_marker SET label = 'changed'")
        int change();

        @Select("SELECT NEXT VALUE FOR audit_sequence")
        long advanceSequence();
    }

    private static HikariDataSource pool(String label) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:audit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1000);
        HikariDataSource pool = new HikariDataSource(config);
        try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE audit_marker(label VARCHAR(32))");
            statement.execute("CREATE SEQUENCE audit_sequence START WITH 1");
            try (var insert = connection.prepareStatement("INSERT INTO audit_marker VALUES (?)")) {
                insert.setString(1, label);
                insert.executeUpdate();
            }
        }
        return pool;
    }

    private static BusinessRuntimeBinding binding(FactoryScope scope, long revision) {
        return new BusinessRuntimeBinding(scope.factoryId(),
                new DatasourceIdentity(scope.factoryId().value()), revision);
    }

    private static final class Fixture implements AutoCloseable {
        final RuntimeRegistry registry = new RuntimeRegistry();
        final HikariDataSource a;
        final HikariDataSource b;
        volatile long revision = 1;
        final FactoryBusinessDataSource source;
        final OwnedTransactionManager manager;
        final BusinessMapper mapper;

        Fixture() throws Exception {
            a = pool("A");
            b = pool("B");
            registry.registerDedicated(A.factoryId(), binding(A, 1).datasourceIdentity(),
                    1, a, a::close, Set.of(BusinessMapper.class));
            registry.registerDedicated(B.factoryId(), binding(B, 1).datasourceIdentity(),
                    1, b, b::close, Set.of(BusinessMapper.class));
            source = new FactoryBusinessDataSource(scope -> binding(scope, scope.equals(A) ? revision : 1), registry);
            manager = new OwnedTransactionManager(source);
            var factory = PersistenceFactories.create(source, Set.of(BusinessMapper.class),
                    configuration -> {}, manager::verifyAccess);
            mapper = new SqlSessionTemplate(factory).getMapper(BusinessMapper.class);
        }

        <T> T run(FactoryScope scope, Supplier<T> work) {
            return FactoryContext.withScope(scope,
                    () -> new TransactionTemplate(manager).execute(status -> work.get()));
        }

        @Override
        public void close() {
            registry.close();
        }
    }

    @AfterEach
    void clean() throws Exception {
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertNull(local(FactoryContext.class, "PINNED"));
        assertNull(local(ErpSourceContext.class, "PINNED"));
        assertFalse(PersistenceReadContext.active());
        FactoryContext.clear();
        ErpSourceContext.restore(null);
        PageHelper.clearPage();
    }

    private static Object local(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return ((ThreadLocal<?>) field.get(null)).get();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void businessSwitchRejectedAndRequiresNewSameFactoryRestoresConnection() throws Exception {
        try (var f = new Fixture()) {
            f.run(A, () -> {
                assertEquals("A", f.mapper.label());
                var outer = TransactionSynchronizationManager.getResource(f.source);
                assertThrows(FoundationException.class, () -> FactoryContext.bind(B));
                assertEquals("A", f.mapper.label());
                var nested = new TransactionTemplate(f.manager);
                nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                nested.execute(status -> {
                    assertFalse(outer == TransactionSynchronizationManager.getResource(f.source));
                    assertEquals("A", f.mapper.label());
                    return null;
                });
                assertTrue(outer == TransactionSynchronizationManager.getResource(f.source));
                assertThrows(FoundationException.class,
                        () -> FactoryContext.withScope(B, () -> nested.execute(status -> f.mapper.label())));
                return null;
            });
        }
    }

    @Test
    void independentPlatformReadAllowedButOrdinaryReadAndWritesRejectedInsideBusiness() throws Exception {
        try (var f = new Fixture(); var p = pool("PLATFORM")) {
            var platform = new PlatformPersistence(p, Set.of(PlatformMapper.class));
            f.run(A, () -> {
                assertThrows(RuntimeException.class, () -> platform.mapper(PlatformMapper.class).label());
                assertEquals("PLATFORM", platform.readOnly(s -> s.mapper(PlatformMapper.class).label()));
                assertThrows(RuntimeException.class,
                        () -> platform.readOnly(s -> s.mapper(PlatformMapper.class).change()));
                assertThrows(FoundationException.class,
                        () -> platform.run(() -> platform.mapper(PlatformMapper.class).change()));
                assertEquals("A", f.mapper.label());
                return null;
            });
        }
    }

    @Test
    void erpSelectionRejectsSwitchAndBusinessOwnerEvenWithRequiresNew() throws Exception {
        try (var f = new Fixture(); var hr = pool("HR"); var wms = pool("WMS");
             var erp = new ErpRoutingDataSource(Map.of("hr", hr, "wms", wms))) {
            var manager = new OwnedTransactionManager(erp);
            ErpSourceContext.select("hr");
            new TransactionTemplate(manager).execute(status -> {
                assertThrows(FoundationException.class, () -> ErpSourceContext.select("wms"));
                assertEquals("hr", ErpSourceContext.required());
                return null;
            });
            f.run(A, () -> {
                var nested = new TransactionTemplate(manager);
                nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                assertThrows(FoundationException.class, () -> nested.execute(status -> null));
                return null;
            });
            ErpSourceContext.restore(null);
        }
    }

    @Test
    void sameThreadRequestsLeaveAllFiveStateFamiliesCleanOnExceptions() throws Exception {
        try (var f = new Fixture(); var p = pool("PLATFORM"); var hr = pool("HR");
             var erp = new ErpRoutingDataSource(Map.of("hr", hr))) {
            var platform = new PlatformPersistence(p, Set.of(PlatformMapper.class));
            var erpManager = new OwnedTransactionManager(erp);
            var boundary = new RequestBoundaryFilter();
            assertThrows(IllegalStateException.class,
                    () -> boundary.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                            (request, response) -> {
                                f.run(A, () -> {
                                    assertThrows(IllegalStateException.class,
                                            () -> platform.readOnly(s -> {
                                                throw new IllegalStateException("TEST ONLY metadata failure");
                                            }));
                                    assertFalse(PersistenceReadContext.active());
                                    assertEquals("A", f.mapper.label());
                                    return null;
                                });
                                String previous = ErpSourceContext.select("hr");
                                try {
                                    new TransactionTemplate(erpManager).execute(status -> null);
                                } finally {
                                    ErpSourceContext.restore(previous);
                                }
                                PageHelper.startPage(2, 10);
                                throw new IllegalStateException("TEST ONLY request failure");
                            }));
            boundary.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (request, response) -> {
                        assertTrue(FactoryContext.current().isEmpty());
                        assertNull(local(FactoryContext.class, "PINNED"));
                        assertNull(local(ErpSourceContext.class, "CURRENT"));
                        assertNull(local(ErpSourceContext.class, "PINNED"));
                        assertFalse(PersistenceReadContext.active());
                        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
                        assertNull(PageHelper.getLocalPage());
                        assertEquals("B", f.run(B, f.mapper::label));
                    });
        }
    }

    @Test
    void refreshInvalidatesOldTransactionDrainsPoolAndNewThreadUsesV2() throws Exception {
        try (var f = new Fixture(); var next = pool("A_V2"); var workers = Executors.newSingleThreadExecutor()) {
            assertThrows(FoundationException.class, () -> f.run(A, () -> {
                assertEquals("A", f.mapper.label());
                f.registry.registerDedicated(A.factoryId(), binding(A, 2).datasourceIdentity(),
                        2, next, next::close, Set.of(BusinessMapper.class));
                f.revision = 2;
                assertFalse(f.a.isClosed());
                assertThrows(RuntimeException.class, f.mapper::label);
                try {
                    assertEquals("A_V2", workers.submit(() -> f.run(A, f.mapper::label)).get(5, TimeUnit.SECONDS));
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                assertFalse(f.a.isClosed());
                return null;
            }));
            assertTrue(f.a.isClosed());
            assertEquals("A_V2", f.run(A, f.mapper::label));
        }
    }

    @Test
    void suspendedBusinessResumesWithWrongContextPinBeforeNextStatementDetectsConflict() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(IllegalStateException.class, () -> f.run(A, () -> {
                var paused = new TransactionTemplate(f.manager);
                paused.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
                paused.execute(status -> {
                    FactoryContext.bind(B);
                    return null;
                });
                assertEquals(B, FactoryContext.required());
                assertThrows(RuntimeException.class, f.mapper::label);
                assertThrows(FoundationException.class, () -> FactoryContext.bind(A));
                assertThrows(FoundationException.class, f.manager::verifyAccess);
                // Rollback avoids commit with this intentionally inconsistent state.
                throw new IllegalStateException("TEST ONLY rollback resumed mismatch");
            }));
        }
    }

    @Test
    void retiredPoolCanBeRegisteredAgainThenOldLeaseClosesNewActivePool() throws Exception {
        try (var f = new Fixture()) {
            var old = f.registry.acquireDedicated(binding(A, 1));
            f.registry.retire(binding(A, 1).datasourceIdentity());
            assertFalse(f.a.isClosed());
            f.registry.registerDedicated(A.factoryId(), binding(A, 2).datasourceIdentity(),
                    2, f.a, f.a::close, Set.of(BusinessMapper.class));
            assertTrue(f.registry.contains(binding(A, 2)));
            old.close();
            assertTrue(f.a.isClosed());
            assertTrue(f.registry.contains(binding(A, 2)));
        }
    }

    @Test
    void failedPoolClosureIsMarkedClosedAndNeverRetried() throws Exception {
        try (var p = pool("closure")) {
            var registry = new RuntimeRegistry();
            var attempts = new AtomicInteger();
            registry.registerDedicated(A.factoryId(), binding(A, 1).datasourceIdentity(), 1,
                    p, () -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("TEST ONLY close failure");
                    }, Set.of(BusinessMapper.class));
            assertThrows(IllegalStateException.class, registry::close);
            registry.close();
            assertEquals(1, attempts.get());
            assertFalse(p.isClosed());
        }
    }

    @Test
    void connectionAcquisitionFailureReleasesPinSelectionAndLease() {
        var closures = new AtomicInteger();
        var broken = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("TEST ONLY connection unavailable");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        };
        try (var registry = new RuntimeRegistry()) {
            registry.registerDedicated(A.factoryId(), binding(A, 1).datasourceIdentity(),
                    1, broken, closures::incrementAndGet, Set.of(BusinessMapper.class));
            var source = new FactoryBusinessDataSource(scope -> binding(scope, 1), registry);
            var manager = new OwnedTransactionManager(source);
            assertThrows(RuntimeException.class, () -> FactoryContext.withScope(A,
                    () -> new TransactionTemplate(manager).execute(status -> null)));
            assertNull(local(FactoryContext.class, "PINNED"));
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
            registry.retire(binding(A, 1).datasourceIdentity());
            assertEquals(1, closures.get());
        }
    }

    @Test
    void readWindowAllowsOtherPlatformTemplateToJoinOuterTransaction() throws Exception {
        try (var p = pool("PLATFORM")) {
            var platform = new PlatformPersistence(p, Set.of(PlatformMapper.class));
            assertThrows(IllegalStateException.class, () -> platform.run(() -> {
                platform.mapper(PlatformMapper.class).change();
                assertEquals("PLATFORM", platform.readOnly(s -> s.mapper(PlatformMapper.class).label()));
                assertEquals("changed", platform.readOnly(s -> platform.mapper(PlatformMapper.class).label()));
                throw new IllegalStateException("TEST ONLY rollback");
            }));
            assertEquals("PLATFORM", platform.mapper(PlatformMapper.class).label());
        }
    }

    @Test
    void selectOnlyWindowDoesNotPreventSqlSideEffects() throws Exception {
        try (var p = pool("PLATFORM")) {
            var platform = new PlatformPersistence(p, Set.of(PlatformMapper.class));
            long first = platform.readOnly(s -> s.mapper(PlatformMapper.class).advanceSequence());
            long second = platform.readOnly(s -> s.mapper(PlatformMapper.class).advanceSequence());
            assertEquals(1L, first);
            assertEquals(2L, second);
        }
    }
}
