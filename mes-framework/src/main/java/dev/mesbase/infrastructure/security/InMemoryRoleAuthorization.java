package dev.mesbase.infrastructure.security;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.mesbase.core.security.*;
import dev.mesbase.core.factory.FactoryScope;
/** Demo/test adapter. Replaces grants atomically; every request consults current grants. */
public final class InMemoryRoleAuthorization implements AuthorizationService {
    private final Map<String,List<Role>> grants=new ConcurrentHashMap<>();
    public void replace(AuthenticatedActor actor,List<Role> roles) { grants.put(actor.id(),List.copyOf(roles)); }
    public boolean hasPermission(AuthenticatedActor actor,FactoryScope scope,Permission permission) {
        return grants.getOrDefault(actor.id(),List.of()).stream().anyMatch(role -> role.factoryId().equals(scope.factoryId()) && role.permissions().contains(permission));
    }
}
