package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.utils.DictUtils;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.core.redis.CenterSettingsCache;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Own Redis process only; MYSQL additionally opt-in. No existing Redis or business rows are touched.
 */
@EnabledIfSystemProperty(named = "mes.test.redis", matches = "true")
class P1SettingsRedisTest {
    @Test
    void realRedisRenameRollbackFailureAndLateFillHaveFiniteExpiry() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var directory = Files.createTempDirectory("mes-p1-redis-");
        var process = new ProcessBuilder("/opt/homebrew/bin/redis-server", "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no", "--dir", directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        var connections = new LettuceConnectionFactory("127.0.0.1", port);
        try {
            connections.afterPropertiesSet();
            connections.start();
            var template = new RedisTemplate<String, Object>();
            template.setConnectionFactory(connections);
            template.setKeySerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            boolean ready = false;
            for (int i = 0; i < 50 && !ready; i++) {
                try (var connection = connections.getConnection()) {
                    ready = "PONG".equals(connection.ping());
                } catch (RuntimeException starting) {
                    Thread.sleep(20);
                }
            }
            assertTrue(ready);
            var redis = new RedisCache();
            redis.redisTemplate = template;
            var namespace = new SystemCacheNamespace(new MockEnvironment()
                    .withProperty("mes.platform.url", "jdbc:h2:mem:TEST_ONLY_P1_" + UUID.randomUUID()));
            ReflectionTestUtils.setField(redis, "namespace", namespace);
            var faults = spy(redis);
            try (var h = new P1CorrectnessTest.Harness(faults)) {
                var type = new SysDictType();
                type.setDictName("TEST type");
                type.setDictType("test_old");
                type.setStatus("0");
                h.dictTypes.insertDictType(type);
                type.setDictId(h.db.jdbc.queryForObject("SELECT dict_id FROM sys_dict_type", Long.class));
                var item = new SysDictData();
                item.setDictLabel("TEST label");
                item.setDictValue("one");
                item.setDictSort(1L);
                item.setDictType("test_old");
                item.setStatus("0");
                h.dictData.insertDictData(item);
                item.setDictCode(h.db.jdbc.queryForObject("SELECT dict_code FROM sys_dict_data", Long.class));
                assertEquals("TEST label", h.dictTypes.selectDictDataByType("test_old").getFirst().getDictLabel());
                String old = redis.storageKey("committed-v1:sys_dict:test_old");
                assertTrue(template.getExpire(old, TimeUnit.MILLISECONDS) > 0);
                assertTrue(template.getExpire(old, TimeUnit.MILLISECONDS) <= 30000);
                var tx = new TransactionTemplate(h.manager);
                assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                    item.setDictLabel("UNCOMMITTED");
                    h.dictData.updateDictData(item);
                    assertEquals("UNCOMMITTED", h.dictTypes.selectDictDataByType("test_old").getFirst().getDictLabel());
                    throw new IllegalStateException("TEST rollback");
                }));
                assertEquals("TEST label", h.dictTypes.selectDictDataByType("test_old").getFirst().getDictLabel());
                type.setDictType("test_new");
                h.dictTypes.updateDictType(type);
                assertFalse(template.hasKey(old));
                assertNull(h.dictTypes.selectDictDataByType("test_old"));
                assertEquals("TEST label", h.dictTypes.selectDictDataByType("test_new").getFirst().getDictLabel());
                String current = redis.storageKey("committed-v1:sys_dict:test_new");
                doThrow(new IllegalStateException("TEST Redis eviction unavailable")).when(faults)
                        .deleteObject(anyString());
                item.setDictType("test_new");
                item.setDictLabel("COMMITTED");
                h.dictData.updateDictData(item);
                assertEquals("COMMITTED", h.db.jdbc.queryForObject("SELECT dict_label FROM sys_dict_data", String.class));
                assertEquals("TEST label", h.dictTypes.selectDictDataByType("test_new").getFirst().getDictLabel());
                assertTrue(template.getExpire(current, TimeUnit.MILLISECONDS) > 0);
                // A second independent cache reader has no local cache and shares the same finite key.
                var second = new CenterSettingsCache(redis, new SpringCommitActions());
                assertNotNull(second.get("sys_dict:test_new"));
                second.put("test:late-fill", "OLD");
                second.invalidate("test:late-fill");
                second.put("test:late-fill", "OLD"); // Explicitly accepted late-fill window, never permanent.
                String late = redis.storageKey("committed-v1:test:late-fill");
                assertTrue(template.getExpire(late, TimeUnit.MILLISECONDS) <= 30000);
                Thread.sleep(30500);
                assertFalse(template.hasKey(current));
                assertFalse(template.hasKey(late));
                assertEquals("COMMITTED", DictUtils.getDictLabel("test_new", "one"));
                assertEquals("one", DictUtils.getDictValue("test_new", "COMMITTED"));
                assertEquals("", DictUtils.getDictLabel("test_missing", "unknown"));
                assertEquals("COMMITTED", h.dictTypes.selectDictDataByType("test_new").getFirst().getDictLabel());
            }
        } finally {
            connections.destroy();
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
