package dev.issuefixture;

import java.util.UUID;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static dev.issuefixture.QualityWorkflowHttpTest.JSON;
import static dev.issuefixture.QualityWorkflowHttpTest.N;
import static dev.issuefixture.QualityWorkflowHttpTest.Q;
import static dev.issuefixture.QualityWorkflowHttpTest.context;
import static dev.issuefixture.QualityWorkflowHttpTest.defect;
import static dev.issuefixture.QualityWorkflowHttpTest.quantities;
import static dev.issuefixture.QualityWorkflowHttpTest.amount;
import static dev.issuefixture.QualityWorkflowHttpTest.assess;
import static dev.issuefixture.QualityWorkflowHttpTest.assessmentCommand;
import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Routed H2 fixture. New reports, declarations, assessments and dispositions use HTTP commands. */
class UnassessedDefectQuotaHttpTest {
    static void allowance(MockMvc mvc, String value) throws Exception {
        var nodes = quantities(mvc).path("nodes");
        amount(nodes.get(0), "effectiveOutputQty", value);
        amount(nodes.get(1), "inputQty", value);
    }
    @Test void case01_02_07_09_10_unassessedAssessmentAndReportLimits() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionQualityQuantityHttpTest.report(m, 11, "100");
            var id = defect(m, 11, "10", null, null);
            allowance(m, "90");
            m.perform(full(post("/api/production/reports/supplements"), "A")
                    .content(ProductionReportHttpTest.supplement(12, "91"))).andExpect(status().isConflict());
            // The legal unassessed declaration must not block the remaining allowance.
            ProductionQualityQuantityHttpTest.report(m, 12, "90");
            var command = assessmentCommand();
            assess(m, id, command);
            assess(m, id, command);
            allowance(m, "90");
        }
    }
    @Test void case09_10_normalOperatorSubmissionUsesTheSameQuota() throws Exception {
        try (var c = ProductionQualityQuantityHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionReportingHttpTest.submit(m, 11, "100");
            defect(m, 11, "10", null, null);
            allowance(m, "90");
            m.perform(full(post(ProductionReportingHttpTest.ROOT), "A")
                    .content(ProductionReportingHttpTest.body(12, "91"))).andExpect(status().isConflict());
            var otherExecution = JSON.readTree(m.perform(full(get("/api/production/report-quantities/executions/2"), "A"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            amount(otherExecution.path("nodes").get(0), "defectQty", "0");
            amount(otherExecution.path("nodes").get(0), "unverifiedDefectQty", "0");
            ProductionReportingHttpTest.submit(m, 12, "90");
            amount(quantities(m).path("nodes").get(1), "reportedQty", "90");
        }
    }
    @ParameterizedTest @CsvSource({"DEFECTIVE,PROCESS", "DEFECTIVE,MATERIAL", "SCRAP,PROCESS", "SCRAP,MATERIAL"})
    void case03_04_07_dispositionKeepsTheSameOccupancy(String warehouseType, String category) throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var db = ProductionWarehouseFixture.db(c);
            // Fixture master data only; all production and quality facts below use HTTP.
            db.update("UPDATE wm_warehouse SET warehouse_type=? WHERE id=1", warehouseType);
            QualityDispositionHttpTest.bindBatch(db, "TEST-QUOTA-" + UUID.randomUUID());
            ProductionQualityQuantityHttpTest.report(m, 11, "100");
            var declaration = (ObjectNode) JSON.readTree(
                    ProductionQualityQuantityHttpTest.command(11, "10"));
            declaration.remove("assessment");
            ((ObjectNode) declaration.get("facts")).put("wasteCategory", category);
            var id = ProductionDefectHttpTest.add(m, declaration.toString(), "A");
            allowance(m, "90");
            assess(m, id, assessmentCommand());
            allowance(m, "90");
            var body = QualityDispositionHttpTest.command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1");
            QualityDispositionHttpTest.dispose(m, id, body);
            QualityDispositionHttpTest.dispose(m, id, body);
            allowance(m, "90");
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type=?",
                    Integer.class, warehouseType.equals("SCRAP") ? "SCRAP_RECEIPT" : "DEFECT_RECEIPT"));
        }
    }
    @Test void case05_06_11_voidAndDistinctDeclarations() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionQualityQuantityHttpTest.report(m, 11, "100");
            allowance(m, "100");
            var first = defect(m, 11, "10", null, null);
            allowance(m, "90");
            var second = defect(m, 11, "5", "INDEPENDENT", null);
            allowance(m, "85");
            for (var id : new String[]{first, second}) {
                m.perform(full(post(Q + "/" + id + "/void"), "A").content("{\"requestKey\":\"" + UUID.randomUUID()
                        + "\",\"expectedRevision\":1,\"revisionReason\":\"TEST ONLY void\"}"))
                        .andExpect(status().isOk());
            }
            allowance(m, "100");
        }
    }
    @Test void case12_andOverlapDoNotDoubleCount() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionQualityQuantityHttpTest.report(m, 11, "100");
            var first = defect(m, 11, "10", "INDEPENDENT", null);
            var overlap = defect(m, 11, "5", null, null);
            allowance(m, "85"); // Reserve unknown attribution conservatively until an explicit decision.
            var assessment = (ObjectNode) JSON.readTree(assessmentCommand());
            assessment.putObject("assessment").put("type", "OVERLAP").put("relatedDeclarationId", first).put("basis", "TEST ONLY same physical items");
            assess(m, overlap, assessment.toString());
            allowance(m, "90");
            ProductionQualityQuantityHttpTest.report(m, 12, "90");
            defect(m, 12, "5", null, null);
            var nodes = quantities(m).path("nodes");
            amount(nodes.get(0), "effectiveOutputQty", "90");
            amount(nodes.get(1), "effectiveOutputQty", "85");
            m.perform(full(get(N), "B")).andExpect(status().isOk());
            var other = JSON.readTree(m.perform(full(get(N), "B")).andReturn().getResponse().getContentAsString());
            amount(other.path("nodes").get(0), "defectQty", "0");
        }
    }
}
