package dev.mesbase;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.security.InMemoryRoleAuthorization;
import dev.mesbase.infrastructure.catalog.DatabaseFactoryPreference;
import dev.mesbase.system.factory.ManagedFactoryExtraMapper;
import dev.mesbase.system.factory.MfUserPreferenceMapper;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: actual production mapper XML against isolated H2 MySQL mode, not live MySQL evidence.
 */
class FactoryPreferenceMigrationTest {
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistedPreferenceUsesStableAuthenticatedIdAndSurvivesAdapterReplacement() throws Exception {
        var fixture = fixture();
        fixture.jdbc()
                .execute("CREATE TABLE mf_user_preference (user_id BIGINT PRIMARY KEY, last_factory_id VARCHAR(64) NOT NULL)");
        var mapper = fixture.sessions().getMapper(MfUserPreferenceMapper.class);
        var preference = new DatabaseFactoryPreference(mapper);
        authenticate(7L, "original");
        preference.save(new AuthenticatedActor("original"), new FactoryId("10"));
        preference.save(new AuthenticatedActor("original"), new FactoryId("2"));
        authenticate(7L, "renamed");
        assertEquals(Optional.of(new FactoryId("2")),
                new DatabaseFactoryPreference(mapper).lastFactory(new AuthenticatedActor("renamed")));
        authenticate(8L, "other");
        assertTrue(preference.lastFactory(new AuthenticatedActor("other")).isEmpty());
        assertThrows(FoundationException.class,
                () -> preference.save(new AuthenticatedActor("renamed"), new FactoryId("10")));
        SecurityContextHolder.clearContext();
        assertThrows(FoundationException.class,
                () -> preference.lastFactory(new AuthenticatedActor("other")));
        assertEquals(1, fixture.jdbc().queryForObject("SELECT COUNT(*) FROM mf_user_preference", Integer.class));
    }

    @Test
    void preferenceIsProxiedByMasterTransactionsAndRollbackDoesNotChangeSavedValue() throws Exception {
        var fixture = fixture();
        fixture.jdbc()
                .execute("CREATE TABLE mf_user_preference (user_id BIGINT PRIMARY KEY, last_factory_id VARCHAR(64) NOT NULL)");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MfUserPreferenceMapper.class,
                    () -> fixture.sessions().getMapper(MfUserPreferenceMapper.class));
            context.registerBean("masterTransactionManager", PlatformTransactionManager.class,
                    () -> new DataSourceTransactionManager(fixture.jdbc().getDataSource()));
            context.register(PreferenceConfiguration.class);
            context.refresh();
            var preference = context.getBean(FactoryPreference.class);
            assertTrue(AopUtils.isAopProxy(preference));
            authenticate(7L, "owner");
            var actor = new AuthenticatedActor("owner");
            preference.save(actor, new FactoryId("10"));
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
                preference.save(actor, new FactoryId("2"));
                throw new IllegalStateException("TEST ONLY rollback");
            }));
            assertEquals(Optional.of(new FactoryId("10")), preference.lastFactory(actor));
        }
    }

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    static class PreferenceConfiguration {
        @Bean
        FactoryPreference preference(MfUserPreferenceMapper mapper) {
            return new DatabaseFactoryPreference(mapper);
        }
    }

    @Test
    void defaultUsesEnabledBusinessBindingsAndNumericFactoryIdRatherThanDatasourceId() throws Exception {
        var fixture = fixture();
        var jdbc = fixture.jdbc();
        jdbc.execute("CREATE TABLE mf_datasource (datasource_id BIGINT PRIMARY KEY, status VARCHAR(16))");
        jdbc.execute("CREATE TABLE mf_factory (factory_id VARCHAR(64) PRIMARY KEY, enterprise_id VARCHAR(64), factory_code VARCHAR(64), factory_name VARCHAR(128), datasource_id BIGINT, status VARCHAR(16), revision BIGINT)");
        var mapper = fixture.sessions().getMapper(ManagedFactoryExtraMapper.class);
        assertEquals(null, mapper.defaultBusinessFactory());
        jdbc.update("INSERT INTO mf_datasource VALUES (100, 'ENABLED'), (1, 'ENABLED'), (2, 'DISABLED')");
        insert(jdbc, "10", 1L, "ENABLED");
        assertEquals("10", mapper.defaultBusinessFactory().factoryId());
        insert(jdbc, "2", 100L, "ENABLED");
        insert(jdbc, "1", 2L, "ENABLED");
        insert(jdbc, "0", null, "ENABLED");
        insert(jdbc, "00", 1L, "DISABLED");
        insert(jdbc, "A", 1L, "ENABLED");
        assertEquals("2", mapper.defaultBusinessFactory().factoryId());
        insert(jdbc, "9999999999999999999999999999999999999999999999999999999999999999", 1L, "ENABLED");
        assertEquals("2", mapper.defaultBusinessFactory().factoryId());
        jdbc.update("UPDATE mf_datasource SET status='DISABLED' WHERE datasource_id=100");
        assertEquals("10", mapper.defaultBusinessFactory().factoryId());
        jdbc.update("UPDATE mf_datasource SET status='DISABLED'");
        assertEquals(null, mapper.defaultBusinessFactory());
    }

    @Test
    void validPreferenceAndExplicitScopeWinAndDefaultDoesNotWritePreference() {
        var preferences = new InMemoryFactoryPreference();
        var actor = new AuthenticatedActor("TEST_ONLY_no_roles");
        Factory two = factory("2", FactoryStatus.ENABLED);
        Factory ten = factory("10", FactoryStatus.ENABLED);
        Factory disabled = factory("1", FactoryStatus.DISABLED);
        FactoryCatalog catalog = new FactoryCatalog() {
            public Optional<Factory> find(FactoryId id) {
                return List.of(two, ten, disabled).stream().filter(f -> f.id().equals(id)).findFirst();
            }

            public List<Factory> enabled(int offset, int limit) {
                return List.of(two, ten);
            }

            public Optional<Factory> defaultFactory() {
                return Optional.of(two);
            }
        };
        var selection = new FactorySelectionService(catalog, preferences);
        assertEquals("2", selection.resolve(actor, Optional.empty()).orElseThrow().factoryId().value());
        assertTrue(preferences.lastFactory(actor).isEmpty());
        preferences.save(actor, ten.id());
        assertEquals("10", selection.resolve(actor, Optional.empty()).orElseThrow().factoryId().value());
        assertEquals("2", selection.resolve(actor, Optional.of(two.id())).orElseThrow().factoryId().value());
        for (String invalid : List.of("missing", "1")) {
            preferences.save(actor, new FactoryId(invalid));
            assertEquals("2", selection.resolve(actor, Optional.empty()).orElseThrow().factoryId().value());
        }
        assertFalse(new InMemoryRoleAuthorization().hasPermission(
                actor, selection.select(actor, two.id()), new Permission("read")));
    }

    private Factory factory(String id, FactoryStatus status) {
        return new Factory(new FactoryId(id), "TEST_ONLY", id, "TEST ONLY", status);
    }

    private void authenticate(long id, String username) {
        var user = new SysUser();
        user.setUserName(username);
        var login = new LoginUser(id, null, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    private void insert(JdbcTemplate jdbc, String id, Long datasource, String status) {
        jdbc.update("INSERT INTO mf_factory VALUES (?, 'TEST_ONLY', ?, 'TEST ONLY', ?, ?, 1)",
                id, id, datasource, status);
    }

    private Fixture fixture() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var configuration = new Configuration();
        configuration.addMapper(MfUserPreferenceMapper.class);
        configuration.addMapper(ManagedFactoryExtraMapper.class);
        var bean = new SqlSessionFactoryBean();
        bean.setDataSource(source);
        bean.setConfiguration(configuration);
        SqlSessionFactory factory = bean.getObject();
        return new Fixture(new JdbcTemplate(source), new SqlSessionTemplate(factory));
    }

    private record Fixture(JdbcTemplate jdbc, SqlSessionTemplate sessions) {
    }
}
