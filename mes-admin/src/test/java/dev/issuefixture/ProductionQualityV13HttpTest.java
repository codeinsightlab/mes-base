package dev.issuefixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Synthetic five-node H2 SQL/HTTP, never claimed as a physical-product tracking system.
 */
class ProductionQualityV13HttpTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String Q = "/api/quality/defects", N = "/api/production/report-quantities/executions/1";

    static AnnotationConfigWebApplicationContext context(int... reports) throws Exception {
        var c = ProductionReceiptHttpTest.context();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            j.update("DELETE FROM mes_production_report");
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
        facts.put("discoveredNodeId", "15");
        facts.put("destinationType", "SAME_NODE");
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

    static String command(String action, String parent, String scrap, String rework, String processed, String recovered,
                          String failed) throws Exception {
        var n = JSON.createObjectNode();
        n.put("requestId", UUID.randomUUID().toString());
        n.put("expectedRevision", 1);
        n.put("action", action);
        if (parent == null) n.putNull("parentId");
        else n.put("parentId", parent);
        n.put("scrapQty", scrap);
        n.put("reworkQty", rework);
        n.put("processedQty", processed);
        n.put("recoveredQty", recovered);
        n.put("failedQty", failed);
        n.put("occurredAt", "2026-01-01T10:00:00");
        n.put("reason", "TEST ONLY V13 quality treatment");
        return n.toString();
    }

    static JsonNode execute(MockMvc m, String id, String body) throws Exception {
        return JSON.readTree(m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    static String event(JsonNode view, String action) {
        for (var e : view.get("events")) if (e.get("actionType").asText().equals(action)) return e.get("id").asText();
        throw new AssertionError("missing event");
    }

    static String start(MockMvc m, String id) throws Exception {
        var allocated = execute(m, id, command("ALLOCATE", null, "6", "4", "0", "0", "0"));
        return event(execute(m, id, command("START", event(allocated, "ALLOCATE"), "0", "4", "0", "0", "0")), "START");
    }

    @Test
    void pendingAtDAllowsUpstreamReportsAndSafeNegativeCorrectionButProtectsItsBasis() throws Exception {
        try (var c = context(99, 98, 97, 96, 95)) {
            var m = MaterialIssueHttpTest.mvc(c);
            defect(m, 14, "2", null, null);
            for (int n = 11; n <= 13; n++)
                m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(n, "1")))
                        .andExpect(status().isOk());
            var blocked = m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(14, "1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("QUALITY_ATTRIBUTION_REQUIRED")).andReturn().getResponse()
                    .getContentAsString();
            assertTrue(blocked.contains("D") && blocked.contains("先核实"));
            var j = ProductionReceiptHttpTest.db(c);
            Long original = j.queryForObject("SELECT id FROM mes_production_report WHERE routing_node_id=14 AND record_type='REPORT'", Long.class);
            String endpoint = "/api/production/reports/" + original + "/adjustments";
            m.perform(full(post(endpoint), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-1\",\"reason\":\"TEST ONLY safe negative\"}"))
                    .andExpect(status().isOk());
            m.perform(full(post(endpoint), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-94\",\"reason\":\"TEST ONLY breaks basis\"}"))
                    .andExpect(status().isConflict());
            var q = quantities(m);
            assertTrue(q.get("nodes").get(2).get("positiveReportBlockReason").isNull());
            assertTrue(q.get("nodes").get(3).get("positiveReportBlockReason").asText().contains("D"));
            assertTrue(q.get("nodes").get(4).get("positiveReportBlockReason").asText().contains("D"));
            Long a = j.queryForObject("SELECT MIN(id) FROM mes_production_report WHERE routing_node_id=11", Long.class);
            m.perform(full(post("/api/production/reports/" + a + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-2\",\"reason\":\"TEST ONLY upstream breaks downstream\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void controlledCorrectionsRespectParentBudgetsAndPreserveOriginalEvents() throws Exception {
        try (var c = context(100, 100, 100, 100, 100); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var allocation = execute(m, id, command("ALLOCATE", null, "2", "8", "0", "0", "0"));
            String a = event(allocation, "ALLOCATE");
            var started = execute(m, id, command("START", a, "0", "6", "0", "0", "0"));
            String st = event(started, "START");
            var result = execute(m, id, command("RESULT", st, "0", "0", "4", "3", "1"));
            String r = event(result, "RESULT");
            var originals = j.queryForList("SELECT * FROM mes_quality_treatment WHERE declaration_id=? ORDER BY id", id);
            String cancel = command("CANCEL_START", st, "0", "2", "0", "0", "0");
            var v = execute(m, id, cancel);
            execute(m, id, cancel);
            amount(v, "startedQty", "4");
            amount(v, "inProgressQty", "0");
            m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(command("CANCEL_START", st, "0", "1", "0", "0", "0")))
                    .andExpect(status().isConflict());
            v = execute(m, id, command("CANCEL_ALLOC", a, "2", "4", "0", "0", "0"));
            amount(v, "allocatedReworkQty", "4");
            amount(v, "pendingDispositionQty", "6");
            amount(v, "netDefectQty", "7");
            m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(command("CANCEL_ALLOC", a, "0", "1", "0", "0", "0")))
                    .andExpect(status().isConflict());
            String correction = command("CORRECT_RESULT", r, "0", "0", "3", "1", "2");
            var correctionA = pool.submit(() -> execute(m, id, correction));
            var correctionB = pool.submit(() -> execute(m, id, correction));
            v = correctionA.get();
            assertEquals(v, correctionB.get());
            amount(v, "processedQty", "3");
            amount(v, "recoveredQty", "1");
            amount(v, "failedQty", "2");
            amount(v, "inProgressQty", "1");
            amount(quantities(m).get("nodes").get(4), "effectiveOutputQty", "91");
            execute(m, id, command("CANCEL_START", st, "0", "1", "0", "0", "0"));
            execute(m, id, command("CANCEL_ALLOC", a, "0", "1", "0", "0", "0"));
            m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(command("CORRECT_RESULT", r, "0", "0", "4", "2", "2")))
                    .andExpect(status().isConflict());
            v = execute(m, id, command("CORRECT_RESULT", r, "0", "0", "3", "2", "1"));
            amount(v, "netDefectQty", "8");
            amount(v, "startedQty", "3");
            amount(v, "processedQty", "3");
            for (var row : originals)
                assertEquals(row, j.queryForMap("SELECT * FROM mes_quality_treatment WHERE id=?", row.get("ID")));
        }
    }

    @Test
    void casesABFullHistoryAndRecoveryDoNotRewriteNormalReports() throws Exception {
        try (var c = context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            var before = j.queryForList("SELECT * FROM mes_production_report ORDER BY id");
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var q = quantities(m);
            amount(q.get("nodes").get(1), "netDefectQty", "10");
            for (int i = 2; i < 5; i++) amount(q.get("nodes").get(i), "historicalOverrunQty", "10");
            amount(q.get("nodes").get(4), "effectiveOutputQty", "90");
            var parent = start(m, id);
            execute(m, id, command("RESULT", parent, "0", "0", "4", "4", "0"));
            q = quantities(m);
            amount(q.get("nodes").get(1), "defectQty", "10");
            amount(q.get("nodes").get(1), "recoveredQty", "4");
            for (int i = 2; i < 5; i++) amount(q.get("nodes").get(i), "historicalOverrunQty", "6");
            amount(q.get("nodes").get(4), "effectiveOutputQty", "94");
            assertEquals(before, j.queryForList("SELECT * FROM mes_production_report ORDER BY id"));
        }
    }

    @Test
    void caseCPartialDownstreamOnlyRestoresCapacityAndCaseGRejectsNewOverrun() throws Exception {
        try (var c = context(100, 100, 100, 80, 60)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var q = quantities(m);
            amount(q.get("nodes").get(3), "availableQty", "10");
            amount(q.get("nodes").get(4), "availableQty", "20");
            amount(q.get("nodes").get(4), "effectiveOutputQty", "60");
            execute(m, id, command("RESULT", start(m, id), "0", "0", "4", "4", "0"));
            q = quantities(m);
            amount(q.get("nodes").get(3), "availableQty", "14");
            amount(q.get("nodes").get(4), "effectiveOutputQty", "60");
            m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(14, "14")))
                    .andExpect(status().isOk());
            m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(14, "1")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void caseDRequiresReviewedIndependentFactsAndOverlapIsNeverCountedTwice() throws Exception {
        try (var c = context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var b = defect(m, 12, "10", "INDEPENDENT", null);
            defect(m, 13, "3", "OVERLAP", b);
            amount(quantities(m).get("nodes").get(4), "effectiveOutputQty", "90");
            defect(m, 13, "3", "INDEPENDENT", null);
            amount(quantities(m).get("nodes").get(2), "defectQty", "6");
            amount(quantities(m).get("nodes").get(2), "directDefectQty", "3");
            amount(quantities(m).get("nodes").get(4), "effectiveOutputQty", "87");
            defect(m, 14, "2", null, null);
            var q = quantities(m);
            assertEquals(true, q.get("writable").asBoolean());
            amount(q.get("nodes").get(3), "unverifiedDefectQty", "2");
        }
    }

    @Test
    void caseEPartialSuccessFailedPieceIsScrappedAndCannotBeRecovered() throws Exception {
        try (var c = context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var parent = start(m, id);
            var v = execute(m, id, command("RESULT", parent, "0", "0", "4", "3", "1"));
            amount(v, "failedQty", "1");
            amount(v, "netDefectQty", "7");
            amount(quantities(m).get("nodes").get(4), "effectiveOutputQty", "93");
            m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(command("RESULT", parent, "0", "0", "1", "1", "0")))
                    .andExpect(status().isConflict());
            m.perform(full(post(Q + "/" + id + "/void"), "A").content("{\"requestKey\":\"no-erase-rework\",\"expectedRevision\":1,\"revisionReason\":\"TEST ONLY forbidden erase\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void caseFHistoricalReceiptRemainsWhileLateQualityCreatesExcess() throws Exception {
        try (var c = context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            ProductionReceiptHttpTest.seed(j, "98");
            defect(m, 12, "10", "INDEPENDENT", null);
            m.perform(full(get("/api/production/receipts/summary").param("executionId", "1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("excess").value("8.000000"))
                    .andExpect(jsonPath("canReceive").value(false));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
        }
    }

    @Test
    void caseHConcurrentReplayRestoresOnlyOnceAndOtherActionCannotOverconsume() throws Exception {
        try (var c = context(100, 100, 100, 100, 100); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var parent = start(m, id);
            var body = command("RESULT", parent, "0", "0", "4", "4", "0");
            var one = pool.submit(() -> execute(m, id, body));
            var two = pool.submit(() -> execute(m, id, body));
            one.get();
            two.get();
            amount(quantities(m).get("nodes").get(4), "effectiveOutputQty", "94");
            var j = ProductionReceiptHttpTest.db(c);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='RESULT'", Integer.class));
            m.perform(full(post(Q + "/" + id + "/treatment"), "A").content(command("RESULT", parent, "0", "0", "4", "4", "0")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void sameCommandIdentityStaysInItsFactoryAndReadOnlyUsersCannotConfirm() throws Exception {
        try (var c = context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = defect(m, 12, "2", "INDEPENDENT", null);
            var body = (ObjectNode) JSON.readTree(ProductionDefectHttpTest.command(UUID.randomUUID()
                    .toString(), 0, "2"));
            var facts = (ObjectNode) body.get("facts");
            facts.put("affectedNodeId", "12");
            facts.put("destinationType", "SAME_NODE");
            var b = ProductionDefectHttpTest.add(m, body.toString(), "B");
            var allocation = command("ALLOCATE", null, "0", "2", "0", "0", "0");
            execute(m, a, allocation);
            m.perform(full(post(Q + "/" + b + "/treatment"), "B").content(allocation)).andExpect(status().isOk());
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='ALLOCATE'", Integer.class));
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='ALLOCATE'", Integer.class));
            m.perform(post(Q + "/" + a + "/treatment").header("Authorization", "Bearer CRUD")
                    .header("X-Factory-Id", "A")
                    .contentType("application/json").content(allocation)).andExpect(status().isForbidden());
            m.perform(full(get(Q + "/" + a + "/treatment"), "B")).andExpect(status().isConflict());
        }
    }

}
