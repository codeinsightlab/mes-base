package dev.mesbase.platform.codegen;

import java.nio.file.Files;
import java.sql.Statement;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.cache.BusinessCacheAspect;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.BusinessCacheQuery;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class BusinessCodegenRunnerTest {
    @TempDir
    Path temporary;

    private Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Files.exists(current.resolve("config/codegen/codegen-config.review.yaml")) ? current : current.getParent();
    }

    private BusinessCodegenConfiguration config(String tables, String mode) throws Exception {
        Path root = repository();
        String yaml = Files.readString(root.resolve("mes-generator/src/test/resources/codegen/configuration.yaml"))
                .replace("tables: []", "tables: " + tables)
                .replace("backendRoot: \".\"", "backendRoot: \"" + temporary.toRealPath() + "/backend\"")
                .replace("frontendRoot: \"../mes-base-ui\"", "frontendRoot: \"" + temporary.toRealPath() + "/frontend\"")
                .replace("mode: \"dry-run\"", "mode: \"" + mode + "\"")
                .replace("formatJava: true", "formatJava: false")
                .replace("property: \"mes.system.enabled\"", "property: \"sample.feature.enabled\"")
                .replace("pageDefault: 20", "pageDefault: 7")
                .replace("fallback: \"business\"", "fallback: \"common\"")
                .replace("dev.mesbase.modules.{module}", "sample.generated.{module}");
        Path file = temporary.resolve("configuration.yaml");
        Files.writeString(file, yaml);
        return BusinessCodegenConfiguration.read(file);
    }

    private DriverManagerDataSource source() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(40))");
            statement.execute("CREATE TABLE pro_sample (id BIGINT PRIMARY KEY, name VARCHAR(40))");
            statement.execute("COMMENT ON TABLE person IS '人员'");
        }
        return source;
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true, order = BusinessCacheAspect.TRANSACTION_ORDER)
    static class CacheTransactions {
    }

    @Test
    void generatedBusinessCrudAndSyncActuallyUseCacheThroughSpringProxies() throws Exception {
        var data = source();
        var jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE material_category(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, code VARCHAR(40), name VARCHAR(40))");
        jdbc.execute("COMMENT ON TABLE material_category IS '缓存生成测试物料分类'");
        jdbc.execute("COMMENT ON COLUMN material_category.id IS '分类主键'");
        jdbc.execute("COMMENT ON COLUMN material_category.code IS '分类编码'");
        jdbc.execute("COMMENT ON COLUMN material_category.name IS '分类名称'");
        config("[material_category]", "write");
        Path file = temporary.resolve("configuration.yaml");
        Files.writeString(file, Files.readString(file) + "\ngenerateSync: true\nsync:\n  matchKey: code\n  batchMaximum: 1000\n");
        var report = new BusinessCodegenRunner().run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("GENERATED", report.status());
        Path backend = temporary.resolve("backend/mes-business");
        Path classes = temporary.resolve("cache-compiled");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null); var paths = Files.walk(backend.resolve("src/main/java"))) {
            assertTrue(compiler.getTask(null, manager, null,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString(), "-parameters"), null,
                    manager.getJavaFileObjectsFromPaths(paths.filter(path -> path.toString().endsWith(".java"))
                            .toList())).call());
        }
        try (var resources = Files.walk(backend.resolve("src/main/resources"))) {
            for (var resource : resources.filter(Files::isRegularFile).toList()) {
                var output = classes.resolve(backend.resolve("src/main/resources").relativize(resource));
                Files.createDirectories(output.getParent());
                Files.copy(resource, output);
            }
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader());
             var context = new AnnotationConfigApplicationContext()) {
            Thread.currentThread().setContextClassLoader(loader);
            context.setClassLoader(loader);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY", Map.of(
                    "mes.system.enabled", "true", "sample.feature.enabled", "true")));
            String root = "sample.generated.material.";
            var resource = loader.loadClass(root + "domain.MaterialCategoryResource");
            assertEquals("business:material:category", resource.getField("PREFIX").get(null));
            var mapperType = loader.loadClass(root + "dao.MaterialCategoryMapper");
            var manager = new OwnedTransactionManager(data);
            var sessions = PersistenceFactories.create(data, Set.of(mapperType), PersistenceFactories::pagination, manager::verifyAccess);
            Object mapper = new SqlSessionTemplate(sessions).getMapper(mapperType);
            var definition = new RootBeanDefinition(mapperType);
            definition.setInstanceSupplier(() -> mapper);
            context.registerBeanDefinition("mapper", definition);
            Map<String, Object> cached = new ConcurrentHashMap<>();
            IntSupplier cachedObjects = () -> (int) cached.keySet().stream()
                    .filter(key -> key.contains(":list:") || key.contains(":detail:")).count();
            var redis = mock(RedisCache.class);
            when(redis.getCacheObject(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
            doAnswer(call -> {
                cached.put(call.getArgument(0), call.getArgument(1));
                return null;
            })
                    .when(redis).setCacheObject(anyString(), any(), any(Integer.class), any(TimeUnit.class));
            doAnswer(call -> {
                String pattern = call.getArgument(0);
                cached.keySet().removeIf(key -> key.startsWith(pattern.substring(0, pattern.length() - 1)));
                return null;
            }).when(redis).deleteByPattern(anyString());
            doAnswer(call -> {
                cached.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(redis).setCacheObject(anyString(), any());
            doAnswer(call -> {
                String versionKey = call.getArgument(0);
                String expected = call.getArgument(1);
                Map<String, byte[]> values = call.getArgument(2);
                if (Objects.equals(expected, cached.get(versionKey))) cached.putAll(values);
                return null;
            }).when(redis).setCacheObjectsIfVersion(anyString(), any(), any(), any(Integer.class));
            var caches = new BusinessCacheProcessor(redis, new SpringCommitActions(), context.getEnvironment());
            context.register(CacheTransactions.class);
            context.registerBean("businessTransactionManager", OwnedTransactionManager.class, () -> manager);
            context.registerBean(BusinessCacheProcessor.class, () -> caches);
            context.registerBean(BusinessCacheAspect.class, () -> new BusinessCacheAspect(caches, manager));
            context.registerBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
            var serviceType = loader.loadClass(root + "service.impl.MaterialCategoryServiceImpl");
            var writerType = loader.loadClass(root + "service.impl.MaterialCategoryServiceImpl");
            context.registerBeanDefinition("service", new RootBeanDefinition(serviceType));
            context.registerBean(SyncCoordinator.class, () -> mock(SyncCoordinator.class));
            context.refresh();
            FactoryContext.bind(new FactoryScope(new FactoryId("TEST_ONLY")));
            Object service = context.getBean("service"), writer = service;
            var input = loader.loadClass(root + "sync.ExternalMaterialCategory");
            var upsert = writerType.getMethod("upsert", FactoryScope.class, List.class);
            Object first = input.getConstructors()[0].newInstance("C1", "分类一");
            upsert.invoke(writer, FactoryContext.required(), List.of(first));
            var detail = serviceType.getMethod("selectById", Long.class);
            var queryType = loader.loadClass(root + "domain.MaterialCategoryQueryDto");
            Object query = queryType.getConstructors()[0].newInstance(null, null, null, null);
            assertInstanceOf(BusinessCacheQuery.class, query);
            assertEquals(7, ((BusinessCacheQuery) query).cacheLimit());
            var list = serviceType.getMethod("selectPage", queryType);
            list.invoke(service, query);
            Object original = detail.invoke(service, 1L);
            jdbc.update("UPDATE material_category SET name=? WHERE id=?", "直接查询会看到此值", 1L);
            Object hit = detail.invoke(service, 1L);
            assertEquals(original.getClass(), hit.getClass());
            assertEquals("分类一", hit.getClass().getMethod("getName").invoke(hit));
            assertEquals(2, cachedObjects.getAsInt());
            Object pageHit = list.invoke(service, query);
            Object items = pageHit.getClass().getMethod("items").invoke(pageHit);
            assertEquals(original.getClass(), ((List<?>) items).getFirst().getClass());
            assertEquals("分类一", original.getClass().getMethod("getName").invoke(((List<?>) items).getFirst()));
            jdbc.update("UPDATE material_category SET name=? WHERE id=?", "分类一", 1L);
            upsert.invoke(writer, FactoryContext.required(), List.of(input.getConstructors()[0].newInstance("C2", "分类二")));
            assertEquals(1, cachedObjects.getAsInt(), "Only-created sync evicts list and retains details");
            list.invoke(service, query);
            upsert.invoke(writer, FactoryContext.required(), List.of(first));
            assertEquals(2, cachedObjects.getAsInt(), "All-skipped sync retains both caches");
            new TransactionTemplate(manager).execute(status -> {
                try {
                    upsert.invoke(writer, FactoryContext.required(), List.of(input.getConstructors()[0].newInstance("C1", "回滚分类")));
                    assertEquals(2, cachedObjects.getAsInt(), "Do not evict before commit");
                    status.setRollbackOnly();
                    return null;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            });
            assertEquals(2, cachedObjects.getAsInt(), "Rollback must retain caches");
            assertEquals("分类一", jdbc.queryForObject("SELECT name FROM material_category WHERE id=1", String.class));
            upsert.invoke(writer, FactoryContext.required(), List.of(input.getConstructors()[0].newInstance("C1", "更新分类")));
            assertTrue(cachedObjects.getAsInt() == 0, "Updated sync evicts all lists and details after commit");
            assertEquals("更新分类", detail.invoke(service, 1L).getClass().getMethod("getName")
                    .invoke(detail.invoke(service, 1L)));
            list.invoke(service, query);
            var create = loader.loadClass(root + "domain.MaterialCategoryCreateCommand");
            var update = loader.loadClass(root + "domain.MaterialCategoryUpdateCommand");
            serviceType.getMethod("insert", create)
                    .invoke(service, create.getConstructors()[0].newInstance("C3", "手工新增"));
            assertEquals(1, cachedObjects.getAsInt(), "Generated create retains details");
            serviceType.getMethod("updateById", Long.class, update).invoke(service, 1L,
                    update.getConstructors()[0].newInstance("C1", "手工修改"));
            assertTrue(cachedObjects.getAsInt() == 0, "Generated update evicts both types");
            list.invoke(service, query);
            detail.invoke(service, 1L);
            serviceType.getMethod("deleteByIds", List.class).invoke(service, List.of(1L));
            assertTrue(cachedObjects.getAsInt() == 0, "Generated delete evicts both types");
        } finally {
            FactoryContext.clear();
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void configuredTemplateRootPreservesOrdinaryCrudColumnSettings() throws Exception {
        var configured = config("[person]", "write");
        Path templates = temporary.resolve("templates");
        Path original = repository().resolve(configured.templates().root());
        try (var files = Files.walk(original)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path output = templates.resolve(original.relativize(source));
                Files.createDirectories(output.getParent());
                Files.copy(source, output);
            }
        }
        Path customPage = templates.resolve("index.vue.vm");
        Files.writeString(customPage, Files.readString(original.resolve("index.vue.vm")) + "\n<!-- TEST ONLY configured template root -->\n");
        Path file = temporary.resolve("configuration.yaml");
        String yaml = Files.readString(file)
                .replace("root: \"mes-generator/src/main/resources/codegen/base\"", "root: \"" + templates + "\"");
        Files.writeString(file, yaml);
        var runner = new BusinessCodegenRunner();
        var report = runner.run(BusinessCodegenConfiguration.read(file), repository(), source());
        assertEquals("GENERATED", report.status());
        String page = Files.readString(temporary.resolve("frontend/src/views/common/person/index.vue"));
        assertTrue(page.contains("configured template root"));
        assertTrue(page.contains("table-key=\"business:person:list\""));
        assertFalse(page.contains("SyncButton"));
        Files.writeString(file, yaml + "\ntableOverrides:\n  person:\n    module: common\n    displayName: 人员\n    frontendTemplate: removed.vue.vm\n");
        assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(file));
        assertEquals(page, Files.readString(temporary.resolve("frontend/src/views/common/person/index.vue")));
    }

    @Test
    void allTablesPreviewAndExplicitTableWriteUseConfiguration() throws Exception {
        var source = source();
        var all = new BusinessCodegenRunner().run(config("[]", "dry-run"), repository(), source);
        assertEquals("DRY_RUN", all.status());
        assertEquals(2, all.tables().size());
        assertFalse(Files.exists(temporary.resolve("backend")));
        var one = new BusinessCodegenRunner().run(config("[person]", "write"), repository(), source);
        assertEquals("GENERATED", one.status());
        assertEquals(1, one.tables().size());
        assertEquals("common", one.tables().getFirst().module());
        Path backend = temporary.resolve("backend/mes-business");
        String controller = Files.readString(backend.resolve("src/main/java/sample/generated/common/controller/PersonController.java"));
        assertTrue(controller.contains("sample.feature.enabled"));
        String service = Files.readString(backend.resolve("src/main/java/sample/generated/common/service/impl/PersonServiceImpl.java"));
        assertTrue(service.contains("? 7 :"));
        assertTrue(Files.readString(temporary.resolve("frontend/src/views/common/person/index.vue"))
                .contains("table-key=\"business:person:list\""));
        Path classes = temporary.resolve("compiled");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null);
             var paths = Files.walk(backend.resolve("src/main/java"))) {
            var units = manager.getJavaFileObjectsFromPaths(paths.filter(path -> path.toString().endsWith(".java"))
                    .toList());
            assertTrue(compiler.getTask(null, manager, null,
                            List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()), null, units)
                    .call());
        }
    }

    @Test
    void selectedArtifactsAndExplicitNullConfigurationAreHonored() throws Exception {
        config("[person]", "write");
        Path file = temporary.resolve("configuration.yaml");
        String yaml = Files.readString(file).replace("artifacts: [all]", "artifacts: [frontend]");
        Files.writeString(file, yaml);
        var configuration = BusinessCodegenConfiguration.read(file);
        var report = new BusinessCodegenRunner().run(configuration, repository(), source());
        assertEquals("GENERATED", report.status());
        assertEquals(2, report.files().size());
        assertTrue(report.files().stream().allMatch(artifact -> artifact.target().name().equals("FRONTEND")));
        Files.writeString(file, yaml.replace("  schema: null\n", ""));
        assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(file));
    }

    @Test
    void rejectedTablePreventsBatchPublication() throws Exception {
        var source = source();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unsupported (left_id BIGINT, right_id BIGINT, PRIMARY KEY(left_id,right_id))");
        }
        var report = new BusinessCodegenRunner().run(config("[]", "write"), repository(), source);
        assertEquals("FAILED_PREFLIGHT", report.status());
        assertEquals(1, report.rejected().size());
        assertFalse(Files.exists(temporary.resolve("backend")));
    }

    @Test
    void missingRequiredConfigurationIsRejected() throws Exception {
        Path file = temporary.resolve("missing.yaml");
        Files.writeString(file, "version: 1\n");
        assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(file));
    }

    @Test
    void menuSqlAddsUpdatesAndPreservesIds() throws Exception {
        var source = source();
        new BusinessCodegenRunner().run(config("[person]", "write"), repository(), source);
        String sql = Files.readString(temporary.resolve("backend/mes-business/docs/generated/common/person-menu.sql"));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_menu(menu_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, menu_name VARCHAR(100), parent_id BIGINT, order_num INT,path VARCHAR(200),component VARCHAR(200),menu_type VARCHAR(1),perms VARCHAR(200),icon VARCHAR(100),create_by VARCHAR(100),create_time TIMESTAMP)");
            statement.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            long pageId;
            try (var rows = statement.executeQuery("SELECT menu_id FROM sys_menu WHERE menu_type='C'")) {
                assertTrue(rows.next());
                pageId = rows.getLong(1);
            }
            statement.execute("INSERT INTO sys_role_menu VALUES(7," + pageId + ")");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.replace("人员", "人员新名称")
                    .getBytes(StandardCharsets.UTF_8)));
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT m.menu_name FROM sys_menu m JOIN sys_role_menu r ON m.menu_id=r.menu_id WHERE r.role_id=7")) {
                assertTrue(rows.next());
                assertEquals("人员新名称", rows.getString(1));
            }
        }
    }

    @Test
    void historicalConfigSyncTogglePreviewAndWriteShareTheSameOutput() throws Exception {
        var data = source();
        var old = config("[person]", "write");
        assertFalse(old.generateSync());
        var runner = new BusinessCodegenRunner();
        var ordinary = runner.run(old, repository(), data);
        assertEquals("GENERATED", ordinary.status());
        assertTrue(ordinary.files().stream().noneMatch(file -> file.path().contains("Sync")));
        Path file = temporary.resolve("configuration.yaml");
        String base = Files.readString(file).replace("overwrite: \"fail\"", "overwrite: \"replace-generated\"");
        Files.writeString(file, base + "\ngenerateSync: true\nsync:\n  matchKey: name\n  batchMaximum: 1000\n");
        var enabled = BusinessCodegenConfiguration.read(file);
        Files.writeString(file, Files.readString(file).replace("mode: \"write\"", "mode: \"dry-run\""));
        var preview = runner.run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("DRY_RUN", preview.status(), preview.rejected().toString());
        assertEquals(4, preview.files().stream().filter(artifact -> artifact.path().contains("/sync/")
                || artifact.path().endsWith("SyncController.java")).count());
        var generated = runner.run(enabled, repository(), data);
        assertEquals("GENERATED", generated.status());
        assertEquals(preview.files(), generated.files());
        Path backend = temporary.resolve("backend/mes-business");
        String page = Files.readString(temporary.resolve("frontend/src/views/common/person/index.vue"));
        assertTrue(page.contains("<SyncButton"));
        assertTrue(page.contains("business:person:sync"));
        assertTrue(Files.readString(backend.resolve("docs/generated/common/person-menu.sql"))
                .contains("business:person:sync"));
        Path classes = temporary.resolve("sync-compiled");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null); var paths = Files.walk(backend.resolve("src/main/java"))) {
            assertTrue(compiler.getTask(null, manager, null,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()), null,
                    manager.getJavaFileObjectsFromPaths(paths.filter(path -> path.toString().endsWith(".java"))
                            .toList())).call());
        }
        Files.writeString(file, base + "\ngenerateSync: false\n");
        var disabled = runner.run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("GENERATED", disabled.status());
        assertTrue(disabled.files().stream().noneMatch(artifact -> artifact.path().contains("/sync/") || artifact.path()
                .endsWith("SyncController.java")));
        assertFalse(Files.readString(temporary.resolve("frontend/src/views/common/person/index.vue"))
                .contains("SyncButton"));
        assertFalse(Files.readString(temporary.resolve("frontend/src/api/common/person.ts")).contains("/actions/sync"));
        assertFalse(Files.readString(backend.resolve("src/main/java/sample/generated/common/controller/PersonPermissions.java"))
                .contains("SYNC"));
        // Disabling sync also removes its methods from the ordinary Service.
        assertFalse(Files.exists(backend.resolve("src/main/java/sample/generated/common/sync/PersonSyncService.java")));
        assertFalse(Files.readString(backend.resolve("src/main/java/sample/generated/common/service/impl/PersonServiceImpl.java"))
                .contains("self.getObject().upsert"));
    }

    @Test
    void unknownMatchKeyRejectsPublicationAndTechnicalTablesCanBeExcluded() throws Exception {
        var data = source();
        config("[]", "write");
        Path file = temporary.resolve("configuration.yaml");
        String base = Files.readString(file);
        Files.writeString(file, base + "\ngenerateSync: true\nsync:\n  matchKey: missing_column\n  batchMaximum: 1000\n");
        var report = new BusinessCodegenRunner().run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("FAILED_PREFLIGHT", report.status());
        assertTrue(report.rejected().stream().allMatch(table -> table.errorCode().equals("INVALID_SYNC_KEY")));
        assertFalse(Files.exists(temporary.resolve("backend")));
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE flyway_business_schema_history(installed_rank INT PRIMARY KEY, description VARCHAR(50))");
        }
        Files.writeString(file, base.replace("excludeTables: []", "excludeTables: [flyway_business_schema_history]"));
        report = new BusinessCodegenRunner().run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("GENERATED", report.status());
        assertEquals(2, report.tables().size());
        assertTrue(report.tables().stream()
                .noneMatch(table -> table.table().equalsIgnoreCase("flyway_business_schema_history")));
    }


    @Test
    void explicitScopeColumnIsServerOwnedInCommandsCrudAndSync() throws Exception {
        var data = source();
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE person ADD factory_id VARCHAR(64) NOT NULL DEFAULT 'TEST_ONLY'");
        }
        config("[person]", "write");
        Path file = temporary.resolve("configuration.yaml");
        String base = Files.readString(file)
                .replace("actions: [read, create, update, delete]", "actions: [list, query, create, update, delete]") + "\ntableOverrides:\n  person:\n    module: common\n    displayName: 人员\n    serverScopeColumn: factory_id\n";
        Files.writeString(file, base + "\ngenerateSync: true\nsync:\n  matchKey: name\n  batchMaximum: 1000\n");
        var report = new BusinessCodegenRunner().run(BusinessCodegenConfiguration.read(file), repository(), data);
        assertEquals("GENERATED", report.status());
        Path javaRoot = temporary.resolve("backend/mes-business/src/main/java/sample/generated/common");
        assertFalse(Files.readString(javaRoot.resolve("domain/PersonCreateCommand.java")).contains("factoryId"));
        assertFalse(Files.readString(javaRoot.resolve("sync/ExternalPerson.java")).contains("factoryId"));
        String controller = Files.readString(javaRoot.resolve("controller/PersonController.java"));
        assertTrue(controller.contains("PersonPermissions.LIST"));
        assertTrue(controller.contains("PersonPermissions.QUERY"));
        String service = Files.readString(javaRoot.resolve("service/impl/PersonServiceImpl.java"));
        assertTrue(service.contains("andFactoryIdEqualTo"));
        assertTrue(service.contains("row.setFactoryId(FactoryContext.required().factoryId().value())"));
        assertTrue(Files.readString(javaRoot.resolve("service/impl/PersonServiceImpl.java"))
                .contains("andFactoryIdEqualTo(scope.factoryId().value())"));
    }

    @Test
    void regenerationRemovesOnlyMarkedLegacySyncClassesAndPreviewDoesNotDelete() throws Exception {
        var data = source();
        var configuration = config("[person]", "write");
        var runner = new BusinessCodegenRunner();
        assertEquals("GENERATED", runner.run(configuration, repository(), data).status());
        Path legacy = temporary.resolve("backend/mes-business/src/main/java/sample/generated/common/sync/PersonSyncService.java");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "// MES-CODEGEN: obsolete\nclass PersonSyncService {}\n");
        Path yaml = temporary.resolve("configuration.yaml");
        String writing = Files.readString(yaml).replace("overwrite: \"fail\"", "overwrite: \"replace-generated\"");
        Files.writeString(yaml, writing.replace("mode: \"write\"", "mode: \"dry-run\""));
        var preview = runner.run(BusinessCodegenConfiguration.read(yaml), repository(), data);
        assertEquals("DRY_RUN", preview.status(), preview.rejected().toString());
        assertTrue(Files.exists(legacy));
        assertTrue(preview.files().stream()
                .anyMatch(file -> file.path().endsWith("/sync/PersonSyncService.java") && file.removed()));
        Files.writeString(yaml, writing);
        assertEquals("GENERATED", runner.run(BusinessCodegenConfiguration.read(yaml), repository(), data).status());
        assertFalse(Files.exists(legacy));
        Files.writeString(legacy, "// user-owned class\nclass PersonSyncService {}\n");
        assertEquals("FAILED_PREFLIGHT", runner.run(BusinessCodegenConfiguration.read(yaml), repository(), data)
                .status());
        assertTrue(Files.readString(legacy).contains("user-owned"));
    }

    private BusinessCodegenConfiguration documentConfig(String mode, String execution) throws Exception {
        config("[person, pro_sample]", execution);
        Path yaml = temporary.resolve("configuration.yaml");
        String documents = "\n  documents:\n    mode: \"" + mode
                + "\"\n    sql: \"docs/generated/all-menu.sql\"\n"
                + "    markdown: \"docs/generated/all-permissions.md\"\n";
        Files.writeString(yaml, Files.readString(yaml).replace("\ntemplates:", documents + "\ntemplates:")
                .replace("artifacts: [all]", "artifacts: [menu]"));
        return BusinessCodegenConfiguration.read(yaml);
    }

    @Test
    void originalAndExplicitPerTableDocumentModesKeepSeparateFiles() throws Exception {
        var runner = new BusinessCodegenRunner();
        var original = runner.run(config("[person, pro_sample]", "dry-run"), repository(), source());
        assertEquals("DRY_RUN", original.status());
        assertEquals(4, original.files().stream().filter(file -> file.target() == CodegenResult.Target.PERMISSION)
                .count());
        var explicit = runner.run(documentConfig("per-table", "dry-run"), repository(), source());
        assertEquals("DRY_RUN", explicit.status());
        assertEquals(4, explicit.files().stream().filter(file -> file.target() == CodegenResult.Target.PERMISSION)
                .count());
        assertTrue(explicit.files().stream().noneMatch(file -> file.path().endsWith("all-menu.sql")));
    }

    @Test
    void mergedDocumentsPreviewMatchesWriteAndSqlExecutesIdempotently() throws Exception {
        var runner = new BusinessCodegenRunner();
        var data = source();
        var preview = runner.run(documentConfig("merged", "dry-run"), repository(), data);
        assertEquals("DRY_RUN", preview.status(), preview.rejected().toString());
        assertFalse(Files.exists(temporary.resolve("backend")));
        var generated = runner.run(documentConfig("merged", "write"), repository(), data);
        assertEquals("GENERATED", generated.status(), generated.rejected().toString());
        assertEquals(preview.files(), generated.files());
        assertEquals(2, generated.files().stream().filter(file -> file.target() == CodegenResult.Target.PERMISSION)
                .count());
        assertEquals(2, generated.files().stream().filter(file -> file.target() == CodegenResult.Target.FRONTEND)
                .count());
        Path root = temporary.resolve("backend/mes-business/docs/generated");
        String sql = Files.readString(root.resolve("all-menu.sql"));
        String markdown = Files.readString(root.resolve("all-permissions.md"));
        assertTrue(sql.contains("business:person:read"));
        assertTrue(sql.contains("business:pro:sample:read"));
        assertTrue(markdown.contains("/api/common/person"));
        assertTrue(markdown.contains("/api/pro/pro-sample"));
        assertFalse(Files.exists(root.resolve("common/person-menu.sql")));
        assertFalse(Files.exists(root.resolve("pro/proSample-permissions.md")));
        try (var connection = data.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_menu(menu_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, menu_name VARCHAR(100), parent_id BIGINT, order_num INT,path VARCHAR(200),component VARCHAR(200),menu_type VARCHAR(1),perms VARCHAR(200),icon VARCHAR(100),create_by VARCHAR(100),create_time TIMESTAMP)");
            for (int run = 0; run < 2; run++) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_type='C'")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                    assertTrue(rows.next());
                    assertEquals(12, rows.getInt(1));
                }
            }
        }
    }

    @Test
    void mergedDocumentConflictsRejectTheWholeBatchAndBadConfigurationIsExplicit() throws Exception {
        var configured = documentConfig("merged", "write");
        Path conflict = temporary.toRealPath().resolve("backend/mes-business/docs/generated/all-menu.sql");
        Files.createDirectories(conflict.getParent());
        Files.writeString(conflict, "-- user-owned file");
        var result = new BusinessCodegenRunner().run(configured, repository(), source());
        assertEquals("FAILED_PREFLIGHT", result.status());
        assertFalse(Files.exists(conflict.resolveSibling("all-permissions.md")));
        assertFalse(Files.exists(temporary.resolve("frontend")));
        assertEquals("-- user-owned file", Files.readString(conflict));
        Path yaml = temporary.resolve("configuration.yaml");
        String valid = Files.readString(yaml);
        for (String invalid : List.of(valid.replace("mode: \"merged\"", "mode: \"invalid\""),
                valid.replace("docs/generated/all-menu.sql", "../outside.sql"),
                valid.replace("docs/generated/all-permissions.md", "docs/generated/all.sql"),
                valid.replace("docs/generated/all-menu.sql", "docs/generated/{module}.sql"))) {
            Files.writeString(yaml, invalid);
            assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(yaml));
        }
    }

    private String sharedDirectorySql(String mode) throws Exception {
        documentConfig(mode, "write");
        Path yaml = temporary.resolve("configuration.yaml");
        Files.writeString(yaml, Files.readString(yaml).replace("directoryName: \"{module}\"",
                "directoryName: \"基础数据\"\n  directoryPath: \"basic\""));
        var report = new BusinessCodegenRunner().run(BusinessCodegenConfiguration.read(yaml), repository(), source());
        assertEquals("GENERATED", report.status(), report.rejected().toString());
        StringBuilder sql = new StringBuilder();
        for (var file : report.files()) {
            if (file.path().endsWith(".sql")) sql.append(Files.readString(Path.of(file.path()))).append('\n');
            if (file.path().endsWith(".json")) {
                String json = Files.readString(Path.of(file.path()));
                assertTrue(json.contains("\"path\": \"/basic/"));
                assertTrue(json.contains("common/person/index") || json.contains("pro/proSample/index"));
            }
        }
        return sql.toString();
    }

    private void menuSchema(Statement statement) throws Exception {
        statement.execute("CREATE TABLE sys_menu("
                + "menu_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY COMMENT '菜单编号',"
                + "menu_name VARCHAR(100) COMMENT '菜单名称', parent_id BIGINT COMMENT '父菜单编号',"
                + "order_num INT COMMENT '显示顺序', path VARCHAR(200) COMMENT '路由路径',"
                + "component VARCHAR(200) COMMENT '组件路径', menu_type VARCHAR(1) COMMENT '菜单类型',"
                + "perms VARCHAR(200) COMMENT '权限标识', icon VARCHAR(100) COMMENT '菜单图标',"
                + "create_by VARCHAR(100) COMMENT '创建人', create_time TIMESTAMP COMMENT '创建时间'"
                + ")");
        statement.execute("COMMENT ON TABLE sys_menu IS '测试菜单'");
        statement.execute("CREATE TABLE sys_role_menu(role_id BIGINT COMMENT '角色编号',"
                + "menu_id BIGINT COMMENT '菜单编号')");
        statement.execute("COMMENT ON TABLE sys_role_menu IS '测试角色菜单绑定'");
    }

    @Test
    void sharedDirectoryCreatesOneParentAcrossModulesInBothDocumentModes() throws Exception {
        for (String mode : List.of("per-table", "merged")) {
            String sql = sharedDirectorySql(mode);
            try (var connection = source().getConnection(); var statement = connection.createStatement()) {
                menuSchema(statement);
                for (int run = 0; run < 2; run++) {
                    ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_type='M' AND menu_name='基础数据' AND path='basic'")) {
                        assertTrue(rows.next());
                        assertEquals(1, rows.getInt(1));
                    }
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu page JOIN sys_menu directory ON page.parent_id=directory.menu_id WHERE page.menu_type='C' AND directory.path='basic'")) {
                        assertTrue(rows.next());
                        assertEquals(2, rows.getInt(1));
                    }
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                        assertTrue(rows.next());
                        assertEquals(11, rows.getInt(1));
                    }
                }
            }
        }
    }

    @Test
    void sharedDirectoryMovesExistingPagesWithoutChangingIdsOrRoleBindings() throws Exception {
        String sql = sharedDirectorySql("merged");
        try (var connection = source().getConnection(); var statement = connection.createStatement()) {
            menuSchema(statement);
            statement.execute("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type) VALUES(100,'基础数据',0,'common','M'),(200,'基础数据',0,'pro','M')");
            statement.execute("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,perms) VALUES(300,'人员',100,'person','C','business:person:read'),(400,'样例',200,'proSample','C','business:pro:sample:read')");
            statement.execute("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(7,300),(8,400)");
            for (int run = 0; run < 2; run++) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu page JOIN sys_menu directory ON page.parent_id=directory.menu_id JOIN sys_role_menu binding ON binding.menu_id=page.menu_id WHERE page.menu_id IN (300,400) AND directory.path='basic'")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_role_menu")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (100,200)")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }

}
