package dev.mesbase.business.production.service;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historical personnel existence, deliberately independent of present capability and assignment.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ReportUserReferenceService {
    private final BusinessDirectoryService directory;

    public ReportUserReferenceService(BusinessDirectoryService directory) {
        this.directory = directory;
    }

    public record HistoricalUser(Long id, String name) {
    }

    public record UserPage(List<HistoricalUser> items, long total, int offset, int limit) {
    }

    public void requireExists(Long id) {
        if (!directory.historicalUserExists(id)) throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public UserPage page(String keyword, int offset, int limit) {
        var page = directory.historicalUsers(keyword, offset, limit);
        return new UserPage(page.items().stream().map(user -> new HistoricalUser(user.id(), user.name()))
                .toList(), page.total(), offset, limit);
    }
}
