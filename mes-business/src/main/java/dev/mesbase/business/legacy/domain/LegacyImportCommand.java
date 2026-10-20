package dev.mesbase.business.legacy.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Frozen, reviewed conversion input; source JSON is evidence, never executable SQL.
 */
public record LegacyImportCommand(String batchId, String snapshotHash, String factoryId, String code,
                                  String sourceHash, String sourcePayload, BigDecimal plannedQty,
                                  LocalDateTime plannedStart, LocalDateTime plannedEnd,
                                  String unitCode, String unitName, String productCode,
                                  String productName, String specification, List<Execution> executions) {
    public record Execution(String sourceKey, String sourceHash, String sourcePayload,
                            BigDecimal inputQty, List<Node> nodes, List<Report> reports,
                            List<Evidence> defects) {
    }

    public record Node(String processId, String code, String name, BigDecimal legacyNet,
                       BigDecimal grossCorrection, BigDecimal effectiveDefect, String evidence) {
    }

    public record Report(String sourceId, String sourceHash, String sourcePayload, String processId,
                         BigDecimal quantity, Long operatorUserId, LocalDateTime reportTime) {
    }

    public record Evidence(String sourceId, String sourceHash, String sourcePayload) {
    }
}
