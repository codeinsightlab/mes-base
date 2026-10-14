package dev.mesbase.server;

import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** TEST ONLY H2 fixture; verifies removal boundaries independently of live MySQL. */
class DuplicateMasterDataRemovalMigrationTest {
    @Test
    void removesRetiredModelsAndGrantsButPreservesBasicModelsAndSharedMenus() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            for (String table : new String[] {"md_workshop", "md_unit_measure", "workshop", "unit_measure"}) {
                sql.execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY COMMENT '测试资料主键') COMMENT='测试基础资料'");
            }
            sql.execute("INSERT INTO workshop VALUES(1)");
            sql.execute("INSERT INTO unit_measure VALUES(1)");
            sql.execute("CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY COMMENT '测试菜单主键',"
                    + "parent_id BIGINT COMMENT '测试父菜单',component VARCHAR(200) COMMENT '测试页面组件',"
                    + "perms VARCHAR(200) COMMENT '测试权限码') COMMENT='测试菜单'");
            sql.execute("CREATE TABLE sys_role_menu(role_id BIGINT COMMENT '测试角色编号',"
                    + "menu_id BIGINT COMMENT '测试菜单编号') COMMENT='测试角色授权'");
            sql.execute("CREATE TABLE sys_user_table_preference(table_key VARCHAR(192) COMMENT '测试表格标识',"
                    + "url VARCHAR(512) COMMENT '测试页面路由') COMMENT='测试列偏好'");
            sql.execute("CREATE TABLE mes_master_sync(sync_key VARCHAR(96) COMMENT '测试同步标识') COMMENT='测试同步状态'");
            sql.execute("INSERT INTO sys_menu VALUES"
                    + "(1,0,NULL,NULL),"
                    + "(2,1,'md/mdWorkshop/index','business:md:workshop:list'),"
                    + "(3,2,NULL,'business:md:workshop:sync'),"
                    + "(4,1,'md/mdUnitMeasure/index','business:md:unit:measure:list'),"
                    + "(5,4,NULL,'business:md:unit:measure:delete'),"
                    + "(6,1,'basic/workshop/index','business:workshop:list'),"
                    + "(7,1,'basic/unitMeasure/index','business:unit:measure:list')");
            sql.execute("INSERT INTO sys_role_menu SELECT 99,menu_id FROM sys_menu");
            sql.execute("INSERT INTO sys_user_table_preference VALUES"
                    + "('business:md:workshop:list','/basic/mdWorkshop'),"
                    + "('business:md:unit:measure:list','/basic/mdUnitMeasure'),"
                    + "('business:workshop:list','/basic/workshop')");
            sql.execute("INSERT INTO mes_master_sync VALUES('codegen.md_workshop'),"
                    + "('codegen.md_unit_measure'),('codegen.workshop')");
            for (int repeat = 0; repeat < 2; repeat++) {
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/system/V28__remove_duplicate_master_data_capabilities.sql"));
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/business/V17__remove_duplicate_master_data_tables.sql"));
            }
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('MD_WORKSHOP','MD_UNIT_MEASURE')"));
            assertEquals(3, count(sql, "SELECT COUNT(*) FROM sys_menu"));
            assertEquals(3, count(sql, "SELECT COUNT(*) FROM sys_role_menu"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM sys_role_menu r LEFT JOIN sys_menu m ON m.menu_id=r.menu_id WHERE m.menu_id IS NULL"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM sys_user_table_preference WHERE table_key='business:workshop:list'"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM mes_master_sync WHERE sync_key='codegen.workshop'"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM workshop"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM unit_measure"));
        }
    }

    private static int count(Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
