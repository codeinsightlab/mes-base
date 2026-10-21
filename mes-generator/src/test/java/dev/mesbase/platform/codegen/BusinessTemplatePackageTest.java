package dev.mesbase.platform.codegen;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.platform.codegen.CodegenRequest.OverwritePolicy;
import dev.mesbase.platform.codegen.CodegenRequest.ScopeKind;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import dev.mesbase.platform.codegen.TableMetadata.Column;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessTemplatePackageTest {
    @TempDir
    Path temporary;

    private Path templates() {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.exists(root.resolve("mes-admin"))) root = root.getParent();
        return root.resolve("mes-generator/src/main/resources/codegen/base");
    }

    private CodegenRequest request(String table, boolean sync) throws Exception {
        Path root = temporary.toRealPath();
        return new CodegenRequest("TEST_ONLY", null, table, "material", "测试", "sample.business.material", "TEST_ONLY",
                ScopeKind.FACTORY_SCOPED, OverwritePolicy.FAIL, false, root.resolve("backend"),
                root.resolve("frontend"), "mes-business", sync, sync ? "code" : null);
    }

    private TableMetadata metadata(String table) {
        return new TableMetadata(null, null, table, "测试", List.of(
                new Column("id", "id", Types.BIGINT, "Long", 19, 0, false, true, true, null, "编号"),
                new Column("code", "code", Types.VARCHAR, "String", 64, 0, false, false, false, null, "编码")),
                List.of(), List.of());
    }

    private CodegenTemplateOptions options(Path root, Map<String, Object> overrides) {
        return new CodegenTemplateOptions(root, overrides, Map.of(
                "domain", "model", "dao", "mapper", "service", "usecase", "service/impl", "usecase.impl",
                "controller", "http", "controller/vo", "http.result", "sync", "source"), List.of(), 10000,
                "src/api/test.ts", "src/views/test.vue", "src/menu/test.json", "docs/test.sql");
    }

    @Test
    void generatedFrontendDelegatesNativeQuerySubmitToTheSharedCrudPage() throws Exception {
        for (boolean sync : List.of(false, true)) {
            var artifacts = new BusinessTemplateRenderer(options(templates(), Map.of()))
                    .render(request("material", sync), metadata("material"));
            String page = artifacts.stream().filter(a -> a.relativePath().endsWith(".vue"))
                    .findFirst().orElseThrow().content();
            assertTrue(page.contains("<CrudPage"));
            assertTrue(page.contains(":fields=\"fields\""));
            assertTrue(page.contains("CrudPage / QueryForm"));
            assertFalse(page.contains("@keyup.enter"));
            assertFalse(page.contains("@keydown.enter"));
            assertFalse(page.contains("queryEnter"));
            assertFalse(page.contains("<el-form"));
        }
    }

    @Test
    void genericTemplatesFollowPackageConfigurationAndProduceCompilableSources() throws Exception {
        for (String table : List.of("material_category", "unit_measure", "workshop", "workstation", "material", "safety_stock")) {
            for (boolean sync : List.of(false, true)) {
                String name = JdbcMetadataReader.camel(table);
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                var artifacts = new BusinessTemplateRenderer(options(templates(), Map.of()))
                        .render(request(table, sync), metadata(table));
                Path sourceRoot = temporary.resolve("sources/" + table + sync);
                for (Artifact artifact : artifacts) {
                    if (artifact.removed() || !artifact.relativePath().endsWith(".java")) continue;
                    String path = artifact.relativePath();
                    String owner = path.substring(path.indexOf("/java/") + 6, path.lastIndexOf('/')).replace('/', '.');
                    assertTrue(artifact.content().contains("package " + owner + ";"), path);
                    assertFalse(artifact.content().contains("dev.mesbase.modules.material"), path);
                    assertFalse(artifact.content().contains("BasicDataRules"), path);
                    if (path.startsWith("src/main/java/")) {
                        Path file = sourceRoot.resolve(path);
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, artifact.content());
                    }
                }
                String controllerName = name + "Controller.java";
                String controller = artifacts.stream().filter(a -> a.relativePath().endsWith(controllerName))
                        .findFirst().orElseThrow().content();
                assertTrue(controller.contains("import sample.business.material.http.result." + name + "PageVo;"));
                assertTrue(controller.contains("import sample.business.material.model." + name + "Entity;"));
                assertTrue(controller.contains("import sample.business.material.usecase.I" + name + "Service;"));
                assertTrue(controller.contains("import dev.mesbase.platform.web.EndpointAccess;"));
                String serviceName = name + "ServiceImpl.java";
                String service = artifacts.stream().filter(a -> a.relativePath().endsWith(serviceName))
                        .findFirst().orElseThrow().content();
                assertTrue(service.contains("import sample.business.material.mapper." + name + "Mapper;"));
                if (sync) assertTrue(service.contains("import sample.business.material.source.External" + name + ";"));
                Path classes = temporary.resolve("classes/" + table + sync);
                Files.createDirectories(classes);
                var compiler = ToolProvider.getSystemJavaCompiler();
                try (var manager = compiler.getStandardFileManager(null, null, null);
                     var files = Files.walk(sourceRoot)) {
                    assertTrue(compiler.getTask(null, manager, null,
                                    List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()), null,
                                    manager.getJavaFileObjectsFromPaths(files.filter(Files::isRegularFile).toList())).call(),
                            table + " sync=" + sync);
                }
                if (!sync) verifyPageLifecycle(classes, name);
            }
        }
    }

    /**
     * TEST ONLY: bypass MyBatis so only the generated resource boundary can clear the page.
     */
    private void verifyPageLifecycle(Path classes, String name) throws Exception {
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            String root = "sample.business.material.";
            Class<?> mapper = loader.loadClass(root + "mapper." + name + "Mapper");
            Class<?> service = loader.loadClass(root + "usecase.impl." + name + "ServiceImpl");
            Class<?> query = loader.loadClass(root + "model." + name + "QueryDto");
            Object input = query.getConstructors()[0].newInstance(0, 20, null);
            assertEquals("id desc", query.getMethod("cacheSort").invoke(input));
            var selectPage = service.getMethod("selectPage", query);
            var failure = new IllegalStateException("TEST ONLY failure before MyBatis interceptor");
            for (boolean fail : List.of(false, true)) {
                Object stub = Proxy.newProxyInstance(loader, new Class<?>[]{mapper}, (proxy, method, args) -> {
                    assertEquals("selectByExample", method.getName());
                    assertEquals("id desc", args[0].getClass().getMethod("getOrderByClause").invoke(args[0]));
                    Page<?> page = PageHelper.getLocalPage();
                    assertEquals(20, page.getPageSize());
                    if (fail) throw failure;
                    page.setTotal(0);
                    return List.of();
                });
                Object instance = service.getConstructor(mapper).newInstance(stub);
                if (fail) {
                    assertSame(failure, assertThrows(InvocationTargetException.class,
                            () -> selectPage.invoke(instance, input)).getCause());
                } else {
                    Object result = selectPage.invoke(instance, input);
                    assertEquals(0L, result.getClass().getMethod("total").invoke(result));
                }
                assertNull(PageHelper.getLocalPage());
            }
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    void syncComparisonCompilesAndPreservesEqualityForEmptySingleAndDecimalFields() throws Exception {
        Column id = metadata("person").columns().getFirst();
        Column code = metadata("person").columns().get(1);
        Column audit = new Column("create_by", "createBy", Types.VARCHAR, "String", 64, 0, true,
                false, false, null, "创建人");
        Column amount = new Column("amount", "amount", Types.DECIMAL, "java.math.BigDecimal", 18, 2, true,
                false, false, null, "数量");
        Column name = new Column("name", "name", Types.VARCHAR, "String", 64, 0, true,
                false, false, null, "名称");
        var variants = List.of(List.of(id, audit), List.of(id, code), List.of(id, amount, code, name),
                List.of(id, code, name, amount));
        for (int variant = 0; variant < variants.size(); variant++) {
            var base = request("person", true);
            var configured = new CodegenRequest(base.datasource(), base.schema(), base.tableName(), base.moduleName(),
                    base.businessName(), base.packageName(), base.author(), base.scope(), base.overwritePolicy(),
                    base.dryRun(), base.backendRoot(), base.frontendRoot(), base.backendModule(), true, "id");
            var options = options(templates(), Map.of());
            options = new CodegenTemplateOptions(options.templateRoot(), options.variables(), options.packages(),
                    List.of("create_by"), options.stringLengthLimit(), options.frontendApi(), options.frontendPage(),
                    options.frontendMenu(), options.menuSql());
            var table = new TableMetadata(null, null, "person", "比较测试", variants.get(variant), List.of(), List.of());
            var artifacts = new BusinessTemplateRenderer(options).render(configured, table);
            String service = artifacts.stream()
                    .filter(artifact -> artifact.relativePath().endsWith("PersonServiceImpl.java"))
                    .findFirst().orElseThrow().content();
            String same = service.substring(service.indexOf("private static boolean same("));
            assertEquals(variant == 0, same.contains("return true;"));
            assertFalse(same.contains("return true\n"));
            Path sources = temporary.resolve("comparison/" + variant + "/sources");
            Path classes = temporary.resolve("comparison/" + variant + "/classes");
            Files.createDirectories(classes);
            for (Artifact artifact : artifacts) {
                if (artifact.removed() || !artifact.relativePath().startsWith("src/main/java/")
                        || !artifact.relativePath().endsWith(".java")) continue;
                Path file = sources.resolve(artifact.relativePath());
                Files.createDirectories(file.getParent());
                Files.writeString(file, artifact.content());
            }
            var compiler = ToolProvider.getSystemJavaCompiler();
            try (var manager = compiler.getStandardFileManager(null, null, null); var files = Files.walk(sources)) {
                assertTrue(compiler.getTask(null, manager, null,
                        List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()), null,
                        manager.getJavaFileObjectsFromPaths(files.filter(Files::isRegularFile).toList())).call());
            }
            try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
                Class<?> entity = loader.loadClass("sample.business.material.model.PersonEntity");
                Class<?> external = loader.loadClass("sample.business.material.source.ExternalPerson");
                Class<?> implementation = loader.loadClass("sample.business.material.usecase.impl.PersonServiceImpl");
                var components = external.getRecordComponents();
                List<Object> arguments = new ArrayList<>();
                var row = entity.getConstructor().newInstance();
                for (var component : components) {
                    Object value = component.getType() == Long.class ? 1L : component.getType() == BigDecimal.class
                            ? new BigDecimal("1.00") : "TEST";
                    arguments.add(value);
                    Object rowValue = value instanceof BigDecimal ? new BigDecimal("1.0") : value;
                    String setter = "set" + Character.toUpperCase(component.getName().charAt(0)) + component.getName()
                            .substring(1);
                    entity.getMethod(setter, component.getType()).invoke(row, rowValue);
                }
                var input = external.getDeclaredConstructors()[0].newInstance(arguments.toArray());
                var comparison = implementation.getDeclaredMethod("same", entity, external);
                comparison.setAccessible(true);
                assertEquals(true, comparison.invoke(null, row, input));
                if (variant != 0) {
                    Column first = variants.get(variant).get(1);
                    String setter = "set" + Character.toUpperCase(first.javaField().charAt(0)) + first.javaField()
                            .substring(1);
                    entity.getMethod(setter, first == amount ? BigDecimal.class : String.class)
                            .invoke(row, new Object[]{null});
                    assertEquals(false, comparison.invoke(null, row, input));
                }
            }
        }
    }

    @Test
    void pageImportsActualConfiguredApiFileForBothExtensionsAndIgnoresStaleVariable() throws Exception {
        for (String extension : List.of("ts", "js")) {
            var base = options(templates(), Map.of("frontendApiImport", "@/api/stale", "route", "/api/custom/person"));
            var configured = new CodegenTemplateOptions(base.templateRoot(), base.variables(), base.packages(),
                    base.auditFields(), base.stringLengthLimit(), "src/api/custom/person." + extension,
                    "src/views/custom/person.vue", base.frontendMenu(), base.menuSql());
            var artifacts = new BusinessTemplateRenderer(configured).render(request("person", true), metadata("person"));
            String page = artifacts.stream().filter(artifact -> artifact.relativePath().endsWith(".vue"))
                    .findFirst().orElseThrow().content();
            String api = artifacts.stream().filter(artifact -> artifact.relativePath().equals(configured.frontendApi()))
                    .findFirst().orElseThrow().content();
            assertTrue(page.contains("import { api } from '@/api/custom/person'"));
            assertTrue(page.contains("import { sync } from '@/api/custom/person'"));
            assertFalse(page.contains("@/api/stale"));
            assertEquals(extension.equals("ts"), api.contains("export interface PersonVo"));
            if (extension.equals("js")) {
                assertFalse(api.contains("request<"));
                assertFalse(api.contains(": string"));
            }
            String controller = artifacts.stream()
                    .filter(artifact -> artifact.relativePath().endsWith("PersonController.java"))
                    .findFirst().orElseThrow().content();
            assertTrue(controller.contains("@RequestMapping(\"/api/custom/person\")"));
            assertTrue(api.contains("'/api/custom/person'"));
            assertTrue(api.contains("'/api/custom/person/' + encodeURIComponent(id)"));
            assertTrue(controller.contains("@GetMapping(\"/{id}\")"));
            assertTrue(controller.contains("@PutMapping(\"/{id}\")"));
            assertTrue(controller.contains("@DeleteMapping"));
            assertTrue(api.contains("method: 'DELETE', body: ids"));
            assertTrue(api.contains("'/api/custom/person/actions/sync'"));
            assertTrue(controller.contains("@PostMapping(\"/actions/sync\")"));
        }
    }

    @Test
    void frontendOnlyDoesNotReadBackendTemplatesAndMenuIsIndependent() throws Exception {
        Path root = temporary.resolve("frontend-templates");
        Files.createDirectories(root);
        for (String name : List.of("api.ts.vm", "index.vue.vm", "menu.json.vm", "menu.sql.vm")) {
            Files.copy(templates().resolve(name), root.resolve(name));
        }
        var renderer = new BusinessTemplateRenderer(options(root, Map.of()));
        var frontend = renderer.render(request("person", false), metadata("person"), Set.of("frontend"));
        assertEquals(2, frontend.size());
        assertTrue(frontend.stream().allMatch(artifact -> artifact.target() == CodegenResult.Target.FRONTEND));
        var menu = renderer.render(request("person", false), metadata("person"), Set.of("menu"));
        assertEquals(3, menu.size());
        assertTrue(menu.stream().noneMatch(artifact -> artifact.relativePath().endsWith(".java")));
    }

    @Test
    void staleCompleteSourcePackageRejectsTheEntirePlanBeforePublication() throws Exception {
        Path custom = temporary.resolve("templates");
        Files.createDirectories(custom);
        try (var files = Files.list(templates())) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Files.copy(source, custom.resolve(source.getFileName()));
            }
        }
        Files.writeString(custom.resolve("controller.java.vm"),
                "// MES-CODEGEN\npackage old.wrong.packagepath;\npublic class MaterialCategoryController {}\n");
        var configured = options(custom, Map.of());
        var plan = new ConfiguredCodegenService.Plan(request("material_category", false), metadata("material_category"), configured);
        var error = assertThrows(CodegenException.class, () -> new ConfiguredCodegenService()
                .generate(List.of(plan), Set.of("backend"), UnaryOperator.identity()));
        assertEquals(CodegenException.Code.TEMPLATE_FAILURE, error.code());
        assertFalse(Files.exists(temporary.resolve("backend")));
        assertFalse(Files.exists(temporary.resolve("frontend")));
    }

    @Test
    void emptyJavaTemplateRejectsPublication() throws Exception {
        Path custom = temporary.resolve("empty-templates");
        Files.createDirectories(custom);
        try (var files = Files.list(templates())) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Files.copy(source, custom.resolve(source.getFileName()));
            }
        }
        Files.writeString(custom.resolve("controller.java.vm"), "");
        var plan = new ConfiguredCodegenService.Plan(request("material_category", false), metadata("material_category"),
                options(custom, Map.of()));
        var error = assertThrows(CodegenException.class, () -> new ConfiguredCodegenService()
                .generate(List.of(plan), Set.of("backend"), UnaryOperator.identity()));
        assertEquals(CodegenException.Code.TEMPLATE_FAILURE, error.code());
        assertFalse(Files.exists(temporary.resolve("backend")));
    }

}
