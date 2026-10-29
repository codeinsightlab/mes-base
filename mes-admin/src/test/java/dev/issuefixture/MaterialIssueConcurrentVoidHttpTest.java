package dev.issuefixture;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deterministic isolated H2 regression: both callers read before competing for the real lock. */
class MaterialIssueConcurrentVoidHttpTest {
    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class GateConfig {
        @Bean
        LockGate materialIssueVoidLockGate() {
            return new LockGate();
        }
    }

    @Aspect
    static class LockGate {
        final AtomicBoolean enabled = new AtomicBoolean();
        final CountDownLatch readers = new CountDownLatch(2);

        @Around("execution(* dev.mesbase.business.production.service.ProductionExecutionRules.lock(..))")
        Object waitForBothReaders(ProceedingJoinPoint invocation) throws Throwable {
            if (enabled.get()) {
                readers.countDown();
                if (!readers.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Both void requests must reach the execution lock");
            }
            return invocation.proceed();
        }
    }

    @Test
    void twoVoidRequestsReuseOneCancellationAndReverseStockOnce() throws Exception {
        try (var context = ProductionInputHttpTest.context(GateConfig.class);
             var pool = Executors.newFixedThreadPool(2)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var json = new ObjectMapper();
            JsonNode input = json.readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = input.get("documentId").asText();
            var gate = context.getBean(LockGate.class);
            gate.enabled.set(true);
            var first = pool.submit(() -> mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var second = pool.submit(() -> mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var a = json.readTree(first.get(15, TimeUnit.SECONDS));
            var b = json.readTree(second.get(15, TimeUnit.SECONDS));
            assertEquals(0, gate.readers.getCount());
            assertEquals("SUCCEEDED", a.get("voidResultStatus").asText());
            assertEquals("SUCCEEDED", b.get("voidResultStatus").asText());
            assertTrue(a.get("voidOperationId").isTextual());
            assertEquals(a.get("voidOperationId").asText(), b.get("voidOperationId").asText());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, document));
        }
    }
}
