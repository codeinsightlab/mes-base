package dev.mesbase.modules.codegen;

import dev.mesbase.platform.codegen.CodegenCli;
import dev.mesbase.platform.codegen.CodegenException;
import dev.mesbase.platform.codegen.CodegenRequest;
import dev.mesbase.platform.codegen.CodegenResult;
import dev.mesbase.platform.codegen.CodegenTemplateOptions;
import dev.mesbase.platform.codegen.ConfiguredCodegenService;
import dev.mesbase.platform.codegen.IdeaGeneratedJavaFormatter;
import dev.mesbase.platform.codegen.JdbcMetadataReader;
import dev.mesbase.platform.codegen.TableMetadata;
import dev.mesbase.platform.codegen.SyncGeneration;
import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Local developer entry; never registered as a web endpoint or scheduled task.
 */
public final class BusinessCodegenRunner {
    public record TablePlan(String table, String module, String displayName, String javaPackage,
                            String apiPath, String permission, String syncMatchKey) {
    }

    public record RejectedTable(String table, String errorCode) {
    }

    public record Report(String status, String mode, List<TablePlan> tables,
                         List<RejectedTable> rejected, List<CodegenResult.GeneratedFile> files) {
    }

    public Report run(BusinessCodegenConfiguration config, Path repository) throws IOException {
        var properties = new YamlPropertySourceLoader().load("codegen-connection",
                new FileSystemResource(repository.resolve(config.source().connectionConfig())));
        var settings = new Binder(ConfigurationPropertySources.from(properties)).bind("mes.codegen", CodegenCli.Settings.class)
                .orElseThrow(() -> new IllegalArgumentException("Missing developer datasource configuration"));
        var profile = settings.profiles().get(config.source().datasource());
        BusinessCodegenConfiguration.require(profile != null && profile.url() != null, "source.datasource");
        var source = new DriverManagerDataSource();
        if (profile.driverClassName() != null) source.setDriverClassName(profile.driverClassName());
        source.setUrl(profile.url());
        source.setUsername(profile.username());
        source.setPassword(profile.password());
        return run(config, repository, source);
    }

    public Report run(BusinessCodegenConfiguration config, Path repository, DataSource source) {
        Path root = repository.toAbsolutePath().normalize();
        List<String> selected = new ArrayList<>(config.selection().tables());
        if (selected.isEmpty()) {
            try (var connection = source.getConnection();
                 var tables = connection.getMetaData()
                         .getTables(connection.getCatalog(), config.source().schema(), "%", new String[]{"TABLE"})) {
                Set<String> names = new TreeSet<>();
                while (tables.next()) {
                    String schema = tables.getString("TABLE_SCHEM");
                    if ("INFORMATION_SCHEMA".equalsIgnoreCase(schema)) continue;
                    names.add(tables.getString("TABLE_NAME"));
                }
                selected.addAll(names);
            } catch (Exception failure) {
                throw new CodegenException(CodegenException.Code.DATASOURCE_UNAVAILABLE, failure);
            }
        }
        BusinessCodegenConfiguration.require(selected.stream().distinct()
                .count() == selected.size(), "selection.tables");
        List<ConfiguredCodegenService.Plan> plans = new ArrayList<>();
        List<TablePlan> descriptions = new ArrayList<>();
        List<RejectedTable> rejected = new ArrayList<>();
        var metadata = new JdbcMetadataReader();
        for (String tableName : selected) {
            if (config.selection().excludeTables().stream().anyMatch(tableName::equalsIgnoreCase)) continue;
            try {
                var table = metadata.read(source, config.source().schema(), tableName);
                ConfiguredCodegenService.validate(table);
                String module = module(config, tableName);
                String displayName = table.comment() == null || table.comment().isBlank() ? tableName : table.comment();
                if (config.tableOverrides() != null && config.tableOverrides().containsKey(tableName)) {
                    var override = config.tableOverrides().get(tableName);
                    if (override.module() != null) module = override.module();
                    if (override.displayName() != null) displayName = override.displayName();
                }
                String javaPackage = expand(config.output().javaPackage(), tableName, module);
                String apiPath = expand(config.backend().apiPath(), tableName, module);
                String permission = config.backend().permission().prefix() + ":"
                        + expand(config.backend().permission().resource(), tableName, module);
                boolean factory = "factory".equals(config.backend().permission().scope());
                String syncKey = config.generateSync() ? config.sync().matchKey() : null;
                if (config.generateSync() && config.tableOverrides() != null && config.tableOverrides()
                        .containsKey(tableName)
                        && config.tableOverrides().get(tableName).syncMatchKey() != null) {
                    syncKey = config.tableOverrides().get(tableName).syncMatchKey();
                }
                if (config.generateSync()) SyncGeneration.validate(table, syncKey);
                var request = new CodegenRequest(config.source().datasource(), config.source().schema(), tableName,
                        module, displayName, javaPackage, config.naming().author(),
                        factory ? CodegenRequest.ScopeKind.FACTORY_SCOPED : CodegenRequest.ScopeKind.GLOBAL,
                        "fail".equals(config.execution().overwrite()) ? CodegenRequest.OverwritePolicy.FAIL
                                : CodegenRequest.OverwritePolicy.REPLACE_GENERATED,
                        true, root.resolve(config.output().backendRoot()).normalize(),
                        root.resolve(config.output().frontendRoot()).normalize(), config.output()
                        .backendModule(), config.generateSync(), syncKey);
                var options = options(config, root, tableName, module, apiPath, permission);
                plans.add(new ConfiguredCodegenService.Plan(request, table, options));
                descriptions.add(new TablePlan(tableName, module, displayName, javaPackage, apiPath, permission, syncKey));
            } catch (CodegenException failure) {
                rejected.add(new RejectedTable(tableName, failure.code().name()));
            }
        }
        // Failed preflight must not publish any table, even in write mode.
        if (rejected.isEmpty() && "write".equals(config.execution().mode())) {
            plans = plans.stream().map(plan -> {
                var r = plan.request();
                return new ConfiguredCodegenService.Plan(new CodegenRequest(r.datasource(), r.schema(), r.tableName(),
                        r.moduleName(), r.businessName(), r.packageName(), r.author(), r.scope(), r.overwritePolicy(),
                        false, r.backendRoot(), r.frontendRoot(), r.backendModule(), r.generateSync(), r.syncMatchKey()), plan.metadata(), plan.templates());
            }).toList();
        }
        UnaryOperator<List<Artifact>> formatter = config.execution().formatJava()
                ? new IdeaGeneratedJavaFormatter(root.resolve(config.execution()
                .formatter())) : UnaryOperator.identity();
        List<CodegenResult.GeneratedFile> files;
        try {
            files = new ConfiguredCodegenService().generate(plans, Set.copyOf(config.selection()
                    .artifacts()), formatter);
        } catch (CodegenException failure) {
            rejected.add(new RejectedTable("<output>", failure.code().name()));
            files = List.of();
        }
        return new Report(rejected.isEmpty() ? "dry-run".equals(config.execution().mode()) ? "DRY_RUN" : "GENERATED"
                : "FAILED_PREFLIGHT", config.execution()
                .mode(), List.copyOf(descriptions), List.copyOf(rejected), files);
    }

    private static String module(BusinessCodegenConfiguration config, String table) {
        String separator = config.naming().module().separator();
        int split = table.indexOf(separator);
        return split > 0 ? table.substring(0, split).toLowerCase(Locale.ROOT) : config.naming().module().fallback();
    }

    private static String expand(String value, String table, String module) {
        String camel = JdbcMetadataReader.camel(table);
        String expanded = value.replace("{module}", module).replace("{classNameCamel}", camel)
                .replace("{tableNameKebab}", table.replace('_', '-').toLowerCase(Locale.ROOT))
                .replace("{tableNameColon}", table.replace('_', ':')).replace("{tableName}", table);
        BusinessCodegenConfiguration.require(!expanded.contains("{") && !expanded.contains("}"), "placeholder");
        return expanded;
    }

    private static String literal(String value) {
        return value.replace("'", "''");
    }

    private static CodegenTemplateOptions options(BusinessCodegenConfiguration c, Path root, String table,
                                                  String module, String apiPath, String permission) {
        var p = c.output().packages();
        Map<String, String> packages = Map.of("domain", p.domain(), "dao", p.mapper(), "service", p.service(),
                "service/impl", p.serviceImplementation(), "controller", p.controller(), "controller/vo", p.response());
        Map<String, Object> variables = new HashMap<>();
        var tableOverride = c.tableOverrides() == null ? null : c.tableOverrides().get(table);
        if (tableOverride != null && tableOverride.serverScopeColumn() != null) {
            variables.put("serverScopeColumn", tableOverride.serverScopeColumn());
        }
        variables.put("syncBatchMaximum", c.generateSync() ? c.sync().batchMaximum() : 1000);
        variables.put("route", apiPath);
        variables.put("permission", permission);
        variables.put("separateQueryPermission", c.backend().permission().actions().contains("list"));
        variables.put("permissionScope", c.backend().permission().scope());
        variables.put("transactionManager", c.backend().transactionManager());
        variables.put("conditionalEnabled", c.backend().conditional().enabled());
        variables.put("conditionalProperty", c.backend().conditional().enabled() ? c.backend().conditional()
                .property() : "");
        variables.put("conditionalValue", c.backend().conditional().enabled() ? c.backend().conditional().value() : "");
        variables.put("pageDefault", c.limits().pageDefault());
        variables.put("pageMaximum", c.limits().pageMaximum());
        variables.put("batchDeleteMaximum", c.limits().batchDeleteMaximum());
        variables.put("batchDeleteAction", c.backend().permission().batchDeleteAction());
        variables.put("crudComponent", c.frontend().crudComponent());
        variables.put("requestModule", c.frontend().requestModule());
        variables.put("fieldTypesModule", c.frontend().fieldTypesModule());
        variables.put("menuTable", c.menu().table());
        variables.put("parentId", c.menu().parentId());
        variables.put("menuDirectorySql", literal(expand(c.menu().directoryName(), table, module)));
        variables.put("directoryOrder", c.menu().directoryOrder());
        variables.put("pageOrder", c.menu().pageOrder());
        variables.put("menuIcon", literal(c.menu().icon()));
        variables.put("frontendIcon", c.menu().frontendIcon());
        variables.put("createdBy", literal(c.menu().createdBy()));
        variables.put("migrateLegacyPermissions", c.menu().migrateLegacyPermissions());
        String api = expand(c.output().frontendApi(), table, module);
        String page = expand(c.output().frontendPage(), table, module);
        variables.put("frontendApiImport", "@/" + api.replaceFirst("^src/", "").replaceFirst("\\.ts$", ""));
        variables.put("pageComponent", page.replaceFirst("^src/views/", "").replaceFirst("\\.vue$", ""));
        return new CodegenTemplateOptions(root.resolve(c.templates().root()).normalize(), variables, packages,
                c.fields().audit(), c.fields().maxStringValidationLength(), api, page,
                expand(c.output().frontendMenu(), table, module), expand(c.output().menuSql(), table, module));
    }
}
