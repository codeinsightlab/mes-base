// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.modules.material.controller.SafetyStockController;
import dev.mesbase.modules.material.controller.SafetyStockPermissions;
import dev.mesbase.modules.material.controller.SafetyStockSyncController;
import dev.mesbase.modules.material.dao.SafetyStockMapper;
import dev.mesbase.modules.material.service.impl.SafetyStockServiceImpl;
import dev.mesbase.modules.material.sync.UnconfiguredSafetyStockQueryAdapter;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** TEST ONLY: scaffold contracts; add real business and database acceptance. */
public class SafetyStockScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(SafetyStockMapper.class));
        for (Method method : SafetyStockController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : SafetyStockServiceImpl.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            Assertions.assertNotNull(transaction);
            Assertions.assertEquals("businessTransactionManager", transaction.transactionManager());
        }
    }

    @Test
    void synchronizationHasIndependentAuthorizationAndExplicitDefaultFailure() throws Exception {
        var access = SafetyStockSyncController.class.getDeclaredMethod("sync")
                .getAnnotation(EndpointAccess.class);
        Assertions.assertEquals(SafetyStockPermissions.SYNC, access.permission());
        Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        var failure = Assertions.assertThrows(FoundationException.class,
                () -> new UnconfiguredSafetyStockQueryAdapter().query(scope, 1000));
        Assertions.assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, failure.code());
    }
}

