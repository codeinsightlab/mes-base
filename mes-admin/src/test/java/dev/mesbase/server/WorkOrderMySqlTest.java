package dev.mesbase.server;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.service.WorkOrderService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * TEST ONLY controlled issue result simulator. Actual configured acceptance MySQL; no HTTP server or ERP.
 */
class WorkOrderMySqlTest {
    static final Set<Class<?>> MAPPERS = Set.of(WorkorderMapper.class, ProductionBatchMapper.class, BatchRouteNodeMapper.class, RoutingMapper.class, RoutingNodeMapper.class, RoutingEdgeMapper.class, RoutingProductMapper.class, OperationMapper.class, WorkOrderExtraMapper.class, MaterialMapper.class, MesMaterialIssueMapper.class);

    @Configuration
    @EnableTransactionManagement
    @Import({WorkOrderService.class, RoutingReferenceRules.class})
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
    }

    @Test
    void realMySqlBusinessAndControlledIssueResults() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mes.test.workorder.mysql"));
        var business = OperationRoutingMySqlTest.source(Path.of(".local/operation-routing-20261013/mysql-b.cnf"), "mes_base_dev_business_simple_b");
        var jdbc = new JdbcTemplate(business);
        var id = new FactoryId("SIMPLE_B");
        var identity = new DatasourceIdentity("TEST_ONLY_WORKORDER_MYSQL");
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(id, "TEST_ONLY", "SIMPLE_B", "验收 B", FactoryStatus.ENABLED)));
        String tag = "TEST-WO-" + UUID.randomUUID().toString().substring(0, 8);
        Long product = jdbc.queryForObject("SELECT id FROM material WHERE material_type='PRODUCT' AND status='ENABLED' ORDER BY id LIMIT 1", Long.class);
        Long op = jdbc.queryForObject("SELECT id FROM operation ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO routing(code,name,version,status,revision,published_at) VALUES(?, 'TEST ONLY 工单分支汇合验收',1,'PUBLISHED',0,NOW())", tag);
        Long routing = jdbc.queryForObject("SELECT id FROM routing WHERE code=?", Long.class, tag);
        jdbc.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) SELECT ?,id,code,name FROM material WHERE id=?", routing, product);
        for (String code : List.of("A", "B", "C", "D", "E"))
            jdbc.update("INSERT INTO routing_node(routing_id,node_code,operation_id,display_order) VALUES(?,?,?,0)", routing, code, op);
        for (var pair : List.of(List.of("A", "B"), List.of("B", "C"), List.of("B", "D"), List.of("C", "E"), List.of("D", "E")))
            jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) SELECT ?,f.id,t.id FROM routing_node f JOIN routing_node t ON t.routing_id=f.routing_id WHERE f.routing_id=? AND f.node_code=? AND t.node_code=?", routing, routing, pair.get(0), pair.get(1));
        var pathA = List.of("A", "B", "C", "E").stream()
                .map(code -> jdbc.queryForObject("SELECT id FROM routing_node WHERE routing_id=? AND node_code=?", Long.class, routing, code))
                .toList();
        var pathB = List.of("A", "B", "D", "E").stream()
                .map(code -> jdbc.queryForObject("SELECT id FROM routing_node WHERE routing_id=? AND node_code=?", Long.class, routing, code))
                .toList();
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
            var input = input(tag, product, routing, 0);
            var created = service.create(input);
            Long order = created.workorder().getId();
            assertEquals(2, created.batches().size());
            assertEquals("19001", created.batches().getFirst().batch().getBatchNo());
            assertThrows(FoundationException.class, () -> service.create(input));
            assertThrows(FoundationException.class, () -> service.update(order, input(tag, product, routing, 99)));
            var updated = service.update(order, input(tag, product, routing, 0));
            assertEquals(1, updated.workorder().getRevision());
            // A surrounding Spring transaction aborts both parent and children after a complete valid create.
            var manager = context.getBean("businessTransactionManager", OwnedTransactionManager.class);
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status -> {
                service.create(input(tag + "-ROLLBACK", product, routing, 0));
                throw new IllegalStateException("TEST ONLY rollback");
            }));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE code=?", Integer.class, tag + "-ROLLBACK"));
            var released = service.release(order, 1);
            assertEquals("RELEASED", released.workorder().getStatus());
            assertThrows(FoundationException.class, () -> service.update(order, input(tag, product, routing, 2)));
            Long batchA = released.batches().get(0).batch().getId(), batchB = released.batches().get(1).batch().getId();
            for (var invalid : List.of(List.of(pathA.get(0), pathA.get(2), pathA.get(3)), List.of(pathA.get(0)), List.of(pathA.get(0), 999999999L, pathA.getLast()), List.of(pathA.get(0), pathA.get(1), pathA.get(1), pathA.getLast())))
                assertThrows(FoundationException.class, () -> service.selectPath(batchA, new WorkOrderModels.PathSelection(1, invalid)));
            service.selectPath(batchA, new WorkOrderModels.PathSelection(1, pathA));
            service.selectPath(batchB, new WorkOrderModels.PathSelection(1, pathB));
            var beginA = service.beginIssue(batchA, 2);
            String commandA = beginA.batch().getIssueCommandId();
            assertThrows(FoundationException.class, () -> service.selectPath(batchA, new WorkOrderModels.PathSelection(2, pathB)));
            service.confirmIssue(batchA, commandA, WorkOrderModels.IssueResult.UNKNOWN);
            assertEquals("UNKNOWN", service.batchDetail(batchA).batch().getRouteStatus());
            assertThrows(FoundationException.class, () -> service.selectPath(batchA, new WorkOrderModels.PathSelection(3, pathB)));
            var beginB = service.beginIssue(batchB, 2);
            String commandB = beginB.batch().getIssueCommandId();
            service.confirmIssue(batchB, commandB, WorkOrderModels.IssueResult.REJECTED);
            service.confirmIssue(batchB, commandB, WorkOrderModels.IssueResult.REJECTED);
            assertEquals("SELECTED", service.batchDetail(batchB).batch().getRouteStatus());
            var reselected = service.selectPath(batchB, new WorkOrderModels.PathSelection(4, pathA));
            var secondB = service.beginIssue(batchB, reselected.batch().getRevision());
            assertThrows(FoundationException.class, () -> service.confirmIssue(batchB, commandB, WorkOrderModels.IssueResult.SUCCESS));
            // Concurrent trusted SUCCESS callbacks are idempotent under the shared parent row lock.
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Integer> callback = () -> {
                    FactoryContext.bind(new FactoryScope(id));
                    try {
                        return service.confirmIssue(batchA, commandA, WorkOrderModels.IssueResult.SUCCESS).batch()
                                .getRevision();
                    } finally {
                        FactoryContext.clear();
                    }
                };
                var results = pool.invokeAll(List.of(callback, callback));
                assertEquals(results.get(0).get(), results.get(1).get());
            }
            assertEquals("LOCKED", service.batchDetail(batchA).batch().getRouteStatus());
            assertEquals("PENDING", service.batchDetail(batchB).batch().getRouteStatus());
            service.confirmIssue(batchB, secondB.batch().getIssueCommandId(), WorkOrderModels.IssueResult.SUCCESS);
            assertThrows(FoundationException.class, () -> service.selectPath(batchA, new WorkOrderModels.PathSelection(4, pathB)));
            assertThrows(FoundationException.class, () -> service.confirmIssue(batchA, commandA, WorkOrderModels.IssueResult.REJECTED));
            assertEquals(pathA, service.batchDetail(batchA).path().stream().map(p -> p.getRoutingNodeId()).toList());
            assertEquals(pathA, service.batchDetail(batchB).path().stream().map(p -> p.getRoutingNodeId()).toList());
            Files.createDirectories(Path.of("target/validation"));
            Files.writeString(Path.of("target/validation/workorder-mysql-result.json"), "{\"passed\":true,\"schema\":\"mes_base_dev_business_simple_b\",\"fixtureTag\":\"" + tag + "\",\"workorderId\":" + order + ",\"routingId\":" + routing + ",\"issueEvidence\":\"TEST_ONLY_SIMULATED_RESULTS\",\"realERP\":false}");
        } finally {
            FactoryContext.clear();
        }
    }

    private static WorkOrderModels.Save input(String code, Long product, Long routing, int revision) {
        return new WorkOrderModels.Save(code, product, new BigDecimal("10"), routing, null, null, null, null, "TEST ONLY", revision,
                List.of(new WorkOrderModels.BatchInput(null, "19001", new BigDecimal("4"), null), new WorkOrderModels.BatchInput(null, "19002", new BigDecimal("6"), null)));
    }
}
