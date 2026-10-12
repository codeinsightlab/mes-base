package dev.mesbase.platform.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;

import static dev.mesbase.platform.codegen.CodegenException.Code.INVALID_REQUEST;
import static dev.mesbase.platform.codegen.CodegenException.Code.UNSUPPORTED_SCHEMA;

/**
 * Metadata-driven business generation with explicitly configured templates and classified artifacts.
 */
public final class DefaultMesCodegenService implements MesCodegenService {
    private final CodegenProfiles profiles;
    private final JdbcMetadataReader metadata;
    private final BusinessTemplateRenderer renderer;
    private final ScaffoldWriter writer;
    private final UnaryOperator<List<Artifact>> javaFormatter;

    public DefaultMesCodegenService(CodegenProfiles profiles) {
        this(profiles, UnaryOperator.identity());
    }

    DefaultMesCodegenService(CodegenProfiles profiles, UnaryOperator<List<Artifact>> javaFormatter) {
        this.profiles = profiles;
        this.javaFormatter = javaFormatter;
        metadata = new JdbcMetadataReader();
        renderer = new BusinessTemplateRenderer();
        writer = new ScaffoldWriter();
    }

    public CodegenResult generate(CodegenRequest request) {
        if (request == null || request.scope() == null || request.overwritePolicy() == null)
            throw new CodegenException(INVALID_REQUEST);
        JdbcMetadataReader.identifier(request.tableName());
        JdbcMetadataReader.identifier(request.moduleName());
        if (!request.moduleName().matches("[a-z][a-z0-9_]{0,62}")) throw new CodegenException(INVALID_REQUEST);
        if (request.packageName() == null || !request.packageName()
                .matches("dev\\.mesbase\\.(modules|platform)\\.[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*") || request.businessName() == null || request.businessName()
                .isBlank() || request.businessName().length() > 100 || request.author() == null || request.author()
                .length() > 100)
            throw new CodegenException(INVALID_REQUEST);
        ScaffoldWriter.checkRoot(request.backendRoot());
        ScaffoldWriter.checkRoot(request.targetBackendRoot());
        ScaffoldWriter.checkRoot(request.frontendRoot());
        String datasource = profiles.name(request.datasource());
        var table = metadata.read(profiles.datasource(datasource), request.schema(), request.tableName());
        var pk = table.columns().stream().filter(TableMetadata.Column::primaryKey).toList();
        if (pk.size() != 1 || !Set.of("Long", "Integer", "String")
                .contains(pk.getFirst().javaType()) || !table.references().isEmpty())
            throw new CodegenException(UNSUPPORTED_SCHEMA);
        Set<String> fields = new HashSet<>();
        for (var c : table.columns()) {
            if (!fields.add(c.javaField())) throw new CodegenException(UNSUPPORTED_SCHEMA);
        }
        if (table.columns().stream().noneMatch(column -> !column.primaryKey())) {
            throw new CodegenException(UNSUPPORTED_SCHEMA);
        }
        String packageRoot = request.scope() == CodegenRequest.ScopeKind.FACTORY_SCOPED ? "dev.mesbase.modules." : "dev.mesbase.platform.";
        if (!request.packageName().startsWith(packageRoot)) throw new CodegenException(INVALID_REQUEST);
        List<String> warnings = new ArrayList<>();
        warnings.add("Scaffold requires Factory/Permission/Resource/Domain/SQL/UI review; no automatic grants, runtime binding or production acceptance.");
        if (table.columns().stream().anyMatch(c -> c.name().toLowerCase(Locale.ROOT).endsWith("status")))
            warnings.add("Status semantics are not inferred from numeric values; define a reviewed enum/dictionary before delivery.");
        warnings.add("Frontend permission state comes from server navigation; long IDs/decimals are transported as strings. No import/export/cache/aggregation generated.");
        var files = writer.write(javaFormatter.apply(renderer.render(request, table)), request);
        return new CodegenResult(request.dryRun() ? CodegenResult.Status.DRY_RUN : CodegenResult.Status.GENERATED, datasource, table.table(), table, files, List.copyOf(warnings));
    }
}
