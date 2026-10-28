package dev.issuefixture;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TEST ONLY: formal production commands stay usable after legacy HTTP retirement. */
class MaterialIssueEndpointRetirementHttpTest {
    @Test
    void legacyCrudSubmitAndVoidRoutesHaveNoHandler() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            for (String path : new String[]{"/api/issue/mes-material-issue", "/api/issue/mes-material-issue-item"}) {
                mvc.perform(full(get(path))).andExpect(status().isNotFound());
                mvc.perform(full(get(path + "/1"))).andExpect(status().isNotFound());
                mvc.perform(full(post(path)).content("{}")).andExpect(status().isNotFound());
                mvc.perform(full(put(path + "/1")).content("{}")).andExpect(status().isNotFound());
                mvc.perform(full(delete(path)).content("[1]")).andExpect(status().isNotFound());
            }
            for (String action : new String[]{"submit", "reopen-rejected", "void", "reconcile-void"})
                mvc.perform(full(post("/api/issue/mes-material-issue" + "/1/actions/" + action)))
                        .andExpect(status().isNotFound());
        }
    }

    @Test
    void productionSceneSavesDraftAndUnifiedDocumentApprovesIt() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String requestId = UUID.randomUUID().toString();
            String command = ProductionInputHttpTest.command(mvc, requestId, "L001", "10");
            String response = mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/drafts")).content(command))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("DRAFT"))
                    .andReturn().getResponse().getContentAsString();
            var draft = new ObjectMapper().readTree(response).get("document");
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE production_confirmation_status IS NOT NULL", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + draft.get("id").asText() + "/actions/approve"))
                            .content("{\"requestId\":\"" + requestId + "\",\"expectedRevision\":\""
                                    + draft.get("revision").asText() + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("POSTED"));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE production_confirmed_at IS NOT NULL", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }
}
