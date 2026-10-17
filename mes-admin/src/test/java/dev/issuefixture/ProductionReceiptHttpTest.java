package dev.issuefixture;

import java.math.BigDecimal;
import dev.mesbase.business.production.controller.ProductionReceiptController;
import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY two routed H2 databases, real Spring locks/XML; historical receipt fixtures explicitly seeded.
 */
class ProductionReceiptHttpTest {
    static final String ROOT = "/api/production/receipts";

    @Configuration
    @Import({ProductionReportHttpTest.Extra.class, ProductionReceiptService.class, ProductionReceiptController.class})
    static class Extra {
        @Bean
        ProductionReceiptQueryMapper receiptQueries(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionReceiptQueryMapper.class);
        }
    }

    static AnnotationConfigWebApplicationContext context(Class<?>... additional) throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class, ProductionDefectHttpTest.Extra.class);
        if (additional.length > 0) c.register(additional);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            ProductionInputTestData.confirmed(j, 1, new BigDecimal("100"));
            j.update("UPDATE mes_production_execution SET target_qty=100,status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=1");
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(1,11,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP),(1,12,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP),(1,13,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP)");
        }
        return c;
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String seed(JdbcTemplate j, String qty) {
        String id = UUID.randomUUID().toString();
        j.update("INSERT INTO mes_production_receipt(id,receipt_no,execution_id,warehouse_id,record_type,quantity_delta,operator_user_id,recorded_by,occurred_at,create_time,product_lot_code) VALUES(?,?,1,1,'RECEIPT',?,100,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST-LOT')", id, "TEST_ONLY_" + id, qty);
        return id;
    }

    static String reverse(String qty, String key) {
        return "{\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY冲销\",\"requestId\":\"" + key + "\",\"recordedBy\":999}";
    }

    @Test
    void qualityFailsClosedAndQueriesActualTerminalWithoutStockOrLifecycleWrites() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/summary")).param("executionId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("output").value("100.000000"))
                    .andExpect(jsonPath("terminalNodeId").value("13")).andExpect(jsonPath("canReceive").value(false));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                            .content("{\"executionId\":1,\"warehouseId\":1,\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("RECEIPT_QUALITY_UNVERIFIED"));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory", Integer.class));
        }
    }

    @Test
    void partialFullReversalReplayBalancesHistoryAndTrustedIdentity() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String id = seed(j, "60"), key = UUID.randomUUID().toString();
            for (int i = 0; i < 2; i++)
                m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse")).content(reverse("20", key)))
                        .andExpect(status().isOk()).andExpect(jsonPath("id").value(key))
                        .andExpect(jsonPath("operatorUserId").value("100"))
                        .andExpect(jsonPath("recordedBy").value("1"));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt", Integer.class));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse")).content(reverse("21", key)))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + key + "/reverse"))
                    .content(reverse("1", UUID.randomUUID().toString()))).andExpect(status().isUnprocessableEntity());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse"))
                    .content(reverse("41", UUID.randomUUID().toString()))).andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse"))
                    .content(reverse("40", UUID.randomUUID().toString()))).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/" + id))).andExpect(status().isOk())
                    .andExpect(jsonPath("originalBalance").value("0.000000"))
                    .andExpect(jsonPath("record.operatorName").value("历史操作人员"));
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT)).param("referenceReceiptId", id))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2));
        }
    }

    @Test
    void upstreamDecreaseExposesExcessButAllowsReversalAndRecalculates() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String id = seed(j, "90");
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,reason,create_time) SELECT 1,13,'ADJUSTMENT',-15,id,operator_user_id,1,report_time,'TEST ONLY真实订正',CURRENT_TIMESTAMP FROM mes_production_report WHERE execution_id=1 AND routing_node_id=13 AND record_type='REPORT'");
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/summary")).param("executionId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("output").value("85.000000"))
                    .andExpect(jsonPath("remaining").value("-5.000000"))
                    .andExpect(jsonPath("excess").value("5.000000"));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse"))
                    .content(reverse("5", UUID.randomUUID().toString()))).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(get(ROOT + "/summary")).param("executionId", "1"))
                    .andExpect(jsonPath("excess").value("0.000000"));
        }
    }

    @Test
    void concurrentReversalCannotOverdrawAndConcurrentReplayHasOneFact() throws Exception {
        try (var c = context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = db(c);
            String id = seed(j, "20");
            var barrier = new CyclicBarrier(2);
            var jobs = IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse"))
                        .content(reverse("15", UUID.randomUUID().toString()))).andReturn().getResponse().getStatus();
            })).toList();
            var statuses = ListHelper.values(jobs);
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            String key = UUID.randomUUID().toString();
            var replayBarrier = new CyclicBarrier(2);
            var replays = IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                replayBarrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(ROOT + "/" + id + "/reverse"))
                        .content(reverse("5", key))).andReturn().getResponse().getStatus();
            })).toList();
            assertEquals(List.of(200, 200), ListHelper.values(replays));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_receipt WHERE id=?", Integer.class, key));
        }
    }

    @Test
    void factoryWarehouseAndPermissionBoundaries() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String id = seed(db(c), "10");
            db(c).update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(77,'TEST_A_ONLY','TEST ONLY A warehouse','ENABLED')");
            m.perform(ProductionReportHttpTest.full(post(ROOT), "B")
                            .content("{\"executionId\":1,\"warehouseId\":77,\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("RECEIPT_WAREHOUSE_INVALID"));
            m.perform(get(ROOT).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            m.perform(ProductionReportHttpTest.full(get(ROOT + "/" + id), "B")).andExpect(status().isNotFound());
            m.perform(ProductionReportHttpTest.full(get(ROOT), "B")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(0));
            m.perform(ProductionCoreV1HttpTest.full(post(ROOT))
                            .content("{\"executionId\":1,\"warehouseId\":99999,\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    private static class ListHelper {
        static List<Integer> values(List<Future<Integer>> jobs) throws Exception {
            var result = new ArrayList<Integer>();
            for (var job : jobs) result.add(job.get());
            return result;
        }
    }
}
