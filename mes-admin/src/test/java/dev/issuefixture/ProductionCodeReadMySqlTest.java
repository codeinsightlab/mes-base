package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import java.util.List;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import dev.mesbase.business.production.domain.ProductionQrCode;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-only real MySQL contract check; no fixture creation or business writes.
 */
@EnabledIfSystemProperty(named = "production.code.mysql.read", matches = "true")
class ProductionCodeReadMySqlTest {
    @Test
    void exactStoredCodeLoadsExecutionPathAndReportsThroughCurrentXml() throws Exception {
        var source = AcceptanceBusinessDatabase.performanceTestSource();
        var config = new Configuration(new Environment("READ ONLY CODE CONTRACT", new JdbcTransactionFactory(), source));
        config.addMapper(MesProductionExecutionMapper.class);
        config.addMapper(MesExecutionPathMapper.class);
        config.addMapper(MesProductionReportMapper.class);
        config.addMapper(ProductionQuantityMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            var connection = session.getConnection();
            connection.setReadOnly(true);
            assertEquals("mes_base_dev_perf_test", connection.getCatalog());
            String code = System.getProperty("production.code.sample");
            assertNotNull(code, "Provide a verified existing sample code explicitly");
            assertEquals(code, ProductionQrCode.normalize(code));
            var execution = session.getMapper(MesProductionExecutionMapper.class).selectByCode(code);
            assertNotNull(execution);
            assertEquals(code, execution.getExecutionCode());
            assertEquals(code, session.getMapper(MesProductionExecutionMapper.class)
                    .selectByPrimaryKey(execution.getId()).getExecutionCode());
            var path = session.getMapper(MesExecutionPathMapper.class).selectByPrimaryKey(code);
            assertNotNull(path);
            assertEquals(code, path.getId());
            var filter = new MesProductionReportExample();
            filter.createCriteria().andExecutionCodeEqualTo(code);
            var reports = session.getMapper(MesProductionReportMapper.class).selectByExample(filter);
            assertTrue(!reports.isEmpty(), "Choose a sample with existing formal reports");
            assertTrue(reports.stream().allMatch(row -> code.equals(row.getExecutionCode())));
            var quantities = session.getMapper(ProductionQuantityMapper.class);
            assertTrue(quantities.inputBalance(code).confirmedQty().signum() > 0);
            assertTrue(!quantities.nodeTotals(code).isEmpty());
            assertTrue(quantities.nodeTotalsBatch(List.of(code)).stream()
                    .allMatch(row -> code.equals(row.executionCode())));
            assertTrue(quantities.inputBalances(List.of(code)).stream()
                    .allMatch(row -> code.equals(row.executionCode())));
            assertTrue(quantities.workorderQuantities(execution.getWorkorderId()).stream()
                    .anyMatch(row -> code.equals(row.executionCode())));
            session.rollback();
        }
    }
}
