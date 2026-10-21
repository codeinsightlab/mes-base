package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RecentProductionReport;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table ledger display query, scoped by the existing business datasource.
 */
public interface RecentProductionReportExtraMapper {
    List<RecentProductionReport> recent(@Param("limit") int limit);
}
