package dev.mesbase.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Batch;
import dev.mesbase.business.legacy.domain.LegacyImportCommand;
import dev.mesbase.business.legacy.domain.LegacyImportCommand.Execution;
import dev.mesbase.business.legacy.domain.LegacyImportCommand.Node;
import dev.mesbase.business.legacy.domain.LegacyImportCommand.Report;
import dev.mesbase.business.legacy.service.LegacyHistoryImportService;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: public prepare validates frozen evidence without any database writes.
 */
@ExtendWith(MockitoExtension.class)
class LegacyImportEvidenceTest {
    @Mock
    private LegacyMigrationMapper metadata;
    @Mock
    private BusinessSourceRules sources;
    @InjectMocks
    private LegacyHistoryImportService service;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final LocalDateTime time = LocalDateTime.of(2025, 1, 2, 3, 4);

    @BeforeEach
    void scope() {
        FactoryContext.bind(new FactoryScope(new FactoryId("F")));
        var user = new LoginUser();
        user.setUserId(100L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }

    @AfterEach
    void clear() {
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
    }

    private String encode(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> raw(String id, String operator) {
        return Map.of("record_id", id, "process_id", "P", "user_id", operator,
                "reporting_work_quantity", "1", "create_time", time.toString());
    }

    private Report report(Map<String, String> raw) throws Exception {
        return new Report(raw.get("record_id"), hash(encode(raw)), encode(Map.of("report", raw)), "P",
                BigDecimal.ONE, Long.valueOf(raw.get("user_id")), time);
    }

    private LegacyImportCommand command(boolean substitutedReport, boolean missingExecution) throws Exception {
        var key = List.of("WO", "CARD", "ROUTE");
        var frozen = Map.of("key", key, "input_qty_candidate", "2",
                "reports", List.of(Map.of("raw", raw("R1", "7")), Map.of("raw", raw("R2", "7"))));
        var payload = encode(Map.of("code", "WO", "executions", missingExecution ? List.of() : List.of(frozen)));
        var execution = new Execution(encode(key), hash(encode(frozen)), encode(frozen), new BigDecimal("2"),
                List.of(new Node("P", "OP", "工序", new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO, "{}")),
                List.of(report(raw("R1", "7")), report(raw("R2", substitutedReport ? "8" : "7"))), List.of());
        return new LegacyImportCommand("B", "a".repeat(64), "F", "WO", hash(payload), payload, new BigDecimal("2"),
                time, time, "U", "Unit", "M", "Material", null, List.of(execution));
    }

    @Test
    void acceptsOriginalEvidenceAndPreservesTheApprovedCommandHash() throws Exception {
        var c = command(false, false);
        var batch = new Batch("B", "report-driven-local-v2", c.snapshotHash(), "PREPARED",
                encode(List.of(hash(encode(c)))), 100L, time, null);
        when(metadata.batch(anyString())).thenReturn(batch);
        assertDoesNotThrow(() -> service.prepare(c));
    }

    @Test
    void rejectsSelfConsistentButSubstitutedReportBeforeAnyMetadataAccess() throws Exception {
        var c = command(true, false);
        assertThrows(FoundationException.class, () -> service.prepare(c));
        verifyNoInteractions(metadata);
    }

    @Test
    void rejectsExecutionAbsentFromFrozenOrderBeforeAnyMetadataAccess() throws Exception {
        var c = command(false, true);
        assertThrows(FoundationException.class, () -> service.prepare(c));
        verifyNoInteractions(metadata);
    }
}
