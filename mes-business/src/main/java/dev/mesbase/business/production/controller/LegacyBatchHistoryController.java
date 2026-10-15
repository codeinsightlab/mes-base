package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.LegacyBatchHistoryQuery;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/batch")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LegacyBatchHistoryController {
    private final LegacyBatchHistoryQuery history;

    public LegacyBatchHistoryController(LegacyBatchHistoryQuery history) {
        this.history = history;
    }

    public record NodeView(String routingNodeId, int executionOrder) {
    }

    public record BatchView(String id, String workorderId, String batchNo, BigDecimal plannedQty,
                            String status, String routeStatus, LocalDateTime lockedAt, int revision, String remark,
                            List<NodeView> path) {
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "batch:query")
    public BatchView detail(@PathVariable Long id) {
        var detail = history.detail(id);
        var b = detail.batch();
        return new BatchView(b.getId().toString(), b.getWorkorderId().toString(), b.getBatchNo(), b.getPlannedQty(),
                b.getStatus(), b.getRouteStatus(), b.getLockedAt(), b.getRevision(), b.getRemark(),
                detail.path().stream().map(n -> new NodeView(n.getRoutingNodeId().toString(), n.getExecutionOrder())).toList());
    }
}
