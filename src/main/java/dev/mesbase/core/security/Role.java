package dev.mesbase.core.security;
import java.util.Set;
import java.util.Objects;
import dev.mesbase.core.factory.FactoryId;
public record Role(String id, FactoryId factoryId, Set<Permission> permissions) {
    public Role { Objects.requireNonNull(id); Objects.requireNonNull(factoryId); permissions=Set.copyOf(permissions); }
}
