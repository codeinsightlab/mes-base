package dev.issuefixture;

import com.github.pagehelper.PageHelper;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: resolve the designated SIMPLE_B binding, SELECT only.
 */
@EnabledIfEnvironmentVariable(named = "MES_WORKSPACE_MYSQL_ACCEPTANCE", matches = "true")
class WorkOrderProgressMySqlTest {
    @Test
    void pagesTheConfiguredOrdersAndAggregatesOnlyTheirFormalCompletionFacts() throws Exception {
        var configuration = new Configuration(new Environment("SIMPLE_B READ ONLY", new JdbcTransactionFactory(), AcceptanceBusinessDatabase.source()));
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        PersistenceFactories.pagination(configuration);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            session.getConnection().setReadOnly(true);
            assertEquals("mes_base_dev_business_simple_b", session.getConnection().getCatalog());
            var mapper = session.getMapper(WorkOrderProgressExtraMapper.class);
            try (var page = PageHelper.offsetPage(0, 5)) {
                var orders = mapper.orders(null, null);
                assertTrue(orders.size() <= 5);
                long total = page.getTotal();
                if (!orders.isEmpty()) {
                    var ids = orders.stream().map(Order::id).toList();
                    var totals = mapper.completions(ids);
                    assertTrue(totals.stream().allMatch(t -> ids.contains(t.workorderId())));
                    System.out.println("SIMPLE_B workorder progress: total=" + total + ", page=" + orders.size() + ", completedOrders=" + totals.size());
                }
            }
            try (var statement = session.getConnection().prepareStatement(
                    "SELECT w.id, w.code, SUM(c.effective_qty) AS quantity FROM workorder w JOIN mes_production_execution e ON e.workorder_id=w.id JOIN mes_execution_completion c ON c.execution_id=e.id GROUP BY w.id,w.code ORDER BY w.id DESC LIMIT 1");
                 var completed = statement.executeQuery()) {
                if (completed.next()) {
                    long id = completed.getLong("id");
                    var expected = completed.getBigDecimal("quantity");
                    var totals = mapper.completions(List.of(id));
                    assertEquals(1, totals.size());
                    assertEquals(0, expected.compareTo(totals.getFirst().quantity()));
                    System.out.println("SIMPLE_B completed workorder verified: id=" + id + ", quantity=" + expected.toPlainString());
                } else {
                    System.out.println("SIMPLE_B has no formal completion facts; no sample facts were written.");
                }
            }
            session.rollback();
        }
    }
}
