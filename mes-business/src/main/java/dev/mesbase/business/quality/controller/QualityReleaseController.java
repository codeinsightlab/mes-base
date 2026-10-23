package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public record Fact(String id, String executionId, String sequenceNo, String actionType, String quantity,
                       String basisHash, String referenceId, String operatorUserId, LocalDateTime recordedAt,
                       String reason) {
    }

    public record History(List<Fact> items, long total, int offset, int limit) {
    }

    @GetMapping("/history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:release:read")
    public History history(@PathVariable Long id, @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(defaultValue = "20") int limit) {
        var p = service.history(id, offset, limit);
        return new History(p.items().stream().map(QualityReleaseController::view).toList(), p.total(), offset, limit);
    }

    private static Fact view(MesQualityReleaseEntity r) {
        return BeanConversions.convert(r, Fact.class);
    }
}
