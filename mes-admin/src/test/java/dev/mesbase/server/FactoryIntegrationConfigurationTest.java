package dev.mesbase.server;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import dev.mesbase.server.schema.V34__FactoryBusinessIntegration;
import dev.mesbase.system.factory.ManagedFactory;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryIntegrationConfigurationTest {
    static MockEnvironment environment() {
        return new MockEnvironment().withProperty("mes.integration.connections.test-a.factory-id", "A")
                .withProperty("mes.integration.connections.test-a.provider", "DINGJIE")
                .withProperty("mes.integration.connections.test-a.service-url", "https://erp.invalid/api")
                .withProperty("mes.integration.connections.test-a.credential-reference", "test.secret")
                .withProperty("mes.integration.connections.test-a.organization", "TEST ONLY")
                .withProperty("mes.integration.connections.test-a.enabled", "true")
                .withProperty("mes.integration.connections.test-a.revision", "3")
                .withProperty("test.secret", "TEST ONLY SECRET");
    }

    static ManagedFactory erp(String id) {
        return new ManagedFactory(id, "TEST", id, "TEST ONLY", null, "ENABLED", 1, "ERP", "DINGJIE", "test-a", 2, false);
    }

    @Test
    void defaultsAndLegacyImportNeverSilentlyDowngrade() {
        var local = new ManagedFactory("A", "T", "A", "A", null, "ENABLED", 1);
        assertEquals(FactoryRuntimeMode.LOCAL, new FactoryIntegrationRegistry(environment()).snapshot(local).mode());
        var env = new MockEnvironment().withProperty("mes.integration.warehouse.factories.B.mode", "CONNECTED")
                .withProperty("mes.integration.warehouse.factories.B.provider", "existing-provider")
                .withProperty("mes.integration.warehouse.factories.B.binding-key", "existing-binding");
        var migration = new V34__FactoryBusinessIntegration(env);
        var rows = migration.legacy(List.of("A", "B"));
        assertEquals("LOCAL", rows.get(0).mode());
        assertEquals(new V34__FactoryBusinessIntegration.Legacy("B", "ERP", "existing-provider", "existing-binding"), rows.get(1));
        env.setProperty("mes.integration.warehouse.factories.B.binding-key", "");
        assertThrows(IllegalStateException.class, () -> migration.legacy(List.of("B")));
        env.setProperty("mes.integration.warehouse.factories.A.mode", "BROKEN");
        assertThrows(IllegalStateException.class, () -> migration.legacy(List.of("A")));
    }

    @Test
    void registryValidatesFactoryProviderReferenceAddressCredentialOrganizationAndVersion() {
        var registry = new FactoryIntegrationRegistry(environment());
        var snapshot = registry.snapshot(erp("A"));
        assertEquals("DINGJIE", snapshot.erpType());
        assertEquals("test-a", snapshot.configurationRef());
        assertEquals(3L, snapshot.configurationRevision());
        assertEquals(1, registry.choices("A").size());
        assertTrue(registry.choices("B").isEmpty());
        assertThrows(FoundationException.class, () -> registry.snapshot(erp("B")));
        assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED, assertThrows(FoundationException.class,
                () -> registry.snapshot(new ManagedFactory("A", "T", "A", "A", null, "ENABLED", 1, "ERP", null, null, 1, false))).code());
        for (var field : List.of("enabled", "provider", "service-url", "credential-reference", "organization", "revision")) {
            var env = environment();
            env.setProperty("mes.integration.connections.test-a." + field, switch (field) {
                case "enabled" -> "false";
                case "revision" -> "0";
                case "provider" -> "SAP";
                case "service-url" -> "https://user:secret@erp.invalid";
                default -> "";
            });
            assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED,
                    assertThrows(FoundationException.class, () -> new FactoryIntegrationRegistry(env).snapshot(erp("A"))).code());
        }
        var missing = environment();
        missing.setProperty("test.secret", "");
        assertThrows(FoundationException.class, () -> new FactoryIntegrationRegistry(missing).snapshot(erp("A")));
        assertThrows(FoundationException.class, () -> registry.snapshot(new ManagedFactory("A", "T", "A", "A", null, "ENABLED", 1, "LOCAL", "DINGJIE", "test-a", 2, false)));
        assertFalse(snapshot.toString().contains("SECRET"));
    }

    @Test
    void irreversibleAuthorityLatchSurvivesUnbindAndOldClientEditing() {
        var old = new ManagedFactory("A", "T", "A", "A", 1L, "ENABLED", 1, "LOCAL", null, null, 1, true);
        assertEquals(ErrorCode.FACTORY_MODE_CHANGE_REQUIRES_MIGRATION,
                assertThrows(FoundationException.class, () -> FactoryIntegrationRegistry.configure(old, erp("A"))).code());
        var unbound = FactoryIntegrationRegistry.configure(old, new ManagedFactory("A", "T", "A", "A", null, "ENABLED", 1));
        assertTrue(unbound.integrationLocked());
        assertThrows(FoundationException.class, () -> FactoryIntegrationRegistry.configure(unbound, erp("A")));
    }

    @Test
    void requestSnapshotIsStableAndNotInheritedByWorkersAndRejectsSwitching() throws Exception {
        var env = environment();
        var registry = new FactoryIntegrationRegistry(env);
        var admitted = registry.snapshot(erp("A"));
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            FactoryContext.bind(new FactoryScope(admitted.factoryId()));
            BusinessRequestSnapshot.install(new BusinessRequestSnapshot(new BusinessRuntimeBinding(admitted.factoryId(), new DatasourceIdentity("TEST"), 1), () -> null, admitted));
            env.setProperty("test.secret", "");
            env.setProperty("mes.runtime.enabled", "true");
            env.setProperty("mes.integration.warehouse.factories.A.mode", "LOCAL_ONLY");
            var integration = new WarehouseIntegration();
            assertEquals(Mode.CONNECTED, integration.configuredMode(new FactoryScope(admitted.factoryId())));
            assertThrows(FoundationException.class, () -> integration.configuredMode(new FactoryScope(new FactoryId("B"))));
            assertSame(admitted, FactoryIntegrationContext.required());
            var changed = new FactoryIntegrationSnapshot(admitted.factoryId(), FactoryRuntimeMode.LOCAL, null, null, 3, null);
            assertThrows(FoundationException.class, () -> FactoryIntegrationContext.run(changed, () -> {
            }));
            assertThrows(FoundationException.class, () -> registry.snapshot(erp("A")));
            try (var workers = Executors.newSingleThreadExecutor()) {
                assertTrue(workers.submit(() -> FactoryIntegrationContext.current().isEmpty()).get());
                workers.submit(() -> FactoryIntegrationContext.run(admitted, () -> {
                    assertSame(admitted, FactoryIntegrationContext.required());
                    assertThrows(FoundationException.class, () -> FactoryContext.bind(new FactoryScope(new FactoryId("B"))));
                })).get();
                assertTrue(workers.submit(() -> FactoryIntegrationContext.current()
                        .isEmpty() && FactoryContext.current().isEmpty()).get());
            }
            assertThrows(FoundationException.class, () -> BusinessRequestSnapshot.install(new BusinessRequestSnapshot(new BusinessRuntimeBinding(admitted.factoryId(), new DatasourceIdentity("TEST"), 1), () -> null, admitted)));
            FactoryContext.bind(new FactoryScope(new FactoryId("B")));
            assertThrows(FoundationException.class, FactoryIntegrationContext::required);
        } finally {
            BusinessRequestSnapshot.finish(request);
            FactoryContext.clear();
            RequestContextHolder.resetRequestAttributes();
        }
        assertTrue(FactoryIntegrationContext.current().isEmpty());
        assertThrows(IllegalStateException.class, () -> FactoryIntegrationContext.run(admitted, () -> {
            throw new IllegalStateException("TEST ONLY");
        }));
        assertTrue(FactoryContext.current().isEmpty());
        assertTrue(FactoryIntegrationContext.current().isEmpty());
    }
}
