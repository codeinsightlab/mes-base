package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public final class DefectStatistics {
    private DefectStatistics() {
    }

    public record Query(Long executionId, Long operationId, Long defectTypeId, String wasteCategory, String attribution,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                        Integer offset, Integer limit) {
    }

    public record Row(Long operationId, Long defectTypeId, String typeName, Long typeNameVariants, String wasteCategory,
                      BigDecimal declaredQty, BigDecimal independentQty, BigDecimal overlapQty,
                      BigDecimal unverifiedQty, BigDecimal returnedQty, BigDecimal netDefectQty) {
    }
}
