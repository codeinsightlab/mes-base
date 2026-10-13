package dev.mesbase.server;

import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** TEST ONLY H2 fixture. Real MySQL migration evidence is recorded separately. */
class QualityRemovalMigrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removesQualityGrantsAndTablesWithoutDeletingSharedMenus(boolean sharedGroup) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE qc_index(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE qc_defect(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE material(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO material VALUES(1)");
            sql.execute("CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY,parent_id BIGINT,path VARCHAR(100),"
                    + "component VARCHAR(200),menu_type CHAR(1),perms VARCHAR(200),create_by VARCHAR(64))");
            sql.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT REFERENCES sys_menu(menu_id))");
            sql.execute("CREATE TABLE sys_user_table_preference(table_key VARCHAR(192),url VARCHAR(512))");
            sql.execute("INSERT INTO sys_menu VALUES"
                    + "(1,0,'qc',NULL,'M',NULL,'codegen'),"
                    + "(2,1,'qcIndex','qc/qcIndex/index','C','business:qc:index:list','codegen'),"
                    + "(3,2,'#',NULL,'F','business:qc:index:sync','codegen'),"
                    + "(4,1,'qcDefect','qc/qcDefect/index','C','business:qc:defect:list','codegen'),"
                    + "(5,4,'#',NULL,'F','qc:defect:delete','codegen'),"
                    + "(7,0,'material','basic/material/index','C','business:material:list','codegen'),"
                    + "(8,0,'qc',NULL,'M',NULL,'TEST_ONLY_USER')");
            if (sharedGroup) {
                sql.execute("INSERT INTO sys_menu VALUES(6,1,'other','other/index','C','TEST_ONLY:other:read','TEST_ONLY_USER')");
            }
            sql.execute("INSERT INTO sys_role_menu SELECT 99,menu_id FROM sys_menu");
            sql.execute("INSERT INTO sys_user_table_preference VALUES('TEST_ONLY_QUALITY','/qc/qcIndex'),"
                    + "('business:material:list','/basic/material')");
            for (int repeat = 0; repeat < 2; repeat++) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/business/V7__remove_early_quality_tables.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/system/V25__remove_early_quality_capabilities.sql"));
            }
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('QC_INDEX','QC_DEFECT')"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'business:qc:%' OR perms LIKE 'qc:%'"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_role_menu r LEFT JOIN sys_menu m ON r.menu_id=m.menu_id WHERE m.menu_id IS NULL"));
            assertEquals(sharedGroup ? 1 : 0, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=1"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=8"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=7"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM material"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_user_table_preference WHERE url='/basic/material'"));
        }
    }

    private static int count(Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
