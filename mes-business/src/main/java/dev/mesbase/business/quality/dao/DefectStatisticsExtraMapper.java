package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.domain.DefectStatistics.Source;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * Cross-table projection; the immutable ledgers retain ownership of facts.
 */
public interface DefectStatisticsExtraMapper {
    List<Source> sources(@Param("query") Query query, @Param("offset") long offset, @Param("limit") int limit);
}
