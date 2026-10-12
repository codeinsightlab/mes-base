package dev.mesbase.modules.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Project configuration, not a running business service or HTTP endpoint.
 */
public record BusinessCodegenConfiguration(Integer version, Source source, Selection selection,
                                           Naming naming, Output output, Templates templates, Backend backend,
                                           Fields fields,
                                           Limits limits, Frontend frontend, Menu menu, Execution execution,
                                           Map<String, TableOverride> tableOverrides, Boolean generateSync, Sync sync) {
    public BusinessCodegenConfiguration {
        generateSync = Boolean.TRUE.equals(generateSync);
    }

    public record Source(String connectionConfig, String datasource, String schema) {
    }

    public record Selection(List<String> tables, List<String> excludeTables, List<String> artifacts) {
    }

    public record Naming(Module module, Strategy className, Strategy displayName, String author) {
    }

    public record Module(String strategy, String separator, String fallback) {
    }

    public record Strategy(String strategy, String fallback) {
    }

    public record Output(String backendRoot, String backendModule, String frontendRoot, String javaPackage,
                         Packages packages, String frontendApi, String frontendPage, String frontendMenu,
                         String menuSql) {
    }

    public record Packages(String domain, String mapper, String service, String serviceImplementation,
                           String controller, String response) {
    }

    public record Templates(String root, String persistence) {
    }

    public record Backend(String apiPath, String transactionManager, Conditional conditional, Permission permission) {
    }

    public record Conditional(Boolean enabled, String property, String value) {
    }

    public record Permission(String prefix, String resource, String scope, List<String> actions,
                             String batchDeleteAction) {
    }

    public record Fields(List<String> audit, Integer maxStringValidationLength) {
    }

    public record Limits(Integer pageDefault, Integer pageMaximum, Integer batchDeleteMaximum) {
    }

    public record Frontend(String crudComponent, String requestModule, String fieldTypesModule) {
    }

    public record Menu(String table, Long parentId, String directoryName, Integer directoryOrder,
                       Integer pageOrder, String icon, String frontendIcon, String createdBy,
                       Boolean migrateLegacyPermissions) {
    }

    public record Execution(String mode, String overwrite, Boolean formatJava, String formatter,
                            String publication, String unsupportedTable) {
    }

    public record Sync(String matchKey, Integer batchMaximum) {
    }

    public record TableOverride(String module, String displayName, String syncMatchKey, String serverScopeColumn) {
    }

    public static BusinessCodegenConfiguration read(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try (var input = Files.newInputStream(file)) {
            document = new Yaml(new SafeConstructor(options)).load(input);
        }
        BusinessCodegenConfiguration config = new JsonMapper().convertValue(document, BusinessCodegenConfiguration.class);
        require(config != null && Integer.valueOf(1).equals(config.version()), "version");
        Object flag = ((Map<?, ?>) document).get("generateSync");
        require(!((Map<?, ?>) document).containsKey("generateSync") || flag instanceof Boolean, "generateSync");
        requireDeclaredKeys(config, document, "configuration");
        requireFields(config, "configuration");
        if (config.generateSync()) {
            require(config.sync() != null, "sync");
            require(config.sync().batchMaximum() > 0 && config.sync().batchMaximum() <= 10000, "sync.batchMaximum");
            require("factory".equals(config.backend().permission()
                    .scope()), "generateSync currently requires factory scope");
        }
        require("table-prefix".equals(config.naming.module.strategy), "naming.module.strategy");
        require("table-pascal-case".equals(config.naming.className.strategy), "naming.className.strategy");
        require("table-comment".equals(config.naming.displayName.strategy)
                && "table-name".equals(config.naming.displayName.fallback), "naming.displayName");
        require("mybatis3-xmlmapper".equals(config.templates.persistence), "templates.persistence");
        require((List.of("read", "create", "update", "delete").equals(config.backend.permission.actions)
                || List.of("list", "query", "create", "update", "delete").equals(config.backend.permission.actions))
                && "delete".equals(config.backend.permission.batchDeleteAction), "backend.permission.actions");
        require(List.of("factory", "platform").contains(config.backend.permission.scope), "backend.permission.scope");
        require(List.of("dry-run", "write").contains(config.execution.mode), "execution.mode");
        require(List.of("fail", "replace-generated").contains(config.execution.overwrite), "execution.overwrite");
        require("preflight-all".equals(config.execution.publication)
                && "fail".equals(config.execution.unsupportedTable), "execution policy");
        require(config.limits.pageDefault > 0 && config.limits.pageMaximum >= config.limits.pageDefault
                && config.limits.batchDeleteMaximum > 0 && config.fields.maxStringValidationLength > 0, "limits");
        require(!config.backend.conditional.enabled || (config.backend.conditional.property != null
                && config.backend.conditional.value != null), "backend.conditional");
        require(!config.selection.artifacts.isEmpty(), "selection.artifacts");
        for (String artifact : config.selection.artifacts) {
            require(List.of("all", "persistence", "backend", "frontend", "menu", "test")
                    .contains(artifact), "selection.artifacts");
        }
        return config;
    }

    private static void requireDeclaredKeys(Object record, Object document, String path) {
        require(document instanceof Map<?, ?>, path);
        Map<?, ?> values = (Map<?, ?>) document;
        for (var component : record.getClass().getRecordComponents()) {
            String name = component.getName();
            boolean optional = name.equals("tableOverrides") || name.equals("generateSync") || name.equals("sync")
                    || record instanceof TableOverride && (name.equals("syncMatchKey") || name.equals("serverScopeColumn"))
                    || record instanceof Strategy && name.equals("fallback")
                    && !"displayName".equals(path.substring(path.lastIndexOf('.') + 1));
            require(values.containsKey(name) || optional, path + "." + name);
            Object value;
            try {
                value = component.getAccessor().invoke(record);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Invalid configuration: " + path, failure);
            }
            if (value != null && value.getClass().isRecord()) {
                requireDeclaredKeys(value, values.get(name), path + "." + name);
            }
        }
    }

    private static void requireFields(Object record, String path) {
        for (var component : record.getClass().getRecordComponents()) {
            String name = component.getName();
            Object value;
            try {
                value = component.getAccessor().invoke(record);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Invalid configuration: " + path, failure);
            }
            boolean nullable = name.equals("tableOverrides") || name.equals("schema") || name.equals("sync")
                    || record instanceof TableOverride && (name.equals("syncMatchKey") || name.equals("serverScopeColumn"))
                    || record instanceof Strategy && name.equals("fallback")
                    || record instanceof Conditional && (name.equals("property") || name.equals("value"));
            require(value != null || nullable, path + "." + name);
            if (value instanceof String text) require(!text.isBlank(), path + "." + name);
            if (value != null && value.getClass().isRecord()) requireFields(value, path + "." + name);
        }
    }

    public static void require(boolean accepted, String field) {
        if (!accepted) throw new IllegalArgumentException("Invalid or missing configuration: " + field);
    }
}
