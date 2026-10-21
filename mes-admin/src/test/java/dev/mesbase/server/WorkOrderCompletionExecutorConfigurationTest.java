package dev.mesbase.server;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderCompletionExecutorConfigurationTest {
    record Consumer(Executor executor) {
    }

    @Test
    void dedicatedBoundedPoolKeepsTheExistingDefaultExecutor() throws Exception {
        try (var c = new AnnotationConfigApplicationContext()) {
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
            Executor existing = Runnable::run;
            c.registerBean("taskExecutor", Executor.class, () -> existing);
            c.registerBean(Consumer.class);
            c.register(WorkOrderCompletionExecutorConfiguration.class);
            c.refresh();
            assertSame(existing, c.getBean(Consumer.class).executor());
            var pool = c.getBean("workOrderCompletionExecutor", ThreadPoolTaskExecutor.class);
            assertEquals(4, pool.getCorePoolSize());
            assertEquals(4, pool.getMaxPoolSize());
            assertEquals(128, pool.getThreadPoolExecutor().getQueue().remainingCapacity());
            var thread = new CompletableFuture<String>();
            pool.execute(() -> thread.complete(Thread.currentThread().getName()));
            assertTrue(thread.get(5, TimeUnit.SECONDS).startsWith("workorder-completion-"));
        }
    }
}
