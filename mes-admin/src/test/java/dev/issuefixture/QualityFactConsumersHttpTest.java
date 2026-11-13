package dev.issuefixture;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Same anomalous facts through real controllers and routed in-memory H2 XML; no shared services.
 */
class QualityFactConsumersHttpTest {
    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "wrong-node", "wrong-execution", "missing-overlap", "over-transfer",
            "null-action", "unknown-action", "lower-action", "null-quantity", "over-return"})
    void allConsumersKeepAnomalousFactsReadableWithoutCertainQuantities(String anomaly) throws Exception {
        try (var context = QualityWorkflowHttpTest.context(
                new Class<?>[]{WorkOrderProgressHttpTest.Extra.class, QualityV2HttpTest.Extra.class},
                100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            String id = QualityWorkflowHttpTest.defect(mvc, 12, "10", "INDEPENDENT", null);
            var db = ProductionWarehouseFixture.db(context);
            switch (anomaly) {
                case "duplicate" ->
                        db.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_code,routing_node_id,action_type,attribution_type,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,12,'ASSESS','INDEPENDENT',1,CURRENT_TIMESTAMP,'TEST ONLY duplicate')",
                                UUID.randomUUID().toString(), "a".repeat(64), id);
                case "wrong-node" ->
                        db.update("UPDATE mes_quality_treatment SET routing_node_id=11 WHERE declaration_id=?", id);
                case "wrong-execution" ->
                        db.update("UPDATE mes_quality_treatment SET execution_code=2 WHERE declaration_id=?", id);
                case "missing-overlap" ->
                        db.update("UPDATE mes_quality_treatment SET attribution_type='OVERLAP',related_declaration_id='TEST ONLY missing' WHERE declaration_id=?", id);
                case "over-transfer" ->
                        db.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_code,routing_node_id,action_type,transfer_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,12,'TRANSFER_WAREHOUSE',12,1,CURRENT_TIMESTAMP,'TEST ONLY over-transfer')",
                                UUID.randomUUID().toString(), "b".repeat(64), id);
                case "null-action" -> {
                    db.execute("ALTER TABLE mes_quality_treatment ALTER COLUMN action_type DROP NOT NULL");
                    db.update("UPDATE mes_quality_treatment SET action_type=NULL WHERE declaration_id=?", id);
                }
                case "unknown-action" ->
                        db.update("UPDATE mes_quality_treatment SET action_type='REWORK' WHERE declaration_id=?", id);
                case "lower-action" ->
                        db.update("UPDATE mes_quality_treatment SET action_type='assess' WHERE declaration_id=?", id);
                case "null-quantity" -> {
                    db.execute("ALTER TABLE mes_quality_treatment ALTER COLUMN returned_qty DROP NOT NULL");
                    db.update("UPDATE mes_quality_treatment SET returned_qty=NULL WHERE declaration_id=?", id);
                }
                case "over-return" ->
                        db.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_code,routing_node_id,action_type,returned_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,12,'RETURN_NODE',11,1,CURRENT_TIMESTAMP,'TEST ONLY over-return')",
                                UUID.randomUUID().toString(), "c".repeat(64), id);
                default -> throw new IllegalArgumentException(anomaly);
            }
            JsonNode detail = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/" + id + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("UNRESOLVED", detail.path("state").asText(), anomaly);
            assertTrue(detail.path("pendingDispositionQty").isNull(), anomaly);
            JsonNode statistics = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/statistics"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(1, statistics.path("total").asInt(), anomaly);
            assertTrue(statistics.path("items").get(0).path("netDefectQty").isNull(), anomaly);
            assertTrue(statistics.path("items").get(0).path("invalidActions").asLong() > 0, anomaly);
            JsonNode progress = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get("/api/production/workorder-progress"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(progress.path("items").get(0).path("qualityUntreatedQty").isNull(), anomaly);
            assertTrue(progress.path("items").get(0).path("completionPercent").isNull(), anomaly);
            assertTrue(progress.path("items").get(0).hasNonNull("issue"), anomaly);
            var quantityFailure = mvc.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isConflict()).andReturn();
            var quantityError = QualityWorkflowHttpTest.JSON.readTree(
                    quantityFailure.getResponse().getContentAsString()).path("errorCode").asText();
            assertTrue(Set.of("QUALITY_DATA_INVALID", "QUALITY_TREATMENT_CONFLICT").contains(quantityError), anomaly);
        }
    }

    @org.junit.jupiter.api.Test
    void legalUnassessedAndIndependentFactsAgreeAcrossAllFourConsumers() throws Exception {
        try (var context = QualityWorkflowHttpTest.context(
                new Class<?>[]{WorkOrderProgressHttpTest.Extra.class, QualityV2HttpTest.Extra.class},
                100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            String unassessed = QualityWorkflowHttpTest.defect(mvc, 12, "10", null, null);
            String independent = QualityWorkflowHttpTest.defect(mvc, 12, "5", "INDEPENDENT", null);

            var unassessedDetail = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/" + unassessed + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var independentDetail = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/" + independent + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("UNVERIFIED", unassessedDetail.path("state").asText());
            assertEquals("PENDING", independentDetail.path("state").asText());

            var statistics = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/statistics"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(1, statistics.path("total").asInt());
            assertEquals(0, new java.math.BigDecimal("10").compareTo(
                    new java.math.BigDecimal(statistics.path("items").get(0).path("unverifiedQty").asText())));
            assertEquals(0, new java.math.BigDecimal("5").compareTo(
                    new java.math.BigDecimal(statistics.path("items").get(0).path("independentQty").asText())));

            var progress = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get("/api/production/workorder-progress"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("5.000000", progress.path("items").get(0).path("qualityUntreatedQty").asText());
            assertEquals("10.000000", progress.path("items").get(0).path("qualityUnverifiedQty").asText());

            mvc.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                            "nodes[1].unverifiedDefectQty").value("10.000000"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                            "nodes[1].directDefectQty").value("5.000000"));
        }
    }

    @org.junit.jupiter.api.Test
    void formalVoidRemovesQualityContributionAcrossAllFourConsumers() throws Exception {
        try (var context = QualityWorkflowHttpTest.context(
                new Class<?>[]{WorkOrderProgressHttpTest.Extra.class, QualityV2HttpTest.Extra.class},
                100, 100, 90, 90, 90)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            String id = QualityWorkflowHttpTest.defect(mvc, 12, "10", "INDEPENDENT", null);
            mvc.perform(full(post(QualityWorkflowHttpTest.Q + "/" + id + "/void"), "A")
                            .content("{\"requestKey\":\"" + UUID.randomUUID()
                                    + "\",\"expectedRevision\":1,\"revisionReason\":\"TEST ONLY formal VOID\"}"))
                    .andExpect(status().isOk());

            var detail = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/" + id + "/treatment"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("VOIDED", detail.path("state").asText());
            var statistics = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get(QualityWorkflowHttpTest.Q + "/statistics"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(0, statistics.path("total").asInt());
            var progress = QualityWorkflowHttpTest.JSON.readTree(mvc.perform(full(
                            get("/api/production/workorder-progress"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(0, new java.math.BigDecimal(progress.path("items").get(0).path("qualityUntreatedQty")
                    .asText()).signum());
            assertEquals(0, new java.math.BigDecimal(progress.path("items").get(0).path("qualityUnverifiedQty")
                    .asText()).signum());
            mvc.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                            "nodes[1].defectQty").value("0"));
        }
    }
}
