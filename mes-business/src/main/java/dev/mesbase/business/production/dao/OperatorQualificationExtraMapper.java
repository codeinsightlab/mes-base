package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.OperatorQualificationSummary.ExplicitOperation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table projections only; settings writes use the generated single-table mapper.
 */
public interface OperatorQualificationExtraMapper {
    List<Long> candidateUserIds(@Param("operationId") Long operationId, @Param("before") Long before);

    List<ExplicitOperation> explicitOperations(@Param("userId") Long userId);

    long enabledExplicitCount(@Param("userId") Long userId);
}
