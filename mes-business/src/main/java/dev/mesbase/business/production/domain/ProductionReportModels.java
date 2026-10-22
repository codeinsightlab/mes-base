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
import dev.mesbase.common.core.page.PageData;
import java.util.Map;
import java.util.UUID;

public final class ProductionReportModels {
    private ProductionReportModels() {
    }

    public record Supplement(@NotNull Long executionId, @NotNull Long routingNodeId,
                             @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                             @NotNull Long operatorUserId, @NotNull @PastOrPresent LocalDateTime reportTime,
                             Long workstationId, @NotBlank @Size(max = 500) String reason, String requestId) {
        public Supplement(Long executionId, Long routingNodeId, BigDecimal quantity, Long operatorUserId,
                          LocalDateTime reportTime, Long workstationId, String reason) {
            this(executionId, routingNodeId, quantity, operatorUserId, reportTime, workstationId, reason, UUID.randomUUID()
                    .toString());
        }
    }

    public record Adjustment(@NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantityDelta,
                             @NotBlank @Size(max = 500) String reason, String requestId) {
        public Adjustment(BigDecimal quantityDelta, String reason) {
            this(quantityDelta, reason, UUID.randomUUID().toString());
        }
    }

    public record VoidOriginal(@NotBlank @Size(max = 500) String reason, String requestId) {
        public VoidOriginal(String reason) {
            this(reason, UUID.randomUUID().toString());
        }
    }

    public record NodeTotal(Long routingNodeId, BigDecimal reportedQty) {
    }

    public record ExecutionNodeTotal(Long executionId, Long routingNodeId, BigDecimal reportedQty) {
    }

    public record ExecutionInputBalance(Long executionId, BigDecimal confirmedQty, BigDecimal reservedQty) {
    }

    public record NodeQuantity(Long executionId, Long routingNodeId, Long operationId, String code, String name,
                               int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                               BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated,
                               BigDecimal recoveredQty, BigDecimal historicalOverrunQty,
                               BigDecimal rawEffectiveOutputQty, BigDecimal rawAvailableQty,
                               BigDecimal unverifiedDefectQty, BigDecimal directDefectQty) {
        public NodeQuantity(Long executionId, Long routingNodeId, Long operationId, String code, String name,
                            int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                            BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated) {
            this(executionId, routingNodeId, operationId, code, name, sequenceNo, inputQty, reportedQty, defectQty, effectiveOutputQty, availableQty, qualityIntegrated, BigDecimal.ZERO, BigDecimal.ZERO, effectiveOutputQty, availableQty, BigDecimal.ZERO, defectQty);
        }

        public NodeQuantity(Long executionId, Long routingNodeId, Long operationId, String code, String name,
                            int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                            BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated,
                            BigDecimal recoveredQty, BigDecimal historicalOverrunQty, BigDecimal rawEffectiveOutputQty,
                            BigDecimal rawAvailableQty, BigDecimal unverifiedDefectQty) {
            this(executionId, routingNodeId, operationId, code, name, sequenceNo, inputQty, reportedQty, defectQty, effectiveOutputQty, availableQty, qualityIntegrated, recoveredQty, historicalOverrunQty, rawEffectiveOutputQty, rawAvailableQty, unverifiedDefectQty, defectQty);
        }

        public BigDecimal netDefectQty() {
            return directDefectQty.subtract(recoveredQty);
        }

        public BigDecimal propagatedLossQty() {
            return historicalOverrunQty;
        }

        public boolean historicalConflict() {
            return historicalOverrunQty.signum() > 0 || rawEffectiveOutputQty.signum() < 0;
        }

    }

    public record ExecutionQuantity(Long executionId, Long workorderId, String executionNo, Long routingId,
                                    BigDecimal targetQty, String pathStatus, boolean writable, String boundary,
                                    List<NodeQuantity> nodes, String routingLabel, BigDecimal productionInputQty,
                                    BigDecimal confirmedInputQty, BigDecimal reservedInputQty, boolean historical) {
        public ExecutionQuantity(Long executionId, Long workorderId, String executionNo, Long routingId,
                                 BigDecimal targetQty, String pathStatus, boolean writable, String boundary,
                                 List<NodeQuantity> nodes, String routingLabel, BigDecimal productionInputQty,
                                 BigDecimal confirmedInputQty, BigDecimal reservedInputQty) {
            this(executionId, workorderId, executionNo, routingId, targetQty, pathStatus, writable, boundary,
                    nodes, routingLabel, productionInputQty, confirmedInputQty, reservedInputQty, false);
        }
    }

    public record ExecutionSummary(Long executionId, Long workorderId, String executionNo, Long routingId,
                                   BigDecimal targetQty, String pathStatus, BigDecimal lastNodeReportedQty,
                                   BigDecimal productionInputQty) {
    }

    public record Balance(Long reportId, BigDecimal effectiveQty) {
    }

    public record Detail(MesProductionReportEntity record, BigDecimal originalEffectiveQty,
                         Map<Long, String> userNames) {
    }

    public record ReportPage(List<MesProductionReportEntity> items, long total, int offset, int limit,
                             Map<Long, String> userNames) implements PageData<MesProductionReportEntity> {
    }

    public record ExecutionPage(List<ExecutionSummary> items, long total, int offset, int limit,
                                Map<Long, String> routingLabels) {
    }
}
