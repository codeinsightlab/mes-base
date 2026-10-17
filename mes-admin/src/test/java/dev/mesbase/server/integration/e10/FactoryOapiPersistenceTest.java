package dev.mesbase.server.integration.e10;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import dev.mesbase.server.managed.FactoryMetadataCache;
import dev.mesbase.system.factory.ManagedFactory;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * H2 真实 SQL fixture，不代表真实 E10 或用户验收库。
 */
class FactoryOapiPersistenceTest {
    private JdbcTemplate jdbc;
    private PlatformPersistence persistence;
    private ManagedFactoryMapper mapper;
    private FactoryOapiService service;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE mf_factory(factory_id VARCHAR(64) PRIMARY KEY,enterprise_id VARCHAR(64),factory_code VARCHAR(64),factory_name VARCHAR(64),datasource_id BIGINT,status VARCHAR(16),revision BIGINT,runtime_mode VARCHAR(16),erp_provider VARCHAR(128),erp_config_ref VARCHAR(128),integration_revision BIGINT,integration_locked BOOLEAN)");
        new ResourceDatabasePopulator(new ClassPathResource("db/system/V38__factory_oapi_configuration.sql")).execute(source);
        jdbc.update("INSERT INTO mf_factory(factory_id,revision,runtime_mode,integration_locked,integration_revision) VALUES('A',1,'LOCAL',TRUE,1),('B',1,'LOCAL',FALSE,1)");
        persistence = new PlatformPersistence(source, Set.of(ManagedFactoryMapper.class));
        mapper = persistence.mapper(ManagedFactoryMapper.class);
        service = new FactoryOapiService(mapper, persistence, new DatasourcePasswordCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), mock(FactoryMetadataCache.class));
    }

    static FactoryOapiSettings settings() {
        return new FactoryOapiSettings("https://erp.invalid/oapi",
                new FactoryOapiSettings.Host("5.7", "TEST_CALLER", "8", "127.0.0.1", "", "zh_CN", "TEST_ACCOUNT"),
                new FactoryOapiSettings.Service("TEST_ERP", "127.0.0.1", "TEST_SERVICE"));
    }

    private FactoryOapiService.View save(String id, long revision, FactoryOapiSettings settings) {
        return persistence.run(() -> service.save(id, new FactoryOapiService.Form(revision, settings)));
    }

    @Test
    void encryptedFactoryConfigurationSurvivesReloadWithoutChangingModeAndRejectsConcurrentOldRevision() {
        assertNull(service.get("A").settings());
        var saved = save("A", 1, settings());
        assertEquals(2, saved.factoryRevision());
        assertEquals(1, saved.configurationRevision());
        assertEquals(settings(), service.get("A").settings());
        assertFalse(saved.locked());
        assertEquals("LOCAL", mapper.find("A").runtimeMode());
        assertTrue(mapper.find("A").integrationLocked());
        assertNull(mapper.find("A").erpConfigRef());
        String encrypted = jdbc.queryForObject("SELECT oapi_config_cipher FROM mf_factory WHERE factory_id='A'", String.class);
        assertFalse(encrypted.contains("TEST_ACCOUNT"));
        assertFalse(encrypted.contains("erp.invalid"));
        assertNull(service.connection("B"));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class, () -> save("A", 1, settings())).code());
        assertEquals(saved.reference(), save("A", 2, settings()).reference());
        assertEquals(2, service.get("A").factoryRevision(), "相同参数保存不产生空版本");
    }

    @Test
    void registryUsesDatabaseChoiceAndIgnoresLegacyE10YamlAndRequiresExactFactoryBinding() {
        var saved = save("A", 1, settings());
        var registry = new FactoryIntegrationRegistry(new MockEnvironment(), service);
        assertEquals(saved.reference(), registry.choices("A").getFirst().reference());
        assertTrue(registry.choices("B").isEmpty());
        var factory = new ManagedFactory("A", "T", "A", "A", null, "ENABLED", 2, "ERP", DigiwinE10MoQueries.PROVIDER, saved.reference(), 2, true);
        assertEquals(1L, registry.snapshot(factory).configurationRevision());
        var other = new ManagedFactory("B", "T", "B", "B", null, "ENABLED", 2, "ERP", DigiwinE10MoQueries.PROVIDER, saved.reference(), 2, true);
        assertThrows(FoundationException.class, () -> registry.snapshot(other));
        jdbc.update("UPDATE mf_factory SET runtime_mode='ERP',erp_provider='DIGIWIN_E10',erp_config_ref=? WHERE factory_id='A'", saved.reference());
        assertTrue(service.get("A").locked());
        assertEquals(ErrorCode.FACTORY_MODE_CHANGE_REQUIRES_MIGRATION, assertThrows(FoundationException.class, () -> save("A", 2, settings())).code());
    }

    @Test
    void invalidInputMissingFactoryAndCorruptedCipherFailClosed() {
        assertThrows(FoundationException.class, () -> service.get("MISSING"));
        assertThrows(FoundationException.class, () -> save("A", 1, new FactoryOapiSettings("https://user:pass@erp.invalid", settings().host(), settings().service())));
        assertNull(service.connection("A"));
        save("A", 1, settings());
        jdbc.update("UPDATE mf_factory SET oapi_config_cipher='broken' WHERE factory_id='A'");
        assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED, assertThrows(FoundationException.class, () -> service.connection("A")).code());
    }

    @Test
    void rollbackKeepsFactoryConfigurationAndRevisionUnchanged() {
        assertThrows(IllegalStateException.class, () -> persistence.run(() -> {
            service.save("A", new FactoryOapiService.Form(1, settings()));
            throw new IllegalStateException("TEST ONLY ROLLBACK");
        }));
        assertNull(service.connection("A"));
        assertEquals(1, service.get("A").factoryRevision());
    }
}
