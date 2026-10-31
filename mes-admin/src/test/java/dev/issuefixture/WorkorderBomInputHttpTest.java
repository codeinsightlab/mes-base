package dev.issuefixture;

import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated routed H2, actual HTTP/service/XML transactions; not deployed or real ERP evidence.
 */
class WorkorderBomInputHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void largeUnrelatedBomCatalogDoesNotBlockFormalWorkorderInput() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            j.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at,is_default) "
                    + "SELECT n,'TEST ONLY unrelated ' || n,'v1',99,1,1,'DRAFT',1,1,CURRENT_TIMESTAMP,0 FROM SYSTEM_RANGE(100,2600) AS generated(n)");
            j.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,sort) "
                    + "SELECT n,100,1,1,1,0 FROM SYSTEM_RANGE(100,10200) AS generated(n)");
            select(m, 1, 1);
            assertTrue(input(m, "10") > 0);
        }
    }

    @Test
    void executionCapacityCacheInvalidatesAfterCommitButNotOnRollbackOrUuidReplay() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            var redis = mock(RedisCache.class);
            var processor = new BusinessCacheProcessor(redis, new SpringCommitActions(), c.getEnvironment());
            var caches = c.getBean(BusinessCacheProcessor.class);
            doAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                processor.evict(call.getArgument(0), call.getArgument(1));
                verify(redis, never()).deleteByPattern(anyString());
                return null;
            }).when(caches)
                    .evict(eq(MesProductionExecutionResource.PREFIX), eq(BusinessCacheEvict.Scope.LIST_AND_DETAIL));

            String first = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "90");
            long execution = JSON.readTree(ProductionInputHttpTest.submit(m, first)).get("executionId").asLong();
            verify(redis, times(2)).deleteByPattern(anyString());
            clearInvocations(redis);

            String appendRequestId = UUID.randomUUID().toString();
            String append = ProductionInputHttpTest.command(m, appendRequestId, "L001", "10");
            j.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_issue CHECK(action_type <> 'ISSUE' OR id <> '"
                    + appendRequestId + "')");
            m.perform(full(post("/api/production/input")).content(append)).andExpect(status().is5xxServerError());
            verify(redis, never()).deleteByPattern(anyString());
            assertEquals(0, new BigDecimal("90").compareTo(j.queryForObject(
                    "SELECT target_qty FROM mes_production_execution WHERE id=?", BigDecimal.class, execution)));

            ProductionInputHttpTest.submit(m, first);
            verify(redis, never()).deleteByPattern(anyString());
            j.execute("ALTER TABLE wm_stock_action DROP CONSTRAINT test_fail_issue");
            assertEquals(execution, input(m, "10"));
            verify(redis, times(2)).deleteByPattern(anyString());
        }
    }

    static void prepare(JdbcTemplate j, String plan, String base, String component) {
        j.update("DELETE FROM mes_production_execution"); // Only unused fresh fixture executions.
        j.update("UPDATE workorder SET planned_qty=? WHERE id=1", new BigDecimal(plan));
        j.update("UPDATE material_bom SET base_qty=? WHERE id=1", new BigDecimal(base));
        j.update("UPDATE material_bom_item SET quantity=? WHERE id=1", new BigDecimal(component));
    }

    static void select(MockMvc m, int revision, long bomId) throws Exception {
        m.perform(full(put("/api/production/workorder/1/material")).content(JSON.writeValueAsString(Map.of(
                        "expectedRevision", revision, "bomId", bomId, "expectedBomRevision", 1,
                        "warehouseIds", List.of(1), "workCenter", "TEST-BOM", "enabled", true))))
                .andExpect(status().isOk());
    }

    static long input(MockMvc m, String output) throws Exception {
        return JSON.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                        .toString(), "L001", output)))
                .get("executionId").asLong();
    }

    static void report(MockMvc m, long execution, String quantity) throws Exception {
        for (long node : List.of(11L, 12L, 13L)) {
            String body = ProductionReportHttpTest.supplement(node, quantity)
                    .replace("\"executionId\":\"1\"", "\"executionId\":\"" + execution + "\"");
            m.perform(full(post("/api/production/reports/supplements")).content(body)).andExpect(status().isOk());
        }
    }

    static void receive(MockMvc m, long execution, String quantity) throws Exception {
        var context = (AnnotationConfigWebApplicationContext) m.getDispatcherServlet().getWebApplicationContext();
        var j = ProductionInputHttpTest.db(context);
        var identity = JSON.readTree(m.perform(full(get("/api/production/identity/" + execution)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String batch = identity.get("batchNo").asText();
        long product = identity.get("productId").asLong();
        var matches = j.queryForList("SELECT id FROM wm_inventory WHERE material_id=? AND warehouse_id=1 AND batch_no=? AND bin_code IS NULL", Long.class, product, batch);
        long stockId;
        if (matches.isEmpty()) {
            var empty = j.queryForList("SELECT id FROM wm_inventory s WHERE material_id=? AND warehouse_id=1 AND quantity=0 AND bin_code IS NULL AND NOT EXISTS(SELECT 1 FROM wm_stock_action a WHERE a.stock_id=s.id)", Long.class, product);
            if (empty.isEmpty()) {
                stockId = j.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM wm_inventory", Long.class);
                j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(?,?,1,?,0,0,'ENABLED')", stockId, product, batch);
            } else {
                stockId = empty.getFirst();
                j.update("UPDATE wm_inventory SET batch_no=? WHERE id=?", batch, stockId);
            }
        } else stockId = matches.getFirst();
        m.perform(full(post("/api/production/executions/" + execution + "/inbound")).content(JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                        .toString(), "stockId", stockId, "quantity", quantity, "reason", "TEST ONLY MES inbound"))))
                .andExpect(status().isOk());
    }

    @Test
    void endedExecutionCannotReopenAndAnotherCombinationCanProduceRemainingPlan() throws Exception {
        try (var c = ProductionInputHttpTest.context(WorkOrderProgressHttpTest.Extra.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            long execution = input(m, "90");
            m.perform(full(post("/api/production/executions/" + execution + "/actions/start")))
                    .andExpect(status().isOk());
            report(m, execution, "90");
            receive(m, execution, "60");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            receive(m, execution, "30");
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            var facts = j.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution);
            String code = JSON.readTree(m.perform(full(get("/api/production/identity/" + execution)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("qrCode").asText();
            m.perform(full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_CLOSED"));
            long next = JSON.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L002", "10"))).get("executionId").asLong();
            assertTrue(execution != next);
            m.perform(full(post("/api/production/executions/" + next + "/actions/start"))).andExpect(status().isOk());
            report(m, next, "10");
            receive(m, next, "10");
            assertEquals(facts, j.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
        }
    }

    @Test
    void cumulativeRoundingMakesTheMiddleDeltaAndLastRemainderExactAcrossBatches() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "3", "3", "1");
            select(m, 1, 1);
            input(m, "1");
            ProductionInputHttpTest.submit(m, ProductionInputHttpTest.command(m, UUID.randomUUID()
                    .toString(), "L002", "1"));
            input(m, "1");
            assertEquals(List.of(new BigDecimal("0.333333"), new BigDecimal("0.333334"), new BigDecimal("0.333333")),
                    j.queryForList("SELECT l.quantity FROM wm_stock_document_line l JOIN wm_stock_document d ON d.id=l.document_id WHERE d.document_type='MATERIAL_ISSUE' ORDER BY l.id", BigDecimal.class));
            assertEquals(0, new BigDecimal("1").compareTo(j.queryForObject("SELECT SUM(l.quantity) FROM wm_stock_document_line l JOIN wm_stock_document d ON d.id=l.document_id WHERE d.document_type='MATERIAL_ISSUE'", BigDecimal.class)));
            assertEquals(0, new BigDecimal("19999").compareTo(j.queryForObject("SELECT SUM(quantity) FROM wm_inventory WHERE id IN (1,2)", BigDecimal.class)));
            assertEquals(0, new BigDecimal("-1").compareTo(j.queryForObject("SELECT SUM(quantity_delta) FROM wm_stock_action WHERE action_type='ISSUE'", BigDecimal.class)));
        }
    }

    @Test
    void releaseDoesNotFreezeButFirstIssueUsesIndependentBaseUnitsAndKeepsOriginalUuid() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            j.update("INSERT INTO unit_measure(id,code,name,status) VALUES(2,'PCS','件','ENABLED')");
            j.update("UPDATE material SET unit_id=2 WHERE id=99");
            j.update("UPDATE material_bom SET unit_id=2 WHERE id=1");
            select(m, 1, 1); // The work order is already released.
            j.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at,is_default) SELECT 2,'BOM-SECOND','2',product_id,base_qty,unit_id,status,revision,remark,created_by,created_at,published_at,0 FROM material_bom WHERE id=1");
            j.update("INSERT INTO material_bom_item SELECT 2,2,material_id,5,unit_id,NULL,sort FROM material_bom_item WHERE id=1");
            select(m, 2, 2);
            select(m, 3, 1);
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM workorder WHERE material_frozen_at IS NOT NULL", Integer.class));
            String body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "40");
            var first = ProductionInputHttpTest.submit(m, body);
            assertNotNull(j.queryForObject("SELECT material_frozen_at FROM workorder WHERE id=1", Object.class));
            j.update("UPDATE material_bom SET status='DISABLED' WHERE id=1");
            assertEquals(first, ProductionInputHttpTest.submit(m, body));
            m.perform(full(put("/api/production/workorder/1/material")).content(JSON.writeValueAsString(Map.of(
                            "expectedRevision", 4, "bomId", 2, "warehouseIds", List.of(1), "workCenter", "TEST", "enabled", true))))
                    .andExpect(status().isConflict());
            assertEquals("150.000000", ProductionInputHttpTest.generate(m, "60").get("allocations").get(0)
                    .get("quantity").asText());
            input(m, "60");
            assertEquals(0, new BigDecimal("250").compareTo(j.queryForObject("SELECT SUM(l.quantity) FROM wm_stock_document_line l JOIN wm_stock_document d ON d.id=l.document_id WHERE d.document_type='MATERIAL_ISSUE'", BigDecimal.class)));
        }
    }

    @Test
    void staleConcurrentCommandsCannotSpendTheLastQuotaTwiceOrCreateAnotherBatchExecution() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            input(m, "90");
            String one = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "10");
            String two = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "10");
            var a = pool.submit(() -> m.perform(full(post(ProductionInputHttpTest.ROOT)).content(one)).andReturn()
                    .getResponse().getStatus());
            var b = pool.submit(() -> m.perform(full(post(ProductionInputHttpTest.ROOT)).content(two)).andReturn()
                    .getResponse().getStatus());
            assertEquals(List.of(200, 409), Stream.of(a.get(), b.get()).sorted().toList());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
        }
    }

    @Test
    void concurrentIdenticalUuidCreditsOnceEvenWhenItUsesTheWholePlan() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            String body = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "100");
            var first = pool.submit(() -> ProductionInputHttpTest.submit(m, body));
            var second = pool.submit(() -> ProductionInputHttpTest.submit(m, body));
            assertEquals(first.get(), second.get());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
            assertEquals(0, new BigDecimal("250").compareTo(j.queryForObject("SELECT SUM(l.quantity) FROM wm_stock_document_line l JOIN wm_stock_document d ON d.id=l.document_id WHERE d.document_type='MATERIAL_ISSUE'", BigDecimal.class)));
        }
    }

    @Test
    void savedMultiMaterialDemandIsPlannedAsOneCompleteCombination() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            prepare(j, "100", "1", "2.5");
            select(m, 1, 1);
            ProductionInputHttpTest.multiMaterial(j);
            var plan = ProductionInputHttpTest.generate(m, "10");
            assertEquals(2, plan.get("combination").get("items").size());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }
}
