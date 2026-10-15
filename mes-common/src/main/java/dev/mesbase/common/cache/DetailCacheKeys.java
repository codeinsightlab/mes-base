package dev.mesbase.common.cache;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * One object key contract shared by CRUD detail reads and directory batch reads.
 */
public final class DetailCacheKeys {
    private DetailCacheKeys() {
    }

    public static String detail(String resourcePrefix, Object id) {
        return prefix(resourcePrefix) + "detail:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
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
