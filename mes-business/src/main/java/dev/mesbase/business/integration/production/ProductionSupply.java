package dev.mesbase.business.integration.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Production-input scene contract for LOCAL stock and production facts.
 */
public final class ProductionSupply {
    private ProductionSupply() {
    }

    public record Stock(String reference, Long materialId, String materialCode, String lotCode,
                        Long warehouseId, String warehouseCode, String binCode, Long unitId, String unitCode,
                        BigDecimal availableQty, LocalDateTime observedAt) {
    }

    public record Requirement(String materialCode, Long materialId, Long unitId, String unitCode,
                              BigDecimal totalRequiredQty) {
    }

    public record Order(Long workorderId, String workorderCode, String productCode, String productName,
                        String workCenter, BigDecimal plannedQty, String outputUnitCode, String status,
                        List<Long> allowedWarehouses,
                        List<Requirement> requirements, String sourceReference, LocalDateTime observedAt,
                        Long productId) {
        public Order {
            allowedWarehouses = List.copyOf(allowedWarehouses);
            requirements = List.copyOf(requirements);
        }
    }

    public record Context(String sourceMode, List<Stock> stocks, List<Order> orders, long total, int offset,
                          int limit) {
        public Context(String sourceMode, List<Stock> stocks, List<Order> orders) {
            this(sourceMode, stocks, orders, orders.size(), 0, orders.size());
        }
    }

    public record Basis(Long bomId, BigDecimal baseQty, BigDecimal componentQty, Long outputUnitId) {
    }

    public record Calculation(String sourceMode, Order order, Stock stock, BigDecimal outputQty, BigDecimal issueQty,
                              Basis basis) {
        public Calculation(String sourceMode, Order order, Stock stock, BigDecimal outputQty, BigDecimal issueQty) {
            this(sourceMode, order, stock, outputQty, issueQty, null);
        }
    }
}
