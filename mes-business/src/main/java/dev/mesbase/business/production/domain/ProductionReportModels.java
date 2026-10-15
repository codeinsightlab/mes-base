package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProductionReportModels {
    private ProductionReportModels() {
    }

    public record Supplement(@NotNull Long executionId, @NotNull Long routingNodeId,
                             @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                             @NotNull Long operatorUserId, @NotNull @PastOrPresent LocalDateTime reportTime,
                             Long workstationId, @NotBlank @Size(max = 500) String reason) {
    }

    public record Adjustment(@NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantityDelta,
                             @NotBlank @Size(max = 500) String reason) {
    }

    public record VoidOriginal(@NotBlank @Size(max = 500) String reason) {
    }

    public record NodeTotal(Long routingNodeId, BigDecimal reportedQty) {
    }

    public record NodeQuantity(Long executionId, Long routingNodeId, Long operationId, String code, String name,
                               int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                               BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated) {
    }

    public record ExecutionQuantity(Long executionId, Long workorderId, String executionNo, Long routingId,
                                    BigDecimal targetQty, String pathStatus, boolean writable, String boundary,
                                    List<NodeQuantity> nodes, String routingLabel) {
    }

    public record ExecutionSummary(Long executionId, Long workorderId, String executionNo, Long routingId,
                                   BigDecimal targetQty, String pathStatus, BigDecimal lastNodeReportedQty) {
    }

    public record Balance(Long reportId, BigDecimal effectiveQty) {
    }

    public record Detail(MesProductionReportEntity record, BigDecimal originalEffectiveQty,
                         Map<Long, String> userNames) {
    }

    public record ReportPage(List<MesProductionReportEntity> items, long total, int offset, int limit,
                             Map<Long, String> userNames) {
    }

    public record ExecutionPage(List<ExecutionSummary> items, long total, int offset, int limit,
                                Map<Long, String> routingLabels) {
    }
}
