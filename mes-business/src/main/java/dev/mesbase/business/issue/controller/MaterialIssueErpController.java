package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.service.MaterialIssueErpService;
import dev.mesbase.business.issue.service.MaterialIssueErpService.QueryView;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueErpController {
    private final MaterialIssueErpService service;

    public MaterialIssueErpController(MaterialIssueErpService service) {
        this.service = service;
    }

    @GetMapping("/api/issue/mes-material-issue/{id}/erp-status")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:erp:query")
    public QueryView query(@PathVariable Long id) {
        return service.queryErpStatus(id);
    }

    @PostMapping("/api/issue/mes-material-issue/{id}/actions/reconcile-erp")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:erp:reconcile")
    public QueryView reconcile(@PathVariable Long id) {
        return service.reconcileErpStatus(id);
    }
}
