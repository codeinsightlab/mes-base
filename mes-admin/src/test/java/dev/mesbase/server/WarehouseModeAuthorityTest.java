package dev.mesbase.server;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
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
                        FactoryIntegrationContext.run(FactorySnapshotFixture.local("A"), () -> {
                            assertEquals(Mode.LOCAL_ONLY, service.configuredMode(scope));
                            assertThrows(FoundationException.class,
                                    () -> service.configuredMode(new FactoryScope(new FactoryId("B"))));
                        });
                        assertThrows(FoundationException.class, () -> service.configuredMode(scope));
                    });
        }
    }

    @Test
    void erpSnapshotReportsTrustedModeWithoutInventingAnOperationalAdapter() {
        var scope = new FactoryScope(new FactoryId("A"));
        var service = new WarehouseIntegration();
        FactoryIntegrationContext.run(FactorySnapshotFixture.erp("A", "E10-A", "FROZEN-A"), () -> {
            assertEquals(Mode.CONNECTED, service.configuredMode(scope));
            assertThrows(FoundationException.class,
                    () -> service.configuredMode(new FactoryScope(new FactoryId("B"))));
        });
    }
}
