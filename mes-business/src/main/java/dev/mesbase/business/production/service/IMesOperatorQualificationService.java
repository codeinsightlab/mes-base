package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.OperatorQualificationSummary;

public interface IMesOperatorQualificationService {
    OperatorQualificationSummary summary(Long userId, int offset, int limit);

    void grantAll(Long userId, int revision);

    void revokeAll(Long userId, int revision);
}
