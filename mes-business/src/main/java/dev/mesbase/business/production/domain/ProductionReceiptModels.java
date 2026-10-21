package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProductionReceiptModels {
    private ProductionReceiptModels() {
    }

    public record Query(Long workorderId, Long executionId, Long warehouseId, String productLotCode,
                        String referenceReceiptId, int offset, int limit) {
    }

    public record Page(List<ProductionReceiptRow> items, long total, int offset, int limit,
                       Map<Long, String> userNames) {
    }

    public record Detail(ProductionReceiptRow record, BigDecimal originalBalance, Map<Long, String> userNames) {
    }
}
