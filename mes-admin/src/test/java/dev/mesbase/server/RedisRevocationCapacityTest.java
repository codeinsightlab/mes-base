package dev.mesbase.server;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.core.redis.UserSessionStore;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: launches and stops its own loopback Redis, never attaches to an existing service.
 */
@EnabledIfSystemProperty(named = "mes.test.redis", matches = "true")
class RedisRevocationCapacityTest {
    @Test
    void measuresAtomicRevocationAndOtherClientLatencyAtIncreasingSessionCounts() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var dir = Files.createTempDirectory("mes-session-test-");
        var process = new ProcessBuilder("/opt/homebrew/bin/redis-server", "--bind", "127.0.0.1",
                "--port", Integer.toString(port), "--save", "", "--appendonly", "no", "--dir", dir.toString())
                .redirectErrorStream(true).redirectOutput(dir.resolve("redis.log").toFile()).start();
        var connections = new LettuceConnectionFactory("127.0.0.1", port);
        try {
            connections.afterPropertiesSet();
            connections.start();
            var redis = new StringRedisTemplate(connections);
            boolean ready = false;
            for (int i = 0; i < 50 && !ready; i++) {
                try {
                    ready = "PONG".equals(redis.getConnectionFactory().getConnection().ping());
                } catch (RuntimeException wait) {
                    Thread.sleep(20);
                }
            }
            assertTrue(ready);
            var namespace = new SystemCacheNamespace(new MockEnvironment()
                    .withProperty("mes.platform.url", "jdbc:h2:mem:CAPACITY-TEST-ONLY-" + UUID.randomUUID()));
            var sessions = new UserSessionStore(redis, namespace);
            var probes = new LettuceConnectionFactory("127.0.0.1", port);
            probes.afterPropertiesSet();
            probes.start();
            try (var connection = probes.getConnection(); var worker = Executors.newSingleThreadExecutor()) {
                var baseline = new ArrayList<Double>();
                for (int i = 0; i < 200; i++) {
                    long start = System.nanoTime();
                    connection.ping();
                    if (i >= 100) baseline.add((System.nanoTime() - start) / 1_000_000.0);
                }
                var rows = new ArrayList<String>();
                for (int count : new int[]{10, 100, 1000, 10000}) {
                    var revokeTimes = new ArrayList<Double>();
                    var repeatTimes = new ArrayList<Double>();
                    var pingTimes = Collections.synchronizedList(new ArrayList<Double>());
                    for (int sample = 0; sample < 5; sample++) {
                        long user = count * 10L + sample;
                        Instant expires = Instant.now().plusSeconds(600);
                        // Exactly count active sessions: 90% old and 10% new; also one dangling index entry.
                        int old = count - count / 10;
                        for (int i = 0; i < count; i++) {
                            assertTrue(sessions.issue(user + "-" + i, "test-digest-" + i, user, i < old ? 1 : 2, "SAFE TEST IDENTITY", expires));
                        }
                        String index = namespace.key("mes:system:{sessions}:user-sessions:" + user);
                        redis.opsForZSet().add(index, namespace.key(UserSessionStore.PREFIX + "missing-" + user), expires.toEpochMilli());
                        var running = new AtomicBoolean(true);
                        var readyProbe = new CountDownLatch(1);
                        Future<?> probe = worker.submit(() -> {
                            while (running.get()) {
                                long start = System.nanoTime();
                                connection.ping();
                                pingTimes.add((System.nanoTime() - start) / 1_000_000.0);
                                readyProbe.countDown();
                            }
                        });
                        assertTrue(readyProbe.await(5, TimeUnit.SECONDS));
                        long start = System.nanoTime();
                        sessions.revoke(user, 2);
                        revokeTimes.add((System.nanoTime() - start) / 1_000_000.0);
                        running.set(false);
                        probe.get(5, TimeUnit.SECONDS);
                        for (int i = 0; i < count; i++) {
                            if (i < old) assertNull(sessions.read(user + "-" + i, "test-digest-" + i));
                            else assertNotNull(sessions.read(user + "-" + i, "test-digest-" + i));
                        }
                        assertEquals((long) count / 10, redis.opsForZSet().zCard(index));
                        start = System.nanoTime();
                        sessions.revoke(user, 2);
                        repeatTimes.add((System.nanoTime() - start) / 1_000_000.0);
                        sessions.revoke(user, 1);
                        assertEquals((long) count / 10, redis.opsForZSet().zCard(index));
                        assertFalse(sessions.issue("stale-" + user, "stale", user, 1, "OLD", expires));
                        sessions.revoke(user, 3);
                        assertEquals(0L, redis.opsForZSet().zCard(index));
                        assertEquals(-1L, redis.getExpire(namespace.key("mes:system:{sessions}:session-epoch:" + user)));
                    }
                    rows.add("{\"sessions\":" + count + ",\"samples\":5,\"revokeMs\":" + revokeTimes
                            + ",\"repeatMs\":" + repeatTimes + ",\"concurrentPingCount\":" + pingTimes.size()
                            + ",\"concurrentPingP95Ms\":" + percentile(pingTimes, .95)
                            + ",\"concurrentPingMaxMs\":" + Collections.max(pingTimes) + "}");
                }
                Path output = Path.of("target/validation/redis-revocation-capacity.json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, "{\"environment\":\"isolated loopback Redis, two independent clients; no production load\","
                        + "\"baselinePingP95Ms\":" + percentile(baseline, .95) + ",\"results\":["
                        + String.join(",", rows) + "]}");
            } finally {
                probes.destroy();
            }
        } finally {
            connections.destroy();
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }

    private static double percentile(List<Double> values, double percentile) {
        var sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1);
    }
}
