package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/production/executions/{id}")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionCompletionController {
    private final ProductionCompletionService service;

    public ProductionCompletionController(ProductionCompletionService service) {
        this.service = service;
    }

    public record Fact(String id, String executionId, String effectiveQty, String releasedQty, String receiptQty,
                       String acceptedQty, String operatorUserId, LocalDateTime recordedAt, String reason) {
    }

    public record Summary(String executionId, String executionNo, String status, String targetQty, String effective,
                          String released, String historicalReleased, String received, String accepted,
                          String remainingHandover, String pendingAcceptance, String outputExcess, String releaseExcess,
                          String basisHash, boolean canComplete, String blockedReason, Fact completion) {
    }

    @GetMapping("/closure")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:production:execution:query")
    public Summary summary(@PathVariable Long id) {
        var s = service.summary(id);
        return new Summary(id.toString(), s.execution().getExecutionNo(), s.execution().getStatus(), s.execution()
                .getTargetQty().toPlainString(), s.release().effective().toPlainString(), s.release().released()
                .toPlainString(), s.release().historicalReleased().toPlainString(), s.received()
                .toPlainString(), s.accepted().toPlainString(), s.remainingHandover()
                .toPlainString(), s.pendingAcceptance().toPlainString(), s.outputExcess()
                .toPlainString(), s.releaseExcess().toPlainString(), s.release()
                .basisHash(), s.canComplete(), s.blockedReason(), s.completion() == null ? null : view(s.completion()));
    }

    @PostMapping("/actions/complete")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:production:execution:complete")
    public Fact complete(@PathVariable Long id, @RequestBody ProductionCompletionService.Command c) {
        return view(service.complete(id, c));
    }

    private static Fact view(MesExecutionCompletionEntity r) {
        return new Fact(r.getId(), r.getExecutionId().toString(), r.getEffectiveQty()
                .toPlainString(), r.getReleasedQty().toPlainString(), r.getReceiptQty()
                .toPlainString(), r.getAcceptedQty().toPlainString(), r.getOperatorUserId()
                .toString(), r.getRecordedAt(), r.getReason());
    }
}
