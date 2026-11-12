package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductionReportModels {
    /**
     * Original formal issue allocations and their referenced returns; never an additional quantity ledger.
     */
    public record MaterialReturnBalance(String executionCode, Long documentId, Long lineId, Long stockId,
                                        Long demandLineId, BigDecimal issuedQty, BigDecimal returnedQty,
                                        Integer outputPrecision, long invalidCount, BigDecimal requiredQty,
                                        BigDecimal plannedQty,
                                        long allocationCount, long returnCount) {
    }

    private ProductionReportModels() {
    }

    public record Supplement(@NotNull String executionCode, @NotNull Long routingNodeId,
                             @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                             @NotNull Long operatorUserId, @NotNull @PastOrPresent LocalDateTime reportTime,
                             Long workstationId, @NotBlank @Size(max = 500) String reason, String requestId) {
        public Supplement(String executionCode, Long routingNodeId, BigDecimal quantity, Long operatorUserId,
                          LocalDateTime reportTime, Long workstationId, String reason) {
            this(executionCode, routingNodeId, quantity, operatorUserId, reportTime, workstationId, reason, UUID.randomUUID()
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

    public record ExecutionNodeTotal(String executionCode, Long routingNodeId, BigDecimal reportedQty) {
    }

    public record ExecutionInputBalance(String executionCode, BigDecimal confirmedQty, BigDecimal reservedQty) {
    }

    public record NodeQuantity(String executionCode, Long routingNodeId, Long operationId, String code, String name,
                               int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                               BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated,
                               BigDecimal returnedQty, BigDecimal historicalOverrunQty,
                               BigDecimal rawEffectiveOutputQty, BigDecimal rawAvailableQty,
                               BigDecimal unverifiedDefectQty, BigDecimal directDefectQty) {
        public NodeQuantity(String executionCode, Long routingNodeId, Long operationId, String code, String name,
                            int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                            BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated) {
            this(executionCode, routingNodeId, operationId, code, name, sequenceNo, inputQty, reportedQty, defectQty, effectiveOutputQty, availableQty, qualityIntegrated, BigDecimal.ZERO, BigDecimal.ZERO, effectiveOutputQty, availableQty, BigDecimal.ZERO, defectQty);
        }

        public NodeQuantity(String executionCode, Long routingNodeId, Long operationId, String code, String name,
                            int sequenceNo, BigDecimal inputQty, BigDecimal reportedQty, BigDecimal defectQty,
                            BigDecimal effectiveOutputQty, BigDecimal availableQty, boolean qualityIntegrated,
                            BigDecimal returnedQty, BigDecimal historicalOverrunQty, BigDecimal rawEffectiveOutputQty,
                            BigDecimal rawAvailableQty, BigDecimal unverifiedDefectQty) {
            this(executionCode, routingNodeId, operationId, code, name, sequenceNo, inputQty, reportedQty, defectQty, effectiveOutputQty, availableQty, qualityIntegrated, returnedQty, historicalOverrunQty, rawEffectiveOutputQty, rawAvailableQty, unverifiedDefectQty, defectQty);
        }

        public BigDecimal netDefectQty() {
            return directDefectQty.subtract(returnedQty);
        }

        public BigDecimal propagatedLossQty() {
            return historicalOverrunQty;
        }

        public boolean historicalConflict() {
            return historicalOverrunQty.signum() > 0 || rawEffectiveOutputQty.signum() < 0;
        }

    }

    public record ExecutionQuantity(String executionCode, Long workorderId, Long routingId,
                                    BigDecimal targetQty, String pathStatus, boolean writable, String boundary,
                                    List<NodeQuantity> nodes, String routingLabel, BigDecimal productionInputQty,
                                    BigDecimal confirmedInputQty, BigDecimal reservedInputQty, Long productId) {
        public BigDecimal materialReturnQty() {
            return confirmedInputQty.subtract(reservedInputQty).subtract(productionInputQty);
        }
    }

    public record ExecutionSummary(String executionCode, Long workorderId, Long routingId,
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
                                Map<Long, String> routingLabels) implements PageData<ExecutionSummary> {
    }
}
