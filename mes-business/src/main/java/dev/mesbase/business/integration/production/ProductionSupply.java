package dev.mesbase.business.integration.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Local and optional ERP providers use the same explicit, immutable supply contract.
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
                        List<Requirement> requirements, String sourceReference, LocalDateTime observedAt) {
        public Order {
            allowedWarehouses = List.copyOf(allowedWarehouses);
            requirements = List.copyOf(requirements);
        }
    }

    public record Context(String sourceMode, List<Stock> stocks, List<Order> orders) {
    }

    public record Calculation(String sourceMode, Order order, Stock stock, BigDecimal outputQty, BigDecimal issueQty) {
    }
}
