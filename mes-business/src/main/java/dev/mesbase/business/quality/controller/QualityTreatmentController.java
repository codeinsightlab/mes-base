package dev.mesbase.business.quality.controller;

import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.AssessmentCommand;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityTreatmentController {
    private final QualityTreatmentService service;

    public QualityTreatmentController(QualityTreatmentService service) {
        this.service = service;
    }

    public record Event(String id, String declarationId, int declarationRevision, String executionId,
                        String routingNodeId, String actionType, String attributionType, String relatedDeclarationId,
                        String basis, String returnedQty, String operatorUserId, String operatorName,
                        String confirmedBy, String confirmedByName, LocalDateTime occurredAt, LocalDateTime recordedAt,
                        String reason, String targetWarehouseId, String stockDocumentId, String transferQty) {
    }

    public record View(String declarationId, int revision, String executionId, String routingNodeId, String defectQty,
                       String attribution, String relatedDeclarationId, String basis, String pendingDispositionQty,
                       String netDefectQty, String state, String recordAction, String destinationType,
                       List<Event> events,
                       String returnedQty, String transferredQty) {
    }

    @GetMapping("/treatments/recent")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public List<Event> recent() {
        var result = service.recent();
        return result.events().stream().map(e -> event(e, result.users())).toList();
    }

    @GetMapping("/defects/{id}/treatment")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public View details(@PathVariable String id) {
        return view(service.details(id));
    }

    @PostMapping("/defects/{id}/assessments")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:revise")
    public View assess(@PathVariable String id, @RequestBody AssessmentCommand c) {
        return view(service.assess(id, c));
    }

    private record History(List<Event> events) {
    }

    private record Labels(String operatorName, String confirmedByName) {
    }

    static View view(QualityTreatmentService.Details data) {
        return BeanConversions.convertWith(data.summary(), new History(data.history().stream()
                .map(event -> event(event, data.users())).toList()), View.class);
    }

    private static Event event(MesQualityTreatmentEntity event, Map<Long, DirectoryLabel> users) {
        return BeanConversions.convertWith(event, new Labels(name(users, event.getOperatorUserId()),
                name(users, event.getConfirmedBy())), Event.class);
    }

    private static String name(Map<Long, DirectoryLabel> names, Long id) {
        var label = names.get(id);
        if (label == null) return null;
        return label.name();
    }
}
