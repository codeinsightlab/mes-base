package dev.issuefixture;

import java.math.BigDecimal;
import dev.mesbase.business.production.controller.RoutingController;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.service.impl.RoutingServiceImpl;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.sync.RoutingQueryPort;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static dev.issuefixture.ProductionReportHttpTest.full;

/**
 * Real XML, Spring transactions and HTTP on two routed H2 databases; center labels are mocks.
 */
class ProductionQualityLifecycleHttpTest {
    static final String E = "/api/production/mes-production-execution", Q = "/api/quality/defects";
    static final String PATH = "/api/production/executions/1/actions/select-path";
    static final String SELECT = "{\"revision\":1,\"routingNodeIds\":[11,14,13]}";
    static final String UPDATE = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"10\",\"status\":\"READY\",\"remark\":\"非身份说明\"}";

    @Configuration
    @Import(RoutingController.class)
    static class RoutingTestConfig {
        @Bean
        IRoutingService lifecycleRouting(ApplicationContext c) {
            return new RoutingServiceImpl(c.getBean(RoutingMapper.class), c.getBean(RoutingNodeMapper.class), c.getBean(RoutingEdgeMapper.class), c.getBean(RoutingProductMapper.class), c.getBean(OperationMapper.class), c.getBean(MaterialMapper.class), c.getBean(RoutingReferenceRules.class), c.getBeanProvider(RoutingQueryPort.class), mock(SyncCoordinator.class), c.getBeanProvider(IRoutingService.class), Validation.buildDefaultValidatorFactory()
                    .getValidator(), mock(BusinessCacheProcessor.class), c.getBean(BusinessDirectoryService.class));
        }
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, ProductionDefectHttpTest.Extra.class, ProductionReportHttpTest.Extra.class, RoutingTestConfig.class);
        c.refresh();
        when(c.getBean(BusinessDirectoryService.class).historicalUserExists(any())).thenReturn(true);
        for (var jdbc : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(jdbc);
            jdbc.update("UPDATE mes_execution_path SET path_status='SELECTED',locked_at=NULL");
            jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) VALUES(14,1,'ALT',1,'O1','未选分支',0,false)");
            jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,11,14),(1,14,13)");
        }
        return c;
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String create(MockMvc m, long execution) throws Exception {
        var j = m.getDispatcherServlet().getWebApplicationContext()
                .getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
        seedReports(j, execution);
        return createQuality(m, execution);
    }

    static void seedReports(JdbcTemplate j, long execution) {
        ProductionInputTestData.confirmed(j, execution, new BigDecimal("10"));
        for (long node : List.of(11L, 12L, 13L))
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',4,100,1,CURRENT_TIMESTAMP,'TEST ONLY prerequisite gross report',CURRENT_TIMESTAMP)", execution, node);
    }

    static String createQuality(MockMvc m, long execution) throws Exception {
        return ProductionDefectHttpTest.add(m, ProductionDefectHttpTest.command("quality-" + execution, 0, "2")
                .replace("executionId\":\"1", "executionId\":\"" + execution), "A");
    }

    @Test
    void noHistoryDeleteStillUsesOriginalRulesAndRemovesSelectedPath() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(full(post(PATH), "A").content(SELECT)).andExpect(status().isOk());
            m.perform(full(delete(E), "A").content("[1]")).andExpect(status().isNoContent());
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM mes_execution_path_node WHERE execution_id=1", Integer.class));
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE id=1", Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "REVISE", "VOID"})
    void everyHistoryIncludingZeroEffectiveVoidedDeclarationProtectsIdentity(String action) throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = create(m, 1);
            if (action.equals("REVISE"))
                m.perform(full(post(Q + "/" + id + "/revisions"), "A").content(ProductionDefectHttpTest.command("revise", 1, "4")))
                        .andExpect(status().isOk());
            if (action.equals("VOID")) {
                m.perform(full(post(Q + "/" + id + "/void"), "A").content("{\"requestKey\":\"void\",\"expectedRevision\":1,\"revisionReason\":\"取消申报\"}"))
                        .andExpect(status().isOk());
                m.perform(full(get(Q + "/executions/1/totals"), "A")).andExpect(jsonPath("$").isEmpty());
            }
            var facts = db(c).queryForList("SELECT * FROM mes_production_defect ORDER BY id");
            var nodes = db(c).queryForList("SELECT * FROM mes_execution_path_node ORDER BY id");
            m.perform(full(delete(E), "A").content("[1]")).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            m.perform(full(post(PATH), "A").content(SELECT)).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("EXECUTION_QUALITY_REFERENCED"));
            m.perform(full(put(E + "/1"), "A").content(UPDATE.replace("routingId\":\"1", "routingId\":\"2")))
                    .andExpect(status().isConflict());
            m.perform(full(put(E + "/1"), "A").content(UPDATE.replace("workorderId\":\"1", "workorderId\":\"2")))
                    .andExpect(status().isConflict());
            // Remark and positive target changes remain governed solely by pre-existing rules.
            m.perform(full(put(E + "/1"), "A").content(UPDATE)).andExpect(status().isOk())
                    .andExpect(jsonPath("remark").value("非身份说明"));
            assertEquals(facts, db(c).queryForList("SELECT * FROM mes_production_defect ORDER BY id"));
            assertEquals(nodes, db(c).queryForList("SELECT * FROM mes_execution_path_node ORDER BY id"));
        }
    }

    @Test
    void batchDeletionRollsBackEarlierCleanupAndFactoriesDoNotShareHistory() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            create(m, 2);
            var nodes = db(c).queryForList("SELECT * FROM mes_execution_path_node ORDER BY id");
            m.perform(full(delete(E), "A").content("[1,2]")).andExpect(status().isConflict());
            assertEquals(nodes, db(c).queryForList("SELECT * FROM mes_execution_path_node ORDER BY id"));
            assertEquals(2, db(c).queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            m.perform(full(delete(E), "B").content("[2]")).andExpect(status().isNoContent());
            assertEquals(1, db(c).queryForObject("SELECT COUNT(*) FROM mes_production_defect WHERE execution_id=2", Integer.class));
        }
    }

    @Test
    void existingWorkorderAndPublishedRoutingGuardsProtectReferencedNodes() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            create(m, 1);
            db(c).update("UPDATE workorder SET status='DRAFT' WHERE id=1");
            m.perform(full(delete("/api/production/workorder/1"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            m.perform(full(delete("/api/production/routing"), "A").content("[1]")).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("ROUTING_FROZEN"));
            m.perform(full(put("/api/production/routing/1/content"), "A").content("{\"revision\":0,\"materialIds\":[1],\"nodes\":[],\"edges\":[]}"))
                    .andExpect(status().isConflict());
            assertEquals(4, db(c).queryForObject("SELECT COUNT(*) FROM routing_node WHERE routing_id=1", Integer.class));
            assertEquals(1, db(c).queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
        }
    }

    @Test
    void queryFailureFailsClosedForQuantityWritesButOrdinaryUpdatesRemainIndependent() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            create(m, 1);
            var facts = db(c).queryForList("SELECT * FROM mes_production_defect ORDER BY id");
            db(c).execute("ALTER TABLE mes_production_defect RENAME TO unavailable_quality_fixture");
            m.perform(full(delete(E), "A").content("[1]")).andExpect(status().isConflict());
            m.perform(full(post(PATH), "A").content(SELECT)).andExpect(status().isInternalServerError());
            m.perform(full(put(E + "/1"), "A").content(UPDATE)).andExpect(status().isOk());
            db(c).update("UPDATE mes_execution_path SET path_status='LOCKED' WHERE id=1");
            m.perform(full(post(PATH), "A").content(SELECT)).andExpect(status().isConflict());
            m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(11, "1")))
                    .andExpect(status().isInternalServerError());
            db(c).execute("ALTER TABLE unavailable_quality_fixture RENAME TO mes_production_defect");
            assertEquals(facts, db(c).queryForList("SELECT * FROM mes_production_defect ORDER BY id"));
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(jsonPath("nodes[0].qualityIntegrated").value(true))
                    .andExpect(jsonPath("nodes[0].defectQty").value("0"));
        }
    }

    @Test
    void qualityHistoryDoesNotBlockMaterialConfirmationStartOrProductionReports() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            create(m, 1);
            ProductionCoreV1HttpTest.issue(m, 1);
            m.perform(full(post(MaterialIssueHttpTest.H + "/1/actions/submit"), "A")).andExpect(status().isOk());
            m.perform(full(post("/api/production/executions/1/actions/start"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("RUNNING"));
            m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(11, "2")))
                    .andExpect(status().isOk());
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(jsonPath("nodes[0].reportedQty").value("6.000000"))
                    .andExpect(jsonPath("nodes[0].qualityIntegrated").value(true));
            // No complete/void command exists: keep the existing refusal of forged status changes.
            m.perform(full(put(E + "/1"), "A").content(UPDATE.replace("READY", "FULLY_RECEIVED")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void concurrentDeleteAndCreateCannotBothCommit() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            seedReports(db(c), 1);
            var barrier = new CyclicBarrier(2);
            Callable<Integer> remove = () -> {
                barrier.await();
                return m.perform(full(delete(E), "A").content("[1]")).andReturn().getResponse().getStatus();
            };
            Callable<Integer> create = () -> {
                barrier.await();
                return m.perform(full(post(Q), "A").content(ProductionDefectHttpTest.command("race", 0, "2")))
                        .andReturn().getResponse().getStatus();
            };
            var a = pool.submit(remove);
            var b = pool.submit(create);
            var left = a.get();
            var right = b.get();
            assertTrue(left == 204 && right != 200 || left != 204 && right == 200);
            assertEquals(0, db(c).queryForObject("SELECT COUNT(*) FROM mes_production_defect d LEFT JOIN mes_production_execution e ON e.id=d.execution_id WHERE e.id IS NULL", Integer.class));
        }
    }
}
