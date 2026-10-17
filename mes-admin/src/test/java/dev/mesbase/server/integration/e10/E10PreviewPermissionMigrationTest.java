package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.server.schema.FactoryFlyway;
import java.net.URI;
import java.time.temporal.TemporalAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: 已配置中心库正式 Flyway V39，不改变授权、模式、绑定或业务数据。
 */
@EnabledIfEnvironmentVariable(named = "MES_E10_V20_MIGRATION", matches = "true")
class E10PreviewPermissionMigrationTest {
    @Test
    void registerOnlySyncPermissionAfterVerifyingTargetAndBackingUp() throws Exception {
        var business = new JdbcTemplate(AcceptanceBusinessDatabase.source());
        assertEquals("mes_base_dev_business_simple_b", business.queryForObject("SELECT DATABASE()", String.class));
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("mes-admin/src/main/resources/application-dev.yml"));
        var environment = new MockEnvironment();
        environment.getPropertySources()
                .addLast(new PropertiesPropertySource("private developer configuration", yaml.getObject()));
        String url = environment.getRequiredProperty("mes.platform.url");
        var uri = URI.create(url.substring(5));
        assertEquals("192.168.18.25", uri.getHost());
        assertEquals(3306, uri.getPort());
        assertEquals("/mes_base_dev", uri.getPath());
        var source = new DriverManagerDataSource(url, environment.getRequiredProperty("mes.platform.username"),
                environment.getRequiredProperty("mes.platform.password"));
        var jdbc = new JdbcTemplate(source);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mf_factory f JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id='SIMPLE_B' AND d.datasource_id=8 AND d.db_host='192.168.18.25' AND d.db_port=3306 AND d.db_name='mes_base_dev_business_simple_b' AND f.status='ENABLED' AND d.status='ENABLED'", Integer.class));
        int version = jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class);
        assertTrue(version == 38 || version == 39, "不执行任务外未决迁移");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='workorder:list' AND menu_type='C'", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='workorder:sync'", Integer.class) <= 1);
        var factories = jdbc.queryForList("SELECT factory_id,runtime_mode,erp_provider,erp_config_ref,integration_revision,integration_locked,revision FROM mf_factory ORDER BY factory_id");
        var grants = jdbc.queryForList("SELECT role_id,menu_id FROM sys_role_menu ORDER BY role_id,menu_id");
        Path backup = Path.of(".local/e10-v20-20261017/center-menu-before.json");
        Files.createDirectories(backup.getParent());
        if (!Files.exists(backup)) {
            var menuRows = jdbc.queryForList("SELECT * FROM sys_menu ORDER BY menu_id");
            menuRows.forEach(row -> row.replaceAll((key, value) -> value instanceof TemporalAccessor ? value.toString() : value));
            Files.writeString(backup, new ObjectMapper().writeValueAsString(menuRows));
            Files.setPosixFilePermissions(backup, PosixFilePermissions.fromString("rw-------"));
        }
        var flyway = FactoryFlyway.configure(source, environment).target("39").load();
        flyway.migrate();
        flyway.validate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='39' AND success=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='workorder:sync' AND menu_type='F' AND create_by='e10-v20'", Integer.class));
        assertEquals(grants, jdbc.queryForList("SELECT role_id,menu_id FROM sys_role_menu ORDER BY role_id,menu_id"));
        assertEquals(factories, jdbc.queryForList("SELECT factory_id,runtime_mode,erp_provider,erp_config_ref,integration_revision,integration_locked,revision FROM mf_factory ORDER BY factory_id"));
        assertEquals("LOCAL", jdbc.queryForObject("SELECT runtime_mode FROM mf_factory WHERE factory_id='SIMPLE_B'", String.class));
    }
}
