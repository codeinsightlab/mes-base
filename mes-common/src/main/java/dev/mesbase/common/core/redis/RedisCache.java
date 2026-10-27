package dev.mesbase.common.core.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.ReturnType;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * spring redis 工具类
 *
 * @author ruoyi
 **/
@SuppressWarnings(value = {"unchecked", "rawtypes"})
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class RedisCache {
    @Autowired
    public RedisTemplate redisTemplate;
    @Autowired
    private SystemCacheNamespace namespace;

    /**
     * Namespaced batch reads; callers must still enforce their resource scope.
     */
    public List<Object> getCacheObjects(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        return redisTemplate.opsForValue().multiGet(keys.stream().map(this::storageKey).toList());
    }

    /**
     * Atomically reject fills started before a committed detail invalidation.
     */
    public void setCacheObjectsIfVersion(String versionKey, String expectedVersion,
                                         Map<String, byte[]> values, int ttlSeconds) {
        if (values.isEmpty()) return;
        byte[] script = ("local v=redis.call('GET',KEYS[1]); "
                + "if ARGV[1]=='0' then if v then return 0 end "
                + "else if v~=ARGV[2] then return 0 end end; "
                + "for i=2,#KEYS do redis.call('SET',KEYS[i],ARGV[i+2],'EX',ARGV[3]) end; return 1")
                .getBytes(StandardCharsets.UTF_8);
        var arguments = new ArrayList<byte[]>();
        arguments.add(redisTemplate.getKeySerializer().serialize(storageKey(versionKey)));
        for (var key : values.keySet()) arguments.add(redisTemplate.getKeySerializer().serialize(storageKey(key)));
        arguments.add((expectedVersion == null ? "0" : "1").getBytes(StandardCharsets.UTF_8));
        arguments.add(expectedVersion == null ? new byte[0] : redisTemplate.getValueSerializer()
                .serialize(expectedVersion));
        arguments.add(Integer.toString(ttlSeconds).getBytes(StandardCharsets.UTF_8));
        for (var value : values.values()) arguments.add(redisTemplate.getValueSerializer().serialize(value));
        redisTemplate.execute((RedisCallback<Long>) connection -> connection.scriptingCommands()
                .eval(script, ReturnType.INTEGER, values.size() + 1, arguments.toArray(byte[][]::new)));
    }

    /** Atomically fills a shared snapshot only while its invalidation token is unchanged. */
    public boolean setCacheObjectIfVersion(String versionKey, String expectedVersion, String key,
                                           Object value, int ttlSeconds) {
        byte[] script = ("local v=redis.call('GET',KEYS[1]); "
                + "if ARGV[1]=='0' then if v then return 0 end "
                + "else if v~=ARGV[2] then return 0 end end; "
                + "redis.call('SET',KEYS[2],ARGV[3],'EX',ARGV[4]); return 1")
                .getBytes(StandardCharsets.UTF_8);
        byte[] version = redisTemplate.getKeySerializer().serialize(storageKey(versionKey));
        byte[] snapshot = redisTemplate.getKeySerializer().serialize(storageKey(key));
        byte[] expected = expectedVersion == null ? new byte[0]
                : redisTemplate.getValueSerializer().serialize(expectedVersion);
        byte[] encodedValue = redisTemplate.getValueSerializer().serialize(value);
        byte[] absent = "0".getBytes(StandardCharsets.UTF_8);
        byte[] present = "1".getBytes(StandardCharsets.UTF_8);
        byte[] ttl = Integer.toString(ttlSeconds).getBytes(StandardCharsets.UTF_8);
        Object rawResult = redisTemplate.execute((RedisCallback<Long>) connection -> connection.scriptingCommands()
                .eval(script, ReturnType.INTEGER, 2, version, snapshot,
                        expectedVersion == null ? absent : present, expected, encodedValue, ttl));
        return rawResult instanceof Number result && result.longValue() == 1L;
    }

    /** Publishes the committed generation and evicts its snapshot as one Redis operation. */
    public void invalidateVersionedObject(String versionKey, String snapshotKey, String newVersion) {
        byte[] script = "redis.call('SET',KEYS[1],ARGV[1]); redis.call('DEL',KEYS[2]); return 1"
                .getBytes(StandardCharsets.UTF_8);
        byte[] version = redisTemplate.getKeySerializer().serialize(storageKey(versionKey));
        byte[] snapshot = redisTemplate.getKeySerializer().serialize(storageKey(snapshotKey));
        byte[] value = redisTemplate.getValueSerializer().serialize(newVersion);
        redisTemplate.execute((RedisCallback<Long>) connection -> connection.scriptingCommands()
                .eval(script, ReturnType.INTEGER, 2, version, snapshot, value));
    }

    public String getCacheVersion(String key) {
        return (String) redisTemplate.opsForValue().get(storageKey(key));
    }

    public String storageKey(String logical) {
        return namespace.key(logical);
    }

    public <T> T getAndDelete(String logical) {
        return (T) redisTemplate.opsForValue().getAndDelete(storageKey(logical));
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value) {
        redisTemplate.opsForValue().set(storageKey(key), value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(storageKey(key), value, timeout, timeUnit);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit) {
        return redisTemplate.expire(storageKey(key), timeout, unit);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key) {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(storageKey(key));
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key) {
        return redisTemplate.delete(storageKey(key));
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return
     */
    public long deleteObject(final Collection<?> collection) {
        return redisTemplate.delete(collection.stream().map(value -> storageKey(String.valueOf(value))).toList());
    }

    /**
     * 缓存List数据
     *
     * @param key      缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList) {
        Long count = redisTemplate.opsForList().rightPushAll(storageKey(key), dataList);
        return count == null ? 0 : count;
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key) {
        return redisTemplate.opsForList().range(storageKey(key), 0, -1);
    }

    /**
     * 缓存Set
     *
     * @param key     缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet) {
        BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(storageKey(key));
        Iterator<T> it = dataSet.iterator();
        while (it.hasNext()) {
            setOperation.add(it.next());
        }
        return setOperation;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(final String key) {
        return redisTemplate.opsForSet().members(storageKey(key));
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null) {
            redisTemplate.opsForHash().putAll(storageKey(key), dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key) {
        return redisTemplate.opsForHash().entries(storageKey(key));
    }

    /**
     * 往Hash中存入数据
     *
     * @param key   Redis键
     * @param hKey  Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value) {
        redisTemplate.opsForHash().put(storageKey(key), hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key  Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey) {
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(storageKey(key), hKey);
    }

    /**
     * 删除Hash中的数据
     *
     * @param key
     * @param hKey
     */
    public void delCacheMapValue(final String key, final String hKey) {
        HashOperations hashOperations = redisTemplate.opsForHash();
        hashOperations.delete(storageKey(key), hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key   Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys) {
        return redisTemplate.opsForHash().multiGet(storageKey(key), hKeys);
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern) {
        Set<String> result = new LinkedHashSet<>();
        try (var cursor = redisTemplate.scan(ScanOptions.scanOptions().match(storageKey(pattern)).count(200).build())) {
            while (cursor.hasNext()) {
                if (result.size() >= 5000) throw new IllegalStateException("Redis scan bound exceeded");
                result.add(namespace.logical(String.valueOf(cursor.next())));
            }
        }
        return result;
    }

    /**
     * Scan physical keys in bounded batches; cluster deletes are single-key to avoid CROSSSLOT.
     */
    public void deleteByPattern(String logicalPattern) {
        String pattern = storageKey(logicalPattern);
        ScanOptions options = ScanOptions.scanOptions().match(redisTemplate.getKeySerializer().serialize(pattern))
                .count(200).build();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            if (connection instanceof RedisClusterConnection cluster) {
                for (RedisClusterNode node : cluster.clusterGetNodes()) {
                    if (node.isMaster()) {
                        try (Cursor<byte[]> cursor = cluster.scan(node, options)) {
                            while (cursor.hasNext()) cluster.keyCommands().del(cursor.next());
                        }
                    }
                }
            } else {
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    List<byte[]> batch = new ArrayList<>(200);
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() == 200) {
                            connection.keyCommands().del(batch.toArray(byte[][]::new));
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) connection.keyCommands().del(batch.toArray(byte[][]::new));
                }
            }
            return null;
        });
    }

    /**
     * 列表中是否存在此元素
     *
     * @param key
     * @param item
     * @return
     */
    public boolean listItemIsExists(String key, String item) {
        return redisTemplate.opsForList().indexOf(storageKey(key), item) != null;
    }

    /**
     * 判断key是否存在
     *
     * @param key
     * @return
     */
    public boolean hasKey(final String key) {
        return redisTemplate.hasKey(storageKey(key));
    }
}
