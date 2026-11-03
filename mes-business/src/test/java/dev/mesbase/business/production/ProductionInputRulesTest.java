package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
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
    void planIsNotAuthorityAndDownstreamUsesEffectiveOutput() {
        var a = new NodeQuantity("1", 11L, 1L, "A", "首工序", 1, BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, true);
        var b = new NodeQuantity("1", 12L, 1L, "B", "末工序", 2, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var view = new ExecutionQuantity("1", 1L, 1L, BigDecimal.ONE, "LOCKED", true, null, List.of(a, b), null, new BigDecimal("300"), new BigDecimal("300"), BigDecimal.ZERO, 1L);
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(view, 11L, new BigDecimal("100")));
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(view, 12L, new BigDecimal("91")));
        var empty = new ExecutionQuantity("1", 1L, 1L, new BigDecimal("999"), "LOCKED", true, null, List.of(new NodeQuantity("1", 11L, 1L, "A", "A", 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true)), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(empty, 11L, BigDecimal.ONE));
    }
}
