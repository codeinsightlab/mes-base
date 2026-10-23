package dev.issuefixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated routed H2, actual HTTP/service/XML transactions; not deployed or real ERP evidence.
 */
class WorkorderBomInputHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    static void prepare(JdbcTemplate j, String plan, String base, String component) {
        j.update("DELETE FROM mes_production_execution"); // Only unused fresh fixture executions.
        j.update("UPDATE workorder SET planned_qty=? WHERE id=1", new BigDecimal(plan));
        j.update("UPDATE material_bom SET base_qty=? WHERE id=1", new BigDecimal(base));
        j.update("UPDATE material_bom_item SET quantity=? WHERE id=1", new BigDecimal(component));
    }

    static void select(MockMvc m, int revision, long bomId) throws Exception {
        m.perform(full(put("/api/production/workorder/1/material")).content(JSON.writeValueAsString(Map.of(
                        "expectedRevision", revision, "bomId", bomId, "warehouseIds", List.of(1), "workCenter", "TEST-BOM", "enabled", true))))
                .andExpect(status().isOk());
    }

    static long input(MockMvc m, String output) throws Exception {
        return JSON.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                        .toString(), "L001", output)))
                .get("executionId").asLong();
    }

    static void report(MockMvc m, long execution, String quantity) throws Exception {
        for (long node : List.of(11L, 12L, 13L)) {
            String body = ProductionReportHttpTest.supplement(node, quantity)
                    .replace("\"executionId\":\"1\"", "\"executionId\":\"" + execution + "\"");
            m.perform(full(post("/api/production/reports/supplements")).content(body)).andExpect(status().isOk());
        }
    }

    static void receive(MockMvc m, long execution, String quantity) throws Exception {
        var document = JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT)).content(JSON.writeValueAsString(Map.of(
                        "documentType", "PRODUCTION_RECEIPT", "sourceExecutionId", Long.toString(execution), "reason", "TEST ONLY BOM input cycle"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = document.get("document").get("id").asText();
        m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines")).content(
                        StockDocumentHttpTest.line("0", "10", "L001", quantity)
                                .replace("\"materialId\":\"1\"", "\"materialId\":\"99\"")
                                .replace("\"binCode\":\"BIN1\"", "\"binCode\":null")))
                .andExpect(status().isOk());
        UnifiedInboundHttpTest.confirm(m, id, 1);
    }

    @Test
    void ninetyThenTenReusesExecutionAndAppendsAnotherEndWithoutRewritingFacts() throws Exception {
        try (var c = ProductionInputHttpTest.context(WorkOrderProgressHttpTest.Extra.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(10,99,1,'L001',0,0,'ENABLED')");
            long execution = input(m, "90");
            m.perform(full(post("/api/production/executions/" + execution + "/actions/start")))
                    .andExpect(status().isOk());
            report(m, execution, "90");
            receive(m, execution, "90");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            var completion = j.queryForMap("SELECT * FROM mes_execution_completion WHERE execution_id=?", execution);
            var reports = j.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution);
            var inbound = j.queryForList("SELECT * FROM wm_stock_action WHERE source_type='STOCK_DOCUMENT' ORDER BY id");
            var endTime = j.queryForObject("SELECT end_time FROM mes_production_execution WHERE id=?", Object.class, execution);
            assertEquals(execution, input(m, "10"));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals(endTime, j.queryForObject("SELECT end_time FROM mes_production_execution WHERE id=?", Object.class, execution));
            assertEquals(completion, j.queryForMap("SELECT * FROM mes_execution_completion WHERE execution_id=?", execution));
            assertEquals(reports, j.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution));
            assertEquals(inbound, j.queryForList("SELECT * FROM wm_stock_action WHERE source_type='STOCK_DOCUMENT' ORDER BY id"));
            report(m, execution, "10");
            receive(m, execution, "10");
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion WHERE execution_id=?", Integer.class, execution));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            m.perform(full(get("/api/production/workorder-progress")).param("workorderCode", "WO1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].completedQty").value("100.000000"))
                    .andExpect(jsonPath("items[0].completedExecutions").value(1));
            assertEquals(0, new BigDecimal("250").compareTo(j.queryForObject("SELECT SUM(quantity) FROM mes_material_issue_item", BigDecimal.class)));
            m.perform(full(get("/api/production/input/calculate")).param("code", "WO1:M1#L001")
                    .param("workorderId", "1")
                    .param("stockReference", "1").param("outputQty", "1")).andExpect(status().is4xxClientError());
            m.perform(full(get("/api/production/executions/" + execution + "/closure"))).andExpect(status().isOk());
        }
    }

    @Test
    void cumulativeRoundingMakesTheMiddleDeltaAndLastRemainderExactAcrossBatches() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "3", "3", "1");
            select(m, 1, 1);
            input(m, "1");
            ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L002", "1"));
            input(m, "1");
            assertEquals(List.of(new BigDecimal("0.333333"), new BigDecimal("0.333334"), new BigDecimal("0.333333")),
                    j.queryForList("SELECT quantity FROM mes_material_issue_item ORDER BY id", BigDecimal.class));
            assertEquals(0, new BigDecimal("1").compareTo(j.queryForObject("SELECT SUM(quantity) FROM mes_material_issue_item", BigDecimal.class)));
            assertEquals(0, new BigDecimal("19999").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE id IN (1,2)", BigDecimal.class)));
            assertEquals(0, new BigDecimal("-1").compareTo(j.queryForObject("SELECT SUM(quantity_delta) FROM wm_stock_action WHERE action_type='ISSUE'", BigDecimal.class)));
        }
    }

    @Test
    void releaseDoesNotFreezeButFirstIssueUsesIndependentBaseUnitsAndKeepsOriginalUuid() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            j.update("INSERT INTO unit_measure(id,code,name,status) VALUES(2,'PCS','件','ENABLED')");
            j.update("UPDATE material SET unit_id=2 WHERE id=99");
            j.update("UPDATE material_bom SET unit_id=2 WHERE id=1");
            select(m, 1, 1); // The work order is already released.
            j.update("INSERT INTO material_bom SELECT 2,'BOM-SECOND','2',product_id,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at FROM material_bom WHERE id=1");
            j.update("INSERT INTO material_bom_item SELECT 2,2,material_id,5,unit_id,NULL,sort FROM material_bom_item WHERE id=1");
            select(m, 2, 2);
            select(m, 3, 1);
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_material WHERE frozen_at IS NOT NULL", Integer.class));
            String body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "40");
            var first = ProductionInputHttpTest.submit(m, body);
            assertNotNull(j.queryForObject("SELECT frozen_at FROM mes_workorder_material WHERE id=1", Object.class));
            j.update("UPDATE material_bom SET status='DISABLED' WHERE id=1");
            assertEquals(first, ProductionInputHttpTest.submit(m, body));
            m.perform(full(put("/api/production/workorder/1/material")).content(JSON.writeValueAsString(Map.of(
                            "expectedRevision", 4, "bomId", 2, "warehouseIds", List.of(1), "workCenter", "TEST", "enabled", true))))
                    .andExpect(status().isConflict());
            m.perform(full(get("/api/production/input/calculate")).param("code", "WO1:M1#L001")
                            .param("workorderId", "1")
                            .param("stockReference", "1").param("outputQty", "60")).andExpect(status().isOk())
                    .andExpect(jsonPath("issueQty").value("150.000000"))
                    .andExpect(jsonPath("stock.unitCode").value("KG"));
            input(m, "60");
            assertEquals(0, new BigDecimal("250").compareTo(j.queryForObject("SELECT SUM(quantity) FROM mes_material_issue_item", BigDecimal.class)));
        }
    }

    @Test
    void staleConcurrentCommandsCannotSpendTheLastQuotaTwiceOrCreateAnotherBatchExecution() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            input(m, "90");
            String one = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "10");
            String two = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "10");
            var a = pool.submit(() -> m.perform(full(post(ProductionInputHttpTest.ROOT)).content(one)).andReturn()
                    .getResponse().getStatus());
            var b = pool.submit(() -> m.perform(full(post(ProductionInputHttpTest.ROOT)).content(two)).andReturn()
                    .getResponse().getStatus());
            assertEquals(List.of(200, 409), Stream.of(a.get(), b.get()).sorted().toList());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }

    @Test
    void concurrentIdenticalUuidCreditsOnceEvenWhenItUsesTheWholePlan() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            String body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
            var first = pool.submit(() -> ProductionInputHttpTest.submit(m, body));
            var second = pool.submit(() -> ProductionInputHttpTest.submit(m, body));
            assertEquals(first.get(), second.get());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(0, new BigDecimal("250").compareTo(j.queryForObject("SELECT SUM(quantity) FROM mes_material_issue_item", BigDecimal.class)));
        }
    }

    @Test
    void componentMembershipAndSingleMaterialScopeAreValidatedInsteadOfSelectingFirstLine() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            long execution = input(m, "10");
            m.perform(full(get("/api/production/input")).param("code", "WO1:P1#L001"))
                    .andExpect(status().isBadRequest());
            m.perform(full(get("/api/production/identity")).param("code", "WO1:P1#L001"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value(Long.toString(execution)));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            // A new work order may select another version; a two-component version must be rejected as a whole.
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,source,create_time,update_time) VALUES(7,'TEST-MULTI',99,100,1,'RELEASED',0,'LOCAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO material_bom SELECT 7,'TEST-MULTI','1',product_id,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at FROM material_bom WHERE id=1");
            j.update("INSERT INTO material_bom_item SELECT 7,7,material_id,quantity,unit_id,NULL,sort FROM material_bom_item WHERE id=1");
            j.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(98,'TEST-MATERIAL-OTHER','TEST ONLY additional material',1,'ENABLED')");
            j.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,sort) VALUES(8,7,98,1,1,1)");
            m.perform(full(put("/api/production/workorder/7/material")).content(JSON.writeValueAsString(Map.of(
                            "expectedRevision", 0, "bomId", 7, "warehouseIds", List.of(1), "workCenter", "TEST", "enabled", true))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_SINGLE_MATERIAL_REQUIRED"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_material WHERE id=7", Integer.class));
        }
    }

}
