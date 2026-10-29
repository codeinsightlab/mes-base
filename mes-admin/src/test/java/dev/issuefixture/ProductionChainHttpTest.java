package dev.issuefixture;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two independent H2 databases, real generated SQL and real HTTP authorization/transactions.
 */
class ProductionChainHttpTest {
    private static final String W = "/api/production/workorder", E = "/api/production/mes-production-execution";
    private static final String DEMAND = "{\"code\":\"QA_DEMAND\",\"productId\":\"1\",\"plannedQty\":\"1000\",\"routingId\":\"1\",\"ruleCode\":\"WORK_ORDER\"}";

    private MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder request, String factory) {
        return request.header("Authorization", "Bearer FULL").header("X-Factory-Id", factory)
                .contentType("application/json");
    }

    @Test
    void concurrentDemandCorrectionKeepsRevisionProtection() throws Exception {
        try (var c = MaterialIssueHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var start = new CountDownLatch(1);
            String body = DEMAND.replace("QA_DEMAND", "WO1").replace("}", ",\"revision\":0}");
            var first = pool.submit(() -> {
                start.await();
                return m.perform(full(put(W + "/1"), "A").content(body)).andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                start.await();
                return m.perform(full(put(W + "/1"), "A").content(body)).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(List.of(200, 409), List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            m.perform(full(get(W + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("workorder.revision").value(1));
        }
    }

    @Test
    void demandExistsWithoutBatchOrExecutionAndUnknownLegacyFieldsAreRejected() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            m.perform(full(post(W), "A").content(DEMAND)).andExpect(status().isOk())
                    .andExpect(jsonPath("batches").doesNotExist())
                    .andExpect(jsonPath("allocation.executionCount").value(0))
                    .andExpect(jsonPath("allocation.remainingTargetQty").value("1000.000000"));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            for (String legacy : List.of("[]", "null", "{}", "7", "[{\"batchNo\":\"B\",\"plannedQty\":\"1000\"}]"))
                m.perform(full(post(W), "A").content(DEMAND.replace("QA_DEMAND", "LEGACY")
                                .replace("}", ",\"batches\":" + legacy + "}")))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("errorCode").value("BAD_REQUEST"));
            m.perform(full(post(W), "A").content(DEMAND.replace("}", ",\"retiredPath\":[]}")))
                    .andExpect(status().isBadRequest());
            m.perform(full(put(W + "/2"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                            .replace("}", ",\"revision\":0,\"batches\":null}")))
                    .andExpect(status().isBadRequest());
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM workorder WHERE code='LEGACY'", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_execution_path", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_execution_path_node", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(2, a.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
            assertEquals(0, a.queryForObject("SELECT revision FROM workorder WHERE id=2", Integer.class));
            m.perform(full(get(W), "A")).andExpect(status().isOk());
            m.perform(full(post(W + "/2/actions/release"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.status").value("RELEASED"));
            m.perform(post(W).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                            .contentType("application/json").content(DEMAND))
                    .andExpect(status().isForbidden());
            m.perform(full(get(W + "/2"), "B")).andExpect(status().isNotFound());
        }
    }

    @Test
    void existingExecutionsKeepVersionsAllocationAndReleaseAdmissionWithoutInventingIssues() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R1','另一已发布版本',3,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO routing_product(routing_id,material_id) VALUES(3,1)");
            String one = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"400\",\"status\":\"READY\"}";
            String two = one.replace("\"routingId\":\"1\"", "\"routingId\":\"3\"").replace("400", "300");
            // Existing fixture executions only; new executions are created by formal BOM input.
            a.update("INSERT INTO mes_production_execution(id,workorder_id,product_id,routing_id,target_qty,status) VALUES(1,1,1,1,400,'READY'),(2,1,1,3,300,'READY')");
            m.perform(full(get(W + "/1/allocation"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("allocatedTargetQty").value("700.000000"))
                    .andExpect(jsonPath("remainingTargetQty").value("300.000000"))
                    .andExpect(jsonPath("excessTargetQty").value("0.000000"));
            m.perform(full(put(W + "/1"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                            .replace("\"routingId\":\"1\"", "\"routingId\":\"3\"").replace("}", ",\"revision\":0}")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_FROZEN"));
            m.perform(full(get(E + "/1"), "A")).andExpect(status().isOk()).andExpect(jsonPath("routingId").value("1"));
            String running = one.replace("READY", "RUNNING").replace("}", ",\"startTime\":\"2026-10-14T09:00:00\"}");
            for (String body : List.of(running, running.replace("RUNNING", "FULLY_RECEIVED")
                    .replace("}", ",\"endTime\":\"2026-10-14T10:00:00\"}")))
                m.perform(full(put(E + "/1"), "A").content(body)).andExpect(status().isConflict())
                        .andExpect(jsonPath("errorCode").value("EXECUTION_FROZEN"));
            m.perform(full(post(W + "/1/actions/release"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isOk());
            m.perform(full(put(E + "/1"), "A").content(running)).andExpect(status().isConflict());
            m.perform(full(post("/api/production/executions/1/actions/start"), "A")).andExpect(status().isConflict());
            m.perform(full(put(E + "/2"), "A").content(two.replace("300", "900")))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(full(get(W + "/1/allocation"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("allocatedTargetQty").value("700.000000"))
                    .andExpect(jsonPath("remainingTargetQty").value("300.000000"))
                    .andExpect(jsonPath("excessTargetQty").value("0.000000"));
            m.perform(full(get(W + "/1/allocation"), "B")).andExpect(status().isOk())
                    .andExpect(jsonPath("executionCount").value(0));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'", Integer.class));
        }
    }

    @Test
    void currentCommandsWorkWithoutRetiredBatchTables() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            m.perform(full(get(W + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("batches").doesNotExist());
            m.perform(full(put(W + "/1"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                    .replace("}", ",\"revision\":0}"))).andExpect(status().isOk());
            m.perform(full(post(W), "A").content(DEMAND)).andExpect(status().isOk());
            m.perform(full(delete(W + "/2"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isNoContent());
            m.perform(full(get(W), "A")).andExpect(status().isOk());
            m.perform(full(post(W + "/1/actions/release"), "A").content("{\"revision\":1}"))
                    .andExpect(status().isOk());
            m.perform(full(post(E), "A").content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"3\",\"status\":\"READY\"}"))
                    .andExpect(status().isMethodNotAllowed());
            a.update("INSERT INTO mes_production_execution(id,workorder_id,product_id,routing_id,target_qty,status) VALUES(1,1,1,1,3,'READY')");
            m.perform(full(get(E + "/1"), "A")).andExpect(status().isOk());

        }
    }

    @Test
    void retiredBatchRoutesHaveNoHandler() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(full(get("/api/production/batch/workorder/1"), "A")).andExpect(status().isNotFound());
            m.perform(full(get("/api/production/batch/1"), "A")).andExpect(status().isNotFound());
            m.perform(full(put("/api/production/batch/1/path"), "A")
                            .content("{\"revision\":2,\"nodeIds\":[55]}"))
                    .andExpect(status().isNotFound());
        }
    }
}
