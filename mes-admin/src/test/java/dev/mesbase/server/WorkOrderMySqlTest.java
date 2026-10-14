package dev.mesbase.server;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.core.error.FoundationException;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.BatchRouteNodeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;

/**
 * TEST ONLY demand and execution convergence against configured acceptance MySQL; all writes roll back.
 */
class WorkOrderMySqlTest {
    static final Set<Class<?>> MAPPERS = Set.of(WorkorderMapper.class, ProductionBatchMapper.class, BatchRouteNodeMapper.class, RoutingMapper.class, RoutingNodeMapper.class, RoutingEdgeMapper.class, RoutingProductMapper.class, OperationMapper.class, WorkOrderExtraMapper.class, MaterialMapper.class, MesMaterialIssueMapper.class, MesProductionExecutionMapper.class);

    @Configuration
    @EnableTransactionManagement
    @Import({WorkOrderService.class, RoutingReferenceRules.class, ProductionExecutionRules.class, MesProductionExecutionServiceImpl.class})
    static class Config {
        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager manager(FactoryBusinessDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sessions(FactoryBusinessDataSource source, OwnedTransactionManager manager) {
            return PersistenceFactories.create(source, MAPPERS, PersistenceFactories::pagination, manager::verifyAccess);
        }

        @Bean
        WorkorderMapper mapperWorkorder(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(WorkorderMapper.class);
        }

        @Bean
        ProductionBatchMapper mapperProductionBatch(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(ProductionBatchMapper.class);
        }

        @Bean
        BatchRouteNodeMapper mapperBatchRouteNode(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(BatchRouteNodeMapper.class);
        }

        @Bean
        RoutingMapper mapperRouting(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(RoutingMapper.class);
        }

        @Bean
        RoutingNodeMapper mapperRoutingNode(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(RoutingNodeMapper.class);
        }

        @Bean
        RoutingEdgeMapper mapperRoutingEdge(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(RoutingEdgeMapper.class);
        }

        @Bean
        RoutingProductMapper mapperRoutingProduct(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(RoutingProductMapper.class);
        }

        @Bean
        OperationMapper mapperOperation(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(OperationMapper.class);
        }

        @Bean
        WorkOrderExtraMapper mapperWorkOrderExtra(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(WorkOrderExtraMapper.class);
        }

        @Bean
        MaterialMapper mapperMaterial(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(MaterialMapper.class);
        }

        @Bean
        MesMaterialIssueMapper mapperMesMaterialIssue(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(MesMaterialIssueMapper.class);
        }

        @Bean
        MesProductionExecutionMapper mapperMesProductionExecution(SqlSessionFactory s) {
            return new SqlSessionTemplate(s).getMapper(MesProductionExecutionMapper.class);
        }
    }

    @Test
    void demandExecutionAllocationAndHistoricalPreservationInRollbackOnlyTransaction() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mes.test.workorder.mysql"));
        var business = AcceptanceBusinessDatabase.source();
        var jdbc = new JdbcTemplate(business);
        var selected = jdbc.queryForList("SELECT w.product_id,w.routing_id FROM workorder w JOIN routing r ON r.id=w.routing_id JOIN material m ON m.id=w.product_id WHERE r.status='PUBLISHED' AND r.published_at IS NOT NULL AND m.status='ENABLED' AND m.material_type='PRODUCT' ORDER BY w.id LIMIT 1")
                .getFirst();
        Long product = ((Number) selected.get("product_id")).longValue();
        Long routing = ((Number) selected.get("routing_id")).longValue();
        String tag = "TEST_CHAIN_" + UUID.randomUUID();
        var id = new FactoryId("SIMPLE_B");
        var identity = new DatasourceIdentity("TEST_ONLY_WORKORDER_ROLLBACK");
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(id, "TEST_ONLY", "SIMPLE_B", "SIMPLE_B", FactoryStatus.ENABLED)));
        var user = new SysUser();
        user.setUserId(1L);
        user.setUserName("TEST_ONLY");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(1L, 1L, user, Set.of()), null, List.of()));
        try (var registry = new RuntimeRegistry(); var context = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> {
            }, MAPPERS);
            var source = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(id, new BusinessRuntimeBinding(id, identity, 1))), registry);
            context.getEnvironment().getPropertySources()
                    .addFirst(new MockEnvironment().withProperty("mes.business.enabled", "true").getPropertySources()
                            .get("mockProperties"));
            context.registerBean(FactoryBusinessDataSource.class, () -> source);
            context.register(Config.class);
            context.refresh();
            FactoryContext.bind(new FactoryScope(id));
            var service = context.getBean(WorkOrderService.class);
            var executionService = context.getBean(IMesProductionExecutionService.class);
            var manager = context.getBean("businessTransactionManager", OwnedTransactionManager.class);
            new TransactionTemplate(manager).execute(status -> {
                var batchesBefore = jdbc.queryForList("SELECT * FROM production_batch ORDER BY id");
                var pathsBefore = jdbc.queryForList("SELECT * FROM batch_route_node ORDER BY id");
                var input = new WorkOrderModels.Save(tag, product, new BigDecimal("1000"), routing, null, null, null, null, "TEST ONLY ROLLBACK", 0);
                var created = service.create(input);
                Long order = created.workorder().getId();
                assertEquals(0, created.batches().size());
                assertEquals(0, created.allocation().executionCount());
                assertThrows(FoundationException.class, () -> service.create(input));
                assertThrows(FoundationException.class, () -> service.update(order, new WorkOrderModels.Save(tag, product, new BigDecimal("1000"), routing, null, null, null, null, null, 99)));
                var e = executionService.insert(new MesProductionExecutionCreateCommand(order, routing, new BigDecimal("400"), "READY", null, null, tag));
                executionService.insert(new MesProductionExecutionCreateCommand(order, routing, new BigDecimal("300"), "READY", null, null, tag));
                assertEquals(new BigDecimal("700.000000"), service.allocation(order).allocatedTargetQty());
                assertEquals(new BigDecimal("300.000000"), service.allocation(order).remainingTargetQty());
                assertThrows(FoundationException.class, () -> executionService.updateById(e.getId(), new MesProductionExecutionUpdateCommand(order, routing, new BigDecimal("400"), "RUNNING", LocalDateTime.now(), null, tag)));
                service.release(order, 0);
                executionService.updateById(e.getId(), new MesProductionExecutionUpdateCommand(order, routing, new BigDecimal("1100"), "RUNNING", LocalDateTime.now(), null, tag));
                assertEquals(new BigDecimal("1400.000000"), service.allocation(order).allocatedTargetQty());
                assertEquals(new BigDecimal("-400.000000"), service.allocation(order).remainingTargetQty());
                assertEquals(new BigDecimal("400.000000"), service.allocation(order).excessTargetQty());
                assertEquals(batchesBefore, jdbc.queryForList("SELECT * FROM production_batch ORDER BY id"));
                assertEquals(pathsBefore, jdbc.queryForList("SELECT * FROM batch_route_node ORDER BY id"));
                status.setRollbackOnly();
                return null;
            });
        } finally {
            FactoryContext.clear();
            SecurityContextHolder.clearContext();
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE code=?", Integer.class, tag));
    }
}
