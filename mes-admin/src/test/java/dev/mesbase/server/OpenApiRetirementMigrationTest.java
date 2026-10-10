package dev.mesbase.server;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEST ONLY H2 fixture: execute the actual retirement SQL, never a live database.
 */
class OpenApiRetirementMigrationTest {
    @Test
    void retiresOnlyOpenApiMenusAndPreservesHistoricalClientData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:openapi-retirement;MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE sys_menu(menu_id bigint, component varchar(100), perms varchar(100))");
                statement.execute("CREATE TABLE sys_role_menu(role_id bigint, menu_id bigint)");
                statement.execute("CREATE TABLE open_api_client(client_id bigint)");
                statement.execute("CREATE TABLE open_api_client_api(client_id bigint, api_code varchar(100))");
                statement.execute("INSERT INTO sys_menu VALUES (1, 'system/openApiClient/index', ''),"
                        + "(2, '', 'system:openApiClient:authorize'), (3, 'system/user/index', 'system:user:list')");
                statement.execute("INSERT INTO sys_role_menu VALUES (10, 1), (10, 2), (10, 3)");
                statement.execute("INSERT INTO open_api_client VALUES (20)");
                statement.execute("INSERT INTO open_api_client_api VALUES (20, 'TEST_ONLY')");
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/system/V16__retire_openapi_permissions.sql"));
            try (var statement = connection.createStatement()) {
                for (String query : new String[]{"SELECT menu_id FROM sys_menu", "SELECT menu_id FROM sys_role_menu"}) {
                    try (var rows = statement.executeQuery(query)) {
                        rows.next();
                        assertEquals(3, rows.getLong(1));
                        assertEquals(false, rows.next());
                    }
                }
                for (String table : new String[]{"open_api_client", "open_api_client_api"}) {
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rows.next();
                        assertEquals(1, rows.getInt(1));
                    }
                }
            }
        }
    }
}
