package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.QualityTreatmentModels.Disposition;
import dev.mesbase.business.quality.service.QualityDispositionService;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityDispositionController {
    private final QualityDispositionService service;

    public QualityDispositionController(QualityDispositionService service) {
        this.service = service;
    }

    @PostMapping("/api/quality/defects/{id}/dispositions")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:revise")
    public QualityTreatmentController.View execute(@PathVariable String id, @RequestBody Disposition command) {
        return QualityTreatmentController.view(service.execute(id, command));
    }
}
