package dev.issuefixture;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.controller.ProductionCombinationController;
import dev.mesbase.business.production.controller.ProductionInboundController;
import dev.mesbase.business.production.controller.ProductionInputController;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.business.production.domain.ProductionQrCode;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.service.ProductionCombinationService;
import dev.mesbase.business.production.service.ProductionInboundService;
import dev.mesbase.business.production.service.ProductionInputService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY two separate H2 business databases; real local commands/SQL/locks, no simulated ERP success.
 */
class ProductionInputHttpTest {
    static final String ROOT = "/api/production/input";

    @Configuration
    @Import({ProductionCombinationService.class, ProductionCombinationController.class, ProductionInputController.class, ProductionInputService.class, ProductionReportHttpTest.Extra.class,
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
            WorkorderMaterialFixture.seedSnapshot(j);
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

    /**
     * TEST ONLY fixed planning identity for existing downstream tests; new planner tests use generate().
     */
    static String command(MockMvc m, String request, String lot, String qty) throws Exception {
        String code = ProductionQrCode.generate("WO1", List.of(new ProductionQrCode.MaterialBatch("M1", lot)));
        return confirmCommand(m, request, code, qty);
    }

    static String confirmCommand(MockMvc m, String request, String code, String qty) throws Exception {
        var calc = m.perform(ProductionCoreV1HttpTest.full(get("/api/production/combinations/calculate"))
                        .param("code", code).param("quantity", qty))
                .andExpect(status().isOk()).andReturn();
        String key = new ObjectMapper().readTree(calc.getResponse().getContentAsString()).get("calculationKey")
                .asText();
        return new ObjectMapper().writeValueAsString(Map.of("requestId", request, "code", code, "outputQty", qty, "routingId", "1", "routingNodeIds", List.of("11", "12", "13"), "calculationKey", key));
    }

    static JsonNode generate(MockMvc m, String quantity) throws Exception {
        return new ObjectMapper().readTree(m.perform(ProductionCoreV1HttpTest.full(post("/api/production/combinations"))
                        .content("{\"workorderId\":\"1\",\"quantity\":\"" + quantity + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    static String submit(MockMvc mvc, String body) throws Exception {
        return mvc.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("POSTED")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void generatedIdentityIgnoresPrintQuantityAndLocationAndNeverCreatesProductionFacts() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var a = generate(m, "100");
            var b = generate(m, "50");
            assertEquals(a.get("combination").get("code"), b.get("combination").get("code"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            j.update("UPDATE wm_inventory SET bin_code='MOVED' WHERE id=1");
            assertEquals(a.get("combination").get("code"), generate(m, "80").get("combination").get("code"));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/combinations/qr"))
                            .param("code", a.get("combination").get("code").asText()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void fixedCombinationAccumulatesConfirmedSetsReplaysAndNeverReopensEndedExecution() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=2");
            String code = generate(m, "100").get("combination").get("code").asText();
            String firstBody = confirmCommand(m, UUID.randomUUID().toString(), code, "50");
            var first = new ObjectMapper().readTree(submit(m, firstBody));
            String execution = first.get("executionCode").asText();
            submit(m, firstBody);
            var second = new ObjectMapper().readTree(submit(m, confirmCommand(m, UUID.randomUUID()
                    .toString(), code, "30")));
            assertEquals(execution, second.get("executionCode").asText());
            assertEquals(0, new BigDecimal("80").compareTo(j.queryForObject("SELECT SUM(output_qty) FROM mes_material_issue_input", BigDecimal.class)));
            assertEquals(0, new BigDecimal("9840").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE status='POSTED'", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/identity")).param("code", code))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionCode").value(execution));
            j.update("UPDATE mes_production_execution SET status='FULLY_RECEIVED' WHERE execution_code=?", execution);
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_CLOSED"));
            assertEquals(code, generate(m, "10").get("combination").get("code").asText());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(firstBody)).andExpect(status().isOk());
        }
    }

    @Test
    void stockChangeReturnsLatestLimitAndFailedConfirmationLeavesNoExecutionOrInput() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=2");
            String code = generate(m, "100").get("combination").get("code").asText();
            String command = confirmCommand(m, UUID.randomUUID().toString(), code, "100");
            j.update("UPDATE wm_inventory SET quantity=80,available_quantity=80 WHERE id=1");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command)).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_STOCK_CHANGED"))
                    .andExpect(jsonPath("maximumQty").value("40.000000"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isOk()).andExpect(jsonPath("maximumQty").value("40.000000"));
        }
    }

    static void multiMaterial(JdbcTemplate j) {
        j.update("UPDATE wm_inventory SET quantity=120,available_quantity=120,batch_no='B01' WHERE id=1");
        j.update("UPDATE wm_inventory SET quantity=80,available_quantity=80,batch_no='B02' WHERE id=2");
        j.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'C','TEST ONLY second component',1,'ENABLED')");
        j.update("INSERT INTO mes_workorder_material(id,workorder_id,line_no,material_id,unit_id,total_required_qty,planned_qty,manually_adjusted,updated_by,updated_at) SELECT 2,1,2,2,1,planned_qty,planned_qty,TRUE,1,CURRENT_TIMESTAMP FROM workorder WHERE id=1");
        j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(3,2,1,100,100,'ENABLED','C01','BIN2')");
    }

    @Test
    void printingRejectsMaterialThatRequiresTwoBatchesWithoutCreatingFacts() throws Exception {
        try (var c = context()) {
            var j = db(c);
            multiMaterial(j);
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/combinations"))
                            .content("{\"workorderId\":\"1\",\"quantity\":\"100\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_SPLIT_BATCH_REQUIRED"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
        }
    }

    @Test
    void fixedMultiMaterialBatchesSupportSmallerRequestsAndConcurrentFirstIssues() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            multiMaterial(j);
            j.update("UPDATE wm_inventory SET quantity=200,available_quantity=200 WHERE id=1");
            var plan = generate(m, "100");
            String code = plan.get("combination").get("code").asText();
            assertEquals(1, plan.get("combination").get("items").get(0).get("batches").size());
            String one = confirmCommand(m, UUID.randomUUID()
                    .toString(), code, "50"), two = confirmCommand(m, UUID.randomUUID().toString(), code, "30");
            var a = pool.submit(() -> submit(m, one));
            var b = pool.submit(() -> submit(m, two));
            var first = new ObjectMapper().readTree(a.get());
            var second = new ObjectMapper().readTree(b.get());
            assertEquals(first.get("executionCode"), second.get("executionCode"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, new BigDecimal("80").compareTo(j.queryForObject("SELECT SUM(output_qty) FROM mes_material_issue_input", BigDecimal.class)));
            assertEquals(0, new BigDecimal("160").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_stock_document_line WHERE material_id=1", BigDecimal.class)));
            assertEquals(0, new BigDecimal("80").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_stock_document_line WHERE material_id=2", BigDecimal.class)));
            assertEquals(0, new BigDecimal("120").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE material_id=1", BigDecimal.class)));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line WHERE demand_line_id IS NULL", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isOk()).andExpect(jsonPath("maximumQty").value("20.000000"))
                    .andExpect(jsonPath("warehouses[0].name").value("验收仓库"));
            m.perform(get("/api/production/combinations/calculate").header("Authorization", "Bearer FULL")
                    .header("X-Factory-Id", "B").param("code", code)).andExpect(status().isConflict());
        }
    }

    @Test
    void failureOnSecondMaterialRollsBackTheWholeLocalTransactionAndSameRequestCanRetry() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            multiMaterial(j);
            j.update("UPDATE wm_inventory SET quantity=200,available_quantity=200 WHERE id=1");
            String code = generate(m, "100").get("combination").get("code").asText();
            String command = confirmCommand(m, UUID.randomUUID().toString(), code, "50");
            j.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_reject_second CHECK(material_id<>2)");
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(command))
                    .andExpect(status().is5xxServerError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, new BigDecimal("200").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            j.execute("ALTER TABLE wm_stock_action DROP CONSTRAINT test_reject_second");
            submit(m, command);
            submit(m, command);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }

    @Test
    void differentCodesOnOneOrderCannotConcurrentlySpendThePlanTwice() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("DELETE FROM mes_execution_path_node");
            j.update("DELETE FROM mes_execution_path");
            j.update("DELETE FROM mes_production_execution");
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            j.update("UPDATE mes_workorder_material SET planned_qty=100,total_required_qty=200 WHERE id=1");
            j.update("UPDATE wm_inventory SET quantity=120,available_quantity=120");
            String first = generate(m, "60").get("combination").get("code").asText();
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            String second = generate(m, "60").get("combination").get("code").asText();
            j.update("UPDATE wm_inventory SET quantity=120,available_quantity=120 WHERE id=1");
            assertNotEquals(first, second);
            String one = confirmCommand(m, UUID.randomUUID().toString(), first, "60");
            String two = confirmCommand(m, UUID.randomUUID().toString(), second, "60");
            var start = new CyclicBarrier(2);
            var a = pool.submit(() -> {
                start.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(one)).andReturn().getResponse()
                        .getStatus();
            });
            var b = pool.submit(() -> {
                start.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT)).content(two)).andReturn().getResponse()
                        .getStatus();
            });
            assertEquals(List.of(200, 409), Stream.of(a.get(), b.get()).sorted().toList());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(0, new BigDecimal("60").compareTo(j.queryForObject("SELECT SUM(output_qty) FROM mes_material_issue_input", BigDecimal.class)));
            assertEquals(0, new BigDecimal("120").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory", BigDecimal.class)));
        }
    }

    @Test
    void ordinaryWarehouseMaterialIssueWithOrderReferenceNeverCreatesMesInput() throws Exception {
        try (var c = context()) {
            var j = db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var response = m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/outbound-documents/aggregate"))
                            .content("{\"header\":{\"direction\":\"OUT\",\"documentType\":\"MATERIAL_ISSUE\",\"ruleCode\":\"MATERIAL_ISSUE\",\"reason\":\"TEST ONLY ordinary warehouse issue\",\"sourceWorkorderId\":\"1\"},\"items\":[{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"20\"}]}"))
                    .andExpect(status().isOk()).andReturn();
            var header = new ObjectMapper().readTree(response.getResponse().getContentAsString()).get("document");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/outbound-documents/" + header.get("id")
                            .asText() + "/actions/approve"))
                    .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), header.get("revision")
                            .asInt()))).andExpect(status().isOk());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
            assertEquals(0, new BigDecimal("9980").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
