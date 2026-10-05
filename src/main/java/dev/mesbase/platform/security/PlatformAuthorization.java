package dev.mesbase.platform.security;
import dev.mesbase.core.security.*;
/** Explicit PLATFORM grants. Factory grants never imply platform administration. */
public interface PlatformAuthorization { boolean hasPermission(AuthenticatedActor actor, Permission permission); }
