package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionInputQuota;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * Cross-table formal input quota, including unresolved reservations; not administrative material issues.
 */
public interface ProductionInputExtraMapper {
    List<String> reversalIds(@Param("documentId") Long documentId);

    boolean hasFormalInput(@Param("workorderId") Long workorderId);

    ProductionInputQuota quota(@Param("workorderId") Long workorderId, @Param("materialId") Long materialId,
                               @Param("unitId") Long unitId);
}
