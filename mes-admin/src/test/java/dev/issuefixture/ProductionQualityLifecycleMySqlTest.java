package dev.issuefixture;

import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.integration.LocalProductionDefectErpGateway;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQuery;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LIVE MYSQL on verified SIMPLE_B. Uses new TEST_QREF identities only, never edits existing business facts.
 */
@EnabledIfEnvironmentVariable(named = "MES_QUALITY_REFERENCE_MYSQL", matches = "true")
@Timeout(90)
class ProductionQualityLifecycleMySqlTest {
    static final Path DIR = Path.of(".local/production-quality-phase2b-20261016");

    record Fixture(long execution, long order, long routing, List<Long> selected, List<Long> alternate, long warehouse,
                   String tag) {
    }

    static class Harness implements AutoCloseable {
        final AnnotationConfigApplicationContext context;
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final ProductionDefectService quality;
        final ExecutionPathService paths;
        final IMesProductionExecutionService executions;
        final ExecutionQualityReferenceQuery references;
        final List<String> declarations = new ArrayList<>();
        final long maxOld;
        final List<?> oldFacts;
        final long maxOldReport;
        final List<?> oldReports;

        Harness() throws Exception {
            context = ProductionDefectMySqlTest.context();
            jdbc = context.getBean(JdbcTemplate.class);
            context.registerBean(LocalProductionDefectErpGateway.class);
            context.registerBean(ProductionDefectService.class);
            context.registerBean(MesProductionExecutionServiceImpl.class);
            quality = context.getBean(ProductionDefectService.class);
            paths = context.getBean(ExecutionPathService.class);
            executions = context.getBean(IMesProductionExecutionService.class);
            references = context.getBean(ExecutionQualityReferenceQuery.class);
            tx = new TransactionTemplate(context.getBean("businessTransactionManager", PlatformTransactionManager.class));
            tx.setIsolationLevelName("ISOLATION_READ_COMMITTED");
            Files.createDirectories(DIR);
            maxOld = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM mes_production_defect", Long.class);
            oldFacts = jdbc.queryForList("SELECT * FROM mes_production_defect WHERE id<=? ORDER BY id", maxOld);
            Files.writeString(DIR.resolve("mysql-existing-quality-" + UUID.randomUUID() + ".yml"), new Yaml().dump(oldFacts));
            maxOldReport = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM mes_production_report", Long.class);
            oldReports = jdbc.queryForList("SELECT * FROM mes_production_report WHERE id<=? ORDER BY id", maxOldReport);
            Files.writeString(DIR.resolve("mysql-existing-reports-" + UUID.randomUUID() + ".yml"), new Yaml().dump(oldReports));
            ProductionReportMySqlTest.actor();
        }

        <T> T transaction(Supplier<T> action) {
            return tx.execute(s -> action.get());
        }

        Fixture fixture() {
            String tag = "TEST_QTY_" + UUID.randomUUID().toString().substring(0, 12);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE code=?", Integer.class, tag));
            var f = transaction(() -> {
                Long material = jdbc.queryForObject("SELECT id FROM material WHERE material_type='PRODUCT' AND status='ENABLED' ORDER BY id LIMIT 1", Long.class);
                Long operation = jdbc.queryForObject("SELECT id FROM operation WHERE status='ENABLED' ORDER BY id LIMIT 1", Long.class);
                Long warehouse = jdbc.queryForObject("SELECT id FROM wm_warehouse WHERE status='ENABLED' ORDER BY id LIMIT 1", Long.class);
                long routing = ProductionReportMySqlTest.insert(jdbc, "INSERT INTO routing(code,name,version,status,revision,published_at) VALUES(?,?,1,'PUBLISHED',0,NOW())", tag, tag);
                jdbc.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) SELECT ?,id,code,name FROM material WHERE id=?", routing, material);
                List<Long> nodes = new ArrayList<>();
                for (String code : List.of("A", "B", "C", "D"))
                    nodes.add(ProductionReportMySqlTest.insert(jdbc, "INSERT INTO routing_node(routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) SELECT ?,?,id,code,name,0,false FROM operation WHERE id=?", routing, code, operation));
                for (var edge : List.of(List.of(0, 1), List.of(1, 3), List.of(0, 2), List.of(2, 3)))
                    jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(?,?,?)", routing, nodes.get(edge.get(0)), nodes.get(edge.get(1)));
                long order = ProductionReportMySqlTest.insert(jdbc, "INSERT INTO workorder(code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?,?,10,?,'RELEASED',0,NOW(),NOW())", tag, material, routing);
                jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(?)", order);
                long execution = ProductionReportMySqlTest.insert(jdbc, "INSERT INTO mes_production_execution(execution_no,workorder_id,product_id,routing_id,target_qty,status,remark,create_by,create_time) VALUES(?,?,?,?,10,'READY',?,'TEST_ONLY_QREF',NOW())", tag, order, material, routing, tag);
                jdbc.update("INSERT INTO mes_execution_path(id,routing_id,path_status,revision,selected_at) VALUES(?,?,'SELECTED',1,NOW())", execution, routing);
                var selected = List.of(nodes.get(0), nodes.get(1), nodes.get(3));
                for (int i = 0; i < selected.size(); i++)
                    jdbc.update("INSERT INTO mes_execution_path_node(execution_id,sequence_no,routing_node_id) VALUES(?,?,?)", execution, i + 1, selected.get(i));
                return new Fixture(execution, order, routing, selected, List.of(nodes.get(0), nodes.get(2), nodes.get(3)), warehouse, tag);
            });
            try {
                Files.writeString(DIR.resolve(f.tag() + ".txt"), "SIMPLE_B datasource8 192.168.18.25:3306/mes_base_dev_business_simple_b\n" + f + "\nnew UUID conflict=0; new TEST ONLY data; pre-existing quality backed up\n");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return f;
        }

        Command command(Fixture f) {
            return new Command("QREF_" + UUID.randomUUID(), null, null, new Facts(f.execution(), f.selected()
                    .get(1), f.selected()
                    .get(1), null, new BigDecimal("2"), "PROCESS", f.tag(), null, null, "WAREHOUSE", null, f.warehouse(), "TEST ONLY lifecycle concurrency", LocalDateTime.of(2026, 1, 1, 10, 0)));
        }

        String create(Fixture f) {
            var id = quality.create(command(f)).record().getDeclarationId();
            synchronized (declarations) {
                declarations.add(id);
            }
            return id;
        }

        void invariant(Fixture f) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect d LEFT JOIN mes_production_execution e ON e.id=d.execution_id WHERE d.execution_id=? AND e.id IS NULL", Integer.class, f.execution()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_defect d LEFT JOIN mes_execution_path_node n ON n.execution_id=d.execution_id AND n.routing_node_id=d.affected_node_id WHERE d.execution_id=? AND n.id IS NULL", Integer.class, f.execution()));

        }

        public void close() {
            try {
                ProductionReportMySqlTest.actor();
                for (String id : declarations)
                    transaction(() -> {
                        var r = quality.latest(id);
                        if (!"VOID".equals(r.getRecordAction()))
                            quality.voidDeclaration(id, new Command("QREF_VOID_" + UUID.randomUUID(), r.getRevisionNo(), "TEST ONLY生命周期验收结束，保留历史作废", null));
                        return null;
                    });
                assertEquals(oldFacts, jdbc.queryForList("SELECT * FROM mes_production_defect WHERE id<=? ORDER BY id", maxOld));
                assertEquals(oldReports, jdbc.queryForList("SELECT * FROM mes_production_report WHERE id<=? ORDER BY id", maxOldReport));
            } finally {
                SecurityContextHolder.clearContext();
                context.close();
            }
        }
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void qualityCreationWithoutProcessedQuantityCannotCreateAnOrphanWhileDeleting() throws Exception {
        try (var h = new Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var f = h.fixture();
            var gate = new CountDownLatch(1);
            var create = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    await(gate);
                    return h.transaction(() -> {
                        h.create(f);
                        return 200;
                    });
                } catch (FoundationException e) {
                    return e.code().httpStatus();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var remove = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                try {
                    await(gate);
                    h.executions.deleteByIds(List.of(f.execution()));
                    return 204;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            gate.countDown();
            assertEquals(204, remove.get(10, TimeUnit.SECONDS));
            assertTrue(create.get(10, TimeUnit.SECONDS) != 200);
            h.invariant(f);
        }
    }

    static void seedGrossReports(Harness h, Fixture f) {
        ProductionInputTestData.confirmed(h.jdbc, f.execution(), BigDecimal.TEN);
        for (long node : f.selected().subList(0, 2))
            h.jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',10,100,100,NOW(),'TEST ONLY Phase 2B gross report',NOW())", f.execution(), node);
    }

    @Test
    void voidedHistoryStillProtectsIdentityWithGrossReportPrerequisites() throws Exception {
        try (var h = new Harness()) {
            var f = h.fixture();
            seedGrossReports(h, f);
            String id = h.create(f);
            h.quality.voidDeclaration(id, new Command("QREF_VOID_" + UUID.randomUUID(), 1, "TEST ONLY全部作废后仍须保护", null));
            assertEquals(0, h.transaction(() -> h.quality.sumEffectiveDefectQty(f.execution(), f.selected().get(1)))
                    .signum());
            assertTrue(h.transaction(() -> h.references.hasAnyHistory(f.execution())));
            assertEquals(ErrorCode.EXECUTION_REFERENCED, assertThrows(FoundationException.class, () -> h.executions.deleteByIds(List.of(f.execution()))).code());
            assertThrows(FoundationException.class, () -> h.paths.select(f.execution(), 1, f.alternate()));
            assertEquals(2, h.transaction(() -> h.quality.history(id, 0, 20)).total());
            h.invariant(f);
        }
    }
}
