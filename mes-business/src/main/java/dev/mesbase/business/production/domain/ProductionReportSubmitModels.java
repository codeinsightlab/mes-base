package dev.mesbase.business.production.domain;

import java.math.BigDecimal;

public final class ProductionReportSubmitModels {
    private ProductionReportSubmitModels() {
    }

    public record Command(Long executionId, Long routingNodeId, BigDecimal reportQty, Long workstationId,
                          String requestId) {
    }

    public record Candidate(Long routingNodeId, String status, String reason) {
    }

}
