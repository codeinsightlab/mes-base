package dev.issuefixture;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.util.UUID;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Real HTTP, XML and transactions on routed H2. Synthetic gross-processed report facts only.
 */
class ProductionQualityQuantityHttpTest {
    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = ProductionReportingHttpTest.context();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            j.update("UPDATE mes_production_execution SET target_qty=100");
            ProductionInputTestData.confirmed(j, 1, new BigDecimal("100"));
            ProductionInputTestData.confirmed(j, 2, new BigDecimal("100"));
        }
        return c;
    }

    static String command(long node, String qty) {
        return ProductionDefectHttpTest.command(UUID.randomUUID().toString(), 0, qty)
                .replace("affectedNodeId\":\"13", "affectedNodeId\":\"" + node);
    }

    static long report(MockMvc m, long node, String qty) throws Exception {
        var r = m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(node, qty)))
                .andExpect(status().isOk()).andReturn();
        return new ObjectMapper().readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    static String quality(MockMvc m, long node, String qty) throws Exception {
        return ProductionDefectHttpTest.add(m, command(node, qty), "A");
    }

    static void adjust(MockMvc m, long original, String delta, int expected) throws Exception {
        m.perform(full(post("/api/production/reports/" + original + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"" + delta + "\",\"reason\":\"TEST ONLY订正\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    void formulasVersionsAndVoidUseTheDeclarationNode() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            report(m, 11, "100");
            var a = quality(m, 11, "5");
            report(m, 12, "95");
            quality(m, 12, "3");
            report(m, 13, "90");
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[0].effectiveOutputQty").value("95.000000"))
                    .andExpect(jsonPath("nodes[1].inputQty").value("95.000000"))
                    .andExpect(jsonPath("nodes[2].inputQty").value("92.000000"))
                    .andExpect(jsonPath("nodes[2].availableQty").value("2.000000"))
                    .andExpect(jsonPath("nodes[0].qualityIntegrated").value(true));
            String revision = ProductionDefectHttpTest.command("revision", 1, "4")
                    .replace("affectedNodeId\":\"13", "affectedNodeId\":\"11");
            for (int i = 0; i < 2; i++)
                m.perform(full(post("/api/quality/defects/" + a + "/revisions"), "A").content(revision))
                        .andExpect(status().isOk()).andExpect(jsonPath("record.affectedNodeId").value("11"));
            m.perform(full(get("/api/quality/defects/executions/1/totals"), "A"))
                    .andExpect(jsonPath("[?(@.affectedNodeId == '11') ].effectiveDefectQty").value(hasItem("4.000000")));
            var voidBody = "{\"requestKey\":\"void\",\"expectedRevision\":2,\"revisionReason\":\"TEST释放容量\"}";
            for (int i = 0; i < 2; i++)
                m.perform(full(post("/api/quality/defects/" + a + "/void"), "A").content(voidBody))
                        .andExpect(status().isOk());
            assertEquals(3, ProductionReportingHttpTest.db(c)
                    .queryForObject("SELECT COUNT(*) FROM mes_production_defect WHERE declaration_id=?", Integer.class, a));
            assertEquals(3, ProductionReportingHttpTest.db(c)
                    .queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[0].defectQty").value("0"))
                    .andExpect(jsonPath("nodes[0].effectiveOutputQty").value("100.000000"));
        }
    }

    @Test
    void lateDefectPreservesReportsAndNewWritesStillRespectEffectiveInput() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            long a = report(m, 11, "100");
            long b = report(m, 12, "98");
            var j = ProductionReportingHttpTest.db(c);
            var before = j.queryForList("SELECT * FROM mes_production_report ORDER BY id");
            quality(m, 11, "5");
            assertEquals(before, j.queryForList("SELECT * FROM mes_production_report ORDER BY id"));
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[1].historicalOverrunQty").value("3.000000"))
                    .andExpect(jsonPath("nodes[1].effectiveOutputQty").value("95.000000"));
            adjust(m, b, "1", 409);
            adjust(m, b, "-3", 200);
            adjust(m, a, "-1", 409);
            m.perform(full(post("/api/quality/defects"), "A").content(command(11, "100")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void declarationsCountProductsByAffectedNodeAndDoNotCountReasonTextOrResponsibility() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            report(m, 11, "100");
            var facts = command(11, "5").replace("TEST ONLY缺陷", "裂纹、划伤、色差")
                    .replace("\"wasteCategory\"", "\"wasteCategory\"");
            ProductionDefectHttpTest.add(m, facts, "A");
            quality(m, 11, "3");
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("nodes[0].defectQty").value("8.000000"))
                    .andExpect(jsonPath("nodes[0].effectiveOutputQty").value("92.000000"))
                    .andExpect(jsonPath("nodes[1].inputQty").value("92.000000"))
                    .andExpect(jsonPath("nodes[2].defectQty").value("0"));
            m.perform(full(post("/api/quality/defects"), "A").content(command(99, "1")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void formalSubmissionUsesQualityCapacityAndCrossFactorySameIdIsIndependent() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            ProductionReportingHttpTest.confirmIssue(m);
            ProductionReportingHttpTest.submit(m, 11, "100");
            quality(m, 11, "5");
            m.perform(full(post(ProductionReportingHttpTest.ROOT), "A").content(ProductionReportingHttpTest.body(12, "96")))
                    .andExpect(status().isConflict());
            ProductionReportingHttpTest.submit(m, 12, "95");
            m.perform(full(get("/api/production/report-quantities/executions/1"), "B")).andExpect(status().isOk())
                    .andExpect(jsonPath("nodes[0].defectQty").value("0"))
                    .andExpect(jsonPath("nodes[1].inputQty").value("0"));
        }
    }

    @Test
    void qualityQueryFailureCannotReturnZeroOrAcceptReport() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReportingHttpTest.db(c);
            j.execute("ALTER TABLE mes_production_defect RENAME TO missing_quality");
            try {
                m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                        .andExpect(status().isInternalServerError());
                m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(11, "1")))
                        .andExpect(status().isInternalServerError());
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            } finally {
                j.execute("ALTER TABLE missing_quality RENAME TO mes_production_defect");
            }
        }
    }
}
