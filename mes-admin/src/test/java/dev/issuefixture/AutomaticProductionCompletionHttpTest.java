package dev.issuefixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retired report/scheduler triggers must never create terminal facts.
 */
class AutomaticProductionCompletionHttpTest {
    @Test
    void lastReportRemainsRunningAndManualCompleteCannotCreateAClosure() throws Exception {
        try (var c = ProductionWarehouseFixture.context(ProductionReportingHttpTest.Extra.class)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            ProductionCompletionHttpTest.oneExecution(j, "100");
            j.update("DELETE FROM mes_production_report WHERE routing_node_id=13");
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            ProductionReportingHttpTest.submit(m, 13, "100");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=1", String.class));
            assertEquals("RELEASED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion", Integer.class));
            m.perform(full(post("/api/production/executions/1/actions/complete"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedBasisHash\":\"x\",\"reason\":\"TEST ONLY\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void dedicatedCompletionSchedulerExecutorAndHintTypesAreRetired() {
        for (String path : new String[]{
                "mes-admin/src/main/java/dev/mesbase/server/WorkOrderCompletionJob.java",
                "mes-admin/src/main/java/dev/mesbase/server/WorkOrderCompletionExecutorConfiguration.java",
                "mes-business/src/main/java/dev/mesbase/business/production/domain/ProductionCompletionRequested.java",
                "mes-business/src/main/java/dev/mesbase/business/production/domain/WorkOrderCompletionRequested.java"})
            assertFalse(Files.exists(Path.of(path)), path);
    }
}
