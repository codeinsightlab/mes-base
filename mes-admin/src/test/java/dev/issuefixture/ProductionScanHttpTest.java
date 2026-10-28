package dev.issuefixture;

import dev.mesbase.business.production.controller.ProductionScanController;
import dev.mesbase.business.production.dao.MesProductionScanActivityMapper;
import dev.mesbase.business.production.domain.ProductionScanInputMethod;
import dev.mesbase.business.production.domain.ProductionScanType;
import dev.mesbase.business.production.service.ProductionScanService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY real HTTP, Spring transaction proxies, OwnedTransactionManager and mapped SQL on two routed H2 DBs.
 */
class ProductionScanHttpTest {
    private static final String ROOT = "/api/production/scan";

    private static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY scan", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, ProductionReportingHttpTest.Extra.class,
                ProductionDefectHttpTest.Extra.class, ProductionScanService.class, ProductionScanController.class);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            j.update("UPDATE mes_production_execution SET batch_no='000LOT' WHERE id=1");
        }
        return c;
    }

    private static JdbcTemplate db(AnnotationConfigWebApplicationContext c, String factory) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get(factory);
    }

    private static String code(String batch) {
        return "WO1:M1#" + batch;
    }

    private static String body(String type, String batch, String method) {
        return "{\"type\":\"" + type + "\",\"code\":\"" + code(batch) + "\",\"inputMethod\":\"" + method + "\"}";
    }

    @Test
    void businessOrderNamedExeUsesTheSameCanonicalRuleAndOldMarkersAreRejected() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            db(c, "A").update("UPDATE workorder SET code='EXE' WHERE id=1");
            m.perform(full(post(ROOT), "A").content("{\"type\":\"REPORTING\",\"code\":\"EXE:M1#000LOT\",\"inputMethod\":\"TEXT\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                    .andExpect(jsonPath("qrCode").value("EXE:M1#000LOT"));
            m.perform(full(get("/api/production/identity/1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("qrCode").value("EXE:M1#000LOT"));
            for (String retired : List.of("EXE:1", "MES1.legacy", "M1#000LOT")) {
                m.perform(full(post(ROOT), "A").content("{\"type\":\"REPORTING\",\"code\":\"" + retired + "\",\"inputMethod\":\"TEXT\"}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("errorCode").value("EXECUTION_QR_INVALID"));
            }
            m.perform(full(post(ROOT), "A").content("{\"type\":\"REPORTING\",\"code\":\"MES2.V08x.TTE.MDAwTE9U\",\"inputMethod\":\"TEXT\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("message", containsString("工单号:品号#批次号")));
        }
    }

    @Test
    void allScenesResolveTheSameIdentityThroughRealProxiesAndOnlyExplicitScanAddsActivity() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c, "A");
            assertTrue(AopUtils.isAopProxy(c.getBean(ProductionScanService.class)));
            assertTrue(c.getBean("businessTransactionManager") instanceof OwnedTransactionManager);
            assertTrue(Proxy.isProxyClass(c.getBean(MesProductionScanActivityMapper.class).getClass()));
            // The current fixture never creates the retired table; the real path must work without it.
            for (var database : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values())
                assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='mes_production_scan_identity'", Integer.class));
            String identity = null;
            for (String scene : List.of("EXECUTION_LOOKUP", "REPORTING", "DEFECT_DECLARATION")) {
                String method = scene.equals("REPORTING") ? "IMAGE" : "TEXT";
                String request = "TEST_SCAN_" + scene;
                var response = m.perform(full(post(ROOT), "A").requestAttr(RequestBoundaryFilter.REQUEST_ID, request)
                                .content(body(scene, "000LOT", method)))
                        .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                        .andExpect(jsonPath("workorderCode").value("WO1"))
                        .andExpect(jsonPath("productCode").value("M1"))
                        .andExpect(jsonPath("batchNo").value("000LOT"))
                        .andExpect(jsonPath("identityType").doesNotExist()).andReturn().getResponse();
                String current = response.getContentAsString();
                if (identity == null) identity = current;
                else assertEquals(identity, current);
                var event = j.queryForMap("SELECT * FROM mes_production_scan_activity WHERE request_id=?", request);
                assertEquals(scene, event.get("scan_type"));
                assertEquals(method, event.get("input_method"));
                assertEquals(1L, ((Number) event.get("user_id")).longValue());
                assertEquals(1L, ((Number) event.get("execution_id")).longValue());
                assertEquals("SUCCESS", event.get("outcome"));
                assertNotNull(event.get("occurred_at"));
                assertTrue(((Number) event.get("duration_ms")).longValue() >= 0);
                assertEquals(request, response.getHeader("X-Request-Id"));
            }
            m.perform(full(get("/api/production/identity"), "A").param("code", code("000LOT")))
                    .andExpect(status().isOk());
            m.perform(full(get("/api/production/reporting/context"), "A").param("executionId", "1"))
                    .andExpect(status().isOk());
            assertEquals(3, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE production_confirmation_status IS NOT NULL", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void failedResolverAndScenePermissionKeepTheirOriginalHttpErrorWhileActivityCommits() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c, "A");
            m.perform(full(post(ROOT), "A").content(body("REPORTING", "NOT_FOUND", "TEXT")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("errorCode").value("EXECUTION_NOT_FOUND"));
            m.perform(post(ROOT).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body("REPORTING", "000LOT", "IMAGE")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("errorCode").value("FORBIDDEN"));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            for (String error : List.of("EXECUTION_NOT_FOUND", "FORBIDDEN")) {
                var event = j.queryForMap("SELECT * FROM mes_production_scan_activity WHERE error_code=?", error);
                assertEquals("FAILURE", event.get("outcome"));
                assertEquals("REPORTING", event.get("scan_type"));
                assertEquals(1L, ((Number) event.get("user_id")).longValue());
                assertEquals(null, event.get("execution_id"));
            }
            assertEquals(0, db(c, "B").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
        }
    }

    @Test
    void auditStorageFailureDoesNotReplaceSuccessfulIdentityOrOriginalLookupError() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c, "A");
            j.execute("DROP TABLE mes_production_scan_activity");
            m.perform(full(post(ROOT), "A").content(body("EXECUTION_LOOKUP", "000LOT", "TEXT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"));
            m.perform(full(post(ROOT), "A").content(body("EXECUTION_LOOKUP", "MISSING", "TEXT")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("errorCode").value("EXECUTION_NOT_FOUND"));
            assertEquals("000LOT", j.queryForObject("SELECT batch_no FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void missingScopeOrTokenDoesNoMappedSqlAndFactoryActivitiesRemainInTheirOwnDatabase() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var counter = new MappedSqlCounter();
            c.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(counter);
            var failure = FactoryContext.withScope(null, () -> assertThrows(FoundationException.class,
                    () -> c.getBean(ProductionScanService.class).scan(ProductionScanType.EXECUTION_LOOKUP,
                            code("000LOT"), ProductionScanInputMethod.TEXT, "TEST_NO_SCOPE")));
            assertEquals(ErrorCode.FACTORY_SCOPE_REQUIRED, failure.code());
            m.perform(post(ROOT).header("X-Factory-Id", "A").contentType("application/json")
                            .content(body("EXECUTION_LOOKUP", "000LOT", "TEXT")))
                    .andExpect(status().isForbidden());
            assertEquals(0, counter.calls.get());
            assertEquals(0, db(c, "A").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            assertEquals(0, db(c, "B").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            db(c, "B").update("UPDATE mes_production_execution SET batch_no='B_ONLY' WHERE id=1");
            m.perform(full(post(ROOT), "A").content(body("EXECUTION_LOOKUP", "000LOT", "TEXT")))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT), "B").content(body("EXECUTION_LOOKUP", "000LOT", "TEXT")))
                    .andExpect(status().isNotFound());
            m.perform(full(post(ROOT), "B").content(body("EXECUTION_LOOKUP", "B_ONLY", "IMAGE")))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"));
            assertTrue(counter.calls.get() > 0);
            assertEquals(1, db(c, "A").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            assertEquals(2, db(c, "B").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity", Integer.class));
            assertEquals(1, db(c, "B").queryForObject("SELECT COUNT(*) FROM mes_production_scan_activity WHERE outcome='SUCCESS' AND input_method='IMAGE'", Integer.class));
        }
    }

    @Intercepts({
            @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
            @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
    })
    static class MappedSqlCounter implements Interceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            calls.incrementAndGet();
            return invocation.proceed();
        }
    }
}
