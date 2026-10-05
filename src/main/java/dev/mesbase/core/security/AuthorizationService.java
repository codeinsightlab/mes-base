package dev.mesbase.core.security;
import dev.mesbase.core.factory.FactoryScope;
public interface AuthorizationService {
    boolean hasPermission(AuthenticatedActor actor, FactoryScope scope, Permission permission);
}
