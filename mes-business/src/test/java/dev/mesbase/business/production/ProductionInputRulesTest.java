package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProductionInputRulesTest {
    @Test
    void identityRoundTripPreservesBytesAndRejectsAmbiguity() {
        var identity = new ProductionScanCode("000P#臂", "M.01", "0001#批次/AbC");
        assertEquals(identity, ProductionScanCode.parse(identity.encode()));
        assertNotEquals(identity.key(), new ProductionScanCode("000P#臂", "M.01", "0001#批次/abc").key());
        assertNotEquals(new ProductionScanCode("P", "M", "é").key(), new ProductionScanCode("P", "M", "e\u0301").key());
        for (String code : List.of("M#L#X", "#L", "M#", " MES1.a.b.c", "MES1.wA.YQ.Yg", "MES1.UA==.TQ.TA"))
            assertThrows(FoundationException.class, () -> {
                if (code.startsWith("MES1.")) ProductionScanCode.parse(code);
                else ProductionScanCode.material(code);
            });
        assertEquals("001", ProductionScanCode.material("M#001").lotCode());
    }

    @Test
    void outputDrivenRequirementsUseOneDivisionAndExactHalfUp() {
        assertEquals(new BigDecimal("200.000000"), ProductionInputSupplyService.issueQuantity(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("100")));
        assertEquals(new BigDecimal("0.166667"), ProductionInputSupplyService.issueQuantity(new BigDecimal("6"), BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(FoundationException.class, () -> ProductionInputSupplyService.issueQuantity(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void planIsNotAuthorityAndDownstreamUsesEffectiveOutput() {
        var a = new NodeQuantity(1L, 11L, 1L, "A", "首工序", 1, BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, true);
        var b = new NodeQuantity(1L, 12L, 1L, "B", "末工序", 2, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var view = new ExecutionQuantity(1L, 1L, "EXE", 1L, BigDecimal.ONE, "LOCKED", true, null, List.of(a, b), null, new BigDecimal("300"), new BigDecimal("300"), BigDecimal.ZERO);
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(view, 11L, new BigDecimal("100")));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(view, 12L, new BigDecimal("91")));
        var empty = new ExecutionQuantity(1L, 1L, "EXE", 1L, new BigDecimal("999"), "LOCKED", true, null, List.of(new NodeQuantity(1L, 11L, 1L, "A", "A", 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true)), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(empty, 11L, BigDecimal.ONE));
    }
}
