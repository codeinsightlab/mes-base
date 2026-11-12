package dev.issuefixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TEST ONLY routed H2; no configured MySQL database or running user service is contacted.
 */
class ProductionMaterialReturnHttpTest {
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode issue(MockMvc m, String lot) throws Exception {
        return json.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m,
                UUID.randomUUID().toString(), lot, "10")));
    }

    private String returnBody(JdbcTemplate j, long document, long stock, String qty) {
        var line = j.queryForObject("SELECT id FROM wm_stock_document_line WHERE document_id=? AND stock_id=?", Long.class, document, stock);
        return json.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(), "documentId", document,
                "documentLineId", line, "stockId", stock, "quantity", qty,
                "expectedQuantity", j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, stock),
                "expectedAvailable", j.queryForObject("SELECT available_quantity FROM wm_inventory WHERE id=?", BigDecimal.class, stock),
                "reason", "TEST ONLY pre-start production material return"));
    }

    private void returnMaterial(MockMvc m, long document, String body) throws Exception {
        m.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/returns"))
                .content(body)).andExpect(status().isOk());
    }

    private void report(MockMvc m, String code, long node, String qty, boolean accepted) throws Exception {
        var result = m.perform(full(post("/api/production/reporting")).content(json.writeValueAsString(
                Map.of("requestId", UUID.randomUUID().toString(), "executionCode", code,
                        "routingNodeId", node, "reportQty", qty))));
        if (accepted) result.andExpect(status().isOk());
        else assertTrue(result.andReturn().getResponse().getStatus() >= 400);
    }

    @Test
    void partialReturnLimitsAllReportingEntrypointsAndBalancesCompletionWithoutChangingOriginalInput() throws Exception {
        try (var c = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var first = issue(m, "L001");
            var other = issue(m, "L002");
            String code = first.get("executionCode").asText();
            long document = first.get("documentId").asLong();
            String body = returnBody(j, document, 1, "2");
            returnMaterial(m, document, body);
            returnMaterial(m, document, body);
            m.perform(full(get("/api/production/report-quantities/executions/{code}", code)))
                    .andExpect(status().isOk()).andExpect(jsonPath("confirmedInputQty").value("10.000000"))
                    .andExpect(jsonPath("productionInputQty").value("9.000000"))
                    .andExpect(jsonPath("materialReturnQty").value("1.000000"));
            m.perform(full(get("/api/production/report-quantities/executions/{code}", other.get("executionCode")
                            .asText())))
                    .andExpect(status().isOk()).andExpect(jsonPath("productionInputQty").value("10.000000"));
            report(m, code, 11, "10", false);
            String supplement = ProductionReportHttpTest.supplement(11, "10")
                    .replace("\"executionCode\":\"1\"", "\"executionCode\":\"" + code + "\"");
            assertTrue(m.perform(full(post("/api/production/reports/supplements")).content(supplement))
                    .andReturn().getResponse().getStatus() >= 400);
            for (long node : List.of(11L, 12L, 13L)) report(m, code, node, "9", true);
            WorkorderBomInputHttpTest.receive(m, code, "9");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?", String.class, code));
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject("SELECT output_qty FROM mes_material_issue_input WHERE document_id=?", BigDecimal.class, document)));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='RETURN'", Integer.class));
        }
    }

    @Test
    void tenConfirmedBalancesOneReturnedOneNetDefectAndEightFormallyReceived() throws Exception {
        var extra = new java.util.ArrayList<Class<?>>(java.util.Arrays.asList(QualityDispositionHttpTest.EXTRA));
        extra.add(ProductionReportingHttpTest.Extra.class);
        extra.add(ProductionDefectHttpTest.Extra.class);
        try (var c = ProductionInputHttpTest.context(extra.toArray(Class<?>[]::new))) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            j.update("INSERT INTO wm_warehouse(id,code,name,status,source,warehouse_type) VALUES(2,'TEST-Q','TEST ONLY quality warehouse','ENABLED','LOCAL','DEFECTIVE')");
            var input = issue(m, "L001");
            String code = input.get("executionCode").asText();
            long document = input.get("documentId").asLong();
            returnMaterial(m, document, returnBody(j, document, 1, "2"));
            for (long node : List.of(11L, 12L, 13L)) report(m, code, node, "9", true);
            String declaration = ProductionDefectHttpTest.add(m, ProductionDefectHttpTest.command("TEST-RETURN-DEFECT", 0, "1")
                    .replace("\"executionCode\":\"1\"", "\"executionCode\":\"" + code + "\""), "A");
            WorkorderBomInputHttpTest.receive(m, code, "8");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?", String.class, code));
            QualityDispositionHttpTest.dispose(m, declaration, QualityDispositionHttpTest.command(UUID.randomUUID()
                    .toString(), "WAREHOUSE", "1", "2"));
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?", String.class, code));
            m.perform(full(get("/api/production/report-quantities/executions/{code}", code)))
                    .andExpect(status().isOk()).andExpect(jsonPath("confirmedInputQty").value("10.000000"))
                    .andExpect(jsonPath("materialReturnQty").value("1.000000"))
                    .andExpect(jsonPath("nodes[2].effectiveOutputQty").value("8.000000"));
        }
    }

    @Test
    void fullReturnBlocksStartAndReportingAndKeepsTheExecutionOpen() throws Exception {
        try (var c = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = issue(m, "L001");
            String code = input.get("executionCode").asText();
            long document = input.get("documentId").asLong();
            returnMaterial(m, document, returnBody(j, document, 1, "20"));
            assertTrue(m.perform(full(post("/api/production/executions/{code}/actions/start", code))).andReturn()
                    .getResponse().getStatus() >= 400);
            report(m, code, 11, "1", false);
            assertEquals("READY", j.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?", String.class, code));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion WHERE execution_code=?", Integer.class, code));
        }
    }

    @Test
    void multipleLocationsAndRepeatedInputsArePooledByDemandBeforeTakingTheMaterialBottleneck() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionInputHttpTest.multiMaterial(j);
            j.update("UPDATE wm_inventory SET quantity=3,available_quantity=3 WHERE id=1");
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(4,1,1,37,37,'ENABLED','B01','BIN4')");
            String code = "WO1:C#C01$M1#B01";
            JsonNode first = null;
            for (int i = 0; i < 2; i++) {
                var input = json.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.confirmCommand(m,
                        UUID.randomUUID().toString(), code, "10")));
                if (first == null) first = input;
            }
            long document = first.get("documentId").asLong();
            // Only this original stock line is returned; the other locations still support the same demand.
            returnMaterial(m, document, returnBody(j, document, 4, "4"));
            m.perform(full(get("/api/production/report-quantities/executions/{code}", code)))
                    .andExpect(status().isOk()).andExpect(jsonPath("confirmedInputQty").value("20.000000"))
                    .andExpect(jsonPath("productionInputQty").value("18.000000"));
        }
    }

    @Test
    void subsequentFormalInputUsesTheFrozenBomRatherThanAnInflatedGrossIssueRatio() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var first = issue(m, "L001");
            String code = first.get("executionCode").asText();
            long document = first.get("documentId").asLong();
            returnMaterial(m, document, returnBody(j, document, 1, "2"));
            issue(m, "L001");
            m.perform(full(get("/api/production/report-quantities/executions/{code}", code)))
                    .andExpect(status().isOk()).andExpect(jsonPath("confirmedInputQty").value("20.000000"))
                    .andExpect(jsonPath("productionInputQty").value("20.000000"))
                    .andExpect(jsonPath("materialReturnQty").value("0.000000"));
        }
    }

    @Test
    void concurrentReturnAndReportingCannotCommitAnOverproduction() throws Exception {
        try (var c = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = issue(m, "L001");
            String code = input.get("executionCode").asText();
            long document = input.get("documentId").asLong();
            String back = returnBody(j, document, 1, "2");
            String production = json.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(),
                    "executionCode", code, "routingNodeId", 11, "reportQty", "10"));
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> {
                    barrier.await();
                    return m.perform(full(post(ProductionInputHttpTest.ROOT +
                                    "/documents/" + document + "/returns")).content(back)).andReturn().getResponse()
                            .getStatus();
                });
                var b = pool.submit(() -> {
                    barrier.await();
                    return m.perform(full(post("/api/production/reporting"))
                            .content(production)).andReturn().getResponse().getStatus();
                });
                int returned = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
                int reported = b.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue((returned == 200 && reported >= 400) || (reported == 200 && returned >= 400));
            }
        }
    }

    @Test
    void noRoutingInboundUsesTheSameNetInputAndRefusesTheOriginalGrossQuantity() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE workorder SET routing_id=NULL WHERE id=1");
            j.update("DELETE FROM routing_product WHERE material_id=99");
            String code = "WO1:M1#L001";
            var calc = json.readTree(m.perform(full(get("/api/production/combinations/calculate")).param("code", code)
                            .param("quantity", "10"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var input = json.readTree(ProductionInputHttpTest.submit(m, json.writeValueAsString(Map.of(
                    "requestId", UUID.randomUUID().toString(), "code", code, "outputQty", "10",
                    "routingNodeIds", List.of(), "calculationKey", calc.get("calculationKey").asText()))));
            long document = input.get("documentId").asLong();
            returnMaterial(m, document, returnBody(j, document, 1, "2"));
            assertThrows(AssertionError.class, () -> WorkorderBomInputHttpTest.receive(m, code, "10"));
            WorkorderBomInputHttpTest.receive(m, code, "9");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?", String.class, code));
            assertEquals(0, new BigDecimal("9").compareTo(j.queryForObject("SELECT accepted_qty FROM mes_execution_completion WHERE execution_code=?", BigDecimal.class, code)));
        }
    }
}
