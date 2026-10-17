package dev.issuefixture;

import dev.mesbase.business.production.service.WorkOrderCancellationService;
import dev.mesbase.business.production.service.WorkOrderCancellationService.Command;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: designated SIMPLE_B, own tagged records retained, real row-lock races and immutable triggers.
 */
@EnabledIfEnvironmentVariable(named = "MES_CANCEL_V18_MYSQL", matches = "true")
class WorkOrderCancellationMySqlTest {
    @Test
    void concurrentCancelAndForwardCreateSerializeAndFactsAreImmutable() throws Exception {
        try (var c = ProductionDefectMySqlTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            c.registerBean(WorkOrderCancellationService.class);
            c.registerBean(MesProductionExecutionServiceImpl.class);
            var cancel = c.getBean(WorkOrderCancellationService.class);
            var executions = c.getBean(IMesProductionExecutionService.class);
            var j = c.getBean(JdbcTemplate.class);
            var tx = new TransactionTemplate(c.getBean("businessTransactionManager", PlatformTransactionManager.class));
            ProductionReportMySqlTest.actor();
            String tag = "TEST_CANCEL_V18_" + UUID.randomUUID();
            long a = seed(j, tag + "_A"), b = seed(j, tag + "_B");
            var baseline = j.queryForList("SELECT * FROM wm_inventory ORDER BY id");
            var barrier = new CyclicBarrier(2);
            var fa = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    barrier.await();
                    return cancel.cancel(a, new Command(UUID.randomUUID().toString(), "TEST ONLY race", 0)).getId();
                } catch (FoundationException e) {
                    return e.code().name();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var fb = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    barrier.await();
                    return cancel.cancel(a, new Command(UUID.randomUUID().toString(), "TEST ONLY race", 0)).getId();
                } catch (FoundationException e) {
                    return e.code().name();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var outcomes = List.of(fa.get(), fb.get());
            assertEquals(1, outcomes.stream().filter(x -> x.equals("WORKORDER_CANCELLATION_CONFLICT")).count());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_workorder_cancellation WHERE workorder_id=?", Integer.class, a));
            var order = j.queryForMap("SELECT product_id,routing_id FROM workorder WHERE id=?", b);
            var race = new CyclicBarrier(2);
            var stop = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    race.await();
                    return cancel.cancel(b, new Command(UUID.randomUUID().toString(), "TEST ONLY forward race", 0))
                            .getId();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var forward = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    race.await();
                    return executions.insert(new MesProductionExecutionCreateCommand(b, ((Number) order.get("routing_id")).longValue(), BigDecimal.ONE, "READY", null, null, "TEST ONLY V18 race"))
                            .getId().toString();
                } catch (FoundationException e) {
                    return e.code().name();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(stop.get().matches("[0-9a-f-]{36}"));
            String positive = forward.get();
            assertTrue(positive.equals("WORKORDER_CANCELLED") || positive.matches("[0-9]+"));
            assertThrows(FoundationException.class, () -> executions.insert(new MesProductionExecutionCreateCommand(b, ((Number) order.get("routing_id")).longValue(), BigDecimal.ONE, "READY", null, null, "TEST ONLY blocked")));
            assertEquals(baseline, j.queryForList("SELECT * FROM wm_inventory ORDER BY id"));
            var original = cancel.history(a);
            var replay = cancel.cancel(a, new Command(original.getId(), original.getReason(), 0));
            assertEquals(original.getId(), replay.getId());
            assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> j.update("UPDATE mes_workorder_cancellation SET reason='TEST forbidden' WHERE id=?", original.getId())));
            assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> j.update("DELETE FROM mes_workorder_cancellation WHERE id=?", original.getId())));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static long seed(JdbcTemplate j, String code) {
        return ProductionReportMySqlTest.insert(j, "INSERT INTO workorder(source,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) SELECT 'LOCAL',?,product_id,10,routing_id,'RELEASED',0,NOW(),NOW() FROM workorder WHERE id=208", code);
    }
}
