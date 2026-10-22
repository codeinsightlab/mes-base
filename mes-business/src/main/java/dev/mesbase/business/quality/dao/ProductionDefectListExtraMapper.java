package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.ProductionDefectModels.ListContext;
import dev.mesbase.business.quality.domain.ProductionDefectModels.TreatmentTotals;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table reads are bounded by the already paginated declaration version IDs.
 */
public interface ProductionDefectListExtraMapper {
    List<ListContext> selectContexts(@Param("ids") List<Long> ids);

    List<TreatmentTotals> selectTreatmentTotals(@Param("ids") List<Long> ids);
}
