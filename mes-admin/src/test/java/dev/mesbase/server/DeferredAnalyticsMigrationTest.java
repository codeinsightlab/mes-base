package dev.mesbase.server;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEST ONLY H2 MySQL-mode migration fixture; no live database is accessed.
 */
class DeferredAnalyticsMigrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removesAnalyticsGrantsAndPreservesFoundationAndOtherBusinessMenus(boolean sharedGroup) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sys_menu (menu_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "menu_name VARCHAR(100), parent_id BIGINT DEFAULT 0, order_num INT, path VARCHAR(100), "
                    + "component VARCHAR(200), menu_type CHAR(1), perms VARCHAR(200), "
                    + "create_by VARCHAR(100), create_time TIMESTAMP, legacy_id BIGINT)");
            sql.execute("CREATE TABLE sys_role_menu (role_id BIGINT, menu_id BIGINT)");
            sql.execute("INSERT INTO sys_menu(menu_name,path,menu_type) VALUES ('TEST ONLY system','system','M')");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/system/V13__factory_analytics_capabilities.sql"));
            if (sharedGroup) {
                sql.execute("INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms) "
                        + "SELECT 'TEST ONLY unrelated feature',menu_id,'other','C','TEST_ONLY:other:read' "
                        + "FROM sys_menu WHERE path='mes/pro' AND create_by='migration'");
            }
            sql.execute("INSERT INTO sys_menu(menu_name,path,menu_type,create_by) "
                    + "VALUES ('TEST ONLY custom group','mes/pro','M','TEST_ONLY owner')");
            sql.execute("INSERT INTO sys_role_menu SELECT 99,menu_id FROM sys_menu");
            sql.execute("CREATE TABLE business_marker (id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO business_marker VALUES (1)");

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/system/V15__defer_production_analytics.sql"));

            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE "
                    + "component IN ('mes/pro/productionMonitor/index','mes/pro/personnelAnalysis/index') "
                    + "OR perms LIKE 'mes:pro:productionMonitor:%' OR perms LIKE 'mes:pro:personnelAnalysis:%'"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_role_menu r "
                    + "LEFT JOIN sys_menu m ON m.menu_id=r.menu_id WHERE m.menu_id IS NULL"));
            assertEquals(10, count(sql, "SELECT COUNT(*) FROM sys_menu "
                    + "WHERE perms LIKE 'system:factory:%' OR perms LIKE 'system:datasource:%'"));
            assertEquals(sharedGroup ? 1 : 0, count(sql, "SELECT COUNT(*) FROM sys_menu "
                    + "WHERE path='mes/pro' AND create_by='migration'"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE create_by='TEST_ONLY owner'"));
            assertEquals(sharedGroup ? 1 : 0, count(sql,
                    "SELECT COUNT(*) FROM sys_menu WHERE perms='TEST_ONLY:other:read'"));
            assertEquals(count(sql, "SELECT COUNT(*) FROM sys_menu"),
                    count(sql, "SELECT COUNT(*) FROM sys_role_menu"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM business_marker"));
        }
    }

    private static int count(Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
