package dev.mesbase.platform.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.lang.model.SourceVersion;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;

/**
 * Explicit batch plans; validates all artifacts before one publication operation.
 */
public final class ConfiguredCodegenService {
    public record Plan(CodegenRequest request, TableMetadata metadata, CodegenTemplateOptions templates) {
    }

    public List<CodegenResult.GeneratedFile> generate(List<Plan> plans, Set<String> targets,
                                                      UnaryOperator<List<Artifact>> formatter) {
        if (plans.isEmpty()) return List.of();
        List<Artifact> artifacts = new ArrayList<>();
        CodegenRequest first = plans.getFirst().request();
        for (Plan plan : plans) {
            CodegenRequest request = plan.request();
            if (!SourceVersion.isName(request.packageName())
                    || !request.moduleName().matches("[a-z][a-z0-9_]{0,62}")) {
                throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            }
            if (!first.backendRoot().equals(request.backendRoot())
                    || !first.frontendRoot().equals(request.frontendRoot())
                    || !first.backendModule().equals(request.backendModule())
                    || first.dryRun() != request.dryRun() || first.overwritePolicy() != request.overwritePolicy()) {
                throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            }
            validate(plan.metadata());
            for (Artifact artifact : new RuoyiTemplateRenderer(plan.templates()).render(request, plan.metadata())) {
                if (targets.contains("all") || targets.contains(group(artifact))) artifacts.add(artifact);
            }
        }
        return new ScaffoldWriter().write(formatter.apply(List.copyOf(artifacts)), first);
    }

    public static void validate(TableMetadata table) {
        var keys = table.columns().stream().filter(TableMetadata.Column::primaryKey).toList();
        if (keys.size() != 1 || !Set.of("Long", "Integer", "String").contains(keys.getFirst().javaType())
                || !table.references().isEmpty() || table.columns().size() < 2) {
            throw new CodegenException(CodegenException.Code.UNSUPPORTED_SCHEMA);
        }
        Set<String> fields = new HashSet<>();
        for (var column : table.columns()) {
            if (!fields.add(column.javaField())) throw new CodegenException(CodegenException.Code.UNSUPPORTED_SCHEMA);
        }
    }

    private static String group(Artifact artifact) {
        String path = artifact.relativePath();
        if (artifact.target() == CodegenResult.Target.PERMISSION || path.startsWith("src/menus/")) return "menu";
        if (artifact.target() == CodegenResult.Target.FRONTEND) return "frontend";
        if (path.startsWith("src/test/")) return "test";
        if (path.endsWith("Mapper.java") || path.endsWith("Mapper.xml")
                || path.endsWith("Entity.java") || path.endsWith("Example.java")) return "persistence";
        return "backend";
    }
}
