package dev.issuefixture;

import dev.mesbase.business.production.controller.WorkOrderProgressController;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import java.util.Map;
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
        jdbc.update("INSERT INTO mes_execution_completion(id,execution_id,effective_qty,released_qty,receipt_qty,accepted_qty,basis_hash,request_hash,operator_user_id,recorded_at,reason) VALUES(?,?, ?,?,?,?, ?,?,1,CURRENT_TIMESTAMP,'TEST ONLY')",
                id, executionId, quantity, quantity, quantity, quantity, "a".repeat(64), "b".repeat(64));
    }

    @Test
    void sumsFormalCompletionFactsAndKeepsFactoriesIsolated() throws Exception {
        try (var c = context()) {
            var db = c.getBean(MaterialIssueHttpTest.Fixture.class).business;
            for (var jdbc : db.values()) {
                ProductionReportHttpTest.seed(jdbc);
                jdbc.update("UPDATE workorder SET planned_qty=10 WHERE id=1");
            }
            complete(db.get("A"), "test-a-1", 1, "4");
            complete(db.get("A"), "test-a-2", 2, "1");
            complete(db.get("B"), "test-b-1", 1, "2");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].plannedQty").value("10.000000"))
                    .andExpect(jsonPath("items[0].completedQty").value("5.000000"))
                    .andExpect(jsonPath("items[0].remainingQty").value("5.000000"))
                    .andExpect(jsonPath("items[0].completionPercent").value("50.00"))
                    .andExpect(jsonPath("items[0].completedExecutions").value(2));
            mvc.perform(ProductionReportHttpTest.full(get(URL), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].completionPercent").value("20.00"));
            db.get("A").update("UPDATE workorder SET planned_qty=2 WHERE id=1");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("items[0].completionPercent").value("250.00"))
                    .andExpect(jsonPath("items[0].remainingQty").value("0"));
            complete(db.get("A"), "test-duplicate", 1, "4");
            mvc.perform(ProductionReportHttpTest.full(get(URL), "A"))
                    .andExpect(jsonPath("items[0].completionPercent").isEmpty())
                    .andExpect(jsonPath("items[0].completedQty").isEmpty())
                    .andExpect(jsonPath("items[0].issue").value("正式完工事实重复或数量异常，请核实"));
        }
    }

    @Test
    void supportsProductSearchLiteralWildcardsStatusPagingAndExactIds() throws Exception {
        try (var c = context()) {
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("UPDATE workorder SET code='TEST_%_A' WHERE id=1");
            jdbc.update("UPDATE material SET name='可检索产品' WHERE id=1");
            jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,source,create_time,update_time) VALUES(9007199254740993,'TEST-OTHER',1,0,1,'DRAFT','LOCAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            var mvc = MaterialIssueHttpTest.mvc(c);
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("keyword", "%"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].code").value("TEST_%_A"))
                    .andExpect(jsonPath("items[0].completionPercent").value("0.00"));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("keyword", "可检索").param("limit", "1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items[0].id").value("9007199254740993"))
                    .andExpect(jsonPath("items[0].completionPercent").isEmpty());
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "1").param("limit", "1"), "A"))
                    .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items[0].id").value("1"));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "2"), "A"))
                    .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items").isEmpty());
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("status", "CANCELLED"), "A"))
                    .andExpect(jsonPath("total").value(0));
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("keyword", "' OR 1=1 --"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
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
            for (String query : new String[]{"limit=101", "limit=0", "offset=-1", "status=INVALID", "keyword=" + "x".repeat(101)}) {
                mvc.perform(ProductionReportHttpTest.full(get(URL + "?" + query), "A"))
                        .andExpect(status().isBadRequest());
            }
        }
    }
}
