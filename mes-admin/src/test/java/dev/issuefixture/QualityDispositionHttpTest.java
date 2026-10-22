package dev.issuefixture;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mesbase.business.quality.controller.QualityDispositionController;
import dev.mesbase.business.quality.service.QualityDispositionService;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import java.math.BigDecimal;
import jakarta.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static dev.issuefixture.ProductionQualityV13HttpTest.context;
import static dev.issuefixture.ProductionQualityV13HttpTest.defect;
import static dev.issuefixture.ProductionQualityV13HttpTest.amount;
import static dev.issuefixture.ProductionQualityV13HttpTest.quantities;
import static dev.issuefixture.ProductionQualityV13HttpTest.JSON;
import static dev.issuefixture.ProductionQualityV13HttpTest.Q;
import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY routed H2 with real transactions, ledger and stock owners. No ERP calls.
 */
class QualityDispositionHttpTest {
    @Configuration
    static class Validation {
        @Bean
        Validator validator() {
            return new LocalValidatorFactoryBean();
        }
    }

    static final Class<?>[] EXTRA = {Validation.class, QualityDispositionService.class, QualityDispositionController.class, WmInventoryServiceImpl.class};

    static String command(String id, String destination, String qty, String warehouse) {
        return "{\"requestId\":\"" + id + "\",\"expectedRevision\":1,\"destination\":\"" + destination + "\",\"quantity\":\"" + qty + "\",\"warehouseId\":" + warehouse + ",\"reason\":\"TEST ONLY disposition\"}";
    }

    static JsonNode dispose(MockMvc m, String declaration, String body) throws Exception {
        return JSON.readTree(m.perform(full(post(Q + "/" + declaration + "/dispositions"), "A").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void returnRestoresNormalAllowanceWithoutDoubleOutputAndKeepsReplayIdentity() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var key = UUID.randomUUID().toString();
            String body = command(key, "RETURN_NODE", "10", "null");
            var result = dispose(m, id, body);
            amount(result, "returnedQty", "10");
            amount(result, "pendingDispositionQty", "0");
            assertEquals("RETURNED", result.get("state").asText());
            assertEquals(0, new BigDecimal("90").compareTo(j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=1 AND routing_node_id=12", BigDecimal.class)));
            var q = quantities(m);
            amount(q.get("nodes").get(1), "effectiveOutputQty", "90");
            dispose(m, id, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE source_quality_event_id=?", Integer.class, key));
            m.perform(full(post("/api/production/reports/supplements"), "A").content(ProductionReportHttpTest.supplement(12, "10")))
                    .andExpect(status().isOk());
            amount(quantities(m).get("nodes").get(1), "effectiveOutputQty", "100");
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A").content(command(key, "RETURN_NODE", "9", "null")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void transferPostsExactlyOneInboundAndNeverCallsItScrap() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            String body = command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1");
            var result = dispose(m, id, body);
            amount(result, "transferredQty", "10");
            amount(result, "allocatedScrapQty", "0");
            var e = result.get("events").get(result.get("events").size() - 1);
            String doc = e.get("stockDocumentId").asText();
            assertEquals("POSTED", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, doc));
            assertEquals(0, new BigDecimal("10").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE batch_no=(SELECT execution_no FROM mes_production_execution WHERE id=1)", BigDecimal.class)));
            dispose(m, id, body);
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE source_execution_id IS NOT NULL", Integer.class));
        }
    }

    @Test
    void overQuantityAndInvalidWarehouseRollbackAllFacts() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            for (String body : new String[]{command(UUID.randomUUID()
                    .toString(), "RETURN_NODE", "11", "null"), command(UUID.randomUUID()
                    .toString(), "WAREHOUSE", "10", "999")})
                m.perform(full(post(Q + "/" + id + "/dispositions"), "A").content(body))
                        .andExpect(status().is4xxClientError());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type IN ('RETURN_NODE','TRANSFER_WAREHOUSE')", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
        }
    }

    @Test
    void inboundPostingFailureRollsBackDraftInventoryAndDisposition() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            j.update("UPDATE unit_measure SET status='DISABLED' WHERE id=1");
            var response = m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                    .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1"))).andReturn().getResponse();
            assertEquals(409, response.getStatus());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE batch_no=(SELECT execution_no FROM mes_production_execution WHERE id=1)", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='TRANSFER_WAREHOUSE'", Integer.class));
        }
    }

    @Test
    void warehouseRequiresStockPermissionsAndSameSource() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            m.perform(post(Q + "/" + id + "/dispositions").header("Authorization", "Bearer REVISE")
                            .header("X-Factory-Id", "A").contentType("application/json")
                            .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                    .andExpect(status().isForbidden());
            j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
            m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                            .content(command(UUID.randomUUID().toString(), "WAREHOUSE", "10", "1")))
                    .andExpect(status().isConflict());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
        }
    }

    @Test
    void competingDispositionsCannotSpendTheSamePendingQuantity() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90);
             var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            var barrier = new CyclicBarrier(2);
            Callable<Integer> request = () -> {
                barrier.await();
                return m.perform(full(post(Q + "/" + id + "/dispositions"), "A")
                                .content(command(UUID.randomUUID().toString(), "RETURN_NODE", "10", "null")))
                        .andReturn().getResponse().getStatus();
            };
            var a = pool.submit(request);
            var b = pool.submit(request);
            assertEquals(Set.of(200, 409), Set.of(a.get(), b.get()));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type='RETURN_NODE'", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE source_quality_event_id IS NOT NULL", Integer.class));
        }
    }

    @Test
    void returningUnallocatedBalanceDoesNotHideUnfinishedHistoricRework() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            ProductionQualityV13HttpTest.execute(m, id, ProductionQualityV13HttpTest.command("ALLOCATE", null, "0", "4", "0", "0", "0"));
            var result = dispose(m, id, command(UUID.randomUUID().toString(), "RETURN_NODE", "6", "null"));
            amount(result, "pendingDispositionQty", "0");
            amount(result, "pendingStartQty", "4");
            assertEquals("WAITING_REWORK", result.get("state").asText());
        }
    }

    @Test
    void listProjectsTrustedContextAndProcessingStatesForTheCurrentFactory() throws Exception {
        try (var c = context(EXTRA, 100, 100, 90, 90, 90)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var id = defect(m, 12, "10", "INDEPENDENT", null);
            dispose(m, id, command(UUID.randomUUID().toString(), "RETURN_NODE", "10", "null"));
            var list = JSON.readTree(m.perform(full(get(Q), "A")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            var row = list.get("items").get(0);
            assertEquals(id, row.get("declarationId").asText());
            assertEquals("WO1", row.get("workorderCode").asText());
            assertEquals("EXE-1", row.get("executionNo").asText());
            assertEquals("M1", row.get("productCode").asText());
            assertEquals("LOCAL", row.get("source").asText());
            assertEquals("RETURNED", row.get("processingState").asText());
            amount(row, "pendingQuantity", "0");
            amount(row, "returnedQuantity", "10");
            var other = JSON.readTree(m.perform(full(get(Q), "B")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals(0, other.get("total").asInt());
        }
    }

}
