package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueService;
import dev.mesbase.business.issue.service.IMesMaterialIssueItemService;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LIVE MYSQL: designated SIMPLE_B, isolated tags retained for user recheck; no external ERP effects.
 */
@EnabledIfEnvironmentVariable(named = "MES_CORE_MYSQL_ACCEPTANCE", matches = "true")
class ProductionCoreV1MySqlTest {
    @Test
    void configuredFactoryFullBusinessFlowAndRealSchema() throws Exception {
        var business = AcceptanceBusinessDatabase.source();
        var jdbc = new JdbcTemplate(business);
        var match = jdbc.queryForMap("SELECT r.id AS routing_id,p.material_id FROM routing r JOIN routing_product p ON p.routing_id=r.id JOIN material m ON m.id=p.material_id WHERE r.status='PUBLISHED' AND m.material_type='PRODUCT' AND m.status='ENABLED' AND EXISTS(SELECT 1 FROM routing_node n WHERE n.routing_id=r.id) ORDER BY r.id LIMIT 1");
        long routing = ((Number) match.get("routing_id")).longValue(), product = ((Number) match.get("material_id")).longValue();
        var nodes = jdbc.queryForList("SELECT id,is_key FROM routing_node WHERE routing_id=?", routing);
        var edges = jdbc.queryForList("SELECT from_node_id,to_node_id FROM routing_edge WHERE routing_id=?", routing);
        // Bounded DFS over the published immutable graph, choosing one root-to-sink path including any key node.
        var selected = new ArrayList<Long>();
        for (var n : nodes) {
            long id = ((Number) n.get("id")).longValue();
            if (edges.stream()
                    .noneMatch(e -> ((Number) e.get("to_node_id")).longValue() == id) && choose(id, nodes, edges, selected))
                break;
        }
        assertEquals(false, selected.isEmpty());
        String tag = "TEST_CORE_V1_" + UUID.randomUUID().toString().substring(0, 8);
        var id = new FactoryId("SIMPLE_B");
        var identity = new DatasourceIdentity("TEST_ONLY_CORE_MYSQL");
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(id, "TEST_ONLY", "SIMPLE_B", "SIMPLE_B", FactoryStatus.ENABLED)));
        try (var registry = new RuntimeRegistry(); var c = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> {
            }, MaterialIssueHttpTest.MAPPERS);
            c.registerBean(FactoryBusinessDataSource.class, () -> new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(id, new BusinessRuntimeBinding(id, identity, 1))), registry));
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
            for (var mapper : MaterialIssueHttpTest.MAPPERS) MaterialIssueVoidMySqlTest.mapper(c, mapper);
            c.register(MaterialIssueVoidMySqlTest.Config.class);
            c.refresh();
            MaterialIssueVoidMySqlTest.scoped(() -> {
                var orders = c.getBean(WorkOrderService.class);
                var executions = c.getBean(IMesProductionExecutionService.class);
                var paths = c.getBean(ExecutionPathService.class);
                var issues = c.getBean(IMesMaterialIssueService.class);
                var items = c.getBean(IMesMaterialIssueItemService.class);
                var submits = c.getBean(MaterialIssueSubmissionService.class);
                var demand = orders.create(new WorkOrderModels.Save(tag, product, BigDecimal.TEN, routing, null, null, null, null, "TEST ONLY Production Core V1", null))
                        .workorder();
                orders.release(demand.getId(), demand.getRevision());
                var e = executions.insert(new MesProductionExecutionCreateCommand(demand.getId(), routing, BigDecimal.TEN, "READY", null, null, tag));
                assertThrows(FoundationException.class, () -> paths.start(e.getId()));
                paths.select(e.getId(), 0, selected);
                var issue = issues.insert(new MesMaterialIssueCreateCommand(tag, demand.getId(), null, null, null, null, null, null, "DRAFT", tag, e.getId()));
                assertThrows(FoundationException.class, () -> submits.submit(issue.getId()));
                var item = items.insert(new MesMaterialIssueItemCreateCommand(issue.getId(), product, "TEST_CORE_LOT", new BigDecimal("1.123456"), null, null, tag));
                var confirmed = submits.submit(issue.getId());
                assertEquals("CONFIRMED", confirmed.getStatus());
                assertEquals("NONE", confirmed.getExternalEffect());
                assertNotNull(confirmed.getSubmissionId());
                assertEquals(confirmed.getSubmissionId(), submits.submit(issue.getId()).getSubmissionId());
                assertEquals("LOCKED", paths.view(e.getId()).path().getPathStatus());
                assertThrows(FoundationException.class, () -> paths.select(e.getId(), 1, selected));
                assertEquals("RUNNING", paths.start(e.getId()).getStatus());
                assertEquals("RUNNING", paths.start(e.getId()).getStatus());
                assertThrows(FoundationException.class, () -> c.getBean(MaterialIssueVoidService.class)
                        .cancel(issue.getId()));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_material_issue' AND (column_name LIKE '%external%' OR column_name='factory_id')", Integer.class));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('mes_execution_path','mes_execution_path_node','mes_material_issue_submission') AND (column_comment='' OR column_name='factory_id')", Integer.class));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_material_issue_erp_extra WHERE id=? AND (external_reference IS NOT NULL OR external_code IS NOT NULL)", Integer.class, issue.getId()));
                System.out.println("MYSQL_CORE_VERIFIED tag=" + tag + " workorder=" + demand.getId() + " execution=" + e.getId() + " issue=" + issue.getId() + " item=" + item.getId() + " path=" + selected + " mode=LOCAL_ONLY");
                return null;
            });
        }
    }

    static boolean choose(long id, List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
                          List<Long> selected) {
        selected.add(id);
        var next = edges.stream().filter(e -> ((Number) e.get("from_node_id")).longValue() == id)
                .map(e -> ((Number) e.get("to_node_id")).longValue()).toList();
        if (next.isEmpty() && nodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.get("is_key")) || n.get("is_key") instanceof Number value && value.intValue() != 0)
                .allMatch(n -> selected.contains(((Number) n.get("id")).longValue()))) return true;
        for (long target : next)
            if (!selected.contains(target) && selected.size() < 100 && choose(target, nodes, edges, selected))
                return true;
        selected.removeLast();
        return false;
    }
}
