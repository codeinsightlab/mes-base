package dev.issuefixture;

import dev.MesBaseApplication;
import dev.mesbase.business.legacy.service.LegacyProductionActivationService;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.system.mapper.SysRoleMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import java.time.Duration;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicit authenticated DEV operations only; never packaged or registered as a production endpoint.
 */
public final class LegacyProductionActivationLauncher {
    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(MesBaseApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry = (BeanDefinitionRegistry) factory;
            for (String name : List.of("org.springframework.context.annotation.internalScheduledAnnotationProcessor", "startSystemJobs", "workOrderCompletionJob"))
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
        }));
        try (var context = app.run(args)) {
            var env = context.getEnvironment();
            var principal = context.getBean(TokenService.class)
                    .resolve(Files.readString(Path.of(env.getRequiredProperty("activation.token-file"))).trim());
            if (context.getBean(SysRoleMapper.class).selectRolePermissionByUserId(principal.getUserId()).stream()
                    .noneMatch(r -> "PLATFORM".equals(r.getKind()) && "0".equals(r.getStatus())))
                throw new IllegalStateException("Enabled authenticated platform operator required");
            SecurityContextHolder.getContext()
                    .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
            var scope = new FactoryScope(new FactoryId("TEST_DEV_PERF_20261019"));
            String scopeText = Files.readString(Path.of(env.getRequiredProperty("activation.scope-file")));
            if (!HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(scopeText.getBytes(StandardCharsets.UTF_8)))
                    .equals(env.getRequiredProperty("activation.scope-sha256")))
                throw new IllegalStateException("Frozen scope changed");
            var scopePlan = new ObjectMapper().readTree(scopeText);
            var metadata = context.getBean(LegacyMigrationMapper.class);
            var results = new ArrayList<Map<String, Object>>();
            boolean apply = "apply".equals(env.getProperty("activation.mode", "probe"));
            var coordinator = context.getBean(SyncCoordinator.class);
            var activation = context.getBean(LegacyProductionActivationService.class);
            var completions = context.getBean(ProductionCompletionService.class);
            var workorders = context.getBean(WorkOrderCompletionService.class);
            var orders = context.getBean(WorkorderMapper.class);
            var executions = context.getBean(MesProductionExecutionMapper.class);
            var transaction = new TransactionTemplate(context.getBean("businessTransactionManager", PlatformTransactionManager.class));
            context.getBean(ManagedDatasourceService.class).runIntegrated(scope, () -> {
                var run = coordinator.claim(scope, "workorder-completion", Duration.ofMinutes(30));
                try {
                    var filter = new WorkorderExample();
                    filter.createCriteria().andStatusEqualTo("HISTORICAL");
                    filter.setOrderByClause("id ASC");
                    var ids = transaction.execute(s -> orders.selectByExample(filter).stream().map(w -> w.getId())
                            .toList());
                    if (scopePlan.size() != 566 || ids.size() > scopePlan.size())
                        throw new IllegalStateException("Activation scope mismatch");
                    for (Long id : ids) {
                        var expected = scopePlan.path(id.toString());
                        var original = transaction.execute(s -> metadata.order(id));
                        if (!expected.isTextual() || original == null || !expected.asText()
                                .equals(original.sourceHash()))
                            throw new IllegalStateException("Source identity outside frozen scope");
                    }
                    var chosen = apply ? ids : ids.stream().limit(2).toList();
                    long checked = 0;
                    for (Long id : chosen) {
                        coordinator.progress(run, ++checked);
                        if (apply) {
                            int count = activation.activate(id);
                            var rows = new MesProductionExecutionExample();
                            rows.createCriteria().andWorkorderIdEqualTo(id);
                            var current = transaction.execute(s -> executions.selectByExample(rows));
                            int complete = 0;
                            var blocked = new ArrayList<Map<String, Object>>();
                            for (var e : current) {
                                if (ProductionExecutionStatus.COMPLETED.getValue()
                                        .equals(e.getStatus()) || completions.reconcile(e.getId(), run)) complete++;
                                else {
                                    var summary = completions.summary(e.getId());
                                    blocked.add(Map.of("executionId", e.getId(), "reason", summary.blockedReason() == null ? "未取得可信完工事实" : summary.blockedReason()));
                                }
                            }
                            boolean parentComplete = workorders.reconcile(id, run);
                            var result = Map.<String, Object>of("workorderId", id, "activatedExecutions", count,
                                    "completedExecutions", complete, "workorderCompleted", parentComplete, "blocked", blocked);
                            results.add(result);
                            try {
                                Files.writeString(Path.of(env.getRequiredProperty("activation.result") + ".jsonl"),
                                        new ObjectMapper().writeValueAsString(result) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException failure) {
                                throw new UncheckedIOException(failure);
                            }
                            System.out.println("Activation checkpoint orders=" + checked + " executions=" + count + " completed=" + complete + " blocked=" + blocked.size());
                        } else {
                            transaction.executeWithoutResult(s -> {
                                int count = activation.activate(id);
                                var rows = new MesProductionExecutionExample();
                                rows.createCriteria().andWorkorderIdEqualTo(id);
                                int complete = 0;
                                var blocked = new ArrayList<String>();
                                for (var e : executions.selectByExample(rows)) {
                                    var summary = completions.summary(e.getId());
                                    if (summary.canComplete()) {
                                        if (!completions.reconcile(e.getId(), run))
                                            throw new IllegalStateException("Eligible execution did not complete");
                                        complete++;
                                    } else blocked.add(summary.blockedReason());
                                }
                                boolean parentComplete = workorders.reconcile(id, run);
                                results.add(Map.of("workorderId", id, "activatedExecutions", count, "completedExecutions", complete,
                                        "workorderCompleted", parentComplete, "blockedReasons", blocked));
                                s.setRollbackOnly();
                            });
                        }
                    }
                    coordinator.succeed(run, null);
                } catch (RuntimeException failure) {
                    coordinator.fail(run);
                    throw failure;
                }
            });
            Files.writeString(Path.of(env.getRequiredProperty("activation.result")), new ObjectMapper().writeValueAsString(Map.of(
                    "apply", apply, "operatorUserId", SecurityUtils.getUserId(), "results", results)));
            SecurityContextHolder.clearContext();
        }
    }
}
