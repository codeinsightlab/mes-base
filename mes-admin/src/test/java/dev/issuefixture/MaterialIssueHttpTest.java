package dev.issuefixture;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.pro.dao.ProWorkorderMapper;
import dev.mesbase.business.issue.controller.MesMaterialIssueController;
import dev.mesbase.business.issue.controller.MesMaterialIssueItemController;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueItemServiceImpl;
import dev.mesbase.business.issue.service.MaterialIssueRules;

import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.framework.security.SystemTokenFilter;
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
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
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
class MaterialIssueHttpTest {
    static final Set<Class<?>> MAPPERS = Set.of(MaterialMapper.class, UnitMeasureMapper.class, WmWarehouseMapper.class, RoutingMapper.class, RoutingProductMapper.class, ProWorkorderMapper.class, MesMaterialIssueMapper.class, MesMaterialIssueItemMapper.class);

    static class Fixture implements AutoCloseable {
        final RuntimeRegistry registry = new RuntimeRegistry();
        final Map<String, JdbcTemplate> business = new HashMap<>();
        final InMemoryFactoryCatalog catalog;
        final ConfiguredBusinessPersistenceResolver resolver;

        Fixture() throws Exception {
            catalog = new InMemoryFactoryCatalog(List.of(factory("A"), factory("B")));
            var bindings = new HashMap<FactoryId, BusinessRuntimeBinding>();
            for (String id : List.of("A", "B")) {
                var source = source();
                var jdbc = new JdbcTemplate(source);
                for (String name : List.of("V2__simple_business_capabilities.sql", "V3__operation_routing.sql", "V8__warehouse_inventory.sql", "V11__material_issue.sql", "V12__material_issue_quantity_precision.sql")) {
                    String ddl = Files.readString(Path.of("mes-admin/src/main/resources/db/business/" + name))
                            .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ");
                    for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
                }
                jdbc.update("INSERT INTO material(id,factory_id,code,name,unit_id,material_type,status) VALUES(1,?,'M1','物料一',1,'ITEM','ENABLED'),(2,'OTHER','M2','越厂物料',1,'ITEM','ENABLED')", id);
                String xml = Files.readString(Path.of("mes-business/src/main/resources/dev/mesbase/business/pro/dao/ProWorkorderMapper.xml"));
                var matches = Pattern.compile("column=\"([^\"]+)\" jdbcType=\"([^\"]+)\"").matcher(xml);
                var columns = new ArrayList<String>();
                while (matches.find())
                    columns.add(matches.group(1) + " " + ("VARCHAR".equals(matches.group(2)) ? "VARCHAR(255)" : matches.group(2)));
                jdbc.execute("CREATE TABLE pro_workorder(" + String.join(",", columns) + ")");
                jdbc.update("INSERT INTO pro_workorder(workorder_id,workorder_code,product_code) VALUES(1,'WO1','M1')");
                jdbc.update("INSERT INTO unit_measure(id,factory_id,code,name,status) VALUES(1,?,'KG','千克','ENABLED')", id);
                jdbc.update("INSERT INTO wm_warehouse(id,factory_id,code,name,status) VALUES(1,?,'W1','验收仓库','ENABLED')", id);
                jdbc.update("INSERT INTO routing(id,factory_id,code,name,version,status,revision,published_at) VALUES(1,?,'R1','已发布版本',1,'PUBLISHED',0,CURRENT_TIMESTAMP),(2,?,'R1','草稿版本',2,'DRAFT',0,NULL)", id, id);
                jdbc.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(1,1,'M1','产品')");
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
    @Import({MesMaterialIssueController.class, MesMaterialIssueItemController.class, MesMaterialIssueServiceImpl.class, MesMaterialIssueItemServiceImpl.class, MaterialIssueRules.class, FoundationErrorAdvice.class})
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
            return PersistenceFactories.create(source, MAPPERS, PersistenceFactories::pagination, manager::verifyAccess);
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
        MesMaterialIssueMapper mapperMesMaterialIssueMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueMapper.class);
        }

        @Bean
        MesMaterialIssueItemMapper mapperMesMaterialIssueItemMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueItemMapper.class);
        }

        @Bean
        UnitMeasureMapper mapperUnitMeasureMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(UnitMeasureMapper.class);
        }

        @Bean
        RoutingMapper mapperRoutingMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingMapper.class);
        }

        @Bean
        RoutingProductMapper routingProducts(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingProductMapper.class);
        }

        @Bean
        ProWorkorderMapper mapperProWorkorderMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProWorkorderMapper.class);
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
                        (actor, scope, permission) -> "full".equals(actor.id()) || permission.code()
                                .endsWith(":list") || permission.code().endsWith(":query")));
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

    static final String H = "/api/issue/mes-material-issue", I = H + "-item";
    static final String HEADER = "{\"issueNo\":\"QA_ISSUE\",\"workorderId\":\"1\",\"routingId\":\"1\",\"status\":\"RECORDED\"}";
    static final String ITEM = "{\"issueId\":\"1\",\"materialId\":\"1\",\"batchNo\":\"ERP/ arbitrary-batch\",\"quantity\":\"99999999999999.123456\",\"unitId\":\"1\",\"warehouseId\":\"1\"}";

    @Test
    void scopedCrudPrecisionSnapshotsAndParentProtection() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(HEADER))
                    .andExpect(status().isOk()).andExpect(jsonPath("createBy").value("1"))
                    .andExpect(jsonPath("workorderCode").value("WO1")).andExpect(jsonPath("productCode").value("M1"));
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantity").value("99999999999999.123456"))
                    .andExpect(jsonPath("materialCode").value("M1")).andExpect(jsonPath("warehouseCode").value("W1"));
            // Same material and batch can occur repeatedly; no guessed uniqueness.
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM))
                    .andExpect(status().isOk());
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A").param("issueId", "1")
                            .param("limit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items.length()").value(1));
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            m.perform(delete(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1]"))
                    .andExpect(status().isConflict());
            m.perform(put(I + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM.replace("99999999999999.123456", "9.123456")))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantity").value("9.123456"));
            m.perform(delete(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1,999]"))
                    .andExpect(status().isNotFound());
            assertEquals(2, c.getBean(Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_material_issue_item", Integer.class));
            m.perform(delete(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1,2]"))
                    .andExpect(status().isNoContent());
            m.perform(delete(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1]"))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void readOnlyPermissionsAndInvalidReferences() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            m.perform(get(H).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk());
            for (String path : List.of(H, I)) {
                m.perform(post(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                                .contentType("application/json").content(path.equals(H) ? HEADER : ITEM))
                        .andExpect(status().isForbidden());
                m.perform(delete(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                                .contentType("application/json").content("[1]"))
                        .andExpect(status().isForbidden());
            }
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json")
                            .content(HEADER.replace("\"routingId\":\"1\"", "\"routingId\":\"2\"")))
                    .andExpect(status().isBadRequest());
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content(HEADER)).andExpect(status().isOk());
            for (String invalid : List.of(ITEM.replace("\"materialId\":\"1\"", "\"materialId\":\"2\""),
                    ITEM.replace("\"issueId\":\"1\"", "\"issueId\":\"999\""),
                    ITEM.replace("99999999999999.123456", "-1"), ITEM.replace("99999999999999.123456", "1.1234567"))) {
                m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                                .contentType("application/json").content(invalid))
                        .andExpect(status().is4xxClientError());
            }
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            assertEquals(0, c.getBean(Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_material_issue_item", Integer.class));
        }
    }
}
