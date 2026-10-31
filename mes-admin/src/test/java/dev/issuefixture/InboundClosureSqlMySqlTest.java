package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.MesProductionCombinationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionCodeRules;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import java.util.ArrayList;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Current mapped SELECTs on the verified mes_base_dev_perf_test binding; no setup, DDL, or business writes.
 */
@EnabledIfEnvironmentVariable(named = "MES_INBOUND_SQL_MYSQL_ACCEPTANCE", matches = "true")
class InboundClosureSqlMySqlTest {
    @Test
    void receiptProofAndProgressQueriesExecuteOnTheVerifiedDatabaseReadOnly() throws Exception {
        var configuration = new Configuration(new Environment("mes_base_dev_perf_test READ ONLY",
                new JdbcTransactionFactory(), AcceptanceBusinessDatabase.performanceTestSource()));
        configuration.addMapper(MesProductionExecutionMapper.class);
        configuration.addMapper(WorkorderMapper.class);
        configuration.addMapper(MaterialMapper.class);
        configuration.addMapper(RoutingMapper.class);
        configuration.addMapper(StockDocumentExtraMapper.class);
        configuration.addMapper(ProductionDefectListExtraMapper.class);
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var connection = session.getConnection();
            connection.setReadOnly(true);
            try (var identity = connection.prepareStatement("SELECT DATABASE()");
                 var row = identity.executeQuery()) {
                assertTrue(row.next());
                assertEquals("mes_base_dev_perf_test", row.getString(1));
            }
            try (var schema = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                            + "AND table_name='mes_quality_treatment' AND column_name='returned_qty'");
                 var columns = schema.executeQuery()) {
                assertTrue(columns.next());
                assertEquals(1, columns.getInt(1),
                        "The mes_base_dev_perf_test database needs the existing V48.1 quality schema upgrade before current queries; this test never migrates it");
            }
            try (var schema = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_production_execution' AND column_name='execution_no'"); var columns = schema.executeQuery()) {
                assertTrue(columns.next());
                assertEquals(0, columns.getInt(1), "Current execution mappings require formal V54");
            }
            var orders = new ArrayList<Long>();
            var executions = new ArrayList<Long>();
            try (var select = connection.prepareStatement(
                    "SELECT id,workorder_id FROM mes_production_execution ORDER BY id DESC LIMIT 3");
                 var rows = select.executeQuery()) {
                while (rows.next()) {
                    executions.add(rows.getLong(1));
                    orders.add(rows.getLong(2));
                }
            }
            assertTrue(!executions.isEmpty(), "The verified database needs existing read-only production samples");
            var identities = new ProductionIdentityService(session.getMapper(MesProductionExecutionMapper.class),
                    session.getMapper(WorkorderMapper.class), session.getMapper(MaterialMapper.class),
                    session.getMapper(RoutingMapper.class), session.getMapper(MesProductionCombinationMapper.class));
            for (Long execution : executions) {
                var identity = identities.read(execution);
                assertNotNull(identity.batchNo(), "Current verified samples have formal execution batch properties");
                String code = ProductionCodeRules.format(new ProductionScanCode(identity.workorderCode(), identity.productCode(), identity.batchNo()));
                assertEquals(identity, identities.resolve(code));
                var inbound = session.getMapper(StockDocumentExtraMapper.class);
                assertNotNull(inbound.formalProductionInbound(execution));
                inbound.latestInboundAuditId(execution);
                assertTrue(inbound.invalidProductionInboundLines(execution) >= 0);
                var proofs = session.getMapper(ProductionDefectListExtraMapper.class).selectReceiptProofs(execution);
                assertNotNull(proofs);
                for (var proof : proofs)
                    assertEquals(1L, proof.validCount(), "Current quality receipts remain valid without scan binding joins");
            }
            var progress = session.getMapper(WorkOrderProgressExtraMapper.class);
            assertNotNull(progress.completions(orders));
            assertNotNull(progress.quality(orders));
            session.rollback();
        }
    }
}
