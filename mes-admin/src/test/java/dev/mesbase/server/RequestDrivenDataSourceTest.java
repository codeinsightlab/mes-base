package dev.mesbase.server;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Druid pooling with H2 connections; injected physical-connect failures, no external database. */
class RequestDrivenDataSourceTest {
    private static final class Pool extends RequestDrivenDataSource {
        final AtomicInteger attempts = new AtomicInteger();
        volatile boolean unavailable;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        Pool(AtomicLong clock) { this(new DatasourceRecovery(clock::get)); }

        Pool(DatasourceRecovery recovery) {
            super(recovery);
            setUrl("jdbc:h2:mem:" + UUID.randomUUID());
            setUsername("sa");
            setInitialSize(0);
            setMinIdle(0);
            setMaxActive(3);
            setMaxWait(250);
            setTestWhileIdle(false);
            setTestOnBorrow(true);
            setValidationQuery("SELECT 1");
        }

        @Override
        public PhysicalConnectionInfo createPhysicalConnection() throws SQLException {
            attempts.incrementAndGet();
            if (entered != null) entered.countDown();
            if (release != null) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("TEST ONLY gate timeout");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(failure);
                }
            }
            if (unavailable) throw new SQLException("TEST ONLY unavailable", "08001");
            return super.createPhysicalConnection();
        }
    }

    @Test
    void allPoolsOfOneSourceShareCooldownAndOneRecoveryBudget() throws Exception {
        var clock = new AtomicLong();
        var shared = new DatasourceRecovery(clock::get);
        try (var runtime = new Pool(shared); var ddl = new Pool(shared)) {
            runtime.unavailable = true; ddl.unavailable = true;
            assertThrows(SQLException.class, runtime::getConnection);
            assertThrows(SQLException.class, ddl::getConnection);
            assertEquals(5, runtime.attempts.get());
            assertEquals(0, ddl.attempts.get());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
            assertEquals(0, ddl.attempts.get());
            assertThrows(SQLException.class, ddl::getConnection);
            assertThrows(SQLException.class, runtime::getConnection);
            assertEquals(5, runtime.attempts.get());
            assertEquals(5, ddl.attempts.get());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
            runtime.unavailable = false;
            try (var connection = runtime.getConnection()) { assertTrue(connection.isValid(1)); }
            assertFalse(shared.cooling());
        }
    }

    @Test
    void fiveFailuresThenCooldownAndNextRequestRecoversWithoutBackgroundAttempts() throws Exception {
        var clock = new AtomicLong();
        try (var pool = new Pool(clock)) {
            pool.unavailable = true;
            assertThrows(SQLException.class, pool::getConnection);
            assertEquals(5, pool.attempts.get());
            for (int i = 0; i < 10; i++) assertThrows(SQLException.class, pool::getConnection);
            assertEquals(5, pool.attempts.get());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
            pool.unavailable = false;
            assertEquals(5, pool.attempts.get());
            try (var connection = pool.getConnection()) { assertTrue(connection.isValid(1)); }
            assertEquals(6, pool.attempts.get());
            try (var connection = pool.getConnection()) { assertTrue(connection.isValid(1)); }
            assertEquals(6, pool.attempts.get());
        }
    }

    @Test
    void existingPoolOutageAlsoOpensCircuitAndFailedRecoveryStartsNewCooldown() throws Exception {
        var clock = new AtomicLong();
        try (var pool = new Pool(clock)) {
            var connection = pool.getConnection();
            var physical = connection.getConnection();
            connection.close();
            physical.close();
            pool.unavailable = true;
            assertThrows(SQLException.class, pool::getConnection);
            assertEquals(6, pool.attempts.get());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
            assertThrows(SQLException.class, pool::getConnection);
            assertEquals(11, pool.attempts.get());
            assertThrows(SQLException.class, pool::getConnection);
            assertEquals(11, pool.attempts.get());
        }
    }

    @Test
    void blockedAProbeDoesNotBlockBOrQueueMoreAProbes() throws Exception {
        var shared = new DatasourceRecovery(new AtomicLong()::get);
        try (var a = new Pool(shared); var ddlA = new Pool(shared); var b = new Pool(new AtomicLong());
             var worker = Executors.newSingleThreadExecutor()) {
            a.unavailable = true;
            a.entered = new CountDownLatch(1);
            a.release = new CountDownLatch(1);
            var failure = worker.submit(() -> assertThrows(SQLException.class, a::getConnection));
            try {
                assertTrue(a.entered.await(3, TimeUnit.SECONDS));
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                    assertThrows(SQLException.class, a::getConnection);
                    assertThrows(SQLException.class, ddlA::getConnection);
                    assertEquals(0, ddlA.attempts.get());
                    try (var connection = b.getConnection()) { assertTrue(connection.isValid(1)); }
                });
                assertEquals(1, a.attempts.get());
            } finally { a.release.countDown(); }
            failure.get(3, TimeUnit.SECONDS);
            assertEquals(5, a.attempts.get());
        }
    }

    @Test
    void capacityTimeoutDoesNotTripCircuitOrCreateExtraConnections() throws Exception {
        try (var pool = new Pool(new AtomicLong()); var first = pool.getConnection();
             var second = pool.getConnection(); var third = pool.getConnection()) {
            assertThrows(SQLException.class, pool::getConnection);
            assertEquals(3, pool.attempts.get());
            first.close();
            try (var connection = pool.getConnection()) { assertTrue(connection.isValid(1)); }
            assertEquals(3, pool.attempts.get());
        }
    }

    @Test
    void businessFactoryDisablesBackgroundCreationAndPlatformFactoryKeepsItsSettings() throws Exception {
        var settings = new DruidPools.Settings(1, 1, 3, 250, 1000, 1000, 1000,
                0, 1000, "SELECT 1", true, false, false, "stat");
        try (var business = DruidPools.createBusiness(settings, "TEST_ONLY_business", "jdbc:h2:mem:" + UUID.randomUUID(), "sa", "", new DatasourceRecovery(System::nanoTime));
             var platform = DruidPools.create(settings, "TEST_ONLY_platform", "jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
            assertEquals(0, business.getCreateCount());
            assertEquals(0, business.getMinIdle());
            assertFalse(business.isTestWhileIdle());
            assertFalse(business.isKeepAlive());
            assertTrue(business.isTestOnBorrow());
            assertEquals(1, platform.getInitialSize());
            assertTrue(platform.isTestWhileIdle());
            try (var connection = business.getConnection()) { assertTrue(connection.isValid(1)); }
        }
    }
}
