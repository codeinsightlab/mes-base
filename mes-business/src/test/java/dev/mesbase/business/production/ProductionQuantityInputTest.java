package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionQuantityInputTest {
    @Test
    void enteredAmountsSupportSixDecimalsAndUnchangedLegacyFactsRemainEditable() {
        var old = new BigDecimal("99999999999999.123456");
        assertDoesNotThrow(() -> ProductionQuantityInput.check(old));
        assertDoesNotThrow(() -> ProductionQuantityInput.check(new BigDecimal("99999999999999.120000")));
        assertThrows(FoundationException.class,
                () -> ProductionQuantityInput.check(new BigDecimal("99999999999999.1234567")));
        assertDoesNotThrow(() -> ProductionQuantityInput.changed(old, old));
        assertDoesNotThrow(() -> ProductionQuantityInput.changed(old.add(new BigDecimal("0.000001")), old));
        assertEquals("99999999999999.123456", old.toPlainString());
    }
}
