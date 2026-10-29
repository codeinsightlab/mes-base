package dev.issuefixture;

import dev.mesbase.common.utils.RequestFingerprint;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Primary;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.controller.ProductionInputController;
import dev.mesbase.business.production.controller.ProductionInboundController;
import dev.mesbase.business.production.service.ProductionInboundService;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.ProductionCodeRules;
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
    @Import({ProductionInputController.class, ProductionInputService.class, ProductionInputSupplyService.class, ProductionReportHttpTest.Extra.class,
            ProductionInboundController.class,
            ProductionInboundService.class})
    static class Extra {
        @Bean
        @Primary
        WarehouseIntegration inputWarehouse() {
            return spy(new WarehouseIntegration());
        }

        @Bean
        IRoutingService routingService() {
            return mock(IRoutingService.class);
        }
    }

    static AnnotationConfigWebApplicationContext context(Class<?>... additional) throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY LOCAL MES", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        if (additional.length > 0) c.register(additional);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            seedBom(j, 2);
            j.update("INSERT INTO mes_workorder_material(id,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,bom_id,bom_base_qty,bom_component_qty,output_unit_id) VALUES(1,1,1,2000,1000,'[1]','TEST-CENTER',1,'ENABLED',1,CURRENT_TIMESTAMP,1,1,2,1)");
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(1,1,1,10000,10000,'ENABLED','L001','BIN1'),(2,1,1,10000,10000,'ENABLED','L002','BIN1')");
        }
        return c;
    }

    static void seedBom(JdbcTemplate j, int quantity) {
        j.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(99,'P1','TEST ONLY output material',1,'ENABLED')");
        j.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,99)");
        j.update("UPDATE workorder SET product_id=99 WHERE id=1");
        j.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at,published_at) VALUES(1,'TEST-BOM','1',99,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        j.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,sort) VALUES(1,1,1,?,1,0)", quantity);
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String command(MockMvc m, String request, String lot, String qty) throws Exception {
        String stock = lot.equals("L001") ? "1" : "2";
        var calc = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "WO1:M1#" + lot)
                        .param("workorderId", "1").param("stockReference", stock).param("outputQty", qty))
                .andExpect(status().isOk()).andReturn();
        String key = new ObjectMapper().readTree(calc.getResponse().getContentAsString()).get("calculationKey")
                .asText();
        return new ObjectMapper().writeValueAsString(Map.of("requestId", request, "code", "WO1:M1#" + lot, "workorderId", "1", "stockReference", stock, "outputQty", qty, "routingId", "1", "routingNodeIds", List.of("11", "12", "13"), "inputKind", "PRODUCTION_INPUT", "calculationKey", key));
    }

    static String submit(MockMvc mvc, String body) throws Exception {
        return mvc.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("POSTED")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void repeatedInputReservesOrderCapacityAndRejectsOverflowWithoutAnotherIssue() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("DELETE FROM mes_production_execution"); // TEST ONLY unused seeded executions
            submit(m, command(m, UUID.randomUUID().toString(), "L001", "100"));
            submit(m, command(m, UUID.randomUUID().toString(), "L001", "900"));
            int issues = j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'", Integer.class);
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "WO1:M1#L001")
                            .param("workorderId", "1").param("stockReference", "1").param("outputQty", "1"))
                    .andExpect(status().isConflict());
            assertEquals(issues, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='MATERIAL_ISSUE'", Integer.class));
            assertEquals(0, new BigDecimal("1000").compareTo(j.queryForObject("SELECT target_qty FROM mes_production_execution", BigDecimal.class)));
        }
    }

    @Test
    void firstRepeatRunningDifferentLotsIdempotenceAndPersistedBusinessBatch() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "WO1:M1#L001")).andExpect(status().isOk())
                    .andExpect(jsonPath("sourceMode").value("LOCAL_ONLY"))
                    .andExpect(jsonPath("stocks[0].availableQty").value("10000.000000"));
            String request = UUID.randomUUID().toString(), body = command(m, request, "L001", "100");
            var first = new ObjectMapper().readTree(submit(m, body));
            long e = first.get("executionId").asLong();
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
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
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE id=? AND target_qty<>300", Integer.class, e));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", first.get("code").asText())).andExpect(status().isOk())
                    .andExpect(jsonPath("executionId").value(Long.toString(e)));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", "M1#L001"))
                    .andExpect(status().isBadRequest());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body.replace("\"100\"", "\"101\"")))
                    .andExpect(status().isConflict());
            j.update("UPDATE mes_production_execution SET status='FULLY_RECEIVED' WHERE id=?", e);
            submit(m, command(m, UUID.randomUUID().toString(), "L001", "10"));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, e));
        }
    }

    @Test
    void concurrentNewRequestsReuseOneBusinessBatchAndRepeatedRequestCreditsOnce() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String one = command(m, UUID.randomUUID().toString(), "L001", "100"), two = command(m, UUID.randomUUID()
                    .toString(), "L001", "200");
            var a = pool.submit(() -> submit(m, one));
            var b = pool.submit(() -> submit(m, two));
            a.get();
            b.get();
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE document_id>0 AND confirmed_at IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE id=1 AND quantity<>9400", Integer.class));
            var aa = pool.submit(() -> submit(m, one));
            var bb = pool.submit(() -> submit(m, one));
            aa.get();
            bb.get();
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE document_id>0 AND confirmed_at IS NOT NULL", Integer.class));
        }
    }

    @Test
    void localFailureRollsBackExecutionIntentBasisAndStock() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            j.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_issue CHECK(action_type <> 'ISSUE')");
            var body = command(m, UUID.randomUUID().toString(), "L001", "100");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().is5xxServerError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_material WHERE frozen_at IS NOT NULL", Integer.class));
            assertEquals(0, new BigDecimal("10000").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
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
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.OUT_ROOT + "/" + first.get("documentId")
                    .asText() + "/actions/void"))).andExpect(status().isConflict());
            assertEquals("POSTED", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, first.get("documentId")
                    .asText()));
            long original = j.queryForObject("SELECT id FROM mes_production_report WHERE execution_id=?", Long.class, e);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,11,'ADJUSTMENT',-200,?,1,1,CURRENT_TIMESTAMP,'TEST ONLY safe correction',CURRENT_TIMESTAMP)", e, original);
            j.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_reversal CHECK(action_type <> 'REVERSAL')");
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.OUT_ROOT + "/" + second.get("documentId")
                    .asText() + "/actions/void"))).andExpect(status().is5xxServerError());
            assertEquals("POSTED", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, second.get("documentId")
                    .asText()));
            j.execute("ALTER TABLE wm_stock_action DROP CONSTRAINT test_fail_reversal");
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.OUT_ROOT + "/" + second.get("documentId")
                            .asText() + "/actions/void"))).andExpect(status().isOk())
                    .andExpect(jsonPath("documentId").value(second.get("documentId").asText()));
            assertEquals("SUCCEEDED", j.queryForObject("SELECT CASE WHEN COUNT(*)=1 THEN 'SUCCEEDED' ELSE NULL END FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, second.get("documentId")
                    .asText()));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/" + e)))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[0].availableQty").value("20.000000"));
            // A historical/admin report entry uses the same quantity guard during the cancellation window.
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/supplements"))
                    .content(ProductionReportHttpTest.supplement(11, "21")
                            .replace("executionId\":\"1", "executionId\":\"" + e))).andExpect(status().isConflict());
        }
    }

    @Test
    void factoriesAndWorkordersKeepSameProductLotIndependentWithoutMaterialCodeGuessing() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);

            var body = command(m, UUID.randomUUID().toString(), "L001", "100");
            var a = new ObjectMapper().readTree(submit(m, body));
            var b = m.perform(ProductionReportHttpTest.full(post(ROOT), "B").content(body)).andExpect(status().isOk())
                    .andReturn();
            var secondFactory = new ObjectMapper().readTree(b.getResponse().getContentAsString());
            assertEquals(a.get("executionId"), secondFactory.get("executionId"));
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(3,'TEST_SAME_PRODUCT_OTHER_ORDER',99,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO workorder_erp_extra(id) VALUES(3)");
            j.update("INSERT INTO mes_workorder_material SELECT 3,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,bom_id,bom_base_qty,bom_component_qty,output_unit_id,NULL FROM mes_workorder_material WHERE id=1");

            var conflictingCalculation = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate"))
                            .param("code", "TEST_SAME_PRODUCT_OTHER_ORDER:M1#L001").param("workorderId", "3")
                            .param("stockReference", "1")
                            .param("outputQty", "100"))
                    .andExpect(status().isOk()).andReturn();
            var conflict = (ObjectNode) new ObjectMapper().readTree(body).deepCopy();
            conflict.put("requestId", UUID.randomUUID().toString());
            conflict.put("workorderId", "3");
            conflict.put("code", "TEST_SAME_PRODUCT_OTHER_ORDER:M1#L001");
            conflict.put("calculationKey", new ObjectMapper().readTree(conflictingCalculation.getResponse()
                    .getContentAsString()).get("calculationKey").asText());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(conflict.toString()))
                    .andExpect(status().isOk());
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            j.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(3,'P2','TEST ONLY second product',1,'ENABLED')");
            j.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,3)");
            j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(2,'TEST_WO2',3,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            j.update("INSERT INTO workorder_erp_extra(id) VALUES(2)");
            j.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at,is_default) SELECT 2,'TEST-BOM-2','1',3,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at,0 FROM material_bom WHERE id=1");
            j.update("INSERT INTO material_bom_item SELECT 2,2,material_id,quantity,unit_id,NULL,sort FROM material_bom_item WHERE id=1");
            j.update("INSERT INTO mes_workorder_material SELECT 2,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,2,bom_base_qty,bom_component_qty,output_unit_id,NULL FROM mes_workorder_material WHERE id=1");

            var calc = m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate"))
                            .param("code", "TEST_WO2:M1#L001")
                            .param("workorderId", "2").param("stockReference", "1").param("outputQty", "50"))
                    .andExpect(status().isOk()).andReturn();
            var payload = (ObjectNode) new ObjectMapper().readTree(body).deepCopy();
            payload.put("requestId", UUID.randomUUID().toString());
            payload.put("workorderId", "2");
            payload.put("code", "TEST_WO2:M1#L001");
            payload.put("outputQty", "50");
            payload.put("calculationKey", new ObjectMapper().readTree(calc.getResponse().getContentAsString())
                    .get("calculationKey").asText());
            var other = new ObjectMapper().readTree(submit(m, payload.toString()));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", "M1#L001"))
                    .andExpect(status().isBadRequest());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", other.get("code").asText())).andExpect(status().isOk())
                    .andExpect(jsonPath("productCode").value("P2"));
            m.perform(ProductionReportHttpTest.full(get("/api/production/identity"), "B")
                            .param("code", a.get("code").asText()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void legacyMes1RequestReplaysItsFrozenUuidWithoutBindingOrBatchBackfill() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            var json = new ObjectMapper();
            String body = command(m, UUID.randomUUID().toString(), "L001", "100");
            var first = json.readTree(submit(m, body));
            var legacy = (ObjectNode) json.readTree(body);
            legacy.put("code", "MES1.TTE.TTE.TDAwMQ");
            var original = json.treeToValue(legacy, ProductionInputService.Command.class);
            String fingerprint = RequestFingerprint.sha256(json.writeValueAsString(original));
            // TEST ONLY frozen pre-upgrade request, deliberately without a business batch. No mapping is consulted.
            j.update("UPDATE mes_material_issue_input SET request_hash=? WHERE id=?", fingerprint, original.requestId());
            j.update("UPDATE mes_production_execution SET batch_no=NULL WHERE id=?", first.get("executionId").asText());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(legacy.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("executionId").value(first.get("executionId").asText()))
                    .andExpect(jsonPath("code").isEmpty());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE document_id>0 AND confirmed_at IS NOT NULL", Integer.class));
            assertEquals(0, new BigDecimal("9800").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            legacy.put("requestId", UUID.randomUUID().toString());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(legacy.toString()))
                    .andExpect(status().isBadRequest());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
        }
    }

    @Test
    void generatedLookupContainsWorkorderProductAndPersistedBatchWithoutLegacyIdentityType() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var json = new ObjectMapper();
            var saved = json.readTree(submit(m, command(m, UUID.randomUUID().toString(), "L001", "100")));
            String code = "WO1:P1#L001";
            assertEquals(code, saved.get("code").asText());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", code))
                    .andExpect(status().isOk()).andExpect(jsonPath("batchNo").value("L001"))
                    .andExpect(jsonPath("identityType").doesNotExist());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", ProductionCodeRules.format(new ProductionScanCode("wo1", "M1", "L001"))))
                    .andExpect(status().isNotFound());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", ProductionCodeRules.format(new ProductionScanCode("WO1", "m1", "L001"))))
                    .andExpect(status().isNotFound());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity"))
                            .param("code", ProductionCodeRules.format(new ProductionScanCode("WO1", "M1", "L001 "))))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void scannedWorkorderIsTheOnlyCandidateDespiteUnrelatedReleasedOrders() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            for (int id = 2; id <= 101; id++)
                j.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?,?,1,1000,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "TEST_SCALE_" + id);
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "WO1:M1#L001"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("orders.length()").value(1))
                    .andExpect(jsonPath("orders[0].workorderId").value("1"));
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("code", "WO1:M1#L001").param("offset", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("orders").isEmpty());
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/calculate")).param("code", "WO1:M1#L001")
                            .param("workorderId", "2").param("stockReference", "1").param("outputQty", "100"))
                    .andExpect(status().isBadRequest());
        }
    }

}
