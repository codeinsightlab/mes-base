package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actual routed H2/XML/HTTP reads; does not repair or fabricate published snapshots.
 */
class RoutingProductDisplayHttpTest {
    @Test
    void linkedCurrentProductIsIndependentOfPublishedSnapshotAndMissingReferencesRemainReadable() throws Exception {
        try (var c = ProductionQualityLifecycleHttpTest.context()) {
            var jdbc = ProductionQualityLifecycleHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            jdbc.update("UPDATE routing_product SET material_code=NULL,material_name=NULL WHERE routing_id=1");
            jdbc.update("UPDATE material SET code='CURRENT-P1',name='当前物料名称' WHERE id=1");
            var before = jdbc.queryForList("SELECT * FROM routing_product WHERE routing_id=1 ORDER BY id");
            mvc.perform(full(get("/api/production/routing/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("products[0].materialId").value("1"))
                    .andExpect(jsonPath("products[0].materialCode").doesNotExist())
                    .andExpect(jsonPath("products[0].materialName").doesNotExist())
                    .andExpect(jsonPath("products[0].currentMaterialCode").value("CURRENT-P1"))
                    .andExpect(jsonPath("products[0].currentMaterialName").value("当前物料名称"));
            assertEquals(before, jdbc.queryForList("SELECT * FROM routing_product WHERE routing_id=1 ORDER BY id"));

            jdbc.update("UPDATE routing_product SET material_code='SNAPSHOT-P1',material_name='发布时名称' WHERE routing_id=1");
            jdbc.update("UPDATE material SET name='再次改名' WHERE id=1");
            mvc.perform(full(get("/api/production/routing/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("products[0].materialName").value("发布时名称"))
                    .andExpect(jsonPath("products[0].currentMaterialName").value("再次改名"));
            mvc.perform(full(get("/api/production/workorder/1/graph")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("products[0].materialName").value("发布时名称"))
                    .andExpect(jsonPath("products[0].currentMaterialName").value("再次改名"));

            jdbc.update("UPDATE routing_product SET material_id=999 WHERE routing_id=1");
            mvc.perform(full(get("/api/production/routing/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("products[0].materialId").value("999"))
                    .andExpect(jsonPath("products[0].materialName").value("发布时名称"))
                    .andExpect(jsonPath("products[0].currentMaterialCode").doesNotExist())
                    .andExpect(jsonPath("products[0].currentMaterialName").doesNotExist());
            mvc.perform(get("/api/production/routing/1").header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "B"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("products[0].currentMaterialCode").value("M1"));
        }
    }
}
