package dev.issuefixture;

import dev.mesbase.business.basic.controller.MaterialBomController;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2: real HTTP, generated SQL and transaction rollback; no configured database is used.
 */
class WorkorderMaterialAggregateHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Configuration
    @Import(MaterialBomController.class)
    static class Extra {
    }

    private static void seed(JdbcTemplate db) {
        db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(98,'TEST-COMPONENT','TEST ONLY second component',1,'ENABLED')");
        db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at,is_default) VALUES(2,'TEST-MULTI','1',99,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP,0)");
        // A missing lower-level version must not invalidate this stage's two valid direct components.
        db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES(21,2,1,2,1,999999,0),(22,2,98,3,1,NULL,1)");
    }

    private static Map<String, Object> line(String source, String material, String quantity, boolean adjusted) {
        var line = new LinkedHashMap<String, Object>();
        line.put("id", null);
        line.put("bomItemId", source);
        line.put("materialId", material);
        line.put("unitId", "1");
        line.put("totalRequiredQty", quantity);
        line.put("manuallyAdjusted", adjusted);
        return line;
    }

    private static Map<String, Object> material(List<Map<String, Object>> lines) {
        var material = new LinkedHashMap<String, Object>();
        material.put("expectedRevision", "0");
        material.put("bomId", "2");
        material.put("expectedBomRevision", "1");
        material.put("warehouseIds", List.of());
        material.put("workCenter", "");
        material.put("enabled", true);
        material.put("items", lines);
        material.put("adjustmentsConfirmed", false);
        return material;
    }

    private static Map<String, Object> command(String quantity, Map<String, Object> material) {
        var command = new LinkedHashMap<String, Object>();
        command.put("ruleCode", "TEST-WORKORDER");
        command.put("productId", "99");
        command.put("routingId", "1");
        command.put("plannedQty", quantity);
        command.put("material", material);
        return command;
    }

    @Test
    void aggregateCreateSavesAllDirectLinesWithoutBatchStockOrLowerLevelManufacturing() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            seed(db);
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(full(get("/api/basic/material-bom/2/direct-components")).param("quantity", "3"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(2))
                    .andExpect(jsonPath("items[0].quantity").value("6.000000"));
            var result = mvc.perform(full(post("/api/production/workorder"))
                            .content(JSON.writeValueAsString(command("3", material(List.of(
                                    line("21", "1", "6", false), line("22", "98", "9", false)))))))
                    .andExpect(status().isOk()).andReturn();
            var id = JSON.readTree(result.getResponse().getContentAsString()).path("workorder").path("id").asString();
            mvc.perform(full(get("/api/production/workorder/" + id + "/material")))
                    .andExpect(status().isOk()).andExpect(jsonPath("bomId").value("2"))
                    .andExpect(jsonPath("items.length()").value(2))
                    .andExpect(jsonPath("items[1].totalRequiredQty").value("9.000000"));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material WHERE workorder_id=?", Integer.class, id));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            mvc.perform(get("/api/production/workorder/" + id + "/material").header("Authorization", "Bearer FULL")
                    .header("X-Factory-Id", "B")).andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidSecondLineAndForeignLineIdentityRollBackTheWholeNewDocument() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            seed(db);
            var mvc = MaterialIssueHttpTest.mvc(context);
            int orders = db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class);
            int rows = db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class);
            var invalid = line("22", "98", "9", true);
            invalid.put("unitId", "987654");
            mvc.perform(full(post("/api/production/workorder")).content(JSON.writeValueAsString(
                            command("3", material(List.of(line("21", "1", "6", false), invalid))))))
                    .andExpect(status().isConflict());
            var foreign = line("21", "1", "6", false);
            foreign.put("id", "1");
            mvc.perform(full(post("/api/production/workorder")).content(JSON.writeValueAsString(
                            command("3", material(List.of(foreign, line("22", "98", "9", false)))))))
                    .andExpect(status().isBadRequest());
            assertEquals(orders, db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
            assertEquals(rows, db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
            assertEquals(0, new BigDecimal("2000").compareTo(db.queryForObject(
                    "SELECT total_required_qty FROM mes_workorder_material WHERE id=1", BigDecimal.class)));
        }
    }

    @Test
    void changingPlanRequiresConfirmationAndPreservesManualAndAddedQuantitiesAtomically() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            seed(db);
            var mvc = MaterialIssueHttpTest.mvc(context);
            var first = material(List.of(line("21", "1", "7", true), line("22", "98", "9", false),
                    line(null, "98", "5", true)));
            var created = mvc.perform(full(post("/api/production/workorder"))
                    .content(JSON.writeValueAsString(command("3", first)))).andExpect(status().isOk()).andReturn();
            var id = JSON.readTree(created.getResponse().getContentAsString()).path("workorder").path("id").asString();
            JsonNode saved = JSON.readTree(mvc.perform(full(get("/api/production/workorder/" + id + "/material")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var lines = new ArrayList<Map<String, Object>>();
            int index = 0;
            for (var item : saved.path("items")) {
                var current = line(null, item.path("materialId").asString(), item.path("totalRequiredQty")
                        .asString(), index != 1);
                current.put("id", item.path("id").asString());
                if (!item.path("bomItemId").isNull()) current.put("bomItemId", item.path("bomItemId").asString());
                if (index++ == 1) current.put("totalRequiredQty", "12");
                lines.add(current);
            }
            var changedMaterial = material(lines);
            changedMaterial.put("expectedRevision", "1");
            var changed = command("4", changedMaterial);
            changed.put("revision", 0);
            mvc.perform(full(put("/api/production/workorder/" + id)).content(JSON.writeValueAsString(changed)))
                    .andExpect(status().isConflict());
            assertEquals(0, new BigDecimal("3").compareTo(db.queryForObject("SELECT planned_qty FROM workorder WHERE id=?", BigDecimal.class, id)));
            changedMaterial.put("adjustmentsConfirmed", true);
            mvc.perform(full(put("/api/production/workorder/" + id)).content(JSON.writeValueAsString(changed)))
                    .andExpect(status().isOk());
            var quantities = db.queryForList("SELECT total_required_qty FROM mes_workorder_material WHERE workorder_id=? ORDER BY line_no", BigDecimal.class, id);
            assertEquals(List.of(new BigDecimal("7.000000"), new BigDecimal("12.000000"), new BigDecimal("5.000000")), quantities);
            assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material WHERE workorder_id=? AND id IN (?,?,?)", Integer.class,
                    id, saved.path("items").get(0).path("id").asString(), saved.path("items").get(1).path("id")
                            .asString(), saved.path("items").get(2).path("id").asString()));
        }
    }
}
