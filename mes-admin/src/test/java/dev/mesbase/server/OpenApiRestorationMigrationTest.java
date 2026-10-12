package dev.mesbase.server;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEST ONLY: V16 followed by the actual compensating V22, on H2 rather than a deployed MySQL.
 */
class OpenApiRestorationMigrationTest {
    @Test
    void compensatesRetirementWithoutDestroyingClientDataOrInventingRoleGrants() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:openapi-restoration;MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE sys_menu(menu_id BIGINT AUTO_INCREMENT PRIMARY KEY,menu_name VARCHAR(100),parent_id BIGINT,"
                        + "order_num INT,path VARCHAR(100),component VARCHAR(100),menu_type VARCHAR(1),perms VARCHAR(100),"
                        + "create_by VARCHAR(64),create_time TIMESTAMP)");
                statement.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
                statement.execute("CREATE TABLE open_api_client(client_id BIGINT)");
                statement.execute("CREATE TABLE open_api_client_api(client_id BIGINT,api_code VARCHAR(100))");
                statement.execute("INSERT INTO sys_menu(menu_id,parent_id,path,menu_type,component,perms) VALUES"
                        + "(1,0,'system','M','',''),(2,1,'openApiClient','C','system/openApiClient/index','system:openApiClient:list'),"
                        + "(3,1,'user','C','system/user/index','system:user:list')");
                statement.execute("INSERT INTO sys_role_menu VALUES(10,2),(10,3)");
                statement.execute("INSERT INTO open_api_client VALUES(20)");
                statement.execute("INSERT INTO open_api_client_api VALUES(20,'TEST_ONLY')");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/system/V16__retire_openapi_permissions.sql"));
            for (int run = 0; run < 2; run++) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/system/V22__restore_openapi_permissions.sql"));
                try (var statement = connection.createStatement()) {
                    for (String table : new String[]{"sys_role_menu", "open_api_client", "open_api_client_api"}) {
                        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                            rows.next();
                            assertEquals(1, rows.getInt(1));
                        }
                    }
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'system:openApiClient:%'")) {
                        rows.next();
                        assertEquals(6, rows.getInt(1));
                    }
                    try (var rows = statement.executeQuery("SELECT menu_id FROM sys_role_menu")) {
                        rows.next();
                        assertEquals(3, rows.getLong(1));
                    }
                }
            }
        }
    }
}
