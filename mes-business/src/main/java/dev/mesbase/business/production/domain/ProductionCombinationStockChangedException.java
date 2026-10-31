package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;

public final class ProductionCombinationStockChangedException extends FoundationException {
    private final BigDecimal maximumQty;

    public ProductionCombinationStockChangedException(BigDecimal maximumQty) {
        super(ErrorCode.PRODUCTION_COMBINATION_STOCK_CHANGED);
        this.maximumQty = maximumQty;
    }

    public BigDecimal maximumQty() {
        return maximumQty;
    }
}
