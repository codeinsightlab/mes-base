package dev.mesbase.business.production;

import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.ProductionInputBalance;
import java.sql.DriverManager;
import java.util.List;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real parameterized SQL in isolated H2; live MySQL validation is separate.
 */
class BatchQuantitySqlTest {
    @Test
    void batchBalancesAndReportTotalsMatchSingleReadsIncludingReservationsAndInvalidEvidence() throws Exception {
        String url = "jdbc:h2:mem:quantity_batch;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(url); var s = c.createStatement()) {
            s.execute("CREATE TABLE mes_execution_path_node(execution_id BIGINT,routing_node_id BIGINT)");
            s.execute("CREATE TABLE mes_production_report(execution_id BIGINT,routing_node_id BIGINT,quantity_delta DECIMAL(20,6))");
            s.execute("CREATE TABLE mes_material_issue_input(execution_id BIGINT,document_id BIGINT,submission_id BIGINT,input_kind VARCHAR(40),confirmed_at TIMESTAMP,source_mode VARCHAR(40),output_qty DECIMAL(20,6),void_result_status VARCHAR(40))");
            s.execute("CREATE TABLE wm_stock_document(id BIGINT,source_execution_id BIGINT)");
            s.execute("CREATE TABLE mes_material_issue_submission(id BIGINT,document_id BIGINT,status VARCHAR(40))");
            s.execute("INSERT INTO mes_execution_path_node VALUES(1,11),(2,11),(2,12),(3,11)");
            s.execute("INSERT INTO mes_production_report VALUES(1,11,100),(1,11,-4),(2,11,200),(2,99,999),(3,11,9999)");
            s.execute("INSERT INTO wm_stock_document VALUES(1,1),(2,1),(3,2),(4,2),(5,2),(6,2)");
            s.execute("INSERT INTO mes_material_issue_submission VALUES(1,1,'SUCCEEDED'),(2,2,'SUCCEEDED'),(3,3,'SUCCEEDED'),(4,4,'UNKNOWN'),(5,5,'SUCCEEDED'),(6,6,'SUCCEEDED')");
            s.execute("INSERT INTO mes_material_issue_input VALUES(1,1,1,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'LOCAL_ONLY',100,NULL),(1,2,2,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'LOCAL_ONLY',3,'SUBMITTING'),(2,3,3,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'LOCAL_ONLY',7,'UNKNOWN'),(2,4,4,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'LOCAL_ONLY',999,NULL),(2,5,5,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'CONNECTED',888,NULL),(2,6,6,'PRODUCTION_INPUT',CURRENT_TIMESTAMP,'LOCAL_ONLY',10000,'SUCCEEDED')");
        }
        var config = new Configuration(new Environment("fixture", new JdbcTransactionFactory(), new UnpooledDataSource("org.h2.Driver", url, null)));
        config.addMapper(ProductionQuantityMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(ProductionQuantityMapper.class);
            var balances = mapper.inputBalances(List.of(1L, 2L, 3L));
            assertEquals(2, balances.size());
            assertEquals(103, mapper.inputBalance(1L).confirmedQty().intValueExact());
            assertEquals(3, mapper.inputBalance(1L).reservedQty().intValueExact());
            assertEquals(7, mapper.inputBalance(2L).confirmedQty().intValueExact());
            assertEquals(7, mapper.inputBalance(2L).reservedQty().intValueExact());
            for (var balance : balances)
                assertEquals(mapper.inputBalance(balance.executionId()), new ProductionInputBalance(balance.confirmedQty(), balance.reservedQty()));
            var totals = mapper.nodeTotalsBatch(List.of(1L, 2L));
            assertEquals(3, totals.size());
            for (var total : totals)
                assertEquals(0, total.reportedQty().compareTo(mapper.nodeTotals(total.executionId()).stream()
                        .filter(n -> n.routingNodeId().equals(total.routingNodeId())).findFirst().orElseThrow()
                        .reportedQty()));
        }
    }
}
