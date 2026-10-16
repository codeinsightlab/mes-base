package dev.stockfixture;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.wm.controller.WmInventoryController;
import dev.mesbase.business.wm.controller.WmWarehouseController;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import dev.mesbase.business.wm.service.impl.WmWarehouseServiceImpl;
import dev.mesbase.business.wm.sync.ExternalWmInventory;
import dev.mesbase.business.wm.sync.ExternalWmWarehouse;
import dev.mesbase.business.wm.sync.WmWarehouseQueryPort;
import dev.mesbase.business.wm.sync.UnconfiguredWmWarehouseQueryAdapter;
import dev.mesbase.business.wm.sync.WmInventoryQueryPort;
import dev.mesbase.business.wm.sync.UnconfiguredWmInventoryQueryAdapter;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
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
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.system.sync.MasterSyncMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
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
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: real generated SQL, HTTP authorization and transactions; two separate H2 databases.
 */
class StockHttpTest {
    static final Set<Class<?>> MAPPERS = Set.of(MaterialMapper.class, WmWarehouseMapper.class, WmInventoryMapper.class);

    static class Fixture implements AutoCloseable {
        final RuntimeRegistry registry = new RuntimeRegistry();
        final Map<String, JdbcTemplate> business = new HashMap<>();
        final InMemoryFactoryCatalog catalog;
        final ConfiguredBusinessPersistenceResolver resolver;
        final PlatformPersistence platform;
        final JdbcTemplate center;
        int ownerChecks;
        int loseAt;
        List<ExternalWmWarehouse> warehouseInputs = List.of();
        List<ExternalWmInventory> inputs = List.of();

        Fixture() throws Exception {
            var center = source();
            this.center = new JdbcTemplate(center);
            new JdbcTemplate(center).execute("CREATE TABLE mes_master_sync(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "factory_id VARCHAR(64),sync_key VARCHAR(96),status VARCHAR(24) DEFAULT 'IDLE',"
                    + "run_token VARCHAR(40),started_at DATETIME(6),updated_at DATETIME(6),finished_at DATETIME(6),"
                    + "last_success_at DATETIME(6),cursor_time DATETIME(6),processed_count BIGINT DEFAULT 0,"
                    + "error_message VARCHAR(1000),UNIQUE(factory_id,sync_key))");
            platform = new PlatformPersistence(center, Set.of(MasterSyncMapper.class));
            catalog = new InMemoryFactoryCatalog(List.of(factory("A"), factory("B")));
            var bindings = new HashMap<FactoryId, BusinessRuntimeBinding>();
            for (String id : List.of("A", "B")) {
                var source = source();
                var jdbc = new JdbcTemplate(source);
                for (String name : List.of("V2__simple_business_capabilities.sql", "V8__warehouse_inventory.sql")) {
                    String ddl = Files.readString(Path.of("mes-admin/src/main/resources/db/business/" + name))
                            .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ")
                            .replaceAll("(?m)^\\s*factory_id [^\\n]+\\n", "")
                            .replace("factory_id,", "")
                            .replace("factory_id, ", "");
                    for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
                }
                jdbc.execute("ALTER TABLE wm_inventory ADD batch_no VARCHAR(128)");
                jdbc.execute("ALTER TABLE wm_inventory ADD bin_code VARCHAR(64)");
                jdbc.update("INSERT INTO material(id,code,name,unit_id,material_type,status) VALUES(1,'M1','物料一',1,'ITEM','ENABLED')");
                business.put(id, jdbc);
                var identity = new DatasourceIdentity("TEST_ONLY_" + id);
                var factoryId = new FactoryId(id);
                registry.registerDedicated(factoryId, identity, 1, source, () -> {
                }, MAPPERS);
                bindings.put(factoryId, new BusinessRuntimeBinding(factoryId, identity, 1));
            }
            resolver = new ConfiguredBusinessPersistenceResolver(catalog, bindings);
        }

        static Factory factory(String id) {
            return new Factory(new FactoryId(id), "TEST_ONLY", id, id, FactoryStatus.ENABLED);
        }

        static DriverManagerDataSource source() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        public void close() {
            registry.close();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @Import({WmWarehouseController.class, WmInventoryController.class, WmWarehouseServiceImpl.class, WmInventoryServiceImpl.class, StockReferenceRules.class, FoundationErrorAdvice.class})
    static class Web implements WebMvcConfigurer {
        @Bean(destroyMethod = "close")
        Fixture fixture() throws Exception {
            return new Fixture();
        }

        @Bean
        BusinessCacheProcessor caches() {
            return mock(BusinessCacheProcessor.class);
        }

        @Bean
        FactoryBusinessDataSource businessDataSource(Fixture f) {
            return new FactoryBusinessDataSource(f.resolver, f.registry);
        }

        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager transactions(FactoryBusinessDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sessions(FactoryBusinessDataSource source, OwnedTransactionManager manager) {
            return PersistenceFactories.create(source, MAPPERS, c -> {
            }, manager::verifyAccess);
        }

        @Bean
        MaterialMapper materials(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MaterialMapper.class);
        }

        @Bean
        WmWarehouseMapper warehouses(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmWarehouseMapper.class);
        }

        @Bean
        WmInventoryMapper inventories(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmInventoryMapper.class);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        SyncCoordinator coordinator(Fixture f) {
            var coordinator = spy(new SyncCoordinator(f.platform, f.platform.mapper(MasterSyncMapper.class), f.catalog));
            doAnswer(call -> {
                if (++f.ownerChecks == f.loseAt) {
                    f.center.update("UPDATE mes_master_sync SET run_token='REPLACED' WHERE status='RUNNING'");
                }
                return call.callRealMethod();
            }).when(coordinator).assertOwner(any());
            return coordinator;
        }

        @Bean
        WmInventoryQueryPort query(Fixture f) {
            return (scope, limit) -> scope.factoryId().value()
                    .equals("B") ? new UnconfiguredWmInventoryQueryAdapter().query(scope, limit) : f.inputs;
        }

        @Bean
        WmWarehouseQueryPort warehouseQuery(Fixture f) {
            return (scope, limit) -> scope.factoryId().value().equals("B")
                    ? new UnconfiguredWmWarehouseQueryAdapter().query(scope, limit) : f.warehouseInputs;
        }

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            var tokens = mock(TokenService.class);
            when(tokens.getLoginUser(any())).thenAnswer(call -> {
                String value = call.getArgument(0, HttpServletRequest.class).getHeader("Authorization");
                if (value == null) return null;
                var user = new SysUser();
                user.setUserId(1L);
                user.setUserName(value.endsWith("CRUD") ? "crud" : "full");
                return new LoginUser(1L, 1L, user, Set.of());
            });
            return http.csrf(c -> c.disable())
                    .addFilterBefore(new SystemTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(c -> c.anyRequest().authenticated()).build();
        }

        public void addInterceptors(InterceptorRegistry r) {
            try {
                var f = fixture();
                r.addInterceptor(new FoundationAccessInterceptor(new FactorySelectionService(f.catalog, new InMemoryFactoryPreference()),
                        (actor, scope, permission) -> "full".equals(actor.id()) || !permission.code()
                                .endsWith(":sync")));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static AnnotationConfigWebApplicationContext context() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(Web.class);
        c.refresh();
        return c;
    }

    static MockMvc mvc(AnnotationConfigWebApplicationContext c) {
        return MockMvcBuilders.webAppContextSetup(c)
                .addFilters(new RequestBoundaryFilter(), c.getBean(FilterChainProxy.class)).build();
    }

    static final String W = "/api/wm/wm-warehouse", I = "/api/wm/wm-inventory";
    static final String WH = "{\"code\":\"W1\",\"name\":\"一号仓\",\"status\":\"ENABLED\"}";

    static String inventory(String material, String warehouse) {
        return "{\"materialId\":\"" + material + "\",\"warehouseId\":\"" + warehouse + "\",\"quantity\":\"1234567890.1234\",\"availableQuantity\":null,\"status\":\"ENABLED\"}";
    }

    @Test
    void crudReferencesUniquenessAndIsolation() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            var f = c.getBean(Fixture.class);
            m.perform(post(W).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(WH)).andExpect(status().isOk())
                    .andExpect(jsonPath("createBy").value("1"));
            m.perform(post(W).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(WH)).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("STOCK_KEY_DUPLICATE"));
            m.perform(get(W + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(inventory("2", "1")))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(inventory("1", "99")))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(inventory("1", "1"))).andExpect(status().isOk())
                    .andExpect(jsonPath("quantity").value("1234567890.1234"));
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content(inventory("1", "1"))).andExpect(status().isConflict());
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("items[0].materialName").value("M1 · 物料一"))
                    .andExpect(jsonPath("items[0].warehouseName").value("W1 · 一号仓"));
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            m.perform(put(I + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(inventory("1", "1").replace("1234567890.1234", "9.0001")))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantity").value("9.0001"));
            m.perform(delete(W).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1]")).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("WAREHOUSE_REFERENCED"));
            m.perform(delete(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content("[1]")).andExpect(status().isNoContent());
            m.perform(delete(W).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content("[1]")).andExpect(status().isNoContent());
            assertEquals(0, f.business.get("A").queryForObject("SELECT COUNT(*) FROM wm_inventory", Integer.class));
        }
    }

    @Test
    void syncUsesSeparatePermissionAndUnconfiguredSourceFails() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            for (String path : List.of(W, I)) {
                m.perform(post(path + "/actions/sync").header("Authorization", "Bearer CRUD")
                        .header("X-Factory-Id", "A")).andExpect(status().isForbidden());
                m.perform(post(path + "/actions/sync").header("Authorization", "Bearer FULL")
                                .header("X-Factory-Id", "B")).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("errorCode").value("SYNC_QUERY_NOT_IMPLEMENTED"));
            }
        }
    }

    static ExternalWmInventory external(long warehouse, String quantity) {
        return new ExternalWmInventory(1L, warehouse, new BigDecimal(quantity), null, "ENABLED", null, null, null);
    }

    @Test
    void syncCompositeKeysRepeatsUpdatesAndRollsBackInvalidBatch() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            var f = c.getBean(Fixture.class);
            f.business.get("A")
                    .update("INSERT INTO wm_warehouse(code,name,status) VALUES('W1','一仓','ENABLED'),('W2','二仓','ENABLED')");
            f.inputs = List.of(external(1, "1.2"), external(2, "2.3"));
            m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("created").value(2));
            f.inputs = List.of(external(1, "1.2000"), external(2, "2.3000"));
            m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("skipped").value(2));
            f.inputs = List.of(external(1, "4.5001"), external(2, "2.3"));
            m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("updated").value(1))
                    .andExpect(jsonPath("skipped").value(1));
            f.inputs = List.of(external(1, "7"), external(3, "9"));
            m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(new BigDecimal("4.5001"), f.business.get("A")
                    .queryForObject("SELECT quantity FROM wm_inventory WHERE warehouse_id=1", BigDecimal.class));
            f.inputs = List.of(external(1, "1"), external(1, "2"));
            m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isBadRequest());
            assertEquals(2, f.business.get("A").queryForObject("SELECT COUNT(*) FROM wm_inventory", Integer.class));
            assertEquals(0, f.business.get("B").queryForObject("SELECT COUNT(*) FROM wm_inventory", Integer.class));
        }
    }

    @Test
    void warehouseSyncCreatesUpdatesAndSkips() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            var f = c.getBean(Fixture.class);
            f.warehouseInputs = List.of(new ExternalWmWarehouse("ERP1", "ERP仓库", "ENABLED", null));
            m.perform(post(W + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("created").value(1));
            m.perform(post(W + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("skipped").value(1));
            f.warehouseInputs = List.of(new ExternalWmWarehouse("ERP1", "ERP仓库更新", "DISABLED", null));
            m.perform(post(W + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("updated").value(1));
        }
    }

    @Test
    void lostRunTokenStopsWritesAndPreventsCommit() throws Exception {
        for (int loseAt : List.of(3, 4)) {
            try (var c = context()) {
                var m = mvc(c);
                var f = c.getBean(Fixture.class);
                f.business.get("A")
                        .update("INSERT INTO wm_warehouse(code,name,status) VALUES('W1','一仓','ENABLED'),('W2','二仓','ENABLED')");
                f.inputs = List.of(external(1, "1"), external(2, "2"));
                f.loseAt = loseAt;
                m.perform(post(I + "/actions/sync").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                        .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("SYNC_EXECUTION_LOST"));
                assertEquals(0, f.business.get("A").queryForObject("SELECT COUNT(*) FROM wm_inventory", Integer.class));
            }
        }
    }

}
