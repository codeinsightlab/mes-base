package dev.issuefixture;

import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;

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

/**
 * LIVE MYSQL: designated binding, real center identity reads, generated XML, rollback and committed concurrent commands.
 * Concurrency leaves one clearly tagged QA execution and immutable REPORT/ADJUSTMENT audit facts with net zero quantity.
 */
@EnabledIfEnvironmentVariable(named = "MES_REPORT_MYSQL_ACCEPTANCE", matches = "true")
class ProductionReportMySqlTest {
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
        c.register(QualityTreatmentService.class, ExecutionQualityReferenceQueryImpl.class, ProductionReportService.class, ProductionQuantityService.class, ReportUserReferenceService.class, ProductionChainLocks.class, ProductionExecutionRules.class, ExecutionPathService.class);
        c.refresh();
        return c;
    }

    static <T> void register(AnnotationConfigApplicationContext c, SqlSessionTemplate template, Class<T> type) {
        c.registerBean(type.getSimpleName(), type, () -> template.getMapper(type));
    }

    static void actor() {
        var user = new SysUser();
        user.setUserId(100L);
        user.setUserName("TEST_ONLY_REPORT");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(100L, 1L, user, Set.of()), null, List.of()));
    }

    record Fixture(long executionId, long firstNode, String tag) {
    }

    static Fixture createFixture(JdbcTemplate jdbc) {
        String tag = "TEST_REPORT_" + UUID.randomUUID().toString().substring(0, 12);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE code=?", Integer.class, tag));
        var source = jdbc.queryForMap("SELECT e.id,e.workorder_id,e.product_id,e.routing_id FROM mes_production_execution e JOIN mes_execution_path p ON p.id=e.id WHERE p.path_status='LOCKED' AND EXISTS(SELECT 1 FROM mes_execution_path_node n WHERE n.execution_id=e.id) ORDER BY e.id LIMIT 1");
        long order = insert(jdbc, "INSERT INTO workorder(code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?,?,10,?,'RELEASED',0,NOW(),NOW())", tag, source.get("product_id"), source.get("routing_id"));
        jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(?)", order);
        long execution = insert(jdbc, "INSERT INTO mes_production_execution(execution_no,workorder_id,product_id,routing_id,target_qty,status,remark,create_by,create_time) VALUES(?,?,?,?,10,'READY',?,'TEST_ONLY_REPORT',NOW())", tag, order, source.get("product_id"), source.get("routing_id"), tag);
        jdbc.update("INSERT INTO mes_execution_path(id,routing_id,path_status,revision,selected_at,locked_at) VALUES(?,?,'LOCKED',1,NOW(),NOW())", execution, source.get("routing_id"));
        jdbc.update("INSERT INTO mes_execution_path_node(execution_id,sequence_no,routing_node_id) SELECT ?,sequence_no,routing_node_id FROM mes_execution_path_node WHERE execution_id=?", execution, source.get("id"));
        long node = jdbc.queryForObject("SELECT routing_node_id FROM mes_execution_path_node WHERE execution_id=? AND sequence_no=1", Long.class, execution);
        ProductionInputTestData.confirmed(jdbc, execution, BigDecimal.TEN);
        return new Fixture(execution, node, tag);
    }

    static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    static Supplement command(Fixture f, String quantity) {
        return new Supplement(f.executionId(), f.firstNode(), new BigDecimal(quantity), 100L, LocalDateTime.of(2026, 1, 1, 10, 0), null, f.tag());
    }

    @Test
    void realMysqlLostResponseAndConcurrentReplayAppendEachIntentOnlyOnce() throws Exception {
        try (var c = context(); var executor = Executors.newFixedThreadPool(2)) {
            var jdbc = c.getBean(JdbcTemplate.class);
            var reports = c.getBean(ProductionReportService.class);
            var tx = new TransactionTemplate(c.getBean("businessTransactionManager", OwnedTransactionManager.class));
            var f = tx.execute(status -> createFixture(jdbc));
            var cmd = command(f, "4");
            var barrier = new CyclicBarrier(2);
            Callable<Long> submit = () -> {
                actor();
                try {
                    barrier.await();
                    return reports.supplement(cmd).getId();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var a = executor.submit(submit);
            var b = executor.submit(submit);
            Long original = a.get();
            assertEquals(original, b.get());
            actor();
            try {
                assertEquals(original, reports.supplement(cmd).getId());
                var changed = new Supplement(cmd.executionId(), cmd.routingNodeId(), BigDecimal.ONE, cmd.operatorUserId(), cmd.reportTime(), null, cmd.reason(), cmd.requestId());
                assertEquals(ErrorCode.REPORT_REQUEST_CONFLICT, assertThrows(FoundationException.class, () -> reports.supplement(changed)).code());
                var login = new SysUser();
                login.setUserId(101L);
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(101L, 1L, login, Set.of()), null, List.of()));
                assertEquals(ErrorCode.REPORT_REQUEST_CONFLICT, assertThrows(FoundationException.class, () -> reports.supplement(cmd)).code());
                actor();
                var correction = new Adjustment(BigDecimal.ONE.negate(), f.tag());
                var adjusted = reports.adjust(original, correction);
                assertEquals(adjusted.getId(), reports.adjust(original, correction).getId());
                var cancel = new VoidOriginal(f.tag());
                var voided = reports.voidOriginalReport(original, cancel);
                assertEquals(voided.getId(), reports.voidOriginalReport(original, cancel).getId());
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_id=?", Integer.class, f.executionId()));
                assertEquals(0, jdbc.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=?", BigDecimal.class, f.executionId())
                        .signum());
                assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE mes_production_report SET reason='forbidden' WHERE id=?", original));
                System.out.println("STABILITY_ADMIN_REPLAY_MYSQL execution=" + f.executionId() + " facts=3 net=0");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void realMysqlRollbackConstraintsAndImmutableLedger() throws Exception {
        try (var c = context()) {
            var jdbc = c.getBean(JdbcTemplate.class);
            var reports = c.getBean(ProductionReportService.class);
            var quantities = c.getBean(ProductionQuantityService.class);
            var tx = new TransactionTemplate(c.getBean("businessTransactionManager", OwnedTransactionManager.class));
            long[] execution = {0};
            actor();
            try {
                tx.executeWithoutResult(status -> {
                    var f = createFixture(jdbc);
                    execution[0] = f.executionId();
                    var first = reports.supplement(command(f, "4.123456"));
                    reports.adjust(first.getId(), new Adjustment(new BigDecimal("1.000001"), f.tag()));
                    assertEquals(0, new BigDecimal("5.123457").compareTo(quantities.getNodeQuantity(f.executionId(), f.firstNode())
                            .reportedQty()));
                    assertThrows(FoundationException.class, () -> reports.adjust(first.getId(), new Adjustment(new BigDecimal("-6"), f.tag())));
                    reports.voidOriginalReport(first.getId(), new VoidOriginal(f.tag()));
                    assertEquals(0, quantities.getNodeQuantity(f.executionId(), f.firstNode()).reportedQty().signum());
                    assertThrows(FoundationException.class, () -> reports.voidOriginalReport(first.getId(), new VoidOriginal(f.tag())));
                    assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE mes_production_report SET quantity_delta=99 WHERE id=?", first.getId()));
                    assertThrows(RuntimeException.class, () -> jdbc.update("DELETE FROM mes_production_report WHERE id=?", first.getId()));
                    assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',0,100,100,NOW(),?,NOW())", f.executionId(), f.firstNode(), f.tag()));
                    var page = reports.page(f.executionId(), f.firstNode(), 0, 20);
                    assertEquals(3, page.total());
                    assertEquals(3, page.items().size());
                    assertTrue(page.userNames().containsKey(100L));
                    assertTrue(reports.getDetail(first.getId()).userNames().containsKey(100L));
                    status.setRollbackOnly();
                });
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_id=?", Integer.class, execution[0]));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE id=?", Integer.class, execution[0]));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void realMysqlConcurrentCommandsCannotExceedConfirmedInputAndPersistAuditableNetZeroFixture() throws Exception {
        try (var c = context(); var executor = Executors.newFixedThreadPool(2)) {
            var jdbc = c.getBean(JdbcTemplate.class);
            var reports = c.getBean(ProductionReportService.class);
            var tx = new TransactionTemplate(c.getBean("businessTransactionManager", OwnedTransactionManager.class));
            var f = tx.execute(status -> createFixture(jdbc));
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> submit = () -> {
                actor();
                try {
                    barrier.await();
                    reports.supplement(command(f, "6"));
                    return true;
                } catch (FoundationException denied) {
                    assertEquals(ErrorCode.REPORT_QUANTITY_CONFLICT, denied.code());
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var a = executor.submit(submit);
            var b = executor.submit(submit);
            assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
            assertEquals(6, jdbc.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=?", Integer.class, f.executionId()));
            long original = jdbc.queryForObject("SELECT id FROM mes_production_report WHERE execution_id=?", Long.class, f.executionId());
            actor();
            try {
                reports.voidOriginalReport(original, new VoidOriginal(f.tag() + " 并发验收后归零"));
            } finally {
                SecurityContextHolder.clearContext();
            }
            assertEquals(0, jdbc.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report WHERE execution_id=?", Integer.class, f.executionId()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_id=?", Integer.class, f.executionId()));
            Files.writeString(Path.of(".local/production-report-20261015/live-acceptance.txt"), "SIMPLE_B datasource=8 host=192.168.18.25 port=3306 database=mes_base_dev_business_simple_b\nexecutionId=" + f.executionId() + "\nfirstNode=" + f.firstNode() + "\ntag=" + f.tag() + "\nconcurrentSuccess=1 concurrentRejected=1 ledgerRows=2 effectiveQuantity=0\n");
        }
    }
}
