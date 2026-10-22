package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import dev.mesbase.common.core.page.PageData;

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
                          BigDecimal allocatedReworkQty, BigDecimal startedQty, BigDecimal processedQty,
                          BigDecimal returnedQty, BigDecimal transferredQty) {
    }

    /**
     * 业务进度计算结果；ID和数量保持原始类型，展示格式由Controller转换。
     */
    public record Progress(Long id, String code, String source, String status, String productCode,
                           String productName, String unitName, BigDecimal plannedQty, BigDecimal completedQty,
                           BigDecimal goodOutputGapQty, BigDecimal scrappedQty, BigDecimal pendingCompletionQty,
                           BigDecimal qualityUntreatedQty, BigDecimal qualityWaitingReworkQty, BigDecimal qualityReworkingQty,
                           BigDecimal qualityUnverifiedQty, BigDecimal completionPercent,
                           long completedExecutions, LocalDateTime lastCompletedAt, String issue,
                           LocalDateTime createTime, BigDecimal qualityTransferredQty) {
    }

    public record Page(List<Progress> items, long total, int offset, int limit) implements PageData<Progress> {
    }
}
