package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Read-only designated MySQL evidence; source mutation policy is owned by business services. */
@EnabledIfEnvironmentVariable(named = "MES_SOURCE_V17_MYSQL", matches = "true")
class SourceIsolationMySqlTest {
    @Test
    void persistedSourcesUseTheServiceOwnedContract() throws Exception {
        try (var connection = AcceptanceBusinessDatabase.source().getConnection()) {
            connection.setReadOnly(true);
            try (var query = connection.createStatement(); var rows = query.executeQuery(
                    "SELECT source FROM workorder WHERE source IS NOT NULL UNION SELECT source FROM wm_warehouse WHERE source IS NOT NULL")) {
                while (rows.next()) {
                    var source = BusinessSourceRules.required(rows.getString(1));
                    assertEquals(rows.getString(1), source.getValue());
                }
            }
        }
        BusinessSourceRules.localEditable("LOCAL");
        assertEquals(ErrorCode.EXTERNAL_SOURCE_READ_ONLY,
                assertThrows(FoundationException.class, () -> BusinessSourceRules.localEditable("EXTERNAL")).code());
    }
}
