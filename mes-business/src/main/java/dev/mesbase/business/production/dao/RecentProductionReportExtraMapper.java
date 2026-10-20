package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RecentProductionReport;
import java.util.List;

/** Cross-table ledger display query, scoped by the existing business datasource. */
public interface RecentProductionReportExtraMapper {
    List<RecentProductionReport> recent();
}
