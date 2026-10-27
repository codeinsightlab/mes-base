package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkOrderModels {
    private WorkOrderModels() {
    }

    public record Save(@Size(max = 64) String code, @NotNull Long productId,
                       @NotNull BigDecimal plannedQty, @NotNull Long routingId,
                       LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                       @Size(max = 128) String externalId, @Size(max = 128) String externalCode,
                       @Size(max = 500) String remark, Integer revision) {
    }

    public record Revision(@NotNull Integer revision) {
    }

    /**
     * Only formal terminal execution facts qualify for the scheduled work-order transition.
     */
    public record CompletionState(long activeCount, long unfinishedCount, long missingCompletionCount) {
        public boolean allCompleted() {
            return activeCount > 0 && unfinishedCount == 0 && missingCompletionCount == 0;
        }
    }

    public record Detail(WorkorderEntity workorder, String productCode, String productName,
                         RoutingEntity routing, List<ProductionBatchEntity> batches, Allocation allocation) {
    }

    public record Summary(WorkorderEntity workorder, int batchCount, String productCode, String productName,
                          String routingCode, Integer routingVersion, Allocation allocation, boolean erpExtraMissing) {
    }

    public record Page(List<Summary> items, long total, int offset, int limit) implements PageData<Summary> {
    }

    /**
     * Read-only planned allocation, summed over every execution status; never actual output.
     */
    public record Allocation(Long workorderId, BigDecimal plannedQty, BigDecimal allocatedTargetQty,
                             BigDecimal remainingTargetQty, BigDecimal excessTargetQty, Long executionCount) {
    }
}
