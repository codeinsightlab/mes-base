package dev.issuefixture;

import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.quality.domain.ProductionDefectModels;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real designated MySQL; creates only isolated TEST fixture facts, no physical handover or ERP claim.
 */
@EnabledIfEnvironmentVariable(named = "MES_QUALITY_WORKFLOW_MYSQL", matches = "true")
class QualityWorkflowMySqlTest {
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
            long order = ProductionReportMySqlTest.insert(j, "INSERT INTO workorder(source,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES('LOCAL',?,?,100,?,'RELEASED',0,NOW(),NOW())", tag, material, routing);
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
                        .toString(), null, null, new Facts(f.execution(), f.nodes()
                        .get(node), d(qty), "PROCESS", f.tag(), null, null, LocalDateTime.of(2026, 1, 1, 10, 0)), new Assessment(Attribution.INDEPENDENT, null, "TEST ONLY reviewed independent five-node facts")))
                .record().getDeclarationId();
    }


    @Test
    void currentQualityProjectionAndImmutableLedgerUseTheLatestContract() throws Exception {
        try (var harness = new ProductionQualityLifecycleMySqlTest.Harness()) {
            var fixture = fixture(harness, 100, 100, 100, 100, 100);
            var id = defect(harness, fixture, 1, 10);
            var treatment = harness.context.getBean(QualityTreatmentService.class).summary(id);
            assertEquals("PENDING", treatment.state().getValue());
            assertEquals(0, d(10).compareTo(treatment.pendingDispositionQty()));
            assertEquals(0, treatment.returnedQty().signum());
            assertThrows(DataAccessException.class, () -> harness.transaction(() -> {
                harness.jdbc.update("UPDATE mes_quality_treatment SET reason='TEST ONLY rejected mutation' WHERE declaration_id=?", id);
                return null;
            }));
            assertEquals(harness.oldFacts, harness.jdbc.queryForList("SELECT * FROM mes_production_defect WHERE id<=? ORDER BY id", harness.maxOld));
        }
    }
}
