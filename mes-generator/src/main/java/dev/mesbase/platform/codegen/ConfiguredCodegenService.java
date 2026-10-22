package dev.mesbase.platform.codegen;

import java.util.Optional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.apache.velocity.app.VelocityEngine;
import java.util.HashMap;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.lang.model.SourceVersion;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import dev.mesbase.platform.codegen.BusinessCodegenConfiguration.Documents;
import java.util.Comparator;

/**
 * Explicit batch plans; validates all artifacts before one publication operation.
 */
public final class ConfiguredCodegenService {
    public record Plan(CodegenRequest request, TableMetadata metadata, CodegenTemplateOptions templates) {
    }

    public List<CodegenResult.GeneratedFile> generate(List<Plan> plans, Set<String> targets,
                                                      UnaryOperator<List<Artifact>> formatter) {
        return generate(plans, targets, formatter, null);
    }

    public List<CodegenResult.GeneratedFile> generate(List<Plan> plans, Set<String> targets,
                                                      UnaryOperator<List<Artifact>> formatter, Documents documents) {
        if (plans.isEmpty()) return List.of();
        List<Artifact> artifacts = new ArrayList<>();
        CodegenRequest first = plans.getFirst().request();
        Map<Path, VelocityEngine> engines = new HashMap<>();
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
            Path templateRoot = Optional.ofNullable(plan.templates()).map(nullableValue -> nullableValue.templateRoot()).orElse(null);
            VelocityEngine engine = engines.computeIfAbsent(templateRoot, VelocityTemplateInitializer::initialize);
            BusinessTemplateRenderer renderer = new BusinessTemplateRenderer(plan.templates(), engine);
            artifacts.addAll(renderer.render(request, plan.metadata(), targets));
        }
        return new ScaffoldWriter().write(formatter.apply(mergeDocuments(artifacts, documents)), first);
    }

    private static List<Artifact> mergeDocuments(List<Artifact> artifacts, Documents documents) {
        if (documents == null || "per-table".equals(documents.mode())) return List.copyOf(artifacts);
        if (!"merged".equals(documents.mode())) throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        List<Artifact> output = new ArrayList<>(artifacts);
        for (String extension : List.of(".sql", ".md")) {
            List<Artifact> parts = artifacts.stream()
                    .filter(artifact -> artifact.target() == CodegenResult.Target.PERMISSION && !artifact.removed()
                            && artifact.relativePath().endsWith(extension))
                    .sorted(Comparator.comparing(Artifact::relativePath)).toList();
            if (parts.isEmpty()) continue;
            boolean sql = extension.equals(".sql");
            StringBuilder content = new StringBuilder(sql ? "-- MES-CODEGEN: 本批次合并菜单 SQL。\n"
                    : "<!-- MES-CODEGEN: 本批次合并权限说明。 -->\n");
            for (Artifact part : parts) {
                content.append('\n').append(sql ? "-- 来源：" : "<!-- 来源：")
                        .append(part.relativePath()).append(sql ? "\n" : " -->\n")
                        .append(part.content().stripTrailing()).append('\n');
            }
            output.removeAll(parts);
            output.add(new Artifact(CodegenResult.Target.PERMISSION, sql ? documents.sql() : documents.markdown(),
                    content.toString()));
        }
        return List.copyOf(output);
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

}
