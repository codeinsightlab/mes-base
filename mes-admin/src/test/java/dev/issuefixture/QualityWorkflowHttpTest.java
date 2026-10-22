package dev.issuefixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mesbase.business.quality.controller.QualityDispositionController;
import dev.mesbase.business.quality.service.QualityDispositionService;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY routed H2: current assessments, return-to-node and warehouse disposition.
 */
class QualityWorkflowHttpTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String Q = "/api/quality/defects", N = "/api/production/report-quantities/executions/1";

    static AnnotationConfigWebApplicationContext context(int... reports) throws Exception {
        return context(new Class<?>[0], reports);
    }

    static AnnotationConfigWebApplicationContext context(Class<?>[] extra, int... reports) throws Exception {
        var modules = new ArrayList<Class<?>>(List.of(extra));
        modules.addAll(List.of(ProductionReportingHttpTest.Extra.class, QualityDispositionHttpTest.Validation.class,
                QualityDispositionService.class,
                QualityDispositionController.class,
                WmInventoryServiceImpl.class));
        var c = ProductionWarehouseFixture.context(modules.toArray(Class<?>[]::new));
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            j.update("DELETE FROM mes_production_report");
            j.update("UPDATE wm_warehouse SET warehouse_type='DEFECTIVE' WHERE id=1");
            j.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,display_order,operation_name) SELECT 14,routing_id,'D',operation_id,4,operation_name FROM routing_node WHERE id=13");
            j.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,display_order,operation_name) SELECT 15,routing_id,'E',operation_id,5,operation_name FROM routing_node WHERE id=13");
            j.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,13,14),(1,14,15)");
            j.update("INSERT INTO mes_execution_path_node(execution_id,sequence_no,routing_node_id) VALUES(1,4,14),(1,5,15)");
            for (int i = 0; i < reports.length; i++)
                j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(1,?,'REPORT',?,100,1,CURRENT_TIMESTAMP,'TEST ONLY V13 normal immutable report',CURRENT_TIMESTAMP)", 11 + i, reports[i]);
        }
        return c;
    }

    static String defect(MockMvc m, long node, String qty, String type, String related) throws Exception {
        var body = (ObjectNode) JSON.readTree(ProductionDefectHttpTest.command(UUID.randomUUID().toString(), 0, qty));
        var facts = (ObjectNode) body.get("facts");
        facts.put("affectedNodeId", Long.toString(node));
        if (type == null) body.remove("assessment");
        else {
            var assessment = body.putObject("assessment");
            assessment.put("type", type);
            assessment.put("basis", "TEST ONLY reviewed attribution, explicit distinct material evidence");
            if (related != null) assessment.put("relatedDeclarationId", related);
        }
        return ProductionDefectHttpTest.add(m, body.toString(), "A");
    }

    static JsonNode quantities(MockMvc m) throws Exception {
        return JSON.readTree(m.perform(full(get(N), "A")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    static void amount(JsonNode n, String key, String value) {
        assertEquals(0, new BigDecimal(value).compareTo(new BigDecimal(n.get(key).asText())), key);
    }


    static String assessmentCommand() {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":1,\"reason\":\"TEST ONLY assessment\",\"assessment\":{\"type\":\"INDEPENDENT\",\"basis\":\"TEST ONLY distinct physical evidence\"}}";
    }

    static JsonNode assess(MockMvc mvc, String id, String body) throws Exception {
        return JSON.readTree(mvc.perform(full(post(Q + "/" + id + "/assessments"), "A").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    static JsonNode transfer(MockMvc mvc, String id, String quantity) throws Exception {
        return QualityDispositionHttpTest.dispose(mvc, id, QualityDispositionHttpTest.command(
                UUID.randomUUID().toString(), "WAREHOUSE", quantity, "1"));
    }

    @Test
    void assessmentUsesServerTimeAndReplayKeepsOneImmutableFact() throws Exception {
        try (var c = context(100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var id = defect(mvc, 12, "10", null, null);
            var body = assessmentCommand();
            var first = assess(mvc, id, body);
            var repeated = assess(mvc, id, body);
            assertEquals(first.get("events"), repeated.get("events"));
            assertEquals(1, first.get("events").size());
            var event = first.get("events").get(0);
            assertEquals(false, event.has("confirmedBy"));
            assertEquals(false, event.has("confirmedByName"));
            assertEquals(false, event.has("occurredAt"));
            assertEquals(true, event.hasNonNull("recordedAt"));
            assertEquals("1", event.get("operatorUserId").asText());
            assertEquals("PENDING", first.get("state").asText());
            mvc.perform(full(post(Q + "/" + id + "/assessments"), "B").content(body))
                    .andExpect(status().is4xxClientError());
            mvc.perform(post(Q + "/" + id + "/assessments").header("Authorization", "Bearer NONE")
                            .header("X-Factory-Id", "A").contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unreadableDispositionDoesNotBlockQueriesOrAllowCommands() throws Exception {
        try (var c = context(new Class<?>[]{WorkOrderProgressHttpTest.Extra.class, QualityV2HttpTest.Extra.class}, 100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var id = defect(mvc, 12, "10", "INDEPENDENT", null);
            for (var action : new String[]{"ALLOCATE", "START", "RESULT", "CANCEL_ALLOC", "CANCEL_START", "CORRECT_RESULT"})
                mvc.perform(full(post(Q + "/" + id + "/treatment"), "A").content("{\"action\":\"" + action + "\"}"))
                        .andExpect(status().is4xxClientError());
            assertEquals(1, ProductionWarehouseFixture.db(c).queryForObject(
                    "SELECT COUNT(*) FROM mes_quality_treatment WHERE declaration_id=?", Integer.class, id));
            var healthy = defect(mvc, 12, "1", "INDEPENDENT", null);
            ProductionWarehouseFixture.db(c)
                    .update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,12,'ALLOCATE',1,CURRENT_TIMESTAMP,'TEST ONLY rejected imported workflow')",
                            UUID.randomUUID().toString(), "x".repeat(64), id);
            var list = JSON.readTree(mvc.perform(full(get(Q), "A")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals(2, list.path("total").asInt());
            assertEquals(healthy, list.path("items").get(0).path("declarationId").asText());
            assertEquals("PENDING", list.path("items").get(0).path("processingState").asText());
            assertEquals("UNRESOLVED", list.path("items").get(1).path("processingState").asText());
            assertEquals(true, list.path("items").get(1).path("pendingQuantity").isNull());
            var detail = JSON.readTree(mvc.perform(full(get(Q + "/" + id + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("UNRESOLVED", detail.path("state").asText());
            assertEquals(true, detail.path("pendingDispositionQty").isNull());
            assertEquals(2, detail.path("events").size());
            mvc.perform(full(get("/api/quality/treatments/recent"), "A")).andExpect(status().isOk());
            mvc.perform(full(get("/api/production/workorder-progress"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].qualityUntreatedQty").doesNotExist());
            mvc.perform(full(get(Q + "/statistics"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].returnedQty").doesNotExist())
                    .andExpect(jsonPath("items[0].invalidActions").value(1));
            mvc.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                            .content(QualityDispositionHttpTest.command(UUID.randomUUID()
                                    .toString(), "RETURN_NODE", "1", null)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("QUALITY_DATA_INVALID"));
            assertEquals(3, ProductionWarehouseFixture.db(c)
                    .queryForObject("SELECT COUNT(*) FROM mes_quality_treatment", Integer.class));
        }
    }
}
