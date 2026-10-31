package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionInputQuota;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table formal input quota, including unresolved reservations; not administrative material issues.
 */
public interface ProductionInputExtraMapper {
    record DemandIssued(Long demandLineId, BigDecimal quantity) {
    }

    BigDecimal confirmedOutput(@Param("workorderId") Long workorderId);

    BigDecimal confirmedExecutionOutput(@Param("executionId") Long executionId);

    List<DemandIssued> issuedByDemand(@Param("workorderId") Long workorderId);

    long unlinkedDemandLines(@Param("workorderId") Long workorderId);

    List<String> reversalIds(@Param("documentId") Long documentId);

    boolean hasFormalInput(@Param("workorderId") Long workorderId);

    ProductionInputQuota quota(@Param("workorderId") Long workorderId, @Param("materialId") Long materialId,
                               @Param("unitId") Long unitId);
}
