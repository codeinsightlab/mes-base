package dev.mesbase.server;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.RoutingStepMapper;
import dev.mesbase.business.production.domain.OperationUpdateCommand;
import dev.mesbase.business.production.domain.RoutingUpdateCommand;
import dev.mesbase.business.production.service.IOperationService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.impl.OperationServiceImpl;
import dev.mesbase.business.production.service.impl.RoutingServiceImpl;
import dev.mesbase.business.production.sync.ExternalOperation;
import dev.mesbase.business.production.sync.ExternalRouting;
import dev.mesbase.business.production.sync.ExternalRoutingStep;
import dev.mesbase.business.production.sync.OperationQueryPort;
import dev.mesbase.business.production.sync.RoutingQueryPort;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.sync.MasterSyncMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * TEST ONLY: actual isolated MySQL + Spring transactions + real coordinator; synthetic ERP port, no server started.
 */
class OperationRoutingMySqlTest {
    static final String FACTORY = "TEST_OR_MYSQL";
    static final Set<Class<?>> MAPPERS = Set.of(OperationMapper.class, RoutingMapper.class,
            RoutingStepMapper.class, RoutingProductMapper.class, MaterialMapper.class);

    static DriverManagerDataSource source(Path file, String expected) throws Exception {
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            settings.load(reader);
        }
        if (!expected.equals(settings.getProperty("database")))
            throw new IllegalArgumentException("Explicit acceptance schema required");
        String password = settings.getProperty("password");
        if (password.startsWith("\"") && password.endsWith("\""))
            password = password.substring(1, password.length() - 1);
        return new DriverManagerDataSource("jdbc:mysql://" + settings.getProperty("host") + ":" + settings.getProperty("port")
                + "/" + expected + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                settings.getProperty("user"), password);
    }

    @Configuration
    @EnableTransactionManagement
    @Import({OperationServiceImpl.class, RoutingServiceImpl.class, RoutingReferenceRules.class})
    static class Config {
        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager manager(FactoryBusinessDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sessions(FactoryBusinessDataSource source, OwnedTransactionManager manager) {
            return PersistenceFactories.create(source, MAPPERS, PersistenceFactories::pagination, manager::verifyAccess);
        }

        @Bean
        OperationMapper operations(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(OperationMapper.class);
        }

        @Bean
        RoutingMapper routes(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingMapper.class);
        }

        @Bean
        RoutingStepMapper steps(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingStepMapper.class);
        }

        @Bean
        RoutingProductMapper products(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RoutingProductMapper.class);
        }

        @Bean
        MaterialMapper materials(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(MaterialMapper.class);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        BusinessCacheProcessor caches() {
            return mock(BusinessCacheProcessor.class);
        }
    }

    @Test
    void syntheticErpUsesRealMySqlUpsertAndCannotRewritePublishedInstructions() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mes.test.operation.mysql"), "Requires explicit owned MySQL acceptance schemas");
        var business = source(Path.of(".local/operation-routing-20261013/mysql-conversion.cnf"), "mes_base_oproute_conversion_20261013");
        var center = source(Path.of(".local/operation-routing-20261013/mysql-center.cnf"), "mes_base_dev");
        var jdbc = new JdbcTemplate(business);
        var centerJdbc = new JdbcTemplate(center);
        var id = new FactoryId(FACTORY);
        var scope = new FactoryScope(id);
        var factory = new Factory(id, "TEST_ONLY", FACTORY, "TEST ONLY", FactoryStatus.ENABLED);
        var catalog = new InMemoryFactoryCatalog(List.of(factory));
        var identity = new DatasourceIdentity("TEST_ONLY_OPERATION_ROUTING_MYSQL");
        var binding = new BusinessRuntimeBinding(id, identity, 1);
        var platform = new PlatformPersistence(center, Set.of(MasterSyncMapper.class));
        // The real coordinator table has a factory FK; seed an exact disposable center identity.
        centerJdbc.update("INSERT INTO mf_factory(factory_id,enterprise_id,factory_code,factory_name,status,revision) VALUES(?,?,?,?,'ENABLED',1)",
                FACTORY, "TEST_ONLY", FACTORY, "TEST ONLY 同步 MySQL 验收");
        final String[] stage = {"first"};
        final int[] queries = {0, 0};
        try (var registry = new RuntimeRegistry(); var context = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(id, identity, 1, business, () -> {
            }, MAPPERS);
            var source = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(id, binding)), registry);
            context.getEnvironment().getPropertySources().addFirst(new MockEnvironment()
                    .withProperty("mes.business.enabled", "true").getPropertySources().get("mockProperties"));
            context.registerBean(FactoryBusinessDataSource.class, () -> source);
            context.registerBean(SyncCoordinator.class, () -> new SyncCoordinator(platform, catalog));
            context.registerBean(OperationQueryPort.class, () -> (selected, maximum) -> {
                queries[0]++;
                if ("lost".equals(stage[0])) {
                    centerJdbc.update("UPDATE mes_master_sync SET run_token=? WHERE factory_id=? AND sync_key='operation.erp' AND status='RUNNING'",
                            "TEST_ONLY_REPLACEMENT_RUN", FACTORY);
                }
                return List.of(
                        new ExternalOperation("TEST-EXT-1", "TEST-OR-MYSQL-OP1", "TEST ONLY 工序", "原工艺要求", "ENABLED"),
                        new ExternalOperation("TEST-EXT-2", "TEST-OR-MYSQL-OP2", "TEST ONLY 后序", null, "ENABLED"));
            });
            context.registerBean(RoutingQueryPort.class, () -> (selected, maximum) -> {
                queries[1]++;
                return List.of(new ExternalRouting(
                        "TEST-EXT-ROUTE", "TEST-OR-MYSQL-ROUTE", "TEST ONLY " + stage[0], null, stage[0],
                        List.of("TEST-OR-MYSQL-PRODUCT"), List.of(new ExternalRoutingStep(1, "TEST-OR-MYSQL-OP1", true, null),
                        new ExternalRoutingStep(2, "TEST-OR-MYSQL-OP2", false, null))));
            });
            context.register(Config.class);
            context.refresh();
            FactoryContext.bind(scope);
            jdbc.update("INSERT INTO unit_measure(factory_id,code,name,status) VALUES(?,?,?,'ENABLED')", FACTORY, "TEST-OR-MYSQL-UOM", "TEST ONLY");
            Long unit = jdbc.queryForObject("SELECT id FROM unit_measure WHERE factory_id=? AND code=?", Long.class, FACTORY, "TEST-OR-MYSQL-UOM");
            jdbc.update("INSERT INTO material(factory_id,code,name,unit_id,material_type,status) VALUES(?,?,?,?,'PRODUCT','ENABLED')", FACTORY, "TEST-OR-MYSQL-PRODUCT", "TEST ONLY", unit);
            var operations = context.getBean(IOperationService.class);
            var routes = context.getBean(IRoutingService.class);
            var coordinator = context.getBean(SyncCoordinator.class);
            var operationRun = coordinator.claim(scope, "operation.erp", Duration.ofMinutes(5));
            assertEquals(ErrorCode.SYNC_RUNNING, assertThrows(FoundationException.class,
                    () -> operations.sync(scope)).code());
            assertEquals(0, queries[0]);
            coordinator.fail(operationRun);
            assertEquals(2, operations.sync(scope).created());
            assertEquals(2, operations.sync(scope).skipped());
            var routingRun = coordinator.claim(scope, "routing.erp", Duration.ofMinutes(5));
            assertEquals(ErrorCode.SYNC_RUNNING, assertThrows(FoundationException.class,
                    () -> routes.sync(scope)).code());
            assertEquals(0, queries[1]);
            coordinator.fail(routingRun);
            assertEquals(1, routes.sync(scope).created());
            assertEquals(1, routes.sync(scope).skipped());
            Long routeId = jdbc.queryForObject("SELECT id FROM routing WHERE factory_id=? AND code=? AND version=1", Long.class, FACTORY, "TEST-OR-MYSQL-ROUTE");
            var published = routes.publish(routeId, 0);
            assertEquals("PUBLISHED", published.getStatus());
            var frozen = routes.detail(routeId);
            assertEquals("原工艺要求", frozen.steps().getFirst().getOperationDescription());
            var op = operations.selectById(frozen.steps().getFirst().getOperationId());
            operations.updateById(op.getId(), new OperationUpdateCommand(op.getCode(), "TEST ONLY 改名", "新工艺要求", "ENABLED", 0, op.getExternalId(), op.getRevision()));
            assertEquals("原工艺要求", routes.detail(routeId).steps().getFirst().getOperationDescription());
            assertEquals("TEST ONLY 工序", routes.detail(routeId).steps().getFirst().getOperationName());
            assertEquals(ErrorCode.ROUTING_FROZEN, assertThrows(FoundationException.class,
                    () -> routes.updateById(routeId, new RoutingUpdateCommand("绕过", null, published.getRevision()))).code());
            assertEquals(ErrorCode.OPERATION_REFERENCED, assertThrows(FoundationException.class,
                    () -> operations.deleteByIds(List.of(op.getId()))).code());
            stage[0] = "changed";
            assertEquals(1, routes.sync(scope).created());
            assertEquals(1, routes.sync(scope).skipped());
            assertEquals("PUBLISHED", routes.detail(routeId).routing().getStatus());
            assertEquals("TEST ONLY first", routes.detail(routeId).routing().getName());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE factory_id=? AND version=2 AND status='DRAFT'", Integer.class, FACTORY));
            assertEquals("SUCCEEDED", centerJdbc.queryForObject("SELECT status FROM mes_master_sync WHERE factory_id=? AND sync_key='routing.erp'", String.class, FACTORY));
            stage[0] = "lost";
            assertEquals(ErrorCode.SYNC_EXECUTION_LOST, assertThrows(FoundationException.class,
                    () -> operations.sync(scope)).code());
            assertEquals("TEST ONLY 改名", operations.selectById(op.getId()).getName());
            assertEquals("TEST_ONLY_REPLACEMENT_RUN", centerJdbc.queryForObject(
                    "SELECT run_token FROM mes_master_sync WHERE factory_id=? AND sync_key='operation.erp'", String.class, FACTORY));
        } finally {
            FactoryContext.clear();
            // Only this fixture's exact factory rows are disposable; source-converted rows remain untouched.
            jdbc.update("DELETE s FROM routing_step s JOIN routing r ON r.id=s.routing_id WHERE r.factory_id=?", FACTORY);
            jdbc.update("DELETE p FROM routing_product p JOIN routing r ON r.id=p.routing_id WHERE r.factory_id=?", FACTORY);
            jdbc.update("DELETE FROM routing WHERE factory_id=?", FACTORY);
            jdbc.update("DELETE FROM operation WHERE factory_id=?", FACTORY);
            jdbc.update("DELETE FROM material WHERE factory_id=?", FACTORY);
            jdbc.update("DELETE FROM unit_measure WHERE factory_id=?", FACTORY);
            centerJdbc.update("DELETE FROM mes_master_sync WHERE factory_id=?", FACTORY);
            centerJdbc.update("DELETE FROM mf_factory WHERE factory_id=? AND factory_code=?", FACTORY, FACTORY);
        }
    }
}
