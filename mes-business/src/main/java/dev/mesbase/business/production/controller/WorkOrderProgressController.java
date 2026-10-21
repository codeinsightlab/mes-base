package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import dev.mesbase.business.production.domain.WorkOrderProgress.Query;
import dev.mesbase.business.production.domain.WorkOrderProgress.Page;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderProgressController {
    private final WorkOrderProgressQueryService service;

    public WorkOrderProgressController(WorkOrderProgressQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/production/workorder-progress")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:list")
    public Page page(@RequestParam(required = false) String workorderCode,
                     @RequestParam(required = false) String productCode, @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "5") int limit) {
        return service.page(new Query(workorderCode, productCode, status, offset, limit));
    }
}
