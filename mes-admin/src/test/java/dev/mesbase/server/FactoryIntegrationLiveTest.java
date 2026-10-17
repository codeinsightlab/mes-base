package dev.mesbase.server;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.ManagedDatasourceService.FactoryForm;
import dev.mesbase.server.schema.FactoryFlyway;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: existing verified SIMPLE_B binding; no server lifecycle or external ERP calls.
 */
@EnabledIfEnvironmentVariable(named = "MES_FACTORY_INTEGRATION_ACCEPTANCE", matches = "true")
class FactoryIntegrationLiveTest {
    @Test
    void migrateVerifiedCenterAndVerifyRealConfigurationPersistence() throws Exception {
        var business = new JdbcTemplate(AcceptanceBusinessDatabase.source());
        assertEquals("mes_base_dev_business_simple_b", business.queryForObject("SELECT DATABASE()", String.class));
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("mes-admin/src/main/resources/application-dev.yml"));
        var environment = FactoryIntegrationConfigurationTest.environment();
        environment.getPropertySources()
                .addLast(new PropertiesPropertySource("verified developer configuration", yaml.getObject()));
        var source = new DriverManagerDataSource(environment.getRequiredProperty("mes.platform.url"),
                environment.getRequiredProperty("mes.platform.username"), environment.getRequiredProperty("mes.platform.password"));
        var jdbc = new JdbcTemplate(source);
        var backup = Path.of(".local/factory-integration-20261017");
        Files.createDirectories(backup);
        String id = "TEST_FACTORY_INT_20261017";
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mf_factory WHERE factory_id=?", Integer.class, id));
        var snapshot = backup.resolve("factory-before.txt");
        if (!Files.exists(snapshot))
            Files.writeString(snapshot, jdbc.queryForList("SELECT * FROM mf_factory").toString());
        Files.writeString(backup.resolve("verified-binding.txt"), jdbc.queryForList("SELECT f.factory_id,d.datasource_id,d.db_host,d.db_port,d.db_name FROM mf_factory f JOIN mf_datasource d ON f.datasource_id=d.datasource_id WHERE f.factory_id='SIMPLE_B'")
                .toString());
        var flyway = FactoryFlyway.configure(source, environment).target("34").load();
        flyway.migrate();
        flyway.validate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='34' AND success=1", Integer.class));
        assertEquals("LOCAL", jdbc.queryForObject("SELECT runtime_mode FROM mf_factory WHERE factory_id='SIMPLE_B'", String.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mf_factory' AND column_name IN ('runtime_mode','erp_provider','erp_config_ref','integration_revision','integration_locked') AND column_comment<>''", Integer.class));
        var persistence = new PlatformPersistence(source, Set.of(ManagedFactoryMapper.class, ManagedDatasourceMapper.class, RoutingMetadataMapper.class));
        try (var runtimes = new RuntimeRegistry()) {
            var service = new ManagedDatasourceService(persistence, persistence.mapper(ManagedDatasourceMapper.class), persistence.mapper(ManagedFactoryMapper.class), null, runtimes, source, null, Set.of(), backup,
                    RoutingCacheFixture.cache(persistence), new FactoryIntegrationRegistry(environment));
            boolean created = false;
            try {
                var local = service.saveFactory(new FactoryForm(id, "TEST ONLY", id, "合成验收工厂", null, "DISABLED", null), true);
                created = true;
                assertEquals("LOCAL", local.runtimeMode());
                assertFalse(local.integrationLocked());
                // Give the synthetic configuration its exact own Factory association, without changing real SIMPLE_B.
                environment.setProperty("mes.integration.connections.test-a.factory-id", id);
                var registry = new FactoryIntegrationRegistry(environment);
                service = new ManagedDatasourceService(persistence, persistence.mapper(ManagedDatasourceMapper.class), persistence.mapper(ManagedFactoryMapper.class), null, runtimes, source, null, Set.of(), backup,
                        RoutingCacheFixture.cache(persistence), registry);
                var erp = service.saveFactory(new FactoryForm(id, "TEST ONLY", id, "合成验收工厂", null, "DISABLED", local.revision(), "ERP", "DINGJIE", "test-a"), false);
                assertEquals("DINGJIE", erp.erpProvider());
                assertEquals("test-a", service.factory(id).erpConfigRef());
                assertEquals(2, erp.integrationRevision());
                var current = service;
                assertThrows(RuntimeException.class, () -> current.saveFactory(new FactoryForm(id, "TEST ONLY", id, "合成验收工厂", null, "DISABLED", erp.revision(), "ERP", "SAP", "test-a"), false));
                var realScope = new FactoryScope(new FactoryId("SIMPLE_B"));
                service.runIntegrated(realScope, () -> assertEquals("LOCAL", FactoryIntegrationContext.required().mode()
                        .name()));
                assertTrue(FactoryIntegrationContext.current().isEmpty());
                Files.writeString(backup.resolve("verification.txt"), "Flyway V34 applied and validated; 5 Chinese-commented center columns; SIMPLE_B LOCAL preserved; synthetic default LOCAL and ERP provider/reference readback passed; invalid provider rejected; explicit worker context cleared.\n" + jdbc.queryForList("SELECT factory_id,runtime_mode,erp_provider,erp_config_ref,integration_revision,integration_locked FROM mf_factory")
                        .toString());
            } finally {
                if (created) service.deleteFactory(id, service.factory(id).revision());
            }
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mf_factory WHERE factory_id=?", Integer.class, id));
        }
    }
}
