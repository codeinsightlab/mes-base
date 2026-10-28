package dev.mesbase.common.cache;

import dev.mesbase.common.core.redis.RedisKeySegment;

/**
 * One object key contract shared by CRUD detail reads and directory batch reads.
 */
public final class DetailCacheKeys {
    private DetailCacheKeys() {
    }

    public static String detail(String resourcePrefix, Object id) {
        return prefix(resourcePrefix) + "detail:" + RedisKeySegment.encode(id.toString());
    }

    /**
     * Resource-wide write fence; never a second cached copy of an object.
     */
    public static String version(String resourcePrefix) {
        return prefix(resourcePrefix) + "detail-version";
    }

    public static String detailPattern(String resourcePrefix) {
        return prefix(resourcePrefix) + "detail:*";
    }

    private static String prefix(String value) {
        return value.endsWith(":") ? value : value + ":";
    }
}
