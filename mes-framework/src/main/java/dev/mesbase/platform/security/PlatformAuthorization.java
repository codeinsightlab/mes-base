package dev.mesbase.platform.security;

import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;

/**
 * Explicit PLATFORM grants. Factory grants never imply platform administration.
 */
public interface PlatformAuthorization {
    boolean hasPermission(AuthenticatedActor actor, Permission permission);

    default boolean hasPlatformRole(AuthenticatedActor actor) {
        return false;
    }
}
