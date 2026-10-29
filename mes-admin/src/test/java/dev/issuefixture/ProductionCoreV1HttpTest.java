package dev.issuefixture;

import dev.mesbase.core.factory.FactoryRuntimeMode;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY vendor, real HTTP + generated SQL + two independent business databases.
 */
class ProductionCoreV1HttpTest {
    static final String E = "/api/production/mes-production-execution", A = "/api/production/executions/1/actions/";
    static final String EXEC = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"12\",\"status\":\"READY\"}";

    static MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer FULL").header("X-Factory-Id", "A").contentType("application/json");
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static void seed(AnnotationConfigWebApplicationContext c) throws Exception {
        var j = db(c);
        j.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_name,is_key,display_order) VALUES(1,1,'A',1,'相同工序',false,1),(2,1,'B',1,'相同工序',false,2),(3,1,'C',2,'分支C',false,3),(4,1,'D',3,'分支D',false,4),(5,1,'E',4,'终点',false,5)");
        j.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,1,2),(1,2,3),(1,2,4),(1,3,5),(1,4,5)");
        var m = MaterialIssueHttpTest.mvc(c);
        if (FactorySnapshotFixture.current(c, "A").mode() == FactoryRuntimeMode.ERP) {
            // TEST ONLY authoritative external release fixture, ordinary local release is forbidden.
            j.update("UPDATE workorder SET status='RELEASED' WHERE id=1");
        } else m.perform(full(post("/api/production/workorder/1/actions/release")).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        // Existing execution fixture for administrative/ERP-intent lifecycle tests; public creation is retired.
        j.update("INSERT INTO mes_production_execution(id,workorder_id,product_id,routing_id,target_qty,status,create_by,create_time,update_time) VALUES(1,1,1,1,12,'READY','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    static void select(MockMvc m) throws Exception {
        m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[\"1\",\"2\",\"3\",\"5\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void localFullFlowMultipleIssuesAndLifecycleCannotBeForged() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            j.update("DELETE FROM mes_production_execution"); // TEST ONLY unused initial fixtures.
            String body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
            var first = new ObjectMapper().readTree(ProductionInputHttpTest.submit(m, body));
            long execution = first.get("executionId").asLong();
            String actions = "/api/production/executions/" + execution + "/actions/";
            var locked = j.queryForObject("SELECT locked_at FROM mes_execution_path WHERE id=?", String.class, execution);
            ProductionInputHttpTest.submit(m, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L001", "100"));
            assertEquals(locked, j.queryForObject("SELECT locked_at FROM mes_execution_path WHERE id=?", String.class, execution));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE' AND status='POSTED'", Integer.class));
            m.perform(full(post(actions + "select-path")).content("{\"revision\":1,\"routingNodeIds\":[11,12]}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(actions + "start"))).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("RUNNING"));
            var started = j.queryForObject("SELECT start_time FROM mes_production_execution WHERE id=?", String.class, execution);
            m.perform(full(post(actions + "start"))).andExpect(status().isOk());
            assertEquals(started, j.queryForObject("SELECT start_time FROM mes_production_execution WHERE id=?", String.class, execution));
            WorkorderBomInputHttpTest.report(m, execution, "150");
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + first.get("documentId")
                            .asText() + "/actions/void")))
                    .andExpect(status().isConflict());
            m.perform(full(put(E + "/" + execution)).content(EXEC.replace("READY", "FULLY_RECEIVED")))
                    .andExpect(status().isConflict());
            m.perform(full(post(E)).content(EXEC.replace("READY", "RUNNING")))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Test
    void completePathUsesNodeIdentityAndRejectsGapsKeyBypassAndOldRevision() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            seed(c);
            var m = MaterialIssueHttpTest.mvc(c);
            for (var path : List.of("[1,3,5]", "[2,3,5]", "[1,2,3]", "[1,2,2,3,5]", "[1,2,999,5]"))
                m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":" + path + "}"))
                        .andExpect(status().isUnprocessableEntity());
            db(c).update("UPDATE routing_node SET is_key=true WHERE id=3");
            m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[1,2,4,5]}"))
                    .andExpect(status().isUnprocessableEntity());
            select(m);
            assertEquals(List.of(1L, 2L, 3L, 5L), db(c).queryForList("SELECT routing_node_id FROM mes_execution_path_node ORDER BY sequence_no", Long.class));
            m.perform(full(post(A + "select-path")).content("{\"revision\":0,\"routingNodeIds\":[1,2,3,5]}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(A + "start"))).andExpect(status().isConflict());
            m.perform(full(get("/api/production/executions/1/path")))
                    .andExpect(jsonPath("nodes[0].operationName").value("相同工序"));
        }
    }
}
