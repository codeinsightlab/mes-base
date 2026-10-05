package dev.mesbase;

import dev.mesbase.common.core.text.Convert;
import dev.mesbase.common.utils.DateUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.uuid.IdUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Compatibility checks for copied Source Product contracts, not a new utility design. */
class SourceProductCommonTest {
    @Test void sourceFormattingAndEscapedPlaceholdersRemainCompatible() {
        assertEquals("qty=3, route=A", StringUtils.format("qty={}, route={}", 3, "A"));
        assertEquals("{} stays, next=A", StringUtils.format("\\{} stays, next={}", "A"));
        assertEquals("factory_id", StringUtils.toUnderScoreCase("factoryId"));
        assertEquals("factoryId", StringUtils.toCamelCase("factory_id"));
    }

    @Test void sourceConversionAndDateContractsRemainCompatible() {
        assertEquals(12, Convert.toInt("12"));
        assertEquals(7, Convert.toInt("invalid", 7));
        assertArrayEquals(new Long[]{1L, 2L}, Convert.toLongArray("1,2"));
        var date=DateUtils.dateTime("yyyy-MM-dd", "2026-10-05");
        assertEquals("2026-10-05", DateUtils.parseDateToStr("yyyy-MM-dd", date));
        assertNull(DateUtils.parseDate("not-a-date"));
    }

    @Test void sourceSecureUuidAndShortIdKeepTheirFormats() {
        String id=IdUtils.randomUUID();
        assertEquals(id, java.util.UUID.fromString(id).toString());
        assertNotEquals(id, IdUtils.randomUUID());
        assertTrue(IdUtils.simpleUUID().matches("[0-9a-f]{32}"));
    }
}
