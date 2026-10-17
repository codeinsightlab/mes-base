package dev.mesbase.business.wm;

import dev.mesbase.business.wm.controller.WmStockActionController;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.domain.WmStockActionResource;
import dev.mesbase.business.wm.service.impl.WmStockActionServiceImpl;
import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEST ONLY: scaffold contracts; add real business and database acceptance.
 */
public class WmStockActionScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(WmStockActionMapper.class));
        for (Method method : WmStockActionController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : WmStockActionServiceImpl.class.getDeclaredMethods()) {
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
        var service = WmStockActionServiceImpl.class;
        var list = service.getMethod("selectPage", WmStockActionQueryDto.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNull(list); // append-only facts must be immediately visible
        Assertions.assertTrue(BusinessCacheQuery.class
                .isAssignableFrom(WmStockActionQueryDto.class));
        var detail = service.getMethod("selectById", String.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(detail);
        Assertions.assertEquals(WmStockActionResource.PREFIX, detail.business());
        Assertions.assertEquals(BusinessCache.Type.DETAIL, detail.type());
    }

    @Test
    void immutableFactsHaveNoCrudWriteSurface() {
        Assertions.assertEquals(2, WmStockActionController.class.getDeclaredMethods().length - 1);
        Assertions.assertTrue(Arrays.stream(WmStockActionServiceImpl.class.getDeclaredMethods())
                .noneMatch(m -> List.of("insert", "updateById", "deleteByIds").contains(m.getName())));
    }
}
