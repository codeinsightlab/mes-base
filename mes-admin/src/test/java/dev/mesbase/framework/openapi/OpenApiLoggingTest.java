package dev.mesbase.framework.openapi;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.platform.web.HttpRequestCompletionListener;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.ServletRequestEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiLoggingTest {
    @Test
    void successRejectionAndSystemFailureUseSharedTerminalEventAndSafeFullSourceTrace() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        root.addAppender(events);
        try {
            for (String kind : List.of("normal", "rejection", "system")) {
                events.list.clear();
                var request = new MockHttpServletRequest("GET", "/open-api/test-only");
                request.addHeader("Authorization", "Bearer TEST_ONLY_SECRET_TOKEN");
                var response = new MockHttpServletResponse();
                var openapi = new OpenApiRequestFilter(new OpenApiHttp(new ObjectMapper()));
                new RequestBoundaryFilter().doFilter(request, response, (req, res) -> openapi.doFilter(req, res, (innerReq, innerRes) -> {
                    if (kind.equals("rejection")) throw new OpenApiException(OpenApiError.INVALID_REQUEST);
                    if (kind.equals("rejection")) {
                        assertEquals(400, response.getStatus());
                        assertTrue(events.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR));
                    }
                    if (kind.equals("system")) {
                        var failure = new IllegalStateException("TEST_ONLY_SECRET_MESSAGE");
                        var frames = new StackTraceElement[40];
                        for (int i = 0; i < frames.length; i++)
                            frames[i] = new StackTraceElement("TEST_ONLY_Type", "frame" + i, "Fixture.java", i);
                        failure.setStackTrace(frames);
                        failure.addSuppressed(new IllegalStateException("TEST_ONLY_SECRET_SUPPRESSED"));
                        throw failure;
                    }
                }));
                String id = response.getHeader("X-Request-Id");
                var listener = new HttpRequestCompletionListener(List.of());
                var event = new ServletRequestEvent(new MockServletContext(), request);
                listener.requestDestroyed(event);
                listener.requestDestroyed(event);
                var summaries = events.list.stream()
                        .filter(e -> e.getFormattedMessage().startsWith("event=http.request.completed")).toList();
                assertEquals(1, summaries.size());
                assertEquals(id, summaries.getFirst().getMDCPropertyMap().get("requestId"));
                assertTrue(summaries.getFirst().getFormattedMessage().contains("clientId="));
                assertTrue(events.list.stream().noneMatch(e -> e.getFormattedMessage().contains("TEST_ONLY_SECRET")));
                if (kind.equals("rejection")) {
                    assertEquals(400, response.getStatus());
                    assertTrue(events.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR));
                }
                if (kind.equals("system")) {
                    var errors = events.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
                    assertEquals(1, errors.size());
                    assertTrue(errors.getFirst().getFormattedMessage().contains("frame39"));
                    assertTrue(errors.getFirst().getFormattedMessage().contains("suppressed:"));
                    assertEquals(500, response.getStatus());
                }
            }
        } finally {
            root.detachAppender(events);
        }
    }
}
