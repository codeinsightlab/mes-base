package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkorderMaterialModels {
    private WorkorderMaterialModels() {
    }

    public record Save(Long expectedRevision, Long bomId, Long expectedBomRevision,
                       List<Long> warehouseIds, String workCenter, boolean enabled,
                       List<Line> items, Boolean adjustmentsConfirmed) {

    }

    public record Line(Long id, Long bomItemId, Long materialId, Long unitId,
                       BigDecimal totalRequiredQty, boolean manuallyAdjusted) {
    }

    public record Document(Long workorderId, Long bomId, Long bomRevision, Long revision,
                           String warehouseIds, String workCenter, String status, LocalDateTime frozenAt,
                           LocalDateTime updatedAt, List<Item> items) {
    }

    public record Item(Long id, Long bomItemId, Long materialId, Long unitId, BigDecimal totalRequiredQty,
                       BigDecimal standardRequiredQty, BigDecimal bomBaseQty, BigDecimal bomComponentQty,
                       BigDecimal plannedQty, Long outputUnitId, boolean manuallyAdjusted) {
    }

    /**
     * A proven standard single line for the issue flow that has not yet been extended.
     */
    public record Single(Long workorderId, Long bomId, Long materialId, Long unitId,
                         BigDecimal totalRequiredQty, BigDecimal plannedQty, BigDecimal bomBaseQty,
                         BigDecimal bomComponentQty, Long outputUnitId, String warehouseIds,
                         String workCenter, Long revision, LocalDateTime frozenAt) {
    }
}
