package dev.mesbase.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCompletionExecutorConfiguration {
    /**
     * Dedicated executor: do not replace the application's default async executor or scheduler.
     */
    @Bean(name = "workOrderCompletionExecutor", defaultCandidate = false)
    ThreadPoolTaskExecutor workOrderCompletionExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("workorder-completion-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
