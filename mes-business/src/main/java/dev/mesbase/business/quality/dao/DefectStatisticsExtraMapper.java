package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.domain.DefectStatistics.Row;
import java.util.List;

/**
 * Cross-table projection; the immutable ledgers retain ownership of facts.
 */
public interface DefectStatisticsExtraMapper {
    List<Row> summarize(Query query);
}
