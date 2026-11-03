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
    List<CurrentFact> selectCurrentFacts(@Param("executionCodes") List<String> executionCodes,
                                         @Param("declarationId") String declarationId);

    List<String> selectFactIdentities(@Param("executionCode") String executionCode);

    long invalidCurrentReceipts(@Param("executionCode") String executionCode);

    List<ReceiptProof> selectReceiptProofs(@Param("executionCode") String executionCode);

    List<ListContext> selectContexts(@Param("ids") List<Long> ids);

    List<CurrentFact> selectCurrentFactsByIds(@Param("ids") List<Long> ids);
}
