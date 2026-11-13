package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Declaration;
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

    public record Quality(Long workorderId, Long declarationRowId, String declarationId, Integer revision,
                          String executionCode, Long routingNodeId, BigDecimal defectQty, String recordAction,
                          String executionStatus) {
        public Declaration declaration() {
            return new Declaration(declarationRowId, declarationId, revision, executionCode, routingNodeId, defectQty, recordAction);
        }
    }

    /**
     * 业务进度计算结果；ID和数量保持原始类型，展示格式由Controller转换。
     */
    public record Progress(Long id, String code, String source, String status, String productCode,
                           String productName, String unitName, BigDecimal plannedQty, BigDecimal completedQty,
                           BigDecimal goodOutputGapQty, BigDecimal pendingCompletionQty,
                           BigDecimal qualityUntreatedQty, BigDecimal qualityReturnedQty,
                           BigDecimal qualityUnverifiedQty, BigDecimal completionPercent,
                           long completedExecutions, LocalDateTime lastCompletedAt, String issue,
                           LocalDateTime createTime, BigDecimal qualityTransferredQty) {
    }

    public record Page(List<Progress> items, long total, int offset, int limit) implements PageData<Progress> {
    }
}
