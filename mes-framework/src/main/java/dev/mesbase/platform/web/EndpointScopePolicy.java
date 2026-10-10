package dev.mesbase.platform.web;

import java.util.List;

/**
 * URL ownership is independent of the client's selected factory.
 */
public final class EndpointScopePolicy {
    private static final List<String> PLATFORM = List.of("/system", "/tool", "/monitor");
    private static final List<String> SHARED = List.of("/session", "/personal", "/common", "/platform/files");

    private EndpointScopePolicy() {
    }

    public static boolean platformManagement(String path) {
        return matches(relative(path), PLATFORM);
    }

    public static boolean shared(String path) {
        return matches(relative(path), SHARED);
    }

    public static EndpointAccess.PermissionScope scope(String path) {
        return platformManagement(path) || shared(path)
                ? EndpointAccess.PermissionScope.PLATFORM : EndpointAccess.PermissionScope.FACTORY;
    }

    private static String relative(String path) {
        return path.startsWith("/api/") ? path.substring(4) : path;
    }

    private static boolean matches(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
