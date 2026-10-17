package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TEST ONLY real designated MySQL; every attempted change is rolled back, including unexpected success.
 */
@EnabledIfEnvironmentVariable(named = "MES_SOURCE_V17_MYSQL", matches = "true")
class SourceIsolationMySqlTest {
    @Test
    void immutableSourcesAndUnresolvedHistoryAreRealDatabaseFacts() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String statement : new String[]{"UPDATE workorder SET source='EXTERNAL' WHERE id=208", "UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=9"}) {
                    var failure = assertThrows(SQLException.class, () -> {
                        try (var s = connection.createStatement()) {
                            s.executeUpdate(statement);
                        }
                    });
                    assertEquals("45000", failure.getSQLState());
                }
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM workorder WHERE source IS NULL")) {
                    rows.next();
                    assertEquals(176, rows.getInt(1));
                }
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT source FROM workorder WHERE id=208")) {
                    rows.next();
                    assertEquals("LOCAL", rows.getString(1));
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
