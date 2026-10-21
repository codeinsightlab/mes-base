package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.RecentProductionReportExtraMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import java.util.Map;
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
        configuration.addMapper(MesQualityTreatmentMapper.class);
        PersistenceFactories.pagination(configuration);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            session.getConnection().setReadOnly(true);
            assertEquals("mes_base_dev_business_simple_b", session.getConnection().getCatalog());
            try (var statement = session.getConnection().prepareStatement("SELECT version FROM flyway_business_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"); var history = statement.executeQuery()) {
                if (history.next()) System.out.println("SIMPLE_B current migration=" + history.getString(1));
            }
            for (String statementId : new String[]{RecentProductionReportExtraMapper.class.getName() + ".recent",
                    MesQualityTreatmentMapper.class.getName() + ".selectRecent"}) {
                var sql = configuration.getMappedStatement(statementId).getBoundSql(Map.of("limit", 10)).getSql();
                try (var statement = session.getConnection().prepareStatement("EXPLAIN " + sql)) {
                    statement.setInt(1, 10);
                    try (var plan = statement.executeQuery()) {
                        while (plan.next()) {
                            System.out.println("SIMPLE_B " + statementId.substring(statementId.lastIndexOf('.') + 1)
                                    + " plan: table=" + plan.getString("table") + ", type=" + plan.getString("type")
                                    + ", key=" + plan.getString("key") + ", rows=" + plan.getString("rows")
                                    + ", extra=" + plan.getString("Extra"));
                        }
                    }
                }
            }
            var capture = new RecentProductionReportHttpTest.SqlCapture();
            configuration.addInterceptor(capture);
            var rows = session.getMapper(RecentProductionReportExtraMapper.class).recent(10);
            assertTrue(rows.size() <= 10);
            for (var row : rows) assertTrue(row.id() != null && row.quantityDelta() != null);
            var defects = session.getMapper(MesQualityTreatmentMapper.class).selectRecent(10);
            assertTrue(defects.size() <= 10);
            capture.assertNoCount();
            System.out.println("SIMPLE_B recent SELECT: reports=" + rows.size() + ", defects=" + defects.size() + ", no count");
            session.rollback();
        }
    }
}
