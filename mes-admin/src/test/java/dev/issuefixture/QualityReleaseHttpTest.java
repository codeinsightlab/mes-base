package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TEST ONLY H2: release authorizes total allowance, preserves immutable history on changed facts.
 */
class QualityReleaseHttpTest {
    @Test
    void explicitReleaseBoundsVersionAndChangedBasisInvalidateWithoutDeleting() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            var input = ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L001", "100"));
            long execution = new ObjectMapper().readTree(input).get("executionId").asLong();
            m.perform(ProductionCoreV1HttpTest.full(post("/api/production/executions/" + execution + "/actions/start")))
                    .andExpect(status().isOk());
            for (long node : new long[]{11, 12, 13})
                j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,create_time,reason) VALUES(?,?,'REPORT',100,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST ONLY')", execution, node);
            String path = "/api/quality/releases/executions/" + execution;
            String id = UUID.randomUUID().toString();
            m.perform(ProductionCoreV1HttpTest.full(post(path))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"101\",\"reason\":\"TEST ONLY\"}"))
                    .andExpect(status().isConflict());
            String body = "{\"requestId\":\"" + id + "\",\"quantity\":\"94\",\"reason\":\"TEST ONLY 放行\"}";
            m.perform(ProductionCoreV1HttpTest.full(post(path)).content(body)).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(path)).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("id").value(id));
            m.perform(ProductionCoreV1HttpTest.full(get(path))).andExpect(status().isOk())
                    .andExpect(jsonPath("released").value("94.000000"));
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,create_time,reason,reference_report_id) SELECT ?,13,'ADJUSTMENT',-10,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST ONLY',MAX(id) FROM mes_production_report WHERE execution_id=? AND routing_node_id=13", execution, execution);
            m.perform(ProductionCoreV1HttpTest.full(get(path))).andExpect(status().isOk())
                    .andExpect(jsonPath("released").value("0"))
                    .andExpect(jsonPath("historicalReleased").value("94.000000"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            m.perform(post(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
    }

    @Test
    void lateRealDefectPreservesPostedInboundAndBlocksFurtherStockChanges() throws Exception {
        try (var c = QualityWorkflowHttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1"))
                    .content(ProductionCompletionHttpTest.release("98"))).andExpect(status().isOk());
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            UnifiedInboundHttpTest.receive(m, "98");
            String defect = QualityWorkflowHttpTest.defect(m, 12, "10", null, null);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1"))
                    .content(ProductionCompletionHttpTest.release("90"))).andExpect(status().isConflict());
            var assess = (ObjectNode) new ObjectMapper().readTree(QualityWorkflowHttpTest.assessmentCommand());
            assess.putObject("assessment").put("type", "INDEPENDENT").put("basis", "TEST ONLY 晚申报独立依据");
            QualityWorkflowHttpTest.assess(m, defect, assess.toString());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/wm/stock-documents/executions/1/basis")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("90.000000"))
                    .andExpect(jsonPath("received").value("98.000000")).andExpect(jsonPath("canReceive").value(false));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_quality_release", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            assertEquals(0, new BigDecimal("98").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

}
