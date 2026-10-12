package dev.mesbase;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.preference.TablePreferenceService;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.system.preference.TableColumnPreference;
import dev.mesbase.system.preference.TablePreferenceKey;
import dev.mesbase.system.preference.TablePreferenceMapper;
import dev.mesbase.web.controller.personal.TablePreferenceController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: actual preference migration/XML, Spring proxy transactions and HTTP interceptor on H2 MySQL mode.
 */
class TablePreferenceTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TablePreferenceService service;
    private JdbcTemplate jdbc;
    private final TablePreferenceKey key = new TablePreferenceKey("/material/material", "business:material:list");
    private final List<TableColumnPreference> columns = List.of(new TableColumnPreference("name", true), new TableColumnPreference("code", false));

    static void identity(String factory, long user) {
        FactoryContext.bind(new FactoryScope(new FactoryId(factory)));
        var entity = new SysUser();
        entity.setUserId(user);
        entity.setUserName("TEST_ONLY_" + user);
        var login = new LoginUser(user, 1L, entity, Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.setEnvironment(new MockEnvironment().withProperty("mes.system.enabled", "true"));
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        service = context.getBean(TablePreferenceService.class);
        jdbc = context.getBean(JdbcTemplate.class);
        identity("A", 1);
    }

    @AfterEach
    void clear() {
        context.close();
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void dimensionsSaveRefreshAndIdempotentResetAreIsolated() {
        assertNull(service.get(key));
        service.save(key, columns);
        assertEquals(columns, service.get(key));
        identity("B", 1);
        assertNull(service.get(key));
        service.save(key, List.of(new TableColumnPreference("id", true)));
        identity("A", 2);
        assertNull(service.get(key));
        service.save(key, List.of(new TableColumnPreference("code", true)));
        identity("A", 1);
        assertEquals(columns, service.get(key));
        assertNull(service.get(new TablePreferenceKey("/other", key.tableKey())));
        assertNull(service.get(new TablePreferenceKey(key.url(), "business:material:second")));
        service.save(key, List.of(new TableColumnPreference("code", true)));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_table_preference", Integer.class));
        service.reset(key);
        service.reset(key);
        assertNull(service.get(key));
        identity("B", 1);
        assertEquals(1, service.get(key).size());
    }

    @Test
    void concurrentUpsertsRelyOnUniqueConstraint() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var futures = IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                identity("A", 1);
                try {
                    start.await();
                    service.save(key, columns);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                } finally {
                    FactoryContext.clear();
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_table_preference", Integer.class));
        assertEquals(columns, service.get(key));
    }

    @Test
    void invalidIdentifiersDuplicatesAndAllHiddenAreRejected() {
        for (String url : List.of("https://host/page", "/page?q=1", "/page#anchor", "/page/", "/page//child", "/<script>"))
            assertThrows(RuntimeException.class, () -> service.get(new TablePreferenceKey(url, key.tableKey())));
        assertThrows(RuntimeException.class, () -> service.save(key, List.of(new TableColumnPreference("code", true), new TableColumnPreference("code", false))));
        assertThrows(RuntimeException.class, () -> service.save(key, List.of(new TableColumnPreference("code", false))));
        assertThrows(RuntimeException.class, () -> service.save(key, List.of(new TableColumnPreference("<script>", true))));
        FactoryContext.clear();
        assertThrows(RuntimeException.class, () -> service.get(key));
    }

    @Test
    void httpUsesTrustedIdentityAndStrictBooleanWithoutChangingPagePermissions() throws Exception {
        mvc.perform(get("/api/factory/table-preferences").param("url", key.url()).param("tableKey", key.tableKey())
                        .header("X-Factory-Id", "A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").doesNotExist());
        String body = "{\"url\":\"/material/material\",\"tableKey\":\"business:material:list\",\"userId\":999,\"factoryId\":\"B\",\"columns\":[{\"key\":\"code\",\"visible\":true}]}";
        mvc.perform(put("/api/factory/table-preferences").header("X-Factory-Id", "A")
                .contentType("application/json").content(body)).andExpect(status().isNoContent());
        assertEquals(1, service.get(key).size());
        mvc.perform(put("/api/factory/table-preferences").header("X-Factory-Id", "A")
                        .contentType("application/json").content(body.replace("true", "\"true\"")))
                .andExpect(status().isBadRequest());
        identity("B", 1);
        assertNull(service.get(key));
        identity("A", 999);
        assertNull(service.get(key));
        identity("A", 1);
        mvc.perform(delete("/api/factory/table-preferences").header("X-Factory-Id", "A")
                .param("url", key.url()).param("tableKey", key.tableKey())).andExpect(status().isNoContent());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/factory/table-preferences").param("url", key.url()).param("tableKey", key.tableKey()))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({TablePreferenceService.class, TablePreferenceController.class, FoundationErrorAdvice.class})
    static class Config implements WebMvcConfigurer {
        @Bean
        DriverManagerDataSource source() {
            return new DriverManagerDataSource("jdbc:h2:mem:pref_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbc(DriverManagerDataSource source) throws Exception {
            var jdbc = new JdbcTemplate(source);
            Path root = Path.of("").toAbsolutePath();
            if (!Files.exists(root.resolve("mes-admin"))) root = root.getParent();
            String migration = Files.readString(root.resolve("mes-admin/src/main/resources/db/system/V23__personal_table_preferences.sql"))
                    .replaceAll("(?i)COLLATE utf8mb4_bin", "")
                    .replaceAll("(?i)CHARACTER SET ascii COLLATE ascii_bin", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            jdbc.execute(migration);
            return jdbc;
        }

        @Bean
        OwnedTransactionManager masterTransactionManager(DriverManagerDataSource source, JdbcTemplate initialized) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        TablePreferenceMapper mapper(DriverManagerDataSource source, OwnedTransactionManager manager) {
            return new SqlSessionTemplate(PersistenceFactories.create(source, Set.of(TablePreferenceMapper.class), ignored -> {
            }, manager::verifyAccess))
                    .getMapper(TablePreferenceMapper.class);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            var catalog = new InMemoryFactoryCatalog(List.of(
                    new Factory(new FactoryId("A"), "TEST", "A", "测试工厂A", FactoryStatus.ENABLED),
                    new Factory(new FactoryId("B"), "TEST", "B", "测试工厂B", FactoryStatus.ENABLED)));
            registry.addInterceptor(new FoundationAccessInterceptor(new FactorySelectionService(catalog, new InMemoryFactoryPreference()), mock(AuthorizationService.class)));
        }
    }
}
