package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkOrderProgress {
    private WorkOrderProgress() {
    }

    public record Query(String workorderCode, String productCode, String status, int offset, int limit) {
    }

    public record Filter(String workorderCodePattern, String productCodePattern, String status) {
    }

    public record Order(Long id, String code, String source, String status, String productCode,
                        String productName, String unitName, BigDecimal plannedQty, LocalDateTime createTime) {
    }

    public record Completion(Long workorderId, BigDecimal quantity, long factCount, long executionCount,
                             long invalidCount, LocalDateTime lastCompletedAt) {
    }

    public record Quality(Long workorderId, BigDecimal declaredQty, String attribution, String destinationType,
                          BigDecimal scrapQty, BigDecimal failedQty, BigDecimal recoveredQty,
                          BigDecimal allocatedReworkQty, BigDecimal startedQty, BigDecimal processedQty) {
    }

    /**
     * 明确字段白名单的进度查询结果，ID和数量保持精确字符串表示。
     */
    public record Progress(String id, String code, String source, String status, String productCode,
                           String productName, String unitName, String plannedQty, String completedQty,
                           String goodOutputGapQty, String scrappedQty, String pendingCompletionQty,
                           String qualityUntreatedQty, String qualityWaitingReworkQty, String qualityReworkingQty,
                           String qualityUnverifiedQty, String completionPercent,
                           long completedExecutions, LocalDateTime lastCompletedAt, String issue,
                           LocalDateTime createTime) {
    }

    public record Page(List<Progress> items, long total, int offset, int limit) {
    }
}
