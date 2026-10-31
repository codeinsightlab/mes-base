package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionCodeRules;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionInputRulesTest {
    @Test
    void identityRoundTripPreservesBytesAndRejectsAmbiguity() {
        var identity = new ProductionScanCode("000WO#臂", "P.01", "0001#批次/AbC:02 ");
        assertEquals("000WO#臂:P.01#0001#批次/AbC:02 ", ProductionCodeRules.format(identity));
        assertEquals(identity, ProductionCodeRules.parse(ProductionCodeRules.format(identity)));
        assertNotEquals(ProductionCodeRules.format(identity), ProductionCodeRules.format(new ProductionScanCode("000WO#臂", "P.01", "0001#批次/abc:02 ")));
        assertNotEquals(ProductionCodeRules.format(new ProductionScanCode("P", "M", "é")), ProductionCodeRules.format(new ProductionScanCode("P", "M", "e\u0301")));
        assertEquals(new ProductionScanCode("WO001", "000P01", "000LOT01"),
                ProductionCodeRules.parse("WO001:000P01#000LOT01"));
        assertEquals(new ProductionScanCode("EXE", "P1", "LOT1"), ProductionCodeRules.parse("EXE:P1#LOT1"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RequestFingerprint.sha256("abc"));
    }

    @Test
    void executionCodeRejectsMissingFieldsInvalidTextAndRetiredEncodings() {
        for (String code : List.of("WO:P", "WO:P#", "WO:#LOT", ":P#LOT", "WO#P:LOT", "WO:P# ",
                "WO:P#L\n", "WO:P#\uD800", "MES2.V08x.TTE.TDAwMQ", "MES1.UA.TQ.TA", "WO:P#" + "L".repeat(129)))
            assertThrows(FoundationException.class, () -> ProductionCodeRules.parse(code));
        assertThrows(FoundationException.class, () -> ProductionCodeRules.parse(null));
        assertThrows(FoundationException.class, () -> ProductionCodeRules.validate(new ProductionScanCode("WO:1", "P", "LOT")));
        assertThrows(FoundationException.class, () -> ProductionCodeRules.validate(new ProductionScanCode("WO", "P#1", "LOT")));
        for (String code : List.of("M#L#X", "#L", "M#"))
            assertThrows(FoundationException.class, () -> ProductionCodeRules.parse(code));
    }

    @Test
    void planIsNotAuthorityAndDownstreamUsesEffectiveOutput() {
        var a = new NodeQuantity(1L, 11L, 1L, "A", "首工序", 1, BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, true);
        var b = new NodeQuantity(1L, 12L, 1L, "B", "末工序", 2, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var view = new ExecutionQuantity(1L, 1L, 1L, BigDecimal.ONE, "LOCKED", true, null, List.of(a, b), null, new BigDecimal("300"), new BigDecimal("300"), BigDecimal.ZERO, 1L);
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(view, 11L, new BigDecimal("100")));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(view, 12L, new BigDecimal("91")));
        var empty = new ExecutionQuantity(1L, 1L, 1L, new BigDecimal("999"), "LOCKED", true, null, List.of(new NodeQuantity(1L, 11L, 1L, "A", "A", 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true)), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(empty, 11L, BigDecimal.ONE));
    }
}
