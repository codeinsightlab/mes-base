package dev.mesbase.business.production.domain;

import java.math.BigDecimal;

public record ProductionInputBalance(BigDecimal confirmedQty, BigDecimal reservedQty) {
    public BigDecimal usableQty() {
        return confirmedQty.subtract(reservedQty);
    }
}
