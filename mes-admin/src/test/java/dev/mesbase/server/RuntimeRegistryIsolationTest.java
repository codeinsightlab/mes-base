package dev.mesbase.server;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.infrastructure.persistence.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeRegistryIsolationTest {
    @ParameterizedTest
    @ValueSource(strings = {"retire", "lastLease", "replace", "retry", "reject"})
    void slowACloseDoesNotBlockBOrCloseTwice(String scenario) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        Runnable closer = () -> {
            if (calls.incrementAndGet() == 1 && scenario.equals("retry")) throw new IllegalStateException("TEST ONLY first close failure");
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("TEST ONLY gate timeout"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new RuntimeException(failure); }
        };
        try (var registry = new RuntimeRegistry(); var workers = Executors.newFixedThreadPool(2)) {
            var a = new BusinessRuntimeBinding(new FactoryId("A"), new DatasourceIdentity("A"), 1);
            var b = new BusinessRuntimeBinding(new FactoryId("B"), new DatasourceIdentity("B"), 1);
            registry.registerDedicated(a.factoryId(), a.datasourceIdentity(), 1, new DriverManagerDataSource(),
                    scenario.equals("reject") ? () -> {} : closer, Set.of());
            registry.registerDedicated(b.factoryId(), b.datasourceIdentity(), 1, new DriverManagerDataSource(), () -> {}, Set.of());
            var lease = registry.acquireDedicated(a);
            if (scenario.equals("lastLease")) registry.retire(a.datasourceIdentity());
            else lease.close();
            if (scenario.equals("retry")) registry.retire(a.datasourceIdentity());
            var closing = workers.submit(() -> {
                switch (scenario) {
                    case "lastLease" -> lease.close();
                    case "replace" -> registry.registerDedicated(a.factoryId(), a.datasourceIdentity(), 2,
                            new DriverManagerDataSource(), () -> {}, Set.of());
                    case "retry" -> registry.retryRetired();
                    case "reject" -> assertThrows(RuntimeException.class, () -> registry.registerDedicated(a.factoryId(),
                            a.datasourceIdentity(), 1, new DriverManagerDataSource(), closer, Set.of()));
                    default -> registry.retire(a.datasourceIdentity());
                }
            });
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                var access = workers.submit(() -> {
                    assertTrue(registry.contains(b));
                    try (var borrowed = registry.acquireDedicated(b)) { borrowed.verify(b); }
                    registry.retryRetired(); // A is already closing; this must neither wait nor close it twice.
                });
                access.get(1, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            closing.get(2, TimeUnit.SECONDS);
            assertEquals(scenario.equals("retry") ? 2 : 1, calls.get());
        }
    }
}
