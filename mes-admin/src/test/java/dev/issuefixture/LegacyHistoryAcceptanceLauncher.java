package dev.issuefixture;

import dev.MesBaseApplication;
import dev.mesbase.business.legacy.domain.LegacyImportCommand;
import dev.mesbase.business.legacy.service.LegacyHistoryImportService;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import org.springframework.beans.factory.config.BeanPostProcessor;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * TEST ONLY isolated real DEV acceptance. Never included in the production JAR.
 */
public final class LegacyHistoryAcceptanceLauncher {
    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(MesBaseApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry = (BeanDefinitionRegistry) factory;
            long hold = context.getEnvironment().getProperty("legacy.test-hold-ms", Long.class, 0L);
            String failMapper = context.getEnvironment().getProperty("legacy.fail-mapper", "");
            String failMethod = context.getEnvironment().getProperty("legacy.fail-method", "insert");
            String failKind = context.getEnvironment().getProperty("legacy.fail-kind", "");
            String failMode = context.getEnvironment().getProperty("legacy.fail-mode", "throw");
            String timing = context.getEnvironment().getProperty("legacy.fail-timing", "before");
            String marker = context.getEnvironment().getProperty("legacy.fail-marker");
            int failAfter = context.getEnvironment().getProperty("legacy.fail-after", Integer.class, 1);
            if (hold > 0 || !failMapper.isEmpty()) factory.addBeanPostProcessor(new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    Class<?> contract = List.of(LegacyMigrationMapper.class, WorkorderMapper.class,
                                    MesProductionExecutionMapper.class, MesProductionReportMapper.class).stream()
                            .filter(t -> t.isInstance(bean)).findFirst().orElse(null);
                    if (contract == null) return bean;
                    var calls = new AtomicInteger();
                    return Proxy.newProxyInstance(bean.getClass()
                            .getClassLoader(), new Class<?>[]{contract}, (proxy, method, values) -> {
                        boolean match = contract.getSimpleName().equals(failMapper) && method.getName()
                                .equals(failMethod)
                                && (failKind.isEmpty() || (values != null && values[0] instanceof Mapping m && failKind.equals(m.entityKind())));
                        // A transition fault targets the final statement, immediately before transaction commit.
                        if (match && method.getName().equals("transition")) match = "COMPLETED".equals(values[2]);
                        boolean fire = match && calls.incrementAndGet() == failAfter;
                        Runnable fail = () -> {
                            try {
                                if (marker != null)
                                    Files.writeString(Path.of(marker), "injected " + contract.getSimpleName() + "." + method.getName());
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                            if ("halt".equals(failMode)) Runtime.getRuntime().halt(86);
                            throw new IllegalStateException("TEST ONLY injected transaction failure");
                        };
                        if (fire && "before".equals(timing)) fail.run();
                        try {
                            Object result = method.invoke(bean, values);
                            if (contract == LegacyMigrationMapper.class && method.getName()
                                    .equals("transition") && "RUNNING".equals(values[2]) && hold > 0)
                                Thread.sleep(hold);
                            if (fire && "after".equals(timing)) fail.run();
                            return result;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
                }
            });
            for (var name : List.of("org.springframework.context.annotation.internalScheduledAnnotationProcessor", "startSystemJobs"))
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
        }));
        var context = application.run(args);
        var environment = context.getEnvironment();
        var mapper = new ObjectMapper().findAndRegisterModules();
        Long operator = environment.getRequiredProperty("legacy.operator", Long.class);
        var user = context.getBean(SysUserMapper.class).selectUserById(operator);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())
                || context.getBean(SysRoleMapper.class).selectRolePermissionByUserId(operator).stream()
                .noneMatch(r -> "PLATFORM".equals(r.getKind()) && "0".equals(r.getStatus())))
            throw new IllegalStateException("Enabled platform operator required");
        var login = new LoginUser(operator, user.getDeptId(), user, Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
        try {
            var source = context.getBean(ManagedDatasourceService.class);
            var command = mapper.readValue(Files.readString(Path.of(environment.getRequiredProperty("legacy.input"))), LegacyImportCommand.class);
            source.runIntegrated(new FactoryScope(new FactoryId(command.factoryId())), () -> {
                var importer = context.getBean(LegacyHistoryImportService.class);
                if (environment.getProperty("legacy.expect-conflict", Boolean.class, false)) {
                    try {
                        importer.prepare(command);
                        throw new AssertionError("Changed source accepted");
                    } catch (FoundationException expected) {
                        try {
                            Files.writeString(Path.of(environment.getRequiredProperty("legacy.result")), "{\"sourceConflictRejected\":true}");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    return;
                }
                importer.prepare(command);
                if (environment.getProperty("legacy.prepare-only", Boolean.class, false)) return;
                if (!environment.getProperty("legacy.no-approval", Boolean.class, false)) importer.approve(command);
                Supplier<Long> execute = () -> environment.getProperty("legacy.append", Boolean.class, false)
                        ? importer.append(command) : importer.execute(command);
                if (environment.getProperty("legacy.rule-conflict", Boolean.class, false)) {
                    var transaction = new TransactionTemplate(context.getBean("businessTransactionManager", PlatformTransactionManager.class));
                    transaction.execute(status -> {
                        var jdbc = new JdbcTemplate(context.getBean("businessDataSource", DataSource.class));
                        if (jdbc.update("UPDATE mes_legacy_migration_batch SET rule_version=? WHERE id=?", "TEST_ONLY_OTHER_RULE", command.batchId()) != 1)
                            throw new AssertionError("Test batch absent");
                        try {
                            execute.get();
                            throw new AssertionError("Different rule accepted");
                        } catch (FoundationException expected) {
                            if (expected.code() != ErrorCode.RESOURCE_CONFLICT) throw expected;
                        }
                        status.setRollbackOnly();
                        return null;
                    });
                    try {
                        Files.writeString(Path.of(environment.getRequiredProperty("legacy.result")), "{\"differentRuleRejectedAndRolledBack\":true}");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return;
                }
                if (environment.getProperty("legacy.rollback-first", Boolean.class, false)) {
                    var transaction = new TransactionTemplate(context.getBean("businessTransactionManager", PlatformTransactionManager.class));
                    transaction.execute(status -> {
                        execute.get();
                        status.setRollbackOnly();
                        return null;
                    });
                    try {
                        Files.writeString(Path.of(environment.getRequiredProperty("legacy.result")), "{\"fullComponentRollback\":true}");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return;
                }
                String barrier = environment.getProperty("legacy.test-barrier");
                if (barrier != null) {
                    try {
                        Path directory = Path.of(barrier);
                        Files.createDirectories(directory);
                        Files.writeString(directory.resolve("ready-" + ProcessHandle.current().pid()), "ready");
                        long deadline = System.nanoTime() + 30_000_000_000L;
                        while (true) {
                            try (var entries = Files.list(directory)) {
                                if (entries.count() >= environment.getProperty("legacy.test-participants", Integer.class, 2))
                                    break;
                            }
                            if (System.nanoTime() > deadline) throw new IllegalStateException("Barrier timeout");
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                Long id;
                try {
                    id = execute.get();
                } catch (RuntimeException failure) {
                    importer.recordFailure(command, failure instanceof FoundationException f && f.code() == ErrorCode.RESOURCE_CONFLICT);
                    throw failure;
                }
                if (environment.getProperty("legacy.drop-response", Boolean.class, false))
                    Runtime.getRuntime().halt(88);
                Long repeated = execute.get();
                if (!id.equals(repeated)) throw new AssertionError("Replay changed identity");
                try {
                    Files.writeString(Path.of(environment.getRequiredProperty("legacy.result")), "{\"workorderId\":\"" + id + "\",\"replaySameIdentity\":true}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
            if (environment.getProperty("legacy.exit-after", Boolean.class, false)) context.close();
        }
    }
}
