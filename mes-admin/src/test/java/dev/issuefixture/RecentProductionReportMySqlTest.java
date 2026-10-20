package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.RecentProductionReportExtraMapper;
import com.github.pagehelper.PageHelper;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: verified existing SIMPLE_B binding, SELECT only, no service restart.
 */
@EnabledIfEnvironmentVariable(named = "MES_WORKSPACE_MYSQL_ACCEPTANCE", matches = "true")
class RecentProductionReportMySqlTest {
    @Test
    void currentConfiguredBusinessDatabaseExecutesTheActualRecentReportXml() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        var configuration = new Configuration(new Environment("SIMPLE_B READ ONLY", new JdbcTransactionFactory(), source));
        configuration.addMapper(RecentProductionReportExtraMapper.class);
        PersistenceFactories.pagination(configuration);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            session.getConnection().setReadOnly(true);
            assertEquals("mes_base_dev_business_simple_b", session.getConnection().getCatalog());
            var sql = configuration.getMappedStatement(RecentProductionReportExtraMapper.class.getName() + ".recent")
                    .getBoundSql(null).getSql();
            try (var statement = session.getConnection().prepareStatement("EXPLAIN " + sql + " LIMIT 6");
                 var plan = statement.executeQuery()) {
                while (plan.next()) {
                    System.out.println("SIMPLE_B recent plan: table=" + plan.getString("table")
                            + ", key=" + plan.getString("key") + ", rows=" + plan.getString("rows")
                            + ", extra=" + plan.getString("Extra"));
                }
            }
            try (var statement = session.getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mes_production_defect' AND column_name='defect_type_id'");
                 var columns = statement.executeQuery()) {
                columns.next();
                System.out.println("SIMPLE_B quality defect_type_id column exists=" + (columns.getInt(1) == 1));
            }
            try (var page = PageHelper.offsetPage(0, 6)) {
                var rows = session.getMapper(RecentProductionReportExtraMapper.class).recent();
                assertTrue(rows.size() <= 6);
                assertTrue(page.getTotal() >= rows.size());
                for (var row : rows) {
                    assertTrue(row.id() != null && row.quantityDelta() != null);
                }
                System.out.println("SIMPLE_B recent report SELECT: total=" + page.getTotal() + ", returned=" + rows.size());
            }
            session.rollback();
        }
    }
}
