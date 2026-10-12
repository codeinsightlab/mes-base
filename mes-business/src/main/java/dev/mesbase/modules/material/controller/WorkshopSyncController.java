// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.sync.WorkshopSyncResult;
import dev.mesbase.modules.material.sync.WorkshopSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkshopSyncController {
    private final WorkshopSyncService service;

    public WorkshopSyncController(WorkshopSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/material/workshop/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.SYNC)
    public WorkshopSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

