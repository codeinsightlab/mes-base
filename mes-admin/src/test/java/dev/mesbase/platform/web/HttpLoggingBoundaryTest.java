package dev.mesbase.platform.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpLoggingBoundaryTest {
    @AfterEach
    void clean() {
        MDC.clear();
        FactoryContext.clear();
    }

    @Test
    void restoresCompleteMdcAndFactoryOnSuccessAndFailure() throws Exception {
        var prior = Map.of("requestId", "outer", "correlationId", "background");
        var scope = new FactoryScope(new FactoryId("OUTER"));
        for (boolean fail : List.of(false, true)) {
            MDC.setContextMap(prior);
            FactoryContext.bind(scope);
            var request = new MockHttpServletRequest("GET", "/test-only");
            var response = new MockHttpServletResponse();
            try {
                new RequestBoundaryFilter().doFilter(request, response, (req, res) -> {
                    assertEquals(request.getAttribute(RequestBoundaryFilter.REQUEST_ID), MDC.get("requestId"));
                    assertTrue(FactoryContext.current().isEmpty());
                    MDC.put("extra", "inner");
                    MDC.remove("correlationId");
                    FactoryContext.bind(new FactoryScope(new FactoryId("INNER")));
                    if (fail) throw new ServletException("TEST ONLY exception");
                });
                assertFalse(fail);
            } catch (ServletException expected) {
                assertTrue(fail);
            }
            assertEquals(prior, MDC.getCopyOfContextMap());
            assertEquals(scope, FactoryContext.required());
        }
    }

    @Test
    void trustedAttributeIsValidatedAndExternalHeadersAreIgnored() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "client-id");
        String id = RequestBoundaryFilter.requestId(request);
        UUID.fromString(id);
        assertEquals(id, RequestBoundaryFilter.requestId(request));
        assertNotEquals("client-id", id);
        request.setAttribute(RequestBoundaryFilter.REQUEST_ID, "trusted_012-abc.def");
        assertEquals("trusted_012-abc.def", RequestBoundaryFilter.requestId(request));
        for (String invalid : List.of("", "a".repeat(129), "bad\r\nforged", "bad\"id", "bad id")) {
            request.setAttribute(RequestBoundaryFilter.REQUEST_ID, invalid);
            UUID.fromString(RequestBoundaryFilter.requestId(request));
        }
    }

    @Test
    void nestedErrorDispatchUsesSameMdcWithoutReenteringFactoryCleanup() throws Exception {
        var request = new MockHttpServletRequest("GET", "/test-only");
        var response = new MockHttpServletResponse();
        var filter = new RequestBoundaryFilter();
        filter.doFilter(request, response, (req, res) -> {
            var scope = new FactoryScope(new FactoryId("NESTED"));
            FactoryContext.bind(scope);
            MDC.put("extra", "prior");
            String id = MDC.get("requestId");
            request.setDispatcherType(DispatcherType.ERROR);
            filter.doFilter(request, response, (nestedReq, nestedRes) -> {
                assertEquals(scope, FactoryContext.required());
                assertEquals(id, MDC.get("requestId"));
                MDC.put("extra", "changed");
            });
            assertEquals("prior", MDC.get("extra"));
        });
        assertNull(MDC.getCopyOfContextMap());
        assertTrue(FactoryContext.current().isEmpty());
    }

    @Test
    void completionCapturesTrustedContextOnceRestoresMdcAndUsesTerminalStatus() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("MES_HTTP_ACCESS");
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var request = new MockHttpServletRequest("GET", "/api/test");
            var response = new MockHttpServletResponse();
            new RequestBoundaryFilter().doFilter(request, response, (req, res) -> {
                FactoryContext.bind(new FactoryScope(new FactoryId("A")));
                HttpRequestCompletionListener.captureUser(request, 42L);
            });
            response.setStatus(503);
            MDC.setContextMap(Map.of("requestId", "listener-outer", "tag", "keep"));
            var listener = new HttpRequestCompletionListener(List.of(), 1000);
            var event = new ServletRequestEvent(new MockServletContext(), request);
            listener.requestDestroyed(event);
            listener.requestDestroyed(event);
            assertEquals(1, events.list.size());
            String message = events.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("status=503"));
            assertTrue(message.contains("factoryId=A userId=42"));
            assertEquals(Map.of("requestId", "listener-outer", "tag", "keep"), MDC.getCopyOfContextMap());
        } finally {
            logger.detachAppender(events);
        }
    }

    @ParameterizedTest
    @CsvSource({"200,1000000,false", "200,0,true", "400,1000000,false", "400,0,true", "500,1000000,false"})
    void accessSummaryAndSeparateSlowWarningKeepMdcAndSingleId(int status, long threshold,
                                                               boolean expectedSlow)
            throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("MES_HTTP_ACCESS");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        Logger slowLogger = (Logger) LoggerFactory.getLogger(HttpRequestCompletionListener.class);
        var slowEvents = new ListAppender<ILoggingEvent>();
        slowEvents.start();
        slowLogger.addAppender(slowEvents);
        var events = new ListAppender<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        events.start();
        logger.addAppender(events);
        try {
            var request = new MockHttpServletRequest("GET", "/api/test");
            var response = new MockHttpServletResponse();
            new RequestBoundaryFilter().doFilter(request, response, (req, res) -> {
            });
            response.setStatus(status);
            String id = response.getHeader("X-Request-Id");
            MDC.setContextMap(Map.of("requestId", "outer", "tag", "preserved"));
            var listener = new HttpRequestCompletionListener(List.of(), threshold);
            var event = new ServletRequestEvent(new MockServletContext(), request);
            listener.requestDestroyed(event);
            listener.requestDestroyed(event);
            assertEquals(1, events.list.size());
            var logged = events.list.getFirst();
            assertEquals(Level.INFO, logged.getLevel());
            assertEquals(expectedSlow ? 1 : 0, slowEvents.list.size());
            if (expectedSlow) {
                assertEquals(Level.WARN, slowEvents.list.getFirst().getLevel());
                assertNull(slowEvents.list.getFirst().getThrowableProxy());
                assertTrue(slowEvents.list.getFirst().getFormattedMessage().startsWith("event=http.request.slow "));
            }
            assertNull(logged.getThrowableProxy());
            assertFalse(logged.getFormattedMessage().contains("requestId="));
            assertEquals(id, logged.getMDCPropertyMap().get("requestId"));
            var layout = new PatternLayout();
            layout.setContext(logger.getLoggerContext());
            layout.setPattern("%level requestId=%X{requestId:-} %msg%n");
            layout.start();
            String rendered = layout.doLayout(logged);
            assertEquals(1, rendered.split(id, -1).length - 1, rendered);
            assertEquals(1, rendered.split("requestId=", -1).length - 1, rendered);
            layout.stop();
            assertEquals(Map.of("requestId", "outer", "tag", "preserved"), MDC.getCopyOfContextMap());
        } finally {
            logger.detachAppender(events);
            logger.setLevel(previousLevel);
            slowLogger.detachAppender(slowEvents);
        }
    }

    @Test
    void ordinaryRejectionsAreSingleWarningsAtInfoLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpFailureLogging.class);
        Level previous = logger.getLevel();
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            logger.setLevel(Level.INFO);
            for (ErrorCode code : List.of(ErrorCode.BAD_REQUEST, ErrorCode.UNAUTHENTICATED, ErrorCode.UNPROCESSABLE)) {
                var request = new MockHttpServletRequest();
                var failure = new FoundationException(code);
                HttpFailureLogging.known(request, failure);
                HttpFailureLogging.known(request, failure);
                assertEquals(1, events.list.size());
                assertEquals(Level.WARN, events.list.getFirst().getLevel());
                assertNull(events.list.getFirst().getThrowableProxy());
                assertTrue(events.list.getFirst().getFormattedMessage().contains("origin=unknown"));
                assertTrue(events.list.getFirst().getFormattedMessage().contains("throwSite=at " + getClass().getName()));
                events.list.clear();
            }
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(events);
        }
    }

    @Test
    void knownRejectionsHaveNoStackAndSystemCauseIsLoggedOnlyOnceAcrossWrapping() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpFailureLogging.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var request = new MockHttpServletRequest();
            var rejection = new FoundationException(ErrorCode.FORBIDDEN);
            HttpFailureLogging.known(request, rejection);
            HttpFailureLogging.known(request, rejection);
            assertEquals(1, events.list.size());
            assertEquals(Level.WARN, events.list.getFirst().getLevel());
            assertNull(events.list.getFirst().getThrowableProxy());
            var cause = new IllegalStateException("TEST ONLY internal");
            HttpFailureLogging.known(request, new FoundationException(ErrorCode.ROUTING_METADATA_UNAVAILABLE, cause));
            HttpFailureLogging.unexpected(request, new ServletException(cause));
            assertEquals(2, events.list.size());
            assertEquals(Level.ERROR, events.list.getLast().getLevel());
            assertTrue(events.list.getLast().getThrowableProxy().getCause()
                    .getStackTraceElementProxyArray().length > 0);
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void securityInfrastructureFailureIsLoggedOnceAndResponseRemains503() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpFailureLogging.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var tokens = mock(TokenService.class);
            var request = new MockHttpServletRequest("GET", "/api/personal/profile");
            var response = new MockHttpServletResponse();
            when(tokens.getLoginUser(request)).thenThrow(new FoundationException(ErrorCode.AUTHENTICATION_UNAVAILABLE,
                    new IllegalStateException("TEST ONLY unavailable")));
            new RequestBoundaryFilter().doFilter(request, response, (req, res) ->
                    new SystemTokenFilter(tokens).doFilter(req, res, (innerReq, innerRes) -> {
                        throw new AssertionError("endpoint must not run");
                    }));
            assertEquals(503, response.getStatus());
            assertTrue(response.getContentAsString().contains("AUTHENTICATION_UNAVAILABLE"));
            assertEquals(1, events.list.size());
            assertEquals(Level.ERROR, events.list.getFirst().getLevel());
            assertEquals(response.getHeader("X-Request-Id"), events.list.getFirst().getMDCPropertyMap()
                    .get("requestId"));
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void fieldsCannotInjectWhitespaceOrExtraKeyValueTokens() {
        assertEquals("bad_id_forged_1", HttpRequestCompletionListener.safe("bad\tid\nforged=1"));
        assertEquals(512, HttpRequestCompletionListener.safe("x".repeat(1024)).length());
    }
}
