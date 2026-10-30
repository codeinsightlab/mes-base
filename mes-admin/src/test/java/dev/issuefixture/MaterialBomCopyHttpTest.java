package dev.issuefixture;

import dev.mesbase.business.basic.controller.MaterialBomController;
import dev.mesbase.business.basic.controller.MaterialBomItemController;
import dev.mesbase.business.basic.service.MaterialBomService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: routed H2, real HTTP authorization and business transaction.
 */
class MaterialBomCopyHttpTest {
    @Configuration
    @Import({MaterialBomController.class, MaterialBomItemController.class, MaterialBomService.class})
    static class Extra {
    }

    @Test
    void copyPreservesSourceAndComponentsThenAllowsEditAndPublish() throws Exception {
        try (var c = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.execute("ALTER TABLE material_bom ALTER COLUMN id RESTART WITH 100");
            db.execute("ALTER TABLE material_bom_item ALTER COLUMN id RESTART WITH 100");
            db.update("UPDATE material_bom SET base_qty=3,remark='source',is_default=TRUE WHERE id=1");
            db.update("UPDATE material_bom_item SET child_bom_id=42,sort=8 WHERE id=1");
            var response = mvc.perform(full(post("/api/basic/material-bom/1/copy"))
                            .content("{\"version\":\"2\",\"revision\":\"1\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value("DRAFT"))
                    .andExpect(jsonPath("defaultVersion").value(false)).andReturn().getResponse().getContentAsString();
            long id = new ObjectMapper().readTree(response).get("id").asLong();
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom_item WHERE bom_id=? AND material_id=1 AND quantity=2 AND unit_id=1 AND child_bom_id=42 AND sort=8", Integer.class, id));
            assertEquals("PUBLISHED", db.queryForObject("SELECT status FROM material_bom WHERE id=1", String.class));
            assertEquals(1L, db.queryForObject("SELECT revision FROM material_bom WHERE id=1", Long.class));
            assertEquals(Boolean.TRUE, db.queryForObject("SELECT is_default FROM material_bom WHERE id=1", Boolean.class));
            assertEquals(1, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM material_bom", Integer.class));
            mvc.perform(full(post("/api/basic/material-bom/1/copy")).content("{\"version\":\"2\",\"revision\":\"1\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(full(post("/api/basic/material-bom/1/copy")).content("{\"version\":\"3\",\"revision\":\"99\"}"))
                    .andExpect(status().isConflict());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM material_bom", Integer.class));
            long line = db.queryForObject("SELECT id FROM material_bom_item WHERE bom_id=?", Long.class, id);
            mvc.perform(full(put("/api/basic/material-bom-item/" + line)).content("{\"materialId\":\"1\",\"quantity\":\"4\",\"childBomId\":null,\"sort\":8}"))
                    .andExpect(status().isOk());
            mvc.perform(full(post("/api/basic/material-bom/" + id + "/publish")).content("{\"revision\":\"2\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value("PUBLISHED"));
            assertEquals(2, db.queryForObject("SELECT quantity FROM material_bom_item WHERE id=1", Integer.class));
        }
    }

    @Test
    void copyRequiresReadAndCreateAndRollsBackIfComponentCopyFails() throws Exception {
        try (var c = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.execute("ALTER TABLE material_bom ALTER COLUMN id RESTART WITH 100");
            mvc.perform(post("/api/basic/material-bom/1/copy").header("Authorization", "Bearer BOM_CREATE")
                            .header("X-Factory-Id", "A").contentType("application/json")
                            .content("{\"version\":\"2\",\"revision\":\"1\"}"))
                    .andExpect(status().isForbidden());
            db.execute("ALTER TABLE material_bom_item ADD CONSTRAINT test_copy_failure CHECK (bom_id <> 100)");
            mvc.perform(full(post("/api/basic/material-bom/1/copy")).content("{\"version\":\"2\",\"revision\":\"1\"}"))
                    .andExpect(status().is5xxServerError());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom_item", Integer.class));
        }
    }

    @Test
    void enableChecksCurrentCompositionAndDoesNotRestoreDefault() throws Exception {
        try (var c = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(full(post("/api/basic/material-bom/1/disable")).content("{\"revision\":\"1\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value("DISABLED"));
            db.update("UPDATE material_bom_item SET quantity=0 WHERE id=1");
            mvc.perform(full(post("/api/basic/material-bom/1/enable")).content("{\"revision\":\"2\"}"))
                    .andExpect(status().isBadRequest());
            assertEquals("DISABLED", db.queryForObject("SELECT status FROM material_bom WHERE id=1", String.class));
            db.update("UPDATE material_bom_item SET quantity=2 WHERE id=1");
            mvc.perform(full(post("/api/basic/material-bom/1/enable")).content("{\"revision\":\"2\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value("PUBLISHED"))
                    .andExpect(jsonPath("defaultVersion").value(false));
            mvc.perform(full(post("/api/basic/material-bom/1/enable")).content("{\"revision\":\"2\"}"))
                    .andExpect(status().isConflict());
        }
    }
}
