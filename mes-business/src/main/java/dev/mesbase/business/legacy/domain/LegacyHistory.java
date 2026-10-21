package dev.mesbase.business.legacy.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class LegacyHistory {
    private LegacyHistory() {
    }

    public record ExecutionEvidence(BigDecimal inputQty, String inputBasis) {
    }

    public record Input(Long executionId, BigDecimal quantity) {
    }

    public record ExecutionNode(Long executionId, Long routingNodeId, BigDecimal legacyNet,
                                BigDecimal grossCorrection, BigDecimal effectiveDefect, BigDecimal unverifiedLossQty) {
        public ExecutionNode(Long executionId, Long routingNodeId, BigDecimal legacyNet,
                             BigDecimal grossCorrection, BigDecimal effectiveDefect) {
            this(executionId, routingNodeId, legacyNet, grossCorrection, effectiveDefect, BigDecimal.ZERO);
        }
    }

    public record Node(Long routingNodeId, BigDecimal legacyNet, BigDecimal grossCorrection,
                       BigDecimal effectiveDefect, String evidence, BigDecimal unverifiedLossQty) {
        public Node(Long routingNodeId, BigDecimal legacyNet, BigDecimal grossCorrection,
                    BigDecimal effectiveDefect, String evidence) {
            this(routingNodeId, legacyNet, grossCorrection, effectiveDefect, evidence, BigDecimal.ZERO);
        }
    }

    public record Mapping(String id, String batchId, String parentId, String sourceTable, String sourcePk,
                          String sourceHash, String ruleVersion, String entityKind, Long entityId,
                          String sourcePayload, String conversionPayload, LocalDateTime createdAt) {
    }

    public record MappingIdentity(String id, String parentId, String sourceHash, String ruleVersion,
                                  Long entityId) {
    }

    public record Batch(String id, String ruleVersion, String snapshotHash, String status,
                        String approvedScope, Long operatorUserId, LocalDateTime createdAt,
                        LocalDateTime finishedAt) {
    }
}
