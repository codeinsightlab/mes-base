package dev.mesbase.server;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import java.lang.reflect.Field;
import java.util.concurrent.Future;
import ch.qos.logback.core.util.FileSize;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: isolated JVM loads the actual Spring Logback XML, writes files and rolls scaled fixtures.
 */
class ProductionLogbackTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void actualFilesRouteLevelsAccessAndStackWithSingleMdcId(String profile) throws Exception {
        Path root = directory.resolve(profile);
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Probe.class.getName(), root.toString(), profile)
                .redirectErrorStream(true).redirectOutput(root.resolveSibling(profile + "-console.log").toFile());
        process.environment().put("MES_LOG_DIR", root.toString());
        process.environment().put("MES_LOG_INSTANCE", "TEST_ONLY_INSTANCE");
        var child = process.start();
        assertTrue(child.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, child.exitValue(), Files.readString(root.resolveSibling(profile + "-console.log")));
        Path logs = root.resolve("TEST_ONLY_INSTANCE");
        String app = Files.readString(logs.resolve("app.log"));
        String error = Files.readString(logs.resolve("error.log"));
        String access = Files.readString(logs.resolve("access.log"));
        assertTrue(app.contains("TEST_ONLY_INFO"));
        assertTrue(app.contains("TEST_ONLY_WARN"));
        assertTrue(app.contains("TEST_ONLY_SYSTEM_FAILURE"));
        assertTrue(app.contains("TEST_ONLY_CAUSE"));
        assertTrue(app.contains("TEST_ONLY_SUPPRESSED"));
        assertTrue(error.contains("TEST_ONLY_SYSTEM_FAILURE"));
        assertFalse(error.contains("TEST_ONLY_INFO"));
        assertFalse(error.contains("TEST_ONLY_WARN"));
        assertFalse(app.contains("TEST_ONLY_DEBUG"));
        assertTrue(access.contains("event=http.request.completed"));
        assertFalse(app.contains("event=http.request.completed"));
        assertFalse(error.contains("event=http.request.completed"));
        assertTrue(app.contains("requestId=-"));
        for (String text : new String[]{app, error, access}) {
            assertFalse(text.contains("\u001b["));
            for (String line : text.lines().filter(value -> value.contains("requestId=")).toList())
                assertEquals(1, line.split("requestId=", -1).length - 1, line);
        }
        String console = Files.readString(root.resolveSibling(profile + "-console.log"));
        assertEquals(profile.equals("dev"), console.contains("\u001b["));
        try (var archives = Files.list(logs.resolve("archive"))) {
            assertFalse(archives.anyMatch(path -> path.getFileName().toString().startsWith("old-")));
        }
        try (var archives = Files.list(logs.resolve("scaled"))) {
            var files = archives.filter(path -> path.toString().endsWith(".gz")).toList();
            assertTrue(files.size() >= 2, files.toString());
            assertTrue(files.stream().anyMatch(path -> path.toString().contains(".1.log.gz")), files.toString());
            for (Path file : files) {
                try (var input = new GZIPInputStream(Files.newInputStream(file))) {
                    assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("TEST_ONLY_ROLL"));
                }
            }
        }
        String rollingResult = Files.readString(logs.resolve("rolling-result.txt"));
        assertTrue(rollingResult.contains("retention=true cap=true"), rollingResult);
    }

    @Test
    void configuredDailySizeHistoryCapsAndStartupCleanupMatchSpecification() throws Exception {
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                getClass().getClassLoader().getResourceAsStream("logback-spring.xml"));
        var appenders = xml.getElementsByTagName("appender");
        for (int i = 0; i < appenders.getLength(); i++) {
            Element appender = (Element) appenders.item(i);
            String name = appender.getAttribute("name");
            if (name.equals("CONSOLE")) continue;
            Element policy = (Element) appender.getElementsByTagName("rollingPolicy").item(0);
            assertEquals(SizeAndTimeBasedRollingPolicy.class.getName(), policy.getAttribute("class"));
            assertEquals(name.equals("ERROR") ? "50MB" : "100MB", value(policy, "maxFileSize"));
            assertEquals(name.equals("APP") ? "30" : name.equals("ACCESS") ? "14" : "60", value(policy, "maxHistory"));
            assertEquals(name.equals("APP") ? "5GB" : name.equals("ACCESS") ? "3GB" : "2GB", value(policy, "totalSizeCap"));
            assertEquals("true", value(policy, "cleanHistoryOnStart"));
            assertTrue(value(policy, "fileNamePattern").endsWith(".%d{yyyy-MM-dd}.%i.log.gz"));
        }
    }

    private static String value(Element element, String tag) {
        return element.getElementsByTagName(tag).item(0).getTextContent();
    }

    public static class Probe {
        // TEST ONLY: await each policy's internal futures so synthetic clock jumps cannot race gzip writes.
        private static void awaitPolicyWork(SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy) throws Exception {
            for (String name : new String[]{"compressionFuture", "cleanUpFuture"}) {
                Field field = TimeBasedRollingPolicy.class.getDeclaredField(name);
                field.setAccessible(true);
                Future<?> future = (Future<?>) field.get(policy);
                if (future != null) future.get(5, TimeUnit.SECONDS);
            }
        }

        public static void main(String[] args) throws Exception {
            Path logs = Path.of(args[0], "TEST_ONLY_INSTANCE");
            Path archives = logs.resolve("archive");
            Files.createDirectories(archives);
            // Actual production policies clean archives just beyond each configured history.
            for (String type : new String[]{"app", "access", "error"}) {
                int days = type.equals("app") ? 32 : type.equals("access") ? 16 : 62;
                Path old = archives.resolve(type + "." + LocalDate.now().minusDays(days) + ".0.log.gz");
                try (var out = new GZIPOutputStream(Files.newOutputStream(old))) {
                    out.write("TEST ONLY old archive".getBytes(StandardCharsets.UTF_8));
                }
                Files.setLastModifiedTime(old, FileTime.from(Instant.now().minusSeconds(days * 86400L)));
            }
            var env = new StandardEnvironment();
            env.setActiveProfiles(args[1]);
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
            var system = new LogbackLoggingSystem(Probe.class.getClassLoader());
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(env), "classpath:logback-spring.xml", null);
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger app = context.getLogger("TEST_ONLY_APP");
            app.setLevel(Level.DEBUG);
            app.info("TEST_ONLY_STARTUP_WITHOUT_ID");
            MDC.put("requestId", "11111111-2222-3333-4444-555555555555");
            app.debug("TEST_ONLY_DEBUG");
            app.info("TEST_ONLY_INFO");
            app.warn("TEST_ONLY_WARN");
            var failure = new IllegalStateException("TEST_ONLY_SYSTEM_FAILURE", new IllegalArgumentException("TEST_ONLY_CAUSE"));
            failure.addSuppressed(new IllegalStateException("TEST_ONLY_SUPPRESSED"));
            app.error("TEST_ONLY_ERROR", failure);
            Logger access = context.getLogger("MES_HTTP_ACCESS");
            if (access.isAdditive()) throw new AssertionError("Access must not propagate to ROOT");
            access.info("event=http.request.completed method=GET path=/test-only status=200 durationMs=1 factoryId=null userId=null");
            MDC.clear();
            @SuppressWarnings("unchecked")
            var source = (RollingFileAppender<ILoggingEvent>) context.getLogger(Logger.ROOT_LOGGER_NAME)
                    .getAppender("APP");
            // Reuse the real file encoder; short fixtures exercise the same policy without filling GBs.
            var rolling = new RollingFileAppender<ILoggingEvent>();
            rolling.setContext(context);
            rolling.setFile(logs.resolve("scaled.log").toString());
            rolling.setEncoder(source.getEncoder());
            var policy = new SizeAndTimeBasedRollingPolicy<ILoggingEvent>();
            policy.setContext(context);
            policy.setParent(rolling);
            policy.setFileNamePattern(logs.resolve("scaled/test.%d{yyyy-MM-dd}.%i.log.gz").toString());
            policy.setMaxFileSize(new FileSize(256));
            policy.setMaxHistory(2);
            policy.setTotalSizeCap(new FileSize(1024));
            policy.setCleanHistoryOnStart(true);
            policy.start();
            rolling.setRollingPolicy(policy);
            rolling.setTriggeringPolicy(policy);
            rolling.start();
            Logger test = context.getLogger("TEST_ONLY_ROLLING");
            test.setAdditive(false);
            test.addAppender(rolling);
            long now = System.currentTimeMillis();
            for (int day = 0; day < 5; day++) {
                policy.getTimeBasedFileNamingAndTriggeringPolicy().setCurrentTime(now + day * 86400000L);
                for (int i = 0; i < 8; i++) {
                    test.info("TEST_ONLY_ROLL {} {} {}", day, i, "x".repeat(300));
                    awaitPolicyWork(policy);
                }
                policy.getTimeBasedFileNamingAndTriggeringPolicy().getArchiveRemover()
                        .cleanAsynchronously(Instant.ofEpochMilli(now + day * 86400000L)).get(5, TimeUnit.SECONDS);
            }
            rolling.stop();
            policy.stop();
            // Compression and cleanup are asynchronous. Observe the next settled cleanup cycle.
            policy.getTimeBasedFileNamingAndTriggeringPolicy().getArchiveRemover()
                    .cleanAsynchronously(Instant.ofEpochMilli(now + 5 * 86400000L)).get(5, TimeUnit.SECONDS);
            context.stop();
            try (var files = Files.list(archives)) {
                if (files.anyMatch(path -> path.toString().endsWith(".gz")))
                    throw new AssertionError("Startup history cleanup failed");
            }
            try (var files = Files.list(logs.resolve("scaled"))) {
                var list = files.filter(path -> path.toString().endsWith(".gz")).toList();
                String expired = LocalDate.now().toString();
                boolean retention = list.stream().noneMatch(path -> path.getFileName().toString().contains(expired));
                long size = 0;
                for (Path path : list) size += Files.size(path);
                Files.writeString(logs.resolve("rolling-result.txt"), "retention=" + retention + " cap=" + (size <= 1024));
            }
        }
    }
}
