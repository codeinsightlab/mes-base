package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Command;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Summary;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/quality/defects/{id}/treatment")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityTreatmentController {
    private final QualityTreatmentService service;

    public QualityTreatmentController(QualityTreatmentService service) {
        this.service = service;
    }

    public record Event(String id, String declarationId, int declarationRevision, String executionId,
                        String routingNodeId,
                        String actionType, String parentId, String attributionType, String relatedDeclarationId,
                        String basis,
                        String scrapQty, String reworkQty, String processedQty, String recoveredQty, String failedQty,
                        String operatorUserId, String operatorName, String confirmedBy, String confirmedByName,
                        LocalDateTime occurredAt, LocalDateTime recordedAt, String reason) {
    }

    public record View(String declarationId, int revision, String executionId, String routingNodeId, String defectQty,
                       String attribution, String relatedDeclarationId, String basis, String allocatedScrapQty,
                       String allocatedReworkQty, String startedQty, String processedQty, String recoveredQty,
                       String failedQty,
                       String pendingDispositionQty, String pendingStartQty, String inProgressQty, String netDefectQty,
                       String state, String recordAction, String destinationType, List<Event> events) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public View details(@PathVariable String id) {
        return view(service.details(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:revise")
    public View execute(@PathVariable String id, @RequestBody Command c) {
        return view(service.execute(id, c));
    }

    private static View view(QualityTreatmentService.Details data) {
        var s = data.summary();
        return new View(s.declarationId(), s.revision(), s.executionId().toString(), s.routingNodeId()
                .toString(), d(s.defectQty()), s.attribution()
                .name(), s.relatedDeclarationId(), s.basis(), d(s.allocatedScrapQty()), d(s.allocatedReworkQty()), d(s.startedQty()), d(s.processedQty()), d(s.recoveredQty()), d(s.failedQty()), d(s.pendingDispositionQty()), d(s.pendingStartQty()), d(s.inProgressQty()), d(s.netDefectQty()), s.state(), s.recordAction(), s.destinationType(), data.history()
                .stream().map(e -> event(e, data.users())).toList());
    }

    private static Event event(MesQualityTreatmentEntity e, Map<Long, DirectoryLabel> users) {
        return new Event(e.getId(), e.getDeclarationId(), e.getDeclarationRevision(), e.getExecutionId()
                .toString(), e.getRoutingNodeId()
                .toString(), e.getActionType(), e.getParentId(), e.getAttributionType(), e.getRelatedDeclarationId(), e.getBasis(), d(e.getScrapQty()), d(e.getReworkQty()), d(e.getProcessedQty()), d(e.getRecoveredQty()), d(e.getFailedQty()), e.getOperatorUserId()
                .toString(), name(users, e.getOperatorUserId()), e.getConfirmedBy()
                .toString(), name(users, e.getConfirmedBy()), e.getOccurredAt(), e.getRecordedAt(), e.getReason());
    }

    private static String name(Map<Long, DirectoryLabel> names, Long id) {
        var n = names.get(id);
        return n == null ? null : n.name();
    }

    private static String d(BigDecimal q) {
        return q.toPlainString();
    }
}
