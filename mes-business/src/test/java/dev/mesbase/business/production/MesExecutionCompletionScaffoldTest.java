package dev.mesbase.business.production;

import dev.mesbase.business.production.controller.MesExecutionCompletionController;
import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.domain.MesExecutionCompletionQueryDto;
import dev.mesbase.business.production.domain.MesExecutionCompletionResource;
import dev.mesbase.business.production.service.impl.MesExecutionCompletionServiceImpl;
import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEST ONLY: scaffold contracts; add real business and database acceptance.
 */
public class MesExecutionCompletionScaffoldTest {
    @Test
    void contractsStayScoped() {
        Assertions.assertEquals(
                PersistenceOwner.Kind.BUSINESS,
                MapperPackages.owner(MesExecutionCompletionMapper.class));
        for (Method method : MesExecutionCompletionController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            EndpointAccess access = method.getAnnotation(EndpointAccess.class);
            Assertions.assertNotNull(access);
            Assertions.assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
        }
        for (Method method : MesExecutionCompletionServiceImpl.class.getDeclaredMethods()) {
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
        var service = MesExecutionCompletionServiceImpl.class;
        var list = service.getMethod("selectPage", MesExecutionCompletionQueryDto.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(list);
        Assertions.assertEquals(MesExecutionCompletionResource.PREFIX, list.business());
        Assertions.assertEquals(BusinessCache.Type.LIST, list.type());
        Assertions.assertTrue(BusinessCacheQuery.class
                .isAssignableFrom(MesExecutionCompletionQueryDto.class));
        var detail = service.getMethod("selectById", String.class)
                .getAnnotation(BusinessCache.class);
        Assertions.assertNotNull(detail);
        Assertions.assertEquals(MesExecutionCompletionResource.PREFIX, detail.business());
        Assertions.assertEquals(BusinessCache.Type.DETAIL, detail.type());
        Assertions.assertTrue(Arrays.stream(service.getMethods())
                .noneMatch(m -> Set.of("insert", "updateById", "deleteByIds").contains(m.getName())));
    }

}
