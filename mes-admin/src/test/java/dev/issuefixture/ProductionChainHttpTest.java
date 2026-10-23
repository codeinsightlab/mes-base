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
    private static final String DEMAND = "{\"code\":\"QA_DEMAND\",\"productId\":\"1\",\"plannedQty\":\"1000\",\"routingId\":\"1\"}";

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
    void demandExistsWithoutBatchOrExecutionAndLegacyWritesAreExplicitlyRejected() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            m.perform(full(post(W), "A").content(DEMAND)).andExpect(status().isOk())
                    .andExpect(jsonPath("batches.length()").value(0))
                    .andExpect(jsonPath("allocation.executionCount").value(0))
                    .andExpect(jsonPath("allocation.remainingTargetQty").value("1000.000000"));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM production_batch", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            for (String legacy : List.of("[]", "null", "[{\"batchNo\":\"B\",\"plannedQty\":\"1000\"}]"))
                m.perform(full(post(W), "A").content(DEMAND.replace("QA_DEMAND", "LEGACY")
                                .replace("}", ",\"batches\":" + legacy + "}")))
                        .andExpect(status().isGone()).andExpect(jsonPath("errorCode").value("LEGACY_BATCH_READ_ONLY"));
            m.perform(full(post(W + "/2/actions/release"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.status").value("RELEASED"));
            m.perform(post(W).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                            .contentType("application/json").content(DEMAND))
                    .andExpect(status().isForbidden());
            m.perform(full(get(W + "/2"), "B")).andExpect(status().isNotFound());
        }
    }

    @Test
    void executionsOwnVersionsAllocationAndReleaseAdmissionWithoutMaterialIssue() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R1','另一已发布版本',3,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(3,1,'M1','产品')");
            String one = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"400\",\"status\":\"READY\"}";
            String two = one.replace("\"routingId\":\"1\"", "\"routingId\":\"3\"").replace("400", "300");
            m.perform(full(post(E), "A").content(one)).andExpect(status().isOk())
                    .andExpect(jsonPath("executionNo").value("EXE-1"));
            m.perform(full(post(E), "A").content(two)).andExpect(status().isOk())
                    .andExpect(jsonPath("routingId").value("3"));
            m.perform(full(get(W + "/1/allocation"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("allocatedTargetQty").value("700.000000"))
                    .andExpect(jsonPath("remainingTargetQty").value("300.000000"))
                    .andExpect(jsonPath("excessTargetQty").value("0.000000"));
            m.perform(full(put(W + "/1"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                            .replace("\"routingId\":\"1\"", "\"routingId\":\"3\"").replace("}", ",\"revision\":0}")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_FROZEN"));
            m.perform(full(get(E + "/1"), "A")).andExpect(status().isOk()).andExpect(jsonPath("routingId").value("1"));
            String running = one.replace("READY", "RUNNING").replace("}", ",\"startTime\":\"2026-10-14T09:00:00\"}");
            for (String body : List.of(running, running.replace("RUNNING", "COMPLETED")
                            .replace("}", ",\"endTime\":\"2026-10-14T10:00:00\"}"),
                    running.replace("RUNNING", "FULLY_RECEIVED")
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
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM production_batch", Integer.class));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_material_issue", Integer.class));
        }
    }

    @Test
    void currentCommandsDoNotNeedLegacyPathTableEvenWhenHistoricalBatchesExist() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO production_batch(id,workorder_id,batch_no,planned_qty,status,route_status,revision,create_time,update_time) VALUES(1,1,'OLD_BATCH',1000,'ISSUED','LOCKED',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            // TEST ONLY: remove the retired table in this isolated H2 fixture to detect hidden coupling.
            a.execute("DROP TABLE batch_route_node");
            m.perform(full(get(W + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("batches[0].routeStatus").value("LOCKED"))
                    .andExpect(jsonPath("batches[0].path").doesNotExist());
            m.perform(full(put(W + "/1"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                    .replace("}", ",\"revision\":0}"))).andExpect(status().isOk());
            m.perform(full(post(W), "A").content(DEMAND)).andExpect(status().isOk());
            m.perform(full(delete(W + "/2"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isNoContent());
            m.perform(full(get(W), "A")).andExpect(status().isOk());
            m.perform(full(post(W + "/1/actions/release"), "A").content("{\"revision\":1}"))
                    .andExpect(status().isOk());
            m.perform(full(post(E), "A").content("{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"3\",\"status\":\"READY\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("routingId").value("1"));
            m.perform(full(get(E + "/1"), "A")).andExpect(status().isOk());
            m.perform(full(post("/api/issue/mes-material-issue"), "A")
                            .content("{\"issueNo\":\"NO_BATCH_PATH\",\"executionId\":\"1\",\"status\":\"DRAFT\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                    .andExpect(jsonPath("routingId").value("1"));
            assertEquals(1, a.queryForObject("SELECT COUNT(*) FROM production_batch", Integer.class));
        }
    }

    @Test
    void historicalBatchAndPathStayReadableButCannotBeMutatedOrOrphaned() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO production_batch(id,workorder_id,batch_no,planned_qty,status,route_status,revision,create_time,update_time) VALUES(1,1,'OLD_BATCH',1000,'ISSUED','LOCKED',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO batch_route_node(id,batch_id,routing_node_id,execution_order) VALUES(1,1,55,1)");
            var before = a.queryForList("SELECT * FROM production_batch");
            var path = a.queryForList("SELECT * FROM batch_route_node");
            m.perform(full(get("/api/production/batch/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("routeStatus").value("LOCKED")).andExpect(jsonPath("path.length()").value(1));
            m.perform(full(get("/api/production/batch/1"), "B")).andExpect(status().isNotFound());
            m.perform(get("/api/production/batch/1").header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            // This fixture uses Spring Security default anonymous rejection (403).
            m.perform(get("/api/production/batch/1")).andExpect(status().isForbidden());
            m.perform(full(put("/api/production/batch/1/path"), "A").content("{\"revision\":2,\"nodeIds\":[55]}"))
                    .andExpect(status().isNotFound());
            m.perform(full(delete(W + "/1"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("WORKORDER_LEGACY_REFERENCED"));
            m.perform(full(put(W + "/1"), "A").content(DEMAND.replace("QA_DEMAND", "WO1")
                            .replace("}", ",\"revision\":0}")))
                    .andExpect(status().isOk());
            assertEquals(before, a.queryForList("SELECT * FROM production_batch"));
            assertEquals(path, a.queryForList("SELECT * FROM batch_route_node"));
        }
    }
}
