package dev.mesbase.modules.material;

import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import org.springframework.core.env.Environment;
import dev.mesbase.common.cache.DetailEntityCache;
import dev.mesbase.common.cache.DetailCacheKeys;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.cache.BusinessCacheAspect;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict.Scope;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialCreateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialPage;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.MaterialQueryDto;
import dev.mesbase.business.basic.domain.MaterialUpdateCommand;
import dev.mesbase.business.basic.service.BasicDataRules;
import dev.mesbase.business.basic.service.IMaterialService;
import dev.mesbase.business.basic.service.impl.MaterialServiceImpl;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialQueryPort;
import dev.mesbase.framework.sync.SyncCoordinator;
import org.springframework.beans.factory.ObjectProvider;
import dev.mesbase.platform.execution.FactoryContext;
import com.github.pagehelper.PageHelper;
import jakarta.validation.Validation;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: real isolated Redis + Spring-proxied H2 transactions; material Mapper/rules are fixtures.
 */
class BusinessCacheIntegrationTest {
    private static Process redisProcess;
    private static int redisPort;
    private AnnotationConfigApplicationContext context;
    private RedisCache redis;
    private BusinessCacheProcessor caches;
    private MaterialMapper mapper;
    private IMaterialService material;
    private Boundary boundary;
    private Probe probe;
    private BusinessDirectoryService directory;

    @BeforeAll
    static void startRedis() throws Exception {
        try (var socket = new ServerSocket(0)) {
            redisPort = socket.getLocalPort();
        }
        var log = Files.createTempFile("mes-cache-redis-", ".log");
        redisProcess = new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", String.valueOf(redisPort),
                "--save", "", "--appendonly", "no").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean ready = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                if (Files.readString(log).contains("Ready to accept connections")) {
                    ready = true;
                    break;
                }
                if (!redisProcess.isAlive())
                    throw new IllegalStateException("TEST ONLY Redis failed: " + Files.readString(log));
                Thread.sleep(50);
            }
            assertTrue(ready, "Isolated Redis must be ready");
        } catch (Exception | AssertionError failure) {
            redisProcess.destroy();
            throw failure;
        }
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redisProcess != null) {
            redisProcess.destroy();
            if (!redisProcess.waitFor(5, TimeUnit.SECONDS)) redisProcess.destroyForcibly();
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MockEnvironment()
                .withProperty("mes.system.enabled", "true").withProperty("mes.business.enabled", "true")
                .withProperty("mes.platform.url", "jdbc:h2:mem:cache-fixture")
                .withProperty("mes.system.cache-namespace", "test_only").getPropertySources().get("mockProperties"));
        context.register(Config.class);
        context.refresh();
        redis = context.getBean(RedisCache.class);
        caches = context.getBean(BusinessCacheProcessor.class);
        mapper = context.getBean(MaterialMapper.class);
        material = context.getBean(IMaterialService.class);
        boundary = context.getBean(Boundary.class);
        probe = context.getBean(Probe.class);
        directory = context.getBean(BusinessDirectoryService.class);
        FactoryContext.bind(scope("A"));
        redis.deleteByPattern("business:*");
        when(mapper.selectByPrimaryKey(anyLong())).thenAnswer(call -> row(call.getArgument(0), "LIST"));
        when(context.getBean(BasicDataRules.class).lockMaterial(anyLong()))
                .thenAnswer(call -> mapper.selectByPrimaryKey(call.getArgument(0)));
        when(mapper.selectByExample(any(MaterialExample.class))).thenAnswer(call -> {
            var page = PageHelper.getLocalPage();
            if (page != null) {
                page.setTotal(123);
                page.add(row(1L, "LIST"));
                return page;
            }
            return List.of(row(1L, "LIST"));
        });
        doAnswer(call -> {
            ((MaterialEntity) call.getArgument(0)).setId(1L);
            return 1;
        }).when(mapper).insert(any());
        when(mapper.insertSelective(any())).thenReturn(1);
        when(mapper.updateByPrimaryKey(any())).thenReturn(1);
        when(mapper.deleteByExample(any())).thenReturn(1);
    }

    @AfterEach
    void cleanup() {
        if (context != null) context.close();
        FactoryContext.clear();
    }

    @Test
    void actualMaterialDirectorySharesCrudDetailsAndLoadsOnlyOneMissingBatch() {
        var one = row(1L, "目录物料一");
        one.setSpecification("必须保留完整规格");
        var two = row(2L, "目录物料二");
        when(mapper.selectByExample(any())).thenReturn(List.of(one, two));
        var first = directory.materialEntities(List.of(2L, 1L, 2L));
        assertEquals(Set.of(1L, 2L), first.keySet());
        verify(mapper, times(1)).selectByExample(any());
        clearInvocations(mapper);
        assertEquals(one.getSpecification(), material.selectById(1L).getSpecification());
        assertEquals(two.getName(), directory.materialEntities(List.of(2L, 1L)).get(2L).getName());
        verify(mapper, never()).selectByPrimaryKey(anyLong());
        verify(mapper, never()).selectByExample(any());

        var three = row(3L, "先从详情缓存");
        when(mapper.selectByPrimaryKey(3L)).thenReturn(three);
        material.selectById(3L);
        assertEquals(three.getName(), directory.materialEntities(List.of(3L)).get(3L).getName());
        verify(mapper, never()).selectByExample(any());
        var four = row(4L, "仅加载缺失物料");
        when(mapper.selectByExample(any())).thenAnswer(call -> {
            MaterialExample query = call.getArgument(0);
            assertEquals(List.of(4L), query.getOredCriteria().getFirst().getAllCriteria().getFirst().getValue());
            return List.of(four);
        });
        assertEquals(4, directory.materialEntities(List.of(1L, 2L, 3L, 4L, 4L)).size());
        verify(mapper, times(1)).selectByExample(any());
        String namespace = caches.prefix("business:material");
        assertEquals(Set.of(DetailCacheKeys.detail(namespace, 1L), DetailCacheKeys.detail(namespace, 2L),
                        DetailCacheKeys.detail(namespace, 3L), DetailCacheKeys.detail(namespace, 4L)),
                Set.copyOf(redis.keys(namespace + "detail:*")));
    }

    @Test
    void materialDirectoryUsesCrudCommitEvictionAndBypassesSharedCacheDuringWrites() {
        var original = row(1L, "原物料");
        when(mapper.selectByExample(any())).thenReturn(List.of(original));
        directory.materialEntities(List.of(1L));
        assertThrows(IllegalStateException.class, () -> boundary.run(() -> {
            material.updateById(1L, new MaterialUpdateCommand("M", "已回滚名称", null, null, 1L, "ENABLED", null, null));
            assertTrue(redis.hasKey(DetailCacheKeys.detail(caches.prefix("business:material"), 1L)));
            return null;
        }, true));
        clearInvocations(mapper);
        assertEquals("原物料", directory.materialEntities(List.of(1L)).get(1L).getName());
        verify(mapper, never()).selectByExample(any());
        material.updateById(1L, new MaterialUpdateCommand("M", "已提交名称", null, null, 1L, "ENABLED", null, null));
        var changed = row(1L, "已提交名称");
        when(mapper.selectByExample(any())).thenReturn(List.of(changed));
        assertEquals(changed.getName(), directory.materialEntities(List.of(1L)).get(1L).getName());
        verify(mapper, times(1)).selectByExample(any());

        boundary.run(() -> {
            assertEquals("已提交名称", directory.materialEntities(List.of(1L)).get(1L).getName());
            return null;
        }, false);
        verify(mapper, times(2)).selectByExample(any());
        FactoryContext.bind(scope("B"));
        directory.materialEntities(List.of(1L));
        verify(mapper, times(3)).selectByExample(any());
        FactoryContext.clear();
        assertThrows(FoundationException.class, () -> directory.materialEntities(List.of(1L)));
    }

    @Test
    void offsetBoundaryAndTypedPageAndDetailRoundTrips() {
        for (int offset : List.of(0, 99, 100)) {
            clearInvocations(mapper);
            var first = material.selectPage(query(offset, 20, "M"));
            var second = material.selectPage(query(offset, 20, "M"));
            verify(mapper, times(offset < 100 ? 1 : 2)).selectByExample(any());
            assertInstanceOf(MaterialEntity.class, second.items().getFirst());
            assertEquals(first.items().getFirst().getId(), second.items().getFirst().getId());
            assertEquals(offset, second.offset());
            assertEquals(20, second.limit());
            assertEquals(123, second.total());
        }
        material.selectById(1L);
        var hit = material.selectById(1L);
        assertInstanceOf(MaterialEntity.class, hit);
        verify(mapper, times(1)).selectByPrimaryKey(1L);
        var keys = redis.keys(caches.prefix("business:material") + "*");
        for (String key : keys) {
            long ttl = redis.redisTemplate.getExpire(redis.storageKey(key), TimeUnit.SECONDS);
            assertTrue(ttl > 0 && ttl <= 300);
        }
    }

    @Test
    void paginationSortFiltersAndStableMapOrderHaveSeparateKeys() {
        material.selectPage(query(0, 20, "A"));
        material.selectPage(query(0, 10, "A"));
        material.selectPage(query(0, 20, "B"));
        verify(mapper, times(3)).selectByExample(any());
        Map<String, String> a = new LinkedHashMap<>();
        a.put("b", "2");
        a.put("a", "1");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("a", "1");
        b.put("b", "2");
        probe.list(new Query(0, 20, "id asc", a));
        probe.list(new Query(0, 20, "id asc", b));
        probe.list(new Query(0, 20, "id desc", b));
        assertEquals(2, probe.calls());
        var original = query(0, 20, "A");
        var changed = new MaterialQueryDto(0, 20, "A", "name", "spec", 1L, 2L, "ENABLED", "ext", "code", null, null);
        assertFalse(caches.queryHash(new Object[]{original}, original)
                .equals(caches.queryHash(new Object[]{changed}, changed)));
    }

    @Test
    void factoryBusinessIsolationAndMissingRecordsNeverCache() {
        material.selectById(1L);
        probe.detail(1L);
        FactoryContext.bind(scope("B"));
        material.selectById(1L);
        verify(mapper, times(2)).selectByPrimaryKey(1L);
        assertEquals(1, probe.calls());
        when(mapper.selectByPrimaryKey(99L)).thenReturn(null);
        assertThrows(FoundationException.class, () -> material.selectById(99L));
        assertThrows(FoundationException.class, () -> material.selectById(99L));
        verify(mapper, times(2)).selectByPrimaryKey(99L);
        assertEquals(null, probe.nullDetail(99L));
        assertEquals(null, probe.nullDetail(99L));
        assertEquals(3, probe.calls());
        FactoryContext.clear();
        assertThrows(FoundationException.class, () -> material.selectById(1L));
    }

    @Test
    void createUpdateDeleteEvictOnlyTheirFactoryBusinessAndType() {
        seed();
        material.insert(new MaterialCreateCommand("M", "name", null, null, 1L, "ENABLED", null, null));
        assertFalse(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
        assertOthersRemain();
        seed();
        material.updateById(1L, new MaterialUpdateCommand("M", "name", null, null, 1L, "ENABLED", null, null));
        assertFalse(has("business:material", "list:fixture"));
        assertFalse(has("business:material", "detail:fixture"));
        assertOthersRemain();
        seed();
        material.deleteByIds(List.of(1L));
        assertFalse(has("business:material", "list:fixture"));
        assertFalse(has("business:material", "detail:fixture"));
        assertOthersRemain();
    }

    @Test
    void springProxyDefersUntilActualCommitAndRollbackRetainsCache() {
        seed();
        boundary.run(() -> {
            material.updateById(1L, new MaterialUpdateCommand("M", "name", null, null, 1L, "ENABLED", null, null));
            assertTrue(has("business:material", "list:fixture"));
            assertTrue(has("business:material", "detail:fixture"));
            return null;
        }, false);
        assertFalse(has("business:material", "detail:fixture"));
        assertEquals(1, context.getBean(JdbcTemplate.class)
                .queryForObject("select count(*) from marker", Integer.class));
        seed();
        assertThrows(IllegalStateException.class, () -> boundary.run(() -> {
            material.deleteByIds(List.of(1L));
            assertTrue(has("business:material", "detail:fixture"));
            return null;
        }, true));
        assertTrue(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
        assertEquals(1, context.getBean(JdbcTemplate.class)
                .queryForObject("select count(*) from marker", Integer.class));
    }

    @Test
    void directoryBatchAndCrudDetailShareTheSameEntityKeyAndCommitEviction() {
        var details = new DetailEntityCache(redis, new MockEnvironment());
        var namespace = caches.prefix("business:material");
        var original = row(1L, "目录缓存的完整实体");
        original.setSpecification("完整规格字段不因目录回显而丢失");
        details.getAll(namespace, List.of(1L, 1L), MaterialEntity.class, MaterialEntity::getId, ids -> Map.of(1L, original));
        assertEquals(List.of(DetailCacheKeys.detail(namespace, 1L)), List.copyOf(redis.keys(namespace + "detail:*")));
        clearInvocations(mapper);
        var fromDirectory = material.selectById(1L);
        assertEquals(original.getName(), fromDirectory.getName());
        assertEquals(original.getSpecification(), fromDirectory.getSpecification());
        assertTrue(redis.keys("reference:*").isEmpty());
        verify(mapper, never()).selectByPrimaryKey(anyLong());
        var fromCrud = row(2L, "CRUD缓存的完整实体");
        when(mapper.selectByPrimaryKey(2L)).thenReturn(fromCrud);
        material.selectById(2L);
        assertEquals(fromCrud.getName(), details.getAll(namespace, List.of(2L), MaterialEntity.class, MaterialEntity::getId,
                ids -> {
                    throw new AssertionError("CRUD warm entry was not reused");
                }).get(2L).getName());
        boundary.run(() -> {
            caches.evict("business:material", Scope.LIST_AND_DETAIL);
            details.getAll(namespace, List.of(1L), MaterialEntity.class, MaterialEntity::getId,
                    ids -> {
                        throw new AssertionError("Evicted before commit");
                    });
            return null;
        }, false);
        assertTrue(redis.keys(namespace + "detail:*").isEmpty());
        var changed = row(1L, "新名称");
        details.getAll(namespace, List.of(1L), MaterialEntity.class, MaterialEntity::getId, ids -> Map.of(1L, changed));
        assertThrows(IllegalStateException.class, () -> boundary.run(() -> {
            caches.evict("business:material", Scope.LIST_AND_DETAIL);
            return null;
        }, true));
        assertEquals(changed.getName(), details.getAll(namespace, List.of(1L), MaterialEntity.class, MaterialEntity::getId,
                ids -> {
                    throw new AssertionError("Rollback evicted detail");
                }).get(1L).getName());
        details.getAll(namespace, List.of(3L), MaterialEntity.class, MaterialEntity::getId, ids -> {
            caches.evict("business:material", Scope.LIST_AND_DETAIL);
            return Map.of(3L, row(3L, "失效前开始的旧读取"));
        });
        assertTrue(redis.keys(namespace + "detail:*").isEmpty());
    }

    @Test
    void noTransactionEvictionCapturesScopeBeforeMethodChangesContext() {
        seed();
        probe.noTransactionWrite();
        FactoryContext.bind(scope("A"));
        assertFalse(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
        assertOthersRemain();
    }

    @Test
    void syncCreatedUpdatedSkippedAndRollbackFollowDynamicEviction() {
        var writer = context.getBean(IMaterialService.class);
        var input = new ExternalMaterial("M", "name", null, null, 1L, "ENABLED", null, null);

        seed();
        when(mapper.selectByExample(any())).thenReturn(List.of());
        assertEquals(1, writer.upsert(scope("A"), List.of(input)).created());
        assertFalse(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
        seed();
        var existing = row(1L, "old");
        when(context.getBean(BasicDataRules.class).lockMaterial(1L)).thenReturn(existing);
        when(mapper.selectByExample(any())).thenReturn(List.of(existing));
        assertEquals(1, writer.upsert(scope("A"), List.of(input)).updated());
        assertFalse(has("business:material", "detail:fixture"));
        seed();
        assertEquals(1, writer.upsert(scope("A"), List.of(input)).skipped());
        assertTrue(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
        when(mapper.selectByExample(any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> boundary.run(() -> writer.upsert(scope("A"), List.of(input)), true));
        assertTrue(has("business:material", "list:fixture"));
        assertTrue(has("business:material", "detail:fixture"));
    }

    @Test
    void readInWriteUnitBypassesCacheAndDoesNotPopulateUncommittedResults() {
        boundary.run(() -> {
            material.selectById(1L);
            material.selectById(1L);
            assertTrue(redis.keys(caches.prefix("business:material") + "*").isEmpty());
            return null;
        }, false);
        verify(mapper, times(2)).selectByPrimaryKey(1L);
    }

    @Test
    void invalidAnnotationContractIsNotSilentlyTreatedAsAMiss() {
        assertThrows(IllegalArgumentException.class, () -> probe.invalidList("not a query"));
        assertThrows(IllegalArgumentException.class, () -> probe.invalidId(List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> caches.evict("business:material:*", Scope.LIST));
        assertThrows(IllegalArgumentException.class, () -> caches.evict("material", Scope.LIST));
        assertThrows(IllegalArgumentException.class, () -> caches.evict("business::material", Scope.LIST));
        assertDoesNotThrow(() -> BusinessCacheProcessor.validateBusiness("business:pro:workorder"));
    }

    @Test
    void scanDeletesMultipleBatchesAndAllDetailsWithinExactBoundary() {
        seed();
        for (int i = 0; i < 450; i++) {
            redis.setCacheObject(caches.prefix("business:material") + "detail:" + i, "TEST ONLY", 300, TimeUnit.SECONDS);
        }
        caches.evict("business:material", Scope.LIST_AND_DETAIL);
        assertEquals(Set.of(DetailCacheKeys.version(caches.prefix("business:material"))),
                Set.copyOf(redis.keys(caches.prefix("business:material") + "*")));
        assertOthersRemain();
    }

    @Test
    void corruptPayloadFallsBackAndBusinessFailuresStillPropagate() {
        material.selectById(1L);
        String key = redis.keys(caches.prefix("business:material") + "detail:*").iterator().next();
        redis.setCacheObject(key, "INVALID TEST ONLY", 300, TimeUnit.SECONDS);
        material.selectById(1L);
        verify(mapper, times(2)).selectByPrimaryKey(1L);
        when(mapper.selectByPrimaryKey(88L)).thenThrow(new IllegalStateException("TEST ONLY database failure"));
        assertThrows(IllegalStateException.class, () -> material.selectById(88L));
        assertThrows(IllegalStateException.class, () -> material.selectById(88L));
        verify(mapper, times(2)).selectByPrimaryKey(88L);
    }

    @Test
    void redisFailuresDegradeWithoutMisreportingCommittedWrites() {
        var failingRedis = mock(RedisCache.class);
        when(failingRedis.getCacheObject(anyString())).thenThrow(new IllegalStateException("TEST ONLY unavailable"));
        doThrow(new IllegalStateException("TEST ONLY unavailable")).when(failingRedis)
                .setCacheObject(anyString(), any(), any(Integer.class), any(TimeUnit.class));
        doThrow(new IllegalStateException("TEST ONLY unavailable")).when(failingRedis).deleteByPattern(anyString());
        var failing = new BusinessCacheProcessor(failingRedis, new SpringCommitActions(), new MockEnvironment());
        assertEquals(null, failing.read("TEST_ONLY", failing.returnType(MaterialEntity.class)));
        assertDoesNotThrow(() -> failing.write("TEST_ONLY", row(1L, "value")));
        assertDoesNotThrow(() -> boundary.run(() -> {
            failing.evict("business:material", Scope.LIST_AND_DETAIL);
            return null;
        }, false));
        assertEquals(1, context.getBean(JdbcTemplate.class)
                .queryForObject("select count(*) from marker", Integer.class));
    }

    @Test
    void clusterScanVisitsMastersAndUsesSingleKeyDeletes() {
        var cluster = mock(RedisClusterConnection.class);
        var master = mock(RedisClusterNode.class);
        var replica = mock(RedisClusterNode.class);
        when(master.isMaster()).thenReturn(true);
        when(cluster.clusterGetNodes()).thenReturn(List.of(master, replica));
        when(cluster.keyCommands()).thenReturn(cluster);
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(cluster.scan(eq(master), any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("TEST_ONLY_1".getBytes(StandardCharsets.UTF_8), "TEST_ONLY_2".getBytes(StandardCharsets.UTF_8));
        var factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(cluster);
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        var clusterRedis = new RedisCache();
        clusterRedis.redisTemplate = template;
        ReflectionTestUtils.setField(clusterRedis, "namespace", context.getBean(SystemCacheNamespace.class));
        clusterRedis.deleteByPattern(caches.prefix("business:material") + "list:*");
        verify(cluster).scan(eq(master), any(ScanOptions.class));
        verify(cluster, never()).scan(eq(replica), any(ScanOptions.class));
        verify(cluster).del("TEST_ONLY_1".getBytes(StandardCharsets.UTF_8));
        verify(cluster).del("TEST_ONLY_2".getBytes(StandardCharsets.UTF_8));
    }

    private static FactoryScope scope(String id) {
        return new FactoryScope(new FactoryId(id));
    }

    private static MaterialQueryDto query(int offset, int limit, String code) {
        return new MaterialQueryDto(offset, limit, code, null, null, null, null, null, null, null, null, null);
    }

    private static MaterialEntity row(Long id, String name) {
        var row = new MaterialEntity();
        row.setId(id);

        row.setCode("M");
        row.setName(name);
        row.setUnitId(1L);
        row.setStatus("ENABLED");
        return row;
    }

    private boolean has(String business, String suffix) {
        return redis.hasKey(caches.prefix(business) + suffix);
    }

    private void seed() {
        FactoryContext.bind(scope("A"));
        for (String business : List.of("business:material", "business:materials", "erp:material", "business:material:list")) {
            for (String suffix : List.of("list:fixture", "detail:fixture", "detail:second"))
                redis.setCacheObject(caches.prefix(business) + suffix, "TEST ONLY", 300, TimeUnit.SECONDS);
        }
        FactoryContext.bind(scope("B"));
        redis.setCacheObject(caches.prefix("business:material") + "list:fixture", "TEST ONLY", 300, TimeUnit.SECONDS);
        FactoryContext.bind(scope("A"));
    }

    private void assertOthersRemain() {
        assertTrue(has("business:materials", "list:fixture"));
        assertTrue(has("business:material:list", "detail:fixture"));
        assertTrue(has("erp:material", "detail:fixture"));
        FactoryContext.bind(scope("B"));
        assertTrue(has("business:material", "list:fixture"));
        FactoryContext.bind(scope("A"));
    }

    public record Query(int offset, int limit, String sort, Map<String, String> filters) implements BusinessCacheQuery {
        public int cacheOffset() {
            return offset;
        }

        public int cacheLimit() {
            return limit;
        }

        public String cacheSort() {
            return sort;
        }
    }

    public static class Probe {
        private int count;

        public int calls() {
            return count;
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        @BusinessCache(business = "erp:material", type = BusinessCache.Type.LIST)
        public MaterialPage list(Query query) {
            count++;
            return new MaterialPage(List.of(row(1L, "probe")), 1, query.offset(), query.limit());
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        @BusinessCache(business = "erp:material", type = BusinessCache.Type.DETAIL)
        public MaterialEntity detail(Long id) {
            count++;
            return row(id, "erp:material");
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        @BusinessCache(business = "erp:material", type = BusinessCache.Type.DETAIL)
        public MaterialEntity nullDetail(Long id) {
            count++;
            return null;
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        @BusinessCache(business = "erp:material", type = BusinessCache.Type.LIST)
        public MaterialPage invalidList(String query) {
            throw new AssertionError("Must validate annotation");
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        @BusinessCache(business = "erp:material", type = BusinessCache.Type.DETAIL)
        public MaterialEntity invalidId(List<Long> id) {
            throw new AssertionError("Must validate annotation");
        }

        @BusinessCacheEvict(business = "business:material", scope = Scope.LIST)
        public void noTransactionWrite() {
            FactoryContext.bind(scope("B"));
        }
    }

    public static class Boundary {
        private final JdbcTemplate jdbc;

        public Boundary(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(transactionManager = "businessTransactionManager")
        public Object run(Supplier<?> work, boolean fail) {
            jdbc.update("insert into marker values (1)");
            Object result = work.get();
            if (fail) throw new IllegalStateException("TEST ONLY rollback");
            return result;
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true, order = BusinessCacheAspect.TRANSACTION_ORDER)
    @Import({RedisCache.class, SystemCacheNamespace.class, BusinessCacheProcessor.class, BusinessCacheAspect.class})
    static class Config {
        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory connectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1", redisPort);
        }

        @Bean
        RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
            var template = new RedisTemplate<String, Object>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new JdkSerializationRedisSerializer());
            template.afterPropertiesSet();
            return template;
        }

        @Bean
        DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:cache_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        }

        @Bean
        OwnedTransactionManager businessTransactionManager(DriverManagerDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        JdbcTemplate jdbc(DriverManagerDataSource source) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("create table marker (id int)");
            return jdbc;
        }

        @Bean
        SpringCommitActions commits() {
            return new SpringCommitActions();
        }

        @Bean
        MaterialMapper mapper() {
            return mock(MaterialMapper.class);
        }

        @Bean
        BusinessDirectoryService directory(RedisCache redis, BusinessCacheProcessor caches, Environment environment,
                                           ObjectProvider<OwnedTransactionManager> transactions,
                                           ObjectProvider<RoutingMapper> routes,
                                           ObjectProvider<OperationMapper> operations,
                                           ObjectProvider<MaterialMapper> materials) {
            return new BusinessDirectoryService(mock(SysUserMapper.class),
                    new DetailEntityCache(redis, environment), caches, mock(PlatformPersistence.class),
                    transactions, routes, operations, materials);
        }

        @Bean
        BasicDataRules rules() {
            return mock(BasicDataRules.class);
        }

        @Bean
        SyncCoordinator coordinator() {
            return mock(SyncCoordinator.class);
        }

        @Bean
        MaterialServiceImpl material(MaterialMapper mapper, BasicDataRules rules,
                                     ObjectProvider<MaterialQueryPort> queries, SyncCoordinator coordinator,
                                     ObjectProvider<IMaterialService> self, BusinessCacheProcessor caches) {
            return new MaterialServiceImpl(mapper, rules, queries, coordinator, self,
                    Validation.buildDefaultValidatorFactory().getValidator(), caches);
        }

        @Bean
        Boundary boundary(JdbcTemplate jdbc) {
            return new Boundary(jdbc);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }
}
