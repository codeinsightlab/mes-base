package dev.issuefixture;

import dev.mesbase.business.basic.controller.MaterialBomController;
import dev.mesbase.business.basic.service.MaterialBomService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read-only BOM HTTP, actual generated SQL, bounded recursion and routed H2; no live database proof.
 */
class MaterialBomExplosionHttpTest {
    private static final String ROOT = "/api/basic/material-bom/explode";

    @Configuration
    @Import({MaterialBomController.class, MaterialBomService.class})
    static class Extra {
    }

    private static void graph(JdbcTemplate db) {
        db.update("UPDATE material_bom SET base_qty=2,version='CHILD-1' WHERE id=1");
        db.update("UPDATE material_bom_item SET quantity=5 WHERE id=1");
        db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(100,'TEST-TOP','顶层',1,'ENABLED'),(98,'TEST-C','分支',1,'ENABLED')");
        db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) "
                + "VALUES(7,'TEST-TOP','ROOT-1',100,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP),"
                + "(8,'TEST-C','BRANCH-1',98,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) "
                + "VALUES(70,7,99,2,1,1,0),(71,7,98,3,1,8,1),(80,8,99,1,1,1,0)");
    }

    @Test
    void sharedChildKeepsBothPathsCoefficientsLevelsAndSummedLeafDemandWithoutWritingFacts() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            BigDecimal balance = db.queryForObject("SELECT SUM(quantity) FROM wm_inventory", BigDecimal.class);
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(true))
                    .andExpect(jsonPath("selectionRequired").value(false))
                    .andExpect(jsonPath("material.id").value("100"))
                    .andExpect(jsonPath("selectedVersion.id").value("7"))
                    .andExpect(jsonPath("versions.total").value(1))
                    .andExpect(jsonPath("items[0].level").value(1))
                    .andExpect(jsonPath("items[0].componentQty").value("2.000000"))
                    .andExpect(jsonPath("items[0].baseQty").value("1.000000"))
                    .andExpect(jsonPath("items[0].quantity").value("20.000000"))
                    .andExpect(jsonPath("items[0].leaf").value(false))
                    .andExpect(jsonPath("items[0].children[0].parentBomVersion").value("CHILD-1"))
                    .andExpect(jsonPath("items[0].children[0].baseQty").value("2.000000"))
                    .andExpect(jsonPath("items[0].children[0].quantity").value("50.000000"))
                    .andExpect(jsonPath("items[0].children[0].leaf").value(true))
                    .andExpect(jsonPath("items[1].children[0].children[0].level").value(3))
                    .andExpect(jsonPath("items[1].children[0].children[0].quantity").value("75.000000"))
                    .andExpect(jsonPath("totals[0].quantity").value("125.000000"))
                    .andExpect(jsonPath("totals[0].paths[0]").value("/70/1"))
                    .andExpect(jsonPath("totals[0].paths[1]").value("/71/80/1"))
                    .andExpect(jsonPath("problems").isEmpty());
            assertEquals(balance, db.queryForObject("SELECT SUM(quantity) FROM wm_inventory", BigDecimal.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            // The original strict structure endpoint still uses the same graph-loading path and quantities.
            mvc.perform(full(get("/api/basic/material-bom/7/structure")).param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("totals[0].quantity").value("125.000000"));
        }
    }

    @Test
    void multipleRootVersionsRequireExplicitSelectionEvenWhenOneIsDefault() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            db.update("UPDATE material_bom SET is_default=TRUE WHERE id=7");
            db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) "
                    + "VALUES(9,'TEST-TOP','ROOT-2',100,1,1,'DRAFT',1,1,CURRENT_TIMESTAMP)");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("versionLimit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("selectionRequired").value(true))
                    .andExpect(jsonPath("complete").value(false)).andExpect(jsonPath("versions.total").value(2))
                    .andExpect(jsonPath("versions.items.length()").value(1))
                    .andExpect(jsonPath("selectedVersion").isEmpty())
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_AMBIGUOUS"));
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("bomId", "7"))
                    .andExpect(status().isOk()).andExpect(jsonPath("selectionRequired").value(false))
                    .andExpect(jsonPath("complete").value(true)).andExpect(jsonPath("selectedVersion.id").value("7"));
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("bomId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("material.code").value("TEST-TOP"))
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_INVALID"));
        }
    }

    @Test
    void missingChildOrCycleBlocksOnlyItsBranchAndNeverMasqueradesAsALeaf() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            db.update("UPDATE material_bom_item SET child_bom_id=999 WHERE id=70");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("items.length()").value(2)).andExpect(jsonPath("items[0].leaf").value(false))
                    .andExpect(jsonPath("items[0].quantity").value("20.000000"))
                    .andExpect(jsonPath("totals[0].quantity").value("75.000000"))
                    .andExpect(jsonPath("problems[0].path").value("/70"))
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_MISSING"));
            mvc.perform(full(get(ROOT)).param("materialCode", "M1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("material.code").value("M1"))
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_MISSING"));
            db.update("UPDATE material_bom_item SET child_bom_id=1 WHERE id=70");
            db.update("UPDATE material_bom_item SET child_bom_id=NULL WHERE id=71");
            db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) "
                    + "VALUES(6,'TEST-CYCLE','1',1,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP)");
            db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES(60,6,99,1,1,1,0)");
            db.update("UPDATE material_bom_item SET child_bom_id=6 WHERE id=1");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("items[0].children[0].children[0].leaf").value(false))
                    .andExpect(jsonPath("problems[0].path").value("/70/1/60"))
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_CYCLE"))
                    .andExpect(jsonPath("totals[0].materialId").value("98"))
                    .andExpect(jsonPath("totals[0].quantity").value("30.000000"));
        }
    }

    @Test
    void explicitLeafReferencesDoNotSilentlyFollowAnotherBomAndInvalidRatiosRemainVisible() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            db.update("UPDATE material_bom_item SET child_bom_id=NULL WHERE id=80");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(true))
                    .andExpect(jsonPath("items[1].children[0].leaf").value(true))
                    .andExpect(jsonPath("totals[1].materialId").value("99"))
                    .andExpect(jsonPath("totals[1].quantity").value("30.000000"));
            db.update("UPDATE material_bom_item SET quantity=0 WHERE id=70");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("items[0].componentQty").value("0.000000"))
                    .andExpect(jsonPath("items[0].quantity").isEmpty())
                    .andExpect(jsonPath("totals[0].materialId").value("99"))
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_INVALID"));
        }
    }

    @Test
    void authorizationFactoryIsolationAndRequestBudgetsApplyToCodeLookup() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            var other = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B");
            graph(other);
            other.update("UPDATE material_bom_item SET quantity=4 WHERE id=70");
            mvc.perform(ProductionReportHttpTest.full(get(ROOT), "B").param("materialCode", "TEST-TOP")
                            .param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("totals[0].quantity").value("175.000000"));
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("totals[0].quantity").value("125.000000"));
            mvc.perform(get(ROOT).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A")
                            .param("materialCode", "TEST-TOP"))
                    .andExpect(status().isForbidden());
            mvc.perform(full(get(ROOT)).param("materialCode", "UNKNOWN"))
                    .andExpect(status().isNotFound());
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("versionLimit", "101"))
                    .andExpect(status().isBadRequest());
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP").param("quantity", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void excessiveReachableGraphReturnsABudgetBlockWithoutPartialDemandOrLosingRootIdentity() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            graph(db);
            db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) "
                    + "SELECT n,7,99,1,1,n,0 FROM SYSTEM_RANGE(1000,3000) AS generated(n)");
            mvc.perform(full(get(ROOT)).param("materialCode", "TEST-TOP"))
                    .andExpect(status().isOk()).andExpect(jsonPath("complete").value(false))
                    .andExpect(jsonPath("material.code").value("TEST-TOP"))
                    .andExpect(jsonPath("selectedVersion.id").value("7"))
                    .andExpect(jsonPath("items").isEmpty()).andExpect(jsonPath("totals").isEmpty())
                    .andExpect(jsonPath("problems[0].errorCode").value("MATERIAL_BOM_BUDGET"));
        }
    }
}
