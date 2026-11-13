package dev.mesbase.business.production.domain;

import java.util.List;

public record OperatorQualificationSummary(Long userId, boolean allOperations, int revision,
                                           boolean validUser, long explicitTotal, long enabledExplicitTotal,
                                           List<ExplicitOperation> operations, int offset, int limit) {
    public record ExplicitOperation(Long operationId, String code, String name, String status) {
    }
}
