package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkOrderModels {
    private WorkOrderModels() {
    }

    public record Save(@NotBlank @Size(max = 64) String code, @NotNull Long productId,
                       @NotNull BigDecimal plannedQty, @NotNull Long routingId,
                       LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                       @Size(max = 128) String externalId, @Size(max = 128) String externalCode,
                       @Size(max = 500) String remark, Integer revision) {
    }

    public record Revision(@NotNull Integer revision) {
    }

    public record PathSelection(@NotNull Integer revision,
                                @NotNull @Size(min = 1, max = 100) List<@NotNull Long> nodeIds) {
    }

    public record BatchDetail(ProductionBatchEntity batch, List<BatchRouteNodeEntity> path) {
    }

    public record Detail(WorkorderEntity workorder, String productCode, String productName,
                         RoutingEntity routing, List<BatchDetail> batches, Allocation allocation) {
    }

    public record Summary(WorkorderEntity workorder, int batchCount, String productCode, String productName,
                          String routingCode, Integer routingVersion, Allocation allocation) {
    }

    public record Page(List<Summary> items, long total, int offset, int limit) {
    }

    /**
     * Read-only planned allocation, summed over every execution status; never actual output.
     */
    public record Allocation(Long workorderId, BigDecimal plannedQty, BigDecimal allocatedTargetQty,
                             BigDecimal remainingTargetQty, BigDecimal excessTargetQty, Long executionCount) {
    }
}
