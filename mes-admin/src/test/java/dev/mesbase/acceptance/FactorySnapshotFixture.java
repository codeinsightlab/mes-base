package dev.mesbase.acceptance;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;

/**
 * TEST ONLY: explicit synthetic snapshots; no production Environment fallback.
 */
public final class FactorySnapshotFixture {
    private final Map<String, FactoryIntegrationSnapshot> snapshots = new ConcurrentHashMap<>();

    public FactorySnapshotFixture() {
        snapshots.put("A", local("A"));
        snapshots.put("B", local("B"));
        snapshots.put("SIMPLE_B", local("SIMPLE_B"));
    }

    public static FactoryIntegrationSnapshot local(String id) {
        return new FactoryIntegrationSnapshot(new FactoryId(id), FactoryRuntimeMode.LOCAL, null, null, 1, null);
    }

    public static FactoryIntegrationSnapshot erp(String id, String provider, String binding) {
        return new FactoryIntegrationSnapshot(new FactoryId(id), FactoryRuntimeMode.ERP, provider, binding, 1, 1L);
    }

    public FactoryIntegrationSnapshot required(String id) {
        var snapshot = Optional.ofNullable(id).map(nullableValue -> snapshots.get(nullableValue)).orElse(null);
        if (snapshot == null) throw new IllegalArgumentException("TEST ONLY unknown factory");
        return snapshot;
    }

    public static FactoryIntegrationSnapshot current(ApplicationContext context, String id) {
        return context.getBean(FactorySnapshotFixture.class).required(id);
    }

    public static void set(ApplicationContext context, FactoryIntegrationSnapshot snapshot) {
        context.getBean(FactorySnapshotFixture.class).snapshots.put(snapshot.factoryId().value(), snapshot);
    }
}
