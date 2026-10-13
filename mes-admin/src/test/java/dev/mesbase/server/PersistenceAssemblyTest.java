package dev.mesbase.server;

import dev.mesbase.web.controller.monitor.DruidController;
import dev.mesbase.server.monitor.DruidMonitorService;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.modules.persistencefixture.LegacyScanProbeMapper;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.persistencefixture.business.BusinessProbeMapper;
import dev.mesbase.persistencefixture.platform.CenterProbeMapper;
import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY center plus two independent Factory H2 databases.
 */
class PersistenceAssemblyTest {
    private DataSource pool(String label) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE marker(label VARCHAR(32))");
            try (var insert = connection.prepareStatement("INSERT INTO marker(label) VALUES(?)")) {
                insert.setString(1, label);
                insert.executeUpdate();
            }
        }
        return source;
    }

    @Test
    void scannersRegisterTwoOwnersWithoutCentralMapperBeansAndFactoryRoutingIsIsolated() throws Exception {
        var center = pool("center");
        var a = pool("A");
        var b = pool("B");
        var scopeA = new FactoryScope(new FactoryId("A"));
        var scopeB = new FactoryScope(new FactoryId("B"));
        var bindA = new BusinessRuntimeBinding(scopeA.factoryId(), new DatasourceIdentity("A"), 1);
        var bindB = new BusinessRuntimeBinding(scopeB.factoryId(), new DatasourceIdentity("B"), 1);
        try (var registry = new RuntimeRegistry(); var context = new AnnotationConfigApplicationContext()) {
            registry.registerDedicated(scopeA.factoryId(), bindA.datasourceIdentity(), 1, a, () -> {
            }, Set.of(BusinessProbeMapper.class));
            registry.registerDedicated(scopeB.factoryId(), bindB.datasourceIdentity(), 1, b, () -> {
            }, Set.of(BusinessProbeMapper.class));
            var business = new FactoryBusinessDataSource(scope -> scope.factoryId()
                    .equals(scopeA.factoryId()) ? bindA : bindB, registry);
            var platformManager = new OwnedTransactionManager(center);
            var businessManager = new OwnedTransactionManager(business);
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("fixture", Map.of("mes.persistence.mapper-packages", "dev.mesbase.persistencefixture")));
            context.addBeanFactoryPostProcessor(new OwnerMapperRegistration(context.getEnvironment(), Map.of(PersistenceOwner.Kind.PLATFORM, List.of("dev.mesbase.persistencefixture.platform"), PersistenceOwner.Kind.BUSINESS, List.of("dev.mesbase.persistencefixture.business"))));
            context.registerBean("masterSqlSessionFactory", SqlSessionFactory.class, () -> PersistenceFactories.create(center, Set.of(CenterProbeMapper.class), c -> {
            }, platformManager::verifyAccess));
            context.registerBean("businessSqlSessionFactory", SqlSessionFactory.class, () -> PersistenceFactories.create(business, Set.of(BusinessProbeMapper.class), c -> {
            }, businessManager::verifyAccess));
            context.refresh();
            assertEquals(2, context.getBeansOfType(SqlSessionFactory.class).size());
            assertEquals("center", context.getBean(CenterProbeMapper.class).label());
            assertThrows(RuntimeException.class, () -> context.getBean(BusinessProbeMapper.class).label());
            var tx = new TransactionTemplate(businessManager);
            assertEquals("A", FactoryContext.withScope(scopeA, () -> tx.execute(status -> context.getBean(BusinessProbeMapper.class)
                    .label())));
            assertEquals("B", FactoryContext.withScope(scopeB, () -> tx.execute(status -> context.getBean(BusinessProbeMapper.class)
                    .label())));
            FactoryContext.withScope(scopeA, () -> tx.execute(status -> {
                assertThrows(RuntimeException.class, () -> FactoryContext.bind(scopeB));
                assertThrows(RuntimeException.class, () -> context.getBean(CenterProbeMapper.class).label());
                var nested = new TransactionTemplate(businessManager);
                nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                assertEquals("A", nested.execute(inner -> context.getBean(BusinessProbeMapper.class).label()));
                assertEquals("A", context.getBean(BusinessProbeMapper.class).label());
                return null;
            }));
            new TransactionTemplate(platformManager).execute(status -> {
                assertThrows(RuntimeException.class, () -> FactoryContext.withScope(scopeA,
                        () -> context.getBean(BusinessProbeMapper.class).label()));
                assertThrows(RuntimeException.class, () -> FactoryContext.withScope(scopeA,
                        () -> tx.execute(inner -> context.getBean(BusinessProbeMapper.class).label())));
                return null;
            });
            assertTrue(FactoryContext.current().isEmpty());
        }
    }

    @Test
    void productionConfigurationBuildsStandardFactoriesAndScansExistingMappers() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml")))
                context.getEnvironment().getPropertySources().addLast(source);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", Map.of(
                    "mes.system.enabled", true, "mes.platform.enabled", true, "mes.platform.migrate", false,
                    "mes.platform.url", "jdbc:h2:mem:" + UUID.randomUUID(), "mes.platform.username", "sa", "mes.platform.password", "")));
            context.registerBean(RuntimeRegistry.class, RuntimeRegistry::new);
            context.registerBean(BusinessPersistenceResolver.class, () -> scope -> {
                throw new IllegalStateException("TEST ONLY no Factory connection");
            });
            context.register(PersistenceConfiguration.class, DruidMonitorService.class, DruidController.class);
            context.refresh();
            assertEquals(Set.of("masterSqlSessionFactory", "businessSqlSessionFactory"),
                    context.getBeansOfType(SqlSessionFactory.class).keySet());
            assertEquals(Set.of("masterDataSource", "businessDataSource"),
                    context.getBeansOfType(DataSource.class).keySet());
            assertEquals(Set.of("masterTransactionManager", "businessTransactionManager"),
                    context.getBeansOfType(OwnedTransactionManager.class).keySet());
            var stats = context.getBean(DruidController.class).stats();
            assertEquals("platform", ((DruidController.Pool) stats.get(AjaxResult.DATA_TAG)).name());
            assertTrue(context.containsBean("sysUserMapper"));
            assertTrue(context.containsBean("materialMapper"));
            assertTrue(context.getBean("businessSqlSessionFactory", SqlSessionFactory.class)
                    .getConfiguration().hasMapper(MaterialMapper.class));
            assertTrue(!context.getBean("masterSqlSessionFactory", SqlSessionFactory.class)
                    .getConfiguration().hasMapper(MaterialMapper.class));
            assertTrue(context.getBean(OwnerMapperCatalog.class).types(PersistenceOwner.Kind.BUSINESS)
                    .contains(MaterialMapper.class));
            assertTrue(context.containsBean("masterTransactionManager"));
            assertTrue(context.containsBean("businessTransactionManager"));
        }
    }

    @Test
    void overlappingPackageRootsFailScanning() {
        assertThrows(IllegalArgumentException.class, () -> new OwnerMapperCatalog(Map.of(PersistenceOwner.Kind.PLATFORM, List.of("dev.mesbase.system"), PersistenceOwner.Kind.BUSINESS, List.of("dev.mesbase.system.factory"))));
    }

    @Test
    void allProductionMappersAreClassifiedByFixedPackages() {
        var registry = new DefaultListableBeanFactory();
        new OwnerMapperRegistration(new MockEnvironment()).postProcessBeanDefinitionRegistry(registry);
        var catalog = registry.getBean(OwnerMapperCatalog.class);
        assertTrue(catalog.types(PersistenceOwner.Kind.PLATFORM).size() > 20);
        assertTrue(catalog.types(PersistenceOwner.Kind.BUSINESS).contains(MaterialMapper.class));
    }

    @Test
    void businessRootHasExactBoundaryAndOldPackagesAreNoLongerRegistered() {
        assertEquals(List.of("dev.mesbase.business"), MapperPackages.ROOTS.get(PersistenceOwner.Kind.BUSINESS));
        assertEquals(PersistenceOwner.Kind.BUSINESS, MapperPackages.owner(MaterialMapper.class));
        assertThrows(IllegalArgumentException.class, () -> MapperPackages.owner(LegacyScanProbeMapper.class));
        assertTrue(!MapperPackages.contains("dev.mesbase.businessSimilar", "dev.mesbase.business"));
        var registry = new DefaultListableBeanFactory();
        new OwnerMapperRegistration(new MockEnvironment()).postProcessBeanDefinitionRegistry(registry);
        var catalog = registry.getBean(OwnerMapperCatalog.class);
        assertTrue(catalog.types(PersistenceOwner.Kind.BUSINESS).stream()
                .allMatch(type -> MapperPackages.contains(type.getPackageName(), "dev.mesbase.business")));
        assertTrue(!registry.containsBeanDefinition("legacyScanProbeMapper"));
    }

}
