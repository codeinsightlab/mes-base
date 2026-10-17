package dev.issuefixture;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY real H2 receipt commands consume explicit current release, never stock.
 */
class ProductionReleasedReceiptHttpTest {
    @Test
    void releaseAllowsPartialHandoverAndReplayButNotOverdraw() throws Exception {
        try (var c = ProductionReceiptHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"94\",\"reason\":\"TEST ONLY 正式放行\"}"))
                    .andExpect(status().isOk());
            String id = UUID.randomUUID().toString();
            String body = "{\"executionId\":1,\"warehouseId\":1,\"productLotCode\":\"TEST-FINISHED\",\"quantity\":\"60\",\"requestId\":\"" + id + "\"}";
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT)).content(body))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT)).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT))
                            .content(body.replace(id, UUID.randomUUID().toString()).replace("\"60\"", "\"20\"")))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(get(ProductionReceiptHttpTest.ROOT + "/summary"))
                            .param("executionId", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("received").value("80.000000"))
                    .andExpect(jsonPath("releaseRemaining").value("14.000000"))
                    .andExpect(jsonPath("accepted").value("0"));
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT))
                            .content(body.replace(id, UUID.randomUUID().toString()).replace("\"60\"", "\"15\"")))
                    .andExpect(status().isConflict());
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }
}
