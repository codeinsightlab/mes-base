package dev.mesbase.system.domain;

/** Current enabled role scope, read from the platform database; not a token claim. */
public record WorkspaceRoleScope(String kind, String factoryId) {}
