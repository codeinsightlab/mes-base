package dev.mesbase.modules.material;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.basic.controller.MaterialCategoryController;
import dev.mesbase.business.basic.controller.MaterialController;
import dev.mesbase.business.basic.controller.SafetyStockController;
import dev.mesbase.business.basic.controller.UnitMeasureController;
import dev.mesbase.business.basic.controller.WorkshopController;
import dev.mesbase.business.basic.controller.WorkstationController;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialCategoryQueryAdapter;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialQueryAdapter;
import dev.mesbase.business.basic.sync.UnconfiguredSafetyStockQueryAdapter;
import dev.mesbase.business.basic.sync.UnconfiguredUnitMeasureQueryAdapter;
import dev.mesbase.business.basic.sync.UnconfiguredWorkshopQueryAdapter;
import dev.mesbase.business.basic.sync.UnconfiguredWorkstationQueryAdapter;
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
        for (var controller : List.of(MaterialController.class, MaterialCategoryController.class,
                UnitMeasureController.class, WorkstationController.class, WorkshopController.class,
                SafetyStockController.class)) {
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
