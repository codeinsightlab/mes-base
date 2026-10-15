package dev.mesbase.business.production;

import dev.mesbase.business.production.controller.MesOperationUserController;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserQueryDto;
import dev.mesbase.business.production.domain.MesOperationUserResource;
import dev.mesbase.business.production.domain.MesOperationUserUpdateCommand;
import dev.mesbase.business.production.service.impl.MesOperationUserServiceImpl;
import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEST ONLY: scaffold contracts; add real business and database acceptance.
 */
public class MesOperationUserScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(MesOperationUserMapper.class));
        for (Method method : MesOperationUserController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : MesOperationUserServiceImpl.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            Assertions.assertNotNull(transaction);
            Assertions.assertEquals("businessTransactionManager", transaction.transactionManager());
        }
    }

    @Test
    void qualificationRelationsAreReadWithoutCache() throws Exception {
        Assertions.assertNull(MesOperationUserServiceImpl.class.getMethod("selectById", Long.class)
                .getAnnotation(BusinessCache.class));
    }
}
