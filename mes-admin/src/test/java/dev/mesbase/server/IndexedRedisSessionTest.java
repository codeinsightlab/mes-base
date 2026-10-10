package dev.mesbase.server;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.core.redis.UserSessionStore;
import java.net.ServerSocket;
import java.nio.file.Files;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: launches and stops its own loopback Redis, never attaches to an existing service.
 */
@EnabledIfSystemProperty(named = "mes.test.redis", matches = "true")
class IndexedRedisSessionTest {
    @Test
    void realLuaSerializesLoginRevocationLogoutAndKeepsAbsoluteExpiry() throws Exception {
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
                    .withProperty("mes.platform.url", "jdbc:h2:mem:TEST-ONLY-" + UUID.randomUUID()));
            var sessions = new UserSessionStore(redis, namespace);
            Instant expires = Instant.now().plusSeconds(60);
            assertTrue(sessions.issue("first", 42, 1, "SAFE IDENTITY", expires));
            String key = namespace.key(UserSessionStore.PREFIX + "first");
            long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
            sessions.read("first");
            assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) <= ttl);
            sessions.logout("first");
            assertNull(sessions.read("first"));
            try (var workers = Executors.newFixedThreadPool(4)) {
                var tasks = new ArrayList<Future<?>>();
                for (int i = 0; i < 40; i++) {
                    String digest = "old-" + i;
                    tasks.add(workers.submit(() -> sessions.issue(digest, 42, 1, "OLD", expires)));
                    tasks.add(workers.submit(() -> sessions.revoke(42, 2)));
                }
                for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 40; i++) assertNull(sessions.read("old-" + i));
            assertFalse(sessions.issue("stale-login", 42, 1, "OLD", expires));
            assertTrue(sessions.issue("new-login", 42, 2, "NEW", expires));
            sessions.revoke(42, 2);
            sessions.revoke(42, 1);
            assertNotNull(sessions.read("new-login"));
            sessions.revoke(42, 3);
            assertNull(sessions.read("new-login"));
        } finally {
            connections.destroy();
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
