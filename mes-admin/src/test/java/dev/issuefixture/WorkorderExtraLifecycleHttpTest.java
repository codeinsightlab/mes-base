package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: local work-order metadata follows the business transaction.
 */
class WorkorderExtraLifecycleHttpTest {
    @Test
    void workorderExtraFollowsTransactionalCreationAndDeletion() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String body = "{\"code\":\"WO_EXTRA_TEST\",\"productId\":1,\"routingId\":1,\"plannedQty\":10,\"ruleCode\":\"WORK_ORDER\"}";
            body = WorkorderMaterialFixture.command(c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A"), body, null);
            m.perform(full(post("/api/production/workorder")).content(body.replace("\"ruleCode\":", "\"externalId\":\"FORGED\",\"ruleCode\":")))
                    .andExpect(status().isConflict());
            m.perform(full(post("/api/production/workorder")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.source").value("LOCAL"));
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE id=2 AND external_id IS NULL AND provider IS NULL", Integer.class));
            m.perform(full(delete("/api/production/workorder/2")).content("{\"revision\":0}"))
                    .andExpect(status().isNoContent());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE id=2", Integer.class));
        }
    }
}
