package dev.issuefixture;

import dev.mesbase.business.production.controller.ProductionReportingController;
import dev.mesbase.business.production.service.ProductionReportSubmitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: two isolated H2 databases, real generated XML/transaction locks/qualification; center users mocked.
 */
class ProductionReportingHttpTest {
    static final String ROOT = "/api/production/reporting";

    @Configuration
    @Import({ProductionReportHttpTest.Extra.class, OperatorAssignmentHttpTest.Extra.class,
            ProductionReportingController.class, ProductionReportSubmitService.class})
    static class Extra {
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class, ProductionDefectHttpTest.Extra.class);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            ProductionInputTestData.confirmed(j, 1, new BigDecimal("10"));
            ProductionInputTestData.confirmed(j, 2, new BigDecimal("10"));
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
        }
        return c;
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String body(long node, String qty, String key) {
        return "{\"executionId\":\"1\",\"routingNodeId\":\"" + node + "\",\"reportQty\":\"" + qty + "\",\"requestId\":\"" + key + "\"}";
    }

    static String body(long node, String qty) {
        return body(node, qty, UUID.randomUUID().toString());
    }

    static void confirmIssue(MockMvc m) throws Exception {
        m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H)).content(ProductionCoreV1HttpTest.HEADER))
                .andExpect(status().isOk());
        m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.I)).content(MaterialIssueHttpTest.ITEM))
                .andExpect(status().isOk());
        m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/1/actions/submit")))
                .andExpect(status().isOk());
    }

    static long submit(MockMvc m, long node, String qty) throws Exception {
        var result = m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(node, qty)))
                .andExpect(status().isOk()).andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sequence", "routing", "status", "graph"})
    void heldPathReuseStillRejectsMalformedOrUnlockedPathsBeforeWriting(String damage) throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            String before = j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class);
            switch (damage) {
                case "sequence" ->
                        j.update("UPDATE mes_execution_path_node SET sequence_no=sequence_no+10 WHERE execution_id=1");
                case "routing" -> j.update("UPDATE mes_execution_path SET routing_id=2 WHERE id=1");
                case "status" -> j.update("UPDATE mes_execution_path SET path_status='SELECTED' WHERE id=1");
                case "graph" -> j.update("UPDATE routing_node SET routing_id=2 WHERE id=12");
                default -> throw new AssertionError(damage);
            }
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1")))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("errorCode").value(damage.equals("graph") ? "ROUTING_GRAPH_INVALID" : "REPORT_PATH_REQUIRED"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            assertEquals(before, j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
        }
    }

    @Test
    void pendingQualityCandidateAndPostShareTheSameNodeBoundary() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            confirmIssue(m);
            var j = db(c);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(1,11,'REPORT',8,1,1,CURRENT_TIMESTAMP,'TEST ONLY stability',CURRENT_TIMESTAMP),(1,12,'REPORT',5,1,1,CURRENT_TIMESTAMP,'TEST ONLY stability',CURRENT_TIMESTAMP),(1,13,'REPORT',3,1,1,CURRENT_TIMESTAMP,'TEST ONLY stability',CURRENT_TIMESTAMP)");
            var json = new ObjectMapper();
            var declaration = json.readTree(ProductionDefectHttpTest.command(UUID.randomUUID().toString(), 0, "2"));
            ((ObjectNode) declaration).remove("assessment");
            var facts = (ObjectNode) declaration.get("facts");
            facts.put("affectedNodeId", "12");
            facts.put("destinationNodeId", "12");
            ProductionDefectHttpTest.add(m, declaration.toString(), "A");
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", "1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("candidates[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("candidates[1].status").value("QUALITY_PENDING"))
                    .andExpect(jsonPath("candidates[2].status").value("QUALITY_PENDING"));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1"))).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(12, "1"))).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("QUALITY_ATTRIBUTION_REQUIRED"));
        }
    }

    @Test
    void lookupReadOnlyPermissionsIdentityAndCandidateReasons() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            for (int i = 0; i < 2; i++)
                m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", "1")))
                        .andExpect(status().isOk()).andExpect(jsonPath("identity.executionId").value("1"))
                        .andExpect(jsonPath("candidates[0].status").value("AVAILABLE"))
                        .andExpect(jsonPath("candidates[1].status").value("NO_QUANTITY"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            m.perform(get(ROOT + "/context").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .param("executionId", "1")).andExpect(status().isForbidden());
            for (String code : List.of("EXE:1", "", "9223372036854775808", "ERP:1"))
                m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", code)))
                        .andExpect(status().isBadRequest());
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", "999")))
                    .andExpect(status().isNotFound());
            j.update("DELETE FROM mes_operation_user");
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", "1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("candidates[0].status").value("NO_CAPABILITY"));
        }
    }

    @Test
    void trustedIdentityStartReplayAndChangedPayload() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            String command = body(11, "3");
            var first = m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                            .content(command.replace("}", ",\"operatorUserId\":999,\"recordedBy\":999,\"reportTime\":\"2000-01-01T00:00:00\",\"recordType\":\"ADJUSTMENT\",\"factoryId\":\"B\"}")))
                    .andExpect(status().isOk()).andExpect(jsonPath("operatorUserId").value("1"))
                    .andExpect(jsonPath("recordedBy").value("1")).andExpect(jsonPath("recordType").value("REPORT"))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(first, m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command.replace("\"3\"", "\"4\"")))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                            .content(command.replace("\"executionId\":\"1\"", "\"executionId\":\"2\"")))
                    .andExpect(status().isConflict());
            submit(m, 11, "2");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void quantitiesPathStatusAndQualificationAreRechecked() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            for (String q : List.of("0", "-1", "11", "1.1234567"))
                m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, q)))
                        .andExpect(status().is4xxClientError());
            for (long n : List.of(12L, 99L))
                m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(n, "1")))
                        .andExpect(status().isConflict());
            j.update("INSERT INTO mes_execution_assignment(execution_id,routing_node_id,user_id,assigned_by,assigned_at) VALUES(1,11,2,1,CURRENT_TIMESTAMP)");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("OPERATOR_NOT_ASSIGNED"));
            j.update("INSERT INTO mes_execution_assignment(execution_id,routing_node_id,user_id,assigned_by,assigned_at) VALUES(1,11,1,1,CURRENT_TIMESTAMP)");
            submit(m, 11, "1");
            j.update("DELETE FROM mes_operation_user");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("OPERATOR_CAPABILITY_REQUIRED"));
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            for (String state : List.of("FULLY_RECEIVED", "CANCELLED")) {
                j.update("UPDATE mes_production_execution SET status=? WHERE id=1", state);
                m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1")))
                        .andExpect(status().is4xxClientError());
            }
            j.update("UPDATE mes_production_execution SET status='READY',start_time=NULL WHERE id=1");
            j.update("UPDATE workorder SET status='DRAFT' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "1")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("WORKORDER_NOT_RELEASED"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void startFailureAndUnconfirmedPathLeaveNoFactOrIdempotencyRecord() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String command = body(11, "1");
            j.update("UPDATE mes_material_issue_input SET confirmed_at=NULL WHERE execution_id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andExpect(status().isConflict());
            assertEquals("READY", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            confirmIssue(m);
            j.update("UPDATE mes_material_issue_input SET confirmed_at=CURRENT_TIMESTAMP WHERE execution_id=1");
            j.update("UPDATE mes_execution_path SET path_status='SELECTED' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andExpect(status().isConflict());
            j.update("UPDATE mes_execution_path SET path_status='LOCKED' WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andExpect(status().isOk());
        }
    }

    @Test
    void concurrentDuplicatesAndOverreportRemainSerializedWithAdminAdjustments() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            String command = body(11, "6");
            var barrier = new CyclicBarrier(2);
            var tasks = List.of(pool.submit(() -> {
                        barrier.await();
                        return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andReturn().getResponse();
                    }),
                    pool.submit(() -> {
                        barrier.await();
                        return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andReturn()
                                .getResponse();
                    }));
            assertEquals(tasks.get(0).get().getContentAsString(), tasks.get(1).get().getContentAsString());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            var gate = new CyclicBarrier(2);
            var a = pool.submit(() -> {
                gate.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "3"))).andReturn()
                        .getResponse().getStatus();
            });
            var b = pool.submit(() -> {
                gate.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "3"))).andReturn()
                        .getResponse().getStatus();
            });
            assertTrue(List.of(a.get(), b.get()).contains(200));
            assertTrue(List.of(a.get(), b.get()).contains(409));
            assertEquals(9, j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE routing_node_id=11", Integer.class));
            var id = j.queryForObject("SELECT MIN(id) FROM mes_production_report", Long.class);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/" + id + "/adjustments"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-2\",\"reason\":\"TEST ONLY订正\"}"))
                    .andExpect(status().isOk());
            submit(m, 11, "3");
            submit(m, 12, "10");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_id=2", Integer.class));
        }
    }

    @Test
    void insertFailureRollsBackStartAndRetryUsesSameRequest() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            j.execute("ALTER TABLE mes_production_report ADD CONSTRAINT test_only_fail CHECK(quantity_delta<>7)");
            String command = body(11, "7");
            assertTrue(m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andReturn().getResponse()
                    .getStatus() >= 400);
            assertEquals("READY", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            j.execute("ALTER TABLE mes_production_report DROP CONSTRAINT test_only_fail");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andExpect(status().isOk());
        }
    }

    @Test
    void formalReportAndAdminAdjustmentUseTheSameLock() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            confirmIssue(m);
            long id = submit(m, 11, "6");
            var gate = new CyclicBarrier(2);
            var a = pool.submit(() -> {
                gate.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(11, "3"))).andReturn()
                        .getResponse().getStatus();
            });
            var b = pool.submit(() -> {
                gate.await();
                return m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/" + id + "/adjustments"))
                                .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"3\",\"reason\":\"TEST ONLY并发订正\"}"))
                        .andReturn()
                        .getResponse().getStatus();
            });
            assertTrue(List.of(a.get(), b.get()).contains(200));
            assertTrue(List.of(a.get(), b.get()).contains(409));
            assertEquals(9, j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=1", Integer.class));
        }
    }

    @Test
    void selectedBranchUsesOnlyItsActualPredecessorAndFactoryLocalIdentity() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            j.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_name,display_order,is_key) VALUES(14,1,'OTHER',1,'未选分支',0,false),(15,2,'OTHER_VERSION',1,'其他版本',0,false)");
            j.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,11,14),(1,14,13)");
            confirmIssue(m);
            submit(m, 11, "2");
            submit(m, 12, "2");
            for (long node : List.of(14L, 15L))
                m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body(node, "1")))
                        .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/context").param("executionId", "1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantities.nodes[2].inputQty").value("2.000000"));
            m.perform(ProductionReportHttpTest.full(get(ROOT + "/context").param("executionId", "1"), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantities.nodes[1].inputQty").value("0"));
            m.perform(get(ROOT + "/context").header("Authorization", "Bearer FULL").param("executionId", "1"))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(post(ROOT).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body(11, "1"))).andExpect(status().isForbidden());
        }
    }

}
