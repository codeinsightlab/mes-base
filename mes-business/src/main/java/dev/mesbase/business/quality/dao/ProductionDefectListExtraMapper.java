package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.ProductionDefectModels.ListContext;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.ReceiptProof;
import java.util.List;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import org.apache.ibatis.annotations.Param;

/**
 * Current version projections, immutable proof reads and page-scoped display references.
 */
public interface ProductionDefectListExtraMapper {
    List<CurrentFact> selectCurrentFacts(@Param("executionIds") List<Long> executionIds,
                                         @Param("declarationId") String declarationId);

    List<String> selectFactIdentities(@Param("executionId") Long executionId);

    long invalidCurrentReceipts(@Param("executionId") Long executionId);

    List<ReceiptProof> selectReceiptProofs(@Param("executionId") Long executionId);

    List<ListContext> selectContexts(@Param("ids") List<Long> ids);

    List<CurrentFact> selectCurrentFactsByIds(@Param("ids") List<Long> ids);
}
