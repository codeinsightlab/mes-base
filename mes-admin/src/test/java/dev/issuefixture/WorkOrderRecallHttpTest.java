package dev.issuefixture;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual routed services and SQL in isolated H2; no deployed-service or live MySQL claim. */
class WorkOrderRecallHttpTest {
    private static final String BASE = "/api/production/workorder/40";

    private static MockHttpServletRequestBuilder command(MockHttpServletRequestBuilder request, String body) {
        return request.header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                .contentType("application/json").content(body);
    }

    @Test
    void releasedOrderMustRecallBeforeEditingAndCanDeleteWithoutDownstream() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO workorder(id,source,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(40,'LOCAL','RECALL_TEST',1,10,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(40)");
            var mvc = MaterialIssueHttpTest.mvc(context);
            String edit = "{\"code\":\"RECALL_TEST\",\"productId\":\"1\",\"routingId\":\"1\",\"plannedQty\":\"12\",\"revision\":0}";
            mvc.perform(command(put(BASE), edit)).andExpect(status().isUnprocessableEntity());
            mvc.perform(command(post(BASE + "/actions/recall"), "{\"revision\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.status").value("DRAFT"))
                    .andExpect(jsonPath("workorder.revision").value(1));
            mvc.perform(command(put(BASE), edit.replace("\"revision\":0", "\"revision\":1")))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.plannedQty").value(12));
            mvc.perform(command(post(BASE + "/actions/release"), "{\"revision\":2}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.status").value("RELEASED"));
            mvc.perform(command(delete(BASE), "{\"revision\":3}")).andExpect(status().isNoContent());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE id=40", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE id=40", Integer.class));
        }
    }

    @Test
    void executionReferencesBlockRecallAndDeleteAndAuthorizationIsEnforced() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO workorder(id,source,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(40,'LOCAL','RECALL_BLOCKED',1,10,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(40)");
            jdbc.update("INSERT INTO mes_production_execution(id,execution_no,workorder_id,product_id,routing_id,target_qty,status) VALUES(40,'RECALL_EXE',40,1,1,10,'READY')");
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(command(post(BASE + "/actions/recall"), "{\"revision\":0}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            mvc.perform(command(delete(BASE), "{\"revision\":0}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            mvc.perform(post(BASE + "/actions/recall").header("Authorization", "Bearer NONE")
                            .header("X-Factory-Id", "A").contentType("application/json").content("{\"revision\":0}"))
                    .andExpect(status().isForbidden());
            assertEquals("RELEASED", jdbc.queryForObject("SELECT status FROM workorder WHERE id=40", String.class));
        }
    }
}
