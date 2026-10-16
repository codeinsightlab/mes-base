package dev.mesbase.business.production;

import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionReceiptQuantityTest {
    @Test
    void capacitySixtyFortyThenOneAndExcessBoundaries() {
        assertDoesNotThrow(() -> ProductionReceiptService.validateReceive(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("60")));
        assertDoesNotThrow(() -> ProductionReceiptService.validateReceive(new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("40")));
        assertThrows(FoundationException.class, () -> ProductionReceiptService.validateReceive(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ONE));
        assertThrows(FoundationException.class, () -> ProductionReceiptService.validateReceive(new BigDecimal("85"), new BigDecimal("90"), BigDecimal.ONE));
        assertDoesNotThrow(() -> ProductionReceiptService.validateReverse(new BigDecimal("90"), new BigDecimal("5")));
    }

    @Test
    void exactDecimalAndNoNegativeZeroOrOverflow() {
        assertDoesNotThrow(() -> ProductionReceiptService.validateReceive(new BigDecimal("1.123456"), BigDecimal.ZERO, new BigDecimal("1.123456")));
        for (String invalid : new String[]{"0", "-1", "1.0000001", "100000000000000"})
            assertThrows(FoundationException.class, () -> ProductionReceiptService.validateReverse(new BigDecimal("99999999999999"), new BigDecimal(invalid)));
        assertThrows(FoundationException.class, () -> ProductionReceiptService.validateReverse(new BigDecimal("5"), new BigDecimal("5.000001")));
    }
}
