package dev.issuefixture;

import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.ProductionReportModels.Supplement;
import dev.mesbase.business.production.domain.ProductionReportModels.Adjustment;
import dev.mesbase.business.production.domain.ProductionReportModels.VoidOriginal;
import dev.mesbase.business.production.service.ProductionReportService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.common.cache.DetailEntityCache;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.system.mapper.SysDeptMapper;
import dev.mesbase.system.mapper.SysPostMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.env.MapPropertySource;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.integration.LocalProductionDefectErpGateway;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * LIVE MYSQL: designated SIMPLE_B binding, real generated SQL, commits, concurrency and trigger immutability.
 */
@EnabledIfEnvironmentVariable(named = "MES_DEFECT_MYSQL_ACCEPTANCE", matches = "true")
class ProductionDefectMySqlTest {
    @Configuration
    @EnableTransactionManagement
    static class Transactions {
    }

    static AnnotationConfigApplicationContext context() throws Exception {
        var c = new AnnotationConfigApplicationContext();
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("LIVE MYSQL TEST ONLY", Map.of("mes.business.enabled", "true", "mes.system.enabled", "true")));
        DataSource source = AcceptanceBusinessDatabase.source();
        var tx = new OwnedTransactionManager(source);
        var factory = PersistenceFactories.create(source, MaterialIssueHttpTest.MAPPERS, PersistenceFactories::pagination, tx::verifyAccess);
        var template = new SqlSessionTemplate(factory);
        c.register(Transactions.class);
        c.registerBean("businessTransactionManager", OwnedTransactionManager.class, () -> tx);
        c.registerBean("jdbc", JdbcTemplate.class, () -> new JdbcTemplate(source));
        c.registerBean(SqlSessionFactory.class, () -> factory);
        for (var type : MaterialIssueHttpTest.MAPPERS) register(c, template, type);
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) config.get("mes")).get("platform");
        var centerSource = new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"));
        var center = new PlatformPersistence(centerSource, Set.of(SysUserMapper.class), cfg -> {
            cfg.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            cfg.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
        });
        c.registerBean(PlatformPersistence.class, () -> center);
        c.registerBean(SysUserMapper.class, () -> center.mapper(SysUserMapper.class));
        c.registerBean(BusinessDirectoryService.class, () -> new BusinessDirectoryService(
                c.getBean(SysUserMapper.class), mock(SysDeptMapper.class), mock(SysPostMapper.class), new DetailEntityCache(mock(RedisCache.class), new MockEnvironment()), mock(BusinessCacheProcessor.class), center, c.getBeanProvider(OwnedTransactionManager.class), c.getBeanProvider(RoutingMapper.class), c.getBeanProvider(OperationMapper.class)));
        c.register(ProductionQuantityService.class, ExecutionQualityReferenceQueryImpl.class, ProductionChainLocks.class, ProductionExecutionRules.class, ExecutionPathService.class);
        c.refresh();
        return c;
    }

    static <T> void register(AnnotationConfigApplicationContext c, SqlSessionTemplate template, Class<T> type) {
        c.registerBean(type.getSimpleName(), type, () -> template.getMapper(type));
    }


    static Command command(String key, int revision, Long execution, Long node, Long warehouse, String qty) {
        return new Command(key, revision == 0 ? null : revision, revision == 0 ? null : "TEST ONLY订正", new Facts(execution, node, node, null, new BigDecimal(qty), "MATERIAL", "TEST_ONLY_DEFECT_PHASE1", null, null, "WAREHOUSE", null, warehouse, null, LocalDateTime.of(2026, 1, 1, 10, 0)));
    }

    @Test
    void committedLifecycleConcurrentIdempotencyAndImmutability() throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var c = h.context;
            var jdbc = h.jdbc;
            var service = h.quality;
            var tx = h.tx;
            var fixture = h.fixture();
            ProductionQualityLifecycleMySqlTest.seedGrossReports(h, fixture);
            Long execution = fixture.execution(), node = fixture.selected().get(1), warehouse = fixture.warehouse();
            String tag = "DEFECT_TEST_" + UUID.randomUUID();
            var backup = Path.of(".local/production-quality-phase2b-20261016/quality-regression-before.txt");
            Files.writeString(backup, "SIMPLE_B:192.168.18.25:3306/mes_base_dev_business_simple_b\nrequestPrefix=" + tag + "\nexistingConflict=" + jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect WHERE request_key LIKE ?", Integer.class, tag + "%") + "\nproductionReports=" + jdbc.queryForList("SELECT id,execution_id,routing_node_id,quantity_delta FROM mes_production_report WHERE execution_id=?", execution));
            var before = jdbc.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution);
            ProductionReportMySqlTest.actor();
            try {
                var create = command(tag, 0, execution, node, warehouse, "2.123456");
                var first = tx.execute(s -> service.create(create)).record();
                h.declarations.add(first.getDeclarationId());
                assertEquals(first.getId(), tx.execute(s -> service.create(create)).record().getId());
                var barrier = new CyclicBarrier(2);
                Callable<Integer> revise = () -> {
                    ProductionReportMySqlTest.actor();
                    try {
                        barrier.await();
                        return tx.execute(s -> {
                            service.revise(first.getDeclarationId(), command("REV_" + UUID.randomUUID(), 1, execution, node, warehouse, "4"));
                            return 200;
                        });
                    } catch (FoundationException e) {
                        return e.code().httpStatus();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                var a = pool.submit(revise);
                var b = pool.submit(revise);
                var results = List.of(a.get(), b.get());
                assertEquals(1, results.stream().filter(x -> x == 200).count());
                assertEquals(1, results.stream().filter(x -> x == 409).count());
                assertEquals(2, tx.execute(s -> service.latest(first.getDeclarationId())).getRevisionNo());
                // Dedicated filtered SQL and latest-version aggregate on real MySQL.
                var effectiveBeforeVoid = tx.execute(s -> service.sumEffectiveDefectQty(execution, node));
                var rows = jdbc.queryForList("SELECT id,revision_no,defect_qty,record_action FROM mes_production_defect WHERE declaration_id=? ORDER BY revision_no", first.getDeclarationId());
                assertEquals(2, rows.size());
                assertThrows(Exception.class, () -> jdbc.update("UPDATE mes_production_defect SET defect_qty=99 WHERE id=?", first.getId()));
                assertThrows(Exception.class, () -> jdbc.update("DELETE FROM mes_production_defect WHERE id=?", first.getId()));
                assertThrows(FoundationException.class, () -> tx.execute(s -> service.create(command(tag, 0, execution, node, warehouse, "3"))));
                var voidCommand = new Command("VOID_" + UUID.randomUUID(), 2, "TEST ONLY验收结束作废", null);
                tx.execute(s -> service.voidDeclaration(first.getDeclarationId(), voidCommand));
                assertEquals(0, effectiveBeforeVoid.subtract(new BigDecimal("4"))
                        .compareTo(tx.execute(s -> service.sumEffectiveDefectQty(execution, node))));
                assertEquals(3, tx.execute(s -> service.history(first.getDeclarationId(), 0, 20)).total());
                // Failure rollback then retry leaves exactly one result, including a concurrently repeated key.
                var retry = command("RETRY_" + UUID.randomUUID(), 0, execution, node, warehouse, "1");
                tx.executeWithoutResult(s -> {
                    service.create(retry);
                    s.setRollbackOnly();
                });
                Callable<MesProductionDefectEntity> same = () -> {
                    ProductionReportMySqlTest.actor();
                    try {
                        barrier.await();
                        return tx.execute(s -> service.create(retry)).record();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                var x = pool.submit(same);
                var y = pool.submit(same);
                var one = x.get();
                h.declarations.add(one.getDeclarationId());
                assertEquals(one.getId(), y.get().getId());
                tx.execute(s -> service.voidDeclaration(one.getDeclarationId(), new Command("VOID_" + UUID.randomUUID(), 1, "TEST ONLY验收作废", null)));
                assertEquals(before, jdbc.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", execution));
                Files.writeString(Path.of(".local/production-quality-phase2b-20261016/quality-regression-acceptance.txt"), "SIMPLE_B verified\nexecution=" + execution + "\naffectedNode=" + node + "\ndeclaration=" + first.getDeclarationId() + "\nretryDeclaration=" + one.getDeclarationId() + "\nversions=3 and 2; all VOID; report rows unchanged; no inventory or external ERP writes\n");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
