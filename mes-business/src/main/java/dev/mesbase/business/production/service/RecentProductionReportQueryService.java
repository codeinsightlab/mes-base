package dev.mesbase.business.production.service;

import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.dao.RecentProductionReportExtraMapper;
import dev.mesbase.business.production.domain.RecentProductionReport;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class RecentProductionReportQueryService {
    private final RecentProductionReportExtraMapper reports;
    private final BusinessDirectoryService directory;

    public RecentProductionReportQueryService(RecentProductionReportExtraMapper reports,
                                              BusinessDirectoryService directory) {
        this.reports = reports;
        this.directory = directory;
    }

    public record Recent(List<RecentProductionReport> items,
                         Map<Long, DirectoryLabel> users) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Recent recent() {
        var rows = List.copyOf(reports.recent(10));
        var users = rows.isEmpty() ? Map.<Long, DirectoryLabel>of()
                : directory.userLabels(rows.stream().map(RecentProductionReport::operatorUserId).distinct().toList());
        return new Recent(rows, users);
    }
}
