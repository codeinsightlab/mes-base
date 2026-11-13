package dev.mesbase.business.production;

import dev.mesbase.business.production.controller.MesOperatorQualificationController;
import dev.mesbase.business.production.dao.MesOperatorQualificationMapper;
import dev.mesbase.business.production.service.impl.MesOperatorQualificationServiceImpl;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEST ONLY: scaffold contracts; add real business and database acceptance.
 */
public class MesOperatorQualificationScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(MesOperatorQualificationMapper.class));
        for (Method method : MesOperatorQualificationController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : MesOperatorQualificationServiceImpl.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            Assertions.assertNotNull(transaction);
            Assertions.assertEquals("businessTransactionManager", transaction.transactionManager());
        }
    }

    @Test
    void qualificationSettingsAreNotCachedAndUseExistingActionPermissions() throws Exception {
        for (var method : MesOperatorQualificationServiceImpl.class.getDeclaredMethods()) {
            Assertions.assertNull(method.getAnnotation(BusinessCache.class));
            Assertions.assertNull(method.getAnnotation(BusinessCacheEvict.class));
        }
        var controller = MesOperatorQualificationController.class;
        Assertions.assertEquals("business:mes:operation:user:list", controller.getMethod("summary", Long.class, int.class, int.class)
                .getAnnotation(EndpointAccess.class).permission());
        Assertions.assertEquals("business:mes:operation:user:create", controller.getMethod("grant", Long.class, MesOperatorQualificationController.Change.class)
                .getAnnotation(EndpointAccess.class).permission());
        Assertions.assertEquals("business:mes:operation:user:delete", controller.getMethod("revoke", Long.class, MesOperatorQualificationController.Change.class)
                .getAnnotation(EndpointAccess.class).permission());
    }
}
