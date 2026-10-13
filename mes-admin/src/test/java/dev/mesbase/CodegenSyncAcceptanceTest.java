package dev.mesbase;
import java.util.function.UnaryOperator;
import dev.mesbase.platform.codegen.JdbcMetadataReader;
import dev.mesbase.platform.codegen.ConfiguredCodegenService;

import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.codegen.CodegenRequest;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST ONLY: compile and execute generated sync against schema without code/name/status/externalId fields.
 */
class CodegenSyncAcceptanceTest {
    @TempDir
    Path temporary;

    @Configuration
    @EnableTransactionManagement
    static class Transactions {
    }

    @Test
    void generatedDefaultStartsAndMainServiceUsesARealSpringTransaction() throws Exception {
        temporary = temporary.toRealPath();
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sync_probe(id BIGINT AUTO_INCREMENT PRIMARY KEY,sku VARCHAR(32) NOT NULL,"
                + "label VARCHAR(80) NOT NULL,payload VARCHAR(64),amount DECIMAL(18,6),CHECK(label <> 'DB_REJECT'))");
        String root = "dev.mesbase.business.synctest";
        var request = new CodegenRequest("test", null, "sync_probe", "synctest", "TEST ONLY", root, "TEST ONLY",
                CodegenRequest.ScopeKind.FACTORY_SCOPED, CodegenRequest.OverwritePolicy.FAIL, false,
                temporary, temporary.resolve("frontend"), "mes-business", true, "sku");
        new ConfiguredCodegenService().generate(List.of(new ConfiguredCodegenService.Plan(request, new JdbcMetadataReader().read(source, null, "sync_probe"), null)), Set.of("all"), UnaryOperator.identity());
        Path backend = request.targetBackendRoot(), classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null); var files = Files.walk(backend.resolve("src/main/java"))) {
            assertTrue(compiler.getTask(null, manager, null, List.of("-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString(), "-parameters"), null, manager.getJavaFileObjectsFromPaths(
                    files.filter(path -> path.toString().endsWith(".java")).toList())).call());
        }
        try (var files = Files.walk(backend.resolve("src/main/resources"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path output = classes.resolve(backend.resolve("src/main/resources").relativize(file));
                Files.createDirectories(output.getParent());
                Files.copy(file, output);
            }
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader());
             var registry = new RuntimeRegistry(); var validators = Validation.buildDefaultValidatorFactory()) {
            Thread.currentThread().setContextClassLoader(loader);
            var scope = new FactoryScope(new FactoryId("A"));
            var catalog = new InMemoryFactoryCatalog(List.of(new Factory(scope.factoryId(), "TEST", "A", "TEST ONLY", FactoryStatus.ENABLED)));
            var binding = new BusinessRuntimeBinding(scope.factoryId(), new DatasourceIdentity("TEST_ONLY"), 1);
            var mapperType = loader.loadClass(root + ".dao.SyncProbeMapper");
            var writerType = loader.loadClass(root + ".service.impl.SyncProbeServiceImpl");
            var serviceType = loader.loadClass(root + ".service.impl.SyncProbeServiceImpl");
            var inputType = loader.loadClass(root + ".sync.ExternalSyncProbe");
            registry.registerDedicated(scope.factoryId(), binding.datasourceIdentity(), 1, source, () -> {
            }, Set.of(mapperType));
            var routing = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog,
                    Map.of(scope.factoryId(), binding)), registry);
            var manager = new OwnedTransactionManager(routing);
            var factory = PersistenceFactories.create(routing, Set.of(mapperType), configuration -> {
            }, manager::verifyAccess);
            Object mapper = new SqlSessionTemplate(factory).getMapper(mapperType);
            var coordinator = mock(SyncCoordinator.class);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.setClassLoader(loader);
                context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.system.enabled", "true")));
                context.register(Transactions.class);
                context.registerBean("businessTransactionManager", OwnedTransactionManager.class, () -> manager);
                context.registerBean(Validator.class, validators::getValidator);
                context.registerBean(SyncCoordinator.class, () -> coordinator);
                var redis = mock(RedisCache.class);
                var caches = new BusinessCacheProcessor(redis, new SpringCommitActions(), context.getEnvironment());
                context.registerBean(BusinessCacheProcessor.class, () -> caches);
                var mapperBean = new RootBeanDefinition(mapperType);
                mapperBean.setInstanceSupplier(() -> mapper);
                context.registerBeanDefinition("mapper", mapperBean);
                context.registerBeanDefinition("service", new RootBeanDefinition(serviceType));
                context.refresh();
                Object service = context.getBean("service"), writer = service;
                assertTrue(AopUtils.isAopProxy(writer));
                FactoryContext.bind(scope);
                var failure = assertThrows(InvocationTargetException.class, () -> loader.loadClass(root + ".service.ISyncProbeService")
                        .getMethod("sync", FactoryScope.class)
                        .invoke(service, scope));
                assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, ((FoundationException) failure.getCause()).code());
                verifyNoInteractions(coordinator);
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sync_probe", Integer.class));
                var upsert = loader.loadClass(root + ".service.ISyncProbeService")
                        .getMethod("upsert", FactoryScope.class, List.class);
                var ctor = inputType.getConstructors()[0];
                Object first = ctor.newInstance("S001", "first", "OLD", new BigDecimal("1.0"));
                Object result = upsert.invoke(writer, scope, List.of(first));
                assertEquals(1, result.getClass().getMethod("created").invoke(result));
                result = upsert.invoke(writer, scope, List.of(first));
                assertEquals(1, result.getClass().getMethod("skipped").invoke(result));
                Object changed = ctor.newInstance("S001", "changed", null, new BigDecimal("1.00"));
                result = upsert.invoke(writer, scope, List.of(changed));
                assertEquals(1, result.getClass().getMethod("updated").invoke(result));
                assertEquals(null, jdbc.queryForObject("SELECT payload FROM sync_probe WHERE sku='S001'", String.class));
                Object valid = ctor.newInstance("S002", "valid", null, null);
                Object bad = ctor.newInstance("S003", "DB_REJECT", null, null);
                assertThrows(InvocationTargetException.class, () -> upsert.invoke(writer, scope, List.of(valid, bad)));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sync_probe", Integer.class));
                assertThrows(InvocationTargetException.class, () -> upsert.invoke(writer, scope, List.of(first, first)));
                String prefix = caches.prefix("business:sync:probe");
                verify(redis, times(2)).deleteByPattern(prefix + "list:*");
                verify(redis, times(1)).deleteByPattern(prefix + "detail:*");
            }
        } finally {
            FactoryContext.clear();
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
