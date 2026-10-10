package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.codegen.mybatis3.IntrospectedTableMyBatis3Impl;
import org.mybatis.generator.config.CommentGeneratorConfiguration;
import org.mybatis.generator.config.Context;
import org.mybatis.generator.config.GeneratedKey;
import org.mybatis.generator.config.JavaClientGeneratorConfiguration;
import org.mybatis.generator.config.JavaModelGeneratorConfiguration;
import org.mybatis.generator.config.ModelType;
import org.mybatis.generator.config.PluginConfiguration;
import org.mybatis.generator.config.SqlMapGeneratorConfiguration;
import org.mybatis.generator.config.TableConfiguration;

/**
 * Adapts already reviewed JDBC metadata to MBG without reading business rows or publishing files.
 */
final class MyBatisPersistenceRenderer {
    List<Artifact> render(CodegenRequest request, TableMetadata metadata, String className) {
        return render(request, metadata, className, null);
    }

    List<Artifact> render(CodegenRequest request, TableMetadata metadata, String className,
                          CodegenTemplateOptions options) {
        Context context = new Context(ModelType.FLAT);
        context.setId("mes-codegen");
        context.setTargetRuntime("MyBatis3");
        context.addProperty("beginningDelimiter", metadata.identifierQuote());
        context.addProperty("endingDelimiter", metadata.identifierQuote());
        var comments = new CommentGeneratorConfiguration();
        comments.addProperty("suppressAllComments", "true");
        context.setCommentGeneratorConfiguration(comments);
        var model = new JavaModelGeneratorConfiguration();
        model.setTargetPackage(request.packageName() + "." + (options == null ? "domain" : options.packages()
                .get("domain")));
        model.setTargetProject("src/main/java");
        context.setJavaModelGeneratorConfiguration(model);
        var client = new JavaClientGeneratorConfiguration();
        client.setConfigurationType("XMLMAPPER");
        client.setTargetPackage(request.packageName() + "." + (options == null ? "dao" : options.packages()
                .get("dao")));
        client.setTargetProject("src/main/java");
        context.setJavaClientGeneratorConfiguration(client);
        var sqlMap = new SqlMapGeneratorConfiguration();
        sqlMap.setTargetPackage(request.packageName() + "." + (options == null ? "dao" : options.packages()
                .get("dao")));
        sqlMap.setTargetProject("src/main/resources");
        context.setSqlMapGeneratorConfiguration(sqlMap);
        var plugin = new PluginConfiguration();
        plugin.setConfigurationType(MyBatisNamingPlugin.class.getName());
        plugin.addProperty("className", className);
        context.addPluginConfiguration(plugin);
        var config = new TableConfiguration(context);
        config.setTableName(metadata.table());
        config.setSchema(metadata.schema());
        config.setDomainObjectName(className + "Entity");
        config.setMapperName(className + "Mapper");
        config.setDelimitIdentifiers(metadata.requiresDelimiter(metadata.table()));
        metadata.columns().stream().filter(TableMetadata.Column::autoIncrement).findFirst()
                .ifPresent(column -> config.setGeneratedKey(new GeneratedKey(column.name(), "JDBC", true, null)));
        context.addTableConfiguration(config);
        var table = new IntrospectedTableMyBatis3Impl();
        table.setContext(context);
        table.setTableConfiguration(config);
        table.setFullyQualifiedTable(new FullyQualifiedTable(null, metadata.schema(), metadata.table(),
                className + "Entity", null, true, null, null, null,
                metadata.requiresDelimiter(metadata.table()), null, context));
        table.setRemarks(metadata.comment());
        table.setTableType("TABLE");
        for (TableMetadata.Column source : metadata.columns()) {
            var column = new IntrospectedColumn();
            column.setContext(context);
            column.setIntrospectedTable(table);
            column.setActualColumnName(source.name());
            column.setJavaProperty(source.javaField());
            column.setFullyQualifiedJavaType(new FullyQualifiedJavaType(source.javaType()));
            column.setJdbcType(source.jdbcType());
            column.setJdbcTypeName(JDBCType.valueOf(source.jdbcType()).getName());
            column.setLength(source.size());
            column.setScale(source.scale());
            column.setNullable(source.nullable());
            column.setIdentity(source.autoIncrement());
            column.setAutoIncrement(source.autoIncrement());
            column.setColumnNameDelimited(metadata.requiresDelimiter(source.name()));
            column.setDefaultValue(source.defaultValue());
            column.setRemarks(source.comment());
            table.addColumn(column);
        }
        metadata.columns().stream().filter(TableMetadata.Column::primaryKey)
                .forEach(column -> table.addPrimaryKeyColumn(column.name()));
        context.getIntrospectedTables().add(table);
        List<GeneratedJavaFile> javaFiles = new ArrayList<>();
        List<GeneratedXmlFile> xmlFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            context.generateFiles(new ProgressCallback() {
                                  }, javaFiles, xmlFiles,
                    new ArrayList<>(), new ArrayList<>(), warnings);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE, exception);
        }
        if (!warnings.isEmpty()) {
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE);
        }
        List<Artifact> artifacts = new ArrayList<>();
        for (GeneratedJavaFile file : javaFiles) {
            artifacts.add(new Artifact(CodegenResult.Target.BACKEND,
                    "src/main/java/" + file.getTargetPackage().replace('.', '/') + "/" + file.getFileName(),
                    "// MES-CODEGEN: MyBatis Generator standard single-table persistence.\n" + file.getFormattedContent()));
        }
        for (GeneratedXmlFile file : xmlFiles) {
            String content = file.getFormattedContent()
                    .replaceFirst("\\?>", "?>\n<!-- MES-CODEGEN: MyBatis Generator standard single-table persistence. -->");
            artifacts.add(new Artifact(CodegenResult.Target.BACKEND,
                    "src/main/resources/" + file.getTargetPackage()
                            .replace('.', '/') + "/" + file.getFileName(), content));
        }
        return List.copyOf(artifacts);
    }
}
