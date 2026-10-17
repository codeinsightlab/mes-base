package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/quality/releases/executions/{id}")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityReleaseController {
    private final QualityReleaseService service;

    public QualityReleaseController(QualityReleaseService service) {
        this.service = service;
    }

    public record Request(String requestId, String expectedReleaseId, BigDecimal quantity, String reason) {
    }

    public record Fact(String id, String executionId, String sequenceNo, String actionType, String quantity,
                       String basisHash, String referenceId, String operatorUserId, LocalDateTime recordedAt,
                       String reason) {
    }

    public record State(String executionId, String effective, String released, String historicalReleased,
                        String currentReleaseId, String basisHash, boolean basisValid, boolean safe,
                        String blockedReason) {
    }

    public record History(List<Fact> items, long total, int offset, int limit) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:release:read")
    public State state(@PathVariable Long id) {
        var s = service.state(id);
        return new State(id.toString(), s.effective().toPlainString(), s.released()
                .toPlainString(), s.historicalReleased()
                .toPlainString(), s.currentReleaseId(), s.basisHash(), s.basisValid(), s.safe(), s.blockedReason());
    }

    @GetMapping("/history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:release:read")
    public History history(@PathVariable Long id, @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(defaultValue = "20") int limit) {
        var p = service.history(id, offset, limit);
        return new History(p.items().stream().map(QualityReleaseController::view).toList(), p.total(), offset, limit);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:release:create")
    public Fact release(@PathVariable Long id, @RequestBody Request c) {
        return view(service.execute(id, new QualityReleaseService.Command(c.requestId(), c.expectedReleaseId(), c.quantity(), false, c.reason())));
    }

    @PostMapping("/revoke")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:release:revoke")
    public Fact revoke(@PathVariable Long id, @RequestBody Request c) {
        return view(service.execute(id, new QualityReleaseService.Command(c.requestId(), c.expectedReleaseId(), c.quantity(), true, c.reason())));
    }

    private static Fact view(MesQualityReleaseEntity r) {
        return new Fact(r.getId(), r.getExecutionId().toString(), r.getSequenceNo()
                .toString(), r.getActionType(), r.getQuantity()
                .toPlainString(), r.getBasisHash(), r.getReferenceId(), r.getOperatorUserId()
                .toString(), r.getRecordedAt(), r.getReason());
    }
}
