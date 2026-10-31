package dev.issuefixture;

import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.mockito.Mockito.mock;


/**
 * Shared wiring for opt-in production fixtures. Retired issue CRUD scenarios are removed.
 */
@EnabledIfEnvironmentVariable(named = "MES_VOID_MYSQL_ACCEPTANCE", matches = "true")
class MaterialIssueVoidMySqlTest {
    @Configuration
    @EnableTransactionManagement
    @Import({LocalStockService.class, QualityTreatmentService.class, MaterialIssueInputFacts.class, ProductionQuantityService.class, ExecutionQualityReferenceQueryImpl.class, MesProductionExecutionServiceImpl.class, ProductionChainLocks.class, ExecutionPathService.class, WorkorderErpFacts.class, WorkOrderService.class, RoutingReferenceRules.class, ProductionExecutionRules.class, MaterialIssueVoidService.class, WarehouseIntegration.class})
    static class Config {
        @Bean
        BusinessDirectoryService directory() {
            return mock(BusinessDirectoryService.class);
        }

        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager manager(FactoryBusinessDataSource s) {
            return new OwnedTransactionManager(s);
        }

        @Bean
        SqlSessionFactory sessions(FactoryBusinessDataSource s, OwnedTransactionManager m) {
            return PersistenceFactories.create(s, MaterialIssueHttpTest.MAPPERS, PersistenceFactories::pagination, m::verifyAccess);
        }
    }

    static <T> void mapper(AnnotationConfigApplicationContext c, Class<T> type) {
        c.registerBean(type, () -> new SqlSessionTemplate(c.getBean(SqlSessionFactory.class)).getMapper(type));
    }

    static <T> T scoped(Callable<T> action) throws Exception {
        var u = new SysUser();
        u.setUserId(1L);
        u.setUserName("TEST_ONLY");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(1L, 1L, u, Set.of()), null, List.of()));
        FactoryContext.bind(new FactoryScope(new FactoryId("SIMPLE_B")));
        try {
            return action.call();
        } finally {
            FactoryContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

}
