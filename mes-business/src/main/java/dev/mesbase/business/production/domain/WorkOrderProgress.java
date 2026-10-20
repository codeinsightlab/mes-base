package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class WorkOrderProgress {
    private WorkOrderProgress() {
    }

    public record Order(Long id, String code, String source, String status, String productCode,
                        String productName, String unitName, BigDecimal plannedQty) {
    }

    public record Completion(Long workorderId, BigDecimal quantity, long factCount, long executionCount,
                             long invalidCount, LocalDateTime lastCompletedAt) {
    }

    public record Progress(Order order, BigDecimal completedQty, BigDecimal remainingQty,
                           BigDecimal completionPercent, long completedExecutions,
                           LocalDateTime lastCompletedAt, String issue) {
    }
}
