package dev.issuefixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mesbase.business.quality.controller.OperationDefectTypeController;
import dev.mesbase.business.quality.controller.ProductionDefectV2Controller;
import dev.mesbase.business.quality.controller.DefectStatisticsController;
import dev.mesbase.business.quality.service.OperationDefectTypeService;
import dev.mesbase.business.quality.service.DefectStatisticsService;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: two isolated H2 business databases, real Spring services, mapper XML and HTTP authorization.
 */
class QualityV2HttpTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String ROOT = "/api/quality/defects";
    static final String TYPES = "/api/production/operation/1/defect-types";

    @Configuration
    @Import({OperationDefectTypeController.class, OperationDefectTypeService.class, ProductionDefectV2Controller.class,
            DefectStatisticsController.class, DefectStatisticsService.class})
    static class Extra {
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        return ProductionDefectHttpTest.context(Extra.class);
    }

    static String config(String code, String cost, Integer revision, String state) {
        return "{\"code\":\"" + code + "\",\"name\":\"划伤\",\"defaultCostCategory\":\"" + cost + "\",\"status\":\"" + state + "\",\"sort\":0" + (revision == null ? "" : ",\"expectedRevision\":" + revision) + "}";
    }

    static String addType(MockMvc m, String factory, String code, String cost) throws Exception {
        var response = m.perform(full(post(TYPES), factory).content(config(code, cost, null, "ENABLED")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).path("id").asText();
    }

    static ObjectNode declaration(String type, String cost) {
        var n = JSON.createObjectNode();
        n.put("requestId", UUID.randomUUID().toString());
        n.put("executionId", "1");
        n.put("nodeId", "12");
        n.put("defectTypeId", type);
        n.put("expectedTypeRevision", 0);
        n.put("quantity", "1");
        n.put("costCategory", cost);
        n.put("occurredAt", "2026-01-01T10:00:00");
        return n;
    }

    @Test
    void configScopesUniquenessRevisionAndHistoricalReferences() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            var type = addType(m, "A", "SCRATCH", "LABOR");
            m.perform(full(post(TYPES), "A").content(config("scratch", "LABOR", null, "ENABLED")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("DEFECT_TYPE_DUPLICATE"));
            jdbc.update("INSERT INTO operation(id,code,name,status,sort,revision) VALUES(2,'O2','打磨','ENABLED',0,0)");
            var other = JSON.readTree(m.perform(full(post(TYPES.replace("/1/", "/2/")), "A").content(config("SCRATCH", "BOTH", null, "ENABLED")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
            assertNotEquals(type, other);
            m.perform(full(post(TYPES), "A").content(config("UNKNOWN-COST", "UNKNOWN", null, "ENABLED")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(ROOT + "/declarations"), "A").content(declaration(other, "BOTH").toString()))
                    .andExpect(status().isNotFound());
            m.perform(full(put(TYPES.replace("/1/", "/2/") + "/" + type), "A").content(config("MOVE", "LABOR", 0, "ENABLED")))
                    .andExpect(status().isNotFound());
            m.perform(full(delete(TYPES.replace("/1/", "/2/") + "/" + other), "A").param("expectedRevision", "0"))
                    .andExpect(status().isNoContent());
            m.perform(full(post(ROOT + "/declarations"), "A").content(declaration(type, "LABOR").toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("record.wasteCategory").doesNotExist())
                    .andExpect(jsonPath("record.costCategory").value("LABOR"));
            m.perform(full(delete(TYPES + "/" + type), "A").param("expectedRevision", "0"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("DEFECT_TYPE_REFERENCED"));
            m.perform(full(put(TYPES + "/" + type), "A").content(config("NEW", "MATERIAL", 0, "DISABLED")))
                    .andExpect(status().isOk());
            m.perform(full(put(TYPES + "/" + type), "A").content(config("NEW", "LABOR", 0, "ENABLED")))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/declarations"), "A").content(declaration(type, "LABOR").toString()))
                    .andExpect(status().isConflict());
            var disabled = declaration(type, "MATERIAL");
            disabled.put("expectedTypeRevision", 1);
            m.perform(full(post(ROOT + "/declarations"), "A").content(disabled.toString()))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(full(get(ROOT), "A")).andExpect(jsonPath("items[0].defectTypeCodeSnapshot").value("SCRATCH"))
                    .andExpect(jsonPath("items[0].costCategory").value("LABOR"));
            var b = addType(m, "B", "BONLY", "BOTH");
            assertEquals(type, b);
            m.perform(full(get(TYPES), "A")).andExpect(jsonPath("items[0].code").value("NEW"));
            m.perform(full(get(TYPES), "B")).andExpect(jsonPath("items[0].code").value("BONLY"));
        }
    }

    @Test
    void declarationDefaultOverridePathAndReplayDoNotRewriteFrozenFacts() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var type = addType(m, "A", "D", "LABOR");
            var body = declaration(type, "LABOR");
            var response = m.perform(full(post(ROOT + "/declarations"), "A").content(body.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var id = JSON.readTree(response).path("record").path("declarationId").asText();
            m.perform(full(put(TYPES + "/" + type), "A").content(config("D", "BOTH", 0, "DISABLED")))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT + "/declarations"), "A").content(body.toString())).andExpect(status().isOk())
                    .andExpect(jsonPath("record.declarationId").value(id))
                    .andExpect(jsonPath("record.costCategory").value("LABOR"));
            body.put("quantity", "2");
            m.perform(full(post(ROOT + "/declarations"), "A").content(body.toString()))
                    .andExpect(status().isConflict());
            m.perform(full(get(ROOT + "/declarations/requests/" + body.path("requestId").asText()), "A"))
                    .andExpect(jsonPath("declarationId").value(id));
            m.perform(full(post(ROOT + "/" + id + "/void"), "A").content("{\"requestKey\":\"legacy-void-v2\",\"expectedRevision\":1,\"revisionReason\":\"保留快照\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("record.costCategory").value("LABOR"));
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void ordinaryCreateCannotSelfAssessOrOverrideCostButAuthorizedCreateCan() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var type = addType(m, "A", "D", "LABOR");
            var justCreate = declaration(type, "LABOR");
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer REVISE").header("X-Factory-Id", "A")
                    .contentType("application/json").content(justCreate.toString())).andExpect(status().isForbidden());
            m.perform(get(ROOT + "/executions/1/nodes/12/declaration-context").header("Authorization", "Bearer CREATE")
                            .header("X-Factory-Id", "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("canOverrideCost").value(false));
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer CREATE").header("X-Factory-Id", "A")
                    .contentType("application/json").content(justCreate.toString())).andExpect(status().isOk());
            var body = declaration(type, "MATERIAL");
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
            var before = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class);
            body.put("costCategory", "LABOR");
            body.set("assessment", JSON.readTree("{\"type\":\"INDEPENDENT\",\"basis\":\"现场核实\"}"));
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
            assertEquals(before, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
            m.perform(full(post(ROOT + "/declarations"), "A").content(body.toString())).andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A").param("executionId", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].independentQty").value("1.000000"))
                    .andExpect(jsonPath("items[0].netDefectQty").value("1.000000"));
            var material = declaration(type, "MATERIAL");
            m.perform(full(post(ROOT + "/declarations"), "A").content(material.toString())).andExpect(status().isOk());
            var both = declaration(type, "BOTH");
            m.perform(full(post(ROOT + "/declarations"), "A").content(both.toString())).andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A")).andExpect(jsonPath("total").value(3));
            var bad = declaration(type, "LABOR");
            bad.put("nodeId", 999);
            m.perform(full(post(ROOT + "/declarations"), "A").content(bad.toString())).andExpect(status().isConflict());
            m.perform(post(TYPES).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                            .contentType("application/json").content(config("X", "LABOR", null, "ENABLED")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void concurrentSameCodeAndSameUuidHaveOneWinnerAndNoDuplicateObservation() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var barrier = new CyclicBarrier(2);
            Callable<Integer> create = () -> {
                barrier.await();
                return m.perform(full(post(TYPES), "A").content(config("RACE", "LABOR", null, "ENABLED"))).andReturn()
                        .getResponse().getStatus();
            };
            var a = pool.submit(create);
            var b = pool.submit(create);
            var statuses = List.of(a.get(), b.get());
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            var id = jdbc.queryForObject("SELECT id FROM operation_defect_type WHERE code='RACE'", Long.class)
                    .toString();
            var body = declaration(id, "LABOR").toString();
            Callable<Integer> declare = () -> {
                barrier.await();
                return m.perform(full(post(ROOT + "/declarations"), "A").content(body)).andReturn().getResponse()
                        .getStatus();
            };
            var x = pool.submit(declare);
            var y = pool.submit(declare);
            assertEquals(200, x.get());
            assertEquals(200, y.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
        }
    }

    @Test
    void statisticsPreserveAttributionRecoveryLegacyUnknownAndLatestRevision() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var type = addType(m, "A", "S", "LABOR");
            var independent = declaration(type, "LABOR");
            independent.put("quantity", "4");
            independent.set("assessment", JSON.readTree("{\"type\":\"INDEPENDENT\",\"basis\":\"独立实物核实\"}"));
            String declaration = JSON.readTree(m.perform(full(post(ROOT + "/declarations"), "A").content(independent.toString()))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("record")
                    .path("declarationId").asText();
            var overlap = declaration(type, "LABOR");
            overlap.put("quantity", "2");
            var assessment = JSON.createObjectNode();
            assessment.put("type", "OVERLAP");
            assessment.put("relatedDeclarationId", declaration);
            assessment.put("basis", "同一批观察");
            overlap.set("assessment", assessment);
            m.perform(full(post(ROOT + "/declarations"), "A").content(overlap.toString())).andExpect(status().isOk());
            m.perform(full(post(ROOT + "/declarations"), "A").content(declaration(type, "LABOR").toString()))
                    .andExpect(status().isOk());
            String allocation = ProductionQualityV13HttpTest.command("ALLOCATE", null, "2", "2", "0", "0", "0");
            var allocated = JSON.readTree(m.perform(full(post(ROOT + "/" + declaration + "/treatment"), "A").content(allocation))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String parent = allocated.path("events").get(1).path("id").asText();
            var started = JSON.readTree(m.perform(full(post(ROOT + "/" + declaration + "/treatment"), "A").content(ProductionQualityV13HttpTest.command("START", parent, "0", "2", "0", "0", "0")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String start = started.path("events").get(2).path("id").asText();
            m.perform(full(post(ROOT + "/" + declaration + "/treatment"), "A").content(ProductionQualityV13HttpTest.command("RESULT", start, "0", "0", "2", "1", "1")))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A").param("costCategory", "LABOR"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].declaredQty").value("7.000000"))
                    .andExpect(jsonPath("items[0].independentQty").value("4.000000"))
                    .andExpect(jsonPath("items[0].overlapQty").value("2.000000"))
                    .andExpect(jsonPath("items[0].unverifiedQty").value("1.000000"))
                    .andExpect(jsonPath("items[0].recoveredQty").value("1.000000"))
                    .andExpect(jsonPath("items[0].netDefectQty").value("3.000000"));
            m.perform(full(get(ROOT + "/statistics"), "A").param("attribution", "UNVERIFIED"))
                    .andExpect(jsonPath("items[0].declaredQty").value("1.000000"));
            m.perform(full(put(TYPES + "/" + type), "A").content(config("S", "LABOR", 0, "ENABLED").replace("划伤", "新划伤")))
                    .andExpect(status().isOk());
            var afterRename = declaration(type, "LABOR");
            afterRename.put("expectedTypeRevision", 1);
            m.perform(full(post(ROOT + "/declarations"), "A").content(afterRename.toString()))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A").param("costCategory", "LABOR"))
                    .andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].typeNameVariants").value(2));
            String legacyBody = ProductionDefectHttpTest.command("old-key-not-a-uuid", 0, "2");
            String legacy = ProductionDefectHttpTest.add(m, legacyBody, "A");
            m.perform(full(post(ROOT), "A").content(legacyBody)).andExpect(status().isOk())
                    .andExpect(jsonPath("record.declarationId").value(legacy))
                    .andExpect(jsonPath("record.costCategory").value("UNKNOWN"));
            var missingLegacyCategory = (ObjectNode) JSON.readTree(ProductionDefectHttpTest.command("missing-old-category", 1, "3"));
            ((ObjectNode) missingLegacyCategory.path("facts")).putNull("wasteCategory");
            m.perform(full(post(ROOT + "/" + legacy + "/revisions"), "A").content(missingLegacyCategory.toString()))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(ROOT + "/" + legacy + "/revisions"), "A").content(ProductionDefectHttpTest.command("old-revision", 1, "3")))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A").param("costCategory", "UNKNOWN"))
                    .andExpect(jsonPath("items[0].declaredQty").value("3.000000"));
            m.perform(full(get(ROOT + "/statistics"), "A").param("from", "2026-02-01T00:00:00"))
                    .andExpect(jsonPath("total").value(0));
            m.perform(full(post(ROOT + "/" + legacy + "/void"), "A").content("{\"requestKey\":\"old-void\",\"expectedRevision\":2,\"revisionReason\":\"测试作废\"}"))
                    .andExpect(status().isOk());
            m.perform(full(get(ROOT + "/statistics"), "A").param("costCategory", "UNKNOWN"))
                    .andExpect(jsonPath("total").value(0));
        }
    }

    @Test
    void createAndAssessmentPermissionsStayIndependentAcrossBothApis() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var type = addType(m, "A", "AUTH", "LABOR");
            var old = (ObjectNode) JSON.readTree(ProductionDefectHttpTest.command("create-only-key", 0, "1"));
            m.perform(post(ROOT).header("Authorization", "Bearer CREATE").header("X-Factory-Id", "A")
                    .contentType("application/json").content(old.toString())).andExpect(status().isForbidden());
            old.remove("assessment");
            String id = JSON.readTree(m.perform(post(ROOT).header("Authorization", "Bearer CREATE")
                            .header("X-Factory-Id", "A")
                            .contentType("application/json").content(old.toString())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("record").path("declarationId").asText();
            var assess = (ObjectNode) JSON.readTree(ProductionQualityV13HttpTest.command("ASSESS", null, "0", "0", "0", "0", "0"));
            assess.set("assessment", JSON.readTree("{\"type\":\"INDEPENDENT\",\"basis\":\"授权人员核实\"}"));
            m.perform(post(ROOT + "/" + id + "/treatment").header("Authorization", "Bearer CREATE")
                    .header("X-Factory-Id", "A")
                    .contentType("application/json").content(assess.toString())).andExpect(status().isForbidden());
            m.perform(post(ROOT + "/" + id + "/treatment").header("Authorization", "Bearer REVISE")
                            .header("X-Factory-Id", "A")
                            .contentType("application/json").content(assess.toString())).andExpect(status().isOk())
                    .andExpect(jsonPath("attribution").value("INDEPENDENT"));
            var typed = declaration(type, "LABOR");
            typed.set("assessment", JSON.readTree("{\"type\":\"INDEPENDENT\",\"basis\":\"同事务授权核实\"}"));
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer CREATE").header("X-Factory-Id", "A")
                    .contentType("application/json").content(typed.toString())).andExpect(status().isForbidden());
            m.perform(post(ROOT + "/declarations").header("Authorization", "Bearer REVISE").header("X-Factory-Id", "A")
                    .contentType("application/json").content(typed.toString())).andExpect(status().isForbidden());
            m.perform(full(post(ROOT + "/declarations"), "A").content(typed.toString())).andExpect(status().isOk());
            assertEquals(2, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_production_defect", Integer.class));
        }
    }

}
