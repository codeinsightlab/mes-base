package dev.issuefixture;

import dev.mesbase.business.production.controller.RecentProductionReportController;
import dev.mesbase.business.production.dao.RecentProductionReportExtraMapper;
import dev.mesbase.business.production.service.RecentProductionReportQueryService;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.plugin.Invocation;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class SqlCapture implements Interceptor {
        final List<String> statements = new ArrayList<>();

        public Object intercept(Invocation invocation) throws Throwable {
            statements.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql().toLowerCase());
            return invocation.proceed();
        }

        void assertNoCount() {
            assertTrue(statements.stream().noneMatch(sql -> sql.contains("count(")), statements.toString());
        }
    }

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
            var capture = new SqlCapture();
            context.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(capture);
            for (String factory : new String[]{"A", "B"}) {
                mvc.perform(ProductionReportHttpTest.full(get(URL), factory))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                        .andExpect(jsonPath("[0].id").value("9007199254740994"))
                        .andExpect(jsonPath("[0].recordType").value("ADJUSTMENT"))
                        .andExpect(jsonPath("[0].quantityDelta").value("-1.000000"))
                        .andExpect(jsonPath("[0].workorderCode").value("TEST-" + factory))
                        .andExpect(jsonPath("[0].operationName").value("首工序"))
                        .andExpect(jsonPath("[1].quantityDelta").value(factory.equals("A") ? "4.123456" : "2.654321"));
            }
            capture.assertNoCount();
            for (int i = 0; i < 12; i++)
                jdbc.get("A")
                        .update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,create_time,reason,request_id,command_fingerprint) VALUES(1,11,'REPORT',1,100,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST ONLY',?, 'test')", "bounded-" + i);
            mvc.perform(ProductionReportHttpTest.full(get(URL).param("offset", "100").param("limit", "1000"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
            capture.assertNoCount();
            assertEquals(14, jdbc.get("A").queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void rejectsMissingScopeWrongPermissionAndReturnsEmptyArray() throws Exception {
        try (var context = context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(get(URL).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(URL).header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            mvc.perform(ProductionReportHttpTest.full(get(URL), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        }
    }
}
