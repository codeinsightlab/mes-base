package dev.mesbase.server;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.domain.OperationUpdateCommand;
import dev.mesbase.business.production.domain.RoutingUpdateCommand;
import dev.mesbase.business.production.domain.RoutingCreateCommand;
import dev.mesbase.business.production.domain.RoutingCopyCommand;
import dev.mesbase.business.production.domain.RoutingContentCommand;
import dev.mesbase.business.production.domain.RoutingNodeCommand;
import dev.mesbase.business.production.domain.RoutingEdgeCommand;
import dev.mesbase.business.production.sync.ExternalRoutingNode;
import dev.mesbase.business.production.sync.ExternalRoutingEdge;
import dev.mesbase.business.production.service.IOperationService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.impl.OperationServiceImpl;
import dev.mesbase.business.production.service.impl.RoutingServiceImpl;
import dev.mesbase.business.production.sync.ExternalOperation;
import dev.mesbase.business.production.sync.ExternalRouting;
import dev.mesbase.business.production.sync.ExternalRoutingStep;
import dev.mesbase.business.production.sync.OperationQueryPort;
import dev.mesbase.business.production.sync.RoutingQueryPort;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.sync.MasterSyncMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import org.springframework.dao.DuplicateKeyException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import java.util.UUID;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * TEST ONLY: configured SIMPLE_B MySQL + Spring transactions; H2 coordinator and synthetic ERP port, no user server started.
 */
class OperationRoutingMySqlTest {
    static final String FACTORY = "SIMPLE_B";
    static final Set<Class<?>> MAPPERS = Set.of(OperationMapper.class, RoutingMapper.class,
            RoutingNodeMapper.class, RoutingEdgeMapper.class, RoutingProductMapper.class, MaterialMapper.class);

    /**
     * TEST ONLY dialect translation; the real center MySQL mapper is unchanged.
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class H2SyncDialect implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            var statement = (StatementHandler) invocation.getTarget();
            var bound = statement.getBoundSql();
            String sql = bound.getSql();
            if (sql.contains("DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND)")) {
                SystemMetaObject.forObject(bound).setValue("sql", sql.replace(
                        "DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND)",
                        "DATEADD('SECOND', -?, CURRENT_TIMESTAMP(6))"));
            }
            return invocation.proceed();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @Import({OperationServiceImpl.class, RoutingServiceImpl.class, RoutingReferenceRules.class})
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
        OperationMapper operations(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(OperationMapper.class);
        }

        @Bean
        RoutingMapper routes(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingMapper.class);
        }

        @Bean
        RoutingNodeMapper nodes(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingNodeMapper.class);
        }

        @Bean
        RoutingEdgeMapper edges(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingEdgeMapper.class);
        }

        @Bean
        RoutingProductMapper products(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingProductMapper.class);
        }

        @Bean
        MaterialMapper materials(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(MaterialMapper.class);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        BusinessCacheProcessor caches() {
            return mock(BusinessCacheProcessor.class);
        }
    }

    @Test
    void syntheticErpUsesRealMySqlUpsertAndCannotRewritePublishedInstructions() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mes.test.operation.mysql"), "Requires the configured SIMPLE_B MySQL acceptance binding");
        var business = AcceptanceBusinessDatabase.source();
        var center = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(business);
        var centerJdbc = new JdbcTemplate(center);
        var id = new FactoryId(FACTORY);
        var scope = new FactoryScope(id);
        var factory = new Factory(id, "TEST_ONLY", FACTORY, "TEST ONLY", FactoryStatus.ENABLED);
        var catalog = new InMemoryFactoryCatalog(List.of(factory));
        var identity = new DatasourceIdentity("TEST_ONLY_OPERATION_ROUTING_MYSQL");
        var binding = new BusinessRuntimeBinding(id, identity, 1);
        var platform = new PlatformPersistence(center, Set.of(MasterSyncMapper.class),
                configuration -> configuration.addInterceptor(new H2SyncDialect()));
        // Coordinator is an explicit H2 fixture; no real center factory or sync rows are modified.
        centerJdbc.execute("CREATE TABLE mes_master_sync(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "factory_id VARCHAR(64),sync_key VARCHAR(96),status VARCHAR(24) DEFAULT 'IDLE',"
                + "run_token VARCHAR(40),started_at DATETIME(6),updated_at DATETIME(6),finished_at DATETIME(6),"
                + "last_success_at DATETIME(6),cursor_time DATETIME(6),processed_count BIGINT DEFAULT 0,"
                + "error_message VARCHAR(1000),UNIQUE(factory_id,sync_key))");
        for (String table : List.of("operation", "routing", "material", "unit_measure")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE code LIKE 'TEST-OR-MYSQL-%'", Long.class) != 0) {
                throw new IllegalStateException("Acceptance sample code conflict; existing rows will not be overwritten");
            }
        }
        final String[] stage = {"first"};
        final int[] queries = {0, 0};
        try (var registry = new RuntimeRegistry(); var context = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> {
            }, MAPPERS);
            var source = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(id, binding)), registry);
            context.getEnvironment().getPropertySources().addFirst(new MockEnvironment()
                    .withProperty("mes.business.enabled", "true").getPropertySources().get("mockProperties"));
            context.registerBean(FactoryBusinessDataSource.class, () -> source);
            context.registerBean(SyncCoordinator.class, () -> new SyncCoordinator(platform, catalog));
            context.registerBean(OperationQueryPort.class, () -> (selected, maximum) -> {
                queries[0]++;
                if ("lost".equals(stage[0])) {
                    centerJdbc.update("UPDATE mes_master_sync SET run_token=? WHERE factory_id=? AND sync_key='operation.erp' AND status='RUNNING'",
                            "TEST_ONLY_REPLACEMENT_RUN", FACTORY);
                }
                return List.of(
                        new ExternalOperation("TEST-EXT-1", "TEST-OR-MYSQL-OP1", "TEST ONLY 工序", "原工艺要求", "ENABLED"),
                        new ExternalOperation("TEST-EXT-2", "TEST-OR-MYSQL-OP2", "TEST ONLY 后序", null, "ENABLED"));
            });
            context.registerBean(RoutingQueryPort.class, () -> (selected, maximum) -> {
                queries[1]++;
                if ("graph".equals(stage[0])) return List.of(new ExternalRouting(
                        "TEST-EXT-ROUTE", "TEST-OR-MYSQL-ROUTE", "TEST ONLY graph", null, "graph",
                        List.of("TEST-OR-MYSQL-PRODUCT"), null,
                        List.of("N10", "N20", "N30A", "N30B", "N40").stream().map(code ->
                                new ExternalRoutingNode(code, "TEST-OR-MYSQL-OP1", false, null, 0)).toList(),
                        mergeEdges().stream()
                                .map(edge -> new ExternalRoutingEdge(edge.fromNodeCode(), edge.toNodeCode()))
                                .toList()));
                return List.of(new ExternalRouting(
                        "TEST-EXT-ROUTE", "TEST-OR-MYSQL-ROUTE", "TEST ONLY " + stage[0], null, stage[0],
                        List.of("TEST-OR-MYSQL-PRODUCT"), List.of(new ExternalRoutingStep(1, "TEST-OR-MYSQL-OP1", true, null),
                        new ExternalRoutingStep(2, "TEST-OR-MYSQL-OP2", false, null))));
            });
            context.register(Config.class);
            context.refresh();
            FactoryContext.bind(scope);
            jdbc.update("INSERT INTO unit_measure(code,name,status) VALUES(?,?,'ENABLED')", "TEST-OR-MYSQL-UOM", "TEST ONLY");
            Long unit = jdbc.queryForObject("SELECT id FROM unit_measure WHERE code=?", Long.class, "TEST-OR-MYSQL-UOM");
            jdbc.update("INSERT INTO material(code,name,unit_id,material_type,status) VALUES(?,?,?,'PRODUCT','ENABLED')", "TEST-OR-MYSQL-PRODUCT", "TEST ONLY", unit);
            var operations = context.getBean(IOperationService.class);
            var routes = context.getBean(IRoutingService.class);
            var coordinator = context.getBean(SyncCoordinator.class);
            var operationRun = coordinator.claim(scope, "operation.erp", Duration.ofMinutes(5));
            assertEquals(ErrorCode.SYNC_RUNNING, assertThrows(FoundationException.class,
                    () -> operations.sync(scope)).code());
            assertEquals(0, queries[0]);
            coordinator.fail(operationRun);
            assertEquals(2, operations.sync(scope).created());
            assertEquals(2, operations.sync(scope).skipped());
            Long operationId = jdbc.queryForObject("SELECT id FROM operation WHERE code=?", Long.class, "TEST-OR-MYSQL-OP1");
            Long materialId = jdbc.queryForObject("SELECT id FROM material WHERE code=?", Long.class, "TEST-OR-MYSQL-PRODUCT");
            verifyGraphs(routes, jdbc, operationId, materialId);
            var routingRun = coordinator.claim(scope, "routing.erp", Duration.ofMinutes(5));
            assertEquals(ErrorCode.SYNC_RUNNING, assertThrows(FoundationException.class,
                    () -> routes.sync(scope)).code());
            assertEquals(0, queries[1]);
            coordinator.fail(routingRun);
            assertEquals(1, routes.sync(scope).created());
            assertEquals(1, routes.sync(scope).skipped());
            Long routeId = jdbc.queryForObject("SELECT id FROM routing WHERE code=? AND version=1", Long.class, "TEST-OR-MYSQL-ROUTE");
            var published = routes.publish(routeId, 0);
            assertEquals("PUBLISHED", published.getStatus());
            var frozen = routes.detail(routeId);
            assertEquals("原工艺要求", frozen.nodes().getFirst().getOperationDescription());
            var op = operations.selectById(frozen.nodes().getFirst().getOperationId());
            operations.updateById(op.getId(), new OperationUpdateCommand(op.getCode(), "TEST ONLY 改名", "新工艺要求", "ENABLED", 0, op.getExternalId(), op.getRevision()));
            assertEquals("原工艺要求", routes.detail(routeId).nodes().getFirst().getOperationDescription());
            assertEquals("TEST ONLY 工序", routes.detail(routeId).nodes().getFirst().getOperationName());
            assertEquals(ErrorCode.ROUTING_FROZEN, assertThrows(FoundationException.class,
                    () -> routes.updateById(routeId, new RoutingUpdateCommand("绕过", null, published.getRevision()))).code());
            assertEquals(ErrorCode.OPERATION_REFERENCED, assertThrows(FoundationException.class,
                    () -> operations.deleteByIds(List.of(op.getId()))).code());
            stage[0] = "changed";
            assertEquals(1, routes.sync(scope).created());
            assertEquals(1, routes.sync(scope).skipped());
            assertEquals("PUBLISHED", routes.detail(routeId).routing().getStatus());
            assertEquals("TEST ONLY first", routes.detail(routeId).routing().getName());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE code='TEST-OR-MYSQL-ROUTE' AND version=2 AND status='DRAFT'", Integer.class));
            assertEquals("SUCCEEDED", centerJdbc.queryForObject("SELECT status FROM mes_master_sync WHERE factory_id=? AND sync_key='routing.erp'", String.class, FACTORY));
            Long versionTwo = jdbc.queryForObject("SELECT id FROM routing WHERE code='TEST-OR-MYSQL-ROUTE' AND version=2", Long.class);
            routes.publish(versionTwo, routes.detail(versionTwo).routing().getRevision());
            stage[0] = "graph";
            assertEquals(1, routes.sync(scope).created());
            assertEquals(1, routes.sync(scope).skipped());
            Long versionThree = jdbc.queryForObject("SELECT id FROM routing WHERE code='TEST-OR-MYSQL-ROUTE' AND version=3", Long.class);
            assertEquals(5, routes.detail(versionThree).nodes().size());
            assertEquals(5, routes.detail(versionThree).edges().size());
            assertEquals(2, routes.detail(versionTwo).nodes().size());
            stage[0] = "lost";
            assertEquals(ErrorCode.SYNC_EXECUTION_LOST, assertThrows(FoundationException.class,
                    () -> operations.sync(scope)).code());
            assertEquals("TEST ONLY 改名", operations.selectById(op.getId()).getName());
            assertEquals("TEST_ONLY_REPLACEMENT_RUN", centerJdbc.queryForObject(
                    "SELECT run_token FROM mes_master_sync WHERE factory_id=? AND sync_key='operation.erp'", String.class, FACTORY));
        } finally {
            FactoryContext.clear();
            // Preflight proved these exact test prefixes absent; cleanup never targets user rows.
            jdbc.update("DELETE e FROM routing_edge e JOIN routing r ON r.id=e.routing_id WHERE r.code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE s FROM routing_node s JOIN routing r ON r.id=s.routing_id WHERE r.code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE p FROM routing_product p JOIN routing r ON r.id=p.routing_id WHERE r.code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE FROM routing WHERE code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE FROM operation WHERE code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE FROM material WHERE code LIKE 'TEST-OR-MYSQL-%'");
            jdbc.update("DELETE FROM unit_measure WHERE code LIKE 'TEST-OR-MYSQL-%'");
        }
    }

    static List<RoutingEdgeCommand> mergeEdges() {
        return List.of(new RoutingEdgeCommand("N10", "N20"), new RoutingEdgeCommand("N20", "N30A"),
                new RoutingEdgeCommand("N20", "N30B"), new RoutingEdgeCommand("N30A", "N40"),
                new RoutingEdgeCommand("N30B", "N40"));
    }

    static void verifyGraphs(IRoutingService routes, JdbcTemplate jdbc, Long operation,
                             Long material) throws Exception {
        var nodes = List.of("N10", "N20", "N30A", "N30B", "N40").stream()
                .map(code -> new RoutingNodeCommand(code, operation, false, null, 0)).toList();
        var cases = List.of(
                new RoutingContentCommand(0, List.of(material), List.of(nodes.get(0), nodes.get(1), nodes.get(2), nodes.get(4)),
                        List.of(new RoutingEdgeCommand("N10", "N20"), new RoutingEdgeCommand("N20", "N30A"), new RoutingEdgeCommand("N30A", "N40"))),
                new RoutingContentCommand(0, List.of(material), nodes.subList(0, 4), mergeEdges().subList(0, 3)),
                new RoutingContentCommand(0, List.of(material), nodes, mergeEdges()));
        var names = List.of("LINEAR", "BRANCH", "MERGE");
        for (int i = 0; i < cases.size(); i++) {
            var route = routes.insert(new RoutingCreateCommand("TEST-OR-MYSQL-" + names.get(i), "TEST ONLY " + names.get(i), null));
            var saved = routes.saveContent(route.getId(), cases.get(i));
            assertEquals(cases.get(i).nodes().size(), saved.nodes().size());
            assertEquals(cases.get(i).edges().size(), saved.edges().size());
            var published = routes.publish(route.getId(), saved.routing().getRevision());
            assertEquals(ErrorCode.ROUTING_FROZEN, assertThrows(FoundationException.class,
                    () -> routes.saveContent(route.getId(), new RoutingContentCommand(published.getRevision(), List.of(material), nodes, mergeEdges()))).code());
            var copy = routes.copy(route.getId(), new RoutingCopyCommand(published.getRevision(), "TEST-OR-MYSQL-" + names.get(i) + "-COPY", "TEST ONLY COPY"));
            assertEquals(cases.get(i).edges().size(), routes.detail(copy.getId()).edges().size());
            var next = routes.newVersion(route.getId(), routes.detail(route.getId()).routing().getRevision());
            assertEquals(cases.get(i).nodes().size(), routes.detail(next.getId()).nodes().size());
            assertEquals(cases.get(i).edges().size(), routes.detail(next.getId()).edges().size());
        }
        var draft = routes.insert(new RoutingCreateCommand("TEST-OR-MYSQL-INVALID", "TEST ONLY INVALID", null));
        for (var edges : List.of(List.of(new RoutingEdgeCommand("N10", "N10")),
                List.of(new RoutingEdgeCommand("N10", "N20"), new RoutingEdgeCommand("N20", "N10")),
                List.of(new RoutingEdgeCommand("N10", "N20"), new RoutingEdgeCommand("N10", "N20")),
                List.of(new RoutingEdgeCommand("N10", "foreign")))) {
            assertEquals(ErrorCode.ROUTING_GRAPH_INVALID, assertThrows(FoundationException.class,
                    () -> routes.saveContent(draft.getId(), new RoutingContentCommand(0, List.of(material), nodes, edges))).code());
            assertEquals(0, routes.detail(draft.getId()).routing().getRevision());
            assertEquals(0, routes.detail(draft.getId()).nodes().size());
        }
        var content = new RoutingContentCommand(0, List.of(material), nodes, mergeEdges());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++)
                results.add(workers.submit(() -> {
                    FactoryContext.bind(new FactoryScope(new FactoryId(FACTORY)));
                    try {
                        start.await();
                        routes.saveContent(draft.getId(), content);
                        return 200;
                    } catch (FoundationException failure) {
                        assertEquals(ErrorCode.ROUTING_REVISION_CONFLICT, failure.code());
                        return 409;
                    } finally {
                        FactoryContext.clear();
                    }
                }));
            start.countDown();
            assertEquals(List.of(200, 409), List.of(results.get(0).get(), results.get(1).get()).stream().sorted()
                    .toList());
        }
        var saved = routes.detail(draft.getId());
        assertEquals(1, saved.routing().getRevision());
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO routing_node(routing_id,node_code,operation_id) SELECT routing_id,node_code,operation_id FROM routing_node WHERE routing_id=? LIMIT 1", draft.getId()));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) SELECT routing_id,from_node_id,to_node_id FROM routing_edge WHERE routing_id=? LIMIT 1", draft.getId()));
        var foreign = routes.insert(new RoutingCreateCommand("TEST-OR-MYSQL-FOREIGN", "TEST ONLY FOREIGN", null));
        routes.saveContent(foreign.getId(), new RoutingContentCommand(0, List.of(material), nodes.subList(0, 1), List.of()));
        Long foreignNode = routes.detail(foreign.getId()).nodes().getFirst().getId();
        jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(?,?,?)", draft.getId(), saved.nodes()
                .getFirst().getId(), foreignNode);
        assertEquals(ErrorCode.ROUTING_GRAPH_INVALID, assertThrows(FoundationException.class,
                () -> routes.publish(draft.getId(), 1)).code());
        jdbc.update("DELETE FROM routing_edge WHERE routing_id=? AND to_node_id=?", draft.getId(), foreignNode);
        var isolated = routes.insert(new RoutingCreateCommand("TEST-OR-MYSQL-ISOLATED", "TEST ONLY ISOLATED", null));
        routes.saveContent(isolated.getId(), new RoutingContentCommand(0, List.of(material), nodes, List.of()));
        assertEquals(ErrorCode.ROUTING_GRAPH_INVALID, assertThrows(FoundationException.class, () -> routes.publish(isolated.getId(), 1)).code());
    }
}
