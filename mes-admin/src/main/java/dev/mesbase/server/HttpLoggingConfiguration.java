package dev.mesbase.server;

import dev.mesbase.platform.web.HttpRequestCompletionListener;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.EnumSet;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

@Configuration
public class HttpLoggingConfiguration {
    @Bean
    FilterRegistrationBean<RequestBoundaryFilter> requestBoundaryRegistration(RequestBoundaryFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        return registration;
    }

    @Bean
    ServletListenerRegistrationBean<HttpRequestCompletionListener> httpCompletionRegistration(Environment environment) {
        String exclusions = environment.getProperty("mes.http.logging.exclude-paths", "/health,/actuator/health/**,/favicon.ico,/assets/**,/static/**,/openapi-docs/**");
        long slowThresholdMs = environment.getProperty("mes.logging.http.slow-threshold-ms", Long.class, 1000L);
        var listener = new HttpRequestCompletionListener(Arrays.stream(exclusions.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList(), slowThresholdMs);
        return new ServletListenerRegistrationBean<>(listener);
    }
}
