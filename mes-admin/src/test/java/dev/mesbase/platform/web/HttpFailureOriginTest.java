package dev.mesbase.platform.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFailureOriginTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpFailureLogging.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;
    private Map<String, String> previousMdc;

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        previousMdc = MDC.getCopyOfContextMap();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
        MDC.put("requestId", "TEST_ONLY_ORIGIN");
    }

    @AfterEach
    void restore() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        RequestBoundaryFilter.restoreMdc(previousMdc);
    }

    @Test
    void serviceValidatorParameterChecksReportsBusinessOriginAndActualThrowSite() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test-only");
        request.setQueryString("secret=must-not-log");
        request.setAttribute(RequestBoundaryFilter.REQUEST_ID, "TEST_ONLY_ORIGIN");
        try {
            dev.mesbase.business.production.service.BusinessValidationPathFixture.service();
        } catch (FoundationException failure) {
            HttpFailureLogging.known(request, failure);
        }
        String line = events.list.getFirst().getFormattedMessage();
        assertTrue(line.contains("method=POST path=/api/test-only"), line);
        assertTrue(line.contains("origin=at dev.mesbase.business.production.service.BusinessValidationPathFixture.validator"), line);
        assertTrue(line.contains("throwSite=at dev.mesbase.common.validation.ParameterChecks.reject"), line);
        assertFalse(line.contains("secret"), line);
        assertFalse(line.contains("requestId="), line);
    }

    static final class Service {
        static void validate() { Validator.validate(); }
        static void execute() { Validator.validate(); }
    }

    static final class Validator {
        static void validate() { dev.mesbase.common.validation.ParameterChecks.reject(ErrorCode.BAD_REQUEST); }
    }

    @Test
    void actualCreationFramesPointToSourceForBothKnownTypes() throws Exception {
        var foundation = new FoundationException(ErrorCode.UNPROCESSABLE);
        var service = new ServiceException("TEST_ONLY_SECRET");
        HttpFailureLogging.known(new MockHttpServletRequest(), foundation);
        HttpFailureLogging.known(new MockHttpServletRequest(), service, "BUSINESS_REJECTED");
        Throwable[] failures = {foundation, service};
        for (int i = 0; i < failures.length; i++) {
            Throwable failure = failures[i];
            StackTraceElement frame = failure.getStackTrace()[0];
            assertEquals(getClass().getName(), frame.getClassName());
            assertEquals("actualCreationFramesPointToSourceForBothKnownTypes", frame.getMethodName());
            String source = Files.readAllLines(Path.of("mes-admin/src/test/java/dev/mesbase/platform/web/HttpFailureOriginTest.java"))
                    .get(frame.getLineNumber() - 1);
            assertTrue(source.contains("new " + failure.getClass().getSimpleName()), source);
            var event = events.list.get(i);
            assertEquals(Level.WARN, event.getLevel());
            assertNull(event.getThrowableProxy());
            assertTrue(event.getFormattedMessage().contains("exception=" + failure.getClass().getName()));
            assertTrue(event.getFormattedMessage().contains("origin=unknown"));
            assertTrue(event.getFormattedMessage().contains("throwSite=at " + frame));
            assertFalse(event.getFormattedMessage().contains("TEST_ONLY_SECRET"));
            assertFalse(event.getFormattedMessage().contains("requestId="));
            assertFalse(event.getFormattedMessage().contains("rootCauseOrigin="));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "native", "no-line", "no-file", "control"})
    void unavailableSourceIsUnknownAndRemainsSingleLine(String kind) {
        var failure = new FoundationException(ErrorCode.BAD_REQUEST);
        failure.setStackTrace(kind.equals("empty") ? new StackTraceElement[0] : new StackTraceElement[]{
                new StackTraceElement("TEST_ONLY.Type", "run", kind.equals("no-file") ? null
                        : kind.equals("control") ? "Fixture\n.java" : "Fixture.java",
                        kind.equals("native") ? -2 : kind.equals("no-line") ? -1 : 42)});
        HttpFailureLogging.known(new MockHttpServletRequest(), failure);
        assertTrue(events.list.getFirst().getFormattedMessage().contains("origin=unknown"));
        assertFalse(events.list.getFirst().getFormattedMessage().contains("\n"));
        assertNull(events.list.getFirst().getThrowableProxy());
    }

    @Test
    void deepestCauseIsAdditionalOriginWithoutReplacingWrapperOrLeakingMessages() {
        var cause = dev.mesbase.business.production.service.BusinessValidationPathFixture.cause();
        var failure = new FoundationException(ErrorCode.UNPROCESSABLE, new IllegalStateException("TEST_ONLY_SECRET_WRAPPER", cause));
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("TEST_ONLY.Wrapper", "translate", "Wrapper.java", 29)});
        HttpFailureLogging.known(new MockHttpServletRequest("GET", "/open-api/test-only"), failure);
        String text = events.list.getFirst().getFormattedMessage();
        assertTrue(text.contains("origin=unknown"));
        assertTrue(text.contains("throwSite=at TEST_ONLY.Wrapper.translate(Wrapper.java:29)"));
        assertTrue(text.contains("rootCauseOrigin=at dev.mesbase.business.production.service.BusinessValidationPathFixture.cause"));
        assertFalse(text.contains("TEST_ONLY_SECRET"));
        assertNull(events.list.getFirst().getThrowableProxy());
    }

    @Test
    void serviceAdviceUsesSameDeduplicatedBoundaryAndPreservesResponse() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestBoundaryFilter.REQUEST_ID, "TEST_ONLY_ORIGIN");
        var service = new ServiceException("TEST ONLY rejected");
        var cause = new IllegalStateException("TEST ONLY cause");
        service.initCause(cause);
        var advice = new FoundationErrorAdvice();
        var first = advice.sourceBusiness(service, request);
        var second = advice.sourceBusiness(service, request);
        HttpFailureLogging.known(request, new FoundationException(ErrorCode.UNPROCESSABLE, service));
        assertEquals(422, first.getStatusCode().value());
        assertEquals(first, second);
        assertEquals("BUSINESS_REJECTED", first.getBody().errorCode());
        assertEquals(1, events.list.size());
        assertTrue(events.list.getFirst().getFormattedMessage().contains("rootCauseOrigin=unknown"));
    }

    @Test
    void cyclicCauseIsUnknownAndDeduplicationTerminates() {
        var cause = new IllegalStateException("TEST ONLY cause");
        var failure = new FoundationException(ErrorCode.UNPROCESSABLE, cause);
        cause.initCause(failure);
        var request = new MockHttpServletRequest();
        HttpFailureLogging.known(request, failure);
        HttpFailureLogging.known(request, failure);
        assertEquals(1, events.list.size());
        assertTrue(events.list.getFirst().getFormattedMessage().endsWith("rootCauseOrigin=unknown"));
    }

    @Test
    void productionInfoConsoleHasSingleWarnOriginsAndSystemErrorStack(@TempDir Path directory) throws Exception {
        Path consoleFile = directory.resolve("console.log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(consoleFile.toFile());
        builder.environment().put("MES_LOG_DIR", directory.toString());
        builder.environment().put("MES_LOG_INSTANCE", "origin");
        var process = builder.start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        String console = Files.readString(consoleFile);
        assertEquals(0, process.exitValue(), console);
        assertEquals(2, console.lines()
                .filter(line -> line.contains("WARN") && line.contains("event=http.request.rejected ")).count());
        for (String line : console.lines().filter(value -> value.contains("event=http.request.rejected ")).toList()) {
            assertEquals(1, line.split("requestId=", -1).length - 1);
            assertTrue(line.contains("origin=unknown"), line);
            assertTrue(line.matches(".*throwSite=at .*\\.main\\(HttpFailureOriginTest.java:[0-9]+\\).*"), line);
        }
        assertFalse(console.contains("TEST_ONLY_SECRET"));
        assertTrue(console.contains("ERROR"));
        assertTrue(console.contains("java.lang.IllegalStateException: TEST ONLY unexpected"));
        assertTrue(console.contains("Caused by: java.lang.IllegalArgumentException: TEST ONLY root"));
        assertTrue(console.contains("\tat " + Probe.class.getName() + ".main(HttpFailureOriginTest.java:"));
    }

    public static class Probe {
        public static void main(String[] args) {
            var env = new StandardEnvironment();
            env.setActiveProfiles("prod");
            var system = new LogbackLoggingSystem(Probe.class.getClassLoader());
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(env), "classpath:logback-spring.xml", null);
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            if (root.getEffectiveLevel() != Level.INFO) throw new AssertionError("ROOT must stay INFO");
            MDC.put("requestId", "11111111-2222-3333-4444-555555555555");
            var foundation = new FoundationException(ErrorCode.UNPROCESSABLE);
            var service = new ServiceException("TEST_ONLY_SECRET");
            var request = new MockHttpServletRequest();
            HttpFailureLogging.known(request, foundation);
            HttpFailureLogging.known(request, foundation);
            HttpFailureLogging.known(request, service, "BUSINESS_REJECTED");
            HttpFailureLogging.known(request, service, "BUSINESS_REJECTED");
            HttpFailureLogging.unexpected(request, new IllegalStateException("TEST ONLY unexpected", new IllegalArgumentException("TEST ONLY root")));
            MDC.clear();
            system.cleanUp();
        }
    }
}
