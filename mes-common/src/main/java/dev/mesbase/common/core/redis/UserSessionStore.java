package dev.mesbase.common.core.redis;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Indexed opaque sessions. Scripts serialize issuance/logout/revocation on the existing Redis instance.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class UserSessionStore {
    public static final String PREFIX = "mes:system:session:v2:";
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            local epoch=redis.call('GET',KEYS[2])
            if epoch and tonumber(epoch)>tonumber(ARGV[2]) then return 0 end
            local expired=redis.call('ZRANGEBYSCORE',KEYS[3],'-inf',ARGV[4])
            for _,k in ipairs(expired) do redis.call('ZREM',KEYS[3],k) end
            redis.call('SET',KEYS[2],ARGV[2])
            redis.call('HSET',KEYS[1],'payload',ARGV[1],'revision',ARGV[2],'userId',ARGV[3])
            redis.call('PEXPIREAT',KEYS[1],ARGV[5])
            redis.call('ZADD',KEYS[3],ARGV[5],KEYS[1])
            local last=redis.call('ZREVRANGE',KEYS[3],0,0,'WITHSCORES')
            redis.call('PEXPIREAT',KEYS[3],last[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            local epoch=redis.call('GET',KEYS[1])
            local revision=tonumber(ARGV[1])
            if not epoch or tonumber(epoch)<revision then redis.call('SET',KEYS[1],ARGV[1]) end
            local sessions=redis.call('ZRANGE',KEYS[2],0,-1)
            for _,k in ipairs(sessions) do
              local version=redis.call('HGET',k,'revision')
              if not version or tonumber(version)<revision then
                redis.call('DEL',k); redis.call('ZREM',KEYS[2],k)
              end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> LOGOUT = new DefaultRedisScript<>("""
            local user=redis.call('HGET',KEYS[1],'userId')
            redis.call('DEL',KEYS[1])
            if user then redis.call('ZREM',ARGV[1]..user,KEYS[1]) end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final SystemCacheNamespace namespace;

    public UserSessionStore(StringRedisTemplate redis, SystemCacheNamespace namespace) {
        this.redis = redis;
        this.namespace = namespace;
    }

    public boolean issue(String digest, long userId, long revision, String payload, Instant expires) {
        return Long.valueOf(1).equals(redis.execute(ISSUE,
                List.of(key(digest), epoch(userId), index(userId)), payload, Long.toString(revision),
                Long.toString(userId), Long.toString(Instant.now()
                        .toEpochMilli()), Long.toString(expires.toEpochMilli())));
    }

    public String read(String digest) {
        return (String) redis.opsForHash().get(key(digest), "payload");
    }

    public void logout(String digest) {
        redis.execute(LOGOUT, List.of(key(digest)), namespace.key("mes:system:user-sessions:"));
    }

    public void revoke(long userId, long revision) {
        redis.execute(REVOKE, List.of(epoch(userId), index(userId)), Long.toString(revision));
    }

    private String key(String digest) {
        return namespace.key(PREFIX + digest);
    }

    private String epoch(long userId) {
        return namespace.key("mes:system:session-epoch:" + userId);
    }

    private String index(long userId) {
        return namespace.key("mes:system:user-sessions:" + userId);
    }
}
