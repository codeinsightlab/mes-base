package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import java.util.UUID;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY H2 real transaction and owner, separate handover from physical acceptance.
 */
class WarehouseAcceptanceHttpTest {
    static String fixture(MockMvc m, JdbcTemplate j) throws Exception {
        j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
        m.perform(ProductionCoreV1HttpTest.full(post("/api/quality/releases/executions/1"))
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"94\",\"reason\":\"TEST ONLY\"}"))
                .andExpect(status().isOk());
        String id = UUID.randomUUID().toString();
        m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT))
                        .content("{\"requestId\":\"" + id + "\",\"executionId\":1,\"warehouseId\":1,\"productLotCode\":\"TEST-FINISHED\",\"quantity\":\"80\"}"))
                .andExpect(status().isOk());
        return id;
    }

    static String command(String id, String qty) {
        return "{\"requestId\":\"" + id + "\",\"stockId\":1,\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY 实际接收\"}";
    }

    @Test
    void partialActualAcceptanceAtomicStockAndReceiptReverseDependency() throws Exception {
        try (var c = ProductionReceiptHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            String receipt = fixture(m, j), url = "/api/wm/production-acceptances/receipts/" + receipt;
            String id = UUID.randomUUID().toString(), body = command(id, "60");
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(body)).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("id").value(id));
            m.perform(ProductionCoreV1HttpTest.full(get(url))).andExpect(status().isOk())
                    .andExpect(jsonPath("pending").value("20.000000"));
            assertEquals("60.0000", j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)
                    .toPlainString());
            m.perform(ProductionCoreV1HttpTest.full(post(ProductionReceiptHttpTest.ROOT + "/" + receipt + "/reverse"))
                            .content(ProductionReceiptHttpTest.reverse("21", UUID.randomUUID().toString())))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(command(UUID.randomUUID().toString(), "20")))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(command(UUID.randomUUID().toString(), "1")))
                    .andExpect(status().isConflict());
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/production-acceptances/" + id + "/reverse"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"10\",\"reason\":\"TEST ONLY 错收逆向\"}"))
                    .andExpect(status().isOk());
            assertEquals(3, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals("70.0000", j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)
                    .toPlainString());
        }
    }

    @Test
    void concurrentAcceptanceCannotExceedHandover() throws Exception {
        try (var c = ProductionReceiptHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            String receipt = fixture(m, j), url = "/api/wm/production-acceptances/receipts/" + receipt;
            var barrier = new CyclicBarrier(2);
            var a = pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(url))
                        .content(command(UUID.randomUUID().toString(), "60"))).andReturn().getResponse().getStatus();
            });
            var b = pool.submit(() -> {
                barrier.await();
                return m.perform(ProductionCoreV1HttpTest.full(post(url))
                        .content(command(UUID.randomUUID().toString(), "60"))).andReturn().getResponse().getStatus();
            });
            var results = List.of(a.get(), b.get());
            assertTrue(results.contains(200));
            assertTrue(results.contains(409));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_warehouse_acceptance", Integer.class));
        }
    }

    @Test
    void acceptanceInsertFailureRollsBackOwnerBalanceAndAction() throws Exception {
        try (var c = ProductionReceiptHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            String receipt = fixture(m, j);
            j.execute("ALTER TABLE mes_warehouse_acceptance ADD CONSTRAINT test_reject_accept CHECK(quantity_delta<0)");
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/production-acceptances/receipts/" + receipt))
                    .content(command(UUID.randomUUID().toString(), "60"))).andExpect(status().is5xxServerError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)
                    .signum());
        }
    }

    @Test
    void consumedInventoryCannotBeReversedIntoNegativeAndErpNeverFallsBack() throws Exception {
        try (var c = ProductionReceiptHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionReceiptHttpTest.db(c);
            String receipt = fixture(m, j), id = UUID.randomUUID().toString();
            String url = "/api/wm/production-acceptances/receipts/" + receipt;
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(command(id, "60"))).andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/local-stock/loss"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"stockId\":1,\"quantity\":\"50\",\"expectedQuantity\":\"60\",\"expectedAvailable\":\"60\",\"reason\":\"TEST ONLY 已消耗差额\"}"))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post("/api/wm/production-acceptances/" + id + "/reverse"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"20\",\"reason\":\"TEST ONLY 不能负库\"}"))
                    .andExpect(status().isConflict());
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "TEST_PROVIDER", "TEST_A"));
            m.perform(ProductionCoreV1HttpTest.full(post(url)).content(command(UUID.randomUUID().toString(), "1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("BUSINESS_SOURCE_CONFLICT"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_warehouse_acceptance", Integer.class));
        }
    }

}
