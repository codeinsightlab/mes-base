// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.modules.material.controller.WorkstationController;
import dev.mesbase.modules.material.controller.WorkstationPermissions;
import dev.mesbase.modules.material.controller.WorkstationSyncController;
import dev.mesbase.modules.material.dao.WorkstationMapper;
import dev.mesbase.modules.material.service.impl.WorkstationServiceImpl;
import dev.mesbase.modules.material.sync.UnconfiguredWorkstationQueryAdapter;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** TEST ONLY: scaffold contracts; add real business and database acceptance. */
public class WorkstationScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(WorkstationMapper.class));
        for (Method method : WorkstationController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : WorkstationServiceImpl.class.getDeclaredMethods()) {
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
        var access = WorkstationSyncController.class.getDeclaredMethod("sync")
                .getAnnotation(EndpointAccess.class);
        Assertions.assertEquals(WorkstationPermissions.SYNC, access.permission());
        Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        var failure = Assertions.assertThrows(FoundationException.class,
                () -> new UnconfiguredWorkstationQueryAdapter().query(scope, 1000));
        Assertions.assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, failure.code());
    }
}

