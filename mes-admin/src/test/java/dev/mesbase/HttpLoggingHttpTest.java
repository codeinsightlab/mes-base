package dev.mesbase;

import dev.MesBaseApplication;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: real Tomcat/security/lifecycle, synthetic identities and factories, no database.
 */
@SpringBootTest(classes = {MesBaseApplication.class, FoundationHttpTest.Fixture.class, HttpLoggingHttpTest.Fixture.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mes.system.enabled=false", "mes.platform.enabled=false", "mes.runtime.enabled=false",
                "mes.http.logging.exclude-paths=/health,/test-only/log/excluded", "server.tomcat.threads.max=1",
                "mes.logging.http.slow-threshold-ms=600"})
class HttpLoggingHttpTest {
    @Autowired
    Environment environment;

    @TestConfiguration
    static class Fixture {
        @Bean
        TestController loggingController() {
            return new TestController();
        }

        @Bean
        FilterRegistrationBean<Filter> loggingFailureFixture() {
            Filter filter = (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                if (http.getDispatcherType() == DispatcherType.REQUEST
                        && http.getRequestURI().equals("/test-only/log/filter-error"))
                    throw new ServletException("TEST ONLY container error", new IllegalStateException("TEST ONLY cause"));
                chain.doFilter(request, response);
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
            registration.setAsyncSupported(true);
            registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
            return registration;
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test-only/log/{kind}")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        String log(@PathVariable String kind) throws InterruptedException {
            if (kind.equals("slow")) Thread.sleep(700);
            if (kind.equals("bad-request")) throw new FoundationException(ErrorCode.BAD_REQUEST);
            if (kind.equals("unauthenticated")) throw new FoundationException(ErrorCode.UNAUTHENTICATED);
            if (kind.equals("business-401")) throw new ServiceException("TEST ONLY expected authentication", 401);
            if (kind.equals("business")) throw new ServiceException("TEST ONLY expected validation");
            if (kind.equals("known")) throw new FoundationException(ErrorCode.UNPROCESSABLE);
            if (kind.equals("system"))
                throw new IllegalStateException("TEST ONLY unexpected", new IOException("TEST ONLY original cause"));
            LoggerFactory.getLogger(TestController.class).info("TEST ONLY application event");
            return MDC.get("requestId");
        }

        @GetMapping("/test-only/log-parameter")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        String parameter(@RequestParam int count) {
            return Integer.toString(count);
        }

        @GetMapping("/test-only/log-async/{kind}")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        Callable<String> async(@PathVariable String kind) {
            return () -> {
                LoggerFactory.getLogger(TestController.class).info("TEST ONLY callable work");
                if (kind.equals("system")) throw new IllegalStateException("TEST ONLY async unexpected");
                return MDC.get("requestId");
            };
        }

        @GetMapping("/test-only/log-direct-async")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        void direct(HttpServletRequest request) {
            var async = request.startAsync();
            async.start(() -> {
                try {
                    async.getResponse().getWriter().write("TEST ONLY direct completion");
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                } finally {
                    async.complete();
                }
            });
        }

        @GetMapping("/test-only/log-timeout")
        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        void timeout(HttpServletRequest request) {
            var async = request.startAsync();
            async.setTimeout(50);
            async.addListener(new AsyncListener() {
                public void onComplete(AsyncEvent event) {
                }

                public void onError(AsyncEvent event) {
                }

                public void onStartAsync(AsyncEvent event) {
                }

                public void onTimeout(AsyncEvent event) {
                    ((HttpServletResponse) event.getAsyncContext().getResponse()).setStatus(504);
                    event.getAsyncContext().complete();
                }
            });
        }
    }

    static class Events extends AppenderBase<ILoggingEvent> implements AutoCloseable {
        final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
        final Logger access = (Logger) LoggerFactory.getLogger("MES_HTTP_ACCESS");
        final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        Events() {
            start();
            root.addAppender(this);
            access.addAppender(this);
        }

        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
        }

        public void close() {
            root.detachAppender(this);
            access.detachAppender(this);
            stop();
        }

        List<ILoggingEvent> completed(String id) {
            return events.stream().filter(e -> e.getFormattedMessage().startsWith("event=http.request.completed ")
                    && id.equals(e.getMDCPropertyMap().get("requestId"))).toList();
        }

        void waitForCompletion(String id) throws InterruptedException {
            long until = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (completed(id).isEmpty() && System.nanoTime() < until) Thread.sleep(10);
        }
    }

    HttpResponse<String> get(HttpClient client, String path, String actor, String factory,
                             String suppliedId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (actor != null) request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (actor + ":test-only-valid-password").getBytes(StandardCharsets.UTF_8)));
        if (factory != null) request.header("X-Factory-Id", factory);
        if (suppliedId != null) request.header("X-Request-Id", suppliedId);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String verify(Events events, HttpResponse<String> response, int status, String path) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        UUID.fromString(id);
        events.waitForCompletion(id);
        var logs = events.completed(id);
        assertEquals(1, logs.size(), path);
        String message = logs.getFirst().getFormattedMessage();
        long duration = Long.parseLong(message.split("durationMs=")[1].split(" ")[0]);
        assertEquals(Level.INFO, logs.getFirst().getLevel());
        assertEquals("MES_HTTP_ACCESS", logs.getFirst().getLoggerName());
        assertTrue(!message.contains("requestId="));
        assertNull(logs.getFirst().getThrowableProxy());
        assertTrue(logs.getFirst().getFormattedMessage().contains("status=" + status), logs.getFirst()
                .getFormattedMessage());
        assertTrue(logs.getFirst().getFormattedMessage().contains("path=" + path));
        assertTrue(logs.getFirst().getFormattedMessage().matches(".*durationMs=[0-9]+.*"));
        return id;
    }

    @Test
    void normalBusinessSystemAndSecurityKeepOneTerminalSummaryAndCorrectSeverity() throws Exception {
        try (var events = new Events(); var client = HttpClient.newHttpClient()) {
            var response = get(client, "/test-only/log/normal?password=TEST_ONLY_QUERY_SECRET", null, null, "bad id=\"\\");
            String id = verify(events, response, 200, "/test-only/log/normal");
            assertEquals(id, response.body());
            assertEquals(Level.INFO, events.completed(id).getFirst().getLevel());
            String slow = verify(events, get(client, "/test-only/log/slow", null, null, null), 200, "/test-only/log/slow");
            assertEquals(Level.INFO, events.completed(slow).getFirst().getLevel());
            assertEquals(1, events.events.stream().filter(e -> slow.equals(e.getMDCPropertyMap().get("requestId"))
                            && e.getFormattedMessage().startsWith("event=http.request.slow ") && e.getLevel() == Level.WARN)
                    .count());
            String bad = verify(events, get(client, "/test-only/log/bad-request", null, null, null), 400, "/test-only/log/bad-request");
            assertEquals(Level.INFO, events.completed(bad).getFirst().getLevel());
            assertKnownWarning(events, bad, FoundationException.class);
            String unauthenticated = verify(events, get(client, "/test-only/log/unauthenticated", null, null, null), 401, "/test-only/log/unauthenticated");
            assertKnownWarning(events, unauthenticated, FoundationException.class);
            String business401 = verify(events, get(client, "/test-only/log/business-401", null, null, null), 401, "/test-only/log/business-401");
            assertKnownWarning(events, business401, ServiceException.class);
            String invalid = verify(events, get(client, "/test-only/log-parameter?count=invalid", null, null, null), 400, "/test-only/log-parameter");
            assertOnlyAccess(events, invalid);
            assertTrue(events.events.stream()
                    .anyMatch(e -> e.getFormattedMessage().equals("TEST ONLY application event")
                            && id.equals(e.getMDCPropertyMap().get("requestId"))));
            assertTrue(events.completed(id).stream()
                    .noneMatch(e -> e.getFormattedMessage().contains("TEST_ONLY_QUERY_SECRET")));
            for (String kind : List.of("business", "known")) {
                String rejected = verify(events, get(client, "/test-only/log/" + kind, null, null, "x".repeat(1024)), 422, "/test-only/log/" + kind);
                assertTrue(events.events.stream().filter(e -> rejected.equals(e.getMDCPropertyMap().get("requestId")))
                        .noneMatch(e -> e.getLevel() == Level.ERROR || e.getThrowableProxy() != null));
                assertKnownWarning(events, rejected, kind.equals("business") ? ServiceException.class : FoundationException.class);
            }
            String failed = verify(events, get(client, "/test-only/log/system", null, null, null), 500, "/test-only/log/system");
            var errors = events.events.stream()
                    .filter(e -> failed.equals(e.getMDCPropertyMap().get("requestId")) && e.getLevel() == Level.ERROR)
                    .toList();
            assertEquals(1, errors.size());
            assertNotNull(errors.getFirst().getThrowableProxy().getCause());
            assertTrue(errors.getFirst().getThrowableProxy().getStackTraceElementProxyArray().length > 0);
            verify(events, get(client, "/api/foundation/context", null, "UNTRUSTED", null), 401, "/api/foundation/context");
            String a = verify(events, get(client, "/api/foundation/access", "visitor", "A", null), 403, "/api/foundation/access");
            assertTrue(events.completed(a).getFirst().getFormattedMessage().contains("factoryId=A"));
            String b = verify(events, get(client, "/api/foundation/access", "reader", "B", null), 403, "/api/foundation/access");
            assertTrue(events.completed(b).getFirst().getFormattedMessage().contains("factoryId=B"));
            String next = verify(events, get(client, "/test-only/log/normal", null, null, null), 200, "/test-only/log/normal");
            assertTrue(events.completed(next).getFirst().getFormattedMessage().contains("factoryId=null userId=null"));
        }
    }

    private void assertKnownWarning(Events events, String id, Class<? extends Throwable> type) {
        var warnings = events.events.stream().filter(e -> id.equals(e.getMDCPropertyMap().get("requestId"))
                && e.getFormattedMessage().startsWith("event=http.request.rejected ")).toList();
        assertEquals(1, warnings.size());
        var event = warnings.getFirst();
        assertEquals(Level.WARN, event.getLevel());
        assertNull(event.getThrowableProxy());
        assertTrue(event.getFormattedMessage().contains("exception=" + type.getName()));
        assertTrue(event.getFormattedMessage()
                .contains("origin=at " + TestController.class.getName() + ".log(HttpLoggingHttpTest.java:"));
        assertTrue(!event.getFormattedMessage().contains("requestId="));
    }

    private void assertOnlyAccess(Events events, String id) {
        assertTrue(events.events.stream().filter(e -> id.equals(e.getMDCPropertyMap().get("requestId")))
                .noneMatch(e -> !e.getLoggerName().equals("MES_HTTP_ACCESS") && e.getLevel()
                        .isGreaterOrEqual(Level.INFO)));
    }

    @Test
    void asyncCompletionDirectCompletionTimeoutAndContainerErrorHaveFinalStatus() throws Exception {
        try (var events = new Events(); var client = HttpClient.newHttpClient()) {
            var response = get(client, "/test-only/log-async/normal", null, null, null);
            String id = verify(events, response, 200, "/test-only/log-async/normal");
            assertEquals(id, response.body());
            String failed = verify(events, get(client, "/test-only/log-async/system", null, null, null), 500, "/test-only/log-async/system");
            for (String requestId : List.of(id, failed)) {
                assertEquals(1, events.events.stream()
                        .filter(e -> e.getFormattedMessage().equals("TEST ONLY callable work")
                                && requestId.equals(e.getMDCPropertyMap().get("requestId"))).count());
            }
            verify(events, get(client, "/test-only/log-direct-async", null, null, null), 200, "/test-only/log-direct-async");
            verify(events, get(client, "/test-only/log-timeout", null, null, null), 504, "/test-only/log-timeout");
            verify(events, get(client, "/test-only/log/filter-error", null, null, null), 500, "/test-only/log/filter-error");
            var excluded = get(client, "/test-only/log/excluded", null, null, null);
            assertEquals(200, excluded.statusCode());
            Thread.sleep(100);
            assertTrue(events.completed(excluded.headers().firstValue("X-Request-Id").orElseThrow()).isEmpty());
        }
    }
}
