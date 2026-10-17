package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProductionReceiptModels {
    private ProductionReceiptModels() {
    }

    public record Receive(Long executionId, Long warehouseId, String productLotCode, BigDecimal quantity,
                          String requestId) {
    }

    public record Reverse(BigDecimal quantity, String reason, String requestId) {
    }

    public record Query(Long workorderId, Long executionId, Long warehouseId, String productLotCode,
                        String referenceReceiptId, int offset, int limit) {
    }

    public record Summary(ProductionIdentity identity, String routingLabel, String pathStatus, Long terminalNodeId,
                          String terminalNodeName, BigDecimal output, BigDecimal received, BigDecimal remaining,
                          BigDecimal excess, boolean canReceive, String blockedCode, String blockedReason,
                          BigDecimal released, BigDecimal releaseRemaining, BigDecimal accepted,
                          BigDecimal pendingAcceptance, BigDecimal releaseExcess) {
    }

    public record Page(List<ProductionReceiptRow> items, long total, int offset, int limit,
                       Map<Long, String> userNames) {
    }

    public record Detail(ProductionReceiptRow record, BigDecimal originalBalance, Map<Long, String> userNames) {
    }
}
