package dev.mesbase.business.production.domain;

import dev.mesbase.business.directory.domain.DirectoryLabel;
import java.math.BigDecimal;
import java.util.List;

/**
 * Planning quantities never form part of combination identity.
 */
public final class ProductionCombinationModels {
    private ProductionCombinationModels() {
    }

    public record Demand(Long id, int lineNo, Long materialId, String materialCode, Long unitId,
                         BigDecimal totalRequiredQty, int quantityPrecision) {
    }

    public record Stock(Long id, Long materialId, String batchNo, Long warehouseId, String binCode,
                        Long unitId, BigDecimal availableQty) {
    }

    public record Selection(Long demandLineId, List<String> batches) {
    }

    public record Allocation(Long demandLineId, Long stockId, Long materialId, Long warehouseId,
                             String binCode, String batchNo, Long unitId, BigDecimal quantity) {
    }

    public record Plan(BigDecimal outputQty, List<Selection> selections, List<Allocation> allocations) {
    }

    public record Item(Long demandLineId, int lineNo, Long materialId, String materialCode, Long unitId,
                       BigDecimal totalRequiredQty, List<String> batches) {
    }

    public record Detail(Long id, String code, Long workorderId, String workorderCode, Long productId,
                         Long outputUnitId, Long demandRevision, Long executionId, String executionStatus,
                         boolean closed, List<Item> items) {
    }

    public record Generated(Detail combination, BigDecimal requestedQty, BigDecimal suggestedQty,
                            List<Allocation> allocations, String strategy) {
    }

    public record Calculation(Detail combination, BigDecimal maximumQty, BigDecimal outputQty,
                              List<Allocation> allocations, String calculationKey, List<DirectoryLabel> warehouses) {
    }

    public record Confirm(String requestId, String code, BigDecimal outputQty, Long routingId,
                          List<Long> routingNodeIds, String calculationKey) {
    }

    public record Result(String requestId, Long documentId, Long executionId, String code,
                         String status, BigDecimal confirmedInputQty, boolean stockApplied) {
    }
}
