package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
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

    public record Page(List<RecentProductionReport> items, long total, int offset, int limit,
                       Map<Long, DirectoryLabel> users) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page recent(int offset, int limit) {
        ProductionQuantityService.paging(offset, limit);
        List<RecentProductionReport> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(reports.recent());
            total = page.getTotal();
        }
        var users = rows.isEmpty() ? Map.<Long, DirectoryLabel>of()
                : directory.userLabels(rows.stream().map(RecentProductionReport::operatorUserId).distinct().toList());
        return new Page(rows, total, offset, limit, users);
    }
}
