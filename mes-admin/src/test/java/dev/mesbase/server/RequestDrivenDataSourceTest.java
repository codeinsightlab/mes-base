package dev.mesbase.server;

import java.sql.SQLException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    void healthyConcurrentColdStartWaitsAndAllBorrowersSucceed() throws Exception {
        concurrentHealthyBorrowers(false);
    }

    @Test
    void healthyConcurrentExpansionWaitsAndAllBorrowersSucceed() throws Exception {
        concurrentHealthyBorrowers(true);
    }

    private void concurrentHealthyBorrowers(boolean expanded) throws Exception {
        try (var pool = new Pool(new AtomicLong()); var workers = Executors.newFixedThreadPool(3)) {
            pool.setMaxWait(3000);
            var borrowed = new ArrayList<com.alibaba.druid.pool.DruidPooledConnection>();
            try {
                if (expanded) borrowed.add(pool.getConnection());
                pool.entered = new CountDownLatch(1);
                pool.release = new CountDownLatch(1);
                var leader = workers.submit(() -> { return pool.getConnection(); });
                assertTrue(pool.entered.await(2, TimeUnit.SECONDS));
                var follower = workers.submit(() -> { return pool.getConnection(); });
                // A healthy physical connection is deliberately slow: followers must wait, not fail.
                assertThrows(TimeoutException.class, () -> follower.get(100, TimeUnit.MILLISECONDS));
                var third = expanded ? null : workers.submit(() -> { return pool.getConnection(); });
                pool.release.countDown();
                borrowed.add(leader.get(2, TimeUnit.SECONDS));
                borrowed.add(follower.get(2, TimeUnit.SECONDS));
                if (third != null) borrowed.add(third.get(2, TimeUnit.SECONDS));
                assertEquals(3, borrowed.size());
                for (var connection : borrowed) assertTrue(connection.isValid(1));
                assertEquals(3, pool.attempts.get());
                assertEquals(3, pool.getActiveCount());
            } finally {
                pool.release.countDown();
                for (var connection : borrowed) connection.close();
            }
        }
    }

    @Test
    void concurrentPoolsShareFailedBudgetAndNextRequestRecovery() throws Exception {
        var clock = new AtomicLong();
        var shared = new DatasourceRecovery(clock::get);
        try (var a = new Pool(shared); var ddl = new Pool(shared); var b = new Pool(new AtomicLong());
             var workers = Executors.newFixedThreadPool(2)) {
            a.setMaxWait(3000); ddl.setMaxWait(3000);
            for (int round = 0; round < 2; round++) {
                if (round > 0) clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
                assertEquals(round * 5, a.attempts.get()); // Elapsed cooldown alone does nothing.
                a.unavailable = true;
                a.entered = new CountDownLatch(1); a.release = new CountDownLatch(1);
                var leader = workers.submit(() -> assertThrows(SQLException.class, a::getConnection));
                assertTrue(a.entered.await(2, TimeUnit.SECONDS));
                var follower = workers.submit(() -> assertThrows(SQLException.class, ddl::getConnection));
                try {
                    assertThrows(TimeoutException.class, () -> follower.get(100, TimeUnit.MILLISECONDS));
                    // A waiting acquisition and a blocked physical connect must not hold B's lock.
                    assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                        try (var connection = b.getConnection()) { assertTrue(connection.isValid(1)); }
                    });
                } finally { a.release.countDown(); }
                leader.get(2, TimeUnit.SECONDS); follower.get(2, TimeUnit.SECONDS);
                assertEquals((round + 1) * 5, a.attempts.get());
                assertEquals(0, ddl.attempts.get());
                assertTrue(shared.cooling());
            }
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(RequestDrivenDataSource.COOLDOWN_MILLIS));
            a.unavailable = false;
            a.entered = new CountDownLatch(1); a.release = new CountDownLatch(1);
            var leader = workers.submit(() -> { return a.getConnection(); });
            assertTrue(a.entered.await(2, TimeUnit.SECONDS));
            var follower = workers.submit(() -> { return ddl.getConnection(); });
            try {
                assertThrows(TimeoutException.class, () -> follower.get(100, TimeUnit.MILLISECONDS));
            } finally { a.release.countDown(); }
            try (var first = leader.get(2, TimeUnit.SECONDS); var second = follower.get(2, TimeUnit.SECONDS)) {
                assertTrue(first.isValid(1)); assertTrue(second.isValid(1));
            }
            assertFalse(shared.cooling());
            assertEquals(11, a.attempts.get()); assertEquals(1, ddl.attempts.get());
        }
    }

    @Test
    void waitingForHealthyConnectTimesOutWithoutCoolingOrAnotherProbe() throws Exception {
        var shared = new DatasourceRecovery(new AtomicLong()::get);
        try (var pool = new Pool(shared); var worker = Executors.newSingleThreadExecutor()) {
            pool.entered = new CountDownLatch(1); pool.release = new CountDownLatch(1);
            var leader = worker.submit(() -> { return pool.getConnection(); });
            assertTrue(pool.entered.await(2, TimeUnit.SECONDS));
            try {
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                        assertThrows(DatasourceRecovery.Unavailable.class, () -> pool.getConnection(100)));
                assertFalse(shared.cooling());
                assertEquals(1, pool.attempts.get());
            } finally { pool.release.countDown(); }
            try (var connection = leader.get(2, TimeUnit.SECONDS)) { assertTrue(connection.isValid(1)); }
        }
    }

    @Test
    void cancelledAWaiterPreservesInterruptAndDoesNotBlockB() throws Exception {
        var shared = new DatasourceRecovery(new AtomicLong()::get);
        try (var a = new Pool(shared); var b = new Pool(new AtomicLong());
             var workers = Executors.newFixedThreadPool(2)) {
            a.setMaxWait(3000);
            a.entered = new CountDownLatch(1); a.release = new CountDownLatch(1);
            var leader = workers.submit(() -> { return a.getConnection(); });
            assertTrue(a.entered.await(2, TimeUnit.SECONDS));
            var waiting = new CountDownLatch(1);
            var thread = new AtomicReference<Thread>();
            var follower = workers.submit(() -> {
                thread.set(Thread.currentThread()); waiting.countDown();
                assertThrows(DatasourceRecovery.Unavailable.class, a::getConnection);
                return Thread.currentThread().isInterrupted();
            });
            try {
                assertTrue(waiting.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> follower.get(100, TimeUnit.MILLISECONDS));
                thread.get().interrupt();
                assertTrue(follower.get(1, TimeUnit.SECONDS));
                assertFalse(shared.cooling()); assertEquals(1, a.attempts.get());
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                    try (var connection = b.getConnection()) { assertTrue(connection.isValid(1)); }
                });
            } finally { a.release.countDown(); }
            try (var connection = leader.get(2, TimeUnit.SECONDS)) { assertTrue(connection.isValid(1)); }
            try (var connection = a.getConnection()) { assertTrue(connection.isValid(1)); }
            assertEquals(1, a.attempts.get());
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
