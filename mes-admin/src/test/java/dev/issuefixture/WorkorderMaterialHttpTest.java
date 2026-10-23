package dev.issuefixture;

import java.util.UUID;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: database demand replaces the conflicting legacy ratio, frozen inputs remain unchanged.
 */
class WorkorderMaterialHttpTest {
    @Test
    void businessDemandOwnsRatioAndCannotChangeAfterPreparation() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionInputHttpTest.db(c).update("UPDATE material_bom_item SET quantity=3 WHERE id=1");
            String path = "/api/production/workorder/1/material";
            String save = "{\"expectedRevision\":1,\"bomId\":\"1\",\"warehouseIds\":[\"1\"],\"workCenter\":\"MES维护\",\"enabled\":true}";
            m.perform(ProductionCoreV1HttpTest.full(put(path)).content(save)).andExpect(status().isOk())
                    .andExpect(jsonPath("revision").value("2"));
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/input/calculate")).param("code", "WO1:M1#L001")
                            .param("workorderId", "1").param("stockReference", "1").param("outputQty", "100"))
                    .andExpect(status().isOk()).andExpect(jsonPath("issueQty").value("300.000000"));
            String request = UUID.randomUUID().toString();
            ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, request, "L001", "100"));
            m.perform(ProductionCoreV1HttpTest.full(put(path))
                            .content(save.replace("expectedRevision\":1", "expectedRevision\":2")))
                    .andExpect(status().isConflict());
            assertEquals("3000.000000", ProductionInputHttpTest.db(c)
                    .queryForObject("SELECT total_required_qty FROM mes_workorder_material WHERE id=1", BigDecimal.class)
                    .toPlainString());
            m.perform(put(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(save)).andExpect(status().isForbidden());
        }
    }

    @Test
    void disabledBusinessAuthorityNeverFallsBackToYamlAndFactoryIsIsolated() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(ProductionCoreV1HttpTest.full(put("/api/production/workorder/1/material"))
                            .content("{\"expectedRevision\":1,\"bomId\":\"1\",\"warehouseIds\":[\"1\"],\"workCenter\":\"MES维护\",\"enabled\":false}"))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/input")).param("code", "WO1:M1#L001"))
                    .andExpect(status().isConflict());
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
        }
    }
}
