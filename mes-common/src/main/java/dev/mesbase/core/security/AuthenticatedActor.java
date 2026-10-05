package dev.mesbase.core.security;
import java.util.Objects;
/** Authenticated identity only. Roles and permissions are not cached here. */
public record AuthenticatedActor(String id) { public AuthenticatedActor { Objects.requireNonNull(id); if(id.isBlank()) throw new IllegalArgumentException("Empty actor"); } }
