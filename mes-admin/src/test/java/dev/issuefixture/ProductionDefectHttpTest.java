package dev.issuefixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.controller.ProductionDefectController;
import dev.mesbase.business.quality.controller.QualityTreatmentController;
import dev.mesbase.business.quality.integration.LocalProductionDefectErpGateway;
import dev.mesbase.business.quality.service.ProductionDefectService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real SQL / Spring transactions / HTTP authorization / two routed H2 factories.
 */
class ProductionDefectHttpTest {
    static final String ROOT = "/api/quality/defects";

    @Configuration
    @Import({QualityTreatmentController.class, ProductionQuantityService.class, ProductionDefectController.class, ProductionDefectService.class, LocalProductionDefectErpGateway.class})
    static class Extra {

    }

    static AnnotationConfigWebApplicationContext context(Class<?>... extras) throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        if (extras.length > 0) c.register(extras);
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
        return "{\"assessment\":{\"type\":\"INDEPENDENT\",\"basis\":\"TEST ONLY explicit independent fixture\"},\"requestKey\":\"" + key + "\"," + (revision == 0 ? "" : "\"expectedRevision\":" + revision + ",\"revisionReason\":\"TEST订正\",") + "\"facts\":{\"executionId\":\"1\",\"affectedNodeId\":\"13\",\"defectQty\":\"" + quantity + "\",\"wasteCategory\":\"PROCESS\",\"defectReason\":\"TEST ONLY缺陷\",\"occurredAt\":\"2026-01-01T10:00:00\"}}";
    }

    static String add(MockMvc m, String body, String factory) throws Exception {
        var json = m.perform(full(post(ROOT), factory).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("record.revisionNo").value(1)).andExpect(jsonPath("externalExecuted").value(false))
                .andExpect(jsonPath("executionMode").value("LOCAL_ONLY")).andReturn().getResponse()
                .getContentAsString();
        return new ObjectMapper().readTree(json).path("record").path("declarationId").asText();
    }

    @Test
    void recentTreatmentKeepsEveryEventAndOrdersByRegistrationTimeRatherThanUuidWithoutCount() throws Exception {
        try (var c = context()) {
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            for (int i = 0; i < 12; i++)
                jdbc.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,operator_user_id,recorded_at,reason,returned_qty,transfer_qty) VALUES(?,?,'same-declaration',?,1,11,?,100,?,'TEST ONLY immutable event',?,?)",
                        String.format("00000000-0000-0000-0000-%012d", 12 - i), "test-" + i, i < 6 ? 1 : 2,
                        i == 11 ? "TRANSFER_WAREHOUSE" : "RETURN_NODE", "2026-01-01 10:00:" + String.format("%02d", i),
                        i == 11 ? "0" : "0.123456", i == 11 ? "0.123456" : "0");
            var capture = new RecentProductionReportHttpTest.SqlCapture();
            c.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(capture);
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(full(get("/api/quality/treatments/recent"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10))
                    .andExpect(jsonPath("[0].id").value("00000000-0000-0000-0000-000000000001"))
                    .andExpect(jsonPath("[0].actionType").value("TRANSFER_WAREHOUSE"))
                    .andExpect(jsonPath("[0].transferQty").value("0.123456"))
                    .andExpect(jsonPath("[9].id").value("00000000-0000-0000-0000-000000000010"))
                    .andExpect(jsonPath("[9].declarationRevision").value(1));
            capture.assertNoCount();
            mvc.perform(full(get("/api/quality/treatments/recent"), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
            mvc.perform(get("/api/quality/treatments/recent").header("Authorization", "Bearer NONE")
                            .header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/quality/treatments/recent").header("Authorization", "Bearer FULL"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void completeVersionLifecycleAndAggregation() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = add(m, command("one", 0, "2"), "A");
            m.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("affectedNodeId").value("13"))
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
    void declarationsValidateActualPathAndResponsibleUserReferences() throws Exception {
        try (var c = context()) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var base = new ObjectMapper().readTree(command(UUID.randomUUID().toString(), 0, "1"));
            var facts = (ObjectNode) base.get("facts");
            facts.put("affectedNodeId", "9999");
            mvc.perform(full(post(ROOT), "A").content(base.toString())).andExpect(status().isBadRequest());
            facts.put("affectedNodeId", "13");
            facts.put("responsibleUserId", "999");
            mvc.perform(full(post(ROOT), "A").content(base.toString())).andExpect(status().isBadRequest());
            facts.put("responsibleUserId", "100");
            var id = add(mvc, base.toString(), "A");
            mvc.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("affectedNodeId").value("13"));
            mvc.perform(full(post(ROOT + "/" + id + "/revisions"), "A").content(command(UUID.randomUUID()
                            .toString(), 1, "3").replace("affectedNodeId\":\"13", "affectedNodeId\":\"12")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void declarationDoesNotPretendToHaveAnExecutedDisposition() throws Exception {
        try (var c = context()) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var id = add(mvc, command("current-declaration", 0, "2"), "A");
            mvc.perform(full(get(ROOT + "/" + id), "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("discoveredNodeId").doesNotExist())
                    .andExpect(jsonPath("responsibleNodeId").doesNotExist())
                    .andExpect(jsonPath("destinationType").doesNotExist())
                    .andExpect(jsonPath("targetNodeId").doesNotExist())
                    .andExpect(jsonPath("targetWarehouseId").doesNotExist())
                    .andExpect(jsonPath("destinationRemark").doesNotExist())
                    .andExpect(jsonPath("commandVersion").doesNotExist());
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
                            .contentType("application/json").content(command("ordinary", 0, "1")))
                    .andExpect(status().isForbidden());
            var ordinary = new ObjectMapper().readTree(command("ordinary", 0, "1"));
            ((ObjectNode) ordinary).remove("assessment");
            m.perform(post(ROOT).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(ordinary.toString())).andExpect(status().isOk());
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
            String initial = command("responsibility", 0, "2").replace("\"wasteCategory\"", "\"responsibleUserId\":\"100\",\"wasteCategory\"");
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
