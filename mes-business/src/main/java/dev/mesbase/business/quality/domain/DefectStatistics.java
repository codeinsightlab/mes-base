package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Declaration;
import org.springframework.format.annotation.DateTimeFormat;

public final class DefectStatistics {
    private DefectStatistics() {
    }

    public record Query(String executionCode, Long operationId, Long defectTypeId, String wasteCategory,
                        String attribution,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                        Integer offset, Integer limit) {
    }

    public record Source(Long id, String declarationId, Integer revision, String executionCode,
                         Long routingNodeId, BigDecimal defectQty, String recordAction, Long operationId,
                         Long defectTypeId, String typeName, String wasteCategory) {
        public Declaration declaration() {
            return new Declaration(id, declarationId, revision, executionCode, routingNodeId, defectQty, recordAction);
        }
    }

    public record Row(Long operationId, Long defectTypeId, String typeName, Long typeNameVariants, String wasteCategory,
                      BigDecimal declaredQty, BigDecimal independentQty, BigDecimal overlapQty,
                      BigDecimal unverifiedQty, BigDecimal returnedQty, BigDecimal netDefectQty, long invalidActions) {
        public Row {
            if (invalidActions > 0) {
                independentQty = null;
                overlapQty = null;
                unverifiedQty = null;
                returnedQty = null;
                netDefectQty = null;
            }
        }
    }
}
