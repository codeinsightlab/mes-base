package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;

/** Precision for new human-entered quantities, separate from stored facts and conversion arithmetic. */
public final class ProductionQuantityInput {
    private ProductionQuantityInput() {
    }

    public static void check(BigDecimal value) {
        if (value == null || value.stripTrailingZeros().scale() > 2)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    /** An unchanged legacy quantity must not prevent editing unrelated metadata. */
    public static void changed(BigDecimal value, BigDecimal previous) {
        if (value == null || previous == null || value.compareTo(previous) != 0) check(value);
    }
}
