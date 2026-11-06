package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.basic.dao.*;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.service.*;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.dao.*;
import dev.mesbase.business.wm.domain.*;
import dev.mesbase.business.wm.service.*;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import dev.mesbase.common.core.domain.entity.*;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.utils.DictDataLookup;
import dev.mesbase.core.factory.*;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.platform.execution.*;
import dev.mesbase.system.mapper.*;
import dev.mesbase.system.service.*;
import dev.mesbase.system.service.impl.*;
import dev.mesbase.system.strategy.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Opt-in real existing MySQL. No DDL, migration, service listener, balance SQL or table reset.
 * Commits only tagged receiving documents/zero-balance stock identities and their official actions.
 * Production-only collaborators and Redis cache transport are stubs; all reached stock/numbering SQL is real.
 */
@EnabledIfEnvironmentVariable(named = "MES_PURCHASE_MYSQL_ACCEPTANCE", matches = "true")
class PurchaseReceiptMySqlTest {
    static final String FACTORY = "TEST_DEV_PERF_20261019";

    @Configuration
    @EnableTransactionManagement
    static class Transactions {
    }

    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    static class FailSecondAction implements Interceptor {
        final AtomicInteger remaining = new AtomicInteger(-1);

        public Object intercept(Invocation i) throws Throwable {
            if (((MappedStatement) i.getArgs()[0]).getId().equals(WmStockActionMapper.class.getName() + ".insert")
                    && remaining.get() > 0 && remaining.decrementAndGet() == 0)
                throw new IllegalStateException("TEST ONLY second stock action failure");
            return i.proceed();
        }
    }

    static <T> T actor(Supplier<T> work) {
        var user = new SysUser();
        user.setUserId(1L);
        user.setUserName("QA_DIRECT_PURCHASE");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(1L, 1L, user, Set.of()), null, List.of()));
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        try {
            FactoryIntegrationContext.run(FactorySnapshotFixture.local(FACTORY), () -> result.set(work.get()));
            return result.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static <T> void mapper(AnnotationConfigApplicationContext c, SqlSessionTemplate template, Class<T> type) {
        c.registerBean(type.getSimpleName(), type, () -> template.getMapper(type));
    }

    record Fixture(AnnotationConfigApplicationContext context, JdbcTemplate db, RuntimeRegistry registry,
                   FailSecondAction failure) implements AutoCloseable {
        public void close() {
            context.close();
            registry.close();
        }
    }

    static Fixture context() throws Exception {
        var physical = AcceptanceBusinessDatabase.performanceTestSource();
        assertTrue(physical.getUrl().startsWith("jdbc:mysql://192.168.18.25:3306/mes_base_dev_perf_test?"));
        var db = new JdbcTemplate(physical);
        assertEquals("mes_base_dev_perf_test", db.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM flyway_business_schema_history WHERE version='86' AND success=1", Integer.class));
        var c = new AnnotationConfigApplicationContext();
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("EXPLICIT LIVE TEST", Map.of("mes.business.enabled", "true", "mes.system.enabled", "true")));
        var registry = new RuntimeRegistry();
        var factoryId = new FactoryId(FACTORY);
        var identity = new DatasourceIdentity("VERIFIED_TEST_DATASOURCE_9");
        registry.registerDedicated(factoryId, identity, 1, physical, () -> {
        }, MaterialIssueHttpTest.MAPPERS);
        var catalog = new InMemoryFactoryCatalog(List.of(new Factory(factoryId, "TEST_ONLY", FACTORY, FACTORY, FactoryStatus.ENABLED)));
        var routed = new FactoryBusinessDataSource(new ConfiguredBusinessPersistenceResolver(catalog, Map.of(factoryId, new BusinessRuntimeBinding(factoryId, identity, 1))), registry);
        var tx = new OwnedTransactionManager(routed);
        var failure = new FailSecondAction();
        var sessions = PersistenceFactories.create(routed, MaterialIssueHttpTest.MAPPERS, cfg -> {
            PersistenceFactories.pagination(cfg);
            cfg.addInterceptor(failure);
        }, tx::verifyAccess);
        var template = new SqlSessionTemplate(sessions);
        c.register(Transactions.class);
        c.registerBean("businessTransactionManager", OwnedTransactionManager.class, () -> tx);
        c.registerBean(SqlSessionFactory.class, () -> sessions);
        for (var type : MaterialIssueHttpTest.MAPPERS) mapper(c, template, type);
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) config.get("mes")).get("platform");
        var centerSource = new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"));
        var centerJdbc = new JdbcTemplate(centerSource);
        assertEquals(1, centerJdbc.queryForObject("SELECT COUNT(*) FROM mf_factory f JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id=? AND f.status='ENABLED' AND f.runtime_mode='LOCAL' AND d.status='ENABLED' AND d.datasource_id=9 AND d.db_host='192.168.18.25' AND d.db_port=3306 AND d.db_name='mes_base_dev_perf_test'", Integer.class, FACTORY));
        var center = new PlatformPersistence(centerSource, Set.of(SysAutoCodeRuleMapper.class, SysAutoCodePartMapper.class, SysAutoCodeResultMapper.class, AutoCodeExtraMapper.class, SysDictDataMapper.class), cfg -> cfg.getTypeAliasRegistry()
                .registerAliases("dev.mesbase.common.core.domain.entity"));
        c.registerBean(PlatformPersistence.class, () -> center);
        for (var type : Set.of(SysAutoCodeRuleMapper.class, SysAutoCodePartMapper.class, SysAutoCodeResultMapper.class, AutoCodeExtraMapper.class, SysDictDataMapper.class))
            registerCenter(c, center, type);
        c.registerBean("masterTransactionManager", OwnedTransactionManager.class, center::transactionManager);
        c.registerBean(DictDataLookup.class, () -> center.mapper(SysDictDataMapper.class)::selectDictDataByType);
        var redis = mock(RedisCache.class);
        when(redis.setCacheObjectIfVersion(anyString(), nullable(String.class), anyString(), any(), anyInt())).thenReturn(true);
        c.getBeanFactory().registerSingleton("redisCache", redis);
        c.registerBean(AutoCodeFactoryContext.class, AutoCodeFactoryContextAdapter::new);
        c.registerBean(PartTypeHandler.class, () -> new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(), new PartTypeFixCharHandler(), new PartTypeSerialNoHandler()));
        c.register(AutoCodeRuleSnapshotCache.class, SysAutoCodeGenerationServiceImpl.class);
        c.registerBean(jakarta.validation.Validator.class, () -> Validation.buildDefaultValidatorFactory()
                .getValidator());
        c.registerBean(BusinessCacheProcessor.class, () -> mock(BusinessCacheProcessor.class));
        c.registerBean(SyncCoordinator.class, () -> mock(SyncCoordinator.class));
        c.registerBean(ProductionChainLocks.class, () -> mock(ProductionChainLocks.class));
        c.registerBean(ProductionQuantityService.class, () -> mock(ProductionQuantityService.class));
        c.registerBean(QualityTreatmentService.class, () -> mock(QualityTreatmentService.class));
        c.registerBean(ExecutionPathService.class, () -> mock(ExecutionPathService.class));
        c.register(WarehouseIntegration.class, BusinessSourceRules.class, MaterialQuantityRules.class, StockReferenceRules.class, LocalStockService.class, StockDocumentService.class, WmInventoryServiceImpl.class);
        c.refresh();
        return new Fixture(c, db, registry, failure);
    }

    static <T> void registerCenter(AnnotationConfigApplicationContext c, PlatformPersistence center, Class<T> type) {
        c.registerBean(type.getSimpleName(), type, () -> center.mapper(type));
    }

    record Stock(long id, long material, long warehouse, long unit, String batch) {
    }

    static Stock stock(Fixture f, long material, long unit, long warehouse, String batch) {
        var item = actor(() -> f.context.getBean(IWmInventoryService.class)
                .insert(new WmInventoryCreateCommand(material, warehouse, BigDecimal.ZERO, BigDecimal.ZERO, "ENABLED", "QA direct purchase acceptance", batch, null)));
        return new Stock(item.inventory().getId(), material, warehouse, unit, batch);
    }

    static StockDocumentModels.AggregateLine line(Stock s, String quantity) {
        return new StockDocumentModels.AggregateLine(null, s.id, s.material, s.warehouse, s.unit, s.batch, null, new BigDecimal(quantity), "QA counted delivery");
    }

    static StockDocumentModels.Detail draft(StockDocumentService service, String tag,
                                            List<StockDocumentModels.AggregateLine> lines) {
        return actor(() -> service.createAggregate(new StockDocumentModels.AggregateSave(new StockDocumentModels.AggregateHeader("IN", "PURCHASE_RECEIPT", tag, null, null, null, "PURCHASE_RECEIPT", null, "QA Supplier", tag), lines)));
    }

    static BigDecimal balance(Fixture f, Stock s) {
        return f.db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, s.id);
    }

    static int actions(Fixture f, long document) {
        return f.db.queryForObject("SELECT COUNT(*) FROM wm_stock_action a JOIN wm_stock_document_line l ON a.document_line_id=l.id WHERE l.document_id=?", Integer.class, document);
    }

    @Test
    void realCommittedMultiMaterialBatchesConcurrentReplayAndRollback() throws Exception {
        try (var f = context(); var pool = Executors.newFixedThreadPool(2)) {
            var service = f.context.getBean(StockDocumentService.class);
            String tag = "QA_PURCHASE_" + UUID.randomUUID().toString().substring(0, 8);
            var materials = f.db.queryForList("SELECT m.id,m.unit_id FROM material m JOIN unit_measure u ON u.id=m.unit_id WHERE m.status='ENABLED' AND u.status='ENABLED' ORDER BY m.id LIMIT 2");
            long warehouse = f.db.queryForObject("SELECT id FROM wm_warehouse WHERE status='ENABLED' AND source='LOCAL' ORDER BY id LIMIT 1", Long.class);
            var a = stock(f, ((Number) materials.get(0).get("id")).longValue(), ((Number) materials.get(0)
                    .get("unit_id")).longValue(), warehouse, tag + "_A");
            var b = stock(f, ((Number) materials.get(1).get("id")).longValue(), ((Number) materials.get(1)
                    .get("unit_id")).longValue(), warehouse, tag + "_B");
            var c = stock(f, a.material, a.unit, warehouse, tag + "_C");
            var doc = draft(service, tag, List.of(line(a, "7"), line(b, "11"), line(c, "5")));
            assertEquals(0, balance(f, a).signum());
            assertEquals(0, balance(f, b).signum());
            assertEquals(0, actions(f, doc.document().getId()));
            var command = new StockDocumentModels.Post(UUID.randomUUID().toString(), doc.document().getRevision());
            var barrier = new CyclicBarrier(2);
            Callable<String> approve = () -> {
                barrier.await();
                return actor(() -> service.approve(doc.document().getId(), command)).document().getStatus();
            };
            var first = pool.submit(approve);
            var second = pool.submit(approve);
            assertEquals("POSTED", first.get(20, TimeUnit.SECONDS));
            assertEquals("POSTED", second.get(20, TimeUnit.SECONDS));
            actor(() -> service.approve(doc.document().getId(), command));
            assertEquals(0, new BigDecimal("7").compareTo(balance(f, a)));
            assertEquals(0, new BigDecimal("11").compareTo(balance(f, b)));
            assertEquals(0, new BigDecimal("5").compareTo(balance(f, c)));
            assertEquals(3, actions(f, doc.document().getId()));
            var rollback = draft(service, tag + "_ROLLBACK", List.of(line(a, "2"), line(b, "3")));
            f.failure.remaining.set(2);
            assertThrows(RuntimeException.class, () -> actor(() -> service.approve(rollback.document()
                    .getId(), new StockDocumentModels.Post(UUID.randomUUID().toString(), rollback.document()
                    .getRevision()))));
            f.failure.remaining.set(-1);
            assertEquals(0, new BigDecimal("7").compareTo(balance(f, a)));
            assertEquals(0, new BigDecimal("11").compareTo(balance(f, b)));
            assertEquals(0, actions(f, rollback.document().getId()));
            assertEquals("DRAFT", f.db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, rollback.document()
                    .getId()));
            assertThrows(RuntimeException.class, () -> actor(() -> service.edit(doc.document()
                    .getId(), new StockDocumentModels.Edit(doc.document().getRevision(), "cannot edit posted", null))));
            // A second operation UUID cannot approve the already-posted document a second time.
            assertThrows(RuntimeException.class, () -> actor(() -> service.approve(doc.document()
                    .getId(), new StockDocumentModels.Post(UUID.randomUUID().toString(), doc.document()
                    .getRevision()))));
            var report = Path.of(".local/knowledge/purchase-receipt/mysql-evidence-" + tag + ".txt");
            Files.createDirectories(report.getParent());
            String evidence = "database=mes_base_dev_perf_test factory=" + FACTORY + "\ntag=" + tag + "\nposted=" + doc.document()
                    .getId() + " number=" + doc.document()
                    .getDocumentNo() + "\nbefore: 0,0,0\nafter: " + balance(f, a) + "," + balance(f, b) + "," + balance(f, c) + "\n" + f.db.queryForList("SELECT id,stock_id,quantity_delta,quantity_before,quantity_after,document_line_id FROM wm_stock_action WHERE document_line_id IN (SELECT id FROM wm_stock_document_line WHERE document_id=?)", doc.document()
                    .getId()) + "\nrollback_document=" + rollback.document()
                    .getId() + " status=DRAFT actions=0 balances unchanged\n";
            Files.writeString(report, evidence);
            System.out.println(evidence);
        }
    }
}
