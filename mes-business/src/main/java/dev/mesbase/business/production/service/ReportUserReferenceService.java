package dev.mesbase.business.production.service;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

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

    public void requireExists(Long id) {
        if (!directory.historicalUserExists(id)) throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
    }

}
