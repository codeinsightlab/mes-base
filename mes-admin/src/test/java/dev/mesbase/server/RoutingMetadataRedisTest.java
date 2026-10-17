package dev.mesbase.server;

import dev.mesbase.common.core.redis.RoutingMetadataStore;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.server.managed.FactoryMetadataCache;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Collection;
import java.nio.file.Files;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: own loopback Redis and H2; no existing Redis or real factory database.
 */
@EnabledIfSystemProperty(named = "mes.test.redis", matches = "true")
class RoutingMetadataRedisTest {
    @Test
    void independentExpiryCommitEvictionAndAllowedLatePublication() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var dir = Files.createTempDirectory("mes-routing-redis-");
        var process = new ProcessBuilder("/opt/homebrew/bin/redis-server", "--bind", "127.0.0.1",
                "--port", Integer.toString(port), "--save", "", "--appendonly", "no", "--dir", dir.toString())
                .redirectErrorStream(true).redirectOutput(dir.resolve("redis.log").toFile()).start();
        var firstConnection = new LettuceConnectionFactory("127.0.0.1", port);
        var secondConnection = new LettuceConnectionFactory("127.0.0.1", port);
        try {
            firstConnection.afterPropertiesSet();
            firstConnection.start();
            secondConnection.afterPropertiesSet();
            secondConnection.start();
            var firstRedis = new StringRedisTemplate(firstConnection);
            var secondRedis = new StringRedisTemplate(secondConnection);
            boolean ready = false;
            for (int i = 0; i < 50 && !ready; i++) {
                try (var c = firstConnection.getConnection()) {
                    ready = "PONG".equals(c.ping());
                } catch (RuntimeException starting) {
                    Thread.sleep(20);
                }
            }
            assertTrue(ready);
            var ns = new SystemCacheNamespace(new MockEnvironment()
                    .withProperty("mes.platform.url", "jdbc:h2:mem:TEST-ONLY-" + UUID.randomUUID())
                    .withProperty("mes.system.cache-namespace", "routing-test"));
            var ttl = Duration.ofSeconds(2);
            var first = new RoutingMetadataStore(firstRedis, ns, ttl);
            var second = new RoutingMetadataStore(secondRedis, ns, ttl);
            first.publish("binding:A", "OLD");
            Thread.sleep(500);
            long remaining = firstRedis.getExpire(ns.key("factory-routing:entry:binding:A"), TimeUnit.MILLISECONDS);
            assertEquals("OLD", second.read("binding:A"));
            second.publish("binding:B", "OTHER");
            long after = firstRedis.getExpire(ns.key("factory-routing:entry:binding:A"), TimeUnit.MILLISECONDS);
            assertTrue(after <= remaining);
            assertTrue(after > 0 && after < 1800);
            second.invalidate(List.of("binding:A"));
            assertNull(first.read("binding:A"));
            // A read begun before eviction may publish stale data; its own TTL bounds that value.
            first.publish("binding:A", "LATE OLD");
            assertEquals("LATE OLD", second.read("binding:A"));
            Thread.sleep(2100);
            assertNull(first.read("binding:A"));
            assertNull(first.read("binding:B"));

            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE mf_factory(factory_id VARCHAR(64),enterprise_id VARCHAR(64),factory_code VARCHAR(32),factory_name VARCHAR(64),datasource_id BIGINT,status VARCHAR(16),revision BIGINT,runtime_mode VARCHAR(16) DEFAULT 'LOCAL',erp_provider VARCHAR(128),erp_config_ref VARCHAR(128),integration_revision BIGINT DEFAULT 1,integration_locked BOOLEAN DEFAULT TRUE)");
            jdbc.execute("CREATE TABLE mf_datasource(datasource_id BIGINT,datasource_name VARCHAR(64),db_host VARCHAR(64),db_port INT,db_name VARCHAR(64),db_username VARCHAR(64),password_cipher VARCHAR(64),status VARCHAR(16),revision BIGINT)");
            jdbc.execute("INSERT INTO mf_factory(factory_id,enterprise_id,factory_code,factory_name,datasource_id,status,revision) VALUES('A','TEST ONLY','A','Before',1,'ENABLED',1)");
            jdbc.execute("INSERT INTO mf_datasource VALUES(1,'TEST ONLY','invalid.test',3306,'TEST_ONLY','TEST_ONLY','ENCRYPTED_ONLY','ENABLED',1)");
            var persistence = new PlatformPersistence(source, Set.of(RoutingMetadataMapper.class));
            var healthy = new FactoryMetadataCache(persistence, second, persistence.mapper(RoutingMetadataMapper.class));
            assertEquals("Before", healthy.definition("A").factoryName());
            assertEquals("Before", healthy.defaultDefinition().factoryName());
            assertNull(healthy.definition("UNKNOWN"));
            assertNull(second.read("binding:UNKNOWN"));
            assertThrows(IllegalStateException.class, () -> persistence.run(() -> {
                jdbc.update("UPDATE mf_factory SET factory_name='Rollback'");
                healthy.changed(List.of("A"));
                throw new IllegalStateException("TEST ONLY rollback");
            }));
            assertEquals("Before", healthy.definition("A").factoryName());
            persistence.run(() -> {
                jdbc.update("UPDATE mf_factory SET factory_name='Committed'");
                healthy.changed(List.of("A"));
                return null;
            });
            assertNull(first.read("binding:A"));
            assertNull(first.read("default"));
            assertEquals("Committed", healthy.definition("A").factoryName());
            var failing = new RoutingMetadataStore(firstRedis, ns, ttl) {
                public void invalidate(Collection<String> fields) {
                    throw new IllegalStateException("TEST ONLY eviction failure");
                }
            };
            var management = new FactoryMetadataCache(persistence, failing, persistence.mapper(RoutingMetadataMapper.class));
            persistence.run(() -> {
                jdbc.update("UPDATE mf_factory SET factory_name='Saved despite Redis failure'");
                management.changed(List.of("A"));
                return null;
            });
            assertEquals("Saved despite Redis failure", jdbc.queryForObject("SELECT factory_name FROM mf_factory", String.class));
            assertEquals("Committed", healthy.definition("A").factoryName());
            Thread.sleep(2100);
            assertEquals("Saved despite Redis failure", healthy.definition("A").factoryName());
            var unavailable = new RoutingMetadataStore(firstRedis, ns, ttl) {
                public String read(String field) {
                    throw new IllegalStateException("TEST ONLY read failure");
                }
            };
            assertEquals(ErrorCode.ROUTING_METADATA_UNAVAILABLE, assertThrows(FoundationException.class,
                    () -> new FactoryMetadataCache(persistence, unavailable, persistence.mapper(RoutingMetadataMapper.class)).definition("A")).code());
            second.invalidate(List.of("binding:A"));
            var noPublish = new RoutingMetadataStore(firstRedis, ns, ttl) {
                public void publish(String field, String payload) {
                    throw new IllegalStateException("TEST ONLY publication failure");
                }
            };
            assertEquals(ErrorCode.ROUTING_METADATA_UNAVAILABLE, assertThrows(FoundationException.class,
                    () -> new FactoryMetadataCache(persistence, noPublish, persistence.mapper(RoutingMetadataMapper.class)).definition("A")).code());
            // After expiration, DB failure must fail, never serve an expired local copy.
            healthy.definition("A");
            Thread.sleep(2100);
            jdbc.execute("DROP TABLE mf_factory");
            assertThrows(FoundationException.class, () -> healthy.definition("A"));
        } finally {
            firstConnection.destroy();
            secondConnection.destroy();
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
