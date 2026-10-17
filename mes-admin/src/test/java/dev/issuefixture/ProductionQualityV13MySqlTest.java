package dev.issuefixture;

import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real designated MySQL; creates only isolated TEST fixture facts, no physical handover or ERP claim.
 */
@EnabledIfEnvironmentVariable(named = "MES_QUALITY_V13_MYSQL", matches = "true")
class ProductionQualityV13MySqlTest {
    record Fixture(long execution, long order, long routing, long warehouse, List<Long> nodes, String tag) {
    }

    static BigDecimal d(int v) {
        return BigDecimal.valueOf(v);
    }

    static Fixture fixture(ProductionQualityLifecycleMySqlTest.Harness h, int... reports) {
        return h.transaction(() -> {
            var j = h.jdbc;
            String tag = "TEST_QUALITY13_" + UUID.randomUUID().toString().substring(0, 8);
            long material = j.queryForObject("SELECT id FROM material WHERE status='ENABLED' ORDER BY id LIMIT 1", Long.class);
            long operation = j.queryForObject("SELECT id FROM operation WHERE status='ENABLED' ORDER BY id LIMIT 1", Long.class);
            long warehouse = j.queryForObject("SELECT id FROM wm_warehouse WHERE status='ENABLED' ORDER BY id LIMIT 1", Long.class);
            long routing = ProductionReportMySqlTest.insert(j, "INSERT INTO routing(code,name,version,status,revision,published_at) VALUES(?,?,1,'PUBLISHED',0,NOW())", tag, tag);
            j.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) SELECT ?,id,code,name FROM material WHERE id=?", routing, material);
            var nodes = new ArrayList<Long>();
            for (int i = 0; i < 5; i++)
                nodes.add(ProductionReportMySqlTest.insert(j, "INSERT INTO routing_node(routing_id,node_code,operation_id,display_order,operation_code,operation_name) SELECT ?,?,id,?,code,name FROM operation WHERE id=?", routing, Character.toString((char) ('A' + i)), i, operation));
            for (int i = 1; i < 5; i++)
                j.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(?,?,?)", routing, nodes.get(i - 1), nodes.get(i));
            long order = ProductionReportMySqlTest.insert(j, "INSERT INTO workorder(code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?,?,100,?,'RELEASED',0,NOW(),NOW())", tag, material, routing);
            j.update("INSERT INTO workorder_erp_extra(id) VALUES(?)", order);
            long execution = ProductionReportMySqlTest.insert(j, "INSERT INTO mes_production_execution(execution_no,workorder_id,product_id,routing_id,target_qty,status,start_time,remark,create_by,create_time) VALUES(?,?,?,?,100,'RUNNING',NOW(),?,'TEST_ONLY_Q13',NOW())", tag, order, material, routing, tag);
            j.update("INSERT INTO mes_execution_path(id,routing_id,path_status,revision,selected_at,locked_at) VALUES(?,?,'LOCKED',1,NOW(),NOW())", execution, routing);
            for (int i = 0; i < 5; i++) {
                j.update("INSERT INTO mes_execution_path_node(execution_id,sequence_no,routing_node_id) VALUES(?,?,?)", execution, i + 1, nodes.get(i));
                j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',?,100,100,NOW(),'TEST ONLY V13 historical normal report',NOW())", execution, nodes.get(i), reports[i]);
            }
            ProductionInputTestData.confirmed(j, execution, d(100));
            return new Fixture(execution, order, routing, warehouse, List.copyOf(nodes), tag);
        });
    }

    static String defect(ProductionQualityLifecycleMySqlTest.Harness h, Fixture f, int node, int qty) {
        return h.quality.create(new ProductionDefectModels.Command(UUID.randomUUID()
                        .toString(), null, null, new Facts(f.execution(), f.nodes().get(node), f.nodes()
                        .getLast(), null, d(qty), "PROCESS", f.tag(), null, null, "SAME_NODE", f.nodes()
                        .get(node), null, null, LocalDateTime.of(2026, 1, 1, 10, 0)), new Assessment(Attribution.INDEPENDENT, null, "TEST ONLY reviewed independent five-node facts")))
                .record().getDeclarationId();
    }

    static Command action(Action type, String parent, int scrap, int rework, int processed, int recovered, int failed) {
        return new Command(UUID.randomUUID()
                .toString(), 1, type, parent, d(scrap), d(rework), d(processed), d(recovered), d(failed), LocalDateTime.of(2026, 1, 1, 10, 0), "TEST ONLY quality13 result", null);
    }

    static void eq(int n, BigDecimal v) {
        assertEquals(0, d(n).compareTo(v));
    }

    static String start(QualityTreatmentService service, String id) {
        var a = service.execute(id, action(Action.ALLOCATE, null, 6, 4, 0, 0, 0)).summary().events().stream()
                .filter(e -> Action.ALLOCATE.name().equals(e.getActionType())).findFirst().orElseThrow().getId();
        return service.execute(id, action(Action.START, a, 0, 4, 0, 0, 0)).summary().events().stream()
                .filter(e -> Action.START.name().equals(e.getActionType())).findFirst().orElseThrow().getId();
    }

    @Test
    void realMysqlCorrectionsReprojectPropagationAndReceiptWithoutChangingHistory() throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness()) {
            h.context.registerBean(ProductionIdentityService.class);
            h.context.registerBean(ProductionReceiptService.class);
            var t = h.context.getBean(QualityTreatmentService.class);
            var q = h.context.getBean(ProductionQuantityService.class);
            var receipt = h.context.getBean(ProductionReceiptService.class);
            var f = fixture(h, 100, 100, 100, 100, 100);
            String id = defect(h, f, 1, 10);
            h.jdbc.update("INSERT INTO mes_production_receipt(id,receipt_no,execution_id,warehouse_id,record_type,quantity_delta,operator_user_id,recorded_by,occurred_at,create_time,reason) VALUES(?,?,?,?,'RECEIPT',98,100,100,NOW(),NOW(),'TEST ONLY stability historical handover')", UUID.randomUUID()
                    .toString(), f.tag(), f.execution(), f.warehouse());
            var allocated = action(Action.ALLOCATE, null, 2, 8, 0, 0, 0);
            t.execute(id, allocated);
            var start = action(Action.START, allocated.requestId(), 0, 6, 0, 0, 0);
            t.execute(id, start);
            var result = action(Action.RESULT, start.requestId(), 0, 0, 4, 3, 1);
            t.execute(id, result);
            var original = h.jdbc.queryForMap("SELECT * FROM mes_quality_treatment WHERE id=?", result.requestId());
            t.execute(id, action(Action.CANCEL_START, start.requestId(), 0, 2, 0, 0, 0));
            t.execute(id, action(Action.CANCEL_ALLOC, allocated.requestId(), 2, 4, 0, 0, 0));
            var correction = action(Action.CORRECT_RESULT, result.requestId(), 0, 0, 3, 1, 2);
            t.execute(id, correction);
            t.execute(id, correction);
            eq(91, q.getExecutionQuantities(f.execution()).nodes().getLast().effectiveOutputQty());
            eq(7, receipt.summary(f.execution()).excess());
            assertEquals(original, h.jdbc.queryForMap("SELECT * FROM mes_quality_treatment WHERE id=?", result.requestId()));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE id=?", Integer.class, correction.requestId()));
            assertThrows(DataAccessException.class, () -> h.jdbc.update("DELETE FROM mes_quality_treatment WHERE id=?", correction.requestId()));
            eq(4, t.summary(id).startedQty());
            eq(3, t.summary(id).processedQty());
            eq(1, t.summary(id).recoveredQty());
            System.out.println("STABILITY_QUALITY_CORRECTION_MYSQL execution=" + f.execution() + " output=91 historicalReceipt=98 excess=7");
        }
    }

    @Test
    void fiveNodeLateQualityImmutableReportsReceiptExcessAndConcurrentRecovery() throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var c = h.context;
            c.registerBean(ProductionIdentityService.class);
            c.registerBean(ProductionReceiptService.class);
            var q = c.getBean(ProductionQuantityService.class);
            var t = c.getBean(QualityTreatmentService.class);
            var receipts = c.getBean(ProductionReceiptService.class);
            var f = fixture(h, 100, 100, 100, 100, 100);
            var before = h.jdbc.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", f.execution());
            String receipt = UUID.randomUUID().toString();
            h.jdbc.update("INSERT INTO mes_production_receipt(id,receipt_no,execution_id,warehouse_id,record_type,quantity_delta,operator_user_id,recorded_by,occurred_at,create_time,reason) VALUES(?,?,?,?,'RECEIPT',98,100,100,NOW(),NOW(),'TEST ONLY historical handover fixture, not physical handover')", receipt, f.tag(), f.execution(), f.warehouse());
            var id = defect(h, f, 1, 10);
            var a = q.getExecutionQuantities(f.execution());
            eq(90, a.nodes().getLast().effectiveOutputQty());
            eq(8, receipts.summary(f.execution()).excess());
            for (int i = 2; i < 5; i++) eq(10, a.nodes().get(i).historicalOverrunQty());
            var parent = start(t, id);
            var result = action(Action.RESULT, parent, 0, 0, 4, 4, 0);
            var x = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                return t.execute(id, result);
            });
            var y = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                return t.execute(id, result);
            });
            x.get();
            y.get();
            eq(94, q.getExecutionQuantities(f.execution()).nodes().getLast().effectiveOutputQty());
            assertThrows(FoundationException.class, () -> t.execute(id, action(Action.RESULT, parent, 0, 0, 4, 4, 0)));
            assertEquals(before, h.jdbc.queryForList("SELECT * FROM mes_production_report WHERE execution_id=? ORDER BY id", f.execution()));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_receipt WHERE execution_id=?", Integer.class, f.execution()));
            var immutable = assertThrows(DataAccessException.class, () -> h.jdbc.update("UPDATE mes_quality_treatment SET recovered_qty=0 WHERE id=?", result.requestId()));
            assertTrue(immutable.getMostSpecificCause().getMessage().contains("Quality treatment facts are immutable"));
            var partial = fixture(h, 100, 100, 100, 80, 60);
            var p = defect(h, partial, 1, 10);
            eq(10, q.getExecutionQuantities(partial.execution()).nodes().get(3).availableQty());
            var ps = start(t, p);
            t.execute(p, action(Action.RESULT, ps, 0, 0, 4, 3, 1));
            eq(13, q.getExecutionQuantities(partial.execution()).nodes().get(3).availableQty());
            eq(60, q.getExecutionQuantities(partial.execution()).nodes().getLast().effectiveOutputQty());
            Files.writeString(Path.of(".local/quality-v13-20261016/mysql-result.txt"), "SIMPLE_B datasource=8; five-node TEST ONLY fixture; execution=" + f.execution() + "; workorder=" + f.order() + "; declaration=" + id + "; late output=90; historical receipt=98; excess=8; recovered=4; output=94; normal reports unchanged; concurrent same result only once; partialExecution=" + partial.execution() + "; partialDeclaration=" + p + "; success=3 failed=1; terminal=60; D available=13; immutable treatment update rejected\n");
        }
    }
}
