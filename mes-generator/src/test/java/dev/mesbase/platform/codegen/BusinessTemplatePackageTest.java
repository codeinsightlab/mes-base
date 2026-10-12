package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.CodegenRequest.OverwritePolicy;
import dev.mesbase.platform.codegen.CodegenRequest.ScopeKind;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import dev.mesbase.platform.codegen.TableMetadata.Column;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void allReviewedJavaTemplatesFollowRootAndSubpackageConfiguration() throws Exception {
        for (var entry : Map.of("material_category", "MaterialCategory", "unit_measure", "UnitMeasure",
                "workshop", "Workshop", "workstation", "Workstation", "material", "Material",
                "safety_stock", "SafetyStock").entrySet()) {
            for (boolean sync : List.of(false, true)) {
                String name = entry.getValue();
                Map<String, Object> overrides = new HashMap<>();
                overrides.put("serviceImplementationTemplate", "reviewed/" + name + "ServiceImpl.java.vm");
                overrides.put("controllerTemplate", "reviewed/" + name + "Controller.java.vm");
                overrides.put("externalInputTemplate", "reviewed/External" + name + ".java.vm");
                if (name.equals("Material")) overrides.put("resourceTemplate", "reviewed/MaterialResource.java.vm");
                var artifacts = new BusinessTemplateRenderer(options(templates(), overrides))
                        .render(request(entry.getKey(), sync), metadata(entry.getKey()));
                for (Artifact artifact : artifacts) {
                    if (artifact.removed() || !artifact.relativePath().endsWith(".java")) continue;
                    String path = artifact.relativePath();
                    String owner = path.substring(path.indexOf("/java/") + 6, path.lastIndexOf('/')).replace('/', '.');
                    assertTrue(artifact.content().contains("package " + owner + ";"), path);
                    assertFalse(artifact.content().contains("dev.mesbase.modules.material"), path);
                }
                String controller = artifacts.stream().filter(a -> a.relativePath().endsWith(name + "Controller.java"))
                        .findFirst().orElseThrow().content();
                assertTrue(controller.contains("import sample.business.material.http.result." + name + "PageVo;"));
                assertTrue(controller.contains("import sample.business.material.model." + name + "Entity;"));
                assertTrue(controller.contains("import sample.business.material.usecase.I" + name + "Service;"));
                assertTrue(controller.contains("import dev.mesbase.platform.web.EndpointAccess;"));
                String service = artifacts.stream().filter(a -> a.relativePath().endsWith(name + "ServiceImpl.java"))
                        .findFirst().orElseThrow().content();
                assertTrue(service.contains("import sample.business.material.mapper." + name + "Mapper;"));
                assertTrue(service.contains("import sample.business.material.usecase.BasicDataRules;"));
                if (sync) assertTrue(service.contains("import sample.business.material.source.External" + name + ";"));
            }
        }
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
        Files.writeString(custom.resolve("stale.java.vm"),
                "// MES-CODEGEN\npackage old.wrong.packagepath;\npublic class MaterialCategoryController {}\n");
        var configured = options(custom, Map.of("controllerTemplate", "stale.java.vm"));
        var plan = new ConfiguredCodegenService.Plan(request("material_category", false), metadata("material_category"), configured);
        var error = assertThrows(CodegenException.class, () -> new ConfiguredCodegenService()
                .generate(List.of(plan), Set.of("backend"), UnaryOperator.identity()));
        assertEquals(CodegenException.Code.TEMPLATE_FAILURE, error.code());
        assertFalse(Files.exists(temporary.resolve("backend")));
        assertFalse(Files.exists(temporary.resolve("frontend")));
    }
}
