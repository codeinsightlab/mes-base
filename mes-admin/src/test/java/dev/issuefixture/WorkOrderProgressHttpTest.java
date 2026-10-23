package dev.issuefixture;

import dev.mesbase.business.production.controller.WorkOrderProgressController;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real routed XML and completion facts; no writes to the configured acceptance database.
 */
class WorkOrderProgressHttpTest {
    private static final String URL = "/api/production/workorder-progress";

    @Configuration
    @Import({WorkOrderProgressController.class, WorkOrderProgressQueryService.class})
    static class Extra {
        @Bean
        WorkOrderProgressExtraMapper progressMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(WorkOrderProgressExtraMapper.class);
            return new SqlSessionTemplate(factory).getMapper(WorkOrderProgressExtraMapper.class);
        }
    }

    static AnnotationConfigWebApplicationContext context() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        c.refresh();
        return c;
    }

    private static void complete(JdbcTemplate jdbc, String id, long executionId, String quantity) {
        jdbc.update("UPDATE mes_production_execution SET status='FULLY_RECEIVED' WHERE id=?", executionId);
        jdbc.update("INSERT INTO mes_execution_completion(id,execution_id,effective_qty,released_qty,receipt_qty,accepted_qty,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES(?,?, ?,?,?,?, ?,?,1,CURRENT_TIMESTAMP,'TEST ONLY')",
                id, executionId, quantity, quantity, quantity, quantity, "a".repeat(64), "b".repeat(64));
    }

    private static void declaration(JdbcTemplate jdbc, String id, int revision, String action, String quantity) {
        jdbc.update("INSERT INTO mes_production_defect(declaration_id,revision_no,record_action,execution_id,affected_node_id,defect_qty,waste_category,defect_reason,request_key,request_fingerprint,reported_by,occurred_at,recorded_by,recorded_at) VALUES(?,?,?,1,11,?,'PROCESS','TEST ONLY',?,?,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)",
                id, revision, action, quantity, UUID.randomUUID().toString(), "d".repeat(64));
    }

    private static void treatment(JdbcTemplate jdbc, String declaration, int revision, String action,
                                  String attribution) {
        treatment(jdbc, declaration, revision, action, attribution, "0");
    }

    private static void treatment(JdbcTemplate jdbc, String declaration, int revision, String action,
                                  String attribution, String returned) {
        jdbc.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,attribution_type,returned_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,?,1,11,?,?,?,1,CURRENT_TIMESTAMP,'TEST ONLY')",
                UUID.randomUUID()
                        .toString(), "t".repeat(64), declaration, revision, action, attribution, returned);
    }

    @Test
    void currentDispositionQuantitiesUseOnlyLatestIndependentVersions() throws Exception {
        try (var c = context()) {
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            ProductionReportHttpTest.seed(jdbc);
            jdbc.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            complete(jdbc, "completed", 1, "90");
            declaration(jdbc, "independent", 1, "CREATE", "10");
            treatment(jdbc, "independent", 1, "ASSESS", "INDEPENDENT");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].qualityUntreatedQty").value("10.000000"));
            treatment(jdbc, "independent", 1, "RETURN_NODE", null, "4");
            jdbc.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,transfer_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,11,'TRANSFER_WAREHOUSE',6,1,CURRENT_TIMESTAMP,'TEST ONLY')", UUID.randomUUID()
                    .toString(), "t".repeat(64), "independent");
            declaration(jdbc, "overlap", 1, "CREATE", "2");
            treatment(jdbc, "overlap", 1, "ASSESS", "OVERLAP");
            declaration(jdbc, "unverified", 1, "CREATE", "3");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].qualityUntreatedQty").value("0.000000"))
                    .andExpect(jsonPath("items[0].qualityReturnedQty").value("4.000000"))
                    .andExpect(jsonPath("items[0].qualityTransferredQty").value("6.000000"))
                    .andExpect(jsonPath("items[0].qualityUnverifiedQty").value("3.000000"))
                    .andExpect(jsonPath("items[0].pendingCompletionQty").value("4.000000"));
            declaration(jdbc, "independent", 2, "VOID", "10");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].qualityTransferredQty").value("0"));
        }
    }

    @Test
    void sumsFormalCompletionFactsAndKeepsFactoriesIsolated() throws Exception {
        try (var c = context()) {
            var db = c.getBean(MaterialIssueHttpTest.Fixture.class).business;
            for (var jdbc : db.values()) {
                ProductionReportHttpTest.seed(jdbc);
                jdbc.update("UPDATE workorder SET planned_qty=10,create_time='2026-10-01 09:30:45' WHERE id=1");
            }
            complete(db.get("A"), "test-a-1", 1, "4");
            complete(db.get("A"), "test-a-2", 2, "1");
            complete(db.get("B"), "test-b-1", 1, "2");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].createTime").value("2026-10-01T09:30:45"))
                    .andExpect(jsonPath("items[0].plannedQty").value("10.000000"))
                    .andExpect(jsonPath("items[0].completedQty").value("5.000000"))
                    .andExpect(jsonPath("items[0].goodOutputGapQty").value("5.000000"))
                    .andExpect(jsonPath("items[0].pendingCompletionQty").value("5.000000"))
                    .andExpect(jsonPath("items[0].completionPercent").value("50.00"))
                    .andExpect(jsonPath("items[0].completedExecutions").value(2));
            mvc.perform(ProductionReportHttpTest.full(get(URL), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].completionPercent").value("20.00"));
            db.get("A").update("UPDATE workorder SET planned_qty=2 WHERE id=1");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("items[0].completionPercent").value("250.00"))
                    .andExpect(jsonPath("items[0].pendingCompletionQty").value("0"));
            complete(db.get("A"), "test-next-cycle", 1, "4");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("items[0].completionPercent").value("250.00"))
                    .andExpect(jsonPath("items[0].completedQty").value("5.000000"));
            complete(db.get("A"), "zz-invalid-latest", 1, "-1");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("items[0].completionPercent").isEmpty())
                    .andExpect(jsonPath("items[0].completedQty").isEmpty())
                    .andExpect(jsonPath("items[0].issue").value("正式完工事实重复或数量异常，请核实"));
        }
    }

    @Test
    void receiptClosedContributionCountsGoodStockAndNetLossWithoutAddingQualityStockTwice() throws Exception {
        try (var c = context()) {
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            ProductionReportHttpTest.seed(j);
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            complete(j, "TEST ONLY receipt closure90", 1, "90");
            declaration(j, "TEST ONLY upstream loss", 1, "CREATE", "10");
            treatment(j, "TEST ONLY upstream loss", 1, "ASSESS", "INDEPENDENT");
            j.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,transfer_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,11,'TRANSFER_WAREHOUSE',10,1,CURRENT_TIMESTAMP,'TEST ONLY')",
                    UUID.randomUUID().toString(), "t".repeat(64), "TEST ONLY upstream loss");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].completedQty").value("100.000000"))
                    .andExpect(jsonPath("items[0].completionPercent").value("100.00"))
                    .andExpect(jsonPath("items[0].goodOutputGapQty").value("10.000000"))
                    .andExpect(jsonPath("items[0].pendingCompletionQty").value("0.000000"));
            // A running execution is not counted as receipt-closed merely because a completion row exists.
            j.update("UPDATE mes_production_execution SET status='RUNNING' WHERE id=1");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].completedQty").value("0"))
                    .andExpect(jsonPath("items[0].completedExecutions").value(0));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @Test
    void supportsExplicitCodePrefixesLiteralWildcardsAndStatusPaging() throws Exception {
        try (var c = context()) {
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("UPDATE workorder SET code='TEST_%_A' WHERE id=1");
            jdbc.update("UPDATE material SET code='PRD_%_A',name='可检索产品' WHERE id=1");
            jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,source,create_time,update_time) VALUES(9007199254740993,'TEST-OTHER',1,0,1,'DRAFT','LOCAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("workorderCode", "TEST_%"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].code").value("TEST_%_A"))
                    .andExpect(jsonPath("items[0].completionPercent").value("0.00"));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("productCode", "PRD_%").param("limit", "1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items[0].id").value("9007199254740993"))
                    .andExpect(jsonPath("items[0].completionPercent").isEmpty());
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "1").param("limit", "1"), "A"))
                    .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items[0].id").value("1"));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "2"), "A"))
                    .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items").isEmpty());
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("status", "CANCELLED"), "A"))
                    .andExpect(jsonPath("total").value(0));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("workorderCode", "' OR 1=1 --"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("workorderCode", "%"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("workorderCode", "TEST_")
                            .param("productCode", "WRONG"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("workorderCode", " TEST_% ")
                            .param("productCode", " PRD_% "), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
            jdbc.update("UPDATE workorder SET status='CANCELLED' WHERE id=1");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("total").value(1));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("status", "ALL"), "A"))
                    .andExpect(jsonPath("total").value(2));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("status", "CANCELLED"), "A"))
                    .andExpect(jsonPath("total").value(1));
        }
    }

    @Test
    void rejectsUnauthorisedAndInvalidQueries() throws Exception {
        try (var c = context()) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(get(URL).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(URL).header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            for (String query : new String[]{"limit=101", "limit=0", "offset=-1", "status=INVALID",
                    "workorderCode=" + "x".repeat(65), "productCode=" + "x".repeat(65)}) {
                mvc.perform(ProductionReportHttpTest.full(get(URL + "?" + query), "A"))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    void warehouseTransfersAndReturnedWorkAreDistinctFromScrapAndUntreatedDefects() throws Exception {
        try (var c = context()) {
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            ProductionReportHttpTest.seed(j);
            j.update("UPDATE workorder SET planned_qty=100 WHERE id=1");
            complete(j, "TEST ONLY completed90", 1, "90");
            declaration(j, "TEST ONLY split disposition", 1, "CREATE", "10");
            treatment(j, "TEST ONLY split disposition", 1, "ASSESS", "INDEPENDENT");
            treatment(j, "TEST ONLY split disposition", 1, "RETURN_NODE", null, "4");
            j.update("INSERT INTO mes_quality_treatment(id,request_hash,declaration_id,declaration_revision,execution_id,routing_node_id,action_type,transfer_qty,operator_user_id,recorded_at,reason) VALUES(?,?,?,1,1,11,'TRANSFER_WAREHOUSE',6,1,CURRENT_TIMESTAMP,'TEST ONLY')", UUID.randomUUID()
                    .toString(), "t".repeat(64), "TEST ONLY split disposition");
            MaterialIssueHttpTest.mvc(c).perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].qualityTransferredQty").value("6.000000"))
                    .andExpect(jsonPath("items[0].qualityUntreatedQty").value("0.000000"))
                    .andExpect(jsonPath("items[0].pendingCompletionQty").value("4.000000"));
        }
    }

}
