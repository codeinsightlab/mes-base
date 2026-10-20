package dev.issuefixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.MesBaseApplication;
import dev.mesbase.business.legacy.domain.LegacyImportCommand;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import dev.mesbase.business.legacy.service.LegacyHistoryImportService;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.system.mapper.SysRoleMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.sql.Connection;
import java.util.Locale;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Explicit operations launcher, test classpath only. No production endpoint or automatic startup job.
 */
public final class LegacyAuthorizedBatchLauncher {
    public record Entry(String file, String sha256, String sourceHash, String phase, boolean append) {
    }

    public record Manifest(String factoryId, String snapshotHash, String authorizationSha256,
                           List<Entry> entries) {
    }

    public record RollbackScope(String manifestHash, List<String> commands) {
    }

    private static void verifyReportIdentities(ConfigurableApplicationContext context, ObjectMapper json,
                                               LegacyImportCommand command) throws Exception {
        var mappings = context.getBean(LegacyMigrationMapper.class);
        var reports = context.getBean(MesProductionReportMapper.class);
        for (var e : command.executions()) {
            String executionKey = sha(json.writeValueAsString(List.of("03dbc952-7403-11ef-802c-0242ac130002", "prod_j2eedb", "EXECUTION", e.sourceKey())));
            var execution = mappings.find(executionKey);
            for (var r : e.reports()) {
                String reportKey = sha(json.writeValueAsString(List.of("03dbc952-7403-11ef-802c-0242ac130002", "prod_j2eedb", "pro_reporting_work", r.sourceId())));
                var mapping = mappings.find(reportKey);
                var row = reports.selectByPrimaryKey(mapping.entityId());
                String requestId = UUID.nameUUIDFromBytes(reportKey.getBytes(StandardCharsets.UTF_8)).toString();
                if (row == null || !row.getExecutionId().equals(execution.entityId())
                        || !row.getRequestId().equals(requestId) || !row.getCommandFingerprint().equals(r.sourceHash())
                        || row.getQuantityDelta().compareTo(r.quantity()) != 0
                        || !row.getOperatorUserId().equals(r.operatorUserId()) || !row.getReportTime()
                        .equals(r.reportTime()))
                    throw new IllegalStateException("Live generated key/source identity mismatch");
            }
        }
    }

    private static String sha(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static final class SqlCounter implements Interceptor {
        private final Thread owner = Thread.currentThread();
        private final Map<String, Long> counts = new LinkedHashMap<>();

        void reset() {
            counts.clear();
        }

        Map<String, Long> snapshot() {
            return Map.copyOf(counts);
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            if (Thread.currentThread() == owner) {
                String sql = ((StatementHandler) invocation.getTarget()).getBoundSql().getSql().strip()
                        .toLowerCase(Locale.ROOT);
                String kind = sql.startsWith("select") ? "select" : sql.startsWith("insert") ? "insert" : "other";
                String table = sql.contains("sys_user") ? "user" : sql.contains("mes_legacy_source_mapping") ? "mapping"
                        : sql.contains("mes_production_report") ? "report" : "other";
                counts.merge(table + "_" + kind, 1L, Long::sum);
            }
            return invocation.proceed();
        }
    }

    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(MesBaseApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry = (BeanDefinitionRegistry) factory;
            for (String name : List.of("org.springframework.context.annotation.internalScheduledAnnotationProcessor", "startSystemJobs")) {
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            }
        }));
        try (var context = app.run(args)) {
            var env = context.getEnvironment();
            var json = new ObjectMapper().findAndRegisterModules();
            Path root = Path.of(env.getRequiredProperty("legacy.directory")).toRealPath();
            String manifestText = Files.readString(root.resolve("manifest.json"));
            String manifestHash = sha(manifestText);
            if (!manifestHash.equals(env.getRequiredProperty("legacy.manifest-sha256")))
                throw new IllegalArgumentException("Frozen manifest changed");
            Manifest manifest = json.readValue(manifestText, Manifest.class);
            if (!"TEST_DEV_PERF_20261019".equals(manifest.factoryId())
                    || !sha(Files.readString(root.resolve("authorization.txt"))).equals(manifest.authorizationSha256()))
                throw new IllegalArgumentException("Authorization scope mismatch");
            // Verify the complete immutable command set before waiting for real login.
            for (Entry entry : manifest.entries()) {
                Path file = root.resolve(entry.file()).normalize();
                if (!file.startsWith(root) || !sha(Files.readString(file)).equals(entry.sha256()))
                    throw new IllegalArgumentException("Command file scope/hash mismatch");
                LegacyImportCommand c = json.readValue(Files.readString(file), LegacyImportCommand.class);
                if (!manifest.factoryId().equals(c.factoryId()) || !manifest.snapshotHash().equals(c.snapshotHash())
                        || !entry.sourceHash().equals(c.sourceHash()))
                    throw new IllegalArgumentException("Command component scope mismatch");
            }
            var completed = new LinkedHashMap<String, String>();
            String resumeHash = env.getProperty("legacy.resume-results-sha256");
            if (resumeHash != null) {
                String priorResults = Files.readString(root.resolve("results.jsonl"));
                if (!sha(priorResults).equals(resumeHash)) throw new IllegalStateException("Resume evidence changed");
                for (String line : priorResults.lines().toList()) {
                    var r = json.readTree(line);
                    String phase = r.path("phase").asText();
                    String hash = r.path("commandHash").asText();
                    if (List.of("trial", "bulk").contains(phase) && r.path("replaySameIdentity").asBoolean()) {
                        if (manifest.entries().stream()
                                .noneMatch(e -> e.sha256().equals(hash) && e.phase().equals(phase)))
                            throw new IllegalStateException("Resume command outside manifest");
                        String previous = completed.putIfAbsent(phase + ":" + hash, r.path("workorderId").asText());
                        if (previous != null && !previous.equals(r.path("workorderId").asText()))
                            throw new IllegalStateException("Resume identities disagree");
                    }
                }
            }
            var counter = new SqlCounter();
            for (String name : List.of("masterSqlSessionFactory", "businessSqlSessionFactory"))
                context.getBean(name, SqlSessionFactory.class).getConfiguration().addInterceptor(counter);
            Files.writeString(root.resolve("ready.json"), json.writeValueAsString(Map.of("manifestHash", manifestHash)));
            var tokens = context.getBean(TokenService.class);
            var roles = context.getBean(SysRoleMapper.class);
            var managed = context.getBean(ManagedDatasourceService.class);
            var importer = context.getBean(LegacyHistoryImportService.class);
            boolean validationOnly = env.getProperty("legacy.replay-validation-only", Boolean.class, false);
            var rollbackScope = validationOnly ? json.readValue(Files.readString(root.resolve("rollback-commands.json")), RollbackScope.class)
                    : new RollbackScope(manifestHash, List.of());
            if (!manifestHash.equals(rollbackScope.manifestHash()) || rollbackScope.commands().size() > 2
                    || rollbackScope.commands().stream()
                    .anyMatch(h -> manifest.entries().stream().noneMatch(e -> e.sha256().equals(h))))
                throw new IllegalStateException("Rollback validation outside frozen scope");
            for (String phase : validationOnly ? List.of("rollback", "validation") : List.of("trial", "bulk", "replay")) {
                Path permit = root.resolve(phase + "-permit.json");
                while (!Files.exists(permit)) {
                    if (Files.exists(root.resolve("STOP"))) return;
                    Thread.sleep(250);
                }
                var gate = json.readTree(Files.readString(permit));
                if (!manifestHash.equals(gate.path("manifestHash").asText()) || !gate.path("go").asBoolean())
                    throw new IllegalArgumentException("Phase gate rejected");
                for (Entry entry : manifest.entries()) {
                    boolean replayOnly = List.of("replay", "validation").contains(phase);
                    boolean rollbackOnly = "rollback".equals(phase);
                    String originalId = completed.get(entry.phase() + ":" + entry.sha256());
                    if ("validation".equals(phase) && originalId == null) continue;
                    if (rollbackOnly && (!rollbackScope.commands().contains(entry.sha256()) || originalId != null))
                        continue;
                    if (!replayOnly && !rollbackOnly && !phase.equals(entry.phase())) continue;
                    if (!replayOnly && originalId != null) continue;
                    if (Files.exists(root.resolve("STOP"))) return;
                    // A per-component permit allows the coordinator to audit bounded checkpoints.
                    Path step = root.resolve("permits").resolve(phase + "-" + entry.sha256());
                    while (!Files.exists(step)) {
                        if (Files.exists(root.resolve("STOP"))) return;
                        Thread.sleep(100);
                    }
                    var principal = tokens.resolve(Files.readString(root.resolve("session-token")).trim());
                    if (roles.selectRolePermissionByUserId(principal.getUserId()).stream()
                            .noneMatch(r -> "PLATFORM".equals(r.getKind()) && "0".equals(r.getStatus())))
                        throw new IllegalStateException("Authenticated enabled platform role required");
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
                    long started = System.nanoTime();
                    try {
                        String text = Files.readString(root.resolve(entry.file()));
                        if (!sha(text).equals(entry.sha256()))
                            throw new IllegalStateException("Frozen command changed");
                        var command = json.readValue(text, LegacyImportCommand.class);
                        managed.runIntegrated(new FactoryScope(new FactoryId(command.factoryId())), () -> {
                            Runnable work = () -> {
                                if (!replayOnly) {
                                    importer.prepare(command);
                                    importer.approve(command);
                                }
                                counter.reset();
                                long transactionStarted = System.nanoTime();
                                Long id = entry.append() ? importer.append(command) : importer.execute(command);
                                long transactionMs = (System.nanoTime() - transactionStarted) / 1_000_000;
                                var firstSql = counter.snapshot();
                                counter.reset();
                                long replayStarted = System.nanoTime();
                                Long replay = replayOnly ? Long.valueOf(originalId)
                                        : entry.append() ? importer.append(command) : importer.execute(command);
                                long replayMs = (System.nanoTime() - replayStarted) / 1_000_000;
                                var replaySql = counter.snapshot();
                                if (!id.equals(replay)) throw new IllegalStateException("Replay identity changed");
                                try {
                                    if (rollbackOnly) verifyReportIdentities(context, json, command);
                                    var resultData = new LinkedHashMap<String, Object>();
                                    resultData.put("phase", phase);
                                    resultData.put("rolledBack", rollbackOnly);
                                    resultData.put("commandHash", entry.sha256());
                                    resultData.put("batchId", command.batchId());
                                    resultData.put("workorderId", id.toString());
                                    resultData.put("operatorId", principal.getUserId().toString());
                                    resultData.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
                                    resultData.put("transactionMs", transactionMs);
                                    resultData.put("replaySameIdentity", true);
                                    resultData.put("replayMs", replayMs);
                                    resultData.put("sqlFirstTransaction", firstSql);
                                    resultData.put("sqlReplay", replaySql);
                                    resultData.put("runtimeArtifactHash", env.getRequiredProperty("legacy.artifact-sha256"));
                                    String result = json.writeValueAsString(resultData);
                                    Files.writeString(root.resolve("results.jsonl"), result + "\n",
                                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                    if (!replayOnly && !rollbackOnly)
                                        completed.put(entry.phase() + ":" + entry.sha256(), id.toString());
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                            };
                            if (rollbackOnly) {
                                var tx = new TransactionTemplate(context.getBean("businessTransactionManager", PlatformTransactionManager.class));
                                tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                                tx.executeWithoutResult(status -> {
                                    work.run();
                                    status.setRollbackOnly();
                                });
                            } else work.run();
                        });
                    } catch (RuntimeException failure) {
                        Files.writeString(root.resolve("failure.json"), json.writeValueAsString(Map.of(
                                "phase", phase, "commandHash", entry.sha256(), "exception", failure.toString())));
                        throw failure;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }
                Files.writeString(root.resolve(phase + "-complete.json"), "{\"complete\":true}");
            }
            // Keep formal HTTP available until the coordinator finishes final reconciliation.
            while (!Files.exists(root.resolve("STOP"))) Thread.sleep(250);
        }
    }
}
