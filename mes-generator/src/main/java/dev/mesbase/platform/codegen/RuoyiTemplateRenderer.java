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
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.web.util.HtmlUtils;

import static dev.mesbase.platform.codegen.CodegenResult.Target.BACKEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.FRONTEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.PERMISSION;

/**
 * RuoYi's Velocity metadata -> context -> named templates -> classified file pipeline.
 * MIT upstream attribution: docs/legal/ruoyi-MIT-LICENSE.txt. Base-specific contracts replace legacy Entity APIs.
 */
public final class RuoyiTemplateRenderer {
    private final VelocityEngine engine;

    public RuoyiTemplateRenderer() {
        engine = RuoyiVelocityInitializer.initialize();
    }

    private String merge(String template, Map<String, Object> model) {
        try {
            var out = new StringWriter();
            engine.getTemplate("codegen/base/" + template, "UTF-8").merge(new VelocityContext(model), out);
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

    private static String validation(Column c, boolean required) {
        String a = required ? (c.javaType()
                .equals("String") ? "@jakarta.validation.constraints.NotBlank " : "@jakarta.validation.constraints.NotNull ") : "";
        if (c.javaType().equals("String"))
            a += "@jakarta.validation.constraints.Size(max=" + Math.min(c.size(), 10000) + ") ";
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
        context.put("packageName", rootPackage + "." + subpackage.replace('/', '.'));
        context.put("body", JavaSourceImports.explicit(merge(template, context)));
        out.add(new Artifact(BACKEND, "src/main/java/" + rootPackage.replace('.', '/')
                + "/" + subpackage + "/" + name + ".java", merge("java.vm", context)));
    }

    private static boolean auditColumn(Column column) {
        return Set.of("create_by", "create_time", "update_by", "update_time")
                .contains(column.name().toLowerCase(Locale.ROOT));
    }

    private static String permissionNamespace(CodegenRequest request) {
        String module = request.backendModule();
        return module.startsWith("mes-") ? module.substring(4) : module;
    }

    private static String sqlLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    public List<Artifact> render(CodegenRequest r, TableMetadata t) {
        boolean scoped = r.scope() == CodegenRequest.ScopeKind.FACTORY_SCOPED;
        Column pk = t.columns().stream().filter(Column::primaryKey).findFirst().orElseThrow();
        String n = cap(JdbcMetadataReader.camel(r.tableName())), p = r.packageName(), name = JdbcMetadataReader.camel(r.tableName()), route = "/api/" + r.moduleName() + "/" + r.tableName()
                .replace('_', '-').toLowerCase(Locale.ROOT), perm = permissionNamespace(r) + ":" + r.tableName()
                .replace('_', ':');
        List<Column> writable = t.columns().stream()
                .filter(c -> !c.primaryKey() && !auditColumn(c)).toList();
        List<Column> create = new ArrayList<>();
        if (!pk.autoIncrement()) create.add(pk);
        create.addAll(writable);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("packageName", p);
        ctx.put("rootPackage", p);
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
        ctx.put("menuNameSql", sqlLiteral(r.businessName()));
        ctx.put("menuActions", List.of(Map.of("code", "read", "label", "查询"),
                Map.of("code", "create", "label", "新增"), Map.of("code", "update", "label", "修改"),
                Map.of("code", "delete", "label", "删除")));
        ctx.put("scoped", scoped);
        ctx.put("permissionScope", scoped ? "factory" : "platform");
        ctx.put("idType", pk.javaType());
        ctx.put("pkField", pk.javaField());
        ctx.put("sqlPk", string(t.sqlIdentifier(pk.name())));
        ctx.put("domainPackage", p + ".domain");
        ctx.put("daoPackage", p + ".dao");
        ctx.put("servicePackage", p + ".service");
        ctx.put("controllerPackage", p + ".controller");
        ctx.put("transactionManager", scoped ? "businessTransactionManager" : "masterTransactionManager");
        boolean hasLongText = t.columns().stream().anyMatch(c -> c.jdbcType() == Types.LONGVARCHAR);
        ctx.put("selectByExampleMethod", hasLongText ? "selectByExampleWithBLOBs" : "selectByExample");
        ctx.put("updateByPrimaryKeyMethod", hasLongText ? "updateByPrimaryKeyWithBLOBs" : "updateByPrimaryKey");
        ctx.put("createFields", create.stream().map(c -> field(c, !c.nullable())).toList());
        ctx.put("editFields", writable.stream().map(c -> field(c, !c.nullable())).toList());
        ctx.put("queryFields", writable.stream().filter(c -> c.jdbcType() != Types.LONGVARCHAR)
                .map(c -> field(c, false)).toList());
        ctx.put("viewFields", t.columns().stream().map(c -> Map.of(
                "name", c.javaField(), "type", viewType(c), "getter", "get" + cap(c.javaField()),
                "stringValue", !viewType(c).equals(entityType(c)))).toList());
        List<Artifact> out = new ArrayList<>();
        String dir = p.replace('.', '/') + "/";
        out.addAll(new MyBatisPersistenceRenderer().render(r, t, n));
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
        emitJava(out, ctx, "controller", n + "Permissions", "permissions.java.vm");

        String ownership = scoped ? "BUSINESS" : "PLATFORM";
        emitJava(out, ctx, "service", "I" + n + "Service", "service.java.vm");
        emitJava(out, ctx, "service/impl", n + "ServiceImpl", "service-impl.java.vm");
        emitJava(out, ctx, "controller", n + "Controller", "controller.java.vm");
        ctx.put("pkField", pk.javaField());
        ctx.put("fields", t.columns().stream()
                .map(c -> Map.of("name", c.javaField(), "type", Set.of("String", "Long", "java.math.BigDecimal")
                        .contains(c.javaType()) ? "string" : c.javaType()
                        .startsWith("java.time") ? "string" : c.javaType()
                        .equals("Boolean") ? "boolean" : "number", "nullable", c.nullable(), "label", string(c.comment() == null || c.comment()
                        .isBlank() ? (c.primaryKey() ? "编号" : c.javaField()) : c.comment()).replace("'", "\\'"), "kind", c.javaType()
                        .equals("java.math.BigDecimal") ? "decimal" : c.javaType()
                        .equals("java.time.LocalDate") ? "date" : c.javaType()
                        .startsWith("java.time") ? "datetime" : c.javaType()
                        .matches("Integer|Double|Float") ? "number" : c.javaType()
                        .equals("Boolean") ? "boolean" : "text", "readonly", (c.primaryKey() && c.autoIncrement() || auditColumn(c)), "searchable", !auditColumn(c) && c.javaType()
                        .equals("String") && c.javaField()
                        .matches("(?i).*(code|name)$"), "maxLength", Math.min(c.size(), 10000), "required", !c.nullable()))
                .toList());
        ctx.put("writableFields", create.stream().map(Column::javaField).toList());
        ctx.put("updateFields", writable.stream().map(Column::javaField).toList());
        out.add(new Artifact(FRONTEND, "src/api/" + r.moduleName() + "/" + name + ".ts", merge("api.ts.vm", ctx)));
        out.add(new Artifact(FRONTEND, "src/views/" + r.moduleName() + "/" + name + "/index.vue", merge("index.vue.vm", ctx)));
        out.add(new Artifact(FRONTEND, "src/menus/" + r.moduleName() + "/" + name + ".json", merge("menu.json.vm", ctx)));
        out.add(new Artifact(PERMISSION, "docs/generated/" + r.moduleName() + "/" + name + "-menu.sql", merge("menu.sql.vm", ctx)));
        out.add(new Artifact(PERMISSION, "docs/generated/" + r.moduleName() + "/" + name + "-permissions.md", "<!-- MES-CODEGEN -->\n# " + r.businessName() + "\n\nScope: " + r.scope() + "; Mapper owner: " + ownership + "\n\nPath: `" + route + "`\n\n" + List.of("read", "create", "update", "delete")
                .stream().map(x -> "- `" + perm + ":" + x + "`\n")
                .collect(Collectors.joining()) + "\nMenu INSERT SQL is generated beside this file for reviewed center-database execution; no automatic execution or role grants. Mapper ownership follows its fixed package; no Mapper annotation or central registration is required.\n"));
        ctx.put("ownership", ownership);
        ctx.put("body", JavaSourceImports.explicit(merge("scaffold-test.java.vm", ctx)));
        ctx.put("packageName", p);
        out.add(new Artifact(BACKEND, "src/test/java/" + dir + n + "ScaffoldTest.java", merge("java.vm", ctx)));
        return List.copyOf(out);
    }
}
