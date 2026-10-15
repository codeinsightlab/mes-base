package dev.mesbase.business.production.controller.vo;

import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProductionReportViews {
    private ProductionReportViews() {
    }

    public record Ledger(String id, String executionId, String routingNodeId, String recordType, String quantityDelta,
                         String referenceReportId, String operatorUserId, String recordedBy, LocalDateTime reportTime,
                         String workstationId, String reason, LocalDateTime createTime, String operatorName,
                         String recordedByName) {
    }

    public record Detail(Ledger record, String originalEffectiveQty) {
    }

    public record Page<T>(List<T> items, long total, int offset, int limit) {
    }

    public record Node(String executionId, String routingNodeId, String operationId, String code, String name,
                       int sequenceNo,
                       String inputQty, String reportedQty, String defectQty, String effectiveOutputQty,
                       String availableQty, boolean qualityIntegrated) {
    }

    public record Execution(String executionId, String workorderId, String executionNo, String routingId,
                            String targetQty,
                            String pathStatus, boolean writable, String boundary, List<Node> nodes,
                            String routingLabel) {
    }

    public record Summary(String executionId, String workorderId, String executionNo, String routingId,
                          String targetQty,
                          String pathStatus, String lastNodeReportedQty, String routingLabel) {
    }

    public record HistoricalUser(String id, String name) {
    }

    public static Ledger ledger(MesProductionReportEntity r) {
        return ledger(r, Map.of());
    }

    public static Ledger ledger(MesProductionReportEntity r, Map<Long, String> names) {
        return new Ledger(id(r.getId()), id(r.getExecutionId()), id(r.getRoutingNodeId()), r.getRecordType(), decimal(r.getQuantityDelta()),
                id(r.getReferenceReportId()), id(r.getOperatorUserId()), id(r.getRecordedBy()), r.getReportTime(), id(r.getWorkstationId()), r.getReason(), r.getCreateTime(), names.get(r.getOperatorUserId()), names.get(r.getRecordedBy()));
    }

    public static Execution execution(ExecutionQuantity v) {
        return new Execution(id(v.executionId()), id(v.workorderId()), v.executionNo(), id(v.routingId()), decimal(v.targetQty()),
                v.pathStatus(), v.writable(), v.boundary(), v.nodes().stream().map(ProductionReportViews::node)
                .toList(), v.routingLabel());
    }

    public static Node node(NodeQuantity n) {
        return new Node(id(n.executionId()), id(n.routingNodeId()), id(n.operationId()), n.code(), n.name(), n.sequenceNo(),
                decimal(n.inputQty()), decimal(n.reportedQty()), decimal(n.defectQty()), decimal(n.effectiveOutputQty()), decimal(n.availableQty()), n.qualityIntegrated());
    }

    public static Summary summary(ExecutionSummary e, String routingLabel) {
        return new Summary(id(e.executionId()), id(e.workorderId()), e.executionNo(), id(e.routingId()), decimal(e.targetQty()), e.pathStatus(), decimal(e.lastNodeReportedQty()), routingLabel);
    }

    public static String id(Long value) {
        return value == null ? null : value.toString();
    }

    public static String decimal(BigDecimal value) {
        return value.toPlainString();
    }
}
