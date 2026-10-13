package dev.mesbase.server;

import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEST ONLY migration fixture; real development MySQL acceptance is recorded separately.
 */
class PersonnelRemovalMigrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removesProjectionAndGrantsButPreservesSystemUsersEquipmentAndSharedDirectory(
            boolean sharedGroup) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE personnel (id BIGINT PRIMARY KEY COMMENT '测试人员投影主键') COMMENT='测试人员投影'");
            sql.execute("CREATE TABLE equipment (id BIGINT PRIMARY KEY COMMENT '测试设备主键') COMMENT='测试设备目录'");
            sql.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY COMMENT '测试用户主键') COMMENT='测试系统用户'");
            sql.execute("INSERT INTO personnel VALUES(1)");
            sql.execute("INSERT INTO equipment VALUES(1)");
            sql.execute("INSERT INTO sys_user VALUES(1)");
            sql.execute("CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY COMMENT '测试菜单主键', parent_id BIGINT COMMENT '测试父菜单标识', "
                    + "menu_name VARCHAR(100) COMMENT '测试菜单名称',path VARCHAR(100) COMMENT '测试菜单路由',component VARCHAR(200) COMMENT '测试页面组件', "
                    + "menu_type CHAR(1) COMMENT '测试菜单类型',perms VARCHAR(200) COMMENT '测试权限码',create_by VARCHAR(64) COMMENT '测试创建主体') COMMENT='测试菜单目录'");
            sql.execute("CREATE TABLE sys_role_menu(role_id BIGINT COMMENT '测试角色标识',menu_id BIGINT REFERENCES sys_menu(menu_id) COMMENT '测试菜单标识') COMMENT='测试角色菜单授权'");
            sql.execute("CREATE TABLE sys_user_table_preference(table_key VARCHAR(192) COMMENT '测试表格标识',url VARCHAR(512) COMMENT '测试页面路由') COMMENT='测试个人表格偏好'");
            sql.execute("INSERT INTO sys_menu VALUES"
                    + "(1,0,'TEST ONLY','resource',NULL,'M',NULL,'codegen'),"
                    + "(2,1,'TEST ONLY','personnel','resource/personnel/index','C','business:personnel:read','codegen'),"
                    + "(3,2,'TEST ONLY','#',NULL,'F','personnel:sync','codegen'),"
                    + "(4,2,'TEST ONLY','#',NULL,'F','business:directory:personnel:read','codegen'),"
                    + "(5,0,'TEST ONLY','equipment','basic/equipment/index','C','business:equipment:read','codegen'),"
                    + "(7,0,'TEST ONLY','resource',NULL,'M',NULL,'TEST_ONLY_USER')");
            if (sharedGroup) {
                sql.execute("INSERT INTO sys_menu VALUES(6,1,'TEST ONLY','other','other/index','C','TEST_ONLY:other:read','TEST_ONLY_USER')");
            }
            sql.execute("INSERT INTO sys_role_menu SELECT 99,menu_id FROM sys_menu");
            sql.execute("INSERT INTO sys_user_table_preference VALUES('business:personnel:list','/resource/personnel'),"
                    + "('business:equipment:list','/basic/equipment')");

            for (int repeat = 0; repeat < 2; repeat++) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/business/V6__remove_personnel_projection.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/system/V24__remove_personnel_capability.sql"));
            }

            assertEquals(0, count(sql, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='PERSONNEL'"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'business:personnel:%' OR perms LIKE 'personnel:%'"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_role_menu r LEFT JOIN sys_menu m ON r.menu_id=m.menu_id WHERE m.menu_id IS NULL"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=4 AND parent_id=0 AND path='business/directory/personnel'"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=99 AND menu_id=4"));
            assertEquals(sharedGroup ? 1 : 0, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=1"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=7"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM equipment"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_user"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_menu WHERE menu_id=5"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_user_table_preference"));
        }
    }

    private static int count(Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
