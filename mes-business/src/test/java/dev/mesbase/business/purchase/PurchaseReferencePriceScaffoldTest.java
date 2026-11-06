package dev.mesbase.business.purchase;

import dev.mesbase.business.purchase.controller.PurchaseReferencePriceController;
import dev.mesbase.business.purchase.dao.PurchaseReferencePriceMapper;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceQueryDto;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceResource;

import dev.mesbase.business.purchase.service.impl.PurchaseReferencePriceServiceImpl;
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
public class PurchaseReferencePriceScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(PurchaseReferencePriceMapper.class));
        for (Method method : PurchaseReferencePriceController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : PurchaseReferencePriceServiceImpl.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            Assertions.assertNotNull(transaction);
            Assertions.assertEquals("businessTransactionManager", transaction.transactionManager());
        }
    }

    @Test
    void cacheContractsUseTheFullPermissionResource() throws Exception {
        var service = PurchaseReferencePriceServiceImpl.class;
        var list = service.getMethod("selectPage", PurchaseReferencePriceQueryDto.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNull(list, "Joined current names must not create a competing display cache");
        Assertions.assertTrue(BusinessCacheQuery.class
                .isAssignableFrom(PurchaseReferencePriceQueryDto.class));
        var detail = service.getMethod("selectById", Long.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(detail);
        Assertions.assertEquals(PurchaseReferencePriceResource.PREFIX, detail.business());
        Assertions.assertEquals(BusinessCache.Type.DETAIL, detail.type());
        var create = service.getMethod("insert", PurchaseReferencePriceCreateCommand.class)
                .getAnnotation(BusinessCacheEvict.class);
        Assertions.assertNotNull(create);
        Assertions.assertEquals(BusinessCacheEvict.Scope.LIST, create.scope());
        Assertions.assertFalse(java.util.Arrays.stream(service.getMethods()).anyMatch(method ->
                method.getName().equals("updateById") || method.getName().equals("deleteByIds")));
    }
}
