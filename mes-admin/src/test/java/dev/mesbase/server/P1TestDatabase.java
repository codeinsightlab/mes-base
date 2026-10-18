package dev.mesbase.server;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

/** TEST ONLY: fresh H2 by default; explicit opt-in creates a unique isolated MySQL schema. */
final class P1TestDatabase implements AutoCloseable {
    final DataSource source;
    final JdbcTemplate jdbc;
    private final DataSource admin;
    private final String schema;

    P1TestDatabase() throws Exception {
        if ("true".equals(System.getenv("MES_P1_MYSQL"))) {
            Map<?, ?> root = new Yaml().load(Files.readString(Path.of(
                    "mes-admin/src/main/resources/application-dev.yml")));
            Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("platform");
            String original = (String) platform.get("url");
            String base = original.substring(0, original.indexOf('/', "jdbc:mysql://".length()) + 1);
            String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000";
            schema = "test_only_p1_" + UUID.randomUUID().toString().replace("-", "");
            admin = new DriverManagerDataSource(original, (String) platform.get("username"), (String) platform.get("password"));
            System.out.println("P1 MYSQL target=" + base + schema + " Factory=TEST_ONLY_UNBOUND; fresh schema only; INSERT/UPDATE/DELETE synthetic rows");
            new JdbcTemplate(admin).execute("CREATE DATABASE `" + schema + "`");
            source = new DriverManagerDataSource(base + schema + options,
                    (String) platform.get("username"), (String) platform.get("password"));
        } else {
            schema = null;
            admin = null;
            source = new DriverManagerDataSource("jdbc:h2:mem:p1_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        jdbc = new JdbcTemplate(source);
        try (Connection connection = source.getConnection()) {
            System.out.println("P1 database product=" + connection.getMetaData().getDatabaseProductName());
        }
    }

    // A reduced, synthetic schema derived from current Entity fields, not a migration acceptance claim.
    void table(String name, Class<?> entity, String primary) {
        var columns = new java.util.LinkedHashMap<String, String>();
        for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> value = field.getType();
                String sql = value == String.class ? "VARCHAR(512)" : value == Long.class || value == long.class ? "BIGINT"
                        : value == Integer.class || value == int.class ? "INT"
                        : value == Boolean.class || value == boolean.class ? "BOOLEAN"
                        : Date.class.isAssignableFrom(value) || value == java.time.LocalDateTime.class ? "TIMESTAMP"
                        : value == java.math.BigDecimal.class ? "DECIMAL(24,6)" : null;
                if (sql != null) {
                    String column = field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
                    columns.put(column, column.equals(primary) ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : sql);
                }
            }
        }
        jdbc.execute("CREATE TABLE " + name + "(" + columns.entrySet().stream()
                .map(e -> "`" + e.getKey() + "` " + e.getValue()).collect(java.util.stream.Collectors.joining(",")) + ")");
    }

    public void close() {
        if (schema != null) new JdbcTemplate(admin).execute("DROP DATABASE `" + schema + "`");
    }
}
