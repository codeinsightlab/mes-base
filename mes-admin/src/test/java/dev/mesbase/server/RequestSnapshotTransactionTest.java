package dev.mesbase.server;

import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: two physical H2 databases through the production Spring routing lifecycle.
 */
class RequestSnapshotTransactionTest {
    private static DriverManagerDataSource database(String label) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sample(name VARCHAR(10))");
        jdbc.update("INSERT INTO sample VALUES(?)", label);
        return source;
    }

    @Test
    void startedTransactionSurvivesRetirementAndNewTransactionRejectsOldSnapshot() throws Exception {
        var oldClosed = new AtomicInteger();
        var resolves = new AtomicInteger();
        var old = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("A-source"), 1);
        var newer = new BusinessRuntimeBinding(old.factoryId(), old.datasourceIdentity(), 2);
        try (var registry = new RuntimeRegistry()) {
            registry.registerDedicated(old.factoryId(), old.datasourceIdentity(), 1, database("old"), oldClosed::incrementAndGet, Set.of());
            var request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            BusinessRequestSnapshot.install(new BusinessRequestSnapshot(old, () -> registry.acquireDedicated(old)));
            var source = new FactoryBusinessDataSource(scope -> {
                resolves.incrementAndGet();
                return newer;
            }, registry);
            var manager = new OwnedTransactionManager(source);
            var jdbc = new JdbcTemplate(source);
            var transaction = new TransactionTemplate(manager);
            new RequestBoundaryFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                FactoryContext.bind(new FactoryScope(old.factoryId()));
                var inner = new TransactionTemplate(manager);
                inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transaction.executeWithoutResult(status -> {
                    inner.executeWithoutResult(nested -> assertEquals("old", jdbc.queryForObject("SELECT name FROM sample", String.class)));
                    assertThrows(FoundationException.class, () -> FactoryContext.bind(new FactoryScope(new FactoryId("B"))));
                    registry.retire(old.datasourceIdentity());
                    registry.registerDedicated(newer.factoryId(), newer.datasourceIdentity(), 2, database("new"), () -> {
                    }, Set.of());
                    assertEquals("old", jdbc.queryForObject("SELECT name FROM sample", String.class));
                    assertEquals(0, oldClosed.get());
                });
                assertEquals(1, oldClosed.get());
                assertThrows(FoundationException.class, () -> transaction.executeWithoutResult(status -> {
                }));
            });
            assertEquals(0, resolves.get());
            assertEquals(1, oldClosed.get());
            assertTrue(FactoryContext.current().isEmpty());
            RequestContextHolder.resetRequestAttributes();
            FactoryContext.bind(new FactoryScope(newer.factoryId()));
            assertEquals("new", transaction.execute(status -> jdbc.queryForObject("SELECT name FROM sample", String.class)));
            assertEquals(1, resolves.get());
        } finally {
            RequestContextHolder.resetRequestAttributes();
            FactoryContext.clear();
        }
    }

    @Test
    void concurrentRequestsKeepIndependentFactoriesAndRetiredRequestsReleaseOnException() throws Exception {
        try (var registry = new RuntimeRegistry(); var workers = Executors.newFixedThreadPool(2)) {
            var a = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("A-source"), 1);
            var b = new BusinessRuntimeBinding(new FactoryId("B"), new DatasourceIdentity("B-source"), 1);
            var closes = new AtomicInteger();
            for (var binding : new BusinessRuntimeBinding[]{a, b})
                registry.registerDedicated(binding.factoryId(), binding.datasourceIdentity(), 1,
                        database(binding.factoryId().value()), closes::incrementAndGet, Set.of());
            var source = new FactoryBusinessDataSource(scope -> {
                throw new IllegalStateException("Snapshot must bypass resolver");
            }, registry);
            var manager = new OwnedTransactionManager(source);
            var entered = new CountDownLatch(2);
            var tasks = new ArrayList<Future<?>>();
            for (var binding : new BusinessRuntimeBinding[]{a, b})
                tasks.add(workers.submit(() -> {
                    var request = new MockHttpServletRequest();
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                    BusinessRequestSnapshot.install(new BusinessRequestSnapshot(binding, () -> registry.acquireDedicated(binding)));
                    try {
                        assertThrows(IllegalStateException.class, () -> new RequestBoundaryFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                            FactoryContext.bind(new FactoryScope(binding.factoryId()));
                            entered.countDown();
                            try {
                                assertTrue(entered.await(5, TimeUnit.SECONDS));
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            new TransactionTemplate(manager).executeWithoutResult(status -> {
                                registry.retire(binding.datasourceIdentity());
                                assertEquals(binding.factoryId()
                                        .value(), new JdbcTemplate(source).queryForObject("SELECT name FROM sample", String.class));
                                status.setRollbackOnly();
                            });
                            throw new IllegalStateException("TEST ONLY request failure");
                        }));
                        assertTrue(FactoryContext.current().isEmpty());
                    } finally {
                        BusinessRequestSnapshot.finish(request);
                        RequestContextHolder.resetRequestAttributes();
                        FactoryContext.clear();
                    }
                    return null;
                }));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(2, closes.get());
        }
    }

    @Test
    void requestSnapshotDoesNotKeepRetiredPoolAliveBeforeTransaction() {
        var closed = new AtomicInteger();
        try (var registry = new RuntimeRegistry()) {
            var binding = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("A-source"), 1);
            registry.registerDedicated(binding.factoryId(), binding.datasourceIdentity(), 1, database("old"), closed::incrementAndGet, Set.of());
            var request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            BusinessRequestSnapshot.install(new BusinessRequestSnapshot(binding, () -> registry.acquireDedicated(binding)));
            registry.retire(binding.datasourceIdentity());
            assertEquals(1, closed.get());
            assertThrows(FoundationException.class, () -> BusinessRequestSnapshot.current(new FactoryScope(binding.factoryId()))
                    .orElseThrow().acquire());
            BusinessRequestSnapshot.finish(request);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
