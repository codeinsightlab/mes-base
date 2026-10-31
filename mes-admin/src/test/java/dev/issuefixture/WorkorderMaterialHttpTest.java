package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: database demand replaces the conflicting legacy ratio, frozen inputs remain unchanged.
 */
class WorkorderMaterialHttpTest {
    @Test
    void adjustedDemandIsTheFormalQuantityBasisInsteadOfTheSharedBomStandard() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(context);
            var m = MaterialIssueHttpTest.mvc(context);
            j.update("UPDATE mes_workorder_material SET manually_adjusted=TRUE,total_required_qty=2001 WHERE workorder_id=1");
            var plan = ProductionInputHttpTest.generate(m, "1");
            assertEquals("2.001000", plan.get("allocations").get(0).get("quantity").asText());
            assertEquals(0, new BigDecimal("2001").compareTo(j.queryForObject("SELECT total_required_qty FROM mes_workorder_material WHERE workorder_id=1", BigDecimal.class)));
        }
    }

    @Test
    void unreadableStandardRatioDoesNotReplaceTheSavedActualDemand() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(context);
            var m = MaterialIssueHttpTest.mvc(context);
            j.update("UPDATE mes_workorder_material SET bom_base_qty=0 WHERE workorder_id=1");
            m.perform(ProductionCoreV1HttpTest.full(get("/api/production/workorder/1/material")))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].standardRequiredQty").isEmpty());
            assertEquals("2.000000", ProductionInputHttpTest.generate(m, "1").get("allocations").get(0).get("quantity")
                    .asText());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }

    @Test
    void savedDemandFreezesOnlyOnFirstSuccessfulMesIssue() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE material_bom_item SET quantity=3 WHERE id=1");
            String path = "/api/production/workorder/1/material";
            String save = "{\"expectedRevision\":1,\"expectedBomRevision\":1,\"bomId\":\"1\",\"warehouseIds\":[\"1\"],\"workCenter\":\"TEST ONLY\",\"enabled\":true}";
            m.perform(ProductionCoreV1HttpTest.full(put(path)).content(save)).andExpect(status().isOk())
                    .andExpect(jsonPath("revision").value("2"));
            assertEquals("300.000000", ProductionInputHttpTest.generate(m, "100").get("allocations").get(0)
                    .get("quantity").asText());
            ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L001", "100"));
            m.perform(ProductionCoreV1HttpTest.full(put(path))
                            .content(save.replace("expectedRevision\":1", "expectedRevision\":2")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void disabledDemandCannotGenerateACodeAndDoesNotChangeAnotherFactory() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            m.perform(ProductionCoreV1HttpTest.full(put("/api/production/workorder/1/material"))
                            .content("{\"expectedRevision\":1,\"expectedBomRevision\":1,\"bomId\":\"1\",\"warehouseIds\":[\"1\"],\"workCenter\":\"TEST ONLY\",\"enabled\":false}"))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(MockMvcRequestBuilders.post("/api/production/combinations"))
                    .content("{\"workorderId\":\"1\",\"quantity\":\"1\"}")).andExpect(status().isConflict());
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
        }
    }
}
