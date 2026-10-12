// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro;

import dev.mesbase.business.pro.controller.ProWorkorderController;
import dev.mesbase.business.pro.controller.ProWorkorderPermissions;
import dev.mesbase.business.pro.dao.ProWorkorderMapper;
import dev.mesbase.business.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.business.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.business.pro.domain.ProWorkorderResource;
import dev.mesbase.business.pro.domain.ProWorkorderUpdateCommand;
import dev.mesbase.business.pro.service.impl.ProWorkorderServiceImpl;
import dev.mesbase.business.pro.sync.UnconfiguredProWorkorderQueryAdapter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
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
public class ProWorkorderScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(ProWorkorderMapper.class));
        for (Method method : ProWorkorderController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : ProWorkorderServiceImpl.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals("sync")) {
                Assertions.assertNull(method.getAnnotation(Transactional.class));
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            Assertions.assertNotNull(transaction);
            Assertions.assertEquals("businessTransactionManager", transaction.transactionManager());
        }
    }

    @Test
    void cacheContractsUseTheFullPermissionResource() throws Exception {
        var service = ProWorkorderServiceImpl.class;
        var list = service.getMethod("selectPage", ProWorkorderQueryDto.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(list);
        Assertions.assertEquals(ProWorkorderResource.PREFIX, list.business());
        Assertions.assertEquals(BusinessCache.Type.LIST, list.type());
        Assertions.assertTrue(BusinessCacheQuery.class
                .isAssignableFrom(ProWorkorderQueryDto.class));
        var detail = service.getMethod("selectById", Long.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(detail);
        Assertions.assertEquals(ProWorkorderResource.PREFIX, detail.business());
        Assertions.assertEquals(BusinessCache.Type.DETAIL, detail.type());
        var create = service.getMethod("insert", ProWorkorderCreateCommand.class)
                .getAnnotation(BusinessCacheEvict.class);
        Assertions.assertNotNull(create);
        Assertions.assertEquals(BusinessCacheEvict.Scope.LIST, create.scope());
        var update = service.getMethod("updateById", Long.class, ProWorkorderUpdateCommand.class)
                .getAnnotation(BusinessCacheEvict.class);
        var delete = service.getMethod("deleteByIds", List.class)
                .getAnnotation(BusinessCacheEvict.class);
        Assertions.assertNotNull(update);
        Assertions.assertNotNull(delete);
        Assertions.assertEquals(BusinessCacheEvict.Scope.LIST_AND_DETAIL, update.scope());
        Assertions.assertEquals(update.scope(), delete.scope());
    }

    @Test
    void synchronizationHasIndependentAuthorizationAndExplicitDefaultFailure() throws Exception {
        var access = ProWorkorderController.class.getDeclaredMethod("sync")
                .getAnnotation(EndpointAccess.class);
        Assertions.assertEquals(ProWorkorderPermissions.SYNC, access.permission());
        Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        var failure = Assertions.assertThrows(FoundationException.class,
                () -> new UnconfiguredProWorkorderQueryAdapter().query(scope, 1000));
        Assertions.assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, failure.code());
    }
}

