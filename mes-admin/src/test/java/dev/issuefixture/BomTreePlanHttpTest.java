package dev.issuefixture;

import dev.mesbase.business.basic.controller.*;
import dev.mesbase.business.basic.service.*;
import dev.mesbase.business.basic.dao.BomPlanReceiptMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

class BomTreePlanHttpTest {
    @Configuration
    @Import({MaterialBomItemController.class, MaterialBomController.class, MaterialBomService.class, BomTreePlans.class, BomTreeController.class})
    static class Extra {
        @Bean
        BomPlanReceiptMapper bomPlanReceiptMapper(SqlSessionFactory f) {
            f.getConfiguration().addMapper(BomPlanReceiptMapper.class);
            return new SqlSessionTemplate(f).getMapper(BomPlanReceiptMapper.class);
        }
    }

    static final String API = "/api/basic/material-bom/tree/";
    static final ObjectMapper JSON = new ObjectMapper();

    static String confirmation(MockMvc mvc, String plan) throws Exception {
        var res = mvc.perform(full(post(API + "preview")).content(plan)).andExpect(status().isOk()).andReturn()
                .getResponse();
        return "{\"plan\":" + plan + ",\"hash\":" + JSON.readTree(res.getContentAsString()).get("hash")
                .toString() + ",\"requestId\":\"" + java.util.UUID.randomUUID() + "\"}";
    }

    @Test
    void createPublishReviseReplayAndRollback() throws Exception {
        try (var c = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.execute("CREATE TABLE material_bom_plan_receipt(request_id VARCHAR(36) PRIMARY KEY,actor_id BIGINT,plan_hash CHAR(64),root_id BIGINT)");
            db.execute("ALTER TABLE material_bom ALTER COLUMN id RESTART WITH 100");
            db.execute("ALTER TABLE material_bom_item ALTER COLUMN id RESTART WITH 100");
            db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(101,'A','A',1,'ENABLED'),(102,'B','B',1,'ENABLED'),(103,'C','C',1,'ENABLED')");
            var nodes = new java.util.ArrayList<BomTreePlans.Node>();
            for (int i = 0; i < 3; i++) {
                var key = String.valueOf(101 + i);
                nodes.add(new BomTreePlans.Node(key, null, null, key, "T" + key, "1", java.math.BigDecimal.ONE, null,
                        java.util.List.of(new BomTreePlans.Line(i == 2 ? "1" : String.valueOf(102 + i), java.math.BigDecimal.ONE, 0, i == 2 ? null : String.valueOf(102 + i)))));
            }
            var a = nodes.get(0);
            var branches = new java.util.ArrayList<>(a.lines());
            branches.add(new BomTreePlans.Line("103", java.math.BigDecimal.ONE, 1, "103"));
            branches.add(new BomTreePlans.Line("99", java.math.BigDecimal.ONE, 2, "1"));
            nodes.set(0, new BomTreePlans.Node(a.key(), null, null, a.product(), a.code(), "1", a.base(), null, branches));
            var existing = mvc.perform(full(get(API + "1"))).andExpect(status().isOk()).andReturn().getResponse();
            nodes.addAll(JSON.readValue(existing.getContentAsString(), BomTreePlans.Open.class).plan().nodes());
            var plan = JSON.writeValueAsString(new BomTreePlans.Plan("101", nodes));
            String payload = confirmation(mvc, plan);
            db.execute("ALTER TABLE material_bom_item ADD CONSTRAINT fail_root CHECK(material_id<>102)");
            mvc.perform(full(post(API + "confirm")).content(payload)).andExpect(status().is5xxServerError());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom", Integer.class));
            db.execute("ALTER TABLE material_bom_item DROP CONSTRAINT fail_root");
            String saved;
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var gate = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Callable<String> submit = () -> {
                    gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return mvc.perform(full(post(API + "confirm")).content(payload)).andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                };
                var first = pool.submit(submit);
                var second = pool.submit(submit);
                saved = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(saved, second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom_plan_receipt", Integer.class));
            long root = JSON.readTree(saved).get("root").asLong();
            long child = db.queryForObject("SELECT id FROM material_bom WHERE code='T103'", Long.class);
            mvc.perform(full(post("/api/basic/material-bom-item")).content(JSON.writeValueAsString(
                            java.util.Map.of("bomId",child,"materialId",101,"quantity","1","childBomId",root,"sort",1))))
                    .andExpect(status().is4xxClientError());
            mvc.perform(full(delete("/api/basic/material-bom")).content("[" + child + "]"))
                    .andExpect(status().isConflict());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM material_bom_item WHERE bom_id=?", Integer.class,child));
            mvc.perform(full(post("/api/basic/material-bom/" + root + "/publish")).content("{\"revision\":4}"))
                    .andExpect(status().isConflict());
            for (long bid : db.queryForList("SELECT id FROM material_bom WHERE id>=100 ORDER BY id", Long.class))
                mvc.perform(full(post("/api/basic/material-bom/" + bid + "/publish")).content("{\"revision\":" + (bid == root ? 4 : 2) + "}"))
                        .andExpect(status().isOk());
            var opened = mvc.perform(full(get(API + root))).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            var orig = JSON.readValue(opened, BomTreePlans.Open.class).plan();
            var revised = new java.util.ArrayList<BomTreePlans.Node>();
            for (var n : orig.nodes())
                revised.add(n.product()
                        .equals("103") ? new BomTreePlans.Node(n.key(), n.source(), n.revision(), n.product(), n.code(), n.version(), n.base(), n.remark(), java.util.List.of(new BomTreePlans.Line("1", java.math.BigDecimal.TEN, 0, null))) : n);
            db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at,is_default) VALUES(500,'OTHER','1',101,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP,0)");
            var leaf = orig.nodes().stream().filter(n -> n.product().equals("103")).findFirst().orElseThrow();
            db.update("INSERT INTO material_bom_item(bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES(500,103,1,1,?,0)", leaf.source());
            var orderId = db.queryForObject("SELECT MIN(id) FROM workorder", Long.class);
            db.update("UPDATE workorder SET bom_id=? WHERE id=?", root, orderId);
            var fix = confirmation(mvc, JSON.writeValueAsString(new BomTreePlans.Plan(orig.root(), revised)));
            mvc.perform(full(post(API + "confirm")).content(fix)).andExpect(status().isOk());
            assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM material_bom WHERE status='DRAFT'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT i.quantity FROM material_bom_item i JOIN material_bom b ON b.id=i.bom_id WHERE b.code='T103' AND b.version='1'", Integer.class));
            assertEquals(root, db.queryForObject("SELECT bom_id FROM workorder WHERE id=?", Long.class, orderId));
            assertEquals(leaf.source(), db.queryForObject("SELECT child_bom_id FROM material_bom_item WHERE bom_id=500", Long.class));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM material_bom_item WHERE child_bom_id=(SELECT id FROM material_bom WHERE code='T103' AND status='DRAFT')", Integer.class));
            mvc.perform(full(post(API + "preview")).content(JSON.writeValueAsString(new BomTreePlans.Plan(orig.root(), revised)).replace("\"version\":\"1\"", "\"version\":\"2\"")))
                    .andExpect(status().isConflict());
            mvc.perform(full(get("/api/basic/material-bom/explode")).param("materialCode", "A")
                            .param("bomId", Long.toString(root)))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("complete")
                            .value(true));
            var split = new java.util.ArrayList<BomTreePlans.Node>();
            for (var n : revised) {
                var ls = n.lines();
                if (n.key().equals(orig.root())) ls = ls.stream()
                        .map(l -> "103".equals(l.material()) ? new BomTreePlans.Line("103", l.quantity(), l.sort(), "fork") : l)
                        .toList();
                split.add(new BomTreePlans.Node(n.key(), n.source(), n.revision(), n.product(), n.code(), n.product()
                        .equals("99") ? n.version() : "split", n.base(), n.remark(), ls));
            }
            split.add(new BomTreePlans.Node("fork", leaf.source(), leaf.revision(), leaf.product(), leaf.code(), "fork", leaf.base(), null, leaf.lines()));
            String splitPlan = JSON.writeValueAsString(new BomTreePlans.Plan(orig.root(), split));
            var splitConfirm = confirmation(mvc, splitPlan);
            db.update("UPDATE material_bom SET revision=revision+1 WHERE id=?", root);
            mvc.perform(full(post(API + "confirm")).content(splitConfirm)).andExpect(status().isConflict());
            db.update("UPDATE material_bom SET revision=revision-1 WHERE id=?", root);
            mvc.perform(full(post(API + "confirm")).content(splitConfirm)).andExpect(status().isOk());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM material_bom WHERE code='T103' AND version IN ('split','fork')", Integer.class));
            nodes.set(2, new BomTreePlans.Node("103", null, null, "103", "T103", "x", java.math.BigDecimal.ONE, null, java.util.List.of(new BomTreePlans.Line("101", java.math.BigDecimal.ONE, 0, "101"))));
            mvc.perform(full(post(API + "preview")).content(JSON.writeValueAsString(new BomTreePlans.Plan("101", nodes))))
                    .andExpect(status().is4xxClientError());
        }
    }
}
