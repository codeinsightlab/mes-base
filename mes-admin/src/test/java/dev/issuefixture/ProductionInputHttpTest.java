package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Primary;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.WarehouseErpAdapter;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.controller.ProductionInputController;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.domain.ProductionScanCode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY two separate H2 business databases; real local commands/SQL/locks, no simulated ERP success.
 */
class ProductionInputHttpTest {
    static final String ROOT = "/api/production/input";

    @Configuration
    @Import({ProductionInputController.class, ProductionInputService.class, ProductionInputSupplyService.class, ProductionReportHttpTest.Extra.class})
    static class Extra {
        @Bean
        @Primary
        WarehouseIntegration inputWarehouse(List<WarehouseErpAdapter> adapters) {
            return spy(new WarehouseIntegration(adapters));
        }

        @Bean
        IRoutingService routingService() {
            return mock(IRoutingService.class);
        }
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY LOCAL MES", Map.of(
                "mes.business.enabled", "true", "mes.production.input.factories.A.workorders.1.material-id", "1",
                "mes.production.input.factories.A.workorders.1.total-required-qty", "2000",
                "mes.production.input.factories.A.workorders.1.work-center", "TEST-CENTER",
                "mes.production.input.factories.A.workorders.1.warehouse-ids", "1")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(1,1,1,10000,10000,'ENABLED','L001','BIN1'),(2,1,1,10000,10000,'ENABLED','L002','BIN1')");
        }
        return c;
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String command(MockMvc m, String request, String lot, String qty) throws Exception {
        String stock = lot.equals("L001") ? "1" : "2";
        var calc = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "M1#" + lot)
                        .param("workorderId", "1").param("stockReference", stock).param("outputQty", qty))
                .andExpect(status().isOk()).andReturn();
        String key = new ObjectMapper().readTree(calc.getResponse().getContentAsString()).get("calculationKey")
                .asText();
        return new ObjectMapper().writeValueAsString(Map.of("requestId", request, "code", "M1#" + lot, "workorderId", "1", "stockReference", stock, "outputQty", qty, "routingId", "1", "routingNodeIds", List.of("11", "12", "13"), "inputKind", "PRODUCTION_INPUT", "calculationKey", key));
    }

    static String submit(MockMvc mvc, String body) throws Exception {
        return mvc.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("CONFIRMED")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void repeatedInputReservesOrderCapacityAndRejectsOverflowWithoutAnotherIssue() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("DELETE FROM mes_production_execution"); // TEST ONLY unused seeded executions
            submit(m, command(m, UUID.randomUUID().toString(), "L001", "100"));
            submit(m, command(m, UUID.randomUUID().toString(), "L001", "900"));
            int issues = j.queryForObject("SELECT COUNT(*) FROM mes_material_issue", Integer.class);
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                            .content(command(m, UUID.randomUUID().toString(), "L001", "1")))
                    .andExpect(status().isConflict());
            assertEquals(issues, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue", Integer.class));
            assertEquals(0, new BigDecimal("1000").compareTo(j.queryForObject("SELECT target_qty FROM mes_production_execution", BigDecimal.class)));
        }
    }

    @Test
    void firstRepeatRunningDifferentLotsIdempotenceAndPermanentOwnership() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "M1#L001")).andExpect(status().isOk())
                    .andExpect(jsonPath("sourceMode").value("LOCAL_ONLY"))
                    .andExpect(jsonPath("stocks[0].availableQty").value("10000.0000"));
            String request = UUID.randomUUID().toString(), body = command(m, request, "L001", "100");
            var first = new ObjectMapper().readTree(submit(m, body));
            long e = first.get("executionId").asLong();
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            assertEquals(first, new ObjectMapper().readTree(submit(m, body)));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE execution_id=? AND output_qty<>100", Integer.class, e));
            j.update("UPDATE mes_production_execution SET status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=?", e);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,11,'REPORT',80,1,1,CURRENT_TIMESTAMP,'TEST ONLY running fixture',CURRENT_TIMESTAMP)", e);
            var next = new ObjectMapper().readTree(submit(m, command(m, UUID.randomUUID().toString(), "L001", "200")));
            assertEquals(e, next.get("executionId").asLong());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/" + e)))
                    .andExpect(status().isOk()).andExpect(jsonPath("productionInputQty").value("300.000000"))
                    .andExpect(jsonPath("nodes[0].availableQty").value("220.000000"));
            var other = new ObjectMapper().readTree(submit(m, command(m, UUID.randomUUID().toString(), "L002", "150")));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE id=? AND target_qty<>300", Integer.class, e));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", first.get("code").asText())).andExpect(status().isOk())
                    .andExpect(jsonPath("executionId").value(Long.toString(e)));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", "M1#L001"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value(Long.toString(e)));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body.replace("\"100\"", "\"101\"")))
                    .andExpect(status().isConflict());
            j.update("UPDATE mes_production_execution SET status='COMPLETED' WHERE id=?", e);
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                    .content(command(m, UUID.randomUUID().toString(), "L001", "10"))).andExpect(status().isConflict());
        }
    }

    @Test
    void concurrentNewRequestsBindOneExecutionAndRepeatedRequestCreditsOnce() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String one = command(m, UUID.randomUUID().toString(), "L001", "100"), two = command(m, UUID.randomUUID()
                    .toString(), "L001", "200");
            var a = pool.submit(() -> submit(m, one));
            var b = pool.submit(() -> submit(m, two));
            a.get();
            b.get();
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE issue_id>0", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE id=1 AND quantity<>9400", Integer.class));
            var aa = pool.submit(() -> submit(m, one));
            var bb = pool.submit(() -> submit(m, one));
            aa.get();
            bb.get();
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE issue_id>0", Integer.class));
        }
    }

    @Test
    void unknownKeepsBindingAndIntentWithoutContributionAndReconcilesOnlyOriginalOperation() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            var integration = c.getBean(WarehouseIntegration.class);
            doReturn(new ErpActionResult(Status.UNKNOWN, Mode.LOCAL_ONLY, null, "TEST ONLY injected interrupted local confirmation")).when(integration)
                    .createMaterialIssue(any(), any(), any());
            var body = command(m, UUID.randomUUID().toString(), "L001", "100");
            var response = m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("SUBMIT_UNKNOWN")).andReturn();
            var r = new ObjectMapper().readTree(response.getResponse().getContentAsString());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("SUBMIT_UNKNOWN"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "TEST_PROVIDER", "TEST_A"));
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + r.get("issueId")
                    .asText() + "/actions/reconcile-erp"))).andExpect(status().isOk());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("CONFIRMED"));
            verify(integration, times(1)).createMaterialIssue(any(), any(), any());
        }
    }

    @Test
    void pendingReductionReservesCapacityBeforeExternalWindowAndSuccessRestoresStockOnce() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            var first = new ObjectMapper().readTree(submit(m, command(m, UUID.randomUUID().toString(), "L001", "100")));
            var second = new ObjectMapper().readTree(submit(m, command(m, UUID.randomUUID()
                    .toString(), "L001", "200")));
            var e = first.get("executionId").asText();
            j.update("UPDATE mes_production_execution SET status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=?", e);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,11,'REPORT',280,1,1,CURRENT_TIMESTAMP,'TEST ONLY reduction fixture',CURRENT_TIMESTAMP)", e);
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + first.get("issueId")
                    .asText() + "/actions/void"))).andExpect(status().isConflict());
            assertEquals("CONFIRMED", j.queryForObject("SELECT status FROM mes_material_issue WHERE id=?", String.class, first.get("issueId")
                    .asText()));
            long original = j.queryForObject("SELECT id FROM mes_production_report WHERE execution_id=?", Long.class, e);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,11,'ADJUSTMENT',-200,?,1,1,CURRENT_TIMESTAMP,'TEST ONLY safe correction',CURRENT_TIMESTAMP)", e, original);
            var integration = c.getBean(WarehouseIntegration.class);
            doReturn(new ErpActionResult(Status.UNKNOWN, Mode.LOCAL_ONLY, null, "TEST ONLY cancellation uncertainty")).when(integration)
                    .execute(any(), any(), anyBoolean());
            m.perform(ProductionCoreV1HttpTest.full(post(MaterialIssueHttpTest.H + "/" + second.get("issueId")
                            .asText() + "/actions/void"))).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("VOID_UNKNOWN"));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/" + e)))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[0].availableQty").value("20.000000"));
            // A historical/admin report entry uses the same quantity guard during the cancellation window.
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/supplements"))
                    .content(ProductionReportHttpTest.supplement(11, "21")
                            .replace("executionId\":\"1", "executionId\":\"" + e))).andExpect(status().isConflict());
        }
    }

    @Test
    void factoriesKeepSameCodeAndLocalIdsIndependentAndOldCodeNeverGuessesAcrossProducts() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY second factory recipe", Map.of(
                            "mes.production.input.factories.B.workorders.1.material-id", "1", "mes.production.input.factories.B.workorders.1.total-required-qty", "2000",
                            "mes.production.input.factories.B.workorders.1.work-center", "TEST-CENTER", "mes.production.input.factories.B.workorders.1.warehouse-ids", "1")));
            var body = command(m, UUID.randomUUID().toString(), "L001", "100");
            var a = new ObjectMapper().readTree(submit(m, body));
            var b = m.perform(ProductionReportHttpTest.full(post(ROOT), "B").content(body)).andExpect(status().isOk())
                    .andReturn();
            var secondFactory = new ObjectMapper().readTree(b.getResponse().getContentAsString());
            assertEquals(a.get("executionId"), secondFactory.get("executionId"));
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(3,'TEST_SAME_PRODUCT_OTHER_ORDER',1,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO workorder_erp_extra(id) VALUES(3)");
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY conflicting order recipe", Map.of(
                            "mes.production.input.factories.A.workorders.3.material-id", "1",
                            "mes.production.input.factories.A.workorders.3.total-required-qty", "2000",
                            "mes.production.input.factories.A.workorders.3.work-center", "TEST-CENTER",
                            "mes.production.input.factories.A.workorders.3.warehouse-ids", "1")));
            var conflictingCalculation = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate"))
                            .param("code", "M1#L001").param("workorderId", "3").param("stockReference", "1")
                            .param("outputQty", "100"))
                    .andExpect(status().isOk()).andReturn();
            var conflict = (ObjectNode) new ObjectMapper().readTree(body).deepCopy();
            conflict.put("requestId", UUID.randomUUID().toString());
            conflict.put("workorderId", "3");
            conflict.put("calculationKey", new ObjectMapper().readTree(conflictingCalculation.getResponse()
                    .getContentAsString()).get("calculationKey").asText());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(conflict.toString()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_IDENTITY_CONFLICT"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_identity", Integer.class));
            j.update("INSERT INTO material(id,code,name,unit_id,material_type,status) VALUES(2,'P2','TEST ONLY second product',1,'PRODUCT','ENABLED')");
            j.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(1,2,'P2','TEST ONLY second product')");
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(2,'TEST_WO2',2,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO workorder_erp_extra(id) VALUES(2)");
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY second product recipe", Map.of(
                            "mes.production.input.factories.A.workorders.2.material-id", "1", "mes.production.input.factories.A.workorders.2.total-required-qty", "2000",
                            "mes.production.input.factories.A.workorders.2.work-center", "TEST-CENTER", "mes.production.input.factories.A.workorders.2.warehouse-ids", "1")));
            var calc = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "M1#L001")
                            .param("workorderId", "2").param("stockReference", "1").param("outputQty", "50"))
                    .andExpect(status().isOk()).andReturn();
            var payload = (ObjectNode) new ObjectMapper().readTree(body).deepCopy();
            payload.put("requestId", UUID.randomUUID().toString());
            payload.put("workorderId", "2");
            payload.put("outputQty", "50");
            payload.put("calculationKey", new ObjectMapper().readTree(calc.getResponse().getContentAsString())
                    .get("calculationKey").asText());
            var other = new ObjectMapper().readTree(submit(m, payload.toString()));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", "M1#L001"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_IDENTITY_AMBIGUOUS"));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", other.get("code").asText())).andExpect(status().isOk())
                    .andExpect(jsonPath("productCode").value("P2"));
            m.perform(ProductionReportHttpTest.full(get("/api/production/identity"), "B").param("code", "M1#L001"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void configuredCandidatesArePagedIndependentlyOfAllReleasedOrders() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            for (int id = 2; id <= 1000; id++)
                j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?,?,1,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "TEST_SCALE_" + id);
            for (int size : new int[]{99, 100, 101, 1000}) {
                j.update("UPDATE workorder SET status=CASE WHEN id<=? THEN 'RELEASED' ELSE 'DRAFT' END", size);
                m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "M1#L001")).andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(1));
            }
            for (int size : new int[]{0, 1, 20, 200}) {
                var properties = new HashMap<String, Object>();
                properties.put("mes.production.input.factories.A.workorders.1.material-id", "99");
                for (int id = 1; id <= size; id++) {
                    String prefix = "mes.production.input.factories.A.workorders." + id + ".";
                    properties.put(prefix + "material-id", "1");
                    properties.put(prefix + "total-required-qty", "20");
                    properties.put(prefix + "warehouse-ids", "1");
                    properties.put(prefix + "work-center", "TEST-CENTER");
                }
                c.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("TEST candidate scale", properties));
                var ids = new ArrayList<Long>();
                for (int offset = 0; offset < Math.max(1, size); offset += 20) {
                    var response = m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "M1#L001")
                                    .param("offset", Integer.toString(offset)))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(size)).andReturn()
                            .getResponse().getContentAsString();
                    for (var order : new ObjectMapper().readTree(response).get("orders"))
                        ids.add(order.get("workorderId").asLong());
                }
                assertEquals(size, new HashSet<>(ids).size());
                for (int i = 0; i < size; i++) assertEquals(size - i, ids.get(i));
                if (size == 200)
                    m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "M1#L001")
                                    .param("workorderId", "1").param("stockReference", "1").param("outputQty", "100"))
                            .andExpect(status().isOk());
            }
        }
    }

}
