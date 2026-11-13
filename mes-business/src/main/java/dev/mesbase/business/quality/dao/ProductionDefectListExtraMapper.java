package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.ProductionDefectModels.ListContext;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.ReceiptProof;
import java.util.List;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Declaration;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Event;
import org.apache.ibatis.annotations.Param;

/**
 * Current version projections, immutable proof reads and page-scoped display references.
 */
public interface ProductionDefectListExtraMapper {
    List<Declaration> selectDeclarations(@Param("executionCodes") List<String> executionCodes,
                                         @Param("declarationId") String declarationId, @Param("ids") List<Long> ids,
                                         @Param("declarationIds") List<String> declarationIds,
                                         @Param("afterId") long afterId,
                                         @Param("limit") int limit);

    List<Event> selectEvents(@Param("ids") List<Long> ids, @Param("afterId") String afterId,
                             @Param("limit") int limit);

    boolean hasEffectiveFacts(@Param("executionCode") String executionCode);

    List<String> selectFactIdentities(@Param("executionCode") String executionCode);

    long invalidCurrentReceipts(@Param("executionCode") String executionCode);

    List<ReceiptProof> selectReceiptProofs(@Param("executionCode") String executionCode);

    List<ListContext> selectContexts(@Param("ids") List<Long> ids);

}
