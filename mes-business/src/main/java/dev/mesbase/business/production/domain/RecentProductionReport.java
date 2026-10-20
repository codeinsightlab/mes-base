package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only ledger projection. Quantity deltas include corrections, not qualified output. */
public record RecentProductionReport(Long id, Long executionId, String executionNo, Long workorderId,
                                     String workorderCode, String source, String productCode, String productName,
                                     String unitName, Long routingNodeId, String operationName, String recordType,
                                     BigDecimal quantityDelta, Long operatorUserId, LocalDateTime reportTime,
                                     LocalDateTime createTime) {
}
