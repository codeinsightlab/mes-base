package dev.mesbase.business.quality.domain;

import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.OperationDefectTypeEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProductionDefectV2Models {
    private ProductionDefectV2Models() {
    }

    public record Command(String requestId, Long executionId, Long nodeId, Long defectTypeId,
                          Integer expectedTypeRevision, BigDecimal quantity, String costCategory,
                          String remark, LocalDateTime occurredAt, Assessment assessment) {
    }

    public record Context(Long executionId, Long nodeId, Long operationId, String operationName,
                          Long productId, String productCode, String productName,
                          List<OperationDefectTypeEntity> types, long typeTotal, int offset, int limit,
                          boolean canOverrideCost) {
    }
}
