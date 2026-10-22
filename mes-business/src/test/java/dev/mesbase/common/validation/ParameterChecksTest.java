package dev.mesbase.common.validation;

import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParameterChecksTest {
    @Test
    void pagingKeepsOffsetAndConfiguredUpperBound() {
        assertDoesNotThrow(() -> ParameterChecks.paging(99, 100));
        assertDoesNotThrow(() -> ParameterChecks.paging(0, 200, 200));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(FoundationException.class,
                () -> ParameterChecks.paging(-1, 20)).code());
        assertThrows(FoundationException.class, () -> ParameterChecks.paging(0, 0));
        assertThrows(FoundationException.class, () -> ParameterChecks.paging(0, 101));
    }

    @Test
    void optionalAndRequiredTextKeepTheirDomainError() {
        assertDoesNotThrow(() -> ParameterChecks.text(null, false, 3, ErrorCode.BAD_REQUEST));
        assertDoesNotThrow(() -> ParameterChecks.text("abc", true, 3, ErrorCode.BAD_REQUEST));
        for (String value : new String[]{null, "", " \t", "\u2003", "abcd"}) {
            assertEquals(ErrorCode.QUALITY_TREATMENT_CONFLICT, assertThrows(FoundationException.class,
                    () -> ParameterChecks.text(value, true, 3, ErrorCode.QUALITY_TREATMENT_CONFLICT)).code());
        }
    }
}
