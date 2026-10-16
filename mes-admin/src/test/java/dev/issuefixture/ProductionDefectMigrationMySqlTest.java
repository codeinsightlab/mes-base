package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import org.flywaydb.core.Flyway;
import org.yaml.snakeyaml.Yaml;
import java.util.Map;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LIVE MYSQL: explicitly verified existing SIMPLE_B binding; no application server lifecycle.
 */
@EnabledIfEnvironmentVariable(named = "MES_DEFECT_MYSQL_ACCEPTANCE", matches = "true")
class ProductionDefectMigrationMySqlTest {
    @Test
    void migrateVerifiedCenterMenu() throws Exception {
        // Verify the designated Factory binding first, then use exactly its configured center.
        AcceptanceBusinessDatabase.source();
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("platform");
        var source = new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"));
        var jdbc = new JdbcTemplate(source);
        var backup = Path.of(".local/production-defect-20261016/center-menu-preflight.txt");
        if (!Files.exists(backup))
            Files.writeString(backup, jdbc.queryForList("SELECT menu_id,parent_id,path,component,perms FROM sys_menu WHERE path='quality' OR perms LIKE 'business:quality:defect:%'")
                    .toString());
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/platform", "classpath:db/system")
                .load();
        flyway.migrate();
        flyway.validate();
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'business:quality:defect:%'", Integer.class));
    }

    @Test
    void migrateDesignatedBusinessDatabase() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        var jdbc = new JdbcTemplate(source);
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("platform");
        var center = new JdbcTemplate(new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password")));
        var binding = center.queryForMap("SELECT f.factory_id,d.datasource_id,d.db_host,d.db_port,d.db_name,f.status AS factory_status,d.status AS datasource_status FROM mf_factory f JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id='SIMPLE_B'");
        Files.writeString(Path.of(".local/production-defect-20261016/binding.txt"), binding.toString());
        var backup = Path.of(".local/production-defect-20261016/schema-preflight.txt");
        Files.createDirectories(backup.getParent());
        var existing = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='mes_production_defect'");
        if (existing.isEmpty())
            Files.writeString(backup, "SIMPLE_B:192.168.18.25:3306/mes_base_dev_business_simple_b\nmes_production_defect absent; no existing rows affected\n" + jdbc.queryForList("SELECT version,description,success FROM flyway_business_schema_history ORDER BY installed_rank"));
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/business")
                .table("flyway_business_schema_history").load();
        flyway.migrate();
        flyway.validate();
        assertEquals(24, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_production_defect'", Integer.class));
    }
}
