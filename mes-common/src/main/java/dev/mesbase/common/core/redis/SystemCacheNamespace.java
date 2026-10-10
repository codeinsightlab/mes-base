package dev.mesbase.common.core.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.core.env.Environment;

/**
 * Source platform cache ownership. Independent of Factory scope and permission.
 */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public final class SystemCacheNamespace {
    private final String prefix;

    public SystemCacheNamespace(Environment environment) {
        String configured = environment.getProperty("mes.system.cache-namespace", "");
        if (!configured.isBlank() && !configured.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Platform cache namespace invalid");
        String database = environment.getRequiredProperty("mes.platform.url");
        if (database.isBlank()) throw new IllegalArgumentException("Platform database identity required");
        try {
            String identity = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(database.getBytes(StandardCharsets.UTF_8)));
            prefix = "mes:" + (configured.isBlank() ? "" : "named:" + configured + ":") + "db:" + identity + ":";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }

    }

    public String key(String logical) {
        if (logical == null) throw new IllegalArgumentException("Cache key required");
        return prefix + logical;
    }

    public String logical(String physical) {
        if (!physical.startsWith(prefix)) throw new IllegalArgumentException("Foreign cache key");
        return physical.substring(prefix.length());
    }
}
