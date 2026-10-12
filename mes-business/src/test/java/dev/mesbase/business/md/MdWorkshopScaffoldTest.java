// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md;

import dev.mesbase.business.md.controller.MdWorkshopController;
import dev.mesbase.business.md.controller.MdWorkshopPermissions;
import dev.mesbase.business.md.dao.MdWorkshopMapper;
import dev.mesbase.business.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.business.md.domain.MdWorkshopQueryDto;
import dev.mesbase.business.md.domain.MdWorkshopResource;
import dev.mesbase.business.md.domain.MdWorkshopUpdateCommand;
import dev.mesbase.business.md.service.impl.MdWorkshopServiceImpl;
import dev.mesbase.business.md.sync.UnconfiguredMdWorkshopQueryAdapter;
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
public class MdWorkshopScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(MdWorkshopMapper.class));
        for (Method method : MdWorkshopController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : MdWorkshopServiceImpl.class.getDeclaredMethods()) {
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
        var service = MdWorkshopServiceImpl.class;
        var list = service.getMethod("selectPage", MdWorkshopQueryDto.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(list);
        Assertions.assertEquals(MdWorkshopResource.PREFIX, list.business());
        Assertions.assertEquals(BusinessCache.Type.LIST, list.type());
        Assertions.assertTrue(BusinessCacheQuery.class
                .isAssignableFrom(MdWorkshopQueryDto.class));
        var detail = service.getMethod("selectById", Long.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(detail);
        Assertions.assertEquals(MdWorkshopResource.PREFIX, detail.business());
        Assertions.assertEquals(BusinessCache.Type.DETAIL, detail.type());
        var create = service.getMethod("insert", MdWorkshopCreateCommand.class)
                .getAnnotation(BusinessCacheEvict.class);
        Assertions.assertNotNull(create);
        Assertions.assertEquals(BusinessCacheEvict.Scope.LIST, create.scope());
        var update = service.getMethod("updateById", Long.class, MdWorkshopUpdateCommand.class)
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
        var access = MdWorkshopController.class.getDeclaredMethod("sync")
                .getAnnotation(EndpointAccess.class);
        Assertions.assertEquals(MdWorkshopPermissions.SYNC, access.permission());
        Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        var failure = Assertions.assertThrows(FoundationException.class,
                () -> new UnconfiguredMdWorkshopQueryAdapter().query(scope, 1000));
        Assertions.assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, failure.code());
    }
}

