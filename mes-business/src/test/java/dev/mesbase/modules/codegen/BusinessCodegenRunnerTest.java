package dev.mesbase.modules.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCodegenRunnerTest {
    @TempDir
    Path temporary;

    private Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Files.exists(current.resolve("docs/development/codegen-config.review.yaml")) ? current : current.getParent();
    }

    private BusinessCodegenConfiguration config(String tables, String mode) throws Exception {
        Path root = repository();
        String yaml = Files.readString(root.resolve("mes-business/src/test/resources/codegen/configuration.yaml"))
                .replace("tables: []", "tables: " + tables)
                .replace("backendRoot: \".\"", "backendRoot: \"" + temporary.toRealPath() + "/backend\"")
                .replace("frontendRoot: \"../mes-base-ui\"", "frontendRoot: \"" + temporary.toRealPath() + "/frontend\"")
                .replace("mode: \"dry-run\"", "mode: \"" + mode + "\"")
                .replace("formatJava: true", "formatJava: false")
                .replace("property: \"mes.system.enabled\"", "property: \"sample.feature.enabled\"")
                .replace("pageDefault: 20", "pageDefault: 7")
                .replace("fallback: \"business\"", "fallback: \"common\"")
                .replace("dev.mesbase.modules.{module}", "sample.generated.{module}");
        Path file = temporary.resolve("configuration.yaml");
        Files.writeString(file, yaml);
        return BusinessCodegenConfiguration.read(file);
    }

    private DriverManagerDataSource source() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(40))");
            statement.execute("CREATE TABLE pro_sample (id BIGINT PRIMARY KEY, name VARCHAR(40))");
            statement.execute("COMMENT ON TABLE person IS '人员'");
        }
        return source;
    }

    @Test
    void allTablesPreviewAndExplicitTableWriteUseConfiguration() throws Exception {
        var source = source();
        var all = new BusinessCodegenRunner().run(config("[]", "dry-run"), repository(), source);
        assertEquals("DRY_RUN", all.status());
        assertEquals(2, all.tables().size());
        assertFalse(Files.exists(temporary.resolve("backend")));
        var one = new BusinessCodegenRunner().run(config("[person]", "write"), repository(), source);
        assertEquals("GENERATED", one.status());
        assertEquals(1, one.tables().size());
        assertEquals("common", one.tables().getFirst().module());
        Path backend = temporary.resolve("backend/mes-business");
        String controller = Files.readString(backend.resolve("src/main/java/sample/generated/common/controller/PersonController.java"));
        assertTrue(controller.contains("sample.feature.enabled"));
        String service = Files.readString(backend.resolve("src/main/java/sample/generated/common/service/impl/PersonServiceImpl.java"));
        assertTrue(service.contains("? 7 :"));
        Path classes = temporary.resolve("compiled");
        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null);
             var paths = Files.walk(backend.resolve("src/main/java"))) {
            var units = manager.getJavaFileObjectsFromPaths(paths.filter(path -> path.toString().endsWith(".java"))
                    .toList());
            assertTrue(compiler.getTask(null, manager, null,
                            List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()), null, units)
                    .call());
        }
    }

    @Test
    void selectedArtifactsAndExplicitNullConfigurationAreHonored() throws Exception {
        config("[person]", "write");
        Path file = temporary.resolve("configuration.yaml");
        String yaml = Files.readString(file).replace("artifacts: [all]", "artifacts: [frontend]");
        Files.writeString(file, yaml);
        var configuration = BusinessCodegenConfiguration.read(file);
        var report = new BusinessCodegenRunner().run(configuration, repository(), source());
        assertEquals("GENERATED", report.status());
        assertEquals(2, report.files().size());
        assertTrue(report.files().stream().allMatch(artifact -> artifact.target().name().equals("FRONTEND")));
        Files.writeString(file, yaml.replace("  schema: null\n", ""));
        assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(file));
    }

    @Test
    void rejectedTablePreventsBatchPublication() throws Exception {
        var source = source();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unsupported (left_id BIGINT, right_id BIGINT, PRIMARY KEY(left_id,right_id))");
        }
        var report = new BusinessCodegenRunner().run(config("[]", "write"), repository(), source);
        assertEquals("FAILED_PREFLIGHT", report.status());
        assertEquals(1, report.rejected().size());
        assertFalse(Files.exists(temporary.resolve("backend")));
    }

    @Test
    void missingRequiredConfigurationIsRejected() throws Exception {
        Path file = temporary.resolve("missing.yaml");
        Files.writeString(file, "version: 1\n");
        assertThrows(IllegalArgumentException.class, () -> BusinessCodegenConfiguration.read(file));
    }

    @Test
    void menuSqlAddsUpdatesAndPreservesIds() throws Exception {
        var source = source();
        new BusinessCodegenRunner().run(config("[person]", "write"), repository(), source);
        String sql = Files.readString(temporary.resolve("backend/mes-business/docs/generated/common/person-menu.sql"));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_menu(menu_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, menu_name VARCHAR(100), parent_id BIGINT, order_num INT,path VARCHAR(200),component VARCHAR(200),menu_type VARCHAR(1),perms VARCHAR(200),icon VARCHAR(100),create_by VARCHAR(100),create_time TIMESTAMP)");
            statement.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            long pageId;
            try (var rows = statement.executeQuery("SELECT menu_id FROM sys_menu WHERE menu_type='C'")) {
                assertTrue(rows.next());
                pageId = rows.getLong(1);
            }
            statement.execute("INSERT INTO sys_role_menu VALUES(7," + pageId + ")");
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.replace("人员", "人员新名称")
                    .getBytes(StandardCharsets.UTF_8)));
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT m.menu_name FROM sys_menu m JOIN sys_role_menu r ON m.menu_id=r.menu_id WHERE r.role_id=7")) {
                assertTrue(rows.next());
                assertEquals("人员新名称", rows.getString(1));
            }
        }
    }
}
