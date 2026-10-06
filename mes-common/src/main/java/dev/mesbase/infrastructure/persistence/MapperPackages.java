package dev.mesbase.infrastructure.persistence;

import java.util.List;
import java.util.Map;

/** Fixed package boundaries; a new Mapper never requires central registration. */
public final class MapperPackages {
    public static final Map<PersistenceOwner.Kind, List<String>> ROOTS = Map.of(
        PersistenceOwner.Kind.PLATFORM, List.of("dev.mesbase.system", "dev.mesbase.quartz", "dev.mesbase.platform"),
        PersistenceOwner.Kind.BUSINESS, List.of("dev.mesbase.mes", "dev.mesbase.modules"),
        PersistenceOwner.Kind.ERP, List.of("dev.mesbase.external"));
    private MapperPackages() {}
    public static boolean contains(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }
    public static PersistenceOwner.Kind owner(Class<?> type) {
        return ROOTS.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(root -> contains(type.getPackageName(), root)))
            .map(Map.Entry::getKey).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Mapper outside persistence packages: " + type.getName()));
    }
    /** Legacy programmatic fixtures/CLI accept explicit ownership outside scanned application packages. */
    public static boolean matches(Class<?> type, PersistenceOwner.Kind expected) {
        if (ROOTS.values().stream().flatMap(List::stream).anyMatch(root -> contains(type.getPackageName(), root)))
            return owner(type) == expected;
        PersistenceOwner legacy = type.getAnnotation(PersistenceOwner.class);
        return legacy != null && legacy.value() == expected;
    }
}
