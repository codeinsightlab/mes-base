package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.issue.controller.vo.MesMaterialIssueVo;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueVoidController {
    private final MaterialIssueVoidService service;

    public MaterialIssueVoidController(MaterialIssueVoidService service) {
        this.service = service;
    }

    @PostMapping("/api/issue/mes-material-issue/{id}/actions/void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:void")
    public MesMaterialIssueVo cancel(@PathVariable Long id) {
        return MesMaterialIssueController.toView(service.cancel(id));
    }

    @PostMapping("/api/issue/mes-material-issue/{id}/actions/reconcile-void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:void")
    public MesMaterialIssueVo reconcile(@PathVariable Long id) {
        return MesMaterialIssueController.toView(service.reconcile(id));
    }
}
