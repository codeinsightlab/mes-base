package dev.issuefixture;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.mesbase.business.quality.controller.ProductionDefectController;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.integration.LocalProductionDefectErpGateway;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Callable;

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
 * TEST ONLY: real SQL / Spring transactions / HTTP authorization / two routed H2 factories.
 */
class ProductionDefectHttpTest {
    static final String ROOT = "/api/quality/defects";

    @Configuration
    @Import({ProductionQuantityService.class, ProductionDefectController.class, ProductionDefectService.class, LocalProductionDefectErpGateway.class})
    static class Extra {

    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        c.refresh();
        when(c.getBean(BusinessDirectoryService.class).historicalUserExists(any())).thenAnswer(x -> Long.valueOf(100)
                .equals(x.getArgument(0)));
        for (var jdbc : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(jdbc);
            ProductionInputTestData.confirmed(jdbc, 1, new BigDecimal("10"));
            ProductionInputTestData.confirmed(jdbc, 2, new BigDecimal("10"));
            seedReports(jdbc, 1);
            seedReports(jdbc, 2);

        }
        return c;
    }

    static void seedReports(JdbcTemplate jdbc, long execution) {
        for (long node : List.of(11L, 12L, 13L))
            jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',10,100,1,CURRENT_TIMESTAMP,'TEST ONLY gross processed fixture',CURRENT_TIMESTAMP)", execution, node);
    }

    static String command(String key, int revision, String quantity) {
        return "{\"requestKey\":\"" + key + "\"," + (revision == 0 ? "" : "\"expectedRevision\":" + revision + ",\"revisionReason\":\"TEST订正\",") + "\"facts\":{\"executionId\":\"1\",\"affectedNodeId\":\"13\",\"discoveredNodeId\":\"12\",\"defectQty\":\"" + quantity + "\",\"wasteCategory\":\"PROCESS\",\"defectReason\":\"TEST ONLY缺陷\",\"destinationType\":\"ROUTING_NODE\",\"occurredAt\":\"2026-01-01T10:00:00\"}}";
    }

    static String add(MockMvc m, String body, String factory) throws Exception {
        var json = m.perform(full(post(ROOT), factory).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("record.revisionNo").value(1)).andExpect(jsonPath("externalExecuted").value(false))
                .andExpect(jsonPath("executionMode").value("LOCAL_ONLY")).andReturn().getResponse()
                .getContentAsString();
        return new ObjectMapper().readTree(json).path("record").path("declarationId").asText();
    }

    @Test
    void completeVersionLifecycleAndAggregation() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = add(m, command("one", 0, "2"), "A");
            m.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("targetNodeId").value("11"))
                    .andExpect(jsonPath("reportedBy").value("1"));
            for (int rev = 1; rev <= 2; rev++)
                m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command("rev" + rev, rev, "4")))
                        .andExpect(status().isOk()).andExpect(jsonPath("record.revisionNo").value(rev + 1));
            m.perform(full(get(ROOT + "/executions/1/totals"), "A"))
                    .andExpect(jsonPath("[0].effectiveDefectQty").value("4.000000"));
            m.perform(full(get(ROOT), "A").param("affectedNodeId", "12")).andExpect(jsonPath("total").value(0));
            m.perform(full(get(ROOT), "A").param("workorderId", "1")).andExpect(jsonPath("total").value(1));
            m.perform(full(post(ROOT + "/" + id + "/void"), "A").content("{\"requestKey\":\"void\",\"expectedRevision\":3,\"revisionReason\":\"TEST作废\"}"))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/executions/1/totals"), "A")).andExpect(jsonPath("$").isEmpty());
            m.perform(full(get(ROOT + "/" + id + "/history"), "A")).andExpect(jsonPath("total").value(4))
                    .andExpect(jsonPath("items[3].defectQty").value("2.000000"));
            m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command("after", 4, "1")))
                    .andExpect(status().isConflict());
            m.perform(full(put(ROOT + "/" + id), "A").content("{}")).andExpect(status().isMethodNotAllowed());
            m.perform(full(delete(ROOT + "/" + id), "A")).andExpect(status().isMethodNotAllowed());
            assertEquals(6, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void idempotencyScopeActorAndCommandIsolation() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var body = command("same", 0, "2");
            var id = add(m, body, "A");
            assertEquals(id, add(m, body.replace("\"2\"", "\"2.00\""), "A"));
            m.perform(full(post(ROOT), "A").content(command("same", 0, "3"))).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("DEFECT_IDEMPOTENCY_CONFLICT"));
            m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command("same", 1, "2")))
                    .andExpect(status().isConflict());
            add(m, body, "B");
            m.perform(full(get(ROOT + "/" + id), "B")).andExpect(status().isNotFound());
            m.perform(full(post(ROOT), "A").content(command("same", 0, "2").replace("executionId\":\"1", "executionId\":\"2")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void actualPathDestinationsAndReferences() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var base = command("check", 0, "1");
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) VALUES(14,1,'ALT',1,'O1','未选分支',0,false)");
            jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,11,14),(1,14,13)");
            m.perform(full(post(ROOT), "A").content(base.replace("discoveredNodeId\":\"12", "discoveredNodeId\":\"14")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(ROOT), "A").content(base.replace("\"destinationType\"", "\"responsibleNodeId\":\"14\",\"destinationType\"")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(ROOT), "A").content(base.replace("\"destinationType\"", "\"targetNodeId\":\"12\",\"destinationType\"")))
                    .andExpect(status().isBadRequest());
            for (String body : List.of(base.replace("check", "downstream")
                    .replace("discoveredNodeId\":\"12", "discoveredNodeId\":\"11"), base.replace("discoveredNodeId\":\"12", "discoveredNodeId\":\"999"), base.replace("\"destinationType\"", "\"targetNodeId\":\"13\",\"destinationType\""), base.replace("\"destinationType\"", "\"responsibleUserId\":\"999\",\"destinationType\""), command("check", 0, "0"), command("check", 0, "0.0000001")))
                m.perform(full(post(ROOT), "A").content(body)).andExpect(status().isBadRequest());
            m.perform(full(post(ROOT), "A").content(base.replace("executionId\":\"1", "executionId\":\"999")))
                    .andExpect(status().isNotFound());
            var id = add(m, base.replace("\"destinationType\"", "\"targetNodeId\":\"11\",\"responsibleUserId\":\"100\",\"destinationType\""), "A");
            m.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("targetNodeId").value("11"));
            m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command("move", 1, "3").replace("affectedNodeId\":\"13", "affectedNodeId\":\"12")))
                    .andExpect(status().isConflict());
            // An upstream target is relative to the discovery node, never the quantity node or UI display_order.
            m.perform(full(post(ROOT), "A").content(base.replace("check", "downstream")
                            .replace("affectedNodeId\":\"13", "affectedNodeId\":\"12")
                            .replace("\"destinationType\"", "\"targetNodeId\":\"13\",\"destinationType\"")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void warehousesAndFailedRequestRetry() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var body = command("warehouse", 0, "2").replace("PROCESS", "MATERIAL").replace("ROUTING_NODE", "WAREHOUSE");
            m.perform(full(post(ROOT), "A").content(body)).andExpect(status().isBadRequest());
            m.perform(full(post(ROOT), "A").content(body.replace("\"destinationType\"", "\"targetWarehouseId\":\"999\",\"destinationType\"")))
                    .andExpect(status().isBadRequest());
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("UPDATE wm_warehouse SET status='DISABLED' WHERE id=1");
            var valid = body.replace("\"destinationType\"", "\"targetWarehouseId\":\"1\",\"destinationType\"");
            m.perform(full(post(ROOT), "A").content(valid)).andExpect(status().isBadRequest());
            jdbc.update("UPDATE wm_warehouse SET status='ENABLED' WHERE id=1");
            m.perform(full(post(ROOT), "A").content(valid.replace("\"destinationType\"", "\"targetNodeId\":\"11\",\"destinationType\"")))
                    .andExpect(status().isBadRequest());
            add(m, valid, "A");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
        }
    }

    @Test
    void permissionsAndConcurrentCommands() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = add(m, command("first", 0, "2"), "A");
            for (String path : List.of(ROOT + "/" + id + "/revisions", ROOT + "/" + id + "/void"))
                m.perform(post(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                                .contentType("application/json").content(command("forbid", 1, "2")))
                        .andExpect(status().isForbidden());
            m.perform(post(ROOT).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(command("ordinary", 0, "1"))).andExpect(status().isOk());
            m.perform(post(ROOT).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A")
                            .contentType("application/json").content(command("forbidden", 0, "1")))
                    .andExpect(status().isForbidden());
            var barrier = new CyclicBarrier(2);
            Callable<Integer> update = () -> {
                barrier.await();
                return m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command(UUID.randomUUID()
                        .toString(), 1, "5"))).andReturn().getResponse().getStatus();
            };
            var a = pool.submit(update);
            var b = pool.submit(update);
            var result = List.of(a.get(), b.get());
            assertTrue(result.contains(200));
            assertTrue(result.contains(409));
            var same = command("concurrent", 0, "3");
            Callable<String> create = () -> {
                barrier.await();
                return add(m, same, "A");
            };
            var x = pool.submit(create);
            var y = pool.submit(create);
            assertEquals(x.get(), y.get());
            Callable<Integer> crossA = () -> {
                barrier.await();
                return m.perform(full(post(ROOT), "A").content(command("cross", 0, "1"))).andReturn().getResponse()
                        .getStatus();
            };
            Callable<Integer> crossB = () -> {
                barrier.await();
                return m.perform(full(post(ROOT), "A").content(command("cross", 0, "1").replace("executionId\":\"1", "executionId\":\"2")))
                        .andReturn().getResponse().getStatus();
            };
            var ca = pool.submit(crossA);
            var cb = pool.submit(crossB);
            var cross = List.of(ca.get(), cb.get());
            assertTrue(cross.contains(200));
            assertTrue(cross.contains(409));
            assertEquals(5, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
        }
    }

    @Test
    void responsibilityHistorySpoofProtectionAndLatestBeforeFiltering() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String initial = command("responsibility", 0, "2").replace("\"destinationType\"", "\"responsibleUserId\":\"100\",\"responsibleNodeId\":\"11\",\"destinationType\"");
            initial = initial.substring(0, initial.length() - 1) + ",\"reportedBy\":999,\"recordedBy\":999}";
            var id = add(m, initial, "A");
            m.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("reportedBy").value("1"))
                    .andExpect(jsonPath("responsibleUserId").value("100"));
            m.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command("responsibility-revise", 1, "3")))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/" + id + "/history"), "A"))
                    .andExpect(jsonPath("items[0].responsibleUserId").isEmpty())
                    .andExpect(jsonPath("items[1].responsibleUserId").value("100"));
            // H2 query fixture deliberately changes an old row to simulate historical import inconsistency.
            // Production MySQL forbids this update; this proves latest selection precedes the affected-node filter.
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("UPDATE mes_production_defect SET affected_node_id=12 WHERE declaration_id=? AND revision_no=1", id);
            m.perform(full(get(ROOT), "A").param("affectedNodeId", "12")).andExpect(jsonPath("total").value(0));
            m.perform(full(get(ROOT + "/executions/1/totals"), "A"))
                    .andExpect(jsonPath("[0].affectedNodeId").value("13"))
                    .andExpect(jsonPath("[0].effectiveDefectQty").value("3.000000"));
        }
    }

}
