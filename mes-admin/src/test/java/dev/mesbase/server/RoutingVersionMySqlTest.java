package dev.mesbase.server;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.domain.RoutingCopyCommand;
import dev.mesbase.business.production.domain.RoutingContentCommand;
import dev.mesbase.business.production.domain.RoutingNodeCommand;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryContext;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** TEST ONLY: real SIMPLE_B generated SQL, all synthetic copies rolled back; never changes route 122. */
class RoutingVersionMySqlTest {
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class Statements implements Interceptor {
        final List<String> sql = new ArrayList<>();
        boolean failEdgeInsert;
        int injectedFailures;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql()
                    .replaceAll("\\s+", " ").strip().toLowerCase());
            if (failEdgeInsert && sql.getLast().startsWith("insert into routing_edge")) {
                injectedFailures++;
                throw new IllegalStateException("TEST ONLY injected edge write failure");
            }
            return invocation.proceed();
        }
    }

    @Test
    void newVersionPreservesGraphWithFiveFewerStatementsAndOneEviction() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mes.test.routing.version.mysql"), "Requires configured SIMPLE_B");
        var business = AcceptanceBusinessDatabase.source();
        var jdbc = new JdbcTemplate(business);
        var id = new FactoryId("SIMPLE_B");
        var identity = new DatasourceIdentity("TEST_ONLY_ROUTING_VERSION");
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(id, "TEST_ONLY", "SIMPLE_B",
                "TEST ONLY", FactoryStatus.ENABLED)));
        String code = "TEST-RV-" + UUID.randomUUID();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE code=?", Integer.class, code));
        try (var registry = new RuntimeRegistry(); var context = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> { }, OperationRoutingMySqlTest.MAPPERS);
            var source = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog,
                    Map.of(id, new BusinessRuntimeBinding(id, identity, 1))), registry);
            context.getEnvironment().getPropertySources().addFirst(new MockEnvironment()
                    .withProperty("mes.business.enabled", "true").getPropertySources().get("mockProperties"));
            context.registerBean(FactoryBusinessDataSource.class, () -> source);
            context.registerBean(SyncCoordinator.class, () -> mock(SyncCoordinator.class));
            context.register(OperationRoutingMySqlTest.Config.class);
            context.refresh();
            var statements = new Statements();
            context.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(statements);
            var routes = context.getBean(IRoutingService.class);
            var caches = context.getBean(BusinessCacheProcessor.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            FactoryContext.bind(new FactoryScope(id));
            try {
                var original = routes.detail(122L);
                var originalGraph = graph(original);
                transaction.executeWithoutResult(status -> {
                    // New rows use a unique TEST code; existing reference rows receive only same-value lock updates.
                    var copy = routes.copy(122L, new RoutingCopyCommand(original.routing().getRevision(), code, "TEST ONLY"));
                    var published = routes.publish(copy.getId(), copy.getRevision());
                    var frozen = routes.detail(published.getId());
                    clearInvocations(caches);
                    statements.sql.clear();
                    long started = System.nanoTime();
                    var next = routes.newVersion(published.getId(), published.getRevision());
                    long elapsed = (System.nanoTime() - started) / 1_000_000;
                    int products = frozen.products().size(), nodes = frozen.nodes().size(), edges = frozen.edges().size();
                    long operations = frozen.nodes().stream().map(node -> node.getOperationId()).distinct().count();
                    assertFalse(products == 0 || nodes == 0, "Budget requires a published nonempty graph");
                    long budget = 10L + 2L * products + operations + nodes + edges;
                    assertEquals(budget, statements.sql.size());
                    assertTrue(statements.sql.stream().noneMatch(sql -> sql.startsWith("delete")));
                    assertEquals(3, statements.sql.stream().filter(sql -> sql.startsWith("select")
                            && (sql.contains(" from routing_node ") || sql.contains(" from routing_product ")
                            || sql.contains(" from routing_edge "))).count());
                    verify(caches, times(1)).evict("business:routing", BusinessCacheEvict.Scope.LIST_AND_DETAIL);
                    assertEquals("DRAFT", next.getStatus());
                    assertEquals(2, next.getVersion());
                    assertEquals(0, next.getRevision());
                    assertNull(next.getPublishedAt());
                    var copied = routes.detail(next.getId());
                    assertEquals(graph(frozen), graph(copied));
                    assertNotEquals(frozen.nodes().getFirst().getId(), copied.nodes().getFirst().getId());
                    assertEquals(frozen.products().stream().map(row -> row.getMaterialId()).sorted().toList(),
                            copied.products().stream().map(row -> row.getMaterialId()).sorted().toList());
                    assertEquals(originalGraph, graph(routes.detail(122L)));
                    // Existing drafts must still delete obsolete edges/products/nodes and retain surviving node IDs.
                    var retained = copied.nodes().getFirst();
                    var edited = routes.saveContent(next.getId(), new RoutingContentCommand(next.getRevision(),
                            List.of(copied.products().getFirst().getMaterialId()),
                            List.of(new RoutingNodeCommand(retained.getNodeCode(), retained.getOperationId(),
                                    retained.getIsKey(), retained.getDescription(), retained.getDisplayOrder())), List.of()));
                    assertEquals(1, edited.products().size());
                    assertEquals(1, edited.nodes().size());
                    assertEquals(retained.getId(), edited.nodes().getFirst().getId());
                    assertTrue(edited.edges().isEmpty());
                    System.out.printf("ROUTING_VERSION_MYSQL sql=%d oldBudget=%d serviceMs=%d P=%d N=%d E=%d O=%d%n",
                            budget, budget + 5, elapsed, products, nodes, edges, operations);
                    status.setRollbackOnly();
                });
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE code=?", Integer.class, code));
                assertEquals(originalGraph, graph(routes.detail(122L)));
                assertEquals(original.routing().getRevision(), routes.detail(122L).routing().getRevision());
                assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
                    var copy = routes.copy(122L, new RoutingCopyCommand(original.routing().getRevision(), code, "TEST ONLY rollback"));
                    var published = routes.publish(copy.getId(), copy.getRevision());
                    statements.failEdgeInsert = true;
                    routes.newVersion(published.getId(), published.getRevision());
                }));
                statements.failEdgeInsert = false;
                assertEquals(1, statements.injectedFailures);
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE code=?", Integer.class, code));
                assertEquals(originalGraph, graph(routes.detail(122L)));
            } finally {
                FactoryContext.clear();
            }
        }
    }

    private static List<String> graph(RoutingDetail detail) {
        var codes = new HashMap<Long, String>();
        detail.nodes().forEach(node -> codes.put(node.getId(), node.getNodeCode()));
        var result = new ArrayList<String>();
        detail.nodes().forEach(node -> result.add("N:" + node.getNodeCode() + ":" + node.getOperationId()
                + ":" + node.getIsKey() + ":" + node.getDescription() + ":" + node.getDisplayOrder()));
        detail.edges().forEach(edge -> result.add("E:" + codes.get(edge.getFromNodeId()) + ":" + codes.get(edge.getToNodeId())));
        return result.stream().sorted().toList();
    }
}
