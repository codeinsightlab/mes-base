package dev.issuefixture;

import java.util.UUID;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import java.util.concurrent.atomic.AtomicReference;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import tools.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2 + real Spring transactions/production commands, no external ERP success simulation.
 */
class LocalWarehouseHttpTest {
    static final String ROOT = "/api/wm/local-stock/";

    static String body(String id, String qty, String before, String issue) {
        var data = new ObjectMapper().createObjectNode().put("requestId", id).put("stockId", "1")
                .put("quantity", qty).put("expectedQuantity", before).put("expectedAvailable", before)
                .put("reason", "TEST ONLY warehouse closure");
        if (issue != null) data.put("issueId", issue);
        return data.toString();
    }

    static void apply(MockMvc m, String type, String qty, String before) throws Exception {
        m.perform(ProductionCoreV1HttpTest.full(post(ROOT + type))
                        .content(body(UUID.randomUUID().toString(), qty, before, null)))
                .andExpect(status().isOk());
    }

    static void balance(JdbcTemplate j, String value) {
        assertEquals(0, new BigDecimal(value).compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        assertEquals(0, new BigDecimal(value).compareTo(j.queryForObject("SELECT available_quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
    }

    @Test
    void legalClosureAndNetReversalAndNoFabricatedFacts() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            apply(m, "opening", "500", "0");
            apply(m, "gain", "300", "500");
            String result = ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L001", "100"));
            String issue = new ObjectMapper().readTree(result).get("issueId").asText();
            balance(j, "600");
            String request = body(UUID.randomUUID().toString(), "20", "600", issue);
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "return")).content(request)).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "return")).content(request)).andExpect(status().isOk());
            balance(j, "620");
            apply(m, "loss", "5", "620");
            balance(j, "615");
            assertEquals(5, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE quantity_after<>quantity_before+quantity_delta OR available_after<>available_before+available_delta OR operator_user_id IS NULL", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "return"))
                    .content(body(UUID.randomUUID().toString(), "181", "615", issue))).andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + issue + "/actions/void")))
                    .andExpect(status().isOk());
            balance(j, "795");
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + issue + "/actions/void")))
                    .andExpect(status().isOk());
            balance(j, "795");
            assertEquals(6, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/wm-stock-action"))).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/wm-stock-action")).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
            m.perform(ProductionCoreV1HttpTest.full(put("/api/wm/wm-stock-action/" + UUID.randomUUID())).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
            m.perform(ProductionCoreV1HttpTest.full(delete("/api/wm/wm-stock-action")).content("[]"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Test
    void uuidConflictStaleSnapshotZeroOpeningAndPermissions() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            apply(m, "opening", "0", "0");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "opening"))
                    .content(body(UUID.randomUUID().toString(), "10", "0", null))).andExpect(status().isConflict());
            String request = body(UUID.randomUUID().toString(), "300", "0", null);
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain")).content(request))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain")).content(request))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain")).content(request.replace("300", "301")))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain"))
                    .content(body(UUID.randomUUID().toString(), "1", "0", null))).andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "loss"))
                    .content(body(UUID.randomUUID().toString(), "301", "300", null))).andExpect(status().isConflict());
            m.perform(post(ROOT + "gain").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(request)).andExpect(status().isForbidden());
            balance(j, "300");
        }
    }

    @Test
    void serializedConcurrentReplayChangesStockOnce() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            String request = body(UUID.randomUUID().toString(), "7", "10000", null);
            var barrier = new CyclicBarrier(2);
            var tasks = List.of(pool.submit(() -> {
                        barrier.await();
                        return m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain")).content(request)).andReturn()
                                .getResponse().getStatus();
                    }),
                    pool.submit(() -> {
                        barrier.await();
                        return m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "gain")).content(request))
                                .andReturn().getResponse().getStatus();
                    }));
            for (var task : tasks) assertEquals(200, task.get());
            balance(j, "10007");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void failureAfterDeductionOrDuringActionOrAfterInputRollsBackAllLocalFacts() throws Exception {
        for (String constraint : List.of(
                "ALTER TABLE mes_material_issue_input ADD CONSTRAINT test_fail CHECK(confirmed_at IS NULL)",
                "ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail CHECK(quantity_delta>=0)",
                "ALTER TABLE mes_execution_path ADD CONSTRAINT test_fail CHECK(id<=2 OR path_status<>'LOCKED')")) {
            try (var c = ProductionInputHttpTest.context()) {
                var j = ProductionInputHttpTest.db(c);
                var m = MaterialIssueHttpTest.mvc(c);
                j.execute(constraint);
                String command = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
                int response = m.perform(ProductionCoreV1HttpTest.full(post(ProductionInputHttpTest.ROOT))
                        .content(command)).andReturn().getResponse().getStatus();
                assertTrue(response >= 400);
                balance(j, "10000");
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL OR stock_applied=true", Integer.class));
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue WHERE status='CONFIRMED'", Integer.class));
            }
        }
    }

    @Test
    void twoDifferentExecutionsCompeteForOneStockWithoutOverdrawing() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE wm_inventory SET quantity=300,available_quantity=300 WHERE id=1");
            j.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'P2','TEST ONLY second product',1,'ENABLED')");
            j.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,2)");
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(2,'WO2',2,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO material_bom SELECT 2,'TEST-BOM-SECOND','1',2,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at FROM material_bom WHERE id=1");
            j.update("INSERT INTO material_bom_item SELECT 2,2,material_id,quantity,unit_id,NULL,sort FROM material_bom_item WHERE id=1");
            j.update("INSERT INTO mes_workorder_material SELECT 2,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,2,bom_base_qty,bom_component_qty,output_unit_id,NULL FROM mes_workorder_material WHERE id=1");
            String first = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
            var second = new ObjectMapper().readTree(first).deepCopy();
            var secondNode = (ObjectNode) second;
            String calculation = m.perform(ProductionCoreV1HttpTest.full(get(ProductionInputHttpTest.ROOT + "/calculate"))
                            .param("code", "WO2:M1#L001").param("workorderId", "2").param("stockReference", "1")
                            .param("outputQty", "100"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            secondNode.put("requestId", UUID.randomUUID().toString()).put("workorderId", "2").put("code", "WO2:M1#L001")
                    .put("calculationKey", new ObjectMapper().readTree(calculation).get("calculationKey").asText());
            var barrier = new CyclicBarrier(2);
            var x = pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ProductionInputHttpTest.ROOT))
                        .content(first)).andReturn().getResponse().getStatus();
            });
            var y = pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ProductionInputHttpTest.ROOT))
                        .content(secondNode.toString())).andReturn().getResponse().getStatus();
            });
            var statuses = List.of(x.get(), y.get());
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 400 || code == 409).count());
            balance(j, "100");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
        }
    }

    @Test
    void oldSubmissionWithoutSourceSnapshotRemainsCancellable() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var calculation = new AtomicReference<Calculation>();
            FactoryIntegrationContext.run(FactorySnapshotFixture.current(c, "A"), () ->
                    calculation.set(c.getBean(ProductionInputSupplyService.class)
                            .calculate("WO1:M1#L001", 1L, "1", new BigDecimal("100"))));
            ProductionInputTestData.confirmed(j, 1, new BigDecimal("100"));
            j.update("UPDATE mes_material_issue_input SET stock_id=1,stock_applied=true,calculation_json=? WHERE issue_id=-1", new ObjectMapper().writeValueAsString(calculation.get()));
            j.update("UPDATE wm_inventory SET quantity=9800,available_quantity=9800 WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/-1/actions/void")))
                    .andExpect(status().isOk());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL' AND reference_action_id IS NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='ISSUE'", Integer.class));
            balance(j, "10000");
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/-1/actions/void")))
                    .andExpect(status().isOk());
            balance(j, "10000");
        }
    }

    @Test
    void changedBinCannotBeRestoredAndStartedProductionCannotReturnMaterials() throws Exception {
        for (boolean changedIdentity : List.of(true, false)) {
            try (var c = ProductionInputHttpTest.context()) {
                var j = ProductionInputHttpTest.db(c);
                var m = MaterialIssueHttpTest.mvc(c);
                var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                        .toString(), "L001", "100")));
                String issue = result.get("issueId").asText();
                if (changedIdentity) {
                    j.update("UPDATE wm_inventory SET bin_code='TEST_TAMPERED_BIN' WHERE id=1");
                    m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + issue + "/actions/void")))
                            .andExpect(status().isConflict());
                } else {
                    j.update("UPDATE mes_production_execution SET start_time=CURRENT_TIMESTAMP WHERE id=?", result.get("executionId")
                            .asLong());
                    m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "return"))
                                    .content(body(UUID.randomUUID().toString(), "20", "9800", issue)))
                            .andExpect(status().isConflict());
                }
                balance(j, "9800");
                assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            }
        }
    }
}
