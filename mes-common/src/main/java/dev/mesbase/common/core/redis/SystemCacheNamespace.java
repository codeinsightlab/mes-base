package dev.mesbase.common.core.redis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

/** Source platform cache ownership. Independent of Factory scope and permission. */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public final class SystemCacheNamespace {
    private final String prefix;

    public SystemCacheNamespace(Environment environment) {
        String configured = environment.getProperty("mes.system.cache-namespace", "");
        if (!configured.isBlank() && !configured.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Platform cache namespace invalid");
        String url = environment.getRequiredProperty("mes.platform.url");
        if (url.isBlank()) throw new IllegalArgumentException("Platform database identity required");
        String[] identity = databaseIdentity(url);
        String label = configured.isBlank() ? "default" : configured;
        prefix = "mes:" + RedisKeySegment.encode(label) + ":center:" + RedisKeySegment.encode(identity[0]) + ":"
                + RedisKeySegment.encode(identity[1]) + ":" + RedisKeySegment.encode(identity[2]) + ":";
    }

    public String key(String logical) {
        if (logical == null) throw new IllegalArgumentException("Cache key required");
        return prefix + logical;
    }

    public String logical(String physical) {
        if (physical == null || !physical.startsWith(prefix)) throw new IllegalArgumentException("Foreign cache key");
        return physical.substring(prefix.length());
    }

    private static String[] databaseIdentity(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:")) throw new IllegalArgumentException("Platform JDBC URL required");
        String url = jdbcUrl.substring(5);
        if (url.startsWith("h2:mem:")) {
            String database = url.substring("h2:mem:".length()).split("[;?]", 2)[0];
            if (database.isBlank()) throw new IllegalArgumentException("Platform database name required");
            return new String[] {"localhost", "0", database};
        }
        int protocol = jdbcUrl.indexOf("://");
        if (protocol < 0) throw new IllegalArgumentException("Platform JDBC URL must identify host, port and database");
        String scheme = jdbcUrl.substring(5, protocol).toLowerCase(Locale.ROOT);
        String databaseOverride = "";
        if (scheme.equals("sqlserver")) {
            int properties = url.indexOf(';');
            if (properties >= 0) {
                var matcher = Pattern.compile("(?i)(?:^|;)databaseName=([^;]+)").matcher(url.substring(properties));
                if (matcher.find()) databaseOverride = matcher.group(1);
                url = url.substring(0, properties);
            }
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) throw new IllegalArgumentException("Platform database host required");
            host = normalizeHost(host);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            int port = uri.getPort();
            if (port < 1) port = defaultPort(scheme);
            String path = uri.getPath();
            String database = path == null ? "" : path.replaceFirst("^/", "");
            if (database.isBlank()) database = databaseOverride;
            if (database.isBlank()) throw new IllegalArgumentException("Platform database name required");
            return new String[] {host, Integer.toString(port), database};
        } catch (URISyntaxException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("Platform JDBC URL must have a readable host, port and database", failure);
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1")) return "localhost";
        return normalized;
    }

    private static int defaultPort(String scheme) {
        return switch (scheme) {
            case "mysql", "mariadb" -> 3306;
            case "postgresql", "postgres" -> 5432;
            case "sqlserver" -> 1433;
            case "oracle" -> 1521;
            default -> throw new IllegalArgumentException("Platform database port must be explicit for this JDBC scheme");
        };
    }
}
