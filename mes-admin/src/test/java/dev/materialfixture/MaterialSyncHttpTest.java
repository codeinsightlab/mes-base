package dev.materialfixture;

import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;

import dev.mesbase.business.production.dao.RoutingProductMapper;

import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;

import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;

import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;

import dev.mesbase.business.wm.dao.WmStockActionMapper;

import dev.mesbase.business.wm.dao.WmInventoryMapper;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.business.basic.controller.MaterialController;
import dev.mesbase.business.basic.controller.MaterialPermissions;
import dev.mesbase.business.basic.service.BasicDataRules;
import dev.mesbase.business.basic.service.MaterialBomService;
import org.springframework.beans.factory.ObjectProvider;
import dev.mesbase.business.basic.service.MaterialUnitRules;
import dev.mesbase.business.basic.service.EquipmentReferenceRules;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.ibatis.session.SqlSessionFactory;
import dev.mesbase.business.basic.dao.MaterialCategoryMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.dao.WorkshopMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.basic.dao.SafetyStockMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialQueryPort;
import dev.mesbase.business.basic.service.impl.MaterialServiceImpl;
import dev.mesbase.business.basic.service.IMaterialService;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialQueryAdapter;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.system.sync.MasterSyncMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real interceptor, Spring services, generated XML and two isolated H2 business databases.
 * Identity/permission lookup is synthetic; this is not Redis or real ERP/deployment acceptance.
 */
class MaterialSyncHttpTest {
    static class Fixture implements AutoCloseable {
        final RuntimeRegistry registry = new RuntimeRegistry();
        final Map<String, JdbcTemplate> business = new HashMap<>();
        final InMemoryFactoryCatalog catalog;
        final ConfiguredBusinessPersistenceResolver resolver;
        final PlatformPersistence platform;
        List<ExternalMaterial> data = new TestMaterialQueryAdapter().query(null, 1000);
        int calls;

        Fixture() throws Exception {
            var center = source();
            new JdbcTemplate(center).execute("CREATE TABLE mes_master_sync(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "factory_id VARCHAR(64),sync_key VARCHAR(96),status VARCHAR(24) DEFAULT 'IDLE',"
                    + "run_token VARCHAR(40),started_at DATETIME(6),updated_at DATETIME(6),finished_at DATETIME(6),"
                    + "last_success_at DATETIME(6),cursor_time DATETIME(6),processed_count BIGINT DEFAULT 0,"
                    + "error_message VARCHAR(1000),UNIQUE(factory_id,sync_key))");
            platform = new PlatformPersistence(center, Set.of(MasterSyncMapper.class));
            catalog = new InMemoryFactoryCatalog(List.of(
                    factory("A"), factory("B"), factory("C")));
            Map<FactoryId, BusinessRuntimeBinding> bindings = new HashMap<>();
            String ddl = Files.readString(Path.of("mes-admin/src/main/resources/db/business/V2__simple_business_capabilities.sql"))
                    .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ")
                    .replaceAll("(?m)^\\s*factory_id [^\\n]+\\n", "")
                    .replace("factory_id,", "")
                    .replace("factory_id, ", "");
            for (String id : List.of("A", "B", "C")) {
                var source = source();
                var jdbc = new JdbcTemplate(source);
                for (String statement : ddl.split(";")) {
                    if (!statement.isBlank()) jdbc.execute(statement);
                }
                jdbc.execute("INSERT INTO unit_measure(code,name,status) VALUES('PCS','件','ENABLED'),('KG','千克','ENABLED')");
                jdbc.execute("ALTER TABLE material ADD CONSTRAINT fixture_reject_name CHECK(name <> 'DB_REJECT')");
                business.put(id, jdbc);
                var identity = new DatasourceIdentity("TEST_ONLY_" + id);
                var factoryId = new FactoryId(id);
                registry.registerDedicated(factoryId, identity, 1, source, () -> {
                }, Set.of(MaterialMapper.class, MaterialCategoryMapper.class, UnitMeasureMapper.class, WorkshopMapper.class, WorkstationMapper.class, SafetyStockMapper.class));
                bindings.put(factoryId, new BusinessRuntimeBinding(factoryId, identity, 1));
            }
            resolver = new ConfiguredBusinessPersistenceResolver(catalog, bindings);
        }

        static Factory factory(String id) {
            return new Factory(new FactoryId(id), "TEST_ONLY", id, "TEST ONLY " + id, FactoryStatus.ENABLED);
        }

        static DriverManagerDataSource source() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        public void close() {
            registry.close();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @Import({MaterialController.class, MaterialServiceImpl.class, BasicDataRules.class,
            FoundationErrorAdvice.class})
    static class Web implements WebMvcConfigurer {
        @Bean
        MaterialUnitRules metering(MaterialMapper materials, UnitMeasureMapper units,
                                   ObjectProvider<MaterialBomService> boms) {
            // Real metering locks; this fixture has no historical-fact tables.
            return new MaterialUnitRules(materials, units, mock(WmInventoryMapper.class),
                    mock(WmStockActionMapper.class), mock(WmStockDocumentLineMapper.class),
                    mock(MesWorkorderMaterialMapper.class), mock(MesMaterialIssueItemMapper.class),
                    mock(RoutingProductMapper.class), mock(WorkorderMapper.class), mock(MesProductionExecutionMapper.class), boms);
        }

        @Bean
        EquipmentReferenceRules equipmentReferences() {
            // This fixture exposes material synchronization, never resource assignment/deletion.
            return mock(EquipmentReferenceRules.class);
        }

        @Bean
        BusinessCacheProcessor businessCaches() {
            return mock(BusinessCacheProcessor.class);
        }

        @Bean(destroyMethod = "close")
        Fixture fixture() throws Exception {
            return new Fixture();
        }

        @Bean
        SyncCoordinator coordinator(Fixture fixture) {
            return new SyncCoordinator(fixture.platform, fixture.platform.mapper(MasterSyncMapper.class), fixture.catalog);
        }

        @Bean
        FactoryBusinessDataSource businessSource(Fixture fixture) {
            return new FactoryBusinessDataSource(fixture.resolver, fixture.registry);
        }

        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager businessManager(FactoryBusinessDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory businessSessions(FactoryBusinessDataSource source, OwnedTransactionManager manager) {
            return PersistenceFactories.create(source, Set.of(MaterialMapper.class, MaterialCategoryMapper.class, UnitMeasureMapper.class, WorkshopMapper.class, WorkstationMapper.class, SafetyStockMapper.class), configuration -> {
            }, manager::verifyAccess);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        MaterialMapper materialMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(MaterialMapper.class);
        }

        @Bean
        MaterialCategoryMapper materialCategoryMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(MaterialCategoryMapper.class);
        }

        @Bean
        UnitMeasureMapper unitMeasureMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(UnitMeasureMapper.class);
        }

        @Bean
        WorkshopMapper workshopMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(WorkshopMapper.class);
        }

        @Bean
        WorkstationMapper workstationMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(WorkstationMapper.class);
        }

        @Bean
        SafetyStockMapper safetyStockMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(SafetyStockMapper.class);
        }

        @Bean
        MaterialQueryPort adapter(Fixture fixture) {
            return (scope, limit) -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "ERP query must precede business transaction");
                if ("C".equals(scope.factoryId().value()))
                    return new UnconfiguredMaterialQueryAdapter().query(scope, limit);
                fixture.calls++;
                return fixture.data;
            };
        }

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            var tokens = mock(TokenService.class);
            when(tokens.getLoginUser(any())).thenAnswer(call -> {
                var request = call.getArgument(0, HttpServletRequest.class);
                String token = request.getHeader("Authorization");
                if (!"Bearer TEST_ALLOW".equals(token) && !"Bearer TEST_DENY".equals(token)) return null;
                var user = new SysUser();
                user.setUserId(1L);
                user.setUserName("Bearer TEST_ALLOW".equals(token) ? "allowed" : "denied");
                return new LoginUser(1L, 1L, user, Set.of());
            });
            return http.csrf(config -> config.disable())
                    .addFilterBefore(new SystemTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(config -> config.anyRequest().authenticated())
                    .exceptionHandling(config -> config.authenticationEntryPoint((request, response, failure) -> response.setStatus(401)))
                    .build();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            try {
                var fixture = fixture();
                registry.addInterceptor(new FoundationAccessInterceptor(
                        new FactorySelectionService(fixture.catalog, new InMemoryFactoryPreference()),
                        (actor, scope, permission) -> "allowed".equals(actor.id())
                                && MaterialPermissions.SYNC.equals(permission.code())));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY", Map.of(
                "mes.business.enabled", "true", "mes.material.erp.bindings.A", "TEST", "mes.material.erp.bindings.B", "TEST")));
        context.register(Web.class);
        context.refresh();
        return context;
    }

    static MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestBoundaryFilter(), context.getBean(FilterChainProxy.class)).build();
    }

    static void sync(MockMvc mvc, String factory, int created, int updated, int skipped) throws Exception {
        mvc.perform(post("/api/basic/material/actions/sync").header("Authorization", "Bearer TEST_ALLOW")
                        .header("X-Factory-Id", factory))
                .andExpect(status().isOk()).andExpect(jsonPath("fetched").value(created + updated + skipped))
                .andExpect(jsonPath("created").value(created)).andExpect(jsonPath("updated").value(updated))
                .andExpect(jsonPath("skipped").value(skipped));
    }

    @Test
    void insertsRepeatsUpdatesAndSeparatesFactories() throws Exception {
        try (var context = context()) {
            var mvc = mvc(context);
            var fixture = context.getBean(Fixture.class);
            sync(mvc, "A", 2, 0, 0);
            sync(mvc, "A", 0, 0, 2);
            sync(mvc, "B", 2, 0, 0);
            var old = fixture.data.getFirst();
            fixture.data = List.of(new ExternalMaterial(old.code(), "测试更新", null, old.categoryId(), old.unitId(), old.status(), old.externalId(), old.externalCode()), fixture.data.get(1));
            sync(mvc, "A", 0, 1, 1);
            assertEquals(2, fixture.business.get("A").queryForObject("SELECT COUNT(*) FROM material", Integer.class));
            assertEquals("测试更新", fixture.business.get("A")
                    .queryForObject("SELECT name FROM material WHERE code='M001'", String.class));
            assertEquals("测试物料一", fixture.business.get("B")
                    .queryForObject("SELECT name FROM material WHERE code='M001'", String.class));
            assertEquals(null, fixture.business.get("A")
                    .queryForObject("SELECT specification FROM material WHERE code='M001'", String.class));
        }
    }

    @Test
    void permissionAndConfigurationAreIndependentAndFailClearly() throws Exception {
        try (var context = context()) {
            var mvc = mvc(context);
            var fixture = context.getBean(Fixture.class);
            mvc.perform(post("/api/basic/material/actions/sync").header("Authorization", "Bearer TEST_DENY")
                            .header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("errorCode").value("FORBIDDEN"));
            mvc.perform(post("/api/basic/material/actions/sync").header("Authorization", "Bearer TEST_ALLOW")
                            .header("X-Factory-Id", "C"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("ERP_SYNC_NOT_CONFIGURED"));
            assertEquals(0, fixture.calls);
            assertEquals(0, fixture.business.get("A").queryForObject("SELECT COUNT(*) FROM material", Integer.class));
            assertThrows(FoundationException.class, () -> context.getBean(IMaterialService.class)
                    .sync(new FactoryScope(new FactoryId("A"))));
        }
    }

    @Test
    void duplicateBusinessKeysAndSqlFailuresNeverLeaveHalfABatch() throws Exception {
        try (var context = context()) {
            var fixture = context.getBean(Fixture.class);
            var mvc = mvc(context);
            fixture.data = List.of(new ExternalMaterial("Z001", "valid", null, null, 1L, "ENABLED", "1", "1"),
                    new ExternalMaterial("Z002", "DB_REJECT", null, null, 1L, "ENABLED", "2", "2"));
            mvc.perform(post("/api/basic/material/actions/sync").header("Authorization", "Bearer TEST_ALLOW")
                            .header("X-Factory-Id", "A"))
                    .andExpect(status().isInternalServerError());
            assertEquals(0, fixture.business.get("A").queryForObject("SELECT COUNT(*) FROM material", Integer.class));
            fixture.data = List.of(fixture.data.getFirst(), fixture.data.getFirst());
            mvc.perform(post("/api/basic/material/actions/sync").header("Authorization", "Bearer TEST_ALLOW")
                            .header("X-Factory-Id", "A"))
                    .andExpect(status().isBadRequest());
            assertEquals(0, fixture.business.get("A").queryForObject("SELECT COUNT(*) FROM material", Integer.class));
        }
    }
}
