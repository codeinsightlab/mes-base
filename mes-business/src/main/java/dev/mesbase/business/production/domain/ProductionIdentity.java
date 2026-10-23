package dev.mesbase.business.production.domain;

import java.math.BigDecimal;

public record ProductionIdentity(Long executionId, String qrCode,
                                 Long workorderId, String workorderCode, Long productId, String productCode,
                                 String productName, Long routingId, String routingCode, Integer routingVersion,
                                 String status, BigDecimal targetQty, String batchNo) {
}
