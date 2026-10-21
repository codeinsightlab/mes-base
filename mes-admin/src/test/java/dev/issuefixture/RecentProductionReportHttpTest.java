package dev.issuefixture;

import dev.mesbase.business.production.controller.RecentProductionReportController;
import dev.mesbase.business.production.dao.RecentProductionReportExtraMapper;
import dev.mesbase.business.production.service.RecentProductionReportQueryService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: routed A/B databases, real XML and HTTP authorization; center identity is fixture data.
 */
class RecentProductionReportHttpTest {
    private static final String URL = "/api/production/reports/recent";

    @Configuration
    @Import({RecentProductionReportController.class, RecentProductionReportQueryService.class})
    static class Extra {
        @Bean
        RecentProductionReportExtraMapper recentMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(RecentProductionReportExtraMapper.class);
            return new SqlSessionTemplate(factory).getMapper(RecentProductionReportExtraMapper.class);
        }
    }

    static AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        context.register(MaterialIssueHttpTest.Web.class, Extra.class);
        context.refresh();
        return context;
    }

    private static void seed(JdbcTemplate jdbc, String code, String quantity) {
        ProductionReportHttpTest.seed(jdbc);
        jdbc.update("UPDATE workorder SET code=? WHERE id=1", code);
        jdbc.update("INSERT INTO mes_production_report(id,execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,create_time,reason,request_id,command_fingerprint) VALUES(9007199254740993,1,11,'REPORT',?,100,1,'2026-10-20 09:00:00','2026-10-20 10:00:00','TEST ONLY',?, 'test')", quantity, code);
        jdbc.update("INSERT INTO mes_production_report(id,execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,create_time,reason,request_id,command_fingerprint) VALUES(9007199254740994,1,11,'ADJUSTMENT',-1,9007199254740993,100,1,'2026-10-19 09:00:00','2026-10-19 10:00:00','TEST ONLY',?, 'test')", code + "-adjust");
    }

    @Test
    void returnsBoundedRecentLedgerWithCorrectionsPrecisionAndFactoryIsolation() throws Exception {
        try (var context = context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business;
            seed(jdbc.get("A"), "TEST-A", "4.123456");
            seed(jdbc.get("B"), "TEST-B", "2.654321");
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("limit", "1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items[0].id").value("9007199254740994"))
                    .andExpect(jsonPath("items[0].recordType").value("ADJUSTMENT"))
                    .andExpect(jsonPath("items[0].quantityDelta").value("-1.000000"))
                    .andExpect(jsonPath("items[0].workorderCode").value("TEST-A"))
                    .andExpect(jsonPath("items[0].operationName").value("首工序"));
            for (String factory : new String[]{"A", "B"}) {
                mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "1").param("limit", "1"), factory))
                        .andExpect(status().isOk()).andExpect(jsonPath("items[0].id").value("9007199254740993"))
                        .andExpect(jsonPath("items[0].workorderCode").value("TEST-" + factory))
                        .andExpect(jsonPath("items[0].quantityDelta").value(factory.equals("A") ? "4.123456" : "2.654321"));
            }
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "2"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items").isEmpty());
            assertEquals(2, jdbc.get("A").queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void rejectsMissingScopeWrongPermissionAndUnboundedPages() throws Exception {
        try (var context = context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(get(URL).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(URL).header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            mvc.perform(ProductionReportHttpTest.full(get(URL), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            for (String limit : new String[]{"0", "101", "-1"}) {
                mvc.perform(ProductionReportHttpTest.full(get(URL).param("limit", limit), "A"))
                        .andExpect(status().isBadRequest());
            }
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "-1"), "A"))
                    .andExpect(status().isBadRequest());
        }
    }
}
