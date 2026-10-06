package dev.mesbase.server;

import dev.MesBaseApplication;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: real Tomcat REQUEST/ASYNC dispatches reuse one servlet worker.
 */
@SpringBootTest(classes = {MesBaseApplication.class, AsyncRequestBoundaryHttpTest.Fixture.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mes.system.enabled=false", "mes.platform.enabled=false", "mes.runtime.enabled=false",
                "server.tomcat.threads.max=1", "server.tomcat.threads.min-spare=1"})
class AsyncRequestBoundaryHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean
        TestController asyncController() {
            return new TestController();
        }

        @Bean
        DispatchAdvice asyncDispatchAdvice() {
            return new DispatchAdvice();
        }

        @Bean
        FilterRegistrationBean<Filter> observeStateOutsideBoundary() {
            Filter filter = (request, response, chain) -> {
                var http = (HttpServletResponse) response;
                http.setHeader("X-TEST-ONLY-Scope", FactoryContext.current().isEmpty() ? "NONE" : "LEAK");
                http.setHeader("X-TEST-ONLY-Page", PageHelper.getLocalPage() == null ? "NONE" : "LEAK");
                http.setHeader("X-TEST-ONLY-Thread", Long.toString(Thread.currentThread().threadId()));
                chain.doFilter(request, response);
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            registration.setAsyncSupported(true);
            registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
            return registration;
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test-only/async-boundary")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        Callable<String> async(HttpServletRequest request) {
            request.setAttribute("TEST_ONLY.originalId", request.getAttribute(RequestBoundaryFilter.REQUEST_ID));
            return () -> "TEST ONLY async response";
        }

        @GetMapping("/test-only/async-boundary-failure")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        Callable<String> failure(HttpServletRequest request) {
            return async(request);
        }

        @GetMapping("/test-only/next-boundary")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        String next() {
            return "TEST ONLY next response";
        }
    }

    @RestControllerAdvice
    static class DispatchAdvice implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
            return type.getContainingClass() == TestController.class;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter type, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
                                      ServerHttpResponse response) {
            var servlet = ((ServletServerHttpRequest) request).getServletRequest();
            if (servlet.getDispatcherType() == DispatcherType.ASYNC) {
                FactoryContext.bind(new FactoryScope(new FactoryId("TEST_ONLY_ASYNC")));
                PageHelper.startPage(2, 10);
                if (servlet.getRequestURI().endsWith("-failure")) {
                    throw new IllegalStateException("TEST ONLY async response failure");
                }
                return servlet.getAttribute("TEST_ONLY.originalId") + "|"
                        + servlet.getAttribute(RequestBoundaryFilter.REQUEST_ID) + "|" + MDC.get("requestId");
            }
            return body;
        }
    }

    @Autowired
    Environment environment;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncDispatchRetainsIdAndReusedServletWorkerStartsNextRequestWithoutState(boolean failure) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var first = get(client, failure ? "/test-only/async-boundary-failure" : "/test-only/async-boundary");
            assertEquals(failure ? 500 : 200, first.statusCode(), first.body());
            String id = first.headers().firstValue("X-Request-Id").orElseThrow();
            UUID.fromString(id);
            assertNotEquals("TEST_ONLY_client_id", id);
            if (failure) assertTrue(first.body().contains(id), first.body());
            else assertEquals(id + "|" + id + "|" + id, first.body());

            var next = get(client, "/test-only/next-boundary");
            assertEquals(200, next.statusCode(), next.body());
            assertEquals(first.headers().firstValue("X-TEST-ONLY-Thread"), next.headers()
                    .firstValue("X-TEST-ONLY-Thread"));
            assertEquals("NONE", next.headers().firstValue("X-TEST-ONLY-Scope").orElseThrow());
            assertEquals("NONE", next.headers().firstValue("X-TEST-ONLY-Page").orElseThrow());
            assertNotEquals(id, next.headers().firstValue("X-Request-Id").orElseThrow());
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(10)).header("X-Request-Id", "TEST_ONLY_client_id").GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
