package dev.mesbase.server;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.WarehouseErpAdapter;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Normal mode authority never reads legacy Environment, including standalone/disabled runtime.
 */
class WarehouseModeAuthorityTest {
    @Test
    void legacyYamlCannotAuthorizeWithoutSnapshotOrOverrideExplicitLocalSnapshot() {
        for (String runtime : List.of("true", "false")) {
            new ApplicationContextRunner().withUserConfiguration(WarehouseIntegration.class)
                    .withPropertyValues("mes.runtime.enabled=" + runtime,
                            "mes.integration.warehouse.factories.A.mode=CONNECTED",
                            "mes.integration.warehouse.factories.A.provider=OTHER",
                            "mes.integration.warehouse.factories.A.binding-key=OTHER")
                    .run(context -> {
                        var service = context.getBean(WarehouseIntegration.class);
                        var scope = new FactoryScope(new FactoryId("A"));
                        assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED,
                                assertThrows(FoundationException.class, () -> service.configuredMode(scope)).code());
                        assertThrows(FoundationException.class, () -> service.submissionSelection(scope));
                        FactoryIntegrationContext.run(FactorySnapshotFixture.local("A"), () -> {
                            assertEquals(Mode.LOCAL_ONLY, service.configuredMode(scope));
                            assertEquals("local", service.submissionSelection(scope).provider());
                            assertThrows(FoundationException.class,
                                    () -> service.configuredMode(new FactoryScope(new FactoryId("B"))));
                        });
                        assertThrows(FoundationException.class, () -> service.configuredMode(scope));
                    });
        }
    }

    @Test
    void erpSnapshotRequiresExactAdapterAndDoesNotDowngradeOnMissingAdapterOrFailure() {
        var scope = new FactoryScope(new FactoryId("A"));
        var missing = new WarehouseIntegration(List.of());
        var adapter = mock(WarehouseErpAdapter.class);
        when(adapter.supports(scope, "FROZEN-A")).thenReturn(true);
        when(adapter.supportsProvider("E10-A")).thenReturn(true);
        var service = new WarehouseIntegration(List.of(adapter));
        FactoryIntegrationContext.run(FactorySnapshotFixture.erp("A", "E10-A", "FROZEN-A"), () -> {
            assertEquals(Mode.CONNECTED, missing.configuredMode(scope));
            assertThrows(FoundationException.class, () -> missing.submissionSelection(scope));
            var selection = service.submissionSelection(scope);
            assertEquals(new WarehouseIntegration.Selection(Mode.CONNECTED, "E10-A", "FROZEN-A"), selection);
            var command = new MaterialIssueSubmitCommand(1L, "TEST", "TEST", 1L, "TEST", null, null, 1L, "TEST", 1L, "TEST", 1L, 1, List.of(1L), null, List.of(), null);
            when(adapter.createMaterialIssue(any(), any())).thenThrow(new IllegalStateException("TEST ERP unavailable"));
            assertThrows(IllegalStateException.class, () -> service.createMaterialIssue(scope, selection, command));
            verify(adapter).createMaterialIssue(scope, command);
        });
    }
}
