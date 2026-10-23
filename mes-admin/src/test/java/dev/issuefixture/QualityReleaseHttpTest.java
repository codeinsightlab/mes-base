package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2: retired release facts remain audit-only, never stock or production authority.
 */
class QualityReleaseHttpTest {
    @Test
    void retiredCommandsAreUnavailableAndOriginalHistoryRemainsReadOnly() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            j.update("INSERT INTO mes_quality_release(id,execution_id,sequence_no,action_type,quantity,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES('test-audit-release',1,1,'RELEASE',20,REPEAT('a',64),REPEAT('b',64),1,CURRENT_TIMESTAMP,'TEST ONLY retired fact')");
            var before = j.queryForList("SELECT * FROM mes_quality_release ORDER BY id");
            String path = "/api/quality/releases/executions/1";
            m.perform(ProductionCoreV1HttpTest.full(get(path + "/history")))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].quantity").value("20.000000"));
            m.perform(ProductionCoreV1HttpTest.full(post(path)).content("{}"))
                    .andExpect(status().isNotFound());
            m.perform(ProductionCoreV1HttpTest.full(post(path + "/revoke")).content("{}"))
                    .andExpect(status().isNotFound());
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "60");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/stock-documents/executions/1/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("remaining").value("40.000000"));
            assertEquals(before, j.queryForList("SELECT * FROM mes_quality_release ORDER BY id"));
        }
    }

    @Test
    void unverifiedDefectBlocksApprovalAndLateConfirmedLossPreservesExistingStock() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            UnifiedInboundHttpTest.stock(j);
            UnifiedInboundHttpTest.receive(m, "98");
            String defect = QualityWorkflowHttpTest.defect(m, 12, "10", null, null);
            String draft = UnifiedInboundHttpTest.draft(m, "1", "1");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/stock-documents/executions/1/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("canReceive").value(false));
            m.perform(ProductionCoreV1HttpTest.full(post(StockDocumentHttpTest.ROOT + "/" + draft + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isConflict());
            var assess = (ObjectNode) new ObjectMapper().readTree(QualityWorkflowHttpTest.assessmentCommand());
            assess.putObject("assessment").put("type", "INDEPENDENT").put("basis", "TEST ONLY late independent loss");
            QualityWorkflowHttpTest.assess(m, defect, assess.toString());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/stock-documents/executions/1/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("90.000000"))
                    .andExpect(jsonPath("received").value("98.000000")).andExpect(jsonPath("canReceive").value(false));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            assertEquals(0, new BigDecimal("98").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
