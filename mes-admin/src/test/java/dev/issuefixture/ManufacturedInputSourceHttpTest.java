package dev.issuefixture;

import dev.mesbase.business.production.controller.ManufacturedInputSourceController;
import dev.mesbase.business.production.dao.ManufacturedInputSourceExtraMapper;
import dev.mesbase.business.production.service.ManufacturedInputSourceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routed H2 fixture, real HTTP/services/XML and stock transactions; no deployed database or ERP proof.
 */
class ManufacturedInputSourceHttpTest {
    private static final String ROOT = "/api/production/input/manufactured-sources";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Configuration
    @Import({ManufacturedInputSourceController.class, ManufacturedInputSourceService.class})
    static class Extra {
        @Bean
        ManufacturedInputSourceExtraMapper manufacturedInputSourceMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(ManufacturedInputSourceExtraMapper.class);
            return new SqlSessionTemplate(factory).getMapper(ManufacturedInputSourceExtraMapper.class);
        }
    }

    private static void prepareParent(JdbcTemplate db) {
        WorkorderBomInputHttpTest.prepare(db, "10", "1", "1");
        db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(100,'TOP-A','TEST ONLY top assembly',1,'ENABLED')");
        db.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,100)");
        db.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,source,create_time,update_time) "
                + "VALUES(7,'TEST-TOP',100,10,1,'RELEASED',0,'LOCAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) "
                + "VALUES(7,'TEST-TOP','1',100,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES(7,7,99,1,1,1,0)");
        db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) "
                + "VALUES(10,99,1,'L001',0,0,'ENABLED'),(20,100,1,'L001',0,0,'ENABLED')");
    }

    private static void selectParent(MockMvc mvc) throws Exception {
        mvc.perform(full(put("/api/production/workorder/7/material")).content(JSON.writeValueAsString(Map.of(
                        "expectedRevision", 0, "bomId", 7, "expectedBomRevision", 1,
                        "warehouseIds", List.of(1), "workCenter", "TEST ONLY parent", "enabled", true))))
                .andExpect(status().isOk());
    }

    private static long produceChild(MockMvc mvc) throws Exception {
        WorkorderBomInputHttpTest.select(mvc, 1, 1);
        long child = WorkorderBomInputHttpTest.input(mvc, "10");
        mvc.perform(full(post("/api/production/executions/" + child + "/actions/start")))
                .andExpect(status().isOk());
        WorkorderBomInputHttpTest.report(mvc, child, "10");
        return child;
    }

    @Test
    void formalChildReceiptFeedsParentAndOnlyTopFormalInboundFinishesTheManufacturingBranch() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            prepareParent(db);
            selectParent(mvc);
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
            long child = produceChild(mvc);
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0)); // Reporting has not put anything in stock.
            WorkorderBomInputHttpTest.receive(mvc, child, "10");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].stockId").value("10"))
                    .andExpect(jsonPath("items[0].executionId").value(Long.toString(child)))
                    .andExpect(jsonPath("items[0].bomId").value("1"))
                    .andExpect(jsonPath("items[0].lineId").isString())
                    .andExpect(jsonPath("items[0].stockActionId").isString())
                    .andExpect(jsonPath("items[0].receivedQty").value("10.000000"))
                    .andExpect(jsonPath("items[0].stockAvailableQty").value("10.000000"));
            mvc.perform(full(get(ROOT)).param("workorderId", "7").param("offset", "1").param("limit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items").isEmpty());

            String key = JSON.readTree(mvc.perform(full(get("/api/production/input/calculate"))
                                    .param("code", "TEST-TOP:P1#L001").param("workorderId", "7")
                                    .param("stockReference", "10").param("outputQty", "10"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .get("calculationKey").asText();
            String command = JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(),
                    "code", "TEST-TOP:P1#L001", "workorderId", "7", "stockReference", "10", "outputQty", "10",
                    "routingId", "1", "routingNodeIds", List.of("11", "12", "13"),
                    "inputKind", "PRODUCTION_INPUT", "calculationKey", key));
            long parent = JSON.readTree(ProductionInputHttpTest.submit(mvc, command)).get("executionId").asLong();
            assertEquals(0, BigDecimal.ZERO.compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=10", BigDecimal.class)));
            mvc.perform(full(post("/api/production/executions/" + parent + "/actions/start")))
                    .andExpect(status().isOk());
            WorkorderBomInputHttpTest.report(mvc, parent, "10");
            assertEquals("RELEASED", db.queryForObject("SELECT status FROM workorder WHERE id=7", String.class));
            assertEquals(0, BigDecimal.ZERO.compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=20", BigDecimal.class)));
            mvc.perform(full(post("/api/production/executions/" + parent + "/inbound"))
                            .content(JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(),
                                    "stockId", "20", "quantity", "10", "reason", "TEST ONLY final top receipt"))))
                    .andExpect(status().isOk());
            assertEquals("COMPLETED", db.queryForObject("SELECT status FROM workorder WHERE id=7", String.class));
            assertEquals("FULLY_RECEIVED", db.queryForObject(
                    "SELECT status FROM mes_production_execution WHERE id=?", String.class, parent));
            assertEquals(0, new BigDecimal("10").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=20", BigDecimal.class)));
            assertEquals(0, db.queryForObject(
                    "SELECT COUNT(*) FROM wm_stock_document WHERE document_type='OTHER_RECEIPT'", Integer.class));
        }
    }

    @Test
    void otherReceiptCannotSubstituteForFormalManufacturingOrOutsourcing() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            prepareParent(db);
            selectParent(mvc);
            var draft = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null);
            String id = draft.get("document").get("id").asText();
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/lines"))
                            .content(StockDocumentHttpTest.line("0", "10", "L001", "10")
                                    .replace("\"materialId\":\"1\"", "\"materialId\":\"99\"")
                                    .replace("\"binCode\":\"BIN1\"", "\"binCode\":null")))
                    .andExpect(status().isOk());
            UnifiedInboundHttpTest.confirm(mvc, id, 1);
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
            var outsource = StockDocumentHttpTest.create(mvc, "OUTSOURCE_RECEIPT", null);
            String outsourceId = outsource.get("document").get("id").asText();
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + outsourceId + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 0)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("STOCK_DOCUMENT_SOURCE_PENDING"));
        }
    }

    @Test
    void sourcesRequireExactChildVersionStockIdentityPermissionAndFactoryScope() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            prepareParent(db);
            selectParent(mvc);
            long child = produceChild(mvc);
            WorkorderBomInputHttpTest.receive(mvc, child, "10");
            mvc.perform(get(ROOT).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A")
                            .param("workorderId", "7"))
                    .andExpect(status().isForbidden());
            mvc.perform(full(get(ROOT)).param("workorderId", "7").param("limit", "101"))
                    .andExpect(status().isBadRequest());
            var other = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B");
            prepareParent(other);
            mvc.perform(ProductionReportHttpTest.full(put("/api/production/workorder/7/material"), "B")
                            .content(JSON.writeValueAsString(Map.of("expectedRevision", 0, "bomId", 7,
                                    "expectedBomRevision", 1, "warehouseIds", List.of(1),
                                    "workCenter", "TEST ONLY other factory", "enabled", true))))
                    .andExpect(status().isOk());
            mvc.perform(ProductionReportHttpTest.full(get(ROOT), "B").param("workorderId", "7"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(1));
            db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) "
                    + "VALUES(8,'TEST-CHILD-V2','2',99,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP)");
            db.update("UPDATE material_bom_item SET child_bom_id=8 WHERE id=7");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
            db.update("UPDATE material_bom_item SET child_bom_id=NULL WHERE id=7");
            mvc.perform(full(get(ROOT)).param("workorderId", "7"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("STOCK_DOCUMENT_SOURCE_PENDING"));
            db.update("UPDATE material_bom_item SET child_bom_id=1 WHERE id=7");
            db.update("UPDATE wm_inventory SET batch_no='DIFFERENT' WHERE id=10");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
        }
    }

    @Test
    void partialReceiptsExposeSharedStockAvailabilityWithoutInventingPerReceiptAllocations() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            prepareParent(db);
            selectParent(mvc);
            long child = produceChild(mvc);
            WorkorderBomInputHttpTest.receive(mvc, child, "4");
            WorkorderBomInputHttpTest.receive(mvc, child, "6");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items[0].receivedQty").value("6.000000"))
                    .andExpect(jsonPath("items[1].receivedQty").value("4.000000"))
                    .andExpect(jsonPath("items[0].stockAvailableQty").value("10.000000"))
                    .andExpect(jsonPath("items[1].stockAvailableQty").value("10.000000"));
            db.update("UPDATE wm_stock_action SET source_id='wrong-document' WHERE id="
                    + "(SELECT stock_action_id FROM wm_stock_document_line WHERE stock_id=10 ORDER BY id DESC LIMIT 1)");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(1));
            db.update("UPDATE wm_inventory SET available_quantity=0 WHERE id=10");
            mvc.perform(full(get(ROOT)).param("workorderId", "7")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
        }
    }
}
