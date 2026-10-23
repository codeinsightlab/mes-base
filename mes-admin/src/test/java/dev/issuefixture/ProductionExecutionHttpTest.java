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

/**
 * Isolated H2: execution creation is owned by successful formal BOM input, not a public CRUD command.
 */
class ProductionExecutionHttpTest {
    static final String E = "/api/production/mes-production-execution";
    static final String CREATE = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"12.12\",\"status\":\"READY\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void supplyLookupDoesNotCreateExecutionAndOnlyConfirmedInputCreatesItsBusinessBatch() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("DELETE FROM mes_production_execution"); // Unused fresh fixture rows only.
            m.perform(full(post(E)).content(CREATE)).andExpect(status().isMethodNotAllowed());
            m.perform(full(get(ProductionInputHttpTest.ROOT)).param("code", "WO1:M1#L001")).andExpect(status().isOk());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            var result = JSON.readTree(ProductionInputHttpTest.submit(m,
                    ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "12.12")));
            String id = result.get("executionId").asText();
            var before = j.queryForList("SELECT * FROM mes_production_execution ORDER BY id");
            m.perform(full(get(E + "/" + id))).andExpect(status().isOk()).andExpect(jsonPath("productId").value("99"));
            m.perform(full(get("/api/production/identity/" + id))).andExpect(status().isOk())
                    .andExpect(jsonPath("qrCode").value("WO1:P1#L001")).andExpect(jsonPath("productCode").value("P1"));
            assertEquals(before, j.queryForList("SELECT * FROM mes_production_execution ORDER BY id"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            m.perform(ProductionReportHttpTest.full(get(E + "/" + id), "B")).andExpect(status().isNotFound());
        }
    }

    @Test
    void ordinaryEditingCannotRewriteIdentityStatusOrReduceAnExecutionWithOfficialInput() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String id = JSON.readTree(ProductionInputHttpTest.submit(m,
                            ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "12.12")))
                    .get("executionId").asText();
            m.perform(full(put(E + "/" + id)).content(CREATE.replace("12.12", "10"))).andExpect(status().isConflict());
            m.perform(full(put(E + "/" + id)).content(CREATE.replace("READY", "RUNNING")))
                    .andExpect(status().isConflict());
            m.perform(full(put(E + "/" + id)).content(CREATE.replace("\"status\"", "\"remark\":\"TEST ONLY metadata\",\"status\"")))
                    .andExpect(status().isOk()).andExpect(jsonPath("remark").value("TEST ONLY metadata"));
            m.perform(full(delete(E)).content("[" + id + "]")).andExpect(status().isConflict());
        }
    }

    @Test
    void crudPermissionDoesNotAuthorizeMutationOrInventAProductionIdentity() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String id = JSON.readTree(ProductionInputHttpTest.submit(m,
                            ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "12.12")))
                    .get("executionId").asText();
            m.perform(put(E + "/" + id).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(CREATE)).andExpect(status().isForbidden());
            m.perform(delete(E).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content("[" + id + "]")).andExpect(status().isForbidden());
            m.perform(full(get("/api/production/identity")).param("code", "EXE:" + id))
                    .andExpect(status().isBadRequest());
            m.perform(get("/api/production/identity/" + id)).andExpect(status().isForbidden());
        }
    }
}
