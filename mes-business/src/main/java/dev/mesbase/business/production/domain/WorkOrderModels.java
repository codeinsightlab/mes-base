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
                       @NotNull BigDecimal plannedQty, Long routingId,
                       LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                       @Size(max = 128) String externalId, @Size(max = 128) String externalCode,
                       @Size(max = 500) String remark, Integer revision, @Size(max = 64) String ruleCode,
                       WorkorderMaterialModels.Save material) {
        public Save(String code, Long productId, BigDecimal plannedQty, Long routingId,
                    LocalDateTime plannedStartAt, LocalDateTime plannedEndAt, String externalId,
                    String externalCode, String remark, Integer revision, String ruleCode) {
            this(code, productId, plannedQty, routingId, plannedStartAt, plannedEndAt, externalId,
                    externalCode, remark, revision, ruleCode, null);
        }
    }

    public record Revision(@NotNull Integer revision) {
    }

    /**
     * Formal receipt approval evaluates whether the work order can be completed.
     */
    public record CompletionState(long activeCount, long unfinishedCount, long missingCompletionCount) {
        public boolean allCompleted() {
            return activeCount > 0 && unfinishedCount == 0 && missingCompletionCount == 0;
        }
    }

    public record Detail(WorkorderEntity workorder, String productCode, String productName,
                         RoutingEntity routing, Allocation allocation) {
    }

    public record Summary(WorkorderEntity workorder, String productCode, String productName,
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
