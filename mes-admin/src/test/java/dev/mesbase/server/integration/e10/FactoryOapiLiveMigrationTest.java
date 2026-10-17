package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.server.schema.FactoryFlyway;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import java.net.URI;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 仅在已核实中心库执行正式 Flyway；不发送 ERP 请求，不切厂、不写业务库。
 */
@EnabledIfEnvironmentVariable(named = "MES_FACTORY_OAPI_MIGRATION", matches = "true")
class FactoryOapiLiveMigrationTest {
    @Test
    void migrateConfiguredCenterWithVerifiedSimpleBBindingAndBackup() throws Exception {
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
        var source = new DriverManagerDataSource(url, environment.getRequiredProperty("mes.platform.username"), environment.getRequiredProperty("mes.platform.password"));
        var jdbc = new JdbcTemplate(source);
        assertEquals("mes_base_dev", jdbc.queryForObject("SELECT DATABASE()", String.class));
        int current = jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class);
        assertTrue(current == 37 || current == 38, "不自动执行其他未决迁移");
        var before = jdbc.queryForList("SELECT factory_id,runtime_mode,erp_provider,erp_config_ref,integration_revision,integration_locked,revision FROM mf_factory ORDER BY factory_id");
        Path backup = Path.of(".local/factory-oapi-20261017");
        Files.createDirectories(backup);
        Path data = backup.resolve("factory-before.json");
        if (!Files.exists(data)) {
            Files.writeString(data, new ObjectMapper().writeValueAsString(jdbc.queryForList("SELECT * FROM mf_factory")));
            Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("rw-------"));
            Files.writeString(backup.resolve("factory-schema-before.txt"), jdbc.queryForMap("SHOW CREATE TABLE mf_factory")
                    .toString());
        }
        var flyway = FactoryFlyway.configure(source, environment).target("38").load();
        flyway.migrate();
        flyway.validate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='38' AND success=1", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mf_factory' AND column_name IN ('oapi_config_ref','oapi_config_cipher','oapi_config_revision') AND column_comment<>''", Integer.class));
        assertEquals(before, jdbc.queryForList("SELECT factory_id,runtime_mode,erp_provider,erp_config_ref,integration_revision,integration_locked,revision FROM mf_factory ORDER BY factory_id"));
        if ("true".equals(System.getenv("MES_FACTORY_OAPI_READBACK"))) {
            var sourceYaml = new YamlPropertiesFactoryBean();
            sourceYaml.setResources(new FileSystemResource("/Users/user/work/heri/ktg-mes/ktg-admin/src/main/resources/application-dev.yml"));
            var expected = sourceYaml.getObject();
            var saved = jdbc.queryForMap("SELECT runtime_mode,erp_provider,erp_config_ref,oapi_config_cipher,oapi_config_revision FROM mf_factory WHERE factory_id='SIMPLE_B'");
            assertEquals("LOCAL", saved.get("runtime_mode"));
            assertNull(saved.get("erp_provider"));
            assertNull(saved.get("erp_config_ref"));
            assertEquals(1L, ((Number) saved.get("oapi_config_revision")).longValue());
            String encrypted = (String) saved.get("oapi_config_cipher");
            var payload = new ObjectMapper().readTree(new DatasourcePasswordCipher(environment.getRequiredProperty("mes.runtime.datasource-key")).decrypt(encrypted));
            assertEquals(expected.get("oapi.YWMS.url").toString(), payload.path("endpoint").asText());
            for (String field : new String[]{"ver", "prod", "timezone", "ip", "id", "lang", "acct"})
                assertEquals(expected.get("oapi.YWMS.host." + field).toString(), payload.path("host").path(field)
                        .asText());
            for (String field : new String[]{"prod", "ip", "id"})
                assertEquals(expected.get("oapi.YWMS.service." + field).toString(), payload.path("service").path(field)
                        .asText());
            assertFalse(encrypted.contains(payload.path("endpoint").asText()));
            assertFalse(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml"))
                    .contains("digiwin-e10:"));
        }

    }
}
