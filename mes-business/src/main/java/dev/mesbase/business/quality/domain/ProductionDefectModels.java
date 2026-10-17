package dev.mesbase.business.quality.domain;

import jakarta.validation.Valid;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
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

public final class ProductionDefectModels {
    private ProductionDefectModels() {
    }

    public record Facts(@NotNull Long executionId, @NotNull Long affectedNodeId, @NotNull Long discoveredNodeId,
                        Long responsibleNodeId,
                        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal defectQty,
                        @NotBlank String wasteCategory, @NotBlank @Size(max = 500) String defectReason,
                        Long responsibleUserId,
                        @Size(max = 500) String responsibilityRemark, @NotBlank String destinationType,
                        Long targetNodeId,
                        Long targetWarehouseId, @Size(max = 500) String destinationRemark,
                        @NotNull @PastOrPresent LocalDateTime occurredAt) {
    }

    public record Command(@NotBlank @Size(max = 64) String requestKey, Integer expectedRevision,
                          @Size(max = 500) String revisionReason, @Valid Facts facts, Assessment assessment) {
        public Command(String requestKey, Integer expectedRevision, String revisionReason, Facts facts) {
            this(requestKey, expectedRevision, revisionReason, facts, null);
        }
    }

    public record Names(Map<Long, String> nodes, Map<Long, String> users, Map<Long, String> warehouses) {
    }

    public record Page(List<MesProductionDefectEntity> items, long total, int offset, int limit, Names names) {
    }

    public record NodeTotal(Long affectedNodeId, BigDecimal effectiveDefectQty, BigDecimal recoveredQty,
                            BigDecimal unverifiedQty, BigDecimal directDefectQty) {
        public NodeTotal(Long affectedNodeId, BigDecimal effectiveDefectQty) {
            this(affectedNodeId, effectiveDefectQty, BigDecimal.ZERO, BigDecimal.ZERO, effectiveDefectQty);
        }
    }

    public record Node(String id, String name, int sequenceNo) {
    }

    public record Warehouse(String id, String code, String name) {
    }
}
