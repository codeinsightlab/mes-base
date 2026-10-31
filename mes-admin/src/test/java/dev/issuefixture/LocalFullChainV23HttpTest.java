package dev.issuefixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY V2.3: independent H2 scenarios, real services, generated SQL and transactions.
 */
class LocalFullChainV23HttpTest {
    @Test
    void cancellationAndPositiveReportShareTheWorkorderLockAndKeepCommittedHistory() throws Exception {
        for (int round = 0; round < 4; round++) {
            try (var c = QualityWorkflowHttpTest.context(99, 98, 97, 96, 95);
                 var pool = Executors.newFixedThreadPool(2)) {
                var m = MaterialIssueHttpTest.mvc(c);
                var j = ProductionWarehouseFixture.db(c);
                var original = j.queryForList("SELECT * FROM mes_production_report ORDER BY id");
                String cancel = WorkOrderCancellationHttpTest.body(UUID.randomUUID().toString());
                String report = ProductionReportHttpTest.supplement(11, "1");
                var barrier = new CyclicBarrier(2);
                var a = pool.submit(() -> {
                    barrier.await();
                    return m.perform(ProductionCoreV1HttpTest.full(post(WorkOrderCancellationHttpTest.CANCEL))
                                    .content(cancel))
                            .andReturn().getResponse().getStatus();
                });
                var b = pool.submit(() -> {
                    barrier.await();
                    return m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/supplements"))
                                    .content(report))
                            .andReturn().getResponse().getStatus();
                });
                assertEquals(200, a.get());
                int reportStatus = b.get();
                assertTrue(List.of(200, 409).contains(reportStatus));
                if (reportStatus == 200) {
                    assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_production_report r CROSS JOIN mes_workorder_cancellation c WHERE r.id=(SELECT MAX(id) FROM mes_production_report) AND r.create_time>c.recorded_at", Integer.class));
                }
                System.out.println("TEST_V23 CASE09 round=" + round + " cancel=200 report=" + reportStatus);
                assertEquals(reportStatus == 200 ? 6 : 5, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
                for (var row : original)
                    assertEquals(row, j.queryForMap("SELECT * FROM mes_production_report WHERE id=?", row.get("ID")));
                assertEquals("CANCELLED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
                m.perform(ProductionCoreV1HttpTest.full(post("/api/production/reports/supplements"))
                                .content(ProductionReportHttpTest.supplement(11, "1"))).andExpect(status().isConflict())
                        .andExpect(jsonPath("errorCode").value("WORKORDER_CANCELLED"));
                m.perform(ProductionCoreV1HttpTest.full(post(WorkOrderCancellationHttpTest.CANCEL)).content(cancel))
                        .andExpect(status().isOk());
                assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation", Integer.class));
            }
        }
    }

    @Test
    void partialReturnPreservesFrozenInputAndCannotCreateAdditionalProductionCredit() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            LocalWarehouseHttpTest.apply(m, "gain", "200", "0");
            String inputBody = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "50");
            var input = new ObjectMapper().readTree(ProductionInputHttpTest.submit(m, inputBody));
            var frozen = j.queryForList("SELECT id,request_hash,calculation_json FROM mes_material_issue_input WHERE document_id=?", input.get("documentId")
                    .asString());
            LocalWarehouseHttpTest.balance(j, "100");
            String body = LocalWarehouseHttpTest.body(UUID.randomUUID().toString(), "20", "100", input.get("documentId")
                    .asString());
            m.perform(ProductionCoreV1HttpTest.full(post(LocalWarehouseHttpTest.ROOT + "return")).content(body))
                    .andExpect(status().isOk());
            m.perform(ProductionCoreV1HttpTest.full(post(LocalWarehouseHttpTest.ROOT + "return")).content(body))
                    .andExpect(status().isOk());
            ProductionInputHttpTest.submit(m, inputBody);
            LocalWarehouseHttpTest.balance(j, "120");
            assertEquals(frozen, j.queryForList("SELECT id,request_hash,calculation_json FROM mes_material_issue_input WHERE document_id=?", input.get("documentId")
                    .asString()));
            var q = new ObjectMapper().readTree(m.perform(ProductionCoreV1HttpTest.full(get("/api/production/report-quantities/executions/" + input.get("executionId")
                            .asString())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(new BigDecimal(q.get("productionInputQty").asString()).compareTo(new BigDecimal("50")) <= 0);
            m.perform(ProductionCoreV1HttpTest.full(post(LocalWarehouseHttpTest.ROOT + "return"))
                            .content(LocalWarehouseHttpTest.body(UUID.randomUUID()
                                    .toString(), "81", "120", input.get("documentId")
                                    .asString())))
                    .andExpect(status().isConflict());
            LocalWarehouseHttpTest.balance(j, "120");
            assertEquals(3, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE stock_id=1", Integer.class));
        }
    }
}
