package dev.issuefixture;

import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;

import static org.mockito.Mockito.mock;

import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueItemServiceImpl;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;

import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.quality.service.QualityTreatmentService;


import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.issue.service.MaterialIssueRules;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.issue.service.IMesMaterialIssueService;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TEST ONLY: configured SIMPLE_B, retains uniquely tagged data for user recheck; no ERP calls.
 */
@EnabledIfEnvironmentVariable(named = "MES_VOID_MYSQL_ACCEPTANCE", matches = "true")
class MaterialIssueVoidMySqlTest {
    @Configuration
    @EnableTransactionManagement
    @Import({LocalStockService.class, QualityTreatmentService.class, MaterialIssueInputFacts.class, ProductionQuantityService.class, ExecutionQualityReferenceQueryImpl.class, MesProductionExecutionServiceImpl.class, MesMaterialIssueItemServiceImpl.class, ProductionChainLocks.class, ExecutionPathService.class, MaterialIssueSubmissionService.class, WorkorderErpFacts.class, WorkOrderService.class, RoutingReferenceRules.class, ProductionExecutionRules.class, MaterialIssueRules.class, MaterialIssueVoidService.class, MesMaterialIssueServiceImpl.class, WarehouseIntegration.class})
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

    @Test
    void realTransactionsVoidUnlockAndConcurrentCreationProtection() throws Exception {
        var business = AcceptanceBusinessDatabase.source();
        var jdbc = new JdbcTemplate(business);
        String tag = "TEST_VOID_" + UUID.randomUUID().toString().substring(0, 8);
        var first = jdbc.queryForList("SELECT w.product_id,w.routing_id FROM workorder w JOIN routing r ON r.id=w.routing_id WHERE r.status='PUBLISHED' AND r.published_at IS NOT NULL ORDER BY w.id LIMIT 1")
                .getFirst();
        long product = ((Number) first.get("product_id")).longValue(), routing = ((Number) first.get("routing_id")).longValue();
        var id = new FactoryId("SIMPLE_B");
        var identity = new DatasourceIdentity("TEST_ONLY_VOID_MYSQL");
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(id, "TEST_ONLY", "SIMPLE_B", "SIMPLE_B", FactoryStatus.ENABLED)));
        try (var registry = new RuntimeRegistry(); var c = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> {
            }, MaterialIssueHttpTest.MAPPERS);
            c.registerBean(FactoryBusinessDataSource.class, () -> new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(id, new BusinessRuntimeBinding(id, identity, 1))), registry));
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
            for (var m : MaterialIssueHttpTest.MAPPERS) mapper(c, m);
            c.register(Config.class);
            c.refresh();
            scoped(() -> {
                var tx = new TransactionTemplate(c.getBean(OwnedTransactionManager.class));
                // Synthetic target product/version for the correction rule, not a real routing publication acceptance.
                long[] target = tx.execute(s -> {
                    var mat = c.getBean(MaterialMapper.class).selectByPrimaryKey(product);
                    mat.setId(null);
                    mat.setCode(tag + "_PRODUCT");
                    mat.setName("TEST ONLY 产品订正目标");
                    c.getBean(MaterialMapper.class).insert(mat);
                    var route = c.getBean(RoutingMapper.class).selectByPrimaryKey(routing);
                    route.setId(null);
                    route.setCode(tag + "_ROUTE");
                    route.setName("TEST ONLY 工单订正规则版本");
                    route.setVersion(1);
                    route.setRevision(0);
                    c.getBean(RoutingMapper.class).insert(route);
                    var link = new RoutingProductEntity();
                    link.setRoutingId(route.getId());
                    link.setMaterialId(mat.getId());
                    c.getBean(RoutingProductMapper.class).insert(link);
                    return new long[]{mat.getId(), route.getId()};
                });
                var orders = c.getBean(WorkOrderService.class);
                var issues = c.getBean(IMesMaterialIssueService.class);
                var voids = c.getBean(MaterialIssueVoidService.class);
                var demand = new WorkOrderModels.Save(tag, product, BigDecimal.TEN, routing, null, null, null, null, "TEST ONLY MYSQL", 0, "WORK_ORDER");
                long wo = orders.create(demand).workorder().getId();
                var issue = issues.insert(new MesMaterialIssueCreateCommand(tag, wo, null, null, null, null, null, null, "RECORDED", "TEST ONLY MYSQL", null));
                var correction = new WorkOrderModels.Save(tag, target[0], BigDecimal.TEN, target[1], null, null, null, null, "TEST ONLY MYSQL", 0, "WORK_ORDER");
                assertEquals(ErrorCode.WORKORDER_ISSUE_REFERENCED, assertThrows(FoundationException.class, () -> orders.update(wo, correction)).code());
                var cancelled = voids.cancel(issue.getId());
                assertEquals("VOIDED", cancelled.getStatus());
                assertEquals(cancelled.getVoidOperationId(), voids.cancel(issue.getId()).getVoidOperationId());
                assertEquals(target[0], orders.update(wo, correction).workorder().getProductId());
                assertEquals(product, issues.selectById(issue.getId()).getProductId());
                var raceDemand = new WorkOrderModels.Save(tag + "_RACE", product, BigDecimal.TEN, routing, null, null, null, null, "TEST ONLY MYSQL", 0, "WORK_ORDER");
                long race = orders.create(raceDemand).workorder().getId();
                var start = new CountDownLatch(1);
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var a = pool.submit(() -> scoped(() -> {
                        start.await();
                        try {
                            issues.insert(new MesMaterialIssueCreateCommand(tag + "_RACE", race, null, product, null, null, routing, null, "RECORDED", "TEST ONLY MYSQL", null));
                            return "ISSUE";
                        } catch (FoundationException e) {
                            return e.code().name();
                        }
                    }));
                    var b = pool.submit(() -> scoped(() -> {
                        start.await();
                        try {
                            orders.update(race, new WorkOrderModels.Save(tag + "_RACE", target[0], BigDecimal.TEN, target[1], null, null, null, null, "TEST ONLY MYSQL", 0, "WORK_ORDER"));
                            return "CHANGED";
                        } catch (FoundationException e) {
                            return e.code().name();
                        }
                    }));
                    start.countDown();
                    String av = a.get(15, TimeUnit.SECONDS), bv = b.get(15, TimeUnit.SECONDS);
                    assertTrue(av.equals("ISSUE") && bv.equals("WORKORDER_ISSUE_REFERENCED") || av.equals("BAD_REQUEST") && bv.equals("CHANGED"));
                }
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_material_issue i JOIN workorder w ON w.id=i.workorder_id WHERE i.issue_no=? AND i.status<>'VOIDED' AND i.product_id<>w.product_id", Integer.class, tag + "_RACE"));
                System.out.println("MYSQL_VOID_VERIFIED synthetic tag=" + tag + " workorder=" + wo + " issue=" + issue.getId() + " targetProduct=" + target[0] + " targetRouting=" + target[1]);
                return null;
            });
        }
    }
}
