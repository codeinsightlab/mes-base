package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import dev.mesbase.platform.codegen.TableMetadata.Column;
import java.io.StringWriter;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.web.util.HtmlUtils;

import static dev.mesbase.platform.codegen.CodegenResult.Target.BACKEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.FRONTEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.PERMISSION;

/**
 * Renders business scaffolds from metadata and explicitly configured Velocity templates.
 * MIT upstream attribution: docs/legal/ruoyi-MIT-LICENSE.txt. Base-specific contracts replace legacy Entity APIs.
 */
public final class BusinessTemplateRenderer {
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.$]+)\\s*;");
    private final VelocityEngine engine;
    private final CodegenTemplateOptions options;

    public BusinessTemplateRenderer() {
        this(null);
    }

    public BusinessTemplateRenderer(CodegenTemplateOptions options) {
        this(options, VelocityTemplateInitializer.initialize(Optional.ofNullable(options)
                .map(nullableValue -> nullableValue.templateRoot()).orElse(null)));
    }

    BusinessTemplateRenderer(CodegenTemplateOptions options, VelocityEngine engine) {
        this.options = options;
        this.engine = engine;
    }

    private String merge(String template, Map<String, Object> model) {
        try {
            var out = new StringWriter();
            if (options == null) {
                engine.getTemplate("codegen/base/" + template, "UTF-8").merge(new VelocityContext(model), out);
            } else {
                var file = options.templateRoot().resolve(template).normalize();
                if (!file.startsWith(options.templateRoot()))
                    throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
                engine.getTemplate(template, "UTF-8").merge(new VelocityContext(model), out);
            }
            return out.toString();
        } catch (RuntimeException e) {
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE, e);
        }
    }

    private static String string(String s) {
        return JdbcMetadataReader.safe(s).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                .replace("\r", " ");
    }

    private static String entityType(Column c) {
        return c.javaType();
    }

    private static String viewType(Column c) {
        return Set.of("Long", "java.math.BigDecimal").contains(c.javaType()) ? "String" : c.javaType();
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String validation(Column c, boolean required) {
        String a = required ? (c.javaType()
                .equals("String") ? "@jakarta.validation.constraints.NotBlank " : "@jakarta.validation.constraints.NotNull ") : "";
        if (c.javaType().equals("String"))
            a += "@jakarta.validation.constraints.Size(max=" + Math.min(c.size(), options == null ? 10000 : options.stringLengthLimit()) + ") ";
        if (c.javaType().equals("java.math.BigDecimal"))
            a += "@jakarta.validation.constraints.Digits(integer=" + Math.max(1, c.size() - c.scale()) + ",fraction=" + c.scale() + ") ";
        return a;
    }

    private Map<String, Object> field(Column column, boolean required) {
        return Map.of("name", column.javaField(), "type", entityType(column),
                "getter", "get" + cap(column.javaField()), "setter", "set" + cap(column.javaField()),
                "validation", validation(column, required), "queryValidation", queryValidation(column), "criteriaMethod", "and" + cap(column.javaField()) + (column.javaType()
                        .equals("String") ? "Like" : "EqualTo"), "string", column.javaType()
                        .equals("String"), "comment", column.comment() == null
                        ? column.name() : column.comment().replace("*/", "* /"));
    }

    private String queryValidation(Column column) {
        String validation = validation(column, false);
        if (column.javaType().equals("java.time.LocalDateTime")) {
            return validation + "@org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) ";
        }
        if (column.javaType().equals("java.time.LocalDate")) {
            return validation + "@org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) ";
        }
        return validation;
    }

    private void emitJava(List<Artifact> out, Map<String, Object> context, String subpackage,
                          String name, String template) {
        String rootPackage = (String) context.get("rootPackage");
        if (options != null) subpackage = options.packages().getOrDefault(subpackage, subpackage).replace('.', '/');
        context.put("packageName", rootPackage + "." + subpackage.replace('/', '.'));
        String rendered = merge(template, context);
        if (rendered.isBlank()) {
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE);
        }
        boolean completeSource = rendered.startsWith("package ") || rendered.startsWith("// MES-CODEGEN") && rendered.contains("\npackage ");
        context.put("body", completeSource ? "" : JavaSourceImports.explicit(rendered));
        String source = completeSource ? rendered : merge("java.vm", context);
        var declaration = PACKAGE_DECLARATION.matcher(source);
        if (!declaration.find() || !context.get("packageName").equals(declaration.group(1)) || declaration.find()) {
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE);
        }
        out.add(new Artifact(BACKEND, "src/main/java/" + rootPackage.replace('.', '/')
                + "/" + subpackage + "/" + name + ".java", source));
    }

    private boolean auditColumn(Column column) {
        return (options == null ? Set.of("create_by", "create_time", "update_by", "update_time") : Set.copyOf(options.auditFields()))
                .contains(column.name().toLowerCase(Locale.ROOT));
    }

    private static String permissionNamespace(CodegenRequest request) {
        String module = request.backendModule();
        return module.startsWith("mes-") ? module.substring(4) : module;
    }

    private static String sqlLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private static boolean selected(Set<String> targets, String group) {
        return targets.contains("all") || targets.contains(group);
    }

    public List<Artifact> render(CodegenRequest r, TableMetadata t) {
        return render(r, t, Set.of("all"));
    }

    public List<Artifact> render(CodegenRequest r, TableMetadata t, Set<String> targets) {
        boolean scoped = r.scope() == CodegenRequest.ScopeKind.FACTORY_SCOPED;
        Column pk = t.columns().stream().filter(Column::primaryKey).findFirst().orElseThrow();
        String n = cap(JdbcMetadataReader.camel(r.tableName())), p = r.packageName(), name = JdbcMetadataReader.camel(r.tableName()), route = "/api/" + r.moduleName() + "/" + r.tableName()
                .replace('_', '-').toLowerCase(Locale.ROOT), perm = permissionNamespace(r) + ":" + r.tableName()
                .replace('_', ':');
        String scopeColumnName = Optional.ofNullable(options)
                .map(nullableValue -> (String) nullableValue.variables().get("serverScopeColumn")).orElse(null);
        Column scopeColumn = Optional.ofNullable(scopeColumnName).map(nullableValue -> t.columns().stream()
                .filter(column -> column.name().equalsIgnoreCase(nullableValue)).findFirst()
                .orElseThrow(() -> new CodegenException(CodegenException.Code.INVALID_REQUEST))).orElse(null);
        if (scopeColumn != null && (scopeColumn.generated() || !scoped || scopeColumn.nullable() || !scopeColumn.javaType().equals("String"))) {
            throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        }
        List<Column> writable = t.columns().stream()
                .filter(c -> !c.generated() && !c.primaryKey() && !auditColumn(c) && c != scopeColumn).toList();
        List<Column> create = new ArrayList<>();
        if (!pk.autoIncrement()) create.add(pk);
        create.addAll(writable);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("packageName", p);
        ctx.put("rootPackage", p);
        ctx.put("serverScope", scopeColumn != null);
        if (scopeColumn != null) ctx.put("scopeField", field(scopeColumn, true));
        ctx.put("ClassName", n);
        ctx.put("className", name);
        ctx.put("functionName", string(r.businessName()));
        ctx.put("functionNameJavaDoc", r.businessName().replace("*/", "* /"));
        ctx.put("authorJavaDoc", r.author().replace("*/", "* /"));
        ctx.put("functionNameHtml", HtmlUtils.htmlEscape(r.businessName()));
        ctx.put("author", string(r.author()));
        ctx.put("moduleName", r.moduleName());
        ctx.put("route", route);
        ctx.put("permission", perm);
        ctx.put("legacyPermission", permissionNamespace(r) + ":" + r.tableName());
        ctx.put("menuDirectorySql", sqlLiteral(r.moduleName()));
        ctx.put("menuDirectoryPath", r.moduleName());
        ctx.put("menuNameSql", sqlLiteral(r.businessName()));
        ctx.put("menuActions", List.of(Map.of("code", "read", "label", "查询"),
                Map.of("code", "create", "label", "新增"), Map.of("code", "update", "label", "修改"),
                Map.of("code", "delete", "label", "删除")));
        ctx.put("scoped", scoped);
        ctx.put("permissionScope", scoped ? "factory" : "platform");
        ctx.put("separateQueryPermission", false);
        ctx.put("generateSync", r.generateSync());
        ctx.put("syncBatchMaximum", 1000);
        ctx.put("conditionalEnabled", true);
        ctx.put("conditionalProperty", scoped ? "mes.system.enabled" : "mes.platform.enabled");
        ctx.put("conditionalValue", "true");
        ctx.put("pageDefault", 20);
        ctx.put("pageMaximum", 100);
        ctx.put("batchDeleteMaximum", 100);
        ctx.put("frontendApiImport", "@/api/" + r.moduleName() + "/" + name);
        ctx.put("pageComponent", r.moduleName() + "/" + name + "/index");
        ctx.put("crudComponent", "@/components/CrudPage.vue");
        ctx.put("requestModule", "@/lib/request");
        ctx.put("fieldTypesModule", "@/lib/crud");
        ctx.put("menuTable", "sys_menu");
        ctx.put("parentId", 0);
        ctx.put("directoryOrder", 0);
        ctx.put("pageOrder", 0);
        ctx.put("menuIcon", "list");
        ctx.put("frontendIcon", "List");
        ctx.put("createdBy", "codegen");
        ctx.put("migrateLegacyPermissions", true);
        ctx.put("batchDeleteAction", "delete");
        ctx.put("idType", pk.javaType());
        ctx.put("pkField", pk.javaField());
        ctx.put("sqlPk", string(t.sqlIdentifier(pk.name())));
        ctx.put("defaultOrderBy", string(t.sqlIdentifier(pk.name()) + (pk.autoIncrement() ? " desc" : "")));
        ctx.put("domainPackage", p + ".domain");
        ctx.put("daoPackage", p + ".dao");
        ctx.put("servicePackage", p + ".service");
        ctx.put("controllerPackage", p + ".controller");
        ctx.put("serviceImplementationPackage", p + ".service.impl");
        ctx.put("transactionManager", scoped ? "businessTransactionManager" : "masterTransactionManager");
        boolean hasLongText = t.columns().stream().anyMatch(c -> !c.generated() && c.jdbcType() == Types.LONGVARCHAR);
        ctx.put("selectByExampleMethod", hasLongText ? "selectByExampleWithBLOBs" : "selectByExample");
        ctx.put("updateByPrimaryKeyMethod", hasLongText ? "updateByPrimaryKeyWithBLOBs" : "updateByPrimaryKey");
        if (options != null) {
            ctx.putAll(options.variables());
            for (var entry : options.packages().entrySet()) {
                switch (entry.getKey()) {
                    case "domain" -> ctx.put("domainPackage", p + "." + entry.getValue());
                    case "dao" -> ctx.put("daoPackage", p + "." + entry.getValue());
                    case "service" -> ctx.put("servicePackage", p + "." + entry.getValue());
                    case "controller" -> ctx.put("controllerPackage", p + "." + entry.getValue());
                    case "service/impl" -> ctx.put("serviceImplementationPackage", p + "." + entry.getValue());
                    default -> {
                    }
                }
            }
            ctx.put("scoped", "factory".equals(ctx.get("permissionScope")));
            route = (String) ctx.get("route");
            perm = (String) ctx.get("permission");
        }
        if (!(ctx.get("menuDirectoryPath") instanceof String directoryPath)
                || !directoryPath.matches("[A-Za-z0-9_-]+")) {
            throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        }
        String frontendApi = options == null ? "src/api/" + r.moduleName() + "/" + name + ".ts" : options.frontendApi();
        if (!frontendApi.startsWith("src/") || frontendApi.contains("..")
                || !frontendApi.matches("[A-Za-z0-9_/-]+\\.(ts|js)")) {
            throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        }
        ctx.put("frontendApiImport", "@/" + frontendApi.substring(4).replaceFirst("\\.(ts|js)$", ""));
        ctx.put("typescriptApi", frontendApi.endsWith(".ts"));
        boolean separateQuery = Boolean.TRUE.equals(ctx.get("separateQueryPermission"));
        ctx.put("listAction", separateQuery ? "list" : "read");
        ctx.put("queryAction", separateQuery ? "query" : "read");
        ctx.put("listPermissionConstant", separateQuery ? "LIST" : "READ");
        ctx.put("queryPermissionConstant", separateQuery ? "QUERY" : "READ");
        var menuActions = new ArrayList<Map<String, String>>();
        if (separateQuery) {
            menuActions.add(Map.of("code", "list", "label", "列表"));
            menuActions.add(Map.of("code", "query", "label", "详情"));
        } else menuActions.add(Map.of("code", "read", "label", "查询"));
        menuActions.addAll(List.of(Map.of("code", "create", "label", "新增"), Map.of("code", "update", "label", "修改"), Map.of("code", "delete", "label", "删除")));
        if (r.generateSync()) menuActions.add(Map.of("code", "sync", "label", "同步"));
        ctx.put("menuActions", menuActions);
        ctx.put("responsePackage", p + "." + (options == null ? "controller.vo" : options.packages()
                .get("controller/vo")));
        ctx.put("createFields", create.stream().map(c -> field(c, !c.nullable())).toList());
        ctx.put("editFields", writable.stream().map(c -> field(c, !c.nullable())).toList());
        ctx.put("queryFields", writable.stream().filter(c -> c.jdbcType() != Types.LONGVARCHAR)
                .map(c -> field(c, false)).toList());
        ctx.put("viewFields", t.columns().stream().filter(c -> !c.generated()).map(c -> Map.of(
                "name", c.javaField(), "type", viewType(c), "getter", "get" + cap(c.javaField()),
                "stringValue", !viewType(c).equals(entityType(c)))).toList());
        ctx.put("generateSync", r.generateSync());
        if (r.generateSync()) {
            if (!scoped) throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            Column syncKey = SyncGeneration.validate(t, r.syncMatchKey());
            ctx.put("syncTaskKey", "codegen." + r.tableName());
            if (!syncKey.primaryKey() && !writable.contains(syncKey)) {
                throw new CodegenException(CodegenException.Code.INVALID_SYNC_KEY);
            }
            List<Column> inputs = new ArrayList<>(create);
            if (!inputs.contains(syncKey)) inputs.addFirst(syncKey);
            ctx.put("syncInputFields", inputs.stream()
                    .map(column -> field(column, !column.nullable() || column == syncKey)).toList());
            ctx.put("syncWritableFields", writable.stream().map(column -> {
                Map<String, Object> values = new HashMap<>(field(column, !column.nullable()));
                values.put("decimal", column.javaType().equals("java.math.BigDecimal"));
                return values;
            }).toList());
            ctx.put("syncKey", field(syncKey, true));
            ctx.put("syncKeyIn", "and" + cap(syncKey.javaField()) + "In");
            ctx.put("syncHasInputPk", !pk.autoIncrement() || syncKey.primaryKey());
            ctx.put("pkGetter", "get" + cap(pk.javaField()));
            ctx.put("pkSetter", "set" + cap(pk.javaField()));
            ctx.put("syncPackage", p + "." + (options == null ? "sync" : options.packages()
                    .getOrDefault("sync", "sync")));

        }
        List<Artifact> out = new ArrayList<>();
        String dir = p.replace('.', '/') + "/";
        if (selected(targets, "persistence")) {
            out.addAll(new MyBatisPersistenceRenderer().render(r, t, n, options));
        }
        String ownership = scoped ? "BUSINESS" : "PLATFORM";
        if (selected(targets, "backend")) {
            ctx.put("commandName", n + "CreateCommand");
            ctx.put("commandFields", ctx.get("createFields"));
            emitJava(out, ctx, "domain", n + "CreateCommand", "command.java.vm");
            ctx.put("commandName", n + "UpdateCommand");
            ctx.put("commandFields", ctx.get("editFields"));
            emitJava(out, ctx, "domain", n + "UpdateCommand", "command.java.vm");
            emitJava(out, ctx, "domain", n + "QueryDto", "query.java.vm");
            emitJava(out, ctx, "domain", n + "Page", "page.java.vm");
            emitJava(out, ctx, "controller/vo", n + "Vo", "view.java.vm");
            emitJava(out, ctx, "controller/vo", n + "PageVo", "page-view.java.vm");
            if (Boolean.TRUE.equals(ctx.get("scoped"))) {
                emitJava(out, ctx, "domain", n + "Resource", "resource.java.vm");
            }
            emitJava(out, ctx, "controller", n + "Permissions", "permissions.java.vm");

            for (var obsolete : List.of("sync/" + n + "SyncService", "sync/" + n + "SyncWriter",
                    "controller/" + n + "SyncController")) {
                int separator = obsolete.lastIndexOf('/');
                String ownerPackage = obsolete.substring(0, separator);
                if (options != null)
                    ownerPackage = options.packages().getOrDefault(ownerPackage, ownerPackage).replace('.', '/');
                out.add(new Artifact(BACKEND, "src/main/java/" + dir + ownerPackage
                        + "/" + obsolete.substring(separator + 1) + ".java", "", true));
            }

            emitJava(out, ctx, "service", "I" + n + "Service", "service.java.vm");
            emitJava(out, ctx, "service/impl", n + "ServiceImpl", "service-impl.java.vm");
            emitJava(out, ctx, "controller", n + "Controller", "controller.java.vm");
            if (r.generateSync()) {
                ctx.put("commandName", "External" + n);
                ctx.put("commandFields", ctx.get("syncInputFields"));
                emitJava(out, ctx, "sync", "External" + n, "command.java.vm");
                emitJava(out, ctx, "sync", n + "QueryPort", "sync-port.java.vm");
                emitJava(out, ctx, "sync", "Unconfigured" + n + "QueryAdapter", "sync-placeholder.java.vm");
                emitJava(out, ctx, "sync", n + "SyncResult", "sync-result.java.vm");
            }
        }
        ctx.put("pkField", pk.javaField());
        ctx.put("fields", t.columns().stream().filter(c -> !c.generated())
                .map(c -> Map.of("name", c.javaField(), "type", Set.of("String", "Long", "java.math.BigDecimal")
                        .contains(c.javaType()) ? "string" : c.javaType()
                        .startsWith("java.time") ? "string" : c.javaType()
                        .equals("Boolean") ? "boolean" : "number", "nullable", c.nullable(), "label", string(c.comment() == null || c.comment()
                        .isBlank() ? (c.primaryKey() ? "编号" : c.javaField()) : c.comment()).replace("'", "\\'"), "kind", c.javaType()
                        .equals("java.math.BigDecimal") ? "decimal" : c.javaType()
                        .equals("java.time.LocalDate") ? "date" : c.javaType()
                        .startsWith("java.time") ? "datetime" : c.javaType()
                        .matches("Integer|Double|Float") ? "number" : c.javaType()
                        .equals("Boolean") ? "boolean" : "text", "readonly", (c.primaryKey() && c.autoIncrement() || auditColumn(c) || c == scopeColumn), "searchable", !auditColumn(c) && c.javaType()
                        .equals("String") && c.javaField()
                        .matches("(?i).*(code|name)$"), "maxLength", Math.min(c.size(), options == null ? 10000 : options.stringLengthLimit()), "required", !c.nullable()))
                .toList());
        ctx.put("writableFields", create.stream().map(Column::javaField).toList());
        ctx.put("updateFields", writable.stream().map(Column::javaField).toList());
        if (selected(targets, "frontend")) {
            out.add(new Artifact(FRONTEND, frontendApi, merge("api.ts.vm", ctx)));
            out.add(new Artifact(FRONTEND, options == null ? "src/views/" + r.moduleName() + "/" + name + "/index.vue" : options.frontendPage(), merge("index.vue.vm", ctx)));
        }
        if (selected(targets, "menu")) {
            out.add(new Artifact(FRONTEND, options == null ? "src/menus/" + r.moduleName() + "/" + name + ".json" : options.frontendMenu(), merge("menu.json.vm", ctx)));
            out.add(new Artifact(PERMISSION, options == null ? "docs/generated/" + r.moduleName() + "/" + name + "-menu.sql" : options.menuSql(), merge("menu.sql.vm", ctx)));
            out.add(new Artifact(PERMISSION, "docs/generated/" + r.moduleName() + "/" + name + "-permissions.md", "<!-- MES-CODEGEN -->\n# " + r.businessName() + "\n\nScope: " + r.scope() + "; Mapper owner: " + ownership + "\n\nPath: `" + route + "`\n\n" + menuActions.stream()
                    .map(action -> action.get("code")).map(x -> "- `" + ctx.get("permission") + ":" + x + "`\n")
                    .collect(Collectors.joining()) + "\nMenu INSERT SQL is generated beside this file for reviewed center-database execution; no automatic execution or role grants. Mapper ownership follows its fixed package; no Mapper annotation or central registration is required.\n"));
        }
        if (selected(targets, "test")) {
            ctx.put("ownership", ownership);
            ctx.put("body", JavaSourceImports.explicit(merge("scaffold-test.java.vm", ctx)));
            ctx.put("packageName", p);
            out.add(new Artifact(BACKEND, "src/test/java/" + dir + n + "ScaffoldTest.java", merge("java.vm", ctx)));
        }
        return List.copyOf(out);
    }
}
