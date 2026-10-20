package dev.mesbase.business.legacy.controller;

import dev.mesbase.business.legacy.service.LegacyHistoryQueryService;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LegacyHistoryController {
    private final LegacyHistoryQueryService history;

    public LegacyHistoryController(LegacyHistoryQueryService history) {
        this.history = history;
    }

    public record Node(String routingNodeId, String legacyNet, String grossCorrection,
                       String effectiveDefect, String evidence) {
    }

    public record Detail(boolean historical, boolean readOnly, String batchId, String ruleVersion,
                         String sourceMappingId, String inputBasis, String historicalInputQty,
                         String sourceEvidence, List<Node> nodes) {
    }

    public record OrderDetail(String workorderId, String sourceMappingId, boolean historical, boolean readOnly,
                              List<LegacyHistoryQueryService.Card> cards, long unmigratedCards,
                              long sourceIncompleteCards, boolean productionComplete) {
    }

    @GetMapping("/api/production/workorder/{id}/legacy-history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:production:report:detail")
    public OrderDetail order(@PathVariable Long id) {
        var detail = history.order(id);
        return new OrderDetail(detail.workorderId().toString(), detail.sourceMappingId(), true, true, detail.cards(),
                detail.unmigratedCards(), detail.sourceIncompleteCards(), detail.productionComplete());
    }

    @GetMapping("/api/production/executions/{id}/legacy-history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:production:report:detail")
    public Detail execution(@PathVariable Long id) {
        var detail = history.execution(id);
        return new Detail(true, true, detail.mapping().batchId(), detail.mapping().ruleVersion(),
                detail.mapping().id(), "LEGACY_MATERIAL_EVIDENCE_NOT_CURRENT_PRODUCTION_INPUT",
                detail.historicalInputQty().toPlainString(), detail.mapping().sourcePayload(),
                detail.nodes().stream().map(n -> new Node(n.routingNodeId().toString(), n.legacyNet().toPlainString(),
                                n.grossCorrection().toPlainString(), n.effectiveDefect().toPlainString(), n.evidence()))
                        .toList());
    }
}
