package dev.issuefixture;

import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY H2 exact five-operation 100 -> defect10 -> scrap6/recover4 -> E94 closure.
 */
class ProductionCompletionHttpTest {
    @Test
    void strictCompleteRequiresAll94AndKeepsTarget100AndIdempotentTerminalFact() throws Exception {
        try (var c = ProductionQualityV13HttpTest.context(100, 100, 100, 100, 100)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            String defect = ProductionQualityV13HttpTest.defect(m, 12, "10", "INDEPENDENT", null);
            String started = ProductionQualityV13HttpTest.start(m, defect);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("90")))
                    .andExpect(status().isConflict());
            ProductionQualityV13HttpTest.execute(m, defect, ProductionQualityV13HttpTest.command("RESULT", started, "0", "0", "4", "4", "0"));
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1")).content(release("94")))
                    .andExpect(status().isOk());
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
            String a = handover(m, "60"), b = handover(m, "20");
            accept(m, a, "60");
            accept(m, b, "20");
            String root = "/api/production/executions/1";
            var state = new ObjectMapper().readTree(m.perform(ProductionCoreV1HttpTest.full(get(root + "/closure")))
                    .andExpect(status().isOk()).andExpect(jsonPath("effective").value("94.000000"))
                    .andExpect(jsonPath("received").value("80.000000"))
                    .andExpect(jsonPath("accepted").value("80.000000"))
                    .andExpect(jsonPath("remainingHandover").value("14.000000")).andReturn().getResponse()
                    .getContentAsString());
            String id = UUID.randomUUID()
                    .toString(), body = "{\"requestId\":\"" + id + "\",\"expectedBasisHash\":\"" + state.get("basisHash")
                    .asText() + "\",\"reason\":\"TEST ONLY 严格完工\"}";
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isConflict());
            String last = handover(m, "14");
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isConflict());
            accept(m, last, "14");
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("effectiveQty").value("94.000000"));
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/complete")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("100.000000", j.queryForObject("SELECT target_qty FROM mes_production_execution WHERE id=1", BigDecimal.class)
                    .toPlainString());
            m.perform(ProductionCoreV1HttpTest.full(post(root + "/actions/select-path"))
                    .content("{\"revision\":1,\"routingNodeIds\":[11,12,13,14,15]}")).andExpect(status().isConflict());
        }
    }

    static String release(String qty) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY\"}";
    }

    static String handover(MockMvc m, String qty) throws Exception {
        String id = UUID.randomUUID().toString();
        m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT))
                        .content("{\"requestId\":\"" + id + "\",\"executionId\":1,\"warehouseId\":1,\"productLotCode\":\"TEST-FINISHED\",\"quantity\":\"" + qty + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    static void accept(MockMvc m, String receipt, String qty) throws Exception {
        m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/production-acceptances/receipts/" + receipt))
                        .content(WarehouseAcceptanceHttpTest.command(UUID.randomUUID().toString(), qty)))
                .andExpect(status().isOk());
    }
}
