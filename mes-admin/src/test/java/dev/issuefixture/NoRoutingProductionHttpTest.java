package dev.issuefixture;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY routed H2: actual aggregate commands, no invented routes, reports or receipt facts.
 */
class NoRoutingProductionHttpTest {
    private final ObjectMapper json = new ObjectMapper();

    private void prepare(JdbcTemplate db) {
        db.update("DELETE FROM routing_product WHERE material_id=99");
        db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'TEST-C','TEST ONLY second component',1,'ENABLED')");
        db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,sort) VALUES(2,1,2,1,1,1)");
        db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(3,2,1,100,100,'ENABLED','C01','BIN-C')");
    }

    private String createBody() {
        var command = new LinkedHashMap<String, Object>();
        command.put("ruleCode", "TEST-WORKORDER");
        command.put("productId", "99");
        command.put("plannedQty", "6");
        command.put("routingId", null);
        command.put("material", Map.of("expectedRevision", "0", "bomId", "1", "expectedBomRevision", "1",
                "warehouseIds", List.of(), "workCenter", "", "enabled", true, "adjustmentsConfirmed", false,
                "items", List.of(Map.of("bomItemId", "1", "materialId", "1", "unitId", "1", "totalRequiredQty", "12", "manuallyAdjusted", false),
                        Map.of("bomItemId", "2", "materialId", "2", "unitId", "1", "totalRequiredQty", "6", "manuallyAdjusted", false))));
        return json.writeValueAsString(command);
    }

    private String confirm(MockMvc mvc, String code, String quantity) throws Exception {
        var calculation = json.readTree(mvc.perform(full(get("/api/production/combinations/calculate"))
                        .param("code", code).param("quantity", quantity)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var command = new LinkedHashMap<String, Object>();
        command.put("requestId", UUID.randomUUID().toString());
        command.put("code", code);
        command.put("outputQty", quantity);
        command.put("routingId", null);
        command.put("routingNodeIds", List.of());
        command.put("calculationKey", calculation.get("calculationKey").asText());
        return json.writeValueAsString(command);
    }

    private JsonNode createAndRelease(MockMvc mvc) throws Exception {
        var order = json.readTree(mvc.perform(full(post("/api/production/workorder")).content(createBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("workorder.routingId").isEmpty())
                .andReturn().getResponse().getContentAsString()).get("workorder");
        mvc.perform(full(post("/api/production/workorder/" + order.get("id").asText() + "/actions/release"))
                        .content(json.writeValueAsString(Map.of("revision", order.get("revision").asInt()))))
                .andExpect(status().isOk());
        return order;
    }

    private String generate(MockMvc mvc, JsonNode order) throws Exception {
        return json.readTree(mvc.perform(full(post("/api/production/combinations"))
                                .content(json.writeValueAsString(Map.of("workorderId", order.get("id").asText(), "quantity", "6"))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("combination")
                .get("code").asText();
    }

    @Test
    void realNewWorkorderIssuesMultipleMaterialsAndReceivesInPartsWithoutAnyRoutingOrReport() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            prepare(db);
            var mvc = MaterialIssueHttpTest.mvc(context);
            var order = createAndRelease(mvc);
            String code = generate(mvc, order);
            mvc.perform(full(get("/api/production/input/preview")).param("code", code)
                            .param("workorderId", order.get("id").asText()))
                    .andExpect(status().isOk()).andExpect(jsonPath("definition").isEmpty());
            String firstCommand = confirm(mvc, code, "2");
            var first = json.readTree(ProductionInputHttpTest.submit(mvc, firstCommand));
            long execution = first.get("executionId").asLong();
            assertNull(db.queryForObject("SELECT routing_id FROM mes_production_execution WHERE id=?", Long.class, execution));
            mvc.perform(full(get("/api/production/identity")).param("code", code))
                    .andExpect(status().isOk()).andExpect(jsonPath("routingId").isEmpty());
            mvc.perform(full(get("/api/wm/inbound-documents/executions/" + execution + "/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("reportingRequired").value(false))
                    .andExpect(jsonPath("remaining").value("2.000000"));
            mvc.perform(full(get("/api/production/reporting/context")).param("executionId", Long.toString(execution)))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantities.writable").value(false))
                    .andExpect(jsonPath("quantities.nodes.length()").value(0));
            mvc.perform(full(post("/api/production/reporting")).content(json.writeValueAsString(Map.of(
                            "requestId", UUID.randomUUID()
                                    .toString(), "executionId", execution, "routingNodeId", 11, "reportQty", "1"))))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("errorCode").value("REPORT_PATH_REQUIRED"));
            mvc.perform(full(get("/api/production/executions/" + execution + "/path")))
                    .andExpect(status().isOk()).andExpect(jsonPath("routingId").isEmpty());
            mvc.perform(full(get("/api/production/executions/" + execution + "/assignment-nodes")))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes.length()").value(0));
            mvc.perform(full(get("/api/production/workorder")))
                    .andExpect(status().isOk());
            mvc.perform(full(get("/api/production/report-quantities/workorders/" + order.get("id").asText())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].executionId").value(Long.toString(execution)))
                    .andExpect(jsonPath("items[0].routingId").isEmpty())
                    .andExpect(jsonPath("items[0].lastNodeReportedQty").isEmpty());
            WorkorderBomInputHttpTest.receive(mvc, execution, "1");
            assertEquals("RUNNING", db.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals("RELEASED", db.queryForObject("SELECT status FROM workorder WHERE id=?", String.class, order.get("id")
                    .asLong()));
            long outputStock = db.queryForObject("SELECT s.id FROM wm_inventory s JOIN mes_production_execution e ON e.product_id=s.material_id AND e.batch_no=s.batch_no WHERE e.id=?", Long.class, execution);
            long documents = db.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Long.class);
            mvc.perform(full(post("/api/production/executions/" + execution + "/inbound"))
                            .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                                            .toString(), "stockId", outputStock,
                                    "quantity", "2", "reason", "TEST ONLY reject excess receipt"))))
                    .andExpect(status().is4xxClientError());
            assertEquals(documents, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Long.class));
            assertEquals(0, BigDecimal.ONE.compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, outputStock)));
            mvc.perform(full(post("/api/wm/outbound-documents/" + first.get("documentId").asText() + "/actions/void")))
                    .andExpect(status().isConflict());
            // Later master-data publication does not rewrite this execution's frozen no-routing basis.
            db.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,99)");
            var second = json.readTree(ProductionInputHttpTest.submit(mvc, confirm(mvc, code, "4")));
            assertEquals(execution, second.get("executionId").asLong());
            WorkorderBomInputHttpTest.receive(mvc, execution, "2");
            assertEquals("RUNNING", db.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            WorkorderBomInputHttpTest.receive(mvc, execution, "3");
            assertEquals("FULLY_RECEIVED", db.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals("COMPLETED", db.queryForObject("SELECT status FROM workorder WHERE id=?", String.class, order.get("id")
                    .asLong()));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_id=?", Integer.class, execution));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_execution_path WHERE id=?", Integer.class, execution));
            assertEquals(0, new BigDecimal("6").compareTo(db.queryForObject("SELECT SUM(output_qty) FROM mes_material_issue_input WHERE execution_id=?", BigDecimal.class, execution)));
            mvc.perform(full(post("/api/production/input")).content(firstCommand)).andExpect(status().isOk());
            mvc.perform(full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_CLOSED"));
        }
    }

    @Test
    void existingPublishedApplicableRoutingCannotBeOmittedToBypassReporting() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            prepare(db);
            db.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,99)");
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(full(post("/api/production/workorder")).content(createBody()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("WORKORDER_INVALID"));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
        }
    }
}
