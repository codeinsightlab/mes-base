package dev.mesbase.modules.material;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.controller.MaterialCategorySyncController;
import dev.mesbase.modules.material.controller.MaterialSyncController;
import dev.mesbase.modules.material.controller.SafetyStockSyncController;
import dev.mesbase.modules.material.controller.UnitMeasureSyncController;
import dev.mesbase.modules.material.controller.WorkshopSyncController;
import dev.mesbase.modules.material.controller.WorkstationSyncController;
import dev.mesbase.modules.material.sync.UnconfiguredMaterialCategoryQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredMaterialQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredSafetyStockQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredUnitMeasureQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredWorkshopQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredWorkstationQueryAdapter;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleSyncContractTest {
    @Test
    void allSixEndpointsRequireIndependentPermissionAndAllDefaultsReject() throws Exception {
        for (var controller : List.of(MaterialSyncController.class, MaterialCategorySyncController.class,
                UnitMeasureSyncController.class, WorkstationSyncController.class, WorkshopSyncController.class,
                SafetyStockSyncController.class)) {
            var method = controller.getDeclaredMethod("sync");
            var access = method.getAnnotation(EndpointAccess.class);
            assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
            assertTrue(access.permission().endsWith(":sync"));
            assertEquals(0, method.getParameterCount(), "scope is server-owned");
        }
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        for (var adapter : List.of(new UnconfiguredMaterialQueryAdapter(),
                new UnconfiguredMaterialCategoryQueryAdapter(), new UnconfiguredUnitMeasureQueryAdapter(),
                new UnconfiguredWorkstationQueryAdapter(), new UnconfiguredWorkshopQueryAdapter(),
                new UnconfiguredSafetyStockQueryAdapter())) {
            var failure = assertThrows(InvocationTargetException.class,
                    () -> adapter.getClass().getMethod("query", FactoryScope.class, int.class)
                            .invoke(adapter, scope, 1000));
            assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, ((FoundationException) failure.getCause()).code());
        }
    }
}
