package dev.mesbase.infrastructure.persistence;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates and records the interfaces discovered by the standard MyBatis-Spring scanner.
 */
public final class OwnerMapperCatalog {
    private final Map<PersistenceOwner.Kind, Set<Class<?>>> groups = new EnumMap<>(PersistenceOwner.Kind.class);
    private final Set<String> names = new HashSet<>();

    public OwnerMapperCatalog(Map<PersistenceOwner.Kind, List<String>> roots) {
        var all = roots.values().stream().flatMap(List::stream).toList();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (MapperPackages.contains(all.get(i), all.get(j)) || MapperPackages.contains(all.get(j), all.get(i))) {
                    throw new IllegalArgumentException("Persistence package roots overlap");
                }
            }
        }
        for (PersistenceOwner.Kind kind : PersistenceOwner.Kind.values()) groups.put(kind, new HashSet<>());
    }

    public void register(PersistenceOwner.Kind kind, Class<?> mapper) {
        if (!names.add(mapper.getSimpleName())) {
            throw new IllegalArgumentException("Duplicate Mapper name: " + mapper.getSimpleName());
        }
        PersistenceOwner owner = mapper.getAnnotation(PersistenceOwner.class);
        if (owner != null && owner.value() != kind) {
            throw new IllegalArgumentException("Mapper annotation conflicts with package: " + mapper.getName());
        }
        groups.get(kind).add(mapper);
    }

    public Set<Class<?>> types(PersistenceOwner.Kind kind) {
        return Set.copyOf(groups.get(kind));
    }
}
