package dev.issuefixture;

import java.util.UUID;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated H2: document identity, frozen input basis and HTTP conversion. */
class StockDocumentProductionInputBasisHttpTest {
    @Test
    void documentIdResolvesFrozenInputBomAndSelectedPath() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String requestId = UUID.randomUUID().toString();
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, requestId, "L001", "10")));
            String documentId = result.get("documentId").asText();
            db.update("UPDATE material_bom_item SET quantity=99 WHERE bom_id=1");
            mvc.perform(full(get(StockDocumentHttpTest.ROOT + "/" + documentId + "/production-input-basis")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("requestId").value(requestId))
                    .andExpect(jsonPath("executionId").value(result.get("executionId").asText()))
                    .andExpect(jsonPath("workorderId").value("1"))
                    .andExpect(jsonPath("sourceMode").value("LOCAL_ONLY"))
                    .andExpect(jsonPath("outputQty").value("10.000000"))
                    .andExpect(jsonPath("issueQty").value("20.000000"))
                    .andExpect(jsonPath("bomId").value("1"))
                    .andExpect(jsonPath("bomBaseQty").value("1.000000"))
                    .andExpect(jsonPath("bomComponentQty").value("2.000000"))
                    .andExpect(jsonPath("businessOccurredAt").isNotEmpty())
                    .andExpect(jsonPath("routingNodeIds[0]").value("11"))
                    .andExpect(jsonPath("routingNodeIds[1]").value("12"))
                    .andExpect(jsonPath("routingNodeIds[2]").value("13"))
                    .andExpect(jsonPath("stockApplied").value(true));
            assertEquals(db.queryForObject("SELECT confirmed_at FROM mes_material_issue_input WHERE document_id=?",
                            LocalDateTime.class, documentId),
                    db.queryForObject("SELECT business_occurred_at FROM mes_material_issue_input WHERE document_id=?",
                            LocalDateTime.class, documentId));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE document_id=?", Integer.class, documentId));
        }
    }

    @Test
    void ordinaryStockDocumentDoesNotExposeProductionBasis() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var document = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null);
            mvc.perform(full(get(StockDocumentHttpTest.ROOT + "/" + document.get("document").get("id").asText()
                    + "/production-input-basis"))).andExpect(status().isNotFound());
        }
    }

    @Test
    void issueQueryAloneCannotReadMaterialBasisButItemListCan() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var input = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String documentId = input.get("documentId").asText();
            mvc.perform(get(StockDocumentHttpTest.ROOT + "/" + documentId + "/production-input-basis")
                            .header("Authorization", "Bearer ISSUE_QUERY").header("X-Factory-Id", "A")
                            .param("documentType", "MATERIAL_ISSUE"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(StockDocumentHttpTest.ROOT + "/" + documentId + "/production-input-basis")
                            .header("Authorization", "Bearer ISSUE_ITEM").header("X-Factory-Id", "A")
                            .param("documentType", "MATERIAL_ISSUE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("materialId").value("1"))
                    .andExpect(jsonPath("issueQty").value("20.000000"));
        }
    }
}
